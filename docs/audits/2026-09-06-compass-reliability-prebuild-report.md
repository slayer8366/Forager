# Pre-build report: say so when the compass cannot be trusted

**Dispatch:** "Say so when the compass cannot be trusted" (small feature, owner-directed).
**Date:** 2026-09-06. **Status:** report before building — nothing built, no product code changed.
**Base:** `main` at `41ce4e1412a3fae52089c7c1fe0a0cc6e98f818f` (confirmed). **Branch:** `claude/new-session-102gri` — already cut from that commit; it carries four docs-only commits above it (the three pulses and one addendum, `fcca1be`…`0563bfe`) and nothing else, so it was not restarted: restarting would have orphaned those reports, and rebasing docs-only commits onto the same base is a no-op. Reported so the eventual PR's five extra docs commits are not a surprise.

Every claim names a file and line on `41ce4e1`, or is marked as platform documentation or inference.

---

## One correction to the pulse this dispatch relies on

The pulse (`2026-09-06-compass-reliability-pulse.md`, §6) inferred that Robolectric's `ShadowSensorManager.createSensorEvent(int, int)` takes an accuracy as its second argument. **Read from the bytecode this time** (`javap -c` on `shadows-framework-4.16.1.jar`): the second `int` goes to `ShadowSensor.newInstance(int)` and into `SensorEvent.sensor` — it is the **sensor type**, not the accuracy. Testability is unchanged, because `SensorEvent.accuracy` is a public field a test sets directly, and `getListeners()` still exposes the app's listener for a direct `onAccuracyChanged` call; but the pulse's sentence was wrong and is corrected here. The rest of the pulse's ground was re-read for this report and holds.

---

## ITEM 1 — read the signal, with a fallback

### 1.1 Detecting "absent or implausible" for `values[4]` — and what cannot be distinguished

What the platform documents for `TYPE_ROTATION_VECTOR`: `values[4]` is "estimated heading accuracy (in radians) (−1 if unavailable)", present since API 18; older sensor implementations may deliver only three or four values. So three detectable cases and one that is not:

| Reading | Meaning | Detectable? |
|---|---|---|
| `values.size < 5` | the sensor does not supply the field | yes — array length |
| `values[4] < 0` (the documented `−1`) | explicitly unavailable | yes |
| `values[4]` NaN, or > π | nonsense | yes |
| `values[4] == 0f` | **either** "perfect" **or** an implementation that leaves the slot at its zero default | **no — not from the value** |

**I cannot distinguish a legitimate zero from an unpopulated one by the value alone, and neither can anyone: the two are the same bits.** What I can say is that the distinction does not need to be made, because of how the two paths compose. Proposal: **treat exactly `0f` as "not supplied" and fall back to the status level for that reading.** The cost of being wrong in the legitimate-zero case is nothing the user sees: a sensor whose heading accuracy is genuinely zero is, by the same sensor's own status, `ACCURACY_HIGH`, so the fallback answers "reliable" — the same answer the primary path would have given. The cost of being wrong the other way (trusting a zero that means "unpopulated") is the whole feature silently disabled on that device. Asymmetric, so the sentinel treatment is the safe side. Two supporting facts, marked as inference from field behaviour rather than documentation: a real fused estimate is never exactly zero (calibrated devices report small positive values, on the order of a few degrees), and devices that do not compute the field are the ones observed to leave it at zero. **The owner should know the design therefore rests on the fallback whenever the value is zero, and that a device which reports zero always is, in effect, on the status-level path.** Device check 1 in the dispatch's list is exactly this.

### 1.2 The threshold, in degrees, argued from what the needle is for

The needle's job is to point a walker at a target, and the HUD claims that to the degree ("Turn 265°", `NavigationHud.kt:354`). The question is at what heading error the needle stops helping and starts misleading, and the app already has the number to compare against: the position's own error circle, which the approach logic treats as twice the fix accuracy (`isApproaching`, `domain/NavigationReadout.kt:29-34`), typically 16–25 m with the 8–12 m accuracy testers see.

A walker who follows a needle with heading error *e* for distance *d* ends up *d*·sin(*e*) off the true line. Over 100 m of walking — about a minute at foraging pace, and roughly the range at which the needle is the thing being followed rather than the destination being visible — the lateral miss is:

| Heading error | Miss after 100 m |
|---|---|
| 5° | 9 m |
| 10° | 17 m |
| **15°** | **26 m** |
| 20° | 34 m |
| 30° | 50 m |

**Proposed threshold: 15° (0.2618 rad).** At 15° the needle walks the user out of the position's own error circle in the first 100 m — the needle is then less trustworthy than the fix it is drawn from, which is the point at which showing it confidently is the lie this dispatch removes. Below 15° the miss stays inside the band the app already calls "Approaching", so the needle is at least as good as the position. Not chosen against a round number: 15° is also comfortably above what calibrated devices report (a few degrees, inference) and comfortably below the tens of degrees a vehicle produces, so it neither fires on a healthy sensor nor misses a distorted one. The literal in the test must be pinned as **0.2618 rad ↔ 15.0°** with both sides written out, not derived from the constant.

**Equivalent on the status-level path:** `SENSOR_STATUS_UNRELIABLE` (0) and `SENSOR_STATUS_ACCURACY_LOW` (1) → unreliable; `MEDIUM` (2) and `HIGH` (3) → reliable. The platform's own wording draws the line there: LOW is "calibration with the environment is needed", UNRELIABLE is "values cannot be trusted"; MEDIUM is "may improve". This mapping is coarser than 15° by nature — the buckets do not say how many degrees — and the two paths produce the same user-visible state, which is what the dispatch requires; they will not always agree on the same reading, and that is inherent, not a bug to fix.

### 1.3 Which sensor's status the fallback reports — established

**On the rotation-vector path, the rotation-vector sensor's own status, and nothing else can arrive.** `AndroidCompassProvider` registers the listener for exactly one sensor on that path — `sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)` (`sensor/AndroidCompassProvider.kt:65`) — and the magnetometer is only looked up to decide the fallback (`:29`), never registered. So `onAccuracyChanged(sensor, accuracy)` fires with `sensor.type == TYPE_ROTATION_VECTOR`, and `event.accuracy` on every event is that sensor's status. The magnetometer's own status is unreachable on this path without an additive registration, which the dispatch puts out of scope. (That the fusion's status tracks the magnetometer's calibration is platform behaviour I infer, not something this code establishes — the fusion has no other yaw reference.)

**On the fallback path** (no rotation-vector sensor), two sensors are registered on the same listener (`:67-68`), so both statuses arrive. Proposal: the status that gates the heading is the **magnetometer's** (`sensor.type == TYPE_MAGNETIC_FIELD`); the accelerometer's is ignored for this purpose. Reason: the distortion this feature is about is magnetic, the accelerometer's status is about gravity estimation, and gating on either-is-low would fire on a signal unrelated to heading trust. Stated as a proposal because the dispatch asked which; this is the one that matches the feature's cause.

### 1.4 Whether the smoother resets on recovery — the trade-off, not a pick

Today `HeadingSmoother` resets on `NoSensor` and `NeedsFix` (`ui/map/TrueHeading.kt:67, :71`) and is otherwise fed every reading (`:74`). Three shapes for the unreliable state, each with a cost:

- **Reset on recovery.** The first good reading is shown as-is; the smoother re-seeds from it. Honest and immediate. Cost: a visible snap of up to the distortion's size (tens of degrees) at the moment of recovery — the reading *was* wrong, so the snap is the truth arriving, but it reads as a jump.
- **Keep running through the unreliable state, no reset.** At alpha 0.3 the distorted history decays by 0.7 per reading: 17 % remains after five readings, 3 % after ten. At `SENSOR_DELAY_UI` (about 16 Hz) that is under a second. Cost: for that half-second to second after recovery the heading is a blend of distorted and true — the "wrong reading that looks healthy" the dispatch names — short, but real, and exactly when the user is looking because the message just cleared.
- **Pause while unreliable** (do not feed distorted readings), resume without reset. The history at recovery is the pre-distortion heading. Cost: the user may have turned during the unreliable stretch, so the resumed smoother drags a *stale* heading into the new readings for the same half-second to second.

