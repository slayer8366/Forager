"""Items 4, 5 and 7 (dispatch -446), from run 1 of each engine and route. Prints shares, distances and OSM
way categories only, never a position. Each route's line is matched to OSM ways with Valhalla's matcher
on the laptop, the same for both engines, so the two are compared on one footing."""
import collections, json, math, sys
import valhalla

cfg = sys.argv[1]
a = valhalla.Actor(cfg)
routes = json.load(open("routes.json"))
names = {"r1": "McKenzie River Trail #3507", "r2": "Salmon River Trail #742", "r3": "Green Lakes Trail #17",
         "r4": "Salmon River Trail #742", "r5": None}


def dist(p, q):
    la1, lo1, la2, lo2 = map(math.radians, (p[0], p[1], q[0], q[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


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


def line(engine, r):
    if engine == "valhalla":
        j = json.load(open(f"phone-out/valhalla-{r}-1.json"))
        return [p for leg in j["trip"]["legs"] for p in decode6(leg["shape"])]
    return [tuple(p) for p in json.load(open(f"phone-out/brouter-hiking-mountain-{r}-1.json"))["latlon"]]


for r in routes:
    for engine in ("valhalla", "brouter"):
        pts = line(engine, r)
        start = routes[r][0]
        req = {"shape": [{"lat": p[0], "lon": p[1]} for p in pts], "costing": "pedestrian", "shape_match": "map_snap",
               "filters": {"attributes": ["edge.length", "edge.use", "edge.names", "edge.sac_scale", "edge.surface", "edge.sidewalk_left", "edge.sidewalk_right"], "action": "include"}}
        try:
            m = json.loads(a.trace_attributes(json.dumps(req)))
        except Exception as e:
            print(r, engine, "match failed:", str(e)[:120]); continue
        by_use = collections.Counter(); sac = collections.Counter(); on_named = 0.0; total = 0.0; sidewalk = 0.0
        for e in m.get("edges", []):
            L = e.get("length", 0) * 1000; total += L
            by_use[e.get("use")] += L
            sac[e.get("sac_scale", "none")] += L
            if names[r] and names[r] in (e.get("names") or []): on_named += L
            if e.get("sidewalk_left") or e.get("sidewalk_right"): sidewalk += L
        share = {k: round(v / total * 100) for k, v in by_use.most_common()} if total else {}
        extra = f", on {names[r]!r} {on_named / total * 100:.0f}%" if names[r] and total else ""
        snap = dist(start, pts[0])
        print(f"{r} {engine:8}: matched {total / 1000:.2f} km; by use (%) {share}; sac_scale (km) "
              f"{ {k: round(v / 1000, 1) for k, v in sac.items()} }{extra}; edges tagged sidewalk {sidewalk / max(total, 1) * 100:.0f}%; "
              f"first route point {snap:.0f} m from the requested start")
