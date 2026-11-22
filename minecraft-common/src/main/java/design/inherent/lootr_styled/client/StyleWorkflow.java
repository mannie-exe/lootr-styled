package design.inherent.lootr_styled.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor;
import design.inherent.reform.operation.OperationScope;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.value.ValueTypes;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Retained shared forms plus one owner-bound asynchronous save/reload operation. */
final class StyleWorkflow implements AutoCloseable {
  private final OperationScope scope;
  private ClientEditingWorkflow workflow;

  StyleWorkflow(Screen parent, ClientForm form, StyleOperations operations, OwnerContext context) {
    var access = context.access(this::isOpen, this::isOpen);
    scope = new OperationScope(context, access, OperationScope.Limits.STANDARD);
    var save =
        new OperationDescriptor<StyleOperations.Signal, StyleOperations.Outcome>(
            DocumentId.parse("lootr_styled:save_reload"),
            ValueTypes.enumeration(StyleOperations.Signal.class),
            OwnerContext.Right.WRITE,
            Set.of(OperationDescriptor.Effect.PERSIST, OperationDescriptor.Effect.APPLY),
            Set.of(OperationDescriptor.EntryPoint.GUI),
            Optional.of(StyleSettingsStore.DEFINITION.id()));
    scope.register(
        save,
        (execution, request) ->
            workflow
                .openEditor(form.route())
                .thenCompose(
                    editor ->
                        DocumentActions.result(
                                editor.actions().ensureLoaded(OperationDescriptor.EntryPoint.GUI))
                            .thenCompose(
                                loaded ->
                                    context.onOwner(
                                        () -> {
                                          requireSubmission(editor);
                                          execution.checkpoint();
                                          return editor
                                              .actions()
                                              .commit(OperationDescriptor.EntryPoint.GUI);
                                        }))
                            .thenCompose(DocumentActions::result))
                .thenCompose(
                    committed ->
                        context
                            .onOwner(
                                () -> {
                                  var receipt = committed.receipt();
                                  if (receipt.isEmpty()
                                      || !receipt.orElseThrow().acknowledgesBaseline()
                                      || receipt.orElseThrow().savedRevision().isEmpty()) {
                                    return CompletableFuture.completedStage(
                                        new StyleOperations.Outcome(
                                            "Settings were not confirmed saved",
                                            receipt.map(value -> (CommitReceipt<?>) value),
                                            Optional.empty(),
                                            List.of(
                                                "Review the settings result before retrying.")));
                                  }
                                  var saved = receipt.orElseThrow();
                                  return operations
                                      .application()
                                      .reload(saved.savedRevision(), execution::checkpoint)
                                      .thenApply(
                                          applied ->
                                              new StyleOperations.Outcome(
                                                  applied.state() == StyleApplication.State.APPLIED
                                                      ? "Saved and reloaded"
                                                      : "Saved; reload not confirmed",
                                                  Optional.of(saved),
                                                  Optional.of(applied),
                                                  applied.details()));
                                })
                            .thenCompose(stage -> stage)));
    scope.freeze();
    var saveAction =
        new ClientWorkflowLayout.Action<>(
            save.id(),
            Component.literal("Save and reload resources"),
            Component.literal("Save this draft, then wait for its resources to finish reloading."),
            this::blocked,
            () ->
                scope.submit(save, OperationDescriptor.EntryPoint.GUI, StyleOperations.Signal.RUN),
            this::isOpen,
            StyleOperations::describe);
    var placement =
        new ClientWorkflowLayout.Placement(
            DocumentId.parse("lootr_styled:appearance"),
            form.route(),
            Component.literal("Appearance"),
            Optional.empty());
    var layout =
        new ClientWorkflowLayout(
            List.of(
                new ClientWorkflowLayout.Group(
                    Component.literal("Settings"),
                    List.of(placement),
                    List.of(
                        saveAction,
                        operations.action(StyleOperations.Action.RELOAD),
                        operations.action(StyleOperations.Action.RETRY))),
                new ClientWorkflowLayout.Group(
                    Component.literal("Profiles"),
                    List.of(),
                    List.of(
                        operations.action(StyleOperations.Action.EXPORT_ARTWORK),
                        operations.action(StyleOperations.Action.EXPORT_MODEL),
                        operations.action(StyleOperations.Action.FOLDER),
                        operations.action(StyleOperations.Action.DETAILS)))),
            List.of());
    workflow =
        ClientEditingWorkflow.open(
            parent, Component.literal("Lootr: Styled"), List.of(form), layout);
  }

  private static void requireSubmission(ClientEditor<?> editor) {
    if (!editor.canSubmit())
      throw new PolicyRefusal(
          "settings.pending", "Finish or correct the current edit before saving");
  }

  private Optional<Component> blocked() {
    if (!isOpen()) return Optional.of(Component.literal("Settings editor is closed"));
    if (scope.hasActiveOperations())
      return Optional.of(Component.literal("Save and reload is running"));
    var documents = workflow.documents();
    var document = documents.getFirst();
    if (!document.available()) return Optional.of(Component.literal("Settings unavailable"));
    if (document.busy()) return Optional.of(Component.literal("Settings are busy"));
    if (document.opened()) {
      try {
        requireSubmission(workflow.editor(document.document()));
      } catch (PolicyRefusal refused) {
        return Optional.of(Component.literal("Correct the current edit before saving"));
      }
    }
    return Optional.empty();
  }

  boolean isOpen() {
    return workflow != null && workflow.isOpen();
  }

  ClientEditingWorkflow workflow() {
    return workflow;
  }

  @Override
  public void close() {
    if (workflow != null && workflow.isOpen()) workflow.close();
    scope.close();
  }
}
