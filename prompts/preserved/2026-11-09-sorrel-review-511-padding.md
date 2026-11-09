# Sorrel review: PR #511 padding delta (1ca65798e..cfa9824f8) and PR #507 test delta (7db90fe50..cf8b3a07f)

2026-11-09. Read from source at cfa9824f8 (detached checkout in ~/Zynergy/Forager, nothing changed or pushed). JUnit and compile logs from the `gh api` artifact zips under /mnt/work/scratch/sorrel-511b, parsed with python3 -I. No Gradle run.

## Verdict
- **#511 delta: mergeable for tonight, with one defect to hand to Hazel (owner's call whether it blocks).** The fix does what it says, the tests bite, Hazel's unverified point (navigationView at first style load) holds from source. The defect is a padding left on the map after navigation is *stopped on the recreated/returned map* while panned away; it is the sibling of a gap that already exists for tilt and bearing before this PR.
- **#507 delta: pass.** Test-only plus report; no production change; the test bites for the reason the revert should produce.

## 1. PR #511 (cfa9824f8)

### Hazel's unverified point: is `state.navigationView` set at the recreated map's first style load? Yes [read]
- `SightingsMapState.navigationView` reads `navigationViewState.value` (SightingsMapState.kt:114), a `rememberUpdatedState(navigationView)` (SightingsMap.kt:279), fed by `renderMode.navigationView` (MapSlot.kt:149), set `if (isNavigating) NavigationViewRequest(...)` (CompactMapMapSlot.kt:130).
- `isNavigating = isReturning || isArrivalHeld || navigatingToWaypoint != null` (AvailabilityScreen.kt:245). All three inputs come from ViewModel StateFlows collected with `collectAsState()` and no initial value: `trackUiState.isReturning / isArrivalHeld / waypointsLoaded` (MainActivity.kt:431, 701-702, 726) and `uiState.waypointNavigation` (MainActivity.kt:372). `trackRecordingViewModel` is `by viewModels` (MainActivity.kt:211), so it survives the recreation and the first composition reads its current value. The style load runs after `getMapAsync`, i.e. after that composition. So on a font/night-mode recreation `navigationView` is non-null at the first style load. Not run; read only.
- Scope: a font change recreates MainActivity (its configChanges, AndroidManifest.xml:176, has no `fontScale`); a rotation does not (orientation|screenSize handled). So the trigger set here is font, locale, uiMode (night), density — and the tab-change path (new MapView, same restore).

### The "not navigating keeps its own padding" guard [read]
- `cameraRestoreFor(..., navigating)` returns `padding = if (navigating) saved.padding else null` (MapCameraMoves.kt:161); `applyCameraRestore` only calls `.padding(...)` when non-null (MapCameraMoves.kt:172). The one production caller passes `navigating = state.navigationView != null` (MapStyleReload.kt:115); other callers are tests, including `RecreationMapStandIn.kt:87`, which keeps the default `false` — so the existing recreation stand-in tests do not exercise the padding path (Hazel says so in the report).
- The guard does what it claims for the case it names (navigation ended while the map was away): the saved padding is dropped and the new map opens with none.

