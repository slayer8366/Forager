# Dispatch 2026-11-02-04: waypoint names on the map (Hazel, Opus)

Written by Rowan Waymark, 2026-11-02. RECORD -1964, -1965. Starts after dispatch 2026-10-30-08's D23 is green.

**The owner**, from a landscape screenshot while plotting a route: "One thing for clarity, have waypoints be labeled on the map. There can easily be dozens that clutter a map, so maybe have it display during route plotting. Do you think it should display the waypoint names all the time? What do typical maps do?" Then, to the planner's options: "Option 1", whose option read "Always, crowded ones hidden until you zoom in, the route's stops always named"; after a researcher's check (RECORD -1964): "Option 1 plus an off switch", whose option read "As above, with a setting to turn names off, as CalTopo has." Then "Yes, start both".

**Prior art, read at source by the researcher (-1964):** CalTopo's Show Labels: "Non-Overlapping: when labels overlap only one label will show. As you zoom in you'll see more of your labels" (training.caltopo.com/all_users/tools/config), with All and None.

**Base:** route-plot-ui after D23 (the route's stops need #449). New branch `waypoint-names` cut from it; a PR into route-plot-ui's PR's base later, as the planner says. No merge. Every premise below is to re-check with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | Names on the map |
| **B** | The setting |
| **C** | Tests and checks |

**For every item:** verify first and report by message before building; fail first, then a revert check (build log checked, restore from a saved copy, forward change confirmed after); full CI green on the last real commit, no empty commits; commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. The names are the user's own record names; no new words on screen beyond B1's, which the planner brings to the owner. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object.

## A. Names on the map
- **A1** (2026-11-02, -1964). Every waypoint's name shows on the map beside its pin, by default. Where names would overlap, only the ones that fit show; zooming in shows more (the map's own label collision, as CalTopo's "Non-Overlapping"). Pins themselves are unchanged, including the crowded-icons fan and list (say how names and the fan interact; stop and ask if they conflict).
- **A2** (2026-11-02, -1964). While plotting a route, and on a saved route's card, the route's stops always keep their names, even where they would collide. Say what yields to them.
- **A3** (2026-11-02, -1964). Names follow the map's night and day look, and never sit over map chrome (CLAUDE.md, nothing fully obstructs the map).

## B. The setting
- **B1** (2026-11-02, -1964). A setting turns waypoint names off (on by default). Propose where it lives and its words; the planner asks the owner before it ships. It persists across restarts (DataStore, per CLAUDE.md's Room/DataStore rule).

## C. Tests and checks
- **C1** (2026-11-02, -1964). Through the real Maps tab: names show by default; a crowded group shows fewer names than pins and more after zooming in; the route's stops are named while plotting; the setting hides them and survives a restart. Fail first, then revert checks. No phone unless the planner says.
