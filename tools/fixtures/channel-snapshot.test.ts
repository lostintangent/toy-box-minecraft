import { expect, onTestFinished, setSystemTime, spyOn, test } from "bun:test";
import { CHANNEL_LEAD_PROFILE, isChannelSystemMessage } from "@channels/model";
import { deleteSessionState } from "@workspace/server/state/sessions";
import * as database from "@/server/database";
import { ChannelDatabase } from "@channels/server/database";
import { getChannelSnapshot } from "@channels/server/stream";
import { seedChannelSnapshotFixture } from "./channel-snapshot";
import { channelTransitionFixture } from "./channel-transitions";

test("the exported client fixture is a canonical public Channel snapshot", async () => {
  const db = await database.createTestDatabase();
  const access = spyOn(database, "getStateDatabase").mockResolvedValue(db);
  let id = 0;
  const random = spyOn(crypto, "randomUUID").mockImplementation(
    () => `00000000-0000-4000-8000-${String(++id).padStart(12, "0")}`,
  );
  setSystemTime(new Date("2026-10-03T20:00:00.000Z"));
  const agents: string[] = [];
  onTestFinished(async () => {
    for (const agentId of agents) deleteSessionState(agentId);
    setSystemTime();
    random.mockRestore();
    access.mockRestore();
    await db.close();
  });
  const { channelId, agentIds } = await seedChannelSnapshotFixture(new ChannelDatabase(db));
  agents.push(...agentIds);
  const snapshot = await getChannelSnapshot(channelId);
  if (!snapshot) throw new Error("Missing canonical fixture");
  expect(snapshot.lead).toMatchObject(CHANNEL_LEAD_PROFILE);
  expect(snapshot.members.map(({ name }) => name)).toEqual([
    "Ada",
    "Finch",
    "Iris",
    "Juniper",
    "Kit",
    "Moss",
    "Nova",
    "Pixel",
  ]);
  expect(snapshot.members.every((member) => !("seenThrough" in member))).toBe(true);
  expect(snapshot.messages.some(isChannelSystemMessage)).toBe(true);
  expect(
    snapshot.messages.find(({ id }) => id === "fixture-progress")?.reactions,
  ).toHaveLength(2);
  expect(snapshot.routines).toHaveLength(1);
  expect(snapshot.lead.status?.state).toBe("waiting");
  expect(snapshot.lead.status).toHaveProperty("wakeAt", "2026-10-03T20:20:00.000Z");
  expect(snapshot.members.find(({ name }) => name === "Ada")?.status).toHaveProperty(
    "workingOn",
  );
  expect(snapshot.members.find(({ name }) => name === "Moss")?.status).toHaveProperty(
    "lookingAt",
  );
  expect(snapshot.request?.id).toBe("fixture-request");
  expect(snapshot.channel.requestSequence).toBe(snapshot.request?.sequence);
  expect(snapshot.presence["ada-engineer"]?.state).toBe("working");
  expect(snapshot.artifacts.every(({ url }) => url?.startsWith("/api/serve/"))).toBe(true);
  expect(snapshot).not.toHaveProperty("sessions");
  const sessions = await db`SELECT COUNT(*) AS count FROM sessions`;
  expect(sessions[0]?.count).toBe(0);

  // Explicit fixture generation reuses this exact assertion and real snapshot path in an isolated DB.
  const output = process.env.TOY_BOX_CHANNEL_FIXTURE_OUTPUT;
  if (output) {
    await Bun.write(output, `${JSON.stringify(snapshot, null, 2)}\n`);
    const transitions = await channelTransitionFixture(new ChannelDatabase(db));
    await Bun.write(
      output.replace("channel-snapshot.json", "channel-transitions.json"),
      `${JSON.stringify(transitions)}\n`,
    );
  }
});
