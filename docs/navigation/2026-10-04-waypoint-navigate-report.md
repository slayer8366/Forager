# Navigating to a chosen waypoint, from its bubble, its details sheet and Records (dispatch 2026-09-28-502, plan tasks T8 and T9)

**Status: built and pushed on `waypoint-navigate` at `631b913b`, not merged.** Desk check on the S22 done for steps 1 to 4. The faded and grey line has not been seen on the phone: indoors the GPS gave no fix.

**Date:** 2026-10-04 (UTC).
**Dispatch:** `prompts/preserved/2026-10-04-09.md` (from `origin/records-after-167`), with Amendment 1 (the owner's three answers, `RECORD.md` -503, and the planner's four rulings) and Amendment 2 (four more existing tests, after my stop on a premise).
**Base:** `origin/main` at `a501a8a0` (PR #167, T7), checked against the remote.
**The owner's words,** to this session directly: "Yes, start -502"; for the phone, "Yes"; at the desk check, "Keep the last line, faded" and "Grey once location is lost (Recommended)". The planner relayed the owner's "Fade with the HUD" (RECORD -506).

App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`. Logs, scripts and any screenshots are in `~/Zynergy/device-evidence/2026-10-04-waypoint-navigate/`; screenshots show grid references, so they stay there.

## In plain terms

1. **Tap a waypoint, then "Navigate":** in its bubble on the Maps tab, in its details sheet (opened from the map or from Records), or on its row in Records. It works with or without a recording.
2. **The navigation view starts as for a return:** the map tilts and follows; the panel at the top points at the waypoint and gives its straight-line distance; the Return button is the X in a circle, "Stop navigating"; a thin dashed line runs from the walker to the waypoint.
3. **Within the larger of twice the GPS accuracy and 15 m,** the waypoint becomes the ring with a check, the dashed line ends, and the panel reads "Arrived". Navigation stays on until ended.
4. **Back ends it and goes back to where Navigate was tapped:** the bubble or the details sheet opens again, or Records comes back. The next Back carries on from there. The X-circle and the panel's X end it and leave the walker on the map.
5. **A return under way gives way:** tapping Navigate pauses the return, and ending the waypoint navigation picks it back up, its line drawn afresh. The off-track alert and the walked-path line do not run meanwhile.
6. **When the phone loses your location,** the line fades with the panel's distance (from 30 s without a fix). Once the location is lost (5 min, "No fix for N min"), the last line stays, in grey. A fresh fix brings it back blue.
7. **Closed by the phone and opened again,** the navigation picks back up. If the waypoint was deleted meanwhile, or is deleted while navigating to it, navigation ends with "Stopped navigating: that waypoint was deleted."
8. **A journal entry's map keeps Directions only.**

## Verified before building (reported by message first; the owner and the planner ruled)

1. **Directions today.** None of the three places was wired above itself: each called `launchDirections`, a `geo:` intent (`ui/availability/AvailabilityPureFunctions.kt:141-171`), from the bubble (`ui/map/MapBubble.kt:311`, `:405-414`), the sheet (`ui/log/RecordDetailsSheet.kt:264-271`, whose KDoc said "Directions only, no Navigate") and the row (`ui/availability/AvailabilityTripsWaypointsUi.kt:267`). The deferral, `docs/plans/journal-redesign.md:486-488`: "Directions stands."
2. **The target and its state.** The HUD's target was `trackUiState.originWaypoint` (`MainActivity.kt:632`), and the one predicate was `val isNavigating = isReturning` (`ui/availability/AvailabilityScreen.kt:802`), whose comment foresaw this: "stage two's target picker ORs its own state into this one line". The HUD already had a straight-line mode for `route = null` (decision D2). `AvailabilityViewModel` holds the live fix with no recording. The owner chose "Pick navigation back up" across the app being closed, so the target is kept (below).
3. **The view.** The navigation view, the start zoom and "Return to Route" all key on `isNavigating`, so widening it turns them on. Going from a return straight into a waypoint keeps `isNavigating` true, so there is no new start zoom: accepted.
4. **Back.** On purpose, Back could never exit a navigation: it raises "Exit navigation?" (`AvailabilityScreen.kt:1092-1114`). For a waypoint the step path says "Back from the navigating HUD stops navigating". The owner chose "Back to where you tapped Navigate" (`RECORD.md` -503).
5. **T7, reused.** `arrivedAtStart` and the ring were already target-agnostic in the map; the HUD's "Arrived" was gated on a route. The X-circle was keyed on returning.
6. **Off-track and the walked line** run only while returning (`ReturnWatch.onFix`'s `if (returning)`; the route tick). So the waypoint pauses the return with the return's own Stop and resumes it with its own Return; the resumed return captures its line afresh (accepted).
7. **Two existing tests** encoded the old rule; Amendment 1 allowed them to change. **Two more premises turned out wrong while building,** below.
8. **Entry maps:** the owner chose "No, only Maps, the sheet and Records".

## What was built

- **`domain/WaypointNavigation.kt`** (new):
  - `WaypointNavigation(waypointId, resumesReturn)`;
  - `WaypointNavigationRepository` (`Result`-returning, like the project's others);
  - `ReturnLeg` (whether a return is under way; pause; resume);
  - `waypointNavigationTarget(navigation, waypoints, waypointsLoaded)`: None, Waiting (not loaded yet), Found, or Gone (loaded and missing).
- **`data/repository/DataStoreWaypointNavigationRepository.kt`** (new): its own file, `waypoint_navigation`, two keys, `waypoint_navigation.target_id` and `waypoint_navigation.resumes_return`, written in one edit and cleared in one edit. **DataStore, not Room,** by CLAUDE.md's rule: one current value, cleared when navigation ends, that nothing joins to or filters against. An id kept without its flag is refused as a failed read rather than guessed.
- **`ui/availability/AvailabilityViewModel.kt`:** `uiState.waypointNavigation`; `onNavigateToWaypoint(id)` pauses a return under way first and remembers it; `onStopWaypointNavigation()` clears it and resumes a paused return; the kept navigation is read at start and not applied over one chosen meanwhile. Read and write failures are logged. Two new constructor parameters, both defaulted, last.
- **`ui/track/TrackRecordingReturnLeg.kt`** (new): `pause` is `stopReturn`, `resume` is `startReturn`. `MainActivity` hands it the recording ViewModel itself, not the Activity.
- **`ui/track/`:** `TrackRecordingUiState.waypointsLoaded`, set by the first successful waypoint read.
- **`ui/availability/AvailabilityScreen.kt`:**
  - `isNavigating = isReturning || navigatingToWaypoint != null`; the target is the waypoint while one is found, else the origin.
  - **Back:** a new handler while a waypoint is the target ends it and goes back where Navigate was tapped, saved with the screen (`WaypointNavigationOrigin`, `ui/map/WaypointNavigationOrigin.kt`). The return's "Exit navigation?" loop is off only while a waypoint is the target; with none chosen it is as it was.
  - **The return control and the HUD's X** end a waypoint navigation first while there is one, otherwise toggle the return as before.
  - **Gone:** the navigation ends and the snackbar host says "Stopped navigating: that waypoint was deleted."
  - **Navigate** comes up on the Maps tab; already navigating and panned away, the view is taken back as "Return to Route" does.
- **`ui/availability/AvailabilityCompactMapUi.kt`:** in waypoint mode the HUD gets no route (D2's straight-line HUD), the map gets the dashed line (`straightLineToTarget`), the ring and pin swap as T7's do, and the control is the X-circle. The bubble opens again on Back's request; the map re-anchors it on its glyph at the next camera idle.
- **`ui/availability/NavigationHud.kt`:** "Arrived" for any target (Amendment 1; no flag); `straightLineToTarget`: from the fix to the target, none with no fix or a lost one, none once arrived.
- **`ui/availability/AvailabilityMapControlsUi.kt`:** the control's icon, label, accent and `enabled` key on navigating, not returning; enabled while navigating with no recording.
- **`ui/map/RouteHomeLayers.kt`:** a straight source and a dashed line layer above the return's lines: azure (the line ahead's), 3 dp, 6 dp dashes and gaps, butt caps. Provisional.
- **Navigate, first, beside Directions:**
  - the bubble (`ui/map/MapBubble.kt`, "Navigate · Directions · Details"), only where `MapRecordSources.onNavigateToWaypoint` is given, which only the Maps tab gives;
  - the details sheet (`RecordDetailsSheet`), an outlined "Navigate" before "Directions";
  - the rows (`WaypointRow`, used by the Waypoints chip and the All logbook), an icon button "Navigate to <name>" before Directions.
- **The plumbing:** `WaypointNavigateControls` (one parameter through `CompactMainScaffold`, which already takes over a hundred) to `CompactMapTab` and `JournalTab` → `RecordsTab`.
- **After the desk check** (owner and planner rulings above): `nextStraightLine` and `StraightLine` (`NavigationHud.kt`):
  - a fresh fix: blue;
  - a stale fix (30 s to 5 min): from it, blue at 0.4;
  - lost: the last line kept, grey (`isOffline`), drawn by its own source and layer in the passed way back's grey, dashed, full opacity;
  - nothing drawn yet: none;
  - arrived: none.

  The line is held in `AvailabilityScreen` above the tab switch. The screen's clock moves on at the two moments (to stale, to lost), so the change shows with no new fix to prompt it. All of the look is provisional.
- **`MainActivity` and `AppContainer`:** the store, the leg and the three new screen inputs.

## Tests

**Tests first, pushed failing** (`343d8dfe`; run `t1-tests-first`). They ran against a skeleton with the types, parameters and tags in place but no behaviour: 11 classes, 127 tests, **49 failures**, no compile errors. Each failure was for the reason expected: no Navigate button, a store that keeps nothing, a target rule that answers "none", and no straight-line source.

**Six of the new tests passed against the skeleton.** They are the "nothing" cases, so they guard those states and are not evidence for the build:
- the store's untouched install, its clearing, and its isolation;
- the target rule with nothing chosen;
- no line with no fix or a lost one;
- the saver with nothing saved.

The existing HUD test of D2's straight-line mode, at 1.1 km, passes before and after.

**One of my new tests was wrong at first.** The entry-map test failed on Directions not being found, a failure no part of -502 could cause. Its glyph sat at 380 dp, below the journal entry's shorter map. It was moved to 120 dp, where `CartographyEntryMapBubblesTest` puts its glyphs, before the tests-first commit. Revert r19 shows it now bites.

**New, in seven new classes, plus three tests added to `TrackRecordingSwipeAwayTest`** (no existing test there changed):
- **`domain/WaypointNavigationTargetTest`** (5): none chosen; found by id; waited for before the waypoints load; gone once loaded and missing; found before the load.
- **`data/repository/DataStoreWaypointNavigationRepositoryTest`** (6, a real DataStore):
  - nothing on an untouched install;
  - both keys round-trip both ways;
  - clearing leaves nothing;
  - the app opened again (a second repository on the file, after the first is closed) reads what was written;
  - its own file holds exactly its two keys;
  - isolation.
- **`ui/availability/AvailabilityViewModelWaypointNavigationTest`** (8, the real ViewModel over fakes):
  - Navigate with no return targets the waypoint, keeps it, and pauses nothing;
  - during a return it pauses the return, and ending picks it back up;
  - ending clears what is kept, and a second end does nothing;
  - another waypoint keeps the paused return;
  - the app opened again picks the navigation back up, return and all;
  - a late read does not replace a fresh choice;
  - a failed read is logged and resumes nothing;
  - a failed write is logged and the navigation still runs.
- **`ui/map/RouteStraightLineLayersTest`** (3): the straight line to its own source; none without one; none once arrived, with the ring drawn.
- **`ui/availability/StraightLineToTargetTest`** (3): from the fix to the waypoint; none with no fix or a lost one; none within 15 m.
- **`ui/map/WaypointNavigationOriginSaverTest`** (3): each of the four kinds round-trips; an id containing colons comes back whole; none as none.
- **`ui/availability/AvailabilityScreenWaypointNavigateTest`** (18). This runs through the real screen and the real ViewModel, with real touches on every control. The map is a stand-in that records what it is handed.
  - **Navigate from each of the four places:** the bubble, the map's sheet, a Records row, and the sheet in Records. Each targets the waypoint with no recording: the HUD aims at it ("Turn 0°") with its straight-line distance and no "Straight line" label, the control reads "Stop navigating" and is enabled, and the map is handed the dashed line and the waypoint's pin.
  - **Back from each of the four:** it ends the navigation and opens the bubble, the map's sheet, Records, or Records with its sheet. From the bubble, the next Back closes it.
  - **The X-circle and the HUD's X** end the navigation where the walker is, with the line removed.
  - **Arrival:** the ring's place instead of the pin, no dashed line, "Arrived", and navigation still on.
  - **A return under way:** Navigate pauses it; Back picks it up (its HUD and line return) and reopens the bubble; the next Back closes the bubble; the one after raises "Exit navigation?", as before.
  - **Deleted:** while navigating, the navigation ends with the message and nothing is kept; picked back up for a deleted waypoint, the same message.
  - **Picked back up:** after the app opens again, and before the waypoints load it waits rather than ending.
  - **The entry map:** its waypoint bubble shows Directions and no Navigate.
  - **The Surface pitfall:** real taps just below the waypoint HUD reach the map.
- **`TrackRecordingSwipeAwayTest`** (3 added), through `AvailabilityViewModel` and `TrackRecordingReturnLeg` as `MainActivity` wires them, with the watch fed as the service feeds it:
  - Navigate during a return pauses it on the watch and the screen, the recording carries on, and a 15 s walk away from the start delivers **no** off-track alert;
  - ending the waypoint navigation picks the return back up, and the same walk then delivers one alert;
  - the waypoints are marked loaded once read.

**The shared fixture** `mapLayersViewModel` (`MapLayersUiFixtures.kt`) gains two parameters, both defaulted to the old behaviour. No test that uses it changes.

### Existing tests changed (Amendments 1 and 2)

Each carries a "Dispatch -502 changed this" line.

| Test | Before | After |
|---|---|---|
| `RecordDetailsSheetTest`, the waypoint sheet test (:426) | named "… and there is no Navigate"; asserted no node's text contained "Navigate" | named "the waypoint sheet offers Navigate beside Directions, and its Directions starts directions to that waypoint"; asserts the sheet's Navigate is shown and reads "Navigate". Its Directions assertions are unchanged. Its `setScreen` gains `onNavigateToWaypoint`, `null` by default, so the class's other tests see the sheet as before; this test passes `{}` |
| `NavigationHudArrivedTest` (:50) | "navigating to anything but the start never reads Arrived": no route, 5 m, not "Arrived" | "navigating to a waypoint, with no route, within 15 m of it the large figure reads Arrived too": the same fix reads "Arrived" |
| `NavigationHudReadoutTest` (:208) | "approaching inside twice the reported accuracy - never arrived, and the needle is not drawn"; large slot "within 41 ft" | named without "never arrived"; large slot "Arrived". "Approaching", no needle and the empty target column unchanged |
| `NavigationHudReadoutTest` (:234), inside case only | 15.57 m at 8 m accuracy: large slot "≈ 50 ft" | "Arrived". The needle's boundary on both sides, "Approaching" and the outside case unchanged |
| `NavigationHudReadoutTest` (:258) | 10 m, no accuracy: large slot "33 ft" | "Arrived". The needle drawn and "Turn 315°" unchanged |
| `NavigationHudReadoutTest` (:357) | unreliable compass, approaching: large slot "within 41 ft" | "Arrived". The needle withheld, the empty target column and "Approaching" unchanged |

**Why, and why these are no weaker:**
- The owner chose arrival at a waypoint "Same as the start", with no arrival flag. Arrival's radius is never smaller than approaching's, so every approaching point has arrived.
- What these tests are about (the needle, "Approaching", the precedence) is asserted as before.
- The accuracy-aware distance strings ("within 41 ft") are still pinned where they live, in `FormatDistanceMetersTest`.

**Added after the desk check,** each tests first (`3c81992b`, `a707b625`, `35373ed2` as below), with the tests of this dispatch's own that the rulings changed noted in them:
- `StraightLineToTargetTest` grows to 12. It covers: current from a fresh fix; kept, now grey, once lost; back current with a fresh fix; none before the first fix; none on arrival; not kept for another waypoint; the 30 s boundary (29.999 s current, 30 s faded); the 5 min boundary (just before, from the stale fix, faded blue; at it, the line kept from before, grey); and the wait until the next change.
- `RouteStraightLineLayersTest` grows to 5: the opacity, and the grey source.
- `AvailabilityScreenWaypointNavigateTest` grows to 21:
  - a lost fix keeps the line, grey, through a trip to the Journal, and a fresh one brings it back;
  - a stale fix draws it faded blue;
  - picked back up with no fix, no line until the first.

### Revert checks

Run by `revert.sh`, which works as follows:
- it edits from a saved copy, never git;
- it reads the build log for compile errors before any result;
- it confirms the tree is identical to HEAD after each check;
- each check runs the 11 classes above.

**All 30 runs compiled with 0 errors.** Each named failure is one its own edit could cause: every check failed only tests whose subject is the edited behaviour.

**One check did not bite at first: r06.** Letting a late read replace a fresh choice changed nothing, so its test passed. The cause was my test's fake store, not the code. A read held back by the test's gate returned the value as it was at the end of the read, and by then the fresh choice had been written to it. The fake now returns what was kept when the read began (`fa5faf84`), and r06b fails for its own edit.

| Check | One edit | Fails |
|---|---|---|
| r01 | loaded and missing is never "gone" | the gone rule; both deleted-waypoint screen tests |
| r02 | not loaded yet counts as gone | the waiting rule; the screen's waiting test |
| r03 | Navigate does not pause the return | the ViewModel's pause and other-waypoint tests; the screen's return test; both swipe-away return tests |
| r04 | ending does not resume the return | three ViewModel resume tests; the screen's return test; the swipe-away resume test |
| r05 | the kept navigation is not picked back up | the ViewModel's and the screen's picked-back-up tests; the screen's waiting and deleted-while-closed tests |
| r06 | a late read replaces a fresh choice | nothing, with the old fake (above) |
| r06b | the same, with the fake fixed | the ViewModel's late-read test |
| r07 | the return flag is not written | three store tests (the read refuses an id without its flag) |
| r08 | "Arrived" only with a route (the gate back) | the waypoint arrival test; the four Amendment 2 tests; the screen's arrival test |
| r09 | the line runs past arrival | the straight-line arrival test; the screen's arrival test |
| r10 | a line on a lost fix | the lost-fix test |
| r11 | no straight source | the straight-source test |
| r12 | the map not handed the line | the bubble test; the picked-back-up test |
| r13 | the HUD keeps the return's route | seven screen tests ("Turn 180°", the route's lookahead, for "Turn 0°") |
| r14 | the control disabled with no recording | the bubble test (`assertIsEnabled`); the X-circle test |
| r15 | the control's icon and label keyed on returning | seven screen tests ("Return to vehicle — start recording first" for "Stop navigating") |
| r16 | Back does not go back | all four Back tests and the return test |
| r17 | the X goes to the return toggle | the X-circle and HUD-X tests |
| r18 | a deleted waypoint ends nothing | both deleted-waypoint tests |
| r19 | Navigate on every bubble, an entry map's included | the entry-map test |
| r20 | the bubble not opened again | the bubble Back test; the return test |
| r21 | the map's sheet not opened again | the map-sheet Back test |
| r22 | the Records sheet not opened again | the Records-sheet Back test |
| r23 | the waypoints never marked loaded | the loaded test |
| r24 | the leg does not pause | both swipe-away return tests |
| r25 | the leg does not resume | the swipe-away resume test |
| r26 | no Navigate in the sheet | four screen sheet tests; the changed `RecordDetailsSheetTest` test |
| r27 | a row's Navigate does nothing | the Records-row test; the HUD-X test (started from a row) |
| r28 | the saver splits an id at its colons | the colon test |
| r29 | the navigation is not kept | four ViewModel tests |

**Not reverted:** the Surface-pitfall tap test. Nothing new is composed over the map: the line is a MapLibre layer, and the HUD and the control are T7's. So it guards against a future overlay rather than proving this one, as -497's long-press test did.

**After the desk check** (same runner, all compiled with 0 errors, each failing only its own tests). r30 to r36 ran at `a646b993`; r35 targeted a function `a707b625` replaced, and r38 covers its successor.

| Check | One edit | Fails |
|---|---|---|
| r30 | the lost line dropped | the kept-line tests (rule and screen) |
| r31 | kept, not faded | the same two |
| r32 | a line to another waypoint kept | the other-waypoint test |
| r33 | the faded opacity full | the opacity test |
| r34 | the map not told the line is faded | the screen's kept-line test |
| r35 | the wait until lost can go negative | the wait test |
| r36 | the kept line held below the tab switch | the screen test's trip to the Journal |
| r37 | a stale fix drawn current | both boundary tests; the screen's stale test |
| r38 | the clock wakes only when lost, not at 30 s | the wait test |
| r39 | kept, not grey | the kept-line rule test, the 5 min boundary, the screen's grey assertion |
| r40 | the grey line also drawn blue | the grey-source test |
| r41 | the map not told the line is grey | the screen's grey assertion |

**Not covered by a revert:** the clock's wake-up itself, in `AvailabilityScreen`. The JVM tests drive the change by a new fix, not by time passing, so whether the line fades at 30 s and greys at 5 min with nothing else moving is device-only.


## Suite

| Run | Tree | Result |
|---|---|---|
| Tests first (`t1-tests-first`) | `343d8dfe` | 11 classes, 127 tests, 49 failures, as expected |
| Built, with neighbours (`t2-built`) | `52e98b9f` | 17 classes, 204 tests, 0 failures |
| Full suite (`t4-full-suite`) | `fa5faf84` | 446 classes, 3,630 tests, 0 failures, 24 skipped |
| Faded line, tests first / built (`t5`, `t6`, `t7`) | `3c81992b` / `a646b993` | 6 failures as expected / 0 failures |
| Full suite (`t8-full-suite`) | `a646b993` | 446 classes, 3,640 tests, 0 failures, 24 skipped |
| Stale fade, tests first / built (`t9`, `t10`) | `a707b625` / `2320ddcf` | 4 failures as expected / 0 failures |
| Full suite (`t11-full-suite`) | `2320ddcf` | 446 classes, 3,643 tests, 0 failures, 24 skipped |
| Grey line, tests first / built (`t12`, `t13`) | `35373ed2` / `631b913b` | 4 failures as expected / 0 failures |
| **Full suite (`t14-full-suite`)** | **`631b913b`** | **446 classes, 3,644 tests, 0 failures, 0 errors, 24 skipped** (the existing `@Ignore`s) |

**Reconciled:**
- `main` at `a501a8a0` has 439 classes and 3,581 tests: no test changed between T7's full run at `e295fbe1` and `a501a8a0`.
- This branch adds 7 classes and 63 tests: 5 + 6 + 8 + 5 + 12 + 3 + 21 in the new classes, and 3 in `TrackRecordingSwipeAwayTest`.
- 3,581 + 63 = 3,644; 439 + 7 = 446.


## The desk check

On the S22, with the owner at the phone. Screenshots are in the evidence folder; they show grid references and stay there.

1. **Bubble → Navigate** (`1.0.2672+gfa5faf84`), "Done, as expected". The waypoint was within reach of the desk, so the screenshot shows "Arrived", the waypoint as the ring with a check, the X-circle and the tilted view.
2. **Back** reopened the bubble, and the next Back closed it: "Done, as expected".
3. **A Records row → Navigate to a far waypoint.** Navigation started. The first time there was no line; repeated, the dashed line was there, about 500 ft behind the walker ("Turn 178°"). The owner: "It's been there when the location is found, but doesn't appear when you can't find location".
4. **Swipe away and reopen:** the navigation came back (the panel and the X-circle), with no fix yet ("Location services unavailable") and so no line.

The owner chose "Keep the last line, faded", and the planner relayed "Fade with the HUD"; both were built (`2320ddcf`).

**On that build the owner saw the line disappear when the fix dropped indoors.** They suggested "An offline grey color will work" and chose "Grey once location is lost" (built, `631b913b`, installed). Why the faded line looked gone is **not established**. Inferred: from 30 s without a fix it was a thin blue line at 0.4, which may be nearly invisible over the pale map. Not confirmed on the phone.

**The owner suspected the build had broken GPS** ("[a mainstream maps app] shows my GPS just fine"). Checked from the phone's own records:
- The app was registered for GPS every second, and GPS was on.
- The GPS chip's last fix was about 1 h 31 min old: 265 m, 4 weak satellites.
- The phone did have a network location about 7 min old at 100 m. That is what the maps app shows, and this app's gate refuses anything worse than 50 m (`domain/LiveFixGate.kt`, unchanged).
- `git diff a501a8a0..HEAD` touches no location code.

The owner chose to compare against main's build (`1.0.2654+ge295fbe1`, the same app code as `a501a8a0`), which was installed. The owner's next message reported the line disappearing, from the -502 build. **The comparison itself was not completed:** no screenshot of main's build was taken. The -502 build was then reinstalled. So "the build broke GPS" is unconfirmed rather than ruled out by a side-by-side, though the phone's records point to indoor GPS.

**Not seen on the phone:** the faded and grey line (no GPS fix indoors), and the return picking back up after a waypoint navigation.


## Disclosure

**Confirmed vs inferred.**
- Confirmed by tests, through the real entry points:
  - Navigate from all four places, with no recording;
  - the HUD, the control and the line;
  - Back to each origin; the X-circle and the HUD's X;
  - arrival;
  - the return paused and picked back up, with no off-track alert meanwhile;
  - the store, and picking the navigation back up;
  - a deleted waypoint;
  - entry maps without Navigate;
  - the faded and grey line rules.
- Confirmed on the S22: Navigate from the bubble and from Records, the HUD, the X-circle, arrival, Back to the bubble, the line when there is a fix, and the navigation coming back after a swipe-away.
- Inferred: why the faded line looked gone on the phone; that indoor GPS, not the build, explains "Location services unavailable".

**Could not determine.**
- The faded and grey line on a phone, and the moment-of-change wake-ups (device-only).
- Whether main's build behaves the same in that spot: the comparison was not finished.
- How a bubble reopened by Back looks when its waypoint is off screen. It is clamped to the map's edge until the next camera idle re-anchors it.
- A real process death restoring Back's destination. The saver round-trips in tests; the phone was swiped away, which loses it by design.
- The return picking back up on a phone.

**Premises that were wrong.**
1. **"Two existing tests change"** (Amendment 1). Six change; the planner ruled the four more in Amendment 2.
2. **"A single current-target id."** Two keys are kept, the id and whether a return was paused (the planner accepted).
3. **Two of my own were wrong:**
   - the entry-map test's glyph sat below that map;
   - the fake store let revert r06 pass. Both are fixed, and recorded above.

**Decided beyond scope.**
1. **A pending delete ends the navigation at the swipe;** Undo brings back the waypoint, not the navigation (accepted).
2. **`waypointsLoaded`** on `TrackRecordingUiState`, so "not loaded yet" is not read as deleted (accepted). If the waypoints never load, a navigation picked back up waits with no HUD and no message. The load failure itself shows in Records, as before.
3. **Navigate re-enters the view if panned away;** no new start zoom (accepted).
4. **Order:** Navigate, Directions, Details (accepted).
5. **The line's look:** blue 3 dp, 6 dp dashes; 0.4 when stale; grey at full opacity when lost. All provisional.
6. **Names kept from T7:** `returnControlIcon`'s parameter `isReturning`, which T7's test calls by name, now means "any navigation", and `arrivedAtStart` now means any target.
7. **The shared fixture `mapLayersViewModel`** gains two defaulted parameters.
8. **Removed:** a guard hiding the return's line during a waypoint navigation. Pausing the return already clears it, so the guard could not be tested.

**A slip of mine.** `343d8dfe`'s co-author trailer names a model, against this session's rule. It was not amended (push before you tidy); the planner noted it in the record.


## Addendum: the GPS side-by-side (RECORD -508)

The owner held that only the -502 build lost GPS indoors. So both builds were run in the same spot with the S22 untouched. Each was installed, force-stopped, launched and left for 120 s; then a screenshot and `dumpsys location` were taken.

| | main's build, `1.0.2654+ge295fbe1` | -502, `1.0.2678+g631b913b` |
|---|---|---|
| The strip | "Location services unavailable" | "Location services unavailable" |
| The app registered for GPS | yes | yes |
| The GPS chip's last fix | the same stale one, about 2 h old, 265 m | the same, unchanged |

**Not a -502 regression:** neither build had a GPS fix to use. The rough network position the phone did have, about 100 m, is refused by the existing 50 m gate in both. The owner then decided on a follow-up, to be its own dispatch: an approximate position shown until GPS arrives, never used for tracks, alerts or "Arrived", with the last known position marked old when there is nothing at all.

Evidence: `~/Zynergy/device-evidence/2026-10-04-waypoint-navigate/device/ab/`, local only (it shows positions).
