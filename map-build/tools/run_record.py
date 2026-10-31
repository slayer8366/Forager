#!/usr/bin/env python3
"""Writes the build's status file and each output's manifest (dispatch 2026-09-28-495).

    run_record.py status FILE --state STATE [--stage S] [--run-id R] [--osm-timestamp T]
                                            [--output DIR] [--message M] [--warnings W1,W2]
    run_record.py manifest OUT.json --pins PINS_ENV --run-dir RUN --osm-timestamp T
                                    --osm-sequence N|REGION:N,... --inputs F... --sources F... --outputs F...
    run_record.py routing-manifest OUT_DIR --run-id R --osm-timestamp T --osm-sequence N|REGION:N,... --notice FILE

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

routing-manifest (dispatch 2026-10-30-coder-04): stages, in one output directory, what the route-data upload sends
(bin/upload-routing.sh): NOTICE, a byte-for-byte copy of the committed notice file (map-build/notices/
routing-NOTICE.txt: the height sources' attributions), and routing-manifest.json, the shape the Worker reads as
routing/manifest.json (server/pmtiles-worker/src/routing/manifest.ts on pre-main): run_id, osm_timestamp,
osm_sequence, files (OUT_DIR/segments/*.rd5 as <run_id>/segments4/...), names (OUT_DIR/names1/*.fnm as
<run_id>/names1/...), each with size and SHA-256, sorted by path; and notice (the NOTICE key, <run_id>/NOTICE) and
notices (its lines). The Worker reads run_id, osm_timestamp and files only, so the added fields are ignored there.
Nothing is written unless the notice file exists and the run id is one the Worker accepts.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
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


def osm_sequence(text):
    """A single region's sequence is a number, as build-orwa.sh records it. A build of several
    regions records each one's, as build-usca.sh does ("us:2415,canada:4945"), because Geofabrik
    numbers each region separately; that is kept as written, since both scripts read it back only
    to compare it with the sequence they would build. Anything else is refused, not guessed at."""
    if text.isdigit():
        return int(text)
    parts = text.split(",")
    if all(len(kv) == 2 and kv[0] and kv[1].isdigit() for kv in (p.split(":") for p in parts)):
        return text
    raise ValueError(f"--osm-sequence must be N or REGION:N,REGION:N...: {text!r}")


def cmd_manifest(a):
    try:
        sequence = osm_sequence(a.osm_sequence)
    except ValueError as e:
        print(e, file=sys.stderr)
        return 2
    metrics = []
    metrics_path = os.path.join(a.run_dir, "metrics.jsonl")
    if os.path.exists(metrics_path):
        with open(metrics_path) as f:
            metrics = [json.loads(line) for line in f if line.strip()]
    manifest = {
        "built_at": now(),
        "osm": {"replication_timestamp": a.osm_timestamp, "replication_sequence": sequence},
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


RUN_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")  # as the Worker's manifest.ts RUN_ID


def staged_entries(run_id, folder, r2_folder, suffix):
    names = sorted(n for n in os.listdir(folder) if n.endswith(suffix)) if os.path.isdir(folder) else []
    return [{"path": f"{run_id}/{r2_folder}/{n}", "size": os.path.getsize(os.path.join(folder, n)),
             "sha256": sha256(os.path.join(folder, n))} for n in names]


def cmd_routing_manifest(a):
    if not RUN_ID.match(a.run_id):
        print(f"--run-id {a.run_id!r} is not a run id the Worker accepts", file=sys.stderr)
        return 2
    if not os.path.isfile(a.notice):
        print(f"no notice file at {a.notice}", file=sys.stderr)
        return 2
    try:
        sequence = osm_sequence(a.osm_sequence)
    except ValueError as e:
        print(e, file=sys.stderr)
        return 2
    files = staged_entries(a.run_id, os.path.join(a.out_dir, "segments"), "segments4", ".rd5")
    if not files:
        print(f"no .rd5 squares in {a.out_dir}/segments", file=sys.stderr)
        return 2
    with open(a.notice, encoding="utf-8") as f:
        notices = [line for line in f.read().split("\n") if line]
    manifest = {
        "run_id": a.run_id,
        "osm_timestamp": a.osm_timestamp,
        "osm_sequence": sequence,
        "files": files,
        "names": staged_entries(a.run_id, os.path.join(a.out_dir, "names1"), "names1", ".fnm"),
        "notice": f"{a.run_id}/NOTICE",
        "notices": notices,
    }
    shutil.copyfile(a.notice, os.path.join(a.out_dir, "NOTICE"))
    write_atomic(os.path.join(a.out_dir, "routing-manifest.json"), manifest)
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
    r = sub.add_parser("routing-manifest")
    r.add_argument("out_dir")
    r.add_argument("--run-id", required=True)
    r.add_argument("--osm-timestamp", required=True)
    r.add_argument("--osm-sequence", required=True)
    r.add_argument("--notice", required=True)
    a = p.parse_args(argv)
    return {"status": cmd_status, "manifest": cmd_manifest, "routing-manifest": cmd_routing_manifest}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
