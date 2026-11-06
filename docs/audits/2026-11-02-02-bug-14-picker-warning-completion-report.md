# Known bug 14's warning in the Offline Maps area picker, decided by the map server: completion report (PR #496)

Dispatch `prompts/preserved/2026-11-02-02-bug-14-picker-warning.md` (on `origin/records-after-173`), with:
- Amendment 1 (A2, C1 to C3; RECORD -2136, -2137);
- Amendment 2 (A3 to A6, B2, C4, C5; RECORD -2144);
- Amendment 3 (A7 to A9, B3, C6, C7; RECORD -2150, -2151);
- the Phase 1 and Phase 1b reports (`prompts/preserved/2026-11-02-02-phase1-report.md`, `-phase1b-report.md`). Phase 1b's section 5 and its plan at :197-229 are the plan built here.

Written by Hazel (Opus), 2026-11-05, in a fresh session for Phase 2, on the planner's go (#494 merged).

Each claim is marked:
- **read**: file and line at the named commit, or the named CI run's JUnit XML or log;
- **observed**: command output;
- **inferred**;
- **not verified**.

Line numbers are at **2dc67bab6**, the last code commit, unless said otherwise.

## Base

- **pre-main** was at **a0535ece1**, the commit named in the go, at the start and again before the PR (observed, `git fetch` and `git ls-remote`). It did not move.
- **records-after-173** moved during the session, from a295c406a to 9f3293a74.
  - None of the dispatch's files changed (observed, `git diff --name-only`).
  - The new RECORD lines only restate the go and the queue behind this PR.
- **The branch** is `bug-14-picker-warning`, cut from a0535ece1, in `~/.cache/forager-wt/bug-14-picker-warning`. Nothing in the planner's worktree was edited.
- **Nothing changes** in `server/`, `app/schemas` or `data/local` (observed, `git diff --stat a0535ece1..HEAD`: empty for those paths).
  - So there is no Worker change (A7) and no Room or DataStore change.
  - Nothing reads MapLibre's store (Amendment 1, C2). None of the new files imports from `map/` (read).
- **The other coders' branches:**
  - `edge-shade` (57adcd4eb) changes 31 files, and none of them is on this branch (observed, `comm` of the two `git diff --name-only` lists). `CentrePinLocationPicker.kt` and `MapFeatureBubble.kt` are untouched here (read, `git diff --stat`).
  - `trip-download-maps` is not on the remote yet (observed).

## Premises re-checked at a0535ece1

Phase 1b was written at ee0651fe0, and #494 landed between that base and this one.

