# Keep following after a recreation with a saved route open (Hazel, 2026-11-09)

Dispatched by Rowan. RECORD -2192 (records-after-173), newest bullet "Diagnosed"; diagnosis in `docs/audits/2026-11-06-01-next-turn-completion-report.md` on next-turn (cfa18286d). The owner: **"Fix: remember framing (Recommended)"**, "After the restart the map carries on following you; nothing else changes." PR #511 into pre-main, branch `keep-follow-on-recreate`, cut from `origin/pre-main` 3cfa59d61. Marks: [observed] run or file read, [read] source read, [inferred] reasoned and not run.

## The cause, checked at pre-main 3cfa59d61 [read]
- `.MainActivity` handles `orientation|screenSize|screenLayout|keyboardHidden` only (AndroidManifest.xml:176), so a font or night-mode change recreates it.
- `val mapCameraMemory = remember { MapCameraMemory() }` (AvailabilityScreen.kt:273) and the map's own `lastAppliedCameraRequestIdState = remember { ... }` (SightingsMap.kt:527) are both lost on the recreation.
- The saved route's fit (`savedRouteCameraRequest`, CompactMapRoutePlotting.kt:122, id `saved-route-<trip>-<serial>`) keeps its id while the route shows. MapDataRefresh.kt:109 reads the applied id from the memory, else the map's own; both are null after the recreation, so `cameraRequestStopsFollowing` (:111) ends following and the fit applies (:124 writes the id back). The navigation view's listener then saves follow-broken (NavigationView.kt, the `tracking changed` handler at :416). All of the line numbers the dispatch gave matched.

## The change
- New `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapCameraMemoryRecreation.kt` (47 lines): `rememberMapCameraMemory()`, a `rememberSaveable` with a saver that keeps **only** `appliedCameraRequestId`. Nothing else in the memory is saved: not the camera, not the search frame's id, not the cover, heading or crowded stacks.
- `AvailabilityScreen.kt:274` calls it in place of `remember { MapCameraMemory() }` (plus one comment line and the import).
- **Same process only.** Saved instance state also comes back after the process is killed in the background. A saved route's id counts opens in `RoutePlottingViewModel.openCount` (RoutePlottingViewModel.kt:200-205), which starts again at 0 in a new process. So without a guard, the first route opened after a process restore could carry the restored id and not be framed. The saved value carries a per-process token, and a value saved by another process restores nothing, so you get a fresh memory as before. This keeps "a new route still frames" true. It is a guard I added, not something the dispatch named (see "Decided beyond scope").
- No files over the size limits: AvailabilityScreen.kt is 755 lines, and the new file is 47.

## Tests
- `KeepFollowOnRecreateTest` (recreated at font 1.0, like a night-mode change) and `KeepFollowOnRecreateFontTwoTest` (font 1.0, then 2.0 and recreated), both at `w823dp-h384dp-land-xxhdpi`. Each runs the real Maps tab (`MapLayersTestScreen` over `AvailabilityScreen`), navigating, with a saved route opened through the route plotting ViewModel and the user re-centred. Then a real `ActivityScenario.recreate()`.
  - "after a recreation the map carries on following and the saved route is not fitted again": the saved route's fit is applied once, following is ended once (by the original open) and the map is still following.
  - "a new route opened after a recreation still frames": a second trip opened after the recreation applies its fit and ends following, as opening a route does.
  - Preconditions on every recreation: a new activity was created and RESUMED, the recreated font scale is the one asked for, a new map was composed, and **a saveable value in the same screen came back**.
- `MapCameraMemoryRecreationTest` (the saver, read directly, since a process restore cannot run under Robolectric): the same process restores the id, a null id saves nothing, and a value from another process restores nothing.
- **The map is a stand-in.** A real MapView cannot run under Robolectric, so `FollowingMapSlot` applies the screen's camera requests with the production rules (`cameraRequestStopsFollowing`, `shouldApplyCameraRequest`, the memory's id else its own, as at MapDataRefresh.kt:109-125). What MapLibre does with a frame, and the navigation view's listener, are left to the S22.

