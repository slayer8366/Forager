# RECORD.md

Forager's record of dispatched work. Each piece of work opens with an intent
entry, written before anything is built, stating what will change, where the
change stops, what it is expected to do, and when it counts as finished or
abandoned. It closes with exactly one terminal entry saying what happened.
Dispatches that open no intent (a pulse, a build the operator declined, a
live exercise of the dispatch hook) are recorded by a dispatch note, so every
preserved prompt under `prompts/preserved/` is accounted for by some entry.

This file is append-only. Nothing already committed is edited or removed; a
correction is a new entry that points at the one it corrects.

What makes an entry well formed is defined in `check_record.py`, and how
entries bind to preserved prompts in `check_prompts.py`, not here. Run both
before committing a change to this file. The existing records in
`docs/audits/` stay where they are and are not migrated into this store.

Design: `docs/process/accountability-design.md`.

## Entries

---

**Kind:** intent
**ID:** 2026-09-22-01
**Timestamp:** 2026-09-22T21:12:30Z
**Title:** Accountability setup, phase 1, dispatch version 1
**Dispatch-file:** preserved/2026-09-22-01.md
**Change:** Build phase 1 of `docs/process/accountability-design.md`: the record store, the planner's tool restrictions, `coder` and `pulse` subagents, the dispatch hook, the device guards and the history guards.
**Scope boundary:** Phase 1 only, as listed in the dispatch's Scope boundary section. No app code, no strip stack, no migration of `docs/audits/`, nothing from the autopilot addendum.
**Baseline:** origin/main 89f53a4f288d59aeec4f3deb44e0103c15ce13b7
**Closed decisions:** A. Planner read-only (Read, Grep, Glob, read-only git and gh Bash, the dispatch tool; Edit, Write and mutating Bash denied). B. Operator approval for build and device dispatches; pulse dispatches run unasked. C. EGD's record store vendored (RECORD.md, check_record.py, check_prompts.py); docs/audits/ not migrated. Gates are hooks and tool restrictions, not instructions. The Restricted Object rule stays an instruction in CLAUDE.md; no hook references it.
**Planner prediction (stated in the dispatch, not withheld):** 1. Decision A is achievable on the installed version only through a hook that identifies the caller, not through settings alone. 2. Pattern-matched Bash guards are bypassable; at least one of sh -c, a variable, bash script.sh, xargs, or a gradle task alias gets past at least one guard. 3. Every hook passes its sabotage test.
**Prediction (outcome — planner):** not authored
**Prediction (mechanism — coder):** not authored
**Finish line:** Steps 0 to 6 committed and pushed; every hook has a failing-first test and a sabotage result; bypass table complete; live exercises run; a terminal entry closes the intent; check_record.py and check_prompts.py pass on the committed tree after the terminal entry; a PR open against main.
**Abort conditions:** The step 0 stop condition (a main-session deny also denies the coder and no hook input field identifies the caller). Any guard that, tested live, lets a real destructive command execute. Two failed fixes on one hook, after which only data gathering.
**Notes:** Written after the fact, during the execution of version 2 (entry 2026-09-22-03), so that the version this session first stopped on is recorded and its preserved prompt is claimed. Version 1 was delivered in chat to an unrestricted Claude Code session; no hook existed to preserve it. The coder's mechanism prediction is `not authored` because version 1 stopped at pre-flight, before step 1, where the dispatch places it.

---

**Kind:** terminal
**ID:** 2026-09-22-02
**Timestamp:** 2026-09-22T21:12:30Z
**Closes:** 2026-09-22-01
**Outcome:** superseded
**Superseded-by:** 2026-09-22-03
**Observed:** Stopped at pre-flight before writing anything. Two findings: `.claude/` in the main checkout already held a settings file (`settings.local.json`, allowing `git fetch *`, `git push *` and one CLAUDE.md commit), the dispatch's stop condition for existing settings; and `.gitignore:34` ignored all of `.claude/`, so no phase 1 file under it could be committed. The operator ruled on both (decisions D and E) and added F; the planner reissued the dispatch as version 2.
**Deviations:** None. Nothing was built under version 1.

