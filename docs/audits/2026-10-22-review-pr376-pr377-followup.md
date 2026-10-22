# Follow-up review of PR #376 (Worker world fill) and PR #377 (three map types): Amendments 3 and 4

**Reviewer:** Sorrel. **Model:** Claude Fable 5.1 (`claude-fable-5-1`). **Date:** 2026-10-22.
**Asked by:** Rowan (planner). **Scope:** read only. No code changed, nothing merged, nothing posted to GitHub.
**Follows:** `docs/audits/2026-10-22-review-pr376-pr377.md` (this branch, `02b57d07a`), whose High and two Mediums these commits answer. Amendments 3 and 4 are in `prompts/preserved/2026-10-22-01.md` on `origin/records-after-173`.
**Commits reviewed, and only these:** #376 `a9c4dd396`, `331a1a9f9`, `6ea87ed73`, `5b9196416` (head `5b9196416`); #377 `3c22e3cfc` (head). Both PRs still base on `origin/pre-main` at `01c7c5b56`, unchanged since the first review (`git log origin/pre-main -1`).

File:line references are at each PR's head unless they say otherwise. "Confirmed" means I read it; "inferred" and "unverified" are marked.

---

## Verdicts

| PR | Verdict | In one line |
|---|---|---|
| **#376** worker-world-fill | **Approve with notes** | The High is closed as Amendment 3 asked and the Mediums as Amendments 3 and 4 asked; the tests fail for the reasons predicted, and I confirmed each run. The notes: one assertion in the Amendment 4 test cannot fail on the fixture it runs against, and the z15 cut quietly changes what a southern offline download gets. Neither needs a change before merge. |
| **#377** three-map-types | **Approve** | The Medium is closed by the one condition Amendment 3 named, the test drives the fallback through its own entry point, the revert fails only that test at the line that names the case, and the head run is green. The log-line change is what Amendment 3 asked. |

---

## 1. Do the fixes close the findings as intended

### #376 High: the TileJSON bounds (`331a1a9f9`, tests `a9c4dd396`)

**Closed.** `src/index.ts:526-528`: for `name === STREET_ARCHIVE_NAME` only, and only when `getTileJson` gave a four-element `bounds`, `advertised.bounds = await worldFillBounds(env, t.bounds, pHeader.tileType)`. Everything else in the TileJSON is as before (`maxzoom` still raised to `OVERFLOW_MAX_ZOOM`, `:523-524`).

- **From the world archive's header, only when present, by one R2 `head()` at TileJSON time: confirmed.** `worldFillBounds` (`:311-331`): `env.BUCKET.head(key)` first (`:314`); a `null` returns the bounds unchanged with a `console.warn` (`:315-316`); then the world archive's own header through `R2Source`/`PMTiles.getHeader()` (`:318`); a tile type other than the Street archive's returns unchanged with a warning (`:319-322`); a `KeyNotFoundError` between the head and the read returns unchanged with a warning (`:325-328`); any other error is thrown, so it reaches the outer handler as a 500 that is not cached. Only the south bound moves: `[bounds[0], Math.min(bounds[1], header.minLat), bounds[2], bounds[3]]` (`:323`). That is Amendment 3's "south to -85.05, everything else as the header gives", and the `Math.min` means a world archive whose south is north of the Street archive's can never narrow the bounds.
- **No silent fallback:** each of the three skip paths logs. One path does not: `t.bounds` not a four-element array (`:526`) leaves the bounds untouched with nothing logged. pmtiles' `getTileJson` always writes a four-element `bounds` from the header (read on GitHub in the first review), so that branch is unreachable in practice. Noted, not a finding.
- **Other archives:** `/other.json` keeps its bounds and never calls `head()` (test at `worker.test.mjs:304-310`, asserting `bucketHeads` is empty). The world fill tile path (`:569-575`) is unchanged by these commits.
- **Inside coverage:** the TileJSON's west, east and north are the header's; the z0-14 tile path is untouched by all four commits (`git show` of each touches only `worldFillBounds`, the TileJSON branch, `tileNorthLat` and the one `if` in the overflow branch).
- **Cost and timing** are stated in the PR (TileJSON edge-cached a day, MapLibre caches it on the phone, purge or wait a day; `Cache-Control: public, max-age=86400` is set by `cacheableResponse` at `:492`). The `head()` is therefore once per edge location per day, not per tile.
- **The live effect, inferred from the first review's live `us.json`:** that header gives `[-180, 15.92097, 180, 85.04032]`, so the widened bounds are `[-180, -85.05, 180, 85.04]`, the whole world. The fixture's `[-124.85, -85, -66.87, 49.6]` is a test artefact of tiny.pmtiles's narrow header, not what the phone will see. Nothing in the code depends on the difference.

