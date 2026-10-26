# Review of PR #412: step 6 PR 2, the way to a waypoint along trails, in the domain

Sorrel Tally (reviewer, Fable 5.1), fresh session, 2026-10-26, written 08:18 UTC and after. For Rowan Waymark.
Read-only on code; no build was run on the laptop, nothing was pushed but this file (branch `sorrel-412`).

**Reviewed:** PR #412, branch `waypoint-route-domain`, commits `2f76a2cf2` (tests over stubs) and `9d0b7f8f9`
(the build), off pre-main `85f05c784`. Against dispatch `prompts/preserved/2026-10-26-10.md`
(`records-after-173`), RECORD -1652, -1653 and -1662 (`records-after-173`, `RECORD.md:28099-28116` and
`:28209-28214`), and the step 6 report `docs/audits/2026-10-25-03-waypoint-along-trails-report.md` §4 "PR 2"
(branch `waypoint-trails-report`).

Every claim below is marked **read** (the file at the hash named), **computed** (from a run's artifacts or a
command), or **inferred** (reasoned from code, not seen running). Line numbers are at `9d0b7f8f9` unless
another hash is named.

## 0. Baseline (step 1 of the reviewer profile)

| Claim in the dispatch to me | Checked | Result |
|---|---|---|
| PR head `9d0b7f8f9`, off `85f05c784` | `gh pr view 412`; `git merge-base origin/pre-main 9d0b7f8f9` | matched (computed) |
| pre-main now `7b388b599` | `git log -1 origin/pre-main` | matched; one commit since the base, `9bead6f4c`, touching only `MushroomLogViewModelWiringTest.kt` (computed) |
| Clean merge with current pre-main | `git merge-tree --write-tree origin/pre-main 9d0b7f8f9` | a tree, no conflict output (computed); GitHub reports `MERGEABLE` (read) |
| Fail-first run 40287085019 | run metadata and JUnit XML | `2f76a2cf2`, failure; 21 tests, 20 failed (computed) |
| PR run 40287886244 green, 5,606 | JUnit XML, 756 files | **5,606 tests, 0 failures, 0 errors, 24 skipped** (computed; the 24 skips are the standing CI allowlist, `AvailabilityScreenMapIconStackTest` and friends plus `GenerateFungiIndexDbAsset`) |
| Reverts R1-R6 on `waypoint-route-domain-revert-1..6` | parents and diffs | each branch is one commit whose parent is `9d0b7f8f9`, each a one-line edit (computed) |
| Tests unchanged between fail-first and build | `git diff 2f76a2cf2 9d0b7f8f9 -- app/src/test` | empty (computed): the tests seen failing are the tests that passed |

No mismatch; the review proceeds.

## 1. Mechanism prediction (written after reading the dispatch and the PR body, before reading the engine)

1. The one-run design (route walker → waypoint, read its first and last points) is equivalent to "snap both
   ends, then route" because the snapper is itself a route from the point to itself through the same matcher
   (`BRouterTrailSnapper`), so both designs ask the same code the same question. Any divergence will be at the
   engine's internal boundaries, not in ordinary terrain.
2. The way home's behaviour is unchanged: the engine moved, the use case did not.
3. The real-engine tests pin one off-trail point on one trail and nothing about junctions or two trails.

Scored in §9.

## 2. Question (1): one engine run, its ends read as the trail points

### What the code does (read)

`FindWayToWaypointUseCase.kt:69` routes `from` → `waypoint` once; `:78-79` takes `route.points.first()` as the
walker's trail point and `route.points.last()` as the waypoint's; `:82` and `:84` build the two `STRAIGHT`
legs from them. No `TrailSnapper` call is made (`engine.snapper` is unused in this file; the test fake hands an
unused `FakeSnapper(emptyList())`, `WayToWaypointFakes.kt:67`).

### How the engine matches an end (read, vendored BRouter v1.7.10 at `third_party/brouter`)

