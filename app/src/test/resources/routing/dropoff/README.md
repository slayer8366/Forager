# Drop-off fixture: an invented street block

For the way to a waypoint's street drop-off and its gates (dispatch 2026-10-28, "way-to route ends across houses"; the
owner's ruling of 10-28 and go of 10-28 ~15:10 PDT). The tests that read it start with `DropOffFixtureTest`.

## Data licence

None needed: **nothing here is OpenStreetMap data, and nothing is a real place.** Every way, house, fence and stream was
drawn by `make-dropoff-fixture.py`, in metres from an origin in the open Atlantic about 100 km south of Block Island,
where OpenStreetMap maps nothing. No one's recorded positions are in it. The routing square was made from that drawing
with BRouter 1.7.10's map creator (MIT licence, Copyright (c) 2019 BRouter contributors).

## Files

- `make-dropoff-fixture.py`: the drawing, and the writer of `dropoff.osm` and the map tile. Python 3 standard library
  only; the same bytes on every run.
- `dropoff.osm`: the block as OpenStreetMap XML, for the map creator. SHA-256 `90925d9d…2e8b`.
- `segments/W75_N40.rd5`: the routing square, 4,475 bytes, SHA-256 `a332b322…d8f7`, made by `make-dropoff-rd5.sh`
  (BRouter's three map-creator steps with the pinned jar and profiles, no elevation). It is the fixture of record:
  WayLinker stamps its run time, so a rerun differs in those bytes.
- `15/9875/12393.mvt`: the one zoom-15 map tile the block lies in, uncompressed, as the 11-01 build writes it (patches 0003 and 0004). SHA-256 `65220c93…e031`.
- `15/9875/12393.before-0003.mvt`: the same tile as every build before it wrote it: no barriers layer (so no mark), no `addr_street`, no `tunnel` on water lines. SHA-256 `e654fb19…68cb`.

## The block

Metres east (x) and north (y) of the origin. The pins are each target house's centre. The walker starts at
(-290, 250), 10 m east of West Road.

| Way | Kind | From → to |
|---|---|---|
| Front Road | `highway=residential` | (-300, 0) → (300, 0) |
| West Road | `highway=residential` | (-300, 0) → (-300, 300) |
| Back Lane | `highway=residential`, dead end | (-300, 140) → (0, 140) |
| East Lane | `highway=residential`, dead end | (300, 0) → (300, 150) |
| driveway A | `highway=service service=driveway access=private` | Front Road at (20, 0) → (20, 60) → (8, 70) |
| trail | `highway=path` | (-300, 300) → (-300, 550) → (300, 550) |

| Case | Target | What BRouter alone does (seen on 2026-10-28) | What the drop-off must do |
|---|---|---|---|
| **A**, the owner's case | house A, centre (0, 75), Front Road 12 | ends at Back Lane's end (0, 140), 65 m away; the straight leg crosses neighbour A | drop off where driveway A meets Front Road, (20, 0), 78 m; the leg crosses no other house; the driveway is never routed |
| **B**, fence | house B, centre (240, 75), Front Road 40 | ends on East Lane at (300, 75), 60 m; the leg crosses fence B (x = 270) | drop off on Front Road at (240, 0), 75 m |
| **C**, river and culvert | house C, centre (-230, 60), West Road 7 | ends on Front Road at (-230, 0), 60 m; the leg crosses stream C (y = 30) | drop off on West Road at (-300, 60), 70 m; that leg crosses only culvert C, which passes |
| **D**, trail | pin D (0, 575) in the wood, no building near | ends on the trail at (0, 550), 25 m | a trail end: keep it, the leg passes |
| **D2**, trail and stream | pin D2 (150, 520) in the wood | ends on the trail at (150, 550), 30 m; the leg crosses stream D2 (y = 535) | a trail end whose leg fails: the plain note, no line |

The residential area covers (-320, -50) to (320, 200); the wood covers (-320, 400) to (320, 700).

## The tile

The layers and fields the Forager basemap build writes at zoom 15, including what the build change of 10-28 adds
(marked **new**). Only what the gates read is drawn here; a real tile carries much more.

| Layer | Features | Fields |
|---|---|---|
| `buildings` | house outlines (polygons); one address point per house, at its centre | `kind` (`building`, `address`), `sort_rank`; on address points `addr_housenumber` and **`addr_street`** (new: upstream drops it at zoom 15) |
| `water` | stream lines | `kind`, `min_zoom`, `sort_rank`, and **`tunnel`** (new on lines: upstream sets it on water polygons only, so a culvert could not be told from an open stream) |
| `landuse` | the residential area and the wood | `kind`, `sort_rank` |
| `roads` | the ways above | `kind`, `kind_detail`, `name`, `access` |
| **`barriers`** (new) | fence lines, and the **mark** (patch 0004): one point at the tile's corner with no id and no fields, in every zoom-15 tile, fences or not | `kind`: `fence`, `wall`, `hedge` or `retaining_wall`; the mark has none |
