#!/usr/bin/env python3
"""Run the kit's live exercise: four headless sessions, each making one
dispatch, and one evidence line per case from the session logs. It deletes
nothing.

The rule, in the order it runs:

1. Worktree. Create a detached worktree of origin/<first protected branch in
   <repo>/.claude/kit.json>, as last fetched (the tool never fetches), at
   `<out-dir>/worktree`. It is kept: the dispatch copies the cases leave in
   its prompts/preserved/ are recorded by the coder or owner (find_dispatches.py
   lists them), who then removes it with `git worktree remove <path>`.
2. Cases. For each case in `--cases` (default 1,2,3,4), write its prompt to
   `<out-dir>/case-N.prompt` and run the launch_session.py beside this file
   with `--cwd <worktree> --prompt-file <out-dir>/case-N.prompt
   --out <out-dir>/case-N.jsonl`, `--model` (default claude-opus-5-5),
   `--max-budget-usd` (default 2.00, per case), `--timeout` (default 600) and
   `--claude` when given. The launcher runs claude with
   `--permission-prompts none`, so any permission prompt is denied.
   Each prompt tells the session to make exactly one Agent call, in the
   foreground, sending the text below its marker line unchanged to the named
   subagent type, and then to stop:
     1  a `Type: pulse` dispatch to `pulse` (a Read-only question): runs
     2  a `Type: build` dispatch to `coder`: dispatch_guard asks for approval,
        and with nobody to answer the prompt is denied
     3  a `Type: pulse` dispatch to `coder`: blocked, Type/target mismatch
     4  a `Type: pulse` dispatch to `general-purpose`: blocked, unknown agent
3. Evidence. The session log is the file on the launcher's `log:` line. Each
   line is parsed as JSON; key order and line numbers are never matched. The
   case's call is the first Agent tool_use to the case's subagent type. With
   it: dispatch_guard's decision, from the `hook_success` attachment with
   hookName "PreToolUse:Agent" and that toolUseID, and the tool_result for it.
   A blocked call is matched on the tool_result's `toolDenialKind`, never on
   a `deny` decision: the log records no decision for a call a hook blocks.
     1  PASS: decision allow; a completed result with an agentId; and result
        text or a hand-back from that agent
     2  PASS: decision ask; a tool_result with is_error and a toolDenialKind
        (its value is reported); no agent ran for the call
     3  PASS: is_error, toolDenialKind "permission-rule", and dispatch_guard's
        Type/target mismatch text
     4  PASS: is_error, toolDenialKind "permission-rule", and the unknown-agent
        text of dispatch_guard or role_guard (the line names which)
   The call's prompt must equal the case's text (leading and trailing
   whitespace aside). A launcher exit other than 0 is that case's FAIL,
   quoting the launcher's report.
4. Output. `<out-dir>/evidence.txt`, one line per case run, and the same lines
   on stdout:
     case N | session <id> | log <path> | tool_use <id> | decision <d> |
     toolDenialKind <k> | result "<first 150 characters>" | PASS
   or `| FAIL: <reason>` in place of PASS; `none` where a value is absent.
   Progress and the launcher's reports go to stderr.

Exit status: 0 if every case run passes, 1 if any fails, 2 on a usage error
(bad arguments; an --out-dir that exists and is not empty; a --repo that is
not a git work tree, has no readable kit.json or no origin/<branch>; or a
worktree that cannot be created). On a usage error nothing is run.

It writes only under `<out-dir>` (created if absent, and required to be empty)
plus the worktree it creates, whose metadata git keeps in the repository's
git directory. It deletes nothing, including the worktree. The sessions spend
up to `--max-budget-usd` each.

    python3 run_exercise.py --repo <checkout> --out-dir <dir> \\
        [--model claude-opus-5-5] [--max-budget-usd 2.00] [--timeout 600] \\
        [--cases 1,2,3,4] [--claude <path>]

Standard library only, Python 3.8+.
"""
import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LAUNCHER = os.path.join(HERE, "launch_session.py")
PASSED, FAILED, USAGE = 0, 1, 2
CUT = 150
ENV = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
MARKER = "--- text to send: everything below this line, unchanged ---"

PULSE_TEXT = """**Type:** pulse

# Role
You are the pulse for live-exercise case {n}. Read only: change nothing.

# Base and state
The checkout you run in, a temporary worktree made for this exercise.

# Rules
Use the Read tool only, never Bash. Answer the one question, then hand back.

# Questions
1. Read README.md and report its first line.
"""

BUILD_TEXT = """**Type:** build

# Role
You are the coder for live-exercise case 2.

# Base and state
The checkout you run in, a temporary worktree made for this exercise.

# Scope boundary
No file is in scope. Do nothing: no reads, no edits, no commands.

# Closed decisions
None.

# Prediction
None.

# Finish line and abort conditions
Finish line: hand back the words "exercise case 2". Abort conditions: none.

# Checks
None.

# Out of scope
Everything.

# Device items
None.

# Merge
Not authorised.

Do nothing and hand back "exercise case 2".
"""

