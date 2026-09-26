# Camera strip basics: owner decisions

**Date:** 2026-09-21
**Base:** `origin/main` at `89f53a4`; recorded on branch `strip-housekeeping`
**Status:** decisions recorded. No control built. The strip still holds only the gated placeholder
(`SHOW_STRIP_PLACEHOLDER`, `CameraBands.kt`), unchanged by this record.

The owner made these in conversation on 2026-09-21. Until this file they existed nowhere in the
tree. The torch dispatch that follows cites this record, so it is written down before that work
starts.

**Reference apps.** Each decision is checked against four camera apps: Google Pixel Camera, Apple
iPhone Camera, Samsung Camera and Open Camera. What each app does, as stated below, is **the
owner's reading, relayed in the dispatch. This session has not re-checked it** and has no way to
from a repository.

---

## Decisions

| ID | Decision | Convention | Followed or departed |
|---|---|---|---|
| **B1** | **Portrait lock stays a setting, default off.** | Pixel, iPhone, Samsung and Open Camera lock the layout. | **Departed**, decided earlier; this record does not reopen it. |
| **B2** | **Torch lives inside the flash chip.** | Samsung and Open Camera. | **Followed.** |
| **B3** | **A save-location toggle on the strip mirrors the existing "Automatically Save Location to Photos" setting.** | All four keep location in settings only. | **Departed**, so that the state is visible before sharing. |
| **B4** | **ADR 0003 is free for new work.** | — | — |
| **B5** | **Order of the basics:** torch and flash, then grid and level, then save-location. RAW waits for the pipeline. | — | — |

### B1. Portrait lock: a setting, default off

The default is in the code: `DEFAULT_LOCK_CAMERA_TO_PORTRAIT = false`
(`DataStoreCameraOrientationPreferenceRepository.kt:37`), surfaced as Settings' "Lock camera to
portrait". The departure from the four reference apps was decided on 2026-09-19. The prior
record is:

- `docs/audits/2026-09-19-unlock-seamless-rotation-prebuild-report.md`
- `docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`

Those reports stand as written. This record cites them and does not reopen the decision.

### B2. Torch inside the flash chip

Torch is a mode of the flash chip rather than a chip of its own, as in Samsung Camera and Open
Camera.

**Alternative considered and rejected for now:** a separate torch chip, which would give
one-tap lighting under a cap. Rejected to keep the strip small. A later record can supersede
this one if field use shows the extra tap costs more than the extra chip.

### B3. Save-location toggle: a mirror of the setting, and not a geotag

The strip toggle shows, and sets, the same preference as the Settings checkbox "Automatically
Save Location to Photos" (`PhotoLocationPreferenceRepository`). **It is a mirror, not a per-shot
override.** Flipping it on the strip flips the setting, and the reverse is also true. There is
only one state.

**Why depart.** All four reference apps keep location in settings only. The strip shows the state
where the photo is taken, so the user knows before sharing whether a position was recorded.

**Wording rule: it is not a geotag.** No comment, label or record may call it one, because
Forager does not write location into the photo. What it does, as read at `89f53a4`:

- **The position is a GPS fix taken after the photo is saved, and it lives in the app's Room
  record beside the photo, not in the file.** `requestAndPatchCaptureFix` checks the setting
  first. When the setting is off, no position is requested at all (`MushroomLogViewModel.kt:830–837`).
  When it is on, the function asks the location provider and patches the fix onto the photo row
  that already exists (`:839–840`).
- **A camera capture carries no location EXIF when stored.** The in-app camera never sets
  `ImageCapture.Metadata.location` (`CameraXCaptureSession.kt:149–153`). `FilePhotoStore`
  scrubs metadata on persist anyway (`FilePhotoStore.kt:132`).

**Scope correction to the claim as dispatched.** The dispatch says "all location EXIF is
scrubbed before storage". That is true of **camera captures only**. The scrub at
`FilePhotoStore.kt:132` is gated on `CameraCapturePhotoSource`. Per the owner's earlier
instruction, **photos imported from the gallery stay untouched** (`FilePhotoStore.kt:125–127`),
and their EXIF coordinates are read into the record (`:138`). The strip toggle is a camera
control, so the rule above holds for it. It does not describe imports.

### B4. ADR 0003

`docs/adr/` holds `0001-motion-precedence.md` and `0002-motion-scheme-adoption.md` at `89f53a4`.
The next number, 0003, is free for new work. No earlier draft under that number is revived, and
no copy kept outside this tree is a source for it.

### B5. Order

1. Torch and flash (one chip, B2).
2. Grid and level.
3. Save-location (B3).

RAW waits until the capture pipeline can carry it. It is not scheduled here.

---

## Added 2026-09-21, torch dispatch: the owner's location model (B6)

Added below the original entries, which are not edited. Extends B3; supersedes nothing.

**B6. Where a fix goes, as the owner states it.** A GPS fix is stored in the app's own record.
It is applied to the photo only if the user places the photo in the Journal. Otherwise it never
leaves the app. The save-location dispatch cites this entry.

