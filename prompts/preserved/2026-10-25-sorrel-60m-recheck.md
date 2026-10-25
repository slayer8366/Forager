# Sorrel's re-check of the 60 m reach (dispatch 2026-10-24-10 Amendment 4, RECORD -1620): PRs #394, #395, #396

**Reviewer:** Sorrel, on Fable (claude-fable-5-1). Review only: nothing edited in app code, no Gradle, no phone, nothing posted to GitHub. Scratch under `~/.cache/sorrel-60m/`.

**Heads confirmed from GitHub and `origin`, 2026-10-25 ~02:20 UTC:**
- A, #394 `way-home-cap` → pre-main: `aff033375` (unchanged since my review of 2026-10-24, `prompts/preserved/2026-10-24-sorrel-394-review.md`).
- B, #395 `way-home-part2-domain` → `way-home-cap`: `ac6f6826f` = `99abdb150` (tests) on `77963f8a4` (the head of my #395 re-check) + `ac6f6826f` (the one-line fix).
- C, #396 `way-home-part2-visible` → `way-home-part2-domain`: `679d9c3e8`, a merge with parents `44e28ba9b` (the head of my #396 re-check) and `ac6f6826f`.

**Inputs read:** Amendment 4 of `prompts/preserved/2026-10-24-10.md`; RECORD -1620 and -1625 (and -1621 to -1624 alongside); my `2026-10-24-sorrel-395-recheck.md` (B-395-2, L-395-10); Hazel's evidence comment on #395 (issuecomment 6391833647); the four CI runs' job logs and `unit-test-report` XML, from the Actions API.

## Verdict

**A merge-ready, then B merge-ready, then C merge-ready, in that order.** 0 Blocking, 0 M, 2 L, 1 observation. B-395-2 is closed: the reach is the gate's constant plus the line's step, every B-395-2 shape has a test asserting at least the true distance, each test was red at 50 m for the figure the 50 m rule gives and red again under the one-line revert with the same message, and the replica finds no short placement at 60 m in any of three sweeps (416,152 placements, 0 short). L-395-10 is fixed in all three texts. The merge into C adds nothing beyond B's two commits. The standing conditions from the earlier re-checks are unchanged: C is retargeted to pre-main after B merges (L-396-9), and the distance figure gets its phone re-check on a build carrying this change (-1621) — that is the one thing nothing here can stand in for.

## 1. The reach is the gate plus the step everywhere (check 1)

`WayHomeProgress.kt:168` at `ac6f6826f` (and identically at `679d9c3e8`):

```kotlin
val WAY_HOME_FIGURE_REACH_METERS: Double = LIVE_FIX_MAX_ACCURACY_METERS.toDouble() + WAY_HOME_LINE_STEP_METERS
```

`LIVE_FIX_MAX_ACCURACY_METERS = 50f` (`LiveFixGate.kt:78`), `WAY_HOME_LINE_STEP_METERS = 10.0` (`WayHomeProgress.kt:175`): 60 m. The figure's only search is `:140`, `earliest(nodes.indices, WAY_HOME_FIGURE_REACH_METERS) ?: index`; `git grep WAY_HOME_FIGURE_REACH_METERS` over `app/` at both heads finds that line, the constant, four doc references, and the test's class doc (`:20`) and switchback upper bound (`:54`). Nothing else in the two PRs' trees reads the reach. The remaining "50 m" strings in `WayHomeProgress.kt` (`:115, :120, :162, :164`) are the gate's figure and Amendment 3's history, each stated as such; `WayHomeWatch.kt:131` (the far band's 50/45 hysteresis) and `:243` (the 50 m stray that re-routes) are the hop band and the re-route threshold, not the figure. `PathHome.kt:170`'s `HOP_FAR_ENTER_ABOVE_METERS = 50.0` is the far band. No stray 50 m stands for the reach. `docs/` and `prompts/` on C's tree carry no description of the figure's reach at all (grep for `WAY_HOME_FIGURE_REACH`, "earliest node within 50", "earliest vertex within 50": no hits).

