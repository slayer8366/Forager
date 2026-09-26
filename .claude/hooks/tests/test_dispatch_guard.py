"""dispatch_guard.py. Crafted Agent payloads against a throwaway
git repository holding the real checkers."""
import datetime
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from harness import HOOKS, TEST_CONFIG, run_hook, slow_path, write_sleeper

HOOK = "dispatch_guard.py"
PREFIX = TEST_CONFIG["guard_env_prefix"]
REPO_ROOT = HOOKS.parent.parent

BUILD_SECTIONS = ["Role", "Base and state", "Scope boundary",
                  "Closed decisions (operator)", "Prediction",
                  "Finish line and abort conditions", "Checks", "Out of scope",
                  "Device items"]
PULSE_SECTIONS = ["Role", "Base and state", "Rules", "Questions"]


def prompt(type_, sections):
    body = [f"# Dispatch: test\n\n**Type:** {type_}\n"] if type_ else ["# Dispatch: test\n"]
    body += [f"## {s}\n\nText for {s}.\n" for s in sections]
    return "\n".join(body)


def agent(prompt_text, subagent="coder", tool_name="Agent", cwd=None):
    return {"hook_event_name": "PreToolUse", "tool_name": tool_name, "cwd": cwd,
            "tool_input": {"description": "t", "prompt": prompt_text,
                           "subagent_type": subagent}}


