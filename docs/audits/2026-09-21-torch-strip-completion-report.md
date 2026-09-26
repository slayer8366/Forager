# Torch, the first strip control — completion report

**Date:** 2026-09-21 · **Branch:** `strip-torch` · **Base:** `1b82b15`, the head of PR #111
(`strip-housekeeping`), **not `main`**. The dispatch says to branch from `main` after #111 merges,
or from #111's head if it is still open. It was open when this branch was cut (`main` at
`89f53a4`). · **Dispatch:** "torch, the first strip control" · **Decision record:**
`2026-09-21-camera-strip-basics-decisions.md` (B2, plus B6 added here).

**One-paragraph outcome.** The strip has its first control: a flash chip whose only working mode is
torch, Off and Torch, cycled by a tap. The chip is hidden on a camera with no flash unit, and
nothing is stored. The session interface gained `hasFlashUnit`, `flashMode` and `setFlashMode`, the
fake carries the same contract, and CameraX drives `enableTorch` through the bound camera. `close()`
turns a lit torch off and resets the mode. The placeholder and its gate are retired, and the strip
is now a container of chips. Camera test set: **18 classes, 101 tests → 21 classes, 114 tests,
0 failures**, both runs fresh. Every test failed first against a skeleton. **17 one-line reverts
and 1 positive control** each failed with a message specific to their own edit, on a clean
compile. One migrated assertion can't be proven on its own, and says why. Nothing here has run on
a device.

---

## Verify before building (at `1b82b15`)

| # | Claim | Found | Holds |
|---|---|---|---|
| 1 | Interface is state, deviceRotation, open, close, capture | `CameraCaptureSession.kt:27, 36, 48, 55, 64` | yes |
| 2 | Bound `Camera` kept and unread; class doc names torch and `hasFlashUnit` | `CameraXCaptureSession.kt:170–171`, `:145` | yes |
| 3 | Fake: state and deviceRotation observable, no flash | `FakeCameraCaptureSession.kt:34, 37` | yes |
| 4 | One strip slot; placeholder gated | slot `CameraBands.kt:138`, Row/Column boxes `:148`/`:156`, `SHOW_STRIP_PLACEHOLDER` `:189` | yes, **one line later than the pulse** |
| 5 | `OverlayIcon`/`OverlayText` carry the outline rule | rule stated `CameraOverlay.kt:37–38`; `OverlayText` `:67`, `OverlayIcon` `:86` | yes |
| 6 | `rotateWithDevice` at `RotateWithDevice.kt:54`, used by the placeholder label | `:54`; label at `CameraBands.kt:183` | yes, **one line later than the pulse** |
| 7 | The three comments #111 left | see below | two stale, one holds |
| 8 | Decision record lists torch in the flash chip | `2026-09-21-camera-strip-basics-decisions.md:24`, B2 | yes |

**The one-line shift in 4 and 6** is the line #111 added at `CameraBands.kt:116–117`. The pulse
read `main`; this branch sits on #111. The content is identical, so this was not treated as a
difference that stops the work.

**Step 7.**
- `:112` ("the `Dialog`'s `onDismissRequest`") is past tense and accurate as history. Unchanged.
- `:115` ("the status bar returns with the dialog's window") was **stale**. The bar is restored on
  the Activity's window when the camera leaves composition (`CameraWindowChrome.kt:125`, called at
  `InAppCameraDialog.kt:201`, tested at `InAppCameraDialogTest.kt:445`). Rewritten.
- `:134`/`:135`: the **reason** it gave was stale. "`currentDisplayRotation()` goes stale inside a
  Dialog" is the 2026-09-18 finding (`80855c3`, `RotateWithDevice.kt:94–104`), and the camera has
  not been a Dialog since 2026-09-19. Passing the value is kept: it is the one reading the
  arrangement also uses (`InAppCameraDialog.kt:187`). The comment now says the reason is history.
  It cites the dialog's own comment (`InAppCameraDialog.kt:275–281`), which records that the local
  invalidates now; this session did not measure that.

---

## Commits

