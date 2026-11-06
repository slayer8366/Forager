# The way home along trails (dispatch 2026-10-23-09, plan step 5): report before building

**Author:** Hazel (app coder). **Status:** a report. Nothing is built: no code changed, no local Gradle run, no phone used.
**Read at:** origin/pre-main `adcbedb1966f5cff352536dc617ee3c3efaec92b`, checked against the remote with `git fetch` before reading. It had not moved from the base the dispatch names. Every `file:line` below is at that commit. App paths are under `app/src/main/java/com/zynergylabs/forager/app/` unless a path says otherwise.
**Dispatch:** `prompts/preserved/2026-10-23-09.md` on `origin/records-after-173` (330456b60).
**Not touched:** PR #386's files, the Worker, route-data download, the T22 camera, waypoint navigation, and the Sundown, Back-by and Off-track alert rules. This report proposes changes to two of those areas' *inputs*. Both are stated as owner questions in section 9.4, not decided here.

## The behaviour as I would build it (restated so it can be checked)

From RECORD -1498 (plan update `docs/plans/2026-10-21-trail-navigation-plan-update.md:83-84`) and -1586:

1. Recording, then tap Return.
2. The map shows the walker's own track back to the point where they left the trail, drawn dashed. From there it shows the trail route to the start, drawn solid. Nothing on the line is a straight-line shortcut longer than the joint figure N in section 2.
3. The HUD distance is the length of that line. Once a trail route is shown, sundown's walk-back time uses that same figure.
4. On arrival: "Arrived", and a ring at the start. Arrival works as it does today (section 4).

The fallbacks always show a line, and it is today's track line:
- **No trail routes stored where you are:** today's line, plus the note "No trail routes here · following your track".
- **The trail route would leave the downloaded area:** today's line, plus "This route leaves your downloaded area. Download a larger area to route there."

Three things in this restatement are not ruled yet, and section 9.4 asks about each:
- what joins the trail's far end to the start (step 2);
- whether "dashed" may mean the walker's own track, when today dashed means a straight line (section 4);
- what the walk-back time uses before Return, when no route is shown.

---

## 1. The cancel fix (#382 M1)

**The defect, confirmed by reading.** `BRouterTrailRouter.route` (`routing/BRouterTrailRouter.kt:51-53`) is a `withContext(dispatcher)` around `ONE_ROUTE_AT_A_TIME.withLock { routeNow(...) }`. `routeNow` builds the engine and calls the blocking `doRun(maxRunningTimeMillis)` inside `runEngine` (`:90-107`, the `doRun` call at `:106`). The engine is a local that nothing outside `runEngine` can reach, and nothing calls `engine.terminate()`. BRouter's `terminate()` (`third_party/brouter/src/main/java/btools/router/RoutingEngine.java:2432-2434`) sets a volatile flag. The search loop reads that flag once per pass (`:1975-1977`) and throws "operation killed by thread-priority-watchdog after N seconds". So when the caller is cancelled, the search keeps its thread and the process-wide mutex (`BRouterTrailRouter.kt:125`) until it ends by itself, or until the 30 s limit (`:123`) hits.

**Where the fix goes.** In `BRouterTrailRouter.kt` only, at `:51-53` and `:90-107`. The engine is built first, so `route` holds a reference to it. `doRun` runs only after a cancellation hook is registered that calls `engine.terminate()`.

**One correction to the suggested shape.** The review suggests `invokeOnCancellation { engine.terminate() }`. That is a method of `CancellableContinuation`, so it exists only inside `suspendCancellableCoroutine`. A blocking `doRun` inside `withContext` gives it nothing to hang on. The two public-API shapes I can see:

- **(a)** `suspendCancellableCoroutine`, with `doRun` handed to a thread the continuation does not own (a single-thread executor for the router), and `invokeOnCancellation { engine.terminate() }`. The mutex has to be released when the engine has actually stopped, in that thread's `finally`, and not when the caller is cancelled. Otherwise, for a moment, two engines are in memory at once, and the one-route memory bound the class documents (`:38-40`) breaks.
- **(b)** `Job.invokeOnCompletion(onCancelling = true)`. This is `@InternalCoroutinesApi`. I would not use it.

I'd build (a). This code is proposed only: it has not been compiled.

