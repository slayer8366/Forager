# Sorrel's re-check of PR #395: way home part 2, PR B, after the Amendment 2 and 3 fixes

**Reviewer:** Sorrel (independent reviewer), on Claude Fable 5.1 (`claude-fable-5-1`, read from this session's environment). **Planner:** Rowan. **Coder:** Hazel (Opus).
**PR:** #395, `way-home-part2-domain` into `way-home-cap`. **Reviewed head:** `77963f8a4`, confirmed by `git fetch` at the start and by `gh pr view` (`headRefOid`); it did not move during this review. **Diff read:** `bedb6d809..77963f8a4` in full (four commits, four files: `WayHomeProgress.kt` +48/−15, `WayHomeWatch.kt` +22/−3, `WayHomeProgressTest.kt` +42/−6, `WayHomeWatchTest.kt` +26), plus `WayHomeProgress.kt` and `WayHomeWatch.kt` whole at the head.
**Read from source:** `CLAUDE.md`; dispatch `prompts/preserved/2026-10-24-10.md` with Amendments 1 to 3, RECORD -1613, -1617, -1618 (and -1619, the handoff) and my first review `prompts/preserved/2026-10-24-sorrel-395-review.md`, all on `origin/records-after-173` at `80cbc6041`; the PR body and both of Hazel's comments; `PathHome.kt:15-24, 123-134` (the ruling and `nextHopBand`), `LiveFixGate.kt:78`, `WayHome.kt:110`, `GeoDistance.kt:37-47, 177-180`, the test helpers `WayHomeFakes.kt` (`Ground`) and `WayHomeLines.kt`. CI read from the GitHub API: each run's head SHA, branch and jobs, the test job's full log, and the `unit-test-report` JUnit XML. Nothing run with Gradle; no phone.
**One thing I did run:** a throwaway Python replica of `WayHomeLine`'s fill and `placeOnWayHome` (same haversine, same `metersPerDegree`, same `Ground.at`), kept outside the repo. Before using it for new cases I checked it against figures from outside itself: it reproduces all five pre-Amendment-3 numbers the fail-first run printed (245.0, 315 against 320, 365 against 395, 605 against 570, 619.9 against 570) and the three head-rule expectations the green run passed (395, 570, 570), to the decimal. Every figure below marked "replica" comes from it; the hand arithmetic is given beside each so it can be checked without it.
Paths below are under `app/src/main/java/com/zynergylabs/forager/app/` (tests under `app/src/test/...`), lines at `77963f8a4`.

## Verdict

**Merge-ready: no**, by the brief's own rule ("any short case is Blocking"). The Amendment 3 rule closes B-395-1 (§1.1: both of my first-review cases now read long, and Hazel's three new tests pin them), and M-395-2, L-395-4 and L-395-9 are fixed as ruled (§2 to §4). But the rule as built measures the 50 m reach to the line's **nodes**, and the nodes are up to 10 m apart, so a fix within 50 m of the walker's true position can be more than 50 m from every node at or before them. That leaves two short cases the gate admits (§1.2, **B-395-2**): a common one of up to one node step (≤ 10 m) on the track parts, and a rare one of hundreds of metres where a later leg lies 55 to 100 m away. One edit closes both: the reach is the gate's 50 m plus the line's own node step (`WAY_HOME_LINE_STEP_METERS`), or is measured to the line's segments instead of its nodes. Because -1618 named 50 m, that is a stop-and-ask, not something to fix silently; it is small either way.

Separately, **M-395-5** (§1.3) is a consequence of -1618's rule that the record does not show the owner was told, and it is not small: on the most ordinary forager pattern, walking a trail home beside one's own wander, the distance home **rises** as the walker approaches the car and reads the whole route when 20 m from it. Never short, so not Blocking under the ruling, but sundown will read it in C, and "don't make me think" argues for the owner hearing it before B merges.

Hazel's evidence holds (§5): both fail-first runs and all three revert runs compiled with zero `e:` lines, every failure is one its edit produces, and the green run is for `77963f8a4`.

## 1. B-395-1 and the Amendment 3 rule

