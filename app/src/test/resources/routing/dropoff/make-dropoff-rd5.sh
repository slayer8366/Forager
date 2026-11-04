#!/usr/bin/env bash
# Builds the drop-off fixture's routing square, segments/W75_N40.rd5, from dropoff.osm (make-dropoff-fixture.py), with
# BRouter 1.7.10's three map-creator steps, as server/pmtiles-worker/test/fixtures/rd5/make-fixture.sh runs them for the
# Harriman square, and as the Pi's build runs them. The committed .rd5 is the fixture of record: WayLinker stamps the
# time it ran, so a rerun differs in those bytes only.
#
#   BROUTER_HOME=<dir> make-dropoff-rd5.sh <work dir>
#
# BROUTER_HOME holds brouter-1.7.10-all.jar and profiles2/ with lookups.dat, all.brf, trekking.brf and softaccess.brf
# (server/pmtiles-worker/test/rd5/fetch-brouter.sh fetches the jar and lookups.dat; make-fixture.sh fetches the three
# .brf). Each is checked here against its pin. Needs java 21 and osmium-tool.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?usage: make-dropoff-rd5.sh <work dir>}
: "${BROUTER_HOME:?set BROUTER_HOME to a folder with brouter-1.7.10-all.jar and profiles2/}"
jar="$BROUTER_HOME/brouter-1.7.10-all.jar"
echo "93e9821640093cde82ed470ac8c08ab84fc7ae3b570e30c9ab41d686c7af1d4a  $jar" | sha256sum --check --quiet
p="$BROUTER_HOME/profiles2"
for entry in lookups.dat:60e59d083bfff198e27e14aae6a3d4ed44e1cb2b all.brf:35b46729f73ffb179cfd14e9614d1c6adc796d4e \
             trekking.brf:42135d41d1adbbc9c09eeaab190350a1ea2de3cc softaccess.brf:88d5a81d3f01fc940b2720473bcc1d03fa77a175; do
  name=${entry%%:*}; blob=${entry##*:}
  [ "$(git hash-object "$p/$name")" = "$blob" ] || { echo "$name blob mismatch" >&2; exit 1; }
done
mkdir -p "$out"
osmium cat --overwrite -o "$out/dropoff.osm.pbf" "$here/dropoff.osm"
br="$out/br"
rm -rf "$br"
mkdir -p "$br"/{nodetiles,waytiles,waytiles55,nodes55,unodes55,segments,no-elevation}
(cd "$br" && java -Xmx1g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.OsmFastCutter \
  "$p/lookups.dat" nodetiles waytiles nodes55 waytiles55 bordernids.dat relations.dat restrictions.dat \
  "$p/all.brf" "$p/trekking.brf" "$p/softaccess.brf" "$out/dropoff.osm.pbf")
(cd "$br" && java -Xmx1g -cp "$jar" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
  nodes55 unodes55 bordernids.dat bordernodes.dat no-elevation)
(cd "$br" && java -Xmx1g -cp "$jar" -DskipEncodingCheck=true btools.mapcreator.WayLinker \
  unodes55 waytiles55 bordernodes.dat restrictions.dat "$p/lookups.dat" "$p/all.brf" segments rd5)
ls -l "$br/segments"
sha256sum "$here/dropoff.osm" "$out/dropoff.osm.pbf" "$br/segments/"*.rd5
