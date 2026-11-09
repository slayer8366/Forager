# Stage device check Part 2: run record

**Dispatch:** 2026-09-28-154 (`prompts/preserved/2026-09-29-21.md`). One section per session. Evidence:
`/home/zynergy-labs/Zynergy/device-evidence/2026-09-29-part-2/`, prefixed `s1-`, `s2-`, `s3-`.

## Session 1: layout and the map

**Coder:** `claude-sonnet-5-5` (as configured for the session; not independently readable from inside it).
**Device:** S22 `R5CT321008R` (SM-S908U). The tablet (`R52T506412L`, shown as `unauthorized`) is not touched; every adb call
uses `-s R5CT321008R`.
**Items:** 1-8, 9-12, 27-31, 34-51, 53-56, 58-59, 60, 61-63.

### Setup, done before any launch

**Base.** `origin/journal-redesign` was `aa0dde85` when the worktree was cut. `git diff --stat 85a41257 aa0dde85` touches
only `RECORD.md` and `prompts/preserved/2026-09-29-22.md`, so the code is the planner's clean-suite code. The APK was built with
the worktree detached at `85a41257`, then the branch was restored.

**Build.** `LC_ALL=C.UTF-8 ./gradlew --offline assembleDebug`, BUILD SUCCESSFUL in 2m 8s (log: `s1-build.log`).
- A first attempt, run with a 590 s `timeout`, exited 137 (killed) and produced no APK. The log showed a Kotlin daemon
  connection failure and fallback. It was rerun with no timeout and succeeded. The cause of the kill is not determined.
- sha256 `642d039d8cf08a03564abde22eacf4c1058643e4411548614fe5597c1cc6df98`
- versionName `1.0.1685+g85a41257`, versionCode `1685`
- signer `CN=Android Debug`, SHA-256 `cb2f6da502c3fe7bea8db747414bed47cbc8350944cf8291e9b09806c94f1626`

**Verified full copy (rule 2), taken before the install** into `s1-copy/`:
- 18 files under `files/`, `databases/`, `shared_prefs/`: `forager.db` + `-wal` + `-shm`, `fungi_index.db` + side files,
  `files/photos/` (3 files), `files/mbgl-offline.db` (no side files exist), 5 DataStore files, 2 shared_prefs, `profileInstalled`.
  This is wider than the dispatch's list. `captures/` and `maplibre-offline/` are empty directories.
- Device sha256 (`device.sha256`) equals the pulled files' sha256 for all 18. The device hashes were re-read after the pull and
  are unchanged.
- `forager.db` (on a scratch copy of the db and its wal, so the original was not replayed): header `SQLite format 3`,
  `integrity_check` = ok, `user_version` = 16. Row counts are in `s1-copy/forager-db-verify.txt`
  (mushroom_log_entries 3, log_photos 3, tracks 1, track_points 23, waypoints 3, offline_regions 2, cartography_entries 7,
  planned_trips 0, cached_searches 2, and the ref tables).
- **Caveat:** the dispatch names `files/photos/`, but the copy holds 3 photos, and the owner-vs-earlier-session provenance
  of these rows is not known to me. They are treated as the owner's and are not edited.

**Install (rule 3).** `install -r` returned Success. `firstInstallTime` is unchanged (`2026-09-22 11:15:05`); versionName went
`1.0.1577+g7d17a5c4` to `1.0.1685+g85a41257`. Same signature (an `install -r` with a different one would have failed). The code
declares Room `version = 16` (`ForagerDatabase.kt:167`), the same as the device, so no migration is predicted.

**Starting settings** (restored and read back at the end): `accelerometer_rotation` 0, `user_rotation` 0, `font_scale` 1.0,
window/transition/animator scales 1.0/1.0/1.0, `wm size` 1080x2316. Crash buffer at start: empty.

### Pre-registration

Registered before the first launch of the new build. Source reports are under `docs/audits/`; `RECORD` is `RECORD.md`. A line
number is given only where I have read the code. Code I have not yet read is listed as unverified. I will add anchors in the
results section when a check reads code.

**General method.** Real `adb shell input` and real rotations only. Each measurement comes from a `uiautomator dump` or from
pixels. A tap that is meant to hit a control is a coordinate tap. Item verdicts are pass, fail, not runnable, or owner to judge.

**Predictions.** These are what I expect, so a surprise is visible:
- P1. Most layout items (34-51) pass, since Part 1 and the follow-ups fixed the earlier fails.
- P2. Robolectric reported zero insets for all of them (CLAUDE.md, "Robolectric reports zero window insets"), so I expect at
  least one placement item (37, 38, 45, 46, 49, 62) to show a real-inset difference.
- P3. Item 38's cluster-to-search-bar margin is about 0 px, as RECORD:4875-4876 says. I predict a pixel read of 0 to a few px.
- P4. Items 53-56 are for the owner. They get captures and no verdict from me.
- P5. Item 63's no-permission-prompt half passes (`PhotoViewerDialog.kt:225` says no storage permission on API 29+). The S22 is
  API 34+.

**Pass conditions.**

| Items | Pass condition (source) |
|---|---|
| 1 | A tap on each glyph kind (find, waypoint, track, region, trip) opens its bubble with the right feature; colour-field cells open only at the point stage; the photo glyph and its cells are touched at least once (m1 report "Device-only", first bullet). |
| 2 | A tap inside an offline circle, away from its outline, opens nothing and reaches what is beneath (RECORD:2788). |
| 3 | The tail tip sits on the glyph, measured from the dump/pixels, with the glyph at each screen edge, at 0/90/270. |
| 4 | Bubbles clear the compass strip, nav, rail and cut-out at 0/90/270 by dump bounds. |
| 5 | The bubble follows its glyph after pan, zoom and rotate; a dismissed bubble does not return. |
| 6 | In fullscreen a feature tap does not bring the chrome back. |
| 7 | Directions leaves the app for the phone's navigation app. |
| 8 | The find overlay opens over the Journal and over a day entry. |
| 9 | On first style load an entry is framed (its records inside the view) in the preview, fullscreen and landscape. |
| 10 | Rotation re-fits; the view is kept after a pan and after opening and closing a find. |
| 11 | Track widths are recorded at zoom 13 and 11 by pixel width; the breadcrumb dots and gaps shrink with the width. |
| 12 | The search field refocuses after `clearFocus()`; at a large font scale in short landscape the fields show, the keyboard lowers, and the scroll goes back up. Font scale is restored. |
| 27-31 | Per the landscape report: the picker and the entry report at 90 and 270 with real insets, the scrolling side's remaining height measured, the map side against the cut-out and rail, pan/zoom kept on rotation with the picker open, L2's opening frame on the 154 dp preview, the cut-off control observed, and a tall bubble on the 154 dp preview not clipped. |
| 34 | Portrait: expanded legend, the cluster's last row is above the legend's top. Collapsed, the cluster is back at its earlier bounds. |
| 35 | Portrait dropdown: Latitude, Longitude and "Search this location" have bounds above the keyboard's top. Record whether the scroll lowers the keyboard. |
| 36 | After a tab round trip, pan, zoom, bearing, tilt and follow mode are the same; a new search still moves the camera. |
| 37 | The "i" is in the portrait and fullscreen place; inboard of the rail at 90; in the cut-out band at 270; a touch opens attribution. |
| 38 | At 90 the caption's bounds do not overlap the cluster's. At 90 and 270 the cluster-to-search-bar gap is read from the dump (P3). |
| 39 | At 90 and 270: the pill is beside the bar; the fill is 56 x 156 dp; drag, snap and the handles work; record and return are reachable while recording. Thumb reach goes to the owner. |
| 40 | Legend inboard of the cluster with the cluster on each side, collapsed and expanded, at 90 and 270; legend vs dropdown; the first-compose jump is noted. |
| 41 | Chip row at 90 and 270 with one chip, two chips, a long label and the cluster far-side: alignment, width cap, ellipsis beside the clear button, clear of the central third and nav inset, 12 dp gap; portrait spacing is 4 dp. |
| 42 | In landscape the cluster drags to the nav inset; in portrait it stops above the legend. |
| 43 | J8's pill is touched at its edges; I report whether the edges take a touch. The 48 dp question is the owner's. |
| 44 | Street on Maps then Satellite on the entry leaves Maps on Street; all three are dark at night. |
| 45 | The search notice's top equals the strip's bottom and the cluster is below it; clearing restores the cluster; 0/90/270. |
| 46 | The Maps snackbar meets the floating nav's top in portrait, takes the system-bar inset in fullscreen, and clears the rail and cut-out at 90 and 270. |
| 47 | The Layers sheet's map-type chips are centred and the same height in portrait and landscape. |
| 48 | The nav-bar band shows the map at 0.8 under the Layers sheet and the short-landscape Records sheet; solid in portrait. |
| 49 | The landscape centre-pin row clears the rail and nav bar at 90 and 270, and the pin stays at the map centre. |
| 50 | One Back closes species suggestions and leaves the search panel open. |
| 51 | The Records sheet shows the picker map through in short landscape and is solid in portrait. |
| 53-56 | Owner to judge from captures. I record what I see and give no verdict on the design questions. |
| 58-59 | Captures at night over Topographical and Street and Satellite, the highlighted region, and the entry map with Offline on. The "is 0.85 too bright" question is the owner's. "Nothing changes by day" is compared with a day capture. |
| 60 | With two planned trips and no search, from a cold start, the flags draw by day and at night, the switch works and a tap opens a bubble. The trips are created as "DEVICE CHECK 2026-09-29" rows and deleted afterwards. |
| 61 | A capture (no location) and an import (its own metadata) appear in the "Forager" album with the right date. Gallery copies are deleted afterwards with their paths recorded. |
| 62 | The control's bounds against the real status and nav bars, in portrait and landscape. |
| 63 | No permission prompt appears; a 12 MP JPEG copies without a stall. |

