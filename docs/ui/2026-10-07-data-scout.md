# Data scout: where the app shows data as raw lines (UI sweep 2)

Dispatch 2026-09-28-653 (RECORD intent -653, preserved as `prompts/preserved/2026-10-07-07.md` on
`records-after-173`). Written 2026-10-07 (UTC) on branch `data-scout`, cut from `origin/main` at
`aa79f25a` (PR #189), which is the base the dispatch assumed. This is a read-only survey: no code
changed, no Gradle run, no phone or emulator. App paths are relative to
`app/src/main/java/com/zynergylabs/forager/app/`.

**How this was gathered.** I read the journal entry editor and report and the Withhold flow
myself. Three read-only sub-agents surveyed (1) Records, Finds, waypoints and the trip planner,
(2) the List and Seasonal tabs, Settings, Backup, Crash logs and offline maps, and (3) the map
surface. I re-read a sample of their citations against the tree and each one held:

- `RecordDetailsSheet.kt:377,394-396`
- `UnitSystem.kt:82,92`
- `OpenMeteoWeatherProvider.kt:149-161`
- `NavigationHud.kt:645` with `NavigationReadout.kt:16-19`
- `JournalEntriesOnMap.kt:27`
- `FindsGalleryScreen.kt:239`
- `AvailabilityResultsUi.kt:583`

Every other sub-agent citation is a reading I have not repeated myself. Line counts are counts of
lines on screen that show a value (a number, date, coordinate or count) rather than a control or
fixed text.

**The dispatch's citations, checked.** Every place it names exists, with two path corrections:
`ReturnToRoutePill.kt` and `WaypointNavigateControls.kt` live in `ui/availability/`, and the
fan-out code lives in `ui/map/fanout/`. The "List" and "Seasonal" tabs are `ListTab` and
`SeasonalTab` in `ui/availability/AvailabilityResultsUi.kt` (`CompactTab.LIST`,
`AvailabilityNavigationUi.kt:86`).

## In plain words

- **The app computes a lot and shows little of it.** A walk's elevation gained and lost, its
  average speed, and how much of it had a height reading are all worked out every time its details
  open, and none of them reach the screen. Height is stored on every track point, so a height
  profile could be drawn, but nothing draws one.
- **The journal entry report and editor are lists of loose values.** Each track shows as
  "0 ft · 0m", where the "m" means minutes but sits next to a distance and reads as metres. Each
  waypoint shows as four-decimal coordinates. Finds read "Find on 2026-10-07", a raw date.
- **Withhold puts one card on screen for every item from the day.** One recorded walk is already
  three cards: the track, its automatic Start waypoint and its automatic End waypoint. On top of
  that come one card per dropped waypoint, per find, and per offline map that covers the walk.
  Every card starts out kept. The code does already know which waypoints belong to which track,
  so grouping is possible from data the app has.
- **The same few raw values recur everywhere without labels.**
  - Compass heading in degrees ("123° SE").
  - MGRS grid references.
  - Four-decimal latitude and longitude.
  - Altitude.
  - Track "points" counts.
  - Tile counts.
  - ISO dates ("2026-09-12").
- **Durations and dates are written four different ways.** Durations appear as "1h 12m",
  "1 h 12 min", "6 min" and "h:mm". Dates appear as ISO ("2026-09-12"), "MMM d", "MMM d, yyyy" and
  "EEE, MMM d, yyyy".
- **There is one chart in the whole app.** It is the Seasonal tab's bar chart, hand-drawn with no
  axes and no values on the bars. The app has no chart or table library. It does have one
  label-and-value row layout, `DetailField`, in `ui/log/RecordDetailsSheet.kt:502`.

## Count per screen

| Screen or panel | Raw-value lines |
|---|---|
| Journal entry editor (Withhold list) | 1 date header + 1 subtitle per card; cards = finds + tracks + (2 + dropped) waypoints per track + covering regions |
| Journal entry report (below the map) | 1 per kept find, track, waypoint and region (+ ISO date header, tags line) |
| Journal list card | 1 stats row (labelled counts; "N tracks · dist · dur") |
| Records, All logbook | 1 per day header + the rows below |
| Recorded track row | 2 (title, "N points") |
| Track details sheet | 6 + 1 per dropped waypoint |
| Waypoint row | 2 (unlabelled MGRS, unlabelled decimal pair) |
| Waypoint details sheet | 5 |
| Offline region row | 2 (one line packs 5 values) + zoom paragraph |
| Offline region details sheet | 6 |
| Offline maps picker / progress / budget | 4 / 1 / 1 |
| Finds gallery tile | 1 (ISO date) |
| Find report | 3 + about 25 "Label: value" lines |
| Find editor | 2 |
| Trip planner, planned trip row | 3 |
| Trip windows card | up to 6 per window |
| List tab | 2 + 1 per species (and an unlabelled bar) |
| Seasonal tab | about 10 to 12 + an unlabelled chart |
| Settings | 1 (build label) |
| Backup | 1 to 2 |
| Crash logs | 1 per file; the detail view is the whole raw file |
| Diagnostics (debug builds only) | 1 + 2 per file + raw log text |
| Navigation HUD | 5 of up to 6 lines (+ sundown line) |
| Compass strip | 1 line holding 3 unlabelled values (+ sundown line) |
| Sundown line | 1 (labelled, 2 to 3 values) |
| Map bubbles | about 9 across the kinds |
| Map legend / Layers sheet | 2 per layer / 1 per layer |
| Journal entries chip menu | 1 per entry (ISO date) |
| Return pill, exit prompt, fan-out, navigation camera | 0 |
| Live recording on the map | 0 visible; 1 in the screen-reader text of the Return button |

## The five the owner would notice most

1. **The journal entry report below the map.** "Recorded track" with "0 ft · 0m", and Start/End
   waypoints with raw coordinates. This is the owner's own "raw data dump" example (section A2).
2. **The Withhold list.** One card per item, every one pre-kept, a walk's Start and End shown as
   separate cards, and no grouping (sections A1 and A3).
3. **The track details sheet.** It stores and computes elevation gain and loss, speed and height
   coverage, then shows only distance, duration and a raw "Points" count. No elevation profile
   (section B2).
4. **The navigation HUD and compass strip.** Unlabelled "123° SE", a "Turn 350°" that means a
   slight left, an unlabelled MGRS string, an unlabelled altitude, and a large distance figure
   that is sometimes the route and sometimes the straight line with nothing to say which
   (section E1).
5. **Seasonal and trip windows.** Rainfall and soil readouts are prose lines. "Shallow soil
   moisture: <n.nn> m³/m³" (format `%.2f`) has no plain meaning. The one chart has no axes or values, and its
   buckets have unequal widths (sections D2 and B6).

Choosing among these is the owner's. This list is ranked by how visible each is on screens the
owner has used and described, and is my judgement, not a measurement.

---

## A. Journal (Cartography) entries

### A1. Entry editor and the Withhold list (`ui/log/CartographyEntryEditScreen.kt`)

**What the user sees now.**

- **Header:** the date as `entry.date.toString()`, so ISO "2026-10-07" (`:220`).
- **Two text fields:** "Your own account (optional)" and "Tags (optional, comma-separated)"
  (`:250`, `:262`).
- **Then, in order:** Photos, Finds, Tracks, Waypoints, Offline Regions (`:269-284`). Each item is
  its own `Card` (`DecisionRow`, `:603-632`).
- **Each card** shows a title line and one subtitle line of values:
  - **Find:** "Find on 2026-10-07" (ISO `LocalDate`), with the user's identification as the
    subtitle (`:467`, `:470`).
  - **Track:** the name or "Recorded track", with `trackSubtitle` as the subtitle (`:476-508`,
    `:662-669`). The subtitle reads `"<distance> · <duration>"`, for example "0 ft · 0m" or
    "2.3 mi · 1h 12m".
    - The duration's "m" means **minutes** and sits next to a distance, so in metric it reads
      "412 m · 48m" (two m's meaning metres and minutes).
    - An exclusion note may be appended: " · no usable points" or "N more not shown (network
      fixes)" (`:642-645`, `domain/NetworkProviderFix.kt:117-122`).
  - **Waypoint:** its name, for example "Start · Oct 7, 12:08 PM" (`domain/AutoWaypointName.kt:15-24`),
    with the subtitle `"%.4f, %.4f"`: unlabelled decimal latitude and longitude (`:517`, `:525`).
  - **Offline region:** its name, with the subtitle `formatDistanceKm(radiusKm)`, a bare radius
    such as "6 mi" with no "radius" label (`:543`, `:547`).
- **Buttons:** a kept card has one "Withhold" button. A withheld card is dimmed to 50% and struck
  through, with "Keep". A new (undecided) card has both buttons and a small "New" label
  (`:603-632`).

**The data.**

- **`TrackDecision`:** `distanceMeters: Double` (metres), `durationMillis: Long` (first to last
  point time), `pointCount: Int` (`domain/model/CartographyEntry.kt:123-130`). These are
  snapshotted at decision time and recomputed whenever the entry is opened
  (`CartographyViewModel.kt:203-221`).
- **`WaypointDecision`:** name, `lat`/`lng` in degrees (`:133-139`).
- **`FindDecision`:** `foundOn: LocalDate`, `ownIdentification`, `hasPhotos` (`:114-120`).
- **`OfflineRegionDecision`:** name, centre, `radiusKm: Int` (`:142-149`).
- The live candidates are the day's `DerivedTrip`, holding full `Track`s with all their points
  (`domain/model/DerivedTrip.kt:19-25`).

**What a summary could be built from.**

- The live track's points carry altitude, time, accuracy and speed
  (`domain/model/TrackPoint.kt:27-33`).
- `ComputeTrackStatisticsUseCase` is already called for each candidate row (`:499`), and it returns
  elevation gain and loss and average speed, which the row drops.
- The snapshot (`TrackDecision`) does not store gain or loss. A kept track that is later deleted
  from Records keeps only distance, duration and point count, plus its saved path
  (`KeptTrackPathRepository`) for drawing.

**Constraints.**

- **Units:** distance follows the unit setting, through `formatDistanceMeters`
  (`domain/model/DistanceUnit.kt:71-78`). Do not swap in `formatDistanceKm`; the comment at
  `:649-661` records why ("0 mi" for a real 92 m track).
- **Honest copy:** the empty report message already gives a next step
  (`CartographyEntryReportScreen.kt:718-721`).

### A2. Entry report (`ui/log/CartographyEntryReportScreen.kt`)

**What the user sees now.**

- **Header:** the ISO date (`:404`).
- **Map:** the entry map at 4:3, which opens fullscreen on tap.
- **Offline toggle:** an "Offline map" switch row when a region covers the entry (`:584-611`).
- **Text:** the user's own text, then `"Tags: a, b"` (`:629`), then photos.
- **Kept items, by kind** (`ReportItemsSection`, `:796-806`). Each item is a title line and a grey
  subtitle line, with no cards:
  - **Finds:** "Find on 2026-10-07" with the identification (`:651`).
  - **Tracks:** "Recorded track" with `trackSubtitle` plus the exclusion suffix (`:655-661`). This
    is the owner's "0 ft · 0m".
  - **Waypoints:** "Start · Oct 7, 12:08 PM" with `"%.4f, %.4f"`, unlabelled coordinates
    (`:665-667`).
  - **Offline Regions:** the name with a bare radius (`:670-673`).
- **Raw-value lines:** one per kept item, plus the date and the tags.
  - A single walk with nothing else reads as at least 3 value lines under the map: track, Start
    and End.

**The data.** The report reads the same snapshot fields as the editor. Nothing else is computed
for it.

**Missing summaries.**

- **A per-entry total** of walked distance, time out, elevation gain, finds count and species.
  - The journal list card already builds a counts row (`ui/log/CartographyEntryCard.kt:269-283`):
    "N finds", "2 tracks · 5.4 km · 2h 10m", "N waypoints", "N offline maps".
  - The species chips come from `entrySpecies` (`:256-257`).
  - The report itself shows neither.
- **The walk's start and end times** are known (`Track.startedAtEpochMillis`/`endedAtEpochMillis`,
  `domain/model/Track.kt:50-51`) but appear only as text inside the auto-waypoint names.