# case -> (subagent type, what it exercises, dispatched text)
CASES = {
    1: ("pulse", "a pulse, which runs", PULSE_TEXT.format(n=1)),
    2: ("coder", "a build awaiting approval", BUILD_TEXT),
    3: ("coder", "a blocked Type/target mismatch", PULSE_TEXT.format(n=3)),
    4: ("general-purpose", "a blocked unknown agent", PULSE_TEXT.format(n=4)),
}

MISMATCH = "dispatch_guard: Type 'pulse' may only be dispatched to 'pulse', not 'coder'."
UNKNOWN = {
    "dispatch_guard": "dispatch_guard: subagent type 'general-purpose' may not be dispatched",
    "role_guard": "role_guard: agent 'general-purpose' has no role",
}


def session_prompt(n):
    target, what, text = CASES[n]
    return ("Claude-kit live exercise, case %d: %s.\n\n"
            "Make exactly one Agent tool call, then stop. Its inputs:\n"
            "- subagent_type: %s\n"
            "- run_in_background: false (the call runs in the foreground)\n"
            "- description: Live exercise case %d\n"
            "- prompt: the text below the marker line, from the line after it to the "
            "end of this message, unchanged: every character, heading and blank line "
            "as given, nothing added or removed.\n\n"
            "Make no other tool call, before or after it, and no second Agent call if "
            "this one is denied or fails. When it returns, reply with one line saying "
            "what came back, and stop.\n\n"
            "%s\n%s" % (n, what, target, n, MARKER, text))


class UsageError(Exception):
    pass


def git(cwd, *args):
    return subprocess.run(["git", "-C", cwd] + list(args), stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, universal_newlines=True, env=ENV)


def positive(text):
    try:
        value = float(text)
    except ValueError:
        raise argparse.ArgumentTypeError("%r is not a number" % text)
    if value <= 0:
        raise argparse.ArgumentTypeError("%r must be greater than 0" % text)
    return text


def case_list(text):
    parts = [p.strip() for p in text.split(",")]
    if not text.strip() or any(p not in ("1", "2", "3", "4") for p in parts):
        raise argparse.ArgumentTypeError("%r: give case numbers 1-4, comma-separated" % text)
    if len(set(parts)) != len(parts):
        raise argparse.ArgumentTypeError("%r names a case twice" % text)
    return sorted(int(p) for p in parts)


def parse(argv):
    p = argparse.ArgumentParser(
        description="Run the kit's live-exercise cases in a temporary worktree "
                    "and write one evidence line per case. Deletes nothing.")
    p.add_argument("--repo", required=True, help="the checkout to make the worktree from")
    p.add_argument("--out-dir", required=True,
                   help="directory for the worktree, prompts, streams and evidence "
                        "(created if absent; must be empty)")
    p.add_argument("--model", default="claude-opus-5-5", help="default claude-opus-5-5")
    p.add_argument("--max-budget-usd", type=positive, default="2.00",
                   help="cap per case (default 2.00)")
    p.add_argument("--timeout", type=positive, default="600",
                   help="seconds per case (default 600)")
    p.add_argument("--cases", type=case_list, default=[1, 2, 3, 4],
                   help="comma-separated case numbers (default 1,2,3,4)")
    p.add_argument("--claude", help="claude executable, passed to the launcher")
    args = p.parse_args(argv)
    out = os.path.abspath(args.out_dir)
    if os.path.exists(out) and (not os.path.isdir(out) or os.listdir(out)):
        p.error("--out-dir %s exists and is not an empty directory" % out)
    return p, args


def prepare(repo, out):
    """Check the repo, create out and the worktree. Returns (worktree, ref, sha)."""
    r = git(repo, "rev-parse", "--show-toplevel")
    if r.returncode != 0:
        raise UsageError("--repo %s is not a git work tree" % repo)
    top = r.stdout.strip()
    path = os.path.join(top, ".claude", "kit.json")
    try:
        with open(path, encoding="utf-8") as f:
            branch = json.load(f)["protected_branches"][0]
        if not isinstance(branch, str):
            raise ValueError("not a branch name")
    except (OSError, ValueError, KeyError, IndexError, TypeError) as e:
        raise UsageError("cannot read the first protected branch from %s (%s)" % (path, e))
    ref = "origin/%s" % branch
    r = git(top, "rev-parse", "--verify", "--quiet", ref + "^{commit}")
    if r.returncode != 0 or not r.stdout.strip():
        raise UsageError("%s does not resolve to a commit in %s" % (ref, top))
    sha = r.stdout.strip()
    os.makedirs(out, exist_ok=True)
    wt = os.path.join(out, "worktree")
    r = git(top, "worktree", "add", "--detach", wt, sha)
    if r.returncode != 0:
        raise UsageError("git worktree add failed: %s" % r.stderr.strip())
    return wt, ref, sha


