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
## B. Branch and tests
- **B1.** A new branch, crowded-list-bubble, from origin/pre-main.
- **B2.** Tests through the real screen, with real coordinate touches on a stack, using a fixed clock (D43's lesson):
  - the bubble is anchored to the stack and moves with a pan;
  - its height is at most the standard bubble's;
  - a 10-record stack scrolls;
  - a row opens its record's bubble, and Back returns to the list;
  - X closes it, and a map tap closes it;
  - portrait and landscape, navigating, at font 1.0 and 2.0.
