package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.client.ui.UiStyle;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * Toy Box effect recipes; Minecraft owns particle ticking, interpolation, culling and rendering.
 */
final class AgentParticles {
  private static final int SPARK = 0, MOTE = 1, CONFETTI = 2;

  private AgentParticles() {}

  private static TextureAtlasSprite sprite(String name) {
    return Minecraft.getInstance()
        .getAtlasManager()
        .getAtlasOrThrow(AtlasIds.PARTICLES)
        .getSprite(Identifier.withDefaultNamespace(name));
  }

  private static Effect spawn(
      ClientAgentEntity owner,
      int kind,
      double x,
      double y,
      double z,
      float vx,
      float vy,
      float vz,
      int lifetime,
      float size,
      int color) {
    Effect effect = new Effect(owner, kind, x, y, z, lifetime, size, color);
    effect.setParticleSpeed(vx, vy, vz);
    Minecraft.getInstance().particleEngine.add(effect);
    return effect;
  }

  /** One twinkle around the head for arrivals and reactions. */
  static void sparkle(ClientAgentEntity e, double height, RandomSource random) {
    double a = random.nextFloat() * Math.PI * 2;
    double rad = 0.34 + random.nextFloat() * 0.2;
    double px = e.getX() + Math.cos(a) * rad;
    double pz = e.getZ() + Math.sin(a) * rad;
    double py = e.getY() + height + (random.nextFloat() * 2f - 1f) * 0.2;
    float tang = 0.004f;
    spawn(
        e,
        SPARK,
        px,
        py,
        pz,
        (float) -Math.sin(a) * tang,
        0.006f + random.nextFloat() * 0.004f,
        (float) Math.cos(a) * tang,
        24 + random.nextInt(10),
        0.12f + random.nextFloat() * 0.05f,
        ARGB.srgbLerp(0.25f, UiStyle.BRASS, UiStyle.CREAM));
  }

  /** Working: a small teal mote rising from the hands ({@code at} is relative to the feet). */
  static void mote(ClientAgentEntity e, net.minecraft.world.phys.Vec3 at, RandomSource random) {
    double rad = Math.toRadians(e.yBodyRot);
    double sx = Math.cos(rad);
    double sz = Math.sin(rad);
    double side = (random.nextFloat() * 2f - 1f) * 0.22;
    double px = e.getX() + at.x + sx * side;
    double pz = e.getZ() + at.z + sz * side;
    double py = e.getY() + at.y + random.nextFloat() * 0.08;
    float fx = (float) (-Math.sin(rad) * 0.003);
    float fz = (float) (Math.cos(rad) * 0.003);
    spawn(
        e,
        MOTE,
        px,
        py,
        pz,
        fx,
        0.013f + random.nextFloat() * 0.007f,
        fz,
        18 + random.nextInt(10),
        0.065f + random.nextFloat() * 0.025f,
        ARGB.srgbLerp(0.2f, UiStyle.status("working"), UiStyle.CREAM));
  }

  /** Done: confetti flakes in the palette, thrown up and fluttering down. */
  static void confetti(ClientAgentEntity e, double height, RandomSource random) {
    int[] colors = {
      UiStyle.SAGE, UiStyle.CREAM, UiStyle.BRASS, UiStyle.CLAY, UiStyle.SAGE, UiStyle.TEAL
    };
    for (int k = 0; k < 18; k++) {
      double a = random.nextFloat() * Math.PI * 2;
      // burst outwards around the shoulders, staying below the nameplate (no flakes over its text)
      float out = 0.05f + random.nextFloat() * 0.045f;
      double px = e.getX() + Math.cos(a) * 0.15;
      double pz = e.getZ() + Math.sin(a) * 0.15;
      double py = e.getY() + height;
      Effect effect =
          spawn(
              e,
              CONFETTI,
              px,
              py,
              pz,
              (float) Math.cos(a) * out,
              0.06f + random.nextFloat() * 0.045f,
              (float) Math.sin(a) * out,
              34 + random.nextInt(18),
              0.075f + random.nextFloat() * 0.03f,
              colors[k % colors.length]);
      effect.confetti((random.nextFloat() * 2f - 1f) * 0.35f, random.nextFloat() * 6.28f);
    }
  }

  /** Only the Toy Box-specific twinkle, fade and paper flutter extend vanilla quad particles. */
  private static final class Effect extends SingleQuadParticle {
    private final ClientAgentEntity owner;
    private final int kind;
    private final TextureAtlasSprite small;
    private float spin;

    Effect(
        ClientAgentEntity owner,
        int kind,
        double x,
        double y,
        double z,
        int lifetime,
        float size,
        int color) {
      super(
          (ClientLevel) owner.level(),
          x,
          y,
          z,
          sprite(kind == SPARK ? "glow" : kind == MOTE ? "glitter_1" : "glitter_2"));
      this.owner = owner;
      this.kind = kind;
      this.lifetime = lifetime;
      quadSize = size;
      hasPhysics = false;
      friction = kind == SPARK ? .99f : kind == MOTE ? .97f : .935f;
      setColor(ARGB.red(color) / 255f, ARGB.green(color) / 255f, ARGB.blue(color) / 255f);
      small = kind == MOTE ? sprite("glitter_0") : sprite;
    }

    void confetti(float spin, float rotation) {
      gravity = 0.0085f / 0.04f;
      this.spin = spin;
      roll = oRoll = rotation;
    }

    @Override
    public void tick() {
      if (owner.isRemoved()) {
        remove();
        return;
      }
      oRoll = roll;
      super.tick();
      roll += spin;
      if (kind == CONFETTI) {
        float flutter = Mth.sin((age + lifetime) * 0.45f) * 0.004f;
        xd += flutter;
        zd -= flutter * 0.6f;
        yd = Math.max(yd, -0.025f);
      }
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
      if (owner.isRemoved() || owner.position().distanceToSqr(camera.position()) >= 48 * 48) return;
      float t = Math.min(1f, (age + partialTick) / lifetime);
      alpha =
          switch (kind) {
            case SPARK -> Math.min(1f, Mth.sin(t * Mth.PI) * 1.6f);
            case MOTE -> t < .15f ? t / .15f : 1f - (t - .15f) / .85f * .9f;
            default -> t > .75f ? (1f - t) / .25f : 1f;
          };
      if (kind == MOTE && t >= .55f) sprite = small;
      super.extract(state, camera, partialTick);
    }

    @Override
    public float getQuadSize(float partialTick) {
      float t = Math.min(1f, (age + partialTick) / lifetime);
      return quadSize
          * switch (kind) {
            case SPARK -> .35f + .65f * Mth.sin(t * Mth.PI);
            case MOTE -> 1f - .45f * t;
            default -> 1f;
          };
    }

    @Override
    protected void extractRotatedQuad(
        QuadParticleRenderState state, Camera camera, Quaternionf rotation, float partialTick) {
      if (kind == CONFETTI) {
        float angle = Mth.lerp(partialTick, oRoll, roll);
        rotation.rotateX((float) Math.acos(.45f + .55f * Math.abs(Mth.sin(angle * 1.7f))));
      }
      super.extractRotatedQuad(state, camera, rotation, partialTick);
    }

    @Override
    protected int getLightCoords(float partialTick) {
      return kind == CONFETTI ? super.getLightCoords(partialTick) : LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    protected Layer getLayer() {
      return Layer.TRANSLUCENT;
    }
  }
}
