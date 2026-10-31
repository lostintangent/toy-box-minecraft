package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Exact preview of Toy Box's public message delivery policy; delivery remains server-owned. */
public final class ChannelAudience {
  private static final Pattern MENTIONS =
      Pattern.compile("(^|[^a-z0-9-])@([a-z0-9][a-z0-9-]*)", Pattern.CASE_INSENSITIVE);

  private ChannelAudience() {}

  public static String handle(String name) {
    String value =
        Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    if (value.length() > 48) value = value.substring(0, 48).replaceAll("-+$", "");
    return value.equals("everyone") ? "everyone-agent" : value.isEmpty() ? "teammate" : value;
  }

  public static List<Agent> resolve(
      State snapshot, String content, Sender sender, boolean acknowledgedRequest) {
    var handles = new HashSet<String>();
    var matcher = MENTIONS.matcher(content);
    boolean mentionAll = false;
    while (matcher.find()) {
      String value = matcher.group(2).toLowerCase(Locale.ROOT);
      if (value.equals("everyone")) mentionAll = true;
      else handles.add(value);
    }
    String senderId = sender.type().equals("agent") ? sender.agentId() : null;
    Agent lead = snapshot.lead();
    if (!mentionAll && handles.isEmpty())
      return lead.id().equals(senderId) ? List.of() : List.of(lead);
    final boolean everyone = mentionAll;
    return snapshot.team().stream()
        .filter(
            a ->
                !a.id().equals(senderId)
                    && (everyone
                        || handles.contains(handle(a.name()))
                        || (acknowledgedRequest && a.id().equals(lead.id()))))
        .toList();
  }

  public static String label(List<Agent> audience, int reachableCount) {
    return audience.size() > 1 && audience.size() == reachableCount
        ? "Everyone"
        : String.join(", ", audience.stream().map(Agent::name).toList());
  }
}
