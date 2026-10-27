# Review: `pi-32-elevation`, the weekly route build with 3″ heights (G8)

Sorrel (reviewer, Fable 5.1) for Rowan Waymark, 2026-10-27T13:55Z (06:55 PDT). Read-only review in the
worktree `~/.cache/forager-wt/sorrel-pi32`; no code changed in the branch, nothing merged, nothing run on
the Pi. Network: GitHub only — the Forager-cartography repository for Aspen's G7, G8 and pilot audits, and
BRouter's own source at tag `v1.7.10` (the version `pins.env:55` pins) for what PosUnifier and WayLinker do
with and without a `.bef`.

**Reviewed:** `origin/pi-32-elevation` at `6b991fac5`, one commit, no PR. **Base confirmed:**
`git merge-base 6b991fac5 20f75d063` is `20f75d063`, `pi-build`'s head, so the branch sits on current
`pi-build` with no drift. **Files:** four, all under `map-build/` (`git diff --stat 20f75d063 6b991fac5`):
`bin/build-usca.sh` (+29 −3), `pins.env` (+13), `tools/test_build_usca_functions.sh` (+22 −2),
`tools/test_build_usca_run.sh` (+21). The ruling: RECORD −1720, "G8 from Sunday, app later" (quoted from
Rowan's dispatch; the records branch was not read here). The app keeps `consider_elevation` off.

**Author's report read:** Forager-cartography `docs/audits/2026-10-27-g8-elevation-in-weekly.md` (fetched
through the GitHub API), with `2026-10-26-elevation-g7.md` (the store) and `2026-10-26-elevation-pilot.md`
(G6, the two-square pilot) behind it.

## Verdict

**Mergeable into `pi-build` for the 2026-11-01 run; no blocking finding.** The change is small and does
what it says: PosUnifier's last argument is the pinned store (`bin/build-usca.sh:845`), and a run cannot
start, or reach BRouter, with the store missing, swapped or altered (`:638`, `:827`). The pins are the
right three (`pins.env:268-270`): a dated directory, the list's own hash, and the count, so a rebuilt
store cannot be picked up without a pin edit. The tests bite — five revert checks run here, each failing
on the check written for that edit (section 3). One silent path remains, inside the routing stage
itself, and it is cheap to close (S1); it is a should-fix rather than a blocker because it needs the card
to leave *after* two checks have passed, and because the fix can land before Sunday or after without
changing anything the owner ruled on.

## Findings, ranked

### Blocking

None.

### Should-fix

**S1. A square can still be built without heights silently, if the store goes away during the routing
stage.** The quick check at `bin/build-usca.sh:827` runs once, before the first band; PosUnifier then
runs ten times (`USCA_ROUTING_BANDS`, `pins.env:196`) over the stage's hours. For every node PosUnifier
asks for the block's file and, if `f.exists()` is false, gives the node no height and prints nothing
(BRouter `PosUnifier.java:128-130` and `:214-246` at `v1.7.10`: the only log line, `*** reading:`, is on
the branch where the file *is* found). WayLinker then writes the square with `elevationType` 3 whether or
not a raster was read (`WayLinker.java:217-223` sets 3 first and only changes it to 1; `:541` writes it),
so the published `.rd5` carries no mark that it has no heights. If the card is pulled or drops out after
`:827` — Aspen's own G8 audit: "The owner has moved drives during runs before" — every remaining square is
built flat, the names stage runs, validation passes (it checks size and roads, not heights), and the set is
published. The app would, once `consider_elevation` is on, route over flat ground with no error anywhere.
This is the owner's "every failure has a fallback, and is logged, never silent" principle; the fallback
here is silent.

There is a marker already on disk that makes the check one `ls`. At the end of each node tile PosUnifier
renames the tile to `<SQUARE>.u5d_3` when it had a raster for it, and leaves it `<SQUARE>.u5d` when it
had none (`PosUnifier.java:142-146`; `_1` for 1″ rasters, not this store). The tile and the `.rd5` share
their base name (`MapCreatorBase.java:84-88`, used for both at `WayLinker.java:218-223` and `:433`), and a
5° node tile is exactly one 5° block (`PosUnifier.java:197-198`: the block index is `ilon/5e6` and
`ilat/5e6` on the same 5° grid the cutter tiles on — inferred for the cutter, read for PosUnifier).
*Suggested:* after each band's PosUnifier at `:844-845`, list `unodes55/*.u5d` (unsuffixed) and `die`
unless each one's square is in a pinned `USCA_BEF_NONE="W170_N20 W180_N25 W80_N25 W95_N15 W95_N20"`,
with a `log` line naming the unsuffixed tiles that were allowed. That turns the comment at
`pins.env:265-266` ("of the 187 squares … have none") into a check the build runs, closes the
mid-stage window, and catches a `.bef` that is present but unreadable to the build user (PosUnifier
would crash on that — `:222` dereferences a null raster after printing `**** ERROR reading` — so that
case is loud already; the check costs nothing there). It also gives `test_build_usca_run.sh` something
behavioural to assert for the brouter stage (N2).

