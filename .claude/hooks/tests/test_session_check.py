"""session_check.py, the SessionStart hook: run as a script, the way Claude
Code runs it, against throwaway repositories with a bare origin. It must
warn (systemMessage plus additionalContext), never block, and exit 0.

These tests do not use harness.run_hook, which reads PreToolUse decisions:
session_check reads the kit.json of the repository it is started in, so
each fixture carries its own.
"""
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HOOK = Path(__file__).resolve().parent.parent / "session_check.py"
GIT_ID = ["-c", "user.name=t", "-c", "user.email=t@example.invalid",
          "-c", "init.defaultBranch=main", "-c", "commit.gpgsign=false"]
KIT_JSON = {"protected_branches": ["main"]}
RESTART = ("settings.json differs: start a new session after updating, since "
           "hooks are read at session start")


def git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *GIT_ID, *args],
                          capture_output=True, text=True, check=True).stdout


def write(repo, rel, text):
    path = Path(repo) / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


class SessionCheck(unittest.TestCase):

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="kit_session_"))
        self.addCleanup(shutil.rmtree, self.root, True)
        self.origin = self.root / "origin.git"
        subprocess.run(["git", *GIT_ID, "init", "-q", "--bare", str(self.origin)],
                       check=True)
        git(self.origin, "symbolic-ref", "HEAD", "refs/heads/main")
        self.repo = self.make_clone("repo", seed=True)

    def make_clone(self, name, seed=False):
        """A work repository whose origin is the bare one. The first one
        seeds main with a minimal .claude/; later ones fetch it."""
        repo = self.root / name
        repo.mkdir()
        git(repo, "init", "-q")
        git(repo, "remote", "add", "origin", str(self.origin))
        if seed:
            write(repo, ".claude/kit.json", json.dumps(KIT_JSON))
            write(repo, ".claude/settings.json", '{"hooks": {}}\n')
            write(repo, ".claude/hooks/role_guard.py", "# role guard v1\n")
            write(repo, ".claude/agents/coder.md", "coder v1\n")
            write(repo, "README.md", "readme\n")
            git(repo, "add", "-A")
            git(repo, "commit", "-q", "-m", "seed")
            git(repo, "push", "-q", "origin", "main")
            git(repo, "branch", "-q", "--set-upstream-to=origin/main")
        else:
            git(repo, "fetch", "-q", "origin")
            git(repo, "checkout", "-q", "-b", "main", "origin/main")
        return repo

    def push_change(self, rel, text):
        """Another checkout changes rel on origin/main; self.repo fetches
        (the test fetches, the hook never does)."""
        other = self.make_clone("other%d" % len(list(self.root.iterdir())))
        write(other, rel, text)
        git(other, "commit", "-q", "-am", "change " + rel)
        git(other, "push", "-q", "origin", "main")
        git(self.repo, "fetch", "-q", "origin")

    def run_hook(self, cwd=None):
        """(exit code, parsed stdout or None, raw stdout, stderr)."""
        payload = {"hook_event_name": "SessionStart", "source": "startup",
                   "session_id": "s0000", "cwd": str(cwd or self.repo)}
        proc = subprocess.run([sys.executable, str(HOOK)], input=json.dumps(payload),
                              capture_output=True, text=True, cwd=str(cwd or self.repo),
                              timeout=60)
        self.assertEqual(proc.returncode, 0,
                         "session_check must always exit 0; stderr: " + proc.stderr)
        out = proc.stdout.strip()
        return proc.returncode, (json.loads(out) if out else None), proc.stdout, proc.stderr

    def assertWarning(self, data):
        self.assertIsNotNone(data, "expected a warning, got no output")
        self.assertTrue(data.get("systemMessage"), data)
        spec = data["hookSpecificOutput"]
        self.assertEqual(spec["hookEventName"], "SessionStart")
        self.assertTrue(spec.get("additionalContext"), data)
        self.assertNotIn("decision", data)
        self.assertNotIn("continue", data)
        return spec["additionalContext"]

    def test_t1_up_to_date_prints_nothing(self):
        status_before = git(self.repo, "status", "--porcelain", "--ignored")
        code, data, raw, _ = self.run_hook()
        self.assertEqual(raw, "")
        self.assertIsNone(data)
        # It writes nothing.
        self.assertEqual(git(self.repo, "status", "--porcelain", "--ignored"), status_before)

    def test_t2_newer_hook_on_origin_is_named_with_the_ff_command(self):
        self.push_change(".claude/hooks/role_guard.py", "# role guard v2\n")
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn(".claude/hooks/role_guard.py", text)
        top = git(self.repo, "rev-parse", "--show-toplevel").strip()
        self.assertIn(f"git -C {top} fetch && git -C {top} merge --ff-only origin/main", text)
        self.assertIn("1 commit", text)
        self.assertNotIn(RESTART, text)
        self.assertIn("origin/main", data["systemMessage"])

    def test_t3_settings_json_difference_adds_the_restart_sentence(self):
        self.push_change(".claude/settings.json", '{"hooks": {"SessionStart": []}}\n')
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn(".claude/settings.json", text)
        self.assertIn(RESTART, text)

    def test_t4_adopter_lock_names_an_edited_file(self):
        files = {}
        for rel in (".claude/hooks/role_guard.py", ".claude/agents/coder.md",
                    ".claude/settings.json"):
            files[rel] = hashlib.sha256((self.repo / rel).read_bytes()).hexdigest()
        write(self.repo, ".claude/kit.lock",
              json.dumps({"tag": "v0.0.0-test", "files": files}, indent=2) + "\n")
        write(self.repo, ".claude/agents/coder.md", "coder v1, edited by the adopter\n")
        # Committed and pushed, so only the lock sees the edit.
        git(self.repo, "add", "-A")
        git(self.repo, "commit", "-q", "-m", "adopter edit")
        git(self.repo, "push", "-q", "origin", "main")
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn("kit.lock", text)
        self.assertIn(".claude/agents/coder.md", text)
        self.assertNotIn(".claude/hooks/role_guard.py", text)

    def test_t5_no_origin_ref_cannot_compare(self):
        git(self.repo, "update-ref", "-d", "refs/remotes/origin/main")
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn("cannot compare", text)
        self.assertIn("origin/main", text)

    def test_t6_unreadable_kit_json_warns(self):
        write(self.repo, ".claude/kit.json", "{not json")
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn("kit.json", text)
        self.assertIn("kit.json", data["systemMessage"])

    def test_t7_uncommitted_hook_edit_is_reported(self):
        write(self.repo, ".claude/hooks/role_guard.py", "# role guard, edited here\n")
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn(".claude/hooks/role_guard.py", text)

    def test_t8_update_worktree_present_is_named_in_the_advice(self):
        write(self.repo, "update_worktree.py", "# the kit's worktree updater\n")
        self.push_change("README.md", "readme v2\n")
        _, data, _, _ = self.run_hook()
        text = self.assertWarning(data)
        self.assertIn("update_worktree.py", text)
        self.assertIn("--apply", text)


if __name__ == "__main__":
    unittest.main()
