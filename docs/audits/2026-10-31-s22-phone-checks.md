# S22 phone checks for #449, #450, the elevation lines and the credits (partial, handed off)

Dispatch `prompts/preserved/2026-10-31-01-s22-phone-checks.md` (origin/records-after-173, RECORD -1900). Run by Hazel
(Opus), 2026-10-31, 15:48 to 16:40 PDT, on the S22 (R5CT321008R, SM-S908U, Android 16), app 1.0.4294+g8e70a28b
(versionCode 4294, tools/s22-combined 8e70a28ba). Evidence is in `/home/zynergy-labs/s22-2026-10-31/checks/` (off git):
`shots/`, `ui/` (uiautomator dumps), `logs/`, `baseline/` and `after/` (raw `run-as tar` plus table and file hashes).
Nothing in this document carries coordinates.

**This run is partial.** I reached the dispatch's 200k-token handoff point with the #449 checks and the credits
mostly done and #450 only begun. What did not run is listed under "Not run" with the reason, and a fresh helper can take it
from there. Read the build-and-setting assumptions first: the phone was indoors on the desk the whole time, **with no GPS fix**
(the strip read "Approximate location, finding GPS…" throughout; network fixes only, accuracy 100 m). Any check that needs a
fix, movement or a real walk could not be shown here.

## Verdict table

Sorrel's #449 list is `docs/reviews/2026-10-30-sorrel-pr449.md` (origin/sorrel-pr449), checks 1-14. Sorrel's #450 list is
`docs/reviews/2026-10-30-sorrel-pr450.md` (origin/sorrel-pr450), checks 1-9.

