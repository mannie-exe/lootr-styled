package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.*;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.minecraft.client.*;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.graphics.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Actual deferred native release failure, distinct from uncertain deletion or a failed draw. */
final class PreviewCleanupFailureProbe {
  enum Mode {
    DEFERRED,
    SYNCHRONOUS,
    REPLACED
  }

  private final Mode mode;
  private final ClientEditingWorkflow workflow;
  private final ClientEditor<ClientSettings> editor;
  private final ClientGraphicHost host;
  private final ClientGraphicHost.View<ClientSettings, Object> view;
  private final ClientGraphicProvider<ClientSettings, Object> provider;
  private final Path file;
  private final byte[] original;
  private final JsonObject result = new JsonObject();
  private CompletableFuture<?> pending;
  private ClientGraphicHost.Attempt attempt;
  private PreparedGraphic lease;
  private PreparedGraphic replacement;
  private Object subject;
  private final int initialFaultCount;
  private AbstractWidget widget;
  private int stage, ticks, settled;
  private boolean closeSubmitted;

  PreviewCleanupFailureProbe() throws Exception {
    this(Mode.DEFERRED);
  }

  @SuppressWarnings("unchecked") // Only the actual bounded provider codec constructs its subjects.
  PreviewCleanupFailureProbe(Mode mode) throws Exception {
    this.mode = mode;
    initialFaultCount = NativeCleanupFault.injected();
    file = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(file);
    workflow = StylePreferences.open(NativeUi.current());
    NativeUi.open(workflow.screen());
    workflow.show(StyleSettingsStore.DEFINITION.id());
    editor = workflow.editor(StyleSettingsStore.DEFINITION);
    pending =
        DocumentActions.result(editor.actions().ensureLoaded(EntryPoint.GUI)).toCompletableFuture();
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    var constructor =
        Class.forName("design.inherent.lootr_styled.client.StyleGraphicProvider")
            .getDeclaredConstructor(GraphicLimits.class);
    constructor.setAccessible(true);
    provider = (ClientGraphicProvider<ClientSettings, Object>) constructor.newInstance(limits);
    host = workflow.graphics(limits, 1);
    view = host.open(editor, provider);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Cleanup failure control timed out at " + stage);
    if (pending != null) {
      if (!pending.isDone()) return null;
      pending.join();
      pending = null;
    }
    if (stage == 0) {
      if (editor
          .form()
          .fieldControl(design.inherent.reform.definition.FieldPath.ROOT.field("models"))
          .isEmpty()) return null;
      subject =
          provider
              .subjectType()
              .codec()
              .parse(
                  com.mojang.serialization.JsonOps.INSTANCE,
                  com.google.gson.JsonParser.parseString(
                      "{\"container\":\"BARREL\",\"looted\":true,\"open\":true,\"lid\":1}"))
              .getOrThrow();
      attempt = view.request(subject);
      stage = 1;
      return null;
    }
    if (stage == 1) {
      if (!attempt.completion().toCompletableFuture().isDone()) return null;
      var outcome = attempt.completion().toCompletableFuture().join();
      if (outcome.state() != ClientGraphicHost.State.READY)
        throw new IllegalStateException("Actual cleanup-failure source was not ready: " + outcome);
      lease = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
      widget = view.widget(Component.literal("Deferred cleanup failure control"));
      widget.setPosition(12, 12);
      widget.setWidth(220);
      widget.setHeight(180);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      NativeCleanupFault.arm(lease);
      if (mode == Mode.SYNCHRONOUS) {
        result.addProperty("pendingUsesAtClose", lease.state().outstandingUses());
        if (lease.state().outstandingUses() != 0)
          throw new IllegalStateException("Synchronous control already has a native use");
        view.close();
        closeSubmitted = true;
        result.addProperty(
            "completedDuringClose", lease.releaseCompletion().toCompletableFuture().isDone());
        stage = 3;
        return null;
      }
      stage = 2;
      ticks = 0;
      return null;
    }
    if (stage == 2) {
      if (!workflow.screen().children().contains(widget))
        SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      if (ticks < 8 || closeSubmitted) return null;
      GraphicDrawAudit.afterNextRendered(
          () -> {
            result.addProperty("pendingUsesAtClose", lease.state().outstandingUses());
            if (lease.state().outstandingUses() < 1)
              throw new IllegalStateException("No submitted use for asynchronous cleanup");
            if (mode == Mode.REPLACED) attempt = view.request(subject);
            else view.close();
            closeSubmitted = true;
          });
      stage = 3;
      return null;
    }
    if (stage == 3) {
      if (!closeSubmitted || !lease.releaseCompletion().toCompletableFuture().isDone()) return null;
      if (++settled < 8) return null;
      var release = lease.releaseCompletion().toCompletableFuture().join();
      if (mode == Mode.REPLACED) {
        if (!attempt.completion().toCompletableFuture().isDone()) return null;
        var latest = attempt.completion().toCompletableFuture().join();
        result.addProperty("newerReady", latest.state() == ClientGraphicHost.State.READY);
        result.addProperty(
            "newerCauseAbsent", latest.cause().isEmpty() && view.failure().isEmpty());
        result.addProperty("newerDisplayed", latest.stamp().equals(view.displayedStamp()));
        if (latest.state() != ClientGraphicHost.State.READY
            || latest.cause().isPresent()
            || view.failure().isPresent()
            || !latest.stamp().equals(view.displayedStamp()))
          throw new IllegalStateException(
              "Old cleanup failure replaced the newer result: " + result);
        replacement = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
      }
      result.addProperty("injected", NativeCleanupFault.injected() - initialFaultCount);
      result.addProperty(
          "actualInjectedCause",
          release.releaseFailure().filter(NativeCleanupFault::caused).isPresent());
      result.addProperty("releaseAttempted", release.releaseAttempted());
      result.addProperty("releaseCompleted", release.releaseCompleted());
      result.addProperty("released", release.released());
      result.addProperty("outstandingUses", release.outstandingUses());
      result.addProperty("nativeBytes", host.nativeOwnership().bytes());
      result.addProperty("sceneBytes", host.retainedOutput().bytes());
      result.addProperty("hostFailureCount", host.releaseFailureCount());
      result.addProperty("viewState", view.snapshot().state().name());
      result.addProperty(
          "hostHasCause", host.releaseFailure().filter(NativeCleanupFault::caused).isPresent());
      var codes = new com.google.gson.JsonArray();
      view.snapshot().report().issues().forEach(i -> codes.add(i.code()));
      result.add("viewCodes", codes);
      result.addProperty(
          "settingsUnchanged", java.util.Arrays.equals(original, Files.readAllBytes(file)));
      writeResult(mc);
      if (NativeCleanupFault.injected() - initialFaultCount != 1
          || !release.releaseFailure().filter(NativeCleanupFault::caused).isPresent()
          || release.released()
          || release.releaseCompleted()
          || release.outstandingUses() != 0
          || (mode != Mode.REPLACED && host.nativeOwnership().bytes() != 0)
          || host.retainedOutput().bytes() <= 0)
        throw new IllegalStateException(
            "Cleanup fault did not preserve truthful release state/accounting: " + result);
      if (host.releaseFailureCount() != 1
          || host.releaseFailure().filter(NativeCleanupFault::caused).isEmpty()
          || view.snapshot().report().issues().stream()
              .noneMatch(i -> i.code().equals("preview.release_failed")))
        throw new IllegalStateException(
            "Asynchronous cleanup failure was not surfaced by the host: " + result);
      if (mode != Mode.REPLACED && view.snapshot().state() != ClientGraphicHost.State.CLOSED)
        throw new IllegalStateException("Cleanup failure reopened a closed view: " + result);
      if (mode == Mode.SYNCHRONOUS && !result.get("completedDuringClose").getAsBoolean())
        throw new IllegalStateException("Immediate cleanup did not settle during close: " + result);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, false);
      view.close();
      if (replacement != null) {
        stage = 4;
        return null;
      }
      host.close();
      workflow.close();
      writeResult(mc);
      return result;
    }
    if (stage == 4) {
      if (!replacement.releaseCompletion().toCompletableFuture().isDone()) return null;
      result.addProperty(
          "replacementReleased",
          replacement.releaseCompletion().toCompletableFuture().join().released());
      result.addProperty("finalHostFailureCount", host.releaseFailureCount());
      result.addProperty("finalNativeBytes", host.nativeOwnership().bytes());
      result.addProperty("finalSceneBytes", host.retainedOutput().bytes());
      if (!result.get("replacementReleased").getAsBoolean()
          || host.releaseFailureCount() != 1
          || host.nativeOwnership().bytes() != 0)
        throw new IllegalStateException("Replacement did not retire independently: " + result);
      host.close();
      workflow.close();
      writeResult(mc);
      return result;
    }
    return null;
  }

  private void writeResult(Minecraft mc) throws java.io.IOException {
    String suffix =
        mode == Mode.DEFERRED ? "" : "-" + mode.name().toLowerCase(java.util.Locale.ROOT);
    Files.writeString(
        mc.gameDirectory.toPath().resolve("lootr-styled-cleanup-failure" + suffix + "-report.json"),
        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
  }
}
