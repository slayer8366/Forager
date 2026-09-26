"""role_guard.py: the planner is read-only, and so is the pulse role's
read-only Bash. Run: python3 -m unittest discover -s .claude/hooks/tests"""
import json
import tempfile
import unittest
from pathlib import Path

from harness import TEST_CONFIG, bash, run_hook, tool

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
        self.assertDenied(tool("mcp__example__send"),
                          "mcp__example__send")
        self.assertDenied(tool("mcp__example__list"),
                          "mcp__example__list")

    def test_unknown_tool_denied_by_default(self):
        self.assertDenied(tool("SomeFutureTool"), "SomeFutureTool", "allowlist")

    def test_allowlisted_tools_pass(self):
        for name in ("Read", "Agent", "Skill", "WebFetch",
                     "WebSearch", "AskUserQuestion", "ToolSearch", "TodoWrite"):
            with self.subTest(name):
                decision, reason = run_hook(HOOK, tool(name))
                self.assertIsNone(decision, f"{name}: {decision} {reason}")

    def test_grep_and_glob_not_on_the_planner_allowlist(self):
        # Neither exists as a tool on Claude Code 2.1.280; the design listed
        # them in error (Claude-kit v0.1 addition).
        for name in ("Grep", "Glob"):
            with self.subTest(name):
                self.assertDenied(tool(name), name, "planner")

    def test_planner_may_not_hand_back(self):
        # The hand-back tool is the pulse's only, not the planner's.
        self.assertDenied(tool("SubagentHandback"), "SubagentHandback", "planner")

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

    # The named patterns, each with its own message. The expected
    # text is the named layer's own wording, not just the command's name:
    # the allowlist behind it also denies most of these and also quotes the
    # command, so asserting on the name alone passed with a named pattern
    # removed (found by a sabotage run).
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
                        "gh api repos/example/project/pulls"):
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

    def test_guarded_words_in_arguments_pass(self):
        # R3: the named check reads each segment's command word, not the text.
        for command in ('git grep -n "sed -i" -- x', 'git log --grep "rm "',
                        "gh pr view 5 --json title"):
            with self.subTest(command):
                self.assertPasses(command)


class PlannerSendMessage(unittest.TestCase):
    """The planner may SendMessage only to an agent this session started: an
    ID that toolUseResult.agentId gives on the transcript line holding the
    result of one of the session's own Agent calls (Claude-kit v0.2 T9)."""

    RULE = "may SendMessage only to an agent this session started"
    AGENT_ID = "a1e0888e2666dcce1"

    def setUp(self):
        tmp = tempfile.TemporaryDirectory(prefix="kit_transcript_")
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)

    # Lines shaped like the session's JSONL log: an assistant line with the
    # tool_use, then a user line with its one tool_result and, at the top
    # level, the tool's structured result.
    @staticmethod
    def call(tool_id, name):
        return json.dumps({"type": "assistant", "message": {"role": "assistant", "content": [
            {"type": "tool_use", "id": tool_id, "name": name, "input": {}}]}})

    @staticmethod
    def result(tool_id, text, tool_use_result):
        return json.dumps({"type": "user", "message": {"role": "user", "content": [
            {"type": "tool_result", "tool_use_id": tool_id,
             "content": [{"type": "text", "text": text}]}]},
            "toolUseResult": tool_use_result})

    def agent_lines(self, agent_id=None):
        agent_id = agent_id or self.AGENT_ID
        return [self.call("toolu_agent1", "Agent"),
                self.result("toolu_agent1",
                            f"Async agent launched successfully.\nagentId: {agent_id}",
                            {"isAsync": True, "status": "async_launched",
                             "agentId": agent_id})]

    def transcript(self, lines):
        path = self.dir / "session.jsonl"
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return str(path)

    def send(self, to, transcript_path, agent_type=None):
        tool_input = {"message": "status?"}
        if to is not None:
            tool_input["to"] = to
        p = tool("SendMessage", agent_type, tool_input)
        if transcript_path is not None:
            p["transcript_path"] = transcript_path
        return run_hook(HOOK, p)

    def assertRuleDenied(self, result, target=None):
        decision, reason = result
        self.assertEqual(decision, "deny", f"expected deny, got {decision!r}")
        self.assertIn(self.RULE, reason)
        if target is not None:
            self.assertIn(target, reason)

    # t1
    def test_an_agent_the_session_started_is_allowed(self):
        path = self.transcript(self.agent_lines())
        decision, reason = self.send(self.AGENT_ID, path)
        self.assertIsNone(decision, reason)

    # t2
    def test_an_id_not_in_the_transcript_is_denied(self):
        path = self.transcript(self.agent_lines())
        self.assertRuleDenied(self.send("a0000000000000000", path), "a0000000000000000")

    # t3
    def test_an_id_only_in_a_read_result_is_denied(self):
        other = "a2222222222222222"
        lines = self.agent_lines() + [
            self.call("toolu_read1", "Read"),
            self.result("toolu_read1", f"agentId: {other}",
                        {"type": "text", "file": {"filePath": "/tmp/notes.md",
                                                  "content": f"agentId: {other}"}})]
        self.assertRuleDenied(self.send(other, self.transcript(lines)), other)

    # t4
    def test_main_a_name_and_a_cross_session_target_are_denied(self):
        path = self.transcript(self.agent_lines())
        for target in ("main", "coder", "worker [3fa9c1]"):
            with self.subTest(target):
                self.assertRuleDenied(self.send(target, path), target)

    # t5
    def test_a_missing_or_unreadable_transcript_is_denied(self):
        cases = {"missing file": str(self.dir / "no-such.jsonl"),
                 "a directory": str(self.dir),
                 "transcript_path absent": None}
        for label, path in cases.items():
            with self.subTest(label):
                self.assertRuleDenied(self.send(self.AGENT_ID, path), self.AGENT_ID)

    # t6
    def test_the_pulse_may_not_send_messages(self):
        path = self.transcript(self.agent_lines())
        decision, reason = self.send(self.AGENT_ID, path, agent_type="pulse")
        self.assertEqual(decision, "deny", reason)
        self.assertIn("the pulse role may not use SendMessage", reason)

    # t7
    def test_a_malformed_line_before_the_agent_result_is_skipped(self):
        lines = self.agent_lines()
        lines.insert(1, '{"type": "user", "message": {not json')
        decision, reason = self.send(self.AGENT_ID, self.transcript(lines))
        self.assertIsNone(decision, reason)

    # t8
    def test_a_missing_or_empty_target_is_denied(self):
        path = self.transcript(self.agent_lines())
        for label, to in (("absent", None), ("empty", ""), ("blank", "   ")):
            with self.subTest(label):
                self.assertRuleDenied(self.send(to, path))


