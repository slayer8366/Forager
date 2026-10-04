import sys, math, collections, osmium
SUBS = ["mckenzie", "green lakes", "fall creek", "salmon river", "ramona", "sandy river", "timberline", "pacific crest"]
WALK = {"path", "footway", "track", "bridleway", "steps"}
def m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2-la1)/2)**2 + math.cos(la1)*math.cos(la2)*math.sin((lo2-lo1)/2)**2
    return 2*6371000*math.asin(math.sqrt(h))
stats = collections.defaultdict(lambda: [0, 0.0, collections.Counter()])
class H(osmium.SimpleHandler):
    def way(self, w):
        n = w.tags.get("name")
        if not n or w.tags.get("highway") not in WALK: return
        if not any(s in n.lower() for s in SUBS): return
        pts = [(x.lat, x.lon) for x in w.nodes if x.location.valid()]
        s = stats[n]; s[0] += 1; s[1] += sum(m(pts[i], pts[i+1]) for i in range(len(pts)-1))
        s[2][w.tags.get("sac_scale") or "-"] += 1
H().apply_file(sys.argv[1], locations=True)
for n, (c, l, sac) in sorted(stats.items(), key=lambda x: -x[1][1])[:40]:
    print(f"{n!r}: {c} ways, {l/1000:.1f} km, sac_scale {dict(sac)}")
