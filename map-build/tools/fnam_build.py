#!/usr/bin/env python3
"""Trail and road names per route square, as FNAM v1 files (dispatch 2026-10-26-pi-31).

    fnam_build.py encode OUT_DIR --osm-timestamp T (--rd5-dir DIR | --square NAME ...) [--jobs N]
                                                                      < named.geojsonseq
    fnam_build.py check DIR --rd5-dir DIR [--osm-timestamp T]

encode reads `osmium export -f geojsonseq` of the named highways on stdin, one feature per line
(a leading record separator is allowed), and writes OUT_DIR/<NAME>.fnm for every square named,
one per .rd5 in --rd5-dir (W75_N40.rd5 -> W75_N40.fnm) and per --square. Every square named gets
a file, even one with no records; pieces in a square not named are dropped. T is the extract's
replication timestamp, ISO 8601 (2026-10-24T20:20:51Z) or Unix seconds. One pass over the input:
each line is simplified, split at the 1/8-degree cell edges and assigned to its cell and square,
and the encoded cells are held in memory until the input ends. --jobs N parses, simplifies and
splits in N worker processes (default 3); the results are taken in input order, so the files are
the same byte for byte whatever N is. A summary is printed to stdout as one JSON line.

check reads every .fnm in DIR against the .rd5 squares in --rd5-dir: one .fnm per .rd5 and no
other; the magic, version, edges (from the name), cells per side, and timestamp (if given); the
tables, directory and records each inside the file, the tables parsing exactly up to the
directory, and the directory's cells covering the records exactly, without overlap. It prints one
line, the count, total bytes and largest file, and exits 1 naming each problem.

The format, the planner's (dispatch pi-31, "FNAM v1"; don't change it without asking), little-endian:
  header, 32 bytes: "FNAM", u16 version 1, i16 west, i16 south (whole degrees), u8 cells per side
    (40), u8 0, i64 OSM timestamp (Unix s), u32 tables offset, u32 directory offset, u32 records
    offset;
  tables: varint N, N x (varint length, UTF-8) - the names and refs, referred to as 1..N, 0 = none;
    then varint M, M x (varint length, bytes) - the highway values, referred to as 0..M-1 in a byte;
  directory: 1600 x (u32 offset from the records offset, u32 length), index row * 40 + col, row 0
    the south; an empty cell has length 0 (its offset is where it would start);
  cell body: varint count, then per record varint name, varint ref, u8 highway, varint points
    (>= 2), then zz(x) zz(y) of the first point from the cell's south-west corner and zz(dx) zz(dy)
    of each next one from the one before, in 1e-6 degrees.
varint is unsigned LEB128, zz(n) = (n << 1) ^ (n >> 63).

Content: LineStrings with highway=* and a name or a ref; not area=yes, and not highway=proposed,
construction, abandoned, razed, disused or platform. Each line is simplified by Douglas-Peucker at
2 m first, then split exactly at cell edges: a segment that crosses an edge gets a point on the
edge, interpolated, which ends one piece and starts the next, so each piece lies inside one cell.
A stretch of line along an edge, like a point on one, belongs to the cell north and east of it
(the floor rule). Points are rounded to 1e-6 degrees; repeated points are dropped, and a piece
left with fewer than two is dropped (it is under about 0.1 m long).
"""
import argparse
import datetime
import json
import math
import os
import re
import resource
import struct
import sys
from collections import deque

VERSION = 1
CELLS = 40                     # per side of a 5-degree square: 1/8-degree cells
PER_DEGREE = 8
SQUARE_DEG = 5
SCALE = 1_000_000              # points in 1e-6 degrees
CELL_UNITS = SCALE // PER_DEGREE
SIMPLIFY_M = 2.0
DROP_HIGHWAY = frozenset({"proposed", "construction", "abandoned", "razed", "disused", "platform"})
HEADER = struct.Struct("<4sHhhBBqIII")
DIR_ENTRY = struct.Struct("<II")
DIR_BYTES = CELLS * CELLS * DIR_ENTRY.size
MAX_HIGHWAY_VALUES = 256
CHUNK_BYTES = 4 << 20
NAME_RE = re.compile(r"([WE])(\d{1,3})_([NS])(\d{1,2})")

