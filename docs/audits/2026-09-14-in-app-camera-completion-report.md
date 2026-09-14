# An in-app camera that takes many photos: completion report

**Date:** 2026-09-14
**Request:** the owner's, verbatim — *"Allow the in app camera to take multiple photos. When exiting
the camera, take user back to where they activated the camera. People can preview after they're
done."* Architecture chosen by the owner after the premise below was corrected.
**Branch:** `claude/new-session-vto65i`, PR #102, open and not merged. `origin/main` at `175b050`,
branch at `eee709b` when this started; the remote had not moved before the push.
No schema change, no migration.

---

## §1 — The premise, corrected before anything was built

**There was no in-app camera.** Every capture went out to whatever camera app the user had, through
`ActivityResultContracts.TakePicture` (`ACTION_IMAGE_CAPTURE`), with Forager supplying a
`FileProvider` destination. That intent returns after exactly one photo, by contract. So "allow the
in app camera to take multiple photos" was not a setting to flip; it named a capability the app did
not have.

Two architectures could deliver it, and they differ by an order of magnitude:

- a **re-launch loop** around the stock camera, keeping the handoff;
- an **in-app camera**, which needs CameraX, a preview surface, and `android.permission.CAMERA`
  back in the manifest.

That permission is not a detail. It was **deliberately removed on 2026-09-10**, before the first
beta, with the reason recorded in the manifest: declaring it enabled nothing while every capture
went out by intent, and it only makes the platform *require* the grant before honouring
`ACTION_IMAGE_CAPTURE`. The removal note set an explicit condition — *"do not re-add it without a
direct CameraX/Camera2 use, and if it ever comes back the runtime request in
`PhotoAcquisitionLaunchers` must come back with it."*

Put to the owner as a stop-and-ask rather than resolved alone (CLAUDE.md, working with ambiguity).
The owner chose the in-app camera, which is the condition that note anticipated. Both halves are
back, and the manifest now records the consequence for anyone reading it later: with `CAMERA`
declared, `ACTION_IMAGE_CAPTURE` would also require the grant, which is one reason the in-app
camera **replaced** the intent rather than sitting beside it.

## §2 — A `Dialog`, which is what makes requirement 2 true by construction

*"When exiting the camera, take user back to where they activated the camera"* is a requirement
about state, not routing. `InAppCameraDialog` is a full-screen `Dialog`: the screen underneath is
never left, never recomposed away, and has nothing to restore, so returning is simply dismissing.
A navigation destination would have meant three launch surfaces (the find edit form, the Album, the
pull-photo picker) each saving and restoring their own position — three chances to get it wrong for
nothing gained. `PhotoViewerDialog` settled the same question the same way earlier in this branch.

It also repays an old bug rather than guarding against it again. Handing off to an external camera
app produced ON_PAUSE/ON_STOP indistinguishable from the user backgrounding Forager, which is what
silently closed the find being edited on every single capture until `isAcquisitionInFlight` was
threaded up to suppress it. A dialog never leaves the Activity, so that confusion no longer arises
for captures at all. **The flag stays**, narrowed: the photo picker and the permission dialog still
leave.

