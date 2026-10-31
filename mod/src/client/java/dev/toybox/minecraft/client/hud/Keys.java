package dev.toybox.minecraft.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.toybox.minecraft.ToyBoxMinecraft;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

/**
 * Toy Box key mappings (Options > Controls > Key Binds > ToyBoxMinecraft, rebindable like any
 * vanilla key): the console ({@code `}), the console from a terminal you look at (Enter) and the
 * request queue ({@code J}). Registered once, from whichever feature initialises first.
 */
public final class Keys {
  public static KeyMapping transcript;
  public static KeyMapping terminal;
  public static KeyMapping requests;
  public static KeyMapping discuss;
  private static boolean registered;

  private Keys() {}

  public static synchronized void ensureRegistered() {
    if (registered) {
      return;
    }
    registered = true;
    KeyMapping.Category cat;
    try {
      cat = KeyMapping.Category.register(ToyBoxMinecraft.id("toybox_minecraft"));
    } catch (IllegalArgumentException alreadyThere) {
      // another feature registered the ToyBoxMinecraft category first: categories are records
      // (equal by id)
      cat = new KeyMapping.Category(ToyBoxMinecraft.id("toybox_minecraft"));
    }
    transcript =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.toybox_minecraft.transcript",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_GRAVE,
                cat,
                1));
    terminal =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.toybox_minecraft.channel_terminal",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RETURN,
                cat,
                2));
    discuss =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.toybox_minecraft.discuss",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_R,
                cat,
                4));
    requests =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.toybox_minecraft.requests",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_J,
                cat,
                3));
  }

  /**
   * Short label of the key a mapping is bound to ("J", "Enter", "Backtick"). Punctuation keys whose
   * glyph is only a pixel or two in the Minecraft font (` ' , . ; :) are spelled out, so a keycap
   * never looks empty.
   */
  public static String label(KeyMapping k) {
    if (k == null || k.isUnbound()) {
      return "?";
    }
    return readable(k.getTranslatedKeyMessage().getString());
  }

  /** Spell out tiny punctuation glyphs; cut long names to 9 characters. */
  public static String readable(String s) {
    String word =
        switch (s) {
          case "`" -> "Backtick";
          case "'" -> "Quote";
          case "´" -> "Accent";
          case "," -> "Comma";
          case "." -> "Period";
          case ";" -> "Semicolon";
          case ":" -> "Colon";
          case "|" -> "Bar";
          default -> s;
        };
    return word.length() > 9 ? word.substring(0, 9) : word;
  }

  public static boolean matches(KeyMapping k, KeyEvent e) {
    return k != null && k.matches(e);
  }
}
