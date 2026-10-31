package dev.toybox.minecraft.client.world;

import dev.toybox.minecraft.block.LampStatus;
import dev.toybox.minecraft.block.MonitorBlock;
import dev.toybox.minecraft.block.PreviewBenchBlock;
import dev.toybox.minecraft.block.RequestPodiumBlock;
import dev.toybox.minecraft.block.StatusLampBlock;
import dev.toybox.minecraft.block.entity.MonitorBlockEntity;
import dev.toybox.minecraft.block.entity.PreviewBenchBlockEntity;
import dev.toybox.minecraft.block.entity.RequestPodiumBlockEntity;
import dev.toybox.minecraft.block.entity.StatusLampBlockEntity;
import dev.toybox.minecraft.client.agents.AgentManager;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelConnection;
import dev.toybox.minecraft.client.channel.ChannelStore;
import dev.toybox.minecraft.world.layout.Anchor;
import dev.toybox.minecraft.world.layout.AnchorNames;
import dev.toybox.minecraft.world.layout.Anchors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** Applies channel presence and attention to the workshop, preserving stale views while offline. */
public final class WorldProjection {
  private static final int RESYNC_TICKS = 40;

  /**
   * Blocks around the layout bounds that still belong to the building (lamps set into the walls).
   */
  private static final int MARGIN = 3;

  /** Reach of a station's signal bulbs around its anchor (blocks). */
  private static final int SIGNAL_REACH = 3;

  /** What the world should show, by binding. Immutable once built. */
  record Wanted(
      Map<String, LampStatus> lamps,
      boolean podiumOpen,
      boolean previewActive,
      Map<String, Boolean> monitorLit) {}

  private static @Nullable Wanted last;
  private static long lastRevision = -1;
  private static long lastLayout = -1;
  private static int ticks;
  private static volatile int lastChanged;

  private WorldProjection() {}

  /** Blocks changed by the last apply (QA / debugging). */
  public static int lastChanged() {
    return lastChanged;
  }

  public static @Nullable Wanted wanted() {
    return last;
  }

  static void tick(Minecraft mc) {
    var server = mc.getSingleplayerServer();
    if (mc.level == null || server == null) {
      return;
    }
    ChannelStore st = ChannelClient.store();
    Anchors.Layout layout = Anchors.current();
    if ((st.isStale() && st.connection().phase() != ChannelConnection.Phase.DELETED)
        || layout.isEmpty()
        || layout.bounds() == null) {
      return;
    }
    ticks++;
    boolean changed = st.revision() != lastRevision || layout.revision() != lastLayout;
    if (!changed && ticks % RESYNC_TICKS != 0) {
      return;
    }
    Wanted w = changed || last == null ? compute(st) : last;
    lastRevision = st.revision();
    lastLayout = layout.revision();
    boolean differs = !Objects.equals(w, last);
    last = w;
    if (differs || ticks % RESYNC_TICKS == 0) {
      Anchors.Bounds b = layout.bounds();
      List<BlockPos> podiumSignals = signalCenters(layout, AnchorNames.REQUEST_PODIUM);
      List<BlockPos> previewSignals = signalCenters(layout, AnchorNames.PREVIEW_BENCH);
      server.execute(
          () -> lastChanged = apply(server.overworld(), w, b, podiumSignals, previewSignals));
    }
  }

  /** Block positions of the anchors of a station (all its slots). */
  private static List<BlockPos> signalCenters(Anchors.Layout layout, String station) {
    List<BlockPos> out = new ArrayList<>();
    for (Anchor a : layout.anchors().values()) {
      String n = a.name();
      if (n.equals(station)
          || n.startsWith(station + "_")
              && n.substring(station.length() + 1).chars().allMatch(Character::isDigit)) {
        out.add(BlockPos.containing(a.x(), a.y(), a.z()));
      }
    }
    return out;
  }

  public static final String BEACON_BINDING = "beacon";

