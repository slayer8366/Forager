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

## Category I, names and icons over the lines: pre-build report, stopped (Hazel, 2026-11-06)

Nothing is built. Read at `744d96821`, with pre-main at `61f99fbe2`; `nav-card-kind-line` (`5427954d2`) changes no file under `ui/map/` or `map-style/`. The owner, verbatim: "Add to 500: can we have street and trail names overlap the navigation line rather than be written under?"; "All lines the app draws"; "Names and icons on top". Three of the planner's premises do not hold as written, and two choices change what a user sees beyond I. That is a stop.

### What the app draws today [read]

- **One map, one setup.** `SightingsMap` is the only `MapView`, at `SightingsMap.kt:268`. On every style load, `MapStyleReload.kt` calls `initializeOverlayLayers` (`OverlayLayerSetup.kt`). That function puts every app layer on top of the whole basemap, in this order:
  1. the registry (`MAP_LAYER_REGISTRY`, `layers/MapLayers.kt`), in its bands, bottom to top: colour fields, areas, decorations, lines, markers;
  2. then the way home and the way to a waypoint;
  3. the plotted route;
  4. the waypoint names;
  5. the fan-out.
- **Every app line, with its production caller.** Each is reached through `initializeOverlayLayers` from `MapStyleReload.kt`.
  - The registry's lines, each a `LineLayer` from `OverlayLineSpecs.kt:213`:
    - the offline region's night border and its outline;
    - the recording breadcrumb and its casing;
    - the journal halos of an offline region and of the kept tracks;
    - the kept tracks and their casing.
  - The way home and the way to a waypoint (`RouteHomeLayers.kt:223-273`, added at `:284-290`): passed, ahead casing, ahead, ahead dotted, ahead straight legs, the straight line, and the straight line kept offline.
  - The plotted route (`PlottedRouteLayers.kt:104-139`): casing, straight casing, trail and straight.
  - The fan-out's legs and their casing (`FanOutLayers.kt:90-96`). These are drawn with the fanned-out markers, above every registry layer.
- **Not lines, beside them:**
  - the plotted route's stop dots, a circle layer directly under the way home (`PlottedRouteLayers.kt:148`);
  - the journal marker rings, symbol layers in the decorations band, below every line. The owner put them there: "1 A", to "Move marker rings below all lines".
- **The puck** is MapLibre's location component, activated after the overlays.

### Premises that were wrong

1. **"Below the basemap's first symbol layer" on Trail Map would put roads over the app's lines.** In Trail Map v4 (`trail-style-v4.json`, the same layer ids as `map-style/forager-trail-v4.json`), the first symbol layer is `reference_fs_number`, the Forest Service trail number, at index 84 of 182. Above it come 32 line layers before the labels begin at `address_label` (117): every road, rail line, bridge and boundary. v3 is the same shape (first symbol 61, roads up to 81). v1 and v2 have no symbol below a line. So, placed literally, a way home along a road would be drawn under the road.
2. **Topo and Street have no symbol layers.** Both are raster pictures (`a.tile.opentopomap.org`, `tile.openstreetmap.org`), with their names part of the image. On them the names can only go over the line by putting the line under the picture, which hides it. So on Topo by day and on Street, day and night, the ask cannot be done, and I's own fallback applies: today's placement, logged. Only Trail Map has names as separate layers. The offline style (`offline-style.json`, 57 layers) has none either.
3. **Topo at night is Trail Map under a Topo picture.** It is `TrailWithTopoOver` (`TrailStyleApply.kt:26`): the Topo raster is the top style layer from zoom 9.5, and Trail Map's labels stop at 9.5. Its first symbol layer is under the picture. Lines placed below it would be covered by the Topo picture at every walking zoom, so this map also has to keep today's placement.

### For the owner (stop)

- **Q1. Where do the app's lines go on Trail Map?**
  - (a) **Recommended:** just under the block of names and icons. Roads, rail, bridges and boundaries stay under the lines, as today, and every name, road number and icon draws over them. One label stays under the lines, as it does today: the Forest Service trail number, because Trail Map's style draws it among the roads.
  - (b) Under the first label of any kind. The trail number goes over too, but every road, railway, bridge and boundary also draws over the lines.
  - (c) As (a), and move the trail number up among the other labels in Trail Map's own style. That is a style change the Worker serves from main, beyond I.
- **Q2. What moves with the lines?** Under the lines today are the forecast colours, the offline area's tint and the journal rings.
  - (a) **Recommended:** all of them move down together, so they stay under the lines as now. Names and icons then also draw over the forecast colours and the offline tint.
  - (b) Lines only. The forecast colours, the offline tint and the journal rings would then draw over the lines, which undoes "Move marker rings below all lines".
  - Either way, the plotted route's stop dots move with the plotted route, staying under the way home. The fan-out's legs stay on top with the fanned-out markers, since they are part of that gesture and not a route. Say if the legs should move too.
- **Q3. Is this right for the other maps?** Topo by day and Street, day and night, keep today's placement, logged, because their names are part of the picture. Topo at night keeps it too, because the Topo picture covers Trail Map there. So the change shows only on Trail Map, day and night, and on downloaded Trail Map areas.

## Category I, built: the app's lines under Trail Map's names (Hazel, 2026-11-06)

Built against Amendment 1's reading after I7 (`records-after-173` at `ca8b278c0`). The owner, verbatim: "Under all names, trail no. too" (I5); "Move down with the lines (Recommended)" (I6); "Stay with the markers" (I7). The starting state matched the remote: `next-turn` at `fd6eaa643`, pre-main `61f99fbe2` merged in. Pre-main moved during the session, to `a5a2a392f` (PR #501, home values out), and was merged in at `4d479107e`. Neither sibling branch touches a file changed here.

**Built: the app half of I5, and I6. Not built: the trail-number style change, I5's second half.** That one is a stop, below.

### What was built

- **`ui/map/AppLinesUnderNames.kt` (new, about 100 lines).** It reads the loaded style's layers before any app layer is added. It finds the first layer of the basemap's top run of name and icon layers, which is the names anchor. Every registry layer in the colour-field, area, decoration and line groups is then added just below that anchor, in registry order. A style whose top layer is not a name or icon layer has no anchor, so everything is added on top, as before. One line is logged per style load, saying which of the two happened and why.
- **`OverlayLayerSetup.kt`: two lines.** The anchor is read before the registry loop. Inside the loop, `style.addLayer(layer)` became `addOverlayLayer(style, layer, overlayPlacement(spec, namesAnchor))`.
- **Why the rest follows without being touched.** The way home and the way to a waypoint are added above the kept tracks. The plotted route and its stop dots are added below the way home's lowest line. Both are relative adds (`RouteHomeLayers.kt:284-290`, `PlottedRouteLayers.kt:136-148`), so they land under the anchor with the kept tracks. The arrival ring and the waypoint names are added above the waypoints, and the fan-out is added on top, so they stay over the whole map. The puck is placed below the fan-out's legs (`LiveLocationPuckOrderTest`), so it stays on top too.
- **The anchor is the top run, not the first symbol layer.** This follows I5 ("roads stay under the lines"). On every served Trail Map, v1 to v4, the top run starts at `address_label` (v4: index 117 of 182). Directly under it are the bridges, so roads, rail, bridges and boundaries stay under the lines. If the trail number is later moved up among the labels, the anchor rule still holds.
- **Added in place, not moved afterwards.** MapLibre 13.5.0's Android `Style` has no move call, only `removeLayer` and adding the layer again. I checked this with `javap` on the pinned AAR. A layer lost between those two steps would be a missing line, so adding in place was the safer choice.