**Two smaller points:**
- **A cancel gets logged as a failure.** A terminated engine reports its kill message through `getErrorMessage()`. `BRouterFailures.classify` (`routing/BRouterFailures.kt:18-38`) does not recognise the message, so it becomes `EngineFailed`, and `BRouterTrailRouter.kt:70` logs it as "BRouter failed". A route that was cancelled on purpose should return cancellation, not a logged engine failure. The fix rethrows `CancellationException` when the caller's job has been cancelled.
- **Cancellation only lands inside the search loop.** The engine reads `terminated` in its search loop (`RoutingEngine.java:1975`) and in one retry path (`:1722`). Waypoint matching and file reads do not read it. So a cancel takes effect on the next pass of the search, not instantly. That is acceptable, and the fix's KDoc should say so.

**A test that fails today.** The vendored engine on the Harriman fixture finishes in milliseconds, so the test cannot cancel it mid-search. It needs a seam: an internal `engineFactory` parameter on `BRouterTrailRouter`, with the real engine as its default. The test supplies a `RoutingEngine` subclass whose `doRun` waits, polling `isTerminated()`, for up to 10 s, and signals a latch when it starts. The test then:
1. launches `route()` on a real thread dispatcher (as `routing/BRouterTrailRouterTest.kt:172-181` already does);
2. waits for the latch;
3. cancels the job;
4. asserts that `isTerminated()` is true within 1 s;
5. asserts that a second `route()` on the same router returns a real fixture route within 2 s.

Today, step 4 fails: nothing calls `terminate`. Step 5 also fails, because the lock is held for the fake's full 10 s.

**Revert check:** delete the one `terminate()` line. Step 4 should then fail with a message naming termination, which this revert can produce. Restore from a saved copy, never from git, and confirm the forward change is still there before citing the result (CLAUDE.md).

## 2. Where the track meets the trail

**Why the engine can't answer this by itself.** The engine snaps both ends of a route to the nearest routable way within its catching range. With dynamic range off (`BRouterTrailRouter.kt:116`, read at `RoutingContext.java:200`), that range is `waypointCatchingRange`. It defaults to 250 m (`RoutingContext.java:143`), and the pinned profile does not set it (`app/src/main/assets/routing/profiles2/hiking-mountain.brf` has no such line). Matching is at `RoutingEngine.java:1609-1630`.

If the walker's current position were handed to `route()`, the engine would snap it to whatever way is nearest, up to 250 m away through forest. Its route would then start from a point the walker never stood on, and the app would have to join the walker to that point with a straight line. That is the shortcut -1498 rules out. The spike saw exactly this: an r4 start 207 m off the trail was snapped to the trail, and no connecting segment came back (`docs/navigation/2026-10-03-engine-spike-report.md:93-102`).

So "where they left the trail" has to be a point on the recorded track. Proposed rule:

- **The leave point:** scan the track's stored points newest-first and take the first one within **N** m of a routable way. The track part runs from the walker back along the track to that point.
- **The entry point:** the same scan oldest-first, starting from the track's first point. It is where the walker first got onto a trail, and the trail route ends there. The track from the entry point to the start, and then the origin leg as today (`domain/RouteHome.kt:151`, `:162`), close the line. *Unruled; section 9.4.*
- **The joints:** the gap between each of those points and the engine's snapped point on the trail is at most N, and it is drawn as a straight joint. This is the same kind of concession the self-join ε makes (`domain/TrackSelfJoin.kt:32`, 10 m), with a bigger figure. Section 9.4 asks for it to be ruled.
- **Walker already on a trail:** the newest point is within N, so the track part is empty and the trail route starts from the walker.
- **Track never within N of a trail:** no trail part at all. The walker gets today's line and a fallback note (section 5).

**How "within N of a routable way" is tested.** Options:

- **A. A new snap method on the `TrailRouter` interface**, for example `nearestTrail(point, routeData): TrailSnap?`. It would be built on BRouter's single-point matching mode, `BROUTER_ENGINEMODE_GETINFO` (`RoutingEngine.java:37`, `doGetInfo` at `:363-486`, which matches one point at `:374`). The scan runs it on points thinned to one every ~20 m (provisional) and stops at the first hit. Then one `route()` call runs from the leave point to the entry point. Cost: one snap per thinned point the walker spent off-trail, plus one route. A snap's cost is **unmeasured**. It does less than a route, so I *infer* it is cheaper; it gets measured on the S22 before anything is tuned on it. This is a new capability, so it is a new function and not a flag on `route()` (CLAUDE.md, Building).
- **B. Use `route()` itself as the test.** Route each candidate to the entry point, and accept the first whose route starts within N of it. The interface does not change, and the last test is already the final route. But each test is a full search. This also depends on the route's first point being the snapped point. That is *unverified*: no test at this commit asserts it (`BRouterTrailRouterTest.kt` has no first-point assertion).
- **C. Read trail geometry from the map's vector tiles.** Rejected. It is a different dataset from the one the engine routes on, so "on a trail" and "routable" could disagree. It would also tie the domain to MapLibre.

A binary search along the track is not an option. Being on or off a trail is not monotonic along a walk, and we want the most recent time the walker was on one.

**What N is: provisional.** I'd start at **25 m**:
- it is above the self-join ε (10 m) and the hop's enter figure (25 m, `domain/PathHome.kt:164`);
- it is near the spike's map-matching spread on a real walk, which had a median of 8.3 m, a 90th percentile of 16.0 m and a maximum of 28.0 m (spike report `:140`). That walk was on roads, not trails, and was measured on the laptop;
- it is well under the live-fix gate's 50 m ceiling (`domain/LiveFixGate.kt:78`).

OpenStreetMap geometry under forest canopy may be worse than on roads. **No measurement of that exists here.** N is tuned on a walk, in one constant, and nowhere else. The other candidates are 15 m (fewer false "on trail" readings beside a path, more walkers who never register as on one) and 50 m (the fix ceiling, longer straight joints). That choice is the owner's.

## 3. When to route

**What a route costs on the S22.** Two sources, and neither is the dispatch 02 report itself (none is on pre-main):
- **RECORD -1572**, relaying #382's S22 check: "0.68 s cold, then 38–94 ms, with about 26 MB peak". I found no on-branch report of those runs, so route lengths and data for those figures are unknown to me.
- **The spike**, with a fresh process each run (spike report `:68-87`): BRouter took 525–1,142 ms per 3.5–10 km route, about 130–138 MB peak for the whole process. The town grid was the slowest.

**Battery cost:** never measured. Not determined.

**Options:**

