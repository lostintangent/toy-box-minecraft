package dev.toybox.minecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Merge Station. Binding: empty = the oldest open merge decision. Drawn by client
 * diff.PreviewBenchRenderer.
 */
public class PreviewBenchBlockEntity extends StationBlockEntity {
  public PreviewBenchBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.PREVIEW_BENCH, pos, state);
  }
}
