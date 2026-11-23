package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftEdit;
import design.inherent.reform.minecraft.client.*;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Actual retained owner/provider/host with a test-mounted shared widget, not production layout. */
final class SharedGraphicHostProbe {
  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final ClientEditor<ClientSettings> editor;
  private final byte[] originalSettings;
  private final PreviewProbeAccess.HostProvider provider;
  private final ClientGraphicHost host;
  private final ClientGraphicHost.View<ClientSettings, Object> view;
  private final JsonArray subjects = new JsonArray();
  private CompletableFuture<?> pending;
  private ClientGraphicHost.Attempt attempt;
  private PreparedGraphic lease;
  private AbstractWidget widget;
  private int stage, index, ticks, callsAtMount;
  private boolean useSeen;
  private volatile boolean screenshot;

  SharedGraphicHostProbe() throws Exception {
    parent = NativeUi.current();
    originalSettings =
        Files.readAllBytes(ClientRuntime.configuration().root().resolve("settings.json"));
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    workflow.show(StyleSettingsStore.DEFINITION.id());
    editor = workflow.editor(StyleSettingsStore.DEFINITION);
    pending =
        DocumentActions.result(editor.actions().ensureLoaded(EntryPoint.GUI)).toCompletableFuture();
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    provider = PreviewProbeAccess.hostProvider(limits);
    host = workflow.graphics(limits, 1);
    view = host.open(editor, provider.provider());
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (pending != null) {
      if (!pending.isDone()) return null;
      pending.join();
      pending = null;
      return null;
    }
    if (stage == 0) {
      if (editor.form().fieldControl(FieldPath.ROOT.field("artwork")).isEmpty()) return null;
      var snapshot = editor.view().snapshot().orElseThrow();
      pending =
          DocumentActions.result(
                  editor
                      .actions()
                      .edit(
                          EntryPoint.GUI,
                          new DraftEdit(
                              snapshot.target(FieldPath.ROOT.field("artwork")),
                              new DraftEdit.SetRaw(ClientSettings.VANILLA.toString()))))
              .toCompletableFuture();
      stage = 1;
      ticks = 0;
      return null;
    }
    if (stage == 1) {
      if (++ticks < 4) return null;
      if (index == 4) {
        if (!java.util.Arrays.equals(
            originalSettings,
            Files.readAllBytes(ClientRuntime.configuration().root().resolve("settings.json"))))
          throw new IllegalStateException("The shared provider saved preview settings");
        if (provider.clientCalls().get() != 0 || provider.policyThreads().isEmpty())
          throw new IllegalStateException("Domain material preparation did not stay on the worker");
        if (host.nativeOwnership().bytes() != 0 || host.retainedOutput().bytes() != 0)
          throw new IllegalStateException("The completed host retained native/scene payload");
        view.close();
        workflow.close();
        NativeUi.open(parent);
        var result = new JsonObject();
        result.add("subjects", subjects);
        result.addProperty("materialCalls", provider.materialCalls().get());
        result.addProperty("materialClientCalls", provider.clientCalls().get());
        result.addProperty("policyThreads", provider.policyThreads().toString());
        result.addProperty("verifiedInheritedImages", provider.verifiedImages().get());
        result.addProperty("settingsUnchanged", true);
        result.addProperty("unsavedArtworkCaptured", true);
        result.addProperty("nativeBytesAfterClose", host.nativeOwnership().bytes());
        result.addProperty("sceneBytesAfterClose", host.retainedOutput().bytes());
        result.addProperty(
            "scope",
            "Actual provider READ capture/worker CPU prep/shared GPU host and test-mounted widget; not final production layout, automatic material policy, exhaustive freshness/cancel/shutdown qualification");
        return result;
      }
      boolean open = index % 2 == 1;
      String container = index < 2 ? (open ? "TRAPPED_CHEST" : "CHEST") : "BARREL";
      var json =
          com.google.gson.JsonParser.parseString(
              "{\"container\":\""
                  + container
                  + "\",\"looted\":"
                  + open
                  + ",\"open\":"
                  + open
                  + ",\"lid\":"
                  + (open ? 1 : 0)
                  + "}");
      var subject =
          provider
              .provider()
              .subjectType()
              .codec()
              .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
              .getOrThrow();
      view.visible(true);
      attempt = view.request(subject);
      stage = 2;
      ticks = 0;
      return null;
    }
    if (stage == 2) {
      var future = attempt.completion().toCompletableFuture();
      if (!future.isDone()) return null;
      var outcome = future.join();
      if (outcome.state() != ClientGraphicHost.State.READY)
        throw new IllegalStateException(
            "Actual provider did not become ready: " + outcome.state() + " " + outcome.report(),
            outcome.cause().orElse(null));
      if (attempt.operation().isEmpty())
        throw new IllegalStateException("Actual provider lost its operation handle");
      var ready = view.preparation().orElseThrow();
      if (!(ready instanceof GraphicPreparation.Ready value))
        throw new IllegalStateException("Host has no prepared native lease");
      lease = value.graphic();
      if (lease.facts().stream()
          .noneMatch(
              f ->
                  f.title().equals("Requested artwork")
                      && f.value().equals(ClientSettings.VANILLA.toString())))
        throw new IllegalStateException(
            "The worker did not consume the complete unsaved candidate");
      widget = view.widget(Component.literal("Shared host container preview"));
      widget.setPosition(12, 12);
      widget.setWidth(220);
      widget.setHeight(180);
      mount(workflow.screen(), widget, true);
      callsAtMount = provider.materialCalls().get();
      stage = 3;
      ticks = 0;
      useSeen = false;
      screenshot = false;
      return null;
    }
    if (stage == 3) {
      if (!workflow.screen().children().contains(widget)) mount(workflow.screen(), widget, true);
      useSeen |= lease.state().outstandingUses() > 0;
      if (view.failure().isPresent())
        throw new IllegalStateException("Shared host draw failed", view.failure().orElseThrow());
      if (++ticks == 8) {
        view.orbit(20, 0);
        view.zoom(1.1);
      }
      if (provider.materialCalls().get() != callsAtMount)
        throw new IllegalStateException("Camera controls recompiled the domain scene");
      if (ticks < 24) return null;
      if (ticks == 24) {
        NativeWorld.screenshot(mc, "shared-host-" + index, () -> screenshot = true);
        return null;
      }
      if (!screenshot) return null;
      if (!useSeen)
        throw new IllegalStateException(
            "The mounted shared host widget never submitted native geometry");
      mount(workflow.screen(), widget, false);
      view.visible(false);
      stage = 4;
      ticks = 0;
      return null;
    }
    if (stage == 4) {
      var release = lease.releaseCompletion().toCompletableFuture();
      if (!release.isDone()) {
        if (++ticks > 160) throw new IllegalStateException("Native host retirement did not settle");
        return null;
      }
      if (!release.join().released())
        throw new IllegalStateException("Native host release failed: " + release.join());
      if (host.nativeOwnership().bytes() != 0 || host.retainedOutput().bytes() != 0)
        throw new IllegalStateException(
            "Host payload accounting outlived successful native retirement");
      var subject = new JsonObject();
      subject.addProperty("index", index);
      subject.addProperty("actualReadOperation", true);
      subject.addProperty("unsavedArtworkCaptured", true);
      subject.addProperty("nativeUseObserved", true);
      subject.addProperty("releaseConfirmed", true);
      subject.addProperty("cameraDidNotRecompile", true);
      subjects.add(subject);
      lease = null;
      widget = null;
      index++;
      stage = 1;
      ticks = 0;
      return null;
    }
    throw new IllegalStateException("Unexpected shared host probe stage");
  }

  private static void mount(Screen screen, AbstractWidget widget, boolean add) throws Exception {
    // Observer-only mounting preserves the real workflow screen/actor. Production embedding
    // must use the library's forthcoming shell slots; no private renderer is installed here.
    var method =
        java.util.Arrays.stream(Screen.class.getDeclaredMethods())
            .filter(
                m ->
                    m.getName().equals(add ? "addRenderableWidget" : "removeWidget")
                        && m.getParameterCount() == 1)
            .findFirst()
            .orElseThrow();
    method.setAccessible(true);
    method.invoke(screen, widget);
  }
}
