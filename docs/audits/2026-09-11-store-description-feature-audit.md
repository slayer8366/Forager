# Audit: the nine features the store description claims, against the code

**Date:** 2026-09-11
**Scope:** audit only. No code, test or dependency changes. Read-only.
**Base:** `claude/beta-signup-website-g3t91u` at `199d642`, cut from `main` at `4957675`
**Amended 2026-09-11** by owner ruling: see "Corrections" at the end. Three verdicts below
(claims 2, 7 and 9) were wrong as first written and are superseded there. The original text is
left in place so the correction has something to be a correction of.

**Companion:** `2026-09-11-listed-features-phase0-inventory.md` established presence by name.
This one reads the implementations and describes what they do.

---

## Verdict table

| # | Store claim | Verdict |
|---|---|---|
| 1 | iNaturalist research by month and location | **Accurate** |
| 2 | Seasonal fruiting lag chart | ~~Copy claims more~~ **Accurate** (corrected) |
| 3 | Tracks, breadcrumbs, waypoint navigation, return to start | **Mostly accurate; two qualifications** |
| 4 | Sundown alerts | **Does not exist** |
| 5 | Offline maps | **Accurate** |
| 6 | Waypoints | **Accurate** |
| 7 | Journal records finds, waypoints, offline maps, tracks | ~~Partly~~ **Accurate, literally** (corrected) |
| 8 | Cartography | **Accurate** |
| 9 | "GPX calibration ... accurate under a canopy" | ~~Inverted~~ **Substantially accurate** (corrected) |

---

## 1. Research by month and location, from iNaturalist

**Exists, and the description matches.**

`data/remote/INaturalistApi.kt` is the only place in the app that speaks iNaturalist's wire
format; everything else depends on `domain/MushroomRepository`. Two endpoints:

- `observations/species_counts` (`:32-42`) ranks taxa by how many verifiable observations exist
  for the filters. This is the availability signal: a species observed often in this place in
  this month, across past years.
- `observations` (`:45-56`) returns individual records with position, taxon, date and photo, for
  the map pins.

Both take `lat`, `lng`, `radius` in km, `month`, and one of `iconic_taxa` or `taxon_id`, which
are mutually exclusive per call (`:15-17`). `verifiable=true` is always sent. Fungi is the only
category, an owner decision recorded at `ui/availability/AvailabilitySearchUi.kt:498`.

One detail worth knowing because it shows the integration was tested rather than assumed:
`withoutTaxonId` carries a note (`:19-23`) that it was verified against the live API first,
because iNaturalist answers 200 and silently ignores parameters it does not recognise, so a
misspelling would look exactly like a working filter that changes nothing.

## 2. The seasonal fruiting lag chart

**The chart exists. What it shows is not "the best time to go."**

`domain/ComputeFruitingLagDistributionUseCase.kt` takes the sightings returned for your area and
month, plus the Open-Meteo rainfall archive, and for each sighting with a known date finds the
nearest *soaking event* that ended on or before it. A soaking event is a run of consecutive rain
days (at least 2.0 mm each, `FruitingPatternAssumptions.RAIN_DAY_MIN_MM`) whose total reaches
10.0 mm (`SOAKING_EVENT_MIN_TOTAL_MM`). The gap in days between the event ending and the sighting
is the lag, and lags are counted into four buckets: **0-6, 7-21, 22-35, 36+ days**.

The 7-21 bucket is `FRUITING_LAG_DAYS`, the rule of thumb the whole feature exists to test. The
chart (`ui/availability/AvailabilityResultsUi.kt:373-400`, a hand-rolled Compose `Canvas`, no
charting dependency) draws that bucket in the theme's primary colour and every other bucket in a
second colour. Its own doc comment states the entire visual claim: **whether the data's tallest
bar lines up with the rule of thumb, or does not.**

**Where the copy overshoots.** `FruitingPatternAssumptions`' header says these constants are
"rules of thumb chosen to bound a search, not fitted parameters," and that combining them into a
score "would launder a rule of thumb into a prediction the data does not support."
`ComputeTripWindowsUseCase`'s header is blunter: there is no measured relationship in this
codebase between weather and observation frequency, so "a 73% or a best day would be an invented
formula wearing the clothes of a prediction."

The store copy says the chart helps you "plan the best time to go on a trip." The code
deliberately refuses to name a best time. It shows you when fruiting has historically followed
rain in your area, and leaves the conclusion to you. That is a defensible and more honest claim,
and it is a different claim.

## 3. Track recording, breadcrumbs, waypoint navigation, return to start

**Recording, breadcrumbs and return to start: yes. The other two clauses need qualifying.**

