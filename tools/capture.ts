#!/usr/bin/env bun
import fs from 'node:fs';
import path from 'node:path';
import { parseArgs } from 'node:util';
import { DevClient, type DevResponse } from './lib/devclient';
import { contactSheet, type Tile } from './lib/contactsheet';
import { projectRoot } from './gradle';

const { values } = parseArgs({ options: { port: { type: 'string', default: '7879' }, output: { type: 'string' } } });
const output = path.resolve(values.output ?? path.join(projectRoot, 'artifacts', 'shots', `workshop-${Date.now()}`));
fs.mkdirSync(output, { recursive: true });
const dev = await DevClient.connect({ port: Number(values.port), timeoutMs: 30000 });
const shots: { name: string; anchor: string; screen?: string }[] = [
  { name: 'workshop', anchor: 'cam_exterior_hero' },
  { name: 'front-sign', anchor: 'cam_front_sign' },
  { name: 'gallery', anchor: 'cam_gallery' },
  { name: 'toy-box', anchor: 'cam_toy_box' },
  { name: 'image', anchor: 'cam_gallery', screen: 'image' },
  { name: 'team', anchor: 'cam_wide_interior' },
  { name: 'hall', anchor: 'cam_hall' },
  { name: 'monitor-close', anchor: 'cam_desk_bay_0' },
  { name: 'nameplate-close', anchor: 'cam_agent_desk' },
  { name: 'waiting', anchor: 'cam_waiting' },
  { name: 'idle-lounge', anchor: 'cam_lounge' },
  { name: 'tasks', anchor: 'cam_task_board' },
  { name: 'attention', anchor: 'cam_request_podium' },
  { name: 'transcript', anchor: 'cam_transcript', screen: 'transcript' },
  { name: 'channel-picker', anchor: 'cam_transcript', screen: 'channels' },
  { name: 'team-roster', anchor: 'cam_wide_interior', screen: 'team' },
  { name: 'artifacts', anchor: 'cam_artifacts', screen: 'artifacts' },
  { name: 'task-tree', anchor: 'cam_task_board', screen: 'tasks' },
  { name: 'request', anchor: 'cam_request_podium', screen: 'request' },
  { name: 'routines', anchor: 'cam_artifacts', screen: 'routines' },
  { name: 'member', anchor: 'cam_wide_interior', screen: 'agent' },
];
const tiles: Tile[] = [];
let before: DevResponse | undefined;
try {
  before = await dev.waitInWorld({ timeoutMs: 60000 });
  await dev.call('dev.command', { cmd: 'time set 12000' });
  await dev.call('dev.command', { cmd: 'weather clear' });
  await dev.call('dev.agents', { settle: true });
  for (const shot of shots) {
    await dev.call('dev.screen', { open: null });
    await dev.call('dev.camera', { anchor: shot.anchor, mode: 'spectator', fov: 70 });
    if (shot.screen) await dev.call('dev.screen', { open: shot.screen });
    const image = await dev.call('dev.screenshot', { name: `toy-box-${shot.name}`, hideHud: !shot.screen, frames: 3 });
    const destination = path.join(output, `${shot.name}.png`);
    fs.copyFileSync(image.path, destination);
    tiles.push({ name: shot.name, path: destination, status: 'ok', note: shot.anchor });
  }
  contactSheet({ out: path.join(output, 'contact-sheet.png'), title: 'TOY BOX MINECRAFT', tiles });
  fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify({ before, after: await dev.call('dev.state'), shots: tiles }, null, 2));
  console.log(output);
} finally {
  await dev.request('dev.screen', { open: null }).catch(() => {});
  const time = before?.world?.time;
  if (Number.isFinite(time)) await dev.request('dev.command', { cmd: `time set ${time}` }).catch(() => {});
  await dev.request('dev.release').catch(() => {});
  dev.close();
}
