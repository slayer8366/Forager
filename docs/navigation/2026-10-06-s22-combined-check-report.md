# S22 combined check: units (-549) and fix provider (-527) on one build of main (report, dispatch 2026-09-28-571)

Dispatch: `prompts/preserved/2026-10-06-05.md` on `records-after-173` (RECORD -571). Device check only: no app
code and no tests changed. One Gradle build (`assembleDebug`) from a detached worktree at `origin/main` =
`f4b6b98e`, installed with `adb install -r` on the S22 (SM-S908U, `ro.build.id` BP2A.250605.031.A3). Installed
versionName **1.0.2824+gf4b6b98e**, versionCode 2824, signer `d59f30b8` unchanged from the build it replaced
(1.0.2820+g38058cd8). The phone's clock is Pacific time; times below are the phone's.

Evidence (screenshots, UI dumps, logcat, walk logs, build log) is at
`~/Zynergy/device-evidence/2026-10-06-s22-combined-check/`, outside every repository. This report holds no
coordinates and no screenshots. Elevations are given as their unit and digit count only (for example "3NN ft");
the exact values are in the evidence folder's UI dumps.

## Result

| Step | Result |
|---|---|
| A1 Imperial: strip elevation | **Pass**: "3NN ft" |
| A2 Imperial: HUD elevation | **Pass**: "3NN ft" |
| A3 Imperial: soil temperature and guidance | **Pass**: "Soil temperature: 47.2°F"; "roughly 50–68 °F" |
| A4 Imperial: sighting bubble accuracy | **Pass**: "±39 ft accuracy" |
| A5 Metric: steps 1 to 4 | **Pass**: "1NN m" (strip), "1NN m" (HUD), "Soil temperature: 14.8°C" and "roughly 10–20 °C", "±7 m accuracy" (and "Accuracy not reported" on another) |
| B Fix provider step 1, indoors | **Pass**, on two network fixes of 50 m or better (acc 20.9 and 48.9) with no GPS fix since navigation started |
| C Fix provider step 2, outdoors | **Pass** from the phone's own log buffer (no live capture) and the owner's report: 138 `provider=gps` fixes of 50 m or better after the indoor capture ended, down to 3.8 m; the normal dot rests on the owner's report (see C) |
| D `ForagerFixRule` against `ForagerFix` | Observation: **0** against **641** |

## Before the install (the planner's added steps)

A test recording was still running from the walk-logger desk run: `TrackRecordingService` was in the foreground
(created about 1 h 9 min earlier), notification 1001 "Recording a track" / "Forager is tracking your location in
the background.", and `walklog-20261006T050933Z.txt` was being written. On the planner's word:

1. **Stopped from the app's own control** (content description "Stop recording track"). Afterwards the control
   read "Start recording track", `dumpsys activity services` listed no `ServiceRecord`, and notification 1001 was
   gone from the active list. The log's last line is `END reason=recording-stopped`. The planner had expected a
   `STOPPED` line. In the code, `STOPPED` is written only for storage-low or a write failure
   (`WalkLogFormat.kt:129`), and a normal stop writes `END` (`WalkLogSession.kt:64,133`). The 65-byte first-run
   file is the one with a `STOPPED` line: `STOPPED reason=storage-low free=0 min=209715200`, which is -569's bug.
2. **Both walk logs pulled**, left on the phone, with the phone's sha256 matching the copy:
   `walklog-20261006T050848Z.txt` (65 bytes, `299b5192…b139`) and `walklog-20261006T050933Z.txt` (54,405,703 bytes,
   `21e2ff97…fbde`).
3. **The new build's first recording logs.** It was a recording of about 6 seconds, started and stopped from the
   app's control: `walklog-20261006T062008Z.txt`, 152,749 bytes, 1,407 lines. The header reads
   `# app versionName=1.0.2824+gf4b6b98e versionCode=2824`, its battery line reads `free=39114678272`, and the
   last line is `END reason=recording-stopped`. It has no `STOPPED` line. This is the -569 fix reading real free
   space on the S22, where the `walklogs/` folder already existed. It is **not** the fresh-folder case, which
   stays with the S26 (-570).
4. **Walk logger switch: off.** Diagnostics > "Walk logger" reads `checked="false"`, and I left it off. A and B
   don't use it.

## A. Units (-549), all five steps

Steps are from `docs/navigation/2026-10-06-units-follow-report.md` "Device check". Strings were read from
`uiautomator dump`, with screenshots alongside.

1. **Imperial, strip.** The strip read "52° NE · 3NN ft · …" (`ui-50.xml`). Elevation reads `liveFix` only
   (`AvailabilityUiState.kt:351`), so it shows only once a GPS fix of 50 m or better has arrived. At the desk
   that happened twice in about 35 minutes (see B). **Pass.**
