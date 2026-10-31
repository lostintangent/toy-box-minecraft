package dev.toybox.minecraft;

import dev.toybox.minecraft.block.ModBlocks;
import dev.toybox.minecraft.block.ModItems;
import dev.toybox.minecraft.block.entity.ModBlockEntities;
import dev.toybox.minecraft.command.ToyBoxMinecraftCommands;
import dev.toybox.minecraft.entity.ModEntities;
import dev.toybox.minecraft.world.WorldFeature;
import dev.toybox.minecraft.world.WorldRules;
import dev.toybox.minecraft.world.layout.Anchors;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both sides) entrypoint: registries (blocks, block entities, items + creative tab, the
 * agent entity type), the Toy Box world rules, the anchor registry and the {@code /toybox_minecraft}
 * command. Client features are wired in {@code dev.toybox.minecraft.client.ClientFeatures}.
 */
public class ToyBoxMinecraft implements ModInitializer {
  public static final String MOD_ID = "toybox_minecraft";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitialize() {
    ModBlocks.init();
    ModBlockEntities.init();
    ModItems.init();
    ModEntities.init();
    WorldRules.init();
    Anchors.init();
    ToyBoxMinecraftCommands.init();
    WorldFeature.init();
    LOGGER.info("Toy Box Minecraft initialized ({} blocks)", ModBlocks.all().size());
  }

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
  }
}
