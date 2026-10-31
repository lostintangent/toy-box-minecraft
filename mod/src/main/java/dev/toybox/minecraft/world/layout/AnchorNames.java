package dev.toybox.minecraft.world.layout;

import java.util.List;

/**
 * Named positions shared by the world builder, agent movement, station renderers and capture
 * tooling. Desk and monitor names use stable bindings, independent of channel membership.
 *
 * <pre>
 * desk_&lt;binding&gt;      feet spot at a desk, facing the monitor (e.g. desk_bay_0)
 * monitor_&lt;binding&gt;   centre of its monitor screen, yaw = screen front
 * &lt;station&gt;           shared station spot: artifacts, terminal, testbench, previewbench, waiting, lounge, user
 * &lt;station&gt;_2.._N     extra slots at the same station (agents spread over them, see {@link #slot})
 * task_board           centre of the Task Wall surface, yaw = front
 * request_podium       the podium block (centre top), yaw = front
 * channel_atrium       centre of the channel room's floor
 * entrance            just inside the main door, facing in
 * spawn               where the player should start
 * cam_&lt;name&gt;          QA camera points (eye position + yaw/pitch), e.g. cam_overview, cam_blockrow
 * </pre>
 */
public final class AnchorNames {
  public static final String DESK_PREFIX = "desk_";
  public static final String MONITOR_PREFIX = "monitor_";
  public static final String CAM_PREFIX = "cam_";

  public static final String ARTIFACTS = "artifacts";
  public static final String TERMINAL = "terminal";
  public static final String TESTBENCH = "testbench";
  public static final String PREVIEW_BENCH = "previewbench";
  public static final String WAITING = "waiting";
  public static final String LOUNGE = "lounge";
  public static final String WAITING_AREA = "waiting_area";
  public static final String USER = "user";

  public static final String TASK_BOARD = "task_board";
  public static final String REQUEST_PODIUM = "request_podium";
  public static final String CHANNEL_ATRIUM = "channel_atrium";
  public static final String TOY_BOX = "toy_box";
  public static final String ENTRANCE = "entrance";
  public static final String SPAWN = "spawn";

  /** Shared destinations for agents; desks are addressed by their individual bindings. */
  public static final List<String> SHARED_STATIONS =
      List.of(ARTIFACTS, TERMINAL, TESTBENCH, PREVIEW_BENCH, WAITING, LOUNGE, USER);

  private AnchorNames() {}

  public static String desk(String binding) {
    return DESK_PREFIX + binding;
  }

  public static String monitor(String binding) {
    return MONITOR_PREFIX + binding;
  }

  /**
   * Slot n (1-based) of a shared station: 1 = the station name itself, then {@code station_2}, ...
   */
  public static String slot(String station, int n) {
    return n <= 1 ? station : station + "_" + n;
  }
}
