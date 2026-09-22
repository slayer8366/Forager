"""history_guard.py: step 6. Crafted Bash payloads, with cwd a throwaway
repository checked out on main or on a feature branch."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from harness import bash, run_hook

HOOK = "history_guard.py"


def make_repo(branch):
    repo = Path(tempfile.mkdtemp(prefix="history_guard_test_"))
    g = ["git", "-C", str(repo), "-c", "user.name=t", "-c", "user.email=t@example.invalid"]
    subprocess.run(["git", "init", "-q", "-b", "main", str(repo)], check=True)
    subprocess.run(g + ["commit", "-q", "--allow-empty", "-m", "base"], check=True)
    if branch != "main":
        subprocess.run(g + ["checkout", "-q", "-b", branch], check=True)
    return repo


class HistoryGuard(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.on_main = make_repo("main")
        cls.on_feature = make_repo("feature")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.on_main, ignore_errors=True)
        shutil.rmtree(cls.on_feature, ignore_errors=True)

    def decide(self, command, repo, who="coder"):
        return run_hook(HOOK, bash(command, who, cwd=str(repo)))

    def assertDenied(self, command, repo, *words):
        decision, reason = self.decide(command, repo)
        self.assertEqual(decision, "deny", f"{command!r}: got {decision!r}")
        for w in words:
            self.assertIn(w, reason, command)

    def assertPasses(self, command, repo):
        decision, reason = self.decide(command, repo)
        self.assertIsNone(decision, f"{command!r}: {reason}")

    def test_force_push_denied_on_any_branch(self):
        for command in ("git push --force", "git push --force --dry-run",
                        "git push --force-with-lease origin feature",
                        "git push --force-with-lease=feature:abc origin feature",
                        "git push -f origin feature", "git push -uf origin feature"):
            with self.subTest(command):
                self.assertDenied(command, self.on_feature, "force")

    def test_merge_on_main_denied_elsewhere_allowed(self):
        self.assertDenied("git merge feature", self.on_main, "git merge", "main")
        self.assertPasses("git merge main", self.on_feature)
        self.assertPasses("git merge-base main feature", self.on_main)

    def test_merge_with_dash_c_uses_that_repository(self):
        self.assertDenied(f"git -C {self.on_main} merge feature", self.on_feature, "main")

    def test_push_to_main_denied(self):
        for command in ("git push origin main", "git push origin HEAD:main",
                        "git push origin feature:refs/heads/main",
                        "git push --dry-run origin main", "git push origin --all"):
            with self.subTest(command):
                self.assertDenied(command, self.on_feature, "main")

    def test_bare_push_on_main_denied(self):
        for command in ("git push", "git push origin", "git push origin HEAD"):
            with self.subTest(command):
                self.assertDenied(command, self.on_main, "main")

    def test_ordinary_push_on_a_branch_allowed(self):
        for command in ("git push", "git push -u origin feature",
                        "git push origin feature:feature", "git push origin HEAD"):
            with self.subTest(command):
                self.assertPasses(command, self.on_feature)

    def test_gh_pr_merge_denied(self):
        self.assertDenied("gh pr merge 112 --squash", self.on_feature, "gh pr merge")
        self.assertPasses("gh pr view 112", self.on_feature)

    def test_history_rewriting_denied(self):
        for command in ("git filter-repo --analyze", "git filter-branch --tree-filter x HEAD",
                        "git-filter-repo --path src"):
            with self.subTest(command):
                self.assertDenied(command, self.on_feature, "filter-")

    def test_applies_to_every_role(self):
        for who in (None, "pulse", "coder", "general-purpose"):
            with self.subTest(who):
                decision, _ = self.decide("git push --force", self.on_feature, who)
                self.assertEqual(decision, "deny")

    def test_unrelated_commands_untouched(self):
        for command in ("git status", "git log --oneline", "git commit -m 'push --force later'"):
            with self.subTest(command):
                self.assertPasses(command, self.on_main)


if __name__ == "__main__":
    unittest.main()