2. **Imperial, HUD while navigating.** It read "3NN ft" in 73 polls over the first window, e.g.
   "Turn 93° · ≈ 500 ft · 3NN ft". **Pass.**
3. **Imperial, availability results.** The soil line is in the drawer's Trip Planner > "Trip Windows"
   (`TripWindowsCard`), not on the List tab: "Soil temperature: 47.2°F", and the guidance reads "A soil
   temperature of roughly 50–68 °F is often quoted as broadly typical for temperate fleshy fungi." Also seen:
   "6–12 days after 0.8 in of rain ending Sep 29", "Search radius: 5 mi". **Pass.**
4. **Imperial, sighting bubble.** "Fungi Including Lichens", "Oct 5, 2025", "±39 ft accuracy". **Pass.**
5. **Metric, steps 1 to 4 again.**
   - Strip: "52° NE · 1NN m". The same fix as step 1, converted.
   - HUD: "1NN m".
   - Trip Windows: "Soil temperature: 14.8°C", "roughly 10–20 °C", and "30mm of rain".
   - Bubbles: "±7 m accuracy" on one sighting and "Accuracy not reported" on another.
   - The search header read "October · 8 km".
   - **Pass.**

   The soil figures under Metric come from a different search than under Imperial: Imperial used a manually
   entered location, and Metric used "Use current location". So 47.2°F and 14.8°C are not the same reading.
   Only the format is checked here. Units was set back to Imperial (US), as I found it.

## B. Fix provider (-527), step 1, indoors at the desk

From `docs/navigation/2026-10-05-fix-provider-report.md` "The S22 step". `adb logcat -s ForagerFix ForagerFixRule`
ran from 23:21:37 to 23:58:08.

**Second window, the one that counts (23:43:38 to 23:53:46).** I navigated to "Waypoint 3", a waypoint already
in the S22's dummy data that sits beside the desk. The UI was polled about every 5 s, with a screenshot on each
network fix of 50 m or better.

- 23:47:43, `provider=network acc=20.9`, with no GPS fix since navigation started. The HUD read "52° NE",
  "—", "Approximate, finding GPS…". The screenshot (`B2-netfix-1.png`) shows the approximate dot with its
  circle and no line.
- 23:48:03, `provider=network acc=48.912598`, still no GPS fix. Same HUD, no line (`B2-netfix-2.png`).
- From 23:43:38 to 23:50:00: 86 polls, every one "—" / "Approximate, finding GPS…", none "Arrived".

**Pass.** One wording note: while navigating, the compass strip hides and the HUD speaks for it. The HUD's text
is "Approximate, finding GPS…" (`APPROXIMATE_HUD_TEXT`, `ApproximatePositionHud.kt:20`). The strip's
"Approximate location, finding GPS…" (`PositionLabel.kt`) was read whenever I wasn't navigating, but I did not
capture it at the moment of a fix of 50 m or better. The one such fix outside navigation (23:27:42, acc 26.4)
arrived while I was on the List tab.

**After 23:50:05, GPS reached the desk.** GPS fixes fell to 47–49 m and then 20–45 m, and the HUD went to
"Arrived" with "Approaching", its elevation "3NN ft" to "4NN ft". That is GPS, which is allowed to arrive. It stayed on "Arrived" while
GPS lasted. After GPS dropped out (accuracy back over 100 m at 23:52:59) it kept "Arrived" with
"Approaching · last fix 33 s ago" … "54 s ago". At 23:55, navigating again under Metric, it read "Arrived" /
"Approaching · last fix 2 min ago". This is an observation about the stale-fix display, not part of -527's
claim. See "Could not determine".

**First window (23:31 to 23:41), confounded, kept for the record.** Navigating to "Waypoint 2", about 500 ft
from the desk:
- No network fix of 50 m or better arrived during it.
- GPS reached the desk at 44–47 m, and a blue line was drawn, correctly, for GPS.
- When GPS lapsed, a grey dashed line stayed. That is -502's kept line once the fix is lost (`straightOffline`,
  `RouteHomeLayers.kt:170`), not a line from a network fix. It does make "draws no line" unreadable in that
  window, which is why the second window was run from a fresh navigation.
- The HUD's approximate readout in that window sometimes read "Turn 87° · ≈ 500 ft · Approximate, finding GPS…".

## C. Fix provider step 2, outdoors (added 2026-10-06, after the owner's walk outside)

The owner took the S22 outside while no capture was running and brought it back in, plugged in. On the planner's
instruction C was read afterwards, with nothing started on the phone.

**The log buffer** (`adb logcat -d -v time -s ForagerFix ForagerFixRule`, read at 00:04:58): 926 lines, from
10-05 23:24:02 to 10-06 00:04:59. It begins after my own `logcat -c` at 23:21, so it covers the whole time since
the indoor capture ended (23:58:08) without a gap: the largest interval between consecutive fix lines after
23:58:08 is 20.0 s, the network provider's own cadence. **The buffer covers the whole window**, since the walk
outside falls after 23:58:08.

