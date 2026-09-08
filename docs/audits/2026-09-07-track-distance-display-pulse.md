# Pulse — track distance display path

**Type:** read-only pulse. **Nothing changed.** Read on PR #77's branch at `495725b` (the pulse
names `11b5cad` as its baseline; the code this pulse is about — the Cartography snapshot, its
recompute, the row formatter, the read seam — is identical at both, and predates the PR).
**Dispatch:** `pulse-track-distance-display.md` (owner's upload).

## The short answer

**The inference does not hold, and the evidence cannot have supported it.** The entry screen's
track distance is the Cartography decision's snapshot (`TrackDecision.distanceMeters`), which is
recomputed on every open from the filtered read seam and written back — but the label it is
shown through, `trackSubtitle` (`ui/log/CartographyEntryEditScreen.kt:647`), **rounds the metres
to whole kilometres first and then converts that integer to whole miles**:

```kotlin
val km = (distanceMeters / 1000.0).roundToInt()      // 1957.4 m → 2 km;  733.2 m → 1 km
val distanceLabel = formatDistanceKm(km, distanceUnit) // 2 km → "1 mi";   1 km → "1 mi"
```

`formatDistanceKm` (`domain/model/DistanceUnit.kt:58`) is the *region-radius* formatter — it takes
an `Int` of kilometres because offline-map radii are whole kilometres — and Stage 2b reused it for
track lengths on 2026-09-01 (`d0bf04e`). Worked from the pulse's own figures, with the app's
`MILES_PER_KM = 0.621371`:

| Sum | ÷ 1000, rounded | × 0.621371, rounded | Displayed |
|---|---|---|---|
| 1957.4 m (all points) | 2 km | 1.24 → **1** | "1 mi" |
| 733.2 m (survivors) | 1 km | 0.62 → **1** | "1 mi" |
| 229.3 m (all, second track) | 0 km | 0 | "0 mi" |
| 92.0 m (survivors, second track) | 0 km | 0 | "0 mi" |

**"1 mi" is what any track from 500 m to just under 2.5 km prints** (0.5–1.5 km → 1 km → "1 mi";
1.5–2.5 km → 2 km → 1.24 → "1 mi"; only from 2.5 km does "2 mi" appear). The pulse's "1.216
rounds to 1, 0.456 does not" is the arithmetic of a formatter this screen does not use. Both
candidates display identically, so the screen says nothing about which sum is stored. The
distance path is filtered (below); the label is too coarse to have shown it either way.

## Questions, in order

### 1. Where does the entry screen's track distance come from?

`TrackDecision.distanceMeters` (`domain/model/CartographyEntry.kt:106`), a **stored snapshot** on
`cartography_entry_track_refs.distanceMeters` (`data/local/CartographyEntryEntity.kt:78`), shown by
`trackSubtitle(...)` at three sites:

| Surface | Site | Distance source |
|---|---|---|
| Entry edit screen, a decided (kept or withheld) track row | `CartographyEntryEditScreen.kt:487` | the snapshot |
| Entry edit screen, an undecided candidate row | `CartographyEntryEditScreen.kt:498–502` | `ComputeTrackStatisticsUseCase()(track.points)` on the day's live track, computed in composition |
| Entry report screen, a kept track row | `CartographyEntryReportScreen.kt:508` | the snapshot |

The snapshot is **written at decision time** — `Track.toDecision` (`CartographyViewModel.kt:544`)
runs `ComputeTrackStatisticsUseCase` over `track.points` when an entry is created
(`CartographyViewModel.kt:113`) or a candidate is decided on (`:270`) — and **recomputed on every
open** by `onOpenEntry` (`CartographyViewModel.kt:158–199`): `withRecomputedTrackSnapshots`
(`:534`) reruns the same computation over the day's tracks as freshly loaded, and if any decision's
figures changed the entry is saved back (`:171–177`; a failed write is logged and the corrected
figures are shown anyway, so the next open recomputes again). So: computed at write time, cached,
and re-derived on read. The entity's doc records this as the one deliberate exception to "an entry
never silently changes on reopen" (`CartographyEntryEntity.kt:55–63`, timestamp-filter dispatch,
owner decision).

### 2. Does that path go through the filtered read seam?

**Yes, at every step.** The tracks the snapshot is computed from, at creation and at recompute,
are `trip.derivedTrip.tracks`, which `GetDerivedTripUseCase` reads with
`trackRepository.getForDay(...)` (`domain/GetDerivedTripUseCase.kt:42`) — `RoomTrackRepository`,
whose every read of `track_points` passes through the one `toDomain` mapping that applies
`excludeNetworkProviderFixes` (`data/repository/RoomTrackRepository.kt:66–79`). The candidate row's
live computation runs over the same `Track.points`. The entry map
(`GetCartographyEntryMapDataUseCase`, `trackRepository.getById` per kept track) draws the same
survivors. There is no second query, no unfiltered DAO call, and no distance computed at the
service or at write time from raw fixes.

