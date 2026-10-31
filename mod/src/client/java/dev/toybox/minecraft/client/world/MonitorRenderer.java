package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.toybox.minecraft.block.entity.MonitorBlockEntity;
import dev.toybox.minecraft.client.agents.AgentManager;
import dev.toybox.minecraft.client.agents.AgentSkins;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel.Message;
import dev.toybox.minecraft.client.channel.ChannelText;
import dev.toybox.minecraft.client.ui.TextUtil;
import dev.toybox.minecraft.client.ui.UiStyle;
import dev.toybox.minecraft.client.ui.WorldUi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;

/** The connected panel is a small window onto the channel transcript. */
public final class MonitorRenderer
    extends StationRenderer<MonitorBlockEntity, MonitorRenderer.State> {
  private static final int PIXELS_PER_BLOCK = 96;

  private record Line(FormattedCharSequence text, int color) {}

  public static final class State extends StationRenderState {
    String name = "Available desk", status = "", key = "";
    int color = UiStyle.TEAL;
    boolean stale;
    List<Line> lines = List.of();
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
  protected void extractStation(MonitorBlockEntity be, State s, float dt) {
    if (!s.panelOrigin) return;
    var state = ChannelClient.store();
    String id = member(s.binding);
    var member = id == null ? null : state.agent(id);
    s.stale = state.isStale();
    String key = state.revision() + ":" + id + ":" + s.panelWidth + ":" + s.panelHeight;
    if (key.equals(s.key)) return;
    s.key = key;
    s.name = member == null ? "Available desk" : member.name();
    s.color = member == null ? UiStyle.TEAL : AgentSkins.nameColor(id);
    s.status =
        member == null
            ? "A channel member's place"
            : state.presence(id).state()
                + (state.presence(id).text() == null ? "" : " · " + state.presence(id).text());
    var rows = new ArrayList<Line>();
    int width = s.panelWidth * PIXELS_PER_BLOCK - 26;
    Long reference = null;
    if (member != null && member.status() != null && !state.presence(id).state().equals("idle")) {
      reference = member.status().workingOn();
      String label = "Working on";
      if (reference == null) {
        reference = member.status().lookingAt();
        label = "Reading";
      }
      if (reference != null) {
        long sequence = reference;
        Message message =
            state.messages().stream()
                .filter(m -> m.sequence() == sequence)
                .findFirst()
                .orElse(null);
        if (message == null && state.request() != null && state.request().sequence() == sequence)
          message = state.request();
        add(rows, label + " #" + sequence, width, 1, "monitor_dark.tool");
        add(
            rows,
            message == null
                ? "Open Toy Box to read this earlier message."
                : ChannelText.sender(message.sender(), state.state())
                    + ": "
                    + ChannelText.message(message, state.state()),
            width,
            3,
            "monitor_dark.text");
        rows.add(new Line(FormattedCharSequence.EMPTY, 0));
      }
    }
    if (member != null) {
      add(rows, "Recent public messages", width, 1, "monitor_dark.result");
      var messages = state.messages();
      int shown = 0;
      for (int i = messages.size() - 1; i >= 0 && shown < 2; i--) {
        Message message = messages.get(i);
        if (message.isSystem()
            || !id.equals(message.sender().agentId())
            || reference != null && message.sequence() == reference) continue;
        add(
            rows,
            "#" + message.sequence() + " · " + ChannelText.message(message, state.state()),
            width,
            reference == null ? 4 : 2,
            "monitor_dark.text");
        shown++;
      }
      if (shown == 0) add(rows, "No other public messages yet.", width, 2, "monitor_dark.muted");
    } else add(rows, "Join the channel to fill this desk.", width, 3, "monitor_dark.muted");
    int count = Math.max(1, (s.panelHeight * PIXELS_PER_BLOCK - 70) / 12);
    s.lines = List.copyOf(rows.subList(0, Math.min(count, rows.size())));
  }

  private static void add(List<Line> lines, String text, int width, int limit, String color) {
    var font = Minecraft.getInstance().font;
    var wrapped = TextUtil.wrapPlain(font, text.replaceAll("\\s+", " ").trim(), width);
    for (int i = 0; i < Math.min(limit, wrapped.size()); i++) {
      String line = wrapped.get(i);
      if (i == limit - 1 && wrapped.size() > limit)
        line = TextUtil.ellipsize(font, line + "…", width);
      lines.add(
          new Line(
              net.minecraft.network.chat.Component.literal(line).getVisualOrderText(),
              UiStyle.color(color)));
    }
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    if (!s.panelOrigin) return;
    int light = WorldUi.uiLight(),
        width = s.panelWidth * PIXELS_PER_BLOCK,
        height = s.panelHeight * PIXELS_PER_BLOCK;
    ps.pushPose();
    toFace(ps, s.facing, 12f / 16f - .003f, PIXELS_PER_BLOCK);
    ps.translate(0, -(s.panelHeight - 1) * PIXELS_PER_BLOCK, 0);
    ps.translate(0, 0, -.005f);
    var font = Minecraft.getInstance().font;
    WorldUi.submitText(ps, c, TextUtil.ellipsize(font, s.name, width - 26), 13, 14, s.color, light);
    WorldUi.submitText(
        ps,
        c,
        TextUtil.ellipsize(font, s.status, width - 26),
        13,
        27,
        UiStyle.color("monitor_dark.muted"),
        light);
    int y = 44;
    for (var line : s.lines) {
      WorldUi.submitText(ps, c, line.text(), 13, y, line.color(), light);
      y += 12;
    }
    WorldUi.submitText(
        ps,
        c,
        TextUtil.ellipsize(
            font,
            s.stale ? "Offline · last known messages" : "Public messages · click to read",
            width - 26),
        13,
        height - 20,
        s.stale ? UiStyle.CLAY : UiStyle.color("monitor_dark.result"),
        light);
    ps.popPose();
  }

  static String member(String binding) {
    if (ChannelClient.store().agent(binding) != null) return binding;
    return AgentManager.get().agentAtDesk(binding);
  }
}
