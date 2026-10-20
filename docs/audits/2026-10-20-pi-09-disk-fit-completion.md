# 2026-10-20 pi-09, completion: a full US + Canada build fits, keeping the last map (not installed)

From the Pi coder on dispatch `2026-10-20-pi-09`. Branch `pi-disk-fit`, from `origin/pi-build`
`cb3307e2`. Phase 1 (premises, prediction, the numbers) is in
`docs/audits/2026-10-20-pi-09-disk-fit-phase1.md`; this records Phase 2. Nothing is installed:
`/opt`, `/etc`, the timer, `/srv/forager-build`'s data, `out-usca` and the real card files were
not written. No index row is added to `docs/audits/README.md` (a serialization point; the planner
records).

## Commits

| Commit | What |
|---|---|
| `de48f2d5` | Phase 1 notes |
| `26a852f9` | Tests first, committed failing |
| `f70c1535` | B, C, D, F, G; `pins.env`; the repo's unit file; README |
| `f20bdba1` | `remove_old_extracts` simplified after its first revert check showed a redundant branch |

`26a852f9`'s message says `test_build_usca_functions.sh` stayed at 37 passing against the old
`curl_resume`. That was wrong: it stopped at `USCA_DOWNLOAD_RETRIES: unbound variable`, the old
function reading the pin this change removes. Corrected in `f70c1535`'s message.

## Failing first (against `cb3307e2`'s script)

- `test_build_usca_disk.sh`: 61 failed, 7 passed. The new functions were undefined (`command not
  found`); the old `curl_resume` made one connection where three were expected. The 7 passes are
  by design: things the change must not remove, and the `set -u` guard the stop test relies on.
- `test_build_usca_run.sh` (the real script end to end): 27 failed, 9 passed. Merge under the
  NVMe's work directory; the old "GB free under" line; downloads into `sources/osm` dying at the
  stand-in's first exit 28; an NVMe stand-in with 1000 bytes free passing the old floor (df -BG
  read it as 1000 GiB) and running to BRouter; no network stage. The 9 passes are unchanged-by-
  design behaviour of `--local --basemap`.
- On the way to green, three faults were the tests', not the script's: a `mountpoint` stand-in
  reading the wrong argument, a `sleep` record lost in a `$(...)` subshell, and an expected card
  need of 10% where the code correctly applies the 5 × 10⁹ minimum (10% of 37.46 × 10⁹ is 3.75 ×
  10⁹). The first run also failed at `planetiler` because the Java stand-in's last `for` returned
  1; fixed before any result was read.

## Revert checks

Runner: copy saved, one edit applied by exact match (must occur once), `bash -n` on the reverted
script (results refused if it does not parse), both new test files run, the script restored from
the saved copy, its sha256 compared with the copy, and the forward line grepped for. Every check
below parsed, restored with a matching sha256, and had its forward line present. The first two
ran on `f70c1535`; the rest on the tree that became `f20bdba1`, which differs only in
`remove_old_extracts`.

