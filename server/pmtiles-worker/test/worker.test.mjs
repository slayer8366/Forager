// Route tests for the Worker (dispatch 2026-10-18-05 Part 2), run against dist/index.js, the bundled file the
// owner pastes into the dashboard, not the TypeScript source: what is tested is what ships.
//
//   npm run build && npm test
//
// R2 is a fake backed by test/fixtures/tiny.pmtiles and an in-memory asset map; Protomaps' live build is
// test/fixtures/live-build.pmtiles behind the fetch fake; the edge cache is a Map;
// fetch is a recorder, so "no Pi request" is asserted as "fetch was never called" for tiles R2 holds.
// The Worker is given PI_ORIGIN_URL and both Access secrets, exactly as the deployed Worker has them, so the
// assertion holds even with the Pi configured.
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { gunzipSync } from "node:zlib";
import crypto from "node:crypto";
import * as reference from "../../../map-style/overrides/forager-reference.mjs";

const here = (p) => new URL(p, import.meta.url);
const ARCHIVE = fs.readFileSync(here("./fixtures/tiny.pmtiles"));
// The world fill (dispatch 2026-10-22-01 Part A): R2's world-south.pmtiles, read where us.pmtiles has no tile.
const WORLD = fs.readFileSync(here("./fixtures/world-south.pmtiles"));
// Protomaps' live build, which the z15 overflow reads: two z15 tiles, listed in test/fixtures/make-live-build-pmtiles.py
// (dispatch 2026-10-22-02). Before it, tiny.pmtiles stood in, stopped at z14, and the overflow's R2 write was unreachable.
const LIVE_BUILD = fs.readFileSync(here("./fixtures/live-build.pmtiles"));
const OFFLINE_STYLE_SOURCE = fs.readFileSync(here("../src/offline-style.json"), "utf8");
const STREET_STYLE_SOURCE = fs.readFileSync(here("../src/street-style-v1.json"), "utf8");
const STREET_V2_STYLE_SOURCE = fs.readFileSync(here("../src/street-style-v2.json"), "utf8");
// Step 8a (dispatch 2026-10-26-14): Trail Map v3, with the official-trails band, and the synthetic archive standing in
// for reference-trails.pmtiles (test/fixtures/make-reference-trails-pmtiles.py; invented positions, marker bodies).
const STREET_V3_STYLE_SOURCE = fs.readFileSync(here("../src/street-style-v3.json"), "utf8");
const REFERENCE = fs.readFileSync(here("./fixtures/reference-trails.pmtiles"));
// The archive's manifest (dispatch 2026-10-26-22 M1), an R2 object beside it: invented values in the real file's shape.
const MANIFEST = fs.readFileSync(here("./fixtures/reference-trails-manifest.json"));
const HOST = "https://forager-pmtiles.brandonlee1-894.workers.dev";

let fetchCalls;
let bucketReads;
let assets;
let worldOnR2;
let bucketHeads;
let bucketPuts;
let referenceOnR2;

class FakeCache {
  constructor() { this.entries = new Map(); }
  async match(key) {
    const url = typeof key === "string" ? key : key.url;
    const hit = this.entries.get(url);
    return hit ? hit.clone() : undefined;
  }
  async put(key, response) {
    const url = typeof key === "string" ? key : key.url;
    this.entries.set(url, response.clone());
  }
}

function rangeRead(archive, options) {
  const { offset, length } = options.range;
  const slice = archive.subarray(offset, offset + length);
  return { body: true, etag: "fixture", httpMetadata: {}, arrayBuffer: async () => slice.buffer.slice(slice.byteOffset, slice.byteOffset + slice.byteLength) };
}

const bucket = {
  async get(key, options) {
    bucketReads.push(key);
    // "other.pmtiles" is any archive that is not the Street one: the same tiles as us.pmtiles, so a tile missing
    // from it is missing from us too, and only the archive's name differs.
    if (key === "us.pmtiles" || key === "other.pmtiles") return rangeRead(ARCHIVE, options);
    if (key === "world-south.pmtiles" && worldOnR2) return rangeRead(WORLD, options);
    if (key === "reference-trails.pmtiles" && referenceOnR2) return rangeRead(REFERENCE, options);
    if (assets.has(key)) {
      const bytes = assets.get(key);
      return { body: true, etag: "asset", httpMetadata: {}, arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) };
    }
    return null;
  },
  async put(key) {
    bucketPuts.push(key);
  },
  // R2's metadata-only read; the Worker uses it to ask whether the world archive is there (dispatch 2026-10-22-01 A3).
  async head(key) {
    bucketHeads.push(key);
    if (key === "us.pmtiles" || key === "other.pmtiles") return { key };
    if (key === "world-south.pmtiles" && worldOnR2) return { key };
    if (key === "reference-trails.pmtiles" && referenceOnR2) return { key };
    return null;
  },
};