---

**Kind:** intent
**ID:** 2026-09-22-03
**Timestamp:** 2026-09-22T21:12:30Z
**Title:** Accountability setup, phase 1, dispatch version 2
**Dispatch-file:** preserved/2026-09-22-02.md
**Change:** As 2026-09-22-01, plus decisions D, E and F and the operator's rulings recorded below.
**Scope boundary:** Phase 1 only, as listed in the dispatch's Scope boundary section, extended by the rulings below. No app code, no strip stack, no migration of `docs/audits/`, nothing from the autopilot addendum. CLAUDE.md gains one short section only.
**Baseline:** origin/main 89f53a4f288d59aeec4f3deb44e0103c15ce13b7, branch accountability-phase-1
**Closed decisions:** A to C as in 2026-09-22-01. D. Narrow `.gitignore:34` to `.claude/*` and re-include `settings.json`, `agents/` and `hooks/`; `settings.local.json`, `skills/` and `worktrees/` stay ignored. E. Existing local and user allows are not edited; step 0 settles precedence by experiment. F. `git filter-repo` and `git filter-branch` blocked in step 6.
**Operator rulings during execution (2026-09-22):** (1) The dispatch hook writes to `prompts/preserved/<date>-<seq>.md`. (2) Two dispatch versions are recorded as a supersede chain: 01 intent v1, 02 terminal superseding it, 03 intent v2. (3) A new entry kind, `dispatch-note` (fields Dispatch-file, Type, Outcome of answered, declined or exercise, and Report as a path or "none"), claims exactly one preserved prompt, opens and closes nothing, and has no prediction or finish line; check_prompts.py accepts it as a claim and check_record.py validates it; the first commit of every build is a sweep writing one note per unclaimed preserved prompt; built failing-first with its diff from upstream listed. (4) The planner's allowlist is Read, Grep, Glob, restricted Bash, Agent, Skill, WebFetch, WebSearch, AskUserQuestion, ToolSearch and TodoWrite; every other tool is denied, including every mcp__ tool and NotebookEdit, and unknown tools are denied by default. (5) Step 3 adds one harmless read-only MCP call attempted from each of coder and pulse, expected unavailable; if it runs, stop. (6) The build runs in a separate worktree, pushed as it goes.
**Planner prediction (stated in the dispatch, not withheld):** As in 2026-09-22-01.
**Prediction (outcome — planner):** not authored
**Prediction (mechanism — coder):** Written after step 0 and before any hook exists. Step 0 already observed the planner's first prediction (settings deny rules reach subagents; `agent_type` is absent in the main session and set in a subagent), so it is recorded there as an observation, not predicted here. For the build: (a) the device and history guards match patterns against the whole command text, so `sh -c '...'` and a variable whose assignment spells out the command are both caught, because the forbidden text still appears in the command; (b) `bash script.sh` gets past every device and history guard, since the script's contents never reach the hook; (c) a gradle task abbreviation such as `cAT` gets past the connectedAndroidTest guard; (d) `xargs` with the forbidden words split across the pipe (`echo --force | xargs git push`) gets past the force-push guard; (e) the planner guard is an allowlist and blocks all five variants, because `sh`, `bash`, `xargs` and variable assignment are not read-only git or gh; (f) every hook fails its sabotage test; (g) check_prompts.py reports the live-exercise prompts as unclaimed until dispatch notes are written for them; (h) the adb install signature guard cannot be exercised live without a connected device, and will be evidenced by unit tests only.
**Finish line:** As in 2026-09-22-01.
**Abort conditions:** As in 2026-09-22-01, plus step 0's precedence stop: any local or user allow beating a project deny.
**Notes:** Bootstrap exception: both prompt files under `prompts/preserved/` were saved by the coder by hand, byte-identical to what the operator supplied, because the dispatch hook that will do this does not exist yet. From step 4 on, the hook writes them.

---

