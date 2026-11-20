package design.inherent.lootr_styled.resources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Inspect dimensions before decoding; decoded pixels have a separate aggregate budget. */
public final class TextureImages {
  private static final int MAX_PIXELS = 16 * 1024 * 1024;
  private final Map<String, Size> admitted = new HashMap<>();
  private int pixels;

  public Size validate(byte[] bytes) {
    String digest = hash(bytes);
    if (admitted.containsKey(digest)) return admitted.get(digest);
    if (bytes.length < 33
        || bytes[0] != (byte) 137
        || bytes[1] != 80
        || bytes[2] != 78
        || bytes[3] != 71
        || bytes[4] != 13
        || bytes[5] != 10
        || bytes[6] != 26
        || bytes[7] != 10) {
      throw new IllegalArgumentException("Texture must be PNG");
    }
    try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IllegalArgumentException("No PNG decoder");
      var reader = readers.next();
      try {
        if (!reader.getFormatName().equalsIgnoreCase("png"))
          throw new IllegalArgumentException("Unexpected texture format");
        reader.setInput(stream, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > 4096 || height > 4096)
          throw new IllegalArgumentException("Texture dimensions exceed 4096");
        int area = Math.multiplyExact(width, height);
        if (area > MAX_PIXELS - pixels)
          throw new ResourceInputs.LimitExceeded("Decoded texture pixel budget exceeded");
        var image = reader.read(0);
        if (image == null || image.getWidth() != width || image.getHeight() != height)
          throw new IllegalArgumentException("PNG decode failed");
        image.flush();
        pixels += area;
        var size = new Size(width, height);
        admitted.put(digest, size);
        return size;
      } finally {
        reader.dispose();
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Malformed PNG", ex);
    }
  }

  public static void validateMetadata(byte[] bytes, Size image) {
    var json = JsonDocuments.object(bytes);
    if (!json.has("animation")) return;
    if (!json.get("animation").isJsonObject())
      throw new IllegalArgumentException("Invalid animation metadata");
    var animation = json.getAsJsonObject("animation");
    int frameWidth = integer(animation, "width", image.width());
    int frameHeight =
        integer(
            animation,
            "height",
            animation.has("width") ? image.height() : Math.min(image.width(), image.height()));
    if (frameWidth < 1
        || frameHeight < 1
        || frameWidth > image.width()
        || frameHeight > image.height()
        || image.width() % frameWidth != 0
        || image.height() % frameHeight != 0)
      throw new IllegalArgumentException("Invalid animation frame size");
    int count = image.width() / frameWidth * (image.height() / frameHeight);
    int time = integer(animation, "frametime", 1);
    if (time < 1 || time > 1000000)
      throw new IllegalArgumentException("Invalid animation frame time");
    if (animation.has("frames")) {
      if (!animation.get("frames").isJsonArray()
          || animation.getAsJsonArray("frames").size() > 4096)
        throw new IllegalArgumentException("Invalid animation frame list");
      for (var value : animation.getAsJsonArray("frames")) {
        int index;
        if (value.isJsonObject()) {
          index = integer(value.getAsJsonObject(), "index", -1);
          int duration = integer(value.getAsJsonObject(), "time", time);
          if (duration < 1 || duration > 1000000)
            throw new IllegalArgumentException("Invalid per-frame time");
        } else {
          index = exactInteger(value);
        }
        if (index < 0 || index >= count)
          throw new IllegalArgumentException("Animation frame outside image");
      }
    }
  }

  private static int integer(com.google.gson.JsonObject json, String field, int fallback) {
    return json.has(field) ? exactInteger(json.get(field)) : fallback;
  }

  private static int exactInteger(com.google.gson.JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Expected metadata integer");
    try {
      return new java.math.BigDecimal(value.getAsString()).intValueExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Invalid metadata integer", ex);
    }
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record Size(int width, int height) {}
}
