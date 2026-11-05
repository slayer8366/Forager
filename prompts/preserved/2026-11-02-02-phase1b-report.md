# Dispatch 2026-11-02-02, Phase 1b report: what the map server answers for "no detail", and how the app would ask (Hazel, Opus)

Phase 1b only. I wrote no code or tests. I created no branch, commit, push or PR, ran no Gradle, didn't touch the phone, and didn't edit RECORD.md, the audit index or known-bugs.md. The planner's worktree is unchanged: `git status` is empty and it is still on its own branch. My fetches updated only remote refs and FETCH_HEAD. I made 19 read-only requests to the live map server, out of the 20 allowed. Scratch was in `/mnt/work/scratch/bug-14-1b/`, with the responses in their own `server-responses/` folder and my scripts in `scripts/`. The folder has been deleted.

Marks: **[read]** means read in source at the cited file and line. **[observed]** means output I saw, including the live responses. **[inferred]** means my reasoning, not seen.

## 0. State checked
- **pre-main** is at **ee0651fe0**, before and after the session [observed]. This is the base for every file:line below.
- **records-after-173** was at f66012ea6 when I read the dispatch, both amendments, the Phase 1 report and the RECORD entries. It is at fc4b3c91d now [observed].
  - None of those four dispatch files changed between the two [observed, `git diff --stat`].
  - The new commits add -2145 to -2149 and two other dispatch files. One of them matters here: stop S4.
- **The Worker** `server/pmtiles-worker/src/index.ts` is the same blob (8896b0912) on main and pre-main [observed]. The live answers below match what that source does.
- **`bug-14-picker-warning`** is not on origin [observed].

## 1. What the server answers for a tile with no detail
### The requests
None of these points is near B1's US point. Every tile is `/us/{z}/{x}/{y}.mvt`, the Trail Map's own tile address, apart from #1.

