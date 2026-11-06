# Journal entry page and barometer height: final report (parts 3b, 4 and 5)

Dispatch `prompts/preserved/2026-10-11-01.md`. Rulings: RECORD -864, -867 and -873 (branch `records-after-173`). Branch `journal-entry-height`, head `0a148b90`, not merged. Parts 1 to 3 are in `2026-10-11-journal-entry-height-parts-1-3-report.md`. Builds ran on CI only (run-this-branch). No local Gradle was run and no phone was touched.

## What landed

| Part | Forward | Revert | Restore |
|---|---|---|---|
| 4. Smoothed GPS height at read time, for Climb and the chart | `239ff402` | `51612822` | `43a25082` |
| 3b. A left-out short recording's own Start and End markers; the offline maps list | `9eb1e78f` | (a) markers `93aca7ca`, (b) list `99d151f7` | (a) `363d587a`, (b) `82a5f003` |
| 5. Barometer: PressureProvider, MIGRATION_20_21, trackHeights, chart returns | `42176d93` and fix `63331992` | `252f195a` | `0a148b90` |

**Order.** Part 4 had already been pushed when RECORD -873 arrived. So part 3b was built after part 4's revert check and before part 5.

## Evidence

Every run was a `workflow_dispatch` on the branch. For each run I checked three things:
- its `headSha` equals the commit named (the script refuses otherwise);
- its log has no compile errors (searched for `e: ` and `Compilation error`);
- its JUnit XML was read.

Restores came from copies saved before each edit. Each restore was confirmed with an empty `git diff <forward> HEAD`.

| Run | Head | Filter | Result |
|---|---|---|---|
| 38167794873 | `239ff402` part 4 | full | 608 suites, 4655 tests, 0 failures, 24 skipped (allowlisted) |
| 38168838912 | `51612822` part 4 revert (`trackHeights` returns raw altitude) | TrackHeights, ComputeTrackStatistics, EntryReport | 4 failures, the 4 predicted. Spike test (a 70.0 survives); jitter test (spread 52.0); drawn profile (110.0, expected 111.67); Compute spike (climb 25.0, expected 0) |
| 38169264800 | `9eb1e78f` part 3b | VM, ShortRecording, report screen, EntryData, report-screen map | 72 tests, 0 failures |
| 38169716954 | `93aca7ca` 3b (a) revert (markers kept) | VM, ShortRecording | 1 failure, the predicted one: `{blip-start=true, blip-saved=true, blip-end=true, ...}` |
| 38170156222 | `99d151f7` 3b (b) revert (offline list put back) | report screen | 1 failure, the predicted one: an "Offline maps" node found |
| 38170575253 | `42176d93` part 5 | full | **4 failures in `JournalBackupTest`**: schema version written as a literal 20 (see below) |
| 38171588582 | `63331992` part 5 + literal fix | full | 609 suites, 4669 tests, 0 failures, 24 skipped |
| 38172714196 | `252f195a` part 5 revert (`trackHeights` never uses the barometer) | BarometricHeight, TrackHeights | 2 failures, predicted before the run along with their values from a port of the code: real climb 59.40 (expected 50 ± 6), flat walk spread 35.11 |

**The part 5 failures were mine.** `JournalBackupTest` writes the schema version as a literal in four places, and I missed them. The failures were:
- the backup manifest's version;
- the snapshot's version;
- the live database's version during a restore;
- a "backup newer than the app" built at 21, which stopped being newer once the app was at 21.

Each moved by one (20 to 21, and 21 to 22 for the newer-than-app case). No assertion was dropped. Fixed in `63331992`.

## What each part does