| # | Check | Verdict | Evidence |
|---|---|---|---|
| 4 | Credits: Heights between Maps and Weather | **Pass** | `ui/05-credits.xml`, `ui/06-credits-heights.xml`: Maps, then Heights (NRCan OGL sentence, Copernicus notice and liability sentence, USGS 3DEP row, "Route heights in Forager are averaged from this data."), then Weather |
| 4 | Credits: the licence link opens | **Pass** | the Heights "licence ›" sent VIEW `https://open.canada.ca/en/open-government-licence-canada` (`dumpsys activity`); it opened in Chrome "Just once" (`shots/09-licence-page.png`); Back returned to the credits |
| 4 | Credits: long Copernicus row readable at default font | **Pass** | font_scale 1.0; the row wraps over 6 lines inside the 1290 px column, no clipping (`shots/06-credits-heights.png`) |
| 1 | Owner layout: "1 Waypoint selected, tap another to route" | **Pass** | `ui/31-plot-1stop.xml`; Undo, Cancel, Save on the card from the first tap |
| 1 | Owner layout: "N Waypoints selected" with total | **Pass** | "2 … 0.4 mi", "3 … 0.8 mi", "4 … 1.3 mi", "5 … 3.2 mi" (`ui/33`, `34`, `35`, `43`) |
| 1 | Owner layout: legs as "A to B" | **Pass** | "Waypoint 1 to TEST A 0.4 mi", "TEST A to TEST B", "TEST B to TEST C", "TEST C to TEST D 1.9 mi" (`ui/44-5stops-expanded.xml`) |
| 1 | Owner layout: "N Waypoints routed" | **Pass** | "4 Waypoints routed 1.3 mi", "5 Waypoints routed 3.2 mi" (`ui/36`, `44`) |
| 1 | Owner layout: the saved peek | **Pass** | "TEST route 1 · 3.2 mi / 5 Waypoints routed / Directions" (`ui/54-opened-row.xml`); dragged open, the legs show with "No trail data, straight line" on the straight one and no elevation lines (saved routes wait for step 3b, -1896) |
| 2 | Elevation lines under each trail leg and the total (imperial) | **Pass** | see "Elevation figures" below |
| 2 | No line on a straight leg | **Pass** | TEST C to TEST D (IN_DIFFERENT_AREAS) shows "No trail data, straight line" and no elevation line; the total line is gone with it (`ui/44`) |
| 2 | Elevation lines in metric | **Pass** | "Elevation: +2 m (↑ 4 ↓ 0)", distances in m (`ui/144-metric.xml`) |
| 449-1 | The line | **Pass by day and night, on this ground** | solid magenta on the trail with white casing by day, black by night; joins and the straight leg are dashes with the casing along each dash (`shots/35-4stops.png`, `45-whole.png`, `133-fit-short.png` day; `140-night-route.png` night). Not seen on "the palest ground on the walk" |
| 449-2 | Layer order with navigation | **Not run** | no GPS fix: navigating to TEST C drew only a straight "≈ 0.7 mi" with the map locked on the network position at high zoom; I could not get both lines in one view. For the owner or the next helper outdoors |
| 449-3 | S-1, the view kept on return | **Pass** | panned and zoomed after opening the saved route, Journal, back to Maps twice: the camera stayed where it was left, no re-fit (`shots/55-moved.png`, `57-back-to-maps.png`, `58-back-to-maps2.png`). With Locate following turned on after the open and a Journal round trip, the view still did not re-fit (`59-follow-on.png`, `60-follow-return.png`). Whether following itself ended again could not be read: with no GPS fix the view does not move either way |
| 449-4 | The fit itself | **Partial pass** | from the row and from the pin, the whole route is inside the view above the card (`shots/54`, `64`); a 47 m two-stop route opened at a capped zoom with both stops and the trail between them in view (`133-fit-short.png`). The 48 dp margin was not measured. "Following ends": the camera did not move over three network fixes after the open (`65-after.png` identical to `64` in the map area), which is consistent with not following, not proof of it |
| 449-5 | N9, a stop exactly on a trail | **Not run** | no stop was placed exactly on a trail line |
| 449-6 | Real insets | **Partial pass** | portrait with the gesture bar: the card's bottom sits above the nav bar (`shots/35`); in fullscreen the card sits above the gesture bar (`shots/101-fullscreen.png`). Whether the cluster clears the expanded card is open: see F-2. Pill and legend chip not run (needs navigation moved away and a forecast field) |
| 449-7 | Back order | **(a) pass with a note, (b) pass, (c) pass, (d) not run** | (a) with the fan open over plotting, first Back closed the fan and kept the plot, second … not taken further (`93-back1`); (b) fullscreen while plotting: first Back cancelled the plot and stayed fullscreen, second left fullscreen (`104-fs-back1`, `105-fs-back2`); (c) route opened from the Trip Planner over the List tab: Back closed the card and line and the List tab returned with the drawer open on the Trip Planner (`108-back-to-list.png`); (d) needs navigation in fullscreen with a plot, not run |
| 449-8 | A leg in flight on the real engine | **Not shown** | legs arrived too fast to see a progress mark (two quick taps: the second was absorbed by the fan; Undo right after a tap: the leg was already in). No "30 s wait" was ever seen. Cancel then Plot route again: the first new leg routed |
| 449-9 | Save | **Pass, Directions included** | dialog above the keyboard with name field and Save reachable (`shots/47-save-ime.png`); Add date, Remove date, save undated: row "TEST route 1 / 3.2 mi", no date; dated "TEST route 2" sorts first under "Today" with "Oct 31, 2026" (`ui/132-two-rows.xml`); past dates disabled in the picker. Directions opened the maps app at exactly the first stop (0.0 m from Waypoint 1, `logs/directions-intent.txt`, coordinates not copied here) |
| 449-10 | Discard | **Pass for Keep plotting; Discard not taken** | two stops, tap on the saved route's pin (through the fan): "Discard this route?" with Discard and Keep plotting (`ui/91-discard.xml`); Keep plotting kept the two-stop plot |
| 449-11 | Bubble at font scale 2.0 | **Pass** | Navigate, Plot route and Details stack inside the bubble (`shots/147-font2-bubble.png`); Plot route started plotting ("1 Waypoint selected, tap another to route", `ui/148`). Font scale restored to 1.0 and confirmed |
| 449-12 | The handle | **Partial pass** | tap toggles peek and expanded; a drag up opened and a drag down closed a saved route's card (`ui/69`, `70`). A real thumb and the five-leg scroll cap are for the owner |
| 449-13 | Touch pass-through | **Pass in portrait** | long-press 12 dp-ish above the card at three x positions reached the map (MapLibre `LONG_PRESS` logged 2 each), on the card itself none (control), mid-map 2 (control). Undo, Cancel and Save each took a tap near their edges. Landscape not run |
| 449-14 | Tablet | **For the owner** | the owner's tablet |
| 450-1 | Redraws stop while recording, screen off | **Not run** | handoff point reached before the recording trial |
| 450-2 | Main-thread CPU re-measured while recording | **Not run** | as above |
| 450-3 | Not recording, app in front, screen off | **Pass** | 70 s with the screen off: 4 "surface is not valid" lines, all in the first 0.8 s, none after (against about 60 s of them on 10-30); main thread 0.3 s CPU (0.4% of a core), process 1.4%; the compass listener was unregistered at the moment of screen-off (`dumpsys sensorservice`: "-" rotation vector at 16:26:28, the screen went off at 16:26:28.79). Observation: the activity window logged `stopped(true)` / `WindowStopped … true` at screen-off (`logs/c3-notrec-screenoff/`) |
| 450-4 | Return within 10 s / after 10 s | **Within: pass. After: not shown** | within (6 s off): frames captured from the wake show the last heading, then the live one, no "—" frame (`logs/c4-within/strips.png`, 275 frames). After (15 s and 20 s off): no "—" appeared in any captured frame either (`logs/c4-after`, `c4-after-r2`, about 50 frames a second); the first lit frame shows a heading figure with the arrow pointing straight up, then the arrow turns. Either the dash lasted less than one frame or it was not shown; adb cannot tell these apart. See F-3 |
| 450-5 | Trust re-check after a calibrating compass | **For the owner** | needs a magnet or the car to make the compass unreliable |
| 450-6 | Navigation across a long screen-off | **Not run** | no GPS fix, and handoff |
| 450-7 | Picker map after a long screen-off (observation) | **Not run** | handoff |
| 450-8 | Paused, not stopped (system dialog or split screen) | **For the owner** | needs a real system dialog; split screen by adb not attempted |
| 450-9 | Backgrounded while recording (observation) | **Not run** | handoff |
| 5 | Compass strip after first fix; does "Alt unavailable" crowd out the heading (observation) | **Not run** | no GPS fix all session, so no first fix to watch |

