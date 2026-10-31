"""Writes test/fixtures/world.pmtiles: a small MVT-typed archive, zoom 0 to 15, gzip tiles, standing in for the
world.pmtiles archive (a Protomaps daily planet, z0-15) the Worker reads first where us.pmtiles has no tile
(dispatch 2026-10-31-coder-08 Part A). As in make-world-south-pmtiles.py, the bodies are marker bytes naming their
own z/x/y and archive, so a test can tell which archive answered.

What each tile is for:
  0/0/0            also in tiny.pmtiles and world-south.pmtiles: the us tile must still win.
  5/9/16           also in world-south.pmtiles: world.pmtiles is read first, so it answers.
  12/2634/1750     Doha at z12, missing from tiny.pmtiles and above world-south's z8: only world.pmtiles has it.
  15/21074/14003   Doha at z15: the top of the planet's range.
  13/3034/4647     São Paulo at z13, south of us.pmtiles's own bounds: served from here, never from the live
                   overflow (which answers only above us.pmtiles's maxzoom).

Run once with the pmtiles Python package (3.x):  uv run --with pmtiles python make-world-pmtiles.py
"""
import gzip
import os

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

HERE = os.path.dirname(os.path.abspath(__file__))
TILES = [(0, 0, 0), (5, 9, 16), (12, 2634, 1750), (13, 3034, 4647), (15, 21074, 14003)]

with open(os.path.join(HERE, "world.pmtiles"), "wb") as f:
    w = Writer(f)
    for z, x, y in sorted(TILES, key=lambda t: zxy_to_tileid(*t)):
        w.write_tile(zxy_to_tileid(z, x, y), gzip.compress(f"planet {z}/{x}/{y}".encode(), mtime=0))
    w.finalize(
        {
            "tile_type": TileType.MVT,
            "tile_compression": Compression.GZIP,
            "min_zoom": 0,
            "max_zoom": 15,
            "min_lon_e7": int(-180 * 1e7),
            "min_lat_e7": int(-85.051129 * 1e7),
            "max_lon_e7": int(180 * 1e7),
            "max_lat_e7": int(85.051129 * 1e7),
            "center_zoom": 0,
            "center_lon_e7": 0,
            "center_lat_e7": 0,
        },
        {"vector_layers": [{"id": "earth", "fields": {"kind": "String"}, "minzoom": 0, "maxzoom": 15}]},
    )