**4. Smoothed GPS height** (`domain/TrackHeights.kt`, `trackHeights`, `smoothedGpsHeights`).
- How it works: a 5-point median, then a mean over the points within 30 s each side. It runs per unbroken run of heights, so a missing height still breaks the run.
- Both windows take the same number of points on each side, shrinking toward a run's ends. So a steady slope, a run's ends, and runs of one or two points come through exactly. This is why every existing `ComputeTrackStatisticsUseCaseTest` assertion still holds, and why the Records track sheet's fixture (points 1 minute apart) is unchanged.
- Readers: `ComputeTrackStatisticsUseCase` (Climb and Descent) and `heightProfileOf` (the chart). That covers the entry page and the Records track sheet alike (-867, "Both places").
- Stored `altitude`, and so GPX export, is unchanged.

**3b.**
- A new entry leaves out a left-out recording's ORIGIN and END markers (`Waypoint.isOwnMarkerOf`, `domain/ShortRecording.kt`). A waypoint saved during that recording stays in, and so does a long walk's own Start. Everything stays in Records.
- The entry page's offline maps list is gone. The Offline map toggle stays.

**5. Barometer.**
- `domain/PressureProvider.kt` (the interface, plus `pressureForPoint`) and `sensor/AndroidPressureProvider.kt`. The sensor is asked for 1 Hz, batched every 5 s. It emits `null` once when the phone has no barometer.
- `TrackRecordingService` collects it only inside the recording job. Each sampled point gets the latest reading if it is at most 2 s old, else `null`.
- `track_points.pressureHpa`, a nullable REAL, through `MIGRATION_20_21`:
  - It is a rebuild, as 14 to 15 was, because the legacy fixtures declare `TrackPointEntity`.
  - Version 21 was re-checked unclaimed on every remote just before the commit (`git log --remotes -S "version = 21,"` and `-S "MIGRATION_20_21"`: only the RECORD text and my own reports).
  - `21.json`'s identity hash was computed with Room 2.8.4's rule, read from the compiler jar with `javap`. The same script reproduces the hashes of `15.json`, `18.json`, `19.json` and `20.json` exactly.
  - **Whether the build's own export matches it was not checked.** KSP rewrites the file on CI, and the run does not publish it.
- `trackHeights` reads barometer height (`domain/BarometricHeight.kt`) for a walk with pressure on at least half its points, and smoothed GPS otherwise. The barometer path:
  - converts pressure to height with the standard atmosphere;
  - drops readings outside 500 to 1100 hPa;
  - applies a 5-point median;
  - takes out any step between readings faster than 2 m/s, together with its step back;
  - anchors to the median of (GPS minus barometer) over the first 60 s;
  - then follows GPS with an hour's time constant, ignoring GPS more than 30 m off.
- **The anchor time constant changed from step 0's 20 minutes to 60 minutes**, on synthetic walks. At 20 minutes, GPS wandering ±17 m came back through the anchor as 15 m of spread and 20 m of false climb on a flat walk. At 60 minutes it was 6 m and none. The cost: a strong 1 hPa per hour weather fall is followed only within about 8 m. All limits are starting values (-867).
- The entry page draws its chart again.

## Assertions changed, removed or inverted in parts 3b, 4 and 5

All paths are under `app/src/test/java/com/zynergylabs/forager/app/`.
- `domain/EntryReportTest.kt`, the drawn-profile test: the morning heights now expect the smoothed 111.67 and 105.33 (they were the raw 110 and 105), and the highest expects 120 (it was 130). Worked by hand in the test.
- `ui/log/CartographyEntryReportScreenTest.kt`, the lists test: its two offline-maps "displayed" assertions are inverted, and the test is renamed `the page lists no finds, tracks, waypoints or offline maps, only the tiles`.
- `ui/log/EntryDataScreensTest.kt`: part 2's inverted chart assertions are restored. The tiles test reads 509 ft, 328 ft and 0.8 mi again, and the no-heights test reads its one-line message again.
- `data/local/SchemaMigrationTest.kt`: the chain test runs to 21 instead of 20; a 20 to 21 test is added.
- `data/backup/JournalBackupTest.kt` and `JournalBackupTestSupport.kt`: version literals moved by one (listed above).

