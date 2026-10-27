# Review: `pi-31-names-stage`, the trail-names build (FNAM v1) in the Pi's weekly build

Sorrel (reviewer, Fable 5.1) for Rowan Waymark, 2026-10-27T05:00Z (2026-10-26 evening PDT). Read-only
review in the worktree `~/.cache/forager-wt/sorrel-pi31`; no code changed in the branch, nothing merged,
nothing run on the Pi or against R2. Network: GitHub only (the Forager-cartography repository for the
dispatch and the spec, and osmium-tool's and libosmium's sources for the memory question).

**Reviewed:** `origin/pi-31-names-stage` at `6cdfaef15`, four commits (`d99f9d2eb`, `c9f6c3c3c`,
`542964537`, `6cdfaef15`), no PR. **Base confirmed:** `git merge-base origin/pi-31-names-stage
origin/pi-build` is `aa14e6eaa`, which is `origin/pi-build`'s head, so the branch sits on current
`pi-build` with no drift. **Files:** eight, all under `map-build/` (`git diff --stat
origin/pi-build..HEAD`): `README.md` (+16), `bin/build-names.sh` (new, 89), `bin/build-usca.sh`
(+54 -2), `pins.env` (+7), `tools/fnam_build.py` (new, 587), `tools/fnam_export.json` (new, 7),
`tools/test_build_names.sh` (new, 175), `tools/test_fnam_build.py` (new, 490).

**Spec read:** the dispatch, `docs/handoffs/2026-10-26-pi-31-names-stage-dispatch.md` in
Forager-cartography (`main` at `92fb5a8`), section "FNAM v1". Aspen's measurements and the two runs:
`docs/audits/2026-10-26-three-builds-work.md` there, sections G4 and "Names for 261024 built". The
Forager repository carries the format only as `fnam_build.py`'s docstring (`tools/fnam_build.py:22-37`).

## Verdict

**Mergeable into `pi-build` as the names build; no blocking finding.** The format as written matches the
dispatch's FNAM v1 field for field (section 1). The weekly build fails loudly, not silently, if the names
step fails, and it fails *after* BRouter (section 2) — whether that is the right trade is a decision for
Aspen and the owner, not a defect, and I set it out below. The memory fix is right in kind and the reason
for the first failure is confirmed from libosmium's source; what is missing is an operating limit in
front of the known cliff (S1). Eight revert checks, run here on a scratch copy, each bite with a
message specific to their own edit (section 4).

## Findings, ranked

### Blocking

None.

### Should-fix

