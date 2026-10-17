"""Measures for the trail data comparison, as set in the report's method section.

Run with `python -I analyze.py RAW_DIR OUT_DIR`. Reads the raw query responses fetched by
fetch.py, writes `OUT_DIR/results.json` (every number the report quotes) and
`OUT_DIR/results.md` (the per-box tables). Requires shapely and pyproj.
"""
import json
import math
import random
import statistics
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from pyproj import Transformer
from shapely import STRtree
from shapely.geometry import LineString, MultiLineString, Point, box as shp_box, shape
from shapely.ops import unary_union

BOXES = {
    1: ("mount-hood", 45.33, -121.78),
    2: ("eagle-cap", 45.20, -117.26),
    3: ("chamberlain-basin", 45.37, -115.20),
    4: ("oregon-badlands", 43.97, -121.05),
    5: ("cape-perpetua", 44.30, -124.04),
    6: ("tiger-mountain", 47.50, -121.98),
    7: ("three-sisters", 44.10, -121.80),
}
NEAR_M = 15.0
WIDE_M = 50.0
SNAP_M = 1.0
SAMPLE_STEP_M = 10.0
SEED = 1172
FEDERAL_ORIGINATORS = ("forest service", "bureau of land management", "national park service",
                       "fish and wildlife", "bureau of reclamation", "army corps", "usfs", "blm", "nps")


def bbox(lat, lon, side_km=10.0):
    dlat = side_km / 111.32 / 2
    dlon = side_km / (111.32 * math.cos(math.radians(lat))) / 2
    return (lat - dlat, lon - dlon, lat + dlat, lon + dlon)


