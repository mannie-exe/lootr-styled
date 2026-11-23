package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextureFrameTest {
  @Test
  void implicitSquareFramesAcceptBothHorizontalAndVerticalStrips() {
    byte[] metadata = "{\"animation\":{\"frames\":[1,0]}}".getBytes(StandardCharsets.UTF_8);
    assertDoesNotThrow(
        () -> TextureImages.validateMetadata(metadata, new TextureImages.Size(32, 16)));
    assertDoesNotThrow(
        () -> TextureImages.validateMetadata(metadata, new TextureImages.Size(16, 32)));
  }
}
