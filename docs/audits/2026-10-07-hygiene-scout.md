# Hygiene scout: where the app is over-engineered and where it is under-engineered (UI sweep 3)

Dispatch 2026-09-28-654 (RECORD intent -654; preserved at `prompts/preserved/2026-10-07-08.md` on
branch `records-after-173`). Read-only survey, written 2026-10-07 (UTC) against `origin/main` at
`aa79f25a` (PR #189). That matched the dispatch's stated base. No code was changed, and no Gradle,
phone or emulator was used.

## In short, for the owner

The app is in better shape than its size suggests. The foundations hold: the logic about the world
(distances, tracks, return estimates, sundown) is kept apart from the screens, and every outside
service (location, compass, camera, the map, the internet) is reached through a connector the app
owns, with stand-ins for testing. No domain file uses Android screen code.

The imbalance is this:

- **Over-engineered: machinery built ahead of need, and a few things done three or four ways.**
  - The motion system is mostly unwired. Of its twelve named styles, nine have no user anywhere in
    the app. The "reduce motion" switch is declared but never connected, and its own comment says
    it is. Motion Part 1 is already wiring it.
  - The app logs errors two ways. Saved settings are read back four different ways.
  - Several files carry long histories of past dispatches in their comments. In some places the
    comments run three or four lines to every line of code, and at least one of those histories
    is now out of date.
- **Under-engineered: the failure paths, which is where it matters most.** The most serious items:
  - One unexpected error during a recording could end the recording mid-walk. The sundown check
    beside it is guarded against exactly this; the GPS path is not.
  - Cancelling a backup or restore is reported as "failed", and a half-written backup file is
    deleted on the way.
  - Opening a crash log that can't be read could itself crash the app.
  - An offline map region can vanish from the list with no trace in the log.
  - If the list of restored offline regions fails to load, the screen shows a shorter list as if
    it were complete.
  - Photos have no size limit at capture, and the small thumbnails decode with no upper bound.
  - About a dozen places quietly fall back to a default without writing a log line, which
    CLAUDE.md does not allow.
- **A handful of very large files do many jobs at once.** The map's main drawing function is about
  1,100 lines, and the map screen's state holder is 1,718. They work and are heavily tested, but
  every change to them is a large change.

**Counts.** "Over" means too much machinery; "under" means too little.

| Area | Over | Under |
|---|---|---|
| Map and its chrome (with the motion package) | 2 | 5 |
| Journal, records, finds, camera, GPX | 3 | 8 |
| Recording, navigation, alerts, location | 1 | 10 |
| Data (Room, DataStore, backup, network, wiring) | 4 | 7 |
| List, Seasonal, search, Settings, Tools | 0 | 4 |
| Build and dependencies | 2 | 0 |
| **Total** | **12** | **34** |

This is a list to choose an order from. It is not a design and nothing in it is scheduled.

## How it was done, and how far to trust it

- The app was surveyed by reading code: by me, and by four read-only sub-surveys (map; journal and
  camera; recording and navigation; data, tabs and build). A fifth, a second pass on the files the
  data survey ran out of time for, did not report before this was written. Those files are listed
  as not covered in their areas.
- I re-read the code myself for every item in the top ten and for a sample of the rest. Each of
  those items is marked **re-read**. Other items rest on the sub-survey's reading and are marked
  **survey**.
- Caller counts come from `git grep -n NAME -- app/src/main`, and test counts from
  `git grep -l NAME -- app/src/test app/src/androidTest`.
- A count of zero test files means no test names the symbol. It does not rule out the behaviour
  being reached indirectly through a screen test. Where that matters it says so.
- Line numbers are as of `aa79f25a`. Paths are relative to
  `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/`, `docs/` or `.github/`.
- The CLAUDE.md rule for sorting:
  - Hand-copied logic (the same lines in several places) counts as **under**, as the dispatch lists it.
  - The same job done in several different styles counts as **over**.
- Items already recorded as deliberate are listed at the end of each area, not counted.

## The ten that matter most

1. **R1. One error on the GPS path can end a recording** (under, re-read).
   - Code: `service/TrackRecordingService.kt:164-184`.
2. **D1. Backup and restore turn a cancel into a failure** (under, re-read).
   - Code: `ui/backup/BackupViewModel.kt:239-243`, `:383-387`.
3. **J1. The crash log viewer can crash on an unreadable file** (under, re-read).
   - Code: `ui/crash/CrashLogPanel.kt:152-154`.
4. **M1. A complete offline region can drop out of the list with no log** (under, re-read).
   - Code: `map/OfflineRegionReconciliation.kt:59-72`, with its parser `map/MapLibreOfflineRegionMetadata.kt:45-63`.
5. **M2. A failed read of restored regions is shown as a complete list** (under, re-read).
   - Code: `ui/availability/AvailabilityViewModel.kt:1089-1093`.
6. **J2. Photos have no size limit at capture, and thumbnails decode unbounded** (under, re-read).
   - Code: `photo/CameraXCaptureSession.kt:271-273`, `ui/log/DecodedPhoto.kt:68`, `:107`.
7. **X1. About a dozen unlogged fallbacks** (under, re-read for most). This is the CLAUDE.md
   "no default fallback that isn't logged" rule, in several areas at once:
   - R2, R3, J4, J5, D2, D6, M1.
8. **M3. Motion machinery defined and unused, and one comment that says otherwise** (over, re-read).
   - Code: `ui/motion/ReduceMotion.kt:18-23`, `ui/motion/MotionPrecedence.kt`, `ui/motion/MotionTokens.kt`.
   - Overlaps motion Part 1.
9. **X2. Very large files doing many jobs** (under, re-read for counts):
   - `ui/map/SightingsMap.kt` (`SightingsMap()` spans `:214-1322`);
   - `ui/availability/AvailabilityViewModel.kt` (1,718 lines);
   - `ui/log/MushroomLogViewModel.kt` (1,277);
   - `ui/track/TrackRecordingViewModel.kt` (1,218);
   - `ui/availability/AvailabilityScreen.kt` (1,853).
10. **X3. Hand-copied logic in five places** (under, re-read). The copies:
    - `hasLocationPermission` (six copies);
    - three HTTP client builders;
    - a local flat-earth projection (three copies) and a second haversine;
    - four "count or zero" helpers in `MainActivity`.

The rest of this document gives each of these, and the smaller items, by area.

## Recording, navigation, alerts and location

Checked and clean:
- No file in `domain/` imports `android.*` or `androidx.*` (grep, re-read).
- Every location and sensor adapter is reached through a Forager-owned interface with a test double:
  - `LocationTracker`, `LocationProvider`, `DeclinationProvider`, `CompassProvider`, `AlertDelivery`,
    `AlertAudibility`, `LevelProvider`, `LastKnownLocationSource`, `BackgroundRunCheck` (survey).

**R1. One error on the GPS path can end a recording.** Under, re-read.
- Where: `service/TrackRecordingService.kt:164-184`.
- Why: inside `locationTracker.fixes.collect`, the calls to `returnWatch.onFix`, `sundownWatch.onFix`
  and `returnWatch.onKeptPoint` have no catch.
- Evidence of intent: the sundown tick beside it is guarded, at `:209-221`, and its comment gives the
  reason: "an exception escaping it would cancel the recording with it". The fix path has no such
  guard.
- The scope is `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (`:83`), with no exception
  handler.
- Balancing: give the fix path the same catch-and-log guard the sundown tick already has.
- Risk: a throw from the off-track judge or the return estimate stops point saving mid-walk.
  - Inferred: with no handler, that probably also crashes the process.
  - Inferred: no test feeds a throwing watch to `TrackRecordingServiceTest` or `TrackRecordingServiceSundownTest`.
  - Not run.
- Overlap: none.

**R2. An unknown recording mode silently becomes Balanced.** Under, re-read.
- Where: `service/TrackRecordingService.kt:105`, which reads
  `TrackRecordingMode.entries.firstOrNull { it.name == modeName } ?: TrackRecordingMode.BALANCED`.
- Why: there is no log line, which breaks the CLAUDE.md fallback rule.
- Balancing: log when the fallback fires.
- Risk: a renamed mode records at the wrong spacing, with no trace. Untested (survey).

**R3. A permission refusal in the one-shot location request is swallowed.** Under, re-read.
- Where: `location/AndroidLocationProvider.kt:93-97`. The `catch (e: SecurityException)` only removes the
  listener.
- Why: if every provider refuses, the caller sees "unavailable" when the cause was permission.
- Recorded only as an observation, not a decision: `docs/audits/2026-08-21-phase1-code-audit.md:158`.
- The sibling class does log the same case: `location/AndroidLastKnownLocationSource.kt:39-46`.
- Balancing: log it as the sibling does.
- Risk: low. One test file names the class.

**R4. `TrackRecordingViewModel` is 1,218 lines doing many jobs.** Under, survey (count re-read).
- Where: `ui/track/TrackRecordingViewModel.kt`.
- Jobs: start/stop and polling, the abandoned-track sweep (`:289`), live-fix gating (`:888-891`), the
  return estimate, waypoint and track pending-deletes (`:1033`, `:1102`), and export.
- Balancing: the delete-undo and live-fix parts are separable.
- Risk: the poll-loop hang CLAUDE.md describes. Guarded by `TrackRecordingViewModelTest`
  (`runRecordingTest`) and 13 other test files.

**R5. `hasLocationPermission` is hand-copied six times.** Under, re-read (count).
- Where:
  - `MainActivity.kt:303`
  - `location/AndroidLastKnownLocationSource.kt:49`
  - `location/AndroidLocationProvider.kt:43`
  - `location/AndroidLocationTracker.kt:95`
  - `service/TrackRecordingService.kt:350`
  - `ui/map/SightingsMap.kt:1892`
- Partly recorded: the service's KDoc (`:344-349`) justifies its own copy and names three of the
  others. It does not mention the tracker's or the last-known source's copy.
- Balancing: one helper in the Android layer.
- Risk: one copy changing alone lets the service start without permission. That reaches the
  foreground-service crash described at `:323-334`.

**R6. The local flat-earth projection is hand-copied three times.** Under, re-read.
- Where: `domain/TrackSelfJoin.kt:91-92`, `domain/RouteHome.kt:195-196`, `domain/OffTrackJudge.kt:108-109`.
- Each copy computes `metersPerDegreeLat = Math.PI * GeoDistance.EARTH_MEAN_RADIUS_METERS / 180.0`,
  then scales longitude by `cos(lat)`.
- Balancing: one function on `GeoDistance`.
- Risk: low; the copies agree today. Each caller has its own tests.

**R7. `startForeground` has no catch for refusals other than permission.** Under; the gap is
re-read, the risk is inferred.
- Where: `service/TrackRecordingService.kt:336-342`.
- Inferred: Android 12+ can refuse a foreground start after a sticky restart from the background
  (`ForegroundServiceStartNotAllowedException`). Nothing catches that.
- Balancing: catch, log and stop, as the permission branch does.
- Risk: the service crashes on a restart. No test.

**R8. A fix with no accuracy is handled three different ways.** Under, survey.
- `domain/LocationSampler.kt:31` accepts it.
- `domain/LiveFixGate.kt:83` accepts it, and that is recorded at `:26-28`.
- `domain/OffTrackJudge.kt:82` treats a missing accuracy as `0.0`, meaning perfect.
- Why: CLAUDE.md asks for an explicit operating limit on reported ranges. Only one of the three
  states its rule.
- Balancing: state the null rule where each gate applies it.
- Risk: a fix with no accuracy lands in the track or drives an off-track alert. Guarded by
  `LocationSamplerTest` and two `OffTrackJudge` test files.

**R9. The GPX export quietly drops the full record on a read failure.** Under, re-read.
- Where: `ui/track/TrackExportPanel.kt:272-274`, which does `.onFailure { Log.w(...) }.getOrDefault(emptyList())`.
- Why: the failure is logged, but the user gets a share sheet with no hint that the export is
  partial. That breaks the CLAUDE.md rule that partial results are reported as such.
- Balancing: tell the user the export is partial.
- Risk: someone shares what they believe is the full record. Test coverage not checked.
- Overlap: possibly the data scout, if it covers the export surface.

**R10. Thin direct tests for two navigation thresholds.** Under, survey.
- `isInApproachingZone` (`domain/NavigationReadout.kt:80`) has 0 test files.
- `LiveFixGate`'s `acceptLiveFix` has 1.
- Balancing: a boundary test for the 100 m approach zone.

**R11. Comments far outweigh code in the return-estimate files.** Over, survey.
- Comment to code lines:
  - `domain/NetworkProviderFix.kt` 96:24
  - `domain/PathHome.kt` 110:50
  - `domain/MovingPace.kt` 173:104
  - `domain/ReturnWalkingTime.kt` 63:51
- Much of it is dispatch history.
- A worked example of how this goes stale: MovingPace and PathHome comments, and CLAUDE.md's
  "check reachability" entry (`CLAUDE.md:150-156`), describe `returnWalkingTime` as having no
  production caller. That was true when written. Since then it has one:
  `domain/SundownWatch.kt:254`, built at `AppContainer.kt:372` and driven by the recording service.
  - Re-read.
  - The CLAUDE.md entry is a past-tense account, not a present claim, so this is not an error in
    it. A later reader could still take it as current.
  - Not edited here.
- Balancing: keep rule and reason in code; move history to `docs/navigation/`.

Recorded as deliberate, not counted:
- Null accuracy passes the live-fix gate: `domain/LiveFixGate.kt:26-28`.
- The off-track judge has no accuracy ceiling, provisional: `domain/OffTrackJudge.kt:8`, `:49-50`.
- The sundown tick catches and logs: `service/TrackRecordingService.kt:209-221`.
- Alert delivery catches, logs and never throws: `alert/AndroidAlertDelivery.kt:66-71` (dispatch 451).
- The off-track preference read fails open to "on", logged: `service/TrackRecordingService.kt:158-160`.
- The off-track alert does not override silent mode (owner, 2026-09-11): `domain/ReturnWatch.kt:272-274`.
- The sampler's accuracy rejection never fires on the owner's GPS:
  `domain/LocationSampler.kt:23-27`, `docs/audits/2026-09-07-fix-log-walk-findings.md`.

## Journal, records, finds, camera and GPX

**J1. The crash log viewer can crash on an unreadable file.** Under, re-read.
- Where: `ui/crash/CrashLogPanel.kt:152-154`, which does
  `LaunchedEffect(file) { content = withContext(Dispatchers.IO) { file.readText() } }`.
- Why: there is no catch. A missing or unreadable file throws out of the composition's coroutine.
  `CrashLogPanel` has 0 test files.
- Recorded: `RECORD.md:9300` covers this read's threading only, not its error handling.
- Balancing: one guarded read that logs and shows "couldn't read this log".
- Risk:
  - The screen for diagnosing crashes becomes a crash.
  - Inferred: it reaches the uncaught handler. The scope's handler was not traced.
- Overlap: **data scout** (Crash logs is on its list of screens).

**J2. Photos have no size limit at capture, and thumbnails decode without an upper bound.** Under, re-read.
- Capture: `photo/CameraXCaptureSession.kt:271-273` builds
  `ImageCapture.Builder().setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY).build()`, with no resolution
  selector. Capture size is whatever the device offers.
- Thumbnails: `ui/log/DecodedPhoto.kt:68` decodes at a fixed `inSampleSize = DECODE_SAMPLE_SIZE`
  (`:107`, value 4), whatever the source size.
- The photo viewer, by contrast, has an explicit limit: `VIEWER_MAX_EDGE_PX = 4096` in
  `decodeBoundedPhoto` (`ui/log/PhotoViewerDialog.kt:443-500`). So the same files decode two ways.
- Why: this is the CLAUDE.md rule that a device-reported range is not a safe operating range.
- Balancing: a resolution cap at capture, and thumbnails through the bounded decoder.
- Risk:
  - Inferred: a large imported photo gives a multi-megabyte bitmap per grid cell, and memory runs out.
  - `DecodedPhotoTest` checks sizes derived from the constant.
  - `CameraXCaptureSession` is untested by design. Robolectric has no camera; recorded at
    `photo/CameraXCaptureSession.kt:40-46`.

**J3. `MushroomLogViewModel` is 1,277 lines mixing many jobs.** Under, survey (count re-read).
- Jobs: list loading, draft and edit, pending delete and undo, photo add, pull and remove, and
  patching a capture's location.
- `onAddPhoto` runs about 134 lines from `:837`. `CartographyViewModel.kt` is 787 lines.
- Balancing: separate photo acquisition and pending-delete.
- Risk: guarded by `MushroomLogViewModelTest` (72 tests) and 12 more files.

**J4. Imported-photo EXIF failures look like "no location".** Under, re-read.
- Where: `photo/FilePhotoStore.kt:166-177` (`runCatching { … }.getOrNull() ?: ExifData(null, null, null)`)
  and `:185`.
- Why: a refused permission, an IO error or a malformed date all become "no coordinate, no time"
  with no log. The KDoc (`:155-162`) calls a missing value the ordinary case, but it handles
  exceptions the same way as absent tags.
- Balancing: log when the catch fires; keep the null result.
- Risk: a find silently gets no position. `FilePhotoStore` has 6 test files; whether any tests the
  throwing path was not checked.

**J5. The orientation read in the metadata scrub falls back unlogged.** Under, survey.
- Where: `photo/PhotoMetadataScrub.kt:88-90`, which does `.getOrElse { ExifInterface.ORIENTATION_UNDEFINED }`.
- Why: a read error looks the same as "no tag". The two other failure points in the same function do
  log.
- Balancing: log in that branch.
- Risk: a photo shows sideways with nothing logged.

**J6. `RecordDetailsSheet` has no tests naming it.** Under, survey.
- Where: `ui/log/RecordDetailsSheet.kt`, 589 lines, 0 test files.
- Indirect coverage through `RecordsTab` tests (4 files) was not checked.
- Overlap: **data scout** (Records and track details).

**J7. Very large Journal screens.** Under, survey (counts re-read).
- `ui/log/JournalTab.kt` 930 lines (`JournalTab()` from `:129`, about 310 lines).
- `CartographyEntryReportScreen.kt` 807; `CartographyEntryEditScreen.kt` 675;
  `CartographyScreen.kt` 659.
- `InAppCameraDialog.kt` 531; `PhotoViewerDialog.kt` 530.
- Balancing: split along existing sections.
- Overlap: **data scout**, which is cataloguing the entry report and editor (the Withhold list).

**J8. GPX import parsing is tested only through the use case.** Under, mild, survey.
- `domain/GpxImportReader.kt` has 0 direct test files. It is covered via `ImportGpxUseCaseTest`,
  `ImportedTrackOutOfJournalTest` and `GpxImportRecordsTest`.
- Which edge cases those cover was not checked.

**J9. Errors are logged two ways.** Over, re-read.
- `domain/ErrorLog.kt` is the owned logging seam, used in 22 main files. Raw `android.util.Log` is
  used in 46.
- ErrorLog's own KDoc (`:15-16`) says `MushroomLogViewModel`'s `Log.w` calls are safe "only because
  nothing tests its failure paths at all". That is now stale, by survey reading:
  `MushroomLogViewModelTest` runs under Robolectric and injects failures (`:640`, `:1553`, `:1921`, `:1970`).
- Balancing: one convention per layer, and a corrected comment.
- Risk: low.

**J10. `PhotoExporter` has one implementation and no test double.** Over, survey.
- Where: `photo/PhotoExporter.kt:35`, implemented only by `FilePhotoExporter` (`:50`).
- The seam's reason is stated at `:22-24`: wrapping the platform, as CLAUDE.md asks.
- Balancing: a fake in `PhotoViewerSaveTest`, or accept it as a vendor seam.

**J11. Comments carry dispatch history.** Over, survey.
- Comment to code lines:
  - `ui/log/WindowOrientation.kt` 96:30
  - `ui/log/PhotoDecodeDispatcher.kt` 28:11
  - `ui/log/CameraAbsence.kt` 77:42
  - `photo/FilePhotoStore.kt` 101:79
- `photo/CameraXCaptureSession.kt` has a header of about 160 lines (227:306 overall). Much of it is
  device findings worth keeping somewhere.
- Balancing: keep the contract in code; move history to `docs/audits/`.

Recorded as deliberate, not counted:
- Imported photos are unscrubbed and exported as a byte copy (owner, 2026-09-29): `photo/PhotoExporter.kt:26-30`.
- The scrub fails open (keeps metadata) when it can't scrub: `photo/PhotoMetadataScrub.kt` around `:70-73`.
- Absent and undefined orientation are treated alike:
  `docs/audits/2026-09-14-capture-metadata-scrub-completion-report.md:116-126`.
- `CameraXCaptureSession` is untested (no camera under Robolectric): `photo/CameraXCaptureSession.kt:40-46`.
- `DecodedPhoto` uses raw `Log.w`: `ui/log/DecodedPhoto.kt:44-48`.
- EXIF location is lost without `ACCESS_MEDIA_LOCATION`: `docs/audits/2026-09-08-data-inventory-for-privacy-policy.md:354`.
- Stale GPX exports are deleted after an hour: `export/TrackGpxExporter.kt:75-81`.
- `CrashLogPanel`'s IO threading is left pending a ruling: `RECORD.md:9300`.
- `StatusBarHider` has one implementation but a test double: `InAppCameraDialogTest.kt:143`. Justified.

## Map and its chrome (with the motion package)

Checked and clean:
- MapLibre stays inside `map/` and `ui/map/`. `AvailabilityViewModel` imports none of it (survey).
- Single-implementation interfaces here all have test doubles (survey):
  - `NudgeCamera`, `FlingSwitch`, `MapTeardownTarget`, `FanSpace`, `MapProbe`, `MapTapSinks`, `OfflineMapRepository`.
- The night recolour's JSON catch is not swallowed (re-read). It becomes an explicit
  `NightRecolour.Left` with a reason (`ui/map/NightColour.kt:106`), which is logged at
  `ui/map/SightingsMap.kt:2409`.

**M1. A complete offline region can drop out of the list with no log.** Under, re-read.
- Where: `map/OfflineRegionReconciliation.kt:59-72`. Its parser is `map/MapLibreOfflineRegionMetadata.kt:45-63`.
- The list code: `dao.getById(region.id) ?: region.metadata?.toRegionMetadata()?.let { … } ?: return@mapNotNull null`.
  A region with tiles on disk, no Room row and unreadable metadata disappears without a log line.
  The other branches in the same function do log (`:39-53`).
- The parser: `toRegionMetadata` catches `NullPointerException`, `NumberFormatException` and
  `IllegalArgumentException`, and returns a bare `null` each time. So even where a caller logs, it
  can't say why.
- Balancing: log in that branch, and have the parser say why it failed.
- Risk:
  - Tiles stay on disk, invisible and undiagnosable.
  - `OfflineRegionReconciliationTest` covers the rebuilt-from-metadata case and the incomplete case.
    By test name, no test covers complete plus unreadable (inferred).
  - `MapLibreOfflineRegionMetadataTest` has 4 tests.

**M2. A failed read of restored regions is shown as a complete list.** Under, re-read.
- Where: `ui/availability/AvailabilityViewModel.kt:1089-1093`.
- `listNotDownloadedRegions().getOrElse { errorLog.w(…); emptyList() }` is followed by
  `offlineRegionsErrorMessage = null`.
- Why: the failure is logged, but the screen shows the downloaded regions alone with the error
  cleared. CLAUDE.md: "Partial or failed results are reported as such."
- Found independently by two sub-surveys.
- Balancing: carry the partial state into the screen.
- Risk: a user who restored a backup sees none of the restored regions, with no reason given.
  Coverage of this branch not checked.

**M3. Motion machinery defined and unused, with one false comment.** Over, re-read.
- `LocalReduceMotion` (`ui/motion/ReduceMotion.kt:23`):
  - 0 callers in `main/`, 0 test files.
  - Its KDoc (`:18-21`) says "the app root provides the real value". Nothing does.
  - The setting is meanwhile read directly at `ui/map/fanout/MarkerFanOutState.kt:78`.
- `MotionPrecedence` (`ui/motion/MotionPrecedence.kt`, 79 lines) and the reduced-motion mapping
  (`MotionTreatment`, `ReducedMotionTreatment`, `reducedMotionEquivalent`, `ReduceMotion.kt:26-58`)
  have 0 production callers, 1 test file each.
- `MotionTokens` (`ui/motion/MotionTokens.kt`): 9 of 12 named items have no production caller.
  - `panelMotionSpec` (20 callers), `navigationMotionSpec` (15) and
    `LOCATION_INDICATOR_MOVE_DURATION_MS` (2) are used.
  - `MARKER_CLUSTER_STAGGER_STEP_MS` and `ROUTE_REVEAL_MS_PER_KM` have no caller and no test.
- Partly recorded: ADR-0002 (`docs/adr/0002-motion-scheme-adoption.md:93-102`) records the unused spec
  *functions* as scaffolding, and ADR-0001 records the precedence design. Neither records the two
  constants, `LocalReduceMotion`'s state, or that the precedence rules are unwired.
- `docs/audits/2026-08-21-phase1-code-audit.md:211` already notes that `MotionPrecedenceTest:44-49`
  checks a constant against itself (a wiring test).
- Balancing: wire or record each piece; correct the comment either way.
- Risk:
  - None at runtime today.
  - A future caller trusting `LocalReduceMotion` would always get `false`.
- Overlap: **motion Part 1** (dispatch -652, item 1) wires `LocalReduceMotion` app-wide and moves the
  marker fan onto it. This finding should be closed by that work, not separately.

**M4. `SightingsMap()` is one composable of about 1,109 lines.** Under, re-read (span).
- Where: `ui/map/SightingsMap.kt:214-1322`, in a 2,486-line file.
- Jobs (survey): style load, layers, camera, location dot, taps, marker fan, night recolour,
  teardown. It takes about 40 parameters, forwarded at `ui/map/MapSlot.kt:530-575`.
- Balancing: split into separately testable parts, in steps.
- Risk: every edit is a large one.
  - 36 test files name it, mostly through the Availability screen.
  - The map body itself is largely unexecuted on the JVM because MapLibre's native code cannot run
    under Robolectric (`map/MapLibreInitializer.kt:6-7`).
  - Inferred: most protection is device checks.
- Overlap: **motion Part 1** may touch the fan and map-side animation here.

**M5. `MapSlot.kt`'s comments outnumber code more than three to one, and one doc block is attached
to the wrong thing.** Over, re-read.
- Size: about 434 comment lines to 132 code lines (survey count).
- The misplaced block: the KDoc explaining `MapOverlayContent` (the Compose compiler crash at ten
  parameters) sits at `:24-36`, directly above `MapRenderMode`'s own KDoc (`:37`). It documents
  nothing. `MapOverlayContent` itself has no KDoc, yet `MapRenderMode`'s KDoc sends readers to
  "its doc comment".
- Balancing: move the block; trim the history.

**M6. Map chrome composables of 146-160 lines.** Under, minor, survey.
- Where: `ui/map/MapChrome.kt` (901 lines): `MapIconBar` from `:411`, `MapIconBarMinimizeHandle` from
  `:571`, `MapModePicker` from `:147`.
- Balancing: none beyond keeping the coordinate-touch tests.
- Overlap: **motion Part 1** (items 2 to 4: press highlight and bounce on the icon bar, and the
  minimise handle). Splitting these while Part 1 is in flight would collide.

**M7. The route-home layer code has no tests.** Under, low, survey.
- `addRouteHomeLayers` (`ui/map/RouteHomeLayers.kt:136`) and `updateRouteHomeLayers` (`:189`) have 0
  test files. Their pure helpers are tested.
- Inferred: the gap is the MapLibre style boundary, so a device check is the realistic guard.

Recorded as deliberate, not counted:
- Unused motion spec functions as scaffolding: `docs/adr/0002-motion-scheme-adoption.md:93-102`.
- The selection-pulse scale bounds are covered by the same ADR note (inferred).
- The self-referential motion tests: `docs/audits/2026-08-21-phase1-code-audit.md:210-216`, `:260`.
- `MARKER_CLUSTERING_THRESHOLD` is provisional: `docs/adr/0001-motion-precedence.md:89-96`.
- The MapLibre offline database stays at its default path: `map/MapLibreStorage.kt:7-20`.
- One shared offline style URL over HTTPS: `map/OfflineStyle.kt`;
  `docs/audits/2026-09-07-offline-style-swap-prebuild-report.md`.
- Room rows missing from MapLibre's read are kept and logged: `map/OfflineRegionReconciliation.kt:34-38`.
- The prior offline list is kept on a failed refresh: KDoc at `ui/availability/AvailabilityViewModel.kt:1080-1082`.

## Data: Room, DataStore, backup, network and app wiring

**D1. Backup and restore turn a cancel into a failure.** Under, re-read.
- Where: `ui/backup/BackupViewModel.kt:239-243` (backup) and `:383-387` (restore). Both read
  `try { … } catch (e: Exception) { Result.failure(e) }`, inside `scope.launch`. The scope is
  cancelled in `onCleared` (`:422`).
- Why: a cancellation is an `Exception`, so cancelling becomes a failure.
  - On backup it is logged as failed, the created file is removed, and the write-failed prompt is set.
  - On restore the screen shows "restore failed".
  - The inner `RoomJournalBackup.attempt` rethrows cancellation correctly (`data/backup/RoomJournalBackup.kt:93`),
    and the same ViewModel rethrows it correctly at `:397`. These two outer catches undo that.
- Balancing: rethrow cancellation at these two sites, as `:397` already does.
- Risk:
  - A misreported failure.
  - State written after the ViewModel is cleared.
  - Inferred: `BackupViewModelTest` does not cover cancellation (not checked).
- Overlap: **data scout** lists Backup as a screen, for display. This finding is about the code
  path, not what is shown.

**D2. The theme setting silently resets when the stored value is unknown, and its comment says
otherwise.** Under, re-read.
- Where: `data/repository/DataStoreAppThemePreferenceRepository.kt:37-40`.
- An unknown stored name (for example after a downgrade) falls through
  `?: legacy ?: AppThemeMode.SYSTEM_DEFAULT` with no log.
- The class comment (`:21-23`) says only a missing value falls back.
- None of the 6 tests stores an unknown name.
- Balancing: treat an unknown name as a logged failure, as the camera grid setting does.

**D3. Saved settings are read back four different ways.** Over, survey.
- An unrecognised stored enum name is:
  - silently defaulted (theme, `DataStoreAppThemePreferenceRepository.kt:37`);
  - thrown (unit system, `DataStoreUnitSystemPreferenceRepository.kt:34`);
  - an `error(...)` (backup frequency, `DataStoreBackupSchedulePreferences.kt:36`);
  - a named `Result` failure (camera grid, `DataStoreCameraGridModeRepository.kt:41-45`).
- D2 is the cost of this.
- Balancing: one shared decode for stored names.

**D4. Three HTTP clients are built from hand-copied code.** Under, re-read.
- `data/remote/OpenMeteoClient.kt` and `data/remote/OpenMeteoArchiveClient.kt` differ only in the
  comment, the object name, the base URL and the API type (`diff`, re-read).
- `data/remote/INaturalistClient.kt:17-31` has the same builder: the User-Agent interceptor, 15-second
  timeouts and debug logging.
- Each file's comment still says it is "the only place that constructs Retrofit/OkHttp".
- The archive client's comment gives a real reason for a separate client (a different host). That
  is a reason for a different base URL, not for a copied builder.
- Balancing: one builder that takes a base URL.
- Risk: a timeout or User-Agent change lands in one client and not the others.

**D5. No explicit request budget for iNaturalist.** Under; the breach is inferred.
- What exists:
  - Search typing is debounced at 300 ms (`ui/availability/AvailabilityViewModel.kt:382`, `:1688`).
  - Page sizes are bounded (30 and 200, `data/remote/INaturalistApi.kt:41`, `:55`).
- What doesn't: `git grep -niE "rate.?limit|throttl|Semaphore|minInterval"` finds no request budget on
  the network path (survey).
- CLAUDE.md names rate limits among the ranges that need an explicit operating limit.
- Balancing: a request budget at the repository boundary.
- Risk: throttling by iNaturalist. No test.

**D6. Malformed iNaturalist dates and coordinates vanish without a log.** Under, re-read.
- `data/repository/INaturalistMushroomRepository.kt:117-121` turns a parse failure into `null`, which
  looks the same as no date.
- `parseLocation` (`:104-112`) returns `null` for out-of-range coordinates, and `mapNotNull` (`:48`)
  drops the sighting with no count. `totalResults` still reports the API's total.
- The obscured-location exclusion is recorded at `:67-83`. The malformed drop is not.
- Balancing: log or count parse failures separately from legitimate absences.

**D7. Four hand-copied "count or zero" helpers in `MainActivity`.** Under, re-read.
- Where: `MainActivity.kt:763-815`, about 63 of its 815 lines. Each is
  `fn(id).getOrElse { errorLog.w(…, "…; showing 0.", error); 0 }`.
- They are logged, so the issue is the copying, and that they sit in the Activity.
- Balancing: one generic helper outside the Activity.

**D8. A second haversine.** Under, re-read.
- `data/backup/RegionMatch.kt:12-31` computes haversine with `EARTH_RADIUS_METRES = 6_371_000.0`.
- `domain/GeoDistance.kt:34`, `:37` already provides `metersBetween`, with radius `6_371_008.8`.
- Harmless at the 1 m threshold this uses. It is still a second formula with a second radius.
- Balancing: call `GeoDistance`.

**D9. Three Result-wrapping helpers.** Over, mild, survey.
- `runCatchingCancellable` (`data/repository/RunCatchingCancellable.kt:18`, 36 call sites).
- `RoomJournalBackup.attempt` (`data/backup/RoomJournalBackup.kt:91-100`).
- `PhotoExporter.loggedOnFailure` (`photo/PhotoExporter.kt:129`).
- `attempt` has a real extra duty (logging and wrapping).
- Balancing: let `attempt` delegate to the first.

**D10. DataStore repositories built two ways, none with a corruption handler.** Over, survey.
- 2 of 11 take an injectable scope (`DataStoreSundownPreferencesRepository.kt:75`,
  `DataStoreMapPreferencesRepository`). The other 9 use the default.
- `ReplaceFileCorruptionHandler` has 0 uses (re-read). A corrupt file makes every read of that store
  fail, logged only where the caller logs.
- Balancing: one construction helper.

**D11. A misplaced doc comment in the backup file seam.** Over, minor, survey.
- `data/backup/ContentResolverBackupFiles.kt:39-43`: `delete`'s comment sits above `sizeOf`'s.
- The catch at `:58` beside it does log (re-read).

Not covered: this pass was ended before a second survey of these files could report, so they are **not cleared, only unread**: the internals of `data/local/Migrations.kt` (1,049 lines; 16 test files name a `MIGRATION_x_y`, so per-migration coverage was not mapped) and the structure of `AppContainer.kt` (419 lines).

Recorded as deliberate, not counted:
- No `@ForeignKey` in `ForagerDatabase`: CLAUDE.md; `docs/audits/2026-09-29-journal-backup-completion-report.md:23`.
- The empty catch in `crash/CrashUncaughtExceptionHandler.kt:23-26`: crash capture must never block
  the previous handler (inline comment).
- MapLibre init failure logged without the exception: `ForagerApplication.kt:66-80` (already logged
  inside the initializer).
- Search cache failures degrade to a miss, logged: `data/repository/RoomSearchCacheRepository.kt:25-29`.
- The photo-location preference fails closed, logged: `MushroomLogViewModelFactory.kt:39-48`.
- Obscured iNaturalist observations are excluded: `data/repository/INaturalistMushroomRepository.kt:67-83`.
- Each DataStore builds its own `PreferenceDataStoreFactory.create` (Robolectric isolation): CLAUDE.md;
  `DataStoreMapPreferencesRepository.kt:26-27`.
- The checkpoint before a backup snapshot is best effort, logged: `data/backup/RoomJournalBackup.kt:155-158`.
- `ScheduledBackupDependenciesProvider` (`data/backup/ScheduledBackup.kt:29`) has one implementation
  (`ForagerApplication`) and no test double. Workers can't take constructor arguments, so this is the
  usual seam (inferred); not counted.

## List, Seasonal, search, Settings and Tools

**L1. `AvailabilityViewModel.kt` is 1,718 lines.** Under, survey (count re-read).
- 51 public functions. It mixes search, offline regions, trips, waypoints, map layers, live fix and
  night mode.
- Its 41 test files already split along those lines (`…OfflineMapsTest`, `…FilterTest`,
  `…LiveFixTest`, `…PlannedTripsTest`).
- Balancing: split along the lines the tests already follow.
- Risk: high churn, well guarded.

**L2. Very large screen files.** Under, survey (counts re-read).
- `ui/availability/AvailabilityScreen.kt` 1,853; `AvailabilitySearchUi.kt` 1,040;
  `AvailabilityResultsUi.kt` 687; `AvailabilitySettingsUi.kt` 676.
- Overlap: **motion Part 1** item 7, the search dropdown tap-through. The survey placed it in
  `AvailabilityCompactScaffold.kt` (1,478 lines), which is equally large.

**L3. Settings composables have thin direct tests.** Under, survey.
- Test files naming each: `SettingsContent` 0, `BuildIdentityFooter` 0, `DrawerHeader` 0,
  `SettingsEntryRow` 2, `CompactToolsDrawerContent` 4.
- Inferred: they may be reached through screen tests such as `BackupSettingsScreenTest`.
- Overlap: **data scout** (Settings readouts).

**L4. "No maps app" and "can't open iNaturalist" show a Toast with no log.** Under, borderline, survey.
- Where: `ui/availability/AvailabilityPureFunctions.kt:171`, `:218`.
- The user is told, so this is minor.
- The file is named "PureFunctions" but uses `Toast`, `Context` and `Intent`.

Not covered: this pass was ended before a second survey of these files could report, so they are **not cleared, only unread**: the internals of `AvailabilityScreen.kt`, `AvailabilitySearchUi.kt`, `AvailabilityResultsUi.kt` and `AvailabilitySettingsUi.kt` (their longest functions, dead private functions, per-composable test counts), and `ui/theme` and `ui/adaptive`.

## Build and dependencies

Checked and clean:
- All 26 entries in `gradle/libs.versions.toml` are exact versions. There is no `+`, range,
  SNAPSHOT or `latest` (survey; the grep for `+` and `latest` re-run).
- The one pre-release, `material3 = "1.5.0-alpha26"`, is pinned exactly and recorded in completion
  reports.

**B1. Compose preview tooling is declared but nothing uses it.** Over, re-read.
- `app/build.gradle.kts:489` (`ui-tooling-preview`) and `:521` (`ui-tooling`, debug only).
- `git grep -l "@Preview" -- app/src/main` returns 0.
- Balancing: drop both, unless previews are planned.

**B2. `app/build.gradle.kts` is about 44% comments.** Over, mild, survey.
- About 300 of 680 lines are comments (302 lines starting with `//`, `/*` or `*`, re-counted).
- Whether they carry weight was not judged. Much of the file is signing and build-identity
  safeguards (`:30-110`, `:175-203`, `:331-420`), where the reasons matter.

