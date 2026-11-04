# Dispatch 2026-11-04-02: the crowded-icons list as a floating bubble (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2039. The owner, with two screenshots (portrait and landscape, navigating, the "4 records at this spot" card covering the route card, the navigation card and Re-center): "The list mode for map icons is a screen hog. Can it be a floating bubble that follows the icon cluster on the map, and adjust its height to the standard bubble size to have it fit easier?" Then, to the planner's questions:
- "Its own bubble replaces it (Recommended)", whose option read "The record's normal bubble (name, coordinates, elevation, Navigate…) opens in the same place; Back returns to the list."
- "Yes, as described (Recommended)", whose option read "Anchored to the stack, follows the map, standard bubble size, scrolls inside, X / Back / tapping the map closes it."

Every fact below is a premise to re-check, with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the user sees |
| **B** | Branch and tests |

**For every item:**
- Verify first: read the code and report to the planner by message before writing anything.
- Then write the tests first, run a revert check on a throwaway CI branch, and get full CI green.
- CI only: no Gradle on the laptop.
- Don't touch the phone.
- Never communicate with any session named Aspen. Never read, name or quote the Restricted Object.
- Our own map is the "Trail Map".
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.
- New user-facing words go to the planner first.
- Map chrome is at 80% (CLAUDE.md), as the bubble already is.

## A. What the user sees
- **A1.** The steps: with Settings > Map > Crowded icons set to the list mode, tap a crowded stack. A bubble opens anchored to the stack, with its tip at the stack, and follows it as the map pans and zooms, the way the waypoint bubble does (MapBubbleShell; CrowdedStackList.kt at pre-main d7a49bb58). It is the standard bubble's size: its width, and no taller than the standard bubble's maximum. It shows the heading "N records at this spot", then the rows, scrolling inside where they don't fit. X, Back, or a tap on the empty map closes it.
- **A2.** Tap a row, and that record's own bubble (name, coordinates, elevation, actions) opens in the same place, replacing the list. Back returns to the list; X closes both.
- **A3.** The old full card is removed, with what it leaves dead, named.
- **A4** (premises to report). Where the list card is placed today, and how; how the waypoint bubble anchors and follows; what the standard bubble's size limits are; and how this sits with the route card, the navigation card, Re-center and the D-items' clearance rules (dispatch 2026-10-30-08). Anything that is the owner's call is a stop.

- **A5** (the owner, 2026-11-04, -2039). "And have it animate open and shut please". The bubble animates when it opens and when it closes. The planner's calls: it uses the standard bubble's existing open and close motion if it has one (report which). If it has none, it grows from and shrinks to the stack's tip, with an existing MotionTokens spec named in the report. Opening a record's bubble from a row, and Back to the list, use the same motion. It is instant under Reduce Motion. A test asserts a mid-frame state with mainClock.autoAdvance off, and the instant state under Reduce Motion.
- **A6** (the owner, 2026-11-04, -2039). "For crowded icons again: have the zoom-in and zoom-out animate rather than jump in also so it looks smooth". Wherever the crowded-icons behaviour moves the map camera (the zoom-then-fan mode zooming in on a stack, and any zoom back out when it closes; report each path with file:line), the camera animates rather than jumping. The planner's calls: use an animated camera move with the app's existing camera timing where there is one (name it), so that it ends exactly where the jump would; it is instant under Reduce Motion; and a pan or zoom by the user during the move takes over without a fight. A test asserts the camera is asked for an animated move with that duration, and an instant one under Reduce Motion.
## B. Branch and tests
- **B1.** A new branch, crowded-list-bubble, from origin/pre-main.
- **B2.** Tests through the real screen, with real coordinate touches on a stack, using a fixed clock (D43's lesson):
  - the bubble is anchored to the stack and moves with a pan;
  - its height is at most the standard bubble's;
  - a 10-record stack scrolls;
  - a row opens its record's bubble, and Back returns to the list;
  - X closes it, and a map tap closes it;
  - portrait and landscape, navigating, at font 1.0 and 2.0.

## C. Rulings on Hazel's verify-first (2026-11-04, -2040, the planner's calls within the owner's words)
Hazel read pre-main d7a49bb58 and corrected these premises: the standard bubble has no maximum height; it re-anchors when the camera rests, or every move while navigating, not frame by frame; a pan closes the list today (-1753, -1757); X on a row's bubble brings the list back today; the standard pop-up fades under Reduce Motion rather than going instant; both crowded-icon camera paths jump.
- **C1** (S1). The height cap is the tallest standard bubble: a waypoint bubble with every action at font 1.0, measured in a Robolectric run and kept as a named constant, and never more than the room on screen. This is the owner's "the standard bubble size".
- **C2** (S2). A pan or zoom keeps the bubble open and following, which is the owner's "follows the icon cluster on the map". This supersedes -1753 and -1757 for this bubble. A user-asked jump (Locate, a search result, Return to Route) still closes it.
- **C3** (S3). From a row's record bubble: Back returns to the list (A2), while X, an empty-map tap, or any of the bubble's actions (Navigate, Details, Directions, Plot route) close both.
- **C4** (S4). Under Reduce Motion it keeps the standard pop-up's fade, like every map pop-up. This corrects the planner's "instant" in A5 and A6 for the bubble; A6's camera move still jumps under Reduce Motion.
- **C5** (S5). The camera uses NAVIGATION_VIEW_TRANSITION_MILLIS, 750 ms, with Hazel's approach (i): find the end by the existing steps, then make one ease there; the fan or list opens on finish, and a user gesture cancels it. Check for a stray frame on the next phone session.
- **C6** (S6). The standard bubble's rule against the cards stays (the cards draw over it). The bubble also keeps clear of the icon cluster's column in portrait, as the old list did (run 40580543726).
- **C7.** In zoom-then-fan mode, a stack still on one spot at zoom 17 opens this same bubble. Confirmed.
- **Worktree:** under /mnt/work/forager-wt/ while the root disk is short. The planner freed about 1.1 GB on / by removing six worktrees of merged branches.