**Kind:** dispatch-note
**ID:** 2026-09-22-04
**Dispatch-file:** preserved/2026-09-22-03.md
**Type:** pulse
**Outcome:** exercise
**Report:** docs/audits/2026-09-22-accountability-phase-1-completion-report.md
**Notes:** Live exercise for 2026-09-22-03: a complete pulse dispatch, sent from a planner session on this branch, run without an approval prompt as decision B requires. The dispatch hook wrote this prompt. The pulse's one Bash read was refused by Claude Code's ordinary permission prompt in `-p` mode, not by a guard.

---

**Kind:** dispatch-note
**ID:** 2026-09-22-05
**Dispatch-file:** preserved/2026-09-22-04.md
**Type:** build
**Outcome:** exercise
**Report:** docs/audits/2026-09-22-accountability-phase-1-completion-report.md
**Notes:** Live exercise for 2026-09-22-03: a complete build dispatch from an interactive planner session. The dispatch hook wrote this prompt and returned ask; the approval prompt appeared and was declined by the coder running the exercise (Esc), not by the operator. Nothing ran.

---

**Kind:** terminal
**ID:** 2026-09-22-06
**Timestamp:** 2026-09-22T21:45:14Z
**Closes:** 2026-09-22-03
**Outcome:** completed
**Observed:** Steps 0 to 6 built and pushed on accountability-phase-1 (a0cdf02 through 82c6d8e, then 1e16526, 16bbb40 and the commit carrying this entry). Step 0: dispatch tool is Agent; settings denies reach subagents; agent_type identifies the caller; hook "ask" prompts interactively and blocks in -p; hooks load mid-session; no local or user allow beat a project deny. Every hook has a failing-first run and sabotage runs; four sabotage runs survived at first, all test gaps, fixed in the tests. Bypass table complete. Non-device live exercises run: planner Write blocked; a build missing Device items blocked by name; a complete pulse ran unasked; a complete build prompted and was declined; connectedAndroidTest --dry-run, git push --force --dry-run and git filter-repo --analyze (in a /tmp clone) blocked by their guards. Planner prediction: 1 and 2 confirmed, 3 confirmed as committed but not first time. Coder prediction: (a) partly wrong, (b) to (h) confirmed. Detail: docs/audits/2026-09-22-accountability-phase-1-completion-report.md.
**Deviations:** Device items 1 to 3 and the two adb live exercises not run: no phone connected; the operator ruled to finish without them, with merge blocked until they pass. Decisions made beyond the dispatch are listed in the report's "Decisions I made", including more than one commit per step and the coder-side live exercises run as claude --agent coder sessions. Autopilot addendum replaced by v2 (reference only) per operator ruling.

---

**Kind:** intent
**ID:** 2026-09-22-07
**Timestamp:** 2026-09-22T23:41:12Z
**Title:** PR #114 follow-up: test flag 1 (built-in agents), fix if it holds, find transcripts, check sandbox egress
**Dispatch-file:** preserved/2026-09-22-05.md
**Change:** (1) Live test: a planner session on this branch dispatches the built-in general-purpose agent with one harmless read-only MCP call. (2) Only if it runs: dispatch_guard.py allows only subagent types coder and pulse and blocks every other type, built-ins included; failing-first, sabotage-tested, then the same dispatch live, expected blocked. (3) Look in Claude Code's local session logs for the live-exercise and device-run sessions, copy anything found into ~/forager-backups/ with an INDEX.md line. (4) Report whether Claude Code 2.1.280's sandboxing can limit network egress, with the source.
**Scope boundary:** .claude/hooks/dispatch_guard.py and its tests; RECORD.md; prompts/preserved/; one docs/audits/ report and its index row; ~/forager-backups/ (create and copy only). No change to coder egress, no app code, nothing deleted anywhere.
**Baseline:** accountability-phase-1 at ca4c778
**Prediction (outcome — planner):** not authored
**Prediction (mechanism — coder):** The general-purpose dispatch passes the dispatch hook, because the hook checks the Type line and sections but not the target, and role_guard does not restrict subagents other than pulse; inside it, the MCP tool is reachable (general-purpose inherits every tool) and the list call runs. The fix is a check on tool_input.subagent_type before anything is written; a call with no subagent_type is blocked too. The child sessions' transcripts are likely absent: the interactive child sessions displayed "Transcript saving is off — inherited CLAUDE_CODE_CHILD_SESSION marker".
**Finish line:** The flag 1 result recorded; if the fix was needed, it is built failing-first, sabotage-tested, and confirmed blocked live; transcript findings reported and anything found copied and indexed; the egress answer reported with its source; a report and index row committed and pushed; a terminal entry closes this intent; both checkers pass on the committed tree.
**Abort conditions:** The live test's MCP call would do anything other than a read. Two failed fixes on the hook, after which only data gathering. Anything under ~/forager-backups/ would need deleting or overwriting.

