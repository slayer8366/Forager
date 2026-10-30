# Dispatch 2026-10-30-16: rescore the 10-30 altitude replay against the new bare-earth heights (a helper, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1865. The owner: "Yes, rescore (Recommended)", whose option read: "A helper reruns the comparison on the saved walk data against the new heights. No phone needed. It gives a real verdict against your 10 m mark."

**Read first:** docs/audits/2026-10-30-s22-walk-altitude.md (s22-walk-altitude 2afa09e58); the "Region 2 redone" section of docs/audits/2026-10-30-s22-ci-build-and-redraws.md (s22-ci-build-redraws 18f5edec3); prompts/aspen/2026-10-30-07 on records-after-173 (why the old file reads high under trees).

**Do:**
- Rerun dispatch -11's scoring on the saved evidence in /home/zynergy-labs/s22-2026-10-30/walk/. The ground is now the new W125_N45.rd5 from Region 2 (id 8), saved under /home/zynergy-labs/s22-2026-10-30/region-2/, in place of the 10-03 file.
- Report the same table: replayed strip, plain sea-level GPS and raw GPS, giving within 10 m, mean, sd and worst. Use the same matched points, and say if the count changes.
- **Pass mark:** within 10 m (-1796). Give a verdict, and the places that miss (time and kind of place, never coordinates).
- Note that the walk was wooded and sloped; Aspen expects about 1-2 m against GPS on open ground.
- **No phone, no Gradle.** Scratch goes under /home/zynergy-labs/s22-2026-10-30/walk/scratch/rescore/.

**Output:** add a section "Rescored on 3DEP" to docs/audits/2026-10-30-s22-walk-altitude.md on branch s22-walk-altitude, and push. Your final message: the verdict and the table.

## Rules
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- The Restricted Object: never read, name or quote it.