**Phone only.** Why the owner's entry showed "0 ft · 0m" cannot be settled from code.

- The figure is recomputed from the live track on every open (`CartographyViewModel.kt:212`), so
  it means the track as stored had zero distance and under one minute of time. A deleted track
  would instead keep its last snapshot.
- One possible cause is a walk still being recorded when it was read. That is an inference.

### A3. Withhold, in depth

**Where candidates come from.**

- `GetDerivedTripUseCase` (`domain/GetDerivedTripUseCase.kt:38-58`) reads four lists for the entry's
  local day: finds by `foundOn` (drafts included), tracks and waypoints by time, and offline regions
  downloaded that day.
- The editor's region list is a different set, `GetTripReportOfflineRegionsUseCase`
  (`domain/GetTripReportOfflineRegionsUseCase.kt:22-37`): every stored region whose tiles cover any
  find, track point or waypoint from that day, whenever it was downloaded.

**The three states.** `DecisionState` is KEPT, WITHHELD or UNDECIDED (`CartographyEntryEditScreen.kt:574`).

- A decision is stored only once made. "Not yet decided" is the absence of a row
  (`domain/model/CartographyEntry.kt:40-48`).
- **Starting an entry marks every one of that day's candidates as kept**
  (`CartographyViewModel.kt:146-157`).
  - So on a new entry nothing is "New". Every card shows "Withhold", and the user's job is to take
    things out.
  - "New" appears only for records that arrive after the entry was started, such as a later walk
    the same day or a GPX import dated that day.