class PlannerTaskStop(unittest.TestCase):
    """The planner may TaskStop only an agent this session started: the same
    target rule as SendMessage, applied to task_id; shell_id is always denied
    (Claude-kit v0.2 T11)."""

    RULE = "may TaskStop only an agent this session started"
    AGENT_ID = "a02810510ac421daf"

    def setUp(self):
        tmp = tempfile.TemporaryDirectory(prefix="kit_transcript_")
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)

    def agent_lines(self):
        return [PlannerSendMessage.call("toolu_agent1", "Agent"),
                PlannerSendMessage.result(
                    "toolu_agent1",
                    f"Async agent launched successfully.\nagentId: {self.AGENT_ID}",
                    {"isAsync": True, "status": "async_launched",
                     "agentId": self.AGENT_ID})]

    def transcript(self, lines):
        path = self.dir / "session.jsonl"
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return str(path)

    def stop(self, tool_input, transcript_path, agent_type=None):
        p = tool("TaskStop", agent_type, tool_input)
        if transcript_path is not None:
            p["transcript_path"] = transcript_path
        return run_hook(HOOK, p)

    def assertRuleDenied(self, result, target=None):
        decision, reason = result
        self.assertEqual(decision, "deny", f"expected deny, got {decision!r}")
        self.assertIn(self.RULE, reason)
        if target is not None:
            self.assertIn(target, reason)

    # s1
    def test_an_agent_the_session_started_is_allowed(self):
        path = self.transcript(self.agent_lines())
        decision, reason = self.stop({"task_id": self.AGENT_ID}, path)
        self.assertIsNone(decision, reason)

    # s2
    def test_an_unknown_id_is_denied(self):
        path = self.transcript(self.agent_lines())
        self.assertRuleDenied(self.stop({"task_id": "a0000000000000000"}, path),
                              "a0000000000000000")

    # s3
    def test_an_id_only_in_a_read_result_is_denied(self):
        other = "a2222222222222222"
        lines = self.agent_lines() + [
            PlannerSendMessage.call("toolu_read1", "Read"),
            PlannerSendMessage.result(
                "toolu_read1", f"agentId: {other}",
                {"type": "text", "file": {"filePath": "/tmp/notes.md",
                                          "content": f"agentId: {other}"}})]
        self.assertRuleDenied(self.stop({"task_id": other}, self.transcript(lines)),
                              other)

    # s4
    def test_a_missing_empty_or_blank_task_id_is_denied(self):
        path = self.transcript(self.agent_lines())
        for label, tool_input in (("absent", {}), ("empty", {"task_id": ""}),
                                  ("blank", {"task_id": "   "})):
            with self.subTest(label):
                self.assertRuleDenied(self.stop(tool_input, path))

    # s5
    def test_a_shell_id_is_always_denied(self):
        path = self.transcript(self.agent_lines())
        for label, tool_input in (
                ("with a valid task_id", {"task_id": self.AGENT_ID, "shell_id": "x"}),
                ("without task_id", {"shell_id": "x"})):
            with self.subTest(label):
                self.assertRuleDenied(self.stop(tool_input, path), "shell_id")

    # s6
    def test_a_missing_or_unreadable_transcript_is_denied(self):
        cases = {"missing file": str(self.dir / "no-such.jsonl"),
                 "a directory": str(self.dir),
                 "transcript_path absent": None}
        for label, path in cases.items():
            with self.subTest(label):
                self.assertRuleDenied(self.stop({"task_id": self.AGENT_ID}, path),
                                      self.AGENT_ID)

    # s7
    def test_the_pulse_may_not_stop_tasks(self):
        path = self.transcript(self.agent_lines())
        decision, reason = self.stop({"task_id": self.AGENT_ID}, path,
                                     agent_type="pulse")
        self.assertEqual(decision, "deny", reason)
        self.assertIn("the pulse role may not use TaskStop", reason)


