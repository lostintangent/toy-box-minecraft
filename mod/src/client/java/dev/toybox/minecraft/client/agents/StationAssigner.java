package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.world.layout.Anchor;
import dev.toybox.minecraft.world.layout.AnchorNames;
import dev.toybox.minecraft.world.layout.Anchors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Sticky physical desk ownership. Avatar identity never depends on a desk or roster index. */
public final class StationAssigner {
  private Map<String, String> assigned = Map.of();

  public void assign(List<String> ids, Anchors.Layout layout) {
    List<String> desks =
        layout.anchors().keySet().stream()
            .filter(n -> n.startsWith(AnchorNames.DESK_PREFIX))
            .sorted(
                java.util.Comparator.comparingInt(StationAssigner::slotNumber)
                    .thenComparing(n -> n))
            .toList();
    assigned =
        RosterLayout.assignDesks(
            assigned,
            ids,
            desks.stream().filter(name -> !name.startsWith("desk_pod_")).toList(),
            desks.stream().filter(name -> name.startsWith("desk_pod_")).toList());
  }

  private static int slotNumber(String name) {
    try {
      return Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
    } catch (NumberFormatException ignored) {
      return Integer.MAX_VALUE;
    }
  }

  public @Nullable String assignedSlot(String id) {
    return assigned.get(id);
  }

  public @Nullable String binding(String id) {
    String desk = assigned.get(id);
    return desk == null ? null : desk.substring(AnchorNames.DESK_PREFIX.length());
  }

  public @Nullable String agentAtDesk(String binding) {
    return assigned.entrySet().stream()
        .filter(e -> e.getValue().equals(AnchorNames.desk(binding)))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  public void clear() {
    assigned = Map.of();
  }

  public static List<Anchor> slots(Anchors.Layout layout, String station) {
    List<Anchor> out = new ArrayList<>();
    for (int n = 1; n <= 256; n++) {
      Anchor a = layout.get(AnchorNames.slot(station, n));
      if (a == null) break;
      out.add(a);
    }
    return out;
  }
}
