# Altitude above sea level: report first (dispatch 2026-10-28-12, RECORD -1791)

Written by Hazel (Opus) on 2026-10-29 04:18 UTC, for Rowan Waymark. It is look-only: no app code changed, no phone touched, no Gradle run. The base is pre-main `d100a4776`, which I checked against the remote at the start and again at 04:18 UTC.

Positions stay off GitHub. My scripts, their inputs and their full output are on the laptop in `/home/zynergy-labs/s26-2026-10-28/analysis/altitude/` (`scripts/`, `ground-3dep-tile/`, `ground-srtm-tile/`, `outputs.txt`). Every figure below comes from `outputs.txt`. The report gives heights, counts and distances, never coordinates.

Marks: **read** (code, a file, or command output), **computed** (my scripts, on the copied data), **inferred**, **from memory** (platform knowledge I did not check against a source here).

## Summary

- **Sea-level conversion fixes the offset.** On the 10-28 walk, the strip's height read 24.1 m low. After conversion it reads 2.5 m low (computed, 71 points, against the S26's own trail heights).
- **Adding the barometer steadies the reading.** The spread drops from 3.3 m to 1.4 m. Of the steps between stored points, 95 % are now under 0.6 m, where today 95 % are under 2.8 m (computed).
- **Android's converter is `android.location.altitude.AltitudeConverter`, API 34.** Its model comes inside the platform, and nothing is downloaded (read, from the SDK and an Android 16 system image). Both test phones run Android 16 (inferred from records), so both have it.
- **The barometer is only read while a recording runs** (read). A live height on the strip needs it read whenever the map is open. That is a behaviour change for the owner to confirm.
- **The 10-24 walk can't judge the level.** The only trail data on the laptop that covers it has heights that disagree with the S26's own by 13 to 25 m on the 10-28 walk, which looks like canopy (inferred). For 10-24 I report only steadiness and shape.
- **Stored altitudes are ellipsoid heights.** So are the GPX export and the journal's highest and lowest labels, which therefore read about 21 m low. Changing that is a stop, and options follow.

## 1. Every reader of a fix's altitude

How I found them: `git grep -n -i altitude` and `git grep -n "\.altitude\b"` over `app/src/main` at `d100a4776`, then each hit's callers traced (read). Line numbers are at `d100a4776`.

**Where the value enters.** It enters at `location/AndroidLocationTracker.kt:130`, as `altitude = if (hasAltitude()) altitude else null`. One-shot fixes use the same rule at `location/AndroidLocationProvider.kt:36` and `location/AndroidLastKnownLocationSource.kt:51`. `LocationFix.Update.toTrackPoint()` copies it field for field (`domain/LocationTracker.kt:73`). Nothing anywhere converts it, so every reader below gets the ellipsoid height.