Recording runs in a foreground service (`service/TrackRecordingService.kt`) with an ongoing
notification, which is why it survives the screen going off without background-location
permission. `domain/LocationSampler` accepts a fix as a track point only when **both** throttles
have been met since the last accepted point, never either alone, so standing still does not
write a point every few seconds. Three modes (`domain/model/TrackRecordingMode.kt`):

| Mode | Min interval | Min distance | Accuracy ceiling |
|---|---|---|---|
| `HIGH_ACCURACY` | 5 s | 5 m | 30 m |
| `BALANCED` | 15 s | 15 m | 50 m |
| `BATTERY_SAVER` | 60 s | 30 m | 100 m |

Breadcrumbs draw live: `breadcrumbPoints` flows from the recording state through
`ui/map/MapSlot.kt:316` into `ui/map/SightingsMap.kt:184` while recording.

Return to start is `domain/ComputeReturnToStartUseCase.kt`, which computes initial bearing, great
circle distance and elevation difference from the current position to the target. The target is
chosen in `ui/track/TrackRecordingViewModel.kt:495-497`: **the origin waypoint if one exists,
otherwise the first breadcrumb**.

**Qualification one: "waypoint navigation" means navigation to the origin waypoint, not to a
waypoint you choose.** `WaypointDesignation` has exactly two values, `ORIGIN` and `END`, and END
is documented as "never a navigation target in stage one, so never drawn on the map." Searches
for a selection path (`navigateTo`, `selectedWaypoint`, `targetWaypoint`, `navTarget` across
`main/`; `onSelect`/`onWaypointClick`/`chooseWaypoint` in files mentioning waypoints under `ui/`)
returned nothing, in this repository and in `forager-bak`. A user cannot pick an arbitrary
waypoint and be guided to it.

**Qualification two: "before sundown" is not implemented.** See 4.

## 4. Sundown alerts

**Does not exist.** This is the only claim in the list with nothing behind it.

The app registers exactly three notification channels, and these are all of them:
`off_track_alert`, `off_track_alert_v2`, `track_recording`. There is no sundown channel, no
alert, no scheduling, and no sunset time computed anywhere in `main/`.

What does exist is `domain/CivilTwilight.kt`: a complete NOAA solar-position implementation, pure
Kotlin, no Android imports and no network, tested in `CivilTwilightTest` against midsummer,
midwinter and a London midsummer-midnight case. It exposes `isNight(epochMillis, lat, lng)`
against a threshold of **-6°**, which is civil twilight. It has **zero references in `main/`**.
The map's night mode is decided elsewhere (`ui/map/MapSlot.kt:310`).

So the arithmetic for a sundown feature is written, tested, and not connected to anything. Its
header also records a design decision worth carrying into any future work: it computes where the
sun *is* rather than the times it crosses the horizon, deliberately, because crossing times force
a polar special case that solar altitude does not have.

## 5. Offline maps

**Exists, and works as described.**

`domain/OfflineMapRepository.kt` is an owned interface; the implementation is
`map/MapLibreOfflineMapRepository.kt` over MapLibre's `OfflineManager`, with region rows in Room
(`offline_regions`, created by `MIGRATION_5_6`). Tiles come from the project's own Cloudflare
Worker serving a PMTiles archive out of R2, so no third-party tile vendor sees the requests.

Two design points recorded in the interface's header: it models **many regions, not one**, after
an earlier all-or-nothing version was found wrong for how offline maps actually get used (a
season visits several places, and downloading a second should not delete the first); and
`download` always targets **one fixed tile source**, with no style parameter, on the owner's
ruling after seeing the configurable version built.

## 6. Waypoints

**Exists.** `waypoints` table (`MIGRATION_4_5`), carrying lat, lng, optional altitude, name,
note, creation time, an optional `trackId`, and the optional `designation` described above.
Created through `TrackRecordingViewModel.addWaypoint` (`:440`) and automatically as the origin
marker via `createOriginWaypoint` (`:365`).

## 7. The journal

**Accurate if "journal system" is read to include Cartography. Not accurate about the journal
entry alone.**

A journal entry (`mushroom_log_entries`) records a find: coordinates, the date found, notes, your
own identification, a full morphology block (cap shape, surface, decorations, margin,
hymenophore, gill attachment and more), sync state, and photos through `log_entry_photos`. It
carries **one** link of the four the copy lists: an indexed `offlineRegionId`
(`MushroomLogEntryEntity.kt:42`), deliberately unconstrained so that deleting a region leaves a
dangling id rather than SQLite editing an entry as a side effect.

It has no `trackId` and no `waypointId`. Tracks and waypoints reach the journal through a
**Cartography entry**, not through a find. So the sentence is true of the journal *system* taken
as a whole and false of a journal entry taken alone.

## 8. Cartography

**Exists, is reachable, and matches its description well.**