- `RoutingEngine.matchWaypointsToNodes` (`RoutingEngine.java:1609-1631`) calls
  `NodesCache.matchWaypointsToNodes(…, range = waypointCatchingRange, …)`; the range is the profile's
  `waypointCatchingRange`, default **250** (`RoutingContext.java:143`); `hiking-mountain.brf` does not set it
  (grep of `app/src/main/assets/routing/profiles2/hiking-mountain.brf`: no match). With `use_dynamic_range=0`
  (`BRouterTrailRouter.kt:192`) the wider retry at `:1615-1624` is skipped and an unmatched end throws
  `"<from|to>-position not mapped in existing datafile"` (`:1627-1629`), which `BRouterFailures.kt` reads as
  `EndOutsideData(START|END)`.
- `WaypointMatcherImpl.checkSegment` (`WaypointMatcherImpl.java:77-156`) is fed every segment of every
  **profile-allowed** way in each decoded tile (class comment `:12-13`), for **every** waypoint in the request
  (`:92`). Each waypoint keeps the segment with the smallest perpendicular distance (`:117-135`), starting from
  `radius = 250` (`:50`), and its crosspoint is the foot on that segment (`:139-153`). The candidate list is
  sorted by **radius first**, direction difference only on an exact tie (`:66-73`, `:254-259`), and the best is
  taken (`:229-235`).
- So each end is matched **independently**, to the geometrically nearest allowed way among the segments the
  matcher saw, within 250 m. "Toward the other end" plays no part except on an exact tie of distance, and on
  an exact tie at a junction node both ways give the same node as the crosspoint, so the trail *point* is the
  same either way (inferred from `:139-153`).

### Is it the same point a snap would give? (read, then inferred)

- The snap is `BRouterTrailRouter.firstRoutePoint(point, point, data)` (`BRouterTrailSnapper.kt:28`,
  `BRouterTrailRouter.kt:89`): the same request path, the same matcher, two waypoints at one position. The
  route's first point and the snap's point both come from the start waypoint's crosspoint cut into its link
  (`RoutingEngine.java:1822`, `OsmPath.java:241-262`), so for the walker's end the two designs run the same code
  on the same inputs. `BRouterTrailSnapperTest` pins first point == snap for `OFF_TRAIL` (`:64-70`), and the new
  `FindWayToWaypointOnBRouterTest` pins the same for the lead-in (`:68-73`, exact equality).
- The waypoint's end is the route's **last** point, which the engine cuts into the final link at the end
  waypoint's crosspoint with a 1.5 m radius (`RoutingEngine.java:1918`, `:2215-2216`; `OsmPath.java:241-244`).
  That is a second projection of the same crosspoint, computed in the link's direction of travel, so it can
  differ from the snap's start-cut of the same point by integer rounding. The coder's test needed **1 m of
  tolerance** there (`FindWayToWaypointOnBRouterTest.kt:84`) where the lead-in test uses equality (`:72`); that
  asymmetry is consistent with this reading (inferred; I did not print the two points).
- **Where the two designs can differ (inferred, not observed):** the matcher only sees segments in tiles that
  were loaded while it was active. `NodesCache.matchWaypointsToNodes` (`NodesCache.java:292-314`) preloads
  **only the end's own micro-cache tile** first (`preloadPosition(…, 12500, 1, false)`, `:296`), and loads the
  3×3 neighbourhood only if nothing matched (`:298-300`; the `radius > RETRY_RANGE` arm cannot fire with
  dynamic range off, since the radius starts at 250 and only falls). A tile is `1,000,000 / divisor`
  micro-degrees across (`OsmFile.java:48`; divisor from the file header, 32 in BRouter's own builds → about
  3.5 km N-S). So an end within 250 m of a tile edge, with a way at, say, 200 m in its own tile and a nearer
  way across the edge, is matched to the 200 m way. In the **one-run design both ends' tiles are decoded through
  one matcher** (`checkSegment` loops over both waypoints), so the walker's match also sees the waypoint's
  tile; a self-snap sees only the walker's tile. Where that matters the route's first point can be *nearer*
  the walker than a snap would be. This favours the one-run design and is a divergence from "snap then route"
  only at micro-cache edges, with two trails within 250 m on opposite sides of the edge. I have not seen it on
  data and did not construct a case.