| Behaviour | Edit | Failures read |
|---|---|---|
| B: downloads to the card | `curl_resume … "$CARD_OSM/$2"` → `"$link"` | disk: "the extract is written on the card", "md5sum … reads the card file through the link"; run: R3 fails, both extracts not on the card, MD5 not passing (the download landed at the link path and `ln -sfn` replaced it with a dangling link) |
| B: cleanup removes card files | card glob dropped from `remove_old_extracts`'s loop | disk: "an old link and the card file it names are both removed", "an old card file with no link is removed" |
| C: merge on the card | `merged="$card_run/…"` → `"$run/…"` | run: "Planetiler read the merged extract from the card's work directory", "no merged extract was ever under the NVMe's work directory" |
| C: removed after Planetiler | `remove_card_run` after Planetiler → `:` | run: "after Planetiler, the merged extract and its directory are gone from the card" |
| C: removed on failure | `remove_card_run` dropped from `cleanup_scratch` | disk: "cleanup_scratch removes this run's directory on the card" |
| D: checked at the state stage | `run_disk_check "$in_bytes" "$dl_bytes"` → `: …` | run: no per-disk lines at the state stage, R4 not failing at the state stage, an extract downloaded, the short card and unmounted card not caught at the state stage |
| D: refuses when short | `(( free < $3 ))` → `(( free < 0 ))` | disk: six refusal checks (today's NVMe, the shortfall message, short card, one byte under, `--basemap` floor, `--basemap` download); run: R4's four |
| F: restart loop | `while (( have < want ))` → `while (( n < 1 && have < want ))` | disk: six F checks (three connections, whole file, -C - each time, the floor each time, the pause, the cap); run: R3's download fails, both extracts' resume offsets missing |
| G: wait for the network | `net="$(wait_for_network …)" \|\| die …` → `net=""` | run: no network stage before state, R5 not failing at network, no "does not resolve" message, Geofabrik asked |

**The first cleanup revert passed everything** (removing the line that deleted a link's card
target). The loop also walks the card's files, so the target was deleted twice over. That line
was removed as redundant (`f20bdba1`), and the revert above is of the mechanism that remains.

The runner's forward-line check is a `grep -F`; for the `cleanup_scratch` revert its marker had
two lines, which `grep -F` reads as either line, so that check alone was weak there. The sha256
match against the saved copy is what shows the restore.

## Suite (after `f20bdba1`)

| Suite | Count |
|---|---|
| `test_build_usca_disk.sh` (new) | 69 checks |
| `test_build_usca_run.sh` (new) | 36 checks |
| `test_build_usca_functions.sh` | 37 checks |
| `test_build_usca_stop.sh` | 27 checks |
| `test_build_functions.sh` | 14 checks |
| Python unittests | 22 tests OK (5 + 11 + 6 `def test_`, counted in the files) |

No `pi07-test-*` units and no `/tmp/pi07-stop.*` left afterwards (observed).
`test_build_functions.sh` never removes its `mktemp -d` directory (it has no trap): each run leaves
a small `/tmp/tmp.*` with `empty`, `empty.tsv` and `srv`. Five such from today are in `/tmp`; they
were left, since not every one could be tied to this session's runs.

## Measured on the card and the unit's sandbox

In a transient unit with the weekly unit's `ProtectSystem=strict`, `RestrictAddressFamilies` and a
`ReadWritePaths=-` on a test directory under `/mnt/archive` (removed afterwards, observed gone):
writing inside the listed directory worked, writing elsewhere on `/mnt/archive` got "Read-only file
system", a `-` path that does not exist did not stop the unit starting, and `mountpoint -q
/mnt/archive` reported it mounted.

## Untested

- A real download from Geofabrik through the new loop, at either link's speed.
- Planetiler and BRouter on the card-resident merge; the read cost is inferred (Phase 1).
- The publish stage end to end: `remove_old_extracts` is tested as a function only; the run
  harness stops at BRouter.
- The installed unit. `systemd-analyze verify` on the repo copy reported nothing.
- The ratios' week-to-week variation; one run measured.
- A `--basemap` run's routing disk need, which is why it keeps the fixed floor.
- `has_default_route` reads the real `/proc` in the run harness, so R3 to R5 assume this machine
  has a default route (it did).

## Noted for Puerto Rico and USVI (a later dispatch)

`USCA_REGIONS` names are used as path parts: `$USCA_GEOFABRIK_BASE/$s-updates/state.txt`,
`$SRC/aux/$s.poly`, and `remove_old_extracts`'s globs `$SRC/osm/$s-[0-9]*` and `$CARD_OSM/$s-[0-9]*`.
A nested name such as `us/puerto-rico` would make the globs look under `sources/osm/us/`, where no
link is made (`file[$s]` is the bare dated name from Geofabrik's Location header), so that region's
old extracts would never be cleaned. Inferred from reading; not tried.
