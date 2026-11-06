# Journal entry page and height: step 0 report

Dispatch `prompts/preserved/2026-10-11-01.md` (branch `records-after-173`, commit `bdd5f5c9`). Owner's rulings: RECORD -864 (same branch). Step 0 only: code read, nothing built. No Gradle build, no test run, no phone.

**Base:** `origin/main` = `bde42068` (checked with `git rev-parse origin/main` after a fetch, 2026-10-11 18:2x UTC), as the dispatch assumed. All file and line references are at that commit, under `app/src/main/java/com/zynergylabs/forager/app/` unless a path is given in full.

## Premises checked

| Premise in the dispatch | Result |
|---|---|
| No pressure sensor used in app/src/main | **Confirmed.** `grep -rn "TYPE_PRESSURE\|PRESSURE" app/src/main` finds nothing. The only `SensorManager` users are `sensor/AndroidCompassProvider.kt`, `sensor/AndroidLevelProvider.kt` (and a comment in `service/TrackRecordingService.kt:469`). |
| Climb is hysteresis-filtered GPS altitude, `ComputeTrackStatisticsUseCase.kt:14-35` | **Confirmed.** 4 m threshold (`domain/ComputeTrackStatisticsUseCase.kt:109-111`), loop at `:63-87`. |
| Barometer is idea 7 of the fusion plan, from research section 2.7 | **Partly wrong reference.** Idea 7 is at `docs/plans/ideas-from-fusion-plan.md:151-166` and says "Source: §2.7". The research file has no section 2.7: the barometer row is in the table of section 1 (`docs/navigation/2026-10-04-location-fusion-research.md:25`). Content matches; the section number does not. |
| The S26 has a barometer (RECORD -566 Amendment 1) | **Confirmed** in RECORD.md on `records-after-173`, line 12350. |
| The S22 has one | **Unverified.** No capability read for the S22 names a barometer (searched RECORD.md for "barometer"). |
| DB version and next migration number | `version = 20` (`data/local/ForagerDatabase.kt:188`). Last migration `MIGRATION_19_20` (`data/local/Migrations.kt:1114`). `git log --remotes=origin -S "version = 21," -- .../ForagerDatabase.kt` finds no remote branch claiming 21. |

## 1. The entry page: where things are drawn, and who else reads them

- **Screen:** `ui/log/CartographyEntryReportScreen.kt`. The report figures are built once at `:392-394` with `entryReportOf(entry, candidates?.tracks, candidates?.waypoints, ComputeTrackStatisticsUseCase())`.
- **Below the map, in order** (`:691-717`): `EntrySummaryTiles` (`:691`), `EntryHeightProfile` (`:692`), `EntryWaypointTable` (`:693-698`), `EntryTrackRows` (`:699-704`), a "Finds" list (`:705-708`), an "Offline maps" list (`:709-714`). Above them, when present: entry text, tags, photos (`:663-688`).
- **Composables:** `ui/log/EntryReportSummary.kt` — tiles `:54-109`, height chart `:118-168` (with a one-line fallback `:170-178`), waypoint table `:190-243`, track rows `:253-281`.
- **Data:** `domain/EntryReport.kt` — `EntryReport` `:21-31`, `HeightProfile` `:33-54`, `EntryReportWaypoint` `:64-71`, `entryReportOf` `:92-139`, `heightProfileOf` `:146-175`.
- **Other readers:**
  - `entryReportOf` / `EntryReport` / `EntryWaypointTable`: only this screen in main (grep). Not used by any export.
  - **`EntryHeightProfile` and `heightProfileOf` are shared with Records.** The track sheet draws the same chart from the same rule: `ui/log/RecordDetailsSheet.kt:429` and `:677-678`. Its Climb and Descent tiles use the same statistics: `RecordDetailsSheet.kt:668-669`. That sheet also opens *from the entry page*, when a track row is tapped (`CartographyEntryReportScreen.kt:699-704`, `:722-737`).
  - `LabelledTiles` is shared with the track sheet (`EntryReportSummary.kt:59-99`, used at `RecordDetailsSheet.kt:424-428`).
  - The waypoint rows' open/closed state is hoisted at `ui/log/CartographyScreen.kt:476` (passed in as `openWaypointRowsState`, `CartographyEntryReportScreen.kt:310-312`).
  - Export: GPX reads `TrackPoint.altitude` directly (`domain/GpxCodec.kt:136`, `:147`), not `EntryReport`.
