# The navigation view's padding after a font-change recreation (Hazel, 2026-11-09)

Dispatched by Rowan. RECORD -2192 (records-after-173), the "#507 merged" bullet: after a font-change recreation while navigating and following, the dot loses the navigation view's padding until the next card change (#500 J8, RECORD -2183, together with #511). Branch `dot-lift-after-recreate`, cut from `origin/pre-main` de1031955 (still pre-main's head when this was written). The work was started by an earlier Hazel session (red test cfbb6c2ae, WIP fix e323c9d07) and finished here: a harness fix (5a6f67147), the runs, and this report. Marks: [observed] run or file read, [read] source or bytecode read, [inferred] reasoned and not run.

## The cause [read, from source and MapLibre 13.5.0 bytecode]
- The recreated map's first style load restores following. MapStyleReload.kt:202 calls `activateLiveLocationIfPermitted` with the camera restore's TRACKING, and that call sets the camera mode (LiveLocationPuck.kt:193).
- When the mode changes from not following to TRACKING with a known location, `LocationCameraController.transitionToCurrentLocation` sets `isTransitioning` and runs an animated move. It uses `Transform.animateCamera`, because `Utils.immediateAnimation` returns false for a real metres-per-pixel value. While that move runs, `paddingWhileTracking` and `tiltWhileTracking` are refused ("the camera mode is transitioning").
- The navigation view applies itself inside that transition (`NavigationModeChange.applyView`). Its padding and tilt are refused at once: MapLibre calls the padding callback's `onCancel` inside the call. Nothing applied them again. The D29/J8 shift runs only when the side insets change, which is why the next card change fixed it.

## The change
- `LiveLocationCameraRestore.kt` (new, 27 lines): `setLiveLocationCameraMode(component, mode, onTransitionEnded)` sets the mode as `cameraMode = mode` did, and reports when the location component's own transition ends, whether it finishes or is cancelled.
- `LiveLocationPuck.kt:174-193`: the activation takes `onCameraTransitionEnded` (does nothing by default) and sets the mode through that function.
- `MapStyleReload.kt:201-206`: the style-load activation connects it to `navigationModeChange.onLocationTransitionEnded()`.
- `NavigationView.kt` (661 lines, under 800): `refusedView` (:373) and `onLocationTransitionEnded` (:376). `landViewPadding`, pulled out of `applyView`'s listener, keeps the tilt and padding when they are refused at once. They are applied again when the transition ends, and only while `expected == mode`. `applyView` (:488), `northUpKeepingFollow` (:605) and `leaveNavigation` (:623) clear what is waiting. So if you panned away, the map stays panned (expected is null), and stopping navigation drops it.
- Test harness: `CameraMapFixture.kt` gives the inert location camera controller the map's `transform`, along with the earlier session's `metersPerPixel`, `Projection` and `finishAnimatedMove`. The controller uses `transform` only when it is enabled and has a location (bytecode at `transitionToCurrentLocation`). No other user of the fixture sets either of those (`git grep '"isEnabled"'` on app/src/test finds only this test).

## Tests
`NavigationViewAfterRestoreTransitionTest` runs on a real MapLibreMap, with the SDK's own LocationComponent and LocationCameraController over a recording native map. It restores TRACKING (the transition is running, checked as a precondition), then applies the view at FACING_UP or CALIBRATING. Then it ends the transition: for FACING_UP, the view's own mode change cancels it; for CALIBRATING, the animated move finishes. It asserts that the padding the location component's animator was given is `[0, 334.5, 0, 153]` (the top quarter of a 1338 px map, plus J8's bottom) and that the tilt is 45.

