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
  private final boolean selectionStates;
  private final java.util.Map<String, String> baselineVisuals;
  private final java.util.Map<String, String> disabledVisuals = new java.util.HashMap<>();

  private record Selection(boolean enabled, String models, String artwork) {}

  private record Subject(String container, boolean looted, boolean open) {}

  private static final java.util.List<Subject> SUBJECTS =
      java.util.List.of(
          new Subject("CHEST", false, false), new Subject("CHEST", true, false),
          new Subject("TRAPPED_CHEST", false, false), new Subject("TRAPPED_CHEST", true, false),
          new Subject("BARREL", false, false), new Subject("BARREL", false, true),
          new Subject("BARREL", true, false), new Subject("BARREL", true, true));

  private static final String MISSING_MODELS = "lootr_styled_missing:models";
  private static final String MISSING_ARTWORK = "lootr_styled_missing:artwork";
  private static final java.util.List<Selection> STATES =
      java.util.List.of(
          new Selection(
              false, ClientSettings.DEFAULT.toString(), ClientSettings.DEFAULT.toString()),
          new Selection(false, MISSING_MODELS, MISSING_ARTWORK),
          new Selection(true, MISSING_MODELS, ClientSettings.DEFAULT.toString()),
          new Selection(true, ClientSettings.DEFAULT.toString(), MISSING_ARTWORK),
          new Selection(true, MISSING_MODELS, MISSING_ARTWORK),
          new Selection(
              true, ClientSettings.DEFAULT.toString(), ClientSettings.DEFAULT.toString()));
  private final ClientEditingWorkflow workflow;
  private ClientEditor<ClientSettings> editor;
  private final ClientGraphicHost host;
  private ClientGraphicHost.View<ClientSettings, Object> view;
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
    this(null);
  }

  PreviewCombinationProbe(JsonObject baseline) throws Exception {
    this.selectionStates = baseline != null;
    var visuals = new java.util.HashMap<String, String>();
    if (baseline != null) {
      if (!baseline.get("complete").getAsBoolean() || baseline.get("failures").getAsInt() != 0)
        throw new IllegalArgumentException("The visual baseline did not complete");
      for (var entry : baseline.getAsJsonArray("rows")) {
        var row = entry.getAsJsonObject();
        visuals.put(
            visualKey(
                row.get("models").getAsString(),
                row.get("artwork").getAsString(),
                row.get("container").getAsString(),
                row.get("looted").getAsBoolean(),
                row.get("open").getAsBoolean()),
            row.getAsJsonObject("prepared").get("visualFingerprint").getAsString());
      }
      if (visuals.size() != 4 * SUBJECTS.size())
        throw new IllegalArgumentException("Incomplete visual baseline");
    }
    baselineVisuals = java.util.Map.copyOf(visuals);
    settings = ClientRuntime.configuration().root().resolve("settings.json");
    original = Files.readAllBytes(settings);
    workflow = StylePreferences.open(NativeUi.current());
    NativeUi.open(workflow.screen());
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    provider = observedProvider(limits);
    host = workflow.graphics(limits, 1);
    pending =
        WorkflowReveal.document(workflow, StyleSettingsStore.DEFINITION)
            .thenAccept(
                opened -> {
                  editor = opened;
                  view = host.open(opened, provider);
                });
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
                  if (value.enabled() != enabled()
                      || !value.models().toString().equals(models())
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
                    String visual = visualFingerprint(ready.graphic().scene());
                    data.addProperty("visualFingerprint", visual);
                    prepared = data; // Preserve actual render inputs if a comparison refuses.
                    if (selectionStates) {
                      if (!enabled()) {
                        // Disabled styling also disables Fresh adaptation; an enabled inherited
                        // profile with Fresh AUTO is not its native visual reference.
                        String key = container() + "|" + looted() + "|" + open();
                        if (index / SUBJECTS.size() == 0) {
                          disabledVisuals.put(key, visual);
                          data.addProperty("visualComparison", "Disabled reference captured");
                        } else {
                          data.addProperty("expectedVisualFingerprint", disabledVisuals.get(key));
                          if (!visual.equals(disabledVisuals.get(key)))
                            throw new IllegalStateException(
                                "Inactive retained profile IDs changed the disabled container");
                          data.addProperty("visualComparison", "Disabled reference matched");
                        }
                      } else {
                        String effectiveModels =
                            models().equals(MISSING_MODELS)
                                ? ClientSettings.VANILLA.toString()
                                : models();
                        String effectiveArtwork =
                            artwork().equals(MISSING_ARTWORK)
                                ? ClientSettings.VANILLA.toString()
                                : artwork();
                        String expectedVisual =
                            baselineVisuals.get(
                                visualKey(
                                    effectiveModels,
                                    effectiveArtwork,
                                    container(),
                                    looted(),
                                    open()));
                        data.addProperty("expectedVisualFingerprint", expectedVisual);
                        if (!visual.equals(expectedVisual))
                          throw new IllegalStateException(
                              "Prepared geometry/material/pixels differ from the effective inherited or Default baseline");
                        data.addProperty("visualComparison", "Built-in reference matched");
                      }
                    }
                    for (var family : java.util.List.of("models", "artwork")) {
                      var expected = family.equals("models") ? value.models() : value.artwork();
                      var effective =
                          ready.graphic().facts().stream()
                              .filter(
                                  f ->
                                      f.id()
                                          .toString()
                                          .equals("lootr_styled:preview/" + family + "_effective"))
                              .findFirst()
                              .orElseThrow();
                      boolean missing = expected.toString().startsWith("lootr_styled_missing:");
                      String resolved =
                          !enabled()
                              ? "None; styling is disabled"
                              : missing ? ClientSettings.VANILLA.toString() : expected.toString();
                      if (!effective.value().equals(resolved))
                        throw new IllegalStateException(
                            "Effective profile fact differs from compiled selection");
                      var source =
                          ready.graphic().facts().stream()
                              .filter(
                                  f ->
                                      f.id()
                                          .toString()
                                          .equals(
                                              "lootr_styled:preview/" + family + "_profile_source"))
                              .findFirst();
                      if (!enabled()
                          ? source.isPresent()
                          : source.isEmpty() || !source.orElseThrow().value().equals("Built-in"))
                        throw new IllegalStateException("Built-in profile source was lost");
                      var status =
                          ready.graphic().facts().stream()
                              .filter(
                                  f ->
                                      f.id()
                                          .toString()
                                          .equals("lootr_styled:preview/" + family + "_resolution"))
                              .findFirst()
                              .orElseThrow()
                              .value();
                      if ((!enabled() && !status.contains("disabled"))
                          || (enabled() && missing && !status.contains("unavailable")))
                        throw new IllegalStateException(
                            "Disabled or missing selection lost its resolution: " + status);
                    }
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
                            && (!enabled()
                                || value.models().equals(ClientSettings.VANILLA)
                                || value.models().toString().equals(MISSING_MODELS));
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
      if (selectionStates) {
        pending = edit("enabled", Boolean.toString(enabled()));
        stage = -1;
        return null;
      }
      pending = edit("models", models());
      stage = 1;
      return null;
    }
    if (stage == -1) {
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
      json.addProperty("looted", looted());
      json.addProperty("open", open());
      json.addProperty("lid", looted() ? 1 : 0);
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
      row.addProperty("enabled", enabled());
      if (selectionStates) row.addProperty("selectionCase", index / SUBJECTS.size());
      row.addProperty("artwork", artwork());
      row.addProperty("container", container());
      row.addProperty("open", open());
      row.addProperty("looted", looted());
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
      if (++index < (selectionStates ? STATES.size() * SUBJECTS.size() : 4 * SUBJECTS.size())) {
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
    if (selectionStates) return STATES.get(index / SUBJECTS.size()).models();
    return (index / (SUBJECTS.size() * 2) == 0 ? ClientSettings.DEFAULT : ClientSettings.VANILLA)
        .toString();
  }

  private String artwork() {
    if (selectionStates) return STATES.get(index / SUBJECTS.size()).artwork();
    return (index / SUBJECTS.size() % 2 == 0 ? ClientSettings.DEFAULT : ClientSettings.VANILLA)
        .toString();
  }

  private String container() {
    return SUBJECTS.get(index % SUBJECTS.size()).container();
  }

  private boolean looted() {
    return SUBJECTS.get(index % SUBJECTS.size()).looted();
  }

  private boolean open() {
    return SUBJECTS.get(index % SUBJECTS.size()).open();
  }

  private boolean enabled() {
    return !selectionStates || STATES.get(index / SUBJECTS.size()).enabled();
  }

  private static String visualKey(
      String models, String artwork, String container, boolean looted, boolean open) {
    return models + "|" + artwork + "|" + container + "|" + looted + "|" + open;
  }

  /** Copied render input only: omit profile-resolution facts, which intentionally differ. */
  private static String visualFingerprint(GraphicScene scene) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      java.util.function.Consumer<String> text =
          value -> {
            digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
          };
      scene.mesh().vertices().forEach(v -> text.accept(v.toString()));
      scene.mesh().triangles().forEach(t -> text.accept(t.toString()));
      var order = java.util.Comparator.comparing(DocumentId::toString);
      scene.materials().entrySet().stream()
          .sorted(java.util.Map.Entry.comparingByKey(order))
          .forEach(e -> text.accept(e.getKey() + "=" + e.getValue()));
      scene.textures().entrySet().stream()
          .sorted(java.util.Map.Entry.comparingByKey(order))
          .forEach(
              e -> {
                var pixels = e.getValue();
                text.accept(e.getKey() + ":" + pixels.width() + "x" + pixels.height());
                int[] argb = pixels.argb();
                var bytes =
                    java.nio.ByteBuffer.allocate(Math.multiplyExact(argb.length, Integer.BYTES));
                bytes.asIntBuffer().put(argb);
                digest.update(bytes.array());
              });
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
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
            .resolve(
                selectionStates
                    ? "lootr-styled-selection-states-report.json"
                    : "lootr-styled-combinations-report.json"),
        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
    return result;
  }
}
