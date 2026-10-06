# Dispatch: T4, the master grid and one layer end to end (SoilGrids pH, western Washington, to PMTiles)

**Verify first and report by message; then build unless something is a stop. One small SoilGrids fetch, only after the D26 GBIF download has finished fetching. No GBIF request, no climate pull, no model fit, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), on the owner's "Yes, write the T4 dispatch", against forager-forecast `origin/main` at `ad64fef`. This dispatch is the first commit on branch `t4-master-grid`. Every cite below is a premise to re-check.

**Writer (D38):** a coder session on the credentials machine (this laptop), on `t4-master-grid`, worktree `~/Zynergy/forager-forecast-t4`. No other session writes to this branch. **Review (D18):** a separate session reviews the finished branch under `docs/dispatch/2026-09-18-review-protocol.md`. **Merge (D40):** only on the owner's written authorisation naming the branch.

**Running alongside D26.** The D26 coder is on `d26-shared-download` in `~/Zynergy/forager-forecast-d26`. Nothing here should touch `records/`; if you find you need it, stop and ask. **Network:** your SoilGrids fetch waits until the D26 download has finished fetching (the planner tells you), so that only one download runs at a time. **Disk:** about 5.8 GB was free before D26's zip landed. Keep T4's data small and say what it takes.

## Why

The owner, in the Forager planner session, asked what stands between now and the forecast as a map layer in the app, then said: "Yes, write the T4 dispatch". T4 is the first task of the model-building stage, and the first that produces something the app's map can open. It is defined in `docs/planning/TASKS.md` (the T4 block, about `:49-53`):

> **T4. Master grid and one layer, end to end** · Depends on: T3 · Does: define the 250 m ESRI:102008 grid, bring SoilGrids pH onto it for western Washington, warp to Web Mercator and write a PMTiles archive. · Verify: the archive opens in the PMTiles viewer with max zoom 9, and ten checked cells match the source within rounding. · Device-only: no

T3 is done (TASKS.md, T3 row). The rules it rests on:
- **D8:** compute in North America Albers equal-area (ESRI:102008); reproject only finished rasters to Web Mercator for tiles.
- **D6:** deliver with PMTiles and MapLibre on Cloudflare (the Cloudflare setup is not part of this task).
- **SPEC.md:** "static habitat layers on a 250 m equal-area grid" (about `:15`), and "Tiles stop at zoom 9. At 45 N that is about 216 m per pixel, which matches 250 m cells" (about `:48`).
- **R8 and D12:** only the weather-cell value carries a percent and the label "sighting chance"; the 250 m raster does not (SPEC, about `:78`). This layer is soil pH, so it carries no chance wording at all.
- **D58:** no forbidden term ("fruiting probability", "probability of finding", "chance of finding") in anything that could reach the Forager repo.
- **DATA_REGISTER:** SoilGrids 2.0, soil pH with 90% intervals, global, 250 m, CC BY 4.0, Verified.

## What must be true when this is done

