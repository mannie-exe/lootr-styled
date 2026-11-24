package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.NavigationAttempt;
import design.inherent.reform.operation.OperationSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;

/** Confirmed save plus cooperative cancellation, using the actual retained operation and UI. */
final class ProductionCancellationProbe {
  private enum Phase {
    OPEN,
    STAGED,
    RUNNING,
    REQUESTED,
    RETURNED,
    WAIT_CAPTURE,
    REVEALED,
    UNKNOWN,
    UNKNOWN_CAPTURE,
    RETRY_READY,
    RETRIED,
    RESTORE_READY,
    RESTORE_STAGED,
    RESTORED
  }

  private static final FieldPath ENABLED = FieldPath.ROOT.field("enabled");
  private final ClientEditingWorkflow workflow;
  private final ClientEditor<ClientSettings> editor;
  private final Screen root;
  private final Path settings;
  private final ClientSettings original;
  private final UUID oldGeneration;
  private final int initialCalls, initialRequests, initialReleases;
  private final JsonObject result = new JsonObject();
  private CompletableFuture<?> pending;
  private ClientDestination destination;
  private UUID handle, unknownGeneration;
  private byte[] saved;
  private BasicFileAttributes attributes;
  private String revision;
  private Phase phase = Phase.OPEN;
  private int ticks, captureTicks;
  private boolean requestedCapture, captured;

