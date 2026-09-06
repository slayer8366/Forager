# Pulse: light budget and turnaround alert — what exists, what does not, and what it would cost

**Type:** read-only survey (owner-directed pulse). **Nothing built; no product code, no tests, no dependency, no gradle change.**
**Date:** 2026-09-06. **Base:** `main` at `41ce4e1412a3fae52089c7c1fe0a0cc6e98f818f` — the merge of PR #68 (location accuracy), confirmed to contain the gate (`domain/LiveFixGate.kt`) and the formatter. Branch `claude/new-session-102gri` restarted from it; this report is its only commit.

Every claim names a file and line on `41ce4e1`, or is marked inferred. Where the pulse asks "what exists", "nothing" is given as the answer when it is the answer.

---

## The short version

Of the feature's four inputs, **three have no implementation**:

| Input | Exists? | What is there instead |
|---|---|---|
| Sunset / twilight time | **Partly** — a solar-*altitude* function exists, unused in production, that can be turned into a sunset time with a root-find; no sunset time is computed anywhere | `CivilTwilight.sunAltitudeDegrees` (NOAA low-precision), offline, no dependency |
| Path length back to the origin along the track | **Nothing** | Straight-line only (`ComputeReturnToStartUseCase`); cumulative track length exists **retrospectively** (`ComputeTrackStatisticsUseCase`), never live |
| A usable pace | **Nothing** | `TrackPoint` carries no speed; the only rolling window in the app is three straight-line distances for the off-track heuristic |
| An interrupting alert | **Partly** — a HIGH-importance channel, a notification and a vibration exist for the off-track alert, but they fire from the Activity's composition, so **inferred: not while the app is in the background** | `postOffTrackAlert` / `vibrateOffTrackAlert` in `MainActivity.kt` |

**The largest missing piece is the alert mechanism, not any of the three computations.** Sunset is a few dozen lines on top of code already in the repo; path-length-home is a loop over a list already in memory; pace is a division that needs a policy. Reaching a phone in a pocket, screen off, with the app in the background and possibly no recording running, has no existing path at all, and everything the owner cares about ("before the moment, not at it", "a pocketed phone") depends on it. That makes this at least two dispatches: the computations (which can be tested headless and shown on the HUD while it is open) and the delivery (which is Android lifecycle work that only hardware can verify).

---

## 1. Sunset

### What computes solar times

**`domain/CivilTwilight.kt`** — pure Kotlin, no Android imports, no network, NOAA's low-precision solar position algorithm. It computes the sun's **altitude**, not sunrise/sunset **times**, by design:

> `fun isNight(epochMillis: Long, latitude: Double, longitude: Double): Boolean = sunAltitudeDegrees(epochMillis, latitude, longitude) <= NIGHT_ALTITUDE_DEGREES` (`:56-57`)
> `internal fun sunAltitudeDegrees(epochMillis: Long, latitude: Double, longitude: Double): Double` (`:67`), ending `return 90.0 - acos(cosZenith.coerceIn(-1.0, 1.0)).deg` (`:110`)

Its own doc comment records why altitude rather than times (`:18-30`): one evaluation, no "which day's sunset?" near midnight, **no timezone handling at all** ("the input is an instant and a position"), and no polar special case. `NIGHT_ALTITUDE_DEGREES = -6.0` is civil twilight (`:52`). Accuracy claim (`:32-35`): better than a tenth of a degree, which moves a threshold crossing by seconds.

**It has no production caller.** `grep -rn "CivilTwilight\." app/src/main` finds only its own file; the only reference in the tree is `app/src/test/java/com/forager/app/domain/CivilTwilightTest.kt`. The map's night mode it was written for moved to a Settings checkbox ("Replaces the map's earlier civil-twilight-automatic/long-press-hold control", `MapPreferencesRepository.kt:36`, `AvailabilityScreen.kt:761`). So: a tested, offline solar-position function exists and is dead in production.

**What a sunset *time* would cost on top of it:** a root-find of `sunAltitudeDegrees(t) = threshold` over the coming hours (bisection over a bounded interval; the function is smooth and monotone between solar noon and midnight), plus the polar cases the doc comment warns about — no crossing today, or the sun never rising. That is a small standard algorithm reusing the existing function, **not a dependency**. NOAA's published sunrise/sunset formulas are an alternative direct computation of the same order of size. No library is needed; none is named because none is recommended.

### Cached weather or seasonal data carrying a sunset

