# Dispatch 2026-10-30-12: the S22 gets the CI build of c4a57389 (routing key), Region 2's trails are fetched, then a look-only read of the screen-off redraws (a helper, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1855. The owner:
- "Yes, then re-fetch Region 2 trails (Recommended)", whose option read: "Install CI run 41107156444's build over the laptop one, check the key works, and fetch Region 2's trail data. Your data stays."
- "Look-only investigation (Recommended)", whose option read: "A helper finds what redraws with the screen off, whether pre-main does it too, and reports. No code until you decide."

## Rules
- S22 only: R5CT321008R. Never the S26.
- Never uninstall, downgrade or clear data.
- Phone data goes in /home/zynergy-labs/s22-2026-10-30/ci-build/, never git. No coordinates or addresses in anything committed.
- No Gradle, no code changes.
- Scratch goes under /home/zynergy-labs/s22-2026-10-30/ci-build/scratch/ (/tmp is near its quota).

## Part 1: the CI build
1. **Baseline:** the build (expect 1.0.4224+gc4a57389, the laptop build), schema 23, Region 1, Region 2 (route data state=missing, reason=no_app_key), the tracks and the logger. Back up forager.db with its WAL.
2. **Download** CI run 41107156444's app-debug-apk and check its digest. Read its versionName and versionCode with aapt or apkanalyzer.
   - If its versionCode is below 4224, `install -r` would refuse. Then STOP and report: don't use -d without the planner's word.
   - Check its signing certificate equals the installed one.
3. **Confirm the key:** check the CI APK carries the routing key, the way the build config injects it, without printing the key. Confirm the installed laptop APK does not.
4. **Install** with `install -r` and launch. Confirm schema 23 and every table's data unchanged (hash as before).
5. **Region 2's trails:** fetch its route data through the app's own flow (the retry or "download again" path the Offline maps screen offers), on Wi-Fi. Confirm its state reads stored, the run id, and that its points carry heights.
6. **Logger** on, ACTIVITY_RECOGNITION granted, and the app left on the Maps tab.

## Part 2: screen-off redraws, look only
The 10-30 walk read logged about 30 redraw lines a second with the screen off, through the whole walk and again at 13:00 with no recording, with memory freed every ~25 s.
1. **Find the source:** reproduce on the S22 (screen off for 2 minutes with the app foregrounded last, then with it backgrounded, both recording and not), and read the logcat tags and lines.
2. **Map the lines to code.** Which part of the app, or of MapLibre or Compose, asks for frames with the screen off?
3. **Is it new?** Was it caused by #440, #447 or the tools, or is it already on pre-main? Answer from the code path, and from any older log in /home/zynergy-labs/s22-2026-10-2*/ (the 10-27 to 10-29 evidence) showing the same lines on older builds.
4. **Cost:** if possible, battery or CPU time per minute with the screen off (dumpsys batterystats or cpuinfo), with and without it.
5. **No fix.** Report the cause, its age and the cost, with file:line where known.

## Output
Write docs/audits/2026-10-30-s22-ci-build-and-redraws.md on a branch s22-ci-build-redraws, cut from origin/records-after-173, and push it. Your final message: Part 1's state, then Part 2's findings.
