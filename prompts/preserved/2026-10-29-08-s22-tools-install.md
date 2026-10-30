# Dispatch 2026-10-29-08: install the #440 tools build on the S22 (a helper, Opus)

Written by Rowan Waymark, 2026-10-29. RECORD -1815. The owner: "S22 plugged in", under -1813's "Yes, next time it's plugged in (Recommended)".

## Rules
These are the same as dispatch 2026-10-29-02's rules (read it on this branch):
- Database first, and never downgrade.
- Phone data stays off GitHub, in /home/zynergy-labs/s22-2026-10-29/tools/.
- GitHub only on the laptop.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- Send commands to the S22 (R5CT321008R) only; never to the S26.

## Steps
1. **Read the phone's state:**
   - build, schema and permissions;
   - Region 1 present;
   - the walk track and its 3 waypoints present.
2. **Install the tools build** from run 40961642397, artifact app-debug-apk 12937313982 (attempt 2), version 1.0.4180+gb18bd56d.
   - Check the digest first, then install with `-r` (4180 over 4175, both schema 22).
   - Confirm the version name, that schema 22 is unchanged, and that Region 1 and the track are still there.
3. **Grant by adb:**
   - `android.permission.ACTIVITY_RECOGNITION`
   - `android.permission.READ_BASIC_PHONE_STATE`
   - Confirm both read granted.
4. **Switch the walk logger on** in Settings > Diagnostics, through the app. Confirm it reads on, and that Settings shows the Diagnostics and Crash Logs rows.
5. **A short indoor check:**
   - start a recording for about 60 s and stop it;
   - confirm a walk log file was written, and give its size and its first header lines;
   - delete only that test track.
   - This shows the logger runs before the owner's walk.
6. **Leave the app on the Maps tab.** Restore any setting you changed.

## Output
docs/audits/2026-10-29-s22-tools-install.md on a branch s22-tools-install, cut from origin/records-after-173 and pushed. Your final message: the installed version, the grants, the logger's state, the test log's evidence, and the phone's state.
