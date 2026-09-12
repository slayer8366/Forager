# Resync recording state on resume: completion report, and what is deferred

**Date:** 2026-09-12
**Branch:** `claude/recording-state-resync-lifecycle-gate`, cut from
`claude/beta-signup-website-g3t91u` at `5f8f258`, which descends from `main` at `4957675`.
Renamed off the beta-signup branch at the owner's instruction: app-side recording work under a
branch named for a signup website reads as a mistake to anyone who finds it later.
**Preceded by:** [§1 gate report](2026-09-12-recording-state-resync-section-1-gate.md)

---

## What was built

`TrackRecordingViewModel.resyncRecordingState()` re-reads the active track's own row and clears
local recording state when that row says the recording is over. It is driven from
`onEnteredForeground` / `onLeftForeground`, which `MainActivity`'s existing
`DefaultLifecycleObserver` now calls for this ViewModel alongside `AvailabilityViewModel`.

**Both lifecycle edges, not only resume.** Resume alone leaves the case where the user stops from
the shade and never reopens the app: nothing runs, nothing clears, and `locationJob` keeps a
platform location registration alive with no foreground service and no notification behind it.
Backgrounding is the one thing that reliably follows a stop from the shade.

**Not the unconditional foreground gate `AvailabilityViewModel` uses**, and the determination that
ruled it out is the substance of this dispatch rather than a footnote. Two consumers in that
collector must keep running with the screen off during a legitimate recording:

1. **The origin waypoint**, seeded from the first fix clearing the mode's accuracy gate, with no
   timeout. `PathHome.kt:59-62` records that under canopy that fix may arrive very late or never. A
   pocketed phone is the normal way to walk a track. Gate the collector off and a canopy recording
   acquires no origin, and `NavigationHud.kt:355-356` then has no target to point at.
2. **The off-track alert.** `returnToStart` is fed from that same collector and is what runs
   `detectOffTrack` and hands the `Alert` to `alertDelivery` (`:573-581`). A foreground gate would
   have stopped it firing with the screen off, which is the exact condition it exists for, and which
   was confirmed working on hardware hours earlier the same day.

`clearRecordingState()` is extracted and shared with `stopRecording()`, so §2's requirement that the
resync leave identical state holds by construction rather than by inspection. The difference between
the two callers is exactly one documented side effect, the end waypoint, and §2 asked why that was
not inherited: it is seeded from `lastGatedFix`, which by resync time is where the walker
backgrounded the app rather than where they stopped recording. Writing a waypoint there would be a
data write from a stale position onto an already-ended track.

## Evidence

| Run | Result |
|---|---|
| New test, hooks inert (**contaminated, superseded**) | Failed, but so would the fixed build — see below |
| New test, fix present | `tests=3 failures=0 errors=0`, passes in **0.652 s** |
| New test, fix reverted from a saved copy | `tests=3 failures=1 errors=0`, fails after **10.599 s** |
| Full suite, fix present | **172 suites, 1346 tests, 24 skipped, 0 failures, 0 errors** |

Before/after: CI at the branch base `5f8f258` reported 172 suites, **1345** tests, 24 skipped, 0
failures. After is 1346. The delta is exactly the one new test, in an existing class, so the suite
count is unchanged. **The skip count is 24 in both and was never adjusted.**

**The dispatch's expected failure pool did not appear.** It named 12 known failures, 11 Room
migration plus `AvailabilityScreenSettingsPanelTest`, as Windows path-length failures. This run is a
Linux container and saw zero. That pool is a property of the owner's Windows host, not of the tree,
and it should not be carried into a Linux report as an expected baseline.

**One piece of evidence in this dispatch was contaminated and is recorded rather than quietly
replaced.** The first "seen it fail" run used a version of the test that checked `isRecording` after
a single idle-sleep-idle cycle. The resync is a database round-trip off the main thread, so the
assertion ran before the read returned — that version failed with the fix as well as without it, and
its failure said nothing about the defect. It was reported at the time as if it did. The superseding
check polls, and the discriminator is the **timing gap**: 0.652 s fixed against 10.599 s reverted,
which no settling artifact produces. This is the "check that never saw the data that could fail it"
family, caught by a number that did not fit rather than by suspicion.

The revert check followed this repo's runner rules: a copy saved before editing and restored from
that copy rather than from git, the build log checked for compile errors before any result was read
(zero), a failure message specific to the edit, and the forward change confirmed still present
afterwards.

---

## Deferred, and why each is recorded rather than fixed

### 1. Two unsynchronized representations of recording state still exist

`Track.endedAtEpochMillis` in Room, written by the service. `TrackRecordingUiState.activeTrack` in
memory. `MainActivity.kt:413` reads `trackUiState.isRecording`, which is `activeTrack != null`, and
never the row. This change **resynchronizes them at two points**; it does not remove the split. Any
path that stops the service without going through the ViewModel diverges again until the next
`ON_START` or `ON_STOP`. Deriving `isRecording` from the row is the structural fix, post-beta.

