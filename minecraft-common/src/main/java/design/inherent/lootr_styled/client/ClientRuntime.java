package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.LocalFiles;
import design.inherent.lootr_styled.profile.ProfileDirectory;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.EmfSupport;
import design.inherent.lootr_styled.resources.FrozenResources;
import design.inherent.lootr_styled.resources.PresentationCompiler;
import design.inherent.lootr_styled.resources.ResourceAccess;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Resource-domain capture; settings persistence and editing belong to re:form. */
public final class ClientRuntime {
  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
  private static volatile ProfileDirectory profiles;
  private static volatile StyleSettingsStore settings;
  private static volatile Supplier<Optional<String>> emfVersion = Optional::empty;
  private static ExecutorService workers;

  public record Preparation(
      FrozenResources resources,
      Optional<String> settingsRevision,
      boolean qualified,
      List<String> status,
      List<PresentationCompiler.Choice> choices,
      Optional<PresentationCompiler.Selections> selections) {
    public Preparation(
        FrozenResources resources,
        Optional<String> settingsRevision,
        boolean qualified,
        List<String> status,
        List<PresentationCompiler.Choice> choices) {
      this(resources, settingsRevision, qualified, status, choices, Optional.empty());
    }

    public static final Preparation EMPTY =
        new Preparation(
            FrozenResources.EMPTY, Optional.empty(), false, List.of("Not available"), List.of());

    public Preparation {
      status = List.copyOf(status);
      choices = List.copyOf(choices);
      java.util.Objects.requireNonNull(selections);
    }
  }

  private ClientRuntime() {}

  public static void initialize(Path configDirectory) {
    initialize(configDirectory, Optional::empty);
  }

  public static synchronized void initialize(
      Path configDirectory, Supplier<Optional<String>> optionalEmfVersion) {
    if (workers != null) workers.shutdown();
    profiles = new ProfileDirectory(configDirectory);
    settings = new StyleSettingsStore(configDirectory);
    emfVersion = java.util.Objects.requireNonNull(optionalEmfVersion);
    workers =
        new ThreadPoolExecutor(
            2,
            2,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            task -> {
              var thread = new Thread(task, "Lootr Styled resources");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  public static List<String> status() {
    return NativeUi.activePack()
        .flatMap(GeneratedPack::preparation)
        .map(Preparation::status)
        .orElse(List.of("Resources are not available; reload to inspect them"));
  }

  public static List<PresentationCompiler.Choice> choices() {
    return NativeUi.activePack()
        .flatMap(GeneratedPack::preparation)
        .map(Preparation::choices)
        .orElse(List.of());
  }

  public static ContainerTargets targets() {
    return NativeUi.targets();
  }

  public static ProfileDirectory configuration() {
    return profiles;
  }

  public static StyleSettingsStore settingsStore() {
    return settings;
  }

  static EmfSupport emfSupport() {
    return EmfSupport.inspect(emfVersion.get());
  }

  static synchronized ExecutorService workers() {
    if (workers == null || workers.isShutdown())
      throw new IllegalStateException("Lootr: Styled resource lifetime is closed");
    return workers;
  }

  public static synchronized void close() {
    if (workers != null) workers.shutdown();
  }

  static CompletableFuture<Preparation> prepareAsync(
      ResourceAccess access, java.util.function.BooleanSupplier current) {
    Runnable checkpoint =
        () -> {
          if (!current.getAsBoolean())
            throw new java.util.concurrent.CancellationException("Retired resource generation");
        };
    var scoped = access.guarded(checkpoint);
    return CompletableFuture.supplyAsync(
        () -> {
          checkpoint.run();
          var result = prepareResult(scoped);
          checkpoint.run();
          return result;
        },
        workers());
  }

  static FrozenResources prepare(ResourceAccess access) {
    return prepareResult(access).resources();
  }

  private static Preparation prepareResult(ResourceAccess access) {
    var notes = new ArrayList<String>();
    LocalFiles local = null;
    Optional<String> revision = Optional.empty();
    try {
      var store = settings;
      var source = store.read();
      var candidate =
          source
              .candidate()
              .orElseThrow(() -> new IllegalArgumentException("Settings unavailable"));
      revision = Optional.of(source.revision());
      var selected = candidate.value();
      profiles.seed();
      try {
        local = LocalFiles.open(profiles.root());
      } catch (IOException unavailable) {
        notes.add("Local profiles unavailable: " + unavailable.getMessage());
      }
      var result =
          PresentationCompiler.compile(
              targets(),
              selected,
              access,
              local,
              NativeUi::validateModel,
              selected.enabled() ? EmfSupport.inspect(emfVersion.get()) : EmfSupport.ABSENT,
              NativeUi.ordinaryChestIsolated());
      if (local != null) local.validateCurrent();
      if (!source.revision().equals(store.read().revision()))
        throw new IOException("Settings changed during resource preparation");
      notes.addAll(result.status());
      LOGGER.info("Lootr: Styled loaded {} resources", result.resources().ids().size());
      LOGGER.debug("Lootr: Styled details: {}", notes);
      return new Preparation(
          result.resources(), revision, true, notes, result.choices(), result.selections());
    } catch (IOException | IllegalArgumentException refused) {
      var reason = "Native fallback: " + refused.getMessage();
      LOGGER.warn("Lootr: Styled uses native resources: {}", reason, refused);
      return new Preparation(FrozenResources.EMPTY, revision, false, List.of(reason), List.of());
    } finally {
      if (local != null) {
        try {
          local.close();
        } catch (IOException failed) {
          LOGGER.warn("Could not close local profile reader", failed);
        }
      }
    }
  }
}
