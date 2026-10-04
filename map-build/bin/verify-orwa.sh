#!/usr/bin/env bash
# Change 7's verification for dispatch 2026-09-28-495, run after a build:
#   1. the archive's header: zooms, layers, and bounds against the extracts';
#   2. the zoom 15 tile at a named place: trail features carrying the new keys, compared with the
#      same ways' tags in the extract;
#   3. the zoom 14 tile at that place against the live archive (us.pmtiles, read-only through the
#      pmtiles CLI, owner's ruling D): the same layers, keys and kind values, apart from the new keys;
#   4. a BRouter route between two named places on the new .rd5 files: its length, and its share on
#      the named trail.
#
# Usage: verify-orwa.sh OUTPUT_DIR SCRATCH_DIR "PLACE" "ROUTE_START" "ROUTE_END" TRAIL_NAME_TEXT
#   e.g. verify-orwa.sh /srv/forager-build/out/current ~/scratch "Ramona Falls" \
#          "Ramona Falls Trailhead" "Ramona Falls" Ramona
#
# Places are found by exact name in the build's own extracts. Coordinates, tile numbers, tiles and
# the route's track go only into SCRATCH_DIR. Standard output carries names, counts, keys and
# lengths, so it can be quoted in a report: positions never enter the repository.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

out="$(cd "${1:?output dir}" && pwd)"; scratch="${2:?scratch dir}"
place="${3:?place}"; start="${4:?route start}"; end="${5:?route end}"; trail="${6:?trail name text}"
mkdir -p "$scratch"; scratch="$(cd "$scratch" && pwd)"
pm="$out/$EXTRACT_NAME.pmtiles"
live=/srv/forager-tiles/us.pmtiles
TRAIL_KEYS=sac_scale,trail_visibility,informal,tracktype,surface,foot
inputs=($(python3 -c 'import json,sys; m=json.load(open(sys.argv[1])); print(" ".join(i["name"] for i in m["inputs"]))' "$out/manifest.json"))
pbfs=(); for n in "${inputs[@]}"; do pbfs+=("$FB_SRV/sources/osm/$n"); done

echo "== 1. Archive header"
pmtiles show --header-json "$pm" > "$scratch/header.json"
pmtiles show --metadata "$pm" > "$scratch/metadata.json"
python3 "$here/tools/union_bounds.py" "${pbfs[@]}" > "$scratch/extract-bounds.txt"
python3 - "$scratch/header.json" "$scratch/metadata.json" "$scratch/extract-bounds.txt" <<'EOF'
import json, sys
h = json.load(open(sys.argv[1])); m = json.load(open(sys.argv[2]))
want = [float(v) for v in open(sys.argv[3]).read().strip().split(",")]
layers = {l["id"]: sorted(l.get("fields", {})) for l in m.get("vector_layers", [])}
same = all(abs(a - b) < 1e-6 for a, b in zip(h["bounds"], want))
print(f"zooms {h['minzoom']}-{h['maxzoom']}, tile type {h['tile_type']}, compression {h['tile_compression']}")
print(f"bounds equal to the union of the extracts' header boxes: {same}")
print(f"layers ({len(layers)}): {', '.join(sorted(layers))}")
print(f"roads fields: {', '.join(layers.get('roads', []))}")
for k in ("name", "version", "planetiler:osm:osmosisreplicationtime", "planetiler:version", "attribution"):
    if k in m: print(f"metadata {k}: {m[k]}")
EOF

echo "== Locating the named places in the extracts (positions to scratch only)"
for n in "$place" "$start" "$end"; do
  for f in "${pbfs[@]}"; do osmium tags-filter -O -o "$scratch/named-$(basename "$f")" "$f" "nw/name=$n"; done
  osmium merge -O -o "$scratch/named.osm.pbf" "$scratch"/named-*.osm.pbf
  osmium export -O -f geojsonseq -o "$scratch/named-$(echo "$n" | tr ' ' '_').geojsonseq" "$scratch/named.osm.pbf"
