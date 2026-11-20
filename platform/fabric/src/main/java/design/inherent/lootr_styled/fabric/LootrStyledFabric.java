package design.inherent.lootr_styled.fabric;

import design.inherent.lootr_styled.client.ClientRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class LootrStyledFabric implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClientRuntime.initialize(
        FabricLoader.getInstance().getConfigDir(),
        () ->
            FabricLoader.getInstance()
                .getModContainer("entity_model_features")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()));
    net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, access) ->
            design.inherent.lootr_styled.client.ClientCommands.register(dispatcher));
  }
}
