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
- "an upgrade from a schema-22 release build with saved routes" (the brief's S22 step): schema 22 has no route tables (they arrive in
  `MIGRATION_22_23`), so no schema-22 build holds a saved route. The S22 steps split it: 22 → 24 with every other record, and 23 → 24
  with routes.
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

1. **Upgrade with saved routes.** A schema-22 build cannot save a route (the route tables arrive at 23), so this takes two upgrades.
   Install a **release** build of pre-main (schema 23), save two routes (one through a trip), and make a backup. Install this
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

# Build, part 4 (Amendment 3): Back in fullscreen after Navigate, the accent marker with the Waypoints layer, the outline in landscape navigation

Hazel Cairnwright (Opus), 2026-11-07 UTC, on the owner's "Build needle + route Back" (Amendment 3) and, relayed by the planner, "Build it"
(the outline in landscape navigation). Branch `route-card`, from `27a2723b2`; code at `79dd49447`. `origin/pre-main` was `1f6901a11` at
the start and at the end, so nothing was merged in.

## What was built

- **Back after Navigate in fullscreen** (the owner: "Only leave full screen if they entered full screen after tapping navigate. If they
  entered navigation mode after full screen mode, then leave navigate."; restated, "Yes, that's it"). New `domain/route/RouteCardBackOrder.kt`
  (pure Kotlin, 43 lines): Navigate records whether fullscreen was already on; leaving fullscreen by any route clears that; Back ending the
  card's navigation in fullscreen, where Navigate came after fullscreen, makes the next Back leave fullscreen. The order is held in
  `RoutePlottingUiState.backOrder`, so it survives recomposition and resets when a route opens or closes. `RoutePlottingBackHandler` reads it:
  - card → Navigate → fullscreen: the card's tier stands aside, so Back 1 is the screen's fullscreen step; Back 2 is the screen's
    waypoint-navigation step (as part 3 built); Back 3 closes the card.
  - fullscreen → card → Navigate: Back 1 is the card's tier, which ends the navigation through the screen's own `endWaypointNavigation(true)`
    (passed in as `RoutePlottingControls.onEndNavigation`, one line in `AvailabilityScreen.kt`); Back 2: the card's tier stands aside, so
    the screen leaves fullscreen; Back 3 closes the card.
  - Fullscreen left and entered again after Navigate counts as the later of the two (Back leaves it first).
  - With no Navigate from the card, the card's Back still goes before fullscreen (-1852 "Yes, cancel first"), unchanged.
- **The accent marker hides with the Waypoints layer** ("Hide with the layer"): `selectedStopMarker` draws nothing for a waypoint stop when
  the map's layer state (`renderMode.layers`, what the map draws) has Waypoints off; it comes back when the switch is on again.
- **The outline in landscape navigation** ("Every edge facing the map"): the outline now takes a set of open edges
  (`RouteCardEdges.kt`, `routeCardOpenEdges`). Resting on the navigation card in landscape, the bottom and the card's own side (the side
  bar's, `routeCardOnLeft`) are open; the top and the side toward the map carry the 1 dp `onSurface` outline. Portrait, landscape off
  navigation and the tucked tab are as before.
- Files: one new main file (43 lines), two new test files (`RouteCardFullscreenBackTest`, 223 lines; `RouteCardOutlineOnNavigationCardTest`,
  90 lines). `CompactMapRoutePlotting.kt` 392 → 421, `RoutePlottingViewModel.kt` 307 → 328, small edits to `CompactMapRouteStops.kt`,
  `RouteCardEdges.kt`, `RouteCard.kt`, `RouteCardTuck.kt`, `AvailabilityCompactMapUi.kt` (769 → 772), `AvailabilityScreen.kt` (754 → 755);
  the test rig `RouteCardBarRig.kt` gained an optional theme (`dark`), default unchanged. None of these is in next-turn's,
  nav-card-kind-line's (at `746bb8df2`, then `f1a0a3bbd`) or journal-locate-outline's diff, checked before the edits and again before
  the commit. `CompactMapBottomChrome.kt` is not touched.

## Verified, and how

- **Red first, on the code before this part.** The new tests alone on `27a2723b2` (throwaway branch, run **42383530270**, filtered to the
  two new classes; 0 compile errors in its log): 8 tests, **5 failed**, each for its own reason, from the run's JUnit XML:
  - fullscreen → card → Navigate: "Back 1 ends the navigation expected null, but was:<wp-creek>" (Back left fullscreen instead);
  - the layer: "with Waypoints off, no accent marker is drawn expected null, but was:<SelectedStopMarker(...)>";
  - the outline, all three (day and night at ROTATION_90, day at ROTATION_270): "the side against the side bar carries no outline; it reads
    [#3B2E24 ×3, #F5F1E6 ...]" (night: #EDE3D0 ×3): a 3 px stroke on the side bar's edge. The top and the map side passed: they were
    already outlined.
  - Three passed on the old code, as expected: card → Navigate → fullscreen (part 3 already built that order, Q-D), fullscreen re-entered
    after Navigate (the same path), and Back without Navigate (the unchanged -1852 case, a guard). They are not evidence for the new code;
    only the revert below is evidence for the Back change.
- **Full CI, run 42383594759 at `79dd49447`: green**, every job. By name against pre-main's latest run (**42337576510** at `1f6901a11`):
  6889 vs 6846 test cases (6865 passed, 24 skipped vs 6822 and 24), **no outcome changed**. One name gone (`SchemaMigrationTest` "4 to 23",
  renamed "4 to 24" in part 2), 44 new, all passing: the 36 of parts 1 to 3 and this part's 8.
- **Three one-line reverts**, each on a throwaway branch from `79dd49447` in its own scratch worktree, so the forward tree was never edited
  (after all three, `git diff 79dd49447 HEAD` is empty, and each forward line is present). Each run filtered to `RouteCardFullscreenBackTest`,
  `RouteCardOutlineOnNavigationCardTest`, `RouteCardNavigateTest`, `RouteCardTuckCloseTest` (27 tests). Each log read for compile errors
  first: **0 in each**.

  | Revert (one line) | Run | Failed | The failure, from its message |
  |---|---|---|---|
  | Back: `END_NAVIGATION -> isFullscreen` made `-> false` (the card's tier never ends navigation in fullscreen) | 42385483720 | 1 of 27 | fullscreen → card → Navigate: "Back 1 ends the navigation expected null, but was:<wp-creek>" |
  | accent: `if (!waypointsDrawn) null else` dropped | 42385486178 | 1 of 27 | "with Waypoints off, no accent marker is drawn expected null, but was:<SelectedStopMarker(...)>" |
  | outline: `onNavigationCard != null` made `false` | 42385488222 | 3 of 27 | the three outline cases: "the side against the side bar carries no outline", a #3B2E24 (night #EDE3D0) run at 0..2 |

  Each failure belongs to its own edit. Nothing else failed; part 3's Back test and the portrait outline tests stayed green in each.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, the real compact Maps tab): both Back orders and the re-entry case, portrait; the accent marker as the data
  handed to the map, with the switch reached by real touches through Tools > Map Layers; the outline's pixels in landscape navigation at
  both rotations by day and at ROTATION_90 at night.
- Inferred, not run: the Back orders in landscape (the handlers do not read orientation); the outline at night at ROTATION_270; the
  accent pin hidden on a real MapLibre map (the map is a stub, as in part 3); fullscreen leaving by the tab handler (fullscreen cannot be
  left by a tab change, CLAUDE.md, UX defaults).
- The test reads the side bar's side as the nearer screen edge, matching `routeCardOnLeft` (the rail's side while on the navigation card);
  it printed the measured runs, it did not read the rail's own bounds.

## Could not determine

- Whether the 3 px stroke the old build drew on the side bar's edge was visible on the phone against the rail (the owner's question was
  about where it should be, so this was not measured).
- How the card's top corner on the side bar's side reads with that side open: the shape keeps both top corners round, and the open side's
  stroke is cut by the clip, so (read from the code, not from pixels) the round corner there likely keeps only part of its stroke. Not asked; for the owner to judge on the phone.

## Premises that were wrong

- The amendment file names are `prompts/preserved/2026-11-06-05-amendment-N.md`, not `...-route-card-amendment-N.md` (as in part 3).
- None about the code: Navigate does not turn fullscreen on (confirmed: the card's `onNavigate` only records and navigates).

## Decided beyond scope (for the planner to confirm or overrule)

- **A second recorded Back rule is replaced for this case, not only -1852.** The screen's navigation-chrome rule
  (`AvailabilityScreenBackHandlers.kt`: "a back press while navigating in fullscreen exits fullscreen and does not touch the navigation"),
  and -502's waypoint step, which backs out of fullscreen first, are overridden for a navigation started from the route card in fullscreen:
  Back 1 ends it with fullscreen still on. That is exactly the owner's second order, and Q-D quoted that rule when it asked, so I read it as
  ruled; the brief named only -1852. Every other navigation (the return, a bubble's or Records' Navigate) is unchanged.
- Fullscreen → card → Navigate, then the navigation ended by the HUD's ✕ (not Back): the next Back closes the card first, as before
  (-1852), because only Back sets "leave fullscreen next". Amendment 3 names only Back; this keeps today's behaviour where it is silent.
- A **trip** as the selected stop keeps its accent flag when the Planned Trips layer is off: the owner's words were about the Waypoints
  layer. Q-E below.
- Leaving fullscreen by its control while the card's navigation runs counts as leaving it; entering it again makes fullscreen the later.

## Questions for the owner

**Q-E. A trip stop's accent with its layer off.** A route stop can be a planned trip; its accent is a flag. With **Planned Trips** switched
off in Map Layers, should that flag hide too, as the waypoint pin's now does with Waypoints? As built: it stays.

## S22 steps (nothing was run on a phone)

1. Open a saved route. Navigate. Tap Fullscreen. Back: fullscreen ends, still navigating. Back: navigation ends, the card stays. Back: the
   card closes.
2. Tap Fullscreen first. Open a saved route (tap its pin). Navigate. Back: navigation ends, still fullscreen, the card stays. Back:
   fullscreen ends. Back: the card closes.
3. As 2, but after Navigate tap Exit fullscreen, then Fullscreen again. Back: fullscreen ends first, still navigating.
4. Open a saved route: the first stop's pin is in the accent. Tools > Map Layers > Waypoints off: the accent pin goes with the pins. On
   again: it is back.
5. Turn the phone to landscape, start navigating (a recording's return), open a route. The card sits on the navigation card: its top and
   the side toward the map have the thin outline; the side against the side bar and the bottom do not. By day and in Night Mode, turned
   both ways.

# Build, part 5 (Amendment 3, last bullet): a trip stop's accent flag hides with the Planned Trips layer

Hazel Cairnwright (Opus), 2026-11-07 UTC, on the owner's answer to Q-E, "Hide with its layer", and their go, "Build it" (relayed by the
planner; `prompts/preserved/2026-11-06-05-amendment-3.md`, added 2026-11-07T16:01:33Z, read on `origin/records-after-173` at
`9bd15f23f`). Branch `route-card`, from `5594b7258`; code at `3c7529797`. `origin/pre-main` was `1f6901a11` at the start and at the end,
so nothing was merged in.

## What was built

- With **Planned Trips** off in Map Layers, the selected stop's accent flag (a trip stop) is not drawn; with it on, it is. The same
  pattern as part 4's waypoint pin: `selectedStopMarker` takes `tripsDrawn` and draws nothing for a trip stop when it is false;
  `plottedRouteOnMap` passes it on; the map's call site in `AvailabilityCompactMapUi.kt` hands it `plannedTripsLayerDrawn(renderMode.layers)`,
  read from what the map draws, as `waypointsLayerDrawn` is (both now share one private `layerDrawn`). Nothing else changes.
- Files: `CompactMapRouteStops.kt` 85 → 96, `CompactMapRoutePlotting.kt` 420 → 425, `AvailabilityCompactMapUi.kt` 772 → 775; one new
  test file, `RouteCardTripAccentLayerTest.kt` (130 lines), in its own class rather than growing `RouteCardFullscreenBackTest`. None of these
  is in journal-locate-outline's, next-turn's or nav-card-kind-line's diff from pre-main (checked before the edit and before the commit);
  `CompactMapBottomChrome.kt` is not touched.

## Verified, and how

- **Red first, on the code before this part.** The new test alone on `5594b7258` (throwaway branch, run **42389370516**, filtered to
  `RouteCardTripAccentLayerTest` and `RouteCardFullscreenBackTest`; 0 compile errors in either test job's log): 6 tests, 1 failed, from the
  run's JUnit XML: "with Planned Trips off, no accent flag is drawn expected null, but was:<SelectedStopMarker(..., trip=true)>". The
  preconditions before it passed (the route opened, the flag at the first stop, a trip, the switch turned the layer off), so it failed for
  the reason expected. The unfiltered "Street map deleted" job in the same run: 6836 tests, that one failed, nothing else.
- **Full CI, run 42389454137 at `3c7529797`: green**, every job. By name against pre-main's latest run (**42337576510** at `1f6901a11`),
  from both runs' JUnit XML: 6890 vs 6846 test cases (6866 passed, 24 skipped vs 6822 and 24), **no outcome changed**. One name gone
  (`SchemaMigrationTest` "4 to 23", renamed in part 2), 45 new, all passing: the 44 of parts 1 to 4 and this part's 1.
- **One one-line revert**, on a throwaway branch from `3c7529797` in its own scratch worktree, so the forward tree was never edited:
  `if (!tripsDrawn) null else` dropped from the trip line of `selectedStopMarker`. Run **42389466321**, filtered as above; each test job's
  log read for compile errors first: **0**. 6 tests, 1 failed: "with Planned Trips off, no accent flag is drawn expected null, but
  was:<SelectedStopMarker(..., trip=true)>", the assertion that line governs; the five `RouteCardFullscreenBackTest` cases, the waypoint
  accent's included, passed. Its unfiltered job: 6836, that one failed, nothing else. After it, the forward file matched the copy saved
  before the revert, and `git diff 3c7529797 HEAD` held only this report.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, the real compact Maps tab, the switch reached by real touches through Tools > Map Layers): the flag as the
  data handed to the map, hidden with the layer off and back with it on; the route card stays open with the layer off.
- Inferred, not run: the flag hidden on a real MapLibre map (the map is a stub, as in parts 3 and 4).

## Could not determine

- Nothing new.

## Premises that were wrong

- None found: the branch, worktree and pre-main were as the brief stated.

## Decided beyond scope (for the planner to confirm or overrule)

- None. Part 4's "a trip keeps its flag with Planned Trips off" is now superseded by the owner's answer.

## S22 steps (nothing was run on a phone)

6. Open a saved route whose first stop is a planned trip: its flag is in the accent. Tools > Map Layers > Planned Trips off: the accent
   flag goes with the trips; the route card stays. On again: it is back.

# Build, part 6 (Amendment 1, plan step 1): Re-center under the route card, verified

Hazel Cairnwright (Opus), 2026-11-07 UTC, a fresh coder on the owner's "Fresh coder finishes it" (RECORD -2187's last bullet, read on
`origin/records-after-173` at `d9ed39a72`), verifying the step 1 build an earlier Hazel wrote and pushed at `cb0cc25a0` without reading
any result. Branch `route-card`, from `cb0cc25a0`; ends at `1da75bb12` plus this report. `origin/pre-main` was `82d4a091a` at the start
and at the end (one `[skip ci]` profile line past `1f6901a11`, the commit its latest CI run is on), so nothing was merged in.

## What was built (by the earlier Hazel, `cb0cc25a0`, read here, not changed)

- Step 1, the owner: "(b) Card leaves room" ("The card always sits high enough for Re-center, leaving an empty band under it while
  following"), replacing -1861's "Pill sits above the card" and -1943's rise. New `RouteCardReCenterRoom.kt` (84 lines, pure geometry
  plus one composable): in portrait while navigating, where Re-center at its own place (bottom centre) shares the card's columns, the card
  sits higher by Re-center's height (at least 48 dp) and an 8 dp gap. Re-center no longer rises for the card (`reCenterRisesForRouteCard`
  and its span measurement removed). The card's reported height counts that room, so the legend chip and the route's camera fit clear it.
  Not navigating, and on a tablet in portrait (card in the other column), nothing changes. Landscape is #506's (the ringed locate row or
  the word where it fits).
