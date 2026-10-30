# Waypoint route plotting: the plan, 2026-10-29

**Status: a plan. Nothing in it is dispatched.** Written by the planner (Rowan Waymark) on 2026-10-29 against pre-main 21d846b9e. It draws on a read-only research pass over that commit, whose citations are below. It covers the owner's decisions at RECORD -1817, -1818 and -1821. Each step is its own dispatch, and each starts only on the owner's go.

## What the owner asked for
"Waypoint to waypoint path plotting should be a thing also. In case people want to plot paths for possible trail runs." It is plotting, not navigation: "This isn't navigation, it's just plotting a route between two waypoints" (-1821).

## The behaviour, as confirmed
```
Maps tab
 tap waypoint A ─> bubble gains "Plot route from here"
 tap it ─> plotting: tap waypoint B ─> trail line A→B, "1.4 mi"
          tap waypoint C ─> line B→C added, "2.9 mi total"
 [Undo last]  [Save to Trip Planner]  [Cancel]
 Back ─> leaves plotting in one tap (owner: "Yes, but Back cancels")
 Save ─> name, date optional ─> it appears in the Trip Planner
Trip Planner ─> tap the route ─> its line is drawn on the map, with its distance
```
- **A leg with no trail data:** the leg's two waypoints are in different downloaded areas, or in none. That leg is drawn as a thin dashed straight line marked as having no trail data, and the route can still be saved. The planner's default; the owner had no preference.
- **A waypoint deleted later:** the saved route keeps its own copy of its points and line, and nothing changes. The planner's default; the owner had no preference.
- **No navigation** of a saved route. Trips keep Directions (-1819).

## What exists (pre-main 21d846b9e)
- **Routing.** `TrailRouter.route(start, end, routeData)` routes two points at a time (domain/routing/TrailRouter.kt:15-28). BRouter runs one route at a time process-wide, with up to 30 s per run (routing/BRouterTrailRouter.kt:199-201), so a chain's legs run one after another.
- **Route data for a leg.** It is found by `FindRouteDataForPoints`, which requires a single downloaded area holding both ends. It returns `InDifferentAreas` or `NoRegionCovers` otherwise (domain/routedata/FindRouteDataForPoints.kt:26-70). Nothing stitches a leg across two areas.
- **Trips.** A trip is `PlannedTrip(id, name, location, date)` (domain/model/PlannedTrip.kt:19-24).
  - The date is required in three places: `SavePlannedTripUseCase.kt:31`, the NOT NULL column (schemas 22.json), and the date picker's confirm (AvailabilityMapOverlaysUi.kt:162).
  - Rows and bubbles always print a date (AvailabilityTripsWaypointsUi.kt:148, MapBubbles.kt:320).
  - Trips sort by date (GetPlannedTripsUseCase.kt:24).
  - Trips are backed up as a RECORD table (data/backup/JournalTables.kt:52-54).
- **Database.** `version = 22` (ForagerDatabase.kt:206), and the latest migration is 21→22 (Migrations.kt:1243). Version 23 was unclaimed on every remote branch on 2026-10-29 and must be re-checked at dispatch (CLAUDE.md, "Verify your base branch").
- **Map taps.** No mode on the Maps tab changes what a tap does today. The pickers read the camera centre, not a tap (CompactMapPickers.kt:128-148; ui/map/fanout/MapTapHandler.kt:91-135). Overlay Back is one handler (AvailabilityCompactMapUi.kt:347-353), and the screen-level order is AvailabilityScreenBackHandlers.kt:62-165.
- **A drawn route line.** Exists only while navigating (CompactMapMapSlot.kt:90; ui/map/RouteHomeLayers.kt). A saved route needs its own layer.
- **A stored line.** Has a precedent in the BLOB codec for kept-track paths (domain/TrackPathCodec.kt:20-40).
- **Files over 800 lines** in the way: Migrations.kt (1266) and AvailabilityCompactScaffold.kt (1440). Near 800: AvailabilityViewModel.kt (797). The modular rule applies: split before adding, and keep new files under 400.

## The order
1. **A trip can carry a route (data).**
   - Migration 22→23 rebuilds `planned_trips` with a nullable date, plus a route table holding the trip's ordered stops (a copy of each waypoint's name and position) and each leg's line and kind (trail or straight).
   - The new migration goes in its own file, not into Migrations.kt.
   - Add the backup TableSpec. Undated trips sort after dated ones. Rows and bubbles show no date when there is none.
   - Existing trips keep their dates. Tested through the migration test harness and the repository.
2. **Plotting logic (domain, headless).** A use case takes an ordered list of waypoints, finds route data per leg, routes each leg in turn, and returns legs plus a total. A leg without data comes back as a straight leg with its reason, and every fallback is logged. Unit-tested on a fake TrailRouter.
3. **Plotting mode on the Maps tab (UI).**
   - "Plot route from here" in the waypoint bubble.
   - A new tap mode in which a waypoint tap adds a leg.
   - A bar with Undo last, Save and Cancel, and the running total.
   - Back cancels in one tap, as its own tier that does not collide with waypoint navigation's Back.
   - Save asks for a name, with an optional date.
   - Map chrome at 80%. New code in new files, nothing added to AvailabilityCompactScaffold.kt.
   - Coordinate-touch tests that taps reach the bar and that a map tap adds a leg.
4. **Showing a saved route.** Tapping a route trip in the Trip Planner, or its bubble, draws its legs on a new map layer (trail solid, straight dashed) with the distance. Delete goes with the trip, the same as today.
5. **Device check on the S22** across a downloaded area, including a leg into a second area.

Each step: Hazel (Opus) builds; Sorrel (Fable) reviews; CI only; a PR into pre-main.

## Questions for the owner before step 3
- **Wording:** "Plot route from here", the bar's labels, the no-trail-data note, and how an undated route reads in the Trip Planner. New words on screen are the owner's.
- **A route trip's bubble:** what it shows (name, distance, number of stops), and whether Directions stays on it, and if so to which stop.
- **Later, not in this plan:** GPX export of a plotted route. The app exports none today; GpxDocument holds one track only (domain/model/GpxDocument.kt:4-5).
