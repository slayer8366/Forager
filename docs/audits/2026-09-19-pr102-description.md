# PR #102 description: the outward-facing summary, fact-checked against the tree

**Date:** 2026-09-19 · **Kind:** PR description, not an audit — this is the body posted to
GitHub, recorded here so the repository carries the outward-facing artifact and not only the
findings behind it. It summarises; the reports it cites are what state.

**Describes:** `claude/new-session-vto65i` at `de78d30`, whose merge-base with `main` is `175b050`.
**Posted to:** https://github.com/slayer8366/Forager/pull/102, 2026-09-19.

**Revised 2026-09-19, later the same day, because the run record landed.** The body below was
updated when `2026-09-19-pr102-device-check-v4-run-record.md` (#105) closed the evidence gap this
document records: the six device results it had listed as "observed but not recorded here" are now
cited to that record, step 9's measurement replaces the inferred main-thread claim, and step 11 is
named as not run with the two checks that would settle it. The verbatim claim below is maintained
rather than allowed to lapse, which is the property that makes this file worth keeping: it is the
body as posted, after that revision.

## Why this exists as a record

The first draft was written from a working conversation rather than from the tree, and was checked
claim by claim against `de78d30` before posting. That check is the reason for the file: a PR body is
the outward-facing artifact, read by people who were not present, and it is the first thing anyone
consults later.

**What the check changed** — five claims wrong, four scoped too widely:

1. **The incidental-exit bullet was inverted.** It described the camera being open as suppressing
   the journal's incidental-exit auto-save. Suppression was proposed, priced and struck
   (`CameraAbsence.kt:38-43`); *opening* the camera needs no guard because it fires no `ON_STOP`.
   As written, the claim contradicted the album-rescue bullet that follows it, which is its
   consequence.
2. **The status bar reveals on a swipe, not a tap** (`CameraWindowChrome.kt:113`).
3. **"A failed log write no longer kills the app; the panel shows when the log has stopped
   recording"** had no support in either half: no catch exists (`DiagnosticsLog.kt:62-74`), the file
   arrives whole in one commit so there is no "no longer", and the nearest recorded fact is the
   opposite (`2026-09-15-diagnostics-panel-hides-the-rotated-generation.md`, recorded not fixed).
   Removed; the real gap is now listed under known issues.
4. **"Four of its steps asserted behaviour that had no source in the code"** — the record says four
   *corrections*, three of them in steps 7 and 12, the fourth a wrong cross-reference
   (`2026-09-16-device-check-v3-tree-corrections.md`). The step-run/tester split was invented and is
   gone: no step has a committed run record.
5. **A "plain-language tester card" was cited and does not exist** in the tree.
6. **The scrub's justification was the API-29 story.** The real reason is stronger: a capture's URI
   is a `FileProvider` one over the app's own `captures/`, so `MediaStore` redaction never applied
   at any API level (`FilePhotoStore.kt:44-49`, itself a correction to a doc comment that claimed
   otherwise).
7. **The scrub is captures-only**, with a byte-identity test on imports so the scope cannot drift
   (`FilePhotoStore.kt:132`); the draft implied it covered every photo. It also keeps APP0 `JFIF`,
   which the kept-list omitted.
8. **CameraX honouring the HAL's tag over `targetRotation` is inferred, not observed**
   (`IntendedOrientation.kt:22-25`), and is now marked as such.
9. **The device line said "Android 16."** The phone's OS version is recorded nowhere in this
   repository; API 36 describes the emulator AVD. Device results moved under "observed but not
   recorded here", since no run record was committed.

Three limitations recorded in the tree but missing from the draft were added: the Diagnostics
panel's rotated-generation gap, the 600dp+ orientation case, and "Lock camera to portrait" pinning
the capture tag.

**Citations.** Every claim in the body below carries a file and line, or is marked as not carried by
this repository. Index-row citations were replaced with the reports the findings live in: an index
row summarises where a report states, and `docs/audits/README.md` line numbers move whenever a row
is inserted, this file being date-ordered rather than append-only.

---

*Everything below this line is the body posted to PR #102, verbatim.*

---

# In-app camera: capture, orientation, privacy scrub, and the overlay

Closes the camera pipeline work. This started as a defect review of the capture path and grew to
cover the in-app camera's architecture, its orientation behaviour, the metadata scrub, and the
overlay.

**Suite: 202 classes, 1588 tests, 0 failures, 24 skipped**, measured at `59d96cc`
(`docs/audits/2026-09-19-reverse-portrait-excluded-report.md`). The three commits after it change
one source file, and only its doc comment. The 24 skips predate this branch and match CI's
`SKIPPED_TESTS_ALLOWLIST` by test ID.

Every claim below is sourced to a file and line in this branch, or marked as not recorded here.

---

## What this fixes

### The original defects

- **Capture temp files were never deleted.** Every capture left a file in `captures/`, doubling
  storage over time. Now released in a `finally` (`FilePhotoStore.kt:106-108`), with a startup
  sweep for anything a dead process left behind (`ForagerApplication.kt:40, 63-70`,
  `CameraCaptureFiles.sweepOrphans`).
- **`FilePhotoStore.persist` and the metadata scrub ran on the main thread.** The whole body is on
  `Dispatchers.IO` now (`FilePhotoStore.kt:102`). The before-state was inferred by reading every
  frame of the chain, not measured. **The after state is now measured**: step 9 of the run
  record found no new StrictMode stack around a capture, and none naming `FilePhotoStore`,
  `PhotoMetadataScrub`, `AddPhotoToLogEntryUseCase` or `AddPhotoToGalleryUseCase`.
- A stale doc comment in `CameraCaptureFiles` that named a cleanup caller which never existed
  (`CameraCaptureFiles.kt:20-24`).

### Privacy

A streaming **allowlist** JPEG scrub, **on camera captures only** — a photo imported from the
gallery is never rewritten (`FilePhotoStore.kt:132`, with a byte-identity test on imports so the
scope cannot drift).

Three segment kinds survive: APP0 `JFIF`, APP2 `ICC_PROFILE` and APP14 `Adobe`
(`PhotoMetadataScrub.kt:228-234`), plus the orientation tag, read before the strip and reapplied
after (`:88-103`). Everything else goes: GPS, MakerNote, XMP, IPTC, the embedded thumbnail,
timestamps, make, model and serials. The file is truncated at the first EOI after the scans
(`:186-225`). Failure is fail-open and logged: the original is left untouched (`:106-113`).

Allowlist rather than denylist, so unknown vendor fields are dropped by default.

**Why this is done in-app** is not the API-29 story. `MediaStore`'s GPS redaction never covered a
capture at any API level, because a capture's URI is a `FileProvider` one over this app's own
`captures/` directory and no `MediaStore` is in the path (`FilePhotoStore.kt:44-49`, recorded as a
correction to a doc comment that used to claim otherwise). The API 26-28 limit is a separate,
still-open matter for gallery *imports* (`:59-70`).

### Orientation

Photos come out upright from all four holds. CameraX takes the saved file's tag from the HAL's own
EXIF rather than from the requested `targetRotation` — **inferred** from reading camera-core 1.6.2
and the device result, not observed (`IntendedOrientation.kt:22-25`) — so `IntendedOrientation`
reapplies the intended tag after save when the pixels are untransposed, and declines when the HAL
rotated them (`:112-145`).

Rotation comes from `OrientationEventListener` (`CameraXCaptureSession.kt:318-321`) rather than
display rotation, so it works with auto-rotate off; display rotation is a logged fallback for a
shot with no reading yet (`:337-342`).

### The window

The camera draws in the **Activity's own window** and requests **seamless rotation**
(`CameraWindowChrome.kt:172-186`), so turning the phone with the camera open snaps rather than
spins.

This was the longest thread in the PR. A `Dialog` cannot get seamless rotation: the platform picks
a rotation's animation from the task's main window, which is only ever `TYPE_BASE_APPLICATION`, and
then requires that window to be the top fullscreen opaque one
(`docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`). Moving the camera into the
Activity's window satisfies both checks while leaving the ViewModel, the absence timeout, the
in-flight guard and photo routing untouched — `MainActivity` stays `state=RESUMED` with the camera
open, so nothing about `ON_STOP` changed
(`docs/audits/2026-09-19-camera-activity-window-report.md:32-33`). The manifest attribute
`android:rotationAnimation` must stay undeclared; it is read first and would defeat the runtime
request (`CameraWindowChrome.kt:156-161`).

**Reverse portrait is excluded from the window.** `SCREEN_ORIENTATION_SENSOR` rather than
`FULL_SENSOR` (`WindowOrientation.kt:120-122`): on a phone the platform refuses to resolve a sensor
reading of `ROTATION_180`, so a half turn leaves the window where it is — no animation, and the
status bar does not move. The sensor still reads all four, so a photo taken upside-down still saves
upright. This is what the reference camera app does, and it is what makes the foraging gill shot
work.

### The overlay

- **The shutter sits on the charger-port edge in every orientation**, computed from device rotation,
  not a fixed screen side (`CameraArrangement.kt:12-38`). It is a physical location the user's thumb
  learns. The mapping assumes a portrait-natural phone, and says so.
- **The top strip sits on the punch-hole edge**, its opposite (`CameraBands.kt:44`).
- **Region model**: viewfinder region plus two bands, zero-thick at full-bleed, controls anchored
  inside the bands. Built with one ratio so adding an aspect-ratio feature later does not mean
  revisiting every control's position.
- **Overlay text and glyphs rotate in place** so they stay readable in the hand (`rotateWithDevice`,
  sensor minus display).
- **White fill with a black outline** (`CameraOverlay.kt:59-60`); errors use the theme's error
  colour (`InAppCameraDialog.kt:352`). The outline provides legibility, the fill carries meaning.
  Foraging means bright scenes and white-on-transparent disappears over a pale cap.
- **The system status bar is hidden** while the camera is open and reveals on a **swipe from the
  screen's top edge** — `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` (`CameraWindowChrome.kt:113`).
- **Done is gone.** Back carried everything it did, so it was a second control for one function.

### Session behaviour

- **The camera closes after four minutes away**, decided by comparing two `elapsedRealtime`
  readings on return (`CameraAbsence.kt:58, 69-75, 106`). Nothing runs while the app is
  backgrounded: no service, no alarm, no wake lock (`:55-56`). A short absence leaves the session
  intact, so a picker round-trip does not close it.
- **Backgrounding with the camera open still leaves the find**, deliberately. Suppressing the
  journal's incidental-exit auto-save while the camera is up was proposed, priced and struck: four
  minutes away is a genuine departure, and the camera being open is not evidence the user is still
  engaged (`CameraAbsence.kt:38-43`,
  `docs/audits/2026-09-17-camera-absence-timeout-completion-report.md`). *Opening* the camera is a
  different matter and needs no guard — it no longer leaves the Activity, so no `ON_STOP` fires at
  all; the in-flight flag stays narrowed to the gallery picker and the permission dialog
  (`PhotoAcquisitionLaunchers.kt:124-128, 132`).
- **A capture that arrives with no editing entry is saved to the album** with a toast and a
  diagnostic entry, instead of being silently dropped along with its temp file
  (`MushroomLogViewModel.kt:742-753`). This is the reachable consequence of the decision above, and
  the path is written out step by step at `:692-713` so nobody deletes it as dead code.

### Diagnostics

A debug-only panel listing `photos/` and `captures/` and sharing any of them through the same
`FileProvider` the app already declares; debug overrides the paths file to reach `photos/` and
`diagnostics/`, and a test asserts it stays a superset of the release one
(`app/src/debug/res/xml/file_paths.xml`, `DebugFileProviderPathsTest`). Plus a log the panel can
read on a phone with no logcat.

Each capture writes two entries: `capture shot` with the device rotation, display rotation, target
rotation, requested degrees and resolution, and one `capture orientation` outcome
(`CameraXCaptureSession.kt:359, 386-423`).

The display rotation was added late (`:349-353`) and immediately earned its place: the glyphs turn
on sensor minus display while the arrangement turns on display alone, so an entry showing only the
sensor cannot show the one disagreement every orientation question comes down to.

---

## Verification

**In the repository.** The suite above; revert checks quoted per dispatch in `docs/audits/README.md`;
and the emulator runs, which are recorded with their measurements — all four turns seamless with
`nav bar allows seamless` and animation `3`, the half turn leaving `mRotation` and the shutter's
bounds identical, capture angle unchanged across both window shapes
(`docs/audits/2026-09-19-camera-activity-window-report.md` and
`docs/audits/2026-09-19-reverse-portrait-excluded-report.md`).

**On the device, recorded.** Samsung Galaxy S26 Ultra, Android 16, at this commit. Steps 7, 8, 9,
10 and 12 of the device check, every reading quoted verbatim in
`docs/audits/2026-09-19-pr102-device-check-v4-run-record.md`, landing in #105:

- the two-entry invariant, three photos giving three pairs;
- three holds giving three distinct EXIF tags, `kept fromTag=6 degrees=90` portrait,
  `fromTag=1 degrees=0` port-right, `fromTag=3 degrees=180` port-left;
- `captures/` empty after a force stop, with `sweep deleted=0`;
- the privacy scrub on a real file: `Orientation: Rotate 90 CW` present, every `GPS*`, `MakerNote*`,
  `XMP*`, `IPTC*`, thumbnail, timestamp, make, model and serial tag absent, file ending `ff d9` with
  nothing after, with the exiftool dump and hex tail in the record;
- no new StrictMode stack around a capture.

The run record is explicit about what it does not cover, including a step 8 item not done as
written, and about two properties of the diagnostics log that change how its output should be read.
The emulator work was on an API 36 AVD
(`docs/audits/2026-09-19-camera-activity-window-report.md:10`).

---

## Known, not blocking

Recorded in the repository:

- **The system draws the status bar over the camera from open until the next relayout.** Untraced.
  Predates this window work and occurs with both windowing shapes
  (`docs/audits/2026-09-19-camera-activity-window-report.md:151`).
- **The reverse-portrait arrangement is unreachable rather than fixed.** The arrangement returns the
  portrait case for any non-landscape window, so a fourth case would be needed if reverse portrait
  ever became reachable. Commented at the branch with exactly what would make it so
  (`CameraArrangement.kt:83-87`).
- **The Diagnostics panel reads only the current log generation**, so a rotation silently hides
  everything written before it and nothing in the panel says so
  (`docs/audits/2026-09-15-diagnostics-panel-hides-the-rotated-generation.md`, recorded not fixed).
- **`saveErrorMessage` carries a success message.** It is the screen's only transient-message
  channel; a parallel field would have threaded through five files to reach the same
  `Toast.makeText` (`MushroomLogViewModel.kt:731-735`).
- **On screens 600dp and wider the platform ignores the orientation request**, so the window can move
  on a tablet or unfolded foldable regardless of the setting
  (`docs/audits/2026-09-16-pr102-device-check-v4.md`, sign-off).
- **"Lock camera to portrait" now pins the capture tag too**, so with it on a landscape shot saves as
  portrait — a difference in the saved file, not only in the layout. Reported, not decided
  (`docs/audits/2026-09-19-reverse-portrait-excluded-report.md:112`).
- **The `JournalTabTest` flake** is unchanged by this PR and has its own audit.

- **`rewritten` is unexercised, not untested.** Every capture in the run read `kept`, because this
  HAL writes the correct tag; the reapply path exists for devices where it does not (run record,
  landing in #105).
- **`ICC_Profile` is absent** from the scrubbed file on a wide-gamut device (same record).
- **`ExifIFD Light Source` survived the allowlist scrub.** Carries nothing identifying, and its value
  is the null one, but something got through an allowlist (same record).

Not settled:

- **Step 11, colour, has not been run.** The scrubbed file carries no `ICC_Profile` on a wide-gamut
  device, and whether that is visible has not been checked. Two checks settle it, in this order: run
  a stock-camera photo of the same subject through exiftool, which separates "this camera writes no
  profile at all" from "ours is losing one" and needs no particular light; then the step's own
  side-by-side, one subject under the same light, in-app against stock. The first decides what a
  flatter in-app photo would mean, and the pair together would give cause and effect, measured and
  observed.
- **API 37 is untested.** The app targets it (`app/build.gradle.kts:245`); no device or working
  emulator image here runs it.

---

## Device check

`docs/audits/2026-09-16-pr102-device-check-v4.md`, with its run record landing in #105. **Run:**
steps 7, 8, 9, 10 and 12. **Not run:** steps 1, 2, 3, 4, 5, 6 and 11 — steps 1, 2, 4 and 5 were
exercised on earlier builds and are deliberately not carried forward, the camera open path having
changed since.

The check was rewritten several times during this PR, and every rewrite came from running it. The
v3-against-the-tree pass found **four corrections, three of them in steps 7 and 12**
(`docs/audits/2026-09-16-device-check-v3-tree-corrections.md`), including one that asked the runner
to search the diagnostics panel for a string only logcat ever shows. Each is superseded in place
with the reason recorded.

---

## Also in this PR, not described above

The branch is 103 commits over 165 files (audit documents and screenshots included) and carries
more than the camera. Not described above, each with its own completion report in `docs/audits/`,
all dated 2026-09-13:

- the fullscreen photo viewer (`PhotoViewerDialog.kt`) —
  `2026-09-13-fullscreen-photo-viewer-completion-report.md`, whose addendum covers album tiles
  opening the same viewer;
- set a find's location at creation — `2026-09-13-find-location-at-creation-completion-report.md`;
- apply EXIF orientation when displaying photos —
  `2026-09-13-exif-orientation-display-completion-report.md`;
- migrations asserted against the committed schemas (`SchemaMigrationTest.kt`) —
  `2026-09-13-schema-migration-tests-completion-report.md`;
- the simpler find form: the seven characteristic sections removed and `LogSectionEditors.kt`
  deleted for want of a caller — owner decision of 2026-09-13, commit `4fcae89`, no separate
  report;
- the "Incomplete" label removed and Notes renamed —
  `2026-09-13-incomplete-label-and-notes-rename-completion-report.md`.
