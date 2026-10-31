package dev.toybox.minecraft.client.channel;

import java.util.List;

/** Ordered durable transitions shared with the Toy Box Channel reducer. */
public sealed interface ChannelEvent
    permits ChannelEvent.Message,
        ChannelEvent.Reaction,
        ChannelEvent.Member,
        ChannelEvent.Status,
        ChannelEvent.Tasks,
        ChannelEvent.Model,
        ChannelEvent.Read {
  long revision();

  record Message(long revision, ChannelModel.Message message) implements ChannelEvent {}

  record Reaction(long revision, long sequence, String agentId, String reaction)
      implements ChannelEvent {}

  record Member(long revision, ChannelModel.Agent member) implements ChannelEvent {}

  record Status(long revision, String agentId, ChannelModel.Status status)
      implements ChannelEvent {}

  record Tasks(long revision, List<ChannelModel.Task> tasks) implements ChannelEvent {
    public Tasks {
      tasks = List.copyOf(tasks);
    }
  }

  record Model(long revision, ChannelModel.ModelConfiguration model, String updatedAt)
      implements ChannelEvent {}

  record Read(long revision, long seenThrough) implements ChannelEvent {}
}