**S1. The export's memory has a cliff at about 268 M nodes and nothing stops the run before it.**
`pins.env:240` pins `USCA_NAMES_EXPORT_INDEX=sparse_mem_array`, used at `bin/build-usca.sh:245-248`.
Read from libosmium (`include/osmium/index/detail/vector_map.hpp`, `VectorBasedSparseMap::set`): the
index is one `std::vector` of `(uint64 id, Location)` pairs, 16 bytes each, grown by `push_back`, so
its capacity doubles: 199.6 M entries sit in a 2^28-entry vector of 4.29 GB, and the moment of the last
doubling (old 2.15 GB and new 4.29 GB both live) is the peak. That is Aspen's 4.2 GB. The next doubling,
at 268,435,456 nodes in the named input, needs 8.6 GB, more than the Pi has. Today's input is 199.6 M
nodes (Aspen's run), 34 % under it; nothing in the branch checks the count against a limit, though the
count is already read and logged at `bin/build-usca.sh:787`, *before* BRouter runs. Two further points
on the figure: (a) `peak_rss.py`'s number is `RUSAGE_CHILDREN` `ru_maxrss` (`tools/peak_rss.py:29`),
the largest single descendant, not the pipeline's sum — the encoder's main process and its three
workers (`USCA_NAMES_JOBS=3`, `pins.env:241`) run alongside osmium, so the pipeline's true peak is
higher than 4.2 GB by the encoder's own RSS, which is in the summary line's `peak_rss_kib`
(`tools/fnam_build.py:464`) and not quoted in Aspen's report; (b) the Pi's 8 GB is shared with the
rest of the Pi. *Suggested:* a pin such as `USCA_NAMES_MAX_INPUT_NODES` (CLAUDE.md: a reported range
describes what is possible, not what is safe; `USCA_BROUTER_MAX_BAND_NODES` at `pins.env:186` is the
pattern), checked at `:787` with a `die` naming the count and the limit, so the stop comes before
BRouter's hours rather than at an OOM kill after them; and `sparse_file_array,<path on the NVMe or
card>` named in `pins.env` as the index to switch to when the input outgrows memory (osmium-tool
`man/osmium-index-types.md`: the file-backed types trade speed for memory; `dense_file_array` is ruled
out, as the ids reach ~12e9).

**S2. The disk check does not count the names step's files, and one of them is unmeasured.** The dispatch
asked (item 5) "if you can see where the check should count it, say where"; neither the README nor
`pins.env` answers. On the NVMe during `names-input` (`bin/build-usca.sh:784-787`) sit, beside
`$routing_in` and all the routing bands: the first filter's output, every `w/highway` way of the
routing input with its nodes (`names_cut`, `:230-231`, `$2.highway.osm.pbf`), then the 2.30 GB named cut.
The highway cut is deleted at `:233` and never measured; it is the whole highway network, named or
not, so plausibly several times the 2.30 GB. After publishing, 0.6 GB of names per kept set
(`USCA_KEEP_GOOD_OUTPUTS=1`, `pins.env:226`, so two sets at peak). A normal run's NVMe check is sized to
Planetiler's peak (`disk_check`, `:456-483`), which is far larger than anything the routing stage
holds, so this is probably covered in practice; a `--basemap` run keeps the 110 GiB floor
(`pins.env:223`). Both are inferences, not measurements. *Suggested:* one `stat -c %s` log line for the
highway cut in `names_cut`, so the next run measures it, and a note in `pins.env`'s disk section.

**S3. A names failure fails the whole weekly build, after BRouter. A decision, not a defect.** The names
stage `die`s on an encode failure (`bin/build-usca.sh:829-830`) and on a check failure
(`:885-886`), and `names_cut`'s failure dies at `:786`. `die` runs `on_exit` (`:117`, `:152-167`):
status `failed` naming the stage, the log kept, this run's `out/` (the finished `.rd5` squares and the
basemap link) moved to `failed-usca/latest`, "never publishable from here", and the previous output and
`current` untouched. So nothing is silent and the fallback is last week's set — but a names failure
costs the week's squares and basemap too, and the squares are hours of BRouter. The dispatch did not
rule on this ("Nothing about the `.rd5` squares may change" is about the files, not the failure path),
and the script's own stated rule is "Both outputs are published together or not at all"
(`:63-66`), which Willow extended to a third output. *The two options, for Aspen and the owner:* (a) keep
it — names and squares must come from one extract (`:778-779`), and once the Worker serves names the app
has no path for a set without them, so a soft failure here would be a silent gap on the phone; or
(b) publish squares and basemap, write the names failure as a post-publish warning (the `warn`
mechanism at `:909-912` exists for steps after publishing), and have the manifest's `names` array
absent — which needs a Worker and app fallback that does not exist yet. I recommend (a), with S1 in
place so the one failure already seen stops before BRouter, not after.

**S4. No reader exists, and the spec lives outside the Forager repository.** Searched `origin/pre-main`,
`origin/pi-build`, `origin/main`, `origin/dev` and `origin/records-after-173` for `FNAM`, `.fnm` and
`names1/`: nothing in `app/` or `server/` (the one hit is a binary `.rd5` fixture). The Worker's manifest
parser reads `run_id`, `osm_timestamp` and `files` only (`server/pmtiles-worker/src/routing/manifest.ts:41-62`
on pre-main), so the `names` array Aspen staged is ignored, as -1709 says. So the "byte for byte"
comparison is encoder against dispatch, not encoder against a reader; the only second implementation is
the test decoder (`tools/test_fnam_build.py:27-88`, written "from the dispatch's FNAM v1"), which agrees
with the encoder on every fixture. Forager owns the reader (Aspen's plan §2; the brief,
`prompts/preserved/2026-10-26-20-aspen-brief.md`), yet the spec is a Forager-cartography handoff and a
Python docstring. *Suggested:* before the reader is dispatched, the FNAM v1 section lands in Forager
(e.g. `docs/plans/` or `map-build/docs/`), with three facts a reader needs that the dispatch states only
in passing or not at all: a way crossing a cell edge appears in both cells with the edge point in both
pieces (so a name can be counted twice across a boundary); records within a cell are in no order;
string and highway indices are per file and not stable across files or cuts.

### Nits

**N1. The dispatch's revert check is not recorded anywhere.** The dispatch asked for one ("break the edge
interpolation ... show which test fails, with its message"); none of the four commit messages, the
README or Aspen's audit records it. I ran eight (section 4); Aspen's record should carry one.

**N2. `names-summary.json` is not kept on failure.** `keep_failed_run` copies `metrics.jsonl versions.txt
bounds.txt coverage.txt` (`bin/build-usca.sh:145`); the names summary (`$run/names-summary.json`,
`:829`) is not in the list, so after a failure in validate or publish the encoder's counts and its own
peak RSS survive only as the one log line at `:831`. Add it to the list.

**N3. `check` is structural only.** `check_file` (`tools/fnam_build.py:485-541`) verifies the header, that
the tables parse up to the directory, and that the cells' spans cover the records exactly; it never
parses a record body, so a body with consistent lengths but a string index past the table, a highway
index past M, or a point count under 2 passes. The test decoder does check these, on fixtures. Fine for
now; worth saying in the README's "Validation checks ..." sentence (`README.md:191-192`) so nobody reads
it as a content check.

**N4. The commit trailers name a model.** All four commits end `Co-Authored-By: Claude Opus 5.5
<noreply@anthropic.com>`; the repository's practice is the line without a model name (RECORD -506,
-510: a recorded slip, disclosed, never amended). Disclose in Aspen's record; do not amend pushed
commits.

**N5. File sizes against the modular-code skill.** `fnam_build.py` is 587 lines and `test_fnam_build.py`
490; the owner's rule for new files is under 400 (RECORD -1490, on pre-main). The encoder splits
naturally at its section comments (squares/encoding helpers, geometry, chunk processing, the `Square`
file writer, `check`). For Aspen's judgment on whether `map-build/` follows the app's rule.

**N6. `build-names.sh` takes its two functions out of `build-usca.sh` by `sed` line ranges**
(`bin/build-names.sh:67`, `/^names_cut() {/,/^}/p`). A reformat of either function body that moves
its closing brace off column 0 breaks the one-off script silently until `test_build_names.sh` runs,
and that test needs osmium (N7). Acceptable, since the test exists; noted so the fragility is known.

**N7. Two of the suites need osmium and could not run here.** `test_build_names.sh` (21 checks) and
`test_build_usca_run.sh` exit "osmium is needed" on this laptop. Aspen reports 21 passing on the Pi; I
did not reproduce that (see "Could not determine").

## 1. The format against the dispatch, and against what the app will read

Encoder against the dispatch's FNAM v1, field by field (encoder lines in `tools/fnam_build.py`):

| Spec | Encoder | Agree |
|---|---|---|
| Header 32 bytes: `FNAM`, u16 version 1, i16 west, i16 south, u8 cells 40, u8 0, i64 Unix s, u32 tables, u32 directory, u32 records; little-endian | `HEADER = struct.Struct("<4sHhhBBqIII")` (`:64`, `assert HEADER.size == 32` `:68`); packed at `:391` in that order; timestamp from `osm_timestamp()` (`:101-105`, ISO or Unix) | yes |
| Strings: varint N, N × (varint length, UTF-8); 1..N, 0 none; names and refs share | `Square.strings` one dict for both (`:346-349`), `encode` writes count then (length, bytes) (`:365-370`) | yes |
| Highway values: varint M, M × (varint length, bytes); 0..M-1 in one byte; M ≤ 256 | `Square.highways` 0-based (`:350-351`), `SystemExit` past 256 (`:352-354`), written after strings (`:365`) | yes |
| Directory 1600 × (u32 offset from records, u32 length); index row×40+col, row 0 south; empty cell length 0 | `DIR_ENTRY = "<II"` (`:65`), one entry per cell in index order (`:375-385`); cell index `(gy % 40) * 40 + gx % 40` (`:310`) with `gy = floor(lat*8)`, equal to `floor((lat-south)*8)*40 + floor((lon-west)*8)` since `south*8` is a multiple of 40; empty cell length 0, offset "where it would start" (`:381-383`) | yes |
| Cell body: varint count; per record varint name, varint ref, u8 highway, varint points ≥ 2; first point zz(x) zz(y) from the cell's SW corner, then zz(dx) zz(dy); 1e-6° | count written at encode (`:377-378`); record fields at `:358-361`; `encode_points` (`:222-243`): corner `gx * 125000` which equals `round((west + col/8) * 1e6)` exactly (1e6/8 = 125000), points `round(x * 1e6)`, zig-zag `(d << 1) ^ (d >> 63)` (`:239-240`), fewer than 2 distinct points → piece dropped (`:231-232`) | yes |
| Content: `highway=*` with a name or a ref, LineStrings; dropped: polygons from closed ways, `area=yes`, `proposed construction abandoned razed disused platform` | export config `fnam_export.json`: `linear_tags: true`, `area_tags: false`, tags `name ref highway area`, no attributes; `--geometry-types=linestring` (`build-usca.sh:245`); encoder drops non-LineString (`:275`), `DROP_HIGHWAY` (`:63`, `:279`), `area=yes` (`:282`), no name and no ref (`:286`) | yes |
| Simplified DP 2 m, local equirectangular frame, before splitting | `simplify` (`:127-173`, `SIMPLIFY_M = 2.0` `:62`), frame scales taken where largest on the line so frame distance ≥ ground distance; called before `split_cells` (`:297-299`) | yes |
| Split exactly at cell edges, interpolated point on the edge; a point on an edge goes north/east (floor) | `split_cells` (`:176-220`): cuts at every eighth-degree line strictly inside each segment, piece assigned by its midpoint's floor (`:209`) | yes |
| Pieces in squares with no `.rd5` dropped; every `.rd5` square gets a file | `wanted` set (`:303-306`, `pieces_outside`); every named square encoded and written (`:446-451`), an empty one with 1600 zero-length entries | yes |
| No bbox (RECORD -1698) | none written | yes |

On closed ways, one fact to carry to the reader: osmium export's rule (osmium-tool `man/osmium-export.md`,
"Closed way") makes a closed way with `area=yes` an area regardless of the config, so it is dropped by
`--geometry-types=linestring` before the encoder sees it, while a closed way without an `area` tag is a
LineString under `linear_tags: true` / `area_tags: false` — roundabouts and loop roads are kept as
lines, which is right for name matching. `test_build_names.sh` exercises this through real osmium on
a closed footway (`w6`, "Loop", kept) and `test_fnam_build.py` through the encoder ("kept closed line",
`:313`, `:317`).

Determinism: `the_same_bytes_whatever_the_jobs_and_chunks` (`test_fnam_build.py:197-209`) shows 1 and
3 jobs with 4 KiB chunks write identical bytes; the encoder takes results in input order (`:437-444`).

**What the app will read:** there is no reader (S4). The spec has no reader-side counterpart to disagree
with; the encoder agrees with the dispatch, and the test decoder, an independent reading of the same
dispatch, agrees with the encoder on every fixture.

## 2. Can the names step break the weekly build?

Yes, deliberately, and loudly (S3). The paths, read at `bin/build-usca.sh`:

- `names-input` (`:784-787`), before `rm -f -- "$routing_in"` (`:788`) and before BRouter: two extra
  `tags-filter` passes on the routing input; a failure `die`s at `:786` with the previous output untouched.
  Cost of a failure here: the Planetiler stage (or the `--basemap` link) is lost with the run.
- `names` (`:828-832`), after BRouter, before validate: `names_encode` runs `osmium export | fnam_build.py`
  under `bash -c 'set -euo pipefail ...'` (`:244-248`), so an OOM-killed osmium (exit 137) fails the
  pipeline even though the encoder, seeing EOF, writes its files and exits 0 — exactly what Aspen's first
  run showed ("187 empty files", loud failure, nothing published). A failure here loses the finished
  squares to `failed-usca/latest`.
- `validate` (`:885-886`): `fnam_build.py check` names each problem; `die` on any.
- Nothing in the names stage writes into `segments/` (`:826-827`, read: it reads `$run/out/segments` for
  the square list and writes `$run/out/names1`), and `touch -d "$osm_ts"` on the `.rd5` files (`:823`)
  precedes it.

Against the owner's principle (every failure has a fallback, logged, never silent): the fallback is the
previous output and `current`, which `on_exit` leaves alone; the status file names the stage; the log
is kept. The silent case the principle guards against is absent. What the design does not have is a
fallback that keeps *this* week's squares when only the names fail — see S3 for the two options.

One more path checked: `build-names.sh` refuses to start while `forager-build-usca.service` is active
(`bin/build-names.sh:33`), removes its temporary directory on any exit (`:62`), and puts it on the card
unless `FNAM_TMP_ROOT` says otherwise (`:29`); its names1 must not exist (`:36`). All four refusals are
tested (`test_build_names.sh:158-173`).

## 3. The memory fix

**Why flex_mem went dense — confirmed from source, not from the pin's comment.** libosmium
`include/osmium/index/map/flex_mem.hpp` (`FlexMem::set_sparse`): once the sparse index holds
`min_dense_entries` = 0xffffff (16.7 M) entries, every new maximum id is tested against
`max_id < entries * 3`, and the index switches to dense if it holds. A PBF's nodes come in id order, and
the US's oldest nodes have small ids, so 16.7 M nodes with ids under about 50 M is enough to flip it; once
dense, each block of 65,536 ids costs 512 KiB when touched, and the named input's ids reach about 12e9
(Aspen) — the index grows until the kernel kills it. 7.4 GB forty seconds in matches that. The pin's
comment (`pins.env:236-239`) gives the right reason in fewer words.

**Is `sparse_mem_array` right for the full input?** Yes for today's input, with the cliff in S1: 16 bytes
per node, no dependence on the id range, 3.2 GB of content in a 4.29 GB vector, peak at the last doubling.
It is also the right *kind*: of the in-memory types, `dense_*` are ruled out by the id range (~96 GB),
`flex_mem` by the switch above, and `sparse_mmap_array` has the same footprint as `sparse_mem_array`
(anonymous mmap instead of `std::vector`, same doubling). The file-backed `sparse_file_array` is the
fallback if the input outgrows memory.

**Is anything else pinned that could go dense?** Read through `pins.env` and the stage commands:
- `USCA_NODEMAP_TYPE=sparsearray` (`pins.env:177`) for Planetiler, memory-mapped on disk — unchanged by
  this branch, not a dense switch.
- `osmium tags-filter` (`names_cut`, `:230-232`; the routing filters, `:749-750`) and `osmium extract -s
  complete_ways` (`:769-770`) keep id *sets*, not location maps; they have no `-i` and no dense/sparse
  choice. Unchanged by this branch except for the two new filter passes, whose peak Aspen measured only
  indirectly (the first run died in the export, not the filters; the second run passed).
- `osmium export` creates two indexes of the pinned type, for positive and negative ids
  (`src/command_export.cpp:620-623` in osmium-tool); the negative one stays empty here.
- The encoder's own memory is bounded by the output it holds (all squares' cell bodies, ~0.6 GB, plus the
  per-square string dicts) and `2 * jobs` pending chunk results of 4 MiB input each (`:439-441`, bounded
  on purpose: "Pool.imap would read all of stdin ahead"). Its peak is in the summary (`peak_rss_kib`) and
  I could not see Aspen's value.

## 4. Do the tests bite?

**Suites run here** (laptop, Python 3, no osmium): `test_fnam_build.py` 21 tests OK;
`test_build_usca_functions.sh` 61 checks OK; `test_build_usca_disk.sh` 95 OK; `test_build_functions.sh`
14 OK; `test_run_record.py` 11 OK; `test_merge_poly.py` and `test_mvt_decode.py` OK.
`test_build_names.sh` and `test_build_usca_run.sh`: not runnable (osmium absent); `test_build_usca_stop.sh`:
needs sudo, not run.

**Revert checks**, eight, each on a scratch copy of `tools/` (the branch's files untouched), restored from a
copy saved before editing and confirmed by sha256 afterwards; each reverted file was `py_compile`d
before its run so no stale result could be read. All 21 tests ran each time.

| Revert | Fails | A message specific to the edit |
|---|---|---|
| R1 no edge interpolation (the dispatch's own: pieces assigned by midpoint, as the prototype did) | 3 | `[(0, 1)] != [(0, 0), (0, 1)]` (one piece, not two); `W80_N40 should hold the line's piece west of lon -75` |
| R2 floor rule inverted (a segment on an edge goes south/west) | 1 | `along an edge goes north and east: (0, 0) != (1, 0)` |
| R3 Douglas-Peucker at 20 m | 3 | `a source point is 18.558 m from the simplified line`; end-to-end `8.976 not <= 2.08` |
| R4 directory offsets omit the cell's count varint | 6 + 1 error | `cell 1's body is 15 bytes but its records end at 5`; `check`: `cell 2 starts at 16, expected 17 (overlap)` |
| R5 header version 2 | 1 | `(b'FNAM', 2, ...) != (b'FNAM', 1, ...)` |
| R6 `platform` kept | 1 | `['drop platform', 'kept area=no', 'kept closed line'] != [...]` |
| R7 zig-zag broken for negative deltas | 2 | decoded points differ by the sign of the delta (`-74949000` for `-74951000`) |
| R8 pieces outside any `.rd5` square kept in the first square | 2 | `['Elsewhere', 'Here'] != ['Here']`; a point `532227 m` from its source |

**Shape of the data.** The unit tests run synthetic GeoJSON through the real command line: hand-placed
lines on known edges, a 1,500-point random walk at hiking step lengths across several cells, 400 random
lines for determinism, 300 for the directory, and a 257-value highway table. The properties are the keys
the export config emits (`name ref highway area`) and the record separator is tested on
(`rs=True`, `:151`). Real osmium output is exercised only by `test_build_names.sh`: two tiny OPL extracts
through the real `tags-filter`, `merge` and `export`, including a closed way, a relation member with no
highway tag, a way present in both extracts, and an extract of another snapshot to be passed over, with
the stage's functions and the one-off script required to write the same bytes (`:155`). No test runs on a
real extract; the only real-scale evidence is Aspen's run (187 files, check passed, W75_N40 at
38,052,137 bytes against the prototype's 38.9 MB). So: the geometry rules are well covered on fixtures
with reverts that bite; the tool chain is covered at toy scale by real osmium where osmium is installed;
the full-scale path has been run once and is not a test.

## 5. Outside the names work

Nothing. All eight files are the names work; the two changed lines in `build-usca.sh` outside the new
blocks are the `validated:` log line (`:888`, adds the `.fnm` count) and the manifest's `--outputs`
(`:896`, adds the `.fnm` files). `pins.env` gains two names pins and no ratio changes. The README's
additions are the stage's paragraph and its two test lines.

## Disclosure

**Confirmed (read or run):** the merge base; every file:line above, read in the worktree at `6cdfaef15`;
the field-by-field format comparison; the 21 unit tests and the five other suites that ran here; the
eight reverts, with compile check and restore verified; flex_mem's switch condition, the sparse array's
`push_back` growth and the 16-byte element (libosmium source, GitHub, master branch — not the exact
version Debian's osmium-tool 1.15.0-1 was built against, `pins.env:38`; these parts of libosmium are
long-stable, but that is an inference); osmium export's closed-way rules and `--geometry-types` (osmium-tool
manual, master); the Worker's manifest parser ignoring `names`; no FNAM reader on any Forager branch
searched.

**Inferred:** the 4.29 GB vector and the 268 M-node cliff (from the growth rule and Aspen's 199.6 M count;
not measured); that the pipeline's true peak exceeds the 4.2 GB figure (from `ru_maxrss` semantics);
the highway cut's size (unmeasured, "several times 2.30 GB" is a guess from the ratio of all highways to
named ones); that a normal run's disk check covers the names intermediates in practice.

**Could not determine:** whether Aspen's 21 `test_build_names.sh` checks pass (no osmium here);
the encoder's own peak RSS on the full input (in the summary line, not quoted in Aspen's audit); whether
the dispatch's verify-before-building item 2 (USVI equivalence of the routing-input cut and the raw-extract
cut) was shown — `build-usca.sh:780-781` and `build-names.sh:6-7` assert it, but I found no record of the
`osmium diff` or byte comparison in the commits, the README or Aspen's audit; whether the dispatch's
revert check was run (N1); the highway cut's size (S2).

**Premises that were wrong:** none in Rowan's brief. One in the branch's own text: `pins.env:238` says
flex_mem "went dense ... since node ids reach about 12e9" — the id range is why dense is *fatal*, not why
the switch happened; the switch is the early-density test above. Harmless to the fix, worth one word.

**Decided beyond scope:** I ran seven reverts beyond the dispatch's one, chose their edits myself, and
ranked S3 as a should-fix decision rather than a defect. I did not run anything on the Pi, did not read
R2, and did not install osmium here.

## Re-check at `932c0a0ee`

Sorrel (Fable 5.1), 2026-10-27T05:16Z (2026-10-26, 22:16 PDT). Read in the worktree
`~/.cache/forager-wt/sorrel-pi31-recheck` at `932c0a0ee`, one commit past `6cdfaef15`; only
`git diff 6cdfaef15 932c0a0ee` reviewed (five files, +41 −7, all under `map-build/`). Nothing changed
in the branch, nothing merged, nothing run on the Pi. Network: GitHub only.

**Verdict: mergeable into `pi-build`.** S1, S2, N2 and N3 are fixed as reported; S3 is left for the
owner, as the commit says; N4 is disclosed and the new commit's trailer carries no model name. One new
should-fix on test coverage (S5), two nits (N8, N9), none blocking.

### 1. Is the limit where it runs before BRouter, and does it die?

Yes to both. `bin/build-usca.sh:787` is `set_stage names-input`; the cut runs at `:789`, the count is
read at `:790`, the check is `:793-794`, and `set_stage brouter` is `:797`. The check is `|| die`, and
`die` (`:117`) logs `FAILED:` and `exit 1`, which runs `on_exit` (status `failed` naming the stage,
previous output and `current` untouched, as section 2 above describes). So an input over the limit
stops the run at the cut, before BRouter's hours, and the `die` message names the count, the limit and
where to look (`pins.env`). The one-off script has the same two lines at `bin/build-names.sh:82-83`,
after its merge and before `names_encode`. `pins.env:255` carries `USCA_NAMES_MAX_INPUT_NODES=260000000`;
both scripts `source "$here/pins.env"` (`build-usca.sh:71`, `build-names.sh:23`) under `set -euo
pipefail`, so a missing pin would itself fail loudly as an unbound variable.

### 2. Is 260 M the right side of the doubling, and does the peak fit the Pi?

**The right side.** The vector's capacity is 2^28 = 268,435,456 entries from the 134,217,729th node on;
the 268,435,457th node doubles it to 2^29 (8.59 GB). 260,000,000 is 3.14 % under the cliff and the
check is `-le`, so exactly 260 M passes and 260,000,001 dies. Arithmetic re-done here, not quoted.

**The Pi.** Raspberry Pi 5, 8 GB (`docs/plans/2026-10-03-own-tiles-survey.md:64`; `pins.env:180`;
`docs/plans/2026-10-04-pi-origin-part1-report.md:39`: `free -h` 7.9 GiB total, 6.7 GiB available idle,
swap 199 MiB). The names stage runs after Planetiler and BRouter have both finished (`:835`), so nothing
else from the build is live beside it.

**The 5.6 GB figure is a bound at today's input, not the peak at the limit.** Two readings of the pin
comment (`pins.env:245-249`), both inferred from the growth rule and Aspen's numbers, not measured:
(a) osmium's 4.2 GB is the doubling moment at node 134 M (old 2.15 GB and the copied 2.15 GB both
live), which comes while the nodes are still being read, before any way reaches the encoder; the
encoder's 1.2 GB peaks at the end of the ways. So "4.2 + 1.2 + workers" adds two maxima that do not
coincide, which is the safe direction. (b) At the limit the picture changes: 260 M × 16 bytes is 4.16 GB
of touched index, about the doubling peak, and it *is* live while the encoder reaches its own peak, which
grows with the input (it holds every square's output: ~0.6 GB of `.fnm` at 199.6 M, so roughly 1.5 GB of
RSS at 260 M if it scales). Pipeline peak at the limit: about 4.3 + 1.5 + 0.14 ≈ 6 GB against 7.9 GiB,
with 199 MiB of swap — fits, with about 1.5-2 GB for the OS and page cache. Not a concern for the
current 199.6 M input (34 % under the limit, headroom ~2.4 GB); worth one clause in the pin comment so
nobody reads 5.6 GB as the figure at 260 M (N9). If the input ever sits near the limit, the
`sparse_file_array` switch the comment names is the right move, and the comment already says not to
raise the limit.

### 3. Do the new tests bite?

**Run here** (laptop, no osmium): `test_fnam_build.py` 21 OK; `test_build_usca_functions.sh` 61 OK;
`test_build_usca_disk.sh` 95 OK; `test_build_functions.sh` 14 OK; `bash -n` on both scripts clean; the
`sed` extraction `build-names.sh:67` relies on still yields `names_cut` and `names_encode`, with the
new `echo` inside `names_cut` (confirmed by `declare -f`). `test_build_names.sh` exits "osmium is
needed", so its 23 checks and Aspen's revert are **not reproduced here**; what follows is a reading of
the test text.

**The node-limit check (`test_build_names.sh:176-182`) is sound for what it tests.** It edits the
*copied* `pins.env` (`$T/tree/map-build/pins.env`, the one `$NAMES` sources), sets the limit to 3,
restores from a copy it saved first (not from git — the rule in CLAUDE.md), and asserts a non-zero exit,
no `names1/` written, and the message with the count and `(3)`. The fixture's named cut has far more
than 3 nodes (`w1` alone has three, `:70-75`), so the limit bites. Aspen's revert — the `build-names.sh`
check removed, the test failing with its own message — is the right revert for *this* check and I have
no reason to doubt it; I could not run it.

**S5 (should-fix, not blocking). The weekly path's copy of the check has no test.** The check S1 asked
for is the one at `build-usca.sh:793-794`, in the script's main body; `test_build_names.sh` can only
reach `build-names.sh`'s separate copy (`:82-83`), and `test_build_usca_functions.sh` extracts named
functions only (`:20`). The two copies are the same text today, read side by side; a later edit to one
(a renamed variable, a changed message) would not be caught, and Aspen's revert says nothing about the
weekly copy. *Suggested:* hoist the two lines into a function in `build-usca.sh` (say
`names_input_ok() { # count }`), call it from both scripts, and add it to the `sed` pattern at
`build-names.sh:67` and `test_build_names.sh:144` — then the one copy is the tested one. Small.

**N8. N2 is untested.** `keep_failed_run`'s test (`test_build_usca_functions.sh:59-67`) writes no
`names-summary.json` into `$run`, so the new entry in the list at `build-usca.sh:145` is exercised by
nothing; the suite's 61 checks are the same 61 as at `6cdfaef15`. One `: > "$run/names-summary.json"`
and one `-f` check would cover it.

**N9. The `-le` test passes an empty or non-numeric count.** Probed here: `[[ "" -le 5 ]]` and
`[[ "abc" -le 5 ]]` are both true (bash evaluates the string as a variable name, giving 0). Under
`set -e` a failing `osmium fileinfo` at `:790` exits the script before the check, so the case needs
`fileinfo` to exit 0 with no number, which I have not seen it do; a guard such as `[[ "$names_nodes" =~
^[0-9]+$ ]] || die ...` closes it for one line. Together with the pin-comment clause from section 2.

**The highway-cut check (`:154-155`)** greps for the string only the new `echo` prints, in both the
one-off log and the stage's output; it is true by construction once the echo exists, which is what it
claims. The `echo` goes to stdout, which `build-usca.sh:111` tees into the log without `log`'s prefix —
the same shape as `basemap_matches`'s messages (`:259`), and the stage subshell in the test defines no
`log`, so `echo` is the right call there.

### 4. Anything else in the diff?

No. The five files: the README sentence for N3 (`README.md:191-192`, one long line); the kept-files
list (`build-usca.sh:145`); the `echo` in `names_cut` (`:232-234`); the `names-input` block
(`:790-794`), whose log line also gains the input's byte size; the matching block in `build-names.sh`
(`:80-83`); `pins.env`'s disk note (`:224-228`), the corrected flex_mem reason (`:245-249`, now the
early-density switch, as section 3 above has it), and the new pin with its comment (`:250-255`); and the
two test checks. No ratio, filter, stage order, or `.rd5` path changes. The commit message ends
`Co-Authored-By: Claude <noreply@anthropic.com>` with no model name.

### Disclosure

**Confirmed (read or run):** every file:line above, in the worktree at `932c0a0ee`; the stage order
and `die`'s path; both scripts' syntax and the `sed` extraction; the four suites that ran here; the
`-le` probe; the cliff arithmetic; the Pi's memory from the three documents cited; the commit trailer.

**Inferred:** that osmium's 4.2 GB peak and the encoder's 1.2 GB do not coincide at 199.6 M, and that
they roughly do at 260 M (from the vector's growth rule, the PBF's node-then-way order and where the
encoder's output accumulates — not measured); the encoder's RSS scaling with the input; the ~6 GB peak
at the limit.

**Could not determine:** whether `test_build_names.sh`'s 23 checks pass and whether Aspen's revert
produced the message claimed (no osmium here); the encoder's RSS on an input near the limit; whether
Debian's libosmium 1.15 grows the sparse array exactly as master does (section 3 above, same caveat).

**Premises that were wrong:** none in Rowan's brief. In Aspen's report, "the measured pipeline peak is
about 5.6 GB" is a sum of two maxima measured separately, not one measurement of the pipeline; the
direction is safe, and the comment says "about", so a wording point (N9), not an error.

**Decided beyond scope:** I ranked S5 as should-fix rather than a nit, because the untested copy is the
one S1 was about; and I did not install osmium here to run the shell suite.
