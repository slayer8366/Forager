#!/usr/bin/env python3
"""Launch one headless Claude Code session under a wall-clock limit, after
checking that it is the expected session. It deletes nothing.

The rule, in the order it runs:

1. Pre-check the checkout. `--cwd` must be a git work tree with no tracked
   changes (`git status --porcelain --untracked-files=no` prints nothing),
   and its HEAD must equal `--expect-commit`, or by default
   `origin/<first protected branch in <cwd>/.claude/kit.json>` as last
   fetched. The launcher never fetches. Any mismatch: exit 4, nothing starts.
2. Hooks check. Run `python3 <cwd>/.claude/hooks/session_check.py` in
   `<cwd>` with a SessionStart payload on stdin (hook_event_name
   "SessionStart", source "startup", cwd, session_id), the file and input
   Claude Code would use, under the same limit and kill rules as the
   launch. Any stdout, a nonzero exit, or a missing hook file: exit 4,
   quoting the text, and nothing starts.
3. Launch `<claude> -p --session-id <new uuid> --output-format stream-json
   --verbose --permission-prompts none` (plus `--max-budget-usd` and
   `--model` when given) in `<cwd>`, with the prompt file on stdin, in its
   own session and process group. Its stdout is written to `--out` line by
   line, verbatim; its stderr to `<out>.stderr`.
4. Identity. The first stream message with type "system" and subtype
   "init" must carry `session_id` equal to the uuid and `cwd` equal to the
   real path of `<cwd>`. A mismatch: stop the group (5), exit 4. Claude
   exiting, or the limit passing, with no init message: exit 5.
5. Time limit. `--timeout` seconds (default 600) from launch. At the limit:
   SIGTERM to the process group, wait up to `--grace` seconds (default 15)
   for the group to empty, then SIGKILL the group. Then confirm no process
   of the group is left (from /proc: a process whose pgrp is the group and
   whose state is not zombie; without /proc, `os.killpg(pgid, 0)`). Any
   left are named, and the exit is 3 anyway.
6. Interruption. SIGINT, SIGTERM or SIGHUP to the launcher while the hooks
   check or the session is running stops that process group as the time
   limit does (5), prints the report with the outcome "interrupted by
   <signal>", and exits 130. A signal that arrives while no hook check or
   session is running (the pre-check, between the hooks check and the
   launch, or after the session's leader has exited) exits 130 at once
   with one line on stderr, killing nothing. A signal that arrives while a
   group is already being stopped is ignored; that stop goes on.
7. Report, one line per fact: session id; the session log, found by its
   session id as the one file matching `<projects root>/*/<uuid>.jsonl`
   (or "not found" with that pattern, or "ambiguous" with every match);
   claude's exit code (negative: killed by that signal number); elapsed
   seconds; signals sent; processes of the group left, if any; the
   outcome.

The projects root is `$LAUNCH_SESSION_PROJECTS_ROOT` when set (for tests),
else `$CLAUDE_CONFIG_DIR/projects` when CLAUDE_CONFIG_DIR is set (inferred,
not verified), else `~/.claude/projects`. The launcher does not compute the
project directory's name. Claude Code names it from the session's cwd:
every character outside [A-Za-z0-9] becomes `-`; per the installed Claude
Code 2.1.282, a name over 200 characters is cut and given a hash suffix
(read from the binary, not verified). The session id is a fresh UUID, so
the log is found by it instead.

Exit codes:
  0  finished: the session exited on its own before the limit, after a
     correct init (claude's own exit code is on the report line)
  2  usage error (bad arguments, an existing --out or <out>.stderr, a
     missing --out directory, an unreadable --prompt-file)
  3  timed out
  4  wrong session (a pre-check, the hooks check, or the init's identity)
  5  failed to start (claude not found or not runnable, or no init message
     before claude exited or the limit passed)
  130 interrupted by SIGINT, SIGTERM or SIGHUP (6)

It writes only `--out` and `<out>.stderr`, both created new at launch, so
a failed check writes nothing. It deletes no log, no output file and no
worktree. git runs with GIT_OPTIONAL_LOCKS=0, so no index refresh is
written. Standard library only, Python 3.8+.

    python3 launch_session.py --cwd <checkout> --prompt-file <file> \\
        --out <stream file> [--expect-commit <sha>] [--timeout 600] \\
        [--max-budget-usd <n>] [--model <m>] [--claude <path>] [--grace 15]
"""
import argparse
import json
import os
import shutil
import signal
import subprocess
import sys
import threading
import time
import uuid

