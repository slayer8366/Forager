# Dispatch 2026-11-02-06: editable map parts, a read-only look at the code (Juniper, read only)

Written by Rowan Waymark, 2026-11-02. RECORD -1971. The owner: "What would it take to make every map part modular and editable in-app, so then people can choose overlays they want shown, what zoom it can be read in, what color fill, etc."; then, to the planner's sketch (four steps: map parts with switches, zoom per part, colours and opacity, presets): "Yes, start the read-only look".

**Bases:** origin/pre-main and origin/route-plot-ui at their heads; name the commits read. Every premise below is the planner's, unverified, and to be checked with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the map is made of |
| **B** | What already changes it at runtime |
| **C** | What each step would cost |

**For every item:** every claim carries a file and line at a named commit, marked read, computed or inferred. Read only: no code, no Gradle, no phone, no Pi, no merges. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## A. What the map is made of
- **A1** (2026-11-02, -1971). Every style the app draws (Trail Map v4 and older Street styles in server/pmtiles-worker/src/, offline-style.json, the Topo and OSM rasters, the trails overlay, hill shading, the app's own record layers): list each, its layer count, and the layers grouped into parts a user would recognise (roads, trails, water, forest, buildings, place names, POIs, contours, ...). Say which parts exist only in raster styles, where nothing per part can change.
- **A2** (2026-11-02, -1971). For each part: the zoom range its data exists at in our tiles (the archive, and offline downloads at z10 to 15), which bounds what a zoom setting could ever show.

## B. What already changes it at runtime
- **B1** (2026-11-02, -1971). Every place the app already edits a loaded style: the night recolour (NightImageSwitch, NightImageApply, nightColorOf), PatternRelayout, the Trails switch and Map Layers' overlay switches (MapLayersToolsSection.kt), the -1513 fallback, the credit strip's style hooks. When each runs (on every style reload?) and how a user edit would join it.
- **B2** (2026-11-02, -1971). Which MapLibre Android calls the app's version supports for per-layer visibility, zoom range and paint properties (read the version from the build files and the library's API docs for that version, quoted).

## C. What each step would cost
- **C1** (2026-11-02, -1971). For each of the planner's four steps (1 map parts with switches, 2 zoom per part, 3 colours and opacity with a night colour for each, 4 presets): the files it would touch, what it would add (domain model, DataStore or Room per CLAUDE.md, UI, tests), what it would break or interact with (night mode, offline, the pattern workaround, credits), and a size in dispatches like the ones in RECORD this month. Say what is not knowable without a spike.
- **C2** (2026-11-02, -1971). Anything that makes a step harder than the planner's sketch, or unnecessary.

**Output:** docs/audits/2026-11-02-editable-map-parts-pulse.md on a new branch `editable-map-parts-pulse` cut from origin/records-after-173, worktree ~/.cache/forager-wt/editable-map-parts-pulse. Push as you go. No PR. The four disclosure sections: confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope. Final message: in plain language, how many parts, what step 1 would take, and the commit hash.