| # | Commit | What | Dispatch step |
|---|---|---|---|
| 1 | `adf0315` | Two stale comments; decision record B6 added | 1 |
| 2 | `c7a7863` | Interface: `hasFlashUnit`, `flashMode`, `setFlashMode`, `FlashMode { Off, Torch }` | 2 |
| 3 | `b72c6a9` | Fake: unit on open, call count, the contract | 3 |
| 4 | `643476f` | CameraX: `enableTorch` through `installFlash`; torch off and reset on close | 4 |
| 5 | `a574862` | `FlashChip` (standalone) | **6** |
| 6 | `66eb22f` | Chip container; placeholder retired; chip placed; tests migrated | **5**, plus placement |

**Steps 5 and 6 were swapped, on purpose.** Retiring the placeholder first would have left the strip
empty until the chip existed. About eight dialog and landscape tests measure the strip, so they
would have failed in between, or needed throwaway interim code. Building the chip first means every
commit is green.

---

## Evidence: failing first, then the revert check, per commit

Each test was run against a skeleton (right signatures, missing behaviour) and failed on an
assertion, not on a compile error. The revert runner saves a copy of the file, applies a one-line
edit, and checks the build log. It **refuses to cite results if the log has compile errors**. It
restores from the saved copy, not from git, and confirms the file is byte-identical to the forward
version (both CLAUDE.md revert rules).

**Commit 2** (`CameraFlashSessionTest`). Failed first: *"the fake reports what it was told
expected:<Torch> but was:<Off>"*. Revert (drop the fake's assignment): the same message.

**Commit 3** (`CameraFlashSessionTest`, +2 tests). Failed first: *"the bound camera's unit is
reported once open expected:<true> but was:<false>"*, *"the request was made expected:<1> but
was:<0>"*. The full camera set then ran at 19 classes, 104 tests, 0 failures, so the 101 existing
tests pass unchanged with the new fake. Reverts:
- unit on open removed → *"…reported once open expected:<true> but was:<false>"*, plus commit 2's
  test *"expected:<Torch> but was:<Off>"*. This revert can cause that second failure: with no unit
  reported, the guard refuses Torch.
- counter removed → *"the request was made expected:<1> but was:<0>"*
- no-unit guard removed → *"and no mode the hardware cannot be in expected:<Off> but was:<Torch>"*
- reset on close removed → *"torch does not persist past close expected:<Off> but was:<Torch>"*

Commit 2's test now opens the session before setting a mode, because commit 3's contract says
there's no unit before open.

**Commit 4** (`CameraXCaptureSessionFlashTest`). Failed first: *"Torch lights the torch
expected:<[true]> but was:<[]>"* and *"expected:<[true, false]> but was:<[]>"*. **Two of its four
tests passed on the stub as well**, because the stub refuses everything. The reverts below are what
prove those two. Reverts:
- torch-off in `close()` removed → *"close turns the torch off expected:<[true, false]> but was:<[true]>"*
- mode reset in `close()` removed → *"torch does not persist past close expected:<Off> but was:<Torch>"*
- guard narrowed to `switch == null` → *"the torch was not asked expected:<[]> but was:<[true]>"*.
  Removing the whole guard would break the smart cast on `switch` three lines down, which is the
  CLAUDE.md trap; narrowing it compiles.
- unit initially `true` → *"expected:<false> but was:<true>"* (before bound)

**Commit 5** (`CameraFlashChipTest`). All four failed first against a chip that was always shown,
did nothing on tap, and always showed Off. Reverts:
- no-unit guard removed → both absence tests: *"Did not expect any node but found '1' node"*
- tap asks for the same mode → *"Off to Torch expected:<Torch> but was:<Off>"*
- glyph pinned to Off → *"(ContentDescription = [Torch on]) … '[Flash off]'"*
- `next()` Torch → Torch → *"Torch to Off expected:<Off> but was:<Torch>"*

**Commit 6.** Seven tests failed first against a skeleton (the container never checks for empty,
the dialog passes no chips, the semantics report 0): the container and no-unit tests with *"Did not
expect any node but found '1' … in-app-camera-strip"*, and five dialog/landscape tests with
*"could not find any node … in-app-camera-flash-chip"*.

