package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.*;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.minecraft.client.*;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicCamera;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Actual product sections, picker gestures, captured inputs, and native domain draws. */
final class SemanticProfilesProbe {
  private final Screen parent, root;
  private final ClientEditingWorkflow workflow;
  private final Path settings;
  private final byte[] originalBytes;
  private final ClientSettings original;
  private final UUID generation;
  private final java.nio.file.attribute.FileTime modified;
  private final JsonArray captures = new JsonArray();
  private final JsonArray gestures = new JsonArray();
  private ClientEditor<ClientSettings> editor;
  private ClientGraphicHost host;
  private FrameCapture capture;
  private int stage, phaseTicks;
  private long drawBaseline;
  private GraphicCamera expectedCamera;
  private java.lang.ref.WeakReference<ClientMeshGraphic> rotatedGraphic =
      new java.lang.ref.WeakReference<>(null);
  private final JsonArray cameraChecks = new JsonArray();
  private java.lang.ref.WeakReference<ClientMeshGraphic> lastGraphic =
      new java.lang.ref.WeakReference<>(null);

  SemanticProfilesProbe(Minecraft mc) throws Exception {
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    originalBytes = Files.readAllBytes(settings);
    original = ClientSettings.parse(originalBytes);
    modified = Files.getLastModifiedTime(settings);
    generation = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    parent = NativeUi.current();
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    SemanticProfileAudit.begin();
    NativeUi.open(root);
    pending = WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++phaseTicks > 900)
      throw new IllegalStateException("Semantic fixture stalled: " + report());
    if (!Arrays.equals(originalBytes, Files.readAllBytes(settings))
        || !modified.equals(Files.getLastModifiedTime(settings))
        || !generation.equals(PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
      throw new IllegalStateException("Semantic preview changed saved settings or resources");
    switch (stage) {
      case 0 -> {
        if (!pending.isDone()) return null;
        pending.join();
        editor = workflow.editor(StyleSettingsStore.DEFINITION);
        host =
            workflow.graphics(
                new GraphicLimits(16L << 20, 32L << 20, 16L << 20, 256, 128L << 20), 1);
        if (queries().size() != 2) return null;
        if (SemanticProfileAudit.count() != 0)
          throw new IllegalStateException("Appearance prepared on entry");
        queries().getFirst().setValue("Vanilla");
        next(1);
      }
      case 1 -> {
        if (chooseDown("Vanilla")) next(2);
      }
      case 2 -> {
        if (accept("Vanilla")) next(3);
        else next(1);
      }
      case 3 -> {
        var expected =
            new ClientSettings(
                original.enabled(), ClientSettings.VANILLA, original.artwork(), original.fresh());
        if (!candidate().equals(expected)
            || !UiProbe.button(root, "Rotate left").active
            || !drawn("DRAFT", expected)) return null;
        rotate("Rotate left", -15);
        next(17);
      }
      case 4 -> {
        if (chooseDown("Default")) next(5);
      }
      case 5 -> {
        if (accept("Default")) next(6);
        else next(4);
      }
      case 6 -> {
        if (!candidate().equals(original) || !drawn("DRAFT", original)) return null;
        requireCamera("Appearance replacement");
        capture = new FrameCapture(root);
        next(7);
      }
      case 7 -> {
        if (!capture.poll(mc, "semantic-appearance")) return null;
        captures.add(capture.receipt("semantic-appearance"));
        navigate(new ClientDestination.Home());
        next(8);
      }
      case 8 -> {
        if (!pending.isDone()) return null;
        pending.join();
        if (!drained()) return null;
        if (SemanticProfileAudit.count() != 2)
          throw new IllegalStateException("Home scheduled another preparation");
        pending =
            WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"));
        next(9);
      }
      case 9 -> {
        if (!pending.isDone()) return null;
        pending.join();
        if (workflow.editor(StyleSettingsStore.DEFINITION) != editor)
          throw new IllegalStateException("Profiles replaced its owner");
        if (queries().size() != 1) return null;
        if (SemanticProfileAudit.count() != 2)
          throw new IllegalStateException("Profiles prepared on entry");
        queries().getFirst().setValue("Vanilla");
        next(10);
      }
      case 10 -> {
        if (chooseDown("Vanilla")) next(11);
      }
      case 11 -> {
        if (accept("Vanilla")) next(12);
        else next(10);
      }
      case 12 -> {
        if (!candidate().equals(original))
          throw new IllegalStateException("Profile inspection staged a value");
        var expected =
            new ClientSettings(
                original.enabled(), ClientSettings.VANILLA, original.artwork(), original.fresh());
        if (!UiProbe.button(root, "Rotate right").active || !drawn("AUDITION", expected))
          return null;
        rotate("Rotate right", 15);
        next(18);
      }
      case 13 -> {
        if (!candidate().equals(original))
          throw new IllegalStateException("Audition role changed the draft");
        var expected =
            new ClientSettings(
                original.enabled(), original.models(), ClientSettings.VANILLA, original.fresh());
        if (!drawn("AUDITION", expected)) return null;
        requireCamera("Audition role replacement");
        capture = new FrameCapture(root);
        next(14);
      }
      case 14 -> {
        if (!capture.poll(mc, "semantic-audition")) return null;
        captures.add(capture.receipt("semantic-audition"));
        workflow.close();
        NativeUi.open(parent);
        next(15);
      }
      case 15 -> {
        if (!drained()) return null;
        if (SemanticProfileAudit.count() != 4 || workflow.isOpen())
          throw new IllegalStateException(
              "Unexpected preparation count or live workflow after close");
        next(16);
        return report();
      }
      case 17 -> {
        if (!cameraRedrawn()) return null;
        if (SemanticProfileAudit.count() != 1)
          throw new IllegalStateException("Appearance rotation prepared another scene");
        requireCamera("Appearance rotation");
        queries().getFirst().setValue("Default");
        next(4);
      }
      case 18 -> {
        if (!cameraRedrawn()) return null;
        if (SemanticProfileAudit.count() != 3)
          throw new IllegalStateException("Audition rotation prepared another scene");
        requireCamera("Audition rotation");
        drawBaseline = GraphicDrawAudit.state().rendered();
        NativeBindings.press(UiProbe.button(root, "Try for Artwork"));
        next(13);
      }
      default -> throw new IllegalStateException("Unknown semantic fixture phase");
    }
    return null;
  }

  private CompletableFuture<?> pending;

  private ClientSettings candidate() {
    return editor.view().snapshot().orElseThrow().inspect().candidate().orElseThrow().value();
  }

  private List<EditBox> queries() {
    return visualOrder(
        UiProbe.children(root).stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .filter(value -> value.getMessage().getString().equals("Search profiles"))
            .toList());
  }

  private List<AbstractWidget> profileLists() {
    return visualOrder(
        UiProbe.children(root).stream()
            .filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast)
            .filter(value -> value.getMessage().getString().equals("Available profiles"))
            .toList());
  }

