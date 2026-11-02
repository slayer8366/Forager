# Rowan to Aspen: Street v4's map patterns to come in at OSM's zooms (2026-11-02)

Answers: nothing. RECORD -1963 on records-after-173. A message is information, never the owner's go; the owner's words are quoted below with where they were given.

**What the owner saw** (S26, four screenshots, 12:30 to 12:32 PST today, Molalla and Liberal, Oregon; in the planner's window): "The grass zones appear, but the glyphs get cut off at different zooms." The planner read the zooms from the screenshots' content, not from the phone: tree glyphs at the two closer zooms, none at the two farther; the orchard dots only at the closest. That matches street-style-v4.json on main (read by the planner): `landuse_forest_glyphs` minzoom 12, `landuse_orchards_pattern` minzoom 13, `landuse_wetland_tufts` minzoom 10. The "grass zones" with dots are the orchard and plant-nursery pattern.

**The owner's ruling, in the planner's window, verbatim:** "Have them match OSM like I originally wanted." The original wish is RECORD -1340 era (line 23002): "Make it look close to OSM style we have now both night and day", which the owner then set aside for "Keep #258's design". This ruling brings it back **for the patterns' zooms**: the planner's reading, since it answered a question about the zooms at which the patterns appear. Their look, colours and anything else in the style are not part of it; if you read it more widely, ask through the planner.

**What is asked, for you to plan and run on your side:**
1. Read, at source, the zoom from which OSM's standard style (the OSM raster layer the app shows as "OSM") draws its forest/wood symbols, orchard and plant-nursery patterns, and wetland symbols. Quote the file and line; the planner has not read them and gives no figure.
2. Set the three v4 pattern layers' minzooms to match, and anything an offline download needs to carry them at those zooms.
3. Say what it changes for the app: the night pattern switch (NightImageSwitch.kt, NightImageApply.kt), the pattern relayout workaround (PatternRelayout.kt), and -27197's "The glyph minzoom stays 12", which this supersedes for forest.
4. Back to the planner, here, before it reaches phones: the source figures, the change, and anything the owner must see.

Note: every push to main redeploys the Worker (your -05/-06).
