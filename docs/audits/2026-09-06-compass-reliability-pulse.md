# Pulse: compass reliability — what the magnetometer already tells us and nobody reads

**Type:** read-only survey (owner-directed pulse). **Nothing built; no product code, no tests.**
**Date:** 2026-09-06. **Base:** `main` at `41ce4e1412a3fae52089c7c1fe0a0cc6e98f818f` (confirmed). Branch `claude/new-session-102gri` sits on it with docs-only commits (the two prior pulses and their addendum); this report is another.

Every claim names a file and line on `41ce4e1`, or is marked as platform documentation or inference. Nothing is carried forward from earlier pulses without being re-read.

---

## The answer first: the accuracy signal is reachable from the sensor path the app already uses. Nothing about the registration has to change.

`AndroidCompassProvider` registers one `SensorEventListener` (`sensor/AndroidCompassProvider.kt:43-62`) and the platform already delivers three accuracy signals to exactly that listener, on exactly those registrations:

1. **`onAccuracyChanged(sensor, accuracy)`** — implemented, and **empty**: `override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit` (`:61`). Someone saw the callback and declined it.
2. **`SensorEvent.accuracy`** — the same status value, carried on **every** `onSensorChanged` event; the code reads `event.sensor.type` and `event.values` (`:45-57`) and never `event.accuracy`.
3. **Rotation-vector heading accuracy** — on the preferred path the event's `values[4]` is the sensor's own *estimated heading accuracy in radians* (−1 when unavailable; API 18+, this app's `minSdk` is 26). The code hands `event.values` to `getRotationMatrixFromVector` (`:46`), which uses `values[0..3]`, and never looks at the fifth element.

So this is the small case the pulse hoped for: a message or a suppression can be built on a value the listener is already handed, through the owned interface, without touching what is registered. The **one** signal that would need a registration change is a magnetic-field **magnitude** (§2), because on the rotation-vector path no `TYPE_MAGNETIC_FIELD` listener exists.

**And the app conflates nothing:** the compass path (`CompassProvider` → `HeadingSmoother` → `ComputeTrueHeadingUseCase`) touches location only to look up declination (`ComputeTrueHeadingUseCase.kt`, lat/lng/altitude/epoch → `GeomagneticField.declination`). No GPS accuracy, freshness or gate value enters any heading computation; no heading value enters any fix gate. The two failure modes are separate in the code as they are in the world.

---

## 1. What the app reads today

**`CompassProvider`, in full** (`domain/CompassProvider.kt`):

```kotlin
interface CompassProvider {
    /**
     * Heading in degrees clockwise from magnetic north, in `[0, 360)`, emitted on every sensor
     * update while collected. `null` when this device has no usable rotation sensor (neither a
     * rotation-vector sensor nor an accelerometer+magnetometer pair) — an explicit "unsupported"
     * per CLAUDE.md, never a fabricated or stale last-known value.
     */
    val heading: Flow<Float?>
}
```

A `Float?`: heading or "no sensor". **The interface has no place for a quality signal today.** Any accuracy reaching the domain changes this owned type (a new emitted shape), which is a domain change but a contained one — three production readers (`rememberTrueHeading`, and through it the strip and the HUD) and one test fake (`FakeCompassProvider`, `AvailabilityScreenMapIconStackTest.kt:2972-2975`, a `MutableStateFlow<Float?>`).

**`AndroidCompassProvider`, the relevant parts** (`sensor/AndroidCompassProvider.kt`):

```kotlin
val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

if (sensorManager == null || (rotationSensor == null && (accelerometer == null || magnetometer == null))) {
    trySend(null)
    close()
    return@callbackFlow
}
…
val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                trySend(headingDegrees(orientation[0]))
            }
            Sensor.TYPE_ACCELEROMETER -> { lastAccelerometer = event.values.clone(); combinedHeading(…)?.let(::trySend) }
            Sensor.TYPE_MAGNETIC_FIELD -> { lastMagnetometer = event.values.clone(); combinedHeading(…)?.let(::trySend) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

if (rotationSensor != null) {
    sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
} else {
    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
}
```

- **Registration:** `TYPE_ROTATION_VECTOR` alone when present; otherwise `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`. Both at `SENSOR_DELAY_UI`. The class doc gives the reason: "Prefers `TYPE_ROTATION_VECTOR` (fused, more stable) and falls back … since not every device carries the fused sensor" (`:16-19`), tracing to `docs/plans/map-redesign.md:392-398` ("check both").
- **On the rotation-vector path there is no magnetometer listener at all.** The magnetometer is *looked up* (`:29`) to decide the fallback, then unused. The magnetometer's own accuracy status therefore never arrives on that path; the rotation-vector sensor's status does (§2 on how the two relate).
- **`onAccuracyChanged` is implemented and does nothing** (`:61`). This is the stronger finding the pulse asked about: the callback is in the file, typed correctly, and discarded.
- **`SensorManager.SENSOR_STATUS_ACCURACY_*` is referenced nowhere in the repository.** `grep -rn "SENSOR_STATUS" .` over `*.kt`, `*.xml`, `*.md`: no match. Nor `onAccuracyChanged` anywhere but this one empty override; nor "calibrat" in any source, resource or doc, save one unrelated plan line about "uncalibrated weather" (`docs/plans/forager-navigator-plan.md:336`).
- **The real provider has no test.** `grep -rln "ShadowSensorManager|AndroidCompassProvider|SensorEvent" app/src/test`: nothing. Every compass test drives `FakeCompassProvider`; `AndroidCompassProvider`'s sensor-type branching and its two registration paths have never been exercised under Robolectric.

Downstream of the provider, for completeness: `rememberTrueHeading` (`ui/map/TrueHeading.kt:60-79`) maps `null` → `NoSensor`, no fix → `NeedsFix`, otherwise `HeadingSmoother.next(magnetic)` → declination → `Available(degrees)`; `HeadingSmoother` (`domain/HeadingSmoother.kt`) is the unit-vector EMA at `DEFAULT_ALPHA = 0.3f`. Neither has a quality input.

---

## 2. What the platform offers at `minSdk` 26

Platform documentation (`android.hardware.SensorManager`, `SensorEvent`, `Sensor`), not this code; marked where inference is involved.

**Accuracy levels** (`SensorManager` constants, all available since API 1 except where noted):

| Constant | Value | Documented meaning |
|---|---|---|
| `SENSOR_STATUS_ACCURACY_HIGH` | 3 | maximum accuracy |
| `SENSOR_STATUS_ACCURACY_MEDIUM` | 2 | average; calibration with the environment may improve readings |
| `SENSOR_STATUS_ACCURACY_LOW` | 1 | low; calibration with the environment is needed |
| `SENSOR_STATUS_UNRELIABLE` | 0 | values cannot be trusted; calibration needed, or the environment does not allow readings |
| `SENSOR_STATUS_NO_CONTACT` | −1 (API 20) | body sensors only; not applicable here |

In practice (inference from field experience, widely reported): a magnetometer beside a vehicle, a fence or a speaker magnet drops to LOW or UNRELIABLE; devices come back to HIGH after motion through several orientations; and the *same* distortion that drops the level shifts the heading by the tens of degrees the pulse describes. The level is the platform's own statement that the number is not to be trusted — the signal this app's honesty pattern exists to surface.

**Per-reading and callback, and how they relate:** `SensorEvent.accuracy` is a public field on every event, carrying the current status; `onAccuracyChanged(sensor, accuracy)` fires on the same listener when the status changes. They are one value seen two ways: the callback is the edge, the field is the level. Either alone is enough; reading the field per event is the simpler of the two because it needs no separate state.

**The rotation-vector path:**

- `TYPE_ROTATION_VECTOR` events carry `values[0..2]` (the vector), `values[3]` (the scalar, API 18+), and **`values[4]`: estimated heading accuracy in radians, or −1 if unavailable** (API 18+). That is a *heading* accuracy, in the unit the app already works in, from the sensor the app already listens to — arguably the most direct signal available, and one the code discards today.
- The rotation-vector sensor's *status* (`SensorEvent.accuracy` / `onAccuracyChanged`) is the fused algorithm's own; on Android devices the fusion's accuracy status is driven by the magnetometer's calibration state, since the magnetometer is what gives the fused vector its yaw reference — **inference**, consistent with the platform's sensor HAL documentation, not something this code or a device here establishes.
- **Magnetometer accuracy specifically is *not* reachable on the rotation-vector path as registered.** To read the magnetometer's own status the app would additionally register `TYPE_MAGNETIC_FIELD` (or `TYPE_MAGNETIC_FIELD_UNCALIBRATED`, API 18+, which also exposes the estimated hard-iron bias in `values[3..5]`). That is an additive registration, not a replacement, and it is the only part of this that touches what is registered.

**Anything else that indicates distortion, reachable in principle:** the magnetic field **magnitude**. `TYPE_MAGNETIC_FIELD` events give the field in µT per axis; `GeomagneticField.getFieldStrength()` gives the expected total field in nT at a position and date — and this app already constructs `GeomagneticField` for declination (`sensor/AndroidDeclinationProvider.kt`). A magnitude far from the expected value (Earth's field is roughly 25–65 µT; a nearby magnet or current-carrying conductor moves it well outside that) indicates distortion independently of the status level. Reachable only with the magnetometer registered (§ above). Reported as reachable; not proposed.

---

## 3. Where a signal would surface — surfaces and constraints, not a design

**The compass strip's full vocabulary**, and where each decision is made (`ui/availability/AvailabilityScreen.kt`, `CompassElevationStripContent`):

| State | Text | Decided by |
|---|---|---|
| no fix, whatever the sensor says | `NO_FIX_MESSAGE` = "Location services unavailable", the strip's whole content | `if (location == null)` (`:4210-4215`) |
| fix, heading available | `"${degrees}° ${cardinal}"` | `is TrueHeadingReading.Available` (`:4248`) |
| fix, no sensor | "Compass unavailable" | `TrueHeadingReading.NoSensor` (`:4249`) |
| fix, sensor present, fix not yet applied (transient) | "—" | `TrueHeadingReading.NeedsFix` (`:4250`) |

The decision input is `TrueHeadingReading` (`ui/map/TrueHeading.kt:17-30`), a sealed type with exactly `Available(degrees)`, `NoSensor`, `NeedsFix`. **A new state joins that sealed type and both `when`s (strip and HUD) must handle it, or the compiler refuses** — the set is closed, which is what keeps it from multiplying silently. The elevation and coordinates segments beside the heading are independent of it.

**The HUD** (`ui/availability/NavigationHud.kt`, `navigationReadout`):

- North compass label, the same `when` as the strip: `Available` → degrees, `NoSensor` → "Compass unavailable", `NeedsFix` → "—" (`:319-323`); the north arrow rotates only for `Available` (`northArrowDegrees = headingDegrees?.let { -it }`).
- **The needle's existing suppression, quoted because a second condition would sit beside it:**

  ```kotlin
  val approaching = freshness != FixFreshness.LOST && isApproaching(distanceMeters, liveFix.accuracyMeters)   // :343
  val targetArrowDegrees = if (headingDegrees != null && freshness != FixFreshness.LOST && !approaching) relativeBearingDegrees(bearing, headingDegrees) else null   // :348
  val targetText = when {
      freshness == FixFreshness.LOST -> "Target"
      approaching -> ""
      headingDegrees != null -> "Turn ${…}°"
      else -> "Bearing ${…}° ${cardinal}"
  }   // :349-356
  ```

  Three things already null the needle: no heading, a lost fix, and the approach threshold. Each has its own text. A compass-reliability condition would be a fourth term in the `if` and a fourth branch in the `when`, and the two `when` orders decide which message wins when two conditions hold at once (a lost fix *and* an unreliable compass, say). That ordering is a decision the pulse does not list — flagged below.
- Status line: `LOST` → "No fix for …", `STALE` → "Last fix … ago" / "Approaching · last fix … ago", `FRESH` → "Approaching" or empty (`:357-361`). Empty for most of a walk — a slot that exists and is usually blank — but it is the GPS-freshness slot, and the pulse's own rule not to conflate the two sensors applies to the copy as much as the code.

**Room:** measured last dispatch at 360 × 640 dp — strip 18 dp; HUD first row 56 dp (48 dp interactive-size `IconButton` + padding), second row ~24 dp by arithmetic, ~80 dp total; search bar 49 dp; bottom nav ~80 dp plus a device-only inset. The heading label in both surfaces is `labelMedium`, one line, `maxLines = 1`; "Compass unavailable" already fits where a heading does, so a message of similar length fits without new height. Anything longer, or a second line, costs height on a surface that just grew a row. Constraint reported; nothing proposed.

---

## 4. What already handles sensor problems

**No magnetometer at all — the "Compass unavailable" path, end to end:**

1. `AndroidCompassProvider`: neither a rotation-vector sensor nor an accelerometer+magnetometer pair → `trySend(null); close()` (`:31-35`) — one `null`, then the flow completes.
2. `rememberTrueHeading`: `magnetic == null -> { smoother.reset(); TrueHeadingReading.NoSensor }` (`TrueHeading.kt:66-69`).
3. Strip and HUD: "Compass unavailable" (`AvailabilityScreen.kt:4249`, `NavigationHud.kt:321`); the HUD's needle falls back to an absolute bearing as text — `else -> "Bearing ${…}"` (`NavigationHud.kt:355`) — with no arrow.

**Could an unreliable-but-present sensor reuse it?** The *shape* yes, the *value* no. `NoSensor` is terminal: the flow closes, the smoother resets, and nothing recovers until the composable restarts the producer. An unreliable compass is a live, reversible condition — it is reliable again ten metres from the truck — so it cannot be `NoSensor`; it needs its own value in the sealed type, on a flow that keeps emitting. The three-way `when` is where it would be handled, and the HUD's "Bearing N°" fallback (absolute bearing, no needle) is the existing rendering of "there is no trustworthy heading" that an unreliable state could share. Reported, not chosen.

**Precedents that degrade or suppress on a quality signal** — the two the pulse names, quoted so whatever is built looks like them:

- **Bearing suppression near the target** (`NavigationHud.kt:343-353`, quoted in §3): one boolean derived from the fix's own reported accuracy, applied to the arrow and the text together, with the status word ("Approaching") carried on the same boolean so the needle and the word cannot drift apart — the class doc's reasoning is "a smoothed unstable bearing is a stable wrong direction" (`NavigationHud.kt:121-131`).
- **Accuracy-aware distance** (`domain/model/DistanceUnit.kt:105-127`): `if (accuracyMeters == null) return formatDistanceMeters(distanceMeters, unit); … if (distanceMeters <= accuracy) return "within ${…}"` — no accuracy means no coarsening and no marker ("inventing a resolution would be the same fabrication in the other direction"); inside the error circle the number is replaced by the bound; outside it the number is rounded to what the accuracy supports and marked `≈`.

Both share one rule: the quality signal changes what is *shown*, never what is *computed*, and a missing signal leaves today's behaviour untouched. `HeadingSmoother` is deliberately outside this pattern — the heading is smoothed, the bearing is not (`NavigationHud.kt:129-131`) — and a compass-accuracy signal would be the first quality input on the heading side.

---

## 5. Calibration

**Mentions in the app:** none. No string in `res/values/strings.xml` mentions the compass at all; no doc comment, help screen or plan line mentions calibration except the unrelated weather note (§1).

**What an app can do — platform facts, with inference marked:**

- **An app cannot trigger magnetometer calibration.** There is no public API for it; calibration is performed continuously by the sensor HAL / fusion from the device's own motion. Confirmed against the platform API surface (no such method on `SensorManager` or `Sensor`).
- **What an app can do** is read the status (§2) and tell the user, since the calibration input is the user moving the device through orientations (the "figure-eight"). Whether the system itself ever prompts: older Android versions and some OEM builds show a system calibration dialog; current stock Android does not surface one for third-party apps, and apps that care (mapping apps) draw their own instruction — **inference from observed platform behaviour, not from documentation this pulse can cite.** The planner's understanding — cannot trigger, can only instruct — is **confirmed**.
- **A subtlety worth recording:** distortion from the environment (the truck) and poor calibration (the sensor's own offsets) both lower the status and both shift the heading, and the user's remedy differs: move away, versus move the phone. The status value does not distinguish them; a field-magnitude check (§2) partly can (a strong anomalous field is the environment). Reported as a limit of the signal, not a design.

**Where a short instruction could live without a modal:** the same one-line label slot the strip and HUD already use for "Compass unavailable" (`labelMedium`, `maxLines = 1`); the HUD's usually-empty status line (with the conflation caveat in §3); the settings tab's plain sections (`NightModeMapsSection`, `DistanceUnitSection` — `AvailabilityScreen.kt` around `:2470-2530`), which is where a static explanation rather than a live state would sit. `SearchNotice` (`AvailabilitySearchUi.kt:521`) is a transient error strip, not a state surface. Nothing in the app today interrupts modally for a sensor condition, and CLAUDE.md's rules on chrome over the map apply to anything new on that surface.

---

## 6. Testability

**Can sensor accuracy be driven under Robolectric?** Yes, for both signals, without new dependencies — established against the `shadows-framework-4.16.1` jar in this build's Gradle cache (`javap org.robolectric.shadows.ShadowSensorManager`), not from memory:

- `ShadowSensorManager.addSensor(int, Sensor)` / `addSensor(Sensor)` — make `getDefaultSensor(TYPE_ROTATION_VECTOR)` return a sensor, so `AndroidCompassProvider` takes the rotation-vector path (or withhold it, to take the fallback path).
- `static createSensorEvent(int)` and **`static createSensorEvent(int, int)`** — build a `SensorEvent` with a `values` array of the given length and, in the two-argument form, a given `accuracy` (the second `int`; Robolectric's own documentation names it so — the signature is what `javap` shows, the parameter meaning is from its docs). `SensorEvent.accuracy` and `SensorEvent.values` are also plain public fields, settable directly.
- `sendSensorEventToListeners(SensorEvent)` / `(SensorEvent, Sensor)` — deliver it to the registered listener, so `values[4]` and `event.accuracy` can both be driven per reading.
- `getListeners()` — returns the registered `SensorEventListener`s, so a test can call `onAccuracyChanged(sensor, SENSOR_STATUS_UNRELIABLE)` on the app's own listener directly. There is no dedicated helper for the callback; this is the route.
- `hasListener(listener, sensor)` — lets a test assert which sensors were registered, which would give `AndroidCompassProvider` the first test of its two registration paths.

`SensorManager.getRotationMatrixFromVector` and `getOrientation` are pure arithmetic in the framework and run under Robolectric (the declination work established the same for `GeomagneticField`); a test can therefore feed a real rotation vector and assert a real heading, then feed the same vector with `accuracy = UNRELIABLE` and assert whatever the new shape emits.

**Hardware only:**

- Whether a given device's rotation-vector status actually drops beside a vehicle, a fence, a transmission line — and by how many degrees the heading is wrong at that moment (a known bearing to a landmark is the reference). "Stand next to a car" is the test, and it is the honest description of it.
- Whether the device populates `values[4]` at all (−1 is permitted), and how it moves under distortion.
- Whether the status recovers on its own after the figure-eight, and how long that takes, since that decides whether an instruction is worth showing.
- What the device's system UI does, if anything, when the status is UNRELIABLE.

---

## Decisions this pulse does not list — flagged, not picked

- **Which of the three reachable signals** a reliability state is derived from: the status level (coarse, four values, well understood), `values[4]` heading accuracy (continuous, in degrees after conversion, availability device-dependent), or both.
- **Precedence in the HUD `when`s** when an unreliable compass coincides with a lost or stale fix, or with the approach threshold — two suppressions with different messages need an order.
- **Whether the smoother resets** on an unreliable→reliable transition, as it does on `NoSensor`/`NeedsFix` (`TrueHeading.kt:67, :71`); a reset snaps, no reset drags the bad heading into the good ones at alpha 0.3.
- **Whether the fallback (accelerometer+magnetometer) path** reports the magnetometer's status or the accelerometer's — on that path both sensors' statuses arrive on one listener and would need distinguishing by `sensor.type`.
- **Overlap with the alert-delivery dispatch:** none in code. **Overlap with the GPS work:** none in code; the only shared object is `GeomagneticField`, used for declination today and reachable for field strength (§2).

## Standing rules

**Tests as found on `41ce4e1`:** **1134 tests, 0 failures, 0 errors, 24 skipped** (`./gradlew :app:testDebugUnitTest --continue` on the unmodified tree, summed from the JUnit XML); the skipped set is byte-identical to the CI allowlist (24 entries). Matches the pulse's baseline of 1134 / 24. Nothing was modified to run it; the `JournalTabTest` "From Album" flake did not fire..

## Required disclosure

**Confirmed (read on `41ce4e1`, or run here):** the registration types and delay; the empty `onAccuracyChanged`; `event.accuracy` and `values[4]` never read; no `SENSOR_STATUS_*`, no calibration mention, no test of the real provider anywhere in the repo; the three-value sealed `TrueHeadingReading` and both `when`s; the needle's suppression terms and the formatter's rules; the Robolectric shadow's method surface (from the jar's bytecode). **Platform documentation, not this code:** the status constants and their meanings; `SensorEvent.accuracy`; `values[4]` on `TYPE_ROTATION_VECTOR` (API 18+); `TYPE_MAGNETIC_FIELD_UNCALIBRATED`; `GeomagneticField.getFieldStrength()`; the absence of any calibration-trigger API. **Inferred:** that the rotation-vector status tracks magnetometer calibration on Android devices; typical field-magnitude ranges; that current stock Android shows no system calibration prompt for third-party apps; the meaning of `createSensorEvent`'s second parameter (from Robolectric's docs, signature confirmed by `javap`). **Could not determine:** everything under "Hardware only". **Premises in this pulse that were wrong:** none found. The planner's expectation that the app reads none of it is confirmed as a finding, with the sharper form that the callback is implemented and discarded. **Decided without cover:** nothing; the "small case" framing at the top is the pulse's own dichotomy answered, not a design choice.