- **Map icons still show details after the table goes:** the entry map's bubbles are `MapBubbleLayer` with the entry's own waypoint snapshots (`CartographyEntryReportScreen.kt:564-579`). A waypoint bubble carries its name and MGRS (`ui/map/MapBubbles.kt:215-216`); its details sheet carries coordinates, "Created" time and track (`RecordDetailsSheet.kt:356-368`). **One figure exists only in the table:** distance walked from the start ("From start", `EntryReportSummary.kt:223-228`, computed at `EntryReport.kt:122` and `:178-187`). Removing the table removes it from the app. Inferred from grep of `distanceFromStartMeters`; no other reader.

## 2. Climb says "Not recorded" while the chart has values

**Trace, from the store to the screen:**
1. Points are read through `RoomTrackRepository`, which drops network-provider fixes at read time (`data/repository/RoomTrackRepository.kt:51-52`, rule `domain/NetworkProviderFix.kt:52`). Both Climb and the chart see the same filtered points.
2. **Climb:** `entryReportOf` (`EntryReport.kt:103-108`) runs `ComputeTrackStatisticsUseCase` on **each included track**, and returns `null` if **any one** track's gain is `null`. A track's gain is `null` unless at least one pair of *adjacent* points both carry an altitude (`ComputeTrackStatisticsUseCase.kt:71-78`, `:95`). So a track with 0 or 1 usable point, or one with no two adjacent heights, makes Climb "Not recorded" for the whole entry (`EntryReportSummary.kt:106`).
3. **Chart:** `heightProfileOf` (`EntryReport.kt:146-175`) **pools** all included tracks: it needs at least 10 points with a height and at least half of all points (`:86`, `:147-152`), then draws each track's heights and **silently drops a track with none** (`:167`).

**So the two disagree whenever one included track is short or empty and another is a real walk.** The chart pools and drops the empty one; Climb lets it veto the whole entry.

**Is that what happened on the owner's walks?** For Oct 10, very probably: the entry holds a separate track started and stopped at 2:44 PM (see section 3). A track that short has at most a point or two, and the service may have stored network fixes that the read seam then drops (inferred). It could not be confirmed without the phone's database. For Oct 7: **could not determine.** The planner's description doesn't say whether that entry holds more than one track.

**Which is wrong:** Climb's rule, for this case. A track with fewer than two usable points has no height change to miss. Treating it as "height unknown" mixes it up with a long walk that recorded no heights, which really is unknown. There is also a doc/code mismatch behind it: `domain/model/TrackStatistics.kt:9-11` says gain is `null` "when no recorded point on the track has an altitude", but the code returns `null` when no *adjacent pair* has one. That covers every one-point track, and any track whose heights never sit next to each other.

**Not decided here (stop):** the fix is a behaviour change the rulings don't cover. Options:
- (a) A track with fewer than two usable points adds nothing to Climb, rather than voiding it.
- (b) Climb pools across tracks, as the chart does.
- (c) Leave Climb alone until the barometer work replaces how it is computed.

Any of these also changes the Records track sheet's Climb tile, because both run on `ComputeTrackStatisticsUseCase` (`RecordDetailsSheet.kt:668`), unless the change is made only in `entryReportOf`.

## 3. The 2:44 PM Start and End at 0 ft

**From the code, it's a separate short recording, kept in the day's entry as its own track. It is not two sessions merged into one track. It is not a display artefact either, except that "0 ft" is not a height.**

