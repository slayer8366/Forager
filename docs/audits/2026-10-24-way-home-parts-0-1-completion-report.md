# Way home parts 0 and 1: completion report (dispatch 2026-10-23-09 Amendment 1)

**Coder:** Hazel (Opus). **Spec:** RECORD -1589; the go is RECORD -1590. **Base:** origin/pre-main `adcbedb19`. Pre-main was checked against the remote before starting, and again before this report; it had not moved.

**Nothing visible changes.** No production code calls the new code yet. That comes in part 2, which ships with step 7.

## What landed

| PR | Branch | Commit | What it does |
|---|---|---|---|
| #388 | `way-home-split` | `7629d5874` | Part 0: a pure-move split of `TrackRecordingViewModel.kt` |
| #389 | `way-home-domain` | `956c133d9` | Part 1, failing first: the tests, against stubs |
| #389 | `way-home-domain` | `425972bbc` | Part 1: the code |

- **Branches and state:** both branches are pushed and their trees are clean. Both PRs are open into pre-main, mergeable and green.
- **Stacking:** #389 is cut from `way-home-split`, so its diff includes part 0 until #388 merges.
- **Merges:** merging waits for Sorrel's review and the owner's word.

## Part 0: what moved, and why

`TrackRecordingViewModel.kt` went from 958 to **852 lines**.

**What moved:**
- **`TrackRecordingLocationFixes.kt`** (new, 140 lines): the live-fix collection and `lastGatedGpsFix`.
  - This is the one self-contained job the ViewModel still ran inline. It follows the pattern the Phase 2.7 split used for the poll and the route tick (-1439).
  - `beginLocationTracking()` stays a ViewModel function, so the ViewModel still decides when the collection starts and stops (-1450).
- **`TrackRecordingRowReads.kt`** (new, 76 lines): the two reads of a track's own row, for take-up and for the resync. Their log lines are unchanged.
  - What follows from each answer stays in `takeUpRunningRecording` and `resyncRecordingState`.
- **The docs that explain the moved code went with it.** The "why breadcrumbs are polled" section went to `TrackRecordingPoller.kt`.

**The file is still over the ~800-line limit.** What is left is mostly the start and stop decision functions: record, take-up, return and resync. RECORD -1450 keeps those in the ViewModel. Getting under 800 would mean moving one of them, and that is the planner's call.

## Part 1: what was built

