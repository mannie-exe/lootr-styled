package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.operation.OperationSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Actual native save-only, confirmed save/failed reload, no-write retry, and value restoration. */
final class ProductionEffectsProbe {
  private enum Phase {
    OPEN,
    STAGE_SAVE_ONLY,
    SAVED_ONLY,
    STAGE_ENABLED,
    FAILED_RESULT,
    FAILED_CAPTURE,
    RETRY_READY,
    RETRIED_RESULT,
    RETRIED_CAPTURE,
    CLOSED
  }

  private static final FieldPath ENABLED = FieldPath.ROOT.field("enabled");
  private final Screen parent, root;
  private final Path settings;
  private final ClientSettings original;
  private final UUID oldGeneration;
  private final ClientEditingWorkflow workflow;
  private final CompletableFuture<?> admission;
  private final int initialReloadCalls;
  private final JsonObject result = new JsonObject();
  private ClientEditor<ClientSettings> editor;
  private CompletableFuture<?> pending;
  private byte[] savedBytes;
  private BasicFileAttributes savedAttributes;
  private String savedRevision;
  private UUID failedHandle;
  private Phase phase = Phase.OPEN;
  private int ticks;
  private boolean captured;

  ProductionEffectsProbe() throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = ClientSettings.parse(Files.readAllBytes(settings));
    oldGeneration = generation();
    initialReloadCalls = ReloadFault.calls();
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    admission =
        WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION)
            .thenAccept(value -> editor = value);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Production effects timed out: " + phase + ": " + result);
    if (!admission.isDone()) return null;
    admission.join();
    if (!workflow.isOpen() && phase != Phase.CLOSED)
      throw new IllegalStateException("Effects lost the retained workflow");
    switch (phase) {
      case OPEN -> {
        if (!UiProbe.hasButton(root, "Save settings")) return null;
        toggleEnabled();
        next(Phase.STAGE_SAVE_ONLY);
      }
      case STAGE_SAVE_ONLY -> {
        if (!enabledIs(!original.enabled()) || !UiProbe.button(root, "Save settings").active)
          return null;
        press("Save settings");
        next(Phase.SAVED_ONLY);
      }
      case SAVED_ONLY -> {
        if (ticks == 20) {
          result.addProperty("saveOnlyWaitingNarration", root.getNarrationMessage().getString());
          result.addProperty("saveOnlyWaitingBusy", busy());
          result.addProperty("saveOnlyWaitingCanSubmit", editor.canSubmit());
          result.addProperty(
              "saveOnlyWaitingDraftEnabled",
              ((DraftNode.Scalar) editor.view().snapshot().orElseThrow().draft().at(ENABLED))
                  .raw());
        }
        if (busy()
            || ClientSettings.parse(Files.readAllBytes(settings)).enabled() == original.enabled())
          return null;
        if (!oldGeneration.equals(generation()) || ReloadFault.calls() != initialReloadCalls)
          throw new IllegalStateException("Save settings also reloaded resources");
        result.addProperty("saveOnlyDoesNotReload", true);
        toggleEnabled();
        next(Phase.STAGE_ENABLED);
      }
      case STAGE_ENABLED -> {
        if (!enabledIs(original.enabled())
            || !UiProbe.button(root, "Save and reload resources").active) return null;
        ReloadFault.arm();
        press("Save and reload resources");
        next(Phase.FAILED_RESULT);
      }
      case FAILED_RESULT -> {
        var snapshot = operation();
        if (snapshot == null) return null;
        var facts = EffectProbeFacts.describe(snapshot);
        if (!facts.get("persistence").getAsString().equals("SAVED")
            || !facts.get("application").getAsString().equals("FAILED")
            || !facts.get("code").getAsString().equals("NATIVE_FAILURE")
            || !oldGeneration.equals(generation())
            || ReloadFault.failures() != 1
            || ReloadFault.calls() != initialReloadCalls + 1)
          throw new IllegalStateException("Failed reload lost its confirmed save: " + facts);
        savedBytes = Files.readAllBytes(settings);
        savedAttributes = Files.readAttributes(settings, BasicFileAttributes.class);
        savedRevision = ClientRuntime.settingsStore().read().revision();
        if (!savedRevision.equals(facts.get("savedRevision").getAsString())
            || !savedRevision.equals(facts.get("sourceRevision").getAsString())
            || !ClientSettings.parse(savedBytes).equals(original))
          throw new IllegalStateException("Failed reload did not retain the actual saved revision");
        failedHandle = snapshot.id();
        result.add("failedSaveReload", facts);
        result.addProperty("confirmedSaveSurvivesFailedReload", true);
        captured = false;
        NativeWorld.screenshot(mc, "production-effects-failed", () -> captured = true);
        next(Phase.FAILED_CAPTURE);
      }
      case FAILED_CAPTURE -> {
        if (!captured) return null;
        pending = WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION);
        next(Phase.RETRY_READY);
      }
      case RETRY_READY -> {
        if (!ready()
            || !UiProbe.hasButton(root, "Retry reload")
            || !UiProbe.button(root, "Retry reload").active) return null;
        unchangedSavedFile();
        press("Retry reload");
        next(Phase.RETRIED_RESULT);
      }
      case RETRIED_RESULT -> {
        var snapshot = operation();
        if (snapshot == null) return null;
        var facts = EffectProbeFacts.describe(snapshot);
        unchangedSavedFile();
        if (snapshot.id().equals(failedHandle)
            || facts.get("hasPersistenceReceipt").getAsBoolean()
            || !facts.get("application").getAsString().equals("APPLIED")
            || !savedRevision.equals(facts.get("sourceRevision").getAsString())
            || oldGeneration.equals(generation())
            || !generation().toString().equals(facts.get("generation").getAsString())
            || ReloadFault.failures() != 1
            || ReloadFault.calls() != initialReloadCalls + 2)
          throw new IllegalStateException(
              "Retry did not apply the saved revision exactly once: " + facts);
        result.add("retryReload", facts);
        result.addProperty("retryUsesSavedRevisionWithoutPersistence", true);
        result.addProperty("retryPreservesSavedFileIdentity", true);
        captured = false;
        NativeWorld.screenshot(mc, "production-effects-retried", () -> captured = true);
        next(Phase.RETRIED_CAPTURE);
      }
      case RETRIED_CAPTURE -> {
        if (!captured) return null;
        if (!ClientSettings.parse(Files.readAllBytes(settings)).equals(original)
            || StylePreferences.resourceStatus().state()
                != design.inherent.lootr_styled.client.StyleApplication.State.APPLIED
            || ReloadFault.failures() != 1
            || ReloadFault.calls() != initialReloadCalls + 2)
          throw new IllegalStateException("Effect check did not restore the saved values");
        result.addProperty("originalSavedValuesRestored", true);
        result.addProperty(
            "sameOwnerAndRoot",
            workflow.editor(StyleSettingsStore.DEFINITION) == editor && NativeUi.current() == root);
        result.addProperty("injectedFailures", ReloadFault.failures());
        press("Done");
        next(Phase.CLOSED);
      }
      case CLOSED -> {
        if (workflow.isOpen() || editor.isOpen() || NativeUi.current() != parent) return null;
        result.addProperty("closedCleanly", true);
        return result;
      }
    }
    return null;
  }

  private void unchangedSavedFile() throws Exception {
    var attributes = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(savedBytes, Files.readAllBytes(settings))
        || !savedAttributes.lastModifiedTime().equals(attributes.lastModifiedTime())
        || !java.util.Objects.equals(savedAttributes.fileKey(), attributes.fileKey()))
      throw new IllegalStateException("Retry rewrote or replaced the confirmed saved file");
  }

  private void toggleEnabled() {
    var cycle =
        UiProbe.children(root).stream()
            .filter(net.minecraft.client.gui.components.CycleButton.class::isInstance)
            .map(net.minecraft.client.gui.components.CycleButton.class::cast)
            .filter(value -> NativeBindings.narration(value).startsWith("Enabled: "))
            .findFirst()
            .orElseThrow();
    if (!cycle.active) throw new IllegalStateException("Enabled control is inactive");
    NativeBindings.press(cycle);
  }

  private boolean enabledIs(boolean value) {
    return !busy()
        && editor.canSubmit()
        && ((DraftNode.Scalar) editor.view().snapshot().orElseThrow().draft().at(ENABLED))
            .raw()
            .equals(Boolean.toString(value));
  }

  private boolean ready() {
    if (!pending.isDone()) return false;
    pending.join();
    return !busy();
  }

  private boolean busy() {
    return workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::busy);
  }

  private OperationSnapshot<?> operation() {
    if (NativeUi.current() != root
        || !(workflow.navigation().current().orElse(null)
            instanceof ClientDestination.OperationResult)) return null;
    return workflow
        .operationInspection()
        .flatMap(value -> value.observedOperation())
        .filter(value -> value.state() == OperationSnapshot.State.COMPLETED)
        .orElse(null);
  }

  private void press(String label) {
    var button = UiProbe.button(root, label);
    if (!button.active) throw new IllegalStateException("Inactive effect control: " + label);
    NativeBindings.press(button);
  }

  private static UUID generation() throws Exception {
    return PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
  }

  private void next(Phase value) {
    phase = value;
    ticks = 0;
  }

  String progress() {
    return phase.toString();
  }

  JsonObject observations() {
    return result.deepCopy();
  }
}
