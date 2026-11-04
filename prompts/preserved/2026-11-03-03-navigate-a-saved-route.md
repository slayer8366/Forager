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

- **A4** (2026-11-03, -1990). Hazel's verify-first (read at route-plot-ui b4749e63e) corrected two planner premises: Navigate and Directions sit side by side nowhere today and a saved route's pin has no bubble, so the card's row is new, not copied; and the route line starts at stop 1, so the way from the walker to stop 1 is not on it. The owner's rulings on her questions, each the option as read:
  - To stop 1: "Like a waypoint (Recommended)": "Exactly what Navigate on a pin does today: a way along trails from where you are to the first stop, or a straight line with its note if there's no trail. Then the route's legs, stop by stop. If you're already at stop 1, it moves straight on to stop 2."
  - Off the route: "Keep the saved route (Recommended)": "The route you planned stays drawn. More than 50 m off, the distance switches to straight-line to the next stop, and after 15 s it shows "Off trail · Head back to the line", as waypoint navigation does today. It never redraws your route."
  - Distance: "To the next stop (Recommended)": "The distance along the route to the next stop, with the stop's name, for example "To Ridge · 2 of 3". It drops at each stop and starts again."
  - At a stop: "A short buzz plus words (Recommended)": "The navigation briefly shows "Reached Ridge", the phone gives one short vibration, then it shows the next stop. You notice it with the phone in your pocket. Today nothing in navigation vibrates."
  - Stopping: "Pick up where you left off (Recommended)": "The app remembers the next stop you hadn't reached. Navigate carries on from there, with no prompt. Closing the app mid-route also picks up again, as waypoint navigation does today. To start over, plot it again or wait until you've finished it."
  - Out of order: "Only the next stop counts (Recommended)": "Reaching a later stop early does nothing. It keeps guiding you to the stop you're headed to. Nothing is skipped without you choosing it, and nothing pops up."
  - The card: the owner, in their own words to the option "Card closes" ("Navigate closes the card and only the navigation card shows. The route line stays drawn on the map."): "Option 2, but have it collapsible. Add an i button to the navigation card at the top right corner to summon the card. Both landscape and portrait". The planner's reading, confirmed by the owner ("Yes to both", -1992): Navigate closes the route card; the navigation card gets an "i" button at its top right, in portrait and landscape, that brings the route card back, collapsible as now; the route line stays drawn either way.
  - Arrival fix: "GPS only (Recommended)": "Only a real GPS fix moves you on, so a rough network fix (which can jump hundreds of metres) can't tick off a stop you haven't reached. Moving on can't be undone, so it's stricter than the "Arrived" shown for a single waypoint."
  - After the last stop: A1 stands ("Arrived" and the ring, on until ended, as for a waypoint).
- **A5** (2026-11-03, -1990, the planner's calls): AvailabilityViewModel.kt (797 lines) is split before it grows past 800 (-1490); WayHome.regionId takes a stated "none" for a saved leg (Hazel picks nullable or a named constant and says which); two stops closer than the arrival radius move on one stop per fix, never two on one fix.
- **A6** (open, for the owner with the words): what the summoned route card shows while navigating (the stop headed to marked, and whether its Navigate reads "Stop navigating"); Back's order; the "i" button's look beside the map credit's "i"; every new word (Hazel's list: the next-stop line, "Reached <name>", "Stopped navigating: that route was deleted.").

## B. Branch, build and tests
- **B1** (2026-11-03, -1988). A new branch, route-navigate, cut from origin/route-plot-ui after D32 lands (not before), merged into pre-main only after #449, on the owner's word. A draft PR is opened only when the planner says.
- **B3** (2026-11-04, -2047, replaces B1's base). #449 is merged (route-plot-ui is in pre-main), so route-navigate is cut from origin/pre-main at 8f240b447 or later; confirm the base before writing. The PR goes against pre-main, opened only when the planner says. Its merge waits for the owner's word. The crowded-list-bubble branch is being built in parallel and touches the map screen: name any file you both change in your report.
- **B4** (2026-11-04, -2047). The order of work. First, a short verify-first report to the planner re-checking A2 and A4's premises at the new base: RouteCard.kt is 389 lines and AvailabilityViewModel.kt is 790 there, by the planner's count. Then the A5 split, then the domain logic (headless, B2), then the ViewModel and the cards. A6 is not ruled yet: build behind placeholders the planner names, and don't settle any word, look or Back order yourself. The planner will send the owner's A6 rulings as A7.
- **B2**. Tests go through the real entry points: the card's Navigate with real coordinate touches, and the ViewModel driving navigation with fixes fed in. Cover: start, each arrival moving on, the last arrival ending, and Stop. Domain logic (the next stop, distance along the route, arrival) stays free of Android UI so it can be tested headless.
