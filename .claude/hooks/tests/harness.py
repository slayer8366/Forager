"""Runs a hook the way Claude Code does: one JSON payload on stdin, the
decision read back from stdout (or exit code 2). Tests assert on the
decision and on the reason text the planner or agent would be shown."""
import atexit
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

HOOKS = Path(__file__).resolve().parent.parent

# Each hook reads the kit.json beside its hooks directory. Tests never use
# the adopter's own config: run_hook copies the hooks into a temporary
# .claude/hooks/ with the config the test chose beside it, so a test's
# result does not depend on how the repository running it is configured.
TEST_CONFIG = {
    "android_package": "com.example.kittest",
    "protected_branches": ["main"],
    "dispatchable_agents": ["coder", "pulse"],
    "type_targets": {"pulse": "pulse", "build": "coder", "device": "coder"},
    "required_sections": {
        "build": ["Role", "Base and state", "Scope boundary", "Closed decisions",
                  "Prediction", "Finish line and abort conditions", "Checks",
                  "Out of scope", "Device items"],
        "device": ["Role", "Base and state", "Scope boundary", "Closed decisions",
                   "Prediction", "Finish line and abort conditions", "Checks",
                   "Out of scope", "Device items"],
        "pulse": ["Role", "Base and state", "Rules", "Questions"],
    },
    "approval_exempt_types": ["pulse"],
    "agent_roles": {"coder": "coder", "pulse": "pulse"},
    "guard_env_prefix": "KIT_GUARD_",
}
NO_CONFIG = object()  # pass as config= to run a hook with no kit.json at all
_trees = {}


def _cleanup():
    for root in _trees.values():
        shutil.rmtree(root, ignore_errors=True)


atexit.register(_cleanup)


def hooks_with(config):
    """A temporary hooks directory whose kit.json is config: a dict (written
    as JSON), a str (written as is, so it may be invalid) or NO_CONFIG."""
    if config is NO_CONFIG:
        key = "\0none"
    elif isinstance(config, str):
        key = "\0raw" + config
    else:
        key = json.dumps(config, sort_keys=True)
    if key not in _trees:
        root = Path(tempfile.mkdtemp(prefix="kit_hooks_"))
        _trees[key] = root
        target = root / ".claude" / "hooks"
        target.mkdir(parents=True)
        for path in HOOKS.glob("*.py"):
            shutil.copy(path, target / path.name)
        if config is not NO_CONFIG:
            text = config if isinstance(config, str) else json.dumps(config)
            (root / ".claude" / "kit.json").write_text(text)
    return _trees[key] / ".claude" / "hooks"


def run_hook(hook, payload, env=None, cwd=None, config=None):
    """(decision, reason). decision is 'allow', 'deny', 'ask', or None when
    the hook stays silent. A non-JSON payload may be passed as a str.
    config defaults to TEST_CONFIG; see hooks_with for the other forms."""
    stdin = payload if isinstance(payload, str) else json.dumps(payload)
    full_env = dict(os.environ)
    full_env.update(env or {})
    hooks = hooks_with(TEST_CONFIG if config is None else config)
    proc = subprocess.run([sys.executable, str(hooks / hook)], input=stdin,
                          capture_output=True, text=True, env=full_env, cwd=cwd,
                          timeout=60)
    if proc.returncode == 2:
        return "deny", proc.stderr
    if proc.returncode != 0:
        raise AssertionError(f"{hook} exited {proc.returncode}, which Claude Code "
                             f"treats as non-blocking: {proc.stderr}")
    out = proc.stdout.strip()
    if not out:
        return None, ""
    data = json.loads(out)["hookSpecificOutput"]
    return data["permissionDecision"], data.get("permissionDecisionReason", "")


def bash(command, agent_type=None, cwd="/tmp"):
    p = {"hook_event_name": "PreToolUse", "tool_name": "Bash", "cwd": cwd,
         "tool_input": {"command": command}}
    if agent_type:
        p["agent_type"] = agent_type
        p["agent_id"] = "a0000"
    return p


def tool(name, agent_type=None, tool_input=None, cwd="/tmp"):
    p = {"hook_event_name": "PreToolUse", "tool_name": name, "cwd": cwd,
         "tool_input": tool_input or {}}
    if agent_type:
        p["agent_type"] = agent_type
        p["agent_id"] = "a0000"
    return p


def write_sleeper(path, seconds, line):
    """An executable at path that sleeps `seconds`, prints `line` and exits 0:
    a stand-in for a tool that answers too slowly. Nothing a test writes
    with this sleeps longer than 5 seconds."""
    path = Path(path)
    path.write_text(f"#!/usr/bin/env python3\nimport sys, time\ntime.sleep({seconds})\n"
                    f"print({line!r})\nsys.exit(0)\n")
    path.chmod(path.stat().st_mode | stat.S_IEXEC)
    return path


def slow_path(fake_dir):
    """A PATH with fake_dir first, so a fake `git` there is the one found."""
    return os.pathsep.join([str(fake_dir), os.environ.get("PATH", "")])
