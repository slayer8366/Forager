# Puerto Rico and the US Virgin Islands as regions of their own (dispatch 2026-10-20-pi-14)

The Pi coder's record of dispatch `prompts/preserved/2026-10-20-pi-14.md`, on branch `pi-islands`,
cut from `origin/pi-build` at `13d20afa` (verified against the remote at the start: `git fetch`
then `rev-parse origin/pi-build` gave `13d20afa9c59...`). Owner's words, relayed verbatim by the
planner: "Write the Puerto Rico dispatch"; to start it, "You start a coder now (Recommended)"; and
the planner's relay counts as the go ("Your relay counts (Recommended)"). Not installed. Nothing
downloaded from Geofabrik but HTML pages, `state.txt`, `.poly` and `.md5` files and HTTP headers.

Each claim is marked **read** (a file and line, or a URL and the time it was read), **observed**
(seen in a command's output here), **measured**, or **inferred**.

## Phase 1, written before any test or change

### The premises, re-checked

1. **Geofabrik publishes the islands as nested regions. True.** Observed 2026-10-20 05:29Z:
   - `north-america/us/puerto-rico-latest.osm.pbf` and `north-america/us/us-virgin-islands-latest.osm.pbf`
     each answer `307` to `north-america/us/<stem>-261018.osm.pbf`;
   - the dated files answer `200`, at 74,087,081 bytes (Puerto Rico) and 3,122,071 bytes (USVI);
     the 261017 files are there too, at 74,075,487 and 3,122,047, each with its `.md5`;
   - `us/puerto-rico.poly` (239 bytes) and `us/us-virgin-islands.poly` (495 bytes) answer `200`;
   - the region pages (`us/puerto-rico.html`, `us/us-virgin-islands.html`) give "70 MB" and
     "3.0 MB" and "all OSM data up to 2026-10-18T20:25:06Z".
   - Each `.md5` names the bare file stem, `puerto-rico-261017.osm.pbf` and
     `us-virgin-islands-261017.osm.pbf` (read, the files themselves), so `md5sum -c` in
     `sources/osm` works if the local file is named by its stem.
2. **One snapshot with `us` and `canada`. True.** Observed 05:28Z, all four `-updates/state.txt`:
   `timestamp=2026-10-18T20\:25\:06Z`; sequences `us` 2416, `canada` 4946, `us/puerto-rico` 3282,
   `us/us-virgin-islands` 1791. The same-snapshot rule (`build-usca.sh:476-483`) stands unchanged.
3. **The script assumes a bare stem. True, at every line the dispatch names.** Read in
   `build-usca.sh` at `13d20afa`: the size URL `:509`, the `.md5` URL `:526`, the download URL
   `:529` (all `$USCA_GEOFABRIK_BASE/${file[$s]}`); the `--local` name `:456` (`$s-$local_date`);
   the cleanup globs `:325-326`; and the outline `:560-561` (`$USCA_GEOFABRIK_BASE/$s.poly`, kept as
   `$s.poly`). The flat URLs return `404` (observed 05:29Z: `north-america/puerto-rico-261018.osm.pbf`
   and `north-america/puerto-rico.poly`). The state and `-latest` URLs (`:466`, `:470`) are right
   for a nested path as written. One thing the dispatch did not list, read at `:470-471`: `file[$s]`
   is the basename of the `-latest` redirect, so for a nested region it is already the stem form,
   `puerto-rico-261018.osm.pbf`, and only the directory is lost.
4. **`basemap_matches` compares the timestamp and only the first region's sequence. True.** Read:
   the call at `:498` passes `${seq[$first]}`; the merge stamps only `${seq[$first]}` into the header
   at `:599`; the function compares Planetiler's two metadata keys at `:232-233`.
5. **Both islands in the last band. True for the coverage points.** `rd5_square` (the script's own,
   `:180-187`) gives `W70_N15` for San Juan and `W65_N15` for Charlotte Amalie (observed); both west
   edges are inside `-75:180` (`pins.env:203`). The band-size figure is pi-03's estimate, not
   re-measured: the band's last count was 104.7M nodes (audit, first addendum), so even pi-03's
   3.4M more stays far under 160M (`USCA_BROUTER_MAX_BAND_NODES`).

Also read: the manifest's sequence parser accepts any `REGION:N` whose name has no `:` or `,`
(`run_record.py:141-151`), so `us/puerto-rico:3282` is accepted as written. Its only reader is
`build-usca.sh`'s own skip check (`:486-489`; `git grep replication_sequence`, the other hits are
`build-orwa.sh`'s and the test's). `USCA_REGIONS` has only ever been `"us canada"`
(`git log -S'USCA_REGIONS=' --all`: set in `92856ef1`, never changed), so every basemap this script
has built holds US and Canada only.

