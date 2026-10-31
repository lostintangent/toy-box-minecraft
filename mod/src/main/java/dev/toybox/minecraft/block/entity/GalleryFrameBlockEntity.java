package dev.toybox.minecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class GalleryFrameBlockEntity extends StationBlockEntity {
  public GalleryFrameBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.GALLERY_FRAME, pos, state);
  }
}
