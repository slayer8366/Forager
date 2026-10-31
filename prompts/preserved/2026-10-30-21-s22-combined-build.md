# Dispatch 2026-10-30-21: the combined S22 build (#447 + #440 + #448 + #449 + #450 + tools), installed with data kept (a helper, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1883. The owner's words:
- "New combined build after the fixes (Recommended)" (-1870)
- "Sorrel review, then into the S22 build (Recommended)" (-1876)
- "Yes let's do 448 also" (-1881)

## Part 1: the build (CI only; never merged, never a PR)
1. Make branch `tools/s22-combined` in worktree ~/.cache/forager-wt/tools-s22-combined, cut from origin/pre-main 1ac67f12b. Verify that head first.
2. Merge each of these in turn, at its head now (merge, not rebase):
   - trail-heights 1cc689166 (#447)
   - altitude-sea-level bb25405c0 (#440)
   - compass-screen-off 331c88214 (#450)
   - route-plot-ui bcb123bc7 (#449)
   - street-v4 e9be0bcdd (#448)

   Any conflict beyond trivial: STOP and report. Don't resolve semantics alone.
3. Bring in the tools exactly as tools/s22-447-440 did: cherry-pick 5b926c667 and c4a57389f (equal to 0997641ec and b18bd56d3).
4. Then cherry-pick e4e25e87b's one-line StreetStyle.kt change (street-v4-s22-debug), which makes the app load Street v4.
5. Push, then run CI with workflow_dispatch so the APK carries the routing key. Read the build log for compile errors first, then the JUnit totals. If CI is red: STOP, and install nothing.
6. Confirm ForagerDatabase.kt reads version 23.

## Part 2: install (S22 R5CT321008R only)
1. **Baseline:** the build (expect 1.0.4224+gc4a57389, the CI build), schema 23, Region 1 (id 3), Region 2 (id 8), the tracks, waypoints and logger.
   - Back up the databases, datastore and offline stores, and hash every table.
   - Before installing, also make an **in-app backup** through the app's own Backup screen, as the owner's safety net. Save it off the phone into the phone-data folder.
2. **Check the APK:** its digest; that its versionCode is above 4224 (stop if not; no -d); that its signing certificate equals the installed one; and that the routing key is present (never print it).
3. **Install** with `install -r` and launch. Confirm schema 23, every table hash unchanged, ACTIVITY_RECOGNITION granted, and the logger on.
4. **Leave** the app on the Maps tab.

## Rules
- Phone data goes in /home/zynergy-labs/s22-2026-10-30/combined/, never git. No coordinates in commits.
- No Gradle on the laptop. gh is logged in; run gh commands bare.
- Never send anything to the S26.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Output
Write docs/audits/2026-10-30-s22-combined-build.md on the tools branch and push it. Your final message: the merges, the CI run and totals, the version installed, and the data checks. The phone checks themselves (Sorrel's 14 for #449 and 9 for #450, and the credits glance) are a separate dispatch, after the owner has had the phone.

## Amendment 1 (2026-10-30): the rebuild (RECORD -1885)
The first build (285476e69, run 41165385746) was red on four tests. The clash was found by dispatch 2026-10-30-22: docs/audits/2026-10-30-compass-clash.md on compass-clash-report (7e17baee2). It is #440 meeting #450's test, which builds a fix with no shown height, a test-only break. The owner:
- "In #440, merge #450 first (Recommended)"
- "Yes, newest Street v4 (Recommended)"
- "Accept them as expected (Recommended)", for the three v4 failures, in this phone-only build only.

**Rebuild:**
1. On tools/s22-combined (merge, never rebase or force-push), merge street-v4 at **307d3c2ac**. It was green and CLEAN at the time of writing; check it is still the head.
2. Add the one-line test fix at CompassScreenOffTest.kt:309 on this branch: `AvailabilityUiState(liveFix = fix)` → `AvailabilityUiState(liveFix = fix).withFixHeight()`. It lands in #440 itself later.
3. Run CI with workflow_dispatch. **Allowed failures are exactly these three:** StreetStyleTest.kt:77, OwnStreetStyleSourceTest.kt:78 and NightExceptionsV4Test.kt (the "every other style keeps v1 to v3" case). Any other failure, or any change in which three fail: STOP.
4. Then Part 2 as written: a raw backup, an in-app backup, the APK checks (versionCode above 4224, signing, routing key), `install -r`, the data checks, the logger, and leaving the app on the Maps tab.

## Amendment 2 (2026-10-31): bring in #449's new card layout (RECORD -1888)
The owner: "Go ahead and add the change to the S22 test build".
1. **Wait** until route-plot-ui's Amendment 6 head has a green full CI run on the PR. It was bce9c2de0, with run 41179955025 in progress at the time of writing; use whatever head the #449 coder reports green. If it goes red, wait for the coder's fix; don't take a red head.
2. **Merge** that route-plot-ui head into tools/s22-combined (merge, never rebase or force-push).
3. **Run CI** with workflow_dispatch. The allowed failures are exactly the same three v4 tests as Amendment 1. Anything else: STOP.
4. **Part 2 again:**
   - a raw backup, and a new in-app backup through the app;
   - the APK checks: versionCode above 4275, signing, and the routing key;
   - `install -r`, with every table hash unchanged;
   - the logger on, and the app left on the Maps tab.
