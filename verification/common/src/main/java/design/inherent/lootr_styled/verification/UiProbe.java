package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StyleApplication;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.ProfileCodec;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftEdit;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.OperationScreen;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Real packaged public forms, retained drafts, persistence, native application and artwork export.
 */
final class UiProbe {
  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final FieldPath ARTWORK = FieldPath.ROOT.field("artwork");
  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final byte[] originalBytes;
  private final ClientSettings original;
  private final Path settingsFile;
  private ClientEditor<?> editor;
  private CompletableFuture<?> pending;
  private Set<Path> existing;
  private int stage;
  private int mountTicks;
  private int nativeWidth, nativeHeight;
  private final JsonObject result = new JsonObject();
  private final JsonObject timings = new JsonObject();
  private final long openedAt = System.nanoTime();
  private long actionAt, searchAt, exportAt;

  UiProbe() throws Exception {
    result.add("timingsMs", timings);
    result.addProperty(
        "timingScope",
        "Frame-polled end-to-end latency at fixture30FPS; includes dispatch, rendering and filesystem work, not pure CPU time");
    parent = NativeUi.current();
    settingsFile = ClientRuntime.configuration().root().resolve("settings.json");
    originalBytes = Files.readAllBytes(settingsFile);
    original = ClientSettings.parse(originalBytes);
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    workflow.show(StyleSettingsStore.DEFINITION.id());
    pending =
        workflow
            .openEditor(StyleSettingsStore.DEFINITION.id())
            .thenCompose(
                opened -> {
                  editor = opened;
                  return DocumentActions.result(editor.actions().ensureLoaded(EntryPoint.GUI));
                })
            .toCompletableFuture();
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (pending != null) {
      if (!pending.isDone()) return null;
      pending.join();
      pending = null;
      return null;
    }
    Screen root = workflow.screen();
    switch (stage) {
      case 0 -> {
        if (editor.form().fieldControl(MODELS).isEmpty()
            || editor.form().fieldControl(ARTWORK).isEmpty()) {
          if (++mountTicks < 120) return null;
          NativeWorld.screenshot(mc, "reform-unmounted", () -> {});
          throw new IllegalStateException(
              "Appearance controls did not mount in the shared workflow");
        }
        var models = editor.form().fieldControl(MODELS).orElseThrow();
        timings.addProperty("open", elapsed(openedAt));
        result.addProperty("catalogueChoices", ClientRuntime.choices().size());
        result.addProperty("guiScaleOption", mc.options.guiScale().get());
        result.addProperty("guiScaleFactor", mc.getWindow().getGuiScale());
        result.addProperty("nativeViewport", root.width + "x" + root.height);
        nativeWidth = root.width;
        nativeHeight = root.height;
        editor.form().fieldControl(ARTWORK).orElseThrow();
        String title =
            ClientRuntime.choices().stream()
                .filter(c -> c.id().equals(original.models()))
                .map(c -> c.title())
                .findFirst()
                .orElse(original.models().toString());
        if (!models.getMessage().getString().contains(title))
          throw new IllegalStateException(
              "Profile control did not show its title: " + models.getMessage().getString());
        NativeBindings.resize(root, mc, 320, 240);
        if (!editor.form().focusField(MODELS))
          throw new IllegalStateException("Keyboard focus did not reach Models");
        viewport(root, 320, 240);
        NativeBindings.resize(root, mc, 900, 540);
        NativeBindings.resize(root, mc, nativeWidth, nativeHeight);
        result.addProperty("profileTitles", true);
        result.addProperty("independentProfiles", 2);
        result.addProperty("viewportAndFocus", "320x240 and900x540");
        if (!editor.form().focusField(MODELS) || !NativeBindings.activateFocused(root))
          throw new IllegalStateException("Keyboard activation did not reach the profile control");
        result.addProperty("nativeKeyboardActivation", true);
        stage = 90;
      }
      case 90 -> {
        var browser = NativeUi.current();
        if (!browser.getClass().getSimpleName().equals("ReferenceScreen"))
          throw new IllegalStateException("Profile title did not open the shared source browser");
        var filter =
            children(browser).stream()
                .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .filter(box -> box.getValue().isEmpty())
                .findFirst()
                .orElseThrow();
        filter.setValue("Vanilla");
        searchAt = System.nanoTime();
        NativeBindings.press(button(browser, "Search"));
        mountTicks = 0;
        stage = 91;
      }
      case 91 -> {
        var browser = NativeUi.current();
        var choice =
            children(browser).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(
                    button ->
                        NativeBindings.narration(button).startsWith(ClientSettings.VANILLA + ": "))
                .findFirst();
        if (choice.isEmpty() || !choice.orElseThrow().active) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Profile source search did not return Vanilla");
          return null;
        }
        NativeBindings.press(choice.orElseThrow());
        timings.addProperty("search", elapsed(searchAt));
        stage = 92;
      }
      case 92 -> {
        var choose = button(NativeUi.current(), "Use selection");
        if (!choose.active) return null;
        NativeBindings.press(choose);
        stage = 93;
      }
      case 93 -> {
        if (NativeUi.current() != root) return null;
        assertModel(ClientSettings.VANILLA.toString());
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Browsing and choosing a profile saved settings");
        result.addProperty("sourceBrowserSearchAndChoice", true);
        pending = edit(MODELS, new DraftEdit.SetRaw("missing:kept"));
        stage = 1;
      }
      case 1 -> {
        assertModel("missing:kept");
        NativeWorld.screenshot(mc, "reform-appearance", () -> {});
        NativeBindings.resize(root, mc, 320, 240);
        assertModel("missing:kept");
        NativeBindings.resize(root, mc, 900, 540);
        NativeBindings.resize(root, mc, nativeWidth, nativeHeight);
        if (!editor.canSubmit())
          throw new IllegalStateException("Valid missing identity cannot be submitted");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Unsaved selection changed settings");
        pending =
            edit(
                FieldPath.ROOT,
                new DraftEdit.SetRecordScalars(Map.of("models", "bad id", "enabled", "false")));
        stage++;
      }
      case 2 -> {
        if (editor.canSubmit())
          throw new IllegalStateException("Invalid profile plus valid sibling permits submission");
        if (button(root, "Save and reload resources").active)
          throw new IllegalStateException("Combined action permits invalid input");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Rejected draft partially saved valid siblings");
        pending = edit(MODELS, new DraftEdit.SetRaw("missing:kept"));
        result.addProperty("invalidDraftAndSiblingPreserved", true);
        stage++;
      }
      case 3 -> {
        if (!editor.canSubmit()) return null;
        actionAt = System.nanoTime();
        NativeBindings.press(button(root, "Save and reload resources"));
        stage++;
      }
      case 4 -> {
        var saved = ClientSettings.parse(Files.readAllBytes(settingsFile));
        if (saved.enabled()
            || !saved.models().toString().equals("missing:kept")
            || !applied()
            || !operationDone()) return null;
        timings.addProperty("firstSaveAndReload", elapsed(actionAt));
        if (!(NativeUi.current() instanceof OperationScreen<?>))
          throw new IllegalStateException("Combined action did not use the shared result screen");
        NativeUi.current().onClose();
        if (NativeUi.current() != root)
          throw new IllegalStateException("Result Back lost the retained workflow");
        pending =
            edit(
                FieldPath.ROOT,
                new DraftEdit.SetRecordScalars(
                    Map.of(
                        "enabled",
                        Boolean.toString(original.enabled()),
                        "models",
                        original.models().toString(),
                        "artwork",
                        original.artwork().toString(),
                        "freshObjects",
                        original.fresh().name())));
        result.addProperty("disabledAndMissingIdentitySavedThenApplied", true);
        stage++;
      }
      case 5 -> {
        if (!editor.canSubmit()) return null;
        actionAt = System.nanoTime();
        NativeBindings.press(button(root, "Save and reload resources"));
        stage++;
      }
      case 6 -> {
        var saved = ClientSettings.parse(Files.readAllBytes(settingsFile));
        if (!saved.equals(original) || !applied() || !operationDone()) return null;
        timings.addProperty("repeatedSaveAndReload", elapsed(actionAt));
        NativeUi.current().onClose();
        try (var files = Files.list(ClientRuntime.configuration().root())) {
          existing = files.collect(java.util.stream.Collectors.toSet());
        }
        NativeBindings.press(button(root, "Profiles"));
        stage = 60;
      }
      case 60 -> {
        exportAt = System.nanoTime();
        NativeBindings.press(button(root, "Export artwork"));
        stage = 7;
      }
      case 7 -> {
        if (!operationDone()) return null;
        Path copy;
        try (var files = Files.list(ClientRuntime.configuration().root())) {
          var added =
              files
                  .filter(
                      path ->
                          !existing.contains(path)
                              && Files.isRegularFile(path.resolve("profile.json")))
                  .toList();
          if (added.isEmpty()) return null;
          if (added.size() != 1)
            throw new IllegalStateException("Export created multiple profiles");
          copy = added.getFirst();
        }
        var profile =
            ProfileCodec.parse(
                ResourceId.parse("lootr_styled_local:" + copy.getFileName()),
                Files.readAllBytes(copy.resolve("profile.json")));
        for (var target : ClientRuntime.targets().artwork()) {
          if (!target.equals(profile.artwork().get(target)))
            throw new IllegalStateException("Export lost native artwork mapping");
          byte[] current = NativeBindings.resource(mc, target.toString());
          byte[] captured =
              Files.readAllBytes(
                  copy.resolve(Path.of("assets", target.namespace(), target.path())));
          if (!java.util.Arrays.equals(current, captured))
            throw new IllegalStateException("Export differs from active native artwork");
        }
        if (!Files.isRegularFile(copy.resolve("SOURCES.json")))
          throw new IllegalStateException("Export lost provenance");
        if (!(NativeUi.current() instanceof OperationScreen<?>))
          throw new IllegalStateException("Export did not use shared operation UI");
        NativeUi.current().onClose();
        if (NativeUi.current() != root) throw new IllegalStateException("Export lost parent/draft");
        if (!ClientSettings.parse(Files.readAllBytes(settingsFile)).equals(original))
          throw new IllegalStateException("Export changed settings");
        workflow.close();
        NativeUi.open(parent);
        result.addProperty("resourceApplicationConfirmed", true);
        result.addProperty("activeArtworkPngsCopied", ClientRuntime.targets().artwork().size());
        result.addProperty("extraBarrelBindings", profile.models().size());
        result.addProperty("settingsRestored", true);
        result.addProperty("sharedSettingsAndOperationViews", true);
        timings.addProperty("artworkExport", elapsed(exportAt));
        stage++;
        return result;
      }
      default -> throw new IllegalStateException("Unexpected native UI probe stage");
    }
    return null;
  }

  private boolean applied() throws Exception {
    var current = ClientRuntime.settingsStore().read();
    var result = StylePreferences.resourceStatus();
    return result.state() == StyleApplication.State.APPLIED
        && result.sourceRevision().equals(java.util.Optional.of(current.revision()));
  }

  private static boolean operationDone() {
    return NativeUi.current() instanceof OperationScreen<?> screen
        && screen
            .observedOperation()
            .filter(
                state ->
                    state.state()
                        == design.inherent.reform.operation.OperationSnapshot.State.COMPLETED)
            .isPresent();
  }

  private static double elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000.0;
  }

  String progress() {
    return "ui=" + stage + (pending == null ? "" : ",pending=" + !pending.isDone());
  }

  private CompletableFuture<?> edit(FieldPath path, DraftEdit.Action action) {
    var snapshot = editor.view().snapshot().orElseThrow();
    return DocumentActions.result(
            editor.actions().edit(EntryPoint.GUI, new DraftEdit(snapshot.target(path), action)))
        .toCompletableFuture();
  }

  private void assertModel(String id) {
    var inspected = editor.view().snapshot().orElseThrow().inspect();
    var settings = ClientSettings.class.cast(inspected.candidate().orElseThrow().value());
    if (!settings.models().toString().equals(id))
      throw new IllegalStateException("Retained draft lost profile identity");
  }

  private static List<GuiEventListener> children(Screen screen) {
    var output = new ArrayList<GuiEventListener>();
    var pending = new ArrayList<GuiEventListener>(screen.children());
    var seen =
        java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<GuiEventListener, Boolean>());
    while (!pending.isEmpty()) {
      var node = pending.removeLast();
      if (!seen.add(node)) continue;
      output.add(node);
      if (node instanceof ContainerEventHandler container) pending.addAll(container.children());
    }
    return output;
  }

  private static Button button(Screen screen, String label) {
    return children(screen).stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(
            button ->
                button.getMessage().getString().equals(label)
                    || NativeBindings.narration(button).startsWith(label + ": "))
        .sorted(
            java.util.Comparator.comparingInt(
                button ->
                    button.getMessage().getString().equals("Run again…")
                        ? 0
                        : button.getMessage().getString().equals("View operation") ? 2 : 1))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing shared action: " + label));
  }

  private static void viewport(Screen screen, int width, int height) {
    for (var child : screen.children())
      if (child instanceof AbstractWidget widget
          && widget.visible
          && (widget.getX() < 0
              || widget.getY() < 0
              || widget.getX() + widget.getWidth() > width
              || widget.getY() + widget.getHeight() > height))
        throw new IllegalStateException("Compact widget escaped viewport");
  }
}
