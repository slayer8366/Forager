# Live counts and empty states: completion (dispatch 2026-10-28-08)

Hazel, 2026-10-28. Dispatch `prompts/preserved/2026-10-28-08-live-counts-empty-states.md` with Amendment 1, on
`records-after-173`, RECORD -1780 and -1781. First stop: `2026-10-28-live-counts-empty-states-first-stop.md`.
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

**Observations, not gates.** Note whether any count flickers as it changes, and whether a list's scroll position stays where it was when a count changes.
