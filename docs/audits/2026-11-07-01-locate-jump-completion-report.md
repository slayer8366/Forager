# Dispatch 2026-11-07-01, Amendment 1: locate stops jumping (completion report)

Hazel Cairnwright (coder), 2026-11-07. RECORD -2189. Branch `locate-jump`, cut from `origin/pre-main` at `82d4a091a`.
The dispatch's own text named the branch `locate-like-recenter`; Rowan's hand-off message named `locate-jump`, which is
what was used. Not built on `journal-locate-outline` (#506): none of the files this change touches is in #506's diff, nor
in nav-card-kind-line's, next-turn's or route-card's (checked against each branch's diff before each edit and again
before the fix commit).

## What the owner asked

- Maps tab: **"Yes, stop the jump"** ("Locate just glides to you with your zoom, like Re-center").
- Journal entry map: **"Glide, keep zoom, no follow"** ("Glides to your position, keeps the zoom, doesn't follow").

## The jump, reproduced, and its cause

Reproduced through the real screen in CI run 42399012115 (pre-fix code, `LocateKeepsCameraTest`):

> while the tap's fix was being fetched, the camera moved instantly (to (39.8283, -98.5795) at zoom 12.0)

That point is the app's fallback region's centre (`JOURNAL_PICKER_DEFAULT_REGION`), not the user. Cause, read from the code
and confirmed by that failure:

