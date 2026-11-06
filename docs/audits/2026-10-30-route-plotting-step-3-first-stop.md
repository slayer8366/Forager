# Route plotting step 3: first stop (look only)

Hazel (Opus), 2026-10-30, for dispatch 2026-10-30-08 (RECORD -1845). Branch `route-plot-ui`, cut from origin/pre-main
**b493cffeb**. That matched the dispatch's stated base and the remote head after `git fetch origin`. No app code is written
here. Every claim names a file and line at b493cffeb (paths under `app/src/main/java/com/zynergylabs/forager/app/` unless
they start with `app/src/test`), or it is marked *inferred* or *unverified*.

Read first: the dispatch; the plan `docs/plans/2026-10-29-waypoint-route-plotting-plan.md` and its three addenda; RECORD -1817
to -1845; dispatch 2026-10-30-02 and its Amendments 1 and 2 (all on origin/records-after-173); and Sorrel's review
`docs/reviews/2026-10-30-sorrel-pr445-pr446.md`. That review is **not on records-after-173** as the dispatch says. It is on
origin/sorrel-pr445-pr446 (9eb44c501).

## Mechanism prediction (how the work makes the change happen)

- **One new ViewModel.** `RoutePlottingViewModel` is Activity-scoped like the others (MainActivity.kt:111-200). It holds the
  stops, the legs and the card's state. It calls `PlotRouteUseCase.leg` once per added stop (domain/route/PlotRouteUseCase.kt:62-65)
  and saves through a new `SavePlottedRouteUseCase`.
- **The Maps tab reads the ViewModel's state through one bundle**, carried in `MapRecordSources` (see (g)). While plotting is
  on, the tab's feature-tap lambda (ui/availability/AvailabilityCompactMapUi.kt:299) sends waypoint taps to the ViewModel instead
  of opening a bubble.
- **New files draw the route:** a non-modal card (`anchoredDraggable`, at 80%), a Back handler, a set of MapLibre line layers
  outside the registry, and a save dialog.
- **Nothing is added to `AvailabilityCompactScaffold.kt` or `AvailabilityViewModel.kt`.**

## (a) How a waypoint tap reaches plotting mode, and Back

**The tap path today (read):**
1. MapLibre's click listener hands every tap to `MapTapHandler.onMapTap` (ui/map/MapListeners.kt:141-149).
2. The handler resolves fans, stacks, bands and markers (ui/map/fanout/MapTapHandler.kt:91-135). A marker becomes
   `sinks.onFeatureTap` (:325).
3. `SightingsMap`'s sink calls `state.onFeatureTap` (MapListeners.kt:121-125), which reaches the host through
   `renderMode.onFeatureTap`.
4. On the Maps tab that is `onFeatureTap = { tap -> tappedThingOf(tap)?.let { tapped = it } }`
   (AvailabilityCompactMapUi.kt:299). That lambda is handed to the map (:490) and to the crowded-stack list (:542;
   CompactMapCrowdedList.kt:76).

**Proposal:** do not touch `MapTapHandler`. It is shared by every map, the journal entry maps included (MapListeners.kt;
CartographyEntryReportScreen.kt:566).
- Branch in the Maps tab's own lambda. While plotting is on, a tap on `MapLayerIds.WAYPOINTS` goes to
  `RoutePlottingViewModel.onWaypointTapped(id)`, and no bubble opens. Every other tap is unchanged.
- This keeps fans and stacks working. A stack still fans: the handler opens it before any sink is called (MapTapHandler.kt:128-133).
  A fanned member's tap comes back through `onFeatureTap` (:99-101), so a waypoint inside a fan is added like any other. A row
  in the crowded list goes the same way (CompactMapCrowdedList.kt:76).
- A plain tap still closes a bubble and leaves fullscreen (CompactMapMapSlot.kt:128-131).
- The tap-to-leg logic (ignore a repeated stop, add, undo) lives in the ViewModel, headless, unit-tested.

