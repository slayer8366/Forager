"""PreToolUse, every tool: what the planner and pulse roles may do.

The planner, which is the main session, is read-only. Settings deny rules
cannot express this: on Claude Code 2.1.280 a settings deny reaches
subagents too, so it would deny the coder as well. The hook input's
agent_type is what tells the agents apart: on 2.1.280 it is absent in the
main session and set to the subagent's name inside one.

The main session always has the planner role. A subagent has the role
agent_roles gives it in .claude/kit.json, and its restrictions come from
that role, not from its name. A subagent with no entry in agent_roles, or
whose role is not one of the kit's roles below, is denied every tool.

- planner: an allowlist of tools; every other tool, including every mcp__
  tool and any tool added later, is denied until the operator adds it by
  name. Bash is limited to read-only git and gh.

  SendMessage (planner only): the target must be an agent this session
  started, that is, an ID that `toolUseResult.agentId` gives on the
  transcript line holding the result of one of the session's own Agent
  calls (matched by tool_use_id). Any other target is denied, and so is
  every target when the transcript cannot be read. The hook asks nothing:
  each message is approved by the operator before it is sent, because the
  operator writes or requests it, or the planner offers it and the operator
  agrees. No message is sent on the planner's own initiative. The hook
  cannot check that approval; coder.md item 6 makes a coder stop on a
  message that widens its scope without quoting an owner ruling. The
  transcript is written asynchronously, so a message sent just after an
  Agent result can be denied until the result is on disk; send it again.

  TaskStop (planner only): the same target rule as SendMessage, applied to
  task_id; shell_id is always denied, since the planner starts no background
  shells. A stop is approved by the operator before it is made, as a message
  is: the operator asks for it, or the planner offers it and the operator
  agrees. A stopped agent writes no terminal of its own; its open intent is
  closed by the next dispatch's record, citing the stop by planner-log line.
- pulse: Read, Grep, Glob, SubagentHandback (to deliver its report), and
  Bash limited to read-only git and gh plus the adb reads getprop, dumpsys
  and screencap. device_guard.py separately
  checks the foreground app before any screencap.
- coder: not restricted here. device_guard.py and history_guard.py apply
  to everyone.

The Bash checks read a command's tokens. The named changes, checked first
so that each is denied by name, are read per segment (split at the shell
punctuation tokens) at the segment's command word, found as history_guard
finds it (its `command_start` drops the leading shell keywords, wrappers
and assignments; its `git_invocation` reads git's global options): `git`
by basename with the subcommand `commit`, `push`, `checkout`, `reset` or
`merge`; `rm`; `mv`; `sed` with a token that is `-i`, starts with `-i` as
a short option, or is `--in-place` with or without `=`; and a command word
whose basename is `gradle`, `gradlew` or `gradlew.bat`. A guarded word
inside an argument (`git log --grep "rm "`, `git grep "sed -i"`) denies
nothing. The named check runs before the punctuation rule, so `git log; rm
x` is denied by name. The checks hold the command forms an agent usually
writes, not every program that could do the same thing.

Known bypasses: .claude/hooks/BYPASSES.md B-08, B-09
"""
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402
import history_guard as hg  # noqa: E402  (command_start, git_invocation)

# Grep and Glob are not on the planner allowlist: neither exists as a tool on
# Claude Code 2.1.280. They are still listed for the pulse, where they are
# inert on that version.
PLANNER_TOOLS = {"Read", "Bash", "Agent", "Skill", "WebFetch",
                 "WebSearch", "AskUserQuestion", "ToolSearch", "TodoWrite",
                 "SendMessage", "TaskStop"}
# SubagentHandback delivers a subagent's report to its caller; without it a
# pulse runs and delivers nothing. The pulse's only, not the planner's.
PULSE_TOOLS = {"Read", "Grep", "Glob", "Bash", "SubagentHandback"}
# The kit's roles. agent_roles maps agents onto these; any other role name
# gets nothing.
KIT_ROLES = ("coder", "planner", "pulse")

