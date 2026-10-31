package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.world.layout.Anchor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic walking for one client-side agent: follows a path (from {@link GridPathfinder}) to
 * its target anchor at a calm walking pace, turning smoothly, then turns to the anchor's yaw. No
 * physics, no collisions, no server: the position is a pure function of the path and time, so it is
 * identical every run (good for QA) and never jitters.
 *
 * <p>Pacing: agents ease into their stride over a few steps, slow down over the last block before a
 * spot (the vanilla walk cycle follows the real distance moved, so the legs slow down too), and
 * keep a personal pace ({@link #setPace}, 0.9-1.07 of {@link #SPEED}). A route can start after a
 * short delay ({@link #walkTo(Anchor, List, int)}): the time it takes to stand up from a seat.
 */
public final class AgentMotion {
  /**
   * Walking speed in blocks per tick (2.9 blocks/s: an unhurried office walk; players sprint 5.6).
   */
  public static final double SPEED = 0.145;

  /** Max body turn per tick, degrees. */
  public static final float TURN = 24f;

  /** Ticks from standing still to full stride. */
  private static final int ACCEL_TICKS = 7;

  /** Distance (blocks) over which an agent slows down before arriving. */
  private static final double ARRIVE = 1.1;

  /** Slowest arrival speed as a fraction of the stride. */
  private static final double ARRIVE_MIN = 0.32;

  private @Nullable Anchor target;
  private final List<Vec3> path = new ArrayList<>();
  private int next;
  private boolean walking;
  private float yaw;
  private long teleports;
  private double pace = 1.0;
  private double speed;
  private int delay;
  private @Nullable Float facing;

  public @Nullable Anchor target() {
    return target;
  }

  public boolean walking() {
    return walking;
  }

  /** Walking or about to (waiting out the start delay). */
  public boolean leaving() {
    return walking && delay > 0;
  }

  public float yaw() {
    return yaw;
  }

  /** Counts every {@link #placeAt} (snap): listeners use it to snap their animations too. */
  public long teleports() {
    return teleports;
  }

  /**
   * While standing, turn towards {@code yaw} instead of the target's yaw (null = the target's): a
   * waiting agent faces you, a listener turns to the speaker.
   */
  public void faceTowards(@Nullable Float yaw) {
    facing = yaw;
  }

  /** Personal pace factor (1 = {@link #SPEED}). */
  public void setPace(double pace) {
    this.pace = Math.max(0.7, Math.min(1.3, pace));
  }

  /** Remaining path points (for debugging). */
  public List<Vec3> remainingPath() {
    return next < path.size() ? List.copyOf(path.subList(next, path.size())) : List.of();
  }

  /** The point the agent is heading for right now (null when standing). */
  public @Nullable Vec3 nextPoint() {
    return walking && next < path.size() ? path.get(next) : null;
  }

  /** Put the agent at {@code a} right away (first appearance, no path, or a settle request). */
  public Vec3 placeAt(Anchor a) {
    target = a;
    path.clear();
    next = 0;
    walking = false;
    yaw = a.yaw();
    teleports++;
    speed = 0;
    delay = 0;
    facing = null;
    return a.pos();
  }

  /** Walk a checked route after allowing time to stand up from a seat. */
  public void walkTo(Anchor a, List<Vec3> route, int delayTicks) {
    boolean was = walking;
    target = a;
    path.clear();
    path.addAll(route);
    next = path.size() > 1 ? 1 : path.size();
    walking = next < path.size();
    delay = was ? 0 : Math.max(0, delayTicks);
    if (!was) {
      speed = 0;
    }
  }

  /**
   * Advance one tick from {@code pos}. Returns the new feet position; {@link #yaw()} is the new
   * body yaw.
   */
  public Vec3 step(Vec3 pos) {
    if (target == null) {
      return pos;
    }
    if (!walking) {
      speed = 0;
      Float f = facing;
      yaw =
          Mth.approachDegrees(
              yaw, f != null ? f : target.yaw(), f != null ? TURN * 0.4f : TURN * 0.6f);
      return pos;
    }
    if (delay > 0) {
      delay--;
      return pos;
    }
    double stride = SPEED * pace;
    double remaining = remaining(pos);
    double want = stride;
    if (remaining < ARRIVE) {
      // ease into the spot: smoothstep from full stride down to a slow last step
      double t = remaining / ARRIVE;
      want = stride * (ARRIVE_MIN + (1 - ARRIVE_MIN) * t * t * (3 - 2 * t));
    }
    speed = Math.min(want, speed + stride / ACCEL_TICKS);
    speed = Math.max(speed, stride * 0.18);
    double budget = speed;
    Vec3 p = pos;
    while (budget > 1e-6 && next < path.size()) {
      Vec3 goal = path.get(next);
      Vec3 d = goal.subtract(p);
      double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
      if (horiz > 1e-4) {
        float want2 = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float diff = Math.abs(Mth.wrapDegrees(want2 - yaw));
        // the last short step onto a seat/spot may be taken sideways (no full turn to face it)
        boolean lastStep = next == path.size() - 1 && horiz < 1.25;
        if (!(lastStep && diff > 60)) {
          yaw = Mth.approachDegrees(yaw, want2, TURN);
        } else {
          yaw = Mth.approachDegrees(yaw, target.yaw(), TURN * 0.6f);
        }
        if (diff > 100 && !lastStep) {
          // turn on the spot first instead of walking backwards
          speed = 0;
          break;
        }
      }
      double dist = d.length();
      if (dist <= budget) {
        p = goal;
        budget -= dist;
        next++;
      } else {
        p = p.add(d.scale(budget / dist));
        budget = 0;
      }
    }
    if (next >= path.size()) {
      walking = false;
    }
    return p;
  }

  /** Path length still to walk from {@code pos}. */
  private double remaining(Vec3 pos) {
    double d = 0;
    Vec3 p = pos;
    for (int i = next; i < path.size(); i++) {
      Vec3 q = path.get(i);
      double dx = q.x - p.x;
      double dz = q.z - p.z;
      d += Math.sqrt(dx * dx + dz * dz);
      p = q;
      if (d > ARRIVE) {
        break;
      }
    }
    return d;
  }
}
