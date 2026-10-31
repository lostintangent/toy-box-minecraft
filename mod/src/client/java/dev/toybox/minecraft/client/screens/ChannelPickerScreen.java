package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelConfig;
import dev.toybox.minecraft.client.channel.ChannelModel.Channel;
import dev.toybox.minecraft.client.ui.*;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

/** An explicit catalog read; choosing a channel replaces only this client's observation. */
public final class ChannelPickerScreen extends ChannelScreen {
  private EditBox address;
  private KitButton refresh;
  private CompletableFuture<List<Channel>> pending;
  private List<Channel> channels = List.of();
  private String catalogAddress, notice = "";
  private URI catalogServer;
  private int fieldY, listY;

  public ChannelPickerScreen() {
    super(null, "Choose a channel");
  }

  @Override
  protected void setInitialFocus() {
    setInitialFocus(address);
  }

  @Override
  protected void init() {
    super.init();
    fieldY = bodyY + 14;
    String value =
        address == null ? ChannelClient.config().server().toString() : address.getValue();
    address =
        addRenderableWidget(
            new EditBox(font, x + 18, fieldY + 5, w - 122, 9, Component.literal("Toy Box server")));
    address.setMaxLength(2048);
    address.setBordered(false);
    address.setTextShadow(false);
    address.setTextColor(UiStyle.INK);
    address.setInvertHighlightedTextColor(false);
    address.setHint(Component.literal(ChannelConfig.DEFAULT_SERVER));
    address.setValue(value);
    refresh = button("Refresh", 76, this::refresh);
    refresh.setPosition(x + w - 90, fieldY);
    if (catalogAddress == null && pending == null) refresh();
  }

  private void refresh() {
    if (pending != null) return;
    channels = List.of();
    notice = "Loading channels…";
    scroll = 0;
    try {
      String entered = address.getValue();
      URI server = ChannelConfig.serverUri(entered);
      var request = pending = ChannelClient.catalog(server);
      request.whenComplete(
          (loaded, error) ->
              minecraft.execute(
                  () -> {
                    if (minecraft.gui.screen() != this || pending != request) return;
                    pending = null;
                    if (!entered.equals(address.getValue())) {
                      notice = "Load channels from this server.";
                    } else if (error != null) {
                      notice = (error.getCause() == null ? error : error.getCause()).getMessage();
                    } else {
                      channels = loaded;
                      catalogServer = server;
                      catalogAddress = entered;
                      notice =
                          channels.isEmpty()
                              ? "No channels yet. Create one in Toy Box."
                              : "Choose a channel to enter its workshop.";
                    }
                  }));
    } catch (Exception error) {
      notice = error.getMessage();
    }
  }

  private void select(Channel channel) {
    try {
      ChannelClient.connect(catalogServer, channel.id());
      minecraft.gui.setScreen(new TranscriptScreen(""));
    } catch (Exception error) {
      notice = "Could not save this connection: " + error.getMessage();
    }
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    g.text(font, "Toy Box server", x + 14, bodyY, UiStyle.INK, false);
    Panels.sprite(
        g,
        address.isFocused() ? Kit.TEXT_FIELD_FOCUSED : Kit.TEXT_FIELD,
        x + 14,
        fieldY,
        w - 114,
        18);
    refresh.setMessage(Component.literal(pending == null ? "Refresh" : "Loading…"));
    refresh.active = pending == null;
    int noticeEnd = text(g, notice, x + 14, fieldY + 28, w - 28, UiStyle.color("paper.muted"));
    listY = noticeEnd + 12;
    scrollHeight = Math.max(0, bodyY + bodyH - listY);
    var visible = address.getValue().equals(catalogAddress) ? channels : List.<Channel>of();
    contentHeight = visible.size() * 48;
    clampScroll();
    g.enableScissor(x + 10, listY, x + w - 10, listY + scrollHeight);
    int rowY = listY - (int) scroll;
    for (Channel channel : visible) {
      if (rowY + 44 >= listY && rowY < listY + scrollHeight) {
        boolean selected =
            catalogServer.equals(ChannelClient.config().server())
                && channel.id().equals(ChannelClient.config().channelId());
        boolean hover = mx >= x + 12 && mx < x + w - 12 && my >= rowY && my < rowY + 43;
        if (hover || selected)
          g.fill(x + 12, rowY, x + w - 12, rowY + 43, UiStyle.color("paper.inset"));
        g.text(
            font,
            TextUtil.ellipsize(font, channel.name(), w - 50),
            x + 20,
            rowY + 7,
            UiStyle.INK,
            false);
        String attention =
            channel.hasPendingRequest()
                ? "Reply requested"
                : channel.hasUnreadCompletion()
                    ? "Tasks completed"
                    : channel.seenThrough() < channel.latestSequence()
                        ? "Unread messages"
                        : "Up to date";
        g.text(
            font,
            attention + (selected ? " · current channel" : ""),
            x + 20,
            rowY + 24,
            channel.hasPendingRequest()
                ? UiStyle.color("paper.link")
                : UiStyle.color("paper.muted"),
            false);
        int top = Math.max(listY, rowY), bottom = Math.min(listY + scrollHeight, rowY + 43);
        if (bottom > top)
          hits.add(new Hit(x + 12, top, w - 24, bottom - top, () -> select(channel)));
      }
      rowY += 48;
    }
    g.disableScissor();
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (address.isFocused() && event.isConfirmation()) {
      refresh();
      return true;
    }
    return super.keyPressed(event);
  }
}
