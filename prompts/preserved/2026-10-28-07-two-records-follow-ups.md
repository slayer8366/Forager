# Dispatch 2026-10-28-07: two Records follow-ups, look-only (a coder, Opus)

Written by Rowan Waymark, 2026-10-28. RECORD -1779. The owner: "Yes, look into both after #432"; then "Yes, look-only first (Recommended)". Cut from **pre-main 9f2df1abb** (#432's merge). Verify that it is still the remote head.

**Look-only.** Read the code and report the cause of each. Change no app code, and don't use the phone. Every claim needs a file:line, or is marked unverified. CLAUDE.md: check who calls a path before saying what it does.

## 1. A stale "used in 1 journal entry"
From the S22 round 2 report (docs/audits/2026-10-28-s22-pr432-round2.md on branch s22-pr432-r2): a journal entry that included Waypoint 1 was deleted. Afterwards, Waypoint 1's sheet and its delete snackbar still said "used in 1 journal entry". A database copy taken after the waypoint was also deleted showed every entry-reference table at 0, so it can't say which of these happened:
- (a) the screen held a stale count, never re-read after the entry delete;
- (b) the reference row outlived the entry until the waypoint went.
CartographyEntryDao.kt:138 removes an entry's waypoint refs on entry delete. In round 3 the snackbar read just "Waypoint deleted" (one sighting).
- Trace how the count reaches the sheet and the snackbar: the query, whether it is a Flow or a one-shot read, and what it is keyed on.
- Trace the entry-delete path: is it one transaction with the ref delete? Does the entry delete go through the DAO at :138 from every caller?
- Say which of (a) or (b) the code allows, or both, and the exact steps that would reproduce it. Propose a failing test for each. Do not write them yet.

## 2. Finds in Records shows a blank list
Records opened on the Finds filter shows a blank list with no empty-state line, while the Waypoints filter has one.
- Find each filter's empty state and why Finds has none.
- List every Records filter and whether it has an empty state, with the text of each.
- Propose the Finds line in the same shape as its siblings. The wording is the owner's call, so give one or two options. Follow "Honest copy plus a next step": say what the user can do, not just that the list is empty.

## Output
docs/audits/2026-10-28-records-follow-ups-look.md on a branch records-follow-ups cut from pre-main 9f2df1abb, pushed. Your final message gives the findings and any questions for the owner, in plain behaviour.

## Rules
CI only, and no Gradle on the laptop. Network: GitHub only. The Restricted Object: never read, name or quote it. Commits end `Co-Authored-By: Claude <noreply@anthropic.com>` with no model name. Ambiguity: stop and report.
