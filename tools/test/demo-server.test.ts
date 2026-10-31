import { test } from 'bun:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { readFileSync } from 'node:fs';
import type { AddressInfo } from 'node:net';
import { createDemoServer } from '../demo-server';

function events(reader: { read(): Promise<{ done: boolean; value?: Uint8Array }> }) {
  const decoder = new TextDecoder();
  let buffer = '';
  return async () => {
    for (;;) {
      const end = buffer.indexOf('\n\n');
      if (end >= 0) {
        const frame = buffer.slice(0, end); buffer = buffer.slice(end + 2);
        const data = frame.split('\n').filter((line) => line.startsWith('data: ')).map((line) => line.slice(6)).join('\n');
        if (data) return JSON.parse(data);
      } else {
        const part = await reader.read();
        assert.equal(part.done, false, 'stream closed before expected event');
        buffer += decoder.decode(part.value, { stream: true });
      }
    }
  };
}

test('demo timestamps move together to startup while future waits keep their offsets', async () => {
  const fixture = JSON.parse(readFileSync(new URL('../../fixtures/channel-snapshot.json', import.meta.url), 'utf8'));
  const historicalTime = Date.parse(fixture.channel.updatedAt);
  fixture.messages[0].timestamp = new Date(historicalTime - 3600000).toISOString();
  const original = structuredClone(fixture);
  const startup = Date.parse('2030-01-01T12:00:00.000Z');
  const demo = createDemoServer(fixture, startup);
  demo.server.listen(0, '127.0.0.1');
  await once(demo.server, 'listening');
  try {
    const shifted = demo.snapshot();
    assert.equal(shifted.channel.updatedAt, new Date(startup).toISOString());
    assert.equal(Date.parse(shifted.messages[0].timestamp), startup - 3600000);
    assert.equal(Date.parse(shifted.request.timestamp), startup);
    assert.equal(Date.parse(shifted.artifacts[0].sharedAt), startup);
    assert.equal(Date.parse(shifted.lead.status.wakeAt), startup + 20 * 60000);
    assert.equal(shifted.presence[shifted.channel.id].wakeAt, shifted.lead.status.wakeAt);
    assert.deepEqual(shifted.routines, original.routines);
    assert.deepEqual(fixture, original);
  } finally { await demo.close(); }
});

test('the public fixture supports snapshots, live messages, and reconnect without private state', async () => {
  const demo = createDemoServer();
  demo.server.listen(0, '127.0.0.1');
  await once(demo.server, 'listening');
  const base = `http://127.0.0.1:${(demo.server.address() as AddressInfo).port}/api/v1/channels/studio-demo`;
  const abort = new AbortController();
  try {
    const live = await fetch(base, { signal: abort.signal });
    const reader = live.body!.getReader();
    const next = events(reader);
    const snapshot = await next();
    assert.equal(snapshot.type, 'snapshot');
    const initial = snapshot.state;
    const catalog = await (await fetch(base.slice(0, base.lastIndexOf('/')))).json();
    assert.deepEqual(catalog, { version: 1, channels: [initial.channel], members: initial.members });
    assert.equal(initial.channel.requestSequence, initial.request.sequence);
    assert.equal('sessions' in initial, false);
    assert.equal(initial.lead.name, 'Lead');
    assert.equal(initial.lead.avatar.color, '#0891b2');
    assert.equal(initial.members.length, 8);
    assert.deepEqual(initial.members.map(({ name }: { name: string }) => name),
      ['Ada', 'Finch', 'Iris', 'Juniper', 'Kit', 'Moss', 'Nova', 'Pixel']);
    assert.equal(initial.request.id, 'fixture-request');
    assert.equal(typeof initial.lead.status.wakeAt, 'string');
    const response = await fetch(`${base}/messages`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: 'from-minecraft', content: 'Put the library by the entrance.' }),
    });
    assert.equal(response.status, 201);
    const detail = await next();
    assert.equal(detail.type, 'message');
    assert.equal(detail.message.id, 'from-minecraft');
    assert.equal(detail.revision, initial.revision + 1);
    const published = demo.snapshot();
    assert.equal(published.messages.at(-1).id, 'from-minecraft');
    assert.equal(published.channel.requestSequence, null);
    assert.equal(published.request, null);
    assert.deepEqual(published.lead.status, initial.lead.status);
    await reader.cancel();
    const resumed = await fetch(`${base}?after=${initial.revision}`, { headers: { Accept: 'text/event-stream' }, signal: abort.signal });
    const resumedReader = resumed.body!.getReader();
    const resumeNext = events(resumedReader);
    assert.deepEqual(await resumeNext(), detail);
    assert.deepEqual(await resumeNext(), { type: 'resumed', presence: published.presence });
    await resumedReader.cancel();
    const cold = await fetch(base, { headers: { Accept: 'text/event-stream' }, signal: abort.signal });
    const coldReader = cold.body!.getReader();
    assert.deepEqual(await events(coldReader)(), { type: 'snapshot', state: demo.snapshot() });
    await coldReader.cancel();
  } finally { abort.abort(); await demo.close(); }
});