**What is built** (`domain/wayhome/WayHomeProgress.kt:124-144`). The place (`:130-134`) is unchanged: earliest node not before `previous` within 25 m, else within 50 m, else anywhere within 25 m, else within 50 m; its hop's band decides `OffLine` (`:137`). The **figure** is new: `figureIndex` (`:139`) is the earliest node anywhere on the line within `WAY_HOME_FIGURE_REACH_METERS` (`:160`, `= LIVE_FIX_MAX_ACCURACY_METERS.toDouble()`, the gate's 50 m, as Amendment 3 asked), and `remaining` (`:141-142`) is the line from that node plus 25 m when that node is on the trail part. No hop is added (the doc at `:121-122` says why), and `toPathHome` (`:91-92`) passes `remainingMeters` whole with `hopBand = NONE`. The `?: index` at `:139` is unreachable: a `Placed` result has its place within 50 m, so the search cannot be empty. The doc cites -1618, Amendment 3 and `PathHome.kt`'s "never short" as asked.

### 1.1 The first review's cases, re-worked

- **Case 1** (previous at (90,0), fix at (100,30)): the earliest node within 50 m of (100,30) anywhere is (60,0) on leg 1 (distance 50.0; leg-1 nodes within 50 m are those with |east − 100| ≤ 40). Along 50 + 60 = 110, so 570 − 110 + 25 = **485 m** against a truth of 420. Long by 65, never short. The test `WayHomeProgressTest.kt:114-121` asserts ≥ 420 and was red at 245 before the fix (§5).
- **Case 2** (double-back west along leg 1 from the riser): at (200,0) the earliest within 50 m is (150,0), figure 570 − 200 + 25 = 395 against 320; at (100,0): (50,0), 495 against 420; at (0,0): the trackBack's (0,−50), 570 against 520. Long everywhere. The test at `:124-134` walks all five points and was red at 315 against 320 before the fix.
- **A correction to my first review.** I wrote that case 1 read 235 m; it read **245 m**. The earliest leg-2 node within 25 m of (100,30) in walking order is (120,20) at 22.4 m, not (110,20); 570 − 350 + 25 = 245. Hazel's fail-first message (`never short: 244.99962302532396`) and the replica agree. The finding was unaffected; the figure in -1617's Observed line ("235 m against 420 m") carries my error.

### 1.2 B-395-2 (Blocking by the brief's rule): the reach is to nodes, and the nodes are up to 10 m apart

The ruling's argument is: a fix the gate admits is within 50 m of the walker; the walker is on the line; so some point of the line at or before them is within 50 m of the fix, and the earliest such point is never further along than the truth. That holds for **points of the line**. The code searches **nodes** (`:129`, over `line.nodes`), and the fill (`:47-55`) puts nodes up to `WAY_HOME_LINE_STEP_METERS` = 10 m apart, so the walker's true position can be up to 10 m from the node behind them and 5 m from the nearest. A fix at 50 m from the walker can then be up to about 60 m from the node behind them (along the line) or about 50.25 m from both neighbours (perpendicular), outside the reach, while a node **ahead** of them, or on a **later leg**, is inside it.

Cases I tried, all with the fix within 50 m of the walker's true position (the ruling's own model of "the gate admits"):

| | Line | Walker truly at | Fix | Figure | Truth | Result |
|---|---|---|---|---|---|---|
| (a) | PR's hairpin, trackBack part | (0,−49), 1 m past node (0,−50) | (0,1), 50 m ahead along the line | 561.7 | 569.0 | **short 7.3 m** (replica; by hand with the fixture's 8.33 m trackBack spacing, see note) |
| (b) | PR's hairpin, trackToStart part | (0,61), 1 m past a node | (0,111), 50 m ahead along the line | 54.5 | 59 | **short 4.5 m** (replica) |
| (c) | PR's hairpin, trail part | (105,0), midway between (100,0) and (110,0) | (105,49.9), 49.9 m north | 275 + 25 | 465 | long: the +25 covers the ≤ 5 m node gap on the trail, and the parallel leg here is 20 m away, within the reach of leg-1 nodes anyway, so a leg-1 node is earliest |
| (d) | Hairpin with legs **70 m** apart (same fixture shape, riser 70 m) | (105,0), midway between nodes | (105,49.9): 49.9 m from the walker, **50.15 m** from both (100,0) and (110,0) | **275** | 465 | **short 190 m** (replica, and by hand: no leg-1 node is within 50 m; the earliest leg-2 node within 50 m is (150,70) at 49.3 m; along 50 + 200 + 70 + 50 = 370; 620 − 370 + 25 = 275). The place is (110,70) at 20.7 m, band NONE, so it is `Placed`, not `OffLine`, and `metersFrom` is 20.7 so it is not a stray either. |
| (e) | PR's own hairpin, legs 20 m apart | (101,0) | (105.4, 49.8): 49.99 m from the walker, 50.09 and 50.01 m from (100,0) and (110,0) | **265** | 419 | **short 154 m** (replica sweep; by hand: earliest leg-2 node within 50 m is (140,20), along 330, 570 − 330 + 25 = 265) |
| sweep | hairpins with legs 20 to 110 m apart; walker at 100 to 109 m east; fix at every degree of bearing and radii 40 to 49.99 m | | | | | short cases at every separation from 20 to 90 m, worst 154 to 234 m, **only** at radii ≥ 49.75 m and bearings within about 5° of perpendicular; none at ≤ 49.7 m; none at 100 m or more apart (replica) |

