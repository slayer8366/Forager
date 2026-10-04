#!/usr/bin/env python3
"""How much of a routed track runs along OSM ways with a given name, without printing positions.

Used for the routing check of dispatch 2026-09-28-495 (a BRouter route on the new .rd5 files). The
report records only the route's description, its length and the share on the named trail, so this
prints lengths and names, never coordinates.

    route_on_named_ways.py TRACK.gpx WAYS.geojsonseq [--tolerance 15] [--name-contains Ramona]

TRACK.gpx: BRouter's output (trkpt elements).
WAYS.geojsonseq: `osmium export -f geojsonseq` of the ways near the route (LineStrings with tags).

Each track segment counts as "on" a way when both of its ends lie within the tolerance of that
way's line. The output gives the track's length, its length on each way name (named ways only),
its length on ways whose name contains the given text, and its length by highway type.
"""
import argparse
import json
import math
import re
import sys

R = 6371008.8  # mean Earth radius, metres


def haversine(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def to_xy(p, ref):
    """Local equirectangular metres around ref: accurate to well under a metre over a few km."""
    k = math.cos(math.radians(ref[1]))
    return ((p[0] - ref[0]) * math.pi / 180 * R * k, (p[1] - ref[1]) * math.pi / 180 * R)


def point_segment_distance(p, a, b):
    ax, ay = a; bx, by = b; px, py = p
    dx, dy = bx - ax, by - ay
    if dx == dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def read_gpx(path):
    """Track points in document order, whatever the order of the lat and lon attributes."""
    text = open(path).read()
    points = []
    for m in re.finditer(r'<trkpt([^>]*)>', text):
        attrs = dict(re.findall(r'(\w+)="([-0-9.]+)"', m.group(1)))
        points.append((float(attrs["lon"]), float(attrs["lat"])))
    if len(points) < 2:
        raise SystemExit(f"fewer than two trkpt in {path}")
    return points


def read_ways(path):
    ways = []
    with open(path) as f:
        for line in f:
            line = line.strip().lstrip("\x1e")
            if not line:
                continue
            feat = json.loads(line)
            geom = feat.get("geometry") or {}
            if geom.get("type") != "LineString":
                continue
            ways.append((feat.get("properties") or {}, geom["coordinates"]))
    return ways


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("track")
    p.add_argument("ways")
    p.add_argument("--tolerance", type=float, default=15.0, help="metres (default 15)")
    p.add_argument("--name-contains", default="")
    a = p.parse_args(argv)

    track = read_gpx(a.track)
    ways = read_ways(a.ways)
    ref = track[0]
    txy = [to_xy(q, ref) for q in track]
    wxy = [(props, [to_xy(q, ref) for q in coords]) for props, coords in ways]

    def nearest(pt):
        """(distance, props) of the closest way to a track point."""
        best = (float("inf"), None)
        for props, line in wxy:
            for i in range(len(line) - 1):
                d = point_segment_distance(pt, line[i], line[i + 1])
                if d < best[0]:
                    best = (d, props)
        return best

    near = [nearest(pt) for pt in txy]
    total = 0.0
    by_name, by_highway = {}, {}
    on_contains = 0.0
    off = 0.0
    for i in range(len(track) - 1):
        seg = haversine(track[i], track[i + 1])
        total += seg
        (d1, p1), (d2, p2) = near[i], near[i + 1]
        if d1 <= a.tolerance and d2 <= a.tolerance:
            props = p1 if d1 <= d2 else p2
            name = props.get("name", "(unnamed)")
            by_name[name] = by_name.get(name, 0.0) + seg
            hw = props.get("highway", "(no highway tag)")
            by_highway[hw] = by_highway.get(hw, 0.0) + seg
            if a.name_contains and a.name_contains.lower() in name.lower():
                on_contains += seg
        else:
            off += seg
    out = {
        "track_points": len(track),
        "track_length_m": round(total),
        "tolerance_m": a.tolerance,
        "length_by_way_name_m": {k: round(v) for k, v in sorted(by_name.items(), key=lambda kv: -kv[1])},
        "length_by_highway_m": {k: round(v) for k, v in sorted(by_highway.items(), key=lambda kv: -kv[1])},
        "not_within_tolerance_of_any_way_m": round(off),
    }
    if a.name_contains:
        out["name_contains"] = a.name_contains
        out["length_on_matching_names_m"] = round(on_contains)
        out["share_on_matching_names"] = round(on_contains / total, 3) if total else None
    json.dump(out, sys.stdout, indent=1)
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