const env = {
  BUCKET: bucket,
  ALLOWED_ORIGINS: "*",
  PI_ORIGIN_URL: "https://origin.zynergy-labs.com",
  CF_ACCESS_CLIENT_ID: "test-id",
  CF_ACCESS_CLIENT_SECRET: "test-secret",
};

let pending;
const ctx = { waitUntil: (p) => pending.push(p) };

async function call(path) {
  const response = await worker.fetch(new Request(`${HOST}${path}`), env, ctx);
  await Promise.all(pending);
  return response;
}

globalThis.caches = { default: new FakeCache() };
globalThis.fetch = async (input, init) => {
  const url = typeof input === "string" ? input : input.url;
  fetchCalls.push({ url, method: init?.method ?? input.method ?? "GET", headers: init?.headers });
  if (url.startsWith("https://build.protomaps.com/")) {
    if ((init?.method ?? "GET") === "HEAD") return new Response(null, { status: 200 });
    // Serve the live-build fixture, with Range support, as FetchSource expects.
    const range = new Headers(init?.headers).get("range") ?? new Headers(input.headers ?? {}).get("range");
    const [, start, end] = /bytes=(\d+)-(\d+)/.exec(range);
    const slice = LIVE_BUILD.subarray(Number(start), Number(end) + 1);
    return new Response(slice, { status: 206, headers: { "Content-Length": String(slice.length), ETag: '"live"' } });
  }
  return new Response("unexpected fetch", { status: 599 });
};

const { default: worker } = await import("../dist/index.js");

beforeEach(() => {
  fetchCalls = [];
  bucketReads = [];
  pending = [];
  worldOnR2 = true;
  referenceOnR2 = true;
  bucketHeads = [];
  bucketPuts = [];
  globalThis.caches.default = new FakeCache();
  assets = new Map([
    ["fonts/Noto Sans Regular/0-255.pbf", Buffer.from("glyphs 0-255")],
    ["sprites/v4/light.json", Buffer.from('{"townspot":{}}')],
    ["sprites/v4/light@2x.png", Buffer.from([0x89, 0x50, 0x4e, 0x47])],
    ["sprites/v5/light.json", Buffer.from('{"townspot":{},"trailhead_b":{}}')],
    ["sprites/v5/light@2x.png", Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x35])],
    ["reference-trails-manifest.json", MANIFEST],
  ]);
});

test("a tile R2 holds is served from R2 with no request to the Pi, even with the Pi configured", async () => {
  const response = await call("/us/14/2651/5776.mvt");
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
  assert.equal(response.headers.get("Content-Type"), "application/x-protobuf");
  const body = Buffer.from(await response.arrayBuffer());
  const text = body[0] === 0x1f ? gunzipSync(body).toString() : body.toString();
  assert.equal(text, "tile 14/2651/5776");
  assert.deepEqual(fetchCalls, [], `the Worker made outbound requests: ${JSON.stringify(fetchCalls)}`);
  assert.ok(bucketReads.every((k) => k === "us.pmtiles"));
});

test("a z15 tile still goes to the overflow path (Protomaps' live build), never to the Pi", async () => {
  const response = await call("/us/15/5303/11553.mvt");
  // The "live build" fixture holds this tile (test/fixtures/make-live-build-pmtiles.py), so the overflow answers 200.
  assert.equal(response.status, 200);
  assert.ok(fetchCalls.some((c) => c.url.startsWith("https://build.protomaps.com/")), JSON.stringify(fetchCalls));
  assert.ok(!fetchCalls.some((c) => c.url.includes("origin.zynergy-labs.com")), JSON.stringify(fetchCalls));
  assert.ok(bucketReads.includes("overflow/us/15/5303/11553.mvt"));
});

test("the TileJSON still advertises maxzoom 15 and the Worker's own tile URL", async () => {
  const response = await call("/us.json");
  assert.equal(response.status, 200);
  const tilejson = await response.json();
  assert.equal(tilejson.maxzoom, 15);
  assert.deepEqual(tilejson.tiles, [`${HOST}/us/{z}/{x}/{y}.mvt`]);
});

test("/style/offline.json is the bundled offline style, byte for byte as before", async () => {
  const response = await call("/style/offline.json");
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Type"), "application/json");
  assert.equal(await response.text(), JSON.stringify(JSON.parse(OFFLINE_STYLE_SOURCE)));
});

