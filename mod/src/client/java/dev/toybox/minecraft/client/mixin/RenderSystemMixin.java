package dev.toybox.minecraft.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.toybox.minecraft.ToyBoxMinecraft;
import dev.toybox.minecraft.client.ClientEnv;
import org.lwjgl.sdl.SDLHints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets SDL hints before SDL is initialised (26.3 uses SDL3 for windowing/input, not GLFW). By
 * default the window is shown WITHOUT activating it, so a dev launch never steals focus. Set
 * TOY_BOX_FOCUS=1 to get the normal "come to front" behaviour.
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
  @Inject(method = "initBackendSystem", at = @At("HEAD"), remap = false)
  private static void toybox_minecraft$sdlHints(CallbackInfoReturnable<?> cir) {
    if (!ClientEnv.TAKE_FOCUS) {
      SDLHints.SDL_SetHint("SDL_WINDOW_ACTIVATE_WHEN_SHOWN", "0");
      SDLHints.SDL_SetHint("SDL_WINDOW_ACTIVATE_WHEN_RAISED", "0");
      SDLHints.SDL_SetHint("SDL_FORCE_RAISEWINDOW", "0");
      ToyBoxMinecraft.LOGGER.info(
          "Window will open without taking focus (TOY_BOX_FOCUS=1 to change)");
    }
  }
}
