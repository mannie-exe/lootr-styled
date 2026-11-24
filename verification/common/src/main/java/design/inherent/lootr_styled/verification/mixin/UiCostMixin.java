package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.UiCostAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observe the actual retained workflow's neutral render/extraction callback without replacing it.
 */
@Mixin(
    targets = "design.inherent.reform.minecraft.client.ClientEditingWorkflow$Overview",
    remap = false)
abstract class UiCostMixin {
  @Inject(
      method = "render(Ldesign/inherent/reform/minecraft/client/FormCanvas;IIF)V",
      at = @At("HEAD"))
  private void lootrStyled$beginUi(CallbackInfo callback) {
    UiCostAudit.begin(this);
  }

  @Inject(
      method = "render(Ldesign/inherent/reform/minecraft/client/FormCanvas;IIF)V",
      at = @At("RETURN"))
  private void lootrStyled$endUi(CallbackInfo callback) {
    UiCostAudit.end(this);
  }
}
