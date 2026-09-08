# Findings — `ForagerFix` walk, 2026-09-07

**Source:** single walk, owner's device, build from current `main` with per-fix instrument logging. 1022 log lines → **344 unique fixes** over a 4.8-minute span. GPS cadence 1.0 s, dead steady.

**Method note:** the log contains each fix **three times**. Dedupe on (time, provider, accuracy, speed) before any counting. 339 of 344 fixes appear in triplicate; 5 appear once. All figures below are on deduplicated fixes.

---

## 1. The provider/speed correlation is CONFIRMED

| provider | hasSpeed | fixes |
|---|---|---|
| gps | true | 289 |
| network | false | 55 |

**Perfect separation, 344/344, zero exceptions.**

This closes the item recorded as UNCONFIRMED in the return-estimate dispatch. Amend that dispatch: the correlation is now established on 344 fixes from one device. Still one device — it is not yet established across hardware, and the beta remains the place that settles that.

## 2. The timestamp discriminator holds, independently

| provider | sub-second millis |
|---|---|
| gps | 0 / 289 |
| network | 55 / 55 |

Fifth dataset, same perfect separation. The timestamp filter shipped in PR #73 is confirmed again on fresh data.

**New and worth acting on:** `hasSpeed` and the sub-second timestamp agree on all 344 fixes. They are two independent readings of the same underlying fact, and they never disagree. That gives a cross-check the filter did not previously have — if the two ever diverge on a real track, something interesting is happening and it is worth logging. Not a reason to change the filter; the timestamp rule is already built and already at the right seam.

## 3. GPS accuracy on this device is a constant — this is the significant finding

All 289 GPS fixes report accuracy **3.7900925**. Not clustered. Identical to seven decimal places, 289 times.

That is not a measurement. The device is reporting a fixed placeholder for horizontal accuracy on the GPS path.

Consequences:

- **The 50 m live-fix gate (`acceptLiveFix`) never rejects a GPS fix on this device.** It is not doing the work it was built to do. It still does real work on network fixes (accuracy 12.5–71.7, genuinely varying), so it stays — but its coverage is narrower than believed.
- **The honest-precision HUD formatter** ("within 16 ft" inside the error circle) is drawing its circle from a constant. The displayed precision is not tracking real fix quality.
- **Any future uncertainty-calibration work** — the "truth inside the 95% circle 93–97%" idea harvested from the fusion plan — cannot use this device's GPS accuracy field at all. It has no signal in it.

This needs confirming on other hardware before it is treated as general. It may be a Samsung behaviour, a chipset behaviour, or something about this build. **This is now a first-class beta question**, and it deserves a line in the beta report template.

## 4. The 2 mph default is validated almost exactly

Average speed across fixes at or above the 0.5 m/s floor: **0.890 m/s = 1.99 mph**.

The default was chosen as a deliberately slow 2 mph, argued from foraging behaviour rather than data. The first real walk lands on 1.99 mph. Leave the constant where it is and add a line to its comment recording this measurement.

Caveat worth keeping: one walk, one walker. The agreement is striking but it is not yet a calibration.

## 5. The moving floor is insensitive within its band

| floor | fixes kept | avg speed of kept |
|---|---|---|
| 0.3 m/s | 217 / 289 (75%) | 0.88 m/s |
| **0.5 m/s** | **211 / 289 (73%)** | **0.89 m/s** |
| 0.7 m/s | 194 / 289 (67%) | 0.91 m/s |
| 1.0 m/s | 40 / 289 (14%) | 1.10 m/s |

The 0.3–0.7 band that was recorded as "considered" barely moves the answer — six fixes and 0.01 m/s between 0.3 and 0.5. The choice inside the band does not matter, which is the best possible outcome for a constant argued rather than measured.

1.0 m/s would have been badly wrong: it discards 86% of the walk. Worth recording as the boundary of the safe range.

