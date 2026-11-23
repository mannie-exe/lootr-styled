package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftEdit;
import design.inherent.reform.minecraft.client.*;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Leave an actual prepared view open when requesting normal Minecraft stop. */
final class RendererShutdownProbe {
  private final ClientEditingWorkflow workflow;
  private ClientEditor<ClientSettings> editor;
  private final ClientGraphicHost host;
  private final PreviewProbeAccess.HostProvider provider;
  private ClientGraphicHost.View<ClientSettings, Object> view;
  private final Path settings;
  private final byte[] originalSettings;
  private CompletableFuture<?> pending;
  private ClientGraphicHost.Attempt attempt;
  private PreparedGraphic lease;
  private AbstractWidget widget;
  private int stage, ticks, settled;
  private boolean armed;

  RendererShutdownProbe() throws Exception {
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    originalSettings = Files.readAllBytes(settings);
    workflow = StylePreferences.open(NativeUi.current());
    NativeUi.open(workflow.screen());
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    provider = PreviewProbeAccess.hostProvider(limits);
    host = workflow.graphics(limits, 1);
    pending =
        WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION)
            .thenAccept(
                opened -> {
                  editor = opened;
                  view = host.open(opened, provider.provider());
                });
  }

  void tick(Minecraft mc) {
    if (++ticks > 400)
      throw new IllegalStateException("Live renderer shutdown control timed out at " + stage);
    if (pending != null) {
      if (!pending.isDone()) return;
      pending.join();
      pending = null;
    }
    if (stage == 0) {
      if (editor.form().fieldControl(FieldPath.ROOT.field("artwork")).isEmpty()) return;
      var snapshot = editor.view().snapshot().orElseThrow();
      pending =
          DocumentActions.result(
                  editor
                      .actions()
                      .edit(
                          EntryPoint.GUI,
                          new DraftEdit(
                              snapshot.target(FieldPath.ROOT.field("artwork")),
                              new DraftEdit.SetRaw(ClientSettings.VANILLA.toString()))))
              .toCompletableFuture();
      stage = 1;
      return;
    }
    if (stage == 1) {
      if (++settled < 4) return;
      var subject =
          provider
              .provider()
              .subjectType()
              .codec()
              .parse(
                  com.mojang.serialization.JsonOps.INSTANCE,
                  com.google.gson.JsonParser.parseString(
                      "{\"container\":\"BARREL\",\"looted\":true,\"open\":true,\"lid\":1}"))
              .getOrThrow();
      attempt = view.request(subject);
      stage = 2;
      return;
    }
    if (stage == 2) {
      if (!attempt.completion().toCompletableFuture().isDone()) return;
      var outcome = attempt.completion().toCompletableFuture().join();
      if (outcome.state() != ClientGraphicHost.State.READY)
        throw new IllegalStateException("Shutdown positive control did not prepare: " + outcome);
      lease = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
      widget = view.widget(Component.literal("Live renderer shutdown control"));
      widget.setPosition(12, 12);
      widget.setWidth(220);
      widget.setHeight(180);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      ticks = 0;
      stage = 3;
      return;
    }
    if (stage == 3) {
      if (!workflow.screen().children().contains(widget))
        SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      if (view.failure().isPresent())
        throw new IllegalStateException(
            "Live shutdown preview could not draw", view.failure().orElseThrow());
      if (ticks >= 8 && !armed) {
        armed = true;
        GraphicDrawAudit.afterNextRendered(
            () -> {
              var initial = new JsonObject();
              initial.addProperty("workflowOpenAtStop", workflow.isOpen());
              initial.addProperty("editorOpenAtStop", editor.isOpen());
              initial.addProperty("pendingUsesAtStop", lease.state().outstandingUses());
              initial.addProperty("nativeBytesAtStop", host.nativeOwnership().bytes());
              initial.addProperty("sceneBytesAtStop", host.retainedOutput().bytes());
              if (!workflow.isOpen()
                  || !editor.isOpen()
                  || lease.state().outstandingUses() < 1
                  || host.nativeOwnership().bytes() == 0
                  || host.retainedOutput().bytes() == 0)
                throw new IllegalStateException(
                    "Shutdown was not requested with a live submitted preview");
              NativeProbe.requestLiveShutdown(
                  mc,
                  host,
                  lease,
                  settings,
                  originalSettings,
                  initial,
                  workflow::isOpen,
                  editor::isOpen);
            });
      }
    }
  }
}
