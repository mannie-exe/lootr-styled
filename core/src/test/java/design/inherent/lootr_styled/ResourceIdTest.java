package design.inherent.lootr_styled;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResourceIdTest {
  @Test
  void preservesNamespacedIdentity() {
    var id = ResourceId.parse("author.styles:chests/copper_variant");
    assertEquals("author.styles", id.namespace());
    assertEquals("chests/copper_variant", id.path());
    assertEquals("author.styles:chests/copper_variant", id.toString());
  }

  @Test
  void refusesAmbiguousAndTraversingIdentities() {
    for (String value :
        new String[] {
          "default",
          ":default",
          "a:b:c",
          "a:",
          "A:b",
          "a:/b",
          "a:b/",
          "a:./b",
          "a:../b",
          "a:b//c",
          "a:b/../c",
          "a:b\\c",
          "a:b%2fc",
          ".:b",
          "..:b"
        }) {
      assertThrows(IllegalArgumentException.class, () -> ResourceId.parse(value), value);
    }
  }

  @Test
  void checksInclusiveLengthBoundary() {
    assertEquals(
        ResourceId.MAX_LENGTH, ResourceId.parse("a:" + "b".repeat(254)).toString().length());
    assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("a:" + "b".repeat(255)));
  }
}
