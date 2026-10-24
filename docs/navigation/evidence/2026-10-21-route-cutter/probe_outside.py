# Evidence for route-proof.test.mjs (dispatch 2026-10-21-07): 30 trail points outside the cut's data, each routed from
# the outside routes' start on the full fixture and on the one-cell cut (cutm1), BRouter default profile.
# Args: harriman.osm (see make-fixture.sh) brouter-1.7.10-all.jar profiles2; needs try/full and cutm1 dirs.
import json, os, random, subprocess, sys, xml.etree.ElementTree as ET
osm, jar, prof = sys.argv[1:4]
pos = {n.get("id"): (float(n.get("lat")), float(n.get("lon"))) for n in ET.parse(osm).getroot().iter("node")}
trail = set()
for w in ET.parse(osm).getroot().iter("way"):
    t = {x.get("k"): x.get("v") for x in w.iter("tag")}
    if t.get("highway") in ("path", "footway", "track"):
        trail.update(nd.get("ref") for nd in w.iter("nd"))
C1 = (41.21875, -74.09375, 41.34375, -73.9375)
out = [i for i in trail if i in pos and 41.22 < pos[i][0] < 41.36 and -74.13 < pos[i][1] < -73.96 and not (C1[0] <= pos[i][0] <= C1[2] and C1[1] <= pos[i][1] <= C1[3])]
random.seed(5); start = (41.2749253, -73.9994484)
for i in random.sample(sorted(out), 30):
    b = pos[i]; q = "lonlats=%f,%f|%f,%f&profile=hiking-mountain&alternativeidx=0&format=geojson&" % (start[1], start[0], b[1], b[0])
    res = []
    for d in ("try/full", "cutm1"):
        r = subprocess.run(["java", "-cp", jar, "btools.server.BRouter", d, prof, q, os.path.abspath("p")], capture_output=True, text=True)
        if os.path.exists("p0.geojson"):
            f = json.load(open("p0.geojson"))["features"][0]; os.remove("p0.geojson"); e = f["geometry"]["coordinates"][-1]
            res.append("ok end=(%.5f,%.5f)" % (e[1], e[0]))
        else: res.append(r.stdout.strip().splitlines()[-1])
    print(i, b, " | ".join(res))
