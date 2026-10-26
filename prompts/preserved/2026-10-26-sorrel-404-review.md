# Sorrel's review of PR #404: the way home starts from the walker's newest kept point (dispatch 2026-10-25-10)

Sorrel Tally, on Fable (claude-fable-5-1), a fresh session. Read-only on code: no edits, no Gradle, no phone, no CI
run of my own, no merge. `gh` used for reads only. Scratch under `~/.cache/sorrel-404/` (the three run logs, the
JUnit XML artifacts of four runs). Hazel's S22 results were not read. Every claim below is marked **read** (from a
file or artifact, with its location), **computed** (by me, from what was read) or **inferred**.

**Reviewed:** PR #404, branch `way-home-fresh-start` at `b50afeb73` (two commits, `dd8d25064` fail-first and
`b50afeb73` fix), off pre-main `44af58a3c`, against dispatch `prompts/preserved/2026-10-25-10.md`
(origin/records-after-173), RECORD -1636 (same branch, RECORD.md), my bends report
`prompts/preserved/2026-10-25-07-report.md` (origin/bends-report) and Hazel's evidence comment
(PR #404, issuecomment-6407877836).

## Mechanism prediction (written from the dispatch and the bends report's section 5, before the diff was read)

1. Hazel takes the hand-the-points shape, not the flush, because the bends report named `ReturnWatch.onKeptPoint`
   as the precedent and a flush would change write timing, which the dispatch makes a stop.
2. The figure at Return does not change in value: #398's added term and the lag trade places. The metres the term
   was adding (the walker's distance back to a stale first node) become part of the line's own track part.
3. The drawn line's start moves to the newest kept point at Return and at every re-route, through the one read in
   `WayHomeWatch.work`.
4. Nothing else changes: no write, no stored-track change, no notification change.

Scored at the end.

## 1. Baseline (read)

- `origin/way-home-fresh-start` = `b50afeb73`; `git merge-base` with `origin/pre-main` = `44af58a3c`, the base the
  PR and the dispatch name. Two commits, as the PR lists.
- Pre-main has moved three times since the branch was cut: #402 `efeeda359` (merged 2026-10-26T03:39:20Z), #401
  `10142c1d2` (03:47:21Z) and #405 `7f40fea59` (04:01:54Z, two lines in `docs/process/*-profile.md`, no code).
  `git merge-tree --write-tree origin/pre-main origin/way-home-fresh-start` exits 0 against `10142c1d2` and against
  `7f40fea59` (trees `72cde1b9f` and `1d7ccfe99`): **the merge is clean.**
- Files (`git diff --stat 44af58a3c..b50afeb73`): `WayHomeKeptPoints.kt` (new, 64), `WayHomeWatch.kt` (+22/−5),
  `RecordingWatches.kt` (+6), `TrackRecordingService.kt` (+3), two new test classes (86 and 198 lines). 374 insertions,
  5 deletions. None of #402's files (`ReturnWatch.kt`, `WayHomeOnCourse.kt`, `WayHomeProgress.kt`, `AppContainer.kt`)
  and none of #401's (`ui/map/*`), as the PR says.

## 2. The cause, confirmed on the base (read)

As the bends report found and the dispatch states: `WayHomeWatch.work` reads the track through `readTrack`
(`WayHomeWatch.kt:215`), wired to `trackRepository::getById` (`AppContainer.kt:439-441`); the service keeps accepted
points in `pendingPoints` and writes them at 20 points or 30 s (`TrackRecordingService.kt:248-252`, `:263-268`,
`:549-550`); the flush is called only there and at stop (`:392`); nothing on Return or re-route flushes. Unchanged
premise, still true at `b50afeb73`.

## 3. Rowan's checks, in order

### 3.1 The kept points reach the watch in the real service's order and thread (read)

`TrackRecordingService.kt:240-253`: inside `locationTracker.fixes.collect`, on the service's `scope`
(`CoroutineScope(SupervisorJob() + Dispatchers.Default)`, `:91`), after the sampler accepts the point (`:240`), after
the return watch's kept point (`:244`), the new call `watches.wayHomeOnKeptPoint(trackId, candidate)` (`:247`) runs
**before** `pendingPoints += candidate` (`:249`). So a point is in the watch before it can be in the buffer or the
store: there is no moment at which a kept point is in neither place. The call is wrapped in `guardWatch`
(`:353-361`), as the others are: a throw loses that one point for the way home and the point is still recorded.
The delegate `RecordingWatches.wayHomeOnKeptPoint` → `container.wayHomeWatch.onKeptPoint` (`RecordingWatches.kt:40`)
is `open` with a body, so #402's and the existing test subclass `ThrowingWatches`
(`TrackRecordingServiceWatchFailureTest.kt:111`) still compile (inferred from the signature; no build run here).

The real-service test (`TrackRecordingServiceWayHomeNewestPointTest`) drives this path with the real service, the
real container and repository, Robolectric's location manager and a `RecordingWatches` subclass that routes the two
way-home calls to a `WayHomeWatch` built in the test with the real `trackRepository::getById`. The only stand-in is
the route search, which records the track handed to it. The service's order and thread are the real ones; the
container's own `wayHomeWatch` instance is not the one used (see note N3).

### 3.2 Each point is used once, none from another recording (read, computed)

- `WayHomeKeptPoints.addedTo` (`WayHomeKeptPoints.kt:45-51`) drops held points whose timestamp is `<=` the stored
  track's last timestamp, then appends the rest. The store returns points `ORDER BY timestampEpochMillis ASC`
  (`TrackDao.kt:52`), so the last stored point is the newest written. The sampler accepts a point only
  `>= minIntervalMillis` after the last accepted one (`LocationSampler.kt:40-41`; 5 s in HIGH_ACCURACY,
  `TrackRecordingMode.kt:27`), so kept timestamps strictly increase within a recording and `<=` cannot drop a
  distinct newer point (computed).
- Another recording: `add` clears the held points when the track id changes (`:36-41`); `addedTo` returns the track
  untouched when its id is not the held one (`:46`).
- The real-service test asserts the exact list of 23 latitudes, in order, after all 23 kept points were handed to
  the watch and 20 of them were also written: that is the used-once property, through the real service, on actual
  output (green in run 40265119510; the failing form in 40264412743 read 20).

### 3.3 Pruning, the 200 cap, the end-of-recording forget (read)

- Pruned at every read, permanently (`removeFirst`, `:48`). Cap `MAX = 200` (`:61`), ten batches; `add` drops the
  oldest past it (`:42`). The doc's claim that more than 20 are held only while writes fail is right for walking
  pace: at 20 the count flush fires (`TrackRecordingService.kt:250-252`).
- Forgotten at `WayHomeWatch.end(trackId)` and `end(null)` (`WayHomeWatch.kt:113-117`), which the service calls at
  stop (`TrackRecordingService.kt:387`) and destroy (`:163`). `stop()` (the ViewModel's end of a return,
  `TrackRecordingViewModel.kt:564`, `:785`) does not forget them, and should not: the recording goes on, and a second
  Return on it still wants them.
- A failed write (`TrackRecordingService.kt:414-416`: the buffer is cleared before the write, the failure logged) is
  the one case where held points outlive their batch: they keep giving the line its start until a later successful
  write moves the stored last timestamp past them, then they are pruned and the way home's track has the gap the
  store has. Consistent with the store; not a regression (computed from the two code paths).

### 3.4 The line starts at the newest kept point at Return and at each re-route (read, computed)

Both the first job of a return and a re-route go through `launchJob` → `work` (`WayHomeWatch.kt:181-187`, `:214-225`),
and `work` is the one place the track is read and now extended (`:224`). `FindWayHomeUseCase.kt:113` builds the
track-back part from `points.lastIndex`, so the line's first point is the handed track's last point. The Return case
is the service test; the re-route case is `WayHomeWatchKeptPointTest`'s first test. I checked its geometry: the
stray fix at (−70, −50) is 70 m from the hairpin's nearest node (0, −50) (`WayHomeLines.kt:20-24`, `Ground.at`), so
`placeOnWayHome` returns `OffLine` and `judgeStray(70)` exceeds `WAY_HOME_STRAY_METERS` (50) with the watch armed and
no job running: one re-route, as the test asserts (computed).

### 3.5 #398's added distance as the safety net, and whether the figure changes (computed)

Worked with the bends report's S22 case, lag L = 18.7 m, walker W, newest stored point S, newest kept point K.

- **Before #404:** the line starts at S. `placeOnWayHome` (`WayHomeProgress.kt:142-146`): the figure's node is the
  earliest within 60 m, node 0 = S; not on trail; `metersPastLineStart(line, W)` = L because W is behind S along the
  first stretch. Figure = total(S) + L.
- **After #404:** the line starts at K, so total(K) = total(S) + (S→K along the track) ≈ total(S) + L. Node 0 = K;
  `metersPastLineStart(line, W)` = W's distance back to K, at most about one sampler step (5 m or 5 s,
  `TrackRecordingMode.kt:27`) plus whatever the walker has walked since the last kept point. Figure ≈ total(S) + L
  + (a few metres).
- **So the figure's value is essentially unchanged; what changes is where its metres come from.** They move out of
  #398's added term and into the line's own length, which is why the dotted line can now start at the dot and the
  figure still agree with it. The 60 m reach now anchors the figure to the walker's own newest point rather than to
  a stale one (the bends report, section 4).
- **#398 stays live** for the cases the kept points cannot cover: the walker's hop past K; a walker who walks on
  after Return or a re-route before any new line is found; and a Return in a process whose watch holds nothing
  (the app reopened and adopted a recording, T1 Part 3: the watch is new, the stored track is all there is, as
  before #404). Prediction 2 holds.

### 3.6 Locking and threading (read)

- All new state is `keptPoints`, written and read only under the existing `lock`: `onKeptPoint` and `end` take it
  on the service's collector and on the service's thread (`WayHomeWatch.kt:113`, `:129`); `work` takes it for the
  `addedTo` call on the `scope` thread (`:224`). That block does not suspend (`synchronized` inside a `suspend fun`
  is safe only when nothing inside it suspends; nothing does). `WayHomeKeptPoints` is documented not thread-safe and
  is reached only through those three (`:28`, read).
- Cost under the lock: `addedTo` copies the stored track's point list once per read (`track.points + points`). A
  recording of a few thousand points makes that a list copy of that size under the lock, once per Return or
  re-route (inferred: negligible).
- Lock order: the service calls `returnOnKeptPoint` and `wayHomeOnKeptPoint` one after the other
  (`TrackRecordingService.kt:244`, `:247`), each releasing before the next; no watch lock is taken inside another by
  this PR. #402's order (ReturnWatch's lock, then `WayHomeWatch.progressFor`'s) is unchanged, and nothing
  `WayHomeWatch` runs under its lock takes ReturnWatch's (`isReturning` reads a `StateFlow` value,
  `WayHomeOnCourse.kt:49-53`, read). No new deadlock path (inferred from reading both watches).

### 3.7 The revert's bite (read from the artifacts, not from the comment)

I downloaded the `unit-test-report` artifacts of the three runs Hazel cites and of the base run, and read the XML
with my own script (`~/.cache/sorrel-404/artifacts/<run>/`).

| Run | Head | What the XML says (read) | Log (read) |
|---|---|---|---|
| Fail-first 40264412743 (`workflow_dispatch`, created 03:30:28Z) | `dd8d25064` | `WayHomeWatchKeptPointTest` 3 tests, 1 failure: "the re-route is found on the written points and the two kept since, once each, in order expected:<[1, 2, 3, 4]> but was:<[1, 2]>"; `TrackRecordingServiceWayHomeNewestPointTest` 1 test, 1 failure: "the way home is found on every kept point, in order, once each: the twenty written and the three pending expected:<[45.001 … 45.023]> but was:<[45.001 … 45.02]>". Timestamps 03:35:39Z. | Filter: both classes, two `Test filter pattern` lines. 0 ` e: ` lines. `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran. |
| Revert 40265113193 (`workflow_dispatch`, created 03:39:03Z) | `6d38c656e` on `way-home-fresh-start-revert-1`, parent `b50afeb73` | Same 2 of 4, same two messages. Timestamps 03:44:17.548Z and 03:44:17.863Z, inside the run. | Both classes filtered. 0 ` e: ` lines. `Build the debug APK` BUILD SUCCESSFUL 03:42:31Z; `compileDebugUnitTestKotlin` 03:43:29Z. |
| Green 40265119510 (`pull_request`, created 03:39:08Z) | `b50afeb73` | 746 suites, 5,557 tests, 0 failures, 0 errors, 24 skipped (summed over the 746 files). The two new classes present, 3 + 1 tests, 0 failures. | `Run the unit tests` BUILD SUCCESSFUL in 14m 25s; all five jobs green. |
| Base 40235720600 (`push`, pre-main) | `44af58a3c` | 744 suites, 5,553 tests, 0 failures, 0 errors, 24 skipped. | (artifact only; the log was not retrievable) |

- The revert is one line (`git diff b50afeb73 origin/way-home-fresh-start-revert-1`): `WayHomeWatch.kt:224`,
  `findWayHome(track, origin)` for `findWayHome(synchronized(lock) { keptPoints.addedTo(track) }, origin)`. The
  forward branch still has the line (read at `b50afeb73:WayHomeWatch.kt:224`). The revert was made on its own
  branch, not restored from git, so the stale-restore failure CLAUDE.md records cannot apply.
- **The messages are specific to this edit.** With the holding code intact and only the read removed, the track
  handed to the search is the stored track alone: `[1, 2]` for `[1, 2, 3, 4]`, and 20 latitudes for 23. No other
  line of the diff could produce those two readings. The fail-first run produces the same two because its stub is
  `fun onKeptPoint(trackId, point) = Unit` (`git show dd8d25064`, read): the same state by a different route.
- The build compiled on a fresh CI runner and the XML is timestamped inside each run, so the stale-XML failure
  CLAUDE.md records cannot apply either. Hazel's table matches what I read in every figure.
- **The suite arithmetic holds:** 5,553 + 4 = 5,557; 744 + 2 = 746 (computed from the two artifacts).
- **What I did not do:** run my own revert. The profile asks for one; Rowan's rules for this review (read-only on
  code, `gh` for reads) leave no way to build, and a `workflow_dispatch` is a write on Actions. The substitute is
  above: Hazel's revert checked against its own artifacts and the one-line diff, not against her comment.

### 3.8 Which pre-main the green run built against (inferred)

`ci.yml` sets no `ref:` on checkout (read), so the `pull_request` run checked out GitHub's merge ref of `b50afeb73`
with pre-main **as of 03:39:08Z**, which was `44af58a3c`: #402 merged twelve seconds later. **No CI run has yet
built #404 with #402, #401 or #405 in.** The merge is clean (3.1) and the file sets are disjoint, so I expect the
pre-main push run after merge to be green; it is the run that proves it, and should be read before anything relies
on the merged state.

### 3.9 Interaction with #402 and #401 (read)

- **#401** (`ui/map/*`, `MapStyleReads.kt` and its callers): no shared file, no shared type. None.
- **#402 reads the dotted line's start.** `shownTrailOf` hands ReturnWatch `wayHome.trackBack.points` as
  `ShownTrail.trackBack` (`WayHomeOnCourse.kt:38`); `isHeadingBackAlong` judges from the line's node 0 with
  `metersPastLineStart` and `metersAheadOfLineStart` (`:98-102`). With #404, node 0 at a re-route is the walker's
  newest kept point, so "held at the start" means at the walker, and the 10 m ahead is 10 m of real walking back.
  Before #404 the lag (up to about 42 m) put the walker **past** the start, which `isHeadingBackAlong` reads as
  walking out; `WayHomeOnCourse.kt:93-95` says so and names this dispatch as the fix. **#404 makes #402's rule read
  what it was written to read.** No code conflict; the interaction is the intended one.
- **Immediately after a re-route** the walker is within 10 m of the new start, so the dotted part is not counted
  until they have walked 10 m back; the trail parts and the path at Return still count. That is #402's design
  (RECORD -1640), unchanged by #404.
- **Comments that go stale at merge**, all in #402's files, which the dispatch's file ownership kept Hazel out of:
  `WayHomeOnCourse.kt:17-18` ("the dotted part from the walker's newest stored point") and `:66` ("A re-routed line
  starts at the walker's newest stored point"), the lag paragraph at `:93-95` ("is to close it", now closed), and
  `WayHomeProgress.kt:153-154` ("A line starts at the newest stored track point"). Each becomes "newest kept point".
  See should-fix S1.

### 3.10 Anything beyond the dispatch (read)

Nothing. The cap, the pruning and the forget are the shape "hand the watch the pending points" needs to be bounded
and correct; the dispatch left that choice to the coder and asked for the reasons, which `WayHomeKeptPoints.kt`'s
doc gives (the flush rejected for write timing, an added write and a path back into the service; the chosen shape
for changing only the line's start). No file outside Hazel's list was touched.

### 3.11 File sizes against the modular-code rule (computed, `wc -l` at `b50afeb73` and `44af58a3c`)

| File | Before | After |
|---|---|---|
| `domain/wayhome/WayHomeKeptPoints.kt` (new) | — | 64 |
| `domain/wayhome/WayHomeWatch.kt` | 250 | 262 |
| `service/RecordingWatches.kt` | 35 | 41 |
| `service/TrackRecordingService.kt` | 556 | 559 |
| `WayHomeWatchKeptPointTest.kt` (new) | — | 86 |
| `TrackRecordingServiceWayHomeNewestPointTest.kt` (new) | — | 198 |

All under the skill's 400 for new files and 800 for additions. The skill says "CI warns on a PR that grows a file
past these limits"; I found no such check in `.github/workflows/ci.yml` or `release.yml` (grep for 800, `wc -l`;
read). That is a claim in the skill, not a fault of this PR; recorded so Rowan knows the warning does not exist.

## 4. Verdict

**Blocking:** none.

**Should-fix:**
- **S1.** After merge, four comments in #402's files describe the line as starting at the newest *stored* point
  (`WayHomeOnCourse.kt:17-18`, `:66`, `:93-95`; `WayHomeProgress.kt:153-154`). A one-commit follow-up by whoever
  owns those files, not a change to #404.
- **S2.** No CI run has built #404 merged with #402/#401/#405 (3.8). The ordinary pre-main push run after merge
  is the proof; read it before the next promotion or S22 build relies on the merged state.

**Notes:**
- **N1.** The service test's precondition (20 stored, 3 pending) depends on the 20-point count flush landing before
  the 30 s timer flush. The test took 5.7 to 6.0 s on CI (XML `time`), so there is room; a runner slower than 30 s
  would fail the precondition loudly, not pass falsely.
- **N2.** Two of the four tests ("another recording", "end forgets") pass on the stub. Hazel flagged this herself;
  they guard a wrong fix and do not show this one works. Agreed.
- **N3.** The real entry point: the service path is real and Return is `WayHomeWatch.find`, which the ViewModel's
  Return calls (`TrackRecordingViewModel.kt:748`, read) and `TrackRecordingWayHomeTest` already drives with a
  `WayHomeWatch`. The production delegate `RecordingWatches.wayHomeOnKeptPoint` → `container.wayHomeWatch` is a
  one-line `open fun` that no test runs, exactly as `returnOnKeptPoint`'s delegate is not. Same standing as the
  precedent.
- **N4.** Untested: `end(null)` forgetting the held points (the destroy path), and the 200 cap. Both are three
  lines read directly; neither is a path a walk reaches at walking pace.
- **N5.** The device check the dispatch asks for (Return within 30 s of a flush and just before one, the dotted
  line starting at the dot both times) is the only check that sees the real flush timing. Hazel has the S22 for it;
  not read here, and this review does not stand in for it.
- **N6.** `ReturnWatch.onKeptPoint` takes no track id and holds points only while begun; the way home's takes the
  id and holds them always, because Return comes later. Deliberate asymmetry, documented in the new class.

**Mergeable: yes**, on the code and the CI evidence. The S22 check remains the dispatch's own gate for the timing;
whether merging waits on it is Rowan's and the owner's call, not this review's.

## 5. Prediction scored

1. Hand-the-points, not a flush: **held** (`WayHomeKeptPoints.kt` doc, `TrackRecordingService.kt:245-247`).
2. The figure's value unchanged, its metres moving from #398's term into the line: **held** (3.5, computed).
3. The start moves at Return and at every re-route through the one read: **held** (`WayHomeWatch.kt:224`; both
   tests).
4. Nothing else changes: **held** (the service diff is three lines before the buffer add; no write, no stored
   track, no notification).

## 6. Confirmed, inferred, could not determine, premises, beyond scope

**Confirmed (read or computed):** the baseline and clean merge against three pre-main heads; the service's call
order and thread; the used-once and other-recording rules; the cap, prune and forget; the one read serving Return
and re-route; the figure's arithmetic; the lock discipline; the revert's one-line diff, compile lines, fresh XML
timestamps, specific messages and suite arithmetic, all from the artifacts; #402's reading of the line's start and
the four stale comments; the file sizes.

**Inferred:** that the PR run built against pre-main `44af58a3c` (event timing plus the absence of `ref:`); that
`ThrowingWatches` compiles with the new `open fun` (signature only; no build here); the negligible cost of the list
copy under the lock; the absence of a new deadlock path.

**Could not determine:** the real flush timing on a phone (N5); whether the base run's log exists (it returned
empty; its artifact answered instead).

**Premises that were wrong:** none in the dispatch. Rowan's note named pre-main at `10142c1d2`; it is `7f40fea59`
(#405, docs only), checked and clean.

**Decided beyond scope:** none. No code, no Gradle, no CI run, no phone.
