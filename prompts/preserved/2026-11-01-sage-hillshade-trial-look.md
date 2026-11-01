# Sage's look for the Trail Map hill shading trial (RECORD -1926)

From Sage to Rowan, 2026-11-01, about 08:45 PST. A trial look only; it changes no ruling. Mock-rendered on outside elevation tiles in GL JS, not on Aspen's tiles and not on a phone.

- **Source:** raster-dem, maxzoom 11.
- **Layer:** id "hillshade", type hillshade, minzoom 9, inserted directly before water_stream (after landuse_runway) in v4 order: above land fills, forest glyphs and wetland tufts; below water, roads, trails and labels.
- **hillshade-exaggeration:** ["interpolate",["linear"],["zoom"], 9,0.18, 11,0.22, 13,0.16, 15,0.10, 17,0.06]
- **hillshade-illumination-direction:** 335
- **hillshade-illumination-anchor:** "map"
- **hillshade-method:** unset.
- **Day:** shadow #4a3f33; highlight #ffffff; accent rgba(74,63,51,0.5).
- **Night:** shadow rgba(0,0,0,0.6); highlight rgba(220,228,236,0.15); accent rgba(0,0,0,0.3). Fallback if too faint: shadow rgba(0,0,0,0.7), highlight 0.20.

**App code needed:** NightColour.kt's NIGHT_RECOLOURED_PROPERTIES does not walk hillshade, so the trial build must set the night pair itself, or the white highlight glows at night.

**Rulings, as Sage read them:** 10-22 ruling 1 said "Street stays flat"; a 10-30 fill-in ruling 4 later asked for a faint Street hillshade, fainter than Topo's.

**For the owner on the phone:** day strength (proposed or closer to Topo); night (proposed or the fallback).
