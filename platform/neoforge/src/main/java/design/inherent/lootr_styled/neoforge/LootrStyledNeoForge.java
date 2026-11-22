package design.inherent.lootr_styled.neoforge;

import design.inherent.lootr_styled.client.ClientRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = "lootr_styled", dist = Dist.CLIENT)
public final class LootrStyledNeoForge {
  public LootrStyledNeoForge(
      net.neoforged.fml.ModContainer container, net.neoforged.bus.api.IEventBus bus) {
    ClientRuntime.initialize(
        FMLPaths.CONFIGDIR.get(),
        () ->
            net.neoforged.fml.ModList.get()
                .getModContainerById("entity_model_features")
                .map(mod -> mod.getModInfo().getVersion().toString()));
    design.inherent.lootr_styled.client.StylePreferences.register(
        design.inherent.reform.neoforge.client.NeoForgeClientRegistration.of(bus, container));
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        net.neoforged.neoforge.event.GameShuttingDownEvent.class,
        event -> design.inherent.lootr_styled.client.StylePreferences.close());
  }
}
