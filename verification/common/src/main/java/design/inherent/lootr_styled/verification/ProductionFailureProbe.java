package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.minecraft.client.GraphicPreparation;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/** Actual profile selection, domain refusal, retained feedback, and explicit healthy recovery. */
final class ProductionFailureProbe {
  private enum Phase {
    OPEN,
    INSPECT,
    SELECT,
    FAILED,
    FAILURE_IMAGE,
    RESTORE,
    RECOVER,
    DRAW,
    HIDDEN,
    CLOSED
  }

  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final ClientGraphicHost host;
  private final Path settings;
  private final byte[] original;
  private final BasicFileAttributes attributes;
  private final UUID generation;
  private final JsonObject result = new JsonObject();
  private final CompletableFuture<?> admission;
  private ClientEditor<ClientSettings> editor;
  private DraftRevision failedRevision;
  private ProductionPreparationAudit.Entry failed, recovery;
  private CompletableFuture<?> navigation;
  private CompletableFuture<design.inherent.reform.presentation.graphics.RenderLifetime.State>
      release;
  private volatile boolean drawn, captured;
  private boolean captureRequested, drawArmed;
  private int ticks, captureTicks;
  private Phase phase = Phase.OPEN;

  ProductionFailureProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    attributes = Files.readAttributes(settings, BasicFileAttributes.class);
    generation = PreviewProbeAccess.generation(PreviewProbeAccess.activePack());
    ProductionPreparationAudit.begin();
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    host =
        workflow.graphics(new GraphicLimits(16L << 20, 32L << 20, 16L << 20, 256, 128L << 20), 1);
    admission =
        WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"))
            .thenAccept(value -> editor = value);
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 300)
      throw new IllegalStateException("Failure preview timed out: " + observations());
    unchanged();
    if (!admission.isDone()) return null;
    admission.join();
    switch (phase) {
      case OPEN -> {
        if (!UiProbe.hasButton(workflow.screen(), "Tinted preview fixture")) return null;
        if (!candidate().equals(ClientSettings.DEFAULTS) || !empty())
          throw new IllegalStateException("Failure fixture did not begin with idle Default draft");
        press("Tinted preview fixture");
        next(Phase.INSPECT);
      }
      case INSPECT -> {
        if (!UiProbe.button(workflow.screen(), "Use for Models").active) return null;
        if (!candidate().equals(ClientSettings.DEFAULTS))
          throw new IllegalStateException("Profile inspection staged settings");
        press("Use for Models");
        next(Phase.SELECT);
      }
      case SELECT -> {
        var draft = candidate();
        if (!draft.models().toString().equals("lootr_styled_local:fixture")) return null;
        if (!draft.artwork().equals(ClientSettings.DEFAULT)
            || !draft.enabled()
            || draft.fresh() != ClientSettings.Fresh.AUTO)
          throw new IllegalStateException("Models choice changed an independent field");
        failedRevision = DraftRevision.of(editor.view().snapshot().orElseThrow());
        failed = prepare();
        next(Phase.FAILED);
      }
      case FAILED -> {
        if (!failed.attempt().completion().toCompletableFuture().isDone()) return null;
        var outcome = failed.attempt().completion().toCompletableFuture().join();
        var codes = new JsonArray();
        outcome.report().issues().forEach(issue -> codes.add(issue.code()));
        result.addProperty("failureState", outcome.state().name());
        result.add("failureCodes", codes);
        outcome.cause().ifPresent(value -> result.addProperty("failureCause", value.toString()));
        if (outcome.state() != ClientGraphicHost.State.REFUSED
            || outcome.report().issues().stream()
                .noneMatch(issue -> issue.code().equals("lootr_styled.preview.tint_unavailable")))
          throw new IllegalStateException(
              "Expected real tint refusal: " + outcome, outcome.cause().orElse(null));
        if (!empty()) return null;
        result.addProperty("specificDomainRefusal", true);
        result.addProperty(
            "failedPreparationLeavesNoGraphic",
            failed
                .view()
                .preparation()
                .filter(GraphicPreparation.Ready.class::isInstance)
                .isEmpty());
        next(Phase.FAILURE_IMAGE);
      }
      case FAILURE_IMAGE -> {
        if (!NativeBindings.unobscured(mc) || ++captureTicks < 8) return null;
        if (!captureRequested) {
          captureRequested = true;
          NativeWorld.screenshot(mc, "production-failure-tint", () -> captured = true);
        }
        if (!captured) return null;
        var narration = new StringBuilder();
        for (var child : UiProbe.children(workflow.screen()))
          if (child instanceof NarratableEntry entry)
            narration.append(NativeBindings.narration(entry)).append('\n');
        String feedback = narration.toString();
        result.addProperty("failureFeedback", feedback);
        boolean useful =
            feedback.contains("native tint provider") && feedback.contains("cannot be inferred");
        result.addProperty("readableFailureCause", useful);
        if (!useful) throw new IllegalStateException("Actual preview feedback omitted tint cause");
        if (!failedRevision.equals(DraftRevision.of(editor.view().snapshot().orElseThrow()))
            || !editor.isOpen()
            || NativeUi.current() != workflow.screen())
          throw new IllegalStateException("Refusal lost complete draft or owner");
        result.addProperty("failedDraftRetained", true);
        press("Default");
        next(Phase.RESTORE);
      }
      case RESTORE -> {
        if (!UiProbe.button(workflow.screen(), "Use for Models").active) return null;
        press("Use for Models");
        next(Phase.RECOVER);
      }
      case RECOVER -> {
        if (!candidate().equals(ClientSettings.DEFAULTS)) return null;
        if (ProductionPreparationAudit.requests() != 1 || !empty())
          throw new IllegalStateException("Repair prepared a graphic implicitly");
        recovery = prepare();
        if (recovery.view() != failed.view())
          throw new IllegalStateException("Recovery replaced the registered viewport");
        next(Phase.DRAW);
      }
      case DRAW -> {
        if (!recovery.attempt().completion().toCompletableFuture().isDone()) return null;
        var outcome = recovery.attempt().completion().toCompletableFuture().join();
        if (outcome.state() != ClientGraphicHost.State.READY)
          throw new IllegalStateException(
              "Default recovery did not prepare: " + outcome, outcome.cause().orElse(null));
        if (!drawArmed) {
          drawArmed = true;
          var graphic =
              ((GraphicPreparation.Ready) recovery.view().preparation().orElseThrow()).graphic();
          release = graphic.releaseCompletion().toCompletableFuture();
          GraphicDrawAudit.afterNextRenderedGraphic(
              value -> {
                if (value.state().outstandingUses() < 1 || !value.state().equals(graphic.state()))
                  throw new IllegalStateException("Recovery draw did not match its prepared lease");
                drawn = true;
              });
        }
        if (!drawn) return null;
        navigation =
            workflow
                .navigation()
                .open(new ClientDestination.Home())
                .completion()
                .thenAccept(
                    value -> {
                      if (value.outcome()
                          != design.inherent.reform.minecraft.client.NavigationAttempt.Outcome
                              .REVEALED)
                        throw new IllegalStateException("Recovery Home was not revealed");
                    })
                .toCompletableFuture();
        next(Phase.HIDDEN);
      }
      case HIDDEN -> {
        if (!navigation.isDone() || !release.isDone() || !empty()) return null;
        navigation.join();
        var state = release.join();
        if (!state.released()
            || state.outstandingUses() != 0
            || state.releaseFailure().isPresent()
            || ProductionPreparationAudit.requests() != 2
            || host.releaseFailureCount() != 0)
          throw new IllegalStateException("Recovered preview did not retire cleanly");
        result.addProperty("explicitRecoveryDrawAndRelease", true);
        result.addProperty("requests", ProductionPreparationAudit.requests());
        press("Done");
        next(Phase.CLOSED);
      }
      case CLOSED -> {
        if (workflow.isOpen() || editor.isOpen() || NativeUi.current() != parent) return null;
        if (!empty())
          throw new IllegalStateException("Closed failure fixture retained allocations");
        result.addProperty("sourceFileAndGenerationUnchanged", true);
        result.addProperty("closedCleanly", true);
        result.addProperty("nativeBytes", host.nativeOwnership().bytes());
        result.addProperty("nativeResources", host.nativeOwnership().resources());
        result.addProperty("sceneBytes", host.retainedOutput().bytes());
        result.addProperty("sceneResources", host.retainedOutput().resources());
        ProductionPreparationAudit.stop();
        return result;
      }
    }
    return null;
  }

  private ClientSettings candidate() {
    return editor.view().snapshot().orElseThrow().inspect().candidate().orElseThrow().value();
  }

  private ProductionPreparationAudit.Entry prepare() {
    int before = ProductionPreparationAudit.requests();
    press("Prepare draft preview");
    if (ProductionPreparationAudit.requests() != before + 1)
      throw new IllegalStateException("Native Prepare did not submit exactly one request");
    press("Show preview");
    return ProductionPreparationAudit.latest();
  }

  private void press(String label) {
    var control = UiProbe.button(workflow.screen(), label);
    if (!control.active) throw new IllegalStateException("Inactive native action: " + label);
    NativeBindings.press(control);
  }

  private boolean empty() {
    return host.nativeOwnership().bytes() == 0
        && host.nativeOwnership().resources() == 0
        && host.retainedOutput().bytes() == 0
        && host.retainedOutput().resources() == 0;
  }

  private void unchanged() throws Exception {
    var now = Files.readAttributes(settings, BasicFileAttributes.class);
    if (!Arrays.equals(original, Files.readAllBytes(settings))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !Objects.equals(attributes.fileKey(), now.fileKey())
        || !generation.equals(PreviewProbeAccess.generation(PreviewProbeAccess.activePack())))
      throw new IllegalStateException("Preview failure changed saved file or resources");
  }

  private void next(Phase value) {
    phase = value;
    ticks = 0;
  }

  String progress() {
    return phase.toString();
  }

  JsonObject observations() {
    var value = result.deepCopy();
    value.addProperty("phase", phase.name());
    value.addProperty("nativeBytes", host.nativeOwnership().bytes());
    value.addProperty("sceneBytes", host.retainedOutput().bytes());
    return value;
  }
}