- Files: `CompactMapRoutePlotting.kt` 425 → 435, `CompactMapBottomChrome.kt` 327 → 322, `RouteCardPlacement.kt` 315 → 307,
  `AvailabilityCompactMapUi.kt` 775 → 776 (one argument, `isNavigating`). None is in next-turn's (#500) diff from pre-main, checked before
  each edit here.
- **This session changed tests only**, never a claim:
  - `8a3d0fc9b`: the shared test base made its compose rule and the navigation view's restore count `protected`. The tests as pushed did
    not compile (4 errors: subclasses reading two `private` members), so both earlier runs (red 42413575796 and the PR run 42413932854 on
    `cb0cc25a0`) were compile failures and proved nothing.
  - `1da75bb12`: one long-press sample. "Beside Re-center, toward the card's end" was the midpoint of Re-center's right and the card's
    right; on the S22 the card (0..198 dp) ends inside Re-center's span (167..217 dp), so that point (207 dp) was on Re-center, which takes
    the press. Now 12 dp past whichever reaches further, beside both.

## Verified, and how

- **Red, on the tests alone** (`3052051fe` + the visibility fix, throwaway branch, run **42414397848**; filtered job and the unfiltered
  "Street map deleted" job, **0 compile errors** in either log, head SHA `dd3746a8e`). Unfiltered: 6865 tests, **6 failed**, nothing else.
  Each is step 1's, from the JUnit XML and log:
  - "Re-center under the open card, where it is with no card: expected ... top=663.0.dp ... was ... top=527.0.dp" (the pill risen above
    the card, not under it); the same in "tucked or closed" once the card comes back;
  - "navigating, the card's bottom is Re-center's height and a gap higher expected:<655.0> but was:<711.0>" (the card not raised);
  - the long-press "in the gap between the card and Re-center" did not reach the map (with the pill above the card that point is the card);
  - `RoutePlottingChromeTest`: "the pill's top 547.0.dp is at or under the card's bottom 711.0.dp";
  - `RouteCardRecenterSharedColumnTest` at `RouteCardRecenterTest.kt:233` (Re-center's top under the card's bottom).
  - Passed on the old code, as expected, so they are not evidence for the build: "the open card never moves" (with the rise, the card
    never moved either; the pill did), the touches across Navigate, Directions and ✕, and the tablet case (no band either way). The
    long-press fix in `1da75bb12` was not re-run red: in the red that test already stops at the gap press, before the line changed.
