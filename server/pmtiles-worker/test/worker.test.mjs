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
const OFFLINE_STYLE_SOURCE = fs.readFileSync(here("../src/offline-style.json"), "utf8");
const STREET_STYLE_SOURCE = fs.readFileSync(here("../src/street-style-v1.json"), "utf8");
const HOST = "https://forager-pmtiles.brandonlee1-894.workers.dev";

let fetchCalls;
let bucketReads;
let assets;

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

const bucket = {
  async get(key, options) {
    bucketReads.push(key);
    if (key === "us.pmtiles") {
      const { offset, length } = options.range;
      const slice = ARCHIVE.subarray(offset, offset + length);
      return { body: true, etag: "fixture", httpMetadata: {}, arrayBuffer: async () => slice.buffer.slice(slice.byteOffset, slice.byteOffset + slice.byteLength) };
    }
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
