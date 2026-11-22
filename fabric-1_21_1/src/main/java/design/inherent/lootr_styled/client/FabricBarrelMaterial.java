package design.inherent.lootr_styled.client;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.util.TriState;

/** One immutable cache value per native wrapper, never a global renderer/resource cache. */
public record FabricBarrelMaterial(
    Renderer renderer, RenderMaterial source, RenderMaterial withoutAmbientOcclusion) {
  public static FabricBarrelMaterial prepare(
      FabricBarrelMaterial previous, Renderer renderer, RenderMaterial source) {
    if (previous != null && previous.renderer == renderer && previous.source == source)
      return previous;
    return new FabricBarrelMaterial(
        renderer,
        source,
        renderer.materialFinder().copyFrom(source).ambientOcclusion(TriState.FALSE).find());
  }
}
