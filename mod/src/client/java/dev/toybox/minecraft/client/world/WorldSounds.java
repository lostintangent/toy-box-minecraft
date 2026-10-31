package dev.toybox.minecraft.client.world;

import dev.toybox.minecraft.client.ClientEnv;
import dev.toybox.minecraft.client.channel.ChannelChanges;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel;
import dev.toybox.minecraft.client.channel.ChannelStore;
import dev.toybox.minecraft.world.layout.AnchorNames;
import dev.toybox.minecraft.world.layout.Anchors;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Spatial cues for live channel changes, quiet on initial snapshots and reconnect. */
public final class WorldSounds {
  private static long lastBell;
  private static int bells;
  private static int chimes;

  private WorldSounds() {}

  public static void init() {
    ChannelClient.store()
        .addListener(
            new ChannelStore.Listener() {
              @Override
              public void onState(
                  ChannelModel.State snapshot, java.util.List<ChannelChanges.Change> changes) {
                for (var change : changes) {
                  if (change instanceof ChannelChanges.RequestOpened) bell();
                  if (change instanceof ChannelChanges.TasksCompleted) chime();
                  if (change instanceof ChannelChanges.MemberJoined)
                    play(AnchorNames.TOY_BOX, SoundEvents.CHEST_OPEN, .3f, 1.3f);
                }
              }
            });
  }

  public static int bells() {
    return bells;
  }

  public static int chimes() {
    return chimes;
  }

  private static void bell() {
    long now = System.currentTimeMillis();
    if (now - lastBell < 1500) {
      return;
    }
    lastBell = now;
    bells++;
    play(AnchorNames.REQUEST_PODIUM, SoundEvents.NOTE_BLOCK_BELL.value(), .35f, 1.19f);
  }

  private static void chime() {
    chimes++;
    play(AnchorNames.TASK_BOARD, SoundEvents.NOTE_BLOCK_CHIME.value(), .4f, 1f);
  }

  public static void delivered() {
    play(AnchorNames.ARTIFACTS, SoundEvents.CHISELED_BOOKSHELF_INSERT, .4f, 1f);
  }

  private static void play(String anchor, SoundEvent sound, float volume, float pitch) {
    Minecraft mc = Minecraft.getInstance();
    var at = Anchors.get(anchor);
    if (ClientEnv.MUTE || mc.level == null || at == null) return;
    mc.level.playLocalSound(
        at.x(), at.y(), at.z(), sound, SoundSource.BLOCKS, volume, pitch, false);
  }
}