- **Junctions:** nearest segment wins; at a junction node the crosspoint is the node whichever way ties. Same
  in both designs.
- **A nearer trail in the wrong direction (or disconnected):** the nearest way is matched regardless of
  direction (radius sorts first). If it is a dead fragment, the island check (`RoutingEngine.java:949-966`,
  `MAXNODES_ISLAND_CHECK = 500`, `:50`) throws "target island detected" / "start island detected" →
  `NoPath` → `NO_ROUTE_FOUND`, "Couldn't find a trail route · straight line". The engine does **not** re-match
  to the next-nearest way. "Snap then route" would fail the same way. This is a limitation shared by both
  designs and is what Q1 (a) rules ("nearest trail point"), not a divergence; worth knowing for PR 3's device
  check: a walker beside a short disconnected spur gets the straight line with "Couldn't find a trail route".
- **Profile-excluded ways** are invisible to the matcher (`WaypointMatcherImpl.java:12-13`), so "nearest trail"
  means nearest *routable* way under `hiking-mountain`. Same in both designs.

### What the real-engine tests do and don't show (read)

`FindWayToWaypointOnBRouterTest` (93 lines) runs the vendored engine on the Harriman fixture: (a) from
`OFF_TRAIL` (34 m off one trail) the lead-in ends exactly at the snap of `OFF_TRAIL` and the trail starts
there (`:68-74`); (b) to `OFF_TRAIL` as the waypoint, the final leg starts within 1 m of its snap and is 34.3 m
(`:78-86`); (c) `NO_TRAIL_NEAR` as either end gives the end's own note (`:89-92`). They show the premise on **one
off-trail point beside one trail**, both as start and as end, and the end-naming of an unmatched end. They do
**not** show a junction, two trails within 250 m, a tile edge, or an island. The fake-engine tests cannot show
any of this: `FakeTrailRouter.nearestFoot` *is* "nearest foot within 250 m" by construction
(`WayToWaypointFakes.kt:42-45`), so they test the use case's reading of the engine, not the engine.

**Answer to (1):** equivalent to "nearest trail point" in every case I can trace except micro-cache tile edges,
where the one-run design sees more tiles than a self-snap and can only do better; ties and junctions give the
same point; a nearer wrong-direction or disconnected trail is matched in both designs and ends in "Couldn't
find a trail route" rather than a route via the next trail. The choice of one run over three is sound and
cheaper (one BRouter lock acquisition instead of three, `BRouterTrailRouter.kt:123`). Not blocking.

## 3. Question (2): `domain/routing/TrailEngine.kt`

Read, 23 lines. `TrailEngine(router, snapper)` is a plain holder; `TrailEngineSource.engine(): Result<TrailEngine>`
is the interface the domain depends on, with the contract "built on first call, the same one after, a failure
keeps nothing, the caller logs". `TrailRoutingEngine.kt` (50 lines) implements it: one `Mutex` (`:35`), one
`built` (`:36`), read and written only inside `withLock` (`:38-48`), install on `ioDispatcher` (`:41`),
`CancellationException` rethrown (`:42-43`), failure returned not kept (`:45`). This is the old
`WayHomeRouting.find` body (`85f05c784:WayHomeRouting.kt:42-55`) moved with its lock and dispatcher, minus the
log line, which moved to the callers, exactly as the TrailEngine doc says. The domain file names the vendor
only in a comment (`TrailEngine.kt:6`), not in code; the Architecture rule is kept. Fine. One nit: the domain
doc's claim that a second engine "would hold a second copy of the engine's profile in memory" I did not verify
(BRouter's `ProfileCache` is static per `BRouterTrailRouter.kt`'s class doc, which argues the opposite); it is
a comment, not behaviour.

