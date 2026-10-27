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
| The five squares with no `.bef` | PosUnifier gives their nodes no height, silently, by design; documented at `pins.env:265-266`; the check expects 182 files, which is 187 − 5 | by design, documented |

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

## Quick look at `ea67332db`

Sorrel (Fable 5.1) for Rowan, 2026-10-27T16:23Z (09:23 PDT). Fresh worktree
`~/.cache/forager-wt/sorrel-pi32-quick` at `ea67332db`; `git diff 6b991fac5 ea67332db` is one commit, four
files, +105 −18, same base (`merge-base` with `origin/pi-build` is still `20f75d063`). Aspen's fixes for S1,
N1, N3, N4 and N5 read against the script, the suites, and BRouter `v1.7.10`'s `PosUnifier.java`,
`WayLinker.java` and `MapCreatorBase.java` fetched again from GitHub. Nothing changed in the branch,
nothing merged.

### Verdict

**Not mergeable as it stands; one line to fix, then it is.** The S1 check itself is right and bites
behaviourally (R10). The *log line beside it* kills the weekly run: `bin/build-usca.sh:874` fails under the
script's own `set -euo pipefail` (`:66`) for every band in which every square has heights, which is most of
the ten bands, so the 2026-11-01 run would end at `brouter` after the first such band's PosUnifier, exit 2,
with no `FAILED:` line saying why (B1). The fix is one `find` in place of one `ls`, plus a run-suite case
with no unsuffixed tile, which is the case that would have caught it. Everything else in the diff does what
Aspen reports.

### Findings

**B1 (blocking). `bin/build-usca.sh:874` exits the script when a band has no unsuffixed tile.** The line is

```
none_here="$(cd "$br/unodes55" && ls -- *.u5d 2>/dev/null | sed 's/\.u5d$//' | tr '\n' ' ')"
```

When no `<SQUARE>.u5d` exists the glob stays literal, `ls` exits 2, `pipefail` (`:66`) makes the pipeline
exit 2, the assignment takes that status, and `errexit` ends the script: no `die`, no `log`, only `on_exit`'s
"run failed with exit 2" (`:160-168`) with the stage at `brouter`. The only bands with an unsuffixed tile are
those whose input (band plus the 1° margin, `pins.env:197`, `:828`) holds one of the five `USCA_BEF_NONE`
squares *and* has nodes there; `-125:-120`, `-120:-110`, `-110:-100` and `-90:-85` have none, so the run
cannot pass band two. **Reproduced**, not inferred: the script's own lines `873-875` and its
`heights_read_ok`, extracted with `sed` and run under `set -euo pipefail` against `unodes55/` holding only
`W125_N45.u5d_3` and `W125_N40.u5d_3` — exit 2, nothing printed; the same against `W80_N25.u5d` plus
`W85_N30.u5d_3` — the log line and exit 0. The line before it, `:873`, is fine: `heights_read_ok` uses a
`for` glob with `[[ -e ]] || continue` (`:433-434`) and returns 0 on an empty folder.

*Why the tests passed:* the run suite's PosUnifier stub always writes `W80_N25.u5d`
(`tools/test_build_usca_run.sh:183`), so R10's two runs both have an unsuffixed tile, and the functions
suite's "an empty tile folder passes" (`tools/test_build_usca_functions.sh:112`) exercises the function, not
line `874`. Aspen's Pi check ran PosUnifier, not the script around it. Same family as CLAUDE.md's "a check
that passes because it never saw the data that could fail it": the one sample the stub produces is the one
band shape that cannot trip the line.

*Fix:* `none_here="$(find "$br/unodes55" -maxdepth 1 -name '*.u5d' -printf '%f\n' | sed 's/\.u5d$//' |
tr '\n' ' ')"` (`find` exits 0 with no match; same idiom as `bef_store_ok`, `:418`), or have
`heights_read_ok` print the allowed unsuffixed squares on success and log that. Either way, add an R10 case
in which the stub writes only `_3` tiles and assert the run reaches WayLinker: that is the normal band.

**N6. R11 is a negative text check.** `tools/test_build_usca_run.sh:418` asserts only that "no elevation
store" is absent from the log, so a `--fetch-only` run that failed for any other reason passes it. R3
(`:265`) and R6 (`:323`) do assert `rc -eq 0` and stage `fetch-only` for a `--fetch-only` run with the
store present; R11 should assert the same with the store absent, since that is its claim.

