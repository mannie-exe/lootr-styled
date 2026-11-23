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
  private ClientEditor<ClientSettings> editor;
  private final byte[] originalSettings;
  private final PreviewProbeAccess.HostProvider provider;
  private final ClientGraphicHost host;
  private ClientGraphicHost.View<ClientSettings, Object> view;
  private final JsonArray subjects = new JsonArray();
  private CompletableFuture<?> pending;
  private ClientGraphicHost.Attempt attempt;
  private PreparedGraphic lease;
  private AbstractWidget widget;
  private int stage, index, ticks, callsAtMount;
  private boolean useSeen;
  private long drawsAtMount;
  private boolean invalidDraftRefused, changedDraftRevoked;
  private final JsonArray refusalCodes = new JsonArray();
  private final PreviewWorkerGate gate = new PreviewWorkerGate();
  private ProviderLifetimeProbe lifetime;
  private JsonObject lifetimeResult;
  private volatile boolean screenshot;

  SharedGraphicHostProbe() throws Exception {
    parent = NativeUi.current();
    originalSettings =
        Files.readAllBytes(ClientRuntime.configuration().root().resolve("settings.json"));
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    provider = PreviewProbeAccess.hostProvider(limits, gate);
    host = workflow.graphics(limits, 1);
    pending =
        WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION)
            .thenAccept(
                opened -> {
                  editor = opened;
                  view = host.open(opened, provider.provider());
                });
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
        pending = edit("models", "not an id");
        stage = 5;
        ticks = 0;
        return null;
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
      drawsAtMount = GraphicDrawAudit.state().rendered();
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
      if (GraphicDrawAudit.state().rendered() <= drawsAtMount)
        throw new IllegalStateException(
            "The shared viewport did not render: "
                + view.snapshot()
                + " "
                + GraphicDrawAudit.state());
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
      subject.addProperty("nativeUseObserved", useSeen);
      subject.addProperty(
          "nativeDrawConfirmed", GraphicDrawAudit.state().rendered() > drawsAtMount);
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
    if (stage == 5) {
      if (++ticks < 4) return null;
      if (editor.view().validation().valid() || !editor.actions().mayInspect())
        throw new IllegalStateException("Invalid-draft contrast did not preserve READ permission");
      view.visible(true);
      callsAtMount = provider.materialCalls().get();
      try {
        attempt = view.request(lastSubject());
        stage = 6;
      } catch (design.inherent.reform.owner.PolicyRefusal refused) {
        refused.report().issues().forEach(issue -> refusalCodes.add(issue.code()));
        invalidDraftRefused = true;
        pending = edit("models", ClientSettings.DEFAULT.toString());
        stage = 7;
        ticks = 0;
      }
      return null;
    }
    if (stage == 6) {
      var future = attempt.completion().toCompletableFuture();
      if (!future.isDone()) return null;
      var outcome = future.join();
      if (outcome.state() != ClientGraphicHost.State.REFUSED || outcome.report().valid())
        throw new IllegalStateException(
            "Invalid input did not produce a structured refusal: " + outcome);
      outcome.report().issues().forEach(issue -> refusalCodes.add(issue.code()));
      invalidDraftRefused = true;
      pending = edit("models", ClientSettings.DEFAULT.toString());
      stage = 7;
      ticks = 0;
      return null;
    }
    if (stage == 7) {
      if (++ticks < 4) return null;
      if (provider.materialCalls().get() != callsAtMount
          || host.nativeOwnership().bytes() != 0
          || host.retainedOutput().bytes() != 0)
        throw new IllegalStateException(
            "Invalid draft entered domain preparation or retained resources");
      if (!editor.view().validation().valid())
        throw new IllegalStateException("Explicit repair did not restore a valid draft");
      attempt = view.request(lastSubject());
      stage = 8;
      return null;
    }
    if (stage == 8) {
      var future = attempt.completion().toCompletableFuture();
      if (!future.isDone()) return null;
      if (future.join().state() != ClientGraphicHost.State.READY)
        throw new IllegalStateException(
            "A repaired draft could not prepare a new preview: " + future.join());
      lease = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
      widget = view.widget(Component.literal("Draft-revision retirement control"));
      widget.setPosition(12, 12);
      widget.setWidth(220);
      widget.setHeight(180);
      mount(workflow.screen(), widget, true);
      stage = 9;
      drawsAtMount = GraphicDrawAudit.state().rendered();
      ticks = 0;
      useSeen = false;
      callsAtMount = provider.materialCalls().get();
      return null;
    }
    if (stage == 9) {
      if (!workflow.screen().children().contains(widget)) mount(workflow.screen(), widget, true);
      useSeen |= lease.state().outstandingUses() > 0;
      if (++ticks < 16) return null;
      if (GraphicDrawAudit.state().rendered() <= drawsAtMount)
        throw new IllegalStateException(
            "Revision control did not render: "
                + view.snapshot()
                + " "
                + GraphicDrawAudit.state()
                + " "
                + lease.state());
      pending = edit("freshObjects", "OFF");
      stage = 10;
      ticks = 0;
      return null;
    }
    if (stage == 10) {
      if (++ticks > 160)
        throw new IllegalStateException("Changed draft did not revoke the prepared graphic");
      if (view.snapshot().state() != ClientGraphicHost.State.STALE
          || !lease.releaseCompletion().toCompletableFuture().isDone()) return null;
      if (view.preparation().orElseThrow() instanceof GraphicPreparation.Ready)
        throw new IllegalStateException("Changed draft still offers the old prepared graphic");
      if (!lease.releaseCompletion().toCompletableFuture().join().released()
          || provider.materialCalls().get() != callsAtMount)
        throw new IllegalStateException(
            "Revision change failed retirement or implicitly recompiled");
      mount(workflow.screen(), widget, false);
      view.visible(false);
      changedDraftRevoked = true;
      stage = 11;
      return null;
    }
    if (stage == 11) {
      if (lifetime == null) {
        view.close();
        lifetime =
            new ProviderLifetimeProbe(
                workflow, editor, host, provider, gate, lastSubject(), parent);
      }
      lifetimeResult = lifetime.tick(mc);
      return lifetimeResult == null ? null : finish();
    }
    throw new IllegalStateException("Unexpected shared host probe stage");
  }

  private CompletableFuture<?> edit(String field, String raw) {
    var snapshot = editor.view().snapshot().orElseThrow();
    return DocumentActions.result(
            editor
                .actions()
                .edit(
                    EntryPoint.GUI,
                    new DraftEdit(
                        snapshot.target(FieldPath.ROOT.field(field)), new DraftEdit.SetRaw(raw))))
        .toCompletableFuture();
  }

  private Object lastSubject() {
    return provider
        .provider()
        .subjectType()
        .codec()
        .parse(
            com.mojang.serialization.JsonOps.INSTANCE,
            com.google.gson.JsonParser.parseString(
                "{\"container\":\"BARREL\",\"looted\":true,\"open\":true,\"lid\":1}"))
        .getOrThrow();
  }

  private JsonObject finish() throws Exception {
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
    result.addProperty("invalidDraftRefused", invalidDraftRefused);
    result.add("refusalCodes", refusalCodes);
    result.addProperty("repairedDraftPrepared", true);
    result.addProperty("changedDraftRevoked", changedDraftRevoked);
    result.add("lifetime", lifetimeResult);
    result.addProperty("settingsUnchanged", true);
    result.addProperty("unsavedArtworkCaptured", true);
    result.addProperty("nativeBytesAfterClose", host.nativeOwnership().bytes());
    result.addProperty("sceneBytesAfterClose", host.retainedOutput().bytes());
    result.addProperty(
        "scope",
        "Actual provider READ/worker/shared native host, actor invalidation and bounded cancel/reload/close controls; not final production layout or exhaustive source/native-input/shutdown/cost qualification");
    return result;
  }

  static void mount(Screen screen, AbstractWidget widget, boolean add) {
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
    try {
      method.setAccessible(true);
      method.invoke(screen, widget);
    } catch (ReflectiveOperationException failed) {
      throw new IllegalStateException("The verification viewport could not be mounted", failed);
    }
  }
}
