package dev.toybox.minecraft.block.entity;

import dev.toybox.minecraft.ToyBoxMinecraft;
import dev.toybox.minecraft.block.ModBlocks;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Block entity types of the stations whose content is dynamic (drawn by a client BER from the
 * Channel state). Each type has its own small class so feature specialists can add fields to theirs
 * without touching the others.
 */
public final class ModBlockEntities {
  public static final BlockEntityType<MonitorBlockEntity> MONITOR =
      register("monitor", MonitorBlockEntity::new, ModBlocks.MONITOR);
  public static final BlockEntityType<GalleryFrameBlockEntity> GALLERY_FRAME =
      register("gallery_frame", GalleryFrameBlockEntity::new, ModBlocks.GALLERY_FRAME);
  public static final BlockEntityType<TaskBoardBlockEntity> TASK_BOARD =
      register("task_board", TaskBoardBlockEntity::new, ModBlocks.TASK_BOARD);
  public static final BlockEntityType<RequestPodiumBlockEntity> REQUEST_PODIUM =
      register("request_podium", RequestPodiumBlockEntity::new, ModBlocks.REQUEST_PODIUM);
  public static final BlockEntityType<PreviewBenchBlockEntity> PREVIEW_BENCH =
      register("preview_bench", PreviewBenchBlockEntity::new, ModBlocks.PREVIEW_BENCH);
  public static final BlockEntityType<StatusLampBlockEntity> STATUS_LAMP =
      register("status_lamp", StatusLampBlockEntity::new, ModBlocks.STATUS_LAMP);
  public static final BlockEntityType<ChannelTerminalBlockEntity> CHANNEL_TERMINAL =
      register("channel_terminal", ChannelTerminalBlockEntity::new, ModBlocks.CHANNEL_TERMINAL);
  public static final BlockEntityType<ArtifactShelfBlockEntity> ARTIFACT_SHELF =
      register("artifact_shelf", ArtifactShelfBlockEntity::new, ModBlocks.ARTIFACT_SHELF);

  private ModBlockEntities() {}

  public static void init() {
    // class init registers everything
  }

  private static <T extends BlockEntity> BlockEntityType<T> register(
      String name, BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
    return Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        ToyBoxMinecraft.id(name),
        new BlockEntityType<>(factory, Set.of(block)));
  }
}