test("/style/street-v1.json is the Street style, with fonts and icons on this Worker only", async () => {
  const response = await call("/style/street-v1.json");
  assert.equal(response.status, 200);
  const text = await response.text();
  assert.equal(text, JSON.stringify(JSON.parse(STREET_STYLE_SOURCE)));
  const style = JSON.parse(text);
  assert.equal(style.glyphs, `${HOST}/fonts/{fontstack}/{range}.pbf`);
  assert.equal(style.sprite, `${HOST}/sprites/v4/light`);
  assert.equal(style.sources.protomaps.url, `${HOST}/us.json`);
});

test("/style/street-v2.json is Trail Map's v2 style, on sprite v5, beside an unchanged v1 (dispatch 2026-10-22-03)", async () => {
  const response = await call("/style/street-v2.json");
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Type"), "application/json");
  const text = await response.text();
  assert.equal(text, JSON.stringify(JSON.parse(STREET_V2_STYLE_SOURCE)));
  const style = JSON.parse(text);
  assert.equal(style.name, "Forager Street v2");
  assert.equal(style.glyphs, `${HOST}/fonts/{fontstack}/{range}.pbf`);
  assert.equal(style.sprite, `${HOST}/sprites/v5/light`);
  // The same tiles as v1, so a region downloaded against either draws the other's tiles.
  assert.equal(style.sources.protomaps.url, JSON.parse(STREET_STYLE_SOURCE).sources.protomaps.url);
  const ids = style.layers.map((l) => l.id);
  for (const id of ["landuse_forest_glyphs", "landuse_wetland_tufts", "pois_area_names"]) assert.ok(ids.includes(id), id);
  // v1 is still v1: its own sprite, and none of v2's layers.
  const v1 = JSON.parse(await (await call("/style/street-v1.json")).text());
  assert.equal(v1.sprite, `${HOST}/sprites/v4/light`);
  assert.ok(!v1.layers.some((l) => l.id === "landuse_forest_glyphs"));
});

test("a font range is read from R2 under fonts/, with a week's cache", async () => {
  const response = await call("/fonts/Noto%20Sans%20Regular/0-255.pbf");
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Type"), "application/x-protobuf");
  assert.equal(response.headers.get("Cache-Control"), "public, max-age=604800");
  assert.equal(await response.text(), "glyphs 0-255");
  assert.deepEqual(bucketReads, ["fonts/Noto Sans Regular/0-255.pbf"]);
  // The second request is answered from the edge cache, not R2.
  bucketReads = [];
  const again = await call("/fonts/Noto%20Sans%20Regular/0-255.pbf");
  assert.equal(await again.text(), "glyphs 0-255");
  assert.deepEqual(bucketReads, []);
});

test("sprite files are read from R2 under sprites/v4/, not mistaken for a tileset", async () => {
  const json = await call("/sprites/v4/light.json");
  assert.equal(json.status, 200);
  assert.equal(json.headers.get("Content-Type"), "application/json");
  assert.equal(await json.text(), '{"townspot":{}}');
  const png = await call("/sprites/v4/light@2x.png");
  assert.equal(png.status, 200);
  assert.equal(png.headers.get("Content-Type"), "image/png");
  assert.deepEqual(bucketReads, ["sprites/v4/light.json", "sprites/v4/light@2x.png"]);
});

test("sprite v5 files are read from R2 under sprites/v5/, with a week's cache, and v4 stays where it was", async () => {
  const json = await call("/sprites/v5/light.json");
  assert.equal(json.status, 200);
  assert.equal(json.headers.get("Content-Type"), "application/json");
  assert.equal(json.headers.get("Cache-Control"), "public, max-age=604800");
  assert.equal(await json.text(), '{"townspot":{},"trailhead_b":{}}');
  const png = await call("/sprites/v5/light@2x.png");
  assert.equal(png.status, 200);
  assert.equal(png.headers.get("Content-Type"), "image/png");
  assert.deepEqual([...Buffer.from(await png.arrayBuffer())], [0x89, 0x50, 0x4e, 0x47, 0x35]);
  const v4 = await call("/sprites/v4/light.json");
  assert.equal(await v4.text(), '{"townspot":{}}');
  assert.deepEqual(bucketReads, ["sprites/v5/light.json", "sprites/v5/light@2x.png", "sprites/v4/light.json"]);
});

test("a missing font range is a 404 and is not cached", async () => {
  const response = await call("/fonts/Noto%20Sans%20Regular/256-511.pbf");
  assert.equal(response.status, 404);
  assets.set("fonts/Noto Sans Regular/256-511.pbf", Buffer.from("later"));
  const again = await call("/fonts/Noto%20Sans%20Regular/256-511.pbf");
  assert.equal(again.status, 200);
});