Note on (a): the hairpin's trackBack, a 50.0 m part, fills to six 8.33 m steps, not five 10 m ones, because `ceil(geometric / 10)` sees 50.000…01; the same arithmetic runs in Kotlin (`:47`), so I expect the same there, but it is inferred, not confirmed, and nothing turns on it: the short case is a node step either way.

So two shapes, one root. **Shape 1** (a, b): the fix is 40 to 50 m ahead of the walker along the line, on a **track part** (no allowance), and the figure counts from the node just ahead of them: short by up to one node step, ≤ 10 m. Reachable with any admitted fix displaced forward; on the trail part the 25 m covers it. **Shape 2** (d, e): the fix is in a band about 0.25 m wide at the very edge of the 50 m, roughly perpendicular to the line, and a later leg lies between about 55 and 100 m away on that side: the figure counts from the later leg and is short by the whole intervening route. Rare in measure (a 0.25 m band at 50 m), but it is exactly the case -1618 was written to exclude, on the PR's own fixture (e).

**Remedy, for the owner and planner, not chosen here.** Reach = the gate's 50 m **plus the line's node step** (`LIVE_FIX_MAX_ACCURACY_METERS + WAY_HOME_LINE_STEP_METERS`, 60 m): then the node behind the walker, at most 10 m behind them, is always within 60 m of any fix within 50 m of them, so the earliest node within reach is never past the truth, for every admitted fix, by construction. It reads up to 10 m longer than now. It is still the gate's constant plus a figure the file already owns (`:167`), not a new one, but -1618 said 50 m, so it is the owner's word. The other way is to measure the reach to the line's **segments** (point-to-segment distance) instead of its nodes, which makes the 50 m exact and needs more code. Tests for either: cases (a) and (d) above, each asserting ≥ truth, plus a fix exactly on the line 50 m ahead of a walker 9 m past a node on a track part.

I do not want to overstate this. Shape 1 is ≤ 10 m, about ten seconds of walking; shape 2 needs a position error in a quarter-metre band at the gate's edge and a particular geometry. Against that, the ruling's whole content is "never short for any fix the gate admits", `PathHome.kt:24` says that is "the only property that matters", and the fix is one constant. Graded Blocking because the brief says any short case is; the owner can grade it otherwise.

### 1.3 M-395-5 (for the owner): wherever the route comes within 50 m of its own earlier part, the figure reads the earlier part, and on the ordinary forager pattern it rises as the walker nears home

This is not a code defect against -1618; it is what -1618's "earliest within 50 m anywhere" means on routes that pass near themselves, and the record (-1618's Observed and Decision lines) shows the owner was told "about 50 to 75 m long on an ordinary trail" and nothing about these. Before Amendment 3 the figure followed the place, which "not before the last place" kept on the current leg; Amendment 3 moved the figure off that rule by design (it was the source of B-395-1), so this is the other side of the same coin.

**The ordinary pattern** (replica; the geometry is the common one, the figures follow from the rule): the forager walks a trail 300 m east from the trailhead, steps off and forages back west 30 m from the trail, and taps Return 20 m east of the trailhead. The way home is trackBack (their wander, 280 m back east to where they left the trail), the trail (300 m west), trackToStart (20 m): 630 m. Walking the trail home, every trail node is within 50 m of a trackBack node that comes **earlier** in the line, so the figure counts from the wander:

| Walker on the trail, east | Truth | Figure | Long by |
|---|---|---|---|
| 290 | 310 | 400 | 90 |
| 200 | 220 | 490 | 270 |
| 100 | 120 | 590 | 470 |
| 60 | 80 | 630 | 550 |
| 20 | 40 | 630 | 590 |
| 0 (the trailhead) | 20 | 630 | 610 |

