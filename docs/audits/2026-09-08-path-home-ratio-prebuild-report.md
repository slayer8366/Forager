# Path-home ratio — report before building (nothing built)

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Date:** 2026-09-08. **Dispatch:** "Path-home ratio: report before building" (planner's, dated
2026-09-07). **Type:** stop-and-ask. No production code, no test, no constant was changed. Companion
script: `2026-09-08-path-home-ratio-discriminators.py` (synthetic fixtures, see §C.0).

## Read this first: the code under report is not on this branch

The dispatch's three commits — `f70d3a6` (Item 3, schema 14→15), `873e569` (Items 1–2, `pathHome`
/ `movingPace` / `returnWalkingTime`), `0d02e4c` (hysteresis restored) — exist only on
`origin/claude/new-session-b7z9bg`, head `c914a30`, which is **PR #77, open against `main`**
(17 commits ahead of `main`, `main` is an ancestor). `main` and this session's designated branch
(`claude/new-session-pb8ynb`, cut at `8eacc91` = `main`) have `ForagerDatabase.version = 14`, no
`PathHome.kt`, no `ReturnWalkingTime.kt`, no `MovingPace.kt`, and nothing matching `hopBand`.

Every file reference below is to `claude/new-session-b7z9bg` at `c914a30`, read from a detached
worktree. This report is committed on the designated branch because the session's instructions
require it; it describes code the designated branch does not carry. **CLAUDE.md's rule applies
(Known pitfalls, "verify your base branch"): the dispatch and the session default disagree on the
branch, so the build that follows this report must name its base explicitly.** Building the
discriminator on a branch cut from `main` would re-declare schema 15 and re-create the collision
that document records for PR #26.

## A. Where the numbers live

### A.1 `pathHome` — the distance source, quoted

`app/src/main/java/com/zynergylabs/forager/app/domain/PathHome.kt:81-104`:

```kotlin
fun pathHome(track: Track, current: LatLng, origin: Waypoint?, previousHopBand: HopBand = HopBand.NONE): PathHome? {
    val points = track.points
    if (points.isEmpty()) return null
    var trackMeters = 0.0
    for (i in 1 until points.size) {
        trackMeters += GeoDistance.metersBetween(LatLng(points[i - 1].lat, points[i - 1].lng), LatLng(points[i].lat, points[i].lng))
    }
    val mostRecent = points.last()
    val hopMeters = GeoDistance.metersBetween(current, LatLng(mostRecent.lat, mostRecent.lng))
    val first = points.first()
    val originLegMeters = origin?.let { GeoDistance.metersBetween(LatLng(first.lat, first.lng), LatLng(it.lat, it.lng)) }
    return PathHome(trackMeters, hopMeters, nextHopBand(previousHopBand, hopMeters), originLegMeters, points.size)
}
```

The distance is `PathHome.totalMeters` (`:146`): `trackMeters + hopCountedMeters + (originLegMeters ?: 0.0)`
— the haversine sum over **every** consecutive pair of `Track.points`, first to last, plus the hop
from the live fix to the most recent stored point (band-gated, §A.3), plus the leg from the first
stored point to the origin waypoint. `Track.points` is the repository read seam's list (network
fixes already excluded — `Track.kt:29-35`).

**One property of this sum governs everything below and the dispatch does not name it: it never
decreases during a recording.** Points only append (`PathHome.kt:75-76` says so), so `trackMeters`
is monotone non-decreasing over the trip, and `totalMeters` can fall only by the hop's ≤ 50 m and
the band's 5 m. Track A's 733 m is the 22-minute case; the same arithmetic on the dispatch's
case-D walker — 3.4 km out, then retracing to the car — reads **6.8 km, about 127 minutes, while
they are standing beside it** (§C.3, scenario D). The patch problem and the return-leg problem are
the same problem: the sum counts ground the walker has already walked back over.

### A.2 `returnWalkingTime` — the consumer, and what the walker sees

