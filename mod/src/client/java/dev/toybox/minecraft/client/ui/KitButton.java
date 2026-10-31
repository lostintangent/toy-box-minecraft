package dev.toybox.minecraft.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Toy Box artwork on Minecraft's button behavior, focus, and narration. */
public final class KitButton extends Button {
  public boolean selected;
  private @Nullable Identifier icon;

  public KitButton(String label, int width, Runnable action) {
    super(0, 0, width, 20, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
  }

  public static KitButton icon(String label, String icon, Runnable action) {
    var button = new KitButton(label, 24, action);
    button.icon = Kit.icon(icon);
    button.setTooltip(Tooltip.create(button.getMessage()));
    return button;
  }

  @Override
  protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
    var font = Minecraft.getInstance().font;
    String state = !active ? "disabled" : isHoveredOrFocused() ? "hover" : "normal";
    Panels.sprite(g, Kit.button(selected, state), getX(), getY(), width, height);
    if (icon != null) Panels.sprite(g, icon, getX() + (width - 12) / 2, getY() + 4, 12, 12);
    else {
      String label = TextUtil.ellipsize(font, getMessage().getString(), width - 12);
      g.text(
          font,
          label,
          getX() + (width - font.width(label)) / 2,
          getY() + 6,
          UiStyle.color(active ? "paper.text" : "paper.disabled"),
          false);
    }
    if (isFocused())
      g.fill(getX() + 4, getBottom() - 3, getRight() - 4, getBottom() - 2, UiStyle.INK);
  }
}