---

**Kind:** dispatch-note
**ID:** 2026-09-22-08
**Dispatch-file:** preserved/2026-09-22-06.md
**Type:** pulse
**Outcome:** exercise
**Report:** docs/audits/2026-09-22-accountability-phase-1-flag1-followup-report.md
**Notes:** Flag 1 live test for 2026-09-22-07, before the fix: a pulse-typed dispatch sent to the built-in general-purpose agent, not to pulse. The dispatch hook preserved and allowed it, and the agent ran an MCP read. The same dispatch after 47512e2 was blocked and not preserved.

---

**Kind:** terminal
**ID:** 2026-09-22-09
**Timestamp:** 2026-09-22T23:45:49Z
**Closes:** 2026-09-22-07
**Outcome:** completed
**Observed:** Flag 1 was real: a planner session dispatched general-purpose and it ran mcp__claude_ai_Resend__list-domains. Fixed in 47512e2 (only coder and pulse may be dispatched), failing-first (7 failures, 'allow' != 'deny'), sabotage-tested twice, and blocked live afterwards. 26 session-log files found under ~/.claude/projects/ and copied to ~/forager-backups/2026-09-22-01/ with an INDEX.md line; the three interactive sessions were never logged. Sandbox network egress: supported per the sandboxing docs (allowedDomains, strictAllowlist from user/managed/CLI settings only), not tried; socat missing here. Mechanism prediction: the dispatch ran and the MCP call ran, as predicted; the interactive transcripts were absent, as predicted, but the -p ones were present, which the prediction did not say.
**Deviations:** The flag 1 test used --allowedTools for the one MCP tool. Nothing deleted. Coder egress unchanged, per the ruling.

---

**Kind:** intent
**ID:** 2026-09-22-10
**Timestamp:** 2026-09-22T23:58:44Z
**Title:** PR #114 follow-up 2: confirm the flag 1 MCP allow is gone; bind dispatch Type to target
**Dispatch-file:** preserved/2026-09-22-07.md
**Change:** (1) Establish where the MCP allow used in the flag 1 test lives, citing file and line or stating it was session-only. (2) dispatch_guard.py: Type pulse only to pulse, Type build or device only to coder, any mismatch blocked; failing-first, sabotage-tested, then live with a pulse-typed dispatch to coder, expected blocked.
**Scope boundary:** .claude/hooks/dispatch_guard.py and its tests; RECORD.md; prompts/preserved/; one docs/audits/ report and its index row. No settings file is edited; nothing is deleted. The ruling's phase 2 items and tally are recorded, not acted on.
**Baseline:** accountability-phase-1 at a221c49
**Prediction (outcome — planner):** not authored
**Prediction (mechanism — coder):** (1) The allow was the --allowedTools CLI flag, so it lived only in those two sessions; no settings file names the tool. (2) Today a pulse-typed dispatch to coder is allowed without approval, because the hook checks target membership and Type separately and never together; the binding check, placed before anything is written, blocks it and leaves no preserved prompt.
**Finish line:** Item 1 answered with evidence; the binding built failing-first, sabotaged, and blocked live; report and index row committed and pushed; a terminal entry closes this intent; both checkers pass on the committed tree.
**Abort conditions:** Two failed fixes on the hook, after which only data gathering. Anything requiring a settings edit or a deletion.

---

