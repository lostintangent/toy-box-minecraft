package dev.toybox.minecraft.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.toybox.minecraft.ToyBoxMinecraft;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The GUI kit sprites ({@code toybox_minecraft:kit/<name>} in the vanilla GUI atlas; nine-slice
 * metadata ships with the stretchable ones). Content padding per sprite comes from {@code
 * gui/kit.json}. Draw them in screens with {@link Panels} (GuiGraphicsExtractor.blitSprite) and in
 * the world with {@link WorldUi}.
 */
public final class Kit {
  public static final Identifier PANEL_PAPER = id("panel_paper");
  public static final Identifier HEADER = id("header");
  public static final Identifier DIVIDER = id("divider");
  public static final Identifier TOOLTIP = id("tooltip");
  public static final Identifier NAMEPLATE = id("nameplate");
  public static final Identifier BUBBLE = id("bubble");
  public static final Identifier BUBBLE_TAIL = id("bubble_tail");
  public static final Identifier PILL = id("pill");
  public static final Identifier TEXT_FIELD = id("text_field");
  public static final Identifier TEXT_FIELD_FOCUSED = id("text_field_focused");
  public static final Identifier PROGRESS_TRACK = id("progress_track");
  public static final Identifier CHECKBOX = id("checkbox");
  public static final Identifier CHECKBOX_CHECKED = id("checkbox_checked");

  /** Padding (left, top, right, bottom) between a sprite's edge and its content, from kit.json. */
  public record Padding(int left, int top, int right, int bottom) {
    public static final Padding ZERO = new Padding(0, 0, 0, 0);
  }

  private static final Map<String, Padding> PADDING = new HashMap<>();

  static {
    try (InputStream in = Kit.class.getResourceAsStream("/assets/toybox_minecraft/gui/kit.json")) {
      if (in != null) {
        JsonObject sprites =
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("sprites");
        for (var e : sprites.entrySet()) {
          JsonObject s = e.getValue().getAsJsonObject();
          if (s.has("content_padding")) {
            JsonObject p = s.getAsJsonObject("content_padding");
            PADDING.put(
                e.getKey(),
                new Padding(
                    p.get("left").getAsInt(),
                    p.get("top").getAsInt(),
                    p.get("right").getAsInt(),
                    p.get("bottom").getAsInt()));
          }
        }
      }
    } catch (Exception e) {
      ToyBoxMinecraft.LOGGER.warn("Kit: could not read gui/kit.json", e);
    }
  }

  private Kit() {}

  public static Identifier id(String name) {
    return ToyBoxMinecraft.id("kit/" + name);
  }

  public static Padding padding(String spriteName) {
    return PADDING.getOrDefault(spriteName, Padding.ZERO);
  }

  /**
   * Button sprite: {@code primary} = clay call-to-action; state: normal | hover | pressed |
   * disabled.
   */
  public static Identifier button(boolean primary, String state) {
    String base = primary ? "button_primary" : "button";
    return "normal".equals(state) ? id(base) : id(base + "_" + state);
  }

  /**
   * 7x7 status dot by status (idle thinking working waiting error done); {@code halo} = 11x11 with
   * glow.
   */
  public static Identifier dot(String status, boolean halo) {
    return id("dot_" + status.toLowerCase(Locale.ROOT) + (halo ? "_halo" : ""));
  }

  /** Task card background by task status (todo doing review done blocked). */
  public static Identifier card(String taskStatus) {
    return id("card_" + taskStatus.toLowerCase(Locale.ROOT));
  }

  public static Identifier progressFill(String tint) {
    return id("progress_fill_" + tint);
  }

  /** 12x12 tool icon: bash decision edit git memory merge message read test. */
  public static Identifier icon(String name) {
    return id("icon_" + name);
  }
}
