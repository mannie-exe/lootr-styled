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
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.NavigationAttempt;
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
      STYLING_SECTION = -12,
      PROFILE_SECTION = -11,
      PROFILE_TOOLS = -9,
      PROFILE_INSPECT = -13,
      PROFILE_MODELS = -14,
      PROFILE_ARTWORK = -15,
      PROFILE_RESTORE = -16,
      PROFILE_RESTORE_ARTWORK = -17,
      PROFILE_PREVIEW_BEFORE = -18,
      PROFILE_PREVIEW_DRAW = -20,
      PROFILE_PREVIEW_CAPTURE = -21,
      PROFILE_SUBJECT_RETIRED = -22,
      PROFILE_VARIANT_PREPARE = -23,
      PROFILE_VARIANT_DRAW = -24,
      PROFILE_VARIANT_RETIRED = -25,
      PROFILE_PREVIEW_REVEAL = -26,
      PROFILE_REOPEN = -27,
      PROFILE_REOPEN_INSPECT = -28,
      PROFILE_REOPEN_CHOOSE = -29,
      PROFILE_REOPEN_RESTORE = -30,
      HOME_RETURN = -8,
      ENTER_SETTINGS = -7;
  private static final int HOME_DIRTY = 210, RETURN_DIRTY = 211;
  private static final int INSPECT_SAVED_RESULT = 212;
  private static final int REVIEW_DRAFT = 213,
      REVIEW_CAPTURE = 214,
      REVIEW_BACK = 215,
      REVIEW_RETURNED = 216,
      REVIEW_FINAL_BACK = 217,
      RESULT_BACK = 218,
      RESULT_FORWARD = 219,
      RESULT_INSPECT_LINK = 220,
      RESULT_RESTORE = 221,
      RESTORE_RESULT_BACK = 222,
      MENU_RETIRED = 223,
      REVIEW_FRESH = 224,
      OPERATION_HEADER_WAIT = 225,
      OPERATION_HEADER_CAPTURED = 226;
  private static final FieldPath MODELS = FieldPath.ROOT.field("models");
  private static final FieldPath ARTWORK = FieldPath.ROOT.field("artwork");
  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final byte[] originalBytes;
  private final ClientSettings original;
  private final Path settingsFile;
  private final java.nio.file.attribute.BasicFileAttributes homeFileAttributes;
  private final java.util.UUID homeResourceGeneration;
  private final long homePreparations = UiCostAudit.preparations();
  private final long homeDiscoveries = UiCostAudit.discoveries();
  private ClientEditor<?> editor;
  private ClientEditor<?> profileOwner;
  private design.inherent.reform.minecraft.client.ClientGraphicHost profileHost;
  private DraftRevision profileRevision;
  private DraftRevision homeRevision;
  private DraftRevision sectionRevision;
  private DraftRevision manualRevision;
  private Object sectionActions;
  private CompletableFuture<?> pending;
  private Set<Path> existing;
  private int stage = HOME_ENTRY;
  private int mountTicks;
  private int freshCaptionStep;
  private boolean freshCaptionsDone;
  private ClientSettings freshCaptionBaseline;
  private java.util.UUID freshCaptionGeneration;
  private final com.google.gson.JsonArray freshCaptionRows = new com.google.gson.JsonArray();
  private boolean captured;
  private int nativeWidth, nativeHeight;
  private final JsonObject result = new JsonObject();
  private final JsonObject timings = new JsonObject();
  private final com.google.gson.JsonArray headerOffers = new com.google.gson.JsonArray();
  private final Set<String> measuredHeaderPhases = new java.util.HashSet<>();
  private int[] headerBaseline;
  private String headerIdentity;
  private final long openedAt = System.nanoTime();
  private long actionAt, searchAt, exportAt;
  private long profileDraws;
  private long operationHeaderDraws;
  private java.util.UUID inspectedOperation, inspectedGeneration;
  private byte[] inspectedSavedBytes;
  private DraftRevision reviewRevision;
  private byte[] reviewSavedBytes;
  private java.util.UUID reviewGeneration;
  private design.inherent.reform.operation.DocumentReview retainedReview;
  private ClientDestination operationOrigin, operationDestination, documentMenuOrigin;
  private Object documentMenuActions;
  private DraftRevision documentMenuRevision, menuUndoRevision;
  private java.util.UUID documentMenuGeneration;
  private Button retiredDefaults;
  private int profileVariant = 1;
  private final com.google.gson.JsonArray profileVariants = new com.google.gson.JsonArray();
  private static final List<String> PROFILE_SUBJECTS =
      List.of(
          "barrel/unlooted/closed",
          "barrel/unlooted/open",
          "barrel/looted/closed",
          "barrel/looted/open",
          "chest/unlooted/closed",
          "chest/unlooted/open",
          "chest/looted/closed",
          "chest/looted/open",
          "trapped-chest/unlooted/closed",
          "trapped-chest/unlooted/open",
          "trapped-chest/looted/closed",
          "trapped-chest/looted/open");

  UiProbe() throws Exception {
    result.add("timingsMs", timings);
    result.add("headerOffers", headerOffers);
    result.addProperty(
        "timingScope",
        "Frame-polled end-to-end latency at fixture30FPS; includes dispatch, rendering and filesystem work, not pure CPU time");
    parent = NativeUi.current();
    settingsFile = ClientRuntime.configuration().root().resolve("settings.json");
    originalBytes = Files.readAllBytes(settingsFile);
    original = ClientSettings.parse(originalBytes);
    homeFileAttributes =
        Files.readAttributes(settingsFile, java.nio.file.attribute.BasicFileAttributes.class);
    homeResourceGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    UiCostAudit.watch(workflow.screen(), "unopened-home");
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (pending != null) {
      if (!pending.isDone()) return null;
      pending.join();
      pending = null;
      return null;
    }
    Screen root = workflow.screen();
    if (stage >= REVIEW_DRAFT || stage == INSPECT_SAVED_RESULT || stage >= 3 && stage <= 7)
      observeResultNavigation(root);
    switch (stage) {
      case HOME_ENTRY -> {
        if (UiCostAudit.samples() < 8 || !NativeBindings.unobscured(mc)) return null;
        if (workflow.documents().stream().anyMatch(ClientEditingWorkflow.DocumentState::opened))
          throw new IllegalStateException("Home eagerly opened an appearance editor");
        var home = UiCostAudit.finish();
        home.addProperty("startupCatalogueDiscoveries", homeDiscoveries);
        home.addProperty(
            "catalogueDiscoveriesDuringHome", UiCostAudit.discoveries() - homeDiscoveries);
        home.addProperty(
            "providerEntriesDuringHome", UiCostAudit.preparations() - homePreparations);
        home.addProperty(
            "openedDocuments",
            workflow.documents().stream()
                .filter(ClientEditingWorkflow.DocumentState::opened)
                .count());
        var now =
            Files.readAttributes(settingsFile, java.nio.file.attribute.BasicFileAttributes.class);
        if (NativeUi.current() != root
            || !(workflow.navigation().current().orElseThrow() instanceof ClientDestination.Home)
            || homeDiscoveries < 1
            || UiCostAudit.discoveries() != homeDiscoveries
            || UiCostAudit.preparations() != homePreparations
            || home.get("otherResourceContentLookupsInCallback").getAsLong() != 0
            || home.get("nativeMenuBackgroundLookupsInCallback").getAsLong() > 1
            || home.get("providerEntriesInCallback").getAsLong() != 0
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile))
            || !homeFileAttributes.lastModifiedTime().equals(now.lastModifiedTime())
            || !java.util.Objects.equals(homeFileAttributes.fileKey(), now.fileKey())
            || !homeResourceGeneration.equals(
                PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
          throw new IllegalStateException(
              "Home performed domain work or changed its saved source: " + home);
        home.addProperty("sameFileAndResourceGeneration", true);
        result.add("unopenedHome", home);
        recordHeader(root, "home");
        NativeBindings.press(button(root, "Profiles"));
        pending =
            WorkflowReveal.view(
                    workflow,
                    StyleSettingsStore.DEFINITION,
                    design.inherent.reform.presentation.DestinationId.parse(
                        "lootr_styled:profiles_page"))
                .thenAccept(
                    opened -> {
                      profileOwner = opened;
                      editor = opened;
                    });
        mountTicks = 0;
        stage = PROFILE_TOOLS;
      }
      case PROFILE_TOOLS -> {
        if (++mountTicks < 4) return null;
        recordHeader(root, "profiles");
        if (profileOwner == null
            || workflow.editor(StyleSettingsStore.DEFINITION) != profileOwner
            || workflow.documents().stream()
                    .filter(ClientEditingWorkflow.DocumentState::opened)
                    .count()
                != 1)
          throw new IllegalStateException("Profiles did not retain the existing settings owner");
        button(root, "Use for Models");
        button(root, "Use for Artwork");
        button(root, "Prepare draft preview");
        profileHost =
            workflow.graphics(
                new design.inherent.reform.presentation.graphics.GraphicLimits(
                    16L * 1024 * 1024,
                    32L * 1024 * 1024,
                    16L * 1024 * 1024,
                    256,
                    128L * 1024 * 1024),
                1);
        if (profileHost.nativeOwnership().bytes() != 0 || profileHost.retainedOutput().bytes() != 0)
          throw new IllegalStateException("Profiles allocated graphics before an explicit request");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Profile tools changed settings without an action");
        result.addProperty("profileTaskUsesSettingsOwner", true);
        result.addProperty("profileTaskIdleNativeBytes", profileHost.nativeOwnership().bytes());
        result.addProperty("profileTaskIdleOutputBytes", profileHost.retainedOutput().bytes());
        var inherited =
            children(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals("Vanilla (Inherited)"))
                .findFirst();
        if (inherited.isEmpty()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Profiles did not list the inherited profile");
          return null;
        }
        profileRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        NativeBindings.press(inherited.orElseThrow());
        mountTicks = 0;
        stage = PROFILE_INSPECT;
      }
      case PROFILE_INSPECT -> {
        if (!button(root, "Use for Models").active) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Inspected profile did not permit guarded selection");
          return null;
        }
        if (!profileRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !profileCandidate().equals(original))
          throw new IllegalStateException("Profile inspection changed the draft");
        if (!requestedNarration(root, "Models").contains("Draft value: Default")
            || !requestedNarration(root, "Artwork").contains("Draft value: Default")) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Profiles did not narrate its actual Default titles");
          return null;
        }
        result.addProperty("profileRequestedDefaultTitles", true);
        result.addProperty("profileInspectionDoesNotStage", true);
        NativeBindings.press(button(root, "Use for Models"));
        mountTicks = 0;
        stage = PROFILE_MODELS;
      }
      case PROFILE_MODELS -> {
        if (!profileCandidate().models().equals(ClientSettings.VANILLA)) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Use for Models did not stage the profile ID");
          return null;
        }
        if (!profileCandidate().artwork().equals(original.artwork()))
          throw new IllegalStateException("Choosing Models also changed Artwork");
        if (!requestedNarration(root, "Models").contains("Draft value: Vanilla (Inherited)")
            || !requestedNarration(root, "Artwork").contains("Draft value: Default")) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Independent requested titles did not update");
          return null;
        }
        result.addProperty("profileRequestedTitlesRemainIndependent", true);
        NativeBindings.press(button(root, "Use for Artwork"));
        mountTicks = 0;
        stage = PROFILE_ARTWORK;
      }
      case PROFILE_ARTWORK -> {
        if (!profileCandidate().artwork().equals(ClientSettings.VANILLA)) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Use for Artwork did not stage the profile ID");
          return null;
        }
        if (!profileCandidate().models().equals(ClientSettings.VANILLA)
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Profile field choices saved or lost a selection");
        if (!requestedNarration(root, "Models").contains("Draft value: Vanilla (Inherited)")
            || !requestedNarration(root, "Artwork").contains("Draft value: Vanilla (Inherited)")) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Profiles did not retain both inherited titles");
          return null;
        }
        result.addProperty("profileRequestedTitlesPreserveExactIds", true);
        result.addProperty("profileTaskIndependentChoices", true);
        pending = edit(MODELS, new DraftEdit.SetRaw(original.models().toString()));
        stage = PROFILE_RESTORE_ARTWORK;
      }
      case PROFILE_RESTORE_ARTWORK -> {
        // Operation completion may run on a worker; enter the next GUI edit on this native tick.
        pending = edit(ARTWORK, new DraftEdit.SetRaw(original.artwork().toString()));
        stage = PROFILE_RESTORE;
      }
      case PROFILE_RESTORE -> {
        if (!profileCandidate().equals(original)
            || profileHost.nativeOwnership().bytes() != 0
            || profileHost.retainedOutput().bytes() != 0
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException(
              "Profiles changed files or prepared unrequested graphics");
        NativeBindings.press(button(root, "Show preview"));
        mountTicks = 0;
        stage = PROFILE_PREVIEW_REVEAL;
      }
      case PROFILE_PREVIEW_REVEAL -> {
        if (++mountTicks < 4) return null;
        var viewport = productionPreview(root);
        if (!focusedPath(root, viewport))
          throw new IllegalStateException("Show preview did not focus the native viewport");
        var panel =
            children(root).stream()
                .filter(design.inherent.reform.minecraft.client.ScrollFrame.class::isInstance)
                .map(design.inherent.reform.minecraft.client.ScrollFrame.class::cast)
                .filter(value -> value.children().contains(viewport))
                .findFirst()
                .orElseThrow();
        double middle = viewport.getX() + viewport.getWidth() / 2.0;
        if (!panel.isMouseOver(middle, viewport.getY())
            || !panel.isMouseOver(middle, viewport.getY() + viewport.getHeight() - 1))
          throw new IllegalStateException(
              "Show preview leaves the viewport outside its native pane");
        String narration = NativeBindings.narration(viewport);
        if (!narration.contains("Draft preview") || !narration.contains("Prepare a preview"))
          throw new IllegalStateException("Idle preview narration lost its identity or state");
        var bounds = new JsonObject();
        bounds.addProperty("x", viewport.getX());
        bounds.addProperty("y", viewport.getY());
        bounds.addProperty("width", viewport.getWidth());
        bounds.addProperty("height", viewport.getHeight());
        bounds.addProperty("narration", narration);
        result.add("productionFittedPreview", bounds);
        result.addProperty("productionShowPreviewFocusAndFit", true);
        if (root.width >= 700
            && profileSubject(root).getWidth()
                < mc.font.width(profileSubject(root).getMessage()) + 8)
          throw new IllegalStateException("The subject caption scrolls despite the wide offer");
        captured = false;
        NativeWorld.screenshot(mc, "reform-production-preview-idle", () -> captured = true);
        stage = PROFILE_PREVIEW_BEFORE;
      }
      case PROFILE_PREVIEW_BEFORE -> {
        if (!captured) return null;
        profileDraws = GraphicDrawAudit.state().rendered();
        if (!profileSubject(root).getValue().equals("barrel/unlooted/closed"))
          throw new IllegalStateException("The production preview lost its default barrel subject");
        result.addProperty("profilePreviewDraftModels", profileCandidate().models().toString());
        result.addProperty("profilePreviewDraftArtwork", profileCandidate().artwork().toString());
        NativeBindings.press(button(root, "Prepare draft preview"));
        mountTicks = 0;
        stage = PROFILE_PREVIEW_DRAW;
      }
      case PROFILE_PREVIEW_DRAW -> {
        if (GraphicDrawAudit.state().rendered() <= profileDraws
            || profileHost.nativeOwnership().bytes() == 0) {
          if (++mountTicks > 200)
            throw new IllegalStateException(
                "Production draft preview did not draw; native="
                    + profileHost.nativeOwnership().bytes()
                    + ", output="
                    + profileHost.retainedOutput().bytes());
          return null;
        }
        if (!profileCandidate().equals(original)
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Production preview changed the settings draft or file");
        result.addProperty("productionProfilePreviewRendered", true);
        result.addProperty("productionPreviewNativeBytes", profileHost.nativeOwnership().bytes());
        result.addProperty("productionPreviewOutputBytes", profileHost.retainedOutput().bytes());
        if (!NativeBindings.narration(productionPreview(root)).contains("Preview ready"))
          throw new IllegalStateException("Ready preview narration lost its actual state");
        recordProfileVariant(PROFILE_SUBJECTS.getFirst());
        captured = false;
        NativeWorld.screenshot(mc, "reform-production-preview-drawn", () -> captured = true);
        stage = PROFILE_PREVIEW_CAPTURE;
      }
      case PROFILE_PREVIEW_CAPTURE -> {
        if (!captured) return null;
        NativeBindings.press(profileSubject(root));
        profileDraws = GraphicDrawAudit.state().rendered();
        mountTicks = 0;
        stage = PROFILE_SUBJECT_RETIRED;
      }
      case PROFILE_SUBJECT_RETIRED -> {
        if (!profileSubject(root).getValue().equals("barrel/unlooted/open"))
          throw new IllegalStateException("The subject control did not select the open barrel");
        if (profileHost.nativeOwnership().bytes() != 0
            || profileHost.retainedOutput().bytes() != 0) {
          if (++mountTicks > 200)
            throw new IllegalStateException("The old production preview did not retire");
          return null;
        }
        if (++mountTicks < 4) return null;
        if (GraphicDrawAudit.state().rendered() != profileDraws)
          throw new IllegalStateException("The changed subject submitted an unrequested draw");
        result.addProperty("productionSubjectRetiresWithoutNewRenderedSubmission", true);
        result.addProperty("productionSubjectRetiredNativeBytes", 0);
        result.addProperty("productionSubjectRetiredOutputBytes", 0);
        stage = PROFILE_VARIANT_PREPARE;
      }
      case PROFILE_VARIANT_PREPARE -> {
        String subject = PROFILE_SUBJECTS.get(profileVariant);
        if (!profileSubject(root).getValue().equals(subject))
          throw new IllegalStateException(
              "The production subject lost its declared order: " + subject);
        if (!profileCandidate().equals(original)
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Subject selection changed the settings draft or file");
        profileDraws = GraphicDrawAudit.state().rendered();
        NativeBindings.press(button(root, "Prepare draft preview"));
        mountTicks = 0;
        stage = PROFILE_VARIANT_DRAW;
      }
      case PROFILE_VARIANT_DRAW -> {
        if (GraphicDrawAudit.state().rendered() <= profileDraws
            || profileHost.nativeOwnership().bytes() == 0) {
          if (++mountTicks > 200)
            throw new IllegalStateException(
                "Production subject did not draw: " + PROFILE_SUBJECTS.get(profileVariant));
          return null;
        }
        recordProfileVariant(PROFILE_SUBJECTS.get(profileVariant));
        NativeBindings.press(profileSubject(root));
        profileDraws = GraphicDrawAudit.state().rendered();
        mountTicks = 0;
        stage = PROFILE_VARIANT_RETIRED;
      }
      case PROFILE_VARIANT_RETIRED -> {
        if (profileHost.nativeOwnership().bytes() != 0
            || profileHost.retainedOutput().bytes() != 0) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Production subject resources did not retire");
          return null;
        }
        if (++mountTicks < 4) return null;
        if (GraphicDrawAudit.state().rendered() != profileDraws)
          throw new IllegalStateException("Subject selection prepared an unrequested preview");
        if (++profileVariant < PROFILE_SUBJECTS.size()) {
          stage = PROFILE_VARIANT_PREPARE;
          return null;
        }
        if (profileVariants.size() != PROFILE_SUBJECTS.size()
            || !profileSubject(root).getValue().equals(PROFILE_SUBJECTS.getFirst()))
          throw new IllegalStateException("The production subject cycle is incomplete");
        result.add("productionPreviewSubjects", profileVariants.deepCopy());
        var discarded = profileOwner;
        workflow.discard(StyleSettingsStore.DEFINITION.id());
        if (discarded.isOpen())
          throw new IllegalStateException("Discard left the previous production editor open");
        pending =
            WorkflowReveal.view(
                    workflow,
                    StyleSettingsStore.DEFINITION,
                    design.inherent.reform.presentation.DestinationId.parse(
                        "lootr_styled:profiles_page"))
                .thenAccept(
                    opened -> {
                      if (opened == discarded || !opened.isOpen())
                        throw new IllegalStateException("Reopen reused the discarded owner");
                      profileOwner = opened;
                      editor = opened;
                    });
        mountTicks = 0;
        stage = PROFILE_REOPEN;
      }
      case PROFILE_REOPEN -> {
        if (!profileCandidate().equals(original)
            || profileHost.nativeOwnership().bytes() != 0
            || profileHost.retainedOutput().bytes() != 0
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Reopening prepared graphics or changed settings");
        var inherited =
            children(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals("Vanilla (Inherited)"))
                .findFirst();
        if (inherited.isEmpty()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Reopened production task did not resolve its source");
          return null;
        }
        NativeBindings.press(inherited.orElseThrow());
        mountTicks = 0;
        stage = PROFILE_REOPEN_INSPECT;
      }
      case PROFILE_REOPEN_INSPECT -> {
        if (!button(root, "Use for Models").active) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Reopened task kept its retired selection owner");
          return null;
        }
        NativeBindings.press(button(root, "Use for Models"));
        mountTicks = 0;
        stage = PROFILE_REOPEN_CHOOSE;
      }
      case PROFILE_REOPEN_CHOOSE -> {
        if (!profileCandidate().models().equals(ClientSettings.VANILLA)) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Reopened task did not stage into the fresh owner");
          return null;
        }
        if (!profileCandidate().artwork().equals(original.artwork())
            || workflow.editor(StyleSettingsStore.DEFINITION) != profileOwner)
          throw new IllegalStateException("Reopened task changed the wrong owner or field");
        pending = edit(MODELS, new DraftEdit.SetRaw(original.models().toString()));
        stage = PROFILE_REOPEN_RESTORE;
      }
      case PROFILE_REOPEN_RESTORE -> {
        if (!profileCandidate().equals(original)
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Restoring the reopened draft changed settings");
        result.addProperty("productionDiscardRebindsSettingsOwner", true);
        NativeBindings.press(navigation(root, "Home"));
        mountTicks = 0;
        stage = HOME_RETURN;
      }
      case HOME_RETURN -> {
        if (++mountTicks < 4) return null;
        if (workflow.editor(StyleSettingsStore.DEFINITION) != profileOwner
            || workflow.documents().stream()
                    .filter(ClientEditingWorkflow.DocumentState::opened)
                    .count()
                != 1)
          throw new IllegalStateException("Home did not retain the Profiles settings owner");
        if (!root.getTitle().getString().equals("Lootr: Styled"))
          throw new IllegalStateException("The workspace lost its stable identity");
        button(root, "Settings");
        button(root, "Profiles");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Home changed settings");
        result.addProperty("lazyHome", true);
        result.addProperty("homeRetainsProfileOwner", true);
        captured = false;
        NativeWorld.screenshot(mc, "reform-home", () -> captured = true);
        stage = ENTER_SETTINGS;
      }
      case ENTER_SETTINGS -> {
        if (!captured) return null;
        NativeBindings.press(button(root, "Settings"));
        pending =
            WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION)
                .thenAccept(
                    opened -> {
                      if (opened != profileOwner)
                        throw new IllegalStateException(
                            "Appearance opened a different owner from Profiles");
                      var models = opened.form().fieldControl(MODELS).orElseThrow();
                      if (!children(workflow.screen()).contains(models))
                        throw new IllegalStateException(
                            "Appearance controls were not mounted when its reveal completed");
                      editor = opened;
                      result.addProperty("documentMountedAtReveal", true);
                    });
        captured = false;
        mountTicks = 0;
        stage = -2;
      }
      case -2 -> {
        if (editor.form().fieldControl(MODELS).isEmpty()) return null;
        if (mountTicks++ == 0) {
          var controls = new com.google.gson.JsonArray();
          for (var child : children(root))
            if (child instanceof AbstractWidget widget) {
              var item = new JsonObject();
              item.addProperty("type", child.getClass().getName());
              item.addProperty("label", widget.getMessage().getString());
              item.addProperty("x", widget.getX());
              item.addProperty("y", widget.getY());
              item.addProperty("width", widget.getWidth());
              item.addProperty("height", widget.getHeight());
              controls.add(item);
            }
          Files.writeString(
              mc.gameDirectory.toPath().resolve("lootr-styled-entry-controls.json"),
              new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(controls));
          captured = false;
          NativeWorld.screenshot(mc, "reform-initial-appearance", () -> captured = true);
          return null;
        }
        if (!captured) return null;
        // The identified document reveal already selected Appearance and expanded its
        // ancestors. Pressing that selected branch again would collapse its sections.
        navigation(root, "Appearance");
        navigation(root, "Styling");
        navigation(root, "Profile selection");
        mountTicks = 0;
        stage = STYLING_SECTION;
      }
      case STYLING_SECTION -> {
        if (++mountTicks < 4 || editor.form().fieldControl(MODELS).isEmpty()) return null;
        sectionRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        sectionActions = editor.actions();
        NativeBindings.press(navigation(root, "Styling"));
        if (!editor
            .form()
            .focusTarget(
                new design.inherent.reform.presentation.FormTarget.Section("group:styling")))
          throw new IllegalStateException("The actual Styling section could not be focused");
        mountTicks = 0;
        stage = PROFILE_SECTION;
      }
      case PROFILE_SECTION -> {
        if (++mountTicks < 4) return null;
        NativeBindings.press(navigation(root, "Profile selection"));
        if (!editor
            .form()
            .focusTarget(
                new design.inherent.reform.presentation.FormTarget.Section("group:selection")))
          throw new IllegalStateException(
              "The actual Profile selection section could not be focused");
        if (editor
            .form()
            .focusTarget(
                new design.inherent.reform.presentation.FormTarget.Section("group:absent")))
          throw new IllegalStateException("A missing layout section was silently substituted");
        if (workflow.editor(StyleSettingsStore.DEFINITION) != editor
            || editor.actions() != sectionActions
            || !sectionRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException(
              "Group navigation replaced or changed the actual settings owner");
        result.addProperty("typedGroupFocusPreservesOwnerAndDraft", true);
        mountTicks = 0;
        stage = -1;
      }
      case -1 -> {
        recordHeader(root, "appearance");
        if (!freshCaptions(root)) return null;
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
        if (!navigation.equals(body))
          throw new IllegalStateException(
              "Actual leaf navigation differs from rendered field order: " + result);
        var groups =
            children(root).stream()
                .filter(
                    design.inherent.reform.minecraft.client.NativeNavigationButton.class
                        ::isInstance)
                .map(design.inherent.reform.minecraft.client.NativeNavigationButton.class::cast)
                .filter(
                    control ->
                        List.of("Styling", "Profile selection")
                            .contains(control.getMessage().getString()))
                .sorted(java.util.Comparator.comparingInt(AbstractWidget::getY))
                .map(control -> control.getMessage().getString())
                .toList();
        if (!groups.equals(List.of("Styling", "Profile selection")))
          throw new IllegalStateException(
              "Actual group navigation order differs from the authored sections: " + groups);
        result.add("renderedGroupNavigationOrder", new com.google.gson.Gson().toJsonTree(groups));
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
        checkChooserReflow(browser, filter);
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
                .filter(button -> button.getMessage().getString().equals("Vanilla (Inherited)"))
                .findFirst();
        if (choice.isEmpty() || !choice.orElseThrow().active) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Profile source search did not return Vanilla");
          return null;
        }
        NativeBindings.press(choice.orElseThrow());
        result.addProperty(
            "titleFirstChooserChoice", choice.orElseThrow().getMessage().getString());
        timings.addProperty("search", elapsed(searchAt));
        stage = 92;
      }
      case 92 -> {
        var identity =
            children(NativeUi.current()).stream()
                .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals("Reference ID"))
                .findFirst()
                .orElseThrow();
        if (!identity.getValue().equals(ClientSettings.VANILLA.toString()))
          throw new IllegalStateException("The titled choice resolved a different profile ID");
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
        documentMenuOrigin = workflow.navigation().current().orElseThrow();
        documentMenuActions = editor.actions();
        documentMenuRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        documentMenuGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
        NativeBindings.press(button(root, "Document actions…"));
        mountTicks = 0;
        stage = 102;
      }
      case 102 -> {
        if (NativeUi.current() != root || !hasButton(root, "Defaults")) {
          if (++mountTicks > 120)
            throw new IllegalStateException("Document actions did not mount in the original root");
          return null;
        }
        assertDocumentMenuOwner();
        if (!documentMenuRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
          throw new IllegalStateException("Opening Document actions edited the draft");
        retiredDefaults = button(root, "Defaults");
        result.addProperty("documentActionsUseOriginalRootAndNoHistoryVisit", true);
        captured = false;
        NativeWorld.screenshot(mc, "reform-document-actions", () -> captured = true);
        stage = 103;
      }
      case 103 -> {
        if (!captured) return null;
        NativeBindings.press(retiredDefaults);
        mountTicks = 0;
        stage = 104;
      }
      case 104 -> {
        if (NativeUi.current() != root
            || workflow.documents().getFirst().busy()
            || hasButton(root, "Defaults")) return null;
        if (++mountTicks < 4) return null;
        assertDocumentMenuOwner();
        assertModel(ClientSettings.DEFAULTS.models().toString());
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Document Defaults saved the draft");
        NativeBindings.press(button(root, "Document actions…"));
        mountTicks = 0;
        stage = 105;
      }
      case 105 -> {
        if (NativeUi.current() != root || !hasButton(root, "Undo")) {
          if (++mountTicks > 120)
            throw new IllegalStateException("Document actions did not reopen in the same root");
          return null;
        }
        assertDocumentMenuOwner();
        var undo = button(root, "Undo");
        if (!undo.active)
          throw new IllegalStateException("Defaults lost the retained undo history");
        NativeBindings.press(undo);
        stage = 106;
      }
      case 106 -> {
        if (NativeUi.current() != root
            || workflow.documents().getFirst().busy()
            || hasButton(root, "Undo")) return null;
        assertDocumentMenuOwner();
        assertModel("missing:kept");
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Document Undo saved the draft");
        result.addProperty("documentActionsDefaultsUndoSameDraft", true);
        menuUndoRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        NativeBindings.press(retiredDefaults);
        mountTicks = 0;
        stage = MENU_RETIRED;
      }
      case MENU_RETIRED -> {
        if (++mountTicks < 4 || workflow.documents().getFirst().busy()) return null;
        assertDocumentMenuOwner();
        assertModel("missing:kept");
        if (!menuUndoRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
          throw new IllegalStateException("A retired Defaults callback edited the current draft");
        result.addProperty("retiredDocumentActionDoesNotReplay", true);
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
        NativeBindings.press(button(root, "Settings"));
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
        manualRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        var hide = manualIdButton(root, "Hide ID");
        if (!focusControl(root, hide) || !NativeBindings.activateFocused(root))
          throw new IllegalStateException("Keyboard activation did not collapse the manual ID");
        mountTicks = 0;
        stage = 110;
      }
      case 110 -> {
        if (editor.form().fieldControl(MODELS).orElseThrow()
            instanceof net.minecraft.client.gui.components.EditBox) {
          if (++mountTicks > 120)
            throw new IllegalStateException("Manual ID did not collapse to the titled control");
          return null;
        }
        if (!focusedPath(root, manualIdButton(root, "Edit ID"))
            || !manualRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !editor.canSubmit()
            || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Manual ID collapse lost focus or changed the draft");
        result.addProperty("manualIdCollapseKeepsDraftAndFocus", true);
        reviewRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        reviewSavedBytes = Files.readAllBytes(settingsFile);
        reviewGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
        NativeBindings.press(button(root, "Review changes…"));
        mountTicks = 0;
        stage = REVIEW_DRAFT;
      }
      case REVIEW_DRAFT -> {
        var review = workflow.inspection();
        if (NativeUi.current() != root
            || !(workflow.navigation().current().orElse(null)
                instanceof ClientDestination.Inspection)
            || review.flatMap(value -> value.reviewedInput()).isEmpty()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Review did not capture the retained draft");
          return null;
        }
        retainedReview = review.orElseThrow().reviewedInput().orElseThrow();
        result.addProperty("reviewUsesOriginalRoot", true);
        var visual =
            List.of(
                FieldPath.ROOT.field("enabled"),
                FieldPath.ROOT.field("freshObjects"),
                MODELS,
                ARTWORK);
        if (!editor.form().fieldOrder().equals(visual))
          throw new IllegalStateException(
              "The form field order differs from its actual composition");
        var capturedOrder = retainedReview.entries().stream().map(value -> value.path()).toList();
        var facts = new JsonObject();
        facts.addProperty("captureOrder", capturedOrder.toString());
        facts.addProperty("formOrder", visual.toString());
        var buttons =
            children(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(
                    value ->
                        !(value
                            instanceof
                            design.inherent.reform.minecraft.client.NativeNavigationButton))
                .filter(
                    value ->
                        Set.of("Enabled", "Fresh Objects", "Models", "Artwork")
                            .contains(value.getMessage().getString()))
                .sorted(
                    java.util.Comparator.comparingInt(Button::getY).thenComparingInt(Button::getX))
                .toList();
        var labels = buttons.stream().map(value -> value.getMessage().getString()).toList();
        facts.addProperty("nativeLabels", labels.toString());
        var coordinates = new com.google.gson.JsonArray();
        for (var value : buttons) {
          var row = new JsonObject();
          row.addProperty("label", value.getMessage().getString());
          row.addProperty("x", value.getX());
          row.addProperty("y", value.getY());
          coordinates.add(row);
        }
        facts.add("nativePositions", coordinates);
        result.add("capturedReviewOrderAndLabels", facts);
        if (root.width >= 700
            && (buttons.size() != 4 || buttons.stream().anyMatch(value -> value.getY() <= 0))) {
          if (++mountTicks > 200)
            throw new IllegalStateException(
                "Review field controls did not reach their native layout: " + facts);
          return null;
        }
        if (root.width >= 700
            && !labels.equals(List.of("Enabled", "Fresh Objects", "Models", "Artwork")))
          throw new IllegalStateException(
              "Review index does not follow the displayed form order: " + facts);
        if (!reviewRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !java.util.Arrays.equals(reviewSavedBytes, Files.readAllBytes(settingsFile))
            || !PreviewProbeAccess.generation(PreviewProbeAccess.activePack())
                .equals(reviewGeneration))
          throw new IllegalStateException(
              "Review changed the draft, saved file, or effective resources");
        var freshPath = FieldPath.ROOT.field("freshObjects");
        var freshEntry =
            retainedReview.entries().stream()
                .filter(value -> value.path().equals(freshPath))
                .findFirst()
                .orElseThrow();
        var originalMode = freshCaptionBaseline.fresh();
        String caption = freshCaption(originalMode);
        if (!freshEntry.baseline().equals("\"" + originalMode.name() + "\"")
            || !freshEntry.draft().equals("\"" + originalMode.name() + "\"")
            || !freshEntry.choiceValues().baseline().orElseThrow().fallback().equals(caption)
            || !freshEntry.choiceValues().draft().orElseThrow().fallback().equals(caption))
          throw new IllegalStateException("Captured Fresh Objects labels changed canonical values");
        result.addProperty("reviewFreshCaption", caption);
        result.addProperty("reviewFreshCanonical", originalMode.name());
        mountTicks = 0;
        stage = REVIEW_FRESH;
      }
      case REVIEW_FRESH -> {
        if (!selectReviewFresh(root)) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Review did not select the actual Fresh Objects field");
          return null;
        }
        recordHeader(root, "review");
        if (++mountTicks < 4) return null;
        captured = false;
        NativeWorld.screenshot(mc, "reform-review", () -> captured = true);
        stage = REVIEW_CAPTURE;
      }
      case REVIEW_CAPTURE -> {
        if (!captured) return null;
        NativeBindings.press(returnToParent(root));
        mountTicks = 0;
        stage = REVIEW_BACK;
      }
      case REVIEW_BACK -> {
        if (NativeUi.current() != root
            || workflow.navigation().current().orElse(null)
                instanceof ClientDestination.Inspection) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Review Back lost its original root destination");
          return null;
        }
        if (!reviewRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !java.util.Arrays.equals(reviewSavedBytes, Files.readAllBytes(settingsFile)))
          throw new IllegalStateException("Review Back changed its retained draft or file");
        pending =
            workflow
                .navigation()
                .open(new ClientDestination.Inspection(StyleSettingsStore.DEFINITION.id()))
                .completion()
                .thenAccept(
                    value -> {
                      if (value.outcome() != NavigationAttempt.Outcome.REVEALED)
                        throw new IllegalStateException("Typed inspection return failed: " + value);
                    })
                .toCompletableFuture();
        stage = REVIEW_RETURNED;
      }
      case REVIEW_RETURNED -> {
        if (NativeUi.current() != root
            || workflow.inspection().flatMap(value -> value.reviewedInput()).orElseThrow()
                != retainedReview
            || !reviewRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !java.util.Arrays.equals(reviewSavedBytes, Files.readAllBytes(settingsFile))
            || !PreviewProbeAccess.generation(PreviewProbeAccess.activePack())
                .equals(reviewGeneration))
          throw new IllegalStateException("Typed inspection return lost its root/capture/source");
        result.addProperty("reviewCachedCaptureSurvivesBackAndTypedReturn", true);
        NativeBindings.press(returnToParent(root));
        mountTicks = 0;
        stage = REVIEW_FINAL_BACK;
      }
      case REVIEW_FINAL_BACK -> {
        if (workflow.navigation().current().orElse(null) instanceof ClientDestination.Inspection) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Review did not return to editing");
          return null;
        }
        result.addProperty("reviewRetainsVisualOrderAndDraft", true);
        stage = 3;
      }
      case 3 -> {
        if (!editor.canSubmit()) return null;
        actionAt = System.nanoTime();
        operationOrigin = workflow.navigation().current().orElseThrow();
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
        if (NativeUi.current() != root)
          throw new IllegalStateException("Combined action replaced the original root");
        operationDestination = workflow.navigation().current().orElseThrow();
        inspectedOperation =
            workflow.operationInspection().orElseThrow().observedOperation().orElseThrow().id();
        inspectedGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
        inspectedSavedBytes = Files.readAllBytes(settingsFile);
        NativeBindings.press(historyButton(root, "Back"));
        mountTicks = 0;
        stage = RESULT_BACK;
      }
      case RESULT_BACK -> {
        if (!workflow.navigation().current().filter(operationOrigin::equals).isPresent()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Result Back lost its exact origin");
          return null;
        }
        NativeBindings.press(historyButton(root, "Forward"));
        mountTicks = 0;
        stage = RESULT_FORWARD;
      }
      case RESULT_FORWARD -> {
        if (!workflow.navigation().current().filter(operationDestination::equals).isPresent()
            || !operationDone()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Forward did not restore the same result");
          return null;
        }
        unchangedOperation();
        result.addProperty("operationBackForwardPreservesExactHandleAndOrigin", true);
        NativeBindings.press(historyButton(root, "Back"));
        mountTicks = 0;
        stage = RESULT_INSPECT_LINK;
      }
      case RESULT_INSPECT_LINK -> {
        if (!workflow.navigation().current().filter(operationOrigin::equals).isPresent()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Second Back inserted an unseen visit");
          return null;
        }
        if (NativeUi.current() != root)
          throw new IllegalStateException("Result Back lost the retained workflow");
        if (!button(root, "Save and reload resources")
            .getMessage()
            .getString()
            .equals("Save and reload resources"))
          throw new IllegalStateException("The immediate operation lost its effect-named button");
        var inspect =
            children(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals("Inspect result"))
                .filter(
                    value ->
                        NativeBindings.narration(value).startsWith("Save and reload resources: "))
                .findFirst()
                .orElseThrow();
        NativeBindings.press(inspect);
        mountTicks = 0;
        stage = INSPECT_SAVED_RESULT;
      }
      case INSPECT_SAVED_RESULT -> {
        if (!operationDone()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Inspect result did not open its retained operation");
          return null;
        }
        operationHeaderDraws = FrameAudit.rootSequence(root);
        stage = OPERATION_HEADER_WAIT;
      }
      case OPERATION_HEADER_WAIT -> {
        if (!operationDone())
          throw new IllegalStateException("Operation result changed before its header capture");
        if (!NativeBindings.unobscured(mc)) return null;
        long draws = FrameAudit.rootSequence(root);
        if (draws < 0) return null;
        if (operationHeaderDraws < 0) {
          operationHeaderDraws = draws;
          return null;
        }
        if (draws < operationHeaderDraws + 4) return null;
        captured = false;
        NativeWorld.screenshot(mc, "reform-operation", () -> captured = true);
        stage = OPERATION_HEADER_CAPTURED;
      }
      case OPERATION_HEADER_CAPTURED -> {
        if (!captured) return null;
        recordHeader(root, "operation");
        unchangedOperation();
        result.addProperty(
            "operationHeaderCaptureDraws", FrameAudit.rootSequence(root) - operationHeaderDraws);
        result.addProperty("effectNamedActionAndInspectionSeparated", true);
        result.addProperty("operationsUseOriginalRoot", NativeUi.current() == root);
        NativeBindings.press(returnToParent(root));
        mountTicks = 0;
        stage = RESULT_RESTORE;
      }
      case RESULT_RESTORE -> {
        if (!workflow.navigation().current().filter(operationOrigin::equals).isPresent()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Inspection Back lost its origin");
          return null;
        }
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
        stage = 5;
      }
      case 5 -> {
        if (!editor.canSubmit()) return null;
        actionAt = System.nanoTime();
        operationOrigin = workflow.navigation().current().orElseThrow();
        NativeBindings.press(button(root, "Save and reload resources"));
        stage++;
      }
      case 6 -> {
        var saved = ClientSettings.parse(Files.readAllBytes(settingsFile));
        if (!saved.equals(original) || !applied() || !operationDone()) return null;
        timings.addProperty("repeatedSaveAndReload", elapsed(actionAt));
        NativeBindings.press(returnToParent(root));
        mountTicks = 0;
        stage = RESTORE_RESULT_BACK;
      }
      case RESTORE_RESULT_BACK -> {
        if (!workflow.navigation().current().filter(operationOrigin::equals).isPresent()) {
          if (++mountTicks > 200)
            throw new IllegalStateException("Restoration result lost its origin");
          return null;
        }
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
        if (NativeUi.current() != root || workflow.operationInspection().isEmpty())
          throw new IllegalStateException("Export did not retain the shared root/operation");
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

  private boolean operationDone() {
    return NativeUi.current() == workflow.screen()
        && (workflow.navigation().current().orElse(null)
                instanceof ClientDestination.OperationResult
            || workflow.navigation().current().orElse(null)
                instanceof ClientDestination.DocumentOperation)
        && workflow
            .operationInspection()
            .flatMap(value -> value.observedOperation())
            .filter(
                state ->
                    state.state()
                        == design.inherent.reform.operation.OperationSnapshot.State.COMPLETED)
            .isPresent();
  }

  private void unchangedOperation() throws Exception {
    if (!workflow
            .operationInspection()
            .orElseThrow()
            .observedOperation()
            .orElseThrow()
            .id()
            .equals(inspectedOperation)
        || !PreviewProbeAccess.generation(PreviewProbeAccess.activePack())
            .equals(inspectedGeneration)
        || !java.util.Arrays.equals(inspectedSavedBytes, Files.readAllBytes(settingsFile)))
      throw new IllegalStateException(
          "Result navigation restarted an effect or changed its source");
  }

  private void observeResultNavigation(Screen root) {
    var facts = new JsonObject();
    facts.addProperty("stage", stage);
    facts.addProperty(
        "current", workflow.navigation().current().map(Object::toString).orElse("none"));
    facts.addProperty("inspectionVisible", workflow.inspection().isPresent());
    workflow
        .operationInspection()
        .flatMap(value -> value.observedOperation())
        .ifPresent(
            value -> {
              facts.addProperty("operation", value.id().toString());
              facts.addProperty("state", value.state().toString());
            });
    var controls = new com.google.gson.JsonArray();
    for (var child : children(root)) {
      if (child instanceof Button button
          && Set.of("Back", "Forward").contains(button.getMessage().getString())) {
        var row = new JsonObject();
        row.addProperty("label", button.getMessage().getString());
        row.addProperty("x", button.getX());
        row.addProperty("y", button.getY());
        row.addProperty("active", button.active);
        controls.add(row);
      }
    }
    facts.add("controls", controls);
    result.add("lastResultNavigation", facts);
  }

  private static Button historyButton(Screen root, String caption) {
    var control =
        children(root).stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(value -> value.getMessage().getString().equals(caption))
            .min(java.util.Comparator.comparingInt(Button::getY))
            .orElseThrow();
    if (!control.active) throw new IllegalStateException("History " + caption + " is disabled");
    return control;
  }

  private void assertDocumentMenuOwner() throws Exception {
    if (workflow.editor(StyleSettingsStore.DEFINITION) != editor
        || editor.actions() != documentMenuActions
        || !workflow.navigation().current().filter(documentMenuOrigin::equals).isPresent()
        || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile))
        || !documentMenuGeneration.equals(
            PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
      throw new IllegalStateException(
          "Document actions changed its owner, destination, file or resources");
  }

  static boolean hasButton(Screen root, String caption) {
    return children(root).stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .anyMatch(value -> value.getMessage().getString().equals(caption));
  }

  private static Button returnToParent(Screen root) {
    return children(root).stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(
            value ->
                value.active
                    && Set.of(
                            "Back to settings",
                            "Back to inspection",
                            "Back to conflict review",
                            "Back to workspace")
                        .contains(value.getMessage().getString()))
        .max(java.util.Comparator.comparingInt(Button::getY))
        .orElseThrow();
  }

  private static double elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000.0;
  }

  String progress() {
    return "ui=" + stage + (pending == null ? "" : ",pending=" + !pending.isDone());
  }

  JsonObject observations() {
    return result.deepCopy();
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

  private void recordHeader(Screen root, String phase) {
    if (measuredHeaderPhases.contains(phase)) return;
    var controls =
        children(root).stream()
            .filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast)
            .filter(
                widget -> {
                  var text = NativeBindings.narration(widget);
                  return text.startsWith("Location:") || text.startsWith("Current location:");
                })
            .toList();
    if (controls.isEmpty() || NativeUi.current() != root)
      throw new IllegalStateException("Actual header location controls missing at " + phase);
    int x = controls.stream().mapToInt(AbstractWidget::getX).min().orElseThrow();
    int y = controls.stream().mapToInt(AbstractWidget::getY).min().orElseThrow();
    int height = controls.getFirst().getHeight();
    var font = Minecraft.getInstance().font;
    int[] offer = {
      root.width, root.height, font.width("Lootr: Styled"), font.lineHeight, x, y, height
    };
    if (controls.stream().anyMatch(widget -> widget.getY() != y || widget.getHeight() != height)
        || height <= 0
        || y < 0
        || y + height > root.height
        || headerBaseline != null
            && (!java.util.Arrays.equals(headerBaseline, offer)
                || !headerIdentity.equals(root.getTitle().getString())))
      throw new IllegalStateException(
          "Ordinary navigation changed the stable header offer at "
              + phase
              + ": "
              + java.util.Arrays.toString(offer));
    if (headerBaseline == null) {
      headerBaseline = offer;
      headerIdentity = root.getTitle().getString();
    }
    var row = new JsonObject();
    row.addProperty("phase", phase);
    row.addProperty("identity", root.getTitle().getString());
    row.addProperty("viewportWidth", root.width);
    row.addProperty("viewportHeight", root.height);
    row.addProperty("fontIdentityWidth", offer[2]);
    row.addProperty("fontLineHeight", offer[3]);
    row.addProperty("locationX", x);
    row.addProperty("locationY", y);
    row.addProperty("locationHeight", height);
    var values = new com.google.gson.JsonArray();
    for (var control : controls) {
      var value = new JsonObject();
      value.addProperty("caption", control.getMessage().getString());
      value.addProperty("x", control.getX());
      value.addProperty("y", control.getY());
      value.addProperty("width", control.getWidth());
      value.addProperty("height", control.getHeight());
      value.addProperty("narration", NativeBindings.narration(control));
      values.add(value);
    }
    row.add("controls", values);
    headerOffers.add(row);
    measuredHeaderPhases.add(phase);
  }

  private boolean freshCaptions(Screen root) throws Exception {
    if (freshCaptionsDone) return true;
    var current = profileCandidate();
    if (freshCaptionBaseline == null) {
      freshCaptionBaseline = current;
      freshCaptionGeneration = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    }
    var modes = ClientSettings.Fresh.values();
    var expected =
        modes[(freshCaptionBaseline.fresh().ordinal() + freshCaptionStep) % modes.length];
    var path = FieldPath.ROOT.field("freshObjects");
    var control = editor.form().fieldControl(path).orElseThrow();
    if (editor.busy()
        || current.fresh() != expected
        || !control.getMessage().getString().equals(freshCaption(expected))) {
      if (++mountTicks > 200)
        throw new IllegalStateException(
            "Fresh Objects caption or canonical edit did not settle: "
                + current.fresh()
                + "/"
                + control.getMessage().getString());
      return false;
    }
    var unchangedFields =
        new ClientSettings(
            current.enabled(), current.models(), current.artwork(), freshCaptionBaseline.fresh());
    if (!unchangedFields.equals(freshCaptionBaseline)
        || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile))
        || !freshCaptionGeneration.equals(
            PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
      throw new IllegalStateException(
          "Fresh caption editing changed another field, file or resources");
    var row = new JsonObject();
    row.addProperty("canonical", current.fresh().name());
    row.addProperty("caption", control.getMessage().getString());
    row.addProperty("narration", NativeBindings.narration(control));
    freshCaptionRows.add(row);
    if (freshCaptionStep == modes.length) {
      if (!current.equals(freshCaptionBaseline))
        throw new IllegalStateException("Fresh caption cycle did not restore the complete draft");
      result.add("freshChoiceCaptions", freshCaptionRows);
      result.addProperty("freshKeyboardCyclePreservesSource", true);
      freshCaptionsDone = true;
      mountTicks = 0;
      return true;
    }
    if (!editor.form().focusField(path) || !NativeBindings.activateFocused(root))
      throw new IllegalStateException("Fresh Objects did not accept native keyboard activation");
    freshCaptionStep++;
    mountTicks = 0;
    return false;
  }

  private boolean selectReviewFresh(Screen root) {
    if (workflow
        .inspection()
        .orElseThrow()
        .selectedField()
        .equals(java.util.Optional.of(FieldPath.ROOT.field("freshObjects")))) return true;
    var index =
        children(root).stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(
                value ->
                    !(value
                        instanceof design.inherent.reform.minecraft.client.NativeNavigationButton))
            .filter(value -> value.getMessage().getString().equals("Fresh Objects"))
            .findFirst();
    if (index.isPresent()) {
      NativeBindings.press(index.orElseThrow());
      result.addProperty("reviewFreshSelectionControl", "Field index");
    } else {
      var cycle =
          children(root).stream()
              .filter(AbstractWidget.class::isInstance)
              .map(AbstractWidget.class::cast)
              .filter(value -> NativeBindings.narration(value).startsWith("Field: "))
              .findFirst();
      if (cycle.isEmpty()) return false;
      if (!focusControl(root, cycle.orElseThrow()) || !NativeBindings.activateFocused(root))
        throw new IllegalStateException(
            "Focused review field cycle did not accept native keyboard input");
      result.addProperty("reviewFreshSelectionControl", "Focused field cycle");
    }
    return false;
  }

  private static String freshCaption(ClientSettings.Fresh value) {
    return switch (value) {
      case AUTO -> "Auto";
      case ON -> "On";
      case OFF -> "Off";
    };
  }

  private ClientSettings profileCandidate() {
    return ClientSettings.class.cast(
        profileOwner.view().snapshot().orElseThrow().inspect().candidate().orElseThrow().value());
  }

  private void recordProfileVariant(String subject) throws java.io.IOException {
    if (!profileCandidate().equals(original)
        || !java.util.Arrays.equals(originalBytes, Files.readAllBytes(settingsFile)))
      throw new IllegalStateException("Production subject preparation changed settings");
    var value = new JsonObject();
    value.addProperty("subject", subject);
    value.addProperty("rendered", true);
    value.addProperty("nativeBytes", profileHost.nativeOwnership().bytes());
    value.addProperty("outputBytes", profileHost.retainedOutput().bytes());
    profileVariants.add(value);
    result.add("productionPreviewSubjects", profileVariants.deepCopy());
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

  private void checkChooserReflow(
      Screen browser, net.minecraft.client.gui.components.EditBox filter) {
    var key =
        children(browser).stream()
            .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
            .map(net.minecraft.client.gui.components.EditBox.class::cast)
            .filter(box -> box.getMessage().getString().equals("Reference ID"))
            .findFirst()
            .orElseThrow();
    String inspected = key.getValue();
    filter.setCursorPosition(3);
    if (!focusControl(browser, filter))
      throw new IllegalStateException("The chooser query is not in the native focus tree");
    var arrangements = new com.google.gson.JsonArray();
    int[] widths = {1100, 320, 1100};
    int[] heights = {540, 240, 540};
    for (int i = 0; i < widths.length; i++) {
      NativeBindings.resize(browser, Minecraft.getInstance(), widths[i], heights[i]);
      long panes =
          browser.children().stream()
              .filter(child -> child.getClass().getSimpleName().equals("FormPanel"))
              .count();
      long expected = i == 1 ? 1 : 2;
      if (panes != expected
          || !children(browser).contains(filter)
          || !children(browser).contains(key)
          || !filter.getValue().equals("Vanilla")
          || !key.getValue().equals(inspected)
          || filter.getCursorPosition() != 3
          || !focusedPath(browser, filter))
        throw new IllegalStateException(
            "Chooser reflow lost its native query, inspected ID, focus, or caret at "
                + widths[i]
                + "; panes="
                + panes);
      var row = new JsonObject();
      row.addProperty("width", widths[i]);
      row.addProperty("height", heights[i]);
      row.addProperty("panes", panes);
      row.addProperty("query", filter.getValue());
      row.addProperty("inspectedId", key.getValue());
      row.addProperty("caret", filter.getCursorPosition());
      arrangements.add(row);
    }
    NativeBindings.resize(browser, Minecraft.getInstance(), nativeWidth, nativeHeight);
    result.add("chooserAdjacentFocusedAdjacent", arrangements);
  }

  static boolean focusControl(ContainerEventHandler parent, GuiEventListener target) {
    for (var child : parent.children()) {
      if (child == target
          || child instanceof ContainerEventHandler nested && focusControl(nested, target)) {
        parent.setFocused(child);
        child.setFocused(true);
        return true;
      }
    }
    return false;
  }

  private static String requestedNarration(Screen screen, String field) {
    for (var child : children(screen)) {
      if (!(child instanceof design.inherent.reform.minecraft.client.ScrollFrame panel)) continue;
      for (var control : panel.children()) {
        if (!(control instanceof AbstractWidget widget)
            || !widget.getMessage().getString().equals("i")
            || !NativeBindings.narration(widget).contains("About " + field)) continue;
        if (!focusControl(screen, widget))
          throw new IllegalStateException("Requested field information lost native focus");
        return NativeBindings.narration(panel);
      }
    }
    return "";
  }

  static boolean focusedPath(ContainerEventHandler parent, GuiEventListener target) {
    var current = parent.getFocused();
    return current == target && current.isFocused()
        || current instanceof ContainerEventHandler nested && focusedPath(nested, target);
  }

  static Button manualIdButton(Screen screen, String label) {
    return children(screen).stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(control -> control.getMessage().getString().equals(label))
        .filter(control -> NativeBindings.narration(control).startsWith("Models:"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Models lost its " + label + " action"));
  }

  private static AbstractWidget productionPreview(Screen screen) {
    return children(screen).stream()
        .filter(AbstractWidget.class::isInstance)
        .map(AbstractWidget.class::cast)
        .filter(widget -> widget.getMessage().getString().equals("Draft preview"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("The production preview widget is missing"));
  }

  private static net.minecraft.client.gui.components.CycleButton<?> profileSubject(Screen screen) {
    return children(screen).stream()
        .filter(net.minecraft.client.gui.components.CycleButton.class::isInstance)
        .map(net.minecraft.client.gui.components.CycleButton.class::cast)
        .filter(
            widget ->
                widget.getValue() instanceof String id
                    && (id.startsWith("barrel/")
                        || id.startsWith("chest/")
                        || id.startsWith("trapped-chest/")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("The production preview subject is missing"));
  }

  static List<GuiEventListener> children(Screen screen) {
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

  static Button button(Screen screen, String label) {
    return children(screen).stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(
            button ->
                button.getMessage().getString().equals(label)
                    || NativeBindings.narration(button).startsWith(label + ": "))
        .sorted(
            java.util.Comparator.comparingInt(
                button -> button.getMessage().getString().equals(label) ? 0 : 1))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing shared action: " + label));
  }

  private static design.inherent.reform.minecraft.client.NativeNavigationButton navigation(
      Screen screen, String label) {
    return navigationChoice(screen, label)
        .orElseThrow(() -> new IllegalStateException("Missing navigation destination: " + label));
  }

  private static java.util.Optional<design.inherent.reform.minecraft.client.NativeNavigationButton>
      navigationChoice(Screen screen, String label) {
    return children(screen).stream()
        .filter(design.inherent.reform.minecraft.client.NativeNavigationButton.class::isInstance)
        .map(design.inherent.reform.minecraft.client.NativeNavigationButton.class::cast)
        .filter(control -> control.getMessage().getString().equals(label))
        .findFirst();
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
