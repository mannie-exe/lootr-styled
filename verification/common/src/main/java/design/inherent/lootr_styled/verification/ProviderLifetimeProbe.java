package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.minecraft.client.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Failure controls over the shared host and actual provider, never shipped with the mod. */
final class ProviderLifetimeProbe {
  private enum Phase {
    CANCEL_ENTERED,
    CANCEL_QUEUED,
    CANCEL_DRAIN,
    CANCEL_RECOVER,
    RESOURCE_ENTERED,
    RESOURCE_RELOAD,
    RESOURCE_DRAIN,
    RESOURCE_RECOVER,
    RESOURCE_DRAW,
    RESOURCE_RELEASE,
    CLOSE_ENTERED,
    CLOSE_HELD,
    CLOSE_DRAIN,
    DONE
  }

  private final ClientEditingWorkflow workflow;
  private final ClientEditor<ClientSettings> editor;
  private final ClientGraphicHost host;
  private final PreviewProbeAccess.HostProvider provider;
  private final PreviewWorkerGate gate;
  private final Object subject;
  private final Screen parent;
  private final JsonObject result = new JsonObject();
  private final JsonArray terminals = new JsonArray();
  private ClientGraphicHost.View<ClientSettings, Object> view;
  private ClientGraphicHost.Attempt attempt, replacement;
  private PreviewWorkerGate.Hold hold;
  private CompletableFuture<Void> reload;
  private PreparedGraphic lease;
  private AbstractWidget widget;
  private Phase phase = Phase.CANCEL_ENTERED;
  private int ticks, callsAtEntry;
  private long drawsBefore;
  private boolean closedAtDrawReturn;
  private java.util.UUID resourceBefore;

  ProviderLifetimeProbe(
      ClientEditingWorkflow workflow,
      ClientEditor<ClientSettings> editor,
      ClientGraphicHost host,
      PreviewProbeAccess.HostProvider provider,
      PreviewWorkerGate gate,
      Object subject,
      Screen parent) {
    this.workflow = workflow;
    this.editor = editor;
    this.host = host;
    this.provider = provider;
    this.gate = gate;
    this.subject = subject;
    this.parent = parent;
    beginHeld();
  }

