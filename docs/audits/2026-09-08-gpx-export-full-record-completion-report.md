# Completion report — GPX export carries the full record

**Type:** Build completion. **Dispatch:** `ec49043e-dispatchbuildgpxexport.md` (2026-09-07, base: merged main, schema 15).

---

## Stop-and-ask on the dispatch itself, resolved before building

The dispatch's own cold-start context names its authority document as
`docs/audits/2026-09-08-gpx-export-full-record-prebuild-report.md` and says to read it before
starting. **That file does not exist** — not on this branch, not on `main`, not anywhere in git
history (`git log --all -- '*gpx-export-full-record-prebuild-report*'` returns nothing), and it is
not listed in this README. Per CLAUDE.md ("a claim about this codebase names a file and location, or
is stated as unverified"), this is flagged rather than silently assumed.

What the dispatch calls "corrections carried from the report — do not re-derive these" turned out to
be directly checkable against the repository regardless of the missing document, and both check:

- **The exclusion predicate is `timestampEpochMillis % 1_000L != 0L`, at `NetworkProviderFix.kt:40`**
  (`fun TrackPoint.isNetworkProviderFix()`). Confirmed by reading the file.
- **Whole-second timestamps are not a truncation bug** — `GpxCodec.encodeTrackPoint` calls
  `Instant.ofEpochMilli(...).toString()`, which is standard `java.time` behaviour (prints
  milliseconds only when non-zero), unchanged by this dispatch.

Everything else the dispatch specifies (the full-record read path, the exporter format, the
round-trip test, the two doc updates) is a concrete, self-contained spec that doesn't depend on the
missing report's own reasoning — only on facts checkable in the repository, which is what this
report does throughout. **Not resolved**: the reference track's real per-point data (see "What could
not be verified" below) — that gap traces directly to the missing report, since it would have been
the report's job to record those points or point at where to find them.

---

## What was built

### 1. The full-record read path

- **`TrackPointRecord`** (`domain/model/TrackPointRecord.kt`, new type): a stored `TrackPoint` paired
  with `kept: Boolean` — the read seam's verdict.
- **`TrackRepository.getFullRecord(id): Result<List<TrackPointRecord>>`** — a new interface method,
  not a flag on `getById`. `getAll`/`getById`/`getForDay`/`toDomain` are untouched; every existing
  display consumer still gets the filtered read.
- **`RoomTrackRepository.getFullRecord`** reads `dao.getPointsForTrack(id)` directly (bypassing
  `excludeNetworkProviderFixes`) and tags each point by calling `isNetworkProviderFix()` — the same
  predicate `toDomain` uses, not a copy. Empty list for a track with no stored points or no such id
  (same as the DAO query it wraps), never a failure.
- Three fakes (`InMemoryTrackRepository`/`FailingTrackRepository` in
  `TrackRecordingViewModelTest.kt`, `InMemoryTracks` in `GetTrackOriginWaypointUseCaseTest.kt`)
  updated to implement the new interface method.

### 2. The exporter and `GpxCodec` (format B1)

- **`GpxDocument`** gained `fullRecord: List<TrackPointRecord> = emptyList()`.
- **`GpxCodec.encode`** writes the raw sequence into `<trk><extensions><forager:fullRecord
  authoritative="true" trksegDerivedFromFullRecord="true" pointCount="N">`, one `<forager:point
  lat=… lon=… timeEpochMillis=… ele=… accuracyMeters=… speedMetersPerSecond=…
  speedAccuracyMetersPerSecond=… kept=…/>` per stored point (optional fields omitted when `null`,
  not zeroed), positioned per the GPX 1.1 schema (`trkType`: `name?, …, extensions?, trkseg*` —
  checked against the schema) immediately before `<trkseg>`. `<trkseg>` itself is unchanged: exactly
  the filtered points, in the same `<trkpt>` shape as before this dispatch.
  `timeEpochMillis` (a `Long` attribute, not an ISO string) is the full-precision field — the literal
  stored value, with no timezone or formatting round-trip to lose precision through.
  The authoritative declaration is two attributes on the extension element itself, not a comment, so
  a parser can read the relationship without out-of-band documentation.
- **Waypoints**: `Waypoint.trackId`/`Waypoint.designation` have no standard GPX element, so they're
  carried in `<wpt><extensions><forager:waypoint trackId=… designation=…/>`, omitted entirely for an
  ordinary waypoint with neither set. One `forager` namespace serves both the full-record and the
  waypoint extension (coder's call, per the dispatch's open question) — one small vocabulary for one
  app's own data, rather than two.
- **`GpxCodec.decode`** reads `<forager:fullRecord>`/`<forager:point>`/`<forager:waypoint>` back
  (non-namespace-aware DOM parsing, matching this file's existing pattern for `<trk>`/`<wpt>`), and
  degrades to an empty `fullRecord` / a `trackId`/`designation`-less `Waypoint` for any document
  without the extension — every GPX file from outside this app, and every one this app wrote before
  this dispatch — never throwing over content this app itself controls.
- **`TrackGpxExporter.write`** now takes `fullRecord` and `waypoints` as **required** parameters, not
  defaulted. Reasoning recorded at the method: `Track` alone has no way to reach an excluded point,
  so only a caller that separately fetched the full record can supply one, and making the parameter
  required means a caller can't silently omit it and ship a file missing the rule's own evidence.

### 3. Round-trip verification

`GpxCodecTest` gained four cases: the full record (every field, both verdicts) round-trips exactly;
an empty full record produces no `<extensions>` block at all and decodes back to empty; a waypoint's
`trackId`/`designation` round-trip through the `forager:waypoint` extension; an ordinary waypoint
with neither produces no extensions block. `RoomTrackRepositoryTest` gained a case exercising
`getFullRecord` against a real Room database (not a fake) with a mix of kept/excluded points, and a
case for the no-such-track / no-stored-points path.

**The dispatch's own acceptance case — the reference track `3aec7001-…` (135 kept of 195 raw, 733.0
m, 22.4 min) — was not built.** See "What could not be verified" below for why, stated rather than
worked around with fabricated per-point data.

### 4. Wiring the real production path

The dispatch's build list stops at the repository method, the codec, and the round-trip test, but a
share-button tap that still calls the old, filtered-only path would not actually carry the full
record anywhere real, so this report treats reaching the exporter from the real UI as part of "the
exporter" (item 2) rather than out of scope. `TrackRecordingViewModel.getFullRecord(trackId)` is a
plain passthrough to the repository. `TrackExportPanel`'s `exportAndShareTrack` calls it, logs (via
`android.util.Log.w`, this file's existing precedent — see `SightingsMap.kt`) rather than swallows a
failure, and still shares the filtered file if the full-record fetch fails rather than blocking the
share entirely. `getFullRecord` and (for `TrackExportList`) `waypoints` are threaded down through
`RecordsTab` → `JournalTab`/`LogPanel` → `AvailabilityScreen` → `MainActivity`, mirroring exactly how
`tracks`/`onTracksOpened` are already threaded, with the same default-value convention
(`{ Result.success(emptyList()) }`) at every level so no unrelated existing screen test needed to
change. `MainActivity` wires the one real instance:
`trackRecordingViewModel::getFullRecord`.

---

## Stop-and-ask A, resolved with evidence, not silenced

The dispatch's stop-and-ask A: if the existing test the (missing) report names does not go red once
excluded points ride in the file, something is wrong with the build. `NetworkFixExclusionPerConsumerTest`'s
GPX case (`GPX export writes the survivors only - a future export can no longer show a sub-second
point`) is that test.

**Confirmed, not assumed:** the production change makes `TrackGpxExporter.write`'s `fullRecord`/
`waypoints` parameters required, so the old test's single-argument call
(`TrackGpxExporter(dir).write(track)`) cannot even compile against the new build. Verified directly —
the rewritten test was saved to a local copy (never `git checkout`, per CLAUDE.md's own recorded
pitfall about that command silently discarding uncommitted forward changes), the old test body was
pasted back into the real file, `:app:compileDebugUnitTestKotlin` was run, and the build log showed
exactly two errors, both on the one line this dispatch's change touches (`No value passed for
parameter 'fullRecord'` / `'waypoints'`) — no other errors, so the failure is attributable to this
edit and not a stale or unrelated one. The rewritten version was then restored from the saved copy
and `git diff --stat` confirmed it matches what's committed here.

The re-scoped test (`GPX export writes the survivors to trkseg, and the full raw record with
verdicts to extensions`) keeps the original's core claim — `<trkseg>` is still exactly the three
survivors, the two network fixes' latitude and sub-second timestamps still don't appear in it — and
adds what changed: the same five raw points, all present in `<trk><extensions>` with the correct
`kept` verdict each, `authoritative="true"` present, and (new coverage) an origin waypoint's
`trackId`/`designation` reaching the file. It exercises the real repository read
(`RoomTrackRepository.getFullRecord`) and a real `RoomWaypointRepository`, not a hand-built fixture.

**Stop-and-ask B** (does carrying the verdict require moving or changing the predicate) did not
apply: `isNetworkProviderFix()` was called from a new site, never moved or altered.

---

## What could not be verified

- **The reference track's real round-trip** (dispatch's acceptance case,
  `3aec7001-1fac-454d-b716-fde89abf79f4`, 135 kept of 195 raw, 733.0 m, 22.4 min). This sandbox has
  no device and no access to that device's database — confirmed by the dispatch's own "sandbox has no
  device" note, and there is no local fixture or exported file for that track id anywhere in this
  repository. The 135/195/733.0 m/22.4 min figures exist only as aggregate counts in prior audit docs
  (`2026-09-07-track-distance-display-pulse.md`, `2026-09-07-track-distance-label-completion-report.md`)
  — not the 195 individual points, which the dispatch's own instruction ("do not derive the expected
  values from the exporter") requires for a real assertion. Fabricating 195 plausible points to hit
  those totals would be exactly the "fabricated plausible value" CLAUDE.md rules out. What was built
  instead: general round-trip correctness (any full record, of any size and verdict mix, survives
  `encode`/`decode` and the real repository exactly) — real coverage of the mechanism, but not a
  substitute for checking this specific dispatch-named case against its own real data. Flagged rather
  than silently dropped.
- **Device-side confirmation that the extension actually helps diagnose a non-second-aligned GNSS
  clock on other hardware** — this dispatch built the instrument; whether it finds anything is
  necessarily a beta-tester question, not something buildable or checkable from here.

---

## Also updated, as the dispatch asked

- `NetworkProviderFix.kt`'s doc comment now points at the GPX full-record export as the instrument
  for checking the rule against hardware other than the owner's, and names the trip-report question
  as no longer the only one.
- `docs/beta/README.md`'s paragraph on why the device report asks for make/model now says the track
  file itself (once a tester sends one) settles what the rule did on that device directly, rather
  than only through the tester's prose description of the symptom. `docs/beta/trip-report.md`'s
  tester-facing template text was not touched — the dispatch named only the README sentence, and nothing
  about what a tester is asked to do or send changed.

---

## Out of scope, confirmed untouched

- What the app displays — `getById`/`getAll`/`getForDay`/`toDomain` unchanged; ruling 1 holds.
- The predicate itself (`isNetworkProviderFix`) — unchanged.
- GPX import — `GpxCodec.decode` gained the ability to read back what `encode` now writes (needed for
  the round-trip test), but nothing new calls `decode` in production; its own doc comment already
  recorded that it has no caller yet, and that remains true.
- An all-tracks/whole-journal export path, publishing or pooling tester data, and silencing, skipping,
  or modifying any test — none of these were done.

---

## Testing

Full suite, before (unmodified `main`, via `git stash`) and after (this branch), both via
`:app:testDebugUnitTest`:

| | Tests | Failures | Skipped |
|---|---|---|---|
| Before | 1277 | 0 | 24 |
| After | 1284 | 0 | 24 |

The skip set is unchanged (this dispatch touched none of the four files carrying the 23 `@Ignore`s or
the one `Assume`-gated asset generator that make up the CI allowlist's 24 entries), so its identity
against the allowlist holds by construction. One flake seen mid-run
(`AndroidAlertAudibilityTest`, a Robolectric artifact-fetch `IOException` through the sandbox's
network proxy, in a class this dispatch never touched) reproduced as a pass in isolation on retry and
is not reported as a regression, per CLAUDE.md ("a test unrelated to the dispatched task ... gets
reported, not touched" — reported here, nothing about the test itself was changed).

**Compilation**: `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` both succeed clean.

**Not run**: any instrumented/on-device test, and the reference-track round-trip named above — no
device in this sandbox.

---

## Filing

Pushed to `claude/new-session-bogh3s` (branched from and current with merged `main`, schema 15 —
verified at the start of this dispatch). PR left open per the dispatch's own instruction; not merged.