## Elevation figures (check 2)

All legs inside Region 1 (heighted trail data), imperial, read from the expanded card while plotting:

| Leg | Distance | Elevation line |
|---|---|---|
| Waypoint 1 to TEST A | 0.4 mi | Elevation: -11 ft (↑ 0 ↓ 13) |
| TEST A to TEST B | 0.4 mi | Elevation: +1 ft (↑ 0 ↓ 0) |
| TEST B to TEST C | 0.4 mi | Elevation: +7 ft (↑ 14 ↓ 0) |
| 4 Waypoints routed (total) | 1.3 mi | Elevation: -4 ft (↑ 13 ↓ 13) |
| TEST C to TEST D (straight, crosses out of Region 1) | 1.9 mi | no elevation line; "No trail data, straight line" |
| 5 Waypoints routed | 3.2 mi | no total elevation line (not every leg has heights, as -1896 rules) |
| TEST F to TEST E | 573 ft | Elevation: -6 ft (↑ 0 ↓ 0) |

Metric: TEST B to TEST C, 664 m, "Elevation: +2 m (↑ 4 ↓ 0)", total the same.

**Observation, not a gate (F-1 below):** the total's climb and descent are not the sums of the legs' (legs ↑ 0+0+14, ↓ 13+0+0;
total ↑ 13 ↓ 13). The net figures do add (-11 + 1 + 7 = -3, shown -4 after rounding). The metric reading of the same B to C leg
(↑ 4 m = 13 ft) against imperial (↑ 14 ft) is consistent with rounding.

## Failures and findings

**F-1. The total's climb and descent do not add up from the legs.** Observation, not a gate: whether this is wrong depends on
the rule. -1896 says climb/descent are "banked by the journal's 4 m rule" and -1897 records "no reset between legs", so the
total is banked over the whole profile rather than summed, and on a nearly flat route a 4 m hysteresis can bank 13 ft up and
13 ft down overall while each leg alone banks less. A reader sees "↑ 0, ↑ 0, ↑ 14" above "↑ 13". Inferred from the two
records, not read in code. Evidence `ui/36-expanded.xml`. For the planner: whether the owner is content with a total that
does not add from its rows (the "Don't make me think" rule is the relevant one).

**F-2. The cluster and the expanded card, by the bounds.** With the card expanded (`ui/36-expanded.xml`), the cluster's lowest
item "Start recording track" ends at y 1603 and the card's first text row starts at y 1554, so the two overlap by about 50 px in
the dump. In the screenshot (`shots/36-expanded.png`) the cluster has been raised and the lower cluster group (record, return
to vehicle) is hidden behind or replaced by the card's region. I did not establish whether the card's top edge sits above y
1554 or whether the cluster is drawn over it. Not a confirmed failure; a look by eye on the phone settles it.

