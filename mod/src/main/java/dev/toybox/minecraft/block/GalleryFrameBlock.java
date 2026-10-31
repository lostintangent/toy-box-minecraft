package dev.toybox.minecraft.block;

import dev.toybox.minecraft.block.entity.GalleryFrameBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Connected frames for images publicly shared in the channel. */
public final class GalleryFrameBlock extends PanelBlock {
  public GalleryFrameBlock(BlockBehaviour.Properties properties) {
    super(properties, 3);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new GalleryFrameBlockEntity(pos, state);
  }
}
