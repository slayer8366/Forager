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

## Addendum, 2026-10-19 20:30 PDT: published

On the owner's word of 2026-10-19 ("Publish now, add PR later"), San Juan left the coverage points
until Puerto Rico is added as a region (`099a1517`). Run `usca-20261019T185700Z` routed all ten
bands and validated: archive check, seven points, 185 squares. It then failed at publish, because
`run_record.py manifest` did `int()` on the sequence and `build-usca.sh` passes
`us:2415,canada:4945` by design (`92856ef1`). That path had never reached publish before. Fixed in
`d5ed9eec`: a plain number stays a number, so `build-orwa.sh` is unchanged; `REGION:N,...` is kept
as written; anything else exits 2. Three tests were added. Putting `int()` back fails two of them.

Before the rerun reached publish, the fixed manifest step was run alone against the failed run's
kept outputs. It exited 0 in 250 s, and the manifest read back the sequence, both inputs with their
published MD5s, 186 outputs and the commit.

Run `usca-20261019T225941Z`, at `d5ed9eec`, ran 22:59–03:21 UTC:

- 185 squares;
- seven coverage points, each with the same road counts and square sizes as the two runs before;
- published `out-usca/20261017T202051Z` with `manifest.json`, and `current` points to it;
- `status-usca.json` reads `ok` at stage `done`.

**Left in place, for the owner to rule on:**

- `kept-usca/`: the basemap, 32 GB, a hard link shared with the published one, and two 1.5 GB sets
  of squares from the runs that failed validation and publish;
- `probe-routing/`, 16 GB;
- the scratch directory of the run stopped by hand, `work/usca-20261019T130011Z`.

**Open:**

- Puerto Rico and the US Virgin Islands as regions of their own;
- why the hand stop left the status file at "running";
- WayLinker's real memory need.

## Addendum, 2026-10-19 20:45 PDT: the hand-stop gap explained (pi-04); Puerto Rico researched (pi-03)

**Supersedes** the first addendum's "The cause is **not determined**", about the status file left
at "running" after `systemctl stop`. It also corrects the hand-written status message of
2026-10-19 13:34Z, which says "Its on_exit did not run (SIGTERM)". Source: the Pi coder's report on
dispatch `2026-10-19-pi-04`. Its repro units were run under the owner's "go on pi-04". The planner
has not re-run them.

