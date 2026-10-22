// Cloudflare Worker that serves ordinary z/x/y tile requests out of a PMTiles archive sitting in
// an R2 bucket — the bridge MapLibre Android's OfflineTilePyramidRegionDefinition needs, since it
// downloads a tile-server-shaped URL template, not a pmtiles:// source directly (see
// docs/plans/maplibre-migration.md §3: MapLibre's OfflineManager and PMTiles sources don't compose).
//
// Adapted from protomaps/PMTiles serverless/cloudflare/src/index.ts (MIT licensed) — the upstream
// reference implementation this project's chosen stack (Cloudflare R2 + Workers) is based on. The
// only changes from upstream: importing the inlined ./shared helpers instead of a sibling
// monorepo package, renamed identifiers to match this project's naming conventions, the
// z>14 overflow path below (not present upstream at all), the Street style, font and sprite
// routes (dispatch 2026-10-18-05 Part 2, also not upstream), and the world fill (dispatch 2026-10-22-01 Part A).
import {
  Compression,
  EtagMismatch,
  FetchSource,
  PMTiles,
  type RangeResponse,
  ResolvedValueCache,
  type Source,
  TileType,
  tileTypeExt,
} from "pmtiles";
import { pmtilesPath, tilePath } from "./shared";
import offlineStyle from "./offline-style.json";
import streetStyle from "./street-style-v1.json";

interface Env {
  ALLOWED_ORIGINS?: string;
  BUCKET: R2Bucket;
  CACHE_CONTROL?: string;
  PMTILES_PATH?: string;
  PUBLIC_HOSTNAME?: string;
}

class KeyNotFoundError extends Error {}

async function nativeDecompress(
  buf: ArrayBuffer,
  compression: Compression
): Promise<ArrayBuffer> {
  if (compression === Compression.None || compression === Compression.Unknown) {
    return buf;
  }
  if (compression === Compression.Gzip) {
    const stream = new Response(buf).body;
    const result = stream?.pipeThrough(new DecompressionStream("gzip"));
    return new Response(result).arrayBuffer();
  }
  throw new Error("Compression method not supported");
}

const CACHE = new ResolvedValueCache(25, undefined, nativeDecompress);

class R2Source implements Source {
  env: Env;
  archiveName: string;

  constructor(env: Env, archiveName: string) {
    this.env = env;
    this.archiveName = archiveName;
  }

  getKey() {
    return this.archiveName;
  }

  async getBytes(
    offset: number,
    length: number,
    _signal?: AbortSignal,
    etag?: string
  ): Promise<RangeResponse> {
    const resp = await this.env.BUCKET.get(pmtilesPath(this.archiveName, this.env.PMTILES_PATH), {
      range: { offset, length },
      onlyIf: { etagMatches: etag },
    });
    if (!resp) {
      throw new KeyNotFoundError("Archive not found");
    }

    const o = resp as R2ObjectBody;
    if (!o.body) {
      throw new EtagMismatch();
    }

    const a = await o.arrayBuffer();
    return {
      data: a,
      etag: o.etag,
      cacheControl: o.httpMetadata?.cacheControl,
      expires: o.httpMetadata?.cacheExpiry?.toISOString(),
    };
  }
}

function resolveAllowedOrigin(request: Request, env: Env): string {
  let allowedOrigin = "";
  if (typeof env.ALLOWED_ORIGINS !== "undefined") {
    for (const o of env.ALLOWED_ORIGINS.split(",")) {
      if (o === request.headers.get("Origin") || o === "*") allowedOrigin = o;
    }
  }
  return allowedOrigin;
}

