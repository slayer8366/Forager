#!/usr/bin/env bash
# Writes brouter-cli/*.geojson: what BRouter v1.7.10's own command-line router returns on segments/ for the routes
# BRouterTrailRouterTest compares against (dispatch 2026-10-23-02), and brouter-cli/heighted/*.geojson: what it
# returns on segments-heighted/ for every route in routes.json (dispatch 2026-10-30-06, Amendment 3). Run from the
# repository root:
#
#   app/src/test/resources/routing/make-expected.sh <dir>
#
# where <dir> holds the release jar and profiles that server/pmtiles-worker/test/rd5/fetch-brouter.sh downloads
# and checks against their pins. The query is the route cutter's (brouter-cli.mjs), with dynamic range off.
set -euo pipefail
home=${1:?usage: make-expected.sh <dir from fetch-brouter.sh>}
here=app/src/test/resources/routing
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
route() { # data-folder out-folder name from-lon,from-lat to-lon,to-lat
  java -cp "$home/brouter-1.7.10-all.jar" btools.server.BRouter "$here/$1" "$home/profiles2" \
    "lonlats=$4|$5&profile=hiking-mountain&alternativeidx=0&format=geojson&profile:use_dynamic_range=0&" "$work/track"
  test -s "$work/track0.geojson" || { echo "$1/$3: BRouter found no route" >&2; exit 1; }
  mv "$work/track0.geojson" "$here/$2/$3.geojson"
}
# Route ends from server/pmtiles-worker/test/fixtures/rd5/routes.json (OpenStreetMap trail nodes).
# On segments/, the square without heights (the cutter's first fixture, 2026-10-22):
route segments brouter-cli inside-across-the-degree-line -73.9978932,41.2742564 -74.0159457,41.3036912
route segments brouter-cli inside-long -74.0422743,41.2772811 -73.996678,41.292043
# On segments-heighted/, the square with 3" heights (the cutter's fixture since 2026-10-30), all eight routes:
mkdir -p "$here/brouter-cli/heighted"
route segments-heighted brouter-cli/heighted inside-across-the-degree-line -73.9978932,41.2742564 -74.0159457,41.3036912
route segments-heighted brouter-cli/heighted inside-short -74.0013283,41.2741743 -74.0189684,41.2788313
route segments-heighted brouter-cli/heighted inside-long -74.0422743,41.2772811 -73.996678,41.292043
route segments-heighted brouter-cli/heighted edge-north-a -74.0006515,41.3047796 -74.0355387,41.3053493
route segments-heighted brouter-cli/heighted edge-north-b -73.9991069,41.3061883 -74.0438051,41.3020883
route segments-heighted brouter-cli/heighted detour-west -74.0832124,41.2940064 -74.0887379,41.2535731
route segments-heighted brouter-cli/heighted outside-west-island -73.9994484,41.2749253 -74.1114362,41.2684229
route segments-heighted brouter-cli/heighted outside-west-moved -73.9994484,41.2749253 -74.1204499,41.2588594
