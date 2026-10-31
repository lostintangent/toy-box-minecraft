package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.client.channel.ChannelCodec;
import dev.toybox.minecraft.client.channel.ChannelModel.*;
import dev.toybox.minecraft.world.DeskPodLayout;
import dev.toybox.minecraft.world.WorkshopDeskLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Deterministic artwork and placement contracts; no Minecraft runtime or network is needed. */
public final class AgentPresentationTest {
  private static int checks;

  public static void main(String[] args) throws Exception {
    artwork();
    activity();
    destinations();
    paths();
    roster();
    indoorDesks();
    pods();
    if (args.length > 0) preview(Path.of(args[0]));
    System.out.println("Agent presentation: " + checks + " checks passed");
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private static int[] pixels(BufferedImage image) {
    return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
  }

  private static void artwork() {
    String mark = "M4 12h16M12 4v16";
    var a = AvatarArt.skin("member-unique", "Member", mark, "#7c3aed");
    check(
        Arrays.equals(
            pixels(a), pixels(AvatarArt.skin("member-unique", "Member", mark, "#7c3aed"))),
        "Skin is deterministic");
    check(
        !AvatarArt.key("Aa").equals(AvatarArt.key("BB")),
        "Texture identity must not use collision-prone String.hashCode");
    check(AvatarArt.color("#AbC123") == 0xffabc123, "Exact authored color survives");
    check(
        !Arrays.equals(
            pixels(a), pixels(AvatarArt.skin("member-unique", "Member", mark, "#ef4444"))),
        "Avatar edits update skin pixels");
    check(
        AvatarArt.slim("member-unique") == AvatarArt.slim("member-unique"),
        "Structural model stable by ID");
    check(a.getWidth() == 256 && a.getHeight() == 256, "HD skin has standard square UV aspect");
    check(a.getRGB(8 * 4, 8 * 4) >>> 24 == 255, "Head front fully opaque");
    check(a.getRGB(20 * 4, 20 * 4) >>> 24 == 255, "Body front fully opaque");
    check(a.getRGB(63 * 4, 63 * 4) >>> 24 == 0, "Unused overlay pixels remain transparent");
    var icon = AvatarArt.portrait("Agent", mark, "#123456");
    check(icon.getRGB(0, 0) >>> 24 == 0, "Browser avatar frame has transparent corners");
    check(icon.getRGB(32, 5) == 0xff123456, "Badge retains exact authored background");
    check(
        AvatarArt.contrast(0xffffffff) == 0xff000000
            && AvatarArt.contrast(0xff000000) == 0xffffffff,
        "Browser contrast-color chooses black or white");
    check(
        (AvatarArt.luminance(AvatarArt.onDark(0xff111111)) + .05)
                / (AvatarArt.luminance(0xff25324b) + .05)
            >= 4.5,
        "Dark nameplate contrast");
  }

  private static void paths() {
    check(
        SvgMark.parse("M2 2h20v20h-20z").getBounds2D().getWidth() == 20,
        "Relative and closed path geometry");
    check(
        SvgMark.parse("M2 2L22 2 22 22 2 22Z").getBounds2D().getHeight() == 20,
        "Repeated coordinates");
    check(
        SvgMark.parse("M2e0 2Q12 0 22 2T22 22C20 20 15 15 12 12S2 8 2 2").getBounds2D().getWidth()
            > 0,
        "Curves and exponent coordinates");
    var arc = SvgMark.parse("M4 12A8 8 0 1 1 20 12A8 8 0 1 1 4 12").getBounds2D();
    check(
        Math.abs(arc.getWidth() - 16) < .001 && Math.abs(arc.getHeight() - 16) < .001,
        "SVG elliptical arc preserves circle bounds");
    for (String bad :
        List.of("<path/>", "L1 2", "Z", "M1 2 javascript:x", "M1e200 2", "M2 2 A2 2 0 3 1 5 5")) {
      try {
        SvgMark.parse(bad);
        throw new AssertionError("Unsafe/invalid path accepted: " + bad);
      } catch (IllegalArgumentException expected) {
        checks++;
      }
    }
  }

  private static void activity() throws Exception {
    var state = ChannelCodec.decode(Files.readString(Path.of("fixtures/channel-snapshot.json")));
    var agent =
        new Agent("channel", "agent", "Agent", null, null, null,
            new Status("working", "Reviewing", null, null, 12L));
    var view = new AgentView(agent.id());
    view.update(agent, new Presence("working", null, null), state, false);
    check(
        view.activity.startsWith("Working on") && view.workingOn == 12L,
        "Work target appears while working");
    view.update(agent, Presence.IDLE, state, false);
    check(
        view.activity.equals("Idle") && view.workingOn == null,
        "An idle nameplate never describes stale work");
  }

  private static void destinations() {
    check(AgentDestination.station("working", false).equals("desk"), "Working uses its desk");
    check(
        AgentDestination.station("waiting", false).equals("waiting"), "Waiting uses its own area");
    check(AgentDestination.station("idle", false).equals("lounge"), "Idle uses the lounge");
    for (String presence : List.of("working", "waiting", "idle"))
      check(
          AgentDestination.station(presence, true).equals("user"),
          "A public reply request takes priority over presence");
    check(
        List.of("waiting", "working", "idle", "waiting").stream()
            .map(state -> AgentDestination.station(state, false))
            .toList()
            .equals(List.of("waiting", "desk", "lounge", "waiting")),
        "Presence transitions move between distinct destinations");
    List<String> spots =
        List.of(
            "waiting",
            "waiting_2",
            "waiting_3",
            "waiting_4",
            "waiting_5",
            "waiting_6",
            "waiting_standing_6_-3",
            "waiting_standing_8_-3");
    List<String> members = List.of("a", "b", "c", "d", "e", "f", "g", "h");
    var initial = RosterLayout.assign(Map.of(), members, spots);
    check(
        initial.size() == 8 && initial.values().stream().distinct().count() == 8,
        "Waiting overflow uses unique validated standing candidates, not shared chairs");
    check(
        initial.equals(RosterLayout.assign(initial, members.reversed(), spots)),
        "Reconnection does not shuffle waiting seats or standing spots");
    var remaining = RosterLayout.assign(initial, List.of("a", "c", "d", "e", "f", "g", "h"), spots);
    check(
        remaining.entrySet().stream()
            .allMatch(entry -> initial.get(entry.getKey()).equals(entry.getValue())),
        "An agent returning to work leaves other waiting agents in place");
    check(
        initial.equals(RosterLayout.assign(remaining, members, spots)),
        "A returning waiter can reclaim its free spot without moving its peers");
  }

  private static void roster() {
    List<String> members = new ArrayList<>(), desks = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      members.add("member-" + i);
      desks.add("desk-" + i);
    }
    var first = RosterLayout.assign(Map.of(), members, desks);
    check(
        first.size() == 40 && first.values().stream().distinct().count() == 40,
        "Every member gets a unique physical desk beyond six");
    java.util.Collections.reverse(members);
    check(
        first.equals(RosterLayout.assign(first, members, desks)),
        "Reconnect/reordered snapshot does not reshuffle desks");
    members.remove("member-10");
    members.add("new-member");
    var changed = RosterLayout.assign(first, members, desks);
    check(
        changed.get("new-member").equals(first.get("member-10")),
        "Joined member takes the freed desk");
    check(
        members.stream()
            .filter(id -> !id.equals("new-member"))
            .allMatch(id -> changed.get(id).equals(first.get(id))),
        "Membership change leaves surviving identities in place");
    check(
        RosterLayout.assign(Map.of(), members, desks.subList(0, 6)).size() == 6,
        "No invented unchecked overflow slots");
  }

