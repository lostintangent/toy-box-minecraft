package dev.toybox.minecraft.client.mixin;

import dev.toybox.minecraft.client.dev.FrameScheduler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  /** End of every rendered frame (after present): DevBridge frame waits + captures. */
  @Inject(method = "renderFrame", at = @At("TAIL"))
  private void toybox_minecraft$frameEnd(boolean advanceGameTime, CallbackInfo ci) {
    FrameScheduler.onFrameEnd();
  }
}
