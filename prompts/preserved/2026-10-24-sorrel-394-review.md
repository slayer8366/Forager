# Sorrel's review of #394 (way-home-cap): the 150 m cap when the length check is skipped

**Reviewer:** Sorrel, on Fable 5.1 (`claude-fable-5-1`, read from this session's environment). The coder, Hazel, is on Opus.
**Written:** 2026-10-24, about 15:30 to 15:50 UTC. Complete, not partial.
**Head reviewed:** #394 `way-home-cap` aff033375, into pre-main; base c83a06722 (the branch's merge base with `origin/pre-main` is c83a06722, the PR is two commits on top of it: 4e9da6512 the tests, aff033375 the rule).
**Read first:** CLAUDE.md; dispatch 2026-10-24-10 with Amendment 1 (`prompts/preserved/2026-10-24-10.md` on `records-after-173`); RECORD -1603, -1613, -1614; Hazel's part 2 report, section 5 (`docs/audits/2026-10-24-10-way-home-part-2-report.md` on `way-home-part2-report`); my own `2026-10-24-sorrel-388-389-review.md` and `2026-10-24-sorrel-389-recheck.md` for L-389R-1 and M-389-2.
**Method:** nothing edited, no Gradle run, no phone, nothing posted to GitHub. Every claim names a file and line at aff033375 or is marked inferred. Runs and XML are read from the CI API and the `unit-test-report` artifacts, not from the PR description.

## Verdict

**Merge-ready**, on the owner's word. 0 Blocking, 0 M, 3 L. The rule is as ruled, in the right place, continuous at 100 m, tested on both sides of 150 m and at 150 m itself, logged once with no position, and each revert fails for the reason that edit predicts, on a build whose log has no `e:` line. L-389R-1 is closed.

| | |
|---|---|
| Rule as ruled (-1603, -1613) | yes, `FindWayHomeUseCase.kt:104-107` |
| Order: rectangle → ratio → cap | yes, `:88`, `:98`, `:105` |
| Continuous at 100 m | yes for what is accepted; the **note** differs across the boundary, by ruling (L-394-1) |
| Both sides of 150 m tested | 149 used, 151 refused (`FindWayHomeCapTest.kt:44-55`); exactly 150 used (`FindWayHomeShortWalkTest.kt:95`) |
| Logged once, no position | yes, `:106` via `trackOnly` `:186-189`; the message carries metres only |
| Fail first | 40106265997: 33 tests, 1 failed, the predicted one |
| Revert 1 (cap never applies) | 40106331877: 33 tests, 1 failed, the same assertion |
| Revert 2 (floor condition dropped) | 40106333453: 33 tests, 10 failed, all `TrackOnly(note=null)` where a route over 150 m with over 100 m walked was expected |
| `e:` lines in the three build logs | 0, 0, 0 (counted by me) |
| Full suite on aff033375 | run 40106889588: see "CI" below |

## The rule, read from the source

`FindWayHomeUseCase.kt:104-107` at aff033375:

```kotlin
if (trackBetween <= WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS && trailMeters > WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS) {
    return trackOnly(null, "the trail route, ${trailMeters.toInt()} m, is over the $WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS m cap for ${trackBetween.toInt()} m walked between the trail points")
}
```

- **The ruled words** (-1603: "when the length check is skipped, a route over a set length (provisional) is not used and the track home is shown"; -1613: "150 m, no note (Recommended)"; Amendment 1 §3: "`TrackOnly(null)`, logged once"). The condition `trackBetween <= 100` is exactly the complement of the ratio check's `trackBetween > 100` at `:98`, so "the length check is skipped" and "the cap applies" are the same predicate, with no gap and no overlap. `trailMeters > 150` is "over 150 m", so a trail part of exactly 150 m is used. `trackOnly(null, …)` returns `WayHomeResult.TrackOnly(null)` (`:188`). Confirmed.
- **The figure.** `WayHome.kt:149`: `WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS = WAY_HOME_DETOUR_RATIO * WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS`, i.e. 1.5 × 100.0 = 150.0, derived rather than typed, so it moves if either parent figure is tuned. The doc (`:135-148`) names both records, cites L-389R-1 as the reason, marks it provisional, says why no note, and says which cases stay under it (the 50 to 80 m crossing of M-389-2 and the 80 m looped shortcut). I checked the two figures it cites: the crossing test asserts 66.2 m (`FindWayHomeShortWalkTest.kt:57`), the loop test 80 m (`:76`). Confirmed.
- **Order.** The rectangle check is `:88-90`, the ratio check `:98-103`, the cap `:105-107`, then the route is built `:109`. A route outside the rectangle gets `LEAVES_AREA` before either length rule sees it, as every route does. The cap and the ratio are mutually exclusive by their first conjunct, so their order relative to each other does not matter for the result; the cap sits after, which reads correctly as "when the check above is skipped". Confirmed.
- **Continuity at 100 m.** At `trackBetween` = 100 + δ the ratio check accepts up to 150 + 1.5δ; at 100 the cap accepts up to 150. The set of accepted routes is continuous at the boundary and nothing that is accepted on one side is refused just across it. Confirmed by reading `:98` and `:105` together. What is **not** continuous is the note on a refused route: see L-394-1.
- **Logged once, no position.** Every fallback return in the function is a single `trackOnly` call, and `trackOnly` makes exactly one `errorLog.w` (`:186-189`). The cap's message carries `trailMeters` and `trackBetween` as whole metres and nothing else; no `LatLng`, no index. `WayHomeFallback` (`:192`) wraps the same string. Confirmed. (`$WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS` prints as `150.0`, so the log reads "over the 150.0 m cap"; nit, L-394-3.)
- **Reachability.** Still nothing in production calls `FindWayHomeUseCase` (the PR says so; PR C wires it). I did not re-grep at this head; my recheck of #389 established it at 8043e2b50 and this PR's `app/src/main` diff touches only the two domain files. Inferred from the diff, not re-verified by grep.

## The tests, read from the source

`FindWayHomeCapTest.kt` (new, 70 lines) uses the same synthetic ground as `FindWayHomeShortWalkTest`: a straight trail east from the origin; `detourOf(east)` (`:32-34`) starts on the trail, goes 40 m south, `east` m east, 40 m north back onto it, then away south-east. So entry is index 0 at (0, 0), leave is index 3 at (east, 0), both on the trail, and the engine's route is given with endpoints at those same two points, so the joints are 0 m and `trailMeters` equals the engine's `lengthMetres` exactly (`FindWayHomeUseCase.kt:91-92`). `trackBetween` = 40 + east + 40: **98 m** for `east` = 18, **102 m** for `east` = 22. I checked that the self-join at ε = 10 m (`:172`) cannot shorten it: no two non-adjacent points of the four are within 10 m. These are the same geometry and arithmetic `FindWayHomeShortWalkTest` already used for the floor at Amendment 4.

- `:44-47` **149 m at 98 m walked → used**, asserted on `trail.meters` = 149.0 ± 0.05 and on `logs` being empty (`trailMetersOf`, `:38-42`). Real output, not a proxy.
- `:49-55` **151 m at 98 m walked → `TrackOnly(null)`**, `logs.size == 1`, and the single log message contains "151 m" and "150". Real output; this is the one that fails first.
- `:57-61` **152 m at 102 m walked → used.** 152 > 150 but 152 < 1.5 × 102 = 153, so only the ratio check runs and passes it. This is the test that pins the cap to the skipped case and is what revert 2 trips.
- `:63-69` **a 400 m route with a point 20 km north of a 10 km region → `LEAVES_AREA`, logged once.** Pins that the rectangle check still comes first. Note this test passes under both reverts (the rectangle check is untouched); it bites only if someone moves the cap above `:88`. That is what it is for, and no revert run was built to show it; recorded under "not checked".
- `FindWayHomeShortWalkTest.kt:91-99` the Amendment 4 floor test moved from a 200 m route to **150 m at 98 m walked (used)** and **160 m at 102 m walked (`LEAVES_AREA`)**, because 200 m at 98 m is now capped by design. The 98 m side still discriminates the floor: 150 > 1.5 × 98 = 147, so without the floor it would be `LEAVES_AREA`. And 150 is not over 150, so it is the exact-boundary case for the cap. The comment (`:92-94`) says why. Confirmed.

So: 149 used, 150 used, 151 refused, with 98 m walked; 152 used with 102 m walked. Both sides of the cap and the boundary itself.

## The evidence, read from the CI API

All three runs are `workflow_dispatch` by `slayer8366` with `tests=com.zynergylabs.forager.app.domain.wayhome.*` (the filter reaches Gradle as one `--tests` argument, `ci.yml:180-210` at aff033375; the three job logs each show `./gradlew --stacktrace testDebugUnitTest "${args[@]}"`). The package holds 25 + 4 + 4 = 33 `@Test`s at both 4e9da6512 and aff033375 (counted by `grep -c '@Test'`), which is each run's total, so the sample is the whole package and nothing was filtered away. Each run is attempt 1. Each XML's `timestamp` is after its run's `created_at` (15:17 → 15:21-15:23), so none is a stale artifact.

**Branch shapes**, by `git diff` against aff033375:
- `way-home-cap-failfirst` is 4e9da6512, the PR's first commit (the tests). Its diff from aff033375 removes only the rule (`FindWayHomeUseCase.kt` −6/+1 doc and code lines) and the constant (`WayHome.kt` −16); the four test files are byte-identical.
- `way-home-cap-revert-1` fa8f18068, parent aff033375, one line: `if (false && trackBetween <= … && trailMeters > …)`.
- `way-home-cap-revert-2` 475575b69, parent aff033375, one line: `if (trailMeters > WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS)`.

**Build logs** (`gh run view --job … --log`, saved under `~/.cache/sorrel-394/`): `grep -cE '(^|\s)e: '` is **0** for jobs 120908480959, 120908677332 and 120908683213. Each log ends `33 tests completed, N failed` with N = 1, 1, 10, and `BUILD FAILED` from `:app:testDebugUnitTest`, not from compilation. The other four jobs of each run (lint, release compile, route cutter, Worker) are `success`.

**JUnit XML** (artifact `unit-test-report` of each run, parsed by me):

| run | class | tests | failures | the failure's message |
|---|---|---|---|---|
| fail-first 40106265997 | CapTest | 4 | 1 | `the track home, with no note (log: []) expected:<TrackOnly(note=null)> but was:<AlongTrails(… trail=WayHomePart(kind=TRAIL, … meters=151.0) …` |
| | ShortWalkTest / UseCaseTest | 4 / 25 | 0 / 0 | |
| revert 1 40106331877 | CapTest | 4 | 1 | the same test, the same message (151.0 m route used, nothing logged) |
| | ShortWalkTest / UseCaseTest | 4 / 25 | 0 / 0 | |
| revert 2 40106333453 | CapTest | 4 | 1 | `along trails, not TrackOnly(note=null) (log: [The way home follows the track (no note): the trail route, 152 m, is over the 150.0 m cap for 102 m walked between the trail points.])` |
| | UseCaseTest | 25 | 9 | each `along trails, not TrackOnly(note=null)` |
| | ShortWalkTest | 4 | 0 | |

**Is each failure one this edit could produce?**
- Fail-first and revert 1 both fail exactly and only the 151 m test, with the route used and the log empty: that is a missing cap and nothing else. Specific.
- Revert 2's CapTest failure carries the cap's own log line naming 152 m, the 150.0 m cap and 102 m walked: the cap firing where the walk was over 100 m, which is precisely what dropping the first conjunct does. Specific.
- Revert 2's nine UseCaseTest failures all read `TrackOnly(note=null)` where a route was expected. `TrackOnly(null)` has three producers in the function (`:61` under two points, `:79` leave-scan none-near, `:106` the cap); the first two are untouched by the one-line edit and these nine pass at aff033375 and at 4e9da6512, so the cap is the only cause. That class's `alongTrails` helper does not print the log, so these nine do not name the cap in their own message; the inference is from the diff and the two neighbouring runs, not from the message. I read `:280-288` for the ratio test: 751 m and 749 m routes over 500 m walked, so under revert 2 the 749 m route is capped, as the failure says.
- Revert 2 leaves ShortWalkTest green, as it should: its only route over 150 m is the 160 m at 102 m walked (`:98`), already `LEAVES_AREA` by the ratio check before revert 2 could reach it, and its 150 m route is not over the cap. Consistent.

**Forward change still present.** The reverts are separate commits on separate branches, each with aff033375 as parent, so the forward branch was never edited and the "restore from a saved copy" failure mode of CLAUDE.md does not arise. `sha1sum` of `FindWayHomeUseCase.kt` at aff033375 is `2bedec09ae25e51cf7dd48f75368a812f6a6974b`, matching the PR body's "2bedec09" (git's own blob id is 03339bd6; the PR quotes the file hash, not the blob). The two main-source files: `FindWayHomeUseCase.kt` 192 → 197, `WayHome.kt` 141 → 157, as the PR says; the new test file is 70 lines, under the 400 limit. `TrackRecordingViewModel.kt` and `AvailabilityViewModel.kt` are not in the diff.

