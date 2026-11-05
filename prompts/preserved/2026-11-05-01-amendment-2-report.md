# Dispatch 2026-11-05-01, Amendment 2: report (Hazel Cairnwright)

PR #491 now checks a stored relief image before decoding it. CI is green on the last commit and the PR is marked ready again. It is **not merged**: the merge waits for the owner, after your S22 check.

The full report is the new "Amendment 2" section on the branch, in `docs/audits/2026-11-05-01-offline-store-read-completion-report.md`. The rest of that report is unchanged.

There was one stop, on a wrong premise in B4. I reported it by message, you answered, and the work went on with your two additions.

## Base
- **At the start:** offline-store-read was at e29b28d59 and pre-main at 368f710fb, both unmoved. records-after-173 was at 087fd55b7, and later at d6e0d57e6 with your in-place correction to -2124.
- **The worktree** `~/.cache/forager-wt/offline-store-read` was clean and level with origin.
- pre-main was still at 368f710fb at the end.

## What was built (A10)
- **`map/PngIntegrity.kt`** (new, 75 lines, one job). `pngIntegrityProblem(png)` checks, in order:
  - every chunk's CRC-32, over its type and data;
  - then the image data's zlib stream: all IDAT data in order, inflated to its end, where zlib checks the Adler-32.
  - The file's doc records why the decoder isn't trusted alone.
- **`OfflineReliefTileStore.kt:75`.** One line, after the inflate that already runs for a deflated row. An image that fails becomes `ReliefTileRead.Failed`: "the stored relief tile z/x/y fails its own checks: …".
- **What the bubble shows: today's no-height case, with no new words.**
  - `ReliefGroundHeightSource.kt:127-129` logs one line naming the tile ("Reading relief tile 11/1024/1024 failed: no ground height there.") and gives `Unknown(READ_FAILED)`.
  - `MapBubbleLayer.kt:251-252` passes that to `waypointElevationLine`, which gives `null` for any `Unknown` (`MapBubbles.kt:386-389`).
  - `MapFeatureBubble.kt:91` draws no line for `null`.
- **Nothing writes to MapLibre's store.**

## Commits
| Commit | What |
|---|---|
| 5912309fc | Tests first: `ReliefImageChecksTest` (5 tests) and the fixture `SyntheticReliefTiles` |
| 455b2372e | Your addition 1: the chunk-end splice test asserts that no `Known(-32768.0)` reaches the bubble |
| 7bb4df9bf | The check |
| 1cb09a1eb | The report's Amendment 2 section |

## The tests and the stop
**How they run.** Through the real read: `OfflineReliefTileStore` on a store file, inside `ReliefGroundHeightSource`, as T1 does it.

**The fixtures are synthetic but shaped like a real tile.** I copied the layout of the SDK-written relief tile already in the repo:
- 256x256 RGB, level-9 zlib, IDAT chunks of 65,536 bytes;
- the tile is 11/1024/1024, at sea off 0°, 0°.

**Tests-first run 42066897340**, from the JUnit XML:
- main job: 965 files, 6,644 tests, 4 failures, 0 errors, 24 skipped;
- Street job: 959 suites, 6,614 tests, the same 4 failures.
- All 4 failures are the new cases. Each matches the prediction I wrote before the push.

| Case | Before the check | What the test bites on |
|---|---|---|
| Guard: an intact image | passes, pixel for pixel, `Known(1234.5)`, nothing logged | not shown to bite |
| One byte changed in IDAT, stored raw | no height already: the decoder's "does not decode as an image" | the logged reason only |
| The same, stored deflated | the same | the logged reason only |
| Splice at a 4 KiB page inside a chunk | the same | the logged reason only |
| Splice where a chunk ends (every CRC holds, the zlib stream breaks) | **`Known(metres=-32768.0)`**, nothing logged | the height the user sees |

**The stop:** B4 said a one-byte change "today returns a height". On the JVM it doesn't, so I stopped and reported.
- **Why, read from source:** Skia's `SkPngCodec.cpp` and Android's `BitmapFactory.cpp`, both at main, not the Android 16 tags.
  - libpng's CRC error on IDAT is fatal, which gives `kErrorInInput`, and BitmapFactory returns null for that.
  - A zlib error with every CRC intact is only a warning. That gives `kIncompleteInput`, which BitmapFactory accepts, with the rows it never reached left at zero (-32768 m).
