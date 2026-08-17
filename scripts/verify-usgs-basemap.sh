#!/usr/bin/env bash
# Verifies what the USGS National Map basemap services actually do, because three of the
# claims the app makes about them cannot be checked by any unit test:
#
#   1. The endpoints serve real tiles for a US location, in the z/y/x order osmdroid's
#      TileSourceFactory builds. A unit test can prove the app *builds* that URL
#      (BasemapTileSourceTest does); only the live service can prove the URL means anything.
#   2. Coverage really stops at the US border. Basemap's US-only note is shown to users in
#      place of coverage detection, so it had better be true.
#   3. The zoom ceiling is lower than the service advertises for itself. The MapServer
#      metadata claims tile levels up to 23; tiles stop well below that. Basemap.maxZoom is
#      set from the observed ceiling, not the advertised one, and this script is the evidence.
#
# A 200 is deliberately not treated as success on its own: this project has been bitten
# repeatedly by APIs answering 200 for requests they did not honour. Every check below
# inspects the response body's magic bytes and only counts it as a tile if it really is a
# JPEG or PNG.
set -euo pipefail

TOPO="https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer"
IMAGERY="https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer"

# Portland, Oregon (45.326, -122.634) — the reference location used throughout this project.
# Tile x/y are precomputed for the Web Mercator grid so this script needs no trig at runtime;
# BasemapTileSourceTest pins the same z12 tile, so the two agree by construction.
#   z12 -> x=652   y=1468
#   z15 -> x=5221  y=11745
#   z16 -> x=10443 y=23490
#   z17 -> x=20886 y=46981
# Paris, France (48.857, 2.352), the non-US control:
#   z12 -> x=2074  y=1409

# Reporting the comparison is this script's whole job, so an unreachable host must not abort
# the run via `set -e`. The failure is recorded and printed, not swallowed.
#
# Note the URL shape: ArcGIS serves tile/{level}/{row}/{col}, so it is z/y/x — *not* the z/x/y
# an ordinary XY tile server uses. Passing these transposed returns a valid tile for the wrong
# place on Earth rather than an error, which is exactly why it is spelled out here.
probe() {
  local base="$1" z="$2" x="$3" y="$4"
  local body
  body=$(mktemp)
  local code
  code=$(curl -sS -o "$body" -w '%{http_code}' -A "com.forager.app" \
    "$base/tile/$z/$y/$x" 2>/dev/null || echo "unreachable")
  local kind
  kind=$(python3 - "$body" <<'PY' 2>/dev/null || echo "unreadable"
import sys
head = open(sys.argv[1], "rb").read(8)
if head.startswith(b"\xff\xd8\xff"):
    print("JPEG")
elif head.startswith(b"\x89PNG\r\n\x1a\n"):
    print("PNG")
else:
    print("not-an-image")
PY
)
  local bytes
  bytes=$(wc -c < "$body" | tr -d ' ')
  rm -f "$body"
  echo "$code $kind $bytes"
}

fail=0

# An image body AND a 200. Either one alone is not the claim being made.
expect_tile() {
  local label="$1" base="$2" z="$3" x="$4" y="$5"
  read -r code kind bytes <<< "$(probe "$base" "$z" "$x" "$y")"
  printf "%-46s z=%-3s http=%-12s body=%-13s %6s bytes  " "$label" "$z" "$code" "$kind" "$bytes"
  if [ "$code" = "200" ] && { [ "$kind" = "JPEG" ] || [ "$kind" = "PNG" ]; }; then
    echo "OK (real tile served)"
  else
    echo "FAILED (expected a 200 with an image body)"
    fail=1
  fi
}

# The absence of a tile is a claim too, and it must not be satisfied by a 200 carrying an
# error page — so a 200 here fails even if the body is not an image.
expect_no_tile() {
  local label="$1" base="$2" z="$3" x="$4" y="$5"
  read -r code kind bytes <<< "$(probe "$base" "$z" "$x" "$y")"
  printf "%-46s z=%-3s http=%-12s body=%-13s %6s bytes  " "$label" "$z" "$code" "$kind" "$bytes"
  if [ "$code" = "unreachable" ]; then
    echo "SKIPPED (service unreachable)"
    fail=1
  elif [ "$code" = "200" ]; then
    echo "FAILED (a tile was served where the app documents none)"
    fail=1
  else
    echo "OK (no tile, as documented)"
  fi
}

echo "== 1. US location (Portland, Oregon) is served, at the zooms the app allows =="
expect_tile "USGSTopo        Portland"          "$TOPO"    12 652   1468
expect_tile "USGSTopo        Portland"          "$TOPO"    15 5221  11745
expect_tile "USGSImageryTopo Portland"          "$IMAGERY" 12 652   1468
expect_tile "USGSImageryTopo Portland"          "$IMAGERY" 15 5221  11745

echo
echo "== 2. The zoom ceiling is real: z16 still serves, z17 does not =="
echo "   Basemap.maxZoom is 15 — one below the observed ceiling, because the service's own"
echo "   advertised maximum (see check 4) is demonstrably wrong and a handful of sample"
echo "   points do not establish that 16 holds everywhere."
expect_tile    "USGSTopo        Portland"       "$TOPO"    16 10443 23490
expect_no_tile "USGSTopo        Portland"       "$TOPO"    17 20886 46981
expect_no_tile "USGSImageryTopo Portland"       "$IMAGERY" 17 20886 46981

echo
echo "== 3. Coverage stops outside the United States (Paris, France) =="
echo "   This is what Basemap's US-only coverage note tells the user, and why the app offers"
echo "   an explicit selector instead of hardcoding USGS or guessing at a fallback."
expect_no_tile "USGSTopo        Paris"          "$TOPO"    12 2074  1409
expect_no_tile "USGSImageryTopo Paris"          "$IMAGERY" 12 2074  1409

echo
echo "== 4. The service's advertised tile levels, for contrast =="
# Double-quoted shell string with single-quoted Python inside, so neither layer has to escape
# the other's quotes. Not a heredoc: a heredoc would take over stdin and the piped JSON would
# never reach Python — which is exactly the bug this replaced.
advertised_levels() {
  curl -sS "https://basemap.nationalmap.gov/arcgis/rest/services/$1/MapServer?f=json" 2>/dev/null \
    | python3 -c "
import json, sys
try:
    lods = json.load(sys.stdin)['tileInfo']['lods']
    print(str(lods[0]['level']) + '-' + str(lods[-1]['level']))
except Exception:
    print('unreadable')
" 2>/dev/null || echo "unreachable"
}

for name in USGSTopo USGSImageryTopo; do
  advertised=$(advertised_levels "$name")
  printf "  %-16s advertises tile levels %s" "$name" "$advertised"
  if [ "$advertised" = "0-23" ]; then
    echo "  <- contradicted by check 2 above; the app trusts the observed ceiling instead"
  else
    echo "  <- changed since this was documented; re-check Basemap.maxZoom"
    fail=1
  fi
done

echo
if [ "$fail" = "0" ]; then
  echo "All USGS basemap checks passed."
else
  echo "One or more USGS basemap checks failed or were skipped — see above."
fi
exit "$fail"