- **"Start · Oct 10, 2026, 2:44 PM"** is the default name of an origin waypoint (`domain/AutoWaypointName.kt:10`, `:23`). It is created from the first GPS fix that passes the accuracy gate after Record is tapped. Its name carries the time that fix arrived (`ui/track/TrackRecordingViewModel.kt:997-1020`, `:1034-1043`).
- **"End · … 2:44 PM"** is created only in `TrackRecordingViewModel.stopRecording()`. It is named with the time of the stop and placed at the last gated GPS fix (`:700-718`; END is created nowhere else in main, by grep). `stopRecording()` is called by the record button's stop (`MainActivity.kt:657`), by a permission rollback (`MainActivity.kt:494`), or by a service halt (`TrackRecordingViewModel.kt:352-354`).
- So the pair means a recording started, got a GPS fix, and was stopped, all inside the minute 2:44. A later recording then started at 4:40 PM with its own Start.
- **Why both are in the entry:** a new entry includes **every** track and waypoint of that day by default (`ui/log/CartographyViewModel.kt:153-163`, `kept = true` for all candidates). The short track and its two markers came in with the real walk.
- **"0 ft"** is the table's **From start** column (`EntryReportSummary.kt:223-228`), not a height. It is 0 because each marker is at or before its own short track's first point (`EntryReport.kt:178-187`). The existing test fixture shows the same thing: Start reads "0 ft" (`app/src/test/.../ui/log/EntryDataScreensTest.kt:408`).
- **Why the stop came so soon (an accidental tap, a halt, or a deliberate test): could not determine** from the code. The phone's walk log or `ForagerFix` log for that minute would say.
- The waypoint table is going (RECORD -864). That removes the visible pair from the page. **The short track itself stays in the entry** and keeps affecting Climb (section 2) and the tiles. Whether a recording that short should be offered or kept by default is a separate question the rulings don't cover.

## 4. Barometer design proposal

Shape only. Every number below is a proposed operating limit, not a measured one, and is marked as such.

**Sensor and platform calls**
- `Sensor.TYPE_PRESSURE` via `SensorManager.getDefaultSensor`. `null` means no barometer: an explicit "unsupported", as `CompassProvider.heading` does (`domain/CompassProvider.kt:18-23`).
- Register with `registerListener(listener, sensor, samplingPeriodUs = 1_000_000, maxReportLatencyUs = 5_000_000)`: 1 Hz, batched so the processor is not woken per sample. `event.values[0]` is hPa, `event.timestamp` is elapsed-realtime nanoseconds.
- **The interface this project owns:** `domain/PressureProvider` with `val readings: Flow<PressureReading?>`. `PressureReading(hPa: Float, elapsedRealtimeNanos: Long)`. The Android implementation goes in `sensor/AndroidPressureProvider.kt`, next to the compass.
- Pressure-to-height is done in domain with the standard-atmosphere formula `h = 44330 × (1 − (p/1013.25)^(1/5.255))`, not `SensorManager.getAltitude`, so it can be tested headless. Only height *differences* are used, so the reference pressure doesn't matter.

**Operating limits (CLAUDE.md: a reported range is not a safe range)**
- **Rate:** 1 Hz requested, whatever `Sensor.getMinDelay()` offers. The track samples a fix about once a second at most (`domain/BatterySaver.kt:47`), and a walker's height doesn't change faster.
- **Plausible values:** accept 500–1100 hPa (sea level to about 5,500 m). Anything outside is dropped, and the drop is logged.
- **Pocket and vent spikes:** keep a 5-sample rolling median (5 s). Reject a median step implying more than 2 m/s of vertical speed; a walker on stairs is well under 1 m/s (proposed, not measured). A rejected sample is logged and doesn't move the reference. This covers short squeezes and a car's ventilation pulses (a few Pa to tens of Pa; 10 Pa ≈ 0.85 m).
- **Weather drift:** up to about 1 hPa an hour in unsettled weather (≈ 8 m/h; research table row "weather drifts it over tens of minutes"). Handled by re-anchoring, not by the spike filter.

