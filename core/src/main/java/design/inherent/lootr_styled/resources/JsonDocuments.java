package design.inherent.lootr_styled.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict bounded JSON for declarative resource inputs; no coercion or duplicate-key loss. */
public final class JsonDocuments {
  private JsonDocuments() {}

  public static JsonObject object(byte[] bytes) {
    if (bytes.length > ResourceInputs.MAX_FILE_BYTES) {
      throw new IllegalArgumentException("JSON byte budget exceeded");
    }
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException ex) {
      throw new IllegalArgumentException("Invalid UTF-8 JSON", ex);
    }
    try (var reader = new JsonReader(new StringReader(text))) {
      reader.setLenient(false);
      var result = read(reader, 0, new int[] {0});
      if (!result.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IllegalArgumentException("Expected one JSON object");
      }
      return result.getAsJsonObject();
    } catch (IOException | NumberFormatException | IllegalStateException ex) {
      throw new IllegalArgumentException("Invalid JSON document", ex);
    }
  }

  public static byte[] encode(JsonObject value) {
    return value.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
    if (depth > 64 || ++nodes[0] > 65536) {
      throw new IllegalArgumentException("JSON structural budget exceeded");
    }
    return switch (reader.peek()) {
      case BEGIN_OBJECT -> {
        var result = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          if (name.length() > 1024 || result.has(name)) {
            throw new IllegalArgumentException("Duplicate or oversized JSON key");
          }
          result.add(name, read(reader, depth + 1, nodes));
        }
        reader.endObject();
        yield result;
      }
      case BEGIN_ARRAY -> {
        var result = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) result.add(read(reader, depth + 1, nodes));
        reader.endArray();
        yield result;
      }
      case STRING -> {
        String value = reader.nextString();
        if (value.length() > 32768 || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
          throw new IllegalArgumentException("Invalid or oversized JSON string");
        }
        yield new JsonPrimitive(value);
      }
      case NUMBER -> {
        String token = reader.nextString();
        if (token.length() > 64) throw new IllegalArgumentException("Oversized JSON number");
        var value = new BigDecimal(token);
        if (!Double.isFinite(value.doubleValue())) {
          throw new IllegalArgumentException("Non-finite JSON number");
        }
        yield new JsonPrimitive(value);
      }
      case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
      case NULL -> {
        reader.nextNull();
        yield JsonNull.INSTANCE;
      }
      default -> throw new IllegalArgumentException("Unexpected JSON token");
    };
  }
}
