package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.toybox.minecraft.block.entity.GalleryFrameBlockEntity;
import dev.toybox.minecraft.client.images.ChannelImages;
import dev.toybox.minecraft.client.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

final class GalleryFrameRenderer
    extends StationRenderer<GalleryFrameBlockEntity, GalleryFrameRenderer.State> {
  static final class State extends StationRenderState {
    ChannelImages.Texture texture;
    String caption;
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public boolean shouldRenderOffScreen() {
    return true;
  }

  @Override
  protected void extractStation(GalleryFrameBlockEntity be, State state, float dt) {
    if (!state.panelOrigin) return;
    var image = ChannelImages.at(state.binding);
    state.texture = image == null ? null : ChannelImages.texture(image);
    state.caption =
        image == null
            ? "Shared images"
            : "#" + image.message().sequence() + " · " + image.message().content();
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    if (!s.panelOrigin) return;
    float w = s.panelWidth * 96, h = s.panelHeight * 96;
    int light = WorldUi.uiLight();
    ps.pushPose();
    toFace(ps, s.facing, 14f / 16f - .003f, 96);
    ps.translate(0, -(s.panelHeight - 1) * 96, 0);
    var texture = s.texture;
    if (texture != null && texture.ready()) {
      float scale = Math.min((w - 32) / texture.width, (h - 54) / texture.height);
      float iw = texture.width * scale, ih = texture.height * scale;
      float x = (w - iw) / 2, y = 16 + (h - 54 - ih) / 2;
      c.order(1)
          .submitCustomGeometry(
              ps,
              RenderTypes.textPolygonOffset(texture.id),
              (pose, vertices) ->
                  WorldUi.quad(
                      pose, vertices, x, y, x + iw, y + ih, 0, 0, 1, 1, 0xffffffff, light));
    } else
      WorldUi.submitText(
          ps,
          c,
          texture == null ? "Images shared here appear on this wall" : texture.status,
          16,
          h / 2,
          UiStyle.INK,
          light);
    WorldUi.submitText(
        ps,
        c,
        TextUtil.ellipsize(Minecraft.getInstance().font, s.caption, (int) w - 32),
        16,
        h - 27,
        UiStyle.INK,
        light);
    ps.popPose();
  }
}
