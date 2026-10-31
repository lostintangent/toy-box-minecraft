package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.ToyBoxMinecraft;
import dev.toybox.minecraft.client.channel.ChannelChanges;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel.*;
import dev.toybox.minecraft.client.world.WorldSounds;
import dev.toybox.minecraft.world.WorkshopDesks;
import dev.toybox.minecraft.world.WorldRules;
import dev.toybox.minecraft.world.layout.Anchor;
import dev.toybox.minecraft.world.layout.AnchorNames;
import dev.toybox.minecraft.world.layout.Anchors;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Owns one client entity per actual Channel member and applies only native live-change effects. */
public final class AgentManager {
  private static final AgentManager INSTANCE = new AgentManager();
  private static final double MAX_WALK = 96;
  private static final int STAND_UP_TICKS = 8;
  private final Map<String, ClientAgentEntity> entities = new LinkedHashMap<>();
  private final StationAssigner assigner = new StationAssigner();
  private Map<String, String> loungeAssignments = Map.of();
  private Map<String, String> waitingAssignments = Map.of();
  private final Seats seats = new Seats();
  private final List<ChannelChanges.Change> pending = new ArrayList<>();
  private final Map<String, Long> departures = new HashMap<>();
  private boolean boxOpen;
  private @Nullable ClientLevel level;
  private String channelId = "";
  private long layoutRevision = -1, ticks;
  private int nextEntityId = -10000, pathFailures;
  private volatile boolean growing;
  private int attemptedSize;

  private AgentManager() {}

  public static AgentManager get() {
    return INSTANCE;
  }

  public Map<String, ClientAgentEntity> entities() {
    return Collections.unmodifiableMap(entities);
  }

  public @Nullable ClientAgentEntity entity(String id) {
    return entities.get(id);
  }

  public @Nullable ClientAgentEntity byEntityId(int id) {
    return level != null && level.getEntity(id) instanceof ClientAgentEntity agent ? agent : null;
  }

  public int pathFailures() {
    return pathFailures;
  }

  public int movingCount() {
    return (int) entities.values().stream().filter(e -> e.motion().walking()).count();
  }

  public @Nullable String deskBinding(String id) {
    return assigner.binding(id);
  }

  public @Nullable String agentAtDesk(String binding) {
    return assigner.agentAtDesk(binding);
  }

  public @Nullable Anchor monitor(String id) {
    String binding = deskBinding(id);
    return binding == null ? null : Anchors.get(AnchorNames.monitor(binding));
  }

  void onState(List<ChannelChanges.Change> changes) {
    // Reconnect and initial snapshots contain no changes. Never replay historical messages.
    if (changes.isEmpty()) return;
    pending.addAll(changes);
  }

  void disconnected() {
    pending.clear();
  }

  void clear() {
    openBox(false);
    pending.clear();
    reset();
    channelId = "";
  }

  /** A cleared store, a new level, or another channel starts the workshop's agents over. */
  private void reset() {
    removeAll();
    departures.clear();
    assigner.clear();
    seats.clear();
    loungeAssignments = Map.of();
    waitingAssignments = Map.of();
    attemptedSize = 0;
    AgentSkins.clear();
  }

  /** Where one member belongs this tick: its station, the anchor to stand at, and any seat there. */
  private record Spot(String station, Anchor target, Seats.@Nullable Seat seat) {}

  void tick(Minecraft mc) {
    State snapshot = sync(mc.level);
    if (snapshot == null) return;
    var layout = Anchors.current();
    boolean relayout = relayout(layout);
    List<Agent> team = snapshot.team().stream().sorted(Comparator.comparing(Agent::id)).toList();
    var ids = team.stream().map(Agent::id).toList();
    assigner.assign(ids, layout);
    growDesks(mc, layout, team.size());
    var pf = new GridPathfinder(level, layout.bounds());
    sendOff(layout, pf);
    departures.keySet().removeAll(ids); // a member who left and came back stays
    deliverBooks(ids);
    var spots = spots(snapshot, team, layout, pf);
    Anchor box = layout.get(AnchorNames.TOY_BOX);
    var joined = joined();
    for (Agent a : team) {
      Spot spot = spots.get(a.id());
      if (spot != null) move(a, spot, snapshot, pf, relayout, joined.contains(a.id()) ? box : null);
    }
    Set<String> members = new HashSet<>(ids);
    removeDeparted(members);
    openBox(entities.values().stream().anyMatch(e -> e.life().emerging()));
    for (var change : List.copyOf(pending)) animate(change, snapshot);
    pending.clear();
    members.addAll(departures.keySet()); // a departing agent keeps its skin until it is gone
    AgentSkins.retain(members);
  }

