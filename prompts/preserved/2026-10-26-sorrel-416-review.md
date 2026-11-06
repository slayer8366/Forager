# Sorrel review 416: step 8a, Trail Map v3 with the official-trails band (app stays on v2)

Sorrel Tally (Fable), 2026-10-26, fresh session. Read-only on code; this file is the only write.
PR #416, branch `step8a-reference-style-v3`: `cbdc359b7` (tests over stubs), `6cfed1aba` (build), off pre-main
`c91f34c49`. Reviewed against dispatch `prompts/preserved/2026-10-26-14.md` with Amendment 1 (origin/records-after-173),
RECORD -1633, -1677, -1678 (and -1679, the planner's hand-off row), and the step 8 report
`docs/audits/2026-10-25-05-reference-layer-report.md` §2, §7, §8 (branch later-steps-reports).

Every claim below is marked **read** (seen in a file at a cited line), **computed** (run here, with the tool named) or
**inferred** (reasoned, not observed).

## Verdict

**Blocking:** none.

**Should-fix (one, documentation inside the PR's own scope):**
1. `server/pmtiles-worker/README.md:44-58`, the Worker's route table, has no row for `/style/street-v3.json`; its last
   style row is v2 (`:54`, "what the app loads from v2 on"). The dispatch's deliverable is the route; the table that lists
   routes should carry it, with "not loaded by the app until the switch PR" (read).

**Notes** (nothing here needs a change before merge; the ones marked *switch PR* are for the dispatch that moves the
app to v3, and are what Amendment 1 asked the coder to record):
- *switch PR:* the region row's "Tiles" and "Tile budget: x / 6000" will double, confirmed from source (§7 below).
- *switch PR / Aspen:* the Worker's z15 overflow has no archive-name check, so an archive built to z14 would have its
  z15 requests answered from the public basemap build and written into R2 under `overflow/reference-trails/…` (§6).
- *go-live:* a tile 404 ("Archive not found") is put in the edge cache for a day per data centre, and the phone keeps a
  404 tile as "no content" for the same `Cache-Control` day (§6). Nothing requests these URLs today.
- the synthetic fixture's two tiles lie north of the bounds its own header declares (§9); harmless to every test.
- `npm run check:street` and `tools/check-street-night.mjs` still validate v2 only; nothing in CI validates v3 against
  the style spec. I ran the validator on v3 myself: 0 errors (§4).
- the coder's six beyond-scope calls were not available to me (not in the PR body, the commits, or any file); §10 lists
  the decisions I could see that go beyond the dispatch's words.

**Mergeable: yes.** GitHub reports `MERGEABLE` / `CLEAN`; `git merge-tree --write-tree origin/pre-main 6cfed1aba` is
clean; `origin/pre-main` is still `c91f34c49`, the PR's base, so no drift to re-check (computed).

**One-line answer:** the PR does what Amendment 1 asked and nothing the app can see; v3 is v2 plus one source and three
layers, byte-for-byte reproducible; the app tree is untouched. Merge is the owner's yes.

## Mechanism prediction (written before computing)

1. The three v3 JSONs differ from v2 only in `name`, `metadata.forager:generated-from`, one added source and three
   added layers, with every v2 layer present in the same order.
2. No file under `app/` changes.
3. The generic `/{name}/…` path serves any archive name, so for `reference-trails` the z15 overflow is reachable with
   no name check, exactly as the dispatch's `index.ts:540-556` pointer says.
4. Each of the four CI reverts fails exactly one test with a message only that edit could produce.
5. The app's "Tile budget" sums each region's `completedTileCount`, which MapLibre counts over every tile source, so a
   v3 region roughly doubles the figure.

**Scored at the end:** all five held (§3, §1, §6, §8, §7). Not predicted: the fixture's bounds oddity (§9) and the README
row gap. One thing I could not settle either way: whether the online map requests z10-11 reference tiles when the band
layers start at z12 (§6, inferred, unverified).

## 1. The app is unchanged (the dispatch's "no visible change", Amendment 1's "the app stays on v2")

- `git diff --name-only c91f34c49 6cfed1aba | grep '^app/'`: **zero files** (computed). Nine files changed in all:
  three generated v3 JSONs, `generate-street.mjs`, the new `overrides/forager-reference.mjs`, `index.ts`, the fixture
  script, the fixture, `worker.test.mjs`.
- Blob ids identical at base and head (computed, `git rev-parse <commit>:<path>`): `StreetStyle.kt` 5a7ef7db96eb,
  `BasemapStyles.kt` ade36da98f66, `EstimateOfflineTileCount.kt` af675ae6f69a, `OfflineMapRepository.kt`
  0b0ccdd33641 (holds `TILE_COUNT_LIMIT = 6000L` at :135 and `MAX_RADIUS_KM = 24` at :241, read),
  `MapLibreOfflineMapRepository.kt` af58587d58ca.
- Step 7 PR 3's files are therefore untouched too (no `app/` file changed).
- The Android CI jobs on the PR run (40378668913) are green, which for an unchanged app tree says only that pre-main
  built; it is not evidence about v3 (read from the run's job list).

## 2. Baseline

- PR head `6cfed1aba`, base `c91f34c49` = `origin/pre-main` = merge-base (computed). Two commits, both signed
  "Co-Authored-By: Claude <noreply@anthropic.com>", no model name (read).
- The stub commit `cbdc359b7`'s `src/street-style-v3.json` is v2 with only `name` changed to "Forager Street v3"
  (computed, Python key-by-key comparison); `index.ts` had no v3 import or route then (read from the build commit's
  diff adding both).
- Node pinned in CI at 24.22.1 (`.github/workflows/ci.yml:702`, read); `@protomaps/basemaps` 5.7.2
  (`map-style/package.json`, read); MapLibre `13.5.0` (`gradle/libs.versions.toml:43`, read).

## 3. v3 = v2 plus the reference source and three layers (computed, Python over the committed files)

- `server/pmtiles-worker/src/street-style-v3.json` == `map-style/forager-street-v3.json` byte for byte
  (sha256 `7ad55d46…`); the preview copy differs from them only in `glyphs` and `sprite` (localhost bases), exactly as
  v2's preview does from v2.
- Top-level keys differing between v2 and v3: `name` ("Forager Street v2" → "Forager Street v3"), `metadata`
  (`forager:generated-from` gains "and overrides/forager-reference.mjs"; `forager:sprite-icons` and `forager:palette`
  unchanged), `sources` (adds `reference`), `layers` (93 → 96). Nothing else.
- `sources.protomaps` identical to v2's. `sources.reference` =
  `{"type":"vector","tiles":["https://forager-pmtiles.brandonlee1-894.workers.dev/reference-trails/{z}/{x}/{y}.mvt"],"minzoom":10,"maxzoom":15}`,
  no `url` (the inline address Amendment 1 chose).
- v3's layers with `source != "reference"` equal v2's 93 layers in order (list equality, computed).
- **Regenerated here** with Node 24.22.1 and the coder's worktree `node_modules` (basemaps 5.7.2) and fetched sprite
  assets, symlinked read-only into a scratch copy of the PR tree: all three outputs **byte-identical** to the committed
  files (`sha256sum -c` OK; git blob ids 02e32f789c62 / 903f0b4950ec match). No hand edit in the generated files
  (computed). The sprite JSON only feeds `forager:sprite-icons`, which equals v2's, so the dependency on the coder's
  fetched assets does not weaken this.

## 4. The band: order, filters, zooms, colour, label (computed from the JSON; design read in `forager-reference.mjs`)

- **Order.** Trail layers (`roads_path_*`) are 18, contiguous, at indices 42-59 in v3 (40-57 in v2, as the report §2
  says). `reference_fs_band` at 40 and `reference_blm_band` at 41 sit directly below `roads_path_track` (42), with
  `roads_other` below them; `reference_fs_number` at 60 sits directly above `roads_path_hard_faint` (59) and below
  `roads_link`, `roads_minor_service`, `roads_minor`, … So every trail line draws over both bands, and the number is
  under every road layer and every road label (a road crossing a trail at z14+ draws over its number; the coder's stated
  aim was "no trail line crosses it", which holds).
- **Filters.** `reference_fs_band`: `["!=", ["coalesce", ["get","trail_type"], ""], "SNOW"]`; `reference_fs_number`:
  `["all", <that>, ["has","trail_no"]]`; `reference_blm_band`: no filter. The `coalesce` means a feature with no
  `trail_type` is kept (absence is not snow), the right direction (read). `SNOW` and `trail_type` are premises for
  Aspen's archive, held in one place with the archive name, source-layer names and zooms (`forager-reference.mjs:21-43`,
  read); the step 8 report §7 names the field but not the value, so the value `SNOW` is the coder's premise (inferred
  from the Forest Service dataset's upper-case codes; not checked against the service here).
- **Zooms.** Bands `minzoom` 12; number `minzoom` 14; source 10-15. Width `["interpolate",["linear"],["zoom"],12,6,15,10]`
  (6 at z12 to 10 at z15, clamped above). Matches the report §2 "6 to 10 dp, from zoom 12, label from 14".
- **Colour.** `line-color` `#7b5ea7`, `line-opacity` 0.35 on both bands; text `#7b5ea7` with `#ffffff` halo 1.5, size 10,
  font `Noto Sans Regular` (a font v2 already uses in 5 layers and the Worker serves; read). The night recolour walks
  `line-color`, `text-color` and `text-halo-color` (`OfflineNightRecolourApply.kt:79-80`, read), so the band and its
  label invert with the rest; whether inverted violet reads is device-only (report §6).
- **Visibility.** No `layout.visibility` on any of the three (default visible), which is -1633's "On" as the dispatch
  says to follow until 8b's switch; the test asserts `visibility != "none"` (read). The report §8's row 8a said
  "visibility none"; the dispatch overrode that, and the coder followed the dispatch.
- **Style-spec validity.** `validateStyleMin` from `@maplibre/maplibre-gl-style-spec` 26.4.4 (the coder's
  `node_modules`): 0 errors for the Worker copy, the app copy and the preview of v3 (computed). The repo's own
  `tools/check-style.mjs` on the v3 preview: 96 layers, all fonts and sprites present, the same one
  `generic_shield-0char` note v2 has (computed). Nothing in CI runs either on v3; `check:street` is pinned to v2
  (`map-style/package.json`, `generate-street.mjs:11-16`, read).

## 5. The v2 freeze (computed)

sha256 of all three v2 copies at `c91f34c49` and at `6cfed1aba`: Worker and app copies `7547507b…` (identical to each
other and across commits), preview `d07ce0e3…`; the test "v2 is frozen" holds exactly these three values
(`worker.test.mjs:419-430`, read). v1 stays held by `tools/check-street-night.mjs:27-31` (read). The freeze test has
never been red in CI (it describes existing state, as the coder disclosed); I made it red locally (§8, revert B).

## 6. The Worker: route, generic path, overflow, 404s; what MapLibre does with each (read at android-v13.5.0)

- **Route.** `index.ts:480-482` adds `/style/street-v3.json` → `streetStyleResponse(…, streetStyleV3)`, the same
  function v1 and v2 use (`:420-427`: JSON, `Cache-Control: public, max-age=86400`, CORS origin; read). v2 and v1
  routes untouched (diff read). The bundle grew 724.69 → 1052.15 KiB (gzip 45.44 → 58.07 KiB) between the stub and
  build runs (read from the two `worker-build.log`s), since v3 is now bundled beside v2 and v1; the dry run completed.
- **Generic path.** `shared.ts:13-16` matches any `/{name}/{z}/{x}/{y}.{ext}` and `/{name}.json`; `index.ts:536-538`
  opens `R2Source(env, name)` with no allow-list (read). So `reference-trails` is served with no code of its own, as the
  tests show against the fixture: z10 200 from R2 only, z15 200 from the archive (the fixture's `live-build.pmtiles`
  holds the same z15 index with a different marker, so an overflow answer would have read "live …"; §9), a missing tile
  204 with no world-fill or `us` read, TileJSON with the archive's own bounds and no `head` of the world archive, and
  404 with no Protomaps read or R2 write when the archive is absent (`worker.test.mjs:500-543`, read; passing in all
  five green or forward runs, read from logs).
- **The overflow path has no name check.** `index.ts:559-566`: for any `name`, a tile above the archive's own
  `maxZoom` and ≤ `OVERFLOW_MAX_ZOOM` (15, `:622`) goes to `overflowTileResponse`, which reads the public basemap's
  daily build and writes the tile to R2 as `overflow/{name}/{z}/{x}/{y}.{ext}` (`:211-263`, read). Only the
  southern-latitude guard (`tileNorthLat <= pHeader.minLat` → 204, `:561-563`) intervenes. The fixture is built to
  z15, so the z15 test passes by the fixture's shape, not by any guard: **if Aspen's archive stops at z14, every z15
  request for the band would be answered with basemap tile bytes (which carry no `fs_trails`/`blm_trails` layer, so the
  band draws nothing) and written into R2 under `overflow/reference-trails/`, and the public build host would see the
  traffic.** The coder recorded "the archive must be built to zoom 15" in `forager-reference.mjs:17-19` and -1678's
  notes; the dispatch's stop "the Worker needing more than serving the style and the generic path" is why no guard was
  added, which I agree with for this PR. For the switch PR: either a name check on the overflow (owner's call), or a
  go-live check that `reference-trails.pmtiles`'s header `maxZoom` is 15. The same path also means the TileJSON
  `/reference-trails.json` advertises `maxzoom` 15 whatever the archive says (`:550-551`), unused by v3 (inline address).
- **404 caching at the edge.** Every tile-path response, 404 "Archive not found" included (`:600-602`), goes through
  `cacheableResponse`, which sets `Cache-Control: public, max-age=86400` (or `env.CACHE_CONTROL`) and calls
  `cache.put(request.url, …)` unconditionally (`:510-526`, read). Cloudflare's documented refusals for `cache.put` are
  206, `Vary: *`, `Set-Cookie`, a non-GET, `Cache-Control` no-store/private and size (developers.cloudflare.com
  Cache API page, read today via fetch); 404 is not among them, and "the contents of the cache do not replicate outside
  of the originating data center". So a tile 404 is cached for a day **per data centre** (inferred from the two reads).
  No phone or test asks for these URLs today (the app is on v2), so nothing is cached now; at go-live, do not probe
  reference tile URLs on the production Worker before the archive is uploaded, or expect up to a day of empty tiles at
  that data centre. The existing test "a missing font range is a 404 and is not cached" (`:247`) covers the asset path,
  a different code path; no test asserts the tile 404's caching either way (read).
- **What MapLibre 13.5.0 does with a 404.** Android's HTTP source, `platform/android/MapLibreAndroid/src/cpp/http_file_source.cpp:172-177`
  (fetched at tag `android-v13.5.0`, commit `efbde815…`): `code == 204 || (code == 404 && resource.kind == Tile)` →
  `response.noContent = true`; any other 404 → `Error::Reason::NotFound`. `src/mbgl/util/http_timeout.cpp:16-32`:
  `NotFound` is not a retried reason (`Duration::max()`). `src/mbgl/style/sources/tile_source.cpp:45-51`: a source
  given a `Tileset` (inline `tiles`) is `loaded = true` at once with no request; `:60-62`: a TileJSON URL whose request
  errors calls `onSourceError` and `loaded` stays false. `src/mbgl/tile/tile_loader_impl.hpp:183`: a `noContent` tile
  is `setData(nullptr)`, an empty tile, no error. The offline download, `platform/default/src/mbgl/storage/offline_download.cpp:522-533`:
  an error response calls `observer->responseError` (which is what reaches `downloadErrorKind`,
  `MapLibreOfflineMapRepository.kt:449-452`: 404 is `Final`, read), and `NotFound` is then skipped; a `noContent`
  response is not an error and is buffered for storage. All of this confirms the coder's citations in
  `forager-reference.mjs:10-19` line for line (read). The claim "online the style never counts as loaded, so no frame is
  ever full" follows from `tile_source.cpp:61-62` (`loaded` never set) and is the premise -1678 was ruled on; I did not
  trace it further into the style's loaded state (inferred).
- **Does the online map fetch z10-11 reference tiles when the bands start at z12?** Not determined. The offline download
  certainly does (it covers the source's declared range, `offline_download.cpp:265-266` → `queueTiles` over the
  `Tileset`'s zoom range, read); online tile requests depend on whether any layer of the source is in range, which I did
  not read. Unverified; only the offline count matters for §7.

## 7. For the switch PR: the native tile limit and the region row (read; the dispatch asked for this to be recorded)

- **MapLibre's own tile-count limit never fires for Forager's downloads.** `offline_database.cpp:1454-1457`:
  `exceedsOfflineMapboxTileCountLimit` requires `resource.kind == Tile && util::mapbox::isCanonicalURL(tileServerOptions, url)`;
  `mapbox.cpp:44-51`: canonical means the URL starts with the configured scheme alias + `://`;
  `tile_server_options.cpp:213-223`: the default configuration is MapLibre's, alias `maplibre`. Forager's tile URLs are
  `https://…`, so the limit set at `MapLibreOfflineMapRepository.kt:274` counts nothing, before or after v3. This also
  explains the 2026 hardware observation recorded at `OfflineMapRepository.kt:112-121` (9,118 tiles past a "limit" of
  6,000). The coder's -1679 note ("it counts only its own scheme's tile URLs") is confirmed (read).
- **"Tiles" per region doubles, and so does the budget line.** `offline_database.cpp:1058-1068` (`putRegionResources`):
  `completedTileCount += 1` for every `Resource::Kind::Tile` stored, and `putTile` stores a `noContent` tile as a row
  with NULL data (`:678-683`, `:708-713`), so empty band tiles count one each. The app's row takes
  `finalStatus.completedTileCount` (`MapLibreOfflineMapRepository.kt:205`), the panel shows
  `"Tile budget: $tilesUsed / 6000"` with `tilesUsed = offlineRegions.sumOf { it.tileCount }`
  (`OfflineRegionsList.kt:125`; `AvailabilityOfflineMapsUi.kt:288`), and the gate refuses when
  `estimateServedOfflineTileCount(region) > TILE_COUNT_LIMIT - sumOf { tileCount }`
  (`AvailabilityViewModelOfflineMaps.kt:116-122`; `AvailabilityOfflineMapsUi.kt:225-226`). The reference source's zooms
  10-15 equal the download's 10-15 (`OfflineMapRepository.kt:159-160`), so a v3 region stores one band tile per basemap
  tile: a 24 km region, 4,405-5,246 basemap tiles by the repository's own table (`OfflineMapRepository.kt:218-224`,
  read), would show roughly 8,800-10,500 tiles, the budget line would read over 6,000 after one download, and the next
  download would be refused while the estimate itself (basemap only, per -1678) stays unchanged. The coder's -1679
  note is confirmed from source; the owner's ruling on what the row and the gate should count is the switch PR's
  question, as -1679 parks it.
- The downloaded size (`completedResourceSize`) grows only by the band tiles' real bytes; empty tiles add 0
  (`putInternal` size 0 when no data, `:317-325`, read).
- Not checked: whether anything in the app depends on layer indices rather than ids (trail layers moved from 40-57 to
  42-59). The app does not load v3 yet, so this is for the switch PR to grep (unverified).

## 8. Evidence: fail-first, forward, reverts, PR run (read from the job logs; reverts' diffs read from GitHub by sha)

| Run | Branch @ sha | Worker job | Build | Result |
|---|---|---|---|---|
| 40378125467 fail-first | step8a @ `cbdc359b7` | 121766701134 failure | dry run completed, 724.69 KiB | 52 tests, 6 failed: the six v3 tests (route `404 !== 200`; structure tests against the renamed v2 stub). The six generic-path and freeze tests passed, as the coder said they would (they describe existing behaviour). The four Android jobs were cancelled, not failed. |
| 40378294048 forward | step8a @ `6cfed1aba` | 121767554974 success | 1052.15 KiB | 52 tests, 0 failed (JUnit XML downloaded: 52 `testcase`s; 35 in `worker.test.mjs` + 17 in `routing.test.mjs`, counts from `grep -c '^test('`; 23 → 35 is the 12 new tests the body claims) |
| 40378389564 revert route | revert-8a-route @ `8d38429d4` | failure | 724.69 KiB | 1 failed: the route test, `404 !== 200`. Diff: `index.ts` −3 lines, the v3 route removed. |
| 40378399785 revert snow | revert-8a-snow @ `4995539ba` | failure | 1051.97 KiB | 1 failed: the snow test, `actual undefined` vs the `notSnow` array. Diff: `forager-reference.mjs` passes `null` for the FS band's filter; the three JSONs regenerated (−12 lines each). |
| 40378409128 revert order | revert-8a-order @ `6bcb0e209` | failure | 1052.15 KiB | 1 failed: the order test, actual `{fsBand 58, blmBand 59, number 60}` vs expected `{38, 39, 58}`. Diff: `withReferenceLayers` puts bands after the trail layers; JSONs regenerated (68/68 lines moved). |
| 40378419080 revert source | revert-8a-source @ `f9cdbe178` | failure | 1052.12 KiB | 1 failed: the source test, actual `url: …/reference-trails.json` vs expected `tiles: […]`. Diff: `referenceSource` returns `url`; JSONs regenerated. |
| 40378668913 PR | step8a @ `6cfed1aba` | 121768603484 success | 1052.15 KiB | 52 tests, 0 failed; all five jobs green |

Each revert is a child of `6cfed1aba` (parents read), each build log shows wrangler's dry run completing with a
"Total Upload" line and no compile error, and each failure names the one test only that edit could break (the expected
values in the order revert, `{38, 39, 58}`, are what the test computes when the bands follow the trail layers, so the
failure is this revert's and not a stale run's). The revert branches are deleted on GitHub; the commits were read by sha
through the API. The coder regenerated the JSONs and rebuilt for each revert, which is why exactly one test failed each
time.

**My own revert check** (reviewer profile step 5), locally with Node 24.22.1 against the CI-built bundle
(`pmtiles-worker-dist` artefact of the forward run; identical sha to the PR run's), on a scratch copy of the PR's
`server/pmtiles-worker/{src,test}` and `map-style/{overrides,forager-street-v2*.json}`, running `worker.test.mjs` alone
(35 tests), restoring from copies saved before editing, never from git, and checking the restored sha each time:
- A first attempt had a scratch missing `map-style/forager-street-v2.json`, so the freeze test failed on `ENOENT` and my
  baseline was 34/35. That failure was the scratch's, not the code's; the input had not been checked (CLAUDE.md's
  "a check whose input you have not verified has not been run yet"). Discarded; the scratch completed and re-run.
- **Baseline** (predicted 35, 0 fail): 35 pass, 0 fail.
- **Revert A**, the snow filter deleted from `reference_fs_band` in the on-disk `src/street-style-v3.json` only, the
  bundle not rebuilt (predicted exactly 2 fail: the snow test with `actual undefined`, and the route test, since the
  bundle's embedded v3 no longer equals the disk copy): 33 pass, 2 fail, those two, the snow test's actual `undefined`
  vs the expected array. Restored; sha `7ad55d46…` = committed.
- **Revert B**, one byte in `src/street-style-v2.json` (the name gains a trailing space), the freeze guard that CI has
  never shown red (predicted exactly 3 fail: "v2 is frozen" on the `worker` sha, "/style/street-v2.json is Trail Map's
  v2 style", and the v3 route test, which also compares served v2 to disk): 32 pass, 3 fail, those three; the freeze
  test's diff shows `worker: 9cad3e03…` against `7547507b…`. Restored; sha `7547507b…` = committed.
- **After restore** (predicted 35, 0 fail): 35 pass, 0 fail.

## 9. The fixture is synthetic (computed, by decoding the archive with a small PMTiles v3 reader written here)

`test/fixtures/reference-trails.pmtiles`, 368 bytes: PMTiles v3, tile type MVT, gzip, zooms 10-15, header bounds
[-123, 42, -120, 46], two entries, no leaf directories. Metadata: `vector_layers` `fs_trails` (fields `trail_no`,
`trail_type`, both String) and `blm_trails` (no fields). Tile bodies: `b"reference 10/163/360"` and
`b"reference 15/5303/11553"`, plain marker text, no geometry and no attributes, so nothing real is in it; the script
`make-reference-trails-pmtiles.py` matches what the file contains (read). Cross-checks against the other fixtures (also
decoded): `live-build.pmtiles` holds `15/5303/11553` with body `live 15/5303/11553` (so an overflow answer is
distinguishable, as the test comment says), `tiny.pmtiles` holds `14/2651/5776`, which the reference archive lacks (so the
204 test shows a reference request never falls through to `us`). One oddity: both reference tiles have north-west corners at about 47.04°N and 46.79°N
(computed from the tile indices), north of the header's declared 46°N; the Worker never checks bounds on a tile read, so
no test is affected, and the TileJSON test asserts the declared box, not the tiles. Worth a line in the script's
comment if it is ever touched; not worth a change now.

## 10. Beyond the dispatch's words (the coder's own list of six was not available to me)

Decisions I could see that the dispatch and Amendment 1 did not spell out, each reasonable, none hiding a behaviour
change: (1) the Forest Service number is a separate symbol layer placed directly **above** the highest trail layer, below
every road layer (the dispatch said only "label from 14"); (2) the label's look: Noto Sans Regular 10, white halo 1.5,
line placement; (3) no BLM label ("BLM's numbering field is not settled", `forager-reference.mjs:40`); (4) `coalesce`
in the snow filter so a missing `trail_type` keeps the trail; (5) the v2 freeze held by sha256 in the Worker tests
(which CI runs) rather than by extending `check-street-night.mjs` (which CI does not run), while `check:street` stays on
v2; (6) `withReferenceLayers` throws when no `roads_path_*` layer exists rather than placing the band elsewhere
(`:116`), the right failure mode; (7) the fixture's z15 tile deliberately shares an index with `live-build.pmtiles`;
(8) the override's header comment carries the MapLibre line citations, which I confirmed (§6). The dispatch's original
8a items that Amendment 1 withdrew (StreetStyle's URL, the older-style notice test, the estimate counting the second
source) are correctly absent (read: no `app/` change).

## 11. File sizes (computed, `wc -l` / `wc -c`)

| File | pre-main | PR |
|---|---|---|
| `map-style/overrides/forager-reference.mjs` (new) | — | 117 lines |
| `map-style/generate-street.mjs` | 81 | 90 |
| `server/pmtiles-worker/src/index.ts` | 612 | 622 |
| `server/pmtiles-worker/test/worker.test.mjs` | 388 | 543 |
| `server/pmtiles-worker/test/fixtures/make-reference-trails-pmtiles.py` (new) | — | 49 |
| `…/fixtures/reference-trails.pmtiles` (new) | — | 368 bytes |
| three `street-v3` JSONs (generated, exempt) | — | 336,390 / 336,344 / 336,390 bytes, 15,469 lines each |

Nothing crosses 800 lines; the new source file is under 400 (modular-code skill). The repository gains about 1.0 MB of
generated JSON; the v2 copies stay, frozen.

## 12. Disclosure

**Confirmed (read or computed today):** everything in §§1-9 and §11 marked so, at `6cfed1aba`, `c91f34c49`,
`origin/records-after-173`, `origin/later-steps-reports`, and MapLibre Native at tag `android-v13.5.0`.

**Inferred:** that Cloudflare's edge stores the 404 (from the documented refusal list, which omits 4xx); that the style
never reaches "loaded" with a failing TileJSON (from `tile_source.cpp:61-62`); the value `SNOW` as the Forest Service's
snow code.

**Could not determine:** the coder's six beyond-scope calls (no list reached me); whether the online map requests
z10-11 reference tiles; whether anything in the app keys on style layer indices; Workers' current script-size limit
against the 1052 KiB bundle (the dry run completed, which is the only check that ran).

**Premises that were wrong or moved:** none found in the dispatch or Amendment 1 against the tree at `c91f34c49`. The
report §8's "visibility none" for 8a was superseded by the dispatch's "follow -1633's On", which the PR follows.

**Decided beyond scope:** nothing. No code, test, branch or PR was touched; the scratch copies live under
`/mnt/work/scratch/sorrel-416/` and the review worktree holds only this file. `docs/audits/README.md` not touched (the
planner's serialization point).
