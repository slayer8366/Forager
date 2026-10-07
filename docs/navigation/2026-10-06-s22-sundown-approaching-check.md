# S22 check: Settings > Sundown and the after-sunset line on main 4db24110 (report, dispatch 2026-09-28-614)

Dispatch: `prompts/preserved/2026-10-06-15.md` on `records-after-173` (RECORD -614). This was a device check only:
no app code and no tests changed. I ran one Gradle build (`assembleDebug`) from a detached worktree at
`origin/main` = `4db24110` (PR #181), under `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`. It
finished "BUILD SUCCESSFUL in 2m 31s" with no `error:` lines in the log, and `./gradlew --stop` followed it. I
installed it with `adb install -r` on the S22 (SM-S908U, serial R5CT321008R, `ro.build.id` BP2A.250605.031.A3).

The run was in the evening, starting about 19:41 PDT on 2026-10-06. Sunset and civil dusk had already passed. The
planner's instruction for tonight was: steps 1 and 2, the after-sunset part of step 3, and step 5. The window
opening in step 3, and all of step 4 (the outdoor walk), are recorded as **not reached tonight**. The owner was not
asked to go outside.

The phone was not attached when the session began (`adb devices` and `lsusb` showed no Samsung device). I built
the APK while the phone was unattached. The planner reported the phone connected at 19:59 PDT and I installed the
APK I had already built, with no rebuild.

Evidence (screenshots, UI dumps, logcat, build log, database hashes, a read-only database copy) is at
`~/Zynergy/device-evidence/2026-10-06-s22-sundown-approaching/`, outside every repository. This report holds no
coordinates and no screenshots. The screenshots show street-level map detail and stay in that folder. The phone's
clock is Pacific time; times below are the phone's.

## Result

| Step | Result |
|---|---|
| 1 Install | **Pass**: versionName "1.0.2845+g4db24110", versionCode 2845, signer `d59f30b8` (replaced 1.0.2824+gf4b6b98e) |
| 2 Settings > Sundown | **Pass**: all items as written, and 45 min survived a close from Recents (details below) |
| 3a Before the window: no line | **Not reached tonight** (would have applied before 16:11 PDT) |
| 3b Window open, before sunset: "Sunset h:mm PM · …" | **Not reached tonight** (16:11 to 18:41 PDT tonight) |
| 3c After sunset, before civil dusk: "Sun set … · dark in …" | **Not reached tonight** (18:41 to 19:10 PDT tonight; the recording started at 20:02) |
| 3d After civil dusk: "Dark since …" | **Pass**: "Dark since 7:10 PM" in the strip with recording on. An independent computation puts tonight's civil dusk at 19:10:34 PDT |
| 3e Tap Return: the line moves into the HUD | **Pass**, in the after-dusk form: "Dark since 7:10 PM" drawn under the HUD row |
| 3f Margin change moves the start-back time at once | **Not reached tonight**: no start-back time is shown after sunset |
| 4 Approaching and Arrived | **Not reached tonight** (needs daylight, GPS outdoors and the owner) |
| 5 Observation: the line against the real top inset | Below the status bar in portrait, landscape and fullscreen (details below) |

## Database hash, before and after

These are device-side `sha256sum` hashes, taken with `run-as` after `am force-stop` (`dbhash.sh` in the evidence
folder).

| When | `forager.db` | `forager.db-wal` |
|---|---|---|
| Before install (03:00:04 UTC) | `c2d96765e4b20796237419e58854c94363d2db1aac872d52fb73c9115a2038bc` | `24a55ed70492132a48b3f5850d44e66c012197386036a1ee5864d8bcb8e4af44` |
| After install, before first launch | `c2d96765…` (same) | `24a55ed7…` (same) |
| After the step 3 recording | `c2d96765…` (same) | `6c9e6e9dcf823100f8d476b543221d03f370a246ce54d89b815c130bcffad548` |

The WAL change is mine. The planner allowed a short recording on the S22's dummy data. It added one track,
`fa3b383e-4691-4a0a-b0a1-885977f4483b`, started 20:02:14 and ended 20:03:44 PDT, holding 1 track point. I found
these values by reading a byte copy of `forager.db` and its WAL pulled with `run-as cat` (the copy's hashes match
the device's), not by opening the database on the phone. I stopped the recording with the in-app Stop control. The
`-shm` file also changed; the app rewrites it on every open, so I do not count it as content.

## Step 2: Settings > Sundown

All values come from UI dumps (`ui-12-settings.xml`, `ui-13-settings-scrolled.xml`, `ui-14-45min.xml`,
`ui-15-after-restart.xml`, `ui-16-back-to-1h.xml`).

- Section heading: "Sundown".
- "Sundown alerts": an `android.widget.CheckBox`, `checked="true"`.
- "Dark under trees", with the sentence "Woods get dark before sunset. Alerts allow this much extra."
- Four `RadioButton` options, "30 min", "45 min", "1 h", "1 h 30". "1 h" had `checked="true"`.
- I tapped "45 min" and it became `checked="true"`. Then I closed the app by swiping its card up in Recents. After
  that, `pidof` returned nothing, so the process had ended.
- I reopened the app from the launcher and went to Tools > Settings. "45 min" was still `checked="true"` and
  "Sundown alerts" was still ticked.
- I set it back to "1 h" (`checked="true"`).

## Step 3: the after-sunset line

I pressed "Start recording track" at 20:02:14 PDT. The position was approximate: the strip read "Approximate
location, finding GPS…", and the Return button read "Recording — waiting for a fix to compute the way back". With
recording on, the strip showed **"Dark since 7:10 PM"** on its own line, under the heading row
(`ui-20-recording.xml`, `20-recording.png`).

I tapped Return. The HUD opened with "No target", "—", "No origin waypoint for this track" and "Stop navigating".
"Dark since 7:10 PM" moved under the HUD row: bounds `[30,466][470,527]`, against `[30,339][1410,400]` in the strip
(`ui-22-after-return-tap.xml`, `22-after-return-tap.png`). I tapped "Stop navigating" and the line went back to the
strip.

I stopped the recording at 20:03:44, and the line went away (`ui-27-after-stop.xml`). That fits the line being
drawn only while recording, as the dispatch states it.

**The times.** The app logs nothing about its sundown computation; logcat has no sundown, sunset or dusk line from
the app (`21-logcat-recording.txt`, `28-logcat-end.txt`). So the time on screen is the app's only output to check.

I checked it on my own with the NOAA solar equations, at the phone's last network location (accuracy 14 m) from
`dumpsys location`, computed off the phone:

| Event | Time (PDT, 2026-10-06) |
|---|---|
| Sunset | 18:41:07 |
| Civil dusk | 19:10:34 |

The screen's "7:10 PM" matches civil dusk to the minute. This position is the system's last location, not
necessarily the one the app used. They agree only to the minute, which is all the line shows.

From the same computation:
- Tonight the window would have opened at 16:11 PDT (2 h 30 min before sunset). "Sun set 6:41 PM · dark in …"
  would have shown from 18:41 to 19:10.
- For 2026-10-07 at the same place, sunset is 18:39 and civil dusk 19:08, so the window opens at about 16:09 PDT.
  That is the time-based window only. A start-back time less than 1 h away can open the line earlier when the walk
  back is known.

I found no app crash: there is no `AndroidRuntime` E or F line and no `FATAL` in the saved logcat.

## Step 5 (observation, not a gate): the line against the real top inset

The inset values come from `dumpsys window`'s `statusBars` InsetsSource. The strip and line positions come from UI
dumps. Screenshots: `23-portrait-strip.png`, `24-landscape-strip.png`, `25-fullscreen-portrait.png`.

| State | Status-bar inset | Strip heading row | "Dark since 7:10 PM" |
|---|---|---|---|
| Portrait | 0 to 100 px | 275 to 336, below the search bar (131 to 221) | `[30,339][1410,400]`, centred |
| Landscape (rotation 1) | 0 to 113 px; display cutout on the left, 0 to 100 px | `[1470,117]` to `[2578,178]` | `[1402,181][1842,242]`, left-aligned under the heading |
| Fullscreen, portrait | 0 to 100 px (the status bar stays visible) | 104 to 165 | `[30,168][1410,229]` |

- In all three, the line sits below the real status bar and nothing overlaps it.
- In landscape the strip starts 4 px below the 113 px inset.
- In landscape the strip runs from x 1402 to 2578. The search bar ends at x 1127, so the strip does not reach it.
  This is a single phone, 3088 px wide; a narrow phone is untested.
- To get landscape I set `accelerometer_rotation` 0 and `user_rotation` 1. Afterwards I restored them to 1 and 0,
  the values I read before, and read `accelerometer_rotation` back as 1.
- I left fullscreen before going on, so the persisted fullscreen setting is off, as I found it.

## Not reached, not verified

- **Not reached tonight:**
  - Step 3: the window before sunset (no line before it opens; the "Sunset … · start back by …" or "Sunset … · in
    … · dark …" forms); the "Sun set … · dark in …" form; the margin change moving the start-back time.
  - All of step 4 (Approaching within 100 m, "Arrived" alone, "Last fix N s ago").
  - The step 3 items need a session on the S22 starting before about 16:09 PDT. Step 4 needs the owner outdoors.
- **Step 3 used an approximate position only.** No GPS fix came indoors during the 90-second recording. The line
  needs only a position, and it had one.
- **The 12/24-hour setting** was not changed. The phone shows 12-hour times ("7:10 PM").
- **The HUD placement** was seen only in the "Dark since" form, with no target and no origin waypoint.
