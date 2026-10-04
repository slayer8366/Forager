# map-build: Forager's own map and routing data, built on the Pi

This folder builds two things from one OpenStreetMap extract of Oregon and Washington, on the
Raspberry Pi:
- `forager-orwa.pmtiles`, Forager's map, zoom 0 to 15. It is built with Protomaps' basemap profile,
  patched to carry trail details.
- BRouter's `.rd5` routing files for the same area.

Both outputs are stamped with the extract's date, and they are published together or not at all.

It is dispatch 2026-09-28-495, stage 4 of `docs/plans/trail-navigation-and-own-tiles.md`. The
reasoning behind every choice is in `docs/audits/2026-10-04-pi-build-part1-report.md`, and the
owner's rulings are `RECORD.md` -601. Nothing here is served to users or uploaded, and the app is
not touched.

## Where things are on the Pi

| Path | What | Owner |
|---|---|---|
| `/opt/forager-build/jdk`, `/opt/forager-build/maven` | Temurin 21 and Maven 3.9.16 | root, read-only to the build |
| `/opt/forager-build/archives/` | the verified downloads, kept for re-checking | root |
| `/opt/forager-build/map-build/` | an installed copy of this folder, with `COMMIT` | root |
| `/opt/forager-build/basemap/` | the patched basemap jar | root |
| `/opt/forager-build/brouter/` | BRouter's jar and routing profiles | root |
| `/srv/forager-build/sources/` | the extracts (`osm/`) and the profile's other sources (`aux/`) | `forager-build` |
| `/srv/forager-build/work/<run>/` | one run's scratch files, deleted when it ends | `forager-build` |
| `/srv/forager-build/out/<extract time>/` | one output: `forager-orwa.pmtiles`, `segments/*.rd5`, `manifest.json` | `forager-build` |
| `/srv/forager-build/out/current` | a link to the newest good output | `forager-build` |
| `/srv/forager-build/logs/`, `status.json` | every run's log, and the last run's state | `forager-build` |
| `/srv/forager-build/failed/latest/` | the latest failed run's outputs and log, for inspection; never published, replaced by the next failure | `forager-build` |

Nothing goes under `/srv/forager-tiles`. The tile server serves every archive in that folder, so
anything placed there would be published.

## The steps, and where each one stands

