package dev.toybox.minecraft.client.agents;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** SVG path-data geometry only: no markup, URLs, fonts, scripts or external resources. */
final class SvgMark {
  private static final Pattern TOKEN =
      Pattern.compile(
          "[MmZzLlHhVvCcSsQqTtAa]|[-+]?(?:[0-9]*\\.[0-9]+|[0-9]+\\.?[0-9]*)(?:[eE][-+]?[0-9]+)?");
  private final List<String> tokens = new ArrayList<>();
  private int i;
  private double x, y, startX, startY, controlX, controlY;
  private char last;
  private final Path2D.Double path = new Path2D.Double();

  static Path2D parse(String data) {
    if (data == null || data.length() > 2000)
      throw new IllegalArgumentException("Invalid avatar path");
    return new SvgMark(data).read();
  }

  private SvgMark(String data) {
    var matcher = TOKEN.matcher(data);
    int end = 0;
    while (matcher.find()) {
      if (!data.substring(end, matcher.start()).matches("[\\s,]*"))
        throw new IllegalArgumentException("Invalid SVG path");
      tokens.add(matcher.group());
      end = matcher.end();
    }
    if (!data.substring(end).matches("[\\s,]*"))
      throw new IllegalArgumentException("Invalid SVG path");
  }

  private Path2D read() {
    char command = 0;
    while (i < tokens.size()) {
      if (letter(tokens.get(i))) command = tokens.get(i++).charAt(0);
      if (command == 0) throw new IllegalArgumentException("Missing SVG command");
      char c = Character.toUpperCase(command);
      boolean relative = Character.isLowerCase(command);
      double ox = relative ? x : 0, oy = relative ? y : 0;
      if (path.getCurrentPoint() == null && c != 'M')
        throw new IllegalArgumentException("SVG path must begin with moveto");
      switch (c) {
        case 'M', 'L', 'T' -> {
          double nx = number() + ox, ny = number() + oy;
          if (c == 'M') {
            path.moveTo(nx, ny);
            startX = nx;
            startY = ny;
          } else if (c == 'T') {
            double cx = last == 'Q' || last == 'T' ? 2 * x - controlX : x;
            double cy = last == 'Q' || last == 'T' ? 2 * y - controlY : y;
            path.quadTo(cx, cy, nx, ny);
            controlX = cx;
            controlY = cy;
          } else path.lineTo(nx, ny);
          x = nx;
          y = ny;
          if (c == 'M') command = relative ? 'l' : 'L';
        }
        case 'H' -> {
          x = number() + ox;
          path.lineTo(x, y);
        }
        case 'V' -> {
          y = number() + oy;
          path.lineTo(x, y);
        }
        case 'C', 'S' -> {
          double ax = c == 'C' ? number() + ox : last == 'C' || last == 'S' ? 2 * x - controlX : x;
          double ay = c == 'C' ? number() + oy : last == 'C' || last == 'S' ? 2 * y - controlY : y;
          double bx = number() + ox, by = number() + oy, nx = number() + ox, ny = number() + oy;
          path.curveTo(ax, ay, bx, by, nx, ny);
          controlX = bx;
          controlY = by;
          x = nx;
          y = ny;
        }
        case 'Q' -> {
          double ax = number() + ox, ay = number() + oy, nx = number() + ox, ny = number() + oy;
          path.quadTo(ax, ay, nx, ny);
          controlX = ax;
          controlY = ay;
          x = nx;
          y = ny;
        }
        case 'A' -> {
          double rx = Math.abs(number()), ry = Math.abs(number()), rotation = number();
          boolean large = flag(), sweep = flag();
          double nx = number() + ox, ny = number() + oy;
          arc(rx, ry, rotation, large, sweep, nx, ny);
          x = nx;
          y = ny;
        }
        case 'Z' -> {
          path.closePath();
          x = startX;
          y = startY;
          command = 0;
        }
        default -> throw new IllegalArgumentException("Unsupported SVG command");
      }
      last = c;
    }
    return path;
  }

  private double number() {
    if (i == tokens.size() || letter(tokens.get(i)))
      throw new IllegalArgumentException("Missing SVG coordinate");
    double n = Double.parseDouble(tokens.get(i++));
    if (!Double.isFinite(n) || Math.abs(n) > 100000)
      throw new IllegalArgumentException("SVG coordinate out of range");
    return n;
  }

  private boolean flag() {
    double n = number();
    if (n != 0 && n != 1) throw new IllegalArgumentException("Invalid SVG arc flag");
    return n == 1;
  }

  private static boolean letter(String s) {
    return s.length() == 1 && Character.isLetter(s.charAt(0));
  }

  /** SVG endpoint arc conversion, split into <=90 degree cubic curves. */
  private void arc(
      double rx, double ry, double degrees, boolean large, boolean sweep, double nx, double ny) {
    if (x == nx && y == ny) return;
    if (rx == 0 || ry == 0) {
      path.lineTo(nx, ny);
      return;
    }
    double phi = Math.toRadians(degrees % 360), cos = Math.cos(phi), sin = Math.sin(phi);
    double dx = (x - nx) / 2, dy = (y - ny) / 2;
    double xp = cos * dx + sin * dy, yp = -sin * dx + cos * dy;
    double scale = xp * xp / (rx * rx) + yp * yp / (ry * ry);
    if (scale > 1) {
      rx *= Math.sqrt(scale);
      ry *= Math.sqrt(scale);
    }
    double a = rx * rx, b = ry * ry, denom = a * yp * yp + b * xp * xp;
    double k = (large == sweep ? -1 : 1) * Math.sqrt(Math.max(0, (a * b - denom) / denom));
    double cxp = k * rx * yp / ry, cyp = -k * ry * xp / rx;
    double cx = cos * cxp - sin * cyp + (x + nx) / 2, cy = sin * cxp + cos * cyp + (y + ny) / 2;
    double start = Math.atan2((yp - cyp) / ry, (xp - cxp) / rx);
    double end = Math.atan2((-yp - cyp) / ry, (-xp - cxp) / rx);
    double delta = end - start;
    if (!sweep && delta > 0) delta -= Math.PI * 2;
    if (sweep && delta < 0) delta += Math.PI * 2;
    int count = Math.max(1, (int) Math.ceil(Math.abs(delta) / (Math.PI / 2)));
    for (int j = 0; j < count; j++) {
      double t = start + j * delta / count,
          u = t + delta / count,
          q = 4.0 / 3 * Math.tan((u - t) / 4);
      double ct = Math.cos(t), st = Math.sin(t), cu = Math.cos(u), su = Math.sin(u);
      double ax = ct - q * st, ay = st + q * ct, bx = cu + q * su, by = su - q * cu;
      path.curveTo(
          cx + rx * cos * ax - ry * sin * ay,
          cy + rx * sin * ax + ry * cos * ay,
          cx + rx * cos * bx - ry * sin * by,
          cy + rx * sin * bx + ry * cos * by,
          cx + rx * cos * cu - ry * sin * su,
          cy + rx * sin * cu + ry * cos * su);
    }
  }
}