**Kind:** terminal
**ID:** 2026-09-22-11
**Timestamp:** 2026-09-23T00:01:11Z
**Closes:** 2026-09-22-10
**Outcome:** completed
**Observed:** (1) The flag 1 MCP allow was the --allowedTools CLI flag, session-only; no settings file on the machine allows the tool (the one match, ~/.claude.json:1464, is the claudeAiMcpEverConnected list). (2) Type bound to target in 79e28c1: failing-first (pulse -> coder 'allow' != 'deny', the bypass; build/device -> pulse 'ask' != 'deny'), sabotaged twice, and a live pulse-typed dispatch to coder was blocked with nothing preserved. Mechanism prediction held on both counts.
**Deviations:** None from the ruling. Beyond it: a second backup (2026-09-22-02) of the new session log and the /tmp scratch, which were not deleted.

---

**Kind:** intent
**ID:** 2026-09-23-01
**Timestamp:** 2026-09-23T02:22:57Z
**Title:** Record the owner's authorization and the route of the PR #114 merge; back up its evidence
**Dispatch-file:** preserved/2026-09-23-01.md
**Change:** A record of who authorized the PR #114 merge, by what route it ran, and what the history guard does with that route; the session log copied and /tmp/kitprobe moved into ~/forager-backups/, each with an INDEX.md line.
**Scope boundary:** RECORD.md and prompts/preserved/ on a branch cut from main at b2435ef; ~/forager-backups/ (create, copy and move in only). No hook, test or other file changes.
**Baseline:** origin/main b2435ef
**Prediction (outcome — planner):** not authored
**Prediction (mechanism — coder):** not authored
**Finish line:** The terminal entry below states the authorization, the route and the guard behaviour with sources; both backups exist and are indexed; both checkers pass; the branch is pushed.
**Abort conditions:** Anything under ~/forager-backups/ would need deleting or overwriting.

---

**Kind:** terminal
**ID:** 2026-09-23-02
**Timestamp:** 2026-09-23T02:22:57Z
**Closes:** 2026-09-23-01
**Outcome:** completed
**Observed:** Authorization: the owner authorized the PR #114 merge, in the session log ~/.claude/projects/-home-zynergy-labs-Zynergy-Forager--claude-worktrees-bridge-cse-019uZR3mJKzHkDaCv5fGnsuk/1b897011-9b08-5256-851d-e0b3c0141e2e.jsonl at line 1121 (backup copy: ~/forager-backups/2026-09-23-01/, same file name, same line). Route: a REST PUT to repos/slayer8366/Forager/pulls/114/merge (merge_method=merge, pinned to head 77c19dd), run by a Claude Code session in the worktree .claude/worktrees/bridge-cse_019uZR3mJKzHkDaCv5fGnsuk, which carries no .claude/ hooks, so no guard ran. GitHub records the merge as slayer8366 at 2026-09-23T01:18:42Z, merge commit b2435ef, indistinguishable from a merge made by hand. The history guard passes gh api merges even when present: run on b2435ef's hooks with the payload gh api -X PUT repos/o/r/pulls/1/merge -f merge_method=merge, history_guard.py returned no decision for the coder; it blocks only the literal gh pr merge (.claude/hooks/history_guard.py:31). role_guard.py denied the same command for the planner, because gh api is given -X. Backups: the session log copied to ~/forager-backups/2026-09-23-01/ (1265 lines, sha256 ebc3b21a1c496a06…, a byte-prefix of the live log); /tmp/kitprobe moved to ~/forager-backups/2026-09-23-02/kitprobe (25 files, hashes identical before and after, MANIFEST.sha256 beside it); both indexed in ~/forager-backups/INDEX.md.
**Deviations:** The first attempt to copy the session log failed (a directory name starting with - broke dirname) after its INDEX.md line had already been appended with a blank line count and hash. INDEX.md is append-only, so that line stands and a correction line after it records the real copy. The gh api merge gap is recorded, not fixed.

---

**Kind:** intent
**ID:** 2026-09-23-03
**Timestamp:** 2026-09-23T02:23:20Z
**Title:** Correct a line citation in 2026-09-23-02
**Change:** Record that 2026-09-23-02 cites the gh pr merge pattern at .claude/hooks/history_guard.py:31; it is at :30.
**Scope boundary:** RECORD.md only.
**Baseline:** record-pr114-merge at 1f35033
**Prediction (outcome — planner):** not authored
**Prediction (mechanism — coder):** not authored
**Finish line:** A terminal entry states the correct line; both checkers pass.
**Abort conditions:** None beyond the checkers failing.

