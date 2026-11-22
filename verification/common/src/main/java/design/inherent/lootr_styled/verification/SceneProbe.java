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
  private static final BlockPos BARREL = new BlockPos(14, 101, 2);

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
      close(mc);
      open(mc, TRAPPED);
      ticks = 0;
      stage = 7;
    } else if (stage == 7 && readyOperation() && ++ticks > 40) {
      REPORT.add("trappedOpened", observe(mc));
      assertOpen(mc, TRAPPED);
      close(mc);
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
      close(mc);
      ticks = 0;
      stage = 11;
    } else if (stage == 11 && readyOperation() && ++ticks > 40) {
      if (mc.level.getBlockState(BARREL).getValue(BlockStateProperties.OPEN))
        throw new IllegalStateException("Native barrel did not close after its viewer left");
      REPORT.add("barrelClosedAgain", observe(mc));
      captured = false;
      NativeWorld.screenshot(mc, "barrel-closed-again", () -> captured = true);
      stage = 12;
    } else if (stage == 12 && captured) {
      open(mc, BARREL);
      ticks = 0;
      stage = 13;
    } else if (stage == 13 && readyOperation() && ++ticks > 40) {
      if (!mc.level.getBlockState(BARREL).getValue(BlockStateProperties.OPEN))
        throw new IllegalStateException("Native barrel did not reopen");
      REPORT.add("barrelReopened", observe(mc));
      NativeUi.open(null);
      ticks = 0;
      stage = 14;
    } else if (stage == 14 && ++ticks > 2) {
      captured = false;
      NativeWorld.screenshot(mc, "barrel-reopened", () -> captured = true);
      stage = 15;
    } else if (stage == 15 && captured) {
      close(mc);
      REPORT.add("barrelCulling", NativeWorld.barrelCulling(mc));
      mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
      ticks = 0;
      stage = 16;
    } else if (stage == 16 && ++ticks > 30) {
      captured = false;
      NativeWorld.screenshot(mc, "barrel-third-person-item", () -> captured = true);
      stage = 17;
    } else if (stage == 17 && captured) {
      mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
      operation =
          mc.getSingleplayerServer()
              .submit(
                  () ->
                      mc.getSingleplayerServer()
                          .getPlayerList()
                          .getPlayers()
                          .getFirst()
                          .connection
                          .teleport(18.5, 103, 25, 180, 18));
      ticks = 0;
      stage = 18;
    } else if (stage == 18 && readyOperation() && ++ticks > 35) {
      captured = false;
      NativeWorld.screenshot(mc, "barrel-framed-and-dropped-items", () -> captured = true);
      stage = 19;
    } else if (stage == 19 && captured) {
      NativeUi.open(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
      ticks = 0;
      stage = 20;
    } else if (stage == 20 && ++ticks > 5) {
      captured = false;
      NativeWorld.screenshot(mc, "barrel-inventory-item", () -> captured = true);
      stage = 21;
    } else if (stage == 21 && captured) {
      NativeUi.open(null);
      REPORT.addProperty("heldItem", mc.player.getMainHandItem().getItem().toString());
      if (java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-pack"))) {
        operation =
            mc.getSingleplayerServer()
                .submit(
                    () ->
                        mc.getSingleplayerServer()
                            .getPlayerList()
                            .getPlayers()
                            .getFirst()
                            .connection
                            .teleport(34, 107, 27, 180, 25));
        ticks = 0;
        stage = 22;
      } else {
        REPORT.addProperty("passed", true);
        return REPORT;
      }
    } else if (stage == 22 && readyOperation() && ++ticks > 40) {
      captured = false;
      NativeWorld.screenshot(mc, "barrel-neighbor-cases", () -> captured = true);
      stage = 23;
    } else if (stage == 23 && captured) {
      REPORT.addProperty("renderedNeighborCases", 24);
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
    if (java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve(".lootr-styled-pack"))) {
      for (int x = 24; x < 45; x++)
        for (int z = 0; z < 24; z++)
          level.setBlock(new BlockPos(x, 100, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
      var neighbors =
          List.of(Blocks.STONE, Blocks.GLASS, Blocks.STONE_SLAB, NativeWorld.block("barrel"));
      int column = 0;
      for (var facing : Direction.values()) {
        for (int row = 0; row < neighbors.size(); row++) {
          var position = new BlockPos(25 + column * 3, 103, 3 + row * 5);
          place(
              level,
              position,
              NativeWorld.block("barrel")
                  .defaultBlockState()
                  .setValue(BlockStateProperties.FACING, facing)
                  .setValue(BlockStateProperties.OPEN, true));
          var neighbor = neighbors.get(row);
          if (neighbor == NativeWorld.block("barrel"))
            place(level, position.relative(facing), neighbor.defaultBlockState());
          else level.setBlock(position.relative(facing), neighbor.defaultBlockState(), 3);
        }
        column++;
      }
    }
    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
    var player = server.getPlayerList().getPlayers().getFirst();
    var stack = new net.minecraft.world.item.ItemStack(NativeWorld.block("barrel"));
    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack.copy());
    player.getInventory().setItem(1, stack.copy());
    level.addFreshEntity(
        new net.minecraft.world.entity.item.ItemEntity(level, 20.0, 101.5, 21.5, stack.copy()));
    level.setBlock(new BlockPos(18, 102, 20), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    var frame =
        new net.minecraft.world.entity.decoration.ItemFrame(
            level, new BlockPos(18, 102, 21), Direction.SOUTH);
    frame.setItem(stack.copy());
    level.addFreshEntity(frame);
    player.inventoryMenu.broadcastChanges();
    player.getAbilities().flying = true;
    player.onUpdateAbilities();
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

  private static void close(Minecraft mc) {
    // This fixture opens through ServerLevel. Keep close/open tasks on the same native
    // executor, rather than racing a C2S close packet against the next server-side open.
    mc.player.clientSideCloseContainer();
    operation =
        mc.getSingleplayerServer()
            .submit(
                () ->
                    mc.getSingleplayerServer()
                        .getPlayerList()
                        .getPlayers()
                        .getFirst()
                        .closeContainer());
  }

  private static void open(Minecraft mc, BlockPos position) {
    var server = mc.getSingleplayerServer();
    operation =
        server.submit(
            () -> {
              var player = server.getPlayerList().getPlayers().getFirst();
              var level = server.overworld();
              if (position.equals(BARREL))
                player.connection.teleport(
                    position.getX() + .5, 102.5, position.getZ() + 2.5, 180, 55);
              else
                player.connection.teleport(
                    position.getX() + .5, 102, position.getZ() + 3.5, 180, 20);
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
      boolean looted = (Boolean) entity.getClass().getMethod("isClientOpened").invoke(entity);
      if (pos.equals(new BlockPos(6, 101, 6)) && looted)
        throw new IllegalStateException("Opening changed an untouched neighboring container");
      if (pos.equals(BARREL) && stage >= 8 && !looted)
        throw new IllegalStateException(
            "Physical state transition lost the player's looted appearance");
      item.addProperty("clientLooted", looted);
      if (entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
        item.addProperty("lid", chest.getOpenNess(1));
      var state = mc.level.getBlockState(pos);
      if (state.hasProperty(BlockStateProperties.FACING))
        item.addProperty("facing", state.getValue(BlockStateProperties.FACING).getName());
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
