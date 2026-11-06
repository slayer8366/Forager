#!/usr/bin/env python3
"""The OSM Standard sample from tile.openstreetmap.org over downtown Asheville, North Carolina (35.59556 N,
82.55194 W, Wikipedia's city point): the centre tile at each zoom 7..15 into osm-tiles/ (9), and at zooms 8
and 10 the other three tiles of the 2x2 block around the tile corner nearest the centre into
renders/osm-extra/ (6), 15 in all. 1.5 s apart, identifying User-Agent, never re-fetching a tile on disk.
Rebuilt 2026-11-06 (dispatch 2026-11-05-11): the earlier sample, from a place near the owner's home, was
removed on the owner's word."""
import math, os, time, urllib.request
LAT, LON = 35.59556, -82.55194
HERE = os.path.dirname(os.path.abspath(__file__))
UA = "Forager-topo-night-measurement/1.1 (+https://github.com/slayer8366/Forager; one-off 15-tile sample)"

def frac(z):
    n = 2 ** z
    return (LON + 180.0) / 360.0 * n, (1.0 - math.asinh(math.tan(math.radians(LAT))) / math.pi) / 2.0 * n

wanted = []
for z in range(7, 16):
    x, y = frac(z)
    wanted.append(("osm-tiles", z, int(x), int(y)))
for z in (8, 10):
    x, y = frac(z); cx, cy = int(x), int(y)
    dx = 1 if x - cx >= 0.5 else -1
    dy = 1 if y - cy >= 0.5 else -1
    for bx, by in ((cx + dx, cy), (cx, cy + dy), (cx + dx, cy + dy)):
        wanted.append((os.path.join("renders", "osm-extra"), z, bx, by))
for d, z, x, y in wanted:
    path = os.path.join(HERE, d, f"{z}_{x}_{y}.png")
    if os.path.exists(path):
        continue
    req = urllib.request.Request(f"https://tile.openstreetmap.org/{z}/{x}/{y}.png", headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        open(path, "wb").write(r.read())
    print(z, x, y, flush=True)
    time.sleep(1.5)
