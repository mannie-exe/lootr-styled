package design.inherent.lootr_styled.neoforge;

import design.inherent.lootr_styled.client.ClientRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = "lootr_styled", dist = Dist.CLIENT)
public final class LootrStyledNeoForge {
  public LootrStyledNeoForge(net.neoforged.fml.ModContainer container) {
    ClientRuntime.initialize(
        FMLPaths.CONFIGDIR.get(),
        () ->
            net.neoforged.fml.ModList.get()
                .getModContainerById("entity_model_features")
                .map(mod -> mod.getModInfo().getVersion().toString()));
    container.registerExtensionPoint(
        net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
        (minecraft, parent) -> new design.inherent.lootr_styled.client.StyleScreen(parent));
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) ->
            design.inherent.lootr_styled.client.ClientCommands.register(event.getDispatcher()));
  }
}
