// Route tests for the Worker (dispatch 2026-10-18-05 Part 2), run against dist/index.js, the bundled file the
// owner pastes into the dashboard, not the TypeScript source: what is tested is what ships.
//
//   npm run build && npm test
//
// R2 is a fake backed by test/fixtures/tiny.pmtiles and an in-memory asset map; the edge cache is a Map;
// fetch is a recorder, so "no Pi request" is asserted as "fetch was never called" for tiles R2 holds.
// The Worker is given PI_ORIGIN_URL and both Access secrets, exactly as the deployed Worker has them, so the
// assertion holds even with the Pi configured.
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { gunzipSync } from "node:zlib";

const here = (p) => new URL(p, import.meta.url);
const ARCHIVE = fs.readFileSync(here("./fixtures/tiny.pmtiles"));
// The world fill (dispatch 2026-10-22-01 Part A): R2's world-south.pmtiles, read where us.pmtiles has no tile.
const WORLD = fs.readFileSync(here("./fixtures/world-south.pmtiles"));
const OFFLINE_STYLE_SOURCE = fs.readFileSync(here("../src/offline-style.json"), "utf8");
const STREET_STYLE_SOURCE = fs.readFileSync(here("../src/street-style-v1.json"), "utf8");
const HOST = "https://forager-pmtiles.brandonlee1-894.workers.dev";

let fetchCalls;
let bucketReads;
let assets;
let worldOnR2;

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
    if (assets.has(key)) {
      const bytes = assets.get(key);
      return { body: true, etag: "asset", httpMetadata: {}, arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) };
    }
    return null;
  },
  async put() {},
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
    // Serve the fixture as the "live build", with Range support, as FetchSource expects.
    const range = new Headers(init?.headers).get("range") ?? new Headers(input.headers ?? {}).get("range");
    const [, start, end] = /bytes=(\d+)-(\d+)/.exec(range);
    const slice = ARCHIVE.subarray(Number(start), Number(end) + 1);
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
  globalThis.caches.default = new FakeCache();
  assets = new Map([
    ["fonts/Noto Sans Regular/0-255.pbf", Buffer.from("glyphs 0-255")],
    ["sprites/v4/light.json", Buffer.from('{"townspot":{}}')],
    ["sprites/v4/light@2x.png", Buffer.from([0x89, 0x50, 0x4e, 0x47])],
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
  // The fixture's "live build" stops at z14, so the overflow path answers 404 after reading its header.
  assert.equal(response.status, 404);
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

test("a missing font range is a 404 and is not cached", async () => {
  const response = await call("/fonts/Noto%20Sans%20Regular/256-511.pbf");
  assert.equal(response.status, 404);
  assets.set("fonts/Noto Sans Regular/256-511.pbf", Buffer.from("later"));
  const again = await call("/fonts/Noto%20Sans%20Regular/256-511.pbf");
  assert.equal(again.status, 200);
});

test("other paths under /fonts/ and /sprites/ never read R2", async () => {
  for (const path of ["/fonts/../us.pmtiles", "/fonts/Noto%20Sans%20Regular/x.pbf", "/sprites/v4/dark.json", "/sprites/us.pmtiles"]) {
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
