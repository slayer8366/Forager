"""The guards' known bypasses, .claude/hooks/BYPASSES.md.

An `accepted` row's test asserts that the bypass still gets through: the
hook gives no decision for the row's inputs. A fix that closes a bypass makes
that test fail, and the table is updated with it. A `fixed` row's test
asserts the block instead: each input gets a deny naming the reason. Every
input is sent to the hook as a payload; nothing is pushed, merged or run on
a device.

test_table_docstrings_and_tests_agree checks the table against the guards'
docstrings and this file: each guard's docstring has a "Known bypasses"
line, every B-NN a hook docstring cites is a row, and every row's Test is a
test in this class.
"""
import ast
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from harness import bash, run_hook, tool

HOOKS = Path(__file__).resolve().parent.parent
TABLE = HOOKS / "BYPASSES.md"
GUARDS = ("role_guard.py", "dispatch_guard.py", "device_guard.py",
          "history_guard.py", "session_check.py")
CITATION = re.compile(r"(?m)^Known bypasses: \.claude/hooks/BYPASSES\.md\b(.*)$")
ROW = re.compile(r"^\|\s*(B-\d{2})\s*\|(.*)\|\s*$")


def make_repo(branch):
    repo = Path(tempfile.mkdtemp(prefix="bypass_test_"))
    g = ["git", "-C", str(repo), "-c", "user.name=t", "-c", "user.email=t@example.invalid"]
    subprocess.run(["git", "init", "-q", "-b", "main", str(repo)], check=True)
    subprocess.run(g + ["commit", "-q", "--allow-empty", "-m", "base"], check=True)
    if branch != "main":
        subprocess.run(g + ["checkout", "-q", "-b", branch], check=True)
    return repo


def table_rows():
    """{ID: [cells after ID]} for the table's rows."""
    rows = {}
    for line in TABLE.read_text().splitlines():
        m = ROW.match(line)
        if m:
            rows[m.group(1)] = [c.strip() for c in m.group(2).split("|")]
    return rows


