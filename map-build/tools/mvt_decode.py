#!/usr/bin/env python3
"""Decode a Mapbox Vector Tile's layers and feature properties, with no dependencies.

Used to verify Forager's map build (dispatch 2026-09-28-495). It never prints geometry, so its
output can go into a report without carrying positions. Gzip-compressed tiles are detected and
decompressed.

    mvt_decode.py features TILE [--layer roads] [--has sac_scale,trail_visibility]
    mvt_decode.py schema TILE
    mvt_decode.py compare OLD NEW [--ignore-keys k1,k2]

`schema` gives, per layer, the attribute keys present and the values of `kind` and `kind_detail`.
`compare` reports layers, keys and kind values present in one tile and not the other, and exits 1
if any differ, so it can gate a check; OSM edits between two builds can make real differences
appear, which a person then reads.
"""
import argparse
import gzip
import json
import struct
import sys

GEOM_TYPES = {0: "unknown", 1: "point", 2: "linestring", 3: "polygon"}


def _varint(buf, pos):
    result = shift = 0
    while True:
        if pos >= len(buf):
            raise ValueError("truncated varint")
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7
        if shift > 70:
            raise ValueError("varint too long")


def _fields(buf):
    """Yields (field number, wire type, value) for one protobuf message."""
    pos = 0
    while pos < len(buf):
        key, pos = _varint(buf, pos)
        field, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = _varint(buf, pos)
        elif wire == 1:
            value, pos = buf[pos:pos + 8], pos + 8
        elif wire == 2:
            length, pos = _varint(buf, pos)
            value, pos = buf[pos:pos + length], pos + length
        elif wire == 5:
            value, pos = buf[pos:pos + 4], pos + 4
        else:
            raise ValueError(f"unsupported wire type {wire}")
        if pos > len(buf):
            raise ValueError("truncated field")
        yield field, wire, value


def _packed(buf):
    pos, out = 0, []
    while pos < len(buf):
        value, pos = _varint(buf, pos)
        out.append(value)
    return out


def _value(buf):
    for field, _, raw in _fields(buf):
        if field == 1:
            return raw.decode("utf-8")
        if field == 2:
            return struct.unpack("<f", raw)[0]
        if field == 3:
            return struct.unpack("<d", raw)[0]
        if field == 4:
            return raw - (1 << 64) if raw >= 1 << 63 else raw
        if field == 5:
            return raw
        if field == 6:
            return (raw >> 1) ^ -(raw & 1)
        if field == 7:
            return bool(raw)
    return None


def decode(data):
    """Returns [{name, version, extent, features: [{id, type, properties}]}], geometry omitted."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    layers = []
    for field, _, raw in _fields(data):
        if field != 3:
            continue
        name, version, extent, keys, values, features = None, 1, 4096, [], [], []
        for lf, _, lv in _fields(raw):
            if lf == 1:
                name = lv.decode("utf-8")
            elif lf == 15:
                version = lv
            elif lf == 5:
                extent = lv
            elif lf == 3:
                keys.append(lv.decode("utf-8"))
            elif lf == 4:
                values.append(_value(lv))
            elif lf == 2:
                features.append(lv)
        decoded = []
        for fbuf in features:
            fid, ftype, tags = None, 0, []
            for ff, _, fv in _fields(fbuf):
                if ff == 1:
                    fid = fv
                elif ff == 3:
                    ftype = fv
                elif ff == 2:
                    tags = _packed(fv)
            if len(tags) % 2:
                raise ValueError(f"layer {name}: odd tag count")
            props = {keys[tags[i]]: values[tags[i + 1]] for i in range(0, len(tags), 2)}
            decoded.append({"id": fid, "type": GEOM_TYPES.get(ftype, str(ftype)), "properties": props})
        layers.append({"name": name, "version": version, "extent": extent, "features": decoded})
    return layers


def schema(layers):
    out = {}
    for layer in layers:
        entry = out.setdefault(layer["name"], {"keys": set(), "kind": set(), "kind_detail": set(), "features": 0})
        for feature in layer["features"]:
            entry["features"] += 1
            entry["keys"].update(feature["properties"])
            for k in ("kind", "kind_detail"):
                if k in feature["properties"]:
                    entry[k].add(str(feature["properties"][k]))
    return {name: {k: (sorted(v) if isinstance(v, set) else v) for k, v in e.items()} for name, e in out.items()}


def compare(old, new, ignore_keys=()):
    """Differences between two schemas, ignoring the named keys. Empty when they match."""
    diffs = []
    for name in sorted(set(old) | set(new)):
        if name not in new:
            diffs.append(f"layer {name}: only in old")
            continue
        if name not in old:
            diffs.append(f"layer {name}: only in new")
            continue
        for part in ("keys", "kind", "kind_detail"):
            a = set(old[name][part]) - set(ignore_keys if part == "keys" else ())
            b = set(new[name][part]) - set(ignore_keys if part == "keys" else ())
            if a - b:
                diffs.append(f"layer {name} {part}: only in old: {sorted(a - b)}")
            if b - a:
                diffs.append(f"layer {name} {part}: only in new: {sorted(b - a)}")
    return diffs


def _read(path):
    if path == "-":
        return sys.stdin.buffer.read()
    with open(path, "rb") as f:
        return f.read()


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("features")
    f.add_argument("tile")
    f.add_argument("--layer")
    f.add_argument("--has", default="", help="comma-separated keys; keep features carrying any of them")
    s = sub.add_parser("schema")
    s.add_argument("tile")
    c = sub.add_parser("compare")
    c.add_argument("old")
    c.add_argument("new")
    c.add_argument("--ignore-keys", default="")
    args = p.parse_args(argv)

    if args.cmd == "features":
        wanted = [k for k in args.has.split(",") if k]
        out = []
        for layer in decode(_read(args.tile)):
            if args.layer and layer["name"] != args.layer:
                continue
            for feature in layer["features"]:
                if wanted and not any(k in feature["properties"] for k in wanted):
                    continue
                out.append({"layer": layer["name"], **feature})
        json.dump(out, sys.stdout, indent=1, sort_keys=True)
        print()
        return 0
    if args.cmd == "schema":
        json.dump(schema(decode(_read(args.tile))), sys.stdout, indent=1, sort_keys=True)
        print()
        return 0
    ignore = [k for k in args.ignore_keys.split(",") if k]
    diffs = compare(schema(decode(_read(args.old))), schema(decode(_read(args.new))), ignore)
    for d in diffs:
        print(d)
    print(f"{len(diffs)} difference(s)")
    return 1 if diffs else 0


if __name__ == "__main__":
    sys.exit(main())