The arithmetic says the window is short in every option; the difference is whether the first second after recovery shows a snap, a blend of wrong-and-right, or a blend of old-and-right. Reset is the only one of the three that never shows a heading the sensor did not just report. Owner's call.

---

## ITEM 2 — a fourth state, and where it ranks

**The type today** (`ui/map/TrueHeading.kt:17-30`): `sealed interface TrueHeadingReading { Available(degrees); NoSensor; NeedsFix }`. The fourth value would be `Unreliable` (name to taste), carrying what the readout needs. Both `when`s — strip (`AvailabilityScreen.kt:4247-4251`) and HUD (`NavigationHud.kt:319-323`) — are exhaustive over the sealed type, so the build breaks until both handle it. No `else` will be added.

**Precedence, as decided by the dispatch:** lost fix, then unreliable compass, then approach threshold; no-sensor unchanged. Where it lands in code: the needle's gate today is `if (headingDegrees != null && freshness != FixFreshness.LOST && !approaching)` (`NavigationHud.kt:348`) and the target text `when` is `LOST → "Target"; approaching → ""; heading → "Turn…"; else → "Bearing…"` (`:349-356`). The unreliable term goes between `LOST` and `approaching` in the `when`, and a comment above it states the order and the reason (position failure is more fundamental; the remedies differ; approach last because it is the only one of the three that is not a failure). The `if` gains the term; the `when` gains a branch in that position.

### What the strip shows — propose and stop

**"Compass unreliable"** in the heading slot — the slot that shows "Compass unavailable" today (`AvailabilityScreen.kt:4249`), same `labelMedium`, one line, no new height. Two words that parallel the existing two, so the vocabulary becomes: a heading · "Compass unavailable" · "Compass unreliable" · "—" · "Location services unavailable". One new string, no new surface, and the strip's icon stops rotating (as it does for `NoSensor`, `:4208`) rather than pointing somewhere with the number withheld. Elevation and coordinates beside it are untouched — they are GPS, and the dispatch's rule not to conflate applies to the strip's layout as much as the code.

### Strip and HUD — the same, or not

- **The strip is ambient.** Nobody steers by it; it is glanced at. Replacing the number with the message is the whole of what it can do, and it costs nothing.
- **The HUD's needle is acted on.** The message alone is not enough there: a needle still drawn beside "Compass unreliable" is a needle someone will follow anyway. So the HUD does more — it suppresses — and the message is the explanation of the suppression, not the treatment.

Proposal: **identical semantics, different consequences** — both surfaces show "Compass unreliable" in their heading slot; the HUD additionally stops drawing the two arrows. That is one state with one meaning; the surfaces differ only in what they were already doing with the heading. The alternative (strip keeps showing the number, greyed) would be the strip claiming something the HUD refuses, on the same screen. Owner decides.

### What the needle does — the precedent, quoted, and whether I match it

The approach suppression: `val targetArrowDegrees = if (headingDegrees != null && freshness != FixFreshness.LOST && !approaching) relativeBearingDegrees(bearing, headingDegrees) else null` (`:348`); the icon then draws dimmed — `tint = if (readout.targetArrowDegrees != null) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)` (`:196`) — with no rotation, and the target text is `""` (`:353`). **Proposal: match it** — needle null, dimmed static icon, empty target text; and the **north** arrow likewise unrotated, since it is the same untrustworthy heading. Not "stay with a warning": a drawn needle is followed regardless of the label beside it. Not "grey but rotated": that is the approach precedent's rejected form.

**One decision the dispatch does not make, flagged:** the `NoSensor` case falls back to the absolute bearing as text, "Bearing 0° N" (`:355`), because without any heading the bearing is still true. An unreliable heading is the same situation for the bearing (it does not depend on heading), so the same fallback is *available*. The owner ruled for the approach case that "an absolute bearing you cannot orient to is a number without a use"; that reasoning applies here too, and matching the approach precedent means `""`. But it leaves `NoSensor` as the one state that still shows a bearing text. Whether `Unreliable` matches `NoSensor` (bearing text) or `approaching` (nothing) is the owner's; I lean to the approach precedent for the reason above and will not pick.

---

## ITEM 3 — what it says: propose and stop

