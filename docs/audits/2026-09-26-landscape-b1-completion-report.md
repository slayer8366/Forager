# Landscape B1: completion report

Date: 2026-09-26. Build step B1 of `docs/plans/landscape-phone-design.md`:
height-aware window classification, the navigation rail on the charger-port
side, and insets at both landscape rotations. Branch `landscape-b1` from
`pre-main` `bc54f6d`. Record: intent `2026-09-26-96`, store copy
`prompts/preserved/2026-09-26-35.md`; the terminal cites CI and the backup.

**Nothing here was run on a device.** Robolectric reports zero window insets
(CLAUDE.md, "Known pitfalls"), so every claim below about insets is about
which side something is on, not how far it sits from a system bar. The device
items are listed at the end for B4.

## What was decided, and by whom

The dispatch closed R1-R11 (R1 the owner's "Classify by window"; R2-R11 the
planner's). The coder stopped once, after the sweep, on how the rail sits on
the Map tab and what it does in fullscreen. The planner ruled A2 (one opaque
rail beside all content) and accepted five readings (planner log line 1427),
recorded as R12-R18. **The owner then corrected R12:** "The problem with it
not being an overlay is that the map resizes when hiding the UI and that's a
UX problem" (planner log line 1443). The planner revised R12, R13 and R17
(line 1447). All of these are recorded, the superseded ones kept, in the
design document's "Resolutions" section and its "Revised on the owner's
correction" subsection.

## What was built

| Piece | Where |
|---|---|
| `isShortWindow()`: `screenHeightDp < 480` | `ui/adaptive/ShortWindow.kt` (new). `currentWindowWidthClass()` unchanged. |
| A short window takes the compact tree whatever its width | `AvailabilityScreen.kt`, the tree branch: `COMPACT \|\| isShortWindow` |
| The one rotation-to-port-edge mapping | `ui/adaptive/PortEdge.kt` (new): `portEdgeFor`, `punchHoleEdgeFor`, `currentWindowPortEdge`. `cameraArrangement` (`ui/log/CameraArrangement.kt`) now calls `portEdgeFor`; `ScreenEdge` stays in the camera file (R16). |
| `ForagerNavigationRail` | `AvailabilityScreen.kt`. Material 3 `NavigationRail`, same destinations, labels, icons, colours and selection as `ForagerBottomNav`, same `onTabSelected`; `navigationBars` inset on its own side only; no header Search action (R14). |
| Map tab, short landscape | The rail is an 80% overlay on the port edge, composed in the bottom bar's layer inside `CompactMapTab`. The map is full-bleed (Scaffold reserves only the top, as in portrait). The controls — search bar, compass strip, taxon chip, HUD, cluster and its restore handle, the add tile, the map-mode picker — are each padded by `mapControlsPadding`: `displayCutout` on the sides, plus the rail's measured width on the port side, or the `navigationBars` inset there in fullscreen, where the rail is absent with no animation (R12, R13, R17 as revised). |
| Other tabs, short landscape | The Scaffold content is a `Row`: an opaque rail beside the content on the port side. Content insets: `statusBars` top, `displayCutout` sides, IME bottom, nothing for a system bar at the bottom. |
| No stale bottom band | `bottomNavHeightPx` is zeroed, and read as 0, while the rail shows; `CompactMapTab`'s own copy likewise (R18). |

Not padded on purpose: the tapped-sighting bubble and the centre-pin picker.
Both are positioned against the map itself; padding them asymmetrically would
move the pin off the map's true centre, so the pin would mark a different
point from the one picked.

## Evidence

**Tests (new): `AvailabilityScreenShortLandscapeTest.kt`**, 13 tests in three
classes, all through the real `AvailabilityScreen`:
`AvailabilityScreenShortLandscapeTest` (`w823dp-h384dp-land`, 11),
`AvailabilityScreenTurnToShortLandscapeTest` (portrait turned to short
landscape by providing a landscape `Configuration`, 1), and
`AvailabilityScreenPortraitBottomNavPinTest` (`w360dp-h640dp`, 1, a pin that
passes before and after by design).

**Tests first.** `c431490` (the A2 version) failed 8 of 9 at `bc54f6d`'s main
code. After the owner's correction, `c31b13f` (the revised tests) was run
against that same main code in a detached check worktree
(`~/Zynergy/forager-wt/landscape-b1-tfcheck`, at `c431490`) and failed 12 of
13, each on the wide tree: no node "Journal", no tag `compact-navigation-rail`,
no "Fullscreen", "Trip Planner" displayed by the permanent drawer, the map
still beside the list, no "Cartography" after the turn. The portrait pin
passed. One test of the revised set passed at the base in its first form (a
long-press on "List" also leaves the map alone in the wide tree's tab row); it
was tightened before commit to assert the pressed point lies on the rail, and
then failed at the base with no `compact-navigation-rail` node.

**Rotation (R10).** Robolectric reports `ROTATION_90` and `ROTATION_270`
distinctly through `ShadowDisplay.setRotation`, as the camera's landscape tests
already rely on. Each rotation test asserts first that the screen's own view
read the pinned rotation. So the port edge is tested through composition at
both rotations, not only through the function.

**Revert checks** (runner saves a copy, refuses on compile errors, reads only
fresh XML, restores from the copy, confirms the forward change; all three: no
compile errors, restored, forward change present):

1. Height condition (`COMPACT || isShortWindow` to `COMPACT`): 12 of 13 fail,
   each on the wide tree, e.g. "could not find any node that satisfies: (Text
   ... contains 'Journal')", "the rail is showing to begin with", "'Trip
   Planner' ... is displayed!".
2. Rail replacing the bottom bar (`showRail = false`): 10 of 13 fail, e.g.
   "Seasonal is in the rail's one column expected:<79.5> but was:<246.0>"
   (the labels are a row), "could not find any node that satisfies: (TestTag =
   'compact-navigation-rail')", "landscape labels are one column
   expected:<35.5> but was:<114.5>". The compact-tree and fullscreen tests
   pass, correctly: this revert keeps the compact tree, and in fullscreen the
   bottom bar slides away.