A cartography entry (`domain/model/CartographyEntry.kt`) is an authored record of one outing: id,
date, free text, tags, a draft flag, and four lists of **decisions** rather than raw links,
`findDecisions`, `trackDecisions`, `waypointDecisions`, `offlineRegionDecisions`, plus photo
attachments. A decision carries `kept`, so the entry records what you chose to include and what
you chose to leave out, rather than silently assembling everything.

Six tables back it (`MIGRATION_10_11`): `cartography_entries` plus one reference table per kind.
`domain/GetCartographyEntryMapDataUseCase.kt` resolves those references for the map, and its
header documents that the four kinds resolve differently on purpose: waypoints and offline
regions already carry their own coordinates in the snapshot and need no fetch; tracks are fetched
by id; finds are fetched one call per distinct day rather than one per find, because the
repository has no `getById`; photos need no fetch at all.

Reached from `ui/log/JournalTab.kt`. Five test classes drive it, including `CartographyScreenTest`
and `CartographyEntryReportScreenMapTest`.

## 9. "GPX calibration to ensure accurate location results under a canopy, and while offline"

**Two problems, and the second is substantive.**

**The wording.** GPX is a file format for exchanging tracks. GPS is the positioning system.
"GPX calibration" is not a thing; the app does have GPX export
(`domain/GpxCodec.kt`, `export/TrackGpxExporter.kt`), which is a different feature and not this
one. Read as "GPS calibration," nothing in the app calibrates a GNSS receiver either. An app
cannot.

**What the code actually does is the opposite of the claim.** Tree cover weakens and reflects
satellite signals; software cannot undo that. What this app does is refuse to show you positions
it does not trust:

- `domain/LiveFixGate.kt:68` gates the live fix at **50 m**. Its header explains the number: a
  tighter 30 m "would blank the HUD under exactly the canopy the owner is testing in," and looser
  would mean "showing positions the track would refuse."
- A rejected fix is dropped and the previous good fix is **held and allowed to age**. Under
  canopy delivering only 60-80 m fixes, the HUD reads "Last fix 45 s ago," then withholds the
  distance entirely with "No fix for 5 min" **while the radio is alive and fixes are arriving**.
  The header calls this "the honest reading" and records that the friendlier alternative, letting
  a bad fix through once the good one goes stale, was considered and refused.
- Recording applies its own per-mode ceiling (30 / 50 / 100 m) before a fix becomes a track point.
- `domain/CompassTrustJudge.kt` decides reading by reading whether the needle can be trusted,
  entering unreliable above one threshold and leaving only below a lower one. The 15° figure is
  derived, not round: a walker following a needle 15° off for 100 m ends 26 m off line, outside
  the position's own error circle, "the point at which drawing it confidently is a lie."
- Network-provider fixes are excluded and the exclusion is **surfaced to the user** in at least
  four places (`ui/track/TrackRecordingUiState.kt:98`, `ui/track/TrackExportPanel.kt:126`,
  `ui/log/CartographyEntryEditScreen.kt:636`, `ui/availability/AvailabilityScreen.kt:597`).
- `domain/NavigationReadout.kt:46` sizes the "Approaching" band at twice the reported accuracy.

Every one of those is a *refusal to overstate position*, which is a real and unusual feature and
worth selling. It is not "ensuring accurate location under a canopy." Under canopy the app
becomes more reserved, not more accurate, and says so on screen.

Also relevant: Android defines `Location.getAccuracy()` as a 68% confidence radius, so about a
third of fixes fall outside their own stated accuracy. Nothing here can promise accuracy.

---

## Disclosure

### Confirmed by observation
Every file and line cited above was read, not grepped for. Constants
(30/50/100 m, 2.0 mm, 10.0 mm, 7-21 days, -6°, 15°) were read from their declarations.

### Could not be determined
- Whether each feature is *correct*, as against present and coherent. This audit describes
  mechanism; it did not run the app or the suite.
- Whether a compass calibration **prompt** is shown to the user, as against the status mapping
  (`sensor/AndroidCompassProvider.kt:175-177`) that would feed one.
- Live accuracy readout and stationary averaging, C11 (a) and (c) from the phase-0 dispatch.
- Where instrument-walk logs and tooling live. Searching `docs/` and the tree for
  `instrument`/`walk` filenames returned an **empty result, not a clean one**.

### Premises that were wrong
- The store list says "GPX calibration." GPX is the export format, which exists separately. The
  intended word is presumably GPS, and neither reading matches the behaviour.
- "Waypoint navigation" implies choosing a waypoint. Navigation targets the origin waypoint only.
- "A journal that records ... waypoints, offline maps, tracks" is true of the journal system
  including Cartography, not of a journal entry, which links only an offline region.

### Decided beyond scope
Nothing was changed. The copy-versus-code gaps in 2, 3, 4, 7 and 9 are reported for the owner to
rule on; this audit does not propose wording.

---

## The one that needs a decision before the beta list goes out

