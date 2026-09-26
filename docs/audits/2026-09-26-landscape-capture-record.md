# Landscape capture record, S22 Ultra, 2026-09-26

Evidence for the planner's landscape redesign. This record describes what is on screen and makes
no proposals. Dispatch `prompts/preserved/2026-09-26-31.md`, intent `RECORD.md` 2026-09-26-88.

## Setup

- **Phone:** R5CT321008R (SM-S908U), and no other device. App `com.zynergylabs.forager.app`
  1.0.899+g429edb96, as installed; nothing was installed. The screen is 1080x2316 px at density 450
  (2.8125), 3-button navigation (`navigation_mode` 0).
- **Method:** `adb shell settings put system user_rotation <n>` with auto-rotate off
  (`accelerometer_rotation` 0), and navigation by `input tap`, `input swipe` and `input keyevent
  KEYCODE_BACK`. Each capture is an `adb exec-out screencap -p` PNG plus a `uiautomator dump`. The
  dump file on the phone was deleted before each dump so that a failed dump could not leave the
  previous one behind; every PNG has a non-empty dump. `dumpsys window displays` cut-out and inset
  lines were saved once per rotation (`r0-`, `r1-`, `r3-window-displays.txt`).
- **Where the files are:** all 138 files, `MANIFEST.md` included, are in
  `~/Zynergy/forager-landscape-capture/`. `MANIFEST.md` lists each file with its surface, rotation
  and UTC time, and whether it was committed.
  [`assets/2026-09-26-landscape-capture/`](assets/2026-09-26-landscape-capture/) holds the 115
  files that show no location data.
- **Left out (22 files, 11 captures), per the previous run's decision 5:**
  - Portrait map captures, which show the grid reference: `r0-map`, `r0-map-search-open`,
    `r0-search-keyboard`, `r0-map-controls-hidden`, `r0-map-fullscreen`,
    `r0-map-fullscreen-exited`.
  - Offline Maps at all three rotations, which show map tiles; in portrait also
    "Pin at: <coordinates>": `r0-`, `r1-`, `r3-records-offline`.
  - Portrait drawer captures that show map tiles beside the drawer: `r0-tools`,
    `r3-after-camera-close`.

  Each capture's PNG and dump were left out together.

**Geometry per rotation** (from the saved `dumpsys window displays` lines):

| user_rotation | Display | Status bar | Nav bar | Cut-out inset band | Punch-hole |
|---|---|---|---|---|---|
| 0 | ROTATION_0, 1080x2316 | top, y 0-75 | bottom, y 2181-2316 | top, y 0-75 | Rect(512,0,568,75) |
| 1 | ROTATION_90, 2316x1080 | top, y 0-84 | **right**, x 2181-2316 | **left**, x 0-75 | Rect(0,512,75,568) |
| 3 | ROTATION_270, 2316x1080 | top, y 0-84 | **left**, x 0-135 | **right**, x 2241-2316 | Rect(2241,512,2316,568) |

Bounds below are `uiautomator` bounds in px, `[left,top][right,bottom]`, for the rotation named.

## What landscape is

At 823 dp wide, landscape is `WindowWidthClass.MEDIUM` (`ui/adaptive/WindowWidthClass.kt:39-45`).
`AvailabilityScreen.kt:2081` renders the COMPACT tree (a modal drawer plus a bottom nav) only for
COMPACT. Every other width gets a `PermanentNavigationDrawer` (`:2117`), a 360 dp panel (`:2246`),
beside the results pane.

So landscape is a different screen tree, not portrait turned sideways. It has:

- no bottom nav;
- no Tools or Journal tab;
- a permanent left panel holding Recent searches, Advanced search, Trip Planner, Mushroom Log,
  Photo Gallery and Settings;
- a right pane with a top search row and List, Maps and Seasonal tabs.

Mushroom Log (the Journal), Photo Gallery and Settings open inside the left panel, with a "Back to
search options" arrow. The right pane stays as it was beside them.

## Surfaces

"Captured" names the file stems. Where a surface is "not run", the reason is given.

