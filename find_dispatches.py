#!/usr/bin/env python3
"""List hook-saved dispatches that sit untracked in other worktrees.

Run from the root of a checkout:

    python3 find_dispatches.py

The store is the tracked files under this checkout's prompts/preserved/.
Every other worktree that `git worktree list` gives is scanned for untracked
files under its prompts/preserved/. Each candidate is reported with its
worktree, file name, size, sha256 and hook header (HEAD, Preserved, target,
type), and given one status:

  in-store  its sha256 equals a store file's; that file is named
  record    a store name is proposed, with a copy command and a citation
  refused   the proposed name is dated today (UTC); rerun after UTC midnight
  stop      stop and ask: not hook-saved, an unknown header HEAD, a name
            clash, or a worktree that can't be read

A file is T1+ if dispatch_guard.py at its header HEAD mentions the shared
counter (`dispatch-seq`); it keeps the name the hook gave it. A file is pre-T1
if dispatch_guard.py exists at that HEAD without it; it is numbered
<Preserved date>-NN, one above the highest number for that date among the
store's names and the names already proposed in this run, in Preserved order.

Read-only: the tool writes nothing, runs git with GIT_OPTIONAL_LOCKS=0 and
only commands that don't write the index or refs, and never reads or touches
the dispatch counter. It copies nothing; the copy commands are for the
operator. Exit status 1 if any item is `stop`, else 0.

Standard library only, Python 3.8+.
"""
import datetime
import hashlib
import os
import re
import shlex
import subprocess
import sys

STORE = "prompts/preserved"
DELIM = "--- verbatim prompt follows ---"
GUARD = ".claude/hooks/dispatch_guard.py"
COUNTER_MARK = "dispatch-seq"
NAME_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})-(\d+)$")
STAMP_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})T\d{2}:\d{2}:\d{2}Z$")
HEX_RE = re.compile(r"^[0-9a-f]{7,40}$")
STATUS_ORDER = ["in-store", "record", "refused", "stop"]
ENV = dict(os.environ, GIT_OPTIONAL_LOCKS="0")


def git(cwd, *args):
    """Run a read-only git command; return (returncode, stdout bytes, stderr text)."""
    r = subprocess.run(["git", "-C", cwd] + list(args), stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, env=ENV)
    return r.returncode, r.stdout, r.stderr.decode("utf-8", "replace").strip()


def warn(msg):
    print(f"warning: {msg}", file=sys.stderr)


def stem(path):
    base = os.path.basename(path)
    return base[:-3] if base.endswith(".md") else base


def untracked_store_files(wt):
    rc, out, err = git(wt, "ls-files", "--others", "--exclude-standard", "-z",
                       "--", STORE + "/")
    if rc != 0:
        return None, err or f"git ls-files exited {rc}"
    return [p for p in out.decode("utf-8", "surrogateescape").split("\0") if p], None


def worktrees(root):
    rc, out, err = git(root, "worktree", "list", "--porcelain")
    if rc != 0:
        raise SystemExit(f"find_dispatches: git worktree list failed: {err}")
    result, cur = [], None
    for line in out.decode("utf-8", "surrogateescape").splitlines():
        if line.startswith("worktree "):
            cur = {"path": line[len("worktree "):], "bare": False, "prunable": False}
            result.append(cur)
        elif cur is not None and line == "bare":
            cur["bare"] = True
        elif cur is not None and line.startswith("prunable"):
            cur["prunable"] = True
    return [w for w in result if not w["bare"]]


def parse_header(data):
    """The hook header's fields, or (None, reason) if the file is not hook-saved."""
    lines = data.decode("utf-8", "replace").splitlines()
    if DELIM not in lines:
        return None, "no delimiter line"
    fields = {}
    for line in lines[:lines.index(DELIM)]:
        key, sep, value = line.partition(":")
        if sep and key not in fields:
            fields[key] = value.strip()
    if "HEAD" not in fields:
        return None, "no HEAD: line"
    if "Preserved" not in fields:
        return None, "no Preserved: line"
    stamp = fields["Preserved"].split(" ", 1)[0] if fields["Preserved"] else ""
    m = STAMP_RE.match(stamp)
    if not m:
        return None, "Preserved: line has no UTC timestamp"
    try:
        datetime.date.fromisoformat(m.group(1))
    except ValueError:
        return None, "Preserved: date is not a calendar date"
    return {
        "head": fields["HEAD"],
        "preserved_line": "Preserved: " + fields["Preserved"],
        "stamp": stamp,
        "date": m.group(1),
        "target": fields.get("Target subagent", "?"),
        "type": fields.get("Type", "?"),
    }, None


