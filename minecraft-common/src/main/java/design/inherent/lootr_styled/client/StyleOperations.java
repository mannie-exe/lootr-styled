package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ProfileExporter;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.OperationScreen;
import design.inherent.reform.operation.OperationDescriptor;
import design.inherent.reform.operation.OperationHandle;
import design.inherent.reform.operation.OperationScope;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.value.ValueTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;

final class StyleOperations {
  enum Action {
    RELOAD("Reload resources"),
    RETRY("Retry reload"),
    EXPORT_ARTWORK("Export artwork"),
    EXPORT_MODEL("Export barrel model"),
    FOLDER("Folder"),
    DETAILS("Resource details");
    final String title;

    Action(String title) {
      this.title = title;
    }
  }

  enum Signal {
    RUN
  }

  record Outcome(
      String summary,
      Optional<CommitReceipt<?>> persistence,
      Optional<StyleApplication.Result> application,
      List<String> details) {
    Outcome {
      details = List.copyOf(details);
    }

    static Outcome of(StyleApplication.Result result) {
      return new Outcome(result.message(), Optional.empty(), Optional.of(result), result.details());
    }
  }

  private final OwnerContext context;
  private final OperationScope scope;
  private final StyleApplication application;
  private final Map<Action, OperationDescriptor<Signal, Outcome>> descriptors =
      new java.util.EnumMap<>(Action.class);

  StyleOperations(OwnerContext context) {
    this.context = context;
    application = new StyleApplication(context);
    scope =
        new OperationScope(
            context, context.access(() -> true, () -> true), OperationScope.Limits.STANDARD);
    for (Action action : Action.values()) {
      var effect =
          switch (action) {
            case RELOAD, RETRY -> OperationDescriptor.Effect.APPLY;
            case EXPORT_ARTWORK, EXPORT_MODEL -> OperationDescriptor.Effect.EXPORT;
            default -> OperationDescriptor.Effect.INSPECT;
          };
      var descriptor =
          new OperationDescriptor<Signal, Outcome>(
              DocumentId.parse("lootr_styled:" + action.name().toLowerCase(java.util.Locale.ROOT)),
              ValueTypes.enumeration(Signal.class),
              effect == OperationDescriptor.Effect.INSPECT
                  ? OwnerContext.Right.READ
                  : OwnerContext.Right.WRITE,
              Set.of(effect),
              Set.of(
                  OperationDescriptor.EntryPoint.GUI,
                  OperationDescriptor.EntryPoint.COMMAND,
                  OperationDescriptor.EntryPoint.INTERNAL));
      descriptors.put(action, descriptor);
      scope.register(
          descriptor,
          (execution, signal) -> {
            execution.checkpoint();
            return switch (action) {
              case RELOAD ->
                  application
                      .reload(Optional.empty(), execution::checkpoint)
                      .thenApply(Outcome::of);
              case RETRY -> {
                var previous = application.last();
                var revision = previous.sourceRevision();
                if (revision.isEmpty())
                  throw new design.inherent.reform.owner.PolicyRefusal(
                      "resources.no_retry",
                      "No saved reload to retry; use Reload resources instead");
                if (previous.state() == StyleApplication.State.APPLIED)
                  throw new design.inherent.reform.owner.PolicyRefusal(
                      "resources.no_retry",
                      "Already reloaded; use Reload resources to reread files");
                yield application.reload(revision, execution::checkpoint).thenApply(Outcome::of);
              }
              case DETAILS ->
                  CompletableFuture.completedStage(
                      new Outcome(
                          "Resource details",
                          Optional.empty(),
                          Optional.of(application.last()),
                          ClientRuntime.status()));
              case FOLDER -> {
                NativeUi.openPath(ClientRuntime.configuration().root());
                yield CompletableFuture.completedStage(
                    new Outcome(
                        "Folder opening requested", Optional.empty(), Optional.empty(), List.of()));
              }
              case EXPORT_ARTWORK, EXPORT_MODEL -> {
                var resources = NativeUi.artworkSources();
                var generation = NativeUi.activePack();
                yield context.onWorker(
                    () -> {
                      Runnable current =
                          () -> {
                            context.requireCurrent();
                            execution.checkpoint();
                            if (generation.isPresent()
                                && generation.orElseThrow().preparation().isEmpty())
                              throw new IllegalStateException(
                                  "Resources changed during export; reload before retrying");
                          };
                      current.run();
                      var destination =
                          action == Action.EXPORT_ARTWORK
                              ? ProfileExporter.exportArtwork(
                                  ClientRuntime.configuration(),
                                  ClientRuntime.targets(),
                                  resources,
                                  current)
                              : ProfileExporter.exportDefault(
                                  ClientRuntime.configuration(), ClientRuntime.targets(), current);
                      return new Outcome(
                          "Created " + destination.getFileName(),
                          Optional.empty(),
                          Optional.empty(),
                          List.of(
                              "Saved to " + destination,
                              action == Action.EXPORT_ARTWORK
                                  ? "Copies retain their artwork licenses. Select the copy under Artwork; also select Models when editing packed barrel textures."
                                  : "This copy contains geometry without artwork.",
                              "Reload profiles, then choose the copy to use your edits."));
                    });
              }
            };
          });
    }
    scope.freeze();
  }

