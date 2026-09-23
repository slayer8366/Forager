"""PreToolUse on the dispatch tool (Agent; Task was its name up to Claude
Code 2.1.63 and still appears in this version's init tool list).

1. Reads the prompt's `Type:` line. Missing or unknown blocks.
2. Checks the sections that type requires; missing ones block, by name.
3. On pass, writes the prompt verbatim to prompts/preserved/<date>-<seq>.md
   (operator ruling, 2026-09-22), headed with the HEAD hash, the target
   subagent and the type. Neither agent writes this file. If it cannot be
   written, the dispatch is blocked: an unpreserved dispatch is the failure
   this hook exists to prevent.
4. Runs check_prompts.py and reports its result. It does not block on it:
   the prompt just written is unclaimed until the coder's next sweep writes
   its dispatch-note, and the planner cannot write that note, so blocking
   would stop the planner dispatching the coder that fixes it.
5. Returns "ask" for build and device (decision B: operator approval) and
   "allow" for pulse.

The section check is structural. A dispatch can carry every heading and
still be wrong.
"""
import datetime
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402

DISPATCH_TOOLS = {"Agent", "Task"}
DISPATCHABLE = {"coder", "pulse"}
TARGET_FOR_TYPE = {"pulse": "pulse", "build": "coder", "device": "coder"}
REQUIRED = {
    "build": ["Role", "Base and state", "Scope boundary", "Closed decisions",
              "Prediction", "Finish line and abort conditions", "Checks",
              "Out of scope", "Device items"],
    "pulse": ["Role", "Base and state", "Rules", "Questions"],
}
REQUIRED["device"] = REQUIRED["build"]
TYPE_LINE = re.compile(r"(?m)^\s*(?:\*\*Type:\*\*|Type:)\s*(\S+)")
HEADING = re.compile(r"(?m)^#{1,6}\s+(.+?)\s*#*\s*$")
DELIMITER = "--- verbatim prompt follows ---"


def missing_sections(text, required):
    headings = [h.strip().lower() for h in HEADING.findall(text)]
    return [s for s in required
            if not any(h == s.lower() or h.startswith(s.lower() + " ")
                       or h.startswith(s.lower() + "(") for h in headings)]


def git(cwd, *args):
    r = subprocess.run(["git", "-C", cwd, *args], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed in {cwd}: {r.stderr.strip()}")
    return r.stdout.strip()


def preserve(root, head, target, type_, text):
    now = datetime.datetime.now(datetime.timezone.utc)
    date = now.strftime("%Y-%m-%d")
    store = Path(root) / "prompts" / "preserved"
    store.mkdir(parents=True, exist_ok=True)
    taken = [int(m.group(1)) for p in store.glob(f"{date}-*.md")
             if (m := re.fullmatch(rf"{date}-(\d+)\.md", p.name))]
    seq = max(taken, default=0) + 1
    header = (f"HEAD: {head}\nTarget subagent: {target}\nType: {type_}\n"
              f"Preserved: {now.strftime('%Y-%m-%dT%H:%M:%SZ')} by "
              f".claude/hooks/dispatch_guard.py\n{DELIMITER}\n")
    while True:
        path = store / f"{date}-{seq:02d}.md"
        try:
            with open(path, "x") as f:  # never overwrite a preserved prompt
                f.write(header + text)
            return path
        except FileExistsError:
            seq += 1


def store_check(root):
    checker = Path(root) / "check_prompts.py"
    if not checker.is_file():
        return "check_prompts.py not found at the repository root; the store was not checked."
    r = subprocess.run([sys.executable, str(checker)], capture_output=True,
                       text=True, cwd=root)
    tail = "\n".join(l for l in r.stdout.splitlines() if l.startswith((" -", "PASS", "FAIL")))
    return f"check_prompts.py exit {r.returncode}:\n{tail}"


def guard(payload):
    if payload.get("tool_name") not in DISPATCH_TOOLS:
        return None
    tool_input = payload.get("tool_input") or {}
    text = tool_input.get("prompt", "") or ""
    target = tool_input.get("subagent_type") or "(none given)"

    # Checked before anything is written. A live test on 2026-09-22 showed the
    # planner dispatching the built-in general-purpose agent, which has Edit,
    # Write, Bash and MCP tools, so decision A did not hold (operator ruling:
    # only the named subagents may be dispatched).
    if target not in DISPATCHABLE:
        return ("deny", f"dispatch_guard: subagent type {target!r} may not be "
                        f"dispatched. Only {', '.join(sorted(DISPATCHABLE))} may; "
                        f"built-in agent types are blocked.")

    m = TYPE_LINE.search(text)
    if not m:
        return ("deny", "dispatch_guard: the dispatch has no `Type:` line. "
                        "Add `**Type:** build`, `device` or `pulse`.")
    type_ = m.group(1).strip("*`").lower()
    if type_ not in REQUIRED:
        return ("deny", f"dispatch_guard: unknown Type {type_!r}. Known types: "
                        f"{', '.join(sorted(REQUIRED))}.")
    # Operator ruling, 2026-09-22: Type binds to target. Unbound, a dispatch
    # typed pulse could go to coder and run without approval (decision B).
    if TARGET_FOR_TYPE[type_] != target:
        return ("deny", f"dispatch_guard: Type {type_!r} may only be dispatched to "
                        f"{TARGET_FOR_TYPE[type_]!r}, not {target!r}.")
    missing = missing_sections(text, REQUIRED[type_])
    if missing:
        return ("deny", f"dispatch_guard: this {type_} dispatch is missing "
                        f"{len(missing)} required section(s): {'; '.join(missing)}. "
                        f"Required for {type_}: {'; '.join(REQUIRED[type_])}.")

    cwd = payload.get("cwd") or "."
    try:
        root = git(cwd, "rev-parse", "--show-toplevel")
        head = git(root, "rev-parse", "HEAD")
        path = preserve(root, head, target, type_, text)
    except Exception as exc:
        return ("deny", f"dispatch_guard: could not preserve the dispatch, so it "
                        f"is blocked: {type(exc).__name__}: {exc}")
    rel = path.relative_to(root).as_posix()
    check = store_check(root)
    if type_ in ("build", "device"):
        return ("ask", f"dispatch_guard: {type_} dispatch to {target} preserved at "
                       f"{rel} (HEAD {head[:10]}). Operator approval required "
                       f"(decision B).\n{check}")
    return ("allow", f"dispatch_guard: pulse dispatch to {target} preserved at "
                     f"{rel} (HEAD {head[:10]}). Pulses run without approval "
                     f"(decision B).\n{check}")


if __name__ == "__main__":
    sys.exit(g.run(guard))
