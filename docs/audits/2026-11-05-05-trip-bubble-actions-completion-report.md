# The trip pin's bubble gets Navigate, Plot route, Details and elevation; trips get a Details card; Details cards get Directions beside Navigate, centred: completion report (PR #494)

Dispatch `prompts/preserved/2026-11-05-05-trip-bubble-actions.md` (on `origin/records-after-173`), with:
- Amendment 1 (A8 to A12, B1, B2, C1 to C5; RECORD -2148);
- the Phase 1 report (`prompts/preserved/2026-11-05-05-phase1-report.md`, RECORD -2147). Its section 6 is the design;
- the planner's answer to this session's one stop (RECORD -2152): RoutePlottingBackOrderTest moves too.

Written by Hazel (Opus), 2026-11-05. This is a fresh session (C5). An earlier Hazel wrote Phase 1; this one built Phase 2 from that report and re-checked what it built on.

Each claim is marked:
- **read**: file and line at the named commit, or the named CI run's JUnit XML or log;
- **observed**: command output;
- **inferred**;
- **not verified**.

Line numbers are at **c99016cfa**, the last code commit, unless said otherwise.

## Base

- **pre-main at the start** (observed, `git fetch origin pre-main records-after-173`):
  - **ee0651fe0**, the dispatch's base;
  - records-after-173 at c03cd8545.
- **pre-main moved during the session:**
  - to **6e24b22c4**: PR #492, the crowded-icons test fix, and its audit row;
  - its four files (two tests, a report, the audit index) share none with this branch (observed, `comm` of the two `git diff --name-only` lists);
  - merged in at **2c2c3fd1f**, a merge with no conflict, both sides kept, never a rebase.
- **The branch:** `trip-bubble-actions`, cut from ee0651fe0, in `~/.cache/forager-wt/trip-bubble-actions`. Nothing in the planner's worktree was edited.
- **No database change.**
  - Nothing under `data/local/` or `schemas/` is touched (read, `git diff --stat ee0651fe0..HEAD`).
  - The one stored addition is a DataStore key, outside the migration sequence (CLAUDE.md, the Room/DataStore pitfall: a flat, current value that nothing joins to).
- **The other coders' branches,** as read at the start (observed, `git diff --stat origin/pre-main...origin/<branch>`):
  - `crowded-icons-test-fix`: two test files; it has since landed;
  - `wayto-end-log`: `FindWayToWaypointUseCase.kt` and one test;
  - the edge-shade probes: two test files;
  - `bug-14-picker-warning` was not on the remote.
  - None shares a file with this branch.
  - In `domain/wayto/` the only change is a new file holding the trip's words (A8, C3). `WayToWaypoint.kt` and `MapBubble.kt` are untouched.

## Premises re-checked before building on them

