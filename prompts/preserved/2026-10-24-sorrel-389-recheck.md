# Sorrel's re-check of #389 (way-home-domain) after Amendments 3 and 4

**Reviewer:** Sorrel, on Fable 5.1 (claude-fable-5-1), read from the session's own environment. The coder, Hazel, is on Opus.
**Written:** 2026-10-24, 10:59 UTC (`date -u`). Complete, not partial.
**Head re-checked:** `way-home-domain` 8043e2b50, into pre-main 073fc77bd. My first review was at ba205a0d1 (`prompts/preserved/2026-10-24-sorrel-388-389-review.md`); the range read is ba205a0d1..8043e2b50, which is the merge 92811e301 plus the eight Amendment 3 and 4 commits, and the code diff against the merge is five source files plus the report.
**Read first:** CLAUDE.md; dispatch 2026-10-23-09 Amendments 3 and 4; RECORD -1595, -1597, -1598, -1600; Hazel's addendum at the end of `docs/audits/2026-10-24-way-home-parts-0-1-completion-report.md` (8043e2b50).
**Method:** nothing edited, no Gradle run, nothing posted to GitHub, no phone. Every "passes" below is read from the GitHub Actions API, the runs' own job logs, or the `unit-test-report` JUnit XML artifacts, which I downloaded and parsed myself. Every claim about code names a file and line at 8043e2b50 or is marked inferred.

## Verdict

**#389 is merge-ready.** Both M findings from the first review are fixed as ruled and each fix is pinned by a test I have watched fail on a one-line revert, read from that revert run's own XML with a message only that revert could produce. L-389-2 is built as the owner said. The merge of pre-main is byte-identical to a clean automatic merge. CI on the head is green on every job; the two first-attempt reds are the two causes Hazel named, confirmed from the job logs.

| | Blocking | M | L |
|---|---|---|---|
| This re-check | 0 | 0 | 3 (one for the owner before part 2; two nits) |

Nothing in #389 is reachable from production: `FindWayHomeUseCase(` has no caller in `app/src/main` other than its own declaration (`FindWayHomeUseCase.kt:51`, grep at 8043e2b50). So the one L for the owner can be ruled before part 2 wires it, after this merges.

---

## M-389-1: the cancel test now pins the join

**Fixed as specified.** `BRouterTrailRouterCancelTest.kt`: the fake search, once terminated, sleeps `STOPPING_MILLIS` (300 ms, `:94`) and sets `stopped` as its last act (`:64-65`). Straight after `cancelAndJoin()` the test asserts `stopped` is true (`:82`) and that the cancel took at least 300 ms (`:83`), then the pre-existing checks (under 2 s, next route found, nothing logged). Production code unchanged: `engine.terminate()` at `BRouterTrailRouter.kt:174`, `withContext(NonCancellable) { run.join() }` at `:175`.

**Revert way-home-a3-revert-1 (run 40078813878, head 6052ec10a):** `git diff 6ed43928a origin/way-home-a3-revert-1` is exactly one deleted line, `:175`'s join. Run conclusion `failure`; only the "Build, test, publish APK" job failed. Its log: zero `e:` lines, `compileDebugUnitTestKotlin` ran, the filter was `--tests ...BRouterTrailRouterCancelTest`. The JUnit XML: 1 test, 1 failure, message `the cancel waited for the engine to stop before it returned` — the new assertion at `:82`, and only it. That message cannot come from any build with the join present, since the join cannot return before the fake's last line. **Confirmed: the test bites on the join, and on nothing else.**

One note, not a finding: the second new assertion (`:83`, at least 300 ms) is never reached when `:82` fails, so the revert exercises one of the two. Both pin the same property; the first is the sharper one.

## M-389-2 as ruled in -1600, option (b)

**The rule matches the ruling.** `FindWayHomeUseCase.kt:97`: `if (trackBetween > WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS && trailMeters > WAY_HOME_DETOUR_RATIO * trackBetween)`. The constant is 100.0, provisional, named, with the reason and the rejected (c) recorded in its doc (`WayHome.kt:121-133`). The rectangle check is unchanged and runs first (`:87-89`). "Over 100 m" is strict, as -1600 says ("over 100 m").

**(c)'s rule is fully gone.** `git grep` at 8043e2b50 over `app/src` for `WAY_HOME_SHORT_TRAIL`, `SHORT_TRAIL` and `short trail part` finds only one doc-comment sentence in `FindWayHomeShortWalkTest.kt:16` describing the removed rule. `FindWayHomeShortTrailPartTest.kt` is deleted. `TrackOnly(null)` is produced at two places only (`FindWayHomeUseCase.kt:60`, `:78`), the two cases from before Amendment 3, and `WayHomeResult.TrackOnly`'s doc (`WayHome.kt:95-99`) names those two.

