"""Writes test/fixtures/live-build.pmtiles: a two-tile MVT-typed archive at z15, gzip tiles, standing in for
Protomaps' live daily build, which the Worker's z15 overflow reads and copies into R2 (dispatch 2026-10-22-02,
from Sorrel's follow-up review of PR #376, docs/audits/2026-10-22-review-pr376-pr377-followup.md, first Low).
Before this fixture the tests served tiny.pmtiles as the live build; it stops at z14, so every z15 tile answered
404 before the overflow's R2 write, and no test could see a write, or the absence of one.

As in make-tiny-pmtiles.py, the bodies are marker bytes naming their own z/x/y, so a test can tell which tile came
back. What each tile is for:
  15/5303/11553  Portland (north edge 46.79°N), inside us.pmtiles's own bounds: the overflow serves it with 200 and
                 writes it into R2 under overflow/, the write path's first test.
  15/9641/15964  Bogotá (north edge 4.61°N), south of us.pmtiles's own bounds: must stay 204 with no Protomaps
                 read and no R2 write (Amendment 4). This tile is what lets that test's "no write" fail: without
                 it, a Worker that still ran the overflow there would find no tile and write nothing anyway.

The writer takes the header's min and max zoom from the tiles written, so the archive reads as z15 to z15, whatever
min_zoom says below; the overflow checks only that z15 is in range.

Run once with the pmtiles Python package (written with 3.4.1):
  uv run --with pmtiles==3.4.1 python make-live-build-pmtiles.py
"""
import gzip
import os

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

HERE = os.path.dirname(os.path.abspath(__file__))
TILES = [(15, 5303, 11553), (15, 9641, 15964)]

with open(os.path.join(HERE, "live-build.pmtiles"), "wb") as f:
    w = Writer(f)
    for z, x, y in sorted(TILES, key=lambda t: zxy_to_tileid(*t)):
        w.write_tile(zxy_to_tileid(z, x, y), gzip.compress(f"live {z}/{x}/{y}".encode(), mtime=0))
    w.finalize(
        {
            "tile_type": TileType.MVT,
            "tile_compression": Compression.GZIP,
            "min_zoom": 0,
            "max_zoom": 15,
            "min_lon_e7": int(-180 * 1e7),
            "min_lat_e7": int(-85 * 1e7),
            "max_lon_e7": int(180 * 1e7),
            "max_lat_e7": int(85 * 1e7),
            "center_zoom": 0,
            "center_lon_e7": 0,
            "center_lat_e7": 0,
        },
        {"vector_layers": [{"id": "roads", "fields": {"kind": "String"}, "minzoom": 0, "maxzoom": 15}]},
    )
