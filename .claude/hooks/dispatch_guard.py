"""PreToolUse on the dispatch tool (Agent; Task was its name up to Claude
Code 2.1.63 and still appears in 2.1.280's init tool list).

0. Blocks any target that is not one of the config's dispatchable_agents.
   A built-in agent type carries every tool, so dispatching one would let
   a read-only planner act through it.
1. Reads the prompt's `Type:` line. Missing or unknown blocks.
2. Blocks a type sent to any agent but the one type_targets names for it,
   so a dispatch cannot skip approval by carrying the wrong type.
3. Checks the sections that type requires (required_sections); missing
   ones block, by name.
4. On pass, writes the prompt verbatim to prompts/preserved/<date>-<seq>.md,
   headed with the HEAD hash, the target subagent and the type. Neither
   agent writes this file. <seq> comes from one counter shared by every
   worktree of the clone, `<git common dir>/claude-kit/dispatch-seq`
   (one line `YYYY-MM-DD NN`, UTC), read and advanced under an exclusive
   lock on `dispatch-seq.lock`; it is one above the larger of the
   counter's number for today and the highest name for today in this
   worktree's store, so separate worktrees cannot save different
   dispatches under one name. If the prompt cannot be written or the
   counter cannot be reached, the dispatch is blocked: an unpreserved
   dispatch is the failure this hook exists to prevent.
   Before writing, it looks in this worktree's store (tracked or untracked
   files alike) for a `YYYY-MM-DD-NN.md` file whose text after its first
   delimiter line equals the prompt's UTF-8 bytes. If one does, the prompt
   is a re-send of that dispatch: the new copy's header gets one more line,
   `Repeat-of: preserved/<name>`, naming the earliest match by (date,
   number), and the reason says so. A file that cannot be read is not a
   match. Numbering, approval and blocking are unchanged.
5. Runs check_prompts.py from the repository root and reports its result. It does
   not block on it: the prompt just written is unclaimed until the coder's
   next sweep writes its dispatch-note, and the planner cannot write that
   note, so blocking would stop the planner dispatching the coder that
   fixes it.
6. Returns "allow" for a type listed in approval_exempt_types and "ask"
   (operator approval) for every other type: approval fails closed.

The section check is structural. A dispatch can carry every heading and
still be wrong.

A dispatch that this hook or role_guard blocks is logged in the session log
with `toolDenialKind` (for example "permission-rule") on the tool_result,
not with a `deny` decision.

Every git the hook runs, and the store check, has guardlib's command timeout
(20 seconds by default, `<guard_env_prefix>TIMEOUT` overrides). A git that
times out is a `CommandTimeout` on the preserve path, so the dispatch is
blocked as "could not preserve the dispatch" naming the timeout; a store
check that times out is reported as such and, like any store-check result,
decides nothing.

Known bypasses: .claude/hooks/BYPASSES.md B-07
"""
import datetime
import fcntl
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402

DISPATCH_TOOLS = {"Agent", "Task"}
TYPE_LINE = re.compile(r"(?m)^\s*(?:\*\*Type:\*\*|Type:)\s*(\S+)")
HEADING = re.compile(r"(?m)^#{1,6}\s+(.+?)\s*#*\s*$")
DELIMITER = "--- verbatim prompt follows ---"
COUNTER_DIR = "claude-kit"  # under the git common dir, shared by all worktrees
COUNTER = "dispatch-seq"
STORE_NAME = re.compile(r"(\d{4}-\d{2}-\d{2})-(\d{2,})\.md")
REPEAT_PREFIX = "Repeat-of: preserved/"


def missing_sections(text, required):
    headings = [h.strip().lower() for h in HEADING.findall(text)]
    return [s for s in required
            if not any(h == s.lower() or h.startswith(s.lower() + " ")
                       or h.startswith(s.lower() + "(") for h in headings)]


def git(cwd, *args):
    r = g.run_command(["git", "-C", cwd, *args])
    if r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed in {cwd}: {r.stderr.strip()}")
    return r.stdout.strip()


class CounterUnreachable(RuntimeError):
    def __init__(self, counter, exc):
        super().__init__(f"the shared dispatch counter could not be reached at "
                         f"{counter}: {type(exc).__name__}: {exc}")


def counter_dir(root):
    common = Path(git(root, "rev-parse", "--git-common-dir"))
    if not common.is_absolute():
        common = Path(root) / common
    return common / COUNTER_DIR


def read_counter(counter, date):
    """The counter's number for date, or 0 if it holds another date or
    does not exist yet."""
    try:
        line = counter.read_text().strip()
    except FileNotFoundError:
        return 0
    m = re.fullmatch(r"(\d{4}-\d{2}-\d{2}) (\d+)", line)
    if not m:
        raise ValueError(f"unreadable counter line {line!r}")
    return int(m.group(2)) if m.group(1) == date else 0


def write_counter(counter, date, seq):
    tmp = counter.with_name(counter.name + ".tmp")
    tmp.write_text(f"{date} {seq:02d}\n")
    os.replace(str(tmp), str(counter))


def text_after_delimiter(data):
    """The bytes after the first line equal to DELIMITER, or None."""
    marker = DELIMITER.encode("utf-8")
    offset = 0
    for line in data.splitlines(keepends=True):
        offset += len(line)
        if line.rstrip(b"\r\n") == marker:
            return data[offset:]
    return None


def repeat_of(root, text):
    """The name of the earliest store file, by (date, number), whose text
    after its delimiter equals text exactly, or None."""
    store = Path(root) / "prompts" / "preserved"
    wanted = text.encode("utf-8")
    named = []
    for p in store.glob("*.md") if store.is_dir() else ():
        m = STORE_NAME.fullmatch(p.name)
        if m and p.is_file():
            named.append(((m.group(1), int(m.group(2))), p))
    for _, p in sorted(named):
        try:
            data = p.read_bytes()
        except OSError:
            continue
        if text_after_delimiter(data) == wanted:
            return p.name
    return None


