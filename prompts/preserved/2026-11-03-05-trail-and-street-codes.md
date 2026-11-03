# Dispatch 2026-11-03-05: our map's code name becomes "trail"; "street" names the OpenStreetMap map (Hazel, Opus)

Written by Rowan Waymark, 2026-11-03. RECORD -1999. The owner, on the planner's call in -1997 (leaving our Trail Map's internal "street" names alone): "Go ahead and make a new map code called Trail drop OSM, and just use street for OSM. That should fix the problem". This builds on the owner's earlier ruling in -1997: "The OSM button should be renamed to \"Street\" to differentiate itself still. OSM credits can still apply".

Every fact below is a premise to re-check, with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What changes |
| **B** | What must not break |
| **C** | Branch, build and tests |

**For every item:** verify first: read the code and report to the planner by message before writing anything. Then fail first, and a revert check on a throwaway CI branch (check the build log for compile errors; restore from a saved copy). Full CI must be green on the last real commit. CI only: no Gradle on the laptop. Don't touch the phone. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object. Our own map is the "Trail Map". Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name. A history rewrite of the repository is planned (-1994): push your work often, and stop pushing when the planner says the window has started.

## A. What changes
- **A1** (-1999). Our Trail Map's code name is "trail" everywhere in code we own: MapMode.TRAIL's stored key, the style's file and class names (StreetStyle.kt and its kin, StreetNightTables.kt, the "street" layer and asset names), the Worker's style routes and the styles' `name` fields ("Forager Street vN" becomes "Forager Trail vN"), and the map-style build scripts. Name each rename. "OSM" is dropped as a name for anything of ours.
- **A2** (-1999). The OpenStreetMap map's code name is "street": its button label reads "Street" (MapMode.kt:48 at pre-main 3b9c0b70b, now "OSM"), and its own code names (MapMode.OPENSTREETMAP, Basemap.OSM_STANDARD and their kin) become Street names. Its tile address (tile.openstreetmap.org), its licence credit and every OSM data credit stay word for word.
- **A3** (premise to re-check). MapMode.kt:38-44 at pre-main records that the stored key "street" already moved once, from OpenStreetMap to the Trail Map (dispatch 2026-10-22-01 Amendment 2), with a note that nothing writes "street" for OpenStreetMap again. This dispatch reverses that on purpose, so the migration in B1 is required.

## B. What must not break
- **B1** (the planner's call). Each user keeps the map they chose. A one-time, flagged migration of the stored map choice: the old "street" (the Trail Map) becomes "trail", and then the old "openstreetmap" becomes "street", in that order, run once and never again. Tested for each starting value, including no value and an unknown value, and with the migration run twice.
- **B2** (the planner's call). Shipped builds keep working: the Worker keeps serving the old style routes (street-vN) next to the new trail-vN routes, and the tile and asset cache keys are handled (3b9c0b70b added a cache version to every key). Name which old routes stay and why.
- **B3**. Anything else stored or shipped under the old names (DataStore keys, offline region metadata, file names on the phone, the backup format) is listed in the verify-first report, with a proposal. No Room migration without the planner's say (a migration number is a stop).

## C. Branch, build and tests
- **C1**. A new branch, trail-and-street-codes, cut from origin/pre-main (d88db1799 or later). The planner opens the PR when asked.
- **C2**. Tests through real entry points: the Layers sheet's buttons by real coordinate touches show Trail, Topo, Street; the migration through the real preferences repository; the Worker's tests for both old and new routes; the style checkers regenerate to what is committed.
- **C3**. New user-facing words: only the "Street" label. Anything else goes to the planner first.
