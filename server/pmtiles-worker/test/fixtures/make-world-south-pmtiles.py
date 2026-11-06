"""Writes test/fixtures/world-south.pmtiles: a small MVT-typed archive, zoom 0 to 9, gzip tiles, standing in for
the world-south.pmtiles archive the Worker reads where us.pmtiles has no tile (dispatch 2026-10-22-01 Part A,
RECORD -1554). As in make-tiny-pmtiles.py, the bodies are marker bytes naming their own z/x/y, so a test can
tell which archive answered.

What each tile is for:
  0/0/0      also in tiny.pmtiles: the us tile must still win, the world one never read.
  5/9/16     missing from tiny.pmtiles: filled from here.
  8/75/130   missing from tiny.pmtiles, at the top of the fill's range (z8): filled from here.
  9/150/260  missing from tiny.pmtiles and present here, but above z8: must stay 204. This tile is what lets
             the z9 test fail if the zoom cap is removed; without it, z9 would be 204 for want of a tile.

Run once with the pmtiles Python package (3.x):  uv run --with pmtiles python make-world-south-pmtiles.py
"""
import gzip
import os

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

HERE = os.path.dirname(os.path.abspath(__file__))
TILES = [(0, 0, 0), (5, 9, 16), (8, 75, 130), (9, 150, 260)]

with open(os.path.join(HERE, "world-south.pmtiles"), "wb") as f:
    w = Writer(f)
    for z, x, y in sorted(TILES, key=lambda t: zxy_to_tileid(*t)):
        w.write_tile(zxy_to_tileid(z, x, y), gzip.compress(f"world {z}/{x}/{y}".encode(), mtime=0))
    w.finalize(
        {
            "tile_type": TileType.MVT,
            "tile_compression": Compression.GZIP,
            "min_zoom": 0,
            "max_zoom": 9,
            "min_lon_e7": int(-180 * 1e7),
            "min_lat_e7": int(-85 * 1e7),
            "max_lon_e7": int(180 * 1e7),
            "max_lat_e7": int(16 * 1e7),
            "center_zoom": 0,
            "center_lon_e7": 0,
            "center_lat_e7": 0,
        },
        {"vector_layers": [{"id": "earth", "fields": {"kind": "String"}, "minzoom": 0, "maxzoom": 9}]},
    )
