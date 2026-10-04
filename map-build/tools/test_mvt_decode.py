#!/usr/bin/env python3
"""Tests for mvt_decode.py, on tiles encoded by hand here, so no map data is needed.

Run: python3 -m unittest map-build/tools/test_mvt_decode.py
"""
import gzip
import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mvt_decode  # noqa: E402


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def field(num, wire, payload):
    key = varint((num << 3) | wire)
    if wire == 0:
        return key + varint(payload)
    if wire == 2:
        return key + varint(len(payload)) + payload
    return key + payload  # fixed32 / fixed64, already packed


def value(v):
    if isinstance(v, bool):
        return field(7, 0, int(v))
    if isinstance(v, str):
        return field(1, 2, v.encode())
    if isinstance(v, float):
        return field(3, 1, struct.pack("<d", v))
    if v < 0:
        return field(6, 0, (v << 1) ^ (v >> 63))
    return field(5, 0, v)


def layer(name, features, extent=4096):
    """features: list of (id, geometry type, {key: value}); geometry is a fixed dummy line."""
    keys, values, body = [], [], b""
    for fid, gtype, props in features:
        tags = []
        for k, v in props.items():
            if k not in keys:
                keys.append(k)
            if v not in values or any(type(x) is not type(v) for x in values if x == v):
                values.append(v)
            tags += [keys.index(k), max(i for i, x in enumerate(values) if x == v and type(x) is type(v))]
        geometry = b"".join(varint(x) for x in (9, 0, 0, 10, 2, 2))  # MoveTo(0,0) LineTo(1,1)
        feat = field(1, 0, fid) + field(2, 2, b"".join(varint(t) for t in tags)) + field(3, 0, gtype)
        feat += field(4, 2, geometry)
        body += field(2, 2, feat)
    out = field(15, 0, 2) + field(1, 2, name.encode()) + body
    out += b"".join(field(3, 2, k.encode()) for k in keys)
    out += b"".join(field(4, 2, value(v)) for v in values)
    return out + field(5, 0, extent)


def tile(*layers):
    return b"".join(field(3, 2, lyr) for lyr in layers)


ROADS = layer("roads", [
    (7, 2, {"kind": "path", "kind_detail": "path", "sac_scale": "hiking", "min_zoom": 14}),
    (8, 2, {"kind": "path", "kind_detail": "track", "tracktype": "grade3", "is_bridge": True}),
    (9, 2, {"kind": "minor_road", "kind_detail": "residential", "sort_rank": -3, "width": 2.5}),
])
WATER = layer("water", [(1, 3, {"kind": "lake"})])


class DecodeTest(unittest.TestCase):

    def test_layers_features_and_typed_values(self):
        layers = mvt_decode.decode(tile(ROADS, WATER))
        self.assertEqual([lyr["name"] for lyr in layers], ["roads", "water"])
        self.assertEqual(layers[0]["version"], 2)
        self.assertEqual(layers[0]["extent"], 4096)
        self.assertEqual(layers[0]["features"], [
            {"id": 7, "type": "linestring",
             "properties": {"kind": "path", "kind_detail": "path", "sac_scale": "hiking", "min_zoom": 14}},
            {"id": 8, "type": "linestring",
             "properties": {"kind": "path", "kind_detail": "track", "tracktype": "grade3", "is_bridge": True}},
            {"id": 9, "type": "linestring",
             "properties": {"kind": "minor_road", "kind_detail": "residential", "sort_rank": -3, "width": 2.5}},
        ])
        self.assertEqual(layers[1]["features"], [{"id": 1, "type": "polygon", "properties": {"kind": "lake"}}])

    def test_gzip_tile_decodes_the_same(self):
        raw = tile(ROADS, WATER)
        self.assertEqual(mvt_decode.decode(gzip.compress(raw)), mvt_decode.decode(raw))

    def test_output_carries_no_geometry(self):
        for lyr in mvt_decode.decode(tile(ROADS)):
            for feature in lyr["features"]:
                self.assertEqual(set(feature), {"id", "type", "properties"})

    def test_schema(self):
        self.assertEqual(mvt_decode.schema(mvt_decode.decode(tile(ROADS, WATER))), {
            "roads": {
                "features": 3,
                "keys": ["is_bridge", "kind", "kind_detail", "min_zoom", "sac_scale", "sort_rank", "tracktype",
                         "width"],
                "kind": ["minor_road", "path"],
                "kind_detail": ["path", "residential", "track"],
            },
            "water": {"features": 1, "keys": ["kind"], "kind": ["lake"], "kind_detail": []},
        })

    def test_compare_ignores_named_keys_and_reports_the_rest(self):
        old = mvt_decode.schema(mvt_decode.decode(tile(layer("roads", [(1, 2, {"kind": "path"})]), WATER)))
        new = mvt_decode.schema(mvt_decode.decode(tile(
            layer("roads", [(1, 2, {"kind": "path", "sac_scale": "hiking"})]), WATER)))
        self.assertEqual(mvt_decode.compare(old, new, ["sac_scale"]), [])
        self.assertEqual(mvt_decode.compare(old, new), ["layer roads keys: only in new: ['sac_scale']"])
        renamed = mvt_decode.schema(mvt_decode.decode(tile(layer("roads", [(1, 2, {"kind": "trail"})]))))
        self.assertEqual(mvt_decode.compare(old, renamed), [
            "layer roads kind: only in old: ['path']",
            "layer roads kind: only in new: ['trail']",
            "layer water: only in old",
        ])

    def test_truncated_tile_is_an_error_not_an_empty_result(self):
        with self.assertRaises(ValueError):
            mvt_decode.decode(tile(ROADS)[:-3])


if __name__ == "__main__":
    unittest.main()
