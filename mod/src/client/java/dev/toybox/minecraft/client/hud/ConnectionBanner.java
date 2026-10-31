package dev.toybox.minecraft.client.hud;

import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Connection trouble only: connecting, retrying, a deleted channel, or none chosen yet. */
public final class ConnectionBanner implements HudElement {
  @Override
  public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
    var link = ChannelClient.connection();
    if (Minecraft.getInstance().player == null || link.connected()) return;
    var font = Minecraft.getInstance().font;
    String title =
        switch (link.phase()) {
          case DELETED -> "Channel deleted";
          case DISABLED -> "Choose a Toy Box channel";
          default -> "Toy Box · connecting";
        };
    String detail = link.error();
    int channelWidth =
        ChannelClient.store().hasData()
            ? Math.min(320, Math.max(120, (int) ((g.guiWidth() - 18) * .58)))
            : 0;
    int w = Math.min(300, g.guiWidth() - channelWidth - 18),
        h = detail == null ? 22 : 36,
        x = g.guiWidth() - w - 6;
    Panels.sprite(g, Kit.TOOLTIP, x, 6, w, h);
    g.text(font, TextUtil.ellipsize(font, title, w - 16), x + 8, 13, UiStyle.CREAM, false);
    if (detail != null)
      g.text(font, TextUtil.ellipsize(font, detail, w - 16), x + 8, 25, UiStyle.CLAY, false);
  }
}
