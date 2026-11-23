package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResourceAccessTest {
  @Test
  void retirementRefusesLookupAndPreviouslyCapturedStreamEntryWithoutTouchingTheSource()
      throws Exception {
    var current = new AtomicBoolean(true);
    var lookups = new AtomicInteger();
    var opens = new AtomicInteger();
    var id = ResourceId.parse("fixture:models/barrel.json");
    var nativeSource =
        new ResourceAccess.Source(
            ResourceAccess.Origin.LOOTR,
            "borrowed pack",
            () -> {
              opens.incrementAndGet();
              return new ByteArrayInputStream(new byte[] {1, 2, 3});
            });
    ResourceAccess access =
        new ResourceAccess() {
          public Optional<Source> find(ResourceId key) {
            lookups.incrementAndGet();
            return key.equals(id) ? Optional.of(nativeSource) : Optional.empty();
          }

          public Map<ResourceId, Source> list(String directory) {
            lookups.incrementAndGet();
            return Map.of(id, nativeSource);
          }
        };
    var scoped =
        access.guarded(
            () -> {
              if (!current.get()) throw new CancellationException("retired");
            });
    var found = scoped.find(id).orElseThrow();
    var listed = scoped.list("models").get(id);
    assertEquals(ResourceAccess.Origin.LOOTR, found.origin());
    assertEquals("borrowed pack", found.description());
    try (var stream = found.opener().open()) {
      assertArrayEquals(new byte[] {1, 2, 3}, stream.readAllBytes());
    }
    assertTrue(scoped.find(ResourceId.parse("fixture:missing")).isEmpty());
    current.set(false);
    assertThrows(CancellationException.class, () -> scoped.find(id));
    assertThrows(CancellationException.class, () -> scoped.list("models"));
    assertThrows(CancellationException.class, () -> found.opener().open());
    assertThrows(CancellationException.class, () -> listed.opener().open());
    assertEquals(3, lookups.get());
    assertEquals(1, opens.get());
    try (var stream = access.find(id).orElseThrow().opener().open()) {
      assertEquals(1, stream.read());
    }
  }
}
