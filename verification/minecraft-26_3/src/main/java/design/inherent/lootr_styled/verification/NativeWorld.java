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
        .getAtlasOrThrow(
            net.minecraft.resources.Identifier.parse("minecraft:textures/atlas/blocks.png"))
        .getSprite(net.minecraft.resources.Identifier.parse(id))
        .contents()
        .name()
        .toString();
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
