# Sorrel's review of PR #512 (dot-lift-after-recreate 92ddee1c6 into pre-main de1031955), 2026-11-09

Model: Fable 5.1. Read-only; nothing changed or pushed. Marks: [read] source or bytecode read; [observed] run artefact read; [inferred] reasoned, not run.

## Verdict
**Approve.** The cause is confirmed from MapLibre 13.5.0 bytecode; the fix is narrow, bounded to the one path that lacked a listener, and cannot re-follow a panned-away map or re-apply stale insets. Red/green/revert/full runs check out, with compile logs clean. Two new cases, no shared case changed outcome. Hazel's beyond-scope note is not a gap this release needs (reasoning below).

## The cause, confirmed [read, javap of android-sdk-13.5.0.aar from ~/.gradle]
- `LocationComponent.paddingWhileTracking(double[], long, callback)` and `tiltWhileTracking(...)`: after `checkActivationState`, refused if `cameraMode == NONE (8)`, then refused if `LocationCameraController.isTransitioning()` ("...ignored because the camera mode is transitioning"); the refusal goes through `notifyUnsuccessfulCameraOperation`, which calls `callback.onCancel()` synchronously inside the call. Hazel's "cancel inside the call" and the `asking` flag in `landViewPadding` rest on this.
- `LocationCameraController.setCameraMode(mode, ..., listener)`: same mode → `listener.onLocationCameraTransitionFinished` at once, nothing else; otherwise `wasTracking = isLocationTracking()`, `cameraMode = mode`, **`maplibreMap.cancelTransitions()` when mode != NONE**, notify tracking listeners, then `transitionToCurrentLocation`.
- `transitionToCurrentLocation`: only when `!wasTracking && isLocationTracking() && location != null` → `isTransitioning = true` and `Transform.animateCamera` (or `moveCamera` when `Utils.immediateAnimation`), with `LCC$1` as callback: both `onCancel` and `onFinish` set `isTransitioning = false` then call the listener. Any other mode change calls `onLocationCameraTransitionFinished` immediately with no transition.
- `Transform.cancelTransitions` and `Transform.onCameraDidChange(true)` both deliver the cancel/finish by `Handler.post`, so `isTransitioning` stays true until the main looper turns. That is why applyView's listener (immediate, following-to-following) finds the restore's transition still running.
- `LocationComponent$CameraTransitionListener` wraps the caller's listener and then calls `reset(mode)` → `resetAllCameraAnimations` (compass + location animators only, from bytecode; padding not among them). Same shape the existing applyView path already uses, so the retry inherits nothing new there.

So: recreated map → first style load → `activateLiveLocationIfPermitted` sets TRACKING from NONE with a known location → 750 ms transition; the navigation-view effect (MapNavigationView.kt:74, keyed on `loadedStyle`) applies the view during it; FACING_UP's mode change cancels (posted) and its listener runs at once → padding/tilt refused; CALIBRATING is the same mode → listener at once → refused. Nothing retried until a side-inset change (D29/J8 `shiftPadding`). Matches the S22 observation.

## The fix [read]
- `LiveLocationCameraRestore.setLiveLocationCameraMode` uses `setCameraMode(mode, listener)` (same default transition as the property setter) and reports both finish and cancel. Only the style-load activation wires it (MapStyleReload.kt:205); the locate press (MapLocationDot.kt:90/92) keeps the no-op default.
- `NavigationModeChange.landViewPadding`: a refusal *inside the call* (`asking`) stores `refusedView`; a later cancel logs as before. The retry runs only from `onLocationTransitionEnded`, only while `expected == mode`, and re-reads `sideInsetsPx()` (`{ currentSideInsets }` via `rememberUpdatedState`, MapNavigationView.kt:46), so insets are current at retry time. `topPaddingPx` is captured, but a height change re-runs the effect → `applyView`, which clears and re-arms. `applyView`, `northUpKeepingFollow`, `leaveNavigation` all clear `refusedView`.

### Against #511 (panned away stays panned; stop clears; a later pan cancels)
- Panned away at the recreation: `cameraRestoreFor` gives NONE, `setCameraMode(NONE)` from NONE is same-mode → listener at once → `refusedView` is null → no-op; the effect returns at `!view.following` before `applyView`. Nothing re-follows. [read; the S22 "0 px" pass on 5074 is unaffected by construction]
- User pans during the restore transition: the gesture's `cancelTransitions` posts the cancel; `LCC$6.onMoveBegin` → `setCameraMode(NONE)` notifies the tracking listener synchronously → `expected = null`, `onLeftView`; the posted cancel then reaches `onLocationTransitionEnded` → retry sees `expected != mode` → logs "not applied again". Correct order. [read]
- Stop: `leaveNavigation` clears `refusedView` and cancels the padding animation as before.
- Not a defect, noted: after the retry lands (~750 ms after the restore), a pan during its 750 ms padding animation meets the pre-existing -470 behaviour (padding animation survives a mode change to NONE). Same as a pan during any applyView's padding animation today; the window has moved, not grown in kind.

