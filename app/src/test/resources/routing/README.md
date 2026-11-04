# Trail router test data

For `BRouterTrailRouterTest` (dispatch 2026-10-23-02), and for `BRouterHeightedRouteTest` and `FindWayHomeHeightsOnBRouterTest` on the square with heights (dispatch 2026-10-30-06, Amendment 3).

## Data licence

`segments/W75_N40.rd5`, `segments-heighted/W75_N40.rd5`, `brouter-cli/*.geojson` and `brouter-cli/heighted/*.geojson` contain OpenStreetMap data, © OpenStreetMap contributors, available under the Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/1-0/. See https://www.openstreetmap.org/copyright.

Elevation: U.S. Geological Survey, 3D Elevation Program (3DEP), public domain. This is the credit for the heights in `segments-heighted/W75_N40.rd5` and `brouter-cli/heighted/*.geojson`, as Aspen gave it (prompts/aspen/2026-10-31-01).

## Files

- `segments/W75_N40.rd5`: a copy of the route cutter's first fixture, `server/pmtiles-worker/test/fixtures/rd5/W75_N40.rd5` as it stood from 2026-10-22 until #452 replaced it (152,665 bytes, SHA-256 `ff6d9d8e439c3cdf914b1566619f64466d9af993c28070ea893425e7f5ec5990`), with no heights. Its README there says where it came from: part of Harriman and Bear Mountain State Parks, New York, built by BRouter 1.7.10's map creator. Copied rather than read from `server/`, so the app's tests don't depend on the Worker's tree.
- `brouter-cli/*.geojson`: what BRouter v1.7.10's own command-line router (`btools.server.BRouter`, the release jar, checked against its pin) returned for two of that fixture's routes, `inside-across-the-degree-line` and `inside-long`, on `segments/` with `hiking-mountain` and dynamic range off. Written by `make-expected.sh`; a rerun gives the same bytes, because the data is fixed.
- `segments-heighted/W75_N40.rd5`: a copy of the same Worker fixture as it stands since #452 (dispatch 2026-10-30-coder-03), the same box with 3″ heights (178,867 bytes, SHA-256 `14f595e8469455bcdcc600239102cb7d60641185759fcae29cfed444af8a965c`); its README there gives the recipe. In a folder of its own, so `segments/` keeps the square without heights and the test that every point's height is missing on it.
- `brouter-cli/heighted/*.geojson`: what the same command-line router returned on `segments-heighted/` for all eight routes in the Worker fixture's `routes.json`, the same query as above. Also written by `make-expected.sh`. Over the eight routes: 3,630 points, every one with a height, 5.75 m to 413.75 m (matching Aspen's count). The two routes also in `brouter-cli/` have the same points on both squares.