The distance home **rises** by 230 m while the walker covers 290 m toward the car, then reads the full route standing beside it, until they are more than 50 m from every wander node. The needle and the greyed part (which read the place, not the figure) behave correctly throughout; it is the number, and in C the sundown walk-back time, that climb. On the test's own loop fixture (`WayHomeProgressTest.kt:71-77`), a walker at the line's end with a `previous` there reads 815 m for 7 m (replica), the same shape.

Never short, so safe in the ruling's sense; but a figure that grows as you approach home is the "state the user could misread as broken" that CLAUDE.md's "Don't make me think" names, and sundown reading it could raise an alert at the car. **The trade-off is real and the owner should make it knowingly:** any rule that is never short for every admitted fix must read the full intervening route wherever the line comes within the reach of its own earlier part, because at such a fix the two legs cannot be told apart; the only levers are the reach, the gate, and what the display does with a figure it knows is a self-approach (for example, keeping "not before" for the figure once the walker has been placed past the approach and is more than the reach from the earlier part — which I have not worked through and do not propose here). The step-7 alerts dispatch should cite this case whichever way the owner rules.

## 2. M-395-2: an engine `Error` falls back to the track, logged

`WayHomeWatch.kt:176-185`: `work(id, origin)` in a `try`; `CancellationException` rethrown first (`:178-179`); then `catch (e: Throwable)` (`:180`) logs once with the throwable (`errorLog.w(TAG, "Working out the way home failed; the track is the way home.", e)`, `:183`) and yields `TrackOnly(NO_ROUTE_FOUND)`. The publish block then runs as for any result: `job = null` (`:192`), `reroutingEnded = true` for a re-route (`:194`), the result shown (`:199`). So an `OutOfMemoryError` no longer leaves `job` set or reaches the scope's handler. **Confirmed from the code.** The comment at `:181-182` names the finding and the three consequences it prevents. `CancellationException` is not swallowed (a `TimeoutCancellationException` is a `CancellationException`, so it too is rethrown). The test `WayHomeWatchTest.kt:198-209` throws `OutOfMemoryError("engine")` from the second search (the re-route), asserts `shown.result == TrackOnly(NO_ROUTE_FOUND)` and exactly one log line containing "failed". Red before the fix and under revert b8 with the same message (§5).

## 3. L-395-4: the far band's hysteresis and the last place across off the line

`WayHomeWatch.kt:88` adds `lastPlace`; `:133-139` derive the previous band from `progress` (`Placed` → its band; `OffLine` → `FAR`; none → `NONE`) and call `placeOnWayHome(line, current, lastPlace, band)`, updating `lastPlace` only on a `Placed` result. So after `OffLine`, `nextHopBand(FAR, hop)` stays `FAR` until the hop is below 45 m (`PathHome.kt:125`), and the place search still starts from the last place rather than from node 0. `lastPlace` is cleared with `progress` on a new line (`:197-198`) and in `resetProgress` (`:216-221`). **Confirmed.** The test `:212-221` walks 48 (placed), 51 (off), 48 (still off), 44 (placed); red before the fix at "48 m again: still off the line" and under revert b9 with that message (§5).

## 4. L-395-9: the generation check's reason

`WayHomeWatch.kt:188-190`: "trackId covers stop and end. The generation covers one window ensureActive cannot: a second find that cancels this job after it passed ensureActive and before it took the lock (L-395-9). A single-thread test cannot open that window, so the tests pin the guard as a whole (revert b3 removes both lines)." That is the reason as I gave it and it is accurate. The PR body's Evidence section now says b3 "removes **two** lines, `ensureActive()` and the generation condition, and pins the publish guard as a whole", with the one-line case explained. **Done as asked.** (The PR body's "What" bullet for `WayHomeProgress` still describes the Amendment 1 rule only; Hazel's second comment carries Amendment 3. L-395-10 below.)

## 5. Hazel's evidence, read from the API

For every run: head SHA and branch from `gh run view --json headSha,headBranch`; the test job's full log from `actions/jobs/{id}/logs`, searched for Kotlin `e:` lines (anchored after the timestamp) and for the compile tasks; the JUnit XML from the `unit-test-report` artifact, tallied by suite with each failure's message. In all six logs both `> Task :app:compileDebugKotlin` and `> Task :app:compileDebugUnitTestKotlin` ran (not `FROM-CACHE` or `UP-TO-DATE`) and there are **zero `e:` lines**. The branches were fetched and each revert's whole edit read as `git diff parent..tip`.