**Cannot run, or unlikely to (registered now, so that it is a declared limit and not a surprise):**
- Items that need new data create only rows named "DEVICE CHECK 2026-09-29 ...". Where the owner's own data is the only
  data on the phone, I use it read-only.
- Item 61's import half needs a photo on the phone to import; if none exists I will say so.
- Item 11's zoom 13 and 11 depend on a track being visible; the phone holds one track.
- A system prompt over the app is not tapped by me (rule 11). It becomes a stop.

### Progress, interim (00:42; the run is not finished, no session verdicts yet)

Evidence is in `s1-*` files in the evidence directory; the live log is `s1-notes.md` there.

- **Item 37, portrait:** pass. A tap at (1040,1915) inside the "i" bounds `[1010,1886][1069,1945]` opened the MapLibre attribution dialog (`s1-i37-p0-attr.png`).
- **Item 1, partly:** the find, photo and region bubbles open on real taps (`s1-m1-p0-tap-glyph.png` and `s1-m1-p0-tap-inside-circle.png`). Waypoint, track and trip glyphs, and the colour-field cells, are not yet touched. All of the phone's existing finds, photos and waypoints are stacked at one spot and the map stops zooming, so the kinds cannot be separated without new data at other places.
- **Item 2:** pass with a caveat. Three taps inside the offline circle, more than 250 px from the glyph stack and more than 100 px from the outline, opened nothing. Nothing lies beneath there, so this shows the interior does not capture a tap, not that a glyph beneath is reached.
- **Observation, no report I have read asserts it:** bubbles opened on taps about 150-170 px (54-60 dp) from the stacked glyphs, and the region bubble on a tap 48 px inside the outline.
- **Rotation setting found changed:** `accelerometer_rotation` read 0 before the install and 1 at the first launch, with the phone at ROTATION_90. I did not set it. I reset it to 0 with `user_rotation` 0. The final read-back will restore the original 0/0.
- **Owner message received mid-run, not actioned:** quoted verbatim in the evidence notes and in the hand-back. It asks to shrink the icon bar, turn the small pill 90° with half of it under the bar, and make the pill the same size as the bar. It is a design change. It has ambiguities (which pill, and what "same size" and "beneath" mean) and it would change the APK that Sessions 2 and 3 reuse. It belongs to a separate dispatch.
- **Owner message withdrawn (00:45):** "Oh sorry ignore that". The pill/icon-bar message above is not a finding and no dispatch follows from it. Items 34, 39 and 40 are judged on their own pass conditions.

### Results so far (interim 2, 00:56; more follow)

Every figure below is from a `uiautomator dump` (`s1-*.xml`) or from the named screenshot (`s1-*.jpg`). The disk filled at 00:51, so
five screenshots from that moment are empty and were deleted, and the remaining PNGs were converted to JPEG q90. Bounds come from
the dumps, not from the JPEGs.

| Item | Verdict | Evidence and reading |
|---|---|---|
| 1 | partly: find, photo, region, waypoint bubbles pass; track, trip and colour-field cells not reached | Find, photo and region bubbles open on real taps (`s1-m1-p0-tap-glyph`). A waypoint I dropped as "DEVICE CHECK 2026-09-29 wp1" opens its own bubble with Directions and Details (`s1-m1-waypoint-bubble`). On the entry map the ORIGIN waypoint and the track were not separable from the find: taps at their glyphs returned the find-1 bubble. No planned trips exist (`planned_trips` = 0), and I have not yet made one (item 60). |
| 2 | pass, with a caveat | At one zoom level in, three taps inside the offline circle (>250 px from the glyphs, >100 px from the outline) opened nothing. Nothing lies beneath there, so this shows the interior does not capture the tap, not that a glyph beneath is reached. |
| 3 | partly | Left edge (photo glyph at x~58): card clamped to x=35, tail tip on the glyph's cap but at its right edge, not centred. The entry map's right-side glyph (x=947): tail tip on the cap. Top, bottom, right edge and 90/270 not tested. |
| 5 | pan and dismiss pass; rotation fails | Pan re-anchors (tail moved with the glyph). After X, a pan does not bring the bubble back. **After a rotation from 0 to 90 with the bubble open the bubble stayed at its portrait place** (`s1-m1-rot90-open`, bounds [~35..780 x ~700..990]) while the glyph was at about (937,573); it overlapped the cluster. A 50 px pan re-anchored it (`s1-m1-rot90-afterpan`). Zoom re-anchor not tested. |
| 6 | pass | In fullscreen a tap on the waypoint pin opened its bubble; the bottom-nav labels are absent from the dump and "Exit fullscreen" stayed. |
| 7 | pass | Directions launched `act=VIEW dat=geo:0,0?q=45.3262615,-122.6181016(DEVICE%20CHECK%202026-09-29%20wp1)`, `cmp=com.google.android.apps.maps/.MapsActivity`, which was the top activity; Back twice returned to Forager. |
| 8 | pass | Find bubble > "Open in Journal" opens the find page over the Journal; the day entry's find-2 bubble > "Open find" opens over the day entry; Back returns to the entry. |
| 34 | pass (portrait) | Collapsed cluster last row [922,1583][1057,1718], legend chip [842,1747][1057,1882]. Expanded legend card [269,1596][1057,1866]; the cluster moved up and its last row ends at 1563: 33 px clear. Re-collapsed: the rows are back at [922,1437]/[922,1583], as before. Needs Diagnostics "Synthetic forecast layers" ON (original OFF; restore at the end). |
| 35 | pass | Latitude [45,990][528,1171], Longitude [551,990][1035,1171], "Search this location" [355,1255][725,1312]; the keyboard's top is at about y=1398. A scroll of the dropdown lowered the keyboard (`mInputShown` true to false). |
| 36 | partly | Pan and zoom survive Maps > Journal > Maps (`s1-t36-before`, `s1-t36-after`, glyph at the same pixel). Locate then round trip: same view. Bearing and tilt cannot be set with a single pointer: not runnable. "A new search still moves the camera": not yet run. |
| 37 | pass at 0, 90, 270 | "i" at [1010,1886][1069,1945] (0); [1886,1010][1945,1069] with the rail from x=1956 (90); [2246,1010][2305,1069] (270). A tap opened the "MapLibre Android" attribution dialog each time (`s1-i37-*`). Fullscreen "i" not yet checked. |
| 38 | pass (caption); margin is 14 px, not ~0 | At 90 and 270 the search bar bottom is 209 and the cluster top 223: 14 px by bounds. The coordinate caption [1260,87]..[1933,178] does not overlap the cluster (x 98-391). |
| 40 | partly: pass at 90 with the cluster on either side | Cluster left, expanded legend [1145,720][1933,990]: no overlap. Cluster dragged right, expanded legend [830,720][1618,990], the pill's left edge 1640: 22 px clear. 270 (cluster right by default): legend [1115,720][1903,990], pill from 1925. Dropdown: the legend shows through the semi-transparent dropdown behind "Search this location" (`s1-search-top`). |
| 42 | pass, with an observation | At 90 the cluster dragged down to [1798,935][1933,1070] (bottom 1070 of 1080). Its "+" then overlaps the "i" [1886,1010][1945,1069] (`s1-drag-down-r90`). Portrait stopping above the legend not yet tested. |
| 47 | pass (portrait) | The three chips sit at y 651-708 (same height); outlines x 124-328 and 715-958 of 1080: margins 124 and 122. Landscape not yet measured. |

**Observations for the owner (not gates):**
- The expanded legend is capped at 96 dp (`MapLayersSheet.kt:440`). With the title wrapped to two lines, the ramp's "0%" and "100%" labels are cut off at rest (`s1-leg-expanded-p0`).
- Long-press then drag is what moves the cluster (`AvailabilityCompactMapUi.kt:910`, `detectDragGesturesAfterLongPress`). A plain swipe on the handle does nothing.
- Bubbles open on taps up to about 54-60 dp from a glyph; nothing I have read states that tolerance.
- The bubble sits under the cluster at 90 where they overlap.
- `accelerometer_rotation` was set back to 1 twice without my setting it (cause outside the app; the app has no code writing it).

### Results so far (interim 3, 01:27)

Evidence prefix `s1-` in `/home/zynergy-labs/Zynergy/device-evidence/2026-09-29-part-2/`; the full live log with every bound is `s1-notes.md` there. **Measurement basis:** every verdict below rests on `uiautomator` bounds unless marked "PNG", except that positions read off screenshots are from JPEGs (the disk filled) and are marked "visual". Item 48 rests on raw PNG pixels (`s1-i48-bandA-crop.png`, `s1-i48-bandB-crop.png`).

