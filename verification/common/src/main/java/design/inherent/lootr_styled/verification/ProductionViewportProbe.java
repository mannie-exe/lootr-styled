package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.minecraft.client.ClientMeshGraphic;
import design.inherent.reform.minecraft.client.NavigationAttempt;
import design.inherent.reform.minecraft.client.ScrollFrame;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicCamera;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.RenderLifetime;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Real task controls and OS window events, with metadata-only native draw observations. */
final class ProductionViewportProbe {
  private enum Phase {
    INPUT_OPEN,
    INPUT_MANUAL,
    INPUT_LONG,
    INPUT_RESTORE,
    OPEN,
    SEARCH,
    SEARCH_PAINT,
    READY,
    ROTATED,
    ZOOMED,
    RESET,
    NARROW,
    NARROW_DRAW,
    NARROW_IMAGE,
    RETURNED,
    RETURNED_DRAW,
    RETURNED_IMAGE,
    HOME_RELEASE,
    REOPEN,
    REOPEN_PAINT,
    REPEAT_READY,
    REPEAT_CAMERA,
    RELEASE
  }

  private static final int CYCLES = 4;
  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final String EXTREME_ID = "fixture:" + "a".repeat(248);
  private Phase phase = Phase.INPUT_OPEN;
  private final ClientEditingWorkflow workflow;
  private final ClientGraphicHost host;
  private final Screen parent;
  private final Path settings;
  private final byte[] original;
  private final java.nio.file.attribute.BasicFileAttributes attributes;
  private final java.util.UUID resourceGeneration;
  private final int[] originalWindow;
  private final int originalWidth, originalHeight;
  private final CompletableFuture<?> admission;
  private CompletableFuture<?> pending;
  private ClientEditor<ClientSettings> editor;
  private EditBox query;
  private DraftRevision revision;
  private GraphicCamera camera;
  private WeakReference<ClientMeshGraphic> graphic;
  private CompletableFuture<RenderLifetime.State> released;
  private long sequence, sceneBytes;
  private final long initialPreparations = UiCostAudit.preparations();
  private long prepareAt, searchAt;
  private int cycle;
  private boolean retainingViewport;
  private int ticks;
  private boolean captured;
  private final JsonObject result = new JsonObject();
  private final JsonArray frames = new JsonArray();
  private final JsonObject cost = new JsonObject();
  private final JsonArray paints = new JsonArray(),
      cycles = new JsonArray(),
      preparationTimes = new JsonArray();

  ProductionViewportProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    attributes = Files.readAttributes(settings, java.nio.file.attribute.BasicFileAttributes.class);
    resourceGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    originalWindow = NativeBindings.windowSize(mc);
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    originalWidth = workflow.screen().width;
    originalHeight = workflow.screen().height;
    host =
        workflow.graphics(
            new GraphicLimits(
                16L * 1024 * 1024, 32L * 1024 * 1024, 16L * 1024 * 1024, 256, 128L * 1024 * 1024),
            1);
    admission =
        WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, MODELS)
            .thenAccept(value -> editor = value);
    result.add("frames", frames);
    result.add("cost", cost);
    cost.add("uiCallbacks", paints);
    cost.add("cycles", cycles);
    cost.add("prepareToFirstDrawObservedNanos", preparationTimes);
    cost.addProperty("nativeBudgetBytes", host.limits().nativeBytes());
    cost.addProperty("outputBudgetBytes", host.limits().outputBytes());
    cost.addProperty("resourceBudget", host.limits().resources());
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Production viewport timed out: " + phase + ": " + result);
    if (!admission.isDone()) return null;
    admission.join();
    var root = phase == Phase.RELEASE ? null : workflow.screen();
    if (phase != Phase.RELEASE) sourceUnchanged();
    if (retainingViewport) unchanged();
    switch (phase) {
      case INPUT_OPEN -> {
        if (!editor.canSubmit() || editor.form().fieldControl(MODELS).isEmpty()) return null;
        var disclosure = UiProbe.manualIdButton(root, "Edit ID");
        if (!disclosure.active) return null;
        NativeBindings.press(disclosure);
        next(Phase.INPUT_MANUAL);
      }
      case INPUT_MANUAL -> {
        if (!(editor.form().fieldControl(MODELS).orElse(null) instanceof EditBox input)
            || !input.active) return null;
        UiCostAudit.watch(root, "accepted-extreme-id");
        long at = System.nanoTime();
        input.setValue(EXTREME_ID);
        cost.addProperty("nativeExtremeInputCallNanos", System.nanoTime() - at);
        next(Phase.INPUT_LONG);
      }
      case INPUT_LONG -> {
        var input = (EditBox) editor.form().fieldControl(MODELS).orElseThrow();
        if (!input.getValue().equals(EXTREME_ID))
          throw new IllegalStateException("Native control truncated supported256-character ID");
        if (!editor.canSubmit()
            || !((DraftNode.Scalar) editor.view().snapshot().orElseThrow().draft().at(MODELS))
                .raw()
                .equals(EXTREME_ID)
            || UiCostAudit.samples() < 8) return null;
        finishPaint();
        cost.addProperty("acceptedInputCharacters", input.getValue().length());
        UiCostAudit.watch(root, "ordinary-id-restoration");
        long at = System.nanoTime();
        input.setValue(ClientSettings.parse(original).models().toString());
        cost.addProperty("nativeOrdinaryInputCallNanos", System.nanoTime() - at);
        next(Phase.INPUT_RESTORE);
      }
      case INPUT_RESTORE -> {
        if (!editor.canSubmit()
            || !editor
                .view()
                .snapshot()
                .orElseThrow()
                .inspect()
                .candidate()
                .orElseThrow()
                .value()
                .equals(ClientSettings.parse(original))
            || UiCostAudit.samples() < 8) return null;
        finishPaint();
        pending =
            WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"));
        next(Phase.OPEN);
      }
      case OPEN -> {
        if (!pending.isDone()) return null;
        pending.join();
        var prepare =
            UiProbe.children(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals("Prepare draft preview"))
                .findFirst();
        if (prepare.isEmpty() || !prepare.orElseThrow().active) {
          var labels = new JsonArray();
          UiProbe.children(root).stream()
              .filter(AbstractWidget.class::isInstance)
              .map(AbstractWidget.class::cast)
              .forEach(value -> labels.add(value.getMessage().getString()));
          result.add("awaitingTaskControls", labels);
          return null;
        }
        result.addProperty("taskControlsObserved", true);
        query =
            UiProbe.children(root).stream()
                .filter(EditBox.class::isInstance)
                .map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals("Search"))
                .findFirst()
                .orElseThrow();
        UiCostAudit.watch(root, "profile-search");
        long at = System.nanoTime();
        query.setValue("Vanilla");
        cost.addProperty("nativeQueryInputCallNanos", System.nanoTime() - at);
        query.setCursorPosition(3);
        revision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        retainingViewport = true;
        var search = UiProbe.button(root, "Search");
        if (!search.active) throw new IllegalStateException("Actual profile search is inactive");
        searchAt = System.nanoTime();
        NativeBindings.press(search);
        cost.addProperty("nativeSearchActionCallNanos", System.nanoTime() - searchAt);
        next(Phase.SEARCH);
      }
      case SEARCH -> {
        if (UiProbe.hasButton(root, "Default") || !UiProbe.hasButton(root, "Vanilla (Inherited)"))
          return null;
        cost.addProperty("searchToFilteredChoiceObservedNanos", System.nanoTime() - searchAt);
        next(Phase.SEARCH_PAINT);
      }
      case SEARCH_PAINT -> {
        if (UiCostAudit.samples() < 8) return null;
        finishPaint();
        if (UiCostAudit.preparations() != initialPreparations
            || host.nativeOwnership().bytes() != 0
            || host.retainedOutput().bytes() != 0)
          throw new IllegalStateException("Input/search prepared an implicit preview");
        cost.addProperty("noImplicitPreparationDuringInputSearch", true);
        startPreparation(root);
        next(Phase.READY);
      }
      case READY -> {
        if (GraphicDrawAudit.state().rendered() <= sequence || host.nativeOwnership().bytes() == 0)
          return null;
        var sample = GraphicDrawAudit.lastRendered();
        graphic = sample.graphic();
        camera = sample.camera();
        sceneBytes = host.retainedOutput().bytes();
        if (sceneBytes <= 0)
          throw new IllegalStateException("Prepared preview has no retained scene");
        preparationTimes.add(System.nanoTime() - prepareAt);
        UiCostAudit.watch(root, "preview-cycle-" + (cycle + 1));
        record(mc, "prepared");
        keyboard(root, "Rotate left");
        next(Phase.ROTATED);
      }
      case ROTATED -> {
        if (!drawn() || GraphicDrawAudit.lastRendered().camera().yaw() == camera.yaw()) return null;
        record(mc, "rotated");
        keyboard(root, "Zoom in");
        next(Phase.ZOOMED);
      }
      case ZOOMED -> {
        if (!drawn() || GraphicDrawAudit.lastRendered().camera().zoom() <= camera.zoom())
          return null;
        record(mc, "zoomed");
        keyboard(root, "Reset view");
        next(Phase.RESET);
      }
      case RESET -> {
        if (!drawn() || !GraphicDrawAudit.lastRendered().camera().equals(camera)) return null;
        record(mc, "reset");
        result.addProperty("cameraControlsReuseGraphic", true);
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.NARROW);
      }
      case NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || root.width == originalWidth && root.height == originalHeight) return null;
        NativeBindings.press(UiProbe.button(root, "Show preview"));
        next(Phase.NARROW_DRAW);
      }
      case NARROW_DRAW -> {
        if (!drawn() || !UiProbe.focusedPath(root, viewport(root))) return null;
        record(mc, "narrow");
        captured = false;
        NativeWorld.screenshot(mc, "production-viewport-narrow", () -> captured = true);
        next(Phase.NARROW_IMAGE);
      }
      case NARROW_IMAGE -> {
        if (!captured) return null;
        fit(root);
        NativeBindings.resizeWindow(mc, originalWindow[0], originalWindow[1]);
        next(Phase.RETURNED);
      }
      case RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), originalWindow)
            || root.width != originalWidth
            || root.height != originalHeight) return null;
        NativeBindings.press(UiProbe.button(root, "Show preview"));
        next(Phase.RETURNED_DRAW);
      }
      case RETURNED_DRAW -> {
        if (!drawn() || !UiProbe.focusedPath(root, viewport(root))) return null;
        record(mc, "returned");
        captured = false;
        NativeWorld.screenshot(mc, "production-viewport-returned", () -> captured = true);
        next(Phase.RETURNED_IMAGE);
      }
      case RETURNED_IMAGE -> {
        if (!captured) return null;
        fit(root);
        result.addProperty("realWindowReflowRetainsSceneAndInput", true);
        var actual = graphic.get();
        if (actual == null) throw new IllegalStateException("The live graphic disappeared");
        released = actual.releaseCompletion().toCompletableFuture();
        if (UiCostAudit.samples() < 8) return null;
        finishPaint();
        retainingViewport = false;
        pending = home();
        next(Phase.HOME_RELEASE);
      }
      case HOME_RELEASE -> {
        if (!pending.isDone()) return null;
        pending.join();
        if (!released.isDone()
            || host.nativeOwnership().bytes() != 0
            || host.retainedOutput().bytes() != 0
            || host.nativeOwnership().resources() != 0
            || host.retainedOutput().resources() != 0) return null;
        var state = released.join();
        if (!state.released() || state.outstandingUses() != 0 || state.releaseFailure().isPresent())
          throw new IllegalStateException("Hidden production preview did not retire");
        cycle++;
        if (UiCostAudit.preparations() - initialPreparations != cycle)
          throw new IllegalStateException("Preparation count diverged from explicit actions");
        var row = new JsonObject();
        row.addProperty("cycle", cycle);
        row.addProperty("providerEntries", UiCostAudit.preparations() - initialPreparations);
        row.addProperty("releasedNativeBytes", host.nativeOwnership().bytes());
        row.addProperty("releasedSceneBytes", host.retainedOutput().bytes());
        row.addProperty("releasedNativeResources", host.nativeOwnership().resources());
        row.addProperty("releasedSceneResources", host.retainedOutput().resources());
        row.addProperty("released", state.released());
        row.addProperty("outstandingUses", state.outstandingUses());
        row.addProperty("nativePeakBytes", host.nativeOwnership().peakBytes());
        row.addProperty("scenePeakBytes", host.retainedOutput().peakBytes());
        row.addProperty("nativePeakResources", host.nativeOwnership().peakResources());
        row.addProperty("scenePeakResources", host.retainedOutput().peakResources());
        row.addProperty("drawWidth", GraphicDrawAudit.lastRendered().width());
        row.addProperty("drawHeight", GraphicDrawAudit.lastRendered().height());
        cycles.add(row);
        if (cycle == CYCLES) {
          if (host.nativeOwnership().peakBytes() > host.limits().nativeBytes()
              || host.retainedOutput().peakBytes() > host.limits().outputBytes()
              || host.nativeOwnership().peakResources() > host.limits().resources()
              || host.retainedOutput().peakResources() > host.limits().resources()
              || host.releaseFailureCount() != 0)
            throw new IllegalStateException(
                "Repeated preview exceeded its declared tracked bounds or failed release");
          cost.addProperty("explicitPreparations", cycle);
          cost.addProperty("noUnexplainedTrackedRetention", true);
          cost.addProperty("retirementAfterEveryCycle", true);
          cost.addProperty("sameOwnerDraftAndSource", true);
          workflow.close();
          NativeUi.open(parent);
          next(Phase.RELEASE);
        } else {
          pending =
              WorkflowReveal.view(
                  workflow,
                  StyleSettingsStore.DEFINITION,
                  DestinationId.parse("lootr_styled:profiles_page"));
          next(Phase.REOPEN);
        }
      }
      case REOPEN -> {
        if (!pending.isDone()) return null;
        pending.join();
        if (!UiProbe.hasButton(root, "Prepare draft preview")
            || !UiProbe.button(root, "Prepare draft preview").active) return null;
        query =
            UiProbe.children(root).stream()
                .filter(EditBox.class::isInstance)
                .map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals("Search"))
                .findFirst()
                .orElseThrow();
        if (!query.getValue().equals("Vanilla") || query.getCursorPosition() != 3)
          throw new IllegalStateException("Repeated route lost actual query/caret");
        UiCostAudit.watch(root, "returned-profile-" + cycle);
        next(Phase.REOPEN_PAINT);
      }
      case REOPEN_PAINT -> {
        if (UiCostAudit.samples() < 8) return null;
        finishPaint();
        if (UiCostAudit.preparations() - initialPreparations != cycle
            || host.nativeOwnership().bytes() != 0
            || host.retainedOutput().bytes() != 0)
          throw new IllegalStateException(
              "Returning to Profiles implicitly prepared/retained a preview");
        cost.addProperty("noAutomaticPreparationOnReturn", true);
        retainingViewport = true;
        startPreparation(root);
        next(Phase.REPEAT_READY);
      }
      case REPEAT_READY -> {
        if (GraphicDrawAudit.state().rendered() <= sequence || host.nativeOwnership().bytes() == 0)
          return null;
        var sample = GraphicDrawAudit.lastRendered();
        if (sample.graphic().get() == graphic.get() || host.retainedOutput().bytes() != sceneBytes)
          throw new IllegalStateException(
              "Repeated preparation reused a retired lease or changed unchanged scene footprint");
        graphic = sample.graphic();
        camera = sample.camera();
        preparationTimes.add(System.nanoTime() - prepareAt);
        UiCostAudit.watch(root, "preview-cycle-" + (cycle + 1));
        keyboard(root, "Rotate left");
        next(Phase.REPEAT_CAMERA);
      }
      case REPEAT_CAMERA -> {
        if (!drawn()
            || GraphicDrawAudit.lastRendered().camera().yaw() == camera.yaw()
            || UiCostAudit.samples() < 8) return null;
        finishPaint();
        var actual = graphic.get();
        if (actual == null) throw new IllegalStateException("Repeated graphic disappeared");
        released = actual.releaseCompletion().toCompletableFuture();
        retainingViewport = false;
        pending = home();
        next(Phase.HOME_RELEASE);
      }
      case RELEASE -> {
        if (!released.isDone()
            || host.nativeOwnership().bytes() != 0
            || host.retainedOutput().bytes() != 0
            || host.nativeOwnership().resources() != 0
            || host.retainedOutput().resources() != 0) return null;
        var state = released.join();
        if (!state.released()
            || state.outstandingUses() != 0
            || state.releaseFailure().isPresent()
            || workflow.isOpen()
            || editor.isOpen())
          throw new IllegalStateException("Production viewport failed to retire");
        if (!Arrays.equals(original, Files.readAllBytes(settings)))
          throw new IllegalStateException("Viewport changed settings");
        result.addProperty("settingsUnchanged", true);
        result.addProperty(
            "resourceGenerationUnchanged",
            resourceGeneration.equals(
                PreviewProbeAccess.generation(PreviewProbeAccess.activePack())));
        result.addProperty("releaseCompleted", true);
        result.addProperty("releasedNativeBytes", host.nativeOwnership().bytes());
        result.addProperty("releasedSceneBytes", host.retainedOutput().bytes());
        return result;
      }
    }
    return null;
  }

  private void startPreparation(Screen root) {
    prepareAt = System.nanoTime();
    var button = UiProbe.button(root, "Prepare draft preview");
    if (!button.active) throw new IllegalStateException("Prepare draft preview inactive");
    NativeBindings.press(button);
    NativeBindings.press(UiProbe.button(root, "Show preview"));
  }

  private CompletableFuture<?> home() {
    var target = new ClientDestination.Home();
    var attempt = workflow.navigation().open(target);
    return attempt
        .completion()
        .thenApply(
            value -> {
              if (!value.id().equals(attempt.id())
                  || !value.target().equals(target)
                  || value.outcome() != NavigationAttempt.Outcome.REVEALED)
                throw new IllegalStateException(
                    "Actual Home reveal failed: " + value, value.cause().orElse(null));
              return value;
            })
        .toCompletableFuture();
  }

  private void finishPaint() {
    var value = UiCostAudit.finish();
    paints.add(value);
    if (value.get("providerEntriesInCallback").getAsLong() != 0
        || value.get("resourceContentLookupsInCallback").getAsLong() != 0)
      throw new IllegalStateException(
          "Observed UI callback performs domain preparation or resource-content lookup: " + value);
  }

  private void sourceUnchanged() throws Exception {
    var now = Files.readAttributes(settings, java.nio.file.attribute.BasicFileAttributes.class);
    if (!Arrays.equals(original, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !java.util.Objects.equals(attributes.fileKey(), now.fileKey())
        || !resourceGeneration.equals(
            PreviewProbeAccess.generation(PreviewProbeAccess.activePack()))
        || workflow.editor(StyleSettingsStore.DEFINITION) != editor
        || revision != null
            && !revision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
      throw new IllegalStateException(
          "Cost/retention check changed source, generation, owner or draft");
  }

  private void next(Phase value) {
    phase = value;
    ticks = 0;
    sequence = GraphicDrawAudit.state().rendered();
  }

  private boolean drawn() {
    if (GraphicDrawAudit.state().rendered() <= sequence) return false;
    var sample = GraphicDrawAudit.lastRendered();
    if (graphic.get() == null
        || sample.graphic().get() != graphic.get()
        || host.retainedOutput().bytes() != sceneBytes)
      throw new IllegalStateException(
          "Camera or window change replaced its prepared graphic/scene");
    return true;
  }

  private void unchanged() throws Exception {
    if (!revision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
        || workflow.editor(StyleSettingsStore.DEFINITION) != editor
        || !Arrays.equals(original, Files.readAllBytes(settings))
        || !resourceGeneration.equals(
            PreviewProbeAccess.generation(PreviewProbeAccess.activePack()))
        || !UiProbe.children(workflow.screen()).contains(query)
        || !query.getValue().equals("Vanilla")
        || query.getCursorPosition() != 3)
      throw new IllegalStateException(
          "Viewport interaction lost its owner, draft, source, or query");
  }

  private void keyboard(Screen screen, String action) {
    var button = UiProbe.button(screen, action);
    if (!UiProbe.focusControl(screen, button) || !NativeBindings.activateFocused(screen))
      throw new IllegalStateException("Keyboard did not activate " + action);
    NativeBindings.press(UiProbe.button(screen, "Show preview"));
  }

  private static AbstractWidget viewport(Screen root) {
    return UiProbe.children(root).stream()
        .filter(AbstractWidget.class::isInstance)
        .map(AbstractWidget.class::cast)
        .filter(value -> value.getMessage().getString().equals("Draft preview"))
        .findFirst()
        .orElseThrow();
  }

  private void fit(Screen root) {
    var widget = viewport(root);
    var panel =
        UiProbe.children(root).stream()
            .filter(ScrollFrame.class::isInstance)
            .map(ScrollFrame.class::cast)
            .filter(value -> value.children().contains(widget))
            .findFirst()
            .orElseThrow();
    double x = widget.getX() + widget.getWidth() / 2.0,
        y = widget.getY() + widget.getHeight() / 2.0;
    var bounds = new JsonObject();
    bounds.addProperty("x", widget.getX());
    bounds.addProperty("y", widget.getY());
    bounds.addProperty("width", widget.getWidth());
    bounds.addProperty("height", widget.getHeight());
    bounds.addProperty("topVisible", panel.isMouseOver(x, widget.getY()));
    bounds.addProperty(
        "bottomVisible", panel.isMouseOver(x, widget.getY() + widget.getHeight() - 1));
    bounds.addProperty("leftVisible", panel.isMouseOver(widget.getX(), y));
    bounds.addProperty(
        "rightWithScrollbarGutterVisible",
        panel.isMouseOver(widget.getX() + widget.getWidth() + 7, y));
    result.add("lastViewportBounds", bounds);
    if (!panel.isMouseOver(x, widget.getY())
        || !panel.isMouseOver(x, widget.getY() + widget.getHeight() - 1)
        || !panel.isMouseOver(widget.getX(), y)
        || !panel.isMouseOver(widget.getX() + widget.getWidth() + 7, y))
      throw new IllegalStateException("The production viewport escapes its native clipping pane");
  }

  private void record(Minecraft mc, String name) {
    var sample = GraphicDrawAudit.lastRendered();
    var row = new JsonObject();
    var size = NativeBindings.windowSize(mc);
    row.addProperty("phase", name);
    row.addProperty("windowWidth", size[0]);
    row.addProperty("windowHeight", size[1]);
    row.addProperty("guiWidth", workflow.screen().width);
    row.addProperty("guiHeight", workflow.screen().height);
    row.addProperty("drawWidth", sample.width());
    row.addProperty("drawHeight", sample.height());
    row.addProperty("camera", sample.camera().toString());
    row.addProperty("nativeBytes", host.nativeOwnership().bytes());
    row.addProperty("sceneBytes", host.retainedOutput().bytes());
    frames.add(row);
  }

  String progress() {
    return phase.toString();
  }

  JsonObject observations() {
    var value = result.deepCopy();
    value.addProperty("phase", phase.toString());
    value.addProperty("uiSamples", UiCostAudit.samples());
    value.addProperty("providerEntries", UiCostAudit.preparations() - initialPreparations);
    value.addProperty("nativeBytes", host.nativeOwnership().bytes());
    value.addProperty("sceneBytes", host.retainedOutput().bytes());
    if (editor != null) {
      value.addProperty("canSubmit", editor.canSubmit());
      editor
          .form()
          .fieldControl(MODELS)
          .ifPresent(
              widget -> {
                if (widget instanceof EditBox input)
                  value.addProperty("nativeModelsText", input.getValue());
              });
    }
    return value;
  }
}
