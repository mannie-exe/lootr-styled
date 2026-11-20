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
import net.minecraft.resources.Identifier;

/** Pinned native observer: private-field access is diagnostic and never a production adapter. */
final class NativeBindings {
  private NativeBindings() {}

  static void resize(net.minecraft.client.gui.screens.Screen screen, Minecraft mc, int w, int h) {
    screen.resize(w, h);
  }

  static void press(net.minecraft.client.gui.components.Button button) {
    button.onPress(
        new net.minecraft.client.input.InputWithModifiers() {
          public int input() {
            return 40;
          }

          public int modifiers() {
            return 0;
          }
        });
  }

  static Object screen(Minecraft mc) {
    return mc.gui.screen();
  }

  static boolean ready(Minecraft mc) {
    return mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen
        && mc.gui.overlay() == null;
  }

  static JsonObject capture(Minecraft mc, String mode) throws Exception {
    var result = new JsonObject();
    if (!mode.equals("native")
        && mc.getResourceManager()
            .getResource(Identifier.parse("minecraft:emf/cem/chest.jem"))
            .isEmpty()) {
      throw new IllegalStateException(
          "Fresh fixture requires its source rig in the actual selected resource stack");
    }

    var resources = new JsonObject();
    for (var barrel : ContainerTargets.MODERN.barrels()) {
      var resource =
          mc.getResourceManager()
              .getResource(Identifier.parse(barrel.target().toString()))
              .orElseThrow();
      resources.addProperty(barrel.target().toString(), resource.sourcePackId());
      if (!resource.sourcePackId().equals("lootr_styled/generated"))
        throw new IllegalStateException(
            "Expected generated Default barrel slot: "
                + resource.sourcePackId()
                + " known: "
                + resource.knownPackInfo());
    }
    var privateSprites = new JsonArray();
    if (java.nio.file.Files.exists(
        mc.gameDirectory.toPath().resolve(".lootr-styled-local-profile"))) {
      var seen = new java.util.HashSet<String>();
      for (var barrel : ContainerTargets.MODERN.barrels()) {
        try (var input =
            mc.getResourceManager()
                .getResource(Identifier.parse(barrel.target().toString()))
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
      if (!List.of("lootr:chest", "lootr:trapped_chest").contains(id)) continue;
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
      int expected = mode.equals("fresh") && id.equals("lootr:trapped_chest") ? 4 : 3;
      if (count != expected)
        throw new IllegalStateException(
            "Expected pinned source/native cube count " + expected + ": " + id + " was " + count);
      if (!mode.equals("native")) info.add("variantSelection", variants(renderer));
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
    for (var part : root.getAllParts().stream().limit(8193).toList()) {
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

  private static JsonObject variants(Object renderer) throws Exception {
    var result = new JsonObject();
    Object[] selected = new Object[1];
    var collector =
        (net.minecraft.client.renderer.SubmitNodeCollector)
            java.lang.reflect.Proxy.newProxyInstance(
                NativeBindings.class.getClassLoader(),
                new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("submitModel")) selected[0] = arguments[0];
                  if (method.getReturnType().isInstance(proxy)) return proxy;
                  return null;
                });
    var submit =
        renderer
            .getClass()
            .getMethod(
                "submit",
                noobanidus.mods.lootr.common.client.state.LootrChestBlockRenderState.class,
                com.mojang.blaze3d.vertex.PoseStack.class,
                net.minecraft.client.renderer.SubmitNodeCollector.class,
                net.minecraft.client.renderer.state.level.CameraRenderState.class);
    Object ordinary = null;
    for (var type : noobanidus.mods.lootr.common.api.LootrChestType.values()) {
      var state = new noobanidus.mods.lootr.common.client.state.LootrChestBlockRenderState();
      state.chestType = type;
      state.facing = net.minecraft.core.Direction.SOUTH;
      state.open = 0.75f;
      selected[0] = null;
      submit.invoke(renderer, state, new com.mojang.blaze3d.vertex.PoseStack(), collector, null);
      if (selected[0] == null)
        throw new IllegalStateException("Native submit did not choose a model");
      if (type == noobanidus.mods.lootr.common.api.LootrChestType.NORMAL) ordinary = selected[0];
      boolean copper =
          type != noobanidus.mods.lootr.common.api.LootrChestType.NORMAL
              && type != noobanidus.mods.lootr.common.api.LootrChestType.TRAPPED;
      if (copper && selected[0] == ordinary)
        throw new IllegalStateException("Copper retained the styled ordinary model");
      ModelPart root = null;
      for (Class<?> model = selected[0].getClass();
          model != Object.class;
          model = model.getSuperclass()) {
        for (var field : model.getDeclaredFields()) {
          if (!field.getName().equals("root") || field.getType() != ModelPart.class) continue;
          field.setAccessible(true);
          root = (ModelPart) field.get(selected[0]);
        }
      }
      if (root == null)
        throw new IllegalStateException("Selected native model has no observable root");
      var data = part(root, type.name(), new IdentityHashMap<>());
      if (copper && (data.get("cubes").getAsInt() != 3 || !root.getClass().equals(ModelPart.class)))
        throw new IllegalStateException("Copper must keep the independent native three-cube layer");
      result.add(type.name(), data);
    }
    return result;
  }
}
