# Accountability phase 1, follow-up: flag 1 fixed, transcripts found, sandbox egress

2026-09-22. PR #114, branch `accountability-phase-1`. Operator ruling preserved as
`prompts/preserved/2026-09-22-05.md`, intent `RECORD.md` 2026-09-22-07. This report supersedes flag 1
("the planner can dispatch a writing subagent") in
`2026-09-22-accountability-phase-1-completion-report.md`; that report is not edited.

## What landed

| Commit | What it does |
|---|---|
| `ca4c778` | The operator's design revision replaces `docs/process/accountability-design.md`, unmodified. "Revert paths" is a phase 2 proposal; nothing in it is built |
| `e171444` | Intent 2026-09-22-07 and the operator's ruling, preserved verbatim by hand (it arrived in chat, to a session without the dispatch hook) |
| `47512e2` | `dispatch_guard.py`: only `coder` and `pulse` may be dispatched |
| (this commit) | The dispatch note for the flag 1 test's preserved prompt, this report, its index row, and the terminal entry |

## Flag 1: it was real, and it is fixed

**Test, before the fix.** A planner session (`claude -p`, main session, so `role_guard.py` applied)
on this branch at `e171444` dispatched `subagent_type: general-purpose` with a complete pulse-typed
prompt (`prompts/preserved/2026-09-22-06.md`) asking for one call to
`mcp__claude_ai_Resend__list-domains`, a read. The dispatch hook allowed it, because it checked the
`Type:` line and the sections but never the target. The agent loaded the tool with ToolSearch and
**the call ran**, returning the account's one domain (`mail.zynergy-labs.com`, status
`partially_failed`, sending and receiving enabled). The agent listed its own tools as including
Agent, Bash, Edit, Read, Skill, ToolSearch and Write. So a read-only planner could act, and send,
through a built-in agent: decision A did not hold.

One condition of the test, recorded so it is not over-read: the session was started with
`--allowedTools mcp__claude_ai_Resend__list-domains`, so Claude Code's ordinary permission prompt did
not stand in the way. Without it, that prompt (a person in an interactive session; an automatic
refusal in `-p`) was the only thing between the planner and the call. No guard was.

**Fix (`47512e2`).** `dispatch_guard.py` now checks `tool_input.subagent_type` against
`{coder, pulse}` before anything else, so a blocked dispatch is never preserved; a call with no
`subagent_type` is blocked too.

- Failing-first, against the hook as it was: 7 failures, all `AssertionError: 'allow' != 'deny'`
  (`general-purpose`, `Explore`, `Plan`, `statusline-setup`, `claude`, `auditor`, and a missing
  `subagent_type`).
- Sabotage 1, the check replaced by `if False:`: the same 7 fail.
- Sabotage 2, `general-purpose` added back to the allowed set: `[general-purpose]` fails, and so do
  the later subtests, not independently: the allowed dispatch preserved a file, which their
  nothing-written assertion then saw. The failure specific to that edit is the first one.
- Both restored from a saved copy, confirmed byte-identical. Hook suite: 48 tests, OK.

**Live, after the fix.** The same planner session command, same prompt, same `--allowedTools`:
blocked, and nothing was preserved.

> `PreToolUse:Agent hook error: dispatch_guard: subagent type 'general-purpose' may not be dispatched. Only coder, pulse may; built-in agent types are blocked.`

**Still open, not in this ruling:** the dispatch hook still does not tie Type to target, so a
dispatch typed `pulse` but sent to `coder` runs without approval (completion report, flag 1, second
half). A `claude --agent coder` main session is still unrestricted as the planner (flag 2).

## Transcripts