FINISHED, USAGE, TIMED_OUT, WRONG, NO_START, INTERRUPTED = 0, 2, 3, 4, 5, 130
POLL = 0.1
KILL_WAIT = 5.0
GIT_TIMEOUT = 60


PROJECTS_ENV = "LAUNCH_SESSION_PROJECTS_ROOT"
HANDLED = (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)

# The process group a signal would stop: `proc` while a hook check or the
# session runs; `starting` while one is being started (a signal then waits in
# `pending`); `stopping` while a group is being stopped.
STATE = {"proc": None, "starting": False, "pending": None, "stopping": False}


class Interrupted(Exception):
    def __init__(self, signum):
        Exception.__init__(self, signum)
        self.signum = signum


def signame(signum):
    try:
        return signal.Signals(signum).name
    except ValueError:
        return "signal %d" % signum


def exit_now(signum):
    """A signal with no hook check or session running: exit 130, kill nothing."""
    try:
        sys.stdout.flush()
        sys.stderr.write("launch_session: interrupted by %s with no hook check or session "
                         "running; nothing was stopped\n" % signame(signum))
        sys.stderr.flush()
    except (OSError, ValueError):
        pass
    os._exit(INTERRUPTED)


def on_signal(signum, frame):
    if STATE["stopping"]:
        return
    if STATE["proc"] is not None:
        STATE["stopping"] = True
        raise Interrupted(signum)
    if STATE["starting"]:
        STATE["pending"] = signum
        return
    exit_now(signum)


def ended():
    """The running process's leader has exited: a signal now kills nothing."""
    STATE["proc"] = None


class Stop(Exception):
    def __init__(self, code, outcome):
        Exception.__init__(self, outcome)
        self.code = code
        self.outcome = outcome


def git(cwd, *args):
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
    try:
        return subprocess.run(["git", *args], cwd=cwd, env=env, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, universal_newlines=True,
                              timeout=GIT_TIMEOUT)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise Stop(WRONG, "wrong session: git %s failed: %s" % (" ".join(args), e))


def expected_commit(cwd, expect):
    if expect:
        ref, label = expect, "--expect-commit %s" % expect
    else:
        path = os.path.join(cwd, ".claude", "kit.json")
        try:
            with open(path, encoding="utf-8") as f:
                branches = json.load(f)["protected_branches"]
            branch = branches[0]
            if not isinstance(branch, str):
                raise ValueError("not a branch name")
        except (OSError, ValueError, KeyError, IndexError, TypeError) as e:
            raise Stop(WRONG, "wrong session: cannot read the first protected branch "
                              "from %s (%s)" % (path, e))
        ref = "origin/%s" % branch
        label = "%s as last fetched" % ref
    r = git(cwd, "rev-parse", "--verify", "--quiet", ref + "^{commit}")
    if r.returncode != 0 or not r.stdout.strip():
        raise Stop(WRONG, "wrong session: %s does not resolve to a commit" % label)
    return r.stdout.strip(), label


def precheck(cwd, expect):
    if not os.path.isdir(cwd):
        raise Stop(WRONG, "wrong session: %s is not a directory" % cwd)
    r = git(cwd, "rev-parse", "--is-inside-work-tree")
    if r.returncode != 0 or r.stdout.strip() != "true":
        raise Stop(WRONG, "wrong session: %s is not a git work tree" % cwd)
    r = git(cwd, "status", "--porcelain", "--untracked-files=no")
    if r.returncode != 0:
        raise Stop(WRONG, "wrong session: git status failed: %s" % r.stderr.strip())
    if r.stdout.strip():
        raise Stop(WRONG, "wrong session: tracked changes in %s: %s"
                   % (cwd, "; ".join(r.stdout.strip().splitlines())))
    r = git(cwd, "rev-parse", "HEAD")
    head = r.stdout.strip()
    if r.returncode != 0 or not head:
        raise Stop(WRONG, "wrong session: %s has no HEAD commit" % cwd)
    want, label = expected_commit(cwd, expect)
    if head != want:
        raise Stop(WRONG, "wrong session: HEAD is %s, expected %s (%s)" % (head, want, label))
    return head


