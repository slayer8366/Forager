# UI split survey: how the large UI files break into files of about 200 lines

**Date:** 2026-10-18 (UTC). **Dispatch:** `prompts/preserved/2026-10-18-09.md` with its Addendum 1 (branch
`records-after-173`). **Record:** Forager RECORD -1323 (direction) and -1324 (this survey's intent). **Written by:**
Hazel Cairnwright (app coder), for Rowan Waymark (app planner). **Branch:** `ui-split-survey`, cut from `origin/pre-main`.

**Read-only.** No source file was changed. No Gradle, emulator or phone was run. The only file on this branch is this
report. There is no PR and no index row, because this is for the next release and `docs/audits/README.md` is a
serialization point (CLAUDE.md, "Known pitfalls").

**How it was done.** I took the inventory, read the cross-cutting files (`MapSlot.kt`, `MapCameraMemory.kt`, CI, the
scripts, the two earlier split reports) and wrote this. The per-file seam tables come from seven read-only survey
helpers, each of which read its files in full and tagged every claim. I spot-checked their claims about the map's
keep-alive, `movableContentOf`, the poll loop and the inventory myself (section 9 says which).

**Tags.** **R** = read: I or a helper read the line. **O** = observed: a `wc` or `grep` output. **I** = inferred:
reasoning, not read. Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/ui/` unless they start with
`app/`, `scripts/`, `docs/` or `.github/`.

---

## 0. The short version, for the owner

- **51 UI files are over 300 lines, and 22 of those are over 600** (O). Together the 187 UI files hold 54,513 lines (O).
- **Most of the big files are big for one of two reasons.**
  - One very long screen function holds everything the screen remembers. The main screen, the frame around the
    tabs, the Maps tab and the map itself are each built around a single function of 600 to 1,660 lines. Moving
    the pieces *around* such a function is safe and mechanical. Breaking up the function itself is a real change:
    the pieces share what the screen remembers, so that has to go into a holder first.
  - The brains behind the screens (the "ViewModels") are each one class. In this language a class can't be spread
    over several files, so splitting one means handing parts of its work to smaller helper classes. That is a
    design step, not a move.
- **About a third of the big-file lines can move today with no change in behaviour.** This is the plain
  arithmetic, the drawing helpers, the labels and the tags. In the map file alone about 1,380 of 2,753 lines are
  like that.
- **The owner's goal, one part per file, so several agents can fix different parts at once, is reachable for the
  controls, cards and sheets.** It is not reachable by moves alone for four places every change still passes
  through: the main screen's parameter list, the frame around the tabs, the Maps tab's own body and the map's own
  body (section 6).
- **This has been done before, safely.** On 2026-09-26 the main screen was cut from 5,674 to 2,229 lines by a pure
  move (Stage F), and the frame around the tabs was extracted on its own (Stage G). Both reports set out a check
  method that proves nothing changed (section 7.1). Stage G also caught a move that would have **saved the
  fullscreen preference inverted**, which is exactly the kind of silent change a move can make.
- **The first three PRs I'd propose are pure moves of plain-value code.** They are, in order: the navigation card's
  wording rules; the map's camera, credit and location-dot helpers; and the map's layer and data helpers. Each one is
  checked by the Stage F method plus the phone launch check (section 8).
- **The map is not kept alive across tabs, and nothing uses `movableContentOf` for it.** A new map is built each time
  the Maps tab comes back. The camera and the way back are carried across in two small "memories" held above the tab.
  The risk for a split is moving those memories, not a keep-alive mechanism (section 9.4).

---

## 1. Premises checked

| Premise (dispatch or RECORD) | Checked against | Result |
|---|---|---|
| pre-main head is `8c90626f` | `git ls-remote origin pre-main` at the start | **Held**: `8c90626f4516da2ae1dddcd44e7c3cde14ce3503` (O) |
| 187 Kotlin files under `ui/` (-1323) | `find ui -name '*.kt' \| wc -l` | **Held**: 187 (O) |
| The eight largest and their counts (-1323) | `wc -l` | **Held**, all eight counts exact (O) |
| "the map files hold the hardest seams (`movableContentOf`, touch routing)" (-1324 prediction); dispatch item 5, "`movableContentOf` keeping the MapView alive" | grep, and reading `MapSlot.kt`, `MapCameraMemory.kt`, `SightingsMap.kt` | **Wrong, in part.** `movableContentOf` appears **once** in all app sources, at `availability/AvailabilityOfflineMapsUi.kt:194` (O), in the Download Maps picker. **The main map is not kept alive.** See 5.3 |
| "most of the size is in a few files whose composables share private state through the file" (-1324) | helpers' seam tables | **Held, with a refinement.** The sharing is mostly through *local* state inside one long function, not through file-level `private` state (sections 3 and 5) |
| "the first PRs can be pure moves of layout helpers and pure functions" (-1324) | helpers' seam tables | **Held** (section 8) |

---

## 2. Inventory: every UI file over 300 lines

51 files (O, `wc -l` at `8c90626f`). The description is what the user sees the file draw or do. Files marked **\*** are
over 600 lines and have their seams in sections 3 and 4.

| Lines | File | What the user sees |
|---|---|---|
| 2,753 | **\*** `map/SightingsMap.kt` | The map itself: basemap, search spot, sighting dots, trips, pins, finds, photos, tracks, offline-area circles, forecast cells, the moving location dot, the credit line; what a tap means; the camera remembered across tab changes |
| 2,097 | **\*** `availability/AvailabilityCompactMapUi.kt` | The Maps tab: map, compass strip, draggable icon bar with record pill, chips, tapped-pin bubble, navigation card, Return to Route, legend, bottom bar or rail, add menu, Layers sheet, centre-pin picker |
| 2,072 | **\*** `availability/AvailabilityScreen.kt` | The whole main screen: which tab, the Tools drawer, snackbars, what Back does at every step, the in-app camera on top |
| 1,954 | **\*** `availability/AvailabilityCompactScaffold.kt` | The frame around every tab: search bar and dropdown, snackbar, bottom bar or side rail, the fade between tabs, how far each tab keeps clear of the system bars, camera cut-out and keyboard |
| 1,950 | **\*** `availability/AvailabilityViewModel.kt` | (No screen.) The engine behind the main screen: searches, species lookup, live location, what the map draws, layers, offline maps, planned trips, waypoint navigation, stored settings |
| 1,372 | **\*** `availability/NavigationHud.kt` | The navigation card: distance, turn words and arrow, north arrow and heading, sunset and back-by, status line, Try again, the X |
| 1,368 | **\*** `availability/AvailabilityMapControlsUi.kt` | The compass strip across the top of the map, the record and return pill, the "+" chooser with its dimming |
| 1,359 | **\*** `track/TrackRecordingViewModel.kt` | (No screen.) Record and Stop, the trail while recording, start and end markers, the way home, arrival, sunset and back-by, waypoint and track lists with Undo |
| 1,264 | **\*** `log/MushroomLogViewModel.kt` | (No screen.) The finds log: list, create, edit, photos, delete |
| 1,235 | **\*** `map/MapChrome.kt` | The shared look of everything drawn over a map: the 80% fill, outlines, icon-bar buttons, handles, touch sizes |
| 1,205 | **\*** `availability/AvailabilitySearchUi.kt` | The search bar at the top and its dropdown of places, species and recent searches |
| 1,088 | **\*** `log/JournalTab.kt` | The Journal tab: Records and Cartography, and the pages that slide over them |
| 963 | **\*** `log/CartographyEntryReportScreen.kt` | A saved journal entry read as a report: the writing, tags, and every kept find, track, waypoint, map and photo |
| 939 | **\*** `availability/AvailabilityMapIconCluster.kt` | The floating icon bar: long-press and drag, snap to a side, minimise, glide, keep clear of other chrome, the L shape in landscape |
| 863 | **\*** `log/CartographyViewModel.kt` | (No screen.) Journal entries: list, create, edit, what each keeps |
| 810 | **\*** `log/CartographyScreen.kt` | The Cartography list of journal entries |
| 737 | **\*** `availability/AvailabilityOfflineMapsUi.kt` | Download Maps: a map with a centre pin, the radius, status, Download and Delete |
| 736 | **\*** `log/RecordDetailsSheet.kt` | The details sheet that slides up when a waypoint, track or offline map row is tapped |
| 674 | **\*** `log/InAppCameraDialog.kt` | The in-app camera: shoot several photos, then leave |
| 662 | **\*** `availability/AvailabilitySettingsUi.kt` | The Tools drawer and Settings, with the build line at the bottom |
| 630 | **\*** `map/MapBubble.kt` | The bubble that appears when a map thing is tapped, with its tail |
| 610 | **\*** `map/MapSlot.kt` | (No screen of its own.) The doorway between the screens and the map: what a screen hands the map, and the stand-in tests use |
| 561 | `map/NavigationView.kt` | How the map tilts and faces while navigating |
| 547 | `map/MapLayersSheet.kt` | The Layers sheet: switches, opacity, order |
| 546 | `log/PhotoViewerDialog.kt` | The full-screen photo viewer: pinch, pan, double-tap, save a copy |
| 530 | `log/CartographyEntryEditScreen.kt` | Writing and curating a journal entry |
| 508 | `map/CentrePinLocationPicker.kt` | Placing something by panning the map under a fixed centre pin |
| 486 | `map/MapBubbles.kt` | What each map bubble says (plain values) |
| 473 | `map/layers/MapLayers.kt` | The map layer list and draw order (plain values) |
| 473 | `log/RecordsTab.kt` | The Records tab and its submenus |
| 466 | `availability/AvailabilityNavigationUi.kt` | The bottom bar and side rail, and the exit-navigation prompt |
| 436 | `backup/BackupViewModel.kt` | (No screen.) Backup and restore |
| 406 | `log/LogEntryReportScreen.kt` | A find read as a report |
| 406 | `log/EntriesAlbum.kt` | The album: every photo, grouped by day |
| 405 | `availability/AvailabilityUiState.kt` | (No screen.) Everything the main screen shows, as one value |
| 401 | `log/CartographyEntryCard.kt` | One card in the entries timeline |
| 390 | `log/LogEntryDetailScreen.kt` | A find's edit form |
| 388 | `availability/AvailabilityMapOverlaysUi.kt` | The trip date and waypoint name dialogs, the observation bubble, map messages |
| 381 | `log/FindsGalleryScreen.kt` | The Finds grid |
| 380 | `map/FanOutLayers.kt` | Stacked markers fanning out when tapped |
| 380 | `availability/MapQuickSettings.kt` | The map's three-dot quick settings menu |
| 361 | `map/BasemapStyles.kt` | Which basemap tiles draw, and the night style |
| 358 | `log/TwoStageSwipe.kt` | Swipe once to reveal Edit, again to delete |
| 353 | `availability/AvailabilitySeasonalUi.kt` | The Seasonal tab: weather and the rain-to-fruiting check |
| 345 | `log/RecordsLogbookList.kt` | Records' All view, grouped by kind |
| 324 | `map/MarkerGlyphs.kt` | The marker shapes |
| 315 | `availability/AvailabilityTripsWaypointsUi.kt` | The planned-trips list |
| 312 | `log/EntryContentsPanel.kt` | The editor's "In this entry" panel |
| 310 | `track/TrackExportPanel.kt` | The list of tracks to share as files |
| 309 | `motion/PageSlide.kt` | How pages slide over each other |
| 303 | `availability/AvailabilityPureFunctions.kt` | (No screen.) Small labels and sums the screens share |

The descriptions for files under 600 lines come from each file's header comment and declarations (R), not a full read.
`ui/motion/**` is owned by the owner in `.github/CODEOWNERS` (O), so `motion/PageSlide.kt` would need the owner's review
for any split.

---

## 3. The five largest files: seams, dependencies, tests

Each seam has a line range at `8c90626f`. **Pure** means it can move to another file in the same package with no
behaviour change, the only edit being `private` to `internal` where noted. For each file the "tests that reach in"
list covers `internal` declarations and test tags. **No file in this survey has a `@VisibleForTesting` member** (O,
grep count 0 under `ui/`). Tests reach in through `internal` names and tags only.

**One rule covers every test answer below.** Kotlin finds a top-level name by package, not by file. So a move
within the same package needs **no test change**. A move into a new sub-package needs imports added to every test
and caller that uses the moved names: about 80 test files for `ui/availability` alone (helper count, O). Every
proposal in section 5 therefore stays in the existing package.

### 3.1 The map: `map/SightingsMap.kt` (2,753 lines)

**What the user sees:** the map itself and everything drawn on it (section 2).

**Its shape.** One function, `SightingsMap`, runs from `:133` (KDoc) to `:1491`, about 1,360 lines (R). After it come
about 1,250 lines of plain helpers that work out what to draw, how lines thicken with zoom, where the camera should go,
where the credit line sits, and how the location dot looks (R). There is no Compose touch code in the file. Touches
reach the map through MapLibre's own listeners, registered once at `:647-805`, and a raw touch listener set up when
the map view is made, at `:1414-1439`. That listener stops a scrolling parent from stealing a drag, and it never
consumes a touch (R).

| Seam | Lines | What | Pure? | What makes it hard |
|---|---|---|---|---|
| S1 | 133-1491 | `SightingsMap`, the one function (regions below) | **No**, as pieces | about 20 "latest value" holders read by listeners registered once; ~10 effects keyed on the loaded style; the map view's lifetime; the dispose order of two `DisposableEffect`s (`:546`, `:620`) |
| S2 | 1493-1522 | `MapCreditCaption` and its tag and pill size | **Yes** | none |
| S3, S4 | 1524-1623, 1647-1720 | setting up the overlay layers; turning a layer spec into a map layer; paint and opacity | Yes; `initializeOverlayLayers`, `applyLayerPaint` and helpers become `internal` | none |
| S5 | 1625-1628 | `TapHandlerRef` | stays with S1 | none |
| S6 | 1630-1645, 2222-2275, 2605-2631 | sighting dot look | Yes; `SIGHTING_DOT_RADIUS_PX` becomes `internal` | none |
| S7 | 2560-2603, 2713-2714 | source and layer ids, feature-id names, log tags | Yes, but **every private id alias and `SIGHTINGS_MAP_TAG` becomes `internal`**: they are read by S1, S3, S4, S8-S14 | the one block of private names the whole file shares |
| S8, S9 | 1722-1770, 2165-2220, 2478-2558 | what a tap hit; refreshing overlay data; building every feature collection | Yes; `refreshOverlayData`, `emptyFeatureCollection` become `internal` | none |
| S10 | 1772-1887, 1944-1979, 2742-2753 | camera decisions: when to move, search framing, restoring the camera, zoom caps | Yes; `applyCameraRestore`, `applyCameraFrame`, `CoverPhase`, `MAPS_COMEBACK_FRAMES_LOGGED` become `internal` or stay with S1 | three KDoc blocks sit on the wrong declarations (`:1772-1786`, `:1801-1804`, `:1889-1894`), and a move has to carry them exactly |
| S11 | 1889-1942 | where the credit, attribution and logo sit | **Yes**, no visibility change | none |
| S12 | 2277-2476, 2715-2740 | line specs and widths by zoom; journal halos | Yes; `lineLayerFor` and stroke constants become `internal` | none |
| S13 | 1981-2163 | the live location dot | Yes; `activateLiveLocationIfPermitted` becomes `internal` | none |
| S14 | 2633-2711 | recolouring offline tiles at night | Yes; `applyOfflineNightRecolour` becomes `internal` | none |

**Regions inside S1** (each can only move by extracting a function, which is not a pure move): the parameters
(`:217-312`); the map view made in `remember` (`:322-327`); latest-value holders, compass and location engines
(`:329-415`); the nudge and fling guard (`:416-455`); the cover picture shown while the map reloads (`:461-493`);
camera bookkeeping (`:508-544`); lifecycle and teardown (`:546-575`); listener registration (`:617-807`); style reload
(`:809-931`); data refresh and camera moves (`:942-1005`, an effect keyed on 16 values); the marker fan (`:1007-1104`);
forecast cells (`:1106-1157`); the location dot and locate button (`:1159-1209`); the navigation view (`:1211-1256`);
attribution insets (`:1258-1322`); the layout (`:1385-1490`) (R, helper).

**Tests that reach in.** 26 test classes, all in `ui/map` except two in `ui/availability` that import by package
name: `SightingsMapOverlayDataTest`, `MapLayerFeatureIdTest`, `TrackWidthByZoomTest`, `JournalEntriesOnMapTest`,
`JournalEntriesOnMapScreenTest`, `SearchClearAndFrameTest`, `MapCreditCaptionTouchTest`, `MapCreditCaptionNoOutlineTest`,
`AttributionBesideCreditTest`, `LogoBesideAttributionTest`, `LayoutFixesMapHelpersTest`, `MapCameraRequestTest`,
`CameraTargetRecenterGuardTest`, `UserCameraGestureTest`, `LocationIndicatorMotionTest`, `LiveLocationPuckOrderTest`,
`LiveLocationPuckSwapTest`, `PuckImageTintTest`, `PositionLookOptionsTest`, `NavigationGestureOptionsTest`,
`FanOutRenderSignalTest`, `FanCircleSymbolTest`, `JournalHaloGlyphTest`, `MarkerGlyphsTest`, `BasemapTest`,
`MapStartGateTest` (O, helper `grep -w`). Every one reaches plain helpers (S2, S6-S13). **No test drives the real map's
touch path**, because MapLibre cannot run under Robolectric (`:631`, `:777`, `:1218`, R). A same-package move changes
no test.

### 3.2 The Maps tab: `availability/AvailabilityCompactMapUi.kt` (2,097 lines)

**What the user sees:** the Maps tab and every control on it (section 2).

**Its shape.** One function, `CompactMapTab` (`:293-927`, 635 lines), holds the tab's state. It composes nine children
**in a fixed order, which is the order things paint and the order touches are tried**: map (`:712`), search bar slot
(`:756`), bubble (`:760`), icon cluster (`:779`), chips (`:801`), compass strip (`:813`), landscape navigation card
(`:848`), bottom chrome (`:868`), pickers (`:884`) (R). That order is documented as load-bearing at `:796-800`,
`:1207-1212` and `:1869-1887` (R). **Each child is already its own private function**, which is why most of this file
can move.

| Seam | Lines | What the user sees | Pure? | What makes it hard |
|---|---|---|---|---|
| A, B | 186-291 | where the icon bar was left: side, height, minimised (`MapIconClusterPositionState`) | **Yes**, already `internal` | none. It is remembered in `AvailabilityScreen.kt:970`, above the tab, so a move doesn't change where the user's position is kept |
| C | 293-927 | `CompactMapTab`, the tab body | whole: yes; as pieces: **no** | local state in its first 400 lines is read across the nine children; it is the function whose size caused the 2026-10-08 launch crash (RECORD -739/-741) |
| D | 929-1039 | the map host (`CompactMapMapSlot`) | Yes (`private` to `internal`) | its taps toggle fullscreen and close the bubble; the search bar slot must stay its sibling (`:424-440`) |
| E | 1041-1059 | `navigationChromeTransitions` | Yes | **no caller anywhere** (O): dead code. Moving it keeps it; removing it is not a move |
| F, G, H | 1061-1096 | sums for the navigation card's limits; the walker-dot clearance; slide-in from the bottom | Yes | none |
| I | 1098-1178 | the tapped thing's bubble | Yes | its touch area is bounded by limits worked out in C (`:769`, `:775`) |
| J | 1180-1294 | the icon cluster host (bar, record pill, cluster) | Yes | must be composed before the strip (`:1207-1212`) |
| K, L | 1296-1461 | the compass strip, with five effects | Yes | writes size state owned by C; reports landscape measurements up to the frame (`:1445-1446`) |
| M1 | 1463-1555 | species and journal chips | Yes | the chip row is sized to its chips, with no `Surface`, so map touches pass beside it (`:1489-1493`) |
| N, O, P | 1557-1650, 1770-1771 | the portrait navigation card and its evening lines | Yes | writes the cluster's bounds |
| M | 1652-1768 | the landscape navigation card | Yes | writes the cluster's bounds (`:1764`) |
| Q | 1773-1963 | Return to Route, legend, floating bottom bar, rail | Yes | **insets and touch**: its composition order is load-bearing (`:1869-1887`); the bar's height includes a system inset Robolectric reports as zero |
| S | 1965-2091 | add menu, Layers sheet, centre-pin picker | Yes | `WindowInsets.navigationBars` at `:2058`, device-only |
| T | 2093-2097 | landscape card width cap; log tag | Yes | none |

**Tests that reach in.** Only `NAVIGATION_INFO_LINE_TAG` (`MapTopCardScreenTest:433`, `RoundedMapCardTouchTest:218`)
(O). No test calls any function in this file directly. Every test reaches it through `AvailabilityScreen(...)` (O).
**The guards against touch interception over the map** are real coordinate long-presses through the whole screen.
These are the tests that must stay green, unchanged, across every PR touching this file: `RoundedMapCardTouchTest`,
`MapTopCardScreenTest`, `MapChromeOutlineTouchTest`, `AvailabilityScreenQuickSettingsTest`,
`AvailabilityScreenSundownLineTest`, `LandscapeBarStripJoinTest`, `LandscapeCardMeetsBarTest`, `MapBarPressFeedbackTest`,
`MapIconClusterGlideTest`, `LandscapeLClusterTest`, `HudLandscapeT11FixesTest`, `AvailabilityScreenLandscapeB2Test`,
`AvailabilityScreenNavigationViewTest`, `MapPopUpMotionTest`, `SmallLeavingChipTest`,
`AvailabilityScreenShortLandscapeTest`, `AvailabilityScreenTabletAsPhoneTest`, `AvailabilityScreenRouteDrawnTest`,
`AvailabilityScreenReturnRouteTest` (O, helper). `AvailabilityScreenMapIconStackTest` has 14 skipped tests on the CI
allowlist (`.github/workflows/ci.yml:297`, O).

### 3.3 The main screen: `availability/AvailabilityScreen.kt` (2,072 lines)

**What the user sees:** the whole main screen (section 2). There is no separate wide layout any more: one tree at
every window size (`:1903-1904`, R).

**Its shape.** Imports take `:3-358`. The screen's parameter list alone is **427 lines** (`:399-827`, about 150
parameters, mostly defaulted). The body (`:828-2049`) is one function (R). Only about 30 lines of top-level
declarations sit outside it.

| Seam | Lines | What | Pure? | What makes it hard |
|---|---|---|---|---|
| S2, S3 | 360-367 | `ResultsTab`; the double-back exit window | **Yes** (S3 `private` to `internal`) | none |
| S5 | 399-827 | the parameter list | **No** | it is the public signature used by `MainActivity` and **81 test files** (O). Shortening it means grouping parameters, which changes the API |
| S6 | 828-1043 | what the screen remembers: tab, journal state, filter, fullscreen, icon bar position, camera memory (`:983`), map mode, legend | No | `var … by remember` and `rememberSaveable` locals shared by everything after |
| S7, S8 | 887-927, 1049-1111 | waypoint navigation actions; the straight line to a waypoint, with a `while (true)` ticker (`:1102-1109`) | No | writes S6 locals |
| S9 | 1112-1171 | what the map is given: forecast feed, layers, journal highlights | almost | two `remember` calls and two locals written from callbacks |
| S10, S11 | 1172-1328 | the drawer and focus; backup, GPX import, return-to-map requests | No | shared "is the drawer open" authority |
| S12 | 1330-1454 | **what Back does**: seven `BackHandler`s | No | their order relative to the frame's nested handlers matters (`:1331-1336`) |
| S13 | 1456-1682 | snackbars and notices, journal draft leave, map-return memory, map record sources | No | shared host state |
| S14 | 1684-1900 | the call into the frame around the tabs: about 200 lines of passing values | No | captures about 100 locals; setters write S6 |
| S15, S16 | 1903-2049 | the Tools drawer host and the in-app camera on top | No | the camera must be composed after the drawer, because order is what puts it on top (`:2029-2032`) |
| S17-S19 | 2052-2072 | drawer tag; `ToolsOfflineMapsReturn`; "Stopped navigating" text | **Yes** | none |

**Tests that reach in.** `TOOLS_DRAWER_SHEET_TAG` (4 classes), `WAYPOINT_NAVIGATION_GONE_TEXT`
(`AvailabilityScreenWaypointNavigateTest`), and `AvailabilityScreen(...)` itself from 81 test files (O). Moving a whole
function within the package changes none of them.

### 3.4 The frame around the tabs: `availability/AvailabilityCompactScaffold.kt` (1,954 lines)

**What the user sees:** the frame around every tab (section 2).

**Its shape.** One function, `CompactMainScaffold` (`:202-1864`, **1,663 lines**), which was itself extracted from the
main screen with 109 parameters in Stage G (`:3-54`, R). After it come about 90 lines of small helpers.

| Seam | Lines | What | Pure? | What makes it hard |
|---|---|---|---|---|
| A | 202-1864 | `CompactMainScaffold` | whole: yes; as pieces: **no** | below |
| B | 1866-1873 | `KEEP_MAP_THROUGH_TAB_FADE`: keeps the live map on screen while it fades out | Yes | a device-checked switch (RECORD -651, -672) |
| C | 1875-1879 | landscape search width caps | Yes | none |
| D, E, F | 1881-1913 | snackbar tag, margin, gliding bottom | Yes (`private` to `internal`) | snackbar touch trimming |
| G, H | 1915-1954 | landscape tab pill fit; strip line height | Yes | none |

**Regions inside A** (extraction only, never pure): search dropdown and strip sizing (`:407-488`, with a
`rememberSaveable` at `:478`); top inset (`:489-519`); Back and focus (`:520-553`); holding the search bar back while a
Journal page leaves (`:563-614`); **tab selection, which also exits fullscreen when leaving Map** (`:615-669`);
**bottom bar height, rail, map padding, reading the real cut-out and navigation-bar insets** (`:670-736`); attribution
insets (`:737-811`); `Scaffold` and snackbar (`:812-882`); **the four-way window-inset choice** (`:883-912`); bottom bar
(`:913-946`); content: rails, header search bar, the tab fade with each tab (`:1220-1639`), the dismiss scrim
(`:1641-1707`), the dropdown with keyboard reads (`:1708-1840`) (R, helper). Shared locals such as the dropdown flag
(read or written at 13 places), map padding (5), bottom bar height (4) cross these regions (R).

**Tests that reach in.** `COMPACT_SNACKBAR_TAG` in six classes: `AvailabilityScreenNavigationViewTest`,
`BackgroundRunPromptTest`, `MapChromeOverMapTest`, `MapChromeResumedTest`, `MapPopUpMotionTest`,
`SnackbarTrimmedTouchTest` (O). Every map long-press guard in 3.2 runs through this frame too.

### 3.5 The main screen's engine: `availability/AvailabilityViewModel.kt` (1,950 lines)

**What the user sees:** nothing directly (section 2).

**Its shape.** One class (`:80-1890`), with one state flow `uiState` (`:185`) backed by a private `_uiState` touched
130 times (O), 54 public functions, 36 constructor parameters (17 required), and a private `TAG` used 53 times (O).
**It has no poll loop**: the only `delay` is the 300 ms species-search debounce (`:416`) (O). One collector runs for as
long as the app is in the foreground (`liveFixJob`, `:330-345`) (R).

| Seam | Lines | Concern | Pure? |
|---|---|---|---|
| V1-V3 | 80-260 | constructor, private state, `init` (17 loaders in a fixed order) | No |
| V4, V6 | 262-395, 538-570 | live location and foreground; permission denied, locate me | No |
| V5 | 397-536 | search inputs: radius, month, species search, coordinates | No |
| V7-V9 | 572-795 | map records, map layers, basemap and icon-bar placement | No |
| V10 | 797-1108 | running searches | No |
| V11 | 1110-1151 | planned trips | No |
| V12 | 1153-1348, 1680-1883 | offline maps, pending delete, `onCleared` | No |
| V14, V15 | 1350-1678 | settings; waypoint navigation | No |
| V17-V19 | 1893-1950 | layer and label helpers; the "no stored preference" stand-ins; two messages | **Yes** (`private` to `internal`) |

Why V1-V15 are "No": a Kotlin class can't span files, and code in another file can't reach `_uiState`, the private
fields or `TAG` (I, language rule).

**Tests that reach in.** 22 test files construct it, each once, all with named arguments; 15 pass only the 17
required parameters (O, helper). Two test helpers multiply that: `MapLayersUiFixtures.mapLayersViewModel` is used by
36 test files and `RealSearchScreenRig` by 7 (O). If the constructor and public functions stay exactly as they are,
**no test changes** (I).

---

## 4. The other seventeen files over 600 lines

The same detail as section 3 was gathered for each of these (seams with line ranges, dependencies, purity, tests). It
is condensed here to what a planner needs: the parts, which of them move purely, and what doesn't. All proposals keep
the existing package, so **no test changes** for any of them (O: every test that reaches these files is in the same
package or imports by package name).

| File (lines) | Parts that move purely (proposed file, about lines) | What can't reach ~200 by moving, and why |
|---|---|---|
| `availability/NavigationHud.kt` (1,372) | tags and texts (~85); the card `NavigationHud` (~220-255, half of it KDoc); `NavigationInfo` with its metrics (~180-210); the card's layout parts (~175-210); evening lines and labelled readout (~100-225); `NavigationHudReadout`, `ReturnRoute`, arrival and straight-line rules (~115-215); `navigationReadout` (~210-215) | Nothing needs a state holder. `navigationReadout` is one 189-line function (`:1142-1330`). `NavigationInfo` has a one-second ticker, `while (true) { delay }` at `:407-412`, which stays whole inside it. Private size constants (`:1351-1369`) are shared by six parts and become `internal` |
| `availability/AvailabilityMapControlsUi.kt` (1,368) | strip measurement helpers (`:123-209`, `:1075-1163`, ~130-180); the record and return pill `TrailheadControls`/`ControlPill` (`:211-405`, ~230); return-control text (`:1166-1201`); the strip wrapper (`:407-539`); the strip's evening lines (`:966-1073`); the "+" chooser `AddActionTile` (`:1203-1343`, ~140) | `CompassElevationStripContent` (`:541-964`, 424 lines) is one composable over the map: a plain `Box`, never a `Surface`, so map touches pass through (`:582-588`); an `AnimatedContent` whose leaving readout takes no touches (`:702-707`). Shrinking it is extraction inside an animation over the map |
| `track/TrackRecordingViewModel.kt` (1,359) | the two messages and `ARRIVAL_HOLD_MILLIS` (`:1341-1359`, ~20) | One class (`:122-1339`). Pending deletes, lists, the way home, polling and take-up each need a collaborator class. **The poll loop rule** (section 7.4) governs every one of them |
| `log/MushroomLogViewModel.kt` (1,264) | edit rules `isUnchangedReEdit`, `leaveKeepsDraft`, the album message (`:1234-1263`, ~30) | One class. One shared lock, `editingEntryMutex` (`:241`), serialises every edit, and the methods must not call each other while holding it (`:124-130`). Every collaborator must share that one lock. Tests assert on the log tag `"MushroomLog"` and message text (`MushroomLogViewModelTest:1265`, `:1393`, `:1421`) |
| `map/MapChrome.kt` (1,235) | colour and alpha tokens, kept in `MapChrome.kt` so CLAUDE.md's pointer to `MAP_CHROME_OVER_MAP_ALPHA` (`:288`) stays true (~190); outlines and shape hooks (`:386-585`, ~200); icon bar sizes, ids and the unused mode picker (~150); `MapIconBar` (`:610-783`, ~175); the minimise and restore handles (`:785-1024`, ~240); `MapBarIconButton` and the unused floating button (~185) | Nothing. Watch: top-level size values read each other across the proposed files (`HANDLE_MARK_EDGE_PADDING` reads `MAP_ICON_BAR_EDGE_INSET`, and so on). There is no cycle today, but a cycle created by a split would make a size read as 0 with no error (I) |
| `availability/AvailabilitySearchUi.kt` (1,205) | search bar `SearchEntryBar` (~240); summary text (~50); species field (~185); `SearchDropdown` with its tags (~260-290); month and recent-search sections (~135); `SearchNotice` with the `LocalSearchNoticeInset` it defines (`:769`, ~78); `SearchControls` and `CollapsibleSection` (~90) | `SearchEntryBar` (230 lines) and `SearchDropdown` (260) are each one function, so their files stay a little over 200 |
| `log/JournalTab.kt` (1,088) | the Entries/Records switch and its enum and tags (~50); journal destinations (~55); `FindsPage`, `FindOverView`, `nextFindOverView` (~70) | `JournalTab` itself (~860 after the moves): a 197-line parameter list; the finds section is a set of closures over local state **on purpose** (`:497-500`); four Back handlers whose composition order decides which wins (`:483`, `:493`, `:891`, `:912`); the Records tab's call site must keep its place or its remembered state drops when the phone turns (`:807-810`) |
| `log/CartographyEntryReportScreen.kt` (963) | its layout and short-window height (~95); constants and the waypoint helper (~75); `ReportItem` and `ReportItemsSection` (`:948-963`) have **no callers** (O) | the report screen itself (~675): nine values remembered per entry and read across header, map, body and dialog. **The map must stay one call in the source** (`:545-548`), or it is torn down and rebuilt when fullscreen toggles |
| `availability/AvailabilityMapIconCluster.kt` (939) | `MapIconClusterState` with `ClusterGlide` (~190-205); the landscape L shape (~80-110); `MapControlToasts` (~30) | `MapIconCluster` (`:298-799`, ~500): the long-press drag over the map (`:607-628`), whose long-lived closure reads every limit through `rememberUpdatedState` because a stale-closure bug was found there (`:402-420`). Splitting it touches the exact path that bug lived on |
| `log/CartographyViewModel.kt` (863) | the per-item decision mappers (`:811-863`, ~55) | one class (~810) |
| `log/CartographyScreen.kt` (810) | `ShortWindowFrame`, `EntriesPage`, `CartographyEntryMode` (~65) | the screen (~660): seven shared remembered values, four page lambdas, Back order by composition order (`:332-340`, `:533-535`, `:577-582`) |
| `availability/AvailabilityOfflineMapsUi.kt` (737) | the side-by-side layout and status (~105); the downloaded regions list (~205) | `OfflineMapsPanel` (~340): six local lambdas over the pin, and **the one `movableContentOf` in the app** (`:193-198`), which must stay in the same function as the landscape branch (`:323`) and be invoked by both layouts |
| `log/RecordDetailsSheet.kt` (736) | all of it: target and saver (~90); the sheet (~170); the four kinds of content (~175); its parts and `LocalTrackDetailsFold` (~105); formatting (~110) | Nothing. `MapChromeSheetNavigationBar` must stay inside the sheet's content, because it reads the sheet's own window (`MapChrome.kt:1225-1234`) |
| `log/InAppCameraDialog.kt` (674) | the shutter (~130); parts: touch swallowing, RAW shot, chip list, tags (~95) | the dialog (~400): seven saved values, z-order and touch order by composition order, and **the orientation calls that keep the camera from ever rotating** (`:209`, `:251-252`, `:276-286`), which must stay at dialog level |
| `availability/AvailabilitySettingsUi.kt` (662) | the Tools drawer content (~220); settings pages (~165); settings rows (~100); the sundown section (~85) | Nothing |
| `map/MapBubble.kt` (630) | the bubble shell (~160-200); the bubble layer (~185); the feature bubble (~210) | Nothing |
| `map/MapSlot.kt` (610) | `MapRenderMode` (~255, one class, mostly KDoc); `MapOverlayContent` (~150); the slot type and the real map (~190) | `MapRenderMode` is one declaration over 200 lines |

Four files have no part that needs a state holder at all: `RecordDetailsSheet.kt`, `AvailabilitySettingsUi.kt`,
`MapBubble.kt`, and `MapChrome.kt` (O, helpers). `NavigationHud.kt` needs none either, but two of its files land a
little over 200.

---

## 5. Proposed target layout for the five largest

All files stay in their current package. **Mechanical** means move only, with `private` becoming `internal` where
named. **Needs a holder** means an interface, state holder or collaborator class has to exist first. That is a design
change and comes after the owner decides on it (section 9.4).

### 5.1 The map, `ui/map/`

| File | Contents (lines at `8c90626f`) | About | Kind |
|---|---|---|---|
| `MapAttribution.kt` | credit caption, attribution and logo placement (S2, S11) | 85 | mechanical |
| `OverlayLayerSetup.kt` | layer setup, spec to layer, paint (S3, S4) | 195 | mechanical |
| `OverlayIds.kt` | source and layer ids, feature ids, log tags (S7) | 50 | mechanical; widen the private aliases and `SIGHTINGS_MAP_TAG` |
| `OverlayFeatures.kt` | tap hits, data refresh, every feature collection (S8, S9) | 190 | mechanical |
| `SightingDotStyle.kt` | sighting dot look (S6) | 95 | mechanical |
| `OverlayLineSpecs.kt` | line specs, widths by zoom, journal halos (S12) | 225 | mechanical |
| `MapCameraMoves.kt` | camera decisions (S10) | 150 | mechanical; carry the three misplaced KDoc blocks exactly |
| `LiveLocationPuck.kt` | the location dot (S13) | 185 | mechanical |
| `OfflineNightRecolourApply.kt` | night recolour of offline tiles (S14) | 80 | mechanical |
| `SightingsMap.kt` | the map function (S1, S5) | **~1,370** | needs a holder |

To bring `SightingsMap.kt` near 200, a **map state holder** would carry the map handle, loaded and applied style,
camera bookkeeping, cover phase and sizes, and each effect group would become its own function taking it:
`MapLifecycle.kt`, `MapListeners.kt`, `MapStyleReload.kt`, `MapDataRefresh.kt`, `MapFan.kt`, `MapForecastCells.kt`,
`MapNavigationView.kt`, `MapAttributionInsets.kt`, `SightingsMapLayout.kt`. Each about 100 to 200 lines (I). None of
that is a move.

### 5.2 The Maps tab, `ui/availability/`

| File | Contents | About | Kind |
|---|---|---|---|
| `MapIconClusterPositionState.kt` | where the icon bar was left (A, B) | 106 | mechanical |
| `CompactMapMapSlot.kt` | the map host (D) | 111 | mechanical |
| `CompactMapBubble.kt` | the tapped thing's bubble (I) | 81 | mechanical |
| `CompactMapIconClusterHost.kt` | the icon cluster host (J) | 115 | mechanical |
| `CompactMapTopStrip.kt` | the compass strip host (K, L) | 166 | mechanical |
| `CompactMapChips.kt` | chips (M1) | 93 | mechanical |
| `CompactMapPortraitNavigationInfo.kt` | portrait card (N, O, P) | 96 | mechanical |
| `CompactMapLandscapeNavigationCard.kt` | landscape card and its transitions (E-H, M) | 173 | mechanical; E is dead code |
| `CompactMapBottomChrome.kt` | Return to Route, legend, bottom bar, rail (Q) | 191 | mechanical |
| `CompactMapPickers.kt` | add menu, Layers sheet, centre pin (S, T) | 130 | mechanical |
| `CompactMapTab.kt` | the tab body (C) | **~640** | needs a holder for its local state (`:474-531`, `:576-699`) |

This is the cleanest of the five: every child is already its own function, so the split is nine mechanical moves.

### 5.3 The main screen, `ui/availability/`

| File | Contents | About | Kind |
|---|---|---|---|
| `AvailabilityScreenDeclarations.kt` | `ResultsTab`, the exit window, drawer tag, `ToolsOfflineMapsReturn`, the "Stopped navigating" text | 30 | mechanical |
| `AvailabilityScreen.kt` | the parameter list and the body | **~2,040** | needs a holder **and** a signature change |

The parameter list alone is 427 lines (`:399-827`). Getting under it needs **parameter groups**, which change the API
used by `MainActivity` and 81 test files. The pattern already exists: `CompactMapTabInputs.kt` (137 lines) grouped
`CompactMapTab`'s 67 parameters into 37 during the launch-crash fix (`docs/audits/2026-10-08-launch-verifyerror-report.md`).
With a screen state holder, the body breaks into `AvailabilityScreenNavigation.kt`, `…MapInputs.kt`,
`…Requests.kt`, `…BackHandlers.kt` (called at the same position), `…Notices.kt`, `…JournalReturn.kt` and
`…DrawerHost.kt`, each 60 to 125 lines (helper, I).

### 5.4 The frame around the tabs, `ui/availability/`

| File | Contents | About | Kind |
|---|---|---|---|
| `CompactScaffoldSnackbar.kt` | snackbar tag, margin, gliding bottom (D-F) | 33 | mechanical |
| `CompactScaffoldLandscapePill.kt` | tab-fade switch, landscape width caps, tab pill fit, strip line height (B, C, G, H) | 55 | mechanical |
| `CompactMainScaffold.kt` | the frame function (A) | **~1,663** | needs a holder |

The frame is one function; this file saves only about 90 lines by moving. With holders for the dropdown state, the
inset and padding sums, and the landscape sizes, it could become `ScaffoldInsets.kt` (the window-inset choice and
padding, `:670-736`, `:883-912`), `ScaffoldSnackbarHost.kt`, `ScaffoldHeaderSearch.kt`, `ScaffoldMapTabCall.kt`,
`ScaffoldJournalTabCall.kt` and `ScaffoldSearchDropdown.kt` (the scrim and dropdown, `:1641-1840`) (I). Stage G's
lesson (section 7.5) applies directly.

### 5.5 The main screen's engine, `ui/availability/`

| File | Contents | About | Kind |
|---|---|---|---|
| `AvailabilityViewModelDefaults.kt` | layer and label helpers, "no stored preference" stand-ins, two messages | 60 | mechanical |
| `AvailabilityViewModel.kt` | the class | **~1,890** | needs collaborators |

With an internal holder of the scope, the state flow, the error log and the log tag, per-concern collaborators could
be `AvailabilityLiveLocation.kt`, `…SearchInputs.kt`, `…SearchRefresh.kt`, `…SearchTabsAndClear.kt`,
`…MapDisplay.kt`, `…PlannedTrips.kt`, `…OfflineMaps.kt`, `…OfflineRegionDeletes.kt`, `…SettingsPrefs.kt` and
`…WaypointNavigation.kt` (45 to 280 lines). The ViewModel keeps its constructor and about 55 forwarding functions, so
it would likely still be over 200 lines (I). If the constructor and public functions stay exactly as they are, no
test changes.

---

## 6. The owner's goal: one part per file, parallel work, shared hubs (Addendum 1)

The owner: "Ideally, every part should have its own file. UI fixes can have several agents attacking their own
changes. Updates will be easier to manage when we need to make it work at a certain time".

### 6.1 One part per file

**After the mechanical moves**, each of these has its own file (sections 4 and 5): the record and return pill, the
"+" chooser, the compass strip's lines and measurements, the navigation card and each of its pieces, the bubble, the
chips, the bottom chrome, the pickers, the icon bar's state and its L shape, the icon bar and its handles, the search
bar, the search dropdown, the search notice, the details sheet and its contents, the Tools drawer, each settings page,
the camera's shutter, the downloaded-maps list. That is most of the controls, panels, overlays and sheets.

**Still sharing a file after every mechanical move** (each would need a holder first):
- **The map's own body** (`SightingsMap.kt`): style reload, tap routing, the marker fan, forecast cells, the location
  dot's behaviour, navigation tilt and attribution insets all live in one function.
- **The Maps tab body** (`CompactMapTab.kt`): it decides the limits each child uses (the bubble's top, the cluster's
  bounds), so a fix to how two children keep clear of each other lands here.
- **The frame around the tabs** (`CompactMainScaffold.kt`): insets, the bottom bar's height, the rail, the dropdown and
  each tab's call site.
- **The main screen** (`AvailabilityScreen.kt`): Back, snackbars and notices, the drawer, the camera.
- **The compass strip's content** (`CompassElevationStripContent`, 424 lines) and **the icon bar's drag**
  (`MapIconCluster`, ~500 lines).
- **The Journal tab** (`JournalTab.kt`), **the report screen**, **Cartography** and **the camera dialog**.
- **The four ViewModels**, one class each.

### 6.2 Parallel work: where two agents would still meet

| Two fixes | The file they'd both touch | Why |
|---|---|---|
| any two Maps-tab controls whose spacing interacts (strip and cluster, bubble and card) | `CompactMapTab.kt` | it computes the limits each child reads (`:670-699`, `:769`, `:775`) and their paint and touch order (`:712-901`) |
| any change that reaches a tab from the main screen (a new value for the Maps tab, a new callback) | `AvailabilityScreen.kt` **and** `CompactMainScaffold.kt` **and** `CompactMapTabInputs.kt` | values are threaded through all three: about 100 locals captured at `AvailabilityScreen.kt:1684-1900`, 109 parameters into the frame |
| anything the map draws, plus anything about how it moves | `SightingsMap.kt` | one function; ~10 effects keyed on the loaded style |
| a new map feature | `MapSlot.kt` (`MapRenderMode` / `MapOverlayContent`) | every new thing the map draws or reports is a field there; 15 UI files name them (O) |
| any two Back fixes on the main screen | `AvailabilityScreen.kt` (`:1330-1454`) | seven Back handlers whose order matters |
| any two fixes to what the main screen remembers | `AvailabilityUiState.kt` (405 lines) and `AvailabilityViewModel.kt` | one state value, read by 22 UI files (O) |
| any two fixes to Journal finds or Journal Back | `JournalTab.kt` | closures over shared local state, by design |

### 6.3 Shared hubs every change passes through, and how to reduce each

| Hub | Why it serialises | Proposal to reduce it |
|---|---|---|
| `AvailabilityScreen.kt`'s parameter list (427 lines; 81 test files call it) | every new input to the screen is a parameter and a test-call edit | parameter groups by concern, as `CompactMapTabInputs.kt` already does for the Maps tab; then each concern's group is its own file |
| `CompactMainScaffold.kt` (one 1,663-line function) | every tab's call site and every inset sum is in it | holders for insets and padding, the dropdown, and landscape sizing; then one file per tab call |
| `CompactMapTab.kt`'s local state | the limits between children | a `CompactMapLayoutState` holder that each child file reads, so a spacing fix touches the holder and the one child |
| `SightingsMap.kt`'s body | style-keyed effects | a map state holder plus one file per effect group (5.1) |
| `AvailabilityUiState.kt` / `AvailabilityViewModel.kt` | one state value, one class | sub-states per concern (search, map display, offline maps, settings) inside the one value, each with its own collaborator file. Changing the shape of the state is an owner-level decision |
| `MapSlot.kt` | `MapRenderMode` / `MapOverlayContent` | split into the three files in section 4 first. Further reduction would group fields by concern, but the slot type is held to 7-8 declared parameters by a compiler crash at 10 (`MapSlot.kt:24-36`, `:552-557`), so groups must stay bundled values |
| test hubs: `AvailabilityScreenMapIconStackTest.kt` (3,446 lines), `MapLayersUiFixtures.mapLayersViewModel` (used by 36 test files), `RealSearchScreenRig` (7) | two agents fixing different controls both add cases to the one big test class | split big test classes by the part they test, as a separate move-only PR per class. **This changes test class names**, so the CI skip allowlist must change in the same PR (section 7.7) |
| `docs/audits/README.md` (382 lines) | CLAUDE.md already names it a serialization point | one fragment file per dispatch, as CLAUDE.md proposes, or sequence the dispatches that touch it. This report followed the second: no row |
| `RECORD.md` | one record, one next-free ID | already per-planner; out of this survey's scope |
| strings | **not a hub.** `res/values/strings.xml` is 36 lines, and only 3 UI files use string resources at all (O). The app's text lives as constants beside the parts that show it, which already suits one-part-per-file | none needed |
| `scripts/dex-register-budget-baseline.txt` and the CI skip allowlist | keyed on class names; a moved function or test class shows up there | named per PR in section 8 |

---

## 7. Risks, read from this repository's own record

### 7.1 Anything a move could change silently: the Stage F checks exist for this

`docs/audits/2026-09-26-split-stage-f-completion-report.md` moved 3,445 lines out of the main screen and proved it
changed nothing with five checks (R):
1. **Moved, not changed:** `git diff --color-moved=zebra --color-moved-ws=allow-indentation-change`, with every `+`
   and `-` line classified by colour. The only unmoved lines allowed are `package`, header comment, import, blank
   separator, and the listed `private` to `internal` lines.
2. **The same declarations:** a sorted `git grep` of top-level declarations at base and head, equal apart from the
   listed widenings.
3. **Byte-identical blocks:** each new file's body, with the widenings reverted by `sed`, compared by `cmp` against the
   source ranges. **Shown to bite**: run once with a widening left in and once with a range one line short, both
   reported `DIFFERS`.
4. **Suite:** the full unit suite before and after, compared class by class and case by case, with the stale-result
   guards: results directory deleted first, the log shows the test task ran and no `e:` line, the new facade classes
   exist in the build output.
5. **Line counts.**

It also records what those checks don't cover: Compose group keys and file-facade class names change with a move;
that matters only for `rememberSaveable` state carried across an app update, not within a build.

### 7.2 Touch interception over the map

CLAUDE.md: a `Surface` or anything with a background or pointer input intercepts touches across its whole layout
bounds, and two regressions of this shape were caught only by a Robolectric test driving the real long-press. In
these files the guards are explicit and positional:
- **Order of children is order of touch.** The Maps tab's nine children (`AvailabilityCompactMapUi.kt:712-901`), the
  cluster before the strip (`:1207-1212`), the bottom chrome's internal order (`:1869-1887`), the camera's layers
  (`InAppCameraDialog.kt:312-313`, `:431`), the handle composed after the container (`AvailabilityMapIconCluster.kt:362-373`).
  A **pure move keeps each function body identical, so order is unchanged** (I). An extraction that reorders calls,
  or moves one child into another's scope, changes it.
- **Deliberately non-intercepting chrome:** the strip is a `Box` not a `Surface` (`AvailabilityMapControlsUi.kt:582-588`);
  the chip row has no `Surface` (`AvailabilityCompactMapUi.kt:1489-1493`); the credit and the cover take no pointer
  input (`SightingsMap.kt:1457`, `:1499-1500`).
- **Deliberately intercepting chrome:** the "+" chooser's scrim (`AvailabilityMapControlsUi.kt:1270-1280`), the
  dropdown's scrim (`AvailabilityCompactScaffold.kt:1641-1707`), the bubble shell (`MapBubble.kt:216`), the camera's
  `swallowTouchesBelow` (`InAppCameraDialog.kt:460-480`).
- **The guards** are the 19 coordinate-touch test classes in 3.2. CLAUDE.md also warns that a semantic click proves
  wiring, not routing, so these, not the click tests, are what proves a touch still reaches the map.

### 7.3 Real insets

Robolectric reports zero insets (CLAUDE.md). The inset code lives in a few places: the frame
(`AvailabilityCompactScaffold.kt:670-736`, `:883-912`, `:1022-1032`, `:1428`, `:1731-1741`), the pickers
(`AvailabilityCompactMapUi.kt:2058`), the bottom bar and rail (`AvailabilityNavigationUi.kt`, 13 inset references, O),
and two settings rows (`AvailabilitySettingsUi.kt:113`, `:165`). `SightingsMap.kt` takes insets only as `Dp`
parameters (helper, O). **A pure move doesn't touch inset code. Any extraction in the frame does, and the unit suite
can't show that it's unchanged.** That needs a phone.

### 7.4 The poll loop in `TrackRecordingViewModel`

Two unbounded loops: `beginPolling` (`:932-953`, every 15 s) and `beginRouteTicks` (`:915-923`, every 5 s), cancelled
only by `clearRecordingState` (`:528-549`) through `stopRecording()` (R). Every test body goes through
`runRecordingTest` (`TrackRecordingViewModelTest.kt:119-125`), which stops recording in a `finally`; three other test
classes carry their own copy (R). The tests also copy the two intervals as their own constants
(`TrackRecordingViewModelTest:903`, `:906`, R). **The rule any split must keep:** wherever a loop moves, `stopRecording()`
still cancels it synchronously, and it launches in the ViewModel's scope. If not, a failing test hangs instead of
failing, and CLAUDE.md records that `runTest`'s timeout didn't fire in 77 minutes. Two more loops elsewhere follow the
same shape: the navigation card's one-second ticker (`NavigationHud.kt:407-412`) and the main screen's straight-line
ticker (`AvailabilityScreen.kt:1102-1109`). Both are `produceState` inside one composable and are cancelled when it
leaves composition (I), so they are safe to move whole.

### 7.5 Value or getter: Stage G's inverted preference

`docs/audits/2026-09-26-split-stage-g-completion-report.md`: when the frame was extracted, the dispatch's pattern
passed each written state as a value. The fullscreen toggle writes `isMapFullscreen` and then reads it back for the
saved preference, so with a value parameter **the saved fullscreen preference would have been inverted**. The fix:
every captured state becomes a getter, every written state also gets a setter (R). **Every "needs a holder" item in
sections 4 and 5 carries this risk.** Pure moves don't.

### 7.6 The 2026-10-08 launch crash: large functions and the phone verifier

`docs/audits/2026-10-08-launch-verifyerror-report.md` (RECORD -739 to -744): the phone's verifier rejected
`CompactMapTab` at start on every phone, while 4,454 Robolectric tests and CI were green. The cause was a code
generator bug on one very large function, fixed by pinning R8 9.3.31 and splitting the function's body. The only check
that sees what a phone sees is `scripts/s22-launch-check.sh` on the S22 test phone (R). A pure move keeps function
bodies identical but changes the class that holds them. An extraction changes their size and shape. **Every PR in
section 8 runs the launch check**, and `scripts/verify-dex-register-budget.sh` is report-only, not a guard (`:15-20`,
R).

### 7.7 Class names: the dex baseline and the CI skip allowlist

- **Dex baseline.** `scripts/dex-register-budget-baseline.txt` lists nine methods over 256 registers, by class:
  `AvailabilityCompactScaffoldKt.CompactMainScaffold` and four of its lambdas, `AvailabilityScreenKt.AvailabilityScreen`
  and two lambdas, and `JournalTabKt.JournalTab` (O). Moving any of those functions to a new file renames its class, so
  the report shows it as NEW (`:4`, R).
- **CI skip allowlist.** `.github/workflows/ci.yml:297` is an exact-match set of 16 skipped tests by test class: 14 in
  `AvailabilityScreenMapIconStackTest`, one each in `AvailabilityScreenOfflineCacheTest` and
  `AvailabilityScreenTripPlanningFlowTest` (O). It fails CI on a skip not in the set and on an entry that no longer
  matches (`:425-451`, R). Moving main sources changes no test class, so it doesn't touch the allowlist. **Splitting
  or renaming one of those three test classes must move its entries in the same PR**, and that is a coverage-adjacent
  change the owner reviews (CLAUDE.md: no widening of the skip allowlist without the owner).

### 7.8 Smaller things a move could change

- **Source-reading test.** `ui/theme/ThemeCompletenessTest.kt:85-105` walks every `.kt` file under `ui/`. A move
  within `ui/` keeps its result (I). A move out of `ui/` would change it.
- **The design-token script** (`scripts/verify-design-tokens.sh`) greps `ui/` by pattern and allows one exception by
  file path, `motion/MotionTokens.kt` (`:79-83`, O). Nothing in this survey moves that file.
- **Initialisation order** of top-level values split across files (`MapChrome.kt`, `AvailabilityOfflineMapsUi.kt:497`):
  no cycle today; a cycle created by a split would make a value read as zero silently (I).
- **Misplaced KDoc** that a move must carry exactly, not "fix": `SightingsMap.kt:1772-1786`, `:1801-1804`,
  `:1889-1894`; `MapSlot.kt:24-36`; `MushroomLogViewModel.kt:661-666`; `RecordDetailsSheet.kt:633-638`;
  `CartographyEntryReportScreen.kt:835`. A stale comment: `InAppCameraDialog.kt:109-110` says the window follows the
  device, while the code requests portrait (`WindowOrientation.kt:147-151`). Leave it to the owner.
- **Dead code** a move would carry: `navigationChromeTransitions` (`AvailabilityCompactMapUi.kt:1041-1059`),
  `ReportItem`/`ReportItemsSection` (`CartographyEntryReportScreen.kt:948-963`), `MapModePicker` and
  `MapFloatingIconButton` (`MapChrome.kt:167`, `:1175`; imported, never called). Removal is a separate decision.
- **Line-number citations in `docs/`.** Hundreds of `File.kt:NNN` citations across docs and prompts point at these
  files (helpers counted 5 to 78 per file, O). They are historical records and stay as written; after a split they
  read against their own commit, not the new tree.
- **`CLAUDE.md:244`** names `MAP_CHROME_OVER_MAP_ALPHA`'s home as `ui/map/MapChrome.kt`. The proposal keeps it there.

---

## 8. Order of work

Every PR is a pure move inside one package: `private` to `internal` on listed declarations, imports, a header line.
No test file changes. Each PR carries the same evidence:

- **The Stage F checks (7.1):** colour-moved classification; declaration-set equality; byte-identical blocks with the
  two bite runs; full unit suite before and after, class by class, with the stale-result guards; line counts.
- **`scripts/s22-launch-check.sh` on the S22**, output pasted into the report, because a move renames the class that
  holds each function and the phone's verifier is the only check that sees the dex (7.6).
- **The two class-name lists**, stated per PR:
  - **CI skip allowlist:** no change in any PR below, because no test class moves. Confirm by
    `git diff --stat -- .github/` being empty.
  - **Dex baseline:** run `scripts/verify-dex-register-budget.sh` before and after and paste both. None of the first
    eight PRs moves a function that is in the baseline, so the report should be unchanged. If it isn't, that is a
    finding, not something to absorb.
- **The coordinate-touch guards (3.2)** named in the suite comparison for any PR touching `ui/availability`, since
  they're the tests that would show touch routing changed.

**First three:**

1. **The navigation card's plain rules** (`availability/NavigationHud.kt`): the tags and texts, `NavigationHudReadout`
   and `ReturnRoute`, the arrival and straight-line rules, and `navigationReadout` into `NavigationHudText.kt`,
   `NavigationHudReadout.kt`, `NavigationArrival.kt` and `NavigationReadout.kt`. About 570 lines, almost all plain
   functions and constants with no composable. The smallest possible first test of the method on a file every
   navigation test reaches. Allowlist: none. Dex baseline: no listed method.
2. **The map's camera, credit and location-dot helpers** (`map/SightingsMap.kt` S2, S10, S11, S13) into
   `MapAttribution.kt`, `MapCameraMoves.kt` and `LiveLocationPuck.kt`. About 420 lines. One composable moves
   (`MapCreditCaption`, which has its own touch test). Carries the three misplaced KDoc blocks exactly. Allowlist:
   none. Dex baseline: no listed method.
3. **The map's layer and data helpers** (`map/SightingsMap.kt` S3, S4, S6-S9, S12, S14) into `OverlayLayerSetup.kt`,
   `OverlayIds.kt`, `OverlayFeatures.kt`, `SightingDotStyle.kt`, `OverlayLineSpecs.kt` and
   `OfflineNightRecolourApply.kt`. About 960 lines. The widest widening list in the series (every private id alias),
   so the declaration-set check carries most weight here. Allowlist: none. Dex baseline: no listed method.

**After those, in order of risk, each its own PR:**

4. `RecordDetailsSheet.kt` (five files, nothing needs a holder).
5. `AvailabilitySettingsUi.kt` (four files).
6. `MapBubble.kt` (three files) and `MapSlot.kt` (three files), as two PRs.
7. `MapChrome.kt` (six files; check initialisation order by reading, 7.8).
8. `AvailabilityMapControlsUi.kt`: the strip measurement, pill, return text, strip wrapper, lines and "+" chooser.
9. The Maps tab's children (`AvailabilityCompactMapUi.kt`), in two or three PRs of three or four children each. Run
   the full coordinate-touch list in each.
10. The remaining mechanical moves: `AvailabilitySearchUi.kt`, `AvailabilityMapIconCluster.kt` (state, L shape,
    toasts), `AvailabilityOfflineMapsUi.kt` (layout, list), the remaining `NavigationHud.kt` composables, the small
    declaration files for the main screen, the frame and the ViewModels, and the `ui/log` files.

**Then a stop.** Every remaining large file needs a holder, a collaborator or a signature change (section 9.4). Those
are design decisions for the owner, not moves, and each would carry the Stage G check method (getter and setter
ledger, revert checks) as well as everything above.

---

## 9. Confirmed and inferred; could not determine; wrong premises; decided beyond scope

### 9.1 Confirmed (read or observed here)

- pre-main head, file count, the eight largest counts, the 51 and 22 (section 1, O).
- `movableContentOf` appears once in app sources (`AvailabilityOfflineMapsUi.kt:194`, O, my grep).
- The main map is not kept alive across tabs: `MapView` made in `remember` at `SightingsMap.kt:322-327`;
  `MapCameraMemory.kt:24-33` says the map leaves composition with its tab; `MapCameraMemory` held at
  `AvailabilityScreen.kt:983` (R, read by me). `MapSlot` is a typealias with the real map as its default
  (`MapSlot.kt:500`, `:559`), read by me.
- `KEEP_MAP_THROUGH_TAB_FADE = true` (`AvailabilityCompactScaffold.kt:1873`) and its one use (`:1223`), read by me.
- The Stage F and G methods, the launch-crash record and both scripts, read by me.
- CI skip allowlist contents (O, my grep) and its rules (R).
- `strings.xml` 36 lines; 3 UI files using string resources; 22 UI files naming `AvailabilityUiState`; 15 naming
  `MapRenderMode`/`MapOverlayContent`; 36 test files using `mapLayersViewModel` (O, my greps).
- `ThemeCompletenessTest` walks all of `ui/` (R, read by me).
- No `@VisibleForTesting` under `ui/` (O).

**Read by helpers, with file and line in sections 3 and 4.** All seam tables, test lists and per-file facts. Group A's
four files were surveyed twice by two helpers working independently. They agreed on every seam boundary, every test
dependency and the poll-loop facts, and differed only in how finely to cut `NavigationHud.kt`. Section 4 gives both
ranges.

### 9.2 Inferred

- That a same-package move needs no test change (Kotlin name resolution; consistent with Stage F, where no test file
  changed).
- That a pure move preserves composition order, touch order and remember scopes, because function bodies are
  byte-identical.
- The sizes of proposed files, which are line ranges plus an estimate for imports.
- The holder designs in section 5 and their file names.

### 9.3 Could not determine (needs a build, a run or a phone)

- Whether each proposed file compiles with only the listed widenings. Name clashes were checked by grep only, and
  found none except `TAG`, private in eight `ui/log` files (stays private).
- Whether the moved classes pass the phone verifier. Only the S22 launch check can say.
- Whether any `rememberSaveable` state changes key across an app update after a move.
- Whether extracting the map's effects keeps their dispose order and relaunch timing (MapLibre can't run under
  Robolectric).
- Whether `LandscapeOfflinePickerTest` would catch a broken `movableContentOf` in Download Maps. A keyword search found
  no test asserting the map survives a turn there.
- Whether the twelve unused imports in `AvailabilityMapIconCluster.kt` and the ~195 apparently unused in
  `AvailabilityScreen.kt` are truly unused.
- Everything that depends on real insets (7.3).

### 9.4 Premises that were wrong, and decisions this survey leaves open

- **Wrong:** "`movableContentOf` keeping the MapView alive" (dispatch item 5) and "`movableContentOf`" among the map
  files' hardest seams (-1324). It is used once, in Download Maps, to keep that screen's map across a layout change.
  The main map is rebuilt on every return to the Maps tab, and what carries across is `MapCameraMemory` and
  `MapReturnMemory`, held above the tab, plus a cover picture while the new map loads (`SightingsMap.kt:461-493`). The
  real keep-alive risk for a split is therefore **where those two memories are remembered** (`AvailabilityScreen.kt:983`,
  `:986`) and, inside one screen, **the map staying one call in the source** (`CartographyEntryReportScreen.kt:545-548`)
  or inside the one `movableContentOf` (`AvailabilityOfflineMapsUi.kt:193-198`). A refactor that moved
  `remember { MapCameraMemory() }` below the tab would lose the camera on every tab change, and no unit test can show
  it, since MapLibre doesn't run under Robolectric.
- **Refined:** most of the shared state is local to one long function, not file-level `private` (section 1).
- **Open for the owner** (not decided here): whether to introduce state holders or collaborators for the screen, the
  frame, the Maps tab, the map and the four ViewModels; whether to group the main screen's parameters (an API change);
  whether to split the big test classes (moves allowlist entries); whether to remove the dead code found.

### 9.5 Decided beyond scope

None. No source changed, nothing was built, no other branch was touched, no index row was added. Seven read-only
survey helpers were used, and one of them wrote and deleted a scratch file in `/tmp`.
