# Failure fixes, the first hygiene build: code and tests written, not compiled

Dispatch 2026-09-28-658 (RECORD intent -658; preserved at `prompts/preserved/2026-10-07-09.md` on
branch `records-after-173`). The owner's choices are RECORD -655. Written 2026-10-07 (UTC) on branch
`failure-fixes`, cut from `origin/main` at `aa79f25a`, the base the dispatch names (checked against
the remote before starting). No PR.

**Nothing here has been compiled or run.** The dispatch held Gradle for the owner's "go", queued
behind the back-by build. The only things run were the three scripts in section 5, with bash and
python, which do not build the app. Every Kotlin claim below is from reading the code.

## In short, for the owner

- A GPS reading that makes a watch throw no longer ends a recording; the point is still saved.
- A refused foreground start stops the service instead of crashing it.
- Cancelling a backup or restore (the screen going away) is no longer reported as "failed", and no
  longer deletes the file.
- The crash log viewer shows a plain line for a log it cannot read, instead of crashing.
- An offline region that drops out of the list is logged with the reason; a failed read of restored
  regions is shown as a partial list, with a message.
- A GPX share without the full GPS record says so before the share sheet.
- A dozen silent fallbacks now log.
- Seven hand-copied pieces are now one each.
- The camera captures at the phone's standard size (about 12 MP); thumbnails decode to the size of
  the cell they sit in.
- The unused serif font (444 KB in every APK) and an unreachable search icon are gone.
- Three checks that could not fail now can, each shown failing on a planted mistake.
- Three new strings need the owner's yes (below), and five questions need the owner's answer
  (the stops).

## Premises that were wrong or needed narrowing

1. **R7, "after a sticky restart."** A sticky restart hands the service a null intent, and
   `onStartCommand` has no branch for one (`when (intent?.action)`, `TrackRecordingService.kt:102` at
   `aa79f25a`), so a sticky restart never reaches `startForeground`. The refusal is still real for
   other starts: from Android 12 a foreground start the system disallows throws
   `ForegroundServiceStartNotAllowedException` (an `IllegalStateException`), and from Android 14 a
   location-type start from the background throws `SecurityException`. Both are now caught, logged
   and the service stops. Inferred from platform documentation, not seen on a phone.
2. **D8, "can't move the 1 m match."** Strictly it can: the shared haversine reads every distance
   longer by a factor of 1.0000014 (6,371,008.8 / 6,371,000), which at 1 m is 1.4 micrometres. A pair
   of centres between 0.9999986 m and 1 m apart would change from "same" to "not same". In practice
   a restore compares a region with its own copy in a backup, whose centre is the same stored number
   (distance 0), and `RegionMatchTest`'s cases are 0.5 m and 2 m. Recorded in `RegionMatch.kt`'s doc.
3. **"Logs through `ErrorLog` where the layer already uses it."** The service, the location
   adapters, the photo code, the crash panel, the export panel and the DataStore repositories all use
   raw `Log.w` today, so they log through `Log.w`. `ErrorLog` is used where it already was
   (`AvailabilityViewModel`, `BackupViewModel`) and in `INaturalistMushroomRepository` (D6), whose
   tests are plain JVM, where `Log.w` throws. To give that repository the container's `ErrorLog`,
   `AppContainer.errorLog` moved to the top of the container (properties initialise in order; it was
   declared after the repository).
4. **G1's header claim.** The header said checks 3 and 4 pass. Check 3 fails at `aa79f25a`, partly on
   real `tween(` calls (`MarkerFanOutState.kt:81`, `RestoreLoadingPage.kt:91-97`) and partly because
   its pattern also matches `GeoDistance.metersBetween(` (three lines in `ApproximatePositionHud.kt`
   and `NavigationHud.kt`). Check 1 fails too, on `Color(0xFFFFD600)` in `ui/log/CameraOverlay.kt:68`.
   The header now says so. Check 3's pattern is not fixed here (not asked); see the stops.
5. **F6's coordinate touches.** The three tagged rows (Sundown alerts, Dark under trees, Off-track
   reminder) already had real coordinate-touch tests (`SundownSettingsTest`,
   `OffTrackReminderSettingsTest`). New coverage is for the five untagged rows.
6. **F3.** Removing the dead branch leaves `SearchEntryBar`'s `onUseCurrentLocation` parameter
   unread (it only fed the removed icon). Removing it changes `AvailabilityCompactScaffold.kt`'s two
   calls, a motion Part 1 file, so it is left, marked, and listed in the stops.
7. **Photo size.** One existing test is predicted to break: `DecodedPhotoTest`'s
   "a rotate-90 tagged photo renders turned" derives its sizes from the fixed sample size the owner's
   choice replaces. Not edited; see the stops.

## The work

### 1. Failure paths

