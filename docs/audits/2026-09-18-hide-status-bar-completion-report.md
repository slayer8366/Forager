# Hide the status bar while the in-app camera is open: completion report

**Dispatch** "hide the status bar while the in-app camera is open" and its **addendum** "option 1
with the inset collapsing", both written against `claude/new-session-vto65i` at `84f0f8b`. Neither
is in the repository; their substance is recorded here.
**Base used:** `84f0f8b`, confirmed as the remote tip.
**Code:** `e14b649`, plus the commit carrying this report, on `claude/hide-status-bar`.

## History: one stop, then the addendum

The first pass stopped at the dispatch's own condition. The controls sat inside
`windowInsetsPadding(WindowInsets.safeDrawing)`, which includes the status bar's inset, so hiding
the bar would move Done and the landscape shutter. Mid-stop, two further asks arrived: that the bar
stay at the phone's physical top in every orientation, and that it stay pullable. That stop also
reported that the system's bar and its pull-down belong to the display's top edge, which the
camera's window lock holds still, so the bar cannot follow the phone without the window rotating.

**The addendum corrected the premise:** nobody asked for the bar to track the physical top. "No
status bar" in every orientation meant hidden in all of them, and a hidden bar has no position to
get wrong. The decision was **option 1 with the inset collapsing**: hide the bar on the dialog's own
window, let the space come back to the controls, and centre centred controls on the full screen.
The reserve-the-inset instruction was withdrawn. `139727a` fixed which *edge* the shutter is on,
and that edge comes from the device's rotation, not from insets.

## What landed (`InAppCameraDialog.kt`)

- **`HideStatusBarForThisDialog`** (`:345-358`, new), called first inside the `Dialog` content
  (`:175`).
  - It takes the dialog's own window from `LocalView.current.parent as DialogWindowProvider` and
    sets `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
  - It hides `statusBars()` only (`:355`), leaving the navigation bar, and asks for the bar back in
    `onDispose` (`:356`).
  - If there is no dialog window, it logs that and does nothing. It never falls back to the
    Activity's window.
- **Per-side insets instead of one safe area.**
  - Before: `Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))` wrapped all
    the controls.
  - After: `Box(Modifier.fillMaxSize())` (`:238`), with each control taking only the sides it needs:
    - Done, both arrangements: top and start (`:248`, `:385`).
    - Portrait column (count and shutter): bottom only (`:261`), so it centres on the full width.
    - Landscape shutter and count: horizontal only (`:399`), so it is vertically centred on the full
      height and still kept off a side nav bar or cutout on the port edge.
- **Where the controls end up, by reading.**
  - Done moves toward the top edge by the collapsed status bar height (in portrait, less any taller
    punch-hole cutout inset, which `safeDrawing` still carries).
  - The portrait shutter is unchanged: its bottom inset is the navigation bar, which stays.
  - The landscape shutter moves to the screen's true vertical middle. Before, it sat in the middle
    of the area between the status bar and the bottom inset, which is off-centre by half their
    difference.
  - **The centring did need correcting**; it was not already true.
  - Which edge the shutter is on is unchanged, because `CameraArrangement` still decides it from
    the rotation.

## Verify-first answers

1. **Does the layout move?** Yes, by design now; see "Where the controls end up". Every position
   depended on the status bar inset through the single `safeDrawing` wrapper (`:223` before).
2. **Its own window, or the Activity's?** Its own. The camera is a Compose `Dialog` with
   `decorFitsSystemWindows = false`. Read from the Compose UI 1.12.0 library's compiled
   `DialogWrapper`, not assumed: that setting makes the window cover the whole screen and sets the
   cutout mode to `ALWAYS`, so hiding the bar leaves no black band at the punch-hole and the dialog's
   full box is the full screen. The Activity's window is never touched, so no exit has Activity
   state to restore.

**Exits, from the code.** Every route out clears the camera target, and `InAppCameraHost.kt:97` then
stops composing the dialog:
- Done: `onDismiss` (`InAppCameraDialog.kt` portrait and landscape Done buttons).
- Back: the `Dialog`'s `onDismissRequest = onDismiss`.
- The four-minute absence timeout: `InAppCameraViewModel.onReturnedToApp` → `close()` (`:80`), a
  different caller from the user exits but the same target → null.
- Activity destroyed ("Don't keep activities", v4 6.4) or process death: the composition and the
  dialog's window go with it.

The hide belongs to the dialog's window and leaves with it on all of them.

**Documents.** v4 step 3 was read against the change: 3.1–3.6 still hold as written. "Vertically
centred" in 3.3 now means the full screen's middle. No step asserted the status bar's presence.
**Added beyond the dispatch**, as with 6.8–6.11: items **3.7–3.9** (hidden in every orientation with
the nav bar kept; back on every exit including the long absence; pullable by swipe, one swipe or
two), and an addition note saying what the suite can and cannot show.

## Evidence

**Can a test express it? Partly, and I've been exact about which part.** Robolectric draws no status
bar and reports zero insets, so whether the bar disappears and where the controls land cannot be
tested; that is device-only, v4 3.7–3.9. What can be tested is the **request**. Robolectric runs the
platform's real `InsetsController`, and its requested-visible types are what the system acts on.
They are read through `InsetsController.getRequestedVisibleTypes` by reflection, because the getter
is not public SDK. The read throws rather than defaulting if the method is ever renamed.

**Seen failing before the fix** (fresh XML): `status bar still requested visible on the camera's window`.