**What this session checked, at `1b82b15`:**

- **No code in `app/src/main` writes GPS into an image file.** A search for `TAG_GPS`,
  `setGpsInfo` and `setLatLong` finds nothing. So "applied to the photo" is not a geotag (B3's
  wording rule stands). Whatever it means, it happens in the app's records, not in the file.
- **What the flag gates.** `PhotoLocationPreferenceRepository.kt:16–19` lists three automatic
  captures: the fix patched onto a camera photo's row, that fix promoted to the find's `foundAt`,
  and the fix a find started from the Journal takes when it is created.

**Not traced, so unverified:**

- Whether every path that takes a camera photo lands it in a Journal entry, or whether a photo's
  row can hold a fix with no entry. The model's "only if placed in the Journal" is recorded as the
  owner's statement, not matched against the camera's entry points.
- Whether any export or share path (for example GPX, or sharing an entry) carries a stored fix
  out of the app. "Never leaves the app" is recorded as the owner's statement.
- **The Journal boundary for gallery imports.** Imports keep their own EXIF, coordinates included
  (`FilePhotoStore.kt:125–127`), and those coordinates are read into the record (`:138`). A file
  the user brought in already carries its position, whether or not it is placed in the Journal, so
  the model above describes camera captures. Whether it is meant to cover imports as well is the
  owner's to say.

---

## Added 2026-09-22, grid-and-level dispatch: grid mode persists, level always shown, crosshair deferred (B7)

Added below the entries above, which are not edited. Cites the dispatch "grid and level" (written
2026-09-22). Extends B5's second item; supersedes nothing.

**B7. One chip cycles Off, Grid, Grid + Level.**

| | Decision | Convention (planner's recollection, unverified) | Followed or departed |
|---|---|---|---|
| **B7a** | **The grid mode persists across camera sessions and app restarts**, in DataStore, default Off. Unlike torch (B2, and the torch dispatch), which resets on close. | All four keep the 3 by 3 grid as a setting. | **Followed** |
| **B7b** | **With Grid + Level on, the level line is always shown**, not only near level. | All four show the level only within a few degrees of horizontal or vertical, then hide it. | **Departed.** A forager lining up a scale card wants to see how far off they are, not only when they arrive. Can be superseded. |
| **B7c** | **The grid is not rotated with the glyphs.** It divides the preview into thirds whichever way the phone is held. | None of the four rotates the grid. | **Followed** |
| **B7d** | **A flat, top-down crosshair (for shooting a cap from above) is deferred** to its own dispatch. The device check's observation of the horizon line with the phone held flat is the input for it. | — | — |

Persisting across restarts is a separate, per-case decision under CLAUDE.md's UX defaults. B7a
is that decision for the grid mode, made by the owner in the dispatch.

---

## Added 2026-09-26, strip Part B dispatch: flash on capture, a self-timer, the save-location chip (B8)

Added below the entries above, which are not edited. Cites the build dispatch preserved as
`prompts/preserved/2026-09-26-28.md` (intent `2026-09-26-81` in `RECORD.md`) and the planner's
rulings on its one stop (planner log line 863, quoted verbatim in that intent). Extends B2 and
B5; builds B3 as written; supersedes nothing.

**Whose decisions these are.** The owner's rulings, 2026-09-26, quoted by the dispatch: *"finish
the camera to basic functionality. that means populate the camera strip with basic camera tools.
dealer's choice on what they are, since they will change in the near future anyway."*; "Land the
stack, then extend" and "Use the recorded plan" (flash on capture, a self-timer and save-location,
as the abandoned `2026-09-26-02` dispatch named them); *"merge into pre main without device check.
defer device check for later"*; "One coder, three features in a row". **Everything in B8a to
B8f below is the planner's choice under that delegation**, not an owner ruling. B1 to B7 stand.

