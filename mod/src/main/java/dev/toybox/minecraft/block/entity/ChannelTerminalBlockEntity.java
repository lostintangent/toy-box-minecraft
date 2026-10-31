package dev.toybox.minecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Console terminal. Use opens the command console. Drawn by client console.ChannelTerminalRenderer
 * (e.g. last command / prompt on the screen).
 */
public class ChannelTerminalBlockEntity extends StationBlockEntity {
  public ChannelTerminalBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CHANNEL_TERMINAL, pos, state);
  }
}
