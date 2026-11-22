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
        ui = new UiProbe();
        stage = 10;
      } else if (stage == 10) {
        var widgets = ui.tick(mc);
        if (widgets == null) return;
        REPORT.add("nativeWidgets", widgets);
        if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-scene"))) {
          stage = 2;
          SceneProbe.begin(mc);
        } else {
          REPORT.addProperty("passed", true);
          finish(mc, null);
        }
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
}
