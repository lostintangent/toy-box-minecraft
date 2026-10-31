#!/usr/bin/env bun
import { runGradle } from './gradle';

const usage = `Toy Box Minecraft
  bun run dev [--server URL] [--channel CHANNEL_ID] [--dev]

Uses Java 25 and the pinned Fabric development client. The Toy Box server runs separately.
Without flags, the mod uses the selection saved in config/toy-box-minecraft.json; at first,
that is the server at http://localhost:3100 and a picker listing its channels.
Environment: TOY_BOX_SERVER_URL, TOY_BOX_CHANNEL_ID, JAVA_HOME.
--dev mutes the game and avoids stealing focus; enables the local screenshot bridge.`;

try {
  const args = process.argv.slice(2);
  const env = { ...process.env };
  let help = false;
  for (let i = 0; i < args.length; i++) {
    const arg = args[i]!;
    if (arg === '--help' || arg === '-h') { help = true; continue; }
    if (arg === '--dev') {
      env.TOY_BOX_DEV = '1';
      env.TOY_BOX_MUTE = '1';
      continue;
    }
    const value = args[i + 1];
    if (!['--server', '--channel'].includes(arg) || !value || value.startsWith('--')) {
      throw new Error(`Unknown or incomplete option: ${arg}\n${usage}`);
    }
    i++;
    if (arg === '--server') {
      const url = new URL(value);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
        throw new Error('--server must be an HTTP(S) base URL without credentials, query, or fragment.');
      }
      env.TOY_BOX_SERVER_URL = url.href;
    } else env.TOY_BOX_CHANNEL_ID = value;
  }
  if (help) console.log(usage);
  else runGradle(['runClient', '--console=plain'], env);
} catch (error) {
  console.error((error as Error).message);
  process.exitCode = 1;
}
