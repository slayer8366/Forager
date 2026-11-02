# Dispatch 2026-11-02-01: an S22 build with #449's Re-center fix; Re-center at a larger font, and known bug 14 proven or not (Hazel, Opus)

Written by Rowan Waymark, 2026-11-02. RECORD -1943, -1944. The owner, of the plan "Re-center at a larger font ... Bug 14: download one small throwaway area south of 15.92°N ... Then delete it and confirm your data is unchanged": "Yes, run both on the S22". Starts only once #449's Amendment 17 part 2 is green.

Read dispatches 2026-11-01-03 and 2026-11-01-02, with their rules. They stand unless this says otherwise. Every fact below is a premise to re-check.

**Arranged by category, 2026-11-02** (planner profile, "Amendments go by category"; the owner: "Apply the categories to the S22 dispatch too"). Parts 1, 2 and 3 as first written are A1, B1 and C1, unchanged in content; later rulings are added as the next item in their category.

| | Category | Status |
|---|---|---|
| **A** | Build and install | A1 done: 1.0.4398 from tools/s22-combined 070b2bb1a, data unchanged (Hazel's report) |
| **B** | Re-center at a larger font | B1 done: off the search bar at 1.0; shares space from 1.3 (Hazel's report) |
| **C** | Known bug 14, proven or not | C1 in progress |

## A. Build and install
**A1** (2026-11-02, -1944; first written as Part 1).
Worktree ~/.cache/forager-wt/tools-s22-combined, branch tools/s22-combined (aef6f01ac as written). Merge, at their heads, stating each: route-plot-ui (part 2's green head) and origin/pre-main (2bc8913f4 plus 6522e5d1e as written; it may have moved). pre-main now carries #462, Street v4: the tools branch's own Street v4 commits may now conflict or be redundant. Conflict beyond trivial: STOP. workflow_dispatch CI; compile errors first, then JUnit. Earlier builds allowed three Street v4 failures; whether they still apply after #462 is a premise to re-check, and any failure you cannot attribute: STOP, install nothing. Then install as before: read the phone's state first (a running recording or walk logger: STOP and ask), schema compared, table hashes, in-app backup saved off the phone, digest, versionCode up, signing equal, `install -r`, hashes unchanged. Nothing cleared or reset.

## B. Re-center at a larger font
**B1** (2026-11-02, -1944; first written as Part 2).
Landscape, navigating to a TEST waypoint, route card expanded at 3 and 4 stops. At font scale 1.0, then 1.3, then 2.0 if adb can set and restore it (read it back): screenshot, and give Re-center's and the card's horizontal spans in px, and whether Re-center sits on the search bar. Say at which scale, if any, the two share space on the phone. That is the question; don't propose a fix. Also confirm at 1.0 that the -1936 finding (Re-center on the search bar) is gone. Restore font scale and rotation by reading them back.

## C. Known bug 14, proven or not
**C1** (2026-11-02, -1944; first written as Part 3).
The row says: south of 15.92°N an offline download says it succeeded but holds only empty tiles; "Read from the code ... not seen on a phone". Prove it or disprove it.
- Before downloading: record the Offline Maps regions list and every table's hash.
- Pick the smallest area the picker allows, in Guatemala or southern Mexico, south of 15.92°N. Say where (to 0.1°), and its size. Name it TEST.
- **A confound:** pre-main now loads Street v4, and its relief tiles answer 404 on the live Worker (the pre-main sort, b39f3e496). A download there could end on the relief 404 rather than bug 14. Record exactly what the app says at the end, and from the log or the region's stored tiles, what it holds by zoom: zoom 8 and below, zoom 10 to 15, and any relief. Say which cause the result belongs to, or that it cannot be told.
- Open the area offline (airplane mode on, then off, read back): what the map shows at zoom 8, 10 and 13. Screenshots.
- Delete the TEST area. Confirm the regions list and every table hash equal the before state; name any that differ and why. The picker's remembered pin is known to point at a test area already; record where it ends.
- Verdict: bug 14 seen as written, seen differently (say how), or not seen.

## Output
Evidence in /home/zynergy-labs/s22-2026-11-02/, never git. Add docs/audits/2026-11-02-s22-recenter-and-bug-14.md on the tools branch, pushed. Send your planner one line after each item. Final message: merges, the CI run, version, data checks, a verdict for each item with its evidence, what was not run, the phone's end state.

## Rules
Touch only the S22. One TEST area download only. No clock changes, no video. Phone-side commands only while CI runs; no laptop Gradle beyond what Part 1 needs, under `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`. The Restricted Object: never read, name or quote it. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. No empty commits to run CI.
