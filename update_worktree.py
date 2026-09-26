#!/usr/bin/env python3
"""Fast-forward a harness worktree, or the main checkout, after moving aside
untracked copies that the target branch already tracks byte for byte.

    python3 update_worktree.py <worktree path> [--apply]

The rule first: the checkout must be on the target branch (the first of
`protected_branches`, the main checkout) or on an unprotected branch (a
harness worktree), with no commits of its own and no changes to tracked
files. Untracked files identical to origin/<target>'s copies are moved to a
backup, then HEAD is fast-forwarded to origin/<target>, and nothing else.
A detached HEAD, another protected branch, and anything that is not a
fast-forward are refused.

Updating the main checkout this way is not a history_guard bypass: a
fast-forward to origin's own tip changes nothing on the remote and merges
nothing new into the branch; the branch only catches up with commits that
were already merged there through a pull request.

A dispatch saved by the dispatch hook sits untracked in the checkout the
planner runs in; once the store copy is merged, the same path is tracked on
the default branch and a fast-forward refuses to overwrite the untracked
file. This tool moves such files into a backup folder and then fast-forwards.
coder.md item 10 runs it on the main checkout after every merge.

Rules:

1. Reads. The path must be the top level of a git worktree
   (`git rev-parse --show-toplevel`). `<path>/.claude/kit.json` gives the
   target, `origin/<first of protected_branches>`, and `backup_dir`.

2. Stops before changing anything, printing every reason, exit 1, if:
   - HEAD is detached, or the current branch is protected but is not the
     target branch (a checkout on the target branch itself, the main
     checkout, is updated like any worktree);
   - `git rev-list origin/<branch>..HEAD` is not empty (own commits: the
     update would not be a fast-forward);
   - `git status --porcelain --untracked-files=no` is not empty (changes to
     tracked files);
   - an untracked file (`git ls-files --others --exclude-standard`) exists
     at a path in origin/<branch>'s tree with different bytes (a symlink or
     a non-file entry at that path counts as different);
   - with --apply: backup_dir is unset, or origin/<branch> is missing after
     the fetch.

3. Dry run (the default). Read-only, no fetch: it compares against
   origin/<branch> as last fetched, and says so. It prints the plan (files
   to move, files left alone, the fast-forward range) and exits 0 if the
   plan would succeed, 1 if not.

4. --apply. Checks rule 2, then `git fetch origin`, then checks rule 2
   again against the fetched ref. Each untracked file identical to
   origin/<branch>'s is moved (never overwriting) into a new folder
   `<backup_dir>/<UTC date>-NN/` (the next free NN) under its repo-relative
   path; `MANIFEST.sha256` there lists the moved files (`sha256sum -c`
   format), and one line is appended to `<backup_dir>/INDEX.md` naming the
   folder, the worktree path, update_worktree.py and origin/<branch>'s SHA.
   Then `git merge --ff-only origin/<branch>`, and the new HEAD is printed.
   If the merge fails it is reported, exit 1; the moved files stay in the
   backup and nothing is moved back. With nothing to move, no folder or
   INDEX line is written.

5. Never touched: untracked files not in the target tree, and ignored
   files. Nothing is deleted.

Git runs with GIT_OPTIONAL_LOCKS=0 and GIT_LITERAL_PATHSPECS=1. Standard
library only, Python 3.8+.
"""
import argparse
import datetime
import errno
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

ENV = dict(os.environ, GIT_OPTIONAL_LOCKS="0", GIT_LITERAL_PATHSPECS="1")
FOLDER_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})-(\d+)$")


def git(path, *args):
    """Run git in path; return (returncode, stdout bytes, stderr text)."""
    r = subprocess.run(["git", "-C", path] + list(args), stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, env=ENV)
    return r.returncode, r.stdout, r.stderr.decode("utf-8", "replace").strip()


def text(b):
    return b.decode("utf-8", "surrogateescape").strip()


def fail(reasons):
    print("update_worktree: stopped; no file was moved and no merge was run:")
    for r in reasons:
        print(f"  - {r}")
    return 1


