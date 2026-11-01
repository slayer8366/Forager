# Hill shading trial tiles (throwaway, never merge)

The owner's go for a small Trail Map hill shading trial (via Rowan, RECORD -1926). Built by Aspen,
2026-11-01, with `make-relief.py`.

- **The area:** Mount Hood and the Columbia Gorge, 45-46 N, 123-121 W.
- **The source:** Forager's 3″ height grid (USGS 3DEP 1″ averaged to 3″; NRCan MRDEM in Canada). No
  download.
- **The format:** `png256/{z}/{x}/{y}.png`, z5-z11, 256 px, Terrarium raster-dem
  (h = R*256 + G + B/256 - 32768 m). 156 tiles, 16.0 MB.
- **The URL** for a throwaway build: `https://raw.githubusercontent.com/slayer8366/Forager/hillshade-trial-tiles/png256/{z}/{x}/{y}.png`
- **Credit:** heights, USGS 3DEP; NRCan MRDEM (as in the app's Heights credits).

Delete this branch after the trial.
