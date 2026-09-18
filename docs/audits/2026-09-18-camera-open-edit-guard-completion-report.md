# Camera-open edit guard: completion report

**Dispatch:** "don't end the journal edit while the in-app camera is open", written against
`claude/new-session-vto65i` at `6bbd90b`. Not committed to the repository; its substance and the
owner's ruling are recorded here and at the guard.
**Base actually used:** `7aa52ab`, the remote tip on 2026-09-18. Since `6bbd90b` it gained only
docs and debug-only diagnostics code, so nothing the dispatch cites had moved.
**Code:** `83d64f0`, plus the docs commit that carries this report, on `claude/camera-open-edit-guard`.

## Stopped before building, then built option 1

The first pass stopped at the verify-first stage. As dispatched, the guard also changes the
**long** absence: `ON_STOP` cannot know how long the user will be away, so after four minutes the
camera would close onto a still-open edit form. That reverses the owner's 2026-09-17 strike of
"return the user mid-edit" (`2026-09-17-camera-absence-timeout-prebuild-report.md:165-176` offered
this exact guard as its option 2, and the owner chose option 1). It was surfaced as two options:
1. Build as dispatched.
2. Guard, then end the edit when the timeout fires.

**The owner chose option 1**, on the ground that both premises of the strike had since been shown
false:
- "Backgrounding already leaves journal editing" holds on the compact layout only (item 2 below).
- The incidental exit was described as protecting the user's work, but it writes nothing: the
  content is already on disk from per-keystroke saves, and `onLeaveEditingIncidentally` only closes
  the form (creation-snapshot pulse, 2026-09-17).

Option 2 was ruled out on its own terms: it violates the dispatch's constraint against touching the
absence timeout, it is wider than narrow, and it would keep compact and wide apart.

## What landed

- **The guard.**
  - Before: `AvailabilityScreen.kt:1474`,
    `if (latestIsJournalEditing && !latestPhotoAcquisitionInFlight) latestOnLeaveEditingIncidentally()`.
  - After: `AvailabilityScreen.kt:1494`, the same with `&& !latestInAppCameraOpen`.
  - `latestInAppCameraOpen` is `rememberUpdatedState(inAppCameraTarget != null)` at `:1457`, beside
    the other `latest*` values.
  - Its comment (`:1438-1456`) records three things:
    - why the guard exists;
    - that skipping the unchanged-re-edit discard while the camera is open is the point of the
      guard, not a tolerated side effect;
    - that the long absence reverses the strike and makes compact agree with wide.
- **`CameraAbsence.kt`**: the "leaving the edit" bullet is superseded for the journal. The original
  text is quoted, and the amendment says which two premises failed and what the ruling is.
  Cartography's half stands.
- **v4 step 6**: the "nor does it return the user mid-edit" sentence is marked superseded in place,
  and a dated 2026-09-18 note explains why. Items 6.8–6.11 are the dispatch's device items, and the
  evidence line now reads eleven confirmations. **Adding the items went slightly beyond "amend the
  note"**: I added them so the imminent device run finds them in the checklist it reads. They are
  confined to step 6 and nothing else in v4 changed.