**F-3. No "—" seen after a long screen-off (450-4).** At about 50 frames a second, no frame after a 15 s or 20 s screen-off
showed "—"; the strip showed a heading from the first lit frame. Sorrel's pass condition is "'—', then the first reading". Either
the first reading lands before the first composed frame (the rotation vector re-registers at about 15 Hz, and the screen takes
some hundreds of ms to light), or the 10 s rule did not replace the value. Not separable by adb. A unit test covers the rule;
on the phone this stays unshown. Evidence: `logs/c4-after/strips.png`, `logs/c4-after-r2/strips.png`.

**Notes from the run, not checks:**
- Tapping a waypoint that sits on a saved route's pin, or two close waypoints, opens the fan first; while plotting, a tap that
  lands on overlapping markers is taken by the fan, not as a stop (seen with TEST B over the trip flag and TEST E beside TEST F).
  The fan then needed its own Back (`93-back1`). By the rulings this is the fan's normal behaviour.
- The map's attribution "i" button sits over the plotting card's Save in fullscreen (`ui/101-fullscreen.xml`: the "i" at
  [1346,2814][1425,2893], Save at [1227,2841][1346,2916]). A tap at the "i" position opened the Save dialog, so the card took the
  touch, which is the safer side; the "i" is drawn over the card there.

## Data and settings

- **Backup first:** `baseline/app-data.tar` (sha256 alongside) of `databases`, `files`, `shared_prefs`, `no_backup`, with
  per-table hashes and per-file hashes, taken at 15:49 PDT before anything was touched.
- **Created and deleted:** waypoints TEST A to TEST F, saved routes "TEST route 1" (undated, five stops) and "TEST route 2"
  (dated today, two stops). Routes deleted from the Trip Planner; waypoints deleted by swipe in Journal > Records > Waypoints,
  each row's name read before its Delete was tapped.
- **Confirmed gone in the database** (pulled after): `waypoints` 11 rows, none named TEST; `planned_trips`,
  `planned_trip_route_stops` and `planned_trip_route_legs` 0 rows. Every forager.db table's row count and hash equals the
  baseline (`diff baseline/table-hashes.txt after/table-hashes.txt`: only the map cache's `resources`, `tiles` (1777 to 1782)
  and `sqlite_sequence` differ, which is MapLibre caching tiles I viewed). `user_version` 23, integrity ok. Regions 1 and 2,
  the 5 tracks, 273 points and the owner's 11 waypoints are unchanged.
- **The owner's data touched:** none edited or deleted. I opened the owner's "Start · Oct 29" waypoint's detail sheet once by a
  mis-tap while looking for the waypoint list, read it, and backed out without changing anything; the table hash proves it.
  Waypoint 1 was used as the first stop of TEST route 1, as the dispatch allows.
- **Settings changed and restored:** units Imperial → Metric → Imperial; Night Maps off → on → off; font scale 1.0 → 2.0 → 1.0
  (read back as 1.0). `map_preferences` is byte-identical to the baseline. One new file exists:
  `files/datastore/distance_unit_preferences.preferences_pb`, holding `unit_system.selected = IMPERIAL`. It was created when I
  first chose a unit; Imperial was the default before, so the behaviour is the same, but the file did not exist before. I did not
  delete it (deleting app files is not named in the dispatch).
- **Chrome:** the licence link was opened "Just once"; no default app was set. Google Maps was opened once by Directions and
  backed out of.
- **Navigation:** one, to TEST C, started and ended with "Stop navigating". No recording was started.
- **Not cleared or reset:** logcat, crash buffers, caches, permissions. Logs were read from timestamps (`logcat -T`).
- uiautomator dumps went to `/data/local/tmp/hz-ui.xml`, removed at the end. My script first named a path in Download, but it
  was changed before the first dump ran, and `ls` confirms no such file exists there. Nothing in Download was touched.

## Phone's end state

1.0.4294+g8e70a28b, Maps tab in front, not plotting, no route showing, no navigation, no recording, no Forager service running,
screen on (Awake), font scale 1.0, Imperial, Night Maps off, logger on (unchanged file). Battery 84%, USB.

## Not run, for a fresh helper

