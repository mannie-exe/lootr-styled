package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrozenResourcesTest {
  private static final ResourceId A = ResourceId.parse("lootr:models/a.json");
  private static final ResourceId B = ResourceId.parse("lootr:models/b.json");

  @Test
  void ownsInputBytesAndIndependentReadStreams() throws Exception {
    var bytes = "original".getBytes(StandardCharsets.UTF_8);
    var input = new HashMap<ResourceId, byte[]>();
    input.put(A, bytes);
    var frozen = new FrozenResources(input, FrozenResources.DEFAULT_LIMITS);
    bytes[0] = 'x';
    input.clear();
    try (var first = frozen.open(A).orElseThrow();
        var second = frozen.open(A).orElseThrow()) {
      assertEquals('o', first.read());
      assertEquals("original", new String(second.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals(8, frozen.byteSize());
  }

  @Test
  void publishesStableUnmodifiableIdentities() {
    var frozen =
        new FrozenResources(Map.of(B, new byte[1], A, new byte[1]), FrozenResources.DEFAULT_LIMITS);
    assertEquals(List.of(A, B), frozen.ids().stream().toList());
    assertThrows(UnsupportedOperationException.class, () -> frozen.ids().clear());
    assertTrue(frozen.contains(A));
    assertTrue(frozen.open(ResourceId.parse("lootr:missing.json")).isEmpty());
  }

  @Test
  void enforcesWholeGenerationBudgetsAtInclusiveBoundary() {
    var input = Map.of(A, new byte[2], B, new byte[3]);
    assertEquals(5, new FrozenResources(input, new FrozenResources.Limits(2, 5)).byteSize());
    assertThrows(
        IllegalArgumentException.class,
        () -> new FrozenResources(input, new FrozenResources.Limits(2, 4)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FrozenResources(input, new FrozenResources.Limits(1, 5)));
    assertTrue(new FrozenResources(Map.of(), new FrozenResources.Limits(0, 0)).ids().isEmpty());
  }

  @Test
  void rejectsNegativeLimitsAndDoesNotOverflowWideBudgets() {
    assertThrows(IllegalArgumentException.class, () -> new FrozenResources.Limits(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> new FrozenResources.Limits(1, -1));
    assertEquals(
        3,
        new FrozenResources(Map.of(A, new byte[3]), new FrozenResources.Limits(1, Long.MAX_VALUE))
            .byteSize());
  }
}
