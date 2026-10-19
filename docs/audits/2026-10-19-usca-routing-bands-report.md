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

## Addendum, 2026-10-19 11:55 PDT: the rerun routed everything, then validation refused San Juan

Run `usca-20261019T140722Z` used `build-usca.sh --local 261017 --basemap …` at `5bc1a752`.

**What happened before it.** An earlier rerun, `usca-20261019T130011Z` at `b7302ed8`, was stopped
by hand 33 minutes in, before any band ran. Its routing input came to 826,768,719 nodes, 1.73
times the highway-and-ferry cut the six bands had been drawn from. The last band would have held
about 187M nodes. The script then checked each band only just before running it, so the run would
have failed hours in. The routing input was saved first, by hard link. Two changes followed:
`c894625b` cuts and counts every band before BRouter runs on any, and `5bc1a752` adds ten bands
drawn from that input's own per-strip counts. That stop left `status-usca.json` at "running". Two
explanations were tried and both disproved: a repro showed bash runs the EXIT trap on SIGTERM. A
TERM trap was taken back out. The cause is **not determined**.

**Routing: all ten bands, every step exit 0, 185 squares.**

| Band | Nodes | cut s | cut peak | link s | link peak | Squares |
|---|---|---|---|---|---|---|
| −180:−125 | 73.2M | 214 | 2.2 GB | 21 | 1.4 GB | 48 |
| −125:−120 | 113.2M | 675 | 2.9 GB | 137 | 3.8 GB | 9 |
| −120:−110 | 128.4M | 924 | 4.0 GB | 193 | 2.8 GB | 19 |
| −110:−100 | 84.6M | 479 | 2.4 GB | 112 | 3.1 GB | 20 |
| −100:−95 | 81.9M | 901 | 2.4 GB | 139 | 3.5 GB | 11 |
| −95:−90 | 94.9M | 635 | 2.6 GB | 142 | 3.5 GB | 13 |
| −90:−85 | 113.2M | 1419 | 3.4 GB | 192 | 3.2 GB | 11 |
| −85:−80 | 150.1M | 1448 | 4.3 GB | 277 | 4.3 GB | 12 |
| −80:−75 | 120.3M | 1468 | 3.5 GB | 204 | 4.4 GB | 11 |
| −75:180 | 104.7M | 826 | 2.7 GB | 149 | 4.4 GB | 31 |

Peaks are resident memory under `-Xmx4g`. WayLinker's peak does not follow a band's node count:
the 113M band peaked higher than the 128M band. Its real need is still **not determined**.

**Validation.** The archive check passed: zooms 0–15, 9 layers, all six trail keys in roads. The
coverage check then failed: "san-juan: z15 tile 15/10366/14673 is missing or has no roads". The
check stops at the first failure, so the other points were then checked by hand the way the
script checks them. The script's own `coverage.txt` agrees with the hand check on the four points
it reached.

| Point | Roads in z15 tile | Routing square |
|---|---|---|
| Seattle | 142 | `W125_N45`, 54.9 MB |
| Miami | 137 | `W85_N25`, 43.9 MB |
| Anchorage | 50 | `W150_N60`, 3.1 MB |
| Honolulu | 127 | `W160_N20`, 2.2 MB |
| San Juan | 0: the tile holds only `earth` and ocean `water` | `W70_N15` missing |
| Toronto | 165 | `W80_N40`, 63.9 MB |
| Whitehorse | 34 | `W140_N60`, 0.95 MB |
| St. John's | 111 | `W55_N45`, 1.7 MB |

**A premise that was wrong.** `build-usca.sh` and the README say Geofabrik's `us` extract carries
Alaska, Hawaii, Puerto Rico and the US Virgin Islands, citing RECORD −1274 and −1300. For
`us-261017.osm.pbf` that is false for the last two. Its highway cut has 0 nodes in a Puerto Rico
box and 0 in a USVI box. Its outline, `us.poly`, runs east only to −66.88°, while San Juan is at
−66.11°. The routing data has no square there either. Where Geofabrik publishes Puerto Rico and
the USVI is **not checked** here.

**Kept.** The 185 squares, with that run's log, metrics and coverage, are in
`kept-usca/usca-20261019T140722Z/`, moved out of `failed-usca/latest` because the next failure
there would delete them. The basemap is unchanged in `kept-usca/usca-20261019T011311Z/`. Nothing
was published.
