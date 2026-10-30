# Dispatch 2026-10-30-10: the S26 gets pre-main 3c5e63468 as the walk's comparison phone (a helper, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1848. The owner: "The S26 will be on its own version. Do you have a suggested version to test?" The planner suggested pre-main 3c5e63468 (1.0.4182+g3c5e6346, schema 22, run 41059608216, artifact app-debug-apk 12979272649). The owner: "Go". Time matters: the owner is about to walk.

## Rules
- **The S26 only:** R5GYC4CYJ3X (SM-S948U), the owner's daily driver. Never the S22.
- **Database first, never downgrade.** If the phone's schema is above 22, or its versionCode is above 4182, STOP and report. Never uninstall, never use `-d`, never clear data.
- Phone data stays off GitHub, in /home/zynergy-labs/s26-2026-10-30/.

## Steps
1. **Read the phone's state:** the installed version name and code, and the schema (`PRAGMA user_version` on a run-as copy, with its WAL). If run-as fails, the app isn't debuggable: STOP.
2. **Back up** forager.db, -wal and -shm, and run an integrity check.
3. **Check the APK:** download the artifact, check its digest, and check its signing certificate equals the installed one. If it differs, STOP.
4. **Install** with `install -r`, keeping data. Launch the app once.
5. **Confirm:** the version, the schema still 22, and the counts of tracks, waypoints, offline regions and journal entries unchanged from step 1.
6. **Leave** the app on the Maps tab.

## Output
A short final message: the version before and after, the schema, the counts, and the backup path. Write the note docs/audits/2026-10-30-s26-control-build.md afterwards, on a branch s26-control-build cut from origin/records-after-173, and push it.
