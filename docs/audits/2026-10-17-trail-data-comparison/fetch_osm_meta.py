"""One small Overpass query per box for the last-edit timestamps of the same ways (tags and
meta only, no geometry), to answer "when were they last updated". Run with
`python -I fetch_osm_meta.py RAW_DIR LOG_PATH`; appends to the same query log as fetch.py."""
import json
import sys
import time
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch as F  # noqa: E402

raw = Path(sys.argv[1])
logpath = sys.argv[2]
for i, (name, lat, lon) in sorted(F.BOXES.items()):
    out = raw / f"{i}-{name}-osm-meta.json"
    if out.exists():
        continue
    s, w, n, e = F.bbox(lat, lon)
    q = (f"[out:json][timeout:120][bbox:{s},{w},{n},{e}];"
         "(way[highway~'^(path|footway|track|bridleway|steps)$'];way[sac_scale];way[trail_visibility];);"
         "out ids tags meta;")
    for ep in F.OVERPASS:
        status, body = F.get(ep, data=urllib.parse.urlencode({"data": q}).encode())
        page = None
        if status == 200:
            try:
                page = json.loads(body)
            except json.JSONDecodeError:
                page = None
        F.log(logpath, i, "osm-meta", ep + " data=" + q, status, len(body), None if page is None else len(page.get("elements", [])))
        if page is not None:
            out.write_text(json.dumps(page))
            print(f"box {i}: {len(page.get('elements', []))} ways with meta", file=sys.stderr)
            break
        time.sleep(10)
    time.sleep(F.PAUSE_S)