  private static void pods() {
    for (int i = 0; i < 150; i++) {
      var a = DeskPodLayout.candidate(i);
      check(a.maxZ() < -36, "Extensions never overlap managed workshop site");
      for (int j = 0; j < i; j++) {
        var b = DeskPodLayout.candidate(j);
        check(
            a.maxX() < b.minX()
                || b.maxX() < a.minX()
                || a.maxZ() < b.minZ()
                || b.maxZ() < a.minZ(),
            "Desk footprints never collide");
      }
    }
  }

  private static void indoorDesks() {
    var centers = WorkshopDeskLayout.CENTERS;
    check(centers.size() == 12, "The default workshop has twelve indoor bays");
    check(
        centers.subList(0, 6).equals(List.of(-15, -11, -7, 7, 11, 15)),
        "Existing six bays retain their positions");
    for (int i = 0; i < centers.size(); i++) {
      check(Math.abs(centers.get(i)) + 1 < 24, "Three-wide desk stays inside workshop walls");
      for (int j = 0; j < i; j++) {
        check(
            Math.abs(centers.get(i) - centers.get(j)) >= 3,
            "Indoor desk and monitor footprints never overlap");
        int left = Math.min(centers.get(i), centers.get(j));
        int right = Math.max(centers.get(i), centers.get(j));
        check(
            WorkshopDeskLayout.monitorMaxX(left) + 1 < WorkshopDeskLayout.monitorMinX(right),
            "Separate owners' monitor panels never connect across desk boundaries");
      }
    }
    var members = java.util.stream.IntStream.range(0, 9).mapToObj(i -> "member-" + i).toList();
    var bays = java.util.stream.IntStream.range(0, 12).mapToObj(i -> "desk_bay_" + i).toList();
    var pods = List.of("desk_pod_0", "desk_pod_1", "desk_pod_2");
    var old = RosterLayout.assignDesks(Map.of(), members, bays.subList(0, 6), pods);
    var expanded = RosterLayout.assignDesks(old, members, bays, pods);
    check(
        expanded.size() == 9 && expanded.values().stream().allMatch(bays::contains),
        "Existing nine-member worlds move every pod occupant indoors after expansion");
    check(
        old.entrySet().stream()
            .filter(e -> bays.contains(e.getValue()))
            .allMatch(e -> expanded.get(e.getKey()).equals(e.getValue())),
        "The six existing indoor assignments survive expansion");
    check(
        expanded.equals(RosterLayout.assignDesks(expanded, members.reversed(), bays, pods)),
        "Indoor assignment survives reordered reconnects");
    var large = java.util.stream.IntStream.range(0, 15).mapToObj(i -> "member-" + i).toList();
    var overflow = RosterLayout.assignDesks(expanded, large, bays, pods);
    check(
        overflow.size() == 15 && overflow.values().stream().filter(pods::contains).count() == 3,
        "Only members beyond twelve indoor desks use outdoor pods");
  }