| Item | What changed | Where | Test |
|---|---|---|---|
| R1 | Each watch call on a fix (return watch, sundown watch, kept point) goes through one guard that catches, logs and goes on, as the sundown tick does. The point is still sampled and saved. | `service/TrackRecordingService.kt` (`guardWatch`), `service/RecordingWatches.kt` | `TrackRecordingServiceWatchFailureTest`: three tests, one per guarded call, each feeding twenty fixes through the real service with that call throwing every time; asserts twenty points stored and twenty throws |
| R7 | `startForeground` refusal caught (`IllegalStateException`, `SecurityException`), logged with the track, service stops. | `TrackRecordingService.startForegroundWithLocationType` | None: Robolectric cannot make `startForeground` refuse. Device-only |
| D1 | Backup and restore rethrow `CancellationException`, as `:397` already did. | `ui/backup/BackupViewModel.kt` | Two in `BackupViewModelTest`, through a `ViewModelStore` `clear()` mid-run: no file deleted, no prompt, no message, nothing logged as failed |
| J1 | One guarded read (`readCrashLog`), logs and shows a plain line. | `ui/crash/CrashLogPanel.kt` | `CrashLogPanelTest` (new): a listed file deleted before the tap shows the line; a readable one shows its text; the read itself |
| M1 | The complete-region branch logs (no metadata, or unreadable with the reason); `readRegionMetadata` says which key was missing or which value was bad. `toRegionMetadata` kept as a wrapper. | `map/OfflineRegionReconciliation.kt`, `map/MapLibreOfflineRegionMetadata.kt` | Two in `OfflineRegionReconciliationTest`, three in `MapLibreOfflineRegionMetadataTest` |
| M2 | A failed read of restored regions sets a partial-list message instead of clearing it. | `ui/availability/AvailabilityViewModel.kt` | One in `AvailabilityViewModelOfflineMapsTest` (the fake can now fail that read) |
| R9 | A Toast before the share sheet when the full record could not be read. | `ui/track/TrackExportPanel.kt` | `TrackExportPartialTest` (new): real touch on the row's Share; toast text then chooser; no toast when the record reads |
| R2 | Unknown or missing recording mode logged. | `TrackRecordingService.onStartCommand` | None |
| R3 | `SecurityException` in the one-shot request logged. | `location/AndroidLocationProvider.kt` | None |
| J4 | EXIF read failure and a malformed EXIF date logged; null result kept. | `photo/FilePhotoStore.kt` | None |
| J5 | Orientation read failure logged. | `photo/PhotoMetadataScrub.kt` | None |
| D2 | Unknown stored theme name logged; fallback unchanged; class comment corrected. | `data/repository/DataStoreAppThemePreferenceRepository.kt` | None |
| D6 | Malformed positions (dropped) and dates (kept, no date) counted in one log line per page, apart from recorded exclusions. | `data/repository/INaturalistMushroomRepository.kt`, `AppContainer.kt` | Two in `INaturalistMushroomRepositoryObservationMappingTest` |
| L4 | Both Toast paths log. | `ui/availability/AvailabilityPureFunctions.kt` | None |

The R1 test needs a seam: the real watches are concrete classes the container builds. The service
now hands each fix to a `RecordingWatches` (an open class over the container's watches, used as-is in
production), and a test sets the service's `internal var watchesFor` before the start command. This is
a design choice made here; see the stops.

### 2. Hand-copied logic, merged (no behaviour change intended)

| Item | One copy now | Callers |
|---|---|---|
| R5 | `location/LocationPermission.kt` `hasLocationPermission(context)`, carrying the service's documented reason (the confirmed foreground-service crash) | `MainActivity` (2 calls), the three location adapters, the service, `SightingsMap` |
| R6 | `GeoDistance.metersPerDegree(lat)`, the same expression each copy used, so results are bit-identical | `TrackSelfJoin`, `RouteHome`, `OffTrackJudge` |
| D8 | `RegionMatch.sameOfflineRegion` calls `GeoDistance.metersBetween` | `RoomJournalBackup` |
| D4 | `data/remote/ApiClients.kt`; the three clients are one-liners; the "only place" comments corrected | `AppContainer` |
| D7 | `countOrZero` in `EntryReferenceCounts.kt`, with the four named helpers moved out of `MainActivity.kt` and kept as one-line delegates (their tests name them) | `MainActivity` |
| F6 | `SettingsCheckboxRow`, `ExplainedSettingsCheckbox`, `SettingsRadioRow`; same modifiers in the same order, tags unchanged | the eight rows in `AvailabilitySettingsUi.kt` |
| F9 | `ForagerDatabase.ALL_MIGRATIONS` made `internal` | the 13 migration test files. Four of them registered only the tail of the chain; they now register all of it, which gives Room the same single path from their start version |

New test for F6: `SettingsRowsTouchTest`, real touches at 5%, 50% and 95% across each of the five
untagged rows on the real screen, reading the callback and its value.

### 3. Photo size

