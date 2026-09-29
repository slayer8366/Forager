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
