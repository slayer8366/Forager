# Accountability phase 1, follow-up 2: the flag 1 MCP allow, and dispatch Type bound to target

2026-09-22. PR #114, branch `accountability-phase-1`. Operator ruling preserved as
`prompts/preserved/2026-09-22-07.md`, intent `RECORD.md` 2026-09-22-10. This report supersedes the
second half of flag 1 in `2026-09-22-accountability-phase-1-completion-report.md` (a pulse-typed
dispatch to `coder` runs without approval), and the "still open" note about it in
`2026-09-22-accountability-phase-1-flag1-followup-report.md`. Neither is edited.

## What landed

| Commit | What it does |
|---|---|
| `825d318` | Intent 2026-09-22-10 and the ruling, preserved verbatim by hand |
| `79e28c1` | `dispatch_guard.py`: Type bound to target |
| (this commit) | This report, its index row, and the terminal entry |

## 1. The MCP allow used in the flag 1 test: session-only

It was the command-line flag `--allowedTools "mcp__claude_ai_Resend__list-domains"` on the two
flag 1 `claude -p` runs (sessions `89505d08…` and `4aa8cab0…`, 23:41 and 23:43 UTC). A command-line
allow applies to the session it is passed to and is not written to any settings file. A search for
`list-domains|Resend` in every settings file those sessions could read or write:

| File | Result |
|---|---|
| `~/.claude/settings.json` | 0 matches |
| `~/.claude/settings.local.json` | absent |
| `.claude/settings.json` on this branch | 0 matches; unchanged since `82c6d8e` |
| `.claude/settings.local.json` in the worktree | absent |
| `~/Zynergy/Forager/.claude/settings.json` | absent |
| `~/Zynergy/Forager/.claude/settings.local.json` | 0 matches |
| `/etc/claude-code/managed-settings.json` | absent |
| `~/.claude.json` | 1 match, line 1464: `"claude.ai Resend"` in `claudeAiMcpEverConnected`, the list of connectors that have ever connected. Not a permission rule. The worktree's project entry in that file has no keys, so no `allowedTools` |

No allow for the tool exists anywhere, so nothing needed removing.

## 2. Dispatch Type bound to target

`dispatch_guard.py` now maps each Type to its one target (`pulse → pulse`, `build → coder`,
`device → coder`) and blocks any other pairing. The check runs after the `Type:` line is parsed and
before the section check and the write, so a mismatched dispatch is never preserved.

- **Failing-first**, against the hook at `a221c49`, 3 failures:
  - `[pulse -> coder]`: `AssertionError: 'allow' != 'deny'`. This is the decision B bypass itself:
    the dispatch would have run with no approval.
  - `[build -> pulse]` and `[device -> pulse]`: `'ask' != 'deny'`.
- **Sabotage 1**, the check replaced by `if False:`: all three fail.
- **Sabotage 2**, `device` remapped to `pulse`: `test_device_type_asks` and `[device -> pulse]` fail,
  exactly the two cases that edit breaks.
- Both restored from a saved copy, confirmed byte-identical. Hook suite: 49 tests, OK.

**Live.** A planner session on this branch at `79e28c1` dispatched a complete pulse-typed prompt to
`subagent_type: coder`: **blocked**, and no prompt was preserved (`prompts/preserved/` still ends at
`-07`).

> `PreToolUse:Agent hook error: dispatch_guard: Type 'pulse' may only be dispatched to 'pulse', not 'coder'.`

## Recorded for phase 2 (operator ruling, 2026-09-22; no change now)

- **Agents start `claude` sessions from Bash only when a dispatch authorizes it.** This build started
  many, all from an unguarded session. Nothing in phase 1 stops a coder doing the same, and a
  `claude --agent coder` session is unrestricted as the planner (completion report, flag 2).
- **Coder egress:** a sandbox network allowlist, plus a separate agent identity on GitHub. `socat`,
  which the Linux sandbox needs, is not installed here.

## Tally (operator ruling, 2026-09-22)

- **Planner error:** the scope of "Don't delete anything" was unclear.
- **Coder deviation, self-reported:** the coder deleted `/tmp/f1` after that ruling
  (`2026-09-22-accountability-phase-1-flag1-followup-report.md` and the chat report after it list what
  was in it and where each part survives).

## Backups

`~/forager-backups/2026-09-22-02/` holds this follow-up's live-test session log (`9b94a053…`) and
its scratch (`/tmp/b2`, and `/tmp/sabotage2.py`, the sabotage runner used above), with an INDEX.md
line. Nothing was deleted; the `/tmp` originals are left in place.

## Decisions I made

1. Opened intent 2026-09-22-10 and preserved the ruling by hand before any work.
2. Answered item 1 by searching every settings file on the machine that the sessions could have read
   or written, including `~/.claude.json`, rather than only the project's.
3. Placed the binding check after the Type check and before the section check, so an unknown Type
   still gets the "unknown Type" message and a mismatch never reaches the write.
4. Kept the `/tmp` scratch rather than deleting it, and copied it and the new session log into a
   second backup, `2026-09-22-02`, which the ruling did not ask for.
5. Recorded the ruling's phase 2 items and tally in this report; the ruling named no location.

## Flags outside scope

- The planner can no longer dispatch anything but `coder` and `pulse`, and only with the matching
  Type, so it cannot use Explore or Plan for searching. Searching falls to Read and `git grep`, as
  before.