test("other paths under /fonts/ and /sprites/ never read R2", async () => {
  for (const path of [
    "/fonts/../us.pmtiles", "/fonts/Noto%20Sans%20Regular/x.pbf", "/sprites/v4/dark.json", "/sprites/us.pmtiles",
    "/sprites/v6/light.json", "/sprites/v5/dark.png",
  ]) {
    const response = await call(path);
    assert.equal(response.status, 404, path);
  }
  assert.deepEqual(bucketReads, []);
});

// --- The world fill (dispatch 2026-10-22-01 Part A, RECORD -1554). us.pmtiles stops at about 16°N; where it has no
// tile at z0-8, the Worker answers from world-south.pmtiles instead of 204. The fixture's tiles and what each is for
// are listed in test/fixtures/make-world-south-pmtiles.py.

async function tileText(response) {
  const body = Buffer.from(await response.arrayBuffer());
  return body[0] === 0x1f ? gunzipSync(body).toString() : body.toString();
}

test("world fill: a z5 tile us.pmtiles lacks is served from world-south.pmtiles, labelled as such", async () => {
  const response = await call("/us/5/9/16.mvt");
  assert.equal(response.status, 200);
  assert.equal(await tileText(response), "world 5/9/16");
  assert.equal(response.headers.get("X-Forager-Origin"), "r2-world-south");
  assert.equal(response.headers.get("Content-Type"), "application/x-protobuf");
  assert.equal(response.headers.get("Cache-Control"), "public, max-age=86400");
  // Only world-south.pmtiles is asserted read: the Worker's PMTiles cache is module-wide, so us.pmtiles's header and
  // directories may already be cached from an earlier test and its missing tile then needs no R2 read at all.
  assert.ok(bucketReads.includes("world-south.pmtiles"), JSON.stringify(bucketReads));
  assert.deepEqual(fetchCalls, [], `the Worker made outbound requests: ${JSON.stringify(fetchCalls)}`);
});

test("world fill: z8, the top of the fill's range, is served from world-south.pmtiles", async () => {
  const response = await call("/us/8/75/130.mvt");
  assert.equal(response.status, 200);
  assert.equal(await tileText(response), "world 8/75/130");
  assert.equal(response.headers.get("X-Forager-Origin"), "r2-world-south");
});

test("world fill: a z9 tile stays 204 even though world-south.pmtiles holds it", async () => {
  const response = await call("/us/9/150/260.mvt");
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
  assert.ok(!bucketReads.includes("world-south.pmtiles"), JSON.stringify(bucketReads));
});

test("world fill: a tile missing from both archives is still 204", async () => {
  // No R2 read is asserted: with both archives' directories cached from earlier tests, a tile in neither directory
  // needs none. The claim here is the answer: still 204, labelled as the Street archive's.
  const response = await call("/us/5/9/17.mvt");
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
});

test("world fill: a tile us.pmtiles holds is served from us.pmtiles, and the world archive is never read", async () => {
  // world-south.pmtiles holds 0/0/0 too, with different bytes, so the order of the two reads is what is tested.
  const response = await call("/us/0/0/0.mvt");
  assert.equal(response.status, 200);
  assert.equal(await tileText(response), "tile 0/0/0");
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
  assert.ok(!bucketReads.includes("world-south.pmtiles"), JSON.stringify(bucketReads));
});

test("world fill: with world-south.pmtiles not on R2, a missing tile is 204 as before, not a 404 or an error", async () => {
  worldOnR2 = false;
  const response = await call("/us/5/9/16.mvt");
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
  assert.ok(bucketReads.includes("world-south.pmtiles"), JSON.stringify(bucketReads));
});

test("world fill: an archive other than the Street one never falls back to the world archive", async () => {
  const response = await call("/other/5/9/16.mvt");
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
  assert.deepEqual(bucketReads.filter((k) => k !== "other.pmtiles"), []);
});

// --- The TileJSON's bounds (dispatch 2026-10-22-01 Amendment 3). MapLibre Native never requests a tile outside a
// source's TileJSON bounds (tile_pyramid.cpp:178-188 at android-v13.5.0), so the fill is reachable only if us.json's
// south bound covers it. tiny.pmtiles's header: -124.85, 24.4, -66.87, 49.6; world-south.pmtiles's south: -85.

function assertBounds(actual, expected, label) {
  assert.equal(actual.length, 4, `${label}: ${JSON.stringify(actual)}`);
  expected.forEach((v, i) => assert.ok(Math.abs(actual[i] - v) < 1e-6, `${label}: bounds[${i}] ${actual[i]}, expected ${v}`));
}

test("TileJSON: with the world archive on R2, us.json's south bound is the world archive's; the rest is the header's", async () => {
  const response = await call("/us.json");
  assert.equal(response.status, 200);
  const tilejson = await response.json();
  assertBounds(tilejson.bounds, [-124.85, -85, -66.87, 49.6], "us.json");
  assert.equal(tilejson.maxzoom, 15);
  assert.ok(bucketHeads.includes("world-south.pmtiles"), JSON.stringify(bucketHeads));
});