**None.** Open-Meteo is called with `daily=precipitation_sum,et0_fao_evapotranspiration` (`data/remote/OpenMeteoApi.kt:42`) and `daily=precipitation_sum` for the archive (`OpenMeteoArchiveApi.kt:34`); `DailyWeather` carries `date`, `isForecast`, `precipitationMm`, `evapotranspirationMm` (`domain/model/WeatherSeries.kt:49-61`). `CivilTwilight`'s doc comment confirms the option was seen and rejected: "Open-Meteo does return those fields and the app already calls it; that option was rejected for this reason [no signal at dusk], not overlooked" (`:12-16`). So there is nothing network-derived that could disagree with a computed value on screen.

### Timezone

**The app knows only the device's zone.** Every zone use is `ZoneId.systemDefault()` or `ZoneOffset.UTC` (`CartographyEntryEditScreen.kt:639`, `PhotoGalleryScreen.kt:222`, `CrashLogPanel.kt:189`, `TrackExportPanel.kt:110`, `LogSectionEditors.kt:264-276`); `LocalDayRange.kt` documents the same ("device-default zone, implicitly", `:14-28`). Nothing maps a position to a zone. **This matters less than the pulse's framing suggests, for one reason:** `CivilTwilight` works in epoch millis and produces an instant. A countdown ("sunset in 1 h 40 min") is a difference of instants and needs no zone at all. Only *displaying a clock time* ("sunset 19:42") needs a zone, and there the device zone is what a user near a boundary is already reading on their lock screen. Reported, not decided.

---

## 2. Distance travelled, and distance back

### Cumulative distance along a track

**Exists, retrospectively only.** `ComputeTrackStatisticsUseCase` (`domain/ComputeTrackStatisticsUseCase.kt:38-105`):

> `for (i in 1 until points.size) { … distanceMeters += GeoDistance.metersBetween(LatLng(previous.lat, previous.lng), LatLng(current.lat, current.lng)) …}`

Its doc comment: "Distance sums `GeoDistance.metersBetween` over consecutive points rather than any straight-line shortcut — the whole reason a track is worth recording is that it is not a straight line." It is called on a finished track's full point list (Records / trip statistics); nothing calls it live during recording, and nothing maintains a running total as points arrive. Stage one's deferral of distance-travelled stands: **no live cumulative distance exists.**

### Path from the current position back to the origin along the track

**Nothing.** The only "distance back" in the app is straight-line: `ComputeReturnToStartUseCase` (`domain/ComputeReturnToStartUseCase.kt:20`, `distanceMeters = GeoDistance.metersBetween(currentLatLng, startLatLng)`), and the HUD's own `GeoDistance.metersBetween(here, there)` (`NavigationHud.kt:334`). There is no nearest-point-on-track projection, no cumulative-distance-at-index, no "remaining length from here to the start". The building blocks are all present — an ordered point list and a pairwise metre function — but the computation does not exist.

### Cost over a long track — the shape of the data

- **Storage:** one Room row per accepted point, `track_points(id, trackId, lat, lng, altitude, accuracyMeters, timestampEpochMillis)`, indexed on `trackId` (`data/local/TrackPointEntity.kt`). Read back whole: `SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestampEpochMillis ASC` (`TrackDao.kt:47`); `RoomTrackRepository.getById` loads the track row plus every point (`:28-31`). `Track`'s doc comment (`Track.kt:23-26`): "loaded together rather than paginated — a multi-hour track is at most a few thousand points at any sane sampling interval".
- **In memory during recording — yes, twice, by two different routes:** the service buffers accepted points in `pendingPoints` and flushes every `FLUSH_BATCH_SIZE = 20` (`TrackRecordingService.kt:129, :252`); `TrackRecordingViewModel.beginPolling` re-reads the *entire* track from Room every `POLL_INTERVAL_MILLIS = 15_000L` and holds it as `breadcrumbPoints` (`TrackRecordingViewModel.kt:249-257, :458`). So a live path-length computation would have an ordered `List<TrackPoint>` already in hand every 15 s, up to 20 points behind the service's buffer (and ~30–45 s behind reality, per the origin-seeding comment at `:265-270`).
- **How many points:** `TrackRecordingMode` (`domain/model/TrackRecordingMode.kt:30-32`) — HIGH_ACCURACY 5 s / 5 m, BALANCED 15 s / 15 m, BATTERY_SAVER 60 s / 30 m, both thresholds required, so an upper bound per hour of 720 / 240 / 60. A four-hour trip: at most ~2 900 / 960 / 240 points. Testers currently run HIGH_ACCURACY (`MainActivity.kt:425-431`).
- **What that costs:** a nearest-point search plus a suffix sum over ≤ 3 000 points is thousands of haversines per evaluation — sub-millisecond work on any phone. **The cost is not compute; it is the 15 s poll and the 20-point flush lag**, which bound how current the path can be, and the whole-track re-read every 15 s, which already happens and which a running total would make unnecessary.

