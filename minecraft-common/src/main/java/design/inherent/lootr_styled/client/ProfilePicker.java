package design.inherent.lootr_styled.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ProfilePicker extends NativePanel {
  private final Screen parent;
  private final Consumer<String> selected;
  private String query = "";
  private int page;

  ProfilePicker(Screen parent, Consumer<String> selected) {
    super(Component.literal("Lootr: Styled — profiles"));
    this.parent = parent;
    this.selected = selected;
  }

  @Override
  protected void init() {
    int w = Math.min(500, width - 24), x = (width - w) / 2;
    var search =
        addRenderableWidget(
            new EditBox(font, x, 12, w, 20, Component.literal("Search profile names and IDs")));
    search.setMaxLength(160);
    search.setValue(query);
    search.setResponder(
        text -> {
          query = text;
          page = 0;
          rebuildWidgets();
        });
    setInitialFocus(search);
    var matches =
        ClientRuntime.choices().stream()
            .filter(
                choice ->
                    (choice.id() + " " + choice.title())
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains(query.toLowerCase(java.util.Locale.ROOT)))
            .toList();
    int perPage = Math.max(1, (height - 94) / 24),
        pages = Math.max(1, (matches.size() + perPage - 1) / perPage);
    page = Math.min(page, pages - 1);
    int start = page * perPage;
    for (int i = start; i < Math.min(matches.size(), start + perPage); i++) {
      var choice = matches.get(i);
      addRenderableWidget(
          Button.builder(
                  Component.literal(choice.title() + " — " + choice.id()),
                  b -> selected.accept(choice.id().toString()))
              .bounds(x, 42 + (i - start) * 24, w, 20)
              .tooltip(Tooltip.create(Component.literal(choice.id() + "\n" + choice.source())))
              .build());
    }
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
                    Component.literal("Next (" + (page + 1) + "/" + pages + ")"),
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
  public void onClose() {
    NativeUi.open(parent);
  }
}
