"""Writes test/fixtures/tiny.pmtiles: a three-tile MVT-typed archive, zoom 0 to 14, gzip tiles, for the
Worker's route tests (dispatch 2026-10-18-05 Part 2). The tile bodies are marker bytes, not real vector
tiles: the Worker never parses a tile, it only finds and returns it, so a body that names its own z/x/y is
what lets a test assert the right tile came back.

Run once with the pmtiles Python package (3.x):  uv run --with pmtiles python make-tiny-pmtiles.py
"""
import gzip
import os

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

HERE = os.path.dirname(os.path.abspath(__file__))
TILES = [(0, 0, 0), (14, 2651, 5776), (14, 2652, 5776)]

with open(os.path.join(HERE, "tiny.pmtiles"), "wb") as f:
    w = Writer(f)
    for z, x, y in sorted(TILES, key=lambda t: zxy_to_tileid(*t)):
        w.write_tile(zxy_to_tileid(z, x, y), gzip.compress(f"tile {z}/{x}/{y}".encode(), mtime=0))
    w.finalize(
        {
            "tile_type": TileType.MVT,
            "tile_compression": Compression.GZIP,
            "min_zoom": 0,
            "max_zoom": 14,
            "min_lon_e7": int(-124.85 * 1e7),
            "min_lat_e7": int(24.4 * 1e7),
            "max_lon_e7": int(-66.87 * 1e7),
            "max_lat_e7": int(49.6 * 1e7),
            "center_zoom": 0,
            "center_lon_e7": 0,
            "center_lat_e7": 0,
        },
        {"vector_layers": [{"id": "roads", "fields": {"kind": "String"}, "minzoom": 0, "maxzoom": 14}]},
    )
