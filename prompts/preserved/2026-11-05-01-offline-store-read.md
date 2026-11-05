# Dispatch 2026-11-05-01: The drop-off reads the map's offline store reliably while the map writes to it (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2118 (intent). It follows S22 session 14 (RECORD -2116), where PR #490 (dispatch 2026-11-04-09, merged as 368f710fb) was checked. Every fact below was read at pre-main 368f710fb and records-after-173 10a33dadc while this was written, and is a premise to re-check, with file and line.

## Authorisation
**Not given yet.** The owner chose "Fix it in this release (Recommended)", which is a design answer, not a go. Hazel starts only on the owner's yes to "Start Hazel on dispatch 2026-11-05-01 now?".

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Rules for every item
- Verify first: Phase 1 reads and reports by message, then stops. Phase 2 builds, on the planner's go.
- Tests first, shown failing, then revert checks on throwaway CI branches. Full CI green on the last real commit. CI only: no Gradle on the laptop.
- Don't touch the phone. Never read, name or quote the Restricted Object. Our own map is the "Trail Map".
- New words a person would see go back to the planner before they're built.
- Stop and report, rather than choosing, on anything the owner would see that this doesn't rule.
- Keep files small and decoupled (the modular-code skill).

## Change: what the user sees

| | Category |
|---|---|
| **A** | What the user sees |
| **B** | Tests and evidence |
| **C** | Branch, build and report |

- **A1** (2026-11-05, -2117). **The owner, verbatim,** to "The read failure makes the drop-off unreliable at moments. How should it be handled?": **"Fix it in this release (Recommended)"**. The option read: "A verify-first dispatch for Hazel: find why the read fails while the map writes, and fix it so the drop-off reads reliably. The doubled opening and the signal-return pass go in it too. The release waits for it, since the drop-off is in the release."
- **A2. The read failure.** In session 14 a read-only open of MapLibre's store failed three times with "SQLiteReadOnlyDatabaseException: attempt to write a readonly database (code 776 SQLITE_READONLY_ROLLBACK[776]): , while compiling: PRAGMA cache_size". The log is `~/s22-2026-11-04/s14/logs/logcat-s14.txt`, around lines 36376, 36414 and 36459.
  - **Twice in the copy pass** (DropOffMap, 04:21:51.666 and .733 local). A second pass 1.4 s later worked.
  - **Once in the way-to's own read** (FindWayToWaypoint, 04:25:40.980: "the lead-in: the map could not be read"). The walker's end went by trail, so no pick-up could be chosen there.
  - Session 13 had none.
  - **Afterwards:** the drop-off's reads of MapLibre's store succeed while MapLibre is writing, so Navigate's choice doesn't depend on that moment. Where a read still can't be made, it goes by trail and is logged, exactly as today (dispatch 2026-11-04-04 A1). Nothing goes dark.
- **A3. The doubled opening.** In step 2 of session 14, one tap through Tools > Offline Maps logged "Offline maps opened with no signal: no drop-off map pass this time." twice, 0.23 s apart (04:20:27.844 and 04:20:28.074). With signal it seems to start two passes. **Afterwards:** one opening is one opening.
- **A4. The signal-return pass.** In step 3, signal returning while Offline maps was open started no pass in about 15 s. That is inferred from no DropOffMap line and no us/15/ request. **Afterwards:** it does what P2 of dispatch 2026-11-04-09 rules (RECORD -2110), or you report why it can't.

## Scope boundary
- **May touch:**
  - a new branch from pre-main;
  - `map/MapLibreOfflineBasemap.kt`, the read-only open at :56, and its callers;
  - the copy pass's read of MapLibre's store;
  - the Offline maps opening and signal-return triggers (`ui/availability/AvailabilityViewModelOfflineMaps.kt`, `AvailabilityOfflineRoutes.kt`);
  - new files; tests; your completion report.
- **Must not touch:**
  - **any write to MapLibre's store,** including opening it writable, which could roll back MapLibre's own unfinished write;
  - MapLibre's configuration (journal mode, cache size);
  - the Worker; Theme.kt; Color.kt; pre-main; main; RECORD.md; the audit index; the phone.
  - Stop and report if the fix seems to need any of these.

## Baseline
- pre-main 368f710fb, PR #490's merge.
- `MapLibreOfflineBasemap.kt:56` opens with `SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)`.
- **The planner's read, not a runtime fact:** session 14's end-s14 copy of mbgl-offline.db reports `PRAGMA journal_mode` = `delete`. A file copy shows only that it isn't in WAL. The mode MapLibre runs is for you to read from MapLibre Native's source at android-v13.5.0.

## Verify first (Phase 1: report these, then stop)
1. **Why the open fails,** from SQLite's documentation and MapLibre Native's source at tag android-v13.5.0, quoted with where each was read:
   - what SQLITE_READONLY_ROLLBACK means;
   - what journal mode and locking MapLibre uses;
   - why a read-only connection meets a hot journal while MapLibre writes;
   - why `PRAGMA cache_size` is where it shows (Android's open sequence).
2. **How often and when** it can happen: every MapLibre write, or only some. Use session 13's and 14's logs (`~/s22-2026-11-04/s13/logs/`, `s14/logs/`).
3. **The fix options, without choosing.** For each: whether it can ever write to MapLibre's store, what it costs, and what the user would notice. Include at least:
   - retry with a bounded wait;
   - a different open (flags or URI, such as nolock or immutable), with whether it is safe while MapLibre writes, read at source;
   - reading through MapLibre's own API, if one exists at 13.5.0;
   - anything else you find.
   - **Stop if every option writes to MapLibre's store.**
4. **A test that fails first** for the read failure. Can a hot journal be made on the JVM (Robolectric's SQLite) so the read fails today? If not, say what can be tested, and what is device-only.
5. **The doubled opening:** why one tap gives two openings, with file and line.
6. **The signal-return pass:** why it didn't run in session 14, with file and line.
7. **Your mechanism prediction, test plan and file list,** with each file's size.
8. **Anything the owner would see that this doesn't rule.**

## Predictions
- **Outcome (planner):** after the fix, repeated Navigate to Waypoint 1 on a good fix, while the map is drawing online, never logs "the map could not be read". One opening of Offline maps logs and runs one pass. Signal returning while it's open runs one pass.
- **Mechanism (coder):** in your Phase 1 report.

## Evidence required
- Tests first, each shown failing for its reason.
- Revert checks under CLAUDE.md's runner rules.
- CI counts from the JUnit XML.
- A device check for the planner to run on the S22, cheapest step first, each step with its pass condition and evidence. Include repeated reads while the map writes.

## Stops
Stop and report on:
- any write to MapLibre's store, or a change to its configuration;
- a server or Worker change;
- new words;
- an owner-level choice;
- a wrong premise;
- a weakened test;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main from a new branch, green on CI at its last real commit, merged only on the owner's word. Then the planner's S22 phone check.

## Final message
Your completion report on the branch at `docs/audits/2026-11-05-01-offline-store-read-completion-report.md`, giving:
- commits and the PR;
- failing-first evidence;
- revert checks;
- CI counts;
- what is untested;
- calls beyond the dispatch;
- the device check.

The planner files your reports beside this dispatch, unedited.