## Harness change [read]
`CameraMapFixture`: `Projection(native, view)` replaces null in the map constructor; the inert controller gets `transform`; `getMetersPerPixelAtLatitude` answers `metersPerPixel` (0 by default, so `Utils.immediateAnimation` keeps jumping for every other test); `finishAnimatedMove` posts via `Transform.onCameraDidChange(true)`. The controller's `transform` is read only inside `transitionToCurrentLocation`'s enabled-with-location branch. These make more of the SDK reachable, not less; by-name shows no existing case changed outcome. Hides nothing.

## Tests and runs [observed]
- Filter in all three filtered runs: `*EntryMapLocateGlideTest* *LocateKeepsCameraTest* *MapCameraRestorePaddingTest* *NavigationViewAfterRestoreTransitionTest* *RouteCameraOnReturnTest*` (11 cases).
- **Red 42644207217** (f6311c98d = pre-main + red test (143 lines) + fixture, no fix; GitHub compare): 0 ` e: `, BUILD FAILED only on `:app:testDebugUnitTest` "There were failing tests"; 11 ran, 2 failed: "the navigation view's padding never reached the camera after the restore's transition (FACING_UP)" / "(CALIBRATING)". Both preconditions passed first.
- **Green 42644211920** (5a6f67147): 11/11, 0 ` e: `, 2× BUILD SUCCESSFUL.
- **Revert 42645543203** (85e0466d5 = 5a6f67147 + one line: `refusedView = {` → unused local; GitHub compare confirms exactly that): 0 ` e: `, 11 ran, same 2 failures with the same messages. That failure is the one this revert produces (the retry is never stored). Belongs.
- **Full 42645501420** (5a6f67147): all jobs green. Head 92ddee1c6 differs from 5a6f67147 by the audit doc only (`git diff --stat`), so the runs cover the code under review.
- **By name vs 42638115477** (de1031955): base 7529 (7505 pass, 24 skip); head 7531 (7507, 24); missing 0; changed 0; new 2 = the two above, passing. Hazel's figures match mine exactly.
- Nothing silenced: no `@Ignore`, skip-list or assertion change in the diff; skip count unchanged at 24.
- The test asserts the padding and tilt the animator coordinator was given (`[0, 334.5, 0, 153]`, 45), not a proxy. It drives `setLiveLocationCameraMode` and `applyView` directly; the MapStyleReload wiring is read, not tested (Hazel says so).

## Hazel's beyond-scope note (locate press not wired)
Not a gap for this release. While navigating, the locate row and the Re-center pill both call `onReturnToRoute` (CompactMapIconCluster.kt:64, Ruling D; AvailabilityScreenFrameCall.kt:212 bumps `navigationViewRequestId`), which goes through `applyView` and its own listener — never MapLocationDot.kt:90's bare `cameraMode = TRACKING`. The unwired path is reachable only by tapping locate when *not* navigating and starting navigation inside the next ~750 ms [inferred]. Fine to leave; worth a line in the next-release list, not this one.

## Defects
None found.

## Owner's calls
- None required for the code. Optional, next release: wire the locate press (MapLocationDot.kt:90/92) through `setLiveLocationCameraMode` too, for the locate-then-navigate-at-once edge.

## Notes
- The S22 step in the report is the right one; the log sequence ("refused at once" → "transition ended: applying again" → "view applied") is what to read. Hazel's own open question stands: if on the phone the view effect runs *after* the 750 ms transition, nothing is refused and the fix is inert but harmless.
- The audit's cited line numbers (`:373`, `:376`, `:488`, `:605`, `:623`) match the branch file.
- RECORD -2192's "J8's lift" label vs the full loss: Hazel's correction (y 771 vs 936/861.5) is consistent with the mechanism — every padding and the tilt are refused together.

## Could not determine
- Device behaviour (dot position after a font change on the S22): Robolectric does not draw the map; needs the S22 step.
- Whether the real `MapView`'s gesture detector cancels transitions on touch-down exactly as reasoned (I read `Transform` and `LocationCameraController`, not `MapGestureDetector`); the conclusion about pan ordering relies on `setCameraMode(NONE)` notifying synchronously, which I did read.
- The 3-line difference between the red test (143 lines at f6311c98d) and the final (146): not fetched; Hazel's note says the red version set `component.cameraMode` directly, which is consistent with `setLiveLocationCameraMode` not existing yet. The revert run, on the final test, is the evidence I rely on.
