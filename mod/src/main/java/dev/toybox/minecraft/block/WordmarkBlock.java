package dev.toybox.minecraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** One tile of the static 9 × 2 entrance wordmark, read left to right, top to bottom. */
public final class WordmarkBlock extends FacingBlock {
  public static final IntegerProperty PART = IntegerProperty.create("part", 0, 17);

  public WordmarkBlock(BlockBehaviour.Properties properties) {
    super(properties, Block.box(0, 0, 14, 16, 16, 16));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(PART);
  }
}