**"Plot route" in the bubble:**
- `MapFeatureBubble` gains an `onPlotRoute: ((String) -> Unit)? = null`, shown for a waypoint with `hasDetails`
  (ui/map/MapFeatureBubble.kt:84-100, beside Navigate's guard at :90).
- Only the Maps tab passes it. It reaches the bubble through `MapRecordSources`, as `onNavigateToWaypoint` does
  (ui/map/MapBubbles.kt:198-202). The journal entry maps build their own `MapRecordSources` (ui/log/JournalTab.kt:672), so
  they get no Plot route.
- Width: the actions are a plain `Row` (MapFeatureBubble.kt:223-225) inside a bubble of at most 280 dp (ui/map/MapBubble.kt:177).
  Three label-sized text buttons ("Navigate", "Plot route", "Details") should fit at font scale 1.0. *Inferred, not measured.*
  At large font scales they may clip. A test at font scale 2.0 decides whether the row becomes a `FlowRow`.

**Navigation is not broken:**
- While plotting, a waypoint tap adds a stop, so no waypoint bubble opens, and Navigate is not reachable from a bubble.
- Plotting does not touch `isNavigating` or `navigatingToWaypoint`. The screen-level navigation handlers
  (ui/availability/AvailabilityScreenBackHandlers.kt:126-136) are registered before the tab's nested ones, so the plotting Back
  handler outranks them while it is enabled. That is the intended order: the card is drawn on top.
- Starting to plot while navigating is allowed (*proposal*; see Q4).

**Back as its own tier (read, then proposal).** Compose asks the most recently registered enabled handler first
(AvailabilityScreenBackHandlers.kt:40-46). On the Maps tab the order, top first, is:
1. the drawer (:62);
2. the add-action menu and the pin pickers (AvailabilityCompactMapUi.kt:347-353);
3. the bubble (ui/map/MapBubbleLayer.kt:73), composed at AvailabilityCompactMapUi.kt:518, after the map slot;
4. the fan's handler, which is composed only while open, so it lands last (ui/map/fanout/MarkerFanOutState.kt:132-136);
5. the crowded list (CompactMapCrowdedList.kt:90);
6. then the screen level: fullscreen (:65), the navigation tiers, and the exit handler (:152).

Proposed plotting tier: `RoutePlottingBackHandler(enabled = plotting && !isDrawerOpen && pendingAction == null &&
!pickingSearchLocation && !showActionMenu)`, composed **before** `CompactMapBubble`. That way a bubble, a fan and the crowded
list (all drawn on top) close first, then the next Back cancels the route in one tap. It outranks fullscreen, because the card
is drawn over the fullscreen map (Q3). The overlap is pinned with a real dispatcher, like `AvailabilityScreenBackOverlapTest`.

**A saved route's card** (step 3's "Back closes the card and the line") is the same tier with its own state. It is never
enabled together with plotting (see (d)).

## (b) The card

**No reusable non-modal sheet exists.**
- The app's two sheets are `ModalBottomSheet`s: Record details (ui/log/RecordDetailsSheet.kt:122-136) and the drawer host's
  sheet. A modal sheet has a scrim and takes the map's taps, so plotting could not tap the next waypoint through it.
- Material3's non-modal `BottomSheetScaffold` would have to wrap the map. That means restructuring
  `ScaffoldMapTabCall`/`CompactMapTab`. Rejected, for its size and its touch-routing risk.

**Proposal:** a new `RouteCard` composable in its own file, drawn in the Maps tab's map `Box`.
- Its anchors follow the project's own precedent, foundation's `AnchoredDraggableState` (ui/log/TwoStageSwipe.kt:9-13, :198).
- Two anchors, **Peek** and **Expanded**.
  - **Peek** is one line: the stops as "Creek → Ridge → Falls" (ellipsized in the middle when long) and the total. Below it,
    the Undo, Cancel and Save row while plotting.
  - **Expanded** adds the per-leg rows, each "Creek → Ridge  1.4 mi", plus the straight-leg note. Its height is capped
    (proposal: 50% of the map box), and it scrolls past that.
  - Dragging the handle or tapping it moves between the two. The anchor is user-set state, so it is kept (see (d)).
