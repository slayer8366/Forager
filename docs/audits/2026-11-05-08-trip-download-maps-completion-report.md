# Download Maps from a trip in the Trip Planner: completion report (PR #497)

Dispatch `prompts/preserved/2026-11-05-08-trip-download-maps.md` (on `origin/records-after-173`), with:
- Amendment 1 (`prompts/preserved/2026-11-05-08-amendment-1.md`; the owner's A1 to A6, the planner's B1 to B5, the stops C1 to C3; RECORD -2171);
- the Phase 1 report (`prompts/preserved/2026-11-05-08-phase1-report.md`; RECORD -2170), whose section 6 is the plan, as Amendment 1 adjusts it;
- two owner rulings and two planner calls relayed by the planner during this build (quoted where used below).

Written by Hazel (Opus), 2026-11-05, for Phase 2, on the planner's go.

Each claim is marked:
- **read**: a file and line at the named commit, or the named CI run's JUnit XML or log;
- **observed**: command output;
- **inferred**;
- **not verified**.

Line numbers are at **52503422c**, the last code commit, unless said otherwise. The report contains no coordinates; the tests use made-up points only.

## Base

- **pre-main** was at **779599c3f**, the commit named in the go, at the start (observed, `git fetch` then `git rev-parse` and `git ls-remote`). It was unmoved just before the PR, and is an ancestor of the branch (observed, `git ls-remote` and `git merge-base --is-ancestor`).
- **records-after-173** moved to bd46c4fd8 meanwhile. The dispatch's three files did not change (observed, `git diff --name-only`).
- **#494** merged as 7357524b7 and **#496** as cd3fd2730, and both are in 779599c3f (observed, `git merge-base --is-ancestor`).
- **The branch** is `trip-download-maps`, cut from 779599c3f, in `~/.cache/forager-wt/trip-download-maps` (the detached worktree Phase 1 left at 3a3d85089, switched onto the new branch). Nothing in the planner's worktree was edited.
- **The CLAUDE.md followed** is pre-main's. The copy loaded into this session from the planner's worktree differs in two places: it says "Street tiles" where pre-main says "Trail Map tiles", and it lacks pre-main's "The owner's word" section (observed, `diff`).
- **The edge shade, #495,** shares no file with this branch. It changed 31 files at 4728bbe42 at the start (observed, `git diff --stat`, read against this build's list), and 34 at d31170fa4 just before the PR, `AvailabilityScreenDrawerHost.kt` among them (observed, `comm` of the two `git diff --name-only` lists: empty).
  - `AvailabilityScreenDrawerHost.kt` is untouched, as asked. So are `SearchControls.kt` and `AvailabilitySettingsUi.kt`, the drawer's content, which the drawer-outline work may need (observed, `git diff --stat 779599c3f`).
  - `CentrePinLocationPicker.kt`, `MapFeatureBubble.kt` and the map-chrome edge files are untouched (observed).
- **Nothing changes** in Room, DataStore, `app/schemas` or `server/` (observed, `git diff --stat`).

## Premises re-checked at 779599c3f

Phase 1 was read at 3a3d85089. #494 and #496 landed between. Line numbers in this table are at 779599c3f.

