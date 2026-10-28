# Two Records follow-ups, look-only (dispatch 2026-10-28-07)

Hazel, 2026-10-28. Dispatch `prompts/preserved/2026-10-28-07-two-records-follow-ups.md` on `records-after-173`,
RECORD -1779. Look-only: no app code changed, no phone, no Gradle.

**Base.** Branch `records-follow-ups`, cut from `origin/pre-main` at `9f2df1abb` (#432's merge). Confirmed as the
remote head with `git ls-remote origin pre-main` before starting. Every file:line below is at `9f2df1abb`.

**Sources read.** The two S22 reports: `docs/audits/2026-10-28-s22-pr432-round2.md` on `s22-pr432-r2` (Part 3 and
its two observations, lines 176 to 202) and `docs/audits/2026-10-28-s22-pr432-round3.md` on `s22-pr432-r3` (setup
at :32 to :35, cleanup at :134 to :146). The screenshots and XML dumps they cite are off GitHub and were not seen.

## 1. A stale "used in 1 journal entry"

### Finding

**The code allows (a), a stale count held on screen. It does not allow (b) to produce the reading seen.**

### How the count reaches the sheet and the snackbar

- **The query.** `CartographyEntryDao.countEntriesReferencingWaypoint` (`CartographyEntryDao.kt:165` to `:174`)
  counts `cartography_entry_waypoint_refs` rows for the waypoint with `kept = 1`, **inner-joined to
  `cartography_entries`** with `isDraft = 0`. It is a `suspend` one-shot, not a Flow.
- **The read.** `TrackRecordingLists.loadWaypoints` (`TrackRecordingLists.kt:51` to `:68`) reads the waypoint list,
  then asks once per waypoint (`:60`) through `waypointEntryReferenceCountOrZero` (`EntryReferenceCounts.kt:48`;
  wired at `TrackRecordingViewModelFactory.kt:28`), and stores the result as a map keyed on waypoint id,
  `TrackRecordingUiState.waypointEntryReferenceCounts` (`TrackRecordingUiState.kt:57`). Nothing re-reads it until
  `loadWaypoints` runs again.
- **The sheet.** `MainActivity.kt:689` passes that map down through `RecordsTab.kt:102` and `:440` to
  `RecordDetailsSheet`, and `RecordDetailsContent.kt:75` draws "Used in" with `journalEntryCountLabel`
  (`RecordDetailsFormat.kt:23` to `:27`).
- **The snackbar.** `TrackRecordingPendingDeletes.requestRemoveWaypoint` copies the count from the same map at the
  moment of the swipe (`TrackRecordingPendingDeletes.kt:58`), and `PendingDeleteSnackbar.kt:58` writes
  "Waypoint deleted · used in 1 journal entry". So the sheet and the snackbar cannot disagree: both read one map.

### Who refreshes the map

Every caller of `loadWaypoints` (`git grep "loadWaypoints()"` in `app/src/main`):

| Caller | When it runs |
|---|---|
| `TrackRecordingViewModel.kt:359` | the ViewModel's `init`: once per process (the ViewModel is Activity-scoped, `MainActivity.kt:185`, so it survives rotation and theme changes) |
| `TrackRecordingLists.kt:106` | a waypoint is dropped |
| `TrackRecordingLists.kt:117`, `TrackRecordingPendingDeletes.kt:89` | a waypoint delete succeeds |
| `TrackRecordingAutoWaypoints.kt:87`, `:112` | the recording's origin or end waypoint is made |
| `MainActivity.kt:412` | a GPX import succeeds |
| `BackupViewModelFactory.kt:46` | a backup is restored |

**No journal-entry action is on this list.** Neither entry delete path (`CartographyEntryExits.kt:228` to `:246`,
`CartographyPendingDeletes.kt:90` to `:115`) nor entry save touches the Track ViewModel. So after an entry that keeps
a waypoint is deleted, the waypoint's count stays at whatever the last `loadWaypoints` read.

### The entry-delete path

- Report screen > Entry options > Delete entry > confirm (`CartographyEntryReportScreen.kt:501`, `:768`) calls
  `onDeleteEntry`, wired to `CartographyViewModel::onDeleteEntry` (`JournalTab.kt:771`, `MainActivity.kt:581`), then
  `CartographyEntryExits.onDeleteEntry` (`CartographyEntryExits.kt:228`).
- The card swipe calls `requestDeleteEntry` (`MainActivity.kt:582`), and its commit runs at
  `CartographyPendingDeletes.kt:101` (or `:128` on clear).
- Both go through `DeleteCartographyEntryUseCase` to `RoomCartographyEntryRepository.delete`
  (`RoomCartographyEntryRepository.kt:48` to `:50`), the one caller of `deleteEntryAndRefs`.
- `deleteEntryAndRefs` (`CartographyEntryDao.kt:134` to `:143`) is one `@Transaction`: the saved track paths, all
  five ref tables (waypoint refs at `:138`), then the entry row.
- `deleteEntryById` (`:96`) has no other caller, and no other `DELETE FROM cartography_entries` exists in
  `app/src/main`. So every entry delete takes the ref rows with it, atomically.

### Why (b) cannot give the reading

- **The join.** Suppose a waypoint ref row did outlive its entry. The count's inner join on `cartography_entries`
  (`CartographyEntryDao.kt:168`) would still leave it out, so a fresh read would say 0. A count of 1 needs the entry
  row itself to exist. The Journal read "No entries yet" (round 2, :180).
- **The waypoint delete leaves ref rows.** It removes ref rows from nothing: `RoomWaypointRepository.delete`
  (`RoomWaypointRepository.kt:34` to `:35`) calls `WaypointDao.deleteById` (`WaypointDao.kt:53` to `:54`). The ref
  tables have no `@ForeignKey` (`CartographyEntryEntity.kt:73`) and no trigger. That the ref row survives a waypoint
  delete is the rule, pinned by `TrackDeleteEntryRefsTest.kt:226` to `:234`.
- **So the 0 in round 2's database copy was already there.** The copy showed `cartography_entry_waypoint_refs` at 0
  after both deletes. The waypoint delete cannot have made it 0, so the only path that could is the entry delete.
  The ref row was already gone before the waypoint went, which is the opposite of (b). This is an inference from
  the code and the copy's counts. No database was read at the moment in between.

### What the two sightings fit (inferred, not observed)

- **Round 2.** The entry and Waypoint 1 survived an `adb install -r` from 4144 to 4150 (round 2, :115, :128). The
  new process's `init` load counted 1. The entry delete did not refresh it, so the sheet and the snackbar showed 1.
- **Round 3.** Waypoint 1 was dropped first, which loads the count at 0. It was then included in the entry, which
  does not refresh the count (round 3, :32 to :35). The count read 0 the whole time and happened to be right at
  cleanup.
- So round 3's honest "Waypoint deleted" is not evidence against (a). It is the same stale value, read when stale
  and true agreed. I did not check whether anything in round 3 restarted the process between the entry's save and
  its delete.

### Sibling cases the same code allows (read, not reproduced)

- **The other direction.** Drop a waypoint, then save an entry that keeps it. The sheet says "Used in: no journal
  entries" and the snackbar warns of nothing, until something reloads.
- **Tracks.** `loadTracks` (`TrackRecordingLists.kt:78` to `:90`) has the same shape for
  `trackEntryReferenceCounts`.
- **Offline regions.** `AvailabilityOfflineMaps.loadOfflineRegions` (`AvailabilityOfflineMaps.kt:111` to `:129`)
  has the same shape for `offlineRegionEntryReferenceCounts`. I did not trace every one of its callers.
- **Photos.** The photo counts in `MushroomLogViewModel` were not traced.

### Steps that would reproduce (a) on a phone (not run)

1. Drop a waypoint. Make a journal entry, include the waypoint, and save it.
2. Make the waypoint list reload while the entry exists: drop a second waypoint, or force-stop and reopen the app.
3. Open the entry and use Entry options > Delete entry > Delete.
4. Go to Records > Waypoints and tap the first waypoint. **Expected if (a):** "Used in: 1 journal entry".
5. Swipe it fully. **Expected if (a):** "Waypoint deleted · used in 1 journal entry".

Without step 2 the count was read as 0 when the waypoint was dropped, so it reads 0 and the bug hides (round 3).

### Proposed failing tests (not written)

**For (a), a test that fails today.**
- **Setup.** Real in-memory Room, with a `TrackRecordingViewModel` and a `CartographyViewModel` built on the real
  repositories over the same database. Seed a waypoint and a committed entry that keeps it. Build the Track
  ViewModel, so its `init` reads the count as 1.
- **Act.** Delete the entry through its real entry point: `CartographyViewModel.onDeleteEntry`, or better the
  report screen's Delete dialog in a Compose test.
- **Assert.** Read `waypointEntryReferenceCounts[wp]` and, after `requestRemoveWaypoint`,
  `pendingWaypointDelete.entryReferenceCount`. Today both read 1; they should read 0.
- **A second case.** Save an entry that keeps an already-loaded waypoint, and expect 1. Today it reads 0.
- **Shape.** Whichever ViewModel class this lands in, any test touching the Track ViewModel goes through
  `TrackRecordingViewModelTest.runRecordingTest`'s stop-in-`finally` shape if it records (CLAUDE.md, the poll-loop
  stall).