test("TileJSON: with the world archive not on R2, us.json keeps the header's bounds", async () => {
  worldOnR2 = false;
  const response = await call("/us.json");
  const tilejson = await response.json();
  assertBounds(tilejson.bounds, [-124.85, 24.4, -66.87, 49.6], "us.json, no world archive");
});

test("TileJSON: an archive other than the Street one keeps its own bounds and never asks after the world archive", async () => {
  const response = await call("/other.json");
  assert.equal(response.status, 200);
  const tilejson = await response.json();
  assertBounds(tilejson.bounds, [-124.85, 24.4, -66.87, 49.6], "other.json");
  assert.deepEqual(bucketHeads, []);
});

// --- The overflow stays inside us.pmtiles's own bounds (dispatch 2026-10-22-01 Amendment 4; the owner, "Stop it").
// With us.json's south bound widened for the fill (Amendment 3), the app now asks for southern z15 tiles too; south of
// the archive's own header minLat (tiny.pmtiles: 24.4) they answer 204 like z9-14, with no Protomaps read and no R2
// write. 15/9641/15964 is Bogotá (north edge 4.61°N).

test("overflow: a z15 tile south of the archive's own bounds is 204, with no Protomaps read and no R2 read or write", async () => {
  // The live-build fixture holds this tile, so a Worker that still ran the overflow here would fetch it, read
  // overflow/ and write it into R2 (dispatch 2026-10-22-02). The side effects and the status are compared in one
  // deepEqual, so a failure shows all four at once rather than stopping at the first.
  const response = await call("/us/15/9641/15964.mvt");
  assert.deepEqual(
    {
      fetchCalls: fetchCalls.map((c) => `${c.method} ${c.url}`),
      overflowReads: bucketReads.filter((k) => k.startsWith("overflow/")),
      bucketPuts,
      status: response.status,
    },
    { fetchCalls: [], overflowReads: [], bucketPuts: [], status: 204 },
  );
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
});

test("overflow: a z15 tile inside the archive's own bounds still goes to the overflow path", async () => {
  // Portland (north edge 46.79°N), the same tile the first overflow test uses: unchanged by Amendment 4. The live
  // build holds it, so the overflow serves it and copies it into R2: the write path's first test (dispatch 2026-10-22-02).
  const response = await call("/us/15/5303/11553.mvt");
  assert.deepEqual(bucketPuts, ["overflow/us/15/5303/11553.mvt"]);
  assert.equal(response.status, 200);
  assert.equal(await tileText(response), "live 15/5303/11553");
  assert.ok(fetchCalls.some((c) => c.url.startsWith("https://build.protomaps.com/")), JSON.stringify(fetchCalls));
  assert.ok(bucketReads.includes("overflow/us/15/5303/11553.mvt"));
});

// --- Step 8a: Trail Map v3 with the official-trails band (dispatch 2026-10-26-14 and Amendment 1; RECORD -1633, -1678).
// The archive's names come from map-style/overrides/forager-reference.mjs, the one place they are written.

const sha256 = (path) => crypto.createHash("sha256").update(fs.readFileSync(here(path))).digest("hex");
const v3Style = () => JSON.parse(STREET_V3_STYLE_SOURCE);
const referenceLayers = (style) => style.layers.filter((l) => l.source === reference.REFERENCE_SOURCE_ID);

test("/style/street-v3.json is the bundled v3 style, beside v2 and v1, which are served as before", async () => {
  const response = await call("/style/street-v3.json");
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Type"), "application/json");
  const text = await response.text();
  assert.equal(text, JSON.stringify(JSON.parse(STREET_V3_STYLE_SOURCE)));
  assert.equal(JSON.parse(text).name, "Forager Street v3");
  assert.equal(await (await call("/style/street-v2.json")).text(), JSON.stringify(JSON.parse(STREET_V2_STYLE_SOURCE)));
  assert.equal(await (await call("/style/street-v1.json")).text(), JSON.stringify(JSON.parse(STREET_STYLE_SOURCE)));
  assert.deepEqual(fetchCalls, []);
});

test("v2 is frozen: all three copies byte for byte what pre-main c91f34c49 held", () => {
  // Regions downloaded against v2 keep drawing in it (MapLibre keys a region by its style URL), so v2 never changes.
  const v2 = "7547507bc2b7dbdae24e8b3735eeda569c0f903c068282259b1e51b4bcc7886d";
  assert.deepEqual(
    {
      worker: sha256("../src/street-style-v2.json"),
      app: sha256("../../../map-style/forager-street-v2.json"),
      preview: sha256("../../../map-style/forager-street-v2.preview.json"),
    },
    { worker: v2, app: v2, preview: "d07ce0e343c1187e607f04e5bb455898776e9acaa41c9133430f459f98fdd039" },
  );
});

