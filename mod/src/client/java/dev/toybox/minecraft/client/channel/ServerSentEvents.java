package dev.toybox.minecraft.client.channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Server-sent event framing shared by channel observation and file watches. */
public final class ServerSentEvents {
  private final BufferedReader reader;
  private final int limit;
  private final BooleanSupplier received;

  /**
   * {@code received} runs after every line, so its owner can record inbound activity, and stops the
   * stream by returning false. Lines and events beyond {@code limit} characters fail the stream.
   */
  public ServerSentEvents(InputStream body, int limit, BooleanSupplier received) {
    this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
    this.limit = limit;
    this.received = received;
  }

  /** The next event's joined data lines, or null once the stream ends or is declined. */
  public String next() throws IOException {
    var data = new StringBuilder();
    String line;
    while ((line = line()) != null && received.getAsBoolean()) {
      if (line.startsWith("data:")) {
        if (!data.isEmpty()) data.append('\n');
        data.append(line.substring(5).stripLeading());
        if (data.length() > limit) throw new IOException("Server-sent event is too large");
      } else if (line.isEmpty() && !data.isEmpty()) return data.toString();
    }
    return null;
  }

  private String line() throws IOException {
    var out = new StringBuilder();
    int c;
    while ((c = reader.read()) != -1) {
      if (c == '\n') return out.toString();
      if (c != '\r') out.append((char) c);
      if (out.length() > limit) throw new IOException("Server-sent line is too large");
    }
    return out.isEmpty() ? null : out.toString();
  }
}
