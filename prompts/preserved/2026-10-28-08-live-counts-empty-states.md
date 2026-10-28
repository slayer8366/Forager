# Dispatch 2026-10-28-08: live "Used in" counts, and empty states with a next step (a coder, Opus)

Written by Rowan Waymark, 2026-10-28. RECORD -1780. Cut from **pre-main 9f2df1abb**; verify that is still the remote head. It builds on the look-only report docs/audits/2026-10-28-records-follow-ups-look.md (branch records-follow-ups, 3086c231c). Read it first.

**Authorisation.** The owner, verbatim:
- "Yes, update straight away (Recommended)"
- "No finds yet. Use New find to log one. (Recommended)"
- "\"No drafts. A find you leave before saving waits here.\" (Recommended)"
- "Yes, in the same change (Recommended)"
- "Start a coder now"

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change
1. **Live counts.** A waypoint's, a track's and an offline map's "Used in N journal entries", in its details and in its delete message, is the true number the moment a journal entry including it is saved or deleted. This covers waypoints (TrackRecordingLists.kt:51-68), tracks (:78-90) and offline maps (AvailabilityOfflineMaps.kt:111-129).
   - Propose the mechanism in your first stop, a live count or a refresh on each entry change. CLAUDE.md applies: new capability is a new path, and no conditional threaded into working code.
   - Say whether photos carry the same count, and if so include them.
2. **Finds, empty Log:** "No finds yet. Use New find to log one." This is shown only when the list is empty and loaded. A failed load keeps its own text (FindsGalleryScreen.kt:165-171).
3. **Finds, empty Drafts:** "No drafts. A find you leave before saving waits here."
4. **All, Tracks and Offline maps:** each empty line gains a next step, in the shape of the Journal's and Waypoints' lines. **Stop first:** propose the wording for these three, and wait for the planner to relay the owner's approval before building any of it. Every other part may go ahead.

## Tests
- **Counts:** the report's proposed (a) test, written to fail first through the real entry point (CartographyViewModel.onDeleteEntry, or the report screen's Delete dialog), and its save-direction mirror, for each record type. Add the (b) guard on the reference rows themselves, with a revert check on CartographyEntryDao.kt:138.
- **Each empty line:** asserted as exact text through the real Records screen, with the load-failed text unchanged.
- **Fail first, then revert checks:** one per rule, with the build log checked for compile errors. Restore from a saved copy, never from git.
- **Modular rule:** new files stay under 400 lines; split before adding to anything that would pass 800.
- CI only. Open a PR into pre-main, with no merge. Put the S22 steps in the PR body.

## Rules
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Long conversation: past about 200k tokens, push and hand off.
- Ambiguity: stop and report.
- New words on screen are the owner's only.