1. The locate tap does two things in one click (`CompactMapIconCluster.kt:61-66` on pre-main): asks following to resume
   (`resumeTrackingRequestId`, or Re-center's `onReturnToRoute` while navigating) and calls `onLocateMe()`.
2. `onLocateMe()` reaches `AvailabilityLocateMe.locateMe()`, which set `locateMeStatus = Loading` at once and then waited
   for the fix (`AndroidLocationProvider` waits on the location manager).
3. `Loading` dropped the fix held so far. With no search, `compactMapDisplayRegion` fell back to the default region; after
   a search, `compactMapLocateFocus` went null. Either way the map's camera target changed in the tap's own frame.
4. `SightingsMap` composes `MapDataRefresh` before `MapLocationDot`, so in that frame the refresh's effect runs first, sees
   "not following, target changed", and moves the camera instantly to the region at `zoomForRadiusKm` (`MapDataRefresh.kt`,
   the `shouldMoveCameraToTarget` branch). Only then does the locate effect turn following on, and MapLibre's 750 ms
   transition glides from the snapped place to the user, at the snapped zoom.

On a phone that reads as a snap and a zoom-out. Not seen on a phone by me (no phone in this dispatch).

## What was built

- `LocateMeStatus.Loading` carries the fix it replaces (`previous`); `lastLocateFix()` reads it, and
  `compactMapDisplayRegion` and `compactMapLocateFocus` read through it. The tap's fetch no longer changes the camera
  target, so nothing moves the camera at the tap but following. The strip's position and height are untouched: on this
  base nothing reads `locateMeStatus` but the two camera-target functions and the toasts (`git grep`), and the toasts'
  `when` treats `Loading` as before.
- `ui/map/LocateGlide.kt` (new): `isLocatePanOnly` (same region, a new locate-me point) and `glideToLocate` (an ease over
  `NAVIGATION_VIEW_TRANSITION_MILLIS`, 750 ms, the navigation view's camera duration, keeping zoom, bearing and tilt).
  `MapDataRefresh` uses it in place of the instant move for that case only. The journal entry map's locate now glides at
  the user's zoom and does not follow (`trackLiveLocation` stays false there).
- Tests: `LocateKeepsCameraTest` (Maps tab), `EntryMapLocateGlideTest` (entry map), a case added to
  `CompactMapLocateFocusTest`; `CameraMapFixture` records animated moves; `CameraFixtureMapState` is the fixture map's state.

No change to the tap itself, the icon bar, or anything a user sees beyond the camera moves above.

## Verified, and how

All CI; no Gradle on the laptop. Results read from each run's `unit-test-report` JUnit XML, after checking the test job's
log for compile errors (`e: file`: 0 in every run cited here).

| Run | Code | Result |
|---|---|---|
| 42397344190 | tests only, pre-fix | EntryMapLocateGlideTest red, expected reason: "the locate moved the camera instantly (to (46.1, -121.9) at zoom 13.0) instead of gliding". LocateKeepsCameraTest red on its own wrong premise (see below). Lint red on my test (`remember` returning Unit), fixed. |
| 42398381691 | test premise fixed, pre-fix | LocateKeepsCameraTest red: jump to the tap's own fix at zoom 12.0, a moment production does not produce (see Could not determine). |
| 42399012115 | test fetch answers later, pre-fix | **The reproduction**: "while the tap's fix was being fetched, the camera moved instantly (to (39.8283, -98.5795) at zoom 12.0)". |
| 42399685358 | fix `8b618a7` | 18 tests, 1 failed: the entry glide ended at zoom -1.0 (`newLatLng`'s "unchanged" marker). Maps tab green. |
| 42400398583 | `54e4829` (glide builds the whole end camera) | 9 tests (EntryMapLocateGlideTest, CartographyEntryReportScreenFullscreenTest), 0 failed. |
| 42401031766 | `54e4829`, full CI on PR #508 | every job green; 6,849 unit tests, 0 failed. Pre-main's last full run (42337576510, `1f6901a`; pre-main's only later commit is a `[skip ci]` doc change) has 6,846, 0 failed. By name, the difference is exactly the three new cases: `LocateKeepsCameraTest`, `EntryMapLocateGlideTest`, and `CompactMapLocateFocusTest` > "a second locate's fetch keeps the first fix as the target until its own arrives". |

Reverts, one line each, on throwaway branches cut from the commit named (`locate-jump` itself was never edited for a
revert; the forward lines were checked present on it afterwards):

- **Revert 1** (`AvailabilityLocateMe`: `Loading()` with no fix kept), run 42399733173 on `8b618a7`: LocateKeepsCameraTest
  red, "while the tap's fix was being fetched, the camera moved instantly (to (39.8283, -98.5795) at zoom 12.0)", the
  reproduction's own message. The entry test also failed in that run, by the zoom -1.0 bug fixed afterwards, not by this
  revert (it does not touch that path).
- **Revert 2** (`MapDataRefresh`: the glide's condition `false`), run 42401024827 on `54e4829`: EntryMapLocateGlideTest red,
  "the locate moved the camera instantly (to (46.1, -121.9) at zoom 13.0) instead of gliding". (The same revert on
  `8b618a7`, run 42399735846, failed the same way.)
- Two fix attempts on the entry glide (the -1.0 zoom, then the explicit end camera); the second passed.

## Confirmed vs inferred

- Confirmed (CI, through the real screen and a real MapLibre map object): the pre-fix jump at the tap, to the fallback at
  zoom 12.0; after the fix, no instant or animated camera move at the tap or when the fix arrives, following turned on; the
  entry map's locate is one ease to the fix at the user's zoom over 750 ms, with no following.
- Inferred: that this is the jump the owner sees. The test copies two things it cannot run: `MainActivity`'s permission
  launcher (with the permission held, androidx posts its answer to the main looper, so `locateMe()` runs before the next
  frame, as the test's post does) and the effect order inside `SightingsMap` (the test's slot composes the same two effects
  in the same order).
- Inferred: the glide itself. Following is MapLibre's own transition to the puck, which needs a location the test map does
  not have; the test reads that following was turned on and that nothing else moved the camera.

## Could not determine

- The navigating case is not exercised by a test. The fix is in the camera target, which does not depend on which action
  the tap runs, so it should cover Re-center's path too; the S22 steps below check it.
- Two rarer moments where a locate fix can still move the camera instantly, not built (no data showing them): a locate tap
  during a basemap change (the remembered tap's following starts after the new style's first refresh), and the very first
  tap that only raises the permission dialog (following cannot start until it is granted). Also a failed fetch after a fix
  (`Unavailable`) still drops the target to the fallback, as before.
- The first red run tested a different moment: its fake fetch answered at once, so the fix and `Loading` landed in one frame
  and the camera jumped to the fix at zoom 12. The production fetch does not do that, so the test was changed to answer
  later; that same-frame moment is not guarded by this change.

## Premises that were wrong

- My own first test assumed the map was not following before the pan; the map opens following (the locate effect's first
  run), so the test's pan now also dismisses following, as a drag does (red run 42397344190 failed on that premise, not on
  the jump).
- The amendment cites `MapDataRefresh.kt:144-155`; on this base the move is at lines 126-138. Same code.

## Decided beyond scope

- None user-visible. The `CameraMapFixture` change (an inert `MapView` with a real change receiver, so an animated move can
  run) affects every test using it; `RouteCameraOnReturnTest` was run with it.

## S22 steps (not run; for the owner or the phone check)

1. Maps tab, not navigating: pan away and zoom in or out. Tap locate. The map glides to you and keeps your zoom; no snap to
   another place first, no zoom change.
2. Run a search, pan away, tap locate: the same.
3. Navigating (portrait and landscape): pan away, tap locate: the map glides back as Re-center does, no snap first.
4. A journal entry, full-screen map: pan away, change zoom, tap locate. The map glides to you, keeps the zoom, and does
   not follow you afterwards (no live dot).
