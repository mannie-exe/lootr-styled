package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.verification.mixin.StyleOutcomeAccessor;
import design.inherent.reform.operation.OperationSnapshot;

/** Verification-only typed observation of the immutable domain result, without effect authority. */
public final class EffectProbeFacts {
  private EffectProbeFacts() {}

  public static JsonObject describe(OperationSnapshot<?> snapshot) {
    if (!(snapshot.result().orElseThrow() instanceof StyleOutcomeAccessor result))
      throw new IllegalStateException("Expected the actual Styled operation result");
    var facts = new JsonObject();
    facts.addProperty("operation", snapshot.operation().toString());
    facts.addProperty("handle", snapshot.id().toString());
    facts.addProperty("summary", result.lootrStyled$summary());
    facts.addProperty("technicalDetails", result.lootrStyled$technicalDetails());
    facts.addProperty("detailCount", result.lootrStyled$details().size());
    facts.addProperty(
        "matchesCurrentResourceTrace",
        result
            .lootrStyled$details()
            .equals(design.inherent.lootr_styled.client.ClientRuntime.status()));
    facts.addProperty("hasPersistenceReceipt", result.lootrStyled$persistence().isPresent());
    result
        .lootrStyled$persistence()
        .ifPresent(
            receipt -> {
              facts.addProperty("persistence", receipt.persistence().name());
              receipt.savedRevision().ifPresent(value -> facts.addProperty("savedRevision", value));
            });
    result
        .lootrStyled$application()
        .ifPresent(
            application -> {
              facts.addProperty("application", application.state().name());
              facts.addProperty("code", application.code().name());
              application
                  .sourceRevision()
                  .ifPresent(value -> facts.addProperty("sourceRevision", value));
              application
                  .generation()
                  .ifPresent(value -> facts.addProperty("generation", value.toString()));
            });
    return facts;
  }
}
