#!/usr/bin/env python3
"""Compare two Android lint XML reports: what is new, gone and still there (dispatch 2026-10-17-03).

Written and proved on a hand-made control (compare-control-*.xml, run_compare_control.sh)
before the CI run's output existed, so the comparison method was fixed before results were read.

Rules, fixed in advance:

- One <issue> element is one issue, counted as count_lint_issues.py counts it.
- Per id: before-count, after-count, and a status:
    new    -- 0 before, >0 after
    gone   -- >0 before, 0 after
    still  -- >0 both times (the counts may differ; both are printed)
- Per site: an issue's site is (id, primary file), with the line number left out on purpose,
  because ~130 commits moved lines. Site counts are compared as multisets, so a file that had
  3 issues of an id and now has 2 shows 1 gone and 2 still. A site that moved to a renamed
  file reads as one gone plus one new; the script cannot tell a move from a fix.
- --root strips the CI checkout prefix from both reports' paths (both runs used the same one).

Usage:
  compare_lint_runs.py BEFORE.xml AFTER.xml [--root DIR] [--json]

Standard library only.
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter

MISSING = "(missing)"


def primary_file(issue, root):
    location = issue.find("location")
    if location is None:
        return MISSING
    path = location.get("file", MISSING)
    if path.startswith("file://"):
        path = path[len("file://"):]
    if root and path.startswith(root):
        path = path[len(root):].lstrip("/")
    return path


def load(path, root):
    top = ET.parse(path).getroot()
    if top.tag != "issues":
        raise SystemExit("{}: root is <{}>, not <issues>".format(path, top.tag))
    issues = top.findall("issue")
    by_id = Counter(i.get("id", MISSING) for i in issues)
    by_site = Counter((i.get("id", MISSING), primary_file(i, root)) for i in issues)
    if sum(by_id.values()) != len(issues) or sum(by_site.values()) != len(issues):
        raise SystemExit("Internal check failed on {}".format(path))
    return len(issues), by_id, by_site


def compare(before_path, after_path, root=None):
    before_total, before_ids, before_sites = load(before_path, root)
    after_total, after_ids, after_sites = load(after_path, root)

    ids = {}
    for issue_id in sorted(set(before_ids) | set(after_ids)):
        b, a = before_ids.get(issue_id, 0), after_ids.get(issue_id, 0)
        status = "new" if b == 0 else "gone" if a == 0 else "still"
        ids[issue_id] = {"before": b, "after": a, "status": status}

    still_sites = before_sites & after_sites
    gone_sites = before_sites - after_sites
    new_sites = after_sites - before_sites
    sites = {
        "still": sum(still_sites.values()),
        "gone": sum(gone_sites.values()),
        "new": sum(new_sites.values()),
    }
    # Every issue is in exactly one bucket on its own side.
    if sites["still"] + sites["gone"] != before_total or sites["still"] + sites["new"] != after_total:
        raise SystemExit("Internal check failed: site buckets do not sum to the totals")

    def listing(counter):
        return ["{} x{} {}".format(i, n, f) for (i, f), n in sorted(counter.items())]

    return {
        "before_total": before_total,
        "after_total": after_total,
        "ids": ids,
        "id_status_counts": dict(sorted(Counter(v["status"] for v in ids.values()).items())),
        "sites": sites,
        "gone_sites": listing(gone_sites),
        "new_sites": listing(new_sites),
    }


def print_tables(r):
    print("Totals: before {} after {} (delta {:+d})".format(
        r["before_total"], r["after_total"], r["after_total"] - r["before_total"]))
    print("Issues by site (id, file; lines ignored): still {still}, gone {gone}, new {new}".format(**r["sites"]))
    print()
    print("  {:<40} {:>6} {:>6} {:>6}  {}".format("id", "before", "after", "delta", "status"))
    rows = sorted(r["ids"].items(), key=lambda kv: (kv[1]["status"], -kv[1]["after"], -kv[1]["before"], kv[0]))
    for issue_id, v in rows:
        print("  {:<40} {:>6} {:>6} {:>+6}  {}".format(
            issue_id, v["before"], v["after"], v["after"] - v["before"], v["status"]))
    print()
    print("Gone sites:")
    for line in r["gone_sites"]:
        print("  " + line)
    print("New sites:")
    for line in r["new_sites"]:
        print("  " + line)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("--root")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    result = compare(args.before, args.after, args.root)
    if args.json:
        json.dump(result, sys.stdout, indent=2, sort_keys=True)
        sys.stdout.write("\n")
    else:
        print_tables(result)


if __name__ == "__main__":
    main()
