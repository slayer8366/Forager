# Privacy and data-sharing changes for the site (planner, 2026-09-29)

The owner asked, verbatim: "Make a report for all privacy and data sharing changes for me to update the site with".

**What this compares.** The live pages are `privacy/index.html` and `delete-data/index.html` on zynergy-site `origin/main` (`0688e4d`). Both say "Last updated: 11 September 2026". They are compared against the app's code:
- **`main`:** `faf2f88f`, 2026-09-26.
- **`journal-redesign`:** `44aba8f6`, the head this report was written on.

Each item says which of the two carries it. "On `main`" means it is already in the app today, so the page is already out of date there. "Journal PR" means it ships when `journal-redesign` merges.

**How it was checked.** Everything here was read from code at those commits, with file and line given, except where marked *unverified*. Suggested wording is a draft for the owner to edit. Nothing on the site has been changed.

---

## A. Already wrong today (on `main`)

### A1. "There is no export or import feature yet"
- **Where on the site:** privacy, "Android's own backup is switched off", the note at its end.
- **What the code does:** tracks export as GPX files through Android's share sheet.
  - Code: `TrackExportPanel.kt`, with `shareGpxIntent` using `ACTION_SEND`.
  - Caller on `main`: `RecordsTab.kt:237`, Records → Recorded tracks.
  - The user picks where each file goes, for example Files, email or another app.
- **Suggested:** "You can export a recorded track as a GPX file from Records → Recorded tracks. Android's share sheet sends it wherever you choose. Once shared, that copy is outside Forager, and it contains the track's coordinates and times."

### A2. "Photos and location metadata": the in-app camera paragraph
- **What the site says:** "Forager does not run an EXIF-stripping step" and "the camera app writes into a file in Forager's own directory … whatever GPS tags the camera app wrote are in the stored file."
- **What the code does:**
  - Photos taken in the app use Forager's own camera, not an outside camera app (`CameraCapturePhotoSource.kt:30`).
  - Each capture is stripped of all metadata except orientation, by an allowlist (`FilePhotoStore.kt:132`, which calls `scrubPhotoMetadata`; the design is in `PhotoMetadataScrub.kt:10-60`).
  - Only three segment kinds are kept: JFIF, the ICC colour profile, and the orientation. GPS, the camera model, times, thumbnails, XMP and IPTC are all removed.
  - This is a JPEG-only step. In-app captures are JPEG.
- **Suggested:** "Photos taken with Forager's camera are stripped of all embedded metadata when they are saved: location, camera details, timestamps, thumbnails, everything. Only the orientation is put back, so the photo displays the right way up. The coordinate of a find is kept in the app's database, not in the photo."

### A3. "Photos and location metadata": imports
This is still broadly right, but needs sharpening:
- Imports are **not** stripped, by the owner's ruling (see B2). The stored copy is a byte copy of what the picker hands over (`FilePhotoStore.kt:37-65`).
- On Android 10 and later, the platform removes GPS from that copy unless the app opts in. Forager does not opt in for the copy.
- On Android 8–9 there is no such removal, so an import can keep its GPS.
- Forager separately reads an import's original date and location, using `ACCESS_MEDIA_LOCATION` and `setRequireOriginal` (`FilePhotoStore.kt:160-167`). It does this to date and place the find. That reading goes into the app's database only.

The existing text is roughly this. Keep it, and add B2.

### A4. Permissions: "Camera: taking a photo for a journal entry"
Still true. Only the context changed: it is now Forager's own camera (A2). No wording change is needed, unless the page elsewhere mentions a camera app.

---

## B. Ships with the Journal PR (`journal-redesign` only)

### B1. Save to Gallery (photo export)
- **Built:** `edb74209`, `photo/PhotoExporter.kt`.
- **What it does:** on the photo viewer, the user can save a copy of a photo.
  - **Android 10 and later:** into the phone's Gallery, in a "Forager" album (Pictures/Forager).
  - **Android 8–9:** into a folder the user picks.
- **What the saved copy contains:**
  - The photo's time is written as the Gallery's "date taken".
  - No location is ever written by Forager.
- **Permissions:** none added.
- **Consequence for the site:** a saved copy is outside Forager's private storage. Other apps with photo access can read it, and uninstalling Forager does not remove it.