- **Observed in repro units under systemd 252 and bash 5.2:** on `systemctl stop`, `on_exit`
  runs. But `$?` holds the status of the last command that finished, not the signal. In the real
  run that was a 0 from the arithmetic just before the band extract (inferred from
  `b7302ed8`'s lines 406–411). So `if [[ $code -ne 0 ]]` skips everything: no status write, no
  kept failure, no cleanup.
- **Behind that guard, two more failures (observed):**
  - a process forked within about 1 ms of the stop is also killed;
  - the first `log` in `on_exit` writes to the killed `tee` and ends bash by SIGPIPE.
- **The fix described, not built:** turn the signal into a nonzero exit; stop `on_exit` writing to
  `tee`; don't depend on a fork in the first instant. Each part has its own revert. It waits for
  the owner's go.
- **Correction to the planner's own reasoning that day:** the planner's TERM-trap idea pointed
  the right way. Its removal was justified only by a repro that showed the trap *runs*; that repro
  never checked what the trap *saw*. That is a check measuring the wrong property, not the absence
  of the property.

**Puerto Rico and the USVI (pi-03 Phase 1, read-only).** All of the following was spot-checked by
the planner at 03:44Z:

- Geofabrik publishes `north-america/us/puerto-rico` (about 70 MB) and `us/us-virgin-islands`
  (about 3 MB).
- All four regions' `state.txt` read `timestamp=2026-10-18T20:25:06Z`.
- The flat URL `build-usca.sh` would build for a nested region returns 404; the nested one returns
  200.

So adding the islands needs a script change for nested regions, not only a region-list edit.

The coder also found that `basemap_matches` checks the timestamp and only the *first* region's
sequence. A US + Canada basemap would therefore pass for a four-region run. San Juan must return to
the coverage points in the same change.

The islands take two squares, `W70_N15` and `W65_N15`, both inside the last band.

## Addendum, 2026-10-19 20:55 PDT: Sunday's weekly build would run out of disk (pi-01); the leftovers inventoried (pi-02)

From the Pi coders' reports on dispatches `2026-10-19-pi-01` and `-pi-02` (Phase 1, read-only).
The planner spot-checked the figures marked so.

- **The first build only fit because about 58 GB was freed while it ran.** The 18:50 PDT deletions
  on 2026-10-18 were the switchover session's removal of the served archives and the orwa output,
  in the sudo log (spot-checked). The 21:05 move of the extracts to the SD card was the other part.
  Its lowest free space was about 45 GB (inferred, from read figures), at the end of Planetiler's
  archive stage.
- **Planetiler's feature file peaked at 104 GB, not 73.** The 73 GB was its size at the end of
  osm_pass2. Planetiler's own summary, `features 104GB`, is spot-checked. The archive was 33 GB.
- **Sunday 2026-10-25, unchanged, is projected about 29 GB short** (inferred, range −26 to −31),
  in the archive stage, about 6½ h into Planetiler. The reasons:
  - the new extracts (18.8 GB) are on the NVMe;
  - the published basemap (34 GB) stays until the new one publishes;
  - the leftovers are still there.
- **`USCA_MIN_FREE_GB` (110) would pass** at about 120 GiB, so it does not protect a full run. A
  full run needs about 146 GiB at the merge (inferred).
- **Freeing every leftover frees 19.86 GB** (planner re-derived 16.737 + 3.118). That is still about
  9 GB short. The planner's "about 23 GB" double-counted the hard-linked 7.6 GB cut, and its "0 to
  25 GB margin" was wrong.
- **The last two weekly firings, 2026-10-11 and 10-18, failed at DNS:** `curl: (6) Could not
  resolve host: download.geofabrik.de`, spot-checked. Whether Wi-Fi fixed that is not determined.
- **`curl_resume` has never run here against the network,** and it has no speed limit or restart
  for a download that slows.
- **The leftovers are still in place.** The owner ruled per item in the planner's window. The
  coder declined to act on the planner's relay, citing the dispatch's "given in the coder's window"
  against the profile's "in Aspen's own window". That conflict is the planner's, and it goes to the
  owner.

## Addendum, 2026-10-20 04:05Z: the hand-stop fix is on pi-build (pi-07); how a full build fits (pi-06); the Pi is on Bluetooth

**The hand-stop fix (pi-07)** is on `pi-stop-fix`, `59880bc3`..`dfb1ac45`, and `pi-build` was
fast-forwarded to `dfb1ac45`. The owner's go, verbatim: "Yes, build the fix (Recommended)". It is
**not installed**: `/opt` is still at `d5ed9eec`.

- **The three parts:**
  - TERM, INT and HUP become exits 143, 130 and 129;
  - on a signal, `on_exit` writes to the log file instead of `tee`;
  - the traps then ignore further signals.
- **Tests:** `test_build_usca_stop.sh` has 27 checks. It was committed failing, 19 failed and 7
  passed against `7f1dc74f`.
- **Revert checks,** each run on a saved copy and restored by sha256:
  - part 1 out fails with the code-0 guard;
  - part 2 out writes the status but keeps nothing;
  - part 3 out fails only the second-signal stand-in. Its first-instant case is not shown.
- **The planner re-ran everything from the branch:** stop 27, functions 37, orwa 14, the Python
  unittests OK, and no units left behind.
- **The coder's correction to pi-04:** under systemd, SIGPIPE is ignored (`IgnoreSIGPIPE=yes`). The
  dead `tee` ends `on_exit` through `set -e`, not SIGPIPE. pi-04's first-instant fork kill did not
  reproduce in 15 stops.
- **A change people will see:** a stopped unit now ends `failed` with exit 143, not
  "Deactivated successfully".

**How a full build fits (pi-06, read-only).** Figures marked measured were measured by the coder;
the rest are inferred.

- **The low point needs about 175–179 GB** with today's layout:
  - features 104 GB (read);
  - archive 34.0 GB (measured);
  - merged extract 18.7 GB;
  - the new downloads 18.8 GB.
- **About 166.5 GB is free once the leftovers go.** The planner's "157" mixed GiB and GB. The
  script's merge message has the same slip: `df -BG` gives GiB, and the message says GB.
- **The SD card measured about 58 MB/s writing and 96 MB/s reading.** MD5 from the card took 196
  s, against 51 s from the NVMe, read from the logs.
- **The margins:**
  - downloads on the card plus the merged extract on the card, keeping the old map: about +25 to
    +28 GB;
  - removing the old map early plus downloads on the card: about +41 to +45 GB;
  - a correct, byte-based floor (D) would refuse an over-size week in minutes.
- **Nothing outside `build-usca.sh` reads `out-usca/current`.** The tile server and tunnel are
  disabled and no cron, timer or rclone job reads it. The Worker's source was not read. RECORD
  −879 and −1276, cited in the script's header, could **not be found** in `RECORD.md` on any
  remote branch.
- **The planner's prediction:** "A + B ≥30 GB" right; "no measurable cost" slightly wrong (+2.4 min
  of MD5); "C costs hours" wrong (minutes).