- **Capture:** `STANDARD_PHOTO_SIZE = Size(4032, 3024)` in `photo/CameraXCaptureSession.kt`, doc quoting
  the owner ("Phone's standard size (Recommended)"), used as a CameraX `ResolutionStrategy` bound with
  `FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER`, default 4:3 aspect kept. Which size a given phone then
  picks is device-only.
- **Thumbnails:** `DecodedPhoto` decodes once, at the first size it is laid out at, through
  `decodeThumbnail`, at `thumbnailSampleSize`: the largest power of two that keeps the photo's
  shorter edge at least the cell's longer edge (the cell crops), then never past the viewer's
  `VIEWER_MAX_EDGE_PX` on the long edge. A 12 MP photo in a 300 px cell decodes at 504 by 378 (it was
  1008 by 756 at the old fixed 4). `ThumbnailSampleSizeTest` (new, plain JVM) checks seven cases worked
  by hand.
- **Imports** keep their files and go through the same two decoders; nothing else changed for them.

### 4. Dead weight

Removed: `LongFormSerifTextStyle`, the Noto Serif family, `res/font/notoserif_regular.ttf`, and in
`SpeciesSearchControls` the location-icon branch, `showLocationTrailingIcon` and `onUseCurrentLocation`,
with its KDoc rewritten for its one caller. Kept with a one-line note: `Spacing.xl`/`xxl`,
`CompassCoordinateTextStyle`, `WindowWidthClass`'s MEDIUM/EXPANDED and the 840 dp breakpoint.

### 5. Blind checks, each shown biting (these were run)

- **G1/F1, `verify-design-tokens.sh` check 2.** Pattern now `com.zynergylabs.forager.app.ui.theme.`.
  With an import of `Bark` planted in a new file under `ui/log`: the fixed script's check 2 listed the
  plant among 75 findings; the original script, run on the same tree, passed check 2. Plant removed,
  74 findings, matching the scout's count. Header corrected (premise 4). The 74 are listed at the end
  of this report and were not changed.
- **G2/F10, `compare-test-baseline.sh`.** `set -euo pipefail`; the `|| true` sites kept; exit 3 with
  "PARSE FAILED" and the reason when a results file cannot be read, a file holds no `<testsuite>`, or
  the suites' own `failures=`+`errors=` do not equal the failing `<testcase>` elements parsed. Planted
  inputs, original then fixed: a failing testcase with its attributes in another order, exit 0 "no
  failures" then exit 3; an unreadable XML, exit 0 then exit 3; an empty XML beside a real failure,
  exit 1 then exit 3. Controls: a normal failure, exit 1 both; 487 real result files from the
  gpx-import run, exit 0 both.
- **F11, `measure-night-inversion.py`.** Prints "Sampled N of M tiles; K skipped" beside the summary,
  and exits 1 with no summary when nothing was sampled. Run with the network replaced by a fake that
  failed every third fetch: 8 "FETCH FAILED" rows and "Sampled 16 of 24 tiles; 8 skipped". The original
  printed the same 8 rows and no count.

### 6. Stale comments