- **Green: full CI, run 42416307227 at `1da75bb12`, every job.** (Run 42414398322 at `8a3d0fc9b` had one failure, the long-press sample
  on Re-center above, fixed in `1da75bb12`.) By name against pre-main's latest run (**42337576510** at `1f6901a11`), both runs' JUnit XML:
  6923 vs 6846 test cases (6899 passed, 24 skipped vs 6822 and 24), **no outcome changed** on any name in both. 12 names gone, 89 new,
  all passing:
  - **Step 1's two renamed claims:** `RoutePlottingChromeTest` "Return to Route sits above the card, ..." → "Re-center sits under the card,
    ..."; `RouteCardRecenterSharedColumnTest` "... Re-center stays clear of it, ... and goes back when it closes" → "... Re-center sits under
    it where it is with no card, ... and stays when it closes".
  - Step 1's 11 new: `RouteCardReCenterUnderCardTest` (6), `RouteCardReCenterBesideCardTabletTest` (1), `RouteCardReCenterRoomTest` (4).
  - `SchemaMigrationTest` "4 to 23" → "4 to 24" (part 2); 45 new from parts 1 to 5 (part 5's count against the same pre-main run).
  - The rest, 9 renames and the remaining new names (the `LocateRingReCenter*` classes among them), are in test files #506 changed
    (journal-locate-outline, merged in at `ceb15321c`); not checked one by one against #506's own run.
- **One one-line revert**, in its own scratch worktree on a throwaway branch from `8a3d0fc9b`, the forward tree never edited: `+ reCenterRoom`
  dropped from the card's `bottomPadding` in `CompactMapRoutePlotting.kt` (the card leaves no room). Run **42414465151**; **0 compile
  errors** in both test jobs' logs, read before the results. Unfiltered: 6869 tests, **6 failed**, nothing else, each the card sitting
  at today's bottom with Re-center 48 dp into it:
  - "Re-center's top sits one gap (Spacing.sm, 8 dp) under the card's bottom expected:<8.0> but was:<-48.0>";
  - "navigating, the card's bottom is Re-center's height and a gap higher expected:<655.0> but was:<711.0>";
  - "a touch at (0.9, 0.1) on Directions reaches it expected:<2> but was:<1>" (Re-center over the card's corner takes the touch);
  - the gap long-press (no gap: the point is on the card); `RoutePlottingChromeTest` "the pill's top 663.0.dp is at or under the card's
    bottom 711.0.dp"; `RouteCardRecenterSharedColumnTest` at line 233.
  - Not caught by this revert, and not expected to be: "the open card never moves" (with no room the card also never moves) and "tucked
    or closed". So "never moves" guards against a card that follows Re-center, not against the room; the room itself is pinned by the
    655/711 and 8 dp assertions.
  - After it: the forward file matches the copy saved before the edit (`cmp`), the `+ reCenterRoom` line is present, and
    `git diff cb0cc25a0 HEAD` holds only the two test fixes (and now this report).

## Confirmed vs inferred

- Confirmed by CI (Robolectric, the real compact Maps tab, real touches at screen coordinates): at the S22's portrait window, navigating,
  Re-center 8 dp under the open card at exactly its no-card place; the card 56 dp higher navigating than not (48 dp Re-center + 8 dp), the
  same size; the card still as Re-center comes and goes through five real touches across it; tucked, back and closed, Re-center at its
  place; Navigate, Directions and ✕ reached across their bounds with Re-center showing; long-presses in the gap, beside Re-center, above
  the card and in the empty band reach the map; a tablet in portrait leaves no band.
- Inferred, not run: real insets (Robolectric reports none, CLAUDE.md), so the band's height on the phone above the navigation bar; RTL
  (from reading `routeCardReCenterRoomFor`); larger font scales in portrait (the room follows Re-center's measured word, read from the code);
  the tucked tab following the raised bottom (the earlier Hazel's call, not asserted here).

## Could not determine

- How the empty band reads on the phone while following (the owner chose it knowingly: "leaving an empty band under it while following").
- Whether the legend chip, now clearing the raised card, still fits under the search bar at the largest font with a long route card (not
  measured).

## Premises that were wrong

- The brief's "red run 42413575796 ... never read": it had been run, but it was a compile failure, not a red; the PR run on `cb0cc25a0`
  was the same compile failure. Neither could have failed for step 1's reason.
- The brief's "RouteCardRecenterSharedColumnTest" is a class inside `RouteCardRecenterTest.kt`; a filter on the file name did not pick it
  up, so it was read from the unfiltered job in each run.

## Decided beyond scope (for the planner to confirm or overrule)

- The long-press sample's change of place (`1da75bb12`) is a test-geometry fix; its claim ("long-presses around Re-center and the card
  reach the map") is unchanged, and the press that sat on Re-center is not one the claim covers.
- The earlier Hazel's own calls stand as written in RECORD -2187: no room on a tablet in portrait; the tucked tab follows the raised
  bottom; RTL from reading.

## S22 steps for the whole route card plan (nothing was run on a phone)

Portrait unless named. Day, then Night Mode where a colour is named.

1. **Re-center under the card (step 1).** Record a short walk, start the return, open a saved route (tap its pin). The card sits higher
   than it does when not navigating. Pan the map: Re-center appears just under the card, about a finger's gap below it, at the same spot
   it uses with no card; the card does not move. Tap Re-center at its middle and both ends: the view follows again, Re-center goes, the
   card stays put. Long-press the map in the empty band and beside Re-center: a waypoint prompt opens each time.
2. Stop navigating with the card open: the card drops back to its usual place. Tuck it, and close it: Re-center is at its usual place.
3. **Tuck (step 2).** Swipe the card sideways: it slides to the edge away from the icon bar, leaving the grip tab at its bottom corner. Tap
   the tab, then drag it: the card comes back. In landscape it tucks to the corner away from the L. While plotting, tucking works too.
4. **Close (step 3).** ✕ on a saved route's card: the card and the route leave the map. From Trip Planner, ✕ closes without reopening
   Tools.
5. **Selecting (step 4).** Open a route: the first stop's pin is in the accent (cream by day, rust in Night Mode; judge the cream by day).
   Tap another stop's pin: its bubble opens and its pin takes the accent. Drag the card's handle up: each waypoint is a row with the leg's
   distance; tap one: its pin takes the accent. Waypoints off in Map Layers: the accent pin goes; a trip stop's flag goes with Planned
   Trips off.
6. **Navigate (step 5).** Navigate (left of Directions) goes to the selected stop. Back ends navigation, the card stays; Back again closes
   it. In fullscreen: card → Navigate → Fullscreen: Back leaves fullscreen first; Fullscreen → card → Navigate: Back ends navigation
   first. A stop whose waypoint was moved or deleted: Navigate greyed, "This stop's waypoint was moved or deleted".
7. **Outline (step 6).** The card has the thin outline on the three sides facing the map, not the screen-edge side; in landscape on the
   navigation card, on the top and the map side only.
8. **Upgrade (part 2/3).** On release builds only: schema 23 with two saved routes → this build: both open with every stop; schema 22 with
   records → this build: every record there.

# Build, part 7 (Amendment 3, the addition of 2026-11-07T23:49:50Z): the route card's outline stops at the navigation card's top

Hazel Cairnwright (Opus), 2026-11-08 UTC, a fresh coder on the owner's "Fresh coder finishes it" (RECORD -2187's last bullet, read on
`origin/records-after-173` at `5e82472c5`; `e629259a9` since, a -2183 line only). The owner: **"Stop at the top"**. Branch `route-card`,
from `1a6a69f2d`; `origin/pre-main` was `82d4a091a` at the start and the end, so nothing was merged in.

## What was built (by the earlier Hazel, read here and pushed unchanged)

- `69e85a884` (test): `RouteCardOutlineStopsAtNavigationCardTest` (113 lines, new). On the real Maps tab at the S22's landscape window,
  recording, navigating, a route plotted, the route card on the navigation card: in the route card's map-facing columns its line is drawn
  from 4 dp to 1 dp above the navigation card's top, and those same columns carry no pixel of the line's colour from 1 dp to 3 dp below
  it. By day and at night at rotation 90, by day at rotation 270.
- `67c93c119` (fix): `RouteCardOutline(shape, openEdges, coveredBottom)` in `RouteCardEdges.kt` (109 lines): with `coveredBottom` above
  0 dp the outline's box ends that far above the card's bottom and is clipped there, so nothing of it is drawn below. `RouteCard.kt` passes
  `coveredBottom = contentBottomPadding`, which is the 16 dp tuck (`ROUTE_CARD_NAVIGATION_TUCK`) on the navigation card and 0 dp in every
  other placement, so every other outline is unchanged. The bottom edge is already open there (`routeCardOpenEdges`), so the clip draws no
  stroke along the navigation card's top.
- Neither file, nor the test, is in `next-turn`'s (`4e8df0524`) or `nav-card-kind-line`'s (`32251448d`) diff from pre-main (checked before
  pushing).

## Verified, and how

- **Red, on the test alone** (`hazel-rcline-red` at `69e85a884`, run **42427248741**, filtered to `*RouteCardOutlineStopsAtNavigationCardTest*`
  and `*RouteCardOutlineOnNavigationCardTest*`; head SHA confirmed; the unit-test Kotlin compiled, **0 compile errors** in the log, read
  first). 6 tests, **3 failed**, each this test at its "below" check, from the JUnit XML: "day, rotation 1, 1.0 dp below the navigation
  card's top: the route card's line stops at that top, so its columns carry no #3B2E24; they read [#3B2E24, #3B2E24, #3B2E24]"; the same
  at night (#EDE3D0) and at rotation 3. So the line ran on below the top in its exact colour, the failure the test predicts; the "above"
  check and both preconditions passed in each, and `RouteCardOutlineOnNavigationCardTest` (3) passed.
- **Green: full CI, run 42427958402 at `67c93c119`, every job.** The test's own reading below the top is now the card's fill (by day
  #F5F1E6, at night #1F1F1F) from 1 dp to 3 dp, and the line above it in columns 1189 to 1191. By name against pre-main's latest run
  (**42337576510** at `1f6901a11`; `82d4a091a` is one `[skip ci]` line past it), both runs' JUnit XML: 6926 vs 6846 test cases (6902 passed,
  24 skipped vs 6822 and 24), **no outcome changed** on any name in both; 12 gone and 92 new, all passing. Against part 6's green
  (42416307227 at `1da75bb12`): **nothing gone, no outcome changed, 3 new**, exactly this test's three.
- **One one-line revert** in its own scratch worktree (`hazel-rcline2-revert`, from `67c93c119`): `coveredBottom = contentBottomPadding`
  → `coveredBottom = 0.dp` in `RouteCard.kt`. Run **42427978951** at `f5d024b70`; **0 compile errors** in both test jobs' logs, read
  before the results. Filtered: 6 tests, 3 failed; unfiltered ("Street map deleted" job): 6872 tests, **3 failed**, 24 skipped, nothing
  else. All three are this test's "below" check with the line's exact colour, the same messages as the red: the failure this edit produces
  and no other. After it the worktree's file was restored from the copy saved before the edit and `cmp`-matched `route-card`'s; the
  forward line is present on `origin/route-card`.
- **Combined throwaway build, #498 + this branch, without the held reading fix** (`hazel-rcline2-combined` = `67c93c119` merged with
  `origin/nav-card-kind-line` `32251448d`, merge `1f70469cc`, clean; `hazel-clash-498-fix-hold` confirmed not an ancestor, and left alone).
  Run **42427998192**, filtered to `*NavigationCardOutlineEveryEdge*`, both test jobs green, **0 compile errors**: **36 cases, 0 failed**,
  and the 16 "route card on the navigation card" cases (4 in each of `NavigationCardOutlineEveryEdge{Narrow,S26}{Day,Night}Test`) are among
  them by name and passed. So with the line stopped at the top, #498's check reads its own outline at the corner's 6 degree spot again; the
  held reading fix is not needed for these 16.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, the real Maps tab, the drawn pixels): in landscape navigation the route card's map-facing line is drawn
  above the navigation card's top and is absent for the 3 dp below it, by day and at night, both rotations tested; every other outline test
  (portrait, landscape off the navigation card) unchanged; #498's 36 outline cases pass with this branch.
- Inferred, not run: the wedge (below). Real insets (Robolectric reports none, CLAUDE.md). Rotation 270 at night (the day case and the
  night case at 90 cover the two colours and the two sides). A caller passing a route card shape with rounded bottom corners (none does).

## Could not determine

- **How the small wedge reads on the phone.** The navigation card's top corners are rounded at 16 dp (`MAP_CARD_CORNER_RADIUS`), the same
  as the tuck, and the route card's bottom corners are square. So beside the navigation card's map-facing top corner, between its arc and
  the route card's square corner, a sliver of route card shows (at most 16 dp tall at the very edge, tapering to nothing 16 dp in, about
  55 dp²): bordered by the navigation card's own outline along the arc, but now with no route card line on its map-facing side. The test
  reads that sliver's top 3 dp as the bare card fill. The owner will judge it on the S22 ("Judge on the S22 first").

## Premises that were wrong

- None found. `origin/route-card` was `1a6a69f2d` and the local branch two commits ahead with exactly the two refs, as briefed; the red
  run had finished its test job (its "Street map deleted" job was still running) and had not been read.

## Decided beyond scope (for the planner to confirm or overrule)

- The earlier Hazel's WIP commit message ("WIP ... Not yet verified") was pushed as written, not amended (never amend); this part is the
  verification it names.

## S22 step (nothing was run on a phone)

9. **Stop at the top.** Landscape, record a short walk, start the return, open a saved route so its card rests on the navigation card. By
   day: the route card's thin outline runs along its top and down its map side and **ends where the navigation card's top edge begins**;
   no line shows through the navigation card below that. Look closely at the navigation card's top corner on the map side: a small curved
   wedge of the route card shows between that rounded corner and the route card's square corner, with the navigation card's outline
   along the curve and no line on the wedge's map side. Judge whether that wedge reads as finished. Repeat in Night Mode, and with the
   phone turned the other way.

# Amendment 4, built: the deleted-stop reason on its own line under the buttons

Hazel Cairnwright (Opus), 2026-11-08 UTC. The owner: **"Own line under buttons"** (Amendment 4, read on `origin/records-after-173`,
`prompts/preserved/2026-11-06-05-amendment-4.md`), build go **"Build it"**. Branch `route-card`, from `666867aeb` as briefed;
`origin/pre-main` was `82d4a091a` at the start and the end and is already in `route-card`, so nothing was merged in.

## What was built

- `cf77aec71` + `43990e276` (test): `RouteCardNavigateReasonLineTest` (new, 149 lines). On the real compact Maps tab at the S22's portrait
  window (`w384dp-h823dp-xxhdpi`) with native graphics (real text measurement), a saved route whose first stop's waypoint is deleted (only
  the second stop's waypoint is on the phone), opened by a real touch on its marker; Navigate greyed. Checked at font scale 1.0 and 2.0
  (each case asserts the scale it ran at): the words are exactly the owner's and none is cut off; Navigate and Directions are in one row,
  Navigate left of Directions; the reason's top is at or below both buttons' bottoms; the reason runs from the card's side padding to its
  side padding (within 1 dp); every line but the last ends at a space, so no word is broken. `43990e276` only adds a missing import: the
  first red run (42438495157) did not compile and was not read as a result.
- `28b6d3534` (fix): `RouteCardSavedButtons` in `ui/route/RouteCardStops.kt` (now 120 lines) is a column: the row of Navigate and
  Directions at its end, as before, then, only when the stop leads nowhere, the reason at full width under it. The words and the greying are
  unchanged; nothing else in the card moved. With no deleted stop, the card is exactly as before (the row alone).

## Verified, and how

- **Red, test alone** (`hazel-rc4-red` at `43990e276`, run **42439101958**, filtered to `RouteCardNavigateReasonLineTest` and
  `RouteCardNavigateTest`; **0 compile errors** in the build job's log, read first). 11 tests, **2 failed**, both the new test, from the JUnit
  XML: at 1.0, "the reason under the buttons: its top 599.0.dp vs their bottoms 675.0.dp, 675.0.dp", the reason 45 dp wide in 7 lines
  `[This , stop's , waypoi, nt was , moved , or , deleted]`; at 2.0, "nothing cut off", the reason 15 dp wide in 35 lines, one letter
  each. So the S22's finding (a narrow column, words broken mid-word) reproduced on the real screen. `RouteCardNavigateTest` (9) passed.
- **Green: full CI, run 42440439690 at `28b6d3534`** (PR #507), every job success, including the full suite with the Street map deleted.
  The new test's own measurements: at 1.0 the reason runs 12 dp to 235.7 dp of a 247.7 dp card, 4 dp under the buttons, in 2 lines
  `[This stop's waypoint was moved or , deleted]`; at 2.0, 12 dp to 308 dp of a 320 dp card, in 2 lines
  `[This stop's waypoint was , moved or deleted]`.
- **By name against pre-main's latest run** (**42337576510** at `1f6901a11`; `82d4a091a` is one `[skip ci]` commit past it), both runs'
  JUnit XML: 6846 vs 6928 test cases (6822 passed and 24 skipped vs 6904 and 24), **no outcome changed** on any name in both; 12 gone and 94
  new, all passing (the same 12 and 92 as part 7's comparison, plus this test's 2). Against part 7's green (42427958402 at `67c93c119`):
  **nothing gone, no outcome changed, 2 new**, exactly this test's two.
- **One one-line revert** in its own scratch worktree (`hazel-rc4-revert` at `123303216`, from `28b6d3534`): in `RouteCardStops.kt`,
  `Column(Modifier.fillMaxWidth()) {` → `Row(Modifier.fillMaxWidth()) {`, which puts the reason back in the button row. Run
  **42440456516**, filtered as the red; the build job's log has **0 compile errors**, `compileDebugUnitTestKotlin` ran, and the build failed
  at `testDebugUnitTest` on exactly the 2 tests, XML timestamped inside this run. Both fail "nothing cut off": the reason measured 0 dp wide
  beside Directions (235.7 dp..235.7 dp at 1.0, 308 dp..308 dp at 2.0), 41 lines of one character: the row squeezing it out, which is
  what this edit does. `RouteCardNavigateTest` (9) passed. The file was then restored from the copy saved before the edit and
  `cmp`-matched the forward file; the forward lines (`Column(Modifier.fillMaxWidth())`, the reason's `fillMaxWidth()`) are present on
  `origin/route-card`.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, the real Maps tab, real text measurement): with a stop's waypoint deleted, the reason sits on its own line
  under Navigate and Directions, at the card's width inside its padding, words whole, at font scale 1.0 and 2.0 in the S22's portrait
  window; the buttons stay in one row at the row's end; no shared test changed outcome.
- Inferred, not run: the same on the phone (Robolectric's fonts and insets are not the S22's; CLAUDE.md). The "moved" case lays out the
  same way, since it is the same flag (`stopLeadsNowhere`) and the same composable; `RouteCardNavigateTest`'s moved case still passes but
  does not measure the layout. Landscape and the navigation-card placement were not measured.

## Could not determine

- **Whether two lines is what the owner pictured.** The sketch in Amendment 4 shows the sentence on one line. The card is not the screen's
  width: it is sized to its own content (`rememberRouteCardWidth`), 247.7 dp at the default text size in this window, which leaves
  223.7 dp for the reason, and the sentence needs a little more, so it wraps once, at a space: "This stop's waypoint was moved or" /
  "deleted". The ruling says "the card's full width", which is what was built; widening the card for the reason would change its size
  and is not in the ruling, so it was not done. A question for the owner, below.

## Premises that were wrong

- None found. `route-card` was at `666867aeb` and `pre-main` (`82d4a091a`) already in it, as briefed.

## Decided beyond scope (for the planner to confirm or overrule)

- No space added between the buttons and the reason beyond what the buttons' own touch area gives (4 dp in the measurement), and none
  under it beyond the card's bottom padding.

## Questions for the owner

- At the default text size the reason takes two lines in the card ("...moved or" / "deleted"), because the card is narrower than the
  sentence. Is that fine as it is, or should the card widen so the sentence fits on one line when it shows?

## S22 step (nothing was run on a phone)

10. **The reason on its own line.** Plot and save a route of two waypoints, then delete the first waypoint (Records). Open the route on the
    map. Navigate is greyed; Navigate and Directions stay in one row at the card's right; under them, starting at the card's left padding,
    "This stop's waypoint was moved or deleted" reads in whole words, wrapping (if at all) only between words. Repeat with the phone's
    largest text size. Then restore the waypoint (or pick the second stop): the reason is gone and the card looks as before.

# Amendment 5, built: the map chrome's outline all the way round, and no buttons while navigating

Hazel Cairnwright (Opus), 2026-11-08 UTC. Amendment 5 read on `origin/records-after-173`
(`prompts/preserved/2026-11-06-05-amendment-5.md`, RECORD -2187), build go **"Build it"**. Branch `route-card`, from `40e0bdfa7` as
briefed; `origin/pre-main` was `82d4a091a` and already in it. Mid-task, on the planner's word that #506 was green, `origin/journal-locate-outline`
(`13eb45fb1`) was merged in (`420e69742`, a merge, no conflicts).

## What was built

- **The outline** (`ui/route/RouteCardEdges.kt`, `848a512af`). `RouteCardOutline` draws `mapChromeOutline()` (1 dp, Bark at 40% by day,
  white at 40% at night) in the card's shape on every side, in every placement: portrait (the side flush to the screen edge too), landscape
  (the bottom on the screen edge too), and resting on the navigation card (the side against the side bar too). Where the navigation card
  covers the card's bottom, the line is drawn round the card's full bounds and clipped at that card's top, so the sides end there and no
  line runs across along it ("Stop at the top" and "Hide behind nav card"). `routeCardOpenEdges` and the card's `openEdges` parameter are
  gone.
- **The tucked card's tab keeps its old outline** (`RouteCardTabOutline`, same file, pixel-for-pixel the old code path: solid 1 dp
  `onSurface`, open on its screen edge). Amendment 5 rules the card's outline; the tab's plan says it wears "the card's outline", so
  whether it follows is a question for the owner, below.
- **No buttons while navigating** (`848a512af`). `RouteCardContent.Saved` carries `navigating`, filled from the screen's one navigation
  predicate (`AvailabilityScreen.kt`'s `isNavigating`: the return, its arrival hold, or a waypoint navigation) in
  `CompactMapRoutePlotting.kt`; `RouteCardSavedButtons` (`RouteCardStops.kt`) draws nothing while it holds, so Navigate, Directions and
  the reason line are gone, and they come back when it ends. The card's natural heights (D42) still report with the buttons hidden
  (`RouteCard.kt`), so the landscape sundown/elevation decisions read the shorter card.
- **Tests** (`e273c5963`, `64b2af93d`): `RouteCardOutlineReading.kt` (shared reading: the ruled line written out as `0x663B2E24` and
  `0x66FFFFFF`, expected over the card's own pixel 4 dp in, that reference checked level with 5 dp in, tolerance 4/255 per channel);
  `RouteCardOutlineLandscapeTest` (new, landscape not navigating, all four sides, day and night); `RouteCardNavigatingButtonsTest` (new:
  Navigate by a real touch, then Back; then the navigation card's ✕ by a real touch; the reason case, navigation started by the view
  model's own start since the card's Navigate is greyed there, ended by the ✕).

## Tests whose claims changed

- `RouteCardTuckCloseTest`, the two outline cases: were "a 1 dp Bark (#3B2E24) / #EDE3D0 outline on the three sides facing the map", the
  screen-edge side bare; now "the map chrome's 1 dp outline, Bark at 40% / white at 40%, all the way round".
- `RouteCardOutlineOnNavigationCardTest`, all three: were "on the top and the side toward the map, not the side bar's"; now the ruled line
  on the top and both sides.
- `RouteCardOutlineStopsAtNavigationCardTest`, all three: the line read in the ruled colour on both sides above the navigation card's
  top; below it, each side's outer pixels level with the card's pixels a few dp in (Amendment 3's exact-colour test below the top could
  not have seen a 40% line); and, new, no line across the card along that top (read up its middle).
- `RouteCardOneWidthTests.corners` (both subclasses' two cases): the corner is read `ceil(1 dp) + 1 px` in from the side as well as from
  top and bottom, since the screen-edge side now carries the line; the claim (square on the edge side, round facing the map) is unchanged.
  It passed before and after the fix, as a reading move should.
- `RouteCardReCenterUnderCardTest` (`64b2af93d`, `eaa91e8ee`), found by full CI 42444465598: "with Re-center under the card, real touches across
  Navigate, Directions and the X each reach their own control" became "...Navigate and Directions are hidden and real touches across the X
  reach it" (Re-center shows only while navigating, so there are no buttons to reach then); "not navigating the card sits where it does
  today, and navigating it leaves Re-center's room under it" kept its name and its place claims, and its "only its place changes, not its
  size" became "its size only by the hidden buttons' row", Material's 48 dp touch row (it reads 128 dp to 80 dp).

## Verified, and how

- **Red** (`hazel-rc5-red` at `e273c5963`, tests only, run **42442574815**, filtered to the seven outline/buttons/navigate classes): the
  build job's log has **0 compile errors** (`compileDebugUnitTestKotlin` ran), "36 tests completed, 13 failed", matching the JUnit XML. All
  13 for the reasons predicted: the old solid line read `#3B2E24` / `#EDE3D0` where the ruled line over the card was `#ABA398` / `#797979`;
  the screen-edge side, the landscape bottom and the side-bar side read bare; "navigating: Navigate is hidden expected:<0> but was:<1>"
  (twice) and "navigating: the reason is hidden expected:<0> but was:<1>". `RouteCardNavigateTest` (9) and `RouteCardOneWidthTest` (6)
  passed.
- **Fix, filtered** (`hazel-rc5-fix` at `848a512af`, run **42442720549**): BUILD SUCCESSFUL, 0 compile errors, all 36 pass. Measured: the
  ruled line `#ABA298` by day over `#F5F1E6` on every side read, the landscape card's bottom at the screen's (384 dp), below the
  navigation card's top both sides' outer pixels `#F5F1E6` as inside, the card's middle just above that top bare.
- **Revert, part 1** (`hazel-rc5-revA`, run **42443284033**): in `RouteCardEdges.kt` the clip around the outline's drawing removed
  (`clipRect(...) { drawContent() }` to `drawContent()`). 0 compile errors, "18 tests completed, 3 failed": the three
  `RouteCardOutlineStopsAtNavigationCardTest` cases, each "N dp below the navigation card's top, no line: 1 px in reads #ABA298 (#787878 at
  night), not the card's own #F5F1E6 (#1F1F1F)", the line running on behind the navigation card, which is what removing the clip does. The
  other outline classes passed. Restored from the copy saved before the edit; `cmp`-matched `848a512af`'s file; the clip line is present
  on `origin/route-card`. (The colour and the sides were each shown biting by the red run, where the old code failed them.)
- **Revert, part 2** (`hazel-rc5-revB`, run **42443288504**): in `RouteCardStops.kt` the line `if (content.navigating) return` deleted.
  0 compile errors, "12 tests completed, 3 failed": the three `RouteCardNavigatingButtonsTest` cases, with the red run's three messages.
  Restored from the saved copy, `cmp`-matched; the line is present on `origin/route-card`.
- **Full CI**: at `420e69742` (after the merge), run **42444465598** failed only the two `RouteCardReCenterUnderCardTest` cases above
  (both jobs that run the suite, 2 failed each); at `64b2af93d`, run **42446027667** failed one case, the size claim I had changed: I took the hidden row as Navigate's tagged
  bounds (40 dp), and the row is Material's 48 dp touch row (the card read 128 dp to 80 dp, as before). Fixed in `eaa91e8ee`; at
  `eaa91e8ee`, run **42447645450**: **every job success**, including the suite with the Street map deleted.

- **By name against pre-main's latest run** (**42337576510** at `1f6901a11`; `82d4a091a` is one `[skip ci]` commit past it), both runs'
  JUnit XML: 6846 vs 6933 test cases (6822 passed and 24 skipped vs 6909 and 24); **no outcome changed** on any name in both; 18 gone and
  105 new, all passing. Against Amendment 4's green (**42440439690** at `28b6d3534`): 6928 vs 6933, **no outcome changed**, 14 gone and 19
  new: the gone are the 6 renamed here (the outline cases in `RouteCardTuckCloseTest` and `RouteCardOutlineOnNavigationCardTest`, and the
  `RouteCardReCenterUnderCardTest` touch case) and the 8 #506 renamed in `ReCenterOutline*Test` and `LocateRingReCenterLandscapeTest`; the
  new are those renamed cases, `RouteCardOutlineLandscapeTest` (2) and `RouteCardNavigatingButtonsTest` (3).

## Confirmed vs inferred

- Confirmed by CI (Robolectric, native graphics, the real Maps tab): the ruled line on every side of the card in portrait, landscape and
  on the navigation card, by day and at night (landscape-on-card at night in one rotation, by day in both); none below the navigation
  card's top and none across along it; Navigate, Directions and the reason hidden while navigating and back after Back and after the ✕.
- Inferred, not run: the same on the phone. Colours are read over this test map's plain ground; on real tiles the 40% line shows the
  tiles through it, as every other piece of chrome's does. Night colours resting on the navigation card were read in one rotation.

## Could not determine

- **Arrival.** The dispatch says the buttons come back when navigation ends by "Back, ✕ or arrival". In the code, arriving does not end a
  waypoint navigation: the navigation card reads "Arrived" and navigating goes on until Back or ✕ (`AvailabilityScreenWaypointNavigateTest`,
  "within 15 m ... the HUD reads Arrived"; `CompactMapArrivedAt.kt`). Built to the predicate, so at "Arrived" the buttons stay hidden
  until the walker ends it. A question for the owner, below.
- **Where the card meets the side bar** (landscape, on the navigation card): the side bar draws its own line along its inner edge, and the
  card's side now carries one too, so two 1 dp lines may sit side by side there. Not measured; for the S22 check.

## Premises that were wrong

- "Arrival" as an end of navigation, above. Otherwise none: `route-card` was at `40e0bdfa7`.

## Decided beyond scope (for the planner to confirm or overrule)

- The tab left as it was (above).
- The card's D42 natural heights report while the buttons are hidden (a guard waited for a non-zero button row; without the change the
  landscape decisions would have read the card 48 dp taller while navigating). No test covers this case.
- The hidden buttons follow the screen's whole navigation predicate, so a return to the vehicle hides them too, not only a navigation the
  card started.
- The merge commit `420e69742` carries git's default message without the co-author line (not amended, per the repo's rules).

## Questions for the owner

- At "Arrived", should Navigate and Directions come back, or stay hidden until Back or ✕ ends the navigation (as built)?
- The tucked card's tab still wears the old solid line, open on its screen edge. Should it take the same plain 40% line all the way round?

## S22 steps (nothing was run on a phone)

11. **The plain line.** Open a saved route in portrait: a thin, soft line (like the bubbles' and the icon bar's) runs all the way round
    the card, including the side against the screen edge; no darker line. Night Mode: a faint pale line. Turn the phone: the card at the
    bottom edge has the line along its bottom too.
12. **Resting on the navigation card.** Landscape, navigating, a route open: the line runs round the top and down both sides, including
    the side against the side bar, and stops where the navigation card's top meets it; nothing below that and no line across the card
    along that top. Look at the side against the side bar for a doubled line.
13. **No buttons while navigating.** Open a saved route, tap Navigate: Navigate and Directions disappear (the card gets shorter). Press
    Back: they come back. Navigate again, then tap the navigation card's ✕: they come back. Walk to the waypoint: at "Arrived" they stay
    hidden (as built) until Back or ✕. With a deleted stop selected, the reason line hides the same way while navigating elsewhere.

# Amendment 5, the tab, built: the tucked card's tab takes the card's outline

Hazel Cairnwright (Opus), 2026-11-08 UTC. The "Added (2026-11-08T05:05:18Z, -2187)" bullet of Amendment 5, read on `origin/records-after-173`
(`prompts/preserved/2026-11-06-05-amendment-5.md`); the owner, of the tab: **"Same as the card"**; build go **"Build it"**. Branch
`route-card` at `69afc0c26` as briefed; `origin/pre-main` at `82d4a091a`, already in it.

## What was built

- **The tab's outline** (`ui/route/RouteCardEdges.kt`, `96a7be8d5`). `RouteCardTabOutline` draws `mapChromeOutline()` (1 dp, Bark at 40%
  by day, white at 40% at night) in the tab's shape, all the way round, as `RouteCardOutline` does for the card. Gone: step 6's solid
  1 dp `onSurface` line, the box that reached 2 dp past the tab's screen edge so the tab's clip cut that side off, and the `RouteCardEdge`
  enum that chose that side (it had no other user). `RouteCardTuck.kt` calls it without the side and its comment says so. Nothing else
  about the tab changed: its size, place, fill, grip and touch area are the same code.
- **Tests** (`5217280b0`, `7bf77ce75`): `RouteCardTabOutlineTests.kt`, one abstract class and two subclasses on the real Maps tab
  (`RouteCardBarRig`), the card tucked by a real sideways swipe on it, the tab read from drawn pixels with Amendment 5's
  `whyNotRuledLine` (the ruled colours written out, expected over the tab's own pixel 4 dp in). `RouteCardTabOutlineTest` (portrait):
  a saved route's tab with the icon bar where it starts (the tab on the left edge) and moved across (the right edge), and the tab while
  plotting, each by day and at night (6). `RouteCardTabOutlineLandscapeTest`: the tab while plotting, recording, by day and at night (2).
  The sides are read along the tab's middle row, the top and bottom down the column 4 dp in from its edge side, where they are straight
  (the 16 dp round corners take the part nearest the map).

## Tests whose claims changed

- None. No existing test read the tab's line: `RouteCardTuckCloseTest` reads the tab's size, place and touches only, and it passed in
  the red, revert and full runs alike.

## Verified, and how

- **Red** (`hazel-rctab-red` at `7bf77ce75`, tests only, run **42450188876**, filtered to the two new classes and
  `RouteCardTuckCloseTest`): the build job's log has **0 compile errors** (`compileDebugUnitTestKotlin` ran), "18 tests completed, 8
  failed", matching the JUnit XML. All 8 new cases failed for the reason predicted: top, bottom and the side facing the map read the old
  solid line, `#3B2E24` by day and `#EDE3D0` at night, where the ruled line over the tab is `#ABA398` over `#F5F1E6` and `#797979` over
  `#1F1F1F`; the side on the screen edge read bare (`#F5F1E6` / `#1F1F1F` all through). Measured places: portrait 0..24 dp (left) and
  360..384 dp (right), 655..711 dp down; landscape 719..743 dp, 328..384 dp down. (An earlier push of the same tests, run
  **42449599501**, did not compile: "'protected' property exposes its 'internal' type 'RouteCardBarRig'"; its results were not read.)
- **Revert** (`hazel-rctab-rev`, run **42451761388**): in `RouteCardEdges.kt`, the tab's `.border(mapChromeOutline(), shape)` changed
  to a solid 1 dp `onSurface` `BorderStroke` (the card's line untouched). Build log: **0 compile errors**, "20 tests completed, 8
  failed", matching the XML: the 8 tab cases, each on all four sides, the screen-edge side now reading the solid colour too (`#3B2E24` /
  `#EDE3D0`), which this revert produces and the red run's bare edge did not, so these are this build's results.
  `RouteCardOutlineLandscapeTest` (the card) and `RouteCardTuckCloseTest` passed. The revert was made in a separate worktree; the file
  was restored there from the copy saved before the edit, `cmp`-matched `origin/route-card`'s, and the forward change is present on
  `origin/route-card` (`96a7be8d5`).
- **Full CI** at `96a7be8d5` (PR #507), run **42451729839**: **every job success**, including the suite with the Street map deleted.
  Measured: the ruled line `#ABA298` by day over `#F5F1E6` on all four sides of the tab, the screen-edge side included.
- **By name against pre-main's latest run** (**42337576510** at `1f6901a11`; `82d4a091a` is one `[skip ci]` commit past it), both runs'
  JUnit XML: 6846 vs 6941 test cases (6822 passed and 24 skipped vs 6917 and 24); **no outcome changed** on any name in both; 18 gone
  (the same 18 as Amendment 5's comparison) and 113 new, all passing (Amendment 5's 105 and these 8). Against Amendment 5's green
  (**42447645450** at `eaa91e8ee`): 6933 vs 6941, **no outcome changed**, none gone, 8 new: the eight tab cases, all passing.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, native graphics, the real Maps tab): the ruled line on all four sides of the tucked card's tab, by day
  and at night, in portrait against either edge (a saved route) and while plotting, and in landscape while plotting.
- Inferred, not run: the same on the phone. Colours are read over this test map's plain ground. The tab while navigating in landscape
  (the card resting on the navigation card) was not read; it is the same composable with the same call, so it draws the same line.

## Could not determine

- **The tab's side against the side bar in landscape.** The landscape tab sits at 719..743 dp on an 823 dp screen, against the side
  bar rather than the screen's own edge; its edge side now carries the 40% line, and the side bar draws its own line along its inner
  edge, so two lines may sit side by side or overlap there, as the report above notes for the card. Not measured; for the S22 check.

## Premises that were wrong

- None found. `route-card` was at `69afc0c26` as briefed, and the tab's code was as the brief described (solid `onSurface`, open on
  its screen edge).

## Decided beyond scope (for the planner to confirm or overrule)

- `RouteCardEdge` (the enum naming the open side) removed with its only use, rather than left unused.
- The landscape tab's "screen edge" side is its side against the side bar (where it sits); it takes the line there too, read as
  "all the way round".

## S22 step (nothing was run on a phone)

14. **The tab's line.** Open a saved route in portrait and swipe the card sideways: the tab left at the edge has the same thin, soft
    line as the card, all the way round, including along the screen edge; no darker line. Move the icon bar to the other side and do it
    again (the tab on the other edge). Night Mode: a faint pale line. While plotting a route, tuck the card: the same. In landscape,
    tuck the card: the same, and look at the tab's side against the side bar for a doubled line.

# Amendment 6, built: the cards meet (A1), and the tuck slides the card (A2)

Hazel Cairnwright (Opus), 2026-11-08 UTC. Amendment 6 read on `origin/records-after-173`
(`prompts/preserved/2026-11-06-05-amendment-6.md`, RECORD -2187), with its "Added (2026-11-08T09:30:13Z)" section: the owner's
"(2) Card fills corner", "Fade (Recommended)", "Stays aside (Recommended)", "Stays under (Recommended)", build go **"Build it"**. Parts
A1 then A2 (coder A); B (the fan) is coder B's, on `route-card-fan`, and none of its files were touched. Branch `route-card`, from
`c511e430e` as briefed; `origin/pre-main` `82d4a091a`, already in it. No navigation card file changed (`CompactMapLandscapeNavigationCard.kt`,
`NavigationHud.kt`, `RoundedMapCard.kt` are read, not edited).

## The previous coder's findings, checked from source at `c511e430e`

All confirmed: the navigation card's top corners round 16 dp (`MapCardCorners.TOP`, `RoundedMapCard.kt:94`, drawn by `mapCardSurface`);
the route card's landscape shape is square at the bottom (`RouteCardPlacement.kt:84`); `ROUTE_CARD_NAVIGATION_TUCK` 16 dp at `:128`
put the card's bottom 16 dp behind the navigation card, kept empty by `contentBottomPadding` (`CompactMapRoutePlotting.kt:332`); a
tucked card reported no height, span or bounds (`:206-215`), which fed `onNavigationCardNow` and so `routeCardSundownStepsAside`. One
more found: the route card is composed before the navigation card (`AvailabilityCompactMapUi.kt:668`, `:686`), so the navigation card
is drawn over it, which is why the 16 dp overlap showed through that card's 80% fill.

## What was built

- **A1** (`4e3e0ed9a`). `RouteCardPlacement.kt`: the card's bottom is the navigation card's top (the tuck is gone);
  `RouteCardLandscapeRoom.cornerFill` (`ROUTE_CARD_NAVIGATION_CORNER_FILL` = the navigation card's `MAP_CARD_CORNER_RADIUS`) replaces
  `tuck`, and `routeCardNavigationTopLimitPx` loses its tuck term (the card's lines lost the same 16 dp of padding, so D42's decision is
  unchanged). New `ui/route/RouteCardFace.kt`: the card's fill and outline are drawn there, not by a Surface. The fill runs `cornerFill`
  past the card's bottom, less a card as wide whose top corners round by that much (the navigation card's shape), so it fills the two
  corner wedges and nothing else below; the outline is the card's shape over that taller box, stroked at twice its width and clipped to
  the fill (as `Modifier.border` draws a shape), so the sides run straight down to the navigation card's curves and no line is drawn
  along the meeting edge. `RouteCard.kt` loses `contentBottomPadding`; `RouteCardEdges.kt` loses Amendment 5's clipped `RouteCardOutline`.
  Rejected: keeping the bounds 16 dp behind and cutting the drawing to the corners (it draws the same, but the bounds would still claim
  the overlap).
- **A2** (`1ae432e13`, `28d3d0203`, `8b7c2e388`). `RouteCardFace.kt`: `RouteCardTuckDraw` (slide, fade, edge, tucked); the drawn card
  slides toward its edge by its width less 24 dp (`ROUTE_CARD_STRIP`), cut at its bounds' sides (so nothing is drawn under the side bar's
  80% fill), the cut being a side of its outline, so the strip is outlined all round; its content is moved and faded in a layer, and
  leaves the semantics tree while tucked; a 4 by 24 dp grip on the card's map-facing 24 dp fades in. `RouteCardTuck.kt`:
  `rememberRouteCardTuckDraw` (the navigation view chrome's 750 ms, as the card's own motion; under reduced motion the card is at once
  where it ends and only the content fades), and `RouteCardStripTouch` (48 dp from the edge at the card's height: tap, or drag away
  past 48 dp, brings it back). Amendment 1's `RouteCardTab`, its tuck transitions and `RouteCardTabOutline` are gone.
  `CompactMapRoutePlotting.kt`: tucked is no longer hidden; only a card leaving reports nothing, so height, span and bounds keep
  reporting while tucked; the card's sideways swipe is not applied at all while tucked (`af9c2b408`): a disabled `draggable` still
  counted as a touch target and kept long-presses anywhere over the card's bounds from the map (diagnostic run 42493712752, a grid of
  45 real long-presses, none reaching the map). `8b7c2e388` had first replaced the card's Surface with a clipping Box on the wrong
  reading that the Surface's catch-all took them; the grid ruled that out (the Surface's catch-all is inside its clip, the strip), and
  `af9c2b408` put the Surface back.

