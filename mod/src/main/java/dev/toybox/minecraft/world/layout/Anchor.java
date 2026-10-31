package dev.toybox.minecraft.world.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A named world position and orientation. For agent spots ({@code desk_bay_0}, {@code lounge}, ...)
 * {@code x,y,z} is where an agent's <b>feet</b> stand and {@code yaw} is the way it faces there.
 * For camera points ({@code cam_*}) it is the <b>eye</b> position and {@code yaw/pitch} the view
 * direction. For block anchors ({@code monitor_bay_0}, {@code task_board}, ...) it is the centre of
 * the surface and {@code yaw} the direction its front faces.
 *
 * <p>Yaw follows Minecraft: 0 = facing +Z (south), 90 = -X (west), 180 = -Z (north), -90 = +X
 * (east).
 */
public record Anchor(String name, double x, double y, double z, float yaw, float pitch) {
  public Vec3 pos() {
    return new Vec3(x, y, z);
  }

  public BlockPos blockPos() {
    return BlockPos.containing(x, y, z);
  }
}