### Nits

**N1. `--fetch-only` now needs the store, and the report does not say so.** The check at
`bin/build-usca.sh:638` is unconditional; `--fetch-only` (`:13`) downloads and checks extracts and then
stops, building nothing, and its disk check deliberately leaves the card out when nothing is left to
download (`:493-494`, `:502-505`). The G8 audit reports the behaviour change for `--basemap` only.
Harmless as a fail-early for a hand-run tool, but either gate the check on `fetch_only -eq 0` (the
comment at `:636-637`, "before the downloads", reads as if downloads are the thing being protected, which
for `--fetch-only` they are not) or report it. The same applies to `--local` from extracts that are not
on the card: `disk_check` never calls `card_ready` for it (`dl_bytes` stays 0 through `:621-633`, the
`--local` branch never adding to it), so `pins.env:261-262`'s "the card, which the build already needs
(card_ready)" is true of a normal run, not of every run.

**N2. The brouter-stage check has no behavioural test; the state-stage one does.** R9
(`tools/test_build_usca_run.sh:375-387`) drives the real script against a missing and a tampered store and
reads the stage and the `FAILED:` line from the log — a real check. The brouter-stage call at `:827` is
asserted only by text (`tools/test_build_usca_functions.sh:104-105`: a `grep -c` of the call equals 2 and
the `full)" || die` form exists), as is the PosUnifier argument (`:102-103`). Those are "the string
appears in the file" proxies (CLAUDE.md, Testing); they bite on edits to the text, as the revert checks
below show, and would also bite on a harmless re-wording. Aspen's own revert record shows the brouter
check working (the run "still dies later, at the brouter-stage check") but no test asserts that. With S1
in place, a stub BRouter cutter that removes the store would be the natural R10.

**N3. The full-check failure does not name the file.** `bin/build-usca.sh:414` discards `sha256sum -c`'s
output and reports "a .bef in DIR does not match its SHA256SUMS". On the Pi, which file is the first
question. `sha256sum -c --quiet` prints exactly the failing lines; capturing them into the message costs
one variable.

**N4. The quick check compares counts, not names.** `:409-412` requires `wc -l SHA256SUMS == N` and
`find … '*.bef' -size +0 | wc -l == N`. A listed `.bef` missing and an unlisted one present passes the
quick check (the full check catches it: `sha256sum -c` fails on a missing file). A `diff` of the two
name lists is as cheap and strictly stronger; it would also make the pinned count redundant rather than
load-bearing.

**N5. A missing store in a `--basemap`/`--local` run says "no elevation store at …", not "is the card
mounted".** For a normal run `card_ready` (`:394-397`) has already said so. For the runs in N1 the first
message a reader sees is the store's path. Adding `(is $USCA_CARD_MOUNT mounted?)` when
`mountpoint -q "$USCA_CARD_MOUNT"` fails would save the first guess.

## The seven questions

### 1. Fallbacks — can a week build squares without heights silently?

Every path, read from the script:

