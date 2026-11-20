package design.inherent.lootr_styled.neoforge;

import design.inherent.lootr_styled.client.ClientRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = "lootr_styled", dist = Dist.CLIENT)
public final class LootrStyledNeoForge {
  public LootrStyledNeoForge() {
    ClientRuntime.initialize(FMLPaths.CONFIGDIR.get());
  }
}