def read_config(top):
    path = os.path.join(top, ".claude", "kit.json")
    try:
        with open(path, encoding="utf-8") as f:
            conf = json.load(f)
    except (OSError, ValueError) as e:
        return None, None, None, f"cannot read {path}: {e}"
    if not isinstance(conf, dict):
        return None, None, None, f"{path} is not a JSON object"
    protected = conf.get("protected_branches")
    if not (isinstance(protected, list) and protected
            and all(isinstance(b, str) and b for b in protected)):
        return None, None, None, f"{path}: protected_branches is not a non-empty list of names"
    backup = conf.get("backup_dir")
    if backup is not None and not (isinstance(backup, str) and backup.strip()):
        return None, None, None, f"{path}: backup_dir is not a non-empty string"
    if backup is not None:
        backup = os.path.expanduser(backup)
    return protected, protected[0], backup, None


def blob_id(top, rel):
    rc, out, err = git(top, "hash-object", "--no-filters", "--", rel)
    return text(out) if rc == 0 else None


def survey(top, protected, branch):
    """Check rule 2 (except backup_dir) against origin/<branch> as it is now.

    Returns (reasons, plan); plan is a dict with target, head, move, leave."""
    ref = f"refs/remotes/origin/{branch}"
    reasons = []
    rc, out, _ = git(top, "symbolic-ref", "-q", "--short", "HEAD")
    current = text(out) if rc == 0 else None
    if current is None:
        reasons.append("HEAD is detached")
    elif current in protected and current != branch:
        reasons.append(f"the current branch `{current}` is protected and is not the "
                       f"target branch `{branch}`")
    rc, out, _ = git(top, "rev-parse", "--verify", "--quiet", ref)
    if rc != 0:
        reasons.append(f"origin/{branch} does not exist here (fetch first)")
        return reasons, None
    target = text(out)
    rc, out, err = git(top, "rev-parse", "--verify", "HEAD")
    if rc != 0:
        reasons.append(f"cannot read HEAD: {err}")
        return reasons, None
    head = text(out)
    rc, out, err = git(top, "rev-list", f"{ref}..HEAD")
    if rc != 0:
        reasons.append(f"git rev-list failed: {err}")
    elif text(out):
        n = len(text(out).splitlines())
        reasons.append(f"the worktree has {n} commit(s) not on origin/{branch} "
                       f"(not a fast-forward)")
    rc, out, err = git(top, "status", "--porcelain", "--untracked-files=no")
    if rc != 0:
        reasons.append(f"git status failed: {err}")
    elif text(out):
        reasons.append("tracked files have changes:\n      "
                       + "\n      ".join(text(out).splitlines()))
    rc, out, err = git(top, "ls-files", "--others", "--exclude-standard", "-z")
    if rc != 0:
        reasons.append(f"git ls-files failed: {err}")
        return reasons, None
    untracked = sorted(p for p in out.decode("utf-8", "surrogateescape").split("\0") if p)
    move, leave = [], []
    for rel in untracked:
        rc, out, err = git(top, "ls-tree", "-z", target, "--", rel)
        if rc != 0:
            reasons.append(f"git ls-tree failed for {rel}: {err}")
            continue
        entry = out.decode("utf-8", "surrogateescape").rstrip("\0")
        if not entry:
            leave.append(rel)
            continue
        meta = entry.split("\t", 1)[0].split()
        full = os.path.join(top, rel)
        if len(meta) != 3 or meta[1] != "blob" or meta[0] == "120000" \
                or os.path.islink(full) or not os.path.isfile(full):
            reasons.append(f"untracked {rel} differs from origin/{branch}'s entry "
                           f"(not a plain file on both sides)")
        elif blob_id(top, rel) != meta[2]:
            reasons.append(f"untracked {rel} differs from origin/{branch}'s copy")
        else:
            move.append(rel)
    return reasons, {"target": target, "head": head, "move": move, "leave": leave}


def print_plan(plan, branch):
    print(f"files to move ({len(plan['move'])}):")
    for rel in plan["move"]:
        print(f"  move   {rel}")
    print(f"files left alone ({len(plan['leave'])}):")
    for rel in plan["leave"]:
        print(f"  leave  {rel}")
    if plan["head"] == plan["target"]:
        print(f"fast-forward: none, HEAD is origin/{branch} ({plan['target'][:7]})")
    else:
        print(f"fast-forward: {plan['head'][:7]}..{plan['target'][:7]} (origin/{branch})")


