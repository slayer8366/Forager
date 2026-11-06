# F5 redone on Sonnet 5.5: backups without recent searches, GPX cache cleaned (dispatch 2026-09-28-216)

**Coder session, model `claude-sonnet-5-5`** (the session was configured with it; the serving model was not independently confirmed).

## Why this exists

F5 already landed on `journal-redesign` (`981bff8d`, report `770fcf6f`, planner suite 3223/0/0/24). It was built on Opus 5.5. The owner asked
for it to be redone on Sonnet 5.5, on a new branch, so the attribution is true. The owner's words, as relayed in this session: "F5 is stated
to be done by Sonnet 5.5 but Opus 5.5 was used on last run. So we have to redo it on Sonnet 5.5 to make it true. Start a new branch".

The planner's message in the same session, sent before it knew of the owner's instruction: "F5 is done ... cancel this dispatch as done. Build
nothing and push nothing." The owner's instruction was followed; the planner was told.

- **Dispatch (governs):** `prompts/preserved/2026-09-29-48.md` at `445b292b`, "2 A / 3 A".
- **Base:** `445b292b`, which has no F5 (verified: `git grep cached_searches` in `data/backup/` finds only `JournalTables.kt:96` and the no-op at `RoomJournalBackup.kt:162`).
- **Branch / worktree:** `privacy-fixes-redo-sonnet`, `/home/zynergy-labs/Zynergy/forager-wt/privacy-fixes-redo-sonnet`. The dispatch names `privacy-fixes`, which already exists with the landed F5.
- **Push target:** `origin/privacy-fixes-redo-sonnet`, not `journal-redesign` (the dispatch says journal-redesign; the owner said new branch, and pushing a second F5 onto the branch that has one would conflict).

## Pre-registration (written and pushed before any test or fix)

Reading, at base `445b292b`:
- `RoomJournalBackup.kt:104-145` `doBackUp` takes the snapshot (`:108`), reads it read-only (`:109`), zips it (`:140`). `:149-174` `takeSnapshot`: copy under the write lock, then fold the copy (`:168-171`). `:162` is `DELETE FROM cached_searches WHERE 0`, a no-op that only takes the write lock.
- Restore (`:178-200`) reads only `JournalTables.journal` specs (`:239`, `:250`, `:321`, `:346`, `:362`); `cached_searches` is in `JournalTables.excluded` (`JournalTables.kt:95-97`), so no restore path writes it.
- `TrackGpxExporter.kt:46-60` `write`; `:78` `forContext` puts files in `cacheDir/tracks`; nothing deletes them. The only caller is `TrackExportPanel.kt:264`, which then calls `startActivity(Intent.createChooser(...))` at `:266`, with no result callback, so the code shows no completion signal sharper than the planner's hour. The dispatch's "stop if a sharper safe signal" does not trigger.
- `ForagerApplication.kt:50-56` `onCreate`; the capture sweep at `:93-` is the pattern for a background start task.

Predictions (each test, at base):

| # | Test | Predicted at base | Reason |
|---|------|-------------------|--------|
| 1 | backup with searches: the snapshot in the zip has 0 `cached_searches` rows, and the phone keeps its 2 | **FAIL** | snapshot holds 2 rows; the message names 2 vs 0 |
| 2 | backup with searches: no trace of a search's text is left in the snapshot bytes | **FAIL** | text is in the snapshot file. Also fails with DELETE alone (free pages keep the text), so it is what makes VACUUM necessary |
| 3 | Replace of a backup that has searches (schema 16 and 17) leaves the phone's own searches equal | **PASS** | restore never reads `cached_searches` (`:239-362`). A guard: passing at base is predicted, not a surprise |
| 4 | Merge of the same, likewise | **PASS** | same |
| 5 | `write` removes an export older than an hour and keeps one 59 minutes old | **FAIL** | the stale file is still there |
| 6 | `write` leaves an old non-`.gpx` file alone | **PASS** | nothing deletes anything at base; guards the scope of the deletion |
| 7 | app start removes a stale export and keeps a fresh one | **FAIL** | the stale file is still there after 5 s |

