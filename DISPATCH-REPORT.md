# DISPATCH-REPORT.md

Completion report for the four-item tester-build dispatch (`Dispatch — tester build for the OMS
field test`). Branch `claude/new-session-vue2za`. All four items implemented; nothing pushed —
this report and the diff live only in this session's local working tree until told otherwise.

Environment note: this container ships no Android SDK. I downloaded the command-line tools and
platform 37.0/build-tools 37.0.0 into `/tmp/android-sdk` (outside the repo, not committed) so every
claim below is backed by a real `./gradlew compileDebugKotlin`/`testDebugUnitTest` run, not
untested code. Full suite: **762 tests, 761 passing.** The one failure
(`JournalTabTest — "From Album on the edit form opens the picker..."`) reproduces only under a
full-suite run, passes standalone, and matches the exact failure class
`TrackRecordingServiceTest`'s own doc comment already documents in this repo (a leftover async job
from an earlier test bleeding into the next test's Robolectric sandbox) — a pre-existing flake, not
a regression from this work. `MainActivity`-level wiring (item 3's mode override, item 4's
`LaunchedEffect`) has no automated test coverage, consistent with this codebase's existing
precedent: no `MainActivityTest` exists for anything else in `MainActivity.kt` either.

---

## Item 1 — GPX export wired to a share button

**Where it lives:** Settings → "Recorded Tracks" (new entry row, both the compact bottom-nav
Settings tab and the medium/expanded drawer's Settings panel — `SettingsContent` is the one shared
composable both host). No new screen: it mirrors the Settings "Crash Logs" row/panel
(`com.forager.app.ui.crash.CrashLogPanel`) exactly — list, tap to share — since that was the only
existing "list this app's own records, hand one to another app" pattern anywhere in the tree.

**New files:**
- `app/src/main/java/com/forager/app/export/TrackGpxExporter.kt` — writes a `Track` to a GPX file
  under `context.cacheDir/tracks/`, filename `forager-track-<yyyy-MM-dd-HHmmss>.gpx` derived from
  **the track's own `startedAtEpochMillis`**, not the moment of export — two tracks from the same
  trip get two distinct, stable names regardless of when either is shared, and re-exporting the
  same track overwrites rather than accumulating duplicates.
