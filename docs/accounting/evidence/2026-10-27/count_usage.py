#!/usr/bin/env python3
"""Count logged token usage for one UTC day across the laptop's Claude Code session logs.

Prints aggregate figures only. Never prints log text, prompts, paths of user
content, or tool inputs. Run as: python3 -I count_usage.py
"""
import json
import os
import re
import statistics
import sys
from collections import Counter, defaultdict

ROOT = "/home/zynergy-labs/.claude/projects"
DAY = "2026-10-27"
TOKEN_FIELDS = ("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens", "output_tokens")

NAMES = ["Rowan", "Hazel", "Sorrel", "Juniper", "Elder", "Alder", "Ash", "Aspen", "Willow"]
ROLE_TEAM = {
    "Rowan": "app", "Hazel": "app", "Sorrel": "app", "Juniper": "app", "Elder": "app",
    "app coder (step 7, unnamed)": "app",
    "Alder": "forecast", "Ash": "forecast",
    "Aspen": "pi", "Willow": "pi",
    "accountant": "app",
}
YOU_ARE = re.compile(r"\bYou are (?:the )?(?:Forager project's )?([A-Z][a-z]+)")
SLEEP = re.compile(r"\bsleep\s+(\d+)")


def folder_team(name):
    if name == "-home-zynergy-labs-Zynergy-Forager" or name.startswith("-home-zynergy-labs-Zynergy-Forager--claude-worktrees-"):
        return "app"
    if name == "-home-zynergy-labs-Zynergy-forager-forecast":
        return "forecast"
    return "other"


def text_of(message):
    """Plain text of a user message, or '' for tool-result-only messages."""
    if not isinstance(message, dict):
        return ""
    c = message.get("content")
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        parts = [b.get("text", "") for b in c if isinstance(b, dict) and b.get("type") == "text"]
        return "\n".join(parts)
    return ""


def role_from_text(text):
    head = text[:600]
    if "Forager project's accountant" in head or "Thistle Plumbline" in head:
        return "accountant", "rule: accountant title or name"
    m = YOU_ARE.search(head)
    if m and m.group(1) in NAMES:
        return m.group(1), "rule: 'You are <name>'"
    for n in NAMES:
        if re.search(r"\b%s\b" % n, text[:120]):
            return n, "rule: name in first 120 chars"
    if re.search(r"step 7", head, re.I):
        return "app coder (step 7, unnamed)", "rule: 'step 7'"
    return None, "none"


def fmt(n):
    return f"{int(n):,}"


