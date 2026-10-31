package dev.toybox.minecraft.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** {@code status} of a status lamp. Colours are the status palette (docs/visual-bar.md). */
public enum LampStatus implements StringRepresentable {
  OFF,
  IDLE,
  THINKING,
  WORKING,
  WAITING,
  ERROR,
  DONE;

  private final String serialized = name().toLowerCase(Locale.ROOT);

  @Override
  public String getSerializedName() {
    return serialized;
  }
}
