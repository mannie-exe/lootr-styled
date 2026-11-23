package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StyleApplication;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StylePreviewCapture;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.ProfileCodec;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftEdit;
import design.inherent.reform.edit.DraftRevision;
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
  private static final int HOME_ENTRY = -10,
      PROFILE_TOOLS = -9,
      HOME_RETURN = -8,
      ENTER_SETTINGS = -7;
  private static final int HOME_DIRTY = 210, RETURN_DIRTY = 211;
  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final FieldPath ARTWORK = FieldPath.ROOT.field("artwork");
  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final byte[] originalBytes;
  private final ClientSettings original;
  private final Path settingsFile;
  private ClientEditor<?> editor;
  private DraftRevision homeRevision;
  private CompletableFuture<?> pending;
  private Set<Path> existing;
  private int stage = HOME_ENTRY;
  private int mountTicks;
  private boolean captured;
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
      case HOME_ENTRY -> {
        if (++mountTicks < 4) return null;
        if (workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::opened))
          throw new IllegalStateException("Home eagerly opened an appearance editor");
        NativeBindings.press(button(root, "Profiles"));
        mountTicks = 0;
        stage = PROFILE_TOOLS;
      }
      case PROFILE_TOOLS -> {
        if (++mountTicks < 4) return null;
        if (workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::opened))
          throw new IllegalStateException("Profile tools opened an appearance editor");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Profile tools changed settings without an action");
        result.addProperty("profileToolsLazy", true);
        NativeBindings.press(navigation(root, "Home"));
        mountTicks = 0;
        stage = HOME_RETURN;
      }
      case HOME_RETURN -> {
        if (++mountTicks < 4) return null;
        if (workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::opened))
          throw new IllegalStateException("Home eagerly opened an appearance editor");
        if (!root.getTitle().getString().equals("Lootr: Styled"))
          throw new IllegalStateException("The workspace lost its stable identity");
        button(root, "Settings");
        button(root, "Profiles");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Home changed settings");
        result.addProperty("lazyHome", true);
        captured = false;
        NativeWorld.screenshot(mc, "reform-home", () -> captured = true);
        stage = ENTER_SETTINGS;
      }
      case ENTER_SETTINGS -> {
        if (!captured) return null;
        NativeBindings.press(button(root, "Settings"));
        pending =
            workflow
                .openEditor(StyleSettingsStore.DEFINITION.id())
                .thenCompose(
                    opened -> {
                      editor = opened;
                      return DocumentActions.result(editor.actions().ensureLoaded(EntryPoint.GUI));
                    })
                .toCompletableFuture();
        captured = false;
        stage = -2;
      }
      case -2 -> {
        if (editor.form().fieldControl(MODELS).isEmpty()) return null;
        NativeBindings.press(navigation(root, "Appearance"));
        mountTicks = 0;
        stage = -1;
      }
      case -1 -> {
        if (++mountTicks < 4) return null;
        var names = List.of("Enabled", "Fresh Objects", "Models", "Artwork");
        var navigation = new com.google.gson.JsonArray();
        children(root).stream()
            .filter(
                design.inherent.reform.minecraft.client.NativeNavigationButton.class::isInstance)
            .map(design.inherent.reform.minecraft.client.NativeNavigationButton.class::cast)
            .filter(control -> names.contains(control.getMessage().getString()))
            .sorted(
                java.util.Comparator.comparingInt(AbstractWidget::getY)
                    .thenComparingInt(AbstractWidget::getX))
            .forEach(control -> navigation.add(control.getMessage().getString()));
        result.add("renderedNavigationOrder", navigation);
        var body = new com.google.gson.JsonArray();
        var rootPath = FieldPath.ROOT;
        var paths =
            List.of(rootPath.field("enabled"), rootPath.field("freshObjects"), MODELS, ARTWORK);
        paths.stream()
            .sorted(
                java.util.Comparator.comparingInt(
                        (FieldPath path) -> editor.form().fieldControl(path).orElseThrow().getY())
                    .thenComparingInt(
                        path -> editor.form().fieldControl(path).orElseThrow().getX()))
            .forEach(path -> body.add(names.get(paths.indexOf(path))));
        result.add("renderedBodyOrder", body);
        result.addProperty("navigationMatchesBody", navigation.equals(body));
        Files.writeString(
            mc.gameDirectory.toPath().resolve("lootr-styled-ui-observations.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        captured = false;
        NativeWorld.screenshot(mc, "reform-navigation", () -> captured = true);
        stage = -3;
      }
      case -3 -> {
        if (!captured) return null;
        mountTicks = 0;
        stage = 0;
      }
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
        if (++mountTicks < 4) return null;
        NativeWorld.screenshot(mc, "reform-profile-browser", () -> {});
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
        mountTicks = 0;
        stage = 1;
      }
      case 1 -> {
        assertModel("missing:kept");
        if (++mountTicks < 4) return null;
        if (!editor
            .form()
            .fieldControl(MODELS)
            .orElseThrow()
            .getMessage()
            .getString()
            .contains("missing:kept"))
          throw new IllegalStateException("Rendered profile title lost the current missing ID");
        recordGeometry("actualLayout");
        captured = false;
        NativeWorld.screenshot(mc, "reform-appearance", () -> captured = true);
        stage = 101;
      }
      case 101 -> {
        if (!captured) return null;
        NativeBindings.resize(root, mc, 320, 240);
        assertModel("missing:kept");
        recordGeometry("compactLayout");
        NativeBindings.resize(root, mc, 900, 540);
        recordGeometry("expandedLayout");
        NativeBindings.resize(root, mc, nativeWidth, nativeHeight);
        if (!editor.canSubmit())
          throw new IllegalStateException("Valid missing identity cannot be submitted");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Unsaved selection changed settings");
        var capture =
            StylePreviewCapture.capture(
                editor, EntryPoint.GUI, DraftRevision.of(editor.view().snapshot().orElseThrow()));
        pending =
            capture
                .completion()
                .thenAccept(
                    input -> {
                      if (!input.settings().models().toString().equals("missing:kept"))
                        throw new IllegalStateException(
                            "Preview capture lost the complete dirty model selection");
                      if (!StylePreviewCapture.resourceCurrent(input.resourceGeneration()))
                        throw new IllegalStateException(
                            "Preview capture returned retired resources");
                      try {
                        if (!java.util.Arrays.equals(
                            originalBytes, Files.readAllBytes(settingsFile)))
                          throw new IllegalStateException(
                              "Read-only preview capture wrote settings");
                      } catch (java.io.IOException error) {
                        throw new java.util.concurrent.CompletionException(error);
                      }
                      result.addProperty("actualReadPreviewCapture", true);
                      try {
                        result.add("isolatedChestPreparation", IsolatedChestProbe.inspect(input));
                      } catch (Exception failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                      }
                      result.addProperty(
                          "previewResourceGeneration", input.resourceGeneration().toString());
                      result.addProperty(
                          "previewNativeInputEpoch", input.input().stamp().nativeInputEpoch());
                    })
                .toCompletableFuture();
        stage = 201;
      }
      case 201 -> {
        NativeBindings.press(button(root, "Document actions…"));
        mountTicks = 0;
        stage = 102;
      }
      case 102 -> {
        if (!NativeUi.current().getClass().getSimpleName().equals("DocumentActionsScreen"))
          throw new IllegalStateException("Document actions did not open the shared auxiliary");
        if (++mountTicks < 4) return null;
        captured = false;
        NativeWorld.screenshot(mc, "reform-document-actions", () -> captured = true);
        stage = 103;
      }
      case 103 -> {
        if (!captured) return null;
        NativeBindings.press(button(NativeUi.current(), "Defaults"));
        mountTicks = 0;
        stage = 104;
      }
      case 104 -> {
        if (NativeUi.current() != root || workflow.documents().getFirst().busy()) return null;
        if (++mountTicks < 4) return null;
        assertModel(ClientSettings.DEFAULTS.models().toString());
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Document Defaults saved the draft");
        NativeBindings.press(button(root, "Document actions…"));
        mountTicks = 0;
        stage = 105;
      }
      case 105 -> {
        if (!NativeUi.current().getClass().getSimpleName().equals("DocumentActionsScreen")) {
          if (++mountTicks > 120)
            throw new IllegalStateException("Document actions did not reopen after Defaults");
          return null;
        }
        var undo = button(NativeUi.current(), "Undo");
        if (!undo.active)
          throw new IllegalStateException("Defaults lost the retained undo history");
        NativeBindings.press(undo);
        stage = 106;
      }
      case 106 -> {
        if (NativeUi.current() != root || workflow.documents().getFirst().busy()) return null;
        assertModel("missing:kept");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Document Undo saved the draft");
        result.addProperty("documentActionsDefaultsUndoSameDraft", true);
        pending =
            edit(
                FieldPath.ROOT,
                new DraftEdit.SetRecordScalars(Map.of("models", "bad id", "enabled", "false")));
        mountTicks = 0;
        stage = 2;
      }
      case 2 -> {
        if (++mountTicks < 4) return null;
        if (editor.canSubmit())
          throw new IllegalStateException("Invalid profile plus valid sibling permits submission");
        if (button(root, "Save and reload resources").active)
          throw new IllegalStateException("Combined action permits invalid input");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Rejected draft partially saved valid siblings");
        result.addProperty("invalidDraftAndSiblingPreserved", true);
        homeRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        NativeBindings.press(navigation(root, "Home"));
        mountTicks = 0;
        stage = HOME_DIRTY;
      }
      case HOME_DIRTY -> {
        if (++mountTicks < 4) return null;
        if (workflow.editor(StyleSettingsStore.DEFINITION) != editor
            || !homeRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
          throw new IllegalStateException("Home replaced the retained invalid draft");
        if (editor.canSubmit()
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Home submitted or discarded invalid input");
        NativeBindings.press(navigation(root, "Appearance"));
        mountTicks = 0;
        stage = RETURN_DIRTY;
      }
      case RETURN_DIRTY -> {
        if (++mountTicks < 4 || editor.form().fieldControl(MODELS).isEmpty()) return null;
        if (workflow.editor(StyleSettingsStore.DEFINITION) != editor
            || !homeRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
          throw new IllegalStateException("Returning to Appearance replaced the draft");
        result.addProperty("homeRetainsInvalidDraft", true);
        pending = invalidPreviewCapture();
        stage = 202;
      }
      case 202 -> {
        var manual =
            children(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(control -> control.getMessage().getString().equals("Edit ID"))
                .filter(control -> NativeBindings.narration(control).startsWith("Models:"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Models lost its manual-ID action"));
        NativeBindings.press(manual);
        mountTicks = 0;
        stage = 107;
      }
      case 107 -> {
        var input = editor.form().fieldControl(MODELS);
        if (input.isEmpty()
            || !(input.get() instanceof net.minecraft.client.gui.components.EditBox)) {
          if (++mountTicks > 120)
            throw new IllegalStateException("Edit ID did not expose native input");
          return null;
        }
        var raw = (net.minecraft.client.gui.components.EditBox) input.get();
        if (!raw.getValue().equals("bad id"))
          throw new IllegalStateException("Manual input lost the invalid retained value");
        mountTicks = 0;
        stage = 109;
      }
      case 109 -> {
        if (++mountTicks < 4) return null;
        NativeWorld.screenshot(mc, "reform-manual-id", () -> {});
        var raw =
            (net.minecraft.client.gui.components.EditBox)
                editor.form().fieldControl(MODELS).orElseThrow();
        raw.setValue("missing:kept");
        mountTicks = 0;
        stage = 108;
      }
      case 108 -> {
        if (!editor.canSubmit()) {
          if (++mountTicks > 120)
            throw new IllegalStateException("Repairing the native ID input did not enable saving");
          return null;
        }
        assertModel("missing:kept");
        result.addProperty("nativeManualIdRepair", true);
        stage = 3;
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

  private CompletableFuture<Void> invalidPreviewCapture() {
    var snapshot = editor.view().snapshot().orElseThrow();
    var allowed =
        editor.view().validation().issues().stream()
            .map(design.inherent.reform.validation.Issue::code)
            .collect(java.util.stream.Collectors.toSet());
    java.util.function.Consumer<Throwable> inspect =
        failed -> {
          while (failed instanceof java.util.concurrent.CompletionException
              && failed.getCause() != null) failed = failed.getCause();
          if (!(failed instanceof design.inherent.reform.owner.PolicyRefusal refusal))
            throw new IllegalStateException(
                "Preview failed for a reason other than input refusal", failed);
          boolean inputIssue =
              refusal.report().issues().stream()
                  .anyMatch(
                      issue ->
                          allowed.contains(issue.code())
                              || issue.path().equals(MODELS)
                              || issue.code().equals("preview.input_invalid")
                              || issue.code().equals("preview.input_unaccepted"));
          if (!inputIssue)
            throw new IllegalStateException(
                "Preview refusal did not identify invalid input", failed);
          var after = editor.view().snapshot().orElseThrow();
          if (snapshot.version() != after.version() || snapshot.generation() != after.generation())
            throw new IllegalStateException("Rejected preview capture changed the draft");
          try {
            if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
              throw new IllegalStateException("Rejected preview capture saved a valid sibling");
          } catch (java.io.IOException error) {
            throw new java.util.concurrent.CompletionException(error);
          }
          result.addProperty("invalidPreviewRefusedWithoutPartialSave", true);
          result.addProperty(
              "invalidPreviewRefusalCode", refusal.report().issues().getFirst().code());
        };
    try {
      var capture = StylePreviewCapture.capture(editor, EntryPoint.GUI, DraftRevision.of(snapshot));
      return capture
          .completion()
          .handle(
              (input, failed) -> {
                if (failed == null)
                  throw new IllegalStateException("Invalid draft produced a preview input");
                inspect.accept(failed);
                return (Void) null;
              })
          .toCompletableFuture();
    } catch (design.inherent.reform.owner.PolicyRefusal refused) {
      inspect.accept(refused);
      return CompletableFuture.completedFuture(null);
    }
  }

  private void assertModel(String id) {
    var inspected = editor.view().snapshot().orElseThrow().inspect();
    var settings = ClientSettings.class.cast(inspected.candidate().orElseThrow().value());
    if (!settings.models().toString().equals(id))
      throw new IllegalStateException("Retained draft lost profile identity");
  }

  private void recordGeometry(String key) throws java.io.IOException {
    var layout = new JsonObject();
    for (var path : List.of(MODELS, ARTWORK)) {
      var control = editor.form().fieldControl(path).orElseThrow();
      var bounds = new JsonObject();
      bounds.addProperty("x", control.getX());
      bounds.addProperty("y", control.getY());
      bounds.addProperty("width", control.getWidth());
      bounds.addProperty("height", control.getHeight());
      bounds.addProperty("title", control.getMessage().getString());
      bounds.addProperty("narration", NativeBindings.narration(control));
      layout.add(path.toString(), bounds);
    }
    result.add(key, layout);
    Files.writeString(
        Minecraft.getInstance().gameDirectory.toPath().resolve("lootr-styled-ui-observations.json"),
        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
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

  private static design.inherent.reform.minecraft.client.NativeNavigationButton navigation(
      Screen screen, String label) {
    return children(screen).stream()
        .filter(design.inherent.reform.minecraft.client.NativeNavigationButton.class::isInstance)
        .map(design.inherent.reform.minecraft.client.NativeNavigationButton.class::cast)
        .filter(control -> control.getMessage().getString().equals(label))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing navigation destination: " + label));
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
