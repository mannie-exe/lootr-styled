package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.resources.TextureImages;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.minecraft.client.GraphicPreparation;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.RenderLifetime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Actual registered task controls, entered work, native reload, hidden return, and public close.
 */
final class ProductionPreparationProbe {
  private enum Phase {
    OPEN,
    ENTERED,
    CANCEL,
    RELOAD,
    HOME,
    RETURN,
    HELD,
    DRAIN,
    READY,
    RETIRE,
    REOPEN,
    DONE
  }

  private static final String[] CASES = {"cancel", "reload", "hidden-return", "close"};
  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final ClientGraphicHost host;
  private final Path settings;
  private final byte[] original;
  private final BasicFileAttributes attributes;
  private final Map<String, String> localFiles;
  private final JsonObject result = new JsonObject();
  private final JsonArray rows = new JsonArray();
  private ClientEditor<ClientSettings> editor;
  private DraftRevision revision;
  private CompletableFuture<?> navigation, reload;
  private CompletableFuture<RenderLifetime.State> released;
  private ProductionPreparationAudit.Entry entered, recovery;
  private PreviewWorkerGate.Hold hold;
  private JsonObject row;
  private UUID generation, expectedGeneration;
  private Phase phase = Phase.OPEN;
  private int scenario, ticks, requestsBeforeHide;
  private long drawMark;