Pass conditions after the fix: 1, 2, 5, 7 pass; 3, 4, 6 stay passing; the full suite has 0 failures, counted from the JUnit XML in a cleared results directory.

Revert checks planned, each from a copy saved before editing:
- remove the `DELETE` in the snapshot: tests 1 and 2 fail;
- remove only the `VACUUM`: test 2 fails, test 1 passes;
- make Replace/Merge copy `cached_searches` in: tests 3 / 4 fail (the guard bites);
- remove the deletion call from `write`: test 5 fails; remove it from `onCreate`: test 7 fails.

Not testable here: the real share sheet and a receiving app still reading the file (device only).

## Tests first, at base `445b292b` (before any fix)

Run: `./gradlew --offline :app:testDebugUnitTest --tests '*JournalBackupTest' --tests '*TrackGpxExporterTest' --tests '*ForagerApplicationGpxStartTest'`, results directory cleared first, every XML newer than the run's start. 51 tests, 4 failed, 0 errors; build log has no compile errors. Exactly the four predicted, each for the stated reason:

| # | Test | Result | Message |
|---|------|--------|---------|
| 1 | backup made with searches holds no rows | FAIL | `cached_searches rows in the backup's snapshot expected:<0> but was:<2>` |
| 2 | no trace of search text in the snapshot | FAIL | `search text found in the snapshot file` |
| 3 | Replace leaves the phone's searches | PASS (predicted) | |
| 4 | Merge leaves the phone's searches | PASS (predicted) | |
| 5 | `write` removes an export older than an hour | FAIL | `an export 61 minutes old is still in the cache` |
| 6 | `write` leaves a non-gpx file alone | PASS (predicted) | |
| 7 | app start removes a stale export | FAIL | `an export 61 minutes old is still in the cache after startup` |

## What landed (on `privacy-fixes-redo-sonnet`)

- `RoomJournalBackup.kt`: new `clearSearchesFromSnapshot`, called in `doBackUp` right after `takeSnapshot`; opens the snapshot **copy** read-write and runs `DELETE FROM cached_searches`. The live database is never written. The no-op `DELETE ... WHERE 0` in `takeSnapshot` is unchanged (it still takes the write lock). Restore is unchanged: it never touched `cached_searches`, and tests 3 and 4 now hold that.
- `TrackGpxExporter.kt`: `deleteStaleExports()` returns a `StaleSweep(found, deleted)` for `.gpx` files in the export directory last modified over `MAX_EXPORT_AGE_MILLIS` (one hour) ago; `write` calls it first.
- `ForagerApplication.kt`: `onCreate` calls `deleteStaleGpxExports()`, on `applicationScope` like the capture sweep, logging a count at INFO and a warning if a file could not be deleted.
- Tests: 4 in `JournalBackupTest`, 2 in `TrackGpxExporterTest`, new `ForagerApplicationGpxStartTest` (1).

Commits: `9b54e5a2` (fix), `29d009ae` (VACUUM dropped); pre-registration `76b71b41`, tests first `eea15726`.

## Verification

- **Tests forward:** the three classes, 51 tests, 0 failures (before the VACUUM change), then the same after.
- **Revert checks** (each from a copy saved before editing, restored from that copy, build log with 0 compile errors, forward change confirmed present afterwards by `cmp` and `git diff`):

| Check | Edit | Result |
|-------|------|--------|
| R1 (twice, once on the final code) | remove the `DELETE` | tests 1 and 2 fail: `expected:<0> but was:<2>`; `search text found in the snapshot file` |
| R2 | remove the `VACUUM` (since dropped) | **nothing failed. Prediction wrong**, see Findings |
| R3a | Replace also copies `cached_searches` | test 3 fails, `schema 16 backup, Replace: the phone's searches expected:...` |
| R3b | Merge also copies `cached_searches` | test 4 fails, `schema 16 backup, Merge: ...` |
| R4 | `write` no longer sweeps | test 5 fails: `an export 61 minutes old is still in the cache` |
| R5 | `onCreate` no longer sweeps | test 7 fails: `... still in the cache after startup` |