`ReturnWalkingTime.kt:57-90`. Line 66 calls `pathHome`, line 67 `movingPace(track.points)`, line 87
divides: `walkingMillis = (path.totalMeters / pace.speedMetersPerSecond * 1_000.0).roundToLong()`.
It returns one `Estimate` (path, pace, `walkingMillis`, degrade reasons; `isAtLeast` when any
reason is set) or a `Withheld`.

**It has no caller.** `grep` over `app/src/main` for `returnWalkingTime`, `PathHome`, `MovingPace`,
`HopBand` finds only the three domain files and their tests. The function's own doc says so
(`ReturnWalkingTime.kt:20-22`: "No surface exists yet, on purpose … the alert brings the
surface"), and the Items 1–3 completion report's "What the next dispatch inherits" describes the
intended caller: wired to the ViewModel's 15 s track poll (`TrackRecordingViewModel.kt:274-291`,
`POLL_INTERVAL_MILLIS = 15_000L` at `:492`), `FixFreshness` from the HUD's classification,
`HopBand` held across polls. **So today the walker is shown no walking time at all.** The 13.7
minutes in the dispatch is what the model *would* say; nothing displays it.

What the walker *is* shown, while navigating, is the **straight line to the origin**, one number:
`NavigationHud.kt:351-358` — `distanceMeters = GeoDistance.metersBetween(here, there)` where
`there` is the origin waypoint, formatted by `formatDistanceWithAccuracy` ("within 4 m" inside
the fix's accuracy circle, "≈ 10 m" beyond it, `DistanceUnit.kt:116-136`), in the distance slot
(`NavigationHud.kt:242-258`), with "Approaching" once inside twice the accuracy. The compass
strip's former visible return arm is gone: `returnToStartStripText` (`AvailabilityScreen.kt:4330-4338`,
"Return: 270° W · 412 m · +3 m") now feeds only the control pill's `contentDescription`. So on
track A the HUD reads "within 4 m" / "Approaching"; the model, unshown, holds 13.7 minutes. The
two numbers disagree by 115× and nothing on screen yet carries the second one.

### A.3 What the hysteresis bands gate — in my own words

`nextHopBand` (`PathHome.kt:111-122`), constants at `:150-159`: enter COUNTED above 25 m, leave
below 20; enter FAR above 50 m, leave below 45; a hop can cross two bands in one step. The band is
returned as `PathHome.hopBand` and the caller passes it back as `previousHopBand` (the function is
pure; a new recording starts at `NONE`). It gates exactly two things:

1. **Whether the hop is added to the distance at all** — `hopCountedMeters` (`:140`) is 0 in
   `NONE`, otherwise the hop as measured. The hop is the straight line from the live fix to the
   *most recent stored point*: the sampler's lag (5–60 s, 5–30 m by mode) plus flush latency.
   Under 25 m it is inside fix noise and omitted; the band stops 25 m of remaining distance
   (≈ 28 s at 0.89 m/s) appearing and vanishing on successive fixes near the threshold.
2. **Whether the estimate is degraded** — `hopIsFar` (`:143`) → `DegradeReason.FAR_FROM_TRACK`
   (`ReturnWalkingTime.kt:76`), which flips the label to "at least". Beyond 50 m the hop is a
   straight line across ground the track does not cover, so the number is marked short-by-nature.

The bands gate **the last few tens of metres between the walker and the track's end**. They say
nothing about the track sum, and nothing about the origin. **Interaction with a radius-based
option:** the two are different quantities (fix→last point versus fix→origin, or track extent) and
do not share a threshold, so they cannot fight arithmetically. What they share is *state held by
the caller between polls*: any switch with hysteresis becomes a second carried band beside
`HopBand`, and the intended caller has to hold both, reset both at recording start, and keep both
across a `Withheld`. One more thing to get right, not a conflict — provided the new band does not
key on the hop and the hop band does not key on the new quantity. The one genuine overlap: FAR is
*also* the state in which the walker is 50+ m from the recorded track, which a "switch to straight
line" option would treat as evidence for switching. Those two readings of one fact point opposite
ways (FAR says "the straight line is not to be trusted"; the switch says "use it") and a design
must pick one.

