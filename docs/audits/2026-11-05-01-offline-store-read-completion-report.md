# Dispatch 2026-11-05-01, completion report: the drop-off reads the map's offline store reliably while the map writes; reads never delete it; one opening per tap (Hazel Cairnwright, Phase 2)

Built from three documents on records-after-173 (read at 1ab868c6d), with RECORD -2116 to -2122:
- the dispatch, `prompts/preserved/2026-11-05-01-offline-store-read.md`;
- Amendment 1, `…-amendment-1.md` (A5 to A9, B1 to B3, C1 to C3);
- the Phase 1 report, `…-phase1-report.md`.

The go:
- the owner's go: "Yes" (-2120);
- the owner's design answer: "Retry, fail safe (Recommended)" (-2122);
- the planner's go for Phase 2: -2122 and C2.

## Summary

- **The read (A5, A7).** Every read-only open of MapLibre's store in the app now goes through one function. There are three such opens: `MapLibreOfflineBasemap`'s, and `OfflineReliefTileStore`'s two.
  - It retries a read that SQLite refuses while MapLibre is mid-save, within B1's limits: 5 attempts, with waits of 50, 100, 200 and 400 ms.
  - Only `SQLiteReadOnlyDatabaseException` is retried. Any other exception fails at once, as before.
  - One line is logged for each read that needed a retry. One is logged for each read still refused at the bound, which then fails, as before.
- **Never a delete (A6).** The same three opens use an error handler that logs a corruption report and leaves the file. Android's default handler deletes it.
- **One opening (A8).** `RecordsTab`'s selection effect reads its own key, not the live chip. That covers `onTracksOpened` too.
- **A9.** The signal-return trigger is unchanged.
- **Nothing writes to MapLibre's store.** Nothing opens it writable or changes its configuration. AppContainer.kt, JournalTab.kt, the Worker, Theme.kt, Color.kt, RECORD.md and the audit index are untouched.
- **Tests first.** There are 18 new tests in 6 new classes. 14 failed first, each for its predicted reason. The other 4 are 2 fixture controls and 2 guards, which pass by design.
- **Revert checks.** There were four one-line reverts. Each failed exactly the cases its edit could cause, and nothing else. Every compile log was clean.
- **CI.** On the last code commit, b1e8ebfe9, the main test job is green: 6,639 tests, 0 failures, 0 errors, 24 skipped. The job that runs without the Street map failed its first attempt on one unrelated, intermittent test, `AvailabilityScreenCrowdedIconsSettingTest`. Its re-run, unchanged, was green: 6,609 tests, 0 failures.
- **A5's torn-read question, plainly** (section 5):
  - For the drop-off's map tiles as the S22 holds them: practically no.
  - For a raw, uncompressed vector-tile row: yes, it can.
  - For the waypoint bubble's raw relief PNGs: I could not determine it. 20 of the 44 relief rows on the S22 are raw.

## Base checked

