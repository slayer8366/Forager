# Dispatch 2026-10-24-03: the S22 check before the promotion (pre-main 073fc77bd)

**Helper:** Hazel, the app coder (a second Hazel session; the other one is on #389 and doesn't need the phone). **Planner:** Rowan. **Owner's word:** "Start now (Recommended)" (RECORD -1599), after being asked to start the S22 check on 073fc77bd.

**Phone:** the S22 (SM-S908U, adb serial R5CT321008R), no SIM, on the laptop's USB. It runs 1.0.3897+g530cbf8a (the #386 head merged into adcbedb19).

**Build:** the `app-debug-apk` artifact of CI run 40078250926 (push, pre-main 073fc77bd, green). Download it with `gh run download`. Don't build locally.

## Before installing
1. **Database version.** Read the Room database version the installed build runs at (from the device, e.g. `run-as`, or the schema JSON at 530cbf8a) and the version 073fc77bd declares (`ForagerDatabase`). If the new one is lower, stop and report: a debug build wipes the database on downgrade (-1526).
2. **Area 1 is the owner's.** Don't delete, re-download or change it. The tile budget is 1336 / 6000.
3. Install with `adb install -r`. Never uninstall.

## Steps (record each as pass, fail, or not run, with what you saw)
1. **Launch:** the app opens to the map on Trail Map with no crash. Read logcat for `FATAL` and for Forager's error lines since launch.
2. **The offline row:** download one small new test area (a few hundred tiles at most) with signal up. The row shows the count, then "Getting trail routes", then finishes with the area's trail routes stored. Then delete that test area, and only that one.
3. **Day glance:** Trail Map at a few zooms around an area with trails. Patches fill, icons draw, and nothing is blank.
4. **Night glance:** turn Night Maps on. The map and icons switch to night, with no full-screen flash after the first load. Turn it off again.
5. **Recording across a locked screen (Elder's line for #388):** with an adb test location provider, start a recording, feed a few fixes, lock the screen for a minute while still feeding, unlock, and Stop. The track's end point lands at the last fix fed, and the track shows in Records at once.
6. Leave the phone as you found it: same build installed, Night Maps off, no test area, no test location provider left on.

## Rules
- Screenshots stay off GitHub: describe what you saw in words. Positions never go into the repository; describe places generically.
- No local Gradle. Change no code, merge nothing, deploy nothing.
- Report anything you can't confirm as "not seen" or "could not determine".

## Deliver
Your report at `prompts/preserved/2026-10-24-03-s22-promotion-check-report.md` on records-after-173. Fetch, add only that file, push, and merge if the push races (never rebase). Your final message gives each step's result, the installed build's version string, the two database versions, and that the phone is free.
