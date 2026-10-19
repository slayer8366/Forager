# 2026-10-19: the first US + Canada build failed at routing; routing now runs in bands

Pi session, owner present. Commits on `pi-build`: `f4196fdf`, `b7302ed8`. Installed at
`b7302ed8`. The rerun's outcome is not in this report; it gets an addendum.

## What failed

Run `usca-20261019T011311Z`, started 2026-10-18 18:13 PDT by `forager-usca-fetch-build` and run
with `build-usca.sh --local 261017` at `92856ef1`:

| Stage | Result |
|---|---|
| merge | exit 0, 1107 s |
| planetiler | exit 0, 27854 s (7h44m), peak 7.0 GB resident; `forager-usca.pmtiles` 33,973,424,728 bytes |
| brouter-cut | exit 1 after 227 s: `OutOfMemoryError: Java heap space` in `TinyDenseLongMap._add`, at 134M of 2.4B nodes, heap 2g (`USCA_BROUTER_XMX`) |

The script behaved as designed on failure. The previous output was untouched, the run was marked
unpublishable, and `out/` was kept in `failed-usca/latest`. The basemap's archive check had not
run, because validation comes after routing.

## What the owner decided

- "Go with A, and keep the basemap and move on". A meant cutting the routing input down to what
  BRouter uses.
- When A alone was measured not to fit, "A + finer regions for now". The big machine is busy with
  forecast work.
- Commits go on `pi-build`, where `map-build/` lives, not on this session's branch (`pi-origin`).

## Measured

- **BRouter's cutter holds about 16 bytes per node of its input.** 2 GB ran out at 134M nodes of
  the full merge. 512 MB ran out at 33M nodes of the cut-down Canada input. Thinning the input
  does not change the rate.
- **The highway-and-ferry cut** (`osmium tags-filter ... w/highway w/route=ferry r/route
  r/type=restriction`) has 434,101,201 US nodes (52.2M ways, 4.3 GB, 769 s) and 43,364,362
  Canada nodes (5.5M ways, 430 MB, 303 s). About 477M nodes in total, roughly 7.6 GB at 16
  bytes, so A alone does not fit the 8 GB Pi. The build's filter (`USCA_ROUTING_FILTER`) also
  keeps railways and waterways, because `all.brf` gives those keys a cost. Its counts are not
  measured here; the run logs them.
- **Nodes per 5-degree strip of longitude** in that cut sum to the cut's own totals. The band
  edges in `USCA_ROUTING_BANDS` are drawn from them: 85.8M, 68.1M, 79.4M, 52.6M, 83.3M and
  108.2M nodes.
- **Splitting does not change the routing data.** Canada's cut was built whole (2g) and in two
  bands, west of −100 and −100 eastward, each extracted with a 1-degree margin using
  `osmium extract -s complete_ways`. Each square was taken from the band holding its west edge.
  All 106 squares were byte-identical except 8 bytes 113 from each file's end. Those bytes
  decode as a big-endian millisecond time equal to that run's WayLinker step, so they are a
  stamp, not data. The comparison was itself checked three ways. Comparing a build with itself
  gave 106 of 106 identical. A byte flipped mid-file was caught. A byte one past the masked
  field was also caught.
- **`osmium extract -s complete_ways` peaks at about 3.8 GB per band**, whatever the band's
  size, and over 7 GB for two bands in one pass. That two-band pass was killed by the kernel. So
  the script extracts one band at a time.

## Changed

- `--basemap FILE` reuses a finished archive. It stops at once unless FILE's metadata names the
  extracts' replication timestamp and sequence (`basemap_matches`). The archive is hard-linked
  into the output, and `versions.txt` records the reuse.
- The routing input is cut and merged, then split into bands. BRouter runs per band and keeps
  only the squares whose west edge is in the band (`rd5_west_edge`). The band list must be
  contiguous from −180 to 180 on 5-degree lines (`routing_bands_ok`).
- A band over `USCA_BROUTER_MAX_BAND_NODES` (160M) stops the run before BRouter starts.
  `USCA_BROUTER_XMX` went from 2g to 4g.
- Tests: `test_build_usca_functions.sh` has 37 checks, 22 of them new, and
  `test_build_functions.sh` has 14. Four revert checks were each restored from a saved copy and
  each failed with its own message:
  - Removing `basemap_matches`' refusal failed "a basemap from another snapshot is refused".
  - Removing the contiguity test failed "a gap between bands is refused".
  - Dropping the minus on W squares failed "W125_N45 starts at -125".
  - The comparison's controls are above.

## Moved, kept

- The kept basemap is at `/srv/forager-build/kept-usca/usca-20261019T011311Z/`, with that run's
  log and metrics. `keep_failed_run` deletes `failed-usca/latest` on the next failure, so it was
  moved out.
- The two extracts were moved to the SD card at `/mnt/archive/forager-build/sources-osm/` on
  2026-10-18. Their MD5s were checked by reading directly from the card. `sources/osm/` holds
  symlinks to them and the `.md5` files. The card must stay mounted until a run publishes, since
  the manifest hashes the inputs.

## Not determined

- Whether WayLinker's memory grows with a band's size the way the cutter's does. At 27M nodes it
  peaked at 2.1 GB resident on a 2 GB heap.
- Whether the bands under the full filter stay under 160M nodes. The run checks this and stops if
  not.
- How long the full run takes.
- That the two-band Canada check covers every edge case of a split. It covers one boundary, at
  −100. The full run has five boundaries and the antimeridian.