- **Order:** `mergeDecisionRows` lists decided rows first, then new ones (`:560-572`).
  - Each set call removes the decision and appends the changed one at the end
    (`CartographyViewModel.kt:321,330,339,348`, `filterNot { … } + decision`).
  - Inferred from code, not seen on a phone: tapping Withhold or Keep moves that card to the bottom
    of its section's decided rows. The Room reads that load decisions have no `ORDER BY`
    (`data/local/CartographyEntryDao.kt:27-39`), so order after reload is SQLite's row order,
    unverified.
- **Draft vs saved:** a draft saves every tap at once. A saved entry collects taps until Save,
  with a confirm dialog (`CartographyViewModel.kt:704-719`; editor `:298`, `:320-360`).

**What withholding changes.**

- A withheld item is left out of:
  - the entry's report lists (`CartographyEntryReportScreen.kt:651-672`)
  - the entry's map: `GetCartographyEntryMapDataUseCase` filters on `kept`, and is the only builder
    of entry map data "by construction" (`domain/GetCartographyEntryMapDataUseCase.kt:54-60,77-109`)
  - the Maps tab highlight when the entry is shown on the map
    (`domain/GetJournalEntryHighlightsUseCase.kt:71-74`)
  - the journal list card's counts, species chips and thumbnail (`CartographyEntryCard.kt:256-283,348-371`)
  - "Show on map" eligibility (`CartographyEntry.kt:91-93`)
  - the entry's offline-region pick (`domain/GetCartographyEntryOfflineRegionUseCase.kt:36`).