// Since dispatch 2026-10-27-02 (RECORD -1730) v3 also differs from v2 in its buildings: the buildings layer's paint, and
// one new layer, buildings_outline. Everything else is v2's, which this test holds by comparing the rest whole.
const BUILDINGS_EDIT = new Set(["buildings", "buildings_outline"]);

test("v3 is v2 plus the reference source and its layers and the buildings edit, and nothing else", () => {
  const v3 = v3Style();
  const v2 = JSON.parse(STREET_V2_STYLE_SOURCE);
  const { [reference.REFERENCE_SOURCE_ID]: added, ...sources } = v3.sources;
  assert.ok(added, "v3 has no reference source");
  assert.deepEqual(sources, v2.sources);
  const own = v3.layers.filter((l) => l.source !== reference.REFERENCE_SOURCE_ID);
  assert.deepEqual(own.filter((l) => !BUILDINGS_EDIT.has(l.id)), v2.layers.filter((l) => !BUILDINGS_EDIT.has(l.id)));
  assert.deepEqual(own.map((l) => l.id), v2.layers.flatMap((l) => (l.id === "buildings" ? ["buildings", "buildings_outline"] : [l.id])));
  // The buildings layer keeps all but its paint: the same source, source-layer, filter and zooms as v2's.
  const buildings = (style) => style.layers.find((l) => l.id === "buildings");
  assert.deepEqual({ ...buildings(v3), paint: undefined }, { ...buildings(v2), paint: undefined });
  assert.deepEqual({ ...v3, name: v2.name, sources: v2.sources, layers: v2.layers, metadata: undefined }, { ...v2, metadata: undefined });
  // The same app tiles, glyphs and sprite: a v3 region draws v2's tiles and fonts.
  assert.equal(v3.sources.protomaps.url, v2.sources.protomaps.url);
});

test("buildings are solid, a step darker at z14, with a 0.75 outline from z15 directly above them (dispatch 2026-10-27-02)", () => {
  // After the OpenStreetMap Carto style's buildings (style/buildings.mss): an opaque fill, 4% darker at its lowest zoom,
  // and from z15 a line 15% darker. The owner: "Can we have a border around the buildings and houses to separate them
  // from the rest of the map?" (RECORD -1730).
  const layers = v3Style().layers;
  const at = (id) => layers.findIndex((l) => l.id === id);
  const buildings = layers[at("buildings")];
  assert.deepEqual(buildings.paint, { "fill-color": ["step", ["zoom"], "#c2c2c2", 15, "#cccccc"], "fill-opacity": 1 });
  assert.equal(at("buildings_outline"), at("buildings") + 1, "the outline is not directly above the buildings");
  assert.deepEqual(layers[at("buildings_outline")], {
    id: "buildings_outline",
    type: "line",
    source: "protomaps",
    "source-layer": "buildings",
    filter: buildings.filter,
    minzoom: 15,
    paint: { "line-color": "#a6a6a6", "line-width": 0.75 },
  });
  // The forest and its tree glyphs draw below, so a building covers them.
  for (const id of ["landuse_park", "landuse_forest_glyphs"]) assert.ok(at(id) >= 0 && at(id) < at("buildings"), id);
});

test("the reference source carries its tile address on this Worker, zooms 10 to 15, and no TileJSON URL", () => {
  const source = v3Style().sources[reference.REFERENCE_SOURCE_ID];
  // A TileJSON URL would 404 until the archive exists, and MapLibre treats that 404 as an error (Amendment 1).
  assert.deepEqual(source, {
    type: "vector",
    tiles: [`${HOST}/reference-trails/{z}/{x}/{y}.mvt`],
    minzoom: 10,
    maxzoom: 15,
  });
  assert.equal(source.url, undefined);
});

test("the bands sit below every Trail Map trail line, the number above them all", () => {
  const ids = v3Style().layers.map((l) => l.id);
  const trails = ids.flatMap((id, i) => (id.startsWith("roads_path_") ? [i] : []));
  assert.equal(trails.length, 18, "Trail Map v2 has 18 trail layers (roads_path_track to roads_path_hard_faint)");
  const at = (id) => ids.indexOf(id);
  assert.deepEqual(
    { fsBand: at("reference_fs_band"), blmBand: at("reference_blm_band"), number: at("reference_fs_number") },
    { fsBand: trails[0] - 2, blmBand: trails[0] - 1, number: trails[trails.length - 1] + 1 },
  );
  assert.deepEqual(referenceLayers(v3Style()).map((l) => l.id), ["reference_fs_band", "reference_blm_band", "reference_fs_number"]);
});

