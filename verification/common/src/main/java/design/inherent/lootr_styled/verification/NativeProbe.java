package design.inherent.lootr_styled.verification;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.client.ClientRuntime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * A separate consumer of the packaged product. Never part of installable/source product artifacts.
 */
public final class NativeProbe {
  private static UiProbe ui;
  private static IsolatedGraphicProbe graphics;
  private static SharedGraphicHostProbe sharedHost;
  private static RendererShutdownProbe rendererShutdown;
  private static ProductionShutdownProbe productionShutdown;
  private static ProductionViewportProbe productionViewport;
  private static ProductionCloseProbe productionClose;
  private static ProductionEffectsProbe productionEffects;
  private static ProductionDocumentProbe productionDocument;
  private static ProductionInputProbe productionInput;
  private static PreviewCombinationProbe combinations;
  private static PreviewAuthorityProbe authority;
  private static PreviewCleanupFailureProbe cleanupFailure;
  private static int cleanupFailureStep;
  private static final JsonObject REPORT = new JsonObject();
  private static final JsonArray snapshots = new JsonArray();
  private static int ticks, stage;
  private static boolean terminal;
  private static volatile boolean reloaded;
  private static String lastProgress = "";

  private NativeProbe() {}

  public static void tick(Minecraft mc) {
    if (terminal) return;
    if (++ticks > 6000) {
      finish(mc, new IllegalStateException("Native fixture exceeded tick bound"));
      return;
    }
    try {
      Path processFile = mc.gameDirectory.toPath().resolve(".lootr-styled-probe-pid");
      if (ticks == 1) Files.writeString(processFile, Long.toString(ProcessHandle.current().pid()));
      Object currentScreen = NativeBindings.screen(mc);
      String progress =
          stage
              + ":"
              + (currentScreen == null ? "none" : currentScreen.getClass().getName())
              + (ui == null ? "" : ":" + ui.progress());
      if (authority != null) progress += ":authority=" + authority.progress();
      if (productionViewport != null) progress += ":viewport=" + productionViewport.progress();
      if (productionClose != null) progress += ":close=" + productionClose.progress();
      if (productionEffects != null) progress += ":effects=" + productionEffects.progress();
      if (productionDocument != null) progress += ":document=" + productionDocument.progress();
      if (productionInput != null) progress += ":input=" + productionInput.progress();
      if (!lastProgress.equals(progress)) {
        Files.writeString(
            mc.gameDirectory.toPath().resolve("lootr-styled-probe-state.txt"), progress + "\n");
        lastProgress = progress;
      }
      var warnings = ProbeWarnings.advance(NativeBindings.screen(mc));
      if (warnings != null) {
        if (!REPORT.has("acknowledgedUpstreamWarnings"))
          REPORT.add("acknowledgedUpstreamWarnings", new JsonArray());
        REPORT.getAsJsonArray("acknowledgedUpstreamWarnings").addAll(warnings);
      }
      if (stage < 2 && !NativeBindings.ready(mc)) return;
      if (stage == 0
          && design.inherent.lootr_styled.client.StylePreferences.resourceStatus().state()
              != design.inherent.lootr_styled.client.StyleApplication.State.APPLIED) return;
      Path modeFile = mc.gameDirectory.toPath().resolve("lootr-styled-probe-mode.txt");
      if (!Files.isRegularFile(modeFile) || Files.size(modeFile) > 32)
        throw new IllegalStateException("Missing bounded probe mode");
      String mode = Files.readString(modeFile, StandardCharsets.UTF_8).strip();
      if (!java.util.Set.of("native", "fresh", "off").contains(mode))
        throw new IllegalStateException("Unknown fixture mode");
      if (stage == 0) {
        REPORT.add("publishedProfiles", PreviewProbeAccess.publishedProfiles());
        String client =
            ClientRuntime.class.getProtectionDomain().getCodeSource().getLocation().toString();
        String core =
            ResourceId.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!client.equals(core) || !client.contains(".jar"))
          throw new IllegalStateException(
              "Product/core must come from the same packaged JAR, not source outputs");
        REPORT.addProperty("productCodeSource", client);
        String library =
            design.inherent.reform.minecraft.client.ClientFileSettings.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString();
        if (!library.contains(".jar"))
          throw new IllegalStateException("re:form must be an independent packaged input");
        REPORT.addProperty("libraryCodeSource", library);
        REPORT.addProperty(
            "libraryInputSha256",
            Files.readString(
                    mc.gameDirectory.toPath().resolve(".lootr-styled-expected-reform-sha256"))
                .strip());
        REPORT.addProperty(
            "artifactSha256",
            Files.readString(mc.gameDirectory.toPath().resolve(".lootr-styled-expected-sha256"))
                .strip());
        REPORT.addProperty("mode", mode);
        REPORT.addProperty("java", Runtime.version().feature());
        snapshots.add(NativeBindings.capture(mc, mode));
        stage = 1;
        mc.reloadResourcePacks()
            .whenComplete(
                (unused, error) -> {
                  if (error != null) finish(mc, error);
                  else reloaded = true;
                });
      } else if (stage == 1 && reloaded) {
        snapshots.add(NativeBindings.capture(mc, mode));
        REPORT.add("snapshots", snapshots);
        if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-production-input"))) {
          productionInput = new ProductionInputProbe(mc);
          stage = 25;
        } else if (Files.exists(
            mc.gameDirectory.toPath().resolve(".lootr-styled-production-viewport"))) {
          productionViewport = new ProductionViewportProbe(mc);
          stage = 21;
        } else if (Files.exists(
            mc.gameDirectory.toPath().resolve(".lootr-styled-production-close"))) {
          productionClose = new ProductionCloseProbe();
          stage = 22;
        } else if (Files.exists(
            mc.gameDirectory.toPath().resolve(".lootr-styled-production-effects"))) {
          productionEffects = new ProductionEffectsProbe();
          stage = 23;
        } else if (Files.exists(
            mc.gameDirectory.toPath().resolve(".lootr-styled-production-document"))) {
          productionDocument = new ProductionDocumentProbe(mc);
          stage = 24;
        } else if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-host-probe"))) {
          sharedHost = new SharedGraphicHostProbe();
          stage = 12;
        } else if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-graphic-probe"))) {
          graphics = new IsolatedGraphicProbe();
          stage = 11;
        } else {
          ui = new UiProbe();
          stage = 10;
        }
      } else if (stage == 10) {
        var widgets = ui.tick(mc);
        if (widgets == null) return;
        REPORT.add("nativeWidgets", widgets);
        REPORT.addProperty("nativeWidgetsComplete", true);
        if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-production-shutdown"))) {
          productionShutdown = new ProductionShutdownProbe();
          stage = 20;
        } else if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-scene"))) {
          stage = 2;
          SceneProbe.begin(mc);
        } else {
          REPORT.addProperty("passed", true);
          finish(mc, null);
        }
      } else if (stage == 11) {
        var observations = graphics.tick(mc);
        if (observations == null) return;
        REPORT.add("isolatedGraphic", observations);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      } else if (stage == 12) {
        var observations = sharedHost.tick(mc);
        if (observations == null) return;
        REPORT.add("sharedGraphicHost", observations);
        combinations = new PreviewCombinationProbe();
        stage = 13;
      } else if (stage == 13) {
        var observations = combinations.tick(mc);
        if (observations == null) return;
        REPORT.add("previewCombinations", observations);
        combinations = new PreviewCombinationProbe(observations);
        stage = 18;
      } else if (stage == 18) {
        var observations = combinations.tick(mc);
        if (observations == null) return;
        REPORT.add("previewSelectionStates", observations);
        authority = new PreviewAuthorityProbe(mc);
        stage = 14;
      } else if (stage == 14) {
        var observations = authority.tick(mc);
        if (observations == null) return;
        REPORT.add("previewAuthority", observations);
        authority = new PreviewAuthorityProbe(mc, true);
        stage = 17;
      } else if (stage == 17) {
        var observations = authority.tick(mc);
        if (observations == null) return;
        REPORT.add("previewReviewRequired", observations);
        cleanupFailure = new PreviewCleanupFailureProbe();
        stage = 15;
      } else if (stage == 15) {
        var observations = cleanupFailure.tick(mc);
        if (observations == null) return;
        String name =
            switch (cleanupFailureStep) {
              case 0 -> "previewCleanupFailure";
              case 1 -> "previewCleanupSynchronous";
              default -> "previewCleanupReplacement";
            };
        REPORT.add(name, observations);
        if (++cleanupFailureStep < PreviewCleanupFailureProbe.Mode.values().length) {
          cleanupFailure =
              new PreviewCleanupFailureProbe(
                  PreviewCleanupFailureProbe.Mode.values()[cleanupFailureStep]);
          return;
        }
        rendererShutdown = new RendererShutdownProbe();
        stage = 16;
      } else if (stage == 16) {
        rendererShutdown.tick(mc);
      } else if (stage == 20) {
        productionShutdown.tick(mc);
      } else if (stage == 21) {
        var observations = productionViewport.tick(mc);
        if (observations == null) return;
        REPORT.add("productionViewport", observations);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      } else if (stage == 22) {
        var observations = productionClose.tick(mc);
        if (observations == null) return;
        REPORT.add("productionClose", observations);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      } else if (stage == 23) {
        var observations = productionEffects.tick(mc);
        if (observations == null) return;
        REPORT.add("productionEffects", observations);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      } else if (stage == 24) {
        var observations = productionDocument.tick(mc);
        if (observations == null) return;
        REPORT.add("productionDocument", observations);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      } else if (stage == 25) {
        var observations = productionInput.tick(mc);
        if (observations == null) return;
        REPORT.add("productionInput", observations);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      } else if (stage == 2) {
        var scene = SceneProbe.tick(mc);
        if (scene != null) {
          REPORT.add("scene", scene);
          REPORT.add("renderedModels", NativeBindings.capture(mc, mode));
          reloaded = false;
          stage = 3;
          mc.reloadResourcePacks()
              .whenComplete(
                  (unused, error) -> {
                    if (error != null) finish(mc, error);
                    else reloaded = true;
                  });
        }
      } else if (stage == 3 && reloaded) {
        REPORT.add("secondReloadInWorld", NativeBindings.capture(mc, mode));
        inspectShaderState(mc);
        REPORT.addProperty("passed", true);
        finish(mc, null);
      }
    } catch (Throwable error) {
      if (stage == 2) REPORT.add("scene", SceneProbe.snapshot());
      finish(mc, error);
    }
  }

  private static void inspectShaderState(Minecraft mc)
      throws ReflectiveOperationException, java.io.IOException {
    Path expectedFile = mc.gameDirectory.toPath().resolve(".lootr-styled-shader-expected");
    if (!Files.isRegularFile(expectedFile)) return;
    if (Files.size(expectedFile) > 8)
      throw new IllegalStateException("Unbounded shader expectation");
    String expected = Files.readString(expectedFile).strip();
    if (!java.util.Set.of("true", "false").contains(expected))
      throw new IllegalStateException("Invalid shader expectation");
    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
    Object instance = api.getMethod("getInstance").invoke(null);
    boolean active = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
    REPORT.addProperty("irisShaderPackInUse", active);
    if (active != Boolean.parseBoolean(expected))
      throw new IllegalStateException(
          "Actual Iris shader state differs from the fixture expectation");
  }

  private static synchronized void finish(Minecraft mc, Throwable error) {
    if (terminal) return;
    terminal = true;
    try {
      if (error != null) {
        if (productionShutdown != null)
          REPORT.add("productionDiscard", productionShutdown.observations());
        if (productionViewport != null)
          REPORT.add("productionViewport", productionViewport.observations());
        if (productionClose != null) REPORT.add("productionClose", productionClose.observations());
        if (productionEffects != null)
          REPORT.add("productionEffects", productionEffects.observations());
        if (productionDocument != null)
          REPORT.add("productionDocument", productionDocument.observations());
        if (productionInput != null) REPORT.add("productionInput", productionInput.observations());
        if (ui != null && !REPORT.has("nativeWidgets")) {
          REPORT.add("nativeWidgets", ui.observations());
          REPORT.addProperty("nativeWidgetsComplete", false);
        }
        REPORT.addProperty("passed", false);
        REPORT.addProperty("error", error.toString());
        com.mojang.logging.LogUtils.getLogger().error("Lootr Styled native fixture failed", error);
      }
      Files.writeString(
          mc.gameDirectory.toPath().resolve("lootr-styled-probe-report.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n",
          StandardCharsets.UTF_8);
    } catch (java.io.IOException failure) {
      com.mojang.logging.LogUtils.getLogger().error("Cannot write native fixture report", failure);
    }
    mc.execute(mc::stop);
  }

  static synchronized void requestLiveShutdown(
      Minecraft mc,
      design.inherent.reform.minecraft.client.ClientGraphicHost host,
      design.inherent.reform.minecraft.client.PreparedGraphic lease,
      Path settings,
      byte[] originalSettings,
      JsonObject initial,
      java.util.function.BooleanSupplier workflowOpen,
      java.util.function.BooleanSupplier editorOpen) {
    requestLiveShutdown(
        mc,
        host,
        lease.releaseCompletion(),
        settings,
        originalSettings,
        initial,
        workflowOpen,
        editorOpen);
  }

  static synchronized void requestLiveShutdown(
      Minecraft mc,
      design.inherent.reform.minecraft.client.ClientGraphicHost host,
      java.util.concurrent.CompletionStage<
              design.inherent.reform.presentation.graphics.RenderLifetime.State>
          release,
      Path settings,
      byte[] originalSettings,
      JsonObject initial,
      java.util.function.BooleanSupplier workflowOpen,
      java.util.function.BooleanSupplier editorOpen) {
    if (terminal) throw new IllegalStateException("Native fixture already finished");
    var fixture = mc.gameDirectory.toPath();
    initial.addProperty("artifactSha256", REPORT.get("artifactSha256").getAsString());
    initial.addProperty("libraryInputSha256", REPORT.get("libraryInputSha256").getAsString());
    RendererShutdownEvidence.arm(
        host,
        release,
        settings,
        originalSettings,
        initial,
        workflowOpen,
        editorOpen,
        value -> completeLiveShutdown(fixture, value));
    REPORT.add("rendererShutdownRequest", initial);
    REPORT.addProperty("passed", false);
    REPORT.addProperty("shutdownPending", true);
    terminal = true;
    try {
      Files.writeString(
          fixture.resolve("lootr-styled-probe-report.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n",
          StandardCharsets.UTF_8);
    } catch (java.io.IOException failed) {
      System.err.println("Cannot write pending renderer shutdown report: " + failed);
    }
    // A normal stop request while this submitted use and workflow are still live. Native
    // shutdown hooks own every close/drain; the test observer does not release anything.
    mc.stop();
  }

  private static synchronized void completeLiveShutdown(Path fixture, JsonObject result) {
    REPORT.add("rendererShutdown", result);
    REPORT.remove("shutdownPending");
    REPORT.addProperty("passed", result.get("passed").getAsBoolean());
    var gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      Files.writeString(
          fixture.resolve("lootr-styled-shutdown-report.json"),
          gson.toJson(result) + "\n",
          StandardCharsets.UTF_8);
      Files.writeString(
          fixture.resolve("lootr-styled-probe-report.json"),
          gson.toJson(REPORT) + "\n",
          StandardCharsets.UTF_8);
    } catch (java.io.IOException failed) {
      System.err.println("Cannot write renderer shutdown evidence: " + failed);
    }
  }
}
