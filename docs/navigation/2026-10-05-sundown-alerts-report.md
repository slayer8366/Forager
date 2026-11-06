# Sundown alerts from the recording service (dispatch 2026-09-28-516) — report

**Status: built, tests and revert checks run; the S22 step waits for the owner's word. Not merged.
Release gate open: one 30-minute-plus out-and-back walk (below).**

Dispatch: `prompts/preserved/2026-10-04-11.md` (on `records-after-168`, now on `main`). Branch
`sundown-alerts`, cut from `origin/main` at `d4bd00bf` (confirmed unmoved before any citation was
acted on), with `main` at `a9dfdd59` (PR #172, dispatch -510) merged in at `952927ea`, merge not
rebase, on the owner's order.

## What landed

| Commit | What |
|---|---|
| `5ab39110` | This report's first draft: the walk comparison and the verify findings. |
| `3adf2fae` | `SundownWatch`, the three-moment `DecideSundownAlertUseCase`, `AlertKind.HEADS_UP/LEAVE_BY`, `WalkBack`, `SundownAlertDetail`; the service drives the watch; notification text; tests. Not yet compiled at this commit. |
| `06a3c260` | The sunset held by the watch (the countdown always reports the *next* sunset); four test setups corrected. |
| `2da705a0` | The held sunset never jumps a day (the search turns over a fraction of a second early); sunset checked to the second. |
| `8a8e45ec` | The owner's wording with a start-by clock time; the alert carries the leave-by time. |
| `952927ea` | `main` (`a9dfdd59`, -510) merged in. Clean, no conflicts. |
| `75ebb13d` | The last known position (-510's `LastKnownLocationSource`, reused) gives the sunset when nothing is live; the service evaluates once at the start of a recording. |
| this commit | This report and its two index rows. |

## The behaviour as built

A recording starts. `TrackRecordingService` begins `SundownWatch` (held by `AppContainer`, like
`ReturnWatch`), hands it every raw fix, evaluates it once at the start, once at the first fix and
every 15 s, and ends it with the recording or the service. Each evaluation:

1. **Sunset**, from the newest live fix of any kind (GPS or network), or with none, the platform's
   last known position. Held once found: the countdown always reports the *next* sunset, so the
   watch keeps the one it is counting toward and refuses a jump to the next day.
2. **The walk back**, from `returnWalkingTime` over the stored track (through the read seam), the
   origin waypoint, and the newest GPS fix (told apart by `isNetworkProviderFix`). Its freshness is
   `fixFreshness` of its age. No GPS fix, a lost one, or an unreadable track: unknown.
3. **Leave-by** = sunset − darkness margin − walk back (or sunset − margin when unknown).
4. **Heads-up** at leave-by − 30 min, **leave-by**, **sunset**: each once per recording; a later
   one already due spends the earlier ones without sounding. All override a silenced phone.
5. Nothing fires with the alerts turned off, before or after a recording, or after the walker,
   heading back (Return tapped), reaches the start by the HUD's arrival rule (option B).

What the notifications say (the owner's wording, 2026-10-05):

| Walk back | Heads-up and leave-by (shared text; the start-by time tells them apart) |
|---|---|
| Measured | **Sunset at 7:12 PM** / The walk back the way you came is about 1 h 30. To finish it before dark, start by 4:42 PM. |
| Thin ("at least") | **Sunset at 7:12 PM** / The walk back is at least 45 min. To finish it before dark, start by 5:27 PM at the latest. |
| Unknown | **Sunset at 7:12 PM** / The walk back is unknown. |
| Sunset alert | **The sun has set** / Light is going now. (unchanged) |

Channel description: "Tells you when the sun sets and how long the walk back is." The old
"Time to head back" strings are removed. Times follow the phone's 12/24-hour setting.

## The walk comparison (verify item 1)

### How the walks were got

The owner said the walks are on the S22 (not the S26 Ultra the dispatch assumed) and approved
copying the app's database off it. The installed build is debuggable, so `forager.db` and its
`-wal`/`-shm` were copied with `adb exec-out run-as … cat` into a scratch folder outside the
repository (`/tmp/s516`). **No position is in this repository**; only the figures below are.

The estimate was run with the **real** `returnWalkingTime`, not a re-implementation: the domain
files it depends on (`ReturnWalkingTime.kt`, `PathHome.kt`, `MovingPace.kt`, `NetworkProviderFix.kt`,
`NavigationReadout.kt`, `GeoDistance.kt`, `TrackSelfJoin.kt` and five model files, as at
`d4bd00bf`) were compiled outside Gradle with the cached Kotlin 2.2.10 compiler, beside a harness
kept in `/tmp/s516` that rebuilds each track the way the read seam does (network fixes excluded,
`excludedPointCount` carried) and calls the function at a quarter, half, three quarters and all of
the way out. Not a Gradle run (the dispatch's one-build-at-a-time rule concerns Gradle); the
project builds with Kotlin 2.3.10, so the compiler differs by a minor version.

### The sample

- **61 tracks** on the S22. **58** are empty or one point (most from 2026-10-04, one-point or
  zero-point starts). **Two** more are under 3.1 minutes and under 30 m from their start.
- **Three usable walks**, all on 2026-10-03: 23.6, 16.4 and 17.5 minutes; 190–220 points; one
  network fix among them; each has a start marker; each ends 5–9 m from its first point.
- **None has a clear turnaround in the dispatch's sense — none retraces its way out.** Taking the
  point furthest from the start as the turnaround:

| Walk | Way out, along the track | Way back, along the track | Way back's median / 90th-percentile distance from the way out | Time at the far end (within 40 m) |
|---|---|---|---|---|
| A | 339 m, 7.9 min | 989 m, 15.6 min | 60 m / 157 m | 0.9 min |
| B | 351 m, 4.8 min | 892 m, 11.6 min | 35 m / 162 m | 7.4 min |
| C | 305 m, 3.7 min | 955 m, 13.8 min | 81 m / 169 m | 0.8 min |

They are loops: the way back is about three times the way out, and most of it is tens of metres
off it.

### What the estimate said

| Walk | Point on the way out | Straight-line from start | Estimate | Label | Pace used |
|---|---|---|---|---|---|
| A | ¼ / ½ / ¾ / turnaround | 71 / 169 / 231 / 311 m | 1.5 / 3.3 / 4.8 / 6.3 min | at least (all) | default 0.89 m/s (all) |
| B | ¼ / ½ / ¾ / turnaround | 80 / 176 / 227 / 268 m | 1.6 / 3.4 / 4.8 / 6.4 min | at least (all) | default (all) |
| C | ¼ / ½ / ¾ / turnaround | 67 / 148 / 208 / 278 m | 1.3 / 2.8 / 4.3 / 5.7 min | at least (all) | default (all) |

Actual walk back from the turnaround: 15.6, 11.6 and 13.8 min (15.1, 11.0 and 13.3 from leaving
the far end). Every estimate was **"at least"** for the one reason `NO_MEASURED_PACE`: moving time
(intervals whose implied speed is at least 0.5 m/s, `MovingPace.kt:116`) never reached the
five-minute bar (`MovingPace.kt:287`) on these short, slow ways out. The default pace, 0.89 m/s
(3.2 km/h), is slower than the phone's own Doppler speed while moving on these walks (median 1.13,
1.23 and 1.24 m/s), which is the direction the default is meant to err.

### What this can and cannot show

- **Can:** the estimate runs end to end on real S22 recordings, through the read seam's rules,
  without a withheld result, and degrades exactly as designed ("at least", default pace) when the
  way out is short. The default pace is conservative against this walker's measured moving speed.
- **Cannot:** whether the estimate is right for a walk back that retraces the way out, because no
  walk here does. The 6-minute "at least" against 12–16 minutes actual is a different route home,
  not an estimate error, and is no evidence either way. Nothing about measured pace (never
  reached), about walks longer than 25 minutes, or about woods.
- **One reading for the alert copy:** on all three walks the walker went home a longer way than
  they came. A leave-by built on the walk back *along the track* would have been 6–10 minutes
  optimistic for these walks, and "Leaving now gets you back before dark" would have overstated it.
  This is why the measured wording says "the way you came", and why only a measured estimate says
  anything about getting back (the owner's ruling, 2026-10-05).

### The owner's ruling on this gate (2026-10-05)

"Build now, the estimate labelled 'at least' as designed." **Validation moves to before release,
as a release gate, not a build gate:** one out-and-back walk of 30 minutes or more, retracing the
way out, compared like the three above (the estimate at points along the way out against the
actual walk back). This branch must not ship in a release until that comparison is filed.

## Verify items 2–5

2. **Where the alerts live.** Confirmed the recording service and the container-held pattern
   (`TrackRecordingService.kt:127-166`, `:56-64` at `d4bd00bf`). The service had no tick of its
   own (a 30 s flush loop only), so the watch has its own 15 s timer. Track from `getById` (the
   read seam, whose exclusion counts the "at least" rules need), not `ReturnWatch`'s in-memory
   points (unfiltered). The hop band is carried in the watch. The screen's countdown is untouched
   (T3); it could read the watch rather than compute twice.
3. **Three moments, and what survives.** Extended as above. The fired set lives in the container-held
   watch, so it survives the Activity going away; it resets per recording and is not in DataStore,
   since a process death ends the recording too (inferred from `TrackRecordingService.kt:86-117`:
   a sticky restart arrives with no intent and records nothing).
4. **`getAlertsEnabled`** had no reader; the watch reads it every evaluation. A failed read is
   logged and the alerts stay on (the default).
5. **"Arrived"** existed only on the screen (`NavigationHud.kt:442`). Built as the owner's option B.

## Location failures and what fills each gap (RECORD -519)

| What the alerts need | Failure | What fills it |
|---|---|---|
| Sunset position | No GPS (canopy, indoors) | Any network fix (owner-approved exception, below) |
| Sunset position | No live reading at all | The platform's last known position (-510's source) |
| Sunset position | No live reading and no last known position | **Gap:** nothing fires until a position arrives. Stated, not hidden. |
| Walk back | No GPS fix, or one older than 5 min | Unknown; leave-by falls back to sunset − margin; text says unknown |
| Walk back | Track unreadable | Same, and logged |
| Walk back | Thin data (short walk, network-heavy track, stale or far fix) | "At least"; never a promise |
| GPS vs network | A phone whose GPS stamps milliseconds | GPS reads as network; walk back unknown; leave-by = sunset − margin (safe direction) |
| Delivery | POST_NOTIFICATIONS denied | Vibration still issued; logged as partial delivery |
| Delivery | App swiped away | The service still runs the watch (test below) |
| Delivery | Process killed | The recording ends too; nothing to alert for. Not recovered. |
| Battery | Phone running low | **Gap**, as -519 records it; deferred by -520. |

## Evidence

**Tests were written with the code, not run first** (the owner asked that this be disclosed): the
first run came after the production code existed. The revert checks below are the evidence the
tests bite.

Runs (Gradle, each with the owner's go; results read only after the build log showed no compile
errors, and from XML written by that run):

| Run | Result |
|---|---|
| Four classes, first attempt | Did not compile (my edit had deleted `Alert`). No results read. |
| Four classes | 25/31. Five failures: the sunset alert never fired (finding below) and test setups with wrong times. |
| Four classes | 29/32. Sunset lost in a sub-second gap (finding below); a 0.3 s sunset expectation. |
| Four classes, after the wording | Test sources did not compile (a colon in a test name). The XML on disk was the previous run's (timestamp 04:03); not read as results. |
| Four classes | 31/31, fresh XML (07:33). |
| Five classes on the merged tree (adds `AndroidLastKnownLocationSourceTest`) | 37/37. |
| Full unit suite, on `83d9cdd1`'s code (`75ebb13d` plus docs) | 457 classes, 3,719 tests, 0 failed, 24 skipped. This branch adds no `@Ignore` (51 in `app/src/test` here and on `main`); the skip count on `main` itself was not run, so "the same 24" is not confirmed. No compile errors in the log. |

Revert checks: one-line edits, each restored from a copy saved before editing (never from git),
the forward change confirmed present afterwards (`git status`, `cmp`). Domain ones (R1–R5, R8, R9)
compiled and run outside Gradle with the cached Kotlin 2.2.10 compiler and JUnit 4.13.2 (the
project builds with 2.3.10), to spare Gradle slots; a positive control (a revert that cannot
compile) was refused by the runner. Service and text ones (R6, R7, R10) in Gradle.

| Revert | Failed (specific to the edit) |
|---|---|
| R1 walk back ignored in the decision | 4: leave-by moving earlier ("expected [LEAVE_BY] but was []"), heads-up timing, at-least heads-up, start past leave-by ("[HEADS_UP, LEAVE_BY]") |
| R2 held sunset removed | 3, each "[HEADS_UP, LEAVE_BY, SUNSET] but was [HEADS_UP, LEAVE_BY]" |
| R3 alerts-off ignored | 1: "with the alerts turned off nothing fires" |
| R4 arrival ignored | 1: "heading back and arrived at the start, nothing more fires" |
| R5 GPS filter removed | 2: the no-GPS and stale-GPS tests ("expected Unknown but was About(...)") |
| R8 heads-up lead zero | 6, each missing HEADS_UP |
| R9 last known position ignored | 1: "a recording with no live reading … expected [HEADS_UP, LEAVE_BY, SUNSET] but was []" |
| R6 service stops feeding fixes to the watch | 1: the service-alone test, `TrackRecordingServiceSundownTest.kt:97` |
| R7 thin estimate worded as measured | 1: "at least never promises", `SundownNotificationTextTest.kt:71` |
| R10 no evaluation at the start of a recording | 1: the no-live-reading service test, `TrackRecordingServiceSundownTest.kt:134` |

The Gradle runner's own summary counted R6/R7/R10's failures but printed no names (it tested an
XML element's truthiness, false for one with no children); the names above are from each run's
own Gradle log, and the runner was fixed before the full suite.

## Installed on the phones (owner's go, 2026-10-05)

`main` at `e1395fc9` merged in at `bf34fc87` (it includes the `1d3d2bbb` the owner named, PR #173;
after it only PR #171's docs). Index conflicts resolved keeping every row. The five affected classes
on that tree: 37/37. Debug APK `1.0.2763+gbf34fc87` (versionCode 2763), signed with the committed
`app/debug.keystore`, built from a clean tree.

| Phone | Serial | Before | Install | versionName read back |
|---|---|---|---|---|
| S26 Ultra (SM-S948U, the owner's daily phone) | `R5GYC4CYJ3X` | 1.0.2695+gc3ba0f46 | `adb install -r`: Success. First install time unchanged (2026-10-03 20:36), so an update with data kept | 1.0.2763+gbf34fc87 |
| S22 Ultra | `R5CT321008R` | 1.0.2708+geb38bec0 | Not connected at first; installed once the owner reconnected it. `adb install -r`: Success. First install time unchanged (2026-10-02 06:21), so an update with data kept | 1.0.2763+gbf34fc87 |

## The S22 step (needs the owner's word)

Not done. **No way exists today to set the darkness margin on a phone** (`setDarknessMarginMinutes`
has no caller in `main/`; its control is T4). Ways to make the alerts fall within minutes, for the
owner to choose:

- **Real sunset (recommended, no tampering):** start a recording about two hours before sunset with
  the phone silenced, stand or walk briefly; with a short track the walk back is minutes, so the
  heads-up comes about 1 h 30 before sunset, leave-by 30 min later, sunset an hour after that. Long,
  but exactly the shipped path.
- **Write the margin into the app's DataStore over adb** (`run-as`, the debuggable build, app
  stopped): quick, but edits app data by hand.
- A debug-only hook: new code, not in this dispatch.

Then: phone silenced, each alert heard and felt, screenshots of the three notifications.

## Confirmed vs inferred

- **Confirmed by test:** every behaviour in "The behaviour as built", through the watch's own calls
  on a fake clock; delivery with no Activity or ViewModel and from the last known position alone,
  through the real service, container and `AndroidAlertDelivery` under Robolectric; the wording.
- **Confirmed by reading:** the citations in the verify items, at `d4bd00bf`.
- **Inferred, not tested:** that a process death ends the recording so nothing need persist; that
  15 s is fine-grained enough on a phone; that the alarm-usage vibration is felt through a pocket
  (device only, existing coverage gap in `AndroidAlertDelivery.vibrateWith`).

## Could not determine

- Whether the estimate is right for a walk that retraces its way out (no such walk exists; release
  gate).
- What the S22 returns as a last known position indoors (device only; -510's report says the same).
- Robolectric reports no real notification sound or vibration: whether the alerts are heard on a
  silenced phone is the S22 step.

## Premises that were wrong

- **The walks are on the S22**, not the S26 Ultra; **none retraces its way out** (loops).
- **The service had no tick of its own.**
- **The raw stream does not carry the provider** (`AndroidLocationTracker.kt:89-97`); the timestamp
  rule is the only test (the owner chose it).
- **Nothing read a last known position** until -510.
- `getAlertsEnabled`, `DecideSundownAlertUseCase` and `returnWalkingTime` had no production caller.
- **The sunset alert could never have fired** through `DecideSundownAlertUseCase` as it was:
  `ComputeSundownCountdownUseCase` searches for the next sunset from now
  (`ComputeSundownCountdownUseCase.kt:44-50`), so `isPastSunset` is never true, and the search turns
  to tomorrow's a fraction of a second before the sunset it reported a minute earlier (measured:
  from S − 1 ms, the next sunset is S + 86,288 s). Fixed in the watch by holding the sunset; the
  countdown and the screen are unchanged.
- **The planner's example times** (sunset 7:12, walk 1 h 30, start by 5:42) leave out the darkness
  margin; with the default hour the start-by is 4:42. Built as sunset − margin − walk.

## Decided beyond scope

- **Which fixes (owner, 2026-10-04).** Walk-back: GPS only, by `isNetworkProviderFix`, the rule the
  track read and the off-track judge use, so the current fix and the track agree; no provider field
  added to `LocationFix` (carrying the true provider is the quick win's job). **Failure direction,
  against RECORD -519:** on a phone whose GPS stamps milliseconds, GPS fixes read as network, the
  walk back reads "unknown", and the leave-by falls back to sunset − margin. Sunset: any live fix,
  network included, and the last known position — the owner's exception to -510's "never decides
  anything", for the sunset time only ("Sunset moves about 4 s per km, and GPS-only would mean no
  sundown alert at all under canopy or indoors"). With no fresh GPS fix: sunset − margin, walk back
  unknown.
- **Arrival (owner, option B).** While returning, the HUD's `hasArrived` against the origin (or the
  track's first point) silences all three for the rest of the recording, even if the walker sets
  out again. Without Return, only stopping the recording silences them.
- **Order (owner, 2026-10-05):** -510 merged first (done, PR #172); `main` merged into this branch
  (not rebased); the last known position wired with a test that a recording with no live reading
  still gets a sunset time and its alerts. **This branch does not merge before the S22 step and the
  release gate are settled as the owner directs.**
- **Mine, for the owner to overrule:** one notification id for all three (each replaces the last in
  the shade); a recording started after sunset alerts on nothing that night (follows from the
  countdown; suits night forays); the margin and enabled flag read on every evaluation, so a change
  takes effect within 15 s; a failed preference read keeps the alerts on and logs it; durations
  rounded up to the minute; the "at the latest" text is used for the thin case on both alerts.

## Not touched

The location gate and -510's code (only its `LastKnownLocationSource` is called); `ReturnWatch` and
its off-track rule; `RouteHome`; the HUD; the map; `MovingPace`'s and `ReturnWalkingTime`'s rules;
the darkness margin's name and default; settings screens (T4); the countdown row (T3).

## Housekeeping

The S22 database copy and the walk CSVs were kept in `/tmp/s516` on the laptop, outside the
repository, and deleted when this report was finished. Only figures were ever written here.
