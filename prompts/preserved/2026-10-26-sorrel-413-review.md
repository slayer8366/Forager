# Review of PR #413: step 6 PR 3, the waypoint route on the map and in the HUD, with Amendment 1's item C

Sorrel Tally (reviewer, Fable 5.1), fresh session, 2026-10-26, written 11:24 UTC. For Rowan Waymark.
Read-only on code; no build on the laptop, no device, nothing pushed but this file (branch `sorrel-413`, off pre-main
`c1deeef85`). Hazel's S22 results were not read.

**Reviewed:** PR #413, branch `waypoint-route-on-map` @ `a00742ffb`: `b1a13c623` (tests over stubs), `daf529d0a`
(the build), `6bbb69c7a` (item C tests over a stub), `a00742ffb` (item C), off pre-main `c1deeef85` (#412 merged).
Against dispatch `prompts/preserved/2026-10-26-11.md` with Amendment 1 (read from the PR branch), RECORD -1652, -1653,
-1665 to -1669 (`records-after-173`, `RECORD.md:28099-28291`), the step 6 report
`docs/audits/2026-10-25-03-waypoint-along-trails-report.md` §3-§4 (`waypoint-trails-report`, `079666037`), and my
reviews of #409 (`sorrel-409`), #412 (`sorrel-412`) and the #412 re-check (`sorrel-412-recheck`, `00aa57876`).

Every claim is marked **read** (the file at the hash named), **computed** (from a run's artifacts or a command), or
**inferred** (reasoned from code, not seen running). Line numbers are at `a00742ffb` unless another hash is named; app
paths are under `app/src/main/java/com/zynergylabs/forager/app/`.

## 0. Baseline

| Claim | Checked | Result |
|---|---|---|
| PR head `a00742ffb`, base pre-main, four commits as named | `gh pr view 413`; `git log c1deeef85..origin/waypoint-route-on-map` | matched: `MERGEABLE`, `CLEAN`, open; exactly those four, linear (read, computed) |
| pre-main is `c1deeef85` | `git log -1 origin/pre-main` | matched; **nothing has landed on pre-main since the base** (computed) |
| 30 files, +1,367 −35; 20 main, 10 test | `git diff --stat` | matched (computed) |
| The eleven runs are on the commits named | `gh run view` × 11 | fail-first `b1a13c623`; r1-r7 on `waypoint-route-on-map-revert-r1..r7` heads `82cde0037`, `046e36060`, `827a2143e`, `b0f4bef0c`, `dc78f4908`, `84aa42b7e`, `13f53e0cd`; item C fail-first `6bbb69c7a` (`pull_request`); forward `a00742ffb` (`pull_request`, success); retry revert `43ceddd62` on `revert-waypoint-retry` (read) |
| The eight revert branches | `git ls-remote` | **all gone from the remote**; each commit read through `gh api …/commits/<sha>`: one commit, one file, ±1 line, parent `daf529d0a` (r1-r7) or `a00742ffb` (retry) (read) |
| Tests unchanged between each fail-first and its build | `git diff -- app/src/test` | `6bbb69c7a..a00742ffb` empty; `b1a13c623..daf529d0a` is **one file, `BRouterTrailRouterTest.kt` +2 −1**: the expected value at `:106-107` gaining `squareHasNoFile = true`, the one change the owner approved (-1668), which the stub commit could not carry since its `EndOutsideData` was still the old shape (`TrailRouter.kt` is not in the stub diff) (computed) |
| The fail-first ran the tests over stubs that change nothing visible | `git diff c1deeef85 b1a13c623 -- app/src/main` | 8 files: a `WayToStray` that always says STAY, an `AvailabilityWaypointRoute` that only clears, the state field, the ViewModel wiring, `ReturnRoute.Straight` with `aim = null`, `PART_STRAIGHT` declared and unused, `wayToWaypointNoteShortText` returning the whole note; nothing produces a `Straight` in that commit (read) |
| Item C's fail-first ran over the build's `AvailabilityWaypointRoute` | `git diff daf529d0a 6bbb69c7a` | `WayToRetry.kt` (a stub, `judge` always false) and the two test files only (computed) |
| All eleven compiled and ran the test task | each run's build-job log | 0 ` e: ` lines, 0 `Kotlin … FAILED`, one `testDebugUnitTest FAILED` with a `tests completed` line in every red run; three logs came back empty on the first fetch and were re-fetched through the jobs API before being cited (computed) |

No mismatch.

## 1. Prediction (written to my scratch, stamped 11:19Z, before any JUnit XML was read; scored in §9)

Tainted in part: the log scan had already shown me the `N tests completed, M failed` lines for seven of the eleven runs,
-1669 says "19 of 23", the PR body says 5,644. So the **counts** were not predictions; **which tests and what message**
were. Fail-first: 19 of 23, the four passing being the VM "no trail routing given" case, the screen arrival case, and the
two existing layers tests; r1 the stray test on "the same stray: spent"; r2 the VM stray test on "a second route"; r3 the
screen swap test on "no straight dashed line"; r4 three screen cases on "by trail" vs "straight" and the empty note;
r5 the layers test and the screen swap on `[track, trail, track]`; r6 the note test and the screen note case; r7 "no
passed line"; item C fail-first 7 (`WayToRetryTest` 4, `RetryTest` 3); forward 5,644 / 0 / 24; retry revert the three
`RetryTest` cases.

## 2. The owner's step path (dispatch "Change" 1-8), traced through the code

| Step | Where | Holds? |
|---|---|---|
| 1. Bubble shows Navigate | untouched (not in the diff) | yes |
| 2. Navigate > today's straight line while the route is worked out | `AvailabilityViewModel.kt:388-394`: the id collector runs the line's `follow` and the route's side by side in one `coroutineScope`; `AvailabilityWaypointRoute.follow` clears `waypointRoute` (`:79`) and the first fix starts a search (`:114`, `retry == null`); until it returns `waypointRoute` is `null`, so the cards hand the HUD `null` (`CompactMapPortraitNavigationCard.kt:124`, `…Landscape…:169`) and the slot draws the straight line (`CompactMapMapSlot.kt:68-70`) | yes (read); pinned by `AvailabilityViewModelWaypointRouteTest` "shows the straight line while…" (a gated search) |
| 3. Swapped **without a blank frame** for three parts, "by trail", needle along the line | the search's result writes `line = found` then `show()` → one `updateState` (`:149`) carrying `hud = Ahead(lookahead, routeMeters)` and the three-part `RouteLine` (`:145-147`); the slot reads `uiState.waypointRoute?.line` and hides the straight line in the **same** expression (`CompactMapMapSlot.kt:68-70`), and the cards read `uiState.waypointRoute?.hud` from the same `uiState` the screen collected (`AvailabilityScreenWaypointRouteTest.kt:118`, `collectAsState`) | yes: one state write, one snapshot per composition, so no frame can hold neither line (**inferred** from Compose's snapshot model; the Robolectric tests run to idle and cannot see frames). The needle: `NavigationReadout.kt:88` aims at `route.lookahead` for `Ahead`. Pinned by the screen test (route.straight null, `[straight, trail, straight]`, "by trail", 600 m) |
| 4. Walk it > counts down | `show()` places on each fix with `placeOnWayHome`/`wayHomeAhead` (`:135-136`), the way home's own functions | yes; pinned by the VM "counts the figure down" case |
| 5. >50 m off > re-route once per stray, 45 m re-arms, **quiet** | `WayToStray.judge` (`:23-32`), §3 below; `startSearch(here)` on REROUTE (`:122`). Quiet: `AvailabilityWaypointRoute.kt` imports no alert type; no `AlertDelivery` is reachable from any `ui/availability` file (grep, computed); the only readers of `waypointRoute` in main are the two cards, the slot, the state and the collaborator (grep) | yes (read) |
| 6. Arrival within 15 m (or 2× accuracy), **on the straight distance** | the HUD: `arrivedAtStart(liveFix, target, now)` before any route branch (`NavigationReadout.kt:112`); the map: `arrivedAt` is the screen's, unchanged, and `aheadCollection(line?.takeIf { arrivedAt == null })` ends the trail line (`RouteHomeLayers.kt:133`) | yes; pinned by the screen arrival case (16 m not arrived, 14 m "Arrived", ring at the waypoint, ahead source empty, navigation still on) |
| 7. Back or X > ends; a paused return picks up | `onStopWaypointNavigation` (`AvailabilityViewModelWaypointNavigation.kt:43-49`) nulls the id → `collectLatest` cancels both followers and `follow(null)` clears both lines; `returnLeg.resume()` unchanged | yes; pinned by the screen X and Back cases (`waypointRoute` null, no HUD, no line) |
| 8. Outside an area, or no route > straight line, "straight", the note; short forms | `ReturnRoute.Straight(note)` (`NavigationHudReadout.kt:110`) is read as `null` is for aim, distance and kind (`NavigationReadout.kt:87, :113, :123`; `ApproximatePositionHud.kt:83`), and puts `note.text` in the status line (`:186`) with the short form as its one fallback (`:201`, `wayToWaypointNoteShortText`, `NavigationHudText.kt:82-83`: the tail dropped, the leaves-area note's short form the owner's unchanged `LEAVES_AREA_SHORT_TEXT`) | yes; pinned by `NavigationWayToWaypointNoteTest` over every note (distance, kind, needle and target identical to today's, status the note, forms `[whole, short]`) |

Foreground only: the fixes are `_uiState.map { it.liveFix }` (`AvailabilityViewModel.kt:389`), the Maps tab's own
(read). Sundown and Back by: no file under `domain/` for them is in the diff (computed).

**Every `ReturnRoute` reader handles `Straight`.** The sealed `when` at `NavigationReadout.kt:85-90` is exhaustive (it
would not compile otherwise); the four `when { … else }` chains at `:106-117`, `:121-126`, `:171-188`, `:195-203` each
gain a `Straight` branch before their `else`; `ApproximatePositionHud.kt:83` keeps the needle; `routeRetryOffered`
(`:217`) and `statusIsStraightNote` (`:206`) test `Unavailable`/`Pending` by type and are rightly false for it. No other
file in main branches on `ReturnRoute` (grep: the rest are parameter declarations) (read, computed).

## 3. The once-per-stray rule against the way home's

`WayToStray.judge` (`:23-32`) and `WayHomeWatch.judgeStray` (`WayHomeWatch.kt:158-177`), side by side (read):

| | Way home | Waypoint | Same? |
|---|---|---|---|
| Re-arm | `metersFromLine < WAY_HOME_STRAY_REARM_METERS` (45.0, `PathHome.kt:173`) → `armed = true` | `< WAY_HOME_STRAY_REARM_METERS` → `armed = true`, STAY | yes, strict `<` both |
| Not a stray | `<= WAY_HOME_STRAY_METERS` (50.0, `:170`) → return | `<= WAY_HOME_STRAY_METERS` → STAY | yes |
| Precedence | `reroutingEnded`, then `job != null`, then `!armed` | `searching`, then `!armed` | same order for the two it has |
| Spend | `armed = false` then launch | `armed = false`, REROUTE | yes |
| After a re-route result | `progress = null; lastPlace = null`, `armed` left spent | `lastPlace = null; band = NONE` (`:165-166`), `armed` left spent | yes |
| `reroutingEnded` | a re-route that falls back ends re-routing for the return | absent | **differs, harmlessly**: a straight re-route result sets `line = null` (`:177`), after which fixes take the `shown == null` branch (`:111-115`) and the stray rule is never consulted; `WayToRetry` governs instead. The way home has no retry, so it needs the flag; the waypoint has one, so it does not |
| Logging | once per `why` | once per verdict (`:124-128`) | same shape |

The distance fed in is the same function (`WayHomeLine.metersFrom`, `WayHomeProgress.kt:72`). `WayToStrayTest` pins
49/50 → STAY, 51 → REROUTE, 80 → ALREADY_REROUTED, 49 and 45.0 do not re-arm, 44.9 does, SEARCHING spends nothing; r1
(drop `!armed`) fails "the same stray: spent" and nothing else (§7).

## 4. The new straight-part map layer (N-412-1)

- **Tagging:** `partPropertyOf` (`RouteHomeLayers.kt:172-176`) is an exhaustive `when`; STRAIGHT → `"straight"`. r5
  (STRAIGHT → `"track"`) fails the layers test and the screen swap test on `[track, trail, track]`, so the tag is the
  mechanism the tests read (computed).
- **Filters:** the casing and solid layers take `all(part != track, part != straight)` (`:206-209`); dotted takes
  `part == track` (`:210`); the new layer `part == straight` (`:211`). A track-home line has no `part` property, and the
  style spec's `!=` treats values of different runtime types as unequal, so `null != "track"` and `null != "straight"`
  are both true and the solid layer still draws it, as the old single `neq` did (inferred from the style spec; the
  forward run's `RouteHomeWayHomeLayersTest` covers the feature side only; the layer side is device-only).
- **Paint order:** passed < casing < solid < dotted < **straight legs** (`:261`) < straight line (`:262`) < offline
  straight < ring above waypoints. Inside the route group, as the report's §4 "one new map layer … within that group"
  says; the group's place (above `KEPT_TRACKS`, below markers, `OverlayLayerSetup.kt:91`) is unchanged (read).
- **Look:** the straight line's own width, dashes, butt cap and azure (`:232-237` against `:238-243`), no casing (the
  filter excludes it from the casing layer). Opacity follows the ahead line's (`:276`) (read).
- **80% chrome:** does not apply, by the file's own rule at `:35-36`: "Map content, not chrome: the 80% rule for chrome
  over the map does not apply. These are MapLibre layers, not Compose surfaces, so they take no touches." The new layer
  is the same kind of thing as the four route layers beside it (read).
- **Night, offline:** night is a style reload (`MapStyleReload.kt:63`, `LaunchedEffect` keyed on `nightMode` and
  `useOfflineTiles`), which rebuilds every overlay with `palette = requested.palette` (`:133-136` →
  `OverlayLayerSetup.kt:91` → `addRouteHomeLayers(style, palette, …)`), and the new layer picks its colour from that
  palette exactly as the other four do (`:233`). The offline night recolour walks the style's own layers before the
  overlays are added and never touches them (`NightColour.kt:74`, `MapStyleReload.kt:128`). So the straight-leg layer
  carries night, day, online and offline the same way the straight line does, by construction (read); what it looks
  like is the device list's "three line kinds by day and night".

## 5. S-412R-1: `squareHasNoFile` in the primary constructor

`data class EndOutsideData(val end: RouteEnd, val squareHasNoFile: Boolean = false)` (`TrailRouter.kt:64`); the body
property and secondary constructor are gone (read). **Does anything that compared by equality behave differently?**

- **Main:** no `==`, `Set`, `Map` or `assertEquals` over a `TrailRouteFailure` anywhere (grep, computed; the re-check's
  §4 table stands). The readers are `is` checks and field reads: `FindWayHomeUseCase.kt:180`, `TrailJoins.kt:91`
  (`failure.end`), `FindWayToWaypointUseCase.kt:121-129`. The producers use the named argument
  (`RoutingDataFolder.kt:34-35`), so the removed secondary constructor has no caller; the green run is the compile proof.
- **Tests:** the one expected value that could change did (`BRouterTrailRouterTest.kt:106-107`, owner-approved). The
  other equality tests compare unflagged values against unflagged producers (`BRouterFailuresTest:18-19`,
  `BRouterTrailSnapperTest:75, :82` — the engine's "not mapped", not a missing file) and pass (14, 6, 1 in the forward
  run, computed). `FindWayToWaypointUseCaseTest:212-216` feeds both shapes in and asserts the note.
- **The mark now bites in equality:** `TrailRouteFailureTest` pins not-equal, equal, `copy` keeps it, `toString` shows
  it; the fail-first shows both fail on the old shape, "Values should be different. Actual: EndOutsideData(end=END)" and
  "copy keeps it" (computed).

The way home's files are not in the diff (`domain/wayhome/`, `routing/` apart from the test), and its suites pass
(`FindWayHomeUseCaseTest` 25, `WayHomeWatchTest` 12, `BRouterTrailRouterTest` 14, `BRouterTrailSnapperTest` 6) (computed).

## 6. Item C: the two triggers

`WayToRetry` (`WayToRetry.kt:30-48`), one instance per straight-line result, built with the note, the place searched
from and the region list as it was then (`AvailabilityWaypointRoute.kt:178`); asked at each later fix only while no
search runs (`:114`, `search == null && retry?.judge(…) != false`) (read).

- **No search per fix:** `judge` is `routeDataForPoints(walker, waypoint, regions) is Found` (`:47-48`), a rectangle
  check over the list already in state; no engine, no storage. A search starts only on a rising edge (`walkedIn =
  holdsNow && !heldBoth`, `:41`) or, for `WALKER_FAR_FROM_TRAIL` alone, past 50 m from the last ask (`:44`). Pinned by
  `RetryTest` "no search on a fix outside the area", "49 m on: no search", "counted from the last search", and the
  WAYPOINT_FAR_FROM_TRAIL walk of 400 m with one search (computed).
- **One at a time:** `search == null` gates the judging (`:114`) and the stray path says SEARCHING (`:29`). Pinned by
  the gated search in "one search at a time" (computed).
- **No loop on a mismatch:** `heldBoth` starts as `holdsBoth(searchedFrom, regionsAtSearch)` (`:36`). So a search made
  from inside an area that came back straight (the engine said far-from-trail, no path, leaves-area, unreadable…) has
  `heldBoth = true`, and walking on inside never rises; only leaving and re-entering does. A stale list that says Found
  where the repository says otherwise gives one search whose straight result rebuilds `retry` with `heldBoth = true`,
  and stops. The far-from-trail retry rebuilds from the new ask point each time (`:178`, `from`), so a standing walker
  never re-asks and a walking one asks once per 50 m, which is the figure the amendment named (inferred from the
  code; `WayToRetryTest` "a search made inside the area does not try again" and the VM 400 m case pin the inside case).
- **The 50 m retry is `WALKER_FAR_FROM_TRAIL` only:** `:44`, `note == WayToWaypointNote.WALKER_FAR_FROM_TRAIL &&`.
  Pinned by `WayToRetryTest` 49/51 m and the WAYPOINT_FAR_FROM_TRAIL no-retry case (computed).
- **The region list's freshness.** `regions()` is `_uiState.value.offlineRegions` (`AvailabilityViewModel.kt:314`),
  loaded at construction (`:370`) and rewritten by `loadOfflineRegions` (`AvailabilityOfflineMaps.kt:111-125`). Every
  path in this process that changes what is on disk reloads it: a download's success (`:201`, `:234`, after the
  download's own route step), a region delete (`AvailabilityOfflineRegionDeletes.kt:106`), and a route fetch's end
  (`AvailabilityOfflineRoutes.kt:57`, `reloadRegions()` after the pass). `fetchRoutes` has no other caller in main
  (grep), and no background worker fetches routes (grep for the store's callers). So **the coder's "a download
  finishing while navigating is taken at the next fix" holds for every way routes can arrive on this phone**, with one
  reading to add: the route fetch itself starts only on opening the Offline sheet, "Try again", or a download
  (`AvailabilityViewModelOfflineMaps.kt:62, :73, :78`); nothing starts one while navigating. The test
  (`AvailabilityViewModelWaypointRetryTest.kt:150-170`) calls `loadOfflineRegions()` by hand in place of those paths,
  so it proves the fix-after-reload half; the reload-after-fetch half is read, not run (read, computed).
- Restored-not-downloaded regions are in the list; `routeDataForPoints` ignores them (`isDownloaded`, the re-check's
  row 9), so they cannot trigger (read).

## 7. The runs (computed from the JUnit XML and the build logs; see §0 for compile)

| Run | Commit | Tests | Failed | Which, and the message | This edit's? |
|---|---|---|---|---|---|
| 40304392875 fail-first | `b1a13c623` | 23 (6 classes) | **19** | `WayToStrayTest` 3 (`expected:<REROUTE> but was:<STAY>`, `<SEARCHING>`); VM 6 ("by trail, not null"; "a second route … but was:<[]>"; `Straight(NO_ROUTE_FOUND)`/`Straight(NO_TRAIL_ROUTES)` vs null; the cast NPE; `assertNotNull`); screen 5 ("no straight dashed line while the trail route is shown expected null"; "the note, whole or short: "; "expected:<[by trail]> but was:<[straight]>"; X and Back on `assertNotNull`); note test 2 ("the straight distance expected:<[0.7 mi]> but was:<[—]>", the stub's `aim = null`; "No trail routes here[ · straight line]"); layers 1 (`[track, trail, track]`); `TrailRouteFailureTest` 2. Pass: VM "no trail routing given", screen arrival (today's rule), layers' 2 existing | yes, each |
| 40305283344 r1 | `82cde0037`: `WayToStray` drops `!armed` | 10 | 1 | stray test, "the same stray: spent expected:<ALREADY_REROUTED> but was:<REROUTE>" | yes |
| 40305296529 r2 | `046e36060`: REROUTE starts no search | 13 | 1 | VM "more than 50 m off…", "a second route, from the stray" expected 2 origins, was 1 | yes |
| 40305310657 r3 | `827a2143e`: straight line kept beside the trail line | 28 | 1 | screen swap, "no straight dashed line … expected null"; the 22 `WaypointNavigateTest` cases green | yes |
| 40305325435 r4 | `b0f4bef0c`: portrait card hands `null` | 28 | 3 | screen swap and real-taps ("by trail" vs "straight"), note case ("the note, whole or short: ") | yes |
| 40305338525 r5 | `dc78f4908`: STRAIGHT tagged track | 9 | 2 | layers and screen swap, `[track, trail, track]` | yes |
| 40305349976 r6 | `84aa42b7e`: status text drops `Straight` | 10 | 2 | note test ("No trail routes here · straight line" vs ""), screen note case | yes |
| 40305362238 r7 | `13f53e0cd`: `atReturn = it.route` | 7 | 1 | VM "no passed line: the step path's three parts only" | yes |
| 40308750070 item C fail-first | `6bbb69c7a` (`pull_request`) | **5,644** | **7** | `WayToRetryTest` 4 ("the first fix inside", "stored now", leaving-and-back, 51 m); `RetryTest` 3 ("one search on walking in, from there" 2 vs 1; "51 m on: one search"; download `expected:<2> but was:<1>`). Pass: inside-no-retry, note-walking-cannot-change, the 400 m case | yes |
| 40310887423 forward | `a00742ffb` (`pull_request`) | **5,644** | **0** | 0 errors, 24 skipped: the standing allowlist (`MapIconStackTest` 19, `TripPlanningFlowTest` 2, `WaypointFlowTest` 1, `OfflineCacheTest` 1, `GenerateFungiIndexDbAsset` 1). 5,644 = 5,613 (pre-main at `c1deeef85`, the re-check's PR run) + 21 (PR 3) + 10 (item C) | |
| 40310973713 retry revert | `43ceddd62`: `retry == null` (never tries again) | 17 | 3 | the three `RetryTest` cases above; `WayToRetryTest` 6 green (the class is intact), VM route test 7 green | yes |

Each failure is one its edit produces and none belongs to another edit. Each revert is one commit on its own branch
off the build commit, so the forward change was never restored from anything (the runner hazard cannot arise). The
revert covering r4 is the portrait card only; the landscape card's identical line (`:169`) has no revert and the
screen test runs portrait (`w384dp-h823dp`), so **the landscape HUD's "by trail" rests on reading, not a test** (read).

## 8. Modular code, sizes, files, touches

- `AvailabilityViewModel.kt` **793** (`wc -l`; the PR body says 792, the item C commit message 793), from 781; under
  the dispatch's 800 stop, with 7 to spare. No touched file is over 800 (largest `AvailabilityUiState.kt` 422,
  `RouteHomeLayers.kt` 310). New files: `AvailabilityWaypointRoute.kt` 188, `WayToStray.kt` 49, `WayToRetry.kt` 49, all
  one job, under 400. CI printed no size warning on the forward run (grep, computed).
- The collaborator pattern is the skill's: the ViewModel starts and stops (`:388-394`, the PR 1 collector), the
  collaborator decides nothing on its own, every write through `updateState` (read).
- **Files outside the lists:** `ApproximatePositionHud.kt`, `NavigationReadout.kt`, `NavigationHudReadout.kt` are in
  neither the report's PR 3 list nor the dispatch's "Also in this PR"; they are the natural home of
  `ReturnRoute.Straight` and the only way to give the HUD the note without a second parameter. `RouteLine.kt`,
  `WayToWaypoint.kt`, `FindWayToWaypointUseCase.kt`, `CompactMapTabInputs.kt` carry doc-comment updates only.
  `MainActivity.kt` and `AvailabilityViewModelWaypointNavigation.kt` were listed and are untouched: the cards read the
  route from the UI state, and PR 1's collector is the start/stop place (-1669 records the first). Deviations to
  record, none to hold the PR on (read).
- **No new composable, no new `Box` child, no Back handler:** the three composable changes (`CompactMapMapSlot.kt:68-70`,
  the two cards' `route =`) pick values; nothing new draws or attaches pointer input over the map (read, the whole diff).
- **The real-touch test:** `AvailabilityScreenWaypointRouteTest.kt:292-315` is a coordinate touch,
  `onRoot().performTouchInput { click(p) }`, 2 rows × 8 points just below the HUD with controls skipped and at least 8
  sampled, the same shape as `AvailabilityScreenWaypointNavigateTest.kt:701-725`, which is **unchanged** (not in the
  diff) and green (22 of 22) (read, computed).
- `CompactMapTabInputs.kt:57-59`'s stale lines remain, with a "since step 6 PR 3" sentence appended, as Amendment 1 says
  (to a later tidy); `WaypointNavigateControls.kt:9-12` and `AppContainer.kt:444` untouched (read).

## 9. The prediction, scored

Every run's failing tests and messages matched (§7 against §1). Three details were not as written: r2 ran 13 tests
(screen 6 + VM 7), not 10; r6's third class was `NavigationWayHomeNoteTest` (the filter's breadth); and the fail-first
note test's first failing assert was the distance ("—") rather than the needle, both of which I had named as the stub's
consequences. Nothing in the results was unpredicted.

## 10. Verdict

**Blocking:** none.

**Should-fix** (for Rowan to place; neither holds the merge):

- **S-413-1 The trail route's line never fades or greys on a stale or lost fix.** The straight line to a waypoint fades
  when the fix is stale and goes grey when it is lost (`nextStraightLine`, `NavigationArrival.kt:83-92`; the owner's
  "Fade with the HUD", "Grey once location is lost", RECORD -502 desk check). The way along trails to the same waypoint
  is built with `aheadIsCurrent = true` always (`AvailabilityWaypointRoute.kt:146`), and its collaborator receives a
  `LatLng` per fix and no clock or age (`:80`, `:109`), so it **cannot** judge freshness; with no new fix nothing
  changes, so the last line stays at full strength while the HUD above it says "Last fix 45 s ago" or "No fix…"
  (`NavigationReadout.kt:169-170`). Inside an area this replaces today's fading line with one that does not fade: a
  visible behaviour the step path did not rule either way and neither report names. The HUD's own words still carry the
  staleness, which the owner may judge enough. Fix if wanted: feed the fix's freshness (as `AvailabilityWaypointLine`
  does) and set `aheadIsCurrent` from it, the way home's withheld fade (`ROUTE_AHEAD_FADED_OPACITY`) doing the rest.
  Device-visible only; inferred from code.
- **S-413-2 (latent) `search` is assigned after `launch` returns** (`:154`, `search = scope.launch { … search = null
  … }`, `:164`). The scope is `viewModelScope`'s `Dispatchers.Main.immediate`, under which a launched body runs in the
  caller's stack until its first real suspension. If `find` ever returned or threw **without suspending**, the body
  would run to completion first, its `search = null` would then be overwritten by the completed job, and `search !=
  null` would block every later search and judge for that navigation (SEARCHING forever, no retry). Not reachable
  today: `listRegions()` suspends on MapLibre's callback (`MapLibreOfflineMapRepository.kt:232-233`) and the engine
  hops to IO; and not testable as written, since `StandardTestDispatcher` never starts a launch eagerly. A one-line
  guard (`search?.isActive == true`, or set the field inside the coroutine) closes it. The way home's `launchJob`
  has the same shape (`WayHomeWatch.kt:181-197`); I did not check its scope's dispatcher. Inferred.

**Notes:**
- **N-413-1** After "Arrived" the navigation stays on (the owner's rule) and the collaborator keeps running; a wander
  of more than 50 m off the line near the waypoint starts a quiet re-route search whose result shows once the walker
  is outside 15 m again. Harmless; the way home ends at arrival and has no such case.
- **N-413-2** `WALKER_FAR_FROM_TRAIL` inside an area costs one engine run per 50 m walked for as long as the walker
  stays beyond the engine's reach, exactly as the amendment allowed ("reuse an existing distance for walking on, such as
  the 50 m stray figure"). It shares the one-route lock with the way home (`WayHomeWatch.kt` class doc), so a way-home
  re-route can wait one run (about 0.6 s on the S22, -1625) behind it. Device-only to feel.
- **N-413-3** Two reads of the waypoint per Navigate (the line's and the route's `readWaypoint`), each logged on
  failure; if one failed and the other did not, that navigation could show a trail line with no straight fallback or
  the reverse. One indexed row, twice; an edge.
- **N-413-4** The landscape card's `route =` has no revert and no test (§7); the portrait one has both.
- **N-413-5** The "download finishing" claim's reload-after-fetch half is read, not run (§6).
- **N-413-6** `WayToStray` has no `reroutingEnded`; not needed, since a straight re-route result hands control to the
  retry (§3).
- **N-413-7** The 80% rule does not apply to MapLibre lines (`RouteHomeLayers.kt:35-36`); the new layer follows the
  straight line's colours and is night/offline-safe by the same reload the other four use (§4). Device-only to see.
- **N-413-8** PR body 792 lines vs 793 measured; trivial.
- **N-413-9** I could not review the coder's two reports to Rowan (beyond-scope choices named there). From the code
  and -1669: A (no grey walked line) is built and pinned by r7; B (straight while more than the far band off during a
  re-route) is `show()`'s `WaypointRouteShown(null, null)` on `OffLine` (`:148`), the owner's "Straight line briefly";
  C as §6. Other choices visible in the code and sound: `ReturnRoute.Straight` as a variant rather than a second HUD
  parameter; `heldBoth` initialised from the ask point so an inside search does not retry on the next fix; the engine's
  throw mapped to `NO_ROUTE_FOUND` as the way home maps it (M-395-2).

**Mergeable:** yes, as it stands, into pre-main `c1deeef85`, which has not moved since the base (`git merge-tree
--write-tree`: a tree, no conflicts; GitHub `MERGEABLE`, `CLEAN`). The owner's eight-step path holds through the code
with the swap atomic in one state write, arrival on the straight distance, Back and X ending both lines, and no sound
or vibration anywhere on the path; the stray rule is the way home's figures and comparisons; the three owner rulings of
-1669 are built; S-412R-1 is the constructor field with the one approved test change; every new rule has a test shown
failing without it on a build that compiled; 5,644 / 0 / 24 on the forward run.

**One-line answers:**
- Step path 1-8: holds (§2); no blank frame by one state write and one snapshot (inferred); arrival straight; Back/X
  clear both lines; quiet.
- Once-per-stray: the way home's figures, strict `< 45`, `<= 50`, same precedence; `reroutingEnded` rightly absent (§3).
- The straight-part layer: tagged, filtered out of casing and solid, drawn thin and dashed in the route group between
  dotted and the straight line; not chrome; night and offline by the same reload as its siblings (§4).
- HUD notes and short forms: every note, both forms, the leaves-area short form the owner's (§2, step 8).
- S-412R-1: nothing in main compares by equality; one test's expected value changed, as approved; the mark now bites (§5).
- Item C: no search per fix, one at a time, no loop (inferred), the list reloaded by every fetch path in the app, the
  50 m retry for `WALKER_FAR_FROM_TRAIL` only (§6).
- `AvailabilityViewModel.kt` 793, under 800; new files under 400; three readout files outside the lists, recorded (§8).
- Real touch below the HUD: a coordinate touch, 2 × 8 points; the existing one unchanged (§8).
- No new composable over the map (§8).
- Reverts: eight, each compiled, each failing on its own edit's message; the two fail-firsts likewise (§7).
- Clean merge; pre-main unmoved.

## 11. Disclosure

**Read:** every Forager file cited, at `a00742ffb` (or the hash named); the four commit messages; the eight revert
patches via the API; the dispatch and Amendment 1; RECORD -1652 to -1669; the step 6 report §3-§4; my three earlier
reviews; the PR body. **Computed:** the diff and stat, the two test-parity diffs, the two stub diffs, eleven runs'
metadata, eleven build logs (compile check, three re-fetched), eleven JUnit XML sets (764 suite files for each full
run), the merge-tree, `wc -l`, and the greps for `ReturnRoute`, `waypointRoute`, `EndOutsideData`, alert deliveries,
`loadOfflineRegions`, `fetchRoutes`, the layer ids and the route-layer call sites. **Inferred:** the no-blank-frame
claim (Compose snapshots); the style-spec `!=` on a missing property; the no-loop reasoning in §6; S-413-1's device
appearance; S-413-2's eager-start hazard and its unreachability today.

**Could not determine:** the coder's two reports to Rowan (not in the repository); anything a frame or a MapLibre layer
shows (device-only, as the dispatch lists); the way home's `launchJob` dispatcher (S-413-2's sibling); the
`workflow_dispatch` filters (not in the API; the class sets were counted from the XML).

**Premises that were wrong or need correcting:** none in the brief. The PR body's "792 lines" is 793 by `wc -l` and
by the item C commit's own message. The report's PR 3 file list named `MainActivity.kt` and
`AvailabilityViewModelWaypointNavigation.kt`, which the build did not need, and omitted the three readout files it did.

**Decided beyond scope, or left for the owner:** S-413-1's placing and whether the HUD's own stale/lost words suffice;
S-413-2's one-line guard, here or in a tidy with its way-home sibling. No revert check of my own was run: no code
pushes and no local Gradle; the eleven CI runs' logs and XML were read in its place, with the prediction written first.

**What I did not do:** no device, no local build, no reading of Hazel's S22 results, no reading of any parallel review.
