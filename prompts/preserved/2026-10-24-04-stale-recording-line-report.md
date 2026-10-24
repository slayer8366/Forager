# Report: does the recording line clear after Stop and delete? (dispatch 2026-10-24-04, S22, pre-main 073fc77bd)

**Answer:** the dotted recording trail never clears on its own. It survives Stop (from the app and from the notification), 30 s of watching, a tab change, backgrounding, and deleting the track. Only the next Record clears it, or the app process ending. By the code's own stated intent that is a bug: `breadcrumbPoints` is documented as "Empty whenever [activeTrack] is null", and `clearRecordingState` never empties it.

**Helper:** Hazel. **Run:** 2026-10-24, 09:35 to 09:52 UTC. **Phone:** S22 (R5CT321008R), build 1.0.3901+g073fc77b (unchanged).
Screenshots stay off GitHub. No positions here; the test spot was a public downtown street, far from Area 1.

## What the line is
- The line is the **"Recording trail"** overlay: dotted purple, source `MapSourceIds.BREADCRUMB`, toggled in Layers as "Recording trail".
- Confirmed on the device: turning that toggle off hid the line, and turning it back on brought it back.
- The saved-track overlay ("Tracks") is a different line: solid purple. It draws above the trail (`GetMapRecordsUseCase.kt:66`). So after a saved track loads on the map, the solid line hides the stale dotted one. To see the dotted one, I turned Tracks off.

## Steps (fixes from a gps test provider, whole-second `--time`, 1 a second)

0. **Found on arrival.** Before any new run, the dotted trail of the 2026-10-24-03 check's last recording was still drawn. That was about 12 minutes after its Stop and after its track was deleted. The app was not restarted in between. It was still there after Home and reopen.

1. **Stop in the app, watch 30 s: the line stays.**
   - Run A (26 s, 6 stored points): no line ever drew, before or after Stop. The 15 s poll (`TrackRecordingViewModel.kt:846`, `POLL_INTERVAL_MILLIS = 15_000L`) hadn't delivered two points while recording, so there was nothing to go stale. Run A says nothing about clearing.
   - Run B (about 70 s, 11 points): the dotted line drew partway through the recording. It covered only the points polled so far, so it lagged the walker. It was unchanged at 0, 10, 20 and 30 s after Stop.

2. **Tab change, then Home and reopen: the line stays.** After the Journal and Maps round trip, the saved track's solid line covered the whole walk. With Tracks turned off, the dotted trail was still underneath, unchanged. After Home and reopen, same.

3. **Delete the track from Records, then 30 s, tab change, background: the line stays.** Records listed "Tracks 0" and "No recorded tracks yet." The dotted trail stayed through all three checks, unchanged.
   - Side observation, not the dispatch's question: after the step-4 delete, one deleted track's **solid** saved-track line stayed on the map for 30 s or more. It went at the next tab change. That track's delete was the second of two, about 9 s apart, and I opened Maps about 10 s after the second swipe. The delete may simply not have been committed yet when Maps read the records (it waits on the Undo snackbar), so I don't count it as a finding.

4. **Stop from the notification: the line stays, through every check in 1 to 3.** After the notification's "Stop recording", the record button went back to idle within 1 s, so the ViewModel's resync ran. The dotted trail stayed after 30 s, a tab change, backgrounding, and the delete.
   - The first attempt (run D1) was spoiled. Both test providers were removed at 09:43:29 UTC, 26 s into the recording, by something other than me. `dumpsys location` logs "gps provider removed mock provider override" with no command of mine at that time. The only other adb processes on the laptop were three read-only `logcat` watchers started the evening before. The camera then followed real network fixes to the phone's real area, and D1 stored only 5 points. **Who removed the providers: could not determine.** A system "Mock location is on" notice was showing during the re-run. I re-added the providers and repeated the run once (D2, 9 points); the results above are from D2.

