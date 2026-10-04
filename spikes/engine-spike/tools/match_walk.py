"""Item 8 (dispatch -446): which mapped way the walker was on, from the 2026-10-03 walk, with Valhalla's
matcher (Meili, through pyvalhalla's trace_attributes). Prints counts and times only, never a position
or a name. The matched result, which has both, stays in this folder (match-walk.json)."""
import json, sqlite3, sys, collections
import valhalla

cfg, db = sys.argv[1], sys.argv[2]
c = sqlite3.connect(db)
tid = [r[0] for r in c.execute("select id from tracks where id like '3ba03878%'")][0]
pts = [dict(lat=r[0], lon=r[1], acc=r[2], time=r[3] // 1000) for r in
       c.execute("select lat, lng, accuracyMeters, timestampEpochMillis from track_points where trackId=? order by timestampEpochMillis", (tid,))]
a = valhalla.Actor(cfg)
req = {
    "shape": [{"lat": p["lat"], "lon": p["lon"], "time": p["time"]} for p in pts],
    "costing": "pedestrian",
    "shape_match": "map_snap",
    "trace_options": {"search_radius": 30, "gps_accuracy": 10},
    "filters": {"attributes": ["edge.way_id", "edge.names", "edge.use", "edge.sidewalk_left", "edge.sidewalk_right",
                               "matched.point", "matched.type", "matched.edge_index", "matched.distance_from_trace_point"],
                "action": "include"},
}
r = json.loads(a.trace_attributes(json.dumps(req)))
json.dump(r, open("match-walk.json", "w"))
edges = r.get("edges", [])
mp = r.get("matched_points", [])
types = collections.Counter(m.get("type") for m in mp)
dists = sorted(m.get("distance_from_trace_point", 0) for m in mp if m.get("type") == "matched")
ways = []
for m in mp:
    e = m.get("edge_index")
    ways.append(edges[e].get("way_id") if e is not None and e < len(edges) else None)
changes = [i for i in range(1, len(ways)) if ways[i] is not None and ways[i - 1] is not None and ways[i] != ways[i - 1]]
uses = collections.Counter(e.get("use") for e in edges)
print("readings", len(pts), "matched points", len(mp), "by type", dict(types))
if dists:
    print("distance reading to matched line: median %.1f m, 90th %.1f m, max %.1f m" % (dists[len(dists) // 2], dists[int(len(dists) * 0.9)], dists[-1]))
print("distinct OSM ways matched", len({w for w in ways if w}), "; way changes along the walk", len(changes))
print("edge uses", dict(uses))
print("edges with a sidewalk tag", sum(1 for e in edges if e.get("sidewalk_left") or e.get("sidewalk_right")), "of", len(edges))
