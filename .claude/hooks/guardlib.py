"""Shared plumbing for the kit's PreToolUse guards.

Every guard reads the adopter's config (.claude/kit.json) and one hook
payload from stdin, and either stays silent (no decision: the call goes on
to the normal permission rules) or prints one permission decision.

A guard that crashes must not let the call through. Claude Code treats any
exit code other than 0 and 2 as non-blocking, so an uncaught exception in a
guard would fail open. `run` catches everything and turns it into a deny
that names the exception, which fails closed and says why.

Every command a guard runs goes through `run_command`, which gives it a
timeout: TIMEOUT seconds, 20 by default, overridable by the environment
variable <guard_env_prefix>TIMEOUT when that is a positive number. A command
that does not finish raises CommandTimeout, which `run` turns into a deny
naming the command and the seconds (no traceback), so a hung tool fails
closed by name before Claude Code's own hook limit cancels the hook.
"""
import json
import os
import re
import shlex
import subprocess
import sys
import traceback
from pathlib import Path


def emit(decision, reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": decision,
        "permissionDecisionReason": reason,
    }}))


# The adopter's config: .claude/kit.json, the file beside this hooks
# directory. Not CLAUDE_PROJECT_DIR and not the payload's repository, so a
# hook always reads the config that was installed with it.
CONFIG_PATH = Path(__file__).resolve().parent.parent / "kit.json"
CONFIG_REQUIRED = ("android_package", "protected_branches", "dispatchable_agents",
                   "type_targets", "required_sections", "approval_exempt_types",
                   "agent_roles")
# backup_dir: where a coder's merge backups live (history_guard's merge
# rule). Optional with no default: unset, every pull-request merge is denied.
CONFIG_DEFAULTS = {"guard_env_prefix": "KIT_GUARD_", "backup_dir": None}
CONFIG = None  # set by run() before the guard is called

# The timeout, in seconds, of every command a guard runs. run() sets TIMEOUT
# from <guard_env_prefix>TIMEOUT once the config (the prefix) is loaded; an
# unset, unreadable or non-positive value keeps the default.
DEFAULT_TIMEOUT = 20
TIMEOUT = DEFAULT_TIMEOUT
TIMEOUT_SUFFIX = "TIMEOUT"


class CommandTimeout(Exception):
    """A command a guard ran did not finish within `seconds`. The message
    names the command's first two words and the seconds."""

    def __init__(self, args, seconds):
        self.command = " ".join(str(a) for a in list(args)[:2])
        self.seconds = seconds
        super().__init__(f"`{self.command}` timed out after {seconds:g} seconds")


def read_timeout(prefix=None, environ=None):
    """<prefix>TIMEOUT as a positive number of seconds, else DEFAULT_TIMEOUT.
    The prefix defaults to the loaded config's guard_env_prefix (or the
    kit's default when no config is loaded)."""
    if prefix is None:
        prefix = (CONFIG or CONFIG_DEFAULTS)["guard_env_prefix"]
    raw = (os.environ if environ is None else environ).get(prefix + TIMEOUT_SUFFIX)
    try:
        value = float(raw)
    except (TypeError, ValueError):
        return DEFAULT_TIMEOUT
    if not 0 < value < float("inf"):  # also false for nan
        return DEFAULT_TIMEOUT
    return value


def run_command(args, **kwargs):
    """subprocess.run(args) with captured text output and TIMEOUT (or the
    `timeout` given). A command that does not finish in time raises
    CommandTimeout; the guard lets it propagate to run(), which denies."""
    kwargs.setdefault("capture_output", True)
    kwargs.setdefault("text", True)
    kwargs.setdefault("timeout", TIMEOUT)
    try:
        return subprocess.run(args, **kwargs)
    except subprocess.TimeoutExpired:
        raise CommandTimeout(args, kwargs["timeout"]) from None


class ConfigError(Exception):
    """The config is missing or invalid. Every hook blocks on it."""


def _string_list(value):
    return (isinstance(value, list)
            and all(isinstance(v, str) and v.strip() and v == v.strip() for v in value))


