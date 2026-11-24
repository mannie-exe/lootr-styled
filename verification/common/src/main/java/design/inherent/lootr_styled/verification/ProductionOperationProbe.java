package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.edit.DraftRevision;
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

/** Actual running operation/disclosure across window events, then one delegated native reload. */
final class ProductionOperationProbe {
  private enum Phase {
    OPEN,
    RUNNING,
    NARROW,
    NARROW_IMAGE,
    RETURNED,
    RETURNED_IMAGE,
    COMPLETED,
    COMPLETED_IMAGE,
    CLOSED
  }

  private final Screen parent, root;
  private final ClientEditingWorkflow workflow;
  private final CompletableFuture<?> admission;
  private final Path settings;
  private final byte[] original;
  private final BasicFileAttributes attributes;
  private final UUID oldGeneration;
  private final String savedRevision;
  private final int[] window;
  private final int initialReloadCalls, initialReloadFailures;
  private final JsonObject result = new JsonObject();
  private final JsonArray states = new JsonArray();
  private ClientEditor<ClientSettings> editor;
  private DraftRevision revision;
  private UUID handle;
  private int rootWidth, rootHeight, ticks, captureTicks;
  private boolean captureRequested, captured;
  private Phase phase = Phase.OPEN;

  ProductionOperationProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    attributes = Files.readAttributes(settings, BasicFileAttributes.class);
    savedRevision = ClientRuntime.settingsStore().read().revision();
    oldGeneration = generation();
    window = NativeBindings.windowSize(mc);
    initialReloadCalls = ReloadFault.calls();
    initialReloadFailures = ReloadFault.failures();
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    admission =
        WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION)
            .thenAccept(value -> editor = value);
    result.add("states", states);
    result.addProperty("savedRevision", savedRevision);
    result.addProperty("oldGeneration", oldGeneration.toString());
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Operation timed out: " + phase + ": " + observations());
    if (!admission.isDone()) return null;
    admission.join();
    unchanged();
    switch (phase) {
      case OPEN -> {
        if (!editor.canSubmit()
            || !UiProbe.hasButton(root, "Reload resources")
            || !UiProbe.button(root, "Reload resources").active) return null;
        revision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        ReloadGate.arm();
        press("Reload resources");
        next(Phase.RUNNING);
      }
      case RUNNING -> {
        var observed = operation();
        if (observed == null
            || observed.state() != OperationSnapshot.State.RUNNING
            || !ReloadGate.holding()) return null;
        if (!UiProbe.hasButton(root, "Show technical details")
            || !UiProbe.button(root, "Show technical details").active) return null;
        handle = observed.id();
        press("Show technical details");
        retained();
        rootWidth = root.width;
        rootHeight = root.height;
        state(mc, "running");
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.NARROW);
      }
      case NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || root.width == rootWidth && root.height == rootHeight) return null;
        retained();
        state(mc, "narrow");
        next(Phase.NARROW_IMAGE);
      }
      case NARROW_IMAGE -> {
        if (!capture(mc, "production-operation-narrow")) return null;
        retained();
        NativeBindings.resizeWindow(mc, window[0], window[1]);
        next(Phase.RETURNED);
      }
      case RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), window)
            || root.width != rootWidth
            || root.height != rootHeight) return null;
        retained();
        state(mc, "returned");
        next(Phase.RETURNED_IMAGE);
      }
      case RETURNED_IMAGE -> {
        if (!capture(mc, "production-operation-returned")) return null;
        retained();
        result.addProperty("runningHandleAndDisclosureRetained", true);
        ReloadGate.release();
        next(Phase.COMPLETED);
      }
      case COMPLETED -> {
        var observed = operation();
        if (observed == null || observed.state() != OperationSnapshot.State.COMPLETED) return null;
        var facts = EffectProbeFacts.describe(observed);
        if (!observed.id().equals(handle)
            || !facts.get("operation").getAsString().equals("lootr_styled:reload")
            || facts.get("hasPersistenceReceipt").getAsBoolean()
            || !facts.get("application").getAsString().equals("APPLIED")
            || !savedRevision.equals(facts.get("sourceRevision").getAsString())
            || !generation().toString().equals(facts.get("generation").getAsString())
            || oldGeneration.equals(generation())
            || ReloadGate.requests() != 1
            || ReloadGate.releases() != 1
            || ReloadFault.calls() != initialReloadCalls + 1
            || ReloadFault.failures() != initialReloadFailures)
          throw new IllegalStateException("Reload did not complete exactly once: " + facts);
        result.add("completedReload", facts);
        result.addProperty("oneActualReloadAfterRelease", true);
        next(Phase.COMPLETED_IMAGE);
      }
      case COMPLETED_IMAGE -> {
        if (!capture(mc, "production-operation-completed")) return null;
        press("Done");
        next(Phase.CLOSED);
      }
      case CLOSED -> {
        if (workflow.isOpen() || editor.isOpen() || NativeUi.current() != parent) return null;
        result.addProperty("sourceFileUnchanged", true);
        result.addProperty("sameDraftAndOwner", true);
        result.addProperty("closedCleanly", true);
        return result;
      }
    }
    return null;
  }

  private void retained() throws Exception {
    var observed = operation();
    if (observed == null
        || !observed.id().equals(handle)
        || observed.state() != OperationSnapshot.State.RUNNING
        || !observed.operation().toString().equals("lootr_styled:reload")
        || !ReloadGate.holding()
        || ReloadGate.requests() != 1
        || ReloadGate.releases() != 0
        || ReloadFault.calls() != initialReloadCalls
        || !oldGeneration.equals(generation())
        || !UiProbe.hasButton(root, "Hide technical details")
        || !root.getNarrationMessage().getString().contains(handle.toString()))
      throw new IllegalStateException(
          "Running operation lost handle/disclosure: " + observations());
  }

  private OperationSnapshot<?> operation() {
    if (NativeUi.current() != root
        || !(workflow.navigation().current().orElse(null)
            instanceof ClientDestination.OperationResult)) return null;
    return workflow.operationInspection().flatMap(value -> value.observedOperation()).orElse(null);
  }

  private void unchanged() throws Exception {
    var now = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(original, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !java.util.Objects.equals(attributes.fileKey(), now.fileKey())
        || workflow.isOpen()
            && (workflow.editor(StyleSettingsStore.DEFINITION) != editor
                || revision != null
                    && !revision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))))
      throw new IllegalStateException("Operation changed saved file, draft or owner");
  }

  private void press(String name) {
    var button = UiProbe.button(root, name);
    if (!button.active) throw new IllegalStateException("Inactive operation control: " + name);
    NativeBindings.press(button);
  }

  private void state(Minecraft mc, String name) {
    var value = observations();
    value.addProperty("phase", name);
    var size = NativeBindings.windowSize(mc);
    value.addProperty("window", size[0] + "x" + size[1]);
    value.addProperty("gui", root.width + "x" + root.height);
    states.add(value);
  }

  JsonObject observations() {
    var value = new JsonObject();
    var observed = operation();
    if (observed != null) {
      value.addProperty("handle", observed.id().toString());
      value.addProperty("operation", observed.operation().toString());
      value.addProperty("state", observed.state().name());
    }
    value.addProperty("holding", ReloadGate.holding());
    value.addProperty("requests", ReloadGate.requests());
    value.addProperty("releases", ReloadGate.releases());
    value.addProperty("nativeEntryAttempts", ReloadFault.calls() - initialReloadCalls);
    value.addProperty("technicalExpanded", UiProbe.hasButton(root, "Hide technical details"));
    return value;
  }

  private static UUID generation() throws Exception {
    return PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
  }

  private boolean capture(Minecraft mc, String name) throws Exception {
    if (!NativeBindings.unobscured(mc)) {
      captureTicks = 0;
      return false;
    }
    if (++captureTicks < 4) return false;
    if (!captureRequested) {
      captureRequested = true;
      NativeWorld.screenshot(mc, name, () -> captured = true);
    }
    return captured;
  }

  private void next(Phase value) {
    phase = value;
    ticks = 0;
    captureTicks = 0;
    captureRequested = false;
    captured = false;
  }

  String progress() {
    return phase.toString();
  }
}