## CI

Run 40106889588 on aff033375 (`pull_request`, attempt 1): `success`, all five jobs `success`. The "Build, test, publish APK" job's log has **0** `e:` lines (counted by me) and ends `BUILD SUCCESSFUL in 16m 37s`. Its `unit-test-report` artifact, parsed by me: **5,492 tests, 0 failures, 0 errors, 24 skipped**, with `FindWayHomeUseCaseTest` 25/0, `FindWayHomeShortWalkTest` 4/0, `FindWayHomeCapTest` 4/0 inside it. The count is 5,485 at my #389 recheck plus 7: #389 itself has since merged into pre-main, and this PR adds 4; I did not reconcile the other 3 against pre-main's intervening merges. 24 skipped is the same figure as the #389 run. No first-attempt failure, no retry. GitHub reports the PR `MERGEABLE`, `CLEAN`, head aff033375, base pre-main.

## L-389R-1: closed?

L-389R-1 said: under (b), a walked stretch of 100 m or less leaves the engine's route with no bound but the rectangle, so a 40 m crossing where the matched ways only connect far away could be handed kilometres. At aff033375 every route on that side is now bounded at 150 m (`:105`), the figure is derived from the two it sits between, and the test pins the refusal at 151 m with no note and one log line. The case I described is closed. What remains on that side is a route of up to 150 m for a short crossing, which is the width of the floor's own purpose (M-389-2's 50 to 80 m crossings and the 80 m loop shortcut), and nothing else I named is left over. Closed.

## Findings

**L-394-1. The note is discontinuous at 100 m, by ruling, not by this code.** A 160 m route refused at 100 m walked gives `TrackOnly(null)`, no note (`:105-107`); the same route refused at 101 m walked gives `TrackOnly(LEAVES_AREA)`, "This route leaves your downloaded area. Download a larger area to route there." (`:98-103`). The accept/refuse boundary is continuous; what the walker is told is not. Both halves are owner rulings (-1589's note for the ratio, -1613's "no note" for the cap), and M-389-2 already recorded that the ratio's note can be false on short joints. Nothing to change in #394; recorded so the owner knows the two rulings meet here, and so step 7 or a later tuning pass can decide whether the ratio's note should also be silent near the floor. `FindWayHomeUseCase.kt:98-107`.

**L-394-2. The rectangle-first test has no revert run.** `FindWayHomeCapTest.kt:63-69` asserts the rectangle check still comes before the cap, and it would fail if the cap were moved above `:88`, but no revert built that move, so its bite is read from the code, not from a red run. The three runs cover the two conjuncts of the condition, which is what the dispatch's rule asks; this is an observation, not a gap in what was ruled. `FindWayHomeCapTest.kt:63-69`.

**L-394-3 (nit).** The log reads "over the 150.0 m cap" because the constant is a `Double` and is interpolated raw (`:106`); the ratio's message at `:101` has the same shape ("1.5 times"), so it is consistent, and the test only asserts `contains("150")` (`FindWayHomeCapTest.kt:54`), so `.toInt()` would not break it. No action unless the log wording matters to someone reading it. `FindWayHomeUseCase.kt:106`.

## What I did not check

- No Gradle, no phone, nothing run by me. Every result is read from the CI API, the job logs and the JUnit XML.
- `FindWayHomeUseCase`'s production callers at aff033375: not re-grepped; inferred from the diff touching only the two domain files and from my #389 recheck.
- The nine UseCaseTest failures under revert 2: their specificity is inferred from the diff and the neighbouring green runs, since their message does not carry the log (above).
- Whether the reverts were built "with git plumbing from a saved copy" as the PR says: not checkable from the remote and not needed, since each revert is a separate commit whose parent is the forward commit; I verified that instead.
- The full-suite run's JUnit XML: see "CI" for what I read of it.
- Hazel's report sections other than 5, and her disclosure sections: not re-read for this PR.
- Trail data on a real walk: none; the 150 m figure is provisional and stays untested on the ground until the PR C timing run.
