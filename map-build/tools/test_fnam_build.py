#!/usr/bin/env python3
"""Tests for fnam_build.py (dispatch 2026-10-26-pi-31), run through its real command line where
the files are concerned. The files are read back by a decoder written here from the format in the
dispatch, not by the encoder's own code.

Run: python3 -m unittest map-build/tools/test_fnam_build.py
"""
import json
import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "fnam_build.py")
sys.path.insert(0, HERE)
import fnam_build  # noqa: E402  (simplify, tested directly)

TS = "2026-10-24T20:20:51Z"
TS_UNIX = 1792873251  # date -u -d 2026-10-24T20:20:51Z +%s


# --- a decoder, from the dispatch's FNAM v1 -------------------------------------------------------

class Reader:
    def __init__(self, data, pos, end):
        self.data, self.pos, self.end = data, pos, end

    def varint(self):
        n = shift = 0
        while True:
            assert self.pos < self.end, "varint past the end of its section"
            b = self.data[self.pos]
            self.pos += 1
            n |= (b & 0x7F) << shift
            if b < 0x80:
                return n
            shift += 7

    def byte(self):
        assert self.pos < self.end
        self.pos += 1
        return self.data[self.pos - 1]

    def bytes(self, n):
        assert self.pos + n <= self.end
        self.pos += n
        return self.data[self.pos - n:self.pos]


def unzz(n):
    return (n >> 1) ^ -(n & 1)


def decode(path):
    """{"header": {...}, "strings": [...], "highways": [...], "dir": [(off, len)] * 1600,
    "records": [{"cell", "row", "col", "name", "ref", "highway", "points"}]}, points in 1e-6 deg."""
    with open(path, "rb") as f:
        data = f.read()
    magic, version, west, south, cells, reserved, ts, toff, doff, roff = struct.unpack_from("<4sHhhBBqIII", data, 0)
    header = dict(magic=magic, version=version, west=west, south=south, cells=cells, reserved=reserved,
                  timestamp=ts, tables=toff, directory=doff, records=roff, size=len(data))
    r = Reader(data, toff, doff)
    strings = [r.bytes(r.varint()).decode("utf-8") for _ in range(r.varint())]
    highways = [r.bytes(r.varint()).decode("utf-8") for _ in range(r.varint())]
    assert r.pos == doff, "the tables do not end at the directory"
    assert roff == doff + 1600 * 8
    directory = [struct.unpack_from("<II", data, doff + 8 * i) for i in range(1600)]
    records = []
    for i, (off, length) in enumerate(directory):
        if not length:
            continue
        row, col = divmod(i, 40)
        cx, cy = round((west + col / 8) * 1e6), round((south + row / 8) * 1e6)
        c = Reader(data, roff + off, roff + off + length)
        for _ in range(c.varint()):
            ni, ri, hi, n = c.varint(), c.varint(), c.byte(), c.varint()
            pts, x, y = [], cx, cy
            for _ in range(n):
                x += unzz(c.varint())
                y += unzz(c.varint())
                pts.append((x, y))
            records.append(dict(cell=i, row=row, col=col, name=strings[ni - 1] if ni else None,
                                ref=strings[ri - 1] if ri else None, highway=highways[hi], points=pts))
        assert c.pos == c.end, f"cell {i}'s body is {length} bytes but its records end at {c.pos - roff - off}"
    return dict(header=header, strings=strings, highways=highways, dir=directory, records=records)


# --- fixtures ---------------------------------------------------------------------------------------

def feature(coords, geom="LineString", **props):
    return json.dumps({"type": "Feature", "geometry": {"type": geom, "coordinates": coords}, "properties": props})


def q(p):
    return (round(p[0] * 1e6), round(p[1] * 1e6))


def zigzag(lon0, lat0, n=6, step=0.002, amp=0.0005):
    """A line DP keeps whole: each corner is tens of metres off the chord."""
    return [[lon0 + i * step, lat0 + (amp if i % 2 else 0.0)] for i in range(n)]


class Encoded:
    """Runs fnam_build.py encode on lines in a temporary directory and decodes the results."""

    def __init__(self, lines, squares, extra=(), rs=False):
        self.tmp = tempfile.TemporaryDirectory()
        self.out = os.path.join(self.tmp.name, "names1")
        args = [sys.executable, TOOL, "encode", self.out, "--osm-timestamp", TS, *extra]
        for s in squares:
            args += ["--square", s]
        text = "".join(("\x1e" if rs else "") + line + "\n" for line in lines)
        self.proc = subprocess.run(args, input=text.encode(), capture_output=True)

    def ok(self, test):
        test.assertEqual(self.proc.returncode, 0, self.proc.stderr.decode())
        return self

    def file(self, square):
        return decode(os.path.join(self.out, square + ".fnm"))

    def close(self):
        self.tmp.cleanup()


