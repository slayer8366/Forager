"""PreToolUse on Bash, every role: protect the owner's phone and its data.

Traced to the 2026-09-22 device run, where connectedAndroidTest uninstalled
the app and wiped the owner's data, taps landed in another app, and a
screenshot caught personal data.

- connectedAndroidTest (any variant) is blocked unless the command keeps the
  APKs installed after the run.
- `adb uninstall`, `pm uninstall` and `pm clear` are blocked.
- `adb install` is blocked unless each APK's signing certificate matches the
  installed package's. A package that is not installed is allowed.
- `adb shell input` and any screencap are blocked unless the resumed
  activity belongs to Forager.

Wherever the guard needs a device or tool answer and cannot get one, it
denies and says what it could not determine. These are patterns over the
command text; see the bypass table in the completion report.
"""
import glob
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402

LEAVE_INSTALLED = "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
CONNECTED_TEST = re.compile(r"\bconnected\w*AndroidTest\b")
# pm forms first: `adb shell pm uninstall` also matches the broader adb pattern.
UNINSTALLS = [
    ("pm uninstall", re.compile(r"\bpm\s+uninstall\b")),
    ("pm clear", re.compile(r"\bpm\s+clear\b")),
    ("adb uninstall", re.compile(r"\badb\b[^;&|]*\suninstall\b")),
]
INSTALL = re.compile(r"\badb\b[^;&|]*\sinstall(?:-multiple)?\b")
FOREGROUND_GATED = re.compile(
    r"\badb\b[^;&|]*\s(?:shell|exec-out)\b[^;&|]*?\b(input|screencap)\b")
RESUMED = re.compile(
    r"(?:topResumedActivity|mResumedActivity|ResumedActivity)\s*[=:]\s*"
    r"ActivityRecord\{\S+ \S+ ([A-Za-z0-9_.]+)/")
SIGNER_DIGEST = re.compile(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)")


def tool(env_name, sdk_name):
    if os.environ.get(env_name):
        return os.environ[env_name]
    if sdk_name == "adb":
        return "adb"
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    found = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", sdk_name)))
    return found[-1] if found else sdk_name


def adb_target(tokens):
    """The adb global options that pick a device (-s SERIAL, -d, -e, -t ID),
    so the guard's own queries go to the same device as the command."""
    opts, i = [], tokens.index("adb") + 1 if "adb" in tokens else len(tokens)
    while i < len(tokens) and tokens[i].startswith("-"):
        if tokens[i] in ("-s", "-t") and i + 1 < len(tokens):
            opts += tokens[i:i + 2]
            i += 2
        elif tokens[i] in ("-d", "-e"):
            opts.append(tokens[i])
            i += 1
        else:
            break
    return opts


def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=60)


def foreground_package(target):
    r = run([tool("FORAGER_GUARD_ADB", "adb"), *target, "shell", "dumpsys",
             "activity", "activities"])
    if r.returncode != 0:
        return None, f"`dumpsys activity activities` failed: {r.stderr.strip() or r.returncode}"
    m = RESUMED.search(r.stdout)
    if not m:
        return None, "no resumed activity found in `dumpsys activity activities`"
    return m.group(1), None


def signer_digests(apk):
    r = run([tool("FORAGER_GUARD_APKSIGNER", "apksigner"), "verify", "--print-certs", apk])
    digests = set(d.lower() for d in SIGNER_DIGEST.findall(r.stdout))
    if r.returncode != 0 or not digests:
        raise RuntimeError(f"apksigner could not read the signature of {apk}: "
                           f"{r.stderr.strip() or r.stdout.strip()[:200]}")
    return digests


def check_install(tokens, target):
    apks = [t for t in tokens if t.endswith(".apk")]
    if not apks:
        return "could not find an .apk argument to check its signature"
    adb = tool("FORAGER_GUARD_ADB", "adb")
    for apk in apks:
        try:
            r = run([tool("FORAGER_GUARD_AAPT2", "aapt2"), "dump", "badging", apk])
            m = re.search(r"package: name='([^']+)'", r.stdout)
            if r.returncode != 0 or not m:
                return (f"could not read the package name of {apk}: "
                        f"{r.stderr.strip() or 'no package line'}")
            pkg = m.group(1)
            r = run([adb, *target, "shell", "pm", "path", pkg])
            paths = [l[len("package:"):].strip() for l in r.stdout.splitlines()
                     if l.startswith("package:")]
            if r.returncode != 0 and (r.stderr.strip() or r.stdout.strip()):
                return (f"could not ask the device whether {pkg} is installed: "
                        f"{r.stderr.strip() or r.stdout.strip()}")
            if not paths:
                continue  # not installed: nothing to overwrite
            base = next((p for p in paths if p.endswith("/base.apk")), paths[0])
            with tempfile.TemporaryDirectory(prefix="device_guard_") as tmp:
                local = os.path.join(tmp, "installed.apk")
                r = run([adb, *target, "pull", base, local])
                if r.returncode != 0 or not os.path.exists(local):
                    return f"could not pull the installed {pkg} to compare signatures: {r.stderr.strip()}"
                installed = signer_digests(local)
            new = signer_digests(apk)
        except Exception as exc:
            return f"could not compare signatures for {apk}: {type(exc).__name__}: {exc}"
        if new != installed:
            return (f"{apk} is signed by {', '.join(sorted(new))} but the installed "
                    f"{pkg} is signed by {', '.join(sorted(installed))}. Installing "
                    f"it means uninstalling first, which wipes the app's data")
    return None


def guard(payload):
    if payload.get("tool_name") != "Bash":
        return None
    command = g.command_of(payload)

    if CONNECTED_TEST.search(command) and LEAVE_INSTALLED not in command:
        return ("deny", f"device_guard: connectedAndroidTest uninstalls the app "
                        f"after the run, wiping its data. Add {LEAVE_INSTALLED}.")
    for name, pattern in UNINSTALLS:
        if pattern.search(command):
            return ("deny", f"device_guard: `{name}` removes the app or its data "
                            f"from the owner's phone and is never run by an agent.")

    gated_install = INSTALL.search(command)
    gated_fg = FOREGROUND_GATED.search(command)
    if not (gated_install or gated_fg):
        return None
    try:
        tokens = g.shell_tokens(command)
    except ValueError as exc:
        return ("deny", f"device_guard: could not parse the command ({exc}), so "
                        f"its device target cannot be checked.")
    target = adb_target(tokens)

    if gated_install:
        problem = check_install(tokens, target)
        if problem:
            return ("deny", f"device_guard: install blocked: {problem}.")
    if gated_fg:
        pkg, problem = foreground_package(target)
        if problem:
            return ("deny", f"device_guard: `{gated_fg.group(1)}` blocked: could not "
                            f"read the foreground app ({problem}).")
        if pkg != g.FORAGER_PACKAGE:
            return ("deny", f"device_guard: `{gated_fg.group(1)}` blocked: the "
                            f"foreground app is {pkg}, not {g.FORAGER_PACKAGE}.")
    return None


if __name__ == "__main__":
    sys.exit(g.run(guard))
