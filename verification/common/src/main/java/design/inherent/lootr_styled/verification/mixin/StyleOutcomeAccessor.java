package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.client.StyleApplication;
import design.inherent.reform.owner.CommitReceipt;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only immutable domain receipt fields; no actor, operation or write access. */
@Mixin(targets = "design.inherent.lootr_styled.client.StyleOperations$Outcome", remap = false)
public interface StyleOutcomeAccessor {
  @Accessor("summary")
  String lootrStyled$summary();

  @Accessor("persistence")
  Optional<CommitReceipt<?>> lootrStyled$persistence();

  @Accessor("application")
  Optional<StyleApplication.Result> lootrStyled$application();
}