- **What the run shows:** the JVM's decoder lets a torn image through only when the tear lands exactly where a chunk ends.
- **The phone's own decoder was not read.** The check makes the outcome the same whatever decoder the phone has.

## Revert check r5
- **The revert:** the one line at `:75` deleted, on the throwaway branch `offline-store-read-r5` (commit c3c635d26, never to be merged). Run 42070485632.
- **Compile log, read first:** both test jobs checked out c3c635d26, there were no `e:` lines, and the tests ran.
- **Restore:** from a copy saved before the edit; `cmp` identical; the tree clean. The forward line is present on HEAD, on origin and in the tree.
- **Results:** 44 tests in 6 classes, **4 failed**, showing your split.
  - The chunk-end splice fails on the height: "Actual: Known(metres=-32768.0)", at addition 1's value assertion. That message text exists only from 455b2372e on, so it cannot be the tests-first run's output.
  - The other three fail on the reason (`:75`, "does not decode as an image").
  - Everything else passed: the guard and the other 5 relief classes, 39 tests.
- **The whole suite on the revert:** the Street job ran unfiltered (959 suites, 6,614 tests) and failed the same 4 and nothing else.

## CI counts at the last commits
| Run | Commit | Main job | Street job |
|---|---|---|---|
| 42070419421 | 7bb4df9bf, last code commit | 965 files, 6,644 tests, **0** failures, 0 errors, 24 skipped | 959 suites, 6,614 tests, **0** failures |
| 42074374922 | 1cb09a1eb, the report | the same | the same |

All 8 jobs passed in both runs. The intermittent crowded-icons test passed both times.

## A finding not asked for, which bears on your device check
**The S22's real relief rows all pass the check.**
- I queried a scratch copy of the end-s14 `mbgl-offline.db` only: `cmp` identical, sha256 prefix bc0976616ecf698a, read-only with `immutable=1`. The copy is now deleted.
- A Python mirror of the two checks finds 104 relief rows. The 44 that hold data all pass (20 raw, 24 deflated); the other 60 are empty.
- So the check would not refuse an intact real tile.

**But none of those 104 rows is linked to a region.**
- The copy has 2 regions and 1,664 region links, all to other tiles.
- The bubble reads only linked relief tiles. So at the end of session 14 it read none on the S22, and the new check could not run there.

**So the device check's height-line step needs a region that holds relief tiles.** The cheapest way would be to run it inside step 4's test area, before that area is deleted. That assumes a download today stores relief tiles, which is inferred from the spike's download, not re-read.

## Untested, said plainly
- **The phone's decoder.** Nothing has run on the phone.
- **Real torn reads.** The splices are cut at offsets I chose, not at SQLite's real page layout.
- **The three CRC cases on the JVM.** They change only the reason in the log, not what the user sees.
- **Bytes after IEND, or after the end of the zlib stream.** These are not refused, and not tested.

## Calls beyond the dispatch, for you to accept or reject
1. **Two extra cases:** the one-byte change stored deflated, and the chunk-end splice.
2. **The refusal tests check the reason in the log.**
3. **A file with no PNG signature counts as `Failed`.**
4. **Bytes after IEND, or after the zlib stream ends, are not refused.**
5. **No new log line in the store.** It uses the height source's existing line.
6. **A WIP branch** `offline-store-read-a2-wip` held the drafted check on origin while I waited at the stop. It ran no CI and was deleted once the PR branch carried its content.
7. **Sources read outside the repo:** Skia and AOSP source at main, on the web, and the end-s14 copy.

## Left
- **PR #491:** open, ready, mergeable, head 1cb09a1eb, base pre-main.
- **On origin:** `offline-store-read`, and `offline-store-read-r5`, which is never to be merged.
- **A PR comment** lists the tests-first reds.
- **The worktree** is clean and level with origin.
- **Scratch** `/mnt/work/scratch/offline-store-read-a2/` is deleted.
- **Not touched:** your worktree, the phone, RECORD.md, the audit index and MapLibre's store.