The disk check sums `size[$s]` over `$USCA_REGIONS` (`:503-516`, `:585-586`), so the islands' bytes
are added with no change (read). The figures are in "What the first rebuild needs" below.

**Coverage points.** San Juan, `san-juan:-66.1057:18.4655`, lies inside `us/puerto-rico.poly`, and
Charlotte Amalie, `charlotte-amalie:-64.9307:18.3419`, inside `us/us-virgin-islands.poly` and not
Puerto Rico's (observed: a ray-casting test on the two outlines as read at 05:29Z). Their z15 tiles,
by the script's `z15_tile`: `15/10366/14673` (the tile the 2026-10-19 run refused) and
`15/10473/14685`. The two outlines overlap slightly: each has one vertex inside the other
(observed).

**The USVI square's size: the estimate is shaky.** Hawaii's extract is 27,352,454 bytes (HEAD,
`us/hawaii-261017.osm.pbf`, 05:30Z), and its square `W160_N20` was 2.2 MB (audit, first
addendum), about 0.080 bytes of square per byte of extract. At that ratio the USVI's 3,122,071
bytes give about 250 kB in `W65_N15`, 2.5 times the 100,000-byte floor (inferred, from one other
region; pi-03 said about 3 times). The ratio depends on how much of an extract is roads rather than
buildings and land use, which differs between regions, and nothing here can count the USVI's roads
without downloading the extract. So the floor is kept at 100,000 bytes for every point, and the
risk is stated, not engineered around: if the USVI square comes in under it, the first rebuild is
refused at validation, after routing, not published wrongly. A per-point floor would be a new
capability, and it is proposed in the report, not built.

### The design

**Region path and file stem.** `USCA_REGIONS="us canada us/puerto-rico us/us-virgin-islands"`. Each
entry is the region's path under `USCA_GEOFABRIK_BASE`, as Geofabrik serves it. Its stem is the
basename, `${s##*/}`: `us`, `canada`, `puerto-rico`, `us-virgin-islands`. A pinned map was the
alternative; it was rejected because Geofabrik's own dated names and `.md5` contents already are the
basename, so a map could only ever restate it or disagree with it.
- **From the path:** `state.txt` and `-latest` (as now); the dated file's size, `.md5` and download
  URLs, from a new `region_url_dir PATH`, which is `USCA_GEOFABRIK_BASE` for a bare name and
  `USCA_GEOFABRIK_BASE/us` for `us/puerto-rico`; and the `.poly` URL, `$USCA_GEOFABRIK_BASE/$s.poly`.
- **From the stem:** the `--local` file name, the cleanup globs, and the outline's local name
  (`sources/aux/puerto-rico.poly`). The dated file name is already the stem form (premise 3).
- **A check on the list, at the state stage:** every entry a lower-case path of `[a-z0-9-]`
  segments, and no two entries with one stem, since two would share local file names and each
  other's cleanup. `us` and `canada` pass it.
- **`us` and `canada` are unchanged:** for a bare name the URL directory is the base itself, and
  the stem is the name, so every URL, file name and glob is the same string as today.
- **`us` first.** The merge stamps the first region's sequence into the header (`:599`), so `us`
  stays first, as the published basemap's metadata has it.

**The manifest's sequence names the paths:** `us:2416,canada:4946,us/puerto-rico:3282,us/us-virgin-islands:1791`.
`us` and `canada` keep their names and their places. The string changes because two regions are
added, so the first four-region run does not match today's manifest and does not skip, which is
right: today's output has no islands. Paths rather than stems, because the path is the region's
name at Geofabrik and the parser accepts it.