def validate_config(data):
    """The config with defaults filled in, or ConfigError naming every problem."""
    if not isinstance(data, dict):
        raise ConfigError(f"the top level is {type(data).__name__}, not an object")
    problems = []
    unknown = sorted(set(data) - set(CONFIG_REQUIRED) - set(CONFIG_DEFAULTS))
    if unknown:
        problems.append(f"unknown key(s) {', '.join(unknown)}")
    missing = [k for k in CONFIG_REQUIRED if k not in data]
    if missing:
        problems.append(f"missing required key(s) {', '.join(missing)}")

    pkg = data.get("android_package")
    if "android_package" in data and pkg is not None and not (
            isinstance(pkg, str) and pkg and not re.search(r"\s", pkg)):
        problems.append("android_package must be a package name or null")
    branches = data.get("protected_branches")
    if "protected_branches" in data and not (_string_list(branches) and branches):
        problems.append("protected_branches must be a non-empty list of branch names")
    agents = data.get("dispatchable_agents")
    if "dispatchable_agents" in data and not (_string_list(agents) and agents):
        problems.append("dispatchable_agents must be a non-empty list of agent names")
    targets = data.get("type_targets")
    if "type_targets" in data:
        if not (isinstance(targets, dict) and targets
                and all(isinstance(v, str) for v in targets.values())):
            problems.append("type_targets must be a non-empty object of type -> agent name")
        elif _string_list(agents):
            strays = sorted(t for t, a in targets.items() if a not in agents)
            if strays:
                problems.append(f"type_targets sends type(s) {', '.join(strays)} to an "
                                f"agent not in dispatchable_agents")
    sections = data.get("required_sections")
    if "required_sections" in data:
        if not (isinstance(sections, dict)
                and all(_string_list(v) for v in sections.values())):
            problems.append("required_sections must be an object of type -> list of "
                            "section names")
        elif isinstance(targets, dict) and set(sections) != set(targets):
            problems.append("required_sections and type_targets must name the same types")
    # Approval fails closed: every Type asks the operator unless it is listed
    # here. Required, so that every exemption is written down.
    exempt = data.get("approval_exempt_types")
    if "approval_exempt_types" in data:
        if not _string_list(exempt):
            problems.append("approval_exempt_types must be a list of dispatch types "
                            "(it may be empty)")
        elif isinstance(targets, dict):
            strays = sorted(t for t in exempt if t not in targets)
            if strays:
                problems.append(f"approval_exempt_types names type(s) "
                                f"{', '.join(strays)} not in type_targets")
    # Agent name -> role. A role name the kit does not define is not a
    # config error: role_guard denies that agent everything at runtime.
    roles = data.get("agent_roles")
    if "agent_roles" in data:
        if not (isinstance(roles, dict) and _string_list(list(roles))
                and _string_list(list(roles.values()))):
            problems.append("agent_roles must be an object of agent name -> role name")
        elif _string_list(agents):
            roleless = [a for a in agents if a not in roles]
            if roleless:
                problems.append(f"dispatchable agent(s) {', '.join(roleless)} have no "
                                f"role in agent_roles")
    prefix = data.get("guard_env_prefix", CONFIG_DEFAULTS["guard_env_prefix"])
    if not (isinstance(prefix, str) and re.fullmatch(r"[A-Z_][A-Z0-9_]*", prefix)):
        problems.append("guard_env_prefix must be an upper-case environment-variable prefix")
    if "backup_dir" in data and not isinstance(data["backup_dir"], str):
        problems.append("backup_dir must be a string (a directory path)")
    if problems:
        raise ConfigError("; ".join(problems))
    config = dict(CONFIG_DEFAULTS)
    config.update(data)
    return config


def load_config(path=CONFIG_PATH):
    try:
        text = Path(path).read_text()
    except FileNotFoundError:
        raise ConfigError(f"{path} does not exist") from None
    try:
        data = json.loads(text)
    except ValueError as exc:
        raise ConfigError(f"{path} is not valid JSON ({exc})") from None
    try:
        return validate_config(data)
    except ConfigError as exc:
        raise ConfigError(f"{path} is invalid: {exc}") from None


def run(guard):
    """Load the config, read the payload, call guard(payload) -> None or
    (decision, reason). A missing or invalid config blocks every call: the
    kit never fails open on its own config."""
    global CONFIG, TIMEOUT
    name = getattr(guard, "__module__", "guard")
    hook = os.path.basename(sys.argv[0]) or name
    try:
        CONFIG = load_config()
    except Exception as exc:
        emit("deny", f"{hook}: the kit config could not be used, so every call is "
                     f"blocked until it is fixed: {exc}")
        return 0
    TIMEOUT = read_timeout()
    try:
        raw = sys.stdin.read()
        payload = json.loads(raw)
        if not isinstance(payload, dict):
            raise ValueError(f"payload is {type(payload).__name__}, not an object")
        result = guard(payload)
    except CommandTimeout as exc:  # a hung command: deny by name, no traceback
        emit("deny", f"{hook}: {exc}; failing closed.")
        return 0
    except Exception as exc:  # fail closed, and say so
        emit("deny", f"{name}: guard error, failing closed: "
                     f"{type(exc).__name__}: {exc}\n{traceback.format_exc(limit=3)}")
        return 0
    if result is not None:
        emit(*result)
    return 0


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
