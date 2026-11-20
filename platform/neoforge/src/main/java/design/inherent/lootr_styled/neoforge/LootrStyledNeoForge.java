package design.inherent.lootr_styled.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = "lootr_styled", dist = Dist.CLIENT)
public final class LootrStyledNeoForge {
  public LootrStyledNeoForge() {
    System.getLogger("lootr_styled")
        .log(System.Logger.Level.INFO, "Lootr: Styled development bootstrap (NeoForge)");
  }
}