- **The fix is a design question.** How the Track ViewModel learns of an entry change is not decided here. One way
  is to refresh on entry save or delete. Another is a Flow-backed count. See the questions below.

**For (b), a guard, not a reproduction.**
- **Test.** A DAO or repository test on in-memory Room: save an entry with a kept waypoint ref, call
  `repository.delete`, then assert that `dao.getWaypointRefs(entryId)` is empty (`CartographyEntryDao.kt:30`).
- **Assert on the ref table, not the count.** The count's join hides a surviving ref row. A test asserting
  `countEntriesReferencingWaypoint == 0` would pass with `:138` deleted, so it would not hold the claim (CLAUDE.md,
  "a check that passes because it never saw the data that could fail it").
- **Revert check.** Delete line `:138` and confirm the test fails with a message naming the waypoint ref.
- **It passes today.** The code does not allow (b), so this test passes before and after any fix for (a). It guards
  `:138`; it proves nothing about the bug.
- **Existing tests.** `TrackDeleteEntryRefsTest.kt:216` to `:224` covers entry delete for saved track paths only. I
  found no existing test that reads the waypoint ref rows after an entry delete.

## 2. Finds in Records shows a blank list

### Finding

**Finds has no empty-state line because it never had one.**
- **What Finds draws.** Records' Finds chip draws `findsContent` (`RecordsTab.kt:426`). JournalTab supplies it
  (`JournalTab.kt:837`, `:629`) as `FindsGalleryScreen` (`JournalTab.kt:586` to `:612`).
