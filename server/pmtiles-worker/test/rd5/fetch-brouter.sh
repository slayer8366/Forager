#!/usr/bin/env bash
# Fetches BRouter v1.7.10 for the cutter's tests (dispatch 2026-10-21-07): the release zip, checked against
# the pins the Pi's build already uses (pi-build map-build/pins.env:54-60, GitHub's own digest of the asset),
# then the jar and the two profile files the tests route with, each profile checked by its git blob hash at
# BRouter commit 4d2639af (pins.env:76). Nothing is committed; the files go in the directory given.
#
#   test/rd5/fetch-brouter.sh <dir>     then   BROUTER_HOME=<dir> node --test test/rd5/*.test.mjs
set -euo pipefail

dest=${1:?usage: fetch-brouter.sh <dir>}
url=https://github.com/abrensch/brouter/releases/download/v1.7.10/brouter-1.7.10.zip
sha256=023fec3ba997758e8cd7ab9e1bae52e962af3f00b57683e3de86b84ffad01532
size=6724983
profiles="lookups.dat:60e59d083bfff198e27e14aae6a3d4ed44e1cb2b hiking-mountain.brf:f2bed1a5c21895195d6fe47c4b1982b5f0a0d4c5"

mkdir -p "$dest/profiles2"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

curl --fail --silent --show-error --location --retry 3 \
  --user-agent "ForagerCI/1.0 (route-cutter tests; github.com/slayer8366/Forager)" \
  --output "$work/brouter.zip" "$url"
actual_size=$(stat -c %s "$work/brouter.zip")
if [ "$actual_size" != "$size" ]; then
  echo "brouter-1.7.10.zip is $actual_size bytes, pinned $size" >&2
  exit 1
fi
echo "$sha256  $work/brouter.zip" | sha256sum --check --quiet

unzip -q -o "$work/brouter.zip" -d "$work"
cp "$work/brouter-1.7.10/brouter-1.7.10-all.jar" "$dest/"
for entry in $profiles; do
  name=${entry%%:*}
  blob=${entry##*:}
  cp "$work/brouter-1.7.10/profiles2/$name" "$dest/profiles2/$name"
  actual=$(git hash-object "$dest/profiles2/$name")
  if [ "$actual" != "$blob" ]; then
    echo "$name has blob $actual, pinned $blob" >&2
    exit 1
  fi
done
echo "BRouter 1.7.10 jar and profiles in $dest"