- **Fill:** `mapChromeFill(..., overMap = true)` with the content colour opaque, as the details sheet does
  (RecordDetailsSheet.kt:123-144). It joins `MapChromeAlphaTest`.
- **Bounds:** the card's own `Surface` has an explicit height from its anchor and a width cap. It never uses `fillMaxSize`
  (CLAUDE.md, the Surface pitfall), so the map above it keeps its long-press. That is tested (h).
- **Landscape:** the window is short (about 360 dp on the S22, *inferred* from the device class) and the rail is on one edge.
  The card keeps M3's sheet width cap of 640 dp (`BottomSheetDefaults.SheetMaxWidth`; the value is *unverified* in material3
  1.5.0-alpha26 and gets checked in code), centred in the map box clear of the rail. In landscape, Expanded is capped lower
  (proposal: 60% of the map box, with the rows scrolling).
- **Tablets** use the same tree as phones (AvailabilityScreen.kt:513-516). The 640 dp cap keeps the card a readable column on a
  wide window, like the Journal's 960 dp column. 640 is M3's sheet value, not the Journal's.
- **What it shares the bottom with** (all *inferred* from reading, not laid out):
  - The icon cluster's drag clamp has to treat the card's top as its bottom limit, as it treats the search notice
    (AvailabilityCompactMapUi.kt:230-234, `searchNoticeBottom`).
  - The attribution "i" and the bottom nav sit below the card.
  - A route fit pads its bottom by the card's peek height (see (c)).
  - In fullscreen there is no nav, so the card's bottom is the real system-bar inset. That is **device-only**: Robolectric
    reports zero insets (CLAUDE.md).

## (c) The map layer for a route line

The precedent is `RouteHomeLayers.kt`: MapLibre line layers outside `MAP_LAYER_REGISTRY` (ui/map/RouteHomeLayers.kt:36-41).
- They are added in `initializeOverlayLayers` after the kept tracks (ui/map/OverlayLayerSetup.kt:91-97), filled by an update
  function (:322-330), driven from `MapDataRefresh`'s keyed effect (ui/map/MapDataRefresh.kt:55-58) through
  `MapOverlayContent.route` (CompactMapMapSlot.kt:76, MapSlot.kt:137).
- That layer already draws a trail solid with its straight joins dashed in a separate layer from the same source
  (`AHEAD_STRAIGHT_LAYER`, RouteHomeLayers.kt:72-82, :259-263).

**Proposal:**
- A new `PlottedRouteLayers.kt` with its own ids (`plotted-route-*`), apart from the navigation layers. It has one source whose
  features carry a `part` property, `trail` or `straight`, and two layers:
  - **trail:** solid, with a casing, so it reads over the topo;
  - **straight:** thin and dashed. It holds both a Trail leg's joins (`line[0..trailFrom]` and `line[trailTo..]`,
    domain/route/PlottedRoute.kt:48-60) and every Straight leg.
- **Stop markers** are the waypoints themselves, already drawn. A route trip's stops are copies (PlottedRoute.kt:36), so a
  saved route whose waypoint was deleted would show no marker there. *Proposal:* small numbered dots on the stops, in a
  symbol layer of the same file. Is that wanted? Q8.
- **Order:** above the kept tracks and below the navigation lines. That way a route being followed home is never hidden under
  a plotted one. MapLibre layers take no touches (RouteHomeLayers.kt:35-36), so the line cannot intercept a tap.
- **Colour:** the navigation line is blue, `0xFF0288D1` by day (RouteHomeLayers.kt:102). The plotted route needs a colour
  distinct from it and from the kept tracks. That is a visual choice for the owner (Q9). No words.