- **What an empty grid shows.** When the grid is empty, `FindsGalleryScreen` draws text only when there is a load
  error (`FindsGalleryScreen.kt:165` to `:171`). Otherwise it draws an empty `LazyVerticalGrid` (`:172`), on both the
  Log and Drafts tabs.
- **What the cue used to be.** Until RECORD -751 (`f09d15dbf`), an empty Log still showed the grid's "+" tile, and
  that tile was the cue. -751 replaced the tile with the floating "New find" button (`FindsGalleryScreen.kt:212` to
  `:219`). Since then an empty Log has no tile and no text.
- **The S22 screenshot.** In a short landscape window the button moves to the L1 row (`:153`). I could not check
  whether the button showed in the S22's `31a-records.png`, which is off GitHub.

### Every Records filter and its empty state

| Filter | Where | Empty text |
|---|---|---|
| All | `RecordsLogbookList.kt:141` to `:143` | "No records yet." |
| Finds (Log) | `FindsGalleryScreen.kt:165` to `:172` | none (only a load error shows) |
| Finds (Drafts) | same | none |
| Tracks | `TrackExportPanel.kt:99` to `:105` | "No recorded tracks yet." (the Import GPX button sits above it, `RecordsTab.kt:408` to `:410`) |
| Waypoints | `AvailabilityTripsWaypointsUi.kt:225` to `:228` | "No waypoints dropped yet. Tap the add button on the map to drop one." |
| Offline maps | `OfflineRegionsList.kt:133` to `:134` | "No regions downloaded yet." (under the tile budget line) |

**Two more lines.**
- All also says "Finds are not listed here. See the Finds chip." when it has no finds list (`RecordsLogbookList.kt:135`
  to `:139`).
- The nearest sibling to Finds is the Journal's Entries timeline, which has the same kind of "New" button: "No
  entries yet. Use New entry to start one." (`CartographyScreen.kt:644`).

**Not asked, noted.** Only Waypoints (and Entries) follow "Honest copy plus a next step". All ("No records yet."),
Tracks and Offline maps state the absence only.

### Proposed Finds line (the owner's call)

Placed where the load-error text sits (`FindsGalleryScreen.kt:165`), on the Log tab only, when it has no finds and
no error, in the same `bodyMedium`/`bodySmall` shape as its siblings:

- **Option A**, matching Entries word for word in shape: "No finds yet. Use New find to log one."
- **Option B**, matching Waypoints' "Tap ... to ..." shape: "No finds logged yet. Tap New find to log your first one."

Both name the button the user can see. In a short landscape window that button is in the row above, not floating,
but the wording holds either way.

## Questions for the owner

1. **The stale count.** A waypoint's "Used in N journal entries" (in its details and in its delete message) is read
   when the waypoint list loads, and is not read again when a journal entry is saved or deleted. Should it update
   as soon as an entry that includes the waypoint is saved or deleted? The same applies to tracks and offline maps.
   The answer decides the fix's shape (refresh on entry change, or a live count), which is a design decision I have
   not made.
2. **Finds wording.** Option A, Option B, or your own words.
3. **Finds Drafts.** Should the Drafts tab get a line too when it is empty? A suggestion, if yes: "No drafts. A find
   you leave before saving waits here."
4. **The other filters.** All, Tracks and Offline maps say only that the list is empty. Bring them in line with
   "say what the user can do" now, or leave them?

## Verified, inferred, not determined

- **Read in code at `9f2df1abb`:** everything with a file:line above.
- **Inferred:** that round 2's count was loaded at process start after the install, and round 3's when the waypoint
  was dropped. Both come from the reports' own sequences, not from logs.
- **Not determined:**
  - The database state between the two deletes in round 2.
  - Whether the "New find" button was on screen in round 2's blank Finds.
  - The callers of `loadOfflineRegions` and the photo counts.
- **Not run:** no tests, no build, no phone.
