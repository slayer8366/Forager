"""history_guard.py. Crafted Bash payloads, with cwd a throwaway
repository checked out on main or on a feature branch."""
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from harness import TEST_CONFIG, bash, run_hook, slow_path, write_sleeper

HOOK = "history_guard.py"
PREFIX = TEST_CONFIG["guard_env_prefix"]


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

    def test_protected_branches_come_from_config(self):
        config = dict(TEST_CONFIG, protected_branches=["release"])
        on_release = make_repo("release")
        try:
            def decide(command, repo):
                return run_hook(HOOK, bash(command, "coder", cwd=str(repo)), config=config)
            for command, repo in (("git merge feature", on_release),
                                  ("git push", on_release),
                                  ("git push origin release", self.on_feature),
                                  ("git push origin HEAD:refs/heads/release", self.on_feature)):
                with self.subTest(command):
                    decision, reason = decide(command, repo)
                    self.assertEqual(decision, "deny", f"{command!r}: {reason}")
                    self.assertIn("release", reason)
            # main is not protected under this config.
            for command, repo in (("git merge feature", self.on_main),
                                  ("git push origin main", self.on_feature),
                                  ("git push", self.on_main)):
                with self.subTest(command):
                    decision, reason = decide(command, repo)
                    self.assertIsNone(decision, f"{command!r}: {reason}")
        finally:
            shutil.rmtree(on_release, ignore_errors=True)

    def test_unrelated_commands_untouched(self):
        for command in ("git status", "git log --oneline", "git commit -m 'push --force later'"):
            with self.subTest(command):
                self.assertPasses(command, self.on_main)

    def test_heredoc_message_then_push_to_branch_allowed(self):
        self.assertPasses("git commit -q --allow-empty -F - <<'EOF'\nDon't block this\nEOF\n"
                          "git push origin feature", self.on_feature)

    def test_word_push_in_unparseable_text_allowed(self):
        self.assertPasses("echo don't push yet", self.on_feature)

    def test_push_to_main_after_heredoc_blocked(self):
        self.assertDenied("cat <<'EOF' > /tmp/x\nit's data\nEOF\ngit push origin main",
                          self.on_feature, "protected branch")

    def test_newline_separates_commands(self):
        for command in ("git status\ngit push origin main",
                        "cat <<EOF\ngit push origin main"):  # no closing line, so kept
            with self.subTest(command):
                self.assertDenied(command, self.on_feature, "protected branch")

    def test_tilde_in_dash_c_expanded_for_push(self):
        # The shell expands ~ in `git -C ~/repo`; the hook, given the
        # unexpanded text, expands it the same way (HOME is a temporary one).
        home = Path(tempfile.mkdtemp(prefix="history_guard_home_"))
        self.addCleanup(shutil.rmtree, home, ignore_errors=True)
        shutil.copytree(self.on_feature, home / "repo")
        env = {"HOME": str(home)}
        for command, expect in (("git -C ~/repo push origin feature", None),
                                ("git -C ~/repo push origin main", "deny")):
            with self.subTest(command):
                decision, reason = run_hook(HOOK, bash(command, "coder"), env=env)
                self.assertEqual(decision, expect, f"{command!r}: {reason}")
                if expect:
                    self.assertIn("protected branch", reason, command)

    def test_tilde_in_dash_c_expanded_for_merge(self):
        home = Path(tempfile.mkdtemp(prefix="history_guard_home_"))
        self.addCleanup(shutil.rmtree, home, ignore_errors=True)
        shutil.copytree(self.on_feature, home / "feature_repo")
        shutil.copytree(self.on_main, home / "main_repo")
        env = {"HOME": str(home)}
        for command, expect in (("git -C ~/feature_repo merge main", None),
                                ("git -C ~/main_repo merge feature", "deny")):
            with self.subTest(command):
                decision, reason = run_hook(HOOK, bash(command, "coder"), env=env)
                self.assertEqual(decision, expect, f"{command!r}: {reason}")
                if expect:
                    self.assertIn("while on main", reason, command)

    def test_unparseable_push_and_continuation_still_blocked(self):
        for command, word in (("git push origin feature && echo 'oops", "could not parse"),
                              ("git push \\\norigin main", "main")):
            with self.subTest(command):
                self.assertDenied(command, self.on_feature, word)