**Notification prompt (planner's request).** Trigger: Maps tab at 270, my `input tap 1993 729` on "Start recording track" (`[1959,695][2027,763]`), the first recording start; status-bar clock 1:03 in `s1-rec270-a.jpg`. The logcat buffer had rolled past it (earliest kept FGS line 01:06:44 for the second start), so the log line is not available. The prompt was the system "Allow Forager to send you notifications?" (`GrantPermissionsActivity`). The owner tapped Allow; `POST_NOTIFICATIONS` went from `granted=false` to `granted=true`. Finding: starting a track recording raises the notification prompt, not only choosing a backup folder. To restore: `adb shell pm revoke com.zynergylabs.forager.app android.permission.POST_NOTIFICATIONS`, then read it back (done at the end).

| Item | Verdict | Reading |
|---|---|---|
| 9, 10 | pass (entry 2026-09-27) | At 0 the preview map is [0,437][1080,1247]; at 90 [116,400][1916,832] (432 px = 154 dp) and at 270 [401,400][2201,832]; both finds and the cluster of glyphs are inside in each; rotation re-fits. A tap on empty preview map opens the entry map fullscreen. Pan-then-find-open-close: not run. |
| 11 | not run | Zoom 13/11 track widths: the map does not zoom out from the entry preview with real input in a way I could measure widths; only 1 old track and my 1-point tracks exist. |
| 12 | not run | Large font scale and refocus not exercised. |
| 27, 28 | pass | Picker (Journal > Records > Offline maps chip) at 90 and 270: controls [1061..1871] / [446..1256] all visible with non-zero size, map beside them; pin text "Pin at: 45.3222, -122.6252" identical at 270, 90 and 0 after a pan. Entry report map centred between insets. |
| 29, 30, 31 | pass / observation | L2 opening frame on the 154 dp preview holds the day's find spread with margin. The bottom-right clickable [1725,856][1871,991] is the Offline map switch, fully inside (rail from 1956). Bubbles (find card about 103 dp, photo card 118 dp) fit inside the 154 dp map and are not clipped; a bubble taller than the map could not be produced. The card covers its own glyph. |
| 39 | partly pass | At 270 with recording on: record/stop [1959,695][2027,763] and return [1959,841][2027,909] both on screen and clear of the legend; a real tap on return started navigation (a banner covers the search bar and strip) and Stop navigating and Stop recording worked. Drag and snap (long-press) worked at 90 with both handles. Thumb reach: owner. The 56 x 156 dp fill: the container above the pill is drawn as one surface (`s1-drag2-r90.jpg`); the owner to judge. |
| 41 | pass at 90 (cluster either side) and 270, one and two chips and a long label | Chips sit at the bar's start on the side away from the cluster and follow the cluster when it is dragged; 104 px clear of the cluster; the long label ellipsises before the X and the row is capped at the bar's width. Portrait spacing: taxon pill [219,272][861,362] to J8 clickable [219,374][680,509], 12 px (4 dp). |
| 43 | pass | Taps 4 px inside each edge of J8's pill opened its list; taps 4 px outside opened nothing. Feel: owner. |
| 44 | pass (Street/Satellite half) | Maps Street; the entry map showed Street; choosing Satellite on the entry left Maps on Street. Night half with 58/59. |
| 45 | pass at 0 | Notice "Enter a valid latitude (-90 to 90) and longitude (-180 to 180)." [0,254][1080,391], its top at the strip's visible bottom (254); the cluster moved down to Fullscreen [956,464] (button top 430), 39 px below the notice; clearing the notice returned the cluster. 90/270 not run. |
| 46 | pass | Trip-start snackbar ("Do Not Disturb is on...") at 0: card ends at 1925, the floating nav starts at 1956 (31 px); fullscreen: 32 px above the system nav band; 90: 59 px from the rail, 53 from the cut-out; 270: 63 and 51. It draws over the legend and the cluster's lower rows. |
| 48 | pass, portrait Layers sheet (PNG) | Screen rows y 2215-2300 in the nav band: with the sheet open pixel std is 0.14-0.22 of the same rows with no sheet, mean (35,32,30): the map shows through at about 0.8, not a flat band. The landscape Records sheet was not opened. |
| 49 | pass on clearance | At 90: OK [120,922][1004,1057], Cancel [1027,922][1911,1057], rail from 1956. At 270: [405..1289] and [1312..2196], rail ends 360. Pin x = map centre exactly; pin y hot spot unknown (node 498..611 against map centre 582). |
| 40, 42 | see interim 2 | unchanged |
| 36 | pass | search by coordinates moved the camera (NET). Bearing/tilt not settable. |

**Finding to note:** deleting my test data: track details sheet has only Share (no Delete), so five 1-point test tracks are left; see the clean-up section at the end.

### Session 1: final results (01:40)

Same basis as above (uiautomator bounds; visual positions from JPEG marked; item 48 from PNG). The complete per-tap log with every bound is `s1-notes.md` in the evidence directory. **Crash buffer:** empty at start; at the end 0 `FATAL` lines and no Forager line.

**Verdicts not given above**

| Item | Verdict | Reading |
|---|---|---|
| 1 | partly | see interim 2. Track and colour-field cells (needs an unstacked location, and a point-stage zoom) not reached; trip glyph: the flag opened its bubble ("DEVICE CHECK 2026-09-29 trip B", coordinates, Directions) at 90. |
| 3 | partly | as interim 2. |
| 4 | not run separately | Bubble bounds against strip, nav, rail and cut-out were seen in passing: photo bubble at 0 [68,849]..; at 90 the bubble overlapped the cluster until a pan (see 5). |
| 5 | **fail on rotation** | as interim 2 (bubble stays at the portrait place until the next pan). |
| 11, 12 | not run | see interim 3. |
| 36 | pass except bearing and tilt (not settable with single-pointer input: not runnable) | |
| 37 | pass at 0, 90 and 270; **fail in portrait fullscreen** | In fullscreen the "i" is at [1010,2246][1069,2305], inside the system navigation band (`NavigationBar0`, from y=2181). Two real taps at (1040,2275) and (1040,2262) opened no dialog and focus stayed on MainActivity (`s1-fsi`, `s1-fsi2`). |
| 38 | pass (caption); margin 14 px, not about 0 | interim 2 |
| 42 | landscape pass with observation; portrait not run | interim 2 (the "+" then covers the "i" at 90) |
| 45 | pass at 0 only | 90 and 270 not run |
| 50 | pass with a qualification | With the keyboard up, Back 1 closes the keyboard, Back 2 closes the suggestions popup leaving the search panel open, Back 3 closes the panel. "One Back" holds once the keyboard is down. |
| 51 | not run | I could not find the Records sheet. See the mistake below. |
| 53 | not run | (no ORIGIN/END pins isolated) |
| 54, 55, 56 | owner to judge | captures: `s1-night-*.png`, `s1-list-tab`, chip screenshots `s1-j8p0/j8r90/j8r270/long90`. The taxon chip and J8 chip are visible against the map at night; no shadow. |
| 58, 59 | owner to judge (captures only) | Night is ON at baseline (map sepia). `s1-night-Street.png`, `s1-night-Topographical.png`, `s1-night-Satellite.png` (PNG crops y 700-1400): the highlighted region outline is a teal ring with a white dashed line on all three. No day comparison, no entry-report-at-night capture. |
| 60 | pass | Cold start, no search: flag drawn for trip B; a tap opened its bubble; Layers > Planned trips off removed the flag and on restored it. Day (Night Maps off) not run. |
| 61 | partly (import only) | The phone has no photo without a location, so the capture-without-location half could not be tested. Saving the existing photo: MediaStore row `Pictures/Forager/forager-photo-20260927-211632.jpg`, 1,841,146 bytes = the source file's size, `datetaken` NULL, `date_added` set. The Gallery app's own date display was not checked, so "the right date" is **unverified**; NULL `datetaken` is the thing to check. |
| 62 | partly | Viewer: Close [23,98][158,233], Save [922,98][1057,233], status bar bottom 75: 23 px clear. Landscape not measured. |
| 63 | pass | No permission prompt; the row was added within 1 s of the tap (12 MP not tested: the file is 1.8 MB). |

**Mistake made (Decisions I made / Flags):** In Journal > Records > Offline maps at 90, tapping "Download Maps" started a real download at once (no confirm step): Offline maps went 2 to 3. I had expected a sheet. That region and the other test data are gone after the restore below. **Flag:** a single tap on Download Maps downloads with no confirmation.

**Decisions I made**
- **Restored the phone from the verified copy at the end of Session 1** (force-stop, `run-as` cat of all 18 files back). Reason: the track details sheet has no Delete, so five 1-point test tracks, ~10 of my waypoints, one saved entry, two trips and one download could not be removed from the UI. Read-back after the restore: all 18 sha256 equal the copy's; `forager.db` integrity ok, `user_version` 16, every table's row count equal to `forager-db-verify.txt`. The dispatch's rule 6 names the restore for Session 3; using it here goes beyond that and is disclosed. Nothing of the owner's changed between the copy and the restore, so the restore is a no-op for the owner's data (the copy was taken before I did anything).
- Turned the debug "Synthetic forecast layers" switch ON for 34/40, and started recordings for 39/46; the restore returned the DataStore file to OFF.
- The owner tapped Allow on the notification prompt; I revoked it (`pm revoke`) and cleared the user-set flag (`pm clear-permission-flags ... user-set`); read back `granted=false, flags=[USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]` = the start state.
- Converted PNG evidence to JPEG q90 when the disk filled; deleted my worktree's `app/build`.
- Deleted the one Gallery row I created (`content delete`, MediaStore id 1001239272, path `Pictures/Forager/forager-photo-20260927-211632.jpg`); the folder no longer lists.

**Flags outside scope**
- `accelerometer_rotation` returned to 1 twice on its own (the app has no code that writes it); left 0/0 at the end.
- Item 37 in fullscreen and item 5 (rotation with a bubble open) are the two device failures.
- Starting a track recording raises the notification-permission prompt (planner's point 2).
- The download-with-no-confirm above.
- A cross-session message (planner ref change) and an owner message about the pill layout (withdrawn) are quoted in `s1-notes.md`.

**Restore read-back.** Rotation 0/0, font_scale 1.0, animation scales 1.0/1.0/1.0, `wm size` 1080x2316, versionName `1.0.1685+g85a41257`, firstInstallTime `2026-09-22 11:15:05` unchanged, `POST_NOTIFICATIONS` not granted; map type, Night Maps and the Diagnostics switch are back to the copy's DataStore values because the DataStore files were restored byte for byte.

**Items with no verdict here:** 11, 12, 51, 53, 61 (capture half), 45 at 90/270, 42 portrait, 4 (separate). They are not runnable or were not reached; they are not passes.

## Session 2: the Journal flows

**Coder:** `claude-sonnet-5-5` as configured for the session (not independently readable from inside it).
**Device:** S22 `R5CT321008R`. The tablet is not touched; every adb call uses `-s R5CT321008R`.
**Items:** 13-26, 32-33, 52, 57. Evidence prefix `s2-`, directory `/home/zynergy-labs/Zynergy/device-evidence/2026-09-29-part-2/`.

### Deviation: a duplicate Session 2 window, and the first copy lost

At about 02:05 a second Claude window (`cse_01FHkngY3...`) started Session 2 on the same phone and wrote into `s2-copy/`. I found
three files there that I had not written, and stopped before running any item. The planner's message (quoted in substance;
the planner's own words are in its message to this session) says that window was told by the owner to ignore the prompt and
delete what it had done. It **deleted `s2-copy/` entirely, including my first verified copy** (21 files, sha256-equal to the
device). It also reset the local `device-part-2` worktree to `f96422f5`. On the phone it only force-stopped and read through
`run-as`. I confirmed afterwards: `s2-copy/` was gone, no such process was left running, and the worktree was at
`f96422f5`; I pulled `origin/journal-redesign` with `--no-rebase` (fast-forward to `2dc77164`). My `force-stop` at about 02:07
may have interrupted that window's reads; that is unverified.

### Setup

- **Build (amendment 1):** versionName `1.0.1685+g85a41257`, versionCode 1685, `firstInstallTime` 2026-09-22 11:15:05, read before anything.
  Not built, not installed. The APK's sha256 was not re-read by me (the run record's Session 1 section carries it).
- **Base:** `device-part-2` at `2dc77164` = `origin/journal-redesign` when read.
- **Verified full copy (rule 2), second take,** in `s2-copy/`: 21 files (`databases/` forager.db, -wal, -shm, fungi_index.db + side
  files; `files/photos/` 3; `files/mbgl-offline.db`; 5 DataStore files; `no_backup/androidx.work.workdb` + side files;
  2 shared_prefs; `profileInstalled`). Device sha256 (`device.sha256`) equals the pulled files' (`local.sha256`) for 21 of 21;
  the device hashes re-read after the pull are unchanged. `forager.db` on a scratch copy of the db and wal: header
  `SQLite format 3`, `integrity_check` ok, `user_version` 16; every table's row count is in `forager-db-verify.txt` and equals
  Session 1's (mushroom_log_entries 3, log_photos 3, tracks 1, track_points 23, waypoints 3, offline_regions 2,
  cartography_entries 7, planned_trips 0, cached_searches 2 ...). This is 3 files wider than Session 1's 18 (the WorkManager db).
