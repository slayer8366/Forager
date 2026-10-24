# Report: the saved track shows on the map right after Stop (dispatch 2026-10-24-07), stopped after part 1

**Answer:** stopped after part 1, as the dispatch's stop condition requires. No code was written and nothing was pushed to #391.

The cause is outside the recording screen. The map's Tracks layer draws from the Maps tab's own snapshot of saved records, `AvailabilityUiState.mapRecords`. That snapshot belongs to `AvailabilityViewModel`, not `TrackRecordingViewModel`. It is read only when the Maps tab comes into view, and after a restore. Nothing connects a track's end to it. #364 connected the service's end report (`TrackEnds`) to the Records list only.

So any fix has to add a trigger in `AvailabilityViewModel` and its factory, or in `MainActivity` or `AvailabilityScreen`. None of those is the recording screen's ViewModel or one of its collaborators. Only the read the trigger would call, `AvailabilityMapDisplay.loadMapRecords`, is "the map-data refresh".

The delay is confirmed on the S22 for both Stop paths: no solid line for 45 s after Stop. It appeared at once after a Journal → Maps tab change.

**Helper:** Hazel. **Run:** 2026-10-24, 11:14 to 11:24 UTC. **Phone:** S22 (R5CT321008R). **Code read at:** `recording-line-clears` 3ad490b59 (clean worktree, equal to origin).
Screenshots stay off GitHub (`~/.cache/forager-shots-1024/r07/`). There are no positions here. The test spot was public downtown streets, far from Area 1.

## Part 1: the cause, step by step (file:line at 3ad490b59)

**What the Tracks layer draws (reachable from production; every link read):**
1. `OverlayFeatures.kt:63` sets the kept-tracks source from `keptTrackPolylines`. The refresh effect is keyed on that list (`MapDataRefresh.kt:57-67`), so the map redraws as soon as the list changes. The draw side is not the problem.
2. That list comes through `SightingsMap.kt:617` ← `MapSlot.kt:125` ← `CompactMapMapSlot.kt:94` (`mapLayers.records.trackPolylines`) ← `AvailabilityScreenMapInputs.kt:42` (`records = mapRecordsDrawn`) ← `AvailabilityScreen.kt:441` (`uiState.drawnMapRecords(logUiState)`, `DrawnMapRecords.kt:17`). That is `AvailabilityViewModel`'s state.
3. The data is `AvailabilityUiState.mapRecords` (`AvailabilityUiState.kt:361`). It is filled by `GetMapRecordsUseCase` (`AppContainer.kt:366`, passed in at `AvailabilityViewModelFactory.kt:50`). That use case keeps a track only once `endedAtEpochMillis` is set (`GetMapRecordsUseCase.kt:102-103`).

**What refreshes it (the only writers of `mapRecords` from the database):**
4. `AvailabilityMapDisplay.loadMapRecords` (`AvailabilityMapDisplay.kt:103-108`) is called from two places only:
   - `onMapShown` (`AvailabilityMapDisplay.kt:52-53`, via `AvailabilityViewModel.kt:581` and `MainActivity.kt:529`). The trigger is `LaunchedEffect(isMapsTabShown) { if (isMapsTabShown) onMapShown() }` at `AvailabilityScreen.kt:686-689`, which its own comment calls "the Maps tab's freshness mechanism".
   - `reloadAfterRestore` (`AvailabilityViewModel.kt:733-737`).
5. The other three writers only remove a deleted find, photo or region from the snapshot (`AvailabilityMapDisplay.kt:71-99`). None of them touches tracks.

**Why a tab change shows the track and Stop does not:**
6. A tab change flips `isMapsTabShown`, so step 4 re-reads the database. By then the service has stored the end, so the track passes `:102` and draws.
7. Stop never reaches `AvailabilityViewModel`. The service stores the end and reports it (`TrackRecordingService.kt:388-389`, `container.trackEnds.report(trackId)`; `TrackEnds.kt:26`, held at `AppContainer.kt:189`).
   - That report has one collector: `TrackRecordingViewModel.kt:366` → `onTrackEnded` (`:411-414`) → `loadTracks()`, which feeds the Records list.
   - `git grep trackEnds` in `app/src/main` finds no other reader. The factory hands it only to `TrackRecordingViewModelFactory.kt:38`.
   - Recording happens on the Maps tab, so the tab stays shown through Stop, and `isMapsTabShown` does not change.
   - The snapshot taken when the tab was shown has no entry for this track. If that recording was running then, its row was still open and `:102` dropped it.

**What #364 and #367 changed** (merges 831ee1949 and e41505601):
- #364 (`00ba9712e`, dispatch 2026-10-21-02, RECORD -1521/-1527) added `TrackEnds`. The service reports a stored end, and `TrackRecordingViewModel` reloads its track list. That is "a finished track shows in Records right after Stop".
- #367 (`c71529554`, dispatch 2026-10-21-04) made the same report resync the recording screen's state after a Stop from the notification.
- Neither touches the Maps tab's records. So Records refreshes on Stop and the map does not, as the dispatch suspected.

**Same root, seen once before (inferred, not tested):** the 2026-10-24-04 report's side observation was a deleted track's solid line staying on the map until a tab change. That fits the same snapshot. `MapRecords.withoutPending` and `drawnMapRecords` filter pending and committed finds, photos and regions, but have no track parameter (`DrawnMapRecords.kt:17-29`). A track delete therefore never leaves the snapshot until the next `onMapShown`. I did not reproduce it.

