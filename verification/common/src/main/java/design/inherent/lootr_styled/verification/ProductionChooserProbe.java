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
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
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