assert HEADER.size == 32


# --- squares ----------------------------------------------------------------------------------

def parse_square(name):
    """'W75_N40' -> (-75, 40), the square's west and south edges."""
    m = NAME_RE.fullmatch(name)
    if not m:
        raise ValueError(f"not a square name: {name}")
    west = int(m[2]) * (-1 if m[1] == "W" else 1)
    south = int(m[4]) * (-1 if m[3] == "S" else 1)
    if west % SQUARE_DEG or south % SQUARE_DEG or not (-180 <= west < 180 and -90 <= south < 90):
        raise ValueError(f"not a square name: {name}")
    return west, south


def square_name(west, south):
    """As BRouter and build-usca.sh's rd5_square name them: W125_N45, E0_N50, W5_S5."""
    return ("W%d" % -west if west < 0 else "E%d" % west) + "_" + ("S%d" % -south if south < 0 else "N%d" % south)


def squares_from_rd5_dir(path):
    return sorted(f[:-4] for f in os.listdir(path) if f.endswith(".rd5"))


# --- encoding helpers ---------------------------------------------------------------------------

def put_varint(buf, n):
    while n > 0x7F:
        buf.append((n & 0x7F) | 0x80)
        n >>= 7
    buf.append(n)


def zz(n):
    return (n << 1) ^ (n >> 63)


def osm_timestamp(value):
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    t = datetime.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)
    return int(t.timestamp())


# --- geometry -----------------------------------------------------------------------------------

def metres_per_degree(phi_deg):
    """WGS84 lengths of a degree of latitude and of longitude at latitude phi."""
    p = math.radians(phi_deg)
    lat = 111132.954 - 559.822 * math.cos(2 * p) + 1.175 * math.cos(4 * p)
    lon = 111412.84 * math.cos(p) - 93.5 * math.cos(3 * p) + 0.118 * math.cos(5 * p)
    return lat, lon


