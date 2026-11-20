package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import org.junit.jupiter.api.Test;

/** Native deserialization only: no sprite baking, GPU, live client or appearance qualification. */
class NativeBarrelModelTest {
  private static String source() throws Exception {
    try (var input =
        NativeBarrelModelTest.class.getResourceAsStream(
            "/assets/lootr_styled/models/block/barrel_clasps.json")) {
      assertNotNull(input);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  // NeoForge deprecates the vanilla parser in favor of custom-loader dispatch. This fixture
  // deliberately parses the same plain cuboids on both loaders, without custom-loader setup.
  @SuppressWarnings("deprecation")
  private static CuboidModel parse(String json) {
    return CuboidModel.fromStream(new java.io.StringReader(json));
  }

  @Test
  void nativeDeserializerAcceptsTheRetainedGeometry() throws Exception {
    String json = source();
    var model = parse(json);
    assertEquals(169, ((UnbakedCuboidGeometry) model.geometry()).elements().size());
  }

  @Test
  void nativeDeserializerRefusesOutOfEnvelopeElements() throws Exception {
    var value = JsonParser.parseString(source()).getAsJsonObject();
    value
        .getAsJsonArray("elements")
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("from")
        .set(0, new com.google.gson.JsonPrimitive(-17));
    String json = value.toString();
    assertThrows(JsonParseException.class, () -> parse(json));
  }
}
