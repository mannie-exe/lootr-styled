package design.inherent.lootr_styled.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Bounded, wrapped, keyboard-accessible diagnostics; never scans resources while rendering. */
final class ReasonScreen extends NativePanel {
  private final Screen parent;
  private final List<String> messages;
  private int page;
  private String narration = "";

  ReasonScreen(Screen parent, List<String> messages) {
    super(Component.literal("Lootr: Styled — effective values"));
    this.parent = parent;
    this.messages = List.copyOf(messages);
  }

  @Override
  protected void init() {
    int w = Math.min(600, width - 32), x = (width - w) / 2;
    var lines = new ArrayList<String>();
    for (String message : messages)
      font.getSplitter()
          .splitLines(message, w, Style.EMPTY)
          .forEach(line -> lines.add(line.getString()));
    int perPage = Math.max(1, (height - 76) / 13),
        pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
    page = Math.min(page, pages - 1);
    addRenderableWidget(
        new StringWidget(
            x,
            12,
            w,
            12,
            Component.literal("Effective values / reasons — " + (page + 1) + "/" + pages),
            font));
    var visible =
        lines.subList(
            Math.min(lines.size(), page * perPage), Math.min(lines.size(), (page + 1) * perPage));
    for (int i = 0; i < visible.size(); i++)
      addRenderableWidget(
          new StringWidget(x, 34 + i * 13, w, 12, Component.literal(visible.get(i)), font));
    narration = String.join(". ", visible);
    var previous =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Previous"),
                    b -> {
                      page--;
                      rebuildWidgets();
                    })
                .bounds(x, height - 48, (w - 4) / 2, 20)
                .build());
    var next =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Next"),
                    b -> {
                      page++;
                      rebuildWidgets();
                    })
                .bounds(x + (w + 4) / 2, height - 48, (w - 4) / 2, 20)
                .build());
    previous.active = page > 0;
    next.active = page + 1 < pages;
    addRenderableWidget(
        Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(x, height - 24, w, 20)
            .build());
  }

  @Override
  public Component getNarrationMessage() {
    return Component.literal(title.getString() + ". " + narration);
  }

  @Override
  public void onClose() {
    NativeUi.open(parent);
  }
}
