# pi-23: tracks from z11 and paths from z12 in the US + Canada tiles; prep, patch, tests

Dispatch `prompts/preserved/2026-10-21-pi-23.md` (with the owner's go and the planner's rulings
appended), against `pi-build` at `1f59eae35`, re-checked by fetch. Written by the pi-23 Pi coder.
No full build has run; that needs the owner's separate "build".

## The owner's rulings (verbatim, through the planner's question tool; see the preserved dispatch)

- Trails: "Paths only (Recommended)". Tracks from z11; `kind_detail=path` from z12; footway,
  bridleway, steps and cycleway stay at 13.
- Go: "Go, build the tests (Recommended)".
- Size limit: "Coder's limits (Recommended)": stop and ask past about 1 GB (3%) whole-map growth,
  over 50% z11-12 growth in any test area, or any single tile over 500 kB.

## The prep: premises, re-checked (read / observed / inferred)

1. **Data floor (read).** Upstream `Roads.java` at `ca93fc06`: `:273` puts every `pm:kind=path`
   (track and pedestrian included) at 12; `:274-275` puts path, cycleway, bridleway, footway, steps
   at 13; `:276` sidewalk, crossing, corridor at 14. Ordered index, last match wins (`Matcher.java`,
   `getInteger` walks `matches.reversed()`). `:392` raises access=private/no to 15; `:398` sets
   attribute `min_zoom = minZoom + 1`; `:448` `setSortKey(minZoom)`. The trail patch touched no zoom.
2. **Low-zoom generalisation (read).** Roads sets `setMinPixelSize(0)`, `setPixelTolerance(0)`
   (`:405-406`), overriding Planetiler's logged defaults (`min_feature_size=1.0`). `postProcess`
   merges lines with `mergeLineStrings(items, 0.5, 0.1, 4)` (`:850-854`); Planetiler 0.10.2's
   bytecode shows every group goes through `LoopLineMerger` with `setMinLength(0.5)`. 0.5 px is
   about 27 m at z11, 13.5 m at z12, at 45°N (inferred): spurs only, not real tracks.
3. **Extracts (observed).** No island `.pbf` on the Pi. Geofabrik serves
   `us/puerto-rico-261017.osm.pbf` (74,075,487 bytes) and `us/us-virgin-islands-261017.osm.pbf`
   (3,122,047) with `.md5`. Headers: PR 2026-10-17T20:20:51Z sequence 3281 (first 64 KB decoded);
   USVI the same timestamp, sequence 1790, MD5 good; us 2415 and canada 4945 the same timestamp.
   `build-usca.sh:538-542` stops on any differing timestamp.
4. **Basemap (read).** No `--basemap` means Planetiler runs (`build-usca.sh:686-703`). No `--force`
   is needed: the current manifest's sequence `us:2415,canada:4945` differs from the four-region one
   (`:547-556`). `run_record.osm_sequence` accepts the four-region string (called on it). The
   published 2026-10-19 run was unit `forager-usca-routing-261017-d`, `--local 261017 --basemap
   <kept>`, 4h22m; unit `-c` failed at publish on the sequence parse later fixed.
5. **Resources (observed).** NVMe 164,111,843,328 bytes free; card 100,409,962,496. The script's
   own check for four regions needs 157.0 GB on the NVMe (inputs 18,807,079,981 x 7.59 + 10%): 7.1
   GB of headroom. Nothing `forager*` running. Firefox was running during the 261017 Planetiler run
   too (journal), so its 7.0 GB peak includes it.
6. **Installed vs committed (observed).** `/opt/forager-build/map-build` equals
   `origin/pi-build:map-build` apart from `COMMIT` (`536f2f3d`).
7. **Other users (read).** `map-style/overrides/forager-street.mjs:129,134` (PATH 13, TRACK 12),
   `:141` (cycleway drawn with footpaths), `map-style/tools/check-street-trails.mjs:105,134`;
   `roads_labels_minor` sorts by `min_zoom` (symbol-sort-key). Identical on `pre-main` and
   `street-cloudflare`. Style follow-up goes through Rowan; not changed here.

**Premise that was wrong:** the dispatch moved footway, bridleway and steps to z12; the rebuild list
(`2026-10-21-next-rebuild-list.md:38-39`) moved only paths. The owner ruled "Paths only".
**Side effect found:** moving the zoom through `highwayZoomsIndex` would also change the `min_zoom`
attribute and so Street's label priority. The planner ruled to keep upstream's attribute and sort key.

## The change