### A.4 Straight-line to origin — already computed, twice; not on this path

- `ComputeReturnToStartUseCase` (`ComputeReturnToStartUseCase.kt:8-24`): one haversine plus one
  bearing, from the live fix to the origin waypoint (or the first breadcrumb before the origin
  exists), run **on every live fix** by `TrackRecordingViewModel.returnToStart` (`:447-471`),
  stored in `uiState.returnToStart`, consumed by the off-track window.
- `navigationReadout` (`NavigationHud.kt:351`): recomputed per recomposition, same haversine.

`pathHome` does not compute it, but it already receives `origin` and `current`, so the addition
is one `GeoDistance.metersBetween` call — microseconds, against the ~135–3000 haversines the
track sum already costs per 15 s poll. A bounding box or max-radius-from-origin over the points is
the same order as the sum (`GeoDistance.boundingRegion` at `GeoDistance.kt:156-166` already does
a box and a max-distance pass, though it rounds to whole km for the map camera and is not usable
as-is). None of this is a cost question.

## B. What "origin" is — code, against the capture

**Fixed at the first gated fix; never re-derived.** `TrackRecordingViewModel.beginLocationTracking`
(`:303-326`): for each live `LocationFix.Update`, if the active mode's `LocationSampler.shouldAccept(lastAccepted = null, candidate)`
passes, and `originWaypoint == null` and no creation is in flight, `createOriginWaypoint` (`:329-356`)
persists a waypoint at that fix's lat/lng and points the track at it. The guard is only re-armed on
a *write failure* (`:351-356`). `originWaypoint` is set to `null` only in `startRecording` (`:178`).
There is no refinement, no re-seeding on a better fix, and no restore path in the ViewModel (the
only `ActiveTrack(...)` construction is at `:175`; whether the foreground service survives process
death was not read). The dispatch's capture matches: origin coordinates = first stored point,
because the sampler's first-fix rule (`LocationSampler.kt:29-32`: accuracy ≤ ceiling, then
"always accepted") is the same rule the ViewModel reuses, and the doc at `:293-302` says the
choice was deliberate — "no timeout (owner decision)".

**The only accuracy floor is the recording mode's ceiling.** `TrackRecordingMode.kt:27-29`:
HIGH_ACCURACY 30 m, BALANCED 50 m (the `startRecording` default, `:162`), BATTERY_SAVER 100 m.
`null` accuracy passes. So the origin can legitimately be a 49 m fix in the default mode. The
7.279 m on track A cleared every ceiling with room to spare; it is mediocre only against the
6.4 m it is being asked to measure.