---

## 3. Pace

### Do `TrackPoint`s carry speed?

**No.** `TrackPoint(lat, lng, altitude, accuracyMeters, timestampEpochMillis)` (`TrackPoint.kt:13-19`); `LocationFix.Update` has the same five fields (`LocationTracker.kt:39-45`); `AndroidLocationTracker.toFix()` maps `latitude, longitude, altitude?, accuracy?, time` and **discards `Location.speed`** (`AndroidLocationTracker.kt:68-74`). Speed is derivable only from consecutive positions and timestamps.

### Does anything compute a moving average or a pace?

**No pace, no speed, no moving average of either.** `grep -rn -i "speed|pace|velocity|movingAverage"` over `app/src/main` finds only Compose `Arrangement.spacedBy` hits. The one rolling window in the app is `DetectOffTrackUseCase` (`domain/DetectOffTrackUseCase.kt`): `WINDOW_SIZE = 3`, over the last three *straight-line distances to the start*, firing when `window.last() - window.first() > 25.0` — a direction-of-travel heuristic, not a speed. `ComputeTrackStatisticsUseCase` yields `durationMillis` and `distanceMeters` for a finished track, from which an *average* pace is one division, retrospectively.

### What happens to a pace estimate when the fix is gated

The gate is on the **live fix only** (`AvailabilityViewModel.collectLiveFixes`, `acceptLiveFix`); the recording path has its own gate (`LocationSampler`, `LocationSampler.kt:24-25`). Two facts follow:

- A pace built from **`breadcrumbPoints`** sees the recording gate's holes (BALANCED refuses > 50 m; HIGH_ACCURACY refuses > 30 m) and the sampler's minimum-distance rule: **a stationary user produces no points at all** ("a stationary period doesn't keep writing points once the interval elapses with no real movement", `TrackRecordingMode.kt:8-11`). So a five-minute rest is a five-minute gap between two points, and distance-over-elapsed-time across it is a *rest-inclusive* pace — which is arguably the right number for "how long to get back" and the wrong one for "how fast do I walk". That is a decision, flagged below.
- A pace built from **`liveFix`** sees the 50 m gate's holes: under canopy the held fix does not move for the duration, and any two-fix delta across the hole is distance-over-a-five-minute-gap. The pulse's premise holds exactly: "a pace computed across a five-minute hole is not a pace." Nothing in the code today would know the hole was there except by the timestamp gap, which is visible.

---

## 4. The alert mechanism

### Notification infrastructure that exists

Two channels:

| Channel | Where created | Importance | Used for |
|---|---|---|---|
| `track_recording` (`TrackRecordingService.CHANNEL_ID`) | `TrackRecordingService.createNotificationChannel()` (`:178-186`) | `IMPORTANCE_LOW` | the ongoing foreground-service notice, `setOngoing(true)`, tap opens `MainActivity` (`:188-202`) |
| `off_track_alert` (`OFF_TRACK_CHANNEL_ID`) | `createOffTrackNotificationChannel` in `MainActivity.kt:467-477`, called from `onCreate` (`:219`) | `IMPORTANCE_HIGH`, `enableVibration(true)` | `postOffTrackAlert` (`:491-505`): `PRIORITY_HIGH`, `setAutoCancel(true)`, id 1002 |

No third channel. No `setFullScreenIntent`, no `USE_FULL_SCREEN_INTENT`, no `WAKE_LOCK` permission, no wake lock anywhere (`grep` over `app/src/main` for all four: nothing). No `AlarmManager`, `WorkManager` or `JobScheduler` usage — the service's own doc comment records choosing a foreground service over the latter two and notes WorkManager "not currently a project dependency" (`TrackRecordingService.kt:44-48`).

### Does anything interrupt the user today?

**Yes, one thing: the off-track alert** — a HIGH-importance heads-up notification plus a two-pulse vibration (`OFF_TRACK_VIBRATION_PATTERN_MILLIS = [0, 250, 150, 250]`, `:465`), debounced by `OFF_TRACK_ALERT_COOLDOWN_MILLIS` in `TrackRecordingViewModel.returnToStart` (`:396-430`). It is the app's only sound/vibration/heads-up path and the only thing that reaches a user from outside the current screen. **Where it fires from is the finding:**