  JsonObject tick(Minecraft mc) {
    if (++ticks > 400)
      throw new IllegalStateException("Provider lifetime control timed out: " + phase);
    switch (phase) {
      case CANCEL_ENTERED -> {
        if (!entered()) return null;
        view.cancel();
        replacement = view.request(subject);
        next(Phase.CANCEL_QUEUED);
      }
      case CANCEL_QUEUED -> {
        if (ticks < 8) return null;
        if (hold.exited
            || attempt.completion().toCompletableFuture().isDone()
            || replacement.operation().isPresent()
            || provider.materialCalls().get() != callsAtEntry)
          throw new IllegalStateException(
              "Replacement bypassed entered work or cancellation claimed early completion");
        result.addProperty("replacementWaitsForEnteredWork", true);
        hold.release();
        next(Phase.CANCEL_DRAIN);
      }
      case CANCEL_DRAIN -> {
        if (!attempt.completion().toCompletableFuture().isDone()) return null;
        var stopped = attempt.completion().toCompletableFuture().join();
        if (!hold.exited || stopped.state() != ClientGraphicHost.State.CANCELLED)
          throw new IllegalStateException(
              "Entered cancellation did not drain truthfully: " + stopped);
        terminal("cancel", stopped);
        attempt = replacement;
        next(Phase.CANCEL_RECOVER);
      }
      case CANCEL_RECOVER -> {
        if (!ready()) return null;
        view.close();
        next(Phase.RESOURCE_ENTERED);
      }
      case RESOURCE_ENTERED -> {
        if (lease != null) {
          if (!released()) return null;
          lease = null;
          beginHeld();
          return null;
        }
        if (!entered()) return null;
        resourceBefore = provider.provider().resourceStamp().snapshot();
        reload = mc.reloadResourcePacks();
        next(Phase.RESOURCE_RELOAD);
      }
      case RESOURCE_RELOAD -> {
        if (!reload.isDone()) return null;
        reload.join();
        if (provider.provider().resourceStamp().snapshot().equals(resourceBefore)) return null;
        if (ticks < 8) return null;
        if (hold.exited
            || attempt.completion().toCompletableFuture().isDone()
            || view.snapshot().state() == ClientGraphicHost.State.READY)
          throw new IllegalStateException("Resource reload admitted old entered output");
        result.addProperty("reloadChangesActualResourceStamp", true);
        hold.release();
        next(Phase.RESOURCE_DRAIN);
      }
      case RESOURCE_DRAIN -> {
        if (!attempt.completion().toCompletableFuture().isDone() || ticks < 4) return null;
        var stale = attempt.completion().toCompletableFuture().join();
        if (!hold.exited
            || (stale.state() != ClientGraphicHost.State.CANCELLED
                && stale.state() != ClientGraphicHost.State.REFUSED))
          throw new IllegalStateException("Old-generation preparation was not discarded: " + stale);
        terminal("reload", stale);
        noPayload();
        attempt = view.request(subject);
        next(Phase.RESOURCE_RECOVER);
      }
      case RESOURCE_RECOVER -> {
        if (!ready()) return null;
        if (!lease.stamp().resources().equals(provider.provider().resourceStamp()))
          throw new IllegalStateException("Recovered preview retained the old resource generation");
        widget = view.widget(Component.literal("Current-generation lifetime control"));
        widget.setPosition(12, 12);
        widget.setWidth(220);
        widget.setHeight(180);
        SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
        drawsBefore = GraphicDrawAudit.state().rendered();
        next(Phase.RESOURCE_DRAW);
      }
      case RESOURCE_DRAW -> {
        if (!workflow.screen().children().contains(widget))
          SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
        if (view.failure().isPresent())
          throw new IllegalStateException(
              "Fresh-generation native draw failed", view.failure().orElseThrow());
        if (ticks == 8) {
          GraphicDrawAudit.afterNextRendered(
              () -> {
                result.addProperty("pendingUsesAtDrawClose", lease.state().outstandingUses());
                view.close();
                closedAtDrawReturn = true;
              });
        }
        if (!closedAtDrawReturn || GraphicDrawAudit.state().rendered() <= drawsBefore) return null;
        SharedGraphicHostProbe.mount(workflow.screen(), widget, false);
        result.addProperty("freshGenerationDrawConfirmed", true);
        result.addProperty("closeAtActualDrawReturn", true);
        next(Phase.RESOURCE_RELEASE);
      }
      case RESOURCE_RELEASE -> {
        if (!released()) return null;
        lease = null;
        beginHeld();
        next(Phase.CLOSE_ENTERED);
      }
      case CLOSE_ENTERED -> {
        if (!entered()) return null;
        workflow.close();
        NativeUi.open(parent);
        if (workflow.isOpen() || editor.isOpen())
          throw new IllegalStateException("Workflow closure retained editing authority");
        next(Phase.CLOSE_HELD);
      }
      case CLOSE_HELD -> {
        if (ticks < 8) return null;
        result.addProperty(
            "closeResultBeforeWorkerExit", attempt.completion().toCompletableFuture().isDone());
        if (hold.exited || view.snapshot().state() != ClientGraphicHost.State.CLOSED)
          throw new IllegalStateException(
              "Closed preview did not stay closed while entered work remained");
        hold.release();
        next(Phase.CLOSE_DRAIN);
      }
      case CLOSE_DRAIN -> {
        if (!attempt.completion().toCompletableFuture().isDone() || !hold.exited) return null;
        var closed = attempt.completion().toCompletableFuture().join();
        if (closed.state() == ClientGraphicHost.State.READY
            || view.preparation().stream().anyMatch(GraphicPreparation.Ready.class::isInstance))
          throw new IllegalStateException("Late closed-workflow result published a preview");
        terminal("close", closed);
        noPayload();
        result.addProperty("closedWorkflowDiscardsLateResult", true);
        result.add("terminalResults", terminals);
        result.addProperty("nativeBytes", host.nativeOwnership().bytes());
        result.addProperty("sceneBytes", host.retainedOutput().bytes());
        next(Phase.DONE);
      }
      case DONE -> {
        return result;
      }
    }
    return null;
  }

  private void beginHeld() {
    view = host.open(editor, provider.provider());
    hold = gate.arm();
    attempt = view.request(subject);
  }

  private boolean entered() {
    if (!hold.entered) return false;
    if (hold.exited || attempt.operation().isEmpty())
      throw new IllegalStateException(
          "Lifetime control did not retain its actual entered operation");
    callsAtEntry = provider.materialCalls().get();
    return true;
  }

  private boolean ready() {
    if (!attempt.completion().toCompletableFuture().isDone()) return false;
    var outcome = attempt.completion().toCompletableFuture().join();
    if (outcome.state() != ClientGraphicHost.State.READY)
      throw new IllegalStateException("Lifetime positive control failed: " + outcome);
    lease = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
    return true;
  }

  private boolean released() {
    var release = lease.releaseCompletion().toCompletableFuture();
    if (!release.isDone()) return false;
    if (!release.join().released())
      throw new IllegalStateException("Positive-control release failed");
    noPayload();
    return true;
  }

  private void noPayload() {
    if (host.nativeOwnership().bytes() != 0 || host.retainedOutput().bytes() != 0)
      throw new IllegalStateException("Lifetime control retained native/scene payload");
  }

  private void terminal(String control, ClientGraphicHost.Result value) {
    var row = new JsonObject();
    row.addProperty("control", control);
    row.addProperty("state", value.state().name());
    var codes = new JsonArray();
    value.report().issues().forEach(issue -> codes.add(issue.code()));
    row.add("codes", codes);
    terminals.add(row);
  }

  private void next(Phase phase) {
    this.phase = phase;
    ticks = 0;
  }
}