### #376 Medium (the straddling row at z0-8): no code change, as Amendment 3 ruled

The PR's deploy notes now say that an `r2` answer in the straddling row is the overlap rule working. That is what Amendment 3 asked for. Aspen's facts about `us.pmtiles`'s southern tiles are relayed, not checked here (the PR says so too).

### #376, Amendment 4: no z15 overflow south of the archive (`5b9196416`, tests `6ea87ed73`)

**Closed as asked.** `src/index.ts:532-539`: inside the existing `z > maxZoom && z <= OVERFLOW_MAX_ZOOM` branch, `if (tileNorthLat(tile[0], tile[2]) <= pHeader.minLat) return cacheableResponse(undefined, cacheableHeaders, 204)` before `overflowTileResponse` is called.

- **The archive's own `minLat`, not the widened bound: confirmed.** `pHeader` is `p.getHeader()` on the requested archive (`:510-513`), the PMTiles header; `worldFillBounds` writes only into the TileJSON response object and nothing reads it back. The 204 goes through `cacheableResponse`, so it is cached a day like the z9-14 204s.
- **A tile that straddles the line is handled sensibly: confirmed.** The test is the tile's *north* edge against `minLat`, so a tile that reaches north of the line keeps the overflow and only a tile lying wholly south is cut. With the live `minLat` 15.92097°, the z15 row that contains it (y 14915, north edge 15.9296°, south edge 15.9191°, my arithmetic from the Web Mercator formula) still overflows; the row below (north edge 15.9191°) is cut. One extra row of z15 tiles reaches the overflow, about 1.2 km tall; the right side to err on.
- **`tileNorthLat` is the standard formula** (`:203-205`), and the two tiles the tests name compute to the edges the comments give: 15/9641/15964 north edge 4.609°N (the comment says 4.61), 15/5303/11553 north edge 46.793°N (the comment says 46.79).
- **Inside the bounds the overflow is unchanged**, pinned by the Portland test (`worker.test.mjs:326-332`).
- **It applies to every archive, not only the Street one** (no `name` check at `:535`). For any other archive its TileJSON bounds are not widened, so MapLibre never asks for a tile south of its header and the cut is unreachable from the app there. Consistent, and not a problem; stated because the PR body says "us.pmtiles's own bounds" and the code says "the requested archive's own bounds".
- **The cut covers exactly what the widening newly reaches:** only the south bound was widened, and only the south is cut. Had west, east or north been widened the overflow would run there, but they were not.

### #377 Medium: a Trail user already fallen back to OSM (`3c22e3cfc`)

**Closed.** `ui/map/NoSignalTrailMap.kt:45-46`:
```
internal fun drawsFromDownloads(useOfflineTiles: Boolean, basemap: Basemap, noSignal: Boolean, trailMapDraws: Boolean = true): Boolean =
    useOfflineTiles || (noSignal && (basemap != Basemap.FORAGER_TRAIL || !trailMapDraws))
```
`rememberDrawsFromDownloads` (`:61-65`) feeds `fallback.ownStreet()` from `OWN_STREET_FALLBACK` by default; `ownStreet()` reads `fellBackState.value`, Compose state (`OwnStreetFallback.kt:29-40`), so the composition follows the flip. Both production callers are untouched (`SightingsMap.kt:590`, and `:596`/`:744` consume the result), so the real map gets the fix through the default parameter.

