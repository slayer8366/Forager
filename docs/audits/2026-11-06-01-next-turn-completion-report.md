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
| ⟨REPORT⟩ | This report. |

Everything is pushed. The worktree is clean.

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
  - **The count.** `sameSidePathsBefore` counts the other ways leaving on the turn's side at the straight-on junctions from the walker's place to the turn. The coordinator's message gave the owner's ruling, "When paths come first (Recommended)". It is given for every band; which bands' words use it is Phase 2b's.
  - **The card's states (E1).** `nextTurnSpace(card, ahead)` blanks the space for every card state but a figure: Arrived, the dash, no fix, within the fix's circle. Phase 2b says which state the card is in, from its own readout.
  - **The way's states.** A way being worked out, a way with no trail route, junctions still being read, an unread read and a walker not yet placed each have their own reason.
- **The M1 line,** `RouteTurnsLog.kt` and `RouteTurnsLogLine.kt`.
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
M1 next turns, way to a waypoint: ways read in 12 ms (2 reads, 9 ways); 7 judged: 2 turns, 1 fork, 2 straight on, 1 no leg, 1 unmatched. #1 node 3, 31 m, join (start) of 3 ways: NO_LEG | #2 node 12, 119 m, junction of 3 ways: turn 4°, margin 86°, STRAIGHT_ON, paths 1 left 0 right | #3 node 20, 201 m, junction of 4 ways: turn -2°, margin 88°, STRAIGHT_ON, paths 1 left 1 right | #4 node 26, 260 m, junction of 4 ways: turn -90°, margin -90°, TURN, plain left, 2 same-side paths before | #5 node 31, 310 m, junction of 3 ways: turn 6°, margin 30°, FORK, keep right | #6 node 40, 400 m, junction of 3 ways: UNMATCHED | #7 node 44, 440 m, join (end) of 3 ways: turn 151°, margin -151°, TURN, sharp right, 0 same-side paths before
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
  - for a straight-on junction, the paths it adds on each side;
  - for a turn, the same-side paths since the last turn, fork or unmatched junction. That is the count TalkBack would read as the turn becomes the next one.

Every figure is a whole number, so no coordinate can appear. Two tests check for the absence of any decimal: one on the line from made-up ways, and one on the fixture's real route. A route too long for one log message is split into numbered parts, "(1 of 3)", each under 3,500 characters, and no point is lost; a test pins this. An unread route reads `M1 next turns, way home: ways unread (IOException) after 5 ms (1 read).`, with the failure's class and never its message, since that can name a route square.

### Evidence

**The tests,** 62 in five classes:
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
- **The full CI on the PR head `8051a72f4`:** ⟨FULLCI⟩

**Revert checks** [observed]. How each was run:
- **The edit.** One line per check, with the predicted failures written down before the run (`/mnt/work/scratch/next-turn-2a/scripts/reverts.py`, deleted with the scratch).
- **The run.** Committed to its own throwaway branch from `8051a72f4`, and run on CI on the five new classes.
- **The restore.** The file was restored from the copy saved before the edit, never from git. The tree was then checked equal to `8051a72f4` before HEAD moved back.
- **Reading the results.** Each run's build log was checked for compile errors before its JUnit XML was read.
- **Afterwards.** The forward change was confirmed present in the branch worktree (`git diff --quiet origin/next-turn`, and the B1 line counted).

⟨REVERTS⟩

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
- **So the data supports the count** [inferred from those figures]. The open question is the meaning of "paths", below.

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
4. **The dispatch's premises held** [observed]: the base, RouteWays' production caller, and the fixture. Q3's option said that where "the way is a straight line" the space shows "A straight-ahead arrow", while this dispatch says "a way with no trail route" is blank. That is a possible conflict, not a wrong premise: see Open items.

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
- **The count counts every way,** and at a junction with two ways on one side it counts two.
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

**Before:** a debug build from this branch's CI (the `app-debug-apk` artifact of the run on the PR head), installed on the S22, with the owner's area downloaded with its trail routes.

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
   - **`same-side paths before` on each `TURN`:** would the owner count those paths, given that every way counts, driveways and roads included?
4. **What else to note.**
   - `UNMATCHED` and `NO_LEG` counts. Any `UNMATCHED` means the space would be blank there.
   - The read time and number of reads in the header (M2).
   - Any `M1 ... ways unread` line.
5. **Privacy.** The M1 line holds no position. The warning written beside an unread line passes the reader's own exception, whose message can name a route square: don't copy that warning into anything shared.

## Open items for Phase 2b

- **A possible conflict, for the planner or the owner.** For the straight line to a waypoint, Q3's option said that where "the way is a straight line" the space shows "A straight-ahead arrow". This dispatch says "a way with no trail route" is blank. 2a returns `Blank(NO_TRAIL_ROUTE)` for it, as dispatched. The way home's track fallback is blank by the owner's own E2 words either way.
- **"Paths", for the owner.** The count counts every way leaving on the turn's side: on the fixture's side paths, 192 service ways, 97 unclassified roads, 67 residential streets and 43 motorway, motorway-link or trunk roads among them. "Take the second left" past a driveway may not be what the owner means. The planner's reading, that the count is for plain turns only, is also still open.
- **Wiring the card.** It needs the line's turns where the line is held: `WayHomeWatch` and `AvailabilityWaypointRoute`, each with `routeTurnsFor` and the route data folder. Today the folder reaches only the use cases' hook. 2b will also need to keep the M1 line and the card on the same turns.
- **The card's E1 state.** It maps its readout to `NextTurnCardState`. `NoTurnLeft` shows the top line's own figure (A3).
- **Owner rulings for 2b.** G1, the TalkBack words, and the fit the owner commented on ("if it is too big bring it up then") were given to the coordinator. The words need the count, as above.

## Clean-up

⟨CLEANUP⟩
