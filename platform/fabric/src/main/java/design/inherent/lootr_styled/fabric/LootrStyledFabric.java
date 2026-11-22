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
    design.inherent.lootr_styled.client.StylePreferences.register(
        design.inherent.reform.fabric.client.FabricClientRegistration.of(
            FabricLoader.getInstance().getModContainer("lootr_styled").orElseThrow()));
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
        .register(client -> design.inherent.lootr_styled.client.StylePreferences.close());
  }
}