## Device check (numbered; for a phone, not run)

Cheapest first. Run on the S22 test phone unless a step says otherwise. Each step names what passes and what to keep as evidence.

1. **Barometer on the S22.**
   - Do: `adb shell dumpsys sensorservice | grep -i pressure`.
   - Pass: a pressure sensor is listed.
   - Evidence: that line.
2. **Pressure is stored.**
   - Do: record 2 minutes outdoors. Pull the database with `adb shell run-as com.zynergylabs.forager cat databases/forager.db > f.db` (debug build) and query `SELECT COUNT(pressureHpa), COUNT(*) FROM track_points WHERE trackId = '<id>'`.
   - Pass: pressure on at least half the points.
   - Evidence: the counts.
   - Why it matters: this is the only check that the sensor's timestamps share the clock `SystemClock.elapsedRealtimeNanos` uses, which the 2 s freshness test assumes. If they don't, every point stores `null` and the walk silently reads smoothed GPS.
3. **Existing entries (the owner's Oct 7 and Oct 10, on the owner's phone).**
   - Pass: map, Offline map toggle, four tiles and the Height chart; no track, finds, waypoint or offline maps list; Climb shows a figure, not "Not recorded" (part 1); the chart has no sharp drop (smoothed GPS; those walks have no pressure).
   - Evidence: what the page shows, described in words, since the owner's screenshots stay off GitHub.
   - Observation, not a gate: the existing Oct 10 entry still includes the 2:44 PM recording and its pins. The under-a-minute rules apply to new entries only.
4. **Waypoint bubble.**
   - Do: on an entry map, tap a waypoint dropped during a walk.
   - Pass: the bubble reads "… walked from the start" in the unit setting, and the bubble is fully on screen.
   - Evidence: what it says.
5. **A new entry for a day with a recording under a minute.**
   - Pass: in the editor, that recording and its Start and End markers are left out, and a waypoint saved during it is kept. Records still lists all of them.
   - Evidence: the editor's lists, described in words.
6. **The sensor runs only while recording.**
   - Do: `dumpsys sensorservice` during and after a recording.
   - Pass: Forager's pressure registration appears only during.
   - Evidence: both outputs.
7. **Battery.**
   - Do: one hour of recording with the barometer, one hour on a phone or build without it, same kind of route; then `dumpsys batterystats`.
   - Pass: the barometer's share is reported and small next to GPS. No threshold is set; that is the owner's call.
   - Evidence: both readouts.
8. **The owner's case: a flat walk with the phone in a pocket**, on the owner's own phone (the S26, which has a barometer), at least 45 minutes.
   - Pass: the chart's highest minus lowest is under about 25 ft over ground the owner calls flat, Climb is under 15 ft, and there is no sharp spike.
   - Evidence: the entry page described in words, plus the step 2 query for that walk.
   - Why 25 ft rather than step 0's 10 ft: on a synthetic walk whose GPS height wanders ±17 m, the anchor still passes 6.4 m (21 ft) of spread. The anchor time constant is the setting that would trade this against weather drift.
9. **A real climb** of known height (a known hill or stairwell).
   - Pass: Climb within ±10% of the known climb.
   - Evidence: the entry's Climb, the known height and its source.
   - Observation, not a gate: drive off at the end with the vents on, still recording, and note whether the chart shows a step beyond the road's grade.

## Not verified

- Nothing in this report was seen on a phone, so all of the device check is open.
- Whether the build's KSP export of `21.json` matches the committed file (see part 5).
- The bubble's position on a real screen. Robolectric checks text and tag only.
- The sensor limits are starting values from synthetic walks, not measurements.
- A barometer is not simulated under Robolectric. The service's wiring of `pressureForPoint` onto points has no test of its own; only the pure function is tested.
- Existing entries keep their decisions. The under-a-minute rules apply to new entries.
- Version 21 must be re-checked against the remote before any merge (-867).