The speed histogram is cleanly **bimodal** — a cluster at 0.0 and a cluster around 1.0, with a trough between. The floor sits in the trough. That is why it is insensitive.

## 6. The platform gives a clean stopped signal

**Exactly 69 fixes have `speed == 0.0`, and exactly 69 have `hasBearing=false`.** The same 69. When the walker is stationary the platform zeroes speed and drops bearing together.

This is a cleaner stop indicator than thresholding speed, and it is free. Not a reason to change the floor — the floor also has to catch slow drift, which this does not — but worth knowing it exists.

`speedAccuracy` is also bimodal: 0.07 on 63 fixes, 0.72 on 199. The low cluster is close in size to the stopped set. Plausibly the platform is confident about zero and less confident about a moving value. **Observed, not explained** — do not build on this.

## 7. The five-minute bar is harder to reach than it looks

- Wall-clock span: **4.8 min**
- Accumulated moving time at the 0.5 floor: **3.5 min**

A five-minute walk produced three and a half minutes of moving time. At this stop ratio the five-minute bar needs roughly **seven minutes of wall clock** — and this was a brisk test walk, not a forager stopping to examine finds. A real trip will take considerably longer to qualify.

This is not an argument to lower the bar. It is an argument that **the default speed will be in use far more often than expected**, which raises the stakes on section 4 being right. Record it; do not change the ruling.

Stop/move structure, for the record: the walk broke into 18 runs, the longest moving stretch 73 s, the longest stop 28 s. Stops are frequent and short. That matches the "stopping is normal forager conduct" reasoning behind the no-stop-allowance ruling.

## 8. Duplicate listeners quantified

Every fix logged **three times**, from a single PID. This is the duplicate-location-listener item that was already queued, now with a number on it: **three listeners, not four**, during this recording.

Three listeners is three times the callback work and plausibly a real battery cost. It remains queued rather than urgent, and the standing position holds: **measure battery before optimising**. But the multiplier is no longer a guess.

Note for whoever picks it up: the dedupe rule at the top of this document has to survive, or any future log analysis silently triples its sample size.

---

## Actions

1. **Amend the return-estimate dispatch**: correlation confirmed, 344/344, one device. Remove the instruction not to build on it, but keep the note that it is single-hardware.
2. **Record the accuracy constant at `acceptLiveFix`** and at the HUD formatter — both are relying on a field that carries no signal on this device.
3. **Add the accuracy question to the beta report template.** It is the highest-value thing the beta can now answer.
4. **Add the 1.99 mph measurement** to the default-speed constant's comment.
5. **Add the 1.0 m/s boundary** to the moving-floor constant's comment.
6. Leave the floor, the five-minute bar, and the no-stop-allowance ruling unchanged.

---

## Coder's note (added when filed, on `8eacc91`)

**Provenance.** The planner's text above is the owner's upload, verbatim. The raw log it analyses
(`6c858cb2-fixlog.txt`, 1023 lines, UTF-16 with a byte-order mark — decode it as UTF-16 before
grepping it, or `grep` finds nothing) is not committed: it carries the fix timestamps of a walk at
a place the owner did not describe, and the repo's own rule for track files applies (`*.gpx` is
ignored for the same reason). Every figure below was recomputed from that file, not copied from
the text above.