  /** Follow the client's level and the observed channel; null while there is nothing to show. */
  private @Nullable State sync(@Nullable ClientLevel lvl) {
    if (lvl != level) {
      level = null; // the old level took its entities with it
      boxOpen = false;
      reset();
      level = lvl;
      layoutRevision = -1;
    }
    if (lvl == null) {
      pending.clear();
      return null;
    }
    ticks++;
    State snapshot = ChannelClient.store().state();
    if (snapshot == null) {
      clear();
      return null;
    }
    if (!channelId.equals(snapshot.channel().id())) {
      reset();
      channelId = snapshot.channel().id();
    }
    return snapshot;
  }

  /** A rebuilt layout invalidates cached seats and desk growth, and agents snap instead of walk. */
  private boolean relayout(Anchors.Layout layout) {
    boolean relayout = layout.revision() != layoutRevision;
    layoutRevision = layout.revision();
    if (relayout) {
      seats.clear();
      attemptedSize = 0;
    }
    return relayout;
  }

  /** Build only checked, empty desk footprints on the integrated server's thread. */
  private void growDesks(Minecraft mc, Anchors.Layout layout, int members) {
    var server = mc.getSingleplayerServer();
    if (growing
        || server == null
        || !WorldRules.isToyBox(server)
        || WorkshopDesks.count(layout) >= members
        || attemptedSize == members) return;
    growing = true;
    attemptedSize = members;
    server.execute(
        () -> {
          try {
            WorkshopDesks.ensure(server.overworld(), members);
          } finally {
            growing = false;
          }
        });
  }

  /** Members who left wave, put down any book, and walk out through the entrance. */
  private void sendOff(Anchors.Layout layout, GridPathfinder pf) {
    Anchor exit = layout.get(AnchorNames.ENTRANCE);
    for (var change : pending)
      if (change instanceof ChannelChanges.MemberLeft left) {
        ClientAgentEntity e = entities.get(left.member().id());
        if (e == null) continue;
        e.life().wave();
        e.life().carryBook(false);
        if (exit != null) retarget(pf, e, exit, null);
        departures.put(left.member().id(), ticks + 400);
      }
  }

  /** Members who joined this tick; their agents come out of the toy box. */
  private Set<String> joined() {
    Set<String> joined = new HashSet<>();
    for (var change : pending)
      if (change instanceof ChannelChanges.MemberJoined j) joined.add(j.member().id());
    return joined;
  }

  /** Authors who carried a book to the library put it down there. */
  private void deliverBooks(List<String> ids) {
    for (String id : ids) {
      ClientAgentEntity e = entities.get(id);
      if (e == null || !e.life().carryingBook()) continue;
      Anchor at = e.motion().target();
      if (at != null && at.name().equals(AnchorNames.ARTIFACTS) && !e.motion().walking()) {
        e.life().carryBook(false);
        WorldSounds.delivered();
      }
    }
  }

