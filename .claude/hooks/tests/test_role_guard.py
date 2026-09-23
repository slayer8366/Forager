"""role_guard.py: decision A (planner read-only) and the pulse role's
read-only Bash. Run: python3 -m unittest discover -s .claude/hooks/tests"""
import unittest

from harness import bash, run_hook, tool

HOOK = "role_guard.py"


class PlannerTools(unittest.TestCase):
    def assertDenied(self, payload, *words):
        decision, reason = run_hook(HOOK, payload)
        self.assertEqual(decision, "deny", f"expected deny, got {decision!r}")
        for w in words:
            self.assertIn(w, reason)
        return reason

    def test_write_edit_notebook_denied(self):
        for name in ("Write", "Edit", "NotebookEdit"):
            with self.subTest(name):
                self.assertDenied(tool(name), name, "planner")

    def test_mcp_tools_denied(self):
        self.assertDenied(tool("mcp__claude_ai_Resend__send-email"),
                          "mcp__claude_ai_Resend__send-email")
        self.assertDenied(tool("mcp__claude_ai_Resend__list-domains"),
                          "mcp__claude_ai_Resend__list-domains")

    def test_unknown_tool_denied_by_default(self):
        self.assertDenied(tool("SomeFutureTool"), "SomeFutureTool", "allowlist")

    def test_allowlisted_tools_pass(self):
        for name in ("Read", "Grep", "Glob", "Agent", "Skill", "WebFetch",
                     "WebSearch", "AskUserQuestion", "ToolSearch", "TodoWrite"):
            with self.subTest(name):
                decision, reason = run_hook(HOOK, tool(name))
                self.assertIsNone(decision, f"{name}: {decision} {reason}")

    def test_malformed_payload_fails_closed(self):
        decision, reason = run_hook(HOOK, "not json")
        self.assertEqual(decision, "deny")
        self.assertIn("failing closed", reason)


class PlannerBash(unittest.TestCase):
    def assertDenied(self, command, *words):
        decision, reason = run_hook(HOOK, bash(command))
        self.assertEqual(decision, "deny",
                         f"{command!r}: expected deny, got {decision!r}")
        for w in words:
            self.assertIn(w, reason, f"{command!r}")

    def assertPasses(self, command):
        decision, reason = run_hook(HOOK, bash(command))
        self.assertIsNone(decision, f"{command!r}: {decision} {reason}")

    # The patterns step 2 names, each with its own message. The expected
    # text is the named layer's own wording, not just the command's name:
    # the allowlist behind it also denies most of these and also quotes the
    # command, so asserting on the name alone passed with a named pattern
    # removed (sabotage run, 2026-09-22).
    def test_named_patterns(self):
        named = "changes the repository or build"
        cases = {
            "git commit -m x": f"`git commit` {named}",
            "git push origin feature": f"`git push` {named}",
            "git checkout main": f"`git checkout` {named}",
            "git reset --hard HEAD~1": f"`git reset` {named}",
            "git merge feature": f"`git merge` {named}",
            "rm -rf build": f"`rm` {named}",
            "mv a b": f"`mv` {named}",
            "echo hi > notes.txt": "redirection (",
            "git log >> log.txt": "redirection (",
            "sed -i s/a/b/ file.kt": f"`sed -i` {named}",
            "./gradlew test": f"`gradle` {named}",
            "gradle build": f"`gradle` {named}",
            "adb shell getprop ro.build.version.release": "dispatch a pulse",
        }
        for command, word in cases.items():
            with self.subTest(command):
                self.assertDenied(command, word)

    def test_read_only_git_and_gh_pass(self):
        for command in ("git log --oneline -5", "git status",
                        "git -C /tmp/x show HEAD:README.md", "git diff main...HEAD",
                        "git rev-parse HEAD", "git ls-remote origin",
                        "git blame app/build.gradle.kts", "git branch -a",
                        "gh pr view 104", "gh pr list --state open",
                        "gh api repos/slayer8366/Forager/pulls"):
            with self.subTest(command):
                self.assertPasses(command)

    def test_outside_the_allowlist_denied(self):
        for command in ("git fetch origin", "cat README.md", "ls",
                        "git -c core.pager=sh log", "git diff --output=x.patch",
                        "git branch new-branch", "gh api -X POST repos/a/b/issues",
                        "gh api repos/a/b/issues -f title=x", "gh pr merge 5",
                        "git log | head", "git log; rm x", "git log $(rm x)",
                        "python3 check_record.py"):
            with self.subTest(command):
                self.assertDenied(command)

    def test_unbalanced_quotes_denied(self):
        self.assertDenied("git log --format='%h", "parse")


class OtherRoles(unittest.TestCase):
    def test_coder_is_not_restricted_by_this_guard(self):
        for payload in (tool("Write", "coder"), bash("rm -rf build", "coder"),
                        bash("git commit -m x", "coder")):
            decision, reason = run_hook(HOOK, payload)
            self.assertIsNone(decision, reason)

    def test_pulse_device_reads_pass(self):
        for command in ("adb shell getprop ro.build.version.release",
                        "adb -s R5CT10 shell dumpsys package com.zynergylabs.forager.app",
                        "adb devices", "adb exec-out screencap -p",
                        "adb shell screencap /sdcard/p.png", "git log -3"):
            with self.subTest(command):
                decision, reason = run_hook(HOOK, bash(command, "pulse"))
                self.assertIsNone(decision, f"{command!r}: {reason}")

    def test_pulse_writes_and_device_input_denied(self):
        for command in ("adb shell input tap 10 10", "adb install app.apk",
                        "adb shell dumpsys battery set level 5",
                        "adb shell 'getprop; rm -rf /sdcard/x'",
                        # Only the remote-punctuation check stops this one: the
                        # case above is also caught by the rm pattern, and in
                        # 'getprop; reboot' the first word 'getprop;' already
                        # fails the read list (both found by sabotage runs).
                        "adb shell 'getprop ro.x; reboot'",
                        "adb pull /sdcard/p.png", "git commit -m x",
                        "adb exec-out screencap -p > shot.png"):
            with self.subTest(command):
                decision, reason = run_hook(HOOK, bash(command, "pulse"))
                self.assertEqual(decision, "deny", f"{command!r}: {decision}")
                self.assertIn("pulse", reason)

    def test_pulse_tools_limited(self):
        for name in ("Write", "Edit", "mcp__claude_ai_Resend__list-domains"):
            with self.subTest(name):
                decision, reason = run_hook(HOOK, tool(name, "pulse"))
                self.assertEqual(decision, "deny")
                self.assertIn("pulse", reason)


if __name__ == "__main__":
    unittest.main()