**What withholding does not change.**

- **The record itself:** it is not deleted and stays in Records. The delete dialog says the same
  (`CartographyEntryEditScreen.kt:309`).
- **The Maps tab's own layers:** they still draw every record.
- **GPX export:** it exports tracks from Records (`export/TrackGpxExporter.kt`, `ui/track/TrackExportPanel.kt`).
  It is not tied to entries.
- **The journal backup:** it copies the whole decision tables, withheld rows included
  (`data/backup/JournalTables.kt:51-81`).
- **Sharing:** the app has no share or export of a journal entry (no `ACTION_SEND` outside crash
  logs, GPX export and import).
- **Meaning:** Withhold means "not part of this day's account", not "private" or "hidden". That is
  the privacy meaning to keep in any redesign.

**How many rows a real long walk produces.** Cards on a fresh entry:

```
finds + tracks + waypoints + covering offline regions
```

- **Auto waypoints:** each recorded track makes two of its own, Start
  (`TrackRecordingViewModel.kt:912-920`) and End (`:618-627`), when a fix passed the accuracy gate.
- **Dropped waypoints:** each adds one (`TrackRecordingViewModel.kt:1002`).
- **GPX imports:** an import adds one waypoint per waypoint in the file, dated by its own time or
  the track's start (`domain/ImportGpxUseCase.kt:90-102`).
- **Worked example:** one walk, five dropped waypoints, six finds, one region gives
  6 + 1 + (2 + 5) + 1 = **15 cards**.
- I have no count from a real entry; that needs the phone.

**What the code would allow, without choosing.**

- **Grouping waypoints under their track.** `Waypoint.trackId` and `Waypoint.designation`
  (ORIGIN/END) are stored (`domain/model/Waypoint.kt:30-32`, `WaypointEntity.kt`).
  - The decision snapshot (`WaypointDecision`) does not carry them, so the group needs the live
    waypoint, which the editor already has as a candidate.
  - For a waypoint whose record was deleted, the group is unknown.
- **Grouping by kind** already exists as sections. Each section could carry a count and a
  section-level choice (keep all / withhold all) built on the same per-item calls.
  - The ViewModel takes one id at a time (`CartographyViewModel.kt:315-349`), so a group action
    would be a new function, or a loop that saves once per item for a draft.
- **New items first, or set apart.** The UNDECIDED state is already known per row.
- **Picking on the map.** Every drawn item on the entry map keeps its record id and kind
  (`GetCartographyEntryMapDataUseCase.kt:62-65`, `RecordPoint`/`RecordPolyline`/`RecordRegion`).
  - Taps already reach `onFeatureTap` and open a bubble (`CartographyEntryReportScreen.kt:476-544`).
  - Today the entry map draws kept items only, so map-based picking would need withheld items
    drawn too, which the "unreachable by construction" rule above was written to prevent. That
    tension is the owner's to rule on.
- **A summary-first view.** For example "Keeping 1 walk, 6 finds, 7 waypoints" with details on
  demand. The counts are what `entryStats` already computes.

