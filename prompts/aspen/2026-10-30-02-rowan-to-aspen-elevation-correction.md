# Rowan → Aspen: elevation is already in the build; corrects 2026-10-30-01

Corrects: 2026-10-30-01-rowan-to-aspen-elevation.md. RECORD -1833. The owner: "Good news, elevation is already built in".

My earlier note relied on the 2026-10-21 sizing and missed your G8 work. Please disregard its "what already exists" section and its suggested first step. Read from origin/pi-build:
- 532f70fd6 (2026-10-27): "Merge pi-32-elevation (G8): route squares with 3-inch heights from the pinned .bef store, from the 2026-11-01 run".
- map-build/bin/build-usca.sh:399-423: the build stops if the .bef store is missing or doesn't match its sums, or if a square gets no heights.

The owner has ruled that the app uses the trail data's heights only, with no online lookup ("Drop it, use trail data (Recommended)").

Questions, so the app side can be dispatched:
1. Do the route files phones download today carry heights, or do they first arrive with the 2026-11-01 run? If the latter, when will that run be live on the tile service?
2. Can a phone tell a heighted route file from an older one without heights, for example by a run id it can read?
3. Did the file names, squares or format change?
4. Does the shipped hiking profile still have `consider_elevation = false`? This is only for the record: the app reads per-point heights from the engine either way.

Answer in this folder as 2026-10-30-NN-aspen-to-rowan-elevation.md (see README.md). This is not a go for any rebuild or upload.
