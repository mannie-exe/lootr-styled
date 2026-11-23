package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.resources.TextureImages;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.junit.jupiter.api.Test;

/** Actual native frame defaults, not a copied dimension formula. No renderer or GPU claim. */
class NativeTextureFrameTest {
  @Test
  void admittedFrameIndicesFollowNativeDimensionsForBothStripOrientationsAndExplicitSizes() {
    for (int imageWidth : new int[] {16, 32})
      for (int imageHeight : new int[] {16, 32})
        for (int width : new int[] {-1, 16})
          for (int height : new int[] {-1, 16}) {
            var nativeMetadata =
                new AnimationMetadataSection(
                    java.util.Optional.empty(),
                    width < 0 ? java.util.Optional.empty() : java.util.Optional.of(width),
                    height < 0 ? java.util.Optional.empty() : java.util.Optional.of(height),
                    1,
                    false);
            var frame = nativeMetadata.calculateFrameSize(imageWidth, imageHeight);
            int count = imageWidth / frame.width() * (imageHeight / frame.height());
            var animation = new JsonObject();
            if (width >= 0) animation.addProperty("width", width);
            if (height >= 0) animation.addProperty("height", height);
            var frames = new JsonArray();
            frames.add(count - 1);
            animation.add("frames", frames);
            var metadata = new JsonObject();
            metadata.add("animation", animation);
            var size = new TextureImages.Size(imageWidth, imageHeight);
            assertDoesNotThrow(
                () ->
                    TextureImages.validateMetadata(
                        metadata.toString().getBytes(StandardCharsets.UTF_8), size));
            frames.add(count);
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    TextureImages.validateMetadata(
                        metadata.toString().getBytes(StandardCharsets.UTF_8), size));
          }
  }
}
