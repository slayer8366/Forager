# Forager engineering audit: main at `137b1427c`, 2026-11-10

**What this is.** A read-only audit of `main`, run on 2026-11-10 by a planner session with nine reader helpers, at the owner's request: "let's perform a full audit of the Forager repo main branch". The owner's scope answers: "App code", "Security and privacy"; kind "Engineering audit". It was delivered as a message and is filed here unchanged in substance on the owner's word, 2026-11-10: "Record, add to bugs list, bring step 1 questions." The follow-up plan is `docs/plans/main-audit-2026-11-10-follow-up.md`.

**Held out of this file.** This repository is public. One server finding (M18), three low server and build items, the list of identifiers found in the tree, and three lines flagged for a home-location check are not printed here. Each is marked "held" where it stood. They wait on the owner's ruling about whether unfixed security detail goes into a public file.

**A claim about the past.** Every file and line below was read at `137b1427c`. `pre-main` (`947f1be2e`) differed from it in three files only, two workflow files and the release runbook, so the app findings held there on the day.

Scope: app code plus security and privacy. Read-only: nothing was written, committed, pushed, built, run or triggered, so every finding comes from reading code, not from a device or a test run.

There is one high finding, about 25 medium, and no critical. The areas least covered are `ui/availability` (62 of 175 files read) and `ui/log` (51 of about 125).

Status tags:
- **[V]** I read the cited lines myself and the mechanism holds.
- **[R]** A reader confirmed it; I did not re-read it.
- **[I]** Inferred: the mechanism is in the code, but timing, reachability or effect was not established.

## Baseline

- HEAD equals `origin/main` at `137b1427c`, 0 ahead and 0 behind.
- App main: 892 Kotlin files, about 117,300 lines. Database version 24.
- The Restricted Object was not met and not read by me or any reader.

## High

**H1. A failed sightings fetch replaces the whole Maps tab with error text. [V]**
- `ui/availability/AvailabilityCompactMapUi.kt:372-385` shows a spinner while loading, the error if the fetch failed, and the map only otherwise.
- The Maps tab's bottom bar and rail (`:725`) and search bar (`:567`) are composed only in the map branch. The Scaffold's own bar draws nothing on Maps (`AvailabilityCompactScaffold.kt:889-890`).
- The error is set at `AvailabilitySearchRuns.kt:211-216`, for example after a search with no signal.
- Nothing on the error screen can change the keys that re-run the fetch (`AvailabilityScreen.kt:507`), so there is no retry.
- Back exits the app, or toggles the exit prompt while navigating (`AvailabilityScreenBackHandlers.kt:126-128`, `:152-159`).
- During every sightings load the map is also replaced by the spinner, so a recording or navigation view disappears for the length of the request.
- Not checked: device behaviour, and whether a relaunch returns to the same error. `MapChromeOverMapTest.kt` references these fields; I did not read what it asserts.

## Medium

**Recording and location**
- **M1. Recording started with Location off never gets a fix. [V]** `location/AndroidLocationTracker.kt:112-116` registers only providers enabled at that moment, and `onProviderEnabled` does nothing (`:108`).
- **M2. A failed point write drops the batch. [V]** `service/TrackRecordingService.kt:412-422` clears `pendingPoints` before the write and only logs on failure.
- **M3. Sticky restart neither resumes nor stops. [V]** `onStartCommand` (`:119-155`) returns `START_STICKY`, and a null intent matches no branch. The track is closed later by the abandoned-track sweep (`AppContainer.kt:478-487`).
- **M4. One-shot location has no accuracy limit. [V]** `location/AndroidLocationProvider.kt:21-39` takes the first GPS or network fix and drops its accuracy. The reader traced it into a find's `foundAt` (`ui/log/MushroomLogCaptureLocation.kt:122-140`) [R].
- **M5. The sundown line stays on the first evening. [V]** `domain/SundownWatch.kt:275-277` replaces any sunset more than 12 hours later with the held one, and nothing releases it. The reader traced the display to "Dark since" the first dusk [R].
- **M6. Way-home placement can jump to a switchback's later leg and stay there. [V]** `domain/wayhome/WayHomeProgress.kt:150-160` takes the earliest node within 25 m, then 50 m, from the previous index forward. Its doc comment says the later leg "is never chosen". The reader traced the effect to the needle aim and announced turns; the distance figure uses its own search.

