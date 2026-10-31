package dev.toybox.minecraft.client.channel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Owner-selected server and channel. The mod never starts an agent or creates a channel. */
public record ChannelConfig(URI server, String channelId, String error, Path file) {
  /** Toy Box's development server; the installed app listens on port 3000 by default. */
  public static final String DEFAULT_SERVER = "http://localhost:3100";

  public static ChannelConfig load() {
    Path file = FabricLoader.getInstance().getConfigDir().resolve("toy-box-minecraft.json");
    JsonObject json = new JsonObject();
    try {
      if (Files.exists(file))
        json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
      else {
        Files.createDirectories(file.getParent());
        Files.writeString(
            file, "{\n  \"serverUrl\": \"" + DEFAULT_SERVER + "\",\n  \"channelId\": \"\"\n}\n");
      }
      String base = setting("TOY_BOX_SERVER_URL", json, "serverUrl", DEFAULT_SERVER);
      String channel = setting("TOY_BOX_CHANNEL_ID", json, "channelId", "");
      URI uri = serverUri(base);
      return new ChannelConfig(
          uri, channel, channel.isBlank() ? "Choose a Toy Box server and channel." : null, file);
    } catch (Exception e) {
      return new ChannelConfig(
          URI.create(DEFAULT_SERVER), "", "Check " + file + ": " + e.getMessage(), file);
    }
  }

  public static URI serverUri(String value) {
    URI uri = URI.create(value.trim().replaceAll("/+$", ""));
    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException(
          "Enter an HTTP(S) server URL without credentials, query or fragment.");
    return uri;
  }

  public ChannelConfig select(URI server, String channelId) throws java.io.IOException {
    JsonObject json = new JsonObject();
    json.addProperty("serverUrl", server.toString());
    json.addProperty("channelId", channelId);
    Files.createDirectories(file.getParent());
    Files.writeString(
        file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
    return new ChannelConfig(server, channelId, null, file);
  }

  private static String setting(String env, JsonObject json, String key, String fallback) {
    String value = System.getenv(env);
    if (value != null && !value.isBlank()) return value.trim();
    return json.has(key) && !json.get(key).isJsonNull()
        ? json.get(key).getAsString().trim()
        : fallback;
  }

  public URI endpoint() {
    return URI.create(server + "/api/v1/channels/" + encode(channelId));
  }

  public URI channelPage() {
    if (channelId.isBlank()) return server;
    return URI.create(
        server
            + "/?channels="
            + encode(new com.google.gson.Gson().toJson(java.util.List.of(channelId))));
  }

  public static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