### B2. Imported photos keep their own metadata when saved to the Gallery
- **The owner's ruling, verbatim:** "Imported photos taken outside the app are not within our scope. They can use a scrubbing app to remove it if they want it removed. All photos taken inside the app are scrubbed either way and that's our scope". Recorded in `RECORD.md` 2026-09-28-131 and in the plan.
- **What the code does:** the saved copy of an import is byte-for-byte the stored file. So if the import still had GPS (Android 8–9, see A3), the Gallery copy has it too.
- **Suggested:** "Saving a photo you took with Forager's camera gives a copy with no location. Saving a photo you imported gives an exact copy of what was imported. If that photo carried a location or other details, so does the copy. Use a metadata-removal app if you want them gone."

### B3. On-device journal backup and restore
Built at `fbdaf0c7`. Rulings are still being applied, record 2026-09-28-133.

- **What a backup holds.** It is one `.zip` file containing a database snapshot and the photo files:
  - the journal: entries, finds and their coordinates;
  - photos;
  - tracks with every GPS point;
  - waypoints;
  - offline-region details (not the map tiles);
  - planned trips, by the owner's ruling of 2026-09-29.
  - Settings are **not** included.
- **Where it goes:** a file the user saves where they choose, through Android's file picker. That could be the phone, an SD card, or a cloud folder if the user picks one.
  - **Forager never uploads it.** A cloud provider the user picks receives it through Android's picker, under that provider's terms.
- **It is not encrypted.** No encryption code exists in `data/backup/`. Anyone with the file can read the journal, photos and GPS tracks in it.
- **Scheduled backup:** daily, weekly or monthly, into a folder the user picks. It is **off by default**, and only the user can turn it on. Old backup files are never deleted by the app.
- **Restore** reads a backup file the user picks, either replacing the phone's journal or merging with it.
- **Suggested wording, core:** "You can back up your journal to a file and restore it later, on this phone or a new one. The backup contains your entries, finds and their locations, photos, recorded tracks with every GPS point, waypoints and planned trips. You choose where it is saved. Forager does not upload it, and it is not encrypted, so keep it somewhere you trust. If you save it to a cloud folder, that service holds a copy under its own terms. Scheduled backups are off unless you turn them on."

### B4. New permissions, pulled in by the scheduling library
Forager's own manifest is unchanged between `main` and `journal-redesign`, and the permission lists are identical. But the **merged** manifest adds permissions from a library.