**Claim 4 is not a nuance, it is absent.** The description tells a reader the app will remind
them to head back, and nothing will. The same sentence appears in claim 3 ("to help you find your
way back before sundown"). A tester who reads that and relies on it is the failure mode worth
avoiding, and the beta signup page is already live.

The other gaps are wording: claims 2, 7 and 9 describe real features in terms the code
deliberately avoids. Claim 9's fix is the largest rewrite and the most interesting one, because
what the app actually does there is better than what the sentence promises.


---

# Corrections (owner ruling, 2026-09-11)

Three of the nine verdicts above were wrong. Recorded here rather than edited away, because the
error in claim 7 is a worked example of the failure this project keeps cataloguing.

## Claim 7: the journal. Wrong, and wrong at the layer I chose to look at.

The audit checked `MushroomLogEntryEntity` for `trackId` and `waypointId`, found neither, and
concluded the journal entry does not record tracks or waypoints. That is true of the entity and
irrelevant to the claim, because **the Journal is not an entity, it is a tab**, and the answer was
sitting in a doc comment the audit never opened.

`ui/log/JournalTab.kt:42-45` quotes the owner's own framing directly:

> "**Records is a logbook** — raw, complete, machine-generated data. **Cartography is where those
> records are compiled into a coherent story.**"

The Journal destination holds two tabs, Records and Cartography. `ui/log/RecordsTab.kt:30-42`
names Records' **four submenus: Waypoints, Offline Maps, Recorded Tracks, and Finds.**

So "a robust journal system that records your finds, waypoints, offline maps, tracks" is not
approximately right, it is a literal list of the four things in the Records tab. **Verdict:
accurate as written.**

The mistake is the same shape as the ones in CLAUDE.md's "check that never saw the data that
could fail it" family, in its reachability form: a question about the product surface was
answered from the persistence layer, one step removed, and nothing in the answer said so. The
cheap check was to open the screen. `git grep JournalTab` would have closed it.

## Claim 2: the fruiting lag chart. The standard applied was too strict.

The audit held that because `ComputeTripWindowsUseCase` refuses to score or rank, the copy could
not say the chart helps you plan the best time to go. Owner's ruling, and it is correct: a
planning aid built on imperfect data is still a planning aid, and the refusal to fabricate a
percentage is a statement about what the *app* asserts, not about what the *user* may conclude.
Applied consistently, the audit's standard would forbid describing the app at all.

"Helps you plan the best time to go" reads naturally as helping the reader work it out, which is
exactly what a distribution of observed lag against a stated rule of thumb does. **Verdict:
accurate as written.**

What survives from the original note, as a watch item rather than a defect: the app does not
itself nominate a day, so if the sentence is ever read as "the app tells you the best day", the
gap reappears. Nothing needs changing today.

## Claim 9: "calibration". The audit answered a claim that was not being made.

The audit objected that software cannot calibrate a GNSS receiver. True, and beside the point.
The owner's meaning is calibration **of the GPS to the code**: tuning how fixes are consumed so
that what reaches the user meets an accuracy standard. Read that way the term is apt, and the
constants are literally calibration values, each with its reasoning recorded at the declaration:

- `domain/LiveFixGate.kt:68`, 50 m on the live fix, chosen against two failure modes: 30 m "would
  blank the HUD under exactly the canopy the owner is testing in", looser would mean "showing
  positions the track would refuse."
- `domain/model/TrackRecordingMode.kt`, per-mode ceilings of 30 / 50 / 100 m before a fix becomes
  a track point.
- `domain/CompassTrustJudge.kt`, 15°, derived from a walker ending 26 m off line over 100 m,
  outside the position's own error circle.
- `domain/NavigationReadout.kt:46`, the "Approaching" band at twice reported accuracy.
- Network-provider fixes excluded, and the exclusion surfaced in four places.

That is a measurement pipeline tuned so its output is trustworthy, which is what calibration
means outside the hardware sense. **Verdict: substantially accurate.**

Two things still worth the owner's eye, both narrow:

1. **GPX is the export format**, and it exists separately (`domain/GpxCodec.kt`,
   `export/TrackGpxExporter.kt`). The store list says "GPX calibration" where it means GPS. A
   one-letter fix, and worth making so the sentence does not read as calibrating a file format.
2. **"Ensure accurate location results"** is the phrase doing the most work. Under canopy the
   calibration sometimes resolves to *no* reading: the HUD withholds distance with "No fix for
   5 min" while fixes are arriving, by design and with the friendlier alternative explicitly
   refused. A reader could expect a position where the app deliberately gives none. "Accurate or
   nothing" is the behaviour, and it is a stronger claim than "accurate", not a weaker one.

## What does not change

Claim 4, sundown alerts, is still absent. Nothing in the owner's ruling touches it, and it
remains the one item in the list with no implementation behind it.