The first green attempt had **one failure that didn't match the prediction**. The setting-on test
reported *"chip unturned at sensor 0 expected:<0.0> but was:<-90.0>"*. That was my test being
wrong. The original claim is stillness, and the chip's angle is a steady −90 before the flip (the
window is at `ROTATION_90`, and the gate pins the sensor to `ROTATION_0`); the count is held turned
by the same amount, which is why its bounds never moved. The test was corrected to "the angle is
unchanged from its first reading", before and after the flip.

Reverts:
- container empty check removed → CameraStripTest no-chips, and dialog no-unit: *"Did not expect
  any node but found '1' … in-app-camera-strip"*
- dialog adds the chip without the unit check → dialog no-unit: the same message (the band stays
  behind a chip that hides itself)
- strip hands its chips `deviceRotation = null` → *"a quarter turn back expected:<-90.0> but
  was:<0.0>"*
- semantics report a constant 0 → the same message
- **positive control, not a revert**, for the real-touch test. No line of this change is what
  keeps the chip uncovered, so there's nothing to revert. A full-screen pointer catcher was added
  over the camera, and the test failed with *"touch 1 at Offset(24.0, 24.0) reached the chip
  expected:<1> but was:<0>"*. The control's first attempt did not compile, and the runner refused
  to cite the stale results.

**Not independently provable: the setting-on test's chip assertion.** The only input to the chip
that varies is the gated `deviceRotation`. Any revert that turns the chip also turns the count, and
the count's assertion comes first. The chip assertion is kept because it's correct and cheap, but
it isn't counted as evidence.

## Suite counts

| Run | Commit | Classes | Tests | Failures | Skipped |
|---|---|---|---|---|---|
| Camera set (`--tests '*Camera*'`), branch point | `1b82b15` | 18 | 101 | 0 | 0 |
| Camera set, branch head | `66eb22f` | 21 | 114 | 0 | 0 |
| Full unit suite, branch head | `66eb22f` | 205 | 1601 | 0 | 24 |

The full suite was run at the head only, not at the branch point; its 24 skips are pre-existing
`@Ignore`s, and none was added. Both camera-set runs deleted the results directory first, so no XML from an earlier run could be
read. Neither build log has compile errors. The +13 tests are 3 in `CameraFlashSessionTest`, 4 in
`CameraXCaptureSessionFlashTest`, 4 in `CameraFlashChipTest`, 1 in `CameraStripTest` (column) and
1 in `InAppCameraDialogTest` (real touch). The filter matches on method names too, so it picks up
four classes that aren't camera classes (`DiagnosticsPanelTest`, `CartographyScreenTest`,
`PhotoGalleryScreenTest`, `CameraTargetRecenterGuardTest`), the same way at both ends.

---

## What could not be tested, and why

- **The CameraX bind.** Robolectric has no camera provider, so the `installFlash` call inside
  `open()`'s bind, the `cameraInfo.hasFlashUnit()` read, `enableTorch` lighting the LED, and the
  failure handler resyncing from `torchState` have never run. The session's own decisions are
  tested through `installFlash`; the camera's side isn't.
- **Torch off before `unbindAll`.** `close()` asks for the torch off first, but the order it
  actually happens in on a device can't be seen without a bound camera. The dispatch's close
  assertion ("closing with torch on leaves `flashMode` at `Off`") **is** unit-tested. The LED going
  out is device step 4.
- **Torch surviving a window turn.** A turn doesn't recreate the Activity
  (`AndroidManifest.xml:160`, `configChanges` includes `orientation`), so the session and the torch
  should survive. That's inferred, not observed.
- **Insets.** Robolectric reports zero, so whether the chip clears the cut-out is device-only
  (CLAUDE.md).

## Device-only, cheap to costly

Pass condition and evidence per step. Observations are not gates. **Step 3 is corrected from the
dispatch.** The dispatch said the glyph "rotates in place with the lock setting on". With the
setting on, the gate pins the rotation and **nothing turns** (owner, 2026-09-18;
`effectiveDeviceRotation`; the setting-on landscape test). Written as the code behaves, both ways:

1. Phone with a flash unit, open the camera. **Pass:** the flash chip is visible in the strip,
   showing Off (crossed bolt). **Evidence:** screenshot.
2. Tap it. **Pass:** the torch lights and the glyph changes to the flashlight. **Evidence:** photo
   of the phone, and a screenshot.
