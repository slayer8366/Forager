# Resync recording state, §1: the gate, and two findings beside it

**Date:** 2026-09-12
**Dispatch:** "resync recording state on resume (beta fix)"
**Branch:** `claude/beta-signup-website-g3t91u`, which descends from `4957675` (`git merge-base
--is-ancestor` confirms). The dispatch names `main` at `4957675` or later; this session's standing
instruction names this branch. They are compatible rather than in conflict, and it is stated here
rather than assumed.
**Status:** §1 only. No code written.

---

## The gate's answer: points cannot be written to an ended track. §1 does not stop.

### What bounds the collector at `:342`

On `origin/main`, `TrackRecordingViewModel.kt:342` is `locationTracker.fixes.collect { fix ->`,
inside `locationJob = viewModelScope.launch { }` at `:339-341`. (On this branch the same code is at
`:394`; the sundown slices inserted 61 lines above it. The dispatch's citation is correct for
`main`.)

It is bounded by two things, and **neither is the service**:

1. `viewModelScope`, which dies with the ViewModel. `onCleared` (`:582-584`) also cancels both jobs
   explicitly.
2. `locationJob?.cancel()`, which appears in exactly two places: `beginLocationTracking` itself
   (a restart), and **`TrackRecordingViewModel.stopRecording()` at `:253`**.

A notification stop calls `TrackRecordingService.stopRecording()`, never the ViewModel's. So the
bound on that collector is the stale field, exactly as the dispatch suspected.

### Whether any write path to track points can run

**No.** `recordTrackPointsUseCase` has **exactly one caller** in `app/src/main/java`:
`TrackRecordingService.kt:168`, inside `flushPendingPoints`. The ViewModel never appends points. It
builds a `TrackPoint` in the collector (`main:344`) and uses it for three things only: `lastGatedFix`
(in memory), seeding the origin waypoint, and `returnToStart(point)` (UI state).

The service's own stop ordering is also correct: `stopRecording()` (`:142`) cancels `recordingJob`,
then in one coroutine flushes (`:150`), ends the track (`:151`), and calls `stopSelf()` (`:154`).
Points in that final flush precede `endedAtEpochMillis` by construction.

### Whether a late point would be rejected

**No. It would be accepted silently.** `RecordTrackPointsUseCase` guards emptiness and nothing else
(`:14`). `RoomTrackRepository.appendPoints` (`:69-72`) calls `dao.insertPoints` with no check on the
track's ended state. No guard exists anywhere on that path.

**This is the part worth carrying forward.** Track points are safe because **no writer exists after
the service stops**, not because anything would refuse the write. The protection is structural, not
defensive. Any future change that gives the ViewModel or any other class a point-write path
reintroduces the corruption risk with nothing to catch it.

---

## Two findings the dispatch did not ask about

### (a) A *waypoint* can be written to an ended track

The stale collector keeps running, and at `main:356` it calls `createOriginWaypoint(active, point)`
whenever `originWaypoint == null && !originCreationInFlight` and a fix clears the accuracy gate.
That writes a `Waypoint` row **and** calls `trackRepository.setOriginWaypoint(active.trackId, ...)`,
mutating the ended track's own row.

Not a track point, so it does not meet the gate's stated stop condition. It is the same family.

Its precondition is that no fix passed the mode's accuracy gate before the notification stop, so the
track has no origin yet. `PathHome.kt:59-62` already records that an under-canopy track may validly
never acquire an origin, and canopy is this app's primary environment. So this is not a remote case.

### (b) The GPS listener survives a stop the user performed, with no notification left

This is the one that changes the severity, and it is not inferred — this project has already fixed
the identical shape once and wrote down why. `AvailabilityViewModel.kt:141-142` and `:163`:

> the `callbackFlow` was never closed, its `awaitClose { locationManager.removeUpdates(listener) }`
> never ran, and the OS [kept delivering] ... **Cancelling the collection completes the
> `callbackFlow`, which is what actually runs** [the removal].

`locationJob` is cancelled only by `TrackRecordingViewModel.stopRecording()` or `onCleared()`. After
a notification stop with the Activity alive, the ViewModel still holds a live collection on
`LocationTracker.fixes`, so the platform listener stays registered. The foreground service is gone,
so the ongoing notification is gone too.

The result is a registered location listener with no notification showing, after the user pressed
Stop. `pollingJob` (`:310`) survives on the same terms, polling the repository every 15 s.

**This is not a display defect.** The button being wrong is the visible symptom; the app continuing
to hold location after the user stopped it is the cost.

### What this means for the chosen fix, and one limitation of it

It argues **for** §2 rather than against it. §2's constraint that clearing must go through the same
code a normal stop uses is exactly what cancels `locationJob` and releases the listener, so the beta
fix closes the leak as well as the display.

**The limitation, which belongs in §4's deferral record:** the resync runs on resume. A user who
stops from the shade and never returns to the app keeps the listener registered until the Activity
is destroyed. Resume-only resync does not cover that window, and naming it now is cheaper than
discovering it later.

---

## Disclosure

### Confirmed by observation
Every line cited. The dispatch's three carried citations were re-derived rather than trusted and all
three are accurate: `MainActivity.kt:413` is `isRecording = trackUiState.isRecording`,
`TrackRecordingService.kt:142` is `private fun stopRecording()`, and `:184-199` carries the
"the Activity is exactly the thing that may not exist" reasoning verbatim.

The one-caller claim for `recordTrackPointsUseCase` is a grep over `app/src/main/java` returning the
declaration, the container wiring, one doc reference, and one call site.

### Could not be determined
Whether the platform listener the ViewModel holds is the *same* registration the service holds or a
second one, and therefore whether (b) costs a second radio duty cycle or shares one. That needs the
Android `LocationTracker` implementation read, which this pass did not do.

### Premises in the dispatch that were wrong
One, and it is the load-bearing one. §1 supposes the ViewModel "may keep collecting positions and
appending them to a track whose `endedAtEpochMillis` is set." The ViewModel does not append points
and never has; the service is the only writer. The worry was correctly aimed at the stale collector
and wrong about what that collector does. What it actually leaks is a location listener and a
waypoint write, which is why both are recorded above rather than left out for being off-question.

### Decided beyond scope
Nothing. No code written. §2 not begun.
