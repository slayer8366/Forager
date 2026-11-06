# Older offline areas get the street drop-off's map data automatically; the notice's new words: completion report (PR #490)

Dispatch `prompts/preserved/2026-11-04-09-drop-off-map-copy.md` (on `origin/records-after-173`), with:
- Amendment 1 (A5 to A10, B1 to B3, C1 to C3);
- RECORD -2112 (the notice's words);
- RECORD -2113 (an unmarked answer stays wanted).

Written by Hazel (Opus), 2026-11-05. An earlier Hazel session wrote the Phase 1 report (`prompts/preserved/2026-11-04-09-phase1-report.md`). This session built Phase 2 from it and re-checked what it built on. Its A8 facts are filed at `prompts/preserved/2026-11-04-09-a8-report.md`.

Each claim is marked:
- **read**: file and line, or the named CI run's JUnit XML or log;
- **observed**: command output;
- **inferred**;
- or **not verified**.

No tile or position from the owner's areas is in this branch. The tests use the invented drop-off block's synthetic tile (`src/test/resources/routing/dropoff/README.md`: "nothing is a real place").

## Base

- I ran `git fetch origin pre-main records-after-173` (read, `git ls-remote`).
  - pre-main was at **949ad8183**, the dispatch's base. It had not moved.
  - records-after-173 was at b78bba112.
- The branch is `drop-off-map-copy`, cut from `origin/pre-main` 949ad8183, in the worktree `~/.cache/forager-wt/drop-off-map-copy`.
- Nothing in the planner's worktree was edited.
- `ForagerDatabase` stays at version 23, and no Room version is used.
- No server or Worker file is touched (read, `git diff --stat 949ad8183..HEAD`: 30 files, all under `app/src/`, plus this report).

## Premises re-checked before building on them

| Premise | How it was checked |
|---|---|
| The reader reads MapLibre's store by the basemap TileJSON's template | read: `MapLibreOfflineBasemap.kt:35-41` (the tile query) and `:44-50` (the source); `StoredTiles.kt:59-68` |
| The fence mark is decided per tile | read: `NearbyMapData.kt:127-131` at 949ad8183 |
| The route fetch takes only regions whose routes still want fetching | read: `AvailabilityOfflineRoutes.kt:28-30`; `RegionRouteData.kt:167-171`. That the S22's routes are `state=stored` is from the Phase 1 report, not re-observed. |
| A downloaded area is the `routeAreaFor` rectangle, at z10 to z15 | read: `MapLibreOfflineMapRepository.kt:139-146` and `:313-320` at 949ad8183 |
| Everything under `files/` is out of backup and device transfer | read: `res/xml/data_extraction_rules.xml` excludes every domain |
| A download's completion reaches `AvailabilityOfflineRoutes.noteDownloaded` | read: `AvailabilityViewModel.kt:282`; `AvailabilityOfflineMaps.kt:206` and `:235` |
| The Worker answers a zoom-15 tile outside its archive with 204, not a filled tile | read: `server/pmtiles-worker/src/index.ts:310` (the world fill reaches only zoom 8), `:363-388` and `:650-659` |

Taken from the 2026-11-04-08 report and not re-read in MapLibre's source: an offline download reuses stored tiles and never revalidates them (its section 2).

## Commits and PR

| Commit | What |
|---|---|
| c153a1a61 | The stubs: the types, interfaces and limits the tests compile against, with no behaviour. |
| 1de3896ac | **Tests first, in their own commit.** Ten new test classes, plus a fake tile server and a fake copy. The notice tests move to the owner's words. |
| a1ec57517 | The copy: `BarrierMark` (with `NearbyMapData` calling it, P7), `DropOffTileCover`, `DropOffMapStore`, `DropOffMapFetch` and `LayeredOfflineBasemap`. |
| 88c53835c | The wiring: the triggers; the listing's size, delete and clean-up; the repository; `AppContainer`. |
| c3815ac1e | The notice's new words (`TrailStyle.kt`). **The last code commit.** |
| (this report) | This file only. |

PR #490 into pre-main was opened as a draft, so its checks run on each commit. It merges only on the owner's word.

## What was built

### The copy's store

Read: `data/dropoffmap/DropOffMapStore.kt` at c3815ac1e.

- **Where it is:** `files/offline_basemap/<area id>/`, beside `offline_routes`. The path is set in `LocalRegionDropOffMaps.create` (`RegionDropOffMaps.kt`).
- **What a folder holds:**
  - each tile, gzipped by the app, as `15-<x>-<y>.mvt`. It is written to `.partial` and renamed into place (`:79-86`);
  - an empty marker, `15-<x>-<y>.empty`, for a 204 (`:88-92`);
  - one `whole` record per area (`:94-100`).
- **Its safeguards:**
  - one pass per area at a time in the process (`:54-66`);
  - the folder is made only at a pass's start, so a write after a delete fails (`:79`);
  - a delete renames the folder aside first (`:149-155`);
  - the orphan clean-up keeps the given ids and any running pass, and leaves names it never writes alone, logged (`:162-182`).
- **Files, not Room:**
  - The copy is asked only two things: "this tile, any area" and "what does area N hold".
  - Nothing joins on it or references it, and it is bulk binary data, not a setting.
  - That is CLAUDE.md's test, with `RegionRouteStore.kt:13-22` as the precedent. The reason is recorded at `DropOffMapStore.kt:23-30`.

### What a pass fetches

Read: `DropOffMapFetch.kt` and `DropOffMapLimits.kt`.

- **Wanted** (`DropOffMapFetch.kt:79-95`):
  - the area's zoom-15 cover (`DropOffTileCover.kt`: the `routeAreaFor` rectangle, with no margin, P6);
  - minus each tile the copy already holds;
  - minus each tile MapLibre's store holds that decodes with the mark;
  - minus each MapLibre tile that doesn't decode. The read hands those to the reader as today, so a copy of them would never be read.
- **The limits** (B1 P5), each with its reason in `DropOffMapLimits.kt`:
  - at most 4 requests in flight (`DropOffMapFetch.kt:126`);
  - starts at least 100 ms apart (`:148-154`);
  - stop at the first connection failure (`:158-162`);
  - stop after 3 answers in a row that are neither 200 nor 204 (`:249-253`);
  - refuse an area of over 4,000 zoom-15 tiles (`:81-83`);
  - don't keep a body of over 512 KiB (`:202`).
- **What is kept of each answer:**
  - a 200 that decodes with the mark is stored;
  - a 204 is recorded as empty;
  - anything else is counted, and the tile stays wanted (RECORD -2113).
- **Whole:** once nothing is wanted, the record is written. Later passes make no request and no MapLibre read (`:60`).
- **The template** is the first tile address of the basemap TileJSON MapLibre's store holds (`:84-87`): the one the way-to keys its reads by. With none stored, nothing is fetched, and that is logged.
- **The client** is the app's JSON client (`ApiClients.httpClient`: the app's User-Agent and 15 s timeouts), as the routes use it (`AppContainer.kt:347-352`).

