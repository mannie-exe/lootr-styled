package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class StyleGraphicSubjectTest {
  @Test
  void viewParametersRoundTripIndependentlyOfTheCompleteSettingsDocument() {
    for (var kind : StyleGraphicSubject.Container.values())
      for (boolean looted : java.util.List.of(false, true))
        for (boolean open : java.util.List.of(false, true))
          for (float lid : java.util.List.of(0f, 0.5f, 1f)) {
            var subject = new StyleGraphicSubject(kind, looted, open, lid);
            var encoded =
                StyleGraphicSubject.TYPE
                    .codec()
                    .encodeStart(JsonOps.INSTANCE, subject)
                    .getOrThrow();
            assertEquals(
                java.util.Set.of("container", "looted", "open", "lid"),
                encoded.getAsJsonObject().keySet());
            assertEquals(
                subject,
                StyleGraphicSubject.TYPE.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow());
          }
  }

  @Test
  void unrelatedSettingsFieldsAndInvalidSubjectValuesAreNotAdmitted() {
    for (String data :
        java.util.List.of(
            "{\"container\":\"CHEST\",\"looted\":false,\"open\":false,\"lid\":2}",
            "{\"container\":\"OTHER\",\"looted\":false,\"open\":false,\"lid\":0}",
            "{\"container\":\"CHEST\",\"looted\":false,\"open\":false,\"lid\":0,\"models\":\"example:foreign\"}"))
      assertTrue(
          StyleGraphicSubject.TYPE
              .codec()
              .parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(data))
              .error()
              .isPresent(),
          data);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StyleGraphicSubject(StyleGraphicSubject.Container.CHEST, false, false, Float.NaN));
  }
}
