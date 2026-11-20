package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.NativeUi;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Real integrated world, actual Lootr interactions and rendered captures. Disposable fixture only.
 */
final class SceneProbe {
  private static final JsonObject REPORT = new JsonObject();
  private static int stage, ticks;
  private static CompletableFuture<Void> operation;
  private static volatile boolean captured;
  private static final BlockPos ORDINARY = new BlockPos(2, 101, 6);
  private static final BlockPos TRAPPED = new BlockPos(2, 101, 10);
  private static final BlockPos BARREL = new BlockPos(8, 101, 2);

  private SceneProbe() {}

  static JsonObject snapshot() {
    return REPORT.deepCopy();
  }

  static void begin(Minecraft mc) {
    NativeWorld.create(mc);
  }

  static JsonObject tick(Minecraft mc) throws Exception {
    if (stage == 0 && NativeUi.current() instanceof CreateWorldScreen screen) {
      var settings = screen.getUiState();
      settings.setName("Lootr Styled Scene");
      settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
      settings.setDifficulty(Difficulty.PEACEFUL);
      settings.setGenerateStructures(false);
      settings.setSeed("42");
      var presets = new java.util.ArrayList<>(settings.getNormalPresetList());
      presets.addAll(settings.getAltPresetList());
      settings.setWorldType(
          presets.stream()
              .filter(
                  entry ->
                      entry.preset() != null
                          && entry
                              .preset()
                              .is(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT))
              .findFirst()
              .orElseThrow());
      NativeBindings.press(
          screen.children().stream()
              .filter(Button.class::isInstance)
              .map(Button.class::cast)
              .filter(button -> button.getMessage().getString().equals("Create New World"))
              .findFirst()
              .orElseThrow());
      stage = 1;
    } else if (stage == 1
        && mc.level != null
        && mc.player != null
        && mc.getSingleplayerServer() != null) {
      operation = mc.getSingleplayerServer().submit(() -> setup(mc));
      ticks = 0;
      stage = 2;
    } else if (stage == 2 && readyOperation() && ++ticks > 60) {
      REPORT.add("closed", observe(mc));
      captured = false;
      NativeWorld.screenshot(mc, "scene-closed", () -> captured = true);
      stage = 3;
    } else if (stage == 3 && captured) {
      open(mc, ORDINARY);
      ticks = 0;
      stage = 4;
    } else if (stage == 4 && readyOperation() && ++ticks > 40) {
      assertOpen(mc, ORDINARY);
      REPORT.add("ordinaryOpened", observe(mc));
      NativeUi.open(null);
      ticks = 0;
      stage = 5;
    } else if (stage == 5 && ++ticks > 2) {
      captured = false;
      NativeWorld.screenshot(mc, "scene-ordinary-open", () -> captured = true);
      stage = 6;
    } else if (stage == 6 && captured) {
      mc.player.clientSideCloseContainer();
      open(mc, TRAPPED);
      ticks = 0;
      stage = 7;
    } else if (stage == 7 && readyOperation() && ++ticks > 40) {
      REPORT.add("trappedOpened", observe(mc));
      assertOpen(mc, TRAPPED);
      mc.player.clientSideCloseContainer();
      open(mc, BARREL);
      ticks = 0;
      stage = 8;
    } else if (stage == 8 && readyOperation() && ++ticks > 40) {
      REPORT.add("barrelOpened", observe(mc));
      if (!mc.level.getBlockState(BARREL).getValue(BlockStateProperties.OPEN))
        throw new IllegalStateException("Native barrel interaction did not preserve physical OPEN");
      NativeUi.open(null);
      ticks = 0;
      stage = 9;
    } else if (stage == 9 && ++ticks > 2) {
      captured = false;
      NativeWorld.screenshot(mc, "scene-looted", () -> captured = true);
      stage = 10;
    } else if (stage == 10 && captured) {
      mc.player.clientSideCloseContainer();
      REPORT.addProperty("passed", true);
      return REPORT;
    }
    return null;
  }

