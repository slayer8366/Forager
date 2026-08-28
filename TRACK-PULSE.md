# TRACK-PULSE.md

Read-only pulse on the track subsystem, ahead of the Oregon Mycological
Society field test. Every number and code path below was re-derived from
the tree on branch `claude/new-session-vue2za` (HEAD `af7490e`), not taken
from any design doc — per the pulse's own standing rule, several project
docs describe superseded code. `docs/plans/forager-navigator-plan.md` does
not state concrete sampling numbers, so there was nothing there to
contradict; the numbers below exist only in the enum they're defined in.

---

## 1. Why return-to-vehicle doesn't start — verdict: not a crash, not
   missing implementation. It's a **legibility bug**: the toggle and the
   off-track math both work and are unit-tested; the UI never shows the
   user anything they'd recognize as "this is now running."

**The full path, traced:**

- The row: `MapBarIconButton` inside `MapIconBar`, wired with
  `onClick = onToggleReturning`, `enabled = isRecording`
  (`app/src/main/java/com/forager/app/ui/availability/AvailabilityScreen.kt:3948-3959`).
- `enabled = false` disables the `clickable` modifier outright
  (`AvailabilityScreen.kt:4008`, `.clickable(enabled = enabled, onClick = onClick)`)
  and drops the row's alpha to 0.4 (`AvailabilityScreen.kt:4007`) — this is
  a real, working Compose disabled state, not a fake dim. **A disabled
  `clickable` never invokes `onClick`, so "the row exists, tapping does
  nothing" is the literal, correct behavior whenever `isRecording` is
  false.**
- `onToggleReturning` is wired at `MainActivity.kt:310-312` to
  `trackRecordingViewModel.startReturn()` / `.stopReturn()`, and
  `startReturn()` itself independently no-ops when nothing is recording
  (`TrackRecordingViewModel.kt:166-170`, `if (!uiState.value.isRecording) return`).
  Two independent gates, same condition, both correct — recording really
  must already be active.
- **The disabled reason is invisible to a sighted user.** The only text
  explaining why the row is greyed — `"Return to vehicle — start recording
  first"` — is a `contentDescription`
  (`AvailabilityScreen.kt:3950-3951`, `returnToStartStripText(...).ifBlank { "Return to vehicle — start recording first" }`),
  i.e. TalkBack-only screen-reader text. A sighted tester sees one dim icon
  among eight stacked rows with no visible label and no toast — indistinguishable
  from "broken." This exactly matches the owner's report of the row
  "appearing greyed."

**Once recording *is* active, the row is enabled and tapping it works —
but the only visible feedback is a single icon's tint changing** (primary
while returning, error while off-track — `AvailabilityScreen.kt:3954-3958`).
The computed bearing/distance/elevation
(`ComputeReturnToStartUseCase.kt`, feeding `ReturnToStartInfo`) is rendered
**nowhere as visible text on screen.** Confirmed two ways:
- `grep` for every use of `returnToStart`/`isReturning`/`isOffTrack` in
  `AvailabilityScreen.kt` turns up exactly two call sites: the
  `contentDescription` build at line 3950 and the icon-tint `when` at
  3954-3958. No `Text(...)` node anywhere reads `returnToStartStripText`.
- The test suite proves this by construction, not omission:
  `AvailabilityScreenMapIconStackTest.kt:423-432` asserts the readout via
  `onNodeWithContentDescription("Return: 180° S · 1.2 km · -45 m")` —
  never `onNodeWithText`. The test author correctly captured that this
  value only exists as accessibility semantics on an icon, not as a
  visible label.

**So, concretely: what a tester on hardware experiences is "I tapped the
compass-looking icon and nothing appeared on my screen," which is true —
the feature computes correct bearing/distance/elevation but attaches it
only to an 8-icon stack's screen-reader text and a subtle single-icon
tint change.** This is not a null vehicle location, not a permission gate,
not a crash — the underlying math and state machine are correct and
covered by 8 passing `TrackRecordingViewModelTest` cases
(`app/src/test/java/com/forager/app/ui/track/TrackRecordingViewModelTest.kt:260-369`).
It is a missing rendering step: no visible strip/banner/card was ever
built to show `ReturnToStartInfo` to a sighted user. **Estimate: small,
targeted UI work** — the data (`TrackRecordingUiState.returnToStart`,
`.isReturning`, `.isOffTrack`) is already flowing correctly into
`AvailabilityScreen`'s composition; what's missing is a visible composable
consuming it, comparable in scope to the existing `CompassElevationStrip`.