**Journal and data loss**
- **M7. Typing during a photo attach is overwritten. [V]** `ui/log/MushroomLogEntryPhotos.kt:62-65` writes back a copy taken before the photo persisted; typing is applied outside the lock (`MushroomLogViewModel.kt:544`). `isSavingPhoto` has no UI reader.
- **M8. A failed find save still shows the report page. [V]** `ui/log/JournalTab.kt:556` flips unconditionally; failure only sets a toast (`MushroomLogViewModel.kt:581-584`).
- **M9. Save-and-leave on a Cartography entry can lose the edit. [V]** `CartographyEntryEditScreen.kt:344`, `:375` call `onSave(); onBack()`. The close clears the editor at once (`CartographyEntryExits.kt:64-88`) while the save is asynchronous (`:117-145`).
- **M10. Sharing a track as GPX can crash. [V]** The write at `ui/track/TrackExportPanel.kt:282-285` has no try/catch, and `export/TrackGpxExporter.kt:56-71` can throw.
- **M11. Backing up over an existing file destroys it on failure or cancel. [V]** `data/backup/ContentResolverBackupFiles.kt:33-34` opens with `"wt"` before the backup runs, and `ui/backup/BackupViewModel.kt` (about `:257-259`, `:287-291`) deletes the file on failure or photo cancel. The ruling at `BackupViewModel.kt:222-227` covers a file this run created; a pre-existing file is not addressed, so this is a question for you. Not confirmed: whether the picker can return an existing document.
- **M12. Planned-trip save throws instead of failing. [V]** `domain/SavePlannedTripUseCase.kt:31-34` uses `require` inside a `Result` function; the `fold` at `ui/availability/AvailabilityPlannedTrips.kt:46-48` cannot catch it. I did not check for a scope exception handler.

**Search, download and map**
- **M13. An older search result can overwrite a newer one. [V in part]** `AvailabilityViewModel.kt:676-704` starts new fetches without cancelling the old. The reader found no fetch compares its inputs before writing [R]. Whether responses arrive out of order in practice is [I].
- **M14. "Download again" can start a second concurrent download. [V]** No guard at `OfflineRegionsList.kt:249-250` or `AvailabilityViewModelOfflineMaps.kt:40-50`; both downloads share one status field. Not checked: whether the repository serialises downloads.
- **M15. A failed style load leaves a blank map with only a log line. [V]** `ui/map/OwnTrailFallback.kt:93-98` falls back only for Trail Map online. The comment at `MapStyleReload.kt:64-69` records what to tell the user as a decision not yet made.
- **M16. The centre-pin picker confirms the last settled position. [V]** `ui/map/CentrePinLocationPicker.kt:169` writes the centre only on camera idle, and `:250` confirms that value. That OK stays tappable mid-move is [I].
- **M17. `MapView` start, stop, low-memory and save-state calls are never forwarded. [V]** `ui/map/MapLifecycle.kt:23-35` forwards resume and pause only. The effect (no SDK-side retry on reconnect, no cache trim) is [I]; the SDK was not opened.

**Server and privacy**
- **M18. Held.** One finding about the map server, verified by the planner in the code. Held out of this public file (see the top).
- **M19. Unrounded coordinates in a release log line. [V]** `ui/map/MapDataRefresh.kt:104` logs the search region at info level. Release builds are not minified (`app/build.gradle.kts:307`), so the line ships [R]. The policy says "a coarse map position" (`docs/legal/privacy-policy.md:223-224`) [R].
- **M20. Full-precision position goes to iNaturalist and Open-Meteo. [V]** `AvailabilityViewModel.kt:579`, `:589`. The policy discloses latitude and longitude [R]. Rounding would be a product decision.

**Release process**
- **M21. Eight commits went straight to main. [V]** `769c4bacf` through `137b1427c`, all `[skip ci]`, workflow files only. Main is 31 commits ahead of pre-main and 4 behind. CLAUDE.md: "Main changes only by a promotion from pre-main."
- **M22. Production can publish a bundle not built from main. [V]** With `version_code` set, `play-production.yml:259-300` checks only that an uploaded bundle has that number. The commit quotes you: "re-upload the same build to Play in the correct place". CLAUDE.md also says "Every Play build comes from main." This is yours to weigh.
- **M23. The `release` environment now allows pre-main. [I]** Each branch guard lives in the dispatched ref's own workflow file, so a changed copy on pre-main could reach the signing secrets once approved. Environment settings are not visible from the repository.
- **M24. Unverified downloads in jobs that hold the keystore and Play key. [R]** `scripts/setup-android-sdk.sh:14` has no checksum, there is no `distributionSha256Sum`, and `pip install` has no hashes (`play-production.yml:267`).

## Questions for the owner

- **M11:** should a backup that fails or is cancelled delete a file that existed before the run?
- **M15:** what should the user be told when a style fails to load?
- **M21 and M22:** are the direct workflow commits and the `version_code` path standing exceptions to the promotion rule?
- **Sundown alerts:** `SundownWatch.kt:86` records them as "Once per recording each". M5 concerns only the stale line, not that ruling.

## Low

