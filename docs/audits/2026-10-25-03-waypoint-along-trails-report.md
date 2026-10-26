# Navigate to a waypoint along trails: report before building (dispatch 2026-10-25-03, plan step 6)

**Status: a report. Nothing is built.** No app code, no Gradle, no phone for this report. Nothing is built until the owner rules on it (RECORD -1629).

**Written by:** Hazel Cairnwright (app coder, Opus), 2026-10-26 UTC. It replaces the pause note pushed at `a5f544fa3`. The report was paused for the #404 diagnosis (RECORD -1650), which is now reported.

**Base, checked against the remote at 2026-10-26T05:00Z:**
- **App code** read at `origin/pre-main` `a41761c8f`. `git ls-remote` gives the same head. That is later than the dispatch's floor `f5a0cfafc`: it has #398, #399, #400, #401, #402 and #405 merged.
- **This branch,** `waypoint-trails-report`, is cut from `origin/records-after-173` at `4e9e5c051`.

**Paths and sources.**
- App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`, and every line number is at `a41761c8f`.
- Two read-only helper surveys gathered the first map of the code. Every line cited below was re-read by me at `a41761c8f`, apart from those marked "(survey)". Those come from the surveys and are pointers for the build's own re-read.

**No position is in this report.** Every figure is metres, seconds or counts.

---

## 0. The short version, for the owner

**Today:**
- Navigate on a waypoint draws a straight dashed line from you to it.
- The HUD gives the straight-line distance, the word "straight", and a needle at the waypoint.
- It needs no recording.
- It works only while the app is on screen.
- Nothing about trails is used.

**What I propose:** the same Navigate, but inside a downloaded area with trail routes the line becomes three parts:
1. a short straight dashed leg from you to the nearest trail;
2. the trail, solid, as on the way home;
3. a short straight dashed leg from the trail to the waypoint.

The HUD reads the distance "by trail". Leave the line by more than 50 m and it re-routes from where you are, once per stray, as the way home does. Outside an area, or when no trail route is found, you get today's straight line with a short note saying why.

**Reused unchanged:**
- the trail engine and the region's route data;
- how a walker is placed on a line, and the distance figure;
- the line ahead, and its dotted and solid drawing;
- arrival;
- the navigation view;
- Back.

**Genuinely new:**
- a way to start and end the trail part at points that are not on the walker's track;
- a third kind of line part, the straight dashed leg;
- a region that covers both ends;
- the waypoint's own notes;
- a re-route that works without a recording.

**Five decisions for the owner** are in section 5. The main one is Q1: how the line gets from you to the trail. The step paths to confirm are in section 3.

**Four PRs, in order** (section 4):
1. Move the straight line out of the screen into the ViewModel. This is the planned move, and a pure move.
2. The waypoint route in the domain, with nothing visible.
3. The route on the map and the HUD.
4. Step 7's quiet off-trail state applied to it. Step 7's PR 3b, written after step 7 builds.

---

## 1. Today's waypoint navigation, read from the code

### 1.1 How it starts and stops

**Entry points.** Navigate is offered in five places:
- the Maps tab's bubble (`ui/map/MapFeatureBubble.kt:91`) and its details sheet;
- a Records row and its details sheet (`ui/log/RecordsTab.kt:271`, `:456-460`);
- a journal entry's map (`ui/log/JournalTab.kt:691-695`).

All of these are survey lines. All five reach `startWaypointNavigation` (`ui/availability/AvailabilityScreenWaypointNavigation.kt:34-46`, survey). That calls the ViewModel through `MainActivity.kt:676-677` (read): `viewModel::onNavigateToWaypoint` and `viewModel::onStopWaypointNavigation`.

**Owner of start and stop: `AvailabilityViewModel`.** Its waypoint functions are in their own file, `ui/availability/AvailabilityViewModelWaypointNavigation.kt` (49 lines): start at `:22-36`, stop at `:43-49` (survey).
- **The state:** `AvailabilityUiState.waypointNavigation`. It "needs no recording: the live fix it measures from is this ViewModel's own" (`ui/availability/AvailabilityUiState.kt:343-346`, read).
- **The store:** the chosen waypoint survives the app being closed. It is a DataStore through `WaypointNavigationRepository` (`domain/WaypointNavigation.kt:33-39`, read; implementation `data/repository/DataStoreWaypointNavigationRepository.kt`, survey).

**A return under way is paused, and resumed at the end** (`domain/WaypointNavigation.kt:47-57`, read).
- Pausing is the return's own Stop (`ui/track/TrackRecordingReturnLeg.kt:14`, survey). So the way home, the off-track alert and the walked-path line all stop while a waypoint is the target (`domain/WaypointNavigation.kt:15-16`, read).

**How it ends:**
- the X in the control pill or on the HUD;
- Back, which goes back to where Navigate was tapped;
- deleting the waypoint, which shows "Stopped navigating: that waypoint was deleted."

All of these are survey lines: `AvailabilityScreenWaypointNavigation.kt:50-71`, `AvailabilityScreenBackHandlers.kt:125-136`, `AvailabilityScreenNotices.kt:100-106`. **Arrival does not end it** (`domain/WaypointNavigation.kt:13`, read).

### 1.2 Where the straight dashed line is produced

**Still in the composable, not in a ViewModel.** See premise 1 in section 8.3.
- `AvailabilityScreen.kt:422` holds it: `var waypointStraightLine by remember { mutableStateOf<StraightLine?>(null) }`.
- A clock at `:423-430` (`produceState`) moves on when the fix's freshness changes, so the line fades without a new fix.
- `:431-432` works out the next line (`nextStraightLine`) and writes it back in a `LaunchedEffect`.

All read. The pure helpers are in `ui/availability/NavigationArrival.kt` (106 lines): `straightLineToTarget`, `StraightLine`, `nextStraightLine` and `millisUntilFreshnessChanges` (survey: `:53-106`).

**How it reaches the map:**
- `WaypointNavigateControls.straightLine` (`ui/availability/WaypointNavigateControls.kt:22`, read). Its doc says it is "worked out and held in `AvailabilityScreen`" (`:11-12`).
- Then through `AvailabilityCompactScaffold.kt:1226` (read), into `CompactMapNavigation.waypointStraightLine` (`CompactMapTabInputs.kt:54-60`, read).
- Then into `RouteOnMap(routeLine, arrivedAt, waypointStraightLine?.points, …)` (`CompactMapMapSlot.kt:84`, read).
- It is drawn by the `STRAIGHT` layers of `ui/map/RouteHomeLayers.kt`: 3 dp, dashes 2 by 2 (`:94`, `:97`, read).

### 1.3 Where the position comes from

- **The ViewModel's own live fix,** not the recording service. `AvailabilityLiveLocation.begin` collects the location tracker (`ui/availability/AvailabilityLiveLocation.kt:63-66`, read).
- **It runs only in the foreground:** `onEnteredForeground` begins it and `onLeftForeground` cancels it (`AvailabilityViewModel.kt:399-402`, `:425-428`, read).
- **So with the screen off, today's waypoint navigation does nothing.** No line moves and no arrival is judged. That is inferred from those two functions, with no other caller found by the survey.

### 1.4 The HUD, sundown and notes

**The HUD is given no route for a waypoint:** `route = if (navigation.isNavigatingToWaypoint) null else navigation.returnRoute` (`CompactMapPortraitNavigationCard.kt:123`, `CompactMapLandscapeNavigationCard.kt:168`, read). With no route:
- **The figure** is the straight-line distance.
- **The kind word** is "straight".
- **The needle** aims at the waypoint.
- **"Approaching"** shows within 100 m.
- **Arrival** is within `max(15 m, 2 × accuracy)`: `hasArrived` (`domain/NavigationReadout.kt:67-71`, `APPROACHING_ACCURACY_MULTIPLIER = 2.0` at `:57`, read).

The other readout lines are survey: `ui/availability/NavigationReadout.kt:85-125`.

**Sundown and Back by** do not use the waypoint. With a recording they show the recording's walk back to the start; without one, sunset and dark only (survey: `CompactMapPortraitNavigationCard.kt:49-59`; `domain/SundownWatch.kt:57-60`).

**Notes:** there are no waypoint notes. The route-note branches in the readout are the return's (survey).

---

## 2. What the way home gives a waypoint, and what is genuinely different

### 2.1 Reused unchanged

| Piece | Why it fits | Where |
|---|---|---|
| The engine: `TrailRouter.route(start, end, routeData)` | Any two points, one region's route folder | `domain/routing/TrailRouter.kt:27` (read) |
| The snapper: `TrailSnapper.nearestTrail(point, toward, routeData)` | The nearest routable way to any point, within the engine's 250 m | `domain/routing/TrailSnapper.kt:22-26` (read) |
| Which region holds a point: `routeDataForPoint`, `routeAreaFor` | Per point | `domain/routedata/FindRouteDataForPointUseCase.kt` (57), `RegionRouteData.kt:177` (survey) |
| Placing the walker and the figure: `placeOnWayHome`, the 60 m reach (-1620), the 25 m trail allowance | They work on any line of parts | `domain/wayhome/WayHomeProgress.kt:128-149` (survey; the function read in full on `b50afeb73` for the #404 diagnosis) |
| The line ahead: `wayHomeAhead`, `partsFrom`, `RouteLine`, `nextRouteLine` | Any line of parts; a new generation swaps the line in | `WayHomeAhead.kt:27-86`; `domain/RouteLine.kt:41-51` (read) |
| Drawing: the dotted and solid runs by the `part` property | Any line of parts | `ui/map/RouteHomeLayers.kt:182-202` (read) |
| The HUD figure: `returnRouteOf(routeHome, note)` | Pure | `ui/availability/NavigationHudReadout.kt:111-115` (survey) |
| The re-route rule: over 50 m from the line re-routes; back under 45 m re-arms; one re-route per stray (-1625: "re-routes once" means one per stray) | The rule is target-free | `WayHomeWatch.kt:148-168` (survey); RECORD -1625 (read) |
| Arrival, the navigation view, Back, the paused return, the stored target | Unchanged | Section 1 |

### 2.2 Tied to the way home and not reusable as it stands

1. **Both ends of the trail part are points of the walker's track.**
   - `FindWayHomeUseCase` routes from the leave point to the entry point (`FindWayHomeUseCase.kt:83`), both found by scanning the stored track (`:71`, `:76`).
   - Its first step needs two stored points (`:61`). All read.
   - **For a waypoint, the far end is the waypoint, and often there is no walked track towards it.** The near end may have no track at all: navigation needs no recording.
2. **The way the walker reaches the trail.**
   - On the way home, the line runs along the walker's own track back to where they left the trail. That is ruled at -445 and -1498 answer 3 (read). It is why the engine is never handed the walker's raw position: "joined to them by a straight line through the forest, the shortcut RECORD -1498 rules out" (`domain/wayhome/TrailJoins.kt:14-18`, read).
   - **For a waypoint ahead, the track behind the walker usually leads the wrong way.** This is the main decision: Q1.
3. **The region.** The way home picks the region from the track's first point (`FindWayHomeUseCase.kt:128`, read). A waypoint route needs one region whose routes cover both ends. Nothing finds that today; the helper's own doc says so: "a caller routing between two points may want the region covering both, which is a different question" (`FindRouteDataForPointUseCase.kt:16-18`, read).
4. **The checks that need a walked track.** The detour check (over 1.5 times the track between the same points) and the short-walk cap compare the trail with the walked track (`FindWayHomeUseCase.kt:93`, `:98`, `:105`, read). A waypoint has no such track. That is Q3.
5. **The line's shape.** `WayHome` has three fixed parts: track back, trail, track to the start (`domain/wayhome/WayHome.kt:19-46`, survey). A waypoint's line is lead-in, trail, final leg. Both legs are straight, so neither should be drawn dotted: dotted means "your own track" (-1589, "Dotted, same colour").
6. **Who drives it.**
   - `WayHomeWatch` is fed by the recording service, and only acts while returning: `isReturning` gates both `progressFor` and `onFix` (`WayHomeWatch.kt:119`, `:128`, read).
   - The screen's route tick returns at once unless returning (`ui/track/TrackRecordingRouteTicks.kt:90`, read).
   - A waypoint navigation pauses the return, so none of these run during it. Its fixes are the Availability ViewModel's, in the foreground only.
7. **The notes' wording.** Every way-home note ends "· following your track", and one says "your way out" (`WayHome.kt:52-88`, survey). For a waypoint the fallback is the straight line, so the words differ (Q4).
8. **Sundown.** Sundown reads the way home's figure as the walk back (-1589, "app-wide"; wired in `AppContainer.kt:459`, survey). A waypoint's distance is not the walk back, so it must not feed sundown. Section 3.5.

---

## 3. Step paths for the owner to confirm

Each path is: you do something > the app shows something > Back. The figures are provisional, for a walk to test. "Trail line" is the solid part. "Straight leg" is a thin dashed part, like today's line.

### 3.1 Navigate, inside a downloaded area with trail routes

1. Tap a waypoint > the bubble shows "Navigate" (unchanged).
2. Tap Navigate > the map switches to the navigation view, and today's straight dashed line shows while the route is worked out.
3. About a second later (the way home's routes took 0.6 to 0.7 s on the S22, -1625) > the line is swapped, without a blank frame, for three parts:
   1. a straight leg from you to the nearest trail (Q1);
   2. the trail line;
   3. a straight leg from the trail to the waypoint.

   The HUD shows the distance along that line, "by trail", and the needle along the line.
4. Walk it > the figure counts down along the line.
5. Back > as today: navigation ends and you go back to where you tapped Navigate. A paused return picks up again.

### 3.2 Navigate, outside a downloaded area, or with no trail route found

1. Tap Navigate > today's straight dashed line, the straight distance and "straight". **New:** a short note in the HUD's status line says why. For example, "No trail routes here · straight line" (Q4).
2. Back > as today.

The note's reasons are the way home's: routes not downloaded, damaged, being fetched or needing an app update; no route found; the route leaving the area. They add three of the waypoint's own: the waypoint far from any trail, you far from any trail, and the two ends in different areas.

### 3.3 Leaving the trail on the way

1. Step off the trail line > nothing changes for now.
2. More than 50 m from the line > it re-routes from where you are now: a new straight leg to the nearest trail, then on to the waypoint. This is once per stray, as on the way home (-1625).
3. **Quiet:** no sound and no vibration. While a waypoint is the target, nothing alerts, today or after this. Step 7's dimmed trail and "Back on trail" come to the waypoint in PR 4.
4. Back > as 3.1 step 5.

### 3.4 Arriving

1. Within 15 m, or twice the fix's accuracy if that is wider > the ring with a check, and "Arrived". The line ends. Navigation stays on until you end it. **Unchanged.**
2. **Arrival is still judged on the straight distance to the waypoint,** not on the line. Being at the trail end of the last straight leg is not arriving.
3. Back or the X > it ends; a paused return picks up again.

### 3.5 What does not change

- **Sundown and Back by** stay the walk back to your start, as today.
- **With the app closed or the screen off,** the line is not re-worked and nothing is judged, as today (Q5).

---

## 4. The proposed PRs, in order

### Modular-code answers (`.claude/skills/modular-code/SKILL.md`, on pre-main)

**1. Files touched, and their sizes** (`wc -l` at `a41761c8f`):

| File | Lines | PR |
|---|---|---|
| `ui/availability/AvailabilityScreen.kt` | **1,023** | 1 (lines move out; nothing is added) |
| `ui/availability/AvailabilityViewModel.kt` | 761 | 1, 3 (a few lines each; the work is in collaborators) |
| `ui/availability/AvailabilityUiState.kt` | 409 | 1, 3 |
| `ui/availability/AvailabilityViewModelWaypointNavigation.kt` | 49 | 3 |
| `AvailabilityViewModelFactory.kt` | 64 | 3 |
| `AppContainer.kt` | 512 | 2 |
| `MainActivity.kt` | 736 | 3 (the route source while a waypoint is the target, about 4 lines) |
| `routing/WayHomeRouting.kt` | 62 | 2 |
| `domain/routedata/FindRouteDataForPointUseCase.kt` | 57 | 2 |
| `domain/wayhome/WayHome.kt` | 157 | 2 (one new part kind) |
| `domain/wayhome/WayHomeProgress.kt` | 224 | 2 (one line: the new kind's mapping) |
| `domain/RouteLine.kt` | 51 | 2 (`RouteLinePartKind.STRAIGHT`) |
| `ui/map/RouteHomeLayers.kt` | 273 | 3 (one layer for straight parts) |
| `ui/availability/CompactMapMapSlot.kt` | 141 | 3 |
| `ui/availability/CompactMapPortraitNavigationCard.kt` | 139 | 3 (`:123`) |
| `ui/availability/CompactMapLandscapeNavigationCard.kt` | 195 | 3 (`:168`) |
| `ui/availability/NavigationHudText.kt` | 73 | 3 (the notes' short forms) |
| `domain/WaypointNavigation.kt` | 94 | 3 (its step path, -1498 answer 6) |

**2. Over about 800 lines:**
- **`AvailabilityScreen.kt`, 1,023.** PR 1 takes the straight line out of it. That is the move the plan already named (plan update, step 2, "it moves to a ViewModel home (-1450)"), and nothing new goes into the file. After PR 1, no PR here adds to it.
- **`AvailabilityCompactScaffold.kt`, 1,441.** Not touched. The waypoint route rides in the inputs the scaffold already passes through unchanged: `returnRoute`, `routeLine` and `waypointStraightLine` (`AvailabilityCompactScaffold.kt:1222-1226`). `MainActivity` chooses what they carry.
- **`ui/track/TrackRecordingViewModel.kt`, 864.** Not touched.
- **`AvailabilityViewModel.kt`, at 761, stays under 800.** The new work is in collaborators (point 3). If a build finds it would cross 800, that is a stop.

**3. New files, one job each:**
- `ui/availability/AvailabilityWaypointLine.kt`, about 100 lines (PR 1): holds the straight line and its freshness clock for the ViewModel.
- `routing/TrailRoutingEngine.kt`, about 60 lines (PR 2): the one engine and snapper, built on first use. Today they are private to `WayHomeRouting`, and BRouter runs under one process-wide lock (`routing/BRouterTrailRouter.kt:121-144`, survey). So both features must share one engine.
- `domain/routedata/FindRouteDataForPoints.kt`, about 50 lines (PR 2): the newest downloaded region whose routes hold both points' squares.
- `domain/wayto/WayToWaypoint.kt`, about 80 lines (PR 2): the result and its notes.
- `domain/wayto/FindWayToWaypointUseCase.kt`, about 200 lines (PR 2), pure. It snaps the walker and the waypoint, finds the region for both, routes, checks the area and Q3's rule, and builds the line: lead-in, trail, final leg.
- `domain/wayto/WayToStray.kt`, about 60 lines (PR 3), pure: the re-route rule, the way home's 50 and 45 m and once per stray, on the ViewModel's fixes.
- `ui/availability/AvailabilityWaypointRoute.kt`, about 150 lines (PR 3): the ViewModel's collaborator that runs the search job. Starting and stopping stay in ViewModel functions (-1450).

**4. Order-sensitive parts:**
- **PR 1 removes a `remember` and its `produceState` and `LaunchedEffect` from `AvailabilityScreen`.** Under the skill, effect order and `remember` keys are behaviour. The three effects that follow them in the composable then launch one place earlier. I expect no change, because nothing reads the line's position in that order (inferred, not tested). It still needs the phone check in section 6, and I flag it to the planner as the skill asks. The line must keep outliving a tab change; in the ViewModel it does, by construction.
- **No new `Box` child and no Back handler.**
- **One new map layer** in `RouteHomeLayers`' existing list, beside the dotted layer. Its paint position is within that group, and the group's place is unchanged.

### PR 1: the straight line moves to the ViewModel (pure move)

- **Behaviour:** none changes.
- **Files:** `AvailabilityScreen.kt` (`:416-432` go), `AvailabilityViewModel.kt`, `AvailabilityUiState.kt`, and the new `AvailabilityWaypointLine.kt`. `ScreenMapBundles.waypointNavigateControlsFor` then reads the line from the UI state (survey: `ScreenMapBundles.kt:44-61`; it may need one line).
- **Evidence:** the line's existing tests pass unchanged before and after: `AvailabilityScreenWaypointNavigateTest` (arrival, faded on stale, grey on lost, none until the first fix, kept across a tab change), `StraightLineToTargetTest` and `RouteStraightLineLayersTest` (survey names).
  - That is the right evidence for a pure move. Under CLAUDE.md, a check that passes identically before and after is suspect, so I say plainly that it is the intended evidence here, not a sign that it bites.
  - A new ViewModel test drives `onNavigateToWaypoint` and a fake clock, and asserts the line fades at 30 s. It is seen failing over a stub first.

### PR 2: the waypoint route, in the domain (nothing visible)

- **Files:**
  - `routing/WayHomeRouting.kt`: uses the shared engine.
  - `AppContainer.kt`: builds the engine once, and the use case.
  - `FindRouteDataForPointUseCase.kt`, untouched apart from a doc pointer to the new two-point helper.
  - `WayHome.kt`: `WayHomePartKind.STRAIGHT`.
  - `WayHomeProgress.kt`: maps it to `RouteLinePartKind.STRAIGHT`.
  - `RouteLine.kt`: the new kind.
  - The four new domain and routing files.
- **The line reuses `WayHome`'s three slots:** the lead-in in `trackBack`, the trail, and the final leg in `trackToStart`, each tagged `STRAIGHT`. That way `WayHomeLine`, `placeOnWayHome`, `wayHomeAhead` and the drawing work unchanged.
  - The names then read oddly for a waypoint ("trackToStart" holding the final leg). That is naming debt I am choosing, stated so it can be overruled.
  - The alternative is a new line type and copies of four functions.
- **Tests (pure, fake engine), each failing first over a stub:**
  - walker and waypoint each near a trail: a three-part line, with a trail part between the snaps;
  - waypoint over 250 m from any trail: no route, with that note;
  - ends in different regions: no route, with that note;
  - a trail point outside the area: no route;
  - Q3's rule at both sides of its figure;
  - the shared engine is built once for both features, and the way home's tests pass unchanged.
- **Revert checks:** one edit per rule, on its own scratch branch, under CLAUDE.md's runner rules.

### PR 3: the route on the map and the HUD

**Files:**
- `AvailabilityViewModelWaypointNavigation.kt`: start the search at Navigate, and drop it at stop.
- `AvailabilityViewModel.kt`, `AvailabilityViewModelFactory.kt`, `AvailabilityUiState.kt`.
- The new `AvailabilityWaypointRoute.kt` and `WayToStray.kt`.
- `MainActivity.kt`: while a waypoint with a found route is the target, `returnRoute` and `routeLine` carry the waypoint's (`MainActivity.kt:672-673`).
- The two navigation cards: the HUD gets the route when there is one, at `:123` and `:168`.
- `CompactMapMapSlot.kt`: no straight line while a trail route is shown (`:84`).
- `RouteHomeLayers.kt`: the straight-part layer.
- `NavigationHudText.kt`: the short notes.
- `domain/WaypointNavigation.kt`: the step path's steps 3 and 7.

**Tests:**
- **Through the real entry point:** the ViewModel's `onNavigateToWaypoint` with a fake engine gives a route with a trail part. It **fails first** on today's code, where the straight line is all there is.
- **The screen** (Robolectric): the HUD reads "by trail" and the map's ahead source carries a trail part. Outside an area, the straight line and its note stay.
- **The stray:** 51 m from the line re-routes once; 49 m does not; back within 45 m re-arms.
- **Arrival** still at 15 m straight to the waypoint.
- **Back and X** still end it and resume a paused return. The existing `AvailabilityScreenWaypointNavigateTest` cases stay green.
- **Real touches:** the taps-below-the-HUD test stays a coordinate touch (CLAUDE.md).
- **One revert check per rule.**

### PR 4: step 7's quiet off-trail state, for a waypoint route

The step 7 report names this PR 3b, and leaves it to be written once this report says where the waypoint line lives. The answer is: in `AvailabilityViewModel`'s state, fed by the ViewModel's foreground fixes, not by the recording service. So step 7's `WayHomeOffTrail` (its PR 3) would be called from `AvailabilityWaypointRoute`, not from `WayHomeWatch`. Written after step 7's PR 3.

### Files shared with step 7 and with the open PRs

| File | This report | Step 7 (`off-trail-report`, section 6) | Open PRs |
|---|---|---|---|
| `AppContainer.kt` | PR 2 | PRs 1, 4 | |
| `ui/map/RouteHomeLayers.kt` | PR 3 | PR 3 | |
| `ui/availability/CompactMapMapSlot.kt` | PR 3 | PR 3 | |
| `ui/availability/NavigationHudText.kt` | PR 3 | PR 3 | |
| `domain/wayhome/WayHomeProgress.kt` | PR 2 (one line) | PR 3 (read) | |
| `domain/WaypointNavigation.kt` | PR 3 | PR 3b (here PR 4) | |
| `domain/wayhome/WayHomeWatch.kt` | not touched | PR 3 | #404 |
| `service/TrackRecordingService.kt`, `RecordingWatches.kt` | not touched | | #404 |
| `domain/WayHomeOnCourse.kt` | not touched | | #406 |

- **Step 7's PRs 1 and 2 are merged:** #399 for the off-track alert on the trail, and #400 "Re-center". So of step 7's own list, only its PR 3 and PR 4 remain to sequence against this.
- **The order I propose:** PR 1 any time; PR 2 any time; then PR 3 and step 7's PR 3 one after the other (one coder per file), in whichever order the planner sets; then PR 4.
- **Nothing here waits for #404 or #406.**

---

## 5. The owner's decisions

**Q1. From you to the trail.**

| Option | What you see | Cost |
|---|---|---|
| **(a) A straight leg to the nearest trail. Recommended.** | A thin dashed line from you to the nearest trail point, then the trail. | Simple, and works with or without a recording. It is the "straight line through the forest" that -1498 ruled out for the way back. That ruling was about returning in failing light along ground you have not walked. Here the waypoint is new ground anyway. |
| (b) Your own track back to the trail when recording, else (a). | As the way home, where your track came from a trail. | Often points you backwards, to the trail you came in on, when a nearer trail lies towards the waypoint. More code: the held kept points of #404 would be needed here too. |
| (c) A trail route only when you are already on a trail (within 25 m); otherwise today's straight line. | The trail line only once you reach a trail. | No straight lead-in at all. But someone foraging off the trail sees no trail line until they find one. |

**Q2. From the trail to the waypoint:** a straight leg from the trail point nearest the waypoint. Confirm. A waypoint over 250 m from any trail, the engine's reach, gets today's straight line with a note. 250 m is the engine's figure, not a chosen one.

**Q3. When the trail route is not worth it.**
- **(a) Recommended:** under 150 m straight to the waypoint, today's straight line. That is the way home's short-walk figure (`WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS`), reused.
- (b) Also refuse a trail route over some multiple of the straight distance. The way home's 1.5 does not transfer: there it compares with the walked track, and here with a straight line, which trails always exceed. This needs a figure from a walk.

**Q4. The notes.** The way home's fallbacks end "· following your track". For a waypoint the fallback is the straight line, so I propose the same notes ending "· straight line". For example:
- "No trail routes here · straight line";
- "Trail routes not downloaded · straight line".

Three are new:
- "The waypoint is far from any trail · straight line";
- "You're far from any trail · straight line";
- "The waypoint is in another area · straight line".

The short forms drop the tail, as the way home's do. Wording is the owner's.

**Q5. With the app closed or the screen off.**
- **(a) As today. Recommended:** the route is worked out and re-routed only while the app is on screen.
- (b) Run waypoint navigation in the background, like the recording. That is a separate feature, with its own battery cost and notification.

---

## 6. Device-only items (a green suite proves nothing about these)

1. **The route time for two snaps and a route** on the S22. The way home measured 0.6 to 0.7 s (-1625). This search adds a snap of the waypoint.
2. **Whether the nearest trail the engine picks is the one a walker would choose,** at junctions and where two trails run close. Sorrel's straightened bends (`prompts/preserved/2026-10-25-07-report.md`) apply here too.
3. **The thin straight legs beside the solid trail and the dotted track,** by day and night, at 80% map chrome. Whether three kinds of line read at a glance.
4. **"by trail" and a note in the HUD** at 360 dp and in landscape.
5. **PR 1's effect order:** the faded line, and the line kept across a trip to the Journal, on the phone as well as in Robolectric.
6. **The foreground-only behaviour:** what the walker sees on turning the screen back on mid-route. Inferred: a re-place, and a re-route if far off.

---

## 7. Not in this report

- **Step 7's alerts.** While a waypoint is the target there are none, and none are proposed.
- **Elevation.** Routing stays flat (-1498 answer 2).
- **The recording line trailing the walker** (the #404 diagnosis's observation). It is separate.

---

## 8. The four disclosure sections

### 8.1 Confirmed vs inferred

**Confirmed by reading at `a41761c8f`:** every line marked "read" above, including:
- where the straight line is produced (`AvailabilityScreen.kt:416-432`);
- that the HUD gets no route for a waypoint (`:123`, `:168`);
- the foreground-only live fix;
- the way home's track-bound steps (`FindWayHomeUseCase.kt:61`, `:71`, `:76`, `:83`, `:93`, `:98`, `:105`, `:113`, `:128`);
- the isReturning gates (`WayHomeWatch.kt:119`, `:128`; `TrackRecordingRouteTicks.kt:90`);
- the engine and snapper signatures and the 250 m reach;
- the helper doc on two-point regions;
- the file sizes in section 4.

**From the helper surveys, not re-read by me:** lines marked "(survey)". They are pointers for the build's own re-read.

**Inferred:**
- that with the screen off nothing in today's waypoint navigation runs;
- that PR 1's effect reordering changes nothing visible;
- that the shared engine lock makes one engine instance necessary;
- the new files' sizes;
- that `WayHome`'s three slots can carry a waypoint line with no change to the placing and drawing functions beyond the one new kind.

### 8.2 Could not determine

- **The route time** with two snaps (device).
- **How often the nearest trail point is a sensible one** (device, and data).
- **Whether "by trail" and a note fit the landscape HUD** (device).
- **How many downloaded areas a waypoint and a walker typically straddle.** Nothing on the phone records it.

### 8.3 Premises that were wrong, or need correcting

1. **"Where the straight dashed line is produced (moved to a ViewModel home by -1450 / step 2's splits)"** (the dispatch, question 1). It has not moved. It is still produced and held in the `AvailabilityScreen` composable (`:416-432`), and its own docs say so (`WaypointNavigateControls.kt:11-12`, `CompactMapTabInputs.kt:56-58`). The plan's step 2 named the move, and it was never done. PR 1 is that move.
2. **The plan update's file sizes** (`docs/plans/2026-10-21-trail-navigation-plan-update.md`, step 2) have moved on. At `a41761c8f`:

   | File | Plan | Now |
   |---|---|---|
   | `TrackRecordingViewModel.kt` | 1,029 | 864 |
   | `MainActivity.kt` | 806 | 736 |
   | `AvailabilityViewModel.kt` | 976 | 761 |
   | `AvailabilityScreen.kt` | 1,324 | 1,023 |

   Only `AvailabilityScreen.kt` and `TrackRecordingViewModel.kt` are still over 800.
3. **"The same treatment as the way home"** (-1498 answer 6) cannot mean the same lead-in. The way home's lead-in is the walker's track back, and a waypoint usually lies elsewhere. So answer 6 is read here as the same trail line, notes, re-route and quiet off-trail behaviour, with Q1 deciding the lead-in. That reading is mine, and it is the owner's to confirm.
4. **The dispatch's base, `f5a0cfafc`,** is behind the remote. Six PRs have merged since, and step 7's PRs 1 and 2 are among them. This changes the shared-file list (section 4), not the design.

### 8.4 Decided beyond scope, or left for the owner

**Left for the owner:** Q1 to Q5, and the notes' wording.

**My choices, stated so they can be overruled:**
- reusing `WayHome`'s three slots, with a new STRAIGHT kind, rather than a new line type (PR 2);
- one shared engine for both features (PR 2);
- the route travelling through the existing `returnRoute` and `routeLine` inputs, chosen in `MainActivity`, so the 1,441-line scaffold is not touched (PR 3);
- arrival judged on the straight distance to the waypoint, not on the line (3.4);
- the waypoint route kept out of sundown (3.5);
- no audit-index row: it is a serialization point, and the dispatch did not ask for one.

**Ran on:** Opus (Hazel), per the dispatch.
