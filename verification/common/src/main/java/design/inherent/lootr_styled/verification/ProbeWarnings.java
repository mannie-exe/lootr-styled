package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Handles known fixture startup dialogs; unexpected loading errors remain fatal. */
final class ProbeWarnings {
  private static boolean acknowledged;
  private static boolean audioDeferred;
  private static boolean experimentalAcknowledged;

  private ProbeWarnings() {}

  static JsonArray advance(Object screen) throws ReflectiveOperationException {
    var game = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
    boolean packFixture = java.nio.file.Files.isRegularFile(game.resolve(".lootr-styled-pack"));
    if (!experimentalAcknowledged
        && packFixture
        && java.nio.file.Files.isRegularFile(game.resolve(".lootr-styled-scene"))
        && screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirmation
        && confirmation.getTitle().getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents title
        && title.getKey().equals("selectWorld.warning.experimental.title")) {
      var yes =
          confirmation.children().stream()
              .filter(net.minecraft.client.gui.components.Button.class::isInstance)
              .map(net.minecraft.client.gui.components.Button.class::cast)
              .filter(
                  button ->
                      button.getMessage().getContents()
                              instanceof
                              net.minecraft.network.chat.contents.TranslatableContents text
                          && text.getKey().equals("gui.yes"))
              .findFirst()
              .orElseThrow();
      if (!yes.active) return null;
      NativeBindings.press(yes);
      experimentalAcknowledged = true;
      var report = new JsonArray();
      report.add("Accepted experimental settings for the disposable pack scene");
      return report;
    }
    if (!audioDeferred
        && screen instanceof net.minecraft.client.gui.screens.Screen nativeScreen
        && screen
            .getClass()
            .getName()
            .equals("com.palm1.analoglib.client.gui.dependency.DependencyWelcomeScreen")
        && packFixture) {
      var later =
          nativeScreen.children().stream()
              .filter(net.minecraft.client.gui.components.Button.class::isInstance)
              .map(net.minecraft.client.gui.components.Button.class::cast)
              .filter(
                  button ->
                      button.getMessage().getContents()
                              instanceof
                              net.minecraft.network.chat.contents.TranslatableContents text
                          && text.getKey().equals("gui.analoglib.welcome.decline"))
              .findFirst()
              .orElseThrow();
      // The native screen disables every action for its first thirty ticks.
      if (!later.active) return null;
      NativeBindings.press(later);
      audioDeferred = true;
      var report = new JsonArray();
      report.add("Deferred optional AnalogLib binary installation in the owned pack fixture");
      return report;
    }
    if (screen == null
        || !screen
            .getClass()
            .getName()
            .equals("net.neoforged.neoforge.client.gui.LoadingErrorScreen")) return null;
    if (acknowledged) return null;
    Class<?> type = screen.getClass();
    var errors = type.getDeclaredField("modLoadErrors");
    var warnings = type.getDeclaredField("modLoadWarnings");
    errors.setAccessible(true);
    warnings.setAccessible(true);
    if (!((List<?>) errors.get(screen)).isEmpty())
      throw new IllegalStateException("Unexpected native loading error");
    var messages = new java.util.HashSet<String>();
    var report = new JsonArray();
    for (Object warning : (List<?>) warnings.get(screen)) {
      var text = warning.getClass().getDeclaredField("text");
      text.setAccessible(true);
      String message = ((Component) text.get(warning)).getString();
      messages.add(message);
      report.add(message);
    }
    Set<String> expected =
        Set.of(
            "Mod entity_model_features uses the deprecated `logoFile` property; change to `bannerFile` and/or (for square icons) `iconFile`",
            "Mod entity_texture_features uses the deprecated `logoFile` property; change to `bannerFile` and/or (for square icons) `iconFile`");
    if (!messages.equals(expected) || report.size() != 2)
      throw new IllegalStateException("Unexpected native loading warning: " + messages);
    var next = type.getDeclaredField("nextScreenTask");
    next.setAccessible(true);
    acknowledged = true;
    ((Runnable) next.get(screen)).run();
    return report;
  }
}
