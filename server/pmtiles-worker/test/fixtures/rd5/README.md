# Route-data cutter fixture

A small BRouter routing square and the routes the cutter's proof runs on it (dispatch 2026-10-21-07, RECORD -1546). The tests are in `../../rd5/`.

## Data licence

`W75_N40.rd5` and `routes.json` contain OpenStreetMap data, © OpenStreetMap contributors. The data is available under the Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/1-0/. See https://www.openstreetmap.org/copyright.

`W75_N40.rd5` is a derivative database of OpenStreetMap, and it is made available here under the same licence. It was produced with BRouter 1.7.10's map creator (MIT licence, Copyright (c) 2019 BRouter contributors). BRouter is downloaded at test time and is not part of this repository.

Elevation: U.S. Geological Survey, 3D Elevation Program (3DEP), public domain. The square's heights are 3DEP 1″ averaged to 3″ (the Pi's elevation store, see Files); the rectangle is in the United States, so no Canadian data is involved.

## The place, and why

The fixture covers part of **Harriman and Bear Mountain State Parks, New York**: 41.22 to 41.36 N, 74.13 to 73.96 W, about 15.6 by 14.2 km.

- **Public land with a dense trail network.** Both parks are run by the New York State Office of Parks, Recreation and Historic Preservation. Every route end in `routes.json` is an OpenStreetMap trail node (`highway=path`, `footway` or `track`) inside one of the two parks. This was checked with OpenStreetMap's `is_in` on 2026-10-22: each of the 14 ends lies in relation 6467468 (Bear Mountain) or 4080499 (Harriman).
- **It crosses a one-degree line but not a five-degree line.** The 74 W meridian runs through Bear Mountain, so the cut rectangle (41.27 to 41.31 N, 74.05 to 73.99 W) spans two one-degree tiles of one square: tiles 1 and 6 of `W75_N40`. The nearest five-degree lines are 75 W, 70 W, 40 N and 45 N, all well away, so one file holds everything.
- **Outside the Pacific Northwest,** as the planner asked, so the fixture hints at nobody's region. Nobody's recorded positions are in it, only OpenStreetMap data.

## Files

- `W75_N40.rd5`: 178,867 bytes, SHA-256 `14f595e8469455bcdcc600239102cb7d60641185759fcae29cfed444af8a965c`. A sparse square: data only in tiles 1 and 6, the rest empty, as BRouter's format allows. **It carries heights**, like the Pi's squares since the elevation run: every point of every route in `routes.json` has one, 5.75 m to 413.75 m over 3,630 points (BRouter 1.7.10, `hiking-mountain.brf`, 2026-10-30). Made by `make-fixture.sh` (dispatch 2026-10-30-coder-03):
  - **OpenStreetMap:** Geofabrik's `new-york-latest.osm.pbf`, Last-Modified 2026-10-30 22:27:56 GMT, 498,367,964 bytes, SHA-256 `4151ed5a4d0074a1b7fe57af71d76c4ec2e35b6522028022a598a969c68e129e`, MD5 checked. The box was cut with osmium 1.15 (`harriman.osm.pbf`, SHA-256 `fcb398554e27d0d297f6bf1305ab95e7041f292ec151c8c68bce963ed54d29fe`).
  - **Heights:** PosUnifier given the Pi's pinned 3″ store, `/mnt/archive/forager-build/elevation/bef3-20261027` (its `SHA256SUMS` SHA-256 `36b05da7cee10340f8ccec1249255d97e7b096564323c71f7101fee443102292`, map-build `USCA_BEF_SUMS_SHA256`); this square reads `srtm_22_04.bef` (SHA-256 `bc6231a5bfd78139c94b9576f3e8241526f39613de43841d0c35945e81517c89`).
  - **Size:** the same data without heights makes 152,665 bytes, so heights add 17.2 %.
  - **Why not the live square, or the first fixture's Overpass query:** the live `W75_N40.rd5` holds whole one-degree tiles, far more ways than this box, which could change what each route in `routes.json` exercises; and Overpass's dated query for the first fixture's snapshot (2026-10-22T04:26:47Z) failed four times on 2026-10-30. The extract gives the same box and way selection a week later; every route keeps its category and observed outcome (the tests below, 35 of 35).
  - The first fixture (2026-10-22, no heights, 152,665 bytes, SHA-256 `ff6d9d8e…5990`) and its Overpass recipe are in git history.
- `make-fixture.sh`: the recipe, with the pinned jar and profiles, and the hashes of every input. A rerun will not match byte for byte, because WayLinker stamps the time it ran. The committed file is the fixture of record.
- `routes.json`: the rectangle the tests cut to, and seven routes, each given by two OpenStreetMap node ids and positions.

## Route categories

The categories are checked on the full fixture by the first test in `route-proof.test.mjs`, so a change of fixture cannot quietly change what a route exercises.

- **inside**: the whole route on the full square lies inside the rectangle. There are three. One crosses the 74 W line, so it reads both tiles.
- **edge**: both ends lie inside the rectangle, and the route leaves the rectangle's own cells but stays within the one-cell margin. These are the routes the margin exists for. With no margin, the cut lacks cells they use.
- **detour**: both ends lie inside the cut's data (rectangle plus margin), but the best route on the full square goes outside it.
- **outside**: one end lies inside the rectangle and the other outside the cut's data, about 1.5 km beyond the margin to the west.
