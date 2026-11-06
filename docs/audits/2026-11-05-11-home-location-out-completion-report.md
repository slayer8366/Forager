# Dispatch 2026-11-05-11, Phase 2: the owner's home location out of the public repository (completion report)

Hazel Cairnwright, continuing Phase 2 from the earlier Hazel's last push, 2026-11-06. RECORD -2182. Branch
`home-location-out`, cut from pre-main `6cfcd8d6c`. The dispatch is
`prompts/preserved/2026-11-05-11-home-location-out.md`, and the plan is section 6 of the Phase 1 report, both on
records-after-173.

**This report carries no home values.** It has no coordinates, grid references, tile numbers, ranges or boxes, and no
offset between the home and the made-up place. Matches are given by kind and count only.

Claims are marked [read] (from the code or a log), [observed] (from a run or a command), [inferred], or
[not verified].

## What landed

| Commit | By | What |
|---|---|---|
| `1a0ce0ed1` A | earlier Hazel | Tests first: the expected values for the made-up place P, plus `MadeUpPlace` and `MadeUpPlaceTest` |
| `4e8b01106` B | earlier Hazel | The test fixtures move to P by one translation |
| `3b82f1271` C | me | The five sunset clocks go back to pre-main's round values, and their comments are recomputed for P (planner's (1b)) |
| `9ad46f6b0` D, `f723e6057` D2 | me | The derived-value sweep: times and heights taken at the owner's home, and one grid reference on a line D edited |
| `0889b4924` F | me | Docs, the two source comments, both scripts, and the 7 topo-night tile images removed |
| `9dab3e3c8` E | me | Two-place test values keep their two places (planner's (a)), including `TripNavigateTest.kt:97` |
| `17dea9d5d` H | me | The topo-night test sample rebuilt over Asheville, and the renders removed (the owner's rulings) |
| `157ad1fe4`, this commit | me | This report: work in progress, then final |

Nothing a user sees changed. The app-code changes are comments only. Everything else is test code, docs and
scripts [read: `git diff --stat 6cfcd8d6c` lists no file under `app/src/main` except the 3 commented files].

## Verification before building

- **Heads** [observed: `git ls-remote` after a fetch]: home-location-out was at `4e8b01106`, pre-main at `6cfcd8d6c`, and
  nav-card-kind-line at `50808417f`, as the dispatch said. pre-main moved once, to `d02d2e74a`, a docs-only commit to
  CLAUDE.md. It was merged, never rebased, at `d375a3191`, and the final search was re-run over the merged tree.
- **The private patterns file** holds a description in comment lines, not `grep -f` patterns. It was read only
  through the earlier Hazel's private scanner and through its one starting-grep line. I checked the scanner before
  relying on it [read]. It reproduces the starting grep's own counts at pre-main exactly [observed].
- **The earlier Hazel's worktree** had 18 uncommitted modified files, so it was not clean. I made my own and did not
  use her uncommitted work [observed].

### Review of A (`1a0ce0ed1`)

- It touched 16 test files. The 11 pair texts and geo links, the tile estimate, the five sunset clocks and the
  bubble-cap grid reference now expect P [read].
- **Red run 42207228535**, on a throwaway ref at A [observed]:
  - the compile was clean [read: the build log has no `e:` lines and no failed compile task];
  - 24 tests failed, all in classes A touched, each message expecting P's value where the run had the old fixture's.
- **Not red before B:**
  - one changed assertion in `AvailabilityScreenMapIconStackTest` sits in a test `@Ignore`d on pre-main since 2026-08-31, so it never ran [read];
  - the noon and evening route-card classes stayed green with A's clocks moved, so their pass does not depend on the clock's exact offset [observed].
  - `MadeUpPlaceTest` is new and passes on both sides.
- **Independent checks** [observed]:
  - The 15 km tile estimate at P, 1795, matches my own reimplementation of the grid count.
  - P's grid reference matches my own UTM and MGRS conversion.
- **Wrong in A, and stopped on:** the five clocks were moved with the fixture to the second. Together with P, those
  shifts let a reader solve for the home to well inside the dispatch's 2 km. The planner ruled (1b), for privacy,
  departing from Phase 1's "moved so it keeps its stated offset". Commit C puts the clocks back to pre-main's round
  values, recomputes what their comments state for P, and removes every mention of the old place's sunset or dusk.
- A widens no tolerance, drops no assertion and silences no test. Its 15 removed assertion lines are each replaced,
  and it adds 2 new tests and no `@Ignore` [read]. Its added lines are clean under the private patterns [observed].

### Review of B (`4e8b01106`)

- It touched 102 files: 184 pairs, 2 single longitudes and 1 generated latitude in tests. The shared fixtures take P
  from `MadeUpPlace` [read].
- Run 42207752188 was green [observed]. It widens no tolerance, drops no assertion and silences no test [read].
- **Wrong in B, and stopped on:** values were written with more decimal places than they had. The planner ruled that
  the values whose places were extended beyond what they had, at two places, keep their own places (commit E).
  Longer values stay as B wrote them, also on the planner's ruling.

## Evidence

| Run | Ref, head | What it shows |
|---|---|---|
| 42207228535 | throwaway at A | A red: 24 failures, all A's, clean compile (above) |
| 42207752188 | home-location-out `4e8b01106` | B green |
| 42210996620 | throwaway: C with B2's old countdown | **Red:** exactly the 2 B2 dusk tests, each rejecting P's line, "Sunset 1:37 AM · in 37 min · dark 2:06", against the old countdown. Clean compile [read: the build log has no `e:` lines and no failed compile task] |
| 42210999406 | throwaway at C `3b82f1271` | C green |
| 42213599513 | throwaway: exactly E's change, on top of D2 | Green, with every test name pre-main has (below), so no expected value changes for E |
| 42216233654 | home-location-out `9dab3e3c8` | **The guard red before the refill:** 3 tests, all in `TopoNightStreetSwitchTest`, "a short directory would make every check below vacuous expected:<81> but was:<75>". That test reads the topo-night folder and pins its sample size. Clean compile, and the other 6787 passed. A stop, ruled by the owner (below) |
| 42223533171 | throwaway: H with the old sample's colour totals | **Red:** exactly the 3 `TopoNightStreetSwitchTest` tests that load the OpenTopoMap sample, each "distinct colours summed per tile zoom, against Pillow's count expected:<17832> but was:<17319>". The test's own reading equals the independent count. Clean compile [read: the build log has no `e:` lines and no failed compile task] |
| 42223536949 | home-location-out `17dea9d5d`, the code head | Green. JUnit by name against pre-main: 0 missing, 0 status changes, 2 new (`MadeUpPlaceTest`) |

- **C's red check.** It is a separate commit in a separate worktree, so the forward change was never edited. The
  forward change is present at the head [observed: grep for the new countdown].
- **All five clocks keep their meaning at P** [observed: a port of the app's `SunCrossing`, run at the fix each class
  uses]:
  - the line is hidden at the day and noon clocks;
  - it is shown at the dusk and evening clocks;
  - it is shown after sunset and before dark at the after-sunset clock.
- **E needs no tests-first commit**, because no expected value changes. The assertions near the two-place points read
  positions relative to them: a fix so many metres south, a needle at 0 degrees, elevation from heights [read].
  So a move of under half a unit in the last place reaches none of them. This is flagged as a check that passes
  identically before and after, for the reason just given.
- **Text that failed after B: none.** B was green, so no distance, bearing, sunset or clock string needed recomputing.
- **JUnit by test name**, against pre-main's own push run 42202397416 on `6cfcd8d6c` [observed: JUnit XML from both]:
  - pre-main has 6836 test cases, and runs 42210999406, 42213599513 and 42223536949 have 6838;
  - 0 are missing and 0 changed status;
  - the 2 new ones are both in `MadeUpPlaceTest`;
  - the main suite is 6788 passed and 24 skipped on pre-main, and 6790 and 24 here; OlderSdks is 24 on both.
- **Live checks, run from the laptop** [observed]: `verify-usgs-basemap.sh` passes at P. P's tiles are served at zooms
  12, 15 and 16, and zoom 17 has none, as documented. `verify-lichen-exclusion.sh` passes at P: both endpoints honour
  the parameter, and the typo control matches the baseline.
- **Starburst findings** [observed: haversine over the table]: after the translation, no step changed by as much as
  1 mm, and the path length and first-to-last distance are unchanged at the precision shown.

## Counts, before and after

The final search uses the private file's box rule, through the scanner, and its starting grep:
- lat and lon as pairs, with 2-decimal pairs, comma decimals and both signs;
- grid references, full and easting-only;
- tile numbers at zoom 13 or more, in text and in file names;
- UTM and scaled-integer forms;
- every tracked text file at the tip.

These are its own counts [observed].

| Kind | pre-main `6cfcd8d6c` | head `d375a3191`, merged with pre-main `d02d2e74a` |
|---|---|---|
| (a) pairs in the box | 242: test 196 (103 files), docs 39 (11), source 5 (2), scripts 2 (2) | **0** |
| (a) single values in range | 22: test 10, docs 11, source 1 | 14, all classified below |
| (b) grid references | 5: docs 4 (2 files), test 1 | **0** |
| (c) tile numbers in text | 14: docs 6, scripts 6, other 2 | 2, both false positives |
| (c) tile numbers in file names | 7 | **0** |
| Values whose partner coordinate is outside the box | 129 | 133 |
| Files with any (a) to (c) hit | 133, plus the 7 tile-named files | 11, all classified below |
| Starting grep, latitude / longitude lines | 174 / 185 | 0 / 1 |

**Reconciliation of the head's counts** [observed]:
- The 14 single values are 10 false positives: pixel bounds in UI dumps 6, test-report timestamps 2, a height in
  metres 1, and a dp range 1. The other 4 are latitudes whose partner, on another line, is outside the box.
- The 2 tile hits are taxon ids in the species index.
- The 11 files are exactly the files holding those 16 hits.
- The one starting-grep line is one of the ruled exceptions below.

These figures are this scanner's, at `6cfcd8d6c`. Phase 1's figures were taken at `779599c3f` with its own
classifier. I have not reconciled the two line by line [not verified]. Two pairs came in with #495 after Phase 1.

## The topo-night sample, rebuilt (the owner's rulings)

- **The stop.** F removed the 7 tiles on the owner's "Remove them". That turned `TopoNightStreetSwitchTest` red:
  it reads `docs/audits/data/2026-09-30-topo-night/` and pins its sample size against a vacuous pass [read:
  `TopoNightStreetSwitchTest.kt:72-79`]. Nobody had known a test read the folder.
- **The owner, verbatim:**
  - to "What should replace the 7 you removed?": **"Replace the whole set (Recommended)"**;
  - to the renders and their tiles: **"Remove them (Recommended)"**.
  - The city was the planner's choice: downtown Asheville, North Carolina, at Wikipedia's city point [read: the
    Wikipedia article, 2026-11-06].
- **The fetch** [observed]:
  - 96 requests, made once, one at a time, 1.5 s apart, with a User-Agent naming the project;
  - 81 OpenTopoMap tiles (a 3 x 3 block at each tile zoom 7 to 15) and 15 OSM Standard tiles (the centre tile at
    each zoom, and the 2 x 2 block at zooms 8 and 10), the same zooms and counts as before;
  - `fetch_tiles.py` and `fetch_osm.py` now choose exactly these files [observed: each script's own choice,
    computed without fetching, equals the files present];
  - `SHA256SUMS` verifies [observed].
- **Totals, recounted independently with Pillow:** 17,319 distinct colours summed per zoom for OpenTopoMap and
  3,637 for OSM. The method was first checked on the old sample: it reproduces the test's old pins, 17,832 and 3,652
  [observed]. The test's own reading, from the red run's message, is 17,319, and the green run passes with 3,637 for OSM.
- **Limits, unchanged** [observed: the folder's Python mirror of the shader, then CI]:
  - The darkness limit is still read from the sample (the zoomed-in means' maximum plus 0.03). It is 0.375 on the
    new sample.
  - Every checked value is also under the original sample's 0.345: Street below 9.5 at 0.12 to 0.15, and the
    crossfade at most 0.232.
  - Labels clear the ground by more than 0.15 at every zoom.
  - The sample-size guard keeps 81 and 15.
- **Removed:** the 24 render images (17 views and 7 sheets and switch pairs) and their 43 render-only tiles (30
  OpenTopoMap and 13 DEM). The render scripts and the needed-DEM list point at the same Asheville point. They were
  not re-run, so no tiles were fetched for them.
  - The measurement's docs keep their figures, marked as made on files since removed. Their place references and
    town names get the marker.
  - The audit doc has a dated note with the rebuilt sample's figures.
- **Privacy check** over the folder, the test and the audit doc, file names included: 143 files [observed]. It
  found 0 hits for the private patterns and 0 for the starting grep. No tile name from the old sample is left, as a
  file or as text, anywhere in the tree.

## Exceptions, by kind and count only, for privacy

- **Ruling 4, values whose partner coordinate is outside the box: 133 at the head**, from 129 at pre-main.
  - 40 have three or more decimals, from 40. They are the same 40 values [observed: per-file counts equal, and only
    2 lines reworded around them].
  - 93 have two decimals, from 89.
  - In addition, there are the 4 single latitudes above, 4 at pre-main too.
  - The planner chose to list these by kind and count only, for privacy. That replaces "by file and count".
- **Derived values left, the planner's (B):** 7 tile-count lines for areas at the old place.
- **The topo-night folder's remaining files from the public town centre** (the zoom-8 to 12 tiles, checksum lines and
  needed-tiles list) were first left under (B). They are now **replaced**, with the whole sample (above).
- **Fixed time strings in formatting tests**, about 20 lines, are left as test data on the planner's word. I found no
  evidence that any was computed from the old place [read: the ones checked are fixed strings, not computed].
- **(d) nearby names:** these are out of scope. I did not recount them; Phase 1's figure is 31 lines in 17 files, at
  `779599c3f`, from a list not passed on [not verified at this head]. This branch changed no place name, except the
  USGS script's test-location label, which now names P. Phase 1 counted no (d) names in scripts.

## The derived-value sweep (planner's instruction, after C)

Reported by kind and count [observed]:

| Kind | Found | Done |
|---|---|---|
| Sunset time at the owner's home, to the second (1 device record) | 1 | Marker (D) |
| Clock times observed at the owner's home: sunset, dark, and times tied to them | 29 lines in 5 docs, 1 test comment | Replaced with `h:mm` and a note in each doc; the test comment reworded (D) |
| Elevation readings at the owner's home | 1 doc, 1 source comment, and the starburst table's 17 altitudes with 3 sentences | Marker, a neutral rewording, and altitudes given from point 0 with a note (D, F) |
| Tile number below zoom 13 tied to the home | 1 source comment | Reworded (D); the USGS script's zoom 12 moved to P with the script (F) |
| Tile counts for areas at the old place | 7 lines | Left, the planner's (B) |
| Distances or bearings to public points, other grid references, time zone | 0 | Nothing to do |

I first reported 26 lines and 2 elevation readings to the planner. Applying the rule found 3 more lines in the same 5
docs and the starburst altitudes. All are treated under the same ruling.

## Device check

None. Nothing a user sees changed, and no claim here rests on hardware [read].

## Disclosure

### Confirmed and inferred

- Confirmed by runs: each red and green above, the name-by-name JUnit comparison, both live checks, and the final
  search's counts.
- Confirmed by my own recomputation: the tile estimate, P's grid reference, each clock comment at the fix its class
  uses, and the starburst steps.
- [inferred] The clock times replaced in the docs were taken at the owner's home. Some, at the old test fixture, were
  headless runs at the same point, so the treatment is the same.

### Could not determine

- **Copies on GitHub's side.** Pull-request refs, forks and caches cannot be checked from here. The red runs' logs and
  artifacts (42207228535 and the others above) carry the old values in their failure messages, as git history does.
  The planner chose to keep run 42207228535.
- Binary files: the scanner skips them. This branch adds none, and Phase 1 checked the images and databases.
- (d) at this head, as above.

### Premises that were wrong

- The private file is a description, not `grep -f` patterns (above).
- The earlier Hazel's worktree was not clean (above).
- "If no CI run exists on 1a0ce0ed1": one did, 42207228535 on a throwaway ref, and I used it.
- Ruling 1's "keeps each value's decimals" and "a single translation to P" cannot both hold for every value. The
  planner ruled (a): values with two places keep their places, and longer values stay exact at P. This replaced the
  planner's own first wording, which I had checked against the code.
- Phase 1's clock method, keeping each offset, was carried out to the second. It was replaced by the planner's (1b).
- My own first sweep counts were low: 26 lines became 29, and the starburst altitudes were missed (above).
- I told the planner the renders were 17 images; there were 24 (17 views, plus 7 sheets and switch pairs of the same
  views). All 24 went under the owner's "Remove them", which named the images, not a count.
- No one, Phase 1 included, knew a unit test read the topo-night folder (above).

### Decided beyond scope

- Clock times got same-length `h:mm` placeholders rather than the long marker, so that measured character counts in
  tables still mean something. One user-set "Back by" time is kept. The heads-up time in the planner handoff is
  replaced too, since it follows from sunset.
- The UI report's format examples quote the source comments, so they use P like those comments do, not the marker.
- The starburst altitudes are given from point 0, and the float32 sentences are reworded to match.
- The USGS script's labels and comment now name Mount Hood (P), and the lichen script's comment says what its search is.
- C also reworded run-history comments in 2 test files A had not touched. They named the old place's sunset.
- The rig's two "fixed step from MadeUpPlace" comments now say "a two-place point near MadeUpPlace", since E moved it.
- The topo-night note is a new file, `REMOVED.md`, in that folder. It was rewritten after the rebuild, and no tile
  numbers appear in it.
- How the OSM extras were chosen: the 2 x 2 block around the tile corner nearest the centre, which is how the original
  extras fell. `fetch_osm.py` now fetches them too.
- The fetch User-Agent names the project's URL. The original scripts named the owner's email, and I did not send it.
- In the renders README, a state name is kept (the made-up place is in the same state), and a regional range is
  reworded to "the foothills". The town names and the centre get the marker.

## Clean-up and open items

- **Throwaway refs, all deleted:** `hlo-throwaway-red-a` (the earlier Hazel's), `hlo-throwaway-red-c`,
  `hlo-throwaway-green-c`, `hlo-throwaway-discover-e` and `hlo-throwaway-red-h`. Their runs are kept.
- My three temporary worktrees are removed. The earlier Hazel's worktree, `~/.cache/forager-wt/home-location-out`, is
  untouched, with its 18 uncommitted files. Whether to remove it is the planner's call.
- The private scratch folder is deleted at the end of the dispatch; the hand-back message confirms it.
- **Open items:**
  - the PR's own run on its final head;
  - the planner's sweep of RECORD.md and prompts/preserved, and pre-main merged into records-after-173;
  - the branch-deletion dispatch;
  - main, by promotion.
  - `TripNavigateTest.kt` is shared with nav-card-kind-line: this branch changes `:97`, theirs `:390-430`. Whichever lands
    second merges pre-main, never a rebase.
