// The two-square proof (dispatch 2026-10-23-01): an area across a five-degree line, downloaded the way the app
// will download it, through the route-data handler (src/routing/handler.ts), routes in BRouter v1.7.10 exactly
// as on the two full squares. The fixture is test/fixtures/rd5/two-squares/ (README.md there): W110_N35 and
// W110_N40, which meet at 40 N west of Boulder, Colorado.
//
// The download: GET /routing/area.json for the rectangle, then GET each file's url, with the app key, against a
// fake R2 holding a manifest and the two full squares. The files are written under the names the answer gives,
// into one folder, which BRouter is pointed at.
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { routingResponse, APP_KEY_HEADER } from "../../src/routing/handler.ts";
import { route, points } from "./brouter-cli.mjs";
import { TEST_APP_KEY, FakeCache, fakeBucket, fakeLimiter, manifestFor } from "./routing-fakes.mjs";

const here = (p) => new URL(p, import.meta.url);
const FIXTURE_DIR = path.dirname(here("../fixtures/rd5/two-squares/routes.json").pathname);
const ROUTES = JSON.parse(fs.readFileSync(here("../fixtures/rd5/two-squares/routes.json"), "utf8"));
const RECT = ROUTES.rectangle;
const SQUARES = Object.fromEntries(ROUTES.squares.map((s) => [s, fs.readFileSync(path.join(FIXTURE_DIR, `${s}.rd5`))]));
const RUN = "usca-20261021T111429Z";
const HOST = "https://forager-pmtiles.example.workers.dev";
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

function env() {
  const objects = { "routing/manifest.json": manifestFor(RUN, SQUARES) };
  for (const [name, bytes] of Object.entries(SQUARES)) objects[`routing/${RUN}/segments4/${name}.rd5`] = bytes;
  return { BUCKET: fakeBucket(objects), ROUTING_APP_KEY: TEST_APP_KEY, ROUTING_IP_LIMITER: fakeLimiter(), ROUTING_KEY_LIMITER: fakeLimiter() };
}

const get = (e, cache, p) =>
  routingResponse(new Request(`${HOST}${p}`, { headers: { [APP_KEY_HEADER]: TEST_APP_KEY, "CF-Connecting-IP": "192.0.2.1" } }), e, cache);

// The app's download, once: the area answer and each file, written into a fresh folder.
let downloadPromise;
const download = () =>
  (downloadPromise ??= (async () => {
    const e = env();
    const cache = new FakeCache();
    const q = `south=${RECT.south}&west=${RECT.west}&north=${RECT.north}&east=${RECT.east}`;
    const areaResponse = await get(e, cache, `/routing/area.json?${q}`);
    const area = await areaResponse.json();
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "rd5-two-"));
    const files = {};
    for (const f of area.files ?? []) {
      const response = await get(e, cache, f.url);
      const bytes = Buffer.from(await response.arrayBuffer());
      files[f.name] = { status: response.status, bytes, header: response.headers.get("X-Forager-Sha256") };
      fs.writeFileSync(path.join(dir, f.name), bytes);
    }
    return { status: areaResponse.status, area, files, dir };
  })());

after(async () => {
  const d = downloadPromise ? await downloadPromise.catch(() => null) : null;
  if (d) fs.rmSync(d.dir, { recursive: true, force: true });
});

const inRect = ([lat, lon]) => lat >= RECT.south && lat <= RECT.north && lon >= RECT.west && lon <= RECT.east;
const crosses40 = (pts) => pts.slice(1).some((p, i) => (p[0] - 40) * (pts[i][0] - 40) < 0);
const fullRuns = new Map();
const full = (r) => {
  if (!fullRuns.has(r.name)) fullRuns.set(r.name, route(FIXTURE_DIR, r.from, r.to));
  return fullRuns.get(r.name);
};

// Not a test of the Worker: it checks the fixture still exercises what the proof claims.
test("on the full squares, every route crosses 40 N and stays inside the rectangle", () => {
  assert.equal(ROUTES.routes.length, 4);
  for (const r of ROUTES.routes) {
    const { feature, message } = full(r);
    assert.ok(feature, `${r.name}: the full squares route it (${message})`);
    const pts = points(feature);
    assert.ok(pts.every(inRect), `${r.name} stays inside the rectangle`);
    assert.ok(crosses40(pts), `${r.name} crosses 40 N`);
  }
});

test("the area answer names both squares, by BRouter's file names, with the run id, sizes and checksums of what is served", async () => {
  const { status, area, files } = await download();
  assert.equal(status, 200, JSON.stringify(area));
  assert.equal(area.run_id, RUN);
  assert.equal(area.osm_timestamp, "2026-10-17T20:20:51Z");
  assert.deepEqual(area.files.map((f) => f.name), ["W110_N35.rd5", "W110_N40.rd5"]);
  for (const f of area.files) {
    assert.match(f.url, new RegExp(`^/routing/${RUN}/${f.name.replace(".", "\\.")}\\?south=`));
    assert.equal(files[f.name].status, 200);
    assert.equal(files[f.name].bytes.byteLength, f.size, `${f.name}: size`);
    assert.equal(sha256(files[f.name].bytes), f.sha256, `${f.name}: sha256 of the bytes served`);
    assert.equal(files[f.name].header, f.sha256, `${f.name}: X-Forager-Sha256`);
  }
});

// Without this, "the same route" could hold because the cut kept everything (the README's earlier extract did).
test("each served file is a real cut: smaller than its full square", async () => {
  const { files } = await download();
  for (const [name, full] of Object.entries(SQUARES)) {
    const served = files[`${name}.rd5`].bytes;
    assert.ok(served.byteLength < full.byteLength, `${name}: ${served.byteLength} bytes served of ${full.byteLength}`);
  }
});

for (const r of ROUTES.routes) {
  test(`route ${r.name} across 40 N is identical on the downloaded cuts: same points, length and cost`, async () => {
    const { dir } = await download();
    const cut = route(dir, r.from, r.to);
    assert.ok(cut.feature, `the cuts route it (${cut.message})`);
    assert.deepEqual(cut.feature, full(r).feature);
  });
}

// The control: with only the southern cut, the same routes cannot be the same, so the test above depends on the
// second square actually being served.
test("with the northern cut left out, no route across 40 N comes out the same", async () => {
  const { dir } = await download();
  const southOnly = fs.mkdtempSync(path.join(os.tmpdir(), "rd5-south-"));
  try {
    fs.copyFileSync(path.join(dir, "W110_N35.rd5"), path.join(southOnly, "W110_N35.rd5"));
    for (const r of ROUTES.routes) {
      const cut = route(southOnly, r.from, r.to);
      assert.notDeepEqual(cut.feature, full(r).feature, `${r.name} (${cut.message})`);
    }
  } finally {
    fs.rmSync(southOnly, { recursive: true, force: true });
  }
});