class EncodeCase(unittest.TestCase):
    def encode(self, lines, squares=("W75_N40",), **kw):
        e = Encoded(lines, squares, **kw)
        self.addCleanup(e.close)
        return e.ok(self)


# --- round trip -------------------------------------------------------------------------------------

class RoundTripTest(EncodeCase):

    def test_names_refs_highways_and_points_come_back(self):
        a = zigzag(-74.99, 40.01)
        b = zigzag(-74.98, 40.03)
        c = [[-74.95, 40.06], [-74.951, 40.062], [-74.949, 40.065]]
        e = self.encode([
            feature(a, highway="path", name="Pacific Crest Trail"),
            feature(b, highway="primary", name="Route 1", ref="US 1"),
            feature(c, highway="track", ref="FR 2012"),
            feature(zigzag(-74.97, 40.07), highway="footway", name="Sentier de la Côte – 東"),
        ], rs=True)
        f = e.file("W75_N40")
        h = f["header"]
        self.assertEqual((h["magic"], h["version"], h["west"], h["south"], h["cells"], h["reserved"]),
                         (b"FNAM", 1, -75, 40, 40, 0))
        self.assertEqual(h["timestamp"], TS_UNIX)
        self.assertEqual(h["tables"], 32)
        got = sorted(((r["name"], r["ref"], r["highway"], r["points"]) for r in f["records"]), key=str)
        want = sorted([("Pacific Crest Trail", None, "path", [q(p) for p in a]),
                       ("Route 1", "US 1", "primary", [q(p) for p in b]),
                       (None, "FR 2012", "track", [q(p) for p in c]),
                       ("Sentier de la Côte – 東", None, "footway", [q(p) for p in zigzag(-74.97, 40.07)])], key=str)
        self.assertEqual(got, want)
        self.assertEqual(len(f["strings"]), len(set(f["strings"])), "a string is in the table twice")

    def test_names_and_refs_share_one_table(self):
        e = self.encode([feature(zigzag(-74.99, 40.01), highway="primary", name="US 1", ref="US 1"),
                         feature(zigzag(-74.98, 40.03), highway="primary", name="Main St", ref="US 1")])
        f = e.file("W75_N40")
        self.assertEqual(sorted(f["strings"]), ["Main St", "US 1"])

    def test_every_square_named_gets_a_file_even_an_empty_one(self):
        e = self.encode([feature(zigzag(-74.99, 40.01), highway="path", name="A")], squares=("W75_N40", "W120_N45"))
        f = e.file("W120_N45")
        self.assertEqual((f["header"]["west"], f["header"]["south"]), (-120, 45))
        self.assertEqual(f["records"], [])
        self.assertTrue(all(length == 0 for _, length in f["dir"]))
        self.assertEqual(f["header"]["size"], f["header"]["records"])

    def test_pieces_in_squares_without_an_rd5_are_dropped(self):
        e = self.encode([feature(zigzag(-79.99, 40.01), highway="path", name="Elsewhere"),
                         feature(zigzag(-74.99, 40.01), highway="path", name="Here")])
        self.assertEqual([r["name"] for r in e.file("W75_N40")["records"]], ["Here"])
        self.assertFalse(os.path.exists(os.path.join(e.out, "W80_N40.fnm")))

    def test_rd5_dir_names_the_squares(self):
        with tempfile.TemporaryDirectory() as d:
            for n in ("W75_N40.rd5", "E0_N50.rd5", "W5_S5.rd5", "notes.txt"):
                open(os.path.join(d, n), "w").close()
            e = Encoded([feature(zigzag(0.01, 50.01), highway="path", name="Greenwich")], (), extra=("--rd5-dir", d))
            self.addCleanup(e.close)
            e.ok(self)
            self.assertEqual(sorted(os.listdir(e.out)), ["E0_N50.fnm", "W5_S5.fnm", "W75_N40.fnm"])
            self.assertEqual([r["name"] for r in e.file("E0_N50")["records"]], ["Greenwich"])
            self.assertEqual((e.file("W5_S5")["header"]["west"], e.file("W5_S5")["header"]["south"]), (-5, -5))

    def test_the_same_bytes_whatever_the_jobs_and_chunks(self):
        rnd = random.Random(7)
        lines = []
        for i in range(400):
            lon, lat = -75 + rnd.random() * 5, 40 + rnd.random() * 5
            pts = [[lon + 0.01 * k + rnd.uniform(-0.002, 0.002), lat + rnd.uniform(-0.01, 0.01)] for k in range(20)]
            lines.append(feature(pts, highway=rnd.choice(["path", "track", "residential"]), name=f"Way {i % 37}"))
        one = self.encode(lines, extra=("--jobs", "1"))
        many = self.encode(lines, extra=("--jobs", "3", "--chunk-bytes", "4096"))
        with open(os.path.join(one.out, "W75_N40.fnm"), "rb") as a, open(os.path.join(many.out, "W75_N40.fnm"), "rb") as b:
            self.assertEqual(a.read(), b.read())
        summary = json.loads(many.proc.stdout)
        self.assertEqual((summary["files"], summary["lines"], summary["ways"]), (1, 400, 400))