---

## B. Records, Finds, waypoints, trip planner (sub-agent 1)

### B1. Records, All logbook (`ui/log/RecordsLogbookList.kt`, `RecordsLogbook.kt`)

- **Day header:** `"EEE, MMM d, yyyy · N record(s)"` (`RecordsLogbookList.kt:235,319`).
- **Filter chips:** a bare count after each label (`RecordsFilterChips.kt:112`).
- **Missing:** a per-day summary (distance walked, finds, species). The data is in memory.

### B2. Recorded track row and track details sheet

**Row** (`ui/track/TrackExportPanel.kt:141-216`):

- The title is the name, or the start time `"MMM d, yyyy, h:mm a"` (`:239`).
- The subtitle is `"N points"`, or `"Imported · <time> · N points"`.
- A 40 dp path thumbnail with no scale.
- The row shows no distance or duration, though the map bubble and entry editor show both for
  the same track.

**Sheet** (`ui/log/RecordDetailsSheet.kt:365-422`) is labelled `DetailField` rows:

- Started
- Ended
- Imported (for an imported track)
- Distance
- Duration, which is "2h 10m" (`:539-547`), measured first to last point, moving or not
- Points, a raw count
- an optional network-fix note
- a 96 dp thumbnail with start, end and waypoint dots
- "Waypoints on this track", one row each with a full date-and-time.

**Computed and not shown.** `ComputeTrackStatisticsUseCase` is called at `:377`. Only distance and
duration are read. These are dropped:

- `elevationGainMeters`
- `elevationLossMeters`
- `averageSpeedMetersPerSecond`
- `pointsWithAltitude` (`domain/model/TrackStatistics.kt:13-36`)

**Also available.**

- `movingPace()` (`domain/MovingPace.kt:98`), whose only caller is the return-time estimate.
- Unit-aware formatters exist: `formatWholeLength` and `formatElevationChange`
  (`domain/model/UnitSystem.kt:82,92`). The second is used only in a screen-reader string
  (`AvailabilityMapControlsUi.kt:612`).

**Stored per point** (`data/local/TrackPointEntity.kt:32-41`):

- altitude (metres, nullable)
- accuracy
- time
- speed: null before schema 15, for network fixes and for GPX imports

So an elevation-over-distance or over-time profile and a pace profile are buildable. No profile
code exists.

### B3. Waypoints list and details

**Row** (`ui/availability/AvailabilityTripsWaypointsUi.kt:183-299`):

- name
- an unlabelled MGRS string
- an unlabelled `"%.4f, %.4f"`

**Sheet** (`RecordDetailsSheet.kt:325-362`) has labelled rows:

- MGRS
- Coordinates
- Created
- Track, the parent's title, which is a timestamp when the track is unnamed
- "Used in N journal entries"

**Stored and never shown:** `altitude`, `note` and `designation` (`domain/model/Waypoint.kt:26-32`).
The note appears only as a map marker snippet (`ui/map/SightingsMap.kt:2240`).

### B4. Offline regions (row, details, picker)

**Row** (`AvailabilityOfflineMapsUi.kt:633-640`) is one line packing five values:

```
"<radius> around <lat>, <lng> — <N> tiles, <x.x> MB — downloaded <relative>"
```

It is followed by a paragraph of raw zoom numbers (`:653-657`).

**Details sheet** (`RecordDetailsSheet.kt:462-475`):

- Radius
- Centre (decimal degrees)
- Tiles (raw count)
- Size
- Downloaded

**Picker** (`:196-238`, `:358-360`, `:469`, `:540-544`):

- `"Download region: %.4f, %.4f"`
- `"~N tiles"`
- `"downloaded / total tiles"`
- `"Tile budget: used / limit"`, a raw ratio with no meter

**Data:** `OfflineRegionSummary` (`domain/OfflineMapRepository.kt:212-225`). The code notes that
per-region sizes overlap, so a true total disk use is not stored (`:540-543`).

### B5. Finds gallery, find report, find editor

**Gallery tile** (`ui/log/FindsGalleryScreen.kt:239`):

- "Find on 2026-09-20", an ISO date.
- No identification on the tile, although `ownIdentification` is stored.

**Find report** (`ui/log/LogEntryReportScreen.kt`):

- The title, with an ISO date (`:122`).
- `"Found at %.4f, %.4f"` (`:161`), with no MGRS, unlike waypoints.
- "Your own identification: …" (`:167`).
- Seven morphology sections as `"Label: value"` lines, about 25 in all (`:219-314`), a natural
  two-column table.
- Spore print "Read on: <ISO date>" (`:293`).