**N7. The names comparison makes the real `SHA256SUMS` load-bearing in a way the old check was not.**
`bef_store_ok` now requires the sorted name list in `SHA256SUMS` to equal the sorted non-empty `*.bef` list
(`:417-419`). A line listing anything other than a `.bef` (a README, a checksum of the list itself) passed
the old count check and now fails the run at `state`. I cannot read the Pi; the G8 audit's install step 3
ran the *previous* `bef_store_ok` on the real store. **Before merging, run the function from `ea67332db`
against `/mnt/archive/forager-build/elevation/bef3-20261027` on the Pi, quick and `full`.** One command.

**N8. A square outside the 187 with nodes would stop the run, loudly.** `heights_read_ok` allows only
`USCA_BEF_NONE`; a node tile for any other square with no `.bef` fails the band and names the square
(`:438`). The 187-square count of past runs and 182 + 5 = 187 say no such tile occurs today. If one ever
does (an extract clipping a way into a landless block), the run stops with the square's name and the
remedy is a pin edit. That is the right direction to fail in; noted so the first such stop is not read as
a store fault.

### The four questions

**1. Does `heights_read_ok` run after every band, and die before WayLinker writes that band? Does the
listing see every square of the band?** Yes on both. The call is at `:873`, inside the band loop, after
PosUnifier (`:870-871`) and before WayLinker (`:876-877`); `$br` is `rm -rf`'d and remade per band
(`:862-863`), so `unodes55/` holds exactly this band's tiles, margin squares included. Against my
PosUnifier reading: `nodeFileStart` calls `resetElevationRaster()` (`PosUnifier.java:109-110`, `:310-315`
clears the map and `lastSrtmRaster`), so each tile's suffix comes from that tile's own lookups;
`nodeFileEnd` renames to `_3` iff `lastSrtmRaster != null` (`:139-146`); `fileFromTemplate` keeps the base
name (`MapCreatorBase.java:84-88`), so the tile is the square. Mid-tile loss of the card: the raster is
decoded whole on the first node (`:217-220`) and cached, so later nodes read memory; a loss *during* the
decode throws, prints `**** ERROR reading`, then dereferences null at `:222` and Java exits non-zero.
**Can an empty or missing `unodes55` pass it?** Yes, both (the glob matches nothing; the suite asserts
the empty case). That is safe: WayLinker links each way tile to the node tile of the same name
(`WayLinker.java:216-223`), so no tile means no square built, and the directory is made at `:863` each
band so "missing" is unreachable. Still inferred, as in the review: that the cutter's 5° node tiles sit
on the same grid as PosUnifier's blocks.

**2. Does R10 bite behaviourally?** Yes, for the drop case. The real script runs; the cutter stub moves
the store after the `brouter`-stage quick check (`test_build_usca_run.sh:176`); the PosUnifier stub writes
`W125_N45.u5d` when the store is gone (`:182`); the script's own `heights_read_ok` and `die` fire; the
WayLinker stub's `link.rec` is asserted absent and the `FAILED:` line is read from the log (`:411`).
Remove `:873` and the run reaches WayLinker and R10 fails. What R10 proves is the script's half; BRouter's
half (that the suffix means what the stub says) rests on the source reading above and Aspen's observation
on the Pi (`.u5d_3` with the store, `.u5d` without, USVI extract), which I cannot re-run. What R10 does not
see is a band with no unsuffixed tile — B1.

**3. The N1 gate.** Correct. `:662` wraps the `state`-stage check in `fetch_only -eq 0`; a `--fetch-only`
run exits at `:714-718` before `merge`, so `:853` (the quick check), `:871` (`$USCA_BEF_DIR` to PosUnifier)
and `:873` (`$USCA_BEF_NONE`) are never reached, and `disk_check` does nothing for it (`:516-517`). Every
other `USCA_BEF` use is on a path a `--fetch-only` run leaves before. `--local` and `--basemap` still go
through `:663`, as `pins.env:261-262` now says.

