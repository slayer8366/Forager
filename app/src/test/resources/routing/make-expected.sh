#!/usr/bin/env bash
# Writes brouter-cli/*.geojson: what BRouter v1.7.10's own command-line router returns on segments/ for the routes
# BRouterTrailRouterTest compares against (dispatch 2026-10-23-02). Run from the repository root:
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
route() { # name from-lon,from-lat to-lon,to-lat
  java -cp "$home/brouter-1.7.10-all.jar" btools.server.BRouter "$here/segments" "$home/profiles2" \
    "lonlats=$2|$3&profile=hiking-mountain&alternativeidx=0&format=geojson&profile:use_dynamic_range=0&" "$work/track"
  test -s "$work/track0.geojson" || { echo "$1: BRouter found no route" >&2; exit 1; }
  mv "$work/track0.geojson" "$here/brouter-cli/$1.geojson"
}
# Route ends from server/pmtiles-worker/test/fixtures/rd5/routes.json (OpenStreetMap trail nodes).
route inside-across-the-degree-line -73.9978932,41.2742564 -74.0159457,41.3036912
route inside-long -74.0422743,41.2772811 -73.996678,41.292043
