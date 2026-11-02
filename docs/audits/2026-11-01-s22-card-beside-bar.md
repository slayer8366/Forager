# S22 build with #449's card beside the bar, and its phone checks

Dispatch `prompts/preserved/2026-11-01-03-s22-build-card-beside-bar.md` (RECORD -1935), origin/records-after-173. Run by Hazel, 2026-11-01, 19:59 to 21:20 PST. Phone-only build; this branch is never merged. Evidence is in `/home/zynergy-labs/s22-2026-11-01/card-beside-bar/`, off git. Sizes are read from screenshots and UI dumps of the phone. 3.75 px per dp.

## Merges (tools/s22-combined, from 3709b6a15, worktree clean)
- origin/route-plot-ui fb878627d, as written: c54820f28.
- origin/pre-main 40e9e4d3b (the dispatch wrote 08c1c3363; it had moved): 94f3b0dd2.
- No conflicts. `ForagerDatabase.kt:214` reads `version = 23`, the same as the phone.

## CI run 41385959991 (workflow_dispatch, 94f3b0dd2)
- 0 compile error lines. Debug build, release compile, lint, tile Worker and route cutter all passed. "No debug-only sources" was skipped, as it is on this branch. No job re-run.
- From the JUnit XML (892 files, against the log's "6282 tests completed, 3 failed, 24 skipped"): testDebugUnitTest 6282 tests, 3 failures, 0 errors, 24 skipped; testDebugUnitTestOlderSdks 24 tests, 0 failures.
- The three failures are the allowed ones, at StreetStyleTest.kt:77, OwnStreetStyleSourceTest.kt:78 and NightExceptionsV4Test.kt:219. The run reads red only for them.

## APK and install
- `app-debug.apk` sha256 d7c4519de1ac114c939cf498733221b5efdf4f88e2d126f2d97bab0e1fe27392, 1.0.4364+g94f3b0dd, versionCode 4364 (above the installed 4324).
- V2 signer cb2f6da5...94f1626, equal to the installed base.apk.
- Routing key: masked in the CI log, field `ROUTING_APP_KEY` present in the dex. The value was not printed, and this run did not fetch a region, so the key's value was not exercised.
- Baseline: 4324, schema 23 (same as the build), 5 tracks, 273 track points, 11 waypoints, 0 planned trips, integrity ok. Raw backup and table hashes in `baseline/`.
- In-app backup: `forager-backup-2026-11-01-a2.zip`, a new name, older files untouched. sha256 5b585b01c6cf1b15..., manifest 4324 and schema 23, pulled to `in-app-backup/`. Not restore-tested.
- Install at 20:18 PST with `install -r`. After launch: every forager.db and fungi_index.db table identical, DataStore and shared_prefs byte-identical, route files byte-identical. Map cache `resources`, `tiles` and `sqlite_sequence` moved (tiles viewed, 1828 to 1837). ACTIVITY_RECOGNITION granted, user 95 still false and left alone. Logger pref on.
- The crash buffer holds 17 lines, all from another app's process at 05:00, none from Forager. Not cleared.

## 1. The bar never moves: pass
Two independent reads at each state, against a shot of the same bar with no card:
- the UI dump bounds of all 8 bar and pill nodes (1 node when minimised);
- the pixels of the bar's own crop, changed-pixel count at a small threshold.

**Every state below: bounds equal, 0 pixels changed.** That is a position check, not a gap: a moved bar changes those pixels.

| Case | 1 stop | 3 stops | 5 stops | 6 stops |
|---|---|---|---|---|
| Portrait, bar right | peek | peek, held, expanded | same | same |
| Portrait, bar left | peek | peek, expanded | peek, held, expanded | same |
| Portrait, bar dragged up 300 px (left side) | peek, held | peek, expanded | peek, held, expanded | same |
| Portrait, bar minimised | peek, held | peek, expanded | peek, held, expanded | same |
| Landscape, L at left | peek, held | peek, held, expanded | same | same |
| Landscape, L at right | peek, held | peek, held, expanded | same | same |
| Landscape, minimised | peek | peek | peek | peek, held, expanded |

- During the width ease too: frames in the ease read 0 pixels changed.
- At 1 stop there are no rows, so "expanded" looks the same as peek.
- **Not run:** a held drag at 1 stop with the bar right in portrait, and a held drag from peek at 3 stops in the left, dragged and minimised portrait cases: the card was already expanded when those shots were taken, so a hold there was not a drag from peek. They are left out of the table. Held drags ran at 5 and 6 stops in every case.
- **A dragged bar in landscape does not exist:** the L fills the height, and a vertical drag left it where it was (`qD`).
- The left, dragged and landscape-right comparisons were each confirmed against a no-card shot. After all of it the bar's bounds and pixels equal the first baseline, and `map_preferences` is byte-identical.
- **Note:** the recording pill's dump bounds read 1 px lower ([130,1180][310,1360]) at some moments while recording, in landscape, with and without navigation. It did not follow the card (the plain no-recording shots are all equal). Cause not established.

## 2. Nothing is covered: pass
Real touches by coordinate, five spread across each control unless stated, card up.

| Control | Landscape, peek | Landscape, expanded | Portrait, peek | Portrait, expanded |
|---|---|---|---|---|
| Layers | 5/5 | 5/5 | 5/5 | 5/5 |
| + | 5/5 | 5/5 | 5/5 | 5/5 |
| Save | 5/5 | 5/5 | 5/5 | 5/5 |
| Record | 2 touches | 5 points, 3 starts and stops | not separately | 6 touches |
| Return, while recording | 2 touches | 4 points | not separately | 2 touches |
| Locate | not separately | 5/5 | not separately | 5 points |

- The 39 px strip below the old card's place: (288,1338) and (152,1338) stopped the recording, and (400,1338) started Return. These were the points the old card's strip left untested.
- The peek touches at (250,1250), (400,1250) and (170,1250) that hit Undo or nothing on 4324 now reach record and Return.
- **Weaker read, portrait locate.** The button cycles the camera, so "the map came back to the same pixels" did not hold as it did in landscape. Each of the five touches changed the map, which is what was read. A first touch-marker on Layers was also unsound: the marker word "Trail" is in the credit line, so it always matched. In landscape the plot surviving each Back still shows the sheet opened (Back would have cancelled it), and portrait was re-run on the marker "Topo".
- **Excluded points:** the bar's side handle lies over the right part of locate and the top of Layers in portrait, and over the left of locate in landscape. A touch at (1388,1505) minimised the bar instead of opening Layers. That is the handle's own place, not the card's, and those points were left out of the counts. It lost one plot; the stray touches after it landed on the map, changed nothing stored, and the portrait run was redone.
- **Not card related, but it does cover the pill:** "Your phone is silenced..." shows for several seconds after starting a recording, across the bottom of the screen, over the pill and the card's lower rows. The pill's nodes leave the dump while it shows. One start at the record button's centre in landscape was not confirmed in the first sequence, before this was understood; later centre starts were.

## 3. The card: pass
- **Portrait width and side.** 312 dp at most: x 30 to 1199 with the bar right, 240 to 1409 with the bar left. Width follows its lines (229 dp and 312 dp seen on the same stops as the names changed).
- **Sliding in:** frames show it entering from the left with the bar right (`iR-*`), from the right with the bar left (`iL-*`). Slide-out frames `oR-*`.
- **Shifting over:** with 6 stops up, the bar crossing moved the card to the far side, frames `XL-*`. It moves after the bar is released, not during the drag.
- **Landscape.** 278.7 dp wide (x 1563 to 2607 with the L left, 100 to 1144 with it right), which is the strip's width, reaching the bottom edge, in the column away from the L. Tops read 960 (1 stop), 1035 (peek), 703 (3 expanded), 371 (5 and 6 expanded). The strip's bottom is about 245, so the card stays below it.
- **Rows scroll** at 6 stops in landscape: swiped, "TEST long ... to TEST C" came into view, with the total line and Save in place.
- With the L at right, the credit line and "i" rise above the card (`R6p`).

## 4. Navigating, landscape: pass, one finding
Navigation started to TEST A through its bubble, with no GPS fix, so "No target" and no live guidance: the check is layout only. Also reached through Return while recording, with "No origin waypoint for this track".
- The card rests on the navigation card: same x, its lower edge tucked behind, top of the navigation card about y 1212, Undo, Cancel and Save at y 1085 to 1160 above it.
- Save 5/5 at peek and 5/5 expanded, each opening the Save route dialog.
- Long name (54 characters; see 6): expanded, three lines whole, elevation and total shown, Save 5/5. Six stops with the navigation card up were seen while recording (`r3`, `r5`): the rows take less height and scroll, with the total line and Save in place. Save was not touched in that state.
- Ended with the navigation card's X and the pill. No navigation left.
- **Finding:** expanded while navigating, the "Re-center" chip rises with the card's height and lands on the search bar, over the end of "Search a location" (`N3`, chip about x 1150 to 1560, y 115 to 285). At peek it sits clear on the map. The card is in the other column, so it has no need to rise.

## 5. The width motion, for the owner to judge
No video. Frames about 200 ms apart, real route on stored data.

**First leg, frame by frame (`f2-*`):** 312, 312, 303.5, 227.2, 213.1, 212.0, 215.5, 224.0, 226.7, settling at 226.9 dp, about 2 s after the tap. **It goes too far and comes back:** the card eases to about 212 dp while the leg is in flight, then widens about 15 dp when the leg lands. The owner should look at this one. The dispatch expected about 223 dp; here 226.9 dp, from these stops' lines.

**A wider leg (`f3-*`):** 226.9, 226.9, 235.5, 282.7, 309.1, 312, 312, flat from about 1.3 s.

**Same with a straight leg (`w6-*`):** 229.1 to 249.9, 295.5, 309.3, 312.

Narrowing after Undo (`e1-*`): 311.5, 273.1, 235.5, 227.2, 226.9.

**Touches inside the ease:**
- Undo: yes. Its button does not move (anchored side). A second Undo in the ease took effect, 3 stops to 1 (`e3-*`).
- Save: **not hit in three tries** (x 1046, 930, 840), during a narrowing ease of about 600 ms. No dialog. Save travels about 320 px in that time and adb's tap timing is coarse, so this cannot separate a missed aim from a blocked touch. Nothing suggests a block: at rest Save is 5/5.

## 6. Long names: pass
- 44 characters: "TEST long stop name forty four characters ok". Whole, on two lines, portrait (`s6e`) and landscape (`Q5e`), rows scrolling in landscape. In portrait 5 legs fitted without scrolling, so portrait scrolling was not seen.
- A 54-character name, made by a slip (see Data): whole on 2 lines in portrait, 3 in landscape.

## 7. Font scale 2.0, portrait: pass
- Set and read back 2.0, restored and read back 1.0.
- "3 Waypoints routed" whole on two lines, peek and expanded, the distance at the right, Undo, Cancel and Save on one line, the bar's bounds equal to baseline (`F2p`, `F2b`).
- At 2.0 the card sits over the larger credit strip; the buttons read over it.

## Observations
- Most stops were outside the downloaded areas, so those legs are straight, logged as NO_REGION_COVERS, with the "No trail data" line. That is the app working. The legs near TEST A were real routes with elevation lines.
- Elevation lines read like "-4 ft (↑ 0 ↓ 0)", the same shape as F-1 in the 10-31 audit.

## Data
- **Created and deleted:** waypoints TEST A to F, TEST N, a TEST long stop, and one more named "TEST long stop name forty four characters okWaypoint 9": clearing the name field failed and the text was added to it. Nine throwaway recordings, 0 points each, no Start or End waypoints. Each deleted by swipe in Journal > Records, name or date read first.
- No Saves. Rotation was changed and restored, font scale too. No clock change, no region download, no video.
- **After, read from the phone:** 11 waypoints, none TEST; 5 tracks; 273 points; 0 planned trips.
- **Hashes:** `after/table-hashes.txt` against `after-install/`: every forager.db and fungi_index.db table equal, including `sqlite_sequence`. DataStore, shared_prefs and route files equal to `baseline/`. Map cache `tiles` 1837 to 1841, `resources` and `sqlite_sequence` moved, which is tiles viewed.
- **Owner's data:** Waypoint 1 untouched. The map's view is near the location at a different zoom than found; `map_preferences` is unchanged.

## End state
1.0.4364+g94f3b0dd, Maps tab in front, not recording, no navigation, no plot, no Forager service, rotation 1 and 0, font scale 1.0, auto time 1 (never changed), phone and laptop clocks equal to the second. Bar right at its old height, not minimised. Temporary frames removed from the phone. Nothing cleared or reset. No Forager crash in the crash buffer.

## Calls beyond the dispatch
- Pixel comparison of the bar's crop, added to the bounds.
- A navigation started through Return while recording, reached by the touch check, and ended at once.
- The TEST N waypoint and a stop made with a slip in its name, both deleted.
- Raw `screencap` frames on the phone, in /data/local/tmp, deleted after each burst. This is not video.
