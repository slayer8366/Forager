# Planner–coder accountability in Claude Code

Proposal, 2026-09-22. Built on Evidence Gated Development as read at E-GD-Philosophy `Test-1` @ 35e39d1: BOOTSTRAP.md, CLAUDE.md, PROTOCOL.md, OVERVIEW.md, README.md. Target repo: Forager. Nothing here is built yet. Three decisions (A, B, C) are open for the operator.

## The idea in one line

EGD holds the roles apart by instruction: the coder is told not to decide and the planner is told not to touch. Claude Code can hold some of those lines by capability instead, with tool restrictions and hooks that run outside the agent. Move each rule that can be enforced by the harness into the harness. Leave in instructions only what no mechanism can see.

## What it answers

Each gate below names the incident it exists for. A gate with no incident behind it is not proposed.

| Failure seen | Where | Gate |
|---|---|---|
| Planner asserts repo state from memory (EGD: 10 of 11 planner errors; this project: the rotation premise, the lock-and-glyph convention, off-by-one line numbers) | EGD RECORD.md; Forager 2026-09-21/22 | Planner gets read access (Decision A) and every dispatch is stamped with the HEAD it was written against |
| Prompt store edited or lost; dispatches existing only in chat | EGD DURABILITY.md; Forager records asked for in chat | Dispatches saved verbatim by a hook, not by either agent |
| `connectedAndroidTest` uninstalled the app and wiped the owner's data | Forager device run, 2026-09-22 | Bash guard |
| Taps landed in another app; a screenshot caught personal data | Same run | Foreground guard on every device input and screenshot |
| Records the planner asked for were never confirmed | Forager, 2026-09-22 | Coder cannot finish without its report and terminal entry committed and pushed |
| Coder decides above its authority without saying so | EGD PROTOCOL rule 6 | Required "Decisions I made" section, plus an independent auditor |

## Roles and what each can physically do

**Operator.** Closes decisions, approves dispatches (Decision B), runs hands-on device items, merges. Nothing below takes any of that away.

**Planner: the main session.**
- Allowed: Read, Grep, Glob; Bash limited to read-only git and gh (log, show, diff, status, pr view); Task, to dispatch.
- Denied: Edit, Write, and every mutating Bash command, enforced by permissions and a PreToolUse hook, not by instruction.
- Still bound by EGD: surfaces options, never closes a decision; states repository facts with the hash it read them at.

**Coder: subagent `coder`.**
- Allowed: Read, Grep, Glob, Edit, Write, Bash, including adb.
- Denied: Task (it cannot delegate), merge, force-push, and the device commands listed below.
- Validates each dispatch structurally before acting and returns missing sections by name (BOOTSTRAP, Coder).

**Pulse: subagent `pulse`.**
- Read-only, including adb reads (getprop, dumpsys, screencap under the foreground guard). No Edit or Write.
- Answers pulses that would flood the planner's context, and all device reads.

**Auditor: subagent `auditor`.**
- Read-only, with one exception: Write is allowed only to `docs/audits/*-audit.md`, enforced by a path-checking hook.
- Runs after every coder dispatch. Compares the preserved dispatch with the diff and the report: files touched outside the scope boundary; closed decisions contradicted; claims without file:line; tests reported as failing first with no pasted failure; the "Decisions I made" section empty while the diff contains choices the dispatch did not make.
- Reports findings, not verdicts. The planner adjudicates and the operator rules.

## The dispatch path

1. The planner drafts the dispatch as the Task prompt.
2. A PreToolUse hook on the dispatch tool:
   - writes the prompt verbatim to `prompts/<date>-<seq>.md`, headed with the HEAD hash and the target subagent. Neither agent writes this file.
   - checks the required sections: Role; Base and state; Scope boundary; Closed decisions; Prediction; Finish line and abort conditions; Checks; Out of scope; Device items. If any is missing, it blocks and names the missing sections to the planner.
3. Operator approval, per Decision B.
4. The subagent runs.
5. A SubagentStop hook on `coder` blocks the finish until:
   - a terminal record entry closes this dispatch's intent;
   - a report file exists with these sections: What landed (hashes); Failing-first evidence; Revert checks; What was not tested; Decisions I made; Flags outside scope;
   - the branch is pushed. EGD: an unpushed commit counts as unsaved.
   The check is structural. It cannot tell whether a section is true, and says so.
6. The planner dispatches `auditor` on the result.
7. The planner brings the report and the audit to the operator.

## Coder guards (PreToolUse on Bash)

- **Intent before action.** Edit, Write and mutating Bash are blocked unless an open intent entry exists for the current dispatch (Decision C sets where).
- **Device data.**
  - Deny `connectedAndroidTest` unless `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` is present.
  - Deny `adb uninstall`, `pm clear` and `pm uninstall`.
  - Deny install over a build with a different signature: the hook compares signatures and blocks.
- **Foreground.** Before any `adb shell input` or `screencap`, the hook reads the resumed activity and blocks unless it is Forager's.
- **History.** Deny `git push --force`, `gh pr merge` and `git merge` into main. Merging is the operator's approval.
- **Restricted Object.** Stays an instruction in CLAUDE.md:253. A hook that detects it would have to contain its name.

## What this does not catch

- A planner and operator wrong in the same way. Every gate passes and the work is still wrong (EGD PROTOCOL, "What these do not fix").
- A decision the coder did not notice making. The auditor raises the odds of catching it; it does not detect it.
- Anything the hooks' own authors got wrong. Hooks are code: each one is built failing-first and sabotage-tested like any other check.
- Tool names and hook payloads vary by Claude Code version. Everything here is checked against the installed version before it is relied on.

## Departures from EGD, recorded as such

- **Planner reads the repository.** EGD's planner cannot, and PROTOCOL rules 1 and 2 exist because of that. With read access, rule 1 becomes: assert only what you read, citing the hash. Rule 2's passback becomes the planner's own read. This is a hypothesis, and it gets measured: count planner state errors caught by the coder or the auditor before and after.
- **The planner dispatches directly.** In EGD the operator carries the prompt to the coder. Here the harness does, and the operator's review moves to the approval step (Decision B).

## Rollout, smallest first

Adopt a gate when it keeps catching things. Review any gate that catches nothing in a phase (EGD, Evidence-Based Feature Addition).

1. **Phase 1, from observed incidents only:** tool restrictions per role, dispatch preservation and section check, device guards, history guards.
2. **Phase 2:** intent gate and the coder SubagentStop gate.
3. **Phase 3:** the auditor.

Each phase is one dispatch, with the hooks sabotage-tested and the results recorded.

## Open for the operator

- **A. Planner read access.** Read-only repository access, or none as in EGD? Proposed: read-only.
- **B. Approval.** Approve every dispatch, or builds and device runs only, with read-only pulses running unapproved? Proposed: builds and device runs only.
- **C. Record store.** Vendor EGD's `RECORD.md` and `check_record.py` into Forager, as OSCam vendored its core? Or define intent and terminal entries inside Forager's existing `docs/audits/` convention? Proposed: vendor EGD's, since the intent gate needs a store a script can check. Existing audits stay where they are.

## Sources

- Claude Code subagents: https://code.claude.com/docs/en/sub-agents
- Claude Code hooks: https://docs.anthropic.com/en/docs/claude-code/hooks
- EGD: https://github.com/slayer8366/E-GD-Philosophy, `Test-1` @ 35e39d1
