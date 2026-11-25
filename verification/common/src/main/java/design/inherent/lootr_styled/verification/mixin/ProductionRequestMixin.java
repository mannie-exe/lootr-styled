package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.ProductionPreparationAudit;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the actual task's public request; never opens a view or starts work. */
@Mixin(value = ClientGraphicHost.View.class, remap = false)
abstract class ProductionRequestMixin {
  @Inject(method = "request", at = @At("RETURN"))
  private void lootrStyled$request(
      Object subject, CallbackInfoReturnable<ClientGraphicHost.Attempt> result) {
    ProductionPreparationAudit.requested(
        (ClientGraphicHost.View<?, ?>) (Object) this, result.getReturnValue());
  }
}
