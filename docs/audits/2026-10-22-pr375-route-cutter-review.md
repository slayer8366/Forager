# Review of PR #375 (route-cutter): the route-data cutter and its BRouter proof

**Reviewer:** Sorrel Tally (independent reviewer, Fable 5.1; the coder was Hazel on Opus, per -1134). **Written:** 2026-10-22 UTC.
**Reviewed:** branch `route-cutter` at `b50685019088ead9b3f6e567a56a306954e5a40f`, into `pre-main` at `06637240754c1ec69c185cec79a8c370e8bbcf11` (the merge base, checked with `git merge-base`). Revert evidence on `route-cutter-revert-1` at `d36a1040c`. Records read from `records-after-173` at `724051e6e`: dispatch `prompts/preserved/2026-10-21-07.md`, RECORD -1543 to -1547. Hazel's Phase 1 report read on `route-cutter`.
**Not done:** no code written, nothing pushed to `route-cutter`, no GitHub comment.

Each claim below is marked **read** (file:line), **computed** (run or counted by me) or **inferred**. BRouter lines are at v1.7.10, fetched as single small files from `raw.githubusercontent.com` with the user agent `ForagerReviewer/1.0 (Sorrel Tally; read-only; github.com/slayer8366/Forager)`: `PhysicalFile.java`, `OsmFile.java`, `Crc32.java`, `WayLinker.java`, `OsmNode.java`, `NodesCache.java`. `RoutingParamCollector.java` and `RoutingContext.java` were read from the source tarball already on the laptop at `/tmp/brouter-src/brouter-1.7.10` (not fetched again). The release zip was fetched once, by the PR's own `fetch-brouter.sh`, for my local run.

## Verdict

**Approve with notes.** Nothing blocking, nothing I would hold the merge for. The cutter matches BRouter v1.7.10's reader and writer byte for byte where I checked it, the cells are copied and never re-encoded, bad input is refused with a named error, the tests fail when they should (Hazel's revert and two of my own), the two corrected assertions were corrected to the right property rather than weakened, the pins verify against their publishers, and the PR's own CI job ran 27 tests with 0 failures on the head commit. The notes are for the next part (the server route), not this one.

## Mechanism prediction, written before computing

Written after reading the source and the evidence, before running anything:

1. Running the 27 tests locally reproduces 27 passes, the same cut size (103,625 bytes), the same detour costs (9,232 against 10,527) and the same moved-end distance (2,275 m), because the fixture, jar and profiles are hash-pinned and BRouter is deterministic for one data set.
2. My revert A (zero the header's version bits) fails every test that routes on the cut, with BRouter's own "lookup version mismatch", plus the format test's version assertion: about 17 of 27.
3. My revert B (east margin one cell short) fails the exact-cells format test, and no route test, because the edge routes go north and the detour and outside routes go west; nothing routes along the east margin.

**Scored at the end:** all three held. Prediction 2 was 17 exactly; prediction 3 was two format failures, not one (the margin-0 test also compares the east column), and zero route failures.

## 1. The cutter against BRouter v1.7.10's format

### Header (bytes 0 to 199)

- **Read** `PhysicalFile.java:96-107`: 25 big-endian longs; `readVersion = (short)(lv >> 48)`; `fileIndex[i] = lv & 0xffffffffffffL`; the lookup version in long 0 is checked against `lookups.dat` when the caller passes one. **Read** `WayLinker.java:553-563`: the writer puts the minor version in long 1 and the lookup version in the other 24.
- `format.ts:41-50` (`readHeader`) takes `getUint16(i*8)` as the version bits and `getUint16(i*8+2) * 2^32 + getUint32(i*8+4)` as the 48-bit end; `writeHeader` (`:108-117`) inverts that with `Math.floor(ends/2^32)` and `ends % 2^32`. **Matches.** The version bits are carried per long (`cut.ts:99`), which is what keeps long 1's minor version distinct. My revert A confirms BRouter checks this on the cut (`lookups.dat=11 W75_N40.rd5=0`).

### Tiles and cells

- **Read** `OsmFile.java:40-42, 56-58`: `tileIndex = lonMod5 * 5 + latMod5`; `fileOffset = tileIndex > 0 ? index[tileIndex-1] : 200`; a tile with `fileOffset == index[tileIndex]` is empty and nothing more is read. `:63-71`: a non-empty tile's 4,096-byte index is checked against `fileHeaderCrcs[tileIndex]`. `:101-116`: cell `i` runs from `posIdx[i-1]` (or `indexsize` for `i = 0`) to `posIdx[i]`, relative to `fileOffset`; size 0 means empty (`:125-127`). `:120`: `subIdx = latRow * 32 + lonCol`.
- `cut.ts:63-97`: for each tile, `tileStart` is 200 or the previous end (`format.ts:61`), an empty source tile is skipped (`cut.ts:66`), the index is CRC-checked before use (`format.ts:88-89`), and for each wanted row the byte range `[tileIndex[first-1] or 4096, tileIndex[last]]` is read as one contiguous block (`cut.ts:80-82`), which is correct because cells in a row are consecutive in index order (**read** `WayLinker.java:465-526`). The new index gives every unwanted cell the running end (empty) and every wanted cell the source cell's exact size (`cut.ts:79, :83-86`). **No off-by-one found.** I checked `first === 0` (start at 4,096, `OsmFile.java:102`), `tileIndex[last]` as the end of the last wanted cell, the running `cellEnd` starting at 4,096 so the tile's length includes its index, and `position += cellEnd` (`cut.ts:93`) as the absolute advance. The output buffer is sized `position + 113` (`cut.ts:101`) and the footer lands at `header.ends[24]`, which is what `PhysicalFile.java:112` reads.
- **Cells are copied, never re-encoded.** `cut.ts:82` is the only read of cell bytes and it goes straight into `parts`; nothing in `src/rd5/` constructs or alters a cell checksum (**read** all four files, 334 lines). Cell checksums are `Crc32(bytes) ^ 2` written by `WayLinker.java:524`; the cutter leaves them in place. The format test at `format.test.mjs:34-49` compares every wanted cell to the source byte for byte with an independent reader (`rd5-reader.mjs`, which uses zlib's CRC inverted, `:15`, so the cutter's own table is not its own judge).
- A wanted tile whose wanted cells are all empty in the source is written as an empty tile (`cut.ts:89`), which BRouter reads as "no data" (`OsmFile.java:57-58`). Correct.

### Footer and checksums

- **Read** `PhysicalFile.java:109-145`: at `fileIndex[24]`: a long, then the header CRC, then 25 ints, then one optional byte; `len == pos` is the old format; `crcData == fileIndexCrc` means 80 divisions, `(crcData ^ 2) == fileIndexCrc` means 32, anything else is "top index checksum error". `WayLinker.java:535-541` writes exactly that, with `^ microCacheEncoding` on the header CRC and a 0 slot for a tile it never wrote (`:437`).
- `format.ts:64-70, 72-85, 119-127` do the same in the same order. `readFooter` refuses the 80-division form as unsupported, the mismatch as a checksum error, and `writeFooter` writes `Crc32(header) ^ 2`, the 25 index CRCs (0 for untouched tiles, `cut.ts:61`), and the elevation byte. **Matches.**
- **Read** `Crc32.java:9-16`: start `0xFFFFFFFF`, reflected table, no final inversion. `crc32.ts` builds the same table from `0xEDB88320` and starts at `-1`; I **computed** that against zlib in the local run (the format test's `headerCrcOk` and `indexCrcOk` pass with `~zlib.crc32`), so the two agree on real data.

### Positions to cells

- **Read** `RoutingParamCollector.java:73-74, 96-97, 280-281`: `ilon = (int)((lon + 180) * 1e6 + 0.5)`. `cells.ts:39-41` uses `Math.round(degrees * 1e6)` then `floor(/31250)` (`OsmFile.java:48` gives `cellsize = 31250`). For non-negative values `Math.round` is `floor(x + 0.5)`, so they agree. Square naming (`cells.ts:28-37`) matches `NodesCache.java:347-358`. I **computed** the fixture's rectangle to global cells 3390 to 3392 by 4200 to 4201 and tiles 1 and 6, which is what the README says and what the format test asserts (`format.test.mjs:86`).

### Refusals

Explicit, named, and tested: old format (`format.ts:66, :77`), no footer (`:66`), header checksum (`:79`), index checksum (`:89`), backwards offsets (`:56, :95`), bad square name, non-overlapping rectangle, negative margin (`cells.ts:30, :34, :49, :52, :59`). Each has a test in `format.test.mjs:109-141`, and each test failed under the stub with the regex it expected absent (stub JUnit, counted below).

### Two things the cutter does silently (notes, not errors)

- **A footer longer than 113 bytes is accepted up to 4,096 and its extra bytes are dropped** (`format.ts:68`, `writeFooter` writes exactly 113). `PhysicalFile.java:121` says "> is o.k. for future extensions", so BRouter 1.7.10 would ignore those bytes too, and its own writer never produces them. Still, a future format with meaning in those bytes would be cut without a word. A refusal above 113 would follow CLAUDE.md's "explicit unsupported" more literally. Low; no such file exists today.
- **A rectangle that runs past the square is clamped, not refused** (`cells.ts:55-58`). Phase 1 stated this as the design (Q1, "clamps the rectangle to the square"). It means a caller asking for an area that crosses a 5-degree line gets a valid file holding only one square's part of it, and nothing in the return value says so. That is the server route's problem to solve (two squares, two cuts), and it should be in that dispatch's premises.

## 2. The tests can fail

### Failing first (stub)

**Computed** from `docs/navigation/evidence/2026-10-21-route-cutter/01-stub-junit.xml`: 25 testcases, 24 `<failure>` elements. Messages: 19 × `cutRd5: not implemented`, and 5 × "The input did not match the regular expression ... 'Error: cutRd5: not implemented'" (2 `/unsupported/i`, 2 `/checksum/i`, 1 `/does not overlap/`). All 24 belong to the stub. The one pass is "on the full fixture, every route is found and lies where its category says", which routes on the full fixture only and never calls the cutter (`route-proof.test.mjs:55-80`); the file's own comment says it is "not a test of the cutter". Disclosed in -1547. Fine.

### Hazel's revert (margin 1 to 0, `route-cutter-revert-1` at `d36a1040c`)

- The branch differs from `route-cutter` in `src/` by one line: `DEFAULT_MARGIN_CELLS = 0` (`cut.ts:43`), **computed** by `git diff` between the two branches.
- **Computed** from `04-revert-margin0-junit.xml` on that branch: 27 testcases, 9 failures. The run log shows all 27 tests ran and reported, so the edited file loaded (Node has no compile step; a load failure would show as zero tests). The 84-line summary kept on `route-cutter` matches the full log line for line where I compared it.
- Each failure traced to the margin: (1) the exact-cells test, `cell 1:253 is the source's bytes`, a ring cell now empty; (2 to 5) both edge routes, both profiles, deep-equal with longer tracks (10,253 to 10,903 m and 11,107 to 11,451 m), which is what losing the margin cells they use does; (6, 7) the detour route, whose start at lon -74.0832 lies in the margin ring (outside the rectangle's -74.05 but inside the cut's -74.09375 edge), so with no margin it is "from-position not mapped" and "target island"; (8, 9) the two outside routes swap outcomes (island becomes a route, moved end becomes island), consistent with the cut's west edge moving one cell east and a different segment being nearest. None is a failure a different edit would produce.
- The forward change is present at head: `cut.ts:43` reads `DEFAULT_MARGIN_CELLS = 1` on `origin/route-cutter` (**read**). I could not verify Hazel's restore procedure itself, only its result.

### My own revert checks (local, Node 24.21.0, JDK 21.0.12, the pinned jar)

Run in a `git archive` copy of the PR at `/tmp/sorrel-run`, outside any repository; files saved to a copy before editing and restored from that copy, then diffed against `origin/route-cutter` (both intact). Load-error lines in each run log: 0. Counts from the JUnit XML Node wrote.

- **As-is:** 27 tests, 0 failed. Diagnostics identical to the evidence: cut 103,625 bytes; detour cost 9,232 / 7,619 m against 10,527 / 7,213 m; moved end 0 m on the full square, 2,275 m on the cut.
- **Revert A, header version bits zeroed** (`cut.ts:99`, `header.versionBits` replaced by 25 zeros): 27 tests, 17 failed. One format failure ("lookup version bits are carried over unchanged"), sixteen route failures, every one carrying BRouter's own message `lookup version mismatch (old rd5?) lookups.dat=11 W75_N40.rd5=0`. So the route tests fail at BRouter's door, not only at the independent reader's. Note that "BRouter's own integrity check" did **not** fail here: `PhysicalFile.main` runs without a lookup version, so that test does not cover the header's version field. The route tests do.
- **Revert B, east margin one cell short** (`cells.ts:56`, `marginCells - 1` on the east column): 27 tests, 2 failed, both format: `cell 6:225 is the source's bytes` (Buffer(471) expected, empty got) and, under margin 0, `cell 6:256`. **No route test failed.** The edge routes run north, the detour and both outside routes run west. The east and south margins are proven only by the byte-level format test, which is exact and sufficient for the cutter; but a reader of the route proof should not take "edge routes are identical" as covering all four sides.

### The two assertions changed in `78fd19de9`

**Read** the diff of `route-proof.test.mjs` in that commit, and `02-first-real-run.log` (22 pass, 3 fail: exit 143 from the integrity check's output buffer, the detour length, the outside end).

- **Detour.** Before: `cutLength > fullLength`. Observed: 7,213 m on the cut against 7,619 m on the full square, so the prediction was wrong, not the cutter. After: `cost(cut) > cost(full)`, `points differ`, and `every point inside the cut`. BRouter minimises cost (the comment cites `RoutingEngine.java:1708-1775`; I did not re-read that file), and the cut's optimum can only cost at least the full square's. The fixture check already asserts the full route leaves the cut, so strict inequality is the right claim short of a tie. This is a correction to the true property, with one assertion added; it is not weaker. The full-length prediction was wrong on its face: a cut can remove a long cheap path and leave a short steep one.
- **Outside end.** Before: one route asserted "moved end". Observed: `target island detected for section 0`. After: `routes.json` names each outside route's observed default outcome (`observedByDefaultOnCut`), one route per outcome, each asserted exactly (message equality, or moved end > 250 m and inside the cut), and an unknown value fails outright (`route-proof.test.mjs:146-150`). Both outcomes are refused cleanly with dynamic range off, asserted by message. This is changed to what was observed and still bites: under my revert A both outside tests fail on message inequality. It is fixture-specific by nature and says so.
- **The harness fix** (`brouter-cli.mjs:63-66`, `maxBuffer` raised) is a harness bug, not an assertion change.

### The 29 / 15 / 14 against 30 / 28 / 17 / 11 question

The commit message of `78fd19de9`, and the file comment in that commit, say 29 points, 15 moved, 14 island. Commit `2c60c9054` ("made deterministic") replaced the comment with 30 sampled, 28 routed on the full square, 17 moved and 11 island, and committed `probe_outside.py` (seed 5, 30 points) and `probe_outside.out`. **At head `b5068501` the file comment reads 30 / 28 / 17 / 11** (`route-proof.test.mjs:124-126`); only the older commit message carries 29 / 15 / 14. I **counted** `probe_outside.out`: 30 lines; 2 fail on the full square (lines 3 and 5, both island); of the 28 that route on the full square, 17 end `ok` on the cut and 11 read `target island`. The current figures are right, and the sample includes the cases that could falsify the "two outcomes" claim (both appear). The earlier 29 / 15 / 14 came from a non-deterministic sample and is superseded; it is not wrong so much as unreproducible, which is why it was replaced.

## 3. `ci.yml`

- **Computed:** the diff against pre-main has no removed lines; the only hunk starts at line 607, the end of the file, and is the new `route-cutter` job (42 lines, `ci.yml:610-651`, job key at `:615`). `ci.yml` is 651 lines.
- **Pins, verified against their publishers:** `actions/setup-node@949feb24...` is the commit the `v7.1.0` tag points at (`gh api .../git/ref/tags/v7.1.0`, type commit, sha matches). `checkout`, `setup-java`, `upload-artifact` use the same SHAs as the three existing jobs (counted in pre-main's `ci.yml`); I did not re-verify those three tags. Node `24.22.1` exact; the CI log shows it acquired. The BRouter zip's size (6,724,983) and SHA-256 (`023fec3b…01532`) match GitHub's asset digest for `v1.7.10`; both profile blob hashes match the `contents` API at `4d2639af`. `fetch-brouter.sh` checks size, then `sha256sum --check` under `set -euo pipefail`, then each profile's `git hash-object`.
- **The result reaches the PR.** `gh pr view 375 --json statusCheckRollup` lists "Route-data cutter routes the same as the full square", SUCCESS, on the `pull_request` run; `gh pr checks 375` shows it as `pass 32s`. The job has no `needs`, so it runs beside the others and its failure would fail the run. Note: `pre-main` has no branch protection (`gh api .../branches/pre-main/protection` returns "Branch not protected"), so no check is *required* for a merge; that is the existing state for every job, not something this PR changed.
- The summarize step fails on a missing XML, zero tests, any failure, or any skip (`ci.yml:633-647`), and the artifact upload errors on a missing file. Reasonable, and stricter than the app job on skips.

## 4. Scope

- `server/pmtiles-worker/src/index.ts`: untouched (`git diff --stat` empty). `tsconfig.json`: one line, `allowImportingTsExtensions` (valid with `noEmit: true`, which is set).
- Sizes: largest new code file is `route-proof.test.mjs` at 158 lines; `format.ts` 127; `cut.ts` 110. All under 400. Evidence files are logs, not code.
- Positions: every route end in `routes.json` is an OSM node id with coordinates. I spot-checked two against the OSM API with the named user agent: node 1682317709 (41.2742564, -73.9978932) and node 797325730 (41.2588594, -74.1204499) match exactly. The probe's start point is the fixture's node 939277321. Nothing reads as a person's recorded position. The README says the place was chosen for trail density and is outside the Pacific Northwest, as the planner asked.
- ODbL: `test/fixtures/rd5/README.md:5-9` carries the OpenStreetMap attribution, the ODbL 1.0 link, the copyright page, and states the `.rd5` is a derivative database under the same licence. BRouter's MIT is named for the tool, which is downloaded and not distributed. Fixture SHA-256 recomputed by me: `ff6d9d8e…5990`, matching the README and `make-fixture.sh:15`; 152,665 bytes.
- Commits: all six end `Co-Authored-By: Claude <noreply@anthropic.com>` with no model name. Both shell scripts are mode 100755.

## 5. The PR's own CI

- **The `pull_request` run for `b5068501` (39726193032) had not finished when this was written:** lint and the release compile passed (6m16s, 4m42s), the route-cutter job passed (32 s), and "Build, test, publish APK" was still running. I did not wait for it.
- **Route-cutter JUnit, read from the artifacts:** run 39726193032 (`route-cutter-junit`): 27 testcases, 0 failures, 0 skipped. Run 39724652858, a `workflow_dispatch` on the same sha that completed green on all four jobs: the same 27 / 0 / 0, and the job's log prints `Route cutter: 27 tests, 0 failed, 0 skipped`.
- **App suite, from run 39724652858's `unit-test-report` artifact** (704 `TEST-*.xml` files, counted by me): 5,252 tests, 0 failures, 0 errors, 24 skipped. Matches -1547.

## Findings, by severity

Nothing blocking. Nothing I would call should-fix for this PR.

**Notes (for the next dispatch, the server route):**

1. `cells.ts:55-58`: a rectangle past the square is clamped silently; the server route must detect a rectangle that crosses a 5-degree line and cut two files, or refuse. The cutter will not say.
2. `format.ts:68` and `:119-127`: footers between 114 and 4,096 bytes are accepted and their tail dropped. Harmless for every file v1.7.10 writes; a refusal would be more literal to "explicit unsupported".
3. `route-proof.test.mjs:82-94`: the route proof exercises the north margin (edge routes) and the west edge (detour, outside); the east and south margins are covered by the format test only (my revert B). Any future change to the margin arithmetic should expect the format test, not the route tests, to catch it.
4. `brouter-cli.mjs:61-70` with my revert A: BRouter's integrity check does not verify the header's lookup version, so that test is not evidence about the version bits. The route tests are.
5. The Node warning `MODULE_TYPELESS_PACKAGE_JSON` prints on every run (seen in all logs). Adding `"type": "module"` to `package.json` would silence it, but that file is the Worker's and the change was outside this dispatch; the planner's call for later.
6. `package.json` keeps caret ranges, as Hazel's Phase 1 report already noted; not touched, not this PR's.
7. Local run used Node 24.21.0 (the only Node on the laptop, under `~/from-flash/map-style/node/`), not the pinned 24.22.1. The results matched CI's exactly; stated so nobody reads my run as a check of the pin.

## What I could not check

- Hazel's revert-runner procedure (save, edit, restore) beyond its result: the forward change is present at head and the revert branch differs by one source line, which is all the repository can show.
- `RoutingEngine.java` (the cost-minimisation and target-island citations) was not re-read; I took the observed behaviour from the runs, which I reproduced.
- Whether `allowImportingTsExtensions` and `.ts` imports work under wrangler's bundler when `index.ts` later imports these modules; nothing imports them yet.
- Cloudflare's per-request limits for the cutter's 20 to 40 range reads, as Phase 1 also could not.
- The three pre-existing action SHAs' tags (`checkout`, `setup-java`, `upload-artifact`); only `setup-node` was new and verified.
- The `pull_request` run's build job, still in progress at writing.

## One-line answers

- **Does the cutter match BRouter v1.7.10's format?** Yes, at every offset I read; cells are copied verbatim; no off-by-one found.
- **Can the tests fail?** Yes: 24 of 25 under the stub, 9 of 27 under Hazel's margin revert, 17 and 2 of 27 under mine, each failure belonging to its own edit.
- **Were the two assertions weakened?** No; both were corrected to the property BRouter actually has, and both still fail under a broken cut.
- **Which probe figure is current?** 30 sampled, 28 routed on the full square, 17 moved / 11 island, recounted from `probe_outside.out`; 29 / 15 / 14 survives only in commit `78fd19de9`'s message.
- **Is `ci.yml` only the new job, pinned, and reaching the PR?** Yes, yes, yes.
- **Merge?** Approve with notes; the merge itself is the owner's word.
