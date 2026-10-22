# Route-data cutter, Phase 1: read, answer, stop

**Dispatch:** `prompts/preserved/2026-10-21-07.md` (records-after-173 at `702ed20e1`), intent RECORD 2026-09-28-1543. **By:** Hazel Cairnwright (coder, Opus). **Written:** 2026-10-22T04:20Z.
**Base checked:** `origin/pre-main` = `06637240754c1ec69c185cec79a8c370e8bbcf11` and `origin/engine-spike` = `5d34f4f11ba44e85ae1a31d5b9116e21a5012e7f` by `git ls-remote` at the start. Both match the dispatch; neither has moved. Branch `route-cutter` is cut from that pre-main.
**Read:** the dispatch, CLAUDE.md, `docs/process/coder-profile.md`, `.claude/skills/modular-code/SKILL.md`, Juniper's report `docs/navigation/2026-10-21-route-data-report.md` and its evidence folder (records-after-173), the spike's `brouter-lib/build.gradle.kts` and `tools/clip_region.py` (engine-spike), `pins.env` and `build-orwa.sh` (pi-build), `ci.yml` and `server/pmtiles-worker/` (pre-main).
**Fetched:** single GETs, user agent `ForagerCoder/1.0 (Hazel Cairnwright; read-only; github.com/slayer8366/Forager)`, from `raw.githubusercontent.com/abrensch/brouter/v1.7.10/`: `PhysicalFile.java`, `OsmFile.java`, `NodesCache.java`, `Rd5DiffTool.java`, `WaypointMatcherImpl.java` (brouter-mapaccess), `WayLinker.java` (brouter-map-creator), `Crc32.java` (brouter-util), `MicroCache.java`, `MicroCache2.java`, `DataBuffers.java` (brouter-codec), `RoutingEngine.java`, `RoutingContext.java` (brouter-core), `BRouter.java` (brouter-server); and `hiking-mountain.brf`, `trekking.brf` from `misc/profiles2` at `4d2639af`. Their git blob hashes match the Pi's pins (`pi:map-build/pins.env:76`). All under 90 KB each, kept outside the repository. The release zip was **not** downloaded.

Nothing was built, run, uploaded or put on a phone. No cutter code was written. Each claim is marked **read** (file:line, BRouter files at v1.7.10), **computed**, or **inferred**.

## In one paragraph

A `.rd5` is a 200-byte header of 25 tile end-offsets, then each non-empty one-degree tile as a 4,096-byte cell index followed by its cells, then a footer of a timestamp, a header checksum and 25 tile-index checksums. Each cell carries its own checksum and decodes on its own, so a cutter copies the wanted cells' bytes unchanged and rewrites only the header, the touched tiles' indexes and the footer (Q1). The cutter belongs in TypeScript beside the Worker, as small modules that read through a range-reader interface, because the Worker is the only server that answers the app and it cannot run the JVM (Q2). The proof needs BRouter itself, which is Java, so it cannot run in today's CI unchanged: the smallest change is one new parallel job in `ci.yml` with Java 21, a pinned Node, the pinned BRouter release jar, and Node's own test runner writing JUnit XML. I estimate one to two runner-minutes per run and no added wall-clock time (Q3). The fixture is a sparse square built by BRouter's own map creator from a small OpenStreetMap clip, about 0.1 to 0.3 MB, with an ODbL notice beside it (Q4). **One premise in both the dispatch and Juniper's report is wrong, and it changes what the proof can assert.** BRouter's profiles leave "dynamic range" waypoint matching on by default. So a route with an end outside the cut does not fail: it snaps that end to the nearest way inside the cut, up to 60 km away, and returns a route. And a route whose ends are inside but whose best path leaves the cut returns a different, longer route. Neither case fails "cleanly" (Q5, and "Premises that were wrong").

## Q1. The format, and what a cutter must rewrite

All of this is read from BRouter v1.7.10 source. Integers are big-endian, because the writer is a `DataOutputStream` (`WayLinker.java:434`, `createOutStream`) and the reader is `ByteDataReader`.

