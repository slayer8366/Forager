# Trail heights: first stop (look only)

Dispatch 2026-10-30-06 (RECORD -1836), read from `origin/records-after-173:prompts/preserved/2026-10-30-06-trail-heights.md`. Hazel Cairnwright (Opus), 2026-10-30. Branch `trail-heights`, worktree `~/.cache/forager-wt/trail-heights`.

No app code was written. Nothing was built or run: no Gradle, no Java, no device. Every claim below is **read** (with file and line, at `3c5e63468` unless said otherwise), **inferred**, or **could not determine**.

## Base

- `origin/pre-main` is `3c5e63468dd1a49598edac5ee4d3a30d2691e27a` after `git fetch origin`, which is the dispatch's base. **Read.**
- Read first, as the dispatch asks: Aspen's note (Forager-cartography `9d72724`, `docs/handoffs/2026-10-30-elevation-in-trail-data.md`), RECORD -1833, -1835 and -1836 (`origin/records-after-173:RECORD.md`), the route plan's Addendum 3 (`origin/records-after-173:docs/plans/2026-10-29-waypoint-route-plotting-plan.md:99-105`; the plan is not on pre-main), and `docs/navigation/2026-10-26-climbing-allowance.md:22-24`.

## The dispatch's "What exists", re-checked

| Claim | Result |
|---|---|
| `routing/BRouterTrailRouter.kt` maps the engine's nodes to positions only and drops their heights | **Confirmed**, `BRouterTrailRouter.kt:112-117`: `points = track.nodes.map { latLngOf(it.getILat(), it.getILon()) }`. |
| `TrailRoute` (`domain/routing/TrailRouter.kt:41-45`) has points and lengthMetres, no heights | **Confirmed**, `TrailRouter.kt:41-45`. |
| `WayHomeHeights.kt` gives the trail part the walker's track as a stand-in and says "the route files carry no elevation" | **Confirmed**, `WayHomeHeights.kt:15-21` (the claim) and `:56-67` (`standInHeights`); wired at `FindWayHomeUseCase.kt:96-98,120`. |
| Live squares carry 3″ heights; five pinned squares have none; data from before 10-28 has none | **Not checkable from this repo.** Taken from Aspen's note and RECORD -1835. The repo's own route fixture has none (see (a)). |

## (a) Where the engine's heights are, and what a missing one looks like

**Where.** Each route point is a `btools.router.OsmPathElement` in `OsmTrack.nodes`. Its height is `getSElev()`, a `short` in **quarter metres** (`OsmPathElement.java:35-36`; `getElev()` at `:43-44` is `selev / 4.`). The point is built from the network node's own `selev` (`OsmPathElement.java:84-87`, `create(OsmPath)` reads `n.getSElev()`) or, for a point along a way's geometry, from the transfer node's own decoded height (`OsmPath.java:213-216`, `originEle2 = transferNode.selev`, used at `:360`). These are the raw stored heights. The `if (ele2 == Short.MIN_VALUE) ele2 = ele1` carry-forward at `OsmPath.java:294` feeds only the cost calculation and the path's own `selev` (`:354`), never a track point. **Read.**

**Missing is `Short.MIN_VALUE` (-32768), never 0.** The evidence:
- The engine's own outputs treat it so: `FormatJson.java:109` leaves the height out of a coordinate when `getSElev() == Short.MIN_VALUE`, and `FormatGpx.java:262,507` leaves out `<ele>`. `OsmNode.selev` starts at `Short.MIN_VALUE` (`mapaccess/OsmNode.java:28`). **Read.**
- **Observed in the repo's own fixture, which has no heights.** `app/src/test/resources/routing/brouter-cli/inside-long.geojson` and `inside-across-the-degree-line.geojson`, written by BRouter 1.7.10's own command-line router on `segments/W75_N40.rd5` (`README.md` in that folder): all 306 and 226 coordinates have two values, no height, and every `messages` row reads Elevation `-8192`, which is `-32768 / 4` in the integer division the message writer uses. "filtered ascend" and "plain-ascend" are both `0`. **Read** (parsed with `python3 -I`).
- What the map creator writes for a node with no elevation source is **inferred, not read**: the map creator is not vendored (`third_party/brouter/src/main/java/btools/` holds `codec`, `expressions`, `mapaccess`, `router`, `util`). The decoders store deltas from 0 (`codec/MicroCache2.java:78,113-114`; `mapaccess/DirectWeaver.java:57,87-88`), so they reproduce whatever value was written, and the fixture shows that value is -32768 on a square built without heights.

**So a square without heights shows up as every point of the route carrying `Short.MIN_VALUE`.** A route that crosses from a heighted square into an unheighted one would carry real heights on one side and `MIN_VALUE` on the other (inferred from the per-node storage above; no such fixture exists).

