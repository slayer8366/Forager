# Dispatch 2026-10-24-05: the recording line clears on Stop (into this promotion)

**Helper:** Hazel (the session that ran 2026-10-24-03 and -04). **Planner:** Rowan. **Owner's word:** "Fix it in this promotion (Recommended)" (RECORD -1602).

**The finding** (`prompts/preserved/2026-10-24-04-stale-recording-line-report.md`, f5d54a02e; planner re-read at pre-main 073fc77bd): `clearRecordingState()`'s `_uiState.update` in `ui/track/TrackRecordingViewModel.kt` resets every recording field except `breadcrumbPoints`, so the dotted recording line stays on the map after Stop (from the app or the notification) until the next Record or the process ends. `TrackRecordingUiState.kt` documents the list as "Empty whenever [activeTrack] is null".

**The behaviour, as a step path:** Record → walk → Stop (in the app, or from the notification) → the dotted recording line is gone from the map at once. The saved track's own line shows as it does today.

**Base:** a new branch `recording-line-clears` from current origin/pre-main (verify against the remote). File ownership: you own `TrackRecordingViewModel.kt` and its test class for this dispatch; the other Hazel on #389 touches neither (checked by the planner).

**Build:**
1. A test that fails first, through the real entry point: record, let the poller read points so `breadcrumbPoints` is non-empty, Stop, assert it is empty. Cover both Stop paths (the app's and the notification's). The existing assertion at `TrackRecordingViewModelTest.kt:316` runs before any point is read and can't fail; say so in the test's doc, or fold it into the new one. Any new test in that class goes through `runRecordingTest` (CLAUDE.md, the stalled-run pitfall).
2. The fix: `breadcrumbPoints = emptyList()` in that update. Nothing else.
3. A revert check: remove the fix line, read the build log for `e:` lines, confirm only the new tests fail, restore from a saved copy, and confirm the fix is still present.

**Rules:** CI only, no local Gradle. Tests fail first. Merge, never rebase. Commits end "Co-Authored-By: Claude <noreply@anthropic.com>".

**Deliver:** open a PR into pre-main and stop there. Sorrel reviews, then an S22 re-check of both Stop paths, and the merge waits for the owner's word. Your final message gives the PR number, head, the CI runs (fail-first, green, revert) and anything left open.
