# Pre-build report — GPX export carries the full record

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Dispatch:** "GPX export carries the full record" (written 2026-09-07, type: report before
building, stop-and-ask on section B).
**Date:** 2026-09-08.
**Branch read:** `claude/new-session-wjlg6g`, cut from `origin/main` at `8eacc91` (PR #76 merge).
Every `file:line` below is on that commit unless it names PR #77's branch.
**Built:** nothing. No test was run; no build was run. The one thing executed was a four-value
Java check of `Instant.toString()` in the sandbox (section B.0).

---

## Summary, for the owner

1. **The exporter reads the same filtered seam as the display, through `getAll`, not
   `getForDay`.** Filtering is a property of the read seam, not of the exporter. Carrying the full
   set is a **new read path**, not a one-line change (section A.2).
2. **The two speed columns are not on `main`.** `main` is schema 14. Schema 15 with
   `speedMetersPerSecond` / `speedAccuracyMetersPerSecond` exists only on PR #77's branch
   (`claude/new-session-b7z9bg`, open, unmerged). This session's branch is cut from `main`.
   **Base-branch question, not resolved here** (section A.3).
3. **The "whole-second truncation bug" is not a bug in the encoder.** The encoder prints
   milliseconds whenever they are non-zero. Every exported point is whole-second because every
   *kept* point is whole-second by the rule's own definition; the fractional points are absent,
   not truncated. Verified by running the JDK's formatter, and already recorded in the
   discriminator findings (section B.0).
4. **Recommendation for section B: variant B1, placed in `<trk><extensions>` rather than
   `<metadata><extensions>`, with the block declaring itself authoritative in-band.** B2's join
   key does not exist on the object the exporter receives, and an ordinal key has the same
   round-trip hazard B2 was meant to avoid (section B.2).
5. **Today's export carries no waypoints at all**, so the reference case's labelled waypoints
   cannot round-trip through it as it stands. Whether they should be added is a scope question
   this report raises (section C.1).
6. **The filter keys on the timestamp, not on accuracy** — the dispatch's "could not determine"
   is determinable from the code (section A.2).

---

## A. What to report

### A.1 Where the exporter is and what it reads

The exporter is `TrackGpxExporter.write`
(`app/src/main/java/com/zynergylabs/forager/app/export/TrackGpxExporter.kt:25-30`):

```kotlin
fun write(track: Track): File {
    exportDir.mkdirs()
    val file = File(exportDir, fileNameFor(track))
    file.writeText(GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList())))
    return file
}
```

It reads nothing itself. It is handed a `Track` and serialises `track.points` through
`GpxCodec.encode` (`app/src/main/java/com/zynergylabs/forager/app/domain/GpxCodec.kt:31-51`), which emits
`lat`, `lon`, `<ele>` (when non-null) and `<time>` per point and nothing else (`:46-51`).
`waypoints` is hard-wired to `emptyList()`.

The `Track` arrives by this chain, every hop read:

| Hop | Location |
|---|---|
| Share icon → `exportAndShareTrack(context, track)` | `ui/track/TrackExportPanel.kt:95-100`, `:127-130` |
| `TrackExportList(tracks = tracks)` | `ui/log/RecordsTab.kt:199` |
| `tracks = trackUiState.tracks` | `MainActivity.kt:437` |
| `TrackRecordingViewModel.loadTracks()` → `getTracks()` | `ui/track/TrackRecordingViewModel.kt:387-395` |
| `GetTracksUseCase` → `repository.getAll()` | `domain/GetTracksUseCase.kt:9` |
| `RoomTrackRepository.getAll` → `entity.toDomain(dao.getPointsForTrack(id))` | `data/repository/RoomTrackRepository.kt:29-31` |

So the dispatch's inference ("the same filtered seam as the display") is right, and its named
method (`getForDay`) is wrong: the Records list is `getAll`. `getForDay` is the derived-trip read
(`domain/GetDerivedTripUseCase.kt:42`). Both go through the same mapping, so the distinction
changes nothing about the filtering; it matters only for anyone who goes looking for the call.

### A.2 Is the filtering in the seam or in the exporter?

**In the seam.** `RoomTrackRepository.toDomain` (`RoomTrackRepository.kt:66-79`):

```kotlin
private fun TrackEntity.toDomain(rows: List<TrackPointEntity>): Track {
    val stored = rows.map(TrackPointEntity::toDomain)
    // The read-seam rule — see NetworkProviderFix.kt. Applied here and nowhere else.
    val kept = excludeNetworkProviderFixes(stored)
    return Track(..., points = kept, ..., excludedPointCount = stored.size - kept.size)
}
```

Every read of `track_points` — `getAll`, `getById`, `getForDay` — passes through this one
function, and the class's own doc comment states the guarantee: "no consumer can see an excluded
point and no consumer has to remember to apply the rule" (`:12-17`). The exporter contains no
filter. The rule itself is `TrackPoint.isNetworkProviderFix() = timestampEpochMillis % 1_000L != 0L`
(`domain/NetworkProviderFix.kt:40`).

**Consequence for the build:** "carry the full set" cannot be done inside the exporter, because
the `Track` it receives no longer contains the excluded points — `Track` carries only `points`
(the survivors) and an integer `excludedPointCount` (`domain/model/Track.kt:37-45`). The exporter
needs a second read that returns the stored sequence. That is a new repository method and a new
type, not a flag on the existing one:

- **New interface method** on `TrackRepository`, e.g. `getStoredPoints(trackId): Result<List<StoredTrackPoint>>`,
  returning every row in DAO order (`TrackDao.getPointsForTrack` orders by
  `timestampEpochMillis ASC`, `data/local/TrackDao.kt:47`) with the verdict computed **in the
  same mapping** by the same predicate, so the verdict cannot drift from the rule that produced
  the display — the same argument `Track.excludedPointCount` already makes for itself
  (`Track.kt:29-35`).
- **A distinct type, not `TrackPoint`.** The seam's guarantee is that a `TrackPoint` a consumer
  holds is a kept one. A `StoredTrackPoint(point: TrackPoint, verdict: Verdict, ordinal: Int)`
  (or similar) keeps that true: nothing that consumes `Track.points` can be handed an excluded
  point by accident, and the exporter is the only caller. CLAUDE.md, Building: "new capability
  is a new function, class, or path — not a conditional threaded into existing working code".
- **Not a raw DAO call from the exporter.** The exporter is Android-layer and depends on the
  domain interface; PR #77's distance pulse already recorded that today "a `Track` with points
  reaches a consumer only through the seam" and named `GpxCodec.decode` as the one unfiltered
  producer with no caller (`docs/audits/2026-09-07-track-distance-display-pulse.md:138-150` on
  that branch). This dispatch would add a second, deliberate, named door; it should be as
  visible as that one.

**The dispatch's "could not determine" is determinable.** The filter keys on the timestamp's
millisecond remainder and on nothing else. Accuracy is not read by the predicate. The perfect
confounding the planner saw in the data (all excluded points fractional *and* 12.6–29.8 m) is
the mechanism the discriminator findings describe — the network provider's fixes carry both
signatures — but the code tests one of them.

### A.3 Are `accuracyMeters` and the speed columns reachable from the exporter's read?

**`accuracyMeters`: yes, today, without any new read.** It is a column
(`data/local/TrackPointEntity.kt:32`), mapped at the seam (`RoomTrackRepository.kt:93`), and on
every `TrackPoint` the exporter already receives. The codec simply does not write it
(`GpxCodec.kt:46-51`), and `GpxCodecTest:36-38` asserts that it decodes as `null` — "not part of
the GPX schema this app writes". Writing it into the extensions block needs no schema change.

**The speed columns: not on this branch, and not on `main`.**

| Fact | Evidence |
|---|---|
| `main` (`8eacc91`) database version | `ForagerDatabase.kt:153` — `version = 14` |
| `TrackPointEntity` on `main` | `TrackPointEntity.kt:26-34` — `id, trackId, lat, lng, altitude, accuracyMeters, timestampEpochMillis`; no speed fields |
| Any `speedMetersPerSecond` in `app/src/main` on `main` | one hit: a `Log.d` string in `location/AndroidLocationTracker.kt:59` (the per-fix instrument log, PR #76); nothing stored |
| Branches on `origin` with `version = 15` or the columns | exactly one: `claude/new-session-b7z9bg` — **PR #77**, open, 17 commits ahead of `main`, based on `8eacc91` |
| On that branch | `MIGRATION_14_15` adds both nullable `REAL` columns; `TrackPointEntity`, `TrackPoint` (defaulted to `null`) and both seam mappings carry them (`git diff origin/main origin/claude/new-session-b7z9bg` over those files) |

So once PR #77 merges, both columns arrive on `TrackPoint` through the same seam mapping and are
reachable exactly as `accuracyMeters` is. Before it merges, they do not exist here.

**This is the base-branch pitfall from CLAUDE.md, and it is a stop-and-ask.** The dispatch
describes a schema-15 database ("the two new schema-15 columns"; "they are null on all 221
existing rows"), so the planner measured against PR #77's build. This session's designated branch
is cut from `main` at schema 14. Two ways the build dispatch can go, and the choice is the
owner's:

- **Sequence after PR #77 merges (recommended).** The format is designed now to carry `spd` /
  `spdAcc` as optional attributes, omitted when null; the build lands after #77 and writes them
  from `TrackPoint`. Nothing about the format changes either way, and the export never claims a
  field the branch cannot supply.
- **Build on `main` now, speed omitted.** Legal, and the format is identical; the speed
  attributes simply never appear until a follow-up after #77. Cheaper in calendar time, one more
  dispatch in total.

Building on top of PR #77's branch is the third option and the one this report does not
recommend: it stacks an unmerged change on an unmerged change, which is the shape that produced
the `MIGRATION_4_5` collision CLAUDE.md records.

### A.4 What export paths exist

**Per-track only.** The share icon on each Records row (`TrackExportPanel.kt:95-100`) is the
only entry point that reaches `TrackGpxExporter`. The only other `ACTION_SEND` in the app is the
crash-log share (`ui/crash/CrashLogPanel.kt:201-206`). There is no `ACTION_SEND_MULTIPLE`, no
Storage Access Framework `ACTION_CREATE_DOCUMENT`, no "share all", no backup of any kind
(grep over `app/src/main` for each). The export goes to the cache directory
(`TrackGpxExporter.kt:42-48`) and exists only long enough for the share target to read it.

**What an all-tracks path would take** — reported, not built:

1. **The file.** GPX 1.1 allows `<trk>` unbounded in one document (schema, section B.1), so a
   whole-journal export is one `<gpx>` with N `<trk>` elements. This is the strongest reason to
   place the raw record inside each `<trk>` rather than under `<metadata>` (section B.2): under
   `<trk>` the track scopes its own block; under `<metadata>` every block would need its own
   track key.
2. **The exporter.** A `write(tracks: List<Track>, ...)` overload or a sibling class; `GpxDocument`
   currently holds `track: Track?` — at most one — by its own doc comment
   (`domain/model/GpxDocument.kt:8-11`), so the document model widens to a list. `GetTracksUseCase`
   already returns every track.
3. **The vehicle.** A 50-track file handed to a chat app through the share sheet is the wrong
   vehicle for a backup; `ACTION_CREATE_DOCUMENT` (save to Documents / Drive) is the one that
   makes sense, which is the SAF work `GpxCodec`'s own doc comment deferred (`GpxCodec.kt:19-21`).
   A single "Share all as GPX" row at the top of the Recorded Tracks sub-tab would be the entry
   point; that is a UI decision for the owner.
4. **What GPX still would not back up.** Mushroom-log entries, photos, cartography entries and
   plans are not GPX. An all-tracks GPX is a tracks-and-waypoints backup, not a journal backup.
   The project record's premise that GPX makes a signing mistake survivable is only partly
   restorable by anything in this dispatch's family; the rest is a database export, which is a
   different queue item.

---

## B. Format

### B.0 The millisecond finding — corrects the dispatch's premise

`GpxCodec.kt:49` writes `Instant.ofEpochMilli(point.timestampEpochMillis)` via string
interpolation, which is `Instant.toString()` — ISO-8601, printing fractional seconds only when
non-zero. Run in this sandbox's JDK against four values:

```
1756414400000 -> 2025-08-28T20:53:20Z      -> parse -> 1756414400000 OK
1756414400123 -> 2025-08-28T20:53:20.123Z  -> parse -> 1756414400123 OK
1756414400120 -> 2025-08-28T20:53:20.120Z  -> parse -> 1756414400120 OK
1756414400001 -> 2025-08-28T20:53:20.001Z  -> parse -> 1756414400001 OK
```

Nothing is truncated. The same fact is already in the audit trail: the coder's answer in
`docs/audits/2026-09-06-timestamp-discriminator-findings.md:164-166` records that export "prints
no fractional seconds when the millisecond part is zero", and
`NetworkFixExclusionPerConsumerTest:137-150` asserts a fractional stamp (`.500Z`) is *absent*
from an export precisely because the seam removed the point, not because the encoder rounded it.

So the 135 whole-second stamps in track A's export are whole-second because the rule keeps only
whole-second points. The 60 fractional stamps are not in the file because the 60 points are not
in the file. The dispatch's "decided beyond scope" item — that truncation is a bug to fix
regardless — has nothing to fix in the encoder.

What *is* worth specifying for the new block: print the timestamp in a **fixed three-decimal
form** (`2026-09-06T12:40:07.000Z`), not `Instant.toString()`'s variable form, so the field's
presence is unconditional and a reader does not need to know the JDK's rule. The `<trkpt><time>`
can stay as it is; both forms are valid `xsd:dateTime`.

Caveat: verified on the sandbox JDK. Android's `java.time` is the same OpenJDK-derived
implementation, and the four exported files the findings documents parsed are consistent with it,
but no device run was made here.

### B.1 Where GPX 1.1 allows `<extensions>` — verified against the schema

Fetched from `https://www.topografix.com/GPX/1/1/gpx.xsd` during this session, child sequences
in order:

| Type | Sequence |
|---|---|
| `gpxType` | `metadata?, wpt*, rte*, trk*, extensions?` |
| `metadataType` | `name?, desc?, author?, copyright?, link*, time?, keywords?, bounds?, extensions?` |
| `trkType` | `name?, cmt?, desc?, src?, link*, number?, type?, extensions?, trkseg*` |
| `trksegType` | `trkpt*, extensions?` |
| `wptType` (also `trkpt`) | `ele?, time?, magvar?, geoidheight?, name?, cmt?, desc?, src?, link*, sym?, type?, fix?, sat?, hdop?, vdop?, pdop?, ageofdgpsdata?, dgpsid?, extensions?` |
| `extensionsType` | `any` from `##other` namespace, `processContents="lax"`, unbounded |

The planner's constraint is confirmed: `<metadata>` is a fixed sequence and takes foreign
elements only inside its trailing `<extensions>`. Two more facts the dispatch did not have:
`<extensions>` is also legal directly under `<trk>` (before its `<trkseg>`s), under `<trkseg>`,
and at the document root after the last `<trk>`. `hdop`/`vdop`/`pdop` exist on a point but are
dilution-of-precision ratios, not metres, so accuracy in metres has no standard slot — the
dispatch's premise holds.

### B.2 Recommendation: B1, inside `<trk><extensions>`, declared authoritative in-band

**Placement: `<trk><extensions>`, not `<metadata><extensions>`.** Three reasons, the first
decisive:

1. A multi-track file (A.4) needs each raw record bound to its track. Under `<trk>`, containment
   is the binding; under `<metadata>`, every block would carry a track id and every consumer
   would join on it. Choosing `<trk>` now costs nothing and removes a join from the all-tracks
   export before it exists.
2. The block sits next to the `<trkseg>` it claims to be the source of, which is where a human
   reading the file looks for it.
3. Consumers ignore it identically. Both are `##other`/lax extension points; a tool that renders
   `<trkseg>` and skips unknown elements does so wherever the block is. This does not change the
   owner's ruling that the rendered track is the filtered path.

**Variant: B1, the full raw sequence, over B2.**

B2's stated cost is a join and a key. Read against the code, the key is the problem:

- The stored row has a unique key — `TrackPointEntity.id`, an autogenerated rowid
  (`TrackPointEntity.kt:27`) — but the seam strips it: `TrackPoint` has no id
  (`RoomTrackRepository.kt:89-95`; `domain/model/TrackPoint.kt:12-18`). Keying the kept points'
  extras to their `<trkpt>` by row id means adding the id to the domain point, or a third type.
- Timestamp as key: two kept points on today's sampler are at least `minIntervalMillis` apart
  (`domain/LocationSampler.kt:28-29`; the tightest mode is 5 000 ms,
  `domain/model/TrackRecordingMode.kt:27`), so kept-point timestamps are unique **on a recording
  that respected the sampler**. That is a property of the sampler, not of the table:
  `track_points` has no uniqueness constraint on `(trackId, timestampEpochMillis)`, and
  `insertPoints` is `REPLACE` on the rowid only (`TrackDao.kt:54-55`). A key that is unique only
  by an invariant nothing enforces is the kind of claim CLAUDE.md asks not to be built on.
- Ordinal position in `<trkseg>` is the only key that needs no invariant — and it is exactly the
  key that breaks under the round-trip edits B2 was chosen to be safe from: drop one `<trkpt>` in
  an editor and every later key silently shifts by one.

So B2 has B1's round-trip hazard *and* a join, and B1's one real cost — the kept points appear
twice — is answered by the mitigation the dispatch already specifies. Size is not a cost: the
largest track is "at most a few thousand points" (`Track.kt:14-17`); at roughly 150 bytes a
point the block adds a few hundred kilobytes at most, and 30 KB for track A.

**The authority declaration, in-band and machine-readable.** The block's root element carries
`authority="record"` and `trksegDerivedFrom="record"` as attributes, plus the three counts
(`storedPoints`, `keptPoints`, `excludedPoints`). A consumer that finds `<trkpt>` count ≠
`keptPoints` knows the rendered track was edited and the block wins. An edit that keeps the count
but moves a coordinate is not detected by that check; a content digest would detect it, and this
report does not propose one — CLAUDE.md, Building: no speculative correction logic without real
data showing the case. The declaration is what the dispatch requires; the count is a cheap tell
on top of it.

**Illustrative shape** (attribute-only points: compact, stable names, no prose among the
numbers; the rule name is recorded so a future second rule can be told apart):

```xml
<gpx version="1.1" creator="Forager" xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:forager="https://github.com/slayer8366/Forager/gpx/1">
  <trk>
    <name>…</name>
    <extensions>
      <forager:record schema="1" authority="record" trksegDerivedFrom="record"
                      trackId="3aec7001-1fac-454d-b716-fde89abf79f4"
                      startedAt="2026-09-06T12:40:07.000Z" endedAt="2026-09-06T13:22:51.000Z"
                      originWaypointId="…"
                      rule="timestampMillisNonZero"
                      storedPoints="195" keptPoints="135" excludedPoints="60">
        <forager:pt i="0" t="2026-09-06T12:40:07.000Z" lat="…" lon="…" ele="…" acc="4.2" v="kept"/>
        <forager:pt i="1" t="2026-09-06T12:40:12.347Z" lat="…" lon="…" ele="111.6" acc="18.4" v="excluded"/>
        <forager:pt i="2" t="…" lat="…" lon="…" acc="5.0" spd="1.21" spdAcc="0.40" v="kept"/>
        …
      </forager:record>
    </extensions>
    <trkseg>
      <!-- the 135 kept points, exactly as today -->
    </trkseg>
  </trk>
</gpx>
```

Field rules: `i` is the stored ordinal in DAO order (0-based); `t` is fixed three-decimal
ISO-8601; `lat`/`lon`/`ele` as stored, `ele` omitted when null; `acc` omitted when null; `spd` /
`spdAcc` omitted when null (present only after schema 15); `v` is `kept` or `excluded`. The
namespace URI is a name, not a fetchable document — the same convention Garmin's extension
namespaces use — and carries a `/1` so a second schema can exist beside the first.

**What a replay-harness reader needs and gets:** every stored row, in order, with the verdict,
timestamp at full precision, accuracy and (post-#77) speed; the rule that produced the verdict;
the track's own id so the case can be named; and the counts to check the file against. Either
view is reconstructible: the rendered walk is the `v="kept"` subset, the raw walk is all of it.

**Sidecar: rejected.** B1-in-`<trk>` satisfies all five of the dispatch's constraints in one
file. The sidecar's cost — a tester moves one file and loses half the backup — is the decisive
one, and there is a second: the share path is a single-stream `ACTION_SEND`
(`TrackExportPanel.kt:140-147`), so a sidecar needs `ACTION_SEND_MULTIPLE`, which every chat
target handles differently and some drop to one attachment.

### B.3 Two things the build dispatch will meet

- **An existing assertion that will go red for the right reason.**
  `NetworkFixExclusionPerConsumerTest:146-147` asserts `assertFalse(gpx.contains(".500Z"))` over
  the *whole file*. Once excluded points ride in the block, `.500Z` is in the file and that test
  fails. Its claim is "the rendered track carries survivors only", and the fix is to re-scope the
  assertion to the `<trkseg>` content — a rewrite of the claim, in scope of the build, not a
  silenced test. Recorded now so nobody reads it as an unrelated failure mid-task.
- **`*.gpx` is gitignored** (`.gitignore:17`, timestamp-filter completion report). No exported
  file, the reference case included, can be committed as a fixture as-is. Section C is written
  around that.

---

## C. The reference case

Track `3aec7001-1fac-454d-b716-fde89abf79f4` appears nowhere in this repository or on any branch
except PR #77's distance pulse, which names it as the 195/135/60 track with a 1957.4 m raw and
733.2 m filtered length. Its file is not in the repo (`*.gpx` ignored) and this sandbox has no
device and no copy of the database. So the verification has an in-repo half and an owner's half,
and neither derives its expected values from the exporter.

### C.1 A precondition the dispatch did not state: the waypoints are not in the export

`TrackGpxExporter.write` passes `waypoints = emptyList()` (`TrackGpxExporter.kt:28`). The
reference case's labelled waypoints — "ground truth for activity state" — are therefore not in
today's file and cannot round-trip through it. `WaypointRepository.getForTrack(trackId)` exists
(`domain/WaypointRepository.kt:29`), and `Waypoint` carries `trackId` and `designation`
(`domain/model/Waypoint.kt:30-32`; `WaypointDesignation.ORIGIN | END`), neither of which is a
standard GPX field.

**Ask:** should the build add the track's waypoints as `<wpt>` elements with
`<extensions><forager:waypoint id="…" trackId="…" designation="ORIGIN"/></extensions>`? It is one
repository call in the export path and one extension element per waypoint, and without it
section C's requirement cannot be met. This report recommends yes and does not decide it.

### C.2 In-repo, headless — expected values are literals, the parser is not the codec

A Robolectric test over real Room in the `NetworkFixExclusionPerConsumerTest` shape (seed through
`appendPoints`, export through `TrackGpxExporter.write` on the `Track` from `GetTracksUseCase`,
`assertDiskUnchanged` after):

- Seed a hand-written track with the reference case's *shape* — a mix of whole-second and
  fractional-millisecond points, some with null `ele`, some with null `acc`, in an order that
  interleaves kept and excluded — with every value a literal in the test.
- Parse the written file with `javax.xml.parsers` directly (a DOM walk written in the test), **not**
  `GpxCodec.decode`; assert the `forager:pt` list equals the seeded literals field by field
  including the fixed-format timestamps, the verdicts equal `timestamp % 1000 != 0` computed in
  the test from the literals, the counts on the block equal the literal counts, and the
  `<trkpt>` sequence equals the `v="kept"` subset in order.
- The reverted-variant check for this test: a one-line revert that drops the verdict attribute,
  or writes `Instant.toString()` instead of the fixed format, and the failure message must name
  that field. Per CLAUDE.md, the revert runner reads the build log for compile errors before it
  reads the XML, restores from a saved copy, and the forward change is confirmed present after.

This proves the format against values the exporter never saw. It does not prove the reference
case.

### C.3 The owner's half — SQL is the expectation, a second parser is the judge

For the captured database (schema 14 or 15) and one fresh export of the reference track from
the built APK:

```sql
SELECT COUNT(*) FROM track_points WHERE trackId = '3aec7001-1fac-454d-b716-fde89abf79f4';
-- expect 195
SELECT SUM(timestampEpochMillis % 1000 != 0) FROM track_points
 WHERE trackId = '3aec7001-1fac-454d-b716-fde89abf79f4';
-- expect 60
.mode csv
SELECT id, timestampEpochMillis, lat, lng, altitude, accuracyMeters
  FROM track_points WHERE trackId = '3aec7001-1fac-454d-b716-fde89abf79f4'
 ORDER BY timestampEpochMillis ASC;
-- the expected block, row for row
SELECT id, name, designation, createdAtEpochMillis, lat, lng FROM waypoints
 WHERE trackId = '3aec7001-1fac-454d-b716-fde89abf79f4';
-- the expected <wpt> set, if C.1 is accepted
```

Then a short script outside the app — Python's `xml.etree` is enough — reads the exported file
and diffs the `forager:pt` rows against the CSV (timestamp formatted from the integer in the
script, not taken from the file), diffs the `<trkpt>` sequence against the CSV rows whose
millisecond remainder is zero, and diffs the `<wpt>` set against the waypoints query. Nothing in
that script imports `GpxCodec`, and the expected 195/135/60 come from SQL, not from the app.
The two pre-existing export files the planner parsed remain the baseline for "the rendered
`<trkseg>` is unchanged": the new export's `<trkseg>` should be byte-identical to the old file's.

This is device work and the owner's; the sandbox cannot claim any of it.

---

## Out of scope — confirmed untouched

No displayed number, no change to the predicate, no import, no pooling, no test altered. Nothing
was built; no test suite was run, so there is no skip count to report.

---

## Required disclosure

### Confirmed (read in this session, `file:line` cited above)

The full call chain from the share icon to `RoomTrackRepository.getAll`; that the filter runs at
`toDomain` and nowhere else; the predicate keys on the timestamp; `accuracyMeters` is on the
domain point the exporter receives; `main` is schema 14 with no speed columns; schema 15 exists
only on PR #77's branch and is mapped through the same seam there; the exporter passes no
waypoints; the only export entry point is the per-row share icon; `Instant.toString()` prints
milliseconds when non-zero (executed); the GPX 1.1 `<extensions>` placements (fetched schema);
`*.gpx` is gitignored; `NetworkFixExclusionPerConsumerTest:146-147` asserts over the whole file.

### Inferred

- That Android's `java.time.Instant.toString()` behaves as the sandbox JDK's does. Same lineage;
  the parsed exports are consistent with it; not run on a device here.
- That kept-point timestamps are unique per track. True by the sampler's interval rule on a
  recording that went through it; not enforced by the table.

### Could not determine

- The reference track's actual contents — the dispatch's 195/135/60 and 1957.4/733.2 m are taken
  as given from the planner's parse and PR #77's pulse; nothing here re-derived them.
- Whether PR #77 will merge before the build dispatch is written. A.3 is the ask.

### Premises in the dispatch that were wrong

- **"Timestamps are truncated to whole seconds."** The encoder preserves milliseconds; the file's
  points are whole-second because only whole-second points survive the seam. There is no
  truncation to fix.
- **"`trackRepository.getForDay`, the same filtered seam the display uses."** Same seam, but the
  Records list and the export read `getAll`. `getForDay` is the derived-trip read.
- **"The two new schema-15 columns."** Not on `main`; on an open PR's branch.
- **"The annotated track … its waypoints are labelled."** Whatever labels they carry, the export
  does not write waypoints, so the file the dispatch asks to round-trip cannot carry them today.

### Decided beyond scope, for the owner to confirm or reverse

- **`<trk><extensions>` over `<metadata><extensions>`.** The dispatch narrowed to the latter; this
  report moves the block one level down for the multi-track reason in B.2. Consumer behaviour is
  identical.
- **Fixed three-decimal timestamps in the block.** Not required by any constraint; chosen so the
  field's form does not depend on the JDK's printing rule.
- **The `rule` attribute and the three counts on the block.** Beyond "verdict per point"; added so
  a second rule and an edited `<trkseg>` are each detectable.
- **Recommending waypoints in the export** (C.1). A scope addition; raised as an ask, not decided.
- **Sequencing the build after PR #77** (A.3). A recommendation; the base branch is the owner's
  call.

### Rulings this report is built on, unchanged

In-app display stays filtered; the export carries the full data set; the rendered `<trk>` is the
filtered path with the raw points riding as data. All from the owner, 2026-09-07. Only section B's
mechanism was open, and this report closes it with a recommendation pending the two asks above.

---

## Addendum — owner's response, 2026-09-08

Recorded verbatim in substance so the build dispatch starts from rulings, not from this report's
recommendations.

### The two corrections, acknowledged

- **Truncation.** The owner filed whole-second truncation as a data-destroying defect to fix
  regardless; it is not a defect. `Instant.toString()` emits milliseconds when non-zero, and the
  exported stamps are whole-second because only whole-second points survive the seam. The
  disclosure section caught it. Nothing in the encoder changes on that account; the fixed
  three-decimal form in the block (B.0) stands as a format choice, not a fix.
- **"Sub-second."** The predicate keying on the timestamp resolves the confounding the planner
  could not. It also partly rehabilitates the original handoff wording: "sub-second" meant the
  fractional-millisecond field, not a sampling interval. The refutation that no sub-second
  *intervals* exist was right, and wrong to imply the record was simply mistaken — it was
  ambiguous. Fixed in the record as ambiguity, not error: a terminology note is appended to
  `2026-09-06-timestamp-discriminator-findings.md`, where the term first appears.

### Rulings

| Question | Ruling |
|---|---|
| Base branch (A.3) | **Wait for PR #77.** A format without the speed fields means a second format revision once they land, and the replay harness is the reason the format exists. The beta signing dispatch may add commits; where those land relative to #77 is to be decided first. |
| Waypoints (C.1) | **Include them.** The annotated track is the harness's first replay case because its waypoints are labelled ground truth for activity state; an export that drops them cannot serve that purpose, which is most of the justification for B1. |
| Placement (B.2) | **`<trk><extensions>` over `<metadata><extensions>` — accepted** as an improvement on the planner's recommendation. |

Section B is therefore closed: B1, inside `<trk><extensions>`, authority declared in-band, with
the track's waypoints as `<wpt>` elements carrying `id`, `trackId` and `designation` in their own
`<extensions>`.

### A hazard the owner named, and the instrument for it

`excludeNetworkProviderFixes` keys on fractional milliseconds as a **proxy for provider, not
provider itself**. It has held across five data sets on one device. If another device's GPS fixes
land on non-whole milliseconds, the rule silently discards good fixes, and the symptom is a track
that looks fine and is simply shorter — the same failure family, one device deep. The B1 export is
what makes the predicate testable on real hardware variety: tester files will carry every stored
point with its verdict, so excluded points along a stretch the tester walked are the rule being
wrong on that hardware.

Done in this commit: a "did the track look shorter than your walk" question added to the trip
report's location block (`docs/beta/trip-report.md`), with its rationale and its never-cut status
recorded in `docs/beta/README.md`. Note for whoever merges: PR #77 edits the same README region
(its "within …" question, the line count, and the never-cut item); the merge will conflict on the
count line and the never-cut sentence. **Resolve by keeping both questions and re-counting the
block's lines and the location questions from the merged file** — do not carry a number written
here into a file marked never-cut. Any count this addendum could give is a prediction about a
merge that has not happened; if #77's README region changes before it lands, a recorded number
applied mechanically would be wrong exactly where being wrong matters most.

Not done, and worth a line in the build dispatch: `NetworkProviderFix.kt`'s doc comment already
states the limit ("a property of a device's GNSS stack, not a guarantee"); when the export lands,
that comment should point at the export as the instrument that checks it, and the beta README's
"until then the question is the only instrument" sentence should be updated to say the file now
is.
