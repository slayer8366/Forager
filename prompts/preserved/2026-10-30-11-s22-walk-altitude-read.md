# Dispatch 2026-10-30-11: read the owner's 10-30 walk on the S22 for altitude (#440) (a helper, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1851. The owner: "Walk done, S22 plugged in". Asked what to look into: "Altitude (#440)". Only that. The way home, elevation and the S26 are not in this read.

## Read first
- Dispatch 2026-10-29-06 (the 10-29 walk read), whose method this follows.
- The #440 rulings: RECORD -1796/-1797 (the pass mark is within 10 m of the known ground height), -1808/-1809 (Amendment 4: steadying kept for 10 minutes across the barometer's stops, and a 10 s hold).
- docs/audits/2026-10-30-s22-tools-447-440.md on tools/s22-447-440: the build, and its walk logger.

## The phone
- **S22 only:** R5CT321008R, on 1.0.4224+gc4a57389, schema 23. Never the S26. Never uninstall, downgrade or clear data.
- Copy evidence to /home/zynergy-labs/s22-2026-10-30/walk/, never git. Keep coordinates and addresses out of the report.

## Steps
1. **Before anything else:** check whether a recording is running. If it is, ask the planner before stopping it.
2. **Copy off:** the database (with its WAL), today's walk log file(s) from the logger, the app's logcat lines and any screen recordings from today.
3. **Find the walk:** its track, start and end times, and length. Was it on slopes or flat (from the barometer and GPS heights)?
4. **Altitude against the ground:**
   - Compare the shown height above sea level along the walk (stored points, and the logger's barometer and GPS records) with the known ground height.
   - For ground truth, use the trail data's heights. Region 1's route data is from 10-27 and likely has none, so if the walk left Region 1, download one small offline area over the walk on Wi-Fi through the app's own flow, which gets the 10-28+ data with heights. Read its heights at the walk's points.
   - Pass mark: within 10 m.
   - Report the spread, the worst points and where they happened (time and kind of place, not coordinates).
5. **Behaviour checks from the log:**
   - the first minute (plain GPS, then the blend);
   - steadiness while walking;
   - any barometer stops and how the height carried across them (Amendment 4's 10-minute rule and 10 s hold);
   - any converter errors.
6. **Say plainly what passed, what failed and what the data cannot tell.**

## Last
- Delete only what you added: the downloaded area, and any exported file.
- Keep Region 1, the old walk track and today's walk.
- Restore any setting you changed, and leave the phone on the Maps tab.

## Output
Write docs/audits/2026-10-30-s22-walk-altitude.md on a branch s22-walk-altitude, cut from origin/records-after-173, and push it. Your final message: the verdict against the 10 m mark, with the figures, and anything for the owner.

## Rules
- gh is logged in. No Gradle.
- Put scratch outside /tmp and the scratchpad, both of which are near their quota. Use /home/zynergy-labs/s22-2026-10-30/walk/scratch/.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- Ambiguity: stop and report.