**Find editor** (`LogEntryDetailScreen.kt:149,179`): the same title and location line.

**Missing:** counts of finds by species, by month or season, or by place. No aggregation code
exists. The only grouping is by day.

### B6. Trip planner and trip windows

**Planned trip row** (`AvailabilityTripsWaypointsUi.kt:59-158`):

- "Today" badge
- name
- date `"MMM d"`, with no weekday or year
- unlabelled MGRS
- an inline unlabelled `"%.4f, %.4f"` (`:146`), which duplicates `decimalDegreesLabel`
- screen-reader text with an ISO date (`:154`).

**Trip windows card** (`AvailabilityResultsUi.kt:523-596`), per window, up to six prose lines:

- date range
- `"X–Y days after <rain> of rain ending <date> (forecast)"`
- more rain forecast
- `"Shallow soil moisture: %.2f m³/m³"`, a raw volumetric figure with no plain meaning
- soil temperature
- evapotranspiration

**Data held but not shown** (`domain/model/TripWindow.kt:12-111`):

- deeper soil moisture
- min/max soil temperature
- the full `rainEvents` with daily totals
- `horizonEnd`

These are enough for a rain timeline with windows on it. A planned trip is not linked to windows
or weather.

---

## C. Other screens (sub-agent 2)

### C1. Settings (`ui/availability/AvailabilitySettingsUi.kt`)

- Almost all controls.
- One readout, `"Build <versionCode> · <versionName>"` (`:93`). The version name carries the commit
  hash (`:80-87`).

### C2. Backup (`ui/backup/BackupSection.kt`, `BackupViewModel.kt`, `RestoreLoadingPage.kt`)

**What the user sees now:**

- "Backup folder" plus `folderName(uri)`, the last segment of a storage URI, which can look
  internal (`:124-131,227-230`).
- One status line (`BackupViewModel.kt:31-42`).

**Computed and dropped:**

- From `BackupReport`: `photoFiles` and `archiveBytes`. Only the missing-photo count reaches the
  screen (`domain/JournalBackup.kt:18`, `BackupViewModel.kt:246`).
- `RestoreReport` (rows inserted, records skipped, rows dropped, photos added) is never shown
  (`JournalBackup.kt:25-31`). The screen says only "Restore complete."

**Not stored at all:** the last backup time (`domain/BackupSchedule.kt:18-22`).

### C3. Crash logs (`ui/crash/CrashLogPanel.kt`)

**List:** one row per file, as a formatted time `"MMM d, yyyy, h:mm a"`. If parsing fails it falls
back to the raw filename (`:140,187-191`).

**Detail:** the whole raw file in monospace (`:153-166`):

- `Timestamp:` in ISO UTC
- `Thread:`
- `API level:`
- the full stack trace (`crash/CrashFileStore.kt:34-40`)

The app version is not written to the file, so a per-version summary is not possible from stored
data.

### C4. Diagnostics (debug builds only, `src/debug/.../ui/diagnostics/DiagnosticsPanel.kt`)

- Raw filenames.
- Size and time per file.
- Raw log text.
- The walk logger's `walklogs/` folder is not listed in the panel (`:208-210`, `:475`).

---

## D. List and Seasonal tabs (sub-agent 2)

### D1. List tab (`ListTab`, `ui/availability/AvailabilityResultsUi.kt:98`)

**What the user sees now:**

- The basis line `"Based on N historical iNaturalist observations of <filter> within <radius> for <Month>."`
  (`:203-205`).