# --- cell edges -------------------------------------------------------------------------------------

def in_cell(f, rec):
    """Every point of the record inside (or on the edge of) the record's own cell."""
    x0 = round((f["header"]["west"] + rec["col"] / 8) * 1e6)
    y0 = round((f["header"]["south"] + rec["row"] / 8) * 1e6)
    return all(x0 <= x <= x0 + 125000 and y0 <= y <= y0 + 125000 for x, y in rec["points"])


class CellEdgeTest(EncodeCase):

    def test_a_line_crossing_a_cell_edge_splits_there_with_the_edge_point_in_both_pieces(self):
        # From cell (row 0, col 0) east across lon -74.875 into col 1; the edge is crossed at
        # three-tenths of the segment, so the edge point's latitude is 40.03 + 0.3 * 0.05.
        e = self.encode([feature([[-74.905, 40.03], [-74.805, 40.08]], highway="path", name="Across")])
        f = e.file("W75_N40")
        recs = sorted(f["records"], key=lambda r: r["col"])
        self.assertEqual([(r["row"], r["col"]) for r in recs], [(0, 0), (0, 1)],
                         "the line should be two pieces, one in each cell either side of the edge")
        edge = (-74875000, round(40.045 * 1e6))
        self.assertEqual(recs[0]["points"], [q((-74.905, 40.03)), edge])
        self.assertEqual(recs[1]["points"], [edge, q((-74.805, 40.08))])
        for r in recs:
            self.assertTrue(in_cell(f, r), f"a piece leaves its cell: {r}")

    def test_a_line_crossing_a_corner_and_several_edges_keeps_each_piece_in_its_cell(self):
        pts = [[-74.99, 40.01], [-74.60, 40.40], [-74.62, 40.02], [-74.30, 40.33]]
        e = self.encode([feature(pts, highway="path", name="Diagonal")])
        f = e.file("W75_N40")
        self.assertGreater(len(f["records"]), 6, "each edge the line crosses should start a new piece")
        ends, starts = set(), set()
        for r in f["records"]:
            self.assertTrue(in_cell(f, r), f"a piece leaves its cell {r['row']},{r['col']}: {r['points']}")
            ends.add(r["points"][-1])
            starts.add(r["points"][0])
        # Each piece but the last ends where another begins, on an edge.
        self.assertEqual(len(ends & starts), len(f["records"]) - 1)
        for x, y in ends & starts:
            self.assertTrue(x % 125000 == 0 or y % 125000 == 0, f"{(x, y)} is not on a cell edge")

    def test_a_line_crossing_a_square_edge_lands_in_both_squares(self):
        e = self.encode([feature([[-75.05, 42.01], [-74.95, 42.03]], highway="primary", name="Border Rd")],
                        squares=("W80_N40", "W75_N40"))
        west, east = e.file("W80_N40"), e.file("W75_N40")
        self.assertEqual(len(west["records"]), 1, "W80_N40 should hold the line's piece west of lon -75")
        self.assertEqual(len(east["records"]), 1, "W75_N40 should hold the line's piece east of lon -75")
        w, x = west["records"][0], east["records"][0]
        self.assertEqual((w["row"], w["col"]), (16, 39))
        self.assertEqual((x["row"], x["col"]), (16, 0))
        edge = (-75000000, 42020000)
        self.assertEqual(w["points"][-1], edge)
        self.assertEqual(x["points"][0], edge)
        self.assertEqual((w["name"], x["name"]), ("Border Rd", "Border Rd"))

    def test_a_line_along_an_edge_goes_north_and_east(self):
        # Along the cell edge lat 40.125 (between rows 0 and 1), and along lon -74.875 (cols 0, 1),
        # and along the square edge lat 45 (W75_N40's top, W75_N45's bottom).
        e = self.encode([feature([[-74.99, 40.125], [-74.95, 40.125]], highway="path", name="Along lat"),
                         feature([[-74.875, 40.01], [-74.875, 40.05]], highway="path", name="Along lon"),
                         feature([[-74.99, 45.0], [-74.95, 45.0]], highway="path", name="Along square")],
                        squares=("W75_N40", "W75_N45"))
        recs = {r["name"]: r for r in e.file("W75_N40")["records"]}
        self.assertEqual((recs["Along lat"]["row"], recs["Along lat"]["col"]), (1, 0))
        self.assertEqual((recs["Along lon"]["row"], recs["Along lon"]["col"]), (0, 1))
        self.assertNotIn("Along square", recs)
        north = {r["name"]: r for r in e.file("W75_N45")["records"]}
        self.assertEqual((north["Along square"]["row"], north["Along square"]["col"]), (0, 0))

    def test_a_point_on_an_edge_goes_north_and_east(self):
        # Starting exactly on an edge and going north: one piece, in the north cell, and no
        # one-point piece left in the south one.
        e = self.encode([feature([[-74.95, 40.125], [-74.951, 40.13], [-74.948, 40.14]], highway="path", name="Up"),
                         feature([[-74.875, 40.06], [-74.86, 40.061], [-74.85, 40.07]], highway="path", name="East")])
        recs = e.file("W75_N40")["records"]
        self.assertEqual(sorted((r["name"], r["row"], r["col"]) for r in recs), [("East", 0, 1), ("Up", 1, 0)])

    def test_touching_an_edge_from_the_south_stays_one_piece_in_the_south_cell(self):
        e = self.encode([feature([[-74.99, 40.10], [-74.98, 40.125], [-74.97, 40.10]], highway="path", name="Touch")])
        recs = e.file("W75_N40")["records"]
        self.assertEqual([(r["row"], r["col"], len(r["points"])) for r in recs], [(0, 0, 3)])


