# Sorrel review: PR #511 follow-on delta (cfa9824f8..ede785657; code 581664787)

2026-11-09. Read from source at origin/keep-follow-on-recreate (nothing changed or pushed; no Gradle). JUnit and job logs from `gh api` under /mnt/work/scratch/sorrel-511c, parsed with python3 -I.

## Verdict
**Pass; mergeable tonight.** The delta closes the defect from my earlier review (padding, and the older tilt/bearing leftover, left on when navigation is stopped on a recreated or returned map). The mechanism is the narrow one, the leave is reachable on X from source, the new test bites for the reason the revert produces, nothing is silenced, and the by-name comparison is clean. No defect to send back to Hazel.

## Correct from source [read]
- **Does a recreated map reach the leave on X?** Yes, given location permission. The second restore site (`MapStyleReload.kt:187`) runs inside the `setStyle` callback; the same callback sets `loadedStyle` (`:191`) and then activates the location component (`:201`, `activateLiveLocationIfPermitted`, with `restoreCameraMode = cameraRestore.cameraMode`). So by the time the map is visible to tap X on, the gates at `MapNavigationView.kt:38-46` (map, trackLiveLocation, current loadedStyle, component activated) hold. X turns `navigationView` null, a key of the effect, which re-runs: `gestureProtection` true -> ordinary options; `active` true (from `holdRestoredView`) -> `leaveNavigation`. If X lands while the style is still loading, that run returns at the loadedStyle gate and the load's own re-run (loadedStyle is a key) performs the leave. Permission not granted -> `activateLiveLocationIfPermitted` returns at `:182`, no leave, the restored camera stays; but without permission the old map could not have applied the view that produced the saved padding either (same gate), so only a revocation in between reaches it. Edge, noted, not a defect.
- **`leaveNavigation` on that map:** the restored mode is `CameraMode.NONE` (saved.following false, `cameraRestoreFor` `:160`), so `wasFollowing` is false: the camera eases flat/north-up/padding 0 from where it stands, `done` restores the zoom cap, following is not resumed. Right for a user who was panned away.
- **Could marking `active` misbehave elsewhere?** `active` is read outside the class only at `MapNavigationView.kt:54` (git grep over app/src/main). Inside: `applyView` sets it; `leaveNavigation` clears it and reads it in `done`. `shiftPadding` and the listener read `expected`, which stays null after `holdRestoredView`, so while panned away the side-padding shift still refuses (as before). A later Re-center/locate makes `view.following` true -> `applyView` as before (`startZoomGate.request(sameModeAsInFlight = expected == mode)` with expected null, so no gate confusion). A later navigation start after the leave is unaffected (leave reset `active`). If the saved camera was following, the restore mode is TRACKING and `applyView` would set `active` anyway; the hold is redundant and harmless. Both restore sites call it; idempotent.
- **Narrowness:** `restore.padding` is non-null only when `navigating` (`MapCameraMoves.kt:161`); a map reopening outside navigation is not marked (second test pins it). Hazel's reason for rejecting the camera-gated leave (it would flatten a user's own tilt at the next style swap) is right: the leave branch runs on every effect run with no view, keyed on loadedStyle.
- Minor: if the saved padding is a zero array (possible if a previous leave's explicit `padding(0,0,0,0)` is what a later navigating snapshot carries), the map is marked and the leave eases an already-flat camera. Harmless.

## Tests [observed + read]
- New marked case runs the production chain: snapshot -> saver -> `cameraRestoreFor` -> `applyFirstStyleCameraRestore` on a fresh `NavigationModeChange` -> the real `leaveNavigation` on the fixture map, asserting the camera handed to native `easeTo` (padding 0,0,0,0; tilt 0; bearing 0; target kept) and `active` false. The gate mirror (`navigationEnds`) copies `MapNavigationView.kt:54` but not the `isLocationComponentActivated` gate; Hazel discloses this (the effect needs a real MapView). Acceptable.
- Fixture: `easing = true` gives `Transform` an inert `MapView` holding a reflected `MapChangeReceiver` (field name `mapChangeReceiver`, 13.5.0). Default false -> `null` view as before; 3 call sites in app/src/test, and by-name shows 0 changed outcomes, so the other users are unaffected. An SDK bump could break the reflected field, loudly, in tests only. Note, not a defect.
- **Revert 42597020907** on c258d4fd3 (present in the local object store; ref deleted): `git diff 581664787 c258d4fd3` is exactly the one line `if (restore.padding != null) navigationModeChange.holdRestoredView()` removed. Compile: 0 ` e: ` lines in the Build/test job log, the Street-deleted job log and the release compile log; `compileDebugUnitTestKotlin` ran; release `BUILD SUCCESSFUL`. Filtered run 12 cases, 1 failure: the marked case, message `navigation's end eased the camera: the leave ran on the reopened map`, which is exactly what no marking produces (`active` false -> no leave -> `eased` null). The full run in the Street-deleted job: `6816 tests completed, 1 failed, 24 skipped`, same case. Not stale.
- **Green 42596934770** on 581664787: 6870 cases, 6846 pass, 24 skip, 0 fail. ede785657 differs from 581664787 by the report only (`git diff --stat`).
- **By name vs 42589007286** (d5b08f795, 6868 cases): 0 missing, 0 changed outcome, 2 new, both pass. Matches Hazel's figures.
- Second case ("no navigation view leaves its camera as before") passes before and after; Hazel flags it as pinning today's behaviour, not evidence. Correct.
- **Nothing silenced:** the delta touches MapCameraMoves, MapStyleReload, NavigationView, the test, the fixture, and the report; no `@Ignore`, no allowlist change, no assertion removed (0 `-...assert` lines in the diff). Forward change present at head (`MapCameraMoves.kt:183`).

## Defects back to Hazel
None.

## Owner's calls
1. Merge #511 as it stands (the S22 (d) jump fix plus this leftover fix) — or hold for S22 step 5 (navigate, pan away, font change, X, locate: walker centred, flat, north up). Everything here is source reading plus CI; the pixels are device-only.
2. Hazel's "decided beyond scope": the marking at both restore sites, and the pre-#511 tilt/bearing leftover being fixed by the same change. Both look right to me; nothing to undo.

## Notes
- Files stay small: MapCameraMoves +10, NavigationView +9, MapStyleReload 2 lines changed.
- Report's cause section and line citations match source at the head (`NavigationView.kt:364`, `:472` applyView's `active = true`, `MapNavigationView.kt:54`, `MapCameraMoves.kt:181-184`).

## Could not determine
- Device behaviour (what MapLibre draws past `easeTo`); whether X reaches the leave with the component activated on a phone — read from the callback order only, not observed.
- Whether MapLibre's `setCameraMode(NONE)` on a fresh component touches padding (SDK sources not local); same gap as my earlier review.