  private static <W extends AbstractWidget> List<W> visualOrder(List<W> values) {
    var result = new java.util.ArrayList<>(values);
    boolean columns =
        values.stream().mapToInt(AbstractWidget::getX).max().orElse(0)
                - values.stream().mapToInt(AbstractWidget::getX).min().orElse(0)
            > 32;
    result.sort(
        (a, b) ->
            columns ? Integer.compare(a.getX(), b.getX()) : Integer.compare(a.getY(), b.getY()));
    return List.copyOf(result);
  }

  private net.minecraft.client.gui.components.events.GuiEventListener focusedLeaf() {
    net.minecraft.client.gui.components.events.GuiEventListener current = root;
    while (current
        instanceof net.minecraft.client.gui.components.events.ContainerEventHandler parent) {
      var child = parent.getFocused();
      if (child == null || child == current) break;
      current = child;
    }
    return current;
  }

  private boolean chooseDown(String title) {
    var lists = profileLists();
    if (lists.isEmpty() || !lists.getFirst().active) return false;
    var target = lists.getFirst();
    if (!UiProbe.focusControl(root, target) || focusedLeaf() != target)
      throw new IllegalStateException("Native list is not the actual dispatched focus leaf");
    observeGesture("down", target);
    if (!NativeBindings.key(root, "key.keyboard.down"))
      throw new IllegalStateException("Native list did not accept Down");
    return true;
  }

  private boolean accept(String title) {
    var target = profileLists().getFirst();
    if (focusedLeaf() != target
        || !NativeBindings.narration(target).contains("Available profiles. " + title)) return false;
    observeGesture("enter", target);
    drawBaseline = GraphicDrawAudit.state().rendered();
    if (!NativeBindings.key(root, "key.keyboard.enter"))
      throw new IllegalStateException("Native list did not accept Enter");
    return true;
  }

  private void observeGesture(String kind, AbstractWidget target) {
    if (gestures.size() >= 32)
      throw new IllegalStateException("Semantic gesture observer exceeded bound");
    var row = new JsonObject();
    row.addProperty("stage", stage);
    row.addProperty("gesture", kind);
    row.addProperty("x", target.getX());
    row.addProperty("y", target.getY());
    row.addProperty("actualFocusLeaf", focusedLeaf() == target);
    row.addProperty("narration", NativeBindings.narration(target));
    var inputs = new JsonArray();
    for (var query : queries()) {
      var item = new JsonObject();
      item.addProperty("x", query.getX());
      item.addProperty("y", query.getY());
      item.addProperty("text", query.getValue());
      inputs.add(item);
    }
    row.add("queries", inputs);
    row.addProperty("modelsBefore", candidate().models().toString());
    row.addProperty("artworkBefore", candidate().artwork().toString());
    gestures.add(row);
  }