def utm_transformer(lat, lon):
    zone = int((lon + 180) // 6) + 1
    epsg = 32600 + zone if lat >= 0 else 32700 + zone
    return Transformer.from_crs("EPSG:4326", f"EPSG:{epsg}", always_xy=True), epsg


def to_lines(geom, tf, clip):
    """Project a GeoJSON-like geometry, clip to the box, return a list of LineStrings."""
    g = shape(geom)
    if g.is_empty:
        return []
    coords_fn = lambda xy: tf.transform(xy[0], xy[1])  # noqa: E731
    parts = list(g.geoms) if isinstance(g, MultiLineString) else [g]
    out = []
    for p in parts:
        if not isinstance(p, LineString) or len(p.coords) < 2:
            continue
        pp = LineString([coords_fn(c[:2]) for c in p.coords])
        c = pp.intersection(clip)
        if c.is_empty:
            continue
        if isinstance(c, LineString):
            out.append(c)
        elif isinstance(c, MultiLineString):
            out.extend(c.geoms)
    return out


def load_osm(path, tf, clip):
    d = json.loads(Path(path).read_text())
    kept, excluded = [], Counter()
    hw = Counter()
    for el in d.get("elements", []):
        if el.get("type") != "way" or "geometry" not in el:
            continue
        tags = el.get("tags", {})
        if tags.get("footway") in ("sidewalk", "crossing"):
            excluded[tags.get("footway")] += 1
            continue
        geom = {"type": "LineString", "coordinates": [[p["lon"], p["lat"]] for p in el["geometry"]]}
        lines = to_lines(geom, tf, clip)
        if not lines:
            continue
        hw[tags.get("highway", "(none)")] += 1
        kept.append({"id": el["id"], "tags": tags, "lines": lines, "length": sum(l.length for l in lines)})
    return kept, excluded, hw, d.get("osm3s", {}), d.get("_endpoint")


def load_arcgis(path, tf, clip):
    d = json.loads(Path(path).read_text())
    kept = []
    for f in d.get("features", []):
        if not f.get("geometry"):
            continue
        lines = to_lines(f["geometry"], tf, clip)
        if not lines:
            continue
        kept.append({"props": f.get("properties", {}), "lines": lines, "length": sum(l.length for l in lines)})
    return kept, d.get("error")


def all_lines(segs):
    return [l for s in segs for l in s["lines"]]


def total_length(segs):
    return sum(s["length"] for s in segs)


def near_share(segs, other_lines, dist):
    """Share of segs' length within dist of other_lines (unioned buffer)."""
    if not segs:
        return None
    if not other_lines:
        return 0.0
    buf = unary_union([l.buffer(dist) for l in other_lines])
    near = 0.0
    for l in all_lines(segs):
        near += l.intersection(buf).length
    tot = sum(l.length for l in all_lines(segs))
    return near / tot if tot else None


def components(segs):
    """Connected pieces; returns (n_pieces, largest piece's share of length).

    Two lines are joined when an end of one lies within SNAP_M of the other line (anywhere
    along it, not only at its ends). First version rounded every vertex to a SNAP_M grid and
    joined lines sharing a grid cell; that split ends 0.1 m apart across a cell boundary and
    reported Mount Hood's Forest Service terra trails as 12 pieces when 48 of 68 segment ends
    lie within 1 m of another segment (extra_checks.py). Replaced 2026-10-17 before the report's
    findings were written; results.md was regenerated.
    """
    lines = all_lines(segs)
    if not lines:
        return None, None
    parent = list(range(len(lines)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    tree = STRtree(lines)
    for i, l in enumerate(lines):
        for c in (l.coords[0], l.coords[-1]):
            p = Point(c)
            for j in tree.query(p.buffer(SNAP_M)):
                j = int(j)
                if j != i and lines[j].distance(p) <= SNAP_M:
                    union(i, j)
    comp_len = defaultdict(float)
    for i, l in enumerate(lines):
        comp_len[find(i)] += l.length
    tot = sum(comp_len.values())
    return len(comp_len), max(comp_len.values()) / tot if tot else None


def present(v):
    return v not in (None, "", "N/A", "n/a", "Unknown", "unknown", " ")


def attr_shares(segs, fields):
    n = len(segs)
    out = {}
    for label, names in fields.items():
        if n == 0:
            out[label] = None
            continue
        c = sum(1 for s in segs if any(present(s["props"].get(f)) for f in names))
        out[label] = c / n
    return out


def osm_attr_shares(segs):
    n = len(segs)
    if n == 0:
        return {}
    def share(pred):
        return sum(1 for s in segs if pred(s["tags"])) / n
    return {
        "name": share(lambda t: "name" in t),
        "trail number (ref)": share(lambda t: "ref" in t),
        "allowed use (access/foot/bicycle/horse/motor_vehicle)": share(lambda t: any(k in t for k in ("access", "foot", "bicycle", "horse", "motor_vehicle", "motorcycle"))),
        "closure/season (seasonal, opening_hours, access=no)": share(lambda t: "seasonal" in t or "opening_hours" in t or t.get("access") == "no"),
        "difficulty (sac_scale, mtb:scale, trail_visibility)": share(lambda t: any(k in t for k in ("sac_scale", "mtb:scale", "trail_visibility"))),
        "surface": share(lambda t: "surface" in t),
        "operator": share(lambda t: "operator" in t),
        "informal=yes": share(lambda t: t.get("informal") == "yes"),
    }


def offsets(fed_segs, osm_lines, rng):
    if not fed_segs or not osm_lines:
        return {"matched": 0, "sampled": 0, "median_m": None, "q1_m": None, "q3_m": None, "per_segment_m": []}
    osm_union = unary_union(osm_lines)
    wide = osm_union.buffer(WIDE_M)
    matched = []
    for s in fed_segs:
        tot = s["length"]
        if tot < 20:
            continue
        inside = sum(l.intersection(wide).length for l in s["lines"])
        if inside / tot >= 0.5:
            matched.append(s)
    sample = matched if len(matched) <= 20 else rng.sample(matched, 20)
    per = []
    for s in sample:
        ds = []
        for l in s["lines"]:
            n = max(2, int(l.length // SAMPLE_STEP_M) + 1)
            for i in range(n):
                p = l.interpolate(i / (n - 1), normalized=True)
                ds.append(p.distance(osm_union))
        per.append(statistics.median(ds))
    per_sorted = sorted(per)
    q = statistics.quantiles(per_sorted, n=4) if len(per_sorted) >= 2 else [None, None, None]
    return {"matched": len(matched), "sampled": len(sample),
            "median_m": statistics.median(per_sorted) if per_sorted else None,
            "q1_m": q[0], "q3_m": q[2], "per_segment_m": [round(x, 1) for x in per_sorted]}


def epoch_dates(vals):
    ds = sorted(datetime.fromtimestamp(v / 1000, tz=timezone.utc).date().isoformat() for v in vals if isinstance(v, (int, float)))
    if not ds:
        return None
    return {"min": ds[0], "median": ds[len(ds) // 2], "max": ds[-1], "n": len(ds)}


def analyze_box(i, raw):
    name, lat, lon = BOXES[i]
    s, w, n, e = bbox(lat, lon)
    tf, epsg = utm_transformer(lat, lon)
    x0, y0 = tf.transform(w, s)
    x1, y1 = tf.transform(e, n)
    clip = shp_box(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
    r = {"box": i, "name": name, "centre": [lat, lon], "bbox_swne": [s, w, n, e], "epsg": epsg,
         "clip_area_km2": clip.area / 1e6}

    osm, osm_excl, osm_hw, osm3s, osm_ep = load_osm(raw / f"{i}-{name}-osm.json", tf, clip)
    fs, fs_err = load_arcgis(raw / f"{i}-{name}-fs.json", tf, clip)
    tnm, tnm_err = load_arcgis(raw / f"{i}-{name}-tnm.json", tf, clip)
    blm = []
    blm_by_layer = {}
    for layer in range(2, 8):
        segs, err = load_arcgis(raw / f"{i}-{name}-blm{layer}.json", tf, clip)
        for sg in segs:
            sg["props"]["_layer"] = layer
        blm_by_layer[layer] = {"count": len(segs), "length_km": total_length(segs) / 1000, "error": err}
        # Layer 7 ("Trails Managed for Public") republishes layers 2 to 6: in box 4 its 76 rows carry
        # the same FAMS_IDs as layers 4 and 5 together (checked 2026-10-17). It is counted, not merged.
        if layer != 7:
            blm.extend(segs)
    r["errors"] = {"fs": fs_err, "tnm": tnm_err}

    # OSM summary
    r["osm"] = {
        "endpoint": osm_ep, "osm_base_timestamp": osm3s.get("timestamp_osm_base"),
        "ways": len(osm), "length_km": total_length(osm) / 1000,
        "excluded_sidewalk_crossing": dict(osm_excl), "by_highway": dict(osm_hw),
        "length_km_by_highway": {k: sum(s["length"] for s in osm if s["tags"].get("highway", "(none)") == k) / 1000 for k in osm_hw},
        "attributes": osm_attr_shares(osm),
    }
    osm_lines = all_lines(osm)
    osm_terra = [s for s in osm if s["tags"].get("piste:type") is None]

    # FS summary
    fs_terra = [s for s in fs if (s["props"].get("trail_type") or "").upper() == "TERRA"]
    fs_fields = {
        "name": ["trail_name"], "trail number": ["trail_no"],
        "allowed use (any *_managed/accpt or allowed_terra_use)": ["allowed_terra_use", "hiker_pedestrian_managed", "hiker_pedestrian_accpt", "bicycle_managed", "pack_saddle_managed", "motorcycle_managed", "atv_managed"],
        "closure/season (any *_restricted)": ["hiker_pedestrian_restricted", "bicycle_restricted", "pack_saddle_restricted", "motorcycle_restricted", "atv_restricted"],
        "difficulty/class (trail_class, typical_trail_grade)": ["trail_class", "typical_trail_grade"],
        "surface": ["trail_surface"], "accessibility_status": ["accessibility_status"],
    }
    r["fs"] = {
        "segments": len(fs), "length_km": total_length(fs) / 1000,
        "terra_segments": len(fs_terra), "terra_length_km": total_length(fs_terra) / 1000,
        "by_trail_type": dict(Counter((s["props"].get("trail_type") or "(none)") for s in fs)),
        "attribute_subset": dict(Counter(s["props"].get("attributesubset") for s in fs)),
        "admin_org": dict(Counter(s["props"].get("admin_org") for s in fs)),
        "attributes": attr_shares(fs, fs_fields),
        "attributes_terra": attr_shares(fs_terra, fs_fields),
        "restricted_values": dict(Counter(s["props"].get("hiker_pedestrian_restricted") for s in fs).most_common(5)),
        "allowed_terra_use_values": dict(Counter(s["props"].get("allowed_terra_use") for s in fs).most_common(6)),
    }

    # TNM summary
    def is_federal(p):
        o = ((p.get("sourceoriginator") or "") + " " + (p.get("primarytrailmaintainer") or "")).lower()
        return any(k in o for k in FEDERAL_ORIGINATORS)
    tnm_fed = [s for s in tnm if is_federal(s["props"])]
    tnm_non = [s for s in tnm if not is_federal(s["props"])]
    tnm_fields = {
        "name": ["name"], "trail number": ["trailnumber"],
        "allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle)": ["hikerpedestrian", "bicycle", "packsaddle", "motorcycle", "ohvover50inches"],
        "closure/season (seasonopen)": ["seasonopen"],
        "difficulty/class": [],  # the layer has no such field
        "surface": ["trailsurface"],
    }
    r["tnm"] = {
        "segments": len(tnm), "length_km": total_length(tnm) / 1000,
        "federal_segments": len(tnm_fed), "federal_length_km": total_length(tnm_fed) / 1000,
        "nonfederal_segments": len(tnm_non), "nonfederal_length_km": total_length(tnm_non) / 1000,
        "by_originator": dict(Counter((s["props"].get("sourceoriginator"), s["props"].get("primarytrailmaintainer")) and f"{s['props'].get('sourceoriginator')} / {s['props'].get('primarytrailmaintainer')}" for s in tnm)),
        "by_trailtype": dict(Counter(s["props"].get("trailtype") for s in tnm)),
        "source_descriptions": dict(Counter(s["props"].get("sourcedatadecscription") for s in tnm).most_common(5)),
        "sourceeditdate": epoch_dates([s["props"].get("sourceeditdate") for s in tnm]),
        "loaddate": epoch_dates([s["props"].get("loaddate") for s in tnm]),
        "attributes": attr_shares(tnm, tnm_fields),
        "attributes_federal": attr_shares(tnm_fed, tnm_fields),
    }

    # BLM summary
    # Field names read from the BLM_Natl_GTLF_Public_Display layer 4 schema on 2026-10-17.
    blm_fields = {
        "name (ROUTE_PRMRY_NM)": ["ROUTE_PRMRY_NM"],
        "trail number (FAMS_ID or ROUTE_PLAN_ID)": ["FAMS_ID", "ROUTE_PLAN_ID"],
        "allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN)": ["PLAN_ALLOW_MODE_TRNSPRT", "PLAN_MODE_TRNSPRT", "PLAN_OHV_ROUTE_DSGNTN"],
        "closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT)": ["PLAN_SEASON_RSTRCT_CODE", "PLAN_ACCESS_RSTRCT"],
        "difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class)": ["OBSRVE_ROUTE_USE_CLASS"],
        "surface (OBSRVE_SRFCE_TYPE)": ["OBSRVE_SRFCE_TYPE"],
        "accuracy stated (ACCURACY_FT)": ["ACCURACY_FT"],
    }
    r["blm"] = {
        "segments": len(blm), "length_km": total_length(blm) / 1000, "by_layer": blm_by_layer,
        "field_names": sorted({k for s in blm for k in s["props"]})[:80],
        "attributes": attr_shares(blm, blm_fields),
    }

    # Federal merged: FS + BLM + TNM federal rows further than NEAR_M from FS/BLM
    base = fs + blm
    base_lines = all_lines(base)
    extra = []
    if tnm_fed:
        if base_lines:
            buf = unary_union([l.buffer(NEAR_M) for l in base_lines])
            for s in tnm_fed:
                inside = sum(l.intersection(buf).length for l in s["lines"])
                if inside / s["length"] < 0.5:
                    extra.append(s)
        else:
            extra = list(tnm_fed)
    fed = base + extra
    fed_lines = all_lines(fed)
    r["federal_merged"] = {"segments": len(fed), "length_km": total_length(fed) / 1000,
                           "tnm_rows_added_beyond_fs_blm": len(extra), "tnm_added_length_km": total_length(extra) / 1000}

    # Overlap
    r["overlap"] = {
        "federal_near_osm_15m": near_share(fed, osm_lines, NEAR_M),
        "osm_near_federal_15m": near_share(osm, fed_lines, NEAR_M),
        "federal_near_osm_50m": near_share(fed, osm_lines, WIDE_M),
        "osm_near_federal_50m": near_share(osm, fed_lines, WIDE_M),
        "fs_near_tnm_15m": near_share(fs, all_lines(tnm), NEAR_M),
        "tnm_near_fs_15m": near_share(tnm, all_lines(fs), NEAR_M),
    }
    fl, ol = total_length(fed), total_length(osm)
    r["overlap"]["federal_only_km_15m"] = (1 - (r["overlap"]["federal_near_osm_15m"] or 0)) * fl / 1000 if fed else 0
    r["overlap"]["osm_only_km_15m"] = (1 - (r["overlap"]["osm_near_federal_15m"] or 0)) * ol / 1000 if osm else 0
    # what OSM-only is made of
    if osm and fed_lines:
        buf = unary_union([l.buffer(NEAR_M) for l in fed_lines])
        only_by_hw = Counter()
        for s in osm:
            outside = sum(l.length - l.intersection(buf).length for l in s["lines"])
            only_by_hw[s["tags"].get("highway", "(none)")] += outside
        r["overlap"]["osm_only_km_by_highway"] = {k: v / 1000 for k, v in only_by_hw.items()}
    else:
        r["overlap"]["osm_only_km_by_highway"] = r["osm"]["length_km_by_highway"] if osm else {}

    # Connectivity
    r["connectivity"] = {}
    for label, segs in (("osm", osm), ("osm_path_footway_only", [s for s in osm if s["tags"].get("highway") in ("path", "footway", "bridleway", "steps")]), ("fs", fs), ("tnm", tnm), ("blm", blm), ("federal_merged", fed)):
        n_pieces, largest = components(segs)
        r["connectivity"][label] = {"pieces": n_pieces, "largest_share": largest, "length_km": total_length(segs) / 1000}

    # Offsets
    rng = random.Random(SEED)
    r["offset"] = offsets(fed, osm_lines, rng)
    r["offset_fs_vs_tnm"] = offsets(fs, all_lines(tnm), random.Random(SEED))
    return r


def fmt_pct(v):
    return "n/a" if v is None else f"{100 * v:.0f}%"


def fmt_km(v):
    return "n/a" if v is None else f"{v:.1f}"


def write_md(results, out):
    lines = ["# Per-box results (computed by analyze.py)", ""]
    lines.append("| # | Box | OSM ways / km | FS segs / km (terra km) | BLM segs / km | USGS segs / km (federal km) | Federal merged km | Fed near OSM 15 m | OSM near fed 15 m | Fed-only km | OSM-only km | Fed near OSM 50 m | OSM near fed 50 m |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in results:
        o, f, b, t, m, ov = r["osm"], r["fs"], r["blm"], r["tnm"], r["federal_merged"], r["overlap"]
        lines.append(f"| {r['box']} | {r['name']} | {o['ways']} / {fmt_km(o['length_km'])} | {f['segments']} / {fmt_km(f['length_km'])} ({fmt_km(f['terra_length_km'])}) | {b['segments']} / {fmt_km(b['length_km'])} | {t['segments']} / {fmt_km(t['length_km'])} ({fmt_km(t['federal_length_km'])}) | {fmt_km(m['length_km'])} | {fmt_pct(ov['federal_near_osm_15m'])} | {fmt_pct(ov['osm_near_federal_15m'])} | {fmt_km(ov['federal_only_km_15m'])} | {fmt_km(ov['osm_only_km_15m'])} | {fmt_pct(ov['federal_near_osm_50m'])} | {fmt_pct(ov['osm_near_federal_50m'])} |")
    lines += ["", "## Connectivity (pieces, largest piece's share of length)", "",
              "| # | Box | OSM | OSM paths only | FS | USGS | BLM | Federal merged |", "|---|---|---|---|---|---|---|---|"]
    for r in results:
        c = r["connectivity"]
        cell = lambda k: "none" if c[k]["pieces"] is None else f"{c[k]['pieces']} pieces, {fmt_pct(c[k]['largest_share'])}"  # noqa: E731
        lines.append(f"| {r['box']} | {r['name']} | {cell('osm')} | {cell('osm_path_footway_only')} | {cell('fs')} | {cell('tnm')} | {cell('blm')} | {cell('federal_merged')} |")
    lines += ["", "## Offset of matched federal segments from the nearest OSM way", "",
              "| # | Box | Matched segs | Sampled | Median m | Q1 m | Q3 m | FS vs USGS median m |", "|---|---|---|---|---|---|---|---|"]
    for r in results:
        o, o2 = r["offset"], r["offset_fs_vs_tnm"]
        f = lambda v: "n/a" if v is None else f"{v:.1f}"  # noqa: E731
        lines.append(f"| {r['box']} | {r['name']} | {o['matched']} | {o['sampled']} | {f(o['median_m'])} | {f(o['q1_m'])} | {f(o['q3_m'])} | {f(o2['median_m'])} |")
    lines += ["", "## Attributes (share of segments carrying the field)", ""]
    for r in results:
        lines.append(f"### Box {r['box']} {r['name']}")
        lines.append("")
        lines.append("OSM: " + ", ".join(f"{k} {fmt_pct(v)}" for k, v in r["osm"]["attributes"].items()))
        lines.append("")
        lines.append("FS: " + ", ".join(f"{k} {fmt_pct(v)}" for k, v in r["fs"]["attributes"].items()) + f"; subsets {r['fs']['attribute_subset']}; trail types {r['fs']['by_trail_type']}")
        lines.append("")
        lines.append("USGS: " + ", ".join(f"{k} {fmt_pct(v)}" for k, v in r["tnm"]["attributes"].items()) + f"; originators {r['tnm']['by_originator']}; source edit dates {r['tnm']['sourceeditdate']}; source descriptions {r['tnm']['source_descriptions']}")
        lines.append("")
        lines.append("BLM: " + ", ".join(f"{k} {fmt_pct(v)}" for k, v in r["blm"]["attributes"].items()) + f"; by layer {{ {', '.join(f'{k}: {v['count']}' for k, v in r['blm']['by_layer'].items())} }}")
        lines.append("")
        lines.append(f"OSM highway mix: {r['osm']['by_highway']}; km {{ {', '.join(f'{k}: {v:.1f}' for k, v in r['osm']['length_km_by_highway'].items())} }}; excluded {r['osm']['excluded_sidewalk_crossing']}; OSM-only km by highway {{ {', '.join(f'{k}: {v:.1f}' for k, v in r['overlap']['osm_only_km_by_highway'].items())} }}")
        lines.append("")
    (out / "results.md").write_text("\n".join(lines) + "\n")


def main():
    raw = Path(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    ids = [int(a) for a in sys.argv[3:]] or sorted(BOXES)
    results = []
    for i in ids:
        if not (raw / f"{i}-{BOXES[i][0]}-osm.json").exists():
            print(f"box {i}: not fetched yet", file=sys.stderr)
            continue
        r = analyze_box(i, raw)
        results.append(r)
        print(f"box {i} {r['name']}: osm {r['osm']['ways']} ways {r['osm']['length_km']:.1f} km; fs {r['fs']['segments']} {r['fs']['length_km']:.1f} km; "
              f"tnm {r['tnm']['segments']} {r['tnm']['length_km']:.1f} km; blm {r['blm']['segments']} {r['blm']['length_km']:.1f} km; "
              f"fed-merged {r['federal_merged']['length_km']:.1f} km; fed near osm {fmt_pct(r['overlap']['federal_near_osm_15m'])}; osm near fed {fmt_pct(r['overlap']['osm_near_federal_15m'])}; "
              f"offset median {r['offset']['median_m']}", file=sys.stderr)
    (out / "results.json").write_text(json.dumps(results, indent=1, default=str))
    write_md(results, out)


if __name__ == "__main__":
    main()