- **R1. Once at Return, plus Try again.** One scan and one route per Return, and again when a return is taken up after the app reopens (`ui/track/TrackRecordingViewModel.kt:440-456`) or resumed after a waypoint pause (`ui/track/TrackRecordingReturnLeg.kt:14-15`). Then the distance along the trail part has to be measured without re-routing. That means projecting the walker onto the trail line, forward-only. Today's rule refuses nearest-point projection on the *track* because its error can be short (`domain/PathHome.kt:15-24`). Applying projection to the trail part reopens that ruling for that part.
- **R2. As R1, then re-route from the walker every K s once they are on the trail part.** The trail part's distance is then the engine's own figure, so no projection is needed. If the walker takes a different trail, the next route follows them. Cost: about one 38–94 ms route every K s. K = 15 s would be under 1% of one core (*inferred* from -1572's warm figure). Battery is unmeasured.
- **R3. As R1, plus a re-route when the walker is more than 50 m from the shown line**, reusing the far-band hysteresis (50 enter, 45 leave, `domain/PathHome.kt:170-173`). This must not call or change the off-track alert, which is step 7's.

My recommendation is **R2 with R3's trigger**. It avoids reopening the projection ruling, and its cost is small on the figures we have. It is the owner's call because of the projection ruling.

**What the HUD shows while it computes.** Today's track route is pure and fast (about 19 ms on a desktop JVM at the four-hour cap, `ui/track/TrackRecordingRouteTicks.kt:76-79`), so it can be drawn at once, exactly as today. When the trail route arrives, it replaces that line. If the trail route fails, the fallback note appears (section 5). Two choices for the owner:
- **(i)** swap the line silently;
- **(ii)** show a short "Finding trail route…" note until it arrives.

I'd pick (i): the user has nothing to wait for or decide.

## 4. The joined line

**What consumes the route today:**
- `RouteHome.Ahead` (`domain/RouteHome.kt:29-38`): lookahead, `routeMeters`, hop band, `pathHome`, `route`, `routeAhead`.
- `nextRouteLine` (`domain/RouteLine.kt:26-34`) turns it into `RouteLine(atReturn, ahead, aheadIsCurrent)` (`:14-18`). `atReturn` is taken from the first route only (`:29`).
- The HUD reads `returnRouteOf` (`ui/availability/NavigationHudReadout.kt:107-111`), giving `ReturnRoute.Ahead(lookahead, routeMeters)` (`:92`).
- The map reads `RouteOnMap(line, arrivedAt, …)` (`ui/map/RouteHomeLayers.kt:51`), which fills one bright source for the line ahead and one grey source for the line at Return (`:101-113`).

**Can today's shape carry it? Partly.**
- **Distance, needle and grey:** today's lookahead and corner guard (`RouteHome.kt:162-174`) work on any polyline with along-route distances. Run on the joined line, they give the lookahead, `routeMeters` and `routeAhead` unchanged in meaning. The greyed walked part still works, since it is the line at Return under a bright line ahead (`RouteLine.kt:5-12`). One new rule is needed: when the trail route arrives after Return, `atReturn` is replaced once by the joined line.
- **`pathHome` does not fit.** `PathHome` is the track's own arithmetic (`domain/PathHome.kt:140-161`): track metres, hop and origin leg. Its only reader in `main/` is `TrackRecordingUiState.pathHome` (`ui/track/TrackRecordingUiState.kt:178`), and that is read only by tests (`TrackRecordingViewModelTest.kt:371-391`, `:1036-1096`).
- **Dashed versus solid doesn't fit.** The line ahead is one source drawn solid (`RouteHomeLayers.kt:156-161`).

**Proposal: a new type, not new flags on `RouteHome`** (CLAUDE.md, Building). `WayHome` in `domain/`:
- the parts: track part, trail part and end part, each a polyline with its kind;
- the joined polyline and the lookahead fields, computed by the existing lookahead code moved into a function of its own;
- the total metres, broken into its parts so a test can check the arithmetic;
- a `note: WayHomeNote?` for the fallbacks.

`RouteHome` and its tests stay as they are and become the track-only fallback. A `WayHome` built from the track alone is `RouteHome.Ahead` re-expressed, so the fallback line is identical to today's.

On the map: one `route-home-ahead` source whose features carry a `part` property, and two layers filtered on it, one dashed and one solid. The grey line at Return stays one layer. Whether MapLibre's `line-dasharray` could be data-driven instead is unverified; the two-layer form doesn't depend on it.

**A conflict to put to the owner.** Today, dashed means "a straight line, not a path to follow". It is the waypoint line (`RouteHomeLayers.kt:39-44`, dashes at `:89`, layer at `:162-167`). The step path draws the walker's own track dashed. Either the meanings coexist (the waypoint line is thin, 3 dp at `:86`, and the way home 6 dp at `:82`), or the track part gets a different treatment, such as the same azure at reduced opacity. I haven't picked one.

**Arrival:** no change. `ReturnWatch` ends the return on a GPS fix inside the arrival circle, measured as a straight line to the start, after the walker has left it (`domain/ReturnWatch.kt:245-258`, `:287-296`). It doesn't read the route, and the ring sits at the start (`RouteHomeLayers.kt:109-111`).

## 5. The fallbacks

**Choosing the route data.** `FindRouteDataForPointUseCase` covers one point (`domain/routedata/FindRouteDataForPointUseCase.kt:20-24`, and its own doc at `:16-18` names the pair question). The way home needs one region whose route rectangle (`domain/routedata/RegionRouteData.kt:177`, `:190-193`) holds both the leave point and the entry point. That is a new function beside `routeDataForPoint` (`:27`): the newest region covering both.

**Every outcome mapped to a line on screen.** Each row except the first shows today's track line plus the note given. Two rows have the owner's wording; the rest need it.

| Outcome | Note shown | Wording |
|---|---|---|
| Trail route found, inside the area | the joined line, no note | — |
| No downloaded region covers both points (`NoRegionCovers`, `:31`) | "No trail routes here · following your track" | owner, -1586 |
| A region covers them, but the server has no squares here (`NotStored` with `NoneHere`, `RegionRouteData.kt:53`) | the same note | owner, -1586 |
| The build lacks the point's square (`SquareMissing`, `FindRouteDataForPointUseCase.kt:38`) | the same note | owner, -1586 (my reading; confirm) |
| Routes never fetched, being fetched, missing or damaged (`NotStored` with `NotFetched`, `Fetching`, `Missing` or `Damaged`, `RegionRouteData.kt:26-62`) | **unruled.** "No trail routes here" would be false: `NotFetched` is documented as "Never presented as 'no routes here'" (`:23-25`) | owner needed |
| The leave point and entry point are in different regions, or one is outside every region | the -1546 notice (the route would leave the downloaded area) | owner, -1546 |
| The track never comes within N of a trail | **unruled** | owner needed |
| `Failed(EndOutsideData)` (`domain/routing/TrailRouter.kt:53`). After the scan this means the data changed under us | treat as `DataMissing` | owner needed |
| `Failed(NoPath)` (`:56`) | **unruled**; for example "No trail route found · following your track" | owner needed |
| `Failed(DataMissing / DataCorrupt)` (`:59`, `:62`) | **unruled.** The routes exist but can't be read; "No trail routes here" is false | owner needed |
| `Failed(EngineFailed)` (`:69`), including the 30 s limit | **unruled** | owner needed |
| The route leaves the area (detected below) | "This route leaves your downloaded area. Download a larger area to route there." | owner, -1546 |

The way home never shows no line. That is -1586 as ruled.

**Detecting "leaves the area" (-1547).** The Worker cuts a region's routes to its rectangle plus one cell of margin (`server/pmtiles-worker/src/rd5/cut.ts:42-43`). A cell is 1/32° (`server/pmtiles-worker/src/routing/area.ts:18`), roughly 3.5 km north to south. So the data on the phone runs past the downloaded map on every side. Options:

- **L1. Check the route's points against the region's rectangle** (`routeAreaFor` and `GeoBoundingBox.contains`, `RegionRouteData.kt:177-193`, both pure). Any point outside means the notice. This catches every best path that leaves the rectangle by less than the margin, because the engine has that data and the route shows it. It is cheap and makes no false alarms. **It cannot see a best path that leaves by more than the margin.** That path isn't in the data, so the engine returns -1547's costlier detour, which stays inside.
- **L2. L1, plus an edge band:** a route that comes within D of the rectangle's edge also gets the notice. This catches some of L1's misses and over-warns near edges. D is a new provisional figure with no data behind it.
- **L3. L1, plus a ratio check against the walker's own track:** if the trail part is more than k times the track between the same two points, show the track and a note. The walker's track is a known way home, so a much longer "best" route is suspect. This is the kind of correction logic CLAUDE.md says not to build without data showing the case (Building), and we have no such data.
- **Beyond scope:** a wider margin in the Worker's cut. The Worker is out of this dispatch.

I'd build L1 and state its limit in the code and in the step path. L2 and L3 are the owner's to choose, or to hold until a walk shows the case.

**Every fallback is logged.** Today the router logs only `EngineFailed` and engine exceptions (`routing/BRouterTrailRouter.kt:63`, `:70`, `:78`). `EndOutsideData`, `NoPath`, `DataMissing` and `DataCorrupt` come back unlogged (`:67-71`). The way-home code will log every fallback once per routing attempt, not per tick, through `ErrorLog.w`. Each line names the outcome kind and the region id. No positions go into the log (plan of record constraint, plan update `:73`). A test asserts that each mapped outcome produces exactly one log entry with its kind.

## 6. Walk-back time

**Where it is read today.** Sundown's walk-back has one production caller: `SundownWatch` at `domain/SundownWatch.kt:289`. It calls `returnWalkingTime(track, origin, current, freshness, band)` (`domain/ReturnWalkingTime.kt:57-90`), which measures the distance with `pathHome` over the stored track (`:66`, `:87`). `SundownWatch` is process-scoped in `AppContainer` (`AppContainer.kt:415-425`) and ticked by the recording service (`service/TrackRecordingService.kt:363`), so it runs with the screen off. `BackByWatch` reads no walk-back figure (`domain/BackByWatch.kt`, no call to either function).

**The problem.** Today the route lives in the ViewModel (`ui/track/TrackRecordingRouteTicks.kt:83-96`, state at `ui/track/TrackRecordingUiState.kt:92-97`). With the screen off, or the Activity gone, there is no route for sundown to read. So "the route shown" needs the route to live where sundown can reach it. Options:

- **W1. A process-scoped way-home holder in `AppContainer`, beside `ReturnWatch`.** It is driven by the return's start and stop, and fed by the service. It holds the current `WayHome`. Both the ViewModel's route tick and `SundownWatch` read it. Sundown uses the shown route's metres while a return shows a trail route, and the track as today otherwise. This needs a new function beside `returnWalkingTime`, something like `returnWalkingTimeAlong(meters, …)`, keeping every degrade rule (`ReturnWalkingTime.kt:34-48`). It also needs a freshness rule for the held route. The decision rules (`DecideSundownAlertUseCase`, called at `SundownWatch.kt:302`) are not changed, but their input is.
- **W2. The route stays in the ViewModel and is published to a shared flow for sundown.** Sundown uses it while it is fresh and falls back to the track. When the screen is off, the HUD and the alert would use different figures, in exactly the case the alert exists for. I would not build this.

I recommend W1. It is an architecture decision, and it touches the Sundown watch's input, which the dispatch puts out of bounds. So it is a stop-and-ask (section 9.4).

**Before Return**, no route is shown, so "the route shown" does not apply. I'd keep the track, as today. Computing a trail route before Return, just for sundown, is a different behaviour and isn't ruled.

## 7. The test plan

Every test is written to fail first, against pre-main, with the failure read. Each one named "revert" gets a one-line revert on its own branch, run on CI. The run log is checked for compile errors before its results are cited. Files are restored from a saved copy, never from git, and the forward change is confirmed present afterwards (CLAUDE.md, Testing).

**Domain, headless:**
- the leave-point and entry-point scans, on synthetic tracks against a fake snap. Cases: on the trail now, wandered off, never on a trail, on-off-on (the newest leave wins), and a point exactly at N;
- the joined line's arithmetic: the total equals the parts summed, with each part's metres asserted;
- the lookahead on the joined line;
- the `atReturn` replacement rule;
- the L1 rectangle check, including the antimeridian case `contains` already handles;
- the pair-region choice;
- the outcome-to-note table, every row, including that each fallback logs once with its kind;
- the walk-back switch: with a route held, sundown's figure equals the route's metres divided by pace; without one, it is today's figure.

**The router, on the real engine:**
- the M1 test (section 1), plus its revert;
- if option A is chosen, the snap on the Harriman fixture (`app/src/test/resources/routing/`): a point on a trail snaps within a few metres; a point far from any way returns none. The expected values come from BRouter's command-line router, as the existing route tests are checked (`BRouterTrailRouterTest.kt:56-59`).

**The real entry point:**
- **`TrackRecordingViewModelTest`, through `runRecordingTest`** (`:121-127`, which stops every recording in a `finally`; the route tick is an unbounded loop, see CLAUDE.md's stall rule). Each test calls `vm.startReturn()` with a fake `TrailRouter` and a fake region list, then asserts on `uiState`:
  - a joined line, with the right parts and distance;
  - each fallback's note and today's line;
  - a re-route after K s on the trail part, if R2 is ruled.
- **Revert:** make the route tick ignore the trail route and always use the track. The joined-line assertions fail, naming the missing trail part.
- **Screen:** one `AvailabilityScreen*` test that taps Return with a coordinate touch (`performTouchInput`) and asserts the map's two ahead sources (`routeHomeFeatureCollections`) and the HUD note's text. Per CLAUDE.md, a semantic `performClick` asserts wiring, not routing.

**Off-track:** if section 9.4's off-track question is ruled to feed the shown line to the judge, a `ReturnWatchTest` case: walking the trail route where it leaves the track raises no alert. Its revert feeds the track instead, and the alert comes back.

**Device only** (from the dispatch, unchanged): on the S22, in a downloaded area with routes:
- record a short walk that leaves a trail, then tap Return. Check the dashed part, the solid part, that the HUD distance matches the line, and arrival;
- repeat outside any area, and check the "No trail routes here" note;
- repeat where the route would leave the area, and check the notice.

A green Robolectric suite proves nothing about real map rendering or real insets (CLAUDE.md, Known pitfalls).

## 8. Size

Line counts by `wc -l` at `adcbedb19`. The change estimates are mine: inferred, not measured.

| File | Lines now | This work | Over 800 after? |
|---|---|---|---|
| `routing/BRouterTrailRouter.kt` | 127 | M1 fix, engine seam, snap method (if A) | no |
| `domain/routing/TrailRouter.kt` | 70 | snap method and its result type (if A) | no |
| `domain/routedata/FindRouteDataForPointUseCase.kt` | 57 | the pair function, or a new file beside it | no |
| `domain/RouteHome.kt` | 202 | the lookahead moved into its own function, behaviour unchanged | no |
| `domain/RouteLine.kt` | 34 | parts, and the `atReturn` replacement | no |
| new `domain/WayHome.kt` | — | the type and its assembly, under 400 | no |
| new `domain/WayHomeTrailPoints.kt` | — | the leave and entry scans, under 200 | no |
| new `domain/WayHomeNotes.kt` | — | the outcome-to-note table and logging | no |
| new holder (if W1), e.g. `domain/WayHomeWatch.kt` | — | process-scoped route, start and stop | no |
| `domain/ReturnWalkingTime.kt` | 123 | `returnWalkingTimeAlong` (if W1) | no |
| `domain/SundownWatch.kt` | 441 | reads the held route (if W1); input only | no |
| `AppContainer.kt` | 478 | builds the router, the holder, and wiring | no |
| `service/TrackRecordingService.kt` | 552 | feeds the holder (if W1) | no |
| `ui/track/TrackRecordingRouteTicks.kt` | 106 | reads the trail route; the R2 re-route | no |
| **`ui/track/TrackRecordingViewModel.kt`** | **958** | a constructor parameter and the hand-off to the tick, about 5–10 lines | **already over** |
| `app/TrackRecordingViewModelFactory.kt` | 41 | one wiring line | no |
| `ui/track/TrackRecordingUiState.kt` | 192 | the way-home field and note | no |
| `ui/availability/NavigationHudReadout.kt` | 111 | `ReturnRoute` carries the note | no |
| `ui/availability/NavigationReadout.kt` | 235 | the note in the status line | no |
| `ui/availability/NavigationHudText.kt` | 62 | the strings | no |
| `ui/map/RouteHomeLayers.kt` | 230 | the dashed and solid layers | no |

**Not touched:**
- **`ui/availability/AvailabilityScreen.kt` (1,023).** `returnRoute` and `routeLine` pass through it as values (`:226-227`, `:850-851`). If the note and the parts ride inside `ReturnRoute` and `RouteLine`, this file doesn't change. So it needs no split for this work.
- **`MainActivity.kt` (736).** The ViewModel is built in `TrackRecordingViewModelFactory.kt:12-41`, and `MainActivity.kt:667-673` passes the same fields through.

**The split question for `TrackRecordingViewModel.kt`.** The modular-code skill says: "A fix of a few lines to a big file doesn't need a split first; a new capability does" (`.claude/skills/modular-code/SKILL.md`, "The limits"). Under W1, this file gets about 5–10 lines of wiring for a new capability. I can't say which side of that line it falls on; it's the planner's ruling. If a split is wanted:
- The return functions (`:440-456`, `:794-856`) can't move out. They are the start and stop decisions -1450 keeps in ViewModel functions.
- The candidates are the resync family (`onEnteredForeground`, `onLeftForeground`, `resyncRecordingState`, from `:683`) and `clearRecordingState` (`:564-668`). Each would move to a collaborator in `ui/track/`, as Phase 2.7 did for the poll and the route tick, with one-line delegations left behind like `setBackBy` (`:384`).
- My estimate is that the resync family alone leaves the file at about 850, and both leave it at about 750. That is unverified until the move is written.
- Whether `clearRecordingState`'s body counts as a decision under -1450 is a question for the planner.

**Proposed PRs:**
- **PR 0** (only if a split is ruled): the pure-move split of `TrackRecordingViewModel.kt`.
- **PR 1, no visible change:** the M1 fix and the snap method, the domain types (`WayHome`, the scans, the notes table, L1, the pair region), with their tests. Reviewable alone.
- **PR 2, visible:** the holder (W1), route tick, HUD note, map layers and sundown's input, with the entry-point and screen tests and the S22 check.

Splitting at the visible line keeps PR 2's phone check focused. It also lets M1 land before any production caller exists, as -1572 asks.

## 9. The four disclosure sections

### 9.1 Confirmed vs inferred

**Confirmed by reading, at `adcbedb19`:**
- every premise listed in 9.3 as holding;
- `TrailRouter`, `BRouterTrailRouter` and `FindRouteDataForPointUseCase` have no production caller. `git grep` over `app/src/main` finds no reference outside their own files, and `AppContainer.kt` builds neither;
- BRouter's catching range, 250 m with no override in the profile, and the `terminate()` mechanics (section 1);
- the cut margin's size (section 5);
- the only walk-back reader is `SundownWatch.kt:289`;
- `TrackRecordingUiState.pathHome` is read only by tests.

**Inferred, not verified:**
- that a GETINFO snap is cheaper than a route;
- that the engine's first route point is the snapped point;
- the CPU share of R2;
- whether `line-dasharray` can be data-driven;
- every line estimate in section 8;
- that L1 catches every route leaving by less than the margin.

That last one follows from the margin being in the data. No test shows it yet.

### 9.2 Could not determine

- **Battery cost** of any routing schedule: never measured.
- **The routes behind -1572's S22 timing** (lengths, data, warm or cold engine): only the RECORD's one-line relay exists. I found no on-branch report of dispatch 02's device check.
- **How far OSM trail geometry sits from GPS under canopy.** The only spread figure is the spike's, on roads, measured on the laptop (spike report `:140`). N rests on that.
- **Whether `clearRecordingState` counts as a "decision" function under -1450**, which decides whether it can move in a split.

### 9.3 Premises that were wrong, or need correcting

All the dispatch's premises hold at `adcbedb19`, with line numbers matching, except these points:
- **The M1 fix's suggested shape.** `invokeOnCancellation` belongs to `suspendCancellableCoroutine`, which a blocking `doRun` can't use as written. The fix needs `doRun` on a thread the continuation doesn't own, and the lock released when the engine actually stops (section 1).
- **Today's line during a return can already raise the off-track alert, and a trail route makes that worse.** This is not a premise the dispatch stated, but it matters to every premise about step 5 standing alone. `ReturnWatch.startReturn` takes the kept points as the judged path (`domain/ReturnWatch.kt:187`). The judge measures against start plus that path (`:255`), with a 40 m line and a 15 s hold (`domain/OffTrackJudge.kt:8`, `:11`), and the Return control turns red when off track (`ui/availability/TrailheadControls.kt:182`). A walker who follows a trail route home, where it runs more than 40 m from their walked track, gets the OFF_TRACK alert for following the app's own line. Step 5 can't fix that without touching the Off-track rules' input, which the dispatch forbids and which step 7 owns. See 9.4.
- **"Where they left the trail" can't come from the engine alone.** With dynamic range off, the engine still snaps up to 250 m. Handing it the walker's position would produce the straight-line shortcut -1498 rules out (section 2).

### 9.4 Decided beyond scope, or left for the owner

Nothing is decided beyond scope. These are left open, as choices:

1. **The off-track alert during a trail route home.** (a) Steps 5 and 7 ship together, so step 5 never reaches a phone alone. (b) Step 5 hands the judge the shown line instead of the walked track. The rule stays; its input changes. (c) Ship step 5 alone, and accept alerts on the trail part.
2. **Where the route lives.** W1, a process-scoped holder that sundown also reads. W2, ViewModel-only, with sundown falling back.
3. **Sundown's input changes under W1.** Accept, or keep sundown on the track until a later dispatch.
4. **How the leave point is tested.** A, a new snap method; B, routes as the test.
5. **N:** 15, 25 or 50 m, provisional.
6. **The far end:** the trail route ends at the entry point, then the walker's own track to the start (my proposal). Or the route ends wherever the engine snaps near the start, with a joint of up to N.
7. **Re-routing:** R1, once with forward-only projection, which reopens the projection ruling for the trail part. R2, a periodic re-route on the trail part, every K s with K provisional. Either, with R3's far-band trigger.
8. **While computing:** a silent swap, or a "Finding trail route…" note.
9. **Dashed meanings:** the track part dashed alongside the dashed waypoint line, or a different treatment for the track part.
10. **The unruled fallback notes** in section 5's table: routes not fetched, missing or damaged; never near a trail; no path; corrupt or missing data; engine failure. The `SquareMissing` reading needs confirming too.
11. **-1547 detection:** L1 alone, L1 + L2, or L1 + L3.
12. **Splitting `TrackRecordingViewModel.kt`:** needed for about 5–10 wiring lines, or not. If needed, which block moves.
13. **Before Return:** sundown keeps the track (my proposal), or a trail route is computed before Return.
