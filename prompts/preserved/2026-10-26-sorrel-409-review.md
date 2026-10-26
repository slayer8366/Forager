# Review of PR #409 (step 6 PR 1: the straight waypoint line moves to the ViewModel), Sorrel, 2026-10-26

Ran on Fable (Fable 5.1), a different model from the coder (Opus). Review only: no app code, no Gradle, no phone; the one write is this file. Written at 2026-10-26T06:14Z against:

- PR #409, `waypoint-line-to-viewmodel` at `9114eda5b`: fail-first `7404a3562` (the test over a stub), `5a9f328b5` (`StraightLine` public), move `9114eda5b`; three linear commits, no merges, off `19c85f78e` (confirmed with `git merge-base`). GitHub reports the same head (`gh pr view 409`).
- dispatch `prompts/preserved/2026-10-26-06.md` with Amendments 1 and 2, and RECORD -1652, -1653, -1655, -1657 (both read from `origin/records-after-173`; -1654, -1656, -1658 for context).
- the step 6 report `docs/audits/2026-10-25-03-waypoint-along-trails-report.md` (branch `waypoint-trails-report`, `079666037`): §1, the modular-code answers, §4 PR 1, §6 and §8.3.
- the code at `9114eda5b` and at the base `19c85f78e`: the whole diff (9 files, +239 −24), `AvailabilityScreen.kt` at both ends, `NavigationArrival.kt`, `AvailabilityWaypointLine.kt`, `AvailabilityViewModelWaypointNavigation.kt`, `AvailabilityLiveLocation.kt:63-80`, `AvailabilityScreenNotices.kt:96-106`, `domain/WaypointNavigation.kt:67-95`, `WaypointRepository.kt`, `WaypointDao.kt`, `RoomWaypointRepository.kt:32`, `CreateWaypointUseCase.kt:30-43`, `ImportGpxUseCase.kt:42, :79, :93, :104-122`, `domain/JournalBackup.kt:15-30`, `data/backup/RoomJournalBackup.kt:252-264, :448-458`, `TrackRecordingPendingDeletes.kt:33-84`, `MainActivity.kt:478-490, :676-690`, `ScreenMapBundles.kt:43-61`, `WaypointNavigateControls.kt:1-30`, `AppContainer.kt:235, :423`; the test diff in full and `AvailabilityScreenWaypointNavigateTest.kt` at the head; the two revert branches `revert-wpline-r1` (`dc30a707c`) and `revert-wpline-r2` (`6cfb590d8`); `.claude/skills/modular-code/SKILL.md` on pre-main.

**Pre-main moved during this review, twice.** The brief said `12a073783` (#406 in). It was `12a073783` when I started and is **`c3e132a54` now: #407 merged at 2026-10-26T06:09:04Z** (`way-home-gps-start`, three commits, files `WayHomeKeptPoints.kt`, `WayHomeWatchKeptPointTest.kt`, new `TrackRecordingServiceWayHomeNetworkFixTest.kt`). Section 9 is written for the tree as it is now.

Every CI claim is from the run's metadata and "Build, test, publish APK" log fetched through the API and the `unit-test-report` JUnit XML downloaded per run (scratch under this session's scratchpad, not kept). Each of the three red logs has 0 lines matching ` e: `; the one run that did not compile (section 6.1) is the one the coder disclosed. Every result below describes the build it claims to.

## Mechanism prediction (written after reading the diff and the two revert edits, before reading any JUnit XML; scored in section 10)