1. **The master grid is defined once, in code**: ESRI:102008, 250 m cells, with an origin and an extent rule that any later layer (T5's host trees, T7's habitat) snaps to. A cell id that is stable across runs. **Propose the origin and the cell id scheme before building.** Every later layer inherits them, so they are the main decision in this task.
2. **SoilGrids pH on that grid for western Washington**, from the primary source, with its exact request stored beside the data (as D52 does for climate pulls) and the CC BY 4.0 attribution text recorded verbatim from the source.
3. **Warped to Web Mercator** (EPSG:3857) only after it is finished on the master grid (D8), then **written as a PMTiles archive with max zoom 9**.
4. **The Verify from TASKS.md**: the archive opens in the PMTiles viewer (https://pmtiles.io or the `pmtiles` CLI's `show`), reporting max zoom 9; and **ten checked cells** match the source within rounding. Choose the ten before you look at the values, spread across the extent and including at least one coastal or water-edge cell and one high-elevation cell, and say how you chose them.

## Verify first, report by message

1. **Base:** fetch; `origin/main` is still `ad64fef`. If it moved, re-check every cite there.
2. **Every cite above**, confirmed or corrected with file:line.
3. **The SoilGrids product, choices the code will make.** Propose each with a reason and a source; the planner brings any real trade-off to the owner.
   - **Access route:** ISRIC's WebDAV VRTs, the WCS, or another route ISRIC documents. Cite ISRIC's page. Note that SoilGrids' native projection is Homolosine, so it gets warped once to ESRI:102008, never twice.
   - **Depth:** SoilGrids publishes pH for 0-5, 5-15, 15-30, 30-60, 60-100 and 100-200 cm. **Which depth (or depth-weighted combination) does the habitat model want?** Check SPEC.md, the RESEARCH_LOG and EVIDENCE.md for anything already decided. If nothing decides it, **stop**: this is a modelling choice the owner rules on, not the builder.
   - **Statistic:** mean, or median (Q0.5), and whether the Q0.05 and Q0.95 bands are carried (the register names "90% intervals").
   - **Units:** SoilGrids stores pH × 10 as an integer. Say where the division happens and what the archive carries.
   - **Resampling:** the method for Homolosine to ESRI:102008 (a 250 m to 250 m regrid), and the method for ESRI:102008 to Web Mercator. Give reasons for each (for a continuous variable, bilinear or average, not nearest, unless you argue otherwise).
4. **"Western Washington":** no definition was found. Propose the extent: for example, Washington west of the Cascade crest, or T1's PNW box clipped to Washington. Name its source and keep it in code. If no defensible definition exists without a choice, **stop and ask**.
5. **The tooling:** what is in `pyproject.toml`/`uv.lock` today (rasterio bundles GDAL 3.12.4, per `pyproject.toml:11-13`; the GDAL bindings are not installed). Find how a PMTiles archive gets written from a raster with what is available: GDAL's own PMTiles driver if this GDAL writes rasters, otherwise MBTiles through GDAL and then the `pmtiles` converter, or another documented route. Cite the tool's own documentation. Any new dependency is pinned to an exact version (uv lock), and says why.
6. **Size:** the expected fetch size and the archive size.

**Stops:** main moved; the depth or statistic is undecided by any row; no defensible "western Washington"; no way to write a raster PMTiles archive without an unpinned or system-level install; anything needing `records/`.

## Build

- **Tests first**, run and seen failing, through the real entry points: the grid (cell id round trip, origin snapping, a known point to a known cell id), the extent, and the pH scaling. A test that only checks that a file got written is not a test (CLAUDE.md in the Forager repo applies here as well).
- **The ten-cell check is a test or a script with its output committed**, reading the source values independently of the pipeline's own code path, so that it can disagree with the pipeline.
- **Revert checks**, one per behaviour: revert by one edit, confirm the failure is specific to that edit, restore from a copy saved before editing (never from git), confirm the forward change is present afterwards, and refuse to cite a run with collection or import errors.
- The full suite before and after (counted as test functions, as the D32 review counted them), ruff check and ruff format clean.
- Data under a gitignored `data/t4/` (confirm it is ignored). The PMTiles archive is data, not committed. Record its sha256, size and max zoom in the report.
- Commit and push as you go: `git push origin t4-master-grid`.

## Do not touch

- **No GBIF request, no Climate Data Store or Open-Meteo pull, no model fit.** One SoilGrids fetch, after D26's download has finished fetching.
- **No secret** in any report, commit, log or chat (D36).
- **Nothing in `records/`**, and no edit to a filed dispatch, report or decision row (D41).
- **No merge into main** (D40). **Nothing in the Forager app repository.** No Cloudflare.

## Report back

A report in `docs/audits/` with its index row: what landed with commit hashes, the verify items, test and revert evidence with counts, the grid definition, the ten-cell table, the archive's hash, size and zoom range, and what was not verified, stated plainly. Update `docs/planning/TASKS.md` and add a `START_HERE.md` session-log row. Then message the Forager planner session, which arranges the review.
