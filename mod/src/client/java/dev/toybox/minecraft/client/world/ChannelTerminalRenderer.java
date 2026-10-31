package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.toybox.minecraft.block.entity.ChannelTerminalBlockEntity;
import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.ui.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;

public final class ChannelTerminalRenderer
    extends StationRenderer<ChannelTerminalBlockEntity, ChannelTerminalRenderer.State> {
  public static final class State extends StationRenderState {
    String title = "Channel";
    List<FormattedCharSequence> lines = List.of();
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  protected void extractStation(ChannelTerminalBlockEntity be, State s, float dt) {
    var snap = ChannelClient.store().state();
    s.title = ChannelClient.store().isStale() ? "Channel · offline" : "Channel transcript";
    String content =
        snap == null
            ? "Connect your Toy Box channel"
            : snap.messages().isEmpty()
                ? "No messages yet"
                : ChannelText.message(snap.messages().getLast(), snap);
    s.lines = TextUtil.wrap(Minecraft.getInstance().font, content, 87).stream().limit(3).toList();
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    int light = WorldUi.uiLight();
    ps.pushPose();
    ps.translate(.5f, .5f, .5f);
    ps.rotateDegrees(Axis.YP, -modelRotation(s.facing));
    ps.translate(-.5f, -.5f, -.5f);
    ps.translate(8 / 16f, 9 / 16f, 11 / 16f);
    ps.rotateDegrees(Axis.XP, 22.5f);
    ps.translate(-8 / 16f, -9 / 16f, -11 / 16f);
    ps.translate(14 / 16f, 17 / 16f, 9.5f / 16f - .0025f);
    ps.scale(-1 / 128f, -1 / 128f, 1);
    WorldUi.submitNineSlice(
        ps, c, WorldUi.Layer.SOLID, Kit.PANEL_PAPER, 0, 0, 96, 56, 0xffffffff, light);
    WorldUi.submitText(ps, c, s.title, 4, 4, UiStyle.CLAY_DARK, light);
    int y = 17;
    for (var line : s.lines) {
      WorldUi.submitText(ps, c, line, 4, y, UiStyle.INK, light);
      y += 10;
    }
    WorldUi.submitText(ps, c, "Open to read or reply", 4, 46, UiStyle.color("paper.muted"), light);
    ps.popPose();
  }
}
