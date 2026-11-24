package design.inherent.lootr_styled.client;

import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.owner.PolicyRefusal;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Asynchronous native application, separate from the file owner's confirmed persistence. */
public final class StyleApplication {
  public enum State {
    APPLIED,
    REFUSED,
    FAILED,
    CANCELLED,
    UNKNOWN
  }

  public enum Code {
    APPLIED,
    NOT_RELOADED,
    BUSY,
    SOURCE_UNAVAILABLE,
    SOURCE_CHANGED,
    GENERATION_MISMATCH,
    NATIVE_FAILURE,
    CANCELLED,
    CONTEXT_CLOSED
  }

  public record Result(
      State state,
      Code code,
      String message,
      Optional<String> sourceRevision,
      Optional<UUID> generation,
      List<String> details) {
    public Result {
      details = List.copyOf(details);
    }
  }

  record Source(String revision, boolean valid) {}

  record Publication(
      Optional<UUID> generation,
      Optional<String> revision,
      boolean qualified,
      List<String> details) {
    Publication {
      details = List.copyOf(details);
    }
  }

  @FunctionalInterface
  interface SourceReader {
    Source read() throws IOException;
  }

  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
  private final OwnerContext context;
  private final SourceReader source;
  private final Supplier<CompletionStage<Void>> reload;
  private final Supplier<Publication> publication;
  private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
  private volatile Result last =
      new Result(
          State.UNKNOWN,
          Code.NOT_RELOADED,
          "Not reloaded",
          Optional.empty(),
          Optional.empty(),
          List.of());

  StyleApplication(OwnerContext context) {
    this(
        context,
        () -> {
          var read = ClientRuntime.settingsStore().read();
          return new Source(read.revision(), read.candidate().isPresent());
        },
        NativeUi::reloadResources,
        () -> {
          var pack = NativeUi.activePack();
          var prepared = pack.flatMap(GeneratedPack::preparation);
          return new Publication(
              pack.map(GeneratedPack::id),
              prepared.flatMap(ClientRuntime.Preparation::settingsRevision),
              prepared.map(ClientRuntime.Preparation::qualified).orElse(false),
              ClientRuntime.status());
        });
  }

  StyleApplication(
      OwnerContext context,
      SourceReader source,
      Supplier<CompletionStage<Void>> reload,
      Supplier<Publication> publication) {
    this.context = context;
    this.source = source;
    this.reload = reload;
    this.publication = publication;
  }

  public Result last() {
    return last;
  }

  boolean reloading() {
    context.requireOwner();
    return !tail.isDone();
  }