## 4. Question (3): `WayHomeRouting`, old constructor kept, a new `FindWayHomeUseCase` per call

Read, both versions side by side (`WayHomeRouting.kt` at `85f05c784` and `9d0b7f8f9`).

- **Built lazily, once, shared:** before, `building.withLock { findWayHome ?: build }` cached the *use case*
  (`85f05c784:WayHomeRouting.kt:42-55`). Now `engines.engine()` caches the *engine* in `TrailRoutingEngine`
  (`:38-48`) and `WayHomeRouting.find` constructs a `FindWayHomeUseCase` over it on every call (`:51`).
  `FindWayHomeUseCase` has no state beyond its four constructor `val`s (`FindWayHomeUseCase.kt:52-57`; no other
  properties in the class), so a fresh instance per call is behaviourally identical to a cached one. The
  router and snapper inside it are the shared engine's (`TrailEngine.router/.snapper`), built once.
  `AppContainer.kt:440-441` builds one `TrailRoutingEngine` and hands it to `WayHomeRouting` and (`:447`) to
  `FindWayToWaypointUseCase`. `TrailRoutingEngineTest` (`:34-56`) pins one install, one router, `assertSame`
  on two `engine()` calls, with both features calling. Revert R3 (drop `.also { built = it }`) fails that test
  **and** the way home's own `WayHomeRoutingTest` "installed once" (expected 1, was 2): the existing way-home
  test now bites on the shared engine (computed from run 40288000867's XML).
- **Order of work preserved:** engine asked for first, then the use case reads the track and regions, as
  before (`:47-51` vs the old `:42-56`). Same log words on a failed install (`:48` vs old `:49`).
- **Lock order:** old, `WayHomeRouting.building` held only while installing the profile and constructing
  `BRouterTrailRouter` (a field-only constructor, `BRouterTrailRouter.kt:71-76`), released before any route.
  New, `TrailRoutingEngine.building` held for the same span (`:38-48`), released before `find` routes.
  BRouter's static `ONE_ROUTE_AT_A_TIME` (`BRouterTrailRouter.kt:201`) is taken inside `runEngine` (`:123`)
  with neither mutex held, in both versions. **No nesting before or after; the lock order is unchanged.**
  With two features sharing, a way-home find and a waypoint find in flight together serialise on `building`
  for the build and on `ONE_ROUTE_AT_A_TIME` for the route; neither holds the other. (Read.)
- **Default parameters:** `AppContainer` passes no `method`, so `TrailSnapMethod.SNAP` as before
  (`TrailRoutingEngine.kt:32`; old `WayHomeRouting.kt:35`). `Dispatchers.IO` as before.
- **The old constructor** (`:37-44`) builds a private `TrailRoutingEngine` so `WayHomeRoutingTest` compiles
  unchanged (`WayHomeRoutingTest.kt:29`, `:41`); its only other callers are the two tests
  (`git grep "WayHomeRouting("` at `9d0b7f8f9`: `AppContainer.kt:441` uses the primary). It is test-only
  scaffolding living in main code; acceptable here, and the doc says so (`:33-36`). Note, not a fix.
- **The way home's tests:** 28 way-home / routing test classes ran in the PR run and passed (computed from the
  XML list: `FindWayHomeUseCaseTest`, `FindWayHomeOnBRouterTest`, `WayHomeWatchTest`, `WayHomeRoutingTest`,
  `BRouterTrailRouterTest`, `BRouterTrailSnapperTest`, …). No way-home test file is in the PR's file list.

**Answer to (3):** the way home behaves identically; built lazily once and shared; lock order unchanged.
Confirmed by reading, and by R3 making the way home's own test fail.

## 5. Question (4): note precedence when the ends are in different regions

