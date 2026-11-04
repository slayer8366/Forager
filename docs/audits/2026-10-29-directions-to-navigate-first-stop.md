# Directions to Navigate: first stop (look only)

Dispatch 2026-10-29-09 (`prompts/preserved/2026-10-29-09-directions-to-navigate.md` on `records-after-173`), RECORD -1817, -1818. Written by Hazel, 2026-10-29. No app code changed.

**Base.** Branch `directions-to-navigate` cut from `origin/pre-main` at `21d846b9e` ("Merge pull request #441"), which is the dispatch's stated base and was the remote head when fetched. Every file and line below was read at that commit.

## How Directions and Navigate work today

- **Directions** hands a `geo:` intent to the phone's maps app: `directionsIntent` and `launchDirections`, `ui/availability/AvailabilityPureFunctions.kt:152-190` (read). It has three production callers: `MapBubbleLayer.kt:144` (both bubble kinds), `RecordDetailsContent.kt:89` (the waypoint sheet) and `AvailabilityTripsWaypointsUi.kt:160` and `:300` (the two rows) (read, by grep of `ui/` and `MainActivity.kt`).
- **Navigate** is by waypoint id only. `AvailabilityViewModel.onNavigateToWaypoint(waypointId)` (`AvailabilityViewModelWaypointNavigation.kt:22-37`) stores a `WaypointNavigation(waypointId, …)`, and the screen resolves that id against the Records waypoint list (`domain/WaypointNavigation.kt:92-99`, `AvailabilityScreen.kt:240`). A point that is not a live Records waypoint cannot be navigated to through this path (read).
- The production callback is never null: `MainActivity.kt:680` passes `viewModel::onNavigateToWaypoint`; `ScreenMapBundles.kt:56` and `AvailabilityScreenMapInputs.kt:151` turn it into the start function with its Back origin (`AvailabilityScreenWaypointNavigation.kt:35-74`). The `null` defaults on the parameters are for tests (inferred from the defaults' doc comments, e.g. `JournalTab.kt:315`).

## Every place Directions is offered

| # | Control (file:line) | Screen | (b) Navigate there today | (c) Under the owner's ruling |
|---|---|---|---|---|
| 1 | Waypoint bubble, "Directions", `MapFeatureBubble.kt:92` (wired at `MapBubbleLayer.kt:144`) | **Maps tab**, a live waypoint's bubble | **Yes**: "Navigate" first, `MapFeatureBubble.kt:91`, through `MapRecordSources.onNavigateToWaypoint` (`AvailabilityScreenMapInputs.kt:151`, origin `MapBubble`, `MapBubbleLayer.kt:148-153`) | Navigate, Details. Directions removed. |
| 2 | Same control, `MapFeatureBubble.kt:92` | **Journal entry map**, a waypoint still in Records | **Yes, since RECORD -890** (`ecff1d75d`): `JournalTab.kt:690-695` passes Navigate with origin `JournalEntryMap`, and Back returns to the entry (`AvailabilityScreenWaypointNavigation.kt:66-71`) | Navigate, Details. Directions removed. |
| 3 | Same control, `MapFeatureBubble.kt:92` | **Journal entry map**, a waypoint kept only in the entry's snapshot (deleted from Records): `hasDetails = false`, `MapBubbles.kt:312-313` | **No**: Navigate is held back by `takeIf { content.hasDetails }`, `MapFeatureBubble.kt:91` ("one an entry knows only from its snapshot has nothing to navigate by"). The navigation path cannot reach it: the id is not in the Records list, so it would resolve to `Gone` (`WaypointNavigation.kt:96`) | **Owner's call.** Proposal: keep Directions here, since it would otherwise be the bubble's only action and the bubble would be left with none. |
| 4 | Waypoint details sheet, "Directions" button, `RecordDetailsContent.kt:88-94` (tag `RECORD_DETAILS_DIRECTIONS_TAG`, `RecordDetailsFormat.kt:109`) | Opened from the **Maps tab** bubble (`MapBubbleLayer.kt:162-185`), from a **Journal entry map** bubble (same layer, sources from `JournalTab.kt:672-696`), and from **Records** (`RecordsTab.kt:435-460`) | **Yes** in all three: "Navigate" shows first when `onNavigate` is passed, `RecordDetailsContent.kt:77-86`, with origins `MapDetails` and `RecordsDetails` | Navigate only. Directions removed. (The sheet is only reachable for a live waypoint, so case 3 does not arise here: the bubble's Details action is behind `hasDetails`, `MapFeatureBubble.kt:93`.) |
| 5 | Waypoint row, Directions icon button, `AvailabilityTripsWaypointsUi.kt:300-302` | **Records > Waypoints** (`WaypointsSection`, called at `RecordsTab.kt:380-387`) | **Yes**: Navigate icon before it, `AvailabilityTripsWaypointsUi.kt:295-299`, origin `RecordsRow` (`RecordsTab.kt:271`) | Navigate only. Directions removed. |
| 6 | Same `WaypointRow`, `AvailabilityTripsWaypointsUi.kt:300` | **Records > All** logbook (`RecordsLogbookList.kt:213`) | **Yes**: same Navigate button, same `navigateFromRow` (`RecordsTab.kt:377`) | Navigate only. Directions removed. |
| 7 | Planned-trip bubble, "Directions", `MapFeatureBubble.kt:113` (wired at `MapBubbleLayer.kt:144`) | **Maps tab** only: the entry map's sources carry no planned trips (`JournalTab.kt:672-696` sets no `plannedTrips`; default empty, `MapBubbles.kt:180`) | **No**, and not with the existing path: a planned trip is not a waypoint and has no id in the Records waypoint list. Offering it would need a new navigation target kind (a trip, or any point) through the ViewModel, the store that keeps a navigation across restarts, the HUD's target resolution and Back's origins: new capability, not a wiring change | **Owner's call.** Proposal: keep Directions. The dispatch's third quote ("route plan next") reads as if trips belong to the route-plan work; I have not assumed that. |
| 8 | Planned-trip row, Directions icon button, `AvailabilityTripsWaypointsUi.kt:160-162` | **Maps tab**, search panel > "Trip Planner" (`SearchControls.kt:68-70` > `TripPlannerSection` > `PlannedTripRow`, `AvailabilityTripsWaypointsUi.kt:100`) | **No**, for the same reason as 7 | **Owner's call.** Proposal: keep Directions, as 7. |

No other production site offers Directions: the remaining matches for "Directions" in `app/src/main` are doc comments (`MapBubble.kt:57`, `TrackExportPanel.kt:299`, `OpenMeteoCredit.kt:70`, `RecordDetailsTarget.kt:89`) and the return control's own icon (`ReturnControlText.kt:25`, `Icons.Filled.Directions`, which is not the Directions action and stays) (read).

## Where Navigate cannot be offered (the owner's call)

1. **A snapshot-only waypoint on a Journal entry map** (row 3). Proposal: keep Directions there.
2. **A planned trip's bubble on the Maps tab** (row 7). Proposal: keep Directions there.
3. **A planned trip's row in the Trip Planner** (row 8). Proposal: keep Directions there.

If the owner keeps Directions in any of these, `launchDirections` stays (the dispatch: "stays while anything still calls it"), and so do `DirectionsIntentTest` and the bubble's `MAP_BUBBLE_DIRECTIONS_TAG`. Only if all three lose it does `launchDirections` go, with its tests, in the same change.

## Premises that were wrong or have moved

- **"An entry map passes `null` and offers Directions only"** (dispatch, "What exists", Map bubbles). Not so at `21d846b9e`: since RECORD -890 (`ecff1d75d`) the Journal entry map passes Navigate (`JournalTab.kt:690-695`). What the entry map lacks Navigate for is only a waypoint gone from Records (row 3). The doc comments at `MapBubbles.kt:198-201`, `MapFeatureBubble.kt:53-55` and `RecordDetailsSheet.kt:51-52` still say an entry map offers Directions only; they are stale and would be corrected in the change.
- **Line numbers.** `MapBubbleLayer.kt:144` is the wiring, not the control; the controls are `MapFeatureBubble.kt:92` (waypoint) and `:113` (trip). `MapBubbles.kt:245` is the trip content's doc comment. The details-sheet lines (`RecordDetailsContent.kt:78-94`) and the row lines (`AvailabilityTripsWaypointsUi.kt:160`, `:300`) match.
- **"Whether in-app navigation can go to a trip's point today."** It cannot (rows 7 and 8).

## Could not determine

- Whether the owner's "the waypoints bubble in the maps" was meant to include the entry map's bubble. Read here as yes, because "Everywhere Navigate exists" covers it and Navigate exists there; flagged so the planner can confirm.
- What the owner wants a snapshot-only waypoint's bubble to show if Directions goes (an empty action row, or nothing at all). That is part of the call on row 3.

## Notes for the change (not decided, for the amendment)

- Files touched would be `MapFeatureBubble.kt` (253 lines), `MapBubbleLayer.kt` (216), `RecordDetailsContent.kt` (229), `AvailabilityTripsWaypointsUi.kt` (315), and, only if every site goes, `AvailabilityPureFunctions.kt` (303). All under 800 lines, so no split is needed first (read with `wc -l`).
- Tests that reference the Directions tag, label or intent today, which the change would have to update and not weaken: `AvailabilityScreenMapBubblesTest`, `AvailabilityScreenWaypointFlowTest`, `AvailabilityScreenWaypointNavigateTest`, `AvailabilityScreenLayoutTest`, `ReferenceTrailBubbleScreenTest`, `RecordDetailsSheetTest`, `RecordsFilterChipsTest`, `DirectionsIntentTest`, `INaturalistObservationIntentTest` (found by grep; contents not yet read).
- Removing Directions from rows 4 to 6 leaves Navigate as the only control in each. With `onNavigate` `null` (tests only), the sheet and the rows would show neither; whether that needs a production guard is not a question, since production always passes it (`MainActivity.kt:680`), but the tests that build these without it would need checking.
