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

## Amendment 1 (2026-10-29): answers to Hazel's findings (RECORD -1820)
- **Entry editor's "In this entry" panel:** the owner: "No, Records only (Recommended)". No button there.
- **Label:** the owner: "\"Delete <map name>\" (Recommended)". Approved.
- **Stale text:** the owner: "Fix wording only (Recommended)". Rename JournalPendingDeleteTest.kt:573's title and correct the TwoStageSwipe.kt comment so both say the row has no *text* Delete control at rest. The assertion stays byte-identical; show this in the diff.
- **Tools:** `gh` is installed and logged in on this laptop. The earlier "no gh" was the planner's error.

## Amendment 2 (2026-10-30): the old no-Delete guard (RECORD -1823)
Run 41006039562 on 38c5f1075 failed 1 of 5910 tests: AvailabilityScreenSettingsPanelTest.kt:686, at lines 699-700, which assert there are zero clickable "Delete" nodes on a row at rest. It found the new trash button. This is J4b L5's guard, written for the old design. The owner: "Exactly one: the trash (Recommended)".
- Change lines 699-700 to assert exactly one clickable node whose description contains "Delete": the trash button, with its tag and the label "Delete <map name>". The text and swipe checks in that test stay unchanged.
- Match the TwoStageSwipe.kt:162 comment to it.
- Show the new assertion failing with a second delete control added (a revert check, with the build log checked), and that it passes forward.
- Run the full suite on the new head. Report the diff of the test lines in the completion report.

## Amendment 3 (2026-10-30): Sorrel's review (RECORD -1828)
Sorrel's review (Fable), docs/reviews/2026-10-30-sorrel-pr443-pr444.md (sorrel-pr443-pr444, 43b89516e), has no S findings. The owner's answers:
- **N1, swiped-open row:** "Hide trash while open (Recommended)". While a row is swiped open, only the red Delete shows; the trash button comes back when the row closes. Fail first through the real swipe, then a revert check. Coordinate touches.
- **N2:** rename the test title at AvailabilityScreenSettingsPanelTest.kt:688 so it matches "exactly one, the trash button". Wording only.
- **N6:** add one landscape glance to the PR body's S22 step 1.
- **Order:** "Merge both, then S22 (Recommended)". When #443 is green on its new code head, the planner merges it and #444 into pre-main. The S22 checks the combined build.
CI only. Push to #443 and report.
