# Live counts and empty states: completion (dispatch 2026-10-28-08)

Hazel, 2026-10-28. Dispatch `prompts/preserved/2026-10-28-08-live-counts-empty-states.md` with Amendment 1, on
`records-after-173`, RECORD -1780 and -1781. PR #434 into pre-main, not merged. First stop: `2026-10-28-live-counts-empty-states-first-stop.md`.
Branch `live-counts-empty-states`, cut from pre-main `9f2df1abb`, which was the remote head when the work started.

## What landed

- **Commits:**
  - `1cc952719`: the first-stop report.
  - `1a078d5e2`: the change and its tests.
  - `0d6f969f3`: three old-text locators in the `testDebug` source set, which CI's first run named.
- **Live counts (the owner: "Live count (Recommended)").**
  - One Room query per record type returns every count at once. Room re-runs it after any committed write to that
    ref table or to `cartography_entries`: `CartographyEntryDao.kt:208` to `:252`.
  - The queries sit behind the new `EntryReferenceCountSource` (`domain/EntryReferenceCountSource.kt`), with
    `RoomEntryReferenceCountSource` as its Room implementation. Each query's `WHERE` is the same as its one-shot
    sibling: committed entries only, and kept references only where the table has `kept`.
  - Each screen starts one collector in `init`:
    - `TrackRecordingViewModel.kt:351`, through `TrackRecordingLists.kt:58` (waypoints and tracks).
    - `AvailabilityViewModel.kt:374`, through `AvailabilityOfflineMaps.kt:118` (offline maps).
    - `MushroomLogViewModel.kt:338`, through `MushroomLogGallery.kt:53` (photos: the delete message, the album badge
      and the map bubble line).
- **Removed, as the first stop proposed.**
  - The per-item count loops in the four list loads.
  - The four `*OrZero` wrappers (`EntryReferenceCounts.kt`) and `GetEntryReferenceCountUseCase`. Those four
    ViewModel wirings were their only callers.
  - The wrappers' two test files (7 tests), which tested only the wrappers.
  - **Kept:** the one-shot repository and DAO count queries. They are still used by `TrackDeleteEntryRefsTest`, and
    `CartographyEntryRepository` has 16 implementers.
- **A failed read** logs and keeps the last good counts (`watchEntryReferenceCounts`).
- **The waypoint details' "Used in"** reads a missing id as 0 (`RecordDetailsContent.kt:75`).
- **`TrackRecordingViewModel.kt`** is 861 lines, down from 868. It gained the `init` call and lost the two lambda
  parameters, so it was not split (the owner: "Allow the 3 lines (Recommended)").
  - `AvailabilityViewModel.kt` is 798 lines and `MushroomLogViewModel.kt` is 770.
  - Every new file is under 400 lines.
- **Empty lines, in the owner's words:**
  - All: `RecordsLogbookList.kt:142`.
  - Tracks: `TrackExportPanel.kt:101`.
  - Offline maps: `OfflineRegionsList.kt:134`.
  - Finds Log and Drafts: `FindsGalleryScreen.kt:172`. They show only when the tab is empty, loaded, and its load
    did not fail. A failed load keeps its own text.

## Verified, and how

- **CI on `0d6f969f3`:** run 40787634936 passed every job (build and test, lint, release compile, Worker, cutter).
- **Test count, from the JUnit XML:**
  - This branch: 807 suites, 5901 tests, 0 failures, 0 errors, 24 skipped.
  - Pre-main `9f2df1abb` (run 40781735857): 805 suites and 5891 tests.
  - 5891 − 7 removed + 17 new = 5901.
- **Fail first.** Branch `live-counts-fail-first` (`62aa61222`, run 40785584591) put the new and edited tests on
  pre-main's own code, with no app change. The offline-map test used pre-main's one-shot seam, wired as its factory
  wires it.
  - The build log has no compile errors. 55 tests ran and 13 failed, each for the predicted reason:
    - Every live-count test read the stale number: "still counts the deleted entry expected:<0> but was:<1>", or
      "does not count the entry just finished expected:<1> but was:<0>". That covers waypoint, track, offline map and
      photo, in both directions.
    - The four new empty lines were not found.
    - The details test found no "Used in" field.
  - Two tests passed there, as expected: the failed-load and the still-loading Finds tests assert an absence, so
    run B below is their check.