| Premise | Verdict |
|---|---|
| Phase 1b's file sizes and lines | #494 changed four of the plan's files (observed, `git diff --stat ee0651fe0 a0535ece1`): `AvailabilityViewModel.kt` (8+/8-, still 790), `AvailabilityUiState.kt` (+6, 447), `MainActivity.kt` (+2, 768), `AppContainer.kt` (+2, 602). Every other planned file is unchanged, so Phase 1b's lines in them hold. |
| The Worker is the same on main and pre-main | Confirmed: `server/pmtiles-worker/src/index.ts` is blob 8896b0912 on both, and unchanged since ee0651fe0 (observed). |
| Portrait: "Pin at" is the first line under the map | Confirmed (read, `AvailabilityOfflineMapsUi.kt:354-355` at a0535ece1) |
| Landscape: "Pin at" and Download Maps are pinned under the scrolling part | Confirmed (read, `:316-324` at a0535ece1; `OfflineMapsSideBySide.kt:90` calls `pinnedActions()` after the scroll, in the controls' column) |
| Download Maps is enabled when `pinIsAPlace && !isDownloading` | Confirmed (read, `:256` at a0535ece1). It is unchanged. |
| The screen's last note's style (`usContinentalNote`): `bodySmall`, padded lg/sm | Confirmed (read, `:193-199` at a0535ece1) |
| The Trail Map's tile address | `Basemap.kt:154` (read). It is passed in by AppContainer, not copied. |
| The download's rectangle is `routeAreaFor` | Confirmed (read, `MapLibreOfflineMapRepository.kt:331-332`) |
| The live server's four answers, at zoom 11 | Confirmed, see "Live requests" below (observed) |
| A8's "the highest latitude a download allows" | **No latitude limit is coded.** The tile budget is what refuses a download (`AvailabilityViewModelOfflineMaps.kt:137-141` at a0535ece1). See "The tile cap" below. |

## Commits and PR

| Commit | What |
|---|---|
| cef5a451e | The interface, client, limits, collaborator, line and tests, over four stubs (fail first) |
| 74a555a90 | The UI test base's rig and server made private: the fail-first run's test compile refused them |
| 316dd6b41 | The four stubs replaced by the real behaviour |
| 2dc67bab6 | The portrait placement test's Name-box lookup excludes the Journal's search pill |
| this commit | This report |

PR **#496** into pre-main, open and not a draft, not merged (observed, `gh api .../pulls/496`: `draft` false, `merged` false, base `pre-main`). Its body was read back from the API and is identical to the one sent.

## What was built

### The check (A3, A4, A7, A8)
- **The interface** `TrailMapDetail` (`domain/mapdata/TrailMapDetail.kt:15-25`).
  - It answers `AllInside`, `SomeOutside` or `NotRun(reason)` (`:27-37`).
  - `NoTrailMapDetail` is the explicit "unsupported" (`:40-42`).
  - `known(area)` reads only this session's earlier answers and asks nothing (`:21`).
- **One tile's answer** (`TileDetail.kt:44-64`, A7):
  - any feature other than the land fill (`earth`) or the sea fill (`water` of kind `ocean`) is **detail**;
  - land alone, or land with sea, is **no detail**;
  - sea alone is **sea**, and counts neither way;
  - a 200 with no features at all is **unread** (`:62`; beyond the dispatch, see below);
  - bytes that are not a vector tile are **unread** (`:38-42`).
  - The fills are named once (`:33-35`).
- **The cover:** every zoom-11 tile the download's rectangle touches (`DetailTileCover.kt:12-39`). An area across the antimeridian is not covered, and says so.
- **The client** `WorkerTrailMapDetail` (`data/mapdetail/WorkerTrailMapDetail.kt`):
  - **The area's answer** (`:61-82`): any tile with no detail gives `SomeOutside`, even if other tiles failed; every tile detail or sea gives `AllInside`; anything else is `NotRun`, with counts and the first reason, never a position.
  - **A 204** is no detail (`:139`).
  - **Requests:** at most 4 out at once (`:85-117`). The first tile with no detail cancels the calls still out (`:104`), and so does cancellation from outside (`:114`).
  - **Body cap:** 1 MiB per tile (`:134`).
  - **HTTP client:** it runs on the app's JSON client (`AppContainer.kt:356-362`).
- **The session cache** `TileDetailCache`: 512 answers, the least recently read dropped first. Unread answers are never kept.

### The ViewModel side (A6, A9)
- **`onOfflineAreaSettled`** (`AvailabilityViewModelOfflineMaps.kt:95-101`): with no signal it logs and asks nothing; otherwise it asks.
- **Signal coming back** asks about the last settled area again (`:86`), through the existing hook.
- **The collaborator** `AvailabilityOfflineDetail` (`ui/availability/AvailabilityOfflineDetail.kt`):
  - it waits 750 ms, and a newer area cancels it, waiting or running (`:43-57`);
  - the whole check has 8 s (`:53`);
  - an answer `known` from the cache is written at once, with no wait (`:47-50`);
  - a not-run answer writes no line, clears an earlier answer for the same area, and logs one line (`:66-75`).
- **Where it lives:** it is built inside `AvailabilityOfflineMaps` (`:44-48`), which takes the `TrailMapDetail` from the ViewModel (`AvailabilityViewModel.kt:196`, `:285`). The ViewModel goes from 790 to 793 lines.

### The screen (A1, A5)
- **The settle:** `LaunchedEffect(pinRegion, pinIsAPlace)` tells the ViewModel each settled area (`AvailabilityOfflineMapsUi.kt:170-171`), on opening, after each pan and after each radius step.
- **The line** shows only when the answer is for the area on screen and says some of it is outside (`OfflineDetailNote.kt:21`). It is `bodySmall`, in the default colour, padded lg/sm (`:22-26`).
- **Placement:**
  - portrait: directly under "Pin at" (`AvailabilityOfflineMapsUi.kt:366`);
  - landscape: pinned above "Pin at" (`:325`).
- **Download Maps:** `enabled` is untouched.

### Wiring
- The settle rides `OfflineRouteActions` (`OfflineRegionRouteLine.kt:49`, call C1), wired at `MainActivity.kt:529`. That is one line changed, plus one import.
- `AvailabilityViewModelFactory.kt:75`, `AvailabilityUiState.kt:411` (`offlineDetail`).

### Files

| File | Lines | Change |
|---|---|---|
| `domain/mapdata/TrailMapDetail.kt` | 42 | new |
| `domain/mapdata/TileDetail.kt` | 65 | new |
| `domain/mapdata/DetailTileCover.kt` | 39 | new |
| `domain/mapdata/TrailMapDetailLimits.kt` | 44 | new |
| `data/mapdetail/WorkerTrailMapDetail.kt` | 176 | new |
| `data/mapdetail/TileDetailCache.kt` | 26 | new |
| `ui/availability/AvailabilityOfflineDetail.kt` | 85 | new |
| `ui/availability/OfflineDetailNote.kt` | 34 | new |
| `AvailabilityOfflineMapsUi.kt` | 415 to 426 | +11 |
| `AvailabilityViewModelOfflineMaps.kt` | 158 to 174 | +16 |
| `AvailabilityOfflineMaps.kt` | 308 to 315 | +7 |
| `AvailabilityViewModel.kt` | 790 to 793 | +3 |
| `AvailabilityUiState.kt` | 447 to 449 | +2 |
| `OfflineRegionRouteLine.kt` | 72 to 74 | +4/-2 |
| `MainActivity.kt` | 768 to 769 | +2/-1 |
| `AvailabilityViewModelFactory.kt` | 74 to 76 | +2 |
| `AppContainer.kt` | 602 to 611 | +9 |
| `RealSearchScreenRig.kt` (test) | 379 to 409 | +32/-2: defaulted parameters (call C2) |

New tests: `TileDetailReaderTest`, `DetailTileCoverTest`, `TrailMapDetailCapGuardTest`, `WorkerTrailMapDetailTest`, `TileDetailCacheTest`, `AvailabilityOfflineDetailTest`, `OfflinePickerDetailNoteTest` (a portrait and a landscape class), and `TestMvt`, a small tile encoder. No file passes 800 lines.

## Every user-facing string

One, the owner's words, exactly (RECORD -1951), at `OfflineDetailNote.kt:30-31`:

> Detailed offline maps aren't available here yet. A download here will be blank when you're offline.

The step path's words are A1's, unchanged:

> "The step path: Tools > Offline Maps > choose an area > the pin is south of 15.92°N > under the area, the line "Detailed offline maps aren't available here yet. A download here will be blank when you're offline." > Download still works. Pin moved north of the line > the line goes. The words are the owner's, exactly."

Its condition is replaced by A3's (the map server decides) and A4's (any part outside), and its placement is set by A5.

The log line is not on screen. It goes to logcat under the tag `AvailabilityViewModel`, as `Log.w`:
- "Offline maps detail check not run: <reason>.";
- the reasons are "no signal", "no answer within 8 s", the client's counts (for example "1 of 16 zoom-11 tiles could not be read (the first: HTTP 503), and none answered without detail"), the cap, the antimeridian, and "this build has no map server check".

## The tile cap (A8): 36

**A8 asks for** "the 24 km area's worst case up to the highest latitude a download allows". No latitude limit is coded. What refuses a download is the tile budget, 6,000 tiles on an empty budget (`OfflineMapRepository.TILE_COUNT_LIMIT`). I read A8 as the latitude at which the budget stops allowing the 24 km download.

**The derivation:**
- **Where the 24 km download stops fitting:** at about 53.4° of latitude (observed in a Python sweep in scratch; read, `TrailMapDetailCapGuardTest`, run 42158393272). The sweep moved the centre in 0.05° steps across one zoom-15 tile, with the app's own estimate.
- **The worst cover up to there:** the 24 km area's worst zoom-11 cover up to that latitude is **36** tiles.
- **Every other area the budget allows:** across every radius, every half degree and 36 placements each, none covers more than 30 tiles.
- **So the cap never stops the check for an area that can be downloaded.**

For comparison, the 24 km area's worst cover is (read, `DetailTileCoverTest`):

| Latitude | Worst cover |
|---|---|
| 16° | 16 |
| 45° | 25 |
| 53° | 36 |
| 70° | 81 |

It is 540 at 85° (observed, the Python sweep).

**Where it is recorded and guarded:**
- The cap and its reason are in `TrailMapDetailLimits.kt:14-25`.
- `TrailMapDetailCapGuardTest` re-derives it from the budget and the radius ceiling, so moving either without the cap fails the build.
- Over the cap, the check is not run and that is logged (`WorkerTrailMapDetail.kt:64-68`).

**A finding, not acted on:** `OfflineMapRepository.MAX_RADIUS_KM`'s comment sizes the ceiling for an archive whose northern edge was 49.60°. The archive now reaches far north of that (RECORD -1514), so a 24 km download is refused by the budget from about 53.4°. The user sees the existing red "exceeds your remaining budget" line there. That is the radius ceiling's concern, outside this dispatch.

## Live requests (read only): 4

Four tile requests at zoom 11, to the Trail Map's tile address, with a descriptive User-Agent (observed). Every answer was gzip-encoded. Bodies were decoded as data in scratch, which is now deleted.

| Kind of place | Answer | Layers |
|---|---|---|
| Inside the coverage | 200, 9,066 bytes decoded | boundaries, earth, landuse (66), places, pois, roads (14), water (3) |
| Outside the source data, inland | 200, 57 bytes | `earth` only |
| Outside the source data, on a coast | 200, 1,268 bytes | `earth`, and `water` of kind `ocean` |
| Open sea inside the archive's box | 200, 75 bytes | `water` of kind `ocean` only |

The four classes A7 names hold at zoom 11. No coordinates or tile numbers are recorded here.

## Failing first

- **Run 42154496302 (cef5a451e).** The test compile failed with two errors, so **no results are cited**:
  - `OfflinePickerDetailNoteTest.kt:71:19` and `:74:19`: "'protected (in OfflinePickerDetailNoteTests)' property exposes its 'internal' type".
  - The main code compiled, and the release compile job passed.
  - Fixed in 74a555a90, one attempt.
- **Run 42155245660 (74a555a90),** filtered to the eight new classes. The compile log is clean (read: 4,504 lines, `compileDebugKotlin` present, no `e:` lines). The JUnit XML holds **63 tests, 35 failures, 0 errors**. Each failure is a stub's:
  - the client and classifier tests expecting no detail got `Detail` or `AllInside` (10);
  - all 9 ViewModel tests found nothing asked, no log line, or no answer;
  - all 16 UI tests failed: "is not displayed", "the server was asked for the opening area's cover ... but was <[]>", "positive control: the check was reached and logged", or no note to measure.
- **Not shown to bite by the stubs:** the 28 tests of things no stub touched. These are the cover counts, the cap guard, the cache bound, the classifier's detail cases, and the client's limits (the cap, the antimeridian, 4 in flight, cancellation, unread statuses, the cache).
  - None has a revert check of its own; R2a and R2b cover the classifier's and client's no-detail paths.
  - The cap guard re-derives a constant, and it fails if the budget moves (inferred from its code; not run against a moved budget).

## The forward runs

- **Run 42155972020 (316dd6b41), unfiltered.** 6,768 tests, **1 failure**, 24 skipped.
  - The failure was the portrait placement test's own lookup: "Expected exactly '1' node but found '2' nodes that satisfy: (SetText is defined)". The second node was the Journal's search pill (`active-search-summary`).
  - The Street-deleted job failed on the same one test.
  - This was a fault in the test, not the code. Fixed in 2dc67bab6, one attempt.
- **Run 42158393272 (2dc67bab6), unfiltered.** **Every job green** (read).
  - Build job: compile log clean (5,192 lines). The JUnit XML holds **6,768 tests, 0 failures, 0 errors, 24 skipped**, in 981 files.
  - **Reconciled against pre-main's own run 42150726011 on a0535ece1:** 6,705 tests in 973 files, with the same 24 skips. By (class, name), exactly 63 tests were added, in the 8 new classes (19, 9, 8, 8, 8, 5, 3, 3), and **none removed** (observed).
  - The Street-deleted job: 975 suites, 6,738 tests, 0 failures, 0 errors, 24 skipped (read, its count step).
  - Lint, release compile, map style, route cutter and Worker: green.

## Revert checks

**How each was run:**
- Each is one edit to a saved copy of the forward file, committed through a temporary index onto a scratch branch from 2dc67bab6, and pushed. My worktree was never edited.
- Each scratch commit differs from 2dc67bab6 by one line in one file (observed, `git diff --shortstat`).
- Each ran filtered to the eight new classes.
- The reader refuses to cite a run whose build log is empty or holds a compile error. Its first version did not: it read an empty log (`gh run view --log` on a run still in progress) as "no compile errors". The empty log was noticed, and the reader was fixed before any result was cited.
- **Predictions** were written to scratch at 03:46Z, before any revert run finished. Every revert's failures matched them, by name and by count.
- **After all five:** the worktree's four forward files are byte-identical to the pushed branch, and HEAD is 2dc67bab6 (observed, `cmp` and `grep` of each forward line).

| Revert (run) | The one edit | Compile log | Failures (of 63) | Messages, each specific to its edit |
|---|---|---|---|---|
| R1 "the check never runs" (42158470393) | `offlineMaps.detail.ask(area)` commented out (`AvailabilityViewModelOfflineMaps.kt:100`) | clean, 4,451 lines | 21: 7 ViewModel and 7 UI tests in each of the two layouts | "the download's own rectangle is asked about expected:<[...]> but was:<[]>"; "positive control: the line shows at the start"; "... is not displayed!". The no-signal tests pass, as predicted: that branch still runs. |
| R2a "a 204 reads as detail" (42158472955) | `204 -> TileDetail.Detail` (`WorkerTrailMapDetail.kt:139`) | clean, 4,422 | 14: 4 client and 5 UI tests in each layout | "expected:<SomeOutside> but was:<AllInside>"; "... but was:<NotRun(reason=1 of 16 zoom-11 tiles could not be read (the first: HTTP 503), and none answered without detail)>"; the UI's 204 cases. The land-only cases pass. |
| R2b "land only reads as detail" (42158475781) | `land > 0 -> TileDetail.Detail` (`TileDetail.kt:59`) | clean, 4,433 | 6: 2 classifier, 2 client, and the land-only UI test in each layout | "expected:<NoDetail> but was:<Detail>"; "expected:<SomeOutside> but was:<AllInside>". The 204 cases pass. |
| R3 "it asks with no signal" (42158478608) | `if (noSignal()) {` to `if (false) {` (`AvailabilityViewModelOfflineMaps.kt:96`) | clean, 4,394 | 4: 2 ViewModel, and the no-signal UI test in each layout | "expected:<[]> but was:<[GeoBoundingBox(...)]>"; "positive control: not asked without signal"; "no line" |
| R4 "the line ignores its answer's area" (42158481450) | `answer.area != area ||` removed (`OfflineDetailNote.kt:21`) | clean, 4,398 | 4: the two pan tests in each layout | "the line goes as soon as the pin moves, before the new area is answered"; "the line goes as soon as the pin moves" |

The remaining jobs of the R1, R2b, R3 and R4 runs were cancelled once their build jobs had reported, to free shared CI. Their results come from the build job alone.

## CI on the PR

The PR's own `pull_request` run on this report's commit is the last real commit's run. Its result is in the message to the planner (observed after this file was written). This file cannot record a run that starts when it is pushed.

## Device check, for the planner (S22, cheapest first)

**Setup:**
- Install the debug APK from the PR's CI run (artifact `app-debug-apk`). Read its version on the About screen.
- Logcat: `adb logcat -s AvailabilityViewModel:W okhttp.OkHttpClient:I`. The debug build logs each tile request.
- Tile paths and positions in the log are not to go on GitHub.

| Step | Do | Pass | Evidence |
|---|---|---|---|
| D1 | At home, with signal: Tools > Offline Maps. Don't touch the map. | Within about 3 s, no line under "Pin at". Logcat shows requests to `/us/11/` for the opening area, 4 to 25 of them, and no "detail check not run" line. | A screenshot, and the request count |
| D2 | Pan to the area of RECORD -1950's check (south of the archive's bound). | About 1 to 2 s after the pin stops, the line reads exactly "Detailed offline maps aren't available here yet. A download here will be blank when you're offline.", directly under "Pin at". Download Maps is enabled, and a tap opens "Download this area?" (then Cancel). | A screenshot |
| D3 | Pan to an area Phase 1b's section 1 found answering land only (its requests 2-4 or 19). | The line shows. This is the case a latitude rule would have missed (A3). | A screenshot |
| D4 | Pan back to the home area. | The line goes when the pin moves, and does not come back. | A screenshot |
| D5 | Pan to D2's area, then turn the phone to landscape. | The line is pinned above "Pin at", wholly in view; Download Maps is reachable. Note how many lines the words take. | A screenshot |
| D6 | Airplane mode on, wait 10 s, then pan to D2's area. | No line. Logcat: "Offline maps detail check not run: no signal." | The logcat line |
| D7 | Still over D2's area: airplane mode off, wait about 40 s (the 30 s return delay), and don't move the pin. | The line appears with no pan. **An observation, not a gate:** this rides the region list's existing signal-return hook. | Screenshots with times |
| D8 (observation) | In a dense covered area at 24 km, count the requests for one placement, then pan a little within the same tiles. | Up to 36 requests, then none for the small pan (the cache). | Counts only |

