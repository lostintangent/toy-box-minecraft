// DevBridge client: connects to the mod's localhost WebSocket (default ws://127.0.0.1:7879)
// and sends JSON requests matched by id. Used by devcli.ts, capture.ts and QA tooling.
//
//   import { DevClient } from './lib/devclient';
//   const dev = await DevClient.connect({ timeoutMs: 120_000 });
//   const st = await dev.call('dev.state');
//   await dev.call('dev.camera', { x: 0, y: 70, z: 0, yaw: 0, pitch: 10 });
//   const shot = await dev.call('dev.screenshot', { name: 'hello' });
//   const h = await dev.health();   // {ok, stalled, msSinceLastFrame}: works even when the game is hung
//   dev.close();

/** A bridge response: {ok:true, ...fields} or {ok:false, error}. Fields depend on the command. */
export type DevResponse = { ok: boolean; error?: string; [field: string]: any };

type Pending = { resolve: (response: DevResponse) => void; reject: (error: Error) => void; timer: Timer };

export const DEFAULT_PORT = Number(process.env.TOY_BOX_DEV_PORT || 7879);

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export class DevError extends Error {
  hung = false;

  constructor(message: string, readonly response?: unknown) {
    super(message);
    this.name = 'DevError';
  }
}

export class DevClient {
  private nextId = 1;
  private readonly pending = new Map<string, Pending>();
  closed = false;

  private constructor(private readonly ws: WebSocket, readonly hello: unknown) {
    ws.addEventListener('message', (event) => this.onMessage(event.data));
    ws.addEventListener('close', () => {
      this.closed = true;
      for (const { reject, timer } of this.pending.values()) {
        clearTimeout(timer);
        reject(new DevError('DevBridge connection closed'));
      }
      this.pending.clear();
    });
  }

  /** Connect, retrying until the bridge is up (the game may still be starting). */
  static async connect(opts: { port?: number; host?: string; timeoutMs?: number; retryMs?: number; onWait?: (elapsedMs: number) => void } = {}) {
    const port = opts.port ?? DEFAULT_PORT;
    const host = opts.host ?? '127.0.0.1';
    const timeoutMs = opts.timeoutMs ?? 30_000;
    const retryMs = opts.retryMs ?? 500;
    const start = Date.now();
    let lastErr: unknown;
    let lastNotice = 0;
    while (true) {
      try {
        return await DevClient.tryConnect(`ws://${host}:${port}`);
      } catch (err) {
        lastErr = err;
      }
      const elapsed = Date.now() - start;
      if (elapsed >= timeoutMs) {
        const reason = lastErr instanceof Error ? lastErr.message : String(lastErr);
        throw new DevError(`DevBridge not reachable on ws://${host}:${port} after ${Math.round(elapsed / 1000)}s (${reason})`);
      }
      if (opts.onWait && elapsed - lastNotice >= 10_000) {
        lastNotice = elapsed;
        opts.onWait(elapsed);
      }
      await sleep(retryMs);
    }
  }

