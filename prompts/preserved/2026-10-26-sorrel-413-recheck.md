# Re-check of PR #413 after Amendments 2 and 3: fade and grey, the search guard, the retry inside an area, the waypoint figure

Sorrel Tally (reviewer, Fable 5.1), fresh session, 2026-10-26, written 16:30 UTC. For Rowan Waymark.
Read-only on code; no build on the laptop, no device, nothing pushed but this file (branch `sorrel-413-recheck`, off
pre-main `c1deeef85`). Hazel's reports to Rowan were not read; Hazel's S22 PR comment was read only because it is on the PR.

**Scope:** what changed on PR #413 (`waypoint-route-on-map`) since my review of `a00742ffb`
(`prompts/preserved/2026-10-26-sorrel-413-review.md`, `sorrel-413`, `f95f83a53`): `git diff a00742ffb 72126f633`,
five commits: Amendment 2 `0662d533c` (tests over stubs), `ca90dd54b` (the build); Amendment 3 `31040fa2d` (tests over a
stub), `df34f71f8` (the build), `72126f633` (the 50 m counted from the last ask point). Against dispatch
`prompts/preserved/2026-10-26-11.md` Amendments 2 and 3 and RECORD -1670, -1671 (both on `origin/records-after-173`;
the dispatch is not on the PR branch), and my waypoint-figure check (`sorrel-waypoint-figure`, `d15f177d2`).

Every claim is marked **read** (the file at the hash named), **computed** (from a run's artifacts or a command), or
**inferred** (reasoned from code, not seen running). Line numbers are at `72126f633` unless another hash is named; app
paths are under `app/src/main/java/com/zynergylabs/forager/app/`, test paths under `app/src/test/java/com/zynergylabs/forager/app/`.

## 0. Baseline

| Claim | Checked | Result |
|---|---|---|
| PR head `72126f633`, base pre-main, the five commits as named, linear | `gh pr view 413`; `git log a00742ffb..72126f633` | matched: `OPEN`, `MERGEABLE`, `CLEAN`, 34 files +1,942 −51 (computed) |
| pre-main is still `c1deeef85` | `git log -1 origin/pre-main` | matched; **pre-main has not moved since the first review** (computed) |
| The diff since `a00742ffb` | `git diff --stat` | 13 files, +598 −39: 6 main, 7 test (two new test classes) (computed) |
| Tests unchanged between each fail-first and its build | `git diff -- app/src/test` | `0662d533c..ca90dd54b` **empty**; `31040fa2d..df34f71f8` **one file**, `AvailabilityViewModelWaypointSearchGuardTest.kt` +5 −1, the fixture change §3 judges; `df34f71f8..72126f633` **empty** (the lastAsk fix touched `WayToRetry.kt` alone, +11 −2) (computed) |
| The stub commits change nothing visible | `git diff a00742ffb 0662d533c -- app/src/main` | 2 files, +11: `RouteLine.aheadIsOffline` (default false, unread) and `RouteHomeIds.OFFLINE_PROPERTY` (unread); **`AvailabilityWaypointRoute.kt` untouched by the stub**, which matters for §2 (computed). `31040fa2d`'s stub is `WayHomeLine.trailAllowanceMeters` declared and unread (read from its message; the fail-first's 405-for-380 confirms it) |
| The fourteen runs are on the commits named | `gh run view` × 14 | fail-firsts `0662d533c` (40323387147) and `31040fa2d` (40342616649), both `pull_request`; `ca90dd54b` 40330063459 and `df34f71f8` 40345308244 red; forward `72126f633` 40348126871 success; a1-a5 on `waypoint-route-on-map-revert-a1..a5` heads `4fe2cafaf`, `5e8b17459`, `cf11f00fa`, `2414f4513`, `006450302`; r1-r4 on `a3b-revert-r1..r4` heads `023290d3c`, `226d2010a`, `4420a801c`, `8e8a37361` (read) |
| The nine revert commits | `gh api …/commits/<sha>` (the branches are gone from the remote) | each one commit, one file, ±1 line (a2 −1): a1-a5 off `ca90dd54b`, r1-r4 off `72126f633`. Each is on its own branch off the build commit, so no forward change was ever restored from anything (read) |
| All fourteen compiled and ran the test task | each run's build-job log (nine came back empty on the first fetch and were re-fetched through the jobs API before being read) | 0 ` e: ` lines, 0 compile failures in all fourteen; each red run has one `testDebugUnitTest FAILED` and a `N tests completed, M failed` line whose M matches the XML (the log's N omits the 24 skipped: 5,626 + 24 = 5,650; 5,631 + 24 = 5,655); the two green runs have neither, as Gradle prints them only on failure (computed) |

