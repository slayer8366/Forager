// The route-data routes (dispatch 2026-10-23-01; RECORD -1568): the app asks for an area and gets BRouter's own
// square files, each cut down to that area by the cutter (../rd5/cut.ts) from the current build on R2.
//
//   GET /routing/area.json?south=&west=&north=&east=
//     200 {"run_id", "osm_timestamp", "files": [{"name": "W125_N45.rd5", "size", "sha256", "url"}]}
//   GET /routing/<run_id>/<NAME>.rd5?south=&west=&north=&east=      (the "url" above)
//     200 the cut file's bytes, with X-Forager-Sha256
//
// Every failure is a status and {"error", "message"}, never an empty success, and never cached.
//
// Access (owner's choice, dispatch 2026-10-23-01, option B, "Stops casual use of the address; not true secrecy"):
// every request carries the app key in X-Forager-App-Key, checked against the ROUTING_APP_KEY secret. **It is a
// deterrent, not secrecy**: the key ships inside the app, and anyone who unpacks the app can read it. The response
// bodies do not depend on how access is checked, so a stronger check can replace this one without changing them.
//
// Rate limits (owner, option D, "In the server code"): Cloudflare's Workers Rate Limiting binding, two of them,
// configured in wrangler.toml with their numbers and the reasoning. Checked per client address first, so requests
// with a wrong key are counted too, then for the app key. Over either: 429 with Retry-After.
//
// A Worker deployed without the key secret or either limiter refuses every routing request with 503
// "not_configured", and logs it: route data is never served open because a deploy step was missed.
import { Rd5ChecksumError, Rd5UnsupportedError, type RangeReader, type Rectangle, cutRd5 } from "../rd5/cut.ts";
import { parseArea, squaresFor } from "./area.ts";
import { MANIFEST_KEY, type Manifest, ManifestInvalidError, type ManifestSquare, isRunId, parseManifest } from "./manifest.ts";

