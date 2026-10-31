package dev.toybox.minecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Connected task panel showing the channel task tree by canonical status. */
public class TaskBoardBlockEntity extends StationBlockEntity {
  public TaskBoardBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.TASK_BOARD, pos, state);
  }
}