  private static void preview(Path path) throws Exception {
    var image = new BufferedImage(960, 340, BufferedImage.TYPE_INT_ARGB);
    var g = image.createGraphics();
    g.setColor(new Color(0xfff7e8));
    g.fillRect(0, 0, 960, 340);
    g.setColor(new Color(0x25324b));
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    g.drawString("Toy Box · native avatar recipe", 24, 34);
    String[] colors = {"#0891b2", "#7c3aed", "#e85d4a", "#16a34a", "#f5c542", "#234567"};
    String[] marks = {
      "M12 3l3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z",
      "M5 12h14M12 5v14",
      "M3 6h18v12H3zM7 10h10M7 14h6",
      "M4 12A8 8 0 1 1 20 12A8 8 0 1 1 4 12",
      "M3 16l5-8 4 5 5-9 4 12",
      "M5 5h14v14H5zM9 9h6v6H9z"
    };
    for (int i = 0; i < 6; i++) {
      int x = 24 + i * 156;
      String id = "native-member-" + i;
      var skin = AvatarArt.skin(id, "Member " + i, marks[i], colors[i]);
      g.drawImage(AvatarArt.portrait("Member " + i, marks[i], colors[i]), x + 42, 54, 64, 64, null);
      // Actual front UV samples: 8x8 head, 8x12 torso, 4x12 legs, 3/4x12 arms.
      int arm = AvatarArt.slim(id) ? 3 : 4, s = 4, bx = x + 56, by = 140;
      part(g, skin, bx, by, 8, 8, 8, 8, s);
      part(g, skin, bx, by + 32, 20, 20, 8, 12, s);
      part(g, skin, bx - arm * s, by + 32, 44, 20, arm, 12, s);
      part(g, skin, bx + 32, by + 32, 36, 52, arm, 12, s);
      part(g, skin, bx, by + 80, 4, 20, 4, 12, s);
      part(g, skin, bx + 16, by + 80, 20, 52, 4, 12, s);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
      g.drawString("Stable member " + i, x + 26, 306);
    }
    g.dispose();
    Files.createDirectories(path.getParent());
    ImageIO.write(image, "png", path.toFile());
  }

  private static void part(
      java.awt.Graphics2D g,
      BufferedImage image,
      int x,
      int y,
      int u,
      int v,
      int w,
      int h,
      int scale) {
    g.drawImage(
        image, x, y, x + w * scale, y + h * scale, u * 4, v * 4, (u + w) * 4, (v + h) * 4, null);
  }
}
