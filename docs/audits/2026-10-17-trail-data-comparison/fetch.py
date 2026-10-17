"""Bounded, polite queries of trail data for the seven sample boxes.

Run with `python -I fetch.py RAW_DIR LOG_PATH [box_ids...]`. Raw responses go to RAW_DIR
(outside the repository: they are untrusted downloaded data and some are a few MB); every
request is appended to LOG_PATH as a TSV row (UTC time, box, source, URL, status, bytes,
records). Nothing national is fetched: every request is an envelope of about 10 x 10 km.
"""
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

UA = "forager-trail-comparison/0.1 (read-only research; contact armyofwann@gmail.com)"
PAUSE_S = 2.0
BOXES = {
    1: ("mount-hood", 45.33, -121.78),
    2: ("eagle-cap", 45.20, -117.26),
    3: ("chamberlain-basin", 45.37, -115.20),
    4: ("oregon-badlands", 43.97, -121.05),
    5: ("cape-perpetua", 44.30, -124.04),
    6: ("tiger-mountain", 47.50, -121.98),
    7: ("three-sisters", 44.10, -121.80),
}
FS = "https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_TrailNFSPublish_01/MapServer/0/query"
BLM = "https://gis.blm.gov/arcgis/rest/services/transportation/BLM_Natl_GTLF_Public_Display/MapServer/{layer}/query"
TNM = "https://carto.nationalmap.gov/arcgis/rest/services/transportation/MapServer/37/query"
OVERPASS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]


def bbox(lat, lon, side_km=10.0):
    dlat = side_km / 111.32 / 2
    dlon = side_km / (111.32 * math.cos(math.radians(lat))) / 2
    return (lat - dlat, lon - dlon, lat + dlat, lon + dlon)  # south, west, north, east


def log(path, box, source, url, status, nbytes, records):
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    with open(path, "a", encoding="utf-8") as f:
        f.write(f"{ts}\t{box}\t{source}\t{url}\t{status}\t{nbytes}\t{records}\n")


def get(url, data=None, tries=3):
    delay = 5.0
    for attempt in range(tries):
        req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                body = r.read()
                return r.status, body
        except urllib.error.HTTPError as e:
            status = e.code
            body = e.read()
            if status in (429, 500, 502, 503, 504) and attempt + 1 < tries:
                print(f"  HTTP {status}, backing off {delay:.0f}s", file=sys.stderr)
                time.sleep(delay)
                delay *= 2
                continue
            return status, body
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt + 1 < tries:
                print(f"  {e}, backing off {delay:.0f}s", file=sys.stderr)
                time.sleep(delay)
                delay *= 2
                continue
            return -1, str(e).encode()
    return -1, b""


def arcgis_query(base, bb, logpath, box, source, where="1=1"):
    s, w, n, e = bb
    feats = []
    offset = 0
    while True:
        params = {
            "f": "geojson",
            "where": where,
            "geometry": f"{w},{s},{e},{n}",
            "geometryType": "esriGeometryEnvelope",
            "inSR": "4326",
            "spatialRel": "esriSpatialRelIntersects",
            "outFields": "*",
            "returnGeometry": "true",
            "outSR": "4326",
            "resultOffset": str(offset),
            "resultRecordCount": "2000",
        }
        url = base + "?" + urllib.parse.urlencode(params)
        status, body = get(url)
        n_rec = None
        page = None
        if status == 200:
            try:
                page = json.loads(body)
            except json.JSONDecodeError:
                page = None
        if page is not None and "features" in page:
            n_rec = len(page["features"])
            feats.extend(page["features"])
        log(logpath, box, source, url, status, len(body), n_rec)
        if page is None or "features" not in page:
            print(f"  {source}: status {status}: {body[:300]!r}", file=sys.stderr)
            return {"error": status, "body": body[:2000].decode(errors="replace"), "features": feats}
        exceeded = page.get("properties", {}).get("exceededTransferLimit") or page.get("exceededTransferLimit")
        if not exceeded and n_rec < 2000:
            break
        offset += n_rec
        time.sleep(PAUSE_S)
    return {"type": "FeatureCollection", "features": feats}


def overpass_query(bb, logpath, box):
    s, w, n, e = bb
    q = (
        f"[out:json][timeout:120][bbox:{s},{w},{n},{e}];"
        "(way[highway~'^(path|footway|track|bridleway|steps)$'];"
        "way[sac_scale];way[trail_visibility];);"
        "out tags geom;"
    )
    for ep in OVERPASS:
        status, body = get(ep, data=urllib.parse.urlencode({"data": q}).encode())
        n_rec = None
        page = None
        if status == 200:
            try:
                page = json.loads(body)
                n_rec = len(page.get("elements", []))
            except json.JSONDecodeError:
                page = None
        log(logpath, box, "osm", ep + " data=" + q, status, len(body), n_rec)
        if page is not None:
            page["_query"] = q
            page["_endpoint"] = ep
            return page
        print(f"  overpass {ep}: status {status}: {body[:200]!r}", file=sys.stderr)
        time.sleep(PAUSE_S * 5)
    return {"error": "all endpoints failed", "_query": q}


def main():
    raw = Path(sys.argv[1])
    logpath = sys.argv[2]
    ids = [int(a) for a in sys.argv[3:]] or sorted(BOXES)
    raw.mkdir(parents=True, exist_ok=True)
    for i in ids:
        name, lat, lon = BOXES[i]
        bb = bbox(lat, lon)
        print(f"box {i} {name} bbox S{bb[0]:.5f} W{bb[1]:.5f} N{bb[2]:.5f} E{bb[3]:.5f}", file=sys.stderr)
        jobs = [
            ("fs", lambda: arcgis_query(FS, bb, logpath, i, "fs")),
            ("tnm", lambda: arcgis_query(TNM, bb, logpath, i, "tnm")),
            ("osm", lambda: overpass_query(bb, logpath, i)),
        ]
        for layer in range(2, 8):
            jobs.append((f"blm{layer}", (lambda L: (lambda: arcgis_query(BLM.format(layer=L), bb, logpath, i, f"blm{L}")))(layer)))
        for source, fn in jobs:
            out = raw / f"{i}-{name}-{source}.json"
            if out.exists():
                continue
            result = fn()
            out.write_text(json.dumps(result))
            n = len(result.get("features", result.get("elements", [])))
            print(f"  {source}: {n} records", file=sys.stderr)
            time.sleep(PAUSE_S)


if __name__ == "__main__":
    main()
