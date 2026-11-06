# Dispatch 2026-11-05-09: the Tools drawer gets the map-chrome outline (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2180. Every fact below was read at origin/edge-shade 4728bbe42 (PR #495, not yet merged) while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim, with a screenshot of the open Tools drawer over the map: **"Give the tools drawer an outline also please"**.

**Changed (2026-11-06T04:50:04Z, RECORD -2180):** the owner, verbatim: "Add it to 495". Phase 2 builds on branch `edge-shade` (PR #495) and doesn't wait for #495 to merge. Where this file says "a new branch `drawer-outline`, cut from pre-main after PR #495 has merged", "after #495 merges" or "a PR into pre-main", read: commits on `edge-shade`, its completion report updated with a section for the drawer, and #495's body updated. #495 then waits for the drawer's S22 check and the owner's word.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. No coordinates or place names in anything pushed.

## Change
- **D1. The drawer's edge that faces the map gets the map-chrome hairline,** the same line the edge shade (PR #495) gives every other window over the map:
  - Bark at 0.4 by day, white at 0.4 at night, about 1 dp, drawn inside the edge;
  - following the sheet's rounded edge and its corners;
  - from the shared helpers #495 adds, not a new style.
- **D2. Only where the drawer is see-through over the map,** that is, on the Maps tab (`mapChromeFill(…, state.compactTab() == CompactTab.MAP)`, AvailabilityScreenDrawerHost.kt:103 as read). Over the other tabs it stays as it is today, solid with no line. This follows the owner's rule for the same component where it covers no map: "In the places that aren't covering a map, they can stay solid". That is the planner's reading of D2. Report anything that makes it doubtful.
- **D3. Both directions.** The drawer opens from the start side, or from the rail's side in landscape (ScreenToolsDrawer.kt:164 as read), so the line goes on whichever edge faces the map.
- **D4. Nothing else changes.**
  - The drawer's fill, content, scrim (transparent, -1313), size, gestures and motion all stay as they are.
  - If the drawer has an elevation shadow over the map, report it in Phase 1 and don't change it. The edge shade's rulings (-2154) covered windows, and the drawer wasn't in their list.

## Scope boundary
- **May touch:** a new branch `drawer-outline`, cut from pre-main **after PR #495 has merged**, so it uses #495's helpers; the drawer host; tests; a short completion report.
- **Must not touch:** pre-main; main; RECORD.md; the audit index; the phone; the branches `trip-download-maps` and `bug-14-picker-warning`; `CentrePinLocationPicker.kt`.

## Phase 1: verify first, read-only, report to the planner by message
Read at origin/edge-shade, and at pre-main once #495 has merged. Answer with file and line:
1. Where the sheet's shape and edge are drawn. Which edges face the map in portrait and in each landscape direction, and whether the far end runs under the status bar or the bottom bar.
2. Whether the drawer casts an elevation shadow over the map today, and its value.
3. Which of #495's helpers fit a drawer sheet (MapChromeMenuEdge, the bubble outline, the strip outline). If none fits, say what a new one would be.
4. How the existing pixel tests can read the drawer's edge, in day and night, portrait and landscape.
5. Any premise above that is wrong.

## Phase 2: on the planner's go, after #495 merges
- **Tests first,** through the real screen: open Tools on the Maps tab, sample pixels across the drawer's map-facing edge (day and night, portrait and landscape), and check that the line is there over the Maps tab and absent on another tab.
- **A revert check** under CLAUDE.md "Testing" runner rules: compile log first, restore from a saved copy, confirm the forward change afterwards.
- **Full CI** green, counts from the JUnit XML. A PR into pre-main, ready, **not merged**.
- **A short S22 check,** cheapest first.

## Stops
Stop and report on:
- anything a user would see beyond the line;
- a choice between edges the dispatch doesn't settle;
- a need to change the drawer's shadow, fill or scrim;
- a wrong premise;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main, green, merged on the owner's word after the S22 check.