- **Checked in:** the merged debug manifest built at `journal-redesign` (`app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml`), which lists:
  - `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`;
  - a `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that is internal to the app, so it needs no text.
- **Where they come from:**
  - The backup coder read the first three from WorkManager's library (`androidx.work` 2.12.0).
  - **`ACCESS_WIFI_STATE`'s source was not traced.** It may already be in `main`'s build, from MapLibre. I did not build `main` to compare. *Unverified.*
- **Suggested lines for the permissions list:**
  - "Run at startup, keep awake: used only so a scheduled backup you turned on can run at its time, including after a restart. With scheduled backup off, they do nothing."
  - "Network state: read by the scheduling and map libraries to know whether the phone is online. It is not used to send anything."

### B5. Deletion: "Nothing is left behind" stops being true
- **On the delete-data page:**
  - "Uninstalling removes everything … Nothing is left behind on the device."
  - "There is no cloud copy to survive the uninstall."
- **On the privacy page:** "What stays on your device … It is deleted when you uninstall Forager or clear its data."
- **Why they stop being true:** backup files (B3) and Gallery copies (B1) are ordinary files outside the app. They survive uninstalling and clearing data.
- **Suggested addition to delete-data:** "Uninstalling does not remove files you saved outside the app: backup files, and photos you saved to your Gallery. Delete those in your Files or Gallery app. Scheduled backups write a new file each time and never delete old ones, so check the folder you chose."
- **Keep:** `allowBackup="false"` is unchanged (`AndroidManifest.xml:134`). There is still no Google automatic backup. The new backup is one the user makes deliberately.

### B6. Wording that stays true, for reassurance
- **Recipients unchanged:** the network hosts in the code are the same as the site's table: iNaturalist, Open-Meteo and its archive, OSM, OpenTopoMap, USGS, MapLibre demo tiles, and the Worker. The only other https strings are a GPX namespace identifier (`GpxCodec.kt:66`), which is not a request, and a Play Store link for iNaturalist.
- **No new trackers:** no analytics, ads or account.
- **Crash traces:** still shared only by the user (`CrashLogPanel.kt:201`).
- **Background location:** none.

---

## C. Not decided yet (may change the text)

- **How a scheduled backup that fails tells the user:** the owner ruled "inform user … and offer to try again". With no screen open, that may mean a notification. If so, "Notifications" in the permissions list gains a use. Not ruled.
- **Restored offline regions:** listed as "not downloaded", with a re-download. A re-download is the same tile traffic as the original download, which the site already describes. No new text is expected.
- **Play Data safety form:** probably unchanged, because backup and export are user-initiated and on-device. That is the owner's call against Google's current definitions, and I have not checked it.

## D. The "Last updated" date
Both pages need a new date when edited. The delete-data page should also say that the backup feature now exists, next to its `allowBackup` paragraph, so the two are not confused.

---

# Addendum after the follow-ups (coder, dispatch 2026-09-28-200, L1)

**For the site agent.** Nothing above this line has been rewritten. Where this addendum disagrees with the text above, this addendum is later and wins.

**What this is.** The report above was written at `journal-redesign` `44aba8f6`. Since then the follow-ups landed: F1 (track delete, the Gallery date), F2 (scheduled-backup pruning, notifications), F3 (a kept track keeps its path, schema 17) and the rest of the Journal PR's work. The three source documents on `legal-drafts` were re-read sentence by sentence against the code at `journal-redesign` `f645e8f9` (merge base `acce3b99`), and every change is listed below as **was / now / why**. The file and line behind each claim, the claims removed, and what was kept but cannot be confirmed from code are in `docs/audits/2026-09-29-legal-docs-l1-completion-report.md`.

**How to read the two groups.** "Main" is `faf2f88f`, the app testers have today.
- **Part 1** changes are true of the app on `main` today. The live pages (`0688e4d`) are wrong or incomplete on them now, and they can go live without waiting for PR #140. Each was checked on `main` by reading the same code there, except where marked.
- **Part 2** changes arrive with PR #140 and must wait for its release.

**Publish from the `legal-drafts` files as they are after L1.** The banners still say DRAFT; remove them and set the "Last updated" date when you publish.

## Part 1. Already true in today's app (`main`): the live pages need these now

| # | Where | Was (live page `0688e4d`, or the first draft where the live page is silent) | Now | Why (file:line at `f645e8f9`) |
|---|---|---|---|---|
| 1 | Privacy, Notifications | "the sundown alerts (a turnaround warning and one at sunset, while a track is recording) ... computed on the device from the clock and your position" | **Removed.** The alerts cannot fire: nothing constructs a turnaround or sunset alert, and the decision class has no caller. The same on `main`. This is the most important item: the live page describes a feature the app does not have. | `TrackRecordingViewModel.kt:833` is the only `Alert(...)` built (off-track); `DecideSundownAlertUseCase` and `SundownPreferencesRepository` have no reader (`AppContainer.kt:258` is the only mention). Known since `docs/audits/2026-09-11-sundown-device-check-procedure.md:15` and `docs/audits/2026-09-12-pr95-pulse-before-it-ships.md:102`. Restore the sentence when the alerts are wired. |
| 2 | Privacy, "What stays on the device" | Crash traces listed under "the app's own private storage, readable by no other app" | Crash traces are set apart: written to the app's own folder in shared storage, on purpose reachable from the phone's file manager, the last ten kept, each with a timestamp, thread name, Android version number and stack trace | `CrashFileStore.kt:76-77` (`getExternalFilesDir(null)/crashes`), `:18` and `:70-72` (the file-manager reason), `:84` (ten), `:34-40` (contents) |
| 3 | Privacy, "What stays on the device"; Delete-data | Not mentioned | The last five searches (place, radius, month, filter, the species list returned) are kept so a search still answers offline. No delete button; the sixth replaces the oldest. | `RoomSearchCacheRepository.kt:71` (`MAX_CACHED_SEARCHES = 5`), `CachedSearchEntity.kt:35`, `CachedSearchDao.kt:34` and `:49` (the only delete is eviction) |
| 4 | Privacy, "Track export"; Delete-data | "The file contains the track's coordinates and times." | Also elevations, the waypoints dropped while recording (names and notes), and every stored point, kept or excluded, each marked. **And a copy of the file stays in the app's cache folder after sharing, and deleting the track does not remove it.** | `GpxCodec.kt:103-113`, `:164-176`; `TrackGpxExporter.kt:78` (`cacheDir/tracks`); nothing deletes it (`grep` for `deleteRecursively`/`cacheDir` finds only the capture sweep, `ForagerApplication.kt:97`) |
| 5 | Privacy, "Permissions", Location | "`AvailabilityViewModel.init` subscribes ... for the ViewModel's lifetime ... `AvailabilityScreen` fires locate-me once per launch" | The subscription follows the screen: acquired at `ON_START`, released at `ON_STOP`; the one-off locate-me is in the compact Maps scaffold | `AvailabilityViewModel.kt:207` and `:233`; `AndroidLocationTracker.kt:103` (one-second floor); `AvailabilityCompactScaffold.kt:432` |
| 6 | Delete-data, "There is nothing stored on a server" | "denying or revoking the location permission ends them; the app keeps working without it" | Denying location stops Forager reading your position but **does not stop the requests**: typed coordinates and moved maps still go out | `MainActivity.kt:491` (manual coordinates), `AvailabilityViewModel.kt:390`; tile URLs `Basemap.kt:148`, `:162`, `:171` |
| 7 | Delete-data, opening of "Delete individual items" | "the deletion takes effect immediately" | Waypoints, tracks, regions, entries, finds and photos show an Undo first and are removed when it closes; only planned trips go at once | `PendingDeleteSnackbar.kt:63-219`; `AvailabilityViewModel.kt:842` |
| 8 | Privacy, "Photos and location metadata", camera | "stripped of all embedded metadata ... only the orientation is put back" | Stripped by an allowlist. Kept: the orientation (re-written), the colour profile and two small technical headers (JFIF pixel density, Adobe colour transform). If the strip fails the photo is stored unchanged and the failure logged. **This corrects A2 above, which named the kept kinds as JFIF, ICC and orientation: the third kind is the Adobe header, and the orientation is written back afterwards.** | `PhotoMetadataScrub.kt:227-234` (`isKeptSegment`), `:65-72` (fail-open) |
| 9 | Privacy, imports | "the platform removes GPS tags from the copy the app reads" | Worded as what Forager does (stores the file as Android hands it over, does not ask for the original) and what Android normally does on 10 and later. Not confirmed on a device. | `FilePhotoStore.kt:121-123` (the byte copy, no `setRequireOriginal`), `:164-165` (the separate metadata read, API 29+) |
| 10 | Privacy, "The Cloudflare Worker" | Live: "no third-party tile vendor sees the requests your device makes to it", and "We also hold no plan that would let us export or retrieve per-request logs from Cloudflare". First draft: also a dated dashboard check, the plan tiers for Logpush and Logpull, a note on Cloudflare's default, and a history note | **Kept:** the Worker writes no log lines and `observability` is off, with the reason in `wrangler.toml`. **Removed:** the dashboard check, the plan-tier sentences, the note on Cloudflare's default and the history note, none of which code can confirm. **Added:** for tiles beyond the Worker's own archive the Worker asks `build.protomaps.com` itself and keeps a copy in R2. **Leave the two live sentences out when you publish:** the first is contradicted by that fetch, the second cannot be confirmed. | `server/pmtiles-worker/wrangler.toml:7-13`; `src/index.ts:161`, `:173-198` (the probe), `:214-243` (the fetch and the R2 write). That is the code in the repository; whether the deployed Worker is the same code is not visible from it. |
| 11 | Privacy, "Downloading an offline region" | "Once a region is downloaded, viewing the map inside it makes no tile requests at all." | **Removed.** Only a network capture can show it; the run record that touched it carries a caveat. | `docs/audits/2026-09-29-offline-regions-device-check-run-record.md:50` |
| 12 | Privacy, beta signup | "... not enough to recover the address it came from" | The hash is described; the clause is removed. The site's code uses a constant, public fallback salt when `RATE_LIMIT_SALT` is unset, and a secret's presence is not visible from a repository. | zynergy-site `functions/api/beta-signup.js:233-237` (read only, `0688e4d`) |
| 13 | Privacy, beta signup and Contact | A provenance note about where the section was copied from; "Verified as receiving mail on 2026-09-09." | **Removed** from the source: a note to editors, and a dated check of a mailbox. | not code |
| 14 | Privacy, Permissions | `ACCESS_WIFI_STATE`'s source "not traced" (B4 above) | **Resolved:** the MapLibre library adds both `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` (`13.5.0`, which `main` also pins), so both are true today. WorkManager and another library also declare network state. Inferred for `main`: `main` was not built. | `app/build/outputs/logs/manifest-merger-debug-report.txt` (built at the base); `gradle/libs.versions.toml:37` on both |
| 15 | Privacy, Permissions | "Notifications, vibrate, foreground service" without constants | Names `POST_NOTIFICATIONS`, `VIBRATE`, `FOREGROUND_SERVICE`; says the notification permission is asked when a recording starts | merged manifest `:60-70`; `MainActivity.kt:619` |
| 16 | Privacy and Delete-data, "Deleting your data" | "Everything Forager stores ... can be deleted ... item by item" | "Almost everything"; the two exceptions named (the last five searches, the cache copy of a shared GPX file) | items 3 and 4 |

## Part 2. Arrives with PR #140: wait for its release

| # | Where | Was | Now | Why (file:line at `f645e8f9`) |
|---|---|---|---|---|
| 17 | Delete-data "Recorded tracks"; Privacy "Deleting your data" | "the track and every GPS point in it" (true only once track delete exists; on `main` nothing calls the delete, per the record) | Track delete exists: a swipe in Records, or Delete on the track's details, with Undo, never while recording. **The owner's ruling that the line stays holds (the bullet stays); it now also says what a delete leaves behind (items 18 and 19).** | `RecordsLogbookList.kt:167-176`, `RecordDetailsSheet.kt:383-391`, `TrackExportPanel.kt:224` (`canBeDeleted`), `TrackRecordingViewModel.kt:719-733` (refuses a recording track), `MainActivity.kt:658` and `:670` |
| 18 | Delete-data (new section "Copies that stay until you delete the entry"); Privacy "Deleting your data" and "What stays on the device" | Not mentioned | A journal entry keeps a copy of what it noted about each track, waypoint, region, find and photo it lists, and the copy stays when the item is deleted: a waypoint's name and position; a region's name, centre and radius; a track's name, distance, duration and points; a find's date and your identification; a photo's attach date. Deleting the entry removes them. The copies are in backups. | `CartographyEntryEntity.kt:82-90`, `:98-105`, `:113-122`, and the find and photo rows; `RoomCartographyEntryRepository.kt:98-141`; `WaypointDao.kt:43`, `OfflineRegionDao.kt:33`, `MushroomLogDao.kt:93-96` (deletes touch none of the entry tables); `CartographyEntryDao.kt:135-143` (the entry delete removes them) |
| 19 | same | Not mentioned | **A deleted track's path is kept** in every entry that had recorded a decision about it (kept or left out): latitude and longitude of each point, in order, no times. F3 is in the base, so this is written as fact. | `DeleteTrackUseCase.kt:50`; `CartographyEntryDao.kt:56-60` (`copyTrackPathToEveryRef`, no `kept` condition); `TrackPathCodec.kt` (16 bytes a point); `Migrations.kt:991` and `ForagerDatabase.kt:168` (schema 17); `CartographyEntryDao.kt:63` and `:135-143` (removed with the entry); `JournalTables.kt:60-75` (in backups) |
| 20 | Privacy, "Scheduled backups"; Delete-data; Privacy "Deleting your data"; Beta README | "Forager never deletes old ones, so the folder keeps every backup" (B3 and B5 above) | **Corrected.** The first run waits one full interval. Forager keeps the newest five files a scheduled backup made and deletes older ones it made itself, from a list of the files it made, so it never touches a backup made by hand or anything else in the folder. Two limits: a lost list (clear data, reinstall) leaves earlier files, and a refused delete leaves the file. A "Try again" backup counts as scheduled. | `ScheduledBackup.kt:71-91` (initial delay), `BackupSchedule.kt:88-104`, `:106-176` (`keepNewestOnly`), `:37-40` (the list), `DataStoreBackupSchedulePreferences.kt:78-89`. **Cause of the first draft's error:** a stale comment, `ContentResolverBackupFiles.kt:19-20`, says "There is no delete here", and the method it sits above is at `:64`. |
| 21 | Privacy, Permissions, new "Notifications, for backups" | (C above: not ruled) | A "Backups" channel. A notification only when a scheduled backup did not finish (with "Try again") or left photos out; a clean run posts nothing. Tapping opens the Backup settings. **The permission is asked once**, the first time scheduled backups are turned on with a folder chosen (Android 13 and later). If it is declined, or notifications are off, the same words appear once in the app at the next launch, but only when a backup had something to report; declining alone shows nothing. | `AndroidBackupNotifier.kt:24`, `:55-93`, `:96-105`; `ScheduledBackupNotice.kt:8-21`, `:33-55`; `BackupViewModel.kt:301-330`; `BackupSection.kt:74-81`; `AvailabilityScreen.kt:1244-1250` |
| 22 | Privacy, Restore; Beta README | "Restore ... replaces ... or merges it in" | Adds: asks each time; does not add an offline region the phone already has (same name and radius, centre within one metre) | `RegionMatch.kt:9-31`; `RoomJournalBackup.kt:237` and `:319`; `BackupSection.kt` (the Replace, Merge, Cancel prompt) |
| 23 | Privacy, Journal backup; Beta README | What a backup holds: entries, finds, photos, tracks, waypoints, trips, region details; no settings | Also: **the file is a copy of the whole database, so it carries the last-five-searches list**; a restore does not bring the list back. A ruling on whether that is intended is open (see below). | `RoomJournalBackup.kt:149-176` (`takeSnapshot` copies `forager.db`; `DELETE FROM cached_searches WHERE 0` at `:162` deletes nothing and only takes the write lock); `JournalTables.kt:95-97` |
| 24 | Privacy, Save to Gallery | "Forager adds the photo's time to the Gallery's 'date taken' field" | Same, made exact: the time on record for the photo (captured time for Forager's camera; for an import the capture time read from the file when Android allows, otherwise the import moment), written at insert and again after publishing, only when the app has one; no location ever. The Android 8 and 9 route is "Save to folder", a place and name chosen in Android's file picker, not a folder. Imports are saved as imported, with the owner's words quoted. | `PhotoExporter.kt:69`, `:86-90`, `:72`, `:76`; `FilePhotoStore.kt:143`; `PhotoViewerDialog.kt:220-262`, `:524-528` |
| 25 | Privacy, Permissions | `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` "let a scheduled backup ... keep its schedule after the phone restarts" | Declared by the WorkManager library (`androidx.work` 2.12.0), not by Forager's manifest and not used by Forager's code; WorkManager is not a dependency on `main`, so these two are new with PR #140. The mechanism sentence is removed because it describes the library, not this repository. With scheduled backups off there is no scheduled job. | `app/build/outputs/logs/manifest-merger-debug-report.txt` (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`: `ADDED from androidx.work:work-runtime:2.12.0`); `gradle/libs.versions.toml:52-55`; `ScheduledBackup.kt:78-81` |
| 26 | Privacy, Permissions (opening) | The list without a check | Lists the merged manifest's permissions exactly: 13, plus one internal. Nothing was added or removed against the first draft's list; only the sources were traced. | merged manifest built with `:app:processDebugMainManifest` (`BUILD SUCCESSFUL`) |