def group_left(pgid):
    """PIDs of live processes in the group; zombies are dead and not counted."""
    if os.path.isdir("/proc"):
        left = []
        for name in os.listdir("/proc"):
            if not name.isdigit():
                continue
            try:
                with open("/proc/%s/stat" % name) as f:
                    stat = f.read()
            except OSError:
                continue
            fields = stat[stat.rindex(")") + 2:].split()
            if len(fields) > 2 and int(fields[2]) == pgid and fields[0] != "Z":
                left.append(int(name))
        return sorted(left)
    try:
        os.killpg(pgid, 0)
    except ProcessLookupError:
        return []
    except PermissionError:
        pass
    return [pgid]


def signal_group(pgid, sig):
    try:
        os.killpg(pgid, sig)
        return True
    except ProcessLookupError:
        return False


def reap(proc):
    """Collect the leader's exit status once it is dead (a zombie)."""
    try:
        proc.wait(timeout=1)
    except subprocess.TimeoutExpired:
        pass


def stop_group(proc, grace):
    """SIGTERM the group, wait up to grace for it to empty, then SIGKILL.
    Returns (signals sent, PIDs still left). Signals are ignored meanwhile."""
    STATE["stopping"] = True
    try:
        return _stop_group(proc, grace)
    finally:
        STATE["proc"] = None
        STATE["stopping"] = False


def _stop_group(proc, grace):
    pgid = proc.pid
    sent = []
    if signal_group(pgid, signal.SIGTERM):
        sent.append("SIGTERM")
    deadline = time.monotonic() + grace
    while True:
        proc.poll()
        if not group_left(pgid):
            reap(proc)
            return sent, []
        if time.monotonic() >= deadline:
            break
        time.sleep(POLL)
    if signal_group(pgid, signal.SIGKILL):
        sent.append("SIGKILL")
    deadline = time.monotonic() + KILL_WAIT
    while True:
        proc.poll()
        left = group_left(pgid)
        if not left or time.monotonic() >= deadline:
            if not left:
                reap(proc)
            return sent, left
        time.sleep(POLL)


def start(argv, cwd, data, on_line, stderr, env=None, on_start=None):
    """Start argv in its own session and process group. data goes to stdin
    from a thread; each stdout line goes to on_line from a reader thread.
    stderr is a file object, or a callable given the whole stderr text.
    The process is registered for signals (STATE) before start returns; a
    signal that arrived while it was starting is acted on then."""
    err_target = stderr if not callable(stderr) else subprocess.PIPE
    STATE["starting"] = True
    try:
        proc = subprocess.Popen(argv, cwd=cwd, env=env, stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=err_target,
                                start_new_session=True)
    except BaseException:
        STATE["starting"] = False
        if STATE["pending"] is not None:
            exit_now(STATE["pending"])
        raise
    STATE["proc"] = proc
    STATE["starting"] = False
    if on_start is not None:
        on_start(proc)

    def feed():
        try:
            proc.stdin.write(data)
            proc.stdin.close()
        except OSError:
            pass

    def read():
        for line in iter(proc.stdout.readline, b""):
            on_line(line)
        proc.stdout.close()

    def read_err():
        stderr(proc.stderr.read())
        proc.stderr.close()

    threads = [threading.Thread(target=feed, daemon=True),
               threading.Thread(target=read, daemon=True)]
    if callable(stderr):
        threads.append(threading.Thread(target=read_err, daemon=True))
    for t in threads:
        t.start()
    if STATE["pending"] is not None and not STATE["stopping"]:
        STATE["stopping"] = True
        raise Interrupted(STATE["pending"])
    return proc, threads


def join(threads, seconds):
    deadline = time.monotonic() + seconds
    for t in threads:
        t.join(max(0.0, deadline - time.monotonic()))


def hooks_check(cwd, real, sid, timeout, grace):
    hook = os.path.join(cwd, ".claude", "hooks", "session_check.py")
    if not os.path.isfile(hook):
        raise Stop(WRONG, "wrong session: %s is missing, so the hooks cannot be checked" % hook)
    payload = json.dumps({"hook_event_name": "SessionStart", "source": "startup",
                          "cwd": real, "session_id": sid}).encode("utf-8")
    out, err = [], []
    env = dict(os.environ, CLAUDE_PROJECT_DIR=real)
    try:
        proc, threads = start(["python3", hook], cwd, payload, out.append, err.append, env)
    except OSError as e:
        raise Stop(WRONG, "wrong session: could not run %s: %s" % (hook, e))
    deadline = time.monotonic() + timeout
    while proc.poll() is None:
        if time.monotonic() >= deadline:
            sent, left = stop_group(proc, grace)
            raise Stop(WRONG, "wrong session: %s did not finish within %s s (sent %s%s)"
                       % (hook, fmt(timeout), ", ".join(sent) or "nothing",
                          "; processes left: %s" % left if left else ""))
        time.sleep(POLL)
    ended()
    join(threads, 5)
    text = b"".join(out).decode("utf-8", "replace").strip()
    errtext = b"".join(err).decode("utf-8", "replace").strip()
    if text or proc.returncode != 0:
        raise Stop(WRONG, "wrong session: session_check reported (exit %d): %s%s"
                   % (proc.returncode, text or "(no stdout)",
                      " | stderr: %s" % errtext if errtext else ""))