`map-build/patches/protomaps-basemaps/0001-roads-trail-attributes.patch`, amended (one pinned patch;
the planner's ruling 3). In `processOsmHighways`, after the access line:
`int foragerMinZoom = minZoom;` lowered to `Math.min(minZoom, 11)` for `kind=path, kind_detail=track`
and `Math.min(minZoom, 12)` for `kind_detail=path`, unless access is private or no; only
`.setMinZoom(foragerMinZoom)` reads it. `min_zoom`, the sort key and names keep upstream's values.

**Found by the first test run, before any commit:** the first draft set the zoom outright. Upstream's
`testHighways` (`RoadsTest.java:323-333`) also pins that any highway on a `US:US` route relation
starts at z6 (`US:I` at z3); the draft pushed such a track to 11 and a path to 12 (2 failures,
"expected `_minzoom=6` ... but was 11 / 12"). `Math.min` fixed it: the change only ever lowers a zoom.

`map-build/bin/build-basemap-jar.sh`: the Forager test count now includes `foragerZoom*`, 29 cases.

## Tests (scratch copy, `mvn -o test -Dtest=RoadsTest`, offline copy of `/srv/forager-build/.m2`)

Forward: `RoadsTest` 98 run, 0 failures; 29 Forager cases (13 trail + 16 zoom), counted with the
jar script's own new grep on the report. New tests: `foragerZoomTrackFromZ11`,
`foragerZoomPathFromZ12`, `foragerZoomKeepsUpstreamMinZoomAttributeAndSortKey` x2,
`foragerZoomOthersUnchanged` x10, `foragerZoomNamesStayAtZ14` x2; edited: `testHighways` rows 18
(track 11) and 19 (path 12), `foragerTrailAttributesOnPath` (12), `...OnTrackFromItsFirstZoom` (11).

Revert checks: each edit on the file after saving a copy; the build log checked for compile errors
and the XML for freshness before reading; restored from the saved copy, sha256 compared (match each
time); the two forward lines confirmed present afterwards. Every failure below is one that edit can
cause, and nothing else failed.

| Revert | Failures | Message |
|---|---|---|
| R1: track line → `minZoom` | 4 of 98 | `foragerZoomTrackFromZ11`, `testHighways[18]`, `...OnTrackFromItsFirstZoom`: `expected _minzoom=11 ... but was 12`; `KeepsUpstream...[1]`: `expected: <11> but was: <12>` |
| R2: path line → `minZoom` | 4 of 98 | `foragerZoomPathFromZ12`, `testHighways[19]`, `foragerTrailAttributesOnPath`: `expected _minzoom=12 ... but was 13`; `KeepsUpstream...[2]`: `expected: <12> but was: <13>` |
| R3: path rule also takes cycleway | 2 of 98 | `testHighways[20]`, `foragerZoomOthersUnchanged[4]`: `expected _minzoom=13, kind_detail=cycleway but was 12` |
| R4: names from `min(minZoomNames, foragerMinZoom)` | 2 of 98 | `foragerZoomNamesStayAtZ14[1]`, `[2]`: `expected name=<null> but was name=Ridge Trail` |
| R5a: `min_zoom` from `foragerMinZoom + 1` | 2 of 98 | `KeepsUpstream...[1]`: `expected: <13> but was: <12>`; `[2]`: `expected: <14> but was: <13>` |
| R5b: sort key from `foragerMinZoom` | 2 of 98 | `KeepsUpstream...[1]`: `expected: <12> but was: <11>`; `[2]`: `expected: <13> but was: <12>` |

R3 and R4 show the guard tests, which pass with or without the change by design, can fail.
Final forward run after all restores: 98, 0 failures.

## Installed, island extracts, jar (2026-10-21, after `e5f5add63`)

- `install-map-build.sh`: `/opt/forager-build/map-build` at `e5f5add63`; `diff -r` against
  `git archive HEAD map-build` shows only `COMMIT`.
- `ip route`: default via `wlan0` (metric 600), `bnep0` behind it. As forager-build:
  `puerto-rico-261017.osm.pbf` (74,075,487 bytes) and `us-virgin-islands-261017.osm.pbf` (3,122,047)
  onto `/mnt/archive/forager-build/sources-osm/`, `.md5` files and symlinks in `sources/osm`;
  `md5sum -c` OK for both; headers 2026-10-17T20:20:51Z, the same as us and canada.
  `puerto-rico.poly` and `us-virgin-islands.poly` pre-placed in `sources/aux` with `.headers`, so a
  `--local 261017` run needs no network until its coastlines turn 30 days old (from 2026-11-03).
- `build-basemap-jar.sh` (as an admin, from `/opt`): checkout `ca93fc06`, patch applied, all 481
  tests in `tiles/` passed, "Forager tests ran: 29 cases, 0 failures", installed jar sha256
  `7f9c4aef942b3688980bfe91a67e455eb1bc59115a4b5a37ddee41d7ed98f8db` (was `a2bc3271...`, the jar the
  261017 archive was built with; that copy kept in scratch for the measurement, then deleted).

## The size measurement (before any full build)

**Method.** Three boxes cut from `us-261017` with `osmium extract -s complete_ways` (one at a time:
317-319 s, peak 3.66-3.81 GB each), each built twice with Planetiler at `--maxzoom=13 -Xmx2g`, once
with the old jar and once with the new, on the same inputs and sources (233-455 s, peak 2.50-2.66
GB each). Compared over tiles wholly inside an inner box, so no tile is cut by the extract's edge:
W1 Gifford Pinchot/Rainier -122.2,46.0,-121.2,47.0; E1 Monongahela WV -80.5,38.0,-79.5,39.0; U1
Seattle -122.6,47.35,-122.05,47.9 (the first U1 inner box, -122.45,47.5,-122.2,47.75, held no whole
z11 tile and six z12 tiles, so it was widened to just inside the extract).

**Control.** The old-jar box build against the published 261017 archive, tile for tile (`pmtiles
tile`): the roads layer has the same feature count in every interior tile, and its bytes agree to
within 0.07% (W1 z11 20,309 vs 20,315, z12 384,782 vs 385,052; E1 z11 43,165 vs 43,162, z12
456,152 vs 456,349; U1 z11 78,154 vs 78,138, z12 612,370 vs 612,484). Whole tiles are not
byte-identical: landuse and pois from large polygons differ near the cut (inferred: relations cut
by the extract). Only the roads layer changes here, so the box deltas stand for the archive's.

| Box | Zoom | Interior tiles | Raw bytes old → new | Gzip growth | Largest tile old → new | `track` features | `path` features | Named roads |
|---|---|---|---|---|---|---|---|---|
| W1 | 11 | 35 | 282,215 → 386,563 (+37.0%) | +39.5% | 35,491 → 35,632 | 0 → 221 | 0 → 0 | 0 → 0 |
| W1 | 12 | 176 | 931,958 → 1,106,411 (+18.7%) | +18.2% | 22,872 → 23,782 | 1,150 → 1,150 | 0 → 1,022 | 101 → 101 |
| W1 | 13 | 704 | identical | 0 | identical | same | same | same |
| E1 | 11 | 24 | 726,894 → 793,025 (+9.1%) | +10.1% | 75,368 → 77,388 | 0 → 94 | 0 → 0 | 0 → 0 |
| E1 | 12 | 130 | 1,912,145 → 1,956,427 (+2.3%) | +2.4% | 51,046 → 51,372 | 413 → 413 | 0 → 301 | 276 → 276 |
| E1 | 13 | 588 | identical | 0 | identical | same | same | same |
| U1 | 11 | 8 | 559,989 → 564,621 (+0.8%) | +0.9% | 128,986 → 130,505 | 0 → 55 | 0 → 0 | 29 → 29 |
| U1 | 12 | 40 | 1,962,528 → 2,071,545 (+5.6%) | +5.9% | 103,446 → 107,738 | 128 → 128 | 0 → 739 | 1,445 → 1,445 |
| U1 | 13 | 187 | identical | 0 | identical | same | same | same |

z11+z12 together, raw: W1 +23.0%, E1 +4.2%, U1 +4.5%. z13 byte-identical in every box: nothing but
the two zooms changed. Named roads unchanged at every zoom (label density), and no track or path
carries a name below z14. Planetiler time per box unchanged (W1 233.3 s vs 234.0 s).

**Extrapolation.** The archive's own per-zoom bytes, read from its PMTiles directories (sum of entry
lengths; 4% above `data_length` because shared tiles count once per entry): z11 1,094,464,087,
z12 2,214,567,843. Each box's gzip growth applied to the whole archive: W1 0.835 GB (2.5%), E1 0.162
GB, U1 0.140 GB; the three boxes' mean 0.379 GB (1.1%). W1 was chosen as track-dense forest, so
0.84 GB is taken as the high end (inferred; three boxes are not a continent).