- **Revert run A.** `live-counts-revert-a2` (`f905c3a7f`, run 40787678089) made twelve one-line reverts on the
  forward code:
  - R1 to R4: each type's source replaced with `NoEntryReferenceCounts`.
  - R5: `deleteWaypointRefsForEntry(id)` dropped from `deleteEntryAndRefs`.
  - R6: `isDraft = 0` dropped from the waypoint live query.
  - R7: the details' `?.let` put back.
  - R8 to R10: the three old texts put back.
  - R11: the Finds line's condition set to `false`.
  - R14: the `.catch` dropped.
  - **Result.** No compile errors in the log. 350 tests ran and 39 failed. Every one is a failure its own revert
    could cause, with a message naming that edit's type or text. No failure fell outside the predictions.
    - Waypoint, track, region and photo count tests in `LiveWaypointTrackCountsTest`, `LiveRegionPhotoCountsTest`,
      `JournalPendingDeleteTest`, `TrackDeleteTest`, `TrackRecordingViewModelTest`, `MushroomLogViewModelTest` and
      `AvailabilityViewModelOfflineMapsTest`.
    - The (b) guard: "the deleted entry's waypoint ref row outlived it expected:<[]> but was:<[wp-1]>".
    - The source test: `{wp-1=3, wp-3=1}` where `{wp-1=2}` was expected.
    - The watch test: an `IOException`.
    - The details test, and each empty-line test with the locators of its text.
