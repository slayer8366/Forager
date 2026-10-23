# Two-square route-data fixture

Two BRouter routing squares that meet at 40 N, and the routes the two-square proof runs across them (dispatch 2026-10-23-01). The test is `../../../rd5/route-proof-two-squares.test.mjs`. The single-square fixture beside this folder is unchanged.

## Data licence

`W110_N35.rd5`, `W110_N40.rd5` and `routes.json` contain OpenStreetMap data, © OpenStreetMap contributors. The data is available under the Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/1-0/. See https://www.openstreetmap.org/copyright.

Both `.rd5` files are derivative databases of OpenStreetMap, made available here under the same licence. They were produced with BRouter 1.7.10's map creator (MIT licence, Copyright (c) 2019 BRouter contributors). BRouter is downloaded at test time and is not part of this repository.

## The place, and why

The fixture covers the foothills west of **Boulder, Colorado**: 39.90 to 40.10 N, 105.40 to 105.22 W. Most of the trails there are in the City of Boulder's Open Space and Mountain Parks. The route ends are on named trails (Bluebell Mesa, Gregory Canyon, Royal Arch, Ranger and others). Unlike the single-square fixture, I did not check each end against park boundaries.

- **It crosses a five-degree line.** The 40 N parallel runs through the parks, so one area needs two square files: `W110_N35` south of it and `W110_N40` north of it. WayLinker writes both sides of the line from one border-nodes file, which is what lets BRouter route across it (route-data report, Q3).
- **The fixture is wider than the cut, in every direction.** The rectangle (39.985 to 40.015 N, 105.31 to 105.28 W) grown by the one-cell margin covers about 39.94 to 40.06 N and 105.34 to 105.25 W. The data reaches beyond that on all four sides, so the cut drops data, and the test checks that it does. An earlier, smaller extract lay entirely inside the cut and could not have shown anything.
- **Outside the Pacific Northwest**, as for the first fixture. It holds no one's recorded positions, only OpenStreetMap data.

## Files

- `W110_N35.rd5`: 174,403 bytes, SHA-256 `4c585e4450865e9f32c8e5b8b27f497c6e8a2e15eacb93532fb84f8e85dc014d`.
- `W110_N40.rd5`: 311,278 bytes, SHA-256 `1faa961d15699bd6c76a6ea57eadad8bd3b6b181db6b3a038e34ed6a50f63814`.
- `make-fixture.sh`: the recipe, the same steps as `../make-fixture.sh` with a different box. `OVERPASS_URL` can name a mirror; the run below used the default.
- `routes.json`: the rectangle and four routes, each given by two OpenStreetMap node ids and positions.

Recorded from the 2026-10-23 run: Overpass `osm_base` 2026-10-23T09:46:02Z; `boulder.osm` sha256 `93bc333b2ea978f983e7449a6667c01b92246a91b1b38676e608fec2a9aba5ec`; `boulder.osm.pbf` sha256 `65651dcdb46ea969e9c10ba879e9c28724f4e118eb1966368263afd1ece29607`. A rerun will not reproduce the squares byte for byte, because OpenStreetMap changes and WayLinker stamps its run time. The committed files are the fixture of record.

## How the routes were chosen

Ends were drawn at random (fixed seed) from nodes on named paths, footways and tracks inside the rectangle: one end south of 39.997 N and the other north of 40.003 N. Each pair was routed on the full squares with the hiking-mountain profile. A pair was kept only if its track stayed inside the rectangle and crossed 40 N. The first six pairs all qualified, and four were kept. The selection script was a one-off and is not committed. Its criteria are re-checked on every run by the test's first case.
