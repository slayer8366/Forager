"""Per-segment view for the forested boxes: each Forest Service segment's name, number,
length, and the share of its length within 15 m and 50 m of an OSM way, so the "federal-only"
kilometres can be read as named trails. Run with `python -I named_gaps.py RAW_DIR`."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analyze as A  # noqa: E402
from shapely.ops import unary_union  # noqa: E402

raw = Path(sys.argv[1])
for i in (1, 3, 5, 7):
    name, lat, lon = A.BOXES[i]
    s, w, n, e = A.bbox(lat, lon)
    tf, _ = A.utm_transformer(lat, lon)
    x0, y0 = tf.transform(w, s)
    x1, y1 = tf.transform(e, n)
    clip = A.shp_box(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
    osm, _, _, _, _ = A.load_osm(raw / f"{i}-{name}-osm.json", tf, clip)
    fs, _ = A.load_arcgis(raw / f"{i}-{name}-fs.json", tf, clip)
    ol = A.all_lines(osm)
    b15 = unary_union([l.buffer(15) for l in ol])
    b50 = unary_union([l.buffer(50) for l in ol])
    print(f"== box {i} {name}: FS segments with under 80% of length within 50 m of OSM (trail_no, name, type, km, near15, near50)")
    rows = []
    for sg in fs:
        n15 = sum(l.intersection(b15).length for l in sg["lines"]) / sg["length"]
        n50 = sum(l.intersection(b50).length for l in sg["lines"]) / sg["length"]
        rows.append((sg["props"].get("trail_no"), sg["props"].get("trail_name"), sg["props"].get("trail_type"), sg["length"] / 1000, n15, n50))
    rows.sort(key=lambda r: -r[3])
    for r in rows:
        if r[5] < 0.8:
            print(f"  {r[0]:>10} {r[1]:<32} {r[2]:<5} {r[3]:5.1f} km  {r[4]:4.0%}  {r[5]:4.0%}")
    print(f"  (of {len(rows)} segments; those at 80% or more within 50 m are omitted)")
    # OSM ways with no federal counterpart: names and highway, longest first
    fl = A.all_lines(fs)
    if fl:
        fb = unary_union([l.buffer(15) for l in fl])
        print(f"  OSM ways with under 20% of length within 15 m of FS, longest first (highway, name, km):")
        orows = []
        for sg in osm:
            near = sum(l.intersection(fb).length for l in sg["lines"]) / sg["length"]
            if near < 0.2:
                orows.append((sg["tags"].get("highway"), sg["tags"].get("name", "(unnamed)"), sg["length"] / 1000, sg["tags"].get("informal"), sg["tags"].get("access")))
        orows.sort(key=lambda r: -r[2])
        for r in orows[:12]:
            print(f"    {r[0]:<9} {r[1]:<40} {r[2]:5.1f} km informal={r[3]} access={r[4]}")
        print(f"    ({len(orows)} such ways, {sum(r[2] for r in orows):.1f} km)")