// Served at /style/offline.json — the glyph-stripped style MapLibreOfflineMapRepository (Android)
// points OfflineTilePyramidRegionDefinition at. It must be a real HTTP(S) URL, not asset://: PR #23
// (docs/plans/maplibre-migration.md) confirmed on hardware that OfflineManager's resource-discovery
// path doesn't resolve asset:// style URLs the way normal MapView style loading does — a download
// against one sits at completed=0/1 indefinitely, no error, no progress, no crash, just stuck.
// Hosting it here (rather than raw.githubusercontent.com, which PR #23 used for its throwaway test
// styles) keeps the real app's dependency under this project's own domain instead of GitHub's.
function offlineStyleResponse(allowedOrigin: string): Response {
  const headers = new Headers({
    "Content-Type": "application/json",
    "Cache-Control": "public, max-age=86400",
  });
  if (allowedOrigin) headers.set("Access-Control-Allow-Origin", allowedOrigin);
  return new Response(JSON.stringify(offlineStyle), { headers });
}

const TILE_CONTENT_TYPES: Partial<Record<TileType, string>> = {
  [TileType.Mvt]: "application/x-protobuf",
  [TileType.Png]: "image/png",
  [TileType.Jpeg]: "image/jpeg",
  [TileType.Webp]: "image/webp",
};

// --- z15 overflow: the local `us.pmtiles` archive is built to zoom 14 only (R2's free tier
// storage budget — see server/pmtiles-worker/README.md), but Protomaps' own daily full-planet
// build this archive was extracted from goes one level deeper, to zoom 15. Rather than storing a
// second, much larger flat continental archive to reach it, tiles beyond 14 are range-read
// directly out of the live daily build on first request and cached into R2 individually
// (`overflow/{name}/{z}/{x}/{y}.{ext}`) — so R2 only ever ends up holding the specific z15 tiles
// somebody's offline download actually touched, not a full continental z15 pyramid. This is the
// "scope to actual search regions" design: OfflineTilePyramidRegionDefinition only ever requests
// tiles inside the bbox a user downloaded, so that's exactly what ends up cached, at whatever
// size that region actually costs rather than the continent's.
//
// NOT VERIFIED AGAINST REAL INFRASTRUCTURE — written and reasoned through, but this project has no
// access to a real Cloudflare account/R2 bucket/live network from this session to deploy or
// exercise it against. Two things worth checking before this ships, beyond ordinary code review:
//   1. That `build.protomaps.com/<date>.pmtiles` reliably serves range requests the way PMTiles'
//      FetchSource expects (it should — the whole point of the format — but hasn't been observed
//      here).
//   2. Protomaps' tolerance for *sustained per-tile production traffic* against their public daily
//      build host, as opposed to the occasional bulk `pmtiles extract` this repo's README already
//      documents. Worth confirming with them directly before this is relied on at real scale.
// -------------------------------------------------------------------------------------------------

/** How many days back from today to probe for a live build before giving up. */
const BUILD_RESOLUTION_LOOKBACK_DAYS = 5;

/** How long a resolved build URL is trusted before re-probing — Protomaps publishes daily. */
const BUILD_RESOLUTION_CACHE_SECONDS = 6 * 60 * 60;

function candidateBuildUrl(daysAgo: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - daysAgo);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `https://build.protomaps.com/${y}${m}${day}.pmtiles`;
}

/**
 * Finds today's (or the most recent available) daily Protomaps build URL, since the filename is
 * dated and changes every day — there is no documented stable "latest" alias (checked; see this
 * repo's own README on the same builds channel). Result is cached at the edge so this only
 * actually probes `build.protomaps.com` once per [BUILD_RESOLUTION_CACHE_SECONDS], not per tile
 * request.
 */