### Runs [observed]
- **First harness, discarded.** Red run 42559645301 (4 of 4 failed, `applied` = `[saved-route-keep-follow-1-1, saved-route-keep-follow-1-1]`). The forward PR run 42561429552 then failed the same 4 in the same way, *with the fix in*. Probe run 42563641078 showed why: the harness set the screen after `recreate()` instead of in `onCreate`, and **restored no saved state at all** (a saveable counter set to 7 came back 0). Those runs could not tell the fix from today's code, so none of them is cited as evidence. The harness now sets the screen in the host activity's `onCreate`, as MainActivity does, and checks the saveable probe on every recreation.
- **Red on today's code, as the revert** (run 42565548306, ref `hazel-follow-revert` 0f114b306). The one-line revert puts `remember { com.zynergylabs.forager.app.ui.map.MapCameraMemory() }` back at AvailabilityScreen.kt:274, so the screen behaves as pre-main does. The compile log has 0 ` e: ` lines and every build step says BUILD SUCCESSFUL. 8 tests ran, 4 failed, and all 4 are the screen tests, each failing after the saved-state probe passed:
  - `the saved route's fit was applied once, not again after the recreation expected:<[saved-route-keep-follow-1-1]> but was:<[saved-route-keep-follow-1-1, saved-route-keep-follow-1-1]>` (both font classes)
  - `the new route's fit applied, after the first route's only fit expected:<[saved-route-keep-follow-1-1, saved-route-keep-follow-2-2]> but was:<[saved-route-keep-follow-1-1, saved-route-keep-follow-1-1, saved-route-keep-follow-2-2]>` (both font classes)
  - Each one is the re-applied fit, which is what this revert causes. The 4 saver tests passed, as they should, since the revert leaves the saver alone. The file was restored from a copy saved before the edit, not from git. Afterwards the worktree was clean and `rememberMapCameraMemory()` was present at :274.
- **Green**: PR run 42565550493 on cc369e11c, success, 0 ` e: ` lines.
- **By name, against pre-main's latest run 42516834668** (0306ec454; pre-main's head 3cfa59d61 is `[skip ci]`, and `git diff --stat 0306ec454 3cfa59d61 -- app` is empty). Base: 6846 cases (6822 pass, 24 skip). PR: 6854 (6830 pass, 24 skip). 0 cases changed outcome, 0 cases are missing, and the 8 new cases all pass.