**Re-anchoring to GPS**
- Height = barometric relative height + offset. The offset is set from GPS altitude, and the barometer supplies all short-term change.
- **Initial offset:** the median of (GPS altitude − baro height) over the first 60 s of GPS fixes with an altitude.
- **Ongoing:** move the offset slowly toward (GPS − baro), with a time constant of about 20 minutes. Short GPS wander averages out; weather drift gets corrected within the hour. Skip any GPS sample more than 30 m from the current fused height (proposed limits).
- **What counts as a good fix:** a GPS-provider fix (`FixProvider.mayAct`). **Not** reported horizontal accuracy: the owner's phone reports a constant 3.79 m on every GPS fix (`domain/LocationSampler.kt:23-27`), so it says nothing about quality. `Location.getVerticalAccuracyMeters()` is a candidate, but whether these phones report a meaningful one is **unverified** (device check, step 2).

**Fallback without a barometer (and for every track recorded before this)**
- **Smoothed GPS height:** a 5-point median to remove spikes like the owner's sharp drop, then a centred moving average over about 60 s of points. Run at read time on stored `altitude`, so it applies to old tracks too.
- Climb keeps the 4 m hysteresis on top of that.
- Pure function, no Android binding.

**What is stored, and where (stop: migration)**
- **Proposal:** one new nullable column `pressureHpa REAL` on `track_points`, through `MIGRATION_20_21` and `ForagerDatabase.version = 21`. Raw pressure, not a computed height: the height is then recomputed by the domain rule at read time, so the filter can change later without touching stored data. `altitude` keeps GPS height unchanged, so GPX export and Records' stored data are unchanged.
- **Its reader:** the new domain height function (below), feeding the entry page's Climb and chart. That honours "a column with no reader is not added" (CLAUDE.md, Room/DataStore pitfall).
- **Per-point attachment:** in `TrackRecordingService`, where a fix becomes a point (`service/TrackRecordingService.kt:213`, `:232-240`), the latest filtered pressure at most 2 s old goes onto the candidate; otherwise `null`. This adds a field to `TrackPoint` (`domain/model/TrackPoint.kt:27-33`), to `toTrackPoint()` (`domain/LocationTracker.kt:70-78`), and to the entity and mapping.
- **Alternative to put to the planner:** a separate table of raw pressure samples keyed by track and time. More data, independent of the point sampler, but also a migration.

**How Climb and the chart read it**
- A new pure function, say `trackHeights(track): List<Double?>`. It returns barometric heights re-anchored to GPS when the track carries pressure, and smoothed GPS heights otherwise.
- `entryReportOf` would read it for Climb and the chart, instead of `point.altitude`.
- **Stop:** `heightProfileOf` and `ComputeTrackStatisticsUseCase` are also read by the Records track sheet (`RecordDetailsSheet.kt:668-669`, `:678`). "Records are unchanged" (RECORD -864) means either new entry-only functions, with the entry page and the track sheet then showing different heights for the same walk (one tap apart, section 1), or the planner widens the ruling. Not chosen here.

