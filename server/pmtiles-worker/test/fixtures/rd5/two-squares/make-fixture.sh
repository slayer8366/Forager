#!/usr/bin/env bash
# How test/fixtures/rd5/two-squares/W110_N35.rd5 and W110_N40.rd5 were made (dispatch 2026-10-23-01), by the same
# steps as ../make-fixture.sh, over a box that crosses the 40 N five-degree line. Run once, by hand; the committed
# .rd5 files are the fixture of record. A rerun will not reproduce it byte for byte, because
# OpenStreetMap changes and WayLinker stamps the time it ran. The values it was made from are recorded below.
#
# Needs: java 21, curl, uv, unzip, git, and BRouter's jar and profiles from ../../../rd5/fetch-brouter.sh
# (BROUTER_HOME), plus all.brf, trekking.brf and softaccess.brf, which the map creator reads, fetched here
# from BRouter's source at the same commit and checked by blob hash, as the Pi's build does
# (pi-build map-build/pins.env:76).
#
# Recorded from the run: see README.md in this folder.
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

# 1. One Overpass query, as ../make-fixture.sh's, over 39.90..40.10 N, 105.40..105.22 W (about 22.2 x 15.4 km),
#    which the 40 N line crosses. See README.md for why this place.
query='[out:xml][timeout:180];way[highway](39.90,-105.40,40.10,-105.22)->.w;(.w;.w>;rel[route~"^(hiking|foot)$"](39.90,-105.40,40.10,-105.22););out body;'
curl -fsS -A "$agent" --data-urlencode "data=$query" -o "$out/boulder.osm" "${OVERPASS_URL:-https://overpass-api.de/api/interpreter}"

# 2. XML to PBF, which is what BRouter's map creator reads (OsmParser.java:3, :93 at v1.7.10).
uv run --no-project --with osmium==4.3.0 python -I - "$out/boulder.osm" "$out/boulder.osm.pbf" <<'PY'
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
  "$p/all.brf" "$p/trekking.brf" "$p/softaccess.brf" "$out/boulder.osm.pbf")
(cd "$br" && java -Xmx2g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
  nodes55 unodes55 bordernids.dat bordernodes.dat no-elevation)
(cd "$br" && java -Xmx2g -cp "$jar" -DskipEncodingCheck=true btools.mapcreator.WayLinker \
  unodes55 waytiles55 bordernodes.dat restrictions.dat "$p/lookups.dat" "$p/all.brf" segments rd5)
ls -l "$br/segments"
sha256sum "$out/boulder.osm" "$out/boulder.osm.pbf" "$br"/segments/*.rd5
