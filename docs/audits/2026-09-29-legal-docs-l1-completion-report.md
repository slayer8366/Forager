# L1 report: the legal docs brought up to date (dispatch 2026-09-28-200)

**Coder session.** Configured model `claude-sonnet-5-5`: the session's system prompt names it ("The exact model ID is claude-sonnet-5-5"). The serving model was not read back from an API response inside the session.
**Branch and worktree:** `legal-drafts`, `/home/zynergy-labs/Zynergy/forager-wt/legal-drafts`.
**Base:** `legal-drafts` at `b6d6eca0` (the planner's first drafts), merged with `origin/journal-redesign` at `f645e8f9` by `git pull --no-rebase origin journal-redesign`, giving `acce3b99`. The merge had no conflict. (`git merge --no-rebase` is not an option of `git merge`, only of `git pull`; the pull form is the dispatch's own command.)
**Governing files, read in full:** `prompts/preserved/2026-09-29-41.md` (the dispatch); `prompts/preserved/2026-09-29-42.md` (the launch prompt); `prompts/preserved/2026-09-29-43.md` (the notes for the site agent, read for context); the three documents on `legal-drafts`; `docs/audits/2026-09-29-privacy-site-update-report.md`; `docs/plans/journal-redesign.md` from "Photo export and on-device journal backup (owner, 2026-09-29)" (line 842) to the end; `docs/audits/2026-09-29-kept-track-path-completion-report.md`; `CLAUDE.md`.

## Pre-registration (written and pushed before any code was read for a claim)

### Premises checked at the base, before observing

| Premise | Checked | Result |
|---|---|---|
| The worktree exists, is on `legal-drafts` at `b6d6eca0` and is clean | `git status`, `git log` in the worktree | holds |
| `origin/journal-redesign` merges cleanly | `git pull --no-rebase origin journal-redesign` | holds, no conflict |
| CLAUDE.md contains the line "a main session in this repository is the planner" (launch prompt, "Your role and rules") | `grep -n -i "planner\|main session\|roles and gates" CLAUDE.md`; the merge did not change CLAUDE.md (`git diff b6d6eca0 HEAD -- CLAUDE.md` is empty) | **wrong premise.** No such line exists at this base or on `journal-redesign`; the only hits for "planner" are the decayed-planner-picture entry (`CLAUDE.md:425-472`). The F3 report (`docs/audits/2026-09-29-kept-track-path-completion-report.md`, header) also mentions a trailing "Roles and gates" section that is not in this file. The launch prompt says the line "does not apply here", so nothing changes; recorded as a finding. |
| CLAUDE.md conflicts with the dispatch | read in full, 485 lines | no conflict found. The dispatch's rules (a claim names a file and line, or is unverified; push before you tidy; no fabricated capability) are CLAUDE.md's own. |
| D58's three phrases can be found | forager-forecast `docs/planning/DECISIONS.md` on `origin/main` (read with `git show`, no checkout changed) has the D58 row; the checkout at `review-protocol` does not carry it | holds. The phrases are in that row's Decision column. They are not written here or anywhere in this repository. |
| F3 (-195) has landed at my base | `git log`: `10fcd2f3` "Terminal 2026-09-28-205 closes -195 (F3)", `0df67ba1` "Merge ... into kept-track-path" | holds as history; **the code is read below before any track-path sentence is written** |

### Predictions and pass conditions

Each is a prediction about what the code at `acce3b99` will show. A prediction that fails is a finding, not an edit to the prediction.