- **The behaviour as steps, confirmed from the code:** Trail chosen, style failed (`onStyleLoadFailed` sets `fellBack`), no signal for 5 s → `drawsFromDownloads(false, FORAGER_TRAIL, true, trailMapDraws=false)` is `true` → the offline branch, exactly what an OSM user gets. Signal back for 30 s → `false` → `mapStyleSourceFor` with `ownStreet = false`, OSM's raster (the fallback still holds until a cold start, `OwnStreetFallback.kt:21-22`). It cannot re-trigger the fallback: `fallsBack(source, useOfflineTiles = true)` is `false` (`OwnStreetFallback.kt:74-79`), unchanged.
- **The test** (`NoSignalTrailMapTest.kt:69-96`) provides `LocalNoSignal` as `MainActivity` does, uses a fallback instance of its own, and asserts three states: Trail drawing with no signal stays on its own style (`false`); after `fallback.onStyleLoadFailed(MapStyleRequest(Uri(STREET_STYLE_URL), useOfflineTiles = false), …)` it draws from the downloads (`true`); signal back gives the raster again (`false`). The fallback is driven through its real entry point and the map's real reader. It does not go through `SightingsMap` itself; the dispatch asked for the real entry point and this is the fallback's, so I accept it.
- **The log line** (`AvailabilityMapDisplay.kt:253`): `replacement.label` → `replacement.basemap.label`, so the line names "OpenTopoMap" rather than the chip "Topo"; the two tests' expected strings follow. One word, as Amendment 3 allowed. The sibling line that still says "opening on Trail" is listed in the PR as a known Low, as Amendment 3 asked.

---

## 2. The disclosed gap: can the later assertions in the Amendment 4 test fail

The test is `worker.test.mjs:317-324`. The revert run (`39835015697`) failed on `assert.equal(response.status, 204)` at `:319` (`404 !== 204`), so `:321-323` were never seen failing. The question is whether each could fail on its own if the code answered 204 but still fetched, read or wrote. Judged from the fakes in the same file:

| Assertion | Can it fail independently of the status? | Why |
|---|---|---|
| `:321` `fetchCalls` is empty | **Yes.** | `globalThis.fetch` is replaced at module scope (`:92-104`) and pushes every call before answering, whatever the Worker then returns. If the overflow ran, `resolveRemoteBuildUrl` would `fetch(…, {method: "HEAD"})` (`index.ts:181`) because its edge-cache key lives in `caches.default`, which `beforeEach` replaces with a fresh `FakeCache` (`:115`); the build URL is never cached across tests. So any path through the overflow leaves at least one entry. |
| `:322` no `overflow/` read | **Yes.** | `bucket.get` pushes every key before deciding what to return (`:50-51`), and `env.BUCKET.get(overflowCacheKey)` is the first statement of `overflowTileResponse` (`index.ts:226-228`), before any status is chosen. |
| `:323` `bucketPuts` is empty | **No, not on this fixture.** | The fake "live build" is `ARCHIVE`, tiny.pmtiles (`:95-101`), whose header stops at z14. `overflowTileResponse` returns 404 at `index.ts:241-243` for every z15 tile, before `getZxy` and before the `put` at `:253-257`. The only path that writes to R2 is unreachable with the data the test can see, so this assertion passes for any code, including a Worker that answered 204 and then wrote. The `put` fake itself is sound (`:62-64`), and `call()` awaits `ctx.waitUntil` promises (`:85-89`), so a write would be recorded if one happened; the fixture is what keeps it from ever happening. |

So two of the three side-effect assertions are live and one is not. This is the family CLAUDE.md names as "a check that passes because it never saw the data that could fail it": the assertion and the thing it checks are decoupled by a fixture that cannot produce the case. It also means the Worker's R2 write path (`index.ts:253-257`) has no test at all, before or after this PR; that was true at pre-main and is not new.

**What would make `:323` able to fail** (for the planner; nothing changed here):
- Give the fetch fake a live-build fixture that holds a z15 tile, for example a `live-build.pmtiles` built the way `make-world-south-pmtiles.py` builds the world fixture, with `maxZoom 15` and the two tiles the tests name (15/9641/15964 and 15/5303/11553). Then the Portland test would answer 200 and could assert `bucketPuts` contains `overflow/us/15/5303/11553.mvt`, which is the first test the write path would have; and the Bogotá test's "no write" would be a claim the fixture can contradict. A revert of the cut would then fail `:323` as well as `:319`.
- Independently of the fixture, the test stops at its first failed assertion. Asserting the side effects before the status, or comparing one object `{status, fetched: fetchCalls.length, overflowReads, puts}` against `{204, 0, [], []}` in a single `deepEqual`, would show all four in one revert failure message instead of one.

