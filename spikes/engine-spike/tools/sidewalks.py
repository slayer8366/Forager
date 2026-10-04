"""Item 7: how sidewalks are mapped within 60 m of the r5 street route (either engine's line). Counts only."""
import json, math, sys, collections, osmium
pts = [tuple(p) for p in json.load(open("phone-out/brouter-hiking-mountain-r5-1.json"))["latlon"]]
def d(p, q):
    ky = 111_320.0; kx = ky * math.cos(math.radians(p[0])); return math.hypot((p[0]-q[0])*ky, (p[1]-q[1])*kx)
lat0=min(p[0] for p in pts)-0.002; lat1=max(p[0] for p in pts)+0.002; lon0=min(p[1] for p in pts)-0.003; lon1=max(p[1] for p in pts)+0.003
near = lambda q: lat0<=q[0]<=lat1 and lon0<=q[1]<=lon1 and min(d(q,p) for p in pts[::3]) < 60
c = collections.Counter(); L = collections.Counter()
class H(osmium.SimpleHandler):
    def way(self, w):
        hw = w.tags.get("highway")
        if not hw: return
        q = [(n.lat, n.lon) for n in w.nodes if n.location.valid()]
        if not q or not any(near(x) for x in q[::2] + [q[-1]]): return
        length = sum(d(q[i], q[i+1]) for i in range(len(q)-1))
        if hw == "footway" and w.tags.get("footway") == "sidewalk": k = "separate sidewalk line"
        elif hw in ("footway", "path", "pedestrian", "steps", "cycleway"): k = f"other {hw}"
        elif w.tags.get("sidewalk") or w.tags.get("sidewalk:both") or w.tags.get("sidewalk:left") or w.tags.get("sidewalk:right"):
            k = f"road with sidewalk={w.tags.get('sidewalk') or 'per-side'}"
        else: k = "road with no sidewalk tag"
        c[k] += 1; L[k] += length
H().apply_file(sys.argv[1], locations=True)
for k in sorted(c, key=lambda k: -L[k]): print(f"{k}: {c[k]} ways, {L[k]/1000:.2f} km")
