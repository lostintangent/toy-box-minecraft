package dev.toybox.minecraft.client.dev;

import dev.toybox.minecraft.block.ModItems;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * {@code dev.screen {open:"creative_toybox_minecraft"}}: the creative inventory on the
 * ToyBoxMinecraft tab (QA of block items).
 */
public final class ItemsDev {
  private ItemsDev() {}

  public static void init() {
    DevBridge.registerScreen(
        "creative_toybox_minecraft",
        mc -> {
          if (mc.player == null || mc.getConnection() == null) {
            throw new DevBridge.DevException("not in a world yet");
          }
          CreativeModeInventoryScreen screen =
              new CreativeModeInventoryScreen(
                  mc.player,
                  mc.player.connection.enabledFeatures(),
                  mc.options.operatorItemsTab().get());
          FrameScheduler.afterFrames(1)
              .thenRun(
                  () ->
                      BuiltInRegistries.CREATIVE_MODE_TAB
                          .get(ModItems.TAB)
                          .ifPresent(
                              tab ->
                                  ((FabricCreativeModeInventoryScreen) screen)
                                      .setSelectedTab(tab.value())));
          return screen;
        });
  }
}
