package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.GraphicDrawAudit;
import design.inherent.reform.minecraft.client.ClientMeshGraphic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientMeshGraphic.class, remap = false)
abstract class GraphicDrawMixin {
  @Inject(method = "draw", at = @At("RETURN"), remap = false)
  private void lootrStyled$drawResult(
      CallbackInfoReturnable<ClientMeshGraphic.DrawOutcome> result) {
    GraphicDrawAudit.observed((ClientMeshGraphic) (Object) this, result.getReturnValue());
  }
}
