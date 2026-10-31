package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.agents.AgentSkins;
import dev.toybox.minecraft.client.agents.AgentView;
import dev.toybox.minecraft.client.agents.AvatarArt;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.TextUtil;
import dev.toybox.minecraft.client.ui.UiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The public channel roster, with the same identity and presence as the workshop agents. */
public final class TeamScreen extends ChannelScreen {
  public TeamScreen() {
    super(Page.TEAM, "Channel team");
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    var state = ChannelClient.store();
    var team = state.team();
    int py = bodyY - (int) scroll, start = py;
    g.enableScissor(x + 8, bodyY, x + w - 8, bodyY + bodyH);
    py = text(g, "Team members · " + team.size(), x + 12, py, w - 24, UiStyle.INK) + 9;
    if (team.isEmpty())
      py =
          text(
              g,
              "Connect a channel to meet its team.",
              x + 12,
              py,
              w - 24,
              UiStyle.color("paper.muted"));
    for (var agent : team) {
      var view = new AgentView(agent.id());
      view.update(agent, state.presence(agent.id()), state.state(), state.isStale());
      int rowY = py, left = x + 12, textX = left + 44, textWidth = w - 80;
      boolean hovered =
          mx >= left
              && mx < x + w - 12
              && my >= rowY
              && my < rowY + 77
              && my >= bodyY
              && my < bodyY + bodyH;
      if (hovered) g.fill(left - 3, rowY - 3, x + w - 12, rowY + 77, UiStyle.color("paper.inset"));
      portrait(g, agent.id(), left, rowY, 34);
      String name = agent.name();
      g.text(
          font,
          TextUtil.ellipsize(font, name, textWidth),
          textX,
          rowY,
          AvatarArt.onLight(AgentSkins.color(agent.id())),
          false);
      String role =
          agent.role() == null || agent.role().isBlank() ? "Channel member" : agent.role();
      g.text(
          font,
          TextUtil.ellipsize(font, role, textWidth),
          textX,
          rowY + 13,
          UiStyle.color("paper.muted"),
          false);
      String status =
          switch (view.state) {
            case "working" -> "Working";
            case "waiting" -> "Waiting";
            default -> "Idle";
          };
      if (view.awaitingUser) status += " · reply requested";
      g.fill(textX, rowY + 30, textX + 4, rowY + 34, UiStyle.status(view.state));
      g.text(
          font,
          TextUtil.ellipsize(font, status, textWidth - 9),
          textX + 9,
          rowY + 28,
          UiStyle.INK,
          false);
      String activityLine = view.activityLine();
      var activity =
          TextUtil.wrapPlain(
              font, activityLine.equalsIgnoreCase(status) ? "" : activityLine, textWidth);
      for (int i = 0; i < Math.min(2, activity.size()); i++) {
        String line = activity.get(i);
        if (i == 1 && activity.size() > 2) line = TextUtil.ellipsize(font, line + "…", textWidth);
        g.text(font, line, textX, rowY + 42 + i * 11, UiStyle.color("paper.muted"), false);
      }
      g.text(font, "View member details →", textX, rowY + 66, UiStyle.color("paper.link"), false);
      int hitY = Math.max(bodyY, rowY - 3), hitBottom = Math.min(bodyY + bodyH, rowY + 77);
      if (hitBottom > hitY)
        hits.add(
            new Hit(
                left - 3,
                hitY,
                w - 21,
                hitBottom - hitY,
                () -> Minecraft.getInstance().gui.setScreen(new AgentCardScreen(agent.id()))));
      py += 86;
    }
    contentHeight = py - start;
    clampScroll();
    g.disableScissor();
  }
}