### The log line that makes a server regression visible (RECORD -2113)

It is `DropOffMapFetch.kt:109`, the line of every pass that ends with tiles still wanted:

"Drop-off map for region N: W wanted, R requests; S stored, E empty, X not stored (U without the mark, D not a tile, B over 524288 bytes, F server errors); stopped: … | every wanted tile was asked for. K still wanted, for the next pass."

The counts are built at `:257-260`.
- A pass with any unmarked answer can't be whole, so it always logs this line, with U counted.
- A whole pass logs `:119`; its unmarked count is 0 by definition.
- A pass that doesn't run logs `:213`, with the reason.

### The read order

B1 P3 and A10; read, `LayeredOfflineBasemap.kt:28-42`.

1. MapLibre's tile. A read failure fails the read, as today.
2. If that tile carries the mark, or doesn't decode: MapLibre's bytes, as today.
3. Otherwise, the copy's tile, if the copy holds one that decodes with the mark.
   - A copy tile that doesn't is discarded, logged, and wanted again.
   - A copy that can't be read is logged, and MapLibre's bytes are returned.
4. Otherwise, MapLibre's bytes or nothing: exactly today's read.

So where neither store holds a tile with the mark, the reader's "has no barrier mark" reason and dispatch 2026-11-04-04 A1's by-trail line are unchanged.

MapLibre's store is only read, through the same read-only open the way-to already used (`MapLibreOfflineBasemap.kt:56`, unchanged). The way-to and the copy share one instance (`AppContainer.kt:343`).

