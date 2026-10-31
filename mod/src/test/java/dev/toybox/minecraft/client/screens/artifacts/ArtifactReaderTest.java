package dev.toybox.minecraft.client.screens.artifacts;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Protocol-faithful read/watch fixture: file mutation, deletion, cancellation and stale response
 * races.
 */
public final class ArtifactReaderTest {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static void until(BooleanSupplier condition, String message) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
    check(condition.getAsBoolean(), message);
  }

  public static void main(String[] args) throws Exception {
    check(
        ArtifactReader.watchUri(URI.create("http://localhost:42/base/api/serve/machine/a%20b.md"))
            .toString()
            .equals("http://localhost:42/base/api/watch/machine/a%20b.md"),
        "Watch URL preserves base path and escaped file address");
    check(
        ArtifactReader.textType("application/octet-stream", "a.md"),
        "Markdown from generic serve MIME is readable");
    check(
        !ArtifactReader.textType("application/pdf", "a.pdf"),
        "Binary formats use browser fallback");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    Map<String, String> content = new ConcurrentHashMap<>();
    content.put("a.md", "# First");
    content.put("b.md", "# Second");
    Map<String, List<LinkedBlockingQueue<String>>> watches = new ConcurrentHashMap<>();
    AtomicInteger active = new AtomicInteger();
    CountDownLatch slowStarted = new CountDownLatch(1), releaseSlow = new CountDownLatch(1);
    server.createContext(
        "/api/serve/machine/",
        exchange -> {
          try {
            String name =
                exchange.getRequestURI().getPath().substring("/api/serve/machine/".length());
            if (name.equals("slow.md")) {
              slowStarted.countDown();
              try {
                releaseSlow.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            String value = name.equals("slow.md") ? "OLD RESPONSE" : content.get(name);
            if (name.equals("large.md")) value = "a".repeat(ArtifactReader.MAX_BYTES + 1);
            if (name.equals("binary.pdf")) value = "PDF";
            if (value == null) {
              exchange.sendResponseHeaders(404, -1);
              return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            exchange
                .getResponseHeaders()
                .add(
                    "Content-Type",
                    name.endsWith(".pdf") ? "application/pdf" : "application/octet-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (IOException ignored) {
          } finally {
            exchange.close();
          }
        });
    server.createContext(
        "/api/watch/machine/",
        exchange -> {
          String name =
              exchange.getRequestURI().getPath().substring("/api/watch/machine/".length());
          var queue = new LinkedBlockingQueue<String>();
          var observers = watches.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());
          observers.add(queue);
          active.incrementAndGet();
          try {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            var out = exchange.getResponseBody();
            out.write(
                "retry: 1000\n\ndata: {\"type\":\"modified\",\"timestamp\":1}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (true) {
              String event = queue.poll(100, TimeUnit.MILLISECONDS);
              String frame = event == null ? ": keepalive\n\n" : "data: " + event + "\n\n";
              out.write(frame.getBytes(StandardCharsets.UTF_8));
              out.flush();
            }
          } catch (IOException ignored) {
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            observers.remove(queue);
            active.decrementAndGet();
            exchange.close();
          }
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/serve/machine/";
    var seen = new CopyOnWriteArrayList<ArtifactReader.State>();
    ArtifactReader reader = new ArtifactReader(Runnable::run, seen::add);
    try {
      reader.select(URI.create(base + "a.md"), "a.md");
      until(
          () -> reader.state().text().equals("# First") && reader.state().watching(),
          "Initial file and active watch");
      content.put("a.md", "# Changed");
      send(watches, "a.md", "{\"type\":\"modified\",\"timestamp\":2}");
      until(() -> reader.state().text().equals("# Changed"), "Modification reloads actual bytes");
      content.remove("a.md");
      send(watches, "a.md", "{\"type\":\"deleted\"}");
      until(
          () ->
              reader.state().phase() == ArtifactReader.Phase.DELETED
                  && reader.state().text().isEmpty(),
          "Deletion clears old document");
      content.put("a.md", "# Recreated");
      send(watches, "a.md", "{\"type\":\"modified\",\"timestamp\":3}");
      until(() -> reader.state().text().equals("# Recreated"), "Recreation recovers on same watch");
      reader.select(URI.create(base + "slow.md"), "slow.md");
      check(slowStarted.await(3, TimeUnit.SECONDS), "Slow old fetch in flight");
      reader.select(URI.create(base + "b.md"), "b.md");
      check(reader.state().text().isEmpty(), "Selection clears old body synchronously");
      until(() -> reader.state().text().equals("# Second"), "New selection wins");
      releaseSlow.countDown();
      Thread.sleep(150);
      check(
          seen.stream().noneMatch(s -> s.text().equals("OLD RESPONSE")),
          "Late cancelled response never reaches listener");
      until(() -> active.get() == 1, "Reselect releases old watches");
      reader.select(URI.create(base + "large.md"), "large.md");
      until(
          () -> reader.state().phase() == ArtifactReader.Phase.UNSUPPORTED,
          "Oversized text uses explicit browser fallback");
      reader.select(URI.create(base + "binary.pdf"), "binary.pdf");
      until(
          () -> reader.state().phase() == ArtifactReader.Phase.UNSUPPORTED,
          "Binary MIME not decoded as text");
      reader.close();
      int callbacks = seen.size();
      until(() -> active.get() == 0, "Closing reader releases watch connection");
      check(seen.size() == callbacks, "No callbacks after close");
    } finally {
      reader.close();
      releaseSlow.countDown();
      server.stop(0);
      executor.shutdownNow();
    }
    stalledBodiesAndWatches();
    System.out.println("Artifact reader: " + checks + " checks passed");
  }

  private static void stalledBodiesAndWatches() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var network = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(network);
    var release = new CountDownLatch(1);
    var allowBody = new java.util.concurrent.atomic.AtomicBoolean();
    var reads = new AtomicInteger();
    var silentConnections = new AtomicInteger();
    var closingConnections = new AtomicInteger();
    server.createContext(
        "/api/serve/machine/",
        exchange -> {
          reads.incrementAndGet();
          String path = exchange.getRequestURI().getPath();
          try {
            exchange.getResponseHeaders().set("Content-Type", "text/markdown");
            if (path.endsWith("body.md") && !allowBody.get()) {
              exchange.sendResponseHeaders(200, 1000);
              exchange.getResponseBody().write('#');
              exchange.getResponseBody().flush();
              release.await(5, TimeUnit.SECONDS);
            } else {
              String text =
                  path.endsWith("body.md")
                      ? "# Body recovered"
                      : silentConnections.get() > 1
                          ? "# Refetched after silence"
                          : "# Before silence";
              byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(200, bytes.length);
              exchange.getResponseBody().write(bytes);
            }
          } catch (IOException ignored) {
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.createContext(
        "/api/watch/machine/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          int number =
              path.endsWith("silent.md")
                  ? silentConnections.incrementAndGet()
                  : path.endsWith("closing.md") ? closingConnections.incrementAndGet() : 0;
          try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            var out = exchange.getResponseBody();
            out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            if ((path.endsWith("silent.md") && number == 1) || path.endsWith("closing.md"))
              release.await(5, TimeUnit.SECONDS);
            else
              while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(40);
                out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
              }
          } catch (IOException ignored) {
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/serve/machine/";
    var seen = new CopyOnWriteArrayList<ArtifactReader.State>();
    var reader =
        new ArtifactReader(
            Runnable::run,
            seen::add,
            java.time.Duration.ofMillis(400),
            java.time.Duration.ofMillis(500),
            java.time.Duration.ofMillis(200));
    try {
      reader.select(URI.create(base + "body.md"), "body.md");
      until(
          () ->
              seen.stream()
                  .anyMatch(
                      s ->
                          s.phase() == ArtifactReader.Phase.ERROR
                              && s.notice().contains("timed out")),
          "headers with stalled file body reach a bounded error");
      check(reader.state().watching(), "healthy file watch does not hide a stalled body timeout");
      allowBody.set(true);
      until(
          () -> reader.state().text().equals("# Body recovered"),
          "timed-out body retries without requiring another modification event");
      reader.select(URI.create(base + "silent.md"), "silent.md");
      until(
          () -> reader.state().text().equals("# Before silence") && reader.state().watching(),
          "silent-watch fixture initially reads and connects");
      until(
          () ->
              seen.stream()
                  .anyMatch(
                      s ->
                          !s.watching()
                              && s.text().equals("# Before silence")
                              && s.notice().contains("Reconnecting")),
          "watch silence marks retained file stale");
      until(
          () ->
              silentConnections.get() >= 2
                  && reader.state().watching()
                  && reader.state().text().equals("# Refetched after silence"),
          "silent watch reconnects and refetches lost file changes");
      seen.clear();
      reader.select(URI.create(base + "closing.md"), "closing.md");
      until(
          () -> seen.stream().anyMatch(s -> !s.watching() && s.notice().contains("Reconnecting")),
          "closing fixture enters reconnect backoff");
      reader.close();
      int beforeClose = closingConnections.get(), readCount = reads.get(), callbacks = seen.size();
      Thread.sleep(800);
      check(
          closingConnections.get() == beforeClose && reads.get() == readCount,
          "closed reader cannot run pending retry or deadline work");
      check(seen.size() == callbacks, "closed reader suppresses deadline callbacks");
    } finally {
      reader.close();
      release.countDown();
      server.stop(0);
      network.shutdownNow();
    }
  }

  private static void send(
      Map<String, List<LinkedBlockingQueue<String>>> watches, String name, String event) {
    for (var observer : watches.getOrDefault(name, List.of())) observer.add(event);
  }
}