> `LaunchedEffect(trackUiState.offTrackAlertId) { if (trackUiState.offTrackAlertId == 0) return@LaunchedEffect; postOffTrackAlert(this@MainActivity); vibrateOffTrackAlert(this@MainActivity) }` — inside `setContent`, `MainActivity.kt:308-313`

It lives in the Activity's Compose composition, keyed on a ViewModel state. **Inferred, from AndroidX's window recomposer behaviour rather than from a run here:** the Activity's recomposer is lifecycle-aware and pauses its frame clock when the Activity is stopped, so a state change that arrives while the app is in the background does not recompose, and the effect fires when the app next comes to the foreground — which is after the moment. The ViewModel itself keeps collecting fixes in the background (`viewModelScope`, kept alive by the process, which the foreground service keeps alive while recording), so the *state* updates; only the delivery waits. If this inference is right, **the app has no path today that interrupts a pocketed phone**, and the off-track alert's field behaviour on a pocketed phone should be checked on hardware before this feature reuses its pattern. If it is wrong, the same path is reusable as-is. This is the single most important thing only hardware can answer.

### Can the app post an interrupting alert with the screen off, from the existing service?

The pieces that exist: a foreground service that is alive whenever a track is recording, with location permission and `foregroundServiceType="location"` (`AndroidManifest.xml:78-81`); a HIGH channel; a vibration function; `POST_NOTIFICATIONS` declared. The pieces that do not: the service posts nothing but its own ongoing notice; it does not observe any state that could raise an alert; there is no wake lock (a notification post and a vibration do not need one — the OS handles both — but any *computation* the service would do between fixes runs only while the process is awake, and with the FGS running it is); no full-screen intent (which is what turns a heads-up into something that lights a screen from off, and which since API 34 needs `USE_FULL_SCREEN_INTENT` and, on 14+, a user-granted special permission by default only for call/alarm apps). **So: from the service, a HIGH-importance notification with vibration can be posted with the screen off, today, with no new permission — the code to decide *when* is what does not exist.** Whether HIGH + vibration is enough to be felt in a pocket is the hardware question the pulse names.

### `POST_NOTIFICATIONS` on API 33+

Requested once, at the moment a recording starts (`MainActivity.kt:423-425`), via a launcher whose callback is empty by design: "No follow-up either way" (`:156-158`). On denial: the foreground service runs silently ("the OS simply won't display it", `:150-155`); `postOffTrackAlert` checks the permission and **returns without posting** (`:491-496`), while `vibrateOffTrackAlert` still runs (VIBRATE is install-time, `:507`). Nothing tells the user the alert channel is closed, and nothing re-asks. **For a safety alert, a denied `POST_NOTIFICATIONS` is a state the feature must know about and does not today** — reported, not designed.

### What fires when the app is not in the foreground and no track is recording?

**Nothing can.** Without the foreground service there is no process guarantee, and on API 26+ a background app's `LocationManager` updates are throttled to a few per hour regardless of the requested interval. `AvailabilityViewModel`'s live-fix collection keeps its listener registered for the life of the process, but the process is an ordinary background process the OS may kill. There is no alarm, no job, no receiver. **A sunset countdown that must interrupt a pocketed phone with nothing recording has no runtime to run in today** — and the two ways to get one (a foreground service without a recording; an exact alarm, which on API 31+ needs `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` and, for the former, a user grant) are both decisions this pulse does not make. Flagged below.

---

## 5. Where it shows

### Room left on the HUD

