# Compass while the screen is off: first stop (look only)

Dispatch `prompts/preserved/2026-10-30-14-compass-screen-off.md` (on `origin/records-after-173`), RECORD -1857. Written by
Hazel, 2026-10-30. Look only: no app code was written or changed. Read first, as the dispatch says: Part 2 of
`docs/audits/2026-10-30-s22-ci-build-and-redraws.md` (8527a76fd) and RECORD -1856.

**Base.** Branch `compass-screen-off` cut from `origin/pre-main`; `git rev-parse origin/pre-main` after `git fetch origin`
gave `b493cffebe6aa1e5183ad23c98c6feb0f7c54f26`, the dispatch's b493cffeb. Every line reference below is read at that
commit.

## Premise check ("What exists")

| Dispatch line | Read | Verdict |
|---|---|---|
| `rememberTrueHeading` collects inside `produceState` (TrueHeading.kt:97-100) | `TrueHeading.kt:97` `produceState(...)`, `:100` `compassProvider.heading.collect` | Confirmed |
| Called from AvailabilityCompactMapUi.kt:417 | `AvailabilityCompactMapUi.kt:417` | Confirmed, **but not the only production caller**: `SightingsMap.kt:281` makes its own heading from `LocalMapCompass` for every map that is not handed one (the pickers and other maps under `AvailabilityScreen`, `ScreenMapLocals.kt:44`, provided at `AvailabilityScreenDrawerHost.kt:71`). `callbackFlow` is cold, so each such map registers its own sensor listener. |
| Listener at SENSOR_DELAY_UI (AndroidCompassProvider.kt:151) | `AndroidCompassProvider.kt:151` (rotation vector), `:153-154` (accelerometer + magnetometer fallback); unregistered in `awaitClose`, `:157` | Confirmed |
| Headings read by the strip (CompassElevationStrip.kt:126) and the map's engine (SightingsMap.kt:283-287) | Both confirmed | Confirmed, **but the list is incomplete**; see (a) |
| Nothing stops the collection when the activity stops | No `repeatOnLifecycle`, `flowWithLifecycle` or `collectAsStateWithLifecycle` anywhere in `app/src/main` (`git grep`) | Confirmed |
| About 11% of a core while recording, app in front | From the 10-30 audit's trials C and D | Not re-measured; quoted from that audit's scope (S22, desk, USB, one build) |

## (a) Every compass consumer, and whether any must run with the screen off

