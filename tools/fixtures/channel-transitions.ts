import { expect } from "bun:test";
import { machineFile, workspaceFileId } from "@files/model";
import type { ChannelObservationEvent, ChannelState } from "@channels/model";
import { reduceChannelState } from "@channels/model/reducer";
import type { ChannelDatabase, ChannelChange } from "@channels/server/database";
import { resolveEventResources } from "@channels/server/resources";
import { getChannelSnapshot } from "@channels/server/stream";
import { channelAgent } from "@channels/server/testFixtures";

/** Real commits pin SQL, the TypeScript reducer, and the Java client to one contract. */
export async function channelTransitionFixture(database: ChannelDatabase) {
  const id = "protocol-conformance";
  await database.createChannel(
    { name: "Protocol", model: { provider: "codex", name: "test" } },
    channelAgent(id, id),
  );
  await database.createMember(channelAgent(id, "teammate", "Teammate"));
  await database.createMember(channelAgent(id, "second-member", "Finch"));
  const post = (name: string, content: string, request = false, user = false) =>
    database.appendMessage({
      id: name, channelId: id, content, request,
      sender: user ? { type: "user" } : { type: "agent", agentId: id },
    });
  await post("old-request", "Choose a direction", true);
  const initial = (await getChannelSnapshot(id))!;
  let current = initial;
  const steps: {
    name: string;
    event: Exclude<ChannelObservationEvent, { type: "deleted" }>;
    expected: ChannelState;
  }[] = [];
  const add = (name: string, event: Exclude<ChannelObservationEvent, { type: "deleted" }>) => {
    current = reduceChannelState(current, event);
    steps.push({ name, event, expected: current });
  };
  const commit = async (name: string, change: { events?: ChannelChange["events"] } | null) => {
    if (!change?.events?.length) throw new Error(`Expected a committed change: ${name}`);
    for (const event of change.events) add(name, resolveEventResources(event));
    // Compare after the transaction: a request or completion can commit multiple events.
    expect(current.channel).toEqual((await database.getChannel(id))!);
  };
  add("sparse presence retains teammates", {
    type: "presence", presence: { [id]: { state: "working", text: "Coordinating" } },
  });
  add("resumption replaces stale presence", {
    type: "resumed", presence: { [id]: { state: "idle" }, teammate: { state: "working" } },
  });
  const notes = machineFile("/workspace/notes.md");
  await commit("shared artifact carries its identity", await database.shareArtifact({
    channelId: id, file: notes, title: "Notes", actor: { type: "agent", agentId: id },
  }));
  await commit("quiet recursive tasks", await database.updateChannel(id, {
    tasks: [{ id: "build", title: "Build", status: "in_progress", ownerId: "teammate",
      artifactId: workspaceFileId(notes), diff: { added: 40, removed: 2 },
      children: [{ id: "review", title: "Review", status: "pending", ownerId: "teammate",
        diff: { added: 3, removed: 1 } }] }],
  }));
  await commit("model edit activity", await database.editChannel({
    channelId: id, model: { provider: "codex", name: "updated" },
  }));
  await commit("read preserves request", await database.markUserSeen(id, current.channel.latestSequence));
  await commit("rename", await database.editChannel({channelId: id, name: "Updated channel"}));
  await commit("purpose set", await database.editChannel({channelId: id, purpose: "Ship"}));
  await commit("purpose cleared", await database.editChannel({channelId: id, purpose: null}));
  await commit("directory", await database.updateChannel(id, {directory: "/workspace"}));
  await commit("preview set", await database.updateChannel(id, {previewUrl: "/preview"}));
  await commit("preview cleared", await database.updateChannel(id, {previewUrl: null}));
  await commit("reply clears request", await post("reply", "Proceed", false, true));
  await commit("new request opens", await post("new-question", "Which color?", true));
  await commit("request reaction", await database.setMessageReaction({
    channelId: id, sequence: current.request!.sequence, agentId: "teammate", reaction: "agree",
  }));
  await commit("member edit uses canonical order", await database.updateMember("teammate", {name: "Ada"}));
  await commit("public status", await database.setAgentStatus("teammate", {
    state: "working", text: "Reviewing", workingOn: current.channel.latestSequence,
  }));
  await commit("member leaves and unassigns tasks", await database.deleteMember("teammate"));
  await commit("completion milestone", await database.updateChannel(id, {
    tasks: [{id: "build", title: "Build", status: "done",
      children: [{id: "review", title: "Review", status: "done"}]}],
  }));
  await commit("partial read keeps completion", await database.markUserSeen(id, current.channel.latestSequence - 1));
  await commit("full read clears completion", await database.markUserSeen(id, current.channel.latestSequence));
  add("duplicate revision is idempotent", steps.at(-1)!.event);

  // A bounded recovery read still includes a pending question outside the transcript window.
  for (let index = 0; index < 100; index++) await post(`later-${index}`, "Progress");
  const recovered = (await getChannelSnapshot(id))!;
  add("authoritative recovery", { type: "snapshot", state: recovered });
  await commit("out-of-window request reaction", await database.setMessageReaction({
    channelId: id, sequence: recovered.request!.sequence, agentId: "second-member", reaction: "love",
  }));
  return { initial, steps };
}
