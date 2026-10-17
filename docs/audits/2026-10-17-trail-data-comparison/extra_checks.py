"""Follow-up checks on the raw responses: what the counted attribute values actually say, and
the Mount Hood overlap and connectivity with the Forest Service snow trails set aside.
Run with `python -I extra_checks.py RAW_DIR` (needs analyze.py beside it, shapely, pyproj)."""
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analyze as A  # noqa: E402

raw = Path(sys.argv[1])


def vals(path, fields, n=6):
    d = json.loads(Path(path).read_text())
    for f in fields:
        c = Counter(str(x["properties"].get(f)) for x in d["features"])
        print(f"  {f}: {dict(c.most_common(n))}")


print("== FS management fields, box 3 chamberlain-basin (TrailNFS_MGMT)")
vals(raw / "3-chamberlain-basin-fs.json", ["allowed_terra_use", "hiker_pedestrian_managed", "hiker_pedestrian_accpt", "hiker_pedestrian_restricted", "pack_saddle_managed", "bicycle_managed", "motorcycle_managed", "trail_class", "typical_trail_grade", "accessibility_status", "special_mgmt_area"])
print("== FS management fields, box 5 cape-perpetua")
vals(raw / "5-cape-perpetua-fs.json", ["allowed_terra_use", "hiker_pedestrian_managed", "hiker_pedestrian_restricted", "bicycle_managed", "bicycle_restricted", "trail_class", "accessibility_status"])
print("== FS management fields, box 7 three-sisters")
vals(raw / "7-three-sisters-fs.json", ["allowed_terra_use", "hiker_pedestrian_managed", "hiker_pedestrian_restricted", "pack_saddle_restricted", "bicycle_managed", "trail_class", "special_mgmt_area"])
print("== FS basic fields, box 1 mount-hood (TrailNFS_Basic)")
vals(raw / "1-mount-hood-fs.json", ["allowed_terra_use", "hiker_pedestrian_managed", "hiker_pedestrian_restricted", "trail_class", "trail_type", "admin_org"])
print("== BLM fields, box 4 oregon-badlands (layers 4 and 5)")
for L in (4, 5):
    vals(raw / f"4-oregon-badlands-blm{L}.json", ["PLAN_ALLOW_MODE_TRNSPRT", "PLAN_MODE_TRNSPRT", "PLAN_OHV_ROUTE_DSGNTN", "PLAN_SEASON_RSTRCT_CODE", "PLAN_ACCESS_RSTRCT", "OBSRVE_ROUTE_USE_CLASS", "OBSRVE_SRFCE_TYPE", "ACCURACY_FT", "COORD_SRC_TYPE", "ROUTE_SPCL_DSGNTN_TYPE"])
print("== USGS use fields, box 4 and box 6")
vals(raw / "4-oregon-badlands-tnm.json", ["hikerpedestrian", "bicycle", "packsaddle", "motorcycle", "trailtype", "trailsurface", "seasonopen"])
vals(raw / "6-tiger-mountain-tnm.json", ["hikerpedestrian", "bicycle", "name", "trailtype", "sourceeditdate"])
print("== USGS use fields where the source is the Forest Service, box 7")
vals(raw / "7-three-sisters-tnm.json", ["hikerpedestrian", "bicycle", "packsaddle", "trailtype", "trailsurface", "nationaltraildesignation"])

print("== Mount Hood with Forest Service snow trails set aside")
name, lat, lon = A.BOXES[1]
s, w, n, e = A.bbox(lat, lon)
tf, _ = A.utm_transformer(lat, lon)
x0, y0 = tf.transform(w, s)
x1, y1 = tf.transform(e, n)
clip = A.shp_box(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
osm, _, _, _, _ = A.load_osm(raw / "1-mount-hood-osm.json", tf, clip)
fs, _ = A.load_arcgis(raw / "1-mount-hood-fs.json", tf, clip)
terra = [x for x in fs if (x["props"].get("trail_type") or "").upper() == "TERRA"]
snow = [x for x in fs if (x["props"].get("trail_type") or "").upper() == "SNOW"]
ol = A.all_lines(osm)
for label, segs in (("terra", terra), ("snow", snow)):
    print(f"  fs {label}: {len(segs)} segs {A.total_length(segs)/1000:.1f} km; near OSM 15 m {A.near_share(segs, ol, 15):.0%}, 50 m {A.near_share(segs, ol, 50):.0%}; pieces/largest {A.components(segs)}")
print(f"  OSM near fs terra 15 m: {A.near_share(osm, A.all_lines(terra), 15):.0%}; 50 m: {A.near_share(osm, A.all_lines(terra), 50):.0%}")
print(f"  OSM pieces/largest: {A.components(osm)}")
# Where do FS terra segments end: does a segment end touch another FS segment (within 1 m, 5 m, 15 m)?
from shapely.geometry import Point  # noqa: E402
ends = []
lines = A.all_lines(terra)
for i, l in enumerate(lines):
    for p in (Point(l.coords[0]), Point(l.coords[-1])):
        if clip.exterior.distance(p) < 5:
            continue  # clipped at the box edge
        d = min((p.distance(m) for j, m in enumerate(lines) if j != i), default=None)
        ends.append(d)
for tol in (1, 5, 15, 50):
    print(f"  FS terra segment ends (not at box edge) within {tol} m of another FS terra segment: {sum(1 for d in ends if d is not None and d <= tol)}/{len(ends)}")
