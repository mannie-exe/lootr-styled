package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.verification.ReloadFault;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No original native entry for the one explicitly armed fixture request. */
@Mixin(value = NativeUi.class, remap = false)
public abstract class NativeReloadFaultMixin {
  @Inject(method = "reloadResources", at = @At("HEAD"), cancellable = true)
  private static void failOnce(CallbackInfoReturnable<CompletableFuture<Void>> callback) {
    if (ReloadFault.enter())
      callback.setReturnValue(
          CompletableFuture.failedFuture(
              new IOException("Controlled reload failure after confirmed settings save")));
  }
}
