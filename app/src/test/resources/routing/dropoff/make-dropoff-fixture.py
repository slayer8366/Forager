#!/usr/bin/env python3
"""Writes the drop-off fixture: an invented street block, at sea, as OpenStreetMap XML and as one zoom-15 map tile.

The fixture is invented. No OpenStreetMap data is in it, and no real place: every way, house, fence and stream is drawn
here, in metres from an origin in the open Atlantic about 100 km south of Block Island, where OpenStreetMap has nothing.
See README.md beside this file for what each case is for.

    python3 make-dropoff-fixture.py <out dir>

writes <out dir>/dropoff.osm (for BRouter's map creator, make-dropoff-rd5.sh), <out dir>/points.json (the named points
the tests read) and <out dir>/15/<x>/<y>.mvt (the map
tile, uncompressed, in the layers and fields the Forager basemap build writes: README.md, "The tile"). Python 3
standard library only. The output is the same bytes on every run: nothing here reads the clock or a random source.
"""
import math
import os
import struct
import sys

ZOOM = 15
EXTENT = 4096
N = 2 ** ZOOM
# The zoom-15 tile everything is drawn in, and the origin: the tile's centre, moved 325 m south so the 750 m the fixture
# spans north to south (-50 m to 700 m) sits in the middle of the tile's 934 m.
TILE_X, TILE_Y = 9875, 12393
M_PER_DEG = 111195.0


def tile_lon(x):
    return x / N * 360 - 180


def tile_lat(y):
    return math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / N))))


CENTRE_LAT = (tile_lat(TILE_Y) + tile_lat(TILE_Y + 1)) / 2
CENTRE_LON = (tile_lon(TILE_X) + tile_lon(TILE_X + 1)) / 2
ORIGIN_LAT = CENTRE_LAT - 325.0 / M_PER_DEG
ORIGIN_LON = CENTRE_LON


def latlon(p):
    """Metres east and north of the origin, to a position rounded to OpenStreetMap's 7 decimals."""
    x, y = p
    lat = ORIGIN_LAT + y / M_PER_DEG
    lon = ORIGIN_LON + x / (M_PER_DEG * math.cos(math.radians(ORIGIN_LAT)))
    return round(lat, 7), round(lon, 7)


def box(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0)]


# --- The block -------------------------------------------------------------------------------------------------------
# Public streets, all highway=residential.
FRONT_ROAD = [(-300, 0), (-230, 0), (0, 0), (20, 0), (240, 0), (300, 0)]
WEST_ROAD = [(-300, 0), (-300, 60), (-300, 140), (-300, 300)]
BACK_LANE = [(-300, 140), (0, 140)]  # a dead end, 65 m behind house A
EAST_LANE = [(300, 0), (300, 150)]  # a dead end, 60 m east of house B
# Case A's private driveway, from Front Road at (20, 0) to house A's door.
DRIVEWAY_A = [(20, 0), (20, 60), (8, 70)]
# Case D's trail through the wood: up from West Road's north end, then east.
TRAIL = [(-300, 300), (-300, 550), (0, 550), (300, 550)]

WAYS = [
    ("Front Road", FRONT_ROAD, {"highway": "residential", "name": "Front Road"}),
    ("West Road", WEST_ROAD, {"highway": "residential", "name": "West Road"}),
    ("Back Lane", BACK_LANE, {"highway": "residential", "name": "Back Lane"}),
    ("East Lane", EAST_LANE, {"highway": "residential", "name": "East Lane"}),
    ("driveway A", DRIVEWAY_A, {"highway": "service", "service": "driveway", "access": "private"}),
    ("trail", TRAIL, {"highway": "path", "surface": "ground"}),
]