| # | Dispatch item | Predicted at (file, from the reports; lines to be found) | Passes when |
|---|---|---|---|
| 1 | Track delete: a swipe in Records, Delete on a track's details, Undo, never while recording | `ui/records/RecordsTab.kt` (swipe), the track details sheet, `domain/DeleteTrackUseCase.kt:19-25` (the F3 report's line, before F3 changed it), wired at `AppContainer.kt` | a production caller exists for each of: swipe, details Delete, Undo, and a recording gate; each with a line |
| 2 | An entry keeps copies: waypoint and region coordinates today | `CartographyEntryEntity.kt` (ref and snapshot tables), `WaypointDecision`/`RegionDecision` snapshots | the snapshot fields and the delete path that leaves them are found with lines |
| 2b | After F3, a deleted track's path (lat/lng, no timestamps) stays in `cartography_entry_track_paths`, in every entry that has the track, until the entry is deleted, and is in backups | `Migrations.kt` (`MIGRATION_16_17`), `CartographyEntryEntity.kt`, `DeleteTrackUseCase.kt`, `CartographyEntryDao.deleteEntryAndRefs`, `JournalTables.kt` | each of the four halves of that sentence is read in code at the base. If any is not, the track-path sentence is written as a pending note and this report says so. |
| 2c | `delete-data.md:17` ("the track and every GPS point in it") is no longer the whole truth | F3 report "Flags outside scope" | the sentence is changed to say what is removed and what a kept copy leaves |
| 3 | Scheduled backups: the first run waits its interval; the app deletes older scheduled files it made itself, keeping the newest 5, and never touches manual backups or anything else in the folder | `data/backup/` (a scheduler and a worker) | read: the enqueue's initial delay; the prune's keep count; **how "made itself" is decided** (a file-name rule?); that a manual file and a foreign file are skipped. If the rule is by name, I will check whether a manual backup could match it, and report that rather than write "never" past what the code guarantees. |
| 3b | The first draft's "Forager never deletes old ones" is wrong | `privacy-policy.md:72`, `delete-data.md:34-35`, `privacy-policy.md:255`, `beta/README.md` | corrected in each place it appears (planner's outcome prediction 1) |
| 4 | A "Backups" notification channel; the permission is asked once when scheduled backups are first turned on; if declined, a message in the app at the next launch | `data/backup/` notifier, the Backup settings panel, a DataStore flag | channel id and name, the request site, the "asked once" flag and the next-launch message each found with a line. Which notifications exist (failure, partial, "saved with N photos left out") is read from the notifier, not from the plan. |
| 5 | A restore does not duplicate an offline region already on the phone | `RoomJournalBackup.kt` (regions) | the match rule is read (name, centre, radius); mentioned only in the sentences that already describe restore |
| 6 | Save to Gallery sets "date taken" from the photo's own time; never writes a location | `photo/PhotoExporter.kt` | the source of the time and the absence of any location column are read |
| 7 | The merged manifest's permissions match the policy's list | `:app:processDebugMainManifest`, then `app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml` | the built manifest is diffed against the policy's list. Expected extras over the first draft: none. Expected to find or not find: a foreground-service type or a permission WorkManager adds beyond the four the site report names. |
| 8 | Imports are saved as imported | `PhotoExporter.kt`, `FilePhotoStore.kt` | the export is a byte copy; no scrub for imports |
| 9 | Every other factual claim in the three documents | code at the base | each is confirmed with a file, corrected, or removed and listed. Claims about things outside this repository's code (a dashboard, a platform's plan limits, the site's own Functions) are listed separately, see below. |

**Outcome predictions, mine:**
1. At least one first-draft sentence besides the "never deletes old ones" one is wrong or unconfirmable at this base. I expect the Cloudflare and Contact sections' dated attestations ("Verified ... on 2026-09-09") to be the unconfirmable ones, because they describe a dashboard and a mailbox, not code.
2. The first draft's permissions list is complete against the merged manifest, and the manifest adds nothing beyond `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and one internal permission. If the manifest shows more, that is a finding.
3. "Never touches manual backups" holds only as far as a file-name rule allows. If the prune decides by name, I will say so rather than repeat the dispatch's "never".

**Not to be done:** no app code, no edit to `RECORD.md`, `docs/audits/README.md`, `CLAUDE.md`, `docs/plans/` or `prompts/`, nothing in zynergy-site, no merge into `journal-redesign`. One Gradle run, `:app:processDebugMainManifest`, after the machine checks.

---

## Resumed: what the code showed, and what changed

**Base for every code claim below:** `acce3b99` (`legal-drafts` `b6d6eca0` merged with `journal-redesign` `f645e8f9`). Line numbers are at that base. While I worked `origin/journal-redesign` moved to `47abc89d`; the four commits between are `RECORD.md`, `docs/audits/README.md`, one audit and two prompts (`git diff --stat f645e8f9 47abc89d`, no file under `app/`, `server/` or `gradle/`), so no code claim is affected and I did not merge them.

**What was run.** One Gradle task, `./gradlew --offline :app:processDebugMainManifest`: `BUILD SUCCESSFUL`. Before it, `pgrep -af '^\S*java .*([G]radleWrapperMain|[G]radleWorkerMain)'` printed nothing and `free -m` showed 2,620 MB available (the rule is 2.5 GB); the check and the launch were separate commands. No test was run and no app code was touched: this is a documents-only change, so "green" means nothing here. Everything else was reading.

### Verdicts on the pre-registration

