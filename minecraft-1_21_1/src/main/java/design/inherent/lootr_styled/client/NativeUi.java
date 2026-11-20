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

  static void openPath(Path path) {
    net.minecraft.Util.getPlatform().openPath(path);
  }
}
