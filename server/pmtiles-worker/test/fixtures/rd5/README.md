# Route-data cutter fixture

A small BRouter routing square and the routes the cutter's proof runs on it (dispatch 2026-10-21-07, RECORD -1546). The tests are in `../../rd5/`.

## Data licence

`W75_N40.rd5` and `routes.json` contain OpenStreetMap data, © OpenStreetMap contributors. The data is available under the Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/1-0/. See https://www.openstreetmap.org/copyright.

`W75_N40.rd5` is a derivative database of OpenStreetMap, and it is made available here under the same licence. It was produced with BRouter 1.7.10's map creator (MIT licence, Copyright (c) 2019 BRouter contributors). BRouter is downloaded at test time and is not part of this repository.

## The place, and why

The fixture covers part of **Harriman and Bear Mountain State Parks, New York**: 41.22 to 41.36 N, 74.13 to 73.96 W, about 15.6 by 14.2 km.

- **Public land with a dense trail network.** Both parks are run by the New York State Office of Parks, Recreation and Historic Preservation. Every route end in `routes.json` is an OpenStreetMap trail node (`highway=path`, `footway` or `track`) inside one of the two parks. This was checked with OpenStreetMap's `is_in` on 2026-10-22: each of the 14 ends lies in relation 6467468 (Bear Mountain) or 4080499 (Harriman).
- **It crosses a one-degree line but not a five-degree line.** The 74 W meridian runs through Bear Mountain, so the cut rectangle (41.27 to 41.31 N, 74.05 to 73.99 W) spans two one-degree tiles of one square: tiles 1 and 6 of `W75_N40`. The nearest five-degree lines are 75 W, 70 W, 40 N and 45 N, all well away, so one file holds everything.
- **Outside the Pacific Northwest,** as the planner asked, so the fixture hints at nobody's region. Nobody's recorded positions are in it, only OpenStreetMap data.

## Files

- `W75_N40.rd5`: 152,665 bytes, SHA-256 `ff6d9d8e…5990`. A sparse square: data only in tiles 1 and 6, the rest empty, as BRouter's format allows. Built from one Overpass query by BRouter 1.7.10's three map-creator steps, the same steps the Pi runs. It carries no elevation, like the Pi's squares. The cutter never made it.
- `make-fixture.sh`: the recipe, with the query, the pinned jar and profiles, and the hashes of every input. A rerun will not match byte for byte, because OpenStreetMap changes and BRouter stamps its build time. The committed file is the fixture of record. The 2026-10-22 run converted XML to PBF with the same pyosmium calls given as `python -c`; the script feeds them to `python -` instead.
- `routes.json`: the rectangle the tests cut to, and seven routes, each given by two OpenStreetMap node ids and positions.

## Route categories

The categories are checked on the full fixture by the first test in `route-proof.test.mjs`, so a change of fixture cannot quietly change what a route exercises.

- **inside**: the whole route on the full square lies inside the rectangle. There are three. One crosses the 74 W line, so it reads both tiles.
- **edge**: both ends lie inside the rectangle, and the route leaves the rectangle's own cells but stays within the one-cell margin. These are the routes the margin exists for. With no margin, the cut lacks cells they use.
- **detour**: both ends lie inside the cut's data (rectangle plus margin), but the best route on the full square goes outside it.
- **outside**: one end lies inside the rectangle and the other outside the cut's data, about 1.5 km beyond the margin to the west.
