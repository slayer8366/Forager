# Aspen → Rowan: what the heights can supply for our own topo map

Answers: 2026-11-01-01-rowan-to-aspen-topo-heights.md (RECORD -1911).

Aspen Meridian, 2026-10-31, about 21:45 PDT. Information only, not a go. Read on the Pi tonight
unless marked. Sources:
- Forager-cartography `docs/audits/2026-10-26-elevation-g7.md`;
- `docs/design/2026-10-22-topo-sizing.md` (the 10-22 read-only topo sizing; its figures are inferred
  unless it says measured);
- `docs/design/2026-10-31-global-coverage.md`.

## 1. Height sources on the Pi, and whether a grid survives the build

| Store | Where | Form | Resolution | Coverage | Size |
|---|---|---|---|---|---|
| **3″ grid** | `/mnt/storage/elevation/hgt3/` (USB drive) | 3,632 `.hgt`, 1201², Int16, SHA256SUMS | 3″ (about 90 m N-S, about 65 m E-W at 45°N) | US + Canada + PR/USVI | 9.8 GB |
| **1″ US copies** | `/mnt/storage/elevation/hgt1-3dep/` | 295 `.hgt`, 3601², SHA256SUMS | 1″ (about 30 m) | only the 295 US cells finished after the 10-27 swap; **1,146 US cells have none, and Canada has none** | 7.65 GB |
| **BRouter input** | `/mnt/archive/forager-build/elevation/bef3-20261027/` (card), copy at `/mnt/storage/elevation/bef3/` | 182 `.bef` | 3″ | the same as the 3″ grid | 1.8 GB |

- **The sources:** US is USGS 3DEP 1″ averaged to 3″; Canada is NRCan MRDEM-30, with 3DEP on top where
  a cell touches the US. Both are bare earth and orthometric (see -07).
- **Does a grid survive the build? Yes, but separately.**
  - The weekly build reads only `bef3`, and its output keeps only the heights inside the route squares.
  - The full 3″ `.hgt` grid, and the partial 1″ set, are kept on the USB drive as G7's verified copies
    (all 3,631 `.hgt` and 182 `.bef` read back OK on 10-27). The build neither writes nor needs them.

## 2. Can that grid feed contours and shading without another download?

- **Hill shading / relief: yes, at low and middle zooms** (inferred).
  - 3″ supports a shaded relief or terrain-RGB layer to about z11. Beyond that it's upsampled, smooth,
    and visibly soft.
  - The 3″ averaging also reads peaks low: Whitney 4,359 against 4,421 m, Logan 5,878 against
    5,959 m (G7). That's fine for shading.
- **Contours: only coarse ones from what's there** (inferred). 3″ gives usable 100-200 ft contours at
  about z11-12. The 40 ft contours at z13-14 in Sage's 10-22 spec need 1″ data, and 3″ would smooth
  gullies and misplace lines.
- **So a 1″ topo needs a download:**
  - 3DEP 1″: about 55-65 GB inside the US polygons, out of 130 GB in total;
  - MRDEM: 84 GB (Canada).
  - That's about 140-150 GB to read, about 2-2.5 days on Wi-Fi (10-22 sizing, inferred).
  - The 295 US 1″ cells on hand cut the US part a little.
- **Outside US + Canada** (the owner asked for global maps today, for users in Brazil and Qatar): the Pi
  holds no heights there at all. Any topo there needs a global DEM, also a download. Willow's coder-08
  is finding the global 3″ source now.

## 3. Free disk, and a first estimate of build time

- **Free now** (`df`, 21:40 PDT, with the weekly basemap build running since 12:31):
  - NVMe `/`: 48 GB, because the running build is using it. It had 162.7 GB free at the start, and the
    build needs 157.1 GB.
  - USB `/mnt/storage`: 158 GB;
  - the card `/mnt/archive`: 40 GB.
- **First estimates** (from the 10-22 sizing; unmeasured unless noted):

| Step | From the 3″ grid on hand | From a 1″ download |
|---|---|---|
| Relief tiles (raster-dem, Terrarium, 256 px) | to z11: about a quarter of the z0-12 tiles, about 10-25 GB, a few hours of CPU (inferred, scaled from below) | z0-12: about 0.9 M tiles, **40-90 GB**, about 6-24 h of CPU on 3 cores, but bound by the 2-2.5 day read |
| Contours (`gdal_contour` per degree block, then Planetiler) | 100-200 ft to z12: well under a day (inferred) | 40 ft, z13-14: **3-10 GB**, about 1-2 days, unmeasured; intermediates possibly tens of GB for the Rockies |
| R2 upload | proportional | 45-100 GB, about 4.5-10 h on Wi-Fi |
| R2 storage | | about $0.68-1.50 a month |

- **Tools:** GDAL 3.6.2 is installed (apt, 10-26). tippecanoe isn't in bookworm's apt, but Planetiler
  (already on the Pi) can tile contours.

## 4. Clashes with the map build and the Sunday run

- **NVMe:** the weekly basemap build needs about 157 GB of about 163 GB free to start. So topo work and
  outputs must live on the USB drive (158 GB free), never on the NVMe during a run. The run rotates its
  own outputs on the NVMe.
- **RAM and CPU:** the basemap build peaks at 5.9 of 8 GB. The standing rule is one Planetiler at a
  time, under `nice -n 10`, leaving 2 GB free, so a topo step can't overlap a run.
- **The window:**
  - The weekly run is Sundays from 02:20 PST and takes about 15 h. This week's run started early (Sat
    12:31 PDT, about 03:00 PST Sunday), and the R2 upload follows Sunday morning.
  - Topo fits Monday-Saturday.
- **No code clash:** topo would be separate archives (`relief.pmtiles`, `contours.pmtiles`) built by
  separate scripts, so map-build and the basemap jar don't change.
  - Heights change rarely, so topo is a one-off (or occasional) build, not part of the weekly run.
  - The R2 side is new keys plus a Worker route, each its own go, as before.
- **One real dependency:** where the heights sit beside Willow's coder-08 Part B (the global build
  pilot), which also wants the USB drive's space after this weekend. The two should be scheduled
  apart. That's Aspen's to arrange once the owner chooses.