async function resolveRemoteBuildUrl(ctx: ExecutionContext): Promise<string> {
  const cacheKey = new Request("https://forager-pmtiles-worker.internal/.protomaps-latest-build-url");
  const cache = caches.default;
  const cached = await cache.match(cacheKey);
  if (cached) return await cached.text();

  for (let daysAgo = 0; daysAgo < BUILD_RESOLUTION_LOOKBACK_DAYS; daysAgo++) {
    const url = candidateBuildUrl(daysAgo);
    const head = await fetch(url, { method: "HEAD" });
    if (head.ok) {
      const resp = new Response(url, {
        headers: { "Cache-Control": `public, max-age=${BUILD_RESOLUTION_CACHE_SECONDS}` },
      });
      ctx.waitUntil(cache.put(cacheKey, resp));
      return url;
    }
  }
  throw new Error(
    `Could not find a live Protomaps daily build in the last ${BUILD_RESOLUTION_LOOKBACK_DAYS} days`
  );
}

/**
 * The latitude of tile row [y]'s north edge at zoom [z] (Web Mercator). Dispatch 2026-10-22-01 Amendment 4: once
 * us.json's south bound was widened for the world fill (Amendment 3), MapLibre asks for z15 tiles south of the
 * archive too, and the overflow would read each one live from Protomaps' build and write it into R2. The owner,
 * asked "South of 16°N at zoom 15, the server would fetch tiles live from the public map provider. Stop that?",
 * chose "Stop it". So a tile lying wholly south of the archive's own header minLat (not the widened TileJSON bound)
 * answers 204, as z9-14 do there. A tile that reaches north of minLat keeps the overflow, unchanged.
 */
export function tileNorthLat(z: number, y: number): number {
  return (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / 2 ** z))) * 180) / Math.PI;
}

function overflowCacheKey(name: string, z: number, x: number, y: number, ext: string): string {
  return `overflow/${name}/${z}/${x}/${y}.${ext}`;
}

/**
 * Serves one tile beyond the local archive's own zoom range by reading it out of Protomaps' live
 * daily build, caching the result into R2 for every subsequent request of that same tile. See the
 * block comment above this section for the design and what's unverified.
 */
async function overflowTileResponse(
  env: Env,
  ctx: ExecutionContext,
  name: string,
  tile: [number, number, number],
  ext: string,
  cacheableHeaders: Headers,
  cacheableResponse: (body: ArrayBuffer | string | undefined, headers: Headers, status: number) => Response
): Promise<Response> {
  const [z, x, y] = tile;
  const key = overflowCacheKey(name, z, x, y, ext);

  const cachedObject = await env.BUCKET.get(key);
  if (cachedObject) {
    if (cachedObject.httpMetadata?.contentType) {
      cacheableHeaders.set("Content-Type", cachedObject.httpMetadata.contentType);
    }
    return cacheableResponse(await cachedObject.arrayBuffer(), cacheableHeaders, 200);
  }

  const remoteUrl = await resolveRemoteBuildUrl(ctx);
  const remoteSource = new FetchSource(remoteUrl);
  const remotePmtiles = new PMTiles(remoteSource, CACHE, nativeDecompress);
  const remoteHeader = await remotePmtiles.getHeader();

  if (z < remoteHeader.minZoom || z > remoteHeader.maxZoom) {
    return cacheableResponse(undefined, cacheableHeaders, 404);
  }

  const tiledata = await remotePmtiles.getZxy(z, x, y);
  if (!tiledata) {
    return cacheableResponse(undefined, cacheableHeaders, 204);
  }

  const contentType = TILE_CONTENT_TYPES[remoteHeader.tileType];
  if (contentType) cacheableHeaders.set("Content-Type", contentType);

  ctx.waitUntil(
    env.BUCKET.put(key, tiledata.data, {
      httpMetadata: contentType ? { contentType } : undefined,
    })
  );

  return cacheableResponse(tiledata.data, cacheableHeaders, 200);
}

// --- R2 only (dispatch 2026-10-18-05 Part 2; RECORD -879, -1300 (1)). The Pi-first step that was here
// (dispatch 2026-10-04-01 part 2) is gone: the owner ruled "Builds the maps and keeps a copy, nothing live"
// (-879), and the Part 1 report found the Pi answering z0-14 for every request. Every tile now comes from
// R2's archive, or from the z15 overflow above. The response still says which, as X-Forager-Origin, so
// the deploy check can read it from outside; with the Pi gone it is always "r2".
const ORIGIN_HEADER = "X-Forager-Origin";