- **Revert run B.** `live-counts-revert-b` (`2cec6e27f`, run 40787680709) cut the Finds condition to
  `visibleEntries.isEmpty()`, which drops both `loadErrorMessage == null` and `!isLoading`. No compile errors. 50
  tests ran and exactly the two predicted tests failed: the failed-load test and the still-loading test, each finding
  "No finds yet. Use New find to log one." where it must not be.
  - **Correction (Amendment 2, Sorrel's N8).** The XML messages do not name the text. Both are `Failed:
    assertDoesNotExist.`, at `RecordsFilterChipsTest.kt:612` and `:626` (line numbers at `b677cdbab`). Those two lines
    are the two `assertDoesNotExist` calls on "No finds yet. Use New find to log one.", so the reading above is right,
    but the specificity comes from the line numbers, not from the message.
- **Each revert ran on its own branch.** The forward branch was never edited by a revert. After reading the runs it
  is still `0d6f969f3`, and its diff from pre-main is 47 files.
- The three check branches were deleted after their results were read.
- **The (b) guard passes before and after the fix, as the look report said.** It guards `CartographyEntryDao.kt:138`
  and proves nothing about the stale count.

## What changed in existing tests, and why

- **Six fixtures** moved from the one-shot lambdas to `FakeEntryReferenceCountSource`, keeping the same counts.
- **`mapLayersViewModel`** gained an `entryReferenceCounts` parameter that defaults to none.
- **Six locators** of the old Tracks and Offline maps texts now find the new texts:
  `AvailabilityScreenBackNavigationTest`, `GpxImportRecordsTest`, `OfflineMapsPanelTextTest`, and three in
  `testDebug/.../AvailabilityScreenSettingsPanelTest`.
- **One assertion's rule changed, by ruling.** `RecordDetailsSheetTest` asserted "a waypoint with no count given has
  no Used in line, rather than a made-up zero". Amendment 1 rules that a missing id reads as 0, so the test now asserts
  "no journal entries", and the edit is cited in place.

## Not verified, and gaps

- **Nothing on a phone.** The S22 steps are below.
- **`createAvailabilityViewModel`'s wiring line for the counts is not under test.** The offline-map tests use the
  test fixture, because the container's offline store is the map library's, which these tests do not stand up.
  - The other three factories are under test: `createTrackRecordingViewModel` and `createMushroomLogViewModel`
    directly, and `CartographyViewModel` from the container's use cases.
  - Removing the line would compile, because the parameter has a default, and the offline-map counts would read 0.
- **`OfflineMapsPanelTextTest` did not run in revert A.** Its classes are `OfflineMapsPanelTextLandscapeTest` and
  `OfflineMapsPanelTextPortraitTest`, so the filter matched nothing. It passed in the full forward run, but no revert
  checked that it reads the new text.
- **Inferred, not traced:** that every journal-entry write path goes through Room and so updates the counts. I found
  no raw SQLite writer, but I did not read every writer.

## Amendment 2 (Sorrel's review, RECORD -1784)

Hazel, 2026-10-29. Sorrel's review is `docs/reviews/2026-10-28-sorrel-pr434.md` (branch `sorrel-pr434`, `7ce136825`). The
owner's answers are in Amendment 2 of the dispatch on `records-after-173`. Worktree head at the start was `b677cdbab`,
matching origin. Pre-main was still `9f2df1abb`, the branch's merge base.

### What landed

- **`0b557868d`: the fail-first tests, with no app change.**
  - `EntryReferenceCountWatchTest` is rewritten as four tests. The old single test ran the watch with `advanceUntilIdle`;
    with a retry, it would have spun forever.
  - `RecordsFilterChipsTest` gains one test, with its own store fake at the bottom of the file.
- **`544a095b2`: N4, the retry (the owner: "Retry by itself (Recommended)").**
  - `watchEntryReferenceCounts` (`domain/EntryReferenceCountSource.kt:58-76`) logs each failed read and collects again
    after `entryReferenceCountRetryDelay` (`:88-89`).
  - A good read resets the wait (`.onEach`, `:67`).
  - Cancellation is not retried, and nor is an exception thrown by the writer: `retryWhen` passes both through. So
    clearing the ViewModel still ends the watch, waits included.
- **`5ecb7bd40`: N5, Drafts says it couldn't load (the owner: "Say it couldn't load (Recommended)").**
  - `MushroomLogViewModel.loadEntries` sets the new `draftsLoadErrorMessage` when the entries read but the drafts did
    not. The text is the Log tab's own failed-load text, now one constant, `LOG_UNAVAILABLE` ("Log entries unavailable.").
  - `FindsGalleryScreen.kt:171` picks the error for the tab being shown. On Drafts it shows the drafts error in the
    failed-load slot, and the empty line stays hidden.
  - `JournalTab.kt` gains one line to pass the field on. That file is 919 lines. The modular rule lets a fix of a few
    lines into a big file go in without a split first, so it was not split.
  - Drafts already on screen stay, as before. The error shows only when the tab has nothing to show.
- **`46cc439d7`: N1, no default for the count source.**
  - The `= NoEntryReferenceCounts` default is gone from `TrackRecordingViewModel`, `AvailabilityViewModel` and
    `MushroomLogViewModel`, along with their now-unused imports.
  - A factory that leaves out its wiring line no longer compiles. The three production factories already pass
    `container.entryReferenceCountSource`.
  - 36 test call sites in 30 files now pass `NoEntryReferenceCounts` by name. Sorrel counted 13 for
    `AvailabilityViewModel`; there were 19.
  - Main-code size change: −1 line in each ViewModel. `TrackRecordingViewModel.kt` is 860 lines.
  - **Slip in the commit message.** That commit message says 858 lines for `TrackRecordingViewModel.kt`. The real
    figure is 860 (`wc -l`). It is pushed, so it is corrected here rather than amended.
- **N8:** corrected in place above, under revert run B, as a marked correction.
- **Not done, as Amendment 2 says:** N6, removing the dead one-shot queries, is a later task. N3, N7 and N9 are record
  only.

### The retry timing chosen

- **The wait:** 1 s after the first failure, doubling each failure in a row (1, 2, 4, 8, 16 s), with a ceiling of 30 s.
  After that it is 30 s for as long as the failure lasts.
  - The figures are `ENTRY_REFERENCE_COUNT_FIRST_RETRY` and `ENTRY_REFERENCE_COUNT_RETRY_CEILING`.
- **Every failure is logged** as "Couldn't count journal entries keeping each <type>; trying again in <wait>."
- **Why these numbers:**
  - The database is on the phone, so a failed read is most likely a moment's contention, and 1 s brings the counts
    back before a user acts on them.
  - A fault that lasts is retried every 30 s. A count is then wrong for at most half a minute after the fault clears,
    and the log gets two lines a minute rather than a stream.
  - No field data shows either kind of failure. The ceiling is the one number to revisit if data appears.

### How the tests stop the retry (CLAUDE.md's poll-loop entry)

- **The risk.** A watch over a source that keeps failing never completes. The old test's `advanceUntilIdle` and
  `runTest`'s closing idle-advance would spin through its waits forever.
- **What every test does:**
  - Each test starts the watch through `watching` (`EntryReferenceCountWatchTest.kt:40-47`). That helper cancels the
    job in a `finally`, so an assertion that throws still stops it.
  - Each test moves time only by a bounded `advanceTimeBy`, and never calls `advanceUntilIdle`.
  - The sources that are meant to stay open end in `awaitCancellation()`, which schedules nothing.
- **`cancelling the watch ends its retries`** holds the cancellation rule:
  - It checks a positive control first: three tries in 5 s.
  - It then cancels, advances ten minutes, and asserts the job is complete, with no further try and no further log line.
- **Evidence that nothing hung:** the full run below finished its tests in the usual time.
- **The ViewModels.** Their production collectors run in `viewModelScope`, and no ViewModel test uses a source that
  throws. `FakeEntryReferenceCountSource` is `MutableStateFlow`s and `NoEntryReferenceCounts` is `emptyFlow()`, so no
  ViewModel test can enter the retry.

### Fail first

Run 40796636308 is a filtered `workflow_dispatch` run of `EntryReferenceCountWatchTest` and `RecordsFilterChipsTest` on
`0b557868d`.

- **The build was checked before the results.** The log has zero `e: file://` lines and the test task ran. The log
  names `0b557868d`.
- **Totals:** 25 tests and 5 failures, all predicted, read from the JUnit XML.
- **The four watch tests**, each stopped at the first failure:
  - "a failed read is tried again, and the counts read after it reach the screen expected:<[{wp-1=1}, {wp-1=2}]> but
    was:<[{wp-1=1}]>"
  - "waits of 1, 2, 4, 8 and 16 s, then 30 s each time after expected:<[0, 1000, 3000, 7000, 15000, 31000, 61000,
    91000]> but was:<[0]>"
  - "every read's counts reach the screen expected:<[{wp-1=1}, {wp-1=2}, {wp-1=3}]> but was:<[{wp-1=1}]>"
  - "positive control: before the cancel it was retrying (tries at 0, 1 and 3 s) expected:<3> but was:<1>"