| Premise (dispatch, Amendment 1, Phase 1) | Verdict |
|---|---|
| The trip bubble is name, date, MGRS, `content.coordinates`, then Directions only (`MapFeatureBubble.kt:117-123`) | Confirmed at ee0651fe0 (read, `:117-124`) |
| A waypoint's elevation line reads a position, not a waypoint (`MapBubbleLayer.kt:247-253`) | Confirmed (read). The same read now serves the trip: `MapBubbleLayer.kt:122-123`. |
| Plotting takes a name and a position, a `RouteStop` (Phase 1's correction to A3) | Confirmed (read, `RoutePlottingViewModel.kt:103-114`; `RoutePlotDraft.kt:14-15`) |
| Navigate is keyed on a waypoint id all the way down | Confirmed at ee0651fe0 (read): `WaypointNavigation.kt:30`; `DataStoreWaypointNavigationRepository.kt:44-67`; `AvailabilityScreen.kt:240`; `AvailabilityWaypointLine.kt:46-52`; `AvailabilityWaypointRoute.kt:104-113` |
| `PlannedTripRepository` has no get-by-id | Confirmed (read, `PlannedTripRepository.kt:12-20`). The trip is read from `getAll()` instead (`NavigationTargetReader.kt:26`). |
| There's no "trips loaded" flag (`AvailabilityUiState.kt:117`) | Confirmed. It's added at `AvailabilityUiState.kt:124`. |
| The way-to notes that name the waypoint are `WayToWaypoint.kt:64, :70, :77` | Confirmed (read). They are the only HUD notes naming one; the waypoint's off-trail notice names none (`NavigationHudText.kt:92`). |
| On arrival the target's pin is left out under the ring (`CompactMapMapSlot.kt:92`) | Confirmed (read). The trip's flag gets the same at `CompactMapMapSlot.kt:91`. |
| A plain trip tapped while plotting opens its bubble (`CompactMapRoutePlotting.kt:88-92`) | Confirmed (read). Now `:88-99`. |
| The waypoint card's Navigate sits alone at the left (`RecordDetailsContent.kt:75-87`, `RecordDetailsParts.kt:127-130`) | Confirmed (read). The track card keeps `DetailsActions`, unchanged (C1). |
| **B2's list is the tests the rulings reverse** | **Incomplete, a wrong premise.** `RoutePlottingBackOrderTest.kt:117-134` opened its "bubble over plotting" with a plain trip, which A11 makes a stop. Stopped and reported. The planner said move it (RECORD -2152). |
| B2: `RecordDetailsSheetTest.kt:236` is reversed | **Partly wrong.** It, and the same assertion at `:611`, run only in sheets opened with no `onNavigateToWaypoint`. Under C1 there's no Directions without Navigate, so both hold as written; only their comments changed (agreed, RECORD -2152). |
| `AvailabilityViewModel.kt` 790, `AvailabilityCompactMapUi.kt` 782 (C2) | Confirmed (observed, `wc -l`). After: 790 and 766. |

## Commits and PR

| Commit | What |
|---|---|
| 4bf17da01 | The stubs: the types and entry points the tests name, with bodies that behave as today. |
| d60fdf67c | **Tests first, in their own commit.** Seven new classes, trip cases in four existing ones, and the B2 moves. |
| 1d33fec6a | Domain: a trip resolves as itself; the kind key; the trip's words. |
| 5f5ac33ff | ViewModel and HUD: start, follow and word it as a trip; the trips-loaded flag. |
| d57330988 | The trip bubble, the trip card and the centred Navigate and Directions. |
| ca1a6f295 | Screen: resolve by kind, start, Back to the trip, its gone message, its flag on arrival. |
| dec463602 | Route plotting: a trip tapped while plotting is a stop (A11). |
| c99016cfa | Three harness faults in the new tests, which the first build run showed (see "Failing first"). **The last code commit.** |
| 2c2c3fd1f | The merge of pre-main 6e24b22c4. |
| (this report) | This file only. |

PR #494 into pre-main, opened as a draft and marked ready when green. It merges only on the owner's word.

## What was built

### The trip bubble (A1, A3, A4, A6, A7)

`MapFeatureBubble.kt:121-136`, read:
- **The lines,** in order:
  - the trip's name;
  - its date (none for an undated trip);
  - its MGRS line;
  - `Elevation N ft` (or `m`, by Units) where the ground height is known, and no line where it isn't.
- **What's gone:** the latitude/longitude line. `MapBubbleContent.Trip` no longer carries coordinates (`MapBubbles.kt:270`, `:339-346`).
- **The actions:** Navigate · Plot route · Details, in the waypoint's FlowRow, so they wrap at large font as its do. There's no Directions.
- **The height:** read for the trip's spot by the same position-keyed read as a waypoint's (`MapBubbleLayer.kt:122-123`).
- **Navigate** (`MapBubbleTripActions.kt:16-22`): closes the bubble and navigates with an origin of kind TRIP, so Back reopens it there.
- **Plot route** (`:29-40`): closes the bubble and starts plotting from `RouteStop(trip.name, trip.location)`. A trip gone from the lists is logged and plots nothing.
- **Details** opens the trip's card.
- **Unchanged:**
  - the waypoint bubble (A6), including Directions for a snapshot-only waypoint (`MapFeatureBubble.kt:102-106`);
  - the 80% fill, which is the shared shell's (A7).

### The trip card (A4 as A9 rules it, A10, A12)

`RecordDetailsTripContent.kt:25-33`, read:
- **The rows:**
  - the name;
  - **Trip date** (A10), in the bubble's and the Trip Planner row's form, with no row for an undated trip;
  - MGRS;
  - Coordinates.
- **Then** Navigate and Directions.
- **No Created and no Used in** (A9). Everything shown is the trip as the Maps tab already holds it: no read, column or migration.
- **The target:** `RecordDetailsTarget.TripDetails`, saved as `trip:<id>` (`RecordDetailsTarget.kt:61`, `:75`, `:85`).
- **Where it resolves:** the sheet looks it up in `plannedTrips`, which only the Maps tab's bubble layer passes (`MapBubbleLayer.kt:215`; C1, "from the Maps tab bubble only"). Elsewhere a trip target closes the sheet, as a record gone from its list does (`RecordDetailsSheet.kt:131-136`).
- **Over the map:** the card is at 80%, as the sheet always is when opened from a bubble (`overMap = true`). A test pins it.

### Directions beside Navigate, centred (A5, C1, A12)

`RecordDetailsNavigateRow.kt:38-54`, read:
- **The row:** Navigate, then Directions to its right, centred in a `FlowRow` (`:42`), so at a large font they wrap onto two centred lines rather than clip.
- **Directions** hands the location to a maps app through `launchDirections`, as the Trip Planner row does.
- **Navigate** closes the card. **Directions leaves it open** (A12).
- **Both buttons are outlined,** with no fill of their own, so over a map the card's fill alone carries the 80%.
- **Where it shows:**
  - the waypoint's card, wherever it opens with Navigate: the Maps tab, Records and a journal entry's map (`RecordDetailsContent.kt:77`);
  - the trip's card;
  - only where there's Navigate (C1).
- **The track card's row is unchanged.**

### Navigate to a trip as itself (A8)

- **The kept navigation:**
  - `WaypointNavigation.kind`, defaulting to WAYPOINT (`WaypointNavigation.kt:33`). The field `waypointId` holds a trip's id when the kind is TRIP; its name is kept from -502, as the KDoc says.
  - DataStore writes `waypoint_navigation.target_kind = "trip"` for a trip only (`DataStoreWaypointNavigationRepository.kt:73-76`, `:87`).
  - **A missing key reads as a waypoint** (`:60`). So a navigation kept before the update resumes as before, and a waypoint's is kept in exactly its two old keys. The existing "two keys" test still holds.
  - An unknown kind is refused as a failed read (`:62`), as a missing return flag is.
- **Resolving** (`TripNavigation.kt:39-54`):
  - a waypoint's navigation resolves exactly as before;
  - a trip's resolves against the trips only, never the waypoints;
  - before `plannedTripsLoaded` it waits; after, a trip that isn't there is gone (`AvailabilityScreen.kt:242`).
  - The flag is set by the first good read (`AvailabilityPlannedTrips.kt:34`). A failed read leaves it as it was and is logged, as the waypoints' flag is.
- **The target:**
  - Found, the trip is `PlannedTrip.asNavigationTarget()` (`TripNavigation.kt:30-31`): a value in memory, never stored, so nothing new reaches Records.
  - The HUD, the line, the arrival and the way along trails read only its id and position (read: `NavigationReadout.kt:85`, `NavigationArrival.kt:25`, `:56`, `:91`, `AvailabilityWaypointRoute.kt:113`, `CompactMapArrivedAt.kt:18-19`, `AvailabilityPureFunctions.kt:70`).
  - It has no creation time, and the waypoint shape needs one. It is 0, and nothing reads a target's (grep of `ui/availability`, observed). This is a call of mine; see "Decided beyond scope".
- **Starting:**
  - `onNavigateToTrip` shares `onNavigateToWaypoint`'s body, with the kind kept (`AvailabilityViewModelWaypointNavigation.kt:23-47`), so a return is paused and picked back up as for a waypoint.
  - The screen dispatches by the origin's kind (`AvailabilityScreenWaypointNavigation.kt:39`).
  - `MainActivity.kt:708` passes it.
- **Following:**
  - The ViewModel follows the target's id and kind (`AvailabilityViewModel.kt:407`).
  - The line and the way along trails read the target through `navigationTargetReader` (`NavigationTargetReader.kt:20-28`; `AvailabilityViewModel.kt:324`, `:327`): a waypoint from Room by id, as before; a trip from the stored trips.
  - Reading the store, not the loaded list, means a trip navigation picked back up at start finds its trip whichever read lands first.
  - Nothing moves a saved trip (both save paths write a new id, and there's no edit), so the copy doesn't go stale.
- **The words:**
  - `WayToWaypointNote.textFor(kind)` (`WayToTripNotes.kt:11-19`): the three notes that name the waypoint name the trip, whole and short (`NavigationReadout.kt:195`, `:212`; `NavigationHudText.kt:108-109`).
  - The way-to carries the kind into `ReturnRoute.Straight` (`AvailabilityWaypointRoute.kt:261`).
  - A trip deleted meanwhile, or while the app was closed, ends the navigation with "Stopped navigating: that trip was deleted." (`AvailabilityScreenDeclarations.kt:41-47`; `AvailabilityScreen.kt:576`).
- **Back:**
  - A trip's bubble and card are origins of kind TRIP, saved as `trip-bubble:` and `trip-map-details:`. A waypoint's keep the strings they had (`WaypointNavigationOrigin.kt:58-94`).
  - Back reopens the trip's bubble or its card (`NavigationReopen.kt:26-49`, called at `AvailabilityCompactMapUi.kt:287` and `CompactMapBubble.kt:102`).
  - Both handlers' order is unchanged: no new Back handler.
- **On arrival:**
  - The trip's flag is left out under the ring (`CompactMapMapSlot.kt:91`).
  - It's matched by id, as the pin is: trip and waypoint ids are both random UUIDs (read, `SavePlannedTripUseCase.kt:28`, `CreateWaypointUseCase.kt:15`, `ImportGpxUseCase.kt:42`), so a waypoint target, or the start, never names a trip.

### A trip tapped while plotting (A11)

`CompactMapRoutePlotting.kt:88-99`, read:
- A trip with no route, tapped while plotting, is added as the next stop under its name, as a waypoint is.
- A trip with a route still opens its route.
- Not plotting, a trip opens its bubble.

### The two large files (C2)

- **`AvailabilityViewModel.kt`:** 790 before, 790 after (observed).
  - An unused import was swapped for the one it needed.
  - Three lines were edited in place; the reader is in `NavigationTargetReader.kt`.
- **`AvailabilityCompactMapUi.kt`:** 782 before, 766 after.
  - The bubble reopen's lookup moved to `NavigationReopen.kt`, with the `LaunchedEffect` kept in the same place. Effect order is unchanged.
  - The log tag went with it, and four imports were no longer used.
- **No split was needed.**

### Files

New files:

| File | Lines | One job |
|---|---|---|
| `domain/TripNavigation.kt` | 54 | the kind, the trip as a target, the resolution |
| `domain/wayto/WayToTripNotes.kt` | 19 | the notes in a trip's words |
| `ui/availability/NavigationTargetReader.kt` | 28 | reading a target by kind |
| `ui/availability/NavigationReopen.kt` | 52 | what Back reopens |
| `ui/log/RecordDetailsNavigateRow.kt` | 54 | Navigate and Directions, centred |
| `ui/log/RecordDetailsTripContent.kt` | 39 | the trip card |
| `ui/map/MapBubbleTripActions.kt` | 51 | the trip bubble's and card's actions |

The 29 other main files changed by a few lines each (observed, `git diff --stat ee0651fe0..c99016cfa -- app/src/main`). The largest after are `MainActivity.kt` 768, `AvailabilityScreen.kt` 746 and `MapBubbles.kt` 541, so none goes past 800.

## Every changed user-facing string

| Where | Before | After |
|---|---|---|
| Trip bubble | the latitude/longitude line, e.g. [a point near the owner's home; removed 2026-11-06] | gone |
| Trip bubble | "Directions" | gone |
| Trip bubble | (none) | "Elevation 1352 ft" / "Elevation 412 m" (the waypoint bubble's existing form) |
| Trip bubble | (none) | "Navigate", "Plot route", "Details" (existing words) |
| Trip card (new) | (none) | the trip's name; **"Trip date"** (new, A10) with the date; "MGRS"; "Coordinates"; "Navigate"; "Directions" (all existing but "Trip date") |
| Waypoint card | Navigate alone, at the left | "Navigate" then "Directions", centred ("Directions" is the existing word, as before -1819) |
| HUD, navigating to a trip | "The waypoint is far from any trail · straight line" | "The trip is far from any trail · straight line" |
| HUD, navigating to a trip | "The waypoint is in another area · straight line" | "The trip is in another area · straight line" |
| HUD, navigating to a trip | "No clear way in to the waypoint · straight line" | "No clear way in to the trip · straight line" |
| HUD short forms, for a trip | the waypoint's | the same three without " · straight line" (the existing short-form rule, RECORD -1652 Q4) |
| Snackbar, a trip deleted while navigating | (none) | "Stopped navigating: that trip was deleted." |

Waypoint targets keep every word exactly (`WayToTripNotesTest`'s third case pins all thirteen notes). No other new words. The new log lines are not user-facing.

## Failing first

**The tests-first run:** CI 42110501957 on d60fdf67c, the stubs plus the tests (read, its JUnit XML; no compile errors in its log). 6,694 tests, **45 failures**, 0 errors, 24 skips, all 45 in this dispatch's classes:

| Class | Failed over the stubs | Passed over the stubs (not shown to bite there) |
|---|---|---|
| TripBubbleActionsTest | 7 of 7 | none |
| TripDetailsCardTest | 6 of 6 | none |
| TripNavigateTest | 10 of 11 | the kept-waypoint guard |
| TripWhilePlottingTest | 2 of 3 | the not-plotting guard |
| WaypointCardDirectionsTest | 3 of 4 | Navigate still navigates (a guard) |
| TripNavigationTargetTest | 5 of 8 | three waypoint and no-kind guards |
| WayToTripNotesTest | 2 of 3 | the waypoint-words guard |
| DataStoreWaypointNavigationRepositoryTest | 3 of 6 new | the before-update read (bitten by r2), a waypoint after a trip (bitten by r2), clearing a trip |
| WaypointNavigationOriginSaverTest | 1 of 2 new | the waypoint strings (a guard) |
| RecordDetailsTargetSaverTest | 1 (the trip case's stub error) | |
| MapBubblesTest, AvailabilityScreenMapBubblesTest, AvailabilityScreenWaypointNavigateTest (2), RecordDetailsSheetTest | the B2 moves | RoutePlottingBackOrderTest's move (its glyph already opened a bubble) |

- **Why each failed:** every scroll or touch failure over the stubs reads "could not find any node" for the missing control (read, the XML's full text). Every other one names the stub's own value, for example `kind=WAYPOINT` and `expected Found, was Waiting`.
- **What the first build run showed** (CI 42113268718 on dec463602; no compile errors): 13 reds, all in the new tests, from three harness faults of mine.
  - The touch helpers scrolled every target, and a bubble's buttons sit in nothing that scrolls ("no parent layout with a Scroll SemanticsAction").
  - A label was read from the unmerged tree.
  - **TripWhilePlottingTest's trip sat exactly on the route fixtures' Creek,** so the existing "Ignore the second tap" rule refused it (`RoutePlotDraft.kt:14-15`). Over the stubs this red couldn't be told apart from the missing behaviour, so **the tests-first red of its two cases is not evidence**; revert r4 is.
  - The other two faults sat behind reds that were already right over the stubs. Fixed at c99016cfa; no assertion was weakened.

## Revert checks

Each check:
- made one edit, on its own branch `trip-bubble-actions-rN` cut from c99016cfa;
- ran with `gh workflow run ci.yml --ref <branch> -f tests=...`;
- was read compile log first: every run had **0 compile-error lines** (read, `gh run view --log`, grepping `e: file:///`, `Compilation error` and `Execution failed for task ':app:compile`);
- then had its JUnit XML read;
- restored its file from a copy saved before the edit, never from git;
- was followed by a check that the forward file is still intact at c99016cfa (observed, `git diff --quiet`).

Each run's head sha is its revert commit. Each test total matches its filtered classes.

| # | One edit | Run | Tests in the filter | Failed, each with an edit-specific message |
|---|---|---|---|---|
| r1 | **The trip bubble's actions:** Details replaced by the old Directions (`MapFeatureBubble.kt:133`) | 42120191432 | 28 (TripBubbleActions 7, TripDetailsCard 6, AvailabilityScreenMapBubbles 15) | 10: "no Directions expected:<0> but was:<1>"; the Details control missing ("Failed: assertExists", "Failed to inject touch input") in the actions test, the Details touches and all six card tests; the B2 trip test's Directions count |
| r2 | **The kind key:** a missing kind read as a trip (`DataStoreWaypointNavigationRepository.kt:60`) | 42120219442 | 12 | 4: every waypoint read comes back `kind=TRIP`. These are the before-update read, the round-trip, the app opened again, and a waypoint after a trip. |
| r3 | **The centred row:** `spacedBy(Spacing.sm)` with no centring (`RecordDetailsNavigateRow.kt:42`) | 42120231228 | 10 (TripDetailsCard 6, WaypointCardDirections 4) | 4: "the pair is centred: its middle 97.333336.dp, the card's 192.0.dp", on the trip card and the waypoint card on the Maps tab, in Records and on an entry map. Directions-to-the-right still passed. |
| r4 | **The add-a-stop path:** the plotting branch deleted (`CompactMapRoutePlotting.kt:94`) | 42120245093 | 6 (TripWhilePlotting 3, RoutePlottingBackOrder 3) | 2: the stops stay `[Creek]` with the trip at [a point near the owner's home; removed 2026-11-06] not added. The not-plotting guard and the moved back-order tests passed. |
| r5 | The trip's words: its WAY_IN_BLOCKED line deleted (`WayToTripNotes.kt:16`) | 42120256677 | 14 | 3: "WAY_IN_BLOCKED for a trip: 'No clear way in to the waypoint · straight line'" twice, and the HUD's "the trip's words, whole or short: 'No clear way in to the waypoint · straight line'" |
| r6 | Waiting for the trips: an unloaded trip taken as gone (`TripNavigation.kt:52`) | 42120286473 | 19 | 2: "expected:<Waiting> but was:<Gone>", and the screen's "still kept ... but was:<null>" |
| r7 | The flag on arrival: trips passed unfiltered (`CompactMapMapSlot.kt:91`) | 42120299910 | 34 (TripNavigate 11, AvailabilityScreenWaypointNavigate 23) | 1: "the trip's flag is left out under the ring". The waypoint arrival test passed. |
| r8 | Elevation: the trip's height not read (`MapBubbleLayer.kt:123`) | 42120316113 | 13 (TripBubbleActions 7, WaypointBubbleElevation 6) | 4: the Elevation lines missing (412 m, 1352 ft, their bounds), and "asks only for the trip's spot" asked nothing. The waypoint's six passed. |
| r9 | Back to the trip's card: a waypoint card reopened (`NavigationReopen.kt:47`) | 42120331233 | 11 | 1: "the card is open again" |

The nine branches stay on the remote as the record of what each run built, and are never merged. Their worktrees in scratch are removed.

## CI

- **c99016cfa** (CI 42116552506, the last code commit; read, its JUnit XML): every job green, **6,694 tests, 0 failures, 0 errors, 24 skips**.
  - 966 result files from `testDebugUnitTest` and 6 from `testDebugUnitTestOlderSdks`.
  - The base, ee0651fe0 (CI 42094523206), had 6,644 tests in 965 files with the same 24 skips. 6,644 + 50 new tests = 6,694, and 965 + 7 new classes = 972 (observed, both runs' XML).
- **2c2c3fd1f** (the merge of pre-main 6e24b22c4; CI 42125306656; read, its JUnit XML): every job green, **6,695 tests, 0 failures, 0 errors, 24 skips**, 966 + 6 result files.
  - The one test more is pre-main's: PR #492 took `AvailabilityScreenCrowdedIconsSettingTest` from 3 cases to 4. It is green here.
  - This is the last real commit. This report's own commit changes no code.

## Device check, for the planner (cheapest first)

Requires the APK of the run on the merge commit (its `app-debug-apk` artifact). The S22. Each step's evidence is a screenshot unless it says otherwise.

1. **Trip bubble.** Maps tab, tap a trip flag with no route.
   - **Pass:** it reads name, date, MGRS, then Navigate · Plot route · Details, with no latitude/longitude and no Directions.
   - Inside a downloaded area it also shows "Elevation N ft" under the MGRS line; outside, no such line.
   - The owner's trip near the 562 ft waypoint is the cheap case: likely to show a line, inferred.
2. **Trip card.** Tap Details.
   - **Pass:** name, "Trip date" with the date, MGRS, Coordinates, then Navigate and Directions side by side and centred, Directions on the right.
   - No Created or Used in row.
   - The card is see-through over the map, as the waypoint's.
3. **Directions.** On the trip card, tap Directions.
   - **Pass:** the maps app opens at the trip.
   - Back to Forager: the card is still open.
4. **Waypoint card.** Tap a waypoint, then Details.
   - **Pass:** Navigate and Directions centred, Directions on the right.
   - Directions opens the maps app at the waypoint.
   - The same in Journal > Records > Waypoints, tapping a row.
5. **Navigate to the trip.** On the trip bubble, tap Navigate.
   - **Pass:** the navigation view, with the HUD aimed at the trip and the dashed line to it; inside an area with trail routes, the way along trails.
   - **Back:** the navigation ends and the trip's bubble is back.
   - Repeat from the card: Back reopens the card.
6. **Pick back up.** Start Navigate to the trip, then swipe the app away and reopen it.
   - **Pass:** it comes back navigating to the trip, not ended with a message.
7. **Plot route.** On the trip bubble, tap Plot route.
   - **Pass:** the route card shows one stop named for the trip.
   - Then tap another trip's flag (one with no route): it joins as the next stop, with no bubble.
8. **Arrival:** an observation, not a gate (needs a walk to a trip). Within about 15 m of the trip, the ring shows and the trip's flag is gone under it. "Arrived" in the HUD.
9. **Deleted while navigating.** Navigate to a trip, then delete it in Tools > Trip Planner.
   - **Pass:** the snackbar "Stopped navigating: that trip was deleted."
10. **A trip's note:** an observation. Only where the trail engine refuses the trip's end, so set up from the map. The HUD reads one of the three trip notes, never "waypoint".

**Device-only, by construction:**
- the 80% look;
- the centred row against real insets at the sheet's foot (CLAUDE.md, Robolectric's zero insets);
- the maps app hand-off;
- the elevation from real tiles;
- MapLibre leaving the flag out on arrival. The tests see what the map is handed, not what it draws.

## What is not tested, said plainly

- **Large font:** the centred pair's wrap onto two lines is not tested. The `FlowRow` is the bubble's own choice, but no test runs the card at 2.0.
- **Landscape:** the trip card's layout in a landscape window, and the trip bubble there, are not tested.
- **A real trail route:** the way along trails to a trip on a real route is tested only by the search being asked for the trip's spot and by a straight-line answer. That the route draws for a trip is inferred from it being the waypoint's code path, with the trip's position.
- **Records:** a trip card can't open from Records (C1), so nothing tests that.
- **Choosing across kinds:** a trip chosen while navigating to a waypoint (and the other way round) keeps the paused return through the shared start body. This is inferred, not tested.

## Decided beyond scope, for the planner to accept or reject

1. **The trip as a waypoint-shaped value in memory** (`asNavigationTarget`), so the HUD, line, arrival and way-to take it unchanged.
   - Its creation time is 0, read nowhere.
   - The alternative was a new target type through the HUD and the 1,440-line scaffold, which C2 and the scaffold's size argue against.
2. **The kind key is written for a trip only.** A waypoint's stays in exactly its two old keys, and the old "two keys" test stands.
3. **The trip is read from the stored trips** for the line and the way-to, not from the screen's loaded list, so a picked-up navigation never misses it by read order.
4. **The trip's Navigate is offered on a host that passes `onNavigateToTrip`:** only the Maps tab, and MainActivity does. An entry map has no trips.
5. **Two flags on one spot after a route from a trip** is the crowded-icons case, unchanged (A12, as ruled).
6. **`RecordDetailsSheet` takes two more parameters,** defaulted, rather than a bundle. The Phase 1 report raised a bundle; not done, to keep the change small.
7. **The field name `waypointId`** is kept on `WaypointNavigation` and the two origins, holding a trip's id with kind TRIP, with KDoc saying so. Renaming it would touch its readers across main (about 17 by a rough grep), including the origin saver and `JournalTab.kt`, for a name.

## Slips

- **The test helpers and a fixture coincidence** cost one extra build run (above).
- **API rate limit:** a GitHub API rate limit (a 403 shared across sessions) held the green run's result for about 20 minutes. Nothing was lost.
- **A backup branch:** while the tests-first run was in flight, the build commits were pushed to a backup branch, `trip-bubble-actions-wip`, so they weren't only local. A push to the PR branch would have cancelled that run. The backup was deleted once the PR branch had them.

Scratch at `/mnt/work/scratch/trip-bubble-actions/` is deleted at the end of the session.
