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
