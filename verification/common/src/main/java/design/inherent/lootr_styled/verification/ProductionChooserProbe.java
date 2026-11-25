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
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.NavigationAttempt;
import design.inherent.reform.minecraft.client.NavigationBookmark;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Real reference search and inspection across native window events, without choosing or saving. */
final class ProductionChooserProbe {
  private enum Phase {
    OPEN,
    BROWSER,
    SEARCHED,
    INSPECTED,
    NARROW,
    NARROW_IMAGE,
    RETURNED,
    RETURNED_IMAGE,
    BACK,
    NAV_ORIGIN,
    NAV_QUERY,
    NAV_CHANGED,
    NAV_UP,
    NAV_RESTORED,
    NAV_NARROW,
    NAV_RETURNED,
    NAV_BASELINE,
    CLOSED
  }

  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private final Screen parent, root;
  private final ClientEditingWorkflow workflow;
  private final CompletableFuture<?> admission;
  private final Path settings;
  private final byte[] original;
  private final BasicFileAttributes attributes;
  private final UUID generation;
  private final int[] window;
  private final String inspectedTitle, inspectedId, queryText;
  private final JsonObject result = new JsonObject();
  private final JsonArray states = new JsonArray();
  private ClientEditor<ClientSettings> editor;
  private DraftRevision revision;
  private Screen browser;
  private int browserWidth, browserHeight, ticks, captureTicks;
  private boolean captureRequested, captured;
  private CompletableFuture<?> navigationReveal;
  private NavigationAttempt navigationAttempt;
  private NavigationBookmark navigationBaseline, navigationBookmark;
  private int upSteps, rootWidth, rootHeight;
  private final JsonArray upTargets = new JsonArray();
  private final JsonObject navigationResult = new JsonObject();
  private Phase phase = Phase.OPEN;

  ProductionChooserProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    attributes = Files.readAttributes(settings, BasicFileAttributes.class);
    generation = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    window = NativeBindings.windowSize(mc);
    if (Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-local-profile"))) {
      var descriptor =
          JsonParser.parseString(
                  Files.readString(
                      ClientRuntime.configuration().root().resolve("fixture/profile.json")))
              .getAsJsonObject();
      inspectedTitle = descriptor.get("title").getAsString();
      inspectedId = "lootr_styled_local:fixture";
      queryText =
          inspectedTitle.substring(
              0,
              inspectedTitle.offsetByCodePoints(
                  0, Math.min(32, inspectedTitle.codePointCount(0, inspectedTitle.length()))));
    } else {
      inspectedTitle = "Vanilla (Inherited)";
      inspectedId = ClientSettings.VANILLA.toString();
      queryText = "Vanilla";
    }
    if (queryText.length() < 3) throw new IllegalArgumentException("Chooser query is too short");
    result.addProperty("inspectedTitle", inspectedTitle);
    result.addProperty("inspectedId", inspectedId);
    result.addProperty("queryText", queryText);
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    admission =
        WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, MODELS)
            .thenAccept(value -> editor = value);
    result.add("states", states);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 300)
      throw new IllegalStateException("Chooser timed out: " + phase + ": " + observations());
    if (!admission.isDone()) return null;
    admission.join();
    unchanged();
    switch (phase) {
      case OPEN -> {
        if (editor.form().fieldControl(MODELS).isEmpty() || !editor.canSubmit()) return null;
        revision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        var candidate =
            editor.view().snapshot().orElseThrow().inspect().candidate().orElseThrow().value();
        result.addProperty("baselineModels", candidate.models().toString());
        result.addProperty("baselineArtwork", candidate.artwork().toString());
        if (!editor.form().focusField(MODELS) || !NativeBindings.activateFocused(root))
          throw new IllegalStateException("Native Models activation failed");
        next(Phase.BROWSER);
      }
      case BROWSER -> {
        browser = NativeUi.current();
        if (browser == root || !UiProbe.hasButton(browser, "Search")) return null;
        if (!UiProbe.button(browser, "Search").active) return null;
        box("Search").setValue(queryText);
        press("Search");
        next(Phase.SEARCHED);
      }
      case SEARCHED -> {
        if (UiProbe.hasButton(browser, "Default")
            || !UiProbe.hasButton(browser, inspectedTitle)
            || !UiProbe.button(browser, inspectedTitle).active) return null;
        var narration = NativeBindings.narration(UiProbe.button(browser, inspectedTitle));
        if (!narration.contains(inspectedTitle))
          throw new IllegalStateException("Chooser narration truncated the profile title");
        result.addProperty("inspectedChoiceNarration", narration);
        result.addProperty("searchRemovedDefaultChoice", true);
        press(inspectedTitle);
        next(Phase.INSPECTED);
      }
      case INSPECTED -> {
        if (!box("Reference ID").getValue().equals(inspectedId)
            || !UiProbe.button(browser, "Use selection").active) return null;
        var query = box("Search");
        if (!UiProbe.focusControl(browser, query))
          throw new IllegalStateException("Cannot focus query");
        query.setCursorPosition(3);
        query.setHighlightPos(1);
        assertRetained();
        browserWidth = browser.width;
        browserHeight = browser.height;
        state(mc, "inspected");
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.NARROW);
      }
      case NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || browser.width == browserWidth && browser.height == browserHeight) return null;
        assertRetained();
        state(mc, "narrow");
        next(Phase.NARROW_IMAGE);
      }
      case NARROW_IMAGE -> {
        if (!capture(mc, "production-chooser-narrow")) return null;
        assertRetained();
        NativeBindings.resizeWindow(mc, window[0], window[1]);
        next(Phase.RETURNED);
      }
      case RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), window)
            || browser.width != browserWidth
            || browser.height != browserHeight) return null;
        assertRetained();
        state(mc, "returned");
        next(Phase.RETURNED_IMAGE);
      }
      case RETURNED_IMAGE -> {
        if (!capture(mc, "production-chooser-returned")) return null;
        assertRetained();
        result.addProperty("queryAndInspectionSurviveWindowReflow", true);
        result.addProperty("inspectionDoesNotStage", true);
        press("Back");
        next(Phase.BACK);
      }
      case BACK -> {
        if (NativeUi.current() != root || !editor.canSubmit()) return null;
        unchangedDraft();
        navigationReveal = WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, MODELS);
        next(Phase.NAV_ORIGIN);
      }
      case NAV_ORIGIN -> {
        if (!navigationReveal.isDone()) return null;
        navigationReveal.join();
        navigationBaseline = workflow.navigation().bookmark();
        navigationQuery().setValue("Models");
        next(Phase.NAV_QUERY);
      }
      case NAV_QUERY -> {
        var query = navigationQuery();
        if (!query.getValue().equals("Models")) return null;
        query.setCursorPosition(3);
        query.setHighlightPos(1);
        if (!UiProbe.focusControl(root, query))
          throw new IllegalStateException("Cannot focus actual workspace search");
        navigationBookmark = workflow.navigation().bookmark();
        if (!navigationBookmark.navigation().orElseThrow().filter().equals("ALL"))
          throw new IllegalStateException("Unexpected initial workspace filter");
        navigationResult.addProperty(
            "query", navigationBookmark.navigation().orElseThrow().query());
        navigationResult.addProperty(
            "caret", navigationBookmark.navigation().orElseThrow().cursor());
        navigationResult.addProperty(
            "selectionIndex", navigationBookmark.navigation().orElseThrow().selection());
        navigationResult.addProperty(
            "initialFilter", navigationBookmark.navigation().orElseThrow().filter());
        navigationResult.addProperty(
            "expandedCount", navigationBookmark.navigation().orElseThrow().expandedCount());
        var filter =
            UiProbe.children(root).stream()
                .filter(value -> value instanceof CycleButton<?>)
                .map(value -> (CycleButton<?>) value)
                .filter(value -> value.getMessage().getString().startsWith("Show"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing workspace filter"));
        if (!UiProbe.focusControl(root, filter) || !NativeBindings.activateFocused(root))
          throw new IllegalStateException("Native workspace filter activation failed");
        navigationQuery().setValue("No matching setting");
        next(Phase.NAV_CHANGED);
      }
      case NAV_CHANGED -> {
        var changed = workflow.navigation().bookmark().navigation().orElseThrow();
        if (changed.filter().equals("ALL") || !changed.query().equals("No matching setting"))
          return null;
        navigationResult.addProperty("changedFilter", changed.filter());
        navigationAttempt = workflow.navigation().up().orElseThrow();
        next(Phase.NAV_UP);
      }
      case NAV_UP -> {
        if (!navigationReady()) return null;
        var visited = new JsonObject();
        visited.addProperty("attempt", navigationAttempt.id().toString());
        visited.addProperty("kind", navigationAttempt.target().getClass().getSimpleName());
        visited.addProperty("target", navigationAttempt.target().toString());
        visited.addProperty("outcome", navigationAttempt.result().orElseThrow().outcome().name());
        upTargets.add(visited);
        if (++upSteps > 8) throw new IllegalStateException("Actual hierarchy did not reach Home");
        if (workflow.navigation().current().orElseThrow() instanceof ClientDestination.Home) {
          if (workflow.navigation().up().isPresent())
            throw new IllegalStateException("Rooted Home unexpectedly has a parent");
          navigationResult.add("upTargets", upTargets);
          navigationAttempt = workflow.navigation().restore(navigationBookmark);
          next(Phase.NAV_RESTORED);
        } else navigationAttempt = workflow.navigation().up().orElseThrow();
      }
      case NAV_RESTORED -> {
        if (!navigationReady()) return null;
        assertNavigation();
        navigationResult.addProperty("bookmarkRestoresNativeQueryFilterAndFocus", true);
        rootWidth = root.width;
        rootHeight = root.height;
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.NAV_NARROW);
      }
      case NAV_NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || root.width == rootWidth && root.height == rootHeight) return null;
        assertNavigation();
        navigationResult.addProperty("narrowGui", root.width + "x" + root.height);
        NativeBindings.resizeWindow(mc, window[0], window[1]);
        next(Phase.NAV_RETURNED);
      }
      case NAV_RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), window)
            || root.width != rootWidth
            || root.height != rootHeight) return null;
        assertNavigation();
        navigationResult.addProperty("bookmarkStateSurvivesActualWindowRoundTrip", true);
        navigationAttempt = workflow.navigation().restore(navigationBaseline);
        next(Phase.NAV_BASELINE);
      }
      case NAV_BASELINE -> {
        if (!navigationReady()) return null;
        if (!navigationQuery()
            .getValue()
            .equals(navigationBaseline.navigation().orElseThrow().query()))
          throw new IllegalStateException("Original workspace query was not restored");
        unchangedDraft();
        navigationResult.addProperty("sameOwnerDraftSourceAndGeneration", true);
        result.add("workspaceNavigation", navigationResult);
        if (!UiProbe.button(root, "Done").active) return null;
        NativeBindings.press(UiProbe.button(root, "Done"));
        next(Phase.CLOSED);
      }
      case CLOSED -> {
        if (workflow.isOpen() || editor.isOpen() || NativeUi.current() != parent) return null;
        result.addProperty("sameDraftAndOwner", true);
        result.addProperty("sourceFileAndGenerationUnchanged", true);
        result.addProperty("closedCleanly", true);
        return result;
      }
    }
    return null;
  }

  private boolean navigationReady() {
    var value = navigationAttempt.result();
    if (value.isEmpty()) return false;
    var completed = value.orElseThrow();
    if (completed.outcome() != NavigationAttempt.Outcome.REVEALED
        || !completed.id().equals(navigationAttempt.id())
        || !completed.target().equals(navigationAttempt.target())
        || NativeUi.current() != root)
      throw new IllegalStateException("Actual workspace destination not revealed: " + completed);
    return true;
  }

  private EditBox navigationQuery() {
    return UiProbe.children(root).stream()
        .filter(EditBox.class::isInstance)
        .map(EditBox.class::cast)
        .filter(value -> value.getMessage().getString().equals("Search settings"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing actual workspace search"));
  }

  private void assertNavigation() {
    var query = navigationQuery();
    var state = workflow.navigation().bookmark().navigation().orElseThrow();
    var expected = navigationBookmark.navigation().orElseThrow();
    if (!query.getValue().equals("Models")
        || query.getCursorPosition() != 3
        || !query.getHighlighted().equals("od")
        || !UiProbe.focusedPath(root, query)
        || !state.filter().equals(expected.filter())
        || state.expandedCount() != expected.expandedCount()
        || !workflow.navigation().current().orElseThrow().equals(navigationBookmark.destination()))
      throw new IllegalStateException(
          "Workspace bookmark lost native state: query="
              + query.getValue()
              + ", cursor="
              + query.getCursorPosition()
              + ", selection="
              + query.getHighlighted()
              + ", focus="
              + UiProbe.focusedPath(root, query)
              + ", filter="
              + state.filter()
              + ", expanded="
              + state.expandedCount()
              + ", target="
              + workflow.navigation().current());
    unchangedDraft();
  }

  private EditBox box(String name) {
    return UiProbe.children(browser).stream()
        .filter(EditBox.class::isInstance)
        .map(EditBox.class::cast)
        .filter(value -> value.getMessage().getString().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing actual chooser input: " + name));
  }

  private void press(String name) {
    var control = UiProbe.button(browser, name);
    if (!control.active) throw new IllegalStateException("Inactive chooser control: " + name);
    NativeBindings.press(control);
  }

  private void assertRetained() {
    var query = box("Search");
    if (NativeUi.current() != browser
        || !query.getValue().equals(queryText)
        || query.getCursorPosition() != 3
        || !query.getHighlighted().equals(queryText.substring(1, 3))
        || !UiProbe.focusedPath(browser, query)
        || !box("Reference ID").getValue().equals(inspectedId))
      throw new IllegalStateException("Chooser lost query, focus or inspection: " + observations());
    unchangedDraft();
  }

  private void unchangedDraft() {
    if (!revision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
      throw new IllegalStateException("Source browsing staged the settings draft");
  }

  private void unchanged() throws Exception {
    var now = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(original, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !java.util.Objects.equals(attributes.fileKey(), now.fileKey())
        || !generation.equals(PreviewProbeAccess.generation(PreviewProbeAccess.activePack()))
        || workflow.isOpen() && workflow.editor(StyleSettingsStore.DEFINITION) != editor)
      throw new IllegalStateException("Chooser changed source, generation or owner");
  }

  private void state(Minecraft mc, String name) {
    var value = observations();
    value.addProperty("phase", name);
    int[] size = NativeBindings.windowSize(mc);
    value.addProperty("window", size[0] + "x" + size[1]);
    value.addProperty("gui", browser.width + "x" + browser.height);
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
    if (editor != null && editor.isOpen() && editor.view().snapshot().isPresent()) {
      var candidate =
          editor.view().snapshot().orElseThrow().inspect().candidate().orElseThrow().value();
      value.addProperty("models", candidate.models().toString());
      value.addProperty("artwork", candidate.artwork().toString());
    }
    if (browser == null || browser == root) return value;
    for (var child : UiProbe.children(browser)) {
      if (!(child instanceof EditBox box)) continue;
      if (box.getMessage().getString().equals("Search")) {
        value.addProperty("query", box.getValue());
        value.addProperty("caret", box.getCursorPosition());
        value.addProperty("selection", box.getHighlighted());
        value.addProperty("queryFocus", UiProbe.focusedPath(browser, box));
      }
      if (box.getMessage().getString().equals("Reference ID"))
        value.addProperty("inspected", box.getValue());
    }
    return value;
  }
}