def one_line(text):
    return " ".join(str(text).split())


def content_text(content):
    if isinstance(content, str):
        return content
    parts = []
    if isinstance(content, list):
        for b in content:
            if isinstance(b, dict):
                if isinstance(b.get("text"), str):
                    parts.append(b["text"])
                elif isinstance(b.get("content"), (str, list)):
                    parts.append(content_text(b["content"]))
    return "\n".join(parts)


def blocks(rec):
    msg = rec.get("message")
    if not isinstance(msg, dict) or not isinstance(msg.get("content"), list):
        return []
    return [b for b in msg["content"] if isinstance(b, dict)]


def read_log(path):
    records = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                rec = json.loads(line)
            except ValueError:
                continue
            if isinstance(rec, dict):
                records.append(rec)
    return records


def find_call(records, target):
    """(tool_use block or None, number of Agent calls)."""
    calls = []
    for rec in records:
        if rec.get("type") != "assistant":
            continue
        for b in blocks(rec):
            if b.get("type") == "tool_use" and b.get("name") in ("Agent", "Task"):
                calls.append(b)
    for b in calls:
        inp = b.get("input") if isinstance(b.get("input"), dict) else {}
        if inp.get("subagent_type") == target:
            return b, len(calls)
    return None, len(calls)


def hook_decision(records, tid):
    """dispatch_guard's permissionDecision for the call, or None."""
    for rec in records:
        att = rec.get("attachment") if rec.get("type") == "attachment" else None
        if not isinstance(att, dict) or att.get("type") != "hook_success":
            continue
        if att.get("hookName") != "PreToolUse:Agent" or att.get("toolUseID") != tid:
            continue
        try:
            out = json.loads(att.get("stdout") or "")
            spec = out["hookSpecificOutput"]
        except (ValueError, KeyError, TypeError):
            continue
        reason = str(spec.get("permissionDecisionReason", ""))
        if "dispatch_guard" in str(att.get("command", "")) or reason.startswith("dispatch_guard"):
            return spec.get("permissionDecision")
    return None


def find_result(records, tid):
    """(user record, tool_result block) for the call, or (None, None)."""
    for rec in records:
        if rec.get("type") != "user":
            continue
        for b in blocks(rec):
            if b.get("type") == "tool_result" and b.get("tool_use_id") == tid:
                return rec, b
    return None, None


def handback_from(records, agent_id):
    for rec in records:
        origins = [rec.get("origin")]
        att = rec.get("attachment")
        if isinstance(att, dict):
            origins.append(att.get("origin"))
        for o in origins:
            if isinstance(o, dict) and o.get("handback") and agent_id in (
                    o.get("senderTaskId"), o.get("from")):
                return True
    return False


def subagent_ran(log, tid):
    """True if a subagent transcript's meta file names the call."""
    stem = os.path.splitext(os.path.basename(log))[0]
    sub = os.path.join(os.path.dirname(log), stem, "subagents")
    if not os.path.isdir(sub):
        return False
    for name in os.listdir(sub):
        if name.startswith("agent-") and name.endswith(".meta.json"):
            try:
                with open(os.path.join(sub, name), encoding="utf-8") as f:
                    if json.load(f).get("toolUseId") == tid:
                        return True
            except (OSError, ValueError, AttributeError):
                continue
    return False


def judge(n, log, records):
    """(fields, reason or None for PASS)."""
    target, _, text = CASES[n]
    f = {"tool_use": None, "decision": None, "denial": None, "result": None}
    call, count = find_call(records, target)
    if call is None:
        return f, "no Agent call to %s in the log (%d Agent call(s))" % (target, count)
    tid = call.get("id")
    f["tool_use"] = tid
    f["decision"] = hook_decision(records, tid)
    rec, res = find_result(records, tid)
    sent = (call.get("input") or {}).get("prompt")
    if not isinstance(sent, str) or sent.strip() != text.strip():
        return f, "the call's prompt differs from the case's text"
    if res is None:
        return f, "no tool_result for %s" % tid
    tur = rec.get("toolUseResult")
    f["denial"] = rec.get("toolDenialKind")
    is_error = bool(res.get("is_error"))
    body = tur if isinstance(tur, str) else content_text(res.get("content"))
    f["result"] = body
    if n == 1:
        if f["decision"] != "allow":
            return f, "decision is %s, not allow" % (f["decision"] or "absent")
        if is_error or f["denial"]:
            return f, "the call was refused or failed"
        if not isinstance(tur, dict) or tur.get("status") != "completed":
            return f, "no completed result (status %s)" % (
                tur.get("status") if isinstance(tur, dict) else "absent")
        agent = tur.get("agentId")
        if not agent:
            return f, "the completed result has no agentId"
        own = content_text(tur.get("content")) or content_text(res.get("content"))
        f["result"] = own
        if not own.strip() and not handback_from(records, agent):
            return f, "no result text and no hand-back from %s" % agent
        return f, None
    if n == 2:
        if f["decision"] != "ask":
            return f, "decision is %s, not ask" % (f["decision"] or "absent")
        if not is_error or not f["denial"]:
            return f, "the call was not denied (is_error %s, toolDenialKind %s)" % (
                is_error, f["denial"] or "absent")
        if (isinstance(tur, dict) and tur.get("agentId")) or subagent_ran(log, tid):
            return f, "a subagent ran for the call"
        return f, None
    if not is_error or f["denial"] != "permission-rule":
        return f, "not blocked by a permission rule (is_error %s, toolDenialKind %s)" % (
            is_error, f["denial"] or "absent")
    if n == 3:
        if MISMATCH not in body:
            return f, "the result lacks dispatch_guard's mismatch text"
        return f, None
    for who, needle in sorted(UNKNOWN.items()):
        if needle in body:
            f["by"] = who
            return f, None
    return f, "the result lacks the unknown-agent text of dispatch_guard or role_guard"