| Surface | Rotation 0 | Rotation 1 | Rotation 3 |
|---|---|---|---|
| Main map screen as it opens | `r0-map` (not committed) | `r1-map` (Maps tab selected, **no map drawn**, see L3) | `r3-map` (Seasonal still selected), `r3-maps` (Maps selected, no map drawn) |
| Map with the icon cluster expanded | `r0-map` (expanded is how it opened); collapsed: `r0-map-controls-hidden` | not run: no map and no cluster on screen; no cluster node in any rotation-1 dump | not run: same reason |
| Map fullscreen, and its exit | `r0-map-fullscreen`, `r0-map-fullscreen-exited` (the "Exit fullscreen" button) | not run: the Fullscreen control belongs to the map cluster, which is not on screen | not run: same reason |
| Search, dropdown open | `r0-search-dropdown` (on Seasonal), `r0-map-search-open`, `r0-search-keyboard` | no dropdown exists: tapping the field raises the keyboard only (`r1-search`); the search options are panel sections (`r1-panel-recent`, `r1-panel-advanced`) | same: `r3-search`, `r3-panel-recent`, `r3-panel-advanced` |
| Tools / settings drawer | `r0-tools` (modal drawer: Trip Planner and Settings only) | the permanent panel, on every capture; Trip Planner expanded: `r1-panel-trip` | same: `r3-panel-trip` |
| Settings | `r0-settings`, `r0-settings-scrolled` | `r1-settings`, `-scroll1` to `-scroll3` | `r3-settings`, `-scroll1` to `-scroll3` |
| Journal > Cartography > Entries, Drafts, Album | `r0-00-current`, `r0-journal-carto-drafts`, `r0-journal-carto-album` | `r1-mushroom-log`, `r1-journal-carto-drafts`, `r1-journal-carto-album` | `r3-mushroom-log`, `r3-journal-carto-drafts`, `r3-journal-carto-album` |
| Journal > Records > Waypoint Markers, Offline Maps, Recorded Tracks, Logged Finds (Log, Drafts) | `r0-journal-records`, `r0-records-offline` (not committed), `r0-records-tracks`, `r0-records-finds`, `r0-records-finds-drafts` | the same five as `r1-` | the same five as `r3-` |
| An existing log entry, viewed and edited | not run: the phone has no log entry. Logged Finds > Log shows only the "New log entry" tile, and Drafts is empty; reaching one needs one created | not run: same | not run: same |
| Photo viewer, on an existing photo | `r0-photo-viewer` | `r1-photo-viewer` | `r3-photo-viewer` |
| Album / Photo Gallery | `r0-journal-carto-album` (portrait has no "Photo Gallery" entry; the Album is the Journal's) | `r1-journal-carto-album`, `r1-photo-gallery` | `r3-journal-carto-album`, `r3-photo-gallery` |
| Cartography screen, and an entry in it | screen: `r0-00-current`; entry: not run, as only the "New Cartography entry" tile exists | screen: `r1-mushroom-log`; entry: not run, same | screen: `r3-mushroom-log`; entry: not run, same |
| Navigation HUD | not run: the map offers "Start recording track" and "Return to vehicle — start recording first", and both need a recording, which creates data | not run: same, and no map in landscape | not run: same |
| In-app camera, "Lock camera to portrait" on | `r0-camera-locked` | `r1-camera-locked` (window portrait, see L14) | `r3-camera-locked` (window portrait); `r3-after-camera-close` (not committed) |
| Other top-level: List, Seasonal | `r0-list`, `r0-seasonal` | `r1-list`, `r1-seasonal` | `r3-list`, `r3-seasonal` |

## What is visibly wrong, by finding

Each finding is described and cited to its bounds; none is judged. "Screenshot" means the reading
comes from the PNG only, with no dump node behind it.

**L1. At rotation 1 the search row runs under the nav bar.**

- The search `EditText` is `[1194,95][2270,253]` and its "Use current location" button is
  `[2135,107][2270,242]`. Both extend 89 px into the nav-bar band (x 2181-2316).
- Screenshot: the nav bar's back chevron is drawn over the field's bottom-right corner.
- It appears on every rotation-1 capture of the main screen: `r1-map`, `r1-list`, `r1-seasonal`,
  `r1-settings` and the rest.

**L2. The top search row does not move with the rotation, but the pane under it does.**

- The row has identical bounds at rotations 1 and 3: "Advanced search options"
  `[1036,107][1171,242]`, the `EditText` `[1194,95][2270,253]`.
- The pane below it moves 60 px. At rotation 1 it is `[1088,264][2181,399]` (the summary row)
  and at rotation 3 it is `[1148,264][2241,399]`.
- At rotation 3 the field's right end (x 2241-2270) lies inside the cut-out inset band
  (x 2241-2316). The punch-hole itself (y 512-568) is not under it.
- Screenshot: the dark header background runs from x ≈1013 to the right edge at both rotations.

**L3. The Maps tab draws no map in landscape.**

- With Maps selected (`r1-map`, `r3-maps`, `r3-records-finds`) no dump lists a MapLibre node.
- Screenshot: a vertical line runs at x ≈2105 (rotation 1) or ≈2166 (rotation 3), from y ≈535
  down, with dark space from there to the nav bar or cut-out.
- Code, read and not measured: Maps at medium width is `CombinedResultsPane`
  (`AvailabilityScreen.kt:2274-2296`). It is a `ListTab` fixed at `COMBINED_PANE_LIST_WIDTH`
  360 dp (`:2302`), a `VerticalDivider`, then `MapTab` with `weight(1f)`. That sits beside the
  360 dp drawer (`:2246`) in an 823 dp window.
- Inference, not measured: the line is that divider, and the map is left well under 30 dp.
- Effect: the whole map cluster is absent at both landscape rotations. No landscape dump contains
  Fullscreen, Reset orientation, Center on my location, Map mode, "Plan a trip or log a find
  here", "Start recording track", "Return to vehicle" or the grid-reference strip.

**L4. Empty states point at a map that landscape does not show.**

- Trip Planner: "No trips planned yet. Tap the add button on the map to plan…"
  (`r1-panel-trip`, `[120,578][968,663]`).
- Waypoint Markers: "No waypoints dropped yet. Tap the add button on the map to d…"
  (`r1-journal-records`, `[75,638][1013,729]`).

**L5. The panel's search-options region is short.**

- It is a `ScrollView` of `[75,84][1013,663]` (579 px) at rotation 1, and `[135,84][1013,663]`
  at rotation 3.
- Under it, fixed rows take y 666-1080: Mushroom Log `[75,666][1013,802]`, Photo Gallery
  `[75,805][1013,941]`, Settings `[75,944][1013,1080]`.
- With Advanced search expanded, the Latitude and Longitude fields `[120,517][532,663]` and
  `[555,517][968,663]` end at the viewport's bottom edge.
- Trip Planner's empty text also ends at 663.

**L6. Settings in the panel.**

- The viewport is `[75,219][1013,964]` (745 px), with a fixed footer "Build 899 · …" at
  `[120,1001][833,1046]`.
- On the first screen the "Dark" row `[120,909][968,1044]` is cut by the viewport edge at 964.
  Its label reads `[278,943][385,964]`.
- Reaching Crash Logs and Diagnostics took three slow drags, at both rotations. In portrait the
  whole list is `[0,75][1013,2065]` and needed one drag.

**L7. Records sub-tab labels break mid-word.**

- The Journal is confined to the panel (x 75-1013 at rotation 1, x 135-1013 at rotation 3), and
  the four Records sub-tabs share that width.
- Screenshots: at rotation 1 the labels read "Waypoi / nt / Marker / s" and
  "Record / ed / Tracks". At rotation 3 they read "Waypo / int / Marke / rs",
  "Recor / ded / Tracks" and "Logge / d / Finds".
- The sub-tab row is 284 px tall (`[309,354][543,638]` at rotation 1), against 170 px in portrait
  (`[270,338][540,508]`).

**L8. Logged Finds > Log shows no "New log entry" tile in landscape.**

- Portrait has the tile at `[45,688][529,1257]`.
- Neither the rotation-1 nor the rotation-3 dump lists it.
- Screenshot `r3-records-finds`: empty space below the Log/Drafts row (y 773-1080).
- Not determined whether it is laid out off-screen or not composed.

**L9. The Cartography tile shrinks.** "New Cartography entry" is `[120,534][388,849]`
(268x315) at rotation 1, against `[45,518][529,1087]` (484x569) in portrait.

**L10. Album in the Journal.**

- The photo tiles `[120,759][533,1080]` and `[556,759][968,1080]` reach the screen's bottom edge,
  inside a `ScrollView` of `[75,714][1013,1080]`. The date labels are not on screen.
- The same photos opened from the panel's Photo Gallery, which has a shorter header, fit:
  tiles `[120,489][533,902]`, dates `[143,925][313,952]`.

**L11. Offline Maps shows 123 px of its map.**

- The map is `[75,957][1013,1080]` inside a `ScrollView` of `[75,638][1013,1080]`.
- The pin, "Pin at", OK and Cancel are not in the dump; they are below the fold.

**L12. Search in landscape raises a floating keyboard over the panel.**

- `mInputShown=true`, and no dropdown opens.
- Screenshot `r1-search`: the keyboard covers roughly x 420-1230, y 88-735, over the panel's
  section titles.

**L13. Photo viewer.**

- The photo is fitted to the full height. Screenshot: it spans x ≈438-1878, with the status-bar
  icons drawn over the photo at its top right.
- Close is `[98,107][233,242]` at rotation 1, clear of the cut-out band (0-75), and
  `[169,118][282,231]` at rotation 3, clear of the nav band (0-135).
- Previous, "1 / 2" and Next sit over the photo at y 922-1057.

**L14. The camera with the lock on.**

- The camera window was portrait (ROTATION_0) whichever rotation it was opened from.
- Each time, the system rewrote `user_rotation` to 0: from 3 at 11:34:49 and from 1 at 11:35:38.
- After closing, the app came back in portrait with the COMPACT layout. Opened from Photo Gallery,
  it came back with the modal drawer open (`r3-after-camera-close`). The landscape rotation the
  app had before the camera did not return.
- The chips are at y 75-210. "Save location: On" `[474,75][609,210]` is directly below the
  punch-hole Rect(512,0,568,75), touching at y 75 without overlapping it.
- "No photos yet" `[407,1748][674,1805]` shows while the Album holds two photos. Whether the count
  is per camera session is unverified.

**L15. The cut-out itself is clear.**

- At rotation 1 the panel's rows start at x 75 (the Mushroom Log row) and x 120 (the section
  headers), clear of the cut-out band. The punch-hole (x 0-75, y 512-568) lies over the panel's
  empty background.
