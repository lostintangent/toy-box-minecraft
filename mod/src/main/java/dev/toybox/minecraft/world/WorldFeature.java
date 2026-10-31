package dev.toybox.minecraft.world;

import com.mojang.brigadier.context.CommandContext;
import dev.toybox.minecraft.ToyBoxMinecraft;
import dev.toybox.minecraft.command.ToyBoxMinecraftCommands;
import dev.toybox.minecraft.world.layout.Anchor;
import dev.toybox.minecraft.world.layout.AnchorNames;
import dev.toybox.minecraft.world.layout.Anchors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import org.jspecify.annotations.Nullable;

/** Builds the workshop and publishes its anchors, preserving player edits unless forced. */
public final class WorldFeature {
  private WorldFeature() {}

  private static volatile @Nullable String lastReport;

  public static @Nullable String lastReport() {
    return lastReport;
  }

  public static void init() {
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          if (!WorldRules.isToyBox(server) || !autoBuild()) return;
          try {
            buildAndPublish(server.overworld(), false);
          } catch (RuntimeException e) {
            ToyBoxMinecraft.LOGGER.error(
                "Building the workshop failed (run /toybox_minecraft hq)", e);
          }
        });
    // Keep the existing command spelling for saved command blocks and development scripts.
    ToyBoxMinecraftCommands.sub(
        root ->
            root.then(
                Commands.literal("hq")
                    .executes(ctx -> build(ctx, false))
                    .then(Commands.literal("force").executes(ctx -> build(ctx, true)))
                    .then(
                        Commands.literal("studio")
                            .executes(ctx -> build(ctx, false))
                            .then(Commands.literal("force").executes(ctx -> build(ctx, true))))));
  }

  private static boolean autoBuild() {
    String v = System.getProperty("toybox_minecraft.hq.autobuild");
    if (v == null) v = System.getenv("TOY_BOX_HQ_AUTOBUILD");
    return v == null
        || !(v.trim().equals("0")
            || v.trim().equalsIgnoreCase("false")
            || v.trim().equalsIgnoreCase("off"));
  }

  private static int build(CommandContext<CommandSourceStack> ctx, boolean force) {
    try {
      Anchors.Layout layout = buildAndPublish(ctx.getSource().getLevel(), force);
      ctx.getSource()
          .sendSuccess(
              () ->
                  Component.literal(
                      "Built Toy Box: " + layout.anchors().size() + " anchors. " + lastReport),
              true);
      return layout.anchors().size();
    } catch (RuntimeException e) {
      ToyBoxMinecraft.LOGGER.error("Building the workshop failed", e);
      ctx.getSource().sendFailure(Component.literal("Building the workshop failed: " + e));
      return 0;
    }
  }

  private static Anchors.Layout buildAndPublish(ServerLevel level, boolean force) {
    Anchors.Builder anchors = Anchors.builder(WorldBuilder.ID);
    lastReport = WorldBuilder.build(level, anchors, force);
    Anchors.Layout layout = anchors.build();
    Anchors.publish(level.getServer(), layout);
    Anchor spawn = layout.get(AnchorNames.SPAWN);
    if (spawn != null)
      level.setRespawnData(
          RespawnData.of(level.dimension(), BlockPos.containing(spawn.pos()), spawn.yaw(), 0));
    return layout;
  }
}
