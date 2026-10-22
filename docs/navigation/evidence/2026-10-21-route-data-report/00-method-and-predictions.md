# Method and predictions, written before reading content

Dispatch: prompts/preserved/2026-10-21-06.md (records-after-173 at 36a2fc396).
Author: Juniper Sounding (researcher). Written 2026-10-21 before any file body,
BRouter source, or size figure was read. Only `git grep -l` / `git ls-tree`
listings had been run to learn which files exist.

## Bases, verified against the remote at start

- `origin/pre-main` = 06637240754c1ec69c185cec79a8c370e8bbcf11 (matches dispatch).
- `origin/pi-build` = 961a90d363498d7fb5e519e4ad25a3513707e247 (matches dispatch).
- `origin/records-after-173` = 36a2fc396 (dispatch said a97e890c; the move is the
  dispatch's own commit, -1541, so no question is changed).

## Method

1. **App (Q1).** Read, at pre-main, the offline-map repository interface
   (`domain/OfflineMapRepository.kt`), its MapLibre implementation
   (`map/MapLibreOfflineMapRepository.kt`, `MapLibreOfflineRegionMetadata.kt`,
   `OfflineRegionReconciliation.kt`), the Room entity/DAO, the Availability
   view-model and delete path, and the offline style served by the Worker
   (`server/pmtiles-worker/src/offline-style.json`). Trace: what URL is fetched,
   what SDK stores it and where on disk, how the displayed size is computed,
   and the delete path. Cite `file:line` at 06637240.
2. **Pi (Q2).** Read, at pi-build, `map-build/README.md`, `pins.env`,
   `bin/fetch-brouter.sh`, `bin/build-orwa.sh`, `bin/build-usca.sh`, and the
   audits that record built outputs (`2026-10-19-usca-routing-bands-report.md`,
   `2026-10-20-cloudflare-upload-prep.md`, `2026-10-20-pi-09-disk-fit...`).
   Sizes come only from numbers those documents record from real runs; I do
   not run the Pi and cannot measure files on it. Each size is marked "read
   from <doc>:<line>".
3. **BRouter (Q3).** Fetch BRouter's own source and docs at tag v1.7.10 from
   its public repository, read-only, bounded (single files via the raw
   endpoint, named user agent, no clone of history). Files to read: the
   segment-file naming/lookup code (`RoutingContext`/`NodesCache`/
   `OsmFile`/`PhysicalFile`), the `Rd5` writer in the map-creator
   (`WayLinker`/`RD5` tile layout), and `docs/` on building segments.
   Question to settle at source: is the 5-degree square a hard assumption,
   and what sub-tiling exists inside an rd5 (BRouter rd5 is known to have
   internal sub-tiles; confirm the grain).
4. **Sizes (Q4).** Choose two or three area sizes the app actually allows
   (read the app's limits in Q1 first, do not assume). Compute route-data
   size per option from: (a) the recorded rd5 sizes for the PNW squares on
   pi-build; (b) the internal sub-tile grain confirmed in Q3, scaled by
   the area's fraction of a square, labelled computed and with the
   assumption (uniform density) stated. No file is rebuilt or downloaded in
   bulk. If a public rd5 size must be checked, read a directory listing
   (HTTP HEAD or index page), never the file.
5. **Publishing (Q5).** Read `server/pmtiles-worker/src/index.ts`,
   `shared.ts`, `wrangler.toml`, README; describe what the Worker serves,
   how it authenticates/limits, and what a route-data path would add. Read
   only; no deploy.
6. **Credits (Q6).** Quote ODbL attribution requirement from the OSM
   copyright page and the OSMF attribution guidelines; quote BRouter's
   LICENSE at v1.7.10. Both with URL. Readings marked "inferred, not legal
   advice".

Every claim is marked **read** (file:line or URL), **computed** (script or
arithmetic shown in this folder) or **inferred**.

## Predictions (mine, before reading)

- P1. The app's offline download uses MapLibre's `OfflineManager` with a
  tile-pyramid region definition against the Worker's offline style; the
  size shown is `OfflineRegionStatus.completedResourceSize` (bytes
  downloaded), not a pre-download estimate. Storage is MapLibre's own
  SQLite cache under the app's files dir. Deletion is
  `OfflineRegion.delete`. No field exists for route data.
- P2. The Pi builds rd5 files with BRouter's map-creator from the same
  OSM extract as the tiles, as 5x5-degree files; they are kept on the Pi
  beside the tiles and are *not* uploaded (the planner's premise). I
  expect the PNW squares (W125_N45, W120_N45, W125_N40, W120_N40) to be
  between 20 MB and 120 MB each.
- P3. BRouter's lookup hard-codes 5-degree files by name
  (`W125_N45.rd5` style) but each rd5 is internally divided into sub-tiles
  (I recall 32 x 32 of 1/32 degree, i.e. ~3.5 km); the router loads only
  the sub-tiles it touches. So a *smaller file* is not what BRouter
  reads, but a *sparse* rd5 (one with only the sub-tiles for an area
  populated) might be loadable. Whether the writer can produce that, or
  the format tolerates missing sub-tiles, is the thing to verify; I
  predict the format allows empty sub-tiles (it must, for ocean) so a
  pre-cut could work, but no tool in v1.7.10 does the cut.
- P4. A typical user area (say 20 km x 20 km) is well under 1 MB of
  route data if cut at sub-tile grain; a whole square is tens of MB. So
  the planner's "about 1 MB" is plausible for a cut area and "tens of MB"
  right for a square.
- P5. The Worker serves PMTiles range requests from R2; adding rd5 (or
  cut pieces) would be a second object type with its own path, served
  whole or by range, with the same cache rules.
- P6. ODbL requires "© OpenStreetMap contributors" and a link to the
  licence; BRouter is MIT and requires the copyright notice in the
  distribution.

If a result contradicts a prediction, the report says so beside the
result rather than adjusting the prediction.
