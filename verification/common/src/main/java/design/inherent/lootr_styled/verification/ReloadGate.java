package design.inherent.lootr_styled.verification;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Holds one fixture request before native entry, then delegates to the actual native reload. */
public final class ReloadGate {
  private static boolean armed;
  private static CompletableFuture<Void> pending;
  private static Supplier<CompletableFuture<Void>> nativeReload;
  private static int requests, releases;

  private ReloadGate() {}

  static synchronized void arm() {
    if (armed || pending != null) throw new IllegalStateException("Reload gate already active");
    armed = true;
  }

  public static synchronized CompletableFuture<Void> enter(
      Supplier<CompletableFuture<Void>> reload) {
    if (!armed) return null;
    armed = false;
    nativeReload = java.util.Objects.requireNonNull(reload);
    requests++;
    pending = new CompletableFuture<>();
    return pending;
  }

  static void release() {
    final CompletableFuture<Void> target;
    final Supplier<CompletableFuture<Void>> reload;
    synchronized (ReloadGate.class) {
      if (pending == null) throw new IllegalStateException("No held reload");
      target = pending;
      pending = null;
      reload = nativeReload;
      nativeReload = null;
      releases++;
    }
    try {
      reload
          .get()
          .whenComplete(
              (unused, error) -> {
                if (error == null) target.complete(null);
                else target.completeExceptionally(error);
              });
    } catch (RuntimeException | Error error) {
      target.completeExceptionally(error);
    }
  }

  static synchronized void abort() {
    armed = false;
    nativeReload = null;
    if (pending == null) return;
    var target = pending;
    pending = null;
    target.completeExceptionally(new CancellationException("Fixture gate aborted"));
  }

  static synchronized boolean holding() {
    return pending != null;
  }

  static synchronized int requests() {
    return requests;
  }

  static synchronized int releases() {
    return releases;
  }
}
