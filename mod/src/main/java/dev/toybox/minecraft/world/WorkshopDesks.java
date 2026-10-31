package dev.toybox.minecraft.world;

import dev.toybox.minecraft.block.LampStatus;
import dev.toybox.minecraft.block.ModBlocks;
import dev.toybox.minecraft.block.MonitorBlock;
import dev.toybox.minecraft.block.PanelBlock;
import dev.toybox.minecraft.block.StatusLampBlock;
import dev.toybox.minecraft.block.entity.StationBlockEntity;
import dev.toybox.minecraft.world.layout.Anchor;
import dev.toybox.minecraft.world.layout.AnchorNames;
import dev.toybox.minecraft.world.layout.Anchors;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Incremental desk pods. Existing structures and player edits are never rewritten or removed. */
public final class WorkshopDesks {
  private WorkshopDesks() {}

  public static int count(Anchors.Layout layout) {
    return (int)
        layout.anchors().keySet().stream()
            .filter(n -> n.startsWith(AnchorNames.DESK_PREFIX))
            .count();
  }

  /**
   * Server-thread only. Validated empty footprints are the only cells this operation can change.
   */
  public static void ensure(ServerLevel level, int wanted) {
    Anchors.Layout layout = Anchors.current();
    if (!layout.name().equals(WorldBuilder.ID) || count(layout) >= wanted) return;
    var anchors = new LinkedHashMap<>(layout.anchors());
    var bounds = layout.bounds();
    int count = count(layout), built = 0;
    // Bound a single update's work. If terrain is obstructed the client uses validated free floor
    // cells.
    for (int candidate = 0; candidate < 2048 && count < wanted && built < 32; candidate++) {
      DeskPodLayout.Pod pod = DeskPodLayout.candidate(candidate);
      if (!clear(level, pod)) continue;
      String binding = "pod_" + candidate;
      int x = pod.x(), z = pod.z(), floor = 65, feet = 66;
      for (int bx = pod.minX(); bx <= pod.maxX(); bx++)
        for (int bz = pod.minZ(); bz <= pod.maxZ(); bz++) {
          level.setBlock(
              new BlockPos(bx, floor, bz), ModBlocks.OAK_PARQUET.defaultBlockState(), Blueprint.FLAGS);
        }
      for (int bx = x - 1; bx <= x + 1; bx++) {
        level.setBlock(new BlockPos(bx, feet, z - 1), St.slab(Blocks.BIRCH_SLAB, true), Blueprint.FLAGS);
        BlockPos screen = new BlockPos(bx, feet + 1, z - 1);
        level.setBlock(
            screen,
            ModBlocks.MONITOR
                .defaultBlockState()
                .setValue(PanelBlock.FACING, Direction.SOUTH)
                .setValue(MonitorBlock.LIT, true),
            Blueprint.FLAGS);
        bind(level, screen, binding);
      }
      level.setBlock(
          new BlockPos(x, feet, z),
          St.stairs(Blocks.BIRCH_STAIRS, Direction.SOUTH, false),
          Blueprint.FLAGS);
      BlockPos lamp = new BlockPos(x - 2, feet, z - 1);
      level.setBlock(
          lamp,
          ModBlocks.STATUS_LAMP
              .defaultBlockState()
              .setValue(StatusLampBlock.STATUS, LampStatus.IDLE),
          Blueprint.FLAGS);
      bind(level, lamp, "agent:" + binding);
      anchors.put(
          AnchorNames.desk(binding),
          new Anchor(AnchorNames.desk(binding), x + .5, feet, z + .5, 180, 0));
      anchors.put(
          AnchorNames.monitor(binding),
          new Anchor(AnchorNames.monitor(binding), x + .5, feet + 1.5, z - .75, 0, 0));
      bounds =
          bounds == null
              ? new Anchors.Bounds(x - 3, 64, z - 3, x + 3, 80, z + 3)
              : new Anchors.Bounds(
                  Math.min(bounds.minX(), x - 3),
                  Math.min(bounds.minY(), 64),
                  Math.min(bounds.minZ(), z - 3),
                  Math.max(bounds.maxX(), x + 3),
                  Math.max(bounds.maxY(), 80),
                  Math.max(bounds.maxZ(), z + 3));
      count++;
      built++;
    }
    if (built > 0)
      Anchors.publish(
          level.getServer(),
          new Anchors.Layout(
              layout.name(), layout.revision(), bounds, java.util.Map.copyOf(anchors)));
  }

  private static boolean clear(ServerLevel level, DeskPodLayout.Pod pod) {
    for (int x = pod.minX(); x <= pod.maxX(); x++)
      for (int z = pod.minZ(); z <= pod.maxZ(); z++) {
        if (!level.getBlockState(new BlockPos(x, 64, z)).is(Blocks.GRASS_BLOCK)
            && !level.getBlockState(new BlockPos(x, 64, z)).is(Blocks.DIRT)) return false;
        for (int y = 65; y <= 70; y++) {
          var state = level.getBlockState(new BlockPos(x, y, z));
          if (!state.isAir() && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.TALL_GRASS))
            return false;
        }
      }
    return true;
  }

  private static void bind(ServerLevel level, BlockPos pos, String value) {
    if (level.getBlockEntity(pos) instanceof StationBlockEntity station) station.setBinding(value);
  }
}
