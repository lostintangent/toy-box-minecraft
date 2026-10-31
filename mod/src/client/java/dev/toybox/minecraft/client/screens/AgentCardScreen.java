package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.agents.AgentManager;
import dev.toybox.minecraft.client.agents.AgentSkins;
import dev.toybox.minecraft.client.agents.AvatarArt;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.KitButton;
import dev.toybox.minecraft.client.ui.UiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Public member profile: identity, native presence, referenced messages and channel actions. */
public final class AgentCardScreen extends ChannelScreen {
  private final String agentId;
  private KitButton message, open, reply;

  public AgentCardScreen(String id) {
    super(Page.TEAM, "Channel member");
    agentId = id;
  }

  public String agentId() {
    return agentId;
  }

  @Override
  protected void init() {
    super.init();
    message =
        button(
            "Message",
            80,
            () -> Screens.openTranscript("@" + ChannelClient.mention(agentId) + " "));
    open = button("Open channel", 100, ChannelClient::openChannel);
    reply = button("Reply requested", Math.min(112, w - 216), () -> Screens.open(Page.REQUEST));
    message.setPosition(x + 12, bodyY + bodyH - 23);
    open.setPosition(x + 98, bodyY + bodyH - 23);
    reply.setPosition(x + 204, bodyY + bodyH - 23);
    scrollHeight = bodyH - 28;
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    var state = ChannelClient.store();
    var agent = state.agent(agentId);
    message.visible = open.visible = agent != null;
    message.active = !state.isStale();
    reply.visible =
        agent != null
            && state.request() != null
            && agentId.equals(state.request().sender().agentId());
    reply.active = !state.isStale();
    if (agent == null) {
      text(g, "This member has left the channel.", x + 12, bodyY, w - 24, UiStyle.INK);
      return;
    }
    int bx = x + 12, by = bodyY - (int) scroll, available = w - 24;
    g.enableScissor(x + 6, bodyY, x + w - 6, bodyY + scrollHeight);
    portrait(g, agentId, bx, by, 36);
    g.text(
        font, agent.name(), bx + 46, by + 3, AvatarArt.onLight(AgentSkins.color(agentId)), false);
    var entity = AgentManager.get().entity(agentId);
    String presence =
        entity == null ? state.presence(agentId).state() : entity.view().activityLine();
    int cy = text(g, presence, bx + 46, by + 17, available - 46, UiStyle.INK);
    cy = Math.max(cy, by + 45);
    if (agent.role() != null && !agent.role().isBlank())
      cy = text(g, agent.role(), bx, cy, available, UiStyle.INK) + 12;
    if (agent.model() != null)
      cy =
          text(
                  g,
                  agent.model().provider() + " · " + agent.model().name(),
                  bx,
                  cy,
                  available,
                  UiStyle.color("paper.muted"))
              + 12;
    cy = text(g, "Recent public messages", bx, cy, available, UiStyle.INK) + 6;
    var messages =
        state.messages().stream()
            .filter(m -> agentId.equals(m.sender().agentId()) && !m.isSystem())
            .toList();
    if (messages.isEmpty())
      cy = text(g, "No public messages yet.", bx, cy, available, UiStyle.color("paper.muted"));
    for (var message : messages.subList(Math.max(0, messages.size() - 5), messages.size())) {
      cy =
          text(
                  g,
                  "#" + message.sequence() + " · " + message.content(),
                  bx,
                  cy,
                  available,
                  UiStyle.INK)
              + 10;
    }
    contentHeight = cy - by;
    g.disableScissor();
    clampScroll();
  }
}