# Houses: (label, outline, street, number). The pins are each target house's centre.
HOUSES = [
    ("house A", box(-8, 67, 8, 83), "Front Road", "12"),  # case A, the target
    ("neighbour A", box(-8, 100, 8, 116), "Back Lane", "3"),  # between Back Lane's end and house A
    ("house B", box(232, 67, 248, 83), "Front Road", "40"),  # case B, the target
    ("house C", box(-238, 52, -222, 68), "West Road", "7"),  # case C, the target
]
# Case B: a fence between house B and East Lane.
FENCES = [("fence B", [(270, 40), (270, 110)], {"barrier": "fence"})]
# Case C: a stream between house C and Front Road, and a stream in a culvert between house C and West Road.
# Case D2: a stream between pin D2 and the trail.
STREAMS = [
    ("stream C", [(-280, 30), (-180, 30)], {"waterway": "stream"}),
    ("culvert C", [(-260, 45), (-260, 75)], {"waterway": "stream", "tunnel": "culvert"}),
    ("stream D2", [(100, 535), (200, 535)], {"waterway": "stream"}),
]
# Named points the tests read from points.json, so no position is written twice. The pins are house centres, or in the
# wood for the trail cases; "engine" is where BRouter alone ends a route to that pin (seen on 2026-10-28); "dropoff" is
# where the street drop-off must end it.
POINTS = {
    "walker": (-290, 250),
    "pin_a": (0, 75), "engine_a": (0, 140), "dropoff_a": (20, 0),
    "pin_b": (240, 75), "engine_b": (300, 75), "dropoff_b": (240, 0),
    "pin_c": (-230, 60), "engine_c": (-230, 0), "dropoff_c": (-300, 60),
    "pin_d": (0, 575), "engine_d": (0, 550),
    "pin_d2": (150, 520), "engine_d2": (150, 550),
    "on_driveway_a": (20, 30),
}
AREAS = [
    ("residential", box(-320, -50, 320, 200), {"landuse": "residential"}),
    ("wood", box(-320, 400, 320, 700), {"landuse": "forest"}),
]

# --- OpenStreetMap XML -----------------------------------------------------------------------------------------------


def write_osm(path):
    node_ids = {}
    nodes = []

    def nid(p):
        if p not in node_ids:
            node_ids[p] = 1000 + len(node_ids)
            nodes.append(p)
        return node_ids[p]

    ways = []

    def way(points, tags):
        ways.append(([nid(p) for p in points], tags))

    for _, points, tags in WAYS:
        way(points, tags)
    for _, outline, street, number in HOUSES:
        way(outline, {"building": "house", "addr:street": street, "addr:housenumber": number})
    for _, points, tags in FENCES + STREAMS:
        way(points, tags)
    for _, outline, tags in AREAS:
        way(outline, tags)
    stamp = 'version="1" timestamp="2026-10-28T00:00:00Z" changeset="1" uid="1" user="fixture"'
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("<?xml version='1.0' encoding='UTF-8'?>\n<osm version=\"0.6\" generator=\"make-dropoff-fixture.py\">\n")
        for p in nodes:
            lat, lon = latlon(p)
            f.write(f'  <node id="{node_ids[p]}" {stamp} lat="{lat:.7f}" lon="{lon:.7f}"/>\n')
        for i, (refs, tags) in enumerate(ways):
            f.write(f'  <way id="{2000 + i}" {stamp}>\n')
            for r in refs:
                f.write(f'    <nd ref="{r}"/>\n')
            for k in sorted(tags):
                f.write(f'    <tag k="{k}" v="{tags[k]}"/>\n')
            f.write("  </way>\n")
        f.write("</osm>\n")


# --- The map tile ----------------------------------------------------------------------------------------------------


def varint(v):
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def field(number, wire, payload):
    key = varint((number << 3) | wire)
    if wire == 0:
        return key + varint(payload)
    return key + varint(len(payload)) + payload


def zigzag(v):
    return (v << 1) ^ (v >> 31)


