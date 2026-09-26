#!/usr/bin/env python3
"""List the Agent calls in a session log and what came back from each.

Rule: settle every agent a lost session sent before re-sending anything.
Cite a call by its tool_use id and timestamp, never by line number: resuming
a session can rewrite its log, which shifts line numbers, sorts keys and
drops queue-operation records, while ids and timestamps survive.

    python3 session_agents.py <session.jsonl>

A session log is ~/.claude/projects/<project>/<sessionId>.jsonl. Subagent
transcripts are read from <log dir>/<sessionId>/subagents/, as
agent-<agentId>.jsonl with agent-<agentId>.meta.json beside each.

Every line is parsed as JSON; a line that doesn't parse is reported as
"line N: not JSON (truncated or partial)" and skipped. Key order doesn't
matter. For each Agent tool_use, in log order, one block gives:

  timestamp and tool_use id; subagent_type; description; foreground or
  background; the line number (a hint only); the agentId (from the result,
  the meta file or a completion notice); the outcome; hand-backs; and the
  SendMessages sent to that agentId.

Outcome is one of:

  completed                        a foreground result, or a completed notice
  failed: <summary>                a notice whose status isn't completed, or
                                   an error result that isn't a denial
  denied: <how>                    an error result with toolDenialKind
  launched, no completion notice   running, lost or cut off
  no result                        the log ends before any result

A denial's <how> is decided by the first of these rules that matches. The
text is the result's toolUseResult when that is a string, else its content.
Rules 1 and 3 read it with an optional leading "Error: " stripped; the
fallback prints its first line as it is, prefix included:

  by user              the text is "Denied by user": an explicit decline,
                       which wins even when a hook asked
  approval not given   a PreToolUse:Agent hook_success attachment for the
                       call's toolUseID printed permissionDecision "ask",
                       and nobody answered: under --permission-prompts none
                       this logs the same toolDenialKind ("permission-rule")
                       as a hook block
  blocked by <guard>   the text has "PreToolUse:<Tool> hook error: <guard>:"
                       or starts "<guard>:", where <guard> is a word
                       (letters, digits, underscores) ending "_guard"
  <first line>         anything else: the text's first line

An Agent decline's exact text is not yet observed in any log: the only
"Denied by user" seen was on a Bash call. An error without toolDenialKind is
"failed", whatever a hook decided. The summary counts every denial under
"denied".

Where an agent has several notices (it was resumed), the last one counts.
Notices are read from task-notification user records, queued_command
attachments and queue-operation copies, and counted once. Hand-backs are
peer user records or queued_command attachments with origin.handback set,
counted once; queue-operation copies are not counted.

For an agent that failed, has no completion notice or has no result, and
has a transcript, it also prints the transcript's last timestamp, its last
assistant text cut to 200 characters, its last tool_use's name and time,
and whether it ends in an API error. API-error records are left out of the
last text and last tool_use.

It never prints a prompt or a whole message body: only descriptions,
summaries, first lines and texts cut to 200 characters. It writes nothing.
The last line counts the calls per outcome.

Exit status 0 when the log was read, even if some lines were not JSON;
2 on a usage error or a log that can't be opened.

Standard library only, Python 3.8+.
"""
import json
import os
import re
import sys

CUT = 200
NOTICE_RE = re.compile(r"<task-notification>(.*?)(?:</task-notification>|$)", re.S)
ERROR_PREFIX = "Error: "
GUARD_HOOK_RE = re.compile(r"PreToolUse:\w+ hook error: (\w+_guard):")
GUARD_START_RE = re.compile(r"(\w+_guard):")
OUTCOME_ORDER = ["completed", "failed", "denied",
                 "launched, no completion notice", "no result"]


def cut(text, n=CUT):
    text = " ".join(str(text).split())
    return text if len(text) <= n else text[:n] + "..."


def first_line(text):
    for line in str(text).splitlines():
        if line.strip():
            return cut(line.strip())
    return ""


def tag(text, name):
    m = re.search(r"<%s>(.*?)</%s>" % (name, name), text, re.S)
    return m.group(1).strip() if m else ""


def content_text(content):
    """All text in a message content (a string or a list of blocks)."""
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
    if not isinstance(msg, dict):
        return []
    c = msg.get("content")
    return [b for b in c if isinstance(b, dict)] if isinstance(c, list) else []


def read_jsonl(path, bad):
    """(line number, record) for each line that parses; bad gets the others."""
    out = []
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for n, line in enumerate(fh, 1):
            if not line.strip():
                continue
            try:
                rec = json.loads(line)
            except ValueError:
                bad.append(n)
                continue
            if isinstance(rec, dict):
                out.append((n, rec))
            else:
                bad.append(n)
    return out


def notices_in(text):
    found = []
    for body in NOTICE_RE.findall(text):
        tid = tag(body, "task-id")
        if tid:
            found.append((tid, tag(body, "tool-use-id"), tag(body, "status"),
                          tag(body, "summary")))
    return found