- An offline banner with a relative time (`:159`).
- One card per species (`:644-687`):
  - common name
  - scientific name
  - an **unlabelled bar** (`LinearProgressIndicator` of `relativeLikelihood`, which is count over
    the top species' count, `domain/model/AvailabilityForecast.kt:20`)
  - `"N observations"`

**Not shown, though in memory:**

- a rank
- each species' share of all observations
- the species count
- `rank` and `photoUrl` from `SpeciesObservationCount`

### D2. Seasonal tab (`SeasonalTab`, `AvailabilityResultsUi.kt:239`)

**Conditions card** (`:453-506`):

- "<rain> of rain in the last 14 days"
- "N days since last rain."
- "<rain> of rain forecast today."

**Sample-size lines** (`:350-370`). There are 2 to 4.

**Fruiting-lag chart** (`:387-407`):

- The only chart in the app.
- A 160 dp hand-drawn `Canvas` of bars scaled to the largest. The rule-of-thumb bucket is in the
  primary colour.
- It has no axes, ticks or values on the bars.
- The counts are listed underneath as label/count rows (`:417-433`).

**Bucket widths.**

- The buckets are 0–6, 7–21, 22–35 and 36+ days, plus "No preceding rain event"
  (`domain/ComputeFruitingLagDistributionUseCase.kt:88-97`). The first three are 7, 15 and 14 days
  wide.
- Raw counts therefore favour the 15-day rule-of-thumb bucket visually. Nothing normalises them.

**The 14 daily rain values are thrown away.** `OpenMeteoWeatherProvider.toDomain` sums them into
one total (`data/repository/OpenMeteoWeatherProvider.kt:149-161`). A daily rain chart would need
that data kept.

---

## E. The map surface (sub-agent 3)

Every surface below that draws over the map uses the 80% fill:

- `MAP_CHROME_OVER_MAP_ALPHA`, `ui/map/MapChrome.kt:268`
- `MapIconStackButtonColor*`
- `mapChromeFill(..., overMap = true)`

Unverified: the bubble's 6 dp shadow (`MapBubble.kt:217`) and the legend's 2 dp shadow
(`MapLayersSheet.kt:474`) may push the composite above 0.8. The code's own comment at
`AvailabilityMapOverlaysUi.kt:333-337` says a shadow does this elsewhere.

### E1. Navigation HUD (`ui/availability/NavigationHud.kt`, `ApproximatePositionHud.kt`)

**Row 1.**

- **Heading:** `"123° SE"` (`:551`).
- **Turn:** `"Turn N°"` (`:645`).
  - `relativeBearingDegrees` returns 0 to 359 (`domain/NavigationReadout.kt:16-19`), so a slight
    left turn reads "Turn 350°".
- **A large distance figure, never labelled** (`:306-314`, `:606-618`). It can read:
  - "within 16 ft" or "≈ 10 m" (straight line with accuracy)
  - the route distance
  - "Arrived"
  - "—"
- **The status line.** While returning, the large figure is the route and the status line reads
  "Straight line ≈ 350 m" (`:653-668`). So the same slot means two things.

**Row 2.**

- **Elevation:** "412 m" / "1352 ft", unlabelled (`:572`).
- **Coordinates:** an MGRS string, unlabelled. A tap switches it to `"Lat. %.4f Long. %.4f"`
  (`AvailabilityPureFunctions.kt:99-108`).

**Units:** distance and elevation follow the setting. Bearings and coordinates have none.

### E2. Compass strip (`ui/availability/AvailabilityMapControlsUi.kt:315-577`)

- One line: `heading · elevation · coordinates`, all three unlabelled (`:545-557`).
- The coordinates end in an ellipsis when they don't fit.
- An optional sundown line underneath.

### E3. Sundown line (`ui/availability/SundownLineText.kt`)

- Labelled lines that pack 2 or 3 values, for example
  `"Sunset {time} · in {dur} · dark {short}"` (`:67-85`).
- The duration format is "1 h 12 min", which differs from the bubbles' "1h 12m".
- In 12-hour mode the later times print "h:mm" with no AM/PM (`:38-44`).

### E4. Live recording

- **Nothing on the map shows recording progress:** no elapsed time, distance so far or point
  count.
- The Return button's screen-reader text is
  `"Return: 212° SW · 1.2 km · +33 ft"` (`AvailabilityMapControlsUi.kt:595-603`). It is never drawn.

### E5. Map bubbles (`ui/map/MapBubble.kt`, `MapBubbles.kt`, `AvailabilityMapOverlaysUi.kt`)

| Kind | Raw values |
|---|---|
| Waypoint | MGRS, unlabelled (`MapBubble.kt:442-443`) |
| Planned trip | MGRS and a decimal pair, both unlabelled (`:463-466`) |
| Track | `"distance · duration"`, e.g. "3.4 km · 1h 12m" (`:456`) |
| Offline region | `"radius · size"`, e.g. "10 km · 45.3 MB" (`:477`) |
| Forecast cell | chance "NN%", "Uncertainty a% to b%", one `"label: value"` line per driver, ISO dates (`:481-491`). The driver `value` is a passed-through string whose content is unverified (`domain/ForecastCells.kt:54`) |
| Sighting | date, "±12 m accuracy" (labelled) |
| Find, photo | none |

The "kept in entries" lines inside bubbles use ISO dates via `journalEntryDateLabel`
(`ui/map/JournalEntriesOnMap.kt:27`). That ISO format is a recorded planner answer ("Q3:
`2026-09-12`"), so changing it is a ruling change, not a fix.

### E6. Legend, Layers sheet, journal chip

**Legend** (`ui/map/layers/MapLegend.kt`):

- a gradient labelled only "0%" and "100%"
- "Week of <ISO>, weather to <ISO>"
- "no forecast here"

**Layers sheet:** a raw `"N%"` beside each opacity slider (`MapLayersSheet.kt:404-417`).

**Journal entries chip menu:** one ISO date per entry (`JournalEntriesChip.kt:150-151`).

**Forecast layer, zoomed out:** below zoom 7 it draws nothing and says nothing
(`ForecastCellLayer.kt:22-32`). The comment there says "No new copy". That is a "don't make me
think" case to flag, not a data-display one.

---

## F. What exists to draw with

**Libraries.** The app has no chart, graph or table library (`gradle/libs.versions.toml`,
`app/build.gradle.kts`). Compose `Canvas` is the only drawing tool, through `androidx-ui-graphics`.

**Existing drawing and layout code.**

- **Seasonal bar chart:** `FruitingLagChart` (`AvailabilityResultsUi.kt:387-407`). It is private
  and tied to its bucket type.
- **Track thumbnails** (`ui/log/TrackThumbnail.kt`):
  - `TrackThumbnail`, 40 dp
  - `WalkThumbnail`, 96 dp, with start, end and waypoint dots
  - `EntryTrackThumbnail` on journal cards (`CartographyEntryCard.kt:385`)
- **Label and value rows:**
  - `DetailField` (`RecordDetailsSheet.kt:502`): a fixed-width label column plus a value, merged
    for TalkBack. Private.
  - `FruitingLagBucketCounts` rows (`AvailabilityResultsUi.kt:417-433`).
- **Bars without drawing:** `LinearProgressIndicator`, already used that way.
- **Maps:** the entry map, a live MapLibre map that opens fullscreen, is the "small map" used in
  reports (`CartographyEntryReportScreen.kt:461-520`).

**Missing building blocks.**

- `ui/theme/` holds no shared card or table composable.
- `strings.xml` holds only notification text. Every on-screen string is inline Kotlin.

**Testing caveat.** Canvas output is not rendered under Robolectric (comment at
`AvailabilityResultsUi.kt:410-414`), so any chart's appearance is device-only by construction.

## G. Rules from CLAUDE.md that touch this

- **Nothing fully obstructs the map.** Any new summary or chart drawn over a map (HUD, strip,
  bubbles, sheets) starts at the 80% fill. A chart inside a report below the map does not cover
  the map and stays solid.
- **Don't make me think.** A figure with no label, a "Turn 350°" for a slight left, and "0m" next
  to a distance all ask the user to decode.
- **Honest copy plus a next step.** Several empty or failed states already follow it. The silent
  forecast layer when zoomed out does not.
- **Units.** Every length goes through the unit setting. Any summary of elevation must use
  `formatWholeLength` / `formatElevationChange`, not a new formatter. The track formatter's
  rounding history is recorded at `CartographyEntryEditScreen.kt:649-661`.
- **Domain/UI split.** The per-entry totals, gain and loss, and any profile series belong in
  `domain/` beside `ComputeTrackStatisticsUseCase`. They are not to be computed in composables;
  the editor already calls the use case inline from a composable at `:499`.
- **Room for data that relates.** Showing gain and loss for a kept track whose record was deleted
  would need a new snapshot column, and a column lands with its reader. Today the snapshot has
  distance, duration and point count only.
- **Withhold means "not in this day's account", not "private".** Withheld records stay in Records,
  on the Maps tab, in GPX export and in backups.

## H. What I could not determine

- **The owner's "0 ft · 0m" entry.** Whether it was a walk still recording, a walk with all
  network fixes, or something else. It needs the entry and track on the phone.
- **Card counts on real entries.** The worked example in A3 is arithmetic, not a measurement.
- **Card movement after a tap.** Whether the card visibly jumps to the bottom of its section on
  Withhold or Keep. Inferred from the append at `CartographyViewModel.kt:330`; not seen.
- **Row order after reopening.** The loading queries have no `ORDER BY`.
- **Altitude coverage on real walks.** Whether it is good enough for gain or a profile to be
  believable. The hysteresis threshold has not been judged against real data here.
- **Layout and legibility.** How the strip's three-value line, the region row's five-value line
  and the sundown line wrap or truncate at real widths, and how the 80% fills and shadows read over
  satellite tiles.
- **Strings the code passes through.** What the forecast driver `value` strings actually say, and
  what `folderName()` prints for real storage providers.
- **24-hour phone setting.** Whether the hard-coded 12-hour patterns
  (`"MMM d, yyyy, h:mm a"`, `AutoWaypointName.kt:24`) clash with it.
- **Citations repeated.** The sub-agents' citations I did not re-read are listed as theirs above.
  The sample I re-read all held.