`FindRouteDataForPoints.kt:28-50` (read). First, the newest downloaded region with stored routes whose route
rectangle holds **both** ends and whose build has both squares (`:35-37`). Failing that, per-end answers in this
order (`:44-49`): both ends `Found` (in different regions) → `InDifferentAreas`; the walker's end covered but
unusable → that state's note; the waypoint's end likewise; one end `Found`, the other in no region →
`InDifferentAreas`; neither → `NoRegionCovers` ("No trail routes here"). The order is the coder's, stated as
changeable (`:23-25`).

Cases traced (inferred from the code, not run beyond the tests' own cases):

| Walker | Waypoint | Note shown | Right? |
|---|---|---|---|
| region A, routes stored | region B, routes stored, no region holds both | "The waypoint is in another area" | yes |
| region A, routes stored | region B covers both ends, B's routes not fetched | "Trail routes not downloaded" | yes, and actionable: fetching B's routes gives a route |
| region A, routes stored | region B covers only the waypoint, routes not fetched | "Trail routes not downloaded" | **misleading**: after the fetch the answer becomes "in another area"; no action gives a route |
| no region | region B, routes stored | "The waypoint is in another area" | the walker is the one outside; the approved notes have no "you're outside your area" (-1652 Q4), so this is the nearest approved wording |
| no region | no region | "No trail routes here" | yes |

The third row is the one to decide. The code cannot tell rows 2 and 3 apart because it never asks "does any
downloaded region's route rectangle hold both ends, whatever its route state?" One such check before `:44`
would route row 3 to `InDifferentAreas` and keep row 2 as it is. Nothing is visible until PR 3, so this is a
should-fix for PR 3's dispatch or an amendment, not a block; it is a planner-level precedence between notes
the owner has already approved, not new wording.

## 6. Question (6): a missing route file read as "far from a trail"

Read. `RoutingDataFolder.check` runs before the engine (`BRouterTrailRouter.kt:124`) and returns
`EndOutsideData(START|END)` when the end's five-degree square has **no `.rd5` file in the folder**
(`RoutingDataFolder.kt:33-34`), on the reasoning that a square with no file holds no data (`:31`). The way home
reads every `EndOutsideData` as `NO_ROUTE_FOUND` ("Couldn't find a trail route"), vague but true
(`FindWayHomeUseCase.kt:180`). The new use case reads it as **"You're far from any trail · straight line"** or
**"The waypoint is far from any trail · straight line"** (`FindWayToWaypointUseCase.kt:118`). For the file-missing
producer that is a fabricated reason: the data is absent, the trail may be ten metres away. CLAUDE.md, Errors:
a failed result is reported as such, never as a plausible value.

**Reachability (read, then inferred):**
- `RegionRouteStore.status` checks every recorded file's size on every read and reports `Damaged("X is gone")`
  when one is missing (`RegionRouteStore.kt:96-101`), which `routeDataForPoints` turns into `NotUsable` →
  "Trail routes couldn't be read" before the engine is reached. So a file deleted from a whole download is
  caught upstream.
- `routeDataForPoints` also refuses a square named in `missingSquares` (`FindRouteDataForPoints.kt:35`), the
  Worker's list of squares the build lacks (`RegionRouteData.kt:35-38`, `:48`).
- What remains: (a) a race, the file going between `listRegions` and the engine run; (b) a square inside the
  region's route rectangle that the Worker **neither delivered nor listed as missing**. Whether (b) can happen
  depends on the Worker's contract, which I did not read (`server/pmtiles-worker`, `routing/area.ts` per the
  RECORD -1546 lineage); the app-side doc says `missingSquares` "names the five-degree squares the area needs
  that the Worker's build does not have", which if the Worker honours it closes (b). **Could not determine from
  the app alone.**

So the misreport is reachable only by a race or a Worker-contract breach, and today by nothing at all (no
production caller). It is still the wrong reading of a failure kind with two producers. Cheapest honest fix:
`RoutingDataFolder` returns `DataMissing` for a missing square file, which the new use case reads as "Trail
routes couldn't be read" (`:119`). That would change the way home's note for the same case from "Couldn't find
a trail route" to "Trail routes couldn't be read", a way-home behaviour change and so a stop-and-ask under the
dispatch's own list; the alternative is to leave the engine and record the gap. **Should-fix**, for Rowan to
place; not blocking, since nothing shows it and the realistic producers are closed upstream.

