package dev.toybox.minecraft.client.images;

import com.mojang.blaze3d.platform.NativeImage;
import dev.toybox.minecraft.ToyBoxMinecraft;
import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.channel.ChannelModel.Message;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * PNG attachments, shared by the gallery and image reader. Minecraft owns decoding and textures;
 * other formats retain their ordinary external-opening path.
 */
public final class ChannelImages {
  public record Image(Message message, int index, String url) {
    public String key() {
      return message.id() + ":" + index;
    }

    public String discussion() {
      return "About the image in message #" + message.sequence() + ": ";
    }
  }

  public static final class Texture {
    public final Identifier id;
    public int width, height;
    public String status = "Loading image…";

    private Texture(Identifier id) {
      this.id = id;
    }

    public boolean ready() {
      return width > 0;
    }
  }

  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .version(HttpClient.Version.HTTP_1_1)
          .build();
  private static final LinkedHashMap<String, Texture> textures = new LinkedHashMap<>(8, .75f, true);
  private static long nextId;

  private ChannelImages() {}

  public static void init() {
    ChannelClient.store()
        .addListener(
            new ChannelStore.Listener() {
              @Override
              public void onCleared() {
                clear();
              }
            });
  }

  public static List<Image> gallery() {
    List<Image> result = new ArrayList<>();
    for (Message message : ChannelClient.store().messages().reversed()) {
      for (int i = 0; i < message.attachments().size(); i++) {
        Image image = image(message, i);
        if (image != null) result.add(image);
        if (result.size() == 3) return result;
      }
    }
    return result;
  }

  public static Image image(Message message, int index) {
    if (index >= message.attachments().size()) return null;
    var attachment = message.attachments().get(index);
    boolean png =
        attachment.isImage()
            ? "image/png".equals(attachment.mimeType())
            : attachment.name().toLowerCase(Locale.ROOT).endsWith(".png");
    if (!png) return null;
    String url =
        attachment.isImage()
            ? "data:" + attachment.mimeType() + ";base64," + attachment.base64()
            : attachment.url();
    return new Image(message, index, url);
  }

  public static Image at(String binding) {
    var images = gallery();
    return switch (binding) {
      case "0" -> images.isEmpty() ? null : images.get(0);
      case "1" -> images.size() < 2 ? null : images.get(1);
      case "2" -> images.size() < 3 ? null : images.get(2);
      default -> null;
    };
  }

  public static Texture texture(Image image) {
    Texture old = textures.get(image.key());
    if (old != null) return old;
    Texture texture = new Texture(ToyBoxMinecraft.id("dynamic/image/" + nextId++));
    textures.put(image.key(), texture);
    while (textures.size() > 8) release(textures.pollFirstEntry().getValue());
    CompletableFuture<byte[]> bytes;
    if (image.url().startsWith("data:")) {
      bytes =
          CompletableFuture.supplyAsync(
              () ->
                  Base64.getDecoder().decode(image.url().substring(image.url().indexOf(',') + 1)));
    } else {
      var request =
          HttpRequest.newBuilder(ChannelClient.resourceUri(image.url()))
              .timeout(Duration.ofSeconds(20))
              .GET()
              .build();
      bytes =
          HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
              .thenApply(
                  response -> {
                    if (response.statusCode() != 200)
                      throw new IllegalStateException("Image unavailable");
                    return response.body();
                  });
    }
    bytes
        .thenApplyAsync(ChannelImages::decode)
        .whenComplete(
            (pixels, error) ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          if (error != null) {
                            texture.status = "Image unavailable · open in Toy Box";
                            return;
                          }
                          if (textures.get(image.key()) != texture) {
                            pixels.close();
                            return;
                          }
                          Minecraft.getInstance()
                              .getTextureManager()
                              .register(
                                  texture.id, new DynamicTexture(texture.id::toString, pixels));
                          texture.width = pixels.getWidth();
                          texture.height = pixels.getHeight();
                        }));
    return texture;
  }

  private static NativeImage decode(byte[] data) {
    try {
      NativeImage source = NativeImage.read(data);
      double scale = Math.min(1, 2048.0 / Math.max(source.getWidth(), source.getHeight()));
      if (scale == 1) return source;
      try (source) {
        NativeImage small =
            new NativeImage(
                Math.max(1, (int) (source.getWidth() * scale)),
                Math.max(1, (int) (source.getHeight() * scale)),
                false);
        source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), small);
        return small;
      }
    } catch (IOException e) {
      throw new java.util.concurrent.CompletionException(e);
    }
  }

  private static void clear() {
    textures.values().forEach(ChannelImages::release);
    textures.clear();
  }

  private static void release(Texture texture) {
    if (texture.ready()) Minecraft.getInstance().getTextureManager().release(texture.id);
  }
}
