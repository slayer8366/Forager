# Review 2026-11-01: pi-37, the barrier mark as a fill (patch 0007), and the Planetiler heap

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-11-01-sorrel-pi37-barrier-mark-fill.md`
in Forager-cartography. Started about 05:15 PST, finished about 06:30 PST on 2026-11-01 (the Pi's clock, NTP-synced;
the record's "07:00" for the dispatch and "06:45" for Willow's report read as not yet shifted off daylight time). Each
claim is marked **read**, **computed** or **inferred**. Nothing in `/opt` or `/srv` was touched; no Planetiler run was
needed, Willow's six Oregon archives and GC logs and the two US logs were enough.

## Prediction (formed on reading the diff and Willow's diagnosis, before the source and the arithmetic)

1. Planetiler 0.10.2 hashes a tile only when every feature is a fill or an edge; a point anywhere defeats it. A
   full-tile polygon keeps it. The heap cost is the pmtiles writer's entry list, one object per entry, which the
   run-length merging of identical consecutive tiles normally keeps small.
2. 0007 is one constant and a comment; coverage is unchanged because the post-processor that adds the mark is
   untouched.
3. PR #439 reads only the layer's presence and only its lines, so a polygon mark changes nothing there, but its fixture
   and comments say "point" and should follow.
4. On the heap: with 0007 the run returns to last week's profile, 3.9 to 4.0 GB live of 4.2; 4g is a 7 % margin and
   a grow of the entry list's backing array would break it; 5g is comfortable only if nothing else is resident.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| `pi-37-barrier-mark-fill` | `63a3649e5` | `63a3649e52a43f03a84d4650220f015a8bf2a26a`, one commit on `de30a84ee` |
| `pi-build`, installed | `de30a84ee` | `origin/pi-build` = `/opt/forager-build/map-build/COMMIT` = `de30a84ee`, an ancestor, so the merge is a fast-forward in content |
| Oregon entries | 418,172 / 613,976 / 613,979 / 418,177 | `pmtiles show` on Willow's archives: p3 418,172; p4 and p5 613,976; p6 613,979; p6fill and p7 418,177 (computed), addressed tiles 668,387 in every one |
| Failed run | OOM at 48 %, 4.2G/4.2G | `usca-20261031T193102Z.log`: `OutOfMemoryError: Java heap space` in `ArrayList.grow` from `WriteablePmtiles$DeduplicatingTileWriter.write` (`WriteablePmtiles.java:341`); the last progress lines read `heap: 4.2G/4.2G … postGC: 4.3G`, gc 20 %; `metrics.jsonl` peak RSS 6,239,552 KiB |

The delta is four files: the patch (98 lines), `build-basemap-jar.sh` (the BarriersTest gate 8 → 9 and a comment),
`pins.env` (0007 appended), one README line. `USCA_PLANETILER_XMX` is **not** changed on the branch. The build service is
`failed` from the 10-31 run; nothing is running. Other work on the Pi during this review: one `pmtiles extract` of a
remote planet archive (not mine; small).

## 1. The mechanism (read from the 0.10.2 sources, fetched from Maven Central)

- `VectorTile.likelyToBeDuplicated()` is `all layers empty || containsOnlyFillsOrEdges()`, and the latter returns
  false at the first feature whose `isFillOrEdge(true)` is false. `isFillOrEdge` returns false at once for any geometry
  that is not a POLYGON (or, with edges allowed, a LINE). So one point in any layer makes the whole tile "not likely to
  be duplicated".
- `TileArchiveWriter` (line 367): `tileDataHash = archive.deduplicates() && tile.likelyToBeDuplicated() && bytes != null
  ? fnv1a64(bytes) : null`. No hash, no deduplication.
- `WriteablePmtiles.DeduplicatingTileWriter.write`: with a hash, a tile whose content was seen before reuses its offset
  and, when it follows the previous entry's run, only bumps `runLength` and returns without a new entry; without a
  hash (`numUnhashedTiles++`) the tile is always written and always gets a new `Entry`. The entries live in an
  `ArrayList` in the heap until the directories are written at the end.
- A full-tile fill keeps the dedup: a polygon is a POLYGON, and the new test asserts `isFill()` and
  `likelyToBeDuplicated()` for a water fill plus the mark; the Oregon archives show it (contents 608,414 → 384,350).
- The planet comment in `VectorTile.java` explains the stakes: the fills-or-edges heuristic "cuts down the number of
  tile hashes … by 98 %"; the mark had switched it off for every z15 tile.

## 2. The patch (read)

`MARK` becomes one polygon with corners (−4, −4), (260, −4), (260, 260), (−4, 260): the tile and its 4 px buffer, id 0,
tags `Map.of()`. `postProcessTile` is unchanged: every tile with `z() == 15`, after the layers' own post-processing,
gets the mark appended to its `barriers` list. So the coverage is 0004's exactly: every zoom-15 tile Planetiler writes,
whatever else it holds, and none below 15 (the `foragerNoMarkBelowZoom15` case stands). No id, no fields, as before.
Minimal: one constant, one import, comments, and the test that pins the shape.

**From the tiles (computed):** Oregon's addressed tiles are 668,387 in all six archives; entries fall 613,979 → 418,177
and contents 608,414 → 384,350 between p6 and p7; the archive 458,535,950 → 434,766,218 B (−5.2 %). p7 equals p6fill
in every count, and p3 (no mark at all) has 418,172 entries, so the fill mark costs five entries over no mark.

## 3. The meaning (read)

- **PR #439 (`wayto-fence-mark` at `bc2cdd141`, unmerged).** `ReadNearbyMapData` sets a tile's coverage to `Checked`
  when `mvt.layer("barriers") != null`, and `NotInTileSet("… has no barrier mark …")` otherwise; it never looks at the
  mark's geometry. `TileFeatureReader` keeps `MvtGeometryType.LINESTRING` only from the layer, so the polygon is dropped
  exactly as the point was. A deduplicated tile is the same bytes served from a shared offset, so the layer is present
  in every z15 tile, filled or not. "Barriers layer present = fences checked" holds for this reader unchanged.
- **But its fixture and words say "point".** `make-dropoff-fixture.py` writes `MARK` as a MoveTo (0, 0) point and its
  docstring says "one point at the tile's corner (0, 0)"; the PR body says the same. The tests pass either way, since
  they test presence, but the fixture no longer matches what the build writes. Should-fix 2, for #439, not for pi-37.
- **Styles and the Worker.** No style (`forager-street-v1` to `v4`, light, the Worker's offline style) and nothing
  under `server/pmtiles-worker/src` reads the `barriers` layer; MapLibre parses an undrawn polygon and discards it.

## 4. The tests and the gate (read and computed)

- The amended case asserts the mark is a POLYGON, `isFill()`, with empty tags; the new case builds a tile of one
  full-tile water fill plus the mark and asserts `likelyToBeDuplicated()`. Together they pin the two things that
  matter: the mark's shape and the property that shape buys. The three 0004 cases (every z15 tile, beside fences, none
  below 15) still hold coverage.
- The gate counts `forager*` cases in BarriersTest: 9 with 0007; without it 8, refused (Willow's revert; the arithmetic
  is certain).
- **My build (computed):** upstream `ca93fc06e` from a clean tree, patches 0001 to 0007 from this branch applied with `git apply --check` (0007 applies cleanly on 0001 to 0006), `mvn package` with a copy of Willow's Maven cache: 513 tests, 0 failures, 42 s. The gates as the script computes them: BarriersTest 9 forager cases with no failure, roads 29 and 7, landuse 4 and 6, buildings 3, water 3. The jar is 88,620,216 B like Willow's p7 jar; its sha256 (`3cb028ca…`) differs by build time (inferred, as before). My first attempt built on a tree where only the untracked Barriers files survived an earlier reset and failed five tests with no forager cases in RoadsTest; that was my scratch, not the patch, and the clean rebuild above replaces it.
- **My revert (computed):** the mark's polygon changed to (0, 0)–(128, 128), a quarter of the tile, everything else as merged: `mvn package` exits 1 with exactly 2 failures, `foragerMarkOnAZoom15TileWithNoBarrier` (`isFill()` false) and `foragerMarkKeepsAFillOnlyTileDeduplicated` (`likelyToBeDuplicated()` false). So a mark that is a polygon but not a fill, which would look right to a reader and still defeat the dedup, is caught; the 9-case count alone would not have caught it, the assertions do.

## 5. The heap (computed from the archives and logs, inferred where marked)

**Size of the thing that overflowed.** `Pmtiles.Entry` is two longs and two ints: 12 B header + 24 B = 36, padded to
40 B, plus a 4 B compressed reference in the list: 44 B per entry (inferred from the layout). The live US archive
(10-27, patches 0001 and 0002) has **58,894,862 entries** for 651,232,395 addressed tiles and 43,217,729 contents
(`pmtiles show`, read): 2.6 GB of entries. That is why the 10-27 run ended at `postGC 3.9G`, then `4.0G`, with one
reading of `4.3G`, of 4.2G: 1.2 to 1.3 GB of baseline plus the list. Willow's scaling of Oregon's +46.8 % gives +27.6M
entries, +1.2 GB; the 10-31 run died at 48 % of the archive exactly as that predicts.

**What killed it, precisely.** The stack is `ArrayList.grow` → `Arrays.copyOf`: the list's backing array doubles by
1.5× and the old and new arrays are live together. At 59M entries the array is 236 MB and its last growth (43.4M →
65.1M capacity) needed 434 MB transiently; at 86M it would have been 516 MB on top of 3.8 GB of entries. So the next
growth step is the cliff: the capacity after 10-27 is about 65.1M entries; crossing it costs another ~390 MB array
plus the copy. 10 % of growth in entries from OSM edits or a feature that defeats the dedup again would do it.

**With 0007.** Entries return to the 10-27 count plus whatever 0005 and 0006 add (Oregon: +5 of 418k, computed), so the
live set at the end returns to 3.9 to 4.0 GB of 4.2, with GC at 5 to 10 % through the archive phase (max 29 % in the
last 400 progress lines of 10-27; the failed run reached 20 % as it died). That is a 5 to 7 % margin: it passed on
10-27 and should pass next week, but one array growth away from failing.

**RSS and the 8 GB.** The Pi has 8,252,416 kB (7.87 GiB), 200 MB of swap, no zram. 10-27's Planetiler peaked at
6,869,136 KiB RSS (6.55 GiB) at 4g; 10-31 at 6.24 GB. RSS counts resident pages of the ~21 GB of memory-mapped node
and multipolygon files, which the kernel can evict; the part it cannot evict is the heap (4.29 GB at 4g) plus the JVM's
own ~0.4 to 0.5 GB. Resident beside it right now (read): four Claude sessions, 1,277 MiB; the desktop, 155 MiB; the
system's own few hundred. So the anonymous total during a run is about 4.8 GB (JVM at 4g) + 1.8 GB (the rest) = 6.6 GB
of 7.87, leaving 1.2 GB of page cache for 21 GB of maps; at 4.5g 1.2 → 0.7 GB; at 5g → 0.2 GB, where the node-map
lookups in pass 2 (4 h 49 m on 10-31) would thrash and the OOM killer is one allocation away. With the sessions closed
the same sums are 2.5 / 2.0 / 1.5 GB.

**Recommendation.**
- **4.5g**, as the pin, with the comment at `pins.env:174-177` rewritten to the measured facts (4g peaked at 6.55 GiB
  RSS on US + Canada; the end-of-run live set is 3.9 to 4.0 GB, of which 2.6 GB is the writer's entry list). It buys
  0.5 GB, which covers one growth of the entry array with room, and costs 0.5 GB of page cache that the sessions, not
  the build, are the larger claim on.
- **5g only when the Pi runs the build alone** (sessions closed Saturday night). That is a rule for the owner, not a
  pin; if it is the practice, 5g is the better pin.
- **Not swap, not zram for the heap.** G1's concurrent marking walks every live `Entry` each cycle; paging the list,
  even to compressed RAM, would turn each cycle into a stall. Zram would help only for the other residents (idle
  sessions compress well), which is a weaker lever than closing them.
- **The real fix is 0007**, which removes 1.2 GB of growth; the pin change is insurance against the next cliff, not a
  substitute. Build time is unchanged by either (Oregon p7 631 s against p6 625 s, noise).

## 6. The install (read)

pi-36's steps apply unchanged: the `--no-ff` merge into `pi-build`, the jar built and installed with its sha256
recorded, `install-map-build.sh` from a clean worktree with `COMMIT` and `map-build.prev` recorded, the rollback written
down (the prev tree moved back, the jar restored from its dated backup). The `-Xmx` change, if the owner takes it, is a
`pins.env` edit that should ride on this branch so one install carries both and one rollback undoes both. Should-fix 1.
Then a rerun is the owner's go, and the 11-08 timer picks the installed tree up either way.

## Verdict

### Blocking

None.

### Should-fix

1. **Put the `-Xmx` change on the branch** (`USCA_PLANETILER_XMX=4.5g`, with the pin's comment rewritten to the
   measurements above), so the install and the rollback each move one tree. As a separate commit, so the owner can take
   0007 without it.
2. **For PR #439, not this branch:** make the fixture's mark the fill the build now writes (a polygon ring over the
   tile) and change its docstring and the PR text from "one point at the tile's corner"; the reader needs no change.

### Notes

- Willow's diagnosis is right in mechanism and in numbers; the entry list, not the feature groups, is the heap.
- The failed run's partial output and log stay in `failed-usca/latest`; nothing here depends on deleting them.
- The 10-27 log carries the warning that Planetiler "will use ~21G memory-mapped files … but the OS only" has less;
  that is the page-cache trade-off above, and 0007 does not change it.

### Prediction, scored

1 held. 2 held. 3 held, including the fixture. 4 held, and the arithmetic put the cliff where the prediction only
guessed it: the next growth of the entry array.

**Is pi-37-barrier-mark-fill mergeable for install, and which -Xmx?** Yes, mergeable: 0007 restores Planetiler's
deduplication for every tile the mark touches, keeps the mark on every zoom-15 tile with no id and no fields, changes
nothing any reader relies on, and is pinned by a test of the shape and a test of the property. **4.5g**, on the branch
as its own commit; 5g if the owner will keep the Pi otherwise idle during the run. The install and a rerun are the
owner's go.