**One engine-made value at the ends.** Where the engine matches a route end to the middle of a way, the end point's height is interpolated between the way's two nodes, and is `MIN_VALUE` when either is (`OsmPath.java:244,255-256,266` calling `interpolateEle`, `:388-393`). So a route's first or last point can be missing next to a heighted neighbour. **Read.** `OsmTrack.java:344-345` fills a missing height at a waypoint join from the next segment; that is for routes with intermediate waypoints, which the adapter does not send (two waypoints, `BRouterTrailRouter.kt:149`). **Read.**

**The mapping, for the build:** `getSElev() == Short.MIN_VALUE` gives "no height" (`null`); anything else gives `getSElev() / 4.0` metres, including 0 and negatives (sea level, below sea level). Note `-12345` is a special `OsmNode` marker (`OsmNode.java:194-198`) on the node in the map, not a track-point value; I did not trace whether it can reach a track point. **Could not determine; I read it as not reaching one.**

## (b) The TrailRoute change, and every caller

**Proposed shape** (for the amendment to accept or change):

```kotlin
data class TrailRoute(
    val points: List<LatLng>,
    val lengthMetres: Int,
    /** The trail data's height at each of [points], in metres; null where the data has none. Never 0 for missing. */
    val heightsMetres: List<Double?> = points.map { null },
) { init { require(heightsMetres.size == points.size) } }
```

A third parameter with a default keeps every two-argument call compiling unchanged, positional or named. A default of "no heights" (not an empty list, not 0) means every fake router reads as a square without heights, so today's tests keep the stand-in they assert.

**Every caller, read at `3c5e63468`:**
- Constructs `TrailRoute`: `routing/BRouterTrailRouter.kt:113` (the one production constructor, the one that gains heights); tests `domain/wayto/WayToWaypointFakes.kt:37` (`TrailRoute(points, Math.round(metres).toInt())`) and `domain/wayhome/WayHomeFakes.kt:97` (`TrailRoute(points.toList(), lengthMetres)`). Both positional, both still compile.
- Reads `points` / `lengthMetres`: `domain/wayhome/FindWayHomeUseCase.kt:90,94,95`; `domain/wayto/FindWayToWaypointUseCase.kt:75,76,79,80,84`; tests `routing/BRouterTrailSnapperTest.kt:69`, `routing/BRouterTrailRouterTest.kt:80,81`. None destructures `TrailRoute`, so adding a component breaks none.
- **Open PRs.** #445 (`route-plot-data`, head `e581d35ae`) adds no `TrailRoute` code; its diff mentions it only in a report. #446 (`route-plot-logic`, head `0e03238f2`) reads `route.points` and `route.lengthMetres` in its new leg routing and uses `FakeTrailRouter` from `WayToWaypointFakes`. With a defaulted third parameter both compile unchanged. **Read** from `git diff origin/pre-main...origin/<branch>`; I did not check out or touch either branch.
- **The way to a waypoint** (`FindWayToWaypointUseCase.kt:84`) builds its trail part with no heights and nothing reads its time (`WayHomeHeights.kt:78`). The dispatch names only the way home, so I would leave it as is.

**In the adapter:** `foundRoute` (`BRouterTrailRouter.kt:104-118`) maps `track.nodes` once, giving both positions and heights. I would pull the node-to-`TrailRoute` mapping into an internal function so the unit tests can hand it built `OsmPathElement`s (`OsmPathElement.create(ilon, ilat, selev, origin)` is public, `OsmPathElement.java:92`), for the mixed case no fixture has.

## (c) The way-home change, the fallback rule, and the partly-heighted proposal

**Where the change lands.** `FindWayHomeUseCase.kt:120` sets the trail part's heights to `standInHeights(...)`. The trail part's points are the leave point, the route's points, then the entry point (`:90`), and its metres are `route.lengthMetres` plus the two straight joints (`:94-95`). The rule as dispatched: every route point heighted, use the trail heights; otherwise the stand-in, as today, logged.

