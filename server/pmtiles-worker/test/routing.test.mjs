// The route-data routes (dispatch 2026-10-23-01), through the Worker's own fetch handler in dist/index.js, the
// bundle that ships, as worker.test.mjs does for the tile routes.
//
//   npm run build && node --test test/routing.test.mjs
//
// R2 is a fake holding routing/manifest.json and the single-square cutter fixture (test/fixtures/rd5/W75_N40.rd5)
// under the run's key; the edge cache is a Map; both rate limiters are fakes that allow a set number of calls.
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import fs from "node:fs";

import { TEST_APP_KEY, FakeCache, fakeBucket, fakeLimiter, manifestFor } from "./rd5/routing-fakes.mjs";

const here = (p) => new URL(p, import.meta.url);
const SQUARE = fs.readFileSync(here("./fixtures/rd5/W75_N40.rd5"));
const RECT = JSON.parse(fs.readFileSync(here("./fixtures/rd5/routes.json"), "utf8")).rectangle;
const RUN = "usca-20261021T111429Z";
const NEXT_RUN = "usca-20261025T090000Z";
const HOST = "https://forager-pmtiles.brandonlee1-894.workers.dev";
const AREA = `/routing/area.json?south=${RECT.south}&west=${RECT.west}&north=${RECT.north}&east=${RECT.east}`;
const KEY_HEADER = "X-Forager-App-Key";
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

globalThis.fetch = async () => new Response("unexpected fetch", { status: 599 });
globalThis.caches = { default: new FakeCache() };
const { default: worker } = await import("../dist/index.js");

let env;
let pending;
const ctx = { waitUntil: (p) => pending.push(p) };

function objectsFor(runId, square = SQUARE) {
  return {
    "routing/manifest.json": manifestFor(runId, { W75_N40: square }),
    [`routing/${runId}/segments4/W75_N40.rd5`]: square,
  };
}

beforeEach(() => {
  pending = [];
  globalThis.caches.default = new FakeCache();
  env = {
    BUCKET: fakeBucket(objectsFor(RUN)),
    ALLOWED_ORIGINS: "*",
    ROUTING_APP_KEY: TEST_APP_KEY,
    ROUTING_IP_LIMITER: fakeLimiter(),
    ROUTING_KEY_LIMITER: fakeLimiter(),
  };
});

async function call(path, { key = TEST_APP_KEY, ip = "192.0.2.7" } = {}) {
  const headers = { "CF-Connecting-IP": ip };
  if (key !== null) headers[KEY_HEADER] = key;
  const response = await worker.fetch(new Request(`${HOST}${path}`, { headers }), env, ctx);
  await Promise.all(pending);
  return response;
}

const cachedUrls = () => [...globalThis.caches.default.entries.keys()];
const squareReads = () => env.BUCKET.reads.filter(([key]) => key.endsWith(".rd5"));

async function assertError(response, status, error) {
  assert.equal(response.status, status);
  assert.equal(response.headers.get("Content-Type"), "application/json");
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  const body = await response.json();
  assert.equal(body.error, error, JSON.stringify(body));
  return body;
}

test("an area answer: one cut file per square, BRouter's name, its size and sha256, and the run id", async () => {
  const response = await call(AREA);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(Object.keys(body), ["run_id", "osm_timestamp", "files", "missing"]);
  assert.deepEqual(body.missing, [], "every square the area needs is in the build");
  assert.equal(body.run_id, RUN);
  assert.equal(body.osm_timestamp, "2026-10-17T20:20:51Z");
  assert.equal(body.files.length, 1);
  const [f] = body.files;
  assert.equal(f.name, "W75_N40.rd5");
  assert.equal(f.url, `/routing/${RUN}/W75_N40.rd5?south=${RECT.south}&west=${RECT.west}&north=${RECT.north}&east=${RECT.east}`);

  const file = await call(f.url);
  assert.equal(file.status, 200);
  assert.equal(file.headers.get("Content-Type"), "application/octet-stream");
  const bytes = Buffer.from(await file.arrayBuffer());
  assert.equal(bytes.byteLength, f.size);
  assert.equal(sha256(bytes), f.sha256);
  assert.equal(file.headers.get("X-Forager-Sha256"), f.sha256);
  assert.ok(bytes.byteLength < SQUARE.byteLength, "a cut, not the whole square");
  // The cut's header is the square's own format: 200 bytes of tile ends, ending at the footer.
  assert.equal(bytes.readUInt32BE(24 * 8 + 4) + 113, bytes.byteLength, "tile 24 ends where the 113-byte footer starts");
});