  ProductionPreparationProbe() throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    attributes = Files.readAttributes(settings, BasicFileAttributes.class);
    localFiles = profileFiles();
    expectedGeneration = generation();
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    host =
        workflow.graphics(new GraphicLimits(16L << 20, 32L << 20, 16L << 20, 256, 128L << 20), 1);
    ProductionPreparationAudit.begin();
    navigation = profiles();
    result.add("cases", rows);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 600)
      throw new IllegalStateException("Production preparation timed out: " + phase + " " + rows);
    unchanged();
    switch (phase) {
      case OPEN, REOPEN -> {
        if (!navigation.isDone()) return null;
        navigation.join();
        if (!UiProbe.hasButton(workflow.screen(), "Prepare draft preview")
            || !UiProbe.button(workflow.screen(), "Prepare draft preview").active) return null;
        if (revision == null) revision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        hold = ProductionPreparationAudit.arm();
        drawMark = GraphicDrawAudit.state().rendered();
        entered = prepare();
        row = new JsonObject();
        row.addProperty("case", CASES[scenario]);
        rows.add(row);
        next(Phase.ENTERED);
      }
      case ENTERED -> {
        if (!hold.entered) return null;
        if (hold.exited
            || entered.attempt().operation().isEmpty()
            || entered.attempt().completion().toCompletableFuture().isDone())
          throw new IllegalStateException("Actual entered production work was not pending");
        row.addProperty("workerEntered", true);
        row.addProperty("workerThread", hold.thread);
        row.addProperty(
            "operation", entered.view().snapshot().operation().orElseThrow().id().toString());
        switch (scenario) {
          case 0 -> {
            press("Cancel preparation");
            next(Phase.CANCEL);
          }
          case 1 -> {
            generation = generation();
            reload = mc.reloadResourcePacks();
            next(Phase.RELOAD);
          }
          case 2 -> {
            requestsBeforeHide = ProductionPreparationAudit.requests();
            navigation = home();
            next(Phase.HOME);
          }
          case 3 -> {
            workflow.close();
            NativeUi.open(parent);
            if (workflow.isOpen() || editor.isOpen())
              throw new IllegalStateException("Public close retained the editing owner");
            next(Phase.HELD);
          }
          default -> throw new IllegalStateException("Unexpected production scenario");
        }
      }
      case CANCEL -> {
        var operation = entered.attempt().operation().orElseThrow().snapshot();
        if (!operation.cancellationRequested()) return null;
        if (operation.terminal() || hold.exited)
          throw new IllegalStateException(
              "Cancellation reported completion before entered work drained");
        row.addProperty("nativeCancellationRequested", true);
        recovery = prepare();
        if (recovery.view() != entered.view())
          throw new IllegalStateException("Replacement changed the actual task view");
        next(Phase.HELD);
      }
      case RELOAD -> {
        if (!reload.isDone()) return null;
        reload.join();
        if (generation.equals(generation())) return null;
        row.addProperty("generationBefore", generation.toString());
        row.addProperty("generationAfter", generation().toString());
        expectedGeneration = generation();
        result.addProperty("actualResourceReloads", 1);
        next(Phase.HELD);
      }
      case HOME -> {
        if (!navigation.isDone()) return null;
        navigation.join();
        if (entered.view().snapshot().visible())
          throw new IllegalStateException("Home did not hide actual production view");
        navigation = profiles();
        next(Phase.RETURN);
      }
      case RETURN -> {
        if (!navigation.isDone()) return null;
        navigation.join();
        if (ProductionPreparationAudit.requests() != requestsBeforeHide)
          throw new IllegalStateException("Returning implicitly prepared a preview");
        row.addProperty("returnDoesNotPrepare", true);
        next(Phase.HELD);
      }
      case HELD -> {
        if (ticks < 8) return null;
        var done = entered.attempt().completion().toCompletableFuture().isDone();
        if (hold.exited
            || GraphicDrawAudit.state().rendered() != drawMark
            || scenario != 3 && done
            || scenario == 0 && recovery.attempt().operation().isPresent())
          throw new IllegalStateException("Entered work was bypassed or obsolete output drew");
        if (scenario == 3 && entered.view().snapshot().state() != ClientGraphicHost.State.CLOSED)
          throw new IllegalStateException("Closed production view revived");
        row.addProperty("heldWithoutObsoleteDraw", true);
        row.addProperty("terminalBeforeWorkerExit", done);
        row.addProperty("stateWhileHeld", entered.view().snapshot().state().name());
        hold.release();
        next(Phase.DRAIN);
      }
      case DRAIN -> {
        if (!hold.exited || !entered.attempt().completion().toCompletableFuture().isDone())
          return null;
        var outcome = entered.attempt().completion().toCompletableFuture().join();
        if (!Set.of(
                ClientGraphicHost.State.CANCELLED,
                ClientGraphicHost.State.REFUSED,
                ClientGraphicHost.State.CLOSED)
            .contains(outcome.state()))
          throw new IllegalStateException(
              "Obsolete production result: " + outcome, outcome.cause().orElse(null));
        row.addProperty("terminalState", outcome.state().name());
        var codes = new JsonArray();
        outcome.report().issues().forEach(issue -> codes.add(issue.code()));
        row.add("codes", codes);
        row.addProperty("workerExited", true);
        if (scenario == 3) {
          if (!empty() || entered.view().snapshot().state() != ClientGraphicHost.State.CLOSED)
            return null;
          result.addProperty("closedWorkflowDiscardsLateResult", true);
          result.addProperty("nativeBytes", host.nativeOwnership().bytes());
          result.addProperty("sceneBytes", host.retainedOutput().bytes());
          result.addProperty("requests", ProductionPreparationAudit.requests());
          if (ProductionPreparationAudit.requests() != 7 || host.releaseFailureCount() != 0)
            throw new IllegalStateException("Production request/release count changed");
          ProductionPreparationAudit.stop();
          next(Phase.DONE);
        } else {
          if (scenario != 0) {
            if (!empty()
                || entered.view().preparation().stream()
                    .anyMatch(GraphicPreparation.Ready.class::isInstance)) return null;
            recovery = prepare();
          }
          next(Phase.READY);
        }
      }
      case READY -> {
        if (!recovery.attempt().completion().toCompletableFuture().isDone()) return null;
        var ready = recovery.attempt().completion().toCompletableFuture().join();
        if (ready.state() != ClientGraphicHost.State.READY)
          throw new IllegalStateException(
              "Production recovery refused: " + ready, ready.cause().orElse(null));
        if (GraphicDrawAudit.state().rendered() <= drawMark) return null;
        var preparation = recovery.view().preparation().orElseThrow();
        if (!(preparation instanceof GraphicPreparation.Ready value))
          throw new IllegalStateException("Recovery lost its actual lease");
        if (!value.graphic().stamp().resources().snapshot().equals(generation()))
          throw new IllegalStateException("Recovery used an old resource generation");
        released = value.graphic().releaseCompletion().toCompletableFuture();
        row.addProperty("explicitRecoveryDrawn", true);
        row.addProperty("recoveryGeneration", generation().toString());
        navigation = home();
        next(Phase.RETIRE);
      }
      case RETIRE -> {
        if (!navigation.isDone() || !released.isDone() || !empty()) return null;
        navigation.join();
        var state = released.join();
        if (!state.released() || state.outstandingUses() != 0 || state.releaseFailure().isPresent())
          throw new IllegalStateException("Production recovery did not retire");
        row.addProperty("recoveryReleased", true);
        scenario++;
        navigation = profiles();
        next(Phase.REOPEN);
      }
      case DONE -> {
        if (!localFiles.equals(profileFiles()))
          throw new IllegalStateException("Preview lifecycle changed local profiles or exports");
        result.addProperty("localProfileFilesUnchanged", true);
        result.addProperty("resourceGenerationPreservedOutsideReload", true);
        result.addProperty("localProfileFileCount", localFiles.size());
        result.addProperty("settingsUnchanged", true);
        result.addProperty("savedFileIdentityUnchanged", true);
        result.addProperty("draftRetainedUntilClose", true);
        return result;
      }
    }
    return null;
  }

  private ProductionPreparationAudit.Entry prepare() {
    int before = ProductionPreparationAudit.requests();
    press("Prepare draft preview");
    if (ProductionPreparationAudit.requests() != before + 1)
      throw new IllegalStateException("Native Prepare did not make one real request");
    press("Show preview");
    return ProductionPreparationAudit.latest();
  }

  private void press(String label) {
    var button = UiProbe.button(workflow.screen(), label);
    if (!button.active) throw new IllegalStateException("Production control inactive: " + label);
    NativeBindings.press(button);
  }

  private CompletableFuture<?> profiles() {
    return WorkflowReveal.view(
            workflow,
            StyleSettingsStore.DEFINITION,
            DestinationId.parse("lootr_styled:profiles_page"))
        .thenAccept(
            value -> {
              if (editor != null && editor != value)
                throw new IllegalStateException("Task return replaced settings owner");
              editor = value;
            });
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
                  || value.outcome()
                      != design.inherent.reform.minecraft.client.NavigationAttempt.Outcome.REVEALED)
                throw new IllegalStateException("Home was not actually revealed: " + value);
              return value;
            })
        .toCompletableFuture();
  }

  private boolean empty() {
    return host.nativeOwnership().bytes() == 0
        && host.nativeOwnership().resources() == 0
        && host.retainedOutput().bytes() == 0
        && host.retainedOutput().resources() == 0;
  }

  private static UUID generation() {
    return PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
  }

  private Map<String, String> profileFiles() throws Exception {
    var found = new TreeMap<String, String>();
    long bytes = 0;
    int entries = 0;
    var root = settings.getParent();
    try (var paths = Files.walk(root)) {
      var iterator = paths.iterator();
      while (iterator.hasNext()) {
        var path = iterator.next();
        if (++entries > 4096 || Files.isSymbolicLink(path))
          throw new IllegalStateException(
              "Local fixture profile tree exceeds its inspection bound");
        if (!Files.isRegularFile(path)) continue;
        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
        if (attrs.size() > (16L << 20) - bytes)
          throw new IllegalStateException(
              "Local fixture profile bytes exceed16MiB inspection bound");
        byte[] data;
        try (var input = Files.newInputStream(path)) {
          data = input.readNBytes((int) ((16L << 20) - bytes + 1));
        }
        var after = Files.readAttributes(path, BasicFileAttributes.class);
        bytes += data.length;
        if (bytes > (16L << 20)
            || data.length != attrs.size()
            || !attrs.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(attrs.fileKey(), after.fileKey()))
          throw new IllegalStateException("Local fixture profile changed during inspection");
        found.put(
            root.relativize(path).toString(),
            TextureImages.hash(data) + ":" + attrs.lastModifiedTime() + ":" + attrs.fileKey());
      }
    }
    return Map.copyOf(found);
  }

  private void unchanged() throws Exception {
    if (phase != Phase.RELOAD && !expectedGeneration.equals(generation()))
      throw new IllegalStateException(
          "Preview lifecycle changed resources outside the explicit reload");
    var now = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(original, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !Objects.equals(attributes.fileKey(), now.fileKey()))
      throw new IllegalStateException("Production preparation changed saved settings");
    if (revision != null
        && editor.isOpen()
        && !revision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow())))
      throw new IllegalStateException("Production preparation changed the draft");
  }

  private void next(Phase value) {
    phase = value;
    ticks = 0;
  }

  String progress() {
    return phase + ":" + scenario;
  }

  JsonObject observations() {
    result.addProperty("phase", phase.name());
    result.addProperty("scenario", scenario);
    result.addProperty("observedNativeBytes", host.nativeOwnership().bytes());
    result.addProperty("observedSceneBytes", host.retainedOutput().bytes());
    if (entered != null) result.addProperty("viewState", entered.view().snapshot().state().name());
    if (hold != null) {
      result.addProperty("holdEntered", hold.entered);
      result.addProperty("holdExited", hold.exited);
    }
    return result.deepCopy();
  }
}