**Header, bytes 0 to 199.** There are 25 longs, one per one-degree tile (`PhysicalFile.java:96-107`).
- The tile index is `lonMod5 * 5 + latMod5` (`OsmFile.java:40-42`; the writer agrees at `WayLinker.java:426` and `:448`, with `nLatSegs = 5`).
- The low 48 bits of long *i* are the **absolute end offset** of tile *i* (`PhysicalFile.java:106`). Tile *i* starts at the end of tile *i-1*, and tile 0 starts at byte 200 (`OsmFile.java:56`). So tiles are contiguous and in index order.
- A tile whose start equals its end is empty and is never read (`OsmFile.java:57-58`).
- The top 16 bits carry the lookup version, except in long 1, which carries the minor version (`WayLinker.java:557-559`). The reader rejects a file whose long-0 version differs from the profile's `lookups.dat` (`PhysicalFile.java:102-104`).

**Tile, at its start offset.** The tile opens with a 4,096-byte cell index: 1,024 ints, one per 1/32-degree cell, numbered `latRow * 32 + lonCol` (`OsmFile.java:49-50`, `:73-76`, `:120`; the writer at `WayLinker.java:458`).
- Each int is the cell's **cumulative end position relative to the tile start**. The first cell starts at 4,096, right after the index (`OsmFile.java:101-103`, `getPosIdx(-1) = indexsize`; the writer at `WayLinker.java:463`, `:510`, `:514`).
- A cell's size is `end(i) - end(i-1)`. Zero means the cell is empty, and the reader returns `MicroCache.emptyCache()` (`OsmFile.java:105-116`, `:125-127`).
- The cells follow the index back to back, in index order (`WayLinker.java:519-526`).

**Cell.** A cell is its encoded bytes plus a trailing 4-byte checksum, `Crc32(bytes) ^ 2` (`WayLinker.java:510`, `:524`). The reader checks that checksum only when the decoder did not consume the buffer exactly (`OsmFile.java:145-155`). A cell decodes **on its own**:
- its coder contexts are built fresh per cell (`MicroCache2.java:41-47`);
- node positions are relative to a base the reader computes from the cell's row and column, not from anything stored elsewhere (`MicroCache2.java:36-38`, `:59-63`).

So a cell copied byte for byte to the same row and column in the same square decodes the same.

**Footer, at the end offset of tile 24** (`PhysicalFile.java:109-145`; the writer at `WayLinker.java:533-541`):
- a long creation timestamp;
- an int `Crc32(header 200 bytes) ^ 2`. The `^ 2` marks the 32-division format, and a value without it means the old 80-division format (`PhysicalFile.java:130-137`);
- 25 ints, each `Crc32` of that tile's 4,096-byte cell index. The reader checks these only for non-empty tiles (`OsmFile.java:66-71`), and the writer leaves 0 for empty tiles (`WayLinker.java:437`, `:518`);
- one byte for the elevation type, read if present (`PhysicalFile.java:117-119`, `:142-144`).

A file that ends exactly at the end of tile 24 is the old format with no footer (`PhysicalFile.java:115`).

**The checksum** is BRouter's own `Crc32`: the standard reflected table, initial value `0xFFFFFFFF`, and **no final inversion** (`Crc32.java:9-16`, `:18-51`). It is therefore the bitwise NOT of the usual CRC-32. **Computed:** I checked the identity in Python against `zlib.crc32` on a test string, and the table entries 1, 2 and 255 match `0xEDB88320`'s.

