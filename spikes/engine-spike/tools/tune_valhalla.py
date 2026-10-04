"""-446 Amendment 3: Valhalla 3.6.3's pedestrian costing tuned toward trails, on the laptop with pyvalhalla 3.6.3,
against the defaults. Each route's line is matched to OSM ways as in analyse_routes.py, so the shares compare with
the spike's report. Prints shares, lengths and times only, never a position."""
import collections, json, statistics, sys, time
import valhalla

a = valhalla.Actor(sys.argv[1])
routes = json.load(open("routes.json"))
names = {"r1": "McKenzie River Trail #3507", "r2": "Salmon River Trail #742", "r3": "Green Lakes Trail #17",
         "r4": "Salmon River Trail #742", "r5": None}
# Every option below is in the 3.6.3 pedestrian costing documentation. None penalises an ordinary road
# against highway=path (walkway_factor covers footway and sidewalk edges only, pedestriancost.cc:751).
SETTINGS = {
    "defaults (as the spike)": {"max_hiking_difficulty": 6},
    "A: walkways and tracks favoured": {"max_hiking_difficulty": 6, "walkway_factor": 0.5, "use_tracks": 1.0},
    "B: A, plus service roads, sidewalked roads and living streets penalised": {
        "max_hiking_difficulty": 6, "walkway_factor": 0.5, "use_tracks": 1.0, "service_factor": 3.0,
        "sidewalk_factor": 3.0, "use_living_streets": 0.0, "alley_factor": 5.0},
    "C: B, with the strongest walkway factor allowed (0.1)": {
        "max_hiking_difficulty": 6, "walkway_factor": 0.1, "use_tracks": 1.0, "service_factor": 3.0,
        "sidewalk_factor": 3.0, "use_living_streets": 0.0, "alley_factor": 5.0},
}


def decode6(s):
    out, i, lat, lon = [], 0, 0, 0
    while i < len(s):
        for which in (0, 1):
            shift = res = 0
            while True:
                b = ord(s[i]) - 63; i += 1; res |= (b & 0x1f) << shift; shift += 5
                if b < 0x20: break
            d = ~(res >> 1) if res & 1 else res >> 1
            if which == 0: lat += d
            else: lon += d
        out.append((lat / 1e6, lon / 1e6))
    return out


results = {}
for label, opts in SETTINGS.items():
    print(f"== {label}: {json.dumps(opts)}")
    for r, stops in routes.items():
        req = {"locations": [{"lat": p[0], "lon": p[1]} for p in stops], "costing": "pedestrian",
               "costing_options": {"pedestrian": opts}}
        times = []
        for _ in range(5):
            t = time.perf_counter(); resp = json.loads(a.route(json.dumps(req))); times.append((time.perf_counter() - t) * 1000)
        pts = [p for leg in resp["trip"]["legs"] for p in decode6(leg["shape"])]
        m = json.loads(a.trace_attributes(json.dumps({
            "shape": [{"lat": p[0], "lon": p[1]} for p in pts], "costing": "pedestrian", "shape_match": "map_snap",
            "filters": {"attributes": ["edge.length", "edge.use", "edge.names"], "action": "include"}})))
        total = sum(e.get("length", 0) for e in m["edges"]) * 1000
        by_use = collections.Counter()
        on = 0.0
        for e in m["edges"]:
            L = e.get("length", 0) * 1000; by_use[e.get("use")] += L
            if names[r] and names[r] in (e.get("names") or []): on += L
        share = {k: round(v / total * 100) for k, v in by_use.most_common()}
        trail = f"{on / total * 100:.0f}% on the named trail" if names[r] else "no named trail"
        print(f"  {r}: {resp['trip']['summary']['length']:.2f} km, {trail}, by use {share}, route time median {statistics.median(times):.0f} ms")
        results[(label, r)] = {"km": resp["trip"]["summary"]["length"], "on_trail": (on / total) if names[r] else None,
                               "by_use": share, "ms": statistics.median(times)}
json.dump({f"{k[0]}|{k[1]}": v for k, v in results.items()}, open("tune-valhalla.json", "w"), indent=1)
