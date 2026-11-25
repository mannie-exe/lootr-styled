package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.ProductionPreparationAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only pause after actual native baking, inside the domain worker's material conversion. */
@Mixin(targets = "design.inherent.lootr_styled.client.StyleStillMaterials", remap = false)
abstract class ProductionWorkerMixin {
  @Inject(method = "material", at = @At("HEAD"))
  private static void lootrStyled$worker(CallbackInfoReturnable<?> result) {
    ProductionPreparationAudit.materialEntered();
  }
}
