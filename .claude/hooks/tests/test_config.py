"""The kit config, .claude/kit.json: a missing or invalid config makes every
hook block with a message naming the problem. The kit never fails open on
its own config."""
import copy
import json
import unittest

from harness import NO_CONFIG, TEST_CONFIG, bash, run_hook, tool

# One payload per hook that the hook, correctly configured, lets through
# without a decision. A config problem must turn each into a deny.
SILENT = {
    "role_guard.py": tool("Read", "coder"),
    "dispatch_guard.py": bash("git status", "coder"),
    "device_guard.py": bash("git status", "coder"),
    "history_guard.py": bash("git status", "coder"),
}


def with_change(**changes):
    config = copy.deepcopy(TEST_CONFIG)
    for key, value in changes.items():
        if value is NO_CONFIG:
            del config[key]
        else:
            config[key] = value
    return config


class Config(unittest.TestCase):
    def assertEveryHookBlocks(self, config, *words):
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload, config=config)
                self.assertEqual(decision, "deny", f"{hook}: {decision!r} {reason}")
                self.assertIn("kit config could not be used", reason)
                self.assertIn("kit.json", reason)
                for w in words:
                    self.assertIn(w, reason)

    def test_valid_config_is_silent(self):
        # The control for every test below: the same payloads, a good config.
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload)
                self.assertIsNone(decision, reason)

    def test_missing_config_blocks_every_hook(self):
        self.assertEveryHookBlocks(NO_CONFIG, "does not exist")

    def test_malformed_json_blocks_every_hook(self):
        self.assertEveryHookBlocks('{"android_package": ', "not valid JSON")

    def test_not_an_object_blocks(self):
        self.assertEveryHookBlocks("[]", "not an object")

    def test_unknown_key_blocks(self):
        config = with_change(prompt_store="prompts/preserved")
        self.assertEveryHookBlocks(config, "unknown key(s) prompt_store")

    def test_checkers_dir_is_an_unknown_key(self):
        # The checkers sit at the repository root; the key that once moved
        # them is gone, and a config still carrying it fails closed.
        self.assertEveryHookBlocks(with_change(checkers_dir="."),
                                   "unknown key(s) checkers_dir")

    def test_missing_required_key_blocks(self):
        for key in ("android_package", "protected_branches", "dispatchable_agents",
                    "type_targets", "required_sections", "approval_exempt_types",
                    "agent_roles"):
            with self.subTest(key):
                self.assertEveryHookBlocks(with_change(**{key: NO_CONFIG}),
                                           f"missing required key(s) {key}")

    def test_wrong_values_block_by_key(self):
        cases = {
            "android_package": ("com example", "android_package"),
            "protected_branches": ([], "protected_branches"),
            "dispatchable_agents": (["coder", ""], "dispatchable_agents"),
            "type_targets": ({"build": 3}, "type_targets"),
            "guard_env_prefix": ("kit-guard", "guard_env_prefix"),
            "approval_exempt_types": ("pulse", "approval_exempt_types"),
            "agent_roles": ({"coder": "coder", "pulse": 3}, "agent_roles"),
        }
        for key, (value, word) in cases.items():
            with self.subTest(key):
                self.assertEveryHookBlocks(with_change(**{key: value}), word)

    def test_type_sent_to_an_undispatchable_agent_blocks(self):
        config = with_change(type_targets={"pulse": "pulse", "build": "builder",
                                           "device": "coder"})
        self.assertEveryHookBlocks(config, "build", "not in dispatchable_agents")

    def test_sections_and_targets_must_name_the_same_types(self):
        sections = dict(TEST_CONFIG["required_sections"])
        del sections["device"]
        self.assertEveryHookBlocks(with_change(required_sections=sections),
                                   "must name the same types")

    def test_exempt_type_must_be_a_configured_type(self):
        config = with_change(approval_exempt_types=["pulse", "review"])
        self.assertEveryHookBlocks(config, "approval_exempt_types", "review",
                                   "not in type_targets")

    def test_no_exempt_types_is_valid(self):
        config = with_change(approval_exempt_types=[])
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload, config=config)
                self.assertIsNone(decision, reason)

    def test_every_dispatchable_agent_needs_a_role(self):
        config = with_change(agent_roles={"coder": "coder"})
        self.assertEveryHookBlocks(config, "agent_roles", "pulse")

    def test_an_unknown_role_name_is_not_a_config_error(self):
        # role_guard denies such an agent at runtime; the config stays valid.
        config = with_change(agent_roles={"coder": "coder", "pulse": "nosuch"})
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload, config=config)
                self.assertIsNone(decision, reason)

    def test_optional_keys_default(self):
        config = with_change(guard_env_prefix=NO_CONFIG)
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload, config=config)
                self.assertIsNone(decision, reason)

    def test_c1_backup_dir_string_is_valid(self):
        config = with_change(backup_dir="~/kit-backups")
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload, config=config)
                self.assertIsNone(decision, reason)

    def test_c2_backup_dir_not_a_string_blocks(self):
        self.assertEveryHookBlocks(with_change(backup_dir=3),
                                   "backup_dir must be a string")

    def test_null_package_is_valid(self):
        config = with_change(android_package=None)
        for hook, payload in SILENT.items():
            with self.subTest(hook):
                decision, reason = run_hook(hook, payload, config=config)
                self.assertIsNone(decision, reason)

    def test_repository_config_and_template_are_valid(self):
        # The shipped template and this repository's own config, through the
        # same validator the hooks use.
        import sys
        from harness import HOOKS
        sys.path.insert(0, str(HOOKS))
        import guardlib
        for path in (HOOKS.parent / "kit.json",
                     HOOKS.parent.parent / "templates" / "kit.json"):
            if not path.exists():
                continue  # an adopter has no templates/ directory
            with self.subTest(str(path)):
                guardlib.validate_config(json.loads(path.read_text()))


if __name__ == "__main__":
    unittest.main()