**Where the vehicle/start location comes from:** there is no distinct
"vehicle" concept anywhere in the codebase (`grep -ri vehicle` across
`app/src/main` returns nothing outside the return-to-vehicle UI strings
themselves). "Return to vehicle" is really "return to the track's first
recorded breadcrumb point":
`TrackRecordingViewModel.returnToStart(current)` reads
`uiState.value.breadcrumbPoints.firstOrNull()`
(`TrackRecordingViewModel.kt:255`). It is never captured or persisted as
its own field — it's simply whichever point happened to be the earliest
one flushed to Room for the active track. Because breadcrumb points are
polled from the database every 15s
(`TrackRecordingViewModel.kt:272`, `POLL_INTERVAL_MILLIS`) and the
recording service itself only flushes points every 20 accepted points or
30 seconds, whichever comes first
(`TrackRecordingService.kt:252-253`, `FLUSH_BATCH_SIZE`/`FLUSH_INTERVAL_MILLIS`),
**a user who starts recording and taps return-to-vehicle within roughly
the first 30-45 seconds will find `breadcrumbPoints` still empty**, so
`returnToStart()` returns `null` and nothing updates
(`TrackRecordingViewModel.kt:254-264`) — a second, narrower way to
observe "nothing happens," on top of the legibility gap above.

---

## 2. Sampling configuration

**Location request parameters** — plain `android.location.LocationManager`,
not Play Services/Fused (`AndroidLocationTracker.kt:20-28`, a deliberate
choice per its own doc comment, to avoid a first Play Services
dependency):
- Registers on **both** `GPS_PROVIDER` and `NETWORK_PROVIDER`
  simultaneously, whichever are enabled (`AndroidLocationTracker.kt:53-57`).
- Requested update interval: **1000 ms**, minimum distance **0 m**
  (`AndroidLocationTracker.kt:56, 80`, `MIN_UPDATE_INTERVAL_MILLIS = 1_000L`)
  — this is only a ceiling on the raw, unfiltered callback rate; there is
  no "priority"/accuracy parameter because `LocationManager` (unlike
  Fused) doesn't have one.

**The real sampling decision is downstream, in `LocationSampler`**
(`LocationSampler.kt:24-37`), gated by `TrackRecordingMode`
(`domain/model/TrackRecordingMode.kt:23-31`):

| Mode | min interval | min distance | max acceptable accuracy |
|---|---|---|---|
| `HIGH_ACCURACY` | 5,000 ms | 5 m | 30 m |
| `BALANCED` (default) | 15,000 ms | 15 m | 50 m |
| `BATTERY_SAVER` | 60,000 ms | 30 m | 100 m |

A candidate fix is only accepted once **both** the interval and distance
thresholds have been cleared since the last *accepted* point, and is
rejected outright if its reported accuracy is worse than the mode's
ceiling, regardless of timing (`LocationSampler.kt:24-37`). **The app
always records in `BALANCED` mode** — `TrackRecordingViewModel.startRecording()`
defaults to it (`TrackRecordingViewModel.kt:108`), and `MainActivity`'s
only call site never passes a mode (`MainActivity.kt:298`,
`trackRecordingViewModel.startRecording()`). **There is no UI anywhere to
choose `HIGH_ACCURACY` or `BATTERY_SAVER`** — `grep` for
`TrackRecordingMode` under `ui/` finds only the one default-parameter
declaration; the enum exists but is unreachable from any screen.

At a typical walking pace this 15s/15m gate can itself explain "accurate
where it lands, but sparse between points": a slow or meandering forager
covering less than 15m in 15s simply produces no new point until both
thresholds clear, and any fix reporting worse than 50m accuracy (easy
under tree canopy) is silently dropped rather than degrading the point
that follows. No additional smoothing/deduplication exists beyond this
one gate (`LocationSampler.shouldAccept` is the only filter between the
raw fix and the persisted `TrackPoint`).

