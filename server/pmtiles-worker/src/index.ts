// Cloudflare Worker that serves ordinary z/x/y tile requests out of a PMTiles archive sitting in
// an R2 bucket — the bridge MapLibre Android's OfflineTilePyramidRegionDefinition needs, since it
// downloads a tile-server-shaped URL template, not a pmtiles:// source directly (see
// docs/plans/maplibre-migration.md §3: MapLibre's OfflineManager and PMTiles sources don't compose).
//
// Adapted from protomaps/PMTiles serverless/cloudflare/src/index.ts (MIT licensed) — the upstream
// reference implementation this project's chosen stack (Cloudflare R2 + Workers) is based on. The
// only changes from upstream: importing the inlined ./shared helpers instead of a sibling
// monorepo package, and renamed identifiers to match this project's naming conventions.
import {
  Compression,
  EtagMismatch,
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
        const t = await p.getTileJson(`https://${env.PUBLIC_HOSTNAME || url.hostname}/${name}`);
        return cacheableResponse(JSON.stringify(t), cacheableHeaders, 200);
      }

      if (tile[0] < pHeader.minZoom || tile[0] > pHeader.maxZoom) {
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

      switch (pHeader.tileType) {
        case TileType.Mvt:
          cacheableHeaders.set("Content-Type", "application/x-protobuf");
          break;
        case TileType.Png:
          cacheableHeaders.set("Content-Type", "image/png");
          break;
        case TileType.Jpeg:
          cacheableHeaders.set("Content-Type", "image/jpeg");
          break;
        case TileType.Webp:
          cacheableHeaders.set("Content-Type", "image/webp");
          break;
      }

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
