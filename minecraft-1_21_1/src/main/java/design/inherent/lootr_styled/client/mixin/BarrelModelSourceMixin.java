package design.inherent.lootr_styled.client.mixin;

import design.inherent.lootr_styled.client.BarrelLighting;
import design.inherent.lootr_styled.client.BarrelModelMarker;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModel.class)
abstract class BarrelModelSourceMixin implements BarrelModelMarker {
  @Unique private boolean lootrStyled$barrel;
  @Shadow protected BlockModel parent;

  @Override
  public BlockModel lootrStyled$parent() {
    return parent;
  }

  @Override
  public boolean lootrStyled$isBarrel() {
    return lootrStyled$barrel;
  }

  @Override
  public void lootrStyled$markBarrel() {
    lootrStyled$barrel = true;
  }

  @Inject(
      method =
          "bake(Lnet/minecraft/client/resources/model/ModelBaker;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Z)Lnet/minecraft/client/resources/model/BakedModel;",
      at = @At("RETURN"))
  private void lootrStyled$markBaked(CallbackInfoReturnable<BakedModel> callback) {
    BarrelLighting.mark((BlockModel) (Object) this, callback.getReturnValue());
  }
}
