package dev.toybox.minecraft.client.agents;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure desk ownership: keep surviving assignments, fill free desks, never alias an occupied desk.
 */
public final class RosterLayout {
  private RosterLayout() {}

  /** Keep indoor seats, move overflow occupants inside when capacity opens, then retain pods. */
  public static Map<String, String> assignDesks(
      Map<String, String> previous,
      Collection<String> members,
      List<String> indoors,
      List<String> overflow) {
    Map<String, String> assigned = new LinkedHashMap<>(assign(previous, members, indoors));
    List<String> remaining = members.stream().filter(id -> !assigned.containsKey(id)).toList();
    assigned.putAll(assign(previous, remaining, overflow));
    return Map.copyOf(assigned);
  }

  public static Map<String, String> assign(
      Map<String, String> previous, Collection<String> members, List<String> desks) {
    Set<String> roster = Set.copyOf(members),
        available = Set.copyOf(desks),
        taken = new HashSet<>();
    Map<String, String> out = new LinkedHashMap<>();
    previous.forEach(
        (id, desk) -> {
          if (roster.contains(id) && available.contains(desk) && taken.add(desk)) out.put(id, desk);
        });
    for (String id : roster.stream().sorted().toList()) {
      if (out.containsKey(id)) continue;
      for (String desk : desks)
        if (taken.add(desk)) {
          out.put(id, desk);
          break;
        }
    }
    return Map.copyOf(out);
  }
}