  private static boolean readyOperation() {
    if (operation == null || !operation.isDone()) return false;
    operation.join();
    return true;
  }

  private static void setup(Minecraft mc) {
    var server = mc.getSingleplayerServer();
    var level = server.overworld();
    for (int x = 0; x < 21; x++)
      for (int z = 0; z < 25; z++)
        level.setBlock(new BlockPos(x, 100, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    var directions = List.of(Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST);
    for (int i = 0; i < 4; i++) {
      place(
          level,
          new BlockPos(2 + i * 4, 101, 6),
          NativeWorld.block("chest")
              .defaultBlockState()
              .setValue(BlockStateProperties.HORIZONTAL_FACING, directions.get(i)));
      place(
          level,
          new BlockPos(2 + i * 4, 101, 10),
          NativeWorld.block("trapped_chest")
              .defaultBlockState()
              .setValue(BlockStateProperties.HORIZONTAL_FACING, directions.get(i)));
    }
    var barrelDirections =
        List.of(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.UP,
            Direction.DOWN);
    for (int i = 0; i < 6; i++)
      for (boolean open : List.of(false, true))
        place(
            level,
            new BlockPos(2 + i * 3, 101, open ? 14 : 2),
            NativeWorld.block("barrel")
                .defaultBlockState()
                .setValue(BlockStateProperties.FACING, barrelDirections.get(i))
                .setValue(BlockStateProperties.OPEN, open));
    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
    var player = server.getPlayerList().getPlayers().getFirst();
    player.connection.teleport(10.5, 105, 23.5, 180, 25);
  }

  private static void place(
      net.minecraft.server.level.ServerLevel level,
      BlockPos position,
      net.minecraft.world.level.block.state.BlockState state) {
    if (state.isAir()) throw new IllegalStateException("Unknown native Lootr fixture block");
    level.setBlock(position, state, 3);
    if (!(level.getBlockEntity(position) instanceof RandomizableContainerBlockEntity entity))
      throw new IllegalStateException("Missing native container at " + position);
    entity.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 42);
    entity.setChanged();
  }

  private static void open(Minecraft mc, BlockPos position) {
    var server = mc.getSingleplayerServer();
    operation =
        server.submit(
            () -> {
              var player = server.getPlayerList().getPlayers().getFirst();
              var level = server.overworld();
              player.connection.teleport(position.getX() + .5, 102, position.getZ() + 3.5, 180, 20);
              level
                  .getBlockState(position)
                  .useWithoutItem(
                      level,
                      player,
                      new BlockHitResult(
                          Vec3.atCenterOf(position), Direction.SOUTH, position, false));
            });
  }

  private static JsonObject observe(Minecraft mc) throws Exception {
    var report = new JsonObject();
    var containers = new JsonArray();
    for (BlockPos pos : List.of(ORDINARY, TRAPPED, BARREL, new BlockPos(6, 101, 6))) {
      Object entity = mc.level.getBlockEntity(pos);
      if (entity == null)
        throw new IllegalStateException("Missing client fixture container " + pos);
      var item = new JsonObject();
      item.addProperty("position", pos.toShortString());
      item.addProperty("class", entity.getClass().getName());
      item.addProperty(
          "clientLooted", (Boolean) entity.getClass().getMethod("isClientOpened").invoke(entity));
      if (entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
        item.addProperty("lid", chest.getOpenNess(1));
      var state = mc.level.getBlockState(pos);
      if (state.hasProperty(BlockStateProperties.OPEN))
        item.addProperty("physicalOpen", state.getValue(BlockStateProperties.OPEN));
      containers.add(item);
    }
    report.add("containers", containers);
    return report;
  }

  private static void assertOpen(Minecraft mc, BlockPos position) {
    if (!(mc.level.getBlockEntity(position)
            instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
        || chest.getOpenNess(1) < .5)
      throw new IllegalStateException("Native lid did not animate open: " + position);
  }
}
