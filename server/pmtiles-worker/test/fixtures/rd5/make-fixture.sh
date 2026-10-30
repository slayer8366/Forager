#!/usr/bin/env bash
# How test/fixtures/rd5/W75_N40.rd5 was made (dispatch 2026-10-21-07). Run once, by hand, on 2026-10-22; the
# committed .rd5 is the fixture of record. A rerun will not reproduce it byte for byte, because
# OpenStreetMap changes and WayLinker stamps the time it ran. The values it was made from are recorded below.
#
# Needs: java 21, curl, uv, unzip, git, and BRouter's jar and profiles from ../../rd5/fetch-brouter.sh
# (BROUTER_HOME), plus all.brf, trekking.brf and softaccess.brf, which the map creator reads, fetched here
# from BRouter's source at the same commit and checked by blob hash, as the Pi's build does
# (pi-build map-build/pins.env:76).
#
# Recorded from the 2026-10-22 run:
#   Overpass osm_base     2026-10-22T04:26:47Z
#   harriman.osm          6,072,339 bytes  sha256 ca33cc587fcc6de3f8c5e50cb3c034e2a30ff0f544ca7a75ff520e28f4447585
#   harriman.osm.pbf        437,514 bytes  sha256 e7148cf5c137fd695ab85c2814c06dd76b2659bf86d43e01600fd832e279baae
#   W75_N40.rd5             152,665 bytes  sha256 ff6d9d8e439c3cdf914b1566619f64466d9af993c28070ea893425e7f5ec5990
set -euo pipefail

out=${1:?usage: make-fixture.sh <work dir>}
: "${BROUTER_HOME:?set BROUTER_HOME to the directory fetch-brouter.sh filled}"
jar="$BROUTER_HOME/brouter-1.7.10-all.jar"
agent="ForagerCoder/1.0 (route-cutter test fixture; github.com/slayer8366/Forager)"
mkdir -p "$out/profiles2"
cp "$BROUTER_HOME"/profiles2/* "$out/profiles2/"
for entry in all.brf:35b46729f73ffb179cfd14e9614d1c6adc796d4e trekking.brf:42135d41d1adbbc9c09eeaab190350a1ea2de3cc \
             softaccess.brf:88d5a81d3f01fc940b2720473bcc1d03fa77a175; do
  name=${entry%%:*}; blob=${entry##*:}
  curl -fsS -A "$agent" -o "$out/profiles2/$name" \
    "https://raw.githubusercontent.com/abrensch/brouter/4d2639af77ea5ed9c30d3e400764eb6f9e8522da/misc/profiles2/$name"
  [ "$(git hash-object "$out/profiles2/$name")" = "$blob" ] || { echo "$name blob mismatch" >&2; exit 1; }
done

# 1. One Overpass query: every highway way with a point in the box, kept whole with all its nodes (as the
#    engine spike's clip_region.py keeps ways whole), and the hiking and foot route relations in the box.
#    Box: 41.22..41.36 N, 74.13..73.96 W, about 15.6 x 14.2 km. See README.md for why this place.
query='[out:xml][timeout:180];way[highway](41.22,-74.13,41.36,-73.96)->.w;(.w;.w>;rel[route~"^(hiking|foot)$"](41.22,-74.13,41.36,-73.96););out body;'
curl -fsS -A "$agent" --data-urlencode "data=$query" -o "$out/harriman.osm" https://overpass-api.de/api/interpreter

# 2. XML to PBF, which is what BRouter's map creator reads (OsmParser.java:3, :93 at v1.7.10).
uv run --no-project --with osmium==4.3.0 python -I - "$out/harriman.osm" "$out/harriman.osm.pbf" <<'PY'
import sys
import osmium
writer = osmium.SimpleWriter(sys.argv[2], overwrite=True)
class Copy(osmium.SimpleHandler):
    def node(self, n): writer.add_node(n)
    def way(self, w): writer.add_way(w)
    def relation(self, r): writer.add_relation(r)
Copy().apply_file(sys.argv[1])
writer.close()
PY

# 3. BRouter's three map-creator steps, exactly as the Pi runs them (pi-build map-build/bin/build-orwa.sh:227-236),
#    with an empty elevation directory, so the square carries no elevation, like the Pi's before 2026-10-28.
br="$out/br"
mkdir -p "$br"/{nodetiles,waytiles,waytiles55,nodes55,unodes55,segments,no-elevation}
p="$out/profiles2"
(cd "$br" && java -Xmx2g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.OsmFastCutter \
  "$p/lookups.dat" nodetiles waytiles nodes55 waytiles55 bordernids.dat relations.dat restrictions.dat \
  "$p/all.brf" "$p/trekking.brf" "$p/softaccess.brf" "$out/harriman.osm.pbf")
(cd "$br" && java -Xmx2g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
  nodes55 unodes55 bordernids.dat bordernodes.dat no-elevation)
(cd "$br" && java -Xmx2g -cp "$jar" -DskipEncodingCheck=true btools.mapcreator.WayLinker \
  unodes55 waytiles55 bordernodes.dat restrictions.dat "$p/lookups.dat" "$p/all.brf" segments rd5)
ls -l "$br/segments"
sha256sum "$out/harriman.osm" "$out/harriman.osm.pbf" "$br/segments/W75_N40.rd5"