def fmt(seconds):
    return ("%g" % seconds)


def projects_root():
    root = os.environ.get(PROJECTS_ENV)
    if root:
        return root
    config = os.environ.get("CLAUDE_CONFIG_DIR")
    if config:
        return os.path.join(config, "projects")  # inferred, not verified
    return os.path.join(os.path.expanduser("~"), ".claude", "projects")


def find_log(root, sid):
    """(pattern, matches): every <root>/<dir>/<sid>.jsonl that is a file."""
    name = sid + ".jsonl"
    pattern = os.path.join(root, "*", name)
    try:
        dirs = sorted(os.listdir(root))
    except OSError:
        return pattern, []
    return pattern, [os.path.join(root, d, name) for d in dirs
                     if os.path.isfile(os.path.join(root, d, name))]


def log_line(sid):
    pattern, matches = find_log(projects_root(), sid)
    if not matches:
        return "log: not found (%s)" % pattern
    if len(matches) > 1:
        return "log: ambiguous, %d files match %s: %s" % (len(matches), pattern,
                                                         ", ".join(matches))
    return "log: %s" % matches[0]


class Stream:
    """Writes stream lines verbatim and watches for the first init message."""

    def __init__(self, out):
        self.out = out
        self.init = None
        self.seen = threading.Event()

    def line(self, raw):
        self.out.write(raw)
        self.out.flush()
        if self.init is not None:
            return
        try:
            msg = json.loads(raw.decode("utf-8"))
        except ValueError:
            return
        if isinstance(msg, dict) and msg.get("type") == "system" and msg.get("subtype") == "init":
            self.init = msg
            self.seen.set()


def identity_problem(init, sid, real):
    if init.get("session_id") != sid:
        return "init session_id %r differs from the launched %s" % (init.get("session_id"), sid)
    if init.get("cwd") != real:
        return "init cwd %r differs from %s" % (init.get("cwd"), real)
    return None


def launch(args, real, sid, prompt, report):
    claude = shutil.which(args.claude)
    if claude is None:
        raise Stop(NO_START, "failed to start: %s not found or not executable" % args.claude)
    argv = [claude, "-p", "--session-id", sid, "--output-format", "stream-json",
            "--verbose", "--permission-prompts", "none"]
    if args.max_budget_usd is not None:
        argv += ["--max-budget-usd", args.max_budget_usd]
    if args.model is not None:
        argv += ["--model", args.model]
    try:
        out = open(args.out, "xb")
    except OSError as e:
        raise Stop(USAGE, "usage error: cannot create %s: %s" % (args.out, e))
    try:
        err = open(args.out + ".stderr", "xb")
    except OSError as e:
        out.close()
        raise Stop(USAGE, "usage error: cannot create %s.stderr: %s" % (args.out, e))
    stream = Stream(out)
    began = time.monotonic()
    report["session id"] = sid

    def registered(proc):
        report["_began"] = began
        report["_pgid"] = proc.pid

    try:
        proc, threads = start(argv, args.cwd, prompt, stream.line, err, on_start=registered)
    except OSError as e:
        raise Stop(NO_START, "failed to start: %s: %s" % (claude, e))
    finally:
        err.close()
    deadline = began + args.timeout
    checked = False
    while True:
        if stream.seen.is_set() and not checked:
            checked = True
            problem = identity_problem(stream.init, sid, real)
            if problem:
                sent, left = stop_group(proc, args.grace)
                report["signals"], report["_left"] = sent, left
                report["claude exit code"] = proc.returncode
                raise Stop(WRONG, "wrong session: %s" % problem)
        if proc.poll() is not None:
            ended()
            break
        if time.monotonic() >= deadline:
            sent, left = stop_group(proc, args.grace)
            report["signals"], report["_left"] = sent, left
            report["claude exit code"] = proc.returncode
            if stream.init is None:
                raise Stop(NO_START, "failed to start: no init message within %s s"
                           % fmt(args.timeout))
            raise Stop(TIMED_OUT, "timed out after %s s" % fmt(args.timeout))
        time.sleep(POLL)
    join(threads, 5)
    report["claude exit code"] = proc.returncode
    report["_left"] = group_left(proc.pid)
    if stream.init is None:
        raise Stop(NO_START, "failed to start: claude exited (code %d) with no init message"
                   % proc.returncode)
    problem = identity_problem(stream.init, sid, real)
    if problem:
        raise Stop(WRONG, "wrong session: %s" % problem)
    return "finished"


