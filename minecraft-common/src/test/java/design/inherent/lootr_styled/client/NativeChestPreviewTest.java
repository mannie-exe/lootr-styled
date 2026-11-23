package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.EmfSupport;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

/** Actual native factories/cube emission, without a graphical client or live renderer. */
class NativeChestPreviewTest {
  private static final GraphicLimits LIMITS =
      new GraphicLimits(
          16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);

  static ChestPreviewInputs input(boolean trapped, boolean looted, float lid) throws Exception {
    return ChestPreviewInputs.capture(
        NativeUi.targets(),
        new ChestPreviewInputs.Subject(ClientSettings.DEFAULTS, trapped, looted, lid),
        NativeBarrelPreviewTest.sources(false),
        null,
        NativeUi::validateModel,
        EmfSupport.ABSENT,
        false,
        () -> {});
  }

  @Test
  void nativeFactoriesProduceCopiedGeometryForEveryArtworkAndLidState() throws Exception {
    for (boolean trapped : List.of(false, true))
      for (boolean looted : List.of(false, true))
        for (float lid : List.of(0f, 0.5f, 1f)) {
          var source = input(trapped, looted, lid);
          var mesh = NativeChestPreview.bake(source, LIMITS, () -> {});
          assertFalse(mesh.isEmpty());
          assertEquals(mesh.vertices().size() / 2, mesh.triangles().size());
          assertEquals(java.util.Set.of(NativeChestPreview.MATERIAL), mesh.materials());
          for (var vertex : mesh.vertices()) {
            assertTrue(
                Float.isFinite(vertex.x())
                    && Float.isFinite(vertex.y())
                    && Float.isFinite(vertex.z()));
            assertTrue(vertex.u() >= 0 && vertex.u() <= 1);
            assertTrue(vertex.v() >= 0 && vertex.v() <= 1);
            assertEquals(0xffffffff, vertex.argb());
          }
          assertThrows(UnsupportedOperationException.class, () -> mesh.vertices().clear());
        }
  }

  @Test
  void poseCapabilitiesDescribeTheOwnedAttachmentsRatherThanTheRequestedProgress()
      throws Exception {
    var input = input(false, false, 1);
    var nativeResult = NativeChestPreview.prepare(input, LIMITS, () -> {});
    assertTrue(nativeResult.nativeLidHasGeometry());
    assertTrue(nativeResult.nativeLockHasGeometry());
    assertTrue(nativeResult.nativeControlHasGeometry());
    assertEquals(1, nativeResult.pose().lidProgress());
    assertFalse(nativeResult.pose().evaluatesRigAnimations());
    assertFalse(nativeResult.pose().usesWorldState());

    // A rig may place its visible lid under the base and leave both native controls empty.
    var original = NativeChestPreview.nativeRoot();
    var empty =
        new net.minecraft.client.model.geom.ModelPart(java.util.List.of(), java.util.Map.of());
    var reference =
        new net.minecraft.client.model.geom.ModelPart(
            java.util.List.of(),
            java.util.Map.of("bottom", original.getChild("bottom"), "lid", empty, "lock", empty));
    var staticRig = NativeChestPreview.prepareOwned(input, reference, LIMITS, () -> {});
    assertFalse(staticRig.mesh().isEmpty());
    assertFalse(staticRig.nativeControlHasGeometry());
    assertEquals(1, staticRig.pose().lidProgress());
    var closed =
        NativeChestPreview.prepareOwned(input(false, false, 0), reference, LIMITS, () -> {});
    assertEquals(closed.mesh().vertices(), staticRig.mesh().vertices());
  }

  @Test
  void poseCapabilitiesRespectHiddenSubtreesAndSkipDrawWithoutHidingChildren() {
    var child = NativeChestPreview.nativeRoot().getChild("lid");
    var parent =
        new net.minecraft.client.model.geom.ModelPart(
            java.util.List.of(), java.util.Map.of("child", child));
    parent.skipDraw = true;
    assertTrue(EntityPreviewGeometry.hasVisibleGeometry(parent, () -> {}));
    child.skipDraw = true;
    assertFalse(EntityPreviewGeometry.hasVisibleGeometry(parent, () -> {}));
    child.skipDraw = false;
    child.visible = false;
    assertFalse(EntityPreviewGeometry.hasVisibleGeometry(parent, () -> {}));
    child.visible = true;
    parent.visible = false;
    assertFalse(EntityPreviewGeometry.hasVisibleGeometry(parent, () -> {}));
    assertThrows(
        CancellationException.class,
        () ->
            EntityPreviewGeometry.hasVisibleGeometry(
                child,
                () -> {
                  throw new CancellationException();
                }));
  }

