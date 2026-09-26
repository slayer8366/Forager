# Accountability setup, phase 1: completion report

2026-09-22. Dispatch versions 1 and 2 (`prompts/preserved/2026-09-22-01.md`, `-02.md`), record
entries 2026-09-22-01 to -03 in `RECORD.md`. Branch `accountability-phase-1`, cut from
`origin/main` at `89f53a4f288d59aeec4f3deb44e0103c15ce13b7`. Claude Code 2.1.280.

**Merge is blocked until device items 1 to 3 pass** (operator ruling, 2026-09-22). No phone was
connected at any point in this run (`adb devices` listed nothing), and the operator ruled to finish
without it. Until the device items pass, no agent runs device work of any kind.

**After merge, restart Claude Code sessions in Forager before relying on the agents.**
`.claude/agents/` did not exist in the main checkout when current sessions started, and Claude Code
only watches agent directories that existed at session start (sub-agents docs, "Write subagent
files"). The hooks, by contrast, load mid-session: see step 0.

## What landed

All pushed to `origin/accountability-phase-1`; the tree was clean after each commit.

| Commit | What it does |
|---|---|
| `a0cdf02` | Step 1: design doc and autopilot addendum v1 unmodified; `check_record.py` and `check_prompts.py` vendored byte-identical from EGD `Test-1` @ `35e39d1`; `RECORD.md` with entries 01 to 03; both dispatch versions under `prompts/preserved/`; `__pycache__/` ignored |
| `f678d5a` | Step 1b: the `dispatch-note` entry kind in both checkers (operator ruling), failing-first |
| `f22dc23` | Step 2: `.gitignore` narrowed (decision D); `role_guard.py` (decision A and the pulse role); shared `guardlib.py`; hook test harness |
| `8784dab` | Step 3: `.claude/agents/coder.md` and `pulse.md` |
| `fca7b43` | Step 4: `dispatch_guard.py` |
| `bd7fc6a` | Step 5: `device_guard.py` |
| `82c6d8e` | Step 6: `history_guard.py` |
| `1e16526` | Autopilot addendum v2 replaces v1 (operator ruling), reference only |
| `16bbb40` | The two prompts the dispatch hook preserved during live exercises, their dispatch notes, and the CLAUDE.md section |
| (this commit) | This report, its index row, and the terminal entry closing 2026-09-22-03 |

## Verification before building

### Pre-flight stop (dispatch version 1)

The first dispatch said to stop if `.claude/` already held agents, settings or hooks. It held
`settings.local.json` (allowing `git fetch *`, `git push *` and one CLAUDE.md commit), nine skills,
and the worktrees directory; no agents, no hooks, and none in `~/.claude/`. `.gitignore:34` also
ignored all of `.claude/`, so nothing under it could be committed. Both went to the operator, who
ruled decisions D and E and added F; version 2 of the dispatch carries them.

Before editing line 34, `git log -L34,34:.gitignore` traced it to `2533e8e` (2026-09-10): it ignored
"Claude Code session state (worktrees, local agent config)" because untracked entries made
`resolveBuildIdentity()` stamp release builds `.dirty`. That is the reason the operator's condition
allowed. The dirty stamp is `app/build.gradle.kts:102` (`val dirtySuffix = if (status.isEmpty()) ""
else ".dirty"`), from `git status --porcelain` at `:74`. Untracked files do count: observed
`?? sub-write.txt` in a throwaway repository's porcelain output, and `status.showUntrackedFiles` is
unset here.

### Step 0: the platform (Claude Code 2.1.280)

| Question | Answer | How it was established |
|---|---|---|
| The dispatch tool's name as hooks see it | `Agent` | Observed: hook payloads carry `"tool_name":"Agent"`. Sub-agents doc: "formerly called `Task` in version 2.1.63 and earlier". Observed later: this version's `-p` init tool list still names it `Task`, so the dispatch hook matches `Agent\|Task`. |
| Does a settings deny apply to subagents as well as the main session? | **Yes** | Observed. A project deny `Bash(echo denied-marker*)` produced `Permission to use Bash with command echo denied-marker sub has been denied.` inside the `probe` subagent, and an `Edit(./probe-denied.txt)` deny blocked the subagent's write (`File is in a directory that is denied by your permission settings.`). |
| Does PreToolUse input identify the calling agent? | **Yes** | Observed. `agent_type` and `agent_id` are null in the main session and `"agent_type":"probe","agent_id":"a8035c99c028d5761"` inside the subagent. Hooks doc: "present only when the hook fires inside a subagent call". |
| Can a PreToolUse hook return "ask"? | **Yes, interactively.** In `-p` it blocks. | Observed interactively (pty): `Hook PreToolUse:Bash requires confirmation for this command: HOOK: ask-marker needs approval ... Do you want to proceed? 1. Yes 2. No`, even with the child session in auto mode. Observed in `-p`: the call failed with the hook's reason. The docs say nothing about `-p`. |
| Where do subagent definitions load from; is a restart needed? | `.claude/agents/` (project) and `~/.claude/agents/` (user), below managed and `--agents`. Watched without restart, **except** a scope's first agents directory created after session start, which needs a restart. | Sub-agents doc, quoted in the session. Observed: child sessions started after the files existed loaded `coder` and `pulse`. |

**Precedence by experiment** (decision E). A throwaway repository under `/tmp` had a local
`settings.local.json` allowing `git push *` (mirroring Forager's), a project `settings.json` denying
`git push --dry-run*`, and user-level settings allowing `git push:*`. In a live session,
`git push --dry-run origin main` was refused: `Permission to use Bash with command git push --dry-run
origin main has been denied.`; the control `git push origin main`, covered only by the allows, ran
(`Everything up-to-date`). **No allow beat the project deny.** This matches `permissions` docs
("deny rules from any scope are evaluated before allow rules").

**Stop condition not met.** A main-session settings deny does also deny subagents, but `agent_type`
identifies the caller, so decision A is achievable by a hook. Settings alone could not do it.

**Also observed: hook configuration loads mid-session.** A blocking hook added to
`.claude/settings.json` while an interactive session was running fired on that session's next Bash
call (`RELOAD-HOOK-FIRED`). That is why this build ran in a separate worktree: committing the planner
restriction into the session's own project directory would have locked the session out partway
through. The operator approved that plan.

## What was built

**Record store.** `RECORD.md` at the repository root, with a header in Forager's words that names the
checkers as the definition and avoids the literal heading text `split_entries` searches for
(`check_record.py`, `text.find("## Entries")`). `check_record.py` and `check_prompts.py` sit beside it
and needed no path or name changes: both resolve `RECORD.md` and `prompts/` relative to their own
directory. The only divergence from upstream is the `dispatch-note` change the operator ruled on (see
Appendix for every changed line). A `dispatch-note` carries `Dispatch-file`, `Type` (build, device,
pulse), `Outcome` (answered, declined, exercise) and `Report`; it may claim only a prompt under
`prompts/preserved/`, and it may not carry `Closes`, `Superseded-by`, `Finish line` or a prediction
field.

**Planner and pulse restrictions (`role_guard.py`, every tool).** In the main session the tool must
be on the operator's allowlist (Read, Grep, Glob, Bash, Agent, Skill, WebFetch, WebSearch,
AskUserQuestion, ToolSearch, TodoWrite); everything else is denied by name, including every `mcp__`
tool and NotebookEdit. Planner Bash, in order: any unquoted redirection; then step 2's named
patterns, each blocked with its own message; then any compound command, pipe, substitution or
variable; then an allowlist of read-only git and gh. The pulse gets Read, Grep, Glob and Bash, with the same read-only
git and gh plus `adb` getprop, dumpsys (not its set/reset/unplug/clear/enable/disable forms),
screencap, devices, get-state and get-serialno. The coder and every other subagent type are not
restricted by this hook.

**Subagents.** `coder` has Read, Grep, Glob, Edit, Write and Bash. Its prompt carries EGD BOOTSTRAP's
coder section in Forager's terms, the operator's sweep rule, and PROTOCOL rule 6 as a required
"Decisions I made" section. `pulse` has Read, Grep, Glob and Bash, answers with file:line and hash,
and ends with "Could not determine". Each description says when to use the agent.

**Dispatch hook (`dispatch_guard.py`, Agent|Task).** It blocks on a missing or unknown `Type:` and
names every missing section. On a pass it writes the prompt verbatim to
`prompts/preserved/<UTC date>-<seq>.md`, headed `HEAD:`, `Target subagent:`, `Type:`, `Preserved:`
and a delimiter line, never overwriting (`open(..., "x")`), and blocks if it cannot write. It runs
`check_prompts.py` and reports the result without enforcing it: the prompt just written is always
unclaimed until the coder's sweep, and the planner cannot write the note, so blocking would deadlock.
It returns `ask` for build and device and `allow` for pulse.

**Device guards (`device_guard.py`, Bash, every role).** connectedAndroidTest in any variant needs the
leave-installed flag. `adb uninstall`, `pm uninstall` and `pm clear` are blocked. `adb install`
compares each APK's signer (aapt2 for the package, `pm path`, pull, apksigner on both); a package that
is not installed is allowed. `adb shell input` and any screencap need `com.zynergylabs.forager.app` as
the resumed activity. Any answer it cannot get is a deny naming what it could not determine.

**History guards (`history_guard.py`, Bash, every role).** Blocked: force-push (`--force`,
`--force-with-lease[=...]`, `-f` alone or in a cluster); `git merge` while the repository, or the
`-C` path, is on main; push to main (an explicit main refspec, `--all`, `--mirror`, or a push with no
refspec or `HEAD` while on main); `gh pr merge`; `git filter-repo` and `git filter-branch` (decision
F), including the `git-filter-repo` binary name.

**Every guard fails closed.** Claude Code treats exit codes other than 0 and 2 as non-blocking, so
an uncaught exception would let the call through. `guardlib.run` turns any exception into a deny
naming it; that path has its own test and sabotage run.

**CLAUDE.md** gains one section, appended at the end so the Restricted Object rule stays at line 253,
where the closed decisions cite it. Its text:

> ## Roles and gates
>
> Planner, coder and pulse are held apart by hooks, not by instruction. The
> design is `docs/process/accountability-design.md`; the subagents are in
> `.claude/agents/`, the gates in `.claude/hooks/`, and the dispatch record in
> `RECORD.md` and `prompts/preserved/`. A main session in this repository is the
> planner and is read-only. Added at the end of this file so that line 253 does
> not move.

**Not touched:** app code, the strip stack (#111 to #113), `docs/audits/` records other than this
report and its index row, `.claude/settings.local.json`, user settings, and anything from the
autopilot addendum.

`git check-ignore -v -n` after the `.gitignore` change (decision D):

```
== meant to be tracked (-n shows non-matching; "::" = no pattern matched, so not ignored)
.gitignore:40:!.claude/settings.json	.claude/settings.json
::	.claude/agents/coder.md
::	.claude/agents/pulse.md
::	.claude/hooks/role_guard.py
::	.claude/hooks/guardlib.py
::	.claude/hooks/tests/test_role_guard.py
== meant to stay ignored
.gitignore:34:.claude/*	.claude/settings.local.json
.gitignore:34:.claude/*	.claude/skills/stop-and-ask/SKILL.md
.gitignore:34:.claude/*	.claude/worktrees/x/README.md
.gitignore:53:__pycache__/	.claude/hooks/__pycache__/guardlib.cpython-314.pyc
```

`git status` listed the "tracked" set as untracked, not ignored, which is the confirmation that
`::` means included.

## Evidence

Run all hook tests with `python3 -m unittest discover -s .claude/hooks/tests`: 46 tests, OK, as
committed. `python3 check_record.py --render-check`: 23 of 23. `python3 check_prompts.py
--render-check`: 5 of 5.

Every failing-first run below was against a stub that exits 0 and prints nothing (allow
everything), or, for the checkers, against upstream logic. Every sabotage run used a runner that saves
a copy of the file, applies one edit, refuses to cite a run showing an import or syntax error,
restores from the saved copy (never from git), and confirms the restored file is byte-identical to
the pre-sabotage copy. Each run printed `restored from saved copy; byte-identical to pre-sabotage:
True`, and each suite was green again afterwards.

### Record store (`dispatch-note`)

Failing-first, upstream logic, new tests:

```
[check19_well_formed_dispatch_note_accepted] FAIL: AssertionError('a well-formed dispatch-note produced errors: ["2026-01-01-05: missing or invalid Kind (got \'dispatch-note\')"]')
[check20..23] FAIL: ... ["2026-01-01-05: missing or invalid Kind (got \'dispatch-note\')"]
[p2_pulse_prompt_with_its_note_passes] FAIL: AssertionError('the claiming dispatch-note is not a valid entry: ["2026-01-01-05: missing or invalid Kind (got \'dispatch-note\')"]')
[p3_sabotaged_note_outside_preserved_fails] FAIL: AssertionError('a dispatch-note claiming a prompt outside preserved/ was accepted: []')
[p4_sabotaged_note_outcome_fails] FAIL: ... missing or invalid Kind ...
```

`p1` (an unclaimed pulse prompt fails) and `p5` (a prompt claimed by both an intent and a note fails)
passed before and after. They guard upstream behaviour and are not evidence for this change. This is
the operator's sequence: the unclaimed pulse fails before the change (`p1`), passes with its note
after (`p2`), and fails again when the note is sabotaged (`p3`, `p4`).

Sabotage:

| Edit | Failed | Message |
|---|---|---|
| `if kind == NOTE_KIND:` → `if kind == "never-a-kind":` | check19 to 23 | `missing or invalid Kind (got 'dispatch-note')` |
| Outcome check → `if False:` | check21 | `dispatch-note with an Outcome outside answered/declined/exercise was accepted: []` |
| forbidden-field check → `if False:` | check23 | `a dispatch-note carrying Closes was accepted: []` |
| preserved-only claim → `if False and ...` | p3 | `a dispatch-note claiming a prompt outside preserved/ was accepted: []` |

### role_guard.py

Failing-first against the stub: 43 subtest failures in 13 tests, every one `AssertionError: None !=
'deny'`. The tests expecting a pass also passed against the stub, so they guard only against
over-blocking.

First implementation run: one real false positive, `git blame app/build.gradle.kts` denied as
"gradle" (`\bgradlew?\b` matched a file name). The pattern now requires a command word.

| Sabotage | Result |
|---|---|
| tool allowlist → `if False:` | FAIL: `test_write_edit_notebook_denied [Write]`, `[Edit]`, `test_mcp_tools_denied`, `test_unknown_tool_denied_by_default`, `test_pulse_tools_limited` |
| `role()`: absent `agent_type` → `"coder"` | FAIL: every `test_named_patterns` case (`git commit -m x`, `git push origin feature`, ...) |
| named `git commit` pattern → never matches | **survived at first.** The allowlist also denies, and its message ("`git commit` is not on the read-only git list") also contains "git commit". The test now asserts the named layer's own wording; re-run: FAIL `test_named_patterns [git commit -m x]` |
| redirection check → `if False:` | FAIL: `[echo hi > notes.txt]`, `[git log >> log.txt]` |
| fail-closed `except` → `return 0` | FAIL: `test_malformed_payload_fails_closed` |
| pulse remote-punctuation check → `if False:` | **survived twice.** `getprop; rm -rf ...` is also caught by the `rm` pattern, and in `getprop; reboot` the first word `getprop;` fails the read list. With `adb shell 'getprop ro.x; reboot'` added: FAIL on that case |

### dispatch_guard.py

Failing-first against the stub: 10 of 11 failed, all `None != 'deny'`, `None != 'ask'` or `None !=
'allow'`. `test_other_tools_ignored` passes against the stub by construction. Before sabotaging, three
assertions were tightened to the hook's own wording: a fail-closed `KeyError: 'audit'` would also
contain `'audit'`.

| Sabotage | Failed |
|---|---|
| missing-Type check → `if False:` | `test_missing_type_blocks` |
| unknown-Type check → `if False:` | `test_unknown_type_blocks` |
| missing-sections check → `if False:` | `test_build_missing_one_section_names_it`, `test_pulse_missing_sections_names_each` |
| `f.write(header + text)` → `text.strip()` | `test_complete_pulse_allowed_and_preserved_verbatim` |
| ask for `("build", "device")` → `("device",)` | `test_complete_build_asks_and_gets_next_sequence` |
| `DISPATCH_TOOLS` without `Task` | `test_task_tool_name_also_checked` |
| cannot-preserve → `allow` | `test_outside_a_repository_blocks` |

### device_guard.py

Failing-first against the stub: 18 failures, all `None != 'deny'`, and one error:
`FileNotFoundError: .../adb.log`, because the stub never called adb to pull the installed APK. First
implementation run: `adb -s R5CT shell pm uninstall` was blocked but named "adb uninstall". The `pm`
patterns are now checked first.

| Sabotage | Failed |
|---|---|
| connectedAndroidTest check → `if False:` | the three `test_connected_android_test_without_flag_denied` cases |
| `pm clear` pattern → never matches | `[adb shell pm clear com.zynergylabs.forager.app]` |
| `adb uninstall` pattern → never matches | `test_applies_to_every_role` (all four roles) |
| signer comparison → `if False:` | `test_install_different_signature_denied` |
| foreground comparison → `if False:` | the three `test_input_and_screencap_blocked_when_forager_not_in_front` cases |
| not-installed shortcut forced true | `test_install_different_signature_denied`, `test_install_same_signature_passes` |
| could-not-read-foreground → `if False:` | **survived at first.** The fallthrough still denied ("the foreground app is None") and also said "foreground". Tightened to the hook's wording; re-run: FAIL both `test_foreground_unreadable_denied` cases |

**Positive control against the real tools.** The fakes' output shape was checked against the real
tools, not assumed. apksigner 37.0.0 prints `V2 Signer: certificate SHA-256 digest: ...`, not the
`Signer #1 ...` form the fake first used, and the fake now prints the observed form (the guard's
regex matched both). Run on Forager's own built APKs in the main checkout, the guard's parsers read
`com.zynergylabs.forager.app` from aapt2 and returned `cb2f6da5...` (debug) against `9fb61662...`
(release), so they tell two signers apart.

### history_guard.py

Failing-first against the stub: every case `None != 'deny'`. Seven sabotage runs, each failing its
own test the first time:

| Sabotage | Failed |
|---|---|
| force-push match → `None` | `test_applies_to_every_role` (all roles), the force-push cases |
| `gh pr merge` → `if False:` | `test_gh_pr_merge_denied` |
| filters → `None` | the three `test_history_rewriting_denied` cases |
| merge-on-main → `if False:` | `test_merge_on_main_denied_elsewhere_allowed`, `test_merge_with_dash_c_uses_that_repository` |
| main refspec → `if False:` | `[git push origin main]`, `[git push origin HEAD:main]`, `[git push origin HEAD]` on main |
| bare push on main → never | `[git push]`, `[git push origin]` |
| `-C` path ignored for merge | `test_merge_with_dash_c_uses_that_repository` |

### Bypass table

Every Bash guard in steps 2, 5 and 6, against the five variants, run through the real hooks with
crafted payloads (the device guard with a fake adb reporting the launcher in front; history cases with
cwd a throwaway repository on main or on a feature branch). "Passes" means the guard returned no
decision.

| Guard | Base | sh -c | variable (split) | bash script.sh | xargs | gradle task alias |
|---|---|---|---|---|---|---|
| planner (role_guard, main session) | blocked | blocked | blocked | blocked | blocked | blocked |
| device: connectedAndroidTest | blocked | blocked | blocked | **passes** | blocked | **passes** (`./gradlew cAT`) |
| device: connectedAndroidTest via umbrella task | blocked | | | | | **passes** (`./gradlew connectedCheck`) |
| device: adb uninstall | blocked | blocked | **passes** | **passes** | blocked | n/a |
| device: foreground (input) | blocked | blocked | **passes** | **passes** | blocked | n/a |
| history: force push | blocked | **passes** | **passes** | **passes** | **passes** | n/a |
| history: push to main | blocked | **passes** | **passes** | **passes** | **passes** | n/a |
| history: merge on main | blocked | blocked | **passes** | **passes** | blocked | n/a |
| history: gh pr merge | blocked | blocked | **passes** | **passes** | blocked | n/a |
| history: filter-repo | blocked | blocked | **passes** | **passes** | blocked | n/a |

Commands: base as in the guard's name; `sh -c '<base>'`; the split variable moves the forbidden word
into a variable (`F=--force; git push $F`, `U=uninstall; adb $U ...`, `P=push; git $P origin
feature`); `bash <name>.sh`; `xargs` feeds the last word through a pipe (`echo --force | xargs git
push`). The gradle alias column is n/a where no gradle task does the thing. A task written to do it
has the same shape as `bash script.sh`.

Variable holding the whole command (the assignment spells it out):

| Guard | Command | Result |
|---|---|---|
| connectedAndroidTest | `C='./gradlew connectedAndroidTest'; $C` | blocked |
| adb uninstall | `C='adb uninstall com.example.doesnotexist'; $C` | blocked |
| foreground (input) | `C='adb shell input keyevent 0'; $C` | blocked |
| force push, `--force` last | `C='git push --force'; $C` | **passes** |
| force push, `--force` mid-command | `C='git push --force origin feature'; $C` | blocked |
| force push via sh -c, `--force` mid-command | `sh -c 'git push --force origin feature'` | blocked |
| push to main | `C='git push origin main'; $C` | **passes** |
| merge on main | `C='git merge feature'; $C` | blocked |
| gh pr merge | `C='gh pr merge 5'; $C` | blocked |
| filter-repo | `C='git filter-repo --analyze'; $C` | blocked |

Outside the five variants, found while building: `git push origin +feature` (force by refspec)
**passes**; `git pull origin feature` on main (a merge) **passes**; `adb shell cmd package uninstall
...` is blocked by the `adb ... uninstall` pattern.

Why the force-push guard misses `sh -c` and a whole-command variable when `--force` is last: its
pattern ends in the lookahead `(?=\s|$)`, and the closing quote after `--force` is neither. Push to
main and merge on main are parsed per command segment, and a quoted string is one token, so a command
inside quotes is never parsed. Per the dispatch, none of these is patched. Each is a finding for the
planner.

### Live exercises

Real Claude Code 2.1.280 sessions on this branch, with the committed hooks.

| Exercise | Session | Result | Message shown |
|---|---|---|---|
| Planner attempts `Write` to `/tmp/live/planner-scratch.txt` | planner, `-p` | **blocked**; file not created | `PreToolUse:Write hook error: role_guard: the planner role may not use Write. The planner allowlist is Agent, AskUserQuestion, Bash, Glob, Grep, Read, Skill, TodoWrite, ToolSearch, WebFetch, WebSearch; any other tool is denied until the operator adds it by name.` |
| Build dispatch missing one section | planner, `-p` | **blocked**; no file written | `PreToolUse:Agent hook error: dispatch_guard: this build dispatch is missing 1 required section(s): Device items. Required for build: Role; Base and state; Scope boundary; Closed decisions; Prediction; Finish line and abort conditions; Checks; Out of scope; Device items.` |
| Complete pulse dispatch | planner, `-p` | **ran unasked**; preserved as `prompts/preserved/2026-09-22-03.md` (header HEAD `1e16526`, target pulse, type pulse; body byte-identical to the dispatched text, 291 bytes) | none (allow). The pulse's own `git rev-parse` was then refused by Claude Code's ordinary permission prompt, `This command requires approval`, not by a guard; see flags |
| Complete build dispatch | planner, interactive (pty) | **prompted**; declined with Esc; nothing ran; preserved as `-04.md` | `Hook PreToolUse:Agent requires confirmation for this tool: dispatch_guard: build dispatch to coder preserved at prompts/preserved/2026-09-22-04.md (HEAD 1e165261d0). Operator approval required (decision B). check_prompts.py exit 1: FAIL: 2 binding violation(s) ... Do you want to proceed? 1. Yes 2. Yes, and don't ask again for coder commands in ~/Zynergy/Forage… 3. No` |
| `./gradlew connectedAndroidTest --dry-run` | `claude -p --agent coder` | **blocked** | `PreToolUse:Bash hook error: device_guard: connectedAndroidTest uninstalls the app after the run, wiping its data. Add -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true.` |
| `git push --force --dry-run` | `claude -p --agent coder` | **blocked** | `PreToolUse:Bash hook error: history_guard: force-push (`--force`) rewrites history on the remote and is never run by an agent.` |
| `git filter-repo --analyze` in a throwaway clone under `/tmp`, never in Forager | `claude -p --agent coder` in the clone | **blocked**; no `.git/filter-repo/` created | `PreToolUse:Bash hook error: history_guard: `git filter-repo` rewrites history and is run by the operator by hand (decision F).` git-filter-repo is not installed on this machine, so had the guard failed, the command would have errored rather than run. |
| `adb uninstall com.example.doesnotexist` | not run | **not run**: no device (operator ruling) | |
| `adb shell input keyevent 0`, Forager not in front | not run | **not run**: no device (operator ruling) | |

The coder-side exercises ran as `claude --agent coder` sessions rather than through an approved
build dispatch, so that nobody but the operator approves a build. The auto-mode classifier refused to
launch those sessions in `bypassPermissions` (`[Create Unsafe Agents]`), so they ran in the default
mode. PreToolUse hooks run before the permission check, so each block above carries the guard's own
message, distinct from `This command requires approval`.

Step 3 check (operator addition): each of `coder` and `pulse` was asked to call
`mcp__claude_ai_Resend__list-domains`. Both reported it unavailable and made no call (coder: "The
Resend tool isn't available to me ... so I made no call"; pulse: "is not available to me, so I did
not call it"). The check could have failed: that child session's init lists `claude.ai Resend`,
status `connected`.

### Predictions, graded

Planner's (dispatch "Prediction"):

1. *Decision A achievable only through a hook that identifies the caller.* **Confirmed** (step 0).
2. *At least one variant gets past at least one guard.* **Confirmed.** Most variants get past most
   device and history guards; none gets past the planner guard.
3. *Every hook passes its sabotage test.* **Confirmed as committed, not first time.** Four sabotage
   runs survived at first (role guard 2, device guard 1, dispatch guard 0, but only because three
   assertions were tightened pre-emptively). All four were test gaps, fixed in the tests.

Coder's mechanism prediction (entry 2026-09-22-03):

- (a) *`sh -c` and a variable spelling out the command are caught.* **Partly wrong.** True for
  connectedAndroidTest, uninstall, foreground, merge on main, gh pr merge and filter-repo. Wrong for
  force-push when `--force` is the last word (trailing quote) and for push to main (segment parse).
- (b) *`bash script.sh` gets past every device and history guard.* **Confirmed.**
- (c) *`cAT` gets past the connectedAndroidTest guard.* **Confirmed**, and `connectedCheck` also
  gets past, which was not predicted.
- (d) *Split `xargs` gets past the force-push guard.* **Confirmed.**
- (e) *The planner guard blocks all five.* **Confirmed.**
- (f) *Every hook fails its sabotage test.* **Confirmed after the test fixes above.**
- (g) *check_prompts.py reports the live-exercise prompts unclaimed until notes exist.*
  **Confirmed** in the build exercise's approval prompt (`2 binding violation(s)`, naming `-03` and
  `-04`); passes after `16bbb40`.
- (h) *The install signature guard is evidenced by unit tests only.* **Confirmed for the device
  half.** The parsing half was also run against the real tools on real APKs.

## Not tested

- **Device items 1 to 3**, and the two adb live exercises: no phone was connected. The foreground
  guard's parsing of real `dumpsys activity activities` output from the S26 Ultra is unverified. The
  regex accepts `topResumedActivity`, `mResumedActivity` and `ResumedActivity` forms, but no real
  output was read. Nor was the install guard's `pm path` / `pull` half run against a device.
- **Approving a build at the prompt.** Only declining was exercised. Whether choosing "2. Yes, and
  don't ask again for coder commands" makes later build dispatches skip the prompt is untested; see
  flags.
- **Hooks on the owner's Windows host.** Every hook is invoked as `python3`, which may not resolve
  there. Earlier records in `docs/audits/` mention a Windows host pool. Not checked.
- **The restart requirement after merge** is from the docs, not observed in Forager's own main
  checkout.
- The Grep and Glob entries in the agents' tool lists and the planner allowlist are inert on this
  version: see flags.

## Device items

Operator ruling: merge is blocked until these pass, and no agent-run device work happens until then.
The phone is connected over USB and unlocked; the agent never presses Home.

1. The owner puts the phone on the home screen and says so. The agent attempts `adb shell input
   keyevent 0`.
   - **Pass:** blocked, with a message naming the foreground package, e.g. `the foreground app is
     com.sec.android.app.launcher, not com.zynergylabs.forager.app`.
   - A message saying `could not read the foreground app` is a finding about the `dumpsys` parsing,
     not a pass. Keep its text.
2. The owner opens Forager. The agent attempts the same command.
   - **Pass:** allowed. Keyevent 0 does nothing visible on screen.
   - If it is blocked, the message names the package the guard read, which shows whether the parse
     or the package name is wrong.
3. The agent attempts `adb uninstall com.example.doesnotexist`.
   - **Pass:** blocked, `device_guard: `adb uninstall` removes the app or its data ...`.
   - If it runs, it fails harmlessly on the missing package. Record it as a guard failure and stop.

## Decisions I made

Every choice below is one the dispatch and the operator's rulings did not make.

1. **Checker placement.** `check_record.py` and `check_prompts.py` sit at the repository root beside
   `RECORD.md`, so they needed no path change. Elsewhere would have meant editing upstream lines.
2. **`Dispatch-file` values are relative to `prompts/`** (`preserved/2026-09-22-01.md`), per
   upstream's own entries and `check_prompts.py`'s walk. I first wrote `prompts/preserved/...`; the
   checker failed on it, and the fix landed before the first commit.
3. **`__pycache__/` added to `.gitignore`.** check_prompts imports check_record, and the hooks
   import guardlib, so an untracked cache would stamp builds `.dirty`.
4. **A separate record field for the planner's prediction.** The dispatch put "this dispatch's
   prediction" and "Outcome prediction: `not authored`" in the same entry. The planner's prediction
   went into its own field, "Planner prediction (stated in the dispatch, not withheld)", so the
   `not authored` sentinel stays literally true and the terminal entry does not carry
   `Prediction-outcome-supplied`, which `check_record.py` treats as fabrication.
5. **Entry 01 written after the fact**, with the same timestamp as 02 and 03 (the time they were
   written, not the time version 1 was delivered). Its Notes say so.
6. **`dispatch-note` details beyond the ruling:**
   - it also requires `Kind` and `ID`, since ID uniqueness and claim messages depend on an ID;
   - it does not require `Timestamp`;
   - it rejects `Closes`, `Superseded-by`, `Finish line` and prediction fields;
   - it does not check that a `Report` path exists;
   - the checkers' tests are `--render-check` modes, following check_record's convention; this is
     new for check_prompts.
7. **Hooks in Python with a shared `guardlib.py`**, failing closed on any exception.
8. **The read-only git and gh lists.**
   - git: log, show, diff, status, rev-parse, ls-files, ls-tree, ls-remote, blame, cat-file,
     describe, shortlog, grep, merge-base, rev-list, show-ref, list-only branch, `remote`/`-v`/
     `get-url`, `config --get/--get-all/--list`, `stash list/show`, `worktree list`. `-C` and
     `--no-pager` are allowed; `-c` is not; `--output` is not.
   - gh: pr view/list/diff/checks/status, issue view/list/status, repo view, run view/list, release
     view/list, and `gh api` without `-X`/`--method`/`-f`/`-F`/`--field`/`--raw-field`/`--input`.
   - `git fetch` is excluded because it writes refs. That means the planner cannot refresh
     `origin/*` itself.
9. **No `adb` at all for the planner.** Decision A's "read-only git and gh" governs, even though step
   2's pattern list says "adb except the read-only subcommands the pulse role uses". Device reads go
   through a pulse.
10. **No pipes or compound commands for the planner**, not even `git log | head`.
11. **The pulse's adb reads include devices, get-state and get-serialno** beyond the three named, and
    exclude dumpsys's mutating forms. The pulse's tool list is also enforced in the hook, not only in
    its frontmatter.
12. **Subagents other than coder and pulse are unrestricted by role_guard.** Nothing specified them;
    see flags.
13. **Dispatch hook details:**
    - it also matches `Task`;
    - a section is a markdown heading equal to, or starting with, the required name;
    - `**Type:** x` and `Type: x` are both accepted;
    - file names use the UTC date, and the header format is mine;
    - it blocks when it cannot preserve;
    - it applies to every Agent call, including Explore and general-purpose.
14. **Device guard details:**
    - `connected\w*AndroidTest` covers task variants;
    - the guard runs its own adb queries against the same `-s` device;
    - every `.apk` argument is checked, comparing against `base.apk` when several paths exist;
    - `FORAGER_GUARD_ADB/AAPT2/APKSIGNER` environment overrides exist for the tests (see flags).
15. **History guard details:**
    - `-f` inside a flag cluster counts as force;
    - `--all` and `--mirror` count as pushing main;
    - `HEAD`, or no refspec, on main counts as pushing main;
    - a detached HEAD is not main.
16. **Both exercise prompts' notes use Outcome `exercise`**, including the declined build. I
    declined it myself as the exercise; the operator did not decline it.
17. **The coder-side live exercises ran as `claude --agent coder` sessions** instead of through an
    approved build dispatch.
18. **The two adb live exercises count as "device work"** under the operator's ruling, so I did not
    run them.
19. **The CLAUDE.md section is at the end of the file**, to keep line 253.
20. **Commit boundaries.** Step 1 is two commits (vendoring, then the logic change, so the upstream
    diff reads on its own), and there are separate commits for addendum v2 and the exercise prompts.
    The dispatch said one commit per step.
21. **Terminal outcome `completed`**, with the device items listed as deviations under the operator's
    ruling to finish without the phone.

## Flags outside scope

1. **A hole in decision A: the planner can dispatch a writing subagent.** role_guard restricts only
   the main session and `pulse`. The planner may call Agent, and the built-in `general-purpose` agent
   has Edit and Write. The dispatch hook only needs a Type and the sections. A related hole: Type and
   target are not tied. A dispatch typed `pulse` to `coder` runs without approval, which bypasses
   decision B. Neither is patched.
2. **A `claude --agent coder` main session escapes the planner restrictions.** Observed: a session
   started with `--agent probe` sends `agent_type: "probe"`, `agent_id: null`. Only whoever launches
   Claude Code can do this, not the planner.
3. **The dispatch hook applies to every Agent call.** An Explore or Plan call from the planner is now
   blocked unless its prompt carries a Type and the sections.
4. **Grep and Glob do not exist as tools on this version.** The `-p` init tool list has neither. In
   practice the planner and pulse search with Read, `git grep` and `git ls-files`.
5. **Pulses are not fully unattended.** Decision B lets the dispatch run unasked, but the pulse's own
   Bash reads go through Claude Code's normal permissions: in `-p` the pulse's `git rev-parse` was
   refused. Making pulses unattended needs project allow rules for read-only commands, which is a
   settings decision.
6. **"Yes, and don't ask again for coder"** is offered at the build approval prompt. If it saves an
   allow rule that silences later hook "ask" decisions, decision B erodes with one keypress.
   Untested.
7. **Serialization points.**
   - `RECORD.md` and the `prompts/preserved/<date>-<seq>` sequence are shared across branches, like
     `docs/audits/README.md`. Two branches can each take `2026-09-23-01`.
   - A merge commit reconciling concurrent `RECORD.md` edits makes `check_record.py` fail
     permanently (`MergeCommitEncountered`, `check_record.py` history walk).
   - Both matter for phase 4's legs and any parallel dispatches.
8. **Preserved prompts stamp builds `.dirty` until swept.** A pulse's prompt stays untracked until
   the next coder sweep commits its note (`app/build.gradle.kts:74`, `:102`).
9. **Where the prompt lands.** The dispatch hook writes into the repository at the payload's `cwd`.
   `${CLAUDE_PROJECT_DIR}` stays at the session's start directory while `cwd` follows a session into
   a worktree, so the hook scripts and the prompt can come from different checkouts.
10. **Hot reload.** Settings hooks load mid-session (step 0), so a coder that edits
    `.claude/settings.json` or a hook in the planner's own checkout changes the guards of the running
    planner. Phase 4's fixed fence covers `.claude/**`; phase 1 does not.
11. **Test seams in production.** `FORAGER_GUARD_*` environment variables redirect the device
    guard's tools. An agent's Bash command cannot set them for the hook process, but a settings
    `env` block could.
12. **Open PRs touching `.gitignore:34`.** #104 (skills under `.claude/skills/`) will conflict with
    this change, as will #109, which the operator expected.
13. **Left behind outside the repository.** Accepting the trust dialog in the step 0 experiment wrote
    a trust entry for `/tmp/s0/work` into `~/.claude.json`. I do not edit files outside the
    repository, so it is still there.
14. **Where phase 4 would plug in.** The dispatch hook is the natural place for the autopilot
    addendum's "block while an Adjust is unanswered" and its dispatch-count watch trigger. Nothing in
    phase 1 blocks either.
15. **The planner's own session.** After merge, every main session in Forager is read-only,
    including sessions like this one, where the owner asks a session to do work directly. Work then
    goes through a dispatch.

## Throwaway files

The step 0 precedence repository (`/tmp/s0`), the live-exercise clone and scratch files
(`/tmp/live`), the pty driver and sabotage runner (`/tmp/pty`), and the EGD clone (`/tmp/egd`) were
deleted after this report was written. None of them is kept.

## Appendix: every line changed from upstream

`diff -u` of E-GD-Philosophy `Test-1` @ `35e39d1` against this branch. Step 1 (`a0cdf02`) vendored
both files byte-identical; every change below is from `f678d5a`.

### `check_record.py`

```diff
--- upstream 35e39d1/check_record.py
+++ accountability-phase-1/check_record.py
@@ -94,6 +94,46 @@
 SUPPLIED_FIELD = "Prediction-outcome-supplied"
 
 
+# Forager addition (operator ruling, 2026-09-22; RECORD.md 2026-09-22-03).
+# A dispatch-note records a dispatch that opens no intent -- a pulse, a
+# build the operator declined, a live exercise of the dispatch hook -- so
+# the prompt the hook preserved for it is still claimed by an entry. It
+# opens and closes nothing and carries no prediction or finish line, so
+# carrying any of those fields is an error rather than an ignored extra.
+NOTE_KIND = "dispatch-note"
+NOTE_REQUIRED = ["Kind", "ID", "Dispatch-file", "Type", "Outcome", "Report"]
+NOTE_TYPES = {"build", "device", "pulse"}
+NOTE_OUTCOMES = {"answered", "declined", "exercise"}
+NOTE_FORBIDDEN = ["Closes", "Superseded-by", "Finish line",
+                  "Prediction (outcome — planner)",
+                  "Prediction (mechanism — coder)"]
+
+
+def _validate_note(fields, entry_id, label_for_errors):
+    errors = []
+    if entry_id and not ID_RE.match(entry_id):
+        errors.append(f"{NOTE_KIND} {entry_id}: malformed ID (expected "
+                      f"YYYY-MM-DD-NN)")
+    for req_label in NOTE_REQUIRED:
+        if not fields.get(req_label, "").strip():
+            errors.append(f"{NOTE_KIND} {label_for_errors}: missing required "
+                          f"field {req_label!r}")
+    note_type = fields.get("Type", "").strip()
+    if note_type and note_type not in NOTE_TYPES:
+        errors.append(f"{NOTE_KIND} {label_for_errors}: Type {note_type!r} is "
+                      f"not one of {sorted(NOTE_TYPES)}")
+    outcome = fields.get("Outcome", "").strip()
+    if outcome and outcome not in NOTE_OUTCOMES:
+        errors.append(f"{NOTE_KIND} {label_for_errors}: Outcome {outcome!r} "
+                      f"is not one of {sorted(NOTE_OUTCOMES)}")
+    for label in NOTE_FORBIDDEN:
+        if label in fields:
+            errors.append(f"{NOTE_KIND} {label_for_errors}: carries field "
+                          f"{label!r}, but a dispatch-note opens and closes "
+                          f"nothing and has no prediction or finish line")
+    return errors
+
+
 def split_entries(text):
     """Raw text blocks for each entry, found after the '## Entries'
     heading and separated by bare '---' lines. Header/format
@@ -151,6 +191,17 @@
         entry_id = fields.get("ID", "").strip()
         label_for_errors = entry_id or f"entry #{i + 1} (no ID)"
 
+        if kind == NOTE_KIND:
+            errors.extend(_validate_note(fields, entry_id, label_for_errors))
+            if entry_id:
+                if entry_id in seen_ids:
+                    errors.append(f"duplicate ID {entry_id}: used by entry "
+                                  f"#{seen_ids[entry_id] + 1} and entry #{i + 1}")
+                else:
+                    seen_ids[entry_id] = i
+            entries.append({"kind": kind, "id": entry_id, "fields": fields})
+            continue
+
         if kind not in ("intent", "terminal"):
             errors.append(f"{label_for_errors}: missing or invalid Kind "
                           f"(got {kind!r})")
@@ -542,6 +593,16 @@
     return _render_entry(fields)
 
 
+def _minimal_note(id_="2026-01-01-05", **overrides):
+    fields = {
+        "Kind": "dispatch-note", "ID": id_,
+        "Dispatch-file": "preserved/2026-01-01-05.md", "Type": "pulse",
+        "Outcome": "answered", "Report": "none",
+    }
+    fields.update(overrides)
+    return _render_entry(fields)
+
+
 def _minimal_record(*entries):
     parts = ["# RECORD.md", "", "## Entries", "", "---", ""]
     for e in entries:
@@ -889,8 +950,75 @@
           "intent ID is accepted",
           check18)
 
+    # ---- Checks 19-23: dispatch-note entries (Forager addition) ------------
+    def check19():
+        text = _minimal_record(_minimal_intent(), _minimal_note())
+        entries, errors, unterminated, _ = validate_entries(text)
+        assert not errors, f"a well-formed dispatch-note produced errors: {errors}"
+        assert len(entries) == 2
+        assert unterminated == ["2026-01-01-01"], (
+            f"a dispatch-note was counted as opening or closing an intent: "
+            f"{unterminated}")
+
+    check("check19_well_formed_dispatch_note_accepted",
+          "a well-formed dispatch-note is rejected as an invalid Kind, or "
+          "is counted as opening or closing an intent",
+          check19)
+
+    def check20():
+        text = _minimal_record(_minimal_note(Report=None))
+        _, errors, _, _ = validate_entries(text)
+        assert any("2026-01-01-05" in e and "'Report'" in e for e in errors), (
+            f"dispatch-note missing 'Report' not reported by ID and field: {errors}")
+
+    check("check20_dispatch_note_missing_field_names_fault",
+          "a dispatch-note missing a required field is accepted, or the "
+          "error does not name both the entry and the field",
+          check20)
+
+    def check21():
+        text = _minimal_record(_minimal_note(Outcome="completed"))
+        _, errors, _, _ = validate_entries(text)
+        assert any("2026-01-01-05" in e and "Outcome" in e and "'completed'" in e
+                   for e in errors), (
+            f"dispatch-note with an Outcome outside answered/declined/exercise "
+            f"was accepted: {errors}")
+
+    check("check21_dispatch_note_rejects_unknown_outcome",
+          "a dispatch-note whose Outcome is not answered, declined or "
+          "exercise is accepted",
+          check21)
+
+    def check22():
+        text = _minimal_record(_minimal_note(Type="audit"))
+        _, errors, _, _ = validate_entries(text)
+        assert any("2026-01-01-05" in e and "Type" in e and "'audit'" in e
+                   for e in errors), (
+            f"dispatch-note with a Type outside build/device/pulse was "
+            f"accepted: {errors}")
+
+    check("check22_dispatch_note_rejects_unknown_type",
+          "a dispatch-note whose Type is not build, device or pulse is "
+          "accepted",
+          check22)
+
+    def check23():
+        text = _minimal_record(_minimal_intent(),
+                               _minimal_note(Closes="2026-01-01-01"))
+        _, errors, unterminated, _ = validate_entries(text)
+        assert any("2026-01-01-05" in e and "'Closes'" in e for e in errors), (
+            f"a dispatch-note carrying Closes was accepted: {errors}")
+        assert unterminated == ["2026-01-01-01"], (
+            f"a dispatch-note's Closes field closed an intent: {unterminated}")
+
+    check("check23_dispatch_note_cannot_close_an_intent",
+          "a dispatch-note carrying a Closes field is accepted, or closes "
+          "the intent it names",
+          check23)
+
+    total = 23
     print(f"\n{'FAIL' if failures else 'PASS'}: {len(failures)} of "
-          f"{18} checks failed{': ' + ', '.join(failures) if failures else ''}")
+          f"{total} checks failed{': ' + ', '.join(failures) if failures else ''}")
     return 1 if failures else 0
 
 
```

### `check_prompts.py`

```diff
--- upstream 35e39d1/check_prompts.py
+++ accountability-phase-1/check_prompts.py
@@ -169,6 +169,17 @@
                 f"{DISPATCH_FIELD!r} field: {', '.join(ids)}")
     errors.extend(duplicate_errors)
 
+    # Forager addition (RECORD.md 2026-09-22-03): a dispatch-note claims
+    # exactly one *preserved* prompt -- the hook's verbatim capture -- never
+    # a recovered one.
+    for entry_id, value in claims:
+        kind = kind_closes.get(entry_id, ("", ""))[0]
+        if kind == cr.NOTE_KIND and not value.startswith(f"{PROVENANCE_DIRS[0]}/"):
+            errors.append(
+                f"entry {entry_id}: a {cr.NOTE_KIND} may only claim a prompt "
+                f"under {PROMPTS_DIR}/{PROVENANCE_DIRS[0]}/, but its "
+                f"{DISPATCH_FIELD!r} names {value!r}")
+
     missing = []
     for entry_id, value in claims:
         if value not in on_disk:
@@ -229,5 +240,118 @@
     return 1
 
 
+# --------------------------------------------------------------------------
+# Self-tests (Forager addition). Store-level fixtures under a temp dir,
+# checked by both this script's binding check and check_record.py's entry
+# validation, because a dispatch-note is only a valid claim if it is also a
+# valid entry.
+# --------------------------------------------------------------------------
+
+def _store(tmp, files, entries):
+    root = Path(tmp)
+    for rel in files:
+        path = root / PROMPTS_DIR / rel
+        path.parent.mkdir(parents=True, exist_ok=True)
+        path.write_text(f"prompt {rel}\n")
+    text = cr._minimal_record(*entries)
+    (root / RECORD_NAME).write_text(text)
+    return text
+
+
+def _store_errors(tmp, files, entries):
+    """Binding errors plus entry-validation errors, for one fixture store."""
+    text = _store(tmp, files, entries)
+    _, binding_errors, _, _ = check_binding(tmp)
+    _, entry_errors, _, _ = cr.validate_entries(text)
+    return binding_errors, entry_errors
+
+
+def render_check():
+    import tempfile
+    print("check_prompts.py --render-check")
+    failures = []
+
+    def check(name, expect_fail_msg, fn):
+        print(f"\n[{name}] expected failure mode if broken: {expect_fail_msg}")
+        try:
+            fn()
+            print(f"[{name}] PASS")
+        except Exception as e:
+            print(f"[{name}] FAIL: {e!r}")
+            failures.append(name)
+
+    pulse = "preserved/2026-01-01-05.md"
+
+    def p1():
+        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
+        binding, _ = _store_errors(tmp, [pulse], [cr._minimal_intent()])
+        assert any(pulse in e and "no RECORD.md entry" in e for e in binding), (
+            f"an unclaimed pulse prompt was not reported: {binding}")
+
+    check("p1_unclaimed_pulse_prompt_fails",
+          "a preserved prompt no entry claims passes the binding check",
+          p1)
+
+    def p2():
+        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
+        binding, entry = _store_errors(
+            tmp, [pulse], [cr._minimal_intent(), cr._minimal_note()])
+        assert not binding, f"a dispatch-note's claim was not accepted: {binding}"
+        assert not entry, f"the claiming dispatch-note is not a valid entry: {entry}"
+
+    check("p2_pulse_prompt_with_its_note_passes",
+          "a pulse prompt claimed by a well-formed dispatch-note fails "
+          "either the binding check or entry validation",
+          p2)
+
+    def p3():
+        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
+        stray = "recovered/2026-01-01-05.md"
+        binding, _ = _store_errors(
+            tmp, [stray],
+            [cr._minimal_note(**{"Dispatch-file": stray})])
+        assert any("2026-01-01-05" in e and "preserved/" in e for e in binding), (
+            f"a dispatch-note claiming a prompt outside preserved/ was "
+            f"accepted: {binding}")
+
+    check("p3_sabotaged_note_outside_preserved_fails",
+          "a dispatch-note claiming a prompt outside prompts/preserved/ "
+          "is accepted as a claim",
+          p3)
+
+    def p4():
+        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
+        _, entry = _store_errors(
+            tmp, [pulse], [cr._minimal_note(Outcome="done")])
+        assert any("Outcome" in e and "'done'" in e for e in entry), (
+            f"a dispatch-note with a sabotaged Outcome was accepted: {entry}")
+
+    check("p4_sabotaged_note_outcome_fails",
+          "a dispatch-note with an Outcome outside answered, declined and "
+          "exercise is accepted",
+          p4)
+
+    def p5():
+        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
+        binding, _ = _store_errors(
+            tmp, [pulse],
+            [cr._minimal_intent(**{"Dispatch-file": pulse}), cr._minimal_note()])
+        assert any("claimed by more than one" in e for e in binding), (
+            f"a prompt claimed by both an intent and a dispatch-note was "
+            f"accepted: {binding}")
+
+    check("p5_note_and_intent_on_one_prompt_fails",
+          "one prompt claimed by both an intent and a dispatch-note is "
+          "accepted",
+          p5)
+
+    total = 5
+    print(f"\n{'FAIL' if failures else 'PASS'}: {len(failures)} of "
+          f"{total} checks failed{': ' + ', '.join(failures) if failures else ''}")
+    return 1 if failures else 0
+
+
 if __name__ == "__main__":
+    if "--render-check" in sys.argv:
+        sys.exit(render_check())
     sys.exit(main())
```

