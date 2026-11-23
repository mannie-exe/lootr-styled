package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftEdit;
import design.inherent.reform.minecraft.client.*;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.presentation.graphics.*;
import design.inherent.reform.reference.ReferenceStamp;
import design.inherent.reform.value.ValueType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Actual unsaved built-in combinations through the packaged default provider and shared host. */
final class PreviewCombinationProbe {
  private final ClientEditingWorkflow workflow;
  private final ClientEditor<ClientSettings> editor;
  private final ClientGraphicHost host;
  private final ClientGraphicHost.View<ClientSettings, Object> view;
  private final ClientGraphicProvider<ClientSettings, Object> provider;
  private final JsonArray rows = new JsonArray();
  private final Path settings;
  private final byte[] original;
  private CompletableFuture<?> pending;
  private ClientGraphicHost.Attempt attempt;
  private PreparedGraphic lease;
  private AbstractWidget widget;
  private volatile JsonObject prepared;
  private int stage, index, ticks, failures;
  private long draws;

  PreviewCombinationProbe() throws Exception {
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    workflow = StylePreferences.open(NativeUi.current());
    NativeUi.open(workflow.screen());
    workflow.show(StyleSettingsStore.DEFINITION.id());
    editor = workflow.editor(StyleSettingsStore.DEFINITION);
    pending =
        DocumentActions.result(editor.actions().ensureLoaded(EntryPoint.GUI)).toCompletableFuture();
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    provider = observedProvider(limits);
    host = workflow.graphics(limits, 1);
    view = host.open(editor, provider);
  }

