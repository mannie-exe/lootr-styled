package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.verification.ReloadFault;
import design.inherent.lootr_styled.verification.ReloadGate;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Verification-only held and failing native reload requests; normally delegates unchanged. */
@Mixin(value = NativeUi.class, remap = false)
public abstract class NativeReloadFaultMixin {
  @Shadow
  private static CompletableFuture<Void> reloadResources() {
    throw new AssertionError("Mixin shadow");
  }

  @Inject(method = "reloadResources", at = @At("HEAD"), cancellable = true)
  private static void failOnce(CallbackInfoReturnable<CompletableFuture<Void>> callback) {
    var held = ReloadGate.enter(NativeReloadFaultMixin::reloadResources);
    if (held != null) {
      callback.setReturnValue(held);
      return;
    }
    if (ReloadFault.enter())
      callback.setReturnValue(
          CompletableFuture.failedFuture(
              new IOException("Controlled reload failure after confirmed settings save")));
  }
}