### 2. The same-name, different-class `stopRecording()` boundary

`TrackRecordingService.stopRecording()` and `TrackRecordingViewModel.stopRecording()` are different
methods with the same name on different classes. The shade calls the first and nothing calls the
second. **This is what made the defect invisible to a reader tracing the path:** the call looks
right at every step. Recorded so the next reader does not have to rediscover it.

### 3. The passing service test could not fail on this defect

`TrackRecordingServiceTest`'s existing stop-action test asserts the track's own row, which is
exactly right for what it targets and structurally incapable of failing here, because it never reads
ViewModel or UI state at all. A fix landed without the new assertion would have recreated the
original condition: green suite, broken device. Same family as §1's finding and as the contaminated
run above.

### 4. A waypoint can still be written to an ended track

The origin seeding calls `createWaypoint` and `trackRepository.setOriginWaypoint`, mutating the
ended track's own row, if a gated fix arrives while the ViewModel still believes it is recording.
The window is now bounded by the next lifecycle edge rather than unbounded, and it is not closed.

### 5. `appendPoints` accepts late writes silently

`RecordTrackPointsUseCase:14` guards emptiness and nothing else. `RoomTrackRepository.appendPoints`
(`:69-72`) calls `dao.insertPoints` with no check on the track's ended state. **Points are safe
today because no writer exists after the service stops, not because anything would refuse one.**
Same family as items 4 and 1: nothing refuses, there just happens to be no writer. A future change
that gives another class a point-write path reintroduces the corruption risk with nothing to catch
it.

### 6. A track stopped from the shade still has no end waypoint

A normal stop creates one; a shade stop never ran the ViewModel code that would. The resync
deliberately does not create one, for the stale-position reason above. Recorded rather than fixed on
a position nobody measured.

### 7. Two platform registrations, and what that does to the battery figure

`LocationTracker.fixes` is a cold `callbackFlow` (`AndroidLocationTracker.kt:35`) with no `shareIn`.
The listener and the `requestLocationUpdates` calls are inside the builder (`:43`, `:73-75`), so
**every collector registers independently, on GPS and network both**. `AppContainer:129` hands out
one tracker instance, which changes nothing. During a foregrounded recording the app therefore holds
at least two independent registrations at a one-second floor, and possibly three with the compass
strip's.

**Consequence for the beta walk, and this is the part that must not be misfiled:** the duplicate is
present throughout *normal* recording, not only inside the bug window. Any battery figure measured
on a trip is therefore an **overestimate of what a fixed app costs**, and should be recorded as that
rather than as the app's battery cost. Routing this ViewModel off the service's fixes is the
structural change, post-beta, alongside item 1.

### 8. The privacy policy now depends on this fix landing

`docs/legal/privacy-policy.md:135-136` says the app "releases its location subscription rather than
relying on Android to withhold it". Inside the bug window that clause is false, because relying on
Android to withhold is precisely what the app ends up doing. **The policy is deliberately not
edited**, on the owner's ruling: describing a defect that is being removed would be wrong within the
week.

The dependency is recorded here because the two are connected in nobody else's notes: **this fix
landing is a precondition for that clause being accurate when the beta opens**, not only for the
record button clearing.

Sharpening one thing: the neighbouring categorical claim at `:139-141`, "the app receives no location
at all", probably survives, because `ACCESS_BACKGROUND_LOCATION` is not declared and
`AvailabilityViewModel.kt:143-145` already records that the platform is what limits delivery. So in
the bug window the app holds a registration it disclaimed holding, while likely receiving nothing
through it. Wrong about the mechanism, accidentally accurate about the effect.

---

## Disclosure

### Confirmed by observation
Every file and line cited. All four test runs, read from JUnit XML with the build log checked for
compile errors first. The full-suite tally. The 0.652 s / 10.599 s timing gap.

### Inferred, not observed
That the origin waypoint would in practice fail to be created under a foreground gate. The code path
is confirmed; the field frequency is inferred from `PathHome.kt:59-62`'s own record of late
acquisition under canopy, not measured.

### Could not be determined
Whether the platform actually delivers anything to the surviving registration in the bug window.
`ACCESS_BACKGROUND_LOCATION` is not declared, so probably not, but nothing here measured it. The
device is the authority on whether the record button now clears.

### Premises that were wrong
Three, all mine or the dispatch's. §1's premise that the ViewModel appends points: it does not and
never has. My own first "seen it fail" evidence: contaminated, superseded, and left in the record
above rather than deleted. And my own alarm at a `gradle exit=0` that was the shell wrapper's exit
rather than Gradle's — checking was right, the conclusion was not, and the XML settled it either way.

### Decided beyond scope
The ON_STOP edge. The dispatch asked for resume only. It was proposed rather than taken, and the
owner took it before any of it was built.