5. **The code.**
   - **Where the line comes from:** `TrackRecordingUiState.breadcrumbPoints` (`ui/track/TrackRecordingUiState.kt:36`). It reaches the map through `MainActivity.kt:654`, then `CompactMapMapSlot.kt:62` and `:77`, then `MapOverlayContent.breadcrumbPoints`, then `OverlayFeatures.kt:61` (`setGeoJson(breadcrumbFeatureCollection(...))`). Nothing on that path checks `isRecording` or `activeTrack`.
   - **The stated intent:** `TrackRecordingUiState.kt:34` says "Empty whenever [activeTrack] is null." `MapOverlayContent.kt:19-21` says "empty whenever nothing is being recorded: a breadcrumb trail is where the device actually walked."
   - **The condition that should clear it:** `clearRecordingState()`, `TrackRecordingViewModel.kt:546`. Every stop path runs it: the in-app Stop at `:642`, and the resync at `:708` (`if (rowReads.rowSaysEnded(active)) clearRecordingState()`, which covers the notification Stop). Its state update at **`:562`** sets `activeTrack = null` and nulls `returnToStart`, `originWaypoint`, `routeHome`, `routeLine`, `sundownLine` and `backBy`. It **does not set `breadcrumbPoints = emptyList()`**. Before the stop, `poller.cancel()` (`:548`) stops the only writer (`TrackRecordingPoller.kt:76`), so the last polled list is frozen in place.
   - **The only places that reset it:** `startRecording` at `:490` (`breadcrumbPoints = emptyList()`), and the take-up at `:614`. That matches what the phone showed: a new Record clears the old line.
   - **Not new in this promotion.** The same `copy(...)` without `breadcrumbPoints` is in `clearRecordingState` at fefc89129 (`:581`) and at main 381515c61 (`:571`). #388 moved the code but didn't cause this.
   - **The test that should have caught it can't fail.** The test "stopping clears active state and the poll loop stops scheduling further work" (`TrackRecordingViewModelTest.kt:306-320`) asserts `breadcrumbPoints.isEmpty()` after `stopRecording()` (`:316`). But it calls `stopRecording()` before any point has been appended or polled, so the list was never anything but empty. The assertion passes with or without a clear in `clearRecordingState`. This is CLAUDE.md's "a check that passes because it never saw the data that could fail it".
   - **Is it a bug by the code's own intent:** yes. Both doc comments above say the trail is empty when nothing is recording, and the clear function resets every other piece of recording state except this one. I fixed nothing.

## Phone left as found
- Build 1.0.3901+g073fc77b is still installed.
- No test tracks: tracks 0, track_points 0.
- The six test Start/End waypoints were deleted through the app. Only "Waypoint 1" remains, as before.
- Area 1 untouched: offline_regions 1, and no test area was made.
- Test providers removed ("removed mock provider override" logged for both). The shell's mock-location appop is back to "default".
- Location is on. Real gps and network fixes arrived within 40 s, with no switch toggling needed this time. The temporary database copy on the laptop has been deleted.
- **One thing left as it is:** the stale dotted trail from run D2 is still drawn on the map. It is in-memory state, and clearing it would need a Record or a process restart, which would be acting on the finding. It goes at the next Record.

## Disclosure
- **Confirmed vs inferred.**
  - Confirmed on the device: every survive/clear observation above; which layer draws the line (via its toggle); database counts.
  - Read from code: the data path, the missing clear at `:562`, the test's empty sample, and the same omission at fefc89129 and 381515c61.
  - Inferred, not run: that the line goes with the process. I didn't restart the app.
- **Could not determine.**
  - What removed the test providers at 09:43:29 UTC.
  - Whether the solid-line delay after the step-4 delete was more than the undo window.
- **Premises that were wrong.** None in the dispatch. My earlier report's "whether it clears was not seen" is now answered: it doesn't.
- **Decided beyond scope.**
  - I repeated step 4 once after the outside removal of the providers.
  - I set the network test provider to the test spot once, so the map camera stayed on the test area.
  - I turned the Tracks layer off and on (left on) to see under the saved-track line.
