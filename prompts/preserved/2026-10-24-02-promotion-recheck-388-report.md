# Elder's re-check: #388 and the known-bugs rows, pre-main fefc89129 to 073fc77bd

**Auditor:** Elder, Forager's UX auditor, a fresh session on Claude Fable 5.1 (`claude-fable-5-1`, read from this session's own environment). The coder, Hazel, ran on Opus; Sorrel's review of #388 was on Fable 5.1. **Planner:** Rowan. **Brief:** `prompts/preserved/2026-10-24-02-promotion-recheck-388.md` (records-after-173 at 074f83467 when read). **Written:** 2026-10-24, started 08:33 UTC, finished about 09:08 UTC (02:08 PDT), from `date -u`.

Read-only: no code changed, nothing merged, nothing deployed, no phone touched, no Gradle. What I ran: `git` reads of origin/pre-main, origin/records-after-173, origin/way-home-domain and the two `way-home-split-check-*` branches; `gh run list`, `gh run view` and `gh run view --log-failed` for the runs named below; `diff` on two extracted method bodies; `date -u`. Every file:line below is at `origin/pre-main` 073fc77bd unless it says otherwise. "Read" means I read it; "relayed" means the record, a review or a report says it and I did not see it; "inferred" is marked.

Yardstick: `docs/process/qa-philosophy.md` section 3 (read from origin/pre-main; the file is not on records-after-173), verdicts in section 4; CLAUDE.md's UX defaults; my re-check of the #386 range (`prompts/preserved/2026-10-24-01-promotion-recheck-report.md`) for item 2, which this range answers.

**Range:** `git diff fefc89129 073fc77bd`: four commits, two PRs. #388 (merge e26c0f591, PR head 7629d5874) and #390 (merge 073fc77bd, commit 8554e29db, `[skip ci]`). Five files, +269/−147: `TrackRecordingViewModel.kt` (−106 net, 958 to 852 lines, counted), two new files `TrackRecordingLocationFixes.kt` (140 lines) and `TrackRecordingRowReads.kt` (76), ten doc-comment lines added to `TrackRecordingPoller.kt` (104), and two rows in `docs/process/known-bugs.md`. Nothing under `app/src/test`, `.github/`, `server/`, `docs/legal/` or `README.md` changes (diff: empty), so no test moved, no CI skip list grew, and no public text changed. Room schema untouched; the upgrade check does not apply (-1133). #388 was cut from adcbedb19 (merge-base read), before #386 merged, and merged onto fefc89129; the two PRs share no file (#386's six files against this range's five: none in common, read from the two stats).

---

## Verdict: pass with items

Nothing in this range changes what a user sees or relies on, by my reading of every non-comment line (below). The split is a move: the one collector body is identical to the old one after four mechanical renames, the three log messages are byte-identical, and every start and stop decision stays on the same line of the ViewModel. Rows 18 and 19 say what my item 2 asked for. CI is green at the exact head, first attempt, on the first run that contains #386 and #388 together. One item follows, which does not risk records, safety or trust by the section 4 definitions: a residual the owner has already accepted (-1592), listed so the promotion report is complete about what it ships. Nothing in this range can be seen on a phone, because nothing in it is visible; "not seen on a phone" below means that no run of the merged build on a phone exists, not that one was owed.

---

## CI

**Green at the exact head, first attempt.** Push run 40078250926 on 073fc77bd: `completed`, `success`, `run_attempt` = 1, all five jobs success ("Build, test, publish APK", "Android lint (debug)", "Release compiles and packages (unsigned)", "Tile Worker serves what its tests say", "Route-data cutter routes the same as the full square"), read from the API at 09:06 UTC. The run on the #388 merge commit e26c0f591 (40078191007) is the same: success, attempt 1, five of five. These are the first two runs on a commit that contains both #386 and #388: #388's own PR run (40062777044, head 7629d5874, success) was cut from adcbedb19, before #386 merged. When I started (08:33 UTC) e26c0f591 was `in_progress` and 073fc77bd `pending`; I waited for both rather than cite the PR run. The known-bugs commit 8554e29db is `[skip ci]` (doc-only), so the merge commit's run is the one that covers it, and it ran.

