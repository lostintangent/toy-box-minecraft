package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelText;
import dev.toybox.minecraft.client.images.ChannelImages;
import dev.toybox.minecraft.client.ui.UiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** The public image and the channel message that gives it context. */
public final class ImageScreen extends ChannelScreen {
  private final ChannelImages.Image image;

  public ImageScreen(ChannelImages.Image image) {
    super(Page.TRANSCRIPT, "Shared image");
    this.image = image;
  }

  @Override
  protected void init() {
    super.init();
    button("Discuss image", 100, () -> Screens.openTranscript(image.discussion()))
        .setPosition(x + 14, bodyY + bodyH - 24);
    button("Open original", 100, () -> ChannelClient.openAttachment(image.message(), image.index()))
        .setPosition(x + 120, bodyY + bodyH - 24);
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    var texture = ChannelImages.texture(image);
    int available = Math.max(20, bodyH - 100);
    if (texture.ready()) {
      float scale = Math.min((w - 28f) / texture.width, (float) available / texture.height);
      int iw = (int) (texture.width * scale), ih = (int) (texture.height * scale);
      g.blit(
          RenderPipelines.GUI_TEXTURED,
          texture.id,
          x + (w - iw) / 2,
          bodyY + (available - ih) / 2,
          0,
          0,
          iw,
          ih,
          texture.width,
          texture.height,
          texture.width,
          texture.height);
    } else text(g, texture.status, x + 14, bodyY + 15, w - 28, UiStyle.INK);
    var message = image.message();
    int py = bodyY + available + 8;
    String sender =
        message.sender().agentId() == null
            ? "You"
            : ChannelText.agentName(message.sender().agentId(), ChannelClient.store().state());
    g.text(font, "#" + message.sequence() + " · " + sender, x + 14, py, UiStyle.CLAY_DARK, false);
    int top = py + 13;
    scrollHeight = bodyY + bodyH - 28 - top;
    g.enableScissor(x + 14, top, x + w - 14, top + scrollHeight);
    contentHeight = text(g, message.content(), x + 14, top - (int) scroll, w - 28, UiStyle.INK);
    contentHeight += (int) scroll - top;
    g.disableScissor();
    clampScroll();
  }
}
