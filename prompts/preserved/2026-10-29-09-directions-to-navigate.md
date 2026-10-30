# Dispatch 2026-10-29-09: Navigate takes Directions' place (a coder, Opus)

Written by Rowan Waymark, 2026-10-29. RECORD -1817, -1818. Cut from **pre-main 21d846b9e**. Check that this is still the remote head before you start.

**Authorisation.** The owner, verbatim:
- "Now that we have in-app navigation, we should remove the Google directions option from the waypoints bubble in the maps."
- "Everywhere Navigate exists", whose option read: "Remove it wherever in-app Navigate is offered, and add Navigate where Directions is today (lists, details sheet)."
- "Small two now, route plan next (Recommended)"

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## What exists (read at 21d846b9e; re-check each line)
- Directions launches the phone's maps app through a `geo:` intent: `launchDirections`, AvailabilityPureFunctions.kt:152-190.
- **Map bubbles:** MapBubbleLayer.kt:144. Navigate is passed only on the Maps tab (`onNavigateToWaypoint`, MapBubbles.kt:198-202). An entry map passes `null` and offers Directions only.
- **Details sheet:** RecordDetailsContent.kt:78-94. Navigate shows when `onNavigate` is passed, and Directions always shows.
- **Rows:** the trip row at AvailabilityTripsWaypointsUi.kt:160 and the waypoint row at :300.
- **Trips:** a planned trip has a bubble with Directions (MapBubbles.kt:245). Whether in-app navigation can go to a trip's point today is for you to find out.

## Stop first (look only, no code)
List every place Directions is offered. For each one, give:
- (a) its file:line and the screen it is on;
- (b) whether in-app Navigate is offered there today, or could be with the existing waypoint-navigation path (say what that would take);
- (c) what that place would show under the owner's ruling.

List the places where Navigate cannot be offered, such as an entry map, or a trip if it has no navigation path. Those are the owner's call. Propose keeping Directions there, and do not decide it. Write this as docs/audits/2026-10-29-directions-to-navigate-first-stop.md, push it, and wait for the planner's amendment.

## Change (after the amendment)
- Where Navigate is offered: remove Directions.
- Where Directions was and Navigate can be offered: Navigate replaces it, through the same path the Maps tab's bubble uses (dispatch 2026-09-28-502), Back included.
- `launchDirections` stays while anything still calls it. Once nothing does, remove it and its tests in the same change.

## Tests
- Through real entry points: the Maps tab's bubble, the details sheet and the rows. Assert that Directions is absent and Navigate is present by tag and label, and that tapping Navigate starts waypoint navigation.
- Where a test claims a touch reaches a control, use a coordinate touch (CLAUDE.md).
- **Fail first, then revert checks:** one per rule, with the build log checked for compile errors. Restore from a saved copy, never from git, and confirm the forward change is still present afterwards.
- **Modular rule:** new files stay under 400 lines. Split before adding to anything over 800 lines, or stop and say so.
- CI only, with no Gradle on the laptop. Open a PR into pre-main, with no merge. Put the S22 steps in the PR body.

## Rules
- Branch `directions-to-navigate`, worktree ~/.cache/forager-wt/directions-to-navigate.
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Past about 200k tokens: push and hand off.
- Ambiguity: stop and report.
- New words on screen are the owner's only. "Navigate" already exists.
