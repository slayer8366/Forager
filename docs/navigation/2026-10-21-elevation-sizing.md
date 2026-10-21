# Elevation for BRouter routing data: sizing, 2026-10-21

Read-only research by Juniper Sounding (researcher, Fable), filed by the planner (Rowan Waymark). It follows RECORD -1498, item 2: "Flat first, size elevation in parallel". This file summarises the report Juniper delivered by message, keeping its citations and its marks (read, computed, inferred, could not determine). Nothing was downloaded beyond metadata, listings and one-byte probes.

## Short answer
- **What BRouter reads.** BRouter 1.7.10's PosUnifier reads elevation only from BRouter's own `.bef` files, one per 5° block. Its converter makes them from 1° SRTM-style `.hgt` files (1″ or 3″); the direct hgt path is commented out (`PosUnifier.java:121-125`, read). The pipeline is source tiles → GDAL → `.hgt` → `ElevationRasterTileConverter` → `.bef` → one extra PosUnifier argument (`build-usca.sh:771-772`).
- **One public-domain source covers everything.** USGS 3DEP 1 arc-second is 3,783 one-degree COGs, 130 GB, on a public S3 bucket with no account (computed). It covers CONUS, Alaska (mostly 60 m resampled), Hawaii, Puerto Rico and the USVI, and holds tiles over almost all of Canada. The licence reads "All 3DEP products are public domain"; courtesy credit "U.S. Geological Survey" (read).
- **The licence caveat.** The provenance of 3DEP's Canadian tiles isn't stated (could not determine). The fallback for Canada is NRCan MRDEM: 84 GB as a single Lambert COG, Open Government Licence – Canada, plus a Copernicus WorldDEM-30 legal notice that must travel with it (read). CGIAR is ruled out: it stops at 60° N and forbids redistribution without permission. CDEM is legacy.
- **Memory.** Elevation doesn't touch the cutter that forced banding, so banding stays. PosUnifier grows from 321 MiB to about 1-1.5 GB at 1″, or about 0.4 GB at 3″ (inferred); both fit under `-Xmx4g`.
- **Disk and time.**
  - A one-off download of about 128 GB: about 2 days on the Pi's Wi-Fi, about a week on the tether. It is converted block by block, so staging stays under 1.5 GB.
  - A permanent `.bef` store: an estimated 2-6 GB at 3″ or 20-45 GB at 1″ (inferred, unmeasured; this is the first thing to measure).
  - Weekly builds gain minutes at 3″, or about 10-40 minutes at 1″.
  - GDAL isn't in the Pi's toolchain yet (`gdal-bin` from apt).
- **App side.** No code change for routing. The shipped `hiking-mountain.brf` has `consider_elevation = false` (line 13), so the app's copy of the profile must flip it; otherwise climbing costs stay off even with elevation in the data.
- **Walk-back time.** BRouter's result carries `filtered ascend`, `plain-ascend` and `total-time` (`FormatJson.java:33-37`). Its time uses Tobler's function at a model 6 km/h, not the walker's pace. Adding a climbing allowance from `ascend` to the app's own pace-based estimate (`ReturnWalkingTime.kt`) would be a separate domain change, and is the owner's decision.
- **1″ or 3″.** BRouter filters ascent at −5 m for 1″ and −10 m for 3″, and the profile ignores gradients under 3% with a 10 m buffer. 1″ matters on short steep features: switchbacks, gullies, bluffs. Recommendation: build 3″ first to prove the pipeline, keep the raw-to-hgt step at 1″ so a 1″ store can follow from the same downloads, and let the measured `.bef` sizes decide.
- **Effort: moderate.** It is all on the Pi side, plus one profile line.

## Biggest unknowns
1. The `.bef` size and converter time on the Pi. Converting one mountain block and one plains block would measure both.
2. The provenance of 3DEP's Canadian tiles. Either ask USGS, or use MRDEM for Canada.
3. The 128 GB one-off download over the Pi's link.
4. Datum seams at the border (NAVD88, CGVD2013 and EGM2008 differ by a few metres; the 10 m buffer should absorb this, inferred).
5. The converter turns exactly 0 m into "no data", which matters at coasts and the Great Lakes.

## ODbL (inferred, not legal advice)
Elevation is attached per OSM node inside the `.rd5`, which makes the routing data a Derivative Database under ODbL. With public-domain 3DEP that adds nothing to carry. With MRDEM or GLO-30, their notices must travel with the data; whether Copernicus's terms sit comfortably inside an ODbL redistribution needs someone qualified.

## Recommended pair
**US and Canada: 3DEP 1″**, with **MRDEM as the Canadian fallback** if USGS can't confirm the Canadian tiles are public domain.
