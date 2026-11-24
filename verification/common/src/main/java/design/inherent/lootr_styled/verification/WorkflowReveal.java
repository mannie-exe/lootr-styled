package design.inherent.lootr_styled.verification;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.NavigationAttempt;
import design.inherent.reform.presentation.DestinationId;
import java.util.concurrent.CompletableFuture;

/** Await the actual native reveal; actor admission alone does not mount editable controls. */
final class WorkflowReveal {
  private WorkflowReveal() {}

  static <T> CompletableFuture<ClientEditor<T>> document(
      ClientEditingWorkflow workflow, DocumentDefinition<T> definition) {
    return reveal(workflow, definition, new ClientDestination.Document(definition.id()));
  }

  static <T> CompletableFuture<ClientEditor<T>> view(
      ClientEditingWorkflow workflow, DocumentDefinition<T> definition, DestinationId destination) {
    return reveal(workflow, definition, new ClientDestination.View(destination));
  }

  static <T> CompletableFuture<ClientEditor<T>> field(
      ClientEditingWorkflow workflow, DocumentDefinition<T> definition, FieldPath path) {
    return reveal(workflow, definition, new ClientDestination.Field(definition.id(), path));
  }

  private static <T> CompletableFuture<ClientEditor<T>> reveal(
      ClientEditingWorkflow workflow, DocumentDefinition<T> definition, ClientDestination target) {
    var attempt = workflow.navigation().open(target);
    return attempt
        .completion()
        .thenApply(
            result -> {
              if (!result.id().equals(attempt.id())
                  || !result.target().equals(target)
                  || result.outcome() != NavigationAttempt.Outcome.REVEALED)
                throw new IllegalStateException(
                    "The native destination was not revealed: " + result,
                    result.cause().orElse(null));
              return workflow.editor(definition);
            })
        .toCompletableFuture();
  }
}
