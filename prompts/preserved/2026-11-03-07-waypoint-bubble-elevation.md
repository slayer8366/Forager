# Dispatch 2026-11-03-07: the waypoint bubble shows the map's ground height (Hazel, Opus)

Written by Rowan Waymark, 2026-11-03. RECORD -2025. The owner: "Now that we have map elevation, can we add elevation to the Waypoint map bubble below the coordinates?" Then, to the planner's questions:
- "The map's ground height (Recommended)", whose option read "The ground height at the waypoint's spot from the trail data's height grid (about 90 m spacing), in your chosen units, e.g. "Elev 1,234 ft". Same for every waypoint at that spot, however it was made. Available inside downloaded areas."
- "Leave the line out (Recommended)", whose option read "The bubble shows name, coordinates and distance as now, with no elevation line. Nothing to misread."

Every fact below is a premise to re-check, with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the user sees |
| **B** | Where the height comes from |
| **C** | Branch and tests |

**For every item:**
- Verify first: read the code and report to the planner by message before writing anything.
- Then write the tests first, run a revert check on a throwaway CI branch, and get full CI green.
- The only allowed failure is RouteCardOnNavigationCardFont20Test on an evening run, with its D37 message.
- CI only: no Gradle on the laptop.
- Don't touch the phone.
- Never communicate with any session named Aspen. Never read, name or quote the Restricted Object.
- Our own map is the "Trail Map".
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.
- New user-facing words go to the planner first.
- Keep OSM-dependent code behind its boundary (RECORD -2001).

## A. What the user sees
- **A1.** The steps: tap a waypoint on the map. Its bubble shows the name, then the coordinates (MGRS), then a new elevation line, then "from start" as now. The line is the ground height at the waypoint's position, in the user's chosen units, e.g. "Elev 1,234 ft". The owner set the words: "Example: Elevation 372 ft." So the line reads "Elevation 372 ft": the word Elevation, a space, the whole number, then the unit in the user's chosen units ("m" for metres), with no colon. (RECORD -2025, the owner's second message.)
- **A2.** Where no ground height is known for that spot, the line is left out. Nothing else in the bubble changes.

## B. Where the height comes from
- **B1** (premise). The route squares downloaded with each region carry heights on a 3-arc-second grid, posts about 90 m apart, since the route build of 2026-10-28 (WayHomeHeights.kt's KDoc at pre-main 15e12da94; RECORD -1835). Find how to read the height at an arbitrary point from the downloaded squares. Note that the routing adapter exposes heights only along routes. Report the path and its cost: the time and memory per bubble open, on the main thread or not.
- **B2.** Lookup happens off the main thread. The bubble shows at once, and the line appears when the height is ready. No network.
- **B3.** If a square has no heights (built before 2026-10-28, or one of the five built without them), the line is left out, and that is logged once.
- **B4.** Keep the lookup in the domain, free of Android UI, behind an interface the project owns, so it can be tested headless and its source can be swapped later (-2001, -2002).

## C. Branch and tests
- **C1.** A new branch, waypoint-bubble-elevation, from origin/pre-main.
- **C2.** Tests:
  - the lookup against a small fixture square, with known posts and an interpolated point;
  - the bubble through its real entry point, with a waypoint inside and outside a downloaded area;
  - feet and metres;
  - a square without heights.

## D. Rulings on Hazel's verify-first (2026-11-03, -2026)
- **D1.** Correction of B1, the planner's premise. The route squares hold one height per way node, not a grid (OsmNode.java:28,132; BRouterTrailRouter.kt:216-228). The 3″ grid was sampled at build time and is not on the phone. WayHomeHeights.kt:17-19's KDoc says otherwise and is corrected in this branch.
- **D2** (the owner). The source: "Map relief tiles (Recommended)", whose option read "True ground height at the waypoint's spot, from the Trail Map's hill-shading data (about 54 m grid here). Shows only in areas downloaded with the current Trail Map (re-downloading older regions adds it). Hazel first does a short test that the offline tiles can be read."
  - Start with a spike: read a relief tile for a point from MapLibre's offline database, in a test with a real database file.
  - Report the schema and compression, and the risk on an SDK upgrade.
  - If it can't be read reliably, stop.
  - Also find the relief data's origin and whether its credit is shown. If a credit is owed and missing, stop and report.
- **D3** (the owner). Numbers: "Elevation 1352 ft (Recommended)", with no separator, using formatWholeLength.
- **D4** (the owner). Where: "Every map with bubbles (Recommended)".
- **D5** (the planner's call). Snapshot-only waypoints get the line too (the owner's "however it was made").
