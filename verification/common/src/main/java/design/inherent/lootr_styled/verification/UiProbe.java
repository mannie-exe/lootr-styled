package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StyleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Exercises actual native widgets, draft preservation and compact layout without user files. */
final class UiProbe {
  private UiProbe() {}

  static JsonObject inspect(Minecraft mc) {
    Screen previous = NativeUi.current();
    var settings = new StyleScreen(previous);
    NativeUi.open(settings);
    var fields =
        settings.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .toList();
    if (fields.size() != 2) throw new IllegalStateException("Missing independent profile controls");
    String original = fields.getFirst().getValue();
    fields.getFirst().setValue("bad id");
    if (button(settings, "Apply & reload").active)
      throw new IllegalStateException("Invalid draft can be applied");
    fields.getFirst().setValue("missing:kept");
    if (!button(settings, "Apply & reload").active)
      throw new IllegalStateException("Valid missing identity cannot be saved");
    NativeBindings.resize(settings, mc, 320, 240);
    var resized =
        settings.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .toList();
    if (!resized.getFirst().getValue().equals("missing:kept"))
      throw new IllegalStateException("Resize lost draft");
    for (var child : settings.children())
      if (child instanceof AbstractWidget widget) {
        if (widget.getX() < 0
            || widget.getY() < 0
            || widget.getX() + widget.getWidth() > 320
            || widget.getY() + widget.getHeight() > 240)
          throw new IllegalStateException("Compact settings widget escaped viewport");
      }
    NativeBindings.press(button(settings, "Choose"));
    var picker = NativeUi.current();
    var search =
        picker.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .findFirst()
            .orElseThrow();
    search.setValue("default");
    if (picker.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .noneMatch(b -> b.getMessage().getString().contains("Default")))
      throw new IllegalStateException("Profile search lost builtin choice");
    picker.onClose();
    var returned =
        settings.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .toList();
    if (!returned.getFirst().getValue().equals("missing:kept"))
      throw new IllegalStateException("Subscreen lost draft");
    returned.getFirst().setValue(original);
    settings.onClose();
    if (NativeUi.current() != previous)
      throw new IllegalStateException("Unchanged draft did not return to caller");
    var result = new JsonObject();
    result.addProperty("independentFields", 2);
    result.addProperty("invalidDraftDisabled", true);
    result.addProperty("missingIdentityEditable", true);
    result.addProperty("resizeAndSubscreenPreserved", true);
    result.addProperty("compactViewport", "320x240");
    result.addProperty("profileSearch", true);
    return result;
  }

  private static Button button(Screen screen, String label) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> b.getMessage().getString().equals(label))
        .findFirst()
        .orElseThrow();
  }
}