def positive(text):
    try:
        value = float(text)
    except ValueError:
        raise argparse.ArgumentTypeError("%r is not a number" % text)
    if value <= 0:
        raise argparse.ArgumentTypeError("%r must be greater than 0" % text)
    return value


def parse(argv):
    p = argparse.ArgumentParser(
        description="Launch one headless Claude Code session under a time limit, "
                    "after checking it is the expected session. Deletes nothing.")
    p.add_argument("--cwd", required=True, help="the checkout the session runs in")
    p.add_argument("--prompt-file", required=True, help="file whose text goes on stdin")
    p.add_argument("--out", required=True, help="stream file to create (must not exist)")
    p.add_argument("--expect-commit", help="commit HEAD must equal (default: "
                   "origin/<first protected branch> as last fetched)")
    p.add_argument("--timeout", type=positive, default=600.0,
                   help="wall-clock seconds from launch (default 600)")
    p.add_argument("--grace", type=positive, default=15.0,
                   help="seconds between SIGTERM and SIGKILL (default 15)")
    p.add_argument("--max-budget-usd", help="passed to claude")
    p.add_argument("--model", help="passed to claude")
    p.add_argument("--claude", default="claude", help="claude executable (default: claude)")
    args = p.parse_args(argv)
    try:
        with open(args.prompt_file, "rb") as f:
            prompt = f.read()
    except OSError as e:
        p.error("cannot read --prompt-file: %s" % e)
    outdir = os.path.dirname(os.path.abspath(args.out))
    if not os.path.isdir(outdir):
        p.error("the --out directory %s does not exist" % outdir)
    for path in (args.out, args.out + ".stderr"):
        if os.path.lexists(path):
            p.error("%s exists; the launcher never overwrites a file" % path)
    return args, prompt


def main(argv=None):
    for sig in HANDLED:
        signal.signal(sig, on_signal)
    args, prompt = parse(sys.argv[1:] if argv is None else argv)
    real = os.path.realpath(args.cwd)
    sid = str(uuid.uuid4())
    report = {}
    try:
        precheck(args.cwd, args.expect_commit)
        hooks_check(args.cwd, real, sid, args.timeout, args.grace)
        code, outcome = FINISHED, launch(args, real, sid, prompt, report)
    except Stop as stop:
        code, outcome = stop.code, stop.outcome
    except Interrupted as intr:
        proc = STATE["proc"]
        sent, left = stop_group(proc, args.grace)
        report["signals"], report["_left"], report["_pgid"] = sent, left, proc.pid
        during = "" if "_began" in report else " during the hooks check"
        if "_began" in report:
            report["claude exit code"] = proc.returncode
        code, outcome = INTERRUPTED, "interrupted by %s%s" % (signame(intr.signum), during)
    launched = "_began" in report
    print("session id: %s" % (sid if launched else "none (not launched)"))
    if launched:
        print(log_line(sid))
    else:
        print("log: none (not launched)")
    rc = report.get("claude exit code")
    print("claude exit code: %s" % ("not run" if not launched else
                                    ("none" if rc is None else rc)))
    elapsed = time.monotonic() - report["_began"] if launched else 0.0
    print("elapsed: %.1f s" % elapsed)
    if report.get("signals"):
        print("signals: %s to process group %d" % (", ".join(report["signals"]),
                                                  report["_pgid"]))
    if report.get("_left"):
        print("processes of group %d still running: %s"
              % (report["_pgid"], " ".join(str(p) for p in report["_left"])))
    print("outcome: %s" % outcome)
    return code


if __name__ == "__main__":
    sys.exit(main())
