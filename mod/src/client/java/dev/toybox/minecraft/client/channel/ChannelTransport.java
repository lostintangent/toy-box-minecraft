package dev.toybox.minecraft.client.channel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.toybox.minecraft.client.channel.ChannelClient.Result;
import dev.toybox.minecraft.client.channel.ChannelConnection.Phase;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** HTTP/SSE observation lifetime. Effects cross onto the client thread exactly once. */
public final class ChannelTransport {
  // Choosing a channel happens before, and independently of, any observation's lifetime.
  private static final HttpClient CATALOG =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  private final ChannelConfig config;
  private final ChannelStore state;
  private final Executor clientThread;
  private final java.util.concurrent.ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "ToyBox-channel-reconnect"));
  private final java.util.concurrent.ExecutorService io =
      Executors.newCachedThreadPool(r -> daemon(r, "ToyBox-channel-http"));
  // Bun/Vite supports HTTP/1.1 SSE but its dev stream can stall h2c Upgrade requests.
  private final HttpClient http =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
  // Reads cross threads; every change is protected by this transport's monitor.
  private volatile int generation;
  private volatile ChannelConnection connection;
  private volatile InputStream stream;
  private volatile boolean running;
  private volatile long lastInbound, snapshots;
  private int attempt;

  public ChannelTransport(ChannelConfig config, ChannelStore state, Executor clientThread) {
    this.config = config;
    this.state = state;
    this.clientThread = clientThread;
    connection =
        new ChannelConnection(
            config.error() == null ? Phase.RETRYING : Phase.DISABLED,
            config.error(),
            System.currentTimeMillis());
  }

  private static Thread daemon(Runnable r, String name) {
    var t = new Thread(r, name);
    t.setDaemon(true);
    return t;
  }

  public ChannelConnection connection() {
    return connection;
  }

  public long snapshotCount() {
    return snapshots;
  }

  public synchronized void start() {
    publish(generation, connection);
    if (config.error() != null || running) return;
    running = true;
    connect();
    timer.scheduleAtFixedRate(this::checkDeadline, 10, 10, TimeUnit.SECONDS);
  }

  private synchronized void checkDeadline() {
    if (!running || stream == null) return;
    long now = System.currentTimeMillis();
    if (connection.phase() == Phase.SYNCING && now - connection.sinceMs() > 15_000)
      retry(generation, "Toy Box did not finish synchronizing the channel within 15 seconds.");
    else if (now - lastInbound > 45_000)
      retry(generation, "The Toy Box event stream stopped responding.");
  }

  public synchronized void stop() {
    running = false;
    ++generation;
    closeStream();
    timer.shutdownNow();
    io.shutdownNow();
    http.shutdownNow();
  }

  public synchronized void reconnect() {
    if (running) {
      ++generation;
      closeStream();
      connect();
    }
  }

  private void closeStream() {
    InputStream old = stream;
    stream = null;
    if (old != null)
      try {
        old.close();
      } catch (Exception ignored) {
      }
  }

  private synchronized void publish(int gen, ChannelConnection next) {
    if (gen != generation) return;
    connection = next;
    clientThread.execute(
        () -> {
          synchronized (this) {
            if (gen == generation) state.connection(next);
          }
        });
  }

  private synchronized void connect() {
    int gen = ++generation;
    attempt++;
    publish(gen, connection.with(Phase.CONNECTING, connection.error()));
    Long after = state.hasData() ? state.state().revision() : null;
    io.execute(() -> read(gen, after));
  }

  private void read(int gen, Long after) {
    boolean hasState = after != null;
    try {
      var request =
          HttpRequest.newBuilder(
                  after == null
                      ? config.endpoint()
                      : URI.create(config.endpoint() + "?after=" + after))
              .timeout(Duration.ofSeconds(15))
              .header("Accept", "text/event-stream")
              .GET()
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (gen != generation || !running) {
        response.body().close();
        return;
      }
      if (response.statusCode() != 200) {
        try (var body = response.body()) {
          throw new IllegalStateException(
              error(
                  response.statusCode(),
                  new String(body.readNBytes(8192), StandardCharsets.UTF_8)));
        }
      }
      if (!response.headers().firstValue("content-type").orElse("").contains("text/event-stream")) {
        response.body().close();
        throw new IllegalStateException(
            "The server did not provide a channel event stream. Check its URL and API version.");
      }
      synchronized (this) {
        if (gen != generation || !running) {
          response.body().close();
          return;
        }
        stream = response.body();
        lastInbound = System.currentTimeMillis();
        publish(gen, connection.with(Phase.SYNCING, null));
      }
      try (var body = response.body()) {
        var events =
            new ServerSentEvents(
                body,
                8_000_000,
                () -> {
                  synchronized (this) {
                    if (gen != generation || !running) return false;
                    lastInbound = System.currentTimeMillis();
                    return true;
                  }
                });
        String data;
        while ((data = events.next()) != null) {
          JsonObject event = JsonParser.parseString(data).getAsJsonObject();
          String type = ChannelCodec.text(event, "type");
          if ("deleted".equals(type)) {
            if (!config.channelId().equals(ChannelCodec.text(event, "channelId")))
              throw new IllegalArgumentException("Deletion identifies a different channel.");
            synchronized (this) {
              if (gen != generation || !running) return;
              running = false;
            }
            clientThread.execute(
                () -> {
                  synchronized (this) {
                    if (gen != generation) return;
                    state.clear();
                    publish(
                        gen,
                        connection.with(
                            Phase.DELETED, "This channel was deleted. Choose another channel."));
                  }
                });
            return;
          }
          if (!"snapshot".equals(type)) {
            if ("presence".equals(type) || "resumed".equals(type)) {
              if (!hasState)
                throw new IllegalStateException(
                    "Toy Box sent presence before the initial channel snapshot.");
              var presence = ChannelCodec.presence(event);
              boolean replace = "resumed".equals(type);
              deliver(
                  gen,
                  () -> {
                    state.presence(presence, replace);
                    if (replace) connected(gen);
                  });
              continue;
            }
            ChannelEvent detail = ChannelCodec.event(event);
            if (detail != null) {
              if (!hasState)
                throw new IllegalStateException(
                    "Toy Box sent detail before the initial channel snapshot.");
              deliver(gen, () -> state.event(detail));
            }
            continue;
          }
          ChannelModel.State snapshot = ChannelCodec.decode(event.getAsJsonObject("state"));
          if (!config.channelId().equals(snapshot.channel().id()))
            throw new IllegalArgumentException(
                "Toy Box returned a different channel. Check the server URL and channel ID.");
          hasState = true;
          snapshots++;
          deliver(
              gen,
              () -> {
                state.replace(snapshot);
                connected(gen);
              });
        }
      }
      if (running && gen == generation)
        throw new IllegalStateException("Toy Box closed the connection");
    } catch (Exception e) {
      if (running && gen == generation)
        retry(
            gen,
            e.getMessage() == null ? "Cannot reach Toy Box at " + config.server() : e.getMessage());
    }
  }

  /** A snapshot or resumed marker establishes a current view before live moments and sends. */
  private void connected(int gen) {
    attempt = 0;
    publish(gen, connection.with(Phase.LIVE, null));
  }

  /** Apply observations on the client thread only while their connection still owns the state. */
  private void deliver(int gen, Runnable update) {
    clientThread.execute(
        () -> {
          synchronized (this) {
            if (gen != generation || !running) return;
            try {
              update.run();
            } catch (Exception error) {
              retry(gen, "Invalid channel update: " + error.getMessage());
            }
          }
        });
  }

  private synchronized void retry(int gen, String reason) {
    if (!running || gen != generation) return;
    generation++;
    closeStream();
    long delay = Math.min(5000, 500L << Math.min(attempt, 3));
    publish(generation, connection.with(Phase.RETRYING, reason));
    int waiting = generation;
    timer.schedule(
        () -> {
          synchronized (this) {
            if (running && waiting == generation) connect();
          }
        },
        delay,
        TimeUnit.MILLISECONDS);
  }

  public CompletableFuture<Result> postMessage(String content) {
    if (content == null || content.isBlank() || content.length() > 12_000)
      return CompletableFuture.completedFuture(
          Result.failure("Write a message of 1–12,000 characters."));
    var body = new JsonObject();
    body.addProperty("id", UUID.randomUUID().toString());
    body.addProperty("content", content);
    return mutate("/messages", body, true);
  }

  public CompletableFuture<Result> markRead(long sequence) {
    if (sequence < 0)
      return CompletableFuture.completedFuture(
          Result.failure("Read sequence must be nonnegative."));
    var body = new JsonObject();
    body.addProperty("sequence", sequence);
    return mutate("/read", body, false);
  }

  private CompletableFuture<Result> mutate(String path, JsonObject body, boolean message) {
    if (!connection.connected())
      return CompletableFuture.completedFuture(
          Result.failure("Toy Box is offline; nothing was sent."));
    var request =
        HttpRequest.newBuilder(URI.create(config.endpoint() + path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
    return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .handleAsync(
            (response, failure) -> {
              if (failure != null) return unconfirmed(failure.getMessage());
              if (response.statusCode() < 200 || response.statusCode() >= 300)
                return Result.failure(error(response.statusCode(), response.body()));
              try {
                var payload = JsonParser.parseString(response.body()).getAsJsonObject();
                if (ChannelCodec.number(payload, "version") != 1)
                  throw new IllegalArgumentException("Unsupported response version");
                return new Result(
                    true,
                    null,
                    message ? ChannelCodec.message(payload.getAsJsonObject("message")) : null,
                    message ? null : ChannelCodec.number(payload, "seenThrough"));
              } catch (Exception e) {
                return unconfirmed("Invalid response: " + e.getMessage());
              }
            },
            clientThread);
  }

  /** A server's channels. Failures complete with a message the picker can show as is. */
  public static CompletableFuture<List<ChannelModel.Channel>> catalog(URI server) {
    var request =
        HttpRequest.newBuilder(URI.create(server + "/api/v1/channels"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();
    return CATALOG
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .handle(
            (response, failure) -> {
              if (failure != null)
                throw new IllegalStateException("Cannot reach this Toy Box server.");
              if (response.statusCode() != 200)
                throw new IllegalStateException(
                    "Unable to list channels (HTTP " + response.statusCode() + ").");
              var catalog = JsonParser.parseString(response.body()).getAsJsonObject();
              if (ChannelCodec.number(catalog, "version") != 1)
                throw new IllegalArgumentException("Unsupported Channel API version.");
              return catalog.getAsJsonArray("channels").asList().stream()
                  .map(value -> ChannelCodec.channel(value.getAsJsonObject()))
                  .toList();
            });
  }

  private static Result unconfirmed(String reason) {
    return Result.failure(
        "Delivery could not be confirmed. Check the channel before trying again. " + reason);
  }

  private static String error(int code, String body) {
    String detail = null;
    try {
      detail = ChannelCodec.text(JsonParser.parseString(body).getAsJsonObject(), "error");
    } catch (Exception ignored) {
    }
    return "Toy Box HTTP "
        + code
        + (detail == null ? ". Check the server URL and channel ID." : ": " + detail);
  }
}
