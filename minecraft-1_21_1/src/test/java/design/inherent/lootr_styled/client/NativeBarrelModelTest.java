package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import design.inherent.lootr_styled.resources.ContainerTargets;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;

/** Native deserialization only: no sprite baking, GPU, live client or appearance qualification. */
class NativeBarrelModelTest {
  private static String source(ContainerTargets.Barrel barrel, boolean packed) throws Exception {
    var id = BarrelCompiler.defaultModel(barrel, packed);
    try (var input =
        NativeBarrelModelTest.class.getResourceAsStream(
            "/assets/" + id.namespace() + "/" + id.path())) {
      assertNotNull(input);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  // NeoForge deprecates this vanilla-only getter for custom loaders. This fixture deliberately
  // checks the retained vanilla cuboids on both loaders, without baking or custom geometry.
  @SuppressWarnings("deprecation")
  void nativeDeserializerAcceptsTheRetainedGeometry() throws Exception {
    for (var barrel : ContainerTargets.LEGACY.barrels()) {
      for (boolean packed : java.util.List.of(false, true)) {
        var model = BlockModel.fromString(source(barrel, packed));
        assertEquals(barrel.open() ? 25 : 32, model.getElements().size());
      }
    }
  }

  @Test
  void nativeDeserializerRefusesOutOfEnvelopeElements() throws Exception {
    var value =
        JsonParser.parseString(source(ContainerTargets.LEGACY.barrels().getFirst(), false))
            .getAsJsonObject();
    value
        .getAsJsonArray("elements")
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("from")
        .set(0, new com.google.gson.JsonPrimitive(-17));
    String json = value.toString();
    assertThrows(JsonParseException.class, () -> BlockModel.fromString(json));
  }
}