- At rotation 3 the panel starts at x 135, clear of the left nav bar. The right pane ends at
  2241 (Seasonal tab `[1876,399][2240,534]`).
- No app node intersects either punch-hole.

**Portrait, for comparison.**

- In fullscreen the grid-reference strip moves up to y 34-169 (the grid text is
  `[574,34][912,169]`). Its top edge is inside the status-bar and cut-out band (y 0-75),
  horizontally clear of the punch-hole (x 512-568).
- With the search dropdown open on the Seasonal tab, one Back and then a tap on Maps left the
  dropdown open (`r0-map-search-open`). Tapping the field again raised the keyboard with the
  dropdown still open (`r0-search-keyboard`). Two Backs then closed it: the first hid the
  keyboard, the second the dropdown (the handler at `AvailabilityScreen.kt:1400`). Whether the
  first Back was consumed by the tab handler (`:907`) was not determined.

**Not the app.** A grey handle at the left edge in portrait (x ≈0-10, y ≈500-830 in the
screenshots) appears on every portrait capture, and in no dump. It is presumably Samsung's Edge
panel handle (unverified).

## Settings changed and restored

| Setting | Before (11:21:31) | During | After (11:36:50) |
|---|---|---|---|
| `accelerometer_rotation` | 0 | 0 throughout, never changed | 0 |
| `user_rotation` | 0 | set 1 at 11:26:25; set 3 at 11:30:29; rewritten to 0 by the system when the locked camera opened (11:34:49); set 1 at 11:35:27; rewritten to 0 again (11:35:38) | 0 |
| "Lock camera to portrait" | unchecked; no `camera_orientation_preferences` file (default) | checked at 11:34:24 (file written, `camera.lock_to_portrait` true) | unchecked at 11:36:36 (file holds false) |
| "Automatically Save Location to Photos" | checked (`photo_location.auto_save` true) | never changed | checked, file unchanged (mtime 03:17 local) |

**Side effects, reported rather than restored:**

1. `files/datastore/camera_orientation_preferences.preferences_pb` now exists and holds false. The
   default (absent) and the stored false read the same (`DataStoreCameraOrientationPreferenceRepository.kt:22-24`).
   The file cannot be removed without deleting app data.
2. `files/datastore/map_preferences.preferences_pb` was created at 04:25 local (11:25 UTC) and holds
   `map.fullscreen` = false. It was written when the dispatched fullscreen step entered and left
   fullscreen (11:25:13 and 11:25:25). False is also the value read when the key is absent
   (`DataStoreMapPreferencesRepository.kt:68`).
3. `files/mbgl-offline.db`, MapLibre's tile cache, read 04:32 local when checked at about 11:34
   UTC and 04:35 local at the end. Its mtime before the run was not recorded, so this record can
   only say it was written during the run, presumably by map views.

Unchanged: `files/photos` (the same two files), `files/captures`, and the Room database
(`forager.db-wal` mtime 03:14 local). No Forager line in `logcat -b crash`. The same MainActivity
record (`29968400`, task 64) was resumed at the start and at the end.
