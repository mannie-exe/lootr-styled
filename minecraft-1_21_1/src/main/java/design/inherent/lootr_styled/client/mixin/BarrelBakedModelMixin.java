package design.inherent.lootr_styled.client.mixin;

import design.inherent.lootr_styled.client.BarrelModelMarker;
import net.minecraft.client.resources.model.SimpleBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SimpleBakedModel.class)
abstract class BarrelBakedModelMixin implements BarrelModelMarker {
  @Unique private boolean lootrStyled$barrel;

  @Override
  public boolean lootrStyled$isBarrel() {
    return lootrStyled$barrel;
  }

  @Override
  public void lootrStyled$markBarrel() {
    lootrStyled$barrel = true;
  }
}