def init_repo(path, branch):
    """A repository at path (created) with one commit, checked out on branch."""
    path.mkdir(parents=True, exist_ok=True)
    g = ["git", "-C", str(path), "-c", "user.name=t", "-c", "user.email=t@example.invalid"]
    subprocess.run(["git", "init", "-q", "-b", branch, str(path)], check=True)
    subprocess.run(g + ["commit", "-q", "--allow-empty", "-m", "base"], check=True)


class OuterRepos(unittest.TestCase):
    """The fixture RelativeDashC and PushWalk share: the payload cwd is
    `outer`, on feature, holding `sub` on main and `sub2` on feature. The
    hook process runs in the harness's default directory (the test
    runner's), not outer. No tests of its own."""

    @classmethod
    def setUpClass(cls):
        cls.outer = Path(tempfile.mkdtemp(prefix="history_guard_outer_"))
        init_repo(cls.outer, "feature")
        init_repo(cls.outer / "sub", "main")
        init_repo(cls.outer / "sub2", "feature")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.outer, ignore_errors=True)

    def decide(self, command, cwd=None):
        return run_hook(HOOK, bash(command, "coder", cwd=str(cwd or self.outer)))


class RelativeDashC(OuterRepos):
    """A relative `git -C` directory is read against the payload's cwd, not
    the hook process's own directory."""

    def test_relative_dash_c_push_from_main_denied(self):
        decision, reason = self.decide("git -C sub push origin")
        self.assertEqual(decision, "deny", reason)
        self.assertIn("protected branch", reason)
        self.assertIn("which is main", reason)

    def test_relative_dash_c_merge_on_main_denied(self):
        decision, reason = self.decide("git -C sub merge x")
        self.assertEqual(decision, "deny", reason)
        self.assertIn("while on main", reason)

    def test_relative_dash_c_push_from_feature_allowed(self):
        decision, reason = self.decide("git -C sub2 push origin feature")
        self.assertIsNone(decision, reason)


class PushWalk(OuterRepos):
    """R2: the push walk follows `cd` and `pushd` from the payload's cwd,
    sees git behind shell keywords, wrappers, assignments and a path to git,
    denies `+` refspecs as force pushes, and the `git merge` check sees
    `--git-dir=` and `--work-tree=`."""

    def assertDenied(self, command, *words, cwd=None):
        decision, reason = self.decide(command, cwd)
        self.assertEqual(decision, "deny", f"{command!r}: got {decision!r} ({reason})")
        for w in words:
            self.assertIn(w, reason, command)

    def assertPasses(self, command, cwd=None):
        decision, reason = self.decide(command, cwd)
        self.assertIsNone(decision, f"{command!r}: {reason}")

    def test_cd_into_main_then_bare_push_denied(self):
        for command in ("cd sub && git push origin",
                        "cd sub; git push origin",
                        "pushd sub && git push origin && popd",
                        "cd sub && cd ../sub2 && cd ../sub && git push origin"):
            with self.subTest(command):
                self.assertDenied(command, "protected branch", "which is main")

    def test_cd_into_feature_or_subshell_cd_then_push_allowed(self):
        # outer is on feature: a cd inside `(...)` does not reach the push after it.
        for command in ("cd sub2 && git push origin feature",
                        "(cd sub) && git push origin"):
            with self.subTest(command):
                self.assertPasses(command)

    def test_unresolvable_cd_then_bare_push_denied_by_name(self):
        for command in ("cd - && git push origin",
                        'cd "$D" && git push origin'):
            with self.subTest(command):
                self.assertDenied(command, "cannot be determined")

    def test_push_to_main_behind_keyword_wrapper_assignment_or_path_denied(self):
        for command in ("{ git push origin main; }",
                        "if true; then git push origin main; fi",
                        "for x in 1; do git push origin main; done",
                        "time git push origin main",
                        "command git push origin main",
                        "exec git push origin main",
                        "env -u X git push origin main",
                        "GIT_TRACE=1 git push origin main",
                        "/usr/bin/git push origin main"):
            with self.subTest(command):
                self.assertDenied(command, "protected branch", "main")

    def test_push_to_feature_behind_keyword_or_wrapper_allowed(self):
        for command in ("{ git push origin feature; }",
                        "time git push origin feature"):
            with self.subTest(command):
                self.assertPasses(command)

    def test_plus_refspec_denied_as_force_push_to_any_destination(self):
        for command in ("git push origin +feature", "git push origin +main"):
            with self.subTest(command):
                self.assertDenied(command, "force")

    def test_full_refspec_to_feature_allowed(self):
        self.assertPasses("git push origin feature:refs/heads/feature")

    def test_merge_with_git_dir_or_work_tree_on_main_denied(self):
        for command in ("git --git-dir=.git merge x", "git --work-tree=. merge x"):
            with self.subTest(command):
                self.assertDenied(command, "while on main", cwd=self.outer / "sub")