### The mark's one definition (B1 P7)

- The mark is `BarrierMark.carriedBy` (`domain/mapdata/BarrierMark.kt:12`). `NearbyMapData.kt:128` now calls it.
- **The reader's existing tests are unchanged and green, with pre-main's counts** (read, the JUnit XML of runs 41926826762 and 42011776915):
  - `NearbyMapDataTest`: 8 of 8;
  - `FindWayToWaypointDropOffTest`: 10 of 10.

### The triggers (B1 P2)

Read: `AvailabilityViewModelOfflineMaps.kt` and `AvailabilityOfflineRoutes.kt`.

- **The selection is its own:** downloaded here, and not whole (`AvailabilityOfflineRoutes.kt:71-72`).
- **A pass starts on:**
  - opening Offline maps with signal, after the list is read and after the route fetch is launched (`AvailabilityViewModelOfflineMaps.kt:63-73`);
  - signal returning while Offline maps is open (`:84`);
  - "Try again" (`:93`);
  - a download's completion (`AvailabilityOfflineRoutes.kt:111`).
- **With no signal at the opening:** no pass, and one line (`:64-66`; `AvailabilityOfflineRoutes.kt:96-98`):

  "Offline maps opened with no signal: no drop-off map pass this time."
- **Passes run one at a time across every trigger** (`:84`). The list then reloads, so the row's size counts the copy.
- `AvailabilityViewModel.kt` (790 lines) is not touched.

### Size, budget and delete (A5, A6)

Read throughout.