**4. Suites and the rest of the diff.** Run here from the worktree: functions **83 ok**, disk **95 ok**,
build_functions **14 ok**, matching Aspen's counts. Not run: the run suite (R10, R11) and names, which
need `osmium` (absent here; `command -v` confirmed), and stop. **Four revert checks** of my own on a scratch
copy of `map-build/`, each restored from a copy saved before editing and `cmp`'d against the worktree after,
`bash -n` before each run; the functions suite stops at its first failure:

| Revert | Check that failed |
|---|---|
| A. names comparison replaced by `:` | `a listed .bef gone fails the quick check, naming it` |
| B. `bad+=` never recorded | `a tile without heights that isn't allowed fails, naming the square` |
| C. `mountpoint` branch deleted | `a missing store with the card not mounted says so (N5)` |
| D. failing file names dropped from the full check | `a changed .bef passes the quick check but fails the full one, naming the file (N3)` |

Each message is specific to its edit. Aspen's "three revert checks" are not named in the report and I did
not try to match them. Nothing else in the diff: `pins.env` adds `USCA_BEF_NONE` and reworks the comment;
the harness's `STUB_BROUTER`/`STUB_DROP_STORE` are R10 plumbing. N3's capture is right: `have="$(…)" ||`
keeps `sha256sum -c --quiet`'s stdout (the `FAILED` lines) while taking its exit status. N4's `comm -3` is
fed sorted lists and strips the tab. N5's `mountpoint -q --` passed here with `/` and a non-mount.

### Disclosure

**Confirmed (read or run)**: the diff and every line cited; B1 by running the script's own lines; three
suites with counts; four revert checks with their messages and byte-identical restores; BRouter
`v1.7.10` `PosUnifier.java`, `WayLinker.java`, `MapCreatorBase.java` as fetched today.

**Inferred**: which bands have an unsuffixed tile (depends on whether the extract has nodes in the five
`USCA_BEF_NONE` squares, e.g. Midway in `W180_N25`; with or without, band two `-125:-120` has none); the
cutter's tile grid, as before.

**Could not determine**: anything on the Pi — the real `SHA256SUMS`'s contents against the new names check
(N7), R10 and R11's results (no `osmium`), Aspen's marker observation, Aspen's three revert checks.

**Premises that were wrong**: Aspen's report has R10 as the behavioural test of S1, which it is for the
drop case; the premise that it covers the normal band is what B1 falsifies. My own review's suggested
check (S1, "list `unodes55/*.u5d` … with a `log` line naming the unsuffixed tiles") is the shape that
was built, and I did not say the `ls` had to survive an empty match under `pipefail`; the suggestion was
mine, so part of this is on the review.

**Decided beyond scope**: none new. The headroom and `elevationType` notes from the review stand.

## Re-look at `8a9331fa6`

