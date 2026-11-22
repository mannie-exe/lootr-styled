package design.inherent.lootr_styled.client.mixin.fabric;

import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.lootr_styled.client.BarrelLighting;
import design.inherent.lootr_styled.client.FabricBarrelMaterial;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(
    targets = "noobanidus.mods.lootr.fabric.client.block.BarrelModel$BakedBarrelModel",
    remap = false)
abstract class BarrelLightingMixin {
  @Unique private volatile FabricBarrelMaterial lootrStyled$material;

  @ModifyVariable(method = "emitBlockQuads", at = @At("STORE"), ordinal = 0)
  private RenderMaterial lootrStyled$material(
      RenderMaterial original,
      @Local(ordinal = 0) BakedModel selected,
      @Local(ordinal = 0) Renderer renderer) {
    if (!BarrelLighting.generated(selected) || selected.useAmbientOcclusion()) return original;
    var current = FabricBarrelMaterial.prepare(lootrStyled$material, renderer, original);
    lootrStyled$material = current;
    return current.withoutAmbientOcclusion();
  }
}
