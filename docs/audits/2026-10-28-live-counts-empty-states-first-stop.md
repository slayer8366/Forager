# Live counts and empty states: first stop (dispatch 2026-10-28-08)

Hazel, 2026-10-28. Dispatch `prompts/preserved/2026-10-28-08-live-counts-empty-states.md` on `records-after-173`,
RECORD -1780. Builds on `docs/audits/2026-10-28-records-follow-ups-look.md` (`records-follow-ups`, 3086c231c).
**Nothing built yet.** No app code changed, no Gradle, no phone.

**Base.** Branch `live-counts-empty-states`, cut from `origin/pre-main` at `9f2df1abb`, confirmed as the remote head
after `git fetch` before starting. Every file:line below is at `9f2df1abb`.

## 1. Live counts: proposed mechanism

### What exists

- Four one-shot `suspend` count queries, `CartographyEntryDao.kt:154` to `:199` (track, waypoint, offline region,
  photo). Each joins `cartography_entries` and counts committed entries only (`isDraft = 0`, plus `kept = 1` for the
  first three).
- Each list's load asks once per item and stores a map: waypoints `TrackRecordingLists.kt:60`, tracks `:85`, offline
  regions `AvailabilityOfflineMaps.kt:127`, photos `MushroomLogGallery.kt:52`.
- No journal-entry save or delete calls any of those loads. The entry delete is `CartographyEntryExits.kt:228`; it
  touches only the Journal's own state.
- No DAO in `data/local` returns a `Flow` today. `room-ktx` is on the classpath (`gradle/libs.versions.toml:102`), so
  Room Flow queries are available.

### Proposal: a live count from the database (recommended)

- **The query.** One new DAO query per record type that returns every count at once, as a Room `Flow`.
  - Example: `SELECT waypointId AS id, COUNT(*) AS count FROM cartography_entry_waypoint_refs INNER JOIN
    cartography_entries ... WHERE kept = 1 AND isDraft = 0 GROUP BY waypointId`.
  - The `WHERE` is the same as the one-shot query beside it.
  - Room re-runs it after any committed write to either table. So every entry save, delete, draft commit, backup
    restore or future write path updates it, with no caller having to remember to.
- **The interface.** A new interface the project owns, `EntryReferenceCountSource`, with one `observe...` per type
  returning `Flow<Map<id, Int>>`. `RoomCartographyEntryRepository` implements it.
  - It is new and separate, rather than four methods added to `CartographyEntryRepository`. That interface has 16
    implementers in `main` and `test`. A new one leaves every fake untouched.
- **The collector.** Each ViewModel gets one new collector, launched once in its scope, that writes the count map.
  - Waypoints and tracks: `TrackRecordingViewModel`. Offline regions: `AvailabilityViewModel`. Photos:
    `MushroomLogViewModel`.
  - The collector lives in a small new class, not in the ViewModel's body.
  - A read failure is logged, and the last good map stays (CLAUDE.md: no silent fallback).
- **The one-shot reads go.** The per-item count loops at `TrackRecordingLists.kt:60` and `:85`,
  `AvailabilityOfflineMaps.kt:127` and `MushroomLogGallery.kt:52` are removed, with the `*OrZero` wrappers they use.
  - **Why remove them:** if both stayed, the two would write one map. A one-shot read that started before an entry
    delete could land after the live one and put the stale number back.
  - **This is a replacement of working code, not only an addition.** I'm saying so because the dispatch asks for a
    new path, not a conditional in old code. No conditional is added. The old read is taken out and the new path is
    the only writer.
  - The six test files that inject the one-shot lambdas would move to the new source.
- **Ids with no entry.** The map holds only ids that have a count, so a missing id reads 0. Every reader already
  treats a missing id as 0 (`TrackRecordingPendingDeletes.kt:58`, `AvailabilityOfflineRegionDeletes.kt:61`,
  `MushroomLogPendingDeletes.kt:218`, `EntriesAlbum.kt:185`, `MapBubbles.kt:385`), except one.
  - The exception is the waypoint sheet's "Used in" (`RecordDetailsContent.kt:75`). It draws only when the id is in
    the map. Today every loaded waypoint is in the map, so it always shows. Under the new map it would vanish at 0.
  - So that line reads `referenceCounts[id] ?: 0`, which keeps "Used in: no journal entries" showing as it does
    today.

### The alternative: refresh on each entry change

- A shared "entries changed" signal, sent by the save and delete use cases, that each ViewModel listens to and then
  re-runs its loads.
- **Why not.** It covers only the write paths that remember to send it. Draft commit, backup restore and any later
  writer would each need adding, and a missed one brings this bug back unseen. It also re-reads the whole list, not
  only the counts.

### Do photos carry the count? Yes, and they are included

- A photo's journal-entry count is read into `MushroomLogUiState.cartographyEntryPhotoReferenceCounts`
  (`MushroomLogGallery.kt:52`). It has the same staleness: no entry save or delete reloads the gallery.
- Three places show it:
  - The photo delete message, "Photo deleted · used in ... journal entries" (`MushroomLogPendingDeletes.kt:218`,
    `PendingDeleteSnackbar.kt:239`).
  - The album tile's "in a journal entry" badge (`EntriesAlbum.kt:291`).
  - The map's photo bubble line, "2 journal entries" (`MapBubbles.kt:385`).
