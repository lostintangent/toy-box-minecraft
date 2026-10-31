package dev.toybox.minecraft.client.agents;

import com.mojang.blaze3d.platform.NativeImage;
import dev.toybox.minecraft.ToyBoxMinecraft;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel.Agent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Owns generated GPU textures for current Channel members; all operations run on the client thread.
 */
public final class AgentSkins {
  private record Entry(String recipe, PlayerSkin skin, Identifier body, Identifier portrait) {}

  private static final Map<String, Entry> CACHE = new HashMap<>();

  private AgentSkins() {}

  public static PlayerSkin get(Agent agent) {
    return entry(agent).skin();
  }

  public static Identifier portrait(String id) {
    Agent agent = ChannelClient.store().agent(id);
    return entry(agent == null ? new Agent("", "__unknown", "?", "", null, null, null) : agent)
        .portrait();
  }

  public static int color(String id) {
    Agent agent = ChannelClient.store().agent(id);
    return AvatarArt.color(agent == null || agent.avatar() == null ? null : agent.avatar().color());
  }

  public static int nameColor(String id) {
    return AvatarArt.onDark(color(id));
  }

  private static Entry entry(Agent agent) {
    String mark = agent.avatar() == null ? null : agent.avatar().mark();
    String color = agent.avatar() == null ? null : agent.avatar().color();
    String recipe = agent.name() + "|" + mark + "|" + color;
    Entry old = CACHE.get(agent.id());
    if (old != null && old.recipe().equals(recipe)) return old;
    if (old != null) release(old);
    String key = AvatarArt.key(agent.id());
    Identifier asset = ToyBoxMinecraft.id("dynamic/agent/" + key + "/body");
    var body = new ClientAsset.ResourceTexture(asset);
    Identifier portrait = ToyBoxMinecraft.id("textures/dynamic/agent/" + key + "/portrait.png");
    register(body.texturePath(), AvatarArt.skin(agent.id(), agent.name(), mark, color));
    register(portrait, AvatarArt.portrait(agent.name(), mark, color));
    Entry next =
        new Entry(
            recipe,
            PlayerSkin.insecure(
                body,
                null,
                null,
                AvatarArt.slim(agent.id()) ? PlayerModelType.SLIM : PlayerModelType.WIDE),
            body.texturePath(),
            portrait);
    CACHE.put(agent.id(), next);
    return next;
  }

  private static void register(Identifier id, BufferedImage source) {
    NativeImage pixels = new NativeImage(source.getWidth(), source.getHeight(), true);
    for (int y = 0; y < source.getHeight(); y++)
      for (int x = 0; x < source.getWidth(); x++) pixels.setPixel(x, y, source.getRGB(x, y));
    Minecraft.getInstance()
        .getTextureManager()
        .register(id, new DynamicTexture(id::toString, pixels));
  }

  private static void release(Entry e) {
    var textures = Minecraft.getInstance().getTextureManager();
    textures.release(e.body());
    textures.release(e.portrait());
  }

  public static void retain(Set<String> ids) {
    CACHE
        .entrySet()
        .removeIf(
            e -> {
              if (ids.contains(e.getKey()) || e.getKey().equals("__unknown")) return false;
              release(e.getValue());
              return true;
            });
  }

  public static void clear() {
    CACHE.values().forEach(AgentSkins::release);
    CACHE.clear();
  }
}
