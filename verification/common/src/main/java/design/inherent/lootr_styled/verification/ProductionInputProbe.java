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
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.DestinationId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * Actual native invalid input and a valid sibling across real window events, without persistence.
 */
final class ProductionInputProbe {
  private enum Phase {
    OPEN,
    SIBLING,
    FIELD,
    MANUAL,
    INVALID,
    NARROW,
    NARROW_IMAGE,
    RETURNED,
    RETURNED_IMAGE,
    REPAIR,
    RESTORE,
    ACTIONS_OPEN,
    ACTIONS_STAGED,
    PARTIAL,
    PARTIAL_QUERY,
    PARTIAL_CAPTURE,
    DEFAULT_MENU,
    DEFAULTED,
    DEFAULT_CAPTURE,
    UNDO_MENU,
    UNDONE,
    REDO_MENU,
    REDONE,
    REDO_CAPTURE,
    REDO_UNDO_MENU,
    REDO_UNDONE,
    RESTORE_MENU,
    RESTORED_ORIGINAL,
    SETUP_REDO_MENU,
    SETUP_REDONE,
    RELOAD_MENU,
    RELOADED,
    RELOAD_CHOICES,
    RELOAD_RESOLVED,
    RELOAD_RETURNED,
    RELOAD_CAPTURE,
    CLOSED
  }

  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final FieldPath ENABLED = FieldPath.ROOT.field("enabled");
  private final Screen parent, root;
  private final ClientEditingWorkflow workflow;
  private final CompletableFuture<?> admission;
  private final Path settings;
  private final byte[] originalBytes;
  private final java.nio.file.attribute.BasicFileAttributes originalAttributes;
  private final ClientSettings original;
  private final UUID generation;
  private final int[] window;
  private final int width, height;
  private final JsonObject result = new JsonObject();
  private final JsonArray states = new JsonArray();
  private ClientEditor<ClientSettings> editor;
  private CompletableFuture<?> pending;
  private DraftRevision invalidRevision;
  private JsonObject invalidDraft;
  private final JsonObject filteredActions = new JsonObject();
  private JsonObject stagedActions;
  private Phase phase = Phase.OPEN;
  private int ticks, captureTicks;
  private boolean captureRequested, captured;
  private long editStarted;
  private long readsBeforeReload;

  ProductionInputProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    originalBytes = Files.readAllBytes(settings);
    originalAttributes =
        Files.readAttributes(settings, java.nio.file.attribute.BasicFileAttributes.class);
    original = ClientSettings.parse(originalBytes);
    generation = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    window = NativeBindings.windowSize(mc);
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    width = root.width;
    height = root.height;
    admission =
        WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, ENABLED)
            .thenAccept(value -> editor = value);
    result.add("states", states);
    result.add("filteredActions", filteredActions);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 300)
      throw new IllegalStateException("Native input timed out: " + phase + ": " + observations());
    if (!admission.isDone()) return null;
    admission.join();
    unchanged();
    switch (phase) {
      case OPEN -> {
        if (editor.form().fieldControl(ENABLED).isEmpty() || !editor.canSubmit()) return null;
        result.add("baselineWholeDraft", draft());
        toggleEnabled();
        next(Phase.SIBLING);
      }
      case SIBLING -> {
        if (!editor.canSubmit() || raw(ENABLED).equals(Boolean.toString(original.enabled())))
          return null;
        pending = WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, MODELS);
        next(Phase.FIELD);
      }
      case FIELD -> {
        if (!pending.isDone()) return null;
        pending.join();
        var manual = UiProbe.manualIdButton(root, "Edit ID");
        if (!manual.active) return null;
        NativeBindings.press(manual);
        next(Phase.MANUAL);
      }
      case MANUAL -> {
        if (!(editor.form().fieldControl(MODELS).orElse(null) instanceof EditBox input)
            || !input.active) return null;
        if (!UiProbe.focusControl(root, input))
          throw new IllegalStateException("Cannot focus actual Models input");
        input.setValue("bad id");
        editStarted = System.nanoTime();
        next(Phase.INVALID);
      }
      case INVALID -> {
        if (editor.canSubmit() || busy()) return null;
        if (!raw(MODELS).equals("bad id")) return null;
        if (UiProbe.button(root, "Save settings").active) return null;
        var input = (EditBox) editor.form().fieldControl(MODELS).orElseThrow();
        if (!UiProbe.focusedPath(root, input))
          throw new IllegalStateException("Native invalid edit lost focus");
        input.setCursorPosition(3);
        input.setHighlightPos(1);
        invalidRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        invalidDraft = draft();
        assertInput();
        result.addProperty("editToInvalidGuardNanos", System.nanoTime() - editStarted);
        result.add("invalidWholeDraft", invalidDraft.deepCopy());
        state(mc, "invalid");
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.NARROW);
      }
      case NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || root.width == width && root.height == height) return null;
        assertRetained();
        state(mc, "narrow");
        next(Phase.NARROW_IMAGE);
      }
      case NARROW_IMAGE -> {
        if (!capture(mc, "production-input-narrow")) return null;
        assertRetained();
        NativeBindings.resizeWindow(mc, window[0], window[1]);
        next(Phase.RETURNED);
      }
      case RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), window)
            || root.width != width
            || root.height != height) return null;
        assertRetained();
        state(mc, "returned");
        next(Phase.RETURNED_IMAGE);
      }
      case RETURNED_IMAGE -> {
        if (!capture(mc, "production-input-returned")) return null;
        assertRetained();
        result.addProperty("invalidInputReflowKeepsWholeDraft", true);
        result.addProperty("invalidInputReflowKeepsCaretSelectionFocus", true);
        ((EditBox) editor.form().fieldControl(MODELS).orElseThrow())
            .setValue(original.models().toString());
        next(Phase.REPAIR);
      }
      case REPAIR -> {
        if (!editor.canSubmit() || busy() || !raw(MODELS).equals(original.models().toString()))
          return null;
        toggleEnabled();
        next(Phase.RESTORE);
      }
      case RESTORE -> {
        if (!editor.canSubmit()
            || busy()
            || !editor
                .view()
                .snapshot()
                .orElseThrow()
                .inspect()
                .candidate()
                .orElseThrow()
                .value()
                .equals(original)) return null;
        result.addProperty("repairRestoresOriginalValuesWithoutSaving", true);
        result.add("restoredWholeDraft", draft());
        pending =
            WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"));
        next(Phase.ACTIONS_OPEN);
      }
      case ACTIONS_OPEN -> {
        if (!ready()) return null;
        for (String key : new String[] {"enabled", "models", "artwork", "freshObjects"})
          if (editor.form().fieldControl(FieldPath.ROOT.field(key)).isPresent())
            throw new IllegalStateException("Backend edit would bypass mounted native input");
        var snapshot = editor.view().snapshot().orElseThrow();
        pending =
            DocumentActions.result(
                    editor
                        .actions()
                        .edit(
                            EntryPoint.GUI,
                            new DraftEdit(
                                snapshot.target(FieldPath.ROOT),
                                new DraftEdit.SetRecordScalars(
                                    Map.of(
                                        "enabled",
                                        "false",
                                        "models",
                                        "lootr_styled:vanilla",
                                        "artwork",
                                        "lootr_styled:vanilla",
                                        "freshObjects",
                                        "OFF")))))
                .toCompletableFuture();
        next(Phase.ACTIONS_STAGED);
      }
      case ACTIONS_STAGED -> {
        if (!ready() || !editor.canSubmit()) return null;
        stagedActions = draft();
        if (!raw(MODELS).equals("lootr_styled:vanilla")
            || !raw(FieldPath.ROOT.field("artwork")).equals("lootr_styled:vanilla")
            || !raw(ENABLED).equals("false")
            || !raw(FieldPath.ROOT.field("freshObjects")).equals("OFF"))
          throw new IllegalStateException("Complete action setup was not admitted");
        filteredActions.add("stagedWholeDraft", stagedActions.deepCopy());
        next(Phase.PARTIAL);
      }
      case PARTIAL -> {
        if (!ready()
            || !UiProbe.hasButton(root, "Prepare draft preview")
            || !UiProbe.hasButton(root, "Search")
            || !UiProbe.button(root, "Search").active) return null;
        query().setValue("Vanilla");
        press("Search");
        next(Phase.PARTIAL_QUERY);
      }
      case PARTIAL_QUERY -> {
        if (busy()
            || UiProbe.hasButton(root, "Default")
            || !UiProbe.hasButton(root, "Vanilla (Inherited)")) return null;
        filteredActions.addProperty("searchRemovedDefaultChoice", true);
        partial();
        if (!stagedActions.equals(draft()))
          throw new IllegalStateException("Partial view changed hidden values");
        filteredActions.addProperty("hiddenModelAndArtworkControls", true);
        filteredActions.addProperty("query", query().getValue());
        next(Phase.PARTIAL_CAPTURE);
      }
      case PARTIAL_CAPTURE -> {
        if (!capture(mc, "production-input-filtered")) return null;
        partial();
        press("Document actions…");
        next(Phase.DEFAULT_MENU);
      }
      case DEFAULT_MENU -> {
        if (!UiProbe.hasButton(root, "Defaults") || !UiProbe.button(root, "Defaults").active)
          return null;
        press("Defaults");
        next(Phase.DEFAULTED);
      }
      case DEFAULTED -> {
        if (busy() || UiProbe.hasButton(root, "Defaults")) return null;
        partial();
        if (!candidate().equals(ClientSettings.DEFAULTS)) return null;
        filteredActions.add("defaultWholeDraft", draft());
        next(Phase.DEFAULT_CAPTURE);
      }
      case DEFAULT_CAPTURE -> {
        if (!capture(mc, "production-input-filtered-defaults")) return null;
        partial();
        press("Document actions…");
        next(Phase.UNDO_MENU);
      }
      case UNDO_MENU -> {
        if (!UiProbe.hasButton(root, "Undo") || !UiProbe.button(root, "Undo").active) return null;
        press("Undo");
        next(Phase.UNDONE);
      }
      case UNDONE -> {
        if (busy() || !editor.canSubmit() || UiProbe.hasButton(root, "Undo")) return null;
        partial();
        if (!stagedActions.equals(draft())) return null;
        filteredActions.add("undoneWholeDraft", draft());
        filteredActions.addProperty("canUndoAfterDefaultsUndo", editor.view().canUndo());
        if (!editor.view().canUndo())
          throw new IllegalStateException("Actual actor has no setup undo after Defaults undo");
        press("Document actions…");
        next(Phase.REDO_MENU);
      }
      case REDO_MENU -> {
        if (!UiProbe.hasButton(root, "Redo") || !UiProbe.button(root, "Redo").active) return null;
        press("Redo");
        next(Phase.REDONE);
      }
      case REDONE -> {
        if (busy() || UiProbe.hasButton(root, "Redo") || !editor.canSubmit()) return null;
        partial();
        if (!candidate().equals(ClientSettings.DEFAULTS)) return null;
        filteredActions.add("redoneWholeDraft", draft());
        next(Phase.REDO_CAPTURE);
      }
      case REDO_CAPTURE -> {
        if (!capture(mc, "production-input-filtered-redo")) return null;
        partial();
        press("Document actions…");
        next(Phase.REDO_UNDO_MENU);
      }
      case REDO_UNDO_MENU -> {
        if (!UiProbe.hasButton(root, "Undo") || !UiProbe.button(root, "Undo").active) return null;
        press("Undo");
        next(Phase.REDO_UNDONE);
      }
      case REDO_UNDONE -> {
        if (busy() || UiProbe.hasButton(root, "Undo") || !editor.canSubmit()) return null;
        partial();
        if (!stagedActions.equals(draft())) return null;
        filteredActions.add("redoUndoneWholeDraft", draft());
        press("Document actions…");
        next(Phase.RESTORE_MENU);
      }
      case RESTORE_MENU -> {
        if (!UiProbe.hasButton(root, "Undo") || !UiProbe.button(root, "Undo").active) return null;
        press("Undo");
        next(Phase.RESTORED_ORIGINAL);
      }
      case RESTORED_ORIGINAL -> {
        if (busy() || UiProbe.hasButton(root, "Undo") || !editor.canSubmit()) return null;
        partial();
        if (!candidate().equals(original)) return null;
        filteredActions.add("restoredWholeDraft", draft());
        filteredActions.addProperty("wholeDocumentDefaultsAndUndo", true);
        press("Document actions…");
        next(Phase.SETUP_REDO_MENU);
      }
      case SETUP_REDO_MENU -> {
        if (!UiProbe.hasButton(root, "Redo") || !UiProbe.button(root, "Redo").active) return null;
        press("Redo");
        next(Phase.SETUP_REDONE);
      }
      case SETUP_REDONE -> {
        if (busy() || UiProbe.hasButton(root, "Redo") || !editor.canSubmit()) return null;
        partial();
        if (!stagedActions.equals(draft())) return null;
        filteredActions.add("setupRedoneWholeDraft", draft());
        readsBeforeReload = UiCostAudit.settingsReads();
        press("Document actions…");
        next(Phase.RELOAD_MENU);
      }
      case RELOAD_MENU -> {
        if (!UiProbe.hasButton(root, "Reload saved")
            || !UiProbe.button(root, "Reload saved").active) return null;
        press("Reload saved");
        next(Phase.RELOADED);
      }
      case RELOADED -> {
        if (busy() || editor.view().review().isEmpty()) return null;
        partial();
        if (!stagedActions.equals(draft()) || editor.canSubmit())
          throw new IllegalStateException(
              "Dirty reload lost the complete draft or submission guard");
        long reads = UiCostAudit.settingsReads() - readsBeforeReload;
        if (reads < 1)
          throw new IllegalStateException("Reload saved did not read the actual store");
        filteredActions.addProperty("reloadSavedAdditionalStoreReads", reads);
        filteredActions.add("dirtyReloadRetainedWholeDraft", draft());
        filteredActions.addProperty("dirtyReloadRequiresReview", true);
        if (!UiProbe.hasButton(root, "Review conflict…")
            || !UiProbe.button(root, "Review conflict…").active) return null;
        press("Review conflict…");
        next(Phase.RELOAD_CHOICES);
      }
      case RELOAD_CHOICES -> {
        var view = workflow.conflictInspection().orElse(null);
        if (view == null || view.reviewedInput().isEmpty()) return null;
        var review = view.reviewedInput().orElseThrow();
        var paths = review.entries().stream().map(entry -> entry.path()).toList();
        if (paths.size() != 4
            || !paths.containsAll(
                java.util.List.of(
                    ENABLED,
                    MODELS,
                    FieldPath.ROOT.field("artwork"),
                    FieldPath.ROOT.field("freshObjects"))))
          throw new IllegalStateException("Dirty reload review omitted a hidden field: " + paths);
        var choices = new JsonObject();
        for (var entry : review.entries()) {
          int attempts = 0;
          while (true) {
            var cycle = (CycleButton<?>) view.choiceControl(entry.path()).orElseThrow();
            if (cycle.getMessage().getString().contains("Observed saved value")) {
              var field = (FieldPath.Field) entry.path().segments().getFirst();
              choices.addProperty(field.id(), cycle.getMessage().getString());
              break;
            }
            if (++attempts > 4)
              throw new IllegalStateException(
                  "Conflict choice did not select observed saved value");
            NativeBindings.press(cycle);
          }
        }
        if (!UiProbe.button(root, "Resolve draft").active) return null;
        filteredActions.add("nativeReloadChoices", choices);
        press("Resolve draft");
        next(Phase.RELOAD_RESOLVED);
      }
      case RELOAD_RESOLVED -> {
        if (busy()
            || workflow.conflictInspection().isPresent()
            || editor.view().review().isPresent()
            || !editor.canSubmit()) return null;
        if (!candidate().equals(original))
          throw new IllegalStateException("Reviewed reload did not restore the saved whole draft");
        filteredActions.addProperty("resolvedSavedSourceWithoutSave", true);
        pending =
            WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"));
        next(Phase.RELOAD_RETURNED);
      }
      case RELOAD_RETURNED -> {
        if (!ready()) return null;
        partial();
        filteredActions.add("reloadedWholeDraft", draft());
        filteredActions.addProperty("wholeDocumentRedoAndReloadSaved", true);
        next(Phase.RELOAD_CAPTURE);
      }
      case RELOAD_CAPTURE -> {
        if (!capture(mc, "production-input-filtered-reloaded")) return null;
        partial();
        filteredActions.addProperty("sameOwnerAndNoWrite", true);
        result.add("filteredActions", filteredActions);
        if (!UiProbe.button(root, "Done").active) return null;
        press("Done");
        next(Phase.CLOSED);
      }
      case CLOSED -> {
        if (UiProbe.hasButton(root, "Discard draft")
            && workflow.isOpen()
            && UiProbe.button(root, "Discard draft").active) {
          NativeBindings.press(UiProbe.button(root, "Discard draft"));
          return null;
        }
        if (workflow.isOpen() || editor.isOpen() || NativeUi.current() != parent) return null;
        result.addProperty("settingsUnchanged", true);
        result.addProperty("resourceGenerationUnchanged", true);
        result.addProperty("sameOwnerUntilClose", true);
        result.addProperty("closedCleanly", true);
        return result;
      }
    }
    return null;
  }

  private ClientSettings candidate() {
    return editor.view().snapshot().orElseThrow().inspect().candidate().orElseThrow().value();
  }

  private boolean ready() {
    if (!pending.isDone()) return false;
    pending.join();
    return !busy();
  }

  private EditBox query() {
    return UiProbe.children(root).stream()
        .filter(EditBox.class::isInstance)
        .map(EditBox.class::cast)
        .filter(box -> box.getMessage().getString().equals("Search"))
        .findFirst()
        .orElseThrow();
  }

  private void partial() {
    if (NativeUi.current() != root
        || !query().getValue().equals("Vanilla")
        || editor.form().fieldControl(ENABLED).isPresent()
        || editor.form().fieldControl(MODELS).isPresent()
        || editor.form().fieldControl(FieldPath.ROOT.field("artwork")).isPresent())
      throw new IllegalStateException(
          "Not the actual filtered Profiles view with hidden native fields: " + observations());
  }

  private void press(String label) {
    var button = UiProbe.button(root, label);
    if (!button.active) throw new IllegalStateException("Inactive document action: " + label);
    NativeBindings.press(button);
  }

  private void assertInput() {
    if (!(editor.form().fieldControl(MODELS).orElse(null) instanceof EditBox input)
        || !input.getValue().equals("bad id")
        || input.getCursorPosition() != 3
        || !input.getHighlighted().equals("ad")
        || !UiProbe.focusedPath(root, input)
        || raw(ENABLED).equals(Boolean.toString(original.enabled()))
        || editor.canSubmit()
        || UiProbe.button(root, "Save settings").active)
      throw new IllegalStateException(
          "Invalid Models input, sibling or native guard was lost: " + observations());
  }

  private void assertRetained() {
    assertInput();
    if (!invalidRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
        || !invalidDraft.equals(draft()))
      throw new IllegalStateException("Reflow changed the complete draft");
  }

  private void unchanged() throws Exception {
    var attributes =
        Files.readAttributes(settings, java.nio.file.attribute.BasicFileAttributes.class);
    if (!Arrays.equals(originalBytes, Files.readAllBytes(settings))
        || !originalAttributes.lastModifiedTime().equals(attributes.lastModifiedTime())
        || !java.util.Objects.equals(originalAttributes.fileKey(), attributes.fileKey())
        || !generation.equals(PreviewProbeAccess.generation(PreviewProbeAccess.activePack()))
        || workflow.isOpen() && workflow.editor(StyleSettingsStore.DEFINITION) != editor)
      throw new IllegalStateException(
          "Input check changed file, resource generation or editor identity");
  }

  private void toggleEnabled() {
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

  private boolean busy() {
    return workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::busy);
  }

  private String raw(FieldPath path) {
    return ((DraftNode.Scalar) editor.view().snapshot().orElseThrow().draft().at(path)).raw();
  }

  private JsonObject draft() {
    var value = new JsonObject();
    for (String key : new String[] {"enabled", "models", "artwork", "freshObjects"})
      value.addProperty(key, raw(FieldPath.ROOT.field(key)));
    return value;
  }

  private void state(Minecraft mc, String name) {
    var value = observations();
    value.addProperty("phase", name);
    var size = NativeBindings.windowSize(mc);
    value.addProperty("window", size[0] + "x" + size[1]);
    value.addProperty("gui", root.width + "x" + root.height);
    states.add(value);
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

  JsonObject observations() {
    var value = new JsonObject();
    if (editor == null || !editor.isOpen()) return value;
    value.add("draft", draft());
    value.addProperty("canSubmit", editor.canSubmit());
    value.addProperty("canUndo", editor.view().canUndo());
    value.addProperty("canRedo", editor.view().canRedo());
    value.add("filteredActions", filteredActions.deepCopy());
    var buttons = new JsonArray();
    for (var child : UiProbe.children(root))
      if (child instanceof net.minecraft.client.gui.components.AbstractButton button) {
        var fact = new JsonObject();
        fact.addProperty("label", button.getMessage().getString());
        fact.addProperty("active", button.active);
        buttons.add(fact);
      }
    value.add("buttons", buttons);
    editor
        .form()
        .fieldControl(MODELS)
        .ifPresent(
            control -> {
              value.addProperty("modelControl", control.getClass().getSimpleName());
              value.addProperty("modelFocus", UiProbe.focusedPath(root, control));
              if (control instanceof EditBox input) {
                value.addProperty("text", input.getValue());
                value.addProperty("caret", input.getCursorPosition());
                value.addProperty("selection", input.getHighlighted());
              }
            });
    return value;
  }
}
