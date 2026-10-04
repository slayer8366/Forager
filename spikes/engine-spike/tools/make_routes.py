"""Make routes.json for the spike (dispatch -446). Positions are written here only, never printed.

r1, r2, r3: about 10 km along a named trail (or the whole trail if shorter), following its mapped line.
r4: r2's route, but starting 200 m off the trail (perpendicular), checked against every mapped walkable way.
r5: the town route from the 2026-10-03 walk: its first reading to the reading farthest from it.
"""
import collections, csv, json, math, sys
import osmium

PBF = sys.argv[1]
WALK_CSV = sys.argv[2]
TRAILS = {"r1": "McKenzie River Trail #3507", "r2": "Salmon River Trail #742", "r3": "Green Lakes Trail #17"}
WANT_M = 10_000
WALKABLE = {"path", "footway", "track", "bridleway", "steps", "pedestrian", "living_street", "residential",
            "service", "unclassified", "tertiary", "secondary", "primary", "cycleway"}


def dist(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


class H(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.trail = {k: [] for k in TRAILS}
        self.walk_segments = []  # every walkable way's points, for the off-trail check

    def way(self, w):
        hw = w.tags.get("highway")
        if hw not in WALKABLE:
            return
        nodes = [(n.ref, (n.lat, n.lon)) for n in w.nodes if n.location.valid()]
        self.walk_segments.append([p for _, p in nodes])
        for k, name in TRAILS.items():
            if w.tags.get("name") == name:
                self.trail[k].append(nodes)


h = H()
h.apply_file(PBF, locations=True)


def longest_line(ways):
    """The trail's line as one ordered list of points: the longest path through its ways' shared nodes."""
    adj = collections.defaultdict(list)
    pos = {}
    for nodes in ways:
        for (a, pa), (b, pb) in zip(nodes, nodes[1:]):
            d = dist(pa, pb)
            adj[a].append((b, d)); adj[b].append((a, d)); pos[a] = pa; pos[b] = pb

    def farthest(start):
        best = {start: (0.0, None)}
        stack = [start]
        while stack:
            u = stack.pop()
            for v, d in adj[u]:
                if v not in best:
                    best[v] = (best[u][0] + d, u); stack.append(v)
        end = max(best, key=lambda n: best[n][0])
        return end, best

    a, _ = farthest(next(iter(adj)))
    b, best = farthest(a)
    path = [b]
    while best[path[-1]][1] is not None:
        path.append(best[path[-1]][1])
    return [pos[n] for n in path], best[b][0]


routes, report = {}, {}
lines = {}
for k in TRAILS:
    line, total = longest_line(h.trail[k])
    acc, cut = 0.0, [line[0]]
    for a, b in zip(line, line[1:]):
        acc += dist(a, b); cut.append(b)
        if acc >= WANT_M:
            break
    lines[k] = cut
    routes[k] = [list(cut[0]), list(cut[-1])]
    report[k] = {"trail": TRAILS[k], "connected_line_km": round(total / 1000, 1), "route_along_trail_km": round(acc / 1000, 1),
                 "straight_km": round(dist(cut[0], cut[-1]) / 1000, 1)}

# r4: 200 m off r2's start, perpendicular to the trail's first stretch.
s, nxt = lines["r2"][0], lines["r2"][min(10, len(lines["r2"]) - 1)]
dy, dx = nxt[0] - s[0], (nxt[1] - s[1]) * math.cos(math.radians(s[0]))
norm = math.hypot(dx, dy)
off = (s[0] + (dx / norm) * 260 / 111_320, s[1] - (dy / norm) * 260 / (111_320 * math.cos(math.radians(s[0]))))


def to_segment_m(p, a, b):
    ky = 111_320.0; kx = ky * math.cos(math.radians(p[0]))
    ax, ay = (a[1] - p[1]) * kx, (a[0] - p[0]) * ky; bx, by = (b[1] - p[1]) * kx, (b[0] - p[0]) * ky
    vx, vy = bx - ax, by - ay; l2 = vx * vx + vy * vy
    t = 0 if l2 == 0 else max(0, min(1, -(ax * vx + ay * vy) / l2))
    return math.hypot(ax + t * vx, ay + t * vy)


def nearest_way_m(p):
    best = float("inf")
    for seg in h.walk_segments:
        if not any(abs(q[0] - p[0]) < 0.02 and abs(q[1] - p[1]) < 0.03 for q in seg):
            continue
        for a, b in zip(seg, seg[1:]):
            best = min(best, to_segment_m(p, a, b))
    return best


routes["r4"] = [list(off), routes["r2"][1]]
report["r4"] = {"start_off_trail_m": round(nearest_way_m(off)), "note": "to the nearest mapped walkable way's line"}

import sqlite3
db = sqlite3.connect(WALK_CSV)  # the walk's copied work.db (track 3ba03878)
tid = [r[0] for r in db.execute("select id from tracks where id like '3ba03878%'")][0]
pts = [(r[0], r[1]) for r in db.execute("select lat, lng from track_points where trackId=? order by timestampEpochMillis", (tid,))]
far = max(pts, key=lambda p: dist(pts[0], p))
# The walk itself is short, so the street route goes on in the walk's direction to a mapped way's point 2.3 to 2.8 km away.
o = pts[0]
def bearing_cos(q):
    ax, ay = (far[1] - o[1]) * math.cos(math.radians(o[0])), far[0] - o[0]
    bx, by = (q[1] - o[1]) * math.cos(math.radians(o[0])), q[0] - o[0]
    return (ax * bx + ay * by) / (math.hypot(ax, ay) * math.hypot(bx, by) or 1)
cands = [q for seg in h.walk_segments for q in seg if 2300 <= dist(o, q) <= 2800]
end = max(cands, key=bearing_cos)
routes["r5"] = [list(o), list(end)]
report["r5"] = {"walk_far_point_km": round(dist(o, far) / 1000, 2), "straight_km": round(dist(o, end) / 1000, 2),
                "direction_cos_to_walk": round(bearing_cos(end), 3), "walk_readings": len(pts)}

json.dump(routes, open("routes.json", "w"))
json.dump(report, open("routes-report.json", "w"), indent=1)
print(json.dumps(report, indent=1))
