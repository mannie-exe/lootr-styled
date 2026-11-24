package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLedger;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import design.inherent.reform.presentation.graphics.GraphicTexture;
import design.inherent.reform.serialization.JsonLimits;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StylePreviewSceneTest {
  private static final GraphicLimits LIMITS =
      new GraphicLimits(
          16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
  private static final JsonLimits METADATA = new JsonLimits(16384, 32, 4096, 8192, 256, 128);

  private static GraphicMaterial material(DocumentId id) {
    return new GraphicMaterial(
        id,
        GraphicMaterial.Alpha.CUTOUT,
        0.1f,
        GraphicMaterial.Cull.BACK,
        GraphicMaterial.Filter.NEAREST,
        GraphicMaterial.Wrap.CLAMP);
  }

  private static byte[] png() throws Exception {
    try (var stream =
        StylePreviewSceneTest.class.getResourceAsStream(
            "/assets/lootr_styled/textures/block/default/barrel/unlooted.png")) {
      assertNotNull(stream);
      return stream.readAllBytes();
    }
  }

  private static StylePreviewMesh scene(Map<DocumentId, GraphicTexture> textures) {
    var points =
        List.of(
            new GraphicMesh.Vertex(0, 0, 0, 0, 0, -1, 0, 0, 1),
            new GraphicMesh.Vertex(1, 0, 0, 1, 0, -1, 0, 0, 1),
            new GraphicMesh.Vertex(0, 1, 0, 0, 1, -1, 0, 0, 1));
    var triangles =
        textures.keySet().stream().map(id -> new GraphicMesh.Triangle(0, 1, 2, id)).toList();
    var materials = new java.util.HashMap<DocumentId, GraphicMaterial>();
    textures.keySet().forEach(id -> materials.put(id, material(id)));
    return new StylePreviewMesh(
        new GraphicMesh(LIMITS, points, triangles), materials, textures, Map.of());
  }

  @Test
  void actualDefaultFramesUseNativeDecodeWithoutChangingFrozenInputOrPublishing() throws Exception {
    var ledger = new GraphicLedger(LIMITS);
    var baked =
        NativeBarrelPreview.bake(
            NativeBarrelPreviewTest.inputs(false, true, true), ledger, () -> {});
    var source = StylePreviewMesh.convert(baked, LIMITS, StylePreviewSceneTest::material, () -> {});
    var frames = source.prepare(ledger, METADATA, () -> {});
    assertEquals(0, ledger.state().bytes());
    assertEquals(0, ledger.state().resources());
    long encodedBytes =
        source.textures().values().stream()
            .mapToLong(GraphicTexture::encodedBytes)
            .max()
            .orElseThrow();
    assertEquals(512L * 512 * Integer.BYTES + encodedBytes, ledger.state().peakBytes());
    assertEquals(source.textures().keySet(), frames.textures().keySet());
    for (var entry : frames.textures().entrySet()) {
      var texture = source.textures().get(entry.getKey());
      var expected = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(texture.png()));
      var actual = entry.getValue();
      assertEquals(expected.getWidth(), actual.width());
      assertEquals(expected.getHeight(), actual.height());
      assertArrayEquals(
          expected.getRGB(
              0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
          actual.argb());
    }
    assertThrows(UnsupportedOperationException.class, () -> frames.textures().clear());
  }

  @Test
  void animationUsesFirstDeclaredFrameAndBudgetsOnlyItsRetainedOutput() throws Exception {
    var id = DocumentId.parse("lootr_styled:frame");
    byte[] png = png();
    var texture =
        new GraphicTexture(
            LIMITS,
            id,
            png,
            Optional.of(
                "{\"animation\":{\"width\":256,\"height\":256,\"frames\":[3,0]}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var scene = scene(Map.of(id, texture));
    long frameBytes = 256L * 256 * Integer.BYTES;
    var exact =
        new GraphicLimits(
            LIMITS.inputBytes(),
            scene.mesh().geometryBytes() + frameBytes,
            LIMITS.decodedPixels(),
            LIMITS.resources(),
            LIMITS.nativeBytes());
    var result = scene.prepare(new GraphicLedger(exact), METADATA, () -> {});
    var actual = result.textures().get(id);
    var expected = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
    assertEquals(512, expected.getWidth());
    assertEquals(frameBytes, (long) actual.width() * actual.height() * Integer.BYTES);
    assertArrayEquals(expected.getRGB(256, 256, 256, 256, null, 0, 256), actual.argb());
  }

  @Test
  void aggregatePixelAndEncodedLimitsRefuseBeforeMalformedImagesCanReachDecoder() throws Exception {
    byte[] headerOnly = java.util.Arrays.copyOf(png(), 33);
    var first = DocumentId.parse("lootr_styled:first");
    var second = DocumentId.parse("lootr_styled:second");
    var scene =
        scene(
            Map.of(
                first,
                new GraphicTexture(LIMITS, first, headerOnly, Optional.empty()),
                second,
                new GraphicTexture(LIMITS, second, headerOnly, Optional.empty())));
    for (var budget :
        List.of(
            new GraphicLimits(
                65, LIMITS.outputBytes(), LIMITS.decodedPixels(), 2, LIMITS.nativeBytes()),
            new GraphicLimits(
                LIMITS.inputBytes(), LIMITS.outputBytes(), 512L * 512, 2, LIMITS.nativeBytes()))) {
      var failed =
          assertThrows(
              PolicyRefusal.class,
              () -> scene.prepare(new GraphicLedger(budget), METADATA, () -> {}));
      assertEquals("preview.budget", failed.report().issues().getFirst().code());
    }
    var invalid =
        assertThrows(
            PolicyRefusal.class,
            () -> scene.prepare(new GraphicLedger(LIMITS), METADATA, () -> {}));
    assertEquals("preview.texture_invalid", invalid.report().issues().getFirst().code());
  }

  @Test
  void everyChestArtworkStateTravelsThroughTheSameFrozenStillImagePath() throws Exception {
    for (boolean trapped : List.of(false, true))
      for (boolean looted : List.of(false, true)) {
        var input = NativeChestPreviewTest.input(trapped, looted, 0);
        var geometry = NativeChestPreview.bake(input, LIMITS, () -> {});
        var source =
            StylePreviewMesh.chest(
                input, geometry, LIMITS, StylePreviewSceneTest::material, () -> {});
        var result = source.prepare(new GraphicLedger(LIMITS), METADATA, () -> {});
        assertEquals(Map.of(NativeChestPreview.MATERIAL, input.texture()), source.sources());
        var pixels = result.textures().get(NativeChestPreview.MATERIAL);
        try (var stream = input.resources().open(input.texture()).orElseThrow()) {
          var expected = javax.imageio.ImageIO.read(stream);
          assertArrayEquals(
              expected.getRGB(
                  0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
              pixels.argb());
        }
      }
  }

  @Test
  void cancellationReturnsNoPartialSetAndDoesNotPoisonTheFrozenSource() throws Exception {
    var id = DocumentId.parse("lootr_styled:cancel");
    var scene = scene(Map.of(id, new GraphicTexture(LIMITS, id, png(), Optional.empty())));
    var calls = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            scene.prepare(
                new GraphicLedger(LIMITS),
                METADATA,
                () -> {
                  if (calls.incrementAndGet() == 4) throw new CancellationException("Retired");
                }));
    assertEquals(1, scene.prepare(new GraphicLedger(LIMITS), METADATA, () -> {}).textures().size());
  }

  @Test
  void outputIsBoundedAcrossFramesAndNoPartiallyDecodedSetEscapes() throws Exception {
    var first = DocumentId.parse("lootr_styled:first");
    var second = DocumentId.parse("lootr_styled:second");
    byte[] png = png();
    var scene =
        scene(
            Map.of(
                first,
                new GraphicTexture(LIMITS, first, png, Optional.empty()),
                second,
                new GraphicTexture(LIMITS, second, png, Optional.empty())));
    var oneFrame =
        new GraphicLimits(
            LIMITS.inputBytes(),
            scene.mesh().geometryBytes() + 512L * 512 * Integer.BYTES,
            LIMITS.decodedPixels(),
            LIMITS.resources(),
            LIMITS.nativeBytes());
    var failed =
        assertThrows(
            PolicyRefusal.class,
            () -> scene.prepare(new GraphicLedger(oneFrame), METADATA, () -> {}));
    assertEquals("preview.budget", failed.report().issues().getFirst().code());
    assertEquals(2, scene.prepare(new GraphicLedger(LIMITS), METADATA, () -> {}).textures().size());
  }
}
