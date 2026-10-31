package dev.toybox.minecraft.client.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.toybox.minecraft.client.channel.ChannelChanges;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelConnection;
import dev.toybox.minecraft.client.channel.ChannelModel;
import dev.toybox.minecraft.client.channel.ChannelStore;
import dev.toybox.minecraft.client.dev.DevBridge;
import dev.toybox.minecraft.client.dev.Fields;
import dev.toybox.minecraft.client.screens.AgentCardScreen;
import dev.toybox.minecraft.entity.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

/**
 * Agents feature: client-side agent NPCs (see mod/DEV.md). Owns entity rendering, the per-tick sync
 * with the Channel state ({@link AgentManager}), walking, nameplates, and the {@code dev.agents}
 * DevBridge diagnostics. Animation and effects live in {@link AgentLife}.
 */
public final class AgentsFeature {
  private AgentsFeature() {}

  public static void init() {
    // The type's own renderer draws nothing; the dispatcher mixin routes ClientAgentEntity to the
    // slim/wide AgentRenderer built here on every resource reload.
    EntityRenderers.register(
        ModEntities.AGENT,
        ctx -> {
          AgentRenderer.provide(ctx);
          return new NoopRenderer<>(ctx);
        });
    ClientTickEvents.END_CLIENT_TICK.register(mc -> AgentManager.get().tick(mc));
    // nameplate declutter: every agent's render state is extracted, nothing is submitted yet
    LevelExtractionEvents.END_EXTRACTION.register(ctx -> PlateLayout.layout(ctx.levelState()));
    ChannelClient.store()
        .addListener(
            new ChannelStore.Listener() {
              @Override
              public void onState(
                  ChannelModel.State snapshot, List<ChannelChanges.Change> changes) {
                AgentManager.get().onState(changes);
              }

              @Override
              public void onConnection(ChannelConnection connection) {
                if (!connection.connected()) AgentManager.get().disconnected();
              }

              @Override
              public void onCleared() {
                AgentManager.get().clear();
              }
            });

    UseEntityCallback.EVENT.register(
        (player, level, hand, entity, hit) -> {
          if (level.isClientSide() && entity instanceof ClientAgentEntity agent) {
            if (hand == InteractionHand.MAIN_HAND) {
              Minecraft.getInstance().gui.setScreen(new AgentCardScreen(agent.agentId()));
            }
            return InteractionResult.FAIL;
          }
          return InteractionResult.PASS;
        });
    AttackEntityCallback.EVENT.register(
        (player, level, hand, entity, hit) ->
            entity instanceof ClientAgentEntity ? InteractionResult.FAIL : InteractionResult.PASS);

    registerDiagnostics();
  }

  private static void registerDiagnostics() {
    DevBridge.registerScreen(
        "agent",
        mc -> {
          var team = ChannelClient.store().team();
          if (team.isEmpty()) {
            throw new DevBridge.DevException("no agents (is the Toy Box connected?)");
          }
          return new AgentCardScreen(team.getFirst().id());
        });
    DevBridge.addStateContributor(
        (mc, o) -> {
          JsonObject a = new JsonObject();
          AgentManager m = AgentManager.get();
          a.addProperty("count", m.entities().size());
          a.addProperty("moving", m.movingCount());
          a.addProperty("pathFailures", m.pathFailures());
          a.addProperty("plates", PlateLayout.laidOut());
          a.addProperty("plateOverlaps", PlateLayout.overlaps());
          a.addProperty("plateOverlapPairs", PlateLayout.overlapPairs());
          a.addProperty("plateLayoutUs", Math.round(PlateLayout.layoutMicros() * 10) / 10.0);
          // agents showing the "needs you" marker; QA: only the Channel request's sender
          a.addProperty(
              "exclaims", m.entities().values().stream().filter(e -> e.view().needsYou()).count());
          o.add("agents", a);
        });
    DevBridge.register(
        "dev.agents",
        10_000,
        "{agent?, settle?:false} -> {agents:[{id, state, activity, needsYou, station, anchor,"
            + " x,y,z, yaw, target, walking, path, posture, seat, headYaw, bubble, plate:{mode"
            + " full|compact, lift, rank, nudge, focused, rect:[x0,y0,x1,y1] screen px}, skin}]};"
            + " settle:true snaps walking agents to their targets and nameplates to their final"
            + " layout (next frame). Totals are in dev.state",
        (req, mc) -> {
          Fields f = Fields.of(req);
          String only = f.optStr("agent", null);
          boolean settle = f.optBool("settle", false);
          return mc.submit(
              () -> {
                AgentManager m = AgentManager.get();
                JsonObject o = new JsonObject();
                if (settle) {
                  o.addProperty("settled", m.settle());
                  PlateLayout.snapNextFrame();
                }
                JsonArray list = new JsonArray();
                for (ClientAgentEntity e : m.entities().values()) {
                  if (only == null || only.equals(e.agentId())) list.add(describe(e));
                }
                o.add("agents", list);
                return o;
              });
        });
    DevBridge.register(
        "dev.agents.fx",
        10_000,
        "{agent, fx: reaction|confetti|sparkle|say, text?, to?: agent id} -> play an agent effect"
            + " now (QA preview; 'say' shows a local speech bubble)",
        (req, mc) -> {
          Fields f = Fields.of(req);
          String id = f.nonBlank("agent");
          String fx = f.nonBlank("fx");
          String text = f.optStr("text", "Pushed the fix - tests are green again.");
          String to = f.optStr("to", null);
          return mc.submit(
              () -> {
                ClientAgentEntity e = AgentManager.get().entity(id);
                if (e == null) {
                  throw new DevBridge.DevException("no agent '" + id + "' in the world");
                }
                if (!e.life().preview(fx, text, to)) {
                  throw new DevBridge.DevException(
                      "unknown fx '" + fx + "' (reaction|confetti|sparkle|say)");
                }
                JsonObject o = new JsonObject();
                o.addProperty("agent", id);
                o.addProperty("fx", fx);
                return o;
              });
        });
  }

