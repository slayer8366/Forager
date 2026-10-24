# Sorrel's review of #391 (recording-line-clears)

**Reviewer:** Sorrel, on Fable 5.1 (claude-fable-5-1). The coder, Hazel, is on Opus.
**Written:** 2026-10-24, about 10:55 UTC. Complete, not partial.
**Head reviewed:** `recording-line-clears` 3ad490b59, into pre-main 073fc77bd. Revert branch `recording-line-clears-revert-1` 4ad95d82b.
**Read first:** CLAUDE.md; dispatch 2026-10-24-05 and the finding 2026-10-24-04 (both from origin/records-after-173 at 2ab46aaa1, with `git show`); RECORD -1601 and -1602.
**Method:** nothing was edited in app code, no Gradle was run, nothing was posted to GitHub, no phone was touched. Every claim below names a file and line at the PR's head, or a CI object read through the GitHub API, or is marked inferred. "Read" means I read the source; "inferred" means I reasoned from it without running it.

## Verdict

**Merge-ready** on the owner's word, after the S22 re-check the dispatch already schedules. Blocking 0, M 0, L 3 (none asks for a code change before merge). The fix is the one line the dispatch asked for, it is the only omission of its kind in the clear, both tests reach the clear through the entry points the two Stops really use, both were shown to fail without the fix on a build that compiled, and the revert branch differs from the head by exactly that line.

Base: the PR's merge-base with pre-main is 073fc77bd (current pre-main), two commits on top (8f60ba490 tests, 3ad490b59 fix), and a trial merge onto pre-main has no conflict markers. GitHub reports it MERGEABLE.

---

## 1. Is the omitted clear the only path that leaves the line drawn?

Yes. Every writer and reader of `breadcrumbPoints` at the head, from `git grep`:

**Writers (four, all in `ui/track/`).**
- `TrackRecordingPoller.kt:76` — the poll's `updateState { it.copy(breadcrumbPoints = track?.points.orEmpty()) }`, the only writer that ever makes the list non-empty. The poll runs only from `begin()` (`:70`), called by `startRecording` (`TrackRecordingViewModel.kt:500`), `takeUpRunningRecording` (`:622`) and `startReturn` (`:742`, guarded by `activeTrack ?: return` at `:724`). `clearRecordingState` cancels it at `:548` before the state update at `:562`.
- `TrackRecordingViewModel.kt:490` — `startRecording` resets it to empty.
- `TrackRecordingViewModel.kt:614` — take-up sets it to the running row's points, while `activeTrack` is being set non-null in the same `copy`.
- `TrackRecordingViewModel.kt:562` — the fixed line. Before this PR this `copy` nulled `activeTrack`, `returnToStart`, `originWaypoint`, `routeHome`, `routeLine`, `sundownLine`, `backBy` and the two flags, and left `breadcrumbPoints`.

**Every stop reaches `clearRecordingState`.** The screen's Stop: `MainActivity.kt:627` (`onToggleRecording`) and `:443` (the permission rollback) call `TrackRecordingViewModel.stopRecording()`, which calls `clearRecordingState()` at `:642`. The notification's Stop: `TrackRecordingService.stopRecording()` flushes, ends the row, then `container.trackEnds.report(trackId)` (`TrackRecordingService.kt:385-389`); the ViewModel collects `trackEnds` from init (`TrackRecordingViewModel.kt:366`), `onTrackEnded` (`:411-414`) calls `resyncRecordingState` for its own track, which calls `clearRecordingState` when `rowSaysEnded` (`:706-709`; `TrackRecordingRowReads.kt:61-75`, true when the row is ended or missing). The service's `halt` path reports the same way (`TrackRecordingService.kt:327-333`), and `onRecordingHalted` also goes through `stopRecording()` (`:385-393`). Foreground/background resyncs (`:652-676`) use the same function. So the one `copy` at `:562` is the single place a stop empties state, and it now empties the list.

**Readers (the map path).** `MainActivity.kt:654` maps `trackUiState.breadcrumbPoints` to `LatLng` unconditionally → `CompactMapMapSlot.kt:62,77` → `MapOverlayContent.breadcrumbPoints` (`MapOverlayContent.kt:23`) → `MapSlot.kt:115` → `SightingsMap.kt:175,614` → `MapDataRefresh.kt:36`, where the `LaunchedEffect` at `:57-60` keys on `breadcrumbPoints`, so a change to the empty list re-runs `refreshOverlayData` → `OverlayFeatures.kt:61` `setGeoJson(breadcrumbFeatureCollection(points))`, and `breadcrumbFeatureCollection` returns an empty collection for fewer than two points (`OverlayFeatures.kt:135-136`; `SightingsMapOverlayDataTest.kt:401` asserts that). No reader checks `isRecording`; the line is drawn from the list alone, which is why emptying the list is the whole fix. The one non-map reader, `TrackRecordingAutoWaypoints.kt:47` (`breadcrumbPoints.firstOrNull()` as a fallback start point), only runs while recording.

