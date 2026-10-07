# Dispatch: T6b, continental soil and host-tree layers on the master grid

**Verify first and report by message; then build unless something is a stop. The fetches run one at a time, only after the planner's go on the verify report. No GBIF request, no climate pull, no model fit, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), on the owner's "Yes, write the T6b dispatch", against forager-forecast `origin/main` at `36cc647`. This dispatch is the first commit on branch `t6b-continental-layers`. Every cite is a premise to re-check.

**Writer (D38):** a coder session on this laptop, worktree `~/Zynergy/forager-forecast-t6b`. **Review (D18).** **Merge (D40).** **Decision rows from D111.**

## The machine: read this first

This 11 GB laptop has crashed three times today from out-of-memory, once from a single script that grew to 9.4 GB (Forager RECORD -610). So:
- **Every heavy command runs under a hard cap:** `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0 <command>`. Design for well under it. Process by tile and stream, and never hold a continental array.
- **All data goes on the USB flash drive** at `/run/media/zynergy-labs/2ebd084f-5fdd-4730-8cef-96b267723190/forecast-data/t6b/`, with about 21 GB free. The internal disk has about 4 GB free, so write nothing large there. If the drive isn't mounted, stop.
- **The run will be long.** T4 extrapolated about 4.5 hours for soil alone. Make it **resumable by tile:** record each finished tile and skip it on restart. One background run that you wait on, committing progress (a tile manifest) as it goes. Report if a restart happens and how much was redone.
- One heavy job at a time. Nothing else runs on this laptop while T6b runs.

## Why

TASKS.md, T6b (added by the owner, D100): "run T4's soil pH layer (D80, D81, D74) and T5's genus canopy shares with their source flags over the whole study area on the master grid: the United States and Canada, with Alaska, Mexico and the Arctic masked … The work is split into tiles that fit in memory. Data lives on the external flash drive". Verify: "no step at any internal tile edge (the same cells computed inside one tile and across a tile edge agree exactly); the count of valid cells per layer is reported against the land area; ten cells per layer, fixed by rule before any value is read, match the source within T4's tolerance; and the full run's time and disk use are recorded against T4's extrapolation". T7 depends on it.

Carry over, unchanged: T4's grid, cell id and exact area-weighted regrid (`grid.py`, `regrid.py`), the 0 to 30 cm blend and approximate Q0.05/Q0.95 bands (D80, D81), and the 0.5 valid fraction (D74). Also T5's genus canopy share with source flags (D84 to D92): TreeMap 2023 CANOPYPCT as the US total (D88), SCANFI's own total as Canada's (D92), capped diameters (D91), and not-available flags in Canada (D85, D89). The T5 review flagged that **SCANFI's ten classes summing to its total was shown on the border strip only**. Check it continent-wide.

## Verify first, report by message

1. Base `36cc647`, and every cite above, confirmed or corrected.
2. **The study-area mask.** SPEC: "Mexico and the Arctic in phase 1 … stay masked", and Alaska is masked (D31, D86). Propose the exact boundary and its source: country lines (from what dataset, with its licence quoted from the source, D31's licence gate), Alaska's line, and **what "the Arctic" means as a line** (a latitude? SCANFI's own extent? an ecozone?). Nothing on record defines the Arctic line: **that is a stop for the owner.** Give options with what each includes, for example SCANFI's own northern limit, 60°N, or the Arctic ecozones.
3. **Extent and tiles.** The master grid's extent for the study area, the tile size and count, and peak memory per tile measured on one trial tile. Tiles overlap by enough that the regrid at an edge sees the same source pixels as it would inside a tile, and say how much that is.
4. **Sources and sizes.**
   - SoilGrids: three depths × three statistics over the extent, as windowed reads per tile, against the 21 GB free. Can the per-tile native subsets be discarded after each tile? Say what's kept.
   - TreeMap 2023: already on the drive (5.26 GB raster plus tree table, T5).
   - SCANFI v2 2025: the ten classes plus the total for all of Canada. Give the size, windowed or whole.
   - Each request is recorded under `docs/pulls/`, and each publisher checksum checked where one exists.
5. **Run time,** extrapolated from the trial tile, per layer.
6. **The ten cells per layer,** fixed by rule now and committed before any value is read. Spread them across the continent: coast, mountains, prairie, boreal, both sides of 49°N, and at least one masked and one no-data cell.

**Stops:** the Arctic line (always); any boundary dataset whose licence is not stated at source; disk beyond the drive; a tile that won't fit under the cap; any change to T4's or T5's rules.

## Build (after the owner's Arctic line and the planner's go)

- **Tests first** through the real entry points: tile boundaries reproduce the untiled result exactly on a synthetic case, the mask, and resume-after-interrupt. Then the strict revert runner (saved-copy restore, `__pycache__` cleared, `PYTHONDONTWRITEBYTECODE=1`, refuse a run with errors or where the interpreter doesn't see the edit). Full suite before and after, under the cap.
- **The run,** tile by tile, resumable, on the flash drive.
- **The Verify, as TASKS.md states it:** the tile-edge agreement on real data (recompute a band of cells across several edges in one window and compare exactly), valid cells against land area per layer, the ten cells per layer, and time and disk against T4's extrapolation. Also the SCANFI class-sum check continent-wide, and the border seam's total-cover step along the whole 49°N line, recorded as T5 did, not tuned.
- Outputs are master-grid rasters per layer, with hashes in the report. No tiles for the app.

## Do not touch

No GBIF request, no climate pull, no fit, no secret (D36), no edit to a filed record (D41), no merge, nothing in the Forager app repo. T4's and T5's rules are unchanged; a change is a stop.

## Report back

A completion report in `docs/audits/` with its index row, TASKS.md and START_HERE updated, then a message to the Forager planner session.
