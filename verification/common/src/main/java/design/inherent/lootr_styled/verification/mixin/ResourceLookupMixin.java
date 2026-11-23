package design.inherent.lootr_styled.verification.mixin;

import design.inherent.lootr_styled.verification.ResourceLookupAudit;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPackResourceManager.class)
abstract class ResourceLookupMixin {
  @Inject(
      method = {
        "getResource",
        "getResourceStack",
        "listResources",
        "listResourceStacks",
        "listPacks",
        "getNamespaces"
      },
      at = @At("HEAD"))
  private void lootrStyled$observeLookup(CallbackInfoReturnable<?> callback) {
    ResourceLookupAudit.observed();
  }
}
