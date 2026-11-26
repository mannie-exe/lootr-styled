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

  static int[] windowSize(Minecraft mc) {
    int[] width = new int[1], height = new int[1];
    org.lwjgl.glfw.GLFW.glfwGetWindowSize(mc.getWindow().getWindow(), width, height);
    return new int[] {width[0], height[0]};
  }

  static void resizeWindow(Minecraft mc, int width, int height) {
    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
  }

  static byte[] resource(Minecraft mc, String id) throws java.io.IOException {
    try (var input =
        mc.getResourceManager()
            .getResource(net.minecraft.resources.ResourceLocation.parse(id))
            .orElseThrow()
            .open()) {
      return input.readAllBytes();
    }
  }

  static void resize(net.minecraft.client.gui.screens.Screen screen, Minecraft mc, int w, int h) {
    screen.resize(mc, w, h);
  }

  static void press(net.minecraft.client.gui.components.AbstractButton button) {
    button.onPress();
  }

  static boolean activateFocused(net.minecraft.client.gui.screens.Screen screen) {
    int key = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.space").getValue();
    return screen.keyPressed(key, 0, 0);
  }

  static boolean key(net.minecraft.client.gui.screens.Screen screen, String name) {
    return screen.keyPressed(
        com.mojang.blaze3d.platform.InputConstants.getKey(name).getValue(), 0, 0);
  }

  static boolean escape(net.minecraft.client.gui.screens.Screen screen) {
    int key = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.escape").getValue();
    return screen.keyPressed(key, 0, 0);
  }

  static String narration(net.minecraft.client.gui.narration.NarratableEntry widget) {
    var collector = new net.minecraft.client.gui.narration.ScreenNarrationCollector();
    collector.update(widget::updateNarration);
    return collector.collectNarrationText(true);
  }

  static Object screen(Minecraft mc) {
    return mc.screen;
  }

  static boolean unobscured(Minecraft mc) {
    return mc.getOverlay() == null;
  }

  static boolean ready(Minecraft mc) {
    return mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen
        && mc.getOverlay() == null;
  }

  static JsonObject capture(Minecraft mc, String mode) throws Exception {
    var result = new JsonObject();
    boolean requireDefaultArtwork =
        java.nio.file.Files.isRegularFile(
            mc.gameDirectory.toPath().resolve(".lootr-styled-default-artwork"));
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
      try (var stream = resource.open()) {
        var document =
            com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        var textures = document.getAsJsonObject("textures");
        if (requireDefaultArtwork && (textures == null || !textures.has("surface")))
          throw new IllegalStateException("Pack fixture did not select packed Default artwork");
        if (textures != null && textures.has("surface")) {
          String wanted = textures.get("surface").getAsString();
          String actual = NativeWorld.sprite(mc, wanted);
          if (!wanted.equals(actual))
            throw new IllegalStateException(
                "Default surface not stitched: " + wanted + " was " + actual);
          result.addProperty(barrel.target().toString() + "/surface", actual);
        }
      }
    }
    if (requireDefaultArtwork) {
      int count = 0;
      for (var image : ContainerTargets.LEGACY.defaultArtwork().entrySet()) {
        var id = image.getValue();
        try (var bundled =
            ClientRuntime.class.getResourceAsStream(
                "/assets/" + id.namespace() + "/" + id.path())) {
          if (bundled == null
              || !java.util.Arrays.equals(
                  bundled.readAllBytes(), resource(mc, image.getKey().toString())))
            throw new IllegalStateException(
                "Pack fixture did not publish the packaged Default image: " + image.getKey());
          count++;
        }
      }
      result.addProperty("defaultArtworkImagesVerified", count);
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
      var countFile = mc.gameDirectory.toPath().resolve(".lootr-styled-expected-private-sprites");
      if (java.nio.file.Files.isRegularFile(countFile)) {
        if (java.nio.file.Files.size(countFile) > 3)
          throw new IllegalStateException("Unbounded sprite expectation");
        int expected = Integer.parseInt(java.nio.file.Files.readString(countFile));
        if (expected < 0 || expected > 128 || seen.size() != expected)
          throw new IllegalStateException(
              "Private sprite count differs: " + seen.size() + " expected " + expected);
      } else if (seen.size() < 6)
        throw new IllegalStateException("Local fixture did not exercise private model textures");
    }
    result.add("privateSprites", privateSprites);
    var status = new JsonArray();
    ClientRuntime.status().forEach(status::add);
    result.add("status", status);
    result.add("resources", resources);
    result.add("barrelLighting", barrels(mc));
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

  @SuppressWarnings(
      "deprecation") // Deliberately query the admitted plain native child on both loaders.
  private static JsonArray barrels(Minecraft mc) throws Exception {
    var observations = new JsonArray();
    var targets = ContainerTargets.LEGACY.barrels();
    var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    var openProperty = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;
    var barrelBlock = NativeWorld.block("barrel");
    for (var orientation : net.minecraft.core.Direction.values()) {
      for (boolean open : List.of(false, true)) {
        var state =
            barrelBlock
                .defaultBlockState()
                .setValue(facing, orientation)
                .setValue(openProperty, open);
        var wrapper = mc.getBlockRenderer().getBlockModel(state);
        for (boolean looted : List.of(false, true)) {
          String field = looted ? "opened" : "unopened";
          var childField = wrapper.getClass().getDeclaredField(field);
          childField.setAccessible(true);
          var child = (net.minecraft.client.resources.model.BakedModel) childField.get(wrapper);
          if (!design.inherent.lootr_styled.client.BarrelLighting.generated(child))
            throw new IllegalStateException("Generated barrel marker did not reach native bake");
          var target =
              targets.stream()
                  .filter(
                      t ->
                          t.open() == open
                              && t.material().equals(looted ? "opened_barrel" : "barrel"))
                  .findFirst()
                  .orElseThrow();
          var source =
              com.google.gson.JsonParser.parseString(
                      new String(
                          resource(mc, target.target().toString()),
                          java.nio.charset.StandardCharsets.UTF_8))
                  .getAsJsonObject();
          boolean expected =
              !source.has("ambientocclusion") || source.get("ambientocclusion").getAsBoolean();
          if (child.useAmbientOcclusion() != expected)
            throw new IllegalStateException("Native barrel leaf lost its AO policy");
          var item = new JsonObject();
          item.addProperty("facing", orientation.getName());
          var random = net.minecraft.util.RandomSource.create(42);
          var quads = child.getQuads(state, null, random);
          if (quads.isEmpty())
            throw new IllegalStateException("Generated barrel has no native quads");
          long queryStart = System.nanoTime();
          for (int repeat = 0; repeat < 100; repeat++)
            if (child.getQuads(state, null, random) != quads)
              throw new IllegalStateException("Repeated state query rebuilt native leaf quads");
          item.addProperty("cachedQueries", 100);
          item.addProperty("cachedQueryNanos", System.nanoTime() - queryStart);
          item.addProperty("unculledQuads", quads.size());
          item.addProperty("physicalOpen", open);
          item.addProperty("looted", looted);
          item.addProperty("generated", true);
          item.addProperty("leafAmbientOcclusion", expected);
          if (wrapper.getClass().getName().contains(".neoforge.")) {
            Class<?> dataClass =
                Class.forName("net.neoforged.neoforge.client.model.data.ModelData");
            Class<?> constants =
                Class.forName("noobanidus.mods.lootr.neoforge.block.ModelDataConstants");
            Object data =
                constants.getField(looted ? "OPENED_MODEL_DATA" : "CLOSED_MODEL_DATA").get(null);
            var method =
                wrapper
                    .getClass()
                    .getMethod(
                        "useAmbientOcclusion",
                        net.minecraft.world.level.block.state.BlockState.class,
                        dataClass,
                        net.minecraft.client.renderer.RenderType.class);
            method.setAccessible(true);
            String actual = method.invoke(wrapper, state, data, null).toString();
            if (!actual.equals(expected ? "DEFAULT" : "FALSE"))
              throw new IllegalStateException(
                  "Native wrapper ignored the selected barrel AO policy: " + actual);
            item.addProperty("wrapperAmbientOcclusion", actual);
            var renderTypes =
                wrapper
                    .getClass()
                    .getMethod(
                        "getRenderTypes",
                        net.minecraft.world.level.block.state.BlockState.class,
                        net.minecraft.util.RandomSource.class,
                        dataClass);
            renderTypes.setAccessible(true);
            var wrapperLayers = renderTypes.invoke(wrapper, state, random, data);
            var childTypes =
                child
                    .getClass()
                    .getMethod(
                        "getRenderTypes",
                        net.minecraft.world.level.block.state.BlockState.class,
                        net.minecraft.util.RandomSource.class,
                        dataClass);
            childTypes.setAccessible(true);
            var childLayers = childTypes.invoke(child, state, random, data);
            var setType = Class.forName("net.neoforged.neoforge.client.ChunkRenderTypeSet");
            var list = setType.getMethod("asList");
            item.addProperty("wrapperChunkRenderTypes", list.invoke(wrapperLayers).toString());
            item.addProperty("leafChunkRenderTypes", list.invoke(childLayers).toString());
          } else {
            var materialField = wrapper.getClass().getDeclaredField("lootrStyled$material");
            materialField.setAccessible(true);
            Object cached = materialField.get(wrapper);
            item.addProperty("renderedMaterialCached", cached != null);
            if (cached != null) {
              Object material =
                  cached.getClass().getMethod("withoutAmbientOcclusion").invoke(cached);
              Object ao =
                  Class.forName("net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial")
                      .getMethod("ambientOcclusion")
                      .invoke(material);
              item.addProperty("renderedAmbientOcclusion", ao.toString());
              if (!ao.toString().equals("FALSE"))
                throw new IllegalStateException(
                    "Fabric emitted a generated barrel with AO enabled");
            }
          }
          observations.add(item);
        }
      }
    }
    return observations;
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
