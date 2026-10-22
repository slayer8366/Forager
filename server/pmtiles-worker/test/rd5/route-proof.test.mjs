// The proof (dispatch 2026-10-21-07; owner's go and ruling in RECORD -1546): BRouter v1.7.10 routes the same
// on a cut as on the square it was cut from, for routes inside the rectangle; and what it does with routes
// that leave the cut, asserted as BRouter actually behaves, both with its default profile and with dynamic
// range matching off.
//
// Route ends and categories: test/fixtures/rd5/routes.json and README.md. Every run here is the real router
// in a separate JVM (brouter-cli.mjs).
import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

import { route, points, metres } from "./brouter-cli.mjs";
import { FIXTURE_DIR, ROUTES, cutToDir } from "./fixture.mjs";
import { globalCol, globalRow } from "./rd5-reader.mjs";

const CELL = 1 / 32;
const RECT = ROUTES.rectangle;

// The area the cut's data covers: the rectangle's cells grown by `margin` cells, in degrees.
function cellBox(margin) {
  return {
    south: (globalRow(RECT.south) - margin) * CELL - 90,
    north: (globalRow(RECT.north) + margin + 1) * CELL - 90,
    west: (globalCol(RECT.west) - margin) * CELL - 180,
    east: (globalCol(RECT.east) + margin + 1) * CELL - 180,
  };
}
const within = (box) => ([lat, lon]) => lat >= box.south && lat <= box.north && lon >= box.west && lon <= box.east;
const inRect = within(RECT);
const inCells0 = within(cellBox(0));
const inCut = within(cellBox(1));

const DYNAMIC_RANGE_OFF = { use_dynamic_range: 0 };
const byCategory = (c) => ROUTES.routes.filter((r) => r.category === c);
const end = (p) => [p.lat, p.lon];

// Cut once, on first use, so the fixture check below runs without the cutter.
let cutDirPromise;
const cutDir = () => (cutDirPromise ??= cutToDir().then((r) => r.dir));
const fullRuns = new Map();
const full = (r, params = {}) => {
  const key = `${r.name}|${JSON.stringify(params)}`;
  if (!fullRuns.has(key)) fullRuns.set(key, route(FIXTURE_DIR, r.from, r.to, params));
  return fullRuns.get(key);
};
const onCut = async (r, params = {}) => route(await cutDir(), r.from, r.to, params);

after(async () => {
  const dir = cutDirPromise ? await cutDirPromise.catch(() => null) : null; // a failed cut is reported by its tests
  if (dir) fs.rmSync(dir, { recursive: true, force: true });
});

// Not a test of the cutter: it checks that the fixture still exercises what each category claims, so a
// change of fixture cannot quietly turn an edge route into an inside one.
test("on the full fixture, every route is found and lies where its category says", () => {
  for (const r of ROUTES.routes) {
    const { feature, message } = full(r);
    assert.ok(feature, `${r.name}: the full square routes it (${message})`);
    const pts = points(feature);
    switch (r.category) {
      case "inside":
        assert.ok(pts.every(inRect), `${r.name} stays inside the rectangle`);
        break;
      case "edge":
        assert.ok([end(r.from), end(r.to)].every(inRect), `${r.name} starts and ends in the rectangle`);
        assert.ok(!pts.every(inCells0), `${r.name} leaves the rectangle's own cells`);
        assert.ok(pts.every(inCut), `${r.name} stays within the one-cell margin`);
        break;
      case "detour":
        assert.ok([end(r.from), end(r.to)].every(inCut), `${r.name} starts and ends in the cut's data`);
        assert.ok(!pts.every(inCut), `${r.name}'s best path leaves the cut's data`);
        break;
      case "outside":
        assert.ok(inRect(end(r.from)) && !inCut(end(r.to)), `${r.name} ends outside the cut's data`);
        break;
      default:
        assert.fail(`unknown category ${r.category}`);
    }
  }
});

for (const r of [...byCategory("inside"), ...byCategory("edge")]) {
  test(`${r.category} route ${r.name} is identical on the cut: same points, length and cost`, async () => {
    const cut = await onCut(r);
    assert.ok(cut.feature, `the cut routes it (${cut.message})`);
    assert.deepEqual(cut.feature, full(r).feature);
  });

  test(`${r.category} route ${r.name} is identical on the cut with dynamic range off`, async () => {
    const cut = await onCut(r, DYNAMIC_RANGE_OFF);
    assert.ok(cut.feature, `the cut routes it (${cut.message})`);
    assert.deepEqual(cut.feature, full(r, DYNAMIC_RANGE_OFF).feature);
  });
}

for (const r of byCategory("detour")) {
  test(`detour route ${r.name}: by default BRouter quietly routes a longer way inside the cut`, async (t) => {
    const fullRun = full(r);
    const cut = await onCut(r);
    assert.ok(cut.feature, `the cut returns a route, no error (${cut.message})`);
    const fullLength = Number(fullRun.feature.properties["track-length"]);
    const cutLength = Number(cut.feature.properties["track-length"]);
    t.diagnostic(`full square ${fullLength} m; cut ${cutLength} m`);
    assert.ok(cutLength > fullLength, "the cut's route is longer than the best one");
    assert.ok(points(cut.feature).every(inCut), "and it stays inside the cut's data");
  });

  test(`detour route ${r.name}: dynamic range off does not change that; the detour is still returned`, async () => {
    const cut = await onCut(r, DYNAMIC_RANGE_OFF);
    assert.ok(cut.feature, `the cut returns a route (${cut.message})`);
    assert.deepEqual(cut.feature, (await onCut(r)).feature);
  });
}

for (const r of byCategory("outside")) {
  test(`outside route ${r.name}: by default BRouter moves the far end onto the cut's data and routes`, async (t) => {
    const fullEnd = points(full(r).feature).at(-1);
    const cut = await onCut(r);
    assert.ok(cut.feature, `the cut returns a route, no error (${cut.message})`);
    const cutEnd = points(cut.feature).at(-1);
    t.diagnostic(`requested end to track end: full ${metres(end(r.to), fullEnd).toFixed(0)} m, ` +
      `cut ${metres(end(r.to), cutEnd).toFixed(0)} m`);
    assert.ok(metres(end(r.to), fullEnd) < 250, "on the full square the track ends at the requested point");
    assert.ok(metres(end(r.to), cutEnd) > 250, "on the cut it ends somewhere else");
    assert.ok(inCut(cutEnd), "inside the cut's data");
  });

  test(`outside route ${r.name}: with dynamic range off the cut refuses it cleanly`, async () => {
    assert.ok(full(r, DYNAMIC_RANGE_OFF).feature, "the full square still routes it with dynamic range off");
    const cut = await onCut(r, DYNAMIC_RANGE_OFF);
    assert.equal(cut.feature, null, "no track");
    assert.equal(cut.message, "to-position not mapped in existing datafile");
  });
}
