# Follow-up to the 2026-11-10 engineering audit of main

**Status: a plan, nothing dispatched, nothing built.** Written 2026-11-10 by the planner (Rowan Waymark) at the owner's request: "Load the planner profile. Write up a plan on how to handle the issues that surfaced." Filed on the owner's word: "No split. Record, add to bugs list, bring step 1 questions." RECORD -2203.

**The audit:** `docs/audits/2026-11-10-main-engineering-audit.md`. Finding numbers below (H1, M1 to M24) are its own.

**This is a claim about the past.** The audit read `main` at `137b1427c`. `pre-main` was `947f1be2e` and differed in three files only (two workflow files and the release runbook), so the app findings held on the branch where fixes land. Each dispatch re-reads its own premises before it builds. Every finding came from reading code; none was reproduced, on a phone or in a test.

## What was not checked when this was written

- The known-bugs rows 1 to 29 were read for overlap with the audit's high and medium findings, and none was seen. Each dispatch re-checks.
- Two earlier planner sessions were listed as idle. Neither was messaged.
- About 113 files in `ui/availability` and 74 in `ui/log` and its neighbours were not read by the audit (step 3).

## Step 0. Record first (done with this file)

The audit under `docs/audits/` with its index row, this plan, and the user-facing findings as rows 30 to 48 of `docs/process/known-bugs.md`. All on pre-main, since main changes only by promotion.

## Step 1. The owner's rulings, before any dispatch

Six findings are decisions, not bugs. They are brought as plain questions, and the answers are recorded at the end of this file.

| Finding | The decision |
|---|---|
| H1 | What the Maps tab shows when a sightings search fails or is loading, and whether the fix is urgent |
| M11 | Whether a failed or cancelled backup may delete a file that existed before the run |
| M15 | What the user is told when a map style fails to load |
| M20 | Whether the position sent to iNaturalist and Open-Meteo is rounded |
| M21, M22, M23 | Whether direct workflow commits to main and the `version_code` publish path are standing exceptions to the promotion rule. The owner's words on the publish path, from commit `71ff5590c`: "re-upload the same build to Play in the correct place" |
| No LICENSE file | Which licence, if any |

A seventh, raised by the filing itself: whether the audit's held parts (M18 and the identifier lists) go into this public repository before they are fixed.

## Step 2. Build in six batches, in this order

Each batch is one or more dispatches to Hazel Cairnwright on pre-main: read and report first, see the failure, then fix. Sorrel Tally reviews every app PR before any build reaches the owner.

1. **H1, the Maps tab.** It is in the live build. The planner profile lets an urgent fix start from main and go to the front of the promotion queue; whether H1 is urgent is the owner's call.
2. **Lost records and crashes.** M7 (typing overwritten during a photo attach), M8 (a failed save shown as saved), M9 (save-and-leave loses the edit), M10 (GPX share crash), M2 (dropped track points), M12 (planned-trip throw), and M11 once ruled.
3. **Wrong position, direction or time.** M1 (recording with Location off), M3 (service restart), M4 (one-shot accuracy), M5 (sundown line), M6 (switchback placement), M16 (centre pin).
4. **Search, downloads and map state.** M13 (stale search result), M14 (second download), M15 once ruled. M17 (map lifecycle calls) goes to Juniper Sounding first, to read the map library's source, because its effect is inferred.
5. **Server and privacy.** M18, M19 (coordinates in the log line), M20 once ruled; the stale README and beta text; the User-Agent line missing from the policy; the identifiers in the tree.
6. **Low findings, as sweeps.** One dispatch for unlogged fallbacks, one for locale formatting, one for dead code. The inferred lows get a read-and-report first. Whatever is not built stays listed.

M24 (unverified downloads in the release jobs) follows the rulings on M21 to M23, since it touches the same workflow files.

## Step 3. Close the coverage gap

A second read of the files the audit did not reach, including the navigation card and most camera UI, run alongside batch 1.

## Step 4. Promote

Each batch goes through the usual gate: CI green on pre-main at the exact commit, Elder Ford's audit, the owner's approval. No batch as planned changes the database, so no upgrade check. For H1 and batches 2 and 3 an S22 check is proposed for what a person sees.

## Constraints on the order

- **One coder per file.** `AvailabilityViewModel.kt`, `TrackRecordingService.kt` and `MushroomLogViewModel.kt` each appear in more than one batch, so those batches run one after another.
- **Files over the split mark.** `AvailabilityCompactScaffold.kt`, `JournalTab.kt` and `TrackRecordingViewModel.kt` are over 800 lines. A bug fix is not a feature, but a coder reports if a fix grows one of them.
- **No split.** The one-function-per-file split is not done (the owner: "No split.").

## Size

No time estimate is given. Roughly 12 to 15 dispatches across the six batches; batches 1 and 2 are the ones not to delay.

## The owner's rulings

Given 2026-11-11T05:54:33Z, each to a question with options; the owner's choice is quoted, and what it chose is the option's own text. RECORD -2203.

| Question | The owner | What that option said |
|---|---|---|
| The audit's held parts | "Keep out until fixed (Recommended)" | They stay out of the public repo, saved to a private file on the owner's computer, and are filed after each one is fixed |
| H1, the screen | "Map stays, auto retry (Recommended)" | Search fails > map, tabs and search bar stay > a line over the map says the search failed, with Try again > it also retries by itself when signal returns. Loading > map stays, small progress mark |
| H1, how fast | "Urgent, from main (Recommended)" | Hazel first reproduces it in a test. If it is real, the fix starts from main, passes the usual gate at the front of the queue, and ships as its own release |
| M11, backup over an existing file | "Old backup survives (Recommended)" | The old file is only replaced once the new backup has finished. On failure or cancel it is left exactly as it was |
| M15, a style fails to load | "Switch to Trail Map, say so (Recommended)" | Style fails > the map switches to Trail Map by itself > a short line says the chosen map couldn't load. The user's chosen map comes back on the next successful load |
| M20, position sent with a search | "Round to about 1 km (Recommended)" | Two decimal places. The exact spot is no longer sent |
| M21 to M23, the release rules | "Both stay, written as exceptions" | The rule is reworded: workflow-only commits may go straight to main, and a bundle already in Play may be published by number. Nothing else changes |
| No LICENSE file | "All rights reserved notice (Recommended)" | A short file stating the code is not licensed for reuse |

**Changed by the owner (2026-11-11T05:55:15Z), the licence:** "I'll figure a real license once I get the iOS port figured out". No licence file or notice is added now; the "All rights reserved notice (Recommended)" answer above no longer stands. The licence is the owner's to raise after the iOS port.

**What these change in the plan.** Batch 1 (H1) starts from main, not pre-main. M23 and M24 are not settled by the release-rules answer: M23 (the release environment allows pre-main) is now part of the written exception's wording to check, and M24 (unverified downloads) is still open work. The wording of the reworded rule, of the two notices (H1, M15) comes back to the owner before it is used. None of this is dispatched: the owner's word so far covers recording and the questions only.
