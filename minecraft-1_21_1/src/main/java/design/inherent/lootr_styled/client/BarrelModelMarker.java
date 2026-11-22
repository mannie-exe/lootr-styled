package design.inherent.lootr_styled.client;

/** Generation-local marker on generated unbaked and baked barrel models. */
public interface BarrelModelMarker {
  boolean lootrStyled$isBarrel();

  void lootrStyled$markBarrel();

  default net.minecraft.client.renderer.block.model.BlockModel lootrStyled$parent() {
    return null;
  }
}
