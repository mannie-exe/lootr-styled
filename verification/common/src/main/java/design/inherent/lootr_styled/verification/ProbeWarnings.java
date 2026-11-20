package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Only the pinned optional dependencies' two known metadata warnings may be acknowledged. */
final class ProbeWarnings {
  private static boolean acknowledged;

  private ProbeWarnings() {}

  static JsonArray advance(Object screen) throws ReflectiveOperationException {
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