**Reader-confirmed or verified**
- Unlogged failures or fallbacks:
  - `ui/log/MushroomLogGallery.kt:139-141`
  - `ui/availability/AvailabilityOfflineMaps.kt:254`
  - `ui/map/NightImageApply.kt:36-43`
  - `ui/map/RouteHomeLayers.kt:322-329`
  - `map/OfflineReliefTileStore.kt:92-94`
  - `domain/mapdata/StoredTiles.kt:62-63`
- "Download again" reports success when moving references failed (`AvailabilityOfflineMaps.kt:214-217`).
- Null accuracy is treated as good on the way home (`domain/wayhome/WayHomeWatch.kt:166`) but not on way-to (`WayToEnds.kt:229`).
- Default-locale number formatting: `domain/model/DistanceUnit.kt:74,77`, `ui/log/LogEntryDetailScreen.kt:194`, `LogEntryReportScreen.kt:188`, `CentrePinLocationPicker.kt:249`.
- `GeoDistance.boundingRegion` rounds to nearest where its doc says up (`domain/GeoDistance.kt:140`).
- Restore reads the archive with no size bound (`data/backup/BackupArchive.kt:143`).
- GPX import:
  - The DOCTYPE guard scans the first 4 KB only (`domain/GpxImportReader.kt:44`).
  - `GpxImportActivity` accepts `file:` URIs.
  - Exported `MainActivity` trusts outcome extras (`MainActivity.kt:289-318`).
- Service: `onDestroy` cancels without a flush (`TrackRecordingService.kt:158-170`); `recordingJob` is unsynchronised (`:92`).
- Photos: a failed copy leaves a partial file (`photo/FilePhotoStore.kt:121-124`); the scrub fails open (`:133`).
- Activity leak through `TrackRecordingViewModelFactory.kt:41`.
- Worker: two low items, held (see the top).
- Build:
  - No dependency verification metadata.
  - `material3` is on an alpha version.
  - One build item, held (see the top).
- The startup sweep loads every point of every track (`data/repository/RoomTrackRepository.kt:39-41`).
- Every overlay source is rebuilt on each new breadcrumb point (`ui/map/MapDataRefresh.kt:60-64`).
- CI: two workflows use `ubuntu-latest`; CODEOWNERS does not cover `.github/workflows`.
- I verified `formatDistanceWithAccuracy` (`DistanceUnit.kt:138`) can throw beyond its coarsest step but is not reachable today; its only caller gets a 50 m-gated fix.

**Inferred**
- Two quick taps on Record can start two tracks (`ui/track/TrackRecordingViewModel.kt:459-520`).
- A photo can attach to whichever Cartography entry is open when it finishes persisting (`ui/log/CartographyEntryEdits.kt:145-154`).
- A cleared search comes back when the location fix arrives (`AvailabilityViewModel.kt:584-609`).
- One unreadable row fails a whole list (`data/repository/RoomMushroomLogRepository.kt:56-63`).
- An integer overflow in `domain/mapdata/MvtDecoder.kt` (about `:84`) may crash, not raise the format error.
- Polar-edge "sun stays down" (`domain/ComputeSundownCountdownUseCase.kt:78-87`).
- Track points with tied timestamps after GPX import (`data/local/TrackDao.kt:52`, `domain/ImportGpxUseCase.kt:73`).
- In-progress input held in plain `remember` is lost on non-rotation recreation.
- Deleting a waypoint that is a track's origin does not clear the reference (`data/local/WaypointDao.kt:53-54`).

**Files over the 800-line split mark [V]**
- `AvailabilityCompactScaffold.kt`: 1,440
- `CameraXCaptureSession.kt`: 945
- `JournalTab.kt`: 919
- `TrackRecordingViewModel.kt`: 860
- `SightingsMap.kt`: 801
- `Migrations.kt` (1,266) is exempt under the skill.

**Dead code [R]**
- `FindRouteDataForPointUseCase`
- `PlotRouteUseCase.invoke(stops)`
- `GpxCodec.decode`, which has no DOCTYPE refusal and must not be wired to user files as it stands
- `parseForecastCells`
- `turnWords(turn)`
- `joinedTrackHome`
- `LogSyncState.Uploading/Uploaded/Failed`
- Several unused properties

## Public text and identifiers [R unless marked]

**Stale or contradicted**
- `README.md:93-97` names a `release.yml` that is not on main.
- `README.md:149` lists a glyph server the app no longer uses.
- `docs/beta/README.md:42-62` is out of date on photo metadata, on backup and import, and on USGS tiles.
- There is no LICENSE file at the repository root. [V]

**Not said in the policy**
- Every request's User-Agent carries the app version, Android release, application id and a support address (`net/AppUserAgent.kt`).

**Identifiers in the public tree**
- Six kinds of identifier were found in tracked files. The kinds and places are held (see the top). No value was printed in the audit.

