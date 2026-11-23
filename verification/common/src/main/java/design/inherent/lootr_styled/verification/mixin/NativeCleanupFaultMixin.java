package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.NativeCleanupFault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "design.inherent.reform.minecraft.client.NativeMeshGraphic", remap = false)
abstract class NativeCleanupFaultMixin {
  @Inject(method = "release()V", at = @At("RETURN"), remap = false, require = 1)
  private void lootrStyled$cleanupFault(CallbackInfo ignored) {
    NativeCleanupFault.afterRelease(this);
  }
}
