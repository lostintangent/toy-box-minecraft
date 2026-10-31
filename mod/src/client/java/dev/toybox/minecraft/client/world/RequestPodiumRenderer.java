package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.toybox.minecraft.block.entity.RequestPodiumBlockEntity;
import dev.toybox.minecraft.client.agents.PlateLayout;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;

public final class RequestPodiumRenderer
    extends StationRenderer<RequestPodiumBlockEntity, RequestPodiumRenderer.State> {
  public static final class State extends StationRenderState {
    boolean show, stale;
    List<FormattedCharSequence> lines = List.of();
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  protected void extractStation(RequestPodiumBlockEntity be, State s, float dt) {
    var request = ChannelClient.store().request();
    s.show = request != null;
    s.stale = ChannelClient.store().isStale();
    s.lines =
        request == null
            ? List.of()
            : TextUtil.wrap(
                    Minecraft.getInstance().font,
                    dev.toybox.minecraft.client.channel.ChannelText.message(
                        request, ChannelClient.store().state()),
                    164)
                .stream()
                .limit(5)
                .toList();
    if (s.show)
      PlateLayout.reserve(
          s.blockPos.getX() + .5,
          s.blockPos.getY() + 1.6,
          s.blockPos.getZ() + .5,
          180,
          30 + s.lines.size() * 10,
          WorldUi.PX * .8f);
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    if (!s.show) return;
    int height = 30 + s.lines.size() * 10, light = WorldUi.uiLight();
    ps.pushPose();
    double ox = s.blockPos.getX() - camera.pos.x,
        oy = s.blockPos.getY() - camera.pos.y,
        oz = s.blockPos.getZ() - camera.pos.z;
    WorldUi.billboard(ps, camera, .5, 1.6, .5, .8f, .08f, ox, oy, oz);
    WorldUi.submitNineSlice(
        ps, c, WorldUi.Layer.SOLID, Kit.BUBBLE, -90, -height, 180, height, 0xffffffff, light);
    ps.translate(0, 0, .5f);
    WorldUi.submitText(
        ps,
        c,
        s.stale
            ? "Offline · last request"
            : "Reply requested · "
                + dev.toybox.minecraft.client.hud.Keys.label(
                    dev.toybox.minecraft.client.hud.Keys.requests),
        -82,
        -height + 7,
        UiStyle.CLAY_DARK,
        light);
    int y = -height + 22;
    for (var line : s.lines) {
      WorldUi.submitText(ps, c, line, -82, y, UiStyle.INK, light);
      y += 10;
    }
    ps.popPose();
  }
}