- `app/src/main/java/com/forager/app/ui/track/TrackExportPanel.kt` — the list/share UI, plus
  `shareGpxIntent(context, file)` split out as its own testable function (mirrors
  `com.forager.app.ui.availability.directionsIntent`/`launchDirections`'s existing split).

**Wiring:** `TrackRecordingViewModel` gained a `GetTracksUseCase` dependency (already existed in
`AppContainer`, called from nowhere before this) and a `loadTracks()` method, called on init and
again whenever the panel opens. `AvailabilityScreen` gained `tracks`/`onTracksOpened` params
threaded through to both Settings hosts.

**GpxCodec itself was not touched** — it was already implemented and tested; this only wires it up.

**Tests** (all passing):
- `TrackGpxExporterTest` (plain JVM, no Robolectric) — file creation, the start-time-derived
  filename, overwrite-not-duplicate behavior, and byte-for-byte content matching `GpxCodec.encode`.
- `AvailabilityScreenSettingsPanelTest` — five new tests reaching the row through
  `AvailabilityScreen`'s real entry point: the entry row exists, the empty state, a track's visible
  timestamp/point-count text plus a `testTag`-reachable share button (not `contentDescription`
  alone — see item 2 for why that distinction matters), a real `ACTION_SEND` chooser firing with
  the correct mime type on tap, and that nothing fires before the tap.

**Two real test bugs found and fixed while writing these** (worth recording, since both would have
passed silently otherwise): a `TrackGpxExporterTest` case read the "before" file's content *after*
the second write had already overwritten it (same path, same `File` handle), so the two sides of
the comparison were trivially identical either way; and the Compose share-button test raced the
`Dispatchers.IO` file write against `waitForIdle()`, which only synchronizes Compose's own clock,
not a real background thread — fixed with `waitUntil`.

---

## Item 2 — Return-to-vehicle made visible in the compass strip

**Placement, per the owner's spec:** a new `ReturnToVehicleStripControl` docks at the compass
strip's far right (`CompassElevationStripContent` in `AvailabilityScreen.kt`), inside its own
`weight(1f)` sibling row so the heading/elevation/coordinates group keeps centering within
whatever width is left — the coordinates segment's existing ellipsis absorbs the width this control
now takes, the same way it already absorbs a narrow phone.

**What it shows:** **distance only** (e.g. "412 m" / "1.2 km"), not the full bearing/distance/
elevation sentence. The strip already has a heading arrow immediately to the left — "which way" is
covered — and there's no room in a strip this narrow for a second full sentence. Full
bearing/elevation stay reachable through `MapIconBar`'s own row and its `contentDescription`.

**Constraints met exactly as specified:**
- Transparent, themed icon — `tint` alone carries state (error/primary/default), no fill, no badge.
- The text lives in a fixed-width (`RETURN_TO_VEHICLE_TEXT_WIDTH = 48.dp`), vertically-centered
  `Box`, reserved whether or not `returnToStart` has a value — a fix landing or being lost never
  reflows the icon sideways.
- Strip height extended to `COMPASS_STRIP_MIN_HEIGHT = MIN_TOUCH_TARGET` (48dp) — a real touch
  target, not the old text-driven wrap height — with the reason recorded next to the constant:
  *"this strip is exactly `MIN_TOUCH_TARGET` tall now... if that control moves elsewhere again,
  this strip should return to wrapping its text content's natural height instead of staying pinned
  to a touch-target minimum it no longer needs."*
- A real `IconButton` (bounded ripple + tint state change on tap), not a static icon.

**Overflow decision (asked to make deliberately):** the centered heading/elevation/coordinates
group now shares the strip with the new control via its own `weight(1f)` — on a narrow phone, the
coordinates segment's pre-existing ellipsis is what gives way, exactly as it already did before
this change for a plain narrow screen.

**Both controls kept, deliberately, not one replacing the other:** `MapIconBar`'s existing
return-to-vehicle row still exists, wired to the identical state. The field test itself — via the
owner's own tester-interview question, "where did you look first when you wanted to get back to
the car?" — is what should decide which placement survives, not a guess made now.

**A real bug found and fixed along the way, not just a UI addition:** the new control's own
interaction test initially failed — tapping it never called `onToggleReturning`. Investigation
(bounds comparison, semantics dumps, an exception probe) confirmed `MapIconBar`'s `Surface`
(vertically centered on the same right-edge column, spanning all 8 of its rows as one continuous
touch-intercepting background) reached up far enough on a short viewport (`w360dp-h640dp`, this
suite's own test config) to sit on top of the new control and silently swallow the tap — the exact
touch-interception class CLAUDE.md already documents twice for this screen. Fixed by composing
`MapIconBar` *before* `CompassElevationStrip` in the shared `Box`, so composition order (which is
also paint/hit-test order for overlapping Box siblings) guarantees the strip's own control wins any
overlap on every screen size, not just typical ones — recorded at both call sites.

**Sweep for the same false-confidence pattern, as asked:** the four existing
`AvailabilityScreenMapIconStackTest` cases that asserted the return-to-vehicle sentence via
`onNodeWithContentDescription` were the exact bug this dispatch names — they passed throughout the
period the feature was invisible to a sighted user. Rewritten to scope the `contentDescription`
check to `MapIconBar`'s own `testTag` (still a valid accessibility assertion) *and* add a parallel
`onNodeWithText` assertion against the new visible strip readout. I also grepped this test suite
for every other `onNodeWithContentDescription`/`onAllNodesWithContentDescription` use (in
`AvailabilityScreenMapIconStackTest.kt` and `AvailabilityScreenSettingsPanelTest.kt`): the
remainder are all asserting on plain icon-only buttons that have no visible text to show in the
first place (Fullscreen, Search, map-mode, Back-arrows) — legitimate accessibility-name assertions,
not stand-ins for a hidden visible element. None of that shape survived unnoticed.

**Tests added/changed:** 4 rewritten, 4 new (48dp touch-target size, the interaction bug's own
regression test, an off-track-tint state-reaches-both-controls check, distance-in-meters on both
controls).

---

## Item 3 — Denser sampling for this build

**Value chosen:** `TrackRecordingMode.HIGH_ACCURACY` (5s / 5m / 30m-accuracy-ceiling) — already
implemented, already tested, already named. `MainActivity`'s one production call site
(`trackRecordingViewModel.startRecording()`, previously no mode arg → implicit `BALANCED`) now
explicitly passes `TrackRecordingMode.HIGH_ACCURACY`, with the reason recorded at the call site.
The **ViewModel's own default parameter stays `BALANCED`** deliberately — this is a tester-build
override at the one place that starts a real recording, not a changed library default, so a future
non-tester caller (or any existing test) still gets `BALANCED` unless it asks otherwise.

**Reasoning:** at typical walking pace (~1.2–1.4 m/s), `HIGH_ACCURACY`'s 5m distance gate is met in
~4s, so its 5s interval gate becomes the binding constraint — roughly one point every 5–7s, versus
`BALANCED`'s ~15–20s. That's a real, meaningful density increase for retracing a route against
Gaia, using numbers this project has already validated (unit-tested `LocationSampler`/
`TrackRecordingModeTest` coverage), not new invented thresholds.

**Adaptive behavior — considered, not built, and here is why:** the dispatch's own framing
("stationary points, that's where the battery goes") attributes the cost to *generating* redundant
points. In this architecture that isn't quite where the cost actually is:
`AndroidLocationTracker.fixes` (`app/src/main/java/com/forager/app/location/AndroidLocationTracker.kt:80`)
requests raw fixes from the OS at a **fixed 1-second interval regardless of `TrackRecordingMode`** —
`LocationSampler` only decides which of those fixes get *written*, so a write-side stationary
detector would reduce Room writes (already cheap) without touching the actual GPS-radio draw. A
genuine "denser while moving, cheaper while stationary" behavior would need to make the OS request
interval itself reconfigurable — restructuring `LocationTracker`/`AndroidLocationTracker`, which
today has no adjustable-interval API. That's a real architecture change, not a cheap one, so per
the dispatch's own "if it's cheap" qualifier I did not build it — building a stationary-detector
that doesn't move the actual battery needle would be exactly the speculative,
doesn't-solve-the-stated-problem work this project's own engineering principles warn against.
Flagging as a real follow-up recommendation, not silently dropped.

**Was the sparse track fully explained by these values, or is Doze/duty-cycling also implicated?**
Not re-litigated from scratch — the pulse's own finding stands: `BALANCED`'s 15s/15m gate alone
fully explains the reported sparseness at ordinary walking pace, and the foreground service already
exempts the app from most Doze restrictions while recording. Real GPS-chip duty-cycling behavior
under Doze remains unverified on physical hardware (this remains true after this pass — nothing in
this environment can test that), so it isn't ruled out as a *contributing* factor, only that it
isn't *needed* to explain what was already observed.

**Not verified by an automated test:** `MainActivity`'s call-site override, the same boundary as
every other Activity-level side effect in this codebase (no `MainActivityTest` exists for anything
else in this file either).

---

## Item 4 — Off-track reaches a pocketed phone

**Debounce chosen:** re-alert every **120 seconds** (`OFF_TRACK_ALERT_COOLDOWN_MILLIS`) for as long
as the heuristic keeps reading `true` — not edge-triggered (alert once, then silent for the rest of
a sustained drift). A real, sustained drift should keep reminding a walker periodically, not go
quiet after the first buzz; 120s is long enough that a forager who checks their pocket after one
buzz has time to look and self-correct before a second fires, short enough that a genuine sustained
drift is still a real, periodic reminder. Labelled an adjustable assumption in the same spirit as
`DetectOffTrackUseCase`'s own threshold — no field data yet on what cadence a real forager wants.
The cooldown clock resets on `stopReturn()`/`stopRecording()`, so a later, separate return attempt
is never blocked by a stale cooldown from an earlier one.

**Where the debounce lives:** `TrackRecordingViewModel.returnToStart()` — the same method that
already recomputes `isOffTrack` on every live fix while returning. A new
`TrackRecordingUiState.offTrackAlertId: Int` increments each time an alert should fire (a one-shot
event counter, the same shape `startRecordingErrorMessage`'s `LaunchedEffect` consumer already uses
for its own Toast, extended to handle a *repeating* condition a nulled-out single field can't
represent). **`DetectOffTrackUseCase` itself is untouched** — same 3-reading/25m-net-increase
heuristic, per the explicit instruction to keep it as-is so the field test is what determines
whether that threshold is right.

**What actually fires, and where:** `MainActivity` observes `trackUiState.offTrackAlertId` via a
`LaunchedEffect` and, on each real increment, both:
1. **Posts a notification** on a **new, dedicated `"off_track_alert"` channel** (`IMPORTANCE_HIGH`,
   vibration enabled at the channel level) — not `TrackRecordingService`'s existing
   `"track_recording"` channel, which is `IMPORTANCE_LOW` on purpose (a silent, ongoing
   "recording is running" notice) and wouldn't sound or vibrate a posted notification regardless of
   what that notification itself requests. Worth noting: `strings.xml` already had
   `off_track_notification_channel_name`/`_title`/`_text` defined, unused anywhere in the tree —
   this wires up exactly the channel and copy they were named for, rather than inventing new text.
2. **Vibrates directly** via `Vibrator`/`VibratorManager` (two short buzzes — `0, 250, 150, 250`ms —
   more likely felt through fabric than one pulse, still brief) — independent of channel/notification
   settings, since that's the mechanism that actually reaches a phone nobody is looking at.
   Required adding `android.permission.VIBRATE` to the manifest (a normal, install-time permission,
   no runtime prompt).

**Icon tint kept, not replaced:** `MapIconBar`'s and the new compass-strip control's tint-on-state
behavior (item 2) is untouched — this adds a channel, it doesn't remove the existing one.

**POST_NOTIFICATIONS handling:** same "declared, not forced" stance `TrackRecordingService`'s own
notification already takes — a denial means no notification posts, not a crash; vibration (a
different, install-time permission) still runs regardless.

**Tests:**
- `TrackRecordingViewModelTest` — 5 new tests: one alert per off-track episode (not per fix), a
  second real alert once the cooldown elapses during a sustained drift, never firing while on
  track, and the cooldown reset on `stopReturn()`.
- `OffTrackAlertTest` (new file, Robolectric) — `postOffTrackAlert`/`vibrateOffTrackAlert`/
  `createOffTrackNotificationChannel` were split out of `MainActivity` as plain `Context`-taking
  top-level functions (the same split `directionsIntent`/`launchDirections` already uses)
  specifically so they're testable without this app's full DI graph: verifies the channel's real
  importance/vibration flag, the posted notification's real channel id/title/text, that a denied
  POST_NOTIFICATIONS permission on API 33+ suppresses posting without crashing, and the vibrator's
  actual triggered pattern.

---

## Not fixed — disclose to testers instead

**No resume after process death; ~30s of tail data lost per kill.** Unchanged from the pulse's own
finding — `TrackRecordingService`'s `START_STICKY` restart carries a null `Intent`, and there is no
persisted active-track id anywhere for a restarted service to resume with. Too large a change for
this timeline, and too dangerous to leave unsaid: **tell testers to glance at the recording
indicator whenever they check the map**, and that losing an entire outing to a silent stop is a
known, real risk on this build.

---

## For the owner — tester questions (restated from the dispatch, unchanged)

- Where did you look first when you wanted to get back to the car?
- Where did you look to start recording? To check it was still going?
- Did you have trouble tapping anything?
- Did you open Seasonal at all? Settings?
- Does anyone forage at dusk, and does anyone wear gloves? (Both assumptions are load-bearing in
  item 2's touch-target sizing and neither has been validated by an actual tester yet.)

---

## Everything touched, for reference

**New:** `export/TrackGpxExporter.kt`, `ui/track/TrackExportPanel.kt`, `export/TrackGpxExporterTest.kt`,
`OffTrackAlertTest.kt`.
**Modified:** `MainActivity.kt`, `ui/availability/AvailabilityScreen.kt`,
`ui/track/TrackRecordingViewModel.kt`, `ui/track/TrackRecordingUiState.kt`, `AndroidManifest.xml`,
`res/xml/file_paths.xml`, `AvailabilityScreenMapIconStackTest.kt`,
`AvailabilityScreenSettingsPanelTest.kt`, `TrackRecordingViewModelTest.kt`.