## Tests

- New: `RouteCardMeetsNavigationCardTests.kt` (A1; 8 cases: the S22's landscape window and a 640 by 360 one, by day and at night, at
  rotations 90 and 270). On the real Maps tab, recording and navigating, read from the drawn screen: down the cards' middle the
  navigation card's top 6 dp read exactly as with no route card (nothing behind it); just above that top the route card reads bare (no
  line of its own along the meeting edge); on both sides the ruled line (Amendment 5's colours, written out) from 4 dp above the top to
  3 dp below it, inside the corner wedges; a pixel 2 dp in and 2 dp down in each wedge reads the route card's own fill.
- New: `RouteCardStripSlideTests.kt` (A2; 4 cases: portrait saved route, portrait plotting, landscape plotting, landscape navigating on
  the navigation card). Tucked by a real swipe, back by a real tap on the strip, the clock stepped by hand, a frame every 32 ms for
  960 ms each way, each read against the same screen with no card: one run of drawn pixels from the edge and nothing else across the
  card's resting width (no other bar); the column 12 dp in drawn from the resting top to the resting bottom (no size change); the fill
  there as at rest (the card itself does not fade); the strongest ink on the card never rising while tucking or falling coming back,
  through a frame part way, gone once tucked; tucked, the run 24 dp wide; back, the card's whole width.
- New: `RouteCardTuckedStaysTest.kt` (2 cases). Landscape, evening, font 2.0, the S22's insets: tucked, the sundown line stays stepped
  aside, the navigation card and the card's bounds do not move, the strip is the card's height at its edge, 24 dp wide; back, still
  aside; closed, the line returns. Portrait, navigating, moved off the view: tucked, Re-center does not move and the strip's bottom is
  the card's, over it; back, Re-center unmoved.

## Tests whose claims changed

- `RouteCardOutlineStopsAtNavigationCardTest` (3 cases), removed: "the route card's line stops at the navigation card's top" and "below
  it, no line" are the reverse of A1. Its above-the-top and no-line-across claims are carried by `RouteCardMeetsNavigationCardTests`.
- `RouteCardLandscapeBarTest`, `RouteCardLandscapeBar270Test`, `RouteCardShortLandscapeBarTest`: "navigating - the card rests on the
  navigation card at its width, behind it, with Save above it" (bottom 16 dp below that card's top) became "... its bottom on that
  card's top, with Save above it".
- `RouteCardPlacementTest`: "a navigation card's top within the tuck of the box's bottom leaves the padding at 0 dp" became "a
  navigation card's top 10 dp over the box's bottom puts the card's bottom there" (10 dp); "a taller navigation card lifts the card to
  its top, less the tuck" (64 dp) became "... lifts the card's bottom to its top" (80 dp).
- `RouteCardTuckCloseTest`: "a real sideways swipe on the card, either way, tucks it to its edge with the tab left there" (card off
  the map, tab 24 by 56 dp) became "... slides it to its edge with a strip of it left there at its full height" (card still there,
  strip 24 dp wide at the card's height and top; closed, both gone).
- Not changed, and passing before and after, so not evidence of A2 on their own: `RouteCardReCenterUnderCardTest`'s "tucked or closed,
  Re-center is where it is today" (Re-center sits at its own place in both designs; `RouteCardTuckedStaysUnderTest` adds the strip's
  claim that the old code fails); `RouteCardTabOutlineTests` (8, the strip's outline all round, read where the tab was read).
- No test asserted "tucking gives the evening line its room back"; that claim was untested before. `RouteCardTuckedStaysAsideTest`
  claims the opposite, per the ruling.

## Verified, and how

Every run's build log was read for compile errors before its results; every count below is from that run's JUnit XML.
- **A1 red**, tests alone at `34480d967` (`hazel-rc6a-red1`, run **42468111141**): 0 compile errors; the 8 new cases failed, but only
  on the sides ("left side, 1.0 dp below the navigation card's top: 1 px in reads #F5F1E6, not the ruled line ... #ABA398"): the reading
  behind the navigation card passed on the old code. The test map's ground (#FAF8F3) is so close to the card's fill (#F5F1E6) that an
  80% navigation card over one or the other differs by 1 to 3 levels a channel, under the outline reading's 4-level tolerance. The
  reading was made exact (`21f8ac623`) and run again on the old code (`hazel-rc6a-red1b`, run **42468898602**): 0 compile errors, all 8
  fail, now also "middle, 0.0 dp below the navigation card's top: reads #AAA196 with the route card open, #ABA298 with the navigation
  card alone (something drawn behind it)" (#797979 vs #787878 at night), 18 rows each, and the 14 bare side rows.
- **A2 red**, tests alone at `c3b8297f5` (`hazel-rc6a-red2`, run **42468756909**): 0 compile errors, 6 of 16 fail. The two
  `RouteCardTuckedStays` cases for the reasons predicted ("tucked: the sundown line stays stepped aside expected:<0> but was:<1>";
  the card's node gone when tucked). The 4 slide cases failed before reading a frame (the card's node gone after the swipe), so the
  frames were made to read against the bounds taken before the touch (`d4b99df3b`) and run again on the old code (`hazel-rc6a-red2b`,
  run **42470557109**): 0 compile errors, all 4 fail on the frames: "the card's fill reads #FAF4FC, not #F5F0F9 as at rest (the card
  itself fades)", "drawn at 66.0 dp from the edge, past the card's run of 0.0 dp", "drawn rows 1757..2132 12 dp in from the edge, not
  the card's 1749..2132" (the 56 dp tab).
- **Green**: full CI at `af9c2b408` (PR #507), run **42495058756**: every job success, 6952 test cases, 6928 passed, 24 skipped, none
  failed, the Street-map-deleted suite included. Before it, full runs 42469749652 (`28d3d0203`) and 42491751151 (`8b7c2e388`) each
  failed one case only, `RouteCardTuckCloseTest`'s "a long-press beside the tab, toward the map reaches the map", which `af9c2b408`
  fixed (see A2 above); run 42468937051 (`21f8ac623`) did not compile its release variant ("'public' function exposes its 'internal'
  parameter type 'RouteCardTuckDraw'", fixed in `28d3d0203`) and was not read as a result.
- **A1 revert** (`hazel-rc6a-revA`, from `8b7c2e388`, whose A1 code is the final one): in `RouteCardPlacement.kt` the card's bottom put
  16 dp back behind the navigation card (`... .toDp() - 16.dp).coerceAtLeast(0.dp)`). Run **42491821718**: 0 compile errors, 11 tests,
  8 failed: all 8 A1 cases, each "middle, 0.0 dp below the navigation card's top: reads #AAA196 with the route card open, #ABA298 ...
  (something drawn behind it)", which only a card behind the navigation card produces; `RouteCardOutlineOnNavigationCardTest` (3)
  passed. Restored from the copy saved before the edit; `cmp` matches the forward file; the forward line is on `origin/route-card`.
- **A2 revert** (`hazel-rc6a-revB2`, from `af9c2b408`): in `RouteCardTuck.kt`, `val slide = 0f` (the card no longer slides; its content
  still fades). Run **42495094557**: 0 compile errors, 16 tests, 5 failed: the 4 slide cases, each "tucked: the strip is 247.7 dp wide,
  not 24" (342.7 dp on the navigation card), and `RouteCardTuckCloseTest`'s long-press beside the strip (with no slide the card's
  controls stay where they were, inside its clip, and take it); both are what this edit does. `RouteCardTuckedStays` (2) passed, as
  they read bounds and reports, not the slide. Restored from the saved copy; `cmp` matches; the forward line is on `origin/route-card`.
  (An earlier run of the same revert from `8b7c2e388`, **42491824110**, failed the same 5 the same way.)
- **By name against pre-main's latest run** (**42337576510** at `1f6901a11`; pre-main is now `81cc37bda`, two `[skip ci]` docs commits
  past it, not merged in): 6846 vs 6952 test cases; **no outcome changed** on any name in both; 23 gone (the 18 of Amendment 5's
  comparison and the 5 renamed above in `RouteCardLandscapeBar*Test` and `RouteCardPlacementTest`), 129 new, all passing.
  **Against 42451729839** (Amendment 5's tab, `96a7be8d5`): 6941 vs 6952, **no outcome changed**, 9 gone (the 3
  `RouteCardOutlineStopsAtNavigationCardTest` cases and the 6 renamed), 20 new, all passing (the 14 new cases and the 6 renamed).

## Confirmed vs inferred

- Confirmed by CI (Robolectric, native graphics, the real Maps tab): A1's four claims at both corners, by day and at night, at both
  rotations, in two window sizes; A2's frames both ways in portrait (saved route, plotting) and landscape (plotting, on the navigation
  card), by day; the tucked card keeps the evening line stepped aside and Re-center under the strip; long-presses around the strip and
  where the card was reach the map.
- Inferred, not run: all of it on a phone. Robolectric has no real insets; the test map's ground is near the cards' fill, so the
  overlap A1 removes reads by 1 to 3 levels here where the owner saw it plainly on real tiles. A2's frames were read by day only, and
  the strip at night only through `RouteCardTabOutlineTests`.

## Could not determine

- **"Both sizes"** in the brief's A1 test list was read as both landscape window sizes (823 by 384 and 640 by 360); if it meant both
  text sizes, font 2.0 is not covered by the A1 test.
- **The tucked card's span and bounds.** The ruling says the tucked card keeps reporting its height; it was built to keep reporting its
  span and bounds too, the resting ones, so nothing on the map moves when it tucks (Re-center beside the L, the "i" hidden under the
  card, the walker's dot kept clear of it). One visible consequence: the "i" stays hidden where the card rests while only the strip
  shows. For the owner to confirm or overrule (question below).

## Premises that were wrong

- None in the dispatch. Two of my own, found by counting: the first A1 red's reading behind the navigation card could not fail on
  this test map (made exact), and `8b7c2e388`'s diagnosis of the long-press (the Surface) was wrong (the disabled draggable; diagnostic
  grid run 42493712752).

## Decided beyond scope (for the planner to confirm or overrule)

- The strip's touch target is 48 dp from the edge at the card's full height (the tab's was 48 by 56 dp), so the 24 dp beside the strip
  brings the card back too.
- The grip slides with the card (it sits on the card's map-facing 24 dp) and fades in as the content fades out.
- Under reduced motion the card is at its end place at once and only the content fades (the card's other motion only fades there).
- While tucked, the card's content leaves the semantics tree, as it did when the card left the screen before A2.
- Span and bounds keep reporting while tucked (above).

## Questions for the owner

- While the route card is tucked, should the map's "i" (the credit button) come back where the card rested, or stay hidden as built
  (the card keeps reporting where it rests, so nothing else moves)?

## S22 steps (nothing was run on a phone)

15. **The cards meet.** Landscape, navigating, a route open on the navigation card: no band of the route card shows through the
    navigation card's top; the route card's side lines run straight down into the navigation card's rounded top corners, both corners
    filled with the card's colour (no map in them), and the only line where the cards meet is the navigation card's own, curving down at
    each end. Night Mode, and the phone turned the other way: the same.
16. **The tuck.** Portrait, a saved route open: swipe the card sideways. The whole card slides to the screen edge in one movement, its
    words and buttons fading as it goes, and stops with a strip about a fingertip wide left at its full height, with the card's colour,
    its outline all round and a short grip; no separate bar appears and nothing changes height. Tap the strip: the card slides back out,
    its words fading in. Drag the strip away from the edge: the same. Long-press the map just beside the strip: it reaches the map.
17. **Tucked, nothing else moves.** Navigating in portrait, moved off the navigation view: tuck the card; Re-center stays where it is,
    under the strip. Landscape in the evening (sundown line showing), open a route on the navigation card so the sundown line steps
    aside, then tuck: the line stays aside and the strip stays where the card was; tap ✕ or Cancel (bring the card back first) and the
    line returns. While plotting, and in landscape, tuck: the same slide.

# Amendment 6, the credit, built: the "i" comes back while the card is tucked

Hazel Cairnwright (Opus), 2026-11-08 UTC. Read on `origin/records-after-173`, `prompts/preserved/2026-11-06-05-amendment-6.md`, section
"Added (2026-11-08T16:58:24Z, -2187)": the owner, "Come back"; restated and confirmed: tucked, the spot where the card rested is clear,
so the "i" shows there again; the card brought back out, the "i" hides under it as today. Base `19c766575` (the fan merge), whose CI run
42501736298 finished green before anything was pushed.

## What hid the "i", from source

`AvailabilityCompactMapUi.kt` hands the map `attributionHideUnder`, the cards' bounds in the map's px, and `MapAttributionInsets`
hides the "i" and the logo when any of them overlaps the "i"'s rectangle (`attributionHiddenUnder`, D28 with D30). The route card's
entry was `routeCardBoundsInRoot`, which A2 keeps reporting while tucked (the resting bounds: the card's layout never moves, only what
it draws, `RouteCardFace.kt`). So a tucked card still hid the "i" across the whole place it rested.

## What changed

- New `ui/availability/RouteCardAttributionCover.kt`: `routeCardDrawnBounds`, the card's bounds moved by the tuck's slide and cut at
  those bounds (the same geometry `RouteCardFace.kt` draws), so the whole card at rest, its 24 dp strip once tucked, and the part still
  over its place on the way; and `ReportRouteCardAttributionCover`, which reports that.
- `CompactMapRoutePlotting.kt`: a new `onCardCoverInRoot` callback, fed from the card's measured bounds and its tuck; `null` when the
  card leaves, as the other reports.
- `AvailabilityCompactMapUi.kt`: the "i" is handed that cover in place of the card's bounds. Nothing else reads it: the evening line,
  Re-center (`cardsInRoot`), the walker's dot (`navigationSideInsetsBesideCard`), the card's height and span all keep the resting
  bounds, so nothing else moves (Amendment 6's "Stays aside" and "Stays under" stand).
- `RouteCardTuck.kt`: one sentence of its header comment, saying the "i" is the one exception.
- Test rig `RouteCardBarRig.applyInsets`: an optional bottom navigation bar inset (default 0, so its other callers are unchanged).

## Verified, and how

- New `AttributionWhileTuckedTest.kt` on the real Maps tab (`RouteCardBarRig`), the card tucked by a real swipe and brought back by a
  real tap on the strip. It asserts the map is handed exactly what is drawn (the card's node out, the strip's node tucked), and that the
  "i" is hidden exactly while something drawn covers it, against the "i"'s rectangle worked out from the margins the map is handed:
  - landscape, not navigating, the L left, 1 and 3 stops, peek and expanded: card out, hidden; tucked, shown (the strip at
    2157-2229 px clears the "i" at 2001-2073); brought back, hidden; the "i"'s corner never moves;
  - portrait, fullscreen with a 48 dp bottom navigation bar delivered (Robolectric reports none; without it the portrait card rests
    above the "i" and never covers it, as AttributionUnderCardPortraitTest records), the bar left, 1 and 3 stops, peek and expanded: the
    same, hidden, shown, hidden;
  - landscape navigating, on the navigation card, 3 stops, peek and expanded: tucked, the route card counts only as its strip, and the
    "i" stays hidden, because it is the navigation card that covers it there (premise below).
- Red, run 42503341832 (throwaway ref `hazel-rcc-red`, the test commit `9de7f63b6` alone, filtered to these classes and
  AttributionUnderCard*): 3 of 8 failed, each on its first tucked check, for this reason: "landscape, the L left, 1 stops, peek,
  tucked: the map is handed the route card's strip as drawn, Rect.fromLTRB(2157.0, 768.0, 2229.0, 1152.0) (handed
  [Rect.fromLTRB(1635.0, 768.0, 2229.0, 1152.0)])", the same in portrait (strip 1080-1152 px, handed 558-1152) and navigating (strip
  2157-2229, handed 1887-2229 with the navigation card). The same run's full-suite job (Street map deleted) had 6903 tests, 3 failed,
  the same three.
- Fix `26811090431` pushed to route-card; run 42504864619 green on every job.
- Revert, run 42504873440 (ref `hazel-rcc-rev`, commit `a1477d611`): the one line handing the "i" `routeCardCoverInRoot` set back to
  `routeCardBoundsInRoot`. Compile log checked first: no `e:` lines, no compile task failed; the JUnit XML timestamps are that run's
  (17:30 UTC). The same three failed with the same messages as the red, each one this revert causes (the resting bounds handed while
  tucked); the full-suite job, 6903 tests, those 3 failed. The file was restored from a copy saved before the edit, never from git, and
  the forward line confirmed present afterwards (`git status` clean against `26811090431`).
- By name, run 42504864619 against route-card's latest green 42501736298 (the fan merge): 6906 pass / 24 skipped before, 6909 pass /
  24 skipped after; no test only before, the 3 new tests only after, no test changed status.

## Confirmed vs inferred

- Confirmed (source and the tests' prints): what hid the "i"; that the tucked strip clears the "i" in every case tested; that in
  landscape navigation the navigation card, not the route card, covers the "i".
- Inferred: that MapLibre then shows the button and the logo again; the map is a stub under Robolectric, as in AttributionUnderCardTest.
  The "i"'s margins are the S22 run record's, as there.

## Could not determine

- Portrait outside fullscreen: under Robolectric the card never covers the "i" there, so the portrait case is tested only in fullscreen
  with a delivered 48 dp navigation bar. Whether the S22's real bar puts the card over the "i" is device-only.
- The moment of the switch during the slide: the cover follows the drawn card frame by frame, so the "i" should appear as the sliding card
  uncovers it and hide as it slides back over it; no frame-by-frame test was written for that.

## Premises that were wrong

- The dispatch's test list asked for the "i" present while tucked in landscape "resting on the navigation card too". There the "i" is
  under the navigation card, not the route card (the route card ends at the navigation card's top, 924 px, above the "i" at 1068-1140
  px; the same in AttributionUnderCardTest's prints before this change), so by D28 it stays hidden while the route card is tucked. The
  ruling ("the spot where the card rested is clear") does not reach it; built as D28 has it, and asked below.

## Decided beyond scope (for the planner to confirm or overrule)

- The tucked card still counts as its 24 dp strip, not as nothing, so the "i" would stay hidden wherever the strip itself covered it
  (D28). In every case tested the strip clears the "i", so this shows no difference today.
- The cover follows the slide, not the tucked flag, so the "i" does not appear under a card still sliding over it.

## Questions for the owner

- Landscape, navigating, a route open on the navigation card: the "i" is under the navigation card, so it stays hidden while the route
  card is tucked. Should it stay that way (as today, D28), or is something else wanted there?

## S22 steps (nothing was run on a phone)

18. **The "i" comes back while tucked.** Landscape, not navigating, the icon bar dragged to the left, a route open (its card in the
    bottom-right corner): the "i" and the MapLibre logo are not showing. Swipe the card to its strip: as the card slides away the "i" and
    the logo appear in the bottom-right corner, in their usual place. Tap the strip: as the card slides back over them they disappear.
    Portrait in fullscreen with the icon bar on the left, a route open: the same.
19. **Landscape navigating.** A route open on the navigation card: tuck it; the "i" stays hidden (the navigation card covers its
    corner); nothing else moves, as step 17.

# Sorrel's review items, fixed

Review: `prompts/preserved/2026-11-08-sorrel-review-507.md` (records-after-173). Dispatched: item 1.1 and note 3.2 (RECORD -2190);
added by the planner mid-task: item 2.2 with the owner's answer (RECORD -2187): "Option 1, but remove the tap highlight since it
spills out the zone". Built on route-card 50ae42c64 in two parts by two Hazel sessions: the first built 2.2 and 3.2 and stopped on
where -967 reaches for 1.1 (ref `hazel-rail-fix`, a698fc483); the owner answered (RECORD -2187, 2026-11-08T22:50:26Z): **"(c) All
three (Recommended)"**, and the second built 1.1 on top. The whole series is fast-forwarded onto route-card.

- **Note 3.2, built.** "Navigated from the card" is cleared when the waypoint navigation is not running
  (`RoutePlottingViewModel.onWaypointNavigationOver`, told by `RoutePlottingBackHandler` in `CompactMapRoutePlotting.kt`). Nothing
  visible changes. Tests: `RouteCardNavigatedFromCardEndsTest` (2, the real screen: the card's Navigate by real touch, ended by Back
  and by the navigation card's ✕, then a navigation started the way a bubble starts it is not the card's) and one
  `RoutePlottingViewModelTest` case. Red 42525146220 ("the card's navigation ended by Back: none is the card's now", the same for the
  ✕); revert 42529011439 (the screen's one line removed) failed the same 2 and no other new test; green 42527216431.
- **Item 2.2, built.** The strip's touch area keeps 48 dp and draws no press highlight (`RouteCardTuck.kt`, `indication = null`).
  Test: `RouteCardStripPressTest`, a real touch held in the touch area beside the 24 dp strip, the band between the strip and the
  touch area's edge compared pixel for pixel with the screen before the press, then lifting must bring the card back. Red 42527186082
  (13,370 of 13,370 band pixels changed, #FEF7FF to #B1ACB2); revert 42529006236 failed it and no other new test; green 42527216431
  (0 changed).
- **Item 1.1, built to the owner's (c).** Wherever the landscape route card or its tucked strip meets the side bar (resting on the
  navigation card while navigating, the plotting card in that card's spot, and the strip's edge, navigating and plotting) the card
  draws no line of its own on that edge; the side bar's is the one line (RECORD -967). The decision is the navigation card's own
  rule, not a copy: that card's call to `landscapeColumnOutlineSides` moved into `landscapeNavigationCardOutlineSides`
  (new `RouteCardRailSide.kt`), which the navigation card now calls and the route card reads through `routeCardBareSide`. So the side
  is bare while the side bar shows; in fullscreen it is lined where a system bar is left on that side and bare where nothing is, as
  the navigation card's is. A card in the far corner (icon bar on the side bar's side, C20) touches no side bar and keeps every line.
  Drawing: `RouteCardFace.kt` clips a band one line wide off the stroke along that side of the card's bounds (`routeCardBareBand`);
  the band starts below the top line and stops above a bottom line, so the top and the plotting card's bottom run to the edge, and
  runs on down into the corner wedge where the card rests on the navigation card. The tucked strip's cut edge is that same bounds side,
  so one band covers the resting card and the strip. `RouteCard` takes the side as `bareSideOnLeft: Boolean?` (its signature is
  public, and the first green run, 42532670714, failed to compile with an internal `ScreenEdge` there).
  - Red: 42532668314 (tests only, on the code before the fix): 21 failures, each "not bare (a second line beside the side bar's):
    1 px in reads #ABA298 [day] / #787878 [night]", the ruled line where the card's own fill is expected. The four navigating strip
    cases also failed on their bottom, a test error (below), fixed in 02f87374d.
  - Revert: 42536206321, the route card told no bare side (`bareSideOnLeft = null`, one line in `CompactMapRoutePlotting.kt`). The
    compile step passed with no `e:` lines in the log; 29 failures, every one carrying "not bare (a second line beside the side bar's)"
    and no other message; no other test changed. Each is a case this revert can fail: the line put back on the side bar's side. The
    revert was built from a saved copy into a separate commit with its own index; the working tree was never edited, and the forward
    line was confirmed present (byte compare with the saved copy) before and after.
  - Green: 42536203917 on 046b2e168, every job green.

## Tests whose claims changed (1.1)

- `RouteCardOutlineOnNavigationCardTest` (re-claimed by the first session, 7 cases): the side against the side bar bare, not lined.
- `RouteCardOutlineLandscapeTest`: its two cases claimed the ruled line on every side, the "right side" (the side bar's) included;
  now that side is bare, the top, bottom and map side lined. Renamed ("... as its other sides do" to "... and its side against the
  side bar has none of its own"). Added: the plotting card at both rotations, day and night (4), and the far-corner control (1).
- `RouteCardTabOutlineLandscapeTest`: its two cases claimed the ruled line on the "side on its screen edge"; now that edge, the side
  bar's, is bare. Renamed ("... all the way round" to "... and none of its own against the side bar"). Added: the strip while plotting
  and while navigating, both rotations, day and night (8). While navigating its bottom is read bare, where it meets the navigation
  card (A1). The portrait tab cases are unchanged.
- `RouteCardMeetsNavigationCardTests` (8 cases, names unchanged): claimed the ruled line down both sides into the corner wedges; now
  the side bar's side reads bare over the same rows, the map side lined as before. Not named in the dispatch: the fix failed these 8
  on the side bar's side only (42534637801), which is the ruling's own edge.

## By name against 42521097620

6,936 tests there, 6,957 in 42536203917, 0 failing in either. 7 old names gone, all re-claims above renamed (3 in
`RouteCardOutlineOnNavigationCardTest`, 2 each in the two landscape outline classes); 28 new names (the re-claimed and added cases,
plus `RouteCardNavigatedFromCardEndsTest` 2, `RouteCardStripPressTest` 1, `RoutePlottingViewModelTest` 1). Every other test has the
same outcome in both runs.

## Confirmed vs inferred

- Confirmed: each revert ran (no compile errors in its log; 6,957 tests reported for 1.1's) and failed only on what its own edit
  can cause; the forward changes are at route-card's head.
- Confirmed from CI output: in landscape plotting the card and its strip sit against the side bar (the strip at 719..743 dp on an
  823 dp screen with the 80 dp rail); the tests now assert that as a precondition.
- Inferred: the app's real ripple spills on the phone as the owner saw; under Robolectric it is not drawn (below).
- Inferred, from reading: the side bar draws its own line on its inner edge, so the shared line still shows there. Not read in pixels
  by these tests, which read the card's side only.

## Could not determine

- Whether a press highlight still shows on a phone: the 2.2 test reads Compose's default indication with the app's theme left out,
  because Material's ripple is drawn by a platform view Robolectric does not render. The first version, under the app's theme, read 0
  changed pixels on the code with the highlight (42525146220), so it could not fail, and was replaced. For the S22.
- How the card's top line and the side bar's line meet at the corner on a phone: the band leaves the top line running to the card's
  edge, and the resting card's rounded top corner on that side still curves to it. Robolectric reports zero insets, and the corner
  pixels are not asserted. For the S22.

## Premises that were wrong

- "In landscape navigation" named one of the places the card meets the side bar; landscape plotting puts the card and its strip
  there too. The owner's (c) covers all three.
- The dispatch named two existing tests to re-claim; a third, `RouteCardMeetsNavigationCardTests`, also claimed the side bar's line
  (down into the corner) and is re-claimed above.

## Decided beyond scope

- The fullscreen case with no system bar on that side leaves the side bare, by the navigation card's rule, though Amendment 5 lined a
  side flush to the screen's edge. That follows "where that rule leaves the rail side lined the route card matches"; on the S22 there
  is a navigation bar, so the line shows there.
- The navigation card's outline call was moved into a shared function so the two cards read one rule; it draws exactly as before
  (same arguments, and every navigation card outline test passes unchanged).

## Questions

- None open for 1.1.

## S22 steps

20. **Strip press.** A saved route open, tucked to its strip. Press and hold on the map just beside the strip (within a finger's width
    of it): no grey highlight appears anywhere; lift: the card slides back.
21. **One line at the side bar (landscape).** Recording, plot a route in landscape with the icon bar on the left: the card sits by the
    side bar at the bottom right. Where the card meets the side bar, one faint line only (the side bar's), none of the card's beside
    it; the card's top, bottom and left keep their faint line. Tuck the card (swipe it toward the side bar): the strip's edge against
    the side bar has no line of its own, its other three sides do. Bring it back, tap Navigate: the card resting on the navigation
    card has the same single line at the side bar, and the two cards agree there. Turn the phone the other way round: the same. Tap
    Fullscreen: the side bar slides away and the card's side there now shows its own faint line (a navigation bar is left on that
    side), as the navigation card's does. Move the icon bar to the right side: the card moves to the bottom left and keeps its line on
    every side.
