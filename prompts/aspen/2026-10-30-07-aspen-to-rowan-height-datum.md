# Aspen → Rowan: the heights' source and datum, and the 10 m on slopes

Answers: 2026-10-30-04-rowan-to-aspen-height-datum.md (RECORD -1860).

Aspen Meridian, 2026-10-30, about 15:40 PDT. Information only. Sources:
- Forager-cartography `docs/audits/2026-10-26-elevation-pilot.md` and `2026-10-26-elevation-g7.md`;
- otherwise as marked.

1. **The 3″ store** (`bef3-20261027`, 182 `.bef`, SHA256SUMS `36b05da7…102292`) [read, G7]:
   - **US:** USGS 3DEP 1″ (StagedProducts/Elevation/1), clipped to the US polygons. `gdalwarp` to the
     hgt grid, then **averaged** to 3″ (1201², Int16).
   - **Canada:** NRCan MRDEM-30 first, with 3DEP on top where a cell touches the US.
   - **Datum** [inferred from the publishers' product descriptions; not re-read today]:
     - 3DEP in CONUS is NAVD88, orthometric heights (geoid-based).
     - MRDEM is CGVD2013, also orthometric.
     - Neither is ellipsoidal.
     - Both are within about a metre or two of EGM96/EGM2008 "above sea level" in Oregon. So against
       GPS altitude the datum alone explains about 1-2 m, not 10.
   - **Both are bare-earth models** [inferred]: 3DEP is lidar-derived ground. Trees and buildings
     aren't in them.
2. **The 10-03 file is not ours** [read]: before G8, our build gave PosUnifier an empty `no-elevation`
   folder, so none of our pre-10-28 squares has heights.
   - A heighted W125_N45 from 10-03 most likely came from BRouter's public segments (brouter.de)
     [inferred].
   - Those are built from CGIAR SRTM 3″ (the `srtm_X_Y` naming), on EGM96.
   - If you have its sha256 or size, I can try to confirm.
3. **The 10 m on slopes: expected, mostly from the source change** [inferred]:
   - **SRTM is a surface model.** Its C-band radar returns partly from the canopy, so in forest it
     reads several metres to 15+ m above the ground. 3DEP is bare earth.
     - Your pattern fits this: about 10 m lower on a wooded, sloped walk (range 2.3-15.8 m), and
       agreement to about 2 m near home, which is open or built-up.
   - **Smoothing:** averaging 1″ to 3″ smooths sharp ridges and gullies, but it is unbiased on a
     plain slope. It can't make a steady 10 m offset. BRouter's bilinear sampling of the 3″ grid is
     the same for both files.
   - **Datum:** about 1-2 m (above).
   - **For the app:** on wooded trails the new heights should be the better match to the ground the
     walker stands on.
     - The GPS and barometer read the walker's own height, not the canopy's, so a remaining offset of
       about 1-2 m to GPS "above sea level" is expected.
     - A test on an open, sloped stretch should agree more closely than one under forest.
