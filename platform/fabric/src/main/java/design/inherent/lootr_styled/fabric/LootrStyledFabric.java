package design.inherent.lootr_styled.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class LootrStyledFabric implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    System.getLogger("lootr_styled")
        .log(System.Logger.Level.INFO, "Lootr: Styled development bootstrap (Fabric)");
  }
}