test('revisioned reads preserve pending requests, cursor zero is valid, and replay gaps reset', async () => {
  const demo = createDemoServer();
  demo.server.listen(0, '127.0.0.1');
  await once(demo.server, 'listening');
  const initial = demo.snapshot();
  const base = `http://127.0.0.1:${(demo.server.address() as AddressInfo).port}/api/v1/channels/studio-demo`;
  const abort = new AbortController();
  try {
    const stream = await fetch(`${base}?after=0`, { headers: { Accept: 'text/event-stream', 'Last-Event-ID': String(initial.revision) }, signal: abort.signal });
    const reader = stream.body!.getReader();
    const next = events(reader);
    assert.deepEqual(await next(), { type: 'resumed', presence: initial.presence }, 'current header cursor takes priority over query');
    await fetch(`${base}/read`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sequence: initial.channel.latestSequence }) });
    const read = await next();
    assert.deepEqual(read, { type: 'read', revision: initial.revision + 1,
      seenThrough: initial.channel.latestSequence });
    assert.deepEqual(demo.snapshot().request, initial.request);
    assert.equal(demo.snapshot().channel.requestSequence, initial.request.sequence);
    assert.equal(demo.snapshot().revision, initial.revision + 1);
    await reader.cancel();
    const gap = await fetch(`${base}?after=0`, { headers: { Accept: 'text/event-stream' }, signal: abort.signal });
    const gapReader = gap.body!.getReader();
    const reset = await events(gapReader)();
    assert.equal(reset.type, 'snapshot');
    assert.equal(reset.state.revision, initial.revision + 1);
    await gapReader.cancel();
    const empty = demo.snapshot();
    empty.revision = 0;
    demo.replace(empty);
    const zero = await fetch(`${base}?after=0`, { headers: { Accept: 'text/event-stream' }, signal: abort.signal });
    const zeroReader = zero.body!.getReader();
    assert.deepEqual(await events(zeroReader)(), { type: 'resumed', presence: empty.presence }, 'revision zero is a usable cursor');
    await zeroReader.cancel();
  } finally { abort.abort(); await demo.close(); }
});

test('reading advances only the cursor and fixture resources resolve', async () => {
  const demo = createDemoServer();
  demo.server.listen(0, '127.0.0.1');
  await once(demo.server, 'listening');
  const origin = `http://127.0.0.1:${(demo.server.address() as AddressInfo).port}`;
  const read = (sequence: number) => fetch(`${origin}/api/v1/channels/studio-demo/read`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sequence }),
  });
  try {
    const initial = demo.snapshot();
    assert.equal((await read(-1)).status, 400);
    assert.deepEqual(await (await read(999999)).json(), {
      version: 1, seenThrough: initial.channel.latestSequence,
    });
    assert.deepEqual(await (await read(0)).json(), {
      version: 1, seenThrough: initial.channel.latestSequence,
    });
    assert.equal(demo.snapshot().channel.requestSequence, initial.request.sequence);
    assert.deepEqual(demo.snapshot().request, initial.request);
    for (const artifact of initial.artifacts) {
      const response = await fetch(origin + artifact.url);
      assert.equal(response.status, 200);
      assert.match(await response.text(), new RegExp(artifact.title));
    }
    const attachment = initial.messages.flatMap((message: any) => (message.attachments ?? []).map((attachment: any) => attachment.url))
      .find((url: string | undefined) => url?.startsWith('/api/serve/'));
    const response = await fetch(origin + attachment);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('content-type'), 'image/png');
  } finally { await demo.close(); }
});

test('the fixture rejects duplicate messages and agent impersonation', async () => {
  const demo = createDemoServer();
  demo.server.listen(0, '127.0.0.1');
  await once(demo.server, 'listening');
  const url = `http://127.0.0.1:${(demo.server.address() as AddressInfo).port}/api/v1/channels/studio-demo/messages`;
  const post = (body: unknown) => fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  try {
    const message = { id: 'same-id', content: 'Hello' };
    assert.equal((await post(message)).status, 201);
    assert.equal((await post(message)).status, 409);
    assert.equal((await post({ ...message, id: 'spoofed', sender: { type: 'agent', agentId: 'studio-demo' } })).status, 400);
    assert.equal(demo.snapshot().messages.filter(({ id }: { id: string }) => id === 'same-id').length, 1);
  } finally { await demo.close(); }
});

test('artifact watches report edits and deletion and release their connections', async () => {
  const demo = createDemoServer();
  demo.server.listen(0, '127.0.0.1');
  await once(demo.server, 'listening');
  const origin = `http://127.0.0.1:${(demo.server.address() as AddressInfo).port}`;
  const url = demo.snapshot().artifacts[0].url;
  const abort = new AbortController();
  try {
    const watch = await fetch(origin + url.replace('/api/serve/', '/api/watch/'), { signal: abort.signal });
    const reader = watch.body!.getReader();
    const next = async () => JSON.parse(new TextDecoder().decode((await reader.read()).value).slice(6));
    assert.equal((await next()).type, 'changed');
    demo.setResource(url, '# Updated in place');
    assert.equal((await next()).type, 'changed');
    assert.equal(await (await fetch(origin + url)).text(), '# Updated in place');
    demo.deleteResource(url);
    assert.equal((await next()).type, 'deleted');
    assert.equal((await fetch(origin + url)).status, 404);
    await reader.cancel();
  } finally { abort.abort(); await demo.close(); }
});