---

## 3. Does widening the TileJSON bounds change anything for offline downloads

**No, for the bounds themselves. Confirmed from MapLibre Native at tag `android-v13.5.0`,** read through the GitHub API today (`platform/default/src/mbgl/storage/offline_download.cpp`; the `src/mbgl/storage/` path the first review would have guessed does not exist at that tag).

- A region download enumerates tiles with `tileCover(definition, type, tileSize, tileset.zoomRange, fn)` (`offline_download.cpp:453-454`), which iterates `coveringZoomRange(reg, type, tileSize, zoomRange)` and at each zoom covers `reg.bounds`, the *region definition's* bounds (`:61-76`). `tileCount` for the progress total does the same (`:78-95`). Neither function receives `tileset.bounds`; the only `Tileset` fields read are `zoomRange`, `tiles[0]` and `scheme` (`:454-463`). So the TileJSON's `bounds` never reaches the download, and the app's `OfflineTilePyramidRegionDefinition(offlineDownloadStyleUrl(), region.toLatLngBounds(), MIN_ZOOM 10, MAX_ZOOM 15, density)` (`MapLibreOfflineMapRepository.kt:114-120`) enumerates exactly what it did before. The app's own pre-flight estimate (`EstimateOfflineTileCount.kt:44-57`) reads only the region and the two zoom constants, and `SERVED_MAX_ZOOM`'s doc reads `maxzoom` from `us.json`, not `bounds` (`OfflineMapRepository.kt:128-160`).
- The offline style (`src/offline-style.json:7, 136`) points at the same `/us.json`, so pre-Trail regions' render style sees the widened bounds too. For rendering from the store that changes nothing a user can see: tiles outside what was downloaded are not in the store either way.

**What the two amendments do reach, beyond the live map:**

1. **z9-14 south of 15.92°N on the live map.** Before, MapLibre never asked; now it asks and each tile answers 204 from `index.ts:576` (the fill stops at `WORLD_FILL_MAX_ZOOM` before touching the world archive). MapLibre's Android HTTP source treats 204 as `noContent` (`platform/android/MapLibreAndroid/src/cpp/http_file_source.cpp:172-173` at the tag), not an error, so there is no retry and the tile is simply empty. The cost is one Worker invocation per tile per edge location per day; the dispatch accepted "grey from z9 up".
2. **A southern offline download at z15, changed by Amendment 4, not by the bounds.** The picker's centre pin is not bounded by the TileJSON, so a region south of 15.92°N could always be downloaded. At pre-main such a download's z15 tiles (z10-14 were already 204) went through the overflow: a live read from Protomaps' build and an R2 `put` for each tile. After `5b9196416` they answer 204. MapLibre stores a `noContent` response as a row with NULL data (`offline_database.cpp:474-476, 501-503`), so a southern pack is now rows of nothing at every zoom, where before it held live z15 tiles. That is what "Stop it" means and I agree with it; it is not in the PR body, and worth a line in the deploy notes, because the world fill now makes that area look mapped in the picker at z8 and below while a download there yields nothing. Whether the picker should say so is a UX question for the owner (the honest-copy rule), outside both dispatches.
3. **The MapLibre readers of `tileset.bounds` I checked** are the tile pyramid (`render_vector_source.cpp:32` → `tile_pyramid.cpp:178-188`, first review) and the offline download above. I did not enumerate every reader in the library; "no other effect" is a claim I am not making.

---

## 4. The run IDs, spot-checked

Each read with `gh run view` or the jobs API today; failed Worker jobs' logs read through `gh api …/jobs/<id>/logs`.