- **Starting settings:** `accelerometer_rotation` 0, `user_rotation` 0, `font_scale` 1.0, animation scales 1.0, `wm size` 1080x2316. Crash buffer empty.
  Disk 4178 MB available at start.

### Pre-registration (before the first launch of this session)

Sources: `2026-09-28-leaving-the-journal-fixes-completion-report.md` (F1/F2/F4 Device-only 1-9 at lines ~261-277; F3 Device-only
1-7 at ~457-470), `2026-09-28-maps-search-bar-after-entry-completion-report.md` Device-only 1-4, `2026-09-28-drawer-back-completion-report.md`
Device-only 1-7, RECORD.md:4195 (item 52), RECORD.md:3948 (item 57). Code read so far, by grep only, not read through:
`AvailabilityScreen.kt:1273` ("Saved to Drafts"), `:1335` ("Save your changes?" in the open-entry-switch path), `CartographyEntryEditScreen.kt:287`
("Finish entry"), `:320` ("Save your changes?"), `MapBubbles.kt:307-310` ("In <find>", "In N finds", "Kept in N ..."). I have not read
the leave logic itself; the pass conditions are the reports' "Expected" lines, not my reading of the code.

**Pass conditions** (the report's Expected line; "Back" is a real `input keyevent 4`; each at 0, then 90 and 270 where the dispatch says so):

| Item | Pass condition |
|---|---|
| 13 | A committed find open in its report survives Maps, Tools, and home-and-return: no "Saved to Drafts", the find remains. |
| 14 | An unchanged editor's Back: no snackbar. After a change, Back: "Saved to Drafts"; Discard: the change is gone, the find intact with its photos. |
| 15 | New find, type, Back, open from Drafts within 4 s, Save, then tap Discard: the saved find stays. |
| 16 | Day entry in Edit with typing, Maps and back: editor with the text. Back: "Save your changes?"; Discard once, Save once. |
| 17 | Item 16 with a withheld waypoint, and with a new entry ("Finish entry" on return). |
| 18 | Dirty editor, night-mode toggle (activity recreation), return: editor present. |
| 19 | Tools then Back closes only the drawer; on Maps a second Back closes the state; over the dropdown (Maps and Journal), "Set on map", the Log-a-find picker and the landscape add-action menu. |
| 20 | The Journal dropdown closes on Back with no drawer open. The F4 report's own finding says it did not close at `b91a543`, so **I predict fail**. |
| 21 | A find's report stays open across Maps/Journal, Tools+Back and home-and-return with no snackbar. |
| 22 | The same from an editor with a change: the editor returns with the change. |
| 23 | Changed find, Maps, "Log a find": "Saved to Drafts" and the new find opens. Unchanged re-edit and viewed find: no snackbar, no second draft. |
| 24 | Item 23 through a bubble's "Open in Journal". |
| 25 | Camera round trip from a find's editor: the find is still open with the photo. Needs a camera; may be a system-camera surface (stop for the owner if a prompt appears, rule 11). |
| 26 | A find opened over Entries from a bubble, then Maps and Journal: stays over Entries; Back returns to Entries. |
| 32 | From a day entry (report, then editor) to Maps: search bar shows with no bare band, above the strip; Journal keeps the entry. Portrait, short landscape (bar on the punch-hole side, capped short of centre), and from the entry map's fullscreen. |
| 33 | Tools then Back over each state listed in drawer-back Device-only 1-6, at 0/90/270: only the drawer closes; Settings panel Back returns to the Tools panel, a second Back closes the drawer. |
| 52 | Photo bubble: 1-3 shown entries: "In <find>" with no "Kept in"; more than 3 (per `MapBubbles.kt:307-310` the "In N finds" form for several finds): one "Kept in N"; no find: no line; switch off: the line as before. |
| 57 | The forced-failure half cannot be run: no debug hook exists (I grepped `app/src/main` for a fail-write hook and found none) and adding one is the owner's call. **Not runnable**; I add no hook. The off-screen-failure-at-next-open half depends on the same forced failure. |

**Predictions:** (P1) item 20 fails. (P2) F1-F3 mostly pass, since Robolectric covered the logic and the device part is the real Back key.
(P3) Items 15 and 24/25 are the likeliest to be hard to drive with real input (the 4 s window; the camera). (P4) Any item needing a
data change uses rows labelled "DEVICE CHECK 2026-09-29 ..." and, where the UI cannot delete them (a track has no Delete), the
phone is returned to the copy at the end and I say so (amendment item 2).

**Declared limits:** item 57's forced half; item 52's ">3 entries" case needs 4 entries that keep one photo (created as DEVICE CHECK rows;
if it proves too costly through the UI I will record it as not reached); tablet-only halves (F1 wide, F3 step 5) are not runnable on the S22.
A system prompt over the app is not tapped by me (rule 11).

### Session 2: results so far (interim 1; portrait, rotation 0 only; more follow)

Every verdict rests on `uiautomator` dumps (`s2-*.xml`) and the named PNGs in the evidence directory. Real input only.
The live log is `s2-notes.md` there. **Rotation lock:** `accelerometer_rotation` read 1 at the first launch (start read 0) and
again about 2 minutes later, neither set by me (amendment 7); each time I set 0/0 again. A `fixrot` guard now runs before each step.
Test data: my finds `DEVICE CHECK 2026-09-29 f1`..`f4` and two day entries (`... entry`, `... new`); the phone is returned to the
copy at the end (a find/entry created through the UI can be deleted through the UI, but the return to the copy is the cleaner read-back).

| Item | Verdict (at 0) | Reading |
|---|---|---|
| 13, 21 | pass | An owner-era find's report (`Find on 2026-09-27`, "DEVICE CHECK find 1"): Maps, back to Journal, Tools then one Back (drawer only; `s2-f1-tools.png`), home key and relaunch: the find stays open in its report each time and no "Saved to Drafts" node is in any dump (`s2-f1-*.xml`). |
| 14 | pass (photos half not covered) | My find f1. Edit, no change, Back: no snackbar, no new draft (Drafts stayed at the pre-existing "Find on 2026-09-28"). Edit, type, Back: "Saved to Drafts" + Discard, Drafts 2; Discard tapped inside the snackbar: Drafts back to 1, the committed find intact with its identification and no "changed text". f1 has no photos, so "with its photos" is **not covered**. A first Discard tap of mine came after the 4 s snackbar had expired and hit the card beneath; it was invalid and redone (noted in `s2-notes.md`). |
| 15 | pass | New find f4, type, Back; within about 3 s: Drafts tab, the draft, Save, then Discard on the snackbar still up (`s2-item15-pre-discard.png` shows "Saved to Drafts / Discard" after the Save). Afterwards Finds is 6 (2 earlier + f1..f4), Drafts 1, and the f4 report is open: the saved find stays. |
| 16 | pass | Day entry in Edit, typed, Maps and Journal: the editor with the text. Back: "Save your changes?"; Discard: the entry unchanged, no typed text. Repeated: typed, Maps/Journal, Back, Save: the entry shows the text. |
| 17 | partly | The new-entry half passes: a new entry, typed, Maps and Journal: the editor with the text and "Finish entry". The withheld-waypoint half was not run. |
| 18 | pass, with an observation | Dirty editor, `cmd uimode night no` (recreation): the editor is there with the text. **Observation:** a "Welcome back — This entry had an edit still pending when the app went to the background. Continue editing, submit it, or save it as a draft." dialog appears (`s2-n1x.png`) after each recreation; no report I read mentions it. Night mode was restored to `yes` and read back. |
| 19 | pass at 0 for four of five | With the Journal dropdown, the Maps dropdown, "Set on map", and the Log-a-find picker each open: Tools, then Back closes only the drawer (state still shown), a second Back closes the state. The landscape add-action menu is a 90/270 case, not yet run. |
| 20 | **pass; my prediction P1 was wrong** | The Journal dropdown, no drawer: Back 1 lowers the keyboard, Back 2 closes the dropdown; the Journal tab stays. The F4 report's finding was made at `b91a543`; I have not established what changed. |

### Session 2: results so far (interim 2; portrait, rotation 0; items 22-26, 32, 33)

| Item | Verdict (at 0) | Reading |
|---|---|---|
| 22 | pass | Editor of a committed find, typed "F3edit" (a change): Maps and Journal, Tools + one Back, home key and relaunch: the editor and the text come back, no snackbar (`s2-v1`, `-v2`, `-v4`). The relaunch happened to land in landscape (the rotation lock flipped, see notes); after setting portrait the text is still there. |
| 23 | pass | Changed find, Maps, "Log a find" (the picker OK): "Saved to Drafts" + Discard, the new find's editor opens (`s2-w1.png`), and the change is in Drafts at once (2 to 3 after). An unchanged re-edit, and a viewed find, then "Log a find": no snackbar and Drafts unchanged (`s2-x1`, `s2-y1`). |
| 24 | pass | A dirty find editor (typed "G24"), Maps, a find glyph's bubble (tapped at about (560,1150); the stack also holds the owner's photo glyph, whose bubble says "Not in a find or a journal entry"), "Open in Journal": "Saved to Drafts" shows and that find opens in its report (`s2-z3.png`). |
| 25 | pass | A find's editor, Camera (the app's own camera, "Save location: On"), Take photo ("1 photo taken"), Back: the editor is open with the photo (`s2-c25e.png`). Saved; f1 now carries a photo. **Observation:** the camera surface turned the screen to landscape; user_rotation had to be set back to 0. |
| 14 (photos half) | pass | f1 with a photo, edited ("photochg"), Back, Back, Discard inside the snackbar: Drafts unchanged at 3, the find shows its photo. |
| 26 | pass | Journal on Entries; Maps, a find's bubble, Open in Journal: the find opens over Entries; Maps and Journal: still over Entries; Back: the Entries list. |
| 32 | pass at 0 (portrait) | Day entry report and editor: Maps shows the bar at [119,65][1057,200] above the strip at y 207, no bare band (`s2-s32b.png`); Journal keeps the entry each time; the same from the entry map's fullscreen. Short landscape not yet run. |
| 33 | pass at 0 for 8 of 9 states | Tools then Back closes only the drawer, with the state kept, over: a clean editor, a report, a dirty editor (then Back 2 raises "Save your changes?"), the Records Finds chip (chip stays selected), the Entries album, the drafts list, the entry map in fullscreen, the pull-photo picker. Settings over an open entry: Back 1 gives the Tools panel, Back 2 closes the drawer, the entry is open. The short-landscape search header is a 90/270 case, not yet run. |

