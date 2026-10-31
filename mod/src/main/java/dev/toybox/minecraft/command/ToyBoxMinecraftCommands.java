package dev.toybox.minecraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.toybox.minecraft.world.layout.Anchor;
import dev.toybox.minecraft.world.layout.Anchors;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * The {@code /toybox_minecraft} command root. Features add their own sub-commands with {@link
 * #sub(Consumer)} from their {@code init()}, so nobody has to edit this file:
 *
 * <pre>
 * ToyBoxMinecraftCommands.sub(root -> root.then(Commands.literal("hq").executes(ctx -> ...)));
 * </pre>
 *
 * Built in: {@code /toybox_minecraft anchors} lists the published anchors.
 */
public final class ToyBoxMinecraftCommands {
  private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> SUBS =
      new CopyOnWriteArrayList<>();

  private ToyBoxMinecraftCommands() {}

  public static void sub(Consumer<LiteralArgumentBuilder<CommandSourceStack>> contributor) {
    SUBS.add(contributor);
  }

  public static void init() {
    sub(
        root ->
            root.then(
                Commands.literal("anchors")
                    .executes(
                        ctx -> {
                          Anchors.Layout layout = Anchors.current();
                          ctx.getSource()
                              .sendSuccess(
                                  () ->
                                      Component.literal(
                                          "Layout '"
                                              + layout.name()
                                              + "' rev "
                                              + layout.revision()
                                              + ": "
                                              + layout.anchors().size()
                                              + " anchors"),
                                  false);
                          for (Anchor a : layout.anchors().values()) {
                            ctx.getSource()
                                .sendSuccess(
                                    () ->
                                        Component.literal(
                                            String.format(
                                                Locale.ROOT,
                                                "  %s  %.2f %.2f %.2f  yaw %.0f pitch %.0f",
                                                a.name(),
                                                a.x(),
                                                a.y(),
                                                a.z(),
                                                a.yaw(),
                                                a.pitch())),
                                    false);
                          }
                          return layout.anchors().size();
                        })));
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, buildContext, selection) -> {
          LiteralArgumentBuilder<CommandSourceStack> root =
              Commands.literal("toybox_minecraft")
                  .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
          for (var s : SUBS) {
            s.accept(root);
          }
          dispatcher.register(root);
        });
  }
}
