# Dispatch 2026-11-05-02: The UX audit of the promotion from pre-main into main (Elder Ford, Fable)

Written by Rowan Waymark, 2026-11-05. RECORD -2132 (intent). The release is the queue the owner ruled on 2026-11-04 (-2056): "release with the current queue of tasks, let them finish and merge, release". Every fact below was read at the commits named, while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim, to "Shall I start Elder's audit now?": **"Yes"**.

## Writing rule
Describe products generically. You write no files: your report is your final message, and the planner files it.

## What you audit
- **The promotion:** pre-main **ee0651fe0** into main **c61850aa2**.
- **Counts, read with git at writing:**
  - pre-main is 663 commits ahead of main: 786 files, +530,831/-3,924, of which app/src/main is 299 files, +18,520/-2,110;
  - main is 19 commits ahead of pre-main: Worker releases and CI guards since the last promotion.
  - All 22 files main changed since the last promotion are identical on pre-main, and `git merge-tree` of the two reports no conflict.
- **The last promotion** was #393, merged as 177215135 on 2026-10-24.
- **CI:** pre-main's push run 42094523206 on ee0651fe0 was queued at writing. ee0651fe0 is two audit-index rows on top of 96d3c0c43, whose run 42089828431 passed all 8 jobs (RECORD -2131). Read the run's result yourself.

## What to read (QA philosophy, section 3)
- **CLAUDE.md, your profile** (`docs/process/auditor-profile.md`) and **`docs/process/qa-philosophy.md`**, all on pre-main.
- **The audit index rows since 2026-10-24** (`docs/audits/README.md`) and the completion reports they name.
- **The known-bugs list,** `docs/process/known-bugs.md`. The promotion names the bugs it ships with: say which of them a user would meet in this release, and which you would hold.
- **The device evidence,** in RECORD.md on records-after-173 (95f50258f or later). The S22 sessions:
  - 11: -2032 to -2037;
  - 12: -2061 to -2065, -2071;
  - 13: -2102, -2103;
  - 14: -2115, -2116;
  - 15: -2126, -2127, -2130.
  Where a behaviour was not seen on a phone, say "not seen on a phone".
- **The owner's rulings for this release,** in RECORD: the release queue (-2056); the release offers what is finished (-1925); the map type chips (-2089, -2093, -2094); the drop-off copy (-2107, -2111, -2112); and the store read (-2122, -2124). Quote them; don't restate them as rules of your own.

## Points the planner could not settle (judge them; don't decide them)
1. Whether the production build and store-listing steps set on 2026-10-17 (-1201) are part of this release.
2. Whether the owner's 2026-11-02 note "Street v4 and the trail way home need work first" (-1940) still stands against 2026-11-04's "the current queue" (-2056).
3. An intermittent test, `AvailabilityScreenCrowdedIconsSettingTest`, "…the pick survives a restart" (-2123). It passed and failed on the same commit and is not yet investigated. Say whether it bears on the promotion.

## How
Follow your profile:
1. Write your mechanism prediction first.
2. Confirm the baseline with git.
3. Walk each changed behaviour a user would meet as a step path (tap → shows → Back) against the QA philosophy, including Appendix B.

The range is large, so say what you walked and what you sampled.

## Never
- Write app code, files or tests.
- Merge.
- Touch the phone.
- Decide on the owner's behalf.
- Settle a conflict between two rulings; show both sides.
- Read, name or quote the Restricted Object.

## The verdict
Pass, pass with items, or fail, as your profile defines them. Each item gives:
- where it is;
- what the user would experience;
- the principle;
- the evidence.

End with:
- what you could not determine;
- what you only sampled.