## 2. The B-395-2 shapes are each tested, and the replica sweep at 60 m is clean (check 2)

**Tests** (`WayHomeProgressTest.kt:155-201` at `ac6f6826f`). `assertNeverShort(line, walker, fix, truth)` (`:156-163`) first asserts the fix is within `LIVE_FIX_MAX_ACCURACY_METERS` of the walker (so each case is one the gate admits, by the ruling's own model), then asserts `remainingMeters >= truth`. Four cases, each with its truth re-derived here from the fixture (`WayHomeLines.hairpin`: track (0,−50)→(0,0), trail (0,0)→(200,0)→(200,20)→(0,20), track (0,20)→(0,120), 570 m):

| Test | My case | Walker | Fix | Gate distance | Truth asserted | Correct truth |
|---|---|---|---|---|---|---|
| shape 1, track back (`:166-171`) | (a) | (0,−48.9) | (0,1) | 49.9 | `totalMeters − 1.1` = 568.9 | 570 − 1.1 ✓ |
| shape 1, track to start (`:174-178`) | (b) | (0,61) | (0,110.9) | 49.9 | 120 − 61 = 59 | ✓ |
| shape 2, 20 m hairpin (`:181-186`) | (e) | (101,0) | (105.4,49.8) | 49.99 | 99 + 20 + 200 + 100 = 419 | ✓ |
| shape 2, 70 m hairpin (`:189-200`) | (d) | (105,0) | (105,49.9) | 49.9 | 95 + 70 + 200 + 100 = 465 | 620 − 155 ✓ |

None of the four sits on a float boundary at 60 m: the nearest node at or behind each walker is 51.0, 54.5, 50.09 and 50.15 m from the fix, all well inside 60 and all outside 50, which is why they were red at 50 and are green at 60 (replica figures at 60: 570.0, 63.6, 465.0, 515.0). The allowance case (`:100-106`) moved from (200,0) to (195,0) because (140,0) is 59.99999… m from (200,0), a coin toss; at (195,0) it is 55 m and the next earlier node 65 m, a 5 m margin each side. The switchback case's upper bound (`:54`) now reads the constant. The whole class doc (`:15-24`) describes the place (Amendment 1) and the figure (Amendments 3 and 4) separately, with the 60 m stated as "the live-fix gate's 50 m plus the line's 10 m node step".

**Replica.** The one from my #395 re-check was already gone before the rising-figure review; the rebuilt one (`~/.cache/sorrel-rfo/replica.py`, outside the repo) is what I used, through `~/.cache/sorrel-60m/replica/sweep60.py`. Before the sweep it was re-validated against **13 figures from outside itself**, all to the decimal: the seven from the rising-figure review's header (244.9996, 484.9994, 394.9994, 494.9994, 364.9994, 275, 265) and the five this fail-first run printed (561.6660, 54.5455, 264.9996, 274.9984, 394.9994 — read from the JUnit XML, §4), plus the head's 405.0 at (195,0). Then three sweeps at reach 60, every placement a fix within 50 m of a walker on the line, "short" meaning figure < true remaining:

| Sweep | What | Placements | Short |
|---|---|---|---|
| A, the earlier one re-run | hairpins with legs 20 to 110 m apart, walker at 100 to 109 m east on leg 1, every 3° of bearing, radii 40 to 49.99 m | 96,000 | **0** |
| B, shape 1's family | walker every 0.5 m along the 20 m hairpin, the 70 m hairpin and the S out-and-back; fix 40 to 49.99 m ahead along the line | 20,155 | **0** |
| C, random | five lines (hairpins 10, 20, 70, 100 m; S), walker uniform along the line, fix at a random bearing and radius ≤ 50 m, half of them in the 49 to 50 m shell | 299,997 | **0** |

Sweep A matches the 96,000 / 0 in `2026-10-25-sorrel-rising-figure-options-review.md` §4. Sweeps B and C are new and cover the shape the re-check's sweep did not (the fix ahead along the line, on the track parts), so the 60 m claim no longer rests on the perpendicular family alone. The argument in the constant's doc (`:162-164`) is the reason this cannot fail: the node at or behind the walker is ≤ 10 m behind them along a straight fill, so ≤ 10 m away, so ≤ 60 m from any fix ≤ 50 m from the walker, so some node at or before the truth is always within reach and the earliest within reach is never past it. The sweeps are a check on the fill's arithmetic, not a substitute for that argument.

## 3. L-395-10 is fixed in all three texts (check 3)

- **PR body**, "What", `WayHomeProgress` bullet (read from `gh pr view 395`): separates the place (Amendment 1) from the figure (Amendments 3 and 4), names the reach as `LIVE_FIX_MAX_ACCURACY_METERS + WAY_HOME_LINE_STEP_METERS` (50 + 10 = 60 m) plus 25 m on the trail with no hop added, and states the ordinary over-read (about 60 m on a track part, 85 m on the trail, more where the line winds back, citing -1622). Correct against the code.
- **Test class doc**, `WayHomeProgressTest.kt:15-24`: as above. Correct.
- **Allowance constant's doc**, `WayHomeProgress.kt:147-155`: now says it was ruled when the figure counted from the place, that since Amendments 3 and 4 the reach alone keeps the figure never short, and that the allowance "is now a margin on top", kept because neither amendment removed it. That is the honest description; the earlier rationale ("the place … can be up to that far") is gone. The reach constant's doc (`:158-167`) cites -1620, the owner's words, Amendment 4, the argument, and B-395-2's two figures. The function doc (`:115-123`) and `WayHomePlace`'s doc (`:69-76`) are updated to match. **L-395-10 closed.**

## 4. Hazel's CI evidence, read from the API (check 4)

All four job logs were pulled from `actions/jobs/<id>/logs` and the four `unit-test-report` artifacts unzipped and tallied by a script over every `<testsuite>` (`~/.cache/sorrel-60m/ci/`).

| Run | Branch / ref checked out | Head | `e:` lines | Compile tasks ran | Result (XML) |
|---|---|---|---|---|---|
| 40147115565 fail-first | `way-home-b4-failfirst` | `99abdb150` | 0 | `compileDebugKotlin`, `compileDebugUnitTestKotlin`, then `testDebugUnitTest --tests com.zynergylabs.forager.app.domain.wayhome.*` | 6 suites, 63 tests, **5 failures**, 0 errors; XML stamped 00:38:15Z, after the 00:33Z checkout |
| 40147195968 revert b11 | `way-home-b-revert-b11` | `033b28f6b` | 0 | same three | 6 suites, 63 tests, **5 failures**, 0 errors; stamped 00:39:26Z, after the 00:34Z checkout |
| 40147173906 green B | `refs/remotes/pull/395/merge` (head `ac6f6826f`) | | 0 | full `testDebugUnitTest` | 737 suites, **5,526 tests, 0 failures, 0 errors, 24 skipped** |
| 40147874822 green C | `refs/remotes/pull/396/merge` (head `679d9c3e8`) | | 0 | full `testDebugUnitTest` | 742 suites, **5,538 tests, 0 failures, 0 errors, 24 skipped** |

**Each failure belongs to its own edit.** The fail-first run's five, from the XML:
`54.54545454571678 against 59.0`; `561.6660383666251 against 568.8993716999985`; `expected:<405.0> but was:<394.99937170607063>`; `264.99956019613114 against 419.0`; `274.99835074348346 against 465.0`. Each is exactly the figure the Amendment 3 rule (reach 50) gives on that case — the replica reproduces all five to four decimals from the 50 m rule and none of them from the 60 m rule or the Amendment 1 rule (which gave 244.9996, 364.9994 and 314.9994 on this fixture, §5 of my #395 re-check). The revert run's five are the same five tests with byte-identical messages, which is what a revert of exactly the `+ WAY_HOME_LINE_STEP_METERS` term must produce and nothing else could: `git diff ac6f6826f origin/way-home-b-revert-b11` is one file, one line (`:168`), the term removed, and `033b28f6b` is one commit on `ac6f6826f` ("REVERT CHECK b11, never merge"). The forward line is present at `ac6f6826f:168` and `679d9c3e8:168`. The 63-test sample is the whole wayhome package, which includes the four new tests and the moved allowance case, i.e. every test that could fail these edits.

**Counts against something outside themselves.** 5,526 = 5,522 (the green run on `77963f8a4`, counted in my #395 re-check) + 4 new tests. 5,538 = 5,534 (the green run on `44e28ba9b`, counted in my #396 re-check) + 4. Both add up; the moved allowance case is a change, not an addition, so 4 is the right increment.

## 5. The merge into C changed nothing beyond B's commits (check 5)

`679d9c3e8` has exactly two parents, `44e28ba9b` and `ac6f6826f`; `git merge-base 44e28ba9b ac6f6826f` is `77963f8a4`, so B's two commits are the only commits C gains. `git diff 44e28ba9b 679d9c3e8` touches two files, 97 insertions and 29 deletions — the same two files and counts as `git diff 77963f8a4 ac6f6826f` — and the changed lines of the two diffs, compared line for line after stripping headers, are **identical**. No conflict resolution, no extra hunk, no file outside the two.

## Findings

| ID | Severity | Where | Finding |
|---|---|---|---|
| L-60-1 | L | Hazel's comment, "Green: B: run 40147173906 on `ac6f6826f` (pull_request)" | A `pull_request` run checks out `refs/remotes/pull/395/merge`, the head merged onto its base (`way-home-cap` `aff033375`), not the head commit itself; same for C's run (`pull/396/merge`). The suite therefore ran on the tree the merge would produce, which is the stronger thing to know and the right one to cite, but the comment's wording says "on `ac6f6826f`". No action needed on the code; the report's wording should say "the PR's merge ref". |
| L-60-2 | L | `WayHomeProgress.kt:115` | The function doc still quotes -1618's words ("2: within 50 m (Recommended)") as the ruling behind "the figure counts from its own node", then says the reach is now the gate plus the step per -1620. Both are true and the sequence is explained in the next sentences; a reader skimming the first line may take 50 m as current. Optional: lead with -1620 and keep -1618 as history. Not worth a commit on its own. |
| O-60-3 | Observation | §2 | The never-short property at 60 m rests on the fill putting nodes ≤ 10 m apart along a straight stretch (`WayHomeProgress.kt:47-55`, `ceil(geometric / 10)` steps). The engine's trail part is scaled to `part.meters` along the line (`:52`) while the nodes' geometric spacing is unscaled; the gate distance is geometric, so the argument holds regardless of scale. Noted so the next person does not re-derive it. |

Nothing in this re-check reopens the sequence conditions already recorded (L-396-9's retarget of C to pre-main; the S22 figure re-check after merge), and nothing here grades against M-395-5, which the owner closed by -1623 without a code change.

## What I could not determine

- The figure on a phone. The S22 check (`2026-10-25-hazel-s22-way-home-2-report.md`, per -1624/-1625) ran on `44e28ba9b`, the 50 m reach; no build with the 60 m reach has been walked. -1621 already schedules that re-check.
- Whether the Kotlin fill lands any real engine stretch exactly on a 10 m boundary in a way that differs from the replica's float behaviour. Immaterial to never-short (a node ≤ 10 m behind exists either way), noted as before.
- Nothing was run with Gradle locally; every count and message above is from CI's own logs and XML, and every figure from the replica is beside its hand arithmetic or a CI-printed figure.
