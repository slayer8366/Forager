#!/usr/bin/env python3
"""Joins Osmosis .poly files into one, with every section of every input, for Planetiler's --polygon.

The US extract's header box spans -180 to 180, because the Aleutians cross the antimeridian, so
the bounding box alone would have Planetiler fill ocean tiles across the whole band from 15 N to
73 N. Geofabrik's .poly outlines of the US (two sections, the second the islands west of 180) and
Canada (one) limit the output to tiles that touch either country. build-usca.sh passes the joined
file as --polygon.

    merge_poly.py NAME OUT.poly IN.poly [IN.poly ...]

Each section keeps its ring, renamed NAME-<input index>-<section name> so names stay unique; a hole
(a section named with a leading "!") stays a hole. Exits non-zero, writing nothing, if an input is
not a well-formed .poly file, so a truncated download cannot silently drop a country.
"""
import os
import sys


def sections(text, path):
    lines = [l.strip() for l in text.splitlines()]
    while lines and not lines[-1]:
        lines.pop()
    if len(lines) < 2 or lines[-1] != "END":
        raise ValueError(f"{path}: does not end with END")
    out, i = [], 1  # line 0 is the file's name
    while i < len(lines) - 1:
        name = lines[i]
        if not name or name == "END":
            raise ValueError(f"{path}: line {i + 1}: expected a section name, got {name!r}")
        coords, i = [], i + 1
        while i < len(lines) - 1 and lines[i] != "END":
            parts = lines[i].split()
            if len(parts) != 2:
                raise ValueError(f"{path}: line {i + 1}: expected two coordinates, got {lines[i]!r}")
            lon, lat = (float(p) for p in parts)
            if not (-180 <= lon <= 180 and -90 <= lat <= 90):
                raise ValueError(f"{path}: line {i + 1}: coordinate out of range")
            coords.append(parts)
            i += 1
        if i >= len(lines) - 1:
            raise ValueError(f"{path}: section {name!r} has no END")
        if len(coords) < 3:
            raise ValueError(f"{path}: section {name!r} has fewer than three points")
        out.append((name, coords))
        i += 1
    if not out:
        raise ValueError(f"{path}: no sections")
    return out


def merge(name, inputs):
    """inputs: [(path, text)]. Returns the joined file's text."""
    parts = [name]
    for n, (path, text) in enumerate(inputs, 1):
        for sname, coords in sections(text, path):
            hole = sname.startswith("!")
            parts.append(("!" if hole else "") + f"{name}-{n}-{sname.lstrip('!')}")
            parts.extend(f"   {lon}   {lat}" for lon, lat in coords)
            parts.append("END")
    parts.append("END")
    return "\n".join(parts) + "\n"


def main(argv):
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2
    name, out, paths = argv[0], argv[1], argv[2:]
    try:
        inputs = [(p, open(p, encoding="ascii").read()) for p in paths]
        text = merge(name, inputs)
    except (OSError, ValueError, UnicodeDecodeError) as e:
        print(f"merge_poly: {e}", file=sys.stderr)
        return 1
    tmp = out + ".part"
    with open(tmp, "w", encoding="ascii") as f:
        f.write(text)
    os.replace(tmp, out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
