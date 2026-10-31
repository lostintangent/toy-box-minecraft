package dev.toybox.minecraft.client.screens;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.*;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.input.*;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Channel composition over Minecraft's editing model; only presentation and channel keys live here.
 */
final class MessageInput extends AbstractWidget {
  private static final int LINE = 10;
  private static final String HINT = "Enter sends · Shift+Enter newline";
  final Editor text;
  private final Font textFont;
  private final Runnable submit;
  private final int maxLines;
  private String status = "";
  private int scrollLine;
  private long editedAt;
  private @Nullable IMEPreeditOverlay preedit;

  MessageInput(
      Font font, int width, int maxLines, String value, Consumer<String> changed, Runnable submit) {
    super(0, 0, width, 28, Component.literal("Message the channel"));
    this.textFont = font;
    this.submit = submit;
    this.maxLines = maxLines;
    text = new Editor(Math.max(20, width - 16 - font.width("999/999")));
    text.setCharacterLimit(12_000);
    text.setCursorListener(this::followCursor);
    text.setValue(value);
    text.setValueListener(changed);
  }

  private void followCursor() {
    int visibleLines = visibleLines();
    height = 28 + (visibleLines - 1) * LINE;
    scrollLine = Math.max(0, Math.min(scrollLine, text.getLineCount() - visibleLines));
    scrollLine =
        Math.max(
            text.getLineAtCursor() - visibleLines + 1,
            Math.min(scrollLine, text.getLineAtCursor()));
    editedAt = System.currentTimeMillis();
  }

  private int visibleLines() {
    return Math.min(maxLines, text.getLineCount());
  }

  void setStatus(String status) {
    if (this.status.equals(status)) return;
    this.status = status;
    setTooltip(Tooltip.create(Component.literal(status + "\n" + HINT)));
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (!isFocused()) return false;
    if (event.isConfirmation() && !event.hasShiftDown()) {
      submit.run();
      return true;
    }
    if (event.isCycleFocus() && !event.hasShiftDown() && completeMention()) return true;
    boolean handled = text.keyPressed(event);
    if (handled) followCursor();
    return handled;
  }

  private boolean completeMention() {
    String prefix = text.value().substring(0, text.cursor());
    int at = prefix.lastIndexOf('@');
    if (at < 0 || prefix.substring(at).contains(" ")) return false;
    String partial = prefix.substring(at + 1).toLowerCase(Locale.ROOT);
    var match =
        Stream.concat(
                Stream.of("everyone"),
                ChannelClient.store().team().stream().map(a -> ChannelClient.mention(a.id())))
            .filter(h -> h.startsWith(partial))
            .findFirst();
    if (match.isEmpty()) return false;
    int cursor = text.cursor();
    text.setSelecting(false);
    text.seekCursor(Whence.ABSOLUTE, at);
    text.setSelecting(true);
    text.seekCursor(Whence.ABSOLUTE, cursor);
    text.insertText("@" + match.get() + " ");
    return true;
  }

  @Override
  public boolean charTyped(CharacterEvent event) {
    if (!capturesInput() || !event.isAllowedChatCharacter()) return false;
    text.insertText(event.codepointAsString());
    return true;
  }

  @Override
  public void playDownSound(SoundManager sounds) {}

  @Override
  public boolean capturesInput() {
    return visible && isFocused();
  }

  @Override
  public void setFocused(boolean focused) {
    super.setFocused(focused);
    editedAt = System.currentTimeMillis();
    Minecraft.getInstance().onTextInputFocusChange(this, focused);
    if (!focused) preedit = null;
  }

  @Override
  public boolean preeditUpdated(@Nullable PreeditEvent event) {
    preedit = event == null ? null : new IMEPreeditOverlay(event, textFont, LINE);
    return true;
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean twice) {
    text.setSelecting(event.hasShiftDown());
    seek(event);
    if (twice) text.selectWordAtCursor();
  }

  @Override
  protected void onDrag(MouseButtonEvent event, double dx, double dy) {
    text.setSelecting(true);
    seek(event);
    text.setSelecting(event.hasShiftDown());
  }

  private void seek(MouseButtonEvent event) {
    text.seekCursorToPoint(
        Math.max(0, event.x() - getX() - 4), ((event.y() - getY() - 5) / LINE + scrollLine) * 9);
  }

  @Override
  protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
    text.draw(g);
    if (isHovered()) g.requestCursor(CursorTypes.IBEAM);
  }

  // Minecraft's line ranges are protected, so their presentation lives in the field subclass.
  final class Editor extends MultilineTextField {
    Editor(int width) {
      super(textFont, width);
    }

    void draw(GuiGraphicsExtractor g) {
      Panels.sprite(
          g,
          isFocused() ? Kit.TEXT_FIELD_FOCUSED : Kit.TEXT_FIELD,
          getX(),
          getY(),
          getWidth(),
          height);
      int tx = getX() + 4, ty = getY() + 5, lines = visibleLines();
      String value = text.value();
      int ink = UiStyle.color("paper.text"), muted = UiStyle.color("paper.muted");
      if (value.isEmpty())
        g.text(
            textFont,
            "Message the channel…",
            tx,
            ty,
            UiStyle.color("ink_ui.ghost_on_paper", 0xFFBCAD95),
            false);
      var selection = text.getSelected();
      for (int i = 0; i < lines; i++) {
        var line = text.getLineView(scrollLine + i);
        int y = ty + i * LINE;
        int a = Math.max(selection.beginIndex(), line.beginIndex());
        int b = Math.min(selection.endIndex(), line.endIndex());
        if (a < b)
          g.fill(
              tx + textFont.width(value.substring(line.beginIndex(), a)),
              y - 1,
              tx + textFont.width(value.substring(line.beginIndex(), b)),
              y + 9,
              UiStyle.color("palette.ui.field_focus", 0xFFF3CDBB));
        g.text(textFont, value.substring(line.beginIndex(), line.endIndex()), tx, y, ink, false);
      }
      String label = TextUtil.ellipsize(textFont, status, getWidth() - 10);
      g.text(
          textFont, label, getRight() - textFont.width(label) - 5, getBottom() - 12, muted, false);
      if (textFont.width(label) + textFont.width(HINT) + 24 < getWidth())
        g.text(textFont, HINT, tx, getBottom() - 12, muted, false);
      if (text.getLineCount() > lines) {
        String more = (scrollLine + lines) + "/" + text.getLineCount();
        g.text(textFont, more, getRight() - textFont.width(more) - 5, ty, muted, false);
      }
      if (isFocused()) {
        var line = text.getLineView(text.getLineAtCursor());
        int cx = tx + textFont.width(value.substring(line.beginIndex(), text.cursor()));
        int cy = ty + (text.getLineAtCursor() - scrollLine) * LINE;
        if ((System.currentTimeMillis() - editedAt) % 1000 < 500)
          g.fill(cx, cy - 1, cx + 1, cy + 9, ink);
        if (preedit == null)
          Minecraft.getInstance().textInputManager().setTextInputArea(cx, cy, cx + 1, cy + LINE);
        else {
          preedit.updateInputPosition(cx, cy);
          g.setPreeditOverlay(preedit);
        }
      }
    }
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(
        NarratedElementType.TITLE,
        Component.translatable("gui.narrate.editBox", getMessage(), text.value()));
  }
}