test("no app key: 403 forbidden, not cached, and R2 is never read", async () => {
  const response = await call(AREA, { key: null });
  const body = await assertError(response, 403, "forbidden");
  assert.deepEqual(body, { error: "forbidden" });
  assert.deepEqual(env.BUCKET.reads, []);
  assert.deepEqual(cachedUrls(), []);
});

test("a wrong app key, on either route: 403 forbidden, even for a file the edge has cached", async () => {
  const area = await (await call(AREA)).json();
  assert.equal(cachedUrls().length, 1, "the right key's answer cached one cut");
  await assertError(await call(AREA, { key: "guess" }), 403, "forbidden");
  await assertError(await call(area.files[0].url, { key: "guess" }), 403, "forbidden");
  await assertError(await call(area.files[0].url, { key: "" }), 403, "forbidden");
});

test("past the per-address limit: 429 rate_limited with Retry-After, not cached, and the key is not even checked", async () => {
  env.ROUTING_IP_LIMITER = fakeLimiter(2);
  assert.equal((await call(AREA)).status, 200);
  assert.equal((await call(AREA, { key: "guess" })).status, 403, "a wrong key still counts against the address");
  const before = cachedUrls().length;
  const response = await call(AREA);
  await assertError(response, 429, "rate_limited");
  assert.equal(response.headers.get("Retry-After"), "60");
  assert.equal(cachedUrls().length, before, "nothing cached by the refusal");
  assert.deepEqual(env.ROUTING_IP_LIMITER.keys, ["ip:192.0.2.7", "ip:192.0.2.7", "ip:192.0.2.7"]);
  assert.equal((await call(AREA, { ip: "198.51.100.4" })).status, 200, "another address is not limited");
});

test("past the app-key limit: 429 rate_limited with Retry-After", async () => {
  env.ROUTING_KEY_LIMITER = fakeLimiter(1);
  assert.equal((await call(AREA)).status, 200);
  const response = await call(AREA, { ip: "198.51.100.4" });
  await assertError(response, 429, "rate_limited");
  assert.equal(response.headers.get("Retry-After"), "60");
});

test("a Worker deployed without the key secret or a limiter refuses with 503 not_configured, never serves open", async () => {
  for (const missing of ["ROUTING_APP_KEY", "ROUTING_IP_LIMITER", "ROUTING_KEY_LIMITER"]) {
    const saved = env[missing];
    delete env[missing];
    await assertError(await call(AREA, { key: null }), 503, "not_configured");
    env[missing] = saved;
  }
  assert.deepEqual(env.BUCKET.reads, []);
});

test("no manifest on R2: 503 manifest_missing", async () => {
  env.BUCKET = fakeBucket({});
  await assertError(await call(AREA), 503, "manifest_missing");
});

// The owner, on #381: "Serve what exists (Recommended)": "The area gets routes for the squares that exist; the
// missing part is named and logged. Coastal areas still get trails."
test("an area partly outside the build: 200 with the squares that exist, the missing ones named and logged", async () => {
  const warnings = [];
  const warn = console.warn;
  console.warn = (...args) => warnings.push(args.join(" "));
  try {
    // Across 75 W: W75_N40 is in the build, W80_N40 is not.
    const response = await call("/routing/area.json?south=41.2&west=-75.05&north=41.3&east=-74.95");
    assert.equal(response.status, 200, await response.clone().text());
    const body = await response.json();
    assert.deepEqual(body.files.map((f) => f.name), ["W75_N40.rd5"]);
    assert.deepEqual(body.missing, ["W80_N40"]);
    const file = await call(body.files[0].url);
    assert.equal(file.status, 200);
    assert.equal(sha256(Buffer.from(await file.arrayBuffer())), body.files[0].sha256);
    assert.ok(warnings.some((w) => w.includes("W80_N40")), `logged: ${JSON.stringify(warnings)}`);
  } finally {
    console.warn = warn;
  }
});