From 23:58:08 to 00:04:59:

| Provider | 50 m or better | Worse than 50 m |
|---|---|---|
| `gps` | 138 | 133 |
| `network` | 1 | 22 |

`ForagerFixRule` lines: **0**. The first `provider=gps` line is at 23:59:59, acc 124.1. The first one of 50 m or
better is at 00:00:10, acc 48.2.

By minute, GPS accuracy ran 28.9–112.6 m (00:00), 72.5–265.5 m (00:01), 88.7–265.8 m (00:02), 3.8–237.7 m
(00:03) and 3.8–24.2 m (00:04). There are 67 GPS fixes of 10 m or better, from 00:03:14 to 00:04:20, reaching
3.8 m. The best GPS at the desk in B was about 20 m, so that stretch is read as the time outside. **That is
inferred:** the log carries no indoor/outdoor marker. From 00:04:26 accuracy settles near 23.5 m, which I read as
back inside.

**The screen now, back inside** (UI dump at 00:05:30): the strip reads "310° NW · 366 ft · <grid reference,
masked>". The screenshot shows the normal solid dot with its accuracy circle, not the approximate one.

**The owner's report**, verbatim, relayed by the planner: "Normal dot and coordinates showed outside".

**Do they agree?** Yes. The log shows `provider=gps` fixes of 50 m or better, down to 3.8 m, across the
stretch read as outside, and no network fix took over. Under -527 that means the normal dot and the strip's
coordinates, which is what the owner saw. The dot and coordinates outside rest on the owner's word alone: no
screenshot or dump was taken outside. What I saw myself is the same state indoors afterwards.

**Pass**, on the log and the owner's report together.

## D. Observation, not a gate

Over 23:21:37 to 23:58:08: **641** `ForagerFix` lines and **0** `ForagerFixRule` lines. By provider:

| Provider | 50 m or better | Worse than 50 m |
|---|---|---|
| `gps` | 180 | 328 |
| `network` | 3 | 130 |

No other provider appeared.

## Database

`forager.db` hashed `c2d96765e4b20796` at all four reads: before the stop, after the stop, after the short
recording, and at the end. The `-wal` and `-shm` files changed between every pair of reads:

| Read | `-wal` |
|---|---|
| Before the stop | `ba2a4357…` |
| After the stop | `ad6cf355…` |
| After the short recording | `b61b8a2d…` |
| At the end | `24a55ed7…` |

The first two changes follow the two recordings I stopped, as the planner expected. The last change spans my
two species searches, three navigations and the Units changes. I take it to be mine, but which write made it is
inferred, not traced.

## Not run

- **C, outdoors, as a live capture.** The owner took the phone out with no capture running; C was read
  afterwards from the phone's log buffer (below). Nobody watched the screen outside except the owner.
- The S26 fresh-folder check and the S26 capability read (-566, -570): not in this dispatch.
- Nothing else Gradle: no tests were run.

## Disclosure

- **Confirmed vs inferred.**
  - Confirmed by UI dump: every quoted string.
  - Confirmed by screenshot: the dot, the circle and the absence of a line in B.
  - Confirmed by logcat line: every provider and accuracy.
  - Inferred: that the grey dashed line in the first window is -502's kept line. I read that from the layer code
    and its colour; I did not trace that frame's source data.
  - Inferred: that the last WAL change is from my own actions.
  - Inferred (C): which minutes the phone was outside, from GPS accuracy alone.
  - Not seen by me (C): the dot and the strip outside; that rests on the owner's report.
- **Could not determine.**
  - Whether "Arrived" on a GPS fix 2 minutes old is intended. `arrivedAtStart` requires a fix that is not
    lost, and I did not read the lost threshold. It is outside -527's claim, and is reported as an
    observation only.
  - The strip's own text at the moment of a network fix of 50 m or better (see B).
- **Premises that were wrong.**
  - The planner expected the stopped recording's log to end with a `STOPPED` line. A normal stop writes `END`.
  - The units report's step 3 says "Availability results". The soil line is in the drawer's Trip Planner,
    not on the results list.
  - Elevation (A1, A2) needs a GPS fix. At a desk that is luck, not a given.
- **Decided beyond scope.**
  - I used the waypoint already beside the desk ("Waypoint 3") rather than placing a new one, so no waypoint
    was written.
  - I ran a second B window after the first was confounded by GPS.
  - `adb logcat -c` cleared the phone's log buffer before the capture. That is a ring buffer, not a file.
  - A tap meant to close a sighting bubble landed on "View on iNaturalist" and opened the iNaturalist app. I
    backed out with Back. Nothing was written there, as far as the screens show.
  - Units was put back to Imperial, and the walk logger left off.