- **The N5 test:**
  - It passed both of its positive controls: two reads ran, so the store's every-second-read failure did hit the
    drafts read, and the load had finished.
  - It then failed on Drafts with "The component with Text ... contains 'Log entries unavailable.' ... is not displayed!".
    "No drafts" was there instead.
- **The other 20** `RecordsFilterChipsTest` tests passed.

### Revert checks

- **How they ran.** Each was a one-line edit on its own branch, cut from `46cc439d7`, in its own throwaway worktree. The
  forward worktree was never edited. Each ran the same filter: `EntryReferenceCountWatchTest`, `RecordsFilterChipsTest`,
  `FindsGalleryScreenTest`, `LiveWaypointTrackCountsTest` and `LiveRegionPhotoCountsTest`.
- **Checked before reading any results.** Every log has zero `e: file://` lines and no compile failure. Each log names
  its own revert commit. Each run had 42 tests.

| Revert | Edit | Run | Result (XML) |
|---|---|---|---|
| a (`3d6bab836`) | `retryWhen`'s predicate returns `false`: no retry | 40797449489 | 4 failed: the 4 watch tests, each with the `IOException("disk read failed")` escaping the watch |
| b (`afbafda2c`) | `.coerceAtMost(ENTRY_REFERENCE_COUNT_RETRY_CEILING)` dropped | 40797458493 | 1 failed: "waits of 1, 2, 4, 8 and 16 s, then 30 s ..." expected `[..., 31000, 61000, 91000]` but was `[..., 31000, 63000, 95000]` |
| c (`c85893ecb`) | `.onEach { failuresInARow = 0 }` dropped | 40797468684 | 1 failed: "a good read in between means the next failure waits 1 s again, not 2 s expected:<[0, 1000, 2000]> but was:<[0, 1000, 3000]>" |
| d (`6445f29c7`) | `val shownLoadError = loadErrorMessage` (Drafts ignores the drafts error) | 40797480319 | 1 failed: the N5 test, "'Log entries unavailable.' ... is not displayed!" |

- **Every failure is one its own edit can cause, and nothing else failed.** Reverts b and c each failed only the one test
  that holds their rule.
- **Revert a needs both runs to read, like N3 in Sorrel's review.** With the predicate returning `false`, the exception
  escapes the watch, so the tests fail on that exception before they reach their assertion messages. That run shows the
  tests notice a watch that does not retry. The fail-first run is the one that shows each assertion catching it.
- **Afterwards:**
  - The forward branch was still `46cc439d7`, matching origin.
  - The three forward lines and the `FindsGalleryScreen` line were still present (grep).
  - The diff from pre-main is 79 files.
- The four revert branches and their worktrees were deleted after the results were read. `ls-remote` finds none.

### CI on the head

Run 40797376100 is the pull_request run on `46cc439d7`.

- **Every job passed:** build and test, lint, release compile, the Worker and the cutter.
- **The log has zero `e: file://` lines.**
- **JUnit XML:** 807 suites, 5905 tests, 0 failures, 0 errors, 24 skipped.
- **The count adds up:** 5901 − 1 (the old watch test) + 4 (the new watch tests) + 1 (the N5 test) = 5905.

