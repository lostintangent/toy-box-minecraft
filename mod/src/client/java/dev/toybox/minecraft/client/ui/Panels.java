package dev.toybox.minecraft.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI drawing with the kit (screens and HUD). All coordinates are GUI pixels; sprites are authored
 * at 1 texel = 1 GUI pixel, nine-sliced by vanilla where they ship slice metadata.
 */
public final class Panels {
  private Panels() {}

  public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h) {
    g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
  }

  public static void sprite(
      GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, int argb) {
    g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, argb);
  }

  /** Matte paper window (soft shadow included in the sprite). */
  public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
    sprite(g, Kit.PANEL_PAPER, x, y, w, h);
  }

  /** Title strip with clay underline (14 px tall), with the title in ink. */
  public static void header(GuiGraphicsExtractor g, Font font, String title, int x, int y, int w) {
    sprite(g, Kit.HEADER, x, y, w, 14);
    Kit.Padding p = Kit.padding("header");
    text(
        g,
        font,
        TextUtil.ellipsize(font, title, w - p.left() - p.right()),
        x + p.left(),
        y + p.top() + 1,
        UiStyle.color("paper.text"));
  }

  /** Flat text, no drop shadow (paper UI never uses shadows). */
  public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int argb) {
    g.text(font, s, x, y, argb, false);
  }

  /** 7x7 status dot (11x11 halo when {@code halo}), top-left at x,y. */
  public static void dot(GuiGraphicsExtractor g, String status, int x, int y, boolean halo) {
    int s = halo ? 11 : 7;
    sprite(g, Kit.dot(status, halo), x, y, s, s);
  }
}