test("an area the build does not cover at all: 404 outside_coverage, naming the squares", async () => {
  const body = await assertError(await call("/routing/area.json?south=45.5&west=-122.7&north=45.6&east=-122.6"), 404, "outside_coverage");
  assert.deepEqual(body.squares, ["W125_N45"]);
});

test("an area over the limit is refused whole: 413 too_large, nothing cut", async () => {
  // About 56 km north to south: just over MAX_ROUTE_AREA_SIDE_KM (50).
  const body = await assertError(await call("/routing/area.json?south=41.0&west=-74.1&north=41.5&east=-74.0"), 413, "too_large");
  assert.match(body.message, /limit is 50 km/);
  assert.deepEqual(squareReads(), []);
  // A 48 x 48 km box at 41.3 N, the app's largest area, is accepted.
  const dLat = 24 / 111.195;
  const dLon = 24 / (111.195 * Math.cos((41.3 * Math.PI) / 180));
  const ok = await call(`/routing/area.json?south=${(41.3 - dLat).toFixed(6)}&west=${(-74.3 - dLon).toFixed(6)}&north=${(41.3 + dLat).toFixed(6)}&east=${(-74.3 + dLon).toFixed(6)}`);
  assert.equal(ok.status, 200, await ok.clone().text());
});

test("a malformed or inverted rectangle: 400 bad_request", async () => {
  for (const q of ["south=41&west=-74&north=40&east=-73", "south=41&west=-74&north=41.1", "south=41&west=x&north=41.1&east=-73.9"]) {
    await assertError(await call(`/routing/area.json?${q}`), 400, "bad_request");
  }
});

test("a square whose header fails its checksum: 502 checksum", async () => {
  const damaged = Buffer.from(SQUARE);
  damaged[7] ^= 0xff; // a byte of tile 0's end offset
  env.BUCKET = fakeBucket(objectsFor(RUN, damaged));
  await assertError(await call(AREA), 502, "checksum");
});

test("a square on R2 whose size differs from the manifest's: 502 checksum", async () => {
  const objects = objectsFor(RUN);
  objects[`routing/${RUN}/segments4/W75_N40.rd5`] = Buffer.concat([SQUARE, Buffer.from([0])]);
  env.BUCKET = fakeBucket(objects);
  await assertError(await call(AREA), 502, "checksum");
});

test("a square the manifest names but R2 lacks: 503 run_missing_square", async () => {
  const objects = objectsFor(RUN);
  delete objects[`routing/${RUN}/segments4/W75_N40.rd5`];
  env.BUCKET = fakeBucket(objects);
  await assertError(await call(AREA), 503, "run_missing_square");
});

test("cuts are cached by URL with the run id in it: a repeat is not cut again, and a new run never gets an old cut", async () => {
  const first = await (await call(AREA)).json();
  const readsAfterFirst = squareReads().length;
  assert.ok(readsAfterFirst > 0);
  await call(AREA);
  await call(first.files[0].url);
  assert.equal(squareReads().length, readsAfterFirst, "the repeat area and the file came from the edge cache");

  env.BUCKET = fakeBucket(objectsFor(NEXT_RUN));
  const second = await (await call(AREA)).json();
  assert.equal(second.run_id, NEXT_RUN);
  assert.equal(second.files[0].url, first.files[0].url.replace(RUN, NEXT_RUN));
  assert.ok(squareReads().length > 0, "the new run's square was read, not the old run's cut");
  assert.ok(squareReads().every(([key]) => key.includes(NEXT_RUN)));
});

test("a file URL from a replaced run, not in the edge cache: 409 run_superseded, naming the current run", async () => {
  const url = `/routing/usca-20261001T000000Z/W75_N40.rd5?south=${RECT.south}&west=${RECT.west}&north=${RECT.north}&east=${RECT.east}`;
  const body = await assertError(await call(url), 409, "run_superseded");
  assert.equal(body.run_id, RUN);
});

test("any other path under /routing/ is 404 not_found, after the key check", async () => {
  await assertError(await call("/routing/manifest.json"), 404, "not_found");
  await assertError(await call("/routing/manifest.json", { key: null }), 403, "forbidden");
});