**The origin's accuracy is not persisted.** `Waypoint` (`Waypoint.kt:22-33`) has no accuracy
field; `asStartPoint` (`TrackRecordingViewModel.kt:473-479`) sets `accuracyMeters = null`. The
7.279 m in the dispatch is readable only because the first *track point* happens to be the same
fix. Anything that wants to reason about the origin's error has to go via `track.points.first()`,
which holds only while that coincidence holds (an origin seeded from the live stream while the
service's first point was a different fix would break it silently). Recorded, not a proposal.

**Consequence for the fix options, confirmed by simulation** (§C.0 script, last section): with
both endpoints perturbed by N(0, 7.3 m), the 6.4 m straight line on track A reads anywhere from
3.6 m (5th percentile) to 27 m (95th) — the median draw is 13 m, twice the truth, because
two-dimensional noise biases a near-zero distance upward. Any rule keyed on an absolute
straight-line figure under ~30 m is keyed on noise. The ratio survives it: 733 ÷ 27 is still 27×.

**Related, inferred:** 733 m over 134 legs is 5.47 m per leg, and the raw points are ~5 s apart
(dispatch). HIGH_ACCURACY's floor is 5 m / 5 s. The track was recorded with almost every leg at
the sampler's minimum, so an unknown share of the 733 m is fix jitter meeting the 5 m threshold
rather than ground walked. Not measurable from the exports; noted because it means the numerator
is *also* softer than it looks, though not at a scale that changes any conclusion here.

## C. Discriminator behaviour

### C.0 What the fixtures are — and are not

**The GPX exports are not in the sandbox.** `*.gpx` is gitignored (`.gitignore:17`), the capture
lives with the owner, and no track fixture file exists on either branch. So the tracks below are
**synthetic reconstructions** matched to the dispatch's measured statistics: A = 135 points,
733 m path, 6.4 m straight, 38×48 m box; B = 19 points, 92 m, 3.0 m, 30×25 m. A is a confined
random walk with reflecting walls and legs at the sampler floor; three seeds give 688–724 m and
identical box and straight-line figures, so the statistics are not seed-specific. The other
scenarios (walk-away, out-and-back, loop, patch-100-m-from-the-car, switchback) are constructed.
Every distance is computed by the script's own haversine, not by the code under report.
**Nothing in §C is a device result, and the shape conclusions matter more than the third digit.**

Walking times use the dispatch's 0.89 m/s. "Shown" means what a surface driven by that candidate
would display.

### C.1 On the two patch tracks

| | A (synthetic) | B (synthetic) |
|---|---|---|
| Path / straight / ratio | 724 m / 6.4 m / 113× | 90 m / 3.0 m / 30× |
| Max distance from origin | 57–61 m (origin at a box corner) | 30 m |
| Retrace walking time | 13.6 min | 1.7 min |
| Straight-line walking time | 7 s | 3 s |

**Containment in a 50 m radius of the origin fails on track A itself** whenever the origin sits
at a corner of the 38×48 m box (diagonal 61 m). The dispatch's "never left a 50 m box" and
"radius 50 m from the origin" are different tests; the box test passes A, the radius test may
not, depending on where in the patch the walker started. A containment rule needs to be stated as
track *extent*, not distance from the origin, or it misses the motivating case.

### C.2 Scenario S1: patch A, then walk straight away — where each candidate changes its answer

| path | straight | ratio | ratio > 3 switch (hyst. 3 / 2.5) shows | ratio > 2 switch (2 / 1.7) shows | retrace | straight |
|---|---|---|---|---|---|---|
| 735 m | 17 m | 43 | straight 0.3 min | straight 0.3 min | 13.8 min | 0.3 min |
| 905 m | 187 m | 4.8 | straight 3.5 min | straight 3.5 min | 17.0 min | 3.5 min |
| 1103 m | 385 m | 2.9 | straight 7.2 min | straight 7.2 min | 20.7 min | 7.2 min |
| 1202 m | 484 m | 2.5 | **retrace 22.5 min** | straight 9.1 min | 22.5 min | 9.1 min |
| 1400 m | 682 m | 2.05 | retrace 26.2 min | straight 12.8 min | 26.2 min | 12.8 min |

- **Ratio switch:** the boundary is stable — with 5 m Gaussian noise on the fix the flip moves by
  one checkpoint and the hysteresis holds it — but **at the flip the displayed time jumps by the
  threshold itself**: 7.2 → 22.5 min on one step at threshold 3. This is arithmetic, not a tuning
  problem: a switch between two estimates that differ by factor R, taken at ratio R, is a jump of
  factor R. Hysteresis stops the flicker; it cannot shrink the jump. Lower thresholds make the
  jump smaller and the straight-line mode last longer (threshold 2 is still showing the straight
  line 680 m out).
- **Whole-track containment (50 m):** flipped at 163 m of path — inside the patch, on the
  synthetic A's geometry — and **can never flip back**, because the extent of an appending track
  is monotone. One step over the edge and the estimate goes from seconds to 13.6 minutes for the
  rest of the trip, however much of the patch is re-walked. Stable in the trivial sense (it moves
  once) and knife-edged at the one place it moves, by the full 100×.

### C.3 Scenario D — the walker 3.4 km out, and their return leg

Outbound: 3397 m path, 2998 m straight, ratio 1.13. **Every candidate leaves the outbound walker
on the retrace**: ratio 1.13 is below any threshold anyone would set; the track is not contained
in anything; retrace and network (§C.5) both read the track. That is the easy half of D.

The return leg, over the same ground:

| path | straight home | ratio | ratio > 3 switch shows | retrace shows |
|---|---|---|---|---|
| 3403 m | 2992 m | 1.14 | retrace 63.7 min | 63.7 min |
| 4806 m | 1755 m | 2.7 | retrace 90.0 min | 90.0 min |
| 5205 m | 1403 m | 3.7 | straight 26.3 min | 97.5 min |
| 6401 m | 347 m | 18 | straight 6.5 min | 119.9 min |
| 6794 m | 0 m | ∞ | straight 0 | **127 min** |

The retrace reads 90 minutes with 1.75 km to go and 127 minutes beside the car: the monotone
property of §A.1 on the case the ruling was written for. The ratio switch happens to be right
here, because on an out-and-back the straight line *is* the track — which is exactly what makes
the next scenario decisive.

### C.4 Scenario D on a loop — the disqualifier

A 1 km-radius loop walked from a point on its rim. Three-quarters round, the walker has 4.7 km of
track behind them and is 1.41 km from the car in a straight line across the loop's interior —
ground they have not walked.

| path | straight | ratio | ratio > 3 switch shows | ratio > 2 switch shows | retrace shows |
|---|---|---|---|---|---|
| 3004 m | 1995 m | 1.5 | retrace 56 min | retrace 56 min | 56 min |
| 4005 m | 1816 m | 2.2 | retrace 75 min | **straight 34 min** | 75 min |
| 4704 m | 1420 m | 3.3 | **straight 26.6 min** | straight 26.6 min | 88 min |
| 6003 m | 280 m | 21 | straight 5 min | straight 5 min | 112 min |

**A ratio-threshold switch sends the loop walker across 1.4 km of unwalked ground** — at any
threshold, since the ratio on a loop grows without bound as it closes. By the dispatch's own
criterion in §D this is disqualifying, and no hysteresis or noise argument rescues it: the
boundary is stable and wrong. Containment passes (the loop is never contained). Retrace passes at
the cost of 112 minutes shown 280 m from the car.

Note what the loop and the patch have in common: in both, the walker's straight line home crosses
ground, and the ratio cannot tell whether that ground was walked. The ratio measures *how much
longer the track is than the chord*; the ruling's question is *whether the chord is known ground*.
Those are different questions, and track A only made them look the same because a 48 m box has
no interior worth the name.

### C.5 Anything else found

**A patch 100 m from the car.** Park, walk 100 m in, work the patch A. Path 823 m, straight
105 m, ratio 7.8, max radius 152 m. Retrace says 15.4 minutes; the truth is ~2. Containment (any
radius that still passes A) says retrace. The ratio switch says straight — right here, wrong on
the loop. **None of the three listed options gets this case and the loop both right.** Since the
"park at the road, walk in, work a patch" shape is at least as ordinary as track A, this is the
case a fix has to be judged on, not only A and B.

**Track-network home (a fourth option).** Treat the recorded track as a graph: consecutive points
are joined; additionally, any two points within ε of each other are joined. The path home is the
shortest route through that graph from the most recent point to the first — walked ground only,
with the single exception that a "closure" edge crosses at most ε metres of unwalked ground.
Results with ε = 12 m:

| scenario | retrace | straight | network home |
|---|---|---|---|
| A | 724 m | 6.4 m | 6.4 m |
| A, 100 m from the car | 823 m | 105 m | 105 m |
| S1 at 484 m out | 1202 m | 484 m | 485 m |
| D outbound (3.4 km wiggly) | 3397 m | 2992 m | 2992 m (see below) |
| D return, 350 m from the car | 6401 m | 347 m | 347 m |
| loop, ¾ round | 4704 m | 1420 m | **4704 m** |
| loop, 77 m from closing | 6206 m | 77 m | 6206 m |

It collapses the patch, the patch-100-m case and the return leg, and on the loop it refuses the
chord because no closure exists — the ruling's property, kept by construction rather than by a
threshold. It is continuous (each scan row moves smoothly) except at the moment a closure forms,
where the estimate drops by the length of the loop just closed; on a real loop that moment is
≤ ε from the origin. Insensitive to ε across 8–20 m on A and A100 (identical to 0.1 m).

Its hazard is the one the nearest-point ruling names, at the ε scale: **two track legs within ε of
each other are treated as connected.** The synthetic switchback (two 220 m legs 10 m apart across
a slope) shows it — ε = 8 leaves the retrace at 650 m, ε = 12 joins the legs and reads 228 m,
cutting 10 m of unwalked ground once. Nearest-point projection's error is unbounded (snap onto
any segment, anywhere); this one's is bounded by ε per closure, but on D's own outbound track the
synthetic zig-zag (points 5 m apart sideways) closes at every step and shaves 12 % — every cut
under ε, cumulatively 400 m. Whether "never more than ε of unwalked ground per closure" satisfies
the retrace ruling, or whether ε has to sit *below* fix noise (which makes the closure test itself
noisy), is the owner's question and is why this is reported, not proposed. Cost: an O(n²) pair
scan is ~1 M haversines at 1500 points (tens of ms per 15 s poll, acceptable); a grid hash makes
it O(n). Hysteresis would be needed on the closure test exactly as on the hop.