Confirmed by the timestamp-filter dispatch's own reverted-variant record
(`2026-09-07-timestamp-filter-completion-report.md:151`): with the seam disabled, the Cartography
recompute test failed `expected:<222.39> but was:<12898.63>` — the unfiltered sum — which is the
seam being the recompute's only input, demonstrated rather than argued.

### 3. If it is a stored field, when was it written, and can it be stale?

It is a stored field, and **it can hold the unfiltered figure on disk**: any decision written
before the seam existed (the filter reached `main` on 2026-09-07 in the timestamp-filter PR, after
the pre-build `35d09cf`; Stage 2b's snapshot dates from 2026-09-01) carries the unfiltered sum
until that entry is next opened through `onOpenEntry`, which recomputes and writes back. That is
precisely the case the recompute was built for, and the case its test plants
(`CartographyViewModelTest.kt:612–617`: a stale 6789.0 m snapshot in the database, two 0.001°
steps of real track, the screen shows 222.39 m, the row is rewritten; fails with the recompute
removed).

What this means for the captured database: **the version-14 file may well hold 1957.4 in
`cartography_entry_track_refs.distanceMeters` for track `3aec7001…`** — if the entry was created on
a pre-filter build and had not been reopened on a post-filter build before the capture. It may
equally hold 733.2. The pulse did not report that column's value, and reading it is the one-line
check that answers the question the display could not: `SELECT distanceMeters, durationMillis,
pointCount FROM cartography_entry_track_refs WHERE trackId = '3aec7001-1fac-454d-b716-fde89abf79f4'`.
`pointCount` is the tell — 195 is the unfiltered era, 135 is post-recompute. Either way, opening
the entry on APK-A or APK-B recomputes it to 733.2 / 135 and writes that back; the number on
screen after any open is the filtered one, printed as "1 mi".

**Paths that publish an entry to the screen without the recompute**, counted from every
`editingEntry =` assignment in the ViewModel: creation (`:123`, fresh from filtered tracks — not
stale), `onOpenEntry`'s own trip-load-failed branch (`:164` — the entry opens **uncorrected** with
the "Couldn't compile that day's report." message; the doc says so), `onFinishEntry` (`:317`,
a commit of the already-open entry), `persist` (`:484`, `:495`, the open entry with a user edit),
and the save write-back (`:354`). Only the trip-load failure shows a snapshot that was never
recomputed this session, and it says so on screen.

### 4. How many surfaces show a track distance, and is each filtered?

Counted from every reader of `.distanceMeters` and every caller of `ComputeTrackStatisticsUseCase`
and the two distance formatters in `app/src/main`:

| # | Surface | What it shows | Source | Filtered? |
|---|---|---|---|---|
| 1 | Entry edit screen, decided track row | "N mi · Hh Mm" | snapshot, recomputed on open | yes (after any open; stale-on-disk possible until then, §3) |
| 2 | Entry edit screen, candidate track row | same | live `Track.points` | yes |
| 3 | Entry report screen, kept track row | same | snapshot | yes, same as 1 |
| 4 | Navigation HUD, distance to target | "within 16 ft" / "≈ 0.3 mi" | `GeoDistance.metersBetween(fix, target)` — a live fix and a waypoint, **no track points** | not a track distance |
| 5 | Compass strip's return arm (`AvailabilityScreen.kt:4337`) | "Return: 270° W · 412 m · …" | `ComputeReturnToStartUseCase`: straight line from the live fix to the track's **first point** | the first point comes through `getById` — yes; but it is one point, not a sum |
| 6 | `TrackRecordingViewModel.recentReturnDistancesMeters` (`:456`) | not shown; the off-track window | same straight line | as 5 |