# --- what is left out -------------------------------------------------------------------------------

class DroppedTest(EncodeCase):

    def test_dropped_values_polygons_and_unnamed_ways_are_absent(self):
        lines = [feature(zigzag(-74.99, 40.01), highway=v, name=f"drop {v}")
                 for v in ("proposed", "construction", "abandoned", "razed", "disused", "platform")]
        lines += [
            feature(zigzag(-74.99, 40.02), highway="pedestrian", area="yes", name="drop area"),
            feature([[[-74.99, 40.03], [-74.98, 40.03], [-74.98, 40.04], [-74.99, 40.03]]], geom="Polygon",
                    highway="pedestrian", name="drop polygon"),
            feature([[[[-74.99, 40.05], [-74.98, 40.05], [-74.98, 40.06], [-74.99, 40.05]]]], geom="MultiPolygon",
                    highway="service", name="drop multipolygon"),
            feature([-74.99, 40.07], geom="Point", highway="bus_stop", name="drop point"),
            feature(zigzag(-74.99, 40.08), highway="path"),
            feature(zigzag(-74.99, 40.09), name="drop not a highway"),
            feature([[-74.99, 40.1], [-74.99, 40.1]], highway="path", name="drop one place"),
            feature(zigzag(-74.98, 40.11), highway="path", area="no", name="kept area=no"),
            feature([[-74.97, 40.11], [-74.96, 40.11], [-74.96, 40.12], [-74.97, 40.11]], highway="residential",
                    name="kept closed line"),
        ]
        e = self.encode(lines)
        f = e.file("W75_N40")
        self.assertEqual(sorted(r["name"] for r in f["records"]), ["kept area=no", "kept closed line"])
        self.assertFalse([s for s in f["strings"] if s.startswith("drop")])
        self.assertEqual(sorted(f["highways"]), ["path", "residential"])

    def test_more_than_256_highway_values_in_a_square_stops_the_run(self):
        lines = [feature(zigzag(-74.99, 40.01 + i * 1e-4), highway=f"v{i}", name="x") for i in range(257)]
        e = Encoded(lines, ("W75_N40",))
        self.addCleanup(e.close)
        self.assertNotEqual(e.proc.returncode, 0)
        self.assertIn("more than 256 highway values", e.proc.stderr.decode())


