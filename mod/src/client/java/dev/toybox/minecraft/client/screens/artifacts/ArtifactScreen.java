package dev.toybox.minecraft.client.screens.artifacts;

import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel.Artifact;
import dev.toybox.minecraft.client.channel.ChannelModel.FileRef;
import dev.toybox.minecraft.client.screens.ChannelScreen;
import dev.toybox.minecraft.client.ui.KitButton;
import dev.toybox.minecraft.client.ui.TextUtil;
import dev.toybox.minecraft.client.ui.UiStyle;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Authoritative Channel artifact list and one independently owned live file reader. */
public final class ArtifactScreen extends ChannelScreen {
  private List<Artifact> artifacts = List.of();
  private @Nullable Artifact selected;
  private @Nullable FileRef preferred;
  private ArtifactReader reader;
  private ArtifactReader.State file =
      new ArtifactReader.State(ArtifactReader.Phase.LOADING, "", "Choose an artifact", false, 0);
  private @Nullable MdLayout document;
  private long channelRevision = -1, documentRevision = -1;
  private int documentWidth, listScroll, listWidth;
  private boolean selectionMade;
  private KitButton open;

  public ArtifactScreen() {
    this(null);
  }

  public ArtifactScreen(@Nullable FileRef preferred) {
    super(Page.ARTIFACTS, "Shared artifacts");
    this.preferred = preferred;
  }

  @Override
  protected void init() {
    super.init();
    open = button("Open in browser", 120, () -> ChannelClient.openArtifact(selected));
    if (reader == null)
      reader =
          new ArtifactReader(
              r -> Minecraft.getInstance().execute(r),
              next -> {
                file = next;
                document = null;
              });
    sync();
  }

  @Override
  public void tick() {
    sync();
  }

  private void sync() {
    var state = ChannelClient.store();
    if (reader == null || state.revision() == channelRevision) return;
    channelRevision = state.revision();
    artifacts = state.artifacts();
    if (!selectionMade) {
      Artifact initial =
          preferred == null
              ? null
              : artifacts.stream().filter(a -> a.file().equals(preferred)).findFirst().orElse(null);
      if (initial == null && !artifacts.isEmpty()) initial = artifacts.getFirst();
      if (initial != null) select(initial);
    } else if (selected != null) {
      Artifact next =
          artifacts.stream().filter(a -> a.file().equals(selected.file())).findFirst().orElse(null);
      if (next == null) {
        selected = null;
        document = null;
        reader.clear("This artifact is no longer shared in the channel.");
        file = reader.state();
      } else if (!java.util.Objects.equals(next.url(), selected.url())) select(next);
      else selected = next;
    }
  }

  private void select(Artifact artifact) {
    selected = artifact;
    selectionMade = true;
    scroll = 0;
    document = null;
    try {
      reader.select(
          artifact.url() == null ? null : ChannelClient.resourceUri(artifact.url()),
          artifact.file().path());
    } catch (IllegalArgumentException e) {
      reader.clear("This artifact's URL is unavailable. Open it in the browser.");
    }
    file = reader.state();
  }

  @Override
  public void removed() {
    super.removed();
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    listWidth = Math.min(180, Math.max(100, w / 3));
    int lx = x + 10, rx = lx + listWidth + 12, rw = w - listWidth - 34;
    g.fill(
        lx + listWidth + 4,
        bodyY,
        lx + listWidth + 5,
        bodyY + bodyH,
        UiStyle.color("board.chip_edge"));
    if (artifacts.isEmpty()) {
      text(g, "No artifacts shared yet.", lx, bodyY, listWidth, UiStyle.INK);
    }
    int listHeight = artifacts.size() * 33;
    listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listHeight - bodyH)));
    g.enableScissor(lx, bodyY, lx + listWidth, bodyY + bodyH);
    int yy = bodyY - listScroll;
    for (Artifact artifact : artifacts) {
      if (yy + 32 >= bodyY && yy < bodyY + bodyH) {
        boolean active = selected != null && selected.file().equals(artifact.file());
        if (active) g.fill(lx, yy, lx + listWidth, yy + 31, 0x2264a996);
        g.text(
            font,
            TextUtil.ellipsize(font, artifact.title(), listWidth - 8),
            lx + 4,
            yy + 4,
            UiStyle.INK,
            false);
        g.text(
            font,
            TextUtil.ellipsize(font, artifact.file().path(), listWidth - 8),
            lx + 4,
            yy + 16,
            UiStyle.color("paper.muted"),
            false);
        final Artifact item = artifact;
        hits.add(
            new Hit(
                lx,
                Math.max(yy, bodyY),
                listWidth,
                Math.min(yy + 32, bodyY + bodyH) - Math.max(yy, bodyY),
                () -> select(item)));
      }
      yy += 33;
    }
    g.disableScissor();
    String title = selected == null ? "Shared artifact" : selected.title();
    g.text(font, TextUtil.ellipsize(font, title, rw), rx, bodyY, UiStyle.INK, false);
    int top = bodyY + 17;
    scrollHeight = Math.max(20, bodyH - 53);
    g.enableScissor(rx, top, rx + rw, top + scrollHeight);
    if (file.phase() == ArtifactReader.Phase.READY) {
      if (document == null || documentRevision != file.revision() || documentWidth != rw) {
        String path =
            selected == null ? "" : selected.file().path().toLowerCase(java.util.Locale.ROOT);
        List<Markdown.Block> blocks;
        if (path.endsWith(".md") || path.endsWith(".markdown") || path.endsWith(".mdx"))
          blocks = Markdown.parse(file.text());
        else {
          var code = new Markdown.Block(Markdown.Type.CODE);
          code.lines.addAll(Arrays.asList(file.text().split("\n", -1)));
          blocks = List.of(code);
        }
        document = MdLayout.build(font, blocks, rw);
        documentRevision = file.revision();
        documentWidth = rw;
      }
      contentHeight = document.height;
      clampScroll();
      document.draw(g, font, rx, top, (int) scroll, scrollHeight);
    } else {
      contentHeight = 0;
      scroll = 0;
      text(g, file.notice(), rx, top, rw, UiStyle.INK);
    }
    g.disableScissor();
    String status = file.watching() ? "Live file" : "";
    if (file.phase() == ArtifactReader.Phase.READY && !file.notice().isEmpty())
      status = file.notice();
    g.text(
        font,
        TextUtil.ellipsize(font, status, rw),
        rx,
        bodyY + bodyH - 32,
        UiStyle.color("paper.muted"),
        false);
    open.visible = selected != null;
    open.setPosition(rx, bodyY + bodyH - 21);
    open.setWidth(Math.min(120, rw));
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double sx, double sy) {
    if (mx < x + listWidth + 16) {
      listScroll -= sy * 33;
      return true;
    }
    return super.mouseScrolled(mx, my, sx, sy);
  }
}