def preserve(root, head, target, type_, text, repeat=None):
    now = datetime.datetime.now(datetime.timezone.utc)
    date = now.strftime("%Y-%m-%d")
    store = Path(root) / "prompts" / "preserved"
    store.mkdir(parents=True, exist_ok=True)
    header = (f"HEAD: {head}\nTarget subagent: {target}\nType: {type_}\n"
              f"Preserved: {now.strftime('%Y-%m-%dT%H:%M:%SZ')} by "
              f".claude/hooks/dispatch_guard.py\n"
              + (f"{REPEAT_PREFIX}{repeat}\n" if repeat else "")
              + f"{DELIMITER}\n")
    try:
        directory = counter_dir(root)
    except Exception as exc:
        raise CounterUnreachable(f"<git common dir>/{COUNTER_DIR}/{COUNTER}", exc)
    counter = directory / COUNTER
    try:
        directory.mkdir(exist_ok=True)
        lock = open(str(directory / (COUNTER + ".lock")), "a")
    except Exception as exc:
        raise CounterUnreachable(counter, exc)
    with lock:  # closing the file releases the lock
        try:
            fcntl.flock(lock, fcntl.LOCK_EX)
            last = read_counter(counter, date)
        except Exception as exc:
            raise CounterUnreachable(counter, exc)
        taken = [int(m.group(1)) for p in store.glob(f"{date}-*.md")
                 if (m := re.fullmatch(rf"{date}-(\d+)\.md", p.name))]
        seq = max(last, max(taken, default=0)) + 1
        while True:
            path = store / f"{date}-{seq:02d}.md"
            try:
                with open(path, "x") as f:  # never overwrite a preserved prompt
                    f.write(header + text)
                break
            except FileExistsError:
                seq += 1
        try:
            write_counter(counter, date, seq)
        except Exception as exc:
            raise CounterUnreachable(counter, exc)
    return path


def store_check(root):
    checker = Path(root) / "check_prompts.py"
    if not checker.is_file():
        return "check_prompts.py not found at the repository root; the store was not checked."
    try:
        r = g.run_command([sys.executable, str(checker)], cwd=root)
    except g.CommandTimeout as exc:
        return (f"check_prompts.py timed out after {exc.seconds:g} seconds; the store "
                f"was not checked.")
    tail = "\n".join(l for l in r.stdout.splitlines() if l.startswith((" -", "PASS", "FAIL")))
    return f"check_prompts.py exit {r.returncode}:\n{tail}"


def guard(payload):
    if payload.get("tool_name") not in DISPATCH_TOOLS:
        return None
    config = g.CONFIG
    dispatchable = set(config["dispatchable_agents"])
    target_for_type = config["type_targets"]
    required = config["required_sections"]

    tool_input = payload.get("tool_input") or {}
    text = tool_input.get("prompt", "") or ""
    target = tool_input.get("subagent_type") or "(none given)"

    # Checked before anything is written: only the configured subagents may
    # be dispatched.
    if target not in dispatchable:
        return ("deny", f"dispatch_guard: subagent type {target!r} may not be "
                        f"dispatched. Only {', '.join(sorted(dispatchable))} may; "
                        f"built-in agent types are blocked.")

    m = TYPE_LINE.search(text)
    if not m:
        types = sorted(required)
        return ("deny", f"dispatch_guard: the dispatch has no `Type:` line. "
                        f"Add `**Type:** {types[0]}`"
                        + "".join(f", `{t}`" for t in types[1:-1])
                        + (f" or `{types[-1]}`." if len(types) > 1 else "."))
    type_ = m.group(1).strip("*`").lower()
    if type_ not in required:
        return ("deny", f"dispatch_guard: unknown Type {type_!r}. Known types: "
                        f"{', '.join(sorted(required))}.")
    # Type binds to target: unbound, a dispatch could carry a type that runs
    # without approval to an agent whose work needs it.
    if target_for_type[type_] != target:
        return ("deny", f"dispatch_guard: Type {type_!r} may only be dispatched to "
                        f"{target_for_type[type_]!r}, not {target!r}.")
    missing = missing_sections(text, required[type_])
    if missing:
        return ("deny", f"dispatch_guard: this {type_} dispatch is missing "
                        f"{len(missing)} required section(s): {'; '.join(missing)}. "
                        f"Required for {type_}: {'; '.join(required[type_])}.")

    cwd = payload.get("cwd") or "."
    try:
        root = git(cwd, "rev-parse", "--show-toplevel")
        head = git(root, "rev-parse", "HEAD")
        repeat = repeat_of(root, text)
        path = preserve(root, head, target, type_, text, repeat)
    except Exception as exc:
        return ("deny", f"dispatch_guard: could not preserve the dispatch, so it "
                        f"is blocked: {type(exc).__name__}: {exc}")
    rel = path.relative_to(root).as_posix()
    where = f"(HEAD {head[:10]})"
    if repeat:
        where += f", repeat of preserved/{repeat} (identical text)"
    check = store_check(root)
    if type_ in config["approval_exempt_types"]:
        return ("allow", f"dispatch_guard: Type {type_!r} dispatch to {target} "
                         f"preserved at {rel} {where}. Type {type_!r} is "
                         f"in approval_exempt_types, so it runs without approval.\n{check}")
    return ("ask", f"dispatch_guard: Type {type_!r} dispatch to {target} preserved "
                   f"at {rel} {where}. Operator approval required: Type "
                   f"{type_!r} is not in approval_exempt_types.\n{check}")


if __name__ == "__main__":
    sys.exit(g.run(guard))
