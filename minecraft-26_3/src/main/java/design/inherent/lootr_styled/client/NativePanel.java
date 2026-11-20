package design.inherent.lootr_styled.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class NativePanel extends Screen {
  NativePanel(Component title) {
    super(title);
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
    extractBackground(graphics, mouseX, mouseY, partial);
    super.extractRenderState(graphics, mouseX, mouseY, partial);
  }
}