| Path | What happens | Silent? |
|---|---|---|
| Normal weekly run, store present and whole | full hash at `state` (`:638`), quick check at `brouter` (`:827`), PosUnifier reads `$USCA_BEF_DIR` (`:845`) | no |
| Store missing, swapped, or short at the start | `die` at `state` with the reason, before any download (`:638`; R9) | no |
| A `.bef` altered before the run | `sha256sum -c` fails in the full check; `die` at `state` (`:414`; R9) | no |
| Card gone between `state` and `brouter` (the day of downloads, Planetiler's hours) | `-d` fails; `die` at `brouter` (`:827`, `:405`) | no |
| Card gone *during* the routing stage | PosUnifier finds no file, gives no height, prints nothing; WayLinker writes the square; validation and publish pass | **yes — S1** |
| A `.bef` present but unreadable or corrupt | PosUnifier prints `**** ERROR reading` and then throws on a null raster (`PosUnifier.java:216-223`); Java exits non-zero; `set -e` ends the run | no (loud, if by accident) |
| A resume from a later stage | there is none: the script has four flags (`:76-85`) and every run starts at `state` | n/a |
| `--basemap` | same checks; now needs the card (Aspen reports this) | no |
| `--fetch-only`, `--local` | same checks, unreported (N1) | no |
| The five squares with no `.bef` | PosUnifier gives their nodes no height, silently, by design; documented at `pins.env:263-264`; the check expects 182 files, which is 187 − 5 | by design, documented |

On the five squares: PosUnifier's block lookup is `srtm_<lon>_<lat>` from the node's own position
(`PosUnifier.java:196-198`, `:249-253`), so a square with no file simply gets `Short.MIN_VALUE` for every
node; nothing in the `.rd5` says so (section S1). The five are named at `pins.env:265-266`. The check's 182
is right: G7 built one `.bef` per 5° block with land data and counted 182 of the 187 squares (G7 audit,
"182 `.bef`, 1,926,598,934 bytes"; "5 squares have no `.bef` … W170_N20, W180_N25, W80_N25, W95_N15,
W95_N20"). I did not re-derive from a
polygon that none of the five holds US or Canadian land; W80_N25 (−80 to −75, 25 to 30) is the one
nearest to it, and Florida's east coast lies just west of −80.0 (Palm Beach is about −80.03), so it
holds the Bahamas and sea. Within a `.bef`, a nodata cell also yields no height silently
(`ElevationRaster.java:51-56`); G7 records all 4,403 cells finished after retries, so this is G7's
completeness, not G8's check.

### 2. The full hash at `state`

Right cost, right place. It reads 1.93 GB from the card and writes nothing; Aspen's "about a minute" is
inferred and plausible for a Pi 5's card reader, and it is spent before a day of downloads rather than
after Planetiler's hours (`:636-637`). The quick re-check at `brouter` does re-check something real: the
directory (so, the card), the list's identity and length, and that 182 non-empty `.bef` are present
(`:405-412`). It does not re-hash the contents, which is the right trade — a content change in those
hours with the names and sizes intact is not a failure this project has seen. What it does not cover is
the window after it (S1).

### 3. The tests

**Run here**, from the worktree: `test_build_usca_functions.sh` **76 checks ok** (9 new, as reported);
`test_build_usca_disk.sh` **95 ok**; `test_build_functions.sh` **14 ok**. **Not run here:**
`test_build_usca_run.sh` and `test_build_names.sh` need `osmium`, which this laptop does not have (both
exit at their own `command -v` guard); `test_build_usca_stop.sh` needs sudo and systemd-run. So R9 — the
one behavioural test of G8 — is Aspen's report (65 checks on the Pi), read, not re-run.

**Revert checks**, run on a scratch copy of `map-build/` under the scratchpad, each restored from a copy
saved before editing (never from git), `bash -n` passed before each run, and the restored file compared
byte-for-byte with the branch head after each. The functions suite exits on its first failure, so each
run names one check:

| Revert | Check that failed |
|---|---|
| A. PosUnifier back on `no-elevation` | `PosUnifier reads the pinned store, and nothing builds into an empty no-elevation folder` |
| B. State-stage full check removed | `the store is checked in full at the state stage and quickly before BRouter` |
| C. `full` branch disabled (`if false`) | `a changed .bef passes the quick check but fails the full one` |
| D. `.bef` count check dropped | `a listed .bef gone fails the quick check` |
| E. Brouter-stage quick check removed | `the store is checked in full at the state stage and quickly before BRouter` |

Each message belongs to its own edit. A and B are the two Aspen ran; C and D show `bef_store_ok`'s
own branches are each held by a check, not only its call sites. B and E fail on the same text check (the
`grep -c … == 2`), which is what N2 is about: the text check cannot tell which call went.

### 4. The pins

`USCA_BEF_DIR` is dated (`bef3-20261027`), `USCA_BEF_SUMS_SHA256` is the list's own hash, and
`USCA_BEF_FILES` its length (`pins.env:268-270`), with the comment "A new store means new pins, not an
edit" (`:267`). A store rebuilt into a new directory is not seen at all; one rebuilt in place changes the
list and fails `:408`; one that keeps the old list and swaps files fails the full check. Pinned well
enough that nothing new is picked up unannounced. **Not verifiable from here:** that the real
`SHA256SUMS` hashes to `36b05da7…` and has exactly 182 lines, all `.bef`; the G8 audit's install step 1
checks both on the Pi before the merge, and step 3 runs `bef_store_ok … full` on the installed path. The
store is under `ReadWritePaths=-/mnt/archive/forager-build` (`systemd/forager-build-usca.service:43`),
so the unit *can* write there; the install step's "root-owned and readable" is what keeps the build user
from altering its own evidence, and should be kept.

### 5. The `--basemap` change

Necessary. `--basemap` reuses the map and still runs the routing stage (`:733-743` then `:763` on), so
BRouter needs the store either way; there is no basemap-only mode that skips routing. The alternative —
the store on the NVMe — is rejected in `pins.env:261-263` for the right reason (`/mnt/storage` is
`nofail` and does not remount; the card is already a hard requirement for the normal run). What could
skip the check is `--fetch-only` (N1).

### 6. Disk

Nothing in the diff changes what the start check measures: the full hash writes nothing, the
`no-elevation` directory it drops was empty, and the store sits on the card where `disk_check` reads free
space with the store already present (`:509`), so it is counted by being there. The growth is in the
output: +10-14 % of the 1.56 GB of squares, about 0.2 GB per kept set (pilot audit, "+14.3 %" on
W125_N45), written on the NVMe after Planetiler's temp is gone, far from the start check's need. The
3.5 GB headroom is Aspen's figure from the Pi (160.75 GB free against 157.2 GB needed; the arithmetic
checks: 18.82 GB × (5.61 + 1.82 + 0.16) × 1.10 = 157.1 GB) and I cannot read the Pi. It is thin: it is
0.42 GB of input growth (3.5 ÷ 8.35) before the start check refuses a run, and about 1.2 GB of it is
Aspen's own scratch awaiting the owner's word. Not G8's doing; recorded under "beyond scope".

### 7. Anything outside G8 in the diff

Nothing. All 85 added lines are the function, its two calls, the pins, and their tests; `S_R1_LOG="$L"`
(`test_build_usca_run.sh:218`) is G8 test plumbing for the R9 log check.

## Disclosure

**Confirmed (read)**: the diff, the base, every `build-usca.sh` line cited, `pins.env`, the unit file;
BRouter `v1.7.10` `PosUnifier.java`, `WayLinker.java`, `MapCreatorBase.java`, `ElevationRaster.java`,
`NodeIterator.java` as fetched from GitHub; three suites run here with their counts; five revert checks
with their messages and the restored file compared to the branch head; the G7, G8 and pilot audits in
Forager-cartography.

**Inferred**: that the cutter's 5° node tiles are on the same grid as PosUnifier's 5° blocks (read for
PosUnifier's index math, not for the cutter — `NodeCutter5`'s source was not at the path I tried); the
"about a minute" for the full hash; that the card dropping out mid-stage is the realistic way S1 happens
(Aspen's statement that drives have moved during runs is the only evidence). W80_N25 holding no US land
is from G7 plus a coastline figure from memory, not a polygon.

**Could not determine**: the real store's `SHA256SUMS` hash and line count (pin unverifiable off the Pi);
R9's 65 checks (no `osmium` here); the names suite; the stop suite; the 160.75 GB free and the 14.8 h last
week (the Pi's log); how long the routing stage runs, which is S1's window; whether the Pi's
`/mnt/archive` mount is itself `nofail`.

**Premises that were wrong**: one half-wrong. The dispatch and the pins comment say the card is already
needed by "the build"; that is true of the normal run and false of `--basemap`, `--local` and
`--fetch-only`, whose disk checks never call `card_ready` (N1). Aspen reports `--basemap`'s change and
not the other two. The dispatch's "a resume from a later stage" has no such path to examine; every run
starts at `state`.

**Decided beyond scope**: (a) the start-check headroom. 3.5 GB is 0.42 GB of input growth; the pins'
own measured inputs went 18.73 GB (`pins.env:205`, two regions) to 18.82 GB (Aspen, four regions) in a
week, which mixes growth with the two islands and so gives no clean rate. The 1.2 GB of scratch in the
G8 audit is the cheapest relief and is the owner's call; Rowan may want it asked before Sunday. (b) The
`.rd5`'s `elevationType` byte says 3 whether or not heights were read, so when the app's
`consider_elevation` change comes, it cannot tell a flat square from a heighted one from the file; S1's
check at build time is the only place that distinction exists. Noted for the app change's dispatch.