### Evidence

Every run was read by `/mnt/work/scratch/hazel-catI/results.py`, the second 2b-1 session's script copied unchanged. It refuses a run that is incomplete, on another commit, or whose log is empty, short, or has a compile error. The filter for the filtered runs was `AppLinesUnderNamesTest`, `MapLayerRegistryTest`, `BasemapStyleTest`, `OwnTrailStyleSourceTest` and `TrailStyleTest`, 70 tests.

- **Red, 42291849106** on `9eb86c9af` (tests plus stubs that keep today's placement). No compile errors. 70 tests, 3 failed, all in `AppLinesUnderNamesTest`, each for the stub's reason:
  - the anchor `expected:<address_label> but was:<null>`;
  - `forecast-chicken-of-the-woods-layer must draw under the names`;
  - the log line reading `stub`.
- **Six of the nine new tests passed on the stubs. They are controls, and they pass before and after by design:**
  - Topographical by day, Street and every other raster map unchanged;
  - Topographical at night unchanged;
  - the offline style unchanged;
  - the block's own order;
  - markers and the fan-out on top;
  - roads under the lines (today's placement already has them under).

  The last of these bites only against the planner's first reading. The i5anchor revert below shows that it does.
- **Full CI, first: PR run 42293168413** on `4d479107e`. The unit tests were all green: 6,952, 0 failed, 24 skipped. One job failed, "The app builds and its tests pass with the Street map deleted". CI's error was "something outside the Street map's place still names it", at `AppLinesUnderNamesTest.kt:11` and `:185`, my test's `StreetMap` import. That was my mistake. The fix, `4d9ab8418`, checks every registered map type from `Basemap.entries`, day and night, and names no Street map.
- **Full CI, green: PR run 42295591797** on `4d9ab8418`. Every job passed: 6,952 tests, 0 failed, 24 skipped.
- **Compared by name with pre-main's push run 42292140229** on `a5a2a392f`, which has 6,838 tests:
  - none missing, none changed status;
  - the 24 skips are the same tests;
  - 114 new tests: 2b-1's 105 and this section's 9 (`AppLinesUnderNamesTest`).
- **The reverts ran at `4d479107e`, before the test fix.** The fix changed only one control test's coverage assertion, and no production line, so the revert results hold for `4d9ab8418`'s code.
- **The final run, after merging pre-main `b516d520a`,** is in the hand-back, since it runs after this report is pushed.
- **The laptop's root drive was nearly full** (14 MB free, the planner says) until about 21:10Z, during this work.
  - My scratch is on `/mnt/work`. My commits were made and pushed in that window, and GitHub accepted each push.
  - Afterwards, a first `git fsck` listed missing trees. A second listed none. `HEAD`'s tree reads whole, and the 364 objects in my commits enumerate without error.
  - Two of my background wait-loop output files under `/tmp` came back empty. They were wait loops only, and every result above was re-read from CI afterwards.

### The three reverts

Each revert was a one-line edit of `4d479107e` in its own scratch worktree, run on CI with the filter above. Both scans read 0 before each push. No compile error appeared in any log. Each file was restored from a copy saved before the edit and compared equal with `4d479107e`'s. Afterwards `next-turn`'s worktree was clean at `4d479107e`, with all three forward lines and both wiring lines present. Each prediction was written before its results were read.

| Revert | Run | Failed of 70 (predicted) | What failed |
|---|---|---|---|
| I5 app order: `overlayPlacement` always `OnTop` | 42293278391 | 2 (2) | the block `directly above roads_bridges_highway expected:<117> but was:<182>`; `forecast-chicken-of-the-woods-layer must draw under the names` |
| I5's anchor rule: `namesAnchorOf` takes the first symbol layer, the planner's first reading | 42293314166 | 4 (4) | anchor `expected:<[address_label]> but was:<[reference_fs_number]>`; Topographical at night `expected null, but was:<reference_fs_number>`; `roads_bridges_highway must stay under the way home`; the log line naming `reference_fs_number (layer 84 of 182)` |
| I6: `GROUPS_UNDER_NAMES` lines only | 42293329295 | 3 (3) | `directly under address_label expected:<136> but was:<207>`; the block's own order (forecast colours first today, lines first with the revert); `forecast-chicken-of-the-woods-layer must draw under the names` |

No revert was run for I5's style half, since it is not built.

### Confirmed and inferred

- **[observed]** The runs, counts and messages above. Every Trail Map style the Worker serves was read: the layer order of `trail-style-v4.json`, `street-style-v4.json`, `street-style-v3.json`, `street-style-v2.json` and `street-style-v1.json`, the offline style, and the raster JSON the app builds.
- **[read]**
  - `OverlayLayerSetup.kt` is reached only from `MapStyleReload.kt:165`, on every style load.
  - The routes and the waypoint names are added relative to registry layers (lines cited above).
  - `Style.addLayerBelow(Layer, String)` exists in the pinned 13.5.0 AAR (`javap`).
- **[modelled, not run]** The test runs the production placement for every registry layer. It models the adds after the registry, written from their code, because MapLibre's `Layer` and `Style` need the native library (`SightingsMapOverlayDataTest`'s doc comment). The wiring itself, the two lines in `OverlayLayerSetup.kt`, cannot be reached by any JVM test. Reverting it would leave the suite green. Only the device check covers it.
- **[not verified]** Anything on a phone: that MapLibre draws the app's lines under `address_label` and the names over them.

### Could not determine

- **Whether the night recolour, or the pattern relayout after a reload, treats an app layer under the names differently from one on top.** Both run on the style's own layers before the overlays are added (`MapStyleReload.kt:149-160`) or touch only pattern layers. I found nothing that depends on the app's layers being on top, but this is a device question.
- **How a downloaded Trail Map area's stored style orders its layers on a phone.** It is the same document, so the same rule applies, but that is inferred.

### Premises that were wrong

- **"In Trail Map's own style the Forest Service trail number moves up among the labels … Nothing else in the style changes. The style's checks must pass."** As written, these three cannot all hold. The style's checks hold v4 frozen:
  - `tools/check-trail-night.mjs` pins `forager-street-v4.json`, its preview copy and the Worker's `street-style-v4.json` to sha256 `d27d42d1…`. On a change it reports "a new design ships as a new version".
  - Its check 10 fails if v4 "moves a layer v3 has", and `reference_fs_number` is a v3 layer.
  - The Worker's tests (`worker.test.mjs:509-531`) hold trail-v4 equal to street-v4 apart from the name, and street-v4 to the same sha256.

  So moving the number in trail-v4 breaks the equality test. Moving it in both breaks the freeze. Either way the checks pass only if they are edited. That is a decision about an earlier ruling (D2, RECORD -2000), and not mine to make.

### Decided beyond scope

- **The anchor is read from the style's own layers, not from which map is chosen.** Topographical, Street, Topographical at night and the offline style keep today's placement because their top layer is not a name or icon layer. No list of maps is kept for this. The one log line per style load says which case happened.
- **One extra revert (the anchor rule)** beyond the one per ruling. It shows that the roads test and the Topographical-at-night test bite.

### For the owner (stop): the trail number in Trail Map's style

The rest of I is built. The trail number still draws under the lines on today's style, as before. The fix for it can go one of three ways:

- (a) Update v4 in place, at both addresses (trail-v4 and street-v4). This is how the first-zooms change was done (dispatch 2026-11-02-coder-13, the owner: "Update v4 in place"). The frozen sums and check 10 would be re-held to the new files. Downloaded areas keep their address and need nothing new.
- (b) Ship it as trail-v5. v4 stays frozen, and the app moves to the new address. Areas downloaded on v4 would then count as an older style and show the older-style notice. That notice is something a user sees beyond I.
- (c) Leave the trail number under the lines for now.

Question: should the trail-number move update Trail Map v4 in place, as the first zooms did, or ship as trail-v5?

### S22 steps (for the owner at the phone; the coder does not run it)

1. **Trail Map, day.** Set a waypoint beside a named road or trail, start the way to it, and zoom to 15 or 16.
   - The road or trail name, and any shield or icon, draws over the blue line.
   - The road itself draws under the line.
   - Waypoint names, markers and the position dot stay over the names.
2. **The same at night.**
3. **The Forest Service trail number** (zoom 14 or more, on a numbered trail under the line): it still draws under the line. Note what is seen, since this is the half that is stopped.
4. **Topographical by day, and Street day and night:** the line draws over the picture as before.
5. **Topographical at night** at walking zoom: as before.
6. **logcat, tag `SightingsMap`:**
   - On Trail Map: "App lines under the map's names: … below address_label (layer 117 of 182)".
   - On the other maps: "… today's placement: its top layer basemap is not a name or icon layer" (the offline style names `roads_bridges_highway` instead).
   - The exact layer number depends on the style the phone has.
7. **A downloaded Trail Map area, offline:** as step 1.

### Clean-up

The throwaway branches `next-turn-catI-red` and the three `next-turn-catI-rev-*` are deleted from origin at the end, and their worktrees removed. Their runs stay readable by number. Scratch: `/mnt/work/scratch/hazel-catI` (mode 700).

## I8, built: the Forest Service trail number over the app's lines, Trail Map v4 updated in place (Hazel, 2026-11-06)

Built against Amendment 1's I8 (`records-after-173` at `8e68f34d7`). The owner, verbatim: "Update v4 in place". On why v4 may change in place (RECORD -2183, relayed by the planner): "Nobody is using Trail maps so updating in place is permitted. Only when we release it do we need to do it properly. It's coming out this release so this might be the last time we get to do that". The starting state matched the remote: `next-turn` at `f3c4a07b7`, pre-main `b516d520a` merged in, the worktree clean. Neither sibling branch (`nav-card-kind-line`, `home-values-out-2`) touches a file changed here.

### What was built

- **`map-style/overrides/forager-reference.mjs`: a new function, `withTrailNumberUnderLabels`.** It moves `reference_fs_number` to the bottom of the style's top run of labels: directly above the last layer that is not a label, and so directly under the first label. It throws, rather than putting the number somewhere else, if the layer or the run is missing. It went in this file, not a new one, because a new file would have to be named in v4's `forager:generated-from`, and that text would then change too.
- **`map-style/generate-trail.mjs`: one line.** v4's layers are now `withTrailNumberUnderLabels(applyReferenceZooms(applyFillIn(v3Layers())))`. v3 is built as before. The header comment says why.
- **Regenerated.** In all six v4 files (`forager-street-v4.json`, `forager-trail-v4.json`, their preview copies, and the Worker's `street-style-v4.json` and `trail-style-v4.json`) only one thing changes: the number moves from layer 84 to layer 116 of 182, between `roads_bridges_highway` and `address_label`. A JSON comparison of each file against its pre-I8 version confirmed this. Every layer object is equal, the order of every other layer is equal, and everything outside `layers` is equal. v1, v2, v3 and `offline-style.json` stay byte for byte, in every copy (`git diff --quiet`, and the sums below).
- **The three locks, each with a comment citing I8.** Each comment says the in-place change is allowed only because Trail Map has not been released yet, and that after release a style change ships as a new version.
  1. `tools/check-trail-night.mjs`: street-v4's three pinned sha256 are the I8 files'.
  2. Its check 10: `reference_fs_number` is the one layer added to the exception, and for v4 only. The check now also holds the number's new place: first in the top run of labels, directly under `address_label`.
  3. `server/pmtiles-worker/test/worker.test.mjs`: the street-v4 sha256 test.

  No other check was changed. The Worker's test that trail-v4 equals street-v4 apart from the name stands, and passes, because both files changed together.
- **`AppLinesUnderNamesTest` extended**, since the anchor claim changes. On v4, at both addresses, the top run now starts with the trail number, so the app's lines go just under `reference_fs_number` and no longer under `address_label`. v1 to v3 keep `address_label`.
  - A new test: on v4 the number is directly under `address_label`, draws over every app line, and the roads and bridges stay under the lines.
  - The forecast-colours test is now held under the number.
  - The log-line test now expects "below reference_fs_number (layer 116 of 182)".
- **`AppLinesUnderNames.kt`: its doc comment only.** It said v4 draws the number at index 84 and that the anchor would stay at `address_label`. Both are now wrong for v4. No code line changed, so the app's placement rule is the same, and on v4 it now finds the number as its anchor.

### Evidence

Every Kotlin run was read by the category I session's `results.py`, copied unchanged to `/mnt/work/scratch/hazel-i8`. It refuses a run that is incomplete, on another commit, or whose log is empty, short, or has a compile error. The filter is category I's, 71 tests with the new one. The style checks ran locally on Node 24.21.0, against dependencies installed from the lockfile and assets fetched and checked by `fetch-assets.sh`. CI runs them on Node 24.22.1. No Gradle ran on the laptop.

- **Locally, before the change:** `generate:trail` reproduced every committed file exactly, and `check:trail` and `check:trail:v4` passed.
- **Red, locally and on CI 42303182875** on `ee6386581` (tests only, old style):
  - Check 10 failed with exactly one problem: "reference_fs_number is layer 84 of 182, between roads_path_hard_faint and roads_link; I8 rules it first in the top run of labels (layer 117), directly under address_label". The regeneration step passed, and the Worker's job was green.
  - Kotlin: no compile errors; 71 tests, 4 failed, all in `AppLinesUnderNamesTest`, as predicted:
    - the anchor `expected:<[reference_fs_number]> but was:<[address_label]>`;
    - the trail number "directly under address_label expected:<142> but was:<84>";
    - `forecast-chicken-of-the-woods-layer must draw under the names`;
    - the log line still reading "below address_label (layer 117 of 182)".
- **After the change, locally:** with only the style moved, the one problem was the three street-v4 sums. With the locks updated, both check suites passed, and regeneration left nothing changed.
- **Full CI, green: PR run 42304139052** on `19fa03166` (I8 with pre-main `5c623a8ba`, PR #502, merged in). Every job passed: 6,953 tests, 0 failed, 24 skipped. No compile errors.
- **Compared by name:**
  - With the last green run, 42298652902 on `f3c4a07b7` (6,952 tests): none missing, none changed status, the same 24 skips, and 1 new test (the trail-number test).
  - With pre-main's latest, push run 42303916388 on `944eb8de1` (6,838 tests): none missing, none changed status, the same 24 skips, and 115 new tests (next-turn's earlier 114 and this one).
- **sha256, before and after:**

| File | Before | After |
|---|---|---|
| street-v1, its Worker copy | `b2ec485d…` | unchanged |
| street-v1 preview | `4fc2e14f…` | unchanged |
| street-v2, its Worker copy | `7547507b…` | unchanged |
| street-v3, its Worker copy | `e2a25982…` | unchanged |
| street-v3 preview | `beb40e3a…` | unchanged |
| offline-style.json | `357e2603…` | unchanged |
| street-v4, its Worker copy | `d27d42d1…` | `8032de2c…` |
| street-v4 preview | `bf389ab3…` | `35572d32…` |
| trail-v4, its Worker copy (pinned nowhere; held equal to street-v4) | `d34fb590…` | `e8a8b705…` |
| trail-v4 preview | `4df087d0…` | `424f590e…` |

### The revert

- **The edit.** One line of `generate-trail.mjs` on `5dca17f05`, in its own scratch worktree: v4's layers without `withTrailNumberUnderLabels`. The styles were then regenerated, which the change itself requires. The regenerated v4 files came back to exactly the pre-I8 sums (`d27d42d1…`, `bf389ab3…`).
- **Prediction, written before reading:**
  - the map-style job red on the three street-v4 sums and on check 10's I8 message;
  - the Worker job red on the street-v4 sum test only;
  - the same 4 Kotlin failures.
- **CI 42303229923** on `0b85cd99a`:
  - Kotlin: no compile errors; 71 tests, 4 failed, the same four messages as the red run.
  - The Worker: 80 tests, 1 failed, "street-v4 is held to I8's v4 …", `actual 'd27d42d1…'`, `expected '8032de2c…'`.
  - The map-style job: regeneration passed, then v2's checks failed on the three street-v4 sums. CI stopped at that step, so v4's checks did not run there. Check 10's I8 message on the reverted files was read locally, from the same files: "reference_fs_number is layer 84 of 182 … directly under address_label".
- **Restored.** The file was restored from the copy saved before the edit, and compared equal with `5dca17f05`'s. Afterwards `next-turn` was clean, with the forward line present and street-v4 at `8032de2c…`.
- **What it means.** The Kotlin failures are identical in the red run and the revert because the reverted output is byte for byte the old style. Each message names the trail number's place or the anchor it decides, which nothing but this change moves.

### Confirmed and inferred

- **[observed]**
  - The runs, counts, messages and sums above.
  - The layer order of all six v4 files before and after, and that only `reference_fs_number`'s index changed.
  - v1 to v3 and offline byte for byte.
- **[read]**
  - The number's layer is a symbol layer from zoom 14 (`forager-reference.mjs`, `labelLayers`).
  - The app finds its anchor from the style's layers alone (`AppLinesUnderNames.kt`, `namesAnchorOf`), so it now goes under the number on v4 with no code change.
  - The app's night pass recolours by layer id (`TrailNightTables.kt`), so the move does not change night colours.
- **[inferred, not verified]** The number keeps its collision priority among the labels. It stays the lowest symbol layer in the style, as before, and MapLibre places symbols in layer order. This is not checked on a phone.
- **[not verified]** Anything on a phone.

### Could not determine

- **How the S22 step can be checked before promotion.**
  - The app loads `TRAIL_STYLE_URL`, the Worker's `/style/trail-v4.json`. The Worker deploys from main, so a phone sees the new v4 only after promotion.
  - I found no way on pre-main for an app build to load a style file instead. `TrailNightTables.kt` names the repository's file names for "a test build loading a file", which suggests such a build exists, but I did not find or check it, on dev or anywhere.
  - What can be seen before promotion is the map-style preview (`map-style/./start.sh`, `forager-trail-v4.preview.json`), in the web engine. It shows the number drawn over roads and bridges from zoom 14, but it draws none of the app's lines. So it is no check of the claim itself.
- **Whether a downloaded Trail Map area picks up the new v4.** The area keeps its address, and whether MapLibre refreshes a stored style for that address is a device question. This was the same for the first zooms.

### Premises that were wrong

None found. The three locks were as the stop described (`check-trail-night.mjs:56-58`, check 10 at `:431`, `worker.test.mjs:530`). trail-v4's own sum is pinned nowhere: it is held only by the equality test, which needed no change.

### Decided beyond scope

- **Where in the labels the number goes: the bottom of the top run, directly under `address_label`.** I8 says "up among the labels" and names no place. The bottom keeps it below every other label, as it was before, so its collision priority does not change. Any higher place would let it win space from names it lost to before.
- **What a user sees beyond the lines: the trail number now also draws over roads, rail, bridges and boundaries**, where before it drew under them. This follows from I5 and cannot be separated from it: the lines draw over the roads (I5, "roads stay under the lines"), and the number draws over the lines. Recorded so the owner sees it named.
- **The function went into `forager-reference.mjs`, not a new file**, so v4's `forager:generated-from` text stays the same.
- **How this differs from the first-zooms change (821fa67d6):**
  - One layer moved, against 78 clones added.
  - No new override file, and no metadata change.
  - The first zooms rewrote rulings in `check-trail-night.mjs` and `check-trail-footpaths.mjs`. I8 adds one exception to check 10, together with a check of the new place, and leaves the footpath checks alone.
  - Both updated street-v4's sums in the same two places.
- **`AppLinesUnderNames.kt`'s doc comment was corrected.** It named v4's old index and said the anchor would stay at `address_label`. Only the comment changed.

### S22 steps (for the owner at the phone, after the next promotion; the coder does not run it)

1. **Trail Map, day.** On a Forest Service trail with a number, set a waypoint further along it, start the way to it, and zoom to 14 to 16.
   - The violet trail number draws over the blue line.
   - Road and trail names also draw over the line.
   - The roads themselves stay under the line.
   - Markers, waypoint names and the position dot stay on top.
2. **The same at night.**
3. **logcat, tag `SightingsMap`, on Trail Map:** "App lines under the map's names: … below reference_fs_number (layer 116 of 182)". Before the promotion, it reads "below address_label (layer 117 of 182)", and step 1 shows the number under the line. That is the old v4, not a failure.
4. **Topographical, and Street day and night:** unchanged.

### Clean-up

The throwaway branches `next-turn-i8-red` and `next-turn-i8-rev` are deleted from origin at the end, and the revert worktree is removed. Their runs stay readable by number. Scratch: `/mnt/work/scratch/hazel-i8` (mode 700). The worktree's `map-style/node_modules` and `map-style/assets` are links into scratch, ignored by git, and are removed at the end.

## 2b-2 step 1: the next turn's arrow, words and own space, in new files (Hazel, 2026-11-06)

The scope is Amendment 1, J, step 1. The owner's word, verbatim: "Go ahead and begin part 2 of 500". The planner's call: the arrow (C1 to C3), the TalkBack words (G3, with the count for plain turns only), the space's own composable and its state from `nextTurnCardState`, tests first, with no file nav-card-kind-line changes. The space is **not on the card yet**. That is step 2, after #498's E15 Hazel hands the card files back.

### What landed

| Commit | What it does |
|---|---|
| `37a988424` | pre-main `b6f0b464d` merged in (merge, not rebase). Its four files (RECORD.md, one test, two audits) are none of this branch's. |
| `78ccee9ce` | Tests first: four test classes, 37 tests, against stubs in the four new files. |
| `2c572cee7` | The implementation. |
| `5e9801df7` | The state function's arrow is its one blank gate (below), so the E1 to E3 revert bites at the state level too. The step's own code ends here. |
| `d8f744907` | pre-main `26a5c5955` merged in (see "Premises that were wrong"). |

The four new files, each under 120 lines:
- **`domain/nextturn/NextTurnArrow.kt`.** `NextTurnArrow`, one value per icon, and `nextTurnArrow(NextTurn)`:
  - a turn's band and side (C1);
  - a fork's side to keep to (C2);
  - `NoTurnLeft` gives `STRAIGHT` (C3);
  - a blank gives `null`.
  It has no UI framework in it.
- **`domain/nextturn/NextTurnWords.kt`.** `nextTurnWords(NextTurn, spokenDistance)` gives G3's words. `spokenFigure(figure)` spells out the unit of the card's figure: "400 ft" becomes "400 feet". For a figure in any other form it returns `null` and does not guess.
- **`ui/availability/NextTurnSpaceShown.kt`.** `nextTurnSpaceShown(readout, hasFix, ahead, unit)` gives the arrow, the figure and the spoken words:
  - it goes through `nextTurnCardState`, then `nextTurnSpace` (E1);
  - a turn's or a fork's figure is `formatDistanceMeters(metersAway, unit)`, the top line's own formatter for a trail route (D1);
  - the straight-ahead figure is the top line's own `distanceText` (A3).
  A figure whose unit cannot be spelled out is spoken as written, and a warning is logged.
- **`ui/availability/NextTurnSpace.kt`.** The composable, with the arrow first and then the figure:
  - **Its width:** the arrow, `Spacing.xs`, and the widest of "88.8 mi" and "8888 ft" (or "88.8 km" and "8888 m"), measured in its own style. This is CLAUDE.md, "Changing info gets its own window". It holds that width blank, so nothing beside it moves.
  - **TalkBack:** `clearAndSetSemantics`, so TalkBack reads the words alone and never the drawn figure.
  - **The icons:** `nextTurnIcon` uses the Material Filled icons TurnSlightLeft/Right, TurnLeft/Right, TurnSharpLeft/Right, ForkLeft/Right and Straight, from `material-icons-extended` 1.7.8, already a dependency. None is AutoMirrored.
  - **Its defaults:** style `labelMedium` (the turn line's own type on #498) and arrow size `COMPASS_ICON_SIZE`. Both are parameters, so step 2 can pass the card's own.

**The one-coder-per-file check** [observed]. Before each commit I ran `git diff --name-only origin/pre-main...origin/nav-card-kind-line` (head `b9fad7604`) and compared it with my files: there was no overlap, and every file of mine is new. The only existing file I edited is this report, which is not in that list.

### Evidence

Each run was read by `results.py`, carried over from 2b-1. It refuses a run that is incomplete, on another SHA, has a short or empty log, or has a Kotlin compile error or a failed compile task. Counts come from the JUnit XML. The filter is `*NextTurnArrowTest;*NextTurnWordsTest;*NextTurnSpaceShownTest;*NextTurnSpaceTest`.

- **Red: run 42312833368** on `78ccee9ce` (the stubs), with 0 compile errors. 37 tests, **28 failed**, each for its stub's reason: `expected:<SLIGHT_LEFT> but was:<null>`, `expected:<turn left in 400 feet> but was:<null>`, `expected:<Filled.[TurnSlightLeft]> but was:<Filled.[Navigation]>`, and the space's tag not found. The 9 that passed on the stubs are controls whose claim is "blank" or "not guessed":
  - the arrow's and the words' blanks;
  - the figures in other forms;
  - E1's four states, E2 and E3 in the state test.
  Each of those is made red by a revert below (blank, e1).
- **Green, filtered: run 42312908709** on `2c572cee7`. 37 of 37 passed.
- **Green, full: PR run 42315093612** on `5e9801df7`. Every job passed: 6,990 tests, 0 failed, 24 skipped.
- **Compared by name with pre-main's push run 42308597327** on `b6f0b464d` (the base merged in). That run had 6,838 tests.
  - None of pre-main's tests is missing here, and none changed status.
  - The 24 skips are the same tests.
  - There are 152 new tests. 115 are this branch's earlier work (2a, 2b-1 and category I), and 37 are step 1's: `NextTurnSpaceShownTest` 14, `NextTurnWordsTest` 13, `NextTurnArrowTest` 6, `NextTurnSpaceTest` 4.

### The thirteen reverts

Each was a one-line edit of `5e9801df7` in a scratch worktree, pushed to a throwaway branch and run with the filter. I wrote each prediction down before the run (`reverts.tsv` in scratch). Each compile log was checked before its results were read, and none had a compile error. The file was restored from a copy saved before the edit, and the restored file was then compared with `5e9801df7`'s: identical every time. Afterwards `next-turn`'s own worktree was clean at `5e9801df7`, and all 13 forward lines were present (`grep -cF`, 1 each). **Every revert failed in exactly the predicted count and tests**, each with a message only its own edit could produce.

| Ruling | One-line revert | Run | Failed of 37 | Message |
|---|---|---|---|---|
| C1 bands | slight left becomes the plain arrow | 42315143740 | 1 (1) | `expected:<SLIGHT_LEFT> but was:<LEFT>` |
| C2 fork | `keep == LEFT` becomes `RIGHT` | 42315153958 | 2 (2) | `expected:<FORK_LEFT> but was:<FORK_RIGHT>`; the fork's state |
| C3 straight | `NoTurnLeft` becomes `null` | 42315163253 | 4 (4) | `expected:<STRAIGHT> but was:<null>`, A3 twice, the fallback |
| E1 to E3, blank | a blank becomes `STRAIGHT` | 42315173022 | 7 (7) | `ARRIVED expected null, but was:<STRAIGHT>`; E1 four times, E2, E3: `but was:<…(arrow=STRAIGHT…` |
| C1 to C3, Material's icons | LEFT becomes `Icons.Filled.TurnRight` | 42315183095 | 1 (1) | `LEFT expected:<Filled.Turn[Lef]t> but was:<Filled.Turn[Righ]t>` |
| Its own window | `.width(spaceWidth)` becomes `.then(Modifier)` | 42315190347 | 1 (1) | `KILOMETERS: a space with width, [0.0.dp, 31.0.dp, 34.0.dp, 33.0.dp, 0.0.dp]` |
| G3 counting | `sameSidePathsBefore > 0` becomes `false` | 42315198997 | 2 (2) | `expected:<t[ake the second] left …> but was:<t[urn] left …>` |
| G3, plain only | sharp also counts | 42315206852 | 2 (2) | `expected:<[make a sharp] left …> but was:<[take the third] left …>` |
| G3, "in 400 feet" | "ft" spoken as "ft" | 42315256878 | 5 (5) | `expected:<400 f[ee]t> but was:<400 f[]t>`, `1 f[oo]t`, and three states |
| E1 | the card state is skipped | 42315278385 | 4 (4) | `expected:<…(arrow=null…)> but was:<…(arrow=LEFT, figure=394 ft…` |
| D1 units | the turn's figure always in miles | 42315285567 | 2 (2) | `expected:<…figure=120 m…>`; the walk: `expected:<[96 m]> but was:<[315 ft]>` |
| A3, the top line's string | the straight-ahead figure becomes "0 ft" | 42315290990 | 3 (3) | `expected:<0[.4 mi]> but was:<0[ ft]>`, `[64]0 m`, `[≈ 130]0 ft` |
| A logged fallback | the warning becomes `Unit` | 42315295786 | 1 (1) | `a warning logged` |

### Confirmed and inferred

- **[observed]:**
  - every run, count and message above;
  - the by-name match with pre-main;
  - the 13 forward lines present after the reverts;
  - the scanner at 0 before each push (`git diff origin/pre-main HEAD`);
  - no file shared with nav-card-kind-line.
- **[read]:**
  - The Material icons exist under these names in `material-icons-extended-release.aar` 1.7.8 (`androidx/compose/material/icons/filled/TurnSlightLeftKt.class` and the rest).
  - `formatDistanceMeters` is the top line's formatter for a trail route (`NavigationReadout.kt`, the `route is ReturnRoute.Ahead` branch).
  - `labelMedium` and `COMPASS_ICON_SIZE` are the turn line's type and icon size on nav-card-kind-line `b9fad7604` (`NavigationCardParts.kt`, `CardTurnLine`).
- **[inferred]** That the arrow draws the right way round on screen. The tests check which icon is chosen and that it is not mirrored, not the drawing itself.
- **[not verified]** Anything on a phone. The space is on no screen yet.

### Could not determine

- **Whether the space fits beside the needle (G2, G4).** That is step 2's measurement, on #498's merged layout. The window revert shows the space's figures at 31 to 34 dp in Robolectric's default font. The space's full width with `labelMedium` was not read out, and neither portrait, landscape nor font 2.0 was measured.
- **How TalkBack voices the words on a device:** "in 0.4 miles", and figures past ten such as "11th".

### Premises that were wrong

- **The base moved again.** The dispatch gave pre-main as `b6f0b464d` or later, and that held [observed]: it was merged in. During the session pre-main moved on to `26a5c5955` (PR #504 and its index row). Those commits change two tests (`EstimateOfflineTileCountTest`, `MapCameraMovesTest`), docs and the audit index, none of them this branch's.
  - **What showed it.** With it fetched, the pre-push scan `git diff origin/pre-main HEAD` read 1 (`lon`). That 1 was in `EstimateOfflineTileCountTest.kt`, the old fixture this branch still held from `b6f0b464d`, which PR #504 replaced. My own changes (`git diff b6f0b464d HEAD`) read 0.
  - **What I did.** I merged pre-main `26a5c5955` in at `d8f744907`, a clean merge of those five files only, by merge, not rebase. After it the scan reads 0.
  - **Its CI.** Full PR run **42319742136** on `d8f744907`: every job passed, 6,990 tests, 0 failed, 24 skipped. Compared by name with pre-main's push run **42315368330** on `3542a0c30` (the last code commit before `26a5c5955`, which is docs only), with 6,838 tests: none missing, none changed status, the same 24 skips, and 152 new, 37 of them step 1's.
- **The laptop's disk filled during the revert runs.** `/mnt/work` went to 100%, from things outside my scratch. My eight revert worktrees (about 137 MB each) were the last straw: the ninth `git worktree add` failed with "No space left on device". I removed my worktrees and reran the last five in one reused worktree. All 13 reverts ran and are read above. The disk stood at 99% (1.1 GB free) when I finished. **Other sessions on the laptop may hit this.**

### Decided beyond scope (each open to the owner)

- **The spelled-out units.** G3 gave "in 400 feet". I spell the others the same way, "miles", "meters" and "kilometers", in US spelling to match the app's own "Kilometers". A figure of exactly 1 is singular ("1 foot", "1 meter"). These are new words in that pattern, not ones the owner wrote.
- **"keep straight in 0.4 miles".** G3 says each set of words "is followed by the distance ("in 400 feet")", so the straight-ahead arrow reads "keep straight in 0.4 miles", where the figure is the distance to the target. **A question for the owner:** is "in" right here, or should it read "keep straight for 0.4 miles"?
- **Counting past ten.** "second" to "tenth" are spelled out. From 11 on it is the figure with its suffix ("11th", "21st").
- **The fallback.** A figure with no unit to spell out (one in a non-English number format, or the straight line's "≈" form, which the domain never pairs with an arrow) is spoken as written, with a warning logged. It is never left silent or guessed.
- **A figure of 100 mi or more** draws past the space's end. The space keeps its width, so nothing beside it moves. "88.8 mi" is the widest figure the window is sized for (Phase 1's sample).
- **My merge commit `37a988424` has git's default message and no `Co-Authored-By` line.** I did not pass `-m`. It was pushed before I noticed, and it is not amended (CLAUDE.md, push before you tidy).

### What step 2 needs

1. Merge origin/pre-main (if it has moved past `26a5c5955`) and origin/nav-card-kind-line into next-turn, by merge, not rebase, after #498's E15 Hazel hands back.
2. In `CardTurnLine`, put `NextTurnSpace(nextTurnSpaceShown(readout, hasFix = liveFix != null, ahead = nextTurn(...), unit), unit)` beside the needle (A1). The `NextTurnWay` comes from `WayHomeShown.turns` or `WaypointRouteShown.turns`, with the line and the progress the holders already keep.
3. Measure the fit on that layout, in portrait, in landscape and at font 2.0 (G2). If it is too big, stop with the measured widths (G4).
4. A coordinate-touch test that the space intercepts nothing over the map, full CI, the S22 steps, and the PR out of draft.

### Clean-up

The throwaway branches `next-turn-2b2-red`, `next-turn-2b2-green` and the 13 `next-turn-2b2-rev-*` are deleted from origin. Their runs stay readable by number. Every scratch worktree is removed. Scratch: `/mnt/work/scratch/hazel-2b2` (mode 700), holding the scripts, the saved copies and the predictions, with the downloaded test reports removed.

## I9, built: the names anchor counts only the basemap's own layers (Hazel, 2026-11-07)

Built against Amendment 1's I9 (`records-after-173` at `be77bde91`), from the S22 check on `tools/s22-combined` `505c55dda`. The starting state matched the dispatch: `next-turn` at `2902c3187`, pre-main `26a5c5955` merged in, the worktree clean. No file changed here is in `git diff --name-only origin/pre-main...origin/nav-card-kind-line`.

### What MapLibre 13.5.0 does (javap -c on the pinned AAR)

- **The ids.** `LocationComponentConstants`: `mapbox-location-shadow-layer`, `-foreground-layer`, `-background-layer`, `-accuracy-layer`, `-bearing-layer`, `-pulsing-circle-layer`, and the source `mapbox-location-source`.
- **The types and order.** `SymbolLocationLayerRenderer.addLayers` adds the shadow, background, foreground and bearing layers as `SymbolLayer`s (`LayerSourceProvider.generateLayer`), each below the one before. It then adds the accuracy `CircleLayer` below the background and the pulsing `CircleLayer` below the accuracy. With `bearingOnTop`, bottom to top: shadow, pulsing, accuracy, background, foreground, bearing; without it, shadow, bearing, pulsing, accuracy, background, foreground. Either way the top run of symbol layers starts at `mapbox-location-background-layer`, the layer the phone named.
- **When they are in `style.layers`.** `MapLibreMap.notifyStyleLoaded` calls `Style.onDidFinishLoadingStyle`, then `LocationComponent.onFinishLoadingStyle`, which re-adds the layers whenever the component was initialised on an earlier style, and only then the app's `setStyle` callback. So on every style load after the dot was first turned on, `readNamesAnchor` finds them on top. On a MapView's first style they are not there yet. This is the same chain `LiveLocationPuckSwapTest` records.

### What was built

- **`ui/map/AppLinesUnderNames.kt`.** `namesAnchorOf` and the log line now count only the basemap's own layers (`basemapOwnLayers`, `isBasemapOwnLayer`). A layer is left out when its id starts with `mapbox-location-` or is one the app adds (`APP_OVERLAY_LAYER_IDS`: the registry's own layers, not the style's band layers, plus the way home, the plotted route, the waypoint names and the fan-out). The log line names every left-out layer. `readNamesAnchor` still hands all of `style.layers` on.
- **Unchanged:** `OverlayLayerSetup.kt`, the placement rule, Trail Map's anchor (v4 `reference_fs_number` since I8, v1 to v3 `address_label`).
- **Tests (new):** `AppLinesUnderNamesForeignLayersTest` (7 tests) and `LocationLayersProbe.java`, in MapLibre's package, which hands the tests the SDK's own id constants in both orders.

### Evidence

Every run was read by `/mnt/work/scratch/hazel-i9/scripts/results.py` (the 2b-2 session's script, unchanged). It refuses a run that is incomplete, on another commit, or whose log is short or has a compile error. Filter: `AppLinesUnderNamesForeignLayersTest`, `AppLinesUnderNamesTest`, `LiveLocationPuckSwapTest`, `BasemapStyleTest`, 32 tests.

- **Red, run 42325539190** on `134678dde` (tests only). No compile errors. 5 of 32 failed, all new, each for I9's reason, as predicted before reading. For example: Topographical by day with the dot `expected null, but was:<mapbox-location-background-layer>`; `trail-style-v4.json with the position dot expected:<reference_fs_number> but was:<mapbox-location-background-layer>`; the log line `below mapbox-location-background-layer (layer 4 of 7)`. The two controls passed.
- **Full CI, green: PR run 42326986879** on `38e9d6524`. Every job passed, the Street-deleted job included. 6,997 tests, 0 failed, 24 skipped.
- **Compared by name with pre-main's push run 42315368330** on `3542a0c30`, the last code commit under `26a5c5955`, which is docs only (6,838 tests). None missing, none changed status, the same 24 skips, 159 new. Against next-turn's previous run, 42319742136 (6,990 tests), the 7 new are exactly this section's.

### The revert

One line, `basemapOwnLayers` returning `layers` unfiltered, on its own scratch worktree, pushed to the throwaway branch `next-turn-i9-rev-ownlayers` (`695779dde`) after the scan read 0. **Run 42327000547:** no compile errors. 5 of 32 failed, predicted 5: the same five as the red run, with the same messages, now with "Left out as not the map's own" in the log lines, since only the anchor's filter was reverted. The file was restored from a copy saved before the edit and compared equal with `38e9d6524`'s. Afterwards `next-turn`'s worktree was clean and the forward line was present (`grep`, 1 match).

### Confirmed and inferred

- **[observed]** The runs, counts and messages above.
- **[read, javap]** The ids, types, order and timing above.
- **[modelled, not run]** The position dot's order and types in the tests come from that javap reading, not from a running MapLibre. Its ids are the SDK's constants. `readNamesAnchor` needs a native `Style`, so it is not run, and the logcat step is what covers it.

### Could not determine

- **The phone's counts are one layer higher than the model on every style.** Topographical by day: phone 8 layers with the background layer at 5, model 7 at 4. Trail Map: phone "layer 117 of 183", the committed v4 has 182 layers with `address_label` at 117. Topographical at night: phone 190 at 187, model 189 at 186 for that Trail Map. No app code adds a layer before the anchor is read (the only `addLayer` callers are the overlay files, all after it). The extra layer is not one of the six location layers, and I could not find its source. It does not change any anchor. The new log line names left-out layers but not every basemap layer, so the next logcat will say whether it is the map's own.
- Which of the two orders the phone uses. Only the bearing-on-top order reconciles with the phone's numbers once the extra layer is counted. Both orders are tested.

### Premises that were wrong

- None in I9 itself. One finding beyond it: the same fault reached Trail Map too. After a reload (a night toggle, or coming back from another map), its anchor would also have been the dot's background layer, putting the app's lines over every Trail Map name. The phone's Trail Map line came from a first load, before the dot's layers existed. I9's fix covers this by the same rule.

### Decided beyond scope

- **"Any layer the app added"** is read as the overlay layers the app adds on a style load. The Topographical picture laid over Trail Map at night is also added by the app, through `Style.Builder`, but it is the map the user sees, and counting it is what keeps that map at today's placement. So it is counted. Open to the owner.
- **The log line names the layers it left out.** This is logcat only and changes nothing a user sees.

### S22 logcat step (repeat on the next combined build; the coder does not run it)

Tag `SightingsMap`, after switching maps at least once with the position dot on, so the style reloads:
- **Topographical by day, Street day and night, the offline style:** "App layers over the whole map (N layers), today's placement: its top layer basemap is not a name or icon layer." (the offline style names its own top layer), followed by "Left out as not the map's own: mapbox-location-…" listing the dot's layers.
- **Topographical at night:** the same, its top layer `basemap`.
- **Trail Map, after a reload:** "… below reference_fs_number …" once I8's style is live, `address_label` before that, also followed by the left-out list. Never `mapbox-location-…` as the anchor.
- Note N against the counts above, for the one-layer difference.

### Clean-up

The throwaway branch `next-turn-i9-rev-ownlayers` is deleted from origin and its worktree removed. Its run stays readable by number. Scratch: `/mnt/work/scratch/hazel-i9` (mode 700).

## I10, built: the basemap's own layers are the ones its style file declares (Hazel, 2026-11-07)

Built against Amendment 1's I10 (`records-after-173` at `077e6c6d5`), from the S22 check on build 4825 (`tools/s22-combined` `3529c63ff`). The starting state matched the dispatch: `next-turn` at `34eff5f8d`, the worktree clean, pre-main at `1f6901a11`. No file changed here is in the changed files of `nav-card-kind-line` or any other open PR's branch (checked before the edits and again before the merge). pre-main `1f6901a11` (PR #505) was merged in (`b4d50993c`), with no shared file.

### What MapLibre 13.5.0 adds (javap and strings on the pinned AAR)

- **The position dot** (I9's reading, unchanged): six layers, `mapbox-location-shadow-layer`, `-foreground-layer`, `-background-layer`, `-accuracy-layer`, `-bearing-layer`, `-pulsing-circle-layer` (`LocationComponentConstants`), symbol and circle layers, added on top on every style load after the dot was first turned on.
- **The annotations, from the native core.** `MapLibreConstants.LAYER_ID_ANNOTATIONS` = `org.maplibre.annotations.points`. `strings` on `libmaplibre.so` (arm64-v8a) finds exactly three `org.maplibre.` ids: the source `org.maplibre.annotations`, the points layer, and the prefix `org.maplibre.annotations.shape.` (one layer per polyline or polygon annotation; the app adds none, `grep` for `addMarker`, `addPolyline`, `addPolygon` in `app/src/main` finds nothing). The points layer is in the style before `Style.Builder`'s layers: `Style.onDidFinishLoadingStyle` adds each plain builder layer with `addLayerBelow(layer, "org.maplibre.annotations.points")` (`MapSnapshotter` does the same). `strings` on the Java classes finds no other run-time layer id.
- **The extra layer I9 could not place is this one.** The phone's counts are now all accounted for: Topographical by day, 1 picture layer + the points layer = "1 of 2"; with the dot, + 6 = "5 of 8". Topographical at night, Trail Map's 182 + the picture + the points layer = "183 of 184"; with the dot, "187 of 190". Trail Map, 182 + the points layer = 183.
- **The style file's own layer list is readable.** `Style.getJson()` returns `NativeMap.getStyleJson()` (`NativeMapView.getStyleJson` returns `""` on a destroyed map). What the native core returns there, the file the style was loaded from rather than the style as it now stands, is native code and not readable from the AAR's Java; it is the one premise of this build the logcat step checks.

### Which rule, and why

- **The rule: a layer is the map's own when the style file declares it** (`StyleFileLayers.kt`, `styleFileLayersOf(style.json)`, decoding only `layers[].id`), or it is the Topographical picture the app's builder lays over Trail Map at night (`BUILDER_BASEMAP_LAYER_IDS`, which I9 already counted as the map, so that map keeps today's placement). Why this over a longer prefix list: it leaves out every layer added at run time whatever its id, so a layer a later MapLibre adds under a name nobody listed cannot become the anchor; the prefix list only covers ids someone has read.
- **The id check stays beside it**, with `org.maplibre.` added to `mapbox-location-` (`MAPLIBRE_LAYER_ID_PREFIXES`) and the app's own layers as before: a layer is never the map's own if its id says MapLibre's or the app's, even if the file listed it. This covers the one unverified premise above: if `getJson()` ever returned the style with its run-time layers, the anchor would still be right for every layer known today.
- **When the file cannot be read** (blank, unparseable, no layers declared) the anchor falls back to the id check alone, and the log line says so and why, at warning level. An empty list is never taken as "no layer is the map's own".
- **The log line** now also says how the own layers were told ("those its style file declares (N)", or the fallback and its reason), and how long the file took to read, in ms: Trail Map's file is about 1.5 MB and is read on the style-load callback, and that cost on a phone is not measured anywhere else.

### What was built

- `ui/map/StyleFileLayers.kt` (new, 49 lines): `StyleFileLayers` (`Read` with the ids, `Unreadable` with a reason) and `styleFileLayersOf`.
- `ui/map/AppLinesUnderNames.kt`: `namesAnchorOf`, `basemapOwnLayers`, `isBasemapOwnLayer` and `namesAnchorLogLine` take the style file's reading; `readNamesAnchor` reads it from `style.json`, times it, and logs. The placement rule, `OverlayLayerSetup.kt` and Trail Map's anchor are unchanged.
- Tests: `AppLinesUnderNamesMapLibreLayersTest` (new, 7 tests). `AppLinesUnderNamesTest` passes each style's own ids as its file (Topographical at night reads the real Trail Map file, so the picture is held by the builder rule); `AppLinesUnderNamesForeignLayersTest` makes every I9 check both ways, the file read and unreadable. Neither lost an assertion.

### Evidence

Every run was read by `/mnt/work/scratch/hazel-i10/scripts/results.py` (I9's script, unchanged), which refuses a run that is incomplete, on another commit, or whose log is short or has a compile error. Filter: `AppLinesUnderNamesMapLibreLayersTest`, `AppLinesUnderNamesForeignLayersTest`, `AppLinesUnderNamesTest`, `LiveLocationPuckSwapTest`, `BasemapStyleTest`.

- **Red, run 42338136946** on `8e1975c2a` (the tests, and the new parameter unused). No compile errors. 4 of 38 failed, all in the new class, as predicted before reading, each for I10's reason: Topographical by day, file read, `expected null, but was:<org.maplibre.annotations.points>`; the log line "go below org.maplibre.annotations.points (layer 1 of 2)", the phone's own words; the unknown-layer test also anchored under the points layer; the prefix list `[mapbox-location-]`. The Trail Map control and the reader test passed.
- **Green, run 42339357192** on `7ce3979b6`: 39 of 39 passed (the seventh new test, a control that no served style declares an id under MapLibre's prefixes, was added with the rule).
- **Full CI, green: PR run 42339403749** on `b4d50993c` (the merge of pre-main). Every job passed, the Street-deleted job included. 7,012 tests, 0 failed, 24 skipped.
- **Compared by name with pre-main's push run 42337576510** on `1f6901a11` (6,846 tests): none missing, none changed status, the same 24 skips, 166 new. Against next-turn's previous full run, 42326986879 on `38e9d6524` (6,997): none missing or changed, 15 new, exactly this section's 7 and pre-main's 8 (`ReCenterOutlinePortraitTest` 5, `ReCenterOutlineLandscapeTest` 3).

### The revert

One line, the style-file rule in `isBasemapOwnLayer` replaced by a comment, on its own scratch worktree, pushed to the throwaway branch `next-turn-i10-rev-stylefile` (`e3208fb8b`) after the scan read 0. Predicted before reading: one failure, the unknown-layer test on a picture map. **Run 42339369585:** no compile errors; 1 of 39 failed, `OPEN_TOPO_MAP night=false: a-layer-no-prefix-names is not in the style file expected null, but was:<a-layer-no-prefix-names>`, a failure only this revert could produce. The annotation tests stayed green, since the id check also leaves the points layer out. The file was restored from a copy saved before the edit and compared equal with `7ce3979b6`'s; afterwards `next-turn`'s worktree was clean and the forward line was present (`grep`, 1 match).

### Confirmed and inferred

- **[observed]** The runs, counts and messages above.
- **[read, javap and strings]** The ids, the builder's placement below the points layer, `getJson()`'s Java path.
- **[inferred, fits every phone count]** The native core adds the points layer on top of the style file's layers on every load, before the builder's and the dot's. Nothing native was run.
- **[modelled, not run]** `readNamesAnchor` needs a native `Style`; the logcat step covers it.

### Could not determine

- **What `Style.getJson()` returns on the phone**: the file as loaded (as built for) or the style as it now stands. Either way the anchor is right for every layer known today; only the first makes the unknown-layer protection real. The log line's "declares (N)" settles it: N equal to the style file's own count (1 for Topographical by day, 182 for Trail Map v4) is the file.
- **The read's cost on a phone** for Trail Map's 1.5 MB file. The log line times it.

### Premises that were wrong

- I9's report: "The extra layer is not one of the six location layers, and I could not find its source." It is MapLibre's annotation layer, the layer I10 names.
- None in I10.

### Decided beyond scope

- **Both rules, not one.** I10 asks to prefer the file over prefixes. The file is the rule, and the prefixes (with `org.maplibre.` added) stay as a second guard and as the logged fallback, so the unverified native premise above cannot put the lines under a MapLibre layer. Open to the owner.
- **The picture at night counts as the map's own** though the file does not declare it, as in I9.
- **The log line says how the layers were told and times the read.** Logcat only; nothing a user sees.

### S22 logcat step (repeat on the next combined build; the coder does not run it)

Tag `SightingsMap`, after switching maps at least once with the position dot on, so the style reloads:
- **Topographical by day, Street day and night:** "App layers over the whole map (K layers), today's placement: its top layer basemap is not a name or icon layer. The map's own layers are those its style file declares (K). Left out as not the map's own: org.maplibre.annotations.points, mapbox-location-…" and "Style file read in N ms." K is 1 for a one-picture style (`singleLayerStyleJson`) and 2 where `BasemapStyles.kt` builds a night style with a second picture under it (line 207); I did not trace which of Street's styles takes which, so K is whatever the two numbers in the line agree on, and they must agree.
- **Topographical at night:** "(183 layers)", top layer `basemap`, "declares (182)", the same left-out list.
- **The offline style:** today's placement, its own top layer, "declares (57)".
- **Trail Map:** "below address_label (layer 117 of 182)" until I8's style is live, then "below reference_fs_number (layer 116 of 182)"; "declares (182)". Never `org.maplibre.` or `mapbox-location-` as the anchor.
- Note the ms figure on Trail Map. If any line says "could not be read", or "declares" a number other than the ones above, report it: the native premise did not hold.

### Clean-up

The throwaway branch `next-turn-i10-rev-stylefile` is deleted from origin and its worktree removed. Its run stays readable by number. Scratch: `/mnt/work/scratch/hazel-i10` (mode 700).
