package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.toybox.minecraft.block.entity.StatusLampBlockEntity;
import dev.toybox.minecraft.client.agents.PlateLayout;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.hud.Keys;
import dev.toybox.minecraft.client.screens.TaskRows;
import dev.toybox.minecraft.client.ui.Kit;
import dev.toybox.minecraft.client.ui.TextUtil;
import dev.toybox.minecraft.client.ui.UiStyle;
import dev.toybox.minecraft.client.ui.WorldUi;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * The atrium introduces the channel: purpose, members, tasks, and the outstanding public request.
 */
public final class StatusLampRenderer
    extends StationRenderer<StatusLampBlockEntity, StatusLampRenderer.State> {
  public static final String ATRIUM_BINDING = "channel:atrium";

  public static final class State extends StationRenderState {
    boolean visible, waitingArea;
    String title = "", team = "", tasks = "", attention = "";
    List<net.minecraft.util.FormattedCharSequence> purpose = List.of();
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public boolean shouldRenderOffScreen() {
    return true;
  }

  @Override
  protected void extractStation(StatusLampBlockEntity be, State s, float dt) {
    s.waitingArea = "waiting:area".equals(s.binding);
    s.visible = ATRIUM_BINDING.equals(s.binding) || s.waitingArea;
    if (!s.visible) return;
    var state = ChannelClient.store();
    var channel = state.channel();
    var font = Minecraft.getInstance().font;
    if (s.waitingArea) {
      long waiting =
          state.team().stream()
              .filter(
                  a ->
                      state.presence(a.id()).state().equals("waiting")
                          && (state.request() == null
                              || !a.id().equals(state.request().sender().agentId())))
              .count();
      s.title = "WAITING";
      s.attention =
          state.isStale()
              ? "Offline · last known"
              : waiting + " " + (waiting == 1 ? "agent" : "agents");
      PlateLayout.reserve(
          s.blockPos.getX() + .5,
          s.blockPos.getY() + 1.3 - 17 * WorldUi.PX * .8,
          s.blockPos.getZ() + .5,
          110,
          34,
          WorldUi.PX * .8f);
      return;
    }
    s.title = channel == null ? "TOY BOX" : channel.name();
    s.purpose =
        TextUtil.wrap(
            font, channel == null ? "Connect this workshop to a channel." : channel.purpose(), 210);
    s.purpose = s.purpose.subList(0, Math.min(3, s.purpose.size()));
    s.team = state.team().size() + " channel members";
    var rows = TaskRows.flatten(state.tasks());
    long done = rows.stream().filter(r -> r.task().status().equals("done")).count();
    s.tasks =
        done + " / " + rows.size() + " tasks complete · " + state.artifacts().size() + " artifacts";
    s.attention =
        state.isStale()
            ? "Offline · last known state"
            : state.request() == null
                ? "Channel transcript: " + Keys.label(Keys.transcript)
                : "1 reply requested · " + Keys.label(Keys.requests);
    PlateLayout.reserve(
        s.blockPos.getX() + .5,
        s.blockPos.getY() + 3.55 - 57 * WorldUi.PX * .9,
        s.blockPos.getZ() + .5,
        244,
        114,
        WorldUi.PX * .9f);
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    if (!s.visible) return;
    int light = WorldUi.uiLight();
    ps.pushPose();
    if (s.waitingArea) {
      WorldUi.billboard(ps, camera, .5, 1.3, .5);
      ps.scale(.8f, .8f, .8f);
      WorldUi.submitNineSlice(
          ps, c, WorldUi.Layer.SOLID, Kit.PANEL_PAPER, -55, -17, 110, 34, 0xffffffff, light);
      ps.translate(0, 0, .5f);
      var font = Minecraft.getInstance().font;
      WorldUi.submitText(ps, c, s.title, -font.width(s.title) / 2f, -10, UiStyle.CLAY_DARK, light);
      WorldUi.submitText(ps, c, s.attention, -font.width(s.attention) / 2f, 3, UiStyle.INK, light);
      ps.popPose();
      return;
    }
    WorldUi.billboard(ps, camera, .5, 3.55, .5);
    ps.scale(.9f, .9f, .9f);
    WorldUi.submitNineSlice(
        ps, c, WorldUi.Layer.SOLID, Kit.PANEL_PAPER, -122, -57, 244, 114, 0xFFFFFFFF, light);
    ps.translate(0, 0, .5f);
    var font = Minecraft.getInstance().font;
    WorldUi.submitText(
        ps, c, TextUtil.ellipsize(font, s.title, 220), -110, -45, UiStyle.INK, light);
    int y = -30;
    for (var line : s.purpose) {
      WorldUi.submitText(ps, c, line, -110, y, UiStyle.INK, light);
      y += 11;
    }
    WorldUi.submitText(ps, c, s.team, -110, 9, UiStyle.INK, light);
    WorldUi.submitText(ps, c, s.tasks, -110, 21, UiStyle.INK, light);
    WorldUi.submitText(ps, c, s.attention, -110, 38, UiStyle.CLAY_DARK, light);
    ps.popPose();
  }
}
