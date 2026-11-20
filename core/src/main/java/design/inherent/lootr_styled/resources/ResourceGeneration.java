package design.inherent.lootr_styled.resources;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Preparation belongs to one native reload. Consumers must check currentness before use. */
public final class ResourceGeneration implements AutoCloseable {
  private final CompletableFuture<Optional<Snapshot>> ready = new CompletableFuture<>();
  private Supplier<FrozenResources> prepare;
  private State state = State.NEW;
  private Thread preparer;
  private Snapshot current;
  private String failure;

  public ResourceGeneration(Supplier<FrozenResources> prepare) {
    this.prepare = Objects.requireNonNull(prepare, "prepare");
  }

  public Optional<Snapshot> snapshot() {
    Supplier<FrozenResources> work = null;
    synchronized (this) {
      if (state == State.CLOSED) {
        return Optional.empty();
      }
      if (state == State.NEW) {
        state = State.PREPARING;
        preparer = Thread.currentThread();
        work = prepare;
        prepare = null;
      } else if (state == State.PREPARING && preparer == Thread.currentThread()) {
        throw new IllegalStateException("Recursive generated-resource preparation");
      }
    }
    if (work != null) {
      FrozenResources result = null;
      String error = null;
      try {
        result = Objects.requireNonNull(work.get(), "prepared resources");
      } catch (RuntimeException ex) {
        error = boundedFailure(ex);
      } catch (Error fatal) {
        synchronized (this) {
          preparer = null;
          if (state != State.CLOSED) {
            state = State.FAILED;
            failure = fatal.getClass().getSimpleName();
          }
          ready.completeExceptionally(
              new IllegalStateException("Fatal resource preparation error"));
        }
        throw fatal;
      }
      synchronized (this) {
        preparer = null;
        if (state != State.CLOSED) {
          if (error == null) {
            current = new Snapshot(result);
            state = State.READY;
            ready.complete(Optional.of(current));
          } else {
            failure = error;
            state = State.FAILED;
            ready.complete(Optional.empty());
          }
        }
      }
    }
    return ready.join();
  }

  public synchronized boolean isCurrent(Snapshot snapshot) {
    return state == State.READY && snapshot == current;
  }

  public synchronized State state() {
    return state;
  }

  public synchronized Optional<String> failure() {
    return Optional.ofNullable(failure);
  }

  @Override
  public synchronized void close() {
    state = State.CLOSED;
    prepare = null;
    current = null;
    failure = null;
    // Drop the completed future's retained byte generation as well as the current pointer.
    ready.obtrudeValue(Optional.empty());
  }

  private static String boundedFailure(RuntimeException error) {
    String message = String.valueOf(error.getMessage());
    var result = new StringBuilder(error.getClass().getSimpleName()).append(": ");
    for (int i = 0; i < Math.min(message.length(), 512) && result.length() < 240; i++) {
      char c = message.charAt(i);
      if (!Character.isISOControl(c)) {
        result.append(c);
      }
    }
    return result.toString();
  }

  public enum State {
    NEW,
    PREPARING,
    READY,
    FAILED,
    CLOSED
  }

  public static final class Snapshot {
    private final FrozenResources resources;

    private Snapshot(FrozenResources resources) {
      this.resources = resources;
    }

    public FrozenResources resources() {
      return resources;
    }
  }
}
