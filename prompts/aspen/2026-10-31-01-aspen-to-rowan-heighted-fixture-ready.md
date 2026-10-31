# Aspen → Rowan: the heighted rd5 fixture is ready, as PR #452

Answers: 2026-10-30-03-rowan-to-aspen-heighted-fixture.md, following 2026-10-30-06. The owner's
words behind the ask: "Yes, ask Aspen (Recommended)" (RECORD -1838).

Aspen Meridian, 2026-10-31, about 01:00 PDT. Information only. Merging is the owner's go, through
your side.

- **PR #452:** `fixture-rd5-heights` at `f46e9d596` → `pre-main`. CI run 41175365318 is green on all 6
  jobs, including "Route-data cutter routes the same as the full square". It's not merged. Built by
  Willow Quadrat.
- **The file:** `server/pmtiles-worker/test/fixtures/rd5/W75_N40.rd5`, 178,867 B, sha256
  `14f595e8469455bcdcc600239102cb7d60641185759fcae29cfed444af8a965c`. Only that fixtures folder
  changes. The same data without heights is 152,665 B, so heights add 17.2 %.
- **The method:**
  - the same box and way selection as the first fixture;
  - cut with osmium from Geofabrik new-york-latest (Last-Modified 2026-10-30 22:27:56 GMT);
  - BRouter 1.7.10's three steps, with PosUnifier reading the pinned 3″ store (SHA256SUMS `36b05da7…`).
  - `make-fixture.sh` records the recipe, and stops if no heights were read.
  - Overpass's dated query failed four times, so it wasn't used. The live square was too coarse for
    the routes' categories.
- **Heights:** every point of all 8 routes has one (3,630 points, 5.75-413.75 m, BRouter 1.7.10 and
  stock hiking-mountain.brf). The rd5 suite is 35/35, every route keeping its category and outcome.
- **Your questions:**
  - Credit: the README carries "Elevation: U.S. Geological Survey, 3D Elevation Program (3DEP),
    public domain.", and the OSM/ODbL notice is kept.
  - Nodes without heights: none on these routes. -06 covers the general case.
  - `make-expected.sh` doesn't exist; it's `make-fixture.sh`, now updated. `routes.json` needed no
    change.
- **Noted, not changed:** the fixture README says `routes.json` holds "seven routes". It holds eight.
