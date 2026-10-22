# Own topo plan: method and predictions, written before any outside data was read

Dispatch 2026-10-21-08 (RECORD 2026-09-28-1552). Researcher: Juniper Sounding.
Bases verified against the remote at the start: pre-main 01c7c5b5, pi-build 961a90d3,
records-after-173 0885670c0 (the dispatch's intent commit; the intent text named dbf69daa,
an earlier commit of the same branch).

Repository reading done before this commit (all read-only): the dispatch, the researcher
profile, CLAUDE.md, the elevation sizing (docs/navigation/2026-10-21-elevation-sizing.md),
the plans of record, RECORD -454, -1291 and -1552, and at pre-main `Basemap.kt`,
`BasemapStyles.kt`, `OfflineStyle.kt`, `StreetStyle.kt`, `OfflineMapRepository.kt`,
`DistanceUnit.kt`, `libs.versions.toml`; at pi-build `map-build/README.md`, `pins.env`,
`build-usca.sh` (grep and sections), `install-toolchain.sh`, the two usca audit reports.
No web page, listing or catalogue had been read when this file was committed.

## Marks
Every claim in the report is marked **read** (a page, file or response, cited), **computed**
(by a script in this folder, with its output beside it) or **inferred**. Figures with none of
the three are errors.

## What will be read outside the repository (small, bounded, no downloads)
1. The map library at the pinned version (MapLibre Native Android 13.5.0): the style spec's
   `raster-dem` source and `hillshade` layer, the encodings it accepts, `hillshade-method`
   values, and whether any contour generation exists on Android (the contour plugin is
   believed JS-only: to confirm).
2. Tools, each at its current pinned release, with licence text read at source: GDAL
   (`gdal_contour`, `gdaldem`, `gdalwarp`, `gdal2tiles`), tippecanoe (felt fork),
   go-pmtiles, rio-rgbify, Planetiler's ability to read vector sources, and what Debian
   bookworm's apt carries for arm64 (the Pi is bookworm: `pins.env` says it has no Java 21).
3. USGS 3DEP: the public S3 bucket listings for the 1 arc-second (`.../Elevation/1/TIFF/current/`)
   and 1/3 arc-second (`.../Elevation/13/TIFF/current/`) products, by `ListObjectsV2` XML
   pages (about 1,000 keys per page, a few hundred KB each): key names and sizes only.
   The 1-arc-second count and total are re-derived rather than quoted from the earlier report.
4. NRCan MRDEM and the Canadian provenance question, re-read at source.
5. Published sizes of comparable terrain tilesets (terrain-RGB/Terrarium planet archives,
   contour tilesets), to check the computed figures against something outside them.
6. USGS's credit wording and NRCan's licence wording, quoted.

## How sizes will be computed (scripts in this folder)
- `s3_list.py`: pages the two S3 prefixes, writes `s3-1arcsec.csv` and `s3-13arcsec.csv`
  (key, size) and totals. It downloads nothing but the listing XML.
- `tiles.py`: from the 1-arc-second key names (n{lat}w{lon}, one-degree cells), computes the
  exact set of Web Mercator tiles at each zoom that intersect those cells (the union over
  cells, so no double counting), for the US+Canada footprint. This stands in for a land
  polygon: it is the footprint of the data itself, so it also counts ocean within a coastal
  cell (an overcount, stated).
- `sizes.py`: multiplies tile counts by per-tile byte models, each model's source stated:
  - elevation tiles (Terrarium PNG, 256 and 512 px): bytes per tile from published archive
    sizes if found, else from the PNG model (an 8-bit-per-channel RGB PNG of smooth terrain
    compresses to roughly 25-45% of raw; raw 256 px RGB = 196,608 bytes);
  - pre-shaded tiles: bytes per 256 px tile for PNG and JPEG hillshade (published figures if
    found, else a stated model);
  - contours: contour length per unit area from the Wentworth relation (length density =
    mean slope / contour interval) with mean-slope figures from published sources, then MVT
    bytes per metre of line at each zoom (vertex spacing of about 2 px after simplification,
    about 2-3 bytes per vertex zig-zag-encoded, plus per-feature overhead). The two intervals
    are 40 ft (12.192 m) and 20 m. Index lines every fifth add tags, not geometry.
  - the per-offline-area extra: the app's region is a bounding box of radius r at zooms 10 to
    15 (`OfflineMapRepository.MIN_ZOOM`/`MAX_ZOOM`, read), at r = 8 km (miles default),
    10 km (km default) and 24 km (`MAX_RADIUS_KM`), at a reference latitude of 45 N.
  All models carry a low and a high figure; the report quotes both.
- Pi time and memory: per-tile GDAL timings are not measurable here (no downloads, no Pi).
  They will be estimated from published per-tile figures where found, else stated as
  "could not determine" with the one measurement that would settle them named.

## Predictions, before data
P1. MapLibre Android 13.5.0 draws hillshade on the phone from a `raster-dem` source
    (Terrarium and Mapbox encodings) and has no contour generation; contours must be vector
    tiles built ahead.
P2. 3DEP 1/3 arc-second current: 1,000 to 1,400 one-degree COGs, 300 to 600 GB in total;
    1 arc-second current: about 3,780 COGs, 125 to 135 GB (the earlier report's 130 GB).
P3. US and Canada, server side: contours at 40 ft or 20 m as vector tiles to zoom 14, 10 to
    40 GB; elevation tiles to zoom 12 at 512 px, 15 to 60 GB; pre-shaded image tiles to zoom 14,
    100 to 400 GB. So the planner's "smallest pair" prediction holds, and "tens of GB" holds for
    the pair but not for pre-shaded images.
P4. Per offline area, zooms 10 to 15: at the 8 km default, contours add 1 to 6 MB and
    elevation tiles (to 12 only) under 2 MB; at 24 km, contours 10 to 50 MB and elevation
    tiles 2 to 10 MB. The planner's "a few MB more per offline area" holds at the default and
    not at the maximum.
P5. One 1-arc-second download serves routing, contours and shading. 1 arc-second (about 30 m
    posts) is fine for hillshade at every zoom the app serves and for contours to zoom 14;
    1/3 arc-second (10 m) shows at zoom 15 and in low-relief ground, at 3 to 5 times the
    download.
P6. GDAL comes from bookworm's apt (3.6.x) and runs per one-degree tile within 8 GB;
    tippecanoe needs a source build on the Pi and must run per band (as routing does) to fit
    8 GB. A first build is days of Pi time, dominated by the 128 GB download and tippecanoe;
    elevation changes rarely enough that the topo is built once and refreshed yearly at most,
    not weekly.
P7. USGS asks for a courtesy credit ("U.S. Geological Survey") and nothing binding; MRDEM's
    Open Government Licence - Canada requires an attribution statement and carries a
    Copernicus notice; GDAL (MIT), tippecanoe (BSD-2), go-pmtiles (BSD-3) and rio-rgbify
    (MIT) need their notices in the app's licence screen only if their code ships in the app,
    which it does not.