**Every number reproduces.** From 1022 `ForagerFix` lines, one PID (3467), deduplicated on
`(time, provider, acc, speed)` as the method note says: 344 unique fixes, 339 seen three times and
5 once. Provider/`hasSpeed`: gps→true 289, network→false 55, no exceptions. Provider/sub-second
millis: gps 0/289, network 55/55. `hasSpeed` and `time % 1000 == 0` agree on 344/344. GPS
accuracy: one value, `3.7900925`, 289 times; network accuracy 12.481–71.706 across 54 distinct
values. GPS cadence: 288 of 288 consecutive gaps are exactly 1000 ms. Wall clock 4.8 min
(1788801910000 → 1788802198000). Floor table, kept / average / mph: 0.3 → 217, 0.877, 1.96;
0.5 → 211, 0.890, 1.99; 0.7 → 194, 0.912, 2.04; 1.0 → 40, 1.099, 2.46. `speed == 0.0`: 69 fixes;
`hasBearing=false`: 69 fixes; the same 69 timestamps. Moving time at the 0.5 floor, counting each
kept GPS fix as its one-second cadence: 211 s = 3.5 min. Runs at the 0.5 floor: 18, longest
moving 73 s, longest stop 28 s. Speed histogram at 0.1 m/s bins: 69 at 0.0, three fixes across
0.2–0.3, then 0.4–0.6 thin (11), 0.7–1.1 the body (200), a tail to 1.6 — bimodal with the trough
where the planner put it.

**Two things the log says that the findings do not take up.**

1. **Doppler speed accuracy on moving fixes is 0.72 m/s, not under 0.5.** The pre-build report
   (`2026-09-07-return-estimate-prebuild-report.md`, §2.1) set the bar for building the pace on
   Doppler speed as "`hasSpeed() == true` with an accuracy under about half a metre per second";
   ruling #9 then said "if Doppler speed is populated, build on it". Populated it is; the
   per-fix accuracy is 0.666–0.74 m/s on every moving fix (mean 0.723 over the 211 kept), against
   a measured walking speed of 0.89 m/s. Two readings of that, for the owner:
   - **Per fix it is useless** — a one-sigma band (the platform documents the figure at the 68 %
     confidence level; SDK documentation, not this repo) of ±0.72 on 0.89 m/s says a single fix
     could be anywhere from stopped to twice walking pace.
   - **Averaged, it is fine, if the errors are independent.** 211 fixes at 0.72 give a standard
     error of the mean of about 0.05 m/s (0.72 / √211), which is the figure Proposal 2 wanted from
     Doppler and would get from 60 fixes at 0.3. Whether consecutive 1 Hz Doppler errors on one
     receiver are independent is not something this log can show; if they are correlated over a
     few seconds the effective sample is smaller and the error larger, still well under the
     0.5-versus-0.7 floor band's own width.
   - **The clean part of the signal is the stop/move split, not the magnitude.** 68 of the 69
     stopped fixes carry a speed accuracy under 0.1 (0.07, 0.06, once 0.01); every moving fix
     carries 0.666 or more; the one exception is a stopped fix at 0.72. So §6's "plausibly the
     platform is confident about zero" is exactly what the data shows — the low cluster *is* the
     stopped set, not merely close to it in size. Still observed, not explained; still not built
     on.
   The pace design that the pre-build proposed (average Doppler speed over accepted points, gated
   on its own accuracy) survives on the averaged reading and fails on the per-fix one, and which
   reading governs is the owner's ruling to make, not mine. **Noted for the Items 1–3 dispatch;
   nothing here changes what this file's Actions ask for.**

   **Owner's ruling (same day): the averaged reading governs, but not for the reason offered.**
   The standard-error argument assumes the errors are independent across fixes; consecutive
   1 Hz Doppler fixes share a receiver, a satellite geometry and a multipath environment, so they
   are correlated and √N overstates the benefit by an unknown factor — **0.05 m/s is a floor on
   the achievable error, not an estimate of it.** The stronger reason is simpler: the model never
   consumes a per-fix speed. It consumes one average over five minutes of moving time, so a bar
   written for per-fix use was aimed at something the design does not do. **The bar is retired,
   not recorded as failed.** Two consequences for the build: (a) **do not compute a confidence
   interval from `speedAccuracy`** — 199 fixes at 0.72, the rest of the moving cluster 0.67–0.81,
   stopped fixes at 0.01–0.07 is a two-state flag, not a per-fix measurement, and error
   propagation on it is unjustified, the same smell as the constant accuracy field; (b) **validate
   the average against point differencing instead** — compute both over the same window and
   compare; the differencing path has to exist anyway as the fallback, so the check costs almost
   nothing and is a real check against ground truth, and a material divergence on a track is
   worth knowing about.