  private static tryConnect(url: string): Promise<DevClient> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      let settled = false;
      const fail = (err: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        ws.close();
        reject(err);
      };
      const timer = setTimeout(() => fail(new Error('no dev.hello within 5s')), 5000);
      ws.addEventListener('error', () => fail(new Error('connection failed')), { once: true });
      ws.addEventListener('close', (event) => fail(new Error(`closed (${event.code}${event.reason ? `: ${event.reason}` : ''})`)), { once: true });
      // The server greets with {type:"dev.hello"} right after the handshake.
      ws.addEventListener('message', (event) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        let hello = null;
        try { hello = JSON.parse(String(event.data)); } catch {}
        resolve(new DevClient(ws, hello));
      }, { once: true });
    });
  }

  private onMessage(data: unknown) {
    let msg: DevResponse & { id?: unknown };
    try { msg = JSON.parse(String(data)); } catch { return; }
    if (msg.id == null) return; // events (dev.hello etc.)
    const p = this.pending.get(String(msg.id));
    if (!p) return;
    this.pending.delete(String(msg.id));
    clearTimeout(p.timer);
    p.resolve(msg);
  }

  /**
   * Send a request and resolve with the raw response ({ok:true,...} or {ok:false,error}).
   * `timeoutMs` is client-side; the server has its own per-command timeout.
   */
  request(type: string, payload: Record<string, unknown> = {}, opts: { timeoutMs?: number } = {}): Promise<DevResponse> {
    if (this.closed) return Promise.reject(new DevError('DevBridge connection closed'));
    const id = String(this.nextId++);
    const timeoutMs = opts.timeoutMs ?? 180_000;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new DevError(`${type} timed out after ${timeoutMs}ms (client side)`));
      }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ ...payload, id, type }));
    });
  }

  /** Like request() but throws DevError when the response is not ok. */
  async call(type: string, payload: Record<string, unknown> = {}, opts: { timeoutMs?: number } = {}) {
    const res = await this.request(type, payload, opts);
    if (!res.ok) throw new DevError(`${type} failed: ${res.error}`, res);
    return res;
  }

  /**
   * Liveness check that works even when the game is hung: dev.ping is answered on the socket
   * thread. stalled:true = the render thread has not finished a frame for 5 s.
   */
  async health({ timeoutMs = 5_000 } = {}) {
    try {
      const p = await this.request('dev.ping', {}, { timeoutMs });
      return { ok: !!p.ok, stalled: !!p.stalled, msSinceLastFrame: (p.msSinceLastFrame ?? null) as number | null, frame: p.frame ?? null, quitting: !!p.quitting };
    } catch (e) {
      return { ok: false, stalled: null, msSinceLastFrame: null, error: (e as Error).message };
    }
  }

  /** Throws DevError (with .hung = true) when the render thread has been stuck for at least minMs. */
  async assertNotHung(minMs = 30_000) {
    const h = await this.health();
    if (h.ok && h.stalled && h.msSinceLastFrame !== null && h.msSinceLastFrame >= minMs) {
      const err = new DevError(`game is hung: the render thread has not finished a frame for ${h.msSinceLastFrame} ms (relaunch it; dev.quit force-exits)`, h);
      err.hung = true;
      throw err;
    }
    return h;
  }

  /** Wait until the player is in a world (poll dev.state). Fails fast if the game hangs. */
  async waitInWorld({ timeoutMs = 300_000, pollMs = 1000, onWait }: { timeoutMs?: number; pollMs?: number; onWait?: (elapsedMs: number, state: DevResponse) => void } = {}) {
    const start = Date.now();
    let lastNotice = 0;
    while (true) {
      const st: DevResponse = await this.request('dev.state', {}, { timeoutMs: 15_000 }).catch((e: Error) => ({ ok: false, error: e.message }));
      // 'ready' = in a world with no loading screen/overlay (older builds: fall back to inWorld).
      if (st.ok && (st.ready ?? (st.inWorld && !st.loading))) return st;
      if (!st.ok) await this.assertNotHung();
      const elapsed = Date.now() - start;
      if (elapsed >= timeoutMs) throw new DevError(`not in a world after ${Math.round(elapsed / 1000)}s`, st);
      if (onWait && elapsed - lastNotice >= 10_000) {
        lastNotice = elapsed;
        onWait(elapsed, st);
      }
      await sleep(pollMs);
    }
  }

  /** Resolves when the server closes the socket (e.g. after dev.quit). */
  waitClosed(timeoutMs = 60_000): Promise<boolean> {
    if (this.closed) return Promise.resolve(true);
    return new Promise((resolve) => {
      const timer = setTimeout(() => resolve(false), timeoutMs);
      this.ws.addEventListener('close', () => { clearTimeout(timer); resolve(true); }, { once: true });
    });
  }

  close() {
    this.ws.close();
  }
}