**Observations (not gates):** (a) a clean day-entry editor's second Back leaves it with no prompt; (b) the "Welcome back" dialog (item 18); (c) two quick taps on the entry map's preview zoom it instead of opening fullscreen; a single tap opens fullscreen and the bottom nav's Tools still opens the drawer there.

### Session 2: rotations 90 and 270, item 52, item 57, and the final state

| Item | Verdict | Reading |
|---|---|---|
| 13, 21 at 90 and 270 | pass | A find's report (`Find on 2026-09-29`): Maps and Journal, Tools + one Back, home and relaunch: the report stays each time, no snackbar (`s2-r90a..d`, `s2-t270e..h`). |
| 19 at 90 and 270 | pass (landscape add-action menu) | The add menu (Trip / Find / Waypoint) open, Tools, Back: only the drawer closes and the menu stays; Back 2 closes the menu (`s2-am1..4`, `s2-t270j..m`). |
| 16 at 90 | pass | Day-entry editor, typed "R90", Maps and Journal: editor and text stay; Back: "Save your changes?"; Discard: no "R90" anywhere (`s2-k90m..o`). A first attempt's typing never reached the field (a tap in the label row did not focus it); it was redone with the tap in the field body. |
| 32 at 90 and 270 | pass | Day entry report and editor to Maps: the search bar is at [194,74][1113,209] at 90 (starts on the punch-hole side, ends short of the 1158 centre line) and [1299,74][2218,209] at 270 (starts past the centre, on the punch-hole side); Journal keeps the entry each time (`s2-e90d`, `s2-t270p`). |
| 33 at 90 | pass (short-landscape search header) | Entries tab, Search button [1590,84][1725,219], header up: Tools, Back closes only the drawer (header stays); Back 2 hides the header (`s2-sh3..6`). An earlier note of mine said no control was found: that was wrong; the button is on the Entries and album headers, not on the Records one I was looking at. |
| 52 | partly, mostly not reached | With two of my entries shown on the map ("2 journal entries on map"): my find f2's bubble shows two `Open entry 2026-09-29` date lines (the 1-3 shown-entries case for a find; `s2-b52-560-1150.xml`). The owner's photo glyph's bubble says "Not in a find or a journal entry" (it is in no find and no shown entry; the code sets that text when no find and no shown entry apply, `MapBubbles.kt:317-318`). **Not reached:** the photo bubble with "In <find>" (my photo glyph shares one spot with the owner's, and a tap reaches only the top glyph; the map stops zooming there, as Session 1 found), the more-than-3 "Kept in N" case (needs four entries), and the switch-off case. |
| 57 | **not runnable** | No debug hook exists to force a refused write (`grep` of `app/src/main` for a failing-write hook found none), and adding one is the owner's call; I added none. Whether each of the seven messages is legible, long enough and clear of the keyboard and cluster at 0/90/270, and the next-Journal-open display of an off-screen failure, are therefore unverified. |

**Not run, declared:** 14, 15, 17 (withheld-waypoint half), 18, 22-26 at 90 and 270 (each was run at 0 only); 33's other states at 90/270; 32's entry-map fullscreen at 90/270; 31-style wide-window halves (F1 wide, F3 step 5: the tablet). Nothing here is a pass by silence.

**Deviation recap:** the duplicate window and the lost first copy (above). One more of mine: a first Discard tap in item 14 came after the snackbar had expired and was redone.

**Restore read-back (the phone was returned to the verified copy, as Session 1 did).** Reason: the finds f1..f4, two day entries, drafts, one camera photo, and each "show on map" flag were created through the UI, and the return to a byte-equal state is the read-back. After force-stop: the one photo file the camera added (`files/photos/ccf4b8a9-...jpg`) was removed and every file in the copy was written back. A first restore pass left 7 of 21 files unequal (WAL/SHM, `mbgl-offline.db`, `profileInstalled`, the WorkManager db files, one shared_prefs file; `mbgl-offline.db` and `androidx.work.workdb-wal` were longer than the copy, so the `cat >` overwrite did not fully replace them); the cause is not determined. A second pass that removed each file first and rewrote it gave **21 of 21 sha256 equal to the copy**, still equal 8 s later, no app process. `forager.db` (scratch copy of db and wal): `integrity_check` ok, `user_version` 16, every table's row count equal to `s2-copy/forager-db-verify.txt`. No Gallery rows were made (`content query` on `Pictures/Forager` returns none); temporary files on `/sdcard` were deleted.

**Settings read-back:** `accelerometer_rotation` 0, `user_rotation` 0, `font_scale` 1.0, animation scales 1.0/1.0/1.0, `wm size` 1080x2316, uimode night `yes` (the start value, restored after item 18), versionName `1.0.1685+g85a41257`, firstInstallTime `2026-09-22 11:15:05`, POST_NOTIFICATIONS `granted=false` (never granted this session). Crash buffer at the end: empty; no `FATAL EXCEPTION` in main/crash. `df -m /`: 2114 MB available at the end.

**Decisions I made:** stopped at the duplicate window and resumed on the planner's word; used my own labelled finds/entries rather than editing existing ones; restored from the copy rather than deleting through the UI (amendment 2); used `cmd uimode night` for item 18's activity recreation and restored it; used the app's own camera for item 25 (no prompt appeared); set `accelerometer_rotation` back to 0 whenever it changed (it flipped to 1 at least three times without my doing it, plus a landscape at relaunch).

**Flags outside scope:**
- The "Welcome back" dialog after each activity recreation with a pending edit (item 18) is not in any report I read.
- The camera surface turns the screen to landscape and leaves it there until `user_rotation` is set.
- A clean day-entry editor's second Back leaves it with no prompt (correct, noted so it is not read as a gap).
- The owner-era `Find on 2026-09-28` draft (Drafts) and the "5 unfinished entries" were present before and untouched.
- The `Show on map` menu entry becomes "Hide from map"; the entry map's preview zooms on two quick taps.
- Disk is at 2114 MB available, close to the amendment's 2048 MB floor.

## Session 3: backup and restore

**Coder:** `claude-sonnet-5-5` as configured for the session (not independently readable from inside it).
**Device:** S22 `R5CT321008R` (SM-S908U). The tablet is `R52T506412L` (SM-X800); it is not touched, and every adb call uses `-s R5CT321008R`.
**Items:** 64-76, plus the -153 items (the "Backups" channel, the permission request when the schedule is turned on, the in-app fallback at the
next launch, Replace re-iding restored regions, "Replace the existing backup file?"). Evidence prefix `s3-`, directory
`/home/zynergy-labs/Zynergy/device-evidence/2026-09-29-part-2/`.

### Setup (before any launch)