**The tests assert real output, and the geometry is right.** I recomputed every figure from `Ground`/`FakeSnapper`/`FakeRouter` (`WayHomeFakes.kt`) on a plane:
- *Crossing* (`FindWayHomeShortWalkTest.kt:48-59`): points 30 m apart so every one is scanned; entry (200, -20), leave (200, 20); `trackBetween` 40 m; route 25 m plus two joints of √(5²+20²) = 20.6 m, trail part 66.23 m, which is over 1.5 × 40 = 60, so without the floor this is exactly the false warning. Asserts `AlongTrails`, trail metres 66.2 ± 0.1, the router's asked pair, and an empty log.
- *Loop* (`:62-80`): 81 points; first point within 25 m of the trail is (100, -10), last is (160, -10); raw track between them 2,040 m; no two non-consecutive points within the self-join ε of 10 m (`TrackSelfJoin.kt:32`), so `trackBetween` is 2,040 m; trail 60 + 10 + 10 = 80 m; back 180, to start 180, total 440. All asserted, to 0.05 to 1 m.
- *98 / 102* (`:91-97`): `detourOf(e)` walks 40 + e + 40 between entry (0,0) and leave (e,0), so 98 and 102 m; the 200 m route is over 1.5× either (147, 153), so the two sides differ only by the floor. Asserts the route's 200 m on one side and `TrackOnly(LEAVES_AREA)` plus exactly one log on the other.
- *Rectangle* (`:100-104`): 98 m walked, one route point 20 km north; asserts `LEAVES_AREA`.

**Revert way-home-a4-revert-1 (run 40082620678, head c256a80f0):** diff against 7e4dd4f31 is one line, `if (true && trailMeters > ...)`. Zero `e:` lines, tests compiled, filter `domain.wayhome.*`, "29 tests completed, 2 failed". XML: `FindWayHomeUseCaseTest` 25/0; `FindWayHomeShortWalkTest` 4 tests, 2 failures: the crossing (`...TrackOnly(note=LEAVES_AREA) (log: [... 66 m, is more than 1.5 times ... 39 m.])`) and the 98 m side (`... 200 m ... 98 m`). The loop and rectangle tests pass, as they must with no floor (80 m against 2,040 m passes the ratio). **Confirmed: the floor is pinned by two tests, with messages the floor's absence produces.**

**Revert way-home-a4-revert-2 (run 40082622304, head 54cc5147d):** diff is one line restoring (c): `if (trailMeters < 100.0) return trackOnly(null, "short trail part")`. Zero `e:` lines, "29 tests completed, 2 failed". XML: the crossing and the loop fail, each `along trails, not TrackOnly(note=null) (log: [... (no note): short trail part.])`. "short trail part" exists only in that revert, so the result is that build's. **Confirmed: the loop test is what separates (b) from (c)**, and it is the one test that cannot separate (b) from no-floor (disclosed by Hazel; a guard, by design).

The Amendment 4 failing-first run 40082554090 (branch `way-home-a4-failfirst`, 65ebfda70) is `failure`; I did not read its XML, since the two revert runs above cover the same tests against both neighbouring rules.

**Is there a new false-warning or lost-shortcut case under (b)?**
- *Lost shortcut:* none. Compared with the pre-Amendment code, (b) only ever skips a check whose outcome was "fall back to the track"; the route is used in a superset of the cases. Compared with (c), the loop case is restored (above).
- *False warning:* the remaining shape is a walker beside a trail within 25 m for over 100 m whose engine route is more than 1.5× their own track between the same points. That needs a route much longer than the trail they walked beside; Hazel reads it as rare and I agree, inferred, not measured.
- *A new case the other way, which I grade L, below:* under (b) a short walked stretch means the engine's route is used with **no length bound at all** except the rectangle. See L-389R-1.

## L-389-2: "Trail routes need an app update · following your track"

**Used exactly when `needsAppUpdate()`.** `FindWayHomeUseCase.kt:140-144`: `Missing` maps to `ROUTES_NEED_APP_UPDATE` iff `state.reason.needsAppUpdate()`, else `ROUTES_NOT_DOWNLOADED`. `needsAppUpdate()` is `FORBIDDEN || NO_APP_KEY` (`RegionRouteData.kt:152-153`) and is the same function the Offline maps row uses (`OfflineRegionRouteLine.kt:34`), whose string is "Map only. Trail routes need an app update." (`:66`). The note text (`WayHome.kt:72`) is "Trail routes need an app update · following your track", the pattern of `:65` and `:75`. `NotFetched` stays `ROUTES_NOT_DOWNLOADED` (`:138`); `Fetching`, `NoneHere`, `Damaged`, `Stored` are unchanged.

**Every other missing reason stays "not downloaded":** `FindWayHomeUseCaseTest.kt:189-198` loops over `RouteDataMissingReason.entries` minus the two (20 reasons declared at `RegionRouteData.kt:69-128`, so 18 tested) and asserts `TrackOnly(ROUTES_NOT_DOWNLOADED)` and one log each. `:201-210` asserts the app-update text for both reasons by string, one log each.

