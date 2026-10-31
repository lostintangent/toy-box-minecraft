package dev.toybox.minecraft.client.hud;

import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.screens.Screens;
import dev.toybox.minecraft.client.screens.TaskRows;
import dev.toybox.minecraft.client.ui.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ChannelBar implements HudElement {
  @Override
  public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
    var s = ChannelClient.store();
    var c = s.channel();
    if (Minecraft.getInstance().player == null || c == null) return;
    var font = Minecraft.getInstance().font;
    if (Minecraft.getInstance().gui.screen() == null && Screens.discussion() != null) {
      String hint = Keys.label(Keys.discuss) + " · Discuss in channel";
      g.text(
          font,
          hint,
          (g.guiWidth() - font.width(hint)) / 2,
          g.guiHeight() / 2 + 18,
          UiStyle.CREAM,
          true);
    }
    int w = Math.min(320, Math.max(120, (int) ((g.guiWidth() - 18) * .58))), x = 6, y = 6;
    Panels.sprite(g, Kit.TOOLTIP, x, y, w, 46);
    String open = Keys.label(Keys.transcript) + " · Channel";
    int openWidth = font.width(open);
    g.text(font, open, x + w - 8 - openWidth, y + 6, UiStyle.withAlpha(UiStyle.CREAM, 170), false);
    g.text(
        font,
        TextUtil.ellipsize(font, c.name() + (s.isStale() ? " · offline" : ""), w - 24 - openWidth),
        x + 8,
        y + 6,
        UiStyle.CREAM,
        false);
    var tasks = TaskRows.flatten(c.tasks());
    long done = tasks.stream().filter(r -> r.task().status().equals("done")).count();
    long working =
        s.team().stream().filter(a -> s.presence(a.id()).state().equals("working")).count();
    long waiting =
        s.team().stream().filter(a -> s.presence(a.id()).state().equals("waiting")).count();
    int agents = s.team().size();
    String summary =
        agents + (agents == 1 ? " agent" : " agents") + " · " + working + " working · " + waiting
            + " waiting";
    g.text(font, TextUtil.ellipsize(font, summary, w - 16), x + 8, y + 18, UiStyle.CREAM, false);
    String progress =
        tasks.isEmpty() ? "No tasks published" : done + " / " + tasks.size() + " tasks done";
    if (s.request() != null) progress += " · " + Keys.label(Keys.requests) + " to read request";
    g.text(
        font,
        TextUtil.ellipsize(font, progress, w - 16),
        x + 8,
        y + 30,
        s.request() != null ? UiStyle.CLAY : UiStyle.CREAM,
        false);
  }
}