def tile_xy(p):
    lat, lon = latlon(p)
    tx = ((lon + 180) / 360 * N - TILE_X) * EXTENT
    ty = ((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * N - TILE_Y) * EXTENT
    return int(round(tx)), int(round(ty))


MARK = "mark"


def geometry(points, kind):
    """MVT commands for a point (1), line (2) or polygon ring (3, closed by ClosePath, not by a repeated point).
    MARK is basemap patch 0004's barrier mark: one point at the tile's corner (0, 0)."""
    if points == MARK:
        return [(1 << 3) | 1, 0, 0]
    pts = [tile_xy(p) for p in points]
    if kind == 3:
        pts = pts[:-1]
    out, cx, cy = [], 0, 0
    for i, (x, y) in enumerate(pts):
        if i == 0:
            out.append((1 << 3) | 1)
        elif i == 1:
            out.append(((len(pts) - 1) << 3) | 2)
        out += [zigzag(x - cx), zigzag(y - cy)]
        cx, cy = x, y
    if kind == 3:
        out.append((1 << 3) | 7)
    return out


def value(v):
    """A string, or a non-negative integer as uint64 (every integer here is one)."""
    if isinstance(v, str):
        return field(1, 2, v.encode())
    assert v >= 0
    return field(5, 0, v)


def layer(name, features):
    """features: (geometry kind, points, {key: value}) in order. Keys and values are listed in first-use order."""
    keys, values, body = [], [], b""
    for i, (kind, points, props) in enumerate(features):
        tags = []
        for k, v in props.items():
            if k not in keys:
                keys.append(k)
            if v not in values:
                values.append(v)
            tags += [keys.index(k), values.index(v)]
        feature = (field(1, 0, i + 1) if points != MARK else b"") + (field(2, 2, b"".join(varint(t) for t in tags)) if tags else b"") + field(3, 0, kind)
        feature += field(4, 2, b"".join(varint(c) for c in geometry(points, kind)))
        body += field(2, 2, feature)
    out = field(15, 0, 2) + field(1, 2, name.encode()) + body
    out += b"".join(field(3, 2, k.encode()) for k in keys)
    out += b"".join(field(4, 2, value(v)) for v in values)
    return out + field(5, 0, EXTENT)


def centroid(outline):
    pts = outline[:-1]
    return (sum(x for x, _ in pts) / len(pts), sum(y for _, y in pts) / len(pts))


def write_tile(path, before_0003=False):
    """The tile as the 11-01 build writes it (patches 0003 and 0004), or, with before_0003, as every build before it did:
    no barriers layer, no addr_street, no tunnel on water lines."""
    buildings = [(3, outline, {"kind": "building", "sort_rank": 400}) for _, outline, _, _ in HOUSES]
    buildings += [
        (1, [centroid(outline)], {"kind": "address", "addr_housenumber": number, "addr_street": street})
        for _, outline, street, number in HOUSES
    ]
    water = [(2, pts, {"kind": tags["waterway"], "min_zoom": 13, "sort_rank": 200, **({"tunnel": tags["tunnel"]} if "tunnel" in tags else {})})
             for _, pts, tags in STREAMS]
    landuse = [(3, outline, {"kind": tags["landuse"], "sort_rank": 150}) for _, outline, tags in AREAS]
    barriers = [(2, pts, {"kind": tags["barrier"]}) for _, pts, tags in FENCES] + [(1, MARK, {})]
    if before_0003:
        buildings = [(k, pts, {f: v for f, v in props.items() if f != "addr_street"}) for k, pts, props in buildings]
        water = [(k, pts, {f: v for f, v in props.items() if f != "tunnel"}) for k, pts, props in water]
    roads = [(2, pts, {"kind": "minor_road", "kind_detail": tags["highway"], **({"name": tags["name"]} if "name" in tags else {}),
                       **({"access": tags["access"]} if "access" in tags else {})})
             for _, pts, tags in WAYS if tags["highway"] != "path"]
    roads += [(2, TRAIL, {"kind": "path", "kind_detail": "path"})]
    layers = [("landuse", landuse), ("water", water), ("roads", roads), ("buildings", buildings)] + ([] if before_0003 else [("barriers", barriers)])
    tile = b"".join(field(3, 2, layer(name, feats)) for name, feats in layers)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(tile)


def write_points(path):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("{\n")
        rows = [f'  "{name}": {{"x": {p[0]}, "y": {p[1]}, "lat": {latlon(p)[0]:.7f}, "lng": {latlon(p)[1]:.7f}}}' for name, p in POINTS.items()]
        f.write(",\n".join(rows) + "\n}\n")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    write_osm(os.path.join(out, "dropoff.osm"))
    write_points(os.path.join(out, "points.json"))
    write_tile(os.path.join(out, str(ZOOM), str(TILE_X), f"{TILE_Y}.mvt"))
    write_tile(os.path.join(out, str(ZOOM), str(TILE_X), f"{TILE_Y}.before-0003.mvt"), before_0003=True)
    print(f"origin {ORIGIN_LAT:.7f},{ORIGIN_LON:.7f}; tile {ZOOM}/{TILE_X}/{TILE_Y}")
    for label, outline, _, _ in HOUSES:
        lat, lon = latlon(centroid(outline))
        print(f"{label}: centre {lat:.7f},{lon:.7f}")


if __name__ == "__main__":
    main()