**A premise the dispatch does not cover: the two kinds of height are on different datums, and the line banks across them.** The stand-in was the walker's own barometric heights, so it met the track parts either side at the same height. Trail-data heights come from the elevation model; the barometric heights are anchored to the phone's GPS height, or to the standard atmosphere with no GPS height (`BarometricHeight.kt:31-36`), and the GPS height is `Location.getAltitude()` (`location/AndroidLocationProvider.kt:36`), which Android documents as height above the WGS84 ellipsoid, not above sea level (**inferred** from the platform's documentation; I did not trace the recording path past that provider). `lineHeightsOf` joins every part's samples into one list (`WayHomeHeights.kt:80-88`) and `climbFrom` banks the whole list at once (`:94-101`, through `bankedClimb`, `ClimbAllowance.kt:91-112`). So if the two differ by more than the 4 m hysteresis, the step counts as a climb at one junction and a descent at the other, both at 2.2 s a metre: a 20 m offset adds about 88 s to the walk back for no hill. "Swap the stand-in for the trail heights" would build that in. Options (propose, don't decide):

- **J1, recommended: the two joint points carry no height.** `bankedClimb` already resets at a missing height and bridges nothing across it (`ClimbAllowance.kt:98-101`), so the step at each junction is never counted. Cost: any climb over the two straight joints, each up to `WAY_HOME_TRAIL_JOIN_METERS` = 25 m (`WayHome.kt:133`), is not counted, which matches `ClimbAllowance.kt:44` ("the straight joints at a trail's ends ... counted flat"). Changes nothing in the banking.
- J2: shift the trail heights so the first one equals the track's height at the leave point. The entry junction still steps by whatever the offset changed over the trail part.
- J3: bank each part on its own and add them up. That changes `lineHeightsOf` / `climbFrom`, which the dispatch says stay as they are.

**The no-barometer rule.** Unchanged, as the dispatch says: with no barometer the track parts are `PartHeights.NoBarometer` (`WayHomeHeights.kt:45-46`) and any one such part makes the whole line `NoBarometer` (`:81`), so there is no allowance **even when the trail part has trail-data heights**. Stated so the amendment confirms it is meant.

**How the trail part says which heights it carries.** `PartHeights.Barometric(samples, standIn)` (`WayHomeHeights.kt:32`) is named for the barometer, and `standIn` is read only by a test (`WayHomeClimbTest.kt:53`). Options: (i) a new case, say `PartHeights.TrailData(samples)`, which `lineHeightsOf` (`:84`) must also accept; or (ii) reuse `Barometric` with `standIn = false`, which mislabels them. I'd take (i) (CLAUDE.md, Building: a new capability is a new path).

**Where each trail height sits along the part.** The stand-in is scaled to the part's metres (`WayHomeHeights.kt:60-66`). For trail heights, I propose cumulative distance along the trail part's points, with the route's own stretch scaled so it equals `route.lengthMetres`, so the last route point lands where the part's metres say it should. The engine's length and the summed point distances can differ by a few metres (not measured).

**The fallback and its log.** All points missing (an old download, a pinned square): the stand-in, as today, logged. FindWayHome's fallbacks to the track are logged once per call (`FindWayHomeUseCase.kt:47-50,201-204`), and it is called at Return and again when the walker strays (`WayHomeWatch.kt:245`, through `routing/WayHomeRouting.kt:51`). I propose the same: one log line per call that falls back, naming the reason and never a position. The other choice is once per recording, as the no-barometer note is (`SundownWatch.kt:68,398-399`). **The amendment picks.**

**Partly heighted (some route points have heights, some don't). Propose, don't decide.** Within one region the files come from one build (`RegionRouteStore.kt:26-27`: a fetch writes into `<id>.partial` and renames it whole; `RegionRouteRecord.kt:17` keeps one `runId`), so a partial route should mostly be: a route touching one of the five pinned squares; an end interpolated next to a missing node (above); or a gap in the elevation model inside a heighted square, if the Pi's build leaves any (**could not determine**; a question for Aspen).
- **P1, recommended: treat any missing route point as "not every point", so the stand-in, logged with the counts ("212 of 230 route points have heights").** It is the dispatch's own rule applied literally, never mixes the two datums, and the cases are rare and near the data's edge.
- P2: use the trail heights, with the missing points as gaps. `bankedClimb` resets at each gap, so climb across a gap is lost and the allowance reads short. A "start back by" that is too late is the unsafe direction.
- P3: fill the gaps from the stand-in. It mixes datums at every gap, the J-problem many times over. Not recommended.
- P4: P2 above some share of heighted points, P1 below. A threshold with no data behind it (CLAUDE.md, Building). Not recommended now.

## (d) Tests, and where the fixtures come from

**Fixture without heights: already in the repo.** `app/src/test/resources/routing/segments/W75_N40.rd5`, the Harriman square, has none (see (a); its `README.md` there and `server/pmtiles-worker/test/fixtures/rd5/README.md` say so too). The router test compares the adapter's route to the command-line router's GeoJSON point by point (`BRouterTrailRouterTest.kt:69-81`). The same comparison gains heights: the GeoJSON's two-value coordinates mean "no height", so the adapter must give `null` at every point, never 0. That is checked against an independent producer, not against what I expect.

**Fixture with heights: not in the repo, and I can't make it within the dispatch's rules.** It needs the elevation store, which is only on the Pi, and network beyond GitHub. Two ways, both Aspen's (or the planner's) to choose:
- **F1, recommended:** Aspen cuts the same Harriman rectangle (`server/pmtiles-worker/test/fixtures/rd5/routes.json`) from the live run's `W75_N40.rd5` with the route cutter, and reruns `app/src/test/resources/routing/make-expected.sh` on it with the pinned 1.7.10 jar to get the two expected GeoJSON files, now with three-value coordinates. It is the data a phone downloads, cut by the cutter the Worker uses, and the expected heights come from BRouter's own router. Committed beside the no-heights pair, e.g. `segments-heighted/W75_N40.rd5` and `brouter-cli/heighted/*.geojson`.
- F2: Aspen rebuilds the fixture with `make-fixture.sh` plus the pinned elevation store. It's the same recipe as today's file, but not the live run.
- Both need: its size (the no-heights cut is 152,665 bytes), its hash in the README, and any credit the elevation source's licence asks for, which I don't know (**could not determine**; Aspen's G8 record would say).

**Tests I'd write first (after the amendment):**
1. Router, heighted fixture: each point's height equals the command-line router's third coordinate, both routes.
2. Router, no-heights fixture: every height is `null` (would fail if the mapping used `getElev()` straight, which gives -8192).
3. Adapter mapping, hand-built `OsmTrack`: `MIN_VALUE` gives null, 0 gives 0.0, -8 gives -2.0, 4 gives 1.0; a mixed track keeps its pattern.
4. Way home through its real entry point, `FindWayHomeUseCase.invoke`, on a fake router that returns heights (the `FakeRouter` in `WayHomeFakes.kt` gains an optional heights argument): every point heighted, the trail part carries them, not the stand-in, and the climb from `placeOnWayHome` reads them (as `WayHomeClimbTest.kt:41-45` does); none heighted, the stand-in and one log line; partly heighted, whatever the amendment picks; no barometer plus heighted trail, still `NoBarometer`; and, if J1 is picked, a track 20 m off the trail's datum on a flat trail adds no climb.
5. End to end on BRouter: `FindWayHomeOnBRouterTest` on the heighted fixture.

Revert checks, one per rule: the `MIN_VALUE` check, the replace, the fallback, and J1 if picked. Each runs under CLAUDE.md's runner rules.

## (e) Files over 800 lines touched

None. The files the change would touch, with line counts at `3c5e63468`: `routing/BRouterTrailRouter.kt` 218, `domain/routing/TrailRouter.kt` 81, `domain/wayhome/WayHomeHeights.kt` 103, `domain/wayhome/FindWayHomeUseCase.kt` 213, `domain/wayhome/WayHomeProgress.kt` 258 (comment only), `domain/SundownWatch.kt` 481 (comment only), `domain/ClimbAllowance.kt` 112 (comment, if J1). Tests: `BRouterTrailRouterTest.kt` 224, `FindWayHomeUseCaseTest.kt` 318, `WayHomeClimbTest.kt` 84, `WayHomeFakes.kt` 99, `FindWayHomeOnBRouterTest.kt` 101, `RoutingTestData.kt` 48. Any new file stays under 400.

## The stale text (change 3)

Saying the routes carry no elevation, or that the trail part is a stand-in "until the routes carry elevation":
- `domain/wayhome/WayHomeHeights.kt:15-21` (named in the dispatch).
- `domain/wayhome/FindWayHomeUseCase.kt:96-97`.
- `domain/wayhome/WayHomeProgress.kt:35-37`.
- `domain/SundownWatch.kt:66-68`.
- `test/.../wayhome/WayHomeClimbTest.kt:18`.
- `docs/navigation/2026-10-26-climbing-allowance.md:12` and the "For the elevation work (not built)" section, `:22-24`.
- `server/pmtiles-worker/test/fixtures/rd5/README.md`, "It carries no elevation, like the Pi's squares". The Worker's tree; is it in scope?
- The RECORD -1822 / -1824 lines are the planner's.

Stale, but not about elevation (noticed, not in scope unless the amendment says): `domain/routing/TrailRouter.kt:12-13` and `FindWayHomeUseCase.kt:29-30` both say nothing calls them in production yet; `FindWayToWaypointUseCase.kt:70` and `WayHomeWatch.kt:245` do. #445's and #446's reports flag the first one too.

## Premises that were wrong or incomplete

1. **"Those heights replace the stand-in"** misses the datum step between barometric and trail-data heights, which the whole-line banking would count as climb. See (c), J1 to J3.
2. "What exists" is correct as far as this repo can show.

## Decided beyond scope

Nothing. Every choice above is a proposal for the amendment.

## A slip of mine

While reading RECORD.md I wrote it out to `/mnt/work/scratch/rec.md`, at the root of the shared scratch folder, not in `/mnt/work/scratch/trail-heights/`. If another session had a `rec.md` there, my write replaced it. I listed the folder only after writing, so I can't tell whether one was there. I have moved the copy to `/mnt/work/scratch/trail-heights/rec.md`.