class Bypasses(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.on_main = make_repo("main")
        cls.on_feature = make_repo("feature")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.on_main, ignore_errors=True)
        shutil.rmtree(cls.on_feature, ignore_errors=True)

    def assertGetsThrough(self, hook, payload):
        decision, reason = run_hook(hook, payload)
        self.assertIsNone(decision, f"{hook} now decides {decision!r} ({reason}); "
                                    f"if this closes the bypass, update BYPASSES.md")

    def assertBlocked(self, hook, payload, words):
        decision, reason = run_hook(hook, payload)
        self.assertEqual(decision, "deny", f"{hook} gives {decision!r} ({reason}); "
                                           f"this row is fixed, so it must deny")
        self.assertIn(words, reason)

    def test_b01_push_through_wrapper_or_git_path(self):
        # R2 moved the `env` and `/usr/bin/git` forms to B-10 (fixed) and R3
        # the `sh -c` form to B-13 (fixed); the interpreter form stays accepted.
        command = ("python3 -c \"import subprocess; "
                   "subprocess.run(['git', 'push', 'origin', 'main'])\"")
        self.assertGetsThrough("history_guard.py",
                               bash(command, "coder", cwd=str(self.on_feature)))

    def test_b10_push_behind_cd_keyword_wrapper_assignment_or_git_path_is_blocked(self):
        for command in (f"cd {self.on_main} && git push origin",
                        f"pushd {self.on_main} && git push origin && popd",
                        "{ git push origin main; }",
                        "time git push origin main",
                        "GIT_TRACE=1 git push origin main",
                        "/usr/bin/git push origin main"):
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py",
                                   bash(command, "coder", cwd=str(self.on_feature)),
                                   "protected branch")

    def test_b11_plus_refspec_is_blocked_as_force(self):
        for command in ("git push origin +main", "git push origin +feature"):
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py",
                                   bash(command, "coder", cwd=str(self.on_feature)),
                                   "force")

    def test_b12_merge_behind_git_dir_or_work_tree_is_blocked(self):
        for command in ("git --git-dir=.git merge x", "git --work-tree=. merge x"):
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py",
                                   bash(command, "coder", cwd=str(self.on_main)),
                                   "while on main")

    def test_b13_push_or_merge_inside_shell_string_or_eval_is_blocked(self):
        for command, cwd, words in (
                ("sh -c 'git push origin main'", self.on_feature, "protected branch"),
                (f'bash -lc "cd {self.on_main} && git push origin"', self.on_feature,
                 "protected branch"),
                ("eval 'git push origin main'", self.on_feature, "protected branch"),
                ("sh -c 'git merge feature'", self.on_main, "while on main")):
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py", bash(command, "coder", cwd=str(cwd)),
                                   words)

    def test_b14_graphql_merge_and_alias_set_are_blocked(self):
        graphql = ("gh api graphql -f query='mutation { mergePullRequest(input: "
                   "{pullRequestId: \"x\"}) { clientMutationId } }'")
        for command, words in ((graphql, "mergePullRequest"),
                               ("gh alias set m 'pr merge'", "gh alias set"),
                               ("gh alias import aliases.yml", "gh alias import")):
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py",
                                   bash(command, "coder", cwd=str(self.on_feature)), words)

    def test_b15_quoted_or_split_subcommand_words_are_blocked(self):
        for command in ('gh "pr" merge 5 -m', 'gh pr mer""ge 5 -m'):
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py",
                                   bash(command, "coder", cwd=str(self.on_feature)),
                                   "denied")

    def test_b02_interpreter_splits_words(self):
        cases = [
            ("python3 -c \"import subprocess; "
             "subprocess.run(['git', 'push', '--force', 'origin', 'feature'])\"",
             self.on_feature),
            ("python3 -c \"import subprocess; "
             "subprocess.run(['gh', 'pr', 'merge', '5', '--merge'])\"",
             self.on_feature),
            ("python3 -c \"import subprocess; "
             "subprocess.run(['git', 'merge', 'feature'])\"",
             self.on_main),
        ]
        for command, repo in cases:
            with self.subTest(command=command):
                self.assertGetsThrough("history_guard.py",
                                       bash(command, "coder", cwd=str(repo)))

    def test_b03_pr_merge_through_gh_api_is_blocked(self):
        first = "gh api -X PUT repos/o/r/pulls/5/merge -f merge_method=merge"
        cases = [(first, "coder"), (first, "pulse"), (first, None)]
        for command in ("gh api --method PUT /repos/o/r/pulls/5/merge",
                        "gh api repos/o/r/pulls/5/merge -F merge_method=squash",
                        "gh api repos/o/r/pulls/5/merge --field merge_method=merge",
                        "gh api repos/o/r/pulls/5/merge --raw-field merge_method=merge",
                        "gh api repos/o/r/pulls/5/merge --input body.json"):
            cases.append((command, "coder"))
        for command, who in cases:
            with self.subTest(command=command, role=who or "planner"):
                self.assertBlocked("history_guard.py",
                                   bash(command, who, cwd=str(self.on_feature)),
                                   "gh pr merge")

    def test_b03_get_of_merge_endpoint_passes(self):
        for command in ("gh api repos/o/r/pulls/5/merge",
                        "gh api /repos/o/r/pulls/5/merge",
                        "gh api -X GET repos/o/r/pulls/5/merge",
                        "gh api --method GET repos/o/r/pulls/5/merge"):
            with self.subTest(command=command):
                self.assertGetsThrough("history_guard.py",
                                       bash(command, "coder", cwd=str(self.on_feature)))

    def test_b04_refspec_in_shell_variable(self):
        for command in ("B=main; git push origin $B",
                        "B=main && git push origin \"${B}\""):
            with self.subTest(command=command):
                self.assertGetsThrough("history_guard.py",
                                       bash(command, "coder", cwd=str(self.on_feature)))

    def test_b05_heredoc_marker_in_quotes_or_comment_is_blocked(self):
        for first in ("echo '<<EOF'", "echo \"<<EOF\"", "true # <<EOF"):
            command = first + "\ngit push origin main\nEOF"
            with self.subTest(command=command):
                self.assertBlocked("history_guard.py",
                                   bash(command, "coder", cwd=str(self.on_feature)),
                                   "protected branch")

    def test_b05_real_heredoc_body_still_dropped(self):
        # T12: a heredoc body is data, so a push inside one is not checked.
        for command in ("cat <<EOF\ngit push origin main\nEOF",
                        "cat <<-EOF\n\tgit push origin main\n\tEOF",
                        "cat <<'EOF'\ngit push origin main\nEOF",
                        "cat <<\"EOF\"\ngit push origin main\nEOF"):
            with self.subTest(command=command):
                self.assertGetsThrough("history_guard.py",
                                       bash(command, "coder", cwd=str(self.on_feature)))

    def test_b05_here_string_is_not_a_heredoc_marker(self):
        # `<<<EOF` is a here-string, not a heredoc: the lines after it are
        # commands, so the push to main among them is checked.
        command = "cat <<<EOF\ngit push origin main\nEOF"
        self.assertBlocked("history_guard.py",
                           bash(command, "coder", cwd=str(self.on_feature)),
                           "protected branch")

    def test_b06_adb_through_variable_quoting_or_alias(self):
        for command in ("A=adb; $A uninstall com.example.kittest",
                        "ad''b uninstall com.example.kittest",
                        "alias a=adb\ntrue; a uninstall com.example.kittest"):
            with self.subTest(command=command):
                self.assertGetsThrough("device_guard.py", bash(command, "coder"))

    def test_b07_send_message_skips_dispatch_checks(self):
        payload = tool("SendMessage", None, {
            "to": "a0001", "summary": "new work",
            "message": "**Type:** build\n\nAlso change the hooks."})
        self.assertGetsThrough("dispatch_guard.py", payload)

    def test_b08_pulse_and_planner_read_outside_checkout(self):
        cwd = str(self.on_feature)
        cases = [
            ("pulse Read", tool("Read", "pulse", {"file_path": "/etc/hostname"}, cwd=cwd)),
            ("planner Read", tool("Read", None, {"file_path": "/etc/hostname"}, cwd=cwd)),
            ("pulse Grep", tool("Grep", "pulse", {"pattern": "a", "path": "/etc/hostname"},
                                cwd=cwd)),
            ("pulse Glob", tool("Glob", "pulse", {"pattern": "*", "path": "/etc"}, cwd=cwd)),
        ]
        for name, payload in cases:
            with self.subTest(case=name):
                self.assertGetsThrough("role_guard.py", payload)

    def test_b09_pr_merge_through_curl(self):
        # Payloads only: nothing is sent to GitHub.
        url = "https://api.github.com/repos/o/r/pulls/5/merge"
        auth = "-H \"Authorization: Bearer x\""
        for command in (f"curl -X PUT {auth} {url}",
                        f"curl -X PUT {auth} -d '{{\"merge_method\":\"merge\"}}' {url}"):
            for hook in ("history_guard.py", "role_guard.py"):
                with self.subTest(command=command, hook=hook):
                    self.assertGetsThrough(hook,
                                           bash(command, "coder", cwd=str(self.on_feature)))

    def test_table_docstrings_and_tests_agree(self):
        self.assertTrue(TABLE.is_file(), f"{TABLE} does not exist")
        rows = table_rows()
        self.assertTrue(rows, f"{TABLE} has no B-NN rows")
        cited = {}
        for path in sorted(HOOKS.glob("*.py")):
            doc = ast.get_docstring(ast.parse(path.read_text())) or ""
            if path.name in GUARDS:
                self.assertRegex(doc, CITATION, f"{path.name}'s docstring has no "
                                                f"'Known bypasses: .claude/hooks/BYPASSES.md' line")
            for ident in re.findall(r"\bB-\d{2}\b", doc):
                cited.setdefault(ident, set()).add(path.name)
        for ident, where in sorted(cited.items()):
            with self.subTest(cited=ident):
                self.assertIn(ident, rows, f"{', '.join(sorted(where))} cites {ident}, "
                                           f"which is not a row of {TABLE.name}")
        for ident, cells in sorted(rows.items()):
            with self.subTest(row=ident):
                names = re.findall(r"\btest_\w+", cells[-1]) if cells else []
                self.assertEqual(len(names), 1, f"{ident}'s Test cell {cells[-1:]} "
                                                f"does not name one test")
                self.assertTrue(callable(getattr(type(self), names[0], None)),
                                f"{ident} names {names[0]}, which is not a test in "
                                f"{Path(__file__).name}")


if __name__ == "__main__":
    unittest.main()
