package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class ChestPreviewInputsTest {
  private static final EmfSupport EMF = EmfSupport.inspect(java.util.Optional.of("3.3.12"));

  private static ChestPreviewInputs capture(
      ContainerTargets targets,
      BarrelPreviewInputsTest.Memory memory,
      ClientSettings settings,
      boolean trapped,
      boolean looted,
      EmfSupport emf,
      Runnable checkpoint)
      throws Exception {
    return ChestPreviewInputs.capture(
        targets,
        new ChestPreviewInputs.Subject(settings, trapped, looted, 0.75f),
        memory,
        null,
        bytes -> BlockModels.validate(JsonDocuments.object(bytes)),
        emf,
        true,
        checkpoint);
  }

  @Test
  void eachChestMaterialUsesItsActualSlotAndOwnsBytesAfterSourceRetirement() throws Exception {
    for (var targets : ContainerTargets.values()) {
      var memory = BarrelPreviewInputsTest.inputs(targets);
      for (boolean trapped : List.of(false, true))
        for (boolean looted : List.of(false, true)) {
          var captured =
              capture(
                  targets,
                  memory,
                  ClientSettings.DEFAULTS,
                  trapped,
                  looted,
                  EmfSupport.ABSENT,
                  () -> {});
          memory.opening =
              () -> {
                throw new AssertionError("Retired source was consulted");
              };
          assertEquals(targets.chests().get(trapped ? 1 : 0), captured.chest());
          assertTrue(captured.rig().isEmpty());
          assertEquals(0.75f, captured.subject().lid());
          assertEquals(
              ClientSettings.DEFAULT, captured.selections().orElseThrow().artwork().requested());
          assertEquals(
              ClientSettings.DEFAULT,
              captured.selections().orElseThrow().artwork().effective().orElseThrow());
          var id = captured.texture();
          try (var image = captured.resources().open(id).orElseThrow()) {
            assertArrayEquals(
                BarrelPreviewInputsTest.bundled(targets.defaultArtwork().get(id)),
                image.readAllBytes());
          }
          memory.opening = () -> {};
        }
    }
  }

  @Test
  void effectiveRigAndRelativeDependenciesAreFrozenWithoutPublishingOrUsingBorrowedHandles()
      throws Exception {
    for (var targets : ContainerTargets.values()) {
      var memory = BarrelPreviewInputsTest.inputs(targets);
      var rig = targets.chests().get(0).target();
      var dependency =
          new ResourceId(
              rig.namespace(),
              rig.path().substring(0, rig.path().lastIndexOf('/') + 1) + "body.jpm");
      memory.put(
          rig,
          ("{\"textureSize\":[64,64],\"models\":["
                  + "{\"part\":\"bottom\",\"model\":\"body.jpm\"},"
                  + "{\"part\":\"lid\"},{\"part\":\"lock\"}]}")
              .getBytes(StandardCharsets.UTF_8),
          ResourceAccess.Origin.OTHER);
      memory.put(
          dependency,
          "{\"boxes\":[{\"coordinates\":[0,0,0,14,10,14]}]}".getBytes(StandardCharsets.UTF_8),
          ResourceAccess.Origin.OTHER);
      var before = java.util.Map.copyOf(memory.sources);
      var captured = capture(targets, memory, ClientSettings.DEFAULTS, false, false, EMF, () -> {});
      memory.opening =
          () -> {
            throw new AssertionError("Borrowed rig dependency escaped");
          };
      assertEquals(java.util.Optional.of(rig), captured.rig());
      assertEquals(3, captured.resources().ids().size());
      try (var stream = captured.resources().open(rig).orElseThrow()) {
        assertTrue(
            new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("lootr_styled_"));
      }
      assertEquals(before, memory.sources);
    }
  }

  @Test
  void artworkChoiceIsIndependentAndReplacedImagesDoNotInheritOldMetadata() throws Exception {
    var targets = ContainerTargets.LEGACY;
    var memory = BarrelPreviewInputsTest.inputs(targets);
    var id = targets.chestArtwork(false, false);
    var metadata = new ResourceId(id.namespace(), id.path() + ".mcmeta");
    memory.put(
        metadata,
        "{\"animation\":{}}".getBytes(StandardCharsets.UTF_8),
        ResourceAccess.Origin.LOOTR);
    var painted =
        capture(
            targets, memory, ClientSettings.DEFAULTS, false, false, EmfSupport.ABSENT, () -> {});
    assertFalse(painted.resources().contains(metadata));
    var settings =
        new ClientSettings(
            true, ClientSettings.DEFAULT, ClientSettings.VANILLA, ClientSettings.Fresh.OFF);
    var inherited = capture(targets, memory, settings, false, false, EmfSupport.ABSENT, () -> {});
    assertTrue(inherited.resources().contains(metadata));
  }

  @Test
  void anEmptyNativeDeclarationCannotHideAnUncapturedTextureOverride() throws Exception {
    var targets = ContainerTargets.LEGACY;
    var memory = BarrelPreviewInputsTest.inputs(targets);
    var rig = targets.chests().get(0).target();
    memory.put(
        rig,
        "{\"textureSize\":[64,64],\"models\":[]}".getBytes(StandardCharsets.UTF_8),
        ResourceAccess.Origin.OTHER);
    assertTrue(
        capture(targets, memory, ClientSettings.DEFAULTS, false, false, EMF, () -> {})
            .rig()
            .isEmpty());
    memory.put(
        rig,
        "{\"texture\":\"other\",\"textureSize\":[64,64],\"models\":[]}"
            .getBytes(StandardCharsets.UTF_8),
        ResourceAccess.Origin.OTHER);
    assertThrows(
        IllegalArgumentException.class,
        () -> capture(targets, memory, ClientSettings.DEFAULTS, false, false, EMF, () -> {}));
  }

  @Test
  void unsupportedRendererMalformedRigAndCancelledInputCannotBecomeReady() throws Exception {
    var targets = ContainerTargets.LEGACY;
    var memory = BarrelPreviewInputsTest.inputs(targets);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            capture(
                targets,
                memory,
                ClientSettings.DEFAULTS,
                false,
                false,
                EmfSupport.inspect(java.util.Optional.of("4.0.0")),
                () -> {}));
    var rig = targets.chests().get(0).target();
    memory.put(
        rig, "{\"models\":{}}".getBytes(StandardCharsets.UTF_8), ResourceAccess.Origin.OTHER);
    assertThrows(
        IllegalArgumentException.class,
        () -> capture(targets, memory, ClientSettings.DEFAULTS, false, false, EMF, () -> {}));
    assertThrows(
        CancellationException.class,
        () ->
            capture(
                targets,
                memory,
                ClientSettings.DEFAULTS,
                false,
                false,
                EMF,
                () -> {
                  throw new CancellationException("Retired preview");
                }));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ChestPreviewInputs.Subject(ClientSettings.DEFAULTS, false, false, Float.NaN));
  }
}