# The named changes (module docstring), read at a segment's command word.
NAMED_GIT = {"commit", "push", "checkout", "reset", "merge"}
NAMED_COMMANDS = {"rm", "mv"}
GRADLE = {"gradle", "gradlew", "gradlew.bat"}
ADB = re.compile(r"\badb\b")

READ_ONLY_GIT = {"log", "show", "diff", "status", "rev-parse", "ls-files",
                 "ls-tree", "ls-remote", "blame", "cat-file", "describe",
                 "shortlog", "grep", "merge-base", "rev-list", "show-ref"}
GIT_BRANCH_FLAGS = {"-a", "-r", "-v", "-vv", "--all", "--remotes", "--list",
                    "--show-current"}
GH_READ = {("pr", "view"), ("pr", "list"), ("pr", "diff"), ("pr", "checks"),
           ("pr", "status"), ("issue", "view"), ("issue", "list"),
           ("issue", "status"), ("repo", "view"), ("run", "view"),
           ("run", "list"), ("release", "view"), ("release", "list")}
GH_API_WRITE_FLAGS = {"-X", "--method", "-f", "-F", "--field", "--raw-field",
                      "--input"}

ADB_GLOBAL_WITH_ARG = {"-s", "-t", "-H", "-P", "-L"}
ADB_GLOBAL_FLAGS = {"-d", "-e", "-a"}
ADB_READ_TOP = {"devices", "get-state", "get-serialno"}
ADB_SHELL_READS = {"getprop", "dumpsys", "screencap"}
DUMPSYS_MUTATORS = {"set", "reset", "unplug", "clear", "enable", "disable",
                    "--reset", "--clear"}


def sed_in_place(args):
    return any(a == "-i" or (a.startswith("-i") and not a.startswith("--"))
               or a == "--in-place" or a.startswith("--in-place=") for a in args)


def named_segment(seg):
    """The named change a segment runs (module docstring), else None."""
    inv = hg.git_invocation(seg)
    if inv:
        return f"git {inv[1]}" if inv[1] in NAMED_GIT else None
    seg = seg[hg.command_start(seg):]
    if not seg:
        return None
    word = os.path.basename(seg[0])
    if word in NAMED_COMMANDS:
        return word
    if word == "sed" and sed_in_place(seg[1:]):
        return "sed -i"
    if word in GRADLE:
        return "gradle"
    return None


def named_change(tokens):
    """The name of the first command among tokens' segments that changes the
    repository or build, else None. Punctuation tokens end a segment."""
    seg = []
    for t in tokens + [";"]:
        if not g.PUNCTUATION.match(t):
            seg.append(t)
            continue
        name = named_segment(seg)
        if name:
            return name
        seg = []
    return None

def read_only_git(tokens):
    i = 1
    while i < len(tokens) and tokens[i].startswith("-"):
        if tokens[i] == "-C" and i + 1 < len(tokens):
            i += 2
        elif tokens[i] == "--no-pager":
            i += 1
        else:
            return f"git option {tokens[i]!r} is not allowed"
    if i >= len(tokens):
        return "bare `git` is not a read"
    sub, args = tokens[i], tokens[i + 1:]
    if any(a.startswith("--output") for a in args):
        return "`--output` writes a file"
    if sub in READ_ONLY_GIT:
        return None
    if sub == "branch" and all(a in GIT_BRANCH_FLAGS for a in args):
        return None
    if sub == "remote" and (not args or args == ["-v"] or
                            (args[0] == "get-url" and len(args) == 2)):
        return None
    if sub == "config" and args and args[0] in {"--get", "--get-all", "--list", "-l"}:
        return None
    if sub == "stash" and args[:1] in (["list"], ["show"]):
        return None
    if sub == "worktree" and args[:1] == ["list"]:
        return None
    return f"`git {sub}` is not on the read-only git list"


def read_only_gh(tokens):
    if len(tokens) >= 2 and tokens[1] == "api":
        bad = [a for a in tokens[2:] if a.split("=", 1)[0] in GH_API_WRITE_FLAGS
               or re.match(r"^-[XfF].", a)]
        return f"`gh api` with {bad[0]!r} can write" if bad else None
    if len(tokens) >= 3 and (tokens[1], tokens[2]) in GH_READ:
        return None
    return f"`{' '.join(tokens[:3])}` is not on the read-only gh list"


