package design.inherent.lootr_styled.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.lootr_styled.client.GeneratedPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import noobanidus.mods.lootr.common.api.LootrChestType;
import noobanidus.mods.lootr.common.client.block.LootrChestBlockRenderer;
import noobanidus.mods.lootr.common.client.state.LootrChestBlockRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep copper states on independent native layer parts when this addon owns the ordinary rig. */
@Mixin(value = LootrChestBlockRenderer.class, remap = false)
abstract class LootrChestScopeMixin {
  @Unique private ChestModel lootrStyled$nativeOthers;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void lootrStyled$isolate(
      BlockEntityRendererProvider.Context context, CallbackInfo callback) {
    if (((Object) this).getClass() != LootrChestBlockRenderer.class) return;
    var resource =
        Minecraft.getInstance()
            .getResourceManager()
            .getResource(Identifier.parse("minecraft:emf/cem/lootr_chest.jem"));
    if (resource.isEmpty() || !(resource.orElseThrow().source() instanceof GeneratedPack)) return;
    var layer =
        ((EntityModelSetAccessor) (Object) context.entityModelSet())
            .lootrStyled$roots()
            .get(ModelLayers.CHEST);
    if (layer == null)
      throw new IllegalStateException(
          "Owned chest adaptation requires the effective native chest layer");
    lootrStyled$nativeOthers = new ChestModel(layer.bakeRoot());
  }

  @ModifyExpressionValue(
      method =
          "submit(Lnoobanidus/mods/lootr/common/client/state/LootrChestBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnoobanidus/mods/lootr/common/client/block/LootrChestBlockRenderer;singleModel:Lnet/minecraft/client/model/object/chest/ChestModel;"),
      require = 3,
      allow = 3)
  private ChestModel lootrStyled$model(
      ChestModel original, @Local(argsOnly = true) LootrChestBlockRenderState state) {
    return lootrStyled$nativeOthers != null
            && state.chestType != LootrChestType.NORMAL
            && state.chestType != LootrChestType.TRAPPED
        ? lootrStyled$nativeOthers
        : original;
  }
}
