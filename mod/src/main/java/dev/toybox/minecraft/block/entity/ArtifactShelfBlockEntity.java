package dev.toybox.minecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Memory archive shelf. Binding: memory scope it shows ("shared" or an agent id; empty = all). Use
 * opens the library screen. Drawn by client library.ArtifactShelfRenderer.
 */
public class ArtifactShelfBlockEntity extends StationBlockEntity {
  public ArtifactShelfBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.ARTIFACT_SHELF, pos, state);
  }
}
