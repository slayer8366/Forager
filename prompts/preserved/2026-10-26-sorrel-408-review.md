# Sorrel Tally: review of PR #408, the recording line reaches the walker's dot

Reviewer: Sorrel Tally (Fable 5.1), a fresh session, read-only on code. Written 2026-10-26T06:20Z (clock),
for Rowan Waymark. Dispatch prompts/preserved/2026-10-26-05.md; RECORD -1651, -1654 (and -1659, which sent
this PR here) on origin/records-after-173; Hazel's diagnosis prompts/preserved/2026-10-25-10-s22-diagnosis-report.md
(branch way-home-fresh-start-s22-report, e06635aaf, section "The recording line trails the walker by up to 30 s",
option (b)). A second Sorrel is reviewing #410 in parallel; its report was not read.

Each claim below is marked **read** (from the named file, line or artifact), **computed** (by me, from data named
here) or **inferred** (reasoned, not observed).

## 1. Baseline

All read with `git rev-parse` / `git merge-base` / `gh api` on 2026-10-26 ~06:05Z, and all match the brief:

| Thing | Brief | Found |
|---|---|---|
| PR head (`recording-line-reaches-dot`) | f0eff0369 | f0eff036923675ca67919bf71f3fb8ecad85c5b6 |
| PR commits | 22cdc3ac3 (tests over stubs + wiring), f0eff0369 (fix) | the same two, and only those, above the base |
| Base | 19c85f78e (#404 merged) | merge-base 19c85f78e |
| pre-main now | c3e132a54 (#406, #407 merged) | c3e132a548351238f23ab03229e149c2a17e47b8 |
| Stub run | 40274048454 | head 22cdc3ac3, branch scratch/recording-line-stubs, test job failed, 63 tests / 9 failed |
| Reverts r1, r2, r3 | 40273314947, 40273317140, 40273319570 | heads 489ebd88e, f4080cc57, 3601b3bde, each parented on f0eff0369, each 63 tests / 1 failed |
| Full run | 40274130158 red then green | attempt 1 test job failed (5,582 tests, 3 failed, 24 skipped), attempt 2 all five jobs green, head f0eff0369 |

`gh pr view 408`: base pre-main, `mergeable: MERGEABLE`, `mergeStateStatus: CLEAN`. The four scratch commits are
still fetchable from origin, so the revert diffs below are read from the commits themselves, not from the logs.

**Not done:** no build or test was run on this laptop (coders and reviewers build on CI here; memory
"Coders are helpers building on GitHub"), so the reviewer-profile step "run its own revert check" was replaced by
reading the coder's three revert commits and their JUnit XML (section 4). No device.

## 2. Mechanism prediction (written after the dispatch and PR description, before the diff and the code)

Predicted: a separate holder the service feeds, a `StateFlow` of an immutable list read by the screen with no lock;
the line merged in the UI state as "stored track, then held points with a timestamp after the stored track's last".
Expected weak spots, in order of likelihood: (1) the merge when the last *written* point is a network fix the store's
read drops, which could draw a written point twice or out of order; (2) the Stop ordering between the holder's end
and the service's final flush; (3) a holder not cleared when a new recording starts, drawing an old recording's tail
on a new line.

Scored in section 6.

## 3. What the PR changes (read: `git diff 19c85f78e f0eff0369`, 13 files, +438 / -2)

- `domain/RecordingKeptTail.kt` (new, 76 lines): `KeptTail(trackId, points)`; `RecordingKeptTail` with a private
  `MutableStateFlow`, `add(trackId, point)` (`_tail.update`: same id appends and `takeLast(MAX)`, another id replaces),
  `end(trackId?)` (`null` or a matching id clears), `MAX = 200`; and `recordingLineOf(stored, tail, trackId)`: the
  stored list unless `tail.trackId == trackId`, else `stored + excludeNetworkProviderFixes(tail.points.filter
  { it.timestampEpochMillis > lastStored })`.
- `AppContainer.kt:440` holds one `RecordingKeptTail`; `TrackRecordingViewModelFactory.kt:40` passes its `tail`.
- `RecordingWatches.kt:46` `recordingLineOnKeptPoint`; `TrackRecordingService.kt:251` calls it inside `guardWatch`,
  directly after the way home's kept-point call and before the `bufferMutex` block; `:164` (`onDestroy`) `end(null)`;
  `:392` (`stopRecording`) `end(trackId)`.
- `TrackRecordingPoller.kt:85-86` collects the flow into `uiState.recordingKeptTail` in `begin`; `cancel` and
  `cancelOnCleared` cancel it. `TrackRecordingUiState.kt:46, 204`: the field and `recordingLine` (a `get()`).
- `TrackRecordingViewModel.kt`: +4 lines (import, constructor parameter, doc line, poller argument); 864 → 868 lines.
- `MainActivity.kt:656`: `breadcrumbPoints = trackUiState.recordingLine.map { LatLng(...) }` (was `breadcrumbPoints`).
- `WayHomeProgress.kt:142-144`: comment only (Sorrel's #404 S1), as the dispatch asked.
- Tests: `RecordingKeptTailTest` (8 cases, plain JUnit), `TrackRecordingServiceRecordingLineTest` (1 case, Robolectric
  sdk 36, real service, container and Room), one new `TrackRecordingViewModelTest` case through `runRecordingTest`.
- Not touched (read, file list): `WayHomeKeptPoints.kt`, `WayHomeWatch.kt`, `WayHomeOnCourse.kt`,
  `ReturnWatchDottedLineTest.kt` (#406's files), anything in `data/`.

## 4. The dispatch's questions, in order

### 4.1 Nothing written differently or earlier

**Read.** The service diff is one guarded call added at `TrackRecordingService.kt:249-251`, before the existing
`bufferMutex.withLock { pendingPoints += candidate; pendingPoints.size >= FLUSH_BATCH_SIZE }` (:252-255), which is
unchanged; `flushPendingPoints` (:412-421), the 30 s flush loop (:267-270), `FLUSH_BATCH_SIZE = 20` and
`FLUSH_INTERVAL_MILLIS = 30_000L` (:554-555) are unchanged. `guardWatch` (:353-361) catches any non-cancellation
exception from `add` and logs it, so a failing holder cannot stop the point being buffered or written. The
sampler's accept decision (`LocationSampler.kt:35-48`) is upstream of the new call and untouched.

**Read (test).** `TrackRecordingServiceRecordingLineTest` drives the real service with 23 shadow fixes and asserts
20 stored after the batch (lines 69-77), 23 in the line, 23 stored after Stop (line 88). It passed in the full run
(attempt 2 green; XML in the revert runs shows the class passing when its own edit is present).

### 4.2 The holder is lock-free; no UI read takes a lock

**Read.** `RecordingKeptTail` has no `Mutex`, no `synchronized`, no `suspend`: `add`/`end` are `MutableStateFlow.update`
(a compare-and-set loop); the screen reads `tail.value` or collects. To be exact rather than reassuring:
`MutableStateFlow`'s setter takes the flow's own monitor for the few instructions of the value swap (kotlinx
internals, **inferred** from library knowledge, not read here); nothing is held across a suspension, and no
application-level lock exists on this path. That is the property the dispatch asked for: `WayHomeWatch`'s lock, the
one the main thread must not wait on, is not taken anywhere the screen reads (the poller's collector at
`TrackRecordingPoller.kt:86` touches only the flow and `updateState`).

### 4.3 The merge: never twice, never out of order, network fixes by the store's rule

**Read.** Stored points come back `ORDER BY timestampEpochMillis ASC` (`TrackDao.kt:52`) and already filtered by
`excludeNetworkProviderFixes` (`RoomTrackRepository.kt:98`). Kept points are strictly increasing in timestamp: the
sampler rejects a candidate unless `candidate.timestampEpochMillis - lastAccepted.timestampEpochMillis >= minIntervalMillis`
(`LocationSampler.kt:40-41`; the smallest mode interval is 5 000 ms, `TrackRecordingMode.kt:27`). So the tail is
sorted, the stored list is sorted, and `> lastStored` (the filtered stored list's last timestamp) partitions them: a
point both written and held has a timestamp `<= lastStored` and is drawn once; nothing is reordered. If the last
*written* point is a network fix, `lastStored` is the last stored GPS point's time, and every held GPS point after it
is unwritten by construction, so no duplicate arises there either (this was my prediction (1); closed by reading).

**Read.** The same function, `excludeNetworkProviderFixes` (`NetworkProviderFix.kt`, the `% 1000 != 0` timestamp rule),
on all three sides: the store's read (`RoomTrackRepository.kt:98`), `recordingLineOf` (`RecordingKeptTail.kt:74`), and
#407's `WayHomeKeptPoints.addedTo` (`WayHomeKeptPoints.kt:60` on pre-main c3e132a54), whose shape is the same
(`lastStored` by timestamp, prune `<=`, filter, append).

**Inferred, edge:** after a service restart (`START_STICKY`, `TrackRecordingService.kt:155`) `lastAccepted` starts at
`null`, so one kept point with a timestamp not after the stored last could be accepted; `>` then simply leaves it out
of the line until it is written, and the store's ordering draws it in place. No duplicate is possible from this path.

### 4.4 Behaviour across Stop, a new recording, a reopened screen, process death

- **Stop from the screen (read).** `clearRecordingState` (`TrackRecordingViewModel.kt:558-578`) cancels the poller
  (and so the collector), sets `activeTrack = null` and `breadcrumbPoints = emptyList()`; `recordingLine` then returns
  `breadcrumbPoints`, empty. Asserted by the new ViewModel case ("no recording line after Stop"). The service's
  `stopRecording` clears the holder (`:392`).
- **Stop from the notification (read; cosmetic).** The service runs `recordingKeptTail.end(trackId)` (`:392`) before
  launching the final flush and `endTrackUseCase` (`:397-404`); the screen learns of the end only through `trackEnds`,
  after both. So for the length of two Room writes the collector sees `NONE` and the line falls back to the stored
  track (shortening by up to one unwritten batch) before `onTrackEnded` clears it. Prediction (2), present but
  harmless: tens of milliseconds, then the line is gone anyway. Noted, not a fix request.
- **A new recording (read).** `add` with a different `trackId` replaces the held list (`RecordingKeptTail.kt:50`);
  `recordingLineOf` ignores a tail for another id (`:72`); `begin` re-collects and a `StateFlow` replays its current
  value. Tested by `a point on another recording replaces the held points` and `points held for another recording...
  are not drawn`. Prediction (3) closed by reading.
- **A reopened screen on a running recording (read, untested).** `takeUpRunningRecording` (`:604-640`) ends in
  `poller.begin(trackId)`, the same entry the fresh start uses, so the collector's first value is the tail as it stands
  and the line reaches the dot on the first frame. No test covers take-up with a tail;
  `TrackRecordingSwipeAwayTest:417` asserts the reopened ViewModel's `breadcrumbPoints` only.
- **Process death (inferred).** `AppContainer` is per process, so the holder starts empty; the line is the stored track
  until the next kept point. The unwritten points were lost with the process before this PR too. `onDestroy` →
  `end(null)` (`:164`) covers a service destroyed without a Stop.

### 4.5 The 200 cap against a long walk without a write (failed writes, -1654 note 3)

**Read.** `flushPendingPoints` copies and clears `pendingPoints` *before* the write and, on failure, only logs
(`TrackRecordingService.kt:412-420`). So a failed batch is gone from the store for good. The holder keeps those points
anyway (it prunes nothing; `recordingLineOf` filters by the stored last timestamp), so the line keeps drawing them.

**Computed.** With `MAX = 200` and the modes' minimum intervals (`TrackRecordingMode.kt:27-29`), the tail covers at
least 200 × 5 s ≈ 16.7 min at High accuracy, 50 min Balanced, 200 min Battery saver. After that many kept points
without a successful write, the oldest held points age out and the line becomes a chord from the last stored point to
the oldest held one. Under normal writes (every 20 points or 30 s) the unwritten part is at most 20 points, a 10×
margin, and the cap never shows.

**Finding (note, pre-existing).** During that window the line presents points the store does not hold, so after Stop
the saved track is shorter than the line that was drawn. The device step "after Stop, the saved track matches the drawn
line" is exactly the detector for this. The way home has had the same property since #404 (`WayHomeKeptPoints`, same
cap, same loss), and the silent drop on a failed write predates both; not this PR's to fix, but worth a record line.

### 4.6 What else reads `breadcrumbPoints` vs `recordingLine`

**Read** (`git grep` on the PR head). `recordingLine` has one production reader, `MainActivity.kt:656`, which builds
`AvailabilityScreenRecording.breadcrumbPoints` (the only construction site with a recording, `MainActivity.kt:623`);
everything in `ui/availability` and `ui/map` named `breadcrumbPoints` is that `LatLng` list flowing down to
`OverlayFeatures.kt:61`. So every map draw of the recording line now reaches the dot, on the compact and the full map
alike. `TrackRecordingUiState.breadcrumbPoints` keeps two readers: `TrackRecordingAutoWaypoints.kt:47` (the start
point, stored only, unchanged and right) and the poller's own write (`TrackRecordingPoller.kt:91`). The route tick
routes along `lastPolledTrack` (stored), unchanged; the way home's own line comes from `WayHomeKeptPoints`. Tests
asserting `breadcrumbPoints` (`NetworkFixExclusionPerConsumerTest:317`, `TrackRecordingSwipeAwayTest:408, 417`, the
older ViewModel cases) still describe the stored track, which is what they claim.

### 4.7 The overlay refresh per kept point (`MapDataRefresh.kt:61-73`): any risk beyond cost

**Read.** The `LaunchedEffect` keys on `breadcrumbPoints` (a `List<LatLng>`, compared structurally, so a recomposition
with the same points does not re-run it). Each kept point now re-runs `overlayDataOnCurrentStyle` (every source's
GeoJSON re-set) and the camera block. The camera block is gated: the search frame by `searchFrameRequestId` once per id
(`:84-104`), a camera request by its id (`:109-113`), the region move by `shouldMoveCameraToTarget` on a *changed*
target (`:115`), and the following case only records the target as applied (`:129`). None of those gates depends on
how often the effect runs, so more runs move the camera no more than before. **Inferred:** the risk is cost alone
(the recomposition and GeoJSON re-push per kept point, 5/15/60 s by mode, on top of the 15 s poll), which the
S22 check for stutter and battery (-1654) is already set to measure. The comment at `MapDataRefresh.kt:78` ("roughly
every 15s while a track is recording") is now an understatement; see should-fix S2.

### 4.8 The poll-loop rule for `TrackRecordingViewModelTest`

**Read.** The new case is `fun ... = runRecordingTest { ... }` (diff line 654); `runRecordingTest`
(`TrackRecordingViewModelTest.kt:124-130`) wraps `runTest(dispatcher)` and stops every created ViewModel in a
`finally`. The case also calls `vm.stopRecording()` itself. The new collector job lives in `viewModelScope` and is
cancelled by `poller.cancel()` on stop; a suspended `StateFlow.collect` schedules nothing, so even unstopped it could
not spin the test dispatcher. The rule is met.

### 4.9 The uncovered MainActivity line (r4)

**Read.** No test reaches `MainActivity.kt:656`: no test file touches `BREADCRUMB_SOURCE_ID`, and the two test files
naming both MainActivity and breadcrumbs (`TrackRecordingSwipeAwayTest`, `TrackRecordingViewModelTest`) assert on the
ViewModel's `breadcrumbPoints`. The coder's claim is right. The gap is older than this PR: the breadcrumb source's
GeoJSON has never had a test through MainActivity. The one line that makes the feature visible is therefore covered
by the S22 check only. See should-fix S1.

### 4.10 The OOM: did this PR push the test heap closer to its limit?

**Read.** Attempt 1 of 40274130158: `MapCreditCaptionNoOutlineDayTest`, `...NightTest`, `...TouchTest` each
`java.lang.OutOfMemoryError` (job 121413124818 log lines 3042-3049); 5,582 tests, 3 failed. The heap report
(prompts/preserved/2026-10-26-04-report.md, 27f9c1076) reproduced the same failure on a base without this PR
(run 40267714277 on 0e5226c3f; -1659 calls this PR's the fourth sighting), measured the live heap at the first caption
class at 490 of 512 MiB, dominated by Robolectric's per-SDK resource tables (127.1 MiB) and 38 retained `Application`
graphs at about 1.73 MiB each (report lines 67, 79), and placed the caption classes at #663 of 746, one later than
an earlier count of #662 of 744 (line 183), which says new classes in packages sorting before `ui.map` run before them.

**Inferred, from that.** This PR adds two test classes and one case. `RecordingKeptTailTest` is plain JUnit (no
sandbox, nothing retained); the extra `TrackRecordingViewModelTest` case is plain JUnit too. The one Robolectric
addition, `TrackRecordingServiceRecordingLineTest` (sdk 36, real container), sorts under `service`, before `ui.map`,
so it runs before the caption classes and can retain at most one more `Application` graph, about 1.7 MiB against a
margin of about 22 MiB. It adds no new SDK table (sdk 36 is the suite's main one). So: not the cause (the failure
reproduces without it), a plausible fraction of a per cent of heap in the wrong direction, and nothing to change here;
#410 is the fix (-1659: 220 MiB at the caption class after the split).

### 4.11 Clean merge with current pre-main; interaction with #407

**Read.** `git merge-tree --write-tree origin/pre-main origin/recording-line-reaches-dot` exits 0 (no conflicts). The
five files #406 and #407 changed since the base (`WayHomeOnCourse.kt`, `WayHomeKeptPoints.kt`,
`ReturnWatchDottedLineTest.kt`, `WayHomeWatchKeptPointTest.kt`, `TrackRecordingServiceWayHomeNetworkFixTest.kt`) do
not overlap the PR's thirteen. GitHub reports the same (`MERGEABLE`, `CLEAN`).

**Read.** #407 and this PR are two holders fed from adjacent guarded calls with the same `candidate`
(`TrackRecordingService.kt:248, :251`) and the same filter function; neither reads the other. A throw in the way home's
call is caught by its own `guardWatch` and the recording line's call still runs. No semantic interaction to flag,
and the PR's own network-fix test (`a network fix held and not written is left out`) is the same rule #407 added
for the way home, so the two lines now agree on what a kept network fix does: nothing.

## 5. The coder's evidence, checked against the artifacts

**Read** (`unit-test-report` artifacts of the four runs, JUnit XML, parsed by me):

| Run | Reverted edit (read from the commit) | Classes run | Failed | Message |
|---|---|---|---|---|
| stubs 40274048454 | holder no-op, `recordingLineOf = stored` | VM 54, service 1, domain 8 | 9 | VM: `expected:<[45.0, 45.001, 45.002]> but was:<[45.0, 45.001]>`; service: `the held points are this recording's expected:<…> but was:<null>`; 7 of 8 domain cases |
| r1 489ebd88e | the one `guardWatch("The recording line's kept point"…)` line | 63 | 1 | service: `the held points are this recording's expected:<a5fec74c…> but was:<null>` |
| r2 f4080cc57 | `keptTailJob = null` for the collector | 63 | 1 | VM: `the line reaches the newest kept point, not yet written, before the next poll expected:<[45.0, 45.001, 45.002]> but was:<[45.0, 45.001]>` |
| r3 3601b3bde | `excludeNetworkProviderFixes(...)` removed | 63 | 1 | domain: `expected:<[45.001, 45.003]> but was:<[45.001, 49.0, 45.003]>` |

Each revert compiled (63 tests completed in each run), each failed exactly one test, and each message is the one
that edit alone can produce (r3's `49.0` is the sub-second-timestamp point drawn; r1's `null` is a holder never fed;
r2's two-point line is a tail never collected). The one domain case that passed over the stub, `points held for
another recording, or with no recording, are not drawn`, is the negative case a no-op holder satisfies by returning
`stored`; the coder flagged it, and it is still worth having (it guards the id check in `recordingLineOf`).

The gradle console lines gave `TrackRecordingViewModelTest.kt:124` for r2, which is the `runRecordingTest` wrapper,
not the assertion; the XML message above is what confirms r2, so cite the XML, not the console, for that one.

## 6. Prediction scored

(1) duplicate/out-of-order when the last written point is a network fix: **not present**, closed by reading the DAO
order, the sampler's strict interval and the shared filter (4.3). (2) Stop ordering between `end` and the flush:
**present**, cosmetic, on the notification Stop only (4.4). (3) a stale tail on a new recording: **not present**,
handled twice over (4.4). One of three, and the one found is a note, not a fix.

## 7. Verdict

**Blocking:** none.

**Should-fix** (neither blocks the merge; both can ride a later change, as 406-1/406-2 do):

- **S1.** The line that makes the feature visible, `MainActivity.kt:656`, has no test and never has had one at the
  breadcrumb source (4.9). The S22 check (-1659: "its S22 check after") is the only evidence for it, so that check
  should be run before the line is relied on, and the "line reaches the dot within a few seconds of each kept point"
  step is the one that exercises this line. A Robolectric test feeding `recordingKeptTail` and reading the breadcrumb
  source's GeoJSON would close the gap for good, but it is a new kind of test for this map, not a one-liner.
- **S2.** Two comments now understate the cadence: `MapDataRefresh.kt:78` ("roughly every 15s while a track is
  recording": now also on each kept point) and, less so, `RouteHome.kt:107` (the "30 to 45 s" lag, still true for the
  stored track the route reads, but no longer for the drawn line). Comment-only.

**Notes:**

- **N1.** A failed flush drops its points from the store silently except for a `Log.w`, and both lines (this one and
  the way home's since #404) keep drawing them for up to 200 kept points (16.7 / 50 / 200 min by mode), then show a
  chord (4.5). Pre-existing; the device step "saved track matches the drawn line" detects it; worth a record line so
  the next person who sees a line longer than its saved track knows where to look.
- **N2.** Notification Stop: the line shortens to the stored track for two Room writes' time before it clears (4.4).
- **N3.** `TrackRecordingViewModel.kt` is 868 lines, over the 800-line split rule; the owner's "Few lines OK"
  (-1654) covers these four.
- **N4.** The reopened-screen case (take-up with a tail) is read from code and untested (4.4); the path is the same
  `poller.begin` the tested start uses.
- **N5.** Heap: not the cause of the attempt-1 OOM, about one `Application` graph (≈1.7 MiB) in the wrong
  direction (4.10); nothing to change here.
- **N6.** `TrackRecordingServiceRecordingLineTest` mirrors `FLUSH_BATCH_SIZE = 20` as a private constant; if the
  service's changes, the test's precondition fails loudly rather than passing vacuously. Fine as is.

**Mergeable:** yes, as built, onto current pre-main (c3e132a54): clean merge, no overlap with #406/#407, the same
network-fix rule as #407, nothing written differently or earlier, three reverts each biting on their own message, and
the device check already queued for the one untested line.

One-line answers: nothing is written differently or earlier (read); the holder takes no application lock and the UI
reads none (read); the merge draws each point once, in order, and drops network fixes by the store's own function
(read); the 200 cap is a 10× margin under working writes and a visible-but-unsaved tail under failed ones (computed,
pre-existing); the refresh per kept point is cost only (inferred, device check pending); the poll-loop rule is met
(read); r4 is uncovered and the device check is its evidence (read); the OOM is not this PR's (read + inferred);
the merge is clean (read).