  StyleApplication application() {
    return application;
  }

  OperationHandle<Outcome> start(Action action, OperationDescriptor.EntryPoint entry) {
    context.requireOwner();
    return scope.submit(descriptors.get(action), entry, Signal.RUN);
  }

  ClientWorkflowLayout.Action<Outcome> action(Action action) {
    return new ClientWorkflowLayout.Action<>(
        descriptors.get(action).id(),
        Component.literal(action.title),
        Component.literal(context(action)),
        () -> {
          if (scope.hasActiveOperations())
            return Optional.of(Component.literal("Another resource operation is running"));
          if (action == Action.RETRY && application.last().sourceRevision().isEmpty())
            return Optional.of(Component.literal("No saved reload to retry"));
          if (action == Action.RETRY
              && application.last().state() == StyleApplication.State.APPLIED)
            return Optional.of(Component.literal("Already reloaded"));
          return Optional.empty();
        },
        () -> start(action, OperationDescriptor.EntryPoint.GUI),
        context::isOpen,
        StyleOperations::describe);
  }

  private static String context(Action action) {
    return switch (action) {
      case RELOAD -> "Read saved settings and current profiles. Unsaved edits stay in the draft.";
      case RETRY -> "Retry the previously saved revision without writing settings again.";
      case EXPORT_ARTWORK ->
          "Copy the active textures and barrel bindings. Unsaved edits are not applied.";
      case EXPORT_MODEL -> "Copy the Default barrel geometry without artwork.";
      case DETAILS -> "Inspect current resource decisions and application status.";
      case FOLDER -> "Open the local profile directory.";
    };
  }

  static OperationScreen.Outcome describe(Outcome outcome) {
    var details = new ArrayList<OperationScreen.Detail>();
    outcome
        .persistence()
        .ifPresent(
            receipt ->
                details.add(
                    new OperationScreen.Detail(
                        Component.literal("Settings"),
                        Component.literal(
                            switch (receipt.persistence()) {
                              case SAVED -> "Saved";
                              case UNCHANGED -> "Unchanged";
                              case REFUSED -> "Not saved";
                              case FAILED -> "Save failed";
                              case UNKNOWN -> "Save state unknown";
                              case NOT_ATTEMPTED -> "Not attempted";
                            }))));
    outcome
        .application()
        .ifPresent(
            result -> {
              details.add(
                  new OperationScreen.Detail(
                      Component.literal("Resources"),
                      Component.literal(
                          switch (result.state()) {
                            case APPLIED -> "Applied";
                            case REFUSED -> "Needs review";
                            case FAILED -> "Reload failed";
                            case CANCELLED -> "Cancelled";
                            case UNKNOWN -> "Not confirmed";
                          })));
              details.add(
                  new OperationScreen.Detail(
                      Component.literal("Status code"), Component.literal(result.code().name())));
              if (!outcome.summary().equals(result.message()))
                details.add(
                    new OperationScreen.Detail(
                        Component.literal("Reload result"), Component.literal(result.message())));
              result
                  .sourceRevision()
                  .ifPresent(
                      revision ->
                          details.add(
                              new OperationScreen.Detail(
                                  Component.literal("Saved revision"),
                                  Component.literal(revision))));
              result
                  .generation()
                  .ifPresent(
                      generation ->
                          details.add(
                              new OperationScreen.Detail(
                                  Component.literal("Resource generation"),
                                  Component.literal(generation.toString()))));
            });
    for (String line : outcome.details())
      details.add(new OperationScreen.Detail(Component.empty(), Component.literal(line)));
    return new OperationScreen.Outcome(Component.literal(outcome.summary()), details);
  }
}
