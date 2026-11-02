# Dispatch 2026-11-02-03: every layer of our own map at OSM's zoom levels, step 1, the comparison (Juniper, read only)

Written by Rowan Waymark, 2026-11-02. RECORD -1963. The owner, in the planner's window, verbatim: "Take \"set it to OSM zoom levels\" and run with it on the entire map." Earlier the same day: "If the map needs to be redownloadedb let's start now".

**What this step is:** a read-only comparison, layer by layer, of the zoom levels at which our map draws each thing against the zoom levels at which the OSM map draws the same thing. Nothing is changed here. The change itself is Aspen's (the style and the Worker are Aspen's, and every push to main redeploys the Worker); this report is what Aspen builds from.

**Bases, as written:** origin/main 8d0eefdbc or later, origin/pre-main 04746d8f0 or later. Verify both and say which you read.

## Amendments, by category
| | Category |
|---|---|
| **A** | What "OSM" and "our map" are |
| **B** | The comparison |
| **C** | What the change would need |

**For every item:** every figure carries its source (file and line, at a named commit or release tag) and is marked read, computed or inferred. The Restricted Object is never read, named or quoted. Read only: no code, no merges, no Gradle, no phone, no Pi. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## A. What "OSM" and "our map" are
- **A1** (2026-11-02, -1963). "OSM" is the map the app shows under that name. Find in the app's code (pre-main) which tile source and style that is, with file and line, and so which style's source to read (for the standard OSM tiles, the openstreetmap-carto project; name the release tag you read, and use a released tag, not a branch head). If the app's OSM is something else, stop and report.
- **A2** (2026-11-02, -1963). "Our map" is every style the app draws as its own: at least `server/pmtiles-worker/src/street-style-v4.json` (Trail Map's Street v4) and `offline-style.json`, and whatever night handling changes zooms. List each style the app can load, from the code (`StreetStyle.kt`, `BasemapStyles.kt` and their callers), and say which are in use on pre-main. "The entire map" is all of them unless you find a reason it can't be; report it.

## B. The comparison
- **B1** (2026-11-02, -1963). For every layer in each of our styles: its id, what it draws, its minzoom and maxzoom (and any zoom-dependent filter or visibility), and OSM's zoom range for the same thing, from OSM's source. Where OSM's range depends on size (way_area, rank, population) or on a tag, say so and give the rule rather than one number.
- **B2** (2026-11-02, -1963). Things one map draws and the other doesn't: list them both ways. Don't decide what to do with them.
- **B3** (2026-11-02, -1963). Labels and symbols too (place names, road names, shields, POIs, the tree, orchard and wetland patterns), not only fills and lines.

## C. What the change would need
- **C1** (2026-11-02, -1963). For each difference: whether a style edit alone does it, or whether our tiles don't carry the thing at OSM's zoom (the basemap archive's own zoom range per layer, and the offline downloads' stored zooms, z10 to 15 per known bug 14), so the map data would have to be rebuilt or re-downloaded. Say which, with the source.
- **C2** (2026-11-02, -1963). What else in the app reads these zooms: the night pattern switch, PatternRelayout, offline downloads, the trail overlay, anything with a zoom threshold in code. File and line.

**Output:** docs/audits/2026-11-02-osm-zoom-levels-comparison.md on a new branch `osm-zoom-levels` cut from origin/records-after-173, worktree ~/.cache/forager-wt/osm-zoom-levels. Push as you go. No PR. The four disclosure sections: confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope. Your final message: in plain language, how many layers differ and by how much, what needs new map data, and what is unknown.
