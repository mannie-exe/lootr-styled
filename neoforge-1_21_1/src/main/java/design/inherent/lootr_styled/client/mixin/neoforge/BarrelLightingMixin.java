package design.inherent.lootr_styled.client.mixin.neoforge;

import design.inherent.lootr_styled.client.BarrelLighting;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.neoforge.init.ModBlockProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(
    targets = "noobanidus.mods.lootr.neoforge.client.block.BarrelModel$BarrelBakedModel",
    remap = false)
abstract class BarrelLightingMixin implements IDynamicBakedModel {
  @Shadow @Final private BakedModel opened;
  @Shadow @Final private BakedModel unopened;
  @Shadow @Final private BakedModel vanilla;
  @Shadow @Final private BakedModel old_opened;
  @Shadow @Final private BakedModel old_unopened;
  @Shadow @Final private boolean ambientOcclusion;

  @Override
  public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
    BakedModel selected = lootrStyled$selected(data);
    return BarrelLighting.generated(selected)
        ? selected.useAmbientOcclusion(state, data, renderType)
        : ambientOcclusion ? TriState.DEFAULT : TriState.FALSE;
  }

  @Unique
  private BakedModel lootrStyled$selected(ModelData data) {
    if (LootrAPI.isVanillaTextures()) return vanilla;
    boolean looted = data.get(ModBlockProperties.OPENED) == Boolean.TRUE;
    if (LootrAPI.isOldTextures()) return looted ? old_opened : old_unopened;
    return looted ? opened : unopened;
  }
}
