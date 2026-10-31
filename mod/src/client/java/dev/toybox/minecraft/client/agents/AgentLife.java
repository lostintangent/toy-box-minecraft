package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.world.layout.Anchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What makes one agent look alive: its posture (typing, reading, waiting, talking), a head that
 * looks at what matters (the monitor, the book, whoever it talks to, you when you come close or
 * when it needs you), idle micro-animations (glances at neighbours, an occasional stretch), state
 * particles ({@link AgentParticles}) and its speech bubble ({@link SpeechBubble}).
 *
 * <p>Simulated per client tick (20 Hz, deterministic per agent: a seeded generator, no wall clock)
 * and interpolated per frame in {@link #extract}. Nothing here allocates per frame. The pose is a
 * small vector of channels ({@code P_*}): arm and leg rotations of the vanilla player model
 * (radians, model space: -x is the agent's right, -z forward), a forward lean, the weight of the
 * custom arm pose against the vanilla walk swing, and the held book. {@link AgentModel} applies it.
 */
public final class AgentLife {
  public static final int P_RAX = 0, P_RAY = 1, P_RAZ = 2, P_LAX = 3, P_LAY = 4, P_LAZ = 5;
  public static final int P_RLX = 6, P_RLY = 7, P_RLZ = 8, P_LLX = 9, P_LLY = 10, P_LLZ = 11;
  public static final int P_LEAN = 12, P_ARMW = 13, P_LEGW = 14, P_BOOK = 15;
  public static final int N = 16;

  /** What the body is doing (picked every tick). */
  public enum Posture {
    WALK,
    IDLE,
    SIT_IDLE,
    SIT_TYPE,
    READ,
    TYPE_STAND,
    WAIT,
    STRETCH,
    TALK
  }

  /** Eye height of the agent model (player eye 1.62 x model scale 0.9375). */
  static final double EYE = 1.52;

  /** Ticks to sit down / stand up. */
  private static final float SIT_STEP = 1f / 8f;

  /** Pose smoothing per tick (fraction of the remaining distance). */
  private static final float POSE_RATE = 0.28f;

  /** The player counts as "near" (agents glance at you) within this distance. */
  private static final double NEAR = 4.2;

  private final ClientAgentEntity e;
  private final RandomSource random;
  final float[] cur = new float[N];
  final float[] prev = new float[N];
  final float[] tgt = new float[N];

  /** Interpolated pose for the current frame (written by {@link #extract}). */
  final float[] frame = new float[N];

  private float sit;
  private float sitPrev;
  private float typeW;
  private float typeWPrev;
  private float talkW;
  private float talkWPrev;
  private float exclaim;
  private float exclaimPrev;
  private Posture posture = Posture.IDLE;
  private Seats.@Nullable Seat seat;

  /** Vertical model offset of the seat the agent sits on / last sat on (blocks). */
  private double drop;

  private float headYaw;
  private float headPitch;
  private long lastTeleports = -1;
  private int age;
  private int emergeUntil, waveUntil, carryUntil;

  void arrive() {
    emergeUntil = age + 24;
  }

  boolean emerging() {
    return age < emergeUntil;
  }

  void wave() {
    emergeUntil = 0;
    waveUntil = age + 30;
  }

  int waveRemaining() {
    return Math.max(0, waveUntil - age);
  }

  void carryBook(boolean value) {
    carryUntil = value ? age + 500 : 0;
  }

  boolean carryingBook() {
    return age < carryUntil;
  }

  // idle behaviour
  private int nextGlance;
  private int glanceUntil;
  private float glanceYaw;
  private float glancePitch;
  private int glanceAt = Integer.MIN_VALUE;
  private int nextStretch;
  private int stretchUntil;
  private int typingUntil;
  private int typingPauseUntil;
  private int nextPageFlip;
  private int pageFlipStart = -100;

  /** "Oh, hi": a busy agent looks up at you for a moment when you come near, then back to work. */
  private int greetUntil;

  private int nextGreet;
  private boolean playerWasNear;
  // conversation
  final SpeechBubble bubble = new SpeechBubble();
  private @Nullable String listenTo;
  private int listenUntil;
  // state reactions
  private int lastConfetti = -1000;
  private int nextMote;
  private boolean confettiPending;
  private int reactionUntil;

  AgentLife(ClientAgentEntity e) {
    this.e = e;
    long h = 0x9E3779B97F4A7C15L;
    for (char c : e.agentId().toCharArray()) {
      h = (h ^ c) * 0x100000001B3L;
    }
    random = RandomSource.create(h);
    nextGlance = 40 + random.nextInt(80);
    nextStretch = 200 + random.nextInt(600);
    nextPageFlip = 60 + random.nextInt(120);
    e.motion().setPace(0.92 + random.nextFloat() * 0.14);
  }

  // ---------------------------------------------------------------- accessors

  public Posture posture() {
    return posture;
  }

  public Seats.@Nullable Seat seat() {
    return seat;
  }

  /** Seated amount 0..1 (this tick). */
  public float sitAmount() {
    return sit;
  }

  public boolean seated() {
    return seat != null && sit > 0.5f;
  }

  /**
   * Set by {@link AgentManager} with the seat at the agent's current target (null = stand there).
   */
  void setSeat(Seats.@Nullable Seat s) {
    seat = s;
  }

  /** Is the agent at (or settling into) its seat right now? */
  private boolean atSeat() {
    Seats.Seat s = seat;
    if (s == null || e.motion().walking()) {
      return false;
    }
    double dx = e.getX() - s.sit().x;
    double dz = e.getZ() - s.sit().z;
    return dx * dx + dz * dz < 0.02;
  }

  // ---------------------------------------------------------------- events (client thread)

  void onSay(String text, @Nullable String to) {
    bubble.show(new SpeechBubble.Line(text, to), age);
  }

  /** A brief public acknowledgement, without adding words to the conversation. */
  void onReaction(String reaction) {
    reactionUntil = age + 28;
    switch (reaction) {
      case "done", "celebrate" -> confettiPending = true;
      case "love" -> {
        for (int i = 0; i < 3; i++)
          e.level()
              .addParticle(
                  net.minecraft.core.particles.ParticleTypes.HEART,
                  e.getX() + (random.nextFloat() * 2f - 1f) * .35,
                  e.getY() + EYE + sitOffset() + .2,
                  e.getZ() + (random.nextFloat() * 2f - 1f) * .35,
                  0,
                  .06,
                  0);
      }
      default -> {
        for (int i = 0; i < 3; i++) AgentParticles.sparkle(e, EYE + .15 + sitOffset(), random);
      }
    }
  }

  /** Another agent talks to this one: look at them while they talk. */
  void listen(String speakerId, int ticks) {
    listenTo = speakerId;
    listenUntil = age + ticks;
  }

  /** QA preview of an effect (dev.agents.fx). */
  boolean preview(String fx, String text, @Nullable String to) {
    switch (fx) {
      case "reaction" -> onReaction(text);
      case "confetti" -> AgentParticles.confetti(e, EYE - 0.15 + sitOffset(), random);
      case "sparkle" -> {
        for (int i = 0; i < 4; i++) {
          AgentParticles.sparkle(e, EYE + 0.15 + sitOffset(), random);
        }
      }
      case "say" -> onSay(text, to);
      default -> {
        return false;
      }
    }
    return true;
  }

  void onTaskDone() {
    confettiPending = true;
  }

  private double sitOffset() {
    return drop * sit;
  }

  // ---------------------------------------------------------------- tick

  void tick(Minecraft mc) {
    age++;
    AgentView v = e.view();
    AgentMotion m = e.motion();
    boolean snap = m.teleports() != lastTeleports;
    lastTeleports = m.teleports();
    System.arraycopy(cur, 0, prev, 0, N);
    sitPrev = sit;
    typeWPrev = typeW;
    talkWPrev = talkW;
    exclaimPrev = exclaim;

    boolean walking = m.walking() && !m.leaving();
    boolean at = atSeat();
    if (at) {
      drop = seat.drop();
    }
    boolean seatedTarget = at && Math.abs(Mth.wrapDegrees(e.yBodyRot - seat.anchor().yaw())) < 30;
    float sitTarget = seatedTarget && !m.leaving() ? 1f : 0f;
    sit = snap ? sitTarget : Mth.approach(sit, sitTarget, SIT_STEP);

    posture = choose(v, walking, seatedTarget || sit > 0.5f);
    pose(posture, tgt);
    if (carryingBook()) {
      arms(tgt, -.98f, -.4f, 0f, -.98f, .4f, 0f);
      tgt[P_ARMW] = 1f;
      tgt[P_BOOK] = 1f;
    } else if (age < waveUntil) {
      tgt[P_ARMW] = 1f;
      tgt[P_RAX] = -2.6f;
      tgt[P_RAZ] = -.3f + .2f * Mth.sin(age * .65f);
    }
    if (sit > 0f && !posture.name().startsWith("SIT")) {
      // standing up / sitting down: legs follow the seat amount
      sitLegs(tgt, sit);
    }
    float rate = snap ? 1f : POSE_RATE;
    for (int i = 0; i < N; i++) {
      cur[i] += (tgt[i] - cur[i]) * rate;
    }
    boolean typing = posture == Posture.SIT_TYPE || posture == Posture.TYPE_STAND;
    if (typing && age >= typingPauseUntil && age >= typingUntil) {
      typingUntil = age + 30 + random.nextInt(70);
      typingPauseUntil = typingUntil + 10 + random.nextInt(30);
    }
    float typeTarget = typing && age < typingUntil ? 1f : 0f;
    typeW = snap ? typeTarget : Mth.approach(typeW, typeTarget, 0.2f);
    float talkTarget = posture == Posture.TALK ? 1f : 0f;
    talkW = snap ? talkTarget : Mth.approach(talkW, talkTarget, 0.12f);
    float exTarget = v.needsYou() ? 1f : 0f;
    exclaim = snap ? exTarget : Mth.approach(exclaim, exTarget, 0.1f);
    if (posture == Posture.READ && age >= nextPageFlip) {
      pageFlipStart = age;
      nextPageFlip = age + 90 + random.nextInt(140);
    }

    look(mc, v, walking, snap);
    bubble.tick(age);
    particlesTick(v, walking);
    if (snap) {
      System.arraycopy(cur, 0, prev, 0, N);
      sitPrev = sit;
      typeWPrev = typeW;
      talkWPrev = talkW;
      exclaimPrev = exclaim;
    }
  }

  private Posture choose(AgentView v, boolean walking, boolean seated) {
    if (walking) return Posture.WALK;
    if (v.stale) return seated ? Posture.SIT_IDLE : Posture.IDLE;
    if (seated)
      return v.state.equals("working") && seat != null && !Double.isNaN(seat.deskTop())
          ? Posture.SIT_TYPE
          : Posture.SIT_IDLE;
    if (bubble.visible()) return Posture.TALK;
    if (v.needsYou()) return Posture.WAIT;
    if (v.state.equals("waiting")) return Posture.WAIT;
    if (v.state.equals("working"))
      return v.workingOn == null && v.lookingAt != null ? Posture.READ : Posture.TYPE_STAND;
    if (age < stretchUntil) return Posture.STRETCH;
    if (--nextStretch <= 0) {
      stretchUntil = age + 34;
      nextStretch = 500 + random.nextInt(700);
      return Posture.STRETCH;
    }
    return Posture.IDLE;
  }

  /** Target pose channels for a posture (arm/leg radians, model space). */
  private void pose(Posture p, float[] t) {
    java.util.Arrays.fill(t, 0f);
    t[P_ARMW] = 1f;
    t[P_RAZ] = 0.05f;
    t[P_LAZ] = -0.05f;
    switch (p) {
      case WALK -> {
        t[P_ARMW] = 0f;
        t[P_LEAN] = 0.03f;
      }
      case IDLE -> {}
      case SIT_IDLE -> {
        arms(t, -0.55f, -0.16f, 0.05f, -0.55f, 0.16f, -0.05f);
        sitLegs(t, 1f);
      }
      case SIT_TYPE -> {
        float reach = typingReach();
        arms(t, -reach, -0.24f, 0f, -reach, 0.24f, 0f);
        sitLegs(t, 1f);
        t[P_RLX] = t[P_LLX] = -1.22f;
        t[P_LEAN] = 0.06f;
      }
      case READ -> {
        arms(t, -0.98f, -0.4f, 0f, -0.98f, 0.4f, 0f);
        t[P_BOOK] = 1f;
        t[P_LEAN] = 0.05f;
      }
      case TYPE_STAND -> {
        arms(t, -0.86f, -0.24f, 0f, -0.86f, 0.24f, 0f);
        t[P_LEAN] = 0.16f;
      }
      case WAIT -> arms(t, -0.42f, -0.46f, 0f, -0.42f, 0.46f, 0f);
      case STRETCH -> arms(t, -3.05f, 0f, -0.22f, -3.05f, 0f, 0.22f);
      case TALK -> arms(t, -0.7f, -0.22f, 0.08f, 0.02f, 0f, -0.05f);
    }
  }

  private static void arms(float[] t, float rx, float ry, float rz, float lx, float ly, float lz) {
    t[P_RAX] = rx;
    t[P_RAY] = ry;
    t[P_RAZ] = rz;
    t[P_LAX] = lx;
    t[P_LAY] = ly;
    t[P_LAZ] = lz;
  }

  private static void sitLegs(float[] t, float w) {
    t[P_RLX] = -1.34f;
    t[P_RLY] = 0.14f;
    t[P_RLZ] = 0.05f;
    t[P_LLX] = -1.34f;
    t[P_LLY] = -0.14f;
    t[P_LLZ] = -0.05f;
    t[P_LEGW] = w;
  }

  /** Arm angle (rad from hanging) that puts the hands just above the desk in front of the seat. */
  private float typingReach() {
    Seats.Seat s = seat;
    if (s == null || Double.isNaN(s.deskTop())) {
      return 1.27f;
    }
    // shoulder pivot: 22 px above the feet in model px (scale 0.9375) when standing, plus the seat
    // drop
    double shoulder = s.anchor().y() + s.drop() + (24 - 2) / 16.0 * 0.9375;
    double hand = 10 / 16.0 * 0.9375;
    double dy = shoulder - (s.deskTop() + 0.04);
    double c = Mth.clamp(dy / hand, -0.2, 0.8);
    return (float) Math.acos(c);
  }

  // ---------------------------------------------------------------- head

  private void look(Minecraft mc, AgentView v, boolean walking, boolean snap) {
    float body = e.yBodyRot;
    Vec3 eye = new Vec3(e.getX(), e.getY() + EYE + sitOffset(), e.getZ());
    float wantYaw = body;
    float wantPitch = 0f;
    boolean tracking = false;
    LocalPlayer player = mc.player;
    Vec3 playerEye = player == null ? null : player.getEyePosition();
    double playerDist = playerEye == null ? Double.MAX_VALUE : playerEye.distanceTo(eye);
    Vec3 point = null;
    boolean faceBody = false;

    if (walking) {
      // look where you go: the next corner, a little ahead of the body turn
      Vec3 n = e.motion().nextPoint();
      if (n != null) {
        point = new Vec3(n.x, eye.y - 0.25, n.z);
      }
    } else if (!v.stale) {
      SpeechBubble.Line say = bubble.visible() ? bubble.current() : null;
      if (say != null && say.to() != null) {
        point = lookPointFor(say.to());
      }
      if (point == null && listenTo != null && age < listenUntil) {
        point = lookPointFor(listenTo);
      }
      if (point == null && v.needsYou() && playerEye != null && playerDist < 24) {
        point = playerEye;
        faceBody = true;
      }
      boolean near = playerEye != null && playerDist < NEAR && inFront(eye, playerEye, body, 105);
      boolean busy = v.state.equals("working");
      if (near
          && !playerWasNear
          && busy
          && age >= nextGreet
          && player != null
          && !player.isSpectator()) {
        greetUntil = age + 40 + random.nextInt(20);
        nextGreet = age + 300 + random.nextInt(200);
      }
      playerWasNear = near;
      // idle agents keep an eye on you while you are close; busy ones look up only briefly
      // (and never at a spectating camera, so QA shots show them at work)
      if (point == null && near && (!busy || age < greetUntil)) {
        point = playerEye;
      }
      if (point == null) {
        point = stationFocus(v);
      }
      if (point == null && age >= glanceUntil && age >= nextGlance) {
        startGlance(v);
      }
      if (point == null && age < glanceUntil) {
        ClientAgentEntity other =
            glanceAt == Integer.MIN_VALUE ? null : AgentManager.get().byEntityId(glanceAt);
        if (other != null) {
          point =
              new Vec3(other.getX(), other.getY() + EYE + other.life().sitOffset(), other.getZ());
        } else {
          wantYaw = body + glanceYaw;
          wantPitch = glancePitch;
        }
      }
    }
    if (point != null) {
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double h = Math.sqrt(dx * dx + dz * dz);
      if (h > 1e-3 || Math.abs(dy) > 1e-3) {
        wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(h, 1e-3)));
        tracking = true;
      }
    } else {
      wantPitch += postureNod();
    }
    float rel = Mth.wrapDegrees(wantYaw - body);
    boolean canTurnBody =
        !walking
            && sit < 0.05f
            && (faceBody
                || posture == Posture.IDLE
                || posture == Posture.TALK
                || posture == Posture.WAIT);
    if (tracking && canTurnBody && Math.abs(rel) > (faceBody ? 25 : 70)) {
      // turn towards whoever we look at (waiting agents face you)
      e.motion().faceTowards(Mth.wrapDegrees(wantYaw));
    } else {
      e.motion().faceTowards(null);
    }
    float limit = sit > 0.5f ? 60f : 75f;
    rel = Mth.clamp(rel, -limit, limit);
    if (age < reactionUntil) wantPitch += 14f * Mth.sin((28 - reactionUntil + age) * .45f);
    wantPitch = Mth.clamp(wantPitch, -40f, 45f);
    float absWant = body + rel;
    if (snap || age <= 1) {
      headYaw = absWant;
      headPitch = wantPitch;
    } else {
      float dy = Mth.wrapDegrees(absWant - headYaw);
      float step = Mth.clamp(dy * 0.35f, -14f, 14f);
      headYaw = Mth.wrapDegrees(headYaw + step);
      headPitch += Mth.clamp((wantPitch - headPitch) * 0.3f, -9f, 9f);
    }
    // never let the head lag outside the neck's range while the body turns
    float off = Mth.clamp(Mth.wrapDegrees(headYaw - body), -limit, limit);
    headYaw = body + off;
    e.setHeadLook(headYaw, headPitch);
  }

  /** Small posture-specific head pitch when nothing in particular is looked at. */
  private float postureNod() {
    return switch (posture) {
      case READ -> 34f;
      case TYPE_STAND -> 24f;
      case SIT_TYPE -> 6f;
      case STRETCH -> -22f;
      case WAIT -> 2f;
      default -> 4f;
    };
  }

  /** The eyes of a nearby agent this one talks to or listens to. */
  private @Nullable Vec3 lookPointFor(String who) {
    ClientAgentEntity other = AgentManager.get().entity(who);
    if (other == null || other == e || other.distanceToSqr(e) > 24 * 24) {
      return null;
    }
    return new Vec3(other.getX(), other.getY() + EYE + other.life().sitOffset(), other.getZ());
  }

  /**
   * Where a busy agent looks: the monitor at its desk; the book / screen / bench below otherwise
   * (via the nod).
   */
  private @Nullable Vec3 stationFocus(AgentView v) {
    if (age < glanceUntil && (posture == Posture.SIT_TYPE || posture == Posture.SIT_IDLE)) {
      return null; // a glance away from the screen
    }
    if (v.station.equals("desk")
        && (posture == Posture.SIT_TYPE
            || posture == Posture.SIT_IDLE
            || posture == Posture.TYPE_STAND)) {
      Anchor mon = AgentManager.get().monitor(v.id);
      if (mon != null && mon.pos().distanceToSqr(e.position()) < 9) {
        return new Vec3(mon.x(), mon.y() - 0.12, mon.z());
      }
    }
    return null;
  }

  private void startGlance(AgentView v) {
    boolean busy = !v.state.equals("idle");
    nextGlance = age + (busy ? 160 + random.nextInt(240) : 70 + random.nextInt(130));
    glanceUntil = age + (busy ? 22 + random.nextInt(20) : 28 + random.nextInt(40));
    glanceAt = Integer.MIN_VALUE;
    if (random.nextFloat() < 0.55f) {
      ClientAgentEntity best = null;
      double bestD = 7 * 7;
      int skip = random.nextInt(3);
      for (ClientAgentEntity o : AgentManager.get().entities().values()) {
        if (o == e) {
          continue;
        }
        double d = o.distanceToSqr(e);
        Vec3 oe = new Vec3(o.getX(), o.getY() + EYE, o.getZ());
        if (d < bestD && inFront(e.position().add(0, EYE, 0), oe, e.yBodyRot, 100)) {
          if (skip-- > 0 && best != null) {
            continue;
          }
          best = o;
          bestD = d;
        }
      }
      if (best != null) {
        glanceAt = best.getId();
        return;
      }
    }
    float side = random.nextFloat() < 0.5f ? -1f : 1f;
    glanceYaw = side * (18f + random.nextFloat() * 30f);
    glancePitch = -6f + random.nextFloat() * 14f;
    if (busy) {
      glanceYaw *= 0.6f;
      glancePitch = -10f;
    }
  }

  private static boolean inFront(Vec3 from, Vec3 to, float bodyYaw, float maxDeg) {
    double dx = to.x - from.x;
    double dz = to.z - from.z;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    return Math.abs(Mth.wrapDegrees(yaw - bodyYaw)) <= maxDeg;
  }

  // ---------------------------------------------------------------- particles

  private void particlesTick(AgentView v, boolean walking) {
    if (confettiPending) {
      confettiPending = false;
      if (!v.stale && age - lastConfetti > 60) {
        AgentParticles.confetti(e, EYE - 0.15 + sitOffset(), random);
        lastConfetti = age;
      }
    }
    if (!v.stale && v.state.equals("working") && !walking && age >= nextMote) {
      nextMote = age + 7 + random.nextInt(8);
      AgentParticles.mote(e, workPoint(), random);
    }
  }

  /** Where working motes rise from: the hands (keyboard, book, bench), relative to the feet. */
  private Vec3 workPoint() {
    double fwd =
        switch (posture) {
          case SIT_TYPE -> 0.5;
          case READ -> 0.42;
          case TYPE_STAND -> 0.55;
          default -> 0.4;
        };
    double up =
        switch (posture) {
          case SIT_TYPE -> 1.0 + sitOffset();
          case READ -> 1.15;
          default -> 1.05;
        };
    double rad = Math.toRadians(e.yBodyRot);
    return new Vec3(-Math.sin(rad) * fwd, up, Math.cos(rad) * fwd);
  }

  // ---------------------------------------------------------------- frame

  /** Interpolate this tick's simulation into the render state (client thread, once per frame). */
  void extract(AgentRenderState s, float pt) {
    s.emerge = Mth.clamp((emergeUntil - age - pt) / 24f, 0f, 1f);
    for (int i = 0; i < N; i++) {
      frame[i] = prev[i] + (cur[i] - prev[i]) * pt;
    }
    float t = age + pt;
    float tw = typeWPrev + (typeW - typeWPrev) * pt;
    if (tw > 0.001f) {
      // typing: quick alternating taps with a slow drift between keys
      frame[P_RAX] += tw * (0.075f * Mth.sin(t * 1.9f) + 0.03f * Mth.sin(t * 0.37f));
      frame[P_LAX] += tw * (0.075f * Mth.sin(t * 1.9f + 2.2f) + 0.03f * Mth.sin(t * 0.41f + 1f));
      frame[P_RAY] += tw * 0.04f * Mth.sin(t * 0.23f);
      frame[P_LAY] += tw * 0.04f * Mth.sin(t * 0.27f + 0.5f);
    }
    float kw = talkWPrev + (talkW - talkWPrev) * pt;
    if (kw > 0.001f) {
      // talking: the right hand moves with the words
      frame[P_RAX] += kw * (0.22f * Mth.sin(t * 0.31f) + 0.08f * Mth.sin(t * 0.83f));
      frame[P_RAY] += kw * 0.1f * Mth.sin(t * 0.19f);
    }
    if (posture == Posture.STRETCH) {
      frame[P_RAZ] += 0.06f * Mth.sin(t * 0.5f);
      frame[P_LAZ] -= 0.06f * Mth.sin(t * 0.5f);
    }
    s.posePose = frame;
    s.life = this;
    float sitF = sitPrev + (sit - sitPrev) * pt;
    s.sit = sitF;
    s.sitDrop = (float) (drop * sitF);
    s.plateBase = (float) Nameplate.HEIGHT + s.sitDrop;
    s.book = frame[P_BOOK];
    float flip = (age - pageFlipStart + pt) / 12f;
    s.pageFlip = flip >= 0 && flip <= 1 ? flip : 0f;
    s.exclaim = exclaimPrev + (exclaim - exclaimPrev) * pt;
    s.bubble = bubble.visibility(age, pt);
  }

  private net.minecraft.client.model.object.book.BookModel.@Nullable State bookState;

  /**
   * The held book's model state, reused while it does not change (the values are quantised to 1/64,
   * so a steadily reading agent submits the same immutable state every frame: no allocation; a page
   * flip or opening creates a few).
   */
  net.minecraft.client.model.object.book.BookModel.State bookState(
      float openness, float flip1, float flip2) {
    float o = Math.round(openness * 64f) / 64f;
    float a = Math.round(flip1 * 64f) / 64f;
    float b = Math.round(flip2 * 64f) / 64f;
    var st = bookState;
    if (st == null || st.openness() != o || st.pageFlip1() != a || st.pageFlip2() != b) {
      st = new net.minecraft.client.model.object.book.BookModel.State(o, a, b);
      bookState = st;
    }
    return st;
  }
}
