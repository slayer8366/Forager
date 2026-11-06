# Removed from this folder and replaced, 2026-11-06

On the owner's word (dispatch 2026-11-05-11, RECORD -2182), everything in this folder that showed a place near the
owner's home was removed:

- **The tile sample.** The owner first said "Remove them" of 7 tiles whose file names were tile numbers near the home.
  Removing them turned `TopoNightStreetSwitchTest` red, because it reads this folder and pins the sample's size. The
  owner then chose "Replace the whole set". Every OpenTopoMap and OSM Standard tile was rebuilt over downtown
  Asheville, North Carolina (Wikipedia's city point), at the same zooms and counts and from the same two tile servers,
  once each: 81 tiles in `tiles/` (`SHA256SUMS`), and 15 in `osm-tiles/` and `renders/osm-extra/`. `fetch_tiles.py`
  and `fetch_osm.py` now fetch exactly these files.
- **The renders.** The owner chose "Remove them" for the map images in `renders/` and their tiles,
  `renders/tiles-extra/` and `renders/dem-tiles/`. The render scripts and `renders/dem-needed.txt` now point at the
  same Asheville point, so they could be re-run, but they have not been.

The written figures from the original sample and the renders stay as they were measured, marked as such:
`per-zoom-table.md`, `renders/README.md`, `renders/metrics.md` and `docs/audits/2026-09-30-topo-night-completion-report.md`.
They cannot be re-run from the files saved here now. The old files are still in git history.