**The cancel fix (#382 M1), in `routing/BRouterTrailRouter.kt`.** When a route is cancelled, the router:
1. terminates the engine;
2. waits, non-cancellably, until the engine has stopped;
3. only then releases the one-route lock and rethrows the cancellation.

A cancel logs nothing. The blocking search now runs on the router's own single engine thread, so a cancel can be heard even when the caller's dispatcher has only one thread. The engine is still built on the router's dispatcher. The report suggested `invokeOnCancellation`, but that only exists inside `suspendCancellableCoroutine`; this is the public-API equivalent.

**A trail snap behind one interface, in two forms** (`domain/routing/TrailSnapper.kt`, `routing/BRouterTrailSnapper.kt`):
- `SNAP` routes from the point to itself.
- `FULL_ROUTE` routes from the point to a second point.

Both read the first point of the route the engine returns. Before building, I checked what the release jar does when a route's two ends are the same point. It returns no error and one point: the matched point on the trail. This was checked on the laptop JVM, which is not Gradle. So the snap needs only BRouter's public result. BRouter's own single-point modes keep the matched point in protected or local fields, so they could not be used.

**The way home, in `domain/wayhome/`** (no Android):
- `WayHome` and its three parts:
  - the walker's track back to the leave point;
  - the trail route, with straight joints at each end of at most 25 m;
  - the walker's track from the entry point back to the start, then on to the origin waypoint.
- **Choosing the routes:** the region used is the one covering the track's first point (`routeDataForPoint`).
- **Scanning for the trail:**
  - the entry scan runs oldest point first, the leave scan newest first;
  - points are tested every 20 m along the track (provisional);
  - only points inside the region's rectangle are tested;
  - a point counts as on a trail within `WAY_HOME_TRAIL_JOIN_METERS = 25.0` (provisional).
- **Checking the route:**
  - every point of the trail route must lie inside the rectangle;
  - the trail route must be at most `WAY_HOME_DETOUR_RATIO = 1.5` (provisional) times the walker's own track between the same two points.
- **The notes:** five, in -1589's wording.
- **Logging:** every fallback is logged once per call. The log gives the reason in words and never a position.

**Not touched:** the map, the HUD, sundown, the alert rules, the camera, waypoint navigation, the Worker, and PR #386's files.

## Evidence

**Part 0:**
- **Full suite** (#388, run 40062777044): 720 suites, 5,412 tests, 0 failures, 24 skipped. That is identical to pre-main `adcbedb19` (run 40054316928).
- **A green pure move proves little on its own,** so I checked that the suite actually exercises the moved code, filtered to `ui.track.*` and `service.*`:
  - **Check a:** I removed the moved `returnToStart(point)` call (run 40064335384). Two `TrackRecordingViewModelTest` cases failed: "expected:<180.0> but was:<-1.0>".
  - **Check b:** I made `rowSaysEnded` never answer yes (run 40064339092). Two `TrackRecordingServiceTest` stop-from-the-shade cases failed, on their own messages.
  - Both builds compiled with no `e:` lines.

**Part 1, failing first** (run 40063662761, on `956c133d9`):
- The build compiled with no `e:` lines. It ran 48 tests and 30 failed, which is every new test.
- The cancel test failed on "the cancelled route's engine was terminated".
- The other 29 failed on `NotImplementedError` from the stubs.

**Part 1, full suite** (#389, run 40064180614): 724 suites, 5,442 tests, 0 failures, 24 skipped. That is 30 more tests than pre-main.

**Revert checks.** Each was a one-line edit on its own branch, cut from `425972bbc`. Each run's log was checked for `e:` lines before its results were read, and none had any. Every failure is one that its revert could produce:

| # | Edit | Result |
|---|---|---|
| 1 | Delete `engine.terminate()` | The cancel test fails: "the cancelled route's engine was terminated" |
| 2 | Any matched trail counts as near, whatever the distance | 10 failures, all from the join radius: leave and entry points move off the trail, and never-near walks route |
| 3 | No length check | Only the 1.5× test fails: expected `LEAVES_AREA`, got a way home |
| 4 | No rectangle check on the route | Only the outside-the-rectangle test fails |
| 5 | Far end without the track | Only the origin test fails, on its list of points |
| 6 | No fallback log | 14 fallback tests fail on "logged once: [] expected:<1>" |
| 7 | Test points outside the rectangle | Only "the point 3 km north … was not tested" fails |

`git rev-parse` confirms the forward branch is still `425972bbc` after the checks. The revert branches (`way-home-revert-1` to `-7`, `way-home-split-check-a` and `-b`) are left on the remote, not deleted.

**What the tests hold:**
- `BRouterTrailRouterCancelTest`: a real `RoutingEngine` built from the real request, with only its search replaced by a wait that ends on terminate. After the cancel, the engine is terminated, the cancel returns within 2 s, the next real route succeeds, and nothing is logged.
- `BRouterTrailSnapperTest`, on the real engine and fixture. Both methods:
  - put a trail node within 1 m of itself;
  - match the same off-trail point to the same trail point, 34.3 m away;
  - fail a point with nothing in range as `EndOutsideData(START)`;
  - agree with the start of the router's own route.
- `FindWayHomeOnBRouterTest`, on the real engine:
  - a walk out along the fixture's trail, then 200 m west, gives the same `WayHome` by both methods: track back to the turn, trail to the first point, a trail length within ±20% of the distance walked out;
  - a walk never near a trail gives `NEVER_NEAR_TRAIL` by both methods.
- `FindWayHomeUseCaseTest` (21 tests, on fakes):
  - the arithmetic of the parts and joints;
  - the newest leave point wins;
  - 24.9 m counts as on the trail and 25.5 m does not;
  - every region state and every router and snapper failure maps to its note;
  - both area checks, with the 1.5× boundary tested at 749 m and 751 m;
  - one log per fallback.

## Findings for the planner

1. **The split stopped at 852 lines.** See part 0.
2. **`FULL_ROUTE` needs a point it can route to.**
   - The entry scan routes toward the walker's most recent point. If that point is more than 250 m from any way, every `FULL_ROUTE` test fails as `EndOutsideData(END)`, and the result is `NO_ROUTE_FOUND`. `SNAP` has no such dependency.
   - This counts against `FULL_ROUTE` alongside the timing.
3. **"No trail routes here" now covers routes that were never fetched, are being fetched, or are missing.** That is -1589's "missing data", applied as written. But `RegionRouteData.NotFetched`'s own doc says it is "never presented as 'no routes here'" (`domain/routedata/RegionRouteData.kt:23-25`). The owner should confirm which holds.
4. **The length check can fire falsely on a very short track.** If the walker's track between the two points is a few metres, the joints alone (up to 50 m) can make the trail route more than 1.5× longer. The walker would then see "leaves your downloaded area" when it doesn't. Nothing guards against this: no data shows the case, so I didn't build logic for it. It should be watched on a walk.
5. **The figures are provisional and untuned:** 25 m, 1.5×, and the 20 m test spacing.
6. **The router's threading contract changed.** The engine's search now runs on its own thread, not on the dispatcher it is given. The existing threading tests still pass unchanged. Sorrel should look at this.

## Not tested

- **No phone.** The S22 timing of `SNAP` against `FULL_ROUTE` is still to do, and it decides which method ships (-1589). It waits for the planner to hand over the phone. The only timing I have is from the laptop JVM, one warm run each, which is not a measurement: about 4 ms for a snap and 37 ms for a full route.
- **Real tracks.** Nothing here has run on a real recorded track or under canopy, so whether 25 m fits real trail geometry is untested.
- **Cancelling during waypoint matching or file reads.** BRouter does not check the terminate flag there, so a cancel then waits until the next pass of the search. That is not tested.
- **Production wiring.** None of this is wired into the app yet, which is by design. Part 2 brings the entry-point tests through the Return tap.

## Device items (for the S22, when it is handed over)

1. **Time both methods.** Run each one over a recorded off-trail walk inside a downloaded area with routes. Note the total scan time and the number of engine calls for each.
   - If `SNAP` is clearly cheaper, ship `SNAP`.
   - If they are close, the `FULL_ROUTE` limitation in finding 2 still favours `SNAP`.
2. **The rest waits for part 2:** the dotted and solid line, the HUD distance and arrival, as the dispatch lists them.
