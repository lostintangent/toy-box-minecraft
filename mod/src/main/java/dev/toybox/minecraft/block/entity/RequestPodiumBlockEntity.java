package dev.toybox.minecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decision Podium. Binding: empty = the oldest open decision. Drawn by client
 * decisions.RequestPodiumRenderer.
 */
public class RequestPodiumBlockEntity extends StationBlockEntity {
  public RequestPodiumBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.REQUEST_PODIUM, pos, state);
  }
}
