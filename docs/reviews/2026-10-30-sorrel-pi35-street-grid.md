# Review 2026-10-30: pi-35, streets from zoom 10 in the weekly build

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-30-sorrel-pi35-street-grid.md`
in Forager-cartography. Started about 22:26 PDT on 2026-10-30, finished about 23:05 PDT. Times are Pacific. Each
claim is marked **read**, **computed** or **inferred**. Renders stay on the Pi (`/tmp/sorrel3/render/shots/`).

## Prediction (formed on reading the dispatch and the patch, before the tile, jar and render work)

1. The z10 and z11 tiles gain `minor_road` features with `kind_detail` in residential, unclassified, road and nothing
   else; z9 and z12 tiles are byte-identical to the base build.
2. The jar gate catches an absent 0005; a wrong constant is caught earlier, by the tests failing `mvn package`.
3. v3 phones **do** see a change: `roads_minor` has no `minzoom` and its width ramps from 0 at z11, so between z11 and
   z12 the new streets draw as hairlines. (Computed from the style before anything else; the render confirms it.)
4. Disk: the need is computed from input bytes, which 0005 does not change; the archive grows under 0.4 GB, inside
   the 14 GB margin. The largest full-run tile stays under 500 KB with room to spare.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| `pi-35-street-grid` | `064d70542` | `064d70542a93b446bdcb1ba2ac556a085d98898f`, one commit on `288ce80cf` |
| Installed | `288ce80cf` | `/opt/forager-build/map-build/COMMIT` = `288ce80cfb7f021d52ddabb4f7aa278a31a33b53`; `origin/pi-34-barrier-mark` is the same commit |
| `origin/pi-build` | `532f70fd6`, 0001-0002 | `532f70fd6`; it is an **ancestor** of pi-35, two commits behind (0003 `5cbb559df`, 0004 `288ce80cf`) |
| Installed tree vs git | | `diff -rq /opt/forager-build/map-build` against `git archive 288ce80cf map-build`: the only difference is `COMMIT` (computed) |
| Willow's jar | `ca9201f7…a4408` | `~/scratch/willow-grid/jars/pi35.jar` sha256 `ca9201f734a076cb4823ded97feca9f984c68b8009faec990f2828cfe50a4408`, 88,619,858 bytes |
| Pilots | +0.68 %, +0.94 %, 228,641 B | Oregon 422,548,294 → 425,433,086 (+0.683 %); New York 563,872,219 → 569,180,977 (+0.941 %); largest z10 tile 228,641 B (computed from the archives) |

No `build-usca` process; `forager-build-usca.service` inactive. The delta `288ce80cf..064d70542` is four files: the patch,
`pins.env` (0005 appended to `BASEMAPS_PATCHES`), nine lines in `build-basemap-jar.sh`, one line in the README.

## 1. Does 0005 do only what is ruled? (read)

**Yes.** The Java change is one constant and one `if` after 0001's path block:
`kind == minor_road && !access in (private, no) && kindDetail in (residential, unclassified, road)` →
`foragerMinZoom = min(minZoom, 10)`. Only `setMinZoom` reads `foragerMinZoom`; the `min_zoom` attribute stays
`minZoom + 1` (upstream's 12 + 1 = 13 for these), the sort key and `sort_rank` are untouched, `postProcess` (merging,
0.1 px simplification) is untouched. Service (`kind_detail` service), living_street, raceway, and anything with
`access=private|no` never enter the branch; a US-route way keeps upstream's earlier zoom through `min`. The test edits
are upstream's `testHighways` rows for the three classes (12 → 10 in both the generic and the US column, the columns
being first zoom with and without a US route context) and the seven new `foragerStreets*` cases, which assert first
zoom 10, `min_zoom` attribute 13, sort key 12, and 13 / 12 / 15 / 15 for service, raceway, private residential and
no-access unclassified.

**From the tiles (computed, Willow's archives decoded with `map-build/tools/mvt_decode.py`):**

| Tile | Base → z10 build | Roads layer change |
|---|---|---|
| Oregon 10/163/366 (Portland) | 130,403 → 206,049 B gzipped | +1 residential, +1 unclassified merged feature; `kind_detail` set on `minor_road` = {residential, unclassified}; `min_zoom` attr 13 |
| Oregon 11/326/732 (Oregon City) | 91,385 → 131,509 B | the same two kinds |
| Oregon 12/652/1464 | 69,162 → 69,162 B, **identical** | none |
| Oregon 9/81/183 | 151,921 → 151,921 B, **identical** | none |
| New York 10/301/385 (lower Manhattan, Brooklyn) | 51,722 → 101,987 B | the same two kinds |

Prediction 1 holds (no tile in the sample carried `road`; the class is rare).

## 2. The jar script's gate (read and computed)

- The existing gate counts `foragerTrail|foragerAccess|foragerZoom` names (29); `foragerStreets…` matches none of
  them, so the 29 still holds with 0005. The new gate requires exactly 7 `foragerStreets` cases and no failure in the
  RoadsTest report. Without 0005 the count is 0 and the script dies there; with 0005 and a failing test, `mvn package`
  exits non-zero and the script stops before any gate (`set -e`).
- **My own build (computed):** upstream at `ca93fc06e` cloned locally, patches 0001-0005 from this branch applied
  cleanly with `git apply --check`, `mvn package` with a copy of Willow's Maven cache: 506 tests, 0 failures, 48 s.
  Gates as the script computes them: ran 29, streets 7, failed 0, landuse 4, barriers 8, buildings 3, water 3. The jar
  is 88,619,858 bytes like Willow's; its sha256 (`df77cecf…`) differs from Willow's because zip entries carry build
  times (inferred); the test counts are what the install gates on.
- **My own revert (computed):** with `FORAGER_STREETS_MIN_ZOOM` set back to 12 and everything else as merged,
  `mvn package` exits 1 with exactly 7 failures, all in RoadsTest: `testHighways` rows 11, 13, 14, 15 (the three
  classes, residential twice) and `foragerStreetsFromZoom10` 1-3. The 29 and the 7 counts are unchanged, so the
  gate's "failures: 7" would also have refused it. Willow's revert (0005 dropped) covers the other way to lose the
  change; between them, absent and wrong are both caught.
- **A stale line, not a gate:** the README still says "all 13 of their cases" for the Forager tests; the script has
  required 29 since pi-23 and now 29 + 7. Notes.

## 3. Size and safety for the full run (computed from the pilot archives, the last run's log and `build-usca.sh`)

- **Largest tile.** All 450 z10 tiles of the New York archive scanned with `pmtiles tile`: the largest is
  **10/302/384 at 228,641 B** (lon −73.83 to −73.48, lat 40.71 to 40.98: the Bronx, Queens, western Nassau), up
  from 131,122 B. Next 186,628 (10/303/384) and 173,142 (10/304/384), both Long Island. The limit is the 500 kB
  per stored (gzipped) tile set in the pi-23 audit. Margin on the largest tile: **271 kB, 54 %**.
- **The New Jersey caveat.** The New York extract stops at the state line, so the two tiles astride the Hudson
  (x = 301: y = 384 at 96,380 B, y = 385 at 101,987 B) are missing their New Jersey half (Fort Lee to Paterson; Jersey
  City, Newark, Bayonne, Elizabeth). The largest tile is not one of them. To settle it I built New Jersey myself with Willow's `pi35.jar` (Geofabrik `new-jersey-latest`, 164,724,852 B, Planetiler 8 m 39 s, exit 0; computed). New Jersey's share of the Hudson tiles: **10/301/384 178,675 B** and 10/301/385 29,716 B gzipped. Added to New York's halves (96,380 and 101,987 B), the full-run tiles are at most about **275 KB** and 132 KB (a sum of the two halves; the union compresses no worse than the sum). New Jersey's own largest z10 tile is 10/300/384 at 249,537 B (Paterson to Newark). So the largest z10 tile the full run will write around New York is about 250 to 275 KB, above the pilot's 228,641 B and at least **225 KB (45 %) under the 500 kB limit**. New Jersey's largest tile at any zoom is a z9 at 274 KB, which 0005 does not touch: z9 tiles are byte-identical before and after.
- **Disk (read from `build-usca.sh` and the 10-27 run's log).** The NVMe need is `(5.61 + 1.82 + 0.16) × input bytes`
  plus 10 % margin: on 10-27, 18,825,253,619 input bytes gave a need of 157.2 GB against 177.9 GB free. 0005 changes
  no input byte, so the check's numbers do not move. What moves is the real archive: 34,137,101,257 B last time;
  +0.94 % is +0.32 GB (+0.68 % is +0.23 GB), against the 14.3 GB margin the ratios carry. The card check (downloads
  plus the merged extract) is untouched. Free now: NVMe 167.3 GB, card 79.8 GB (`df`, read).
- **Build time.** Oregon 509 → 562 s (+10 %), New York 804 → 798 s (−1 %): noise around zero. The 10-27 run's
  Planetiler stage took 7 h 45 m inside a 14 h 45 m run; a 10 % Planetiler change would be under 50 min. Nothing
  downstream is timed to the minute.
- **Memory.** Peak RSS in the pilots 4.5 to 5.0 GB at `-Xmx3g`; the run uses `USCA_PLANETILER_XMX=4g` as before.

## 4. The v3 app today (computed and rendered)

**The premise "v3 draws no `minor_road` below z12" is not exact.** v3's `roads_minor` and `roads_tunnels_minor` have
no `minzoom`; their width is `interpolate exponential 1.6: z11 → 0, z12.5 → 0.5, z15 → 2, z18 → 11`. So a
`minor_road` present in a z11 tile is drawn from zoom 11 at 0 px, growing to 0.13 px at z11.5, 0.26 px at z11.9 and
0.29 px at z12. Before 0005 the z11 tiles held no `minor_road` (upstream's first zoom is 12), so nothing was drawn
there; after it they hold residential and unclassified streets, and v3 draws them, in white, as hairlines.

I rendered v3 unchanged on Willow's Oregon base and z10 archives at Oregon City (GL JS in headless Firefox, 412×800
at 2×): at z11.0 and z12.0 the two renders are pixel-identical (z11.0: width 0; z12.0: the z12 tiles already carried
the streets); at **z11.5 2.85 % and at z11.9 3.23 % of pixels differ**, and the difference is a faint white street
lattice in the residential blocks that the base render does not have. Casings (`roads_minor_casing`, width 0 until
z12) and labels (minzoom 14) do not change, and the bridges layer has `minzoom` 12.

So: nothing changes on phones from Sunday's build itself, because the archive reaches phones only with the separate
R2 upload go. When that go is taken, v3 phones gain a faint hairline street grid between zoom 11 and 12, online and
in regions downloaded afterwards. It is a small change in the direction the owner asked for, but the owner's option
text said "phones see no change until v4 ships", and that is what the owner agreed to. Should-fix 1.

## 5. The merge into `pi-build` (computed)

`origin/pi-build` (`532f70fd6`) is an ancestor of `064d70542`; `git merge-tree --write-tree` of the two gives tree
`2bf69e925…`, which is exactly pi-35's own tree. A `--no-ff` merge therefore produces a merge commit whose tree is
pi-35's: the installed `288ce80cf` map-build (confirmed byte for byte against `/opt` above) plus the four-file delta,
and nothing else; pi-33 and pi-34 arrive exactly as installed. No conflicts are possible.

## 6. Revert checks

Mine is in section 2 (the constant back to 12: 7 failures, `mvn` exit 1). Willow's (0005 dropped) is read from the
dispatch; I did not find its log in the scratch (`jar-no0005.log` holds Maven's warnings only), but the gate's
arithmetic makes the claim certain: 0 `foragerStreets` cases ≠ 7.

## Verdict

### Blocking

None.

### Should-fix

1. **Before the R2 upload go, not before the install:** tell the owner that v3 will show faint white hairline
   streets between zoom 11 and 12 once the new archive is served (section 4), and take the upload go on that fact.
   The options are to accept it (it is faint, and OSM-like), or to give v3's `roads_minor` and `roads_tunnels_minor`
   a `minzoom` of 12 in a v3.1 (which breaks the byte-frozen v3), or to hold the upload until v4 ships. The owner's
   call; not Willow's.
2. The README's "all 13 of their cases" is two gates out of date (29, now 29 + 7). One line.

### Notes

- The pilot's "no measurable change" in build time holds within noise; the sign differs between the two pilots.
- The patch comment claims OSM Carto draws these classes from its zoom 12 and the app shows OSM at `round(zoom + 1)`;
  I did not check the app's OSM raster zoom offset, it does not bear on the build.
- My scratch jar's sha256 differs from Willow's by build time only (same size, same test counts); the install's own
  sha256 is the one to record.
- The `pmtiles` CLI at `/usr/local/bin` and `mvt_decode.py` were enough to recount everything; no new tooling.

### Prediction, scored

1 held (no `road` in the sample tiles, as expected for a rare class). 2 held, both ways. 3 held, and the render put a
number on it. 4 held: disk untouched, archive +0.32 GB, the worst tile about 275 KB. Nothing unpredicted.

**Is pi-35 mergeable for install before Sunday 11-01's run? Yes.** The patch does exactly what was ruled, the gate
and the tests catch both an absent and a wrong patch, the full-run margins (tile size, disk, time) are wide, and the
merge is a fast-forward in content onto the installed tree. Install by Willow is the owner's go, as the dispatch has
it. The one thing to carry forward is should-fix 1: the R2 upload is not invisible to v3 phones between zoom 11 and 12.
