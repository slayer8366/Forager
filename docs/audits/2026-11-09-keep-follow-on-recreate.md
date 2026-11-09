# Keep following after a recreation with a saved route open (Hazel, 2026-11-09)

Dispatched by Rowan. RECORD -2192 (records-after-173), newest bullet "Diagnosed"; diagnosis in `docs/audits/2026-11-06-01-next-turn-completion-report.md` on next-turn (cfa18286d). The owner: **"Fix: remember framing (Recommended)"**, "After the restart the map carries on following you; nothing else changes." PR #511 into pre-main, branch `keep-follow-on-recreate`, cut from `origin/pre-main` 3cfa59d61. Marks: [observed] run or file read, [read] source read, [inferred] reasoned and not run.

## The cause, checked at pre-main 3cfa59d61 [read]
- `.MainActivity` handles `orientation|screenSize|screenLayout|keyboardHidden` only (AndroidManifest.xml:176), so a font or night-mode change recreates it.
- `val mapCameraMemory = remember { MapCameraMemory() }` (AvailabilityScreen.kt:273) and the map's own `lastAppliedCameraRequestIdState = remember { ... }` (SightingsMap.kt:527) are both lost on the recreation.
- The saved route's fit (`savedRouteCameraRequest`, CompactMapRoutePlotting.kt:122, id `saved-route-<trip>-<serial>`) keeps its id while the route shows. MapDataRefresh.kt:109 reads the applied id from the memory, else the map's own; both are null after the recreation, so `cameraRequestStopsFollowing` (:111) ends following and the fit applies (:124 writes the id back). The navigation view's listener then saves follow-broken (NavigationView.kt, the `tracking changed` handler at :416). All of the line numbers the dispatch gave matched.

## The change
- New `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapCameraMemoryRecreation.kt` (50 lines): `rememberMapCameraMemory()`, a `rememberSaveable` with a saver that keeps **only** `appliedCameraRequestId`. Nothing else in the memory is saved: not the camera, not the search frame's id, not the cover, heading or crowded stacks.
- `AvailabilityScreen.kt:274` calls it in place of `remember { MapCameraMemory() }` (plus one comment line and the import).
- **Same process only.** Saved instance state also comes back after the process is killed in the background. A saved route's id counts opens in `RoutePlottingViewModel.openCount` (RoutePlottingViewModel.kt:200-205), which starts again at 0 in a new process. So without a guard, the first route opened after a process restore could carry the restored id and not be framed. The saved value carries a per-process token, and a value saved by another process restores nothing, so you get a fresh memory as before. This keeps "a new route still frames" true. It is a guard I added, not something the dispatch named (see "Decided beyond scope").
- No files over the size limits: AvailabilityScreen.kt is 755 lines, and the new file is 50.

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