**Deleted with the intent path:** `pendingCapture` and its custom `Saver`. They existed so a capture
destination survived Activity recreation while an external camera app was foregrounded — a real fix
for a real lost photo (device-check patch, Item 2). Nothing recreates the Activity now. The fix is
not regressed; its precondition is gone. Recorded rather than left as state nothing reads
(CLAUDE.md's reachability rule).

## §3 — Each photo is handed over the moment it lands

`onPhotoCaptured` fires per shot, not once with a list at dismissal. That is what *"people can
preview after they're done"* asks for: no per-shot confirm screen, nothing to accept or retake, the
camera stays up. It also means a session interrupted by a phone call keeps the photos already taken,
which a batch handed over at the end would lose.

The running count is the only feedback during a session, deliberately. A thumbnail of the last shot
invites reviewing inside the camera, which is the flow the owner asked to move to afterwards.

## §4 — The interface is not tidiness

`CameraCaptureSession` (three methods' worth of surface: a state, and `capture(File): Result<Unit>`)
exists because **CameraX cannot run in this project's test harness at all** — Robolectric has no
camera provider, no HAL, no surface. A screen calling CameraX directly would have had no testable
behaviour left: not the shutter, not the count, not failure handling. Behind the interface all of
that is ordinary Robolectric work, and the untested surface is one file
(`CameraXCaptureSession.kt`) instead of the whole feature.

Two decisions inside that file are worth their own line, because both were chosen *against* the
more modern option and for the same reason — nothing here can be tested before it ships, so the
deciding factor is which API is most certain to behave as expected on a device:

- `camera-view`'s `PreviewView` in an `AndroidView`, not `camera-compose`'s `CameraXViewfinder`.
- `ProcessCameraProvider.getInstance` with a listener, not the newer suspend `awaitInstance`.

**Orientation is read per capture, not once at bind.** Setting `targetRotation` at bind is the
obvious version and wrong in a way that would ship easily: turning the phone does not necessarily
recreate the Activity, so a session bound in portrait would tag every later landscape photo
portrait. The EXIF orientation tag is what the whole display path reads and what
`scrubPhotoMetadata` deliberately preserves, so getting it wrong here would surface as sideways
photos everywhere downstream.

**No location is ever attached.** `ImageCapture.Metadata` carries an optional `location` and this
never sets one, so a photo taken here has no GPS to strip. `scrubPhotoMetadata` still runs on
persist and still should: the camera HAL writes make, model and timestamps regardless, which is
exactly what an allowlist is for.

CameraX pinned to **1.6.2**, the latest stable on Google's Maven, checked against
`dl.google.com/dl/android/maven2/androidx/camera/<artifact>/maven-metadata.xml`. That metadata's own
`<latest>` and `<release>` both point at `1.7.0-alpha03`; this project does not take the alpha line,
the same call and the same check as `datastorePreferences` and `exifinterface`. Resolution confirmed
at 1.6.2 from `app:dependencies`, not assumed from the catalog.

---

## Evidence

### Suite counts, from JUnit XML

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (`eee709b`) | 178 | 1429 | 0 | 0 | 24 |
| after (`26a9423`) | 179 | 1442 | 0 | 0 | 24 |

1429 + 13 (`InAppCameraDialogTest`, a new suite) = **1442**, counted from the XML. Skip count
unchanged; the CI allowlist untouched. `assembleDebug` exit 0. Container runs on Linux, not GitHub
Actions.

### Revert checks

Six, through the runner. Restores verified byte-identical against a saved copy each time; tree
clean against `26a9423` afterwards.

| revert | failure produced |
|---|---|
| the camera dismisses itself after a successful shot | three: "the camera never dismissed itself expected:&lt;0&gt; but was:&lt;4&gt;", and two more |
| a failed capture sets no message | two, both "TestTag = 'in-app-camera-error' is not displayed" |
| the failed capture's file is not deleted | `a failed capture leaves no file behind` — "left behind: [a7ddaf8f-….jpg]" |
| the shutter is always enabled | two, both "Failed to assert the following: (is not enabled)" |
| every shot shares one destination filename | `each shot goes to its own file` — "expected:&lt;3&gt; but was:&lt;1&gt;" |
| (guard proof) a deliberately non-compiling revert | the runner refused to read results and named the unresolved reference |

### Two findings the checks produced, which are the useful part of this report

**A test that could not fail.** `a failed capture leaves no file behind` passed with the cleanup
deleted — zero failures on that revert. `CameraCaptureFiles.newCapture()` creates the *directory*
but not the file, and the fake wrote nothing on a failed capture, so there was never a file to leave
behind and both branches produced an empty directory. The cleanup exists for a camera that writes a
partial file and *then* errors, which is the case the fake was not modelling. This is CLAUDE.md's
named family — the check and the thing checked decoupled by a step in between — and it was the
revert that found it, not review. The fake now writes a stub before failing, a companion test holds
that property, and the same revert now fails on its own message.

Worth being exact about what this means: **the original version of that test was evidence of
nothing**, and it read exactly as convincing as the fixed one.

**This class broke another one.** The first full-suite run after adding `InAppCameraDialogTest` took
down `AvailabilityScreenSettingsPanelTest`'s GPX share test, which stayed green on its own.
`FileProvider` caches one `PathStrategy` per authority in a **static** map, and Robolectric gives
every `@Test` method a fresh data directory. Clearing that cache on entry fixes this class and
breaks the next one: the strategy the last method leaves behind points at a temp directory that no
longer exists. Clearing on the way out too keeps the damage inside this file.

Nothing was silenced, skipped or `@Ignore`d — the suite is green because the cause was removed. The
diagnosis came from running one failing method alone and watching it pass, which is what separated
"my file paths are wrong" from "state survives between methods". `sCache` was confirmed by
reflecting over `FileProvider`'s declared fields, not remembered, and the cleanup throws if androidx
renames it.

### A guard in the revert runner that could never have fired

The runner captured only stdout. **Kotlin writes its `e: ` compile errors to stderr**, so its
compile-error guard — the one CLAUDE.md's own rule exists to provide — could not fire for a Kotlin
error at all. A deliberately-broken revert reported "NO JUNIT XML PRODUCED" instead of naming the
compile failure; only the XML deletion stopped it citing the previous run's results, which is the
exact failure that rule was written after. Fixed to capture both streams and to treat a failed
non-test Gradle task as a build failure, then proved by re-running the same broken revert: it now
names the unresolved reference and refuses to read results.

Every revert check recorded in `docs/audits/` before today ran under the broken guard. They are
still trustworthy for the reason CLAUDE.md already gives — each names a failure message specific to
its own edit — but the guard was not the thing making them so.

---

## Disclosure

### Confirmed by observation

- The intent path and its one-shot contract, by reading `PhotoAcquisitionLaunchers.kt` and
  `CameraCaptureFiles.kt`; the absent `CAMERA` permission and absent CameraX, by reading the
  manifest and the version catalog.
- CameraX 1.6.2 being the latest stable, from Google's Maven metadata; the resolved version, from
  `app:dependencies`.
- `FileProvider.sCache`, by reflection over its declared fields.
- Every count, failure message and revert result above, from JUnit XML and build logs in this
  container.

### Inferred, not observed

- That `PreviewView` and the listener form of `getInstance` are the safer of the two API pairs.
  Reasoned from their age and stability, not measured — nothing here can be.
- That a partial-file-then-error is how a real CameraX failure presents. It is what the cleanup is
  written for and what the fake now models; the real failure mode was not observed.

### Could not be determined

**Everything about the camera itself.** Stated in full because a green suite must not be read as
covering any of it:

- whether a real camera opens, and whether the viewfinder draws;
- whether a captured JPEG comes out right-way-up, which is the per-capture `targetRotation`
  decision in §4 and the one most likely to be wrong;
- whether the scrub parses a real CameraX JPEG (it refuses and leaves the file whole if it cannot,
  so the failure mode is "metadata kept", not "photo damaged");
- whether the permission prompt appears once and behaves on a denial;
- how the shutter *feels* shot-to-shot, which is the entire point of the feature;
- whether a device with no back camera falls through to the front one.

The owner has said the device check covers the whole release when it is finished. These are the
lines for it.

### Premises that were wrong

- **"The in app camera"** — there was none. §1.
- **My own instrument.** The fake's failure behaviour made one test unable to fail, and I had
  written a test asserting the very property that guaranteed it. Found by a revert, not by me.

### Decided beyond scope

- **Replacing the intent path outright** rather than keeping both. Forced by the permission's
  effect on `ACTION_IMAGE_CAPTURE`, and recorded in the manifest.
- **Deleting `pendingCapture` and `CaptureSaver`.** §2.
- **Placing `CameraDialog()` at each of the three call sites** rather than emitting it from inside
  `rememberPhotoAcquisitionLaunchers`, which would have saved three lines. A `remember*` function
  that quietly draws UI costs the next reader more than three lines are worth.
- **Fixing the revert runner.** Not asked for; it was producing a guarantee it could not deliver.

### Checks that did not fire, and empty results

- No CI run at the time of writing.
- The first `failed-file-not-cleaned` revert produced **zero** failures. Reported as the finding it
  was rather than dropped once the test was fixed.
- No test asserts anything about the viewfinder slot's contents; it is a `Box` in every test.