/** Cloudflare's rate limiting binding, as its documentation shows it: `limit({ key })` resolves to `{ success }`. */
export interface RateLimiter {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

export interface RoutingEnv {
  BUCKET: R2Bucket;
  /** Secret, set with `wrangler secret put ROUTING_APP_KEY`; never in a committed file (the repository is public). */
  ROUTING_APP_KEY?: string;
  ROUTING_IP_LIMITER?: RateLimiter;
  ROUTING_KEY_LIMITER?: RateLimiter;
}

/** The header the app sends its key in. */
export const APP_KEY_HEADER = "X-Forager-App-Key";

/** Both limiters' window, in seconds (wrangler.toml, `period`); also the Retry-After a 429 gives. */
export const ROUTING_RATE_PERIOD_SECONDS = 60;

/** How long the edge keeps a cut. Safe for any length: the run id in its URL means the bytes never change. */
const CUT_CACHE_CONTROL = "public, max-age=2592000";

const FILE_PATH = /^\/routing\/([^/]+)\/([WE]\d{1,3}_[NS]\d{1,2})\.rd5$/;

class SquareMissingError extends Error {}

function errorResponse(status: number, error: string, message?: string, extra: Record<string, unknown> = {}): Response {
  const headers = new Headers({ "Content-Type": "application/json", "Cache-Control": "no-store" });
  if (status === 429) headers.set("Retry-After", String(ROUTING_RATE_PERIOD_SECONDS));
  const body = message === undefined ? { error, ...extra } : { error, message, ...extra };
  return new Response(JSON.stringify(body), { status, headers });
}

const hex = (bytes: Uint8Array): string => [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");

async function sha256(bytes: Uint8Array | string): Promise<Uint8Array> {
  const data = typeof bytes === "string" ? new TextEncoder().encode(bytes) : bytes;
  return new Uint8Array(await crypto.subtle.digest("SHA-256", data));
}

/** Compares digests, so the time taken does not depend on how much of a wrong key matched. */
async function keyMatches(given: string | null, expected: string): Promise<boolean> {
  if (given === null) return false;
  const [a, b] = await Promise.all([sha256(given), sha256(expected)]);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function loadManifest(env: RoutingEnv): Promise<Manifest | Response> {
  const object = await env.BUCKET.get(MANIFEST_KEY);
  if (!object) {
    console.error(`Route data: ${MANIFEST_KEY} is not on R2`);
    return errorResponse(503, "manifest_missing", "route data is not available: no manifest");
  }
  try {
    return parseManifest(await object.text());
  } catch (e) {
    if (e instanceof ManifestInvalidError) {
      console.error(`Route data: ${e.message}`);
      return errorResponse(503, "manifest_invalid", "route data is not available: the manifest cannot be read");
    }
    throw e;
  }
}

/** Reads one square by byte range, checking every read against the manifest's size for it. */
function squareReader(env: RoutingEnv, square: ManifestSquare): RangeReader {
  return {
    size: square.size,
    async read(offset: number, length: number): Promise<Uint8Array> {
      const object = await env.BUCKET.get(square.key, { range: { offset, length } });
      if (!object) throw new SquareMissingError(square.key);
      if (object.size !== square.size) {
        throw new Rd5ChecksumError(`${square.key} is ${object.size} bytes on R2; the manifest says ${square.size}`);
      }
      const bytes = new Uint8Array(await (object as R2ObjectBody).arrayBuffer());
      if (bytes.byteLength !== length) throw new Rd5ChecksumError(`${square.key}: asked for ${length} bytes at ${offset}, got ${bytes.byteLength}`);
      return bytes;
    },
  };
}

interface Cut {
  bytes: Uint8Array;
  sha256: string;
}

/** The cut, from the edge cache when this URL was cut before, else cut now and cached. */
async function cutFor(env: RoutingEnv, cache: Cache, cacheUrl: string, square: ManifestSquare, rect: Rectangle): Promise<Cut> {
  const cached = await cache.match(cacheUrl);
  const cachedSha = cached?.headers.get("X-Forager-Sha256");
  if (cached && cachedSha) return { bytes: new Uint8Array(await cached.arrayBuffer()), sha256: cachedSha };

  const bytes = await cutRd5(squareReader(env, square), square.fileName.replace(/\.rd5$/, ""), rect);
  const digest = hex(await sha256(bytes));
  const headers = new Headers({
    "Content-Type": "application/octet-stream",
    "Cache-Control": CUT_CACHE_CONTROL,
    "X-Forager-Sha256": digest,
  });
  await cache.put(cacheUrl, new Response(bytes, { headers }));
  return { bytes, sha256: digest };
}

/** Maps the cutter's and the reader's failures to their answers; anything else is not ours to name. */
function cutFailure(e: unknown, square: string): Response {
  if (e instanceof SquareMissingError) {
    console.error(`Route data: the manifest names ${e.message} but R2 has no such object`);
    return errorResponse(503, "run_missing_square", `the route data for ${square} is missing from the current build`);
  }
  if (e instanceof Rd5ChecksumError) {
    console.error(`Route data: ${square}: ${e.message}`);
    return errorResponse(502, "checksum", `the route data for ${square} failed its checksum`);
  }
  if (e instanceof Rd5UnsupportedError) {
    console.error(`Route data: ${square}: ${e.message}`);
    return errorResponse(502, "unsupported_square", `the route data for ${square} is in a form this server cannot cut`);
  }
  throw e;
}

/** The rectangle's edges as the request gave them, in a fixed order, so equal areas give equal URLs. */
function areaQuery(params: URLSearchParams): string {
  return ["south", "west", "north", "east"].map((k) => `${k}=${params.get(k)}`).join("&");
}

async function areaResponse(request: Request, env: RoutingEnv, cache: Cache, url: URL): Promise<Response> {
  const area = parseArea(url.searchParams);
  if (!area.ok) return errorResponse(area.status, area.error, area.message);
  const manifest = await loadManifest(env);
  if (manifest instanceof Response) return manifest;

  const needed = squaresFor(area.rect);
  const absent = needed.filter((name) => !manifest.squares.has(name));
  if (absent.length > 0) {
    return errorResponse(404, "outside_coverage", "the area needs route data this build does not cover", { squares: absent });
  }

  const files = [];
  for (const name of needed) {
    const square = manifest.squares.get(name)!;
    const path = `/routing/${manifest.runId}/${square.fileName}?${areaQuery(url.searchParams)}`;
    let cut: Cut;
    try {
      cut = await cutFor(env, cache, new URL(path, request.url).toString(), square, area.rect);
    } catch (e) {
      return cutFailure(e, name);
    }
    files.push({ name: square.fileName, size: cut.bytes.byteLength, sha256: cut.sha256, url: path });
  }
  const body = { run_id: manifest.runId, osm_timestamp: manifest.osmTimestamp, files };
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

async function fileResponse(request: Request, env: RoutingEnv, cache: Cache, url: URL, runId: string, name: string): Promise<Response> {
  const area = parseArea(url.searchParams);
  if (!area.ok) return errorResponse(area.status, area.error, area.message);
  if (!isRunId(runId)) return errorResponse(404, "not_found");
  if (!squaresFor(area.rect).includes(name)) {
    return errorResponse(400, "bad_request", `the area does not need ${name}`);
  }
  // The canonical URL, so a file is cached under the same key the area answer put it under.
  const cacheUrl = new URL(`/routing/${runId}/${name}.rd5?${areaQuery(url.searchParams)}`, request.url).toString();
  const toClient = (bytes: Uint8Array, digest: string): Response =>
    new Response(bytes, {
      headers: {
        "Content-Type": "application/octet-stream",
        "Cache-Control": "no-store",
        "X-Forager-Sha256": digest,
      },
    });

  const cached = await cache.match(cacheUrl);
  const cachedSha = cached?.headers.get("X-Forager-Sha256");
  if (cached && cachedSha) return toClient(new Uint8Array(await cached.arrayBuffer()), cachedSha);

  const manifest = await loadManifest(env);
  if (manifest instanceof Response) return manifest;
  if (runId !== manifest.runId) {
    return errorResponse(409, "run_superseded", "this build of the route data has been replaced; ask for the area again", {
      run_id: manifest.runId,
    });
  }
  const square = manifest.squares.get(name);
  if (!square) return errorResponse(404, "outside_coverage", "the area needs route data this build does not cover", { squares: [name] });
  try {
    const cut = await cutFor(env, cache, cacheUrl, square, area.rect);
    return toClient(cut.bytes, cut.sha256);
  } catch (e) {
    return cutFailure(e, name);
  }
}

/** Answers every request under /routing/. */
export async function routingResponse(request: Request, env: RoutingEnv, cache: Cache): Promise<Response> {
  if (!env.ROUTING_APP_KEY || !env.ROUTING_IP_LIMITER || !env.ROUTING_KEY_LIMITER) {
    console.error(
      "Route data refused: the Worker is missing " +
        [!env.ROUTING_APP_KEY && "the ROUTING_APP_KEY secret", !env.ROUTING_IP_LIMITER && "ROUTING_IP_LIMITER", !env.ROUTING_KEY_LIMITER && "ROUTING_KEY_LIMITER"]
          .filter(Boolean)
          .join(", ")
    );
    return errorResponse(503, "not_configured", "route data is not available from this server");
  }

  let address = request.headers.get("CF-Connecting-IP");
  if (!address) {
    console.warn("Route data: no CF-Connecting-IP header; counting the request under one shared address");
    address = "unknown";
  }
  if (!(await env.ROUTING_IP_LIMITER.limit({ key: `ip:${address}` })).success) {
    return errorResponse(429, "rate_limited", "too many requests; try again shortly");
  }
  if (false) {
    return errorResponse(403, "forbidden");
  }
  if (!(await env.ROUTING_KEY_LIMITER.limit({ key: "app" })).success) {
    return errorResponse(429, "rate_limited", "too many requests; try again shortly");
  }

  const url = new URL(request.url);
  if (url.pathname === "/routing/area.json") return areaResponse(request, env, cache, url);
  const file = FILE_PATH.exec(url.pathname);
  if (file) return fileResponse(request, env, cache, url, file[1], file[2]);
  return errorResponse(404, "not_found");
}
