package design.inherent.lootr_styled.fabric;

import design.inherent.lootr_styled.client.ClientRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class LootrStyledFabric implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClientRuntime.initialize(FabricLoader.getInstance().getConfigDir());
  }
}
