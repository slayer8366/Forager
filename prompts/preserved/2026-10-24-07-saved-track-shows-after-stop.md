# Dispatch 2026-10-24-07: the saved track shows on the map right after Stop (into #391, this promotion)

**Helper:** Hazel, a fresh session. **Planner:** Rowan. **Owner's word:** "Fix the gap too, first (Recommended)" (RECORD -1604).

**The finding** (`prompts/preserved/2026-10-24-06-s22-recording-line-recheck-report.md`, 454e712c6): on the S22 with #391's build, after Stop in the app the dotted recording line goes at once (the fix works), but the saved track's solid line does not appear for 30 s and more, until the user changes tab and comes back. The same delay was seen on pre-main 073fc77bd in the 2026-10-24-04 check, where the stale dotted line hid it. Whether Stop from the notification shows the same delay was not recorded.

**The behaviour, as a step path:** Record → walk → Stop (in the app, or from the notification) → the dotted line is gone and the saved track's solid line is on the map at once (Tracks layer on), with no tab change. A brief moment while the track saves is fine; seconds are not.

## Part 1: find the cause (before any code)
- Trace what the map's Tracks layer draws from and what refreshes it: the reader, its trigger, and why a tab change refreshes it while Stop does not. Name file:line for each step. Check reachability from production first (CLAUDE.md, Testing).
- Note what -1527 / PR #364 ("a finished track shows in Records right after Stop") and #367 changed; Records may refresh on Stop while the map does not.
- On the S22 (R5CT321008R, build 1.0.3904+gb4206e22 installed, area 1 untouched; whole-second timestamps on test fixes; the test providers have twice been removed by something unknown, so re-add and log if needed), confirm the delay for both Stop paths.
- **Stop and report to the planner, without code,** if the fix touches more than the recording screen's ViewModel or collaborators, the map-data refresh and their tests, or if it would change what any other screen shows.

## Part 2: the fix
- On branch `recording-line-clears` (#391). First verify it against the remote; merge, never rebase. You own the files you name in part 1 for this dispatch.
- A test that fails first through the real entry point (the screen's Stop, and the notification's as the service reports it) and asserts the map's track data includes the finished track without a tab change. New tests in `TrackRecordingViewModelTest` go through `runRecordingTest`.
- The smallest fix. A revert check: read the build log for `e:` lines first, check each failure is one this revert causes, restore from a saved copy, and confirm the fix is still present.
- A green full CI run. Re-run a known-bugs row 2 flake once at most, and name it.

**Rules:** CI only, no local Gradle. Commits end "Co-Authored-By: Claude <noreply@anthropic.com>". Screenshots off GitHub; no positions in the repository. Merge nothing.

**Deliver:** push to #391 and add a comment to the PR with the cause, the fix and the runs. Then the S22: both Stop paths show the solid line at once, after installing the new PR APK (check the database version first). Leave the phone as found. Your report at `prompts/preserved/2026-10-24-07-saved-track-shows-after-stop-report.md` on records-after-173 (add only that file; merge, never rebase). Your final message gives the cause, the fix, the runs, the phone results, and that the phone is free.
