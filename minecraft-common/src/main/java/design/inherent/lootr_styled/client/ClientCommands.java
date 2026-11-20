package design.inherent.lootr_styled.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

/** Same local actions on both loaders. No setting command is sent to the server. */
public final class ClientCommands {
  private ClientCommands() {}

  public static <S> void register(CommandDispatcher<S> dispatcher) {
    dispatcher.register(
        LiteralArgumentBuilder.<S>literal("lootrstyled")
            .executes(
                context -> {
                  Minecraft.getInstance()
                      .execute(() -> NativeUi.open(new StyleScreen(NativeUi.current())));
                  return 1;
                }));
  }
}
