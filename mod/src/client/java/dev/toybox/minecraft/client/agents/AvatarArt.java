package dev.toybox.minecraft.client.agents;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Pure, reproducible avatar artwork. The badge uses Toy Box's 24px outline SVG recipe. */
public final class AvatarArt {
  public static final int SKIN_SIZE = 256;
  private static final int SHELL = 0xfffff7e8,
      INK = 0xff25324b,
      JOINT = 0xff35466d,
      MINT = 0xffd0efe1;

  private AvatarArt() {}

  public static String key(String id) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static int variant(String id) {
    return Integer.parseInt(key(id).substring(0, 2), 16);
  }

  public static boolean slim(String id) {
    return (variant(id) & 1) == 1;
  }

  public static int color(String color) {
    return color != null && color.matches("#[0-9a-fA-F]{6}")
        ? 0xff000000 | Integer.parseInt(color.substring(1), 16)
        : 0xff0891b2;
  }

  public static double luminance(int color) {
    double[] rgb = {(color >> 16 & 255) / 255.0, (color >> 8 & 255) / 255.0, (color & 255) / 255.0};
    for (int i = 0; i < 3; i++)
      rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
    return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
  }

  public static int contrast(int background) {
    return luminance(background) > .179 ? 0xff000000 : 0xffffffff;
  }

  public static int onDark(int color) {
    int c = color;
    for (int i = 0; i < 24 && (luminance(c) + .05) / (luminance(INK) + .05) < 4.5; i++)
      c = mix(c, 0xffffffff, .1);
    return c;
  }

  public static int onLight(int color) {
    int c = color;
    for (int i = 0; i < 24 && (luminance(SHELL) + .05) / (luminance(c) + .05) < 4.5; i++)
      c = mix(c, 0xff000000, .1);
    return c;
  }

  private static int mix(int a, int b, double w) {
    int r = (int) ((a >> 16 & 255) * (1 - w) + (b >> 16 & 255) * w),
        g = (int) ((a >> 8 & 255) * (1 - w) + (b >> 8 & 255) * w),
        v = (int) ((a & 255) * (1 - w) + (b & 255) * w);
    return 0xff000000 | r << 16 | g << 8 | v;
  }

  public static BufferedImage portrait(String name, String mark, String color) {
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int identity = color(color);
    g.setColor(new Color(identity, true));
    g.fillOval(0, 0, 64, 64);
    g.setColor(new Color(contrast(identity), true));
    if (mark != null && !mark.isBlank()) {
      try {
        g.translate(64 * .18, 64 * .18);
        g.scale(64 * .64 / 24, 64 * .64 / 24);
        g.setStroke(new BasicStroke(2.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(SvgMark.parse(mark));
      } catch (IllegalArgumentException ignored) {
        /* Invalid paths retain the identity color; no external SVG decoder. */
      }
    } else {
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
      String initial =
          name == null || name.isBlank()
              ? "?"
              : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
      var fm = g.getFontMetrics();
      g.drawString(
          initial, (64 - fm.stringWidth(initial)) / 2, (64 - fm.getHeight()) / 2 + fm.getAscent());
    }
    g.dispose();
    return image;
  }

  public static BufferedImage skin(String id, String name, String mark, String color) {
    BufferedImage image = new BufferedImage(SKIN_SIZE, SKIN_SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.scale(4, 4);
    int identity = color(color), v = variant(id), arm = slim(id) ? 3 : 4;
    cuboid(g, 0, 0, 8, 8, 8, SHELL); // head
    cuboid(g, 16, 16, 8, 12, 4, SHELL); // torso
    cuboid(g, 40, 16, arm, 12, 4, SHELL);
    cuboid(g, 32, 48, arm, 12, 4, SHELL);
    cuboid(g, 0, 16, 4, 12, 4, SHELL);
    cuboid(g, 16, 48, 4, 12, 4, SHELL);
    fill(g, 8, 10, 8, 5, INK);
    fill(g, 9, 8, 6, 1, identity);
    // Head/body shape details are tied to the durable ID, never roster order.
    int eyeY = 11 + (v >> 2 & 1);
    fill(g, 9, eyeY, 2, 2, MINT);
    fill(g, 13, eyeY, 2, 2, MINT);
    if ((v & 4) != 0) fill(g, 11, eyeY + 1, 2, 1, MINT);
    fill(g, 11, 15, 2, 1, identity);
    fill(g, 0, 10, 8, 3, identity);
    fill(g, 16, 10, 8, 3, identity);
    fill(g, 20, 20, 8, 2, identity);
    fill(g, 20, 30, 8, 2, INK);
    fill(g, 32, 20, 8, 2, identity);
    for (int[] origin : new int[][] {{40, 16}, {32, 48}}) {
      fill(g, origin[0] + 4, origin[1] + 4, arm, 2, identity);
      fill(g, origin[0], origin[1] + 9, arm * 2 + 8, 2, JOINT);
      fill(g, origin[0], origin[1] + 14, arm * 2 + 8, 2, INK);
    }
    for (int[] origin : new int[][] {{0, 16}, {16, 48}}) {
      fill(g, origin[0], origin[1] + 8, 16, 2, JOINT);
      fill(g, origin[0], origin[1] + 13, 16, 1, identity);
      fill(g, origin[0], origin[1] + 14, 16, 2, INK);
    }
    // Distinct raised head connectors on the outer layer.
    fill(g, 32, 10, 2, 3, identity);
    fill(g, 54, 10, 2, 3, identity);
    g.dispose();
    Graphics2D badge = image.createGraphics();
    badge.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    badge.drawImage(portrait(name, mark, color), 84, 90, 24, 24, null);
    badge.dispose();
    return image;
  }

  private static void cuboid(Graphics2D g, int u, int v, int w, int h, int d, int color) {
    fill(g, u + d, v, w, d, color);
    fill(g, u + d + w, v, w, d, color);
    fill(g, u, v + d, 2 * (w + d), h, color);
  }

  private static void fill(Graphics2D g, int x, int y, int w, int h, int color) {
    g.setColor(new Color(color, true));
    g.fillRect(x, y, w, h);
  }
}
