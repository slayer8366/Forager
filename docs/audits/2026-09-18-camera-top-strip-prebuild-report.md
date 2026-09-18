# Camera top strip: pre-build report, stopped on the dispatch's own condition

**Dispatch:** "the camera top strip, geometry only", written against `e14b649` plus the
hide-status-bar branch. Not in the repository.
**Base:** `5ca296e`, the remote tip of `claude/new-session-vto65i` on 2026-09-18, which includes
`e14b649`.
**Built:** nothing in the app. Emulator screenshots of the camera **as it is today**, with overlays,
are in `img/2026-09-18-camera-top-strip/`.
**Stopped** on "if the layout needs restructuring, stop and report before doing it". Done occupies
the punch-hole edge in two of the three arrangements, and where it goes is not a geometry question.

## Verify-first answers

**1. How `cameraArrangement` expresses the port edge.** Not as an edge. It is one of three
arrangements, chosen once at open from the portrait setting, the window's shape, and the window's
display rotation (`CameraArrangement.kt:84-92`):
- `Portrait` for any portrait window or when the setting is on: shutter at the screen bottom.
- `LandscapePortLeft` for `ROTATION_270`: shutter on the left.
- `LandscapePortRight` otherwise: shutter on the right.

The window is locked while the camera is open (`WindowOrientationLock.kt:88-103`), so a physical
edge maps to one screen side for the whole session. **The punch-hole edge is the opposite of the
shutter's side in each arrangement**: top, right and left respectively. That needs no separate
logic, but it does need a small mapping from arrangement to edge, because no edge value exists
today.

One caveat, pre-existing and not caused here: `Portrait` does not distinguish a window at
`ROTATION_180`. On a 180° window the screen bottom is the punch-hole edge, and the shutter would sit
there. On the AVD a window never reaches 180°: with the virtual phone turned upside down, the
display stayed at its previous rotation (`mRotation` 3 after 90°, still 3 after 180°, 1 after 270°).
So the case looks unreachable, but it is **unverified on the S26 Ultra**.

**2. What hiding the status bar did to the top inset.** Done takes top and start `safeDrawing`
insets (`InAppCameraDialog.kt:248`, `:385`). With the bar hidden, the top inset is the cut-out
alone: 136 px in portrait on the AVD, and 0 in landscape, where the cut-out is on a side. On screen:
- **Portrait:** Done's label at y 247–300, directly below the cut-out band (0–136). That is the band
  the bar used to occupy, and the band a strip below the cut-out would occupy.
- **Landscape:** Done's label at y 111–164, up against the top.

**3. Can the strip compose inside the existing arrangement? Mechanically yes, geometrically no, not
without a decision.** The controls' `Box` (`:238`) takes any aligned child. But Done is
`TopStart` in every arrangement, which puts it on the punch-hole edge:

| Arrangement | Punch-hole edge (screen) | Done (top-left) on that edge? |
|---|---|---|
| `Portrait` | top | **yes**: it sits in the band below the cut-out |
| `LandscapePortRight` (rot 1) | left | **yes**: top-left corner, beside the cut-out inset |
| `LandscapePortLeft` (rot 3) | right | no |

A strip along that edge overlaps Done, or has to route around it, in two of the three. Options for
the owner, none chosen:
1. **Done moves into the strip.** The strip becomes the home of the dismiss control, and Done's
   corner rule changes from "top-left" to "on the punch-hole edge", computed like the shutter's.
2. **Done moves off the punch-hole edge**, for example to the corner opposite the shutter on the
   port edge, and the strip has that edge to itself.
3. **The strip avoids Done's corner** and runs along the rest of the punch-hole edge. It stays small
   in size but asymmetric, and different per arrangement.

Each changes a position v4 step 3 asserts ("Done top-left"), which is the second reason this is not
something to absorb.

## The emulator, and what it can and cannot show

- **It does simulate a cut-out, a centred punch-hole**, matching the reference device's shape. The
  `pixel_7` profile reports `DisplayCutout` insets of 136 px on the top edge, with a bounding rect
  of (480, 0)–(625, 136) on a 1,080 × 2,400 display. In landscape the rect moves with the rotation,
  read from the system rather than derived: rot 1 (0, 455)–(136, 600), rot 3 (2264, 480)–(2400, 625).
  The `...cutout.emulation.hole` overlay is also available and was not needed.
- **`screencap` does not draw the cut-out.** The black boxes in the annotated images are the
  **reported** rects, drawn on afterwards. Done and the shutter are outlined from the accessibility
  tree's bounds (Done's are its label's, not the whole button's), because the emulator's synthetic
  scene puts white text on white. The magenta band is illustrative only: the cut-out inset plus one
  48 dp row, the space a strip might take. No strip exists.
- **Confirmed in passing:** the status bar is hidden and the nav handle is kept, and the landscape
  shutter is vertically centred on the full height (y 446–635 of 1,080, centre 540). That is the
  first on-screen confirmation of the hide-status-bar change's centring correction, on an emulator
  rather than the device.

Setup: AVD `forager_measure36` (API 36 `google_apis` x86_64), `-gpu host`, desktop session.
The APK was built at `5ca296e`. Orientation was set by the emulator's acceleration sensor, and the
camera was opened through the real UI:
- compact layout: Journal → Album → Camera;
- wide layout, in landscape: Photo Gallery → Camera.

| File | Hold |
|---|---|
| `cam_portrait_annotated.png` | portrait, window rot 0 |
| `cam_inverted_annotated.png` | phone upside down, window still rot 0; Done's label turned in place |
| `cam_land_a_annotated.png` | landscape rot 1: port right, punch-hole left |
| `cam_land_b_annotated.png` | landscape rot 3: port left, punch-hole right |
| `strip_baseline_sheet.png` | all four, side by side |

## Not done

Nothing in the app was built: no strip, placeholder, test or v4 item. Those wait on where Done goes.
The cut-out clearance approach (sit below/beside the cut-out, or split around it) is not decided
either. Both are straightforward once Done's place is settled, and the emulator can show either.