| Premise | Verdict |
|---|---|
| The rows' layout (Phase 1, Q5) | Unchanged since 3a3d85089: neither row file changed (observed, `git diff --stat`). |
| Phase 1's file sizes | Four moved between 3a3d85089 and 779599c3f (observed, `wc -l` of each at both): `AvailabilityOfflineMapsUi.kt` 415 to 426, `AvailabilityUiState.kt` 441 to 449, `MainActivity.kt` 766 to 769, `AvailabilityScreen.kt` 741 to 746, by #494 and #496 together. Phase 1's "after #494" figures (447, 768) were before bug 14. None is over 800 after this build (table below). |
| The page has one line about the area under "Pin at" | New since Phase 1: bug 14's `OfflineDetailNote` (read, `OfflineDetailNote.kt:19-27`; `AvailabilityOfflineMapsUi.kt:172` calls it in the slot both layouts use). The owner chose that slot for the route notes. |
| The landscape slot is a column, so two lines stack | Confirmed (read, `OfflineMapsSideBySide.kt:72-90`: `pinnedActions()` inside the controls' `Column`). |
| The bottom bar's Maps glyph | `Icons.Filled.Map`, through `CompactTab.MAP.icon()` (read, `AvailabilityNavigationUi.kt:118-121`). The icon uses that function, so the two can't drift apart. |
| The picked point is written as `lat.toString()` | Confirmed (read, `JournalTab.kt:823`). The trip's tap writes it the same way. |
| The only writer that blanks the point and name is a successful download | Confirmed (read, `AvailabilityOfflineMaps.kt:241-250`). |
| `CentrePinState.touched` is readable without touching the picker file | Confirmed (read, `CentrePinLocationPicker.kt:159-161`: public getter, private setter). |
| A broken stored route (C1) | Refused and logged, and the trip loads as a plain trip (read, `RoomPlannedTripRepository.kt:51-66`). `PlottedRoute` refuses a route with no legs or a broken leg at construction (read, `PlottedRoute.kt:18-30`, and each leg at `:66-67`). My message to the planner during the build cited `PlottedRoute.kt:54-66` and `RoomPlannedTripRepository.kt:51-67`; those were wrong (a `cat -n` over two files numbered them as one listing), are corrected here, and the planner was told. |
| A straight leg is its two stops | Confirmed (read, `PlotRouteUseCase.kt:76`). So only a trail leg can bend outside the stops (C2). |

## Commits

| Commit | What |
|---|---|
| 6d6ba8d29 | Tests first: the three test classes, the icon on both rows, its composition local, its tap, and the wiring, over five stubs (the area, the two state changes, the leave watch, the note) and no pin key |
| ab45b4ec5 | The screen test's missing `getOrNull` import (run 42169851458's test compile refused it) |
| 6d5c31460 | The stubs replaced: the area, the state, the note, the leave watch, the pin key |
| 76fbfdf20 | The screen test no longer scrolls to the Tools drawer's fixed Offline Maps row |
| fcbb0f87e | Positive controls with a tree dump in the two tests that type a name |
| 52503422c | The screen test runs in touch mode, as a finger puts a phone |
| this commit | This report |

PR **#497** into pre-main: open, not a draft, not merged (observed, `gh api .../pulls/497`: `draft` false, `merged` false, base `pre-main`). Its body was read back from the API and is identical to the one sent.

## What the user sees

**On every trip in Tools > Trip Planner, a maps icon** (Amendment 1, A1; the owner: "A maps icon next to the navigate icon.").
- **The glyph:** the bottom bar's Maps glyph (the planner's reading of A1).
- **Its button:** the same one as the card's Directions and Delete (`BouncingIconButton` with an `Icon`), so its size (a 24 dp glyph in a 48 dp touch target) and colour (the card's content colour) are theirs. This was my reading, and the planner accepted it.
- **Where** (A5, the owner: "Left of Directions (Recommended)"):
  - on a plain trip: maps, Directions, Delete;
  - on a route trip: the body (which opens the route), then maps, then Delete. The icon is outside the body, so a tap on it only opens Offline Maps (read, `PlannedRouteRow.kt:49-68`).
- **Its accessibility label:** "Download Maps for <trip name>" (A1).
- The plain card's text loses one icon's width, 48 dp [inferred from the Material default].