| # | Reader | Where (read) | What it does with the height | Should it read sea level after this change? |
|---|---|---|---|---|
| 1 | **Strip "Alt"** | `AvailabilityUiState.kt:415` (`liveAltitudeMeters = liveFix?.altitude`) → `CompactMapTopStrip.kt:100` → `AvailabilityMapControlsUi.kt:342-343`; also `CompassStripEveningLines.kt:51-52`; width at `CompassStripMeasurement.kt:149`, `AvailabilityCompactScaffold.kt:473`, `CompactMapTopStrip.kt:120` | shows the number as it arrives | **Yes**: sea level plus barometer, the owner's choice. |
| 2 | **Navigation card's elevation** | `NavigationReadout.kt:65` reads `fix.altitude` itself, not `liveAltitudeMeters` | shows the number, or "Elevation unavailable" | **Yes, the same value as the strip.** Today it has its own read, so the two could disagree after the change unless both read one source. |
| 3 | **Stored track points** | `service/TrackRecordingService.kt:220` (`fix.toTrackPoint()`), Room column `TrackPointEntity.kt:37` | stored as reported | **Stop.** See "Stored data" below. |
| 4 | **Start and End markers** (waypoints) | `ui/track/TrackRecordingAutoWaypoints.kt:72` (Start), `:107` (End); column `WaypointEntity.kt:23` | stored | Same stop as 3. No screen shows a waypoint's altitude: the only readers are GPX export (row 5) and the return's start point (row 8). |
| 5 | **GPX export** | `domain/GpxCodec.kt:147` (`<ele>` on each `trkpt`), `:166` (`<ele>` on each `wpt`), `:136` (`ele=` on the full-record extension) | writes the stored value | **Probably yes, as sea level**, since GPX `<ele>` is read as height above sea level by other apps (from memory). It depends on the stored-data choice. |
| 6 | **GPX import** | `GpxCodec.kt:234,250,265`; `GpxImportReader.kt:97,112` → `ImportGpxUseCase.kt:96` | stores a file's `<ele>` in the same column | **Not converted.** An imported file's `<ele>` is most likely sea level already (inferred), so converting it again would double-correct. Imported tracks can be told apart (`tracks.importedAtEpochMillis`). |
| 7 | **Climbing allowance** | `domain/ClimbAllowance.kt:73` → `barometricHeights`; GPS only sets the anchor (`BarometricHeight.kt:89-90`, `:97-103`) | uses height *changes* only | **No change needed.** The anchor's level cancels out of a climb, and -1689's "barometer only" rule stands. |
| 8 | **Return control's elevation difference** | `ComputeReturnToStartUseCase.kt:13-16` (start minus current), from `ReturnWatch.kt:273` and `TrackRecordingAutoWaypoints.kt:135`; text at `ReturnControlText.kt:47-49` → `returnControlDescription` → `TrailheadControls.kt:177` | **only a screen reader hears it**: it is the control's `contentDescription`, never drawn | **Optional.** A difference of two ellipsoid heights is nearly free of the offset (the geoid changes by about 0.01 m across this walk, computed), but each end carries the ±10 m noise. Fed from the steadied height it would be steadier. The owner can leave it. |
| 9 | **Journal Climb and Descent** | `ComputeTrackStatisticsUseCase.kt:64` → `trackHeights` (`TrackHeights.kt:18-19`) | changes only | No change needed. |
| 10 | **Journal height chart, highest and lowest labels** | `EntryReport.kt:161-177` → `EntryReportSummary.kt:126-127` (`formatWholeLength(profile.highestMeters …)`) | **shows absolute heights**, anchored to GPS | **Yes.** Today they read about 21 m (70 ft) low in western Oregon. It depends on the stored-data choice, or a read-time conversion. |
| 11 | **Declination** | `ui/map/TrueHeading.kt:129` → `AndroidDeclinationProvider.kt:24` (`GeomagneticField`) | the field model's height input | No change. The effect is hundredths of a degree per kilometre (`DeclinationProvider.kt:31-33`). |
| 12 | **MapLibre's location** | `ui/map/MapPosition.kt:173` | copied onto the platform `Location` MapLibre draws | No change. MapLibre doesn't draw height (inferred; not read in MapLibre's source). |
| 13 | **Locate-me** | written at `AvailabilityViewModel.kt:608` into `LocateMeStatus.Located.altitude` (`LocateMeStatus.kt:27`) | **nothing reads it**: no `.altitude` read of the status anywhere in `app/src/main` | No change. It is a field with no reader. |
| 14 | **"Heights recorded" count** | `RecordDetailsContent.kt:150`; `EntryReport.kt:151` | counts points with a height | No change. |

**Finds and photos carry no altitude** (read). The find and photo entities have no altitude column: `git grep altitude` under `data/local` finds only `TrackPointEntity` and `WaypointEntity`. An imported photo's EXIF read takes only latitude and longitude (`photo/FilePhotoStore.kt:201-204`).

**A premise in the code is wrong.** `BarometricHeight.kt:11` says "GPS gives height above sea level". It gives ellipsoid height, so the barometer's anchor, and with it the chart's absolute labels (row 10), sits about 21 m low here. The code is otherwise right for changes.

### Stored data: a stop, with options (not decided)

Today every stored track point and marker holds the ellipsoid height. GPX writes it out as `<ele>`, and a GPX import stores a file's `<ele>`, probably sea level, in the same column.

| Option | Old tracks | New tracks | GPX export | Cost |
|---|---|---|---|---|
| **A. Keep storing ellipsoid height; convert when read** (chart labels, GPX export) | converted like new ones, on a phone with the converter | as old | sea level wherever the phone can convert; on a phone without it, the export either leaves `<ele>` out or writes the raw height marked as ellipsoid. That is a sub-choice. | No migration. Every reader that shows an absolute height needs the converter at read time, which works offline from latitude and longitude. Imported tracks must be skipped. |
| **B. Add a sea-level column**, filled from now on | stay ellipsoid-only (or are back-filled by option A's conversion) | both heights | sea level when present | A Room migration and a new column, which per CLAUDE.md lands with its reader (export or chart) in the same change. |
| **C. Store sea level in the existing column from now on** | ellipsoid | sea level | mixed: old and new exports differ by about 21 m, and nothing in the file says which is which | No migration, but the column's meaning changes silently by date. |
| **D. Leave storage and GPX alone; change only the live screens** | unchanged | unchanged | ellipsoid, as today | Nothing. The journal's labels stay 21 m low. |

C mixes two datums in one column with nothing to tell them apart, so it is the one I'd advise against. Choosing between A, B and D is the owner's call.

## 2. The converter

**The API (read, from `platforms/android-37.0/data/api-versions.xml` in the laptop's SDK):**

- `android.location.altitude.AltitudeConverter`, since **API 34** (Android 14):
  - `addMslAltitudeToLocation(Context, Location)`, since 34;
  - `tryAddMslAltitudeToLocation(Location): Boolean`, since **35**.
- `Location.hasMslAltitude()`, `getMslAltitudeMeters()`, `getMslAltitudeAccuracyMeters()`, `setMslAltitudeMeters`, `removeMslAltitude`, all since 34.

The app's `minSdk` is 26 (`app/build.gradle.kts:265`), so phones on Android 8 to 13 have no converter.

**What it needs: no download** (read). The geoid model ships inside the platform. I pulled `framework-res.apk` out of the SDK's Android 16 system image on the laptop (`system-images/android-36/google_apis/x86_64`, build `BE2A.250530.026.F3`), read-only. It holds `assets/geoid_map/`: six 512 × 512 disk tiles of geoid height, one per S2 cube face, about 15 KB each, plus params (map level 9, `height = 193 × u − 107` m, stated RMSE 0.27 m).

I decoded that model myself (`scripts/geoid.py`). The pixel layout is inferred, and the decode was checked against known geoid features (from memory): the Indian Ocean low gave −107 m (known about −106), Hudson Bay −44.8 (about −45), Iceland +67.8 (about +65). The other pixel order gave nonsense. The model gives **−21.6 m at the 10-28 walk and −20.7 m at the 10-24 walk**. So sea level there lies about 21 m *below* the ellipsoid, and a sea-level height is the GPS height plus about 21 m.

**The phones (inferred from records, not read off the phones for this report):**
- S22 Ultra (SM-S908U): Android 16 (`docs/audits/2026-09-28-l0a-device-check-run-record.md:9`, `docs/navigation/2026-10-02-alerts-in-service-prebuild-report.md:326`).
- S26 Ultra: Android 16 (`docs/audits/2026-09-19-pr102-description.md:222`).

Both are API 36, at or above 35, so both have both methods. Samsung's framework carrying the same `geoid_map` assets as the emulator image is inferred. Reading `getprop ro.build.version.sdk` on each phone and making one conversion is a cheap first device step.

**How it fails (from memory of the platform docs; not checked against a source here):**
- `addMslAltitudeToLocation` may read from disk, so it must not run on the main thread. It throws `IOException` if the model can't be loaded, and `IllegalArgumentException` for a location with no altitude or a bad latitude or longitude.
- `tryAddMslAltitudeToLocation` doesn't block. It returns `false` when the data isn't loaded yet.
- Some phones may already fill `hasMslAltitude()` in the GNSS location itself (unverified).
- Below API 34, the class doesn't exist.

**Proposed interface the app owns (CLAUDE.md: vendor APIs behind an interface):**

```kotlin
// domain/SeaLevelHeight.kt — pure, no Android
sealed interface SeaLevelHeight {
    data class Known(val meters: Double, val accuracyMeters: Float?) : SeaLevelHeight
    /** The phone cannot convert: below API 34, or the model failed to load (logged once). Shown as "Alt unavailable". */
    data object Unsupported : SeaLevelHeight
    /** The fix carried no height (today's "no altitude" case). */
    data object NoHeight : SeaLevelHeight
}
```

- `location/AndroidSeaLevelConverter.kt` would be the one class that touches `AltitudeConverter`. It would:
  - use the location's own `hasMslAltitude()` value if the platform already set one;
  - otherwise call `addMslAltitudeToLocation` off the main thread;
  - return `Unsupported` below 34 or on `IOException`, logged once, never a raw height passed off as sea level.
- The tracker calls it in `toFix()` while the `Location` is still in hand (`AndroidLocationTracker.kt:126-136`). `LocationFix.Update` gains a `seaLevel: SeaLevelHeight` beside the raw `altitude`, which stays the phone's reported value.
- Tests use a fake converter. Whether Robolectric runs the real `AltitudeConverter` with its assets is unverified, so I'd plan one fake at the interface and one device step.

## 3. The barometer smoothing

**What exists.** `barometricHeights` (`BarometricHeight.kt:46-108`) already joins the two: barometer for changes, GPS for the level, through an anchor (median over the first minute, then a slow pull with an hour's time constant, ignoring GPS more than 30 m off). It looks at the whole track, centred on each point. That suits the journal, but it can't run live, where only the past is known.

**Proposal: the same rule, run forward in time,** in a new pure class (for example `domain/LiveHeight.kt`). It takes each fix's sea-level height and the latest barometer readings, and returns the height to show:

1. The barometer height is the standard-atmosphere height (`pressureAltitudeMeters`, `BarometricHeight.kt:111`), taken as the median of the last 60 s of readings, with the same 500 to 1,100 hPa limits.
2. The anchor starts as the median of (sea-level GPS − barometer) over the first 60 s. After that, each fix moves it by (difference × elapsed / 60 min), skipping GPS more than 30 m off. These are the constants already in `BarometricHeight.kt:156-184`, and nothing new is tuned.
3. Shown height = barometer height + anchor.

**The three cases the dispatch names:**

- **No barometer** (`PressureProvider.readings` emits `null`, `PressureProvider.kt:12-17`): the shown height is the converted GPS height as it arrives, with no smoothing. That is today's behaviour, minus the offset. Inventing a GPS smoother for the strip isn't backed by data here.
- **Stale fix** (GPS lost; the strip holds the last fix, `AvailabilityUiState.kt:320-327`): the anchor freezes, and the barometer goes on moving the height. Today the strip holds the last fix's height. Whether it should keep moving on the barometer alone is an owner question (Q3 below).
- **Weather pressure drift:** the hour-long anchor follows it. A strong 1 hPa an hour fall, about 8 m, is followed within about that much (`BarometricHeight.kt:173-176`). On both 10-24 recordings, the frozen-anchor error against the ground trended +3.9 and +3.7 m an hour over 2 h 20 min and 2 h 11 min (computed). That is consistent with mild drift, but the ground there isn't reliable (below), so it isn't a measurement of drift.

**The barometer only runs during a recording today** (read: `AppContainer.kt:225`, "read by the recording service while a recording runs"; the only collector is `TrackRecordingService.kt:196`). The strip shows height without a recording, so this proposal needs the barometer read while the map is in the foreground, beside the location collection (`AvailabilityLiveLocation.kt`). It is about one reading a second, batched for 5 s (`PressureProvider.kt:37-43`). Its battery cost isn't measured.

### Numbers on the stored walks

How I made the ground heights (computed, `scripts/ground.py`):
- I routed each track through a via point every 100 m with BRouter 1.7.10 and the app's `hiking-mountain.brf` (the same setup as the walk read, `use_dynamic_range=0`).
- Each stored point got the height of the nearest routed trail node within 10 m. Points with no node that close (off trail) were left out, and the counts below say how many were kept.
- Each shown value uses only the points up to it, as a live strip would. It runs on the stored points, not on the strip's once-a-second fixes, so the live steps would be smaller and more frequent.

Two sources of trail data, and they disagree:
- **The S26's own `W125_N45.rd5`** (run `usca-20261027T221400Z`, OSM 2026-10-26, from `routes/routes.properties`) has heights. Every routed node carries `<ele>` (computed), and the Pi's no-height builds carry none (Forager-cartography `docs/audits/2026-10-26-elevation-pilot.md`, "Without elevation, no track point has `<ele>`"). That they are USGS 3DEP bare-earth heights, above sea level, is inferred from RECORD -1720 and -1728. **It doesn't cover the 10-24 walk**: BRouter says "from-position not mapped". The file is a 1 MB area download.
- **The laptop's full `W125_N45.rd5`** (`from-flash/engine-spike/brouter/`, 2026-10-03) covers both walks. Its heights are most likely SRTM, a surface model that reads treetops in woods (inferred; I didn't check its build).
  - On the 10-28 walk the two disagree: ground 30.2 to 34.2 m from the S26 file, against 43.2 to 59.0 m from the laptop's.
  - The barometer follows the S26 file's shape (correlation 0.76) and not the laptop's (0.02).
  - **So the laptop file's heights can't judge the level of a woods walk.** For 10-24 I report steadiness and shape, not the level.

**10-28 S26 walk, 8 min, flat (4 m), 71 of 84 points matched, against the S26's own trail heights:**

| What the strip would show | Mean error | Spread (sd) | Worst | Step between stored points, 95th pct / largest |
|---|---|---|---|---|
| Today: raw GPS | −24.1 m (−79 ft) | 3.3 m | 31.7 m | 2.8 / 8.1 m |
| Sea level only | −2.5 m (−8 ft) | 3.3 m | 10.1 m | 2.8 / 8.1 m |
| **Sea level + barometer (the proposal)** | **−2.9 m (−10 ft)** | **1.4 m** | **6.3 m** | **0.6 / 4.1 m** |
| the same, anchor time constant 5 or 20 min | −2.5 / −2.8 m | 1.5 / 1.4 m | 6.3 m | 0.6 / 4.1 m |

The 4.1 m largest step is the second point, in the first minute while the anchor forms. After that the largest is 0.8 m. On this walk the time constant moves the mean by 0.4 m between 5 and 60 minutes, so the data give no reason to change the existing hour.

**10-24 hilly walk, against the laptop's trail heights (level not judged):**

| Recording | Matched | Spread today → sea level + barometer | Steps 95th pct / largest, today → proposal | Correlation with ground: barometer / GPS |
|---|---|---|---|---|
| A, 140 min | 281 of 457 | 7.3 → 5.8 m | 5.4 / 9.6 → 1.5 / 6.0 m | 0.91 / 0.84 |
| B, 131 min | 428 of 678 | 12.9 → 13.6 m | 3.4 / 15.8 → 1.1 / 15.8 m | 0.85 / 0.90 |

- The steadying holds on both recordings.
- On B, the spread against this ground doesn't improve. The sea-level GPS and the barometer height agree to a 5.6 m spread there, while both differ from the laptop's ground by about 13 m. That points at the ground, not the method (inferred).
- B's 15.8 m largest step is its first point, which has no pressure. The others above 5 m are at gaps of 68 to 123 s between stored points, so they are real movement between points, not jumps.
- The mean errors against this ground (−20.1 m on A, −0.8 m on B, in `outputs.txt`) aren't a judgement of level, for the reason above.

**What these samples can't show:**
- the 10-28 walk is flat and short, so it can't test hill tracking or weather drift;
- 10-24 has no trusted ground;
- neither stored the vertical accuracy, so none was used.

A woods walk on trail data that carries the S26's kind of heights would test the level on a hill.

## 4. Words on screen

**Today (read):** `ELEVATION_UNAVAILABLE_TEXT = "Elevation unavailable"` (`NavigationHudText.kt:55`). It shows:
- on the strip whenever the shown height is null, with no fix or with a fix that carries no height (`AvailabilityMapControlsUi.kt:343`, `CompassStripEveningLines.kt:52`);
- on the navigation card for a fix that carries no height (`NavigationReadout.kt:65`; with no fix that row isn't drawn).

It wears no "Alt" label, because a status names itself (`NavigationHudText.kt:54`, `AvailabilityMapControlsUi.kt:383-385`).

**The owner's "Alt unavailable"** is for a phone that can't convert to sea level. It too would wear no extra label, so it never reads "Alt Alt unavailable".

**How the two sit together** is a stop, because the choice changes existing words:
- **(a) One phrase:** "Alt unavailable" replaces "Elevation unavailable" everywhere. The walker sees one phrase for "no height to show", whatever the cause. It is shorter on the 360 dp strip.
- **(b) Two phrases:** "Alt unavailable" for a phone that can't convert, "Elevation unavailable" for a fix without a height. The walker sees two phrases that mean the same thing to them.

Under "Don't make me think" (CLAUDE.md), (a) is the simpler one. I added no other words.

## 5. Tests: what fails first, and through which entry point

Each test below fails on `d100a4776` for the reason named, before any code is written.

1. **The strip shows sea level.** Entry: `AvailabilityViewModel`'s live location collection (`AvailabilityLiveLocation.begin()`), fed by a fake `LocationTracker` and a fake converter that adds 21.6 m. The test reads the strip's `COMPASS_STRIP_ELEVATION_TAG` text in the composed screen. A fix of 7.9 m should show "Alt 97 ft". Today it shows "Alt 26 ft" (fails: raw read at `AvailabilityUiState.kt:415`).
2. **Unsupported reads "Alt unavailable".** Same entry, with the converter returning `Unsupported`. Today it shows the raw number.
3. **The navigation card and the strip agree.** Same state, both read. Today the card reads `fix.altitude` on its own (`NavigationReadout.kt:65`), so it would fail as soon as test 1 is made to pass at the state.
4. **The barometer steadies the height.** `LiveHeight`, pure, on synthetic fixes shaped like the 10-28 walk: ±10 m GPS noise on a flat walk, with steady pressure. The shown steps stay under about 1 m after the first minute, where raw GPS steps reach 8 m. A second case: a 0.12 hPa fall (about 1 m up) with steady GPS raises the shown height by about 1 m within the 60 s window. The walk data are synthetic, never the stored walk (the dispatch keeps walk data off GitHub).
5. **No barometer.** A fake `PressureProvider` that emits `null`. The shown height equals the converted GPS height, fix for fix.
6. **The barometer starts with the map, not only with a recording.** Through the screen's entry with no recording, the fake `PressureProvider` is collected. Today it isn't (`TrackRecordingService.kt:196` is the only collector).
7. **The Android adapter** (`AndroidSeaLevelConverter`): a `Location` that already `hasMslAltitude()` is used as is; below API 34 the result is `Unsupported`. Robolectric with `@Config(sdk = …)`. Whether Robolectric runs the real conversion with its assets is unverified, so the real conversion is a device step.

Tests for stored data and GPX wait for the owner's choice in section 1. Each new test gets a revert check under CLAUDE.md's rules.

## 6. File sizes (modular rule)

Line counts at `d100a4776` (`wc -l`, read):

| File | Lines | Touched how |
|---|---|---|
| `location/AndroidLocationTracker.kt` | 162 | `toFix()` calls the converter |
| `domain/LocationTracker.kt` | 96 | `LocationFix.Update` gains `seaLevel` |
| `ui/availability/AvailabilityLiveLocation.kt` | 102 | collects pressure beside fixes, feeds `LiveHeight` |
| `ui/availability/AvailabilityUiState.kt` | 425 | `liveAltitudeMeters` becomes the live height (its readers then need no edit) |
| `ui/availability/NavigationReadout.kt` | 249 | reads the same live height |
| `ui/availability/NavigationHudText.kt` | 102 | the words (section 4) |
| `ui/availability/AvailabilityMapControlsUi.kt` | 525 | only if (b) in section 4 |
| `ui/availability/CompassStripEveningLines.kt` | 139 | only if (b) |
| `ui/availability/CompassStripMeasurement.kt` | 202 | only if (b) |
| `AppContainer.kt` | 535 | wires the converter |
| `domain/BarometricHeight.kt` | 185 | its doc's "above sea level" premise corrected; the constants shared with `LiveHeight` |
| new `domain/SeaLevelHeight.kt`, `domain/LiveHeight.kt`, `location/AndroidSeaLevelConverter.kt` | each well under 400 | new |

- `AvailabilityViewModel.kt` is at **797** lines and `AvailabilityCompactScaffold.kt` at **1,440**. The plan touches neither: the pressure collection goes in `AvailabilityLiveLocation.kt`, and the scaffold reads `liveAltitudeMeters`, whose name stays.
- If a coder finds the ViewModel must change, it needs the split first (over 800 before adding).
- The stored-data options add `GpxCodec.kt` (301), `EntryReport.kt` (195), and for option B the entities and `Migrations.kt`.

## Owner's questions, in plain behaviour

1. **Saved walks and exports.** Today a saved walk and its GPX file keep the phone's raw height, about 70 ft too low here. Pick one:
   - A: keep saving the raw height, and have the journal and exports show sea level;
   - B: save both from now on;
   - C: save sea level from now on, so old and new walks differ;
   - D: leave saved walks and exports as they are, and fix only the live screens.

   (C is the one I'd advise against.)
2. **"Alt unavailable".** On a phone that can't convert, the strip will say "Alt unavailable". Today the strip says "Elevation unavailable" when a reading has no height. Pick one:
   - (a) one phrase, "Alt unavailable", for both;
   - (b) keep both phrases.
3. **When GPS drops out.** When the phone loses GPS for a while, should the height keep moving with the barometer, or freeze at the last reading as it does now?
4. **The barometer when not recording.** To steady the height on the map when no recording runs, the app reads the barometer whenever the map is open, about once a second. Is that all right?
5. **The Return control's spoken height difference.** It is heard only with a screen reader. Should it use the same steadied height, or stay as it is?

## Disclosure

**Confirmed (read or computed):**
- every altitude reader and line in section 1, at `d100a4776`;
- no sea-level conversion anywhere in `app/src/main`;
- the barometer is read only during a recording;
- the API levels of `AltitudeConverter` and the `Location` sea-level methods (SDK `api-versions.xml`);
- the geoid model ships in the Android 16 framework, with nothing downloaded;
- the decoded geoid heights at the two walks (−21.6 m, −20.7 m), checked against known features;
- the S26's trail data carries heights, and doesn't cover the 10-24 walk;
- the two trail-data files disagree by 13 to 25 m on the 10-28 walk;
- every simulated figure in section 3;
- the file sizes.

**Inferred:**
- both test phones are on Android 16 (from earlier records, not read off the phones now);
- Samsung's framework carries the same geoid assets;
- the S26 trail heights are 3DEP above sea level;
- the laptop trail heights are SRTM and read canopy;
- MapLibre doesn't use height;
- an imported GPX `<ele>` is sea level.

**From memory (not checked against a source here):**
- `getAltitude()` is defined as ellipsoid height (the −2.5 m residual after conversion strongly supports it);
- the converter's threading and its failure modes;
- GPX readers treat `<ele>` as sea level;
- the reference geoid features used to check the decode.

**Could not determine:**
- whether Robolectric can run the real converter;
- whether either phone already puts a sea-level height on its GNSS fixes;
- the barometer's battery cost while the map is open;
- weather drift on these walks, separated from ground error;
- live once-a-second behaviour, since only stored points exist.

**Premises that were wrong:**
- `BarometricHeight.kt:11` says GPS gives height above sea level; it gives ellipsoid height.
- The dispatch's "trail data's ground heights" for the 10-24 walk don't exist on the S26's trail data. The only file covering it has heights unfit for a woods walk's level, so the 10-24 numbers judge steadiness only.
- The Return control's elevation line isn't on screen; only a screen reader speaks it (`TrailheadControls.kt:177`).

**Decided beyond scope:**
- I read the Android 16 emulator system image (read-only, with `debugfs`) to get the platform's geoid model, and decoded it with my own script.
- I read the Forager-cartography elevation-pilot doc from GitHub.
- I used the laptop's 2026-10-03 trail data for the 10-24 walk.
- The analysis is kept in the walk folder on the laptop, not committed.
- No row was added to `docs/audits/README.md`, since the dispatch names only this report and the index is a serialization point (CLAUDE.md), following the walk read.