**Surfaces that show no distance at all:** Records' Recorded Tracks rows (`TrackExportPanel.kt:
110–117` — "N points" plus the exclusion note; `TrackExportSubtitleTest` pins it), the Entries feed
(no track figures), the entry map and plates (survivor polylines, no length), the GPX export
(points, no total). The pulse's "assume nothing until counted" was right to ask: there are **three**
track-length displays, all one formatter and one source, and three straight-line distances that are
not track lengths. No fourth reader.

### 5. Does `pathHome` consume filtered points?

**Yes, and the earlier claim survives — with its premise stated more precisely.** `pathHome` and
`returnWalkingTime` have **no caller** in `app/src/main` (grep: none outside their own files); the
claim "takes a `Track`, so the filtered seam is the only way in" is about the *type*, and the type's
producers are the real premise. Counted: every `Track(...)` construction in main —

| Producer | Points |
|---|---|
| `RoomTrackRepository.toDomain` (`:70`) | filtered — the seam |
| `StartTrackUseCase` (`:21`) | `emptyList()` — a new recording, no points yet |
| `GpxCodec.decode` (`:72`) | the file's points, **unfiltered** — and `decode` has **no caller in main** (only `encode` is used, by `TrackGpxExporter`); GPX import is not wired |

So today a `Track` with points reaches a consumer only through the seam. The one unfiltered
producer exists as a decoder with no import path behind it; if GPX import is ever wired, an
imported `Track` must be persisted and read back (as a recording is) rather than handed to a
consumer directly, or the seam is bypassed. That is the statement the Items 1–3 report should have
made and this pulse now records. Nothing in this pulse's evidence contradicts it: the Cartography
distance is not a second path, it is the same seam plus a cache of its output.

### 6. Is `excludedPointCount` consistent with the distance beside it?

**Consistent wherever both appear, and for this track neither appears.** The exclusion note
(`networkFixExclusionNote`) prints only when `networkFixExclusionIsLarge()` — more than 75 % of at
least 10 stored points excluded, or one or no survivors. Track `3aec7001…` is 60 of 195 = 31 %, so
no row shows a count for it at all; the second track, 7 of 26 = 27 %, likewise. Where the note does
print: the candidate row (#2) pairs it with a distance computed from the same `Track.points` in the
same composition — one object, both facts; the decided row (#1) pairs it with the snapshot,
recomputed on open from the same day's `tracks` the note reads — consistent after any open, and
before one the note is not shown at all (the live track is loaded by the same `onOpenEntry`). The
report row (#3) shows no count, only " · no usable points" at `pointCount == 0`, which the recompute
keeps current. The contradiction the pulse fears — a stated exclusion beside a sum that includes
the excluded points — has no site.

## What this pulse actually found

Not the bug it was looking for; two smaller things:

1. **The track-length label is far coarser than its source.** Rounding to whole kilometres and
   then to whole miles makes "1 mi" cover 500 m to 2.5 km and prints "0 mi" for anything under
   500 m — a 400 m walk, a real one, reads as no distance. The app already has the right formatter:
   `formatDistanceMeters` ("752 ft", "0.5 mi", "1.2 mi"), built for the HUD, which would have
   printed the two candidates as "1.2 mi" and "0.5 mi" and made this pulse unnecessary. Whether
   the three row sites should move to it is the owner's call; it changes what every existing
   entry's rows read, in the same way the recompute did, and the pulse's own "do not" covers it.
2. **A stale snapshot on disk is possible and invisible until the entry is opened**, by design;
   the captured database can be checked directly (§3) and is the only way to know which figure
   the owner's entry held at capture time.

## Required disclosure

**Confirmed from the code** (files and lines as cited): the three `trackSubtitle` sites and the
formatter's two roundings; `MILES_PER_KM`; the snapshot's write (`toDecision`) and recompute
(`withRecomputedTrackSnapshots`, `onOpenEntry`) paths and every `editingEntry =` assignment;
`GetDerivedTripUseCase.getForDay` and the repository's single mapping; `GetCartographyEntryMapDataUseCase`'s
`getById`; every `.distanceMeters` reader and `ComputeTrackStatisticsUseCase` caller in main; every
`Track(` construction; that `GpxCodec.decode` has no caller; that `pathHome` has no caller; the
Records row's subtitle; the recompute test and its reverted-variant record. **Confirmed from git:**
the formatter's date (`d0bf04e`, 2026-09-01) and the filter's (2026-09-07). **Confirmed by
arithmetic** (Python as a calculator, not the app): the table in the short answer.

**Inferred:** that the owner's entry was created before the filter and not reopened after — the
only way the on-disk snapshot holds 1957.4; equally possibly it holds 733.2. Not determinable from
here.

**Could not determine:** the value of `distanceMeters` / `pointCount` in the captured
`cartography_entry_track_refs` row — the one fact that would close §3; whether the "22m" duration
is the filtered or unfiltered span (`durationMillis` is last minus first surviving point, so the
two differ only if an end point was a network fix; the pulse did not report both durations).

**Premises in the pulse that were wrong:** "1.216 rounds to 1. 0.456 does not" — true of
`formatDistanceMeters`, false of the formatter this screen uses, which rounds kilometres before
miles and prints "1 mi" for both. The summation method is not the difference either: the app's
`ComputeTrackStatisticsUseCase` is the same great-circle sum over consecutive points, no smoothing
(`:52–60`), on the same mean radius. The pulse's arithmetic itself holds; its reading of the label
is what does not. The larger worry — a distance path around the seam — is not borne out: there is
one seam, one cache of its output, and a label that cannot show the difference.

**Decided beyond scope:** nothing. No file outside `docs/audits/` touched.