**Time-since-last-near-origin, max-radius-from-origin, bounding-box extent** were also tried as
discriminators; each is a monotone or near-monotone statistic of the whole trip and fails the
return leg the same way containment does. Not tabulated.

## D. The case that must not break — verdicts

The walker 3 km out on a 3.4 km track, ratio 1.13, outbound:

| candidate | verdict | why |
|---|---|---|
| 1. Show both numbers | passes | the retrace figure is still shown; nothing switches |
| 2. Switch when the whole track fits a small box/radius | passes | never contained; but see §C.2 — a one-way flip with a 100× jump, and a 50 m *radius* can miss track A |
| 3. Walker chooses the mode | passes only by delegation | the wrong choice is available, in failing light, to the person the alert exists to protect |
| Ratio-threshold switch (any threshold) | **fails** | not on this input — on the loop variant (§C.4) it sends the walker across 1.4 km of unwalked ground; the dispatch's "under any input" clause is met |
| Track-network home | passes on a linear track | reads the track (or 12 % less of it on a zig-zag track, each cut ≤ ε); refuses the loop's chord outright |

## E. What the walker sees — what the surface carries today

The HUD (`NavigationHud.kt:187-298`) is two rows over the map: row one is north compass, target
compass, a `weight(1f)` column holding **one bold `titleMedium` distance and one `labelMedium`
status line**, and the exit button; row two is elevation · coordinates. Every text is `maxLines = 1`.
The distance slot currently carries the straight line ("within 4 m", "≈ 350 m", "0.3 mi"); the
status line carries "Approaching", "Last fix 45 s ago", or nothing.

