package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.reform.reference.ReferenceIndex;
import design.inherent.reform.reference.ReferencePage;
import design.inherent.reform.reference.ReferenceQuery;
import design.inherent.reform.reference.ReferenceResolution;
import design.inherent.reform.reference.ReferenceStamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StyleProfileReferencesTest {
  @Test
  void declaredFactsAreCachedMetadataAndUnknownCountsAreNotReportedAsZero() {
    var id = design.inherent.lootr_styled.ResourceId.parse("example:profile");
    var unknown =
        StylePreferences.profileEntry(
            new design.inherent.lootr_styled.resources.PresentationCompiler.Choice(
                id, "Profile", "Pack"));
    assertEquals(Map.of("ID", id.toString(), "Source", "Pack"), unknown.preview());
    var known =
        StylePreferences.profileEntry(
            new design.inherent.lootr_styled.resources.PresentationCompiler.Choice(
                id,
                "Profile",
                "Pack",
                java.util.Optional.of(
                    new design.inherent.lootr_styled.resources.PresentationCompiler
                        .ContributionCounts(4, 0))));
    assertEquals("4", known.preview().get("Declared model entries"));
    assertEquals("0", known.preview().get("Declared artwork entries"));
    assertEquals(unknown.key(), known.key());
    assertEquals(unknown.value(), known.value());
    assertEquals(unknown.label(), known.label());
    assertEquals(unknown.searchText(), known.searchText());
  }

  @Test
  void unavailableIsNotAnEmptyCatalogueOrAnUnknownProfile() {
    var stamp =
        new ReferenceStamp(
            UUID.randomUUID(), StyleSettingsStore.PROFILES, UUID.randomUUID(), "fixture");
    var unavailable = StylePreferences.profileSource(stamp, false, List.of());
    var empty = StylePreferences.profileSource(stamp, true, List.of());
    var query = new ReferenceQuery("", 8);
    assertEquals(
        ReferencePage.Availability.UNAVAILABLE_CONTEXT, unavailable.search(query).availability());
    assertFalse(unavailable.search(query).exhausted());
    assertEquals(
        ReferenceResolution.Status.UNAVAILABLE_CONTEXT,
        unavailable.resolve("example:missing").status());
    assertEquals(ReferencePage.Availability.READY, empty.search(query).availability());
    assertTrue(empty.search(query).exhausted());
    assertEquals(
        ReferenceResolution.Status.UNKNOWN_REFERENCE, empty.resolve("example:missing").status());
    var entry =
        new ReferenceIndex.Entry<>(
            "example:present",
            "example:present",
            "Named profile",
            "Named profile",
            Map.<String, String>of());
    var ready = StylePreferences.profileSource(stamp, true, List.of(entry));
    assertEquals("Named profile", ready.search(query).choices().getFirst().label());
    assertEquals(ReferenceResolution.Status.RESOLVED, ready.resolve("example:present").status());
  }
}
