# Dispatch 2026-11-06-01, Phase 2a: hand-back (Hazel Cairnwright)

Preserved by the planner from the hand-back message, verbatim. The full report is docs/audits/2026-11-06-01-next-turn-completion-report.md on branch next-turn (draft PR #500). RECORD -2183.

## Phase 2a of dispatch 2026-11-06-01: done, on draft PR #500

The next-turn domain and the M1 log line are built on `next-turn`, with Amendment 1's B5 (walkable ways only) in. Nothing a user sees changes. CI is green on the branch head, and the full suite matches pre-main by test name plus 68 new tests. The card's space and arrow are Phase 2b.

- **Base:** origin/pre-main was `6cfcd8d6c`, as you said. It moved to `d02d2e74a` (CLAUDE.md only) during the work; I merged it in at `8051a72f4`, not rebased.
- **Head:** `64be65216`. The code head is `7c1e051aa`; the commit after it adds only the report.
- **PR:** #500 into pre-main, still a draft. Its body says Phase 2b (the card) is still to come.
- **Report:** `docs/audits/2026-11-06-01-next-turn-completion-report.md`, with the four disclosure sections, the revert table and the S22 M1 check. No row was added to `docs/audits/README.md`.

### What was built
Eight new files in `domain/nextturn/` plus `PathsPassed.kt`, each under 200 lines. Four existing files changed, none of them changed on `nav-card-kind-line` or `home-location-out` (I checked before each edit):
- `FindWayToWaypointUseCase` and `FindWayHomeUseCase` take an optional `trailRouteFound` hook;
- `WayHomeRouting` passes it on;
- `AppContainer` wires it to the log line.

What the code does:
- **Junctions (F1):** read through `RouteWays`, one read round each 1 km stretch of the route. The vendored BRouter is untouched.
- **Turn rule (B1):** R(30°, 30°), measured 20 m either side, every way counted.
- **Joins (B2), forks (B3), dog-legs (B4), bands and fork side (C1, C2):** as ruled.
- **Distance (D1):** counted from the needle's place, never below 0. A turn is passed only when the walker's place passes its node.
- **Same-side count:** counts walkable ways only (B5). The ways it leaves out (`service`, `motorway`, `motorway_link`, `trunk`, `trunk_link`) are counted separately. The turn rule still counts every way.
- **Empty states (E1, E2):** each has its own result. "No turn left" is returned only when every junction ahead is known.
- **Unknowns:** a junction that can't be matched to the data, or a lead-in with no length, gets its own class.

`nextTurn` and `nextTurnSpace` have no production caller yet; that is Phase 2b's wiring. The M1 line does fire on a real build: at the first search for a waypoint or trip, at every re-route, and at every Return.

### Evidence
Every run's build log was checked for compile errors before its results were read: 0 in each.

| Run | Commit | Result |
|---|---|---|
| 42222073533, red | `71cf9b936` (stubs) | 53 of 62 red, each for its own rule. The 9 negatives passed on the stubs, as expected. |
| 42225034423, green | `ee7398388` | 61 of 62. The one red was my own D1 test's wrong premise, fixed at `ae1317e86` (below). |
| 42230249460, B5 red | `d8d2f6734` | 5 B5 tests red, each for its own reason. |
| 42227417395, full | `8051a72f4` | 8 of 8 jobs green, 6,898 tests. |
| 42234175132, full | `7c1e051aa` | 8 of 8 jobs green, 6,904 tests. |
| 42238904353, full | `64be65216` | 8 of 8 jobs green, 6,904 tests. |

- **Counts by name:** I compared against pre-main's run 42202397416 (at `6cfcd8d6c`) and run 42225728551 (at `d02d2e74a`), both 6,836 tests. The head has 6,904: 6,880 passed, 24 skipped, 0 failed. No pre-main test is missing, none changed status, and the 24 skipped are the same 24. The 68 new tests are exactly the six new classes.
- **Revert checks:** 25 one-line reverts, each with its failures predicted before it ran. Each ran on its own throwaway branch, was restored from a saved copy, and the tree was checked against the forward commit afterwards. 22 failed exactly as predicted. Three failed one test more, and in each case the extra failure comes from that same edit, not a stale run:
  - **r04 (three or more ways):** judging every two-way point makes the line long enough to split, and the fixture's 3 M1 tests count messages.
  - **r20 (reads along the route):** same splitting cause; the reversed fixture route's single read leaves its far junctions unread.
  - **r23 (B1 counts every way):** also failed B1's own "every way counts" test, whose straight-on way is `highway=service`. I missed that when predicting.
  - **One negative has no revert:** "a straight line logs nothing" is an absence claim with no line to revert.
- **Fixture numbers for the count**, from 195 of Phase 1's 200 routes, rebuilt without writing down any coordinate:
  - Dead ends within 50 m change the count on 3 of 1,220 plain turns, so the data supports it.
  - B5 changes the count on 44 of those 1,220 turns. 37 of them drop to 0, so they read "turn left" where they read "take the second left".
- **Privacy:** the diff, the probe output, the PR body and the report were checked against the private home patterns: 0 matches.

### Premises that were wrong (all mine, in tests)
- **The D1 test** assumed the walker's line has a node every 10 m exactly. A 250 m stretch got 26 steps, so CI read 151.923 where the test expected 150. The tests now read their expectation from the line's own nodes.
- **The made-up route reader** returned every way within 5 km, so the reads-along-the-route test could not fail. It now returns only ways near each read.
- **The winding-through test** was held back from being a turn by both thresholds, so no single revert could turn it red. It now isolates the bend threshold.

### The M1 line
One line per route at info level, tag `NextTurn`. It has no positions; every figure is a whole number. Example, pinned exactly by a test:
`M1 next turns, way to a waypoint: ways read in 12 ms (2 reads, 9 ways); 7 judged: 2 turns, 1 fork, 2 straight on, 1 no leg, 1 unmatched. #1 node 3, 31 m, join (start) of 3 ways: NO_LEG | #2 node 12, 119 m, junction of 4 ways: turn 4°, margin 86°, STRAIGHT_ON, paths 1 left 0 right, not paths 1 left 0 right | … | #4 node 26, 260 m, junction of 4 ways: turn -90°, margin -90°, TURN, plain left, 2 same-side paths before, 1 same-side way not counted | …`

A line longer than 3,500 characters is split into numbered parts. The report gives the S22 steps: where the line appears, how to read it, and what to compare against the owner's own judgement of which junctions are turns. The APK to use is from run 42234175132, whose build had the routing app key set. One caution: the warning written beside an "unread" line can name a route square, so don't copy that warning into anything shared.

### Open items for Phase 2b
- **Plain turns only?** Your reading that the count is for plain turns only is still open to the owner. 2a gives the count for every band, so the words can follow either answer.
- **Card wiring:** the card needs `routeTurnsFor` where the line is held (`WayHomeWatch`, `AvailabilityWaypointRoute`), and the route data folder there too. Today only the use cases' hook gets the folder.
- **Card states:** the card maps its readout to `NextTurnCardState` (Arrived, the dash, no fix, within the fix's circle).
- **Straight-ahead arrow:** `NoTurnLeft` shows the top line's own figure, and only on a trail route (E3).
- **Words:** the owner's TalkBack words (G1) and the count's "take the second left" go into the words layer.
- **Choices made beyond the dispatch:** the report lists them, for you to confirm or change. Examples: reads of 1 km, 1 m counting as "on a way", a lead-in with no length gives no turn, and the way home's track to the start always counts as unknown, even when it is only the leg to the origin.

### Clean-up
- **Throwaway branches deleted from origin:** all 25 revert branches, both probe branches and the draft-backup branch. Their CI runs stay readable.
- **Scratch:** the scratch worktrees and `/mnt/work/scratch/next-turn-2a/` are deleted.
- **Left in place:** the branch worktree `~/.cache/forager-wt/next-turn`, for Phase 2b.
- **Not touched:** no other branch, no git stash, no Gradle on the laptop, and your worktree (status clean).