## File overlap with PRs that merge after this one [observed]
- `git diff --name-only origin/pre-main...origin/route-card` (#507) also changes `AvailabilityScreen.kt`, but its hunk is at :612 and this PR's is at :273, so it should merge without a conflict [inferred]. No other file this PR touches is changed there.
- `...origin/next-turn` (#500): none of this PR's files.

## S22 step
1. Install the build from this PR. Start navigating to a waypoint, then open a saved route so its card shows. Tap Re-center, so the map is following again.
2. Change the system font size to the largest setting (or switch system night mode) and come back to Forager.
3. Expected: the map is still following your position in the navigation view, and the route is not framed again (no zoom-out, no world view). Opening a different saved route afterwards still fits that route.
4. Log check: no `saved-route-` frame applied after the recreation; the `ForagerNavView` `tracking changed` line does not report a move away.

## Confirmed and inferred
- [observed] The runs above, their failure messages and compile logs, and the by-name comparison.
- [read] Every file:line in "The cause", at 3cfa59d61.
- [inferred] That the real MapView path behaves like the stand-in. Both use the same predicate functions and the same id sources, but MapDataRefresh itself does not run under Robolectric.

## Could not determine
- Whether the real navigation view keeps following on the phone. That needs the S22 step.
- The process-restore guard's real-device behaviour. Only the saver is tested, because a process restore can't run under Robolectric.

## Premises that were wrong
- None in the dispatch. My own first test harness was wrong: it never restored saved state, so it read red with or without the fix (see "First harness, discarded"). It was caught because the forward run failed the same way as the red run.

## Decided beyond scope (open to the owner)
- **The process token.** This was added so a route opened after a process restore still frames. Without it, the fix would have introduced that regression. Nothing a user sees changes in the same process.
- **Not fixed, same mechanism (search frame)** [read, not run]. `MapCameraMemory.appliedSearchFrameId` is also a plain field lost on recreation. `uiState.searchFrameSerial` (AvailabilityViewModel, survives) goes up with every search (AvailabilityViewModel.kt:578, :594; AvailabilitySearchStates.kt:84). So after any search in the session, a recreation re-applies the search frame. MapDataRefresh.kt:88-105 runs that before the camera-request check and ends following (`STOP_FOLLOWING_AND_FRAME`). In such a session this PR alone would **not** keep navigation following. Saving `appliedSearchFrameId` the same way is a one-field addition to the same saver. It wasn't done because the owner's words were "nothing else changes". The S22 session in RECORD -2192 appears to have had no search, since the font change with no route card at 18:28 kept the navigation view.
- **Not navigating, route open, then a recreation.** Before this PR, the recreated map re-framed the route. Now it opens as a fresh map does (the region move, then following once the location activates), because the saved camera is still not kept across a recreation. Keeping the saved camera too would make the map reopen exactly as it was left in every case, but that changes what a recreation does with no route open, so it is for the owner.

## Clean-up
- Throwaway refs `hazel-follow-red`, `hazel-follow-revert` and `hazel-follow-probe` were deleted from origin after the runs. The runs stay readable by number.

---

# Extension: the search frame and the camera kept across a recreation (Hazel, 2026-11-09)

Dispatched by Rowan on #511 (head 1c50dd6c2). RECORD -2192's newest bullet: the gap under -2190 (after a search, the search frame's applied id was lost on a recreation, and its re-apply ended following), and the owner: **"Exactly as I left it (Recommended)"**, "The map remembers its position and zoom across the restart, so nothing moves. Also applies with no route open." Go: "Build it" (on #511). Code commit 7b2b6c1b6.

## Where the camera lives, read from source [read]
- `MapCameraMemory.saved` (MapCameraMemory.kt:36) is the camera a map last settled on (target, zoom, bearing, tilt, following, the applied region target). The camera idle listener writes it (MapListeners.kt:218), and so does a search frame (MapDataRefresh.kt:103).
- A new MapView's first style load restores it through `cameraRestoreFor` (MapStyleReload.kt:114, MapCameraMoves.kt:143): the camera, the region target taken as applied (so the region move does not run), and the camera mode, TRACKING if it was following and NONE if not. This is the path a map coming back from a tab change already takes. With no saved camera, a fresh map does the region move at `zoomForRadiusKm` and then follows once the location activates.
- So "exactly as I left it" needs no new restore path. The recreated map only has to find `saved` in the memory. Following wins when it was on, because the snapshot carries `following` and the restore puts TRACKING back.
- Only the Maps tab hands a camera memory to its map (ScaffoldMapTabCall.kt:129). Every other map (the journal's among them) gets none, so nothing changes for them.

## The change
- `MapCameraMemoryRecreation.kt` (98 lines): the saver now keeps three fields, all under the same per-process token: `appliedCameraRequestId` (as before), `appliedSearchFrameId` (item 1) and `saved` (item 2). They are kept as a `Serializable` value (`SavedMapCameraMemory`, `SavedCamera`), so the saved-state Bundle can hold them. A memory with none of the three set saves nothing. A value from another process restores nothing, so after a process restore you get a fresh map, as after a restart. The restore is at :73-79: item 1 is `appliedSearchFrameId = value.appliedSearchFrameId` (:76), item 2 is `saved = value.camera?.toSnapshot()` (:77).
- `AvailabilityScreen.kt:271-273`: the comment only. The call is unchanged.
- The cover picture, the heading and the crowded stacks are still session-only.

## Tests
- The map stand-in moved to its own file, `RecreationMapStandIn.kt`, and now does more than before. It applies search frames (`searchFrameMove` against `appliedSearchFrameId`). It restores the memory's saved camera when a map is made (`cameraRestoreFor`). After each move it writes the saved camera, as the idle listener does. `KeepFollowOnRecreateTest` uses it in place of its old private `FollowingMapSlot`.
- New in `KeepFollowOnRecreateTests` (both font classes): "after a search then a recreation the map carries on following and the search is not framed again". A real search goes through the ViewModel (`searchManualCoordinates`), then a route is opened, then Re-center, then the recreation.
- New `MapCameraOnRecreateTest` / `MapCameraOnRecreateFontTwoTest` (not navigating; font 1.0 to 1.0, and 1.0 to 2.0):
  - "with a route open the map reopens where the user left it"
  - "with no route open ..."
  - "a new route opened after the recreation still frames"
  
  Every recreation first asserts that the saveable probe came back, the activity is RESUMED at the asked font, and a new map was composed.
- `MapCameraMemoryRecreationTest`: the search frame and camera round trip, a camera with no applied target, Java serialization of the saved value, nothing saved when empty, and the other-process guard. The case "no applied request saves nothing" was renamed to "nothing applied and no camera saves nothing", because the empty condition now covers all three fields.

### Runs [observed]
- **Red, tests on #511's code** (run 42568553531, ref `hazel-follow2-red` e10f22639). The compile log has 0 ` e: ` lines and the debug APK built (BUILD SUCCESSFUL). 16 ran and 6 failed, every one a case this change targets:
  - `the search's frame was not applied again after the recreation expected:<[1]> but was:<[1, 1]>` (font 1.0) and `... but was:<[1, 1, 1]>` (font 2.0)
  - `the camera is where the user left it expected:<StandInCamera(target=LatLng(lat=45.4123, lng=-121.7531), zoom=15.25, bearing=30.0, tilt=20.0)> but was:<StandInCamera(target=LatLng(lat=39.8283, lng=-98.5795), zoom=12.0, bearing=0.0, tilt=0.0)>` (route open and no route, both fonts)
- **Revert 1, the search frame id not restored** (run 42570620634, ref `hazel-follow2-rev1` 633e89b21; one line: `appliedSearchFrameId = 0` at :76). 0 ` e: ` lines, debug APK BUILD SUCCESSFUL. 19 ran and 3 failed: the two search tests (`expected:<[1]> but was:<[1, 1]>` / `<[1, 1, 1]>`) and the saver's `a recreation in the same process brings back the applied search frame and the camera expected:<4> but was:<0>`. All three failures are ones this revert causes. The camera tests passed.
- **Revert 2, the camera not restored** (run 42570622962, ref `hazel-follow2-rev2` b90311d5a; one line: `saved = null` at :77, on top of the forward code). 0 ` e: ` lines, debug APK BUILD SUCCESSFUL. 19 ran and 6 failed:
  - the four "reopens where the user left it" cases (the same message as red)
  - the saver's camera round trip, `but was:<null>`
  - "a camera with no applied target comes back with none", `but was:<null>`
  
  The search tests passed. All six failures are ones this revert causes.
- Both reverts were made from a copy saved before editing (`forward.kt`) and pushed only to their own refs. Afterwards the working file matched the saved copy (`cmp`), and lines :76-77 hold the forward code.
- **Green**: PR run 42570608128 on 7b2b6c1b6, success, 0 ` e: ` lines.
- **By name, against 42565550493** (#511's previous green): base 6854 (6830 pass, 24 skip), PR 6865 (6841 pass, 24 skip). 0 cases changed outcome. 1 case is missing, the rename above. The 12 new cases all pass.

## S22 step
1. Install the build from this PR. **Not navigating**, with a saved route open, pan, zoom, turn and tilt the map away from your position. Change the system font size (or night mode) and come back. Expected: the map is exactly where you left it, with the same zoom, turn and tilt, and is not following.
2. Close the route and repeat with no route open. Expected: the same.
3. Run a search (manual coordinates). Start navigating, open a saved route, and tap Re-center. Change the font size and come back. Expected: still following, with no frame of the search and no frame of the route. Log check: no `ForagerSearchFrame` "search frame ... applied" line after the recreation, and the `ForagerNavView` `tracking changed` line does not report a move away.
4. After a recreation, open a different saved route. Expected: it is framed.

## Confirmed and inferred
- [observed] The runs, their compile logs, the failure messages and the by-name comparison above.
- [read] Where the camera lives and how a new map restores it (file:line above), at 7b2b6c1b6.
- [inferred] That the real map restores the recreated memory's camera exactly as it does after a tab change. It is the same `cameraRestoreFor` path with the same snapshot, but the restore itself runs only in a real MapView.

## Could not determine
- What the user sees on the phone, including whether MapLibre's first frames flash anything before the restore. The tab-change path draws its cover picture over those frames. A recreation has no picture (the bitmap is not saved), so the cover decision is `NO_PICTURE`, and the map draws itself as before. That needs the S22.
- A recreation while the camera is still moving (a fling, or an eased move). The saved camera is the last idle one, so the map reopens where it last settled, not mid-move. This is the same as a tab change. I read this from the code, but it was not run.
- A process restore on a device. Only the saver's guard is tested.

## Premises that were wrong
- None in the dispatch. The line numbers it gave (MapDataRefresh.kt:88-105) matched.

## Decided beyond scope (open to the owner)
- **Following comes back as it was left, including off.** If the user had panned away while navigating (following off), the recreated map now stays where they left it, where before it reopened following. That is what "Exactly as I left it" says, and it matches a tab change, but it is a change for the navigating case.
- **One rename.** The saver test case "no applied request saves nothing" became "nothing applied and no camera saves nothing". It is the same assertion, widened to the three kept fields.

## Clean-up
- Throwaway refs `hazel-follow2-red`, `hazel-follow2-rev1` and `hazel-follow2-rev2` were deleted from origin after the runs. The runs stay readable by number.
- The two revert commits were also made on the worktree's local `keep-follow-on-recreate` branch, by mistake. They were never pushed to the PR branch. The worktree now sits on `keep-follow-2`, which tracks `origin/keep-follow-on-recreate`. Its local branch still carries those two commits, and it was not reset (per the no-reset rule).
