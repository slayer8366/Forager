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

---

# Addendum: Amendment 1 built (RECORD -1605)

**Answer:** fixed. On the S22 with the PR build, after Stop, in the app or from the notification, the saved track's solid line is on the map in the first frame after Stop (about 1 s). There is no tab change. The dotted line is gone in the same frame.

**Helper:** Hazel. **Run:** 2026-10-24, 11:25 to 12:17 UTC. **Base:** pre-main 073fc77bd, checked against the remote before starting; #391 was at 3ad490b59.

## The fix (#391, f0360352e)
- `AvailabilityViewModel` takes `trackEnds: StateFlow<String?>`. It defaults to `MutableStateFlow(null)`, as `TrackRecordingViewModel` does.
- In `init` it runs `viewModelScope.launch { trackEnds.collect { endedTrackId -> if (endedTrackId != null) mapDisplay.loadMapRecords() } }`. The decision to reload stays in the ViewModel (RECORD -1450).
- `AvailabilityViewModelFactory` passes `container.trackEnds.latest`.
- `AvailabilityViewModel.kt` goes from 751 to 761 lines.
- No other screen reads `mapRecords`. The deleted-track gap is left alone, as the amendment says.

## The test
- **Where it lives.** `AvailabilityViewModelMapLayersTest`, "a track end reported through TrackEnds puts the finished track in the map records with no tab change". Its private `viewModel(...)` helper gained a `trackEnds` parameter.
- **What it does.**
  - The tab is shown while the track is recording, and the test asserts the track is not in the records yet.
  - The end is then stored, and `TrackEnds.report` is called after it, which is the service's order.
  - It then asserts the finished track is in `mapRecords`, with no `onMapShown` after the report.

## Runs
- **Fail-first: run 40090024619** (pull_request on 3598b1d70). That commit has the test, the constructor parameter and the factory wiring, and no collector.
  - Test job log: 0 `e:` lines.
  - Full suite: 5450 tests, 1 failed, 24 skipped.
  - The failure is the new test's last assertion: `the finished track is in the map records after Stop expected:<[RecordPolyline(recordId=track-1, …)]> but was:<[]>`. The XML timestamp is 11:37:29Z, inside the job.
- **Green: run 40091393614** (pull_request on f0360352e). Every job succeeded.
  - `unit-test-report` has 726 result files: 5450 tests, 0 failures, 0 errors, 24 skipped.
  - The new test is present and passes (class XML timestamp 11:58:02Z).
  - No flake re-run was needed.
- **Revert: run 40091409765** (workflow_dispatch filtered to the class, on `recording-line-clears-revert-2` 26f89d8bc).
  - Its diff from f0360352e is the collector line alone.
  - Log: 0 `e:` lines, and `assembleDebug` BUILD SUCCESSFUL.
  - XML: 8 tests, 1 failure, the same test with the same message (timestamp 11:53:23Z, inside the job). That failure is one this revert produces.
  - The revert was made in its own worktree; the original file was saved first. The collector is still present on `origin/recording-line-clears` at f0360352e.
  - Scope: one class. The production code differs from the head only by that line.
- **PR comment:** https://github.com/slayer8366/Forager/pull/391#issuecomment-6384042707

## The S22 (R5CT321008R)
- **The install.**
  - Downloaded the `app-debug-apk` of run 40091393614 into an empty directory outside every repository. `aapt2` reads 1.0.3906+gf86ae828.
  - f86ae828 is GitHub's merge of f0360352e into pre-main 073fc77bd, with no tree difference from f0360352e.
  - The phone's database read `user_version` 22, and the build declares 22 (`ForagerDatabase.kt:206`).
  - `adb install -r`, with no uninstall. `dumpsys` reads 1.0.3906+gf86ae828, and the database still reads 22.
- **Fixes:** gps and network test providers, whole-second `--time`, one a second. Tracks layer on.
- **Stop in the app (12:12:32.9 UTC, about 50 s recording):**
  - Before Stop, the dotted line lagged behind the walker.
  - Frames at +1.2, 2.0, 2.9, 5.9, 10.8 and 30.9 s: the solid line runs from the start to the walker in every one. The dotted line and the red Stop button are gone.
- **Stop from the notification (12:14:26.6 UTC, "Stop recording" in the expanded shade):**
  - Before Stop, the dotted line was drawn.
  - Frames at +1.7, 2.4, 3.3, 6.2, 11.2 and 31.2 s: the solid line is there in every one, and the dotted line is gone.
- **The test providers vanished again.** Both were removed at 12:11:36 UTC, about 3 s after the feeder added them, with no command of mine at that time. This is the same pattern as twice before.
  - The feeder logged one re-add for it, 0.5 s later at 12:11:36 ("set failed: IllegalArgumentException"), before Record at 12:11:41.
  - No removal happened during either recording.
  - Cause: could not determine.

## Phone left as found
- **Build:** 1.0.3906+gf86ae828 is installed (the PR build, the same schema).
- **Test data:** the two test tracks and three Start/End waypoints were deleted through the app. Afterwards: `user_version` 22, tracks 0, track_points 0, waypoints "Waypoint 1" only, offline_regions 1. The temporary database copies are deleted.
- **Area 1:** untouched.
- **Providers and location:**
  - Test providers removed at 12:16:26 UTC ("removed mock provider override" for both).
  - The mock-location appop is back to "default", and location is on.
  - The feeder has stopped.
- **Screen:** Maps tab.

## Disclosure
- **Confirmed vs inferred.**
  - Confirmed: every run figure above, read from the job logs and JUnit XML; the APK's commit and version; the database versions; the on-phone frames.
  - Inferred: that the solid line appears within about 1 s. The frames are about 0.8 s apart, and the first was taken 1.2 s after Stop.
- **Could not determine.** What removes the test providers.
- **Premises that were wrong.** The amendment names `AvailabilityViewModelTest`, and no class by that name exists: the ViewModel's tests are split by topic. The test went into `AvailabilityViewModelMapLayersTest`, which tests what `onMapShown` loads into `mapRecords`. That is a file outside the three the amendment named.
- **Decided beyond scope.**
  - Branch `recording-line-clears-revert-2` is left on the remote for review, as revert-1 was. Do not merge it.
  - Installing the new build restarted the app, so the Journal tab opened on Entries. I switched it to Records for the cleanup and did not check which chip it was left on.
