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
and their EXIF coordinates are read into the record (`:137`). The strip toggle is a camera
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