  /**
   * Where each member belongs, in roster order: its station's anchor and any seat there, or checked
   * free floor when that spot is taken or obstructed. Lounge and waiting spots are sticky per agent,
   * and an author still carrying a book heads to the library.
   */
  private Map<String, Spot> spots(
      State snapshot, List<Agent> team, Anchors.Layout layout, GridPathfinder pf) {
    Map<String, String> stations = new HashMap<>();
    for (Agent agent : team) {
      boolean requested =
          snapshot.request() != null && agent.id().equals(snapshot.request().sender().agentId());
      stations.put(
          agent.id(), AgentDestination.station(snapshot.presence(agent.id()).state(), requested));
    }
    var ids = team.stream().map(Agent::id).toList();
    var loungeSpots = zoneSpots(level, pf, layout, AnchorNames.LOUNGE);
    var waitingSpots = zoneSpots(level, pf, layout, AnchorNames.WAITING);
    loungeAssignments =
        RosterLayout.assign(
            loungeAssignments,
            ids.stream().filter(id -> stations.get(id).equals(AnchorNames.LOUNGE)).toList(),
            List.copyOf(loungeSpots.keySet()));
    waitingAssignments =
        RosterLayout.assign(
            waitingAssignments,
            ids.stream().filter(id -> stations.get(id).equals(AnchorNames.WAITING)).toList(),
            List.copyOf(waitingSpots.keySet()));
    Map<String, Spot> spots = new HashMap<>();
    List<Vec3> occupied = new ArrayList<>();
    for (Agent a : team) {
      String station = stations.get(a.id());
      String desk = assigner.assignedSlot(a.id());
      Anchor target =
          switch (station) {
            case AnchorNames.USER -> layout.get(AnchorNames.USER);
            case AnchorNames.LOUNGE -> loungeSpots.get(loungeAssignments.get(a.id()));
            case AnchorNames.WAITING -> waitingSpots.get(waitingAssignments.get(a.id()));
            default -> desk == null ? null : layout.get(desk);
          };
      ClientAgentEntity existing = entities.get(a.id());
      if (existing != null && existing.life().carryingBook())
        target = layout.get(AnchorNames.ARTIFACTS);
      Seats.Seat seat = target == null ? null : seats.at(level, target, ticks, pf);
      if (target == null
          || seat == null && !safe(pf, target.pos(), occupied)
          || seat != null && !spaced(seat.target().pos(), occupied)) {
        target = freeSpot(level, pf, layout, a.id(), occupied);
        seat = null;
        // A crowded or player-obstructed zone can use checked free floor, never another chair.
        if (!station.equals(AnchorNames.WAITING) && !station.equals(AnchorNames.LOUNGE))
          station = "workshop";
      }
      if (target == null) continue; // a completely obstructed world cannot safely hold another entity
      occupied.add(target.pos());
      spots.put(a.id(), new Spot(station, target, seat));
    }
    return spots;
  }

  /**
   * Spawn the member's agent (out of the toy box when it just joined) or refresh it, then send it to
   * its spot: walking, or snapping when the layout was rebuilt.
   */
  private void move(
      Agent a,
      Spot spot,
      State snapshot,
      GridPathfinder pf,
      boolean relayout,
      @Nullable Anchor arrival) {
    boolean stale = ChannelClient.store().isStale();
    ClientAgentEntity e = entities.get(a.id());
    if (e == null || e.isRemoved()) {
      e = new ClientAgentEntity(level, a.id(), AgentSkins.get(a));
      e.setId(nextEntityId--);
      e.life().setSeat(arrival != null ? null : spot.seat());
      place(
          e,
          arrival != null ? arrival : spot.seat() == null ? spot.target() : spot.seat().target());
      if (arrival != null) e.life().arrive();
      level.addEntity(e);
      entities.put(a.id(), e);
    } else e.setSkin(AgentSkins.get(a));
    e.view().update(a, snapshot.presence(a.id()), snapshot, stale);
    e.view().station = spot.station();
    e.view().anchor = spot.target().name();
    Anchor effective = spot.seat() == null ? spot.target() : spot.seat().target();
    Anchor current = e.motion().target();
    if (current != null && current.name().equals(AnchorNames.TOY_BOX)) {
      if (e.life().emerging()) return;
      // Step down from the open box before asking the floor pathfinder for a route.
      var step = new Anchor("unboxed", current.x(), current.y() - .875, current.z() - 1, 180, 0);
      e.motion().walkTo(step, List.of(e.position(), step.pos()), 0);
      return;
    }
    if (e.motion().walking() && e.motion().target().name().equals("unboxed")) return;
    if (relayout) {
      e.life().setSeat(spot.seat());
      place(e, effective);
    } else if (!stale) retarget(pf, e, effective, spot.seat());
  }

  /** Members who left finish walking out (20 s at most) before their agents are removed. */
  private void removeDeparted(Set<String> members) {
    for (var it = entities.entrySet().iterator(); it.hasNext(); ) {
      var en = it.next();
      if (!members.contains(en.getKey())
          && (departures.getOrDefault(en.getKey(), 0L) <= ticks
              || !en.getValue().motion().walking())) {
        remove(level, en.getValue());
        departures.remove(en.getKey());
        it.remove();
      }
    }
  }

