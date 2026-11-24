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
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
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
import net.minecraft.locale.Language;

/** Actual language/font reload and restoration beside a complete invalid native draft. */
final class ProductionLocaleProbe {
  private enum Phase {
    OPEN,
    SIBLING,
    FIELD,
    MANUAL,
    INVALID,
    CHANGED,
    CHANGED_IMAGE,
    RESTORED,
    RESTORED_IMAGE,
    REPAIR,
    REPAIRED,
    CLOSED
  }

  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final FieldPath ENABLED = FieldPath.ROOT.field("enabled");
  private final Screen parent, root;
  private final ClientEditingWorkflow workflow;
  private final CompletableFuture<?> admission;
  private final Path settings;
  private final byte[] originalBytes;
  private final BasicFileAttributes attributes;
  private final ClientSettings original;
  private final String originalLanguage, otherLanguage;
  private final boolean originalUnicode;
  private final JsonObject result = new JsonObject();
  private final JsonArray states = new JsonArray();
  private ClientEditor<ClientSettings> editor;
  private CompletableFuture<?> pending;
  private DraftRevision revision;
  private JsonObject invalidDraft;
  private Language previousLanguage;
  private UUID previousGeneration;
  private Phase phase = Phase.OPEN;
  private int ticks, captureTicks;
  private boolean captureRequested, captured;

  ProductionLocaleProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    originalBytes = Files.readAllBytes(settings);
    attributes = Files.readAttributes(settings, BasicFileAttributes.class);
    original = ClientSettings.parse(originalBytes);
    originalLanguage = mc.getLanguageManager().getSelected();
    otherLanguage = originalLanguage.equals("de_de") ? "en_us" : "de_de";
    originalUnicode = mc.options.forceUnicodeFont().get();
    workflow = StylePreferences.open(parent);
    root = workflow.screen();
    NativeUi.open(root);
    admission =
        WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, ENABLED)
            .thenAccept(value -> editor = value);
    result.add("states", states);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 600)
      throw new IllegalStateException("Locale input timed out: " + phase + ": " + observations(mc));
    if (!admission.isDone()) return null;
    admission.join();
    unchanged();
    switch (phase) {
      case OPEN -> {
        if (editor.form().fieldControl(ENABLED).isEmpty() || !editor.canSubmit()) return null;
        result.add("baselineWholeDraft", draft());
        toggle();
        next(Phase.SIBLING);
      }
      case SIBLING -> {
        if (!editor.canSubmit() || raw(ENABLED).equals(Boolean.toString(original.enabled())))
          return null;
        pending = WorkflowReveal.field(workflow, StyleSettingsStore.DEFINITION, MODELS);
        next(Phase.FIELD);
      }
      case FIELD -> {
        if (!ready()) return null;
        var disclosure = UiProbe.manualIdButton(root, "Edit ID");
        if (!disclosure.active) return null;
        NativeBindings.press(disclosure);
        next(Phase.MANUAL);
      }
      case MANUAL -> {
        if (!(editor.form().fieldControl(MODELS).orElse(null) instanceof EditBox input)
            || !input.active) return null;
        if (!UiProbe.focusControl(root, input))
          throw new IllegalStateException("Cannot focus actual Models input");
        input.setValue("bad id");
        next(Phase.INVALID);
      }
      case INVALID -> {
        if (editor.canSubmit() || busy() || !raw(MODELS).equals("bad id")) return null;
        var input = input();
        input.setCursorPosition(3);
        input.setHighlightPos(1);
        revision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        invalidDraft = draft();
        result.add("invalidWholeDraft", invalidDraft.deepCopy());
        retained();
        state(mc, "original");
        reload(mc, otherLanguage, !originalUnicode);
        next(Phase.CHANGED);
      }
      case CHANGED -> {
        if (!reloaded(mc, otherLanguage, !originalUnicode)) return null;
        retained();
        state(mc, "changed");
        next(Phase.CHANGED_IMAGE);
      }
      case CHANGED_IMAGE -> {
        if (!capture(mc, "production-locale-changed")) return null;
        retained();
        reload(mc, originalLanguage, originalUnicode);
        next(Phase.RESTORED);
      }
      case RESTORED -> {
        if (!reloaded(mc, originalLanguage, originalUnicode)) return null;
        retained();
        state(mc, "restored");
        result.addProperty("localeRoundTripRetainsWholeDraft", true);
        result.addProperty("caretSelectionFocusAndSaveGuardRetained", true);
        result.addProperty("originalLocaleRestored", true);
        next(Phase.RESTORED_IMAGE);
      }
      case RESTORED_IMAGE -> {
        if (!capture(mc, "production-locale-restored")) return null;
        retained();
        input().setValue(original.models().toString());
        next(Phase.REPAIR);
      }
      case REPAIR -> {
        if (!editor.canSubmit() || busy() || !raw(MODELS).equals(original.models().toString()))
          return null;
        toggle();
        next(Phase.REPAIRED);
      }
      case REPAIRED -> {
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
        result.add("restoredWholeDraft", draft());
        var done = UiProbe.button(root, "Done");
        if (!done.active) return null;
        NativeBindings.press(done);
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
        result.addProperty("sameOwnerUntilClose", true);
        result.addProperty("closedCleanly", true);
        return result;
      }
    }
    return null;
  }

  private void reload(Minecraft mc, String language, boolean unicode) throws Exception {
    previousLanguage = Language.getInstance();
    previousGeneration = generation();
    mc.getLanguageManager().setSelected(language);
    mc.options.languageCode = language;
    mc.options.forceUnicodeFont().set(unicode);
    pending = mc.reloadResourcePacks();
  }

  private boolean reloaded(Minecraft mc, String language, boolean unicode) throws Exception {
    if (!pending.isDone()) return false;
    pending.join();
    if (!NativeBindings.unobscured(mc)
        || Language.getInstance() == previousLanguage
        || previousGeneration.equals(generation())) return false;
    if (!mc.getLanguageManager().getSelected().equals(language)
        || !mc.options.languageCode.equals(language)
        || mc.options.forceUnicodeFont().get() != unicode)
      throw new IllegalStateException("Native language/font selection was not applied");
    return true;
  }

  private EditBox input() {
    return (EditBox) editor.form().fieldControl(MODELS).orElseThrow();
  }

  private void retained() {
    var input = input();
    if (NativeUi.current() != root
        || !input.getValue().equals("bad id")
        || input.getCursorPosition() != 3
        || !input.getHighlighted().equals("ad")
        || !UiProbe.focusedPath(root, input)
        || editor.canSubmit()
        || UiProbe.button(root, "Save settings").active
        || !revision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
        || !invalidDraft.equals(draft()))
      throw new IllegalStateException("Locale reload lost retained native input/draft/focus");
  }

  private void unchanged() throws Exception {
    var now = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(originalBytes, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !java.util.Objects.equals(attributes.fileKey(), now.fileKey())
        || workflow.isOpen() && workflow.editor(StyleSettingsStore.DEFINITION) != editor)
      throw new IllegalStateException("Locale check changed settings or owner");
  }

  private boolean busy() {
    return workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::busy);
  }

  private boolean ready() {
    if (!pending.isDone()) return false;
    pending.join();
    return !busy();
  }

  private void toggle() {
    var control =
        UiProbe.children(root).stream()
            .filter(CycleButton.class::isInstance)
            .map(CycleButton.class::cast)
            .filter(value -> NativeBindings.narration(value).startsWith("Enabled: "))
            .findFirst()
            .orElseThrow();
    if (!control.active) throw new IllegalStateException("Enabled control inactive");
    NativeBindings.press(control);
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

  private static UUID generation() throws Exception {
    return PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
  }

  private void state(Minecraft mc, String name) throws Exception {
    var value = observations(mc);
    value.addProperty("phase", name);
    value.addProperty("generation", generation().toString());
    states.add(value);
  }

  JsonObject observations(Minecraft mc) {
    var value = new JsonObject();
    value.addProperty("language", mc.getLanguageManager().getSelected());
    value.addProperty("unicode", mc.options.forceUnicodeFont().get());
    value.addProperty("doneLabel", Language.getInstance().getOrDefault("gui.done"));
    var widths = new JsonArray();
    for (String sample : new String[] {"iiiiiiii", "WWWWWWWW", "MWil 0123", "ä中"})
      widths.add(mc.font.width(sample));
    value.add("fontWidths", widths);
    if (editor == null || !editor.isOpen()) return value;
    value.add("draft", draft());
    value.addProperty("canSubmit", editor.canSubmit());
    editor
        .form()
        .fieldControl(MODELS)
        .ifPresent(
            control -> {
              value.addProperty("modelFocus", UiProbe.focusedPath(root, control));
              if (control instanceof EditBox box) {
                value.addProperty("text", box.getValue());
                value.addProperty("caret", box.getCursorPosition());
                value.addProperty("selection", box.getHighlighted());
              }
            });
    return value;
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
