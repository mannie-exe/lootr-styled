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