**New tests (2), `InAppCameraDialogTest`:**
- `the status bar is requested hidden on the camera's own window, and the Activity's is left alone`:
  the dialog window (from `ShadowDialog.getLatestDialog()`, asserted not to be the Activity's) has
  the status bar requested hidden, and the Activity's window has it requested visible.
- `the status bar is requested visible again when the dialog leaves`.

**Revert checks.** No compile errors; failures read only from XML newer than the run; each file
restored from a saved copy and matched to `HEAD`.

| Revert | Result |
|---|---|
| S1 comment out `controller.hide(statusBars())` | both tests fail: `status bar still requested visible on the camera's window`; `precondition: hidden while open` |
| S2 `onDispose { }` instead of `show()` | **nothing fails** |

**S2 is a finding, not a pass.** Under Robolectric the dialog's window going away is enough on its
own for the request to read visible again, so the second test pins "removing the window restores
the bar", not the explicit `show()`. Its doc now says so. The `show()` stays as belt and braces, for
a platform that might hold a departing window's request, and **nothing here proves it is needed**.

**Layout changes: no test, and none written.** A test asserting control positions would pass
identically before and after under zero insets, which is the kind of check that cannot fail.

**Suite:** `:app:testDebugUnitTest`, executed: 200 classes, **1,579 tests, 0 failures, 0 errors, 24
skipped**. That is 1,577 plus the two new tests; the skips pre-date this change, and the diff adds
no `@Ignore`.

## Not verified

- **Everything visual, on a device only:** the bar actually hidden in each orientation, the nav bar
  kept, the controls' new positions, the bar back on each exit, and the swipe reveal and shade.
- **Whether the transient bar shifts the controls when revealed by swipe.** Inferred not: transient
  bars overlay content without changing the insets the layout reads. Checked by v4 3.9.
- **Behaviour below API 30.** `WindowInsetsControllerCompat` falls back to the dialog decor view's
  `systemUiVisibility` there; minSdk is 26, and the device run is on API 36.

## Whether the collapse reads as an improvement or a shift

In portrait it will mostly read as nothing at all, since the status bar and the punch-hole occupy
nearly the same band and Done moves up by the difference. In landscape it should read as an
improvement. The bar was a strip across the top of a viewfinder you frame through, it is gone, and
the shutter now sits where the thumb expects the middle of the phone, not slightly below it. The
one thing someone mid-run could read as a shift is Done sitting higher than in the last run of step
3. Item 3.7 says so in advance so it is not recorded as a failure.