test("the bands: one per agency source-layer, violet at 35%, 6 to 10 wide, from zoom 12; the number from 14", () => {
  const byId = Object.fromEntries(referenceLayers(v3Style()).map((l) => [l.id, l]));
  for (const [id, sourceLayer] of [["reference_fs_band", "fs_trails"], ["reference_blm_band", "blm_trails"]]) {
    const l = byId[id];
    assert.ok(l, `v3 has no ${id} layer`);
    assert.equal(l.type, "line", id);
    assert.equal(l["source-layer"], sourceLayer, id);
    assert.equal(l.minzoom, 12, id);
    assert.equal(l.paint["line-color"], "#7b5ea7", id);
    assert.equal(l.paint["line-opacity"], 0.35, id);
    assert.deepEqual(l.paint["line-width"], ["interpolate", ["linear"], ["zoom"], 12, 6, 15, 10], id);
    // The -1633 default "On": visible until 8b's switch says otherwise.
    assert.notEqual(l.layout?.visibility, "none", id);
  }
  const number = byId.reference_fs_number;
  assert.ok(number, "v3 has no reference_fs_number layer");
  assert.equal(number.type, "symbol");
  assert.equal(number["source-layer"], "fs_trails");
  assert.equal(number.minzoom, 14);
  assert.deepEqual(number.layout["text-field"], ["get", "trail_no"]);
});

// The filters are evaluated on features, not compared as text: what is claimed is which trails draw. The evaluator below
// knows only the operators these filters may use and throws on any other, so a filter written with a new operator fails
// here rather than being read wrongly. Its answers were checked against MapLibre GL JS 6.12.0's own featureFilter
// (map-style's pinned copy) when written (dispatch 2026-10-26-22).
function evaluate(expression, properties) {
  if (!Array.isArray(expression)) return expression;
  const [op, ...args] = expression;
  const value = (e) => evaluate(e, properties);
  switch (op) {
    case "get": return Object.hasOwn(properties, args[0]) ? properties[args[0]] : null;
    case "has": return Object.hasOwn(properties, args[0]);
    case "literal": return args[0];
    case "coalesce": for (const a of args) { const v = value(a); if (v !== null && v !== undefined) return v; } return null;
    case "==": return value(args[0]) === value(args[1]);
    case "!=": return value(args[0]) !== value(args[1]);
    case "!": return !value(args[0]);
    case "all": return args.every((a) => value(a) === true);
    case "any": return args.some((a) => value(a) === true);
    case "match": {
      const input = value(args[0]);
      for (let i = 1; i + 1 < args.length; i += 2) {
        const labels = Array.isArray(args[i]) ? args[i] : [args[i]];
        if (labels.includes(input)) return value(args[i + 1]);
      }
      return value(args[args.length - 1]);
    }
    default: throw new Error(`the test's evaluator does not know the operator ${JSON.stringify(op)}`);
  }
}

// One Forest Service feature per trail_type the live archive carries (RECORD -1700: TERRA 78,169, SNOW 8,177, WATER 76),
// and one with no type, which is kept.
const FS_FEATURES = {
  terra: { trail_type: "TERRA", trail_no: "060" },
  snow: { trail_type: "SNOW", trail_no: "061" },
  water: { trail_type: "WATER", trail_no: "062" },
  untyped: { trail_no: "063" },
};
const drawnBy = (layer) => Object.keys(FS_FEATURES).filter((k) => layer.filter === undefined || evaluate(layer.filter, FS_FEATURES[k]) === true);

test("Forest Service snow and water trails are left out of its band and its number; BLM's band has no filter", () => {
  const byId = Object.fromEntries(referenceLayers(v3Style()).map((l) => [l.id, l]));
  for (const id of ["reference_fs_band", "reference_blm_band", "reference_fs_number"]) assert.ok(byId[id], `v3 has no ${id} layer`);
  assert.deepEqual(
    { band: drawnBy(byId.reference_fs_band), number: drawnBy(byId.reference_fs_number) },
    { band: ["terra", "untyped"], number: ["terra", "untyped"] },
  );
  // The number also needs a number to show.
  assert.equal(evaluate(byId.reference_fs_number.filter, { trail_type: "TERRA" }), false);
  assert.equal(byId.reference_blm_band.filter, undefined);
});

