# Dispatch 2026-10-27-03: night tree glyphs vanish at full zoom, diagnose from code (a coder, Opus; report only)

Written by Rowan Waymark, 2026-10-27, against pre-main 6064fef86. RECORD -1730.

**Authorisation.** The owner, on the S22 this morning, verbatim: "Also, in night mode, the tree glyphs disappear when all the way zoomed in, but reappear when full zoom is broken." The owner: "Diagnose from code first (Recommended)". Build nothing.

## What was seen (Aspen, from five owner screenshots, which stay off GitHub)
- **Day:** glyphs show at full zoom.
- **Night, full zoom:** none.
- **Night, slightly zoomed out:** they show (10T ER 28691 19251).

## The style (Aspen's reading; a premise to re-check)
- `landuse_forest_glyphs` has minzoom 12 and no maxzoom.
- The only night rule naming its image is forager:night-patterns `{"tree_dark_irregular": "#5d804f"}`.
- So nothing in the style stops the glyphs at any zoom, which points at the app's night image path (NightImageSwitch.kt / NightImageApply.kt: a bitmap added at runtime, with fill-pattern switched to its name).

## Hypotheses (Aspen's, none confirmed)
1. MapLibre cross-fades fill-pattern between z and z+1, and at the integer maximum zoom the runtime night image doesn't resolve.
2. The added bitmap's pixel ratio or size.
3. Something re-applies the switch on a zoom change.

## Do
- **Trace the code.** Follow the night pattern path from the code: where the night image is made and added, how fill-pattern is set (a constant or an expression), which zoom events touch it, and the map's max zoom.
- **Hypothesis 1:** read MapLibre Native 13.5.0's pattern handling at an integer max zoom on GitHub (the pinned version, gradle/libs.versions.toml). Is the pattern resolved for zoom and zoom+1? What happens when one of the two images is missing?
- **Test what a JVM can show,** for example the layer's fill-pattern value after the night switch, at the cap and below it. Say what is device-only.
- **The verdict:** say which hypothesis the code supports, and give the cheapest fix with a test that fails before and passes after. Do not build the fix.
- **The S22 reproduction:** propose steps for the owner's next S22 session, as tap > shows > Back, with what to log (camera zoom; the layer's fill-pattern; whether style.getImage of the night name exists at the cap and just below).

## Output
- Write the report to docs/audits/2026-10-27-night-glyph-zoom-report.md on a new branch, night-glyph-zoom-report, and push it.
- Include the four disclosure sections.
- No Gradle builds beyond CI, if a test is worth running, and no device.
- **Network:** none beyond GitHub.
- **The Restricted Object:** never read, name or quote it.
