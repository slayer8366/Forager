# 2026-09-26: build and device dispatches run without operator approval

This note records why `.claude/kit.json` now reads
`"approval_exempt_types": ["pulse", "build", "device"]`, what still gates
builds and device work, and how the change was checked. The work was begun by
intent `2026-09-26-29` (dispatch `prompts/preserved/2026-09-26-17.md`). That
intent was closed `superseded` by terminal `2026-09-26-33`, and the work was
finished under intent `2026-09-26-32` (dispatch
`prompts/preserved/2026-09-26-23.md`), which is where this note was written.

## 1. The owner's rulings

These are from the planner session on 2026-09-26, as the dispatches quote them:

- "Mark my approval in advance".
- "Can't have me hitting buttons. Those hooks need to change".
- Asked which types should run without approval, the owner chose **"build and
  device"**. The option read: "No prompts at all. The other guards still
  apply: role_guard, device_guard, and history_guard, which blocks merges
  without a backup, force-pushes and the like. Nothing asks before a coder
  touches the phone."
- After the classifier refused the edit to its coder (section 3), the planner
  gave the owner the edit to make, and the owner replied "Done". The edit is
  the owner's commit `2d34a44`, "kit.json: exempt build and device dispatches
  from operator approval (owner, 2026-09-26)".
- The owner's live observation, relayed by the planner: the first build
  dispatched after the edit raised "No approval message".

The owner also said "The kit has a way of dealing with this". **The planner**
reads that as `approval_exempt_types`, `guardlib.py:145-146`: "Approval fails
closed: every Type asks the operator unless it is listed here. Required, so
that every exemption is written down." This reading is the planner's, not the
owner's.

## 2. What still gates builds and device work

Only the operator-approval prompt is removed. The following still apply:

- **dispatch_guard.** It still does three things:
  - binds each Type to its target (`dispatch_guard.py:238-242`: build and
    device go only to `coder`);
  - denies a dispatch that is missing a required section (`:243-247`), or
    has an unknown Type (`:235-237`);
  - preserves every dispatch to `prompts/preserved/` before it runs
    (`:249-257`).

  All of this happens before the approval test at `:263`.
- **role_guard.** It still applies each role's tool limits.
- **device_guard.** It still applies to every device command.
- **history_guard.** It still denies force-pushes and the like. It denies a
  pull-request merge unless the caller is a coder, the command is the one
  allowed form, and a backup exists.

## 3. The classifier refusal and the owner's edit

Intent -29's step 4 was the kit.json edit. The Claude Code auto-mode
classifier refused it to that intent's coder:

> Permission for this action was denied by the Claude Code auto mode
> classifier. Reason: [Self-Modification].

Source: the -29 coder's subagent log,
`~/.claude/projects/-home-zynergy-labs-Zynergy-Forager--claude-worktrees-bridge-cse-01BcqShzosraMo4pUXkqaqRp/321c677d-f4cb-5bb7-8265-014f29328807/subagents/agent-aa7f6da473f78fa6b.jsonl`,
lines 138 and 144 (2026-09-26T05:45:33Z and 05:46:13Z). The refusal also said
not to pursue the same outcome by another route. That was followed: no coder
edited kit.json. The owner made the edit personally at `2d34a44`.

## 4. Stopped dispatches along the way

- **`prompts/preserved/2026-09-26-16.md`** stopped before any work. As the
  replacing dispatch (-17) put it, "its finish line needed
  `update_worktree.py` runs that could not succeed." It is recorded as
  dispatch-note `2026-09-26-28`.
- **`prompts/preserved/2026-09-26-18.md`**, the continuation after the
  owner's edit, stopped before writing anything, because a pulse's store copy
  appeared while it ran. It is recorded as dispatch-note `2026-09-26-30`.

## 5. Rejected alternatives

- **Keeping `device` gated.** The owner's choice of "build and device"
  rejected it.
- **Changing Claude-kit upstream.** It was rejected in favour of this
  repository's own `kit.json`, which the planner's call noted is what the gate
  reads (`guardlib.py:40`, the kit.json beside the hooks directory). The
  dispatches record no further reason.

## 6. How the change was checked

A positive control, `/tmp/approval_control/control.py` (outside the
repository), runs the worktree's real `dispatch_guard.py` through
`.claude/hooks/tests/harness.py`. It uses the worktree's `kit.json`, read at
run time, as the config, against a throwaway git repository. The base half ran
under intent -29 with `["pulse"]`. The after half ran under intent -32 with the
owner's `["pulse", "build", "device"]`:

| Case | Base (`["pulse"]`) | After (`["pulse", "build", "device"]`) |
|---|---|---|
| build → coder | ask: "Operator approval required: Type 'build' is not in approval_exempt_types." | **allow**: "Type 'build' is in approval_exempt_types, so it runs without approval." |
| device → coder | ask: "Operator approval required: Type 'device' is not in approval_exempt_types." | **allow**: "Type 'device' is in approval_exempt_types, so it runs without approval." |
| pulse → pulse | allow: "Type 'pulse' is in approval_exempt_types, so it runs without approval." | allow, same reason |
| unknown Type 'refactor' → coder | deny: "unknown Type 'refactor'. Known types: build, device, pulse." | deny, identical |
| build → pulse | deny: "Type 'build' may only be dispatched to 'coder', not 'pulse'." | deny, identical |
| build missing Checks → coder | deny: "this build dispatch is missing 1 required section(s): Checks. …" | deny, identical |

Other results from these runs:

- A `diff` of the base and after deny reasons is empty.
- Both runs put three files, `-01` to `-03`, into the temporary store.
- `ls prompts/preserved/` in the worktree was identical before and after the
  after run.

**The hook suite cannot tell before from after.**
`python3 -m unittest discover -s .claude/hooks/tests -p 'test_*.py'` ran 179
tests, OK, both before and after. That is because `harness.py` gives every
hook its own `TEST_CONFIG` (`harness.py:34`: `"approval_exempt_types":
["pulse"]`), never the repository's `kit.json`. The suite shows that the hook
code is undamaged, which is not in question since it was not edited. It is no
evidence of this change. The positive control is the discriminating check.

`check_kit.py` passes on 26 files ("PASS: 26 vendored file(s) match
.claude/kit.lock (kit v0.2).") because `kit.json` is not in `kit.lock`.

## 7. Stale wording, listed and not edited

`git grep -n -i "approv"` over `.claude/`, `CLAUDE.md` and `docs/process/`.
These lines now overstate the gate for build and device dispatches:

- `.claude/agents/coder.md:3`: "the operator approves it before it runs".
  This file is vendored.
- `docs/process/accountability-design.md:26`: "Closes decisions, approves
  dispatches (Decision B)".
- `docs/process/accountability-design.md:53`: "Operator approval, per
  Decision B."
- `docs/process/accountability-design.md:121`: "the operator's review moves
  to the approval step (Decision B)".
- `docs/process/accountability-design.md:136`: "B. Approval: builds and
  device runs only; pulses run unapproved."

These matches concern merges, not dispatch approval. They are listed for
completeness:

- `.claude/hooks/history_guard.py:54, 539, 546`: merging into a protected
  branch "is the operator's approval".
- `docs/process/accountability-design.md:71`: "Merging is the operator's
  approval."

Under the kit's branch model a coder merges builds into the working trunk (see
`2026-09-26-pre-main-working-trunk.md`), so these lines are stale in a
different way.

The remaining matches are unaffected:

- the `approval_exempt_types` code and its tests (`dispatch_guard.py`,
  `guardlib.py`, `harness.py`, `test_config.py`, `test_dispatch_guard.py`),
  with `.claude/kit.json:15` itself;
- `.claude/hooks/BYPASSES.md:31` (B-07, SendMessage);
- `.claude/hooks/role_guard.py:23-33` (planner messages and stops);
- `docs/process/accountability-autopilot.md` (scope approval under
  autopilot).

## 8. Flags

- **The main checkout** `/home/zynergy-labs/Zynergy/Forager` is on
  `claude/new-session-vto65i`, 2 commits ahead of its remote and 2 behind. It
  has an uncommitted `.gitignore` change and no `.claude/kit.json`. Its
  `.claude/` holds only `settings.local.json`, `skills` and `worktrees`: it
  has no `.claude/hooks/` and no `.claude/settings.json`. Whether a session
  rooted there runs any kit hook is unverified; this note's change does not
  reach it from that checkout. Not touched.
- **The backup directory** `~/Zynergy/forager-repo-backups` was empty, with
  no `INDEX.md`, when this work began. Its first backup is PR #120's (see the
  pre-main note).