def adb_read(tokens):
    i = 1
    while i < len(tokens) and tokens[i].startswith("-"):
        if tokens[i] in ADB_GLOBAL_WITH_ARG and i + 1 < len(tokens):
            i += 2
        elif tokens[i] in ADB_GLOBAL_FLAGS:
            i += 1
        else:
            return f"adb option {tokens[i]!r} is not allowed"
    rest = tokens[i:]
    if not rest:
        return "bare `adb` is not a read"
    if rest[0] in ADB_READ_TOP and len(rest) <= 2:
        return None
    if rest[0] in ("shell", "exec-out") and len(rest) >= 2:
        remote = rest[1:]
        # A quoted remote command arrives as one token; split it for the check.
        if len(remote) == 1 and " " in remote[0]:
            remote = remote[0].split()
        if any(re.search(r"[;&|`$<>]", t) for t in remote):
            return "the remote command contains shell punctuation"
        if remote[0] not in ADB_SHELL_READS:
            return f"`adb {rest[0]} {remote[0]}` is not one of getprop, dumpsys, screencap"
        if remote[0] == "dumpsys" and DUMPSYS_MUTATORS & set(remote[1:]):
            return "this dumpsys form changes device state"
        return None
    return f"`adb {rest[0]}` is not a device read"


def check_bash(command, role):
    try:
        tokens = g.shell_tokens(command)
    except ValueError as exc:
        return f"the command could not be parsed ({exc}), so it is not run"
    if g.has_redirection(tokens):
        return "redirection (`>`, `>>`, `<`) writes or reads files outside the read tools"
    name = named_change(tokens)
    if name:
        return f"`{name}` changes the repository or build"
    if role == "planner" and ADB.search(command):
        return "`adb` is for the pulse role; dispatch a pulse for device reads"
    if any(g.PUNCTUATION.match(t) for t in tokens) or "$" in command or "`" in command:
        return "compound commands, pipes, substitution and variables are not allowed; run one read at a time"
    if not tokens:
        return "empty command"
    head = tokens[0]
    if head == "git":
        return read_only_git(tokens)
    if head == "gh":
        return read_only_gh(tokens)
    if head == "adb" and role == "pulse":
        return adb_read(tokens)
    allowed = "git and gh" + (" and adb reads" if role == "pulse" else "")
    return f"`{head}` is not read-only {allowed}"


SEND_RULE = ("role_guard: the planner may SendMessage only to an agent this "
             "session started (an ID that toolUseResult.agentId gives on the "
             "transcript line holding the result of one of the session's own "
             "Agent calls).")


def own_agent_ids(transcript_path):
    """The agent IDs the session's own Agent calls returned, read from its
    JSONL transcript. Lines that do not decode as UTF-8 or parse as JSON are
    skipped. Raises OSError if the file cannot be read."""
    agent_calls, results = set(), []
    with open(transcript_path, "rb") as f:
        for raw in f:
            try:
                line = json.loads(raw.decode("utf-8"))
            except ValueError:
                continue
            if not isinstance(line, dict):
                continue
            message = line.get("message")
            content = message.get("content") if isinstance(message, dict) else None
            if not isinstance(content, list):
                continue
            blocks = [b for b in content if isinstance(b, dict)]
            for b in blocks:
                if b.get("type") == "tool_use" and b.get("name") == "Agent":
                    agent_calls.add(b.get("id"))
            tool_results = [b for b in blocks if b.get("type") == "tool_result"]
            use_result = line.get("toolUseResult")
            if len(tool_results) == 1 and isinstance(use_result, dict):
                agent_id = use_result.get("agentId")
                if isinstance(agent_id, str) and agent_id:
                    results.append((tool_results[0].get("tool_use_id"), agent_id))
    return {agent_id for use_id, agent_id in results if use_id in agent_calls}