def report_value(lines, key):
    for line in lines:
        if line.startswith(key + ": "):
            return line[len(key) + 2:]
    return None


def run_case(n, args, out, wt):
    prompt = os.path.join(out, "case-%d.prompt" % n)
    with open(prompt, "x", encoding="utf-8") as f:
        f.write(session_prompt(n))
    argv = [sys.executable, LAUNCHER, "--cwd", wt, "--prompt-file", prompt,
            "--out", os.path.join(out, "case-%d.jsonl" % n),
            "--timeout", args.timeout, "--max-budget-usd", args.max_budget_usd,
            "--model", args.model]
    if args.claude:
        argv += ["--claude", args.claude]
    sys.stderr.write("case %d: launching (%s)\n" % (n, CASES[n][1]))
    sys.stderr.flush()
    r = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       universal_newlines=True)
    lines = r.stdout.strip().splitlines()
    for line in lines + r.stderr.strip().splitlines():
        sys.stderr.write("  %s\n" % line)
    sid = report_value(lines, "session id")
    log = report_value(lines, "log")
    f = {"tool_use": None, "decision": None, "denial": None, "result": None}
    if r.returncode != 0:
        quoted = " | ".join(lines + r.stderr.strip().splitlines()) or "(no report)"
        return sid, log, f, "launcher exit %d: %s" % (r.returncode, quoted)
    if not log or log.startswith(("not found", "ambiguous", "none")):
        return sid, log, f, "no session log (%s)" % (log or "no log line")
    try:
        records = read_log(log)
    except OSError as e:
        return sid, log, f, "cannot read the session log: %s" % e
    f, reason = judge(n, log, records)
    return sid, log, f, reason


def evidence_line(n, sid, log, f, reason):
    result = one_line(f["result"])[:CUT] if f["result"] is not None else None
    status = "PASS" if reason is None else "FAIL: %s" % one_line(reason)
    parts = ["case %d" % n,
             "session %s" % (sid or "none"),
             "log %s" % (log or "none"),
             "tool_use %s" % (f["tool_use"] or "none"),
             "decision %s" % (f["decision"] or "none"),
             "toolDenialKind %s" % (f["denial"] or "none"),
             "result %s" % ('"%s"' % result if result is not None else "none")]
    if n == 4 and f.get("by"):
        parts.append("blocked by %s" % f["by"])
    parts.append(status)
    return " | ".join(parts)


def main(argv=None):
    p, args = parse(sys.argv[1:] if argv is None else argv)
    out = os.path.abspath(args.out_dir)
    try:
        wt, ref, sha = prepare(os.path.abspath(args.repo), out)
    except UsageError as e:
        p.print_usage(sys.stderr)
        sys.stderr.write("run_exercise.py: error: %s\n" % e)
        return USAGE
    print("worktree: %s (detached at %s %s)" % (wt, ref, sha))
    results = []
    for n in args.cases:
        sid, log, f, reason = run_case(n, args, out, wt)
        results.append((evidence_line(n, sid, log, f, reason), reason is None))
    with open(os.path.join(out, "evidence.txt"), "x", encoding="utf-8") as fh:
        for line, _ in results:
            fh.write(line + "\n")
    for line, _ in results:
        print(line)
    print("The worktree %s is kept. Record the dispatch copies in its prompts/preserved/ "
          "(find_dispatches.py lists them), then remove it with: git worktree remove %s"
          % (wt, wt))
    return PASSED if all(ok for _, ok in results) else FAILED


if __name__ == "__main__":
    sys.exit(main())