  @Test
  void openingOnlyChangesDetachedLidAndLockWhileLaterClosedGeometryIsExact() throws Exception {
    var closed = NativeChestPreview.bake(input(false, false, 0), LIMITS, () -> {});
    var open = NativeChestPreview.bake(input(false, false, 1), LIMITS, () -> {});
    var again = NativeChestPreview.bake(input(false, false, 0), LIMITS, () -> {});
    assertEquals(closed.vertices(), again.vertices());
    assertEquals(closed.triangles(), again.triangles());
    assertNotEquals(closed.vertices(), open.vertices());
    assertEquals(closed.vertices().size(), open.vertices().size());
    // At least one complete six-face body remains byte-for-byte stable across poses.
    long stable =
        java.util.stream.IntStream.range(0, closed.vertices().size())
            .filter(i -> closed.vertices().get(i).equals(open.vertices().get(i)))
            .count();
    assertTrue(stable >= 24, "The native chest body changed when only its lid opened");
  }

  @Test
  void nativeVisibilityAndSkipDrawRetainTheirDifferentChildSemantics() {
    var root = NativeChestPreview.nativeRoot();
    var full = EntityPreviewGeometry.capture(root, NativeChestPreview.MATERIAL, LIMITS, () -> {});
    root.getChild("bottom").skipDraw = true;
    var skipped =
        EntityPreviewGeometry.capture(root, NativeChestPreview.MATERIAL, LIMITS, () -> {});
    assertTrue(skipped.vertices().size() < full.vertices().size());
    root.skipDraw = true;
    assertEquals(
        skipped.vertices(),
        EntityPreviewGeometry.capture(root, NativeChestPreview.MATERIAL, LIMITS, () -> {})
            .vertices());
    root.visible = false;
    var hidden = EntityPreviewGeometry.capture(root, NativeChestPreview.MATERIAL, LIMITS, () -> {});
    assertTrue(hidden.isEmpty());
    root.visible = true;
    root.getChild("bottom").skipDraw = false;
    assertEquals(
        full.vertices(),
        EntityPreviewGeometry.capture(root, NativeChestPreview.MATERIAL, LIMITS, () -> {})
            .vertices());
  }

  @Test
  void aCapturedRigCannotFallBackToNativeWhenItsOptionalAdapterIsAbsent() throws Exception {
    var base = NativeBarrelPreviewTest.sources(false);
    var target = NativeUi.targets().chests().get(0).target();
    var sources =
        new design.inherent.lootr_styled.resources.ResourceAccess() {
          public java.util.Optional<Source> find(design.inherent.lootr_styled.ResourceId id) {
            if (id.equals(target))
              return java.util.Optional.of(
                  new Source(
                      Origin.OTHER,
                      "Missing-adapter negative control",
                      () ->
                          new java.io.ByteArrayInputStream(
                              ("{\"textureSize\":[64,64],\"models\":[{\"part\":\"bottom\"},"
                                      + "{\"part\":\"lid\"},{\"part\":\"lock\"}]}")
                                  .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            return base.find(id);
          }

          public java.util.Map<design.inherent.lootr_styled.ResourceId, Source> list(
              String directory) {
            return base.list(directory);
          }
        };
    var input =
        ChestPreviewInputs.capture(
            NativeUi.targets(),
            new ChestPreviewInputs.Subject(ClientSettings.DEFAULTS, false, false, 0),
            sources,
            null,
            NativeUi::validateModel,
            EmfSupport.inspect(java.util.Optional.of("3.3.12")),
            true,
            () -> {});
    assertTrue(input.rig().isPresent());
    var refusal =
        assertThrows(PolicyRefusal.class, () -> NativeChestPreview.bake(input, LIMITS, () -> {}));
    assertEquals(
        "lootr_styled.preview.rig_unavailable", refusal.report().issues().getFirst().code());
  }

  @Test
  void cancellationAndSmallBudgetsRefuseWithoutChangingFutureNativeGeometry() throws Exception {
    var source = input(false, false, 0);
    assertThrows(
        CancellationException.class,
        () ->
            NativeChestPreview.bake(
                source,
                LIMITS,
                () -> {
                  throw new CancellationException("Retired");
                }));
    var tiny =
        new GraphicLimits(
            LIMITS.inputBytes(),
            1,
            LIMITS.decodedPixels(),
            LIMITS.resources(),
            LIMITS.nativeBytes());
    var refusal =
        assertThrows(PolicyRefusal.class, () -> NativeChestPreview.bake(source, tiny, () -> {}));
    assertEquals("preview.budget", refusal.report().issues().getFirst().code());
    assertFalse(NativeChestPreview.bake(source, LIMITS, () -> {}).isEmpty());
  }
}