done
python3 - "$scratch" "$place" "$start" "$end" <<'EOF'
import json, os, sys
scratch, names = sys.argv[1], sys.argv[2:]
def centroid(g):
    t, c = g["type"], g["coordinates"]
    pts = [c] if t == "Point" else c if t == "LineString" else c[0] if t == "Polygon" else c[0][0]
    return sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts)
found = {}
for n in names:
    feats = []
    with open(os.path.join(scratch, f"named-{n.replace(' ', '_')}.geojsonseq")) as f:
        for line in f:
            line = line.strip().lstrip("\x1e")
            if line:
                ft = json.loads(line)
                if ft["properties"].get("name") == n:
                    feats.append(ft)
    # Prefer the feature whose type fits the name: a waterfall for a falls, a trailhead or its parking.
    def rank(ft):
        p = ft["properties"]
        if p.get("waterway") == "waterfall" or p.get("natural") == "waterfall": return 0
        if p.get("highway") == "trailhead": return 1
        if p.get("amenity") == "parking": return 2
        return 3
    feats.sort(key=rank)
    if not feats:
        sys.exit(f"no feature named exactly '{n}' in the extracts")
    ft = feats[0]
    found[n] = centroid(ft["geometry"])
    tags = {k: v for k, v in ft["properties"].items() if k in ("waterway", "natural", "highway", "amenity", "tourism")}
    print(f"'{n}': {len(feats)} feature(s) with that name; using a {ft['geometry']['type']} tagged {tags}")
json.dump(found, open(os.path.join(scratch, "places.json"), "w"))
EOF

echo "== 2. Zoom 15 tile at '$place': trail features with the new keys"
python3 - "$scratch" "$place" <<'EOF'
import json, math, os, sys
scratch, place = sys.argv[1], sys.argv[2]
lon, lat = json.load(open(os.path.join(scratch, "places.json")))[place]
for z in (14, 15):
    n = 2 ** z
    x = int((lon + 180) / 360 * n)
    y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n)
    open(os.path.join(scratch, f"tile{z}.txt"), "w").write(f"{z} {x} {y}\n")
EOF
read -r z x y < "$scratch/tile15.txt"; pmtiles tile "$pm" "$z" "$x" "$y" > "$scratch/new-z15.mvt"
python3 "$here/tools/mvt_decode.py" features "$scratch/new-z15.mvt" --layer roads --has "$TRAIL_KEYS" > "$scratch/new-z15-trail-features.json"
python3 - "$scratch/new-z15-trail-features.json" <<'EOF'
import json, sys, collections
feats = json.load(open(sys.argv[1]))
print(f"roads features carrying any trail key: {len(feats)}")
summary = collections.defaultdict(lambda: collections.Counter())
for f in feats:
    p = f["properties"]
    label = f"{p.get('name', '(unnamed)')} [{p.get('kind')}/{p.get('kind_detail')}]"
    for k in ("sac_scale", "trail_visibility", "informal", "tracktype", "surface", "foot"):
        if k in p: summary[label][f"{k}={p[k]}"] += 1
for label, c in sorted(summary.items()):
    print(f"  {label}: {', '.join(f'{k} x{v}' for k, v in sorted(c.items()))}")
EOF

echo "== 2b. The same ways' tags in the extract"
python3 - "$scratch" <<'EOF'
import json, os, sys
scratch = sys.argv[1]
places = json.load(open(os.path.join(scratch, "places.json")))
lons = [p[0] for p in places.values()]; lats = [p[1] for p in places.values()]
pad = 0.02
open(os.path.join(scratch, "bbox.txt"), "w").write(f"{min(lons)-pad},{min(lats)-pad},{max(lons)+pad},{max(lats)+pad}")
EOF
for f in "${pbfs[@]}"; do osmium extract -O -s complete_ways -b "$(cat "$scratch/bbox.txt")" -o "$scratch/area-$(basename "$f")" "$f"; done
osmium merge -O -o "$scratch/area.osm.pbf" "$scratch"/area-*.osm.pbf
osmium tags-filter -O -o "$scratch/area-ways.osm.pbf" "$scratch/area.osm.pbf" w/highway
osmium export -O -f geojsonseq -o "$scratch/area-ways.geojsonseq" "$scratch/area-ways.osm.pbf"
python3 - "$scratch/area-ways.geojsonseq" "$scratch/new-z15-trail-features.json" <<'EOF'
import json, sys, collections
osm = collections.defaultdict(collections.Counter)
with open(sys.argv[1]) as f:
    for line in f:
        line = line.strip().lstrip("\x1e")
        if not line: continue
        p = json.loads(line)["properties"]
        if p.get("highway") not in ("path", "footway", "track", "bridleway", "steps") or "name" not in p: continue
        for k in ("sac_scale", "trail_visibility", "informal", "tracktype", "surface", "foot"):
            if k in p: osm[p["name"]][f"{k}={p[k]}"] += 1
