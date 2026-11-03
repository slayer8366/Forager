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

## D. Rulings on Hazel's verify-first (2026-11-03, -2000)
Hazel read pre-main at d88db1799. Corrected premises: no style layer or app asset is named "street". The only runtime ids, "basemap-street" (BasemapStyles.kt:89-90), name the OpenStreetMap raster, so they stay. B2's cache keys need no change. The chips are in MapTypePanel, not the Layers sheet.
- **D1** (S1, the owner). "Add trail-v4 now", whose option read "Publish a copy of today's style as trail-v4 and switch the app to it. Every downloaded region would then say "Older map style. Download again to update.", which isn't true, unless extra code treats the two as the same. The server has to go live before any app uses it."
  - The planner's calls: street-v4 and trail-v4 are treated as the same style, so no region shows a false "Older map style" notice. That's tested, and it's the "Don't make me think" rule.
  - trail-v4 goes into STREET_V4_STYLE_FILES' and STREET_STYLE_URLS' successors, and is tested to get v4's night tables.
  - The Worker change lands and is served before any app build loads trail-v4. Hazel reports the release route and stops before any deploy, since that is outward-facing and needs the planner's word.
  - No app build that loads trail-v4 goes on the S22 until the Worker serves it.
- **D2** (S2, the planner's call). street-v1..v4 and offline.json stay byte for byte, frozen as now. "Forager Trail v4" appears only in trail-v4.json.
- **D3** (S3, the planner's call). osm_timestamp stays. It names the OSM data's date, which is a data reference, and it's a stored and wire format.
- **D4** (S4, the planner's call). Log tags and messages take the new names. Name each one, so phone-check scripts can be updated.
- **D5** (S5, the planner's call, as Hazel proposed). Update comments that name a renamed identifier or call our map "Street" in the present tense. Quotes and history stay. TopoNightStreetSwitchTest keeps its name. docs/, RECORD and prompts are not touched.
- **D6** (the owner). CLAUDE.md: "Change to "Trail Map tiles" (Recommended)", whose option read "Only that word changes. The line stays where it is, so nothing that points to its line number moves." The "Don't make me think" bullet's "Street tiles" becomes "Trail Map tiles", with the line count unchanged. README.md and STATUS.md go with -1992's Trail Map wording, in this branch.
- **D7** (the owner, 2026-11-03, -2001). "We will migrate away from OSM altogether so we should keep all OSM references detached and easily removable from this point on." On its scope: "The Street map first (Recommended)", whose option read "From now on the Street button's map (OpenStreetMap's own tiles) lives in one self-contained place that can be deleted cleanly. OSM data (Trail Map tiles, trails, routing) is kept behind its own boundary too, ready to swap out when you choose a replacement source, but nothing changes for users now." For this branch:
  - Everything about the Street map goes in one place, behind an interface the rest of the app depends on: its Basemap entry, MapMode entry, chip, night raster ("basemap-street"), credit, tile address and fallback. Deleting that place, and the one line that registers it, leaves the app compiling and working.
  - Give the size of what that deletion would be and test that the app runs without it.
  - The fallback when the Trail Map's style fails to load (OwnStreetFallback, -1513) currently falls back to the Street map. Report what it would fall back to without it, and stop before changing that behaviour: it's the owner's call.
  - Report where OSM data enters (tiles, trails, routing, osm_timestamp, BRouter) and propose the boundary. Build only what this branch already touches; the rest is a later dispatch.