def scan(records):
    calls, order = {}, []
    results, sends, send_errors = {}, [], set()
    notices, seen_notices = [], set()
    handbacks, seen_handbacks = [], set()
    asked = set()

    def add_notices(text, when):
        for key in notices_in(text):
            if key not in seen_notices:
                seen_notices.add(key)
                notices.append((when, key))

    for n, rec in records:
        rtype = rec.get("type")
        when = rec.get("timestamp") or ""
        origin = rec.get("origin") if isinstance(rec.get("origin"), dict) else {}
        if rtype == "assistant":
            for b in blocks(rec):
                if b.get("type") != "tool_use":
                    continue
                inp = b.get("input") if isinstance(b.get("input"), dict) else {}
                if b.get("name") == "Agent" and b.get("id") and b["id"] not in calls:
                    calls[b["id"]] = {"id": b["id"], "when": when, "line": n,
                                      "type": inp.get("subagent_type") or "",
                                      "desc": inp.get("description") or "",
                                      "bg": bool(inp.get("run_in_background"))}
                    order.append(b["id"])
                elif b.get("name") == "SendMessage" and inp.get("to"):
                    sends.append((b.get("id"), when, str(inp["to"])))
        elif rtype == "user":
            for b in blocks(rec):
                if b.get("type") == "tool_result" and b.get("tool_use_id"):
                    tid = b["tool_use_id"]
                    if b.get("is_error"):
                        send_errors.add(tid)
                    if tid not in results:
                        results[tid] = {"is_error": bool(b.get("is_error")),
                                        "denial": rec.get("toolDenialKind"),
                                        "tur": rec.get("toolUseResult"),
                                        "text": content_text(b.get("content"))}
            if origin.get("kind") == "task-notification":
                add_notices(content_text((rec.get("message") or {}).get("content")), when)
            if origin.get("kind") == "peer" and origin.get("handback"):
                sender = origin.get("senderTaskId") or origin.get("from")
                key = (sender, origin.get("body") or when)
                if key not in seen_handbacks:
                    seen_handbacks.add(key)
                    handbacks.append((when, sender))
        elif rtype == "attachment":
            att = rec.get("attachment") if isinstance(rec.get("attachment"), dict) else {}
            if (att.get("type") == "hook_success" and att.get("hookName") == "PreToolUse:Agent"
                    and att.get("toolUseID") and hook_decision(att.get("stdout")) == "ask"):
                asked.add(att["toolUseID"])
            if att.get("type") == "queued_command":
                ao = att.get("origin") if isinstance(att.get("origin"), dict) else {}
                if ao.get("kind") == "peer" and ao.get("handback"):
                    sender = ao.get("senderTaskId") or ao.get("from")
                    key = (sender, ao.get("body") or when)
                    if key not in seen_handbacks:
                        seen_handbacks.add(key)
                        handbacks.append((when, sender))
                elif isinstance(att.get("prompt"), str):
                    add_notices(att["prompt"], when)
        elif rtype == "queue-operation":
            if isinstance(rec.get("content"), str):
                add_notices(rec["content"], when)
    return calls, order, results, sends, send_errors, notices, handbacks, asked


def hook_decision(stdout):
    """The permissionDecision a hook printed on stdout, or None."""
    if not isinstance(stdout, str):
        return None
    try:
        data = json.loads(stdout)
    except ValueError:
        return None
    out = data.get("hookSpecificOutput") if isinstance(data, dict) else None
    return out.get("permissionDecision") if isinstance(out, dict) else None


def denial(text, asked):
    """How a denied call was denied (see the module docstring)."""
    bare = str(text).strip()
    if bare.startswith(ERROR_PREFIX):
        bare = bare[len(ERROR_PREFIX):].strip()
    if bare == "Denied by user":
        return "by user"
    if asked:
        return "approval not given"
    m = GUARD_HOOK_RE.search(bare) or GUARD_START_RE.match(bare)
    if m:
        return "blocked by %s" % m.group(1)
    return first_line(text)


def load_meta(subdir):
    """toolUseId -> agentId from the subagents/ meta files."""
    by_tool = {}
    if not os.path.isdir(subdir):
        return by_tool
    for name in sorted(os.listdir(subdir)):
        if not (name.startswith("agent-") and name.endswith(".meta.json")):
            continue
        try:
            with open(os.path.join(subdir, name), encoding="utf-8") as fh:
                meta = json.load(fh)
        except (OSError, ValueError):
            continue
        if isinstance(meta, dict) and meta.get("toolUseId"):
            by_tool[meta["toolUseId"]] = name[len("agent-"):-len(".meta.json")]
    return by_tool