**Battery**
- **Unmeasured.** The fusion research says sensor cost on modern phones is batched in a low-power hub, and that the S22's cost is unmeasured (`docs/navigation/2026-10-04-location-fusion-research.md:35`). Barometer parts are generally very low power next to GNSS, but that is not verified for either phone.
- Proposal: register only while a recording runs (inside the service's recording job). Measure on the device (step 5).

**Headless tests (domain free of Android, sensor behind the owned interface)**
- Pressure-to-height: 1013.25 hPa → 0 m; 898.75 hPa → about 1000 m.
- **Owner's case, synthetic:** a flat 2-hour walk, GPS altitude wandering 92–126 m (302–414 ft) with one sharp drop, pressure flat ± sensor noise. Height range within a few metres, Climb under one hysteresis step.
- Pocket spike: a 3 s, 30 Pa excursion on a flat walk adds no Climb and no chart step.
- Weather drift: pressure falling 1 hPa an hour for 3 hours, GPS flat. Fused height stays within the re-anchor tolerance of GPS, Climb near zero.
- Real climb: a 50 m staircase in pressure, GPS noisy. Climb ≈ 50 m within one threshold.
- Re-anchor converges to the GPS median; a GPS outlier more than 30 m out is ignored.
- Fallback: no pressure, GPS noise ± 15 m on a flat track. Smoothed range bounded, Climb ≈ 0. Old tracks (all `pressureHpa` null) take the fallback.
- A fake `PressureProvider` emitting `null` → the service stores `null` pressure, and the read uses the fallback.
- `toTrackPoint` carries pressure (the pattern of the speed fields, `LocationTracker.kt:62-69`).
- Migration 20→21 in `app/src/test/.../data/local/SchemaMigrationTest.kt`, its existing `migrate(...)` pattern (`:67` ff).

## 5. Existing tests touching the table or the chart

All paths under `app/src/test/java/com/zynergylabs/forager/app/`.

| Test | Today it asserts | After the change | Lost assertion? |
|---|---|---|---|
| `ui/log/EntryDataScreensTest.kt:377-390` "the report shows labelled tiles and the height profile's labels" | tiles (`:381-383`) and chart labels and tag (`:384-389`) | tiles unchanged; while hidden the chart must be absent; when it returns, values change if smoothing alters min/max (`509 ft` / `328 ft`) | **Yes, while hidden** (`:384-389`) |
| `ui/log/EntryDataScreensTest.kt:400-420` waypoint table times and From start | rows, times, distances, column headers | table absent | **Yes, removed whole**: the table is gone by ruling |
| `ui/log/EntryDataScreensTest.kt:423-440` touches show/hide coordinates | coordinate-row toggling | removed | **Yes**: feature gone |
| `ui/log/EntryDataScreensTest.kt:472-479` no heights → one line, Climb "Not recorded" | line tag and text; Climb tile | Climb part unchanged; the line hidden with the chart? (ruling unclear) | **Possibly** (`:476-477`) |
| `ui/availability/LeavingTheJournalFixesTest.kt:805-821` waypoint row stays open across a tab change | hoisted open-row state | removed, along with the hoisting at `CartographyScreen.kt:476` | **Yes** |
| `ui/log/CartographyEntryReportScreenTest.kt:183-184` | "Waypoints" heading and "Trailhead" shown | must invert to absent | **Yes** (inverted) |
| `ui/log/CartographyEntryReportScreenTest.kt:93` | "Waypoints" absent on a photo-only entry | still passes before and after: **suspect, covers nothing new** | no |
| `domain/EntryReportTest.kt:139-161` waypoint rows | `EntryReport.waypoints` | if the field is removed (no reader left), test removed | **Yes, if removed** |
| `domain/EntryReportTest.kt:70-108` Climb and profile rules | gain sum, null cases, limits | change only if section 2's fix or the new height source lands; `:87` "no heights" would change under option (b) | depends on the stop |
| `ui/log/EntryLabelsTest.kt:83-86` | `waypointCoordinatesLine` | the function's only main caller is the table (`EntryReportSummary.kt:233`); dead code if kept | **Yes, if removed** |
| `ui/log/TrackSheetDataTest.kt:161-185` | Records track sheet chart and Climb | unchanged if Records stays untouched | no |

Other hits are not affected: "Waypoints" as a layer or chip label in `CartographyEntryMapLayersTest.kt:135`, `RecordsFilterChipsTest.kt:206-232`, `AvailabilityScreenMapLayersTest.kt:69`.

## 6. What only a phone can confirm (numbered device check, for later; not run)

Run on the S22 test phone unless named otherwise. Cheapest first. Each step names its pass condition and evidence.

1. **Barometer present on the S22.** `adb shell dumpsys sensorservice | grep -i pressure`. Pass: a pressure sensor is listed. Evidence: the line, with vendor, range and resolution. (The S26 is already confirmed, RECORD -566 Amendment 1.)
2. **Vertical accuracy reported.** A one-minute outdoor log of `hasVerticalAccuracy()` and its value per GPS fix. Pass: present and varying. Evidence: the log. If it's constant or absent, re-anchoring uses the provider gate only.
3. **Entry page layout** after the UI change: open an entry with waypoints. Pass: map, offline toggle, four tiles; no Waypoints table; no Height chart while hidden; tapping a waypoint icon opens its bubble with name and MGRS, and Details shows the time. Evidence: screenshot kept off GitHub, described in words.
4. **Sensor stops when recording stops.** `adb shell dumpsys sensorservice` during and after a recording. Pass: Forager's pressure registration present only while recording. Evidence: both outputs.
5. **Battery.** A 1-hour recording with and one without the barometer, same route type. `adb shell dumpsys batterystats` per-app sensor and total. Pass: the barometer's share is reported and small next to GPS (no threshold set yet; the owner's call). Evidence: both readouts.
6. **The owner's case:** a flat walk, phone in a pocket, on the owner's own phone (the S26, which has a barometer), at least 45 minutes. Pass: the Height chart's highest minus lowest is under about 10 ft over terrain the owner calls flat, Climb reads under about 15 ft, and no sharp spike. Evidence: the entry page described in words; the GPX full record exported, for reading pressure against GPS height.
7. **A real climb**, a known hill or stairwell of known height. Pass: Climb within ± 10% of the known climb. Evidence: entry page figures; the known height and its source.
8. **Fallback.** A recording with the barometer unavailable (a phone without one, if available; otherwise a debug flag forcing the fallback). Pass: the chart draws from smoothed GPS, Climb is a figure not "Not recorded", no spike like the owner's. Evidence: entry page described in words.
9. **Car drive-off.** End a walk, drive off with the ventilation on, recording still running for a few minutes. Pass: no step in the chart beyond the road's real grade. Evidence: entry page described in words, and the logged spike rejections. *Observation, not a gate.*

