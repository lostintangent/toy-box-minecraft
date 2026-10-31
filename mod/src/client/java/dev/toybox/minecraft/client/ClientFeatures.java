package dev.toybox.minecraft.client;

import dev.toybox.minecraft.client.agents.AgentsFeature;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.dev.AnchorsDev;
import dev.toybox.minecraft.client.dev.ItemsDev;
import dev.toybox.minecraft.client.hud.HudFeature;
import dev.toybox.minecraft.client.screens.Screens;
import dev.toybox.minecraft.client.world.WorldClient;

/** Composition root: one channel observer, then independent views of that state. */
public final class ClientFeatures {

  private ClientFeatures() {}

  public static void init() {
    ChannelClient.init();
    AnchorsDev.init();
    ItemsDev.init();
    AgentsFeature.init();
    HudFeature.init();
    WorldClient.init();
    Screens.init();
    dev.toybox.minecraft.client.dev.DevBridge.addStateContributor(
        (mc, state) -> state.add("channel", diagnostics()));
    dev.toybox.minecraft.client.dev.DevBridge.register(
        "dev.channel",
        5000,
        "{reconnect?:boolean} - channel observer diagnostics",
        (request, mc) ->
            mc.submit(
                () -> {
                  if (dev.toybox.minecraft.client.dev.Fields.of(request)
                      .optBool("reconnect", false)) ChannelClient.reconnect();
                  return diagnostics();
                }));
  }

  private static com.google.gson.JsonObject diagnostics() {
    var state = ChannelClient.store();
    var result = new com.google.gson.JsonObject();
    result.addProperty("phase", state.connection().phaseName());
    result.addProperty("stale", state.isStale());
    result.addProperty("channelId", state.channel() == null ? null : state.channel().id());
    result.addProperty("name", state.channel() == null ? null : state.channel().name());
    result.addProperty("members", state.team().size());
    result.addProperty("messages", state.messages().size());
    result.addProperty("artifacts", state.artifacts().size());
    result.addProperty("routines", state.routines().size());
    result.addProperty("request", state.request() == null ? null : state.request().sequence());
    result.addProperty(
        "latestSequence", state.channel() == null ? null : state.channel().latestSequence());
    result.addProperty(
        "seenThrough", state.channel() == null ? null : state.channel().seenThrough());
    result.addProperty("snapshots", ChannelClient.snapshotCount());
    result.addProperty("bells", dev.toybox.minecraft.client.world.WorldSounds.bells());
    result.addProperty("chimes", dev.toybox.minecraft.client.world.WorldSounds.chimes());
    return result;
  }
}