| # | Request | Why | Answer [observed] |
|---|---|---|---|
| 1 | `/us.json` | The archive's bounds and per-layer zooms | 200, from the edge cache (age 59,448 s). Bounds `[-180, -85.05, 180, 85.04032]`. Centre `[0, 50.4806449, 0]`. Max zoom 15. Every layer goes to z15 except `landcover` (z0-7). |
| 2-4 | z10 220/443, z12 881/1772, z14 3527/7091 (inland central Mexico) | No detail, north of 15.92°N | 200, 57 bytes each: one `earth` polygon of kind `earth`, nothing else |
| 5-10 | z10 268/476, z11 537/952, z12 1075/1904, z13 2150/3809, z14 4300/7618, z15 8601/15237 (inland Central America) | No detail, south of the line, at every zoom | **204**, empty body, `cache-control: public, max-age=86400` |
| 11 | z10 231/388 (the picker's default centre, `AvailabilityOfflineMapsUi.kt:402`) | A tile with detail | 200, 19,203 bytes: boundaries, earth, landuse (139), places, roads (20), water (10) |
| 12 | z14 3705/6212 (same place) | Detail at a deep zoom | 200, 2,338 bytes: buildings, earth, landuse, pois, roads, water |
| 13 | z10 554/438 (the Sahara) | Outside the Americas | 200, 57 bytes, `earth` only |
| 14 | z12 1161/1834 (a Caribbean island's coast, outside the data) | A coast outside coverage | 200, 1,222 bytes: `earth`, plus `water` of kind `ocean` |
| 15 | z12 341/906 (Alaska's Arctic coast) | A sparse covered tile | 200, 680 bytes: earth, landuse (4), roads (3) |
| 16 | z12 1228/1254 (inland Quebec) | A sparse covered tile | 200, 3,213 bytes: earth, landuse (wetland, 3), water (stream, water) |
| 17 | z10 301/384 (a dense metro, New York) | Bandwidth | 200, 410,407 bytes decoded, 305,508 transferred (zstd), 0.66 s |
| 18 | z12 1206/1539 (same metro) | Bandwidth at z12 | 200, 402,542 bytes decoded, 264,068 transferred, 1.23 s |
| 19 | z10 230/455 (Mexico City) | Does an uncovered big city carry world place data? | 200, 55 bytes, `earth` only |

- Every tile answer carried `x-forager-origin: r2` [observed].
- I decoded the bodies as data, with a small protobuf reader run as `python3 -I` from a separate folder [observed].
- I deliberately requested no z15 tile north of the line: if the archive's max zoom were 14, such a request would go through the overflow (`index.ts:616-621`), which reads the public build live and writes into R2.

### What this shows
- **F1. "No detail" comes in two forms, split by latitude rather than by zoom.**
  - **South of the archive's south bound:** 204 at z10 to z15 [observed, #5-10].
  - **North of it, outside the source extracts:** 200 with a tile holding only the land fill (`earth`), and on a coast the sea (`water`, kind `ocean`) [observed: #2-4, #13, #14, #19, across three continents and at z10, z12 and z14].
  - **So a 204-only check misses most of what the owner was told it would cover.** It would show no line in Mexico, the Caribbean, or anywhere else north of about 16°N outside the US and Canada [inferred from F1]. The option the owner chose reads "The warning shows wherever it doesn't: Central and South America, Mexico, the Caribbean, anywhere outside the coverage" (-2144). This is the wrong premise in stop S5.
- **F2. The header's bounds.**
  - `/us.json`'s west, east and north come from the archive header, and its south is replaced by the world fill's -85.05 (`index.ts:333-353`, :604-606) [read]. They read -180, 180 and 85.04032 [observed].
  - The header's centre latitude, 50.4806449, is the midpoint of 85.04032 and 15.92097. So the header's south bound is most likely still 15.92097°N [inferred].
  - The header box therefore covers every longitude north of about 15.92°N, and the build fills land and sea across all of it [inferred from F1's samples].
- **F3. The archive goes to z15.** Every vector layer's maximum zoom is 15 [observed, #1], so the header's max zoom is most likely 15 [inferred]. If so:
  - z10 to z15 are all served from the archive itself (`index.ts:644-659`);
  - the overflow and its south-bound check at :616-621 never run for `us` [inferred].
- **F4. The Worker's code, per zoom, for `us`** [read]:
  - z10 to z15 inside the archive's zooms: `getZxy` (:644). A tile it holds answers 200 with its bytes (:649-650).
  - A tile it lacks tries the world fill (:652-657). That stops above z8 (:310, :369), so the answer is 204 (:659).
  - Both 200 and 204 are cached at the edge for a day (:567) [read, and observed in the headers].
- **F5. A covered tile carries features from the map's data.** All four covered samples did: roads, land use, streams or lakes, places, boundaries, buildings, points of interest [observed, #11, #12, #15, #16]. No uncovered sample carried any of these, Mexico City's included [observed, #19].

## 2. The request the app would make (my proposal)
- **Address:** `GET https://forager-pmtiles.brandonlee1-894.workers.dev/us/10/{x}/{y}.mvt`.
  - The template comes from `Basemap.FORAGER_TRAIL.tileUrlTemplate` (`Basemap.kt:154`), passed in by AppContainer, so there is no new copy of it [read].
  - It equals the Trail Map style's `protomaps` source tiles (`/us.json` `tiles[0]`) [observed].
  - The request goes on the app's JSON client, `ApiClients.httpClient` (`ApiClients.kt:23-37`): the app's User-Agent and 15 s timeouts, the client the routes and the drop-off copy already use (`AppContainer.kt:332-337`, :347-352) [read]. The Worker refuses some default user agents (-1514).
- **Zoom 10,** the download's own lowest zoom (`OfflineMapRepository.kt:170`) [read].
- **Tiles:** every z10 tile the download rectangle touches.
  - The rectangle is `routeAreaFor(region)` (`RegionRouteData.kt:177`), the one the download uses (`MapLibreOfflineMapRepository.kt:331-337`) [read].
  - The cover uses the formula of `DropOffTileCover.of` (`DropOffTileCover.kt:19-25`, via `TileFeatureReader.tileOf`, `MapFeatures.kt:103-109`), at z10 [read].
  - An area crossing the antimeridian is "unsupported", said so, as `DropOffTileCover.kt:17-20` does.
  - Count, worst case for the 24 km area [computed]: 9 tiles to 45°N, 16 at 60°N, 25 at 70°N, 36 at 75°N.
- **Each answer is classified** (pure Kotlin, reusing `MvtDecoder`, `MvtDecoder.kt:17-27`):
  - **no detail:** a 204, or a 200 whose features are all `earth`, or `water` of kind `ocean`, with at least one `earth` feature;
  - **sea:** a 200 holding only `ocean`. It counts neither way;
  - **detail:** a 200 with any other feature;
  - **unread:** a 200 that doesn't decode or is over the byte cap, or any other status (403, 404, 5xx).
- **The area:**
  - **some outside:** any tile is "no detail". The line shows, and the remaining requests are cancelled.
  - **all inside:** every tile is "detail" or "sea". No line.
  - **not run:** anything else. No line, and one log line (section 6).
- **Why z10 and not finer.**
  - At z10, a tile holding only land means none of the map's data is drawn anywhere in that 30 to 40 km square, and its z11-15 tiles are land-only too [inferred from F1/F5].
  - Its limit is the coverage edge. A z10 tile that straddles the end of the data counts as "detail", so up to one z10 tile of the area, about 33 km at the US-Mexico border's latitude [computed], can download blank with no line.
  - z11 halves that band (16.6 km) for 16 to 25 requests. z12 quarters it (8.3 km) for 42 to 64 requests and roughly 15 MB in a dense covered city [computed from #17-18]. That is stop S6.

## 3. The operating limit (stated, not measured, as `DropOffMapLimits.kt:3-8` states its own)
| Limit | Value | Reason |
|---|---|---|
| Debounce | 750 ms after the last pin settle or slider step | A second drag or slider step replaces the first. |
| Requests per settle | at most 25 z10 tiles | The 24 km area's worst case at 70°N. Over it: not run, logged. |
| In flight | 4 | `DropOffMapLimits.MAX_IN_FLIGHT`'s figure, a fifth of the map's 20 per host, so the picker map's own tiles aren't crowded (`MapHttpClient.kt:30`). |
| Time-out | 8 s for the whole check | Then not run, logged. The calls are cancelled, not left to finish. |
| Latest wins | a new area cancels the running check | No bytes spent on an area off screen. |
| Body cap | 1 MiB per tile | The largest measured is 410 KB (#17, one sample). Over it: unread. |
| Early stop | the first "no detail" answer ends the check | An uncovered area costs a few KB. |

Data use, from the samples [inferred]:
- no-detail tiles are 55 B to 1.2 KB;
- covered tiles are 2 KB to 410 KB;
- a first look at a dense covered city at 24 km is about 9 × 300 KB, roughly 2.7 MB. That figure was transferred as zstd; the app's client asks for gzip, which is likely somewhat larger.

**Beyond the dispatch's letter (call C3):** a per-session cache of tile answers, up to 512 entries, held by the ViewModel collaborator.
- A pan inside the same z10 tiles then asks nothing, and the line doesn't blink.
- A rebuild shows up on the next app start. The edge itself keeps answers for a day (F4).

## 4. The interface and client, following existing patterns
- **The interface, in `domain/mapdata/`:** `TrailMapDetail { suspend fun check(area: GeoBoundingBox): AreaDetail }`.
  - It answers `AllInside`, `SomeOutside` or `NotRun(reason)`.
  - `NoTrailMapDetail` is the explicit "unsupported" answer: `NotRun("this build has no map server check")`.
  - Patterns: `RegionDropOffMaps` and `NoRegionDropOffMaps` (`RegionDropOffMaps.kt:22-46`); `NetworkSignal` (`NetworkSignal.kt:9-19`) [read].
- **The client, in `data/mapdetail/`:** `WorkerTrailMapDetail`.
  - Pattern: `DropOffMapFetch` (`DropOffMapFetch.kt:47-55`, :198-207 for the 200/204/else answer, :212-215 for one log line on not-run) [read].
  - Limits go in a `TrailMapDetailLimits` object, after `DropOffMapLimits.kt:9-37`.
- **The ViewModel:**
  - Pattern: an extension function in `AvailabilityViewModelOfflineMaps.kt` reading `noSignal()` as :62-73 and :80-85 do (default "no signal", `AvailabilityViewModel.kt:179`).
  - A collaborator built beside `offlineRoutes` (`AvailabilityViewModel.kt:274-292`) [read].
- **UI to ViewModel:** pattern `OfflineRegionsList.kt:91-103` (a `LaunchedEffect` calls a bundle callback, and the ViewModel decides).
  - The callback rides the existing `OfflineRouteActions` (`OfflineRegionRouteLine.kt:47`), wired at `MainActivity.kt:527`.
  - That is the only route to the panel that doesn't touch `AvailabilityCompactScaffold.kt` (1,440 lines; :312, :1347) or `JournalTab.kt` (919; :250, :832) [read]. Call C1.

## 5. Does anything need the Worker changed?
- **No, for the proposal above.** The live tile route already gives everything it reads (F1 to F4) [observed, read].
- **One dependency on the map side.** The classifier names the build's fill layers once (`earth`, and `water` of kind `ocean`). A future build that adds another world-wide fill layer at z10 and above would make uncovered tiles read as detail.
- **An exact alternative is the map side's lane, not mine.** The map side could publish its coverage on a small Worker route, which would be exact at the edge and nearly free in bytes. That is a Worker release on main. If the planner prefers it, I stop (S5).

## 6. No signal, or a failed answer
- **With no signal at the moment of asking:** no line, and one log line through `ErrorLog` (`AppContainer.kt:204`): "Offline maps detail check not run: no signal."
  - "No signal" is the maps' own state (`noSignal()`, `MainActivity.kt:108`, :119), read when the debounced area arrives.
- **A failed answer** (connection, time-out, a non-200/204 status, an undecodable or over-cap tile, the tile cap, the antimeridian): no line, and one log line naming the reason.
  - The line gives counts and the reason, never the position, as `DropOffMapFetch.kt:41` states for its own log.
  - "Some outside" is shown only on a real "no detail" answer, even when other tiles failed. No guess.
- **Download Maps stays enabled in every case.** `enabled = pinIsAPlace && !isDownloading` is untouched (`AvailabilityOfflineMapsUi.kt:256`) [read].

## 7. Mechanism prediction
1. **The panel waits for the area to settle.** A `LaunchedEffect(pinRegion)` in `OfflineMapsPanel` (pin plus radius, `AvailabilityOfflineMapsUi.kt:163-165`) waits 750 ms, then calls `routeActions.onAreaSettled(pinRegion)` when `pinIsAPlace`.
   - A new pan (`onCameraIdle`, `CentrePinLocationPicker.kt:169`) or a slider step restarts it.
   - It also runs once when the picker opens.
2. **The ViewModel decides.** `AvailabilityViewModel.onOfflineAreaSettled(region)` reads `noSignal()`. With no signal it logs and stops; otherwise it calls `offlineDetail.ask(region)`.
3. **The collaborator runs the check.** It cancels any running check, then launches `trailMapDetail.check(routeAreaFor(region))` in `viewModelScope` under the 8 s deadline.
   - A definite answer is written to the screen state as `offlineDetail = OfflineDetailAnswer(area = region, someOutside)`.
   - "Not run" is logged.
4. **The client asks the server** as sections 2 and 3 describe: the cache first if ruled, then up to 4 requests at once, classify, stop early.
5. **The panel shows the line only for the area on screen.** It shows the owner's words only when `uiState.offlineDetail?.area == pinRegion && someOutside`.
   - The line goes the moment the pin or radius moves.
   - An answer for an earlier area never shows.
   - The owner's words: "Detailed offline maps aren't available here yet. A download here will be blank when you're offline."
6. **Placement (A5)** [read; layout inferred]:
   - **Portrait:** directly after `confirmActions()` (:355), inside the map's column, so it is directly under "Pin at". `bodySmall`, default colour, padded `Spacing.lg` × `Spacing.sm`, like the continental note (:193-199).
   - **Landscape:** inside the panel's `pinnedActions` lambda (:316-324), before `CentrePinConfirmActions`, so it is pinned above "Pin at". `OfflineMapsSideBySide.kt` calls that lambda inside its controls column (:72-91), so that file is unchanged.
7. **Predicted timing:** the line appears about 1 to 1.5 s after the pin settles with signal: the debounce plus one round of small, edge-cached requests [inferred].

## 8. Test plan (B2: fail first, then revert checks)
**Through the real picker.**
- `RealSearchScreenRig` (`RealSearchScreenRig.kt:179-266`) composes the real `AvailabilityScreen` and `AvailabilityViewModel`. It gains defaulted parameters (call C2):
  - the `TrailMapDetail`;
  - `noSignal`;
  - an `ErrorLog`;
  - `routeActions` wired as `MainActivity.kt:527` wires them (the rig's bundle at :249-257 passes none today);
  - a picker map that settles at a point the test sets on each drag, as `PanRecordingMapSlot.kt:34-59` does. Today's map ignores `onCameraIdle` (:208).
- The server is behind the project's interface, with the real `WorkerTrailMapDetail` over `FakeTileServer` (`FakeTileServer.kt:20-70`): the real request, classifier and ViewModel, with only the network faked.
- **Fixtures:**
  - detail: the build tile already in test resources (`DropOffTestData.TILE`, `DropOffFixtureTest.kt:93`);
  - land-only and land-plus-ocean: made by a small test encoder. The live bytes are not copied in.

**UI cases, in portrait and in a landscape subclass.** Entry is Tools > Offline Maps, as `DownloadPausedShownTest.kt:61-65`. The pan is a real drag.
1. All tiles have detail: no line. Positive controls: "Pin at" reads the panned point, and the server was asked for exactly the area's z10 cover.
2. One tile answers 204: the line, as literal text, and Download Maps enabled, with a real touch opening "Download this area?".
3. One tile answers land-only: the same as case 2.
4. No signal: no line, nothing asked, and the log line exact.
5. A pan from a no-detail area into a covered one: the line goes.
6. A pan into an area whose request fails: the line goes, logged.
7. Placement by bounds:
   - portrait: under "Pin at" and above the Name field;
   - landscape: above "Pin at", outside the scroll.

**Unit tests.**
- **The classifier:** 204, land-only, land plus ocean, ocean-only (sea), each detail layer, undecodable.
- **The cover:** counts at 16°N, 45°N and 70°N; the antimeridian.
- **The client:**
  - the cap: 26 tiles means not run and nothing asked;
  - at most 4 in flight (`maxInFlight`);
  - the early stop;
  - the byte cap;
  - 403 and 500;
  - a dropped connection;
  - the time-out.
- **The ViewModel:** latest wins; the log lines.

**Fail first.** The tests are committed over stubs: the classifier answers "detail", the note draws nothing, and the ViewModel function does nothing.
- Expected failures: cases 2, 3, 5 (its positive control) and 4's log line. Each fails with "no node with text …" or a log mismatch.
- Cases 1 and 6, the "nothing asked" check and the enabled checks pass over the stubs. They are listed as "not shown to bite" until the revert checks.

**Revert checks.** Each runs on its own scratch branch and CI run. The compile log is read first, the file is restored from a saved copy, and the forward change is confirmed afterwards.
- **R1 "the check never runs":** the ViewModel function returns at once. Cases 2, 3 and 5 should fail.
- **R2a "204 read as detail":** case 2 and the client's 204 case should fail.
- **R2b "land-only read as detail":** case 3 and the classifier's land-only case should fail.
- **R3 "it warns with no signal":** the guard is removed. Case 4 should fail, with the line shown and the asked list non-empty.
- **R4 (a mutation beyond B2):** the panel ignores the answer's area. Case 6 should fail.

Full CI green on the last real commit, with counts read from the JUnit XML.

## 9. Files, with sizes now (modular-code answers)
**New main files (about 430 lines):**
- `domain/mapdata/TrailMapDetail.kt`, ~55: the interface, the answer and the "unsupported" default;
- `domain/mapdata/TileDetail.kt`, ~50: one tile's answer classified, with the fill layers named once;
- `domain/mapdata/DetailTileCover.kt`, ~30: the area's z10 tiles;
- `data/mapdetail/WorkerTrailMapDetail.kt`, ~150: the client;
- `data/mapdetail/TrailMapDetailLimits.kt`, ~40: the limits;
- `ui/availability/AvailabilityOfflineDetail.kt`, ~80: the collaborator (and the cache, if ruled);
- `ui/availability/OfflineDetailNote.kt`, ~35: the owner's words and the line.

**Changed:**

| File | Now | After |
|---|---|---|
| `AvailabilityViewModel.kt` | **790** | ~800 |
| `AvailabilityViewModelOfflineMaps.kt` | 158 | ~178 |
| `AvailabilityUiState.kt` | 441 | ~447 |
| `OfflineRegionRouteLine.kt` | 72 | ~76 |
| `AvailabilityOfflineMapsUi.kt` | 415 | ~430 |
| `MainActivity.kt` | 766 | 767 |
| `AvailabilityViewModelFactory.kt` | 74 | 75 |
| `AppContainer.kt` | 600 | ~608 |

**Tests:**
- new, about 900 lines: the classifier, cover, client, ViewModel and picker test classes, plus the tile encoder;
- changed: `RealSearchScreenRig.kt`, 379 to ~400.

**Not touched:**
- `CentrePinLocationPicker.kt` (the edge-shade dispatch lists its :389);
- `OfflineMapsSideBySide.kt`, `JournalTab.kt`, `RecordsTab.kt`, `AvailabilityCompactScaffold.kt`, `AvailabilityScreen.kt`, `Basemap.kt`;
- the DropOff* files and `MapLibreStoreRead.kt` (C2; nothing reads the store);
- `server/pmtiles-worker`, RECORD.md, the audit index, known-bugs.md and CI.

**Order-sensitive code:**
- one new `LaunchedEffect` keyed on `pinRegion`; no existing effect is moved;
- in landscape, one child before "Pin at" in its column. Nothing overlaps.

## 10. What the owner would see that the rulings don't cover
1. **Delay and blink.** The line comes about a second after the pin settles. It goes at once when the pin moves. Without the cache (C3) it blinks off and on with each pan inside an uncovered region.
2. **Opening and the slider.** The line follows the radius slider, since "any part" depends on the area. It is checked when the picker opens, with no pan.
3. **Signal coming back.** If the picker opened with no signal and the signal then returns, nothing is checked until the pin moves, unless the check also rides the existing signal-return hook (`AvailabilityViewModelOfflineMaps.kt:80-85`).
4. **Near the coverage edge** (the US-Mexico border, where the data stops some way south of it), an area can be partly blank with no line, by up to about 33 km at z10.
5. **Remote covered places** (the Arctic): a z10 tile holding only land and sea would show the line though it is covered. Not seen in my four covered samples, and not ruled out.
6. **Open sea.** An area that is all open sea shows no line north of the line, and shows it south of it (204).
7. **Data use.** A first look at a dense covered city at the largest radius may download about 2 to 3 MB.
8. **Bigger than "A small build".** This build is bigger than the "A small build" in the owner's go (-2136). The promotion waits longer.
9. **The continental note.** The screen's last line, "Offline downloads cover the continental United States with vector map data." (:390), sits on the same screen. It is the owner's words; noted in Phase 1 and not changed here.

## Confirmed against inferred
**Confirmed:**
- **[observed]**
  - the bases, and that none of my sources moved;
  - the 19 answers in section 1: 204 south of the line, land and sea fill north of it outside coverage, detail inside;
  - the tile sizes;
  - the per-layer zooms.
- **[read]**
  - the Worker's paths per zoom (F4);
  - the app's patterns and every file:line in sections 2 to 9;
  - the route of `OfflineRouteActions` through the 1,440- and 919-line files;
  - the trip bubble's planned files (`2026-11-05-05-phase1-report.md:200-224`, go -2148).

**Inferred:**
- the header's south bound of 15.92097, from its centre;
- a max zoom of 15, and so the overflow being unreachable for `us`;
- the fill covering every longitude north of the line;
- a land-only z10 tile meaning its deeper tiles are land-only too;
- the timing and bytes per settle;
- the landscape layout;
- OkHttp's gzip size.

## Could not determine
- Whether any covered z10 tile holds only land and sea (the false-warning risk, item 5). The map side could count such tiles inside the extracts' polygons from the archive.
- How far past the US-Mexico border the source data runs.
- The header's max zoom and south bound by direct read, rather than through the TileJSON.
- What a z15 tile north of the line answers: not requested, to avoid any chance of the overflow's write.
- Whether the 11-03 build covers Puerto Rico and the US Virgin Islands: not requested.
- Whether the deployed Worker is byte for byte main's folder. Its answers are consistent with the source.
- The largest real z10 tile: I have one metro sample.

## Premises that were wrong
- **The planner's addition in -2142, and C4's "204, or another code".** "The Worker already answers 204 for a … tile where the archive has no detail" holds only south of about 15.92°N. North of it, outside the source data, the answer is 200 with land and sea fill at every zoom sampled. A 204-only check would not give the coverage the owner's chosen option describes (F1).
- **Known bug 14's "south of 15.92°N"** (the row and README:65) understates the bug. Outside the data north of the line, downloads would store land-only tiles: blank but for the land colour [inferred from tile answers; no download made]. This is for the planner's row.
- **C4's line numbers still hold, but one path is likely dead.** `index.ts:610-621` and :649-659 are where they were. The z15 south-bound check at :616-621 likely never runs for the current archive (F3).
- **The Phase 1 report's "Not touched: RealSearchScreenRig.kt"** no longer holds. B2's real-picker test needs the rig to hand the ViewModel the server and a signal state.

## Stops
- **S4 (planner): two coders on one file.** The trip bubble's Phase 2 (go -2148, "As a trip") plans changes to `AvailabilityViewModel.kt` (790), `MainActivity.kt` and `AvailabilityUiState.kt`, and this build needs all three. Modular-code: "Two coders running at once never share a file … the planner sequences them". `AvailabilityViewModel.kt` would also pass 800 with both, which means a split first.
- **S5 (planner): the mechanism, given the wrong 204 premise.** Choose between:
  - (a) the app reads z10 tiles and classifies them, with no Worker change (my proposal);
  - (b) the map side publishes its coverage on a Worker route. That is a Worker release on main, the map side's lane; I stop if it is chosen.
- **S6 (planner, and the owner where it changes what a user sees):** z10's edge band of up to about 33 km, against the requests and data a finer zoom costs.
- **S7 (planner): calls.**
  - C1: widen `OfflineRouteActions` to carry the settle.
  - C2: the rig change.
  - C3: the session cache.
  - Re-checking on opening, on the slider and on signal return.
  - `AvailabilityViewModel.kt` at the 800 threshold, or the collaborator built inside `AvailabilityOfflineMaps` (+3 lines there instead).

## Anything decided beyond scope
- **Which requests.** I chose the 19 requests and their places (section 1), including Mexico City and a dense metro to test the classifier and price bandwidth. I skipped z15 north of the line on purpose. Request 20 was left unused.
- **Extra reading:**
  - RECORD -1341, -1514, -1554, -1558, -1559, -2007, -2135 to -2141, -2143 and -2148;
  - the trip bubble's and the end-log dispatch's file lists;
  - the crowded-icons test-fix branch's diff (two test files, no overlap).
- **Proposals, not decisions:**
  - the classification rule;
  - every limit value in section 3;
  - the answer carrying its own area;
  - the cache;
  - test fixtures made by an encoder rather than copied from production.
- **Commit trailer.** Phase 2 commits would end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name, as the dispatch and profile say. That overrides this session's default, which names the model.