**The Pi is on the Bluetooth tether, not Wi-Fi.** Found by pi-06, checked by the planner at
04:02Z with `ip route` and `nmcli`. `wlan0` failed its association at 19:48 PDT on 2026-10-18 and
has not reconnected. The planner's memory note said Wi-Fi was the default route; it has been
corrected. The 2026-10-18 weekly failure was the tether being down (no network), not DNS
(inferred). At Bluetooth speed, a weekly download is about 26 h (inferred).

**Cleanup (pi-02 Phase 2): not done.** Claude Code's permission check refused the coder's first
delete as irreversible. Nothing was deleted. The planner did not route around it, and gave the
owner the commands to run by hand.

## Addendum, 2026-10-20 04:12Z: the hand-stop fix installed (pi-08)

The owner's word, verbatim: "Install now (Recommended)". At 21:07 PDT the installer put `dfb1ac45`
into `/opt/forager-build/map-build`; the previous copy, `d5ed9eec`, is now `map-build.prev`.

- `bin` and `tools` match the checkout. The only difference was the checkout's git-ignored
  `__pycache__`, which the installer correctly does not copy. The coder stopped on it, and the
  planner cleared it as not a committed diff.
- `test_build_usca_stop.sh` was run from `/opt`, so it tested the installed files: "ok: 27 checks".
- No test units are left behind. The timer is still disabled.
- The planner confirmed `COMMIT` and the installed trap line with its own reads.

## Addendum, 2026-10-20 04:55Z: a full build fits, keeping the last map (pi-09), merged into pi-build, not installed

The owner's words, verbatim: "No, always keep the last map (Recommended)" and "Yes, build it
(Recommended)". The coder's own records are `docs/audits/2026-10-20-pi-09-disk-fit-phase1.md` and
`-completion.md`. `pi-disk-fit` (`de48f2d5`..`4a378991`) was merged into `pi-build` at `9588d0df`.

**What changed:**

- **Downloads and the merged extract go on the SD card,** and the cleanup removes old card
  extracts.
- **A byte-based disk check runs at the state stage and at the merge.** Features 5.61×, archive
  1.82× and multipolygon 0.16× the input; a 10% margin, minimum 5e9 bytes.
- **Downloads now use a resume loop:** abort under 50 kB/s for 120 s, pause 10 s, at most 500
  connections.
- **A bounded wait for the network.**
- **The repo's unit gains `ReadWritePaths=-/mnt/archive/forager-build`.**

**Found on the way (measured by the coder):**

- curl's `--retry` with `-C -` re-sends the starting offset. Today's installed `curl_resume`
  re-downloads from the start after each interruption.
- `ip route` fails under the unit's `RestrictAddressFamilies`.
- The 2026-10-18 restart loop's 900 KB/s was on Wi-Fi.

**Review:**

- **The planner re-ran every suite from the branch:** disk 69, run 36, functions 37, stop 27,
  orwa 14, Python 22. After the merge, disk, run and functions passed again.
- **The planner read the card cleanup by hand.** It only touches `us-` and `canada-` dated
  extracts, never this run's inputs.
- **Revert checks:** one per behaviour, each restored from a copy. The coder's first cleanup revert
  passed everything; that exposed a redundant line, which was removed (`f20bdba1`).
- **The coder corrected its own commit message's false "37 checks"** in `f70c1535`.

**Sunday under the new check (inferred):**

- With the leftovers still present, the run is refused at the state stage, about 9.9e9 bytes short,
  within minutes.
- With them cleared, it passes with about 10e9 of slack and about 24e9 of real headroom at the low
  point.

**Not installed.** Installing needs the owner's go: the scripts and pins into `/opt`, and the unit
into `/etc` with `daemon-reload`.

**Untested:** a real Geofabrik download through the loop; Planetiler reading from the card; publish
end to end; the installed unit; and how much the ratios move from week to week.

**Proposed, not built:** a per-link speed floor, 700 kB/s on Wi-Fi.