## Stops for the planner (not chosen)

1. **Migration:** `pressureHpa` on `track_points`, `MIGRATION_20_21`, version 21; its reader is the entry page's height function. Or a separate samples table.
2. **Records unchanged vs shared code:** the chart rule and the Climb statistics are shared with the Records track sheet (section 1). Keep Records on raw GPS, with the entry page and the track sheet showing different heights one tap apart, or widen the ruling.
3. **Climb mismatch fix:** options (a), (b), (c) in section 2. Also the doc/code mismatch at `TrackStatistics.kt:9-11`.
4. **What "only the stats and map" covers:** the owner's words were "The only thing that should show in a journal entry page are the stats and map". The ruling removes only the waypoint table. The page also draws the Tracks rows, the Finds list, the Offline maps list, and the entry's text, tags and photos (`CartographyEntryReportScreen.kt:663-714`). Which of those stay is not ruled. In particular, the Tracks rows are the page's path to a track's details sheet.
5. **Climb tile while the chart is hidden:** it is computed from the same GPS heights the owner called wonky. The ruling keeps four tiles but hides only the chart.
6. **Lost assertions:** the tests in section 5 marked "Yes".
7. **From start:** the figure exists only in the table and disappears with it (section 1).
8. **The short track:** whether a few-second recording should be kept by default in a day's entry (section 3). Not covered by the rulings.
9. **Phone-only items:** section 6, all of it.

## What was not done

- No code was written. No Gradle build or test was run. No phone was touched.
- The causes on the owner's real walks (Oct 7 Climb; why the 2:44 recording stopped) were inferred from the code, not read from data.
- Barometer power and noise on these phones are not measured. Every limit in section 4 is proposed, not measured.