- **Fed by** a new `MapOverlayContent.plottedRoute`, passed through `MapSlot` and `SightingsMap` as `route` is
  (SightingsMap.kt:220-221, :624). That is about 4 lines in SightingsMap.kt (779), which stays under 800.
- **N9:** a stop exactly on a trail gives a zero-length dashed join (PlotRouteUseCase.kt:100 per Sorrel). The feature builder
  emits it as is, a two-point line at one position, and a test asserts it is built without special-casing.
  *Unverified:* that MapLibre draws a zero-length line as nothing rather than a dot. It needs a device or a look on the S22.

**Fitting the route.**
- The Maps tab can take a `MapRenderMode.cameraRequest` (MapRenderMode.kt:212, :304; MapSlot.kt:135). It is applied by
  `applyCameraFrame` (ui/map/MapCameraMoves.kt:168-192). Two findings:
  1. **`shouldApplyCameraRequest` refuses any request while the map follows GPS** (MapCameraMoves.kt:108-112). Opening a
     route while Locate is following would silently not move the map. The open has to leave follow mode first: the request
     counts as a move the user asked for (`CameraMoveCause.APP_USER_ASKED`, MapTapHandler.kt:169-171), and that is
     designed in, not left to chance.
  2. **`EntryMapFrame.Fit` has one padding for all four sides** (domain/EntryMapFrame.kt:12-16). The card covers the bottom, so
     a route fitted with 48 dp all round would end under the card. *Proposal:* a new frame,
     `EntryMapFrame.FitInsets(bounds, top, bottom, start, end)`, as a new branch in `applyCameraFrame` (a new type, not a
     flag on Fit). Its bottom inset is the card's measured peek height.

## (d) Where the plotting state lives, and what survives

**Lives in `RoutePlottingViewModel`, Activity-scoped** (proposal), created like the others (MainActivity.kt:111-200).
- **It holds:**
  - the mode: Off, Plotting, or Showing a saved trip by id;
  - the stops, and the legs as they come back;
  - the one leg in flight, if any;
  - the card's anchor;
  - the save dialog's open flag.
- **Pure functions decide the state** (headless): adding a stop ignores the same stop twice in a row (the domain refuses it,
  PlottedRoute.kt:14-15, :22-24). Undo removes the last stop and its leg. The default name is built here too.
- **Legs route one at a time.** Each tap appends the stop at once and starts `PlotRouteUseCase.leg(from, to)`. A later tap waits
  behind it, so the chain stays in order. The engine runs one route at a time anyway (routing/BRouterTrailRouter.kt:199-201),
  with up to 30 s per route (:195-197).
- **Undo or Cancel cancels the leg in flight.** A cancel terminates the engine and waits for it to stop
  (BRouterTrailRouter.kt:50-57, :166-167), so the next route never overlaps it.
- **While a leg is in flight** the card shows its row with a progress indicator, and the total reads only the finished legs.
  An unfinished leg's figure is never shown.
- **Saving waits for every leg.** Save is disabled while a leg is in flight, and enabled from two stops with all legs done.
  *Proposal.*

**What survives:**
- **A tab change: everything**, because the ViewModel outlives the tab. That is CLAUDE.md's UX default, and I see no reason
  against it. One caution: Q1's "plotting mode with nothing visible" would be worse after a tab change, which is one more
  reason to show the card from the Plot route tap.
- **Rotation:** everything, for the same reason.
- **Process death: lost.** Persisting an unsaved draft is a separate, per-case decision (CLAUDE.md) and is not proposed for
  step 3.

**One mode at a time.** Plotting and showing a saved route are exclusive, so the map draws one plotted line and one card.
What opening a saved route does to an unsaved plot is Q5.

## (e) Save

**`SavePlannedTripUseCase` does not fit as it stands.**
- It takes a non-null `date` and requires it to be no earlier than today (domain/SavePlannedTripUseCase.kt:30-35).
- It builds a trip with no route (:35).