class DispatchGuard(unittest.TestCase):
    def setUp(self):
        self.repo = Path(tempfile.mkdtemp(prefix="dispatch_guard_test_"))
        git = ["git", "-C", str(self.repo), "-c", "user.name=t",
               "-c", "user.email=t@example.invalid"]
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        for name in ("check_record.py", "check_prompts.py"):
            shutil.copy(REPO_ROOT / name, self.repo / name)
        (self.repo / "RECORD.md").write_text("# RECORD.md\n\n## Entries\n\n---\n")
        subprocess.run(git + ["add", "."], check=True)
        subprocess.run(git + ["commit", "-q", "-m", "base"], check=True)
        self.head = subprocess.run(["git", "-C", str(self.repo), "rev-parse", "HEAD"],
                                   capture_output=True, text=True).stdout.strip()
        self.today = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
        self.store = self.repo / "prompts" / "preserved"

    def tearDown(self):
        shutil.rmtree(self.repo, ignore_errors=True)

    def written(self):
        return sorted(p.name for p in self.store.glob("*.md")) if self.store.exists() else []

    def test_missing_type_blocks(self):
        decision, reason = run_hook(HOOK, agent(prompt(None, BUILD_SECTIONS), cwd=str(self.repo)))
        self.assertEqual(decision, "deny")
        self.assertIn("has no `Type:` line", reason)
        self.assertEqual(self.written(), [])

    def test_unknown_type_blocks(self):
        decision, reason = run_hook(HOOK, agent(prompt("audit", BUILD_SECTIONS), cwd=str(self.repo)))
        self.assertEqual(decision, "deny")
        # The hook's own wording: a fail-closed KeyError would also contain 'audit'.
        self.assertIn("unknown Type 'audit'", reason)
        self.assertEqual(self.written(), [])

    def test_build_missing_one_section_names_it(self):
        decision, reason = run_hook(HOOK, agent(prompt("build", BUILD_SECTIONS[:-1]),
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "deny")
        self.assertIn("missing 1 required section(s): Device items.", reason)
        self.assertEqual(self.written(), [])

    def test_pulse_missing_sections_names_each(self):
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS[:2]),
                                                "pulse", cwd=str(self.repo)))
        self.assertEqual(decision, "deny")
        self.assertIn("Rules", reason)
        self.assertIn("Questions", reason)

    def test_complete_pulse_allowed_and_preserved_verbatim(self):
        text = prompt("pulse", PULSE_SECTIONS)
        decision, reason = run_hook(HOOK, agent(text, "pulse", cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        self.assertEqual(self.written(), [f"{self.today}-01.md"])
        saved = (self.store / f"{self.today}-01.md").read_text()
        header, sep, body = saved.partition("\n--- verbatim prompt follows ---\n")
        self.assertTrue(sep, f"no verbatim delimiter in: {saved[:300]!r}")
        self.assertEqual(body, text)
        self.assertIn(f"HEAD: {self.head}", header)
        self.assertIn("Target subagent: pulse", header)
        self.assertIn("Type: pulse", header)

    def test_complete_build_asks_and_gets_next_sequence(self):
        self.store.mkdir(parents=True)
        (self.store / f"{self.today}-01.md").write_text("earlier\n")
        decision, reason = run_hook(HOOK, agent(prompt("build", BUILD_SECTIONS),
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "ask", reason)
        self.assertIn("approval", reason)
        self.assertIn(f"prompts/preserved/{self.today}-02.md", reason)
        self.assertEqual(self.written(), [f"{self.today}-01.md", f"{self.today}-02.md"])

    def test_device_type_asks(self):
        decision, _ = run_hook(HOOK, agent(prompt("device", BUILD_SECTIONS), cwd=str(self.repo)))
        self.assertEqual(decision, "ask")

    def test_store_check_result_reported_not_enforced(self):
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "allow")
        self.assertIn("check_prompts.py", reason)
        self.assertIn("no RECORD.md entry", reason)

    # A built-in agent carries every tool, MCP included, so a read-only
    # planner could act through it. Only the configured subagents may be
    # dispatched.
    def test_only_coder_and_pulse_may_be_dispatched(self):
        for target in ("general-purpose", "Explore", "Plan", "statusline-setup",
                       "claude", "auditor"):
            with self.subTest(target):
                decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS),
                                                        target, cwd=str(self.repo)))
                self.assertEqual(decision, "deny")
                self.assertIn(f"subagent type {target!r} may not be dispatched", reason)
                self.assertEqual(self.written(), [], "a blocked dispatch was preserved")

    def test_missing_subagent_type_blocked(self):
        payload = agent(prompt("pulse", PULSE_SECTIONS), cwd=str(self.repo))
        del payload["tool_input"]["subagent_type"]
        decision, reason = run_hook(HOOK, payload)
        self.assertEqual(decision, "deny")
        self.assertIn("may not be dispatched", reason)
        self.assertEqual(self.written(), [])

    # Type binds to target: unbound, a pulse-typed dispatch to coder would
    # run without the approval builds need.
    def test_type_and_target_mismatch_blocked(self):
        cases = (("pulse", PULSE_SECTIONS, "coder", "'pulse'"),
                 ("build", BUILD_SECTIONS, "pulse", "'coder'"),
                 ("device", BUILD_SECTIONS, "pulse", "'coder'"))
        for type_, sections, target, required in cases:
            with self.subTest(f"{type_} -> {target}"):
                decision, reason = run_hook(HOOK, agent(prompt(type_, sections), target,
                                                        cwd=str(self.repo)))
                self.assertEqual(decision, "deny")
                self.assertIn(f"Type {type_!r} may only be dispatched to {required}", reason)
                self.assertEqual(self.written(), [], "a mismatched dispatch was preserved")

    def test_task_tool_name_also_checked(self):
        decision, _ = run_hook(HOOK, agent(prompt(None, BUILD_SECTIONS), tool_name="Task",
                                           cwd=str(self.repo)))
        self.assertEqual(decision, "deny")

    def test_other_tools_ignored(self):
        decision, _ = run_hook(HOOK, {"tool_name": "Bash", "cwd": str(self.repo),
                                      "tool_input": {"command": "git log"}})
        self.assertIsNone(decision)

    # Values from .claude/kit.json
    def test_agents_types_and_sections_come_from_config(self):
        config = dict(TEST_CONFIG, dispatchable_agents=["coder", "reader"],
                      agent_roles={"coder": "coder", "reader": "pulse"},
                      type_targets={"pulse": "reader", "build": "coder", "device": "coder"},
                      required_sections={"pulse": ["Role", "Question"],
                                         "build": ["Role", "Plan"],
                                         "device": ["Role", "Plan"]})

        def decide(text, target):
            return run_hook(HOOK, agent(text, target, cwd=str(self.repo)), config=config)
        decision, reason = decide(prompt("pulse", ["Role", "Question"]), "reader")
        self.assertEqual(decision, "allow", reason)
        decision, reason = decide(prompt("build", ["Role", "Plan"]), "coder")
        self.assertEqual(decision, "ask", reason)
        self.assertEqual(len(self.written()), 2)
        decision, reason = decide(prompt("pulse", ["Role", "Question"]), "pulse")
        self.assertEqual(decision, "deny")
        self.assertIn("subagent type 'pulse' may not be dispatched", reason)
        decision, reason = decide(prompt("pulse", PULSE_SECTIONS), "reader")
        self.assertEqual(decision, "deny")
        self.assertIn("missing 1 required section(s): Question.", reason)
        self.assertEqual(len(self.written()), 2)

    # Approval fails closed: a Type asks the operator unless the config
    # exempts it by name.
    def test_type_not_exempt_asks(self):
        config = dict(TEST_CONFIG,
                      type_targets=dict(TEST_CONFIG["type_targets"], review="coder"),
                      required_sections=dict(TEST_CONFIG["required_sections"],
                                             review=["Role", "Plan"]))
        decision, reason = run_hook(HOOK, agent(prompt("review", ["Role", "Plan"]), "coder",
                                                cwd=str(self.repo)), config=config)
        self.assertEqual(decision, "ask", reason)
        self.assertIn("Type 'review'", reason)
        self.assertIn("Operator approval required", reason)
        self.assertNotIn("pulse", reason.split("\n")[0])
        self.assertEqual(len(self.written()), 1)

    def test_approval_exemptions_come_from_config(self):
        def decide(config, type_, sections, target):
            return run_hook(HOOK, agent(prompt(type_, sections), target, cwd=str(self.repo)),
                            config=config)
        none_exempt = dict(TEST_CONFIG, approval_exempt_types=[])
        decision, reason = decide(none_exempt, "pulse", PULSE_SECTIONS, "pulse")
        self.assertEqual(decision, "ask", reason)
        self.assertIn("Type 'pulse'", reason)
        build_exempt = dict(TEST_CONFIG, approval_exempt_types=["pulse", "build"])
        decision, reason = decide(build_exempt, "build", BUILD_SECTIONS, "coder")
        self.assertEqual(decision, "allow", reason)
        self.assertIn("Type 'build'", reason)
        self.assertIn("approval_exempt_types", reason)
        self.assertNotIn("pulse", reason.split("\n")[0])
        decision, reason = decide(build_exempt, "device", BUILD_SECTIONS, "coder")
        self.assertEqual(decision, "ask", reason)

    def test_checkers_are_found_at_the_root_only(self):
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        self.assertIn("check_prompts.py exit ", reason)
        tools = self.repo / "tools"
        tools.mkdir()
        for name in ("check_record.py", "check_prompts.py"):
            (self.repo / name).rename(tools / name)
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        self.assertIn("check_prompts.py not found at the repository root", reason)

    # T1: every worktree of a clone draws saved-dispatch names from one
    # sequence, a locked counter in the shared git directory.
    def worktree(self):
        parent = Path(tempfile.mkdtemp(prefix="dispatch_guard_wt_"))
        self.addCleanup(shutil.rmtree, parent, ignore_errors=True)
        path = parent / "wt"
        subprocess.run(["git", "-C", str(self.repo), "worktree", "add", "-q", str(path)],
                       check=True)
        return path

    def test_two_worktrees_draw_distinct_names(self):
        other = self.worktree()
        other_store = other / "prompts" / "preserved"
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(other)))
        self.assertEqual(decision, "allow", reason)
        names = self.written() + sorted(p.name for p in other_store.glob("*.md"))
        self.assertEqual(names, [f"{self.today}-01.md", f"{self.today}-02.md"])

    def test_unreachable_counter_blocks(self):
        # A regular file where the counter directory belongs: it cannot be created.
        blocker = self.repo.resolve() / ".git" / "claude-kit"
        blocker.write_text("not a directory\n")
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "deny", reason)
        self.assertIn("shared dispatch counter could not be reached", reason)
        self.assertIn(str(blocker / "dispatch-seq"), reason)
        self.assertEqual(self.written(), [])

    def test_counter_starts_above_the_store(self):
        self.store.mkdir(parents=True)
        (self.store / f"{self.today}-05.md").write_text("earlier\n")
        decision, reason = run_hook(HOOK, agent(prompt("build", BUILD_SECTIONS),
                                                cwd=str(self.repo)))
        self.assertEqual(decision, "ask", reason)
        self.assertEqual(self.written(), [f"{self.today}-05.md", f"{self.today}-06.md"])

    # T10: a prompt identical to a stored dispatch's text is saved as a
    # re-send of it, naming the earliest such file.
    def stored(self, name, text):
        self.store.mkdir(parents=True, exist_ok=True)
        (self.store / name).write_text(
            "HEAD: fixture\nTarget subagent: pulse\nType: pulse\n"
            "Preserved: 2026-01-01T00:00:00Z by .claude/hooks/dispatch_guard.py\n"
            "--- verbatim prompt follows ---\n" + text)

    def saved_header(self, name):
        saved = (self.store / name).read_text()
        header, sep, _ = saved.partition("\n--- verbatim prompt follows ---\n")
        self.assertTrue(sep, f"no verbatim delimiter in: {saved[:300]!r}")
        return header

    def test_r1_identical_text_is_marked_repeat(self):
        text = prompt("pulse", PULSE_SECTIONS)
        self.stored(f"{self.today}-01.md", text)
        decision, reason = run_hook(HOOK, agent(text, "pulse", cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        self.assertEqual(self.written(), [f"{self.today}-01.md", f"{self.today}-02.md"])
        header = self.saved_header(f"{self.today}-02.md")
        self.assertIn(f"\nRepeat-of: preserved/{self.today}-01.md", header)

    def test_r2_one_byte_different_is_not_a_repeat(self):
        text = prompt("pulse", PULSE_SECTIONS)
        self.stored(f"{self.today}-01.md", text + " ")
        decision, reason = run_hook(HOOK, agent(text, "pulse", cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        self.assertNotIn("Repeat-of", self.saved_header(f"{self.today}-02.md"))
        self.assertNotIn("repeat of", reason)

    def test_r3_earliest_identical_file_is_named(self):
        text = prompt("pulse", PULSE_SECTIONS)
        self.stored(f"{self.today}-03.md", text)
        self.stored("2026-01-01-07.md", text)
        decision, reason = run_hook(HOOK, agent(text, "pulse", cwd=str(self.repo)))
        self.assertEqual(decision, "allow", reason)
        header = self.saved_header(f"{self.today}-04.md")
        self.assertIn("\nRepeat-of: preserved/2026-01-01-07.md", header)
        self.assertEqual(header.count("Repeat-of"), 1)

    def test_r4_reason_names_the_repeat(self):
        text = prompt("build", BUILD_SECTIONS)
        self.stored(f"{self.today}-01.md", text)
        decision, reason = run_hook(HOOK, agent(text, cwd=str(self.repo)))
        self.assertEqual(decision, "ask", reason)
        self.assertIn(f"repeat of preserved/{self.today}-01.md (identical text)", reason)
        self.assertIn(f"prompts/preserved/{self.today}-02.md", reason)

    def test_outside_a_repository_blocks(self):
        outside = tempfile.mkdtemp(prefix="dispatch_guard_norepo_")
        try:
            decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                    cwd=outside),
                                        env={"GIT_CEILING_DIRECTORIES": os.path.dirname(outside)})
            self.assertEqual(decision, "deny")
            self.assertIn("preserve", reason)
        finally:
            shutil.rmtree(outside, ignore_errors=True)

    # Every git the hook runs has the kit's timeout: a git that answers too
    # slowly blocks the dispatch by name instead of being waited for.
    def test_slow_git_denied_by_name(self):
        fake = Path(tempfile.mkdtemp(prefix="dispatch_guard_slow_"))
        self.addCleanup(shutil.rmtree, fake, ignore_errors=True)
        write_sleeper(fake / "git", 5, str(self.repo))
        env = {"PATH": slow_path(fake), PREFIX + "TIMEOUT": "1"}
        decision, reason = run_hook(HOOK, agent(prompt("pulse", PULSE_SECTIONS), "pulse",
                                                cwd=str(self.repo)), env=env)
        self.assertEqual(decision, "deny", f"got {decision!r}: {reason}")
        self.assertIn("timed out", reason)


if __name__ == "__main__":
    unittest.main()
