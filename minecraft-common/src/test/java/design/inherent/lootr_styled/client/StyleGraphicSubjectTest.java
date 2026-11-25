package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class StyleGraphicSubjectTest {
  @Test
  void openAndLootedControlsKeepTheirMeaningWhenChangingContainer() {
    var subject = new StyleGraphicSubject(StyleGraphicSubject.Container.BARREL, false, false, 0);
    var seen = new java.util.HashSet<String>();
    for (var container : StyleGraphicSubject.Container.values()) {
      subject = subject.withContainer(container);
      for (boolean looted : java.util.List.of(false, true)) {
        subject = subject.withLooted(looted);
        for (boolean opened : java.util.List.of(false, true)) {
          subject = subject.withOpened(opened);
          assertEquals(container, subject.container());
          assertEquals(looted, subject.looted());
          assertEquals(opened, subject.opened());
          if (container == StyleGraphicSubject.Container.BARREL)
            assertEquals(opened, subject.open());
          else assertEquals(opened ? 1f : 0f, subject.lid());
          seen.add(container + "/" + looted + "/" + opened);
        }
      }
      var next =
          container == StyleGraphicSubject.Container.BARREL
              ? StyleGraphicSubject.Container.CHEST
              : StyleGraphicSubject.Container.BARREL;
      assertTrue(subject.withContainer(next).opened());
      assertTrue(subject.withContainer(next).looted());
    }
    assertEquals(12, seen.size());
  }

  @Test
  void unrelatedControlsRetainPartialNativeChestPosesAndInactiveFields() {
    var original = new StyleGraphicSubject(StyleGraphicSubject.Container.CHEST, false, true, 0.5f);
    assertEquals(
        new StyleGraphicSubject(StyleGraphicSubject.Container.CHEST, true, true, 0.5f),
        original.withLooted(true));
    assertEquals(
        new StyleGraphicSubject(StyleGraphicSubject.Container.TRAPPED_CHEST, false, true, 0.5f),
        original.withContainer(StyleGraphicSubject.Container.TRAPPED_CHEST));
    assertSame(original, original.withContainer(original.container()));
    assertTrue(original.opened());
    assertEquals(1f, original.withOpened(true).lid());
    assertEquals(0f, original.withOpened(false).lid());
    assertTrue(original.withOpened(false).open());
    var barrel = original.withContainer(StyleGraphicSubject.Container.BARREL);
    assertTrue(barrel.open());
    assertEquals(0.5f, barrel.withOpened(false).lid());
    assertFalse(barrel.withOpened(false).withContainer(original.container()).opened());
  }

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