- **The shown size** is computed at `OfflineRegionReconciliation.kt:114`: tiles, routes and the copy. That is the size every listed row shows (`OfflineRegionsList.kt`'s row parts read `sizeBytes`).
- **The download's own summary** (`MapLibreOfflineMapRepository.kt:206-215`) is unchanged. A new id holds no copy when its download returns; the list reload after its pass shows it.
- **The tile budget** (`OfflineTileBudget.kt`) is not touched. It counts each area by its estimate (A6).
- **Delete:**
  - the user's delete takes the copy, after the routes (`MapLibreOfflineMapRepository.kt:245`);
  - so does the listing's never-finished delete (`OfflineRegionReconciliation.kt:68`);
  - copies no area owns are cleared with the routes' keep set (`:124-126`);
  - a failed delete is logged, and the next listing clears it (`:147-153`).
- **A restored area gets no copy.** It isn't downloaded here, so it isn't selected, and `fetchDropOffMap` refuses it (`MapLibreOfflineMapRepository.kt:234-239`).

### The notice's new words (RECORD -2112)

- `OLDER_MAP_STYLE_NOTICE` is now exactly "Older map style. Download again for the new look and hill shading." (`TrailStyle.kt:125`).
- `regionHasOlderMapStyle` is unchanged (`TrailStyle.kt:111-114`).
- The constant's doc comment records the owner's choice and the old words.

## The storage and request figures

These are from the Phase 1 report's observation of session 13's snapshot. They were not re-observed here.

| | Wanted | Requests | Tile bytes (gzip) | On disk, in 4 KB blocks |
|---|---|---|---|---|
| Region 1 | 6 | 6 | 57,129 (at the old copies' size) | 73,728 |
| Region 2 | 598 | 598 | 1,867,084 | 3,219,456 |
| Total | 604 (600 distinct) | 604 | about 2.0 MB, at today's slightly larger tiles [inferred] | about 3.4 MB [inferred] |

- **File sizes:** the app gzips each tile itself, so a file should be about the size the edge sends [inferred: Java's default gzip level against the edge's; not measured on a real tile]. `DropOffMapStoreTest` checks only that a stored file is smaller than its tile.
- **Duration:** a pass takes at least about 60 s on the S22, at 10 starts a second [inferred from the limit; not measured].
- **Counts:** they will be lower if online viewing has refreshed more of those tiles in MapLibre's store since session 13. Such tiles are skipped.
- **Budget and size:** the "832 / 6000" tile budget is unchanged (A6). Region 2's shown size grows by about 2 to 3 MB (A5).

## Failing first: run 42008951889, at 1de3896ac (the tests over the stubs)

- **The build log has no compile errors** (read, the build job's log): 0 `e:` lines, and `assembleDebug` BUILD SUCCESSFUL. The test task failed only on failing tests.
- **The JUnit XML:**
  - 958 files, 6,621 tests, 55 failures, 0 errors, 24 skipped;
  - `testDebugUnitTest`: 952 files, 6,597 tests;
  - `testDebugUnitTestOlderSdks`: 6 files, 24 tests, 0 failures.
- 6,621 is the base's 6,556 (run 41926826762, at 949ad8183) plus the 65 new tests.
- **All 55 failures are in the new or reworded classes,** and each names what is missing:

| Class | Failed / tests | The failure, as named in the XML |
|---|---|---|
| `FindWayToWaypointDropOffCopyTest` | 2 / 5 | "the trail ends 141.4 m from dropoff_a (log: … the waypoint's end: tile 15/9875/12393 has no barrier mark …)"; "its removal is logged: []" |
| `LayeredOfflineBasemapTest` | 5 / 11 | The copy's marked tile is not read: "array lengths differed, expected.length=1037 actual.length=893" and "actual array was null". Nothing is discarded: "expected:<[TileId(z=15, x=9875, y=12393)]> but was:<[]>". Nothing is logged: "logged: []". |
| `BarrierMarkTest` | 2 / 3 | "expected:<Marked> but was:<Undecodable(reason=not built yet)>" |
| `DropOffTileCoverTest` | 4 / 4 | The sampled cover, "but was:<[]>"; the antimeridian box, "expected null, but was:<Cover(xs=1..0, ys=1..0)>" |
| `DropOffMapStoreTest` | 10 / 11 | Among others: "actual array was null"; "the write should have failed" |
| `DropOffMapFetchTest` | 14 / 14 | "but was:<NotRun(reason=not built yet)>"; the asked tiles, "but was:<[]>" |
| `OfflineRegionReconciliationDropOffMapTest` | 4 / 4 | The size: "expected:<{1=42600, 2=300}> but was:<{1=2600, 2=300}>". The delete: "expected:<[5]> but was:<[]>". The clean-up, and the failed-delete line. |
| `AvailabilityViewModelDropOffMapsTest` | 8 / 8 | Opening with signal: "expected:<[3, 8, 4]> but was:<[]>", and the same for each trigger. With no signal, no log line. |
| `OfflineRegionNoticeWordsTest` | 3 / 3 | "expected:<…Download again [for the new look and hill shading].> but was:<…Download again [to update].>" |
| `TrailStyleTest` | 1 / 15 | The same comparison |
| `OfflineRegionOlderStyleNoticeTest` | 2 / 5 | "contains 'Older map style. Download again for the new look and hill shading.' … is not displayed!" |

**Passed over the stubs, so not shown to bite here:**
- **`FindWayToWaypointDropOffCopyTest`, 3 tests.** They guard today's behaviour, which the stubs already have:
  - neither store holds the tile marked: by trail, with its two lines. This is the A10 fallback test the dispatch asks for;
  - MapLibre's tile is marked: the drop-off;
  - MapLibre's store can't be read: by trail.
- **`LayeredOfflineBasemapTest`, 6 tests:** the branches where today's read is the answer.
- **`BarrierMarkTest`, "undecodable":** the stub already answered Undecodable.
- **`DropOffMapStoreTest`, "an area with no folder holds nothing".**
- **`OfflineTileBudgetDropOffMapTest`, 2 tests** (B2's budget). The budget never counted a copy. r8 below shows they bite.
- **`OfflineRegionOlderStyleNoticeTest`, 3 tests:** its tag checks.

The Street-deleted job went red on the same run. Its failures were not read from XML, because that job uploads none.

## Revert checks

Each is one line, edited on its own throwaway branch from c3815ac1e and never merged. Each ran through `gh workflow run ci.yml --ref drop-off-map-copy-rN` (RECORD -865).

**How each branch was made** (observed, the runner's output):
1. The file was copied aside.
2. The file was edited in place.
3. The revert commit was built on a temporary index with `git commit-tree`, so the worktree never changed branch.
4. The file was restored from the saved copy, not from git.
5. Before anything was pushed, four checks were made:
   - the file's sha256 matched the saved copy;
   - the worktree was clean;
   - the forward line was present;
   - HEAD was unchanged.

Each revert commit is a one-line change on c3815ac1e (read, `git diff --shortstat`). After all nine, the worktree was clean at c3815ac1e, matching `origin/drop-off-map-copy`, and each of the nine forward lines was present once (observed).

**Each run's results** (read, its build job log, then its JUnit XML):
- The build log has 0 compile errors, and `assembleDebug` BUILD SUCCESSFUL.
- R1 to R6 were filtered to the 18 drop-off and neighbouring classes, and each XML holds all 164 of their tests. r7 to r9 ran the full suite, and each XML holds all 6,621.
- Every failure below is one its own edit can cause:

| Revert | Branch commit, run | One-line edit | Sample | What failed, and nothing else |
|---|---|---|---|---|
| R1 | 742b2f2da, 42014290576 | `LayeredOfflineBasemap.kt:30` becomes `if (true) return Result.success(stored)`: MapLibre's bytes only | 164 | 7: the 5 copy branches of `LayeredOfflineBasemapTest`, and 2 of `FindWayToWaypointDropOffCopyTest`: "the trail ends 141.4 m from dropoff_a (log: … has no barrier mark …)" and "the bad tile is gone, so the next pass fetches it again" |
| R2 | 9a4b6b9be, 42014294736 | `AvailabilityOfflineRoutes.kt:72` takes the route fetch's selection (`routeData.needsFetch()`) | 164 | 5 of `AvailabilityViewModelDropOffMapsTest`. Opening with signal: "expected:<[3, 8, 4]> but was:<[4]>", the two areas with stored routes dropped, as on the S22. Also the restored row, the whole area, signal returning, and the cannot-run line. |
| R3 | c9f989686, 42014298748 | `DropOffMapFetch.kt:176` stores an unmarked answer | 164 | 2 of `DropOffMapFetchTest`: "…notStored=1…> but was:<…notStored=0…>" (the unmarked case, and the empty-answer case) |
| R4 | e0298f63b, 42014303578 | `AvailabilityViewModelOfflineMaps.kt:64` becomes `if (false) {` | 164 | 5. Three of `AvailabilityViewModelDropOffMapsTest`: no-signal "expected:<[]> but was:<[3]>", signal returning, and Try again "[3, 8]". Also `AvailabilityViewModelOfflineRoutesTest`'s two no-signal tests, "expected:<[]> but was:<[1]>": the same `if` guards the route fetch. |
| R5 | b90ca2c36, 42014308389 | `OfflineRegionReconciliation.kt:68`'s delete becomes `Unit` | 164 | 2 of `OfflineRegionReconciliationDropOffMapTest`: "expected:<[5]> but was:<[]>", and the failed-delete line missing |
| R6 | 2ac336e48, 42014312875 | `DropOffMapFetch.kt:126` becomes `Semaphore(Int.MAX_VALUE)` | 164 | 2 of `DropOffMapFetchTest`: "at most 24 in flight", and the connection failure asking for all nine |
| r7 (B2, A5) | 7803fd0f4, 42014316624 | `OfflineRegionReconciliation.kt:114` drops `+ dropOffMap.sizeBytes` | full, 6,621 | **1**: the size test, "expected:<{1=42600, 2=300}> but was:<{1=2600, 2=300}>" |
| r8 (B2, A6) | a59d1dcf1, 42014321175 | `OfflineTileBudget.kt:20` adds `+ dropOffMap.tiles` | full, 6,621 | **2**, both in `OfflineTileBudgetDropOffMapTest`: "expected:<1774> but was:<2372>" and "expected:<3548> but was:<4146>" |
| r9 (the words) | b6572188d, 42014326225 | `TrailStyle.kt:125` back to "Older map style. Download again to update." | full, 6,621 | **6**, all words tests: `TrailStyleTest` 1, `OfflineRegionNoticeWordsTest` 3, `OfflineRegionOlderStyleNoticeTest` 2 |

**Notes on the reverts:**
- Under R6, "three server errors in a row stop the pass" still passed. With answers that come back at once, the stop can land before many extra requests start, so that test doesn't guard the in-flight limit, and isn't claimed to.
- B2 asked that r7 and r8 each fail only their own test, and the planner that r9 fail only the words tests. They did, across the full suite.
- The Street-deleted job also went red on r7 to r9. Its failures weren't read from XML.

## CI on the last code commit: run 42011776915, at c3815ac1e

- **Every job is green** (read):
  - the build and tests;
  - the Street-deleted build;
  - lint;
  - the release compile;
  - the debug-source check;
  - the map style, cutter and Worker checks.
- **The build log has no compile errors** (read).
- **The build job's JUnit XML** (`unit-test-report`):
  - `testDebugUnitTest`: 952 files, 6,597 tests, 0 failures, 0 errors, 24 skipped;
  - `testDebugUnitTestOlderSdks`: 6 files, 24 tests, 0 failures, 0 errors, 0 skipped;
  - in all, **6,621 tests, 0 failures, 0 errors, 24 skipped**: the base's 6,556 plus the 65 new. The 24 skipped are the base's same 24.
- **The new classes all pass, 65 tests** (read):

  | Class | Tests |
  |---|---|
  | `FindWayToWaypointDropOffCopyTest` | 5 |
  | `LayeredOfflineBasemapTest` | 11 |
  | `BarrierMarkTest` | 3 |
  | `DropOffTileCoverTest` | 4 |
  | `DropOffMapStoreTest` | 11 |
  | `DropOffMapFetchTest` | 14 |
  | `OfflineRegionReconciliationDropOffMapTest` | 4 |
  | `OfflineTileBudgetDropOffMapTest` | 2 |
  | `AvailabilityViewModelDropOffMapsTest` | 8 |
  | `OfflineRegionNoticeWordsTest` | 3 |

- **Their neighbours are unchanged:**
  - `NearbyMapDataTest` 8;
  - `FindWayToWaypointDropOffTest` 10;
  - `AvailabilityViewModelOfflineRoutesTest` 12;
  - `OfflineRegionReconciliationRoutesTest` 7;
  - `OfflineTileBudgetTest` 3;
  - `TrailStyleTest` 15;
  - `OfflineRegionOlderStyleNoticeTest` 5.
- **Measured** (observed, the XML's output): at most 4 requests were in flight at once, for 24 tiles each held 40 ms.
- **This report's own commit** changes only this file. Its run is on the PR.

## The notice's words

**The tests that asserted the old words now assert the owner's.** That is the ruled value, not a weakening:
- `TrailStyleTest.kt:114` ("the notice reads as accepted");
- `OfflineRegionOlderStyleNoticeTest.kt:78`, `:87` and `:102`, and its doc comment at `:30`.

`:102` is an absence check, so it passes with either wording; its tag check is the guard there.

**Through the real list:** `OfflineRegionNoticeWordsTest` composes `OfflineRegionsSection`, the list Offline maps draws, at the S22's portrait window (`w384dp-h823dp`), with two areas:
- the street-v3 area shows exactly the new words;
- the trail-v4 area shows none.

**Does it wrap?** Measured in that test with Robolectric's native graphics (observed, the `MEASURED` lines in the XML), with the section at the window's full width, as the Records tab gives it:

| Words | Default text size (1.0) | Largest (2.0) |
|---|---|---|
| New: "Older map style. Download again for the new look and hill shading." | **2 lines** in a 300 dp box: "Older map style. Download again for the new look" / "and hill shading." | **4 lines**: "Older map style." / "Download again for the" / "new look and hill" / "shading." |
| Old, for comparison (run 42008951889) | 1 line, in a 263 dp box | 3 lines |

- Every line breaks between words, and nothing is cut off at either size (asserted). So known bug 23, words broken inside, does not occur here.
- The phone's own fonts and exact width were not measured. That is a device check.

**Every other place the old words appear.** This PR changes none of these:
- **Code:** `TrailStyle.kt:122`, the new doc comment, which quotes them as history.
- **Docs on this branch:**
  - `docs/audits/2026-10-18-street-cloudflare-part1-report.md:426` and `:480`;
  - `docs/audits/2026-10-22-trail-map-character-completion-report.md:277`.
- **On `origin/records-after-173`:**
  - `RECORD.md`, 4 times;
  - in `prompts/preserved/`: 2026-10-23-05, 2026-10-24-03, 2026-10-27-04, 2026-11-03-05, 2026-11-04-08 (the dispatch and the report), and 2026-11-04-09 (the dispatch and Amendment 1).
- **On `origin/main`:**
  - `StreetStyle.kt`'s constant, and its test;
  - `OfflineRegionOlderStyleNoticeTest`;
  - the same two audit docs.
- No other string in the app holds them (read, `git grep -n 'Download again to update'`).

## What is untested

- **The repository's own paths:** `MapLibreOfflineMapRepository.fetchDropOffMap` and its delete. They need `OfflineManager`, so they are device-only. The reconciliation's paths are tested.
- **`AppContainer`'s wiring:**
  - that the way-to really reads through the layered basemap;
  - that the copy is made under `files/offline_basemap`.

  Device-only.
- **The real server and network.** The tests use a fake server in an interceptor, with no network. Untested:
  - the edge's gzip, and OkHttp unwrapping it;
  - real timing;
  - a real 204;
  - how long the pass takes on the S22.
- **Real MapLibre writes while a pass reads.** It is the same read-only open the way-to already makes beside MapLibre, and session 13 saw no SQLite error with it (RECORD -2103). It hasn't been checked under a 600-tile read.
- **A pass cancelled midway** (the ViewModel cleared). Not tested. Each file is written whole or not at all, and the pass's mark is released in a `finally` [inferred].
- **A power loss mid-write.** Not tested. The read side of a short file is tested (`DropOffMapStoreTest`, "cannot be unwrapped").
- **A real 24 km area** (3,844 tiles) against the 4,000 cap. Not run; the cap test uses a 4,096-tile box.
- **The row's wrap in the phone's own fonts.** Robolectric measured w384dp only.

## Calls beyond the dispatch, for the planner to accept or reject

1. **"Try again" runs that area's pass too.** The dispatch's scope names "Try again" beside the opening; B1 P2 names only signal returning and a download's completion. "Try again" shows only after a failed route fetch, so it can't start a pass on the S22.
2. **The pass at a download's completion starts in `AvailabilityOfflineRoutes.noteDownloaded`, not in a ViewModel function.**
   - The ViewModel already hands every completed download there (`AvailabilityViewModel.kt:282`).
   - C1 keeps that 790-line file untouched, so I couldn't add the call there.
   - RECORD -1450 asks that start decisions stay in ViewModel functions, and this is the one place the rule bends. It is recorded in the doc comment at `AvailabilityOfflineRoutes.kt:102-106`.
3. **Passes run one at a time across every trigger,** behind a lock in `AvailabilityOfflineRoutes`. So the limits of 4 in flight and 10 a second hold for the whole app, not per trigger.
4. **Any status other than 200 or 204 counts toward the 3-in-a-row stop.** The Phase 1 report named 5xx, 429 and an unexpected 404. Without this, a 403 for every tile would ask for every tile.
5. **The 512 KiB cap applies to the body as read.** OkHttp has already unwrapped the edge's gzip, so the cap bounds the tile itself. That also rules out a small body that unwraps to something huge.
6. **The app gzips each tile itself before storing it.** The stored size then doesn't depend on what the edge chose to send, and the owner's "about 2 to 3 MB" holds.
7. **An area whose rectangle crosses the antimeridian is refused and logged:** unsupported rather than guessed. No area the archive serves is near it.
8. **A MapLibre tile that doesn't decode is not wanted,** to match step 2 of the read order.
9. **A pass that throws unexpectedly ends as a logged "not run",** never as a crash of its caller. Tiles written before it stay.
10. **A copy that can't be read is logged, and the way-to reads MapLibre's store alone,** rather than failing the way-to.
11. **A copy tile without the mark is discarded, like one that doesn't decode.** Only marked tiles are stored, so this can come only from damage.
12. **The way-to and the copy share one `MapLibreOfflineBasemap` instance** (`AppContainer.kt:343`). It caches the basemap source for the process, as the way-to's own instance already did.
13. **"Tests first" is two commits:** the stubs (c153a1a61), then the tests (1de3896ac).
14. **The revert runner builds each revert commit on a temporary index,** as described under "Revert checks", so the worktree never changes branch.
15. **R1 to R6 ran filtered** to 18 classes (164 tests). r7 to r9 ran the full suite, because their claim is "only its own test".
16. **Debug builds log each tile request** through the JSON client's logging interceptor: about 600 lines on the S22's first pass. Release builds log only the pass's own line.

## The owner's points settled during the build

- **RECORD -2112:** the notice's words, built as given.
- **RECORD -2113:** an unmarked 200 isn't stored and stays wanted, with no guard. The line that counts it is named above.

## Device check (for the planner on the S22)

**Before you start:**
- Use the debug APK from the last code commit's run (`app-debug-apk`, run 42011776915), or from a later run on this PR.
- The steps go cheapest first. Each names its pass condition and its evidence. Lines marked "observation" are recorded, but are not gates.
- Log tags:
  - `DropOffMap`: the copy's lines;
  - `AvailabilityViewModel`: the no-signal line;
  - `FindWayToWaypoint`: the way-to;
  - `okhttp.OkHttpClient`: each request, in debug builds.

**The steps:**

1. **Install, then check that no copy exists yet.**
   - Pass: `run-as <package> ls files` shows no `offline_basemap`.
   - Evidence: the command's output, and `forager.db` still at schema 23.
2. **The notice, with no signal** (airplane mode, if the owner allows it). Wait at least 5 s for the app's no-signal state, then open Records > Offline maps.
   - Pass: both areas' rows read exactly "Older map style. Download again for the new look and hill shading."
   - Pass: the log has exactly one line, "Offline maps opened with no signal: no drop-off map pass this time.", with no `DropOffMap` line, and still no `offline_basemap` folder.
   - Evidence: a screenshot, the logcat, and `ls`.
   - Observation: how many lines the notice takes at the phone's own text size. The test predicts 2 at the default size.
3. **The pass, with signal.** Turn Wi-Fi on, leave Offline maps, and open it again.
   - Pass: for each area, one `DropOffMap` line reading "whole. R requests; S stored, E empty.", with R = S + E.
   - If a line reads "… still wanted" instead, record it whole. Its "without the mark" count must be 0: a non-zero count is the server regression RECORD -2113 named.
   - Evidence: the logcat with its timestamps, and `run-as <package> sh -c 'for d in files/offline_basemap/*; do echo $d; ls $d | wc -l; du -sb $d; done'`.
   - Observation: the R counts against the expected 6 and 598 (fewer if online viewing refreshed more tiles), and the time from the first `us/15/` request to the "whole" line (about 60 s or more for Region 2).
4. **Size and budget.**
   - Pass: once the list reloads, each area's shown size has grown by about its folder's `du` (Region 2 by about 2 to 3 MB).
   - Pass: "Tile budget: 832 / 6000" is unchanged.
   - Evidence: screenshots from step 2 and from now, and the `du` from step 3.
5. **MapLibre's store is untouched by the pass.**
   - Pull copies of `mbgl-offline.db` before step 3 and after step 4, and query them read-only on the laptop.
   - Pass: `region_tiles` counts are unchanged for both areas.
   - Pass: their region-linked zoom-15 `us` rows keep their `length(data)`, apart from any row whose `accessed` moved, which is online drawing and not the pass.
   - Pass: the app's log shows no SQLite error.
   - Evidence: the two queries' output.
6. **A second opening, with signal.**
   - Pass: no `DropOffMap` line, and no `us/15/` request line: both areas are whole.
   - Evidence: the logcat.
7. **Navigate to Waypoint 1**, as in session 13, on a good fix.
   - Pass: the `FindWayToWaypoint` log no longer says "the waypoint's end: tile … has no barrier mark" for Waypoint 1's fourth reach tile, the tile RECORD -2103 names.
   - Evidence: the logcat and a screenshot of the card.
   - Observation, not a gate: what the card shows (a drop-off, a gated trail end, or one of the ruled notes), and any line for the walker's end. This depends on the map round the waypoint (the Phase 1 report, "Could not determine").
8. **Optional, only on the owner's word: a small test area, then its delete.** This covers the download-completion trigger and the user's delete path, which the JVM tests can't reach.
   - Pass: when the download finishes, a `DropOffMap` line appears for the new area's id.
   - Pass: after "Delete", that id's folder under `files/offline_basemap` is gone, with no "Couldn't delete … drop-off map copy" line.
   - Evidence: the logcat and `ls`.
9. **Optional, only on the owner's word: font scale 2.0.**
   - Observation: the notice's lines (the test predicts 4, each breaking between words).
   - Afterwards, set the font scale back.
