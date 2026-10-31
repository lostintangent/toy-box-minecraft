package dev.toybox.minecraft.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Colors and text measurements shared by the artifact Markdown reader. */
public final class ReaderStyle {
  private ReaderStyle() {}

  public static int ink() {
    return UiStyle.color("paper.text");
  }

  public static int muted() {
    return UiStyle.color("paper.muted");
  }

  public static int boldWidth(Font font, String text) {
    return font.width(Component.literal(text).withStyle(Style.EMPTY.withBold(true)));
  }

  public static int mix(int a, int b, float t) {
    float u = Math.max(0, Math.min(1, t));
    int r = Math.round(((a >> 16) & 255) * (1 - u) + ((b >> 16) & 255) * u);
    int g = Math.round(((a >> 8) & 255) * (1 - u) + ((b >> 8) & 255) * u);
    int bl = Math.round((a & 255) * (1 - u) + (b & 255) * u);
    return 0xff000000 | r << 16 | g << 8 | bl;
  }
}
