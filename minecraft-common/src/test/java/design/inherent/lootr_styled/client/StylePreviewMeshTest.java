package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StylePreviewMeshTest {
  // Conversion tests declare their own envelope; no native/GPU budget is qualified here.
  private static final GraphicLimits LIMITS =
      new GraphicLimits(
          16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);

  private static GraphicMaterial material(DocumentId texture) {
    return new GraphicMaterial(
        texture,
        GraphicMaterial.Alpha.CUTOUT,
        0.1f,
        GraphicMaterial.Cull.BACK,
        GraphicMaterial.Filter.NEAREST,
        GraphicMaterial.Wrap.CLAMP);
  }

  @Test
  void sharedMeshRetainsCopiedNativeCoordinatesWindingTextureSourcesAndLighting() throws Exception {
    var nativeMesh =
        NativeBarrelPreview.bake(NativeBarrelPreviewTest.inputs(false, true, true), () -> {});
    var shared =
        StylePreviewMesh.convert(nativeMesh, LIMITS, StylePreviewMeshTest::material, () -> {});
    assertEquals(nativeMesh.faces().size() * 4, shared.mesh().vertices().size());
    assertEquals(nativeMesh.faces().size() * 2, shared.mesh().triangles().size());
    assertEquals(shared.mesh().materials(), shared.materials().keySet());
    assertEquals(shared.materials().keySet(), shared.textures().keySet());
    for (int faceIndex = 0; faceIndex < nativeMesh.faces().size(); faceIndex++) {
      var original = nativeMesh.faces().get(faceIndex);
      for (int vertex = 0; vertex < 4; vertex++) {
        var expected = original.vertices().get(vertex);
        var actual = shared.mesh().vertices().get(faceIndex * 4 + vertex);
        assertEquals(expected.x(), actual.x());
        assertEquals(expected.y(), actual.y());
        assertEquals(expected.z(), actual.z());
        assertEquals(expected.u(), actual.u());
        assertEquals(expected.v(), actual.v());
        assertEquals(0xffffffff, actual.argb());
      }
      var first = shared.mesh().triangles().get(faceIndex * 2);
      var second = shared.mesh().triangles().get(faceIndex * 2 + 1);
      assertEquals(faceIndex * 4, first.a());
      assertEquals(faceIndex * 4 + 1, first.b());
      assertEquals(faceIndex * 4 + 2, first.c());
      assertEquals(first.c(), second.a());
      assertEquals(faceIndex * 4 + 3, second.b());
      assertEquals(first.a(), second.c());
      assertEquals(original.texture(), shared.sources().get(first.material()));
      assertEquals(original.lightEmission(), first.lighting().lightEmission());
      assertEquals(nativeMesh.ambientOcclusion(), first.lighting().ambientOcclusion());
    }
    for (var texture : shared.textures().values()) {
      byte[] copy = texture.png();
      copy[0] = 0;
      assertEquals((byte) 137, texture.png()[0]);
    }
  }

  @Test
  void anInvisibleNativeModelStaysReadyWithoutMaterialPreparation() throws Exception {
    var nativeMesh =
        NativeBarrelPreview.bake(
            NativeBarrelPreviewTest.inputs(false, false, true, true), () -> {});
    var shared =
        StylePreviewMesh.convert(
            nativeMesh,
            LIMITS,
            id -> {
              throw new AssertionError("An empty model must not prepare a material");
            },
            () -> {});
    assertTrue(shared.mesh().isEmpty());
    assertTrue(shared.mesh().vertices().isEmpty());
    assertTrue(shared.mesh().triangles().isEmpty());
    assertTrue(shared.materials().isEmpty());
    assertTrue(shared.textures().isEmpty());
    assertTrue(shared.sources().isEmpty());
    assertEquals(
        new design.inherent.reform.presentation.graphics.GraphicMesh.Bounds(0, 0, 0, 0, 0, 0),
        shared.mesh().bounds());
  }

  @Test
  void chestBundleRetainsTheNativeMaterialAndCapturedArtworkBinding() throws Exception {
    var input = NativeChestPreviewTest.input(true, true, 0);
    var mesh = NativeChestPreview.bake(input, LIMITS, () -> {});
    var bundle =
        StylePreviewMesh.chest(input, mesh, LIMITS, StylePreviewMeshTest::material, () -> {});
    assertSame(mesh, bundle.mesh());
    assertEquals(java.util.Set.of(NativeChestPreview.MATERIAL), bundle.materials().keySet());
    assertEquals(java.util.Map.of(NativeChestPreview.MATERIAL, input.texture()), bundle.sources());
    try (var stream = input.resources().open(input.texture()).orElseThrow()) {
      assertArrayEquals(
          stream.readAllBytes(), bundle.textures().get(NativeChestPreview.MATERIAL).png());
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StylePreviewMesh.chest(
                input,
                mesh,
                LIMITS,
                ignored -> material(DocumentId.parse("example:foreign")),
                () -> {}));
  }

  @Test
  void emptyChestProducesNoTextureBindingsOrMaterialPreparation() throws Exception {
    var input = NativeChestPreviewTest.input(false, false, 0);
    var mesh =
        new design.inherent.reform.presentation.graphics.GraphicMesh(
            LIMITS, java.util.List.of(), java.util.List.of());
    var bundle =
        StylePreviewMesh.chest(
            input,
            mesh,
            LIMITS,
            ignored -> {
              throw new AssertionError("An invisible chest must not prepare a material");
            },
            () -> {});
    assertTrue(bundle.mesh().isEmpty());
    assertTrue(bundle.materials().isEmpty());
    assertTrue(bundle.textures().isEmpty());
    assertTrue(bundle.sources().isEmpty());
  }

  @Test
  void textureResourceLimitRefusesBeforeMaterialPreparation() throws Exception {
    var source =
        NativeBarrelPreview.bake(NativeBarrelPreviewTest.inputs(false, false, false), () -> {});
    var calls = new AtomicInteger();
    var limit =
        new GraphicLimits(
            LIMITS.inputBytes(),
            LIMITS.outputBytes(),
            LIMITS.decodedPixels(),
            1,
            LIMITS.nativeBytes());
    var failure =
        assertThrows(
            PolicyRefusal.class,
            () ->
                StylePreviewMesh.convert(
                    source,
                    limit,
                    id -> {
                      calls.incrementAndGet();
                      return material(id);
                    },
                    () -> {}));
    assertEquals("preview.budget", failure.report().issues().getFirst().code());
    assertEquals(0, calls.get());
  }

  @Test
  void smallOutputBudgetRefusesBeforeMaterialPreparation() throws Exception {
    var source =
        NativeBarrelPreview.bake(NativeBarrelPreviewTest.inputs(false, false, true), () -> {});
    var calls = new AtomicInteger();
    var limits =
        new GraphicLimits(
            LIMITS.inputBytes(),
            1,
            LIMITS.decodedPixels(),
            LIMITS.resources(),
            LIMITS.nativeBytes());
    var refused =
        assertThrows(
            PolicyRefusal.class,
            () ->
                StylePreviewMesh.convert(
                    source,
                    limits,
                    id -> {
                      calls.incrementAndGet();
                      return material(id);
                    },
                    () -> {}));
    assertEquals("preview.budget", refused.report().issues().getFirst().code());
    assertEquals(0, calls.get());
  }

  @Test
  void unresolvedTintAndForeignMaterialBindingsCannotBePresentedAsReady() throws Exception {
    var source =
        NativeBarrelPreview.bake(NativeBarrelPreviewTest.inputs(false, false, true), () -> {});
    var faces = new ArrayList<>(source.faces());
    var face = faces.getFirst();
    faces.set(
        0,
        new BarrelPreviewMesh.Face(
            face.texture(),
            face.vertices(),
            face.direction(),
            face.shade(),
            face.shadeDirectionOverride(),
            3,
            face.lightEmission()));
    var tinted = new BarrelPreviewMesh(source.inputs(), faces, source.ambientOcclusion());
    var refusal =
        assertThrows(
            PolicyRefusal.class,
            () ->
                StylePreviewMesh.convert(tinted, LIMITS, StylePreviewMeshTest::material, () -> {}));
    assertEquals(
        "lootr_styled.preview.tint_unavailable", refusal.report().issues().getFirst().code());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StylePreviewMesh.convert(
                source, LIMITS, id -> material(DocumentId.parse("example:foreign")), () -> {}));
  }
}
