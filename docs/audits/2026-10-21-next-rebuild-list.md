# The next-rebuild list

Kept by Aspen Meridian, the Pi planner; written and committed by Pi coders. Each item says what the
next US + Canada build should change, who ruled it and where. An item leaves the list by a dated
note saying which build carried it, never by deletion. Started 2026-10-21 by dispatch pi-22.

## 1. Four regions: Puerto Rico and the USVI (pending since 2026-10-20)

Installed as regions by pi-16 with no rebuild ("Install only, rebuild later"). The next build is a
full four-region rebuild (`README.md`'s pi-16 row; `2026-10-19-usca-routing-bands-report.md`,
fifteenth addendum).

## 2. Match the topo map's zooms for tracks and paths (added 2026-10-21)

**The owner, verbatim:** "Whatever the topo map is set to. Let's match that for zoom level on names
and paths"; after the phone re-check, "Add to list (Recommended)". This supersedes Rowan's RECORD
-1507 target of z12 for names and paths (on `records-after-173`) for what the Pi builds.

**Topo's levels, read** from OpenTopoMap's published Mapnik style (der-stefan/OpenTopoMap master:
`styles-otm/roads-casing.xml`, `road-names-text.xml`, zoom entities in `inc/entities.xml.inc`; the
live OTM server's version is not verified): tracks from tile z12, `path` from z13, footway, steps and
bridleway from z14, major-road names from z15, all other names from z16. MapLibre fetches raster
tile zoom `round(mapZoom + 1)` for a 256 px source (`RasterShaderModel.kt:54-55`, pre-main;
correction by Rowan, accepted), so on screen topo shows tracks from about map zoom 11.5, paths
12.5, footways 13.5, major names 14.5, minor names 15.5.

**Street, live 2026-10-21** (after the -1506 Worker paste): tracks 12, paths 13, major names 11,
minor names 14. Street is half a zoom behind topo for tracks and paths, and ahead for footways and
all names. **The data floor:** tracks first appear at tile z12 and paths at z13 (Hazel's read of
Roads.java at `BASEMAPS_COMMIT ca93fc06`, relayed by Rowan; confirmed by the planner on
`out-usca/20261017T202051Z`: Gifford Pinchot NF, `kind_detail=track` z10 0, z11 0, z12 5; Paradise
z10–12 no `kind=path` at all). So the style cannot close the gap alone.

**Observed on the owner's S26,** four screenshots at 01:52–01:53 PDT on 2026-10-21: at one zoom topo draws the
forest tracks and Street draws none; a little closer in, Street's tracks appear and line up with
topo's. The planner's estimate of the step, from feature sizes, is about half a zoom.

**The change for the next build:** tracks in the tiles from z11 and paths from z12. Footways and
names stay as they are, since Street already leads topo there. The app style then follows (tracks
about 11.5, paths about 12.5), through Rowan.

**Before any upload:** measure the tile-size growth and label density against the 261017 archive.
If the cost is large, bring it to the owner first (Rowan's relay; the option's own words).

## 3. Elevation for routing (filed for later)

Sizing by Juniper, filed by Rowan: `docs/navigation/2026-10-21-elevation-sizing.md` on
`records-after-173` (`0ef16527a`). Not ruled for any build yet.