| Run | Branch, commit | Result | What the log shows |
|---|---|---|---|
| 39824322127 | `worker-world-fill` `331a1a9f9` | **success**, five jobs green, Tile Worker job ran | not read beyond the conclusion |
| 39824332688 | `scratch-wwf4-tests-only` `a9c4dd396` | failure, Tile Worker job only | `Total Upload: 375.30 KiB` (built); 19 tests; one failure, `us.json: bounds[1] 24.4, expected -85` |
| 39824337558 | `scratch-wwf4-revert-bounds` `cc94d80b5` | failure, Tile Worker job only | `Total Upload: 375.41 KiB`; 19 tests; the same one failure, same message. `git diff 331a1a9f9 cc94d80b5` is one deleted line, the `advertised.bounds =` assignment. |
| 39835005188 | `worker-world-fill` `5b9196416` (PR head) | **success**, five jobs green | Tile Worker job: `Total Upload: 376.79 KiB`; `tests 21, pass 21, fail 0, skipped 0` |
| 39835011541 | `scratch-wwf5-tests-only` `6ea87ed73` | failure, Tile Worker job only | `Total Upload: 376.48 KiB`; 21 tests; one failure, the Bogotá case, `actual: 404, expected: 204` |
| 39835015697 | `scratch-wwf5-revert-overflow` `3c221d186` | failure | Tile Worker job: `Total Upload: 376.74 KiB`; 21 tests; the same one failure, same message. `git diff 5b9196416 3c221d186` is one changed line (`if (false && …)`). **Observation:** the "Build, test, publish APK" job on this run is still reported `in_progress` with no completion time, 25 minutes after the run was marked completed/failure (`gh api …/runs/39835015697/jobs`). Immaterial to the Worker evidence, which is the Tile Worker job alone; recorded because a run state that does not add up is the kind of thing this project has learned to write down. |
| 39824475818 | `scratch-tmt-revert-fallback` `9a6c69f42` | failure, "Build, test, publish APK" job only | `> Task :app:compileDebugUnitTestKotlin` present and zero ` e: ` lines, so the reverted build compiled; `5 tests completed, 1 failed`: `NoSignalTrailMapTest > a Trail user fallen back to OSM draws from the downloads with no signal, like an OSM user FAILED … AssertionError at NoSignalTrailMapTest.kt:91`. Line 91 is `assertEquals("fallen back to OSM, no signal: from the downloads", true, trail)`, the one assertion this revert can break. `git diff 3c22e3cfc 9a6c69f42` is the one condition put back. |

Each failing run's failure is one its own edit could produce and none belongs to another edit; each built its own bundle (distinct `Total Upload` sizes). Beyond the seven: **39824450646** on `3c22e3cfc`, #377's head, completed **success** (conclusion only; the suite counts in the PR body are as reported, not re-read).

---

## Findings, by severity

| Severity | Where | Finding |
|---|---|---|
| Low | `server/pmtiles-worker/test/worker.test.mjs:323` | The "no R2 write" assertion cannot fail on the fixture: the fake live build stops at z14, so the overflow's `put` (`index.ts:253-257`) is unreachable for any z15 tile and the Worker's write path has no test. Section 2 says what would make it bite. |
| Low | `server/pmtiles-worker/src/index.ts:532-537` (PR body, deploy notes) | Amendment 4 changes what an offline download south of 15.92°N gets at z15: 204 rows instead of live Protomaps tiles. Not in the PR body; worth a line, with the picker now showing land there at z8 and below. |
| Low | `server/pmtiles-worker/src/index.ts:535` | The cut applies to every archive's own `minLat`, not only the Street archive's. Unreachable for the others from the app; stated because the PR body says "us.pmtiles's own bounds". |
| Note | `server/pmtiles-worker/src/index.ts:526` | A `bounds` that is not a four-element array leaves the TileJSON untouched without a log line. pmtiles always writes four, so unreachable; recorded against the no-unlogged-fallback rule. |
| Note | run 39835015697 | The APK job reads `in_progress` on a run marked completed. Immaterial to the Worker evidence. |

Nothing against #377.

## What I did not check

- The live deploy, the live `us.json` after it, and every device step (the two PRs' lists plus the first review's ten). The one that decides whether the High is closed on a phone is #376's step 6, Bogotá at z8 after a cache clear.
- Aspen's facts about `us.pmtiles`'s southern tiles and the world archive's header (relayed in the PR).
- The suite counts and case-by-case comparison for 39824450646, and the Tile Worker log of 39824322127: conclusions only.
- Whether `tsc --noEmit` would pass: the job bundles with esbuild and does not type-check (PR body, "Not verified"). `R2Bucket.head()` exists in `@cloudflare/workers-types`, so I expect no issue, but that is inferred.
- Every reader of `Tileset::bounds` in MapLibre Native (section 3, item 3).
- No CI, no Gradle, no npm, no emulator, nothing run.