**A tap on it** (the dispatch's B2):
- Tools closes, and Offline Maps opens where Tools > Offline Maps opens it, on Journal > Records > Offline maps.
- **"Pin at"** reads the trip's point. For a route trip, it reads the middle of the route's line.
- **The Name box** holds the trip's name.
- **The radius** is unchanged for a plain trip ("today's starting value"). For a route trip it is the smallest whole km from 1 to 24 whose download holds the whole line, or 24.
- **Nothing downloads** (B4). The page's own Download Maps and "Download this area?" are unchanged.

**Back** is the Tools row's own (B2: "no new Back handler"). The first Back puts back the page the user came from with Tools open on the Trip Planner; the second closes Tools.

**Leaving Offline Maps without downloading forgets the trip** (A2, the owner: "Forget the trip (Recommended)"). The page gets back what it held before the tap: the pin, the name and the radius. The next Tools > Offline Maps opens as it does today.

**An Offline Maps page already open under Tools, panned or not, moves its pin to the trip** (B3). The picker's map is rebuilt for that one tap, since the map is remembered on the pin [read, `AvailabilityOfflineMapsUi.kt:165`, `:183`; the cost on a phone is not verified].

**Two notes, the owner's words exactly, at most one at a time,** under "Pin at" in portrait and above it in landscape, after bug 14's line when both show, in bug 14's style (`bodySmall`, the default colour, padded like the continental note):
- "Some of this route's stops are outside this area. Download the rest as a second area." (A6) shows when a stop is outside the area at 24 km.
- "Part of this route's trail is outside this area. Download the rest as a second area." shows when every stop fits at 24 km but part of the line doesn't. This was C2. The owner, relayed by the planner, chose "Its own note (Recommended)" for it, and "Under "Pin at" (Recommended)" for where both go.
- **The stops note wins** when both would be true (the planner's call).
- **Either note goes** once the pin is moved or the radius changed, because that is a different area, about which the note may not be true. It comes back if the radius returns to the starting one with the pin unmoved. A6 left this to my recommendation (below).

No other new words.

## Decided beyond the dispatch (to accept or reject)

1. **"Covered" is the downloaded rectangle, not a circle** (A4 asked for the choice and the reason). A point is covered when it lies inside `GeoDistance.boundingBox` at the centre and radius. That is the rectangle the download itself asks for, so it is the only test that says whether a point will be offline.
   - A circle sits inside that rectangle, so a circle test would start the radius up to 1.41 times larger, for a route along a diagonal, without holding any point the rectangle doesn't.
   - The radius is found by trying each whole km with the download's own function, not by a formula that could round differently (read, `TripDownloadArea.kt:61-79`).
2. **The centre is the middle of the line's extent**, halfway between its southernmost and northernmost points and between its westernmost and easternmost ones, as `GeoDistance.boundingRegion` already takes "the middle". The dispatch's B3 said "the centre of the smallest circle holding every stop"; A4 replaced B3 and named no centre. For a rectangle, this centre needs the least radius to reach both ends of each span.
3. **The note goes when the pin or the radius moves** (A6 asked for my recommendation). I recommend this: after either move the area is a different one, and the note's sentence is about "this area". The alternative is to keep re-checking the route against whatever area is on screen and show the note while it holds. That would also show it on areas that aren't the trip's starting one, which A6's "while the trip's starting area stands" seems to rule out.
4. **A download that succeeds from the trip's page ends the trip's opening** (`AvailabilityOfflineMaps.kt:250`). Leaving afterwards does not put back the entry from before the tap; the page is the page after any download, with point and name cleared as today. A2's question was about a user who "leaves Offline Maps without downloading"; this is the case where they downloaded.
5. **Leaving during a running download puts the page back at once.** The download carries on with the name and area it started with, and its success clears the fields as any success does. Not tested.
6. **A second trip tapped before leaving keeps the first tap's "before"**, so leaving still puts back the page as the user left it. A plain trip after a route trip takes the radius from before the first tap, not the route's.
7. **A route trip's radius is held as the user's own** (`offlineMapRadiusTouched`), so a change of units while the page is open leaves it. Leaving restores the flag as well.
8. **The composition local.** The tap reaches the rows through `LocalDownloadMapsForTrip`, provided in `AvailabilityScreen.kt` around the drawer host's call and read by the Trip Planner list (read, `AvailabilityScreen.kt:716`, `AvailabilityTripsWaypointsUi.kt:104`). The planner accepted it and said to keep it.
9. **The shared test screen, `MapLayersTestScreen`, wires the icon as MainActivity does**, so every test that uses it with planned trips now has the icon on its rows. No existing test changed result (the forward run below).

## Stops, and how each was settled

- **Where the note sits**, and C2. I reported both by message. The owner ruled both, relayed by the planner, as above.
- **C1:** not reachable as stated. A route with no legs or a broken leg can't be built, and a stored one that won't read back loads as a plain trip, logged. Such a trip opens as a plain trip: its point and today's radius, with no starting radius invented.
- **C3:** the icon's glyph, size and colour are its neighbours', as described above. The planner accepted that reading.
- **The drawer host:** not needed. No stop.
- **Two failed attempts at one symptom:** none. Three separate test faults each took one fix (below).

## Premises that were wrong

1. The dispatch's evidence list says "one km smaller would leave a point out, unless at the 24 km cap". It also can't apply at the 1 km floor, where there is no smaller radius. The tests skip that half there.
2. The plan's changes to `SearchControls.kt`, `AvailabilitySettingsUi.kt` and `AvailabilityScreenDrawerHost.kt` were not needed. The composition local reaches the rows without them.
3. Phase 1's "after #494" sizes (`AvailabilityUiState.kt` 447, `MainActivity.kt` 768) predate bug 14; the base's are 449 and 769 (premises table).

## Evidence

### Failing first

- **Run 42169851458 (6d6ba8d29):** the test compile failed with one error, `TripDownloadMapsScreenTest.kt:140:71 Unresolved reference 'getOrNull'` (read, the build log through the jobs API). **No results are cited.** The main code compiled. Fixed in ab45b4ec5.
- **Run 42170547293 (ab45b4ec5), filtered to the three new classes:** the compile log was clean (read, 4,476 lines, both compile tasks, no `e:` lines). The XML held 22 tests and 19 failures, the predicted count. **Five screen failures did not match the prediction:** "Semantic Node has no parent layout with a Scroll SemanticsAction". My helper scrolled to the Tools drawer's Offline Maps row, which is fixed below the drawer's scrolling list. That was a fault in the test, fixed in 76fbfdf20.
- **Run 42171351243 (scratch commit a7522ec77: ab45b4ec5 with 76fbfdf20's screen test, a one-line difference):** 22 tests, 19 failures. 18 matched the prediction. One, the download test, failed on "Failed to inject touch input": the dialog's Download was not there. Investigated below, with the forward run.
- **Run 42175189003 (scratch commit aa6fd7f3a: ab45b4ec5's stubs with 52503422c's tests; observed, `git diff` empty for `app/src/main` against ab45b4ec5 and for `app/src/test` against 52503422c):** the compile log was clean. **22 tests, 19 failures, every one at its predicted assertion** (predictions in scratch before the run, read):
  - 7 domain: "a route trip opens around its route, was AtTrip(...)";
  - 5 state: the fields unchanged, the pin request 0, no note;
  - 7 screen: "...: the pin is on the trip's point" (both kinds of row, both notes), "an open, panned page: the pin moved to the trip", "positive control: the trip's radius", "the trip's area downloaded under its name".
  - **Not shown to bite by the stubs:** the 3 passes. These are the plain trip's domain case, "with no trip opened, leaving changes nothing" and the icon order. The stubs left the icon real, so its order and label are guarded only by their assertions having been seen to pass on the forward runs.

### The focus finding (run 42171353636, then 42173561071)

- **Run 42171353636 (76fbfdf20), unfiltered:** 6,790 tests, 2 failures, 0 errors, 24 skipped. Both failures were mine, both "Failed to inject touch input", and both in the two tests that type a name into the Name box.
- **Run 42173561071 (fcbb0f87e)** added positive controls that print the focused node and the tree. In both tests **the search pill (`active-search-summary`) held focus**. Its dropdown opens on focus (read, `AvailabilitySearchUi.kt:83-91, 258`) and took the first Back and the touch on Download Maps.
- **The cause:** the test ran outside touch mode, where a focused field going away hands focus on [inferred from the platform's focus rules; the hand-off itself not traced]. The repo's own remedy, `RealSearchScreenRig.touchModeHost()` ("Touch mode, as a finger puts a phone", `RealSearchScreenRig.kt:356-363`), fixed it in 52503422c.
- **Run 42174250492 (52503422c), filtered:** 8 of 8 screen tests pass.
- Whether a phone does anything like this after a typed name is **not verified**. It is device step D8, an observation.

### The forward runs

- **Run 42175191783 (52503422c), unfiltered:** **every job green** (read).
  - Build job: compile log clean (5,216 lines). The JUnit XML holds **6,790 tests, 0 failures, 0 errors, 24 skipped**, in 984 files.
  - The Street-deleted job: 978 suites, 6,760 tests, 0 failures, 0 errors, 24 skipped (read, its count step).
  - Lint, release compile, map style, route cutter and Worker: green.
- **Reconciled against pre-main's own run 42167274295 on 779599c3f:** 6,768 tests with the same 24 skips. By (class, name), exactly 22 tests were added, in the three new classes (8, 8, 6), none were removed, and none changed status (observed). So no existing test changed result with the icon on the shared test screen's rows.

### Revert checks

**How each was run:**
- Each is one edit to the forward file as committed at 52503422c, read from git rather than the worktree, built through a temporary index into a scratch commit whose parent is 52503422c, and pushed to a scratch branch. My worktree was never edited, so there was nothing to restore.
- Each scratch commit differs from 52503422c by one edit in one file (observed, `git diff --shortstat`).
- Each ran filtered to the three new classes.
- The reader refuses a run whose build log is empty or too short, or holds a Kotlin compile error, or lacks either compile task. Its first version read the log with `gh run view --log`, which is empty until the whole run ends. It refused that empty log rather than citing it, and was switched to the jobs API.
- **Predictions** were written to scratch before any revert run (read, `predictions-reverts.txt`).
- **After all nine:** HEAD is 52503422c, the same as `origin/trip-download-maps`. The 15 changed files are byte-identical to the pushed branch (observed, `cmp`), and each of the nine forward lines is present (observed, `grep -F`). Each run's other jobs were cancelled once its build job had reported, to free shared CI; every result comes from the build job alone.

| Revert (run) | The one edit | Compile log | Failures (of 22) | Messages, each specific to its edit |
|---|---|---|---|---|
| R1 "the pin is not set" (42175303815) | the trip's point not written: `offlineMapLatText`/`LngText` kept as they were (`TripDownloadMaps.kt:67-68`) | clean, 4,447 lines | 8: 2 state, 6 screen | "...: the pin is on the trip's point" on both kinds of row and both notes; "an open, panned page: the pin moved to the trip"; "the trip's area downloaded under its name" (the area at the default centre); the 2 state tests' point text unchanged. The leaving test passes, as predicted. |
| R2 "the name is not set" (42175307421) | `offlineMapNameText = trip.name` to `= offlineMapNameText` (`:69`) | clean, 4,410 | 7: 1 state, 6 screen | "...: the Name box holds the trip's name expected:<[Test trip A]> but was:<[]>" (and B, C, D); "an open, panned page: the Name box holds the trip's name"; "the trip's area downloaded under its name" (as "My spot"). |
| R3 "the route's radius is not set" (42175310528) | `route?.radiusKm ?: before.radiusKm` to `before.radiusKm` (`:70`) | clean, 4,406 | 5: 1 state, 4 screen | "...: the radius expected:<Radius: [24] km> but was:<Radius: [10] km>" (both notes), "...[12]..." (the route row); "positive control: the trip's radius"; the state test's 12 against 5. Every plain-trip test passes. |
| R3b "the stops, not the whole line" (42175998431) | `route.legs.flatMap { it.line }` to `route.stops.map { it.position }` (`TripDownloadArea.kt:63`) | clean, 4,418 | **7, against 6 predicted**: 4 domain, 3 screen | "the middle of the line's latitudes"; C2's "expected:<24> but was:<6>"; "every point of the line is inside the area that downloads" (the out-and-back and three-stop routes, each naming its bend); the route row's "the pin is on the trip's point"; the trail note's "the radius ... [6]". **The seventh:** the leaving test's "positive control: the trip's radius expected:<Radius: [1]2 km> but was:<Radius: []2 km>". 2 km is this route's stops-only radius, so this edit produced it; my prediction missed that the leaving test opens the same route. |
| R4 "no pin key" (42176000747) | `key(uiState.offlineMapPinRequest) { ... }` removed (`AvailabilityOfflineMapsUi.kt:165`) | clean, 4,405 | 2 screen | "an open, panned page: the pin moved to the trip" (the panned point kept); "positive control: the note is back with the trip's area" (the moved pin kept, so the note stays hidden). |
| R5 "the trip is not forgotten" (42176003043) | `currentOnLeft?.invoke()` to `Unit` (`TripDownloadMapsUi.kt:92`) | clean, 4,429 | 3 screen | "Tools > Offline Maps afterwards opens as before the trip" (plain and route), each showing the trip's pin and name; "...the user's own entry put back". The state test passes (the transform is untouched), and so does the download test (a success ends the opening). |
| R6 "no stops note" (42176237608) | `route.stopsOutside ->` to `false ->` (`TripDownloadMaps.kt:62`) | clean, 4,403 | 2: 1 state, 1 screen | "the owner's note shows when a stop is outside at 24 km"; "expected:<STOPS_OUTSIDE> but was:<TRAIL_OUTSIDE>". |
| R7 "no trail note" (42176240387) | `!route.lineCovered ->` to `false ->` (`:63`) | clean, 4,402 | 2: 1 state, 1 screen | "the owner's trail note shows when every stop fits but part of the line doesn't"; "expected:<TRAIL_OUTSIDE> but was:<null>". |
| R8 "the note never goes" (42176243586) | `if (pinMoved \|\| radiusKm != ...) return` to `if (false) return` (`TripDownloadMapsUi.kt:108`) | clean, 4,395 | 1 screen | "the note goes once the pin moves". |

## Files and sizes

| File | Before | After |
|---|---|---|
| `domain/TripDownloadArea.kt` (new) | | 79 |
| `ui/availability/TripDownloadMaps.kt` (new) | | 108 |
| `ui/availability/TripDownloadMapsUi.kt` (new) | | 126 |
| `MainActivity.kt` | 769 | 774 |
| `AvailabilityScreen.kt` | 746 | 754 |
| `AvailabilityUiState.kt` | 449 | 453 |
| `AvailabilityOfflineMapsUi.kt` | 426 | 433 |
| `AvailabilityTripsWaypointsUi.kt` | 337 | 343 |
| `AvailabilityOfflineMaps.kt` | 315 | 317 |
| `PlannedRouteRow.kt` | 70 | 73 |
| `AvailabilityScreenOfflineMaps.kt` | 25 | 27 |

Tests: `TripDownloadAreaTest` (8), `TripDownloadMapsStateTest` (6), `TripDownloadMapsScreenTest` (8), and two wiring lines in `MapLayersUiFixtures.kt`. `AvailabilityViewModel.kt` (793), `JournalTab.kt`, `RecordsTab.kt` and `AvailabilityCompactScaffold.kt` are untouched (observed).

**Order-sensitive changes, for the phone check** (modular-code, "What stays together"):
- one `remember` key, at `AvailabilityOfflineMapsUi.kt:165`;
- one new effect in the screen's body, after the Tools row's own (`AvailabilityScreen.kt:493`);
- the drawer host's call wrapped in a composition-local provider, at the same position, with `InAppCameraHost` still after it.

## Conventions

- **The icon:** checked against its own row. Directions and Delete are `BouncingIconButton` with an `Icon` (read, `AvailabilityTripsWaypointsUi.kt:184-189`, `PlannedRouteRow.kt:65-67`), and the icon follows them. The glyph is the bottom bar's and the Tools Offline Maps row's (`AvailabilitySettingsUi.kt:177`).
- **The note:** follows bug 14's line, the page's existing one-line note about the area (read, `OfflineDetailNote.kt:19-27`), as the owner chose.
- **Back:** follows the Tools row's return (`AvailabilityScreenBackHandlers.kt:75-87`), unchanged.
- **The leave watch:** follows `ToolsOfflineMapsReturnWatch` (read, `ScreenToolsOfflineMapsReturn.kt:59-80`): arrived, then moved on.
- **The ViewModel calls:** extension functions on the ViewModel in their own file, as `AvailabilityViewModelOfflineMaps.kt` does, so `AvailabilityViewModel.kt` is not touched.
- No outside apps were checked; the conventions here are this app's own.

## Not tested, said plainly

- **Nothing has run on a phone.** That covers:
  - real touch routing through the drawer;
  - the picker map's rebuild when a trip's tap lands on an open page;
  - real insets;
  - the landscape layout with a note;
  - TalkBack reading the label.
- **Landscape:** no test. Both layouts call the one slot (read, `AvailabilityOfflineMapsUi.kt:332`, `:373`).
- **Miles:** the tests are metric. The radius line reads through the existing `formatDistanceKm`.
- **Leaving during a running download**, and a download started before a trip's tap that finishes after it. In the second case the success clears the trip's point and name, and an untouched pin would then follow to the default. Neither case is tested.
- **A real route over about 48 km across**, or one whose trail bends past its stops: whether any exists on the owner's phone is not verified.
- **A route across the antimeridian:** not handled. It would open at 24 km with the stops note. The offline maps cover the continental United States only.
- **The note's effect on the page's height in landscape**, where the pinned part grows by one line (two with bug 14's): not measured.

## Device check for the S22 (the planner runs it), cheapest first

**Setup:**
- Install the debug APK from this PR's CI run (artifact `app-debug-apk`), and read its version on the About screen.
- Nothing on the phone needs changing, and no trip needs adding, for D1 to D5 and D7 to D8. D6 needs a route bigger than one download. If none exists, D6 is not run (creating one is a change to the phone's data, for the owner's say-so).
- No coordinates, screenshots of places or trip names go on GitHub.
- **A failure at D1, D2a or D2 stops the run.**

| Step | Do | Pass | Evidence |
|---|---|---|---|
| D1 | Tools > Trip Planner. Look at a plain trip's card and a route trip's card. | Plain: maps icon, Directions, Delete, left to right. Route: the text, then maps, then Delete. The maps icon matches the bottom bar's Maps glyph in shape, and Directions in size and colour. | A screenshot of the drawer (crop out names and coordinates) |
| D2a | From the Maps tab: Tools > Offline Maps. Note "Pin at", the Name box and the radius. Back, then Back. | This is today's page, the baseline for D2 and D4. | A screenshot (crop out "Pin at") |
| D2 | Tools > Trip Planner. Tap a plain trip's maps icon once. | Tools closes. Journal > Records > Offline maps shows. "Pin at" matches the decimal line on the trip's card. The Name box holds the trip's name. The radius is D2a's. No "Download this area?". | A screenshot; Pin at against the card, said as "same" or "differs" |
| D3 | Back, then Back. | The first Back puts back the tab you started on, with Tools open on the Trip Planner. The second closes Tools. | Said in words |
| D4 | Then Tools > Offline Maps. | It opens as D2a did: the same Name box and radius, and the pin where D2a's opened (near your location, unless D2a held an earlier point). | A screenshot, said as "same as D2a" or "differs" |
| D5 | Tools > Offline Maps, pan the map away, then Tools > Trip Planner > a trip's maps icon. | The pin jumps to the trip. **Note any flash or blank map while the map rebuilds: an observation, not a gate.** | Said in words, with how long any blank lasted |
| D6 | Only if the phone has a route over about 48 km across: tap its maps icon. | "Radius: 24 km", and the stops note, exactly, under "Pin at". Drag the map a little: the note goes. | A screenshot |
| D7 | Tap a route trip's maps icon, then turn the phone to landscape. | The pin, the name and the radius stay. If D6 ran, the note sits above "Pin at", wholly in view, with Download Maps reachable. | A screenshot |
| D8 (observation) | On Offline Maps, tap the Name box and type a letter. Tap the bottom bar's Maps. Then Tools > Trip Planner > a trip's maps icon. | **Either answer is a finding, not a gate:** whether the search pill or its dropdown opens by itself, and whether the keyboard comes up. The tests found this outside touch mode only. | Said in words |

**The row to keep:** the build's version; D1 to D7 pass or fail (with D2a's baseline); D5's flash; D8's answer; any surprise on a step that passed.

A later build that changes these paths runs this from the top: a pass here is evidence about this build.

## Scratch

`/mnt/work/scratch/trip-download-maps/` held the forward copies, the predictions, the run reader and the downloaded results. It is deleted after this report is pushed; the hand-back to the planner says whether that happened. The scratch branches (`scratch/trip-maps-stub-3`, `scratch/trip-maps-stub-4`, and two rounds of the nine `scratch/trip-maps-revert-*`) were deleted from the remote, and none remains (observed, `git ls-remote`).
