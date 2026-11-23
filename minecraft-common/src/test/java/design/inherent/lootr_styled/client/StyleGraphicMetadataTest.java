package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.resources.TextureImages;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientGraphicImages;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicTexture;
import org.junit.jupiter.api.Test;

class StyleGraphicMetadataTest {
  @Test
  void stillAlphaFollowsTheFirstDeclaredFrameRatherThanTheNativeSingleFrameShortcut()
      throws Exception {
    var image =
        new java.awt.image.BufferedImage(16, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 16; x++) image.setRGB(x, y, y < 16 ? 0xffabcdef : 0x80abcdef);
    var buffer = new java.io.ByteArrayOutputStream();
    assertTrue(javax.imageio.ImageIO.write(image, "PNG", buffer));
    var limits = new GraphicLimits(1024 * 1024, 1024 * 1024, 1024 * 1024, 8, 1024 * 1024);
    for (int frame = 0; frame < 2; frame++) {
      var texture =
          new GraphicTexture(
              limits,
              DocumentId.parse("test:still"),
              buffer.toByteArray(),
              java.util.Optional.of(
                  ("{\"animation\":{\"frames\":[" + frame + "]}}")
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      var pixels = ClientGraphicImages.decodeStill(texture, limits, StyleGraphicProvider.METADATA);
      assertEquals(16, pixels.width());
      assertEquals(16, pixels.height());
      var input =
          StyleStillMaterialsTest.scene(
              pixels.width(),
              pixels.height(),
              pixels.argb(),
              java.util.List.of(new float[] {0, 0, 1, 1}));
      var result = StyleStillMaterials.classify(input, limits, () -> {});
      assertEquals(
          frame == 0
              ? design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.OPAQUE
              : design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.TRANSLUCENT,
          result.materials().values().iterator().next().alpha());
    }
  }

  @Test
  void maximumAcceptedFrameListIsNotNarrowedByTheProviderMetadataReader() throws Exception {
    var image =
        new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xffabc123);
    var buffer = new java.io.ByteArrayOutputStream();
    assertTrue(javax.imageio.ImageIO.write(image, "PNG", buffer));
    var frames = new com.google.gson.JsonArray();
    for (int i = 0; i < 4096; i++)
      frames.add(com.google.gson.JsonParser.parseString("{\"index\":0,\"time\":1}"));
    var animation = new com.google.gson.JsonObject();
    animation.add("frames", frames);
    var meta = new com.google.gson.JsonObject();
    meta.add("animation", animation);
    byte[] bytes = meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(bytes.length > 16384);
    TextureImages.validateMetadata(bytes, new TextureImages.Size(16, 16));
    var limits = new GraphicLimits(1024 * 1024, 1024 * 1024, 1024 * 1024, 8, 1024 * 1024);
    var texture =
        new GraphicTexture(
            limits,
            DocumentId.parse("lootr_styled:frames"),
            buffer.toByteArray(),
            java.util.Optional.of(bytes));
    var result = ClientGraphicImages.decodeStill(texture, limits, StyleGraphicProvider.METADATA);
    assertEquals(16, result.width());
    assertEquals(16, result.height());
    assertEquals(0xffabc123, result.pixel(0, 0));
  }
}
