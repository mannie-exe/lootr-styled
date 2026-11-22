package design.inherent.lootr_styled.client.mixin;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import design.inherent.lootr_styled.client.BarrelModelMarker;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import java.lang.reflect.Type;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockModel$Deserializer")
abstract class BarrelModelDecodeMixin {
  @Inject(
      method =
          "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
      at = @At("RETURN"))
  private void lootrStyled$decode(
      JsonElement input,
      Type type,
      JsonDeserializationContext context,
      CallbackInfoReturnable<BlockModel> callback) {
    if (!input.isJsonObject()) return;
    var marker = input.getAsJsonObject().get(BarrelCompiler.GENERATED_MARKER);
    if (marker != null
        && marker.isJsonPrimitive()
        && marker.getAsJsonPrimitive().isBoolean()
        && marker.getAsBoolean()
        && callback.getReturnValue() instanceof BarrelModelMarker generated)
      generated.lootrStyled$markBarrel();
  }
}
