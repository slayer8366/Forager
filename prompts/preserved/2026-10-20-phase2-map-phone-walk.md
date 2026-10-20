# Phone walk: the map after UI split Phase 2.2

Written by the planner (Rowan Waymark), 2026-10-20, for the owner. Forager RECORD: -1458. On the S22, with the pre-main build the planner installs (it names the build when it hands this over). About 10 minutes, indoors is fine.

**Why this walk exists.** Phase 2.2 cut the map's code into about a dozen files without meant changes to what it does. Every PR had identical test results, an S22 launch check and a code review, and the planner's automated screenshots and a real drag matched. But no automated test can run the real map (it doesn't load in the test environment), so the things below have only been read in the code, never seen. If anything differs from "what you should see", stop and tell the planner what you saw; nothing needs fixing on the spot.

**Screenshots:** take one only if something looks wrong. They stay on your phone or laptop, never on GitHub.

## Steps (cheapest first)

| # | Do this | What you should see |
|---|---|---|
| 1 | Open the app on the Maps tab. Drag the map, pinch to zoom in and out, and twist two fingers to rotate. | The map follows your fingers smoothly. The compass needle turns as you rotate. Nothing else moves. |
| 2 | Tap the locate button so the map follows you, then nudge the map slightly with one finger, and then flick it hard. | A small nudge springs back and keeps following. A hard flick stops following. |
| 3 | Switch to Journal, then back to Maps. | The map comes back where you left it, with no flash of a blank or wrong map. |
| 4 | Press Home, wait a few seconds, then open the app again. | The map is as you left it and still responds to touch. |
| 5 | Open the Layers sheet and change the basemap (Street ↔ Topographical). Then switch Night Maps on and off in Tools. | The map restyles each time. Markers and lines redraw. The map stays where it was. |
| 6 | Tap a marker of each kind you have on screen (a waypoint, a find, a photo, a planned trip). | Each opens its own bubble. |
| 7 | With a bubble open, turn the phone to landscape and back. | The bubble stays attached to its marker. |
| 8 | Find a place where markers stack (zoom out until several overlap) and tap the stack. | It fans out. Tapping a fanned marker opens that one. Panning the map folds the fan away. |
| 9 | Open a fanned marker's entry in the Journal, then come back to the map. | The fan reopens where it was. |
| 10 | Run a search for a place. | The map frames the searched area once. If it was following you, it stops. |
| 11 | *If you have time:* turn on a forecast layer in the Layers sheet. | The coloured forecast cells appear and change as you pan or zoom. |
| 12 | *If you have time:* start a short recording, walk a few steps (or wait), then stop it. | The breadcrumb line draws, and the map doesn't snap back to the search area while it records. |

## Not part of this walk
- The way-home route line (needs a recording with a way home set): it was checked in code and review only.
- Offline tiles: the planner can check those by adb if you want.

## What the planner records
Your pass or fail for each step, in your words, in the RECORD. Any fail becomes a finding with the step number, and nothing is promoted to main until it's understood.
