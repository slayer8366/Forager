"""check_kit.py -- drift check for the vendored kit files.

Compares every file listed in .claude/kit.lock with the SHA-256 the lock
records for it, and fails naming each file that differs or is missing. Runs
from the repository root it sits in and never contacts the kit repository.
Adopter-owned files (.claude/kit.json, RECORD.md, CLAUDE.md, prompts/) are
not in the lock and are not checked.

    python3 check_kit.py
"""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCK = ".claude/kit.lock"


def check(root=HERE):
    """(ok, tag, problems, checked)."""
    lock_path = Path(root) / LOCK
    try:
        lock = json.loads(lock_path.read_text())
        tag, files = lock["tag"], lock["files"]
    except FileNotFoundError:
        return False, None, [f"{LOCK} does not exist; nothing to check against"], 0
    except (ValueError, KeyError, TypeError) as exc:
        return False, None, [f"{LOCK} is unreadable: {type(exc).__name__}: {exc}"], 0
    problems = []
    for path, expected in sorted(files.items()):
        file = Path(root) / path
        if not file.is_file():
            problems.append(f"{path}: missing")
            continue
        actual = hashlib.sha256(file.read_bytes()).hexdigest()
        if actual != expected:
            problems.append(f"{path}: sha256 {actual} differs from the lock's {expected}")
    return not problems, tag, problems, len(files)


def main():
    ok, tag, problems, checked = check()
    if ok:
        print(f"PASS: {checked} vendored file(s) match {LOCK} (kit {tag}).")
        return 0
    print(f"FAIL: {len(problems)} vendored file(s) differ from {LOCK}"
          f"{f' (kit {tag})' if tag else ''}:")
    for p in problems:
        print(f" - {p}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
