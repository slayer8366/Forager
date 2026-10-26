# Review of PR #406 (standing at the dotted line's start alerts again), Sorrel, 2026-10-26

Ran on Fable (Fable 5.1), a different model from the coder (Opus). Review only: no app code, no Gradle, no phone; the one write is this file. Written at 2026-10-26T05:15Z against:

- PR #406, `dotted-start-standing` at `4ca137578` (the step 7 coder): fail-first `3886c7627`, fix `f01229e43`, comment commits `a62295bbe` and `4ca137578`; merge base with `origin/pre-main` `efeeda359` (confirmed with `git merge-base`). The PR's four commits are these four (`gh pr view 406`).
- dispatch `prompts/preserved/2026-10-26-03.md` (read from `origin/dotted-start-standing`), RECORD -1640 and -1645 (read from `origin/records-after-173`, with -1641, -1648, -1650, -1651 and -1654 for context), my earlier review `prompts/preserved/2026-10-26-sorrel-402-a2-review.md` (branch `sorrel-402-a2`, `760cfd0c3`), items 402a2-1 and 402a2-3.
- the coder's evidence comment, PR #406 issue comment 6408428306 (read through `gh api`).
- the code at `4ca137578`: `WayHomeOnCourse.kt` (whole), `WayHomeProgress.kt:104-224`, `ReturnWatchDottedLineTest.kt` (the diff against `efeeda359`); the three revert branches `dotted-start-standing-r1`, `-r2`, `-r3`; PR #404's diff against its base `44af58a3c`; `~/.cache/step7-a2/tools/revert.py`.

