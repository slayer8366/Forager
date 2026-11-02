# Dispatch 2026-11-01-04: sort pre-main for the release: ready, needs polish, must hide (Juniper, Opus; read only)

Written by Rowan Waymark, 2026-11-01. RECORD -1936. The owner: "If it's not ready for release, we'll keep it under the hood and offer whatever feature we can from what is finished, and then work on the rest until it's polished"; "Yes, start the sort (Recommended)".

**Bases, as written:** origin/main 0a819b3d6, origin/pre-main 13bcd0ebf. Verify both. Every claim here is a premise to check.

**Why:** everything on pre-main goes to main together, so unfinished work reaches users on promotion unless hidden first. No sort exists.

**Report, feature by feature, what a user of the Play build would newly see or get**, from main's last promoted state to pre-main, by user-visible feature and not by commit. Read what the current Play release is from RECORD, and don't assume it equals main. For each feature:
- **Sort:** ready; needs polish; or must hide. Give the reason.
- **Evidence:** the audit, review or phone check that covers it, with its path, and which build it ran on. "Merged" or "CI green" is not evidence of ready.
- **Reachable:** who calls it, and how a user gets to it. Trace it in the code at pre-main, with file and line.
- **To hide it, if it must be hidden:** the smallest honest way, and what else depends on it. Propose, don't build.
- **What a user would have to think about:** a state that looks broken, a dead end, or a control that does nothing (CLAUDE.md, "Don't make me think").

**Use, as premises:** the trail navigation plan's steps (docs/plans/2026-10-21-trail-navigation-plan-update.md), RECORD -1628, -1923, -1924, -1925; docs/process/known-bugs.md; docs/process/qa-philosophy.md; Aspen's note prompts/aspen/2026-11-01-07 (the Street v4 switch still to come). Note what the release needs from the map side, without directing it.

**Separately:** open PRs into pre-main that a feature depends on (#449, #440, #433 and #435 to #439): for the drop-off stack, say where it stands and whether anything on pre-main is unfinished without it.

**End with:** a short list, "what could ship today", "what could ship after small work", "what must be hidden", and the known bugs that should hold a promotion, with reasons.

**Disclosure:** every claim marked read, computed or inferred, with the four sections: confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope.

**Output:** docs/audits/2026-11-01-pre-main-release-sort.md on a new branch pre-main-release-sort cut from origin/records-after-173, worktree ~/.cache/forager-wt/pre-main-release-sort. Push as you go. No PR. Your final message: the three lists in plain language, then what is unknown.

## Rules
Read only: no code, no merges, no Gradle, no phone, no Pi. The Restricted Object: never read, name or quote it. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
