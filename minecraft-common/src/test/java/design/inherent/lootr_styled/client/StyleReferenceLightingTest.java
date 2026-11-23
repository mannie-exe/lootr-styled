package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StyleReferenceLightingTest {
  private static final GraphicLimits LIMITS = new GraphicLimits(16384, 16384, 4096, 16, 65536);

  @Test
  void sourceWorldAoStaysRefusedWhileExplicitReferenceRetainsEveryOtherSurfaceFact() {
    var base =
        StyleStillMaterialsTest.scene(
            1, 1, new int[] {0x7f123456}, List.of(new float[] {0, 0, 1, 1}));
    for (var shading : GraphicLighting.Shading.values())
      for (var direction : GraphicLighting.Direction.values())
        for (int emission : new int[] {0, 7, 15}) {
          var light = new GraphicLighting(shading, Optional.of(direction), emission, true);
          var triangles = new ArrayList<GraphicMesh.Triangle>();
          var first = base.mesh().triangles().getFirst();
          triangles.add(
              new GraphicMesh.Triangle(first.a(), first.b(), first.c(), first.material(), light));
          triangles.add(base.mesh().triangles().get(1));
          var source =
              new GraphicScene(
                  new GraphicMesh(LIMITS, base.mesh().vertices(), triangles),
                  base.materials(),
                  base.textures());
          assertThrows(PolicyRefusal.class, () -> GraphicProjection.requireSupported(source));
          var result = StyleReferenceLighting.prepare(source, LIMITS, () -> {});
          assertEquals(1, result.nativeAoTriangles());
          var actual = result.scene();
          assertDoesNotThrow(() -> GraphicProjection.requireSupported(actual));
          assertEquals(source.mesh().vertices(), actual.mesh().vertices());
          assertEquals(source.mesh().bounds(), actual.mesh().bounds());
          assertEquals(source.materials(), actual.materials());
          assertEquals(source.textures(), actual.textures());
          assertEquals(0x7f123456, actual.textures().values().iterator().next().pixel(0, 0));
          for (int i = 0; i < triangles.size(); i++) {
            var before = triangles.get(i);
            var after = actual.mesh().triangles().get(i);
            assertEquals(before.a(), after.a());
            assertEquals(before.b(), after.b());
            assertEquals(before.c(), after.c());
            assertEquals(before.material(), after.material());
            assertEquals(before.lighting().shading(), after.lighting().shading());
            assertEquals(before.lighting().direction(), after.lighting().direction());
            assertEquals(before.lighting().lightEmission(), after.lighting().lightEmission());
            assertFalse(after.lighting().ambientOcclusion());
          }
          assertTrue(source.mesh().triangles().getFirst().lighting().ambientOcclusion());
        }
  }

  @Test
  void nativeNoAoAndIntentionallyEmptyModelsDoNotNeedAReplacementScene() {
    var source =
        StyleStillMaterialsTest.scene(1, 1, new int[] {-1}, List.of(new float[] {0, 0, 1, 1}));
    assertSame(source, StyleReferenceLighting.prepare(source, LIMITS, () -> {}).scene());
    var empty =
        new GraphicScene(
            new GraphicMesh(LIMITS, List.of(), List.of()), java.util.Map.of(), java.util.Map.of());
    assertSame(empty, StyleReferenceLighting.prepare(empty, LIMITS, () -> {}).scene());
  }

  @Test
  void cancellationAndSmallerDeclaredBudgetsRefuseWithoutChangingSource() {
    var source =
        StyleStillMaterialsTest.scene(1, 1, new int[] {-1}, List.of(new float[] {0, 0, 1, 1}));
    var calls = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            StyleReferenceLighting.prepare(
                source,
                LIMITS,
                () -> {
                  if (calls.incrementAndGet() == 2) throw new CancellationException();
                }));
    var tiny = new GraphicLimits(64, 64, 16, 2, 256);
    assertThrows(PolicyRefusal.class, () -> StyleReferenceLighting.prepare(source, tiny, () -> {}));
    assertFalse(source.mesh().triangles().getFirst().lighting().ambientOcclusion());
  }
}