| # | Step | Script | State |
|---|---|---|---|
| 1 | Install Java, Maven and osmium, each checked | `bin/install-toolchain.sh <download dir>` | **done** 2026-10-04 |
| 2 | Create the `forager-build` user and the two folders | by hand (see the report's addendum) | **done** 2026-10-04 |
| 3 | Commit this folder | | **done**, this commit |
| 4 | Install this folder into `/opt`, then build the patched basemap jar | `bin/install-map-build.sh`, then `/opt/forager-build/map-build/bin/build-basemap-jar.sh` | **done** 2026-10-04 |
| 5 | Fetch BRouter's jar (release zip) and profiles (source), and check them | `…/bin/fetch-brouter.sh <download dir>` | **done** 2026-10-04 |
| 6 | Download the extracts and sources | `sudo -u forager-build …/bin/build-orwa.sh --fetch-only` | waits |
| 7 | The manual run | `sudo -u forager-build …/bin/build-orwa.sh` | waits |
| 8 | The zoom 14 comparison against `us.pmtiles`, read-only through the `pmtiles` CLI | `pmtiles tile` and `tools/mvt_decode.py compare` | waits |
| 9 | Install the two units; **the timer stays disabled** | copy `systemd/*` to `/etc/systemd/system/` | waits |

## The owner's rulings (RECORD -601)

- **A.** `access` stays at zoom 15, as upstream emits it.
- **B.** The six trail keys appear at every zoom the trail appears.
- **C.** Coastline polygons are refreshed monthly (`COASTLINE_MAX_AGE_DAYS`).
- **D.** The zoom 14 comparison may read `us.pmtiles` read-only through the `pmtiles` CLI.
- **E.** BRouter's jar comes from its release zip. If the jar lacks the map creator, stop and ask;
  do not compile. `fetch-brouter.sh` exits 3 in that case.
- **Change 5, option (b):** the routing profiles come from BRouter's source at the tag, each pinned
  by git blob hash, since the zip leaves out `all.brf` and `softaccess.brf`.
- **F.** A dedicated `forager-build` user.

## How each download is checked

- **Temurin:** the pinned SHA-256, plus Adoptium's GPG signature. The key's fingerprint was checked
  on four channels before it was pinned.
- **Maven:** the pinned SHA-512, plus the release manager's GPG signature, likewise checked on four
  channels.
- **osmium-tool:** through apt's signed Debian index.
- **The two public keys** are in `keys/`.
- **The basemap profile:** the pinned commit, confirmed with `git rev-parse` after checkout. The
  patch must apply cleanly, and upstream's tests must pass with the patch applied.
- **BRouter's jar:** from the release zip, checked by size and GitHub's digest. BRouter publishes no
  checksum file of its own.
- **BRouter's routing profiles:** from BRouter's source at the same tag, because the release zip
  leaves out two that the map creator reads. Each of the five files the build reads is checked
  against its pinned git blob hash.
- **Geofabrik extracts:** their published MD5. Both must name the same snapshot, and their headers
  must carry that timestamp.
- **The profile's other sources:** none publishes a checksum, so each file's size, ETag and
  SHA-256 are recorded in the manifest.

## What one run of `build-orwa.sh` does

1. Reads both states' `state.txt`. It stops with "nothing to do" if the current output was built
   from the same snapshot.
2. Downloads the dated extracts and checks their MD5s and header timestamps.
3. Fetches any missing source, and refreshes the coastline polygons when they are older than 30 days.
4. Merges the two extracts with `osmium merge` and sets the replication timestamp in the merged
   header, because osmium 1.15.0 drops it. It computes the union of the inputs' bounding boxes
   (`tools/union_bounds.py`), because the merged file has none and Planetiler would otherwise build
   the whole world.
5. Runs Planetiler with the patched profile, with no downloads, and the computed `--bounds`, to
   zoom 15.
6. Runs BRouter's `OsmFastCutter`, `PosUnifier` (with no elevation data) and `WayLinker`. It
   keeps only the two states' four squares (`RD5_SQUARES`), deleting the squares that hold only
   stretches of ferry routes to Alaska and listing them in the manifest, then sets each `.rd5`
   file's date to the extract's.
7. Validates before publishing:
   - zooms 0 to 15;
   - bounds inside the extract's;
   - the nine layers Forager's style reads;
   - all six trail keys in the roads layer;
   - exactly the four expected `.rd5` squares, none implausibly small.
8. Writes `manifest.json`, moves the output into `out/`, and switches `current` atomically. It keeps
   the newest two outputs and deletes extracts older than the ones just used.

Any failure leaves the previous output and `current` untouched. The run's log and outputs are
kept in `failed/latest/`, `status.json` names the failed stage, and the run exits non-zero, so a
timer-started run shows as a failed unit. `status.json`'s `last_success` names only a published
build; a download-only or skipped run never counts.

Each step's wall time and peak memory are recorded by `tools/peak_rss.py`, since GNU `time` is not
installed. The kernel has the memory cgroup disabled, so systemd cannot cap the build's memory.
The JVM's `-Xmx` caps it, and `OOMScoreAdjust=800` points the kernel at the build first.

## Tests and checks

- `python3 -m unittest map-build/tools/test_mvt_decode.py`: the tile decoder, against tiles
  encoded by hand.
- `python3 -m unittest map-build/tools/test_run_record.py`: the status rules and the manifest's
  dropped squares.
- `bash map-build/tools/test_build_functions.sh`: `build-orwa.sh`'s square-dropping and
  failed-run keeping, loaded from the script itself.
- The patch's own tests (`foragerTrail…`, `foragerAccess…` in upstream's `RoadsTest`) run inside
  `build-basemap-jar.sh`. It refuses the jar unless all 13 of their cases appear in the test report
  with no failures.

## Licences

The outputs are ODbL (© OpenStreetMap contributors). The `landcover` layer also derives from ESA
WorldCover, CC-BY 4.0, which needs its own credit wherever it is drawn. The map design is
Protomaps' CC0. The code licences are listed in the report, section 7.
