package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.RendererShutdownEvidence;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class RendererShutdownMixin {
  @Inject(method = "close", at = @At("RETURN"))
  private void lootrStyled$rendererReturned(CallbackInfo callback) {
    RendererShutdownEvidence.rendererReturned("Minecraft.close");
  }
}
