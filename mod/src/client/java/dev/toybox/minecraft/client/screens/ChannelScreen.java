package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.agents.AgentSkins;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.ui.KitButton;
import dev.toybox.minecraft.client.ui.Panels;
import dev.toybox.minecraft.client.ui.TextUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/** Common channel navigation. Every page reads the same authoritative snapshot. */
public abstract class ChannelScreen extends Screen {
  public enum Page {
    TRANSCRIPT("Transcript", "message"),
    TEAM("Team", "team"),
    TASKS("Tasks", "tasks"),
    REQUEST("Request", "decision"),
    ARTIFACTS("Artifacts", "read"),
    ROUTINES("Routines", "routines");
    final String label, icon;

    Page(String label, String icon) {
      this.label = label;
      this.icon = icon;
    }
  }

  protected final Page page;
  protected int x, y, w, h, bodyY, bodyH;
  protected double scroll;

  /** Scrolled content and its visible height; the body unless a screen fixes a header or footer. */
  protected int contentHeight, scrollHeight;
  private KitButton preview;
  protected final List<Hit> hits = new ArrayList<>();

  public record Hit(int x, int y, int w, int h, Runnable action) {
    boolean contains(double px, double py) {
      return px >= x && px < x + w && py >= y && py < y + h;
    }
  }

  protected ChannelScreen(Page page, String title) {
    super(Component.literal(title));
    this.page = page;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
    extractTransparentBackground(g);
  }

  @Override
  protected void init() {
    x = Math.max(10, (width - Math.min(650, width - 20)) / 2);
    y = 18;
    w = width - 2 * x;
    h = height - 36;
    int tx = x + 10, tabY = y + 25;
    int tabsWidth = 0;
    for (Page p : Page.values()) tabsWidth += font.width(p.label) + 20;
    if (page != null)
      for (Page p : Page.values()) {
        var tab =
            addRenderableWidget(
                tabsWidth <= w - 16
                    ? new KitButton(p.label, font.width(p.label) + 16, () -> Screens.open(p))
                    : KitButton.icon(p.label, p.icon, () -> Screens.open(p)));
        tab.selected = p == page;
        tab.setPosition(tx, tabY);
        tx += tab.getWidth() + 4;
      }
    bodyY = page == null ? tabY : tabY + 27;
    bodyH = scrollHeight = Math.max(24, y + h - 10 - bodyY);
    int actionsX = x + w - (page == null ? 60 : 86);
    var open =
        addRenderableWidget(KitButton.icon("Open Toy Box", "open", ChannelClient::openChannel));
    open.setPosition(actionsX, y + 4);
    preview =
        addRenderableWidget(KitButton.icon("Open preview", "preview", ChannelClient::openPreview));
    preview.setPosition(actionsX + 26, y + 4);
    if (page != null) {
      var change =
          addRenderableWidget(
              KitButton.icon(
                  "Change channel",
                  "channels",
                  () -> minecraft.gui.setScreen(new ChannelPickerScreen())));
      change.setPosition(x + w - 34, y + 4);
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
    hits.clear();
    Panels.panel(g, x, y, w, h);
    var channel = ChannelClient.store().channel();
    String title =
        page == null
            ? "Connect to Toy Box"
            : channel == null ? "Toy Box · choose a channel" : channel.name();
    if (page != null) {
      String suffix = " · " + page.label + (ChannelClient.store().isStale() ? " · offline" : "");
      title = TextUtil.ellipsize(font, title, w - 108 - font.width(suffix)) + suffix;
    }
    preview.active = ChannelClient.hasPreview();
    Panels.header(g, font, title, x + 6, y + 6, w - (page == null ? 68 : 96));
    renderContent(g, mx, my, dt);
    super.extractRenderState(g, mx, my, dt);
  }

  @Override
  public void removed() {
    clearFocus();
    super.removed();
  }

  protected abstract void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt);

  protected int text(GuiGraphicsExtractor g, String text, int px, int py, int maxWidth, int color) {
    var lines = TextUtil.wrap(font, text == null ? "" : text, Math.max(25, maxWidth));
    for (var line : lines) {
      g.text(font, line, px, py, color, false);
      py += 11;
    }
    return py;
  }

  protected void portrait(GuiGraphicsExtractor g, String agent, int px, int py, int size) {
    g.blit(
        RenderPipelines.GUI_TEXTURED,
        AgentSkins.portrait(agent),
        px,
        py,
        0,
        0,
        size,
        size,
        64,
        64,
        64,
        64);
  }

  protected KitButton button(String label, int width, Runnable action) {
    return addRenderableWidget(new KitButton(label, width, action));
  }

  protected void clampScroll() {
    scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - scrollHeight)));
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
    for (Hit hit : hits)
      if (hit.contains(e.x(), e.y())) {
        hit.action().run();
        return true;
      }
    return super.mouseClicked(e, twice);
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double sx, double sy) {
    if (super.mouseScrolled(mx, my, sx, sy)) return true;
    scroll -= sy * 30;
    clampScroll();
    return true;
  }
}