  /** One admitted reload at a time; rejected requests cannot clear the active attempt. */
  CompletionStage<Result> reload(Optional<String> expected, Runnable checkpoint) {
    context.requireOwner();
    if (!tail.isDone())
      return CompletableFuture.completedStage(
          problem(State.REFUSED, Code.BUSY, "A resource reload is already running", expected));
    var settled = new CompletableFuture<Void>();
    tail = settled;
    var revision = new java.util.concurrent.atomic.AtomicReference<>(expected);
    var started = new java.util.concurrent.atomic.AtomicBoolean();
    var confirmed = new java.util.concurrent.atomic.AtomicReference<Result>();
    var result =
        context
            .onWorker(
                () -> {
                  checkpoint.run();
                  Source selected;
                  try {
                    selected = source.read();
                  } catch (IOException unavailable) {
                    throw new Refusal(
                        Code.SOURCE_UNAVAILABLE,
                        "Couldn't read settings.json; check the file and its permissions before reloading",
                        unavailable);
                  }
                  if (!selected.valid())
                    throw new Refusal(
                        Code.SOURCE_UNAVAILABLE, "Check settings.json before reloading resources");
                  if (expected.isPresent() && !expected.orElseThrow().equals(selected.revision()))
                    throw new Refusal(
                        Code.SOURCE_CHANGED, "Settings changed; review them before reloading");
                  revision.set(Optional.of(selected.revision()));
                  return selected.revision();
                })
            .thenCompose(
                captured ->
                    context
                        .onOwner(
                            () -> {
                              checkpoint.run();
                              var previous = publication.get().generation();
                              started.set(true);
                              return reload
                                  .get()
                                  .thenCompose(
                                      ignored ->
                                          context.onOwner(
                                              () -> {
                                                checkpoint.run();
                                                var applied = publication.get();
                                                if (!applied.qualified()
                                                    || !applied
                                                        .revision()
                                                        .equals(Optional.of(captured))
                                                    || applied.generation().isEmpty()
                                                    || applied.generation().equals(previous))
                                                  return problem(
                                                      State.FAILED,
                                                      Code.GENERATION_MISMATCH,
                                                      "Resources did not publish the saved settings; review resource details",
                                                      Optional.of(captured));
                                                var observation =
                                                    new Result(
                                                        State.APPLIED,
                                                        Code.APPLIED,
                                                        "Reloaded",
                                                        Optional.of(captured),
                                                        applied.generation(),
                                                        applied.details());
                                                confirmed.set(observation);
                                                return observation;
                                              }));
                            })
                        .thenCompose(stage -> stage)
                        .thenCompose(
                            observed -> {
                              if (observed.state() != State.APPLIED)
                                return CompletableFuture.completedStage(observed);
                              return context
                                  .onWorker(
                                      () -> {
                                        try {
                                          return source.read();
                                        } catch (IOException unavailable) {
                                          throw new Refusal(
                                              Code.SOURCE_UNAVAILABLE,
                                              "Resources reloaded, but settings.json couldn't be checked; inspect the file before retrying",
                                              unavailable);
                                        }
                                      })
                                  .thenCompose(
                                      current ->
                                          context.onOwner(
                                              () -> {
                                                checkpoint.run();
                                                if (!current.valid()
                                                    || !current.revision().equals(captured))
                                                  return observationProblem(
                                                      State.REFUSED,
                                                      Code.SOURCE_CHANGED,
                                                      "Settings changed during reload; review the current file",
                                                      revision.get(),
                                                      confirmed.get());
                                                if (!publication
                                                    .get()
                                                    .generation()
                                                    .equals(observed.generation()))
                                                  return observationProblem(
                                                      State.UNKNOWN,
                                                      Code.GENERATION_MISMATCH,
                                                      "Another reload replaced this result; inspect resources before retrying",
                                                      revision.get(),
                                                      confirmed.get());
                                                return observed;
                                              }));
                            }))
            .handle(
                (value, failed) -> {
                  if (failed == null) return value;
                  Throwable cause = failed;
                  while (cause instanceof java.util.concurrent.CompletionException
                      && cause.getCause() != null) cause = cause.getCause();
                  if (cause instanceof Refusal refused) {
                    if (refused.getCause() != null)
                      LOGGER.warn(
                          "Lootr: Styled settings source could not be checked", refused.getCause());
                    return observationProblem(
                        started.get() ? State.UNKNOWN : State.REFUSED,
                        refused.code,
                        refused.getMessage(),
                        revision.get(),
                        confirmed.get());
                  }
                  if (cause instanceof PolicyRefusal refused
                      && refused.report().issues().stream()
                          .anyMatch(issue -> issue.code().equals("operation.cancelled")))
                    return observationProblem(
                        started.get() ? State.UNKNOWN : State.CANCELLED,
                        Code.CANCELLED,
                        started.get()
                            ? "Cancellation requested after reload started; inspect resources before retrying"
                            : "Reload cancelled; saved settings remain",
                        revision.get(),
                        confirmed.get());
                  if (!context.isOpen())
                    return observationProblem(
                        State.UNKNOWN,
                        Code.CONTEXT_CLOSED,
                        "The client closed before resource application was confirmed",
                        revision.get(),
                        confirmed.get());
                  LOGGER.warn("Lootr: Styled resource application failed", cause);
                  return problem(
                      State.FAILED,
                      Code.NATIVE_FAILURE,
                      "Couldn't reload resources. Saved settings remain; inspect the client log before retrying.",
                      revision.get());
                });
    var response = new CompletableFuture<Result>();
    result.whenComplete(
        (value, failed) -> {
          if (value != null) last = value;
          settled.complete(null);
          if (failed == null) response.complete(value);
          else response.completeExceptionally(failed);
        });
    return response.minimalCompletionStage();
  }

  private static Result problem(State state, Code code, String message, Optional<String> revision) {
    return new Result(state, code, message, revision, Optional.empty(), List.of());
  }

  private static Result observationProblem(
      State state, Code code, String message, Optional<String> revision, Result observed) {
    return new Result(
        state,
        code,
        message,
        revision,
        observed == null ? Optional.empty() : observed.generation(),
        observed == null ? List.of() : observed.details());
  }

  private static final class Refusal extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final Code code;

    Refusal(Code code, String message) {
      super(message);
      this.code = code;
    }

    Refusal(Code code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }
  }
}