**The basemap check: a sidecar, `<archive>.regions`.** Every run that produces an archive writes,
next to it in the output, `forager-usca.pmtiles.regions`, two lines: `timestamp=<osm_ts>` and
`sequence=<the manifest's sequence string>`. A Planetiler run writes it after Planetiler; a
`--basemap` run writes its own after linking the basemap, since its check has just vouched for it.
`--basemap FILE` then also runs a new `basemap_regions_match`, after `basemap_matches`:
- with `FILE.regions` present, its timestamp and sequence must equal this run's, or the run stops;
- with none, the archive predates the sidecar. Every such archive was built from `us canada`
  (above), so a new pin, `USCA_BASEMAP_UNLISTED_REGIONS="us canada"`, says so: the basemap is
  accepted only when this run's regions are exactly those, and the metadata check does the rest
  as it does today. Any other region list is refused, naming the missing file.

**On the dispatch's stop** ("if the only mechanism would change how today's kept basemap is
accepted"): this does not. Today's kept basemap has no sidecar. A `us canada` run with it is
accepted exactly as now, by the same metadata check, with one more log line. A four-region run is
refused, which is the hazard itself. So Phase 2 goes ahead. The published output gains one small
file, the sidecar, which the manifest does not list, as it lists only the archive and the squares.

**The outlines.** `merge_poly.py` is not changed: it loops over any number of inputs (read,
`merge_poly.py:55-63`). A new test joins four, with two overlapping. Its shapes are made up, not
Geofabrik's vertices, so that no coordinates enter the repo beyond the two coverage points.

