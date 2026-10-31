package dev.toybox.minecraft.world;

import dev.toybox.minecraft.ToyBoxMinecraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Identity and game rules for the saved Toy Box world opened by the client's WorldLauncher.
 * Keeps this world calm and editable without affecting other worlds.
 */
public final class WorldRules {
  /** Save folder and displayed world name. */
  public static final String LEVEL_NAME = "Toy Box";

  /** Surface Y of the superflat meadow: grass top is at y=64, so you stand at y=65. */
  public static final int SURFACE_Y = 64;

  /** Golden-hour-ish day time set once when the world is created. */
  public static final long INITIAL_TIME = 12000L;

  private static final String MARKER_FILE = "toybox_minecraft-world.json";

  private WorldRules() {}

  public static void init() {
    ServerLifecycleEvents.SERVER_STARTED.register(WorldRules::onServerStarted);
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          if (!isToyBox(server)) {
            return;
          }
          ServerPlayer player = handler.getPlayer();
          // DevBridge camera shots switch to spectator; normal play should resume in creative.
          if (player.gameMode() == GameType.SPECTATOR || player.gameMode() == GameType.SURVIVAL) {
            player.setGameMode(GameType.CREATIVE);
          }
        });
  }

  public static boolean isToyBox(MinecraftServer server) {
    return LEVEL_NAME.equals(
        server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString());
  }

  private static void onServerStarted(MinecraftServer server) {
    if (!isToyBox(server)) {
      return;
    }
    applyRules(server);
    Path marker = server.getWorldPath(LevelResource.ROOT).resolve(MARKER_FILE);
    if (!Files.exists(marker)) {
      // First start of a fresh Toy Box world.
      run(server, "time set " + INITIAL_TIME);
      run(server, "weather clear");
      run(server, "setworldspawn 0 " + (SURFACE_Y + 1) + " 0");
      try {
        Files.writeString(
            marker,
            "{\"createdBy\":\"toybox_minecraft\",\"created\":\"" + Instant.now() + "\"}\n",
            StandardCharsets.UTF_8);
      } catch (IOException e) {
        ToyBoxMinecraft.LOGGER.warn("Could not write Toy Box world marker {}", marker, e);
      }
      ToyBoxMinecraft.LOGGER.info(
          "Initialised fresh Toy Box world (time {}, clear weather)", INITIAL_TIME);
    }
  }

  /** Idempotent; applied on every start so rule changes in code reach existing worlds. */
  public static void applyRules(MinecraftServer server) {
    GameRules rules = server.getGameRules();
    rules.set(GameRules.ADVANCE_TIME, false, server);
    rules.set(GameRules.ADVANCE_WEATHER, false, server);
    rules.set(GameRules.KEEP_INVENTORY, true, server);
    rules.set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, server);
    rules.set(GameRules.SPAWN_MOBS, false, server);
    rules.set(GameRules.SPAWN_MONSTERS, false, server);
    rules.set(GameRules.SPAWN_PATROLS, false, server);
    rules.set(GameRules.SPAWN_PHANTOMS, false, server);
    rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
    rules.set(GameRules.SPAWN_WARDENS, false, server);
    rules.set(GameRules.MOB_GRIEFING, false, server);
    // Preserve the construction: no vine growth or snow layers.
    rules.set(GameRules.SPREAD_VINES, false, server);
    rules.set(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT, 0, server);
    rules.set(GameRules.RESPAWN_RADIUS, 0, server);
    rules.set(GameRules.LOCATOR_BAR, false, server);
    rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
    rules.set(GameRules.COMMAND_BLOCK_OUTPUT, false, server);
    rules.set(GameRules.LOG_ADMIN_COMMANDS, false, server);
    // Allow large /fill operations for the world builder.
    rules.set(GameRules.MAX_BLOCK_MODIFICATIONS, 1_000_000, server);
  }

  private static void run(MinecraftServer server, String command) {
    server
        .getCommands()
        .performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
  }
}
