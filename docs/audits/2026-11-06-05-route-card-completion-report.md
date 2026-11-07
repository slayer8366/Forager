# Dispatch 2026-11-06-05: the waypoint route card — pre-build report (stopped before building)

Hazel Cairnwright (Opus), 2026-11-07 UTC. Branch `route-card`, cut from `origin/pre-main` at `1f6901a11` (checked against the remote
at the start: it is the dispatch's stated base, the #505 merge). RECORD -2187. **Nothing is built.** The dispatch's "Before building"
section found three of its named stops (an earlier ruling this changes, a shared file with an open PR, the tab's look), plus one
premise that does not hold as worded (Navigate's target). The questions for the owner are at the end.

## What was traced (read from the code at `1f6901a11`)

- **The card and its caller.** `RouteCard` (`ui/route/RouteCard.kt:144`, 389 lines) is called once, from `BoxScope.CompactMapRouteCard`
  (`ui/availability/CompactMapRoutePlotting.kt:166`, 355 lines), which `AvailabilityCompactMapUi.kt` (766 lines) composes. The card has two
  contents: `Plotting` (buttons Undo, Cancel, Save) and `Saved` (one button, Directions; `RouteCard.kt:336-352`). The owner's screenshot
  card ("Waypoint 15 to Waypoint 1", "3 Waypoints routed", "0.6 mi", "Directions") is the `Saved` one. Placement is
  `RouteCardPlacement.kt` (portrait: flush against the screen edge away from the icon bar, square on that side, C17; landscape: the corner
  away from the L, or resting on the navigation card while navigating, C18/C21).
- **How a route is dismissed today.** Saved route: `RoutePlottingActions.onCloseSavedRoute()` (`RoutePlottingViewModel.kt:194`), which
  clears the mode, so the card and the line both leave. Back calls it (`RoutePlottingBackHandler`, `CompactMapRoutePlotting.kt:332-345`)
  and, for a route opened from the Trip Planner, also reopens the Tools drawer. Plotting: `onCancel()` (`RoutePlottingViewModel.kt:127`),
  wired to the card's Cancel and to Back. ✕ would reuse `onCloseSavedRoute` (and `onCancel` if ✕ shows while plotting; see Q4).
- **How navigation to a waypoint is started today.** One entry, `startWaypointNavigation(origin: WaypointNavigationOrigin)`
  (`AvailabilityScreenWaypointNavigation.kt:37`). It takes an *id* (a waypoint's, or since -2148 a planned trip's, with kind TRIP), stores
  the origin so Back can retrace, and calls the ViewModel's navigate-to-id. Every caller passes an id (`MapBubbleLayer.kt:163,182,208,216`,
  `RecordsTab.kt`, `JournalTab.kt`).
- **The Re-center pill's place.** `CompactMapBottomChrome.kt:89-170`. Shown while navigating and not following (`returnToRouteShown`,
  line 109). Bottom centre above the attribution; it **rises above the route card** by the card's height and a gap where its span overlaps
  the card's (`routeCardLift`, `reCenterRisesForRouteCard`, lines 107-108 and 147). D17: while the card rests on the navigation card
  (landscape navigation) it goes beside the L at the bottom instead (`reCenterBesideTheL`, `RouteCardPlacement.kt:241`); D34: also where its
  word would cover a card (`reCenterWordCoversCard`); D27/D32: icon only where the room beside the L is short.

## Earlier rulings this touches

- **RECORD -1861** (dispatch 2026-10-30-08 Amendment 3), the owner: **"Pill sits above the card (Recommended)"**. "Re-center below the
  route card" reverses this one. The owner's newer words in -2187 are clear, but the dispatch says an override is a stop, so it is here.
- **RECORD -1943** (Amendment 17 part 2): Re-center rises *only where it sits over the card*. Replaced along with -1861 for the open card.
- **RECORD -1958 (D17), -1976 (D27), -2005 (D34)**: Re-center beside the L in landscape navigation. Not changed by this build if #506 lands
  first: #506 (RECORD -2186, "Ring replaces the pill") removes the pill in landscape navigation, so "under the card" is a portrait-only change.
- **RECORD -2021 (D42)** the evening line stepping aside, and D37/D40/D44: read; they decide the card's height in landscape on the navigation
  card and are not changed, but a tucked card no longer needs that room, which is a behaviour choice (Q3).
- **RECORD -2148 (A8)**: navigating to a planned trip "as itself"; Navigate would reuse it (Q5).

## Shared file

**PR #506** (branch `journal-locate-outline`, open) edits `CompactMapBottomChrome.kt` at line 109, the very line that decides whether the
pill shows, inside the function that places it. Moving Re-center under the card is an edit to that function. That is a shared file
(dispatch: stop and say which). `nav-card-kind-line` (#498) and `next-turn` (#500) touch none of the main files this needs; #498 changes two
test classes on route-card placement (`RouteCardOnNavigationCardFontTest`, `RouteCardSundownStepsAsideTest`) which this build would not edit.

## Files this would touch (modular-code skill)

`RouteCard.kt` 389 (adding ✕, Navigate and the outline takes it over 400; the tuck and tab go in a new `RouteCardTuck.kt`, and the outline
shape helper with it), `CompactMapRoutePlotting.kt` 355, `CompactMapBottomChrome.kt` 320 (shared with #506), possibly
`AvailabilityCompactMapUi.kt` 766 (to pass the tucked state to the pill; under 800 after a few lines), `WaypointNavigationOrigin.kt` and
`AvailabilityScreenWaypointNavigation.kt` for a new Back origin (Q5). Order-sensitive: the card's place in the Box (paint and touch order)
and the Back tiers do not move.

## Confirmed vs inferred

- Confirmed from code: everything under "What was traced", with file and line.
- Confirmed from code: a route's stops carry no waypoint id. `RouteStop(val name: String, val position: LatLng)`, `domain/route/PlottedRoute.kt:37`.
- Confirmed from code: a saved route is a `PlannedTrip` whose `location` is its first stop's position (`SavePlottedRouteUseCase.kt:24`).
- Inferred, not checked on a phone: that the owner's screenshot was portrait navigation (the pill shows only while navigating and panned away).
- Inferred: that the route card stays on the map when navigation starts (nothing in `startWaypointNavigation` closes it; not run).

## Could not determine

- Real sizes on the S22 (no phone in this dispatch, no Gradle on the laptop). The tab sizes below are dp values from the code's constants,
  not measured pixels; a CI screenshot can measure them once built.

## Premises that were wrong

- **"Starts the existing navigation to the route's first waypoint."** The existing navigation targets a record by id; a route's first stop
  is a name and a position with no id, and it may be a trip rather than a waypoint (A11, -2148). The nearest existing navigation is "navigate
  to the trip as itself" (-2148), whose spot *is* the first stop, but it is named for the route. See Q5.

## Decided beyond scope

Nothing. No code written.

## Questions for the owner

**Q1. Re-center under the card replaces "Pill sits above the card" (-1861). How should the two share the bottom?** Today, in portrait
while navigating, the card sits at the bottom and Re-center pops up above it when you pan away. For Re-center to be under the card, the card
must sit higher by Re-center's height (about 48 dp plus a gap).

```
 (a) card rises while Re-center shows       (b) card always leaves Re-center's room
   following:        panned away:              following:        panned away:
  |            |    |  [ card    ]|            |  [ card    ]|    |  [ card    ]|
  |  [ card    ]|   | (Re-center) |            |             |    | (Re-center) |
  |____________|    |_____________|            |_____________|    |_____________|
```
(a) the card moves up when you pan and back down when you tap Re-center; nothing is empty while following. (b) the card never moves, but
while navigating there is an empty band under it. Recommended: (a), since the card moving is what makes Re-center noticed, and it only
happens while panned away. When tucked or closed, Re-center is where it is today, as the dispatch says.

**Q2. #506 changes the same code (where Re-center shows).** May this wait for #506 to merge and be cut from pre-main after it? Recommended:
yes. Then Re-center under the card is portrait only, since #506 takes the pill out of landscape navigation.

**Q3. What the tucked card's tab looks like.** Proposal, from the code's sizes: it slides to the screen edge it already sits against in
portrait (the side away from the icon bar), and a tab stays at the card's bottom corner on that edge:
```
  |                         |
  |                       [▶| <- tab: 24 dp wide x 56 dp tall to see, 48 x 56 dp to touch,
  |_________________________|    80% fill, the same outline, rounded on the map side, square on the edge
```
- (A) a plain grip tab, a short vertical bar in it, as the card's handle (Recommended: smallest, covers the least map);
- (B) the tab shows the route's total, "0.6 mi", about 56 x 40 dp;
- (C) the tab shows a small route icon, 40 x 48 dp.
In landscape, does it slide to the same side as the card's corner (away from the L), and while the card rests on the navigation card, does
tucking it give that room back to the navigation card (its evening line comes back, D42)? Recommended: yes to both.

**Q4. ✕ while plotting.** The plotting card already has Cancel. Should ✕ show while plotting too (doing what Cancel does), or only on a
saved route's card? Recommended: only on a saved route's card, since Cancel already does it while plotting. And may the card be tucked
while plotting (taps on waypoints keep adding stops while it is tucked)? Recommended: yes. For a route opened from the Trip Planner, ✕ just
closes it; only Back reopens the Tools drawer. Recommended: yes.

**Q5. What Navigate goes to, and what Back does after it.** A route's stops do not remember which waypoint they came from. Options:
- (a) navigate to the route as a trip (what "Navigate" on a trip does now): the same screen, way along trails and arrival, to the first
  stop's spot, but the navigation card names the route ("Waypoint 15 to Waypoint 1"), not "Waypoint 15". Recommended.
- (b) find the waypoint at the first stop's spot and navigate to it by name: right name, but a moved or deleted waypoint, or a stop that was
  a trip, gives nothing to navigate to, so the button would sometimes do nothing.
Back after Navigate: Back retraces the way in, so one Back ends the navigation and leaves the route card open as it was. Confirm?
(This needs a new "from the route card" origin; today's trip origins reopen a bubble or card the route does not have.)

**Q6. The outline on the edge side.** In portrait the card is flush against the screen edge on one side, square there. Outline the three
sides facing the map only (Recommended, as the centre-pin confirm row does), or all four?

---

# Build, part 1 (Amendment 1): the ✕, the tuck and tab, the outline; two stops for the owner

Hazel Cairnwright (Opus), 2026-11-07 UTC, after the owner's go ("Go ahead and begin building", typed in the planner's window). Branch
`route-card`, base `origin/pre-main` `1f6901a11`. I checked it against the remote at the start and again before this write-up: it had not
moved, so nothing needed merging in. The pre-build part above still holds as a trace. Where its recommendations differ from Amendment 1,
Amendment 1 replaces them.

## Step 4's selection path to `startWaypointNavigation`, traced first

- A route stop is `RouteStop(name, position)` (`domain/route/PlottedRoute.kt:37`). It is copied when the waypoint or trip is tapped:
  `RouteStop(waypoint.name, LatLng(waypoint.lat, waypoint.lng))` at `CompactMapRoutePlotting.kt:86` and `MapBubbleLayer.kt:175`, and
  `RouteStop(trip.name, trip.location)` at `CompactMapRoutePlotting.kt:94` and `MapBubbleTripActions.kt:35`. The id is known at the tap
  and dropped there. The database keeps only the name, lat and lng of each stop (`RoomPlannedTripRepository.kt:57`). The class doc gives
  the reason: "The route keeps its own copy of its stops, so deleting a waypoint later changes nothing".
- Navigation starts in one place, `startWaypointNavigation(origin)` (`AvailabilityScreenWaypointNavigation.kt:37`). It calls
  `onNavigateToWaypoint(id)`, or `onNavigateToTrip(id)` for kind TRIP. Both need an id. Nothing navigates to a bare position.
- On the map, a saved route draws its own stop dots, which can't be tapped (`PlottedRouteLayers.kt:142`). If the waypoint hasn't moved,
  its own pin sits on the same spot. Tapping that pin is a `MapLayerIds.WAYPOINTS` tap whose feature id is the waypoint's id. So a marker
  tap carries an id, but a row in the card's list would not.
- **So a route stop cannot be tied to a saved waypoint by identity.** The only way is to match it at Navigate time, by position and
  name, against the waypoints and trips on the phone. That fails if the waypoint was moved or deleted after the route was saved, and it
  is ambiguous if two waypoints share a spot. This is the dispatch's first stop. Step 4 (selection) and step 5 (Navigate, which step 4
  feeds) are **not built**. Q-A is at the end.

## The selected marker's colour, measured (not chosen)

The plan's accent is cream `#EDE3D0` by day and rust `#A2612C` at night. I measured it in the same arithmetic `MapPaletteTest` uses
(WCAG contrast, Oklab ΔE), against the waypoint pin (`MapPalette.waypoint`), its casing, the route's own stop dot, and the measured
ground clusters (`MapPaletteTest.dayGround` / `nightGround`):

| | vs the normal waypoint pin | vs its casing | nearest ground (board (a), threshold 0.15) | vs the route's stop dot |
|---|---|---|---|---|
| day, cream `#EDE3D0` | 12.30:1, ΔE 0.663 | 1.27:1 against white (board (c) threshold 3:1) | ΔE 0.023, against `#E5E9E1` | 2.46:1, ΔE 0.399 |
| night, rust `#A2612C` | 1.71:1, ΔE 0.282 | 4.28:1 against black | ΔE 0.078, against `#7F752A` | 1.74:1, ΔE 0.353 |

By day, cream is easy to tell from the normal pin, so the stop as literally worded does not fire. But on the light day ground cream
nearly disappears (ΔE 0.023), and it barely shows against its white casing (1.27:1). The selected pin may read as a gap rather than a
highlight. The night rust also falls short on ground distance (0.078). These are colour arithmetic, not a phone check. I picked no
colour. Q-B is at the end.

## What was built (steps 2, 3, 6)

- **Step 3, the ✕** (`ui/route/RouteCardEdges.kt`, new). It shows on a saved route's card only, at the top end corner over the handle's
  row. The target is a 48 dp square, drawn as a 20 dp ✕, with the accessibility label "Close route". It calls
  `RoutePlottingActions.onCloseSavedRoute()`, the same close Back uses (reused, not copied), and never reopens the Tools drawer. The
  saved route's name keeps clear of it. While plotting there is no ✕; Cancel does that job, as today.
- **Step 2, the tuck and tab** (`ui/availability/RouteCardTuck.kt`, new). A sideways swipe of 48 dp or more, either way, tucks the card
  (`RoutePlottingUiState.tucked`, held in the Activity-scoped ViewModel, so it survives a tab change). The card slides off to the edge it
  already sits against: in portrait the edge away from the icon bar, in landscape its corner's side, away from the L. A grip-bar tab is
  left at the card's bottom corner: 24 × 56 dp drawn, a 48 × 56 dp touch target, 80% fill, the card's outline on its three map-facing
  sides. Tapping the tab, or dragging it away from the edge by 48 dp or more, brings the card back. Tucking works while plotting too.
  While tucked, the card reports no height, span or bounds, the same as a card that is leaving. So Re-center goes back to where it sits
  today, and the landscape navigation card's evening line gets its room back (`stepsAside` is false). A route that closes, saves or
  opens starts untucked.
- **Step 6, the outline** (`RouteCardEdges.kt`). It is Re-center's `BorderStroke(1.dp, onSurface)`, in the card's shape, on the three
  sides facing the map. The flush side (portrait: the screen edge; landscape: the bottom) gets none: the outline's box reaches 2 dp past
  that edge and the card's own clip cuts it off. It takes no touches and changes no sizes.
- Files: `RouteCard.kt` 389 → 397 lines, `CompactMapRoutePlotting.kt` 355 → 375, `RoutePlottingViewModel.kt` +11, and two new files
  under 160 lines. None of these is in `git diff --name-only origin/pre-main...` for journal-locate-outline (#506), nav-card-kind-line or
  next-turn (136 files checked).

## Verified, and how

- Tests: `RouteCardTuckCloseTest` (new, 10 cases), on the real compact Maps tab with the real `RoutePlottingViewModel`. Every touch is a
  real one at screen coordinates: five points across the ✕, Directions and the tab; real swipes across the card both ways; a real drag
  on the tab. Five long-presses around the tab and over the tucked card's old area reach the map: just above the tab, beside it toward
  the map, and at the card's old top, middle and far corner. The outline is read from drawn pixels in the ruled hex, by day and at night.
- First run, 42348184916: 8 failures, all fixed. (1) A touch at (0.1, 0.1) of the ✕ missed, because `IconButton` clips its click to a
  circle. Replaced with a square target. (2) My test looked up the tab's drawn part in the merged tree. (3) The six
  `RouteCardOneWidth*Test` corner cases failed: the square-corner pixel they read at y = 1 px now falls on the new top outline. That
  check now reads ceil(1 dp) + 1 px in, still inside the 16 dp round corner's cut, so it still tells square from round. This changes an
  existing test's sample point because of a ruled change. It is disclosed here; the reviewer should confirm it is not a weakening.
- Forward, 42349525171 at `72e7e6b4f`: 206 of 206 pass (filter `*RouteCardTuckCloseTest;*RouteCard*;*RoutePlotting*;*ReCenter*`).
- Revert checks. Each is a one-line edit on a throwaway branch made in a separate scratch worktree, so the forward tree was never
  touched. Each run's log shows 0 compile errors, and each was filtered to `*RouteCardTuckCloseTest;*RouteCardOneWidth*` (28 tests):
  - step 2 (`onSetTucked` sets nothing), run 42349561038: 5 failures, every one a tuck case, e.g. "a swipe toward the edge tucks the
    card", and no `route-card-tab` node in the tab, drag and long-press cases;
  - step 3 (`onClose = {}`), run 42349563007: 1 failure, "a touch at (0.1, 0.1) on the X expected:<Off> but was:<Showing(...)>";
  - step 6 (the outline call removed), run 42349564653: 2 failures, "day, the card's top: a #3B2E24 outline is drawn; the edge reads
    [#F5F1E6, ...]" and the night case with `#1F1F1F`.
  Each failure belongs to its own edit, and nothing from another step failed. After the checks, the forward change is still at HEAD
  (`git diff 72e7e6b4f HEAD` is empty; each of the three lines is present).
- Full CI, run 42351328873 at `c0aab69c5`: every job green, 6856 test cases, 0 failed. Compared by name with pre-main's latest run (42337576510 at `1f6901a11`, 6846): no test missing, and the 10 new ones are all `RouteCardTuckCloseTest`'s. PR #507, draft.

## Not built, and what waits

- **Step 1** (Re-center under the card) waits for #506's coder to hand back. Then `origin/journal-locate-outline` gets merged in (merge,
  never rebase). It needs `CompactMapBottomChrome.kt`, a #506 file.
- **Steps 4 and 5** (selection, list, marker highlight, Navigate) wait on Q-A and Q-B.
- Not covered by tests: the tuck in landscape (the code path is the same, with the side from `routeCardOnLeft`), and the evening line
  coming back when tucked on the landscape navigation card (it follows from `stepsAside` being false; not run).

## Confirmed vs inferred

- Confirmed from code, with file and line: everything in the trace above. Confirmed by CI: the portrait behaviours listed under
  "Verified".
- Inferred: landscape tuck placement and the evening line's return (not run). Re-center returning to today's place when tucked follows
  from the card reporting height 0 and span null, which is what `CompactMapBottomChrome` reads (`AvailabilityCompactMapUi.kt:712-716`).
  No test of that before step 1.

## Could not determine

- Real sizes and colours on the S22 (no phone in this dispatch). The contrast table above is arithmetic on the board's ground clusters.

## Premises that were wrong

- Amendment 1, step 4, "the selected waypoint ... Navigate to the selected waypoint": a stop holds no waypoint id, so "the selected
  waypoint" is a copy that may no longer match any waypoint on the phone (see the trace).
- Amendment 1, step 4, "expand the card ... the route's waypoints are listed": the expanded card already lists the route's *legs*
  ("Creek to Ridge 1.4 mi", `RouteCard.kt` `RouteLegRows`), not its waypoints. Whether waypoint rows replace those or are added beside
  them is not settled (Q-C).

## Decided beyond scope

- The ✕'s place (top end corner, over the handle's row) and its accessibility label "Close route". The plan names the ✕ but not where it
  goes.
- A swipe either way tucks the card, not only a swipe toward the edge (reason in `RouteCardTuck.kt`: a direction that silently does
  nothing is the kind of thing "Don't make me think" rules out). The swipe and tab-drag thresholds are 48 dp.
- The tab's accessibility click label, "Show route".
- The tab's grip bar: 4 × 24 dp, in the card handle's look (40% content colour).

## S22 steps (for when the phone is in)

1. Portrait, open a saved route from its pin. The card shows a ✕ at its top right (bar on the right: top left of the card's right
   corner side). Tap the ✕: the card and the line leave. Back: nothing more closes.
2. Open it again and swipe the card sideways (either way). It slides to the edge away from the icon bar, and a 24 dp grip tab stays at
   the card's bottom corner. Long-press the map just beside the tab and where the card was: a pin is offered as usual.
3. Tap the tab: the card comes back. Tuck again, then drag the tab toward the map: it comes back.
4. Check the outline by day and with Night Mode: a thin dark (day) or pale (night) line on the card's top, bottom and map side, none on
   the screen-edge side.
5. Landscape, navigating, with the route card on the navigation card: tuck it. The tab sits in the card's corner away from the L, and the
   navigation card's evening line (if it was stepped aside) comes back.

## Questions for the owner

**Q-A. Navigate's target: a route's stops don't remember which waypoint they came from.**
- (a) Remember it from now on: each new route stores the waypoint (or trip) each stop came from, and Navigate uses it. Routes already
  saved, and stops whose waypoint was later deleted, fall back to (b). This needs a database change.
- (b) Match at Navigate time by the stop's spot and name. Nothing stored. If the waypoint was moved or deleted, Navigate cannot start;
  the button would be greyed with a short reason.
- (c) Navigate to the stop's spot itself, under the stop's name, whether or not a waypoint is still there. Always works, but it is a new
  kind of navigation target and touches the navigation code another coder is changing now.

**Q-B. The selected marker's colour by day.** Cream is easy to tell from the normal (deep purple) pin, but on the light day map it nearly
vanishes (ΔE 0.023 against the palest ground, 1.27:1 against its white outline). Keep cream and judge it on the phone, or ask for
another choice? (The night rust also sits close to the darker ground, ΔE 0.078.) No colour was picked.

**Q-C. The expanded card's list.** Expanding the card today lists the legs ("Creek to Ridge 0.4 mi"). For selecting a waypoint, should
the open card list the waypoints instead of the legs, or both (waypoints as tappable rows, with each leg's distance under the waypoint
it leads to)?

---

# Build, part 2 (Amendment 2): the soft link and schema 24, then stopped on the version number

Hazel Cairnwright (Opus), 2026-11-07 UTC. Branch `route-card`, from `3e2125c9f`. `origin/pre-main` was still `1f6901a11`, so there was
nothing to merge in. **Stopped:** the planner passed on, with the owner's go, that version 24 is already promised to route plotting
step 4b (heights; `MigrationRouteTrips.kt` on pre-main: "step 4b brings its own migration, 23 to 24 (Amendment 1, H3)"). By the time
that message came I had already written and pushed a `Migration(23, 24)`. It is disclosed below, not reset (we never reset).

## What was built (on the branch, at `e9da49fc5`)

- **The soft link.** `RouteStop.link` holds a waypoint's or a trip's id (`domain/route/PlottedRoute.kt`, `RouteStopLink`).
  `planned_trip_route_stops` gets `linkedWaypointId` and `linkedTripId`, each a plain, indexed, nullable column with no
  `@ForeignKey`. All four taps that add a stop now set the link (`domain/route/RouteStopOf.kt`, new; called from
  `CompactMapRoutePlotting.kt`, `MapBubbleLayer.kt`, `MapBubbleTripActions.kt`). The repository writes the link and reads it back.
  A row with both links set keeps its stop and loses both links, and that is logged.
- **Clearing.** Deleting a trip clears links to it in the same transaction (`PlannedTripRouteDao.delete`). For a waypoint, the
  links that point at waypoints no longer on the phone are cleared at the start of `loadAll`, in the same transaction as the read. So
  a route never reads back a link to nothing, and the stop's name and spot are never touched.
- **Schema 24.** `MIGRATION_23_24` adds the two columns and their indexes. `24.json` is Room's own export from run 42372005536 (a
  scratch branch with an upload step only, now deleted). Every table except the stops table is identical to `23.json`. Version,
  `SCHEMA_VERSION` and `ALL_MIGRATIONS` all moved to 24.
- Tests: `RouteStopLinkMigrationTest` (new). It builds a real v22 database with a trip from `22.json`, migrates it to 23 with
  `MIGRATION_22_23` (validated against `23.json`), and writes two routes (5 stops, 3 legs) at 23. It then opens the database the
  app's way (release path) and reads it back: every trip, stop and leg is equal and every link is null. A second case saves linked
  stops, deletes the waypoint and the trip, and checks that the stops stay with both columns NULL. `SchemaMigrationTest` gains
  23→24, and its chain test moves from 4→23 to 4→24.
- Existing tests changed because of the ruled change, disclosed for the reviewer:
  - Schema 23 → 24: `JournalTablesSchemaTest`, `JournalBackupTest` (live version during restore), and `JournalBackupTestSupport.SCHEMA`.
  - The backup seed keeps the two link columns null (`LINK_COLUMNS`).
  - The stop fixtures now carry the link the app makes: `Waypoint.stop()`, `TripWhilePlottingTest.saddleStop` and `TripBubbleActionsTest:201`.

## Verified, and how

- Red over the stub (migration registered and empty), run 42372005536: 44 tests in 6 classes, 4 failures. They are exactly the four
  migration cases (both `RouteStopLinkMigrationTest` cases, plus `SchemaMigrationTest`'s 23→24 and 4→24), each failing with
  "Migration didn't properly handle: planned_trip_route_stops". The other 40 passed (`TripBubbleActionsTest`, `TripWhilePlottingTest`,
  `RouteCardTuckCloseTest`, `JournalTablesSchemaTest`, and the other `SchemaMigrationTest` cases).
- **Not verified:** the forward run with the real migration. The PR's own run 42372888049 (at `e9da49fc5`) was still running at hand-back.
- **Not done:** the revert checks. I set up three one-line reverts (the migration without one column, `loadAll` without the waypoint
  sweep, a waypoint stop without its link), then cancelled their runs and deleted them at the stop, because renumbering would make
  them stale. None of them is cited as evidence.

## Not built

- Navigate (item 3), selecting (item 4), the card's waypoint list, the accent marker, and Back after Navigate. The plan I traced for
  the next coder:
  - Navigate calls `MapRecordSources.onNavigateToWaypoint` / `onNavigateToTrip` with a new `WaypointNavigationOrigin.RouteCard`.
  - Back needs work: `RoutePlottingBackHandler` is composed after `AvailabilityScreenBackHandlers`, so today Back closes the route
    before it ends navigation. It must stand aside while navigation started from this card is running.
  - The marker can be an accent `drawGlyph` symbol layer, added in `PlottedRouteLayers.kt` above `MapLayerIds.WAYPOINTS`
    (`OverlayLayerSetup.kt` is next-turn's).
  - `RouteCard.kt` is at 397 lines, so the list and Navigate go in new files.
- Plan step 1 still waits for #506.

## Confirmed vs inferred

- Confirmed from refs on 2026-11-07, re-checked after the planner's message:
  - No origin branch has a ForagerDatabase above 23. main and dev are at 22, pre-main at 23.
  - `git log --remotes=origin -S "Migration(23, 24)"` finds only this branch's `0df329a58` and the records commit `75507c5b9`.
  - `-S "version = 24,"` finds nothing. `-S MIGRATION_23_24` finds only `0df329a58`.
  - So step 4b is **not built** on any branch.
- Inferred: clearing a waypoint's links at read time behaves the same as clearing them at delete time. Navigate resolves against the
  waypoints on the phone either way. The one difference: a waypoint restored from a backup before routes are next read would get its
  link back.

## Could not determine

- Whether the forward suite is green. The run was in flight at hand-back.

## Premises that were wrong

- Amendment 2, item 2: "no remote branch has a Migration(23, 24)" was true, but 24 was already promised in a code comment to step 4b.
  I checked refs, not comments.

## Decided beyond scope

- Two link columns, one per kind, not a single column. The planner may rule otherwise.
- Waypoint links are cleared at read, not at delete. The waypoint repository is built in `AppContainer.kt`, which the next-turn
  branch changes, so wiring a delete-time clear there would have been a shared file. I rejected a SQLite trigger because CLAUDE.md
  says links are "joined in code".

## Question for the owner

**Version 24 was promised to the planned heights step (4b), and nothing for it is built anywhere. Should the route card take 24 and
4b move to 25, or should the route card take 25?** If 25: the branch's `MIGRATION_23_24` becomes `MIGRATION_24_25` only after 4b's
24 exists, so this PR would then wait on 4b. If the route card keeps 24, what is on the branch now stands, and the pre-main comment
about 4b gets updated.

## S22 steps (once the number is ruled and Navigate is built)

Install a build at schema 23 (pre-main) with two saved routes, then install this build over it. Both routes open with every stop and
distance as before.

---

# Build, part 3 (Amendment 2, finished): verified the soft link and schema 24; Navigate, Back, selection, the list, the accent marker

Hazel Cairnwright (Opus), 2026-11-07 UTC, on the owner's "Begin building". Branch `route-card`, from `137546e7b`; ends at `c8ac2519d`.
`origin/pre-main` was `1f6901a11` throughout (checked at the start and at the end), so nothing was merged in. No remote branch other
than this one has ForagerDatabase above 23 (all 1,335 remote refs read). Version 24 is this PR's by the owner's "Route card takes 24".

## Verified, and how

- **Run 42372888049** (the hand-back's PR run, `e9da49fc5`, head SHA confirmed): compiled, 6835 tests, **3 failed**, all from schema 24
  and the link: `JournalBackupTest` pinned schema 23 twice (lines 79 and 99), and `AvailabilityScreenWaypointNamesTest` expected a stop
  without its link. I updated the three for the ruled change (`6eaf53ff7`), keeping each check's strength (the link is now asserted too).
- **Restore from 22 (new, `OlderBackupRestoreTo24Test`)**: a real v22 database from `22.json`, one row in every journal table, archived
  as a v22 app does, restored (Replace) through `openForRestore`. Every table's row survives, every v22 column has its value
  (column by column), nothing is logged, the phone is at 24. The same for a v23 backup with a route: the stop survives with both links
  NULL. The values a Replace rewrites by design (a region's fresh id and the two references that follow it, the track point's auto
  key) are compared by what they point at.
- **Full CI, run 42377387878 at `c8ac2519d`: green**, every job. By name against pre-main's latest run (42337576510 at `1f6901a11`):
  6881 vs 6846 test cases, **no outcome changed**. One name is gone: `SchemaMigrationTest` "4 to 23 - the full chain", which the
  previous part renamed to "4 to 24". 36 new, all passing: `OlderBackupRestoreTo24Test` 2, `RouteStopLinkMigrationTest` 2,
  `SchemaMigrationTest` 2, `RouteStopNavigateTargetTest` 7, `RouteCardNavigateTest` 9, `PlottedRouteSelectedStopTest` 4, and part 1's
  `RouteCardTuckCloseTest` 10 (new against pre-main).
- **Eleven one-line reverts.** Each was made on a throwaway branch in a separate scratch worktree, so the forward tree was never
  edited and needed no restore. Each was based on `c8ac2519d`. Every log was read for compile errors first: **0 in each**. Each run's
  failure messages come from that run's own JUnit XML. After all eleven, `git diff c8ac2519d HEAD` in the forward tree is empty and
  every reverted line is present.

  | Revert (one line) | Run | Failed | The failure, from its message |
  |---|---|---|---|
  | link write: `linkedWaypointId = null` in the repository | 42377405695 | 1 of 69 | "the links read back as saved" |
  | read-time clearing: `clearMissingWaypointLinks()` dropped from `loadAll` | 42377410516 | 1 of 69 | "the stops stay, names and spots unchanged, with the links cleared" |
  | migration: the `linkedWaypointId` ADD COLUMN dropped | 42377417230 | 9 of 69 | "no such column: linkedWaypointId ... CREATE INDEX": both `RouteStopLinkMigrationTest` cases, `SchemaMigrationTest` 23→24 and 4→24, both restore tests, and the three older-backup restores in `JournalBackupTest` (they migrate through 24) |
  | Navigate through the link: the link case returns nothing | 42377423523 | 1 of 35 | "a linked stop goes to its waypoint ... even after it has moved": expected `Waypoint(id=wp-creek)` but was null |
  | Navigate by spot and name: the waypoint match dropped | 42377429871 | 4 of 35 | the old route's Navigate, and the three domain cases that match by spot and name |
  | greyed: `enabled = true` | 42377435353 | 1 of 35 | "is not enabled" on the moved-waypoint case |
  | Back: the stand-aside dropped | 42377440773 | 1 of 35 | "one Back ends the navigation expected null, but was WaypointNavigation(wp-creek)" |
  | marker selection: the call dropped | 42377446506 | 1 of 35 | "a touch at (0.1, 0.1) on Ridge's marker expected 1 but was 0" |
  | row selection: `onSelectStop = {}` | 42377452044 | 2 of 35 | the row case (expected 1 but was 0), and the accent marker not moving to the picked stop |
  | the accent marker: `null` | 42377456983 | 1 of 35 | "the first stop by default expected SelectedStopMarker(...) but was null" |
  | first by default: `selectedStop = 1` | 42377463323 | 5 of 35 | every case that expects the first stop got Ridge, and the greyed case (stop 1 is not the moved one) |

  Each failure belongs to its own edit. Nothing from another item failed.
- The new tests did not have a separate all-red run before the code. Their evidence of biting is the revert table above: the feature
  taken out by one line, with the tests in place, each failing for that reason. The first runs of steps 1 and 2 (42374438824 to
  42374444655, 42375180889) **did not compile** (my test code). They are cited for nothing. The compile check is what caught it.

## What was built

- **Navigate's target** (`domain/route/RouteStopNavigateTarget.kt`, pure Kotlin): the stop's link while its waypoint or trip is on the
  phone; else a waypoint, then a trip, at exactly the stop's spot with exactly its name; else none. The card's Navigate goes there
  through the existing `startWaypointNavigation` (the Maps tab's `onNavigateToWaypoint`/`onNavigateToTrip`), with a new origin
  `WaypointNavigationOrigin.RouteCard` (saved as `route-card:<id>` / `trip-route-card:<id>`). Where it leads nowhere, Navigate is greyed,
  and "This stop's waypoint was moved or deleted" stands at the start of the button row.
- **Back** (`RoutePlottingBackHandler`): while navigation started from this card's Navigate is running, the card's Back tier stands
  aside. So Back is the screen's waypoint-navigation step, which ends it, and Back for the `RouteCard` origin reopens nothing. The next
  Back closes the card, as before. This changes today's order (Back closed the card first) only for a navigation the card started. The
  way in was the card, then Navigate, so this is "Back retraces the way in". Recorded rulings checked: -1852 "Yes, cancel first"
  (fullscreen Back while plotting) and the navigation-chrome rule in `AvailabilityScreenBackHandlers.kt` (fullscreen backs out before
  navigation). Outside fullscreen neither conflicts. One edge case does: see Q-D.
- **Selection** (`RoutePlottingUiState.selectedStop`, 0 by default, reset when a route opens or closes): a tap on a stop's waypoint or
  trip marker selects the first stop leading to it, and the bubble opens as before (`CompactMapRouteStops.kt`). The open saved card
  lists the waypoints as rows (`ui/route/RouteCardStops.kt`): the name, then under it the leg leading to it (distance, the "No trail
  data" note on a straight leg). The first row has nothing under it. Each row has a 48 dp minimum height. Tapping a row selects it.
- **The accent marker**: the selected stop's waypoint pin (or trip flag) is drawn again over the waypoints layer in cream `#EDE3D0` by
  day and rust `#A2612C` at night. It sits where the record stands now, which is where Navigate goes, and nothing is drawn when the
  stop leads nowhere. There are two new `MarkerIcon` entries (registered with every other marker, so `OverlayLayerSetup.kt`, next-turn's,
  is untouched), and a source and symbol layer in `PlottedRouteLayers.kt`. Kept for the owner to judge on the phone.
- Files: three new (40, 76 and 114 lines). `RouteCard.kt` 397 → 410, `CompactMapRoutePlotting.kt` 375 → 392,
  `AvailabilityCompactMapUi.kt` 766 → 769, `RoutePlottingViewModel.kt` 282 → 307, plus small edits to `WaypointNavigationOrigin.kt`,
  `AvailabilityScreenWaypointNavigation.kt`, `MarkerGlyphs.kt` and `PlottedRouteLayers.kt`. None of them is in next-turn's,
  nav-card-kind-line's or journal-locate-outline's diff, checked before each edit. `MigrationRouteTrips.kt`'s comment and
  `CompactMapBottomChrome.kt` are not touched.
- Existing tests changed because of the ruled change, disclosed for the reviewer:
  - three for schema 24 and the link (above);
  - four saved-card tests that read "Directions" as the last word or leg rows in the open saved card (`RouteCardLayoutTest` ×2,
    `RouteCardTotalLineTest`, `RouteCardTotalLineContentTest`). Run 42376061270 showed them failing; they now expect "Navigate" and the
    waypoint rows;
  - `MarkerGlyphsTest` gains the two accent icons, with their colours as hex.

## Not built, and what waits

- Plan step 1 (Re-center under the card) still waits for #506. It needs `CompactMapBottomChrome.kt`.
- PR #507 stays a draft.

## Confirmed vs inferred

- Confirmed by CI: everything in the revert table, on the portrait compact Maps tab under Robolectric (a stub map: the accent marker
  is checked as the data handed to the map, plus the pixels of the two new images, not as drawn on a MapLibre map).
- Inferred, not run: the marker drawn above the waypoint's own pin on a real map (layer order read from code); landscape;
  fullscreen (Q-D); a trip as the selected stop on screen (the domain test covers it).

## Could not determine

- How cream reads on the phone by day (the owner's to judge, as ruled).
- Whether the restore's "every record survives" also holds for a real user's v22 backup with real photos. The test's rows are synthetic,
  one per table.

## Premises that were wrong

- The dispatch file names: they are `prompts/preserved/2026-11-06-05-amendment-1.md` and `-amendment-2.md`, not
  `...-route-card-amendment-N.md`. Same content; no effect.
- "PR run 42372888049 ... read it first": it was **red** (3 failures, above), not just unread.

## Decided beyond scope (for the planner to confirm or overrule)

- While plotting, the open card keeps its leg rows. The waypoint rows are on a saved route only, where there is something to select and
  navigate to. Q-C asked "for selecting a waypoint". If the owner meant the plotting card too, it is a small follow-up.
- Where the reason sits: at the start of the button row, in the space that was empty. No new line.
- Where the host navigates nowhere (tests only), Navigate is greyed with no reason, since the reason would not be true.
- The spot-and-name fallback also matches a trip, after waypoints (a stop can be a trip, A11). Two records with the same name at the
  same spot: the first is taken.
- A route through one waypoint twice (A, B, A): a tap on A's marker selects the first visit.
- Rows tell a screen reader which one is selected. The rows do not look different, by the owner's "Only the marker".
- The accent marker shows even when the waypoints layer is switched off in Layers.

## Questions for the owner

**Q-D. Back in fullscreen, after Navigate on the route card.** Outside fullscreen, one Back ends the navigation and the card stays, as
planned. In fullscreen, the screen's long-standing navigation rule applies first: "a back press while navigating in fullscreen exits
fullscreen and does not touch the navigation". So it takes Back (exit fullscreen), then Back (end navigation), then Back (close the
card). Before this change, the card's Back went before fullscreen (-1852, "Yes, cancel first"). As built: fullscreen first. Keep that,
or should the card's navigation end before fullscreen exits?

## S22 steps (for when the phone is in; nothing was run on a phone)

1. **Upgrade with saved routes.** Install a **release** build at schema 22 (main, the Play version) and save two routes... routes need
   23, so: install a release build of pre-main (schema 23), save two routes (one through a trip), and make a backup. Install this
   build's release APK over it. Both routes open with every stop and distance as before. A release build, because a debug build wipes
   on a schema problem instead of failing. Then, on a schema-22 release build with waypoints, trips and finds, upgrade straight to this
   build: every record is there. Restore the 23 backup: the routes come back.
2. Open a route saved before this build. Navigate goes to the first stop's waypoint. Back: navigation ends, the card stays. Back
   again: the card closes.
3. Open a route, open the card with its handle. Each waypoint is a row with the leg's distance under it. Tap the second row. Its pin
   turns cream (day) or rust (Night Mode). Navigate goes there.
4. Tap another stop's pin on the map. Its bubble opens and its pin turns to the accent. Judge the cream by day.
5. Move the first stop's waypoint (edit its position) on an old route, or delete it. Reopen the route. Navigate is greyed, with "This
   stop's waypoint was moved or deleted".
6. In fullscreen, after Navigate: note the Back order (Q-D).
