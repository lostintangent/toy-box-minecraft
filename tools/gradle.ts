import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

type Env = Record<string, string | undefined>;

export const projectRoot = path.resolve(import.meta.dir, '..');

const isJava25 = (output: string) => /version "25(?:[."])/.test(output);

export function javaEnvironment(env: Env = process.env): Env {
  const candidates = [env.JAVA_HOME];
  if (process.platform === 'darwin') {
    const located = spawnSync('/usr/libexec/java_home', ['-v', '25'], { encoding: 'utf8' });
    if (located.status === 0) candidates.push(located.stdout.trim());
    candidates.push('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');
    candidates.push('/usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');
  }
  for (const home of candidates) {
    if (!home) continue;
    const executable = path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    if (!fs.existsSync(executable)) continue;
    const version = spawnSync(executable, ['-version'], { encoding: 'utf8' });
    if (version.status === 0 && isJava25(version.stderr + version.stdout)) return { ...env, JAVA_HOME: home };
  }
  const version = spawnSync('java', ['-version'], { encoding: 'utf8', env });
  if (version.status === 0 && isJava25(version.stderr + version.stdout)) return { ...env };
  throw new Error('Java 25 is required. Set JAVA_HOME to a JDK 25 installation.');
}

export function runGradle(args: string[], env: Env = process.env) {
  const javaEnv = javaEnvironment(env);
  const windows = process.platform === 'win32';
  const command = windows ? 'cmd.exe' : 'sh';
  const commandArgs = windows ? ['/d', '/c', 'gradlew.bat', ...args] : ['./gradlew', ...args];
  const child = spawn(command, commandArgs, {
    cwd: path.join(projectRoot, 'mod'), env: javaEnv, stdio: 'inherit',
  });
  const interrupt = () => child.kill('SIGINT');
  const terminate = () => child.kill('SIGTERM');
  process.on('SIGINT', interrupt);
  process.on('SIGTERM', terminate);
  child.once('error', (error) => { console.error(error.message); process.exitCode = 1; });
  child.once('exit', (code) => {
    process.off('SIGINT', interrupt);
    process.off('SIGTERM', terminate);
    process.exitCode = code ?? 1;
  });
  return child;
}

if (import.meta.main) {
  try { runGradle([...process.argv.slice(2), '--console=plain']); }
  catch (error) { console.error((error as Error).message); process.exitCode = 1; }
}