1. The fail-first run fails only the two new ViewModel cases, with `expected:<StraightLine(...)> but was:<null>`: the stub only clears the line.
2. Revert r1 (the screen ignores the ViewModel's line) fails exactly the screen cases that assert a drawn line and no ViewModel case; the cases that assert the line's absence (arrival, X, Back) stay green.
3. Revert r2 (the ViewModel never starts the line) fails those same screen cases plus both ViewModel cases.
4. The green run's total is pre-main's total plus 2.

## 1. Verdict

**Mergeable.** The move does what the dispatch and the two amendments say: the line's state and clock are in the ViewModel (`AvailabilityWaypointLine.kt`, 64 lines), the waypoint is read by id at start and at pick-up (-1657), the screen keeps its one "gone" rule and is the only reader of the state line (section 2.3), and no waypoint's position can change after it is placed (section 3). The seven runs are sound and each revert's failures are the ones its edit can produce (section 6). Behaviour is unchanged to the eye, with the accepted dark-mode/language survival and one timing change the owner's option 2 implies (section 2.1); no frame can show a wrong line (section 2.2, inferred).

**Blocking:** none.

**Should-fix** (none holds the merge):

- **409-1.** No run has built #409's code with #407's. The PR's one `pull_request` run (40274772010, 05:32Z) checked out the merge ref against `12a073783` (#406 in; its 5,574 = 5,569 + 2 new + #406's 3, section 6.4), before #407 landed (06:09Z). The files are disjoint (`git merge-tree` against `c3e132a54`: clean, no overlap in `git diff --name-only`), and #407 is the way home's kept points, which the waypoint line never reads; the behaviour does not couple as #406's did with #404. Pre-main's push run after the merge will be the first joint build; same shape as my 406-3, closed there by pre-main's run.
- **409-2.** Two doc comments now say the opposite of the code: `WaypointNavigateControls.kt:11-12` ("worked out and held in `AvailabilityScreen` so it outlives the Maps tab leaving composition") and, per the step 6 report §8.3 item 1, `CompactMapTabInputs.kt:56-58` (not re-read by me; the report's pointer). Neither file was in the dispatch's list, which is why the coder left them; one line each, with the next change that touches them.

**Notes:** sections 2.1 (the one-read delay), 2.4 (what the screen tests now prove and by what route), 3 (the restore path), 7 (the beyond-scope choices, including the file-ownership deviation for `NavigationArrival.kt`), 8 (sizes).

## 2. Is behaviour unchanged, as the owner was promised?

### 2.1 The step path, and the one timing change

Tap a waypoint → Navigate → the dashed line, faded when the fix is stale, grey when lost, none until the first fix, kept across a tab change → Back → as today. Read end to end:

- **The drawing rules are the same functions.** `nextStraightLine` and `millisUntilFreshnessChanges` (`NavigationArrival.kt:83-107`) are unchanged in body; `AvailabilityWaypointLine.follow` (`:46-62`) calls them with the same four inputs the screen's `remember`/`produceState`/`LaunchedEffect` did (base `AvailabilityScreen.kt:422-432`): the previous line, the fix, the target, the clock. The loop shape is the same: write the line, sleep until the fix's freshness next changes, re-read the clock, write again; break once lost or with no fix (read).
- **The fix is the same fix.** The screen's `uiState.liveFix` and the collaborator's `_uiState.map { it.liveFix }` (`AvailabilityViewModel.kt:381`) are one field, written only by `AvailabilityLiveLocation.begin` (`:63-78`) after its accuracy gate; nothing nulls it (`git grep 'liveFix = null'`: no hit), so "lost" happens only by ageing, as before (read).
- **Kept across a tab change:** the line now lives in `AvailabilityUiState` (`:354`), which outlives every tab; before it lived in a `remember` above the tab switch (base `:422`). Same survival, one level up (read). It now also survives the Activity being rebuilt by a dark-mode or language change, the accepted case (Amendment 1).
- **Restart:** a stored navigation makes `follow(id)` run at ViewModel construction (`:377-381`), where the fix flow's first value is `null`, so `nextStraightLine(null, null, …)` gives no line until the first fix (read), as the owner's option said.
- **Back and the X** reach `onStopWaypointNavigation` (`AvailabilityViewModelWaypointNavigation.kt:44-50`), whose id change cancels `follow` and runs `follow(null)`, which clears the line (read).

**The one timing change (inferred, not visible to a test).** At the base the screen computed `nextWaypointLine` inline in the same composition that resolved the target (`:431`) and handed that value to the controls (`:854`), so the HUD and the line appeared in one frame. Now `onNavigateToWaypoint` writes the id; the collector clears the line, then **suspends on `readWaypoint`** (a Room `getById`, `AppContainer.kt:423`), then draws on the next state emission. The HUD, which the screen resolves synchronously, is up at least one frame before the line. The read is a single indexed row; I put it at a few ms to tens of ms, one or two frames at 60 Hz, below what a person notices in the usual case, and on the picked-back-up path the read runs at construction, long before the first fix, so there it costs nothing visible. This is what the owner's "look up by id" (-1657) buys the line with; I record it as a note, not a change to argue over. Rowan's question "the new one-database-read delay" is answered: yes, it exists, this large, on Navigate and on a target switch only.

### 2.2 Can any frame show a wrong line?

A wrong line would be the line to the old target drawn under a new one. The only route is a frame rendered between the id write and the collector's clear. **Inferred: no such frame.** `onNavigateToWaypoint` runs on the main thread (a click handler) and writes `_uiState` synchronously (`AvailabilityViewModelWaypointNavigation.kt:35`); the collector is on `viewModelScope` (`Dispatchers.Main.immediate`), and `StateFlow` resumes a waiting collector in place when no dispatch is needed, so the clear at `AvailabilityWaypointLine.kt:47` runs inside that same `update` call, before the handler returns and long before the next frame. Compose then composes from the latest snapshot: id B, line null. The screen's own gate (`:416`, `navigatingToWaypoint?.let { … }`) also drops the line whenever its resolved target is not Found. The Robolectric cases run everything to idle and cannot see frames, so this is a reading of the dispatcher, not a measurement; a caller off the main thread could open the window, and there is none (read: `MainActivity.kt:676` binds the click to the ViewModel function directly). The step 6 report's §6 item 5 phone check (the faded line, and the line kept across a trip to the Journal) is the right place to look at this with eyes; the PR lists it as not run.

### 2.3 Does the screen's "gone" rule still end navigation before any ViewModel line is drawn?

**Yes, on the screen; the ViewModel's state can hold an undrawn line meanwhile.** Precisely:

- The only main-source reader of `uiState.waypointStraightLine` is `AvailabilityScreen.kt:416` (`git grep` at the head: the state field, the collaborator's two writes, and that one read; the rest is the existing `straightLine` plumbing downstream of it). It reads the line only through `navigatingToWaypoint`, which is `null` for `Waiting` and for `Gone` (`:332-333`).
- The `Gone` effect is unchanged (`AvailabilityScreenNotices.kt:100-106`): it ends the navigation and shows "Stopped navigating: that waypoint was deleted."; ending it nulls the id, which cancels `follow` and clears the state line.
- An Undo-pending waypoint **is still in the store** ("hidden … at once, still saved", `TrackRecordingPendingDeletes.kt:40-42`; the real delete runs at `commitRemoveWaypoint`, `:72`), so `getById` returns it and the ViewModel can compute a line for it into state. It is never drawn: the screen's list (`trackUiState.visibleWaypoints`, `MainActivity.kt:683`) leaves it out, the target resolves `Gone`, and the gate above holds. The screen test "a waypoint deleted while navigating to it ends the navigation and says so" (`AvailabilityScreenWaypointNavigateTest.kt:586-597`) exercises exactly this masking, by accident of its fixture: its `storedWaypoints` is a constant `listOf(creek, oak)` (`:150`), so the ViewModel is still handed `creek` after the screen's list drops it, and the case passes on the gate alone (`assertNotNavigating` asserts `map.content.route == null`, `:301-305`). The sibling "deleted while the app was closed" uses `wp-gone` (`:610`), which the fixture does not store, so there the ViewModel's `readWaypoint` returns null as production's would.

So the coder's "the screen's gate stays the one rule" is right, and -1657's "it applies no hiding rule of its own" is met. The collaborator's doc (`AvailabilityWaypointLine.kt:24-28`) says the same.

### 2.4 The test setup change

**No assertion or test body changed.** The diff of `AvailabilityScreenWaypointNavigateTest.kt` has three hunks, all setup: imports (`LocationTracker`, `filterNotNull`); the fields (`fix` and `fixPresent` become properties whose setters also publish to a `MutableStateFlow` the fake tracker exposes; `storedWaypoints`, `clock`); and `setScreen` (the ViewModel is built with `locationTracker`, `readWaypoint`, `currentTime`, and `onEnteredForeground()` is called; `currentTime = clock` replaces an identical inline lambda). The 22 `@Test` bodies are byte-identical (the diff shows no hunk inside one). 708 → 740 lines. The fail-first commit `7404a3562` made this setup change while the screen still drew its own line, and run 40274113620 at `5a9f328b5` shows all 22 screen cases green under it, so the new setup was shown not to disturb the old mechanism before the move.

**What the cases now prove, and by what route.** Before, the screen was handed `state.copy(liveFix = …)` and computed the line itself, so the cases proved the screen's computation. Now the screen still receives that copy for the HUD, while the line comes from the ViewModel fed the same fixes through the tracker, through the real `AvailabilityLiveLocation` gate. The cases therefore prove the ViewModel-to-screen path end to end, which is more than before, with one thing they can no longer prove: that the HUD and the line read **the same** fix. The test feeds both in step by construction; that they are one field in production is a reading (section 2.1, second bullet), not a test. A constant clock (`t + 1_000`) means the collaborator's sleep loop never ticks in the screen cases, exactly as the base's `produceState` loop never ticked under the same clock; the fade and grey timings are proven by the new ViewModel test with the scheduler's clock (`AvailabilityViewModelWaypointLineTest.kt:58-75`: current at 29.999 s, faded at 30 s, grey at 5 min, cleared on stop). Nothing is weakened; one coupling moved from "tested" to "read".

## 3. "Waypoint positions never change after placing"

**Holds, read from the code**, with one path the coder's four citations cover only by implication:

- The store's writers are `RoomWaypointRepository.save` → `dao.upsert` (REPLACE on id, `RoomWaypointRepository.kt:32`, `WaypointDao.kt:50-51`), `dao.clearTrackId` (`WaypointDao.kt:47`, `UPDATE waypoints SET trackId = NULL`, position untouched) and `deleteById`. `save` has two callers: `CreateWaypointUseCase.kt:42` (id from `idGenerator`, a fresh UUID) and `ImportGpxUseCase.kt:114`, whose waypoints get `id = idGenerator()` (`:42, :79, :93`, `UUID.randomUUID`). So no app path writes a row that already exists (read; `git grep` for `INSERT`, `UPDATE waypoints`, `REPLACE INTO` finds no raw SQL on the table outside the backup).
- The backup is the one place that rewrites rows by id. **Merge** skips a record whose id is already on the phone (`domain/JournalBackup.kt:21-23`, the coder's citation). **Replace** (`RoomJournalBackup.kt:261`, `DELETE FROM` every journal table; `:457`, plain `INSERT`) deletes and re-inserts inside one transaction (`:254`); the row that comes back carries the archive's values, which for the same id are the values it was created with. A hand-edited archive could move a waypoint; that is not a product path. While navigating through a Replace restore, the screen's list reloads after the transaction and the target is Found again if the archive had it, and the ViewModel's copy is the same position (inferred from the restore reading above; not exercised).

The verify-first the dispatch asked for ("trace `WaypointRepository.save`'s callers") is answered correctly in the PR body and the collaborator's doc (`AvailabilityWaypointLine.kt:26-28`).

## 4. Effect order

The removed block (base `AvailabilityScreen.kt:416-432`) held one `remember`, one `produceState` (itself a `remember` plus a `LaunchedEffect`) and one `LaunchedEffect`. Their only outputs were `waypointStraightLine` and `straightLineNow`, whose only readers were `:431` and, through `nextWaypointLine`, the controls at `:854` (`grep` at the base: no other line names either). The ten `LaunchedEffect`s that follow (`:506` to `:936` at the base, `:490` to `:920` at the head) keep their relative order and keys, and none reads those values. So the report's flag resolves as the coder said: no effect-order dependency (read). The phone check in report §6 item 5 stands as the eyes-on confirmation; listed, not run.

## 5. The dispatch's terms

| Term | Met | How I know |
|---|---|---|
| None visible, except the accepted survival | Yes, plus the timing note in 2.1 | sections 2.1 to 2.3 |
| Line state in the ViewModel, work in a collaborator | Yes | `AvailabilityWaypointLine.kt` (64 lines); start/stop decided at `AvailabilityViewModel.kt:377-381` (-1450) |
| Waypoint and clock via the factory, not `MainActivity.kt` | Yes | `AvailabilityViewModelFactory.kt:64-66`; `MainActivity.kt` untouched |
| Look up by id at start and at pick-up; no hiding rule of its own | Yes | `follow` at `:46-52`; the collector runs for a stored id at construction |
| The screen's gate stays the one rule | Yes | section 2.3 |
| Test setup may change, assertions not | Yes | section 2.4 |
| `AvailabilityViewModel.kt` under 800 | Yes, 781 | `wc -l` at `9114eda5b` (761 at the base) |
| CI only; new branch off current pre-main; PR into pre-main; no merges | Yes | three linear commits off `19c85f78e`, the pre-main of the dispatch's writing |
| Device-only listed, not run | Yes | PR body's last line: effect order on the phone, Navigate across a trip to the Journal |
| Existing tests pass unchanged, said plainly as a move's evidence | Yes | PR body; `StraightLineToTargetTest` (12) and `RouteStraightLineLayersTest` (5) exist and ran (section 6) |
| New ViewModel test through `onNavigateToWaypoint` with a fake clock, 30 s fade, failing over a stub first | Yes | section 6.1 |
| Not touched: `AvailabilityCompactScaffold.kt`, `TrackRecordingViewModel.kt`, `MainActivity.kt`, the track UI state | Yes | `git diff --name-only 19c85f78e 9114eda5b` |

## 6. The runs

All four cited runs are on `waypoint-line-to-viewmodel` or its revert branches; the three `workflow_dispatch` runs used the filter `*AvailabilityScreenWaypointNavigateTest;*AvailabilityViewModelWaypointLineTest` and, by the pattern's breadth, also ran `StraightLineToTargetTest` and `RouteStraightLineLayersTest`: 22 + 2 + 12 + 5 = 41 cases in each (XML).

### 6.1 Fail-first

- **40273820490** at `7404a3562`: did not compile, `AvailabilityUiState.kt:354 'public' function exposes its 'internal' parameter type 'StraightLine'` (three jobs red). The coder disclosed this in `5a9f328b5`'s message; no test ran, and no result was cited from it.
- **40274113620** at `5a9f328b5`: 0 compile errors; 41 run, **2 failed**, both `AvailabilityViewModelWaypointLineTest`, both `expected:<StraightLine(points=[(45.3215, -122.634), (45.326, -122.634)], isCurrent=true, isOffline=false)> but was:<null>` at `:58` and `:86`. The stub cleared the line and drew nothing (`7404a3562`'s message), and the 22 screen cases were green under the new setup with the screen still drawing. **Prediction 1 held.**

### 6.2 Green

- **40274772010**, the `pull_request` run at head `9114eda5b` (merge ref against `12a073783`): all five jobs green; **5,574 run, 0 failed, 24 skipped** (749 XML files). The four line classes: 22, 2, 12, 5, all green.

### 6.3 Reverts

Both are one-line edits committed on their own branches on top of `9114eda5b`, so the forward change was never touched: the PR head is still `9114eda5b` (`gh pr view`), and `git diff 9114eda5b origin/revert-wpline-r{1,2}` shows exactly the one line each.

- **r1** (`dc30a707c`, `AvailabilityScreen.kt:416` → `val nextWaypointLine: StraightLine? = null`), run **40274822121**: 0 compile errors; 41 run, **5 failed**, all `AvailabilityScreenWaypointNavigateTest`, each `expected:<[LatLng(...), LatLng(45.326, -122.634)]> but was:<null>`: the bubble Navigate (`:321`, "the dashed line, from the walker to the waypoint"), lost-kept (`:485`, the precondition line), stale (`:522`, "from the stale fix"), picked back up with no fix (`:545`), app opened again (`:606`). Every one is an assertion on `map.content.route.straight`; the 17 other cases, which assert the line's absence or do not touch it, stayed green, and both ViewModel cases stayed green, since the ViewModel still computed the line. **Prediction 2 held**, and the failures are ones only this edit can produce.
- **r2** (`6cfb590d8`, `AvailabilityViewModel.kt:381` → `follow(null, …)`), run **40274824545**: 0 compile errors; 41 run, **7 failed**: the same five screen lines with the same messages, plus both ViewModel cases (`:58`, `:86`, `but was:<null>`). **Prediction 3 held.**

### 6.4 The count

Pre-main's run on the PR's base, **40271808291** at `19c85f78e`: 5,569 run, 24 skipped. The PR's 5,574 is +5, not the +2 the two new cases give. Per class (XML diff): `AvailabilityViewModelWaypointLineTest` +2 and `ReturnWatchDottedLineTest` 5 → 8. The three are #406's (`git diff --stat 19c85f78e origin/pre-main` at the time: `ReturnWatchDottedLineTest.kt` +68 −2), which the `pull_request` run picked up through the merge ref (`ci.yml:71`, `actions/checkout` with no `ref`). So the total matches its source once the merge ref is counted, and the PR's "5,574" is the merge's figure, not the branch's. **Prediction 4 missed by 3**, for that reason; recorded in section 10.

## 7. The beyond-scope choices

- **`StraightLine` public** (`NavigationArrival.kt:63-64`). Forced: `AvailabilityUiState` is a public data class, and Kotlin refuses a public property, constructor and `copy` over an internal type (run 40273820490's error). The alternative, an `internal` property, fails the same way on `copy`; making the UI state internal is a far larger change. Right call. **It is also a file outside the dispatch's list**, changed without a stop (the skill: "A coder who needs a file outside that list stops and reports"). The dispatch's own file list omitted it because the plan assumed a pure move. One word, compiler-forced, disclosed in the commit; I record the deviation and would not hold the PR on it.
- **The init collector on the waypoint id** (`AvailabilityViewModel.kt:377-381`). `_uiState.map { waypointId }.distinctUntilChanged().collectLatest { follow(id, liveFixes) }`: the start and stop are decided in the ViewModel's own body (-1450), `collectLatest` is the cancel, and `distinctUntilChanged` on the id stops the collaborator's own state writes from restarting it (the line's writes change `_uiState` but not the id or the fix, so neither flow re-emits; read). It runs from construction, so a stored navigation is read before the first foreground; that is what "picked back up" needs. Sound.
- **Clearing on id change** (`AvailabilityWaypointLine.kt:47`). The base's `nextStraightLine` kept a previous line only when it ended at the same target (`NavigationArrival.kt:91`); clearing first gives the same result for a target switch and is what makes section 2.2's no-wrong-frame reading hold. Sound.
- **Failed lookup logged** (`:49-51`). `errorLog.w` with a message, then no line while the HUD (the screen's target) keeps working: the user sees distance and needle without the dashed line. Logged, so within CLAUDE.md's rule; a new failure mode the base did not have, since it read no database for the line. Rare (an indexed read of one row). The `null` branch (`:52`) is not logged, and need not be: the screen tells the user ("Stopped navigating: that waypoint was deleted."), which is the honest copy the rule asks for.

## 8. Modular code and sizes

`wc -l` at `9114eda5b` (base in brackets): `AvailabilityViewModel.kt` 781 (761), under 800; `AvailabilityScreen.kt` 1,007 (1,023), shrinks by 16 and gains nothing; `AvailabilityUiState.kt` 416 (409); `AvailabilityWaypointLine.kt` 64 (new, one job, under 400); `AvailabilityViewModelFactory.kt` 67 (64); `NavigationArrival.kt` 107 (106); tests 740 (708), 97 (new), 418 (411). No CI size annotation on the green run (none expected). The skill's "what stays together" list: no `Box` child, no Back handler, no `remember` key moved; the effects are removed, not reordered (section 4). The collaborator pattern is the one the skill names (`AvailabilityLiveLocation.kt`): the ViewModel decides, the collaborator works, every write goes through `updateState`.

## 9. Merge state

`git merge-tree --write-tree origin/pre-main origin/waypoint-line-to-viewmodel` at `c3e132a54` (#407 in): clean, no conflicts; the PR's nine files and #406's two and #407's three are disjoint. GitHub's `mergeable_state` read `clean` against `12a073783` and `unknown` (recomputing) after #407 landed during this review. Should-fix 409-1 is the joint-build point; the merge itself is clean.

## 10. The four disclosure sections

### 10.1 Confirmed vs inferred

**Read from code or artifacts:** every file:line above; the diff; the three commit messages; the four run conclusions, head SHAs, job lists, the `e:` counts and the JUnit XML messages and totals; the revert branches' one-line diffs; the merge-tree result; the pre-main movement.

**Computed:** 41 = 22 + 2 + 12 + 5; 5,574 − 5,569 = 5 = 2 + 3, per class.

**Inferred:** section 2.1's size of the one-read delay (no measurement; a Room read of one indexed row); section 2.2's no-wrong-frame (a reading of `Dispatchers.Main.immediate` and `StateFlow`'s in-place resume, not a frame capture); section 3's restore-while-navigating behaviour (the restore's transaction read, the navigation not exercised through it).

**Scoring the prediction:** 1, 2, 3 held exactly, down to which cases and which messages. 4 missed by 3: I predicted the branch's count and the run built the merge ref. The miss is the useful part: a `pull_request` total is a statement about the merge, not the branch, and I will read it that way from now on.

### 10.2 Could not determine

- Anything a frame shows (2.1, 2.2): the suite runs to idle; only the phone check in report §6 item 5 can see it.
- Whether the review profile's "run its own revert check" was open to me: the brief forbids code pushes and the laptop runs no Gradle for the app, and a `workflow_dispatch` needs a pushed revert branch. I did not run one. In its place I checked the coder's two against their own XML (messages specific to each edit, 0 compile errors, the PR head untouched, every failing line mapped to a `route.straight` assertion) and against prediction before reading them. That is a check of the coder's evidence, not an independent revert.
- `CompactMapTabInputs.kt:56-58` (409-2) is cited from the step 6 report, not re-read by me.

### 10.3 Premises that were wrong, or need correcting

- The brief's "pre-main is now 12a073783 (#406 in)": it is `c3e132a54` (#407 in) as of 06:09Z. Section 9 and 409-1 are written for that.
- The PR body's "CI 40274772010 green 5,574" is right as a figure and is the merge's, not the branch's (6.4); nothing in the body says which. Not an error, a scope the figure carried silently, which is the derived-figure pattern CLAUDE.md names.
- The dispatch's file list did not include `NavigationArrival.kt`, which the move needed (7); the plan's "pure move" premise was already corrected by Amendment 1, and this is the last trace of it.

### 10.4 Decided beyond scope, or left for the owner

- I rated the one-read delay (2.1) a note rather than a visible change to raise. The owner chose look-up-by-id knowing it reads the store; whether a frame or two of HUD-before-line counts as "visible" is theirs to say if the phone check shows it.
- 409-2's stale comments are left for the next change that owns those files; I did not ask for a comment-only commit on this PR.

## One-line answers

- **Behaviour unchanged as promised?** Yes to the eye, with the accepted dark-mode/language survival; one new frame or two of HUD-before-line on Navigate and on a target switch (a Room read), invisible in the usual case; no frame can show a wrong line (inferred, not measured).
- **Does the screen's "gone" rule still end navigation for an Undo-pending or deleted waypoint before any ViewModel line is drawn?** Yes on the screen, which is the only reader; the ViewModel's state may hold an undrawn line for an Undo-pending waypoint until the navigation ends, since the store still has the row.
- **Does the test setup change weaken the screen tests?** No; 22 bodies unchanged, assertions unchanged; they now prove the ViewModel-to-screen path, and one coupling (HUD and line read one fix) is now read rather than tested.
- **Do the reverts bite?** Yes, both, with 0 compile errors and messages only their edits produce; the PR head was never touched.
- **Beyond-scope choices?** All four sound; `StraightLine` public was compiler-forced and is a file-ownership deviation to record, not to hold.
- **Modular-code rules and sizes?** Met: ViewModel 781 of 800, new file 64 of 400, the screen shrinks.
- **Clean merge with current pre-main?** Yes, against `c3e132a54`; no run has built it with #407 yet (409-1).
- **Mergeable?** Yes.