Sorrel (Fable 5.1) for Rowan, 2026-10-27T16:35Z (09:35 PDT). Fresh worktree
`~/.cache/forager-wt/sorrel-pi32-b1` at `8a9331fa6`; `git diff ea67332db 8a9331fa6` is one commit, two
files, +12 −3: `bin/build-usca.sh` (+3 −1) and `tools/test_build_usca_run.sh` (+9 −2). Same base
(`merge-base` with `origin/pi-build` is still `20f75d063`). Aspen's note on the fix read from
Forager-cartography `docs/audits/2026-10-27-g8-elevation-in-weekly.md` ("Sorrel's quick look: B1 found
and fixed"), fetched through the GitHub API. Nothing changed in the branch, nothing merged, nothing run
on the Pi.

### Verdict

**Mergeable into `pi-build` for the 2026-11-01 run.** B1 is fixed, reproduced here on the script's own
lines in a clean bash: the old line exits 2 on a folder of `_3` tiles and on an empty one, the new line
reaches the next command on both, and on the mixed folder both lines log the same thing (question 1).
The new R10 case is the right shape and would fail with the old line back, on the one check Aspen names
(question 2, inferred from the code since the run suite cannot run here). N6 is correct (question 3).
Nothing else in the diff. One thing to carry into the install, not a finding against the branch: N7 was
answered on the backup copy because the pinned path does not exist yet, and the install's step 4 runs
the same check on the pinned path (below).

### Findings

None blocking, none should-fix. Notes:

**N9. The run harness still cannot run here** (`tools/test_build_usca_run.sh:26` requires `osmium`,
absent on this machine, `command -v` confirmed), so R10's new case and R11's new assertion are read and
simulated at line level, not run. Aspen's counts (functions 83, run 70, names 23, disk 95) are taken on
report; the 70 reconciles with the file — 63 literal `check "` lines plus the loops at `:267-272`
(2 regions × 4) and `:328-331` (2 islands × 3). Functions suite run here from the worktree: **83 ok**,
unchanged by the diff, as expected.

**N10. `find` on a missing `unodes55/` exits 1 and ends the run under `pipefail`, like the old `ls`.**
Run here: the new line against a path with no `unodes55/` prints find's error and exits 1. Unreachable in
the script, because `mkdir -p "$br"/{…,unodes55,…}` at `bin/build-usca.sh:863` runs each band before
PosUnifier; noted so nobody moves `:876` above `:863`.

**N11. The three pre-existing `ls` uses in the script are not B1's shape, checked rather than assumed.**
`:958` substitutes `ls … | wc -l` inside a `log` argument (errexit does not act on a substitution that is
one word of a command with a name, and `ls` on an existing directory exits 0 even when empty); `:987`
feeds `mapfile` from a process substitution, whose status errexit never sees. Both run here under
`set -euo pipefail` against an empty directory: the log line prints "0 .rd5 squares", `mapfile` yields
an empty array, exit 0. The other `ls` uses in `map-build/` are in the test suites' own assertions
(`test_build_names.sh:99,106,174`, `test_build_functions.sh:32,50`, `test_build_usca_run.sh:167,221,348`,
`test_build_usca_stop.sh:94`), each on a directory, not a glob, or already `2>/dev/null || …`-guarded.
Inside G8: `:417` is `sed` on a file and `:418` is `find`; neither can exit non-zero on an empty match.

**N7, carried.** Aspen ran the new `bef_store_ok` against `/mnt/storage/elevation/bef3`, "the verified
copy of what the install will put at `USCA_BEF_DIR` (that path doesn't exist yet)" (G8 audit). That
answers the list half of N7 — `SHA256SUMS` hashes to the pin and every line is `<sha>  srtm_X_Y.bef` —
and the 13 s full check is plausible against the store's 1,926,598,934 bytes (G7 audit `:156`; about
148 MB/s). What it cannot answer is the directory half: that `bef3-20261027` on the card will hold
exactly those 182 non-empty `.bef` and no other. The install's step 4 ("run `bef_store_ok … full` on the
installed path", G8 audit) is the check for that, and it must run the function from the *installed*
`8a9331fa6` script, which `install-map-build.sh` in step 3 provides. If step 4 is skipped, the run's own
`state`-stage check (`:663`) still stops a bad store before anything expensive — loudly, with the
names — so the cost of skipping it is a missed week, not a wrong map.

### The five questions

**1. Is B1 fixed under `set -euo pipefail`?** Yes. The reproduction from the quick look, rerun on this
head: `heights_read_ok` (`:427-439`) extracted by its function markers, lines `:873` and `:876-877`
extracted by number, both the old (`ea67332db:873-875`) and the new, run by `env -i PATH=/usr/bin:/bin
bash --norc --noprofile` under `set -euo pipefail` with stub `log`/`die` and a sentinel echo after:

| Folder | Old `ls` line | New `find` line |
|---|---|---|
| only `W125_N45.u5d_3`, `W125_N40.u5d_3` | exit 2, nothing printed | sentinel reached, exit 0, no log line |
| `W80_N25.u5d` + `W85_N30.u5d_3` | log "for W80_N25 ", sentinel, exit 0 | the same |
| empty | exit 2, nothing printed | sentinel reached, exit 0 |
| `W125_N45.u5d` + `W80_N25.u5d` (disallowed tile) | — | `die` from `:873`, naming `W125_N45`, before `:876` |
| no `unodes55/` at all | — | find's error, exit 1 (N10, unreachable) |

The driver's `USCA_BEF_NONE` was a stand-in list containing `W80_N25`, not the pin's
(`pins.env:274`: `W170_N20 W180_N25 W80_N25 W95_N15 W95_N20`); the test is of the pipeline's exit
status, which does not depend on the list. `find -name '*.u5d'` matches the whole name, so `.u5d_3`
tiles are not listed (the only-`_3` row above shows no log line); `-maxdepth 1` includes the start
directory at depth 0, whose name cannot match. The added `sort` makes the log's order stable;
`-printf` is GNU, as `:418` already is, and that line has run on the Pi (N7). Neither the script nor
the harness sets `nullglob` or `failglob` (`grep shopt`: none), which fits Aspen's note that an
interactive profile hid the failure once: under `nullglob` the old `ls --` with no operand would have
listed the directory and reported `_3` tiles as having no heights — wrong, not fatal — while the build
runs under plain bash. **Any other pipeline of the same shape?** None in the diff; none in the G8 lines;
the script's other `ls` uses are a different shape and were run, not reasoned (N11).

**2. Does the new R10 case bite?** By reading, yes, and Aspen's "69 of 70" is the number the code
predicts. The stub (`test_build_usca_run.sh:182-184`) with `STUB_ALL_HEIGHTS=1` and the store present
writes `W125_N45.u5d_3` and nothing else — the only-`_3` row above, the band shape B1 missed. The check
(`:412-413`) wants `link.rec` present, no "no heights, as pinned" line, and no "run failed with exit 2".
With the old line back the script exits 2 at `:874` before WayLinker, so `link.rec` is absent and
`on_exit` (`:164`) logs "run failed with exit 2": two of three conditions fail. The WayLinker stub exits
1 by design (`:186`, "each run stops itself"), so a passing run logs "run failed with exit 1", and the
third condition is specific to B1's exit code — GNU `ls` exits 2 for a missing operand, confirmed by the
reproduction. The other two R10 runs are untouched by the revert: `r10-ok` writes `W80_N25.u5d` (mixed
row, passes either way) and `r10-drop` dies at `:873` before the line. So exactly one check fails,
matching Aspen's count. `$T/bef-r10` is present during `r10-all` (made at `:400`, moved only by
`r10-drop` at `:415`). I could not run it (N9).