class OtherRoles(unittest.TestCase):
    def test_coder_is_not_restricted_by_this_guard(self):
        for payload in (tool("Write", "coder"), bash("rm -rf build", "coder"),
                        bash("git commit -m x", "coder")):
            decision, reason = run_hook(HOOK, payload)
            self.assertIsNone(decision, reason)

    def test_pulse_device_reads_pass(self):
        for command in ("adb shell getprop ro.build.version.release",
                        "adb -s TESTSERIAL01 shell dumpsys package com.example.kittest",
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

    def test_pulse_may_hand_back(self):
        # SubagentHandback delivers a subagent's report to its caller;
        # without it a pulse runs and delivers nothing.
        decision, reason = run_hook(HOOK, tool("SubagentHandback", "pulse",
                                               {"message": "report"}))
        self.assertIsNone(decision, reason)

    def test_pulse_tools_limited(self):
        for name in ("Write", "Edit", "mcp__example__list"):
            with self.subTest(name):
                decision, reason = run_hook(HOOK, tool(name, "pulse"))
                self.assertEqual(decision, "deny")
                self.assertIn("pulse", reason)


class Roles(unittest.TestCase):
    """Restrictions come from the role agent_roles gives an agent, not from
    the agent's name. An agent with no role, or with a role the kit does not
    define, may use no tool at all."""

    def roles(self, **extra):
        return dict(TEST_CONFIG, agent_roles=dict(TEST_CONFIG["agent_roles"], **extra))

    def test_an_agent_mapped_to_pulse_gets_the_pulse_restrictions(self):
        config = self.roles(reader="pulse")
        decision, reason = run_hook(HOOK, tool("Write", "reader"), config=config)
        self.assertEqual(decision, "deny", reason)
        self.assertIn("role_guard: the pulse role may not use Write", reason)
        decision, reason = run_hook(HOOK, bash("git commit -m x", "reader"), config=config)
        self.assertEqual(decision, "deny", reason)
        self.assertIn("role_guard: the pulse role's Bash is read-only", reason)
        decision, reason = run_hook(HOOK, tool("Read", "reader"), config=config)
        self.assertIsNone(decision, reason)

    def test_an_agent_mapped_to_planner_gets_the_planner_restrictions(self):
        config = self.roles(scribe="planner")
        decision, reason = run_hook(HOOK, tool("Write", "scribe"), config=config)
        self.assertEqual(decision, "deny", reason)
        self.assertIn("role_guard: the planner role may not use Write", reason)

    def test_restrictions_follow_the_role_not_the_name(self):
        config = dict(TEST_CONFIG, agent_roles={"coder": "coder", "pulse": "coder"})
        decision, reason = run_hook(HOOK, tool("Write", "pulse"), config=config)
        self.assertIsNone(decision, reason)

    def test_an_agent_with_no_role_is_denied_everything(self):
        for name in ("Read", "Bash", "Write"):
            with self.subTest(name):
                payload = (bash("git status", "general-purpose") if name == "Bash"
                           else tool(name, "general-purpose"))
                decision, reason = run_hook(HOOK, payload)
                self.assertEqual(decision, "deny", reason)
                self.assertIn("role_guard: agent 'general-purpose' has no role in "
                              "agent_roles", reason)

    def test_an_agent_with_an_unknown_role_is_denied_everything(self):
        config = self.roles(reader="nosuch")
        for name in ("Read", "Write"):
            with self.subTest(name):
                decision, reason = run_hook(HOOK, tool(name, "reader"), config=config)
                self.assertEqual(decision, "deny", reason)
                self.assertIn("role_guard: agent 'reader' has role 'nosuch'", reason)
                self.assertIn("not one of the kit's roles", reason)

    def test_the_main_session_is_always_the_planner(self):
        config = self.roles(planner="coder")
        decision, reason = run_hook(HOOK, tool("Write"), config=config)
        self.assertEqual(decision, "deny", reason)
        self.assertIn("role_guard: the planner role may not use Write", reason)


if __name__ == "__main__":
    unittest.main()
