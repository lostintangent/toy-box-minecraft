package dev.toybox.minecraft.client.channel;

/** This client's observation, independent from server-owned channel work. */
public record ChannelConnection(Phase phase, String error, long sinceMs) {
  public enum Phase {
    DISABLED,
    CONNECTING,
    SYNCING,
    LIVE,
    RETRYING,
    DELETED
  }

  public boolean connected() {
    return phase == Phase.LIVE;
  }

  public String phaseName() {
    return phase.name().toLowerCase(java.util.Locale.ROOT);
  }

  ChannelConnection with(Phase next, String reason) {
    return new ChannelConnection(next, reason, System.currentTimeMillis());
  }
}
