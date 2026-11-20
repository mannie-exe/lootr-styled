package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.NativeProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ProbeTickMixin {
  @Inject(method = "runTick", at = @At("TAIL"))
  private void lootrStyledProbe$tick(CallbackInfo callback) {
    NativeProbe.tick((Minecraft) (Object) this);
  }
}