In the dispatch's order, cheapest first: 450-1 and 450-2 (a throwaway TEST recording with the screen off, two minutes, using
`checks/scripts/sample.sh <label> 120` which samples `/proc` ticks and the compass listener without clearing logcat), 450-9
(same script with `home`), 450-7, then 449-2, 449-5, 449-6's pill and chip, 449-7(d), 449-13 in landscape, and check 5. Those
needing a GPS fix (449-2, 449-7(d), 450-6, check 5) need the phone outdoors.

## For the owner

1. **450-5, the trust re-check:** on the Maps tab, hold a magnet (or sit in the car) until the strip shows the compass as
   unreliable. Press the power button to turn the screen off, take the phone away from the magnet, wait 5 s, press power. Pass:
   the strip stays untrusted for about 2 s after the screen comes on, then trusts; it is not trusted the moment the screen lights.
2. **450-8, paused not stopped:** on the Maps tab, pull down the notification shade, or open split screen with another app.
   Pass: the strip's heading keeps turning as you turn the phone.
3. **449-12, the handle with a thumb:** plot five waypoints; tap the bar at the top of the card, then drag it up and down.
   Pass: it opens and closes under a thumb, and the open list scrolls without covering more than half the map.
4. **449-14, the tablet:** plot a route; the card stops at its width cap, the map's buttons and the legend chip sit above it,
   and a long-press beside the card reaches the map.
5. **F-2, by eye:** expand a four-waypoint plot on the phone; does the record button sit over the card's first row?

## Disclosure

- **Confirmed:** every Pass row above, from the named dumps, screenshots and logs; the after-state hashes.
- **Inferred:** F-1's cause (from -1896 and -1897, not code); that the map not moving over network fixes means following ended.
- **Could not determine:** F-2 by dump alone; F-3's cause.
- **Premises that were wrong or incomplete:** none in the dispatch; the session simply had no GPS fix, which the dispatch did
  not assume either way.
- **Slips, disclosed:** one mis-tap opened an owner waypoint's detail sheet, read-only;
  a helper script of mine (open a saved route from the Trip Planner, then Back twice) pressed one Back too many once and
  showed "Tap Back Button Again to Exit" (the app did not exit), and later ran a second time by mistake; both times it only
  opened and closed TEST route 1 and moved the camera. I deleted that script.
- The Restricted Object was not read, named or quoted.

# Part 2

Dispatch `prompts/preserved/2026-10-31-02-s22-phone-checks-part-2.md` (origin/records-after-173, RECORD -1902), rules as in
2026-10-31-01. Run by Hazel (Opus), 2026-10-31, 16:41 to 17:12 PDT, same phone and build (1.0.4294+g8e70a28b), same
conditions: **indoors on the desk, no GPS fix** ("Approximate location, finding GPS…" throughout, network fixes only).
Evidence is in `/home/zynergy-labs/s22-2026-10-31/checks/part2/` (off git): `logs/`, `shots/`, `ui/`, `baseline/`,
`after/`, `scripts/`. Nothing here carries coordinates or place names.

**Also partial.** The #450 checks and F-3 ran; the #449 leftovers did not (see "Not run"). The reason is the map, not the
checks: an early double tap zoomed the map in to street level, and getting a usable view back by adb took most of the
remaining budget (see "Zooming out by adb", for the next helper).

## Verdict table (part 2)