**Wording.** The header (`build-usca.sh:2-9`, `:41-43`, `:49-51`, section 3's title), `pins.env`'s
region block and the README's US + Canada section say the islands are regions of their own.

### Prediction, before any test

- Tests committed first, against today's script, will fail as follows:
  - the four-region `--fetch-only` run fails at the **state** stage, with "could not read the size
    of puerto-rico-261017.osm.pbf", because the size URL is flat and the stand-in Geofabrik answers
    404 as the real one does;
  - the four-region `--local` run fails at the state stage with "--local: .../us/puerto-rico-261017.osm.pbf
    or its .md5 is missing", the path used as a file name;
  - the four-region `--basemap` runs fail at that same `--local` line, before the basemap check is
    reached: in today's script the hazard is unreachable, because no four-region run gets that far.
    So the basemap refusal's own evidence is the function tests and its revert check, not the
    failing-first run;
  - the cleanup test leaves the old island extracts in place, since `us/puerto-rico-[0-9]*` matches
    nothing;
  - the function tests stop at the first missing function (`region_url_dir`);
  - the pins checks fail on `USCA_REGIONS` and the coverage points;
  - the four-outline merge test **passes before the change**, since `merge_poly.py` is not changed.
    It covers existing code, and is flagged as such rather than counted as failing-first evidence;
  - the `us canada` URL-list run passes before and after, by design: it is the check that
    nothing changed for them.
- After the change: every suite passes; the existing checks pass unedited, run with
  `USCA_REGIONS="us canada"`.
- Each revert check fails only the checks of its own edit.

## Phase 2: built, 2026-10-20

No premise was false and the basemap stop did not apply (Phase 1), so Phase 2 went ahead.

### Commits on `pi-islands`

- `698d8da8`: Phase 1, this record's first section.
- `8dd93a16`: the tests, committed failing.
- `6e4c6ba6`: the change, in `build-usca.sh`, `pins.env` and the README.
- This record's Phase 2 section is the commit after them.

### Failing first (observed, against the unchanged script at `8dd93a16`)

- `test_build_usca_functions.sh`: 22 of 24 new checks failed. The failures were "command not found"
  for the four new functions, and the pins (regions, the unlisted-regions pin, the two points). The
  two that passed test the existing `rd5_square` and `rd5_west_edge` on the new points, which are
  facts and not changes.
  - Two slips in the test were caught there and fixed before the commit. First, refusal checks
    written `! f ...` passed with `f` undefined; each now requires `declare -F f` first. Second, a
    pin check read a variable the test had itself set; it now unsets it before sourcing `pins.env`.
- `test_build_usca_disk.sh`: 2 failed. The old island extracts were left in place, because
  `us/puerto-rico-[0-9]*` matches nothing. The keep checks and the `us canada` guard passed, by
  design.
- `test_build_usca_run.sh`: 21 failed, as predicted.
  - R6 stopped at the state stage: "could not read the size of puerto-rico-261017.osm.pbf: no
    Content-Length for https://download.geofabrik.de/north-america/puerto-rico-261017.osm.pbf".
  - R7 and every R8 stopped at "--local: .../sources/osm/us/puerto-rico-261017.osm.pbf or its .md5
    is missing".
  - R2b failed: no sidecar yet.
  - R6b passed (2), by design. So did R8's "before routing" check, vacuously, since the run stopped
    earlier. One check, R6's "no path-named directory", first passed vacuously because the card
    directory was never made. It now requires the directories to exist.
  - R1 to R5, run with `USCA_REGIONS="us canada"`, passed unedited, 38 as before. They also passed
    with the restructured stand-in before any script change.
- `test_merge_poly.py`: the two new four-outline tests passed before the change, as predicted.
  `merge_poly.py` is unchanged, so they are coverage of existing code, not evidence for this change.

### Revert checks (observed)

One per behaviour, by `revert.py` in the session's scratchpad. For each case it:
- saved a copy;
- applied one exact edit, refused unless the old text occurred exactly once;
- checked that the reverted file parses with `bash -n`;
- ran the affected suite;
- restored from the saved copy and compared the sha256: every case matched.

After all cases, `git diff --stat HEAD` was empty, and the forward change was confirmed present by
grep (3 `region_url_dir "$s"`, 2 `write_basemap_regions "$pmtiles_out"`, 1 `basemap_regions_match "$basemap"`).

| Reverted | Failed | Read as |
|---|---|---|
| size URL to flat | run, 11, all R6 | state stage, the 404 size message |
| `.md5` URL to flat | run, 9, all R6 | fetch-extracts, exit 22 (curl's 404) |
| download URL to flat | run, 9, all R6 | fetch-extracts, exit 1 (the download failed) |
| `--local` name to the path | run, 9, all R7 and R8 | "--local: .../us/puerto-rico-261017.osm.pbf ... missing" |
| cleanup's stem line removed | disk, 2 | the two island-removal checks |
| `.poly` URL to the stem | run, 10, R6, R7 and R8-four | fetch-sources, exit 22 (404) |
| `.poly` local name to the path | run, 10, the same | fetch-sources, exit 1 |
| the `basemap_regions_match` call removed | run, 3, R8-none and R8-two | the island-less basemaps accepted |
| the unlisted-regions branch made to refuse | functions 1, run 4 | R2 refused: "no .../kept.pmtiles.regions; an archive without one was built from us canada" |
| the sidecar after Planetiler removed | run, 1 | R7's `.regions` check |
| the sidecar after `--basemap` removed | run, 1 | R2b |
| the stem-uniqueness test removed | functions, 1 | "two regions with one stem are refused" |
| `pins.env` regions back to `us canada` | functions, 1 | the regions pin check |
| `pins.env` points without the two islands | functions, 3 | San Juan, Charlotte Amalie, and the seven-then-more check |

Every failure in each case is one that edit can produce. None names another edit's check.

### The full suite (observed, at `6e4c6ba6`)

| Suite | Before | After |
|---|---|---|
| `test_build_usca_disk.sh` | 89 | 95 |
| `test_build_usca_run.sh` | 38 | 62 |
| `test_build_usca_functions.sh` | 37 | 61 |
| `test_build_usca_stop.sh` | 27 | 27 |
| `test_build_functions.sh` | 14 | 14 |
| Python: merge_poly, mvt_decode, run_record | 5, 6, 11 | 7, 6, 11 |

The new totals match the checks added: disk 6, run 24, functions 24, Python 2.

### Not tested

- **Real Geofabrik downloads of a nested region** through `curl_resume`. The stand-in serves by URL
  path and answers 404 for the flat URL, as Geofabrik did when read (observed 05:29Z). But no
  `.osm.pbf` was fetched.
- **Planetiler, BRouter and validation on the islands.** The stand-ins stop at BRouter's first step,
  so the coverage check on San Juan and Charlotte Amalie has not run on real data. The merge did
  run, through real osmium: 8 nodes from four tiny extracts.
- **The USVI square's real size.** About 250 kB is an estimate (Phase 1).
- **The island 261017 files' header timestamps.** They are inferred to be 2026-10-17T20:20:51Z, like
  the us and canada 261017 files. Reading them needs the files.
- **A failed `.md5` fetch ends the run with curl's exit 22 and no "FAILED:" line** (observed in the
  `.md5` revert). This is so for `us` and `canada` today too, so it was left as it is.

### What the first rebuild with the islands needs (inferred unless marked)

- **The basemap must be built again.** No existing archive holds the islands. Today's kept basemap
  has no `.regions` file, so a four-region `--basemap` run refuses it at the state stage, in
  seconds (R8). Planetiler took 7h44m on us + canada (measured, 2026-10-19). The islands add 0.4%
  of input, so expect about the same. Then routing, validation and publish: the last such run took
  4h22m (22:59–03:21Z, audit). That is about 12 hours in all.
- **Downloads.**
  - With a new snapshot, the four 261018 extracts are 18,812,042,605 bytes (HEAD, 05:29Z); the
    islands' share is 77,209,152.
  - With `--local 261017`, the us and canada 261017 files must still be on the card. That was not
    checked here, since the card's files are out of bounds. The two island 261017 files
    (77,197,534 bytes, `200` at Geofabrik at 05:29Z) and their `.md5` files would have to be put
    on the card, linked from `sources/osm` as `puerto-rico-261017.osm.pbf` and
    `us-virgin-islands-261017.osm.pbf`, by hand. That is a download, which waits for the owner's go.
  - If their headers carried another snapshot, the run would stop at the same-snapshot check,
    before any build.
  - At the 2026-10-18 Wi-Fi rate (about 0.9 MB/s), the islands take under two minutes, and on the
    tether (about 0.2 MB/s) under seven.
- **Disk.**
  - Free space read by `df` at 05:33Z: NVMe 165,683,728,384 bytes, card 100,409,962,496 bytes.
  - A full 261018 run needs 157,061,743,709 bytes on the NVMe, by the script's own
    `planetiler_need` and `with_margin`. That leaves 8,621,984,675 to spare. Without the islands
    the need would be 156,417,124,499, so they cost 644,619,210 bytes of margin.
  - The card needs 42,624,085,210 bytes for a full download.
  - `--local 261017`: the NVMe needs 157,020,310,761 and the card 23,807,079,981, plus the islands'
    download.
- **No skip.** The published manifest's sequence is `us:2415,canada:4945` (audit). A four-region
  run's string differs, so the skip check does not stop it.

### The USVI floor, proposed, not built

If the owner or planner wants the floor to fit the square, a fourth field on a coverage point,
`name:lon:lat:min-bytes`, would let Charlotte Amalie carry its own floor, for example 50,000,
while the rest keep 100,000. The point name and coordinates stay as pinned. That is a new
capability, and it is left for the planner. As built, a USVI square under 100,000 bytes refuses
the run at validation. It does not publish one.

### Calls made beyond the dispatch

- `regions_ok`, a check on the region list (path syntax, one stem per region), run before the
  network stage.
- A `--basemap` run writes its own `.regions` for the archive it links, so a reused basemap's
  output can be reused again.
- The test's stand-in Geofabrik now serves by URL path, and R1 to R5 are pinned to `us canada`.
  Their checks are unedited.
- `pins.env` no longer quotes the `us.poly` east-edge longitude, which the old comment carried.
  That figure stays in the audit record, and leaving it out keeps the repo's coordinates to the
  pinned points.
- The test fixtures place the islands' nodes on the two coverage points, inside whole-degree
  boxes, and `test_merge_poly.py` uses made-up rectangles. No Geofabrik vertex is committed.
- Read-only extras: Hawaii's HEAD, for the size ratio, and `df` on the two filesystems, which reads
  no file on either.
