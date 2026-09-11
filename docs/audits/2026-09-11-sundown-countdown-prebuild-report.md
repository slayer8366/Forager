# Pre-build report: the sundown countdown

**Date:** 2026-09-11
**Phase:** 1 of the listed-features dispatch
**Status:** no code written. Four owner decisions (S1 to S4) are open, and one fork the dispatch
did not anticipate is open with them.
**Base:** `claude/beta-signup-website-g3t91u` at `034726c`

---

## The short version

Three of the four things the Phase 1 spec budgets for are already paid.

1. **No solar algorithm needs choosing or bundling.** Sunset time is a bisection over
   `CivilTwilight.sunAltitudeDegrees`, which is already here and already tested.
2. **No new location listener.** `TrackRecordingViewModel` already holds a gated fix.
3. **No new timer, and therefore no `runTest` stall risk.** A 15 s poll loop already runs while
   recording, and it already has the "call a named function from inside it" pattern.

What is *not* free, and what the spec assumed away: **the recording notification is built once
and never updated.** There is no path to change its text. That is the decision this report exists
to surface.

---

## 1. Getting a time out of an altitude function

`domain/CivilTwilight.kt` computes where the sun *is*:

```
internal fun sunAltitudeDegrees(epochMillis: Long, latitude: Double, longitude: Double): Double
```

Pure, no Android imports, no network, NOAA low-precision equations, accurate to better than a
tenth of a degree for present-day dates. Tested in `CivilTwilightTest` against midsummer,
midwinter and London midsummer midnight.

