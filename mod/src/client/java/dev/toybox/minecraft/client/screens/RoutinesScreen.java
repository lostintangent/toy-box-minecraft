package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.UiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Routine definitions only; no invented next-run time or client-time-zone conversion. */
public final class RoutinesScreen extends ChannelScreen {
  public RoutinesScreen() {
    super(Page.ROUTINES, "Channel routines");
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    int py = bodyY - (int) scroll, start = py;
    g.enableScissor(x + 8, bodyY, x + w - 8, bodyY + bodyH);
    var routines = ChannelClient.store().routines();
    if (routines.isEmpty())
      py = text(g, "No routines scheduled for this channel.", x + 12, py, w - 24, UiStyle.INK);
    for (var routine : routines) {
      py = text(g, routine.title(), x + 12, py, w - 24, UiStyle.INK) + 4;
      py =
          text(
                  g,
                  "Schedule: " + routine.schedule() + " · server local time",
                  x + 12,
                  py,
                  w - 24,
                  UiStyle.color("paper.muted"))
              + 6;
      py = text(g, routine.prompt(), x + 12, py, w - 24, UiStyle.INK) + 18;
    }
    contentHeight = py - start;
    clampScroll();
    g.disableScissor();
  }
}
