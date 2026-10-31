package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.agents.AgentSkins;
import dev.toybox.minecraft.client.agents.AvatarArt;
import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.channel.ChannelModel.*;
import dev.toybox.minecraft.client.images.ChannelImages;
import dev.toybox.minecraft.client.ui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.minecraft.util.FormattedCharSequence;

/** The public Channel transcript and one ordinary message composer. */
public final class TranscriptScreen extends ChannelScreen {
  private static final Map<String, MessageDraft> drafts = new HashMap<>();
  private final MessageDraft draft;
  private long draftRevision;
  private MessageInput input;
  private final String channelId;
  private final String agentId;
  private boolean marking;
  private long revision = -1, renderedThrough, acknowledged, retryReadAt;
  private int cachedWidth = -1;
  private final long openedAt = System.currentTimeMillis();
  private List<Entry> rows = List.of();
  private final Long unreadAfter;

  private record Chip(Reaction reaction, String label, int x, int y, int width) {}

  private record MessageLayout(
      Message message, List<FormattedCharSequence> lines, List<Chip> chips, int height) {}

  private record Entry(TranscriptRows.Row row, int top, int height, List<MessageLayout> messages) {}

  public TranscriptScreen(String prefill) {
    this(prefill, null);
  }

  public TranscriptScreen(String prefill, String agentId) {
    super(Page.TRANSCRIPT, "Channel transcript");
    this.agentId = agentId;
    channelId = ChannelClient.config().channelId();
    var channel = ChannelClient.store().channel();
    unreadAfter =
        channel != null && channel.seenThrough() < channel.latestSequence()
            ? channel.seenThrough()
            : null;
    String key = ChannelClient.config().server() + "\u0000" + channelId;
    draft = drafts.computeIfAbsent(key, ignored -> new MessageDraft());
    draft.prefill(prefill);
    draftRevision = draft.revision();
    scroll = Double.MAX_VALUE;
  }

  @Override
  protected void setInitialFocus() {
    setInitialFocus(input);
  }

  @Override
  protected void init() {
    super.init();
    input =
        addRenderableWidget(
            new MessageInput(
                font, w - 28, bodyH < 160 ? 1 : 3, draft.value(), this::saveEdit, this::submit));
  }

  public String value() {
    syncDraft();
    return input.text.value();
  }

  public void setValue(String text) {
    input.text.setValue(text);
  }

  private void saveEdit(String value) {
    draft.edit(value);
    draftRevision = draft.revision();
  }

  private void syncDraft() {
    if (draftRevision == draft.revision()) return;
    input.text.setValue(draft.value());
    draftRevision = draft.revision();
    if (input.text.value().isEmpty()) scroll = Double.MAX_VALUE;
  }

  public void submit() {
    syncDraft();
    if (ChannelClient.store().isStale()) {
      draft.notice("Offline. Nothing was sent.");
      return;
    }
    MessageDraft.Submission submission = draft.begin();
    if (submission == null) return;
    ChannelClient.postMessage(submission.content())
        .whenComplete(
            (result, error) ->
                minecraft.execute(
                    () -> {
                      String failure =
                          error == null
                              ? result.error()
                              : "Delivery could not be confirmed. Check the channel before sending"
                                  + " again.";
                      draft.complete(submission, error == null && result.ok(), failure);
                      syncDraft();
                    }));
  }

  @Override
  public void tick() {
    syncDraft();
    var state = ChannelClient.store();
    if (agentId != null
        || state.channel() == null
        || !state.channel().id().equals(channelId)
        || state.isStale()
        || !minecraft.getWindow().isFocused()) return;
    long sequence = renderedThrough;
    if (marking
        || sequence <= Math.max(acknowledged, state.channel().seenThrough())
        || System.currentTimeMillis() < retryReadAt) return;
    marking = true;
    ChannelClient.markRead(sequence)
        .whenComplete(
            (result, error) ->
                minecraft.execute(
                    () -> {
                      marking = false;
                      if (error == null && result.ok() && result.seenThrough() != null) {
                        acknowledged = Math.max(acknowledged, result.seenThrough());
                        if (result.seenThrough() < sequence)
                          retryReadAt = System.currentTimeMillis() + 5000;
                      } else retryReadAt = System.currentTimeMillis() + 5000;
                    }));
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    syncDraft();
    var state = ChannelClient.store();
    var snapshot = state.state();
    int fieldY = bodyY + bodyH - input.getHeight();
    scrollHeight = Math.max(20, fieldY - bodyY - 8);
    if (state.revision() != revision || cachedWidth != w) {
      boolean atBottom = scroll >= Math.max(0, contentHeight - scrollHeight) - 3;
      rebuild(snapshot, w - 78);
      revision = state.revision();
      cachedWidth = w;
      if (atBottom) scroll = Double.MAX_VALUE;
    }
    clampScroll();
    renderedThrough = 0;
    g.enableScissor(x + 10, bodyY, x + w - 10, bodyY + scrollHeight);
    for (Entry entry : rows) {
      int ry = bodyY + entry.top() - (int) scroll;
      if (ry + entry.height() < bodyY || ry >= bodyY + scrollHeight) continue;
      renderEntry(g, entry, snapshot, ry);
    }
    if (!rows.isEmpty() && contentHeight - scroll <= scrollHeight)
      renderedThrough = snapshot.channel().latestSequence();
    g.disableScissor();
    if (rows.isEmpty())
      text(
          g,
          state.hasData() ? "No public messages yet." : "Waiting for this channel's snapshot…",
          x + 16,
          bodyY + 12,
          w - 40,
          UiStyle.color("paper.muted"));
    String audience =
        ChannelClient.audience(input.text.value()).stream()
            .map(Agent::name)
            .reduce((a, b) -> a + ", " + b)
            .orElse("Nobody");
    String status =
        draft.sending()
            ? "Sending…"
            : draft.feedback().isBlank() ? "→ " + audience : draft.feedback();
    input.setStatus(status);
    input.setPosition(x + 14, fieldY);
  }

