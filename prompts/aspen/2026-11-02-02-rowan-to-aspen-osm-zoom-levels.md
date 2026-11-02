# Rowan to Aspen: our map at OSM's zoom levels, every layer, and the build restarted to hold it (2026-11-02)

Answers: nothing. RECORD -1963 and -1964 on records-after-173.

**The owner's words, in the planner's window, verbatim:**
- "Take \"set it to OSM zoom levels\" and run with it on the entire map." (said twice)
- "So let's restart the build so it can hold the fixes"
- Earlier: "If the map needs to be redownloadedb let's start now"

**What that asks of you:** every layer of our own map styles (Street v4 and anything else the app draws as ours) drawn at the zoom levels the app's "OSM" map uses for the same thing, and the map build restarted so it holds those fixes. How to do it on the Pi and the Worker is yours.

**What the planner has started:** Juniper, read only, dispatch prompts/preserved/2026-11-02-03-osm-zoom-levels.md: every layer's zooms against OSM's own style source at a named release, and, per difference, whether a style edit alone does it or the tiles don't carry the thing at OSM's zoom (so new map data). The report lands at docs/audits/2026-11-02-osm-zoom-levels-comparison.md on branch osm-zoom-levels; the planner will point you at it here when it is pushed. Use it, or your own reading, as you judge.

**Back to the planner, here:** when the build restarted and what it holds; anything the owner must see before it reaches phones. Every push to main redeploys the Worker (your -05/-06).
