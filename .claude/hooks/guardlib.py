"""Shared plumbing for Forager's PreToolUse guards.

See docs/process/accountability-design.md. Every guard reads one hook
payload from stdin and either stays silent (no decision: the call goes on
to the normal permission rules) or prints one permission decision.

A guard that crashes must not let the call through. Claude Code treats any
exit code other than 0 and 2 as non-blocking, so an uncaught exception in a
guard would fail open. `run` catches everything and turns it into a deny
that names the exception, which fails closed and says why.
"""
import json
import re
import shlex
import sys
import traceback

FORAGER_PACKAGE = "com.zynergylabs.forager.app"


def emit(decision, reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": decision,
        "permissionDecisionReason": reason,
    }}))


def run(guard):
    """Read the payload, call guard(payload) -> None or (decision, reason)."""
    name = getattr(guard, "__module__", "guard")
    try:
        raw = sys.stdin.read()
        payload = json.loads(raw)
        if not isinstance(payload, dict):
            raise ValueError(f"payload is {type(payload).__name__}, not an object")
        result = guard(payload)
    except Exception as exc:  # fail closed, and say so
        emit("deny", f"{name}: guard error, failing closed: "
                     f"{type(exc).__name__}: {exc}\n{traceback.format_exc(limit=3)}")
        return 0
    if result is not None:
        emit(*result)
    return 0


def role(payload):
    """'planner' for the main session, else the subagent's agent_type.

    Step 0 (2026-09-22, Claude Code 2.1.280) observed agent_type absent in
    the main session and set to the subagent's name inside one."""
    agent_type = payload.get("agent_type")
    return agent_type if agent_type else "planner"


def command_of(payload):
    return (payload.get("tool_input") or {}).get("command", "") or ""


def shell_tokens(command):
    """Tokens with unquoted shell punctuation split out (';', '&&', '|', '>'
    and so on become their own tokens). Raises ValueError on unbalanced
    quotes, which callers treat as a reason to deny, not to guess."""
    lexer = shlex.shlex(command, posix=True, punctuation_chars=True)
    lexer.whitespace_split = True
    return list(lexer)


PUNCTUATION = re.compile(r"^[;&|<>()]+$")


def has_redirection(tokens):
    return any(PUNCTUATION.match(t) and (">" in t or "<" in t) for t in tokens)
