package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.owner.PolicyRefusal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StyleApplicationTest {
  @TempDir Path config;

  private static final class Fixture implements AutoCloseable {
    final OwnerContext context =
        new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD);
    final AtomicReference<StyleApplication.Source> source =
        new AtomicReference<>(new StyleApplication.Source("r1", true));
    final AtomicReference<IOException> sourceFailure = new AtomicReference<>();
    final AtomicReference<StyleApplication.Publication> publication =
        new AtomicReference<>(
            new StyleApplication.Publication(
                Optional.of(new UUID(0, 1)), Optional.of("old"), true, List.of()));
    final List<CompletableFuture<Void>> nativeWork = new ArrayList<>();
    final StyleApplication application =
        new StyleApplication(
            context,
            () -> {
              if (sourceFailure.get() != null) throw sourceFailure.get();
              return source.get();
            },
            () -> {
              var pending = new CompletableFuture<Void>();
              nativeWork.add(pending);
              return pending;
            },
            publication::get);

    void finish(int attempt, String revision) {
      publication.set(
          new StyleApplication.Publication(
              Optional.of(UUID.randomUUID()),
              Optional.of(revision),
              true,
              List.of("Qualified resources")));
      nativeWork.get(attempt).complete(null);
    }

    @Override
    public void close() {
      context.close();
    }
  }

  @Test
  void completionReleasesAdmissionAndPublishesResultBeforeNotifyingObservers() {
    try (var f = new Fixture()) {
      var first = f.application.reload(Optional.of("r1"), () -> {});
      var observedLast = new AtomicReference<StyleApplication.Result>();
      first.thenAccept(
          result -> {
            observedLast.set(f.application.last());
            f.application.reload(Optional.of("r1"), () -> {});
          });
      f.finish(0, "r1");
      assertEquals(StyleApplication.State.APPLIED, observedLast.get().state());
      assertEquals(
          2, f.nativeWork.size(), "A completion observer can start the next explicit reload");
      f.finish(1, "r1");
    }
  }

  @Test
  void aBusyRefusalDoesNotReleaseTheActiveNativeWork() {
    try (var f = new Fixture()) {
      var first = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
      for (int i = 0; i < 2; i++)
        assertEquals(
            StyleApplication.Code.BUSY,
            f.application.reload(Optional.empty(), () -> {}).toCompletableFuture().join().code());
      assertFalse(first.isDone());
      assertEquals(1, f.nativeWork.size());
      f.finish(0, "r1");
      assertEquals(StyleApplication.State.APPLIED, first.join().state());
    }
  }

  @Test
  void changedInvalidOrCancelledSourceRefusesBeforeNativeEntry() {
    try (var f = new Fixture()) {
      assertEquals(
          StyleApplication.Code.SOURCE_CHANGED,
          f.application.reload(Optional.of("old"), () -> {}).toCompletableFuture().join().code());
      f.source.set(new StyleApplication.Source("r1", false));
      assertEquals(
          StyleApplication.Code.SOURCE_UNAVAILABLE,
          f.application.reload(Optional.empty(), () -> {}).toCompletableFuture().join().code());
      assertEquals(
          StyleApplication.Code.CANCELLED,
          f.application
              .reload(
                  Optional.empty(),
                  () -> {
                    throw new PolicyRefusal("operation.cancelled", "Cancelled before native entry");
                  })
              .toCompletableFuture()
              .join()
              .code());
      assertTrue(f.nativeWork.isEmpty());
    }
  }

  @Test
  void nativeCompletionRequiresANewMatchingPublishedGenerationAndCurrentSource() {
    try (var f = new Fixture()) {
      var wrong = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
      f.finish(0, "wrong");
      assertEquals(StyleApplication.Code.GENERATION_MISMATCH, wrong.join().code());
      var unchanged = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
      f.nativeWork.get(1).complete(null);
      assertEquals(StyleApplication.Code.GENERATION_MISMATCH, unchanged.join().code());
      var stale = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
      f.source.set(new StyleApplication.Source("new", true));
      f.finish(2, "r1");
      assertEquals(StyleApplication.Code.SOURCE_CHANGED, stale.join().code());
    }
  }

  @Test
  void aNativeFailureNeverReportsApplied() {
    try (var f = new Fixture()) {
      var result = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
      f.nativeWork
          .getFirst()
          .completeExceptionally(new IOException("Controlled native reload failure"));
      assertEquals(StyleApplication.State.FAILED, result.join().state());
      assertEquals(StyleApplication.Code.NATIVE_FAILURE, result.join().code());
      assertEquals(Optional.of("r1"), result.join().sourceRevision());
    }
  }

  @Test
  void anUnreadableSourceRefusesBeforeReloadAndKeepsCompletedReloadUncertaintyAfterward() {
    try (var f = new Fixture()) {
      f.sourceFailure.set(new IOException("Controlled unreadable source"));
      var before = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture().join();
      assertEquals(StyleApplication.State.REFUSED, before.state());
      assertEquals(StyleApplication.Code.SOURCE_UNAVAILABLE, before.code());
      assertTrue(f.nativeWork.isEmpty());
      f.sourceFailure.set(null);
      var pending = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
      f.sourceFailure.set(new IOException("Controlled readback failure"));
      f.finish(0, "r1");
      var after = pending.join();
      assertEquals(StyleApplication.State.UNKNOWN, after.state());
      assertEquals(StyleApplication.Code.SOURCE_UNAVAILABLE, after.code());
      assertEquals(f.publication.get().generation(), after.generation());
      assertEquals(Optional.of("r1"), after.sourceRevision());
      assertEquals(List.of("Qualified resources"), after.details());
    }
  }

  @Test
  void cancellationAfterNativeEntryCannotClaimThatTheReloadWasPrevented() {
    try (var f = new Fixture()) {
      var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
      var pending =
          f.application
              .reload(
                  Optional.empty(),
                  () -> {
                    if (cancelled.get())
                      throw new PolicyRefusal("operation.cancelled", "Cancelled");
                  })
              .toCompletableFuture();
      cancelled.set(true);
      f.finish(0, "r1");
      var result = pending.join();
      assertEquals(StyleApplication.State.UNKNOWN, result.state());
      assertEquals(StyleApplication.Code.CANCELLED, result.code());
      assertEquals(Optional.of("r1"), result.sourceRevision());
      assertEquals(1, f.nativeWork.size());
    }
  }

  @Test
  void closureDuringNativeWorkDoesNotPublishApplied() {
    var f = new Fixture();
    var pending = f.application.reload(Optional.of("r1"), () -> {}).toCompletableFuture();
    f.close();
    f.finish(0, "r1");
    assertEquals(StyleApplication.State.UNKNOWN, pending.join().state());
    assertEquals(StyleApplication.Code.CONTEXT_CLOSED, pending.join().code());
  }

  @Test
  void aConfirmedSaveStaysSavedAfterNativeFailureAndRetryDoesNotWrite() throws Exception {
    var store = new StyleSettingsStore(config);
    try (var f = new Fixture()) {
      var owner =
          FileOwner.stagedFile(
              f.context,
              store,
              16,
              FileOwner.Initialization.CREATE_DEFAULTS,
              ApplicationEffects.root(
                  ApplicationEffects.Timing.EXPLICIT_RELOAD, "Reload resources"));
      try (var session = owner.open(f.context.access(() -> true, () -> true))) {
        session.initialize().toCompletableFuture().join();
        var observed = session.read().toCompletableFuture().join();
        var changed =
            new ClientSettings(
                false, ClientSettings.VANILLA, ClientSettings.DEFAULT, ClientSettings.Fresh.OFF);
        var draft =
            DraftTree.from(StyleSettingsStore.DEFINITION.type(), changed, store.draftLimits());
        var prepared = session.prepare(observed, draft).toCompletableFuture().join();
        var receipt = session.commit(prepared).toCompletableFuture().join();
        assertEquals(CommitReceipt.Persistence.SAVED, receipt.persistence());
        assertEquals(CommitReceipt.Application.STAGED, receipt.application());
        var file = config.resolve("lootr_styled/settings.json");
        byte[] saved = Files.readAllBytes(file);
        String revision = receipt.savedRevision().orElseThrow();
        f.source.set(new StyleApplication.Source(revision, true));
        var failed = f.application.reload(Optional.of(revision), () -> {}).toCompletableFuture();
        f.nativeWork
            .get(0)
            .completeExceptionally(new IOException("Controlled reload failure after save"));
        assertEquals(StyleApplication.State.FAILED, failed.join().state());
        assertEquals(CommitReceipt.Persistence.SAVED, receipt.persistence());
        assertArrayEquals(saved, Files.readAllBytes(file));
        var retry = f.application.reload(Optional.of(revision), () -> {}).toCompletableFuture();
        f.finish(1, revision);
        assertEquals(StyleApplication.State.APPLIED, retry.join().state());
        assertArrayEquals(saved, Files.readAllBytes(file));
      }
    }
  }
}
