#!/usr/bin/env python3
"""Writes the build's status file and each output's manifest (dispatch 2026-09-28-495).

    run_record.py status FILE --state STATE [--stage S] [--run-id R] [--osm-timestamp T]
                                            [--output DIR] [--message M] [--warnings W1,W2]
    run_record.py manifest OUT.json --pins PINS_ENV --run-dir RUN --osm-timestamp T
                                    --osm-sequence N --inputs F... --sources F... --outputs F...

status: STATE is running, ok, ok_with_warnings, skipped or failed. ok_with_warnings means the
output was published, but a step after publishing failed; --warnings names those steps. The file
is replaced atomically. last_success means the last published build and nothing else: only an ok
or ok_with_warnings write at stage "done" with an --output sets it. A fetch-only or skipped run
does not, so a weekly run whose download works and build fails cannot read as current. Any other
write carries the previous last_success over, but only if it names a published output; an entry
without one, as an earlier version of this tool wrote for a fetch-only run, is dropped.

manifest: records, for one output directory, everything needed to say what it was built from:
- the OSM timestamp and sequence;
- each input's size, SHA-256 and published MD5 (FILE.md5 beside it);
- each auxiliary source's size, SHA-256 and the HTTP headers it was fetched with (FILE.headers);
- each output's size and SHA-256;
- the pins and the map-build commit;
- the tool versions (RUN/versions.txt);
- the per-step metrics (RUN/metrics.jsonl, from peak_rss.py).
A missing record file is reported as such in the manifest, never filled in with a guess.
"""
import argparse
import datetime
import hashlib
import json
import os
import sys


def now():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_atomic(path, obj):
    tmp = f"{path}.tmp.{os.getpid()}"
    with open(tmp, "w") as f:
        json.dump(obj, f, indent=1, sort_keys=True)
        f.write("\n")
    os.replace(tmp, path)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def file_entry(path):
    return {"name": os.path.basename(path), "size": os.path.getsize(path), "sha256": sha256(path)}


def read_or_missing(path):
    if not os.path.exists(path):
        return {"missing": os.path.basename(path)}
    with open(path) as f:
        return f.read().strip()


def parse_pins(path):
    pins = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            pins[key] = value.strip('"')
    return pins


def headers(path):
    """Selected response headers from `curl -D` output; the last response wins after redirects."""
    if not os.path.exists(path):
        return {"missing": os.path.basename(path)}
    wanted = ("content-length", "last-modified", "etag", "location")
    out = {}
    with open(path, errors="replace") as f:
        for line in f:
            if line.upper().startswith("HTTP/"):
                out = {"status": line.strip()}
            elif ":" in line:
                k, v = line.split(":", 1)
                if k.strip().lower() in wanted:
                    out[k.strip().lower()] = v.strip()
    return out


def dropped(path):
    """The .rd5 squares deleted after the build, from build-orwa.sh's name<TAB>size list."""
    if not path:
        return []
    if not os.path.exists(path):
        return {"missing": os.path.basename(path)}
    out = []
    with open(path) as f:
        for line in f:
            if line.strip():
                name, size = line.rstrip("\n").split("\t")
                out.append({"name": name, "size": int(size)})
    return out


def cmd_status(a):
    previous = {}
    if os.path.exists(a.file):
        with open(a.file) as f:
            try:
                previous = json.load(f)
            except json.JSONDecodeError:
                previous = {"unreadable_previous_status": True}
    carried = previous.get("last_success")
    if not (isinstance(carried, dict) and carried.get("output")):
        carried = None
    status = {
        "state": a.state,
        "stage": a.stage,
        "run_id": a.run_id,
        "osm_timestamp": a.osm_timestamp,
        "message": a.message,
        "warnings": [w for w in (a.warnings or "").split(",") if w],
        "updated": now(),
        "last_success": carried,
    }
    if a.state == "ok_with_warnings" and not status["warnings"]:
        print("ok_with_warnings needs --warnings naming the failed steps", file=sys.stderr)
        return 2
    if a.state in ("ok", "ok_with_warnings") and a.stage == "done" and a.output:
        status["last_success"] = {"run_id": a.run_id, "osm_timestamp": a.osm_timestamp, "output": a.output,
                                  "at": status["updated"], "warnings": status["warnings"]}
    write_atomic(a.file, status)
    return 0


def cmd_manifest(a):
    metrics = []
    metrics_path = os.path.join(a.run_dir, "metrics.jsonl")
    if os.path.exists(metrics_path):
        with open(metrics_path) as f:
            metrics = [json.loads(line) for line in f if line.strip()]
    manifest = {
        "built_at": now(),
        "osm": {"replication_timestamp": a.osm_timestamp, "replication_sequence": int(a.osm_sequence)},
        "inputs": [dict(file_entry(p), published_md5=read_or_missing(p + ".md5")) for p in a.inputs],
        "sources": [dict(file_entry(p), fetched_with=headers(p + ".headers")) for p in a.sources],
        "outputs": [file_entry(p) for p in a.outputs],
        "dropped_rd5": dropped(a.dropped),
        "pins": parse_pins(a.pins),
        "map_build_commit": read_or_missing(os.path.join(os.path.dirname(os.path.abspath(a.pins)), "COMMIT")),
        "tool_versions": read_or_missing(os.path.join(a.run_dir, "versions.txt")),
        "metrics": metrics,
        "licences": {
            "data": "ODbL (OpenStreetMap contributors); the landcover layer derives from ESA WorldCover, CC-BY 4.0",
            "design": "Protomaps basemap design CC0",
        },
    }
    write_atomic(a.out, manifest)
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("status")
    s.add_argument("file")
    s.add_argument("--state", required=True, choices=["running", "ok", "ok_with_warnings", "skipped", "failed"])
    s.add_argument("--stage")
    s.add_argument("--run-id")
    s.add_argument("--osm-timestamp")
    s.add_argument("--output")
    s.add_argument("--message")
    s.add_argument("--warnings", help="comma-separated names of the steps that failed after publishing")
    m = sub.add_parser("manifest")
    m.add_argument("out")
    m.add_argument("--pins", required=True)
    m.add_argument("--run-dir", required=True)
    m.add_argument("--osm-timestamp", required=True)
    m.add_argument("--osm-sequence", required=True)
    m.add_argument("--inputs", nargs="+", required=True)
    m.add_argument("--sources", nargs="+", required=True)
    m.add_argument("--outputs", nargs="+", required=True)
    m.add_argument("--dropped", help="build-orwa.sh's list of deleted .rd5 squares (name<TAB>size)")
    a = p.parse_args(argv)
    return cmd_status(a) if a.cmd == "status" else cmd_manifest(a)


if __name__ == "__main__":
    sys.exit(main())