**Against the owner's limits ("Coder's limits (Recommended)"):** whole map about 0.4 GB, at most
about 0.84 GB, under 1 GB; largest z11-12 growth in a test area W1's +37.0% raw at z11 (+23.0% for
z11-12 together), under 50%; largest tile 130,505 bytes, under 500 kB. None crossed.

## After cleanup

Measurement scratch deleted (extracts, mbtiles, the Maven repo copy, the old jar copy). NVMe free
164,072,095,744 bytes against the build's own check of 157,020,310,761 (inputs 18,807,079,981 x 7.59
+ 10%): 7.05 GB of headroom. Card free 100,332,761,088.

## Not done (not covered by this go)

- The full build: needs the owner's "build". Command proposed in the pi-23 prep report: a transient
  unit running `build-usca.sh --local 261017` with `RemainAfterExit=yes`, about 12.5 h (inferred from
  measured parts).
- **The USVI BRouter probe, a question for the owner:** `charlotte-amalie` needs `W65_N15.rd5` over
  100 kB or the run refuses to publish after about 12 h, and that square's size is an estimate
  (`pins.env:232`). Cost: BRouter's three steps on the 3.1 MB USVI extract, about 2 minutes and
  under 1 GB (inferred, at 16 bytes per node), scratch only.
- Any upload. The old output is untouched.