**A race I reasoned about but did not test (inferred).** If `clearRecordingState` runs while the poll is suspended inside `trackRepository.getById`, the poll job is cancelled (`TrackRecordingPoller.kt:96`). `RoomTrackRepository.getById` (`RoomTrackRepository.kt:43`) is a `runCatchingCancellable` over Room suspend DAO calls, which resume with `CancellationException` once their job is cancelled, so the `onSuccess` write at `TrackRecordingPoller.kt:76` cannot land after the clear. With the in-memory fake the call never suspends, so the tests do not exercise this; I am stating it from the coroutine library's documented `withContext` behaviour, not from a run.

## 2. Do the tests go through real entry points, and can they fail?

**Entry points.** `the recording line is cleared by the screen's Stop` (`TrackRecordingViewModelTest.kt:351-366`) calls `vm.stopRecording()`, the method `MainActivity.kt:627` calls. `the recording line is cleared by Stop from the notification` (`:373-391`) never calls `stopRecording`; it ends the row (`trackRepository.end("track-1", 5_000L)`) and then `trackEnds.report("track-1")`, the order the service uses at `TrackRecordingService.kt:385-389` ("the end is reported only once it is stored"), and the ViewModel hears it through the same `trackEnds.latest` StateFlow the production factory passes (`TrackRecordingViewModelFactory.kt:38`; the test helper at `:170` passes `trackEnds.latest`). It also asserts `isRecording` is false first (`:389`), so the empty list is shown to come from the resync's clear and not from some other write. Both tests first let the poll read two points and assert the list is `[45.0, 45.001]` before Stop (`:360`, `:383`), so each test's sample is the one that could fail it; this is the gap the old test at `:306-320` had (it stops before any poll), and that test now says so in a comment (`:316-317`) rather than being removed, which the dispatch allowed. Both go through `runRecordingTest` (`:121-127`), as the class requires.

**They fail without the fix.** Fail-first run 40083975160 (workflow_dispatch on 8f60ba490, filtered to the class: build log line 3418 shows `testDebugUnitTest --tests com.zynergylabs.forager.app.ui.track.TrackRecordingViewModelTest`): the "Build, test, publish APK" job's log has zero `e:` lines, `assembleDebug` reports `BUILD SUCCESSFUL`, and the JUnit XML in artifact `unit-test-report` (12555994735) says 53 tests, 2 failures, 0 errors, timestamp 10:02:44Z (inside that job's 09:57-10:02 window, so not stale). The two failures are the two new tests, each on its own last assertion with the message `no recording line after Stop[ from the notification] expected:<[]> but was:<[TrackPoint(lat=45.0 …), TrackPoint(lat=45.001 …)]>` — the two points the test appended, which is the failure only this edit can produce. I confirmed `git diff origin/pre-main 8f60ba490 -- app/src/main` is empty, so that run tested pre-main's production code.

**They pass with it.** Run 40086109829 (pull_request on 3ad490b59): every job `success`; its `unit-test-report` (12557387079) totals 5449 tests, 0 failures, 0 errors across 734 result files, and the class's XML has 53 tests, 0 failures, with both new names present, timestamp 10:49:00Z. Hazel's PR comment says pre-main's run 40078250926 had 5447; I confirmed that run is `success` on 073fc77bd but did not open its artifact to recount.

## 3. The revert check

- **Exactly one line.** `git diff origin/recording-line-clears origin/recording-line-clears-revert-1` is one file, one line: `breadcrumbPoints = emptyList()` removed from `TrackRecordingViewModel.kt:562`. `git diff origin/pre-main origin/recording-line-clears-revert-1 -- app/src/main` is empty: the revert branch's production code is pre-main's byte for byte, plus the two tests.
- **No `e:` lines.** The revert run's test job log (job 120849321359, 3605 lines) has zero `e:` lines; `assembleDebug` reports `BUILD SUCCESSFUL in 1m 53s`; the Release job in the same run also compiled (`success`). The reverted build ran.
- **The failures are this revert's.** The JUnit XML (artifact 12557461322, timestamp 10:33:52Z, inside the job's 10:29-10:34 window) says 53 tests, 2 failures, the same two names, the same `expected:<[]> but was:<[TrackPoint(lat=45.0 …` messages. No other test in the class fails.
- **The forward change is still present** on origin/recording-line-clears at 3ad490b59 (`:562` carries the clear). The dispatch's "restore from a saved copy" step is moot for a revert done on a separate branch cut from the head; nothing on the head could have been discarded by it.