**Proposal:** a new `SavePlottedRouteUseCase(repository, today, idGenerator)`.
- It takes `route: PlottedRoute`, `name: String` and `date: LocalDate?`.
- It refuses a blank name, and a date before today when one is given.
- It saves `PlannedTrip(location = route.stops.first().position, date, route)`. `PlannedTrip`'s init already checks the pin
  (domain/model/PlannedTrip.kt:33-35).
- Its save goes through the existing `PlannedTripRepository.save` (domain/PlannedTripRepository.kt:17), which writes the stops
  and legs (data/repository/RoomPlannedTripRepository.kt, `dao.save`).
- This is a new path, per CLAUDE.md ("new capability is a new function").

**Refreshing the list after a save** without adding to `AvailabilityViewModel.kt` (797 lines):
- Its `plannedTrips` holder (AvailabilityViewModel.kt:207) changes from `private val` to `internal val`, with no line added.
- The plotting ViewModel is handed `onSaved = { viewModel.plannedTrips.loadPlannedTrips() }` in MainActivity.
- `loadPlannedTrips` already exists (ui/availability/AvailabilityPlannedTrips.kt:31-43).
- A failed save is logged and the dialog stays open with the route intact. Its words are Q2.

**The dialog.** `TripDatePickerDialog` cannot be reused: its confirm requires a picked date (AvailabilityMapOverlaysUi.kt:156-166).
- *Proposal:* a new `SaveRouteDialog` shaped like `WaypointNameDialog` (:207-262), a plain `Dialog` with an 80% surface.
- It has a name field pre-filled with the default name, and a row to add an optional date that opens the existing M3 date
  picker floored at today (:131-138). Save is disabled on a blank name.
- **Default name:** "<first> to <last>", or "<first> and back" when the last stop is at the first stop's position. Position, not
  name: two waypoints both named "Creek" in different places give "Creek to Creek". *Proposal*, flagged as Q10.

## (f) N8 to N10 (Sorrel, #445/#446)

- **N8**, stored reason names unpinned (PlottedRoute.kt:70-109; RoomPlannedTripRepository.kt:97, :110): a new test asserts
  `StraightReason.entries.map { it.name }` equals the eleven names written as literals, in order. Step 3 writes the first
  stored route, so this lands before the save path. Revert check: rename one constant, and the test fails naming it.
- **N9**, zero-length join: handled in (c). The drawing expects it, and a test builds it.
- **N10**, `getAll` decodes every route on every Trip Planner load (PlannedTripRouteDao `loadAll`, :20): kept as is in step 3.
  - Today there are 0 routes, and a user with tens of routes decodes tens of BLOBs per load. *Inferred* to be small, not
    measured.
  - On-demand loading would add a second read path and a "route not loaded yet" state to the card.
  - *Proposal:* measure on the S22 with 20 saved routes during step 5's device check, and change only if it shows.

## (g) Files over 800 lines, and the split plan

| File | Lines | Touched? |
|---|---|---|
| ui/availability/AvailabilityCompactScaffold.kt | 1440 | **No.** See below. |
| ui/availability/AvailabilityViewModel.kt | 797 | **No line added.** One `private` becomes `internal` ((e)). |
| ui/map/SightingsMap.kt | 779 | About 4 lines (one parameter, one pass), to about 783. Under 800. |

**Keeping the scaffold untouched.**
- The scaffold unpacks every map input by name and passes each one on (AvailabilityCompactScaffold.kt:347-390, the
  `ScaffoldMapTabCall` call at :1166-1197). A new input there costs at least two lines in the 1440-line file.
- `MapRecordSources` is already built in AvailabilityScreen (AvailabilityScreen.kt:600; AvailabilityScreenMapInputs.kt:108) and
  passes through the scaffold untouched (:390, :1197) to `CompactMapTab` (AvailabilityCompactMapUi.kt:245). It already carries
  a bubble action (`onNavigateToWaypoint`).
