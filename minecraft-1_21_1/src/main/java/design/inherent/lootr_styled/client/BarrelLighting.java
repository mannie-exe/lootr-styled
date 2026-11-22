package design.inherent.lootr_styled.client;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;

/** No world, player, resource-manager or static generation state is retained. */
public final class BarrelLighting {
  private BarrelLighting() {}

  public static boolean generated(BakedModel model) {
    return model instanceof BarrelModelMarker marker && marker.lootrStyled$isBarrel();
  }

  public static void mark(BlockModel source, BakedModel model) {
    if (!(model instanceof BarrelModelMarker baked)) return;
    // Native wrapper leaves may inherit a generated parent. Bound the walk independently
    // of any foreign parent graph; the compiler already flattens its admitted input.
    for (int depth = 0; source instanceof BarrelModelMarker marker && depth < 64; depth++) {
      if (marker.lootrStyled$isBarrel()) {
        baked.lootrStyled$markBarrel();
        return;
      }
      source = marker.lootrStyled$parent();
    }
  }
}