**3. Is N6 correct?** Yes. `build` sets `rc` (`:217-220`) and `new_srv r11` sets `$S` (`:199`);
`status_field` (`:193`) reads the JSON; a `--fetch-only` run writes `--state ok --stage fetch-only` and
exits 0 at `bin/build-usca.sh:714-718`, which is what R3 (`:265`) and R6 (`:323`) already assert with
the store present. The old negative text check is kept as a fourth condition. N1's gate (`:662`) is
unchanged.

**4. Anything else in the diff?** The comment at `:874-875` says why `find`; the stub's comment at
`:183`. Nothing else. `bash -n` on both files: clean. shellcheck is absent here.

**5. Mergeable for 2026-11-01?** Yes. The install sequence in the G8 audit (merge `--no-ff`, copy the
store to `USCA_BEF_DIR`, `install-map-build.sh`, then `bef_store_ok … full` on the installed path) covers
what this review cannot see; step 4 is the one that closes N7.

### Disclosure

**Confirmed (read or run)**: the diff and every line cited; B1 fixed, by running the script's own
extracted lines, old and new, in a clean bash over five folder shapes; the `:958`/`:987` shapes under
`set -euo pipefail`; the functions suite (83 ok) and `bash -n`; the check count's reconciliation to 70;
the merge-base; the G7/G8 audits as fetched today (store bytes, install steps, Aspen's N7 note).

**Inferred**: that the new R10 case fails with the old line back, and fails alone — from the stub, the
check's three conditions, `on_exit`'s text and the reproduction's exit code, not from running the suite;
that 13 s is a plausible full check, from 1.93 GB and a USB 3 drive on the Pi.

**Could not determine**: anything on the Pi — the run, names and disk suites' results, Aspen's revert
check, the backup copy's check; whether `bef3-20261027` on the card will hold exactly the 182 listed
`.bef` (the install's step 4 answers it).

**Premises that were wrong**: none found in Aspen's report. One of my own: the quick look named
`/mnt/archive/forager-build/elevation/bef3-20261027` as the path to run N7 against; it does not exist
yet, so Aspen ran the backup copy, which was the available thing to run. The pinned path gets its check
at install.

**Decided beyond scope**: none. N10 and N11 are notes, not asks.
