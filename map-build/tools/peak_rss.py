#!/usr/bin/env python3
"""Runs a command and records its wall time and peak resident memory.

GNU time is not installed on the Pi, so the peak comes from getrusage(RUSAGE_CHILDREN).ru_maxrss:
the largest resident set of any child this wrapper has waited for, in KiB on Linux. Each call
runs one command, so that is the command's own peak.

    peak_rss.py LABEL -- COMMAND [ARGS...]

Prints one line to stderr and, if FB_METRICS names a file, appends the same as a JSON line.
Exits with the command's exit status.
"""
import json
import os
import resource
import subprocess
import sys
import time


def main(argv):
    if len(argv) < 3 or argv[1] != "--":
        print(__doc__, file=sys.stderr)
        return 2
    label, cmd = argv[0], argv[2:]
    start = time.monotonic()
    code = subprocess.call(cmd)
    elapsed = time.monotonic() - start
    peak_kib = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    record = {"label": label, "exit": code, "elapsed_s": round(elapsed, 1), "peak_rss_kib": peak_kib}
    print(f"metrics: {json.dumps(record)}", file=sys.stderr)
    path = os.environ.get("FB_METRICS")
    if path:
        with open(path, "a") as f:
            f.write(json.dumps(record) + "\n")
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
