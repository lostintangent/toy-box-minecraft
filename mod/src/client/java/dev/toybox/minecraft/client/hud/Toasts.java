package dev.toybox.minecraft.client.hud;

import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.ui.*;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Short live Channel moments. Reconnect never replays these. */
public final class Toasts implements HudElement {
  private record Notice(String text, long until) {}

  private static final Deque<Notice> notices = new ArrayDeque<>();

  public static void init() {
    ChannelClient.store()
        .addListener(
            new ChannelStore.Listener() {
              @Override
              public void onState(ChannelModel.State s, List<ChannelChanges.Change> changes) {
                for (var change : changes) {
                  String text =
                      switch (change) {
                        case ChannelChanges.MemberJoined e ->
                            e.member().name() + " joined the channel";
                        case ChannelChanges.MemberLeft e -> e.member().name() + " left the channel";
                        case ChannelChanges.TasksCompleted e -> "The team completed its task list";
                        case ChannelChanges.ArtifactShared e ->
                            "Artifact shared: " + e.artifact().title();
                        case ChannelChanges.RequestOpened e ->
                            "The lead requests your reply · " + Keys.label(Keys.requests);
                        case ChannelChanges.RequestAcknowledged e -> "Your reply reached the lead";
                        case ChannelChanges.ReactionChanged e ->
                            e.current() == null
                                ? null
                                : ChannelText.agentName(e.agentId(), s)
                                    + " reacted "
                                    + ChannelText.reaction(e.current());
                        default -> null;
                      };
                  if (text != null) {
                    notices.addLast(new Notice(text, System.currentTimeMillis() + 6000));
                    while (notices.size() > 3) notices.removeFirst();
                  }
                }
              }

              @Override
              public void onCleared() {
                notices.clear();
              }
            });
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
    while (!notices.isEmpty() && notices.getFirst().until() < System.currentTimeMillis())
      notices.removeFirst();
    var font = Minecraft.getInstance().font;
    int y = 62;
    for (Notice n : notices) {
      int w = Math.min(300, font.width(n.text()) + 20), x = (g.guiWidth() - w) / 2;
      Panels.panel(g, x, y, w, 24);
      g.text(font, TextUtil.ellipsize(font, n.text(), w - 16), x + 8, y + 8, UiStyle.INK, false);
      y += 28;
    }
  }
}
