#!/usr/bin/env bash
# How test/fixtures/rd5/W75_N40.rd5 was made. The committed .rd5 is the fixture of record; a rerun will not match it
# byte for byte, because WayLinker stamps the time it ran.
#
# Since dispatch 2026-10-30-coder-03 the square carries heights, like the Pi's squares since the elevation run (G8): the
# same box and way selection as before, cut from a dated Geofabrik extract with osmium, and PosUnifier given the pinned 3"
# elevation store. The first fixture (dispatch 2026-10-21-07, 2026-10-22) came from one Overpass query and had no
# heights; its recipe is in git history. Overpass's dated query for that snapshot failed four times on 2026-10-30
# (connection resets and a 502), so this recipe uses the extract instead, which the Pi's builds already use.
#
# Needs: java 21, curl, git, osmium 1.15, and BRouter's jar and profiles from ../../rd5/fetch-brouter.sh (BROUTER_HOME),
# plus all.brf, trekking.brf and softaccess.brf, which the map creator reads, fetched here from BRouter's source at the
# same commit and checked by blob hash, as the Pi's build does (pi-build map-build/pins.env).
#
# Usage: make-fixture.sh <work dir> <new-york-latest.osm.pbf> <elevation store dir>
#
# Recorded from the 2026-10-30 run (see README.md for the sums):
#   extract     Geofabrik north-america/us/new-york-latest.osm.pbf, Last-Modified 2026-10-30 22:27:56 GMT, MD5 checked
#   elevation   /mnt/archive/forager-build/elevation/bef3-20261027 (SHA256SUMS sha256 36b05da7...; srtm_22_04.bef)
set -euo pipefail

out=${1:?usage: make-fixture.sh <work dir> <new-york-latest.osm.pbf> <elevation store dir>}
extract=${2:?the New York extract}
bef=${3:?the elevation store}
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

# 1. The fixture's box from the extract, as the first fixture's Overpass query selected it: every highway way with a
#    node in the box, kept whole with all its nodes (osmium's complete_ways), and the hiking and foot route relations
#    with a member in the box, without their other members. Box: 41.22..41.36 N, 74.13..73.96 W.
osmium extract --overwrite -s complete_ways -b -74.13,41.22,-73.96,41.36 "$extract" -o "$out/box.osm.pbf"
osmium tags-filter --overwrite "$out/box.osm.pbf" w/highway -o "$out/ways.osm.pbf"
osmium tags-filter --overwrite -R "$out/box.osm.pbf" r/route=hiking,foot -o "$out/rels.osm.pbf"
osmium merge --overwrite "$out/ways.osm.pbf" "$out/rels.osm.pbf" -o "$out/harriman.osm.pbf"

# 2. BRouter's three map-creator steps, as the Pi runs them (pi-build map-build/bin/build-usca.sh, the routing bands),
#    with PosUnifier given the elevation store, so the square carries 3" heights like the Pi's.
br="$out/br"
mkdir -p "$br"/{nodetiles,waytiles,waytiles55,nodes55,unodes55,segments}
p="$out/profiles2"
(cd "$br" && java -Xmx2g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.OsmFastCutter \
  "$p/lookups.dat" nodetiles waytiles nodes55 waytiles55 bordernids.dat relations.dat restrictions.dat \
  "$p/all.brf" "$p/trekking.brf" "$p/softaccess.brf" "$out/harriman.osm.pbf")
(cd "$br" && java -Xmx2g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
  nodes55 unodes55 bordernids.dat bordernodes.dat "$bef")
# PosUnifier renames a node tile <SQUARE>.u5d_3 when it read a 3" raster for it, and leaves <SQUARE>.u5d when it found
# none (PosUnifier.java:128-146 at 1.7.10), saying nothing either way.
ls "$br/unodes55"
[ -e "$br/unodes55/W75_N40.u5d_3" ] || { echo "PosUnifier read no heights for W75_N40" >&2; exit 1; }
(cd "$br" && java -Xmx2g -cp "$jar" -DskipEncodingCheck=true btools.mapcreator.WayLinker \
  unodes55 waytiles55 bordernodes.dat restrictions.dat "$p/lookups.dat" "$p/all.brf" segments rd5)
ls -l "$br/segments"
sha256sum "$extract" "$out/harriman.osm.pbf" "$br/segments/W75_N40.rd5"
