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
