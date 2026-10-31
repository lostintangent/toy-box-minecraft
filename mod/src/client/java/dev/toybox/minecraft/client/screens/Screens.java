package dev.toybox.minecraft.client.screens;

import com.google.gson.JsonObject;
import dev.toybox.minecraft.block.ModBlocks;
import dev.toybox.minecraft.block.entity.StationBlockEntity;
import dev.toybox.minecraft.client.agents.ClientAgentEntity;
import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.dev.DevBridge;
import dev.toybox.minecraft.client.dev.Fields;
import dev.toybox.minecraft.client.hud.Keys;
import dev.toybox.minecraft.client.images.ChannelImages;
import dev.toybox.minecraft.client.screens.artifacts.ArtifactScreen;
import dev.toybox.minecraft.client.world.TaskBoardRenderer;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Channel navigation, keyboard entry points, and screen previews. */
public final class Screens {
  private static boolean offeredConnection;

  private Screens() {}

  public static void open(ChannelScreen.Page page) {
    Minecraft.getInstance().gui.setScreen(screen(page));
  }

  public static void openTranscript(String prefill) {
    Minecraft.getInstance().gui.setScreen(transcript(prefill));
  }

  /** The one place a page becomes a screen, for navigation and the dev bridge alike. */
  private static Screen screen(ChannelScreen.Page page) {
    return switch (page) {
      case TRANSCRIPT -> transcript(null);
      case TEAM -> new TeamScreen();
      case TASKS -> new TasksScreen();
      case REQUEST -> new RequestScreen();
      case ARTIFACTS -> new ArtifactScreen();
      case ROUTINES -> new RoutinesScreen();
    };
  }

  /** Without a usable channel, the transcript's place is the channel picker. */
  private static Screen transcript(String prefill) {
    return ChannelClient.config().error() != null
            || ChannelClient.connection().phase() == ChannelConnection.Phase.DELETED
        ? new ChannelPickerScreen()
        : new TranscriptScreen(prefill);
  }

  /** Pointing at public channel content only adds context to an ordinary channel message. */
  public static String discussion() {
    var mc = Minecraft.getInstance();
    if (mc.hitResult instanceof EntityHitResult hit
        && hit.getEntity() instanceof ClientAgentEntity agent
        && ChannelClient.store().agent(agent.agentId()) != null)
      return "@" + ChannelClient.mention(agent.agentId()) + " ";
    if (mc.hitResult instanceof BlockHitResult hit && mc.level != null) {
      var block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
      if (block == ModBlocks.TASK_BOARD) {
        var task = TaskBoardRenderer.taskAt(hit);
        return task == null ? null : "About “" + task.title() + "”: ";
      }
      if (block == ModBlocks.GALLERY_FRAME
          && mc.level.getBlockEntity(hit.getBlockPos()) instanceof StationBlockEntity be) {
        var image = ChannelImages.at(be.binding());
        return image == null ? null : image.discussion();
      }
    }
    return null;
  }

  public static void init() {
    Keys.ensureRegistered();
    ClientTickEvents.END_CLIENT_TICK.register(
        mc -> {
          if (!offeredConnection && mc.player != null && mc.gui.screen() == null) {
            offeredConnection = true;
            if (ChannelClient.config().error() != null) mc.gui.setScreen(new ChannelPickerScreen());
          }
          while (Keys.transcript.consumeClick()) if (mc.gui.screen() == null) openTranscript(null);
          while (Keys.discuss.consumeClick())
            if (mc.gui.screen() == null) {
              String context = discussion();
              if (context != null) openTranscript(context);
            }
          while (Keys.requests.consumeClick())
            if (mc.gui.screen() == null) open(ChannelScreen.Page.REQUEST);
          while (Keys.terminal.consumeClick())
            if (mc.gui.screen() == null
                && mc.hitResult instanceof BlockHitResult hit
                && mc.level != null
                && mc.level.getBlockState(hit.getBlockPos()).is(ModBlocks.CHANNEL_TERMINAL))
              openTranscript(null);
        });
    for (var page : ChannelScreen.Page.values())
      DevBridge.registerScreen(page.name().toLowerCase(Locale.ROOT), mc -> screen(page));
    DevBridge.registerScreen("channels", mc -> new ChannelPickerScreen());
    DevBridge.registerScreen(
        "image",
        mc -> {
          var images = ChannelImages.gallery();
          return images.isEmpty() ? new TranscriptScreen(null) : new ImageScreen(images.getFirst());
        });
    DevBridge.register(
        "dev.transcript",
        10000,
        "{text?,submit?} - open the Channel transcript and optionally post an ordinary"
            + " message",
        (req, mc) ->
            mc.submit(
                () -> {
                  var f = Fields.of(req);
                  var screen =
                      mc.gui.screen() instanceof TranscriptScreen current
                          ? current
                          : new TranscriptScreen("");
                  if (mc.gui.screen() != screen) mc.gui.setScreen(screen);
                  if (f.has("text")) screen.setValue(f.str("text"));
                  if (f.optBool("submit", false)) screen.submit();
                  JsonObject result = new JsonObject();
                  result.addProperty("draft", screen.value());
                  return result;
                }));
    DevBridge.register(
        "dev.artifacts",
        5000,
        "{} -> shared artifact and routine counts",
        (req, mc) ->
            mc.submit(
                () -> {
                  JsonObject out = new JsonObject();
                  out.addProperty("artifacts", ChannelClient.store().artifacts().size());
                  out.addProperty("routines", ChannelClient.store().routines().size());
                  out.addProperty("open", mc.gui.screen() instanceof ArtifactScreen);
                  return out;
                }));
  }
}
