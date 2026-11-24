package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.minecraft.client.ClientMeshGraphic;
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
    OPEN,
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
    RELEASE
  }

  private Phase phase = Phase.OPEN;
  private final ClientEditingWorkflow workflow;
  private final ClientGraphicHost host;
  private final Screen parent;
  private final Path settings;
  private final byte[] original;
  private final java.util.UUID resourceGeneration;
  private final int[] originalWindow;
  private final int originalWidth, originalHeight;
  private final CompletableFuture<?> admission;
  private ClientEditor<ClientSettings> editor;
  private EditBox query;
  private DraftRevision revision;
  private GraphicCamera camera;
  private WeakReference<ClientMeshGraphic> graphic;
  private CompletableFuture<RenderLifetime.State> released;
  private long sequence, sceneBytes;
  private int ticks;
  private boolean captured;
  private final JsonObject result = new JsonObject();
  private final JsonArray frames = new JsonArray();

  ProductionViewportProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
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
        WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"))
            .thenAccept(value -> editor = value);
    result.add("frames", frames);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 400)
      throw new IllegalStateException("Production viewport timed out: " + phase + ": " + result);
    if (!admission.isDone()) return null;
    admission.join();
    var root = phase == Phase.RELEASE ? null : workflow.screen();
    if (phase != Phase.RELEASE && phase != Phase.OPEN) unchanged();
    switch (phase) {
      case OPEN -> {
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
        query.setValue("Vanilla");
        query.setCursorPosition(3);
        revision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        sequence = GraphicDrawAudit.state().rendered();
        NativeBindings.press(prepare.orElseThrow());
        NativeBindings.press(UiProbe.button(root, "Show preview"));
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
        workflow.close();
        NativeUi.open(parent);
        next(Phase.RELEASE);
      }
      case RELEASE -> {
        if (!released.isDone()
            || host.nativeOwnership().bytes() != 0
            || host.retainedOutput().bytes() != 0) return null;
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
    return result.deepCopy();
  }
}