// --- The world fill (dispatch 2026-10-22-01 Part A; RECORD -1554). us.pmtiles stops at about 16°N (its TileJSON
// bounds read 15.92° south on 2026-10-22), so south of that the Street map drew grey. Where the Street archive has
// no tile at z0-8, the tile is read from a second R2 archive, world-south.pmtiles (the Protomaps planet to z8,
// uploaded by the Pi planner), and served labelled with its own origin. Nothing else changes: the TileJSON, the
// z15 overflow, the style routes and every other archive name answer as before.
//
// Which archive is "the Street archive": the Worker has no setting naming it. The name lives in the bundled Street
// style's source URL (…/us.json), so it is read from there rather than written a second time here; the offline
// style points at the same archive. A request under any other name never reaches the world archive.
//
// If world-south.pmtiles is not on R2, the tile answers 204 exactly as before, and a warning is logged. Other
// read errors are not caught here, as with the Street archive's own reads below: a 500 is not cached, a 204 is
// cached for a day.
// -------------------------------------------------------------------------------------------------

/** The R2 archive name (key `world-south.pmtiles`) the fill reads. To be confirmed against the uploaded object. */
const WORLD_FILL_ARCHIVE = "world-south";

/** The fill answers at this zoom and below only; the world archive is built to z8 (RECORD -1554). */
const WORLD_FILL_MAX_ZOOM = 8;

/** X-Forager-Origin on a tile the fill answered. */
const WORLD_FILL_ORIGIN = "r2-world-south";

/** The archive name a bundled style's TileJSON URL names: `https://host/us.json` gives `us`. */
export function archiveNameFromTileJsonUrl(url: string): string {
  const match = new URL(url).pathname.match(/^\/(.+)\.json$/);
  if (!match) throw new Error(`Bundled style source is not a /{name}.json TileJSON URL: ${url}`);
  return match[1];
}

/**
 * The TileJSON [bounds] for the Street archive while the world fill can answer (dispatch 2026-10-22-01 Amendment 3).
 * MapLibre Native never requests a tile outside a source's TileJSON `bounds` (`tile_pyramid.cpp:178-188` at
 * android-v13.5.0, the app's pinned version), and us.pmtiles's header stops at about 15.92°N, so without this the app
 * never asks for a tile the fill could serve. The south bound becomes the world archive's own (its header's minLat,
 * -85.05 on the uploaded file); west, east and north stay as the Street archive's header gives them.
 *
 * Only while the world archive is on R2: presence is one R2 `head` of its key at TileJSON time (the TileJSON is then
 * cached at the edge for a day, so this is not per tile). Absent, or a tile type the fill would refuse, the bounds are
 * returned unchanged and a warning is logged.
 */
async function worldFillBounds(env: Env, bounds: number[], streetTileType: TileType): Promise<number[]> {
  const key = pmtilesPath(WORLD_FILL_ARCHIVE, env.PMTILES_PATH);
  try {
    if (!(await env.BUCKET.head(key))) {
      console.warn(`World fill bounds skipped: ${key} is not on R2; the TileJSON keeps the Street archive's bounds`);
      return bounds;
    }
    const header = await new PMTiles(new R2Source(env, WORLD_FILL_ARCHIVE), CACHE, nativeDecompress).getHeader();
    if (header.tileType !== streetTileType) {
      console.warn(`World fill bounds skipped: ${WORLD_FILL_ARCHIVE} has tile type ${header.tileType}; the TileJSON keeps the Street archive's bounds`);
      return bounds;
    }
    return [bounds[0], Math.min(bounds[1], header.minLat), bounds[2], bounds[3]];
  } catch (e) {
    if (e instanceof KeyNotFoundError) {
      console.warn(`World fill bounds skipped: ${key} vanished during the read; the TileJSON keeps the Street archive's bounds`);
      return bounds;
    }
    throw e;
  }
}