def last_activity(path):
    bad = []
    try:
        records = read_jsonl(path, bad)
    except OSError as exc:
        return ["cannot read %s: %s" % (os.path.basename(path), exc.strerror)]
    last_ts, last_text, last_tool, api_error = "", None, None, None
    for _, rec in records:
        if rec.get("timestamp"):
            last_ts = rec["timestamp"]
        if rec.get("type") != "assistant":
            continue
        if rec.get("isApiErrorMessage"):
            continue
        for b in blocks(rec):
            if b.get("type") == "text" and str(b.get("text", "")).strip():
                last_text = b["text"]
            elif b.get("type") == "tool_use":
                last_tool = (b.get("name") or "?", rec.get("timestamp") or "?")
    if records:
        tail = records[-1][1]
        if tail.get("isApiErrorMessage"):
            api_error = tail.get("error") or "API error"
    lines = ["last activity (%s):" % os.path.basename(path),
             "  last timestamp: %s" % (last_ts or "none")]
    lines.append("  last assistant text: %s" % (cut(last_text) if last_text else "none"))
    lines.append("  last tool_use: %s" % ("%s at %s" % last_tool if last_tool else "none"))
    lines.append("  ends in API error: %s" % ("yes (%s)" % api_error if api_error else "no"))
    if bad:
        lines.append("  transcript lines not JSON: %s" % ", ".join(str(n) for n in bad))
    return lines


def outcome_of(call, res, agent_notices, asked=False):
    if res is not None:
        if res["is_error"]:
            text = res["tur"] if isinstance(res["tur"], str) else res["text"]
            if res["denial"]:
                return "denied: %s" % denial(text, asked)
            return "failed: %s" % first_line(text)
        tur = res["tur"] if isinstance(res["tur"], dict) else {}
        if tur.get("status") == "completed":
            return "completed"
    if agent_notices:
        _, (_, _, status, summary) = agent_notices[-1]
        return "completed" if status == "completed" else "failed: %s" % cut(summary)
    if res is None:
        return "no result"
    return "launched, no completion notice"


def main(argv):
    if len(argv) != 2 or argv[1] in ("-h", "--help"):
        sys.stderr.write("usage: python3 session_agents.py <session.jsonl>\n")
        return 2
    log = argv[1]
    bad = []
    try:
        records = read_jsonl(log, bad)
    except OSError as exc:
        sys.stderr.write("session_agents.py: cannot open %s: %s\n" % (log, exc.strerror))
        return 2
    stem = os.path.splitext(os.path.basename(log))[0]
    subdir = os.path.join(os.path.dirname(os.path.abspath(log)), stem, "subagents")
    calls, order, results, sends, send_errors, notices, handbacks, asked = scan(records)
    meta = load_meta(subdir)

    print("session log: %s" % log)
    print("subagent transcripts: %s%s" % (subdir, "" if os.path.isdir(subdir) else " (none)"))
    for n in bad:
        print("line %d: not JSON (truncated or partial)" % n)

    counts = dict((k, 0) for k in OUTCOME_ORDER)
    for i, tid in enumerate(order, 1):
        call = calls[tid]
        res = results.get(tid)
        tur = res["tur"] if res and isinstance(res["tur"], dict) else {}
        agent_id = tur.get("agentId") or meta.get(tid)
        if not agent_id:
            for _, (task_id, notice_tid, _, _) in notices:
                if notice_tid == tid:
                    agent_id = task_id
                    break
        mine = [x for x in notices if agent_id and x[1][0] == agent_id]
        outcome = outcome_of(call, res, mine, tid in asked)
        for k in OUTCOME_ORDER:
            if outcome == k or outcome.startswith(k + ":"):
                counts[k] += 1
        hb = [w for w, s in handbacks if agent_id and s == agent_id]
        sm = ["%s%s" % (w, " (error)" if sid in send_errors else "")
              for sid, w, to in sends if agent_id and to == agent_id]
        print("")
        print("Agent call %d: %s  %s" % (i, call["when"], tid))
        print("  type: %s" % call["type"])
        print("  description: %s" % cut(call["desc"]))
        print("  mode: %s" % ("background" if call["bg"] else "foreground"))
        print("  line: %d (hint only; a resume rewrites line numbers)" % call["line"])
        print("  agentId: %s" % (agent_id or "unknown"))
        print("  outcome: %s" % outcome)
        print("  hand-backs: %d%s" % (len(hb), " (%s)" % ", ".join(hb) if hb else ""))
        print("  SendMessages: %d%s" % (len(sm), " (%s)" % ", ".join(sm) if sm else ""))
        unfinished = (outcome.startswith("failed:")
                      or outcome in ("launched, no completion notice", "no result"))
        if unfinished and agent_id:
            path = os.path.join(subdir, "agent-%s.jsonl" % agent_id)
            if os.path.isfile(path):
                for line in last_activity(path):
                    print("  " + line)
            else:
                print("  last activity: no transcript")

    print("")
    print("summary: %d Agent call(s); %s" % (
        len(order), ", ".join("%s %d" % (k, counts[k]) for k in OUTCOME_ORDER)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
