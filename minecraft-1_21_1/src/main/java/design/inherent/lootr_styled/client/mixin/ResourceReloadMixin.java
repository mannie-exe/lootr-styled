package design.inherent.lootr_styled.client.mixin;

import design.inherent.lootr_styled.client.GeneratedPack;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ReloadableResourceManager.class)
abstract class ResourceReloadMixin {
  @Shadow @Final private PackType type;

  @ModifyVariable(method = "createReload", at = @At("HEAD"), argsOnly = true)
  private List<PackResources> lootrStyled$append(List<PackResources> packs) {
    return type == PackType.CLIENT_RESOURCES ? GeneratedPack.append(packs) : packs;
  }
}
