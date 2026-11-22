package design.inherent.lootr_styled.verification;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

final class NativeWorld {
  private NativeWorld() {}

  static void create(Minecraft mc) {
    net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(
        mc,
        () ->
            design.inherent.lootr_styled.client.NativeUi.open(
                new net.minecraft.client.gui.screens.TitleScreen()));
  }

  static Block block(String kind) {
    String id = "lootr:" + kind;
    return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
        net.minecraft.resources.Identifier.parse(id));
  }

  static String sprite(Minecraft mc, String id) {
    return mc.getAtlasManager()
        .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS)
        .getSprite(net.minecraft.resources.Identifier.parse(id))
        .contents()
        .name()
        .toString();
  }

  static com.google.gson.JsonArray barrelCulling(Minecraft mc) {
    var observations = new com.google.gson.JsonArray();
    var position = new net.minecraft.core.BlockPos(14, 101, 2);
    for (var facing : net.minecraft.core.Direction.values())
      for (boolean open : java.util.List.of(false, true)) {
        var barrel =
            block("barrel")
                .defaultBlockState()
                .setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                    facing)
                .setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN,
                    open);
        if (!barrel.isCollisionShapeFullBlock(mc.level, position))
          throw new IllegalStateException("Presentation changed native full-cube collision");
        for (var neighbor :
            java.util.List.of(
                net.minecraft.world.level.block.Blocks.STONE,
                net.minecraft.world.level.block.Blocks.GLASS,
                net.minecraft.world.level.block.Blocks.STONE_SLAB))
          for (var face : net.minecraft.core.Direction.values()) {
            var state = neighbor.defaultBlockState();
            boolean visible = neighborFace(mc, position, state, barrel, face);
            var vanilla =
                net.minecraft.world.level.block.Blocks.BARREL
                    .defaultBlockState()
                    .setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties
                            .FACING,
                        facing)
                    .setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN,
                        open);
            boolean expected = neighborFace(mc, position, state, vanilla, face);
            var item = new com.google.gson.JsonObject();
            item.addProperty("facing", facing.getName());
            item.addProperty("physicalOpen", open);
            item.addProperty("neighbor", neighbor.getClass().getSimpleName());
            item.addProperty("face", face.getName());
            item.addProperty("neighborFaceVisible", visible);
            item.addProperty("vanillaControlVisible", expected);
            if (visible != expected)
              throw new IllegalStateException("Barrel changed native neighbor culling: " + item);
            observations.add(item);
          }
      }
    return observations;
  }

  @SuppressWarnings(
      "deprecation") // Shared native state-pair control, without external face-hiding hooks.
  private static boolean neighborFace(
      Minecraft mc,
      net.minecraft.core.BlockPos position,
      net.minecraft.world.level.block.state.BlockState state,
      net.minecraft.world.level.block.state.BlockState occluder,
      net.minecraft.core.Direction face) {
    return net.minecraft.world.level.block.Block.shouldRenderFace(
        state, occluder, face.getOpposite());
  }

  static void screenshot(Minecraft mc, String name, Runnable done) throws java.io.IOException {
    Path path = mc.gameDirectory.toPath().resolve(name + ".png");
    net.minecraft.client.Screenshot.takeScreenshot(
        mc.gameRenderer.mainRenderTarget(),
        image -> {
          try (image) {
            image.writeToFile(path);
          } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
          }
          done.run();
        });
  }
}
