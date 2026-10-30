# Dispatch 2026-10-29-10: a visible Delete button on offline-map rows (a coder, Opus)

Written by Rowan Waymark, 2026-10-29. RECORD -1817, -1818. Cut from **pre-main 21d846b9e**. Check that this is still the remote head before you start.

**Authorisation.** The owner, verbatim:
- "Also have a way to delete downloaded offline maps."
- "Visible Delete button (Recommended)", whose option read: "Each row in Offline maps shows a trash button. The swipe still works and Undo stays."
- "Small two now, route plan next (Recommended)"

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## What exists (read at 21d846b9e; re-check each line)
- Offline-map rows are in OfflineRegionsList.kt. A short swipe reveals Delete, and a full swipe deletes (`TwoStageSwipeRow`, :67, :142).
- The delete is a pending one with an Undo snackbar: `requestDeleteOfflineRegion`, MainActivity.kt:503 and :712-715, and AvailabilityOfflineRegionDeletes.kt.
- Nothing on screen shows that the swipe exists.

## Change
- Each offline-map row shows a trash icon button. Tapping it asks for the same pending delete the swipe asks for, through the same callback, with the same Undo. Add no confirm dialog, since the swipe has none and Undo covers it.
- The swipe stays as it is.
- First list every screen that shows offline-map rows (the Offline maps panel, Records, and any other), and put the button on each. If a row has no room, or the button would conflict with a control or touch target already there, stop and report with a sketch.
- The button's screen-reader label: propose one in the PR body (for example "Delete <map name>"). The owner approves the wording before merge.
- Rows drawn over a map follow the 80% chrome rule (CLAUDE.md).

## Tests
- Through the real screen: a coordinate touch on the button, sampled at several points across its bounds (CLAUDE.md). It asserts that the row leaves, Undo shows, Undo brings the row back, and an ended snackbar deletes the map. The same holds on each screen with rows.
- The swipe's own tests still pass, untouched.
- **Fail first, then revert checks:** one per rule, with the build log checked for compile errors. Restore from a saved copy, never from git, and confirm the forward change is still present afterwards.
- **Modular rule:** new files stay under 400 lines. Split before adding to anything over 800 lines, or stop and say so.
- CI only, with no Gradle on the laptop. Open a PR into pre-main, with no merge. Put the S22 steps in the PR body.

## Rules
- Branch `offline-map-delete-button`, worktree ~/.cache/forager-wt/offline-map-delete-button.
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Past about 200k tokens: push and hand off.
- Ambiguity: stop and report.
- New words on screen are the owner's only.