No mismatch.

## 1. Prediction (written to scratch, stamped 16:13Z and refined 16:18Z, before any JUnit XML or build log was read; scored in §8)

Tainted in part: the run list showed conclusions (a4 success, every other revert failure), the commit messages predict
"six failures" for `31040fa2d` and name `df34f71f8`'s red, Amendment 3 says `ca90dd54b` was 2 of 5,650, the brief says
5,655. So counts and pass/fail were known; **which tests and what messages** were predicted, and for a4 **why** it is
green. Written: Amendment 2 fail-first 5 (freshness "a stale fix: faded, with no new fix"; layers "every part marked
grey"; guard "a second search" 1 of 2; `WayToRetryTest` on `LEAVES_AREA: 51 m on`; `RetryTest` 1 of 2); a1 freshness,
a2 the grey mark, a3 the opacity, a4 green because the launch is queued on the unconfined event loop, a5 the two 51 m
cases; Amendment 3 fail-first six as the message says, the entries case failing on `LEAVES_AREA`; `df34f71f8` the one
`:32` case; forward 5,655 = 5,644 + 6 + 5; r1 the outside case, the Amendment 1 `:30` case and the `RetryTest` outside
case; r2 405-for-380 and ~435-for-412; r3 ~435-for-412 alone; r4 `:32` alone.

## 2. Check (1): fade and grey

**The rule and figures are the straight line's** (read). `AvailabilityWaypointRoute.withFreshnessOf`
(`AvailabilityWaypointRoute.kt:239-242`) takes the fix's age through the same `fixFreshness` the straight line uses
(`domain/NavigationReadout.kt:108-115`: 30 s stale, 5 min lost): FRESH → `aheadIsCurrent = true`; STALE → current
false, offline false, which `routeAheadOpacity` draws at `ROUTE_AHEAD_FADED_OPACITY` 0.4 (`RouteHomeLayers.kt:154-157`,
`:96`), the straight line's own faded value (`routeStraightOpacity`, `:164`); LOST, or no fix → `aheadIsOffline = true`,
full opacity. `nextStraightLine` (`NavigationArrival.kt:83-92`) makes the same three cuts at the same thresholds.

**Redraw between fixes, no search** (read). `follow` collects with `collectLatest` (`:104-111`): one `onFix` per fix,
then a `while` loop that sleeps `millisUntilFreshnessChanges` (`NavigationArrival.kt:100-107`, the straight line's own
clock, `AvailabilityWaypointLine.kt:53-62` has the identical loop) and calls `publish()` only. `publish` (`:188-192`)
rewrites `placed` with the freshness as of now and nothing else; no path from a tick reaches `startSearch`. Every
write of the line goes through `publish`, so the `aheadIsCurrent = true` that `show()` still writes at `:180` is
overridden before it reaches the state (`:183-184`). The freshness test pins "no second search for any of it" at its end
(`AvailabilityViewModelWaypointRouteFreshnessTest.kt:74-108`, `router.asked.size == 1`) and compares the two lines at
each moment (`waypointStraightLine.isCurrent` false at 30 s, `.isOffline` true at 5 min).

**Grey at full opacity, casing kept** (read). `aheadCollection` marks every feature `offline` when the line is lost
(`RouteHomeLayers.kt:183`); the three ahead layers that carry colour take `aheadColour` (`:214-218`), a `switchCase` on
`has("offline")` between the passed line's grey and the ahead azure, by day and by night (`:246`, `:252`, `:260`). The
casing (`:239-244`) keeps `palette.casing` and is not in the diff; `updateRouteHomeLayers` sets the same opacity on all
four (`:299-303`), 1.0 when grey. So a grey trail line is the solid grey line inside its casing; the grey straight legs
are exactly the straight line's own offline layer (`:259-264` against `:273-278`: same grey, width, dashes, butt cap).
The way home never sets `aheadIsOffline` (grep: the one writer is `:241` of the collaborator, computed), and the layers
test pins the way home and the track home carry no mark and fade as before (`RouteHomeWayHomeLayersTest.kt:73-96`).
What MapLibre draws from the expression is device-only, as the test's own comment says.

**A fix arriving already lost** (read, with one difference to record). `onFix(fix)` with a fix older than 5 min places
the walker from it (`show`, `:151`) and publishes grey; `onFix(null)` keeps the last `placed` and publishes grey
(`:138-142`). `liveFix` is only ever written with a fix (`AvailabilityLiveLocation.kt:71-77`; `readLastKnownLocation`
writes `lastKnownFix`, not `liveFix`, `:98-101`), so the `null` branch runs only before the first fix, when `placed` is
`null` and nothing is drawn. The difference: the straight line **does not redraw from a lost fix**
(`straightLineToTarget`, `NavigationArrival.kt:53-54`, returns `null` for LOST; `nextStraightLine` keeps the previous
line), while the trail line **places the walker on the lost fix and draws ahead from there**, grey; on the first fix of a
navigation it also searches from that position. Accepted by the owner as "a fix arriving already lost shown grey"
(Amendment 3); whether the owner knew the straight line draws nothing from such a fix I could not tell (the Amendment 2
report is not in the repository). N-413R-1.

**Reverts** (computed, §7): a1 (drop `withFreshnessOf`) fails the freshness test on "a stale fix: faded, with no new
fix"; a2 (drop the mark) fails the layers test on "every part marked grey … was [false, false, false]"; a3 (grey faded)
fails it on "grey at full opacity … expected 1.0 was 0.4". Each is its own edit's message.

## 3. Check (2): the LAZY guard

**What the build does** (read). `startSearch` (`:194-232`) launches `LAZY` (`:199`), assigns `search = job` (`:230`),
then `job.start()` (`:231`). The body cannot run before `start()`, so `search = null` inside it (`:209`) can never be
overwritten by the assignment, whether the body runs in place or is queued. **The hazard is closed by construction on
every path**, including ones this build does not have (read; inferred for paths not exercised).

**Does the guard test bite?** The Amendment 2 fail-first (40323387147, on `0662d533c`) shows the guard test **failing**:
"a second search, not blocked by the first one's finished job expected:<2> but was:<1>". At that commit the collaborator
is `a00742ffb`'s (the stub touched two other files, §0): `liveFixes.collect { … onFix(…) }` and `search = scope.launch {
… search = null … }`. The second search there is `WALKER_FAR_FROM_TRAIL` at 160 m, which `a00742ffb`'s retry would start;
the only way to get 1 is `search != null`, so **the finished job did block the second search: S-413-2 was reachable on
that shape, in this fixture** (computed, inferred). Yet a4 (`2414f4513`, `LAZY` → `DEFAULT`, the assignment still after
`launch`) is **green** (40330228302, 8 of 8). What differs between the two commits is `ca90dd54b`'s other change in the
same file: `collect` → `collectLatest` (`git show ca90dd54b`, `-liveFixes.collect` / `+liveFixes.collectLatest`).

**Why that makes the difference** (read from the pinned library, inferred for the composition). `collectLatest` is
`mapLatest(…).buffer(0).collect()`, a `ChannelFlow`; its collector runs `coroutineScope { emitAll(produceImpl(this)) }`
and `produceImpl` starts the producer with `CoroutineStart.ATOMIC` (`kotlinx-coroutines-core-jvm-1.11.0-sources.jar`,
`commonMain/flow/internal/ChannelFlow.kt:114-120`; the project pins 1.11.0, `gradle/libs.versions.toml:24`; the jar's
SHA-1 matched Maven Central's). ATOMIC is `startCoroutine` → `DispatchedContinuation.resumeWith`
(`CoroutineStart.kt:356-362`, `DispatchedContinuation.kt:188-198`): on a dispatcher whose `isDispatchNeeded` is false,
`executeUnconfined`, which **runs the body inside an unconfined event loop it marks active**, and every later undispatched
launch or resume made while that loop is active is **queued on it, not run in place** (`DispatchedContinuation.kt:302-318`:
"When unconfined loop is active -- dispatch continuation for execution to avoid stack overflow"). `transformLatest`'s
per-value child is `launch(start = UNDISPATCHED)` (`flow/internal/Merge.kt:29`), so `onFix` runs inside that loop;
`scope.launch(DEFAULT)` from there is `startCoroutineCancellable` → `resumeCancellableWith` → `executeUnconfined` → queued
(`:206-216`). The body therefore runs after `search = job` even without `LAZY`, and `search = null` lands last. With the
plain `collect` of `a00742ffb`, the first value was emitted in the `follow` coroutine's own frame, resumed by a real
dispatch from the held lookup, with no unconfined loop active, so the launch ran in place and the hazard bit.

**So:** the relayed reason, "its revert, a4, cannot bite on this build's path" (Amendment 3), is **right**; the mechanism
is the `collectLatest` the fade needed, which moved every `onFix` inside an unconfined loop. I could not read the coder's
own wording (not in the repository or on the PR). Two things follow:

- The guard test's class comment is now **stale about its own path** (`AvailabilityViewModelWaypointSearchGuardTest.kt:42-48`):
  "The case that reaches it is a navigation resumed by a real dispatch, as after the waypoint's lookup returns from the
  database's thread: its first fix's search then starts in place." That was true at `0662d533c` and false from
  `ca90dd54b`, which is why a4 is green. It should say that on this build the collector's `collectLatest` queues the
  launch, so the test can no longer see the hazard and stands as a regression check on the search path (S-413R-1).
- **"Say so in the PR" was not done.** The PR body is the first review's (13 lines, "792 lines", nothing of Amendments 2
  or 3, nothing of a4); the one PR comment is Hazel's S22 check at 12:06Z, before Amendment 2 (read: `gh pr view --json
  body`, `gh api …/issues/413/comments`). S-413R-2.

**Reachability in production** (inferred): for the hazard to bite there, `onFix` would have to run outside an unconfined
loop (a `_uiState.update` from off the main thread, so the collector's resumption is dispatched) **and** `find` would have
to return without suspending; `FindWayToWaypointUseCase` suspends at `listRegions()` on MapLibre's callback (as in my
first review). Neither holds today; `LAZY` closes it regardless.

## 4. Check (3): the retry

**Only inside a downloaded area, the route rectangle** (read). `insideAnArea` (`WayToRetry.kt:73-74`) is
`regions.any { it.isDownloaded && routeAreaFor(it.region).contains(walker) }`: the same predicate `routeDataForPoints`
applies to a point (`FindRouteDataForPoints.kt:28`, `:51`; `routeAreaFor` is the region's bounding rectangle,
`RegionRouteData.kt:177`, `contains` `:190`). The waypoint need not be in it, as Amendment 3 words it ("whose area holds
the walker's fix"). A region listed but not downloaded is not inside (`isDownloaded`), pinned by `WayToRetryTest:139-143`.

**Counted from the last ask point** (read). `lastAsk` (`:52`) starts at `searchedFrom` and moves to `here` whenever
`judge` says yes (`:61`), on the area edge as on the 50 m. In the app the collaborator builds a new `WayToRetry` from each
search's own point (`AvailabilityWaypointRoute.kt:224`, `from`), so `lastAsk` only differs from `searchedFrom` for a
caller that keeps asking one instance, which is what `WayToRetryTest:28-33` does and what `df34f71f8` broke
(40345308244: one failure, `:32` "the next fix inside: no second search", 1,010 m from the first search point; computed).
`72126f633` fixes it in `WayToRetry.kt` alone (+11 −2, computed) and r4 (`lastAsk` → `searchedFrom`) fails exactly that
case and nothing else (computed).

**Never per fix, one at a time** (read). `judge` is pure (no engine, no storage; the rectangle test over the list in
state); a search starts only on its yes, behind `search == null` (`AvailabilityWaypointRoute.kt:148`), and the stray path
says SEARCHING while one runs (unchanged). Pinned: `RetryTest:196-217` ("49 m on: no search", "51 m on: one search, from
there"), the Amendment 1 "one search at a time" (`:112-114`), and the 400 m `WALKER_FAR_FROM_TRAIL` case (`:124`).

**The notes** (read, computed). `WALKING_CAN_CHANGE` (`:77-83`) is the owner's five: `LEAVES_AREA`, `NO_ROUTE_FOUND`,
`NO_TRAIL_ROUTES`, `WALKER_FAR_FROM_TRAIL`, `WAYPOINT_IN_ANOTHER_AREA`. The enum has ten entries
(`WayToWaypoint.kt`): those five, the owner's three excluded (`WAYPOINT_FAR_FROM_TRAIL`, `ROUTES_UNREADABLE`,
`ROUTES_NEED_APP_UPDATE`, pinned not to retry at 300 m either way, `WayToRetryTest:78-84`), and the two Amendment 3
accepted out (`ROUTES_NOT_DOWNLOADED`, `ROUTES_FETCHING`), which the area trigger still covers when the list changes.
A complete partition; the class comment lists all three groups (`:28-37`).

**The Amendment 1 tests unchanged** (computed). `WayToRetryTest.kt:1-42` is byte-identical between `a00742ffb` and
`72126f633`; `AvailabilityViewModelWaypointRetryTest.kt`'s body before the new case is identical apart from one added
import (`TrailRouteFailure`). Both pass on the forward run. The inside-no-retry case moved its note from
`NO_ROUTE_FOUND` to `ROUTES_UNREADABLE` (`WayToRetryTest:49-53`), which the Amendment 2 change required and the comment
records; the claim it pins is unchanged.

**The guard-test fixture change** (read): `offlineMapRepository = regions`, the same `FakeRegions` the use case reads
(`AvailabilityViewModelWaypointSearchGuardTest.kt:74-80`). Without it the ViewModel's list was `mapLayersViewModel`'s
default and the walker was never "inside", so Amendment 3 would have stopped the 160 m retry the case needs. The app's
two readers do read one repository (the first review's §6), the assertions are untouched (diff `ca90dd54b..72126f633`:
fixture lines only), and the test's claim is about the second search being allowed, not about where the region list
comes from. **Acceptable.** It does mean the guard test now also depends on the inside rule; a change there would show
up here first.

**Reverts** (computed, §7): a5 (`WALKER_FAR_FROM_TRAIL` only) fails `LEAVES_AREA: 51 m on` and the `RetryTest` 51 m
case; r1 (drop `insideAnArea`) fails five, all its own: the entries case on `NO_TRAIL_ROUTES: 100 m on, outside`, the
Amendment 1 `:30` "still outside", "leaving the area and walking back in" (the outside fix is 1,000 m from the inside ask),
"a region not downloaded is not inside", and the `RetryTest` outside case (1 expected, 2). The one new case no revert
bites is "inside an area that holds the walker and not the waypoint" (`WayToRetryTest:129-136`): it passed over the
Amendment 3 stub and stays green under r1, as the stub commit's message disclosed. Its claim (51 m yes, 49 m no, inside
an area holding the walker alone) is a conjunction the other cases cover in halves. N-413R-2.

## 5. Check (4): the waypoint figure

- **Allowance 0 on the waypoint line only** (read, computed). `WayHomeLine(wayHome, trailAllowanceMeters = WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS)`
  (`WayHomeProgress.kt:26`); `placeOnWayHome` adds `line.trailAllowanceMeters` on a trail node (`:155`). Two constructions
  in main: the way home's `WayHomeLine(it.wayHome)` (`WayHomeWatch.kt:203`, not in the diff, the default 25 m) and the
  waypoint's `WayHomeLine(result.way, trailAllowanceMeters = 0.0)` (`AvailabilityWaypointRoute.kt:216`). **Nothing else
  reads `trailAllowanceMeters`**: grep over `app/src` finds the declaration, the one reader at `:155`, the waypoint writer,
  three doc mentions and the test's construction (`WayHomeProgressTest.kt:115`).
- **The way home's figure and every way-home test unchanged** (computed). The constant is unchanged (`:224`), its one
  consumer is the default; the test diff touches `WayHomeProgressTest.kt` only to add one case (`:114-120`, +14), and the
  existing 405 assertion at `:105` (`380.0 + WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS`) is unchanged context. No other
  `domain/wayhome` test is in the diff; `WayHomeWatchTest`, `FindWayHomeUseCaseTest` are green on the forward run.
- **The comment** (`WayHomeProgress.kt:215-222`, read) against my S-1: "from about 7 m out, can read short by about half
  their offset (about 10 m short at 20 m beside), which the 25 m covers to about 33 m" matches S-1 (−2.5 at 7.5, −5 at
  10, −10 at 20, −20 at 40; covered to p ≈ 33). "still never short for a walker on the drawn line" is my §3 theorem under
  its two premises; the comment names the lateral premise and **omits the second, the true forward error** (S-1: O2
  reads short from 65 m of error against 80 m with the 25 m, the gate's bound being 50 m on reported accuracy). Small,
  and the owner heard both; N-413R-3. "Nothing but the HUD reads the waypoint's figure (no sundown)" matches -1671 and my
  check. "115 m with 16 m left on the S22 walk's shape, 90 m without" is Juniper's computed W figure
  (`2026-10-26-juniper-waypoint-figure-options.md:43, :56`); my check confirmed 115 and 90 by hand arithmetic (its §4) but
  did not re-derive "16 m left" (W's 40 m leg, "Arrived" at 15 m): read from Juniper, not recomputed. "the S22 walk's
  shape" is a replica modelled on the walk, as both reports say, not the walk's own figures.
- **Tests** (computed): `WayHomeProgressTest:114-120` reads 380 on the waypoint line and 405 on the way home's, same fix;
  `AvailabilityViewModelWaypointRouteTest:137-153` goes through `onNavigateToWaypoint` and reads 412 ± 5 at (205, 0)
  (the fail-first and r2/r3 show 434.9999, i.e. 435, so the 25 m is the whole difference), and "never short of the 355 m
  left". The fail-first fails both (405-for-380, 435-for-412); r2 (constant read in place of the line's) fails both;
  r3 (waypoint line built with the default) fails the ViewModel case alone, as it must, since the unit case builds its
  own 0 line. Each message is its edit's.

## 6. Check (6): the merge

`git merge-tree --write-tree origin/pre-main 72126f633` writes a tree with no conflicts; GitHub `MERGEABLE`, `CLEAN`;
pre-main is `c1deeef85`, the PR's base, unmoved (computed). Sizes: `AvailabilityViewModel.kt` 793 (one argument added on
an existing line, as the commit says), `AvailabilityWaypointRoute.kt` 246, `WayToRetry.kt` 83, `RouteHomeLayers.kt` 337,
`WayHomeProgress.kt` 243 (`wc -l`, computed). Nothing in the diff touches `MapBubbleLayer` or any composable: the pop-up
stays where Amendment 3 put it, on the known-bugs list.

## 7. The runs (computed from the JUnit XML and the build logs; compile in §0)

| Run | Commit | Tests | Failed | Which, and the message | This edit's? |
|---|---|---|---|---|---|
| 40323387147 A2 fail-first | `0662d533c` | **5,650** | **5** | freshness "a stale fix: faded, with no new fix"; layers "every part marked grey … [false, false, false]"; `RetryTest` "51 m on: one search, from there" 1 of 2; **guard "a second search, not blocked by the first one's finished job expected:<2> but was:<1>"** (§3); `WayToRetryTest` "LEAVES_AREA: 51 m on". The moved `ROUTES_UNREADABLE` case passes | yes, each |
| 40330063459 A2 build | `ca90dd54b` | 5,650 | 2 | `WayToRetryTest:30` "still outside"; `RetryTest:106` "no search on a fix outside the area" 1 of 2: the Amendment 1 cases, red by design (Amendment 3's reason) | yes |
| 40330195286 a1 | `4fe2cafaf`: publish drops freshness | 8 | 1 | freshness "a stale fix: faded, with no new fix" | yes |
| 40330203264 a2 | `5e8b17459`: no grey mark | 8 | 1 | layers "every part marked grey … [false, false, false]" | yes |
| 40330214658 a3 | `cf11f00fa`: grey faded | 8 | 1 | layers "grey at full opacity … expected:<1.0> but was:<0.4>" | yes |
| 40330228302 a4 | `2414f4513`: `DEFAULT` for `LAZY` | 8 | **0** | green: the guard test cannot see the hazard on this build's path (§3) | n/a |
| 40330238937 a5 | `006450302`: `WALKER_FAR_FROM_TRAIL` only | 13 | 2 | `WayToRetryTest` "LEAVES_AREA: 51 m on"; `RetryTest` 51 m 1 of 2 | yes |
| 40342616649 A3 fail-first | `31040fa2d` | **5,655** | **6** | `WayToRetryTest:30` "still outside"; "a region not downloaded is not inside"; "outside every downloaded area": "NO_TRAIL_ROUTES: 100 m on, outside"; `RetryTest:106` 1 of 2; VM figure "expected:<412.0> but was:<434.9999…>"; `WayHomeProgressTest` "expected:<380.0> but was:<404.999…>". The "holds the walker and not the waypoint" case passes over the stub, as disclosed | yes, each |
| 40345308244 A3 build | `df34f71f8` | 5,655 | 1 | `WayToRetryTest:32` "the next fix inside: no second search" (the lastAsk gap, fixed by `72126f633`) | yes |
| 40348126871 forward | `72126f633` (`pull_request`) | **5,655** | **0** | 24 skipped (the standing allowlist). 5,655 = 5,644 (`a00742ffb`) + 6 (A2: freshness 1, layers 1, guard 1, `WayToRetryTest` 2, `RetryTest` 1) + 5 (A3: `WayToRetryTest` 3, `WayHomeProgressTest` 1, VM route 1); the A2 fail-first's 5,650 is the intermediate sum | |
| 40348207549 r1 | `023290d3c`: `insideAnArea` dropped | 44 | 5 | entries case "NO_TRAIL_ROUTES: 100 m on, outside"; `:30` "still outside"; "leaving the area and walking back in"; "a region not downloaded is not inside"; `RetryTest:106` 1 of 2 | yes, all five |
| 40348213865 r2 | `226d2010a`: `placeOnWayHome` reads the constant | 44 | 2 | VM figure 435 for 412; `WayHomeProgressTest` 405 for 380 | yes |
| 40348219425 r3 | `4420a801c`: `WayHomeLine(result.way)` | 44 | 1 | VM figure 435 for 412 | yes |
| 40348224418 r4 | `8e8a37361`: `searchedFrom` for `lastAsk` | 44 | 1 | `WayToRetryTest:32` "the next fix inside: no second search" | yes |

Each failure is one its edit produces; none belongs to another edit; every red run's build compiled (§0). The revert
branches are gone from the remote; each commit was read through the API (one file, ±1 line, parent the build commit).

## 8. The prediction, scored

Every run's count and every revert's failing class matched. Three details were not as written: (1) the Amendment 3
entries case fails first on `NO_TRAIL_ROUTES`, the enum's first constant, not `LEAVES_AREA` (I had read the retry set's
order into `WayToWaypointNote.entries`); (2) r1 fails five, not three: "leaving the area and walking back in" and "a
region not downloaded is not inside" are also r1's, which I had not traced (the 1,000 m from the inside ask point and the
`isDownloaded` test both live only in the inside check); (3) **unpredicted and the most useful finding:** the Amendment 2
fail-first shows the guard test biting on `a00742ffb`'s shape, so the a4 green is not "the test never could" but "the
build's own `collectLatest` took the path away", which my prediction had stated as the mechanism without knowing the
fail-first would prove the contrast.

## 9. Verdict

**Blocking:** none.

**Should-fix** (neither holds the merge; for Rowan to place):

- **S-413R-1 The guard test's class comment describes a path this build no longer has**
  (`AvailabilityViewModelWaypointSearchGuardTest.kt:42-48`). It says the held-lookup navigation "starts in place"; from
  `ca90dd54b` the `collectLatest` collector queues the launch on the unconfined event loop, which is exactly why a4 is
  green and why the same test went red on the stub (§3). As written, the next reader will believe the test guards the
  `LAZY` line; it does not, and the fix is to say so in the comment (and that `LAZY` is the construction-level guard). A
  comment edit, no code.
- **S-413R-2 Amendment 3's "say so in the PR" is not done.** The PR body is still the first review's (792 lines, no
  Amendment 2 or 3, no a4) and there is no comment after Hazel's S22 one (read). Amendment 3 asked for the a4 statement
  in the PR; the PR body should also carry the amendments' summary so the merge commit's description matches what is merged.

**Notes:**

- **N-413R-1** A fix that arrives already lost: the trail line places and draws from it, grey, and on a navigation's
  first fix searches from it; the straight line draws nothing from such a fix (keeps its previous line, or none). The
  owner accepted "shown grey"; the asymmetry is recorded here in case the Amendment 2 report did not name it (§2).
- **N-413R-2** `WayToRetryTest:129-136` ("holds the walker and not the waypoint") is bitten by no revert and passed over
  the stub, as the coder disclosed; its halves are covered by the other cases (§4).
- **N-413R-3** The allowance comment names S-1's lateral premise and not its forward-error one (65 m vs 80 m); the owner
  heard both in the check (§5).
- **N-413R-4** "115 m with 16 m left" in that comment is Juniper's W figure, read, not recomputed by me (§5).
- **N-413R-5** The guard test now depends on the inside rule through its fixture; a change to `insideAnArea` would fail
  it first (§4).
- **N-413R-6** Sizes: `AvailabilityViewModel.kt` 793, unchanged; new and changed files under 400 (§6).
- **N-413R-7** Nine build logs were empty on first fetch and re-fetched through the jobs API, as in the first review; the
  counts cited are from the re-fetched logs and the XML, which agree.

**Mergeable:** yes, as it stands, into pre-main `c1deeef85`, which has not moved (`git merge-tree`: a tree, no
conflicts; GitHub `MERGEABLE`, `CLEAN`). Both amendments are built as ruled: the trail line fades and greys by the
straight line's rule, figures and clock, redrawn between fixes with no search, grey at full opacity inside its casing; the
search is `LAZY` and held, closed by construction; the 50 m retry runs only inside a downloaded region's rectangle, from
the last ask point, for the owner's five notes, never per fix, one at a time, with the Amendment 1 tests unchanged; the
waypoint line alone carries allowance 0, the way home and its tests untouched; 5,655 / 0 / 24 on the forward run; nine
reverts and two fail-firsts each failing on its own edit's message on builds that compiled, and the one green revert
explained by the code rather than assumed.

**One-line answers:**
- (1) Fade and grey: the straight line's rule, thresholds and tick; redraw without a search; grey full with casing kept;
  a lost fix shown grey (and placed from, unlike the straight line, N-413R-1); the `offline` property on every feature
  and the three colour layers switched on it (§2).
- (2) The guard: closes the hazard on every path by construction; a4 cannot bite because `collectLatest` queues the
  launch, which the Amendment 2 fail-first proves by contrast; the relayed reason is right, the test's comment is stale
  (S-413R-1), and the PR does not say so (S-413R-2) (§3).
- (3) The retry: inside a downloaded region's route rectangle only, from the last ask point, never per fix, one at a
  time; five notes in, three excluded, two accepted out, ten in all; Amendment 1 tests identical; the fixture change
  acceptable (§4).
- (4) The figure: allowance 0 at the one waypoint construction, the way home's default and tests unchanged, one reader of
  the field; the comment accurate to S-1's lateral trade, silent on the forward-error one (§5).
- (5) Runs: fourteen read, all compiled, each red run on its own edit, 5,655 / 0 / 24 forward, a4 green and explained (§7).
- (6) Clean merge; pre-main unmoved (§6).

## 10. Disclosure

**Read:** every Forager file cited, at `72126f633` or the hash named; the five commit messages; the nine revert patches
via the API; the dispatch's Amendments 2 and 3 and RECORD -1670, -1671 on `records-after-173`; my first review and my
waypoint-figure check; Juniper's report at the lines cited; the PR body and its one comment; the six kotlinx.coroutines
1.11.0 source files named, from the sources jar whose SHA-1 matched Maven Central's. **Computed:** the diff and stat, the
three test-parity diffs, the stub diffs, fourteen runs' metadata, fourteen build logs (nine re-fetched), fourteen JUnit
XML sets (766 suite files per full run), the merge-tree, `wc -l`, the greps for `trailAllowanceMeters`, `WayHomeLine(`,
`WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS`, `aheadIsOffline`/`OFFLINE_PROPERTY`, `liveFix =`, and the enum's entries.
**Inferred:** the composition of the library's dispatch rules into "the launch is queued on this build's path" (§3; the
contrast between the fail-first and a4 is the evidence, the library source the mechanism); the production reachability
of the hazard; what MapLibre draws from the `switchCase`.

**Could not determine:** the coder's own wording of why a4 cannot bite, and the Amendment 2 and 3 reports to Rowan (not
in the repository); whether the owner was told the straight line draws nothing from a lost fix (N-413R-1); anything a
frame or layer shows (device-only).

**Premises that were wrong or need correcting:** the brief's "the comment at WayHomeProgress.kt:220-228" is at
`:215-222` (the doc block is `:206-223`). The brief's "the dispatch prompts/preserved/2026-10-26-11.md" is on
`records-after-173`, not the PR branch. Nothing else in the brief was wrong.

**Decided beyond scope, or left for the owner:** none. No revert check of my own was run (no code pushes, no local
Gradle); the fourteen CI runs' logs and XML were read in its place, with the prediction written first, and the one
unexpected result (a red guard test on the fail-first beside a green a4) was traced to its mechanism in the library's
source rather than left as "the test can't bite".

**What I did not do:** no device, no local build, no reading of Hazel's reports to Rowan, no reading of any parallel review.