**No adaptive sampling exists.** There is no stationary/moving distinction
anywhere in `LocationSampler` or `TrackRecordingService` — the same fixed
thresholds apply whether the forager is walking or standing still at a
patch for ten minutes (each ~15m step the person doesn't take just delays
the next accepted point; it doesn't stop sampling attempts from being
made against the flow of raw 1-second fixes).

**Are these numbers deliberate or inherited defaults?** Deliberate and
documented: the enum's own doc comment
(`domain/model/TrackRecordingMode.kt:17-21`) explicitly labels them
"adjustable assumptions, not measured facts... chosen for a plausible
multi-hour foraging walk and not yet checked against a real recorded
track's battery draw." They are named constants with a recorded rationale,
not silent magic numbers — but the doc comment's own caveat is exactly
what this field test would be validating, and the mode the field testers
will actually get (`BALANCED`) was never a deliberate choice made *for
them* — it's just the parameterless default nobody has overridden.

**Foreground service / Doze:** recording does run as a real foreground
service with a persistent, ongoing notification
(`TrackRecordingService.kt:216-223`, `startForeground(...,
FOREGROUND_SERVICE_TYPE_LOCATION)` on API 29+), which is the platform's
own exemption path for continuous background location work — a legitimate
FGS is not expected to be Doze-throttled the way a plain background app
would be. I found no evidence of any additional wake lock or battery-
optimization-exemption request (`grep` for `WakeLock`/`PowerManager` under
`app/src/main` returns nothing) — none should be needed for a
`LocationManager` FGS, but I can't verify actual GPS-chip duty-cycling
behavior under Doze on real hardware from source alone; **flagging as
unverified** rather than asserting the sparse track is or isn't a Doze
symptom. Given the 15s/15m gate above fully explains the reported
sparseness on its own, I'd treat "it's a Doze symptom" as the less likely
of the two, not the default explanation.

---

## 3. What survives process death

**Points are written incrementally, but batched, not per-fix.**
`TrackRecordingService` buffers accepted points in memory
(`pendingPoints`, `TrackRecordingService.kt:61`) and flushes to Room via
`RecordTrackPointsUseCase` either every 20 accepted points or every 30
seconds, whichever comes first (`TrackRecordingService.kt:126-143,
252-253`). **Worst case, a kill loses up to 30 seconds (or up to 19
accepted points) of the most recent recording** — for `BALANCED` mode's
15s interval, that's roughly one to two points, not a large loss, but not
zero either. The `Track` row itself is created and persisted before any
point exists (`StartTrackUseCase.kt:20-29`), so an interrupted recording
never loses the track's identity/start time, only its tail.

