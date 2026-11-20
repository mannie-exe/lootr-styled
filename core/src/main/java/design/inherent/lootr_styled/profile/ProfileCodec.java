package design.inherent.lootr_styled.profile;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import design.inherent.lootr_styled.ResourceId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Strict, bounded profile metadata. Identity and stream lifetime belong to the source adapter. */
public final class ProfileCodec {
  public static final int MAX_METADATA_BYTES = 256 * 1024;

  private ProfileCodec() {}

  /** Reads at most the limit plus one byte. Does not close the caller's stream. */
  public static StyleProfile read(ResourceId id, InputStream input) throws IOException {
    return parse(id, input.readNBytes(MAX_METADATA_BYTES + 1));
  }

  public static StyleProfile parse(ResourceId id, byte[] utf8) {
    if (utf8.length > MAX_METADATA_BYTES) {
      throw new IllegalArgumentException("Profile metadata exceeds the byte limit");
    }
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try (var reader =
        new JsonReader(new InputStreamReader(new ByteArrayInputStream(utf8), decoder))) {
      reader.setLenient(false);
      reader.beginObject();
      var fields = new HashSet<String>();
      String title = id.toString();
      boolean format = false;
      Map<ResourceId, ResourceId> models = Map.of();
      Map<ResourceId, ResourceId> artwork = Map.of();
      while (reader.hasNext()) {
        String key = reader.nextName();
        if (!fields.add(key)) {
          throw new IllegalArgumentException("Duplicate profile property");
        }
        switch (key) {
          case "format" -> {
            if (reader.peek() != JsonToken.NUMBER || !reader.nextString().equals("1")) {
              throw new IllegalArgumentException("Profile format must be integer 1");
            }
            format = true;
          }
          case "title" -> title = string(reader);
          case "models" -> models = mappings(reader, false);
          case "artwork" -> artwork = mappings(reader, true);
          default -> throw new IllegalArgumentException("Unknown profile property");
        }
      }
      reader.endObject();
      if (!format || reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IllegalArgumentException("Profile requires format 1 and exactly one JSON object");
      }
      return new StyleProfile(id, title, models, artwork);
    } catch (IOException | IllegalStateException ex) {
      throw new IllegalArgumentException("Invalid profile JSON or UTF-8", ex);
    }
  }

  private static Map<ResourceId, ResourceId> mappings(JsonReader reader, boolean artwork)
      throws IOException {
    reader.beginObject();
    var result = new HashMap<ResourceId, ResourceId>();
    while (reader.hasNext()) {
      var target = ResourceId.parse(reader.nextName());
      var source = ResourceId.parse(string(reader));
      if (!kind(target.path(), artwork) || !kind(source.path(), artwork)) {
        throw new IllegalArgumentException("Profile resource has an incompatible file kind");
      }
      if (result.putIfAbsent(target, source) != null) {
        throw new IllegalArgumentException("Duplicate profile resource target");
      }
      if (result.size() > StyleProfile.MAX_MAPPINGS) {
        throw new IllegalArgumentException("Profile has too many resource mappings");
      }
    }
    reader.endObject();
    return result;
  }

  private static boolean kind(String path, boolean artwork) {
    return artwork
        ? path.endsWith(".png") || path.endsWith(".png.mcmeta")
        : path.endsWith(".json") || path.endsWith(".jem") || path.endsWith(".jpm");
  }

  private static String string(JsonReader reader) throws IOException {
    if (reader.peek() != JsonToken.STRING) {
      throw new IllegalArgumentException("Profile field requires a JSON string");
    }
    return reader.nextString();
  }
}
