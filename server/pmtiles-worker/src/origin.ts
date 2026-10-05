// The Pi origin: tiles for the archives named in ORIGIN_ARCHIVES come from the Raspberry Pi's tile
// server first (`pmtiles serve` behind a Cloudflare Tunnel and an Access service token,
// docs/plans/2026-10-04-pi-origin-part1-report.md), and from the same archive in R2 when the Pi
// cannot answer. The owner's ruling, 2026-10-05: "A + B as a backup" (the Pi serves, R2 is the
// backup), recorded in docs/audits/2026-10-04-pi-build-part1-report.md on `pi-build`.
//
// Kept free of Workers-only types and of the pmtiles library, so the decision of when to fall back
// is unit-testable under plain Node (test/origin.test.ts) with an injected fetch and clock.
//
// What `pmtiles serve` 1.31.2 answers, read on the Pi on 2026-10-05: 200 for a tile, 204 for an
// empty tile, 404 "Tile not found" outside the archive's zoom range, 404 "Archive not found" for an
// unknown name. A 200 or 204 is the Pi's answer and is served. A 404 goes to R2 for that request
// only, so R2 gives the final word on a missing tile and an archive missing from the Pi still
// serves; it does not mark the Pi down. Anything else (a timeout, a network error, a 5xx, the
// tunnel's 530, or an Access refusal, which means the token is wrong) falls back and marks the Pi
// down for `downForMs`, so tiles during a Wi-Fi outage do not each wait out the timeout.

export interface OriginConfig {
  /** The origin's base URL, e.g. https://origin.zynergy-labs.com, without a trailing slash. */
  baseUrl: string;
  clientId: string;
  clientSecret: string;
  timeoutMs: number;
  downForMs: number;
  /** Archive names served Pi-first. Any other name goes straight to R2, as before. */
  archives: ReadonlySet<string>;
}

export interface OriginEnv {
  ORIGIN_URL?: string;
  ORIGIN_ARCHIVES?: string;
  ORIGIN_TIMEOUT_MS?: string;
  ORIGIN_DOWN_FOR_MS?: string;
  CF_ACCESS_CLIENT_ID?: string;
  CF_ACCESS_CLIENT_SECRET?: string;
}

export const DEFAULT_TIMEOUT_MS = 3000;
export const DEFAULT_DOWN_FOR_MS = 60_000;

/**
 * Reads the origin settings. Returns a reason string instead of a config when the origin is not
 * configured, so the caller can log once why every request is going to R2 rather than doing so
 * silently.
 */
export function originConfig(env: OriginEnv): OriginConfig | { unconfigured: string } {
  const missing = (
    [
      ["ORIGIN_URL", env.ORIGIN_URL],
      ["ORIGIN_ARCHIVES", env.ORIGIN_ARCHIVES],
      ["CF_ACCESS_CLIENT_ID", env.CF_ACCESS_CLIENT_ID],
      ["CF_ACCESS_CLIENT_SECRET", env.CF_ACCESS_CLIENT_SECRET],
    ] as const
  )
    .filter(([, v]) => !v || !v.trim())
    .map(([k]) => k);
  if (missing.length > 0) return { unconfigured: `missing ${missing.join(", ")}` };

  const archives = new Set(
    env.ORIGIN_ARCHIVES!.split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
  );
  if (archives.size === 0) return { unconfigured: "ORIGIN_ARCHIVES names no archive" };

  return {
    baseUrl: env.ORIGIN_URL!.trim().replace(/\/+$/, ""),
    clientId: env.CF_ACCESS_CLIENT_ID!.trim(),
    clientSecret: env.CF_ACCESS_CLIENT_SECRET!.trim(),
    timeoutMs: positiveInt(env.ORIGIN_TIMEOUT_MS, DEFAULT_TIMEOUT_MS),
    downForMs: positiveInt(env.ORIGIN_DOWN_FOR_MS, DEFAULT_DOWN_FOR_MS),
    archives,
  };
}

function positiveInt(v: string | undefined, fallback: number): number {
  const n = Number(v);
  return Number.isInteger(n) && n > 0 ? n : fallback;
}

/** Per-isolate memory of the Pi being down. Each Cloudflare location keeps its own. */
export class OriginState {
  downUntil = 0;
  downReason = "";
}

// No result or reason ever carries the tile's coordinates: reasons are logged and sent in a
// response header, and coordinates would record where users looked.
export type OriginResult =
  | { source: "pi"; status: 200 | 204; body: ArrayBuffer | null; contentType: string | null }
  | { source: "r2"; reason: string; markedDown: boolean };

type FetchFn = (input: string, init: { headers: Record<string, string>; signal: AbortSignal }) => Promise<Response>;