- *Proposal:* add one field to it, `routePlotting: RoutePlottingControls? = null`, carrying the ViewModel's state and its
  callbacks. That is zero lines in the scaffold.
- The cost: `MapRecordSources` grows beyond "what bubbles look up". The alternative is a new `CompactScaffoldMapInputs` field,
  which means editing the scaffold, so splitting it first under the modular rule. That is a large move-only PR before any
  feature. **The planner's choice** (Q11).

**Other files touched, all under 800:**
- MapFeatureBubble.kt 257, MapBubbles.kt 509, AvailabilityCompactMapUi.kt 696 (about 20 lines: the branch, the card, the Back
  tier), AvailabilityTripsWaypointsUi.kt 320, AvailabilityScreen.kt 731 (about 10), AvailabilityScreenMapInputs.kt 152,
  MainActivity.kt 740 (about 12), AppContainer.kt 535 (2), OverlayLayerSetup.kt 201, MapCameraMoves.kt, MapDataRefresh.kt 136,
  MapSlot.kt 154, MapOverlayContent.kt 149, domain/EntryMapFrame.kt.
- **AvailabilityCompactMapUi.kt** goes to about 716. If it nears 800 in the build, the card's wiring moves to a
  `CompactMapRouteCard.kt` first.

**New files, each under 400 lines:**
- `RoutePlottingViewModel.kt`, plus `RoutePlottingState.kt` for the pure state logic;
- `SavePlottedRouteUseCase.kt`;
- `RouteCard.kt`;
- `SaveRouteDialog.kt`;
- `PlottedRouteLayers.kt`;
- `RoutePlottingBackHandler.kt`, or inside RouteCard.kt.

## (h) Coordinate-touch tests

All of these go through the real `AvailabilityScreen` with the fake map slot that turns a real touch on a glyph's spot into
`renderMode.onFeatureTap`, as `SightingsMap` does. That precedent is
app/src/test/.../ui/availability/AvailabilityScreenMapBubblesTest.kt:108-160. Real MapLibre taps cannot run under
Robolectric, so "a finger on the real map's waypoint" is device-only.

Every "touch" is a `performTouchInput { click(...) }` at several points across the target's own bounds: the four corners
inset 10% and the centre (CLAUDE.md; the `touchAcross` shape at app/src/test/.../ui/availability/SettingsRowsTouchTest.kt:84-91).

1. **"Plot route" in a waypoint bubble.** Five points across the button: each starts plotting from that waypoint, and the
   bubble closes.
2. **Undo, Cancel and Save on the peeking card.** Five points across each. Undo removes the last stop, Cancel clears the route
   and the card, and Save opens the save dialog. Run in portrait, short landscape (the rail on), and a tablet-size window.
3. **The card's handle.** Touches across it toggle Peek and Expanded, and a real drag between anchors moves it.
4. **The map beside and above the card:**
   - a real **long-press** at several points just above the card's top and beside it (landscape, with the 640 dp cap) reaches
     the map's long-press;
   - a real tap on a waypoint glyph just above the card adds a stop.

   This is the Surface pitfall's regression guard.
5. **A waypoint glyph during plotting.** Several touches across a glyph's touch area each add exactly one leg (the first),
   then are ignored (the same stop twice). No bubble opens. A→B→A adds three stops.
6. **A fanned stack** holding two waypoints. A real touch on a fanned member adds it as a stop.
7. **A Trip Planner route row.** Touches across the row's body, away from the Directions and Delete buttons, open the route:
   the drawer closes, the tab is Maps, a fit request is issued, and the card peeks. Touches on Delete still only delete.
8. **The save dialog's buttons**, touched across, behave as their labels say.