### DEFECT (back to Hazel): padding left on after navigation ends *on* the recreated or returned map [read, not seen on a phone]
Path: navigating, following broken (panned away), then a font/night change or a tab round-trip, then the user stops navigation on the Maps tab.
- The restore puts the saved navigation padding on the new map (this PR). `NavigationModeChange` is `remember { NavigationModeChange() }` per SightingsMap (SightingsMap.kt:306), so the new map's instance has `active = false`; `active` is only set true by `applyView` (NavigationView.kt:463), which runs only `if (view.following)` (MapNavigationView.kt:63-72) — not when panned away.
- When navigation ends, the leave branch runs `leaveNavigation` only `if (state.navigationModeChange.active)` (MapNavigationView.kt:54-58). So on this map it is skipped, and the padding the restore applied (0, top quarter, D29 right inset, 0) stays. Nothing else writes padding: `shiftPadding` and `applyView` act only while following; `activateLiveLocationIfPermitted` only sets `cameraMode` (LiveLocationPuck.kt:190). The next locate (TRACKING) centres the walker in the padded view, i.e. below centre and shifted sideways, until the next navigation's leave clears it.
- Not new in kind: before this PR the same path already left the restored **tilt (45°) and bearing** unflattened on stop, for the same `active=false` reason. This PR adds padding to that leftover. Hazel's own comment ("only a map that applied the view takes it off again") names the mechanism but applied it to the other direction only.
- Suggested change (Hazel's design): when the first-style-load restore carries padding, mark the new map as holding the view, e.g. `state.navigationModeChange.active = true` beside `applyCameraRestore` in MapStyleReload.kt (then `leaveNavigation` runs on stop and eases flat with padding 0, as it does today on an un-recreated map); or gate the leave branch on `active || camera padding/tilt non-zero`. Test: extend MapCameraRestorePaddingTest's chain — after the padded restore, run the leave decision (`active` true, or `leaveNavigation` on the fixture map) and assert the camera the native side is given has padding (0,0,0,0) and tilt 0; red by removing the new marking. S22 step: navigate, pan away, change font, tap the navigation card's X, then locate — the walker should sit at the centre with no tilt.
- Per "see it on the phone before fixing": this is a source reading. Hazel's S22 step 4 covers stop-while-away, not stop-after-return; add the step above either way.

### The tests bite [observed]
- Red 42588847395 on fb10089f7: tree = green d5b08f795 minus 6 forward lines in MapCameraMemoryRecreation.kt and MapCameraMoves.kt (`git diff --stat fb10089f7 d5b08f795`). Compile log: 0 `e:`/`error:` lines, BUILD SUCCESSFUL. JUnit (10 cases in the two classes): 2 failures — `MapCameraRestorePaddingTest."a view panned away ... reopens at the padding it was left at"`: "the padding the target is the centre of: actual array was null"; `MapCameraMemoryRecreationTest."... brings back the camera's padding"`: "expected:<[0.0, 270.0, 1544.0, 0.0]> but was:<null>". Both are messages this removal produces.
- Revert 42588978971 on 313a2dbc8: exactly one line removed vs d5b08f795, the `.apply { restore.padding?.let { padding(...) } }`. Compile 0 errors. JUnit: 1 failure, the end-to-end case, same message; the saver case passes — correct for this revert, and distinct from the red run, so not stale output.
- Green 42589007286 on d5b08f795: 6868 cases, 0 failures, 24 skipped; the 3 new cases pass. cfa9824f8 differs from d5b08f795 only by the report (`git diff --stat`: docs/audits/2026-11-09-keep-follow-on-recreate.md).
- Hazel's "6814 completed" Gradle counts for red/revert are not in the artifacts (filtered runs upload 10 cases); not needed for the bite.
- The end-to-end test asserts the camera the fixture's native `jumpTo` was given (CameraMapFixture.kt:80-92 records it), so it is a value check on the production chain, not pixels. Hazel states the pixel claim as inferred; agreed, S22 step 1 is the evidence for it.
- "not navigating ... keeps its own padding" passes before and after; Hazel flags it as not evidence for the fix. Correct.

### Notes
- Files touched stay small (MapCameraMoves 257, MapListeners 260, MapStyleReload 214 lines). New test file 68 lines.
- `SavedCamera` gains a field; the saver is process-token keyed so a shape change across processes cannot be read (MapCameraMemoryRecreation.kt:38). Fine.
- Cover snapshot (SightingsMap.kt:764) and `searchFrameSnapshot` (MapCameraMoves.kt:96) carry no padding (default null); the cover is picture-only and a search frame is not-following. No issue found.
- Report's "Problem 2" (credit band at large font) is diagnosis only and matches the files it cites; not checked further.

### Owner's calls
1. Fix the stop-after-return padding leftover in this PR tonight, or ship #511 as is (fixes the S22 (d) jump) and take the leftover (with its pre-existing tilt/bearing sibling) as a follow-up. The visible symptom is after a navigate → pan away → font/night change or tab round-trip → Stop: the next locate puts the walker off-centre until another navigation ends.
2. Problem 2 (credit band at font 2.0) behaviour choice, as Hazel listed.

## 2. PR #507 (7db90fe50..cf8b3a07f)
- `git diff --stat`: only `app/src/test/.../RouteCardStopDeletedWhileNavigatingTest.kt` (+130) and the report; **no `app/src/main` change** [observed].
- Green 42591776170 on d62e04f52: 7007 cases, 0 failures, 24 skipped; the new test passes. cf8b3a07f differs from d62e04f52 only in the report (3 lines).
- Revert 42591779438 on 0015c7ff1: one-line production edit vs d62e04f52 (`RouteStopNavigateTarget.kt`: the link returned without checking the waypoint still exists). Compile 0 errors, BUILD SUCCESSFUL. JUnit (8 cases, filtered): new test fails "Failed to assert the following: (is not enabled)" on the Navigate node — the failure this revert produces; `RouteStopNavigateTargetTest` gone-link case also fails with "expected Waypoint(id=wp-creek) but was Waypoint(id=wp-gone)" — the same edit's. Not stale.
- Test drives the real compact Maps tab: real touch on the marker, the handle, and the navigation card's X; navigation started through the ViewModel's own entry. Hazel discloses the delete enters as the list losing the waypoint rather than the Journal swipe. Acceptable for the claim made.
- Finding 2 (bubble under the expanded route card) is left to the owner; the composition-order reading is cited to lines on both refs; not re-read by me.

## Could not determine
- Device behaviour of either change; all of the above is source reading plus CI artifacts.
- Whether MapLibre's `setCameraMode(NONE)` on a fresh component touches padding (SDK sources not in the Gradle cache); the app side (LiveLocationPuck.kt:190) only sets the mode.
- Hazel's full-suite counts for the red/revert runs (artifacts hold the filtered classes only).