### Not verified

- **Nothing on a phone.** S22 steps 7 and 8 below are new for this amendment.
- **A retry is unit-tested only, on a fake flow.**
  - Whether Room's `Flow` hands a query exception to the collector, so that `retryWhen` sees it, is still inferred.
    Sorrel recorded the same gap; Room's source was not read.
  - A real database failure cannot be forced on the phone without a debug hook, so S22 step 8 is an observation, not a
    gate.
- **The N5 fake's read order.** The store fails every second `getAll`. The test asserts exactly two reads, so it holds
  only while `loadEntries` reads the entries first and the drafts second. If that order changes, the positive control
  fails first, and the test cannot pass silently.
- **The N5 test's ViewModel is never cleared**, as with N7. Its collector is `emptyFlow()` and completes at once.

## S22 steps (SM-S908U; not run)

**Before installing.** This PR has no database change: `ForagerDatabase.version` is unchanged and there is no migration. So installing it over the S22's current build keeps its data. Check the installed build's schema first anyway, as the downgrade note asks.

Steps are in order, cheapest first. Keep screenshots off GitHub and describe them in words.

1. **A waypoint, save direction (the sibling case in the look report).**
   - Drop a new waypoint W.
   - Start a journal entry for today, include W, and tap Finish entry.
   - Without leaving the app or restarting it, go to Records > Waypoints and tap W.
   - **Pass:** "Used in: 1 journal entry". Before this PR it read "no journal entries" until something reloaded the list.
   - **Evidence:** a screenshot of the details sheet.
2. **The same waypoint, delete direction (round 2's reading).**
   - Open that entry and use Entry options > Delete entry > Delete.
   - Go to Records > Waypoints and tap W.
   - **Pass:** "Used in: no journal entries".
   - Close the sheet and swipe W fully.
   - **Pass:** the snackbar reads "Waypoint deleted", with no "used in". Tap Undo.
   - **Evidence:** both screenshots.
3. **A track.**
   - Take a finished track that no entry includes, or record a short one.
   - Include it in a new entry and tap Finish entry.
   - Swipe the track in Records > Tracks.
   - **Pass:** "Track deleted · used in 1 journal entry". Tap Undo.
   - Delete the entry, then swipe the track again.
   - **Pass:** "Track deleted". Tap Undo.
4. **A photo.**
   - Attach an album photo to a new entry and tap Finish entry.
   - **Pass:** the album tile shows its journal-entry badge without a reload.
   - Delete the entry.
   - **Pass:** the badge goes. A long-press Delete on the photo then reads "Photo deleted", with no journal entries named. Tap Undo.
5. **An offline map.**
   - Include an offline map in a new entry and tap Finish entry.
   - Swipe the map in Records > Offline maps.
   - **Pass:** "Offline map deleted · used in 1 journal entry". Tap Undo.
   - Delete the entry, then swipe again.
   - **Pass:** "Offline map deleted". Tap Undo.
6. **The empty lines.** Only for a list that is already empty on the S22; don't delete records to empty one. Say which lists were skipped.
   - **Pass, word for word:**
     - All: "No records yet. Record a track, drop a waypoint or download a map, and it shows here."
     - Tracks: "No recorded tracks yet. Tap the record button on the map to record one."
     - Offline maps: "No maps downloaded yet. Pan the map to an area and tap Download Maps."
     - Finds > Log: "No finds yet. Use New find to log one."
     - Finds > Drafts: "No drafts. A find you leave before saving waits here."
   - Also check that each line's next step names a control that is on screen.

7. **Drafts that could not be read (Amendment 2, N5).** Not something a phone can be made to do without a debug hook.
   - **Pass:** skip, and say so. The rule is held by `RecordsFilterChipsTest` and revert d.
   - **Instead, as a gate:** with no drafts, Finds > Drafts still reads "No drafts. A find you leave before saving
     waits here.", word for word.
   - **And:** with one draft, Drafts shows it, labelled "Drafts (1)", and no failed-load text.
8. **Counts after a long session (Amendment 2, N4).** Leave the app open on Records for a few minutes with the screen on.
   Then finish an entry that includes a waypoint and check its "Used in".
   - **Pass:** the count is right without a restart.
   - Note in words any `EntryReferenceCounts` line in logcat, if a log is taken. None is expected. An observation, not
     a gate.

**Observations, not gates.** Note whether any count flickers as it changes, and whether a list's scroll position stays where it was when a count changes.