  private void rebuild(State snapshot, int maxWidth) {
    contentHeight = 0;
    if (snapshot == null) {
      rows = List.of();
      return;
    }
    List<Entry> list = new ArrayList<>();
    var messages =
        snapshot.messages().stream()
            .filter(m -> agentId == null || agentId.equals(m.sender().agentId()))
            .toList();
    for (var row : TranscriptRows.group(messages, unreadAfter)) {
      boolean system = row.messages().getFirst().isSystem();
      int height = (row.startsDay() ? 20 : 0) + (row.startsUnread() ? 18 : 0) + (system ? 0 : 16);
      List<MessageLayout> layouts = new ArrayList<>();
      for (Message message : system ? List.of(row.messages().getLast()) : row.messages()) {
        var lines =
            TextUtil.wrap(
                font,
                system
                    ? TranscriptRows.systemText(row, snapshot)
                    : ChannelText.message(message, snapshot),
                maxWidth);
        List<Chip> chips = new ArrayList<>();
        int cx = 0, cy = 0;
        for (var reaction : message.reactions()) {
          String label =
              TextUtil.ellipsize(
                  font,
                  ChannelText.reaction(reaction.reaction())
                      + " · "
                      + ChannelText.agentName(reaction.agentId(), snapshot),
                  maxWidth - 24);
          int cw = font.width(label) + 24;
          if (cx > 0 && cx + cw > maxWidth) {
            cx = 0;
            cy += 19;
          }
          chips.add(new Chip(reaction, label, cx, cy, cw));
          cx += cw + 4;
        }
        int mh =
            lines.size() * 12 + message.attachments().size() * 16 + (chips.isEmpty() ? 0 : cy + 21);
        layouts.add(new MessageLayout(message, lines, chips, mh));
        height += mh + 6;
      }
      height += 8;
      list.add(new Entry(row, contentHeight, height, List.copyOf(layouts)));
      contentHeight += height;
    }
    rows = List.copyOf(list);
  }

  private void renderEntry(GuiGraphicsExtractor g, Entry entry, State snapshot, int py) {
    var row = entry.row();
    Message first = row.messages().getFirst();
    if (row.startsDay()) {
      String date =
          TranscriptRows.day(first)
              .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"));
      if (lineVisible(py + 3))
        g.text(font, date, x + 16, py + 3, UiStyle.color("paper.muted"), false);
      py += 20;
    }
    if (row.startsUnread()) {
      g.fill(x + 16, py + 5, x + w - 16, py + 6, UiStyle.CLAY);
      if (lineVisible(py)) g.text(font, "New", x + 20, py, UiStyle.INK, false);
      py += 18;
    }
    int left = x + 52;
    if (!first.isSystem()) {
      String sender = ChannelText.sender(first.sender(), snapshot);
      int color = UiStyle.INK;
      if (first.sender().agentId() != null) {
        portrait(g, first.sender().agentId(), x + 15, py, 26);
        color = AvatarArt.onLight(AgentSkins.color(first.sender().agentId()));
      }
      if (lineVisible(py + 1))
        g.text(font, TextUtil.ellipsize(font, sender, w - 80), left, py + 1, color, false);
      py += 16;
    }
    for (var layout : entry.messages()) {
      Message message = layout.message();
      int start = py;
      if (snapshot.request() != null && snapshot.request().sequence() == message.sequence())
        g.fill(left - 6, py, left - 4, py + Math.max(12, layout.height()), UiStyle.CLAY);
      for (var line : layout.lines()) {
        if (lineVisible(py))
          g.text(
              font,
              line,
              left,
              py,
              UiStyle.color(first.isSystem() ? "paper.muted" : "paper.text"),
              false);
        py += 12;
      }
      for (int a = 0; a < message.attachments().size(); a++) {
        int index = a, top = py;
        String name = message.attachments().get(a).name();
        String label = name == null ? "Open image " + (a + 1) : "Open " + name;
        if (lineVisible(py + 2))
          g.text(
              font,
              TextUtil.ellipsize(font, label + " ↗", w - 80),
              left,
              py + 2,
              UiStyle.color("paper.link"),
              false);
        if (top >= bodyY && top + 14 <= bodyY + scrollHeight)
          hits.add(
              new Hit(
                  left,
                  top,
                  w - 78,
                  14,
                  () -> {
                    var image = ChannelImages.image(message, index);
                    if (image != null) minecraft.gui.setScreen(new ImageScreen(image));
                    else ChannelClient.openAttachment(message, index);
                  }));
        py += 16;
      }
      for (var chip : layout.chips()) {
        int cx = left + chip.x(), cy = py + chip.y();
        g.fill(cx, cy, cx + chip.width(), cy + 16, UiStyle.color("paper.inset"));
        portrait(g, chip.reaction().agentId(), cx + 3, cy + 2, 12);
        if (lineVisible(cy + 4)) g.text(font, chip.label(), cx + 19, cy + 4, UiStyle.INK, false);
      }
      int bottom = start + layout.height();
      if (bottom >= bodyY && bottom <= bodyY + scrollHeight)
        renderedThrough = Math.max(renderedThrough, message.sequence());
      py = bottom + 6;
    }
  }

  private boolean lineVisible(int py) {
    return py >= bodyY && py + font.lineHeight <= bodyY + scrollHeight;
  }

  @Override
  public boolean charTyped(CharacterEvent e) {
    if (e.codepoint() == 96 && System.currentTimeMillis() - openedAt < 200) return true;
    return super.charTyped(e);
  }
}