def main():
    files = []
    for folder in sorted(os.listdir(ROOT)):
        fpath = os.path.join(ROOT, folder)
        if not os.path.isdir(fpath):
            continue
        for dirpath, _dirs, names in os.walk(fpath):
            for nm in names:
                if nm.endswith(".jsonl"):
                    files.append((folder, os.path.join(dirpath, nm)))

    file_info = {}          # file key -> dict
    session_role = {}       # sessionId -> (role, method) from main files
    seen_msg = {}           # message id -> turn dict (deduped)
    dup_within = 0
    dup_across = 0
    seen_tool = set()
    unparsed = Counter()
    lines_total = 0
    sleep_msgs = set()

    for folder, path in files:
        key = os.path.relpath(path, ROOT)
        is_sub = "/subagents/" in path
        info = {
            "folder": folder, "team_folder": folder_team(folder), "sub": is_sub,
            "session_id": None, "first_user_text": None, "first_user_ts": None,
            "window_turns": 0, "max_ctx": 0, "compactions": 0, "reads": Counter(),
            "sleeps": [], "bad": 0, "branch": None,
        }
        file_info[key] = info
        with open(path, encoding="utf-8", errors="replace") as fh:
            for raw in fh:
                lines_total += 1
                try:
                    o = json.loads(raw)
                except Exception:
                    info["bad"] += 1
                    continue
                if not isinstance(o, dict):
                    continue
                sid = o.get("sessionId")
                if sid and info["session_id"] is None:
                    info["session_id"] = sid
                ts = o.get("timestamp") or ""
                typ = o.get("type")
                if typ == "user" and info["first_user_text"] is None:
                    t = text_of(o.get("message"))
                    if t.strip():
                        info["first_user_text"] = t
                        info["first_user_ts"] = ts
                        info["branch"] = o.get("gitBranch") or info["branch"]
                if not isinstance(ts, str) or not ts.startswith(DAY):
                    if o.get("isCompactSummary") or o.get("subtype") == "compact_boundary":
                        pass  # compactions counted only inside the window
                    continue
                if o.get("isCompactSummary") or o.get("subtype") == "compact_boundary":
                    info["compactions"] += 1
                msg = o.get("message")
                if typ == "assistant" and isinstance(msg, dict):
                    content = msg.get("content")
                    if isinstance(content, list):
                        for b in content:
                            if isinstance(b, dict) and b.get("type") == "tool_use":
                                tid = b.get("id")
                                if tid and tid in seen_tool:
                                    continue
                                if tid:
                                    seen_tool.add(tid)
                                if b.get("name") == "Read":
                                    fp = (b.get("input") or {}).get("file_path")
                                    if fp:
                                        info["reads"][fp] += 1
                                if b.get("name") == "Bash":
                                    cmd = (b.get("input") or {}).get("command") or ""
                                    for s in SLEEP.findall(cmd):
                                        info["sleeps"].append(int(s))
                                        sleep_msgs.add(msg.get("id") or ("uuid:" + str(o.get("uuid"))))
                    usage = msg.get("usage")
                    if isinstance(usage, dict):
                        mid = msg.get("id") or ("uuid:" + str(o.get("uuid")))
                        rec = {
                            "file": key, "ts": ts, "model": msg.get("model") or "?",
                            "branch": o.get("gitBranch") or info["branch"] or "?",
                            "sid": sid, "sub": is_sub,
                        }
                        for f in TOKEN_FIELDS:
                            rec[f] = int(usage.get(f) or 0)
                        if mid in seen_msg:
                            if key == seen_msg[mid]["file"]:
                                dup_within += 1
                            else:
                                dup_across += 1
                            if rec["output_tokens"] > seen_msg[mid]["output_tokens"]:
                                seen_msg[mid].update({f: rec[f] for f in TOKEN_FIELDS})
                        else:
                            seen_msg[mid] = rec
                        ctx = rec["input_tokens"] + rec["cache_creation_input_tokens"] + rec["cache_read_input_tokens"]
                        info["max_ctx"] = max(info["max_ctx"], ctx)
                        info["window_turns"] += 1
        if info["bad"]:
            unparsed[key] = info["bad"]

    # Role per session: main files by own first message; subagents inherit their parent's role if their own is unknown.
    for key, info in file_info.items():
        if not info["sub"]:
            r, m = role_from_text(info["first_user_text"] or "")
            info["role"], info["role_method"] = (r or "unassigned"), m
            if info["session_id"] and r:
                session_role[info["session_id"]] = r
    for key, info in file_info.items():
        if info["sub"]:
            r, m = role_from_text(info["first_user_text"] or "")
            if r:
                info["role"], info["role_method"] = r, m
            elif info["session_id"] in session_role:
                info["role"], info["role_method"] = session_role[info["session_id"]], "inherited from parent session"
            else:
                info["role"], info["role_method"] = "unassigned", "none"

    # Turn aggregation (deduplicated)
    turns = list(seen_msg.values())
    for t in turns:
        info = file_info[t["file"]]
        t["role"] = info["role"]
        t["team"] = ROLE_TEAM.get(info["role"]) or info["team_folder"]
        t["total"] = sum(t[f] for f in TOKEN_FIELDS)

    def bucket():
        return {"turns": 0, "files": set(), "in": 0, "cc": 0, "cr": 0, "out": 0, "total": 0}

    def add(b, t, file_key):
        b["turns"] += 1
        b["files"].add(file_key)
        b["in"] += t["input_tokens"]
        b["cc"] += t["cache_creation_input_tokens"]
        b["cr"] += t["cache_read_input_tokens"]
        b["out"] += t["output_tokens"]
        b["total"] += t["total"]

    overall = bucket()
    by_team, by_role, by_task, by_model = defaultdict(bucket), defaultdict(bucket), defaultdict(bucket), defaultdict(bucket)
    for t in turns:
        add(overall, t, t["file"])
        add(by_team[t["team"]], t, t["file"])
        add(by_role[(t["team"], t["role"])], t, t["file"])
        add(by_task[t["branch"]], t, t["file"])
        add(by_model[t["model"]], t, t["file"])

    def row(label, b):
        return (f"{label:<34} turns={fmt(b['turns']):>7} files={len(b['files']):>4} "
                f"input={fmt(b['in']):>12} cache_w={fmt(b['cc']):>14} cache_r={fmt(b['cr']):>16} "
                f"output={fmt(b['out']):>11} total={fmt(b['total']):>16}")

    print("=== SAMPLE ===")
    print(f"day window (UTC turn timestamps): {DAY}")
    print(f"project folders found: {len({f for f, _ in files})}")
    print(f"jsonl files read: {len(files)} (main {sum(1 for i in file_info.values() if not i['sub'])}, subagent {sum(1 for i in file_info.values() if i['sub'])})")
    print(f"lines read: {fmt(lines_total)}")
    print(f"files with a turn in the window: {sum(1 for i in file_info.values() if i['window_turns'])}")
    print(f"usage-bearing turns in window before dedup: {fmt(sum(i['window_turns'] for i in file_info.values()))}")
    print(f"duplicate chunk records dropped (same message, same file): {fmt(dup_within)}")
    print(f"duplicate records dropped (same message, another file): {fmt(dup_across)}")
    print(f"turns after dedup: {fmt(overall['turns'])}")
    unp = ", ".join(f"{folder_team(k.split('/')[0])}/{'helper' if '/subagents/' in k else 'main'}/{os.path.basename(k)[:8]}={c}"
                    for k, c in sorted(unparsed.items())) or "none"
    print(f"files with unparseable lines (team/kind/id-prefix=bad lines): {unp}")
    print(f"unparseable lines total: {sum(unparsed.values())} in {len(unparsed)} files")
    print()

    print("=== TOTALS BY TEAM ===")
    for team in sorted(by_team):
        print(row(team, by_team[team]))
    print(row("ALL", overall))
    team_sum = sum(b["total"] for b in by_team.values())
    print(f"reconcile team sum == overall: {team_sum == overall['total']} ({fmt(team_sum)} vs {fmt(overall['total'])})")
    print("not visible on this laptop: Pi team sessions (logs stay on the Pi); cloud sessions")
    print()

    print("=== TOTALS BY ROLE (team, role) ===")
    for (team, role) in sorted(by_role, key=lambda k: (-by_role[k]["total"])):
        print(row(f"{team}/{role}", by_role[(team, role)]))
    role_sum = sum(b["total"] for b in by_role.values())
    print(f"reconcile role sum == overall: {role_sum == overall['total']} ({fmt(role_sum)} vs {fmt(overall['total'])})")
    unassigned_files = [i for i in file_info.values() if i["role"] == "unassigned" and i["window_turns"]]
    print(f"sessions with window turns but unassigned role: {len(unassigned_files)} files, "
          f"{fmt(sum(i['window_turns'] for i in unassigned_files))} turns")
    print()

    print("=== TOTALS BY MODEL ===")
    for m in sorted(by_model, key=lambda k: -by_model[k]["total"]):
        print(row(m, by_model[m]))
    print()

    print("=== TASKS (task = git branch of the turn), top 15 by total tokens ===")
    tasks = sorted(by_task.items(), key=lambda kv: -kv[1]["total"])
    for name, b in tasks[:15]:
        print(row(name, b))
    print(f"tasks listed: {len(tasks)}; reconcile task sum == overall: "
          f"{sum(b['total'] for b in by_task.values()) == overall['total']}")
    print()

    print("=== FIVE MOST EXPENSIVE SINGLE TURNS ===")
    for t in sorted(turns, key=lambda t: -t["total"])[:5]:
        print(f"team={t['team']} role={t['role']} task={t['branch']} model={t['model']} "
              f"sub={t['sub'] if 'sub' in t else '?'} input={fmt(t['input_tokens'])} cache_w={fmt(t['cache_creation_input_tokens'])} "
              f"cache_r={fmt(t['cache_read_input_tokens'])} output={fmt(t['output_tokens'])} total={fmt(t['total'])}")
    print()

    print("=== CONTEXT SIZE (max input-side tokens in any one turn, per session) ===")
    ctx = [i["max_ctx"] for i in file_info.values() if i["window_turns"]]
    if ctx:
        print(f"sessions: {len(ctx)}; median max context: {fmt(statistics.median(ctx))}; "
              f"max: {fmt(max(ctx))}")
        for thr in (100000, 200000, 300000):
            print(f"  sessions with max context >= {fmt(thr)}: {sum(1 for c in ctx if c >= thr)}")
    comp = Counter()
    for i in file_info.values():
        if i["compactions"]:
            comp[i["role"]] += i["compactions"]
    print(f"compaction records in window by role: {dict(comp) if comp else 'none found'}")
    print()

    print("=== FILES READ MORE THAN ONCE (Read tool, per session file, in window) ===")
    rr = defaultdict(lambda: [0, 0, 0])  # role -> [files with repeats, extra reads, total reads]
    for i in file_info.values():
        for p, c in i["reads"].items():
            rr[i["role"]][2] += c
        repeats = [c for c in i["reads"].values() if c > 1]
        if repeats:
            rr[i["role"]][0] += len(repeats)
            rr[i["role"]][1] += sum(c - 1 for c in repeats)
    for role in sorted(rr, key=lambda r: -rr[r][1]):
        a, b, c = rr[role]
        print(f"{role:<34} repeated-file pairs={a:>5} extra reads={b:>6} all reads={c:>6}")
    print()

    print("=== POLLED WAITS (Bash sleep commands in window) ===")
    sw = defaultdict(lambda: [0, 0])
    for i in file_info.values():
        for s in i["sleeps"]:
            sw[i["role"]][0] += 1
            sw[i["role"]][1] += s
    for role in sorted(sw, key=lambda r: -sw[r][1]):
        print(f"{role:<34} sleep commands={sw[role][0]:>5} seconds asked={sw[role][1]:>8}")
    if not sw:
        print("none found")
    print()

    print("=== PROMPT SIZE (first user message of sessions that started in window, characters) ===")
    ps = defaultdict(list)
    for i in file_info.values():
        if i["first_user_ts"] and str(i["first_user_ts"]).startswith(DAY):
            kind = "helper" if i["sub"] else "main"
            ps[(kind, i["role"])].append(len(i["first_user_text"] or ""))
    for k in sorted(ps, key=lambda k: -max(ps[k])):
        v = ps[k]
        print(f"{k[0]:<7}{k[1]:<34} n={len(v):>4} median={fmt(statistics.median(v)):>7} max={fmt(max(v)):>7}")
    print()

    print("=== ROLE ASSIGNMENT METHOD (files) ===")
    meth = Counter((i["sub"], i["role_method"]) for i in file_info.values())
    for (sub, m), c in sorted(meth.items(), key=lambda kv: -kv[1]):
        print(f"{'helper' if sub else 'main  ':<8}{m:<40} files={c}")

    print()
    print("=== TURNS THAT ISSUED A BASH SLEEP (polled waits), tokens they cost ===")
    sl = defaultdict(lambda: [0, 0])
    for mid, t in seen_msg.items():
        if mid in sleep_msgs:
            sl[t["role"]][0] += 1
            sl[t["role"]][1] += t["total"]
    for role in sorted(sl, key=lambda r: -sl[r][1]):
        print(f"{role:<34} sleep turns={sl[role][0]:>5} tokens in those turns={fmt(sl[role][1]):>14}")
    if not sl:
        print("none")
    print()
    print("=== SESSIONS WITH WINDOW TURNS (id = first 8 chars of file name) ===")
    per_file = defaultdict(lambda: [0, 0])
    for t in turns:
        per_file[t["file"]][0] += 1
        per_file[t["file"]][1] += t["total"]
    for key in sorted(per_file, key=lambda k: -per_file[k][1]):
        info = file_info[key]
        team = ROLE_TEAM.get(info["role"]) or info["team_folder"]
        name = os.path.basename(key)[:8]
        sub = "helper" if info["sub"] else "main"
        branches = sorted({t["branch"] for t in turns if t["file"] == key})
        print(f"{name} {sub:<7} {team:<9} {info['role']:<34} branch={'/'.join(branches)[:40]:<40} turns={per_file[key][0]:>4} "
              f"total={fmt(per_file[key][1]):>14} max_ctx={fmt(info['max_ctx']):>8} sleeps={len(info['sleeps']):>4}")

    print()
    print("=== TURNS WHOSE CONTEXT EXCEEDED 200,000 TOKENS (input side of that turn), by role ===")
    big = defaultdict(lambda: [0, 0, 0])
    for t in turns:
        ctx_t = t["input_tokens"] + t["cache_creation_input_tokens"] + t["cache_read_input_tokens"]
        if ctx_t > 200000:
            big[t["role"]][0] += 1
            big[t["role"]][1] += t["total"]
            big[t["role"]][2] += t["cache_read_input_tokens"]
    for role in sorted(big, key=lambda r: -big[r][1]):
        print(f"{role:<34} turns={big[role][0]:>5} total={fmt(big[role][1]):>14} cache_r={fmt(big[role][2]):>14}")
    excess = defaultdict(int)
    for t in turns:
        ctx_t = t["input_tokens"] + t["cache_creation_input_tokens"] + t["cache_read_input_tokens"]
        if ctx_t > 200000:
            excess[t["role"]] += ctx_t - 200000
    print("input-side tokens above 200,000 in those turns, by role (upper bound on cache reads a 200,000-token cap would avoid):")
    for role in sorted(excess, key=lambda r: -excess[r]):
        print(f"  {role:<34} {fmt(excess[role]):>14}")
    print(f"  all roles {fmt(sum(excess.values()))}")
    print(f"all roles: turns={sum(v[0] for v in big.values())} total={fmt(sum(v[1] for v in big.values()))} "
          f"share of day total={sum(v[1] for v in big.values()) / overall['total'] * 100:.1f}%")


if __name__ == "__main__":
    main()
