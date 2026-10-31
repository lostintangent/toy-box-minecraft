import { machineFile, sessionFile, workspaceFileId } from "@files/model";
import { setSessionStatus } from "@workspace/server/state";
import type { ChannelDatabase } from "@channels/server/database";
import { channelAgent } from "@channels/server/testFixtures";

/** Seed ordinary public Channel operations in an isolated database; no provider or runtime starts. */
export async function seedChannelSnapshotFixture(channels: ChannelDatabase) {
  const channel = await channels.createChannel(
    {
      name: "Studio demo",
      purpose: "Build a welcoming workshop where the team's work is clear at a glance.",
      model: { provider: "codex", name: "gpt-5.5" },
    },
    channelAgent("studio-demo", "studio-demo"),
  );
  const members = [
    [
      "pixel-designer",
      "Pixel",
      "Shape the workshop and its shared surfaces.",
      "#9d85bf",
      "M12 3l9 9-9 9-9-9z",
    ],
    [
      "ada-engineer",
      "Ada",
      "Build the client connection and message flow.",
      "#de765f",
      "M5 5h14v14H5z M9 9h6v6H9z",
    ],
    [
      "nova-accessibility",
      "Nova",
      "Keep text and interaction clear for everyone.",
      "#d0896c",
      "M12 3v18 M3 12h18 M5 5l14 14 M19 5L5 19",
    ],
    [
      "moss-reviewer",
      "Moss",
      "Review outcomes and verify the finished experience.",
      "#78a06b",
      "M4 12l5 5L20 5",
    ],
    [
      "kit-writer",
      "Kit",
      "Make the handoffs and reference documents useful.",
      "#b891cc",
      "M5 4h14v16H5z M8 8h8 M8 12h8 M8 16h5",
    ],
    [
      "finch-researcher",
      "Finch",
      "Collect examples and investigate open questions.",
      "#d5a44e",
      "M4 17L12 4l8 13z M8 17v4 M16 17v4",
    ],
    [
      "juniper-integrator",
      "Juniper",
      "Connect the pieces into one coherent result.",
      "#69885c",
      "M3 8h8v8H3z M13 8h8v8h-8z M11 12h2",
    ],
    [
      "iris-systems",
      "Iris",
      "Check reconnect behavior and public state boundaries.",
      "#6fbaaa",
      "M12 3C3 3 3 21 12 21s9-18 0-18z M3 12h18",
    ],
  ] as const;
  for (const [id, name, role, color, mark] of members) {
    const worker = channelAgent(channel.id, id, name);
    worker.metadata = { seenThrough: 0, role, avatar: { color, mark } };
    await channels.createMember(worker);
  }

  const assignment = await channels.appendMessage({
    id: "fixture-assignment",
    channelId: channel.id,
    sender: { type: "agent", agentId: channel.id },
    content:
      "@ada @pixel Connect the public transcript, then make the team's progress easy to follow.",
  });
  const plan = sessionFile("pixel-designer", "Workshop plan.md");
  const artifact = await channels.shareArtifact({
    channelId: channel.id,
    actor: { type: "agent", agentId: "pixel-designer" },
    title: "Workshop plan",
    file: plan,
  });
  await channels.shareArtifact({
    channelId: channel.id,
    actor: { type: "agent", agentId: "kit-writer" },
    title: "Interaction notes",
    file: machineFile("/demo/Interaction notes.md"),
  });
  const progress = await channels.appendMessage({
    id: "fixture-progress",
    channelId: channel.id,
    sender: { type: "agent", agentId: "ada-engineer" },
    content:
      "Messages and reconnects now use the same public Channel state. @moss Please review the handoff.",
    attachments: [
      "/demo/workshop preview.png",
      {
        mimeType: "image/png",
        base64:
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
      },
    ],
  });
  await channels.setMessageReaction({
    channelId: channel.id,
    sequence: progress.message.sequence,
    agentId: "iris-systems",
    reaction: "agree",
  });
  await channels.setMessageReaction({
    channelId: channel.id,
    sequence: progress.message.sequence,
    agentId: "moss-reviewer",
    reaction: "done",
  });
  await channels.setRoutine(channel.id, {
    title: "Daily review",
    schedule: "0 9 * * 1-5",
    prompt: "Review the current work, follow up on blockers, and post only meaningful changes.",
  });
  await channels.updateChannel(channel.id, {
    // Diffs and artifacts describe each task's own work; displays roll diffs up, never artifacts.
    tasks: [
      { id: "t1", title: "Connect the team", status: "done", ownerId: "ada-engineer" },
      {
        id: "t2",
        title: "Build the workshop",
        status: "in_progress",
        ownerId: "pixel-designer",
        artifactId: workspaceFileId(plan),
        diff: { added: 1180, removed: 96 },
        children: [
          {
            id: "t3",
            title: "Express the team's identities",
            status: "done",
            diff: { added: 342, removed: 18 },
          },
          { id: "t4", title: "Choose the library location", status: "blocked" },
        ],
      },
      {
        id: "t5",
        title: "Verify reconnect behavior",
        status: "in_progress",
        ownerId: "iris-systems",
        diff: { added: 0, removed: 0 },
      },
      {
        id: "t6",
        title: "Collect useful examples",
        status: "pending",
        ownerId: "finch-researcher",
        diff: { added: 12, removed: 0 },
      },
    ],
    previewUrl: "/demo-preview",
  });
  await channels.setAgentStatus("ada-engineer", {
    state: "working",
    text: "Checking message delivery",
    workingOn: assignment.message.sequence,
  });
  await channels.setAgentStatus("pixel-designer", {
    state: "working",
    text: "Arranging the shared workspace",
    workingOn: assignment.message.sequence,
  });
  await channels.setAgentStatus("moss-reviewer", {
    state: "working",
    text: "Reviewing the workshop plan",
    lookingAt: "message" in artifact ? artifact.message.sequence : assignment.message.sequence,
  });
  await channels.setAgentStatus("finch-researcher", {
    state: "waiting",
    text: "Waiting for the layout direction",
  });
  await channels.setAgentStatus(channel.id, {
    state: "waiting",
    text: "Waiting for Moss's review",
    wakeAt: new Date(Date.now() + 20 * 60_000).toISOString(),
  });
  await channels.appendMessage({
    id: "fixture-request",
    channelId: channel.id,
    sender: { type: "agent", agentId: channel.id },
    content:
      "Where should the shared library sit: near the entrance or beside the team desks? Reply with your preference and any constraints.",
    request: true,
  });
  await channels.markUserSeen(channel.id, assignment.message.sequence);
  for (const id of ["ada-engineer", "pixel-designer", "moss-reviewer", "iris-systems"]) {
    setSessionStatus(id, "running");
  }
  return { channelId: channel.id, agentIds: [channel.id, ...members.map(([id]) => id)] };
}
