package design.inherent.lootr_styled.client;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class NativeUi {
  private NativeUi() {}

  public static Screen current() {
    return Minecraft.getInstance().screen;
  }

  public static void open(Screen screen) {
    Minecraft.getInstance().setScreen(screen);
  }

  static design.inherent.lootr_styled.resources.ResourceAccess artworkSources() {
    return new NativeSources(Minecraft.getInstance().getResourceManager(), true);
  }

  static void openPath(Path path) {
    net.minecraft.Util.getPlatform().openPath(path);
  }

  static design.inherent.lootr_styled.resources.ContainerTargets targets() {
    return design.inherent.lootr_styled.resources.ContainerTargets.LEGACY;
  }

  static boolean ordinaryChestIsolated() {
    return false;
  }

  static void validateModel(byte[] bytes) {
    net.minecraft.client.renderer.block.model.BlockModel.fromString(
        new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
  }

  static java.util.Optional<GeneratedPack> activePack() {
    var client = Minecraft.getInstance();
    if (client == null || client.getResourceManager() == null) return java.util.Optional.empty();
    return client
        .getResourceManager()
        .listPacks()
        .filter(GeneratedPack.class::isInstance)
        .map(GeneratedPack.class::cast)
        .findFirst();
  }

  static java.util.concurrent.CompletableFuture<Void> reloadResources() {
    return Minecraft.getInstance().reloadResourcePacks();
  }
}