**Recording does not resume after process death or force-close.** Three
independent gaps, each confirmed in the tree:
1. The service is `START_STICKY`
  (`TrackRecordingService.kt:96`), so Android may recreate the killed
  service — **but with a `null` Intent**, and `onStartCommand`'s `when
  (intent?.action)` has no branch for that case
  (`TrackRecordingService.kt:75-97`): nothing restarts location collection.
2. Even if it tried, the service has nothing to resume with —
  `currentTrackId` is an in-memory `@Volatile` field
  (`TrackRecordingService.kt:66`) that resets to `null` on a fresh process,
  and there is no persisted "currently active track id" anywhere (`grep`
  for `currentTrackId`/`activeTrackId` across `app/src/main` finds only
  this one in-memory field).
3. The UI side is symmetric: `TrackRecordingUiState.activeTrack` is plain
  in-memory `ViewModel` state
  (`TrackRecordingUiState.kt:20-21`), explicitly documented as not surviving
  process death (`TrackRecordingViewModel.kt:44-52`, the class's own "What
  this does not handle" section) — "deliberately not built here to keep
  this pass scoped to starting, stopping, and showing a recording that's
  actually running."

Net effect: a track killed mid-recording (OS memory pressure, force-close,
crash) is left in Room with `endedAtEpochMillis == null` and up to ~30s of
unflushed tail data gone, and nothing in the app — service or UI — notices
or offers to resume or close it out on next launch. This is a real,
named gap (the ViewModel's own doc comment calls it out), not something I
inferred. **This project already treats exactly this kind of survival as
a hard requirement for downloads** (per this pulse's own framing) —
tracks do not get the same treatment today.

---

## 4. `DetectOffTrackUseCase`

**What it computes:** a pure distance-trend heuristic, not real route
deviation (`DetectOffTrackUseCase.kt:1-29`). While `isReturning` is true,
every live fix's distance back to the start
(`ComputeReturnToStartUseCase`'s `distanceMeters`) is appended to a
rolling history (`TrackRecordingViewModel.recentReturnDistancesMeters`,
`TrackRecordingViewModel.kt:96, 254-264`). Off-track fires when, over the
**last 3 readings** (`WINDOW_SIZE = 3`, `DetectOffTrackUseCase.kt:23`),
the most recent distance exceeds the oldest of those three by more than
**25.0 meters** (`NET_INCREASE_THRESHOLD_METERS = 25.0`,
`DetectOffTrackUseCase.kt:27`) — i.e. a net *increasing* trend in distance
back to start, not a comparison against any planned route or trail
geometry. The class's own doc comment is explicit that this can't and
doesn't distinguish "terrain forcing a detour" from "actually drifting
off course."

**Reference/threshold, confirmed real:** the only "reference" is the
walker's own first breadcrumb point (see §1); there is no independent
trail/path reference anywhere in the domain layer.

**Confirmed sole consumer:** `isOffTrack` flows
`TrackRecordingViewModel` → `TrackRecordingUiState.isOffTrack`
(`TrackRecordingUiState.kt:38`) → `AvailabilityScreen`'s `isOffTrack`
parameter (`AvailabilityScreen.kt:466`) → `MapIconBar`'s `activeColor`
`when` (`AvailabilityScreen.kt:3954-3958`), which only ever changes the
return-to-vehicle icon's **tint color**. `grep` for `isOffTrack` across
all of `app/src/main` (7 occurrences total, all traced above) confirms
there is no second collector — no notification, no vibration, no sound,
no banner.

**What exists in the app already that off-track *could* reach a pocketed
phone through, but doesn't today:**
- `TrackRecordingService` already runs as a foreground service with an
  active `NotificationCompat.Builder`-based notification channel
  (`TrackRecordingService.kt:178-202`) — updating that existing
  notification's text, or posting a second high-priority one, needs no
  new permission (`POST_NOTIFICATIONS` is already declared and requested,
  `AndroidManifest.xml`, `MainActivity.kt:295-297`).
- No vibration API usage exists anywhere in `app/src/main` (`grep -r
  "Vibrator\|VibrationEffect"` returns nothing) — this would be new
  plumbing, not a hookup to something already there.
- The off-track *signal* itself (`isOffTrack: Boolean`) is already
  computed on the right side of the process (inside
  `TrackRecordingViewModel`, which is UI-layer, not the service) — routing
  it to a notification would need either moving the off-track check into
  `TrackRecordingService` (which already collects live fixes independently,
  see §1's noted duplication) or having the service observe the same
  state, neither of which exists today.

---

## 5. What the UI currently implies vs. what it does

| Control | What a reasonable user assumes | What it actually does |
|---|---|---|
| Record button (`FiberManualRecord`/`Stop`, `AvailabilityScreen.kt:3940-3947`) | Starts/stops a GPS track, visible progress | Works as implied: creates a `Track` row, starts the foreground service, breadcrumbs draw on the map (`SightingsMap.kt:587`, confirmed rendered via `GeoJsonSource`) |
| Return-to-vehicle icon (`Directions`, `AvailabilityScreen.kt:3948-3959`) | Gives a bearing/distance back to where you started, updates live | Computes real, live, correct bearing/distance/elevation (§1) but shows it **only as invisible accessibility text** and a single icon tint — no visible readout to a sighted user |
| Off-track color change (`AvailabilityScreen.kt:3954-3958`) | Some kind of alert when drifting off course | Fires only a color swap on one icon in an 8-icon stack — no sound, no notification, no vibration, easy to miss while the phone is pocketed (matches the owner's report verbatim) |
| Breadcrumb trail on the map | Shows the path walked so far | Works as implied, but is up to 45 seconds stale (15s UI poll + up to 30s service flush) at any given moment |
| Waypoint drop/marker | Drops a pin, visible on the map | Works as implied (`CreateWaypointUseCase.kt`, rendered via `waypointsFeatureCollection`, `SightingsMap.kt:801-802`) |
| Nothing in the UI exposes | — | Track history list, per-track statistics, or track deletion — `GetTracksUseCase`, `ComputeTrackStatisticsUseCase`, and `DeleteTrackUseCase` all exist and are wired into `AppContainer` (`AppContainer.kt:170-171`) but are **never called from any `ui/` file** (`grep` across `app/src/main/java/com/forager/app/ui` finds zero references to any of the three). A recorded track, once ended, is permanently invisible in the app — no way to view, review, or delete it. |
| Nothing in the UI exposes | — | Choice of recording mode — `HIGH_ACCURACY`/`BATTERY_SAVER` exist but are unreachable (§2); the app always silently runs `BALANCED` |

**Before testers go out, items needing a decision (disclosure or removal)
specifically:** the return-to-vehicle readout (currently invisible to a
sighted user — testers will believe it's broken, matching exactly what
the owner already reported) and the off-track signal (currently a
same-icon color change only, easy to miss in a pocket) are the two most
likely to be relied on as "safety controls" and are also the two weakest
on legible output today.

---

## 6. Export / comparison against Gaia

**There is no way to get a recorded track out of this app today** — no
GPX file write, no share sheet, no Storage Access Framework picker. This
is not an inference; it's stated directly in the codec's own doc comment
(`GpxCodec.kt:19-21`): "Actually writing the encoded string to a file the
user picks, or reading one back, is Storage Access Framework work needing
a document-picker UI — Phase 1c... This class is the codec only." Confirmed
by `grep`: `GpxCodec` is referenced only from its own model
(`domain/model/GpxDocument.kt`) and its own unit test
(`app/src/test/java/com/forager/app/domain/GpxCodecTest.kt`) — zero
references anywhere under `app/src/main/java/com/forager/app/ui`, `service/`,
or `MainActivity.kt`.

**What exists and works, one layer down from a UI:** `GpxCodec.encode()`
correctly serializes a `Track` and its `Waypoint`s to GPX 1.1 XML
(`GpxCodec.kt:31-44`) and `GpxCodec.decode()` can round-trip it back
(`GpxCodec.kt:62-86`), both covered by `GpxCodecTest`. The gap is purely
the UI/file layer: there is no button, menu item, or share intent
anywhere that calls `encode()` and writes or shares the result.

**Practical implication for the field test:** as scoped today, there is
no way to turn the field test into accuracy data against Gaia's own
recorded track — only visual/anecdotal comparison on two separate
screens. Wiring a minimal "share as GPX" action (encode the ended
`Track`, hand the string to `Intent.ACTION_SEND` with a `text/xml` mime
type — no SAF file picker required for a share-only path) would be
substantially smaller than the "Phase 1c" SAF-picker scope the doc
comment describes, if that's an acceptable bar for this pass; that's a
product/scope call, not one I'm making here.

---

## Summary for the decision this feeds

- **Item 1 (return-to-vehicle):** not a crash, not unimplemented — the
  state machine and math are correct and unit-tested. It's a **UI
  omission**: the computed return info is wired to `contentDescription`
  only, never to a visible `Text`/banner, and the disabled-state reason is
  likewise TalkBack-only. Estimate: small, targeted UI addition (one new
  visible composable consuming state that already flows correctly).
- **Item 2 (sparse track):** not a Doze symptom by anything found in the
  tree (foreground service already exempts it, no evidence checked either
  way on real hardware) and not a dropped-after-arrival filtering bug —
  it's the `BALANCED` mode's 15s/15m/50m gate, which is a deliberate,
  documented, but self-admittedly unvalidated choice, applied uniformly
  with no adaptive stationary/moving behavior and no user-facing way to
  choose a denser mode.
- **Item 3 (process death):** confirmed real gap, already named in the
  code's own doc comments — up to ~30s of tail data lost per kill, and no
  resume path on any restart, unlike this project's existing standard for
  downloads.
- **Items 4-6:** off-track detection is a real, tested, but narrow
  heuristic whose only output is an icon tint; the app already has an FGS
  notification channel and `POST_NOTIFICATIONS` permission that a stronger
  off-track signal could reuse without new permissions; and there is
  currently no export path out of the app at all, though the codec that
  would back one already exists and is tested.