  static Wanted compute(ChannelStore state) {
    Map<String, LampStatus> lamps = new HashMap<>();
    Map<String, Boolean> lit = new HashMap<>();
    boolean request = state.request() != null, working = false, waiting = false;
    for (var member : state.team()) {
      String presence = state.presence(member.id()).state();
      LampStatus lamp =
          switch (presence) {
            case "working" -> LampStatus.WORKING;
            case "waiting" -> LampStatus.WAITING;
            default -> LampStatus.IDLE;
          };
      if (request && member.id().equals(state.request().sender().agentId()))
        lamp = LampStatus.WAITING;
      working |= presence.equals("working");
      waiting |= presence.equals("waiting");
      lamps.put("agent:" + member.id(), lamp);
      lit.put(member.id(), true);
      String desk = AgentManager.get().deskBinding(member.id());
      if (desk != null) {
        lamps.put("agent:" + desk, lamp);
        lit.put(desk, true);
      }
    }
    boolean preview = state.channel() != null && state.channel().previewUrl() != null;
    LampStatus aggregate =
        !state.hasData()
            ? LampStatus.OFF
            : request
                ? LampStatus.WAITING
                : working
                    ? LampStatus.WORKING
                    : waiting
                        ? LampStatus.WAITING
                        : state.channel().hasUnreadCompletion() ? LampStatus.DONE : LampStatus.IDLE;
    for (int n = 0; n < state.routines().size(); n++)
      lamps.put("routine:#" + (n + 1), LampStatus.IDLE);
    lamps.put("channel", aggregate);
    lamps.put("channel:atrium", aggregate);
    lamps.put("request", request ? LampStatus.WAITING : LampStatus.OFF);
    boolean waitingHere =
        state.team().stream()
            .anyMatch(
                a ->
                    state.presence(a.id()).state().equals("waiting")
                        && (state.request() == null
                            || !a.id().equals(state.request().sender().agentId())));
    lamps.put("waiting:area", waitingHere ? LampStatus.WAITING : LampStatus.IDLE);
    lamps.put("preview", preview ? LampStatus.IDLE : LampStatus.OFF);
    lamps.put(BEACON_BINDING, aggregate);
    return new Wanted(Map.copyOf(lamps), request, preview, Map.copyOf(lit));
  }

  /** Server thread: set every bound station block in the building site to its wanted state. */
  static int apply(
      ServerLevel level,
      Wanted w,
      Anchors.Bounds b,
      List<BlockPos> podiumSignals,
      List<BlockPos> previewSignals) {
    Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
    int x0 = (b.minX() - MARGIN) >> 4;
    int x1 = (b.maxX() + MARGIN) >> 4;
    int z0 = (b.minZ() - MARGIN) >> 4;
    int z1 = (b.maxZ() + MARGIN) >> 4;
    for (int cx = x0; cx <= x1; cx++) {
      for (int cz = z0; cz <= z1; cz++) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) {
          continue;
        }
        for (BlockEntity be : chunk.getBlockEntities().values()) {
          BlockPos p = be.getBlockPos();
          if (p.getX() < b.minX() - MARGIN
              || p.getX() > b.maxX() + MARGIN
              || p.getZ() < b.minZ() - MARGIN
              || p.getZ() > b.maxZ() + MARGIN) {
            continue;
          }
          BlockState s = be.getBlockState();
          BlockState want = wantedState(be, s, w);
          if (want != null && want != s) {
            changes.put(p, want);
          }
        }
      }
    }
    signals(level, podiumSignals, w.podiumOpen(), changes);
    signals(level, previewSignals, w.previewActive(), changes);
    changes.forEach(
        (pos, state) ->
            level.setBlock(
                pos,
                state,
                state.getBlock() instanceof RequestPodiumBlock
                    ? Block.UPDATE_ALL
                    : Block.UPDATE_CLIENTS));
    return changes.size();
  }

  /** Copper bulbs around the given station anchors follow {@code on} (no redstone involved). */
  private static void signals(
      ServerLevel level, List<BlockPos> centers, boolean on, Map<BlockPos, BlockState> changes) {
    for (BlockPos center : centers) {
      for (BlockPos pos :
          BlockPos.betweenClosed(
              center.offset(-SIGNAL_REACH, -1, -SIGNAL_REACH),
              center.offset(SIGNAL_REACH, 6, SIGNAL_REACH))) {
        if (!level.isLoaded(pos)) continue;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CopperBulbBlock
            && state.getValue(CopperBulbBlock.LIT) != on)
          changes.putIfAbsent(pos.immutable(), state.setValue(CopperBulbBlock.LIT, on));
      }
    }
  }

  private static @Nullable BlockState wantedState(BlockEntity be, BlockState s, Wanted w) {
    if (be instanceof StatusLampBlockEntity lamp && s.getBlock() instanceof StatusLampBlock) {
      return s.setValue(
          StatusLampBlock.STATUS, w.lamps().getOrDefault(lamp.binding(), LampStatus.OFF));
    }
    if (be instanceof RequestPodiumBlockEntity && s.getBlock() instanceof RequestPodiumBlock) {
      return s.setValue(RequestPodiumBlock.OPEN, w.podiumOpen());
    }
    if (be instanceof PreviewBenchBlockEntity && s.getBlock() instanceof PreviewBenchBlock) {
      return s.setValue(PreviewBenchBlock.ACTIVE, w.previewActive());
    }
    if (be instanceof MonitorBlockEntity mon
        && s.getBlock() instanceof MonitorBlock
        && !mon.binding().isEmpty()) {
      Boolean lit = w.monitorLit().get(mon.binding());
      return s.setValue(MonitorBlock.LIT, lit != null && lit);
    }
    return null;
  }
}
