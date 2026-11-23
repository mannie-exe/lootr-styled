package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BarrelPreviewInputsTest {
  private static final String NATIVE =
      """
      {"textures":{"side":"minecraft:block/barrel_side","top":"minecraft:block/barrel_top"},
       "elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
         "up":{"texture":"#top"},"north":{"texture":"#side"}}}]}
      """;

  static final class Memory implements ResourceAccess {
    final Map<ResourceId, Source> sources = new HashMap<>();
    Runnable opening = () -> {};

    void put(ResourceId id, byte[] bytes, Origin origin) {
      sources.put(
          id,
          new Source(
              origin,
              origin.name(),
              () -> {
                opening.run();
                return new ByteArrayInputStream(bytes);
              }));
    }

    public Optional<Source> find(ResourceId id) {
      return Optional.ofNullable(sources.get(id));
    }

    public Map<ResourceId, Source> list(String directory) {
      return Map.of();
    }
  }

  static byte[] bundled(ResourceId id) throws Exception {
    try (var stream =
        BarrelPreviewInputsTest.class.getResourceAsStream(
            "/assets/" + id.namespace() + "/" + id.path())) {
      assertNotNull(stream, id.toString());
      return stream.readAllBytes();
    }
  }

  static Memory inputs(ContainerTargets targets) throws Exception {
    var memory = new Memory();
    memory.put(
        ResourceId.parse("minecraft:models/block/block.json"),
        "{}".getBytes(StandardCharsets.UTF_8),
        ResourceAccess.Origin.MINECRAFT);
    for (var entry : targets.defaultArtwork().entrySet()) {
      byte[] bytes = bundled(entry.getValue());
      memory.put(entry.getValue(), bytes, ResourceAccess.Origin.OTHER);
      memory.put(entry.getKey(), bytes, ResourceAccess.Origin.LOOTR);
    }
    for (var barrel : targets.barrels()) {
      memory.put(
          barrel.target(), "{}".getBytes(StandardCharsets.UTF_8), ResourceAccess.Origin.LOOTR);
      memory.put(
          barrel.vanilla(),
          NATIVE.getBytes(StandardCharsets.UTF_8),
          ResourceAccess.Origin.MINECRAFT);
      for (boolean packed : List.of(false, true)) {
        var id = BarrelCompiler.defaultModel(barrel, packed);
        memory.put(id, bundled(id), ResourceAccess.Origin.OTHER);
      }
      var surface = BarrelCompiler.defaultSurface(barrel);
      memory.put(surface, bundled(surface), ResourceAccess.Origin.OTHER);
    }
    return memory;
  }

  private static BarrelPreviewInputs capture(
      ContainerTargets targets,
      Memory memory,
      ClientSettings settings,
      boolean looted,
      boolean open,
      Runnable checkpoint)
      throws Exception {
    return BarrelPreviewInputs.capture(
        targets,
        new BarrelPreviewInputs.Subject(settings, looted, open),
        memory,
        null,
        bytes -> BlockModels.validate(JsonDocuments.object(bytes)),
        EmfSupport.ABSENT,
        false,
        checkpoint);
  }

  @Test
  void completeDefaultCombinationCapturesAllPhysicalAndMaterialStatesWithoutBorrowedHandles()
      throws Exception {
    for (var targets : ContainerTargets.values()) {
      var memory = inputs(targets);
      for (boolean looted : List.of(false, true))
        for (boolean open : List.of(false, true)) {
          var captured = capture(targets, memory, ClientSettings.DEFAULTS, looted, open, () -> {});
          memory.opening =
              () -> {
                throw new AssertionError("Borrowed source used after capture");
              };
          var model = captured.resources().open(captured.barrel().target()).orElseThrow();
          try (model) {
            var json = JsonDocuments.object(model.readAllBytes());
            assertEquals(open ? 25 : 32, json.getAsJsonArray("elements").size());
            assertTrue(json.getAsJsonObject("textures").has("surface"));
          }
          assertEquals(open, captured.barrel().open());
          assertEquals(
              ClientSettings.DEFAULT, captured.selections().orElseThrow().models().requested());
          assertEquals(
              ClientSettings.DEFAULT,
              captured.selections().orElseThrow().models().effective().orElseThrow());
          assertEquals(
              PresentationCompiler.ProfileResolution.State.SELECTED,
              captured.selections().orElseThrow().artwork().state());
          assertEquals(looted ? "opened_barrel" : "barrel", captured.barrel().material());
          assertTrue(
              captured.resources().ids().stream().anyMatch(id -> id.path().endsWith(".png")));
          memory.opening = () -> {};
        }
    }
  }

  @Test
  void artworkSelectionIsIndependentAndDoesNotInheritMetadataFromAnOverriddenImage()
      throws Exception {
    var targets = ContainerTargets.LEGACY;
    var memory = inputs(targets);
    var image = ResourceId.parse("lootr:textures/block/barrel_side.png");
    var metadata = new ResourceId(image.namespace(), image.path() + ".mcmeta");
    memory.put(
        metadata,
        "invalid lower metadata".getBytes(StandardCharsets.UTF_8),
        ResourceAccess.Origin.LOOTR);
    var painted = capture(targets, memory, ClientSettings.DEFAULTS, false, false, () -> {});
    assertFalse(painted.resources().contains(metadata));
    memory.sources.remove(metadata);
    var inherited =
        capture(
            targets,
            memory,
            new ClientSettings(
                true, ClientSettings.DEFAULT, ClientSettings.VANILLA, ClientSettings.Fresh.OFF),
            false,
            false,
            () -> {});
    assertTrue(inherited.resources().contains(image));
    try (var model = inherited.resources().open(inherited.barrel().target()).orElseThrow()) {
      assertFalse(
          JsonDocuments.object(model.readAllBytes()).getAsJsonObject("textures").has("surface"));
    }
  }

  @Test
  void cancelledBorrowedSourcesCannotProduceACompleteCapture() throws Exception {
    var current = new AtomicBoolean(true);
    var memory = inputs(ContainerTargets.LEGACY);
    memory.opening = () -> current.set(false);
    assertThrows(
        CancellationException.class,
        () ->
            capture(
                ContainerTargets.LEGACY,
                memory,
                ClientSettings.DEFAULTS,
                false,
                false,
                () -> {
                  if (!current.get()) throw new CancellationException("retired");
                }));
  }

  @Test
  void anExternalExecutableModelIsNotSubstitutedWithAPlainCube() throws Exception {
    var targets = ContainerTargets.LEGACY;
    var memory = inputs(targets);
    memory.put(
        targets.barrels().getFirst().target(),
        "{\"loader\":\"example:custom\"}".getBytes(StandardCharsets.UTF_8),
        ResourceAccess.Origin.OTHER);
    var refusal =
        assertThrows(
            IllegalArgumentException.class,
            () -> capture(targets, memory, ClientSettings.DEFAULTS, false, false, () -> {}));
    assertTrue(refusal.getMessage().contains("Custom block-model loader"));
  }
}