- **Base:** `device-part-2` at `fd560563` (Session 2's last push), already containing `85a41257` (`git merge-base --is-ancestor`).
- **Build (amendment item 1):** versionName `1.0.1685+g85a41257`, versionCode 1685, `firstInstallTime` 2026-09-22 11:15:05,
  `lastUpdateTime` 2026-09-29 00:30:31, read from `dumpsys package` before anything else. Not built, not installed. The APK's sha256 was not re-read by me.
- **Starting settings:** `accelerometer_rotation` 0, `user_rotation` 0, `font_scale` 1.0, window/transition/animator scales 1.0/1.0/1.0,
  `wm size` 1080x2316. Crash buffer (`logcat -b crash -d`) empty. Launcher in focus, screen awake. POST_NOTIFICATIONS `granted=false` (Session 2's read-back).
- **Deviation, disk:** `df -m /` reads **977 MB available**, below the amendment's 2048 MB floor for bulk evidence (Session 2 ended at 2114 MB; something else on
  the machine used about 1.1 GB since). I did not delete anything of another session's. This session's evidence is the 34 MB copy plus PNG crops, so it does not
  write in bulk; I will re-read `df` before each batch and stop if it falls under 300 MB.
- **Verified full copy (rule 2), `s3-copy/`:** force-stopped, then pulled through `run-as`: 21 files (`databases/` forager.db, -wal, -shm, fungi_index.db + side files;
  `files/photos/` 3; `files/mbgl-offline.db` (23,674,880 bytes); 5 DataStore files; `files/profileInstalled`; `no_backup/androidx.work.workdb` + -shm + -wal; 2 shared_prefs).
  Device sha256 (`device.sha256`) equals the pulled files' (`local.sha256`) for **21 of 21**; the device hashes re-read after the pull are unchanged; no app process.
  `forager.db` on a scratch copy of the db and its wal: header `SQLite format 3`, `integrity_check` = ok, `user_version` = 16, every table's row count in
  `forager-db-verify.txt` (mushroom_log_entries 3, log_photos 3, log_entry_photos 1, tracks 1, track_points 23, waypoints 3, offline_regions 2, cartography_entries 7,
  cartography_entry_find_refs 2, ..._offline_region_refs 1, ..._photo_refs 1, ..._track_refs 1, ..._waypoint_refs 3, planned_trips 0, cached_searches 2).
  The 21 hashes are also equal, file for file, to Session 2's `s2-copy/device.sha256`, so the phone was left exactly as Session 2's restore put it. (My `forager-db-verify.txt`
  lists user tables by `sqlite_master`; it omits `sqlite_sequence`, which Session 2's file lists with 1 row. Nothing else differs.)

### Pre-registration (before the first launch of this session)

Sources read at this base: dispatch `prompts/preserved/2026-09-29-21.md` and amendment `-29.md` (read in full, on `origin/journal-redesign`); the inventory
(items 64-76, group 1h); the backup report's "Resumed" -137 and -153 sections (its final form). Code read by me at this base, in
`app/src/main/java/com/zynergylabs/forager/app/`: `ui/backup/BackupSection.kt` (whole file); by grep only: `ui/backup/BackupViewModel.kt:32-62`
(message strings), `ui/backup/RestoreLoadingPage.kt:49-160`, `data/backup/RoomJournalBackup.kt:234-297` (region re-id), `domain/ScheduledBackupNotice.kt:13-19`,
`data/backup/AndroidBackupNotifier.kt:22-101` and `data/backup/ScheduledBackup.kt:84` (`UNIQUE_WORK_NAME = "journal-backup"`),
`ui/availability/AvailabilityOfflineMapsUi.kt:575-588`. Where a pass condition rests on a line I have only grepped, it is the report's line, not my reading of the logic.

**Method.** Real `adb shell input` and real rotations only. System pickers (create file, choose folder, open file) are driven with `input`; the DocumentsUI's own
"Allow access to ...?" step of the folder picker is part of that picker (item 65's persisted permission), so I treat it as part of the flow under test. The
**notification permission** prompt (turning the schedule on with a folder chosen, `BackupSection.kt:88-93`; and starting a recording) is a system prompt over the app: rule 11
and amendment item 6 say stop and ask the owner to tap it. I will not tap it and will not `pm grant`. Files I create on the phone: a folder
`/sdcard/Documents/DEVICE-CHECK-2026-09-29/` and the backup zips in it; my paths are recorded and removed at the end.

**Plan of the round trip (state names):** S0 = the verified copy (owner's data only). Backup **A** is taken at S0. Then I add one find labelled
"DEVICE CHECK 2026-09-29 s3" (state S1) and take backup **B**. Then: Replace-restore of A (to S0'), Merge-restore of B, Merge-restore of A (the "second backup" is B; both
Merges are run), each read back from the database (`run-as` copy of `forager.db` + wal, scratch, integrity, counts). At the end the phone is returned to S0 (amendment 2 and 8: remove each
file on the device first, then push the copy; every hash again after a few seconds; files that did not exist in the copy, such as a new DataStore file or a photo, are removed).

| Item | Pass condition (source) | Prediction |
|---|---|---|
| 64 | A backup to a chosen folder writes `forager-backup-<date>.zip` and shows "Backup saved." (`BackupViewModel.kt:32`). A Replace restore of A returns the journal rows to S0's counts (mushroom_log_entries 3, log_photos 3, tracks 1, track_points 23, waypoints 3, cartography_entries 7, planned_trips 0) and the 3 photo files with the same sha256; a Merge restore of B adds the s3 find without duplicating the rest. `offline_regions` and its refs change ids by design (below). | passes on rows; the region ids differ from S0 |
| 65 | The Save picker offers `forager-backup-<date>.zip` (`backupFileName`, `BackupSchedule.kt:71`); the folder picker's choice shows in the section under "Backup folder" by its own name (`BackupSection.kt:127-135`) and survives a force-stop and relaunch (persisted permission, `takePersistableUriPermission` in `ContentResolverBackupFiles.kt`; unverified line); the open-file picker lists the zip. A **cloud provider:** I will look at whether the pickers list one, and will **not write a backup to one** (it would send the owner's journal, with its locations, to an external account; hard to reverse). Recorded as not run unless the owner rules otherwise. | pass except the cloud write, not run |
| 66 | The schedule (`UNIQUE_WORK_NAME = "journal-backup"`, `ScheduledBackup.kt:84`) run through `adb shell cmd jobscheduler run -f` (job id read from `dumpsys jobscheduler`) puts a `forager-backup-<date>.zip` in the folder. I do not change the clock and do not force Doze; "under Doze" is **not run** and said so. | file lands; Doze half not run |
| 67 | The Backup section in the Tools drawer (Tools, then Settings): title "Backup", "Back up now", "Automatic backup" with its switch, "How often" with Daily/Weekly/Monthly, "Backup folder" + "Choose folder", "Restore from backup" (`BackupSection.kt:96-160`), at 0, 90 and 270, each control's bounds from `uiautomator dump` inside the screen and not covered by another. | pass; the landscape scroll is my likeliest surprise (the report's "how far the scroll lands" is device-only) |
| 68 | Fresh install state: the switch is off and "Weekly" selected (`BackupSection.kt:97-115`; the report's test names). The DataStore file for the schedule does not exist in the copy, so this is the first read. | pass |
| 69 | After Replace of A, each restored region shows "Not downloaded" and a "Download again" button (`AvailabilityOfflineMapsUi.kt:579-588`). "Download again" runs against the real server (NET). It needs no Wi-Fi/data change. It starts a real tile download; I will size it first from the row's radius and zoom and the tile budget before tapping, and stop and ask if it looks large. | rows show; the download's size is unknown to me |
| 70 | Merge gives each incoming region a **new negative id** below the current minimum (`RoomJournalBackup.kt:290,297`); the refs (`mushroom_log_entries.offlineRegionId`, `cartography_entry_offline_region_refs`) follow. Replace also re-ids, counting down from -1 (`RoomJournalBackup.kt:236-244`). Read from `forager.db`. I predict Merge of a backup into a phone that already holds the same regions **adds them again** (the report's decision 2), so `offline_regions` grows. | Replace ids -1,-2; Merge adds more |
| 71 | Unreadable photos pause: "N photos couldn't be backed up." with Try again / Continue without file(s) / Cancel (`BackupViewModel.kt:50`, `BackupSection.kt:150-160`); Continue saves and says "Backup saved, but N photos couldn't be found and were left out." (`:41-42`); Cancel deletes the file this run created. I will cause it **only with a photo of my own**: a DEVICE CHECK find with a camera photo, then remove that one photo file through `run-as`. The owner's three photos are never touched. | pass |
| 72 | A failed write that deletes only its own file, with Try again / Cancel (`BackupViewModel.kt:62`). **Predicted not runnable:** it needs a forced write failure and there is no hook (Session 2 found none for item 57; adding one is the owner's call). I will look for a way to fail a write with only the phone's own controls, and if none, record it not runnable and add no hook. | not runnable |
| 73 | Planned trips round-trip (`JournalTables.kt:54`). The phone has 0 planned trips. I would create one named "DEVICE CHECK 2026-09-29 s3 trip" before backup B, restore, and read `planned_trips` from the db. If a trip cannot be created with real input, not reached. | pass if created |
| 74 | With a track recording, choosing a backup to restore shows "Stop recording before restoring a backup." and nothing is staged (`BackupViewModel.kt:37`, block near `:261`). Starting a recording asks for POST_NOTIFICATIONS (Session 1's finding): a system prompt, so I stop for the owner. Without an answer this is **not reached**. | depends on the owner |
| 75 | After a Replace/Merge commits: a full-screen page, the app icon pulsing, "Loading your restored journal…"; then the icon stops, "Your journal is restored." and "Done" at its centre; a tap on the icon (168 dp, description "Done", `RestoreLoadingPage.kt:53,130`) returns to Maps with a ~300 ms grow-and-fade; with `animator_duration_scale` 0 it goes straight to Maps (`RestoreLoadingPage.kt:96`). Captured as a screenshot series (the pulse and the fade are timing, so I judge from frames and say how many). I set the scale to 0 for the reduced-motion run and restore 1.0. | pass; frame capture may miss the 300 ms fade, which I will say |
| 76 | "Backups" channel exists with that user-visible name (`AndroidBackupNotifier.kt:97-101`, read from `dumpsys notification` or the app's notification settings); a scheduled run that meets an unreadable photo posts "Scheduled backup saved. 1 photo couldn't be backed up." (`ScheduledBackupNotice.kt:19`), and a tap opens Tools, then Settings at the Backup section. **Needs POST_NOTIFICATIONS granted, which needs the owner's tap on the prompt.** With it declined, the same text is kept and shown once as a snackbar at the next launch (the -153 in-app fallback). | not reachable without the owner; the decline path is reachable only if the owner taps Don't allow |
| -153: Replace re-ids | Replace of A leaves `offline_regions` ids negative (-1, -2), never the backup's original ids (`RoomJournalBackup.kt:238-244`). | pass |
| -153: "Replace the existing backup file?" | Picking an existing non-empty zip in the Save picker asks first, with Replace / Cancel (`BackupSection.kt:165-172`); Cancel writes nothing (sha256 of that zip unchanged); a new empty file is not asked about. | pass |

**Predictions, so a surprise is visible:** (P1) the two notification-permission items (66-notification half, 74, 76) cannot be finished without an owner tap; (P2) Replace of A returns the
journal tables to S0's counts and the photo files to S0's hashes, but **not** `offline_regions` ids; (P3) `mbgl-offline.db` is unchanged by a restore (regions are Room rows only, "Not downloaded"),
so any change to it comes from "Download again"; (P4) item 72 is not runnable; (P5) the return to S0 needs the remove-then-push order (Session 2's finding), and the WorkManager db will differ until it is restored.

**Declared limits:** the cloud provider write, "under Doze", the forced-failure half of item 72, and anything behind the notification prompt unless the owner taps it. Tablet-only halves
(a restore onto the tablet as a new phone, the wide layout's Backup section) are not runnable on the S22. Item 8's channel importance (`IMPORTANCE_DEFAULT`) is the coder's unruled choice per the report; I record what the device shows and give no verdict on the choice.

### Session 3: results so far (interim 1; items reachable without a system prompt)

Every verdict rests on the dumps (`s3-*.xml`), the PNGs, the db reads (`s3-dbread-*.txt`, scratch copies of `forager.db` + wal read through `run-as`) and the backup zips
pulled to `s3-files/`. Real input only. Files I made on the phone: `/sdcard/Documents/DEVICE-CHECK-2026-09-29/forager-backup-2026-09-29.zip` (A, taken at S0),
`forager-backup-B.zip` (B, after my find `DEVICE CHECK 2026-09-29 s3` with one camera photo and my trip `DEVICE CHECK 2026-09-29 s3 trip`), `forager-backup-C.zip` (C, after I removed my
one photo file through `run-as`); plus `/sdcard/s3d.xml` (my dump scratch file). All to be deleted at the end.

| Item | Verdict | Reading |
|---|---|---|
| 68 | pass | First open of the Backup section: switch off, Weekly selected (`s3-a3.png`). |
| 67 | pass at 0, 90, 270 (see note) | The section is in Tools, then Settings, scrolled to. Controls stack without overlap and inside the screen at 0 (`s3-a3.xml`), 90 (`s3-s1.xml`, `s3-s2.xml`) and 270 (`s3-t3.xml`); each needs a scroll in landscape. |
| 65 | pass, cloud write not run | The Save picker's name field offered `forager-backup-2026-09-29.zip` (`s3-b1.xml`). The picker's roots list "Downloads", the phone and **eight Google Drive accounts** (`s3-b3.png`); I did **not** save to any (it would upload the owner's journal). The open-file picker listed all three zips (`s3-i1.xml`). Folder picker (`Choose folder`, persisted permission): **not yet run**, see below. |
| 64 (backup half) | pass | "Backup saved." (`s3-c1.xml`); A is 5,520,214 bytes; its `manifest.json` and `forager.db` (counts equal to the copy's: entries 3, log_photos 3, tracks 1, points 23, waypoints 3, cartography 7, trips 0) and 3 photos whose sha256 equal the phone's (`s3-files/A.zip`). |
| -153: "Replace the existing backup file?" | pass | Choosing the existing zip in the Save picker first raised DocumentsUI's own "Replace file?" (`s3-f4.png`); Replace on that raised the app's "Replace the existing backup file?" with Replace / Cancel (`s3-f5.png`); Cancel: the zip's sha256 is unchanged (`eebf6c12...`), no new file, back in the section. Replace on it was **not** run (it would have overwritten A). A new name is not asked about (B, C). |
| 71 | pass | With my one photo file removed: "1 photo couldn't be backed up." with Try again / Continue without file(s) / Cancel (`s3-h1.xml`). Try again with the file still missing paused again (`s3-h2.xml`). Cancel deleted the file this run created (C.zip gone, A and B intact by `ls`). Continue: "Backup saved, but 1 photo couldn't be found and was left out." (`s3-h4.xml`), and C.zip holds `forager.db` + the 3 photos, not mine. |
| 64 (restore half), Replace | pass, with an observation | Replace of A: "Restore complete." shown in the section after Done. Journal tables equal S0's counts (`s3-dbread-replaceA.txt`: entries 3, log_photos 3, tracks 1, points 23, waypoints 3, cartography 7, trips 0; integrity ok, user_version 16); my find, photo and trip are gone; the 3 photo files' sha256 equal the copy's. **Observation:** `offline_regions` = 4 rows, the restored `-1`, `-2` **and the original 1, 2**. Restoring onto the phone that made the backup gives the regions twice (the originals are still real MapLibre regions; the restored rows are "Not downloaded"). Nothing in the report predicted the originals staying. |
| -153: Replace re-ids regions | pass | The restored rows are `-1` and `-2`; the entry's region ref follows (`cartography_entry_offline_region_refs` region id 1 to -1). |
| 69 | pass | Journal > Records > Offline maps chip (4): two rows labelled "Not downloaded" with "Download again" (`s3-l6` dump). "Download again" on "DEVICE CHECK" (1 mi, 17 tiles, 0.3 MB by the row's own text): "17 tiles, 0.3 MB — downloaded just now", tile budget 261 to 278 of 6000, against the real server (`s3-m2.xml`); the old `-1` row is replaced by id 3 and the entry's ref follows to 3 (`s3-dbread-afterDL.txt`). Not tapped: the "Download again" on the 5 mi region. |
| 70 | pass | Merge of B: incoming regions took `-3` and `-4`, below the phone's lowest (`-2`); the existing ones are untouched. Merging A afterwards added `-5` and `-6`. Each Merge adds the backup's regions again (the report's decision 2); after two Merges the phone has 8 region rows for 2 real regions. The one entry ref stayed on 3 (its entry already existed, so it was skipped). |
| 73 | pass | B carries `planned_trips` with my trip (`s3-files/bx/forager.db`); Merge of B into the phone with 0 trips gives `planned_trips` = 1 with that id and name (`s3-dbread-mergeB.txt`). The Replace of A had removed the trip (Replace deletes the journal data). |
| 64 (Merge half) | pass | Merge of B: mushroom_log_entries 3 to 4, log_photos 3 to 4, log_entry_photos 1 to 2, trips 0 to 1; the restored photo `7bdef055...` on the phone has the sha256 of the copy in B (`78cd1e7f...`); nothing else duplicated (tracks 1, points 23, waypoints 3, cartography 7 unchanged). Merge of A into that: the same counts, only regions +2. |
| 75 | partly | Replace and both Merges each reached the Done page: the icon, "Done" over it, "Your journal is restored." (`s3-replace-contact.png`). A tap on the icon (168 dp) went to Maps (`s3-done-contact.png`). **Not captured:** the pulse with "Loading your restored journal…" (the reload finished inside the first ~0.3 s frame, I caught none) and the grow-and-fade (my frames are 0.3 to 1 s apart; I have no video decoder here). Reduced motion (`animator_duration_scale` 0, restored to 1.0 and read back): the first frame after the tap is already Maps (`s3-reduced-contact.png`). |
| Observation (not a gate) | | After the Replace of A the Journal's Entries/Finds view was still showing the report of my find (`Find on 2026-09-29`, "DEVICE CHECK 2026-09-29 s3"), which the Replace had just deleted (`s3-l3.xml`, list showed the find's report over a database that no longer holds it). This is the case the report names as not reloaded ("an entry or find open in an editor"), seen on the device. |

**Not yet run:** the folder picker and the schedule (66, 76, and the "Backups" channel), the recording block (74), the forced write failure (72), and the return to S0.

### Session 3: the schedule, notifications, the recording block, the restore read-back, and final results

**Owner taps.** Three system prompts were tapped by the owner, never by me (rule 11). I asked with the question tool each time and read `dumpsys package` afterwards to confirm the answer took effect:
(1) the notification prompt raised by turning Automatic backup on with a folder chosen: **Allow** (`granted=true`, `USER_SET`); (2) after I revoked it and switched the schedule off and on, the prompt again: the owner tapped **Allow** (answered "Tapped Allow instead"),
so the declined path did not run; (3) I asked once more: **Don't allow** (`granted=false`, `USER_SET|USER_FIXED`); (4) starting a recording raised it again: **Don't allow**. The DocumentsUI "Allow Forager to access folder?" dialog of the folder picker I did tap myself (twice), reading it as part of the picker under test; see Decisions.

| Item | Verdict | Reading |
|---|---|---|
| 65 (folder picker) | pass | "Choose folder" opened the tree picker in my folder, "USE THIS FOLDER", then DocumentsUI's "Allow access for ..." dialog (`s3-v2.xml`); the section then shows the folder as `Documents/DEVICE-CHECK-2026-09-29` (`s3-v3.xml`). It survives a force-stop and relaunch (`s3-am0.xml`), and scheduled runs after later process restarts still wrote into it. Cloud provider: listed, not written to (see 65 above). |
| 66 | pass for the file, **and one finding about the forcing method** | Turning Automatic backup on wrote `forager-backup-2026-09-29 (1).zip` (7,382,294 bytes, at 03:43; the phone then held my find and photo, so the zip has 6 entries). `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler <pkg> 0` (the namespace flag is needed; without it, "Could not find job 0") **did not run the worker**: WorkManager logged "Delaying execution for ...ScheduledBackupWorker because it is being executed before schedule ... Status is ENQUEUED; not doing any work and rescheduling" (03:57:06, `logcat`), and the job was rescheduled under Job ID 1. What did run it, by real input: switching the switch off and on again (the periodic work is re-enqueued and starts at once: a new `(2).zip` at 03:57), and the notification's Try again. Changing the frequency (Daily, Weekly) only rescheduled it, with no run. "Under Doze" not run (I did not force Doze). |
| 76 | pass, cold-start tap not run | With the permission granted, the run that met my removed photo posted, in channel `backups`, id 1102: title "Scheduled backup saved. 1 photo couldn't be backed up." (`dumpsys notification`, `s3-z1.xml`), with a content intent. From Home, tapping it opened the app with the Tools drawer on Settings and the Backup section in view (`s3-z2.xml`). A tap from a cold start (the app not running) was not run. The zip of that run holds `forager.db` + the 3 owner photos (5,520,524 bytes). |
| -153: the "Backups" channel | pass | `NotificationChannel{mId='backups', mName=Backups, mImportance=3}` in `dumpsys notification` (importance 3 is `IMPORTANCE_DEFAULT`, the coder's unruled choice; no verdict on it). |
| A failed scheduled run | pass | I deleted my folder from `/sdcard` (my own), then switched the schedule off and on: the run failed at `ContentResolverBackupFiles.createInFolder` (logcat, `ScheduledBackupWorker`), and a notification "Scheduled backup didn't finish" with one action appeared (`dumpsys`: `actions=1`; `s3-ab2.xml` shows the button "Try again"). I recreated the folder and tapped Try again: a `journal-backup-retry` job ran (`Worker result SUCCESS`), a zip landed, and the "didn't finish" notification was gone. |
| -153: the declined path, the in-app fallback | pass | With the permission not granted, the run's notice "Scheduled backup saved. 1 photo couldn't be backed up." was shown as text in the app at the next launch (`s3-ag1.xml`, at about y 1772-1886, no action button), and not at the launch after (`s3-ag2.xml`: 0 matches). No notification was posted in between (`dumpsys` listed none). |
| 74 | pass | A recording running (the record button then reads "Stop recording track"), Tools > Settings > "Restore from backup": no picker, and "Stop recording before restoring a backup." shown (`s3-ak1.xml`); nothing was staged. Recording stopped afterwards. |
| 72 | **not runnable** | A write that fails after the file exists needs a forced failure; I know of none with the phone's own controls and there is no hook (Session 2 found none for item 57; adding one is the owner's call). I added none. What I did see: **Cancel** on the unreadable-photos pause deletes the file that run created (a real SAF delete, seen by `ls`), which is the delete half, not the failed-write dialog or its Try again. |
| 75 | as above | Unchanged: Done page and the tap to Maps seen; the pulse and the fade not captured; reduced motion goes straight to Maps. |

**Restore read-back (the phone returned to the verified copy, as the amendment allows).** Reason: my finds, trip, photos, the recording's track, the restored region rows, the schedule's DataStore file and WorkManager's rows are in the phone. After force-stop I removed `files/datastore/backup_schedule_preferences.preferences_pb` (the one file that did not exist in the copy), then
removed each copied file on the device, then pushed the copy through `run-as` (`adb exec-in`). **The first pass left the files unequal to the copy**: 11 of 21 hashes differed (the db wal and shm, both `mbgl-offline.db`, `profileInstalled`, `ActivityThread.IDS.xml`, the three WorkManager files),
with the two `.db` files the same size as the copy's. The cause is not determined; the files it hit are the ones a running app writes, but `pidof` was empty each time I read it. That pass wrote each file with `adb exec-in ... 2>/dev/null || adb shell ...`, so if `exec-in` failed for a file its fallback used `adb shell` with stdin (a pty), which I did not check. The second pass (force-stop, remove each file again, push each by `exec-in` only) gave
**21 of 21 sha256 equal to the copy** (`s3-post-restore.sha256` against `s3-copy/device.sha256`) and still equal 8 s later (`s3-post-restore-2.sha256`), no app process. `forager.db` (scratch copy of the db and wal): `integrity_check` ok, `user_version` 16, every table's row count equal to `s3-copy/forager-db-verify.txt` (`s3-final-counts.txt`; `diff` empty). WorkManager's db is restored, and `dumpsys jobscheduler` shows no Forager job.

**Residue I cannot undo from the copy:** (a) the notification channel `backups` now exists in the system's notification settings (a channel created by the app is system state, not app data); (b) the system's persisted tree grant to my folder (`Documents/DEVICE-CHECK-2026-09-29`), now a deleted folder, may remain in the system's URI-grant list; I did not look. Neither is in the app's data directory.

**Files I made on the phone, all deleted:** `/sdcard/Documents/DEVICE-CHECK-2026-09-29/` with `forager-backup-2026-09-29.zip` (5,520,214 bytes, A; the recreated folder's last zip, 5,520,523 bytes, replaced it), `forager-backup-B.zip` (7,382,221), `forager-backup-C.zip` (5,520,453), `forager-backup-2026-09-29 (1).zip`, `(2).zip` (the scheduled runs); later `(1).zip`, `(2).zip` (5,520,524 each) in the recreated folder; and `/sdcard/s3d.xml`. `ls` afterwards: none present. No Gallery rows were made (no item of mine saves to it). Host copies of the zips I pulled are in `s3-files/` (A, B, C, `sched1`, `sched2`, `retry`), for the planner to read; they hold the phone's own data and no other person's.

**Settings read-back:** `accelerometer_rotation` 0, `user_rotation` 0 (both read at the end; `user_rotation` was set to 1 and 3 for item 67 and to 1 by the camera at the find's photo, each set back), `font_scale` 1.0, window/transition/animator scales 1.0/1.0/1.0 (animator scale was set to 0 for the reduced-motion check and read back), `wm size` 1080x2316, uimode night `yes`,
versionName `1.0.1685+g85a41257`, `firstInstallTime` 2026-09-22 11:15:05, POST_NOTIFICATIONS `granted=false, flags=[USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]`, the starting flags (I cleared the `USER_SET`/`USER_FIXED` that the owner's denials set, with `pm clear-permission-flags`). Crash buffer at the end: empty. `df -m /`: 6588 MB available at the end (977 MB at the start).

**Decisions I made:**
1. **Tapped DocumentsUI's "Allow Forager to access folder?" dialog myself** (twice), reading it as part of the folder picker under test (item 65). Rule 11 names system prompts over the app and gives the notification permission as its example; this is DocumentsUI's own step inside the picker the item exercises. Pre-registered before I did it, but it is a reading of the rule, not a ruling.
2. **Did not write any backup to a cloud provider** (Drive): it sends the owner's journal (with locations) to an external account.
3. **Asked the owner to tap the notification prompt** rather than `pm grant`; and used `pm revoke` and `pm clear-permission-flags` only to put the permission back to its starting state (amendment 6).
4. **Used the off/on switch and Try again to run the schedule**, since `cmd jobscheduler run` did not start the worker (above). Neither changes the clock.
5. **Deleted and recreated my own backup folder** to force a failed scheduled run: my folder only, under `/sdcard/Documents`, none of the owner's files.
6. **Removed my own photo's file through `run-as`** (twice) to cause the unreadable-photo case; the owner's three photos were never touched (hashes equal the copy's at the end).
7. **Reran the restore after the first pass failed** to reach 21 of 21 (amendment 8), and reported the failed first pass.

**Flags outside scope:**
- **Restoring onto the phone that made the backup shows every region twice** (the real MapLibre regions 1, 2 and the restored "Not downloaded" copies), and **each Merge adds them again** (8 rows for 2 regions after Replace + two Merges). The report names decision 2 for Merge; the Replace case (the original rows staying) is not in it.
- **After a Replace, an open find's report stayed on screen** for a find the Replace had deleted (item 75's observation above).
- **`adb shell cmd jobscheduler run` cannot force this app's periodic backup** (WorkManager refuses before its schedule), so a dispatch that names it as the forcing route needs the off/on toggle or Try again instead.
- **Turning the schedule on runs a backup at once** (WorkManager's periodic first run), before any period has elapsed, and a second one each time the switch is toggled. That may be intended; the report does not say.
- **A scheduled run with nothing wrong writes a new `(n).zip` beside the old ones** each time, as the report's "one file per run, nothing deleted" says; a folder chosen for a daily schedule fills. Nothing prunes it.
- **The notification prompt is raised again by each off/on of the switch while the permission is denied**, and by starting a recording; two denials fixed the permission (`USER_FIXED`) until I cleared the flags.
- **Host disk fell to 977 MB** during the session and rose to 6588 MB by the end: something else on the machine moved about 5.6 GB.
- **Not run, declared:** the pulse frames and the grow-and-fade (no video decoder here); the cold-start notification tap; "under Doze"; the forced write failure (72); the cloud write; Replace-over-existing (I only cancelled it); item 69's second "Download again" (the 5 mi region); tablet-only halves (a restore onto the tablet as the new phone; the wide layout's Backup section).
