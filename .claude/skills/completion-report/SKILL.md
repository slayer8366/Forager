---
name: completion-report
description: The structure of a report that lets someone decide whether work is done without re-reading the diff — what landed with commit hashes, verification before building, evidence, suite counts, and what was not tested stated plainly. Use this whenever you finish a task and are about to describe what you did, whenever a task asked you to verify something before building it, and whenever your work will be reviewed by someone who was not watching. Use it even for small changes, since the parts that get skipped on small changes are exactly the parts that matter later.
---

# Completion report

The reader was not watching. They need to decide whether the work is done, whether the evidence supports that, and what is still open — without reading the diff.

Written well, the report is also the record. Six months later it is what explains why the code looks the way it does.

## Structure

**What landed.** Commit hashes, and for each commit one line on what it does. Push state and whether the tree is clean. If a branch moved under you, say so and say which commit you measured at.

**Verification before building.** When the task asked you to verify something first, report it before the fix, not after. The reader needs to know what you found before they read what you did about it, because sometimes the finding should have changed the plan.

This is also where a wrong premise in the task gets stated. Name it, show the line that disproves it, move on. Do not bury it.

**What was built.** What changed, in prose, not a file list. Name what you deliberately did not touch — that list is often as informative as the change itself.

**Evidence.** Tests added or reworked and what each holds. Revert checks: which edit, which test, what message. Suite counts before and after, failures, skipped. Build exit code. Any check whose green state you constructed a positive control for, and what the control showed.

**Not tested, said plainly.** The most valuable section and the easiest to omit. What could not be verified here, why, and what would verify it. "This needs a device" and "this needs credentials I do not have" and "I did not read that file" are all acceptable; silence is not.

**Device or environment items.** Enumerated separately, phrased as things someone can do: where to tap, what to look at, what each outcome would mean. If both outcomes are informative, say so — "rewritten confirms the mechanism; kept means the earlier result needs another explanation" tells the tester they cannot fail.

## Tone

Lead with what is true, not with what was hard. The reader wants the state of the work, not a narrative of how it got there.

Corrections to your own earlier record belong in the report, not hidden in a commit message. When something you previously stated turns out wrong, withdraw it explicitly and say what replaced it. When the earlier claim was never published, edit it directly and say that is what you did, because there was no published claim to withdraw.

## Suite counts are a claim like any other

"0 failures" means something only if the suite ran the cases that could fail. When a fix cannot break any existing test — because no fixture contains the relevant case — say so, since the green is not evidence for the fix.

When a host has known failures, compare by test ID rather than by count. Matching counts are not matching identities, and a regression that arrives while an unrelated failure disappears keeps the count stable.

## What not to do

Do not report a fix as done when the evidence is that you read the code and it looks right. That is a verification, not a confirmation, and the difference has cost real time: a capture path can assign a value correctly at every hop and still be overridden downstream by a library or a platform, leaving the reading true and the behaviour wrong.

Do not describe a device-only item as though it were verified. Do not let a summary sentence claim more than the section above it supports.
