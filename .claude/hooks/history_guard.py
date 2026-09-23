"""PreToolUse on Bash, every role: history is the operator's.

Merging is the operator's approval (design, "History"), and a rewrite is
run by the operator by hand (decision F). Blocked:

- git push --force, --force-with-lease, -f (alone or in a cluster);
- git merge while the repository is on main;
- git push to main: an explicit main refspec, --all or --mirror, or a push
  with no refspec (or HEAD) while on main;
- gh pr merge;
- git filter-repo and git filter-branch.

Force, gh pr merge and the filters match the whole command text. Push
refspecs and the merge branch check are parsed per command segment, so a
command hidden in a quoted string (sh -c '...') is seen by the first group
and not by the second; see the bypass table in the completion report.
"""
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402

GIT_OPTS = r"(?:\s+(?:-C\s+\S+|-c\s+\S+|--no-pager|--git-dir=\S+|--work-tree=\S+))*"
FORCE_PUSH = re.compile(
    r"\bgit\b" + GIT_OPTS + r"\s+push\b[^;&|]*?\s"
    r"(--force(?:-with-lease)?(?:=\S*)?|-[A-Za-z]*f[A-Za-z]*)(?=\s|$)")
PR_MERGE = re.compile(r"\bgh\b[^;&|]*\spr\s+merge\b")
FILTERS = re.compile(r"\bgit(?:\s+|-)(filter-(?:repo|branch))\b")
MERGE = re.compile(r"\bgit\b((?:\s+(?:-C\s+\S+|-c\s+\S+|--no-pager))*)\s+merge\b(?!-)")
MAIN_REFS = {"main", "refs/heads/main"}
PUSH_OPTS_WITH_VALUE = {"-o", "--push-option", "--repo", "--receive-pack", "--exec"}


def current_branch(directory):
    r = subprocess.run(["git", "-C", directory, "symbolic-ref", "--short", "-q", "HEAD"],
                       capture_output=True, text=True)
    if r.returncode == 0:
        return r.stdout.strip(), None
    if r.returncode == 1 and not r.stderr.strip():
        return "(detached HEAD)", None
    return None, r.stderr.strip() or f"git exited {r.returncode}"


def segments(tokens):
    seg = []
    for t in tokens:
        if g.PUNCTUATION.match(t):
            if seg:
                yield seg
            seg = []
        else:
            seg.append(t)
    if seg:
        yield seg


def git_invocation(seg):
    """(directory override or None, subcommand, args) for a segment that
    runs git, else None."""
    if not seg or seg[0] != "git":
        return None
    i, directory = 1, None
    while i < len(seg) and seg[i].startswith("-"):
        if seg[i] in ("-C", "-c") and i + 1 < len(seg):
            if seg[i] == "-C":
                directory = seg[i + 1]
            i += 2
        else:
            i += 1
    if i >= len(seg):
        return None
    return directory, seg[i], seg[i + 1:]


def push_targets_main(args, branch):
    positional, i = [], 0
    while i < len(args):
        a = args[i]
        if a in ("--all", "--mirror"):
            return f"`git push {a}` pushes main along with every other branch"
        if a in PUSH_OPTS_WITH_VALUE:
            i += 2
            continue
        if not a.startswith("-"):
            positional.append(a)
        i += 1
    refspecs = positional[1:]
    if not refspecs:
        return (f"a push with no refspec pushes the current branch, which is main"
                if branch == "main" else None)
    for spec in refspecs:
        spec = spec.lstrip("+")
        src, _, dst = spec.partition(":")
        dst = dst or src
        if dst == "HEAD":
            dst = branch
        if dst in MAIN_REFS:
            return f"refspec {spec!r} pushes to main"
    return None


def guard(payload):
    if payload.get("tool_name") != "Bash":
        return None
    command = g.command_of(payload)
    cwd = payload.get("cwd") or "."

    m = FORCE_PUSH.search(command)
    if m:
        return ("deny", f"history_guard: force-push (`{m.group(1)}`) rewrites "
                        f"history on the remote and is never run by an agent.")
    if PR_MERGE.search(command):
        return ("deny", "history_guard: `gh pr merge` is blocked. Merging is the "
                        "operator's approval.")
    m = FILTERS.search(command)
    if m:
        return ("deny", f"history_guard: `git {m.group(1)}` rewrites history and "
                        f"is run by the operator by hand (decision F).")

    for m in MERGE.finditer(command):
        dash_c = re.search(r"-C\s+(\S+)", m.group(1) or "")
        directory = dash_c.group(1).strip("'\"") if dash_c else cwd
        branch, err = current_branch(directory)
        if err:
            return ("deny", f"history_guard: `git merge` blocked: could not read "
                            f"the current branch of {directory} ({err}).")
        if branch == "main":
            return ("deny", "history_guard: `git merge` while on main is blocked. "
                            "Merging into main is the operator's approval.")

    try:
        tokens = g.shell_tokens(command)
    except ValueError as exc:
        if re.search(r"\bpush\b", command):
            return ("deny", f"history_guard: could not parse this push ({exc}), "
                            f"so its target cannot be checked.")
        return None
    for seg in segments(tokens):
        inv = git_invocation(seg)
        if not inv or inv[1] != "push":
            continue
        directory, _, args = inv
        branch, err = current_branch(directory or cwd)
        if err:
            return ("deny", f"history_guard: push blocked: could not read the "
                            f"current branch ({err}).")
        problem = push_targets_main(args, branch)
        if problem:
            return ("deny", f"history_guard: push to main blocked: {problem}. "
                            f"Main changes only through a PR the operator merges.")
    return None


if __name__ == "__main__":
    sys.exit(g.run(guard))
