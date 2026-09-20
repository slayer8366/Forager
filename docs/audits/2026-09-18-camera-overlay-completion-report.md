# Camera overlay: the strip, the bands, the hidden bar and the contrast rule — completion report

**Date:** 2026-09-18 · **Branch:** `claude/new-session-vto65i` · **Base:** `6bbd90b` (section 0 at
`ee59e81`) · **Spec:** "Camera overlay: build specification" (fresh build; nothing from any other
branch cited) · **Pre-build:** `2026-09-18-camera-overlay-prebuild-report.md`.

**One-paragraph outcome.** Built what section 0 found missing and left the rest alone: a named
port edge and its opposite; a region model of a full-bleed viewfinder and two zero-thick bands;
the strip on the punch-hole edge with Done as its first resident and a gated placeholder; the
status bar hidden on the dialog's own window with the safe area collapsed; and one definition of
the contrast rule replacing nine styling sites. Suite 1568 → 1581 tests (200 → 202 classes),
0 failures, 24 skipped unchanged. Eight revert checks, seven biting with edit-specific messages,
**one that fails nothing and is not counted**. Emulator: all four holds and both landscapes opened
fresh, status bar hidden while open and back after Back, cut-out cleared, outline read over pure
white; one misreading of a system indicator caught by a counterfactual build and recorded.

---

## What was built, against the spec's rules

| Rule | Where | Note |
|---|---|---|
| 1 shutter on the port edge | `CameraArrangement.kt` `ScreenEdge`, `portEdge()`; `InAppCameraDialog.kt` `CameraBand(edge = port)` | the edge the three arrangements always implied, now a value |
| 2 strip on the punch-hole edge | `punchHoleEdge()` = `portEdge().opposite`; `CameraBands.kt` `CameraStrip` | **cut-out: the strip sits inboard of it** — its band pads by `WindowInsets.displayCutout` on its own edge only |
| 3 glyphs rotate | unchanged for the count/error; `rotateWithDevice` on Done and the placeholder label | one rule, every arrangement |
| 4 status bar hidden on the dialog's window | `CameraWindowChrome.kt` | exits enumerated below; nav bar untouched; swipe reveals by `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` |
| 5 safe area collapses | `InAppCameraDialog.kt`: the `safeDrawing` wrapper is gone | each band pads by cut-out ∪ nav bar on its own edge; a left/right band takes no top/bottom inset, so the landscape shutter is on the true screen centre |
| 6 region model | `CameraBands.kt` `CameraBand(edge, thickness = 0.dp)` | one ratio, no setting |
| 7 no background | `CameraStrip`, `StripPlaceholder` | outline only |
| 8 contrast | `CameraOverlay.kt`: `OverlayText`, `OverlayIcon`, `OverlayProgress`, `overlayRing` | **nine sites → one definition**; the dialog now names no colour except its black background, and passes `colorScheme.error` as a *fill* to `OverlayText`, which is the rule |
| 9 empty strip | `CameraStrip` returns before composing when it has neither Done nor content | tested at the component |
| 10 placeholder | `StripPlaceholder`, **gated by `SHOW_STRIP_PLACEHOLDER`** — one edit, `false`, removes it | label "Strip"; ships or not is the owner's call after seeing it |
| 11 no working controls | none added | Done pre-existed |

**Done changed, reported in section 0 before it was moved.** It is the strip's first resident, and
**an icon (outlined ✕, content description "Done"), not the labelled button it was**: a 58 dp
labelled button does not fit a strip one control row deep along a vertical edge without widening
the strip past the size the spec names. Its position moved in one arrangement (`LandscapePortLeft`:
screen left → right, i.e. onto the punch-hole edge where the other two already had it).

**The status bar's exits, from the code.** Done (`onDismiss`); Back and outside-touch
(`Dialog.onDismissRequest`); the four-minute absence timeout (`InAppCameraViewModel.onReturnedToApp`
→ `close()` → `InAppCameraHost` composes nothing); the Activity going away. All four destroy the
dialog's window, which is the only window the bar was hidden on, so there is no restore path to
miss — including the timeout, which the spec named as the one a restore design would forget.