  private void openBox(boolean open) {
    if (boxOpen == open) return;
    boxOpen = open;
    var box = Anchors.get(AnchorNames.TOY_BOX);
    var server = Minecraft.getInstance().getSingleplayerServer();
    if (box != null && server != null)
      server.execute(
          () -> server.overworld().blockEvent(box.blockPos(), Blocks.CHEST, 1, open ? 1 : 0));
  }

  private void animate(ChannelChanges.Change change, State snapshot) {
    if (change instanceof ChannelChanges.MessagePosted posted) {
      Message m = posted.message();
      if (m.system() != null && m.system().type().equals("artifact_shared")) {
        var actor = m.system().actor();
        ClientAgentEntity author = actor == null ? null : entity(actor.agentId());
        if (author == null) WorldSounds.delivered();
        else author.life().carryBook(true);
      }
      ClientAgentEntity speaker = entity(m.sender().agentId());
      if (speaker != null && !m.content().isBlank()) {
        var audience =
            dev.toybox.minecraft.client.channel.ChannelAudience.resolve(
                snapshot, m.content(), m.sender(), false);
        String to = audience.size() == 1 ? audience.getFirst().id() : null;
        speaker.life().onSay(m.content(), to);
        for (var member : audience) {
          ClientAgentEntity listener = entity(member.id());
          if (listener != null && listener != speaker)
            listener.life().listen(speaker.agentId(), 80);
        }
      }
    } else if (change instanceof ChannelChanges.ReactionChanged reaction) {
      ClientAgentEntity member = entity(reaction.agentId());
      if (member != null && reaction.current() != null)
        member.life().onReaction(reaction.current());
    } else if (change instanceof ChannelChanges.TasksCompleted) {
      for (var member : entities.values()) member.life().onTaskDone();
    }
  }