# --- Douglas-Peucker at 2 m ---------------------------------------------------------------------------

def metres(a, b, lat):
    """Ground distance between two nearby lon/lat points, in a frame local to them."""
    ky, kx = fnam_build_free_degree(lat)
    return math.hypot((a[0] - b[0]) * kx, (a[1] - b[1]) * ky)


def fnam_build_free_degree(lat):
    # WGS84 degree lengths, worked from the series in Snyder (1987), not the encoder's function.
    p = math.radians(lat)
    a, e2 = 6378137.0, 0.00669437999014
    s = 1 - e2 * math.sin(p) ** 2
    m = a * (1 - e2) / s ** 1.5 * math.pi / 180   # meridian
    n = a / math.sqrt(s) * math.cos(p) * math.pi / 180  # parallel
    return m, n


def dist_to_polyline(p, line):
    """Metres from p to the nearest point of line, each segment measured in a frame at its own
    latitude."""
    best = float("inf")
    for a, b in zip(line, line[1:]):
        lat = (a[1] + b[1] + p[1]) / 3
        ky, kx = fnam_build_free_degree(lat)
        ax, ay, bx, by, px, py = a[0] * kx, a[1] * ky, b[0] * kx, b[1] * ky, p[0] * kx, p[1] * ky
        dx, dy = bx - ax, by - ay
        l2 = dx * dx + dy * dy
        t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
        best = min(best, math.hypot(px - ax - t * dx, py - ay - t * dy))
    return best


def walk(seed, n, lat0, step_m, wobble_m, turn=0.4):
    rnd = random.Random(seed)
    ky, kx = fnam_build_free_degree(lat0)
    pts, heading, lon, lat = [], 0.3, -121.5, lat0
    for _ in range(n):
        pts.append((lon, lat))
        heading += rnd.uniform(-turn, turn)
        d = step_m + rnd.uniform(-wobble_m, wobble_m)
        lon += math.cos(heading) * d / kx
        lat += math.sin(heading) * d / ky
    return pts


class SimplifyTest(unittest.TestCase):

    def assert_within(self, source, kept, limit):
        worst = max(dist_to_polyline(p, kept) for p in source)
        self.assertLessEqual(worst, limit, f"a source point is {worst:.3f} m from the simplified line")
        # And the other way: points along the simplified line, near the source.
        worst = 0.0
        for a, b in zip(kept, kept[1:]):
            for k in range(1, 8):
                t = k / 8
                worst = max(worst, dist_to_polyline((a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])), source))
        self.assertLessEqual(worst, limit, f"the simplified line passes {worst:.3f} m from the source")

    def test_no_kept_line_moves_more_than_2_m(self):
        for seed, lat in ((1, 47.5), (2, 64.8), (3, 18.3), (4, 40.0)):
            src = walk(seed, 600, lat, 4.0, 3.0)
            kept = fnam_build.simplify(src)
            self.assertLess(len(kept), len(src) * 0.9, "the test line was barely simplified; it tests nothing")
            self.assertEqual((kept[0], kept[-1]), (src[0], src[-1]))
            self.assert_within(src, kept, 2.0)

    def test_a_straight_line_with_sub_metre_noise_collapses_and_a_3_m_bump_stays(self):
        ky, kx = fnam_build_free_degree(45.0)
        rnd = random.Random(5)
        straight = [(-120 + i * 10 / kx, 45 + rnd.uniform(-0.5, 0.5) / ky) for i in range(100)]
        self.assertLessEqual(len(fnam_build.simplify(straight)), 4)
        bump = [(-120 + i * 10 / kx, 45 + (3.0 / ky if i == 50 else 0.0)) for i in range(101)]
        self.assertIn(bump[50], fnam_build.simplify(bump))
        small = [(-120 + i * 10 / kx, 45 + (1.9 / ky if i == 50 else 0.0)) for i in range(101)]
        self.assertEqual(fnam_build.simplify(small), [small[0], small[-1]])

    def test_end_to_end_the_decoded_pieces_stay_within_2_m_and_rounding(self):
        src = [(-74.9 + (p[0] + 121.5), p[1] - 47.5 + 40.1) for p in walk(9, 1500, 47.5, 40.0, 10.0, turn=0.08)]
        e = Encoded([feature([list(p) for p in src], highway="path", name="Long walk")], ("W75_N40",))
        self.addCleanup(e.close)
        e.ok(self)
        f = e.file("W75_N40")
        self.assertGreater(len({r["cell"] for r in f["records"]}), 3, "the walk should cross cells")
        # 2 m, plus half a 1e-6-degree step's diagonal (under 0.08 m).
        for r in f["records"]:
            for x, y in r["points"]:
                self.assertLessEqual(dist_to_polyline((x / 1e6, y / 1e6), src), 2.08)


