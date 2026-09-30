# S22 session check (dispatch 2026-09-28-311), partial run record

Build under test read back from the phone: versionName `1.0.2160+gc849ae99`, versionCode 2160, serial R5CT321008R. No build, no install, no Gradle. Evidence folder: `/home/zynergy-labs/Zynergy/device-evidence/2026-09-30-session-check` (outside the repo). **Status: IN PROGRESS, this is a mid-run push.**

## Copy (before anything)
`before-copy/`: 22 files via `run-as cat`, app force-stopped (no pid). Device sha256 equal to the pulled files for 22 of 22 (`device.sha256`, `local.sha256`), device hashes re-read after the pull unchanged (`device-after.sha256`). `forager.db` on a scratch copy of db and wal: integrity ok, user_version 17; counts: cached_searches 2, cartography_entries 7, find_refs 2, offline_region_refs 1, photo_refs 1, track_paths 0, track_refs 1, waypoint_refs 3, log_entry_photos 1, log_photos 3, mushroom_log_entries 3, offline_regions 2, planned_trips 1, track_points 23, tracks 1, waypoints 3, sqlite_sequence 1, room_master_table 1, android_metadata 1.

## Settings, start
accelerometer_rotation 0, user_rotation 0, animator/transition/window scale 1.0/1.0/1.0, `cmd uimode night` yes, font_scale 1.0, basemap Topographical (Layers sheet), fullscreen off.

## Verdicts so far
| # | Verdict | Evidence | What in it shows it |
|---|---|---|---|
| 1 | PASS for legs; other layers not determinable | c1-fan.png, c1-fan-hub-crop.png | Legs draw over the puck's disc. At this zoom only the legs reach the puck; no circle, dot or icon overlaps it, so those layers are not determinable here. |
| 2 | PASS on Topographical at start | s0-launch.png, s0-stack-crop.png | Puck disc and heading wedge draw over the stack's camera and mushroom glyphs and the track. |
| 3 | **FAIL** (basemap swap), PASS (leave and return) | c3-street-folded-crop.png, c3-topo-back-folded-crop.png, c3-after-swap-and-tab-crop.png, c3-fan-after-tab-crop.png, c3-street-fan-crop.png | After Topographical to Street, fan folded: the camera glyph draws over the puck (only the heading wedge shows); unchanged 5 s later; still so after swapping back to Topographical. A tab leave and return restores the puck above the markers. A fan opened after the swap (Street) and after the tab return still draws its legs over the puck. |
| 4 | accuracy circle and heading wedge present; tracking not determinable | s0-launch.png | Dashed accuracy circle and blue wedge visible. The phone was stationary and I cannot move it or mock a location, so whether the puck tracks movement and turns is not determinable. |
| 5 | **FAIL** (glyph order pops at fold end and open start) | c5-fold-day.mp4, c5-fold-last-frames.png, c5-open-frames.png | Animator scale 5. Fold frames 610 to 614: the front glyph of the settled stack changes from the mushroom (over the camera) to the camera. Open frames 134 to 142: camera in front, then mushroom over camera. Positions barely move; the z-order changes in one frame at both ends. |
| 6 | PASS for grow and shrink from nothing; mid-fold look recorded | c5-fold-frames.png, c5-open-frames.png | Circles are absent at the first open frames, appear small and grow (frames 174 on), and shrink to nothing by frame 542 on the fold. **Recorded, not judged:** mid-fold the circle is smaller than the glyph and sits off its centre (frames 518 to 534). |
| 8 | PASS | c8-1-fan.png .. c8-4-back2.png | Fan open, Tools drawer open; Back 1: drawer closed, fan still open; Back 2: fan folded. |
| 9 | PASS | c9-1-fs.png .. c9-3-back2.png | Fan, fullscreen; Back 1: left fullscreen, fan open; Back 2: fan folded. |
| 10 | PASS x3 | c10a-*, c10b-*, c10c-* | "+" menu, Log-a-find pin picker (+ then Find), and search "Set on map" picker: one Back closed each and the fan stayed open. |
| 11 | recorded, unchanged under Option A | c11-1-back1.png, c11-2-back2.png, c11-3-back3.png | Fan and search dropdown with keyboard: Back 1 hid the keyboard, Back 2 folded the fan (dropdown still open), Back 3 closed the dropdown. |

## Incidents
- ~14:02: a tap at (540,2250), aimed at the Layers sheet's "Close sheet" node (bounds `[0,2181][1080,2316]`), landed on the system navigation bar's Home button. The app went to the launcher. **No system prompt was on screen**; I relaunched. The launcher screenshot showed the owner's wallpaper photo, so I deleted it. The node's bounds overlap the nav bar: I now close that sheet by dragging its handle down.
- A first attempt to decode the fold video held every frame in memory and was OOM-killed (exit 137); I re-ran it streaming. A container restart also occurred; the phone state was checked afterwards (animator scale still 5.0, Forager focused).

## Open settings at this push
animator_duration_scale 5.0 (start 1.0), to be restored. Basemap restored to Topographical.