Measured last dispatch at 360 × 640 dp: search bar 49 dp; HUD 56 dp before the second row was added, ~80 dp after (48 dp interactive-size row + 24 dp coordinates band + 8 dp padding, arithmetic); bottom nav ~80 dp plus the real system-bar inset (device-only, Robolectric reports zero — CLAUDE.md's pitfall). Outside fullscreen that leaves roughly 430 dp of map. **A third HUD element costs either a third row (~24 dp at `labelMedium` with a tap band, ~16 dp without) or width in the first row, which is already the two compass columns, the weighted distance/status column and the 48 dp exit.** The first row's status line (`NAVIGATION_HUD_STATUS_TAG`) is empty whenever the fix is fresh and the user is not approaching (`NavigationHud.kt:342-346`) — a slot that exists and is usually blank — but it is also where "Approaching" and the stale messages already live, and CLAUDE.md's rule on "new capability is a new path, not a conditional threaded into working code" applies. Constraint reported; no layout proposed.

### A surface when the HUD is closed and nothing is navigated

**None on the map for this purpose.** The map's chrome is: the compass strip (top, present whenever not navigating), the icon cluster, the taxon filter chip, the HUD (navigating only), the bottom nav. `SearchNotice` (`AvailabilitySearchUi.kt:521`) is a text strip for search/permission failures composed at three call sites (`AvailabilityScreen.kt:1173, :1585, :1754`), driven by `AvailabilityUiState`'s error fields — a message surface, not a persistent state one. `OfflineResultsBanner` (`AvailabilityResultsUi.kt:140`) is a `Card` inside the *results list*, not the map. The one `SnackbarHost` is the compact scaffold's, used for the log-draft "Discard" undo (`AvailabilityScreen.kt:934-942`); a Snackbar is transient by construction. **So there is a notice strip and a snackbar to borrow, and no persistent-state surface on the map that survives the HUD being closed** — anything continuous (a countdown) would be new chrome, with the touch-interception pitfalls CLAUDE.md records for chrome over the map.

---

## 6. What the gate changed — consequences for this feature

1. **Two "distance back" numbers from two differently-filtered streams already exist.** The HUD's distance comes from the gated `liveFix`; `TrackRecordingViewModel.returnToStart(point)` is called for **every** `LocationFix.Update`, gated or not — the sampler decides only the origin seed, and `returnToStart(point)` runs after it regardless (`TrackRecordingViewModel.kt:274-292`: `if (active != null && LocationSampler(active.mode).shouldAccept(…)) { … }` then `returnToStart(point)` outside that `if`). The pill's TalkBack sentence and the off-track heuristic therefore use ungated fixes. A return-time estimate must pick one stream, and the pick is a decision: the gated one is honest and goes stale under canopy; the ungated one moves and may be 60 m wrong. Flagged.
2. **A held fix means a held "here".** Under canopy the HUD's position is where the user stood when the last ≤ 50 m fix arrived — up to five minutes and, at walking pace, up to ~400 m ago — and the error grows in exactly the conditions (heavy canopy, dusk) the feature targets. Any estimate derived from it is an estimate from a position the user has left.
3. **The track keeps growing while the HUD is stale.** The recording gate is looser than the live gate for BATTERY_SAVER (100 m) and equal for BALANCED (50 m), tighter for HIGH_ACCURACY (30 m); so a stale HUD does not imply a stalled track, nor the reverse. The last accepted `TrackPoint`'s timestamp is an independent, already-persisted "how old is my last known position" the feature can read.
4. **The puck diverges** (previous report): the map marker is fed by MapLibre's own listener, so a user can watch themselves move while the HUD's estimate is frozen. A countdown that freezes beside a moving marker will read as broken, exactly as the stale distance would.

### How an estimate degrades honestly — what the data supports

The owner's requirement is "at least X". The data supports a **lower bound that only grows while the fix is stale**: at the moment of the last accepted fix the path home was *L* metres; every second since, the user may have walked further from home but cannot have shortened the path home by more than their pace × elapsed — and with no pace (§3) the honest floor is simply *L* itself, unchanged, plus the elapsed time already spent. That is a floor, not an estimate, and it holds regardless of pace policy. Everything tighter than that needs a pace, which needs the decisions in §3. The HUD already has the vocabulary for this — `FixFreshness.STALE` de-emphasises and shows the age; `LOST` withholds the number (`domain/NavigationReadout.kt:53-62`) — so "at least X, last fix N min ago" is a formatting of facts the state already carries. Reported as what the data supports; the wording and the thresholds are the owner's.

---

## 7. Existing state to reuse

### What identifies the origin

Confirmed, both pointers, each with one meaning:

- `Track.originWaypointId: String? = null` — "an explicit pointer to the `Waypoint` that marks where this track started … `null` for every track recorded before the HUD exists, and for any track whose origin was never created" (`Track.kt:19-27, :35`).
- `Waypoint.trackId: String? = null` — "the `Track` this waypoint was dropped **while recording**, or `null` … The richer meaning, not 'this track's origin'" (`Waypoint.kt:12-22, :30`); plus `Waypoint.designation: WaypointDesignation?` with `ORIGIN` (`:31-32`).
- Read path: `GetTrackOriginWaypointUseCase` (`domain/GetTrackOriginWaypointUseCase.kt:21-25`), null when the track has no pointer.

**A track with no origin is a valid state**, and the code treats it as one: the origin is seeded by "the first fix that passes the active mode's accuracy gate, no timeout (owner decision) … under canopy it may never [arrive], in which case the track validly has no origin" (`TrackRecordingViewModel.kt:264-270`). The HUD then says "No origin waypoint for this track" (`NavigationHud.kt:317`); the return arm's successor, `returnToStart`, falls back to the first breadcrumb (`:417-419`). Two different fallbacks for the same missing origin — reported.

### Does this feature need a recording?

**Position without a recording:** yes — `AvailabilityUiState.liveFix` is collected for the life of the process whenever permission is held, recording or not (`AvailabilityViewModel.collectLiveFixes`, `:140-148`), gated at 50 m, with age via `ageMillis`. **Origin without a recording:** no — there is no notion of "where I started today" outside a track; `TrackRecordingUiState.originWaypoint` is null with no active track (`:206`), and waypoints dropped with no recording carry `trackId = null`. **Runtime without a recording:** none in the background (§4). So without a recording the feature would have a position and a sunset, and neither a home nor a way to interrupt. Not decided here.

---

## Decisions this pulse does not list — flagged, not picked

- **Which distance-back stream** an estimate uses: gated `liveFix` (honest, goes stale) or the ungated fixes `returnToStart` already consumes (moves, may be 60 m wrong) — §6.1.
- **Rest-inclusive or moving pace.** The sampler writes no points while stationary, so `breadcrumbPoints` time-over-distance includes rests by construction; a "moving pace" needs a stationary detector that does not exist — §3.
- **A denied `POST_NOTIFICATIONS`** is currently silent and un-re-asked; a safety alert needs a stance on it — §4.
- **The runtime with no recording:** a foreground service without a track, or an exact alarm (each with its own permission story) — §4.
- **The two existing no-origin fallbacks** ("No origin waypoint" on the HUD vs first breadcrumb in the pill's sentence) should be one before a third consumer of "home" is added — §7.
- **Whether the full-screen-intent path** (the only thing that lights a dark screen) is in scope at all; it is a special permission on API 34+ — §4.

## Standing rules

**Tests as found on `41ce4e1`:** **1134 tests, 0 failures, 0 errors, 24 skipped** (`./gradlew :app:testDebugUnitTest --continue` on the unmodified tree, summed from the JUnit XML); the skipped set is byte-identical to the CI allowlist (24 entries). Matches the pulse's baseline of 1134 / 24. the `JournalTabTest` "From Album" flake did not fire. Nothing modified to run it. `JournalTabTest`'s "From Album" flake: see the same line.

## Only hardware can answer

1. **Whether an alert wakes a phone in a pocket** — the off-track alert's HIGH channel + two-pulse vibration, today, screen off, app backgrounded, recording running: does it fire at all in the background (the recomposer inference in §4), and is it felt?
2. Whether `Location.speed` is populated by the device's GPS provider (it is discarded today; if it is reliable it is a pace input the app is throwing away).
3. The real bottom-nav height with system insets, for the HUD room figure.

## Required disclosure

**Confirmed (read on `41ce4e1`):** every file/line above; `CivilTwilight` has no production caller; no sunset field is requested or stored; no speed on any point type; no pace, no live cumulative distance, no path-home computation; two channels at LOW and HIGH; no wake lock, alarm, job or full-screen intent; the off-track alert fires from a `LaunchedEffect` in the Activity's composition; `returnToStart` runs on ungated fixes; both origin pointers and the no-origin state.
**Inferred:** that the lifecycle-aware window recomposer pauses recomposition while the Activity is stopped, so the off-track effect does not fire in the background (from AndroidX's design, not from a run here); the ~80 dp HUD height (measured 56 + arithmetic 24); background `LocationManager` throttling on API 26+ (platform documentation); the API 34+ full-screen-intent permission behaviour (platform documentation).
**Could not determine:** everything under "Only hardware can answer".
**Premises in this pulse that were wrong:** "no branch, no commit to product code" — kept; the report itself is committed to the branch as the pulse asks. The pulse's implicit premise that the alert could reuse "the existing service" is incomplete rather than wrong: the service can post, but nothing feeds it a decision, and the app's one existing interrupt does not route through it. The premise that solar time "may have no implementation" is half-right: the altitude function exists; the time does not.
**Decided without cover:** nothing built. The ranking of the missing pieces (delivery largest) is my judgement and is stated as such.
