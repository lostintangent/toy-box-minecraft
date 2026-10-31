package dev.toybox.minecraft.world;

/** Non-overlapping workshop extensions, outside the base builder's managed site. */
public final class DeskPodLayout {
  public record Pod(int x, int z) {
    public int minX() {
      return x - 2;
    }

    public int maxX() {
      return x + 2;
    }

    public int minZ() {
      return z - 2;
    }

    public int maxZ() {
      return z + 2;
    }
  }

  private DeskPodLayout() {}

  public static Pod candidate(int index) {
    if (index < 0) throw new IllegalArgumentException("Negative desk index");
    // Seven-block spacing keeps two clear walking columns between five-block pods.
    return new Pod(-21 + (index % 7) * 7, -43 - (index / 7) * 7);
  }
}
