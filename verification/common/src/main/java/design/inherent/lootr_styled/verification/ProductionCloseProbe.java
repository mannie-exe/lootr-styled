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
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.RenderLifetime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Actual dirty task, native close decisions, and domain graphic retirement. */
final class ProductionCloseProbe {
  private enum Phase {
    OPEN,
    STAGE,
    READY,
    READY_CAPTURE,
    DECISION,
    DECISION_CAPTURE,
    REVIEW_CHOOSER_CANCEL,
    REVIEW_CANCELLED,
    REVIEW_DECISION,
    REVIEW_CHOOSER_SELECT,
    REVIEW_CAPTURED,
    REVIEW_RETURNED,
    KEEP_DECISION,
    KEPT,
    READY_AGAIN,
    DECISION_AGAIN,
    CLOSED,
    REOPEN,
    REOPEN_CLOSED
  }

  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final GraphicLimits LIMITS =
      new GraphicLimits(
          16L * 1024 * 1024, 32L * 1024 * 1024, 16L * 1024 * 1024, 256, 128L * 1024 * 1024);
  private final Screen parent, root;
  private final Path settings;
  private final byte[] original;
  private final ClientSettings saved;
  private final java.util.UUID resourceGeneration;
  private final ClientEditingWorkflow workflow;
  private final ClientGraphicHost host;
  private final String changedModels;
  private final CompletableFuture<?> admission;
  private ClientEditor<ClientSettings> editor, freshEditor;
  private ClientEditingWorkflow fresh;
  private CompletableFuture<?> editing, reopening;
  private CompletableFuture<RenderLifetime.State> release;
  private DraftRevision dirtyRevision;
  private ClientDestination profilesOrigin;
  private CompletableFuture<?> returning;
  private Phase phase = Phase.OPEN;
  private long drawMark;
  private int ticks, preparations;
  private boolean captured;
  private final JsonObject result = new JsonObject();
  private final JsonArray releases = new JsonArray();