3. Port side (`portEdge = ScreenEdge.Right`): only the two `ROTATION_270`
   tests fail: "the rail is on the window's left edge expected:<0.0> but
   was:<743.0>" and "a long-press at (827.0.dp, 89.0.dp) must reach the map",
   a sample point beside a rail that is on the wrong side.

**A test-design failure found by its own guard.** The first long-press
sampler skipped any point at the cluster's height and sampled zero points: in
a 384 dp window the cluster spans 8-376 dp. The sample-count assertion caught
it; the sampler now skips only points inside a control's bounds.

**Suite** (`testDebugUnitTest`, JUnit XML): before, at `bc54f6d`, 214 classes,
1668 tests, 0 failures, 0 errors, 24 skipped; after, 217 classes, 1681 tests,
0 failures, 0 errors, 24 skipped. Every one of the 1668 baseline tests is
present with the same status; `AvailabilityScreenWideWindowLayoutTest` (7),
the `AvailabilityScreenLayout*` classes (54), `CameraArrangementTest` (4) and
`InAppCameraDialogLandscapeTest` (15) all pass unchanged.

## The map's controls in the interim, from the Robolectric layout only

B1 does not rearrange the map's controls (B2 does). Measured under
Robolectric at `w823dp-h384dp`, zero insets, map `0-823 x 0-384` in every
case:

| State | Rail | Search bar | Compass strip (text) | Cluster |
|---|---|---|---|---|
| `ROTATION_90` | x 743-823, full height | x 0-743, y 0-85 | x 26-735, y 85-121 | x 687-735, y 8-376 |
| `ROTATION_90`, fullscreen | absent | slid away | x 26-815, y 0-36 | x 767-815, y 8-376 |
| `ROTATION_270` | x 0-80, full height | x 80-823, y 0-85 | x 106-815, y 85-121 | x 767-815, y 8-376 |
| `ROTATION_270`, fullscreen | absent | slid away | x 26-815, y 0-36 | x 767-815, y 8-376 |

What that looks like: the search bar and compass strip take the top 121 dp of
a 384 dp window; the cluster runs almost the full height on the right (beside
the rail at `ROTATION_90`, at the window edge at `ROTATION_270`). The search
dropdown, when open, is not padded for the rail and covers it (B2's search
sheet replaces it).

## R10's other check: an end-edge drawer or side sheet in Material 3 `1.5.0-alpha26`

Read from the artifact's `classes.jar`, not run: no class name contains
"SideSheet"; `ModalNavigationDrawer`'s parameters are drawer content,
modifier, state, `gesturesEnabled`, scrim colour and content, with no edge or
anchor parameter; `DismissibleNavigationDrawer` and `ModalWideNavigationRail`
have none either. So an end-anchored drawer or side sheet is not built in.
Whether a layout-direction workaround would do it is unverified. B2/B3.

## Device items, for B4 (none run here)

At both `ROTATION_90` and `ROTATION_270`:

- The rail sits next to the system bar on the port side.
- No rail item is under the system bar.
- No control sits in the cut-out band; map tiles do draw under it.
- The status bar does not cover content.
- In fullscreen, no control sits under the system bar on the port side.
- The map does not change size when the rail hides or shows.