tile = collections.defaultdict(set)
for ft in json.load(open(sys.argv[2])):
    p = ft["properties"]
    for k in ("sac_scale", "trail_visibility", "informal", "tracktype", "surface", "foot"):
        if k in p and "name" in p: tile[p["name"]].add(f"{k}={p[k]}")
for name in sorted(tile):
    o = set(osm.get(name, {}))
    print(f"  {name}: tile {sorted(tile[name])}; extract ways of that name carry {sorted(o)}; "
          f"every tile value present in the extract: {tile[name] <= o}")
EOF

echo "== 3. Zoom 14 tile against the live archive (read-only, pmtiles CLI)"
read -r z x y < "$scratch/tile14.txt"
pmtiles tile "$pm" "$z" "$x" "$y" > "$scratch/new-z14.mvt"
pmtiles tile "$live" "$z" "$x" "$y" > "$scratch/live-z14.mvt"
python3 "$here/tools/mvt_decode.py" schema "$scratch/live-z14.mvt" > "$scratch/live-z14-schema.json"
python3 "$here/tools/mvt_decode.py" schema "$scratch/new-z14.mvt" > "$scratch/new-z14-schema.json"
python3 - "$scratch/live-z14-schema.json" "$scratch/new-z14-schema.json" <<'EOF'
import json, sys
a, b = json.load(open(sys.argv[1])), json.load(open(sys.argv[2]))
print(f"live layers: {sorted(a)}")
print(f"new layers:  {sorted(b)}")
print(f"roads kind, live: {a.get('roads', {}).get('kind')}; new: {b.get('roads', {}).get('kind')}")
print(f"roads kind_detail, live: {a.get('roads', {}).get('kind_detail')}; new: {b.get('roads', {}).get('kind_detail')}")
EOF
python3 "$here/tools/mvt_decode.py" compare "$scratch/live-z14.mvt" "$scratch/new-z14.mvt" --ignore-keys "$TRAIL_KEYS" || true

echo "== 4. BRouter: '$start' to '$end' on the new .rd5 files, hiking-mountain"
read -r route < <(python3 -c 'import json,sys; p=json.load(open(sys.argv[1])); print("%.6f,%.6f|%.6f,%.6f" % (*p[sys.argv[2]], *p[sys.argv[3]]))' "$scratch/places.json" "$start" "$end")
(cd "$scratch" && rm -f testtrack0.gpx && "$FB_OPT/jdk/bin/java" -Xmx512m -cp "$FB_OPT/brouter/brouter-$BROUTER_VERSION-all.jar" \
  btools.server.BRouter "$out/segments" "$FB_OPT/brouter/profiles2" 0 hiking-mountain "$route" > brouter.out 2>&1) || true
if [[ ! -s "$scratch/testtrack0.gpx" ]]; then echo "no route produced; BRouter said:"; grep -v -E '[-0-9]+\.[0-9]{4,}' "$scratch/brouter.out" | head -20; exit 1; fi
grep -oE 'track-length = [0-9]+|filtered ascend = -?[0-9]+|time=[0-9.]+|energy=[0-9.]+' "$scratch/testtrack0.gpx" | sort -u | head -5
python3 "$here/tools/route_on_named_ways.py" "$scratch/testtrack0.gpx" "$scratch/area-ways.geojsonseq" --name-contains "$trail"