test("reference archive, generic path: a z10 tile is served from reference-trails.pmtiles, with no outside request", async () => {
  const response = await call(`/${reference.REFERENCE_ARCHIVE}/10/163/360.mvt`);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("X-Forager-Origin"), "r2");
  assert.equal(response.headers.get("Content-Type"), "application/x-protobuf");
  assert.equal(await tileText(response), "reference 10/163/360");
  assert.deepEqual(fetchCalls, []);
  assert.ok(bucketReads.every((k) => k === "reference-trails.pmtiles"), JSON.stringify(bucketReads));
});

test("reference archive, generic path: a z15 tile comes from the archive, never from the Protomaps overflow", async () => {
  // live-build.pmtiles holds this tile too, so an overflow answer would read "live 15/5303/11553".
  const response = await call(`/${reference.REFERENCE_ARCHIVE}/15/5303/11553.mvt`);
  assert.equal(response.status, 200);
  assert.equal(await tileText(response), "reference 15/5303/11553");
  assert.deepEqual({ fetchCalls, bucketPuts }, { fetchCalls: [], bucketPuts: [] });
});

test("reference archive, generic path: a tile it lacks is 204, never us's tile or the world fill's", async () => {
  // tiny.pmtiles (us) holds 14/2651/5776; the world fill answers only for us, and only to z8.
  const response = await call(`/${reference.REFERENCE_ARCHIVE}/14/2651/5776.mvt`);
  assert.equal(response.status, 204);
  assert.ok(bucketReads.every((k) => k === "reference-trails.pmtiles"), JSON.stringify(bucketReads));
  assert.deepEqual(fetchCalls, []);
});

test("reference archive, generic path: its TileJSON has its own zooms and bounds, and never asks after the world archive", async () => {
  const response = await call(`/${reference.REFERENCE_ARCHIVE}.json`);
  assert.equal(response.status, 200);
  const tilejson = await response.json();
  assert.deepEqual(
    { tiles: tilejson.tiles, minzoom: tilejson.minzoom, maxzoom: tilejson.maxzoom, bounds: tilejson.bounds },
    { tiles: [`${HOST}/reference-trails/{z}/{x}/{y}.mvt`], minzoom: 10, maxzoom: 15, bounds: [-123, 42, -120, 46] },
  );
  assert.deepEqual(bucketHeads, []);
});

test("reference archive not on R2 yet: a tile is a 404 (no content to MapLibre), with no Protomaps read or R2 write", async () => {
  referenceOnR2 = false;
  for (const path of ["10/163/360", "15/5303/11553"]) {
    const response = await call(`/${reference.REFERENCE_ARCHIVE}/${path}.mvt`);
    assert.equal(response.status, 404, path);
  }
  assert.deepEqual({ fetchCalls, bucketPuts }, { fetchCalls: [], bucketPuts: [] });
});

// --- The official-trails manifest (dispatch 2026-10-26-22 M1; the switch report section 5): the archive's dates, sources
// and coverage, which the generic /reference-trails.json drops (pmtiles' getTileJson keeps no custom metadata). Served
// from R2's reference-trails-manifest.json as its bytes, never through the tileset path.

const MANIFEST_PATH = "/reference-trails-manifest.json";

test("manifest: served from R2's reference-trails-manifest.json byte for byte, as JSON, cached a day like the other JSON routes", async () => {
  const response = await call(MANIFEST_PATH);
  assert.equal(response.status, 200);
  assert.deepEqual(
    {
      type: response.headers.get("Content-Type"),
      cache: response.headers.get("Cache-Control"),
      origin: response.headers.get("Access-Control-Allow-Origin"),
      body: Buffer.from(await response.arrayBuffer()).equals(MANIFEST),
      reads: bucketReads,
    },
    { type: "application/json", cache: "public, max-age=86400", origin: "*", body: true, reads: ["reference-trails-manifest.json"] },
  );
  assert.deepEqual(fetchCalls, []);
});

test("manifest: a second request is answered from the edge cache, with no R2 read", async () => {
  await call(MANIFEST_PATH);
  bucketReads = [];
  const again = await call(MANIFEST_PATH);
  assert.equal(again.status, 200);
  assert.ok(Buffer.from(await again.arrayBuffer()).equals(MANIFEST));
  assert.deepEqual(bucketReads, []);
});

test("manifest: not on R2 is a 404 that is not cached, and never a read of a .pmtiles archive", async () => {
  assets.delete("reference-trails-manifest.json");
  const response = await call(MANIFEST_PATH);
  assert.equal(response.status, 404);
  assert.deepEqual(bucketReads, ["reference-trails-manifest.json"]);
  // Uploaded later: the next request finds it, because the 404 was not kept.
  assets.set("reference-trails-manifest.json", MANIFEST);
  const later = await call(MANIFEST_PATH);
  assert.equal(later.status, 200);
  assert.ok(Buffer.from(await later.arrayBuffer()).equals(MANIFEST));
});
