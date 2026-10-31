#!/usr/bin/env bun
// Generate the fixture through Toy Box's real database and public snapshot reader.
import { spawnSync } from 'node:child_process';
import path from 'node:path';

const root = path.resolve(import.meta.dir, '..');
const source = path.resolve(process.argv[2] ?? path.join(root, '..', 'toy-box'));
const output = path.join(root, 'fixtures', 'channel-snapshot.json');
const result = spawnSync(process.execPath, [
  `--tsconfig-override=${path.join(source, 'tsconfig.json')}`,
  'test',
  path.join(root, 'tools/fixtures/channel-snapshot.test.ts'),
], {
  cwd: source,
  env: { ...process.env, TOY_BOX_CHANNEL_FIXTURE_OUTPUT: output },
  stdio: 'inherit',
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