def new_folder(backup):
    os.makedirs(backup, exist_ok=True)
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    while True:
        used = [int(m.group(2)) for m in (FOLDER_RE.match(n) for n in os.listdir(backup))
                if m and m.group(1) == date]
        folder = os.path.join(backup, f"{date}-{max(used, default=0) + 1:02d}")
        try:
            os.mkdir(folder)
            return folder
        except FileExistsError:
            continue


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def move_file(src, dst):
    """Move src to dst without overwriting; dst must not exist."""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    try:
        os.link(src, dst)
    except OSError as e:
        if e.errno not in (errno.EXDEV, errno.EPERM, errno.EMLINK, errno.ENOTSUP):
            raise
        digest = sha256_of(src)
        fd = os.open(dst, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as out, open(src, "rb") as inp:
            shutil.copyfileobj(inp, out)
        shutil.copystat(src, dst)
        if sha256_of(dst) != digest:
            raise OSError(f"copy of {src} to {dst} does not match; source kept")
    os.unlink(src)


def apply(top, protected, branch, backup):
    if backup is None:
        return fail(["backup_dir is not set in .claude/kit.json; --apply needs it"])
    reasons, plan = survey(top, protected, branch)
    if reasons:
        return fail(reasons)
    rc, _, err = git(top, "fetch", "origin")
    if rc != 0:
        return fail([f"git fetch origin failed: {err}"])
    reasons, plan = survey(top, protected, branch)
    if reasons:
        return fail(reasons)
    print(f"after fetching, comparing against origin/{branch} {plan['target']}")
    print_plan(plan, branch)
    if plan["move"]:
        folder = new_folder(backup)
        lines = []
        for rel in plan["move"]:
            dst = os.path.join(folder, rel)
            move_file(os.path.join(top, rel), dst)
            lines.append(f"{sha256_of(dst)}  {rel}\n")
        with open(os.path.join(folder, "MANIFEST.sha256"), "x", encoding="utf-8") as f:
            f.writelines(lines)
        name = os.path.basename(folder)
        row = (f"| {name} | Move by update_worktree.py from worktree {top}: "
               f"{len(plan['move'])} untracked file(s) byte-identical to origin/{branch} at "
               f"{plan['target']}, at their repo-relative paths; MANIFEST.sha256 lists them. "
               f"Nothing was deleted | They would have blocked `git merge --ff-only "
               f"origin/{branch}` | update_worktree.py --apply | Not needed: git holds "
               f"identical copies at {plan['target']} |\n")
        index = os.path.join(backup, "INDEX.md")
        prefix = ""
        if os.path.exists(index) and os.path.getsize(index):
            with open(index, "rb") as f:
                f.seek(-1, os.SEEK_END)
                if f.read(1) != b"\n":
                    prefix = "\n"
        with open(index, "a", encoding="utf-8") as f:
            f.write(prefix + row)
        print(f"moved {len(plan['move'])} file(s) to {folder}")
    rc, out, err = git(top, "merge", "--ff-only", f"refs/remotes/origin/{branch}")
    if rc != 0:
        print(f"update_worktree: git merge --ff-only origin/{branch} failed: {err or text(out)}")
        if plan["move"]:
            print(f"the moved files stay in {folder}; nothing was moved back")
        return 1
    rc, out, _ = git(top, "rev-parse", "HEAD")
    print(f"HEAD is now {text(out)}")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description="Fast-forward a harness worktree, or the "
                                 "main checkout, to origin/<first protected branch>.")
    ap.add_argument("path", help="top level of the worktree")
    ap.add_argument("--apply", action="store_true", help="fetch, move, fast-forward")
    args = ap.parse_args(argv)
    path = os.path.realpath(os.path.expanduser(args.path))
    rc, out, err = git(path, "rev-parse", "--show-toplevel")
    if rc != 0:
        return fail([f"{args.path} is not in a git worktree: {err}"])
    top = os.path.realpath(text(out))
    if top != path:
        return fail([f"{args.path} is not the top level of its worktree ({top} is)"])
    protected, branch, backup, err = read_config(top)
    if err:
        return fail([err])
    print(f"worktree {top}; target origin/{branch}")
    if args.apply:
        return apply(top, protected, branch, backup)
    print(f"dry run: no fetch; comparing against origin/{branch} as last fetched")
    reasons, plan = survey(top, protected, branch)
    if plan:
        print_plan(plan, branch)
    if reasons:
        print("the plan would not succeed:")
        for r in reasons:
            print(f"  - {r}")
        return 1
    print("the plan would succeed; rerun with --apply to act")
    return 0


if __name__ == "__main__":
    sys.exit(main())
