# Dispatch 2026-10-30-22: find and fix why #450's real-screen compass test fails once combined (a coder, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1884. The owner: "Find the cause, fix it, then build (Recommended)".

**The failure:** in the combined build tools/s22-combined (285476e69), `CompassScreenOffTest` "the Maps tab of the real screen stops reading the compass while the screen is off, and keeps the 10 s rule" (CompassScreenOffTest.kt:350) expected `30° NE` in the strip and got `""`.
- Runs: 41165385746, and a re-run of the class alone, 41166716655, same failure, 0 compile errors.
- It passes on #450 alone (41142139782).
- Read first: the report docs/audits/2026-10-30-s22-combined-build.md on origin/tools/s22-combined (9e52310f0).

## Steps
1. **Find which PR clashes.** Use scratch branches named `scratch/compass-clash-*` (delete them after), each pre-main 1ac67f12b + #450 (compass-screen-off 331c88214) + one other PR:
   - #449 (route-plot-ui bcb123bc7)
   - #448 (street-v4 e9be0bcdd)
   - #447 (trail-heights 1cc689166)
   - #440 (altitude-sea-level bb25405c0)

   Run CI filtered to CompassScreenOffTest. Read each build log for compile errors first.
2. **Find the cause** in the code, with file:line: why the strip shows "" there. Is it a real behaviour break on the screen (the strip blank after a return), or only the test's host or lookup? Say which, with evidence.
3. **Stop and report** before fixing: the clashing PR, the cause, and the proposed fix and which PR it belongs in. Also say whether merging that PR first, then the other, would make pre-main red. The planner relays it to the owner.

## Rules
- CI only, no Gradle on the laptop, no adb. gh is logged in; run gh commands bare.
- Don't push to the PR branches yet.
- Scratch goes in /mnt/work/scratch/compass-clash/.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
