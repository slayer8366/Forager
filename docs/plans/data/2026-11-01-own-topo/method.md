# Own topo plan: method and predictions, before outside data

Dispatch 2026-11-01-01 (RECORD -1911). Researcher: Juniper Sounding. Written 2026-11-01 UTC, before any
web page, listing or catalogue was read in this run. Builds on the 10-21 method notes
(origin/topo-plan-report, docs/plans/data/2026-10-21-own-topo/method.md), which are kept as they stand.

Bases verified on the remote at start: pre-main d54189479, pi-build de30a84ee, records-after-173 512d90322.
Also read: Forager-cartography main 970f7c9 (public to this account; not named in the dispatch).
Repository reading before this commit: the dispatch, the 10-21 dispatch and method, the Aspen messages,
Basemap.kt, BasemapStyles.kt, MapMode.kt, StreetStyle.kt, OfflineStyle.kt, part of the offline repository,
pins.env, cartography's topo rulings and topo sizing (10-22), elevation G7 and G8 audits.

## Marks
Every claim is **read** (file:line, or URL), **computed** (script here, output beside it) or **inferred**.

## Outside reading (small, bounded; no dataset, no bulk pull, no account)
1. Map library at 13.5.0: style spec raster-dem, hillshade, encodings, hillshade-method, contour support.
2. Tool licences and versions: GDAL, tippecanoe, Planetiler contour input, go-pmtiles.
3. 3DEP listings: S3 XML listing pages only, for the 1 and 1/3 arc-second prefixes.
4. Published sizes of comparable terrain tilesets, to test the computed figures.
5. Licence words: USGS credit, OGL-Canada, MRDEM product page, Copernicus notice; OpenTopoMap terms.

## Scripts
- `tiles.py`: exact tile counts by zoom for the data footprint, and for one offline area (no network).
- `sizes.py`: byte models, each low and high with source stated.

## Predictions (before data)
P1. 13.5.0 has `raster-dem` + `hillshade` from Terrarium and Mapbox encodings, and draws no contours
    from a DEM on Android. Contours must be vector tiles built ahead.
P2. The Pi's 3" hgt store (10.5 GB) can feed hillshade to about z10-z11 of real detail and contours only
    coarsely; at 90 m, 40 ft contours in steep ground will look blocky at z14. A 1" pull of the 1,146 US
    cells without a kept copy, plus MRDEM-30 for Canada, is needed for the design as ruled.
P3. Relief to z12 at 256 px for US + Canada is 15-90 GB; contours z12-14 are 5-40 GB. Topo is tens of GB,
    which fits R2 cost-wise and is a disk and time problem on the Pi.
P4. One 8 km offline area gains under 10 MB; a 24 km area under 60 MB.
P5. The app work is the smaller part; the build pipeline and first measurements hold it. An honest range
    is 2-5 weeks, with hard unknowns in: phone rendering of hillshade, offline store handling a raster-dem
    source, and contour labels in the offline style (glyph-layer crash risk, read in the offline repository doc).
P6. Credits: USGS courtesy line; OGL-Canada statement; Copernicus notice where MRDEM is used.
