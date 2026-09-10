# Pre-build report: exclude network-provider fixes from recorded tracks, by the clock they carry

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Dispatch:** "Exclude network-provider fixes from recorded tracks, by the clock they carry"
(planner, owner-directed; replaces Part A of the two-data-corrections dispatch, which stays held).
**Base confirmed: `main` at `49b65c7`.** The branch `claude/new-session-102gri` is off that SHA and
carries one documentation commit already (`076748c`, the beta-template pre-build report, still
awaiting the owner's answers); no code has changed since `49b65c7`. All three items are
report-before-building. **Nothing is built in this commit.**

Suite as found: 1178 tests, 0 failed, 24 skipped, allowlist byte-identical — this session's last
full run on the tree that became `49b65c7`. Re-run and reported again with the build.

**Constraints worked under, as the dispatch requires them stated:** four tracks, one device, one
day, two locations; second-aligned GPS time is a chipset property, not a guarantee (Item 3 exists
because of it); the earlier radios-off ~40 m excursion is still unexplained by this theory; the
fix-logging branch remains the direct confirmation. Nothing below claims this rule explains every
bad fix ever recorded.

The load-bearing code fact is not re-litigated: the stored timestamp is `Location.getTime()`,
unchanged end to end (`docs/audits/2026-09-06-timestamp-discriminator-findings.md`, coder's answer).
I re-read the three lines on `49b65c7` while re-verifying the seam and they are as recorded.

---

## ITEM 1 — the read seam

### 1.1 The seam holds on current `main` — re-verified, not trusted

- `TrackDao.getPointsForTrack` (`data/local/TrackDao.kt:47`) is the **only** read of `track_points`.
  Its only callers are `RoomTrackRepository.getAll` (`:25`), `getById` (`:30`) and `getForDay`
  (`:36`), each passing the rows through the single private mapping `TrackEntity.toDomain(points)`
  (`:61–67`).
- `database.trackDao()` is called in exactly one place, `AppContainer.kt:221`, to build that
  repository. No other class holds the DAO.
- A grep of `app/src/main` for `track_points`, `TrackPointEntity` and `getPointsForTrack` outside
  the DAO, the entity, the database class and the repository finds only migration SQL and doc
  comments.
- The alert-delivery work (PR #71) touched `TrackRecordingViewModel` but not its read path: it still
  reads through `trackRepository.getById` (`:273`) and nothing else.

**Every `Track.points` reader, by grep, on `49b65c7`** — the list the per-consumer assertions will
follow:

| Reader | Via | What it shows |
|---|---|---|
| `TrackRecordingViewModel.beginPolling` (`:273–274`) | `getById`, every 15 s while recording | `breadcrumbPoints` → the live line; also the return-to-start fallback (`:440`) |
| `TrackExportPanel.trackSubtitle` (`:104`) | `GetTracksUseCase` → `getAll` | Records' "N points" row subtitle |
| `TrackGpxExporter.write` → `GpxCodec.encode` (`GpxCodec.kt:38`) | the same `Track` from Records | the exported file |
| `GetDerivedTripUseCase` (`:42`) → `CartographyViewModel.toDecision`, `CartographyEntryEditScreen.kt:491`, `GetTripReportOfflineRegionsUseCase.kt:35` | `getForDay` | Cartography candidates, snapshot values, fresh statistics, coverage coordinates |
| `GetCartographyEntryMapDataUseCase` (`:43`) | `getById` | the entry map's line |
| `GetTrackOriginWaypointUseCase` (`:22`) | `getById` | reads `originWaypointId` only; points unused |

**Not larger than described.** No bypass; the seam is the one mapping.

### 1.2 Where the predicate lives — proposal

`app/src/main/java/com/zynergylabs/forager/app/domain/NetworkProviderFix.kt` (name to settle), pure Kotlin, no
Android imports:

```kotlin
/** True when [this] fix's stored clock has a fractional second — see the file's doc comment for why that marks the network provider. */
fun TrackPoint.isNetworkProviderFix(): Boolean = timestampEpochMillis % 1_000L != 0L

/** The read-seam rule: every stored point that is not a network-provider fix, in stored order. */
fun excludeNetworkProviderFixes(points: List<TrackPoint>): List<TrackPoint> = points.filterNot { it.isNetworkProviderFix() }
```

One home, three readers: the repository mapping (`TrackEntity.toDomain` calls the list function),
the tests, and Item 3 (which needs the same predicate to count what was excluded). The file's doc
carries the evidence chain in two sentences and the two findings documents by name, the
`Location.getTime()` fact, and the Item 3 caveat. Rejected: a method on `RoomTrackRepository`
(the data layer would then own a domain rule and Item 3 could not reach it without the DAO) and a
`TrackReadFilter` list-of-rules object from Part A's plan (Part A is not being built and a second
rule is explicitly out of scope; a list of one is the speculative abstraction CLAUDE.md warns
against — if a second rule ever comes, the list function is the place it joins).

**One consequence of "not counted anywhere" that Item 3 needs settled here:** the excluded count
has to survive the seam or Item 3 cannot observe it. Proposal: `Track` gains
`excludedPointCount: Int = 0` — a **domain field computed at read**, not a column, set by the
mapping to `rows.size − kept.size`. Default `0` so no constructor site changes; every consumer that
does not care ignores it. It is the only way Item 3 can know "this track was 100 % network fixes"
without a second read of the DAO. Flagged as a model change the dispatch did not name.

### 1.3 GPX goes through the seam — confirmed, and what that means for evidence

`TrackExportPanel` shares the `Track` it got from `GetTracksUseCase` (`:116`, `TrackGpxExporter
.forContext(context).write(track)`), and `GpxCodec.encode` walks `track.points` (`:38`). So after
Item 1, **every export is filtered.** Stated plainly, as the dispatch asks: **the four exports this
dispatch rests on were unfiltered; a future export will never again show a sub-second point, and
so can never again be used to test this rule.** A tester's GPX will show whether the *surviving*
track looks right, not whether the rule chose correctly. The unfiltered evidence path from here on
is the fix-logging branch, or a direct read of `track_points` on a device — neither is in this
dispatch. This is the cost of "export matches what the user sees", accepted by the owner; recorded
so nobody later reads a clean export as proof the rule is idle.

### 1.4 The four decided points — one disagreement to report, as invited

- **Read seam, not source.** Agreed, for the reason the dispatch quotes.
- **All-sub-second track.** Item 3.
- **Not counted anywhere, not deleted.** Agreed; the mapping is the only change and it writes
  nothing.
- **First/last protection not needed.** **Agreed for the stored track, with one adjacent fact to
  report rather than disagree:** the return-to-start fallback takes `breadcrumbPoints.firstOrNull()`
  (`TrackRecordingViewModel.kt:440`) only when no origin waypoint exists, and the **origin waypoint is
  created from the live fix stream, not from stored points** (`:309`, the first fix passing the
  mode's accuracy gate). A network fix with 20 m accuracy passes the HIGH ceiling of 30 m, so **the
  origin waypoint itself can be a network fix**, and Item 1 does not touch it — the waypoint is
  persisted with its own timestamp (`createdAtEpochMillis`, which is `currentTime`, not the fix's
  clock), so the predicate cannot even be applied to it after the fact. Not this dispatch's scope
  (it says do not change the sampler or the source); reported because "if the first point came from
  the network, it was never where the user was" is exactly true of the origin too, and the origin is
  what the HUD navigates back to.

### 1.5 Gap handling and cost

Adjacent survivors join directly; distance counts across the gap; timestamps of the survivors are
real, so duration is unchanged. Cost: one modulo per row per read, on top of the existing per-row
mapping — nothing.

### 1.6 The moving zigzag — does the evidence support "network fixes too"?

From the dispatch's own table (I cannot read the files; they are not in the repository): track B's
excursions are single-point, 24–36 m out and back, all ten of its sub-second points, and removing
them takes 498 m to 81 m against 27 m straight-line; track D, airplane mode, walked, has no step
over 5.8 m and no sub-second point. **That supports the reading** that B's zigzags were network
fixes — one walk with them, one without, same day, same phone — **but D is one loop at one place and
does not cover the earlier radios-off ~40 m excursion**, which had no network provider to blame. So:
consistent with "no second rule needed" for the network mechanism; silent on whether a second
mechanism exists. No second rule proposed.

---

## ITEM 2 — Cartography's snapshot

### 2.1 Anything else persisting a derived distance or count — searched again on `49b65c7`

`grep` of `data/local/*.kt` for `distanceMeters`, `durationMillis`, `pointCount`: only
`CartographyEntryTrackRefEntity` (`CartographyEntryEntity.kt:68–70`) and its DDL (`Migrations.kt:642–644`).
`OfflineRegionEntity` still has no tile count (read live); `WaypointEntity`, `MushroomLogEntryEntity`
persist positions, not derived figures. **Unchanged: the Cartography track ref is the only one.**
`pointCount` still has no reader — written at `CartographyViewModel.kt:489`, mapped at
`RoomCartographyEntryRepository.kt:98,107`, displayed nowhere. Reported, not fixed.

### 2.2 The build, as accepted

Recompute on next open with write-back before the first frame: when `CartographyViewModel` loads
an entry for editing or the report, for each `TrackDecision` whose track still exists, recompute
`ComputeTrackStatisticsUseCase(track.points)` from the (now filtered) read; if `distanceMeters`,
`durationMillis` or `pointCount` differ, save the entry with the corrected refs **before** publishing
it to state. Rows whose track is gone keep their figure (nothing to recompute from) — recorded in the
entity doc. The entity's "never silently change on reopen" doc comment gets the exception written
beside the rule: a cached distance is not authored content and the cached number was wrong.

**Cost:** one filtered read per kept track per open — the edit screen already does that read for
candidate rows. Not larger than described.

---

## ITEM 3 — observable when it does a lot

### 3.1 Surfaces that exist (read, not invented)

1. **Records' track row subtitle** — `TrackExportPanel.trackSubtitle` (`:103–107`): "N points" /
   "N points · recording". A row state, per track, already the place a track's size is stated.
2. **The map's `SnackbarHost`** (`AvailabilityScreen.kt:1498`), shared since PR #71 with the
   trip-start warning; Long duration, no action.
3. **Cartography's decision rows** — subtitles from `trackSubtitle(distance, duration, unit)`
   (`CartographyEntryEditScreen.kt:481,495`) and the report screen (`:487`).
4. The live map's breadcrumb line, which for fewer than two points draws nothing
   (`SightingsMap.kt:908–909`) — silence, today, which is the case Item 3 forbids.

No new surface is proposed.

### 3.2 Threshold — chosen, and why it does not fire on A, B, C

Two conditions, either of which marks a track as "the rule did a lot":

- **Survivors ≤ 1 with at least 2 stored points** — the empty-track case, unconditional. A track of
  one stored point is one point either way and is not flagged.
- **Excluded fraction > 75 % with at least 10 stored points.** Tracks A, B, C excluded 47 %, 31 %,
  25 % — the rule working. 75 % is deliberately far above the highest of those (47 %) rather than
  just above it: on the owner's device the network provider delivers about one fix in three to four
  (the ~20 s cadence against GPS's ~5 s, from the first findings document), so a correctly working
  device will not approach three in four, while a device whose GNSS clock is not second-aligned
  excludes every GPS fix and lands at or near 100 %. The minimum of 10 stored points stops a
  three-point track that happened to catch two network fixes from flagging itself. Both numbers are
  constants with this reasoning on them and nothing else behind them — there is no data from a
  non-aligned device yet, which is the point of surfacing it.

### 3.3 Shape — proposed, and stopping

- **Stored tracks (Records, Cartography):** the row subtitle says it. "N points" becomes, when
  flagged, **"N points · M more not shown (network fixes)"**, and for the empty case **"No usable
  points — all M fixes were from the network provider"**. Row state, not a dialog, not repeated, and
  it explains a blank entry map in the same breath. Cartography's decision rows get the same suffix
  from the same field.
- **The live recording:** the one case where a blank is dangerous — a tester with a non-aligned
  phone would record a track and see no line while walking. Proposal: **once per recording**, when a
  poll first observes the flag on the active track, one Snackbar in the map host, Long, no action:
  **"Forager can't tell your phone's GPS fixes from network ones, so this track isn't being drawn.
  It is still being recorded."** True, non-modal, once, and it names the consequence for the thing
  the user is looking at. Not repeated on later polls (a `Boolean` in ViewModel state, reset on
  stop, not persisted — no column).
- **What carries it:** `Track.excludedPointCount` from 1.2, and a pure
  `fun Track.isMostlyExcluded(): Boolean` (name to settle) beside the predicate, so the threshold
  lives in one place and every surface reads the same answer.

**Copy is proposed, not final** — it is the owner's to set, as the trip-start strings were.

---

## Verification plan (for the build, after answers)

- Predicate tests, literals by hand: `…000L` kept, `…001L` and `…999L` excluded, `0L` kept
  (millis exactly zero is the boundary and is asserted directly).
- A hand-built mixed track loses exactly its sub-second points; an all-whole-second track loses
  nothing (both matter equally); an all-sub-second track returns empty with `excludedPointCount ==
  n`.
- `RoomTrackRepositoryTest`, in-memory Room: rows inserted, read back filtered, then
  `getPointsForTrack` compared row-for-row to the inserted rows — nothing on disk changes.
- **Per consumer**, the table in 1.1: breadcrumbs after a poll, the Records subtitle count, GPX
  output (the sub-second point's coordinates absent from the file), derived-trip candidates, the
  entry map coordinates, coverage coordinates.
- Cartography: after open, the ref equals the fresh statistics to the metre and the row string
  matches.
- Item 3: the threshold function on the A/B/C proportions (47/31/25 % — pinned as literals) is
  false; on 76 %/10 points and on 0-or-1 survivors it is true; the Records subtitle and the
  Snackbar appear in the flagged case and not in the ordinary case, by node count; the Snackbar
  appears once across two polls.
- Every new test reverted one line at a time, build log checked, forward edit re-checked afterwards.

## Device checks the owner must walk

1. A previously starbursted track (A or B) draws clean and its distance figure drops — and in
   Cartography, the kept-track subtitle reads the new figure after reopening the entry.
2. **The airplane-mode control track (D) is unchanged** — same line, same distance, no suffix on
   its row. This is the honest-track case in the field.
3. A fresh recording on the owner's phone draws as before with no Snackbar.

## Required disclosure

**Confirmed:** every file and line above on `49b65c7`; the seam, its three callers, the single DAO
holder, the six `points` readers; GPX through the seam; the only persisted derived value; the origin
waypoint's source and its timestamp field; the breadcrumb line's two-point minimum; the surfaces.

**Inferred:** the ~1-in-3-to-4 network cadence used to justify 75 % (from the first findings
document's ~20 s / ~5 s reading of one track); that a non-aligned device would land near 100 %
(follows from the mechanism, unobserved); the reading of tracks B and D, from the dispatch's table,
not the files.

**Could not determine:** anything about a second device; whether the earlier radios-off excursion
carried a sub-second stamp (that track was not among the four).

**Premises in this dispatch that were wrong:** none found. One refinement: "first and last point
protection: not needed" is right for the stored track; the origin *waypoint* is a separate object
the rule cannot reach (1.4).

**Decided without cover, flagged for the owner:** `Track.excludedPointCount` as a read-computed
domain field (a model change); the 75 % / 10-point threshold and the ≤ 1-survivor rule; the two
subtitle strings and the one Snackbar string; once-per-recording for the live case; leaving the
origin-waypoint finding as a report.
