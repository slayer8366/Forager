# The placeholder label turns with the setting on: measured, and it is the "same value, still diverge" case

**Date:** 2026-09-18 · **Branch:** `claude/new-session-vto65i` at `0ace01f` · **Dispatch:** "the
portrait lock means nothing turns, including the placeholder", item 2 · **Status:** measured,
**stopped before fixing** on the dispatch's own condition — the two glyphs read the same value and
still diverge — with the cause established and two fixes priced.

## The three questions, answered from the code and then from a probe

**1. Where the label's rotation comes from, and whether it is the count's value.** The same value.
`InAppCameraDialog.kt:235` hands `CameraStrip` `deviceRotation = session.deviceRotation`;
`CameraBands.kt:153` passes it into the strip's content lambda; `defaultStripContent()` (`:160-161`)
forwards it to `StripPlaceholder(edge, deviceRotation)` (`:165`), which applies
`rotateWithDevice(deviceRotation)` (`:180`). The count gets `session.deviceRotation` at
`InAppCameraDialog.kt:241` and applies the same modifier at `:285`. One source, the gated
`CameraXCaptureSession.deviceRotation` (`effectiveDeviceRotation`, pinned to `ROTATION_0` with the
setting on).

**2. Why they diverge only after a flip.** Because `rotateWithDevice` has a *second* term, the
display rotation, and that term is read at a different moment for each glyph. A throwaway probe
(a log line inside `rotateWithDevice` naming the glyph and both terms; built, run, and the source
restored from a saved copy, never committed) through the failing sequence — setting on, landscape
open, the flip, then two holds — logged, in order:

```
LABEL surface=0 display=1 target=-90.0 viewDisplay=1 config=2
COUNT surface=0 display=1 target=-90.0 viewDisplay=1 config=2
COUNT surface=0 display=0 target=0.0  viewDisplay=0 config=2      (5.6 s later)
COUNT surface=0 display=0 target=0.0  viewDisplay=0 config=2
```

Both glyphs composed first while the window was still landscape (`display=1`), both correctly
targeting −90° for that window. The window then flipped to portrait. **The count recomposed and
re-read the display (`display=0`, target 0); the label never recomposed again.** Its display term
stayed at 1, its target at −90°, and it stays turned in every hold because the surface term is
pinned and the display term is never re-read.

**Why the count recomposed and the label did not:** nothing about the flip itself reached either.
`config=2` — `Configuration.ORIENTATION_LANDSCAPE` — is what `LocalConfiguration.current` reported
**inside the dialog's composition in every line, including after the window was portrait.** So
`currentDisplayRotation()` (`RotateWithDevice.kt:98-101`), which reads `LocalConfiguration.current`
precisely so that a window turn recomposes it, gets no invalidation inside a `Dialog`'s content:
the configuration local the dialog content sees did not change when the Activity's did. The count
was re-read anyway because `ShutterCluster` recomposes for other reasons — `shutterEnabled` flips
when the session goes `Opening → Ready`, and the 5.6 s gap between the count's first and second
lines is that transition — and each recomposition happens to re-evaluate the modifier's default
argument against the live `View.display`. The label's composable has no other input that changes,
so it is never re-evaluated. **The count is correct by coincidence, the same way setting-on was.**

**3. If a different value, what is it.** It is not a different value. It is the same gated
surface term, with a display term that is a live read on recomposition and a stale one otherwise.
The mechanism the code relies on for freshness — `LocalConfiguration` invalidation — does not fire
inside the dialog.

## Why this did not show anywhere else

- **Setting off:** the window is `LOCKED` and does not turn while the dialog is up; when it turns
  across a return (`6689ed1`), the arrangement key changes edges and both glyphs' subtrees are
  recreated, so their display terms are read fresh. The one case where the window turns *without*
  the arrangement changing is the setting-on flip: `Portrait` before and after.
- **The tests:** every dialog test provides `LocalConfiguration` from outside the dialog through
  `CompositionLocalProvider`, where it does update, and Robolectric cannot turn the real window
  under the dialog without recreating the Activity. The gate test added for item 1 passes on this
  code and says so in its doc: it guards the gate, not the invalidation. **No test in the suite can
  see this defect**, which is why it is recorded here with the probe's output rather than a red.

## The dispatch's stop condition, and the two fixes

The two glyphs read the same value and still diverge, so this stops. The cause is not in the gate
and not in the label: it is that a glyph inside the dialog only refreshes its display term when
something else happens to recompose it. Any fix changes how the display term reaches glyphs, which
is the second stop condition, and it is not label-specific:

1. **Hoist the display term.** `InAppCameraDialog.kt:164` already computes `displayRotation =
   currentDisplayRotation()` *outside* the `Dialog {}` block, in the Activity's composition, where
   `LocalConfiguration` does invalidate — it is what re-derives the arrangement on a return. Pass
   that value down and call `rotateWithDevice(surface, displayRotation)` with it at both call sites
   instead of letting each glyph read its own default. Every glyph then turns on the same term the
   arrangement turns on. Smallest change; touches every glyph's call site, which the dispatch says
   to stop on; leaves `currentDisplayRotation()`'s in-dialog weakness in place for any future
   caller.
2. **Make `currentDisplayRotation()` invalidate on its own** — observe the window (a
   `View.OnLayoutChangeListener` or `DisplayManager.DisplayListener` behind a `produceState`) so it
   is correct anywhere, dialog or not. Right at the root; a rework of the modifier, which the
   dispatch also says to stop on.

Neither is built. Item 1 (v4 step 4.2, the gate test) is unaffected by this and landed.

## Emulator note

The after-flip state the dispatch asked to screenshot is already on record from the 6.11 run
(`img/2026-09-18-camera-overlay/setting_on_return.png`): portrait window, portrait arrangement,
count upright, "Strip" on its side inside its own horizontal box. The probe run reproduced it a
second time with the terms logged. **Not device-only:** nothing here needs a phone.
