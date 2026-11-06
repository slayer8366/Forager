# Dispatch 2026-11-06-01, the distance to the next turn: completion report

Hazel Cairnwright, 2026-11-06. RECORD -2183. Dispatch `prompts/preserved/2026-11-06-01-next-turn.md`, Phase 1 report `prompts/preserved/2026-11-06-01-phase1-report.md`, Amendment 1 at records-after-173 6dce60793, all read from origin. Branch `next-turn`, draft PR #500 into pre-main.

The figures in this report are angles, distances along routes, counts and indices. The routes are the public routing fixture's and made-up ways round the made-up point P. No coordinate of anyone's walk appears here.

## Phase 2a

The scope is Amendment 1, H2: the domain only, in new files, with nothing a user sees changing. Phase 2b (the card's space and arrow) is still to come on this branch, after #498 merges.

### What landed

| Commit | What it does |
|---|---|
| `71cf9b936` | Tests first. The domain API in `domain/nextturn/`, every body a stub returning a neutral answer, plus the five test classes and their made-up ground (`TurnGround.kt`). The use cases get a `trailRouteFound` hook that is not called yet. |
| `a8877c8c8` | The implementation: the junctions, the turn rule, forks, bands, the next turn, the empty states, the count, the M1 line and its wiring. |
| `ee7398388` | The winding-through test is moved inside the margin, so only B1's bend threshold keeps it from being a turn (see Evidence). |
| `4faaa9867` | The made-up reader returns only the ways near each read, so the reads along a route are actually tested (see Evidence). |
| `ae1317e86` | The D1 distance tests take their expectation from the line's own nodes. The first version had a wrong premise of mine (see "Premises that were wrong"). |
| `8051a72f4` | pre-main `d02d2e74a` (docs only: CLAUDE.md, the owner's "own window" rule) merged in, not rebased, as the coordinator asked. |
| `09e0d76b8` | This report, as a draft. |
| `d8d2f6734` | B5 tests first, against a stub that counts every way. B5 is the owner's ruling that only walkable ways count as paths passed. |
| `7c1e051aa` | B5: the count passes over `highway=service`, `motorway`, `motorway_link`, `trunk` and `trunk_link`. The ways it leaves out are counted apart, and the M1 line shows them. |
| (this commit) | This report, finished. |

Everything is pushed. The worktree is clean. The code head is `7c1e051aa`; the commits after it are documents only.

### Verification before building

- **Base** [observed]: origin/pre-main was `6cfcd8d6c8c4b45a53f267b2978c867a599c9e19`, as the dispatch said. The branch was cut there. pre-main moved during the work to `d02d2e74a`, a CLAUDE.md-only commit, which is merged in at `8051a72f4`.
- **One coder per file** [observed]: before each edit to an existing file I re-fetched, then compared that file with `git diff --name-only 6cfcd8d6c origin/nav-card-kind-line` (`4d4519459`, then `3a5e66fcc`) and the same for `origin/home-location-out` (`9dab3e3c8`, then `157ad1fe4`). None of `AppContainer.kt`, `FindWayToWaypointUseCase.kt`, `FindWayHomeUseCase.kt` or `WayHomeRouting.kt` is changed on either branch. Every other file of mine is new.
- **Reachability** [read]: `RouteWays` already has a production caller: `AppContainer` hands `BRouterRouteWays` to `StreetDropOffEnds`. The M1 line's path from the user's tap is:
  - **the way to a waypoint:** `AvailabilityWaypointRoute.startSearch`, then the factory's `container.findWayToWaypoint` (`AvailabilityViewModelFactory.kt:70`), then `FindWayToWaypointUseCase`'s new `trailRouteFound`;
  - **the way home:** `WayHomeWatch.launchJob`, then `wayHomeRouting::find` (`AppContainer`), then `FindWayHomeUseCase`'s `trailRouteFound`.
  So the line fires on a real build at the first search, at every re-route and at every Return. **`nextTurn` and `nextTurnSpace` have no production caller.** That is the H2 scope: the card is Phase 2b. Their tests call them directly, with places from the app's own `placeOnWayHome`.
- **The fixture** [read]: `app/src/test/resources/routing/segments-heighted/W75_N40.rd5`, the public Harriman fixture Phase 1 used. Routes on it are found through the use cases' own entry points, with ends taken from the fixture's own route files and `RoutingTestData`. No coordinate was copied.

### What was built

All in `app/src/main/java/com/zynergylabs/forager/app/domain/nextturn/`. Every file is under 200 lines; the largest is `RouteTurns.kt` at 179.

- **The junctions along a route (F1),** `RouteWaysAlong.kt`, `WayBranches.kt` and `RouteTurns.kt`.
  - **The reads.** The trail route's ways are read through the project's own `RouteWays`, in one read round each 1 km stretch of the route. Each read takes in the data cells round it, so every route point's cell is read, and with it every way that ends there.
  - **What is kept.** Only the ways that touch the route, each once. A way's points are matched to the route's by micro-degree key, never by comparing doubles.
  - **The engine.** The vendored BRouter is not touched.
- **What is judged (B1, B2),** `routeTurnsFor` in `RouteTurns.kt`.
  - **Junctions.** Every trail-route point where three or more of the data's ways meet. A point inside one way is two ways, so a bend within a way is never judged.
  - **The two joins.** These are where the line reaches the trail route and where it leaves it: the way to a waypoint's lead-in and final leg, and the way home's two joints. They are judged by the same rule. The ways there are the trail's directions from the engine's matched point (found on the way's stretch, within 1 m) plus the leg.
  - **A join where the trail ends.** That gives two ways, so it is no junction.
- **The turn rule (B1, B3, C1, C2),** `TurnRule.kt`.
  - **R(30°, 30°).** It is a turn when another way is straighter than the route's way, or when the route bends at least 30° and is the straightest by less than 30°.
  - **The measure.** Directions are taken 20 m either side on the line as drawn, legs included. Each other way's direction runs 20 m along it, or to its end. Every way counts, whatever its tags.
  - **Forks (B3, the planner's reading).** A fork is where the route is not turning and another way leaves within 45° of the route's way out. A branch to the right makes a keep left, and to the left a keep right. Where branches lie within 45° on both sides, the nearer one decides.
  - **Bands (C1).** Slight below 45°, plain from 45° to below 135°, sharp from 135°, with no U-turn band.
  - **Dog-legs (B4).** Each junction is judged on its own, and nothing merges two of them.
- **The unknown cases are their own classes.**
  - **`UNMATCHED`.** The route's way in or out is not among the data's branches at a junction, or an interior route point is not in the data.
  - **`NO_LEG`.** There is no line before or after a point to take a direction from: a lead-in of no length, when the walker stands on the trail, or a final leg of no length.
- **The next turn (D1, E1, E2 and the owner's count),** `NextTurnAhead.kt` and `NextTurn.kt`.
  - **The result.** `nextTurn(way, progress, current)` returns one of `Turn(band, side, metersAway, sameSidePathsBefore)`, `Fork(keep, metersAway)`, `NoTurnLeft` or `Blank(reason)`.
  - **The distance (D1).** `metersAway` is the line's metres from the needle's own place (`aimFrom`, the function the needle uses) to the turn's node, held at 0 or above.
  - **Passing (D1).** A turn is passed only when the walker's place (`placeOnWayHome`) is past its node.
  - **Unknown is never "no turn left".** An `UNMATCHED` junction before the next turn blanks the space until it is passed. `NoTurnLeft` is returned only when every junction ahead is known.
  - **The way home's own track (E2, the planner's reading).** When the way home's last part is the walker's own track, with length, there is no known turn beyond the last joint, so it is `Blank(TRACK_TURNS_UNKNOWN)`. That holds once the walker is past the last joint, and before it if no turn is left. On the track back, the next turn is the coming joint, as the planner's reading has it.
  - **The count.** `sameSidePathsBefore` counts the paths leaving on the turn's side at the straight-on junctions from the walker's place to the turn. The coordinator's message gave the owner's ruling, "When paths come first (Recommended)". It is given for every band; which bands' words use it is Phase 2b's.
  - **Walkable ways only (B5).** The owner chose "Walkable ways only (Recommended)": "Footpaths, trails, tracks and streets count. Driveways, parking-lot lanes and motorway slips don't, so a driveway never makes it "the second left"." Under the planner's reading of the tags (`PathsPassed.kt`), every way the route data knows counts except `highway=service`, `motorway`, `motorway_link`, `trunk` and `trunk_link`. The route data's own `lookups.dat` holds each of these as its own `highway` value, so the tag alone tells them apart. The ways left out are counted apart on each side (`leftNotPaths`, `rightNotPaths`). The turn rule still counts every way, and a test pins that.
  - **The card's states (E1).** `nextTurnSpace(card, ahead)` blanks the space for every card state but a figure: Arrived, the dash, no fix, within the fix's circle. Phase 2b says which state the card is in, from its own readout.
  - **The way's states.** A way being worked out, a way with no trail route, junctions still being read, an unread read and a walker not yet placed each have their own reason.
- **The M1 line,** `RouteTurnsLog.kt` and `RouteTurnsLogLine.kt`. It gives the count and, beside it, the side ways left out of it (B5).
  - **When.** One info line, tag `NextTurn`, for every trail route the way to a waypoint and the way home find.
  - **Off the caller's thread.** It is written from a scope `AppContainer` holds, so the route shows when it is found, exactly as before.
  - **Failures.** A read that fails writes an "unread" line and a warning. A reader that throws is warned of. Throwable is caught, as the way home's own job does (M-395-2).
- **The four existing files.**
  - `FindWayToWaypointUseCase` and `FindWayHomeUseCase` take an optional `trailRouteFound(way, routeData)` hook. Each calls it once, just before it returns a trail route, and never for a straight line or the track.
  - `WayHomeRouting` passes the hook on.
  - `AppContainer` builds the `RouteTurnsLog` with its own `BRouterRouteWays` and `Log.i`.
  - Nothing else in those files changed.

**Not touched:** every UI file, including `NavigationReadout`, the card and the HUD. Also the vendored BRouter, `RouteWays.kt` and `BRouterRouteWays.kt`, `WayHomeLine`, `WayHomeWatch`, `AvailabilityWaypointRoute`, `ci.yml`, `docs/audits/README.md` and RECORD.md.

### What the M1 line looks like

The format test pins this string exactly (`RouteTurnsLogTest`, made-up junctions):

```
M1 next turns, way to a waypoint: ways read in 12 ms (2 reads, 9 ways); 7 judged: 2 turns, 1 fork, 2 straight on, 1 no leg, 1 unmatched. #1 node 3, 31 m, join (start) of 3 ways: NO_LEG | #2 node 12, 119 m, junction of 4 ways: turn 4°, margin 86°, STRAIGHT_ON, paths 1 left 0 right, not paths 1 left 0 right | #3 node 20, 201 m, junction of 4 ways: turn -2°, margin 88°, STRAIGHT_ON, paths 1 left 1 right, not paths 0 left 0 right | #4 node 26, 260 m, junction of 4 ways: turn -90°, margin -90°, TURN, plain left, 2 same-side paths before, 1 same-side way not counted | #5 node 31, 310 m, junction of 3 ways: turn 6°, margin 30°, FORK, keep right | #6 node 40, 400 m, junction of 3 ways: UNMATCHED | #7 node 44, 440 m, join (end) of 3 ways: turn 151°, margin -151°, TURN, sharp right, 0 same-side paths before, 0 same-side ways not counted
```

The parts of the line:
- **The header:** the read's time in ms, the number of reads and the ways kept (M2), then each class's count.
- **Each judged point:**
  - `#n` is its order along the route, and `node` is its index on the walker's line;
  - the metres along the line from its first point;
  - `junction`, `join (start)` or `join (end)`, and how many ways meet there;
  - the route's own turn, signed with right positive;
  - the margin: how much straighter the route is than the straightest other way, below 0 when another way is straighter;
  - the class, and the band and side or the fork's keep;
  - for a straight-on junction, the paths it adds on each side, and the ways there that are not paths (B5);
  - for a turn, the same-side paths since the last turn, fork or unmatched junction, with the same-side ways left out of the count. That is the count TalkBack would read as the turn becomes the next one.

Every figure is a whole number, so no coordinate can appear. Two tests check for the absence of any decimal: one on the line from made-up ways, and one on the fixture's real route. A route too long for one log message is split into numbered parts, "(1 of 3)", each under 3,500 characters, and no point is lost; a test pins this. An unread route reads `M1 next turns, way home: ways unread (IOException) after 5 ms (1 read).`, with the failure's class and never its message, since that can name a route square.

### Evidence

**The tests,** 62 in five classes before B5, and 68 in six with it (B5's, below):
- `TurnRuleTest` (17): B1's two clauses and both thresholds, B3, C1's edges and C2.
- `RouteTurnsTest` (15), on made-up ways round P through `routeTurnsFor`: B1 at real geometry, the B2 joins (between a way's points, a trail's end, shallow, no length, the way home's joints), B4, unmatched, unread, and F1's reads and timing.
- `NextTurnAheadTest` (18): D1, E1, E2, the count, unknowns and forks. The walker is placed fix by fix through the app's own `placeOnWayHome`.
- `RouteTurnsLogTest` (7): the M1 format, its parts, unread, a throwing reader and no decimals.
- `RouteTurnsOnBRouterTest` (5), on the real vendored engine and the fixture with heights, through `FindWayToWaypointUseCase` and `WayHomeRouting`, the real entry points:
  - the hook fires once per trail route, with the region's own route data, and never for a straight line;
  - the M1 line follows, once per route and again on a second search;
  - **F1:** the reads along the route find exactly as many junctions as one read of the whole area counts by way ends (12 on that route).

**Red first** [observed], run 42222073533 at `71cf9b936`, filtered to the new classes. The build log had 0 Kotlin compile errors (`e: file://`).
- **53 of the 62 were red**, each with a message naming its rule's missing behaviour. Examples: `expected:<TURN> but was:<STRAIGHT_ON>`, `a turn, not NoTurnLeft`, `expected:<Blank(reason=NO_TRAIL_ROUTE)> but was:<NoTurnLeft>`, `junctions judged against junctions in the data expected:<12> but was:<0>`.
- **9 were green on the stubs:** the negatives, whose claim is "not a turn", "no junction" or "nothing logged". By CLAUDE.md's rule ("a check that passes identically before and after a code change is suspect") each was given a revert that turns it red, below, except one absence claim, listed under "Not tested".

**Green** [observed]:
- **Run 42225034423 at `ee7398388`, filtered:** 0 compile errors, 61 of 62. The one red was my D1 test's wrong premise, `expected:<150.0> but was:<151.92307692282554>`, fixed at `ae1317e86` (see "Premises that were wrong").
- **The Street-map-deleted job, which runs every unit test:** at `a8877c8c8` (on the probe run 42223206418) it ran 6,869 tests with 1 failure, that same test. At `ee7398388` it ran 6,868 with the same one.

**The full CI on the PR,** every job and every unit test, compared by name.
- **Run 42227417395 at `8051a72f4`, before B5:** 8 of 8 jobs green, 6,898 tests, 0 failed, 24 skipped.
- **Run 42234175132 at `7c1e051aa`, the code head:** 8 of 8 jobs green, 0 compile errors. 6,904 tests: 6,880 passed, 24 skipped, 0 failed. The Street-map-deleted job's own suite ran 6,874 tests, with 0 failed.
- **Against pre-main.** The baselines are run 42202397416 at `6cfcd8d6c` (6,836 tests: 6,812 passed, 24 skipped) and run 42225728551 at `d02d2e74a` (the same 6,836). No test of pre-main's is missing at the head, and none changed status. The 24 skipped are the same 24. The 68 new tests are exactly the six new classes.

**Revert checks** [observed]. How each was run:
- **The edit.** One line per check, with the predicted failures written down before the run (`/mnt/work/scratch/next-turn-2a/scripts/reverts.py`, deleted with the scratch).
- **The run.** Committed to its own throwaway branch from the forward commit (`8051a72f4` for r01 to r20, `7c1e051aa` for r21 to r24 and r17b), and run on CI on the new classes.
- **The restore.** The file was restored from the copy saved before the edit, never from git. The tree was then checked equal to the forward commit before HEAD moved back.
- **Reading the results.** Each run's build log was checked for compile errors before its JUnit XML was read.
- **Afterwards.** The forward change was confirmed present in the branch worktree (`git diff --quiet origin/next-turn`, and the edited B1 and B5 lines counted).

Reverts r01 to r20 were run from `8051a72f4`. The lines they edit are unchanged at the code head `7c1e051aa`. B5 moved the side counting below them, so r17, the count's own revert, was run again from `7c1e051aa` as r17b, beside B5's four.

| Revert | Rule | The one-line edit | Run | Failed / predicted | Verdict | A failure message |
|---|---|---|---|---|---|---|
| r01 | B1 | drop "bends at least 30° and is the straightest by less than 30°" | 42227482140 | 6 / 6 | match | `expected:<TURN> but was:<STRAIGHT_ON>` (T on its stem, wide Y, a perpendicular lead-in, the way home's first joint) |
| r02 | B1 | margin `< 30` to `<= 30` | 42227551381 | 1 / 1 | match | `expected:<STRAIGHT_ON> but was:<TURN>` (straightest by exactly 30°) |
| r03 | B1 | bend threshold S to 0° | 42227568942 | 3 / 3 | match | `expected:<STRAIGHT_ON> but was:<TURN>` (winding through) |
| r04 | B1 | a junction is 2 or more ways, not 3 | 42227587595 | 17 / 14 | see below | `junctions judged against junctions in the data expected:<12> but was:<224>` |
| r05 | B2 | judge junctions only, not the joins | 42227604468 | 6 / 6 | match | `a turn, not Blank(reason=TRACK_TURNS_UNKNOWN)` |
| r06 | B3 | no forks | 42227621367 | 5 / 5 | match | `a fork, not NoTurnLeft` |
| r07 | B3 | fork within 60° | 42227638766 | 1 / 1 | match | `expected:<STRAIGHT_ON> but was:<FORK>` |
| r08 | C1 | slight up to and including 45° | 42227666493 | 1 / 1 | match | `expected:<PLAIN> but was:<SLIGHT>` |
| r09 | C2 | keep sides swapped | 42227681938 | 2 / 2 | match | `expected:<LEFT> but was:<RIGHT>` |
| r10 | D1 | distance from the walker's place, not the needle's | 42227698105 | 3 / 3 | match | `expected:<0.0> but was:<19.230769230800888>` |
| r11 | D1 | no hold at 0 | 42227716706 | 1 / 1 | match | `expected:<0.0> but was:<-9.999681951291677>` |
| r12 | D1 | passed when the needle passes the node | 42227732855 | 2 / 2 | match | `a turn, not NoTurnLeft` |
| r13 | E1 | "Arrived" passes the turn through | 42227750187 | 1 / 1 | match | `ARRIVED expected:<Blank(reason=ARRIVED)> but was:<Turn(…)>` |
| r14 | E2 | no track ahead on the way home | 42227766899 | 2 / 2 | match | `expected:<Blank(reason=TRACK_TURNS_UNKNOWN)> but was:<NoTurnLeft>` |
| r15 | E2 | any last part with length is "unknown turns" | 42227787134 | 4 / 4 | match | `expected:<NoTurnLeft> but was:<Blank(reason=TRACK_TURNS_UNKNOWN)>` |
| r16 | A3 | never "no turn left" | 42227804870 | 4 / 4 | match | as r15 |
| r17 | count | both sides counted | 42227823802 | 3 / 3 | match | `expected:<1> but was:<2>` |
| r18 | M1 | the way to a waypoint's hook not called | 42227841836 | 2 / 2 | match | `expected:<2> but was:<0>` |
| r19 | M1 | the way home's hook not called | 42227857735 | 1 / 1 | match | `expected:<[(WayHome(…` (nothing reported) |
| r20 | F1 | one read at the route's start | 42227873902 | 2 / 1 | see below | `the junction 3 km on is found expected:<TURN> but was:<UNMATCHED>` |
| r21 | B5 | every way's tags count as a path | 42234427396 | 3 / 3 | match | `the footpath alone: "take the second left" expected:<1> but was:<2>` |
| r22 | B5 | the junction counts every way on each side | 42234447826 | 3 / 3 | match | as r21 |
| r23 | B5 | B1 decides on paths only | 42234466525 | 2 / 1 | see below | `expected:<TURN> but was:<STRAIGHT_ON>` (a driveway straight on) |
| r24 | B5 | `footway` added to the ways that are not paths | 42237526676 | 3 / 3 | match | `the footpath alone: "take the second left" expected:<1> but was:<0>` |
| r17b | count | both sides counted, again at `7c1e051aa` | 42234485766 | 4 / 4 | match | `the slip is not a path: "turn right" expected:<0> but was:<1>` |

**The three that failed more than predicted.** Each extra failure is one this edit produces, not a stale run: each build log has no compile error, and each message names this edit's effect.
- **r23.** Its prediction held, and B1's own "every way in the data counts, whatever its tags" failed too (`the private way straight on makes the route's right a turn expected:<TURN> but was:<STRAIGHT_ON>`). That test's way straight on is `highway=service`, which r23 drops from the turn rule. I missed it when predicting: it is the same claim as B5's B1 case, made earlier.
- **r04.** All 14 predicted failed, and with them the fixture's 3 M1 tests (`expected:<2> but was:<14>`, `List has more than one element`). Judging every two-way point puts 224 points on the fixture route's line. That splits it into numbered parts, and those tests count messages.
- **r20.** Its prediction held. The fixture's "logs again" test also failed (`expected:<2> but was:<4>`): one read round the reversed route's start leaves that route's far junctions unread, so its line grows and splits. The fixture's F1 test passed under r20, as predicted, because the forward route's cells all lie within one read. So that test guards junction coverage, not the reads along the route; the made-up 3 km test guards those.

Every one of the 25 runs:
- had 0 Kotlin compile errors in its build log;
- ran 62 or 68 tests, the new classes only, from its own run's artifact;
- where it was still running once its build-and-test job had finished, was cancelled, to free runners from the Street-map-deleted job's full suite, which a revert does not need.

**The same-side count, on the fixture** [observed]. This was the coordinator's question: whether the junction data can support the count.
- **The probe.** A throwaway probe (run 42223206418, branch deleted) rebuilt Phase 1's 200 routes with no coordinate written down: the 8 fixture routes' ends, then 192 random pairs of trail graph nodes inside those routes' box, seed 20261106. They went through the app's own way-to use case, and 195 were found. Counts are per pass over 894.7 km of trail route.
- **Junction passes:** 3,501, of which 1,616 turns, 186 forks and 1,699 straight on. That is 1.81 turns per km, against Phase 1's 1.83.
- **Side paths at straight-on junctions:** 2,016. Their sides as the probe measured them disagreed with the domain's own count at 0 junctions.
- **How each side path ends,** followed through two-way nodes:
  - 1,236 reach a junction;
  - 622 run on past 300 m;
  - 158 end at a dead end, 70 of them within 50 m (53 service ways, 15 paths, 2 tracks).
- **Ways shorter than 20 m to their far node:** 310 of the 2,016. Most lead on.
- **The count on plain turns,** of 1,220: 0 for 797, 1 for 283, 2 for 103, 3 for 22, 4 or more for 15.
- **Dead ends taken out:** dropping those within 50 m would change the count on 3 of the 1,220. Dropping those within 20 m would change 2.
- **So the data supports the count** [inferred from those figures]. The meaning of "paths" was then put to the owner, who ruled B5.

**B5, walkable ways only** [observed]:
- **The tests:** 6 more:
  - `PathsPassedTest` (2): each excluded tag, and the counted ones;
  - `TurnRuleTest` (2): the not-paths counted apart, and B1 still counting every way;
  - `RouteTurnsTest` (1): a driveway at a junction passed straight through;
  - `NextTurnAheadTest` (1): "take the second left" past a driveway and a motorway slip.
  The M1 format test's pinned string gained the not-counted figures.
- **Red first:** PR run 42230249460 at `d8d2f6734`, the stub counting every way. The build-and-test job ran 6,904 tests with 0 compile errors in its log. 5 were red, each for its own reason, for example `the footpath alone: "take the second left" expected:<1> but was:<2>` and `expected:<0> but was:<2>`. The two negatives passed on the stub: B1 still counting every way, and the walkable values counted. Reverts r23 and r24 turn them red. The next push cancelled the rest of that run.
- **The probe again,** at `7c1e051aa` (run 42230408805, branch deleted), on the same 195 routes:
  - **The ways left out:** 235 of the 2,016 side ways at straight-on junctions (service 192, motorway 38, trunk 3, motorway_link 2).
  - **The domain's own figures:** the walkable and not-path counts it now gives disagreed with the probe's at 0 junctions.
  - **Plain-turn counts, every way:** 0 for 798, 1 for 282, 2 for 103, 3 for 22, 4 or more for 15.
  - **Plain-turn counts, walkable only:** 0 for 835, 1 for 260, 2 for 90, 3 for 20, 4 or more for 15.
  - **The shift:** 44 of the 1,220 plain turns change, 36 down by one and 8 by two or more. 37 of them go to 0, so the turn reads "turn left" where it read "take the second left".
  - **Why "every way" here is not the first probe's figure:** it differs by one turn (798 against 797 at 0). The first probe counted 2 side ways at joins as paths; the second counts 0 [observed], so both are ways B5 leaves out [inferred; their tags were not printed].

**Privacy** [observed]: the diff, the probe's output and the PR body were checked against the private home patterns, with 0 matches.

### Conventions

- **The info line through an injected `info` lambda,** `Log.i` in `AppContainer`. This follows `FindWayToWaypointUseCase`'s own line naming what each end became (dispatch 2026-11-05-07). Followed.
- **Work outliving the Activity, in an `AppContainer`-held `SupervisorJob` scope,** with Throwable caught but not cancellation. This follows `WayHomeWatch`'s job (Sorrel's M-395-2). Followed.
- **Failures as distinct sealed results, never a plausible stand-in.** This follows `TrailRouteResult`, `WayHomeResult` and `WayToWaypointResult`. Followed.
- **A warning through `ErrorLog` with an `Exception` subclass naming the reason.** This follows `WayHomeFallback` and `WayToWaypointFallback`. Followed.
- **Made-up test ground in metres east and north of a base point.** This follows `Ground` in `WayHomeFakes.kt`. Followed, with P as the base as the dispatch asks.
- **UI prior art:** none checked. Nothing a user sees is built in 2a.

### Confirmed and inferred

- **[read]**
  - The callers of the M1 path (Verification above).
  - `aimFrom` and `placeOnWayHome` are the needle's and the place's own functions.
  - `WayHomeLine` keeps every part point as a node, which the node mapping relies on and checks: a point missing from the line makes the route unread rather than guessed.
- **[observed]**
  - Every run, count and message above.
  - The fixture route's junction count matching a whole-area read (12).
  - The probe's figures.
- **[inferred]**
  - That the data supports the count: from the probe's figures.
  - That reads of 1 km round stretches of a route cover every junction's ways. This rests on BRouter keeping a way at both its ends, and on a read returning whole cells. On the fixture it held: the F1 test's count matched the whole-area read.
- **[not verified]**
  - Anything on the S22: the line firing on a device, its read time (M2), and the thresholds on the owner's routes.

### Could not determine

- **How R(30°, 30°), the fork's 45° and the count fare on the owner's own routes.** This is M1's purpose; see the S22 check below.
- **How long the reads take on the phone (M2), and their memory.** Not measured anywhere: the tests assert the timing's arithmetic on a made-up clock, not a duration, and no CI run printed the line. The figure is in the log line's header.
- **Whether BRouter's matched route ends always lie within 1 m of a way of the data.** The joins assume so. On the fixture, every join found its way: the probe's 195 routes gave 0 unmatched joins, and 0 unmatched junctions in 3,501 passes (Phase 1 saw 1 in 3,702). Of 364 joins there, 355 were `NO_LEG`, because the fixture's route ends are trail nodes, so their legs have no length.

### Premises that were wrong

1. **My own first D1 test,** fixed at `ae1317e86`. It assumed the walker's line has a node exactly every 10 m. `WayHomeLine` fills a stretch in `ceil(length / 10)` steps. A 250 m stretch measured by haversine came a hair over 250, so it got 26 steps of 9.6 m. CI showed `151.923…` where the test expected `150.0`: exactly 21 steps of 250/26 past the stretch's start, so the code was right and the test was wrong. The four distance tests now take their expectation from the line's own nodes, with the figure's rough size beside it.
2. **The made-up reader returned every way within 5 km of a read,** fixed at `4faaa9867`. One read at a route's start would then have found a junction 3 km on, so the F1 test could not have told reads along the route from one read. It now returns the ways within the read's circle plus 100 m, as a cell read does.
3. **The winding-through case had two thresholds both keeping it from being a turn,** fixed at `ee7398388`. No one-line revert of either could have turned it red. It now lies inside the margin, so only the bend threshold holds it.
4. **The dispatch's premises held** [observed]: the base, RouteWays' production caller, and the fixture. One possible conflict was raised to the planner. Q3's option said that where "the way is a straight line" the space shows "A straight-ahead arrow", while this dispatch says "a way with no trail route" is blank. The owner has since ruled "Blank" for it (E3), so 2a's result stands.

### Decided beyond scope

Each is open to the planner, and each is one place in the code.

- **Reads of 1 km round stretches of the route,** not one read round the whole route.
- **1 m as "on a way"** for the engine's matched ends.
- **`NO_LEG` (less than 0.5 m of line) is no turn,** and its ways count on no side. A walker standing on the trail at a lead-in's join gets no arrow there.
- **Unknowns blank the space** until they are passed, under `JUNCTION_UNREAD`:
  - an interior route point that is not in the data;
  - a junction whose way in or out is not among its branches;
  - a join on no way.
- **Within 45° on both sides of a fork, the nearer branch decides the keep side.**
- **A branch exactly along the way out** (measure zero) is taken as leaving right.
- **The way home's track to the start counts as unknown whenever it has any length,** even when it is only the straight leg to the origin waypoint, which `WayHome` does not mark apart. So in that case the space is blank, not the straight-ahead arrow.
- **At a junction with two paths on one side, the count counts two.** Which ways are paths is B5's, now ruled; this part is not.
- **The count is given for every band,** leaving which words use it to Phase 2b.
- **The card's E1 states are an enum the card will say** (`NextTurnCardState`), not worked out again in the domain from the readout. This keeps one source of truth for "Arrived", the dash and the fix's circle.
- **The way's own states each have a blank reason:** pending, no trail route, junctions not yet read, unread, not placed.
- **The M1 line is split into numbered parts above 3,500 characters,** under Android's log message limit.

### Not tested, said plainly

- **Nothing calls `nextTurn` or `nextTurnSpace` in production.** The card is Phase 2b, so the per-fix cost of `nextTurn` on a real line is untested on a device.
- **The M1 line on a real build.** The APK builds with it (CI's assembleDebug), and the wiring is tested through the use cases. The `AppContainer` lines themselves are not unit-tested, as the existing `info = Log.i` line beside them is not: no test builds `AppContainer`.
- **"A straight line logs nothing"** passed on the stubs and has no revert. It is an absence claim, and the forward code makes no call there to revert. It would fail if a later change called the hook for every result.
- **Concurrency.** A read can run beside an engine route on the phone. `BRouterRouteWays` shares no engine state, as its own doc says, but memory under both at once is unmeasured.

## The S22 M1 check, for the planner

**What it is for:** to set B1's thresholds (and the fork's 45°, and the count) against the owner's own judgement of which junctions are turns, before they are kept (B1: "The planner reads that log on the S22 on the owner's own routes before the thresholds are kept").

**Before:** a debug build from this branch's CI, installed on the S22, with the owner's area downloaded with its trail routes. The build is the `app-debug-apk` artifact of run 42234175132, at the code head `7c1e051aa`. The run on the report's commit builds the same code. That run's build step had the routing app key set (`FORAGER_ROUTING_APP_KEY: ***` in its log) [observed], so the build can fetch trail routes.

1. **Where it appears.** Logcat, tag `NextTurn`, level I: `adb logcat -s NextTurn:I` (warnings from the same tag show as W).
   - **The way to a waypoint:** one line at the first fix after Navigate to a waypoint or trip, when a trail route is found, and one more at each re-route.
   - **The way home:** one line at Return, when a trail route is found, and one more at each stray re-route.
   - **No line** for a straight line or the track alone.
2. **How to read it.** "What the M1 line looks like" above gives every field. To find a junction on the map, use its metres along. The way to a waypoint's line starts where the walker stood when the route was found. The way home's starts at the walker's newest kept point at Return or at the re-route. The line carries no position, by design, so the matching is done by walking or by measuring along the drawn route.
3. **What to compare.** For each judged point, would the owner call it a turn, a fork, or carrying on?
   - **`TURN`s with a small turn and a negative margin** (another way straighter): does a small bend at a Y read as a turn?
   - **`STRAIGHT_ON` entries near the thresholds:** a turn of 20° to 30° with a margin under 30° (near S), or a turn of 30° or more with a margin of 25° to 35° (near M). These are where a threshold change would move the class.
   - **`FORK` entries:** is a keep-left or keep-right arrow wanted there?
   - **The joins (`join (start)`, `join (end)`):** is the choice at the lead-in, the final leg or a joint judged as the owner would?
   - **`same-side paths before` on each `TURN`, and the ways `not counted` beside it (B5):** are those the paths the owner would count for "take the second left", and the left-out ones (service ways, motorways, trunk roads and their slips) the ones they would leave out?
4. **What else to note.**
   - `UNMATCHED` and `NO_LEG` counts. Any `UNMATCHED` means the space would be blank there.
   - The read time and number of reads in the header (M2).
   - Any `M1 ... ways unread` line.
5. **Privacy.** The M1 line holds no position. The warning written beside an unread line passes the reader's own exception, whose message can name a route square: don't copy that warning into anything shared.

## Open items for Phase 2b

- **Ruled since the dispatch (Amendment 1, B5 and E3, as the coordinator relayed them):**
  - **E3.** For the straight line to a waypoint, the owner chose "Blank": "Nothing, as for places with no trails or routing data. "straight" beside the distance already says it." This replaces the "(or the way is a straight line)" part of Q3. 2a's `Blank(NO_TRAIL_ROUTE)` for it stands. The straight-ahead arrow (`NoTurnLeft`) shows only on a trail route with no turn left before the target, and 2a returns it only there.
  - **B5.** The count is walkable ways only. It is built above.
- **Still open: the planner's reading that the count is for plain turns only.** It is open to the owner. 2a gives the count for every band, so the words can take either answer.
- **Wiring the card.** It needs the line's turns where the line is held: `WayHomeWatch` and `AvailabilityWaypointRoute`, each with `routeTurnsFor` and the route data folder. Today the folder reaches only the use cases' hook. 2b will also need to keep the M1 line and the card on the same turns.
- **The card's E1 state.** It maps its readout to `NextTurnCardState`. `NoTurnLeft` shows the top line's own figure (A3).
- **The TalkBack words.** These are the owner's G1 words, relayed by the coordinator: "turn left", "make a sharp left", "slight left", "keep left", "keep straight", each followed by the distance, with "take the second left" from the count. The fit: the owner's "if it is too big bring it up then".

## Clean-up

- **Throwaway branches, all deleted from origin after their runs:**
  - the 25 revert branches, `next-turn-2a-revert-*`;
  - the two probes, `next-turn-2a-probe` and `next-turn-2a-probe2`;
  - the draft's backup, `next-turn-2a-wip`.
  Their runs stay readable on GitHub by the numbers above.
- **Scratch worktrees:** the probes' and the reverts' worktrees under `/mnt/work/scratch/next-turn-2a/` are removed. The scratch folder itself, holding the scripts, the saved copies and the downloads, is deleted at hand-back, after the last CI check.
- **Not touched:** no other branch, no git stash, no Gradle on the laptop, and the planner's worktree.
- **Not added:** no row in `docs/audits/README.md`, as the dispatch said.

## Phase 2b-1 (draft: built, not yet run on CI, not pushed)

Hazel Cairnwright, 2026-11-06. The scope is Amendment 1, H4, verbatim: "2b-1, now: `routeTurnsFor` held where each line is held (WayHomeWatch, AvailabilityWaypointRoute) with its route data folder, recomputed on each re-route. Then the card's state mapping (`NextTurnCardState`) as a pure function, and H3. These have no UI, and tests come first." The owner's go, verbatim: "#500: restart Phase 2b-1", and earlier "Carry on with the building".

**Status: stopped before the first push.** The privacy scan the dispatch requires before every push (`git diff origin/next-turn HEAD`) reports 6 matches, not 0. All 6 are in six files the pre-main merge (`ec738c364`, a clean merge of `64be65216` and `61f99fbe2`: its tree equals `git merge-tree` of the two) brings in, each byte-identical to pre-main `61f99fbe2`, and on added lines only. The two 2b-1 commits alone scan 0 (`git diff ec738c364 HEAD`). The question is with the planner; nothing has been pushed.

### What is built (local commits `b99797aea` tests first against stubs, `b64d6f571` the code)

- **The folder.** `WayHomeResult.AlongTrails` and `WayToWaypointResult.AlongTrails` carry `routeData`, the folder the route was found on, set by both use cases. `null` only where a test builds a result without one.
- **The reader** (`domain/nextturn/RouteTurnsReader.kt`, new). `RouteTurnsReader`, the project's own seam, and `RouteWaysTurnsReader`, which calls `routeTurnsFor`. No folder, a failed read and a reader that throws each give an explicit `RouteTurns.Unread`, warned of; a throw is named by its class, never its message.
- **One read per holder** (`domain/nextturn/HeldRouteTurns.kt`, new). Each new line cancels the read in flight and starts its own.
- **WayHomeWatch.** `WayHomeShown.turns`: `null` while read and with no line. Read for each line found, at Return and at each re-route; kept only on the line it was read for; cancelled by a fallback to the track and by Stop.
- **AvailabilityWaypointRoute.** `WaypointRouteShown.turns`, the same, held while the walker is off the line, since the line is still held then. The ViewModel and its factory pass the reader on; `AppContainer` builds one for both.
- **The card's state** (`ui/availability/NextTurnCardStateOf.kt`, new). `nextTurnCardState(readout, hasFix)` reads the card's own readout: no fix, "Arrived" (live or held), "Unable to calculate route" (a new `NextTurnCardState.ROUTE_WITHHELD`), the dash, else a figure.
- **H3.** A join whose lead-in (or final leg), measured along the line, is shorter than `TURN_MEASURE_METERS` is a new `JunctionClass.SHORT_LEG`: never a turn, no side for its ways, counted apart in the M1 line ("0 short legs" in its header, so the pinned format test's header gains it).

### Still to do once the scan is ruled on

Red run on the stubs, green, the one-line revert per ruling, full CI compared by test name with pre-main, and the four disclosure sections.

### Hand-back, mid-verification (Hazel, 2026-11-06)

- **The scan, ruled.** The owner: "Push, check new work only (Recommended)". Before each push I ran both `git diff origin/pre-main HEAD` and `git diff ec738c364 HEAD` through the scanner, and both read 0 every time.
- **Pushed:** `eb906bb05`, then `884f36c6e`. `884f36c6e` takes the colons out of `NextTurnCardStateTest`'s test names: the JVM refuses them (the compile log of run 42280248236, 18 lines of `Name contains illegal characters: :.`).
- **The first red run, 42280248236, read nothing.** It had compile errors, so its results were not read.
- **Started, not yet read:**
  - red run 42281323874 on throwaway `next-turn-2b1-red` (`65c4c65c5`: the stubs plus the name fix), filtered to the new classes and `RouteTurnsLogTest`, `NextTurnAheadTest` and `RouteTurnsTest`;
  - PR run 42281325309, the full CI on `884f36c6e`.
- **Still to do:**
  - green;
  - four one-line reverts, with the predictions below;
  - the full CI compared by test name with pre-main;
  - the four disclosure sections.
- **The four reverts and their predictions.** Run each with `/mnt/work/scratch/hazel-2b1/scripts/revert.py`, which edits one line, pushes a throwaway branch and restores from a saved copy. Read each with `results.py`, which refuses to read any run whose log has a compile error.
  - **H3,** `legMeters < TURN_MEASURE_METERS` to `< 0.0`: 7 `ShortLegJoinTest` failures predicted.
  - **The folder carried,** `FindWayToWaypointUseCase`'s `routeData = routes.folder` dropped: 2 predicted (the VM turns test 1, and `RouteTurnsHeldOnBRouterTest`'s way to a waypoint).
  - **The holders' turns,** `HeldRouteTurns`' `deliver(...)` dropped: 4 predicted (`WayHomeWatchTurnsTest` 1 and 2, the VM turns tests 1 and 2).
  - **NextTurnCardState,** the `ROUTE_UNAVAILABLE_TEXT` branch removed: 1 predicted (the held-back route).
- **To watch:** `NextTurnAheadTest`'s 10 m lead-ins may go red under H3. Report them; don't edit them.
- **Throwaway branch to delete afterwards:** `next-turn-2b1-red`.

## Phase 2b-1, verified (Hazel, second session, 2026-11-06)

Taken over from the hand-back above. The state it gave matched the remote: `origin/next-turn` at `6e7a121e3`, `next-turn-2b1-red` at `65c4c65c5`, pre-main at `61f99fbe2`, already merged in. Pre-main did not move during the session. `6e7a121e3` changes only this report from `884f36c6e`, so its runs test the same code.

### Evidence

Each run was read by a script in my own scratch, which refuses a run unless it is complete, on the expected commit, has a non-empty log, and that log has no Kotlin compile error (`e: file://`) and no failed compile task. Counts come from the JUnit XML, by task, class and test name.

- **The previous session's scripts had one hole, so I did not use them.** `results.py` called `gh run view` without a repository. From a folder outside the repository, gh fails, the log comes back empty, and an empty log counts 0 compile errors. Mine passes the repository and refuses an empty log. `revert.py` pushed a branch but started no run, since a push to a branch other than main, pre-main or dev runs no CI. Mine starts the filtered run.
- **The red run, 42281323874** on `65c4c65c5`, the stubs: no compile errors; 79 tests in the 9 filtered classes, 31 failed. Every failure is in a 2b-1 class, and each is the stub's reason:
  - the reader answering `stub`;
  - the held read never delivering (`List is empty`);
  - the folder `null`;
  - the card state always `SHOWS_DISTANCE`;
  - H3's `SHORT_LEG` read as `TURN`;
  - the M1 header with no short-leg count.
  The 2b-1 tests that pass on the stubs are controls: the three judged joins in `ShortLegJoinTest`, and the four `SHOWS_DISTANCE` cases in `NextTurnCardStateTest` (a figure, no turn left, a stale fix, E3). Each is a case the stub's answer already gets right.
- **Green, PR run 42281418807** on `6e7a121e3`: every job passed. 6,943 tests, 0 failed, 24 skipped.
- **Compared by name with pre-main's push run 42245483101** on `61f99fbe2` (the exact base): 6,838 there, 6,943 here.
  - No test of pre-main's is missing, and none changed status.
  - The 24 skips are the same tests.
  - The 105 new tests are in 12 new classes (999 to 1,011 XML files), the 2a and 2b-1 tests: `NextTurnAheadTest` 19, `TurnRuleTest` 19, `RouteTurnsTest` 16, `NextTurnCardStateTest` 12, `ShortLegJoinTest` 10, `RouteTurnsLogTest` 7, `WayHomeWatchTurnsTest` 6, `RouteTurnsOnBRouterTest` 5, `RouteTurnsReaderTest` 4, `AvailabilityViewModelWaypointRouteTurnsTest` 3, `PathsPassedTest` 2, `RouteTurnsHeldOnBRouterTest` 2.
- **`NextTurnAheadTest`'s 10 m lead-ins did not go red under H3.** All 19 pass in the green run, and again in each revert run.

### The four reverts

Each was a one-line edit of `6e7a121e3` on a scratch worktree, pushed to a throwaway branch and run on CI with the 2b-1 filter. Each compile log was checked before its results were read: no compile errors in any. The file was restored from a copy saved before the edit, and compared with `6e7a121e3`'s: identical. Afterwards `next-turn`'s own worktree was still clean at `6e7a121e3`, with each of the four forward lines present. Each revert's failures match its prediction in count and in test, and each is one only that edit could cause.

| Revert | Run | Failed of 79 | What failed |
|---|---|---|---|
| H3: `legMeters < TURN_MEASURE_METERS` to `< 0.0` (`8be917fa4`) | 42284711550 | 7 (predicted 7) | all 7 short-leg cases in `ShortLegJoinTest`: `expected:<SHORT_LEG> but was:<TURN>`; the trail behind counted (`expected:<0> but was:<1>`); the 5 m walker's `Turn(band=PLAIN, side=LEFT …)` for `NoTurnLeft`; the M1 line reading `0 short legs` |
| The folder carried: `FindWayToWaypointUseCase`'s `routeData = routes.folder` dropped (`90025f830`) | 42284725439 | 2 (predicted 2) | the VM's folder test (`expected:</routes/1> but was:<null>`) and `RouteTurnsHeldOnBRouterTest`'s way to a waypoint (`the folder it was found on … but was:<null>`). The way home's own test still passes, since only the way-to edit was reverted. |
| The holders' turns: `HeldRouteTurns`' `deliver(...)` dropped (`19292eea3`) | 42284739412 | 4 (predicted 4) | `WayHomeWatchTurnsTest`'s first read and re-route read, and the VM's first read and stray re-route: each `expected:<Read(…)> but was:<null>` |
| `NextTurnCardState`: the `ROUTE_UNAVAILABLE_TEXT` branch made a comment (`6894dc7ae`) | 42284755068 | 1 (predicted 1) | the held-back route: `expected:<ROUTE_WITHHELD> but was:<SHOWS_DISTANCE>` |

The folder run's run log came back at 1,232 lines. Its job log, read through the API, has `compileDebugUnitTestKotlin` running and "79 tests completed, 2 failed", with no compile error.

### Confirmed and inferred

- **[observed]** Every run, count and message above. The red run's failures, each the stub's reason. The by-name match with pre-main. The four reverts' failures. The forward lines present after each restore.
- **[read]**
  - `WayHomeWatch` and `AvailabilityWaypointRoute` each hold a `HeldRouteTurns`.
  - `AppContainer` builds one `RouteWaysTurnsReader` for both.
  - `nextTurnCardState` reads only the card's readout and the fix.
- **[inferred]** That the way home's folder is carried as the way to a waypoint's is. Only the way-to line was reverted; the way home's line is the same shape, and its own test (`RouteTurnsHeldOnBRouterTest`, the way home) failed on the stubs.
- **[not verified]** Anything on a phone. No screen reads the turns or the card state yet (2b-2).

### Could not determine

- **Whether the reads cancel cleanly under real timing on a phone.** The cancel tests run on a test dispatcher, and nothing on a device reads the result yet.
- **The read's cost once it runs on every re-route** on a real line (M2, as in 2a).

### Premises that were wrong

- **The previous session's results script would have read an empty log as clean,** when run outside the repository (above). It was not used for any result cited here. I found nothing to show it had produced a wrong reading before.
- **That revert script could not have run CI by pushing alone:** no run starts on a push to a throwaway branch.
- The hand-back's state, its four predictions and its watch item held [observed].

### Decided beyond scope

- **The previous Hazel's three beyond-scope calls** went to the planner in her hand-back message. I did not find them written in this report, and I have neither restated nor changed them.
- **Mine:** the card-state revert turns the line into a comment rather than deleting it, so the diff stays one line in and one line out. It has the same effect as removing the branch.

### Clean-up

The four revert branches and `next-turn-2b1-red` are deleted from origin, their worktrees removed. Their runs stay readable by the numbers above. My scratch is `/mnt/work/scratch/hazel-2b1b` (mode 700). The previous session's `/mnt/work/scratch/hazel-2b1` is left as found, less `wt-red`.