Claude Code's local session logs are under `~/.claude/projects/<encoded working directory>/`, one
JSONL file per session, with a `subagents/` folder per session holding each subagent's transcript.
Found, for the sessions of this work (timestamps UTC, from each log's first line):

| Started | Session | What it was |
|---|---|---|
| 20:36:04 | `-tmp-s0-work/4ee1f378…` (+1 subagent) | Step 0 precedence run |
| 20:37:00 | `-tmp-s0-work/429619f0…` (+1 subagent) | Step 0 second pass: subagent deny, caller hook |
| 21:23:28 | `…accountability-phase-1/b0c61423…` (+2 subagents) | Step 3 MCP availability check, coder and pulse |
| 21:32:55 | `-tmp-s0-work/7518e0a9…` | `--agent` payload probe |
| 21:37:40 | `…accountability-phase-1/8e4d1eb8…` | Live: planner Write |
| 21:38:21 | `…accountability-phase-1/3e70b0e4…` (+1 subagent) | Live: build missing a section; complete pulse |
| 21:39:50 | `…accountability-phase-1/7f02a9da…` | Live: connectedAndroidTest, force push |
| 21:40:14 | `-tmp-live-fr-clone/90ae51a9…` | Live: filter-repo in the /tmp clone |
| 21:46:56, 21:47:32 | `…accountability-phase-1/9a7bffb7…`, `4be16e6e…` | Device item 1 (and its re-run) |
| 22:00:16 | `…accountability-phase-1/2685e266…` | Device items 2 and 3 |
| 22:01:34 | `…accountability-phase-1/cda43c18…` | Device item 2 re-run |
| 23:41:33 | `…accountability-phase-1/89505d08…` (+1 subagent) | Flag 1 test, before the fix |
| 23:43:33 | `…accountability-phase-1/4aa8cab0…` | Flag 1 test, after the fix |

26 files in all. **Not found:** the three interactive (pty) sessions: step 0's "ask" test, the hot
reload test, and the build-approval exercise. On screen they showed `Transcript saving is off —
inherited CLAUDE_CODE_CHILD_SESSION marker`, so Claude Code did not write them; only the completion
report's quotes of them survive. The one `-p` launch that failed on its own arguments never started a
session.

All 26 files were copied to `~/forager-backups/2026-09-22-01/`, keeping their
`~/.claude/projects/` layout, each confirmed byte-identical to its source by SHA-256 (3.1 MB).
`~/forager-backups/INDEX.md` was created with the append-only header and one line for this backup.
Nothing was deleted, in `~/.claude/projects/` or anywhere else.

## Sandbox egress on Claude Code 2.1.280

**Yes, per the documentation; not tried on this machine.** Source:
<https://code.claude.com/docs/en/sandboxing>, read 2026-09-22.

- The sandbox confines Bash, PowerShell and Monitor commands and "their child processes". Its
  network layer routes traffic through a proxy that admits only allowed domains: "Claude Code
  pre-allows no domains by default", `sandbox.network.allowedDomains` pre-allows hosts, and
  `strictAllowlist: true` denies anything outside the allowlist "instead of prompting". That is the
  control the design's "Gap: outbound sends" asks about, for the coder's Bash.
- **Where it can be set matters.** `strictAllowlist` is honored only "in user, managed, or CLI
  `--settings` settings", not in the repository's `.claude/settings.json`. The fallback to
  running a failed command unsandboxed is turned off with `"allowUnsandboxedCommands": false`, after
  which "Claude Code ignores the `dangerouslyDisableSandbox` parameter". `excludedCommands` lists
  merge from every scope, so a developer can always append commands that run outside the sandbox.
- **It covers commands, not tools.** It does nothing for MCP tools or WebFetch, which are
  separate tools; those stay with the allowlists and permission rules.
- **Its own stated limits:** the proxy decides on the client-supplied hostname without inspecting
  TLS by default, and the docs warn that allowing broad domains "such as `github.com` can create
  paths for data exfiltration", including by domain fronting. The coder needs GitHub, so the
  allowlist it would need is exactly that kind of domain.
- **This machine:** Linux on Ubuntu 26.04. The docs require `bubblewrap` and `socat`: `bwrap` is
  installed (`/usr/bin/bwrap`), **`socat` is not**. The docs also note that on Ubuntu 24.04 and later
  the default AppArmor policy stops bubblewrap from creating user namespaces, with a setup step. None
  of this was configured or run; per the ruling, coder egress is unchanged.

Every version floor the page states for these features (for example v2.1.216 for
`sandbox.filesystem.disabled`) is below 2.1.280, but that is the documentation's claim about the
version, not an observation of it.

## Decisions I made

1. Opened intent 2026-09-22-07 and preserved the ruling by hand before any work, following the
   coder's own rules, though no hook required it in this session.
2. Ran the flag 1 test with `--allowedTools` for the one MCP tool, so the test measured the guards
   and roles rather than the ordinary permission prompt; disclosed above.
3. Ordered the new check before the Type and section checks, so a blocked target leaves no preserved
   prompt.
4. Blocked a call with no `subagent_type`, which the ruling did not name.
5. Recorded the flag 1 test's preserved prompt with a `dispatch-note`, Type `pulse` (its `Type:`
   line) and Outcome `exercise`; the note names the real target.
6. Copied the session logs in their `~/.claude/projects/` layout, and created
   `~/forager-backups/INDEX.md` with a header and table columns of my choosing.
7. Included this follow-up's own two flag 1 sessions in the backup, beyond the live-exercise and
   device-run sessions the ruling named.

## Flags outside scope

- Claude Code prunes its own session logs after a retention period (the `cleanupPeriodDays`
  setting); not checked here, and it is why the copy matters.
- The PR #114 description does not mention flag 1; a line saying it is fixed has been added to it.
