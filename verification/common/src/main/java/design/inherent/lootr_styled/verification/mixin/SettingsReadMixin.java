package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.client.StyleSettingsStore;
import design.inherent.lootr_styled.verification.UiCostAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Count real reads of the product's retained store, including reads on worker threads. */
@Mixin(targets = "design.inherent.reform.storage.TextStore", remap = false)
abstract class SettingsReadMixin {
  @Inject(method = "read", at = @At("HEAD"))
  private void lootrStyled$read(CallbackInfoReturnable<?> callback) {
    if ((Object) this instanceof StyleSettingsStore) UiCostAudit.settingsRead();
  }
}