## 7. The rest of the dispatch to me

### The 100 m test replacing Q3's rule (read, computed)

`FindWayToWaypointUseCaseTest.kt:121-127`: walker at (0, 20), waypoint at (100, 20), trail along north = 0;
asserts trail 100.0 m and total 140.0 ± 0.5 (20 + 100 + 20). It cites -1653 ("It's always worth it"). Revert R1
inserts `if (metersBetween(from, waypoint) < 150.0) return straight(NO_ROUTE_FOUND, …)` and exactly this test
fails, with the message "a route along trails, not StraightLine(note=NO_ROUTE_FOUND)" (run 40287977818,
computed). The test bites on the rule it names. There is **no detour-limit check anywhere in the use case**
(read, `:56-91`: region, engine, route, two-point guard, area check, build), so "no detour limit" is true by
absence of code; **no test pins it** (the three-part test has a 600 m route for a 510 m straight distance, ratio
1.2). A test with a trail route several times the straight distance would make the absence a claim. Note, not
a fix: the dispatch asked only for the 100 m test.

### The revert bites (computed from each run's JUnit XML and failed-job log)

| Revert | One-line edit | Failed (of 21) | Specific to the edit? | Compiled? |
|---|---|---|---|---|
| R1 40287977818 | 150 m cut-off restored | the 100 m test | yes: `StraightLine(NO_ROUTE_FOUND)` is the inserted return | yes |
| R2 40287988837 | `STRAIGHT -> TRACK` mapping | "placed and drawn": `expected [STRAIGHT, TRAIL, STRAIGHT] but was [TRACK, TRAIL, TRACK]` | yes | yes |
| R3 40288000867 | `built` never kept | `TrailRoutingEngineTest` assertSame; `WayHomeRoutingTest` installs 1 vs 2 | yes, both | yes |
| R4 40288010954 | `area.contains(to)` dropped | newest-region (routed on the wrong region → `LEAVES_AREA`), different-regions and waypoint-outside (both `WAYPOINT_FAR_FROM_TRAIL` instead of `WAYPOINT_IN_ANOTHER_AREA`) | yes, all three are what a region holding only the walker produces | yes |
| R5 40288022756 | `if (false)` on the end check | walker-far (fake) and walker-far (real engine): both `WAYPOINT_FAR_FROM_TRAIL` instead of `WALKER_FAR_FROM_TRAIL` | yes | yes |
| R6 40288032963 | area check off | leaves-area: `AlongTrails(…)` instead of `LEAVES_AREA` | yes | yes |

"Compiled" is read from each failed-job log: `21 tests completed, N failed` and `> Task :app:testDebugUnitTest
FAILED` present, no `e: file://` or `compileDebug…Kotlin FAILED` lines (grep, computed). Each run's 21 tests are
the four new or touched classes (`FindWayToWaypointUseCaseTest` 14, `FindWayToWaypointOnBRouterTest` 3,
`TrailRoutingEngineTest` 2, `WayHomeRoutingTest` 2 = 21, computed from the four XML files), run through CI's
`tests` filter (`.github/workflows/ci.yml:26`). Each revert is its own branch off `9d0b7f8f9`, so the forward
change was never touched by a restore (the CLAUDE.md runner hazard does not arise in this shape). Each failure
is one its revert could produce and none is a stale run. **All six are evidence.**

### Fail-first (computed from run 40287085019's XML)