| # | Prediction | Observed | Verdict |
|---|---|---|---|
| 1 | Track delete: swipe, details Delete, Undo, recording gate | Swipe `RecordsLogbookList.kt:167-176`; details `RecordDetailsSheet.kt:383-391`; gate `TrackExportPanel.kt:224` (`endedAtEpochMillis != null`) and the ViewModel refuses a recording track `TrackRecordingViewModel.kt:719-733`; Undo `:736`, `MainActivity.kt:658`, `:670`. Not on `main` (`git grep requestRemoveTrack faf2f88f` finds nothing). | confirmed |
| 2 | Waypoint and region coordinates kept | `CartographyEntryEntity.kt:98-105` (waypoint name, lat, lng), `:113-122` (region name, lat, lng, radius); written for **left-out** decisions too (`RoomCartographyEntryRepository.kt:117-141` maps every decision, kept or not). The deletes touch none of it (`WaypointDao.kt:43`, `OfflineRegionDao.kt:33`, `MapLibreOfflineMapRepository.kt:187-190`). Also kept: a find's date and identification (`CartographyEntryEntity.kt` find ref; `MushroomLogDao.kt:93-96` does not touch it) and a photo's attach date. | confirmed, and wider than the dispatch says |
| 2b | After F3, a deleted track's path stays | Table `cartography_entry_track_paths` (`Migrations.kt:991-1003`, `ForagerDatabase.kt:168`, `:229`); copy at delete `DeleteTrackUseCase.kt:50` through `CartographyEntryDao.kt:54-60` (no `kept`, no `isDraft` condition); 16 bytes a point, lat/lng, no times (`TrackPathCodec.kt`); removed with the entry `CartographyEntryDao.kt:135-143`; in backups `JournalTables.kt:60-75`. All four halves read. | confirmed. F3 is in the base; the sentence is written as fact. |
| 2c | `delete-data.md:17` no longer the whole truth | It said "the track and every GPS point in it". True of the track, incomplete now. | confirmed |
| 3 | The prune keeps 5, first run waits, manual files safe | `KEEP_NEWEST_SCHEDULED_BACKUPS = 5` (`BackupSchedule.kt:89`); `keepNewestOnly` `:136-161`; first run `setInitialDelay(days)` (`ScheduledBackup.kt:87-88`) and `apply` runs only when a setting changes (`BackupViewModel.kt:301-330`, and the comment at `ScheduledBackup.kt:71-74`). | confirmed |
| 3 (mine) | "the rule is by name, so a manual file could match it" (outcome prediction 3) | **Wrong, favourably.** The prune deletes only URIs in a list the scheduled job wrote (`BackupSchedule.kt:37-40`, `:97-104`, `DataStoreBackupSchedulePreferences.kt:78-89`); the default file name is the same for manual and scheduled backups (`backupFileName`, `:85`), so a name rule would have been unsafe and none exists. Limits found instead: the list lives in DataStore, so clearing data or reinstalling forgets it; a refused delete is logged and never retried (`:100-104`, `:153-160`). | prediction refuted; limits written into the documents |
| 3b | "Forager never deletes old ones" corrected | Gone from `privacy-policy.md` (Scheduled backups bullet and the Deleting section) and `delete-data.md`. **Where the first draft got it:** a stale comment, `ContentResolverBackupFiles.kt:19-20` ("There is no delete here, by design"), above the method `delete` at `:64` that `BackupSchedule.kt:154` calls. | confirmed |
| 4 | "Backups" channel; permission asked once; message at next launch if declined | Channel `AndroidBackupNotifier.kt:24`, `:96-105`; two notices only (`ScheduledBackupNotice.kt:11-21`), a clean run posts nothing (`:38-42`); asked once `BackupViewModel.kt:319-330` (flag set before the request), raised from `BackupSection.kt:74-81` on Android 13+ only when not granted (`:215-217`). **Premise refined:** the in-app message is not a reaction to declining. It is the same words a failed or photo-skipping run would have posted, shown once at launch when the notification could not be posted (`ScheduledBackupNotice.kt:49-53`, `AvailabilityScreen.kt:1244-1250`). Declining alone shows nothing. | confirmed with the refinement; the documents say so |
| 5 | Restore does not duplicate regions | `RegionMatch.kt:9-31` (same name, same radius, centres within 1 m); used in both modes `RoomJournalBackup.kt:237` (Replace), `:319` (Merge). | confirmed; mentioned in the policy and README, whose restore sentences exist; delete-data has none |
| 6 | Gallery date from the photo's own time | `PhotoExporter.kt:69` and again after publishing `:86-90`; the time is `LogPhoto.createdAtEpochMillis` (`FilePhotoStore.kt:143`: the file's EXIF capture time for an import when readable, else now). Only when the record has one. No location column anywhere in the file. **Whether the Gallery shows it is device-only** (the code's own comment at `:78-83` says its cause is unconfirmed). | confirmed as what Forager writes |
| 7 | Manifest: nothing beyond the known four plus one internal | Exactly 13 permissions plus one internal, as listed in the policy: `INTERNET`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `CAMERA`, `ACCESS_MEDIA_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `VIBRATE`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` (+ `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`). Sources from `manifest-merger-debug-report.txt`: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` from `androidx.work:work-runtime:2.12.0` (not a dependency on `main`, `gradle/libs.versions.toml:52`); `ACCESS_WIFI_STATE` from MapLibre `13.5.0` only; `ACCESS_NETWORK_STATE` from MapLibre, WorkManager and `media3-common`. Only `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `VIBRATE` were unnamed in the first draft. | confirmed; the site report's "not traced" is closed |
| 8 | Imports saved as imported | `PhotoExporter.kt:27-31` and `:76` (a byte copy); `FilePhotoStore.kt:132` scrubs captures only. | confirmed |
| Outcome 1 | At least one other draft sentence wrong or unconfirmable | Many; see the ledger and the removals. The largest is the **sundown alerts**: the first draft (and the live page) describe alerts that cannot fire. | confirmed, beyond expectation |
| Outcome 2 | Permissions list complete | See row 7. | confirmed |

### Premises that were wrong (findings)

1. **CLAUDE.md** has no line "a main session in this repository is the planner" and no "Roles and gates" section (`grep -n -i "planner\|main session\|roles and gates" CLAUDE.md`; unchanged by the merge). The launch prompt says the line does not apply, so nothing changed for me.
2. **`git merge --no-rebase`** is not a `git merge` option. I used the dispatch's own `git pull --no-rebase origin journal-redesign`.
3. **The dispatch's item 4** ("if declined, a message shows in the app at the next launch"): see row 4.
4. **The dispatch's item 3** ("never touches manual backups or anything else in the folder") holds by construction, with the two limits in row 3 (mine).
5. **The first draft's "the sundown alerts"** and **"Recorded tracks: ... every GPS point in it"** and **"Forager never deletes old ones"**: false or incomplete, see below.
6. **`AppContainer.kt:220`** says "a launch that never opens Backup never starts [WorkManager]". The merged manifest auto-initialises it through `androidx.startup` (`InitializationProvider` carries `androidx.work.WorkManagerInitializer`), so the comment reads as wrong. I did not rely on it: the documents say only that no job is scheduled when the schedule is off.

### Claim ledger: privacy-policy.md

| Claim as now written | Change | File:line |
|---|---|---|
| Banner: builds PR #140, date left for the owner | reworded (item 8) | n/a |
| An entry keeps a copy of what it noted (waypoint name and position; region name, centre, radius; track name, distance, duration, point count; find date and identification; photo attach date), which stays when the item is deleted, and goes with the entry | added | `CartographyEntryEntity.kt:82-90`, `:98-105`, `:113-122`, find/photo rows; `RoomCartographyEntryRepository.kt:98-141`; `WaypointDao.kt:43`; `OfflineRegionDao.kt:33`; `MushroomLogDao.kt:93-96`; `CartographyEntryDao.kt:135-143` |
| A deleted track's path (lat/lng, in order, no times) is saved into every entry that decided about the track, kept or left out, until the entry is deleted; in backups | added | `DeleteTrackUseCase.kt:50`; `CartographyEntryDao.kt:54-60`, `:63`, `:135-143`; `TrackPathCodec.kt`; `Migrations.kt:991`; `JournalTables.kt:60-75` |
| Last five searches kept, no clear button | added (true today) | `RoomSearchCacheRepository.kt:71`; `CachedSearchEntity.kt:35`; `CachedSearchDao.kt:34`, `:49` |
| Shared GPX file stays in the cache | added (true today) | `TrackGpxExporter.kt:78`; no sweep (`ForagerApplication.kt:97` sweeps captures only) |
| Crash traces: shared-storage folder, last ten, contents | corrected from "private, readable by no other app" (true today) | `CrashFileStore.kt:34-40`, `:70-77`, `:84`; `CrashLogPanel.kt:201` |
| `allowBackup="false"` at `:134` | line added | `app/src/main/AndroidManifest.xml:134` |
| Backup holds ... and the last-five-searches list; not encrypted; no settings | extended | `RoomJournalBackup.kt:149-176` (`:162` is a no-op that takes the lock); `JournalTables.kt:42-97`; `BackupArchive.kt:8-11`; no cipher code in `data/backup/`, `ui/backup/` (`grep -i cipher\|encrypt\|AES\|password`, no hits) |
| Scheduled: weekly default, off by default, first run after a full interval, newest five kept, older own files deleted from a recorded list, never manual or foreign files, two limits, "Try again" counts | corrected and extended | `BackupSchedule.kt:16-20`, `:37-40`, `:88-104`, `:132`, `:136-161`; `ScheduledBackup.kt:71-91`; `ContentResolverBackupFiles.kt:64-72` |
| Restore asks each time; skips a region the phone already has | extended | `BackupSection.kt` (Replace, Merge, Cancel prompt); `RegionMatch.kt:9-31`; `RoomJournalBackup.kt:237`, `:319` |
| GPX export contents (elevation, waypoints with names and notes, every stored point with a verdict) | extended (true today) | `GpxCodec.kt:103-113`, `:130-141`, `:164-176`; `TrackExportPanel.kt:266-283` |
| Save to Gallery: album `Pictures/Forager` on 10+, "Save to folder" (a file picker) on 8 and 9 | corrected ("a folder you pick" was a file picker) | `PhotoExporter.kt:133`; `PhotoViewerDialog.kt:220-262`, `:290-300` |
| Offline "viewing the map inside a region makes no tile requests at all" | **removed** | not confirmable in code; `docs/audits/2026-09-29-offline-regions-device-check-run-record.md:50` |
| Worker: no log lines, `observability` off, the reason; no retention setting | kept, code-only wording | `server/pmtiles-worker/wrangler.toml:7-13`; `src/index.ts`, `src/shared.ts` (no `console`/`log`: `grep`, exit 1) |
| Worker fetches overflow tiles from `build.protomaps.com` and keeps them in R2 | added (true of the code) | `src/index.ts:161`, `:173-198`, `:214-243` |
| Camera: allowlist keeps orientation, colour profile, two technical headers; fail-open | corrected | `PhotoMetadataScrub.kt:65-72`, `:227-234`; `FilePhotoStore.kt:132`; `CameraXCaptureSession.kt:151-156` |
| Imports: stored as Android hands them over; Android 10+ "normally" withholds GPS | reworded | `FilePhotoStore.kt:121-123`, `:164-176` |
| Gallery "date taken": the record's time; import capture time or import moment; only when present; no location | made exact | `PhotoExporter.kt:69`, `:86-90`; `FilePhotoStore.kt:143` |
| Owner's imports words quoted verbatim | added | ruling quoted from `docs/audits/2026-09-29-photo-export-completion-report.md:19` and `docs/plans/journal-redesign.md` ("Photo export: imports leave unchanged") |
| Permissions: the merged list; the location paragraph rewritten to the lifecycle; the media-location ask; notifications named and asked when a recording starts; Backups notifications; permission asked once; Wi-Fi/network sources; WorkManager's two | rewritten | merged manifest `:11-118`; `AvailabilityViewModel.kt:207`, `:233`; `AndroidLocationTracker.kt:103`; `AvailabilityCompactScaffold.kt:432`; `PhotoAcquisitionLaunchers.kt:32-45`; `MainActivity.kt:619`; `AndroidBackupNotifier.kt:24-105`; `ScheduledBackupNotice.kt:8-55`; `BackupViewModel.kt:319-330`; `BackupSection.kt:74-81`; `AvailabilityScreen.kt:1244-1250`; manifest-merger report |
| Sundown alerts sentence | **removed** (contradicted) | `TrackRecordingViewModel.kt:833` is the only `Alert(...)` built; `AndroidAlertDelivery.kt:55-60` delivers kinds nothing builds; `DecideSundownAlertUseCase.kt:46` has no caller; `AppContainer.kt:258` constructs the preferences and nothing reads them |
| Beta signup: facts against the site's code | verified, one clause removed, provenance note removed | zynergy-site `functions/api/beta-signup.js:90-99`, `:151-190`, `:230-272`; `db/schema.sql:26-50` (read only, `0688e4d`) |
| Deleting your data | rewritten: "almost everything", track delete mechanics, the entry copies, the two no-button items, scheduled-file limits | `RecordsLogbookList.kt:167-176`; `RecordDetailsSheet.kt:383-391`; items above |
| Contact: "Verified as receiving mail on 2026-09-09" | **removed** | not code |

### Claim ledger: delete-data.md

| Claim as now written | Change | File:line |
|---|---|---|
| Undo first for waypoint, track, region, entry, find, photo; a planned trip goes at once | corrected from "takes effect immediately" | `PendingDeleteSnackbar.kt:63-219`; `AvailabilityViewModel.kt:842`; `RoomPlannedTripRepository.kt:21-22` |
| Recorded tracks: swipe or Delete on details; never while recording; removes the track and its points; not the entry copy or a shared GPX file | confirmed and qualified | as row 1 above |
| Waypoints: "start and end waypoints a recording drops" | corrected from "vehicle and origin markers" (there is no vehicle designation) | `WaypointDesignation.kt:17-23` |
| Entry copies (per kind) and the saved path | new section | as the policy ledger |
| Shared GPX file and the last five searches remain | new | as the policy ledger |
| Scheduled backups: newest five kept, older own files deleted, recorded list, two limits | corrected from "never deletes old ones" | as the policy ledger |
| Photo delete removes the stored file | confirmed | `DeleteGalleryPhotoUseCase.kt:34`; `FilePhotoStore.kt:149-153` |
| "denying or revoking the location permission ends them" | **corrected** (false: typed coordinates and the map still go out) | `MainActivity.kt:491`; `AvailabilityViewModel.kt:390`; `Basemap.kt:148`, `:162`, `:171` |
| "no record of these requests" | corrected (the last five searches are recorded on the phone) | `RoomSearchCacheRepository.kt:71` |
| Beta signup paragraph | verified against the site's code | as the policy ledger |
| Banner | reworded (item 8) | n/a |

### Claim ledger: docs/beta/README.md

| Claim as now written | Change | File:line |
|---|---|---|
| Banner: builds PR #140 | reworded | n/a |
| Denying location does not stop the requests; last five searches kept | added | `MainActivity.kt:491`; `RoomSearchCacheRepository.kt:71` |
| "a downloaded offline region removes the tile half while you are inside it" | **removed** (same reason as the policy) | not confirmable in code |
| Camera scrub keeps orientation, colour profile and two headers; imports "normally" withheld on 10+; owner's words quoted; Gallery "date taken" | corrected | as the policy ledger |
| Restore asks each time and skips a region already present; scheduled first run after a full interval; newest five kept; "Backups" notification; permission asked once; in-app message | added | as the policy ledger |
| Backup holds waypoints, trips, region details and the last-five-searches list | extended | as the policy ledger |
| Deleting a track does not remove what an entry kept | added | as the policy ledger |
| "the app keeps a track point only when its timestamp lands on a whole second" | corrected to "counts", every point still stored | `NetworkProviderFix.kt:48` (`timestampEpochMillis % 1_000L != 0L`); `NetworkProviderFix.kt` doc: applied at the read seam, "Nothing stored is deleted" |
| Unchanged and read: the GPX export carries every stored point with a verdict; the 50 m live-fix gate and "Approaching" and "within" read the accuracy | confirmed | `GpxCodec.kt:103-141`; `LiveFixGate.kt:20-30`, `:68`; `DistanceUnit.kt:93-107` |
| The trip report has 24 questions, five in the location block | counted from the file, unchanged | `docs/beta/trip-report.md` |
| Measured figures (`3.7900925` m, 289 of 289 on one walk) | **not re-derived**; they are quoted with their scope (one phone, one walk) and their source document. The value also appears as a comment at `DistanceUnit.kt:107`. | `docs/audits/2026-09-07-fix-log-walk-findings.md` |

### Claims read and left unchanged (so the reader knows they were checked)

The recipient hosts and fields: `INaturalistClient.kt:14`, `OpenMeteoClient.kt:14`, `OpenMeteoArchiveClient.kt:18`, `Basemap.kt:148`, `:162`, `:171`, `BasemapStyles.kt:224`, `OfflineStyle.kt:18`, and the query fields at `INaturalistApi.kt:31-55`, `OpenMeteoApi.kt:30-47`, `OpenMeteoArchiveApi.kt:23-30`. The observation link `AvailabilityPureFunctions.kt:198`. No upload path: `grep '@POST\|@PUT\|@Multipart'` over `app/src/main` finds none. No analytics, crash-reporting or advertising dependency: `app/build.gradle.kts:481-519`. `ACCESS_BACKGROUND_LOCATION` absent and the recording service typed `location`: merged manifest `:223-226`. Gallery attach needs no permission: `PhotoAcquisitionLaunchers.kt:139` (`PickVisualMedia.ImageOnly`). Photos are stored in `filesDir/photos`: `FilePhotoStore.kt:191`. Planned-trip, waypoint, region and entry deletes: `RoomPlannedTripRepository.kt:21`, `RoomWaypointRepository.kt:34`, `MapLibreOfflineMapRepository.kt:187`, `RoomCartographyEntryRepository.kt:48`.

### Removed as unconfirmable or contradicted (the list the dispatch asks for)

| # | Removed | Why | Restores when |
|---|---|---|---|
| 1 | "the sundown alerts (a turnaround warning and one at sunset ...)" and "computed on the device" | contradicted: no alert can fire | the alerts are wired |
| 2 | "Verified in the Cloudflare dashboard on 2026-09-09 ..." | a dashboard observation, not code | the owner re-verifies and wants it back |
| 3 | The Logpush, Logpull, `httpRequestsAdaptive` and Workers-metrics paragraph | platform plan tiers, not code | never from code; the owner may keep it as a stated belief |
| 4 | "Cloudflare documents `observability.enabled` as defaulting to `true`" and "the only accurate one available while Cloudflare documents no figure" | claims about Cloudflare's documentation | same |
| 5 | The editorial history ("This section previously carried an open question ...") | a note about the draft, not the app | never |
| 6 | "Once a region is downloaded, viewing the map inside it makes no tile requests at all" (policy) and its README twin | needs a network capture | a capture is taken |
| 7 | "... not enough to recover the address it came from" | depends on a secret salt whose presence is not visible; the code falls back to a constant public salt (`beta-signup.js:233-237`) | the owner confirms `RATE_LIMIT_SALT` is set |
| 8 | "Nothing is ... shared with anyone else" (Worker paragraph) | contradicted by the overflow fetch; replaced by the specific statement | n/a |
| 9 | The provenance note under the beta signup section | an editor's note | never |
| 10 | "Verified as receiving mail on 2026-09-09." | a mailbox check, not code | the owner wants it back |
| 11 | "the deletion takes effect immediately" | false for six kinds of item | n/a |
| 12 | "denying or revoking the location permission ends them" | false | n/a |
| 13 | "Everything Forager stores ... can be deleted ... item by item" | false | n/a |
| 14 | "readable by no other app" as applied to crash traces | false | n/a |
| 15 | "vehicle ... markers" as a waypoint kind | no such kind | n/a |
| 16 | The mechanism sentence for `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` ("keep its schedule after the phone restarts") | describes the library | n/a |

### Statements kept although this repository's code cannot confirm them (for the planner to rule)

- **Distribution and scope:** "including its Google Play closed test"; the closed-test rules in `docs/beta/README.md` ("Joining the test", fourteen continuous days, a Google account).
- **About documents outside the repository:** "the Play Data safety declaration ... states that location is collected and shared"; "the document Play's Data safety declaration points at"; "that page is the URL given in the Play Data safety declaration".
- **Statements of practice or intent:** "Nothing is sold or used for advertising or profiling"; the beta list's "Nobody else receives it. It is not sold, not shared and not used for any mailing beyond the beta"; its retention ("until the beta ends or you ask"); "not directed at children". The site's code sends one notification email and has no other mailing (`beta-signup.js:151-190`), which is consistent but is not the commitment.
- **Platform behaviour:** app-private storage is unreadable by other apps; uninstalling removes an app's storage, including its app-specific folder on shared storage; Android 10+ withholds GPS from an app that has not asked for the original; a Gallery copy is visible to apps with photo access; Android 8 and 9 have no such step.
- **The beta list's outside facts:** that `support@` and `privacy@` are read; that the secrets (`RESEND_API_KEY`, `TURNSTILE_SECRET`, `RATE_LIMIT_SALT`) are set.
- **The README's rationale text:** the template-design narrative; the measured figures (see the ledger).

### What waited on F3

Nothing waits. F3 is in the base (`10fcd2f3`, `0df67ba1`); the track-path sentence is written as fact in the policy and delete-data, with the four halves read (row 2b). The only F3-related item still open is the F3 coder's own flag, which this dispatch closes: `delete-data.md:17`'s "every GPS point in it" is now qualified.

### Decisions I made

1. **Read the zynergy-site repository, read-only** (`git show origin/main:...` on the existing checkout; no fetch, checkout, edit or build), to verify the beta signup section's facts against the site's own code. The dispatch says "do not touch" the repository; I read "touch" as modify, as in "never touch `RECORD.md`". I changed nothing there. If that reading is wrong, the section's facts are unverified and should be treated as such.
2. **Where to draw "confirmable in code".** I confirmed against this repository's code, the merged manifest, the Worker's code in this repository and (read-only) the site's code. I removed what only a dashboard, a dated check, a platform's plan tier or Cloudflare's documentation could support, and what code contradicted. I kept, and listed above, statements of practice, statements about documents outside the repository, and platform behaviour. A stricter reading removes the kept list too; a looser one restores removals 2 to 4. Both are one edit.
3. **Added disclosures the dispatch did not list**, because the old text was false or a data category was missing: the last-five-searches list (and that a backup carries it), the crash-trace location, the GPX cache copy, the Worker's onward fetch, finds and photos in the entry copies, the Undo windows, the location-denial correction, the Save-to-folder wording. Each is a factual sentence with a citation and each can be struck without touching another.
4. **Mentioned the restore-region rule in the policy and the README**, since each already has a sentence describing restore (item 5); not in delete-data, which has none.
5. **Quoted the owner's imports words** in the policy and README (dispatch: "Quote the owner's verbatim words where the documents rely on a ruling"). No other ruling is relied on by a sentence that names it; the others are recorded here.
6. **Named a "Try again" backup as counted among the scheduled ones** because the code prunes it with them (`BackupSchedule.kt:132`).
7. **Marked items "true today"** only where I read the same code on `main` (`faf2f88f`) with `git grep`; the ones I did not read on `main` carry no marker.
8. **Wrote this report as a new file** rather than a "Resumed" section of an existing one, because none exists for this dispatch; the pre-registration above is the earlier part and is not rewritten. (The session's own instructions discourage report files; the dispatch requires one.)

### Flags outside scope

1. **The sundown alerts are not wired**, on `main` and here, and the live page says they exist. Known in `docs/audits/2026-09-11-sundown-device-check-procedure.md:15` and `2026-09-12-pr95-pulse-before-it-ships.md:102`; a live-page correction does not need PR #140.
2. **The backup carries `cached_searches`** because `takeSnapshot` copies the whole database file (`RoomJournalBackup.kt:149-176`), while `JournalTables.excluded` (`:95-97`) calls it "not journal data" and the owner's backup ruling 3 lists what a backup holds without it. Disclosed. Dropping the table from the snapshot would be an app change. I read this; I did not open a produced backup file to see the rows.
3. **The exported GPX file is never deleted from `cacheDir/tracks`** (`TrackGpxExporter.kt:78`). Disclosed. A cleanup would be an app change.
4. **The Worker fetches overflow tiles from `build.protomaps.com`** (`index.ts:161`), so the live page's "no third-party tile vendor sees the requests your device makes to it" is contradicted for that path. Whether the deployed Worker matches the repository is not visible.
5. **A stale comment** at `ContentResolverBackupFiles.kt:19-20` ("There is no delete here") is the likely origin of the first draft's error; and `AppContainer.kt:220`'s claim about WorkManager not starting (finding 6 above).
6. **`docs/beta/trip-report.md` and `device-report.md`** are outside L1's three files and were not read against code; `trip-report.md` names the path "Journal → Records → Recorded Tracks → the share button", unconfirmed, and carries its own privacy sentences.
7. **The notes for the site agent** (`prompts/preserved/2026-09-29-43.md`) say to leave delete-data's "Recorded tracks" line as it is. The source keeps that bullet and qualifies it, per dispatch item 2 and the F3 coder's flag. The planner should amend the notes.
8. **Site code:** if `RATE_LIMIT_SALT` is unset the rate limiter uses a constant salt in the public source (`beta-signup.js:233-237`), and an IPv4 address hashed with a known salt can be recovered by enumeration. Not visible from a repository whether it is set.
9. **Nothing was run on a device**, so every "the app does X" in the documents is a reading of code, not an observation, including the Gallery date (`PhotoExporter.kt:78-83` says its own cause is unconfirmed).

### D58 and the push record

Before each push `/tmp/l1_d58_check.sh` scanned the added lines of `git diff acce3b99`, untracked files and the messages of commits after `acce3b99` for the three phrases in the D58 row's Decision column (found by `git show origin/main:docs/planning/DECISIONS.md` in forager-forecast, no checkout changed). The script builds the patterns from fragments so that it does not spell them, and a positive control (a temp file holding one phrase) returned one hit before I trusted a clean result. The phrases are not written here or in any file in this repository. Pushes: `d359819d` (pre-registration and merge), `fa7ad25e`, `4275f8dc`, and the final commit below.
