// Cloudflare Worker that serves ordinary z/x/y tile requests out of a PMTiles archive sitting in
// an R2 bucket — the bridge MapLibre Android's OfflineTilePyramidRegionDefinition needs, since it
// downloads a tile-server-shaped URL template, not a pmtiles:// source directly (see
// docs/plans/maplibre-migration.md §3: MapLibre's OfflineManager and PMTiles sources don't compose).
//
// Adapted from protomaps/PMTiles serverless/cloudflare/src/index.ts (MIT licensed) — the upstream
// reference implementation this project's chosen stack (Cloudflare R2 + Workers) is based on. The
// only changes from upstream: importing the inlined ./shared helpers instead of a sibling
// monorepo package, renamed identifiers to match this project's naming conventions, and the
// z>14 overflow path below (not present upstream at all).
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

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method.toUpperCase() === "POST") return new Response(undefined, { status: 405 });

    const url = new URL(request.url);

    if (url.pathname === "/style/offline.json") {
      return offlineStyleResponse(resolveAllowedOrigin(request, env));
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
        const advertised = { ...t, maxzoom: Math.max(declaredMaxZoom, OVERFLOW_MAX_ZOOM) };
        return cacheableResponse(JSON.stringify(advertised), cacheableHeaders, 200);
      }

      if (tile[0] < pHeader.minZoom || tile[0] > pHeader.maxZoom) {
        if (tile[0] > pHeader.maxZoom && tile[0] <= OVERFLOW_MAX_ZOOM) {
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