  /** Stable candidate names include actual seats and checked standing room around this zone. */
  private Map<String, Anchor> zoneSpots(
      ClientLevel level, GridPathfinder pf, Anchors.Layout layout, String station) {
    List<Anchor> anchors = StationAssigner.slots(layout, station);
    Map<String, Anchor> spots = new LinkedHashMap<>();
    List<Vec3> reserved = new ArrayList<>();
    for (Anchor anchor : anchors) {
      Seats.Seat seat = seats.at(level, anchor, ticks, pf);
      BlockPos above = BlockPos.containing(anchor.x(), anchor.y() + 1, anchor.z());
      if (seat != null
              && seat.approach() != null
              && level.getBlockState(above).getCollisionShape(level, above).isEmpty()
              && spaced(seat.target().pos(), reserved)
          || seat == null && safe(pf, anchor.pos(), reserved)) {
        spots.put(anchor.name(), anchor);
        reserved.add(anchor.pos());
      }
    }
    if (anchors.isEmpty()) return spots;
    int minX = (int) Math.floor(anchors.stream().mapToDouble(Anchor::x).min().orElseThrow()) - 2;
    int maxX = (int) Math.floor(anchors.stream().mapToDouble(Anchor::x).max().orElseThrow()) + 2;
    int minZ = (int) Math.floor(anchors.stream().mapToDouble(Anchor::z).min().orElseThrow()) - 2;
    int maxZ = (int) Math.floor(anchors.stream().mapToDouble(Anchor::z).max().orElseThrow()) + 2;
    int y = (int) Math.floor(anchors.getFirst().y());
    double cx = (minX + maxX + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;
    for (int z = minZ; z <= maxZ; z += 2)
      for (int x = minX; x <= maxX; x += 2) {
        double floor = pf.floor(x, y, z);
        if (Double.isNaN(floor)) continue;
        Vec3 pos = new Vec3(x + .5, floor, z + .5);
        if (!safe(pf, pos, reserved)) continue;
        String name = station + "_standing_" + x + "_" + z;
        float yaw = (float) Math.toDegrees(Math.atan2(-(cx - pos.x), cz - pos.z));
        spots.put(name, new Anchor(name, pos.x, pos.y, pos.z, yaw, 0));
        reserved.add(pos);
      }
    return spots;
  }

  private static boolean spaced(Vec3 at, List<Vec3> occupied) {
    return occupied.stream().noneMatch(p -> p.distanceToSqr(at) < 1.5 * 1.5);
  }

  private static boolean safe(GridPathfinder pf, Vec3 at, List<Vec3> occupied) {
    if (!pf.clear(at, at)
        || Double.isNaN(
            pf.floor((int) Math.floor(at.x), (int) Math.floor(at.y + .05), (int) Math.floor(at.z))))
      return false;
    return spaced(at, occupied);
  }

  private static @Nullable Anchor freeSpot(
      ClientLevel lvl, GridPathfinder pf, Anchors.Layout layout, String id, List<Vec3> occupied) {
    var b = layout.bounds();
    BlockPos spawn = lvl.getRespawnData().pos();
    int minX = b == null ? spawn.getX() - 16 : b.minX(),
        maxX = b == null ? spawn.getX() + 16 : b.maxX();
    int minZ = b == null ? spawn.getZ() - 16 : b.minZ(),
        maxZ = b == null ? spawn.getZ() + 16 : b.maxZ();
    int y = b == null ? spawn.getY() : b.minY() + 1;
    for (int z = minZ; z <= maxZ; z += 2)
      for (int x = minX; x <= maxX; x += 2)
        for (int dy : new int[] {0, -1, 1}) {
          double floor = pf.floor(x, y + dy, z);
          if (Double.isNaN(floor)) continue;
          Vec3 pos = new Vec3(x + .5, floor, z + .5);
          if (safe(pf, pos, occupied)) return new Anchor("free_" + id, pos.x, pos.y, pos.z, 180, 0);
        }
    return null;
  }

  private static void place(ClientAgentEntity e, Anchor target) {
    Vec3 p = e.motion().placeAt(target);
    e.snapTo(p, target.yaw());
  }

  private void retarget(
      GridPathfinder pf, ClientAgentEntity e, Anchor target, Seats.@Nullable Seat seat) {
    Anchor current = e.motion().target();
    if (current != null
        && current.name().equals(target.name())
        && current.pos().distanceToSqr(target.pos()) < 1e-4) {
      return;
    }
    AgentLife life = e.life();
    List<Vec3> route = new ArrayList<>();
    Vec3 start = e.position();
    int delay = life.waveRemaining();
    Seats.Seat from = life.seat();
    if (from != null && life.sitAmount() > 0f && !e.motion().walking()) {
      // get up first, then step out of the seat to its free side
      delay = Math.max(delay, STAND_UP_TICKS);
      if (from.approach() != null) {
        route.add(start);
        start = from.approach();
      }
    }
    Vec3 dest = seat != null && seat.approach() != null ? seat.approach() : target.pos();
    List<Vec3> path =
        start.distanceToSqr(dest) < 1e-6 ? List.of(start, dest) : pf.find(start, dest);
    if (path == null || length(path) > MAX_WALK) {
      pathFailures++;
      ToyBoxMinecraft.LOGGER.info(
          "Agent {}: no walkable route to {} ({}), teleporting",
          e.agentId(),
          target.name(),
          path == null ? "no path" : "too far");
      life.setSeat(seat);
      place(e, target);
      return;
    }
    route.addAll(path);
    if (seat != null && seat.approach() != null) {
      route.add(target.pos()); // the last step: onto the seat
    }
    life.setSeat(seat);
    e.motion().walkTo(target, route, delay);
  }

  private static double length(List<Vec3> route) {
    double d = 0;
    for (int i = 1; i < route.size(); i++) {
      d += route.get(i).distanceTo(route.get(i - 1));
    }
    return d;
  }

  /** Snap every agent to its target now (QA: no one mid-walk in a screenshot). */
  public int settle() {
    int n = 0;
    for (ClientAgentEntity e : entities.values()) {
      Anchor t = e.motion().target();
      if (t != null && e.motion().walking()) {
        place(e, t);
        n++;
      }
    }
    return n;
  }

  private void removeAll() {
    if (level != null) {
      for (ClientAgentEntity e : entities.values()) {
        remove(level, e);
      }
    }
    entities.clear();
  }

  private static void remove(ClientLevel lvl, ClientAgentEntity e) {
    lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
  }
}