**Said plainly, as the spec required: the band model has no second case to test.** Both bands are
zero-thick at the one ratio, so "inside the band" and "at the edge" coincide and every band test
is an edge-anchoring test. The structure is correct by construction and by review. The tests that
can fail — shutter on the port edge, strip on the punch-hole edge, every rotation — are the
evidence, and they are listed next.

## Evidence

**Tests (13 new; 1568 → 1581).** In device anatomy throughout:
- `CameraEdgesTest`: the port edge at all four rotations, the punch-hole edge as its opposite for
  every arrangement, never the same edge, `opposite` an involution.
- `InAppCameraDialogTest` (portrait): strip flush with the top, full width, one row deep, Done in it;
  shutter bottom-centre; **the status bar hider is asked once, with the dialog's window
  (`ShadowDialog.getLatestDialog().window`) and never the Activity's**; with no strip content the
  placeholder is absent and the shutter is where it was; outlined text is one semantics node.
- `InAppCameraDialogLandscapeTest`: strip flush with the left edge at `ROTATION_90` and the right
  at `ROTATION_270`, full height, one row deep, opposite the shutter; Done on the punch-hole edge
  in both; the placeholder label turns in place. Rotations pinned through `ShadowDisplay` with the
  precondition that the harness reports the pin (`139727a`'s convention).
- `CameraStripTest`: the empty strip composes nothing; with content it is one row deep.

**Revert checks.** Each against a copy saved before editing, restored from that copy, compile
errors counted in the build log before results were read, and every forward file confirmed
byte-identical to its saved copy afterwards.

| # | Reverted | Compile errors | Failures | Message |
|---|---|---|---|---|
| A | punch-hole edge = port edge | 0 | 6 | `Portrait expected:<Top> but was:<Bottom>`; `flush with the punch-hole edge expected:<0.0> but was:<592.0>` |
| B | status bar not hidden | 0 | 1 | `asked once expected:<1> but was:<0>` |
| C | empty strip composes | 0 | 1 | `Did not expect any node but found '1' node … in-app-camera-strip` |
| D | stroked text keeps semantics | 0 | 6 | `Expected exactly '1' node but found '2' nodes` (also broke four existing tests, which is the point) |
| **E** | **shutter padding before its tag** | 0 | **0** | **fails nothing — not evidence.** Placing the padding after `semantics` but before `testTag` does not shrink the tagged node, because the tag merges into the semantics node created earlier; this revert does not reproduce the original defect. The shutter-position guard's evidence is the *live* pre-fix failure, `port edge (right at ROTATION_90) … expected:<624.0> but was:<622.5>`, and revert H below. |
| F | placeholder label does not turn | 0 | 1 | `a quarter turn swaps the extents expected:<4.5> but was:<18.0>` |
| G | bands ignore their edge | 0 | 2 | `flush with the punch-hole edge expected:<0.0> but was:<423.0>` |
| H | port-edge mapping inverted | 0 | 9 | `port edge (right at ROTATION_90) … expected:<624.0> but was:<88.0>`; portrait `expected:<455.0> but was:<132.0>` |

**Suite:** 202 classes, 1581 tests, 0 failures, 24 skipped — on the final code, after the last edit.

**One baseline run discarded and recorded.** An attempt to run the landscape class on the pre-change
code used `git stash`, which does not stash untracked files; the "base" build did not compile, my
compile-error grep missed the `-i` log's format, and the "10 tests, 4 failures" it printed was my
own earlier XML read back. Exactly the stale-artifact failure in CLAUDE.md, caught by reading the
log by eye. Nothing from it is cited.

## Emulator (API 36, `forager_measure36`, `-gpu host`, desktop session)

**The AVD simulates a centred punch-hole**, confirmed before anything was claimed: `DisplayCutout`
insets `(0,136,0,0)`, rect `(480,0)–(625,136)` on 1080×2400, reported as `[0,0][136,1080]` and
`[2264,0][2400,1080]` in the two landscapes. Screenshots are in `img/2026-09-18-camera-overlay/`;
the `final_*` three are the shipped label, the rest the earlier "Controls" label and otherwise
identical geometry. Holds were set through the accelerometer (`emu sensor set acceleration`) —
`mRotation` reads the foreground *window*, not the phone, and under the locked camera it never
moves, which is what the earlier `emu rotate` loop tripped over.

| File | What it shows |
|---|---|
| `final_p1_portrait_open` | portrait: no status bar; strip below the cut-out, ✕ then the outlined "Strip" box; shutter bottom-centre with black ring; count outlined; nav handle present. **The top of the frame is the synthetic scene's pure white, and the ✕ and box read over it.** |
| `p2`, `p4` (held landscape, window locked) | ✕, count and label turned a quarter in place; nothing else moved |
| `p3` (held upside down) | glyphs turned a half; shutter still on the window's bottom, which is the port edge in that hold |
| `final_lA_open_landscape_A` (rot 1, port right) | strip down the **left** edge inboard of the 136 px cut-out band, ✕ at top; shutter on the **right**, vertically centred on the frame (from the screenshot: ~y 450–630 of 1080, centre 540 — not from a bounds dump); count inboard |
| `lB_open_landscape_B` (rot 3, port left) | the mirror: strip down the right, shutter on the left |
| `lA_/lB_held_portrait_window_locked` | landscape arrangements held portrait: glyphs turned, layout unmoved |

`dumpsys window` with the camera open: `statusBars … visible=false`, frame `[0,0][1080,136]` in
portrait and `[0,0][2400,74]` in landscape; after Back, `visible=true`. Not exercised on the
emulator: the swipe-to-reveal and the four-minute exit.

**A misreading, and the counterfactual that caught it.** A green pill with a camera glyph sits
top-right in every shot. I read it as the Album screen showing through a cut-out band the dialog
did not cover, wrote a `layoutInDisplayCutoutMode` attribute and a comment saying so, and rebuilt.
It is **Android's camera-in-use privacy indicator**, a system overlay: it appears in the other
build's shots too, in the window's own orientation, over the viewfinder. Whether the attribute
was *needed* was then measured rather than argued: a build without it reports the dialog window
at `[0,0][1080,2400]`, `layoutInDisplayCutoutMode=always`, identical to the build with it. The
attribute was removed and the comment now records the measurement.

**The spec's "lead worth checking" (a 31 px landscape shutter offset).** Not seen. The shutter
band on a vertical edge applies no top or bottom inset, and the screenshot puts the shutter on the
frame's vertical centre. If it exists on the S26 Ultra it is a device item.

**One layout defect found and fixed on the emulator:** the placeholder's label wrapped to
"Contr/ols" inside the 40 dp column of a vertical strip. Label shortened to "Strip".

## What could not be tested, and why

- **Insets.** Robolectric reports zero for the cut-out and the navigation bar, so "the strip clears
  the cut-out" and "the shutter clears the nav bar" are emulator/device only. The tests assert
  "flush with the edge", which is what zero insets make the same claim.
- **The bar actually hiding, returning, and the swipe.** `WindowInsetsControllerCompat`'s effect is
  invisible to Robolectric; the tests assert which window was asked. Hiding and return-after-Back
  are emulator-confirmed above; the swipe and the timeout exit are not.
- **The band model's non-degenerate case.** Does not exist yet; see above.

## Device-only (S26 Ultra), per the spec

1. Shutter on the charger-port edge: portrait, inverted portrait, both landscapes.
2. Strip on the punch-hole edge in all four, clearing the cut-out.
3. Text rotating with the phone in all four.
4. No status bar in any orientation; navigation bar present.
5. Status bar returns after every exit, including the four-minute timeout.
6. Swipe from the top reveals the bar and the shade — one swipe or two.
7. Overlay readable over a bright outdoor scene.
8. Saved photos upright from all four orientations (the capture path is untouched; this is the
   regression check).

## Documents

**v4 step 3 rewritten** against what was built, with a superseding note listing what each old
clause was and why it changed: Done → ✕ in the strip; no "upright, not turned"; the status bar
hidden and its return tested at three exits (new 3.6–3.8, including the timeout); the strip and its
placeholder named in 3.1, 3.3, 3.4. Step 4's cross-reference moved from 3.6 to 3.7. The sign-off
matrix and step 6 were not changed.

## Constraints, checked

`OrientationEventListener`, the capture-rotation path, `IntendedOrientation.kt`, the window lock
and its setting: not in the diff. No ratio, no setting, no library. The strip reserves nothing when
empty (revert C). The shutter's bounds are unchanged to 0.51 dp in every test that pins them.
