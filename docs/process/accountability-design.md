# Planner–coder accountability in Claude Code

Proposal, 2026-09-22. Built on Evidence Gated Development as read at E-GD-Philosophy `Test-1` @ 35e39d1: BOOTSTRAP.md, CLAUDE.md, PROTOCOL.md, OVERVIEW.md, README.md. Target repo: Forager.

Status, revised 2026-09-22: phase 1 is built on PR #114, and its three device items passed on the S22 Ultra. Decisions A to F are closed; see the end. "Revert paths" is a proposal for phase 2, and nothing in it is built.

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

## Revert paths (proposed 2026-09-22, for phase 2)

**Rule.** Nothing that changes state runs until a way back exists and is recorded. Where no way back can exist, the action belongs to the owner.

Git already covers most of the work, but only committed and pushed work. EGD's durability finding applies: an unpushed commit counts as unsaved. The gaps are everything git does not hold.

| State | Revert path | Enforced by |
|---|---|---|
| Code and records | Committed and pushed before the next step | SubagentStop report gate (phase 2) |
| Uncommitted work before a destructive git command (`reset --hard`, `checkout --`, `clean -fd`, `stash drop`, branch delete) | The hook writes a backup ref (`refs/backup/<date>-<seq>`) first, then allows the command | PreToolUse on Bash |
| App data on the test phone, debug build | Snapshot of the app's data directory through `run-as`, into the backup directory, before any device dispatch; restore on request | Device dispatch preflight |
| App data on the test phone, release build | None: `run-as` works only on a debuggable build. Switching between debug and release also needs an uninstall, because they are signed with different keys (the phase 1 install guard blocks it) | Owner only. The test phone stays on debug builds for agent work |
| Anything needed later that lives in `/tmp` or a worktree | Moved into the repo, a pushed branch, or the backup directory before the session ends | Change-of-watch checklist |
| The owner's machine settings | Copied beside the original before any edit; the owner does these edits anyway | Instruction |
| External services: email, cloud resources, anything sent | None can exist | Owner only. Enforced today for the planner and pulse only, by their allowlists. **Not enforced for the coder:** its Bash is unrestricted apart from the device and history guards, so `curl`, `gh api -X POST` or `gh pr comment` can send. See "Gap: outbound sends" below |

**Where backups live (owner, 2026-09-22).** On the local drive. The owner has the space.

- **One directory outside the repo**, for example `~/forager-backups/`, with a subfolder per backup named by date and sequence.
  - Outside the repo because snapshots of app data hold the owner's own data, and they must never reach git.
  - Not `/tmp`, which Ubuntu clears at boot.
- **An index**, `~/forager-backups/INDEX.md`, append-only. One line per backup: what it holds, what action it guards, which dispatch it belongs to, and how to restore it. A backup nobody can find is not a revert path.
- **Agents create, never delete.** A PreToolUse guard blocks any agent command that removes, moves or overwrites anything under the backup directory. A backup the agent can erase does not protect against the agent. Pruning is the owner's, by hand, when space runs short.
- **One copy, on one disk.** This is a revert path for mistakes, not an archive against disk failure. If something needs to survive a dead drive, that is a separate decision.

**Evidence.** The wipe on 2026-09-22 had no snapshot to restore, so the owner's settings were retyped by hand. `/tmp/ro-map.txt` was the only old-to-new hash map and lived where nothing survives.

**Already short of the rule.** Phase 1's dispatch told the coder to keep none of its throwaway files, and the coder complied: the raw logs of the live exercises and device runs in `/tmp` are deleted. The reports quote every guard message verbatim, so the results survive. The raw transcripts may too: Claude Code keeps its own session logs on the local drive. Check there before calling them lost, and copy what is found into the backup directory. The conflict was in the planner's dispatch, not the coder's conduct.

**Gap: outbound sends.** Two routes send things out past the owner.
- **The coder's Bash.** Pattern guards on `curl` and friends would leak the way the phase 1 bypass table predicted. The durable control is network egress limited at the sandbox level to what the coder needs (git and GitHub), if Claude Code's sandboxing supports that on the installed version. To verify, then decide in phase 2.
- **The planner dispatching a built-in agent.** The dispatch tool can start the built-in general-purpose agent, which likely gets every tool, MCP included. If so, a read-only planner could act, and send, through it. That is inferred from the docs and not yet tested. The fix belongs in phase 1, since it is decision A not holding: the dispatch hook allows only the named subagents (`coder`, `pulse`) and blocks every other agent type.

**Exception: deliberate removal.** A backup of something the owner has withdrawn keeps alive what was meant to be gone. The 2026-09-21 rewrite left exactly such copies (`/tmp/ro-rewrite.git`, a pre-rebase branch). When the owner directs a removal, the owner decides whether a backup exists and where it is held. No agent makes or keeps one, local or otherwise, and no hook creates one automatically for that operation.

**To verify before building.** Claude Code's own checkpoints cover the file edits it makes, not Bash side effects. The rule does not lean on them. Confirm their scope on the installed version anyway, so nobody assumes more coverage than they give.

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

## Closed decisions (operator, 2026-09-22)

- **A. Planner read access:** read-only. Later ruling: planner allowlist of Read, Grep, Glob, restricted Bash, Agent, Skill, WebFetch, WebSearch, AskUserQuestion, ToolSearch, TodoWrite; everything else denied, including every `mcp__*` tool.
- **B. Approval:** builds and device runs only; pulses run unapproved.
- **C. Record store:** EGD's `RECORD.md` and checkers vendored into Forager.
- **D. `.claude/` in git:** `.gitignore` narrowed to `.claude/*` with settings, agents and hooks re-included.
- **E. Existing permission allows:** untouched; precedence settled by experiment in step 0.
- **F. History rewriting is guarded:** `git filter-repo` and `git filter-branch` blocked.
- **Backups:** on the local drive, outside the repo, indexed, created by agents but never deleted by them.

## Sources

- Claude Code subagents: https://code.claude.com/docs/en/sub-agents
- Claude Code hooks: https://docs.anthropic.com/en/docs/claude-code/hooks
- EGD: https://github.com/slayer8366/E-GD-Philosophy, `Test-1` @ 35e39d1