/** The Street archive: the one the bundled Street style draws from. */
export const STREET_ARCHIVE_NAME = archiveNameFromTileJsonUrl(streetStyle.sources.protomaps.url);

/**
 * The world archive's tile at [tile], or undefined when the fill does not apply or has no tile: above
 * [WORLD_FILL_MAX_ZOOM], outside the archive's own zoom range, a tile type that differs from the Street archive's,
 * or the archive missing from R2. Each of the last two is logged, since each means the deploy is not as intended.
 */
async function worldFillTile(
  env: Env,
  tile: [number, number, number],
  streetTileType: TileType
): Promise<ArrayBuffer | undefined> {
  const [z, x, y] = tile;
  if (z > WORLD_FILL_MAX_ZOOM) return undefined;
  const world = new PMTiles(new R2Source(env, WORLD_FILL_ARCHIVE), CACHE, nativeDecompress);
  try {
    const header = await world.getHeader();
    if (header.tileType !== streetTileType) {
      console.warn(
        `World fill skipped: ${WORLD_FILL_ARCHIVE} has tile type ${header.tileType}, ${STREET_ARCHIVE_NAME} has ${streetTileType}; answering 204`
      );
      return undefined;
    }
    const tiledata = await world.getZxy(z, x, y);
    return tiledata?.data;
  } catch (e) {
    if (e instanceof KeyNotFoundError) {
      console.warn(`World fill skipped: ${pmtilesPath(WORLD_FILL_ARCHIVE, env.PMTILES_PATH)} is not on R2; answering 204`);
      return undefined;
    }
    throw e;
  }
}

// --- The vector Street style, its fonts and its icons (dispatch 2026-10-18-05 Part 2).
//
// /style/street-v1.json is map-style/forager-street-v1.json, bundled in like offline-style.json so it needs
// no upload. The "v1" is load-bearing: MapLibre's offline store keys a pack by the style URL it was
// downloaded against, so a later design ships as street-v2.json beside this one and packs downloaded
// against v1 keep drawing while v1 still serves. /style/offline.json is untouched for the same reason
// (-1294).
//
// Fonts and icons are R2 objects the owner uploads (the r2-upload/ folder): fonts/{stack}/{range}.pbf and
// sprites/v4/{light,light@2x}.{json,png}, with fonts/OFL.txt and sprites/LICENSE-MIT.md beside them. They
// are matched before tilePath, because tilePath's tileset pattern also matches /sprites/v4/light.json.
// Only these exact shapes are read; anything else under /fonts/ or /sprites/ is a 404, never an R2 read of
// an arbitrary key.
// -------------------------------------------------------------------------------------------------

/** Fonts and icons never change under one name: a new set gets a new path. A week, at the edge and on the phone. */
const ASSET_CACHE_CONTROL = "public, max-age=604800";

const FONT_PATH = /^\/fonts\/([A-Za-z0-9 ,-]+)\/(\d+-\d+)\.pbf$/;
const SPRITE_PATH = /^\/sprites\/v4\/(light(?:@2x)?)\.(json|png)$/;

/** The R2 key and content type for a font or sprite URL, or null when [pathname] is neither. */
export function assetKeyFor(pathname: string): { key: string; contentType: string } | null {
  let decoded: string;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return null;
  }
  const font = decoded.match(FONT_PATH);
  if (font) return { key: `fonts/${font[1]}/${font[2]}.pbf`, contentType: "application/x-protobuf" };
  const sprite = decoded.match(SPRITE_PATH);
  if (sprite) {
    return { key: `sprites/v4/${sprite[1]}.${sprite[2]}`, contentType: sprite[2] === "png" ? "image/png" : "application/json" };
  }
  return null;
}

