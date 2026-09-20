# The absence timeout, as built: one requirement of three, and why the other two were struck

**Date:** 2026-09-17 · **Branch:** `claude/new-session-vto65i` · **Commits:** `702be42` (code and
tests), plus this report's commit · **Base:** `69e2f26` · **Supersedes** the "Options, priced, none
chosen" section of `2026-09-17-camera-absence-timeout-prebuild-report.md`, which is left unedited as
the record of what was found before the decisions were made.

**One-paragraph outcome.** Four minutes away with the in-app camera open now closes it, decided by
subtracting two `SystemClock.elapsedRealtime` readings on return — no service, no alarm, no wake
lock. The dispatch's other two requirements were struck by the owner after the pre-build report:
captures are already persisted on the shutter tap, so there is no unsaved bucket to flush, and
backgrounding already leaves journal editing and arms cartography's prompt, which **agrees** with a
four-minute absence rather than conflicting with it. Suite 1547 → 1563 (198 → 200 classes),
0 failures, 24 skipped unchanged. Two revert checks, each with 0 compile errors and messages
specific to its own edit.

## The owner's reasoning on the two struck requirements, recorded because it outlives the decision

**Requirement 2 (return the user mid-edit) — struck.** The pre-build report framed the journal
auto-save and the cartography prompt as colliding with the feature. The owner's ruling reframed it:
*"Four minutes of absence is the app's definition of a genuine departure. The auto-save and the
cartography prompt are what the app already does on a genuine departure, and they were designed for
exactly that. Suppressing them would mean the camera being open makes a real departure not count as
one. That's backwards. The camera being open is not evidence the user is still engaged; the
four-minute absence is evidence they weren't."* So the two hooks are not a collision to work around;
the requirement contradicted them and was written without knowing they existed.

**Requirement 3 (save unpersisted captures) — struck.** Every capture persists on the shutter tap,
so the requirement had no work to do. The privacy property it was written to protect holds earlier
and harder than the spec assumed.

**Three premises of the dispatch did not hold**, all of the same kind — how the app works, asserted
from conversation rather than from the tree: that captures could be unpersisted, that the lifecycle
hooks were absent, and that `InAppCameraTarget` identified the record rather than routing the photo.

## What was built

- `CameraAbsence.kt` — `CAMERA_ABSENCE_TIMEOUT_MILLIS` (four minutes, the owner's number, recorded
  as a judgement rather than a measurement), the pure `cameraClosesAfterAbsence`, and
  `CameraAbsenceWatcher`, which reports `ON_STOP`/`ON_START` with an elapsed-real-time reading.
- `InAppCameraViewModel` — holds the departure, clears it on `open`/`close`, and closes on a return
  that crosses the threshold. Still no Android dependency and still tested headless: the clock is
  read at the UI edge and handed in as a number.
- `MainActivity` — composes the watcher only while the camera is open.
- `libs.versions.toml` / `app/build.gradle.kts` — `androidx.lifecycle:lifecycle-runtime-testing` at
  the existing `lifecycle` version ref, for `TestLifecycleOwner`. Pinned through the catalog, per
  CLAUDE.md.

**`ON_STOP` not `ON_PAUSE`:** a permission dialog pauses without stopping, and that is not the user
leaving. **`elapsedRealtime` not `CurrentTimeProvider`:** elapsed real time counts through sleep and
cannot jump, where wall-clock epoch millis is a timestamp to store rather than an interval to
measure — an NTP correction could move it under a measurement in flight.

## The thing the tests found that the design had not

`Lifecycle.addObserver` replays events to bring a new observer up to the owner's current state, so
composing the watcher over an already-started Activity fires one `ON_START` immediately — a return
reported before any departure. It is harmless, because `open()` clears the departure and
`onReturnedToApp` returns early without one. It is also **load-bearing**: it is what makes an
Activity recreation during an absence still close the camera on return, since the rebuilt
composition's replayed `ON_START` is the return and the ViewModel still holds the departure. Found
as an unexpected `[0, 5000]` in an assertion, then documented and given its own test rather than
absorbed into an expected value.

## Evidence

**Tests (16 new across two classes).** The decision is tested under the threshold, at it (four
minutes or more closes, so the boundary closes), over it, with no departure, on a negative interval,
and for the departure being consumed by its own return. The watcher is tested on a real
`TestLifecycleOwner`: both callbacks with the clock's readings, the pause-is-not-a-departure case,
the replay case, and that leaving composition removes the observer — with a precondition assertion
that it was attached in the first place, so that last test cannot pass on a watcher that never
worked.

**The clock positive control, as the dispatch required.** The clock is **injected, not pinned in the
harness**, so under- and over-threshold are different literals in the test body and cannot collapse
into one instant. The watcher test additionally asserts the list of readings the watcher actually
took, so a watcher that stopped consulting the clock would fail rather than pass on a default.

**Revert checks — two, because there are two things to break.** Each against a copy saved before
editing, never `git checkout --`, and each with the build log checked for compile errors first:

| Reverted | Compile errors | Failures | Message |
|---|---|---|---|
| the threshold comparison (`away >= threshold` → `false`) | 0 | 3 | `four minutes or more, so the boundary closes`; `expected:<null> but was:<ALBUM>` |
| the `ON_STOP` wiring (departure unreported) | 0 | 3 | `expected:<[1000]> but was:<[]>` |

Different sets and different messages, which is what tells the decision and the wiring apart —
either alone would have left the other unproven.

**Suite:** 1547 → 1563 tests, 198 → 200 classes, 0 failures, 24 skipped unchanged.

## Documents

**v4 step 6 corrected**, with a superseding note in the body. Old 6.1 asserted a close-on-background
that nothing implemented; it is replaced by three items — short absence (still open), long absence
(closed), and the picker round trip (still open, the case close-on-background would have broken).
Old 6.2–6.5 are now 6.4–6.7, unchanged. The note also records the two struck requirements, so a
runner does not go looking for captures to check or an edit to be returned to.

**v4 step 8 is untouched and still needs rewriting.** Its premise ("three photos, do not persist" →
`captures` holds three) does not match the code: there is no user-reachable way to leave three
captures unpersisted except through the silent-swallow path below, so 8.4–8.5 test a bug path and
fixing the bug deletes the step's premise. Held deliberately — what it should say depends on the
swallow decision, which is the owner's and not yet made.

## Known gaps, stated rather than discovered later

- **Process death during an absence.** No timer fires and none is wanted; the process is new, the
  ViewModel is gone, and the camera is closed on return regardless. Captures were already persisted;
  genuine orphans are collected by the startup sweep with its unchanged two-second mtime guard. As
  the dispatch instructed, recorded and not built around.
- **The silent swallow at `MushroomLogViewModel.kt:646`** — a `LOG_ENTRY` capture arriving with no
  editing entry is dropped with no persist, no `release()` and no log: a leaked scratch file and a
  lost photo. Pre-existing, untouched, and needing its own decision. The owner's inclination is to
  log and release rather than invent a destination.
- **Four minutes is unmeasured.** Nobody has data on picker round-trip time on a slow device or on
  how long people actually leave. A number to change, not a design to revisit.
- **Nothing here was run on hardware.** The device items are v4 steps 6.1–6.3.