**What a cutter must write**, given the source file and a set of wanted cells:
1. **Cells:** each wanted cell's bytes from the source, **verbatim, including its own checksum**. No re-encoding and no new cell checksums.
2. **Each tile with at least one wanted cell:** a new 1,024-int index in which unwanted cells repeat the previous end, then the wanted cells in index order. Its `Crc32` goes into the footer slot for that tile.
3. **Each tile with no wanted cell:** nothing. Its end offset equals the previous tile's end, and its footer slot is 0.
4. **Header:** the new absolute end offsets, with each long's top 16 bits copied from the source (version in all but long 1, minor version in long 1).
5. **Footer:** the timestamp (I propose copying the source's, so a cut traces to its build; this is a call to accept or reject), `Crc32(new header) ^ 2`, the 25 index checksums, and the elevation byte copied.
6. **Refuse explicitly**, never guess, when the source is not the 32-division format, has no footer, or has a header or index checksum that does not verify. That follows CLAUDE.md's "unsupported, never a fabricated value".

A rectangle in a square becomes cells through `ilon = (lon + 180) * 1e6` and `ilat = (lat + 90) * 1e6`, with cell = `ilon / 31250` (`OsmFile.java:48`, `cellsize = 1000000 / divisor`; `NodesCache.java:159-160`). The cutter clamps the rectangle to the square, because a neighbouring square is a separate file and a separate cut.

**Reading cost.** Cells in one row of a tile are contiguous in the file, so a rectangle needs one byte range per cell row per tile. **Computed:** a 24 km area at 45.5 N is 17 rows (Juniper's script output, `route_data_sizes.out`). That comes to about 17 to 34 cell-range reads, plus one to four index reads, plus the header and footer, in three dependent rounds.

**Juniper's reading checked.** Everything in her Q3 format bullets matches the source at the lines she gave: `PhysicalFile.java:19`, `:99-107`, `:131-134`; `OsmFile.java:48-50`, `:56-58`, `:80-82`, `:105-116`, `:118-157`, `:125-127`; `WayLinker.java:63-64`, `:449`, `:468`; `NodesCache.java:157-196`, `:213-235`, `:292-345`, `:347-375`. Two refinements, not errors:
- the "recomputed indexes and CRCs" of her option C means only the header checksum and the touched tiles' index checksums, because cell checksums are copied;
- she did not mention the version and minor-version bits in the header longs, which a cutter must carry over.

Her one claim that does not hold is in Q5 below.

## Q2. Where the cutter lives

**Recommendation:** a TypeScript module inside `server/pmtiles-worker/src/rd5/`, written against an interface that reads a byte range, `(offset, length) => Promise<Uint8Array>`. It returns the cut file's bytes. It knows nothing of R2, HTTP or Cloudflare types.
- The later server route wraps R2's `get(key, { range })` in that interface.
- The test wraps a local file.

**Why not the JVM.** The owner chose a server-side cut on request. The only server the app talks to is the Cloudflare Worker, which is TypeScript and cannot run a JVM library (`server/pmtiles-worker/package.json`, `wrangler.toml`). The Pi serves nothing to users (`pi:map-build/README.md:13`, `:32-33`, via Juniper Q2). A Kotlin or Java cutter could only be called by a server the project does not have, so it would be the wrong answer the dispatch warns about.

**Why this needs no owner choice.** I read "the server" as the existing Worker, because that is the only server route the owner's option text ("cutter + server route + phone") can mean today. I have not stopped on it. If the planner knows of a different intended server runtime, that would be the stop.

**Until the server route exists, the cutter has no production caller.** That is by the dispatch's design, and it is stated here so nobody later reads its tests as evidence that a path runs in production.

**modular-code's four questions:**
1. **Files touched, and their size now.**
   - None of the existing source. `index.ts` (469 lines), `shared.ts` (41) and `worker.test.mjs` (187) are not touched.
   - `server/pmtiles-worker/tsconfig.json` (16 lines): plus one line, `"allowImportingTsExtensions": true`. The modules import each other as `./crc32.ts` so that Node can run them directly. This is a call beyond the dispatch's "new files"; the alternative is one 300-line file.
   - `.github/workflows/ci.yml` (609 lines): plus about 45 lines, only if the planner approves Q3's change. That makes about 655 lines, under 800.
2. **Files that go over 800 lines:** none.
3. **New files, one job each** (sizes are estimates):
   - `src/rd5/crc32.ts` (~40): BRouter's checksum.
   - `src/rd5/format.ts` (~150): read and validate the header, footer and a tile index; write a header, index and footer; explicit "unsupported" errors.
   - `src/rd5/cells.ts` (~80): turns a rectangle plus a margin in cells into the wanted cell rows per tile, clamped to the square. Names a square from a position, using the same rule as `NodesCache.java:347-358`.
   - `src/rd5/cut.ts` (~150): the cutter, over the range-reader interface.
   - `test/rd5/format.test.mjs` (~200): format-level tests with no BRouter. Checksums verify, empty tiles stay empty, each copied cell is byte-equal to its source, and unsupported input is refused.
   - `test/rd5/route-proof.test.mjs` (~200): runs BRouter on the full fixture and on the cut, and compares.
   - `test/rd5/brouter-cli.mjs` (~80): spawns the pinned jar and parses its output.
   - `test/rd5/fetch-brouter.sh` (~40): downloads the pinned zip and two profile files, and checks their hashes.
   - `test/fixtures/rd5/`: the fixture `.rd5`, `routes.json`, `NOTICE.md` and `make-fixture.sh` (Q4).
4. **Anything that must stay in place for order reasons:** nothing. There is no UI, Back handler or effect.

**Not checked:** Cloudflare's per-request CPU and subrequest limits for 20 to 40 range reads. Those limits are outside this dispatch's sources. The cut itself is byte copying plus checksums over at most four 4 KB indexes and 200 bytes of header, so it is light (inferred). The read count matters for the later server part.

## Q3. How the proof runs, and where

**What it needs:**
- BRouter v1.7.10 to route. The project already pins its release zip by URL, size and GitHub's SHA-256 (`pi:map-build/pins.env:54-60`): `brouter-1.7.10.zip`, 6,724,983 bytes, `023fec3b…01532`. The zip's `brouter-1.7.10-all.jar` holds the command-line router `btools.server.BRouter` (`pins.env:63`, a class the Pi checks for; the Pi runs `brouter-$VERSION-all.jar`, `build-orwa.sh:226`). I propose the proof use that same jar rather than the spike's `brouter-lib`. Compiling from source needs a source checkout and a Gradle project, which CI does not have. The jar is already the project's pinned artefact, and it is the same engine version the Pi built the squares with. Not committed: downloaded and hash-checked each run.
- `lookups.dat` and `hiking-mountain.brf` from `misc/profiles2` at `4d2639af`, checked by git blob hash as the Pi does (`pins.env:75-76`). The spike's default profile is `hiking-mountain` (`SpikeActivity.kt:39`, engine-spike).
- Java 21, which is what the Pi runs (`pins.env:16`) and what CI already sets up.
- Node with built-in TypeScript type stripping, to run the cutter's `.ts` without an npm install.
- The fixture and its routes (Q4).

**How it routes.** The jar's four-argument mode is `java -cp brouter-1.7.10-all.jar btools.server.BRouter <segmentDir> <profileDir> "lonlats=…&profile=hiking-mountain&format=geojson&…" <outFile>` (`BRouter.java:16-62`).
- An `&` must follow `lonlats` (`:24-26`).
- It **always exits 0**, printing any error message on stdout (`:56-62`). So the harness reads stdout and the output file, never the exit code.
- Each route runs twice, once with the full fixture in `<segmentDir>` and once with the cut.

**Can it run in CI unchanged? No.**
- CI runs only `./gradlew testDebugUnitTest` and reads `app/build/test-results/testDebugUnitTest/TEST-*.xml` (`ci.yml:176-219`, `:237`).
- It has no Node or npm step (grep of `ci.yml`: none). The planner's reading is confirmed.
- Putting the proof in the app's unit tests would mean a Kotlin copy of the cutter (not the one the server calls) and a BRouter jar on the app's test classpath, which is a change to `app/build.gradle.kts`. I rejected that.

**The smallest change.** One new job in `ci.yml`, with no `needs:`, so it runs beside the three existing jobs. Proposed text, for the planner to approve; the setup-node pin is filled in at Phase 2 the way the other actions are pinned:

```yaml
  route-cutter:
    name: Route-data cutter routes the same as the full square
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5.7.0
        with: { distribution: temurin, java-version: "21" }
      - uses: actions/setup-node@<pinned sha> # exact Node 24.x, chosen at Phase 2
        with: { node-version: "<exact 24.x>" }
      - name: Fetch the pinned BRouter jar and profiles
        run: server/pmtiles-worker/test/rd5/fetch-brouter.sh "$RUNNER_TEMP/brouter"
      - name: Run the cutter's tests
        env: { BROUTER_HOME: "${{ runner.temp }}/brouter" }
        run: >
          node --test --test-reporter=spec --test-reporter-destination=stdout
          --test-reporter=junit --test-reporter-destination=route-cutter-junit.xml
          server/pmtiles-worker/test/rd5/
      - name: Summarize from the JUnit XML
        if: always()
        run: # python as in the build job: totals per suite; fail if the file is missing or has 0 tests
      - uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        if: always()
        with: { name: route-cutter-junit, path: route-cutter-junit.xml }
```

**Cost (estimated, not measured).**
- Checkout, Java and Node setup: about 30 to 60 s.
- Zip download (6.7 MB): a few seconds.
- About 12 BRouter runs at roughly one JVM start each: about 15 to 30 s.
- Total: one to two runner-minutes per push, in parallel, so no added wall-clock time against the 45-minute build job.
- The repository is public (`gh repo view`: PUBLIC). As I understand GitHub's terms, standard runners are free for public repositories; I did not read that this session.

**Alternative:** a separate workflow file filtered to `server/pmtiles-worker/**`. It would not touch `ci.yml` and would run only when the cutter changes. I don't recommend it: pre-main expects every merge to carry its own recorded result (`ci.yml:9-11`, `:39-46`), and a path-filtered check is absent rather than green on merges that don't touch it. The planner's call.

**The Worker's existing `worker.test.mjs` stays out of CI.** It needs `npm ci` plus a wrangler build (`package.json` scripts). Adding it is not in scope. Separately, `package.json` uses caret ranges (`^4.3.0` and others) against CLAUDE.md's pinning rule. There is a lockfile, and I did not touch either.

## Q4. The fixture

**What it is.** A sparse `.rd5`, named for its real 5-degree square, built by BRouter v1.7.10's own map creator from a small OpenStreetMap clip. It is built with the Pi's exact three steps and the same pinned jar and profiles (`pi:map-build/bin/build-orwa.sh:227-236`: `OsmFastCutter`, `PosUnifier` with an empty elevation directory, `WayLinker`). The cutter never makes it, so the proof compares the cutter's output against an input the cutter did not produce.
- **Source data:** a clip about 20 x 20 km. Ways are kept whole, as in the spike's `clip_region.py` (engine-spike, `tools/clip_region.py:1-3`, `:17-22`).
- **Where the data comes from:** one bounded OpenStreetMap download for that box, from the Overpass API or a small regional extract, decided at Phase 2. The spike's method read a whole state file, which is a bulk download (189 s, 2.2 GB of memory, `engine-spike-report.md:190`).
- **The area:** public land with a dense trail network, crossing a **one-degree** line but not a five-degree line, so the cut spans two tiles of one square. It is chosen by trail density only, not near any person's places. I propose the planner name it. A non-PNW area avoids any hint about the owner's region.
- **The cut rectangle under test:** about 5 x 5 km inside the clip, crossing the one-degree line, with a margin of one cell.
- **Size (estimated):** about 54 cells at Juniper's forest rate of 1,296 bytes per cell (computed from `route_data_sizes.out`), plus the tails of whole ways. That is about 0.1 to 0.3 MB, and the cut is about a third of that. Both are measured and reported at Phase 2. I propose a hard ceiling of 0.5 MB for the committed fixture.
- **Committed beside it:**
  - `make-fixture.sh`: the recipe, including the bounding box, the download, the pinned jar and profile hashes, and the commands.
  - The source data's timestamp and SHA-256, and the fixture's SHA-256.
  - `routes.json`: the route endpoints, which are OSM trail nodes on public land, never anyone's recorded positions.
  - `NOTICE.md`.
  - The OSM source file is not committed; the recipe and its hash are.
- **Licence.** The fixture is a derivative database of OpenStreetMap, distributed publicly in a public repository, so ODbL applies. `NOTICE.md` says: "Contains OpenStreetMap data © OpenStreetMap contributors, available under the Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/ ; see https://www.openstreetmap.org/copyright. This file is itself made available under the ODbL." It also says how it was produced, with BRouter 1.7.10, MIT. This follows the OSMF wording Juniper quoted (her Q6, "Databases"). BRouter's MIT notice is needed only where BRouter is distributed; the jar is downloaded at test time, not committed. Inferred, not legal advice.
- **Stop check:** nothing in either licence prevents carrying the fixture or using the jar this way (inferred).

## Q5. Mechanism prediction, written before building

1. **Failing first.** With `cut.ts` a stub that throws "not implemented", every format and route test fails on that message, and the route tests fail before BRouter is called.
2. **BRouter accepts the cut file.** It finds the header checksum with `^ 2`, so the 32-division format (`PhysicalFile.java:133-134`). The touched tiles' index checksums verify (`OsmFile.java:66-71`). The copied cells decode, and their own checksums still match because their bytes are unchanged.
3. **Routes that stay inside are identical.** That means routes whose ends lie inside the rectangle and whose full-fixture route stays inside it. They give the same track length, cost and coordinate list on the cut as on the full fixture. The mechanism:
   - the nodes outside the cut stay hollow and are never expanded (`NodesCache.java:213-235`);
   - BRouter's last pass searches with `airDistanceCostFactor = 0`, so it is exact (`RoutingEngine.java:1710-1712`, `pass2coefficient` default 0 at `RoutingContext.java:158`; the final re-track at `RoutingEngine.java:1794`);
   - so removing nodes that the optimum never visits cannot change the optimum.

   The one way they could differ is a tie between equal-cost paths, broken by exploration order. I expect none on the fixture's routes, and if one appears it will be reported, not hidden.
4. **Waypoint matching needs the margin.** BRouter matches an endpoint by loading its own cell, then the ring of cells around it at 1/32 degree (`NodesCache.java:294-301`, `preloadPosition :327-345`), with a 250 m catching range (`RoutingContext.java:143`). With a one-cell margin, an endpoint inside the rectangle sees the same cells on both files. The revert check removes the margin. A route with an endpoint inside the rectangle, within 250 m of a way that lies just outside it, then matches a different way or point on the cut, and its track differs. The fixture's routes include one such edge route on purpose.
5. **A route with an end outside the cut does not fail. It succeeds with that end moved.** I read this from source and did not run it.
   - The first match finds no way within 250 m.
   - The engine then retries with dynamic range: `use_dynamic_range` defaults to on (`RoutingContext.java:200`, default `1f`), and neither `hiking-mountain.brf` nor `trekking.brf` sets it (grep, both files). The retry is at `RoutingEngine.java:1612-1624`, with up to 60 km (`:60`) and up to 20 rings (`NodesCache.java:23`, `:298-300`; `WaypointMatcherImpl.java:43-46`).
   - It matches the nearest way inside the cut and returns a route ending there.
   - `add_beeline` is off in both profiles (`hiking-mountain.brf:48`, `trekking.brf:19`), so no straight line joins the requested point.

   Only with dynamic range off do I expect "`to-position not mapped in existing datafile`" (`RoutingEngine.java:1626-1630`). Whether passing `use_dynamic_range=0` through the CLI's profile parameters overrides a value the profile never assigns is **could not determine** (`BRouter.java:99-102` passes them to `RoutingContext.keyValues`, `RoutingContext.java:36`, `:101`). Phase 2 tries that first, then a test-only profile copy with one added `assign`.
6. **A route whose ends are inside but whose best path leaves the cut** succeeds on the cut with a different, longer, costlier route. Nothing fails or warns.
7. **"No track found"** (`RoutingEngine.java:1771`, `:1775`) appears only when both ends match but no path joins them inside the cut. One example is an end on a trail piece that the cut leaves disconnected from the other end.
8. **An end in a different 5-degree square, with that file absent,** gives "`datafile W…_N….rd5 not found`" (`NodesCache.java:305`, `:331-333`, `:384-387`).

So the proof can assert 3 and 4 as the dispatch wants. For "a route leaving the area", it can assert the behaviour BRouter actually has (5 to 8), but not "fails cleanly" with the default profile. That needs a decision; see below.

## Choices this report raises

- **For the planner, and in the end the owner: what "a route leaving the area" should do.** With the profiles as they are, BRouter quietly moves an outside endpoint onto the cut's edge (Q5, 5) and quietly detours inside it (Q5, 6). The dispatch's prediction, and the app's later need to "say so in plain words", assume a failure that BRouter does not produce. Options for the proof, the app being step 4:
  - (a) assert the real default behaviour, and pin `use_dynamic_range=0` as a second case that does fail cleanly;
  - (b) have the caller compare the matched endpoint with the requested one;
  - (c) both.

  I propose (a) for this dispatch's tests, since it records what the engine does and decides nothing for the app. The phone's behaviour is an owner decision at step 4, under "Don't make me think": a route that looks fine but ends somewhere else is the "state the user could misread" case. I have not stopped Phase 1 on it, because Phase 1 stops here anyway. It needs an answer before Phase 2's tests are written.
- **Margin size.** One cell (about 3.5 x 2.4 km at 45 N) is enough for waypoint matching (Q5, 4). It does nothing for Q5, 6, where a wider margin trades bytes for fewer silent detours. I propose one cell as the default and a parameter, with the trade put to the owner when the server route is built.
- **Privacy of the cut request** (Juniper's option E, `wrangler.toml:7-13`). A cut on request names the user's area in the URL. The owner's option text as the dispatch quotes it does not mention this. It does not affect the cutter itself, but it should reach the owner before the server-route part.

## Premises that were wrong

- **Juniper's report, Q3, options B and C:** "a start or target outside gives 'position not mapped'". Under the profiles' defaults it gives a route snapped to the nearest way inside, up to 60 km away (Q5, 5). Her citation `RoutingEngine.java:1626-1630` is the throw *after* the dynamic retry at `:1612-1624`, which she did not mention. Read from source, not run.
- **The dispatch's "a route leaving the rectangle fails … not with a crash or a wrong route":** with the default profile, both kinds of leaving produce a successful route that differs from the full square's. These are exactly the "wrong route" the dispatch rules out (Q5, 5 and 6).
- **The dispatch's "same points, as in the spike":** the spike report shows identical **lengths** (r2 10,029 m, r4 9,940 m, `engine-spike-report.md:186`). It does not show that the points were compared. The prediction may still hold; it is not the spike's finding.
- **The planner's 10 km prediction** ("well under 1 MB, in line with 0.14–0.23 MB") cannot be measured by this dispatch's CI proof, which uses a small fixture, not a real Pi square. A real square is 54.9 MB and lives on the Pi. Measuring it needs either BRouter's public weekly square (about 40 MB, a bulk download) or a run on the Pi, and neither is in scope. Phase 2 will report the fixture's and the cut's measured sizes and say this plainly.
- **Confirmed, not wrong:** the base commits; "CI does not run the Worker's tests"; the empty-tile and empty-cell lines `OsmFile.java:56-58`, `:125-127`; Juniper's format reading generally (Q1).

## Could not determine

- Whether the profile-parameter route overrides `use_dynamic_range` (Q5, 5).
- The release zip's exact contents beyond the classes the Pi checks, such as whether `profiles2` is inside it. Not downloaded; the fetch script will take the profiles from source by blob hash, as the Pi does.
- Cloudflare's CPU and subrequest limits against the cut's 20 to 40 range reads.
- Which Node is on the runner image by default. The proposal pins its own.
- Whether Node's type stripping treats these `.ts` files as ES modules without `"type": "module"` in `package.json`. I expect yes, by syntax detection; the fallback is `.mts`. Phase 2's first failing run shows which.

## Decided beyond scope

- The proof uses the Pi's pinned release jar, not the spike's source-built `brouter-lib`. Reason: no Gradle in this job, and it is the same engine version the squares were built with.
- I propose a one-line `tsconfig.json` change (`allowImportingTsExtensions`) so the cutter can be small modules.
- I propose copying the source's creation timestamp into the cut.
- I propose a 0.5 MB ceiling on the committed fixture and a fixture area outside the PNW.
- I fetched `RoutingContext.java`, `WaypointMatcherImpl.java`, `BRouter.java` and the two profiles, which are beyond Juniper's file list. The edge behaviour and the CLI needed them.

## What stopped me

Nothing blocked Phase 1. Two items need the planner before Phase 2: the "route leaving the area" assertion (Choices, first item) and approval of the `ci.yml` job. No `ci.yml`, `index.ts`, main or release-build change was made.