def hook_class(root, head, cache):
    """'T1+' or 'pre-T1', or (None, reason) if the header HEAD can't be resolved."""
    if head in cache:
        return cache[head]
    if not HEX_RE.match(head):
        res = (None, f"header HEAD {head!r} is not a hex commit id")
    elif git(root, "rev-parse", "--verify", "--quiet", head + "^{commit}")[0] != 0:
        res = (None, f"header HEAD {head} is not a commit in this repository")
    else:
        rc, out, _ = git(root, "cat-file", "blob", f"{head}:{GUARD}")
        if rc != 0:
            res = (None, f"{GUARD} is absent at header HEAD {head}")
        elif COUNTER_MARK.encode() in out:
            res = ("T1+", None)
        else:
            res = ("pre-T1", None)
    cache[head] = res
    return res


def main():
    today = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    rc, out, err = git(os.getcwd(), "rev-parse", "--show-toplevel")
    if rc != 0:
        print(f"find_dispatches: not inside a git checkout: {err}", file=sys.stderr)
        return 1
    root = out.decode("utf-8", "surrogateescape").strip()

    # The store: tracked files under this checkout's prompts/preserved/.
    rc, out, err = git(root, "ls-files", "-z", "--", STORE + "/")
    if rc != 0:
        print(f"find_dispatches: git ls-files failed: {err}", file=sys.stderr)
        return 1
    store_by_sha, store_names = {}, set()
    for rel in sorted(p for p in out.decode("utf-8", "surrogateescape").split("\0") if p):
        store_names.add(stem(rel))
        try:
            with open(os.path.join(root, rel), "rb") as f:
                sha = hashlib.sha256(f.read()).hexdigest()
        except OSError as exc:
            warn(f"tracked store file {rel} can't be read: {exc.strerror}")
            continue
        store_by_sha.setdefault(sha, []).append(stem(rel))

    own, _ = untracked_store_files(root)
    if own:
        warn(f"{len(own)} untracked file(s) under {STORE}/ in this checkout "
             f"not scanned (only other worktrees are scanned)")

    items = []          # dicts: status, name, note, sources, sha, size, header
    groups = {}         # sha256 -> item under construction
    scanned = 0
    real_root = os.path.realpath(root)
    for wt in worktrees(root):
        path = wt["path"]
        if os.path.realpath(path) == real_root:
            continue
        scanned += 1
        if wt["prunable"] or not os.path.isdir(path):
            items.append({"status": "stop", "name": path, "sources": [],
                          "note": "worktree can't be read: its directory is missing"})
            continue
        files, err = untracked_store_files(path)
        if files is None:
            items.append({"status": "stop", "name": path, "sources": [],
                          "note": f"worktree can't be read: {err}"})
            continue
        for rel in files:
            full = os.path.join(path, rel)
            try:
                with open(full, "rb") as f:
                    data = f.read()
            except OSError as exc:
                items.append({"status": "stop", "name": os.path.basename(rel),
                              "sources": [(path, full)],
                              "note": f"file can't be read: {exc.strerror}"})
                continue
            sha = hashlib.sha256(data).hexdigest()
            if sha in groups:
                groups[sha]["sources"].append((path, full))
                continue
            groups[sha] = {"sha": sha, "size": len(data), "data": data,
                           "sources": [(path, full)]}

    cache = {}
    t1, pre = [], []
    for sha, g in groups.items():
        g["sources"].sort(key=lambda s: s[1])
        first = g["sources"][0][1]
        header, reason = parse_header(g.pop("data"))
        g["header"] = header
        if sha in store_by_sha:
            names = store_by_sha[sha]
            g.update(status="in-store", name=names[0],
                     note="identical to the store file "
                          + ", ".join(f"{STORE}/{n}.md" for n in names))
        elif header is None:
            g.update(status="stop", name=os.path.basename(first),
                     note=f"not hook-saved: {reason}")
        else:
            cls, reason = hook_class(root, header["head"], cache)
            if cls is None:
                g.update(status="stop", name=os.path.basename(first),
                         note=f"unknown hook: {reason}")
            else:
                g["cls"] = cls
                (t1 if cls == "T1+" else pre).append(g)
                continue
        items.append(g)

    order = lambda g: (g["header"]["stamp"], g["sources"][0][1])
    taken = set()

    # T1+ first, so a refused T1+ name still counts as taken for pre-T1 numbering.
    for g in sorted(t1, key=order):
        items.append(g)
        names = sorted({stem(s[1]) for s in g["sources"]})
        name = names[0]
        m = NAME_RE.match(name)
        if len(names) > 1:
            g.update(status="stop", name=name,
                     note="T1+: identical copies under different names: "
                          + ", ".join(names))
        elif not m:
            g.update(status="stop", name=name,
                     note="T1+: the hook's name is not <date>-NN")
        elif m.group(1) != g["header"]["date"]:
            g.update(status="stop", name=name,
                     note=f"T1+: the name's date differs from the Preserved "
                          f"date {g['header']['date']}")
        elif name in store_names:
            g.update(status="stop", name=name,
                     note="T1+: the name is taken in the store by other content")
        elif name in taken:
            g.update(status="stop", name=name,
                     note="T1+: the name is proposed in this run for other content")
        else:
            taken.add(name)
            g.update(name=name, note="T1+: the hook's name")
            g["status"] = "refused" if m.group(1) == today else "record"

    for g in sorted(pre, key=order):
        items.append(g)
        date = g["header"]["date"]
        top = 0
        for n in store_names | taken:
            m = NAME_RE.match(n)
            if m and m.group(1) == date:
                top = max(top, int(m.group(2)))
        name = f"{date}-{top + 1:02d}"
        taken.add(name)
        g.update(name=name, note=f"pre-T1: numbered after {date}-{top:02d}")
        g["status"] = "refused" if date == today else "record"

    for g in items:
        if g["status"] == "refused":
            g["note"] += "; dated today; rerun after UTC midnight"

    items.sort(key=lambda g: (STATUS_ORDER.index(g["status"]), g["name"]))
    counts = {s: sum(1 for g in items if g["status"] == s) for s in STATUS_ORDER}

    print(f"Store: {root}/{STORE}, {len(store_names)} tracked file(s). "
          f"Other worktrees scanned: {scanned}. Today (UTC): {today}.")
    print()
    for g in items:
        print(f"{g['status']} {g['name']}  {g['note']}")
        if g.get("sha"):
            print(f"    {g['size']} bytes, sha256 {g['sha']}")
        h = g.get("header")
        if h:
            print(f"    header: HEAD {h['head']}, {h['preserved_line']}, "
                  f"target {h['target']}, type {h['type']}")
        for wt, full in g["sources"]:
            print(f"    from worktree {wt}: {os.path.basename(full)}")
    print()
    print("Counts: " + ", ".join(f"{s} {counts[s]}" for s in STATUS_ORDER))

    record = [g for g in items if g["status"] == "record"]
    print()
    print("Copy commands:")
    for g in record:
        print(f"  cp -- {shlex.quote(g['sources'][0][1])} {STORE}/{g['name']}.md")
    if not record:
        print("  (none)")
    print()
    print("Citations:")
    for g in record:
        h = g["header"]
        src = g["sources"][0][1]
        also = "".join(f"; also at {s[1]}" for s in g["sources"][1:])
        print(f"  `{STORE}/{g['name']}.md` from {src} ({g['size']} bytes, "
              f"sha256 {g['sha']}; header \"{h['preserved_line']}\", "
              f"HEAD {h['head']}, target {h['target']}, type {h['type']}; "
              f"{g['cls']}{also})")
    if not record:
        print("  (none)")

    return 1 if counts["stop"] else 0


if __name__ == "__main__":
    sys.exit(main())
