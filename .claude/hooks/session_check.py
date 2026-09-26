"""SessionStart, every source: warn when this session's hooks may be stale.

Claude Code reads the hooks when a session starts, so a session keeps the
guards its checkout had then. This hook compares that checkout with what is
expected and warns; it never blocks.

The rule:

1. The repository is `git rev-parse --show-toplevel` run from the payload's
   `cwd`. The hook reads `.claude/kit.json` there; if that fails, it warns
   about it and checks nothing else. The expected branch is the first of
   `protected_branches`, compared as `origin/<branch>` as last fetched. The
   hook never fetches. If that ref does not exist, it warns "cannot compare"
   and names the ref.
2. Stale check: `git diff --name-only origin/<branch> -- .claude/hooks
   .claude/agents .claude/settings.json .claude/kit.json`. This compares the
   working tree, not HEAD, so uncommitted edits count. Any path it prints
   differs from the default branch. `git rev-list --count
   HEAD..origin/<branch>` is also reported when above 0.
3. Lock check, only if `.claude/kit.lock` exists: every file the lock lists
   is compared by sha256 with the lock, and missing or differing files are
   reported. It runs whether or not the ref in 1 exists.
4. Output. When anything is reported, one JSON object on stdout: a short
   `systemMessage` for the user, and `hookSpecificOutput`
   `{"hookEventName": "SessionStart", "additionalContext": <the full text>}`.
   The full text holds the differing paths, the commit count, and, as advice
   that is never run, `git -C <toplevel> fetch && git -C <toplevel> merge
   --ff-only origin/<branch>`. If `<toplevel>/update_worktree.py` is a file,
   the advice is instead `python3 <toplevel>/update_worktree.py <toplevel>`
   (dry run), then the same with `--apply`, which moves aside untracked
   copies that the branch now tracks, then fast-forwards. If
   `.claude/settings.json` differs it adds
   "settings.json differs: start a new session after updating, since hooks
   are read at session start". When nothing differs it prints nothing.
5. Never block. It always exits 0, and reports any internal error as a
   warning in the same form. It writes nothing: git runs with
   GIT_OPTIONAL_LOCKS=0, so no index refresh is written. Each git runs with
   a 20-second timeout, overridden by the environment variable
   KIT_GUARD_TIMEOUT when it is a positive number (the kit's command-timeout
   override, read under the default guard_env_prefix because this hook runs
   git before it can read kit.json); a timeout is reported as an internal
   error, like any other.

Unlike the PreToolUse guards it does not use guardlib.run(), which denies on
a config error.

Known bypasses: .claude/hooks/BYPASSES.md none
"""
import hashlib
import json
import os
import subprocess
import sys

HOOK = "session_check"
PATHS = [".claude/hooks", ".claude/agents", ".claude/settings.json", ".claude/kit.json"]
SETTINGS = ".claude/settings.json"
LOCK = ".claude/kit.lock"
UPDATER = "update_worktree.py"
RESTART = ("settings.json differs: start a new session after updating, since "
           "hooks are read at session start")


def git_timeout(default=20):
    """KIT_GUARD_TIMEOUT as a positive number of seconds, else default."""
    try:
        value = float(os.environ.get("KIT_GUARD_TIMEOUT"))
    except (TypeError, ValueError):
        return default
    return value if 0 < value < float("inf") else default


GIT_TIMEOUT = git_timeout()


class Problem(Exception):
    """A condition to report and stop at (no repository, no usable kit.json)."""


def git(top, *args):
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
    return subprocess.run(["git", "-C", top, *args], capture_output=True, text=True,
                          env=env, timeout=GIT_TIMEOUT)


def toplevel(cwd):
    proc = git(cwd, "rev-parse", "--show-toplevel")
    if proc.returncode != 0:
        raise Problem(f"{cwd} is not in a git repository, so this session's hooks "
                      f"cannot be checked: {proc.stderr.strip()}")
    return proc.stdout.strip()


def expected_branch(top):
    path = os.path.join(top, ".claude", "kit.json")
    try:
        with open(path, encoding="utf-8") as f:
            config = json.load(f)
        branches = config["protected_branches"]
        if not isinstance(branches, list) or not branches or not isinstance(branches[0], str):
            raise ValueError("protected_branches must be a non-empty list of branch names")
        return branches[0]
    except Exception as exc:
        raise Problem(f"{path} could not be read, so this session's hooks cannot be "
                      f"checked: {type(exc).__name__}: {exc}")