## Part 1: the phone (build 1.0.3904+gb4206e22, no install)
- **Database before:** `user_version` 22, tracks 0, track_points 0, waypoints "Waypoint 1", offline_regions 1. The Tracks layer was on (checked in the Layers sheet).
- **Fixes:** gps and network test providers, whole-second `--time`, one a second. The feeder re-adds the providers if a set fails and logs each re-add.
  - Log: two re-adds, both at feeder starts (11:15:27, 11:18:18 UTC). None mid-run.
  - `dumpsys location` shows no removal between those adds and my own removal at 11:23:20 UTC. The outside removal seen twice before did not happen this time.
- **Run A, Stop in the app (Stop at 11:16:49.98 UTC):**
  - Before Stop, the dotted line was drawn behind the walker.
  - From the first frame (+1.0 s) through +2, 5, 10, 20, 30 and 45 s: record button idle, no dotted line, **no solid line**.
  - The fixes kept moving during that window. At +0.3 to +20 s the stop point and the route south of it were on screen, and the route was blank.
  - After Journal → Maps and a pan back to the route: the solid line is drawn, ending at the stop point.
- **Run B, Stop from the notification ("Stop recording", at 11:21:03.62 UTC):**
  - I stopped feeding fixes before the tap, so the camera stayed put.
  - Before Stop, the dotted line was drawn behind the walker.
  - From the first frame (+1.4 s, shade closing) through +45 s: idle, no dotted line, **no solid line**. The route was on screen throughout.
  - After Journal → Maps (11:22:02): the solid line is drawn at once.
- **So, both paths:** the dotted line goes at once, and the saved track does not appear until a tab change: at least 45 s in both runs, with no sign it ever would on its own.

## Why I stopped (the binding condition)
The fix needs something to tell `AvailabilityViewModel` that a track has ended. Every place that could do it is outside the scope part 1 allows:

- **Option 1: the same pattern as #364, aimed at the map.** `AvailabilityViewModel` collects `TrackEnds.latest` and calls `mapDisplay.loadMapRecords()` on each id. `AvailabilityViewModelFactory` passes in `container.trackEnds.latest`. A test would go in `AvailabilityViewModelTest`.
  - Files: `AvailabilityViewModel.kt` (751 lines, about 10 more), `AvailabilityViewModelFactory.kt` (62), and maybe a named entry point in `AvailabilityMapDisplay.kt` (268).
  - The decision to reload stays in a ViewModel function (RECORD -1450).
  - Side effect on the Maps tab (inferred): the reload re-reads all four kinds, so a find, photo or region saved since the tab was shown would also appear at a track's end. The Layers sheet's records and the Journal-entry halos follow the same snapshot.
  - No other screen reads `mapRecords`.
- **Option 2: wiring in `MainActivity`.** When `TrackRecordingViewModel`'s track list reloads, call `AvailabilityViewModel.onMapShown()`. This touches `MainActivity` and also re-asks the forecast store.
- **Option 3: draw finished tracks from `TrackRecordingViewModel`'s own list.** This changes the Tracks layer's data source, which is a larger change.

My recommendation, if the scope is widened: option 1. It is the smallest, it mirrors the shipped #364 mechanism, and the end is reported only after it is stored, so the reload can never read the row still open (`TrackEnds.kt` doc).

Option 1 could also close the track-delete gap above, by a track parameter in `withoutPending`. That is a separate change and is not asked for.

## Phone left as found
- **Build:** 1.0.3904+gb4206e22, unchanged; nothing was installed.
- **Test data:** both test tracks and the three Start/End waypoints were deleted through the app (Journal → Records, swipe). Run B has no End waypoint, which is the known gap after a Stop from the shade.
- **Database afterwards:** `user_version` 22, tracks 0, track_points 0, waypoints "Waypoint 1", offline_regions 1. Temporary copies deleted.
- **Area 1:** untouched.
- **Test providers:** removed ("removed mock provider override" logged for both at 11:23:20 UTC). The shell's mock-location appop is back to "default". Location is on.
- **Screen:** on the Maps tab, with the Records chip back on Waypoints. A Trip Planner drawer was open when I arrived; I closed it with Back before starting.

## Disclosure
- **Confirmed vs inferred.**
  - Confirmed on the device: the delay on both paths, the solid line after a tab change, the database counts before and after, and the provider events.
  - Read from code at 3ad490b59: steps 1 to 7. The `trackEnds` reader list comes from `git grep`.
  - Inferred, not run: the track-delete gap; option 1's side effects; that the solid line never appears without a tab change (I watched for 45 s).
- **Could not determine.** Whether some other event (rotation, a restore) would refresh the snapshot sooner in normal use. By the code, only a restore would.
- **Premises that were wrong.** The dispatch scoped the fix to "the recording screen's ViewModel or collaborators, the map-data refresh". The data's owner and its only refresh trigger belong to the Maps tab's ViewModel, not the recording screen.
- **Decided beyond scope.**
  - I closed a Trip Planner drawer that was open on arrival.
  - The feeder started each run from a fresh point, so run B did not overlap run A.
