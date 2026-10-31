package dev.toybox.minecraft.world;

import java.util.List;

/** Indoor desk centers by stable bay number; the first six retain their original positions. */
public final class WorkshopDeskLayout {
  public static final List<Integer> CENTERS =
      List.of(-15, -11, -7, 7, 11, 15, -22, -19, -3, 3, 19, 22);

  private WorkshopDeskLayout() {}

  /** The end bays have two-wide displays so adjacent monitors never connect across owners. */
  public static int monitorMinX(int center) {
    return center == 22 ? center : center - 1;
  }

  public static int monitorMaxX(int center) {
    return center == -22 ? center : center + 1;
  }
}