  ProductionCloseProbe() throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    saved = ClientSettings.parse(original);
    changedModels =
        (saved.models().equals(ClientSettings.VANILLA)
                ? ClientSettings.DEFAULTS.models()
                : ClientSettings.VANILLA)
            .toString();
    resourceGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    host = workflow.graphics(LIMITS, 1);
    admission =
        WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"))
            .thenAccept(value -> editor = value);
    result.add("releases", releases);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Production close timed out: " + phase + ": " + result);
    if (!admission.isDone()) return null;
    admission.join();
    unchangedSource();
    if (dirtyRevision != null && phase.ordinal() <= Phase.DECISION_AGAIN.ordinal()) retained();
    switch (phase) {
      case OPEN -> {
        if (!UiProbe.hasButton(root, "Prepare draft preview")) return null;
        var snapshot = editor.view().snapshot().orElseThrow();
        editing =
            DocumentActions.result(
                    editor
                        .actions()
                        .edit(
                            EntryPoint.GUI,
                            new DraftEdit(
                                snapshot.target(MODELS), new DraftEdit.SetRaw(changedModels))))
                .toCompletableFuture();
        next(Phase.STAGE);
      }
      case STAGE -> {
        if (!editing.isDone()) return null;
        editing.join();
        if (!models(editor).equals(changedModels)
            || workflow.documents().stream().noneMatch(ClientEditingWorkflow.DocumentState::dirty))
          throw new IllegalStateException("The close witness did not stage an unsaved choice");
        dirtyRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        if (!prepare()) return null;
        next(Phase.READY);
      }
      case READY -> {
        if (!rendered()) return null;
        observeGraphic();
        captured = false;
        NativeWorld.screenshot(mc, "production-close-ready", () -> captured = true);
        next(Phase.READY_CAPTURE);
      }
      case READY_CAPTURE -> {
        if (!captured || busy()) return null;
        NativeBindings.press(UiProbe.button(root, "Done"));
        next(Phase.DECISION);
      }
      case DECISION -> {
        if (!decision() || !released() || ticks < 4) return null;
        recordRelease();
        var narration = root.getNarrationMessage().getString();
        if (!narration.contains("Appearance"))
          throw new IllegalStateException("The decision did not identify the affected settings");
        result.addProperty("decisionNarration", narration);
        result.addProperty("sameRootDecision", true);
        captured = false;
        NativeWorld.screenshot(mc, "production-close-decision", () -> captured = true);
        next(Phase.DECISION_CAPTURE);
      }
      case DECISION_CAPTURE -> {
        if (!captured) return null;
        profilesOrigin = workflow.navigation().current().orElseThrow();
        drawMark = GraphicDrawAudit.state().rendered();
        activate("Review changes");
        next(Phase.REVIEW_CHOOSER_CANCEL);
      }
      case REVIEW_CHOOSER_CANCEL -> {
        if (!UiProbe.hasButton(root, "Inspect Appearance")) return null;
        requireProfilesOrigin();
        activate("Back to settings");
        next(Phase.REVIEW_CANCELLED);
      }
      case REVIEW_CANCELLED -> {
        if (!UiProbe.hasButton(root, "Prepare draft preview")
            || UiProbe.hasButton(root, "Inspect Appearance")) return null;
        requireProfilesOrigin();
        requireIdleGraphic();
        result.addProperty("reviewChooserKeepsProfilesContext", true);
        activate("Done");
        next(Phase.REVIEW_DECISION);
      }
      case REVIEW_DECISION -> {
        if (!decision()) return null;
        activate("Review changes");
        next(Phase.REVIEW_CHOOSER_SELECT);
      }
      case REVIEW_CHOOSER_SELECT -> {
        if (!UiProbe.hasButton(root, "Inspect Appearance")) return null;
        requireProfilesOrigin();
        activate("Inspect Appearance");
        next(Phase.REVIEW_CAPTURED);
      }
      case REVIEW_CAPTURED -> {
        if (!(workflow.navigation().current().orElse(null) instanceof ClientDestination.Inspection)
            || workflow.inspection().flatMap(value -> value.reviewedInput()).isEmpty()) return null;
        var review = workflow.inspection().orElseThrow().reviewedInput().orElseThrow();
        var models =
            review.entries().stream()
                .filter(value -> value.path().equals(MODELS))
                .findFirst()
                .orElseThrow();
        if (NativeUi.current() != root
            || !review.target().equals(dirtyRevision)
            || !JsonParser.parseString(models.draft()).getAsString().equals(changedModels)
            || !JsonParser.parseString(models.baseline())
                .getAsString()
                .equals(saved.models().toString()))
          throw new IllegalStateException(
              "Close review did not capture the actual unsaved Models choice");
        requireIdleGraphic();
        result.addProperty("closeReviewCapturesSameDraft", true);
        returning =
            WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"));
        next(Phase.REVIEW_RETURNED);
      }
      case REVIEW_RETURNED -> {
        if (!returning.isDone()) return null;
        if (returning.join() != editor)
          throw new IllegalStateException("Close review returned a different editor");
        if (!UiProbe.hasButton(root, "Prepare draft preview")) return null;
        requireProfilesOrigin();
        requireIdleGraphic();
        result.addProperty("reviewReturnRetainsOwner", true);
        activate("Done");
        next(Phase.KEEP_DECISION);
      }
      case KEEP_DECISION -> {
        if (!decision()) return null;
        activate("Keep editing");
        next(Phase.KEPT);
      }
      case KEPT -> {
        if (UiProbe.hasButton(root, "Keep editing")
            || !UiProbe.hasButton(root, "Prepare draft preview")
            || ticks < 4) return null;
        requireIdleGraphic();
        result.addProperty("keepEditingRetainsDraft", true);
        result.addProperty("noAutomaticPreparation", true);
        if (!prepare()) return null;
        next(Phase.READY_AGAIN);
      }
      case READY_AGAIN -> {
        if (!rendered() || busy()) return null;
        observeGraphic();
        NativeBindings.press(UiProbe.button(root, "Done"));
        next(Phase.DECISION_AGAIN);
      }
      case DECISION_AGAIN -> {
        if (!decision() || !released()) return null;
        recordRelease();
        activate("Discard draft");
        next(Phase.CLOSED);
      }
      case CLOSED -> {
        if (workflow.isOpen() || editor.isOpen() || NativeUi.current() != parent) return null;
        if (!released()) return null;
        result.addProperty("discardClosesOwner", true);
        fresh = StylePreferences.open(parent);
        NativeUi.open(fresh.screen());
        reopening =
            WorkflowReveal.view(
                    fresh,
                    StyleSettingsStore.DEFINITION,
                    DestinationId.parse("lootr_styled:profiles_page"))
                .thenAccept(value -> freshEditor = value);
        next(Phase.REOPEN);
      }
      case REOPEN -> {
        if (!reopening.isDone()) return null;
        reopening.join();
        if (freshEditor == editor
            || !models(freshEditor).equals(saved.models().toString())
            || fresh.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::dirty))
          throw new IllegalStateException("Fresh editing reused the discarded choice or owner");
        result.addProperty("freshOwnerUsesSavedChoice", true);
        NativeBindings.press(UiProbe.button(fresh.screen(), "Done"));
        next(Phase.REOPEN_CLOSED);
      }
      case REOPEN_CLOSED -> {
        if (fresh.isOpen() || freshEditor.isOpen() || NativeUi.current() != parent) return null;
        result.addProperty("settingsUnchanged", true);
        result.addProperty("resourceGenerationUnchanged", true);
        result.addProperty("explicitPreparations", preparations);
        result.addProperty("releasedNativeBytes", host.nativeOwnership().bytes());
        result.addProperty("releasedSceneBytes", host.retainedOutput().bytes());
        return result;
      }
    }
    return null;
  }

  private void requireProfilesOrigin() {
    if (NativeUi.current() != root
        || !workflow.navigation().current().filter(profilesOrigin::equals).isPresent())
      throw new IllegalStateException("The review chooser changed the original Profiles context");
  }

  private void requireIdleGraphic() {
    if (host.nativeOwnership().bytes() != 0
        || host.retainedOutput().bytes() != 0
        || GraphicDrawAudit.state().rendered() != drawMark)
      throw new IllegalStateException(
          "Returning from close or review automatically prepared a preview");
  }

  private boolean prepare() {
    var button = UiProbe.button(root, "Prepare draft preview");
    if (!button.active) return false;
    drawMark = GraphicDrawAudit.state().rendered();
    NativeBindings.press(button);
    NativeBindings.press(UiProbe.button(root, "Show preview"));
    preparations++;
    return true;
  }

  private boolean rendered() {
    return GraphicDrawAudit.state().rendered() > drawMark && host.nativeOwnership().bytes() > 0;
  }

  private void observeGraphic() {
    var graphic = GraphicDrawAudit.lastRendered().graphic().get();
    if (graphic == null || graphic.state().released() || host.retainedOutput().bytes() <= 0)
      throw new IllegalStateException("The close witness has no live production graphic");
    release = graphic.releaseCompletion().toCompletableFuture();
    result.addProperty("observedUsesBeforeDecision", graphic.state().outstandingUses());
  }

  private boolean decision() {
    return NativeUi.current() == root
        && UiProbe.hasButton(root, "Keep editing")
        && UiProbe.hasButton(root, "Discard draft");
  }

  private boolean released() {
    if (release == null || !release.isDone()) return false;
    var state = release.join();
    if (!state.released() || state.outstandingUses() != 0 || state.releaseFailure().isPresent())
      throw new IllegalStateException("A hidden production graphic failed to retire: " + state);
    return host.nativeOwnership().bytes() == 0 && host.retainedOutput().bytes() == 0;
  }

  private void recordRelease() {
    var state = release.join();
    var row = new JsonObject();
    row.addProperty("released", state.released());
    row.addProperty("outstandingUses", state.outstandingUses());
    row.addProperty("releaseFailed", state.releaseFailure().isPresent());
    row.addProperty("nativeBytes", host.nativeOwnership().bytes());
    row.addProperty("sceneBytes", host.retainedOutput().bytes());
    releases.add(row);
  }

  private void retained() {
    if (!workflow.isOpen()
        || !editor.isOpen()
        || workflow.editor(StyleSettingsStore.DEFINITION) != editor
        || !dirtyRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
        || !models(editor).equals(changedModels))
      throw new IllegalStateException("Closing or keeping editing lost its actual dirty owner");
  }

  private void unchangedSource() throws Exception {
    if (!Arrays.equals(original, Files.readAllBytes(settings))
        || !resourceGeneration.equals(
            PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
      throw new IllegalStateException("A close decision saved settings or reloaded resources");
  }

  private boolean busy() {
    return workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::busy);
  }

  private void activate(String caption) {
    var control = UiProbe.button(root, caption);
    if (!control.active
        || !UiProbe.focusControl(root, control)
        || !NativeBindings.activateFocused(root))
      throw new IllegalStateException("Native keyboard did not activate " + caption);
  }

  private static String models(ClientEditor<ClientSettings> value) {
    return ((DraftNode.Scalar) value.view().snapshot().orElseThrow().draft().at(MODELS)).raw();
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
