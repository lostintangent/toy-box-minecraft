package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelContractTest.require;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A protocol-faithful server verifies transport lifetimes and actual outbound native capabilities.
 */
public final class ChannelTransportTest {
  public static void run() throws Exception {
    failedConnectionCannotPublishQueuedSnapshot();
    httpAndSseLifetime();
  }

  private static void httpAndSseLifetime() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var network = Executors.newVirtualThreadPerTaskExecutor();
    var client = Executors.newSingleThreadExecutor();
    server.setExecutor(network);
    var shutdown = new CountDownLatch(1);
    var first = new CountDownLatch(1);
    var second = new CountDownLatch(1);
    var live = new CountDownLatch(1);
    var resumedLive = new CountDownLatch(1);
    var refreshed = new CountDownLatch(1);
    var reset = new CountDownLatch(1);
    var deleted = new CountDownLatch(1);
    var connections = new AtomicInteger();
    var posts = new AtomicInteger();
    var reads = new AtomicInteger();
    var effects = new AtomicInteger();
    var stream = new AtomicReference<OutputStream>();
    var received = new AtomicReference<JsonObject>();
    var source = new AtomicReference<>(ChannelContractTest.fixture());
    server.createContext(
        "/api/v1/channels/channel-native",
        exchange -> {
          // An h2c upgrade stalls the real Bun/Vite stream route: require plain HTTP/1.1.
          if (exchange.getRequestHeaders().getFirst("Upgrade") != null) {
            exchange.sendResponseHeaders(426, -1);
            exchange.close();
            return;
          }
          if (exchange.getRequestMethod().equals("POST")) {
            var body =
                JsonParser.parseString(
                        new String(
                            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            boolean read = exchange.getRequestURI().getPath().endsWith("/read");
            int code = 200;
            String reply;
            if (read) {
              reads.incrementAndGet();
              reply =
                  "{\"version\":1,\"seenThrough\":"
                      + Math.min(body.get("sequence").getAsLong(), 2)
                      + "}";
            } else {
              received.set(body);
              int count = posts.incrementAndGet();
              code = count == 1 ? 201 : 500;
              reply =
                  count == 1
                      ? "{\"version\":1,\"message\":"
                          + ChannelContractTest.messageJson(
                              body.get("id").getAsString(),
                              3,
                              "user",
                              null,
                              body.get("content").getAsString())
                          + "}"
                      : "{\"error\":\"Fixture rejected message\"}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            try (var out = exchange.getResponseBody()) {
              out.write(bytes);
            }
            return;
          }
          require(
              "text/event-stream".equals(exchange.getRequestHeaders().getFirst("Accept")),
              "native SSE negotiation");
          int attempt = connections.incrementAndGet();
          require(
              java.util.Objects.equals(
                  exchange.getRequestURI().getRawQuery(),
                  attempt == 1 ? null : attempt == 2 ? "after=7" : "after=9"),
              "reconnect resumes the last applied durable revision");
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          OutputStream out = exchange.getResponseBody();
          stream.set(out);
          if (attempt == 2) {
            emit(
                out,
                "{\"type\":\"message\",\"revision\":8,\"message\":"
                    + ChannelContractTest.messageJson("offline", 4, "agent", "a", "Offline")
                    + "}");
            emit(out, "{\"type\":\"resumed\",\"presence\":{\"a\":{\"state\":\"idle\"}}}");
          } else snapshot(out, source.get());
          try {
            shutdown.await(20, TimeUnit.SECONDS);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
          try {
            out.close();
          } catch (Exception ignored) {
          }
        });
    server.start();
    var state = new ChannelStore(connection());
    var transport = new ChannelTransport(config(server), state, client);
    state.addListener(
        new ChannelStore.Listener() {
          public void onConnection(ChannelConnection value) {
            if (value.connected()) {
              if (connections.get() == 1) first.countDown();
              else second.countDown();
            }
          }

          public void onState(ChannelModel.State value, List<ChannelChanges.Change> changes) {
            int count =
                (int)
                    changes.stream().filter(ChannelChanges.MessagePosted.class::isInstance).count();
            effects.addAndGet(count);
            if (count > 0) live.countDown();
            if (value.messages().getLast().id().equals("resumed-live")) resumedLive.countDown();
            if (value.revision() == 7 && value.presence("a").state().equals("idle"))
              refreshed.countDown();
            if (value.channel().name().equals("Recovered source")) reset.countDown();
          }

          public void onCleared() {
            deleted.countDown();
          }
        });
    try {
      transport.start();
      require(first.await(5, TimeUnit.SECONDS), "initial HTTP/1.1 snapshot arrives");
      require(state.team().size() == 3 && effects.get() == 0, "quiet authoritative hydration");
      var receipt = transport.postMessage("@zoe-artist hello").get(5, TimeUnit.SECONDS);
      require(
          receipt.ok() && receipt.message().sender().type().equals("user"),
          "canonical message receipt decoded");
      require(
          received.get().get("content").getAsString().equals("@zoe-artist hello")
              && !received.get().get("id").getAsString().isBlank(),
          "native UUID message boundary");
      var read = transport.markRead(999).get(5, TimeUnit.SECONDS);
      require(
          read.ok() && read.seenThrough() == 2 && reads.get() == 1,
          "clamped canonical read receipt");
      require(state.request() != null, "read receipt never fabricates request acknowledgement");
      require(
          !transport.postMessage("Reject").get(5, TimeUnit.SECONDS).ok(),
          "failed POST returns error");
      var next = source.get().deepCopy();
      next.getAsJsonObject("channel").addProperty("latestSequence", 3);
      next.addProperty("revision", 4);
      next.getAsJsonArray("messages")
          .add(ChannelContractTest.messageJson("live", 3, "agent", "a", "Live"));
      source.set(next);
      emit(
          stream.get(),
          "{\"type\":\"message\",\"revision\":4,\"message\":"
              + ChannelContractTest.messageJson("live", 3, "agent", "a", "Live")
              + "}");
      require(
          live.await(5, TimeUnit.SECONDS) && effects.get() == 1,
          "live snapshot emits one message effect");
      emit(stream.get(), "{\"type\":\"tasks\",\"revision\":5,\"tasks\":[]}");
      emit(
          stream.get(),
          "{\"type\":\"model\",\"revision\":6,\"model\":{\"provider\":\"codex\",\"name\":\"changed\"},\"updatedAt\":\"2026-10-03T12:00:00Z\"}");
      emit(stream.get(), "{\"type\":\"read\",\"revision\":7,\"seenThrough\":3}");
      emit(stream.get(), "{\"type\":\"presence\",\"presence\":{\"a\":{\"state\":\"idle\"}}}");
      require(
          refreshed.await(5, TimeUnit.SECONDS) && effects.get() == 1,
          "granular updates do not duplicate the message effect");
      require(
          transport.snapshotCount() == 1
              && state.channel().latestSequence() == 3
              && state.tasks().isEmpty()
              && state.channel().model().name().equals("changed")
              && state.channel().seenThrough() == 3
              && state.request() != null,
          "all granular event types reach native state without replacement snapshots");
      next = next.deepCopy();
      next.getAsJsonObject("channel").addProperty("latestSequence", 4);
      next.addProperty("revision", 8);
      next.getAsJsonArray("messages")
          .add(ChannelContractTest.messageJson("offline", 4, "agent", "a", "Offline"));
      source.set(next);
      stream.get().close();
      require(second.await(7, TimeUnit.SECONDS), "EOF reconnect catches up and resumes");
      require(
          state.messages().size() == 4
              && effects.get() == 1
              && transport.snapshotCount() == 1
              && state.tasks().isEmpty()
              && state.channel().model().name().equals("changed")
              && state.channel().seenThrough() == 3
              && state.presence("a").state().equals("idle"),
          "replay retains cached state without another snapshot or historical effects");
      require(posts.get() == 2 && reads.get() == 1, "no mutation retry on reconnect");
      emit(
          stream.get(),
          "{\"type\":\"message\",\"revision\":9,\"message\":"
              + ChannelContractTest.messageJson("resumed-live", 5, "agent", "a", "Live again")
              + "}");
      require(
          resumedLive.await(5, TimeUnit.SECONDS) && effects.get() == 2,
          "events after resumed create live moments exactly once");
      var recovery = next.deepCopy();
      recovery.getAsJsonObject("channel").addProperty("name", "Recovered source");
      recovery.getAsJsonObject("channel").addProperty("latestSequence", 6);
      recovery.addProperty("revision", 10);
      var recoveredMessages = new com.google.gson.JsonArray();
      recoveredMessages.add(
          ChannelContractTest.messageJson(
              "gap-new", 6, "agent", "a", "Recovered, not a live bubble"));
      recovery.add("messages", recoveredMessages);
      source.set(recovery);
      transport.reconnect();
      require(reset.await(5, TimeUnit.SECONDS), "full snapshot reaches authoritative recovery");
      require(
          state.messages().size() == 1
              && state.messages().getFirst().id().equals("gap-new")
              && effects.get() == 2
              && transport.snapshotCount() == 2,
          "unavailable replay falls back to a quiet replacement snapshot");
      emit(stream.get(), "{\"type\":\"deleted\",\"channelId\":\"channel-native\"}");
      require(deleted.await(5, TimeUnit.SECONDS), "deleted event reaches state");
      require(state.team().isEmpty(), "deleted snapshot is cleared");
      int atDelete = connections.get();
      Thread.sleep(1200);
      require(
          connections.get() == atDelete
              && transport.connection().phase() == ChannelConnection.Phase.DELETED,
          "deletion stops reconnecting");
      require(
          !transport.postMessage("After deletion").get().ok() && posts.get() == 2,
          "deleted channel cannot receive messages");
    } finally {
      transport.stop();
      shutdown.countDown();
      server.stop(0);
      network.shutdownNow();
      client.shutdownNow();
    }
  }

  private static void failedConnectionCannotPublishQueuedSnapshot() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var network = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(network);
    var queued = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    server.createContext(
        "/api/v1/channels/channel-native",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          try (var out = exchange.getResponseBody()) {
            snapshot(out, ChannelContractTest.fixture());
          }
        });
    server.start();
    var state = new ChannelStore(connection());
    var transport = new ChannelTransport(config(server), state, queued::add);
    try {
      transport.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (transport.connection().phase() != ChannelConnection.Phase.RETRYING
          && System.nanoTime() < deadline) Thread.sleep(5);
      require(
          transport.connection().phase() == ChannelConnection.Phase.RETRYING,
          "EOF invalidates connection generation");
      Runnable task;
      while ((task = queued.poll()) != null) task.run();
      require(
          !state.hasData() && !state.connection().connected(),
          "queued obsolete callback cannot hydrate or mark live");
    } finally {
      transport.stop();
      server.stop(0);
      network.shutdownNow();
    }
  }

  private static ChannelConnection connection() {
    return new ChannelConnection(
        ChannelConnection.Phase.CONNECTING, null, System.currentTimeMillis());
  }

  private static ChannelConfig config(HttpServer server) {
    return new ChannelConfig(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        "channel-native",
        null,
        Path.of("test-config.json"));
  }

  private static void snapshot(OutputStream out, JsonObject snapshot) throws java.io.IOException {
    emit(out, "{\"type\":\"snapshot\",\"state\":" + snapshot + "}");
  }

  private static void emit(OutputStream out, String json) throws java.io.IOException {
    out.write((": keepalive\n\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }
}