  private boolean drawn(String basis, ClientSettings expected) {
    if (!focusPreview()) return false;
    if (!SemanticProfileAudit.last(basis, expected)
        || GraphicDrawAudit.state().rendered() <= drawBaseline
        || host.retainedOutput().bytes() == 0) return false;
    var graphic = GraphicDrawAudit.lastRendered().graphic().get();
    if (graphic == null || graphic == lastGraphic.get()) return false;
    lastGraphic = new java.lang.ref.WeakReference<>(graphic);
    return true;
  }

  private boolean focusPreview() {
    var viewport =
        UiProbe.children(root).stream()
            .filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast)
            .filter(value -> value.getMessage().getString().equals("Appearance preview"))
            .findFirst();
    if (viewport.isEmpty()) return false;
    var widget = viewport.orElseThrow();
    UiProbe.focusControl(root, widget);
    var panel =
        UiProbe.children(root).stream()
            .filter(ScrollFrame.class::isInstance)
            .map(ScrollFrame.class::cast)
            .filter(value -> value.children().contains(widget))
            .findFirst()
            .orElseThrow();
    double x = widget.getX() + widget.getWidth() / 2.0;
    if (!panel.isMouseOver(x, widget.getY() + widget.getHeight() / 2.0)) {
      panel.mouseScrolled(x, root.height / 2.0, 0, -4);
      return false;
    }
    return true;
  }

  private boolean drained() {
    return host.retainedInput().bytes() == 0
        && host.retainedOutput().bytes() == 0
        && host.nativeOwnership().bytes() == 0;
  }

  private void rotate(String label, double yaw) {
    var sample = GraphicDrawAudit.lastRendered();
    var camera = sample.camera();
    expectedCamera =
        new GraphicCamera(
            camera.yaw() + yaw, camera.pitch(), camera.zoom(), camera.panX(), camera.panY());
    rotatedGraphic = sample.graphic();
    drawBaseline = sample.sequence();
    var control = UiProbe.button(root, label);
    if (!control.active
        || !UiProbe.focusControl(root, control)
        || focusedLeaf() != control
        || !NativeBindings.activateFocused(root))
      throw new IllegalStateException("Current native camera control refused: " + label);
    focusPreview();
  }

  private boolean cameraRedrawn() {
    if (!focusPreview()) return false;
    var sample = GraphicDrawAudit.lastRendered();
    return sample.sequence() > drawBaseline
        && rotatedGraphic.get() != null
        && sample.graphic().get() == rotatedGraphic.get()
        && sample.camera().equals(expectedCamera);
  }

  private void requireCamera(String check) {
    var actual = GraphicDrawAudit.lastRendered().camera();
    if (!actual.equals(expectedCamera))
      throw new IllegalStateException(check + " reset the edited camera: " + actual);
    var row = new JsonObject();
    row.addProperty("check", check);
    row.addProperty("yaw", actual.yaw());
    row.addProperty("pitch", actual.pitch());
    row.addProperty("zoom", actual.zoom());
    row.addProperty("panX", actual.panX());
    row.addProperty("panY", actual.panY());
    cameraChecks.add(row);
  }

  private void navigate(ClientDestination target) {
    var attempt = workflow.navigation().open(target);
    pending =
        attempt
            .completion()
            .thenApply(
                result -> {
                  if (!result.id().equals(attempt.id())
                      || result.outcome() != NavigationAttempt.Outcome.REVEALED)
                    throw new IllegalStateException(
                        "Semantic navigation did not reveal: " + result);
                  return result;
                })
            .toCompletableFuture();
  }

  private void next(int value) {
    stage = value;
    phaseTicks = 0;
  }

  String progress() {
    return Integer.toString(stage);
  }

  JsonObject report() {
    var result = new JsonObject();
    result.addProperty("stage", stage);
    result.addProperty("complete", stage == 16);
    result.add("capturedInputs", SemanticProfileAudit.report());
    result.add("captures", captures);
    result.add("gestures", gestures);
    result.add("cameraChecks", cameraChecks);
    if (expectedCamera != null) {
      var sample = GraphicDrawAudit.lastRendered();
      var diagnostic = new JsonObject();
      diagnostic.addProperty("expected", expectedCamera.toString());
      diagnostic.addProperty("lastDrawn", sample.camera().toString());
      diagnostic.addProperty("drawBaseline", drawBaseline);
      diagnostic.addProperty("lastDrawSequence", sample.sequence());
      diagnostic.addProperty("sameGraphic", sample.graphic().get() == rotatedGraphic.get());
      result.add("cameraObservation", diagnostic);
    }
    if (host != null) {
      result.addProperty("retainedInputBytes", host.retainedInput().bytes());
      result.addProperty("retainedOutputBytes", host.retainedOutput().bytes());
      result.addProperty("nativeBytes", host.nativeOwnership().bytes());
    }
    var labels = new JsonArray();
    UiProbe.children(root).stream()
        .filter(AbstractWidget.class::isInstance)
        .map(AbstractWidget.class::cast)
        .forEach(value -> labels.add(value.getMessage().getString()));
    result.add("controlLabels", labels);
    return result;
  }
}
