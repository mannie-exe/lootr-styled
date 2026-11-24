package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftEdit;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.DocumentImports;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.DestinationId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;

/** Actual file conflict, imported complete draft, stale-target refusal, Stage and Undo. */
final class ProductionDocumentProbe {
  private enum Phase {
    OPEN,
    LOCAL_EDIT,
    DOCUMENT,
    RELOADED,
    CONFLICT,
    CHOICES,
    CHOICES_CAPTURE,
    RESOLVED,
    IMPORT_OPEN,
    IMPORT_SOURCE,
    IMPORT_PREVIEW,
    STALE_EDIT,
    STALE_REFUSAL,
    IMPORT_REFRESHED,
    IMPORT_STAGED,
    UNDO_OPEN,
    UNDONE,
    RESTORE_OPEN,
    RESTORE_SOURCE,
    RESTORE_PREVIEW,
    RESTORE_STAGED,
    SAVED,
    CLOSED
  }

  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final FieldPath ARTWORK = FieldPath.ROOT.field("artwork");
  private static final FieldPath ENABLED = FieldPath.ROOT.field("enabled");
  private final Screen parent, root;
  private final Path settings, imported, restore, externalCopy;
  private final ClientSettings original;
  private final String localModels;
  private final UUID generation;
  private final ClientEditingWorkflow workflow;
  private final CompletableFuture<?> admission;
  private final JsonObject result = new JsonObject();
  private ClientEditor<ClientSettings> editor;
  private CompletableFuture<?> pending;
  private byte[] external;
  private boolean restoringSave, captured;
  private DocumentImports.Preview comparison;
  private DraftRevision beforeStale;
  private Map<String, String> beforeImport;
  private Phase phase = Phase.OPEN;
  private int ticks;

  ProductionDocumentProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = ClientSettings.parse(Files.readAllBytes(settings));
    generation = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    imported =
        mc.gameDirectory.toPath().resolve("lootr-styled-import-values.json").toAbsolutePath();
    restore =
        mc.gameDirectory.toPath().resolve("lootr-styled-restore-values.json").toAbsolutePath();
    externalCopy =
        mc.gameDirectory.toPath().resolve("lootr-styled-external-settings.json").toAbsolutePath();
    var values = JsonParser.parseString(original.toJson()).getAsJsonObject();
    values.remove("format");
    Files.writeString(restore, values.toString());
    values.addProperty("enabled", false);
    values.addProperty("models", "bad id");
    values.addProperty("artwork", "missing:import");
    values.addProperty("freshObjects", "OFF");
    Files.writeString(imported, values.toString());
    localModels =
        (original.models().equals(ClientSettings.VANILLA)
                ? ClientSettings.DEFAULTS.models()
                : ClientSettings.VANILLA)
            .toString();
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    admission =
        WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"))
            .thenAccept(value -> editor = value);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Production document timed out: " + phase + ": " + result);
    if (!admission.isDone()) return null;
    admission.join();
    if (!generation.equals(PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
      throw new IllegalStateException("Document inspection reloaded resources");
    if (external != null
        && !restoringSave
        && !Arrays.equals(external, Files.readAllBytes(settings)))
      throw new IllegalStateException("Conflict or import wrote settings without an explicit save");
    if (phase != Phase.CLOSED
        && (NativeUi.current() != root || workflow.editor(StyleSettingsStore.DEFINITION) != editor))
      throw new IllegalStateException("Document action replaced its actual root or owner");
    switch (phase) {
      case OPEN -> {
        pending = edit(MODELS, localModels);
        next(Phase.LOCAL_EDIT);
      }
      case LOCAL_EDIT -> {
        if (!ready()) return null;
        pending = WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION);
        next(Phase.DOCUMENT);
      }
      case DOCUMENT -> {
        if (!ready() || !UiProbe.hasButton(root, "Document actions…")) return null;
        var changed = JsonParser.parseString(original.toJson()).getAsJsonObject();
        changed.addProperty("models", "missing:external");
        changed.addProperty("artwork", "lootr_styled:vanilla");
        external = changed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(settings, external);
        Files.write(externalCopy, external);
        press("Document actions…");
        next(Phase.RELOADED);
      }
      case RELOADED -> {
        if (!UiProbe.hasButton(root, "Reload saved")) return null;
        press("Reload saved");
        next(Phase.CONFLICT);
      }
      case CONFLICT -> {
        if (busy() || editor.view().review().isEmpty()) return null;
        if (workflow.conflictInspection().isEmpty()) {
          if (!UiProbe.hasButton(root, "Review conflict…")
              || !UiProbe.button(root, "Review conflict…").active) return null;
          press("Review conflict…");
        }
        next(Phase.CHOICES);
      }
      case CHOICES -> {
        var view = workflow.conflictInspection().orElse(null);
        if (view == null || view.reviewedInput().isEmpty()) return null;
        var review = view.reviewedInput().orElseThrow();
        var fields = review.entries().stream().map(value -> value.path()).toList();
        if (!fields.contains(MODELS) || !fields.contains(ARTWORK) || editor.canSubmit())
          throw new IllegalStateException(
              "Conflict omitted compared profile fields or permits save");
        result.addProperty("conflictSameOwnerRoot", true);
        result.addProperty("comparedModelsAndArtwork", true);
        var compared = new JsonArray();
        for (var entry : review.entries()) {
          var facts = new JsonObject();
          facts.addProperty("path", entry.path().toString());
          facts.addProperty("baseline", entry.baseline());
          facts.addProperty("observed", entry.observed());
          facts.addProperty("draft", entry.draft());
          compared.add(facts);
        }
        result.add("conflictValues", compared);
        for (var entry : review.entries()) {
          int attempts = 0;
          while (true) {
            var cycle = (CycleButton<?>) view.choiceControl(entry.path()).orElseThrow();
            if (cycle.getMessage().getString().contains("Your draft")) break;
            if (++attempts > 4)
              throw new IllegalStateException("Conflict choice did not select draft");
            NativeBindings.press(cycle);
          }
        }
        if (!UiProbe.button(root, "Resolve draft").active) return null;
        next(Phase.CHOICES_CAPTURE);
      }
      case CHOICES_CAPTURE -> {
        if (ticks < 4) return null;
        var view = workflow.conflictInspection().orElseThrow();
        var selected = new JsonObject();
        for (var entry : view.reviewedInput().orElseThrow().entries())
          selected.addProperty(
              entry.path().toString(),
              view.choiceControl(entry.path()).orElseThrow().getMessage().getString());
        result.add("nativeConflictChoices", selected);
        if (!UiProbe.button(root, "Resolve draft").active)
          throw new IllegalStateException("Conflict choices did not survive their native rebuild");
        captured = false;
        NativeWorld.screenshot(mc, "production-document-conflict", () -> captured = true);
        next(Phase.RESOLVED);
      }
      case RESOLVED -> {
        if (!captured) return null;
        press("Resolve draft");
        next(Phase.IMPORT_OPEN);
      }
      case IMPORT_OPEN -> {
        if (busy()
            || workflow.conflictInspection().isPresent()
            || editor.view().review().isPresent()) return null;
        result.add("actualResolvedDraft", facts(values()));
        if (!raw("models").equals(localModels)
            || !raw("artwork").equals(original.artwork().toString()))
          throw new IllegalStateException("Conflict resolution did not retain chosen draft fields");
        result.addProperty("resolutionDoesNotSave", true);
        result.add("resolvedDraft", facts(values()));
        openImport();
        next(Phase.IMPORT_SOURCE);
      }
      case IMPORT_SOURCE -> {
        if (!ready() || workflow.importInspection().isEmpty()) return null;
        drop(imported);
        next(Phase.IMPORT_PREVIEW);
      }
      case IMPORT_PREVIEW -> {
        if (!previewReady()) return null;
        comparison = workflow.importInspection().orElseThrow().reviewedInput().orElse(null);
        if (comparison == null) {
          press("Preview import");
          return null;
        }
        requireValueFormat(comparison);
        if (!comparison.stageable() || comparison.importedValidation().valid())
          throw new IllegalStateException(
              "Repairable bad profile input did not remain a stageable draft");
        beforeStale = DraftRevision.of(editor.view().snapshot().orElseThrow());
        result.addProperty("firstImportComparison", comparison.id().toString());
        pending = edit(ENABLED, "false");
        next(Phase.STALE_EDIT);
      }
      case STALE_EDIT -> {
        if (!ready() || !previewReady()) return null;
        if (beforeStale.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
          throw new IllegalStateException("The stale-target control did not change the draft");
        next(Phase.STALE_REFUSAL);
      }
      case STALE_REFUSAL -> {
        if (ticks < 4) return null;
        if (UiProbe.button(root, "Stage draft").active
            || workflow.importInspection().orElseThrow().reviewedInput().orElseThrow()
                != comparison)
          throw new IllegalStateException(
              "Old import comparison permits staging against a newer draft");
        result.addProperty("staleImportTargetRefused", true);
        beforeImport = values();
        result.add("draftBeforeImport", facts(beforeImport));
        press("Preview import");
        next(Phase.IMPORT_REFRESHED);
      }
      case IMPORT_REFRESHED -> {
        var review = workflow.importInspection().orElseThrow().reviewedInput().orElse(null);
        if (review == null || review == comparison || !previewReady()) return null;
        requireValueFormat(review);
        if (!review.stageable() || !UiProbe.button(root, "Stage draft").active) return null;
        captured = false;
        NativeWorld.screenshot(mc, "production-document-import", () -> captured = true);
        next(Phase.IMPORT_STAGED);
      }
      case IMPORT_STAGED -> {
        if (!captured) return null;
        press("Stage draft");
        next(Phase.UNDO_OPEN);
      }
      case UNDO_OPEN -> {
        if (busy() || workflow.importInspection().isPresent()) return null;
        if (!raw("models").equals("bad id")
            || !raw("artwork").equals("missing:import")
            || !raw("enabled").equals("false")
            || !raw("freshObjects").equals("OFF")
            || editor.canSubmit())
          throw new IllegalStateException(
              "Import omitted whole-draft fields or lost invalid input");
        result.addProperty("wholeDraftInvalidImportRetained", true);
        result.add("importedDraft", facts(values()));
        press("Document actions…");
        next(Phase.UNDONE);
      }
      case UNDONE -> {
        if (!UiProbe.hasButton(root, "Undo") || !UiProbe.button(root, "Undo").active) return null;
        press("Undo");
        next(Phase.RESTORE_OPEN);
      }
      case RESTORE_OPEN -> {
        if (busy() || UiProbe.hasButton(root, "Undo")) return null;
        if (!values().equals(beforeImport))
          throw new IllegalStateException("Undo did not restore the complete pre-import draft");
        if (!editor.canStage(MODELS)) return null;
        result.addProperty("undoRestoresCompleteDraft", true);
        openImport();
        next(Phase.RESTORE_SOURCE);
      }
      case RESTORE_SOURCE -> {
        if (!ready() || workflow.importInspection().isEmpty()) return null;
        drop(restore);
        next(Phase.RESTORE_PREVIEW);
      }
      case RESTORE_PREVIEW -> {
        if (!previewReady()) return null;
        var review = workflow.importInspection().orElseThrow().reviewedInput().orElse(null);
        if (review == null) {
          press("Preview import");
          return null;
        }
        requireValueFormat(review);
        if (!review.importedValidation().valid() || !UiProbe.button(root, "Stage draft").active)
          return null;
        press("Stage draft");
        next(Phase.RESTORE_STAGED);
      }
      case RESTORE_STAGED -> {
        if (busy()
            || workflow.importInspection().isPresent()
            || !editor.canSubmit()
            || !UiProbe.hasButton(root, "Save settings")
            || !UiProbe.button(root, "Save settings").active) return null;
        restoringSave = true;
        press("Save settings");
        next(Phase.SAVED);
      }
      case SAVED -> {
        if (busy() || !ClientSettings.parse(Files.readAllBytes(settings)).equals(original))
          return null;
        result.addProperty("originalSavedValuesRestored", true);
        result.addProperty("resourceGenerationUnchanged", true);
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

  private void openImport() {
    pending =
        workflow
            .navigation()
            .open(new ClientDestination.Import(StyleSettingsStore.DEFINITION.id()))
            .completion()
            .thenAccept(
                value -> {
                  if (value.outcome()
                      != design.inherent.reform.minecraft.client.NavigationAttempt.Outcome.REVEALED)
                    throw new IllegalStateException("Import was not revealed: " + value);
                })
            .toCompletableFuture();
  }

  private void drop(Path file) {
    root.onFilesDrop(List.of(file));
    if (workflow.importInspection().orElseThrow().reviewedInput().isPresent())
      throw new IllegalStateException("Dropping a file implicitly prepared an import");
    result.addProperty("importNoImplicitPreview", true);
  }

  private void requireValueFormat(DocumentImports.Preview preview) {
    if (!preview.format().id().toString().equals("reform:import/value_json")
        || preview.sourceSchemaVersion().isPresent())
      throw new IllegalStateException(
          "Import inferred native format1 instead of the selected value format");
    result.addProperty("importValueFormatDistinctFromNativeFile", true);
  }

  private boolean previewReady() {
    return UiProbe.hasButton(root, "Preview import")
        && UiProbe.button(root, "Preview import").active;
  }

  private CompletableFuture<?> edit(FieldPath path, String value) {
    var snapshot = editor.view().snapshot().orElseThrow();
    return DocumentActions.result(
            editor
                .actions()
                .edit(
                    EntryPoint.GUI,
                    new DraftEdit(snapshot.target(path), new DraftEdit.SetRaw(value))))
        .toCompletableFuture();
  }

  private String raw(String key) {
    return ((DraftNode.Scalar)
            editor.view().snapshot().orElseThrow().draft().at(FieldPath.ROOT.field(key)))
        .raw();
  }

  private Map<String, String> values() {
    return Map.of(
        "enabled",
        raw("enabled"),
        "models",
        raw("models"),
        "artwork",
        raw("artwork"),
        "freshObjects",
        raw("freshObjects"));
  }

  private static JsonObject facts(Map<String, String> values) {
    var result = new JsonObject();
    values.forEach(result::addProperty);
    return result;
  }

  private boolean ready() {
    if (!pending.isDone()) return false;
    pending.join();
    return !busy();
  }

  private boolean busy() {
    return workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::busy);
  }

  private void press(String title) {
    var button = UiProbe.button(root, title);
    if (!button.active) throw new IllegalStateException("Inactive document control: " + title);
    NativeBindings.press(button);
  }

  private void next(Phase value) {
    phase = value;
    ticks = 0;
  }

  String progress() {
    return phase.toString();
  }

  JsonObject observations() {
    var facts = result.deepCopy();
    if (editor != null && editor.isOpen() && workflow.isOpen()) {
      try {
        facts.add("currentDraft", facts(values()));
        facts.addProperty("nativeCanStageModels", editor.canStage(MODELS));
        facts.addProperty("nativeCanSubmit", editor.canSubmit());
        facts.addProperty("currentNarration", root.getNarrationMessage().getString());
        var controls = new JsonObject();
        for (var path : List.of(MODELS, ARTWORK))
          editor
              .form()
              .fieldControl(path)
              .ifPresent(
                  control ->
                      controls.addProperty(path.toString(), control.getMessage().getString()));
        facts.add("nativeFieldMessages", controls);
      } catch (RuntimeException unavailable) {
        facts.addProperty("observerStateUnavailable", unavailable.toString());
      }
    }
    return facts;
  }
}