**Revert way-home-a3-revert-4 (run 40078819190, head c8dd86ec0):** diff against 6ed43928a is one line, `if (false)` for `needsAppUpdate()`. XML: `FindWayHomeUseCaseTest` 25 tests, 1 failure, the new test, `NO_APP_KEY expected:<Trail routes n[eed an app update]...> but was:<Trail routes n[ot downloaded]...>`. (Its other class in that run is Amendment 3's since-deleted `FindWayHomeShortTrailPartTest`, 4/0, as it should be at that commit.) Confirmed.

## The merge 92811e301

Parents ba205a0d1 and 073fc77bd, as stated. `git merge-tree --write-tree ba205a0d1 073fc77bd` produces tree 108b4da28, and `git diff 108b4da28 92811e301^{tree}` is empty: **the merge commit is byte-identical to a clean automatic merge**, so nothing was resolved by hand and nothing from either side was dropped. GitHub reports #389 `MERGEABLE`, `CLEAN`, head 8043e2b50, base pre-main.

## CI

Check-runs on 8043e2b50: all five `success`, all from run 40084969261 (attempt 2). The two second-attempt runs, each read from its attempt-1 job list and the failed job's log:
- **40083121709 on 7e4dd4f31, attempt 1:** four jobs `success`, "Android lint (debug)" `failure`. Log: `Warning: An error occurred while preparing SDK package Android SDK Build-Tools 36: Error reading Zip content from a SeekableByteChannel` ... `Caused by: java.util.zip.ZipException: Archive is not a ZIP archive` ... `"Install Android SDK Build-Tools 36 v.36.0.0" failed.` A corrupt SDK download on the runner, before lint ran. Attempt 2 `success`. Confirmed as Hazel described.
- **40084969261 on 8043e2b50, attempt 1:** four jobs `success`, "Build, test, publish APK" `failure`. Log: zero `e:` lines; `MushroomLogViewModelWiringTest > a find deleted through the Journal's ViewModel leaves the Maps tab's records FAILED ... AssertionError at MushroomLogViewModelWiringTest.kt:80`; `5485 tests completed, 1 failed, 24 skipped`. That is row 2 of `docs/process/known-bugs.md` on pre-main (`:26`, the parked intermittent test). The 5,485 total matches Hazel's count. Attempt 2 `success`. Confirmed.

Neither first-attempt failure touches #389's code; neither was retried more than once; no test was skipped or silenced in the diff.

---

## Findings

**L-389R-1. Under (b), a short walked stretch gives the engine's route no length bound at all.** `FindWayHomeUseCase.kt:97`: when `trackBetween` is 100 m or under, the only check left is the rectangle (`:87`). If the two matched points land on ways that connect only far away (a trail passing under a road with no link; two parallel paths), a 40 m crossing can be handed a route of kilometres, and the old code and (c) would both have given the track there. It is not a false warning but its mirror: a long route where the walker's 40 m of track was the answer. The rectangle still bounds it to the downloaded area, nothing in production reaches the code yet, and I have no real walk showing it, so by CLAUDE.md's rule it is reported, not built. For the owner before part 2: a one-line cap for the skipped case (skip the ratio only when `trailMeters` is also under some absolute figure, or under `trackBetween` plus a fixed allowance) would close it at the same provisional level as the other figures. Grade L; owner's call.

**L-389R-2 (nit).** `trackBetween` is the walker's track joined to itself at ε = 10 m (`trackPart` → `joinedTrackRoute`, `FindWayHomeUseCase.kt:92`, `:166-167`; `TrackSelfJoin.kt:32`), not the raw metres walked. The dispatch named `trackBetween` as the quantity, so this is as ruled; I record it because -1600's words are "the walked stretch", and a tight wander whose points fall within 10 m of each other counts as its shortest self-joined path. The same measure the app already uses for the way home, so right in my reading; no change.

**L-389R-3 (nit).** Hazel's Amendment 3 addendum cites `FindWayHomeUseCase.kt:142-147` and `:95-97` for its fixes; at 8043e2b50 the Missing mapping is `:140-144` and the rule is `:97`, because Amendment 4 removed lines. The Amendment 4 section's lines are current. No action; noted so the next reader does not trust the earlier numbers.

## What I did not check

- No Gradle, no phone, nothing run by me. Every result is read from the CI API, the job logs and the JUnit XML artifacts.
- The Amendment 3 failing-first run (40078652887) and Amendment 4 failing-first run (40082554090): conclusions `failure` confirmed, XML not read; the revert runs cover the same assertions against both neighbouring rules.
- Amendment 3's reverts 2 and 3 (`way-home-a3-revert-2/-3`): they pinned the now-removed (c) rule; not read.
- The full-suite XML of 40084969261 attempt 2: the 5,485 count is read from attempt 1's log (`5485 tests completed, 1 failed, 24 skipped`), not recounted from attempt 2's artifact.
- `NOT_RETRYABLE` at `RegionRouteData.kt:155-160` also lists `NO_APP_KEY` and `FORBIDDEN` among others; I did not read what uses it. `needsAppUpdate()` (`:152-153`) is the function the way home and the row both call, which is what L-389-2 asked.
- L-389R-1's likelihood on real trail data: not measured; it is derived from the rule's shape, not from a walk.
- Everything in the first review not touched by Amendments 3 and 4 (the snapper, the scan, FULL_ROUTE, the remaining Lows) stands as reviewed at ba205a0d1; the code diff since the merge touches none of those files.