- **Branch heads.** `git fetch origin` at the start gave pre-main **368f710fb**, unmoved from the handoff, and records-after-173 **1ab868c6d**.
- **The branch.** `offline-store-read` was cut from origin/pre-main 368f710fb, in `~/.cache/forager-wt/offline-store-read`. Its upstream to pre-main was unset, so a bare push cannot reach pre-main.
- **Phase 1's premises, re-checked before building on them:**
  - **The opens of the store.** I grepped every `openDatabase`, `openOrCreateDatabase`, `OPEN_READ*`, `SQLiteOpenHelper` and `mbgl-offline` in `app/src/main`. The store is opened at three places only: `MapLibreOfflineBasemap.kt:56` and `OfflineReliefTileStore.kt:47` and `:73`, at base. `RoomJournalBackup.kt`'s five opens (`:111`, `:168`, `:185`, `:203`, `:210`) are of the journal's own snapshot and backup copies.
  - **The default handler (read in Robolectric's Android 16 framework jar with `javap`, as Phase 1 did):**
    - `openDatabase(String, CursorFactory, int, DatabaseErrorHandler)` exists;
    - the constructor installs `DefaultDatabaseErrorHandler` when none is given;
    - `SQLiteQuery.fillWindow` catches `SQLiteDatabaseCorruptException` and calls `onCorruption`;
    - `open()` logs "Database corruption detected in open()" and calls it;
    - the default handler logs "Corruption reported by sqlite on database: " and calls `deleteDatabaseFile`.
    - Then observed on CI: T2's three cases had their files deleted before the change.
  - **The doubled opening's cause.** `RecordsTab.kt:242-247` is as Phase 1 read it. The doubling was then observed on CI: 2 openings where 1 is right.
  - **Robolectric and 776** (Phase 1 could not determine this): Robolectric refuses with "attempt to write a readonly database (code 776 SQLITE_READONLY_ROLLBACK): , while compiling: SELECT data, compressed FROM resources …". That is at the first query, not the open, as Phase 1 predicted.

## Commits and PR

PR **#491**, `offline-store-read` into pre-main, draft until CI is green, then marked ready. It is not merged: the merge waits for the owner.

| Commit | What |
|---|---|
| bb41345b4 | The tests, over stubs: the two readers take a `log` and a `pause`, unused |
| 7fc9210f9 | The store read: `MapLibreStoreRead.kt` (the open with the handler, and the retry), `MapLibreStoreReadLimits.kt`, and both readers through them |
| b1e8ebfe9 | `RecordsTab`: the selection effect reads its own key |
| (this commit) | This report |

**Files** (line counts by `wc -l`, then and now):

| File | Lines | Change |
|---|---|---|
| `map/MapLibreStoreRead.kt` | new, 77 | `openMapLibreStoreReadOnly` (`:37`), its handler (`:40`), `retryingRefusals` (`:61`) |
| `map/MapLibreStoreReadLimits.kt` | new, 25 | B1's limits with their reasons, as `DropOffMapLimits.kt` does |
| `map/MapLibreOfflineBasemap.kt` | 69 → 85 | its open through both (`:67`); `log` and `pause` take defaults |
| `map/OfflineReliefTileStore.kt` | 161 → 183 | both reads through one private `read` (`:99`); the row reading moved into `tileIn` and `fingerprintIn`, unchanged in what they do |
| `ui/log/RecordsTab.kt` | 476 → 481 | `val openedTab = selectedTab`; the effect is keyed on it and reads it (`:246-248`) |
| 6 test classes and 1 fixture file | new, 806 in all | below |

No file passes 800 lines. Nothing in composition order, Back order, effect keys or launch order changes.

## Failing-first evidence

The tests-first CI run was 42042157685, at bb41345b4, read from the JUnit XML in `unit-test-report`.
- **Main job:** 964 files, 6,639 tests, 15 failures, 0 errors, 24 skipped.
- **Street-deleted job:** 6,609 tests, 14 failures.
- **The 15th failure** in the main job is not this branch's (see "Not this branch's").

**T1, a read refused mid-save.** The fixture is `MapLibreStoreFixtures.hotJournalCopy`.
- The test's own writer runs at `synchronous = OFF` and holds a transaction open with an `accessed` write. Then the database and its journal are copied to a folder where nothing holds a lock.
- The fixture checks itself before handing the copy back: the journal is over 512 bytes and starts with the header `d9 d5 05 f9 20 a1 63 d7`.
- The test's pause removes the journal when a refusal is to clear, as MapLibre's commit does when it ends.

| Case | Failed first with |
|---|---|
| A basemap read, tried again | expected the tile, got Failure(SQLiteReadOnlyDatabaseException … code 776 SQLITE_READONLY_ROLLBACK …) |
| A basemap read at the bound | expected:<[50, 100, 200, 400]> but was:<[]> |
| A relief read, tried again | expected the tile, got Failed(SQLiteReadOnlyDatabaseException … 776) |
| A relief read at the bound | expected:<[50, 100, 200, 400]> but was:<[]> |
| The relief fingerprint, tried again | expected:<16::140876:1> but was:<null> |
| Through the bubble's height source (`ReliefGroundHeightSource`) | expected a height, got Unknown(reason=READ_FAILED) |
| The way-to (`FindWayToWaypointUseCase`, real BRouter, the reader composed as AppContainer composes it) | "… keeps the engine's end by trail …: the waypoint's end: the map could not be read: … 776 SQLITE_READONLY_ROLLBACK" |
| The copy pass (`DropOffMapFetch.pass`) | log: [Drop-off map for region 3 not fetched: MapLibre's store could not be read.] |

**T2, a store SQLite reports corrupt.** The fixture is `MapLibreStoreFixtures.corruptRootPages`: every table and index root page is overwritten with 0xFF, and `sqlite_master` is left whole. So a statement prepares and fails at its first step, in `fillWindow`.

| Case | Failed first with |
|---|---|
| The basemap read | the store is still there after a corruption report on a read (it had been deleted) |
| The relief tile read | the same |
| The relief fingerprint read | the same |

**T3, one opening.**

| Case | Failed first with |
|---|---|
| `OfflineMapsOpenedOnceTest`: real touches at five points across the Tools drawer's Offline maps row, through the real screen and ViewModel, with Back between touches | openings … for one touch at (0.5, 0.5) of the row expected:<1> but was:<2> |
| `RecordsTabOpensOnceTest`: composed with the Offline maps latch set | expected:<1> but was:<2> |
| `RecordsTabOpensOnceTest`: composed with the Tracks latch set | expected:<1> but was:<2> |

**Passing by design, before and after:**
- **Two fixture controls.** A plain read-only open of the copy taken mid-save is refused with 776. The store with its root pages overwritten is reported to the error handler.
- **Two guards in `RecordsTabOpensOnceTest`.** Already on Offline maps with no latch, it opens once. With its own latch, it opens once.
- These are not shown to bite and are listed as such.

**Not shown to bite (B3), existing guards, unchanged:**
- **The chip touches:** `RecordsFilterChipsTest.kt:282-287`.
- **The signal return:** `OfflineRegionRouteRowTest.kt:144-178`. It is told once per return while composed, never at first composition, and is told on the first return when opened with no signal.
- These two already cover the list-level test Phase 1 proposed (section 7), so none was added.

## Revert checks

The runner rules from CLAUDE.md were followed:
- one line per revert;
- the compile log read before the results;
- the file restored from a copy saved before the edit;
- the forward change confirmed afterwards.

How each revert was made:
- The edit was made in place, in the worktree.
- The revert commit was built from the edited file through a temporary index, so HEAD never moved. It was pushed as a throwaway branch `offline-store-read-rN`.
- The file was restored from the saved copy, and the runner checked three things: `cmp` against the copy, a clean tree, and the forward line present.
- Each branch then ran on CI with `gh workflow run ci.yml --ref offline-store-read-rN -f tests=…`.
- At the end, HEAD and origin/offline-store-read were both b1e8ebfe9. The tree was clean, with all three forward lines present.

| Revert | The one line | Run, head | Compile | Result: the cases that failed |
|---|---|---|---|---|
| r1, the retry removed | `MapLibreStoreRead.kt:69` `if (attempt == MapLibreStoreReadLimits.ATTEMPTS) {` → `if (true) {` | 42045574650, 848d98b2d | no `e:` lines; tests ran | 32 tests, **8 failed**: all six T1 cases in `MapLibreStoreRefusalTest` but the control, plus the way-to and the copy pass. T2 and the existing reader tests passed. |
| r2, the effect reads live state | `RecordsTab.kt:248` `when (openedTab) {` → `when (selectedTab) {` | 42045610922, 02826f856 | clean | 26 tests, **3 failed**: the three T3 cases, each 2 where 1 is right. The guards and `RecordsFilterChipsTest` passed. |
| r3, the default handler back on the basemap | `MapLibreOfflineBasemap.kt:67` `openMapLibreStoreReadOnly(database, log)` → `SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)` | 42045627305, 43c01e1b2 | clean | 17 tests, **1 failed**: T2's basemap case. Its file was deleted. |
| r4, the default handler back on the relief | the same edit at `OfflineReliefTileStore.kt:99` | 42045641635, dfaf7d123 | clean | 24 tests, **2 failed**: T2's two relief cases, and only those, as B2 asks. |

**Why each failure belongs to its own edit:**
- **r1.** Its failures carry r1's own line, "… was refused while MapLibre was saving on all 5 attempts, over 750 ms, and fails.", logged after a single attempt with no wait. Only the bound branch running on attempt 1 produces that. The bound cases fail on `[]` waits, and T2 is untouched.
- **r2.** Only effect-count cases fail.
- **r3 and r4.** Each fails only the T2 cases of its own reader, on the file's deletion. The retry cases pass in both.
- CI runners start empty, so no result could be left over from another run. Each log shows the checkout of its own revert ref.

## CI counts

Read from the JUnit XML in each run's `unit-test-report`, not from the job's status.

| Run | Commit | Job | Files | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|---|---|
| 42042157685 | bb41345b4 (tests first) | main | 964 | 6,639 | 15 | 0 | 24 |
| 42042157685 | bb41345b4 | Street deleted (its own summary) | 958 | 6,609 | 14 | 0 | 24 |
| 42045409580 | b1e8ebfe9 (last code commit) | main | 964 (958 + 6) | 6,639 (6,615 + 24) | **0** | 0 | 24 |
| 42045409580, attempt 1 | b1e8ebfe9 | Street deleted | 958 | 6,609 | 1 (not this branch's) | 0 | 24 |
| 42045409580, attempt 2 (the failed job re-run, unchanged) | b1e8ebfe9 | Street deleted | 958 | 6,609 | **0** | 0 | 24 |

- **The counts against the source.** The 6 new classes add 6 result files to the main job: 964 against pre-main's 958. They add 18 tests: 7 + 4 + 1 + 1 + 1 + 4. All 18 pass at b1e8ebfe9.
- **The other jobs at b1e8ebfe9 passed:** build, lint, release compile, the style checkers, the Worker tests, the route cutter, and no debug sources.

**Not this branch's: `AvailabilityScreenCrowdedIconsSettingTest`, "real touches across each row pick it, and the pick survives a restart".**
- **How it failed:** ComparisonFailure, "the file holds it under map.crowded_icons expected:<[list]> but was:<[zoom_then_fan]>".
- **Where:**
  - at bb41345b4, it failed in the main job and passed in the Street-deleted job;
  - at b1e8ebfe9, the reverse.
  - The same commit gave both outcomes, so it is intermittent.
- **Why [inferred, not traced]:** it touches List, Zoom then fan, List, then waits until the repository reads List, then reads the raw file. A file still holding the middle pick fits two writes landing out of order.
- **What I did:** this branch touches no settings code. Reported, not touched; the failed job was re-run once, unchanged.
- **Not known:** whether its rate on other branches is the same. The recent full runs on other branches that I listed passed.

## What is untested, said plainly

- **On the phone, nothing yet.** Device-only:
  - the two copies of SQLite not seeing each other's locks;
  - MapLibre's real commit timing, and whether 750 ms clears the refusals the S22 meets;
  - the vendor open step (`PRAGMA cache_size`);
  - whether the phone's framework honours the handler given to the four-argument `openDatabase`. It is public API; the vendor's build was not read.
- **On the JVM, the refusal comes at the first query.** On the phone it came inside the open. The retry wraps both (call C1), but only the JVM case is tested.
- **The corruption handler on the phone.** It would take a corrupt store, which is never made on the phone.
- **Torn reads.** Not testable here. Section 5 is reading, one observation on a copy, and inference.
- **`onTracksOpened`** is tested at `RecordsTab` with the latch set, not through a real GPX import.
- **The signal-return trigger** on the phone (unchanged; device step 5, which has a precondition).
- **The four tests that pass by design,** and the two existing guards: not shown to bite.

## 5. A5: what a torn read of a tile can yield in this code

**The exposure** (the Phase 1 report, 1.5) is unchanged by the retry. MapLibre can commit while the app's read is in progress, because neither copy of SQLite sees the other's lock. A blob that spans overflow pages can then come back with some pages from before the commit and some from after.

**The drop-off's map tiles (`us`, read by the way-to and the copy pass):**
- **Compressed rows: practically never wrong-but-valid.**
  - [read] `StoredTiles.bytesOf` inflates a row whose flag is set (`StoredTiles.kt:28-50`). The loop runs to `finished()`, where zlib checks the Adler-32 of the whole inflated tile. A failure is an `IOException`, so the way-to's end goes by trail, logged, and the pass stops, logged.
  - [observed, end-s14 copy] 972 of 973 `us` rows are compressed.
  - [observed, the laptop's zlib, which Java's Inflater also wraps] I spliced a 4,092-byte span, one overflow page's payload at page size 4,096, from one stored tile into another at a random offset. That was 3,897 torn copies of the 196 compressed rows over 8 KiB. zlib accepted 0.
  - What is left is a checksum collision.
  - **The planner's premise said "gzip's check".** The flag is zlib-wrapped Deflate (`StoredTiles.kt:25-26`), checked by Adler-32. Gzip applies only to a body still gzip-wrapped (`:30`), checked by CRC-32 and length in `GZIPInputStream`. The end-s14 copy has 0 such rows.
- **Raw (uncompressed) rows: yes, it can.**
  - [read] `MvtDecoder` checks structure: wire types, field lengths, command ids (`:102`), and tag ranges. It does not check coordinates. MoveTo and LineTo parameters are added as they come (`:95-96`), with no bound against the extent.
  - [inferred, not exercised] So a foreign span that lands inside a geometry's parameters and keeps the varint boundaries decodes as wrong coordinates, with no error.
  - [observed] The end-s14 copy has one such row: 73 bytes, inside one page. A tear across a commit's page writes cannot split a one-page row. Whether a single 4 KiB page read can tear against MapLibre's write on the phone's file system I could not determine.
- **Rows read across a commit:**
  - [read] The schema as `MapLibreOfflineBasemapTest` copies it gives `tiles.id` AUTOINCREMENT. I did not re-read MapLibre's source this session.
  - [inferred] So a row id is never reused for another tile. A read whose index lookup comes before a commit and whose row read comes after yields one of four things: the old row, the new row, no row ("not stored"), or an SQLite error. It never yields another tile's data.

**The waypoint bubble's relief PNGs (`OfflineReliefTileStore`):**
- [read] Compressed rows go through the store's own `inflate`, which runs to `finished()`, so they have zlib's check: a torn one fails as `Failed`.
- [observed, end-s14 copy] 20 of the 44 relief rows are raw PNGs of 139 to 161 KB, about 35 to 40 pages each. That is many page boundaries for a commit to fall between.
- [could not determine] A raw PNG's protection is the platform's image decoder: PNG's per-chunk CRC-32, and the image data's own zlib check. Whether the phone's decoder rejects a damaged PNG, or returns a partly decoded image, was not read and is not readable from the permitted sources.
- If it returns one, the bubble could show a wrong height. **This is the one place where the inference in the owner's option ("would very likely produce data the app can't make sense of") is not established.**
- I built nothing for it: the owner chose "Retry, fail safe", and nothing here was asked.

## Calls beyond the dispatch, for the planner to accept or reject

1. **The whole read is retried, the open and its statements together.** B1 says "on `SQLiteReadOnlyDatabaseException` from a read-only open of the store". I read "open" as the read-only connection: opened, then read.
   - On the JVM the refusal comes at the first query (observed).
   - SQLite checks for a hot journal at every statement that starts a read (its locking document, 4.1).
   - Retrying only the `openDatabase` call would leave the phone's later statements bare, and the JVM tests could not pass.
   - The statements only read, so running them again changes nothing.
2. **The log sink and the pause come through constructor defaults.** They are `Log.w` under each reader's own tag, and `Thread.sleep`. That leaves `AppContainer.kt`, which is not in the file list, untouched.
3. **The waits block the IO thread** (`Thread.sleep`): at most 750 ms per read, not cancellable mid-wait.
   - `MapLibreOfflineBasemap` holds its lock through them, so the way-to's and the copy pass's reads queue behind a retrying read. They already take turns behind that lock.
   - The bound is per read, so a store that never clears costs Navigate about 1.5 s: two ends, read in turn (`WayToEnds.kt`). That is noted beside the limits.
4. **The log wording.** These are log lines, not words a person sees:
   - "A basemap read of MapLibre's store was refused while MapLibre was saving, and read on attempt N of 5."
   - "… was refused while MapLibre was saving on all 5 attempts, over 750 ms, and fails."
   - "SQLite reported MapLibre's store (mbgl-offline.db) corrupt on a read-only open. The file is left as it is, and the read fails."
   - The relief's lines say "A relief read of MapLibre's store".
5. **The fixtures.** The hot-journal write is a resource's or a tile's `accessed` update, as MapLibre's own reads write. The corrupt store overwrites root pages only.
6. **Two fixture controls** that pass before and after by design.
7. **No new list-level signal-return test.** It exists (above).
8. **Evidence read beyond the code:**
   - Robolectric's Android 16 framework jar, with `javap`.
   - The end-s14 `mbgl-offline.db`. It was copied to scratch: `cmp` identical, sha256 prefix bc0976616ecf698a. The copy alone was queried, read-only with `immutable=1`, with the laptop's Python. The splice experiment ran on it.
   - The phone and the live store were never touched.
9. **The revert runner** built each revert commit through a temporary index (above).
10. **A PR comment** lists the reds: #491.
11. **One re-run** of the failed Street-deleted job at b1e8ebfe9 (above).

## Premises that were wrong

1. **A5's "compressed rows (gzip's check)":** it is zlib's Adler-32 (section 5).
2. **Phase 1's proposed list-level signal-return test** already exists.

## Could not determine

- The phone's framework: its handler, its PNG decoder, and its open sequence.
- Whether a single page read can tear against MapLibre's write on the phone.
- MapLibre's commit timing on the S22.
- The crowded-icons test's cause, and its rate elsewhere.

## Stops

None hit.
- Nothing writes to MapLibre's store or changes its configuration.
- No server change. No new words a person sees.
- No owner-level choice was taken. The relief finding in section 5 is reported, not built on.
- No test weakened. No symptom took two attempts.

## Device check for the planner (S22)

Cheapest first. Each step has its pass condition and its evidence. Observations are marked as not gates.

**Before.** Install the APK from PR #491's last CI run (`app-debug-apk`) over 4616, as session 14 did. Check its version and signer. Capture logcat from before step 1.

**Lines to grep** (the app's pid):
- **Openings:** "Offline maps opened with no signal: no drop-off map pass this time."
- **A refusal recovered (observation):** "was refused while MapLibre was saving, and read on attempt" (tags `MapLibreOfflineBasemap`, `OfflineReliefTileStore`).
- **At the bound (gate):** "was refused while MapLibre was saving on all 5 attempts".
- **The way-to's read failing (gate):** "the map could not be read" (tag `FindWayToWaypoint`).
- **The copy pass's read failing (gate):** "MapLibre's store could not be read" (tag `DropOffMap`).
- **Expected now, and not failures by themselves:** every refused attempt still shows as SQLite's "(776)" line and Android's "Failed to open database '…/mbgl-offline.db'". The phone refuses inside the open. Count them as refusals seen.
- **Gate:** no "Corruption reported by sqlite", "deleting the database file" or "corrupt on a read-only open".

1. **One opening per tap, no signal** (A3, A8).
   - **Do:** turn airplane mode on. On the Maps tab, with the Journal on Entries, go Tools > Offline Maps. Then Back (Tools reopens) and Offline Maps again, and Back and Offline Maps a third time.
   - **Pass:** exactly 3 opening lines, one after each tap and before the next.
   - **Fail:** two lines for one tap, or none.
   - **Evidence:** `grep -n` with timestamps, beside the tap times.
2. **Navigate, repeated while the map draws online** (A5, the way-to's read).
   - **Do:** turn signal on, and wait for "Validated network (30 s, or at start)…". With Waypoint 1 on a good fix, as in session 14's step 7, start each run where a map view starts at the moment of the read: Journal > Records > Waypoints, then Navigate on Waypoint 1. That brings the Maps tab's map back and loads its style, and session 14's refusal came 1.1 s after that. Let the card settle, stop navigation, and go back to the Journal. **Do this 10 times.**
   - **Pass:** 0 "the map could not be read" lines, and 0 "on all 5 attempts" lines, over the whole step.
   - **Evidence:** the grep counts, and each run's card.
   - **Observation, not a gate:** the count of "read on attempt N of 5" lines and of 776 lines. If both are 0, the step passed without exercising the retry. Say so: it is then no evidence for the retry on the phone.
3. **The bubble's height while the map loads** (A7). This is an observation, and only where Waypoint 1's bubble showed an Elevation line before.
   - **Do:** after a stop in step 2, open Waypoint 1's bubble on the Maps tab as the map loads.
   - **Pass:** the Elevation line shows; no "A relief read … on all 5 attempts" line; no "Reading relief tile … failed" line.
   - **Evidence:** a shot and the grep.
4. **The copy pass's read at a download's end** (A5, the copy pass).
   - **Do:** with signal, download a small test area, as session 14's step 8 did.
   - **Pass:** at its end, "Drop-off map for region N: whole. …" or a line with its counts, and no "MapLibre's store could not be read".
   - **Then:** delete the test area as step 8 did: its folder gone, no "Couldn't delete" line, and the budget back.
   - **Evidence:** the grep, and the folder listing.
5. **The signal-return pass, as ruled** (A4, A9).
   - **Precondition:** an area whose drop-off copy is not whole.
     - Session 14 left both of the S22's areas whole (RECORD -2116, step 3).
     - A whole area is not selected (`AvailabilityOfflineRoutes.kt:71-72`), so on the S22 as it stands the trigger runs no pass and logs nothing. The step could then neither pass nor fail.
     - Getting such an area changes the phone's data. That is the planner's call, and the owner's yes.
     - If none is available, record this step as not run, not as passed.
   - **Do:** with Offline maps open in airplane mode, turn airplane mode off. Keep Offline maps on screen **at least 35 s after Android's `+VALIDATED`**: that is, past the app's "Validated network (30 s, or at start)…" line, and about 10 s more.
   - **Pass:** one drop-off pass line for that area after the Validated line, while Offline maps is still on screen, and no second one.
   - **Evidence:** the grep, with timestamps beside `+VALIDATED` and the Validated line.
