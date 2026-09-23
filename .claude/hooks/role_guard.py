"""PreToolUse, every tool: what the planner and pulse roles may do.

Decision A (operator, 2026-09-22): the planner, which is the main session,
is read-only. Settings deny rules cannot express this, because step 0
observed a settings deny reaching subagents too, which would deny the
coder as well; the hook input's agent_type is what tells the roles apart.

- planner: an allowlist of tools (operator ruling, decision A gap); every
  other tool, including every mcp__ tool and any tool added later, is
  denied until the operator adds it by name. Bash is limited to read-only
  git and gh.
- pulse: Read, Grep, Glob, and Bash limited to read-only git and gh plus
  the adb reads getprop, dumpsys and screencap. device_guard.py separately
  checks the foreground app before any screencap.
- coder, and any other subagent: not restricted here. device_guard.py and
  history_guard.py apply to everyone.

The Bash checks are patterns over the command text. They hold the command
forms an agent usually writes, not every program that could do the same
thing; see the bypass table in the completion report.
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402

PLANNER_TOOLS = {"Read", "Grep", "Glob", "Bash", "Agent", "Skill", "WebFetch",
                 "WebSearch", "AskUserQuestion", "ToolSearch", "TodoWrite"}
PULSE_TOOLS = {"Read", "Grep", "Glob", "Bash"}

GIT_PREFIX = r"\bgit\s+(?:(?:-C\s+\S+|-c\s+\S+|--no-pager|--git-dir=\S+|--work-tree=\S+)\s+)*"
# Step 2's required patterns. Checked first so each is blocked by name.
NAMED_PATTERNS = [
    ("git commit", re.compile(GIT_PREFIX + r"commit\b")),
    ("git push", re.compile(GIT_PREFIX + r"push\b")),
    ("git checkout", re.compile(GIT_PREFIX + r"checkout\b")),
    ("git reset", re.compile(GIT_PREFIX + r"reset\b")),
    ("git merge", re.compile(GIT_PREFIX + r"merge\b(?!-)")),
    ("rm", re.compile(r"(?:^|[\s;&|(`])rm\s")),
    ("mv", re.compile(r"(?:^|[\s;&|(`])mv\s")),
    ("sed -i", re.compile(r"\bsed\b[^;&|]*\s(?:-i|--in-place)")),
    # A command word, not a file name: matches `gradle`, `./gradlew`, not `build.gradle.kts`.
    ("gradle", re.compile(r"(?:^|[\s;&|(`/])gradlew?(?:\.bat)?(?=\s|$|[;&|)])")),
]
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
    for name, pattern in NAMED_PATTERNS:
        if pattern.search(command):
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


def guard(payload):
    who = g.role(payload)
    if who not in ("planner", "pulse"):
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
    return None


if __name__ == "__main__":
    sys.exit(g.run(guard))