**Producer.** One: `AndroidCompassProvider` (`AppContainer.kt:223`), handed only to `MainActivity` (`MainActivity.kt:664`)
and from there down the screen's composables. `AvailabilityScreen.kt:136` also defaults to a new one (test convenience).
The only collectors of `CompassProvider.heading` in `app/src/main` are the two calls to `rememberTrueHeading` above
(`git grep "heading.collect\|compassProvider.heading"` finds only `TrueHeading.kt:100`). The app has one other
heading-shaped sensor, `AndroidLevelProvider` (camera's level line), collected only while the line is shown
(`LevelLine.kt:54`); not a compass consumer and not in scope.

**Readers of the `State<TrueHeadingReading>`**, all in `ui/`:

| Reader | Where | What it does with the heading |
|---|---|---|
| Compass strip text | `CompassElevationStrip.kt:126` | "304° NW" readout |
| Navigation HUD (needle, north arrow, heading) | `NavigationInfo.kt:85`, passed through `NavigationHud.kt:161`, the portrait and landscape navigation cards (`CompactMapPortraitNavigationCard.kt:115`, `CompactMapLandscapeNavigationCard.kt:160`) and `CompactMapTopStrip.kt:99` | Draws |
| Navigation facing judge | `NavigationView.kt:108-122` (`rememberNavigationFacing`, called at `AvailabilityCompactMapUi.kt:421`) | Decides facing-up / calibrating / north-up for the map camera. **Also runs its own 250 ms tick loop while navigating** (`:119-122`), independent of the compass |
| Map puck / facing-up map | `SightingsMap.kt:283-287` via `TrueHeadingCompassEngine.update` (`NavigationView.kt:182-211`) | Turns MapLibre's location puck |

**Background readers: none found.** Read for each the dispatch names:

- **Recording service:** `service/TrackRecordingService.kt` and `service/RecordingWatches.kt` contain no "heading",
  "compass" or "bearing" (grep, no hits).
- **Off-track alert:** `domain/OffTrackJudge.kt:84` judges `TrackPoint`s (positions) only; `OffTrackReminder.kt` and
  `alert/AndroidAlertDelivery.kt` have no compass reference.
- **Walk-back / return to start:** its bearing is true-north from positions (`ReturnToStartInfo.kt:8`, `GeoDistance`), not
  the compass.
- **Navigation:** every navigation use of the heading is a composable above (HUD, facing judge, puck), all drawing.
- Every file in `app/src/main` that names `TrueHeadingReading`, `CompassReading` or `magneticHeadingDegrees` is under
  `ui/`, `domain/` (the types and the pure judge/use case) or `sensor/` (the provider) (`git grep -l`, 20 files).

So nothing needs the heading while the activity is stopped. Inferred limit of that claim: it covers `app/src/main` at
b493cffeb. The walk logger on `dev` has its own sensors (10-30 audit, "What the tools build adds") and was not read; it is
not on pre-main.

## (b) Proposed mechanism, and where it goes

**Prediction:** collect the compass only while the composition's lifecycle is at least `STARTED`. Screen off stops the
activity, so the `callbackFlow` is cancelled, its `awaitClose` unregisters the listener (`AndroidCompassProvider.kt:157`),
no heading reaches any reader, nothing invalidates the view tree, and the redraw triple stops. Screen on starts the
activity, the flow is collected again and the listener re-registered.

**Where:** inside `rememberTrueHeading` (`TrueHeading.kt:85-135`), and nowhere else:

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current   // androidx.lifecycle.compose, as SightingsMap.kt:32
return produceState(initialValue = initial, compassProvider, computeTrueHeading, hasFix, lifecycleOwner) {
    val trust = CompassTrustJudge()
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val smoother = HeadingSmoother()            // new per start: see (c)
        compassProvider.heading.collect { ... }     // body unchanged
    }
}
```

(Sketch of placement, not the code to land.) `repeatOnLifecycle` is in `lifecycle-runtime-ktx` 2.11.0, already a
dependency (`gradle/libs.versions.toml:77`, `app/build.gradle.kts:545`); no new dependency. The app has one activity and no
`NavHost` (`git grep`), so `LocalLifecycleOwner` is `MainActivity` in production and the compose rule's `ComponentActivity`
in tests.

Why here:

- Both production collectors go through this function, so one change covers the Maps tab and every picker map, and a
  future caller gets it for free.
- `STARTED`, not `RESUMED`: the activity is paused but still visible under a permission dialog, in multi-window and with
  other overlays; the compass should keep turning while the user can see it. `STOPPED` is exactly "not visible".
- Domain stays free of Android lifecycle: `CompassProvider` and `AndroidCompassProvider` are untouched.

Rejected:

- **In `AndroidCompassProvider`, via `ProcessLifecycleOwner`:** ties the sensor wrapper to app-wide UI lifecycle, and
  `ProcessLifecycleOwner` dispatches its stop on a delay (about 700 ms, from AndroidX's documentation as I remember it, not
  read here). It would also need a new dependency (`lifecycle-process` is not in the catalog; grep shows only the four
  lifecycle entries at `:77-80`).
- **At each call site:** two sites today, and the next map that takes `LocalMapCompass` would miss it.

Not proposed, and named so it is not mistaken for part of the fix: `rememberNavigationFacing`'s 250 ms tick
(`NavigationView.kt:119-122`) keeps running while stopped during a navigation. It writes the same value while the heading
stands still (`mutableStateOf` drops an equal write, so it should cause no frame; inferred, not measured). Leaving it is
the narrow reading of "must not change how the compass behaves on screen". Its one visible consequence is in (c).

## (c) The first heading after the screen comes back

What each reader shows, under the mechanism above, in order:

1. **During the stop:** the `State` keeps the last value. Nothing is drawn (screen off).
2. **Screen on, before the first new reading:** the last value is shown. On the S22 the gap was about 200 ms on a tab
   return (RECORD -761, quoted in `TrueHeading.kt:94-95`). The phone may have turned meanwhile, so this is a stale arrow
   for that gap.
3. **First new reading:** with a fresh `HeadingSmoother` per start, it is shown as-is, a snap to the true heading, the
   same shape `TrueHeading.kt:118-123` already chose for recovery from an untrusted compass ("a visible snap ... is the
   truth arriving"). Keeping the old smoother would instead blend from the pre-sleep heading at alpha 0.3, a sweep that
   looks like the phone turning, which is the jump the dispatch rules out. So the smoother is new per start.

Point 2 is a choice the dispatch's "no stale arrow" touches, and I don't want to pick it alone. The options:

- **A. Keep the last value until the first reading.** No "—" flash; a stale arrow for about 200 ms, then a snap.
- **B. On stop, show "—" (`NeedsFix`) until the first reading.** Never stale. Shows the "—" flash that RECORD -761 had
  removed for the tab return (the owner: "Keep last reading (Recommended)").
- **C. Apply -761's own rule to the screen-off gap.** On return, keep the last value if it is within
  `HEADING_KEPT_FOR_MS` (10 s, `TrueHeading.kt:162`) of the stop, else "—" until the first reading. A quick glance away
  behaves like a tab return; after a long pocket walk the phone has likely turned, and "—" is the honest answer, the
  reasoning already written on `HeadingMemory.fresh` (`:151-156`). Costs a few lines in `rememberTrueHeading`; the
  `HeadingMemory` class is reused, not changed.

My reading is that C matches both the dispatch and -761, but it is a behaviour choice, so it is the planner's (and maybe
the owner's) call. Under B and C, the puck: `TrueHeadingCompassEngine.update` with a non-`Available` reading keeps the
last heading and reports accuracy `UNRELIABLE` (`NavigationView.kt:200-211`). What MapLibre draws for an unreliable
compass accuracy was not read.

**The trust judge across the stop** is a second call. A fresh judge per start would forget an untrusted compass, so a
compass that was "Compass calibrating…" at screen-off could read as trusted on return without the clear hold, the false
all-clear `CompassTrustJudge.kt:20-22` exists to prevent. Keeping one judge across the stop (as in the sketch) keeps the
`unreliable` flag, but `goodSinceMillis` can then date from before the stop: the first good reading after return satisfies
`reading.timestampMillis - since >= clearHoldMillis` (`CompassTrustJudge.kt:54`) at once, with no hold since. The narrow
fix is a new function on the judge that restarts only the clear hold (keeps `unreliable`, clears `goodSinceMillis`),
called at each start. Proposed, not decided.

**Navigation, seen from the stop:** if the heading was `Unreliable` at screen-off, the facing judge's tick still runs,
reaches 15 s and turns the map north-up while the screen is off, as it does today with the screen on; on return it
reads north-up until the compass is `Available` again (`NavigationView.kt:89-99`). Unchanged by the fix; noted so a
device check does not file it as new.

## (d) Tests through real entry points

Template: `LandscapeHeadingReadersTest` (`createAndroidComposeRule<ComponentActivity>()`, the real
`AndroidCompassProvider` over Robolectric's `ShadowSensorManager`, `shadow.listeners` read directly, `:63-126`). The real
lifecycle entry point is `composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)` then `RESUMED`, which
drives the activity's own `onStop`/`onStart`; it is already used this way in `AvailabilityScreenBackNavigationTest.kt:152-154`
and `EntrySaveFailureShownTest.kt:372-374`.

Proposed tests (a new test file, so no 800-line file grows):

1. **Collection stops.** Compose a reader of `rememberTrueHeading` with the real provider; assert one listener registered.
   `moveToState(CREATED)`; assert `shadow.listeners` is empty. This is the proof: the listener's removal is
   `awaitClose` running, which happens only if the collecting coroutine was cancelled. Fails today with "listener still
   registered while stopped".
2. **Collection resumes, once.** `moveToState(RESUMED)`; assert exactly one listener (not two: a leaked first collector
   would show as two).
3. **No heading lands while stopped.** Send a rotation-vector event while stopped (`ShadowSensorManager` delivers only to
   registered listeners, so this also cross-checks 1); after return the strip still shows the pre-stop value (or "—"
   under B/C), not the event's heading.
4. **The first heading after return is not blended.** Before stop, settle the strip at heading X; while stopped nothing;
   after return send Y; assert the strip shows Y's true heading exactly, not a smoothed value between X and Y. Fails if
   the smoother is kept.
5. **The trust hold restarts** (if the judge change is accepted): untrusted before stop, good readings after return,
   still "calibrating" until the hold has elapsed after return.
6. **Under C:** a stop shorter than 10 s returns showing X; longer shows "—" until the first reading. Driven by the
   `nowMillis` clock `rememberTrueHeading` already takes (`TrueHeading.kt:91`), from the compact map's `currentTime`.
7. **Through the real screen:** one test composing `AvailabilityScreen` on the Maps tab, so the claim covers the real
   production call site (`AvailabilityCompactMapUi.kt:417`), not only a test host calling `rememberTrueHeading`.

Revert check, per CLAUDE.md's runner rules: remove the `repeatOnLifecycle` wrapper (one edit) and confirm 1 and 3 fail with
their own messages; move the smoother out of the block and confirm 4 fails.

One thing a Robolectric test cannot show: that a real screen-off stops the activity (`onStop`), which this whole fix rests
on. The 10-30 audit's log names `windowStopped` lines, which fits, but the activity's state was not logged there. That,
and the redraw lines stopping, are device-check steps (the S22: screen off while recording with the app in front, count
the triple per minute, read `dumpsys sensorservice` for the listener). Unrelated to the tests: whether Compose pauses the
strip's recomposition while stopped (it may pause the frame clock then) was not read; the puck's path through
`snapshotFlow` does not need frames either way, which would fit the 10-30 audit's finding that the redraws continue.
Inferred, not read.

## (e) Files over 800 lines touched

None. The fix would touch `ui/map/TrueHeading.kt` (162 lines), possibly `domain/CompassTrustJudge.kt` (small; the new
clear-hold function), and add one test file. Read for line counts and not to be touched: `SightingsMap.kt` (779),
`AvailabilityCompactMapUi.kt` (696), `NavigationView.kt` (569), `AndroidCompassProvider.kt` (241).

## Disclosure

- **Confirmed (read at b493cffeb):** every file and line above; the two production collectors; no background reader in
  `app/src/main`; no lifecycle-aware collection exists; the dependency is present.
- **Inferred:** that screen off stops `MainActivity` (platform behaviour, fits the log's `windowStopped` lines); that the
  facing tick causes no frame while the heading is still; whether Compose pauses recomposition while stopped.
- **Could not determine:** what MapLibre draws for `UNRELIABLE` compass accuracy; the cost after the fix (needs the
  phone).
- **Premises that were wrong or incomplete:** `rememberTrueHeading` has a second production caller,
  `SightingsMap.kt:281` (every map under `AvailabilityScreen` not handed a heading), each with its own sensor listener;
  the readers include the HUD, the navigation cards and the navigation facing judge, not only the strip and the puck.
  Neither changes the mechanism; both widen what the tests must cover.
- **Decisions left open for the planner:** (c)'s A/B/C on the first heading after return; the trust judge's clear hold
  across a stop.
- **Beyond scope:** none. No code written.
