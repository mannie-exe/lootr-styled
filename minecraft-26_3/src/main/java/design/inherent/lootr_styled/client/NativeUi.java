package design.inherent.lootr_styled.client;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class NativeUi {
  private NativeUi() {}

  public static Screen current() {
    return Minecraft.getInstance().gui.screen();
  }

  public static void open(Screen screen) {
    Minecraft.getInstance().gui.setScreen(screen);
  }

  static void openPath(Path path) {
    com.mojang.blaze3d.Blaze3D.openPath(path);
  }
}
