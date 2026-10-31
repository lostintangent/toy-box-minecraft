package dev.toybox.minecraft.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Text helpers shared by screens and in-world renderers: ellipsis, wrapping, scrolling. */
public final class TextUtil {
  public static final String ELLIPSIS = "…";

  private TextUtil() {}

  /** {@code s} cut to {@code maxWidth} px with a trailing ellipsis when it does not fit. */
  public static String ellipsize(Font font, String s, int maxWidth) {
    if (s == null) {
      return "";
    }
    if (font.width(s) <= maxWidth) {
      return s;
    }
    int ew = font.width(ELLIPSIS);
    if (maxWidth <= ew) {
      return "";
    }
    return font.plainSubstrByWidth(s, maxWidth - ew).stripTrailing() + ELLIPSIS;
  }

  /** Word-wrap plain text (respecting newlines) into lines of at most {@code width} px. */
  public static List<FormattedCharSequence> wrap(Font font, String s, int width) {
    return font.split(FormattedText.of(s == null ? "" : s), Math.max(1, width));
  }

  /** Word-wrap into plain strings (useful for caching/measuring). */
  public static List<String> wrapPlain(Font font, String s, int width) {
    List<String> out = new ArrayList<>();
    for (String para : (s == null ? "" : s).split("\n", -1)) {
      if (para.isEmpty()) {
        out.add("");
        continue;
      }
      font.getSplitter()
          .splitLines(para, Math.max(1, width), net.minecraft.network.chat.Style.EMPTY)
          .forEach(ft -> out.add(ft.getString()));
    }
    return out;
  }
}