**Pre-main moved during this review.** It was `a41761c8f` (#401, #403, #405 in) when I started and is `19c85f78e` now: **#404 merged at 2026-10-26T04:58:45Z** (RECORD -1651, after Hazel's diagnosis found her first S22 verdict had measured the purple recording line, not the way home's dots). The dispatch and my brief describe #404 as unmerged; section 5 is written for the tree as it is now.

Every CI claim is from the run's "Build, test, publish APK" job log fetched through the API and the `unit-test-report` JUnit XML downloaded per run (`~/.cache/sorrel-406/logs/`, `~/.cache/sorrel-406/xml/`). Each of the seven job logs has 0 lines matching ` e: `, `compileDebugKotlin` and `compileDebugUnitTestKotlin` both ran (no `UP-TO-DATE`/`FROM-CACHE`), every other job of each run is green, and the XML timestamps are distinct per run (table in section 6). Every result below describes the build it claims to.

Geometry claims are from reading the code and from a replica (`~/.cache/sorrel-406/replica/standing_at_start.py`, review scratch, not app code) extended from the 402-a2 one: it reproduces the test class's seven walks under the head's rule (1, 1, 1, 2 alerts; 2, 1, 1) before anything else is asked of it, and runs the same walks under the old rule, the coder's two rejected options, and a 10 m gate. Flat plane, fixes carry no accuracy, as the tests' do; what that leaves out is in section 8.

## Mechanism prediction (written after reading the code, before running the replica; scored in section 8)

1. The gate bites only in the latch case: the place leaves the first node only when the fix is over 25 m from it, so for a walker heading home steadily the new condition is already true wherever the place half counted, and the only readings it removes are ones taken within 25 m of node 0 after the place left it.
2. The coder's r2 claim holds: after the past-the-start test, `metersAheadOfLineStart > 0` fails only at the exact first node, so my 402a2-1 suggestion was a no-op.
3. The 25 m gate will be more robust to jitter than a 10 m one for the standing walker, because a reading has to land over 25 m from node 0 and ahead to restart the hold, against 10 m.
4. The cost is a drawn line that leaves its start and comes back within 25 m of it less than 10 m ahead; a walker following it there is uncounted, and if they were re-armed on the way out and the stretch is 15 s long, they are alerted while following the line home.

## 1. Verdict

**Mergeable as ruled.** The fix closes 402a2-1 both ways in (section 3.1), does nothing beyond 25 m of the line's start (prediction 1 held), keeps the three ruled behaviours (3.2 to 3.4), and the 25 m is the better of the two gates the coder weighed (3.6). The seven runs are sound and each revert's failures are the ones its edit can produce (section 6). Both of my earlier suggestions were wrong, and the coder was right to reject them (section 2).

**Blocking:** none.

**Should-fix** (none holds the merge; the first is a comment line, the other two are wording for the next step):

- **406-1.** The code comment's "What it costs" paragraph (`WayHomeOnCourse.kt:94-96`) says readings under 10 m ahead "no longer count by the place" on a line that curls back within 25 m of its start. Measured, that is **one alert while following the drawn line home** on such a line, where the old rule gives none (section 3.5: 29 to 32 s off and uncounted against a 15 s hold). The owner ruled "Coder proposes, Sorrel checks" for the figure; the proposal's cost should be stated as the alert it is, not as the readings. One comment line.
- **406-2.** The device item "head back 30 m, walk out to the start, stand 60 s (one alert)" needs "30 m past the dotted line's start", or "until the Off track state has been clear for 10 s". A walker who went on past the re-route point before turning has a line start behind their turn; heading back 30 m from the turn may be under 10 s counted, so they are never re-armed, and standing at the start then gives no alert because the one alert for that stray already came (section 5, replica section 7 and its trace). Written as it is, the step can file the once-per-stray rule as a failure of this fix.
- **406-3.** No run has built #406's code with #404's. The PR's two `pull_request` runs (04:02Z, 04:27Z) merged the head with a base before #404 landed (04:58Z); the files are disjoint (`git merge-tree`: 0 conflicts) but the behaviour couples through where the line starts (section 5). Pre-main's push run after this merge will be the first; either wait for it or re-run the PR's checks on the moved base first. Same shape as my #404 review's S2, which -1651 notes was closed for #404 by pre-main's run.

**Notes:** sections 2, 3.6, 3.7, 6.2, 6.3 and 7.

## 2. The r2 claim: was my 402a2-1 suggestion wrong? Yes.

**Read.** `isHeadingBackAlong` (`WayHomeOnCourse.kt:118-123`) returns false first when `metersPastLineStart(line, here) > 0.0`. `metersPastLineStart` (`WayHomeProgress.kt:164-174`) returns the fix's distance from node 0 when the dot product of the first stretch with the way to the fix is `<= 0.0`, else 0. So after that line, either the dot product is above 0, or the fix is at node 0 exactly (dot 0, distance 0, so `> 0.0` is false). `metersAheadOfLineStart` (`:181-191`) is that same dot product over the stretch's length. So `metersAheadOfLineStart > 0` is true wherever the first test let the reading through, except at the exact first node. **"(a) AND `metersAheadOfLineStart > 0`" differs from (a) at one point of measure zero.** The coder's "it is what the not-past test already gives" is right.

**Computed.** Replica rule `r2` matches rule `old` on all seven test walks (1, 1, 1, 2; 1, 0, 1) and on every standing trial (section 3.2 table: 1/200 at every sigma, the same single trial as `old`). CI revert r2 fails the same two cases with the same two messages as r1, which is the gate removed (section 6).

My 402-a2 text also offered "the fix itself is more than 10 m ahead of the start" as the gloss on that condition. That reading makes the place half imply the node-step half, which is dropping (a), and r3 shows what that costs (section 3.4). Neither reading of my suggestion was usable; recorded here so the next person does not pick it up from the earlier review.

## 3. The 25 m gate

### 3.1 The two ways in, closed (the dispatch's change)

Replica, the test's conditions (every reading stored, no store lag), rule `gate25`: heading back 30 m then standing at the start with the test's jitter cycle: **2 alerts** (old rule 1); one reading jumping 30 m toward home then standing at the start: **1 alert** (old 0); both with the generations the test asserts. Matches the fail-first and forward runs (section 6).

**Why it closes both** (read): `placeOnWayHome` (`WayHomeProgress.kt:128-149`) takes the earliest node from the previous place on within 25 m, so the place leaves node 0 only when the fix is over 25 m from it, and never comes back. Within 25 m of node 0, a place free to move back would be node 0 again, whose along-distance is 0; the gate `GeoDistance.metersBetween(node 0, here) > WAY_HOME_TRAIL_JOIN_METERS` makes the place half read exactly as that place would. Beyond 25 m both agree. The gate is therefore "the place half as if the place did not latch", and nothing else; prediction 1 held.

One detail the replica trace showed that I had wrong in my own head: after heading back 30 m the latched place is **node 1** (about 10 m along), not a node 30 m along, because the place is the *earliest* node within 25 m. It changes nothing above (the gate reads the fix's distance to node 0, not which node latched) but it kills one alternative, section 3.7.

### 3.2 Standing at the start against jitter (computed)

Production sampler (5 s / 5 m), no store lag (which is the #404 tree, section 5), Gaussian jitter of sigma metres on every reading, 200 trials of 60 s standing; trials in which the alert came while standing:

| Shape | Rule | s=1 | s=3 | s=5 | s=8 | s=10 |
|---|---|---|---|---|---|---|
| back 30 m, out to the start, stand | old (#402) | 1 | 1 | 1 | 1 | 1 |
| | **gate 25 m (#406)** | **200** | **200** | **200** | **177** | **123** |
| | gate 10 m | 200 | 200 | 192 | 71 | 26 |
| | r2 | 1 | 1 | 1 | 1 | 1 |
| one 30 m jump, stand at the start | old | 1 | 1 | 1 | 1 | 1 |
| | **gate 25 m** | **200** | **200** | **199** | **129** | **71** |
| | gate 10 m | 200 | 200 | 182 | 36 | 12 |
| stand at the far end, no latch (402-a2 §1.2's case) | old | 200 | 200 | 200 | 192 | 151 |
| | **gate 25 m** | 200 | 200 | 200 | **194** | **170** |
| | gate 10 m | 200 | 200 | 200 | 193 | 159 |

Readings: the old rule's 1/200 is one trial's alert on the walk, not while standing; the standing walker is never alerted under it, at any jitter. The 25 m gate alerts in every trial up to sigma 5 and in 177 of 200 at sigma 8; the 10 m gate falls off from sigma 5 (192) and is 71 at sigma 8. Prediction 3 held: under the 10 m gate a reading 10 m off node 0 and ahead restarts the hold (about 46% of readings at sigma 8 are over 10 m from the mean), under the 25 m one it takes 25 m (under 1%). The misses that remain under the 25 m gate at sigma 8 and 10 are mostly the node-step half's own: a reading 10 m ahead along the stretch counts by (b) whatever the gate (the far-end row, where no place latched, is 194 and 170 at the same sigmas). The gate also helps the far-end stander slightly at high jitter (194 against 192, 170 against 151), because a jittered reading over 25 m from node 0 can latch the place by itself (402-a2 §1.4) and the gate then stops that latch counting while they stand.

The S22 reports a constant 3.79 m accuracy on GPS fixes (402-a2 §1.4); real woods jitter is not Gaussian, and I have no measurement of it, so the sigma that matters on a device is unknown. The table says which gate degrades first, not where the device sits.

### 3.3 Following the dotted line home never alerts (computed)

Out to 200 m (four re-routes) and back to the trail, store lag on and off, route job delay 0, 3 and 8 s: **1 alert** (the one on the way out), `isOffTrack` false at the end, under old, gate 25 and gate 10 alike, all twelve runs. The bent stray of the test followed home with the production sampler and store lag (test shape: 50.4 m east, 49.6 north, 7 east, then back): 1 alert, off false, under every rule including r3, lag on and off; with 5 m kept-point spacing the line's first stretch at that turn is not the exact 7 m westward one the test stores, so (b) carries it there. The r3 cost is real where the stored points make the first stretch turn within a node step (the test's case and the direct test), and depends on the kept-point spacing, which the coder's comment does not say; a note, not a defect.

### 3.4 A pause while heading back counts (computed)

Out 110 m, back to 80, stand 60 s: 1 alert, off false, under old, gate 25 and gate 10 (no lag). With the store lag, off is **true** through the pause under all three, as in 402-a2 §1.3; that is the lag's cost, unchanged by #406, and #404 closes it (section 5). Standing 30 m back on a straight line is 30 m from node 0, so the gate does not act; on a straight line the two halves agree wherever the gate could.

### 3.5 The cost (computed): a line that comes back within 25 m of its own start

A shape that has it: stray east to 100.8 m (two re-routes), north 45 m, west 30, south 25, east 15, north to the next re-route at about 50 m from the line, which fires at P. The drawn line from P runs south 30 m, west 15, north 25 (into the band 0 to 10 m ahead of P within 25 m of it), east 30 along that band, then south and home. A walker following that drawn line home exactly:

| Rule | every reading stored | production sampler |
|---|---|---|
| old (#402) | 1 alert, longest off-and-uncounted run 11 s | 1 alert, 13 s |
| **gate 25 m (#406)** | **2 alerts**, longest run **29 s** | **2 alerts**, **32 s** |
| gate 10 m | 1 alert, 12 s | 1 alert, 14 s |

The first alert under every rule is the pre-existing one of 402-a2 §1.2 (the walker reaches P armed with the hold part run, and the first 10 m back are uncounted under every rule). The **second alert under the 25 m gate** comes on the east leg along the band: re-armed on the 30 m south leg (counted by (b) from 10 m on, over 10 s), then 29 to 32 s off and uncounted within 25 m of P, against the 15 s hold. The 10 m gate's band is 16.8 m of that leg, 12 to 14 s, one to three readings short of the hold: no alert, by a margin a slower walker or a wider jitter would close. Prediction 4 held, and it is the real cost the coder's comment should name (406-1).

How often: the shape needs the walker to have worked a stretch of 21 m or more within 10 m behind the point where the re-route will fire, then gone 25 m or more away, then come back to that point from the side they worked, all within 50 m of the previous line, and then to follow the drawn line (not a straight line) home. A forager working a strip at the edge of the 50 m band can do it; a walker going out and coming back cannot. It is also exactly the cost a non-latching place would have had: the old rule avoided it only by the latch, which is the defect this PR fixes. I would not hold the merge for it, and the owner should hear it.

The 402-a2 lag cost (the line's start up to one store write behind the turn) applies to every number in this section until #404 is in; it is in now.

### 3.6 25 m against 10 m

Both close the two ways in (section 3.1, both at 200/200 to sigma 3). They part at jitter sigma 5 and above (3.2: 200 against 192, 177 against 71, 199 against 182, 129 against 36) and on the looping line (3.5: an alert against a near miss). The 25 m is the place's own reach, no new figure, and reads as "the place half as if the place did not latch"; the 10 m would be a second use of the node step for a different purpose. The standing walker is the case the alert exists for and the looping line is a nuisance alert on the way home. **25 m is the right choice of the two,** and the choice is the owner's to confirm with the cost in front of them.

### 3.7 One alternative tried and dead (computed)

Gating the place half on the place's own hop (fix to its node within 25 m) instead of the fix's distance to node 0 would keep the looping line counted (the place is a node of the loop, within 25 m). Run: 1/200 on both standing shapes at every sigma, no better than the old rule. The reason is 3.1's detail: the latched place is node 1, about 10 m from node 0, so a walker standing at node 0 is within 25 m of it and the hop gate stays open. Recorded so the coder does not go there.

## 4. Lock order and the place read

Unchanged from #402: the new line reads `line.nodes.first()` and `here`, both already in hand inside `isHeadingBackAlong`; no new call into either watch. The 402-a2 §2 reading stands.

## 5. Interaction with PR #404 (now merged, pre-main `19c85f78e`)

**Read.** #404 hands `WayHomeWatch` each kept point (`onKeptPoint`, `WayHomeKeptPoints`) and gives `findWayHome` the stored track plus the kept points newer than its last stored one (`WayHomeWatch.kt`, `keptPoints.addedTo(track)`), so a re-routed line starts at the newest kept point (at most about 5 m / 5 s behind the walker) instead of the newest stored one (up to 20 points or 30 s behind). It touches no file #406 touches (`git merge-tree`: 0 conflicts) and does not read `isHeadingBackAlong` or `placeOnWayHome`.

**Does #406's behaviour change with #404 in?** Not the rule; what changes is which point node 0 is, and that moves every figure in section 3 onto the shapes the tests use:

- The standing shapes with the kept start (replica section 7, `store_lag=False`): **100/100** at sigma 3 and 5, both ways in. With the lagged start (`store_lag=True`): 0/100 and 15/100. The lagged zeros are **not the latch**: the trace (replica, section A) shows the line's start 26 m behind the turn, so heading back 30 m from the turn is 4 m past it, 3 s counted, never re-armed; the one alert for that stray stands and no second comes. That is the once-per-stray rule, and it is why 406-2 asks the device item to count the 30 m from the dotted line's start, not the turn.
- The 402-a2 lag costs close: off through a pause (3.4), the first-metres alert window widened by the lag (402-a2 §1.2), and the 10 m added to by the lag.
- The first stretch is still two kept points at least 5 m apart (stored points were kept points, only later), so the direction noise of 402-a2 §1.2 is unchanged.
- Hazel's S22 diagnosis (-1651) found the way home's line starting 0.0 m from the newest point on the diagnostic build; her earlier "still at the last written point" had measured the recording line. So the kept-start rows above are the device's rows now, subject to 406-3.

## 6. The runs and the reverts

Each revert's parent is `f01229e43` and its diff against it is the one edit shown (`git diff f01229e43 origin/dotted-start-standing-rN`): r1 the gate removed (`val placeCounts = placeAlong > 0.0`); r2 the gate replaced by `metersAheadOfLineStart(line, here) > 0.0`; r3 `return metersAheadOfLineStart(line, here) >= WAY_HOME_LINE_STEP_METERS` (leaving `placeCounts` unused; a warning at most, and the job log has 0 ` e: `). The head's refs are the ones the comment names (`gh run view`, `headSha` per run).

| Run | Ref | Event | ` e: ` | Tests / failed | Failures, from the XML | Belongs to its edit |
|---|---|---|---|---|---|---|
| Fail-first 40265801686 | `3886c7627` (tests only, on `efeeda359`) | dispatch | 0 | 27 / 2 | "headed back 30 m, then held at the line's start: the stray out and the stand there, two alerts expected:<2> but was:<1>"; "one reading jumped toward home, then held at the line's start: one alert expected:<1> but was:<0>". DottedLine 8 cases. XML 03:52:46Z | Yes: the latch, both ways in; the bent-stray guard passes on the base, as the coder says and as 3.3 explains. |
| Forward 40266468160 | `f01229e43` | dispatch | 0 | 27 / 0 | none. XML 04:01:28Z | the fix. |
| r1 40266470685 | `44eb0689e` | dispatch | 0 | 27 / 2 | the same two messages. XML 04:00:13Z | Yes: the gate removed is the old rule; those two cases and no other. |
| r2 40266472653 | `a291275a1` | dispatch | 0 | 27 / 2 | the same two messages. XML 04:01:37Z | Yes, and it is the demonstration of section 2: identical to r1 because the condition is the old rule. |
| r3 40266474448 | `34c2c1f5a` | dispatch | 0 | 27 / 2 | "40 m back along the bent dotted line, placed on it, is on course"; direct "40 m back along the line, placed on it, is heading back". XML 04:01:42Z | Yes: the place half dropped loses the two cases only the place half carries; the standing cases pass, as they must. 402a2-3 is now bitten through the entry point. |
| PR 40266512535 | `f01229e43` | pull_request | 0 | **5,568 / 0**, 24 skipped, 746 suites | none. XML 04:02:08Z to 04:18:46Z | the fix with the base of 04:02Z. |
| PR 40268622230 | `4ca137578` | pull_request | 0 | **5,568 / 0**, 24 skipped, 746 suites | none. XML 04:26:38Z to 04:42:35Z | the head; comment-only commits on top. |

5,568 is 5,560 (#402's green) plus 3 new DottedLine cases plus 5 from #401/#403/#405's merges into the base between the two; I did not break the five down. The two full runs' totals are from their 746 XML files, not the run's table.

### 6.2 Force pushes (a finding only, as asked)

`~/.cache/step7-a2/tools/revert.py` (read): `git reset -q --mixed d83d11999` in the forward worktree, assert clean, edit, `git commit -q -am`, then `git push -q -f origin HEAD:refs/heads/off-track-reroute-track-a2-<name>`, then restore the file from a saved copy. The `-f` is to a ref named per revert, so it overwrites only if that name already existed.

The repository events API (`repos/.../events`, 300 events, covering 2026-10-23T14:50Z to 2026-10-26T04:46Z with visible gaps) shows: `CreateEvent` for `off-track-reroute-track-a2-forward`, `-r1`, `-r3`, `-r5`, `-r6`, `-r7`, `-r8` (02:37:52Z to 02:39:00Z) and for `dotted-start-standing-r3` (03:55:54Z, six seconds after the forward push); **no event at all for `a2-r2`, `dotted-start-standing-r1` or `-r2`**, which exist on the remote; and **no `PushEvent` on any revert ref with a non-zero `before`**, which is what a force push over an existing branch would produce. Three refs with no create event in the same window is the API dropping events, so this is "no evidence of a force push", not evidence of none. The remote state is consistent with none: each of the seven `a2-r*` branches and the three `dotted-start-standing-r*` branches is exactly one commit on its forward parent (`git rev-list --count`). The coder's #406 note ("each revert in its own branch and worktree off `f01229e43`, no force push") matches the parents.

### 6.3 One observation on the #402 tool (read; no damage visible)

`git reset --mixed` and `git commit -am` move whatever branch is checked out in the forward worktree to the revert commit; the next revert's reset moves it back, and after the last revert it stays on that revert's commit with the working tree restored. The remote `off-track-reroute-track` was pushed at 02:51:59Z, after the reverts, and landed at `d83d11999`, and `a2-forward` is `d83d11999`, so nothing wrong reached the remote. A tool that commits on a detached HEAD, or in its own worktree as #406's did, avoids the hazard.

## 7. Anything beyond the dispatch; file sizes

- Main code: one import, one changed line, one new line (`WayHomeOnCourse.kt:121-122`), and comments. Test: one field, one helper (`walkNorthTo`), three cases. Nothing else.
- `WayHomeOnCourse.kt` 123 lines (103 at the base), `ReturnWatchDottedLineTest.kt` 283 (217), `WayHomeProgress.kt` and `ReturnWatch.kt` untouched (224, 398). Under the 400 rule.
- The two comment commits (Rowan's relay of my #404 review's S1) say the line starts at the newest stored point "or with #404 the newest kept one"; with #404 merged, "the newest kept one" is the fact and the "stored" wording is history, in this file and still at `WayHomeProgress.kt:153-154` ("A line starts at the newest stored track point"), which the coder left for the planner. A note; a later tidy.
- Line length: 16 lines over 120 columns in `WayHomeOnCourse.kt` at the head (18 at the base); the new code line `:121` is 133. No line-length rule found in `.editorconfig`, the Gradle files or a detekt config, and lint is green, so this is style only. The rewrap commit's "the two lines the last commit left over 120" rewrapped two specific lines, not the file.
- A shape I expected to find and did not (replica section 8): a line whose later leg crosses behind its start, where the past-the-start test would uncount a walker following it home. On the shape I built the walker is within 40 m of the trail before the hold runs, so no alert, under old and gate 25 alike. Not pursued; recorded so it is not re-derived as a worry.

## 8. What I verified, how, and what I did not; the prediction scored

Verified: the rule and the gate by reading `WayHomeOnCourse.kt` whole and `WayHomeProgress.kt:104-224` at `4ca137578`, the test diff, the three revert diffs, PR #404's main-code diff, and `revert.py`; the seven runs from their job logs and XML; the behaviours and figures by the replica, which reproduces the test class first; the events API for the force-push question; pre-main's movement by `git log` and `gh pr view 404`.

Prediction scored: 1 to 4 held. Not predicted: the latched node is node 1, not a node 30 m along (3.1), which is what killed 3.7; and the old rule's own first-metres alert on the looping shape, which is 402-a2 §1.2's cost reappearing and made the loop table read "2 against 1" rather than "1 against 0".

Not verified, or outside what this review can reach:

- Nothing on a phone. The coder's two device items stand with 406-2's wording; with #404 in, "stand at the far end of a stray 60 s (one alert)" and "head back 30 m past the dotted line's start, walk out to its start, stand 60 s (one alert)" are the two.
- The replica is a flat plane, fixes carry no accuracy, the trail is straight; the route job returns the same trail; jitter is Gaussian. It is evidence about the rule against the judge and the place, not about any walk. The jitter sigma of a real device under trees is not known to me.
- The 5,568 breakdown beyond "+3 new cases" (6.1).
- Whether any revert ref was ever overwritten (6.2): the API cannot show it either way.
- No run has built #406 with #404 (406-3).

## Files

Report: `prompts/preserved/2026-10-26-sorrel-406-review.md` (this file, branch `sorrel-406`, cut from pre-main `a41761c8f`). Scratch: `~/.cache/sorrel-406/logs/` (seven job logs), `~/.cache/sorrel-406/xml/` (seven runs' JUnit XML), `~/.cache/sorrel-406/replica/standing_at_start.py`.