**B3. The CI skip allowlist holds 24 tests.** Information, not counted.
- Where: `.github/workflows/ci.yml:237-362`.
- The mechanism is sound: exact identity match, and the run fails on a stale entry (`:376`).
- Whether every entry has a recorded reason was not checked.
- Silencing tests is the owner's call (CLAUDE.md), so this is listed for completeness only.

Not covered: this pass was ended before a second survey of these files could report, so they are **not cleared, only unread**: the 13 files in `scripts/` (whether each is referenced from CI or docs, and whether each stops on error).

## Overlaps with the other two sweeps

Neither sibling branch had any commits when this was written. `motion-part-1` and `data-scout` were
both at `aa79f25a` locally, and neither was on the remote. So the overlaps below are read from their
dispatches (-652 and -653), not from their code.

**Motion Part 1 (-652)**
- **M3**: `LocalReduceMotion` and the unused motion machinery. Part 1's item 1 wires
  `LocalReduceMotion` and moves the marker fan onto it, which should close the false comment and the
  second way of reading the setting. The unused tokens and `MotionPrecedence` stay a separate
  question.
- **M6**: `MapIconBar` and the minimise handle in `ui/map/MapChrome.kt`. Part 1 adds the press
  highlight and bounce there; any split of these functions would collide.
- **M4**: `SightingsMap.kt`. The marker fan and map-side animation.
- **L2**: the search dropdown, in `ui/availability/AvailabilityCompactScaffold.kt`. Part 1's item 7
  changes its close.

**Data scout (-653)**
- **J1**: the crash log viewer.
- **J6**: `RecordDetailsSheet`.
- **J7**: the entry report and editor, the Withhold list.
- **L3**: Settings.
- **D1**: the Backup screen. Only for display; D1 is about the code path behind it.
- **R9**: the GPX export, if the data scout covers exports.

The data scout catalogues how data is *shown*. This survey covers how the code *behaves*. They
touch the same files but not the same lines, as far as can be told without its report.

## What is inferred rather than confirmed

- **Consequences.** That R1 crashes the process; that J1 reaches the uncaught handler; that J2 runs
  out of memory on a large import; that R7's exception fires on a sticky restart. The code gaps are
  confirmed, the consequences are not, and nothing was run.
- **Zero test counts.** These are name matches only. A screen test may reach the same code
  indirectly, notably J6, L3 and J8.
- **Survey items.** Items marked "survey" rest on a sub-survey's reading and were not re-read by me.
- **Not covered.** The areas listed as not covered above.
