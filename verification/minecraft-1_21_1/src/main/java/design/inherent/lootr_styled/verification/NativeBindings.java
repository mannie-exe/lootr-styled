package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.resources.ContainerTargets;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Pinned native observer: private-field access is diagnostic and never a production adapter. */
final class NativeBindings {
  private NativeBindings() {}

  static void resize(net.minecraft.client.gui.screens.Screen screen, Minecraft mc, int w, int h) {
    screen.resize(mc, w, h);
  }

  static void press(net.minecraft.client.gui.components.Button button) {
    button.onPress();
  }

  static Object screen(Minecraft mc) {
    return mc.screen;
  }

  static boolean ready(Minecraft mc) {
    return mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen
        && mc.getOverlay() == null;
  }

  static JsonObject capture(Minecraft mc, String mode) throws Exception {
    var result = new JsonObject();
    if (!mode.equals("native")
        && mc.getResourceManager()
            .getResource(ResourceLocation.parse("minecraft:emf/cem/chest.jem"))
            .isEmpty()) {
      throw new IllegalStateException(
          "Fresh fixture requires its source rig in the actual selected resource stack");
    }

    var resources = new JsonObject();
    for (var barrel : ContainerTargets.LEGACY.barrels()) {
      var resource =
          mc.getResourceManager()
              .getResource(ResourceLocation.parse(barrel.target().toString()))
              .orElseThrow();
      resources.addProperty(barrel.target().toString(), resource.sourcePackId());
      if (!resource.sourcePackId().equals("lootr_styled/generated"))
        throw new IllegalStateException("Expected generated Default barrel slot");
    }
    var privateSprites = new JsonArray();
    if (java.nio.file.Files.exists(
        mc.gameDirectory.toPath().resolve(".lootr-styled-local-profile"))) {
      var seen = new java.util.HashSet<String>();
      for (var barrel : ContainerTargets.LEGACY.barrels()) {
        try (var input =
            mc.getResourceManager()
                .getResource(ResourceLocation.parse(barrel.target().toString()))
                .orElseThrow()
                .open()) {
          var model =
              com.google.gson.JsonParser.parseReader(
                      new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))
                  .getAsJsonObject();
          for (var value : model.getAsJsonObject("textures").entrySet()) {
            String id = value.getValue().getAsString();
            if (!id.startsWith("lootr_styled:block/generated/") || !seen.add(id)) continue;
            String actual = NativeWorld.sprite(mc, id);
            if (!id.equals(actual))
              throw new IllegalStateException(
                  "Private local sprite not stitched: " + id + " was " + actual);
            privateSprites.add(id);
          }
        }
      }
      if (seen.size() < 6)
        throw new IllegalStateException("Local fixture did not exercise private model textures");
    }
    result.add("privateSprites", privateSprites);
    var status = new JsonArray();
    ClientRuntime.status().forEach(status::add);
    result.add("status", status);
    result.add("resources", resources);
    Object dispatcher = mc.getBlockEntityRenderDispatcher();
    Field mapField = dispatcher.getClass().getDeclaredField("renderers");
    mapField.setAccessible(true);
    Map<?, ?> renderers = (Map<?, ?>) mapField.get(dispatcher);
    var containers = new JsonObject();
    var identities = new IdentityHashMap<ModelPart, String>();
    for (var entry : renderers.entrySet()) {
      if (!(entry.getKey()
          instanceof net.minecraft.world.level.block.entity.BlockEntityType<?> type)) continue;
      String id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString();
      if (!List.of("lootr:lootr_chest", "lootr:lootr_trapped_chest").contains(id)) continue;
      Object renderer = entry.getValue();
      var info = new JsonObject();
      info.addProperty("renderer", renderer.getClass().getName());
      var roots = new JsonObject();
      for (Class<?> owner = renderer.getClass();
          owner != Object.class;
          owner = owner.getSuperclass()) {
        for (var field : owner.getDeclaredFields()) {
          if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
          field.setAccessible(true);
          Object value = field.get(renderer);
          if (value instanceof ModelPart part
              && List.of("bottom", "lid", "lock").contains(field.getName()))
            roots.add(field.getName(), part(part, id, identities));
          if (field.getName().equals("singleModel") && value != null) {
            for (Class<?> model = value.getClass();
                model != Object.class;
                model = model.getSuperclass()) {
              for (var candidate : model.getDeclaredFields()) {
                if (candidate.getType() != ModelPart.class || !candidate.getName().equals("root"))
                  continue;
                candidate.setAccessible(true);
                roots.add("root", part((ModelPart) candidate.get(value), id, identities));
              }
            }
          }
        }
      }
      if (roots.isEmpty())
        throw new IllegalStateException("Native observer could not inspect single chest model");
      int count =
          roots.entrySet().stream()
              .mapToInt(e -> e.getValue().getAsJsonObject().get("cubes").getAsInt())
              .sum();
      if (count < 3)
        throw new IllegalStateException("Native fixture lost ordinary chest geometry: " + id);
      boolean pack =
          java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-pack"));
      int expected = pack ? 11 : mode.equals("fresh") && id.endsWith("trapped_chest") ? 4 : 3;
      if (count != expected)
        throw new IllegalStateException(
            "Expected pinned source/native cube count " + expected + ": " + id + " was " + count);
      info.add("parts", roots);
      containers.add(id, info);
    }
    if (containers.size() != 2)
      throw new IllegalStateException("Expected both native Lootr single-chest renderers");
    result.add("containers", containers);
    return result;
  }

  private static JsonObject part(
      ModelPart root, String owner, IdentityHashMap<ModelPart, String> identities)
      throws Exception {
    var result = new JsonObject();
    result.addProperty("class", root.getClass().getName());
    var cubes = new JsonArray();
    int nodes = 0;
    var cubeField = ModelPart.class.getDeclaredField("cubes");
    cubeField.setAccessible(true);
    for (var part : root.getAllParts().limit(8193).toList()) {
      if (++nodes > 8192) throw new IllegalStateException("Native part observer bound exceeded");
      String previous = identities.putIfAbsent(part, owner);
      if (previous != null && !previous.equals(owner))
        throw new IllegalStateException("Separate chest renderers alias a live model part");
      for (Object cube : (List<?>) cubeField.get(part)) {
        var bounds = new JsonArray();
        for (String name : List.of("minX", "minY", "minZ", "maxX", "maxY", "maxZ"))
          bounds.add(((Number) cube.getClass().getField(name).get(cube)).floatValue());
        cubes.add(bounds);
      }
    }
    result.addProperty("nodes", nodes);
    result.addProperty("cubes", cubes.size());
    result.add("bounds", cubes);
    return result;
  }
}