---

**Kind:** terminal
**ID:** 2026-09-23-04
**Timestamp:** 2026-09-23T02:23:20Z
**Closes:** 2026-09-23-03
**Outcome:** completed
**Observed:** At b2435ef, git grep puts PR_MERGE, the only pattern that blocks a merge command in history_guard.py, at .claude/hooks/history_guard.py:30, not :31 as 2026-09-23-02 says. Everything else in 2026-09-23-02 stands. The same wrong line was given in chat, in the kit-extraction pulse answer of 2026-09-23 (Question 7).
**Deviations:** None. It carries out item 1 of the ruling preserved as preserved/2026-09-23-01.md, which 2026-09-23-01 claims; one prompt cannot be claimed twice, so this intent claims none.

---

**Kind:** intent
**ID:** 2026-09-23-05
**Timestamp:** 2026-09-23T03:57:52Z
**Title:** Merge d55-artifact-contract into main in slayer8366/forager-forecast
**Dispatch-file:** preserved/2026-09-23-02.md
**Change:** One no-ff merge commit of d55-artifact-contract (2d8cc8f) into forager-forecast main (82f28b6), made in a temporary worktree of ~/Zynergy/forager-forecast-t0b cut from origin/main, pushed to origin/main, the worktree then removed. The merge message quotes the owner's authorisation, "I authorize merging d55-artifact-contract into main", with its time, as D40 and D50 require.
**Scope boundary:** In forager-forecast: that one merge commit and its push; no file edited, no branch deleted, nothing written in ~/Zynergy/forager-forecast. In Forager: RECORD.md and prompts/preserved/ on branch worktree-bridge-cse_01Md1NYxk9qgG8y6g7CiSm3y, pushed to that branch, not merged into Forager main.
**Baseline:** forager-forecast origin/main 82f28b65e1c46148c0518e30b411428ee0a7e2bb, origin/d55-artifact-contract 2d8cc8f9988899c5cc9a0d31e89147a0bbfe216f (both verified by git fetch before this entry; 0 behind, 5 ahead); Forager branch at 624173b
**Prediction (outcome — planner):** The merge is clean; its tree equals 2d8cc8f^{tree}; git diff 82f28b6 against the merge names exactly the six files; DECISIONS.md on the result has D60 as its highest row.
**Prediction (mechanism — coder):** Because origin/main is an ancestor of the branch, a --no-ff merge has nothing to combine: git takes the branch tree as it is and adds only a commit with two parents, so the tree check and the six-file diff pass by construction. The only hook set is .githooks/pre-commit, which git merge does not run (a merge runs pre-merge-commit, and none exists), so no hook bears on it. The audit index row count on the result equals the branch's.
**Finish line:** origin/main is the new merge commit with parents 82f28b6 and 2d8cc8f; the temporary worktree is removed; this intent is closed by a terminal entry; both checkers pass; this branch is pushed; a short report is delivered.
**Abort conditions:** origin/main is not 82f28b6 or origin/d55-artifact-contract is not 2d8cc8f at merge time; the merge conflicts; the tree check fails; the secret check finds anything; the push is rejected (no force-push).

---

**Kind:** dispatch-note
**ID:** 2026-09-23-06
**Dispatch-file:** preserved/2026-09-23-03.md
**Type:** pulse
**Outcome:** answered
**Report:** none: the pulse ended without a hand-back; the planner received no report

---