### Runs [observed]
- **First red, discarded: 42639764449** (ref `hazel-lift-red`, cfbb6c2ae). Both cases threw `NullPointerException: ... because "this.transform" is null` inside `LocationCameraController.transitionToCurrentLocation` at the restore call. That is a harness gap (the fixture's controller had no transform), not the defect, so it is not cited as red.
- **Red: 42644207217** (ref `hazel-lift2-red` f6311c98d: the red test plus the harness fix, without the fix). The compile log has 0 ` e: ` lines and the build was BUILD SUCCESSFUL. 11 cases ran, 2 failed: `the navigation view's padding never reached the camera after the restore's transition (FACING_UP)` and the same for `(CALIBRATING)`. Both preconditions passed first: the restore set TRACKING, and the transition was running.
- **Green (filtered): 42644211920** (5a6f67147): 11 of 11 pass, 0 ` e: ` lines.
- **Revert: 42645543203** (ref `hazel-lift2-revert` 85e0466d5). It changes one line, NavigationView.kt:559: `refusedView = {` becomes `@Suppress("UNUSED_VARIABLE") val revertedRefusedView: () -> Unit = {`, so the refused view is never kept. The compile log has 0 ` e: ` lines and the build was BUILD SUCCESSFUL. 11 cases ran, 2 failed, with the same two messages as red. Losing the retry is exactly what this revert causes. The revert was made in a separate detached worktree. The file there was restored from a copy saved before the edit, not from git, and the copy matches 5a6f67147. The branch worktree was clean afterwards, with `refusedView = {` present.
- **Full CI: 42645501420** (5a6f67147): every job green.
- **By name, against pre-main's 42638115477** (de1031955, green). Base: 7529 cases (7505 pass, 24 skip). This branch: 7531 (7507 pass, 24 skip). 0 cases changed outcome, 0 are missing, and the 2 new cases are the two above, both passing.

## S22 step
1. Install the build from this PR. In landscape, start navigating to a waypoint, so the route card rests at the bottom. Make sure the map is following you (Re-center if not).
2. Change the system font size (for example to 1.3, then to the largest), and come back to Forager.
3. Expected: about a second after the map comes back, the walker's dot sits where it did before the change: in the lower part of the map (top padding a quarter of the map), above the resting route card (J8), and the map is tilted. It should not stay at the plain centre until the card changes.
4. Log check (`ForagerNavView`): "the tilt and padding refused at once; applied again when the location component's transition ends", then "the location component's transition ended: applying the view's tilt and padding again", then "view applied".
5. Also check: panned away while navigating, then a font change, then the map stays exactly where it was and is not followed. Stopping navigation after the restart leaves the walker centred, flat and north up.

## Confirmed and inferred
- [observed] The runs, their compile logs, the JUnit XML tallies and the by-name comparison above.
- [read] The cause, from source and from `javap` of MapLibre 13.5.0's `LocationCameraController` (`transitionToCurrentLocation`). The earlier session read the refusal in `paddingWhileTracking`/`tiltWhileTracking` and the cancel-inside-the-call. I did not re-read those two, but the red run is consistent with them: the padding never reached the animator.
- [inferred] That the real MapView behaves like the fixture. The native map is a recording proxy, and the move's end is simulated with `Transform.onCameraDidChange(true)`.

## Could not determine
- Whether the S22 now draws the dot in the right place after a recreation. That needs the S22 step; Robolectric does not draw the map.
- The test calls `setLiveLocationCameraMode` and `applyView` directly, not through MapStyleReload's style-load path (a real MapView cannot run under Robolectric). The connection at MapStyleReload.kt:205 is checked by reading it, not by a test.
- Whether the timing on the device matches: does the navigation view apply during the 750 ms transition or after it? If it applies after, nothing is refused and nothing changes [inferred].

## Premises that were wrong
- **All of the navigation padding was lost, not only J8's.** RECORD -2192 describes the defect as the dot losing "J8's lift (it sits at the map's centre)". The S22 measurement the earlier session took the case from has the dot at y 771. That is against 936 with the top padding alone and 861.5 with J8's lift, so the top quarter's padding and the tilt were lost too, not only J8's bottom. The fix re-applies all of them. The record's own words ("sits at the map's centre") match this; only the label "J8's lift" was narrower than the defect.
- The earlier session's red run (42639764449) was taken as red evidence in the hand-over. It failed on a harness NPE, not on the defect (see Runs).

## Decided beyond scope (open to the owner)
- **The other mode changes are not connected.** The locate press on a not-yet-activated component (`MapLocationDot.kt:92`) still gets the default do-nothing callback, and the press on an activated one sets `cameraMode = CameraMode.TRACKING` directly (:90). If a navigation view were applied during either one's TRACKING transition, it would be refused the same way. No case of this has been seen. It was left alone so the change stays limited to the reported defect.
- No README index row has been added: rows here record merges, and this PR is not merged.

## Clean-up
- Throwaway refs `hazel-lift-red`, `hazel-lift2-red`, `hazel-lift2-green` and `hazel-lift2-revert` are deleted from origin after the runs. The runs stay readable by number.
