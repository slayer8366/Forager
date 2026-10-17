#!/usr/bin/env python3
"""Count the issues in an Android lint XML report (dispatch 2026-10-12-01, RECORD -918).

Written and proved on a hand-made control (control-report.xml, run_control.sh) before any
real lint output existed, so the counting method was fixed before the results were read.

Counting rules, fixed in advance:

- One <issue> element is one issue. Lint writes one element per incident; an incident with
  secondary locations carries several <location> children but is still one issue. Locations
  are counted separately and never added to the issue count.
- Severity, category and id are lint's own attribute values, copied exactly. Nothing is
  regrouped: a sub-category such as "Usability:Typography" is its own row, as lint wrote it.
- An issue missing an attribute is counted under "(missing)" rather than dropped, so the
  per-severity, per-category and per-id tables each always sum to the issue total. The
  script checks that they do and exits non-zero if not.
- Examples are the first <location> of an issue (lint's primary location), as file:line.
  --root strips a leading directory (the CI checkout) so paths read repo-relative.

Usage:
  count_lint_issues.py REPORT.xml [--root DIR] [--examples N] [--top N] [--json]

--json prints the full counts as JSON (used by run_control.sh); otherwise prints tables.
Standard library only.
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict

MISSING = "(missing)"


def location_text(location, root):
    path = location.get("file", MISSING)
    if path.startswith("file://"):
        path = path[len("file://"):]
    if root and path.startswith(root):
        path = path[len(root):].lstrip("/")
    line = location.get("line")
    return "{}:{}".format(path, line) if line else path


def count(report_path, root=None, examples=3):
    tree = ET.parse(report_path)
    top = tree.getroot()
    if top.tag != "issues":
        raise SystemExit(
            "{}: root element is <{}>, not <issues>; not a lint XML report".format(
                report_path, top.tag
            )
        )

    issues = top.findall("issue")
    direct = set(map(id, issues))
    stray = [el for el in top.iter("issue") if id(el) not in direct]
    if stray:
        raise SystemExit(
            "{}: {} <issue> element(s) are not direct children of <issues>; the report has a "
            "shape this script was not written for".format(report_path, len(stray))
        )

    by_severity = Counter()
    by_category = OrderedDict()
    by_id = OrderedDict()
    location_total = 0

    for issue in issues:
        severity = issue.get("severity", MISSING)
        category = issue.get("category", MISSING)
        issue_id = issue.get("id", MISSING)
        locations = issue.findall("location")
        location_total += len(locations)

        by_severity[severity] += 1
        by_category.setdefault(category, Counter())[severity] += 1

        entry = by_id.setdefault(
            issue_id,
            {"count": 0, "severities": Counter(), "categories": Counter(), "examples": []},
        )
        entry["count"] += 1
        entry["severities"][severity] += 1
        entry["categories"][category] += 1
        if locations and len(entry["examples"]) < examples:
            entry["examples"].append(location_text(locations[0], root))

    total = len(issues)
    sums = {
        "severity": sum(by_severity.values()),
        "category": sum(sum(c.values()) for c in by_category.values()),
        "id": sum(e["count"] for e in by_id.values()),
    }
    bad = {name: value for name, value in sums.items() if value != total}
    if bad:
        raise SystemExit(
            "Internal check failed: issue total {} but tables sum to {}".format(total, bad)
        )

    return {
        "report": {"format": top.get("format"), "by": top.get("by")},
        "total_issues": total,
        "total_locations": location_total,
        "issues_with_several_locations": sum(
            1 for issue in issues if len(issue.findall("location")) > 1
        ),
        "by_severity": dict(sorted(by_severity.items())),
        "by_category": {
            category: dict(sorted(counter.items()))
            for category, counter in sorted(
                by_category.items(), key=lambda kv: (-sum(kv[1].values()), kv[0])
            )
        },
        "by_id": {
            issue_id: {
                "count": entry["count"],
                "severities": dict(sorted(entry["severities"].items())),
                "categories": dict(sorted(entry["categories"].items())),
                "examples": entry["examples"],
            }
            for issue_id, entry in sorted(
                by_id.items(), key=lambda kv: (-kv[1]["count"], kv[0])
            )
        },
    }


def print_tables(result, top_n):
    report = result["report"]
    print("Report: format={} by={}".format(report["format"], report["by"]))
    print("Issues: {}  (locations: {}; issues with several locations: {})".format(
        result["total_issues"], result["total_locations"],
        result["issues_with_several_locations"]))
    print()
    print("By severity")
    for severity, n in result["by_severity"].items():
        print("  {:<12} {}".format(severity, n))
    print()
    severities = sorted(result["by_severity"])
    print("By category (severities inside)")
    print("  {:<45} {:>6}  {}".format("category", "total", "  ".join(severities)))
    for category, counter in result["by_category"].items():
        print("  {:<45} {:>6}  {}".format(
            category, sum(counter.values()),
            "  ".join("{:>{}}".format(counter.get(s, 0), len(s)) for s in severities)))
    print()
    print("By id (largest first{})".format(", top {}".format(top_n) if top_n else ""))
    rows = list(result["by_id"].items())
    if top_n:
        rows = rows[:top_n]
    for issue_id, entry in rows:
        print("  {:<40} {:>5}  {}  {}  e.g. {}".format(
            issue_id, entry["count"],
            "/".join(entry["severities"]), "/".join(entry["categories"]),
            "; ".join(entry["examples"]) or "-"))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("report")
    parser.add_argument("--root", help="leading directory to strip from file paths")
    parser.add_argument("--examples", type=int, default=3,
                        help="example locations kept per id (default 3)")
    parser.add_argument("--top", type=int, default=0,
                        help="print only the N largest ids in the table (0 = all)")
    parser.add_argument("--json", action="store_true", help="print the counts as JSON")
    args = parser.parse_args(argv)

    result = count(args.report, root=args.root, examples=args.examples)
    if args.json:
        json.dump(result, sys.stdout, indent=2)
        sys.stdout.write("\n")
    else:
        print_tables(result, args.top)


if __name__ == "__main__":
    main()
