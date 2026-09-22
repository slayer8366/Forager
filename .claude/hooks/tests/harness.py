"""Runs a hook the way Claude Code does: one JSON payload on stdin, the
decision read back from stdout (or exit code 2). Tests assert on the
decision and on the reason text the planner or agent would be shown."""
import json
import os
import subprocess
import sys
from pathlib import Path

HOOKS = Path(__file__).resolve().parent.parent


def run_hook(hook, payload, env=None, cwd=None):
    """(decision, reason). decision is 'allow', 'deny', 'ask', or None when
    the hook stays silent. A non-JSON payload may be passed as a str."""
    stdin = payload if isinstance(payload, str) else json.dumps(payload)
    full_env = dict(os.environ)
    full_env.update(env or {})
    proc = subprocess.run([sys.executable, str(HOOKS / hook)], input=stdin,
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