**Back** is tested with the real dispatcher, not by calling handlers:
- bubble over plotting: the bubble closes first, then the route is cancelled;
- fan over plotting: the fan folds first;
- drawer over plotting: the drawer closes first;
- fullscreen + plotting: Back cancels plotting first (Q3);
- a saved route's card: Back closes the card and the line.

**Headless unit tests** of the ViewModel and pure state run on a fake `TrailRouter`. They cover:
- the ignored repeat;
- Undo with a leg in flight cancels it;
- legs in order;
- the default names;
- the Save gate;
- `SavePlottedRouteUseCase`'s refusals.

Each is seen failing first, then given a revert check under CLAUDE.md's runner rules.

## (i) Prior art

Read on the web for this stop. **Gaia GPS and AllTrails** returned HTTP 403 to the fetch tool, so their facts come from search
snippets of their official help pages and one hiking-club guide (*partly unverified*). **OsmAnd**'s docs were read in full and
checked against its source (`measurementtool/MeasurementToolFragment.java`). **Footpath**'s user guide was read directly.

**Conventions:**
- **Undo** removes the last point or segment and is always a visible control (all four).
- **Save** is a visible control, top right in all four.
- **A pre-filled name.** Gaia: "Start to End" from place names, and the same name once when both ends match. OsmAnd: the date.
- **The summary** is at the bottom: distance, climb and an elevation profile. OsmAnd's is an expandable bottom panel with a
  per-segment list.
