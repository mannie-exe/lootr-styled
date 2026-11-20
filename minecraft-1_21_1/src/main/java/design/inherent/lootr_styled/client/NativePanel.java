package design.inherent.lootr_styled.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class NativePanel extends Screen {
  NativePanel(Component title) {
    super(title);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    renderBackground(graphics, mouseX, mouseY, partial);
    super.render(graphics, mouseX, mouseY, partial);
  }
}