  /** One agent's physical presentation: placement, movement, posture, speech and nameplate. */
  private static JsonObject describe(ClientAgentEntity e) {
    AgentView v = e.view();
    AgentLife l = e.life();
    JsonObject j = new JsonObject();
    j.addProperty("id", v.id);
    j.addProperty("entityId", e.getId());
    j.addProperty("state", v.state);
    j.addProperty("activity", v.activityLine());
    j.addProperty("stale", v.stale);
    j.addProperty("needsYou", v.needsYou());
    j.addProperty("station", v.station);
    j.addProperty("anchor", v.anchor);
    j.addProperty("x", round(e.getX()));
    j.addProperty("y", round(e.getY()));
    j.addProperty("z", round(e.getZ()));
    j.addProperty("yaw", round(e.getYRot()));
    var t = e.motion().target();
    if (t != null) {
      JsonObject tj = new JsonObject();
      tj.addProperty("x", round(t.x()));
      tj.addProperty("y", round(t.y()));
      tj.addProperty("z", round(t.z()));
      tj.addProperty("yaw", round(t.yaw()));
      j.add("target", tj);
    }
    j.addProperty("walking", e.motion().walking());
    JsonArray path = new JsonArray();
    for (Vec3 p : e.motion().remainingPath()) {
      JsonArray pj = new JsonArray();
      pj.add(round(p.x));
      pj.add(round(p.y));
      pj.add(round(p.z));
      path.add(pj);
    }
    j.add("path", path);
    j.addProperty("posture", l.posture().name());
    j.addProperty("seated", l.seated());
    j.addProperty("sit", round(l.sitAmount()));
    Seats.Seat st = l.seat();
    if (st != null) {
      JsonObject sj = new JsonObject();
      sj.addProperty("x", round(st.sit().x));
      sj.addProperty("z", round(st.sit().z));
      sj.addProperty("top", round(st.seatTop()));
      sj.addProperty("drop", round(st.drop()));
      sj.addProperty("deskTop", Double.isNaN(st.deskTop()) ? null : round(st.deskTop()));
      j.add("seat", sj);
    }
    j.addProperty("bodyYaw", round(e.yBodyRot));
    j.addProperty("headYaw", round(e.getYHeadRot()));
    j.addProperty("headPitch", round(e.getXRot()));
    j.addProperty("bubble", l.bubble.visible() ? l.bubble.current().text() : null);
    PlateLayout.Track pt = PlateLayout.track(v.id);
    if (pt != null) {
      JsonObject pj = new JsonObject();
      pj.addProperty("mode", pt.compact ? "compact" : "full");
      pj.addProperty("lift", round(pt.lift));
      pj.addProperty("target", round(pt.targetLift));
      pj.addProperty("depth", round(pt.depth));
      pj.addProperty("weight", pt.weight);
      pj.addProperty("capped", pt.capped);
      pj.addProperty("leaderGaps", pt.gapCount);
      pj.addProperty("rank", pt.rank);
      pj.addProperty("nudge", Math.round(pt.nudge * 1e5) / 1e5);
      pj.addProperty("scale", round(pt.scale));
      pj.addProperty("focused", pt.focused);
      JsonArray r = new JsonArray();
      r.add(round(pt.rx0));
      r.add(round(pt.ry0));
      r.add(round(pt.rx1));
      r.add(round(pt.ry1));
      pj.add("rect", r);
      j.add("plate", pj);
    }
    j.addProperty("model", e.getSkin().model().getSerializedName());
    j.addProperty("skin", e.getSkin().body().texturePath().toString());
    return j;
  }

  private static double round(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
