#!/usr/bin/env python3
"""Prints the union of the header bounding boxes of OSM PBF files, as minlon,minlat,maxlon,maxlat.

The merged extract carries no bounding box (osmium merge 1.15.0 drops the inputs' boxes; checked
on synthetic files, 2026-10-04), and Planetiler falls back to the whole world when it finds none
(Bounds.java at 0.10.2). So build-orwa.sh passes this union to Planetiler as --bounds.

    union_bounds.py FILE.osm.pbf [FILE.osm.pbf ...]

Exits non-zero, printing nothing on stdout, if any input has no header box: a union that silently
missed one input would clip it out of the map.
"""
import json
import subprocess
import sys


def boxes_of(path):
    out = subprocess.run(["osmium", "fileinfo", "-j", path], check=True, capture_output=True, text=True).stdout
    return json.loads(out)["header"]["boxes"]


def union(box_lists):
    boxes = [b for bl in box_lists for b in bl]
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def main(paths):
    if not paths:
        print(__doc__, file=sys.stderr)
        return 2
    box_lists = []
    for p in paths:
        bl = boxes_of(p)
        if not bl:
            print(f"no header bounding box in {p}", file=sys.stderr)
            return 1
        box_lists.append(bl)
    print(",".join(repr(v) for v in union(box_lists)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