**Fail-first.**
- **40128862012**, `way-home-b2-failfirst` at `571be4d7a`, one commit on `bedb6d809` touching only `WayHomeWatchTest.kt` (that file identical to the head's). `WayHomeWatchTest` only: 11 tests, **2 failures**: the Error test ("the re-route's Error gives the track expected:<TrackOnly(note=NO_ROUTE_FOUND)> but was:<AlongTrails(…" — the old `catch (Exception)` let the `Error` escape, so the first result stayed shown) and the hysteresis test ("48 m again: still off the line, not back until under 45 m"). Both are the tests the fix is for, both failing for the reason the fix addresses.
- **40130073406**, `way-home-b3-failfirst` at `7ff0a197e`, one commit on `63978d6a0` touching only `WayHomeProgressTest.kt` (identical to the head's); `63978d6a0`'s `WayHomeProgress.kt` is `bedb6d809`'s (its diff against `bedb6d809` under `main/` is `WayHomeWatch.kt` alone), so the Amendment 1 rule was what ran. The wayhome package, 6 suites, 59 tests, **5 failures**, each matching the pre-Amendment-3 rule exactly (and the replica): Sorrel's case 244.9996 (the figure from the leg-2 place (120,20)); the double-back "at 200.0 m east … 314.9994 against 320.0" (from the place (190,20)); the allowance case "expected 395.0 but was 364.9994" (from (180,0), the earliest within 25 m); the off-line case "expected 570.0 but was 604.9996" (the old counted hop, 570 + 35); the gate case "expected 570.0 but was 619.8998" (570 + 49.9). The sample includes the three tests that could fail it and the two whose expectations moved with the rule.

**Reverts.** b8 and b9 are cut from `63978d6a0`, whose `WayHomeWatch.kt` and `WayHomeWatchTest.kt` are byte-identical to `77963f8a4`'s (`git diff` empty), so that is fine; b10 is cut from `77963f8a4` itself.

| # | Run | Branch, parent | The whole edit | Tests | Failure and whether this edit produces it |
|---|---|---|---|---|---|
| b8 | 40129453676 | `e50bfa66f` on `63978d6a0` | `WayHomeWatch.kt:180`: `catch (e: Throwable)` → `catch (e: Exception)`, one line | `WayHomeWatchTest`, 11, **1 failed** | the Error test, "expected TrackOnly … but was AlongTrails": the `OutOfMemoryError` escapes the narrower catch and the first result stays shown. **Yes**, only this edit. |
| b9 | 40129460933 | `354706fd7` on `63978d6a0` | `WayHomeWatch.kt:135`: `is OffLine -> HopBand.FAR` → `HopBand.NONE`, one line | `WayHomeWatchTest`, 11, **1 failed** | "48 m again: still off the line, not back until under 45 m": with `NONE` as the previous band, 48 m enters `COUNTED`, not `FAR`. **Yes**, only this edit. |
| b10 | 40130536365 | `fa96081f6` on `77963f8a4` | `WayHomeProgress.kt:139`: `earliest(nodes.indices, REACH) ?: index` → `index`, one line | wayhome package, 6 suites, 59, **3 failed** | Sorrel's case 244.9996 (figure from the leg-2 place (120,20): 570 − 350 + 25); the double-back 314.9994 against 320 (from (190,20): 570 − 280 + 25); the allowance case 364.9994 against 395 (from (180,0)). Each is the figure taken from the needle's place, which is exactly this edit. **Yes.** The gate and off-line cases pass under b10, as Hazel said: with the figure from node 0 at (0,−50) and no hop they give 570 either way; they pin the dropped hop, not the reach. |

None of the three cites a stale result: each log compiled, each run's failures are the tests its edit breaks and no others, and each message carries a figure the edit produces.

**The green run.** 40130538002: `headSha` `77963f8a4a902abcd12d9bfe4cf08a96c002a31a`, branch `way-home-part2-domain`, event `pull_request`, all five jobs `success` (test, lint, release compile, Worker, route cutter). Test job: zero `e:` lines, `BUILD SUCCESSFUL in 16m 53s`. JUnit: **737 suites, 5,522 tests, 0 failures, 0 errors, 24 skipped**; 5,522 is `bedb6d809`'s 5,517 plus the five tests added (two in `WayHomeWatchTest`, three in `WayHomeProgressTest`). Matches Hazel's comment.

## 6. Files

`WayHomeWatch.kt` 234 → 250 lines, `WayHomeProgress.kt` 149 → 167; tests 205 → 231 and 106 → 142. All under 400. `TrackRecordingViewModel.kt`, `AvailabilityViewModel.kt`, `AvailabilityScreen.kt` not in the diff.

## Findings, graded

| ID | Grade | Where | Finding |
|---|---|---|---|
| B-395-2 | **Blocking** (by the brief's rule; see §1.2's last paragraph) | `WayHomeProgress.kt:129, 139, 160` | The 50 m reach is measured to nodes up to 10 m apart, so an admitted fix (within 50 m of the walker) can be outside the reach of every node at or before them: short by up to one node step (≤ 10 m) on the track parts for a fix 40 to 50 m ahead along the line; short by the intervening route (154 m on the PR's own fixture, 190 m with legs 70 m apart) for a fix in a ~0.25 m band at the gate's edge, perpendicular, with a later leg 55 to 100 m away. Remedy: reach = gate + `WAY_HOME_LINE_STEP_METERS`, or measure to segments; owner's word, since -1618 named 50 m. |
| M-395-5 | M, for the owner | `WayHomeProgress.kt:139` (the rule, not a defect) | Wherever the route comes within 50 m of its own earlier part, the figure reads the earlier part: on a forager's trail-and-wander walk the distance home rises from 400 to 630 m over the last 290 m and reads 630 m at the car; sundown will read it in C. Not in -1618's record of what the owner accepted ("50 to 75 m long on an ordinary trail"). |
| L-395-10 | L | PR body, "What", bullet 2; `WayHomeProgressTest.kt:12-16`; `WayHomeProgress.kt:146-153` | Three texts still describe the Amendment 1 figure ("the earliest node within 25 m … plus 25 m, so never short"): the PR body's `WayHomeProgress` bullet, the test class doc, and the allowance constant's doc (its rationale, "the place … can be up to that far", is now the figure's node within 50 m). The function doc at `:115-122` is current. |
| L-395-11 | L | first review §1; RECORD -1617 Observed | My first review's "235 m" for case 1 should have been 245 m (the earliest leg-2 node within 25 m is (120,20), not (110,20)). Finding unaffected. |
| (closed) | — | `WayHomeProgress.kt:139-142` | B-395-1: both first-review cases now read long (485 against 420; 395/495/570 along the double-back); tests at `WayHomeProgressTest.kt:114-134`, red before, bitten by b10. |
| (closed) | — | `WayHomeWatch.kt:180-184` | M-395-2: `Throwable` caught after `CancellationException`, logged once, track shown, job cleared; test red before, bitten by b8. |
| (closed) | — | `WayHomeWatch.kt:88, 133-139, 197-198` | L-395-4: band carried as `FAR` across `OffLine`, last place kept; test red before, bitten by b9. |
| (closed) | — | `WayHomeWatch.kt:188-190`; PR body | L-395-9: the reason is written and the b3 wording matches the edit. |
| (standing) | L | as in the first review | L-395-1, -2, -3, -5, -7, -8: not in Amendment 2's scope; unchanged at this head. L-395-6 remains closed. |

## What I verified, and how

- The head: `git fetch`, `git rev-parse origin/way-home-part2-domain`, `gh pr view --json headRefOid`, all `77963f8a4`.
- The diff and both files whole, read; the ruling, the amendments and the records, read from `origin/records-after-173`.
- Every figure in §1 either by hand on the fixture's metres and, where marked, by the replica validated against the eight CI numbers in the header. The sweep in §1.2 ran in the replica only.
- CI: SHAs, branches, jobs, logs and JUnit XML from the API as described in §5; each revert's edit as `git diff parent..tip` on the fetched branch; the revert bases' files diffed against the head.

## What I could not determine

- Nothing was run with Gradle or on a phone. The replica is not the app; it agrees with the app on every figure the app has printed, which is the strongest check available without a build, and cases (a), (b), (d), (e) and the §1.3 table would each be one test on CI.
- Whether the Kotlin fill really gives six nodes on the 50 m trackBack (§1.2 note); inferred from the same arithmetic, immaterial to the findings.
- How C's "Arrived" and sundown read `remainingMeters` on the §1.3 pattern; C is not in this review.
- The real-world rate of 49.75 to 50 m position errors under canopy; shape 2's frequency is a claim about GPS, not about this code.
- Run 40130538002's lint, Worker and route-cutter artifacts beyond their job conclusions.
