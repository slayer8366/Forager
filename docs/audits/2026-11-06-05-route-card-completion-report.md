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

- Tests: `RouteCardTuckCloseTest` (new, 11 cases), on the real compact Maps tab with the real `RoutePlottingViewModel`. Every touch is a
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
- Full CI by name against pre-main's latest run: see the PR's run, recorded in the hand-back.

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