| # | Check | Verdict | Evidence |
|---|---|---|---|
| 450-1 | Redraws stop while recording, app in front, screen off | **Pass** | 3 min off: 16 "surface is not valid" lines (8, 4, 4 a minute, the 8 being the screen-off burst), against about 940 a minute on 10-30. Repeat, 90 s: 9 lines (4, 4, 1), one every 15 s after the burst. `dumpsys gfxinfo` total frames did not move while off (64979 before and after). The compass listener (`AndroidCompassProvider`) was unregistered at screen-off and absent in every 8 s sample. `logs/450-1-rec-front-off/`, `logs/450-1-rec-front-off-r2/` |
| 450-2 | Main-thread CPU, same runs | **Fail** | main thread **39.2%** of a core over 174 s (process 46.3%), and **29.4%** over 82 s on the repeat (process 36.6%), against the target of about 4.7% and above the 16% of trial C. See F-4 |
| 450-9 | Backgrounded while recording (observation) | **Observed: falls well below 4.7%** | Home, screen on, 116 s: main 0.7% of a core, process 8.4%; compass listener absent; 3 surface lines, all at the move to Home. `logs/450-9-rec-home/` |
| 450-4 / F-3 | Return after more than 10 s: "—", then the first reading | **Pass, shown** | screenrecord started before the wake. After 15 s off: frame 10 the old heading (arrow straight up), **frame 11 "—"**, frame 12 the new heading crossfading in. After 20 s off: the old heading for two frames, then one frame with the heading blank (no figure), then the reading. Control, 6 s off: the first lit frame already shows the live heading, no dash and no blank. In every run these frames come while the panel is still at 1-10% of full brightness, which is why part 1's screencaps and the eye never caught it. `logs/f3-r3/first-frames-stretched.png`, `logs/f3-r4-vs-within.png` |
| 450-6 | Navigation across a long screen-off | **Partial, no fix** | navigating to Waypoint 1 (owner's, read-only, ended with Stop navigating). Compass unregistered at each screen-off and re-registered at each wake (`dumpsys sensorservice`). Run 1 (15 s): the HUD's first lit frames show the same 292° as before the off, so stale and fresh cannot be told apart. Run 2 (20 s): the first lit frame shows the HUD with no heading figure and the heading arrow straight up, the next frame the reading (296°). The grey arrow to the target stayed throughout. "Map stays facing-up": the map did not move between the first lit frame and 3 s later, but with no fix the map was not rotating with heading before the off either, so this is not shown. `logs/450-6-nav-r1/`, `logs/450-6-nav-r2/` |
| 450-7 | Picker map after a long screen-off (observation) | **Observed, puck not readable** | Offline Maps picker, 15 s off: its compass also unregistered at screen-off and re-registered at the wake. The puck sits almost wholly under the picker's pin at that zoom; the visible edge did not change over the first 12 lit frames (about 250 ms). What MapLibre draws for `UNRELIABLE` there is still unread. `logs/450-7-picker-r1/puck-grid.png` |
| 449-2, 449-5, 449-8 | Layer order; a stop on a trail; a leg in flight | **Not run** | see "Not run" |
| 449-4, 449-6, 449-7(d), 449-10, 449-13 landscape | Part 1's partials | **Not run** | see "Not run" |

## F-4. The main thread works at about 30-40% of a core while recording with the screen off

The fail in 450-2, with what was measured. Not diagnosed further than this.

- **What runs.** A 10 s `simpleperf` profile of the main thread, screen off, recording, activity `STOPPED`
  (`logs/450-1-rec-front-off/perf-children.txt`): 72% under `Choreographer.doFrame`, 70% under Compose's
  `AndroidUiFrameClock` frame callback, 65% under `Recomposer.runRecomposeAndApplyChanges`, 42% in `AnimatedContent`, and 18%
  in the app's `WordSwap` → `wordsOf` (the regex that keys the crossfade, `ui/motion/WordSwap.kt:23` at 8e70a28ba). So
  composition is running frame after frame while the activity is stopped, and no frame is drawn (gfxinfo unchanged).
  `top -H`: the main thread at 33.8%, no other thread above 4.4%.
- **What is registered while off.** The compass is gone. Still registered: one `AndroidPressureProvider` (1 s period, 5 s
  batch; a second instance unregistered at screen-off) and the walk logger, `AndroidWalkLogPlatform` (step counter, step
  detector, rotation vector at 100 ms, game rotation vector, pressure, and gyroscope, magnetometer and accelerometer at
  40 ms). `AndroidPressureProvider.onSensorChanged` is 0.8% of the profile; the walk logger does not appear in it.
- **What differs.** Not recording, screen off (part 1's 450-3): main 0.4%. Recording, screen on: main 27.7%. Recording,
  Home: 0.7%. Recording, screen off: 29-39%. So the cost appears with the recording, and the screen-off in front does not
  stop it as Home does. The screen-off here is "Dozing" (always-on display), where the task stays `visible=true,
  visibleRequested=false`; with Home it is hidden.
- **Inferred, not read in code:** that some state the recording writes is read in composition on the Maps tab, and that the
  frame clock keeps running under the always-on display while the activity is stopped. Which state, and whether the walk
  logger (a dev tool on this build) is part of it, I did not establish. Trial C on 10-30 was on a walk with a fix; this
  was indoors with "finding GPS" and "waiting for a fix" lines, so the figures are not like for like.
- **Observation:** the throwaway track showed "0 points" in Records while the database held one track point for it.

## Zooming out by adb (for the next helper)

`input` has no two-finger gesture, `/dev/input` is closed to the shell by SELinux, and `KEYCODE_ZOOM_OUT` does nothing on
this map. What works about half the time: `input tap X Y; input swipe X Y X Y-600 700` in one `adb shell` (a double tap
then a slow drag up, MapLibre's one-finger zoom-out). When it fails it pans instead. Part 1's `zoomout.sh` (motionevent
moves) did not work at all this time. Avoid double-tapping on the map: two `input tap`s zoom in a whole level.

## Not run, for a fresh helper or the owner

All need plotting, which needs a usable map view and TEST waypoints on a trail inside Region 1 or 2:
- **449-5:** drop a TEST waypoint exactly on a trail line (zoom in first), plot from it.
- **449-4:** open a saved TEST route from the Trip Planner and measure the route's extent against the view edges and the
  card's top (48 dp is 132 px on this phone).
- **449-10:** two stops plotted, tap a saved route's pin, tap **Discard**: the saved route opens with its dots.
- **449-6:** the pill (navigating, map moved away) and the legend chip (a forecast field on, then restored) above the card.
- **449-7(d):** navigating, fullscreen, plotting: Back cancels the plot, then leaves fullscreen, then the navigation prompt.
- **449-13 landscape:** needs `accelerometer_rotation` (now 1) or `user_rotation` changed and restored.
- **449-2:** navigating and a plotted route across the same trail. Without a fix the way-home line is straight from the
  network position, so this is best outdoors.
- **449-8:** a screenrecord from before the tap (the method used for F-3 here) may catch the progress mark.

## Data and settings (part 2)

- **Backup first:** `part2/baseline/` (raw `run-as tar`, table and file hashes), 16:41 PDT. Its forager.db tables equal
  part 1's after-state.
- **Created and deleted:** one throwaway recording (16:43 to 16:55 PDT, unnamed, one network point), deleted by swipe in
  Journal > Records > Tracks after reading its row ("Oct 31, 2026, 4:43 PM"). No waypoints, routes or trips were created.
- **Confirmed gone:** `part2/after/table-hashes.txt` against the baseline: every forager.db table identical (tracks 5,
  track_points 273, waypoints 11, offline_regions 2, planned trips, stops and legs 0) except `sqlite_sequence`
  (track_points 275 → 276, from the inserted and deleted point). `user_version` 23, integrity ok. The other differences are
  the map tile cache. `shared_prefs` and datastore files byte-identical.
- **The owner's data:** Waypoint 1 navigated to (twice across screen-offs, ended); its detail sheet opened from the Journal
  and closed; a track bubble opened once by a mis-tap and closed. Nothing edited; the hashes prove it.
- **Settings:** none changed. Font scale 1.0, auto-rotate 1, screen timeout unchanged.
- **Navigation and recording:** one navigation, ended with Stop navigating; one recording, ended with Stop recording.
- **Not cleared or reset:** logcat, crash buffers, caches, permissions. Temporary files on the phone (`hz-ui.xml`,
  `hz-perf.data`, a pinch folder) removed; no screenrecord or simpleperf left running.
- **Laptop slip, disclosed:** my first F-3 screenrecord used the headerless raw format with no working time limit and wrote
  4.5 GB in about two minutes, which **filled the laptop's root disk (40 MB free) for about a minute** before I killed it and
  truncated the file (3.6 GB free after; 3.4 GB at the end). Anything else writing to the root disk in that minute may have
  failed. The later captures used the framed format at 360x772 with a hard stop.

## Phone's end state (part 2)

1.0.4294+g8e70a28b, Maps tab in front, zoomed in near the network position, not plotting, no navigation, no recording, no
Forager service running, screen on (Awake), font scale 1.0, auto-rotate on, Imperial, Night Maps off. Battery 89%, USB.

## Disclosure (part 2)

- **Confirmed:** the counts, CPU figures, listener states and frames above, from the named logs and captures.
- **Inferred:** F-4's mechanism (composition kept running by recording state under the always-on display).
- **Could not determine:** which state drives F-4; whether the walk logger contributes; the map's facing behaviour across a
  screen-off without a fix; what the picker's puck draws.
- **Premises wrong or incomplete:** none in the dispatch.
- The Restricted Object was not read, named or quoted.
