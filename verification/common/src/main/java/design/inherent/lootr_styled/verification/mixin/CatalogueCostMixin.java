package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.UiCostAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive all-thread discovery entry count; never changes sources or compilation. */
@Mixin(targets = "design.inherent.lootr_styled.profile.ProfileCatalogue", remap = false)
abstract class CatalogueCostMixin {
  @Inject(method = "discover", at = @At("HEAD"))
  private static void lootrStyled$discover(CallbackInfoReturnable<?> callback) {
    UiCostAudit.catalogueDiscovered();
  }
}