Without redesign the panel can carry **one more short string**: the status line is empty in the
common fresh-fix, not-approaching state, and a walking time is short ("13 min walk", "at least
13 min"). It cannot carry two distances *and* two times: that is four numbers in a slot built for
one, and the owner has already struck one duplicate from this panel ("9 ft · 9 ft", recorded at
`NavigationHud.kt:141` and `:378`). So "show both numbers" as literally stated does not fit; "show the
straight line, as now, plus one walking time" does — which puts the whole question back on which
distance the one time is computed from. **Also to record:** the dispatch's premise that the
walking time is *displayed* is not yet true (§A.2). The surface decision and the discriminator
decision are one dispatch, the alert's, as the completion report already says.

## Recommendation (planner's, for the owner to rule on)

Do not build a ratio-threshold switch; §C.4 disqualifies it. Do not build option 2 as a radius
from the origin; state it as track extent if it is built at all, and expect the one-way 100× jump.
The question the owner has to answer before anything is built is not "which discriminator" but
**whether "at most ε metres of unwalked ground per closure" is inside the retrace ruling or
outside it.** If inside, the track-network home is the only candidate found that handles the
patch, the patch-100-m case, the return leg and the loop with one rule and no threshold on a
noisy quantity; it needs ε chosen against real fix noise on more than one device, and a closure
hysteresis. If outside, the honest position is option 1 reduced to what the panel holds: the
straight line as now, plus a walking time labelled as the retrace, and the alert's margin built
on a number known to be long by up to the whole trip on the way home.

## Out of scope — confirmed untouched

No change to the retrace ruling, `CivilTwilight`, the filter, the moving floor, the five-minute
bar, the governing instrument, or any test. No test run was needed for this report and none was
run; the skip count and allowlist are as PR #77's completion report left them (24 skipped,
allowlist identity) — quoted, not re-measured.