/**
 * Asks the Pi for one tile. Never throws: every way the Pi can fail comes back as an "r2" result
 * carrying the reason, for the caller to log and to put in the X-Forager-Source header.
 */
export async function originTile(
  cfg: OriginConfig,
  state: OriginState,
  name: string,
  tile: [number, number, number],
  ext: string,
  fetchFn: FetchFn,
  now: () => number
): Promise<OriginResult> {
  if (now() < state.downUntil) {
    return { source: "r2", reason: `pi marked down: ${state.downReason}`, markedDown: false };
  }

  const markDown = (reason: string): OriginResult => {
    state.downUntil = now() + cfg.downForMs;
    state.downReason = reason;
    return { source: "r2", reason, markedDown: true };
  };

  const [z, x, y] = tile;
  const url = `${cfg.baseUrl}/${name}/${z}/${x}/${y}.${ext}`;
  let resp: Response;
  try {
    resp = await fetchFn(url, {
      headers: { "CF-Access-Client-Id": cfg.clientId, "CF-Access-Client-Secret": cfg.clientSecret },
      signal: AbortSignal.timeout(cfg.timeoutMs),
    });
  } catch (e) {
    const err = e as { name?: string; message?: string };
    if (err?.name === "TimeoutError" || err?.name === "AbortError") {
      return markDown(`no answer within ${cfg.timeoutMs} ms`);
    }
    return markDown(`fetch failed: ${err?.message ?? String(e)}`);
  }

  if (resp.status === 200) {
    let body: ArrayBuffer;
    try {
      body = await resp.arrayBuffer();
    } catch (e) {
      return markDown(`body read failed: ${(e as Error)?.message ?? String(e)}`);
    }
    return { source: "pi", status: 200, body, contentType: resp.headers.get("Content-Type") };
  }
  if (resp.status === 204) {
    return { source: "pi", status: 204, body: null, contentType: null };
  }
  if (resp.status === 404) {
    const text = (await resp.text().catch(() => "")).trim().slice(0, 80);
    return { source: "r2", reason: `pi 404${text ? ` (${text})` : ""}`, markedDown: false };
  }
  return markDown(`pi answered ${resp.status}`);
}

export type OriginJsonResult =
  | { source: "pi"; json: Record<string, unknown> }
  | { source: "r2"; reason: string; markedDown: boolean };

/**
 * Asks the Pi for an archive's tileset JSON, with the same fallback rules as [originTile]. The
 * Pi's `tiles` URL names its own --public-url, which is not this Worker, so it is replaced with
 * `tilesBase` (this Worker's `https://host/name`) before the JSON is served.
 */
export async function originTileJson(
  cfg: OriginConfig,
  state: OriginState,
  name: string,
  tilesBase: string,
  fetchFn: FetchFn,
  now: () => number
): Promise<OriginJsonResult> {
  if (now() < state.downUntil) {
    return { source: "r2", reason: `pi marked down: ${state.downReason}`, markedDown: false };
  }
  const markDown = (reason: string): OriginJsonResult => {
    state.downUntil = now() + cfg.downForMs;
    state.downReason = reason;
    return { source: "r2", reason, markedDown: true };
  };

  let resp: Response;
  try {
    resp = await fetchFn(`${cfg.baseUrl}/${name}.json`, {
      headers: { "CF-Access-Client-Id": cfg.clientId, "CF-Access-Client-Secret": cfg.clientSecret },
      signal: AbortSignal.timeout(cfg.timeoutMs),
    });
  } catch (e) {
    const err = e as { name?: string; message?: string };
    if (err?.name === "TimeoutError" || err?.name === "AbortError") {
      return markDown(`no answer within ${cfg.timeoutMs} ms`);
    }
    return markDown(`fetch failed: ${err?.message ?? String(e)}`);
  }
  if (resp.status === 404) {
    const text = (await resp.text().catch(() => "")).trim().slice(0, 80);
    return { source: "r2", reason: `pi 404${text ? ` (${text})` : ""}`, markedDown: false };
  }
  if (resp.status !== 200) return markDown(`pi answered ${resp.status}`);

  let json: unknown;
  try {
    json = await resp.json();
  } catch (e) {
    return markDown(`tileset JSON unreadable: ${(e as Error)?.message ?? String(e)}`);
  }
  if (typeof json !== "object" || json === null || Array.isArray(json)) {
    return markDown("tileset JSON is not an object");
  }
  const obj = json as Record<string, unknown>;
  const tiles = obj.tiles;
  const ext =
    Array.isArray(tiles) && typeof tiles[0] === "string" ? (tiles[0].match(/\{y\}\.([a-z]+)$/)?.[1] ?? null) : null;
  if (!ext) return markDown("tileset JSON has no usable tiles URL");
  return { source: "pi", json: { ...obj, tiles: [`${tilesBase}/{z}/{x}/{y}.${ext}`] } };
}