**Label, both surfaces:** **"Compass unreliable."** Says the heading cannot be trusted right now, names no cause, fits the slot, parallels "Compass unavailable".

**If a remedy line is wanted anywhere** (the dispatch's subtlety: distortion versus calibration have different remedies and the status cannot tell them apart), the only honest form names both without ranking them: **"Move away from metal, or move the phone in a figure of eight."** It asserts nothing about which applies. Where it could live without a modal: nowhere on the strip (one line, already full), and on the HUD only in the status line, which is the GPS-freshness slot — putting compass copy there is the conflation the dispatch forbids in code, applied to copy. So the remedy line has no natural home on the map today; the settings tab is the one place a static sentence fits (`NightModeMapsSection`/`DistanceUnitSection` shape, `AvailabilityScreen.kt` ~`:2470-2530`). Propose: the label only, now; the remedy line queued with a surface decision. Owner's call.

---

## Decisions this dispatch does not make — flagged, not picked

1. **Hysteresis / debounce.** `values[4]` arrives at ~16 Hz and will cross 15° both ways in jitter near the boundary; the approach threshold has no hysteresis, but it gates on distance, which moves slowly. A state that flickers between "Compass unreliable" and a heading several times a second is its own kind of broken. Options: none (match the approach precedent exactly), a few consecutive readings to enter/leave, or a band (enter above 15°, leave below 12°). Not listed in the dispatch; needs an answer before the test literals are fixed.
2. **The `CompassProvider` shape.** `Flow<Float?>` becomes a flow of a small owned type (heading + uncertainty-in-degrees? + status), `null` still meaning no sensor; `FakeCompassProvider` and the three production readers change. Contained, but it is the interface the pulse noted has no place for a quality signal, and its shape is a design choice.
3. **`Unreliable` with `NoSensor`-style bearing text versus approach-style nothing** (Item 2 above).
4. **Smoother behaviour** (Item 1.4).
5. **Fallback path's gating sensor** — proposed magnetometer (Item 1.3); confirm.

## Tests planned (for after the answers)

`AndroidCompassProvider` gets its first tests, through `ShadowSensorManager`: register a rotation-vector sensor; deliver an event with `values[4] = 0.35f` (20.05°, above 15°) → unreliable emitted; `values[4] = 0.17f` (9.74°) → heading emitted as reliable; `values[4] = 0f` with `event.accuracy = SENSOR_STATUS_ACCURACY_LOW` → unreliable **via the fallback**, asserted by the emitted reading naming the status path (direct, not by absence of effect); `values[4] = 0f` with `accuracy = HIGH` → reliable; a 4-element `values` array with `accuracy = UNRELIABLE` → unreliable via fallback; `listener.onAccuracyChanged(rotationSensor, UNRELIABLE)` from `getListeners()` → unreliable on its own. Readout: precedence pairs — LOST + unreliable → "Target"/"No fix for…" (lost wins), unreliable + approaching → unreliable's rendering; recovery — unreliable then good → `Available`. Strip and HUD screen tests for the message in both, needle absent in the HUD. Each new test run with the behaviour reverted. **Left untested by design**: the rest of `AndroidCompassProvider` (the fallback path's heading arithmetic, `combinedHeading`), per the dispatch's out-of-scope note — reported now so it is not a surprise later.

## Standing rules

Baseline 1134 / 24 confirmed on this tree at the end of the last pulse; skip set identical to the allowlist. Re-run after building.

## Required disclosure (pre-build)

**Confirmed:** every citation; the single-sensor registration on the rotation-vector path; the exhaustive `when`s; the approach suppression's exact form; the Robolectric factory's second argument (bytecode). **Inferred:** calibrated devices' typical `values[4]` (a few degrees) and distorted values (tens); that zero is what unpopulating implementations leave; that the fusion's status follows the magnetometer; ~16 Hz at `SENSOR_DELAY_UI`. **Could not determine:** whether the owner's device populates `values[4]`; everything on the device list. **Premises in this dispatch that were wrong:** none in the dispatch. One in the pulse it relies on, corrected above (the shadow factory's second argument). **Decided:** nothing built; every proposal above waits on the owner's answer, and five unlisted decisions are flagged.
