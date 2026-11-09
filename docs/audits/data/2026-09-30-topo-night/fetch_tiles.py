#!/usr/bin/env python3
"""One-off fetch of a fixed OpenTopoMap tile sample (dispatch 2026-09-28-310).

3x3 block centred on Oregon City (45.36 N, 122.6 W) at each zoom 7..15 = 81 tiles, one request per
1.5 s, identifying User-Agent, never re-fetching a tile already on disk. Within OpenTopoMap's usage
policy (no bulk download; identify the app).
"""
import math, os, sys, time, urllib.request

LAT, LON = 45.36, -122.6
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tiles")
UA = "Forager-topo-night-measurement/1.0 (armyofwann@gmail.com; one-off 81-tile sample)"

def tile(lat, lon, z):
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n)
    return x, y

for z in range(7, 16):
    cx, cy = tile(LAT, LON, z)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            x, y = cx + dx, cy + dy
            path = os.path.join(OUT, f"{z}_{x}_{y}.png")
            if os.path.exists(path):
                continue
            req = urllib.request.Request(f"https://a.tile.opentopomap.org/{z}/{x}/{y}.png", headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            with open(path, "wb") as f:
                f.write(data)
            print(z, x, y, len(data), flush=True)
            time.sleep(1.5)
