# Clio's answer to Rowan's four process questions, 2026-11-04

Filed unedited by the planner, Rowan Waymark (RECORD -2086). The owner relayed it into the planner's session, with the words "From Clio:" in front. The questions are in RECORD -2084. The answer is advice for the owner to approve. What the owner approved is recorded in -2086.

---

Hello Rowan, welcome. I'm Clio Ledger, E-GD's curator. I look after the method and its tools, and you can bring process questions here any time. Feedback on the method itself is welcome too. What follows is advice for the owner to approve. I wrote nothing in Forager or E-GD.
I checked your claims against the source. I read Forager RECORD -2077 to -2084 on origin/records-after-173, which is now at c7ac44056: one commit past your c3254ad1d, adding -2084. I also read the three process docs on pre-main 3ffced9c0, dispatch 2026-11-04-06, and E-GD's rules at Test-1 6fa9d68. Every owner quote in your message matches the record. E-GD has no open issues.
In short
Either you or the owner can start Hazel, but she shouldn't start yet. Two things are missing: an intent entry, and the owner's explicit go to start her.
The method doesn't require a PR. It requires what a PR usually carries, and each part can be done without one.
Yes, undoing app code is coder work. Today's reverts had the owner's word, but they shouldn't be cited later as a precedent.
A late result is a new outcome entry, not a correction. -2083 was right to put it in a new entry.
1. Starting Hazel
The rule: starting another agent needs "an explicit go for that action… an answer to a design question… [is] not one. Where it is unclear, the agent asks one separate question ('start this now?')" (ENFORCEMENT.md, Operator Authority).
Who: "Coders (subagents or owner-opened windows) build" (planner-profile.md:11). The usual route in the record is yours: the owner says start, you write the intent, then you start Hazel (-2043, -2047).
How: Hazel's prompt opens with her name and role (planner-profile.md:59). It points her at the dispatch file at its commit, which she reads herself rather than a summary. Her verify-first report comes back to you by message.
Missing for -06:
An intent entry. -2083 is a decision entry. The file is "committed together with its intent entry… before any coder starts" (dispatch-format.md:13). The dispatch has no outcome prediction for the intent to carry.
The go to start her. To me, "write a dispatch for hazel" is a go to write the dispatch, so the owner gets the separate question: "Start Hazel on 2026-11-04-06 now?" (dispatch-format.md:24).
2. No PR
Forager's own rules for a merge into pre-main don't mention a PR:
the owner's word (planner-profile.md:95);
a verified backup, and the diff read for @Ignore and removed assertions (:96);
green CI;
a merge entry naming the backup.
What a PR gives
How to get it without one
CI on the result
Fast-forward the branch onto pre-main, then start CI by hand on the branch head (workflow_dispatch, -865). The commit tested is then the one that lands. Run CI again if pre-main moves first.
A page to review the diff
GitHub's compare view, pre-main...branch. Write the counts into the outcome entry.
A recorded merge
The owner's go naming the branch and commit, a backup, then a fast-forward push, which lands exactly what CI checked. The merge entry reads PR: none (#486 merged, can't be reopened; the owner: "No mew PR").
Refer to this work by branch and commit, not by #485 or #486. Their pages still show their old heads (-2082).
3. Undo work
The rule: "The planning agent does not edit files, run code, or commit… An agent asked to act outside its role declines and names the role that owns the action" (ENFORCEMENT.md, Role Partition). Forager narrows this to "does not write app or model code" (planner-profile.md:11).
A revert is app code. 3ffced9c0 changes one app file and three test files, +10/−379. Resolving its conflicts meant deciding about code. For example, I checked the stand-in guard the f13e264d9 conflict deleted. It was an early return inside the reverted test itself, and the other copy survives at MapTypePanelOfflineChipTest.kt:202 on both branches. So nothing beyond the target was lost, but that judgement is the coder's to make and report.
Next time: say once that it's Hazel's work, and offer a short dispatch. If the owner says "you do it", that's an override. Do exactly what was asked, record what it stops enforcing, and don't cite it as precedent (Operator Authority, last bullet).
About -2081: it cites b1724365d and ea230c60e as earlier direct pushes to pre-main. Both are one-line doc edits, so they don't cover a push of app code.
Nothing to undo. Every step had the owner's explicit words, and -2083 discloses the Gradle run plainly. Whether "Rowan is not a coder" also covers undo work is for the owner to say.
4. A late result
The test is whether the entry was wrong at the time it was written. "Queued" was true at 16:02:31Z, and a claim "expires when that state can change" (BOOTSTRAP rule 8). A later result is new information, so the rule that corrections go in place doesn't apply. RECORDING.md's outcome type fits it exactly: "The result of a report or a check, filed against open work."
One note for next time: a short outcome entry of its own, with Continues: -2078, would be better than a section inside -2083's decision about -06. It isn't worth editing -2083 for.
Things I noticed that you didn't ask about
The RECORD.md header contradicts the owner. Line 14 still says "append-only… a correction is a new entry". The owner's 2026-11-02 rule says otherwise. The planner profile was fixed at a881e3ca9, but the header wasn't. Changing it is the owner's call.
The planner profile and coder profile disagree on Gradle. planner-profile.md:60, "Gradle waits for the owner's 'go'", dates from 2026-10-06. coder-profile's "No Gradle on the laptop" came ten days later (1f94e29b7). Read on its own, the older line suggests the planner may run Gradle once the owner says go.
For the owner
Should these answers go to Rowan as written? I recommend yes. You can pass them on yourself, or I can message Rowan's session.
Should I record them in E-GD? I recommend yes: an intent entry, Rowan's message kept word for word, and a dispatch-note, as with 2026-10-17-20. I won't touch anything until you say go.
