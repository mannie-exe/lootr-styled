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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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
    MISSING,
    MISSING_NARROW,
    MISSING_NARROW_IMAGE,
    MISSING_RETURNED,
    MISSING_RETURNED_IMAGE,
    BACK,
    REVIEW,
    REVIEW_NARROW,
    REVIEW_CYCLE,
    REVIEW_IMAGE,
    REVIEW_RETURNED,
    REVIEW_BACK,
    NAV_ORIGIN,
    NAV_QUERY,
    NAV_CHANGED,
    NAV_UP,
    NAV_RESTORED,
    NAV_NARROW,
    NAV_DRAWER,
    ANCESTOR_MENU,
    ANCESTOR_SELECTED,
    ANCESTOR_RESTORED,
    ANCESTOR_DRAWER,
    NAV_RETURNED,
    NAV_BASELINE,
    CLOSED
  }

  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final String MISSING_ID = "missing:preview_fixture";
  private static final String MISSING_REASON = "Reference is not present in this complete index";
  private static final List<String> FIELD_NAMES =
      List.of("Enabled", "Fresh Objects", "Models", "Artwork");
  private static final List<FieldPath> FIELD_PATHS =
      List.of(
          FieldPath.ROOT.field("enabled"),
          FieldPath.ROOT.field("freshObjects"),
          MODELS,
          FieldPath.ROOT.field("artwork"));
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
  private final JsonArray unresolvedStates = new JsonArray();
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
  private static final List<String> ANCESTORS =
      List.of("Home", "Settings", "Appearance", "Profile selection");
  private final List<ClientDestination> upDestinations = new ArrayList<>();
  private final JsonArray ancestorVisits = new JsonArray();
  private int ancestorIndex;
  private int inspectionWidth, inspectionHeight, inspectionStep;
  private design.inherent.reform.operation.DocumentReview inspectionCapture;
  private final JsonArray inspectionCycle = new JsonArray();
  private Screen ancestorMenu;
  private String locationNarration;
  private JsonObject ancestorVisit;
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
    result.add("unresolvedStates", unresolvedStates);
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
        box("Reference ID").setValue(MISSING_ID);
        press("Inspect");
        next(Phase.MISSING);
      }
      case MISSING -> {
        if (!browser.getNarrationMessage().getString().contains(MISSING_REASON)) return null;
        if (!UiProbe.hasButton(browser, "Hide source details")) press("Source details");
        var key = box("Reference ID");
        if (!UiProbe.focusControl(browser, key))
          throw new IllegalStateException("Cannot focus unresolved manual ID");
        key.setCursorPosition(3);
        key.setHighlightPos(1);
        assertUnresolvedRetained();
        unresolvedState(mc, "missing");
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.MISSING_NARROW);
      }
      case MISSING_NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || browser.width == browserWidth && browser.height == browserHeight) return null;
        assertUnresolvedRetained();
        unresolvedState(mc, "narrow");
        next(Phase.MISSING_NARROW_IMAGE);
      }
      case MISSING_NARROW_IMAGE -> {
        if (!capture(mc, "production-chooser-missing-narrow")) return null;
        assertUnresolvedRetained();
        NativeBindings.resizeWindow(mc, window[0], window[1]);
        next(Phase.MISSING_RETURNED);
      }
      case MISSING_RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), window)
            || browser.width != browserWidth
            || browser.height != browserHeight) return null;
        assertUnresolvedRetained();
        unresolvedState(mc, "returned");
        next(Phase.MISSING_RETURNED_IMAGE);
      }
      case MISSING_RETURNED_IMAGE -> {
        if (!capture(mc, "production-chooser-missing-returned")) return null;
        assertUnresolvedRetained();
        result.addProperty("unresolvedManualIdSurvivesWindowReflow", true);
        press("Back");
        next(Phase.BACK);
      }
      case BACK -> {
        if (NativeUi.current() != root || !editor.canSubmit()) return null;
        unchangedDraft();
        NativeBindings.press(UiProbe.button(root, "Review changes…"));
        next(Phase.REVIEW);
      }
      case REVIEW -> {
        var review = workflow.inspection().orElse(null);
        if (review == null || review.reviewedInput().isEmpty()) return null;
        if (inspectionCapture == null) inspectionCapture = review.reviewedInput().orElseThrow();
        assertInspection();
        if (!review.selectedField().equals(java.util.Optional.of(FIELD_PATHS.getFirst()))) {
          var index =
              UiProbe.children(root).stream()
                  .filter(Button.class::isInstance)
                  .map(Button.class::cast)
                  .filter(
                      value ->
                          !(value
                              instanceof
                              design.inherent.reform.minecraft.client.NativeNavigationButton))
                  .filter(value -> value.getMessage().getString().equals(FIELD_NAMES.getFirst()))
                  .findFirst();
          if (index.isPresent()) NativeBindings.press(index.orElseThrow());
          else advanceInspectionCycle();
          return null;
        }
        inspectionWidth = root.width;
        inspectionHeight = root.height;
        NativeBindings.resizeWindow(mc, 320, 240);
        next(Phase.REVIEW_NARROW);
      }
      case REVIEW_NARROW -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), new int[] {320, 240})
            || root.width == inspectionWidth && root.height == inspectionHeight) return null;
        assertInspection();
        next(Phase.REVIEW_CYCLE);
      }
      case REVIEW_CYCLE -> {
        assertInspection();
        int index = inspectionStep % FIELD_PATHS.size();
        if (!workflow
            .inspection()
            .orElseThrow()
            .selectedField()
            .equals(java.util.Optional.of(FIELD_PATHS.get(index)))) return null;
        var cycle = inspectionCycleControl();
        if (!cycle.getMessage().getString().equals(FIELD_NAMES.get(index)))
          throw new IllegalStateException("Focused inspection caption differs from selected field");
        inspectionCycle.add(cycle.getMessage().getString());
        if (inspectionStep++ == FIELD_PATHS.size()) {
          result.add("inspectionNativeFieldCycle", inspectionCycle);
          next(Phase.REVIEW_IMAGE);
        } else {
          advanceInspectionCycle();
          next(Phase.REVIEW_CYCLE);
        }
      }
      case REVIEW_IMAGE -> {
        if (!capture(mc, "production-inspection-narrow")) return null;
        assertInspection();
        NativeBindings.resizeWindow(mc, window[0], window[1]);
        next(Phase.REVIEW_RETURNED);
      }
      case REVIEW_RETURNED -> {
        if (!Arrays.equals(NativeBindings.windowSize(mc), window)
            || root.width != inspectionWidth
            || root.height != inspectionHeight) return null;
        assertInspection();
        if (!workflow
            .inspection()
            .orElseThrow()
            .selectedField()
            .equals(java.util.Optional.of(FIELD_PATHS.getFirst())))
          throw new IllegalStateException("Inspection reflow lost the selected native field");
        result.addProperty("inspectionSameCaptureAcrossWindowReflow", true);
        NativeBindings.press(UiProbe.button(root, "Back to settings"));
        next(Phase.REVIEW_BACK);
      }
      case REVIEW_BACK -> {
        if (workflow.inspection().isPresent() || !editor.canSubmit()) return null;
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
        upDestinations.add(navigationAttempt.target());
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
        var categories = UiProbe.button(root, "Categories");
        if (!categories.active
            || !UiProbe.focusControl(root, categories)
            || !NativeBindings.activateFocused(root))
          throw new IllegalStateException("Cannot open actual compact navigation drawer");
        navigationResult.addProperty("nativeCategoriesDrawerOpened", true);
        next(Phase.NAV_DRAWER);
      }
      case NAV_DRAWER -> {
        if (UiProbe.children(root).stream()
            .noneMatch(
                value ->
                    value instanceof EditBox input
                        && input.getMessage().getString().equals("Search settings"))) return null;
        // Activating Categories deliberately moved native focus. Retained search data must survive;
        // focus it explicitly before measuring the next wide-layout return.
        assertNavigation(false);
        navigationResult.addProperty(
            "queryFocusedAfterDrawerActivation", UiProbe.focusedPath(root, navigationQuery()));
        if (!UiProbe.focusControl(root, navigationQuery()))
          throw new IllegalStateException("Cannot focus search in the actual compact drawer");
        assertNavigation();
        navigationResult.addProperty("queryExplicitlyRefocusedInDrawer", true);
        navigationResult.addProperty("narrowGui", root.width + "x" + root.height);
        if (upDestinations.size() != ANCESTORS.size())
          throw new IllegalStateException("Declared ancestor hierarchy changed: " + upTargets);
        openLocationMenu();
      }
      case ANCESTOR_MENU -> {
        ancestorMenu = NativeUi.current();
        if (ancestorMenu == null
            || ancestorMenu == root
            || !ancestorMenu.getTitle().getString().equals("Location")) return null;
        var expectedOrder = new ArrayList<>(ANCESTORS);
        expectedOrder.add("Models");
        var observedOrder =
            UiProbe.children(ancestorMenu).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> expectedOrder.contains(button.getMessage().getString()))
                .sorted(
                    java.util.Comparator.comparingInt(Button::getY).thenComparingInt(Button::getX))
                .map(button -> button.getMessage().getString())
                .toList();
        if (!observedOrder.equals(expectedOrder))
          throw new IllegalStateException(
              "Native ancestor menu lost declared order: " + observedOrder);
        var name = ANCESTORS.get(ancestorIndex);
        var control = UiProbe.button(ancestorMenu, name);
        var narration = NativeBindings.narration(control);
        if (!control.active
            || !narration.contains(name)
            || !UiProbe.focusControl(ancestorMenu, control)
            || !NativeBindings.activateFocused(ancestorMenu))
          throw new IllegalStateException("Native ancestor keyboard activation failed: " + name);
        ancestorVisit = new JsonObject();
        ancestorVisit.addProperty("name", name);
        ancestorVisit.addProperty("narration", narration);
        ancestorVisit.addProperty("locationNarration", locationNarration);
        ancestorVisit.addProperty("expectedTarget", ancestorTarget().toString());
        next(Phase.ANCESTOR_SELECTED);
      }
      case ANCESTOR_SELECTED -> {
        if (NativeUi.current() != root
            || !workflow.navigation().current().filter(ancestorTarget()::equals).isPresent())
          return null;
        unchangedDraft();
        ancestorVisit.addProperty(
            "actualTarget", workflow.navigation().current().orElseThrow().toString());
        ancestorVisit.addProperty("sameRootOwnerAndDraft", true);
        ancestorVisits.add(ancestorVisit);
        navigationAttempt = workflow.navigation().restore(navigationBookmark);
        next(Phase.ANCESTOR_RESTORED);
      }
      case ANCESTOR_RESTORED -> {
        if (!navigationReady()) return null;
        var categories = UiProbe.button(root, "Categories");
        if (!categories.active
            || !UiProbe.focusControl(root, categories)
            || !NativeBindings.activateFocused(root))
          throw new IllegalStateException("Cannot reopen compact navigation after ancestor return");
        next(Phase.ANCESTOR_DRAWER);
      }
      case ANCESTOR_DRAWER -> {
        if (UiProbe.children(root).stream()
            .noneMatch(
                value ->
                    value instanceof EditBox input
                        && input.getMessage().getString().equals("Search settings"))) return null;
        assertNavigation(false);
        if (!UiProbe.focusControl(root, navigationQuery()))
          throw new IllegalStateException("Cannot refocus restored native navigation search");
        assertNavigation();
        if (++ancestorIndex < ANCESTORS.size()) openLocationMenu();
        else {
          navigationResult.add("ancestorKeyboardVisits", ancestorVisits);
          NativeBindings.resizeWindow(mc, window[0], window[1]);
          next(Phase.NAV_RETURNED);
        }
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

  private ClientDestination ancestorTarget() {
    return upDestinations.get(upDestinations.size() - 1 - ancestorIndex);
  }

  private void openLocationMenu() {
    var location =
        UiProbe.children(root).stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(
                button ->
                    button.getMessage().getString().equals("Models")
                        && NativeBindings.narration(button).startsWith("Location:"))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Missing actual compact Location control"));
    locationNarration = NativeBindings.narration(location);
    if (ANCESTORS.stream().anyMatch(name -> !locationNarration.contains(name))
        || !locationNarration.contains("Models")
        || !location.active
        || !UiProbe.focusControl(root, location)
        || !NativeBindings.activateFocused(root))
      throw new IllegalStateException(
          "Actual Location control lost its full path or keyboard action: " + locationNarration);
    next(Phase.ANCESTOR_MENU);
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
    assertNavigation(true);
  }

  private void assertNavigation(boolean requireSearchFocus) {
    var query = navigationQuery();
    var state = workflow.navigation().bookmark().navigation().orElseThrow();
    var expected = navigationBookmark.navigation().orElseThrow();
    if (!query.getValue().equals("Models")
        || query.getCursorPosition() != 3
        || !query.getHighlighted().equals("od")
        || requireSearchFocus && !UiProbe.focusedPath(root, query)
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

  private void assertUnresolvedRetained() {
    var query = box("Search");
    var key = box("Reference ID");
    String narration = browser.getNarrationMessage().getString();
    if (NativeUi.current() != browser
        || !query.getValue().equals(queryText)
        || query.getCursorPosition() != 3
        || !query.getHighlighted().equals(queryText.substring(1, 3))
        || !key.getValue().equals(MISSING_ID)
        || key.getCursorPosition() != 3
        || !key.getHighlighted().equals(MISSING_ID.substring(1, 3))
        || !UiProbe.focusedPath(browser, key)
        || UiProbe.button(browser, "Use selection").active
        || !UiProbe.hasButton(browser, "Hide source details")
        || !narration.contains("unknown reference")
        || !narration.contains(MISSING_REASON))
      throw new IllegalStateException(
          "Unresolved manual ID lost state or admission guard: "
              + observations()
              + "; "
              + narration);
    unchangedDraft();
  }

  private void assertInspection() {
    if (NativeUi.current() != root
        || !(workflow.navigation().current().orElse(null) instanceof ClientDestination.Inspection)
        || workflow.inspection().flatMap(value -> value.reviewedInput()).orElse(null)
            != inspectionCapture
        || !editor.form().fieldOrder().equals(FIELD_PATHS))
      throw new IllegalStateException("Inspection reflow replaced its actual root/capture/order");
    unchangedDraft();
  }

  private net.minecraft.client.gui.components.AbstractWidget inspectionCycleControl() {
    return UiProbe.children(root).stream()
        .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
        .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
        .filter(value -> NativeBindings.narration(value).startsWith("Field: "))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No mounted focused Field cycle"));
  }

  private void advanceInspectionCycle() {
    var cycle = inspectionCycleControl();
    if (!cycle.active
        || !UiProbe.focusControl(root, cycle)
        || !NativeBindings.activateFocused(root))
      throw new IllegalStateException(
          "Focused inspection cycle refused native keyboard activation");
  }

  private void unresolvedState(Minecraft mc, String phase) {
    var value = observations();
    var key = box("Reference ID");
    value.addProperty("phase", phase);
    var size = NativeBindings.windowSize(mc);
    value.addProperty("window", size[0] + "x" + size[1]);
    value.addProperty("gui", browser.width + "x" + browser.height);
    value.addProperty("manualCaret", key.getCursorPosition());
    value.addProperty("manualSelection", key.getHighlighted());
    value.addProperty("manualFocus", UiProbe.focusedPath(browser, key));
    value.addProperty("useSelectionActive", UiProbe.button(browser, "Use selection").active);
    value.addProperty("sourceDetailsOpen", UiProbe.hasButton(browser, "Hide source details"));
    value.addProperty("narration", browser.getNarrationMessage().getString());
    unresolvedStates.add(value);
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
    value.add("workspaceNavigation", navigationResult.deepCopy());
    if (workflow.isOpen() && workflow.navigation().current().isPresent())
      value.addProperty(
          "workspaceDestination", workflow.navigation().current().orElseThrow().toString());
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
      if (box.getMessage().getString().equals("Reference ID")) {
        value.addProperty("inspected", box.getValue());
        value.addProperty("manualCaret", box.getCursorPosition());
        value.addProperty("manualSelection", box.getHighlighted());
        value.addProperty("manualFocus", UiProbe.focusedPath(browser, box));
      }
    }
    return value;
  }
}