- So the photo count gets the same live query, and its tests.

### Premise to correct: tracks and offline maps have no "Used in" in their details

- The dispatch says each type's count shows "in its details and in its delete message".
- In the code, only `WaypointDetails` draws "Used in" (`RecordDetailsContent.kt:75`). `TrackDetails` (`:98`) and
  `OfflineRegionDetails` (`:213`) have no such field. For those two the count reaches the screen only in the delete
  message (`PendingDeleteSnackbar.kt:58` to `:59`).
- **I will not add a "Used in" field to those details.** That would be new words on screen. If the owner wants it,
  it is a separate yes.

### Two questions on the mechanism

1. **Live count, or refresh on entry change?** I recommend the live count.
2. **The 800-line rule.** `TrackRecordingViewModel.kt` is 868 lines today. The dispatch says to split before adding
   to anything past 800.
   - The wiring is about three lines there: a constructor parameter, and one call in `init` to the new class.
   - Either those three lines are accepted as is, or the file is split first, which is a separate, larger change.
   - `AvailabilityViewModel.kt` (795) and `MushroomLogViewModel.kt` (767) stay under 800 with the same three lines.

### Test notes (for after the go)

- **Test (a) and its mirror.** For each of the four types, a test on real in-memory Room through
  `CartographyViewModel.onDeleteEntry`. The save direction goes through the entry's real save path. Each reads the
  map and the pending-delete count, and is written to fail first on today's code.
- **Test (b).** The (b) guard reads `getWaypointRefs` after `repository.delete`, with a revert check on
  `CartographyEntryDao.kt:138`, as the look report proposed. The report also said it passes before and after any
  fix, so it guards `:138` and proves nothing about the stale count.
- **The poll-loop rule.** Any test using the Track ViewModel that records goes through `runRecordingTest`'s
  stop-in-`finally`.
- **Not yet known.** A Room `Flow` collector is a suspended wait, not a `delay` loop, so I don't expect it to make
  `runTest`'s idle-advance spin. That is inferred, not run, and CI will show it.

## 2. Empty lines for All, Tracks and Offline maps: proposed wording (the owner's words)

Their siblings, for shape: Journal "No entries yet. Use New entry to start one." (`CartographyScreen.kt:644`).
Waypoints "No waypoints dropped yet. Tap the add button on the map to drop one."
(`AvailabilityTripsWaypointsUi.kt:226`).

### Tracks

- **Today:** "No recorded tracks yet." (`TrackExportPanel.kt:101`).
- **What the user can do here:**
  - The Import GPX button sits above this line on the same screen (`RecordsTab.kt:408` to `:410`).
  - Recording is the map's round record button. Its label is "Start recording track" (`TrailheadControls.kt:164`).
- **Option A:** "No recorded tracks yet. Tap the record button on the map to record one, or use Import GPX."
- **Option B:** "No recorded tracks yet. Tap the record button on the map to record one." This one leaves Import GPX
  to the button above.

### Offline maps

- **Today:** "No regions downloaded yet." (`OfflineRegionsList.kt:134`).
- **What the user can do here:** the same panel has the pin map and the "Download Maps" button
  (`AvailabilityOfflineMapsUi.kt:259`).
  - In a wide window those sit beside the list, not above it (`OfflineMapsSideBySide.kt:67`). So the wording does
    not say "above".
- **Option A:** "No regions downloaded yet. Pan the map to an area and tap Download Maps."
- **Option B:** "No maps downloaded yet. Pan the map to an area and tap Download Maps." This one uses "maps", as the
  chip and the button do, in place of "regions".

### All

- **Today:** "No records yet." (`RecordsLogbookList.kt:142`).
- **What it lists:** tracks, waypoints and offline maps. Finds are listed only under their own chip
  (`RecordsLogbookList.kt:135` to `:139`). No one button makes a record from here.
- **Option A:** "No records yet. Record a track, drop a waypoint or download a map, and it shows here."
- **Option B:** "No records yet. Use the record or add button on the map to make one." This one names the two map
  buttons, as the Waypoints line does, and leaves out downloads.

## What goes ahead and what waits

- **Waits for the owner, through the planner:**
  - The three lines above.
  - Mechanism questions 1 and 2.
- **May go ahead under the dispatch:** the Finds Log and Drafts lines. I am holding them anyway, so the whole change
  lands as one PR after the go.

## Verified, inferred, not determined

- **Read at `9f2df1abb`:** every file:line above. The 16 implementers count is from `git grep -l ":
  CartographyEntryRepository"` over `app/src`. The six test files count is a `git grep` for the four one-shot lambda
  names under `app/src/test`.
- **Inferred:** that Room's invalidation covers every entry write path. All writes go through Room, and I found no
  raw SQLite writer, but I did not trace every writer. Also inferred: that the Flow collector does not stall
  `runTest`.
- **Not determined:** how the Records screen's Compose tests will wait on a Room Flow emission under Robolectric.
- **Index row:** not appended to `docs/audits/README.md` at this stop. It goes in with the completion report, because
  that file is a serialization point.
