package design.inherent.lootr_styled.verification.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import design.inherent.lootr_styled.verification.RendererShutdownEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
abstract class RendererShutdownMixin {
  @Inject(method = "shutdownRenderer", at = @At("RETURN"))
  private static void lootrStyled$rendererReturned(CallbackInfo callback) {
    RendererShutdownEvidence.rendererReturned("RenderSystem.shutdownRenderer");
  }
}