## 4. Is anything else in recording state missed the same way?

No. Every field whose doc in `TrackRecordingUiState.kt` says it is null or empty with no recording is reset at `:562`: `returnToStart` (`:72`), `originWaypoint` (`:82`), `routeHome`/`routeLine` (`:92`, `:97`), `sundownLine` (`:146`), `backBy` (`:153`), `isReturning`/`isOffTrack`, and `activeTrack`. `isArrivalHeld` is reset by `endArrivalHold()` at `:547` (`TrackRecordingWatchCopies.kt:100`). Outside the state object, the clear also resets `lastPolledTrack` (`:550`), `lastGatedGpsFix` (`:558`), `originCreationInFlight` and the take-up (`:559-560`).

What is left non-null on purpose and is a different shape: `tripStartWarning`, `backgroundRunPrompt` and `networkFixesNotice` are documented as one-shot notices keyed by id, "cleared on the next start" (`TrackRecordingUiState.kt:106-137`), and `startRecordingErrorMessage` (`:31`) is the Toast field the next successful start clears (`TrackRecordingViewModel.kt:489`). None is documented as empty-when-not-recording, none draws anything on the map, and I ask for no change to them.

## Findings

**Blocking:** none. **M:** none.

**L1 — the "only the new tests fail" claim is a claim about one class.** Both the fail-first and the revert run were filtered to `TrackRecordingViewModelTest` (53 tests), so "2 failed, exactly the two new ones" was measured on that class, not the suite (PR body, "Fail-first"; PR comment, "Revert check"). It is sufficient here because the production code on both runs is byte-identical to pre-main 073fc77bd, whose full run 40078250926 is green, so no other class could have changed outcome. State the scope when quoting it. No change asked.

**L2 — the fail-first run also carries an unrelated hang.** Run 40083975160's "Release compiles and packages (unsigned)" job (120843035990) started 09:57:14Z and was cancelled at 10:52:14Z, 55 minutes later; `ci.yml:546` gives that job `timeout-minutes: 45`. The run's conclusion is now `failure` for two reasons, not one; the test job it is cited for completed normally at 10:02:52Z and is unaffected. Not this PR's doing and not evidence against it. Rowan may want it noted as a CI observation.

**L3 — the notification test plays the service's part short.** The real `TrackRecordingService.stopRecording()` also ends the return, sundown and back-by watches and flushes buffered points (`TrackRecordingService.kt:378-385`) before `endTrackUseCase` and `report`. The test does end-then-report only (`TrackRecordingViewModelTest.kt:385-386`). Nothing in that gap can affect the clear (the resync reads only `endedAtEpochMillis`, `TrackRecordingRowReads.kt:69`), so the test's claim stands; the doc comment's "as the service really reports it" should be read as "in the service's store-then-report order", which is what `:369-371` actually says. No change asked.

**For the S22 re-check (observation, not a finding).** After the screen's Stop the dotted line should vanish at once, and the solid saved-track line appears only when the service has flushed and reported the end and `loadTracks` has run (`TrackRecordingViewModel.kt:412`). A moment with neither line is the expected shape of the dispatch's step path ("gone from the map at once"; "the saved track's own line shows as it does today"), not a regression.

## What I did not check

- I ran nothing: no Gradle, no emulator, no phone. Everything about the map's redraw is read from the Compose keying and the GeoJSON builder, not seen.
- The green run's build log for `e:` lines. A green `testDebugUnitTest` cannot come from a build that failed to compile, so I did not open it.
- Pre-main run 40078250926's artifact, to recount 5447 and 24 skipped; I confirmed only that the run is `success` on 073fc77bd.
- Why the Release job in the fail-first run hung (L2).
- The poll/clear race in section 1 is inferred from library semantics, not tested.
- Whether the Layers sheet's "Recording trail" toggle or any other chrome reads `isRecording` to hide the line independently; the finding report says nothing on the draw path does, and my grep of readers agrees, but I did not read the Layers sheet.
- Hazel's statement that the revert worktree was restored from a saved copy. Unverifiable from the remote and, as above, moot for the head.