def simplify(pts, tol_m=SIMPLIFY_M):
    """Douglas-Peucker in one equirectangular frame for the line. The frame's two scales are
    taken where they are largest on the line (east-west nearest the equator, north-south nearest
    a pole), so a distance in it is never less than on the ground and the line moves at most
    tol_m. The first and last points are always kept."""
    n = len(pts)
    if n < 3:
        return pts
    lo = min(p[1] for p in pts)
    hi = max(p[1] for p in pts)
    ky = metres_per_degree(max(abs(lo), abs(hi)))[0]
    kx = metres_per_degree(0.0 if lo <= 0 <= hi else min(abs(lo), abs(hi)))[1]
    xs = [p[0] * kx for p in pts]
    ys = [p[1] * ky for p in pts]
    tol2 = tol_m * tol_m
    keep = [False] * n
    keep[0] = keep[n - 1] = True
    stack = [(0, n - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        ax, ay = xs[a], ys[a]
        dx, dy = xs[b] - ax, ys[b] - ay
        l2 = dx * dx + dy * dy
        best, bi = -1.0, -1
        for i in range(a + 1, b):
            px, py = xs[i] - ax, ys[i] - ay
            if l2 == 0.0:
                d2 = px * px + py * py
            else:
                t = (px * dx + py * dy) / l2
                if t <= 0.0:
                    d2 = px * px + py * py
                elif t >= 1.0:
                    ex, ey = px - dx, py - dy
                    d2 = ex * ex + ey * ey
                else:
                    ex, ey = px - t * dx, py - t * dy
                    d2 = ex * ex + ey * ey
            if d2 > best:
                best, bi = d2, i
        if best > tol2:
            keep[bi] = True
            stack.append((a, bi))
            stack.append((bi, b))
    return [p for p, k in zip(pts, keep) if k]


def split_cells(pts):
    """Splits a line at every 1/8-degree grid line it crosses, with a point on the line where it
    crosses, and returns [((gx, gy), [points])], one per piece, each inside cell (gx, gy) of the
    global 1/8-degree grid (gx = floor(lon * 8)). A sub-segment goes to the cell holding its
    midpoint, by the floor rule, so one along a grid line goes north or east of it."""
    pieces = []
    cur, cur_cell = None, None
    floor = math.floor
    for i in range(len(pts) - 1):
        x0, y0 = pts[i]
        x1, y1 = pts[i + 1]
        fx0, fx1, fy0, fy1 = x0 * PER_DEGREE, x1 * PER_DEGREE, y0 * PER_DEGREE, y1 * PER_DEGREE
        cuts = []
        if fx0 != fx1:
            lo, hi = (fx0, fx1) if fx0 < fx1 else (fx1, fx0)
            k = floor(lo) + 1
            while k < hi:
                t = (k - fx0) / (fx1 - fx0)
                cuts.append((t, (k / PER_DEGREE, y0 + t * (y1 - y0))))
                k += 1
        if fy0 != fy1:
            lo, hi = (fy0, fy1) if fy0 < fy1 else (fy1, fy0)
            k = floor(lo) + 1
            while k < hi:
                t = (k - fy0) / (fy1 - fy0)
                cuts.append((t, (x0 + t * (x1 - x0), k / PER_DEGREE)))
                k += 1
        if len(cuts) > 1:
            cuts.sort(key=lambda c: c[0])
        prev = (x0, y0)
        for q in [c[1] for c in cuts] + [(x1, y1)]:
            if q == prev:
                continue
            cell = (floor((prev[0] + q[0]) * (PER_DEGREE / 2)), floor((prev[1] + q[1]) * (PER_DEGREE / 2)))
            if cell == cur_cell:
                cur.append(q)
            else:
                if cur_cell is not None:
                    pieces.append((cur_cell, cur))
                cur, cur_cell = [prev, q], cell
            prev = q
    if cur_cell is not None:
        pieces.append((cur_cell, cur))
    return pieces


def encode_points(pts, gx, gy):
    """(the record's point count and points, from cell (gx, gy)'s south-west corner, as bytes; the
    count), or None if fewer than two distinct points are left at 1e-6 degrees."""
    q = []
    last = None
    for x, y in pts:
        p = (round(x * SCALE), round(y * SCALE))
        if p != last:
            q.append(p)
            last = p
    if len(q) < 2:
        return None
    buf = bytearray()
    put_varint(buf, len(q))
    px, py = gx * CELL_UNITS, gy * CELL_UNITS
    for x, y in q:
        dx, dy = x - px, y - py
        put_varint(buf, (dx << 1) ^ (dx >> 63))
        put_varint(buf, (dy << 1) ^ (dy >> 63))
        px, py = x, y
    return bytes(buf), len(q)


# --- one chunk of input: parse, filter, simplify, split ----------------------------------------

_wanted = None  # the squares to keep, as (west // 5, south // 5), set in each worker


def _init_worker(wanted):
    global _wanted
    _wanted = wanted


def new_stats():
    return {"lines": 0, "ways": 0, "not_linestring": 0, "dropped_highway": 0, "area_yes": 0,
            "no_name_or_ref": 0, "degenerate": 0, "points_in": 0, "points_simplified": 0,
            "pieces": 0, "pieces_tiny": 0, "pieces_outside": 0, "points_out": 0}


def process_chunk(chunk):
    """chunk: bytes holding whole lines. Returns (pieces, stats); each piece is
    (square key, cell index, name, ref, highway, encoded points)."""
    out = []
    st = new_stats()
    wanted = _wanted
    for line in chunk.split(b"\n"):
        line = line.strip().lstrip(b"\x1e").strip()
        if not line:
            continue
        st["lines"] += 1
        f = json.loads(line)
        g = f.get("geometry") or {}
        p = f.get("properties") or {}
        if g.get("type") != "LineString":
            st["not_linestring"] += 1
            continue
        hw = p.get("highway")
        if not hw or hw in DROP_HIGHWAY:
            st["dropped_highway"] += 1
            continue
        if p.get("area") == "yes":
            st["area_yes"] += 1
            continue
        name, ref = p.get("name") or "", p.get("ref") or ""
        if not name and not ref:
            st["no_name_or_ref"] += 1
            continue
        coords = g.get("coordinates") or []
        st["points_in"] += len(coords)
        pts = []
        for c in coords:
            pt = (float(c[0]), float(c[1]))
            if not pts or pt != pts[-1]:
                pts.append(pt)
        if len(pts) < 2:
            st["degenerate"] += 1
            continue
        st["ways"] += 1
        pts = simplify(pts)
        st["points_simplified"] += len(pts)
        for (gx, gy), piece in split_cells(pts):
            key = (gx // CELLS, gy // CELLS)
            if key not in wanted:
                st["pieces_outside"] += 1
                continue
            enc = encode_points(piece, gx, gy)
            if enc is None:
                st["pieces_tiny"] += 1
                continue
            st["pieces"] += 1
            st["points_out"] += enc[1]
            out.append((key, (gy % CELLS) * CELLS + gx % CELLS, name, ref, hw, enc[0]))
    return out, st


def read_chunks(stream, size=CHUNK_BYTES):
    rest = b""
    while True:
        block = stream.read(size)
        if not block:
            if rest:
                yield rest
            return
        block = rest + block
        cut = block.rfind(b"\n")
        if cut < 0:
            rest = block
            continue
        yield block[:cut + 1]
        rest = block[cut + 1:]


# --- one square's file ----------------------------------------------------------------------------

class Square:
    __slots__ = ("west", "south", "strings", "highways", "bodies", "counts")

    def __init__(self, west, south):
        self.west, self.south = west, south
        self.strings = {}    # string -> 1-based index
        self.highways = {}   # value -> 0-based index
        self.bodies = [None] * (CELLS * CELLS)
        self.counts = [0] * (CELLS * CELLS)

    def add(self, cell, name, ref, hw, enc):
        s = self.strings
        ni = s.setdefault(name, len(s) + 1) if name else 0
        ri = s.setdefault(ref, len(s) + 1) if ref else 0
        h = self.highways
        hi = h.setdefault(hw, len(h))
        if hi >= MAX_HIGHWAY_VALUES:
            raise SystemExit(f"{square_name(self.west, self.south)}: more than {MAX_HIGHWAY_VALUES} highway values "
                             f"(FNAM v1 holds them in one byte); the latest is {hw!r}")
        body = self.bodies[cell]
        if body is None:
            body = self.bodies[cell] = bytearray()
        put_varint(body, ni)
        put_varint(body, ri)
        body.append(hi)
        body += enc
        self.counts[cell] += 1

    def encode(self, timestamp):
        tables = bytearray()
        for table in (self.strings, self.highways):   # dicts keep insertion order: index order
            put_varint(tables, len(table))
            for value in table:
                b = value.encode("utf-8")
                put_varint(tables, len(b))
                tables += b
        directory = bytearray()
        bodies = []
        offset = 0
        for count, body in zip(self.counts, self.bodies):
            if count:
                head = bytearray()
                put_varint(head, count)
                bodies.append(head)
                bodies.append(body)
                length = len(head) + len(body)
            else:
                length = 0
            directory += DIR_ENTRY.pack(offset, length)
            offset += length
        tables_off = HEADER.size
        dir_off = tables_off + len(tables)
        rec_off = dir_off + DIR_BYTES
        if rec_off + offset > 0xFFFFFFFF:
            raise SystemExit(f"{square_name(self.west, self.south)}: over 4 GiB, past FNAM v1's u32 offsets")
        header = HEADER.pack(b"FNAM", VERSION, self.west, self.south, CELLS, 0, timestamp, tables_off, dir_off, rec_off)
        return [header, tables, directory] + bodies


def write_atomic(path, parts):
    tmp = path + ".part"
    with open(tmp, "wb") as f:
        for p in parts:
            f.write(p)
    os.replace(tmp, path)


def encode_main(args):
    names = set(args.square or [])
    if args.rd5_dir:
        names.update(squares_from_rd5_dir(args.rd5_dir))
    if not names:
        raise SystemExit("no squares: give --rd5-dir with .rd5 files in it, or --square")
    squares = {}
    for n in sorted(names):
        west, south = parse_square(n)
        squares[(west // SQUARE_DEG, south // SQUARE_DEG)] = Square(west, south)
    timestamp = osm_timestamp(args.osm_timestamp)
    os.makedirs(args.out_dir, exist_ok=True)
    old = [f for f in os.listdir(args.out_dir) if f.endswith((".fnm", ".fnm.part"))]
    if old:
        raise SystemExit(f"{args.out_dir} already holds .fnm files ({len(old)}); give an empty directory")
    wanted = frozenset(squares)
    stats = new_stats()

    def take(result):
        pieces, st = result
        for k, v in st.items():
            stats[k] += v
        for key, cell, name, ref, hw, enc in pieces:
            squares[key].add(cell, name, ref, hw, enc)

    chunks = read_chunks(sys.stdin.buffer, args.chunk_bytes)
    if args.jobs <= 1:
        _init_worker(wanted)
        for chunk in chunks:
            take(process_chunk(chunk))
    else:
        import multiprocessing
        with multiprocessing.Pool(args.jobs, initializer=_init_worker, initargs=(wanted,)) as pool:
            # Bounded and in order: Pool.imap would read all of stdin ahead into its queue.
            pending = deque()
            for chunk in chunks:
                pending.append(pool.apply_async(process_chunk, (chunk,)))
                if len(pending) >= 2 * args.jobs:
                    take(pending.popleft().get())
            while pending:
                take(pending.popleft().get())

    total, largest, most_hw, most_strings = 0, (None, -1), (None, 0), (None, 0)
    for sq in squares.values():
        n = square_name(sq.west, sq.south)
        parts = sq.encode(timestamp)
        path = os.path.join(args.out_dir, n + ".fnm")
        write_atomic(path, parts)
        size = sum(len(p) for p in parts)
        total += size
        if size > largest[1]:
            largest = (n, size)
        if len(sq.highways) > most_hw[1]:
            most_hw = (n, len(sq.highways))
        if len(sq.strings) > most_strings[1]:
            most_strings = (n, len(sq.strings))
    rs = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    rc = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    print(json.dumps({"files": len(squares), "bytes": total, "largest": {"name": largest[0], "bytes": largest[1]},
                      "most_highway_values": {"name": most_hw[0], "values": most_hw[1]},
                      "most_strings": {"name": most_strings[0], "strings": most_strings[1]},
                      **stats, "peak_rss_kib": rs, "peak_rss_kib_workers": rc}))
    return 0


# --- check --------------------------------------------------------------------------------------

def _varint_at(data, pos, end):
    n = shift = 0
    while True:
        if pos >= end:
            raise ValueError("a varint runs past its section")
        b = data[pos]
        pos += 1
        n |= (b & 0x7F) << shift
        if b < 0x80:
            return n, pos
        shift += 7
        if shift > 63:
            raise ValueError("a varint over 64 bits")


def check_file(path, name, timestamp=None):
    """The problems with one .fnm file, as a list of strings (empty if none)."""
    problems = []
    with open(path, "rb") as f:
        data = f.read()
    size = len(data)
    if size < HEADER.size + DIR_BYTES:
        return [f"{name}: {size} bytes, too short for a header and a directory"]
    magic, version, west, south, cells, reserved, ts, tables_off, dir_off, rec_off = HEADER.unpack_from(data, 0)
    want_w, want_s = parse_square(name)
    if magic != b"FNAM":
        problems.append(f"{name}: magic {magic!r}")
    if version != VERSION:
        problems.append(f"{name}: version {version}")
    if (west, south) != (want_w, want_s):
        problems.append(f"{name}: edges {west},{south}, the name says {want_w},{want_s}")
    if cells != CELLS or reserved != 0:
        problems.append(f"{name}: cells per side {cells}, reserved {reserved}")
    if timestamp is not None and ts != timestamp:
        problems.append(f"{name}: timestamp {ts}, expected {timestamp}")
    if not (HEADER.size <= tables_off <= dir_off and dir_off + DIR_BYTES == rec_off <= size):
        problems.append(f"{name}: offsets tables {tables_off}, directory {dir_off}, records {rec_off} in {size} bytes")
        return problems
    try:
        pos = tables_off
        for which in ("strings", "highway values"):
            count, pos = _varint_at(data, pos, dir_off)
            if which == "highway values" and count > MAX_HIGHWAY_VALUES:
                problems.append(f"{name}: {count} highway values")
            for _ in range(count):
                length, pos = _varint_at(data, pos, dir_off)
                pos += length
                if pos > dir_off:
                    raise ValueError(f"the {which} table runs into the directory")
        if pos != dir_off:
            problems.append(f"{name}: the tables end at {pos}, the directory starts at {dir_off}")
    except ValueError as e:
        problems.append(f"{name}: {e}")
    records = size - rec_off
    spans = []
    for i in range(CELLS * CELLS):
        off, length = DIR_ENTRY.unpack_from(data, dir_off + i * DIR_ENTRY.size)
        if length:
            if off + length > records:
                problems.append(f"{name}: cell {i} at {off}+{length}, past the records' {records} bytes")
            spans.append((off, length, i))
    spans.sort()
    at = 0
    for off, length, i in spans:
        if off != at:
            problems.append(f"{name}: cell {i} starts at {off}, expected {at} ({'overlap' if off < at else 'gap'})")
            break
        at = off + length
    if not problems and at != records:
        problems.append(f"{name}: the cells cover {at} of the records' {records} bytes")
    return problems


def check_main(args):
    rd5 = set(squares_from_rd5_dir(args.rd5_dir))
    fnm = {f[:-4] for f in os.listdir(args.dir) if f.endswith(".fnm")}
    timestamp = osm_timestamp(args.osm_timestamp) if args.osm_timestamp else None
    problems = [f"{n}.rd5 has no {n}.fnm" for n in sorted(rd5 - fnm)]
    problems += [f"{n}.fnm has no {n}.rd5" for n in sorted(fnm - rd5)]
    total, largest = 0, (None, -1)
    for n in sorted(fnm & rd5):
        path = os.path.join(args.dir, n + ".fnm")
        try:
            problems += check_file(path, n, timestamp)
        except (OSError, ValueError, struct.error) as e:
            problems.append(f"{n}: {e}")
        size = os.path.getsize(path)
        total += size
        if size > largest[1]:
            largest = (n, size)
    if problems:
        print(f"names check: {len(problems)} problems: " + "; ".join(problems[:20])
              + ("; ..." if len(problems) > 20 else ""))
        return 1
    print(f"names check: {len(fnm)} .fnm files, one per .rd5; {total} bytes; largest {largest[0]}.fnm, {largest[1]} bytes")
    return 0


def main(argv):
    ap = argparse.ArgumentParser(description="FNAM v1 names files per route square")
    sub = ap.add_subparsers(dest="cmd", required=True)
    e = sub.add_parser("encode")
    e.add_argument("out_dir")
    e.add_argument("--osm-timestamp", required=True)
    e.add_argument("--rd5-dir")
    e.add_argument("--square", action="append")
    e.add_argument("--jobs", type=int, default=3)
    e.add_argument("--chunk-bytes", type=int, default=CHUNK_BYTES, help=argparse.SUPPRESS)  # for the tests
    c = sub.add_parser("check")
    c.add_argument("dir")
    c.add_argument("--rd5-dir", required=True)
    c.add_argument("--osm-timestamp")
    args = ap.parse_args(argv)
    return encode_main(args) if args.cmd == "encode" else check_main(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
