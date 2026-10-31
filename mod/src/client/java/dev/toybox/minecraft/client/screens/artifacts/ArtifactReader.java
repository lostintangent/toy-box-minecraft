package dev.toybox.minecraft.client.screens.artifacts;

import com.google.gson.JsonParser;
import dev.toybox.minecraft.client.channel.ServerSentEvents;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One selected file's HTTP read/watch lifetime. Closing/reselecting cancels both streams and
 * rejects old work.
 */
public final class ArtifactReader implements AutoCloseable {
  public enum Phase {
    LOADING,
    READY,
    DELETED,
    UNSUPPORTED,
    ERROR
  }

  public record State(Phase phase, String text, String notice, boolean watching, long revision) {}

  public static final int MAX_BYTES = 1024 * 1024;
  private final HttpClient http =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
  private final ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "ToyBox-artifact-deadlines");
            thread.setDaemon(true);
            return thread;
          });
  private final Duration bodyTimeout, watchSilence, retryDelay;
  private final Consumer<Runnable> dispatch;
  private final Consumer<State> listener;
  private long generation, readVersion, revision;
  private URI uri;
  private String filename = "";
  private Future<?> readTask, watchTask;
  private ScheduledFuture<?> readDeadline, readRetry;
  private InputStream readBody, watchBody;
  private long watchInboundAt;
  private boolean closed;
  private volatile State state = new State(Phase.LOADING, "", "Choose an artifact", false, 0);

  public ArtifactReader(Consumer<Runnable> dispatch, Consumer<State> listener) {
    this(dispatch, listener, Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(1));
  }

  ArtifactReader(
      Consumer<Runnable> dispatch,
      Consumer<State> listener,
      Duration bodyTimeout,
      Duration watchSilence,
      Duration retryDelay) {
    if (bodyTimeout.isNegative()
        || bodyTimeout.isZero()
        || watchSilence.isNegative()
        || watchSilence.isZero()
        || retryDelay.isNegative()
        || retryDelay.isZero()) throw new IllegalArgumentException("Deadlines must be positive");
    this.dispatch = dispatch;
    this.listener = listener;
    this.bodyTimeout = bodyTimeout;
    this.watchSilence = watchSilence;
    this.retryDelay = retryDelay;
    long interval = Math.max(1, Math.min(250, watchSilence.toMillis() / 4));
    timer.scheduleAtFixedRate(this::checkWatchDeadline, interval, interval, TimeUnit.MILLISECONDS);
  }

  public State state() {
    return state;
  }

  public synchronized void select(URI serve, String filename) {
    if (closed) return;
    cancel();
    generation++;
    this.uri = serve;
    this.filename = filename == null ? "" : filename;
    long current = generation;
    if (!validServe(serve)) {
      publish(current, Phase.UNSUPPORTED, "", "Open this artifact in the browser.", false);
      return;
    }
    publish(current, Phase.LOADING, "", "Loading artifact…", false);
    refresh(current);
    watchTask = io.submit(() -> watch(current));
  }

  public synchronized void clear(String notice) {
    if (closed) return;
    cancel();
    generation++;
    uri = null;
    publish(generation, Phase.DELETED, "", notice, false);
  }

  private static boolean validServe(URI uri) {
    return uri != null
        && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        && uri.getUserInfo() == null
        && uri.getRawPath().contains("/api/serve/");
  }

  public static URI watchUri(URI serve) {
    if (!validServe(serve)) throw new IllegalArgumentException("Not a Toy Box file URL");
    String value = serve.toASCIIString();
    int route = value.indexOf("/api/serve/");
    return URI.create(value.substring(0, route) + "/api/watch/" + value.substring(route + 11));
  }

  private synchronized boolean current(long g) {
    return !closed && g == generation;
  }

  private synchronized void refresh(long g) {
    if (!current(g)) return;
    cancelReadTimers();
    if (readTask != null) readTask.cancel(true);
    close(readBody);
    readBody = null;
    long ticket = ++readVersion;
    URI source = uri;
    String name = filename;
    readTask = io.submit(() -> read(g, ticket, source, name));
  }

  private void read(long g, long ticket, URI source, String name) {
    try {
      var response =
          http.send(
              HttpRequest.newBuilder(source)
                  .timeout(bodyTimeout)
                  .header("Accept", "text/plain, text/markdown, application/json, */*;q=0.5")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        synchronized (this) {
          if (!current(g) || ticket != readVersion) return;
          readBody = body;
          readDeadline =
              timer.schedule(
                  () -> expireRead(g, ticket), bodyTimeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        if (response.statusCode() == 404) {
          loaded(g, ticket, Phase.DELETED, "", "This file was deleted or is no longer available.");
          return;
        }
        if (response.statusCode() != 200) {
          loaded(
              g,
              ticket,
              Phase.ERROR,
              "",
              "Unable to read artifact (HTTP " + response.statusCode() + ").");
          return;
        }
        String type =
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream")
                .toLowerCase(Locale.ROOT);
        long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (!textType(type, name)) {
          loaded(
              g,
              ticket,
              Phase.UNSUPPORTED,
              "",
              "This file opens in the browser (" + type.split(";")[0] + ").");
          return;
        }
        if (size > MAX_BYTES) {
          loaded(
              g,
              ticket,
              Phase.UNSUPPORTED,
              "",
              "This file is larger than 1 MiB. Open it in the browser.");
          return;
        }
        byte[] bytes = body.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
          loaded(
              g,
              ticket,
              Phase.UNSUPPORTED,
              "",
              "This file is larger than 1 MiB. Open it in the browser.");
          return;
        }
        String text =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        if (text.indexOf('\0') >= 0) {
          loaded(
              g,
              ticket,
              Phase.UNSUPPORTED,
              "",
              "This file contains binary data. Open it in the browser.");
          return;
        }
        loaded(g, ticket, Phase.READY, text, "");
      }
    } catch (java.nio.charset.CharacterCodingException e) {
      loaded(
          g, ticket, Phase.UNSUPPORTED, "", "This file is not UTF-8 text. Open it in the browser.");
    } catch (Exception e) {
      if (!Thread.currentThread().isInterrupted())
        loaded(
            g,
            ticket,
            Phase.ERROR,
            "",
            "Unable to read artifact. It will retry when file observation reconnects.");
    } finally {
      synchronized (this) {
        if (current(g) && ticket == readVersion) {
          readBody = null;
          if (readDeadline != null) readDeadline.cancel(false);
          readDeadline = null;
        }
      }
    }
  }

  static boolean textType(String mime, String filename) {
    String type = mime.split(";", 2)[0].trim();
    if (type.startsWith("text/")
        || type.equals("application/json")
        || type.equals("application/javascript")
        || type.equals("application/xml")) return true;
    // Toy Box serves unlisted extensions, including Markdown, as octet-stream.
    return type.equals("application/octet-stream")
        && filename
            .toLowerCase(Locale.ROOT)
            .matches(
                ".*\\.(md|markdown|mdx|txt|log|csv|tsv|json|jsonc|yaml|yml|toml|ini|py|js|ts|tsx|jsx|java|rs|go|sh|css|sql)$");
  }

  private synchronized void loaded(long g, long ticket, Phase phase, String text, String notice) {
    if (current(g) && ticket == readVersion) {
      if (readDeadline != null) readDeadline.cancel(false);
      readDeadline = null;
      readBody = null;
      if (phase == Phase.READY && !state.watching() && state.notice().startsWith("Reconnecting"))
        notice = state.notice();
      publish(g, phase, text, notice, state.watching());
    }
  }

  private synchronized void expireRead(long g, long ticket) {
    if (!current(g) || ticket != readVersion || readBody == null) return;
    readVersion++;
    if (readTask != null) readTask.cancel(true);
    close(readBody);
    readBody = null;
    readDeadline = null;
    publish(g, Phase.ERROR, "", "Artifact read timed out. Retrying…", state.watching());
    long expected = readVersion;
    readRetry =
        timer.schedule(
            () -> {
              synchronized (this) {
                if (current(g) && readVersion == expected) refresh(g);
              }
            },
            retryDelay.toNanos(),
            TimeUnit.NANOSECONDS);
  }

  private synchronized void checkWatchDeadline() {
    if (closed || watchBody == null || System.nanoTime() - watchInboundAt < watchSilence.toNanos())
      return;
    InputStream stalled = watchBody;
    watchBody = null;
    publish(generation, state.phase(), state.text(), "Reconnecting to live file changes…", false);
    close(stalled); // Unblocks the watch's reader, whose owner performs backoff and refetch.
  }

  private void watch(long g) {
    int attempt = 0;
    while (current(g) && !Thread.currentThread().isInterrupted()) {
      try {
        URI target;
        synchronized (this) {
          target = watchUri(uri);
        }
        var response =
            http.send(
                HttpRequest.newBuilder(target)
                    .timeout(bodyTimeout)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
          synchronized (this) {
            if (!current(g)) return;
            watchBody = body;
            watchInboundAt = System.nanoTime();
          }
          if (response.statusCode() != 200)
            throw new IOException("Watch HTTP " + response.statusCode());
          if (!response
              .headers()
              .firstValue("Content-Type")
              .orElse("")
              .startsWith("text/event-stream")) throw new IOException("Not SSE");
          synchronized (this) {
            if (current(g))
              publish(
                  g,
                  state.phase(),
                  state.text(),
                  state.phase() == Phase.READY ? "" : state.notice(),
                  true);
          }
          attempt = 0;
          refresh(g); // Every reconnect refetches, independent from any lost modification event.
          var events =
              new ServerSentEvents(
                  body,
                  65536,
                  () -> {
                    synchronized (this) {
                      if (!current(g)) return false;
                      watchInboundAt = System.nanoTime();
                      return true;
                    }
                  });
          String event;
          while ((event = events.next()) != null) watchEvent(g, event);
        }
      } catch (Exception e) {
        if (Thread.currentThread().isInterrupted()) return;
      } finally {
        synchronized (this) {
          if (current(g)) watchBody = null;
        }
      }
      synchronized (this) {
        if (!current(g)) return;
        publish(
            g,
            state.phase(),
            state.text(),
            state.phase() == Phase.READY ? "Reconnecting to live file changes…" : state.notice(),
            false);
      }
      try {
        Thread.sleep(Math.min(10000, retryDelay.toMillis() << Math.min(attempt++, 3)));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private synchronized void watchEvent(long g, String data) {
    if (!current(g)) return;
    try {
      var event = JsonParser.parseString(data).getAsJsonObject();
      String type = event.get("type").getAsString();
      if (type.equals("modified")) refresh(g);
      else if (type.equals("deleted")) {
        cancelReadTimers();
        readVersion++;
        if (readTask != null) readTask.cancel(true);
        close(readBody);
        readBody = null;
        publish(
            g,
            Phase.DELETED,
            "",
            "This file was deleted. The reader will update if it is recreated.",
            true);
      }
    } catch (RuntimeException ignored) {
    }
  }

  private synchronized void publish(
      long g, Phase phase, String text, String notice, boolean watching) {
    if (!current(g)) return;
    State next = new State(phase, text, notice, watching, ++revision);
    state = next;
    dispatch.accept(
        () -> {
          synchronized (this) {
            if (!current(g) || state.revision() != next.revision()) return;
          }
          listener.accept(next);
        });
  }

  private void cancel() {
    cancelReadTimers();
    readVersion++;
    if (readTask != null) readTask.cancel(true);
    if (watchTask != null) watchTask.cancel(true);
    close(readBody);
    close(watchBody);
    readBody = null;
    watchBody = null;
  }

  private void cancelReadTimers() {
    if (readDeadline != null) readDeadline.cancel(false);
    if (readRetry != null) readRetry.cancel(false);
    readDeadline = null;
    readRetry = null;
  }

  private static void close(Closeable c) {
    if (c != null)
      try {
        c.close();
      } catch (IOException ignored) {
      }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    generation++;
    cancel();
    io.shutdownNow();
    timer.shutdownNow();
    http.shutdownNow();
  }
}