3. Torch on, turn the phone to landscape.
   **3a, setting off:** the torch stays lit, and the glyph reads upright once the window has
   turned. **3b, setting on:** the torch stays lit, and the glyph doesn't turn.
   **Evidence:** a landscape screenshot for each.
4. Close the camera (Back) with the torch on. **Pass:** the torch is off within a second.
   **Evidence:** observation, with the time noted.
5. Reopen the camera. **Pass:** the torch is off and the glyph shows Off. **Evidence:** screenshot.
6. A device or emulator with no flash unit (the API 36 AVD likely has none; unverified), open the
   camera. **Pass:** no flash chip and no strip. **Evidence:** screenshot.
7. *Observation, not a gate:* torch brightness, and whether it makes gills readable at 10 cm.
8. *Observation, not a gate:* the camera timeout (four minutes) with the torch on. It closes the
   camera, so the torch should go out with it. Not asked for; listed because close is the only
   thing that turns it off.

---

## Conventions

Reference apps: **Google Pixel Camera, Apple iPhone Camera, Samsung Camera, Open Camera.** What each
app does is the owner's reading, relayed in the dispatch; this session didn't check it.

| Convention | Source | This build |
|---|---|---|
| Torch lives inside the flash chip | Samsung, Open Camera (record B2) | **Followed** |
| No flash unit, no flash chip; none of the four shows a dead control | all four | **Followed** (no chip, and no strip when nothing else is in it) |
| Torch doesn't persist; all four reset it when the camera closes | all four | **Followed** (reset in `close()`, nothing written to DataStore) |

**Glyphs (open item).** `Icons.Filled.FlashOff` for Off and `Icons.Filled.FlashlightOn` for Torch.
They come from `material-icons-extended`, which the app already ships (`app/build.gradle.kts:487`).
Open Camera's own icons for these states are a crossed bolt and a flashlight. That mapping is from
memory, not checked against Open Camera's source.

## Decided beyond the dispatch

- **Steps 5 and 6 swapped**, for the reason above.
- **`GatedFakeCameraCaptureSession` touched.** The dispatch doesn't name it. It delegates to the
  fake and wouldn't compile without the three new members, so it delegates them too.
- **Tests migrated beyond `CameraStripTest`.** Retiring the placeholder also retired what five
  dialog and landscape tests asserted on: its tag, and its label's bounds. Each now asserts the same
  claim on the chip. None was weakened, disabled or skipped.
- **A semantics property on the shared `rotateWithDevice`** (`RotateWithDeviceTarget`, the settled
  target angle). The chip is square, so the placeholder's extents check, moved onto the chip, would
  pass on a chip that never turned. This is the CLAUDE.md "a check that never saw the data that
  could fail it" family, caught before it was written. **It also puts that property on the count
  and error nodes**, which use the same modifier. Their layout and drawing are unchanged; the full
  camera set is green. The dispatch said not to change the count, so this is disclosed here.
- **`setFlashMode` failures are logged and resynced** from `cameraInfo.torchState`. A superseded
  request (CameraX cancels a pending `enableTorch` when a newer one arrives) is logged as such, not
  as a failure. Not asked for. It exists so the chip never shows a torch the camera isn't running
  (CLAUDE.md, no silent failure). Device-only.
- **The chip starts at the strip's start** (left in portrait, top along a vertical edge). The
  dispatch doesn't place the chip along the strip. Changing it is one `Arrangement` argument.

## Premises that were wrong

- **Device step 3 as dispatched** (the glyph rotating with the setting on). Corrected above.
- **Line numbers in verify steps 4 and 6**: one line early for this base. Explained above; the
  content matched.

## Constraints, checked

No Auto or On mode, not even as enum values (`FlashMode` is `Off, Torch`). Nothing persisted:
there's no DataStore or Room change in the diff. Location, grid, level, capture rotation and the
lock default aren't in the diff. The shutter band isn't in the diff. The count's code isn't in the
diff; it gains a semantics property through the shared modifier, disclosed above. #111's record is
not edited in place; B6 is appended below it. Nothing is restored from the withdrawn PR or from any
copy outside the tree.