No test skipped, deleted or weakened (no test file in the range), no growth in CI's skip list (`.github/` untouched). The suite count at head is not in the record; see "Not items".

---

## What I verified about #388

Each of the four things the brief names that a split could move without saying so, then the rest.

1. **Init order.** Both new collaborators are declared as `private val`s of the ViewModel, `locationFixes` at `TrackRecordingViewModel.kt:336` and `rowReads` at `:347`, before the `init` block at `:349`, which is where the first coroutine that can reach them (`takeUpRunningRecording`, `:352`) is launched. The one property declared *earlier* that refers to `locationFixes` is `routeTicks` (`:241`), through the lambda `{ locationFixes.lastGatedGpsFix }` (`:246`). That mattered to check: before this range the lambda read a nullable field (`lastGatedGpsFix`, declared at fefc89129's `:325`, also after `routeTicks`), so an eager call during construction would have read `null` harmlessly, whereas now it would throw on the not-yet-assigned `locationFixes`. It cannot: `TrackRecordingRouteTicks` reads that lambda only inside `updateRouteHome()` (`TrackRecordingRouteTicks.kt:87`), has no `init` block, and `updateRouteHome` is called from `begin()`/the tick, never from the constructor (read). Nothing between the old field's place and the new declaration (`takeUp` `:294`, `watchCopies` `:307`, `autoWaypoints` `:319`) refers to either name (grep). Read.
2. **What survives a configuration change.** Unchanged: the collection job and the last gated GPS fix are fields of an object the ViewModel owns, so they live and die with the ViewModel exactly as the two fields did. `onCleared` (`:837-839`) cancels through `locationFixes.cancel()` where it cancelled `locationJob`; `clearRecordingState` (`:546-551`) likewise. The two collaborators take `scope = { viewModelScope }` read at call time, so the collection still launches in `viewModelScope` and no scope of its own (`TrackRecordingLocationFixes.kt:72`, `:105`). Read; rotation during a recording not seen on a phone on this build, and there is nothing in the diff that could change it.
3. **Logging.** The three `errorLog.w` lines moved to `TrackRecordingRowReads.kt:31`, `:37-38`, `:72`; their text, tag (`TrackRecordingViewModel.Companion.TAG`, a `const val` in an `internal companion object`, `:845-850`) and the `Throwable` each carries are byte-identical to fefc89129's `:620`, `:626-627`, `:778` (grep of both files). No log line added or removed. The CLAUDE.md fallback rule holds: a row that cannot be read still takes up nothing and still logs; a read failure on resync still clears nothing and still logs. Read.
4. **A recording's lifecycle.** `beginLocationTracking()` is still called from `startRecording` and `takeUpRunningRecording`, and it is now one line, `locationFixes.begin()` (`:785`). `begin()` cancels any previous job before launching, as before (`TrackRecordingLocationFixes.kt:104`). The four places that reset or read the last gated fix are the same four, renamed: new recording (`:478`), `clearRecordingState` (`:554`), take-up (`:606`), and `stopRecording`'s end-waypoint seed read *before* the clear (`:641`, order preserved). `takeUpRunningRecording`'s early returns inside `try`/`finally { takeUpInFlight = false }` are preserved by `rowReads.trackToTakeUp(trackId) ?: return` (`:599`), and `resyncRecordingState` runs `clearRecordingState()` on the same continuation with no suspension between the read's answer and the clear (`:708`; `rowSaysEnded`'s `return@onSuccess` guard at `TrackRecordingRowReads.kt:68` is the old non-local `return`, after which nothing followed). Read.
5. **The collector body is a pure move, checked by me.** I extracted the old `beginLocationTracking` body from fefc89129 and the new `begin()` body, applied the four renames (`viewModelScope.launch`→`scope().launch`, `uiState.value`→`uiState()`, `takeUp.isTakenUp`→`isTakenUp()`, `originCreationInFlight`→`originCreationInFlight()`), stripped indentation, and `diff`ed: identical, 28 lines each. `uiState()` is `{ _uiState.value }` (`:337`), the same `MutableStateFlow` the public `uiState` is an `asStateFlow()` of (`:217-218`), so the read is the same value. The order of the three origin guards and the position of `returnToStart(point)` outside the accuracy gate are preserved. This is the same check Sorrel reports; done again rather than relayed.
6. **Every other non-comment line of the ViewModel diff** is one of: three removed imports (`LocationSampler`, `LocationFix`, `mayAct`), two removed fields, the two collaborators' construction, or a rename. Read, line by line, against the diff.
7. **Does the suite exercise the moved code?** A green pure move proves little, and Hazel's two one-line reverts are the evidence that it does. I verified both from outside her report: branch `way-home-split-check-a` (9ab963483) differs from 7629d5874 by exactly the deletion of `returnToStart(point)`; `check-b` (a6dee9566) by exactly the deletion of the `ended = true` line (diffs read). Their runs 40064335384 and 40064339092 are `failure` on "Build, test, publish APK" alone, the other four jobs `success`; the failed-step logs carry **zero** compile-error lines (`grep -cE '\be: (file|/)'` = 0 on each, the CLAUDE.md revert-runner check), and the failures are the ones each edit must produce: check a, `TrackRecordingViewModelTest` "starting a recording collects live fixes and updates returnToStart reactively" and "stopping the recording stops collecting fixes and clears returnToStart" (expected 180.0, was −1.0); check b, `TrackRecordingServiceTest` "a stop from the shade leaves the UI reporting not recording once the Activity resumes" and "... shows not recording at once, with no resume, and the track can then be deleted". Each run: "139 tests completed, 2 failed" (the filtered subset Hazel named; read from the logs). Both checks drive the moved code through the real entry points (`startRecording`/`stopRecording`; the service's stop from the shade), as the Testing rules ask.
8. **The owner's rulings.** -1450: the start and stop decisions stay in the ViewModel (verified in 4). -1490: new files 140 and 76 lines, under 400; the file being split goes to 852, over the ~800 line, which the owner accepted ("Accept 852 for now (Recommended)", -1592). Nothing in #386's six files is touched (1 above). No `@Ignore`, `@Disabled`, `assume*` or weakened assertion anywhere in the range (grep of added and removed lines: none; no test file in the range at all).
9. **Nothing a user can see.** No string, resource, composable, layout, colour, or Manifest line in the range (file list). No new dependency (`gradle/libs.versions.toml` untouched). The 80% rule, Back, insets, copy and A7 do not apply because no surface changed.

## Rows 18 and 19 against item 2

My item 2 asked for "two rows, 'New (pre-main)', 'Serious: No', each citing its RECORD entry", for the "no signal" wording on a server error (-1594) and the fast-fling blank patch (-1591). At `docs/process/known-bugs.md:42-43`:

- **Row 18** says what the user meets in plain words (the pause line reads "no signal" when the server errored, and keeps reading it for a few seconds after signal returns, about 7 s on the S22, until the next tile), that the download carries on either way, Status "Open" with the fix ruled for after the promotion and the owner's chosen line "Paused · continues by itself" quoted, cites RECORD -1594, Where "New (pre-main)", Serious "No", Seen names Sorrel's L-b, my report by path, `downloadErrorKind`, and separates what was seen (the 7 s on the S22) from what was read (the server-error case). The 7 s figure and the "until the next tile" mechanism match my item 2 and `MapLibreOfflineMapRepository.kt:402-408` as I read them yesterday. **Says what item 2 asked for.**
- **Row 19** says the user-visible outcome (patches stay coarse until the user zooms after a hard fling with no signal), the cause in user terms (only eight held), that it is not a regression, Status "Open" with the fix ruled for after the promotion, cites RECORD -1591, Where "New (pre-main)", Serious "No", Seen "Seen on the S22 during #386's Amendment 3 check (RECORD -1591)", which -1591's Observed field supports ("A hard fling offline left a blank patch"). **Says what item 2 asked for.**

Both rows follow the table's six columns (`:23-24`). Nothing else in the file changed (two added lines, zero removed). The "Not listed, fixed before it shipped" paragraph after the table is unchanged and still true.

---

## Items

### 1. The file the split was for is still over the ~800-line line, and the next PR adds to it

- **Where:** `TrackRecordingViewModel.kt`, 852 lines (counted). CLAUDE.md, "Building": "split a file over about 800 before a feature goes into it".
- **What the user meets:** nothing. This is the process residual of a split that stopped at the point -1450 fixes (the start and stop decisions stay in the ViewModel; Hazel's report says moving one of them "is the planner's call").
- **Why it matters:** the owner accepted 852 "for now" (-1592), so this is not a broken ruling; it is listed because the modular-code rule exists so that the *next* dispatch does not grow the file, and part 2 (the way-home wiring) is the next dispatch into it. Not a user-facing item; recorded so the promotion report is complete about what it ships.
- **Recommendation:** none for this promotion. Before part 2, the planner decides which of the four decision functions moves, or records that 852 stands.

---

## Not items, recorded so nobody looks for them

- **`lastGatedGpsFix` is now a public `var` on an `internal` class** (`TrackRecordingLocationFixes.kt:91`) where it was a `private var` of the ViewModel. Anything in the module can now write it; today only the ViewModel does (grep: the four sites in 4 above). Not user-visible; a visibility nit for the next pass over that file.
- **Hazel's suite figure for #388** (5,412 tests, 0 failures, identical to adcbedb19's run) is read in her report, not re-parsed. It is consistent with the PR having been cut from adcbedb19, before #386's 32-odd tests; the merged build's count is in the green run's JUnit XML, which I did not download; nothing in the range adds or removes a test, so it should equal fefc89129's count, and a different figure would be the thing to explain.
- **Sorrel's L-388-1** (`Job` import still used): confirmed irrelevant to users; not re-checked.
- **The doc moves** carry two sentences I checked for truth after the move: "what stopRecording() seeds the end waypoint from" (`TrackRecordingLocationFixes.kt:84`) is still true (`:641`); "two things in this collector must keep running with the screen off ... Reason 1 still stands" is the old text with `[beginLocationTracking]'s collector` reworded to "this collector", a true rewording.

---

## Known bugs this promotion ships with (A6)

From `docs/process/known-bugs.md` at 073fc77bd, 19 rows; main has 13. Rows 1 to 17 as in my two earlier reports. Rows 18 and 19 are this range (above), both "New (pre-main)", neither serious, both with a fix already ruled for after the promotion. **New bugs of the fail kinds in this range:** none found. **This range adds nothing a row could be about:** a split with no behaviour change and two rows.

---

## Not seen on a phone

Nothing in this range can be seen, so every phone statement below is about the *merged build* rather than about a visible change:

- **A recording on a build that contains #388**: start, pocket the phone, stop; a stop from the notification shade; a reopened app taking up a running recording. All read from the code and from the two revert checks' JVM tests; the S22 check the planner has planned (-1595) would be the first run of the merged ViewModel on a phone. If the planner wants one line added to that check: start a recording, lock the screen for a minute, stop, and confirm the end waypoint lands (the one path that reads `lastGatedGpsFix` across the split boundary, `:641`).
- **Everything in my 2026-10-24-01 report's "Not seen on a phone"** still stands; nothing in this range changes it.

---

## What I could not check

- No phone, no emulator, no Gradle: every "passes" is CI's conclusion read from the API, or a log line read from a failed run.
- The two check runs' *full* build logs: I read the failed-step logs (`--log-failed`) for compile errors and test names; I did not read the green jobs' logs.
- Hazel's completion report (`docs/audits/2026-10-24-way-home-parts-0-1-completion-report.md`) is on `origin/way-home-domain` (ba205a0d1), not on pre-main or records-after-173; read there.
- JUnit XML counts: Hazel's, read in her report; not re-parsed.

---

## Summary for the owner

Promote. CI is green on the merged build, first attempt. #388 moves code without changing what the app does: I checked the moved collector line for line against the old one, the three log messages are unchanged to the character, the starts and stops happen on the same lines, and two one-line breakages on the moved code each failed exactly the tests they should, with clean builds. Rows 18 and 19 are the two rows I asked for yesterday, each saying what the user meets, citing your ruling, and marked not serious. The one thing to carry forward is that the file is still 852 lines and the next dispatch adds to it, which you have already accepted for now.