## Required disclosure

### Confirmed

- The branch/PR facts in the opening section (`git branch -r --contains`, `git log main..b7z9bg`,
  GitHub PR list).
- Every quoted line range, read from `c914a30`.
- `returnWalkingTime` has no caller in `app/src/main` (grep, all three type names).
- The origin's creation path, its single-shot guard, and the absence of an accuracy field on
  `Waypoint`.
- The script's outputs, on the synthetic fixtures (re-runnable: `python3 docs/audits/2026-09-08-path-home-ratio-discriminators.py`).
- **Spot-check, 2026-09-08:** none of the fourteen source files cited above changed between
  `c914a30` and PR #77's merged head `8dc10d4` (`git diff --stat` over the cited paths is empty), so
  every line number holds on `main` at `cb16932`. The commits in between touched the build file,
  `.gitignore`, `CLAUDE.md` and audit documents only.

### Inferred

- That track A was recorded in HIGH_ACCURACY mode (5.47 m mean leg against a 5 m / 5 s floor).
- That the origin sits at or near a corner of A's box (the dispatch says its coordinates equal
  the first point and gives the box, not the origin's place in it); §C.1's radius finding holds
  for any corner-ish origin and is stated conditionally.
- That the synthetic tracks' *shape* conclusions transfer to the real ones. The magnitudes
  (jump factor = threshold; containment monotone; loop ratio unbounded) are arithmetic and do not
  depend on the fixtures; the exact flip positions do.

### Could not determine

- The real tracks' behaviour under any candidate — no export was available.
- Whether the foreground service survives process death with the origin pointer intact (not read).
- What ε real fix noise supports; one device reports a constant accuracy, so the field cannot say.

### Premises in the dispatch that were wrong or incomplete

- "What is displayed to the walker" presupposes a displayed walking time; none is (§A.2).
- Option 2's "small radius" and the analysis's "50 m box" are two different tests; one can fail
  the motivating track (§C.1).
- The patch is presented as the case the retrace ruling under-specifies; the return leg of the
  ruling's own linear case is under-specified the same way, by a larger amount (§A.1, §C.3).

### Decided beyond scope

- Adding the loop as a variant of case D and treating it as the disqualifying input. The dispatch
  gave a linear track; the loop is the same walker on a different route and is where the ratio
  fails.
- Adding the patch-100-m case as a required case alongside A and B.
- Reporting a fourth option with numbers rather than as a sentence.
- Committing this report on the designated branch rather than on PR #77's, per the session's
  branch instruction, with the mismatch flagged at the top instead of resolved.
