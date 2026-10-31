package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import com.google.gson.Gson;
import java.net.URI;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;

/** Native Channel capabilities available to every Minecraft presentation surface. */
public final class ChannelClient {
  public record Result(boolean ok, String error, Message message, Long seenThrough) {
    public static Result failure(String error) {
      return new Result(false, error, null, null);
    }
  }

  private static ChannelConfig config;
  private static ChannelStore store;
  private static ChannelTransport transport;

  private ChannelClient() {}

  public static void init() {
    config = ChannelConfig.load();
    store =
        new ChannelStore(
            new ChannelConnection(
                ChannelConnection.Phase.DISABLED, config.error(), System.currentTimeMillis()));
    transport = new ChannelTransport(config, store, r -> Minecraft.getInstance().execute(r));
    ClientLifecycleEvents.CLIENT_STARTED.register(mc -> transport.start());
    ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> transport.stop());
  }

  public static ChannelStore store() {
    return store;
  }

  public static ChannelConnection connection() {
    return store.connection();
  }

  public static ChannelConfig config() {
    return config;
  }

  public static long snapshotCount() {
    return transport.snapshotCount();
  }

  public static CompletableFuture<Result> postMessage(String text) {
    return transport.postMessage(text);
  }

  public static CompletableFuture<Result> markRead(long sequence) {
    return transport.markRead(sequence);
  }

  public static void reconnect() {
    transport.reconnect();
  }

  public static CompletableFuture<List<Channel>> catalog(URI server) {
    return ChannelTransport.catalog(server);
  }

  /** Switch observation while keeping the presentation listeners and per-channel drafts. */
  public static void connect(URI server, String channelId) throws java.io.IOException {
    ChannelConfig next = config.select(server, channelId);
    transport.stop();
    store.clear();
    config = next;
    transport = new ChannelTransport(config, store, r -> Minecraft.getInstance().execute(r));
    transport.start();
  }

  public static String mention(String agentId) {
    Agent agent = store.agent(agentId);
    return agent == null ? "" : ChannelAudience.handle(agent.name());
  }

  public static List<Agent> audience(String text) {
    return store.state() == null
        ? List.of()
        : ChannelAudience.resolve(
            store.state(), text, Sender.USER, store.channel().hasPendingRequest());
  }

  public static boolean hasPreview() {
    return store.channel() != null && store.channel().previewUrl() != null;
  }

  public static void openChannel() {
    open(config.channelPage());
  }

  public static void openPreview() {
    if (!hasPreview()) {
      openChannel();
      return;
    }
    try {
      open(resourceUri(store.channel().previewUrl()));
    } catch (Exception e) {
      openChannel();
    }
  }

  public static void openArtifact(Artifact artifact) {
    if (artifact == null) {
      openChannel();
      return;
    }
    openFile(artifact.file());
  }

  public static void openAttachment(Message message, int index) {
    if (message == null || index < 0 || index >= message.attachments().size()) {
      openChannel();
      return;
    }
    Attachment attachment = message.attachments().get(index);
    try {
      // Open a locally materialized image, rather than handing an untrusted data URI to a browser.
      if (attachment.isImage()) {
        String suffix =
            switch (attachment.mimeType()) {
              case "image/png" -> ".png";
              case "image/jpeg" -> ".jpg";
              case "image/gif" -> ".gif";
              case "image/webp" -> ".webp";
              default -> null;
            };
        if (suffix == null) {
          openChannel();
          return;
        }
        byte[] bytes = Base64.getDecoder().decode(attachment.base64());
        var file = Files.createTempFile("toy-box-attachment-", suffix);
        Files.write(file, bytes);
        file.toFile().deleteOnExit();
        org.lwjgl.sdl.SDLMisc.SDL_OpenURL(file.toUri().toString());
        return;
      }
      open(resourceUri(attachment.url()));
    } catch (Exception e) {
      openChannel();
    }
  }

  public static URI resourceUri(String url) {
    URI uri = URI.create(url);
    return uri.isAbsolute()
        ? uri
        : URI.create(config.server() + "/").resolve(url.startsWith("/") ? url.substring(1) : url);
  }

  private static void openFile(FileRef file) {
    // Resource URLs serve raw bytes; workspace panes render .toy apps and editable files.
    open(
        URI.create(
            config.server() + "/?files=" + ChannelConfig.encode(new Gson().toJson(List.of(file)))));
  }

  private static void open(URI uri) {
    if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
      org.lwjgl.sdl.SDLMisc.SDL_OpenURL(uri.toString());
  }
}
