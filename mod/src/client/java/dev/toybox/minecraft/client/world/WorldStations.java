package dev.toybox.minecraft.client.world;

import dev.toybox.minecraft.block.ModBlocks;
import dev.toybox.minecraft.block.entity.ModBlockEntities;
import dev.toybox.minecraft.block.entity.StationBlockEntity;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.images.ChannelImages;
import dev.toybox.minecraft.client.screens.*;
import dev.toybox.minecraft.client.screens.artifacts.ArtifactScreen;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

/** Workshop stations render channel state and open the corresponding channel screens. */
final class WorldStations {
  private WorldStations() {}

  static void init() {
    ChannelImages.init();
    BlockEntityRenderers.register(
        ModBlockEntities.GALLERY_FRAME, ctx -> new GalleryFrameRenderer());
    BlockEntityRenderers.register(ModBlockEntities.MONITOR, ctx -> new MonitorRenderer());
    BlockEntityRenderers.register(ModBlockEntities.TASK_BOARD, ctx -> new TaskBoardRenderer());
    BlockEntityRenderers.register(
        ModBlockEntities.REQUEST_PODIUM, ctx -> new RequestPodiumRenderer());
    BlockEntityRenderers.register(
        ModBlockEntities.CHANNEL_TERMINAL, ctx -> new ChannelTerminalRenderer());
    BlockEntityRenderers.register(
        ModBlockEntities.ARTIFACT_SHELF, ctx -> new ArtifactShelfRenderer());
    UseBlockCallback.EVENT.register(
        (player, level, hand, hit) -> {
          if (!level.isClientSide() || player.isSecondaryUseActive()) return InteractionResult.PASS;
          var pos = hit.getBlockPos();
          var state = level.getBlockState(pos);
          var block = state.getBlock();
          String binding =
              level.getBlockEntity(pos) instanceof StationBlockEntity be ? be.binding() : "";
          if (block == ModBlocks.MONITOR) {
            Minecraft.getInstance()
                .gui
                .setScreen(new TranscriptScreen(null, MonitorRenderer.member(binding)));
          } else if (block == ModBlocks.GALLERY_FRAME) {
            var image = ChannelImages.at(binding);
            if (image != null) Minecraft.getInstance().gui.setScreen(new ImageScreen(image));
          } else if (block == ModBlocks.TASK_BOARD) {
            Screens.open(ChannelScreen.Page.TASKS);
          } else if (block == ModBlocks.REQUEST_PODIUM) {
            Screens.open(ChannelScreen.Page.REQUEST);
          } else if (block == ModBlocks.CHANNEL_TERMINAL) {
            Screens.openTranscript(null);
          } else if (block == ModBlocks.ARTIFACT_SHELF) {
            int index = ArtifactShelfRenderer.rowIndex(level, pos, state, binding);
            var artifact = ArtifactShelfRenderer.entryAt(index);
            Minecraft.getInstance()
                .gui
                .setScreen(new ArtifactScreen(artifact == null ? null : artifact.file()));
          } else if (block == ModBlocks.ARTIFACT_CATALOG || block == Blocks.LECTERN) {
            Screens.open(ChannelScreen.Page.ARTIFACTS);
          } else if (block == ModBlocks.PREVIEW_BENCH) {
            ChannelClient.openPreview();
          } else if (block == ModBlocks.STATUS_LAMP) {
            if (binding.startsWith("routine:")) Screens.open(ChannelScreen.Page.ROUTINES);
          } else return InteractionResult.PASS;
          return InteractionResult.FAIL;
        });
  }
}