  @SuppressWarnings("unchecked") // Only the actual bounded subject codec constructs parameters.
  private ClientGraphicProvider<ClientSettings, Object> observedProvider(GraphicLimits limits)
      throws Exception {
    var constructor =
        Class.forName("design.inherent.lootr_styled.client.StyleGraphicProvider")
            .getDeclaredConstructor(GraphicLimits.class);
    constructor.setAccessible(true);
    var actual = (ClientGraphicProvider<ClientSettings, Object>) constructor.newInstance(limits);
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
            .thenApply(
                result -> {
                  var value = request.input().candidate().value();
                  if (!value.models().toString().equals(models())
                      || !value.artwork().toString().equals(artwork()))
                    throw new IllegalStateException(
                        "Preview did not capture the complete edited selection");
                  if (result instanceof GraphicScenePreparation.Ready ready) {
                    var data = new JsonObject();
                    var mesh = ready.graphic().scene().mesh();
                    data.addProperty("triangles", mesh.triangles().size());
                    data.addProperty(
                        "aoTriangles",
                        mesh.triangles().stream()
                            .filter(t -> t.lighting().ambientOcclusion())
                            .count());
                    data.addProperty(
                        "directionalTriangles",
                        mesh.triangles().stream()
                            .filter(
                                t -> t.lighting().shading() == GraphicLighting.Shading.DIRECTIONAL)
                            .count());
                    var facts = new JsonArray();
                    for (var fact : ready.graphic().facts()) {
                      var f = new JsonObject();
                      f.addProperty("id", fact.id().toString());
                      f.addProperty("title", fact.title());
                      f.addProperty("value", fact.value());
                      facts.add(f);
                    }
                    data.add("facts", facts);
                    if (data.get("aoTriangles").getAsInt() != 0)
                      throw new IllegalStateException("Reference lighting still requests world AO");
                    boolean nativeAo =
                        actual
                                .subjectType()
                                .codec()
                                .encodeStart(
                                    com.mojang.serialization.JsonOps.INSTANCE, request.subject())
                                .getOrThrow()
                                .getAsJsonObject()
                                .get("container")
                                .getAsString()
                                .equals("BARREL")
                            && value.models().equals(ClientSettings.VANILLA);
                    var sourceAo =
                        ready.graphic().facts().stream()
                            .filter(
                                f ->
                                    f.id()
                                        .toString()
                                        .equals("lootr_styled:preview/native_ambient_occlusion"))
                            .findFirst()
                            .orElseThrow();
                    if (nativeAo != sourceAo.value().startsWith("Requested on "))
                      throw new IllegalStateException(
                          "Native AO source fact was lost: " + sourceAo);
                    if (ready.graphic().facts().stream()
                        .noneMatch(
                            f ->
                                f.id().toString().equals("lootr_styled:preview/reference_lighting")
                                    && f.value()
                                        .contains("world ambient occlusion is not simulated")))
                      throw new IllegalStateException("Reference lighting is not disclosed");
                    prepared = data;
                  }
                  return result;
                });
      }
    };
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (++ticks > 1000) throw new IllegalStateException("Combination probe timed out at " + stage);
    if (pending != null) {
      if (!pending.isDone()) return null;
      pending.join();
      pending = null;
    }
    if (stage == 0) {
      if (editor.form().fieldControl(FieldPath.ROOT.field("models")).isEmpty()) return null;
      pending = edit("models", models());
      stage = 1;
      return null;
    }
    if (stage == 1) {
      pending = edit("artwork", artwork());
      stage = 2;
      ticks = 0;
      return null;
    }
    if (stage == 2) {
      if (ticks < 4) return null;
      var json = new JsonObject();
      json.addProperty("container", container());
      json.addProperty("looted", index % 2 == 1);
      json.addProperty("open", index % 2 == 1);
      json.addProperty("lid", index % 2 == 1 ? 1 : 0);
      prepared = null;
      var subject =
          provider
              .subjectType()
              .codec()
              .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
              .getOrThrow();
      view.visible(true);
      attempt = view.request(subject);
      stage = 3;
      return null;
    }
    if (stage == 3) {
      if (!attempt.completion().toCompletableFuture().isDone()) return null;
      var outcome = attempt.completion().toCompletableFuture().join();
      var row = new JsonObject();
      row.addProperty("models", models());
      row.addProperty("artwork", artwork());
      row.addProperty("container", container());
      row.addProperty("open", index % 2 == 1);
      row.addProperty("state", outcome.state().name());
      row.addProperty("report", outcome.report().toString());
      outcome.cause().ifPresent(cause -> row.addProperty("cause", cause.toString()));
      if (prepared != null) row.add("prepared", prepared);
      rows.add(row);
      if (outcome.state() != ClientGraphicHost.State.READY) {
        failures++;
        write(false);
        view.visible(false);
        stage = 5;
        return null;
      }
      write(false);
      lease = ((GraphicPreparation.Ready) view.preparation().orElseThrow()).graphic();
      widget = view.widget(Component.literal("Built-in appearance preview"));
      widget.setPosition(12, 12);
      widget.setWidth(220);
      widget.setHeight(180);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      draws = GraphicDrawAudit.state().rendered();
      stage = 4;
      ticks = 0;
      return null;
    }
    if (stage == 4) {
      if (!workflow.screen().children().contains(widget))
        SharedGraphicHostProbe.mount(workflow.screen(), widget, true);
      if (view.failure().isPresent())
        throw new IllegalStateException("Combination draw failed", view.failure().orElseThrow());
      if (ticks < 4) return null;
      if (GraphicDrawAudit.state().rendered() <= draws)
        throw new IllegalStateException("Combination widget did not draw");
      rows.get(rows.size() - 1).getAsJsonObject().addProperty("drawn", true);
      SharedGraphicHostProbe.mount(workflow.screen(), widget, false);
      view.visible(false);
      stage = 5;
      return null;
    }
    if (stage == 5) {
      if (lease != null && !lease.releaseCompletion().toCompletableFuture().isDone()) return null;
      if (lease != null && !lease.releaseCompletion().toCompletableFuture().join().released())
        throw new IllegalStateException("Combination lease did not release");
      if (host.nativeOwnership().bytes() != 0 || host.retainedOutput().bytes() != 0)
        throw new IllegalStateException("Combination payload leaked");
      rows.get(rows.size() - 1).getAsJsonObject().addProperty("retired", true);
      lease = null;
      if (++index < 16) {
        stage = 0;
        ticks = 0;
        return null;
      }
      view.close();
      host.close();
      workflow.close();
      if (!java.util.Arrays.equals(original, Files.readAllBytes(settings)))
        throw new IllegalStateException("Preview changed saved settings");
      var result = write(true);
      if (failures > 0)
        throw new IllegalStateException("Built-in appearance combinations refused: " + failures);
      return result;
    }
    return null;
  }

  private String models() {
    return (index / 8 == 0 ? ClientSettings.DEFAULT : ClientSettings.VANILLA).toString();
  }

  private String artwork() {
    return (index / 4 % 2 == 0 ? ClientSettings.DEFAULT : ClientSettings.VANILLA).toString();
  }

  private String container() {
    return switch (index % 4) {
      case 0 -> "CHEST";
      case 1 -> "TRAPPED_CHEST";
      default -> "BARREL";
    };
  }

  private CompletableFuture<?> edit(String name, String value) {
    var snapshot = editor.view().snapshot().orElseThrow();
    return DocumentActions.result(
            editor
                .actions()
                .edit(
                    EntryPoint.GUI,
                    new DraftEdit(
                        snapshot.target(FieldPath.ROOT.field(name)), new DraftEdit.SetRaw(value))))
        .toCompletableFuture();
  }

  private JsonObject write(boolean complete) throws Exception {
    var result = new JsonObject();
    result.add("rows", rows);
    result.addProperty("complete", complete);
    result.addProperty("failures", failures);
    result.addProperty(
        "settingsUnchanged", java.util.Arrays.equals(original, Files.readAllBytes(settings)));
    Files.writeString(
        Minecraft.getInstance()
            .gameDirectory
            .toPath()
            .resolve("lootr-styled-combinations-report.json"),
        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
    return result;
  }
}
