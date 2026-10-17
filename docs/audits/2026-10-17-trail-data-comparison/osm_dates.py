"""Per box: the last-edit timestamps of the OSM ways in the trail query (from the osm-meta
responses). Prints the count, the oldest, median and newest timestamp, the share edited within
the last two years, and the median across all boxes. Run with `python -I osm_dates.py RAW_DIR`."""
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch as F  # noqa: E402

raw = Path(sys.argv[1])
for i, (name, _, _) in sorted(F.BOXES.items()):
    p = raw / f"{i}-{name}-osm-meta.json"
    if not p.exists():
        print(f"box {i} {name}: no meta response")
        continue
    d = json.loads(p.read_text())
    ts = sorted(e["timestamp"] for e in d.get("elements", []) if e.get("type") == "way" and "timestamp" in e and e.get("tags", {}).get("footway") not in ("sidewalk", "crossing"))
    if not ts:
        print(f"box {i} {name}: 0 ways")
        continue
    recent = sum(1 for t in ts if t >= "2024-10-17") / len(ts)
    years = sorted(t[:4] for t in ts)
    print(f"box {i} {name}: {len(ts)} ways; oldest {ts[0][:10]}, median {ts[len(ts)//2][:10]}, newest {ts[-1][:10]}; edited in last two years {recent:.0%}; median year {statistics.median(int(y) for y in years):.0f}")
