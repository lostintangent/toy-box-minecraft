package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.channel.ChannelModel.Message;
import dev.toybox.minecraft.client.ui.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A request is the lead's public message. Replying is ordinary channel conversation. */
public final class RequestScreen extends ChannelScreen {
  private Message request;
  private KitButton reply;
  private List<KitButton> attachments = List.of();

  public RequestScreen() {
    super(Page.REQUEST, "Request from the lead");
  }

  @Override
  protected void init() {
    super.init();
    reply = button("Reply in channel", 122, () -> Screens.openTranscript(""));
    var current = request = ChannelClient.store().request();
    attachments =
        current == null
            ? List.of()
            : IntStream.range(0, current.attachments().size())
                .mapToObj(
                    i ->
                        button(
                            "Open attachment " + (i + 1),
                            125,
                            () -> ChannelClient.openAttachment(current, i)))
                .toList();
  }

  @Override
  public void tick() {
    if (!Objects.equals(ChannelClient.store().request(), request)) rebuildWidgets();
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    reply.visible = false;
    int py = bodyY + 8 - (int) scroll;
    g.enableScissor(x + 10, bodyY, x + w - 10, bodyY + bodyH);
    if (request == null)
      py =
          text(
              g,
              "No request is waiting for your reply.",
              x + 16,
              py,
              w - 32,
              UiStyle.color("paper.muted"));
    else {
      py =
          text(g, "Lead · request #" + request.sequence(), x + 16, py, w - 32, UiStyle.CLAY_DARK)
              + 12;
      py = text(g, request.content(), x + 16, py, w - 32, UiStyle.color("paper.text")) + 16;
      for (var attachment : attachments) {
        attachment.setPosition(x + 16, py);
        attachment.visible = py >= bodyY && py + 20 < bodyY + bodyH;
        py += 26;
      }
      py =
          text(
                  g,
                  "Your reply is posted publicly to the channel and reaches the lead. Reading this"
                      + " request does not acknowledge it.",
                  x + 16,
                  py,
                  w - 32,
                  UiStyle.color("paper.muted"))
              + 15;
      reply.setPosition(x + 16, py);
      reply.visible = py >= bodyY && py + 20 < bodyY + bodyH;
      reply.active = !ChannelClient.store().isStale();
      py += 28;
    }
    contentHeight = py - bodyY + (int) scroll;
    g.disableScissor();
    clampScroll();
  }
}
