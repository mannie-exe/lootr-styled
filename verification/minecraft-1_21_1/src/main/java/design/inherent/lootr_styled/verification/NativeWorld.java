package design.inherent.lootr_styled.verification;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

final class NativeWorld {
  private NativeWorld() {}

  static void create(Minecraft mc) {
    net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(
        mc, design.inherent.lootr_styled.client.NativeUi.current());
  }

  static Block block(String kind) {
    String id = "lootr:lootr_" + kind;
    return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
        net.minecraft.resources.ResourceLocation.parse(id));
  }

  static String sprite(Minecraft mc, String id) {
    return mc.getTextureAtlas(
            net.minecraft.resources.ResourceLocation.parse("minecraft:textures/atlas/blocks.png"))
        .apply(net.minecraft.resources.ResourceLocation.parse(id))
        .contents()
        .name()
        .toString();
  }

  static void screenshot(Minecraft mc, String name, Runnable done) throws java.io.IOException {
    Path path = mc.gameDirectory.toPath().resolve(name + ".png");
    try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
      image.writeToFile(path);
    }
    done.run();
  }
}