## Corrections to the first part of this report

- **A2:** the kept segment kinds. See Part 1, item 8.
- **B3:** "Old backup files are never deleted by the app" is wrong. See Part 2, item 20.
- **B4:** the source of `ACCESS_WIFI_STATE` is now traced. See Part 1, item 14; `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` are WorkManager's, item 25.
- **B5:** "Scheduled backups write a new file each time and never delete old ones, so check the folder you chose" is superseded by item 20. The advice to check the folder stands.
- **C, "How a scheduled backup that fails tells the user ... Not ruled":** ruled. See item 21.
- **C, "Restored offline regions":** the phone that made the backup does not get duplicates. See item 22.
- **B3 "Suggested wording, core":** add "and the list of your last five searches" (item 23) if the site keeps a short form.

## Open for the owner

- **The sundown sentence on the live page** (item 1): the page describes a feature that cannot fire. Removing it does not need PR #140.
- **The backup carries the last-five-searches list** (item 23). The backup ruling (`docs/plans/journal-redesign.md`, "Photo export and on-device journal backup", ruling 3) lists what a backup holds and does not list it. The documents now disclose it. Dropping the table from the snapshot would be an app change.
- **The exported GPX file is never deleted from the cache** (item 4). Disclosed; a cleanup would be an app change.
- **Two live sentences to leave out** (item 10) and **the Worker's onward fetch** (item 10), which is also new disclosure.
- **`docs/beta/trip-report.md` and `device-report.md`** are outside L1's three files and were not read against the code. `trip-report.md` names a path, "Journal → Records → Recorded Tracks → the share button", that I did not confirm.
- **The notes for the site agent** (`prompts/preserved/2026-09-29-43.md`) say to leave delete-data's "Recorded tracks" line as it is. The source now keeps that bullet and adds what a delete leaves behind, because F3 made "every GPS point in it" incomplete (the F3 report's flag, `docs/audits/2026-09-29-kept-track-path-completion-report.md`, "Flags outside scope"). The planner should decide whether to amend the notes.