**Correction to the dispatch, as asked.** It called the re-edit discard consequence "accepted".
The owner framed it as the point: while the camera is open, nothing is discarded and the entry
stays open, because something may be about to arrive (the creation-snapshot pulse's rules). The
guard's comment is written that way. The dispatch file is not in the repository, so it cannot be
corrected in place; this paragraph is the correction's record.

## Verify-first answers

1. **Scope and `rememberUpdatedState`.** `inAppCameraTarget` is a parameter of `AvailabilityScreen`
   (`:510`) and is readable at the hook through `compactMainScaffold`'s closure (`:1327`). Nothing
   the dispatch cites had moved since `6ff1262`: `:1430-1437` and `:1474` matched before the edit.
   `rememberUpdatedState` is required, not just stylistic: revert G2 below captures the value once
   and the guard stops working.
2. **Wide layout.** The hook lives in `compactMainScaffold`, which is composed only in the COMPACT
   branch (`:2091`, `:2120` before the edit). MEDIUM/EXPANDED windows have no `ON_STOP` hook that
   ends a find edit: `LogPanel` ends one only on back and view switches (`LogPanel.kt:242`, `:262`).
   So the defect was compact-only, and the wide layout already returned users mid-edit. This is now
   backed by a test as well as by reading (below). Device item 6.11 is a confirmation that the
   layouts agree, not a regression check.
3. **The seven `backgroundThenResume()` tests** are in `AvailabilityScreenBackNavigationTest.kt`
   (`:737`, `:752`, `:765`, `:780`, `:815`, `:865`, `:945`). The file never references the camera.
   All 27 tests in the class pass with the guard.

## Evidence

**Seen failing before the fix**, through the real lifecycle (`ActivityScenario.moveToState`
CREATED → RESUMED), with the real camera dialog over a fake capture session, the find opened
through the real Journal → Records → Logged Finds → New log entry path, and its own Camera button:

> `the incidental exit must not run while the camera is open expected:<0> but was:<1>`

**New tests (3), in `AvailabilityScreenInAppCameraTest`:**
- `backgrounding with the camera open over a find leaves the find open, and the shot still routes to it`:
  no incidental exit, the entry still set, the camera still up, and the shutter delivers to the
  find's photo callback.
- `backgrounding over a find with no camera open still leaves the edit, as before`: one call, entry
  cleared (unchanged behaviour).
- `on the wide layout backgrounding over an open find never ends the edit, camera or not`: after a
  width flip to 700 dp, backgrounding adds no call. Calls made by the flip itself are counted first
  and excluded. This test passes with or without the guard, because it pins existing wide-layout
  behaviour, not the change; its control is the compact no-camera test, the same path without the
  flip, which does produce a call.

**Revert checks.** Each is a one-line edit. Build logs were checked for compile errors (none),
failures were read only from XML newer than the run, and each file was restored from a saved copy
and then matched `HEAD` byte for byte.

| Revert | Failed | Message |
|---|---|---|
| G1 remove `&& !latestInAppCameraOpen` | the camera-open test only | `the incidental exit must not run while the camera is open expected:<0> but was:<1>` |
| G2 `remember { mutableStateOf(inAppCameraTarget != null) }` instead of `rememberUpdatedState` | the camera-open test only | the same message: the observer reads the value from registration, when the camera was closed |

**Suite:** `:app:testDebugUnitTest`, executed: 200 classes, **1,577 tests, 0 failures, 0 errors,
24 skipped**. That is 1,574 before this change plus the three new tests; the skips pre-date it, and
the diff adds no `@Ignore`.

## Not tested / not verified

- **Nothing on a device.** v4 items 6.8–6.11 are the device checks. The four-minute close itself is
  `MainActivity`'s `CameraAbsenceWatcher` and is not in the Robolectric screen harness, so the long
  absence (6.9) is verified only by reasoning: the guard leaves the entry set, and nothing else
  clears it on return.
- The shot's routing is asserted only as far as `AvailabilityScreen`'s `onAddLogPhoto` callback.
  That the ViewModel attaches it to *this* find is `MushroomLogViewModel`'s existing behaviour and
  was not re-tested here.
- The process-kill consequence (an untouched re-edit draft surviving into Drafts) was not exercised.
  It follows from the discard not running, which G1 shows.
- Cartography's `ON_STOP` hook (`CartographyScreen.kt:204-206`) is untouched and has no camera
  guard. Out of scope, and cartography's prompt behaviour is by design.

## Out of scope, noted

The owner's separate finding, that a *new* entry opened and left unchanged still leaves a draft,
is pre-existing, is not caused by this change, and is the subject of its own pulse. This change
does not touch it.
