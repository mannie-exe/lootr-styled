package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResourceGenerationTest {
  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for fixture gate");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Fixture interrupted", ex);
    }
  }

  @Test
  void preparesOnceAndUsesGenerationIdentityRatherThanEqualBytes() {
    var count = new AtomicInteger();
    var first =
        new ResourceGeneration(
            () -> {
              count.incrementAndGet();
              return FrozenResources.EMPTY;
            });
    var a = first.snapshot().orElseThrow();
    assertSame(a, first.snapshot().orElseThrow());
    var other = new ResourceGeneration(() -> FrozenResources.EMPTY);
    var b = other.snapshot().orElseThrow();
    assertSame(a.resources(), b.resources());
    assertFalse(first.isCurrent(b));
    assertTrue(first.isCurrent(a));
    assertEquals(1, count.get());
  }

  @Test
  void closingRevokesCurrentnessAndCannotStartUnusedWork() {
    var count = new AtomicInteger();
    var generation =
        new ResourceGeneration(
            () -> {
              count.incrementAndGet();
              return FrozenResources.EMPTY;
            });
    generation.close();
    assertTrue(generation.snapshot().isEmpty());
    assertEquals(0, count.get());
    var ready = new ResourceGeneration(() -> FrozenResources.EMPTY);
    var snapshot = ready.snapshot().orElseThrow();
    ready.close();
    ready.close();
    assertFalse(ready.isCurrent(snapshot));
    assertTrue(ready.snapshot().isEmpty());
    assertEquals(ResourceGeneration.State.CLOSED, ready.state());
  }

  @Test
  void concurrentReadersShareOneActualPreparation() throws Exception {
    var calls = new AtomicInteger();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var allReaders = new CountDownLatch(4);
    var generation =
        new ResourceGeneration(
            () -> {
              calls.incrementAndGet();
              entered.countDown();
              await(release);
              return FrozenResources.EMPTY;
            });
    try (var workers = Executors.newFixedThreadPool(4)) {
      var results =
          new ArrayList<java.util.concurrent.Future<Optional<ResourceGeneration.Snapshot>>>();
      try {
        for (int i = 0; i < 4; i++) {
          results.add(
              workers.submit(
                  () -> {
                    allReaders.countDown();
                    await(allReaders);
                    return generation.snapshot();
                  }));
        }
        await(entered);
        assertEquals(ResourceGeneration.State.PREPARING, generation.state());
      } finally {
        release.countDown();
      }
      var first = results.getFirst().get(5, TimeUnit.SECONDS).orElseThrow();
      for (var result : results) {
        assertSame(first, result.get(5, TimeUnit.SECONDS).orElseThrow());
      }
      assertEquals(1, calls.get());
    }
  }

  @Test
  void closedInFlightWorkCannotPublishAndWaitersAreReleased() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var generation =
        new ResourceGeneration(
            () -> {
              entered.countDown();
              await(release);
              return FrozenResources.EMPTY;
            });
    try (var workers = Executors.newFixedThreadPool(2)) {
      var preparing = workers.submit(generation::snapshot);
      try {
        await(entered);
        var waiting = workers.submit(generation::snapshot);
        generation.close();
        assertTrue(waiting.get(5, TimeUnit.SECONDS).isEmpty());
      } finally {
        release.countDown();
      }
      assertTrue(preparing.get(5, TimeUnit.SECONDS).isEmpty());
      assertEquals(ResourceGeneration.State.CLOSED, generation.state());
    }
  }

  @Test
  void failureIsBoundedAndDoesNotRetryInsideOneReload() {
    var calls = new AtomicInteger();
    var generation =
        new ResourceGeneration(
            () -> {
              calls.incrementAndGet();
              throw new IllegalArgumentException("bad\n".repeat(10000));
            });
    assertTrue(generation.snapshot().isEmpty());
    assertTrue(generation.snapshot().isEmpty());
    assertEquals(1, calls.get());
    assertEquals(ResourceGeneration.State.FAILED, generation.state());
    String reason = generation.failure().orElseThrow();
    assertTrue(reason.length() <= 240);
    assertFalse(reason.contains("\n"));
  }

  @Test
  void recursionFailsInsteadOfJoiningItself() {
    var reference = new AtomicReference<ResourceGeneration>();
    var generation =
        new ResourceGeneration(() -> reference.get().snapshot().orElseThrow().resources());
    reference.set(generation);
    assertTrue(generation.snapshot().isEmpty());
    assertEquals(ResourceGeneration.State.FAILED, generation.state());
    assertTrue(generation.failure().orElseThrow().contains("Recursive"));
  }

  @Test
  void fatalErrorsPropagateWithoutLeavingAPendingFuture() {
    var generation =
        new ResourceGeneration(
            () -> {
              throw new AssertionError("fixture");
            });
    assertThrows(AssertionError.class, generation::snapshot);
    assertEquals(ResourceGeneration.State.FAILED, generation.state());
    assertThrows(CompletionException.class, generation::snapshot);
  }
}