# --- the directory, and check -----------------------------------------------------------------------

class DirectoryTest(EncodeCase):

    def lines(self):
        rnd = random.Random(11)
        out = []
        for i in range(300):
            lon, lat = -75 + rnd.random() * 5, 40 + rnd.random() * 5
            out.append(feature([[lon, lat], [lon + rnd.uniform(-0.3, 0.3), lat + rnd.uniform(-0.3, 0.3)],
                                [lon + rnd.uniform(-0.3, 0.3), lat + rnd.uniform(-0.3, 0.3)]],
                               highway="track", name=f"T{i}", ref=str(i % 13)))
        return out

    def test_offsets_and_lengths_cover_the_records_exactly_without_overlap(self):
        f = self.encode(self.lines()).file("W75_N40")
        h = f["header"]
        spans = sorted((off, length) for off, length in f["dir"] if length)
        self.assertGreater(len(spans), 100)
        at = 0
        for off, length in spans:
            self.assertEqual(off, at, "a gap or an overlap between cells")
            at += length
        self.assertEqual(at, h["size"] - h["records"])
        # decode() has already checked each cell's records end exactly at its length.

    def check(self, out, rd5_names, *extra):
        with tempfile.TemporaryDirectory() as d:
            for n in rd5_names:
                open(os.path.join(d, n + ".rd5"), "w").close()
            return subprocess.run([sys.executable, TOOL, "check", out, "--rd5-dir", d, *extra],
                                  capture_output=True, text=True)

    def test_check_passes_good_files_and_names_the_count_bytes_and_largest(self):
        e = self.encode(self.lines(), squares=("W75_N40", "W80_N40"))
        r = self.check(e.out, ["W75_N40", "W80_N40"], "--osm-timestamp", TS)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        size = os.path.getsize(os.path.join(e.out, "W75_N40.fnm")) + os.path.getsize(os.path.join(e.out, "W80_N40.fnm"))
        self.assertIn(f"2 .fnm files, one per .rd5; {size} bytes; largest W75_N40.fnm", r.stdout)

    def test_check_fails_a_missing_file_a_wrong_header_and_a_truncated_file(self):
        e = self.encode(self.lines(), squares=("W75_N40", "W80_N40"))
        r = self.check(e.out, ["W75_N40", "W80_N40", "W85_N40"])
        self.assertEqual(r.returncode, 1)
        self.assertIn("W85_N40.rd5 has no W85_N40.fnm", r.stdout)
        path = os.path.join(e.out, "W80_N40.fnm")
        with open(path, "r+b") as fh:
            fh.seek(6)
            fh.write(struct.pack("<h", -70))
        r = self.check(e.out, ["W75_N40", "W80_N40"])
        self.assertEqual(r.returncode, 1)
        self.assertIn("W80_N40: edges -70,40, the name says -80,40", r.stdout)
        r = self.check(e.out, ["W75_N40", "W80_N40"], "--osm-timestamp", "2026-10-21T20:20:51Z")
        self.assertIn("W75_N40: timestamp", r.stdout)
        path = os.path.join(e.out, "W75_N40.fnm")
        with open(path, "r+b") as fh:
            fh.truncate(os.path.getsize(path) - 3)
        r = self.check(e.out, ["W75_N40"])
        self.assertEqual(r.returncode, 1)
        self.assertIn("W75_N40: cell", r.stdout)
        self.assertIn("W80_N40.fnm has no W80_N40.rd5", r.stdout)

    def test_encode_refuses_a_directory_already_holding_names_files(self):
        e = self.encode([feature(zigzag(-74.99, 40.01), highway="path", name="A")])
        r = subprocess.run([sys.executable, TOOL, "encode", e.out, "--osm-timestamp", TS, "--square", "W75_N40"],
                           input=b"", capture_output=True)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("already holds .fnm files", r.stderr.decode())


if __name__ == "__main__":
    unittest.main()