function streetStyleResponse(allowedOrigin: string): Response {
  const headers = new Headers({
    "Content-Type": "application/json",
    "Cache-Control": "public, max-age=86400",
  });
  if (allowedOrigin) headers.set("Access-Control-Allow-Origin", allowedOrigin);
  return new Response(JSON.stringify(streetStyle), { headers });
}

async function assetResponse(
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  asset: { key: string; contentType: string },
  allowedOrigin: string
): Promise<Response> {
  const cache = caches.default;
  const withOrigin = (r: Response): Response => {
    const headers = new Headers(r.headers);
    if (allowedOrigin) headers.set("Access-Control-Allow-Origin", allowedOrigin);
    headers.set("Vary", "Origin");
    return new Response(r.body, { headers, status: r.status });
  };
  const cached = await cache.match(request.url);
  if (cached) return withOrigin(cached);

  const object = await env.BUCKET.get(asset.key);
  if (!object) {
    // Not cached: a missing font range or icon file is a deploy gap to fix, not a fact to keep for a week.
    return withOrigin(new Response("Not found", { status: 404 }));
  }
  const body = await object.arrayBuffer();
  const headers = new Headers({ "Content-Type": asset.contentType, "Cache-Control": ASSET_CACHE_CONTROL });
  ctx.waitUntil(cache.put(request.url, new Response(body, { headers })));
  return withOrigin(new Response(body, { headers }));
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method.toUpperCase() === "POST") return new Response(undefined, { status: 405 });

    const url = new URL(request.url);

    if (url.pathname === "/style/offline.json") {
      return offlineStyleResponse(resolveAllowedOrigin(request, env));
    }

    if (url.pathname === "/style/street-v1.json") {
      return streetStyleResponse(resolveAllowedOrigin(request, env));
    }

    const asset = assetKeyFor(url.pathname);
    if (asset) {
      return assetResponse(request, env, ctx, asset, resolveAllowedOrigin(request, env));
    }
    if (url.pathname.startsWith("/fonts/") || url.pathname.startsWith("/sprites/")) {
      return new Response("Not found", { status: 404 });
    }

    const { ok, name, tile, ext } = tilePath(url.pathname);

    const cache = caches.default;

    if (!ok) {
      return new Response("Invalid URL", { status: 404 });
    }

    const allowedOrigin = resolveAllowedOrigin(request, env);

    const cached = await cache.match(request.url);
    if (cached) {
      const respHeaders = new Headers(cached.headers);
      if (allowedOrigin) respHeaders.set("Access-Control-Allow-Origin", allowedOrigin);
      respHeaders.set("Vary", "Origin");

      return new Response(cached.body, {
        headers: respHeaders,
        status: cached.status,
      });
    }

    const cacheableResponse = (
      body: ArrayBuffer | string | undefined,
      cacheableHeaders: Headers,
      status: number
    ) => {
      cacheableHeaders.set("Cache-Control", env.CACHE_CONTROL || "public, max-age=86400");

      const cacheable = new Response(body, {
        headers: cacheableHeaders,
        status,
      });

      ctx.waitUntil(cache.put(request.url, cacheable));

      const respHeaders = new Headers(cacheableHeaders);
      if (allowedOrigin) respHeaders.set("Access-Control-Allow-Origin", allowedOrigin);
      respHeaders.set("Vary", "Origin");
      return new Response(body, { headers: respHeaders, status });
    };

    const cacheableHeaders = new Headers();

    cacheableHeaders.set(ORIGIN_HEADER, "r2");
    const source = new R2Source(env, name);
    const p = new PMTiles(source, CACHE, nativeDecompress);
    try {
      const pHeader = await p.getHeader();

      if (!tile) {
        cacheableHeaders.set("Content-Type", "application/json");
        const t = (await p.getTileJson(`https://${env.PUBLIC_HOSTNAME || url.hostname}/${name}`)) as Record<
          string,
          unknown
        >;
        // Advertise the overflow ceiling, not just the local archive's own maxzoom, so MapLibre's
        // OfflineManager knows tiles are actually servable past 14 — see the overflow block above.
        const declaredMaxZoom = typeof t.maxzoom === "number" ? t.maxzoom : pHeader.maxZoom;
        const advertised: Record<string, unknown> = { ...t, maxzoom: Math.max(declaredMaxZoom, OVERFLOW_MAX_ZOOM) };
        // The world fill is only reachable if the bounds cover it (worldFillBounds, dispatch 2026-10-22-01 Amendment 3).
        if (name === STREET_ARCHIVE_NAME && Array.isArray(t.bounds) && t.bounds.length === 4) {
          advertised.bounds = await worldFillBounds(env, t.bounds as number[], pHeader.tileType);
        }
        return cacheableResponse(JSON.stringify(advertised), cacheableHeaders, 200);
      }

      if (tile[0] < pHeader.minZoom || tile[0] > pHeader.maxZoom) {
        if (tile[0] > pHeader.maxZoom && tile[0] <= OVERFLOW_MAX_ZOOM) {
          // Dispatch 2026-10-22-01 Amendment 4 (the owner: "Stop it"): never south of the archive's own bounds.
          if (tileNorthLat(tile[0], tile[2]) <= pHeader.minLat) {
            return cacheableResponse(undefined, cacheableHeaders, 204);
          }
          return await overflowTileResponse(env, ctx, name, tile, ext, cacheableHeaders, cacheableResponse);
        }
        return cacheableResponse(undefined, cacheableHeaders, 404);
      }

      const extToType: Record<string, TileType> = {
        mvt: TileType.Mvt,
        pbf: TileType.Mvt, // allow this for now, matching upstream's own transitional alias
        png: TileType.Png,
        jpg: TileType.Jpeg,
        webp: TileType.Webp,
        avif: TileType.Avif,
      };

      const expectedType = extToType[ext];
      if (pHeader.tileType !== expectedType && tileTypeExt(pHeader.tileType) !== "") {
        return cacheableResponse(
          `Bad request: requested .${ext} but archive has type ${tileTypeExt(pHeader.tileType)}`,
          cacheableHeaders,
          400
        );
      }

      const tiledata = await p.getZxy(tile[0], tile[1], tile[2]);

      const contentType = TILE_CONTENT_TYPES[pHeader.tileType];
      if (contentType) cacheableHeaders.set("Content-Type", contentType);

      if (tiledata) {
        return cacheableResponse(tiledata.data, cacheableHeaders, 200);
      }
      if (name === STREET_ARCHIVE_NAME) {
        const filled = await worldFillTile(env, tile, pHeader.tileType);
        if (filled) {
          cacheableHeaders.set(ORIGIN_HEADER, WORLD_FILL_ORIGIN);
          return cacheableResponse(filled, cacheableHeaders, 200);
        }
      }
      return cacheableResponse(undefined, cacheableHeaders, 204);
    } catch (e) {
      if (e instanceof KeyNotFoundError) {
        return cacheableResponse("Archive not found", cacheableHeaders, 404);
      }
      throw e;
    }
  },
};

/**
 * The real ceiling is whatever `remoteHeader.maxZoom` reports at request time (checked in
 * [overflowTileResponse], and self-correcting if Protomaps' build ever changes) — this constant is
 * only a cheap upper bound to avoid firing the whole remote-fetch path for a zoom nothing serves,
 * and to advertise a sane `maxzoom` in the tileset JSON without an extra network round trip on
 * every `/us.json` request. 15 matches Protomaps' documented full-planet build ceiling as of this
 * writing (see server/pmtiles-worker/README.md and the resolved offline-strategy note in
 * docs/plans/maplibre-migration.md) — re-check if their builds ever go deeper.
 */
const OVERFLOW_MAX_ZOOM = 15;