**Home location**
- The patterns file matched no tracked file. [V]
- `scan.py` reported no coordinate-pair, MGRS, grep or tile hits. [V]
- It also reported 15 single-axis latitude hits and 1 longitude hit in about 13 files, which I did not open to judge.
- Readers flagged three literals for the owner's check. Their places are held (see the top).

## Clean checks

**App code**
- No Android or vendor UI imports under `domain/`, apart from the NGA MGRS library, which is justified in its comment. [V]
- No `GlobalScope`, `runBlocking`, TODO or FIXME. [V]
- The release database throws on a missing migration; destructive fallback is debug only (`data/local/ForagerDatabase.kt:253-255`). [V]
- The migration chain 3 to 24 matches the schema. [R]
- Restore validates on a scratch copy before touching live data, and zip-slip is guarded. [R]

**Platform [R]**
- Nine permissions, all used. No background location, wake lock or storage permission.
- Android backup is off.
- No cleartext traffic and no weakened TLS.
- PendingIntents are immutable.
- Release signing comes from the environment.

**Privacy [R]**
- No analytics, crash or ad SDK.
- Outbound hosts match the policy's list: iNaturalist, two Open-Meteo hosts, OpenStreetMap, OpenTopoMap and the project's Worker.
- No device or user id is sent.

**CI and secrets [R]**
- All actions are pinned by commit SHA.
- Every workflow has `contents: read`.
- There are no risky triggers.
- No key shapes in the tree or in history.
- No GPS tags in any tracked image.

**Server [R]**
- One Worker and one R2 bucket.
- No user records, and observability is off.

## Coverage and gaps

| Area | Read in full |
|---|---|
| Persistence, migrations, backup | All, except schema JSONs 4 to 23 |
| Domain subpackages | 114 of 114 |
| Domain root | 174 of 174 |
| Platform, manifest, build | Complete |
| Network and server | Complete; some line numbers approximate |
| `ui/map`, `motion`, `theme` | 139 of 139 (about 62 from stripped printouts) |
| `ui/availability` | 62 of 175 |
| `ui/log`, `track`, `route` and others | 51 of about 125 |
| Privacy, CI, secrets | Workflows and policy in full; other files sampled |

- **Not read at all:** the navigation card composables, most camera UI, `ui/credits`, `ui/adaptive`, and the deletion paths behind `docs/legal/delete-data.md`.
- **Not visible from the repository:** GitHub environment and branch-protection settings, the deployed Worker and its dashboard rules, and Play Console state.
- **Out of scope as agreed:** tests and CI as an area of their own, and the docs and record.

## Split report: one function per file

Counts come from a line-pattern script, not a parser. Plain grep gave 1,685 top-level functions against the script's 1,672.

- 323 of 892 files (36%) hold two or more top-level functions, 1,508 functions between them.
- Splitting those to one function per file adds 1,185 files, taking the tree from 892 to about 2,077.
- 92 files already hold exactly one top-level function and no type. 405 files have no top-level function.

| Package | Files | Splittable | New files |
|---|---|---|---|
| `ui/availability` | 175 | 91 | 322 |
| `ui/map` | 117 | 73 | 302 |
| `ui/log` | 90 | 49 | 211 |
| `domain` root | 174 | 30 | 89 |
| `data/repository` | 29 | 9 | 42 |
| `ui/route` | 10 | 6 | 29 |
| `photo` | 14 | 8 | 27 |
| `ui/motion` | 12 | 9 | 24 |
| `map` | 16 | 8 | 23 |
| Everything else | 255 | 40 | 116 |

Most functions in one file: `CartographyEntryCard.kt` 26, `RouteCardPlacement.kt` 21, `EntryContentsPanel.kt` 19, `CameraCapabilities.kt` 17, `AvailabilityPureFunctions.kt` 17, `BasemapStyles.kt` 16.

Limits on that goal:
- **Member functions are not counted.** About 2,077 functions sit inside classes and interfaces. The largest are `RoutePlottingViewModel` 42, `TrackRecordingViewModel` 38, `AvailabilityViewModel` 36, `NavigationView` 35 and `CartographyEntryDao` 34. A DAO cannot be split by function.
- **Visibility would widen.** 323 top-level functions and 320 top-level values are `private`; each would need to become `internal` or find a new home.
- **The modular-code skill names things that stay together.** It lists composition order, Back handler order, effect order and map memories, and says splitting those "goes to the owner as a stop-and-ask". It also lists "Moving code just to move it" as a case where the skill does not apply. I report this against your goal and have not applied it over it.
- **Order-bound files are not identified.** I have not judged which of the 323 files are order-bound; that needs a file-by-file read.

**The owner's ruling on this section, 2026-11-10:** "No split." The one-function-per-file split is not done.
