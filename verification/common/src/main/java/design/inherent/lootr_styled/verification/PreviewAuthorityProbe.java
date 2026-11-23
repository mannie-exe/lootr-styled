package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.*;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.owner.*;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.reform.presentation.graphics.*;
import design.inherent.reform.reference.*;
import design.inherent.reform.value.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Actual Styled preparation with a separately owned test file and controllable native gateway. */
final class PreviewAuthorityProbe {
  private final AtomicBoolean mayRead = new AtomicBoolean(true);
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final AtomicInteger rejected = new AtomicInteger();
  private final AtomicInteger prepared = new AtomicInteger();
  private final OwnerContext context;
  private final OwnerContext.Access access;
  private final ReferenceActions<String, String> references;
  private final ClientEditingWorkflow workflow;
  private final ClientEditor<ClientSettings> editor;
  private final ClientGraphicHost host;
  private final ClientGraphicHost.View<ClientSettings, Object> view;
  private final ClientGraphicProvider<ClientSettings, Object> provider;
  private final java.util.concurrent.Executor ownerExecutor;
  private final PreviewWorkerGate gate = new PreviewWorkerGate();
  private final JsonObject result = new JsonObject();
  private final Screen parent;
  private final Path file, runtimeFile;
  private final byte[] original, runtimeOriginal;
  private CompletableFuture<?> pending;
  private ClientGraphicHost.Attempt attempt;
  private PreparedGraphic lease;
  private AbstractWidget widget;
  private PreviewWorkerGate.Hold hold;
  private Object subject;
  private int stage, ticks, atRevocation;
  private long draws;
  private boolean closedAtDraw;

  String progress() {
    return stage + ":prepared=" + prepared.get();
  }

  PreviewAuthorityProbe(Minecraft mc) throws Exception {
    parent = NativeUi.current();
    runtimeFile = ClientRuntime.configuration().root().resolve("settings.json");
    runtimeOriginal = Files.readAllBytes(runtimeFile);
    var config = mc.gameDirectory.toPath().resolve("permission-owner/config");
    file = config.resolve("lootr_styled/settings.json");
    Files.createDirectories(file.getParent());
    // Explicit fixture initialization, before preview admission; never the production owner file.
    Files.writeString(file, ClientSettings.STARTER);
    original = Files.readAllBytes(file);
    ownerExecutor =
        action -> {
          if (!accepting.get()) {
            rejected.incrementAndGet();
            throw new RejectedExecutionException("The test editor gateway has stopped");
          }
          mc.execute(action);
        };
    context =
        new OwnerContext(
            ownerExecutor,
            ForkJoinPool.commonPool(),
            () -> {
              if (!mc.isSameThread())
                throw new IllegalStateException("Actual client thread required");
            },
            OwnerContext.Limits.STANDARD);
    access = context.access(mayRead::get, () -> false);
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    var referenceLimits = new ReferenceLimits(4096, 1048576, 64, 256, 4096);
    references =
        new ReferenceActions<>(
            context,
            access,
            StyleSettingsStore.PROFILES,
            ValueTypes.text(256),
            SnapshotPolicy.immutable(),
            referenceLimits);
    var entries =
        ClientRuntime.choices().stream()
            .map(
                c ->
                    new ReferenceIndex.Entry<>(
                        c.id().toString(),
                        c.id().toString(),
                        c.title(),
                        c.id() + " " + c.title(),
                        Map.of("Source", c.source())))
            .toList();
    references.bind(
        PreviewProbeAccess.generation(PreviewProbeAccess.activePack()).toString(),
        stamp ->
            new ReferenceIndex<>(
                stamp, ValueTypes.text(256), SnapshotPolicy.immutable(), referenceLimits, entries));
    var store = new StyleSettingsStore(config);
    var owner =
        FileOwner.stagedFile(
            context,
            store,
            32,
            FileOwner.Initialization.DEFER_MISSING,
            ApplicationEffects.root(
                ApplicationEffects.Timing.EXPLICIT_RELOAD,
                "Application belongs to the test owner"));
    var options =
        new ClientFormOptions<ClientSettings>(
            FormPresentation.EMPTY, Map.of(StyleSettingsStore.PROFILES, references));
    var form =
        ClientForm.of(
            StyleSettingsStore.DEFINITION.id(),
            Component.literal("Read-only preview control"),
            () ->
                new ClientForm.Availability(
                    true, false, Component.literal("Test-owned read-only file")),
            previous ->
                ClientEditors.open(
                    context,
                    access,
                    owner.open(access),
                    StyleSettingsStore.DEFINITION,
                    StyleSettingsStore.LIMITS,
                    actions ->
                        options.open(
                            previous,
                            Component.literal("Read-only preview control"),
                            Component.literal("Verification fixture"),
                            StyleSettingsStore.DEFINITION,
                            actions,
                            new SettingsScreen.SubmissionLabels(
                                Component.literal("Save"),
                                Component.literal("Save reviewed"),
                                Component.literal("Save and close")))),
            Optional.empty());
    workflow =
        ClientEditingWorkflow.open(
            parent, Component.literal("Preview authority control"), List.of(form));
    NativeUi.open(workflow.screen());
    workflow.show(StyleSettingsStore.DEFINITION.id());
    editor = workflow.editor(StyleSettingsStore.DEFINITION);
    pending =
        DocumentActions.result(editor.actions().ensureLoaded(EntryPoint.GUI)).toCompletableFuture();
    provider = provider(limits);
    host = workflow.graphics(limits, 1);
    view = host.open(editor, provider);
  }

