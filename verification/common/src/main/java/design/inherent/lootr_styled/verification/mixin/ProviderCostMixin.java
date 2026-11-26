package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.UiCostAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Count actual domain provider entries; never changes preparation, authority, or outcomes. */
@Mixin(targets = "design.inherent.lootr_styled.client.StyleGraphicProvider", remap = false)
abstract class ProviderCostMixin {
  @Inject(method = "prepareCaptured", at = @At("HEAD"))
  private void lootrStyled$prepare(
      design.inherent.reform.presentation.graphics.CapturedGraphicRequest<?, ?> request,
      design.inherent.reform.minecraft.client.ClientGraphicWork work,
      CallbackInfoReturnable<?> callback) {
    UiCostAudit.providerEntered();
    design.inherent.lootr_styled.verification.SemanticProfileAudit.observe(request);
  }
}
