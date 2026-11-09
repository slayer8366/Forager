#!/usr/bin/env python3
"""One centre tile per zoom 7..15 of tile.openstreetmap.org over Oregon City (9 tiles), 1.5 s apart,
identifying User-Agent. Report-only evidence for dispatch 2026-09-28-310 item 2 (does OSM Standard
share the low-zoom problem?)."""
import math, os, time, urllib.request
LAT, LON = 45.36, -122.6
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "osm-tiles")
UA = "Forager-topo-night-measurement/1.0 (armyofwann@gmail.com; one-off 9-tile sample)"
for z in range(7, 16):
    n = 2 ** z
    x = int((LON + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(LAT))) / math.pi) / 2.0 * n)
    path = os.path.join(OUT, f"{z}_{x}_{y}.png")
    if os.path.exists(path):
        continue
    req = urllib.request.Request(f"https://tile.openstreetmap.org/{z}/{x}/{y}.png", headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        open(path, "wb").write(r.read())
    print(z, x, y, flush=True)
    time.sleep(1.5)