class CommandWord(OuterRepos):
    """R3: every rule is decided on a segment's command word and arguments,
    so a guarded word inside an argument denies nothing; the strings given
    to `sh -c`, `bash -lc` and `eval` are walked as commands; a GraphQL
    merge, `gh alias set` and quoted or split `gh pr merge` words are
    denied; unparseable text is denied only when it mentions a guarded
    command."""

    def assertDenied(self, command, *words, cwd=None):
        decision, reason = self.decide(command, cwd)
        self.assertEqual(decision, "deny", f"{command!r}: got {decision!r} ({reason})")
        for w in words:
            self.assertIn(w, reason, command)

    def assertPasses(self, command, cwd=None):
        decision, reason = self.decide(command, cwd)
        self.assertIsNone(decision, f"{command!r}: {reason}")

    def test_guarded_words_in_arguments_pass(self):
        # sub is on main: none of these runs a push, a merge or a filter.
        for command in ('git grep -E "git merge|--force" -- .',
                        'git commit -m "gh pr merge 5 --merge later"',
                        "echo git push --force origin main",
                        "git log --grep filter-repo",
                        "python3 -c \"print('git push origin main')\""):
            with self.subTest(command):
                self.assertPasses(command, cwd=self.outer / "sub")

    def test_push_to_feature_then_another_command_passes(self):
        # outer is on feature; the `-rf` after the push is rm's, not a force flag.
        for command in ("git push origin feature\nrm -rf build",
                        "sh -c 'git push origin feature'"):
            with self.subTest(command):
                self.assertPasses(command)

    def test_push_inside_shell_string_or_eval_denied(self):
        for command, words in (("sh -c 'git push origin main'", ("protected branch",)),
                               ('bash -lc "cd sub && git push origin"',
                                ("protected branch", "which is main")),
                               ("eval 'git push origin main'", ("protected branch",)),
                               ("sh -c 'git push --force origin x'", ("force",))):
            with self.subTest(command):
                self.assertDenied(command, *words)

    def test_quoted_or_split_gh_pr_merge_words_denied(self):
        for command in ('gh "pr" merge 5 -m', 'gh pr mer""ge 5 -m'):
            with self.subTest(command):
                self.assertDenied(command, "denied")

    def test_graphql_merge_and_alias_set_denied(self):
        self.assertDenied("gh api graphql -f query='mutation { mergePullRequest(input: "
                          "{pullRequestId: \"x\"}) { clientMutationId } }'",
                          "mergePullRequest")
        self.assertDenied("gh alias set m 'pr merge'", "gh alias set")

    def test_merge_after_cd_or_behind_git_dir_denied(self):
        self.assertDenied("cd sub && git merge x", "while on main")
        self.assertDenied("git --git-dir=sub/.git merge x", "cannot be determined")

    def test_earlier_denials_still_hold(self):
        for command, word in (("gh api -X PUT repos/o/r/pulls/5/merge -f merge_method=merge",
                               "gh pr merge"),
                              ("git push origin feature && echo 'oops", "could not parse")):
            with self.subTest(command):
                self.assertDenied(command, word)

    def test_unparseable_text_denied_naming_the_mention(self):
        self.assertDenied("echo 'oops; gh pr merge 5 -m", "could not parse", "gh pr merge")


class Timeouts(unittest.TestCase):
    """Every command the hook runs has the kit's timeout (<prefix>TIMEOUT
    seconds, 20 by default): a git that answers too slowly is a deny that
    names the timeout, not a wait that Claude Code cuts short."""

    def setUp(self):
        self.fake = Path(tempfile.mkdtemp(prefix="history_guard_slow_"))
        self.repo = make_repo("main")
        write_sleeper(self.fake / "git", 5, "main")

    def tearDown(self):
        shutil.rmtree(self.fake, ignore_errors=True)
        shutil.rmtree(self.repo, ignore_errors=True)

    def test_slow_git_denied_by_name(self):
        env = {"PATH": slow_path(self.fake), PREFIX + "TIMEOUT": "1"}
        decision, reason = run_hook(HOOK, bash("git merge x", "coder", cwd=str(self.repo)),
                                    env=env)
        self.assertEqual(decision, "deny", f"got {decision!r}: {reason}")
        self.assertIn("timed out", reason)
        self.assertIn("failing closed", reason)