  ProductionCancellationProbe(ClientEditingWorkflow workflow, ClientEditor<ClientSettings> editor)
      throws Exception {
    this.workflow = workflow;
    this.editor = editor;
    root = workflow.screen();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = ClientSettings.parse(Files.readAllBytes(settings));
    oldGeneration = generation();
    initialCalls = ReloadFault.calls();
    initialRequests = ReloadGate.requests();
    initialReleases = ReloadGate.releases();
    pending = WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Cancellation timed out: " + phase + ": " + result);
    if (!workflow.isOpen()
        || workflow.editor(StyleSettingsStore.DEFINITION) != editor
        || NativeUi.current() != root)
      throw new IllegalStateException("Cancellation lost its owner/root");
    switch (phase) {
      case OPEN -> {
        if (!ready() || !editor.canSubmit()) return null;
        toggle();
        next(Phase.STAGED);
      }
      case STAGED -> {
        if (!enabled(!original.enabled())
            || !UiProbe.button(root, "Save and reload resources").active) return null;
        ReloadGate.arm();
        press("Save and reload resources");
        next(Phase.RUNNING);
      }
      case RUNNING -> {
        var observed = operation();
        if (observed == null
            || observed.terminal()
            || !ReloadGate.holding()
            || !UiProbe.hasButton(root, "Request cancellation")
            || !UiProbe.button(root, "Request cancellation").active) return null;
        handle = observed.id();
        destination = workflow.navigation().current().orElseThrow();
        saved = Files.readAllBytes(settings);
        attributes = Files.readAttributes(settings, BasicFileAttributes.class);
        revision = ClientRuntime.settingsStore().read().revision();
        if (ClientSettings.parse(saved).enabled() == original.enabled())
          throw new IllegalStateException("Cancellation preceded the actual save");
        held();
        press("Request cancellation");
        next(Phase.REQUESTED);
      }
      case REQUESTED -> {
        var observed = operation();
        if (observed == null || !observed.cancellationRequested()) return null;
        if (observed.terminal()
            || !observed.id().equals(handle)
            || observed.state() != OperationSnapshot.State.CANCELLATION_REQUESTED)
          throw new IllegalStateException("Cancellation claimed to stop pending work");
        held();
        result.addProperty("requestRetainsPendingWork", true);
        NativeBindings.escape(root);
        next(Phase.RETURNED);
      }
      case RETURNED -> {
        result.addProperty("escapeReturnDestination", workflow.navigation().current().toString());
        if (workflow.operationInspection().isPresent() || !UiProbe.hasButton(root, "Save settings"))
          return null;
        held();
        NativeBindings.escape(root);
        next(Phase.WAIT_CAPTURE);
      }
      case WAIT_CAPTURE -> {
        held();
        String narration = root.getNarrationMessage().getString();
        if (!narration.contains("Wait for pending operations")) return null;
        result.addProperty("pendingCloseNarration", narration);
        if (!capture(mc, "production-effects-cancel-pending")) return null;
        result.addProperty("escapeReturnsWithoutCancellingOwner", true);
        result.addProperty("pendingWorkPreventsWorkspaceClose", true);
        var attempt = workflow.navigation().open(destination);
        pending =
            attempt
                .completion()
                .thenAccept(
                    value -> {
                      if (!value.id().equals(attempt.id())
                          || !value.target().equals(destination)
                          || value.outcome() != NavigationAttempt.Outcome.REVEALED)
                        throw new IllegalStateException(
                            "Pending result was not actually revealed: " + value);
                    })
                .toCompletableFuture();
        next(Phase.REVEALED);
      }
      case REVEALED -> {
        if (!ready()) return null;
        var observed = operation();
        if (observed == null
            || !observed.id().equals(handle)
            || !observed.cancellationRequested()
            || observed.terminal())
          throw new IllegalStateException("Returning to the result replaced or ended its handle");
        held();
        ReloadGate.release();
        next(Phase.UNKNOWN);
      }
      case UNKNOWN -> {
        var observed = operation();
        if (observed == null || !observed.terminal()) return null;
        var facts = EffectProbeFacts.describe(observed);
        unchanged();
        if (observed.state() != OperationSnapshot.State.COMPLETED
            || !observed.cancellationRequested()
            || !observed.id().equals(handle)
            || !facts.get("persistence").getAsString().equals("SAVED")
            || !facts.get("application").getAsString().equals("UNKNOWN")
            || !facts.get("code").getAsString().equals("CANCELLED")
            || !revision.equals(facts.get("savedRevision").getAsString())
            || !revision.equals(facts.get("sourceRevision").getAsString())
            || oldGeneration.equals(generation())
            || ReloadFault.calls() != initialCalls + 1
            || ReloadGate.requests() != initialRequests + 1
            || ReloadGate.releases() != initialReleases + 1
            || ReloadGate.holding())
          throw new IllegalStateException(
              "Cancellation lost the confirmed save/uncertainty: " + facts);
        unknownGeneration = generation();
        result.add("cancelledSaveReload", facts);
        result.addProperty("cancellationRequested", observed.cancellationRequested());
        result.addProperty("publishedGenerationAfterCancellation", unknownGeneration.toString());
        result.addProperty("confirmedSaveAndUnknownApplication", true);
        next(Phase.UNKNOWN_CAPTURE);
      }
      case UNKNOWN_CAPTURE -> {
        unchanged();
        if (!capture(mc, "production-effects-cancelled")) return null;
        pending = WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION);
        next(Phase.RETRY_READY);
      }
      case RETRY_READY -> {
        if (!ready()
            || !UiProbe.hasButton(root, "Retry reload")
            || !UiProbe.button(root, "Retry reload").active) return null;
        unchanged();
        press("Retry reload");
        next(Phase.RETRIED);
      }
      case RETRIED -> {
        var observed = operation();
        if (observed == null || !observed.terminal()) return null;
        var facts = EffectProbeFacts.describe(observed);
        unchanged();
        if (observed.state() != OperationSnapshot.State.COMPLETED
            || observed.cancellationRequested()
            || observed.id().equals(handle)
            || facts.get("hasPersistenceReceipt").getAsBoolean()
            || !facts.get("application").getAsString().equals("APPLIED")
            || !revision.equals(facts.get("sourceRevision").getAsString())
            || !generation().toString().equals(facts.get("generation").getAsString())
            || unknownGeneration.equals(generation())
            || ReloadFault.calls() != initialCalls + 2)
          throw new IllegalStateException(
              "Cancellation retry changed the save or lost publication: " + facts);
        result.add("retryAfterCancellation", facts);
        result.addProperty("retryPreservesSavedFileIdentity", true);
        pending = WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION);
        next(Phase.RESTORE_READY);
      }
      case RESTORE_READY -> {
        if (!ready() || !editor.canSubmit()) return null;
        toggle();
        next(Phase.RESTORE_STAGED);
      }
      case RESTORE_STAGED -> {
        if (!enabled(original.enabled())
            || !UiProbe.button(root, "Save and reload resources").active) return null;
        press("Save and reload resources");
        next(Phase.RESTORED);
      }
      case RESTORED -> {
        var observed = operation();
        if (observed == null || !observed.terminal()) return null;
        var facts = EffectProbeFacts.describe(observed);
        if (observed.state() != OperationSnapshot.State.COMPLETED
            || !facts.get("persistence").getAsString().equals("SAVED")
            || !facts.get("application").getAsString().equals("APPLIED")
            || !ClientSettings.parse(Files.readAllBytes(settings)).equals(original)
            || !generation().toString().equals(facts.get("generation").getAsString())
            || ReloadFault.calls() != initialCalls + 3)
          throw new IllegalStateException(
              "Cancellation fixture did not restore saved values: " + facts);
        result.add("restoredSaveReload", facts);
        result.addProperty("originalSavedValuesRestored", true);
        result.addProperty("nativeReloads", ReloadFault.calls() - initialCalls);
        return result;
      }
    }
    return null;
  }

  private void held() throws Exception {
    unchanged();
    if (!ReloadGate.holding()
        || ReloadGate.requests() != initialRequests + 1
        || ReloadGate.releases() != initialReleases
        || ReloadFault.calls() != initialCalls
        || !oldGeneration.equals(generation()))
      throw new IllegalStateException("Pending reload entered native code or changed source");
  }

  private void unchanged() throws Exception {
    var now = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(saved, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !Objects.equals(attributes.fileKey(), now.fileKey()))
      throw new IllegalStateException("Cancellation or retry rewrote the saved file");
  }

  private boolean enabled(boolean value) {
    return editor.canSubmit()
        && ((DraftNode.Scalar) editor.view().snapshot().orElseThrow().draft().at(ENABLED))
            .raw()
            .equals(Boolean.toString(value));
  }

  private void toggle() {
    var control =
        UiProbe.children(root).stream()
            .filter(CycleButton.class::isInstance)
            .map(CycleButton.class::cast)
            .filter(value -> NativeBindings.narration(value).startsWith("Enabled: "))
            .findFirst()
            .orElseThrow();
    if (!control.active) throw new IllegalStateException("Enabled control is inactive");
    NativeBindings.press(control);
  }

  private void press(String label) {
    var control = UiProbe.button(root, label);
    if (!control.active) throw new IllegalStateException("Inactive cancellation control: " + label);
    NativeBindings.press(control);
  }

  private OperationSnapshot<?> operation() {
    return workflow.operationInspection().flatMap(value -> value.observedOperation()).orElse(null);
  }

  private boolean ready() {
    if (!pending.isDone()) return false;
    pending.join();
    return true;
  }

  private boolean capture(Minecraft mc, String name) throws Exception {
    if (!NativeBindings.unobscured(mc)) {
      captureTicks = 0;
      return false;
    }
    if (++captureTicks < 4) return false;
    if (!requestedCapture) {
      requestedCapture = true;
      NativeWorld.screenshot(mc, name, () -> captured = true);
    }
    return captured;
  }

  private static UUID generation() throws Exception {
    return PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
  }

  private void next(Phase value) {
    phase = value;
    ticks = captureTicks = 0;
    requestedCapture = captured = false;
  }

  String progress() {
    return phase.toString();
  }

  JsonObject observations() {
    return result.deepCopy();
  }
}