A countdown needs a *time*, not an altitude. The Phase 1 spec reads that as "use a published
algorithm (NOAA's solar calculator equations) or a pinned library," with a licence question and
an open-source-notices consequence attached. **Neither is needed.** Altitude is monotonic across
a crossing and always defined, so the time the sun passes any threshold is found by bisecting
this function over a bounded window. Forty iterations over a closed-form expression is
microseconds; recomputing it every 15 s costs nothing measurable.

Three things follow, and the third is the one that matters:

- **No new dependency**, so no licence to vet and nothing added to the open-source notices.
- **No second copy of the solar arithmetic**, which CLAUDE.md's rule against duplicated logic
  would otherwise make a live risk: two implementations of the same equations drifting apart.
- **The polar case stops being a special case.** `CivilTwilight`'s own header records *why* it
  chose altitude over crossing times: "It has no polar special case. Above the Arctic circle
  there are stretches of the year with no sunrise and no sunset to compute, and an algorithm
  built on those times has to detect that and branch." A bisection inherits that property for
  free. If no crossing exists in the search window, the search does not find one, and the result
  is "no crossing today" as a *search outcome* rather than a branch someone had to remember to
  write. The spec's `NoSunset` / `NoSunrise` states still exist, but they are reported by the
  search rather than detected by a special case.

This also resolves the tension the last audit flagged: the spec appeared to reverse
`CivilTwilight`'s documented decision. It does not. The decision was about how to *compute*, and
that stands. Only the question being asked changes.

`sunAltitudeDegrees` is `internal`, which is module-visible, so a domain-layer caller reaches it
without widening visibility. Worth noting because the alternative, making it public, would have
been the wrong reflex.

## 2. Where the position comes from

`TrackRecordingViewModel` already runs `locationJob` (`:341`) and holds `lastGatedFix: TrackPoint?`
(`:150`). The recorded track's points carry lat and lng. **No new OS location listener is
required**, which the spec makes a stop-and-ask, and the Data safety draft's two-listener note
stays true.

Failure paths from the spec map onto existing state:

- **No fix yet**: `lastGatedFix` is null, and the countdown reports itself unavailable with the
  reason, rather than computing against a guessed position.
- **Stale fix**: the app already has the vocabulary for this. `LiveFixGate`'s held-fix ageing
  reads "Last fix 45 s ago" and withholds at five minutes. The countdown should say the age of
  the fix it used, not silently use an hour-old one.

## 3. The delivery vehicle already exists, and its limit

`TrackRecordingViewModel.beginPolling` (`:286-301`) runs `while (true) { ... delay(15_000) }`
while recording, and already calls `updatePathHome(track)` from inside it.

**This is the whole reason Phase 1 is cheap.** A countdown recomputed in that loop needs no new
coroutine, no alarm, and no scheduling. Specifically:

- **No exact alarms**, so `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` never come up. The spec
  makes adding either a stop-and-ask; the design never approaches it.
- **No new poll loop**, so CLAUDE.md's stalled-test pitfall does not apply to new code. That
  pitfall cost this project 77 minutes of real time on a spin that `runTest`'s timeout did not
  break. Adding a second unbounded loop would have re-armed it. Any new test still goes through
  `runRecordingTest`, which stops recording in a `finally`.
- **`updatePathHome` is the precedent** for the shape: a named function called from the loop, not
  a conditional threaded into it. `updateSundown(...)` follows it exactly, which keeps CLAUDE.md's
  "new capability is a new path" rule satisfied without inventing a new pattern.

### The fork the spec did not anticipate

`TrackRecordingService` builds its notification **once**, in `buildNotification()` (`:204`), and
posts it through `startForeground` (`:246`). There is **no `notify()` call anywhere in the
service**, so there is no path that updates the notification's text after it is posted, and its
content is two static string resources.

The spec says "the alert runs inside the existing recording foreground service and is cancelled
when recording stops," which reads as though a changing countdown could live there. It cannot,
not without new machinery. Three shapes, in ascending cost:

| | Shape | What it needs | Cost |
|---|---|---|---|
| **A** | Countdown on the recording screen only | `updateSundown` in the existing loop, UI state field, a row in the recording UI | Small. No service change at all. |
| **B** | A, plus discrete notifications at thresholds | A, plus a `notify()` at the lead-time and sunset crossings, on a new channel | Moderate. One new channel, two posts per trip, no continuous update. |
| **C** | A live countdown inside the recording notification | B, plus rebuilding and re-posting the notification every poll, and feeding the service position and time it does not currently have | Largest. A notification re-posted every 15 s for hours, with the battery and notification-churn questions that brings. |

**Recommendation: B.** A alone is invisible to someone walking with the phone in a pocket, which
is the situation the feature exists for. C pays a continuous cost for a number nobody is watching
continuously. B puts the countdown where you look when you look, and taps you on the shoulder
twice.

C is also the only one of the three that needs the service to learn about location, which is a
larger change than the feature warrants.

## 4. Settings

`DataStoreMapPreferencesRepository` is the pattern: `PreferenceDataStoreFactory.create` directly
rather than the singleton delegate (which caches per-process and breaks Robolectric isolation
across `@Test` methods), namespaced keys, and `Result`-returning suspend get/set. An enabled flag
and a lead time follow it. No Room, no migration, so the migration sequence is untouched.

## 5. The stated limit, and where it goes

Sunset is computed for a **clear, flat horizon**. A ridge to the west, a valley, or canopy makes
it dark meaningfully earlier, and the error is not small: under trees, useful light can end half
an hour or more before the almanac sunset.

The spec says the UI states this once and never presents sunset as the time light runs out. That
is right, and it is the same posture as `LiveFixGate`: say what is actually known, and do not
dress a computed number as a guarantee. The countdown is a countdown to sunset, not to darkness,
and the copy should not blur them.

**This is also why the countdown must never say there is enough time.** It cannot know: the
return estimate is not available (see below), and even with one, terrain and light are not the
same question.

## 6. What is still blocked, and is not on this path

`returnWalkingTime` has one occurrence of its call form in `main/`, its own declaration at
`domain/ReturnWalkingTime.kt:57`. **Zero production callers**, deliberately
(`ui/track/TrackRecordingUiState.kt:70`). So Phase 6, "about X minutes back at your pace so far,"
remains blocked on validating that estimate against walked tracks, and nothing in this phase
unblocks it. v1 shows time remaining and nothing about the walk back.

---

## Disclosure

### Confirmed by observation
Every file and line above was read. The bisection claim rests on `sunAltitudeDegrees` being pure,
total and module-visible, all three verified at `domain/CivilTwilight.kt:63`. The
"no notification update path" claim rests on the absence of any `notify(` in
`service/TrackRecordingService.kt`, which was grepped for directly.

### Could not be determined
- Whether a notification re-posted every 15 s for a multi-hour recording is acceptable on real
  hardware. That is a device measurement, and Robolectric will not answer it. It is one reason to
  prefer B over C rather than to find out the hard way.
- What lead time is useful. Nothing in this project supports a figure, and the one input that
  might have (walk-back time) has no production caller.
- Whether any bisection window narrower than 24 h is safe at extreme latitudes. The
  implementation should search a full local day and report no-crossing rather than assume one
  exists.

### Premises in the dispatch that were wrong
1. **"Use a published algorithm or a pinned library. The pre-build report chooses, with the
   library's licence. A bundled library adds to the open-source notices finding."** No library is
   needed; the equations are already here and tested. That whole item is closed at zero cost.
2. **"The alert runs inside the existing recording foreground service."** It can be *triggered*
   from there, but the notification has no update path, so a live countdown in it is the most
   expensive of the three shapes rather than the default one.
3. The last audit's reading that the spec reversed `CivilTwilight`'s design decision. It does
   not; the decision was about computation and it stands.

### Decided beyond scope
Nothing built. The three shapes in §3 are laid out rather than chosen, because the choice changes
what gets built and belongs to the owner.

---

## The four open decisions

| ID | Decision | Recommendation, now grounded in the code |
|---|---|---|
| **S1** | Sunset, civil dusk, or both | **Both, and it costs one parameter.** The bisection takes a threshold, so 0° (sunset) and −6° (civil dusk, already `NIGHT_ALTITUDE_DEGREES`) are the same code called twice. Count down to sunset as the headline; show civil dusk as the second marker, since that is when you actually stop being able to see. |
| **S2** | When alerts are active | **Recording only for v1.** The poll loop exists only while recording. Anything wider needs scheduling the design currently avoids entirely. |
| **S3** | Default lead time | **No recommendation, and I will not invent one.** Nothing in the project supports a figure, and the input that might have (walk-back time) has no production caller. Owner sets the number; the setting is user-editable from day one. |
| **S4** | How many alerts | **Two**, if shape B: one at lead time, one at sunset. Two discrete posts across a trip, which is cheap and is the tap on the shoulder the feature is for. |

Plus the new one this report surfaces:

| **S5** | Screen only, screen plus alerts, or live in the notification | **B: screen plus two alerts.** Reasoning in §3. |