- **Snapping is on by default.** Where routing data is missing the leg is a straight line, which OsmAnd draws dashed ("for
  areas not covered by routing data").
- **Out-and-back** is an explicit action in Gaia, Footpath and AllTrails. None of them treats tapping the start again specially.

**Where step 3 departs, each already ruled by the owner, recorded so it reads as a choice:**
1. **Only existing waypoints are tappable**, not any point on the map (all four allow any point). Owner: "Chain of waypoints"
   (-1817, -1821).
2. **Back cancels at once, with no confirmation.** OsmAnd asks "Exit without saving?" when there are changes. In OsmAnd, an
   expanded panel also collapses on the first Back. Owner: "Yes, but Back cancels", "in one tap" (-1817, -1845). Q6 asks
   whether an expanded card collapses first.
3. **Save sits on the bottom card**, not top right. Owner: "Undo · Cancel · Save" (-1845).
4. **An optional date on a route.** None of the four has one. Owner: "Date optional" (-1817).
5. **No snap toggle.** The straight leg is the fallback only, consistent with OsmAnd.
6. **Out-and-back comes from tapping the first waypoint last**, named "<first> and back", rather than from an explicit
   button. Consistent with the owner's default name (-1845). No app was found doing this, but no app was found contradicting
   it either.

**Matches:** Undo as a visible control. "<first> to <last>" as Gaia's naming pattern. A bottom panel that expands to
per-leg rows, as OsmAnd's does. A dashed straight leg where there is no data, as OsmAnd's.

## Premises in the dispatch or plan that turned out wrong or stale

1. **Sorrel's review is not on origin/records-after-173.** It is on origin/sorrel-pr445-pr446 (9eb44c501).
2. **The plan's step 3 called the bar's buttons "Undo last" and "Save to Trip Planner"** (plan, "The behaviour"). RECORD -1845
   superseded them with "Undo", "Cancel" and "Save". The dispatch is current. Noted only so nobody builds from the plan's diagram.
3. **The plan says the date picker "confirm" is one of three places requiring a date** (plan, "What exists"). Still true.
   Step 1 left `TripDatePickerDialog` date-required (AvailabilityMapOverlaysUi.kt:156-166) and `SavePlannedTripUseCase` too
   (:30-35). So step 3 needs a new dialog and a new use case, not an edit. Not wrong, but the work is larger than "Save asks
   for a name, with an optional date" suggests.
4. **"Tapping … the pin's bubble moves the map to fit the whole route"** (dispatch). The trip bubble today is a plain card with
   one action, Directions (MapFeatureBubble.kt:111-118), and tapping its body does nothing. So the mechanism is unstated (Q7).
5. **"The Trip Planner row shows name, distance and climb, plus the date if set."** The row today also shows MGRS and decimal
   degrees (AvailabilityTripsWaypointsUi.kt:146-156). Whether a route row keeps them is unstated (Q12).
6. **The Maps tab's camera ignores a fit request while following GPS**, and the only fit frame has uniform padding ((c)). Not a
   dispatch premise, but "moves the map to fit the whole route" needs both handled.

## Questions for the planner and the owner

Owner (behaviour and words):
- **Q1. Between "Plot route" and the first leg**, the dispatch's card "peeks live from the first leg", so after one tap the user
  is in a mode with nothing on screen saying so. A waypoint tap then adds instead of opening a bubble, and the only exit is Back.
  - *Proposal:* the card peeks at once with the first stop's name and a hint line. Cancel is enabled; Undo and Save are disabled.
  - That needs **a new word**, for example **"Tap the next waypoint"**.
- **Q2. A failed save** needs words, for example **"Couldn't save the route."**, after the existing "Couldn't save the planned
  trip." (AvailabilityPlannedTrips.kt:52).
- **Q3. Fullscreen while plotting.** Does the card show in fullscreen? *Proposal:* yes, and Back cancels plotting before it
  leaves fullscreen.
- **Q4. Plotting while navigating or recording.** *Proposal:* allowed, with the navigation line drawn above the plotted one.
- **Q5. Opening a saved route while an unsaved one is being plotted.** Options:
  - (a) the unsaved route is dropped;
  - (b) the row tap is ignored until plotting ends;
  - (c) a prompt (new words).
  - Also: does Back after opening a route from the Tools drawer reopen the drawer, the way Records' "Offline maps" row does
    (RECORD -871; AvailabilityScreenBackHandlers.kt:75-87)? The owner's "Back retraces the way in" suggests it should.
- **Q6. Back with the card expanded:** collapse first, as OsmAnd does, or cancel in one tap as ruled? *Proposal:* one tap, as
  ruled.
- **Q7. A route trip's bubble.** What opens the route from it? Options:
  - (a) tapping the pin opens the route directly, with no bubble;
  - (b) the bubble gains an action (a new word, for example **"Show route"**);
  - (c) tapping the bubble's body.

  And what the bubble shows: name, distance, date if set?
- **Q8. Stop markers** on a saved route whose waypoints may have been deleted: small dots at the stops, yes or no?
- **Q9. The route line's colour**, distinct from the navigation blue and the kept tracks.
- **Q10. "and back"** when the last stop is at the first stop's position, by position (proposal) rather than by name.
- **Q12. A route row** in the Trip Planner: does it keep MGRS and coordinates (the first stop's), or show name, distance and
  date only?

Also new words for the save dialog (proposals, none shipped):
- a title, for example **"Save route"**;
- the name field's label, for example **"Route name"**, or the existing **"Trip name"**;
- the optional date row, for example **"Add date"**, and a way to remove a picked date, for example **"Remove date"**.

Planner (mechanics):
- **Q11.** Carry the plotting controls in `MapRecordSources` (no scaffold edit), or as a new scaffold input after a move-only
  split of AvailabilityCompactScaffold.kt?
- Also asked: accept the `private` → `internal` on `AvailabilityViewModel.plannedTrips`; `EntryMapFrame.FitInsets` as a new
  frame type; leaving follow mode on open; and N10 measured in step 5 rather than changed now.

## Not done or not verified

- **No code**, no build, no CI, no device.
- **Line counts and file and line citations** were read at b493cffeb.
- **Not measured:** bubble action widths, the card's landscape heights, and N10's cost.
- **Not checked:** MapLibre's drawing of a zero-length line, and M3's `SheetMaxWidth` value in 1.5.0-alpha26.
- **Prior art for Gaia GPS and AllTrails** is from search snippets, because their pages returned 403.