| | Decision (planner, under the owner's delegation) | Departs from the abandoned -02 plan? |
|---|---|---|
| **B8a** | **Strip order Flash, Timer, Grid, Location.** Flash hidden without a flash unit, as before; Timer and Location always shown. | No. |
| **B8b** | **Flash on capture joins torch in the one flash chip** (extends B2). `FlashMode` is Off, Auto, On, Torch, a tap cycling in that order. Each mode sets the torch and the `ImageCapture` flash mode: Off (torch off, `FLASH_MODE_OFF`), Auto (off, `FLASH_MODE_AUTO`), On (off, `FLASH_MODE_ON`), Torch (on, `FLASH_MODE_OFF`). One session only: reset to Off on close, the torch-off-before-unbind order unchanged. No flash unit: refused and logged, as before. The torch-failure resync keeps "the reported mode matches the hardware" and never overwrites a successful change to Auto or On. Glyphs `FlashOff` "Flash off", `FlashAuto` "Flash auto", `FlashOn` "Flash on", `FlashlightOn` "Torch on". | **Yes, one case dropped:** -02 required "a mode set before install is applied on install". See below. |
| **B8c** | **Self-timer chip**, Off, 3 s, 10 s, one session only, default Off, held by the camera screen. A shutter press with the timer set starts a countdown; the capture happens once at zero, through the existing capture path unchanged. Large centred numerals, upright under `rotateWithDevice`, no pointer input. A second shutter press cancels with no capture, the shutter reading "Cancel timer" meanwhile; Back cancels and closes as before. Changing the timer mid-countdown affects the next press only; the photo count moves only on an actual capture; flash applies at the capture. Glyphs `TimerOff` "Timer off", `Timer3` "Timer 3 seconds", `Timer10` "Timer 10 seconds". | **Yes, where the countdown lives.** See below. |
| **B8d** | **Location chip: the B3 mirror.** Shows and sets "Automatically Save Location to Photos", one state, no per-shot override. Threaded the way `lockToPortrait` is: `uiState.autoSaveLocationToPhotos` and `onAutoSaveLocationToPhotosChanged`, from `AvailabilityScreen` through `InAppCameraHost`, `InAppCameraSlot` and `CameraXInAppCamera` to `InAppCameraDialog`, as required parameters; it never writes the repository. Content description "Save location: On" / "Save location: Off", and per B3's wording rule it is not called a geotag anywhere. Glyphs `LocationOn`, `LocationOff`. The handler's optimistic update is known and left unchanged. | No. |
| **B8e** | **Not built:** pinch zoom and zoom buttons, tap to focus or meter, exposure, a lens switch, RAW, aspect ratio, a last-shot thumbnail, and B7d's crosshair. | No. |
| **B8f** | **Glyph availability:** a named icon that did not resolve at compile time would have stopped the build, with no substitute. All nine resolved: eight from `material-icons-extended`, `LocationOn` from `material-icons-core`, both already on the classpath (planner ruling 5). | No. |

**Why the countdown is not in the ViewModel (B8c's departure).** -02 put it "in the camera's
ViewModel, or in a plain Kotlin class the ViewModel owns". This build holds a plain Kotlin
`CaptureCountdown` (package `photo`, no Compose or Android UI imports, testable headless under
virtual time) in `InAppCameraDialog`, with `remember`, on the dialog's own
`rememberCoroutineScope()`. The reasons, from the pulse the dispatch cites:
`InAppCameraViewModel` is Activity-scoped (`MainActivity.kt:147` at `2753a83`; the pulse said
`:148`) and is not passed to the
dialog; a ViewModel-held countdown would survive Back unless every exit cancelled it explicitly;
and hoisting it would touch `MainActivity`, `AvailabilityScreen`, the host and every slot lambda.
Held by the dialog, anything that removes the dialog cancels its scope and the countdown with it.

**Why "a mode set before install is applied on install" was dropped (B8b's departure;
planner ruling 3).** It cannot happen in production. `open()`'s bind calls `installImageCapture`
(`CameraXCaptureSession.kt:280` at `2753a83`) before `installFlash` (`:282`), and `setFlashMode`
refuses until `installFlash` has run (`:451`). Per CLAUDE.md's "check reachability before
measuring behaviour" it is not tested, and per "no speculative logic" no code carries a
pre-install mode. It is replaced by two cases: (a) after the bind, the `ImageCapture`'s flash
mode is `FLASH_MODE_OFF`; (b) On, then close, then a new bind: the session reports Off and the new
`ImageCapture` is `FLASH_MODE_OFF`. **Their `ImageCapture` halves cannot fail**, because
`FLASH_MODE_OFF` is a fresh `ImageCapture`'s own default; (b)'s session half can. What does
evidence the mapping is the four-row table test and its revert check (completion report). Under
Robolectric `open()` binds nothing, so "after `open()`" is modelled by the bind's own two calls,
`installImageCapture` then `installFlash`, in that order.

**Existing tests whose expected values changed, because the behaviour changes on purpose.** None
was silenced or weakened; each is named in the commit that changed it.

- `InAppCameraDialogTest`, the real-touch flash chip test: "five taps from Off end on Torch"
  became "five taps from Off end on Auto" (B8b's cycle). Commit `cd38624`.
- `CameraFlashChipTest`: `a tap asks the session for the next mode, Off to Torch to Off` (two
  taps) renamed `... Off to Auto to On to Torch to Off` and extended to four taps, each asserting
  the mode and the glyph (B8b; planner ruling 2). Commit `cd38624`.
- `InAppCameraDialogTest`: `with no flash unit there is no flash chip, the grid chip comes first,
  and the shutter is where it was` renamed `... the timer chip comes first and the grid chip
  second, ...`. It asserts the Timer chip in the first slot and the grid chip in the second, each
  by its offset along the strip against `STRIP_ROW_HEIGHT`, as precise as the old first-slot
  check. The no-flash-chip and shutter assertions are unchanged. This is the planner applying
  B8a, made under the owner's delegation (planner ruling 1). Commit `ca34239`.

**Unverified, and a device item:** whether CameraX honours an `ImageCapture` flash mode while
the torch is lit. It does not arise in this build, since Torch's capture flash is
`FLASH_MODE_OFF`, but whether Torch plus a capture fires the flash is on the device list.