F4 (`AvailabilityScreen.kt`'s two links to the removed `AvailabilitySearchTopBar`) and F5 (two "no
consumer" comments in `AppContainer.kt`; both consumers checked in `MainActivity`). CLAUDE.md not
edited: its `returnWalkingTime` entry is a past-tense account and `SundownWatch.kt` now calls that
function (scout R11), which a later reader could take as current. For the owner.

R8 (comments only): `LocationSampler.shouldAccept` and `OffTrackJudge.next` now state their rule
for a fix with no accuracy; `LiveFixGate` already did. No rule changed.

## Proposed user-facing strings (need the owner's yes before the build)

- **J1**, the crash log detail view, in place of the log's text:
  "Couldn't read this crash report. Go back and open it again. If it still won't open, it may have been removed."
- **M2**, above the Offline maps list when restored regions could not be read:
  "Couldn't read the regions restored from your backup, so only downloaded regions are listed. Close Offline maps and open it again to retry."
- **R9**, a Toast just before the share sheet when the full GPS record could not be read:
  "Couldn't add the full GPS record, so this file has the track as shown but not every raw point. Share again to try including it."

Each is one constant (`CRASH_LOG_UNREADABLE_TEXT`, `RESTORED_REGIONS_UNREADABLE_MESSAGE`,
`PARTIAL_GPX_EXPORT_MESSAGE`), and each test reads the constant, so a reworded string changes in one
place.

## Stops, for the owner

1. **D10, a corrupt settings file.** Today: none of the ten DataStore files has a corruption handler
   (`ReplaceFileCorruptionHandler`, 0 uses). A corrupt file makes every read and write of that store
   fail; every repository wraps the read in `runCatchingCancellable`, so it becomes a failed `Result`,
   and each caller falls back as it already does (for example the theme and units reads log and
   leave the screen's default, `AvailabilityViewModel.kt` around `:1409` and `:1459`). It never
   heals: the file stays corrupt and every launch fails the same way. Options: (a) leave it; (b) a
   handler that resets that one file to defaults and logs it, so the user's choices in that store
   are lost once and the app works again; (c) the same, plus a one-time message that the settings in
   that group were reset.
2. **D3, four ways of reading back a stored name.** Today: an unknown theme name falls back silently
   (now logged, D2); an unknown units name throws inside `runCatchingCancellable`, so the read fails
   and the screen keeps its default; an unknown backup frequency `error(...)`s the same way, so the
   whole backup schedule read fails and the section starts from "off"; an unknown camera grid name is
   a named failure. Unchanged here. Options: one shared decode that either falls back with a log (the
   setting resets to its default) or fails the read (the screen keeps its default and the stored
   value stays), applied to all four.
3. **R8, a fix with no accuracy, for the off-track alert.** The sampler and the live-fix gate let it
   through as "not reported"; the off-track judge treats it as 0 m, judging it as if exact, so the
   line is 40 m with no widening. Unchanged here; changing it changes when the off-track alert fires.
4. **R7, what the screen shows when the service is refused.** The service now stops instead of
   crashing, but the recording screen, which already started the track, still shows "recording" with
   nothing recording, as the permission branch does today. That is a wrong picture. Options: leave
   it as the permission branch has it; or tell the screen, which needs a way back from the service to
   the ViewModel that does not exist today.
5. **`DecodedPhotoTest`'s rotation test.** Predicted to fail: it composes `DecodedPhoto` with no size
   and expects the 40 by 20 fixture at a fixed quarter (10 by 5 dp). Decoded to its cell, the fixture is
   not sampled at all. The constant it reads (`DECODE_SAMPLE_SIZE`) is kept, unused, only so the test
   compiles. Proposed rewrite: give each photo a fixed cell and assert the decoded bitmap's turn
   through `decodeThumbnail` directly, then remove the constant. Not touched until the planner says.
6. **Smaller ones.** (a) The R1 test seam (`RecordingWatches` and the service's `internal var
   watchesFor`): a test-only hook in production code, chosen as the narrowest one available; confirm
   or name another. (b) `SearchEntryBar`'s now-unread `onUseCurrentLocation`: remove it with
   `AvailabilityCompactScaffold.kt` after motion Part 1? (c) `verify-design-tokens.sh` check 3 matches
   `metersBetween(`; fix its pattern (`\btween\(`) or leave it? (d) R9 is a Toast, which shows over the
   share sheet for a few seconds; a dialog ("Share anyway?") would make the person act on it, and is
   a behaviour change.

## Collisions

- **back-by** also changes `TrackRecordingService.kt`, `AppContainer.kt`, `MainActivity.kt` and
  `AvailabilityScreen.kt`. Its fix loop adds `container.backByWatch.onFix(candidate, fix.provider)`
  beside the two watches guarded here. After merging main with back-by in it, that call needs the
  same guard: a `backByOnFix` on `RecordingWatches` and a `guardWatch` line, plus a fourth test in
  `TrackRecordingServiceWatchFailureTest`. Not done yet (back-by is not merged).
- **motion-part-1** also changes `CrashLogPanel.kt`, `TrackExportPanel.kt`, `AvailabilitySearchUi.kt`
  and `AvailabilitySettingsUi.kt`. The lines differ (motion swaps `clickable` and `IconButton` for its
  own; these change a read, the export function, `SpeciesSearchControls` and the settings rows), but
  textual conflicts are possible when the second branch merges. None of motion's own files named in
  the dispatch was touched.

## What is unverified because nothing was compiled

- That any of it compiles. Particular risks, each checked by reading only: the `internal var` of an
  internal type on the public service class; the generic local functions in `readRegionMetadata`;
  `ApiClients.create`'s generic `Class<T>`; the CameraX 1.6.2 `ResolutionSelector` calls; the
  `snapshotFlow` decode in `DecodedPhoto`.
- That the new tests pass, and that each fails with the fix reverted. No revert check was run. The
  plan for them: R1 (remove each `guardWatch`, expect 0 points stored), D1 (remove each rethrow,
  expect the deleted file and the write-failed prompt), J1 (read with `file.readText()` again, expect
  `FileNotFoundException`), M1 (remove the warn, expect 0 warnings), M2 (set the message back to
  `null`, expect `null`), R9 (remove the Toast, expect no toast), D6 (remove the log call, expect an
  empty list), thumbnails (the old fixed 4, expect the hand-worked values to fail). Each from a saved
  copy, with the build log checked for compile errors first.
- That the merged copies change nothing: the existing tests of each caller are the evidence, and
  they have not run.
- `SettingsRowsTouchTest` finds each row by its label plus a click action; if a label also appears on
  another clickable node on that screen, the finder will report more than one.
- The capture size a real phone picks, the thumbnails' look on a phone, and R7's refusal: device-only.
- The full suite was not run, so its count before and after is not known.

## Appendix: the 74 design-token findings (check 2), not changed

By token: Spacing 45, `navigationBarContainerColor` 15, Bark 9, Cream 3, `SurfaceContainerLight` 1,
`SurfaceContainerDark` 1. Paths are under `app/src/main/java/com/zynergylabs/forager/app/`, line
numbers as of this branch.

```
ui/map/MapBubble.kt:70:import com.zynergylabs.forager.app.ui.theme.Bark
ui/map/MapBubble.kt:72:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/map/SightingsMap.kt:38:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/map/JournalEntriesChip.kt:29:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/map/JournalEntriesChip.kt:30:import com.zynergylabs.forager.app.ui.theme.Bark
ui/map/JournalEntriesChip.kt:32:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/map/MapLayersSheet.kt:67:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/map/MapLayersSheet.kt:82:import com.zynergylabs.forager.app.ui.theme.Bark
ui/map/MapLayersSheet.kt:84:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/map/MapChrome.kt:60:import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
ui/map/MapChrome.kt:61:import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
ui/map/MapChrome.kt:62:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/map/MapChrome.kt:64:import com.zynergylabs.forager.app.ui.theme.Bark
ui/map/MapChrome.kt:65:import com.zynergylabs.forager.app.ui.theme.Cream
ui/map/MapChrome.kt:68:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/map/CentrePinLocationPicker.kt:42:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/map/CentrePinLocationPicker.kt:44:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityNavigationUi.kt:52:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/NavigationHud.kt:66:import com.zynergylabs.forager.app.ui.theme.Bark
ui/availability/NavigationHud.kt:68:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityMapControlsUi.kt:94:import com.zynergylabs.forager.app.ui.theme.Bark
ui/availability/AvailabilityMapControlsUi.kt:95:import com.zynergylabs.forager.app.ui.theme.Cream
ui/availability/AvailabilityMapControlsUi.kt:97:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilitySearchUi.kt:108:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilitySearchUi.kt:118:import com.zynergylabs.forager.app.ui.theme.Bark
ui/availability/AvailabilitySearchUi.kt:120:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityOfflineMapsUi.kt:31:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilityOfflineMapsUi.kt:99:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityResultsUi.kt:76:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityScreen.kt:14:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilityScreen.kt:341:import com.zynergylabs.forager.app.ui.theme.Bark
ui/availability/AvailabilityScreen.kt:342:import com.zynergylabs.forager.app.ui.theme.Cream
ui/availability/AvailabilityScreen.kt:344:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/ReturnToRoutePill.kt:25:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/ReturnToRoutePill.kt:26:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilityCompactMapUi.kt:29:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilityCompactMapUi.kt:144:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityTripsWaypointsUi.kt:49:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityMapOverlaysUi.kt:77:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilityMapOverlaysUi.kt:85:import com.zynergylabs.forager.app.ui.theme.Bark
ui/availability/AvailabilityMapOverlaysUi.kt:87:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilitySettingsUi.kt:77:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityCompactScaffold.kt:57:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/availability/AvailabilityCompactScaffold.kt:168:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/availability/AvailabilityMapIconCluster.kt:118:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/track/ImportGpxButton.kt:17:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/track/TrackExportPanel.kt:43:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/backup/RestoreLoadingPage.kt:43:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/backup/BackupSection.kt:32:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/crash/CrashLogPanel.kt:37:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/JournalTab.kt:52:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/CartographyScreen.kt:45:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/EntriesToolbar.kt:16:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/CartographyEntryCard.kt:37:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/RecordsLogbookList.kt:44:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/JournalShortWindow.kt:41:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/LogEntryDetailScreen.kt:53:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/PullPhotoPickerScreen.kt:25:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/LogEntryReportScreen.kt:54:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/CartographyEntryListScreen.kt:32:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/RecordsTab.kt:33:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/CartographyEntryReportScreen.kt:69:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/log/CartographyEntryReportScreen.kt:93:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/InAppCameraDialog.kt:47:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/EntriesAlbum.kt:49:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/PhotoViewerDialog.kt:73:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/CameraBands.kt:27:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/SidewaysEntryCard.kt:32:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/CartographyEntryEditScreen.kt:60:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/RecordDetailsSheet.kt:53:import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
ui/log/RecordDetailsSheet.kt:63:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/EntriesDrafts.kt:26:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/RecordsFilterChips.kt:28:import com.zynergylabs.forager.app.ui.theme.Spacing
ui/log/FindsGalleryScreen.kt:40:import com.zynergylabs.forager.app.ui.theme.Spacing
```

## Amendment 1 (RECORD -660), written 2026-10-07 after the owner's answers; supersedes the strings and stops above

Still nothing compiled or run, apart from the design-token script (bash). Each item is the owner's
answer as the planner relayed it.

1. **Strings, "Shorter set".** J1 "Couldn't read this crash report. Go back and open it again."; M2
   "Couldn't read the regions from your backup. Showing downloaded regions only. Reopen Offline maps to
   try again."; R9 "Couldn't add the full GPS record. This file has the track only. Share again to
   retry." In the three constants; the tests read the constants.
2. **D10, "Reset it and say so once".** Every one of the ten settings files is now built through
   `data/repository/SettingsDataStore.kt` `settingsDataStore(...)`, which installs DataStore's
   `ReplaceFileCorruptionHandler`: a file that will not parse is replaced with empty preferences (that
   file only), logged with its name and cause, and `SettingsResetNotice` (`domain/`, held by
   `AppContainer` and handed to all ten) is raised. **Where the message shows (proposal):** a long Toast
   from `MainActivity`, the first time the screen is started after the reset, then cleared; the reset is
   found on the first read of that file, which the screen's ViewModels make as they start. Text:
   "Some settings couldn't be read and were reset. Check Settings." Test: `SettingsResetAndDecodeTest`
   writes a truncated proto to the theme file and reads it through the real repository (the read
   works, reads as the default, the notice is pending, the file takes writes again); an intact file
   raises nothing. The Toast itself is not tested: no test composes `MainActivity`.
3. **D3, "Fall back and log".** `decodeStoredName` in the same file: nothing stored is the default,
   silently; an unknown name is logged and is the default. Used by theme (default: the legacy choice,
   else System Default, as before), units (IMPERIAL; also the legacy distance-unit key), backup
   frequency (WEEKLY) and camera grid (Off). Tests: an unknown backup frequency and an unknown unit
   system read through the real repositories, and the decode's log. **Existing test this breaks, not
   edited:** `DataStoreCameraGridModeRepositoryTest`, "a stored name this build does not know is a
   failed read, not a silent Off" asserts `gridModeFromStored("Crosshair").isFailure`; it is now a
   success (Off). `gridModeFromStored` keeps its `Result` type so the test compiles. Proposed
   replacement: assert Off and the logged line.
4. **R7, "Fix it, say why".** `domain/RecordingHalts.kt` (held by `AppContainer`): the service reports
   a refused foreground start, a start with no location permission, and permission lost mid-walk, ends
   that track, then stops itself. `TrackRecordingViewModel` (built with
   `container.recordingHalts.latest` in `MainActivity`) shows not recording for its own track and sets
   the message the screen already shows as a Toast. Refused: the owner's "Recording couldn't start.
   Open Forager and tap Record again." **Permission (proposal):** "Recording stopped because Forager
   can't use your location. Allow location for Forager in your phone's Settings, then tap Record
   again." Test: `TrackRecordingHaltTest`, the permission case end to end (the ViewModel's Record, then
   the real service started with permission withdrawn: not recording, the message, the track ended);
   the refused case by reporting to the same container object (Robolectric cannot refuse a foreground
   start); a report for another track changes nothing.
5. **R8:** comments only, unchanged.
6. **DecodedPhotoTest, rewritten.** The rotation test now reads `decodeThumbnail` with exact sizes for
   a 10 px and a 40 px cell (10×20 and 20×10, then 20×40 and 40×20), and a sized composable test checks
   the thumbnail loads in its 10 dp cell. `DECODE_SAMPLE_SIZE` removed. Unverified: the other tests in
   that class write non-image bytes and rely on Robolectric faking a 100×100 decode; the new bounds read
   first is assumed to see the same fake size.
7. **Design tokens, "Piece by piece".** Of the 74, eleven are in files this sweep changes. Fixed (3):
   `AvailabilityScreen.kt`'s `Bark` and `Cream` imports (both unused), and `AvailabilitySearchUi.kt`'s
   `Bark`, now through `ui/theme/MapChromeContentColor.kt` `mapChromeContentColor(isDark)` (same
   colours), excluded in check 2 as `MapIconBarAccent` is. Check 2 now lists 71. **Not fixed (8), for
   the owner:** `Spacing` in `AvailabilitySearchUi.kt`, `AvailabilityScreen.kt`,
   `AvailabilitySettingsUi.kt`, `TrackExportPanel.kt`, `CrashLogPanel.kt` (the spacing scale, imported by
   design; no call-site fix exists), and `navigationBarContainerColor` in `SightingsMap.kt`,
   `AvailabilitySearchUi.kt`, `AvailabilityScreen.kt` (the theme's own role accessor, "the one token" of
   the owner's C1 ruling; replacing it would undo that). Both look like candidates for check 2's
   exclusion list rather than code changes.
8. **Check 3, pattern fixed.** Now `(^|[^[:alnum:]_])tween\(`. With a planted file holding one
   `tween(300)` and one `metersBetween(`: the fixed check listed the `tween` and not the other; the old
   pattern listed both, plus the three real `metersBetween(` lines. Plant removed. Its findings, not
   fixed (both files are motion Part 1's): `ui/map/fanout/MarkerFanOutState.kt:81`, and
   `ui/backup/RestoreLoadingPage.kt:91`, `:92`, `:94`, `:97`.

The R1 seam is accepted, R9 stays a Toast, `SearchEntryBar`'s unused parameter waits for motion Part 1
(the planner). CLAUDE.md is not edited.

## Amendment 2 (RECORD -661), written 2026-10-07; supersedes Amendment 1's items 1, 2, 3 and 7 where they differ

Still nothing compiled or run apart from the design-token script.

1. **R7 permission string:** used as written ("Use it as written"); its KDoc now says so.
2. **The settings-reset notice is a snackbar** ("Snackbar with 'Settings' button"). The `MainActivity`
   Toast is removed. `MainActivity` collects `container.settingsResetNotice.pending` and hands it to
   `AvailabilityScreen` (`settingsResetNoticePending`, `onSettingsResetNoticeShown`), which clears it as
   it shows it, on the shared snackbar host's own scope, with `SnackbarDuration.Indefinite`, a dismiss
   button, and the action "Settings" (`SETTINGS_RESET_ACTION_LABEL`). "Settings" opens Tools, then
   Settings, through a new `openSettingsOnlyRequest` (the backup notification's request also scrolls to
   Backup, which this should not). Test: `SettingsResetSnackbarTest` composes the real
   `AvailabilityScreen` with a real `SettingsResetNotice`: shown, cleared as shown, still there 30 s
   later, a real touch on "Settings" closes it and Settings shows; dismissed, it does not return;
   nothing owed, nothing shown. Not reached: `MainActivity`'s two lines of wiring (no test composes it),
   and the dismiss button is found by M3's default description "Dismiss" (assumed, not checked).
3. **`DataStoreCameraGridModeRepositoryTest` rewritten:** an unknown name is Off, with exactly one
   warning, "Unknown stored camera grid mode 'Crosshair'; using Off, the default.", read from
   `ShadowLog`; a known or absent name logs nothing.
4. **Check 2 allows `Spacing` (the design scale) and `navigationBarContainerColor` (the owner's C1
   ruling), one line each.** Rerun: check 2 lists **11** (was 74, then 71). They are `Bark` in
   `MapBubble.kt`, `JournalEntriesChip.kt`, `MapLayersSheet.kt`, `MapChrome.kt`, `NavigationHud.kt`,
   `AvailabilityMapControlsUi.kt`, `AvailabilityMapOverlaysUi.kt`; `Cream` in `MapChrome.kt` and
   `AvailabilityMapControlsUi.kt`; `SurfaceContainerDark`/`Light` in `MapChrome.kt`. None is in a file
   this sweep changes. Checks 1 and 3 unchanged (1 and 5 findings).

## Build and test (RECORD -664), 2026-10-07; supersedes "What is unverified because nothing was compiled"

Branch merged with `origin/main` at `340bdc4a` (a CLAUDE.md note) first; back-by not merged. Every
Gradle run under `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`, Gradle heap
1536m, Kotlin daemon 2g, Java temp `~/.cache/forager-test-tmp`; free space checked before each run
(lowest 5.8 GB); `./gradlew --stop` at the end, no daemon left running.

**Compile.** Main sources compiled first time. Test sources had one error,
`BackupViewModelTest.kt:656` (a fully qualified `initializer`, which is an extension and needs an
import); fixed (`b4582a2f`).

**New and changed tests, targeted run:** 137 tests in 15 classes, 2 failures, both mine, both fixed
(`ef5c5882`), then green:
- `TrackExportPartialTest`: "Can't toast on a thread that has not called Looper.prepare()". Under
  the compose test harness the share coroutine resumed from its IO hop on a thread with no Looper.
  The Toast now posts through `withContext(Dispatchers.Main)`. On a phone the scope is the main
  thread already (inferred); the explicit hop costs nothing there.
- `SettingsResetSnackbarTest`: two nodes matched "Settings" with a click action; the closed Tools
  drawer stays composed off screen with its own Settings row. The test now picks the action beside
  the snackbar's message.

**Revert checks: 15, all bit, each with a failure specific to its edit.** Run by a script that edits
one line, runs only the affected classes, checks the build log for compile errors (none in any of
the 15), reads the JUnit XML, and restores the file from a copy saved before the edit (never git),
then confirms the restored file equals the forward version (true for all 15; `git status` clean after).

| Revert | Failing test, and its message |
|---|---|
| R1 return-watch guard removed | WatchFailure "a return watch that throws on every fix": expected 20 points, was 0 |
| R1 sundown guard removed | WatchFailure "a sundown watch…": expected 20, was 0 |
| R1 kept-point guard removed | WatchFailure "…every kept point": expected 20, was 0 |
| D1 backup rethrow removed | BackupViewModelTest: "the file is not removed as a failed write: [content://docs/new.zip]" |
| D1 restore rethrow removed | BackupViewModelTest: "never shown as failed: RESTORE_FAILED" |
| J1 back to `file.readText()` | CrashLogPanelTest: `FileNotFoundException` for the deleted report |
| M1 warning removed | OfflineRegionReconciliationTest: "one warning, naming the region and the missing key: []" |
| M2 message back to null | AvailabilityViewModelOfflineMapsTest: expected the partial message, was null |
| R9 Toast skipped | TrackExportPartialTest: expected the message, was null |
| D6 log call removed | ObservationMappingTest: expected the one count line, was [] |
| Thumbnails back to a fixed 4 | DecodedPhotoTest (10 px cell: expected 10×20, was 5×10) and four ThumbnailSampleSizeTest cases (expected 8, 2, 32, 1; was 4) |
| D10 handler removed | SettingsResetAndDecodeTest: the read failed with `CorruptionException: Unable to parse preferences proto` |
| D3 unknown name throws | Grid test and three SettingsResetAndDecodeTest cases: "unknown camera grid mode 'Crosshair'", "unknown backup frequency 'FORTNIGHTLY'", "unknown unit system 'NAUTICAL'" |
| R7 halt ignored | TrackRecordingHaltTest, both halt cases: "the screen shows not recording" expected true, was false |
| Snackbar never shown | SettingsResetSnackbarTest, both showing cases: timed out waiting for the message |

Not revert-checked: the merged copies (no behaviour to revert; their callers' tests are the evidence),
`SettingsRowsTouchTest` (a structure test), R7's foreground refusal inside the service (Robolectric
cannot refuse), the R2/R3/J4/J5/L4 log lines (no tests), and the capture size (device-only).

**Full suite.** The first run was killed by the 5 GB cap (systemd: "oom-kill", 5G peak): the Gradle
daemon started by the first compile kept running, with a Kotlin daemon that the revert runs had
started again, in the same scope. The JUnit folder then held only the last revert run's XML; it was
recognised as stale (one suite, a revert's failure), deleted, and not cited. Rerun with no Kotlin
daemon alive: **495 suites, 4,004 tests, 24 skipped, 11 failures**.

**All 11 are existing tests, broken by the thumbnail change, one cause, not touched:**
`PhotoDecodeThreadTest` (2), `DecodedPhotoGestureTest` (5), `DecodedPhotoSemanticsTest` (4). Each
symptom is the photo never appearing ("Condition still not satisfied after 5000 ms", "the swap to the
image did not apply within 8 frames"). The reason: these classes install test shadows of
`BitmapFactory.decodeFile` (`ThreadRecordingBitmapFactoryShadow`, `GatedBitmapFactoryShadow`) that
return an 8×8 bitmap for every call and never fill in `outWidth`/`outHeight`. The new decode reads
the file's dimensions first (`inJustDecodeBounds`, as the viewer's `decodeBoundedPhoto` does), sees
0×0 from the shadow, and stops with "could not read the dimensions", so the placeholder stays. The
real `BitmapFactory`, and Robolectric's default shadow (`DecodedPhotoTest` passes), fill them in. Also,
`PhotoDecodeThreadTest` asserts the photo is "decoded exactly once", counting `decodeFile` calls,
and the new decode makes two (bounds, then pixels). Proposed fix, test-only: both shadows answer a
bounds-only call the way the platform does (set `outWidth`/`outHeight`, return null, and neither
record nor gate it), so they count and gate only the pixel decode. Waiting for the planner's word
before touching them.

## The decode stand-ins fixed (RECORD -670), 2026-10-07; supersedes the previous section's 11 failures

The owner: "Yes, fix and rerun (Recommended)". Test-only change (`af39a73e`): both stand-ins
(`ThreadRecordingBitmapFactoryShadow`, `GatedBitmapFactoryShadow`) answer a dimensions-only call
(`inJustDecodeBounds`) as the platform does, setting `outWidth`/`outHeight` to 8 and returning null,
and neither record nor gate it; the pixel decode is recorded and gated as before. No assertion was
changed: `PhotoDecodeThreadTest`'s "decoded exactly once" now counts the one pixel decode.

- **The three classes:** 12 tests, 0 failures.
- **Revert check:** the old stand-ins restored from copies saved before the edit, the three classes
  run (no compile errors in the log): the same 11 failures as before, with the same messages. The new
  stand-ins then restored from their saved copies and compared byte for byte with what was committed.
- **Full suite, rerun** under the same caps, no Kotlin daemon left from earlier, JUnit folder cleared
  first: **495 suites, 4,004 tests, 24 skipped, 0 failures.** No out-of-memory kill in this run (the
  only one in the system log is the earlier, recorded one at 15:36).
- `./gradlew --stop` run at the end; no Gradle or Kotlin daemon left.

Device-only and still not checked: the capture size a phone picks, thumbnails' look, R7's foreground
refusal, and the inset-dependent placement of the new snackbar.