21 tests, 20 failed over the stubs, one passed: **`the notes are the owner's words`** (`:198`). The stub commit
already carried the whole `WayToWaypoint.kt` (67 lines in `2f76a2cf2`, identical to the build's), so that test
never failed first. It is a wording pin and its subject is the enum itself; harmless, but it has not been shown
to bite. The way home's two `WayHomeRoutingTest` cases also failed over the stubs, as the stub commit message
predicted (read), which is a second confirmation that the way home now runs through the new engine.

### File sizes (computed, `wc -l` at `9d0b7f8f9`)

New: `FindRouteDataForPoints.kt` 69, `TrailEngine.kt` 23, `FindWayToWaypointUseCase.kt` 137, `WayToWaypoint.kt`
67, `TrailRoutingEngine.kt` 50, tests 216 / 69 / 93 / 74. Changed: `AppContainer.kt` 529 (from 512 at the
report's count), `WayHomeRouting.kt` 57 (from 62), `WayHome.kt` 161, `WayHomeProgress.kt` 230, `RouteLine.kt`
55. Nothing near 400 new or 800 changed; within the modular-code skill's limits. The report priced
`FindWayToWaypointUseCase.kt` at about 200 lines; it is 137 because the snapping went into the one engine run.

### Clean merge (computed)

`git merge-tree --write-tree origin/pre-main 9d0b7f8f9` produced a tree with no conflict output; the only
pre-main commit since the base touches a file this PR does not. Mergeable as it stands.

### Dispatch scope (read)

Files touched are the ones the dispatch and the report §4 PR 2 name, plus the tests, minus one: the report
promised a doc pointer in `FindRouteDataForPointUseCase.kt` to the two-point helper; that file is untouched
and its doc still says the two-point question "is a different question" (`:16-18`). Trivial doc debt. The
report's naming-debt choice (lead-in in `trackBack`, final leg in `trackToStart`) is taken as the dispatch
allowed, and the `WayToWaypoint.kt:12-22` doc says so. `AvailabilityViewModel.kt`, `MainActivity.kt` and the
track UI are untouched (file list). Nothing visible: `findWayToWaypoint` has no caller outside `AppContainer`
(`git grep` at `9d0b7f8f9`, computed).

## 8. Verdict

**Blocking:** none.

**Should-fix** (neither shows until PR 3; both can ride in PR 3's dispatch or an amendment, Rowan's call):
- **S-412-1** The note precedence of §5 row 3: "Trail routes not downloaded" where no region can ever hold both
  ends. One check, "does any downloaded region's route rectangle hold both ends?", before the per-end states
  separates it from the actionable case (row 2).
- **S-412-2** `EndOutsideData` from a missing square file read as "far from any trail" (§6). Either
  `RoutingDataFolder` says `DataMissing` for that producer (a small way-home wording change for the same
  case: stop-and-ask) or the gap is recorded. Reachable today only by a race or a Worker-contract breach I
  could not rule out from the app.

**Notes:**
- **N-412-1** `RouteLine.kt:29-32` says "Nothing draws it yet". `RouteHomeLayers.kt:149` tags every non-TRAIL
  part as `PART_TRACK`, so a `STRAIGHT` part that reached the map **today would be drawn dotted**, which -1589
  reserves for the walker's own track. Nothing produces one in production, so this is a fact for PR 3's
  dispatch: `:149` must change in the same PR that gives the use case a caller.
- **N-412-2** `WayHomeProgress.kt:149` gives the trail allowance only to `TRAIL` parts, so a figure on a straight
  leg gets none. Sensible for a straight leg; stated so PR 3's placing is read with it in mind.
- **N-412-3** `LEAVES_AREA` keeps the way home's full sentence with no "· straight line" tail
  (`WayToWaypoint.kt:52-56`). The coder's reading of Q4 is defensible (the sentence had no tail to swap) and is
  disclosed; the owner approved the other nine. One line for the owner, if Rowan wants every fallback to say
  what is shown.
- **N-412-4** "No detour limit" is true by absence and pinned by no test (§7).
- **N-412-5** One test never failed first (the wording pin, §7). Cosmetic.
- **N-412-6** The old `WayHomeRouting` constructor is test-only scaffolding in main code (§4). Fine while
  `WayHomeRoutingTest` stays as it is; a later tidy could move those two tests onto the primary constructor
  and drop it.
- **N-412-7** The doc pointer the report promised for `FindRouteDataForPointUseCase.kt` was not added.
- **N-412-8** For PR 3's device check: a walker beside a short disconnected spur (an island) gets "Couldn't
  find a trail route · straight line", not a route via the next trail, in this design and in the snap-first one
  alike (§2). Known, not a bug.

**Mergeable:** yes, as it stands, into pre-main. Nothing it adds is reachable by a user; the way home is
unchanged by reading and by its own tests biting on the new engine (R3); every rule the dispatch binds has a
test shown failing without it; the merge is clean against `7b388b599`.

**One-line answers:**
- (1) One run is equivalent to snap-then-route except at micro-cache tile edges, where it sees more and can
  only do better; junctions and ties give the same point; a nearer wrong-direction or disconnected trail is
  matched in both designs and yields "Couldn't find a trail route". The real-engine tests show one point on one
  trail, as start and as end, and the end-naming; nothing about junctions, two trails or edges.
- (2) `TrailEngine.kt` is the old build body behind a domain interface, correct and small.
- (3) The way home behaves identically: built lazily once, shared, lock order unchanged, no state in the use
  case to lose.
- (4) Precedence is right except where no region can hold both ends and one end's routes are unfetched
  (S-412-1).
- (6) A missing square file reads as "far from a trail"; closed upstream in every ordinary case, open by race
  or Worker breach (S-412-2).

## 9. The prediction, scored

1. Equivalence through the shared matcher: **confirmed**, with one refinement I did not predict: the one-run
   design sees both ends' tiles through one matcher, so the two designs differ at tile edges in the one-run
   design's favour, and the end cut can differ from the start cut by rounding (the test's 1 m tolerance).
2. Way home unchanged: **confirmed** by reading and by R3.
3. Real-engine tests pin one point on one trail: **confirmed**; they also pin end-naming for an unmatched end,
   which I did not list.

## 10. Disclosure

**Confirmed vs inferred.** Read: every Forager file cited, at `9d0b7f8f9` or `85f05c784`; the vendored BRouter
files cited, at `9d0b7f8f9`. Computed: run metadata, JUnit totals (5,606 / 0 / 24; 21 / 20 for fail-first; the
six reverts' failures), the revert branches' parents and diffs, the merge-tree, `wc -l`, the grep for callers.
Inferred: the tile-edge divergence (§2), the rounding behind the 1 m tolerance (§2), the five precedence rows
not covered by a test (§5), the reachability of the missing-file misreport (§6).

**Could not determine.** Whether the Worker always lists every undelivered square of a region's rectangle in
`missing` (closes §6 (b)); the exact micro-cache divisor in the Harriman fixture's header (I used BRouter's
usual 32); the actual distance between the route's last point and the waypoint's snap on the fixture (the
test allows 1 m; I did not run it). Why the end-cut and start-cut differ I reasoned from `OsmPath.java`, not
from printed values.

**Premises that were wrong, or need correcting.** None in the dispatch to me. In the report §4 PR 2: the use
case was priced at about 200 lines and is 137; the `FindRouteDataForPointUseCase.kt` doc pointer was planned
and not done. In the PR body: "no detour limit" is attributed to -1653, whose words are "It's always worth it";
the phrase is the dispatch's (`2026-10-26-10.md`, "no detour limit"), which binds the coder, so no harm.

**Decided beyond scope, or left for the owner.** I ran no revert check of my own: no builds on the laptop
(coders are CI-only, the laptop is for forecast work and T6b) and no pushes but this report. In its place I
read all six revert runs' logs and XML for compile errors and failure specificity (§7). S-412-1 and S-412-2
are left to Rowan to place; N-412-3 is one line for the owner if Rowan wants it asked.

**What I did not do.** No device; no local Gradle; no reading of the Worker; the coder's full report (the PR
body says it "goes to the planner") was not available to me, so the revert checks were read from CI, not from
that report.
