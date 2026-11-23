package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLighting;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import design.inherent.reform.presentation.graphics.GraphicPixels;
import design.inherent.reform.presentation.graphics.GraphicScene;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StyleStillMaterialsTest {
  private static final DocumentId TEXTURE = DocumentId.parse("test:frame");
  private static final GraphicLimits LIMITS =
      new GraphicLimits(16_000_000, 16_000_000, 1_000_000, 128, 64_000_000);

  @Test
  void oneTextureUsesIndependentFaceAlphaWithoutChangingGeometryOrLighting() {
    var pixels = new int[] {0xff123456, 0x00123456, 0x80123456, 0xffabcdef};
    var source =
        scene(
            4,
            1,
            pixels,
            List.of(
                new float[] {0, 0, .25f, 1}, new float[] {.25f, 0, .5f, 1},
                new float[] {.5f, 0, .75f, 1}, new float[] {.75f, 0, 1, 1}));
    var result = StyleStillMaterials.classify(source, LIMITS, () -> {});
    assertEquals(source.mesh().vertices(), result.mesh().vertices());
    assertEquals(source.textures(), result.textures());
    var expected =
        List.of(
            GraphicMaterial.Alpha.OPAQUE,
            GraphicMaterial.Alpha.CUTOUT,
            GraphicMaterial.Alpha.TRANSLUCENT,
            GraphicMaterial.Alpha.OPAQUE);
    for (int i = 0; i < result.mesh().triangles().size(); i++) {
      var triangle = result.mesh().triangles().get(i);
      var original = source.mesh().triangles().get(i);
      assertEquals(original.a(), triangle.a());
      assertEquals(original.b(), triangle.b());
      assertEquals(original.c(), triangle.c());
      assertEquals(original.lighting(), triangle.lighting());
      var material = result.materials().get(triangle.material());
      assertEquals(expected.get(i / 2), material.alpha());
      assertEquals(GraphicMaterial.Cull.BACK, material.cull());
      assertEquals(GraphicMaterial.Filter.NEAREST, material.filter());
      assertEquals(GraphicMaterial.Wrap.CLAMP, material.wrap());
      assertEquals(
          expected.get(i / 2) == GraphicMaterial.Alpha.CUTOUT ? .1f : 0, material.cutoff());
    }
    assertEquals(3, result.materials().size());
  }

  @Test
  void tileInteriorsAndUnalignedBordersMatchIndependentPixelScan() {
    int size = 64;
    int[] pixels = new int[size * size];
    Arrays.fill(pixels, 0xff123456);
    pixels[32 * size + 32] = 0x80123456;
    pixels[15 * size + 16] = 0;
    pixels[48 * size + 63] = 0x40123456;
    int[] edges = {0, 1, 15, 16, 17, 31, 32, 33, 48, 63, 64};
    var rectangles = new ArrayList<float[]>();
    var expected = new ArrayList<GraphicMaterial.Alpha>();
    for (int x0 : edges)
      for (int x1 : edges) {
        if (x0 >= x1) continue;
        for (int y0 : edges)
          for (int y1 : edges) {
            if (y0 >= y1) continue;
            rectangles.add(new float[] {x0 / 64f, y0 / 64f, x1 / 64f, y1 / 64f});
            boolean zero = false, partial = false;
            for (int y = y0; y < y1; y++)
              for (int x = x0; x < x1; x++) {
                int alpha = pixels[y * size + x] >>> 24;
                zero |= alpha == 0;
                partial |= alpha > 0 && alpha < 255;
              }
            expected.add(
                partial
                    ? GraphicMaterial.Alpha.TRANSLUCENT
                    : zero ? GraphicMaterial.Alpha.CUTOUT : GraphicMaterial.Alpha.OPAQUE);
          }
      }
    var source = scene(size, size, pixels, rectangles);
    var result = StyleStillMaterials.classify(source, LIMITS, () -> {});
    for (int i = 0; i < expected.size(); i++)
      assertEquals(
          expected.get(i),
          result.materials().get(result.mesh().triangles().get(i * 2).material()).alpha(),
          "rectangle " + Arrays.toString(rectangles.get(i)));
  }

  @Test
  void clampAndDegenerateUvsSelectTheActualEdgeTexel() {
    var source =
        scene(
            2,
            1,
            new int[] {0, 0x80ffffff},
            List.of(
                new float[] {-2, 0, -1, 1}, new float[] {1, 1, 1, 1}, new float[] {2, 0, 3, 1}));
    var result = StyleStillMaterials.classify(source, LIMITS, () -> {});
    assertEquals(
        GraphicMaterial.Alpha.CUTOUT,
        result.materials().get(result.mesh().triangles().get(0).material()).alpha());
    assertEquals(
        GraphicMaterial.Alpha.TRANSLUCENT,
        result.materials().get(result.mesh().triangles().get(2).material()).alpha());
    assertEquals(
        GraphicMaterial.Alpha.TRANSLUCENT,
        result.materials().get(result.mesh().triangles().get(4).material()).alpha());
  }

  @Test
  void repeatedLargeFootprintsDoNotRepeatedlyScanTheImage() {
    int size = 512;
    int[] pixels = new int[size * size];
    Arrays.fill(pixels, 0xff123456);
    pixels[size * size - 1] = 0;
    var rectangles = new ArrayList<float[]>();
    for (int i = 0; i < 2048; i++) rectangles.add(new float[] {0, 0, 1, 1});
    var source = scene(size, size, pixels, rectangles);
    var result = StyleStillMaterials.classify(source, LIMITS, () -> {});
    assertEquals(1, result.materials().size());
    assertEquals(
        GraphicMaterial.Alpha.CUTOUT, result.materials().values().iterator().next().alpha());
  }

  @Test
  void excessiveDistinctBoundarySamplingRefusesInsteadOfRunningUnbounded() {
    int size = 64;
    int[] pixels = new int[size * size];
    Arrays.fill(pixels, 0xff123456);
    pixels[pixels.length - 1] = 0;
    var rectangles = new ArrayList<float[]>();
    for (int x = 1; x < 15; x++)
      for (int y = 1; y < 15; y++)
        rectangles.add(new float[] {x / 64f, y / 64f, 63 / 64f, 63 / 64f});
    var source = scene(size, size, pixels, rectangles);
    var limits = new GraphicLimits(16_000_000, 16_000_000, pixels.length, 128, 64_000_000);
    assertThrows(PolicyRefusal.class, () -> StyleStillMaterials.classify(source, limits, () -> {}));
  }

  @Test
  void cancellationStopsInsideImagePreparationAndLeavesTheInputIntact() {
    int[] pixels = new int[512 * 512];
    Arrays.fill(pixels, 0xff123456);
    var source = scene(512, 512, pixels, List.of(new float[] {0, 0, 1, 1}));
    var calls = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            StyleStillMaterials.classify(
                source,
                LIMITS,
                () -> {
                  if (calls.incrementAndGet() == 3) throw new CancellationException();
                }));
    assertEquals(0xff123456, source.textures().get(TEXTURE).pixel(511, 511));
    assertEquals(1, source.materials().size());
  }

  @Test
  void intentionallyEmptyModelsRemainEmpty() {
    var source =
        new GraphicScene(new GraphicMesh(LIMITS, List.of(), List.of()), Map.of(), Map.of());
    var result = StyleStillMaterials.classify(source, LIMITS, () -> {});
    assertTrue(result.mesh().isEmpty());
    assertTrue(result.materials().isEmpty());
  }

  @Test
  void maximumAcceptedImageAndCuboidFaceCountStayWithinTheFiniteEnvelope() {
    int size = 4096;
    int[] pixels = new int[size * size];
    Arrays.fill(pixels, 0xff123456);
    pixels[pixels.length - 1] = 0;
    var rectangles = new ArrayList<float[]>();
    // The resource contract admits 1024 cuboids with at most six faces each.
    for (int i = 0; i < 6144; i++) rectangles.add(new float[] {0, 0, 1, 1});
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 128 * 1024 * 1024, 16 * 1024 * 1024, 256, 256 * 1024 * 1024);
    var source = scene(size, size, pixels, rectangles, limits);
    var result = StyleStillMaterials.classify(source, limits, () -> {});
    assertEquals(12288, result.mesh().triangles().size());
    assertEquals(
        GraphicMaterial.Alpha.CUTOUT, result.materials().values().iterator().next().alpha());
    assertEquals(source.textures(), result.textures());
  }

  static GraphicScene scene(int width, int height, int[] pixels, List<float[]> rectangles) {
    return scene(width, height, pixels, rectangles, LIMITS);
  }

  private static GraphicScene scene(
      int width, int height, int[] pixels, List<float[]> rectangles, GraphicLimits limits) {
    var vertices = new ArrayList<GraphicMesh.Vertex>();
    var triangles = new ArrayList<GraphicMesh.Triangle>();
    for (var r : rectangles) {
      int base = vertices.size();
      vertices.add(new GraphicMesh.Vertex(0, 0, 0, r[0], r[1], -1, 0, 0, 1));
      vertices.add(new GraphicMesh.Vertex(1, 0, 0, r[2], r[1], -1, 0, 0, 1));
      vertices.add(new GraphicMesh.Vertex(1, 1, 0, r[2], r[3], -1, 0, 0, 1));
      vertices.add(new GraphicMesh.Vertex(0, 1, 0, r[0], r[3], -1, 0, 0, 1));
      var light =
          new GraphicLighting(
              GraphicLighting.Shading.DIRECTIONAL, java.util.Optional.empty(), 7, false);
      triangles.add(new GraphicMesh.Triangle(base, base + 1, base + 2, TEXTURE, light));
      triangles.add(new GraphicMesh.Triangle(base + 2, base + 3, base, TEXTURE, light));
    }
    return new GraphicScene(
        new GraphicMesh(limits, vertices, triangles),
        Map.of(TEXTURE, StyleStillMaterials.material(TEXTURE, GraphicMaterial.Alpha.OPAQUE)),
        Map.of(TEXTURE, new GraphicPixels(limits, TEXTURE, width, height, pixels)));
  }
}
