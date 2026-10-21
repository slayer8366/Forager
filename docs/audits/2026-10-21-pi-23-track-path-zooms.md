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

## Still to do under this go (recorded below as it happens)

Install, island extracts, jar, the three-box measurement. Not covered: the full build, the USVI
BRouter probe, any upload.
