package dev.toybox.minecraft.client.screens;

/** One channel's unsent message and pending send survive every transcript screen instance. */
public final class MessageDraft {
  public record Submission(long revision, String content) {}

  private String value = "";
  private String feedback = "";
  private long revision;
  private Submission pending;

  public String value() {
    return value;
  }

  public long revision() {
    return revision;
  }

  public String feedback() {
    return feedback;
  }

  public boolean sending() {
    return pending != null;
  }

  public void edit(String text) {
    if (text.equals(value)) return;
    value = text;
    revision++;
    feedback = "";
  }

  /** Navigation supplies context without discarding a message already being composed. */
  public void prefill(String prefix) {
    if (prefix == null || prefix.isBlank() || value.startsWith(prefix)) return;
    String next = prefix + value;
    // Preserve an existing draft in full if a context prefix would exceed the message limit.
    if (next.length() <= 12_000) edit(next);
  }

  public void notice(String text) {
    feedback = text;
  }

  public Submission begin() {
    if (pending != null || value.isBlank()) return null;
    pending = new Submission(revision, value.strip());
    feedback = "Sending…";
    return pending;
  }

  /** A receipt only clears the exact draft revision that its request submitted. */
  public boolean complete(Submission submission, boolean success, String error) {
    if (submission != pending) return false;
    pending = null;
    boolean clear = success && revision == submission.revision();
    if (clear) {
      value = "";
      revision++;
    }
    feedback = success ? "Posted to channel" : error;
    return clear;
  }
}