**Kind:** intent
**ID:** 2026-09-23-07
**Timestamp:** 2026-09-23T04:09:27Z
**Title:** Resume: merge d55-artifact-contract into main in slayer8366/forager-forecast
**Dispatch-file:** preserved/2026-09-23-04.md
**Change:** One --no-ff merge commit of d55-artifact-contract (2d8cc8f) into forager-forecast main (82f28b6), made in a new temporary worktree of ~/Zynergy/forager-forecast-t0b cut from origin/main, pushed to origin/main, the worktree then removed. The merge message is the D40 report under D50: it quotes the owner's authorisation, "I authorize merging d55-artifact-contract into main", given in chat on 2026-09-22; recorded as about 20:49 PDT, when the first merge dispatch reached the coder (2026-09-23T03:49:10Z, the Preserved line of preserved/2026-09-23-02.md). Supersedes 2026-09-23-05, which stopped before touching forager-forecast.
**Scope boundary:** In forager-forecast: that one merge commit and its push to origin/main, and the temporary worktree's creation and removal; no file edited, no branch deleted, nothing written in ~/Zynergy/forager-forecast. In Forager: RECORD.md and prompts/preserved/ on branch worktree-bridge-cse_01Md1NYxk9qgG8y6g7CiSm3y, pushed to that branch, not merged into Forager main.
**Baseline:** forager-forecast origin/main 82f28b65e1c46148c0518e30b411428ee0a7e2bb, origin/d55-artifact-contract 2d8cc8f9988899c5cc9a0d31e89147a0bbfe216f (re-verified by git fetch in t0b before this entry; is-ancestor exit 0; 0 behind, 5 ahead; 6 files differ); Forager branch at dcb2a4b
**Prediction (outcome — planner):** The merge is clean; its tree equals 2d8cc8f^{tree}; git diff --name-only 82f28b6 against the merge lists exactly the 6 files; the highest DECISIONS.md row on the result is D60; the audit index row count on the result equals the branch's; both Forager checkers pass once 03, 05, 02 and 04 are all claimed.
**Prediction (mechanism — coder):** origin/main is an ancestor of the branch, so the --no-ff merge has no three-way content to combine: git records the branch's tree unchanged under a commit with two parents, which makes the tree check, the 6-file diff, the D60 row and the index row count all hold by construction. No merge hook fires in the temporary worktree (a merge runs pre-merge-commit only, and if one is configured it will show in the output). The secret check finds 0 because the branch's own history was pushed without it and the merge adds no content.
**Finish line:** origin/main in forager-forecast is a merge commit with parents 82f28b6 and 2d8cc8f; the temporary worktree is removed; 2026-09-23-05 is closed as superseded by this intent; this intent is closed by a terminal entry; both checkers pass; this branch is pushed; a report is delivered.
**Abort conditions:** Either tip has moved at merge time; the merge conflicts; the tree check fails; the secret check hits; a checker fails for a reason other than the four prompts 02, 03, 04 and 05's claim; the push is rejected (never force-push); another new unclaimed prompt appears in prompts/preserved/.

---

**Kind:** terminal
**ID:** 2026-09-23-08
**Timestamp:** 2026-09-23T04:09:27Z
**Closes:** 2026-09-23-05
**Outcome:** superseded
**Superseded-by:** 2026-09-23-07
**Observed:** The first merge coder appended 2026-09-23-05 and stopped before touching forager-forecast: check_prompts.py failed on the unclaimed preserved/2026-09-23-03.md. Nothing was merged or pushed in forager-forecast under 2026-09-23-05. The owner ruled that 03 is recorded as an answered dispatch-note (2026-09-23-06) and that the merge resumes under the dispatch preserved as preserved/2026-09-23-04.md, intent 2026-09-23-07.
**Deviations:** 2026-09-23-05 was committed with its text unchanged; its closing '---' separator was missing and was added in dcb2a4b so that the following entry parses separately.

---

**Kind:** dispatch-note
**ID:** 2026-09-23-09
**Dispatch-file:** preserved/2026-09-23-05.md
**Type:** build
**Outcome:** declined
**Report:** none; the coder stopped before any work because the hook edit's authorisation reached it only through the planner; see the planner's chat of 2026-09-22

---

**Kind:** dispatch-note
**ID:** 2026-09-23-10
**Dispatch-file:** preserved/2026-09-23-06.md
**Type:** build
**Outcome:** declined
**Report:** none; the coder stopped before its intent at scope item 3 (no request shape for the daily-statistics datasets) and handed the gap back; superseded by revision 2

---