def stale_findings(top, branch):
    """(lines, differing paths, whether the ref exists)."""
    ref = f"origin/{branch}"
    if git(top, "rev-parse", "--verify", "--quiet", f"refs/remotes/{ref}^{{commit}}").returncode != 0:
        return [f"cannot compare: {ref} does not exist in this clone (as of the last "
                f"fetch), so this checkout's .claude/ was not checked against it."], [], False
    lines = []
    proc = git(top, "diff", "--name-only", ref, "--", *PATHS)
    if proc.returncode != 0:
        raise RuntimeError(f"git diff against {ref} failed: {proc.stderr.strip()}")
    paths = [p for p in proc.stdout.splitlines() if p]
    if paths:
        lines.append(f"These files differ from {ref} (as of the last fetch; working tree, "
                     f"uncommitted edits included):")
        lines.extend(f"  {p}" for p in paths)
    proc = git(top, "rev-list", "--count", f"HEAD..{ref}")
    if proc.returncode != 0:
        raise RuntimeError(f"git rev-list against {ref} failed: {proc.stderr.strip()}")
    behind = int(proc.stdout.strip() or "0")
    if behind > 0:
        lines.append(f"HEAD is {behind} commit{'s' if behind != 1 else ''} behind {ref} "
                     f"(as of the last fetch).")
    return lines, paths, True


def lock_findings(top):
    path = os.path.join(top, LOCK)
    if not os.path.exists(path):
        return []
    try:
        with open(path, encoding="utf-8") as f:
            files = json.load(f)["files"]
        if not isinstance(files, dict):
            raise ValueError("its files entry is not an object")
    except Exception as exc:
        return [f"{LOCK} could not be read, so vendored files were not checked: "
                f"{type(exc).__name__}: {exc}"]
    problems = []
    for rel, expected in sorted(files.items()):
        full = os.path.join(top, rel)
        if not os.path.isfile(full):
            problems.append(f"  {rel}: missing")
            continue
        with open(full, "rb") as f:
            actual = hashlib.sha256(f.read()).hexdigest()
        if actual != expected:
            problems.append(f"  {rel}: sha256 {actual} differs from the lock's {expected}")
    if not problems:
        return []
    return [f"These files differ from {LOCK}:"] + problems


def check(payload):
    """(systemMessage, additionalContext), or None when nothing differs."""
    cwd = payload.get("cwd") if isinstance(payload, dict) else None
    if not isinstance(cwd, str) or not cwd:
        raise Problem("the SessionStart payload has no cwd, so this session's hooks "
                      "cannot be checked")
    top = toplevel(cwd)
    try:
        branch = expected_branch(top)
    except Problem as exc:
        return (f"Claude-kit {HOOK}: .claude/kit.json could not be read; this "
                f"session's hooks were not checked.", str(exc))
    lines, paths, ref_exists = stale_findings(top, branch)
    lock = lock_findings(top)
    if not lines and not lock:
        return None
    ref = f"origin/{branch}"
    text = [f"Claude-kit {HOOK}: this session's hooks may be stale. Claude Code read "
            f"them from {top} when the session started."]
    text += lines + lock
    updater = os.path.join(top, UPDATER)
    if os.path.isfile(updater):
        text.append(f"To update (advice; not run by the hook): `python3 {updater} {top}` "
                    f"(dry run), then the same with `--apply`. It moves aside untracked "
                    f"copies that the branch now tracks, then fast-forwards.")
    else:
        text.append(f"To update (advice; not run by the hook): git -C {top} fetch && "
                    f"git -C {top} merge --ff-only {ref}")
    if SETTINGS in paths:
        text.append(RESTART)
    if not ref_exists:
        short = f"cannot compare with {ref}"
    else:
        parts = []
        if paths:
            parts.append(f"{len(paths)} file(s) under .claude/ differ from {ref}")
        behind = [l for l in lines if l.startswith("HEAD is ")]
        if behind:
            parts.append(behind[0].split(" (as of")[0])
        if not parts:
            parts.append(f"no difference from {ref}")
        short = "; ".join(parts)
    if lock:
        short += f"; files differ from {LOCK}"
    if SETTINGS in paths:
        short += "; settings.json differs, so start a new session after updating"
    return (f"Claude-kit {HOOK}: {short} (as of the last fetch). See the session "
            f"context for the files and the update command.", "\n".join(text))


def main():
    try:
        try:
            payload = json.loads(sys.stdin.read())
            result = check(payload)
        except Problem as exc:
            result = (f"Claude-kit {HOOK}: this session's hooks were not checked.", str(exc))
    except Exception as exc:  # never block; say what went wrong instead
        result = (f"Claude-kit {HOOK}: internal error; this session's hooks were not "
                  f"checked.", f"Claude-kit {HOOK} failed: {type(exc).__name__}: {exc}")
    if result is not None:
        message, context = result
        print(json.dumps({"systemMessage": message,
                          "hookSpecificOutput": {"hookEventName": "SessionStart",
                                                 "additionalContext": context}}))
    return 0


if __name__ == "__main__":
    try:
        main()
    except BaseException:  # even a failure to print must not become a non-zero exit
        pass
    sys.exit(0)