def git_in(repo, *args):
    return subprocess.run(["git", "-C", str(repo), "-c", "user.name=t",
                           "-c", "user.email=t@example.invalid"] + list(args),
                          check=True, capture_output=True, text=True).stdout.strip()


def sha256_of(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class MergeRule(unittest.TestCase):
    """The merge rule: a coder's PR merge passes only with a verified,
    fresh backup under backup_dir. Each test gets its own bare origin, a
    clone with origin/main fetched, and an empty backup directory."""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="history_guard_merge_"))
        self.origin = self.root / "origin.git"
        self.work = self.root / "work"
        self.backups = self.root / "backups"
        self.backups.mkdir()
        subprocess.run(["git", "init", "-q", "--bare", str(self.origin)], check=True)
        subprocess.run(["git", "init", "-q", "-b", "main", str(self.work)], check=True)
        git_in(self.work, "commit", "-q", "--allow-empty", "-m", "base")
        git_in(self.work, "remote", "add", "origin", str(self.origin))
        git_in(self.work, "push", "-q", "origin", "main")
        git_in(self.work, "fetch", "-q", "origin")
        self.config = dict(TEST_CONFIG, backup_dir=str(self.backups))

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def write_backup(self, pr, index="full", branch="main", ref=None):
        """A backup as coder.md item 10 describes it, for origin/<branch>
        (main unless said); ref is the ref bundled, origin/<branch> unless
        said. index picks the INDEX.md line: "full" names the folder, #<pr>
        and the SHA; "none" names none of them; "no-pr" and "no-sha" leave
        one out; "folder-in-longer-name" names the folder only as the start
        of a longer folder name."""
        folder = self.backups / f"2026-01-01-pr{pr}"
        folder.mkdir()
        sha = git_in(self.work, "rev-parse", f"origin/{branch}")
        bundle = f"{branch}.bundle"
        git_in(self.work, "bundle", "create", "-q", str(folder / bundle),
               ref or f"origin/{branch}")
        (folder / "merge.json").write_text(json.dumps(
            {"pr": pr, "branch": branch, "sha": sha, "bundle": bundle}))
        (folder / "MANIFEST.sha256").write_text("".join(
            f"{sha256_of(folder / name)}  {name}\n"
            for name in ("merge.json", bundle)))
        line = {"full": f"- {folder.name}: #{pr}, {branch} at {sha}\n",
                "none": "- some other backup\n",
                "no-pr": f"- {folder.name}: {branch} at {sha}\n",
                "no-sha": f"- {folder.name}: #{pr}\n",
                "folder-in-longer-name":
                    f"- {folder.name}2: #{pr}, {branch} at {sha}\n"}[index]
        with open(self.backups / "INDEX.md", "a") as f:
            f.write(line)
        return folder

    def decide(self, command, who="coder", config=None):
        return run_hook(HOOK, bash(command, who, cwd=str(self.work)),
                        config=self.config if config is None else config)

    def assertMergeDenied(self, command, *words, config=None):
        decision, reason = self.decide(command, config=config)
        self.assertEqual(decision, "deny", f"{command!r}: got {decision!r} {reason}")
        for w in words:
            self.assertIn(w, reason, command)

    def test_m1_coder_merge_commit_with_valid_backup_allowed(self):
        self.write_backup(12)
        decision, reason = self.decide("gh pr merge 12 --merge")
        self.assertIsNone(decision, reason)

    def test_m2_coder_squash_with_valid_backup_allowed(self):
        self.write_backup(12)
        decision, reason = self.decide("gh pr merge 12 --squash")
        self.assertIsNone(decision, reason)

    def test_m3_backup_dir_unset_denied(self):
        self.write_backup(12)
        self.assertMergeDenied("gh pr merge 12 --merge", "(c) config", "backup_dir",
                               config=TEST_CONFIG)

    def test_m4_no_backup_for_that_pr_denied(self):
        self.write_backup(7)
        self.assertMergeDenied("gh pr merge 8 --merge", "(d) backup", "merge.json")

    def test_m5_bundle_altered_after_manifest_denied(self):
        folder = self.write_backup(12)
        with open(folder / "main.bundle", "ab") as f:
            f.write(b"x")
        self.assertMergeDenied("gh pr merge 12 --merge", "(d) backup", "MANIFEST.sha256")

    def test_m6_origin_moved_since_backup_denied(self):
        self.write_backup(12)
        git_in(self.work, "commit", "-q", "--allow-empty", "-m", "later")
        git_in(self.work, "push", "-q", "origin", "main")
        git_in(self.work, "fetch", "-q", "origin")
        self.assertMergeDenied("gh pr merge 12 --merge", "(e) freshness")

    def test_m7_index_line_incomplete_denied(self):
        # The INDEX.md line must name the folder, #<N> and the pre-merge SHA.
        for index in ("none", "no-pr", "no-sha"):
            with self.subTest(index):
                shutil.rmtree(self.backups)
                self.backups.mkdir()
                self.write_backup(12, index=index)
                self.assertMergeDenied("gh pr merge 12 --merge", "(d) backup",
                                       "INDEX.md")

    def test_m8_forms_denied_by_name(self):
        self.write_backup(12)
        for command, word in (("gh pr merge 12 --rebase", "--rebase"),
                              ("gh pr merge 12 --merge --auto", "--auto"),
                              ("gh pr merge 12 --merge --admin", "--admin"),
                              ("gh pr merge 12 --merge --delete-branch", "--delete-branch"),
                              ("gh pr merge 12 --merge -R x/y", "-R"),
                              ("gh pr merge --merge", "pull request number"),
                              ("gh pr merge 12 --merge && echo done", "one command")):
            with self.subTest(command):
                self.assertMergeDenied(command, "(b) form", word)

    def test_m9_planner_and_pulse_denied_even_with_a_backup(self):
        self.write_backup(12)
        for who in (None, "pulse"):
            with self.subTest(who):
                decision, _ = self.decide("gh pr merge 12 --merge", who)
                self.assertEqual(decision, "deny")

    def test_m10_origin_moved_on_the_remote_without_a_local_fetch_denied(self):
        # The remote's main moves through another clone; work's origin/main
        # stays at the backup's sha, so only a read of the remote sees it.
        self.write_backup(12)
        other = self.root / "other"
        subprocess.run(["git", "clone", "-q", "-b", "main", str(self.origin), str(other)],
                       check=True, capture_output=True)
        git_in(other, "commit", "-q", "--allow-empty", "-m", "later")
        git_in(other, "push", "-q", "origin", "HEAD:main")
        self.assertMergeDenied("gh pr merge 12 --merge", "(e) freshness", "remote")

    def test_m11_bundle_of_another_ref_at_the_same_sha_denied(self):
        # merge.json names main; the bundle's head is origin/other at the
        # same commit.
        git_in(self.work, "branch", "other", "main")
        git_in(self.work, "push", "-q", "origin", "other")
        git_in(self.work, "fetch", "-q", "origin")
        self.write_backup(12, ref="origin/other")
        self.assertMergeDenied("gh pr merge 12 --merge", "(d) backup",
                               "refs/remotes/origin/other")

    def test_m12_branch_not_protected_denied(self):
        # A self-consistent backup of a branch the config does not protect.
        git_in(self.work, "branch", "feature", "main")
        git_in(self.work, "push", "-q", "origin", "feature")
        git_in(self.work, "fetch", "-q", "origin")
        self.write_backup(12, branch="feature")
        self.assertMergeDenied("gh pr merge 12 --merge", "(d) backup", "protected")

    def test_m13_index_names_folder_only_inside_a_longer_name_denied(self):
        # Folder 2026-01-01-pr1; the line names 2026-01-01-pr12, #1 and the sha.
        self.write_backup(1, index="folder-in-longer-name")
        self.assertMergeDenied("gh pr merge 1 --merge", "(d) backup", "INDEX.md")

    def test_m14_remote_unreadable_denied(self):
        self.write_backup(12)
        git_in(self.work, "remote", "set-url", "origin", str(self.root / "missing.git"))
        self.assertMergeDenied("gh pr merge 12 --merge", "(e) freshness",
                               "could not be read")

    def test_m15_remote_denial_never_echoes_credentials(self):
        self.write_backup(15)
        git_in(self.work, "remote", "set-url", "origin",
               "https://user:s3cr3t@example.invalid/x.git")
        decision, reason = self.decide("gh pr merge 15 --merge")
        self.assertEqual(decision, "deny", f"got {decision!r}: {reason}")
        self.assertIn("(e) freshness", reason)
        self.assertIn("could not be read", reason)
        self.assertNotIn("s3cr3t", reason)


if __name__ == "__main__":
    unittest.main()
