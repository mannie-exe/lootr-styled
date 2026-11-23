package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

/** Observe the real registered task's submitted graphic; never mount or close a substitute. */
final class ProductionShutdownProbe {
  private final ClientEditingWorkflow workflow;
  private final ClientGraphicHost host;
  private final Path settings;
  private final byte[] original;
  private final CompletableFuture<?> admission;
  private ClientEditor<ClientSettings> editor;
  private int ticks;
  private boolean requested;

  ProductionShutdownProbe() throws Exception {
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    workflow = StylePreferences.open(NativeUi.current());
    NativeUi.open(workflow.screen());
    host =
        workflow.graphics(
            new GraphicLimits(
                16L * 1024 * 1024, 32L * 1024 * 1024, 16L * 1024 * 1024, 256, 128L * 1024 * 1024),
            1);
    admission =
        WorkflowReveal.view(
                workflow,
                StyleSettingsStore.DEFINITION,
                DestinationId.parse("lootr_styled:profiles_page"))
            .thenAccept(value -> editor = value);
  }

  void tick(Minecraft mc) {
    if (++ticks > 400) throw new IllegalStateException("Production shutdown preview timed out");
    if (!admission.isDone()) return;
    admission.join();
    if (requested) return;
    var controls = new ArrayList<GuiEventListener>(workflow.screen().children());
    for (int i = 0; i < controls.size(); i++)
      if (controls.get(i) instanceof ContainerEventHandler container)
        controls.addAll(container.children());
    var prepare =
        controls.stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals("Prepare draft preview"))
            .findFirst();
    if (prepare.isEmpty() || !prepare.orElseThrow().active) return;
    var viewport =
        controls.stream()
            .filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals("Draft preview"))
            .findFirst()
            .orElseThrow();
    if (host.nativeOwnership().bytes() != 0 || host.retainedOutput().bytes() != 0)
      throw new IllegalStateException("Production shutdown task prepared before request");
    if (!UiProbe.focusControl(workflow.screen(), viewport))
      throw new IllegalStateException("Production shutdown viewport lost its native focus path");
    requested = true;
    GraphicDrawAudit.afterNextRenderedGraphic(
        graphic -> {
          var state = graphic.state();
          if (!workflow.isOpen()
              || !editor.isOpen()
              || state.outstandingUses() < 1
              || host.nativeOwnership().bytes() == 0
              || host.retainedOutput().bytes() == 0)
            throw new IllegalStateException(
                "Production stop lacks a live submitted native graphic");
          var initial = new JsonObject();
          initial.addProperty("productionTask", true);
          initial.addProperty("workflowOpenAtStop", workflow.isOpen());
          initial.addProperty("editorOpenAtStop", editor.isOpen());
          initial.addProperty("pendingUsesAtStop", state.outstandingUses());
          initial.addProperty("nativeBytesAtStop", host.nativeOwnership().bytes());
          initial.addProperty("sceneBytesAtStop", host.retainedOutput().bytes());
          NativeProbe.requestLiveShutdown(
              mc,
              host,
              graphic.releaseCompletion(),
              settings,
              original,
              initial,
              workflow::isOpen,
              editor::isOpen);
        });
    NativeBindings.press(prepare.orElseThrow());
  }
}
