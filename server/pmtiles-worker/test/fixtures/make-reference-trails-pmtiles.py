"""Writes test/fixtures/reference-trails.pmtiles: a synthetic stand-in for the agency-trails archive the step 8
reference layer will read (dispatch 2026-10-26-14, step 8 report section 7: `reference-trails.pmtiles`, source-layers
`fs_trails` and `blm_trails`, zooms 10 to 15). Nothing in it is real: the positions are invented, and as in
make-tiny-pmtiles.py the bodies are marker bytes naming their own z/x/y, so a test can tell which archive answered.

What each tile is for:
  10/163/360    the bottom of the archive's range: served from here.
  15/5303/11553 the top of the range. live-build.pmtiles holds the same tile, so a z15 answer that came from the
                overflow path (Protomaps' live build) would read "live ...", not "reference ...".
Not here, on purpose:
  14/2651/5776  tiny.pmtiles (us) holds it: a reference request for it must be 204, never us's tile or the world's.

Run once with the pmtiles Python package (3.x):  uv run --with pmtiles python make-reference-trails-pmtiles.py
"""
import gzip
import os

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

HERE = os.path.dirname(os.path.abspath(__file__))
TILES = [(10, 163, 360), (15, 5303, 11553)]

with open(os.path.join(HERE, "reference-trails.pmtiles"), "wb") as f:
    w = Writer(f)
    for z, x, y in sorted(TILES, key=lambda t: zxy_to_tileid(*t)):
        w.write_tile(zxy_to_tileid(z, x, y), gzip.compress(f"reference {z}/{x}/{y}".encode(), mtime=0))
    w.finalize(
        {
            "tile_type": TileType.MVT,
            "tile_compression": Compression.GZIP,
            "min_zoom": 10,
            "max_zoom": 15,
            # An invented box, inside the continental one tiny.pmtiles declares.
            "min_lon_e7": int(-123.0 * 1e7),
            "min_lat_e7": int(42.0 * 1e7),
            "max_lon_e7": int(-120.0 * 1e7),
            "max_lat_e7": int(46.0 * 1e7),
            "center_zoom": 10,
            "center_lon_e7": int(-121.5 * 1e7),
            "center_lat_e7": int(44.0 * 1e7),
        },
        {
            "vector_layers": [
                {"id": "fs_trails", "fields": {"trail_no": "String", "trail_type": "String"}, "minzoom": 10, "maxzoom": 15},
                {"id": "blm_trails", "fields": {}, "minzoom": 10, "maxzoom": 15},
            ]
        },
    )