## Not tested, said plainly

- **Real hardware:** nothing has run on a phone. That covers:
  - real timing;
  - the real server through OkHttp's gzip;
  - real cancellation of a blocked call. The fake server's interceptor cannot be unblocked by `Call.cancel()`, so the tests show only that nothing more is *started* after an early stop or a cancellation;
  - real insets, and how many lines the words take in landscape on the S22 (D5).
- **The signal-return hook from the screen:** tested at the ViewModel function (`AvailabilityOfflineDetailTest`), not through `OfflineRegionsSection`'s existing `LaunchedEffect`. The rig provides no `LocalNoSignal`, so that hook cannot fire there. The hook itself is unchanged.
- **The data cost** of a dense covered area at the largest radius (D8).
- **A remote covered place whose zoom-11 tile holds only land and sea** would show the line falsely (Phase 1b, section 10, item 5). Not seen, and not ruled out.
- **The cap guard against a moved budget:** never run with the budget moved (inferred to fail from its code).

## Decided beyond the dispatch, to accept or reject

1. **An answer the cache already knows is written at once, with no 750 ms wait** (`AvailabilityOfflineDetail.kt:47-50`). Phase 1b told the owner the cache stops the line blinking while panning (its section 10, item 1). With the wait applied to cached answers too, the line would still blink off for 750 ms on each pan inside the same tiles. A cached answer costs no request, so the wait, which exists to limit requests, is not applied to it.
2. **The cache lives in the client,** for the app process, not in the ViewModel collaborator as Phase 1b's section 3 said. Tile answers are the client's business; the collaborator sees areas only. The effect the planner accepted is the same: a rebuilt map is read on the next app start.
3. **The wait is in the collaborator, not the panel** (Phase 1b section 7, step 1 had the panel wait). The panel reports every settle; the ViewModel side waits and decides, so the timing is tested on virtual time.
4. **The limits object is in `domain/mapdata/`,** not `data/mapdetail/` as Phase 1b's file list had it. The screen's side reads the wait and the time-out, and nothing under `ui/` imports from `data/` (observed, `grep`).
5. **A 200 with no features is unread:** no line, logged. A7 names no class for it.
6. **Any feature in the `earth` layer counts as the land fill,** whatever its kind; on the sea side only `water` of kind `ocean` counts. The live tiles carried kind `earth` only.
7. **On a not-run answer, an earlier answer for the same area is cleared** (A6: no line). Answers for other areas are left, since they never show.
8. **The cap of 36** read from the tile budget (above), where A8's "the highest latitude a download allows" names no coded limit.
9. **The rig wires `routeActions` for every test that uses it** (call C2). With its default of no signal, the settle only logs to the rig's discarding log, so the other tests see no change. Their counts are unchanged: 6,705 tests carried over, none removed.

## Premises that were wrong

- **"The 24 km area's worst case up to the highest latitude a download allows" (A8).** No such latitude is coded. The budget sets it, at about 53.4°, which is why the cap is 36 and not a far-north figure.
- **`OfflineMapRepository.MAX_RADIUS_KM`'s comment** (an archive ending at 49.60°) is stale for the current archive. Reported above, not changed.
- **The scratch folder.** Amendment 1 names `/mnt/work/scratch/bug-14-picker-warning/`, and the Phase 2 go names `/mnt/work/scratch/bug-14/`. The later one was used. The folder is deleted at the end of the session.

## Stops

None. No new words, no colour, and no placement beyond A5 were needed. No other coder's file was touched. No symptom took two attempts: each of the two test faults was fixed at the first attempt.
