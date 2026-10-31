package dev.toybox.minecraft.client.hud;

import dev.toybox.minecraft.ToyBoxMinecraft;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** Channel identity, connection health, and public collaboration moments. */
public final class HudFeature {
  private HudFeature() {}

  public static void init() {
    Keys.ensureRegistered();
    HudElementRegistry.addLast(ToyBoxMinecraft.id("hud/connection"), new ConnectionBanner());
    HudElementRegistry.addLast(ToyBoxMinecraft.id("hud/channel"), new ChannelBar());
    HudElementRegistry.addLast(ToyBoxMinecraft.id("hud/moments"), new Toasts());
    Toasts.init();
  }
}