def check_send_message(payload):
    tool_input = payload.get("tool_input")
    to = tool_input.get("to") if isinstance(tool_input, dict) else None
    if not isinstance(to, str) or not to.strip():
        return f"{SEND_RULE} Blocked: `to` is missing or empty."
    target = to.strip()
    path = payload.get("transcript_path")
    if not isinstance(path, str) or not path:
        return (f"{SEND_RULE} Blocked: target {target!r}; the hook input has no "
                f"transcript_path, so the session's agents cannot be read.")
    try:
        ids = own_agent_ids(path)
    except OSError as exc:
        return (f"{SEND_RULE} Blocked: target {target!r}; the transcript could "
                f"not be read ({type(exc).__name__}: {exc}).")
    if target not in ids:
        return (f"{SEND_RULE} Blocked: target {target!r} is not one of them. "
                f"If its Agent call has only just returned, send again once "
                f"the result is on disk.")
    return None


STOP_RULE = ("role_guard: the planner may TaskStop only an agent this session "
             "started (an ID that toolUseResult.agentId gives on the transcript "
             "line holding the result of one of the session's own Agent calls), "
             "named by task_id; shell_id is always denied.")


def check_task_stop(payload):
    tool_input = payload.get("tool_input")
    if not isinstance(tool_input, dict):
        tool_input = {}
    shell_id = tool_input.get("shell_id")
    if shell_id is not None and shell_id != "":
        return (f"{STOP_RULE} Blocked: shell_id {shell_id!r} is set; the planner "
                f"starts no background shells.")
    task_id = tool_input.get("task_id")
    if not isinstance(task_id, str) or not task_id.strip():
        return f"{STOP_RULE} Blocked: `task_id` is missing or empty."
    target = task_id.strip()
    path = payload.get("transcript_path")
    if not isinstance(path, str) or not path:
        return (f"{STOP_RULE} Blocked: target {target!r}; the hook input has no "
                f"transcript_path, so the session's agents cannot be read.")
    try:
        ids = own_agent_ids(path)
    except OSError as exc:
        return (f"{STOP_RULE} Blocked: target {target!r}; the transcript could "
                f"not be read ({type(exc).__name__}: {exc}).")
    if target not in ids:
        return (f"{STOP_RULE} Blocked: target {target!r} is not one of them. "
                f"If its Agent call has only just returned, try again once the "
                f"result is on disk.")
    return None


def guard(payload):
    agent = payload.get("agent_type")
    if not agent:
        who = "planner"
    else:
        roles = g.CONFIG["agent_roles"]
        if agent not in roles:
            return ("deny", f"role_guard: agent {agent!r} has no role in agent_roles, "
                            f"so it may use no tool. Give it one of the kit's roles "
                            f"({', '.join(KIT_ROLES)}) in .claude/kit.json.")
        who = roles[agent]
        if who not in KIT_ROLES:
            return ("deny", f"role_guard: agent {agent!r} has role {who!r}, which is "
                            f"not one of the kit's roles ({', '.join(KIT_ROLES)}), so "
                            f"it may use no tool.")
    if who == "coder":
        return None
    tool = payload.get("tool_name", "")
    allowed = PLANNER_TOOLS if who == "planner" else PULSE_TOOLS
    if tool not in allowed:
        return ("deny", f"role_guard: the {who} role may not use {tool}. The "
                        f"{who} allowlist is {', '.join(sorted(allowed))}; any "
                        f"other tool is denied until the operator adds it by name.")
    if tool == "Bash":
        problem = check_bash(g.command_of(payload), who)
        if problem:
            return ("deny", f"role_guard: the {who} role's Bash is read-only "
                            f"({'git and gh' if who == 'planner' else 'git, gh and adb reads'}). "
                            f"Blocked: {problem}.")
    if tool == "SendMessage" and who == "planner":
        problem = check_send_message(payload)
        if problem:
            return ("deny", problem)
    if tool == "TaskStop" and who == "planner":
        problem = check_task_stop(payload)
        if problem:
            return ("deny", problem)
    return None


if __name__ == "__main__":
    sys.exit(g.run(guard))
