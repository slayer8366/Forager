# Dispatch 2026-11-03-03: Navigate on a saved route's card: walk the route stop by stop (Hazel, Opus)

Written by Rowan Waymark, 2026-11-03. RECORD -1988. The owner, with a screenshot of a saved route's card in portrait ("Waypoint 1 to Waypoint 2", "3 Waypoints routed 1.3 mi", "Directions"): "Add a navigate function to this card please". Then, to the planner's questions:
- "Walk the route, stop by stop (Recommended)", whose option read "Tap Navigate > the app's navigation starts toward Waypoint 1 along the route line. When you reach a stop, it moves on to the next one, until the last. The navigation card shows the next stop and the distance along the route."
- "Navigate, then Directions (Recommended)", whose option read "Navigate comes first and Directions stays beside it, matching the pin's bubble, where Navigate is first among its actions."
- "Moves on by itself (Recommended)", whose option read "Within the usual arrival distance, it says you've reached the stop and goes straight on to the next. Nothing to tap."
- "Its own branch after #449 (Recommended)", whose option read "#449 is green and nearly done. Route navigation is a bigger feature, so a separate branch keeps #449 ready to merge when you say."

Every fact below is a premise to re-check, with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the user sees and does |
| **B** | Branch, build and tests |

**For every item:** verify first. Read the code and report to the planner by message before writing anything. Then fail first, then a revert check on a throwaway CI branch (check the build log for compile errors; restore from a saved copy). Full CI must be green on the last real commit. CI only: no Gradle on the laptop. Don't touch the phone. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object. Our own map is the "Trail Map". Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name. New user-facing words go back to the planner before they're built. Stop and report, don't choose, on anything the owner would see that this doesn't rule.

## A. What the user sees and does
- **A1** (2026-11-03, -1988). The step path, as the planner restated it to the owner: a saved route's card > **Navigate** (first, with Directions kept beside it) > the app's own navigation starts toward the route's first stop, along the route line > the navigation card shows the next stop and the distance along the route > within the usual arrival distance of a stop, it says you've reached it and goes straight on to the next, with nothing to tap > after the last stop, navigation ends as it does on reaching a waypoint.
- **A2** (premises, read at route-plot-ui b4749e63e by the planner): the saved card's only button is Directions (`ui/route/RouteCard.kt:295-309`, "Directions to the first stop on a saved route"). The app's own Navigate exists for single waypoints only (dispatch 2026-09-28-502: `domain/WaypointNavigation.kt`, `MapFeatureBubble.kt:94`, `RecordDetailsContent.kt:83`, `AvailabilityViewModelWaypointNavigation.kt`). Re-check all of it.
- **A3** (verify-first questions, for the planner to bring to the owner). Report what the code does today and propose, but don't choose:
  - you're far from the first stop: how you get to it, and what the card says;
  - you leave the route line: what "distance along the route" shows;
  - a route with repeated stops (plots allow repeats);
  - Stop navigating mid-route: what happens, and whether it resumes;
  - reaching a stop out of order;
  - the existing D-items around the navigation card (D17 to D32) and how they apply;
  - the arrival distance used today, with file:line;
  - what is said or shown at each stop: the words, and any sound or vibration.

## B. Branch, build and tests
- **B1** (2026-11-03, -1988). A new branch, route-navigate, cut from origin/route-plot-ui after D32 lands (not before), merged into pre-main only after #449, on the owner's word. A draft PR is opened only when the planner says.
- **B2**. Tests go through the real entry points: the card's Navigate with real coordinate touches, and the ViewModel driving navigation with fixes fed in. Cover: start, each arrival moving on, the last arrival ending, and Stop. Domain logic (the next stop, distance along the route, arrival) stays free of Android UI so it can be tested headless.
