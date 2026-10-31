package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import java.util.ArrayList;
import java.util.List;

/** Client-thread owner of the last authoritative state and this observer's connection. */
public final class ChannelStore {
  public interface Listener {
    default void onState(State state, List<ChannelChanges.Change> changes) {}

    default void onConnection(ChannelConnection connection) {}

    default void onCleared() {}
  }

  private final List<Listener> listeners = new ArrayList<>();
  private State state;
  private ChannelConnection connection;
  private long revision;

  public ChannelStore(ChannelConnection connection) {
    this.connection = connection;
  }

  public State state() {
    return state;
  }

  public Channel channel() {
    return state == null ? null : state.channel();
  }

  public ChannelConnection connection() {
    return connection;
  }

  public boolean hasData() {
    return state != null;
  }

  public boolean isStale() {
    return !connection.connected();
  }

  public long revision() {
    return revision;
  }

  public List<Agent> team() {
    return state == null ? List.of() : state.team();
  }

  public Agent agent(String id) {
    return state == null ? null : state.agent(id);
  }

  public List<Message> messages() {
    return state == null ? List.of() : state.messages();
  }

  public List<Task> tasks() {
    return state == null ? List.of() : state.tasks();
  }

  public List<Artifact> artifacts() {
    return state == null ? List.of() : state.artifacts();
  }

  public List<Routine> routines() {
    return state == null ? List.of() : state.routines();
  }

  public Message request() {
    return state == null ? null : state.request();
  }

  public Presence presence(String id) {
    return state == null ? Presence.IDLE : state.presence(id);
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /** Snapshots are authoritative recovery; only live changes create moments. */
  void replace(State next) {
    state = next;
    revision++;
    notifyListeners(listener -> listener.onState(next, List.of()));
  }

  void event(ChannelEvent event) {
    if (state == null)
      throw new IllegalStateException("Channel detail arrived before its snapshot");
    update(ChannelReducer.apply(state, event));
  }

  void presence(java.util.Map<String, Presence> presence, boolean replace) {
    if (state == null)
      throw new IllegalStateException("Channel presence arrived before its snapshot");
    update(ChannelReducer.presence(state, presence, replace));
  }

  private void update(State next) {
    if (next == state) return;
    // Catch-up updates the cached view quietly; only events after arrival create moments.
    List<ChannelChanges.Change> changes =
        connection.connected() ? ChannelChanges.diff(state, next) : List.of();
    state = next;
    revision++;
    notifyListeners(listener -> listener.onState(next, changes));
  }

  void connection(ChannelConnection next) {
    connection = next;
    revision++;
    notifyListeners(listener -> listener.onConnection(next));
  }

  void clear() {
    state = null;
    revision++;
    notifyListeners(Listener::onCleared);
  }

  private void notifyListeners(java.util.function.Consumer<Listener> action) {
    for (Listener listener : List.copyOf(listeners))
      try {
        action.accept(listener);
      } catch (RuntimeException error) {
        System.getLogger(ChannelStore.class.getName())
            .log(System.Logger.Level.ERROR, "Channel presentation listener failed", error);
      }
  }
}