  @SuppressWarnings("unchecked") // The bounded provider codec is the sole subject constructor.
  private ClientGraphicProvider<ClientSettings, Object> provider(GraphicLimits limits)
      throws Exception {
    var ctor =
        Class.forName("design.inherent.lootr_styled.client.StyleGraphicProvider")
            .getDeclaredConstructor(GraphicLimits.class);
    ctor.setAccessible(true);
    var actual = (ClientGraphicProvider<ClientSettings, Object>) ctor.newInstance(limits);
    return new ClientGraphicProvider<>() {
      public DocumentId id() {
        return actual.id();
      }

      public ValueType<Object> subjectType() {
        return actual.subjectType();
      }

      public GraphicLimits limits() {
        return actual.limits();
      }

      public ReferenceStamp resourceStamp() {
        return actual.resourceStamp();
      }

      public CompletionStage<GraphicScenePreparation> prepare(
          GraphicRequest<ClientSettings, Object> request, ClientGraphicWork work) {
        return actual
            .prepare(request, work)
            .thenCompose(
                value ->
                    work.onWorker(
                        () -> {
                          if (!(value instanceof GraphicScenePreparation.Ready))
                            throw new IllegalStateException(
                                "The actual domain provider was not ready");
                          if (!request.input().candidate().value().equals(ClientSettings.DEFAULTS))
                            throw new IllegalStateException(
                                "Read-only capture lost part of its saved candidate");
                          prepared.incrementAndGet();
                          gate.run();
                          return value;
                        }));
      }
    };
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 500)
      throw new IllegalStateException("Preview authority control timed out at " + stage);
    if (pending != null) {
      if (!pending.isDone()) return null;
      pending.join();
      pending = null;
    }
    if (stage == 0) {
      if (editor
          .form()
          .fieldControl(design.inherent.reform.definition.FieldPath.ROOT.field("models"))
          .isEmpty()) return null;
      try {
        access.require(OwnerContext.Right.WRITE);
        throw new IllegalStateException("Test owner unexpectedly grants WRITE");
      } catch (PolicyRefusal denied) {
        result.addProperty(
            "actualWriteDenied",
            denied.report().issues().stream().anyMatch(i -> i.code().equals("owner.permission")));
      }
      subject =
          provider
              .subjectType()
              .codec()
              .parse(
                  com.mojang.serialization.JsonOps.INSTANCE,
                  com.google.gson.JsonParser.parseString(
                      "{\"container\":\"BARREL\",\"looted\":true,\"open\":true,\"lid\":1}"))
              .getOrThrow();
      try {
        attempt = view.request(subject);
      } catch (PolicyRefusal waiting) {
        if (waiting.report().issues().isEmpty()
            || waiting.report().issues().stream()
                .anyMatch(i -> !i.code().equals("preview.input_unaccepted"))) throw waiting;
        result.addProperty("initialInputGuardObserved", true);
        return null;
      }
      stage = 1;
      ticks = 0;
      return null;
    }
    if (stage == 1 || stage == 7) {
      if (!ready()) return null;
      widget = view.widget(Component.literal("Actual provider authority control"));
      widget.setPosition(12, 12);
      widget.setWidth(220);
      widget.setHeight(180);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      draws = GraphicDrawAudit.state().rendered();
      stage = stage == 1 ? 2 : 8;
      ticks = 0;
      return null;
    }
    if (stage == 2) {
      remount();
      if (ticks < 8 || GraphicDrawAudit.state().rendered() <= draws) return null;
      result.addProperty("readOnlyDomainDraw", true);
      atRevocation = prepared.get();
      mayRead.set(false);
      stage = 3;
      ticks = 0;
      return null;
    }
    if (stage == 3) {
      if (!released()) return null;
      if (view.snapshot().state() == ClientGraphicHost.State.READY
          || prepared.get() != atRevocation)
        throw new IllegalStateException(
            "Revoked read access retained or re-prepared a current preview");
      result.addProperty("permissionLossRetiresReadyLease", true);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, false);
      try {
        view.request(subject);
        throw new IllegalStateException("Revoked preview request was admitted");
      } catch (PolicyRefusal denied) {
        if (denied.report().issues().stream().noneMatch(i -> i.code().equals("owner.permission")))
          throw denied;
        result.addProperty("revokedReadRequestRefused", true);
      }
      mayRead.set(true);
      hold = gate.arm();
      attempt = view.request(subject);
      stage = 4;
      ticks = 0;
      return null;
    }
    if (stage == 4) {
      if (!hold.entered) return null;
      mayRead.set(false);
      stage = 5;
      ticks = 0;
      return null;
    }
    if (stage == 5) {
      if (ticks < 8) return null;
      if (hold.exited
          || attempt.completion().toCompletableFuture().isDone()
          || view.snapshot().state() == ClientGraphicHost.State.READY)
        throw new IllegalStateException(
            "Permission loss claimed early completion or mounted held output");
      result.addProperty("permissionLossWaitsForEnteredWork", true);
      hold.release();
      stage = 6;
      ticks = 0;
      return null;
    }
    if (stage == 6) {
      if (!attempt.completion().toCompletableFuture().isDone() || !hold.exited) return null;
      var outcome = attempt.completion().toCompletableFuture().join();
      if (outcome.state() != ClientGraphicHost.State.CANCELLED
          && outcome.state() != ClientGraphicHost.State.REFUSED)
        throw new IllegalStateException("Revoked output mounted late");
      noPayload();
      result.addProperty("revokedEnteredOutputDiscarded", true);
      result.addProperty("revokedTerminal", outcome.state().name());
      mayRead.set(true);
      attempt = view.request(subject);
      stage = 7;
      ticks = 0;
      return null;
    }
    if (stage == 8) {
      remount();
      if (ticks < 8 || closedAtDraw) return null;
      GraphicDrawAudit.afterNextRendered(
          () -> {
            if (closedAtDraw) return;
            result.addProperty("pendingUsesAtEditorRetirement", lease.state().outstandingUses());
            if (lease.state().outstandingUses() < 1)
              throw new IllegalStateException("No submitted native use at context retirement");
            accepting.set(false);
            try {
              ownerExecutor.execute(
                  () -> {
                    throw new IllegalStateException("Stopped executor ran work");
                  });
              throw new IllegalStateException("Stopped executor accepted work");
            } catch (RejectedExecutionException expected) {
              result.addProperty("oldExecutorActuallyRejects", true);
            }
            context.close();
            closedAtDraw = true;
          });
      stage = 9;
      ticks = 0;
      return null;
    }
    if (stage == 9) {
      if (!closedAtDraw || !released()) return null;
      if (editor.isOpen() || context.isOpen() || rejected.get() != 1)
        throw new IllegalStateException(
            "Editor authority revived or cleanup used the stopped executor");
      result.addProperty("retiredEditorExecutorCannotBlockGpuRelease", true);
      result.addProperty("unexpectedStoppedExecutorSubmissions", rejected.get() - 1);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, false);
      view.close();
      host.close();
      workflow.close();
      references.close();
      NativeUi.open(parent);
      if (!Arrays.equals(original, Files.readAllBytes(file))
          || !Arrays.equals(runtimeOriginal, Files.readAllBytes(runtimeFile)))
        throw new IllegalStateException("Authority preview control wrote a settings file");
      result.addProperty("testAndRuntimeSettingsUnchanged", true);
      result.addProperty("preparations", prepared.get());
      if (prepared.get() != 3)
        throw new IllegalStateException("Preview was prepared without an explicit request");
      result.addProperty(
          "scope",
          "Separate test-owned file/access/executor with the actual Styled provider; not production local-file permission policy");
      return result;
    }
    return null;
  }

  private boolean ready() {
    if (!attempt.completion().toCompletableFuture().isDone()) return false;
    var outcome = attempt.completion().toCompletableFuture().join();
    if (outcome.state() != ClientGraphicHost.State.READY)
      throw new IllegalStateException(
          "Actual authority provider failed: " + outcome, outcome.cause().orElse(null));
    lease = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
    return true;
  }

  private void remount() {
    if (!workflow.screen().children().contains(widget))
      SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
    if (view.failure().isPresent())
      throw new IllegalStateException(
          "Authority preview draw failed", view.failure().orElseThrow());
  }

  private boolean released() {
    if (!lease.releaseCompletion().toCompletableFuture().isDone()) return false;
    if (!lease.releaseCompletion().toCompletableFuture().join().released())
      throw new IllegalStateException("Authority preview cleanup failed");
    noPayload();
    return true;
  }

  private void noPayload() {
    if (host.nativeOwnership().bytes() != 0 || host.retainedOutput().bytes() != 0)
      throw new IllegalStateException("Revoked preview payload remains retained");
  }
}
