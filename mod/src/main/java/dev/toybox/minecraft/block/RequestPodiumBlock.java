package dev.toybox.minecraft.block;

import dev.toybox.minecraft.block.entity.RequestPodiumBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;

/** A pending channel request opens the podium and emits a strength-15 redstone signal. */
public class RequestPodiumBlock extends FacingEntityBlock {
  public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

  public RequestPodiumBlock(BlockBehaviour.Properties properties) {
    super(
        properties,
        Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(4, 2, 4, 12, 11, 12),
            Block.box(0.5, 11, 1, 15.5, 15, 16)),
        RequestPodiumBlockEntity::new);
  }

  @Override
  protected BlockState initialState(BlockState state) {
    return state.setValue(OPEN, false);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(OPEN);
  }

  @Override
  protected boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return state.getValue(OPEN) ? 15 : 0;
  }

  public static int light(BlockState state) {
    return state.getValue(OPEN) ? 9 : 0;
  }
}
