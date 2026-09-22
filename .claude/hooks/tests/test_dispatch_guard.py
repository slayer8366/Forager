"""dispatch_guard.py: step 4. Crafted Agent payloads against a throwaway
git repository holding the real checkers."""
import datetime
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from harness import HOOKS, run_hook

HOOK = "dispatch_guard.py"
REPO_ROOT = HOOKS.parent.parent

BUILD_SECTIONS = ["Role", "Base and state", "Scope boundary",
                  "Closed decisions (operator, 2026-09-22)", "Prediction",
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

    def test_task_tool_name_also_checked(self):
        decision, _ = run_hook(HOOK, agent(prompt(None, BUILD_SECTIONS), tool_name="Task",
                                           cwd=str(self.repo)))
        self.assertEqual(decision, "deny")

    def test_other_tools_ignored(self):
        decision, _ = run_hook(HOOK, {"tool_name": "Bash", "cwd": str(self.repo),
                                      "tool_input": {"command": "git log"}})
        self.assertIsNone(decision)

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


if __name__ == "__main__":
    unittest.main()