- **Full suite**, results directory cleared, every XML newer than the run start: 379 files, **3145 tests, 0 failures, 0 errors, 24 skipped** (second run). The first run had **1 failure**, `LeavingTheJournalFixesTest` "F3 the Maps search bar shows on Maps while a find is kept open on the Journal", `IllegalArgumentException: performMeasureAndLayout called during measure layout`. Unrelated to this change (Compose layout, none of these files); the class then passed 34/34 three times alone, and the whole suite passed on rerun. Not touched. It reads as a flake seen once in three runs of that class's code, with no cause established.

## Findings

1. **A prediction failed (R2).** I predicted that `DELETE` alone would leave the deleted text in the file and that test 2 would fail without a `VACUUM`. It passed. A probe (a throwaway test, not committed) showed `PRAGMA secure_delete` reads 1 by default under Robolectric's SQLite 3.44.3, and that the text was also absent with `secure_delete=OFF` on a whole-table `DELETE`. So test 2 cannot tell `DELETE` from `DELETE` + `VACUUM`. I dropped the `VACUUM` (see Decisions). Test 2 still bites the `DELETE` (R1).
2. **The base had no F5** and the branch the dispatch names already holds the landed F5 (from the Opus run); this is a redo on a new branch. See the top of this report.
3. **The code shows no share-completion signal** (`TrackExportPanel.kt:266`, `startActivity(Intent.createChooser(...))`, no result). The dispatch's "stop if sharper" did not trigger; the one-hour rule is what was built.

## Decisions I made

- Branch and worktree names `privacy-fixes-redo-sonnet` (the owner said "a new branch"; `privacy-fixes` and `privacy-fixes-sonnet` are in use). Pushed to that branch, not `journal-redesign`, on the owner's "new branch". Report file name differs from the dispatch's `2026-09-29-privacy-fixes-completion-report.md`, which the landed F5 holds on `journal-redesign`.
- Base `445b292b`, the dispatch's, so the redo is comparable and the new branch has an F5 to build.
- `VACUUM` added then removed (Finding 1). The owner or planner may prefer an explicit `PRAGMA secure_delete=ON`; it costs nothing, but no test could bite it here either.
- Only `.gpx` files are swept, by last-modified time, not every file in `cacheDir/tracks`.
- The sweep at start runs off the main thread, like the capture sweep.
- `deleteStaleExports` returns `StaleSweep(found, deleted)`, not a bare count, so a file that could not be deleted is logged instead of swallowed.

## Flags outside scope

- The landed F5 on `journal-redesign` and this branch do the same job differently; they will conflict if both are merged. Which one is kept is for the owner. Merge is not authorised and was not done.
- `/home/zynergy-labs/Zynergy/forager-wt/privacy-fixes-sonnet` had a live session with uncommitted F5 edits from base `445b292b` (seen 13:23 PDT). Possibly a duplicate of this run; not touched.
- The `LeavingTheJournalFixesTest` flake above.
- Planner role moved during the session (to the session "You are the planner...", ref `05172f`); the hand-back goes there.

## Not tested / device-only

- On a phone: make a backup with a search on it, unzip it, and confirm `forager.db` has no `cached_searches` rows and no search text (`strings forager.db | grep`), and that the phone's searches still show afterwards. This also settles whether `secure_delete` is on in the device's SQLite, which was only probed under Robolectric.
- On a phone: share a track, and confirm the receiving app opens it; confirm the file is gone from the app's cache after an hour and a restart (`run-as ... ls cache/tracks`).
- The one-hour boundary itself is tested at 59 and 61 minutes, not at exactly 60.