2. **Four readers of the accuracy field, not two.** §3 names the live-fix gate and the HUD
   formatter. On the same constant: `isApproaching` (`domain/NavigationReadout.kt:29`) fires at
   twice the fix's accuracy, so on this device "Approaching" is a fixed 7.58 m circle, and
   `LocationSampler.shouldAccept` (`domain/LocationSampler.kt:24-25`) compares the fix against the
   recording mode's ceiling (30 / 50 / 100 m), which a GPS fix on this device never exceeds
   either. Both keep doing real work on network fixes, as §3 says of the gate. Action 2 asked for
   the note at two sites and that is where it went; these two are listed here so the next reader
   of either knows, and are not annotated in code by this change. **Owner's ruling (same day):
   annotate all four, and treat `isApproaching` as the serious one, noted separately — the gate
   and the formatters show or keep a wrong number; `isApproaching` makes a decision from a field
   with no signal in it.** Done in the follow-up commit on this branch: notes on `isApproaching`
   (`domain/NavigationReadout.kt`, with the ranking stated) and `LocationSampler.shouldAccept`
   (`domain/LocationSampler.kt`); the gate's own list re-ordered to carry the ranking.

**One correction to the log's own format, for whoever parses it next:** a network fix logs
`speed=null` and `speedAccuracy=null` (the tracker prints the value only when the platform's
`has*()` says it is meaningful, `location/AndroidLocationTracker.kt:57-59`), so a parser that
expects a number in every field rejects all 55 network lines and silently reports a 289-fix
walk with perfect correlation. That is how my first pass failed, and it would have "confirmed"
§1 on the wrong sample.

### Where the six Actions landed (this change, branch `claude/new-session-b7z9bg`)

| Action | Where |
|---|---|
| 1. Amend the return-estimate record | `2026-09-07-return-estimate-prebuild-report.md`: a "Walk result" addendum after the owner-acceptance section; the "Inferred" line, the "could not determine" bullet and ruling #9 each carry a pointer to it. The single-hardware note is kept in all four places. |
| 2. Record the accuracy constant at `acceptLiveFix` and the HUD formatter | `domain/LiveFixGate.kt` (a new doc section on what the field carries on the owner's device) and `domain/model/DistanceUnit.kt` (a paragraph on `formatDistanceWithAccuracy`). Widened by the owner's ruling to all four readers: `domain/NavigationReadout.kt` (`isApproaching`, the decision-maker, noted on its own) and `domain/LocationSampler.kt` (`shouldAccept`). Comments only; no behaviour changed, no test changed. |
| 3. The accuracy question in the beta template | `docs/beta/trip-report.md`, LOCATION block, gated on having used the arrow screen — the only surface where a tester can see the field, as "within N ft". `docs/beta/README.md` records why it asks, adds it to the never-cut set, and updates the line count. |
| 4. The 1.99 mph measurement on the default-speed constant | The constant does not exist yet (Items 1–3 unbuilt). Recorded on the pre-build report's Proposal 2, as the text the constant's comment must carry. |
| 5. The 1.0 m/s boundary on the moving-floor constant | Same: recorded on ruling #4 in the pre-build report, as the text the constant's comment must carry. |
| 6. Floor, five-minute bar, no-stop allowance unchanged | Nothing touched. |

**Not in scope, not built:** the speed columns (migration 14→15), the path home, the pace — the
Items 1–3 dispatch. The three-listener finding (§8) stays queued with the duplicate-listener item;
the dedupe rule at the top of this file is the thing to carry into any later log analysis.

**The parser hazard is now a CLAUDE.md entry** (Testing), by the owner's ruling, as the third
instance of one family: a check that passes because it never saw the data that could fail it —
the legacy-miles test that passed under both branches, the revert runner printing stale results,
and this parser confirming a correlation on the sample that excluded every disconfirming case.

