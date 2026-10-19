#!/usr/bin/env bash
# Tests for build-usca.sh's rd5_square, z15_tile, curl_resume's partial-file handling and
# keep_failed_run. The functions are taken from the script itself, not copied, as in
# test_build_functions.sh, and run against a fake tree in a temporary directory.
#
# Run: bash map-build/tools/test_build_usca_functions.sh      (exits non-zero on the first failure)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
script="$here/bin/build-usca.sh"

T="$(mktemp -d)"
trap 'rm -rf -- "$T"' EXIT
FB_SRV="$T/srv"; OUT="$FB_SRV/out-usca"; run="$FB_SRV/work/usca-r2"; run_id=usca-r2; stage=validate
log_file="$FB_SRV/logs/usca-r2.log"
mkdir -p "$OUT/20261017T202051Z" "$FB_SRV/logs" "$run/out/segments"
ln -s 20261017T202051Z "$OUT/current"
echo "log line" > "$log_file"
log() { echo "LOG $*"; }

eval "$(sed -n '/^cleanup_scratch() {/,/^}/p; /^FAILED_KEEP=/p; /^keep_failed_run() {/,/^}/p; /^rd5_square() {/,/^}/p; /^z15_tile() {/,/^}/p; /^curl_resume() {/,/^}/p; /^basemap_matches() {/,/^}/p; /^routing_bands_ok() {/,/^}/p; /^rd5_west_edge() {/,/^}/p' "$script")"
declare -F rd5_square z15_tile curl_resume keep_failed_run cleanup_scratch basemap_matches routing_bands_ok rd5_west_edge >/dev/null \
  || { echo "FAIL: functions not loaded"; exit 1; }

pass=0
check() { if eval "$2"; then pass=$((pass + 1)); else echo "FAIL: $1"; exit 1; fi; }

# --- rd5_square: BRouter names a square by its south-west corner, in 5-degree steps --------------
check "Seattle is in W125_N45, one of build-orwa.sh's four squares" '[[ "$(rd5_square -122.335 47.608)" == W125_N45 ]]'
check "Honolulu is in W160_N20" '[[ "$(rd5_square -157.8583 21.3069)" == W160_N20 ]]'
check "San Juan is in W70_N15" '[[ "$(rd5_square -66.1057 18.4655)" == W70_N15 ]]'
check "a point on a square edge belongs to the square east of it" '[[ "$(rd5_square -120 45)" == W120_N45 ]]'
check "the Aleutians west of 180 are an E square" '[[ "$(rd5_square 172.9 52.9)" == E170_N50 ]]'

# --- z15_tile -------------------------------------------------------------------------------------
# Expected values from OpenStreetMap's slippy-map formula in its log(tan + sec) form, worked
# separately from the asinh form the script uses.
check "Seattle's z15 tile" '[[ "$(z15_tile -122.335 47.608)" == "15 5248 11443" ]]'
check "Honolulu's z15 tile" '[[ "$(z15_tile -157.8583 21.3069)" == "15 2015 14398" ]]'
check "the tile at 0,0 is the centre one" '[[ "$(z15_tile 0.0001 -0.0001)" == "15 16384 16384" ]]'

# --- curl_resume: a partial file from an earlier run is resumed, not discarded ------------------
USCA_DOWNLOAD_RETRIES=0; HTTP_USER_AGENT=test
mkdir -p "$T/dl"; printf 'first half' > "$T/dl/x.osm.pbf"
curl() { # stands in for curl: records the partial file it was handed, then "finishes" it
  local out=""; while [[ $# -gt 0 ]]; do [[ "$1" == -o ]] && out="$2"; shift; done
  cat "$out" > "$T/dl/seen"; printf ' second half' >> "$out"
}
curl_resume "https://example.invalid/x.osm.pbf" "$T/dl/x.osm.pbf"
check "the partial file was handed to curl to resume" '[[ "$(cat "$T/dl/seen")" == "first half" ]]'
check "the finished file is in place and the .part is gone" '[[ "$(cat "$T/dl/x.osm.pbf")" == "first half second half" && ! -e "$T/dl/x.osm.pbf.part" ]]'
unset -f curl

# --- keep_failed_run ------------------------------------------------------------------------------
echo "archive" > "$run/out/forager-usca.pmtiles"
printf 'seattle\t15/5249/11442\n' > "$run/coverage.txt"
keep_failed_run 1
K="$FB_SRV/failed-usca/latest"
check "kept in failed-usca, outside out-usca" '[[ "$K" != "$OUT"* && -d "$K" ]]'
check "the run's outputs, coverage and log are kept" '[[ "$(cat "$K/out/forager-usca.pmtiles")" == archive && -f "$K/coverage.txt" && -f "$K/usca-r2.log" ]]'
check "FAILED.txt names the run, stage and exit" 'grep -q "run usca-r2 failed at stage validate with exit 1" "$K/FAILED.txt"'
check "current is untouched" '[[ "$(readlink "$OUT/current")" == 20261017T202051Z ]]'
check "build-orwa.sh's kept failure is not touched" '[[ ! -e "$FB_SRV/failed/latest" ]]'

# --- basemap_matches: a reused basemap must name the extracts' snapshot ---------------------------
# The metadata keys are the ones the 2026-10-19 archive carries (planetiler 0.10.2).
mkdir -p "$T/bm"; echo "archive" > "$T/bm/forager-usca.pmtiles"; : > "$T/bm/empty.pmtiles"
pmtiles() { # stands in for pmtiles show --metadata
  printf '{"planetiler:osm:osmosisreplicationtime": "2026-10-17T20:20:51Z", "planetiler:osm:osmosisreplicationseq": "2415", "type": "baselayer"}\n'
}
check "a basemap from these extracts is accepted" 'basemap_matches "$T/bm/forager-usca.pmtiles" 2026-10-17T20:20:51Z 2415 >/dev/null'
check "a basemap from another snapshot is refused" '! basemap_matches "$T/bm/forager-usca.pmtiles" 2026-10-24T20:21:02Z 2415 >/dev/null'
check "a basemap with another sequence is refused" '! basemap_matches "$T/bm/forager-usca.pmtiles" 2026-10-17T20:20:51Z 2416 >/dev/null'
check "the refusal names both snapshots" '[[ "$(basemap_matches "$T/bm/forager-usca.pmtiles" 2026-10-24T20:21:02Z 2422)" == *"2026-10-17T20:20:51Z sequence 2415"*"2026-10-24T20:21:02Z sequence 2422"* ]]'
check "a missing basemap is refused" '! basemap_matches "$T/bm/nothing.pmtiles" 2026-10-17T20:20:51Z 2415 >/dev/null'
check "an empty basemap is refused" '! basemap_matches "$T/bm/empty.pmtiles" 2026-10-17T20:20:51Z 2415 >/dev/null'
pmtiles() { printf '{"type": "baselayer"}\n'; }
check "a basemap whose metadata has no snapshot is refused" '! basemap_matches "$T/bm/forager-usca.pmtiles" 2026-10-17T20:20:51Z 2415 >/dev/null'
pmtiles() { return 1; }
check "unreadable metadata is refused" '! basemap_matches "$T/bm/forager-usca.pmtiles" 2026-10-17T20:20:51Z 2415 >/dev/null'
unset -f pmtiles

# --- routing_bands_ok and rd5_west_edge: which band builds which square ----------------------------
check "contiguous bands from -180 to 180 are accepted" 'routing_bands_ok -180:-115 -115:-100 -100:-90 -90:-80 -80:180'
check "one band over everything is accepted" 'routing_bands_ok -180:180'
check "a gap between bands is refused" '! routing_bands_ok -180:-115 -110:180'
check "an overlap between bands is refused" '! routing_bands_ok -180:-115 -120:180'
check "bands that stop short of 180 are refused" '! routing_bands_ok -180:-80'
check "bands that start east of -180 are refused" '! routing_bands_ok -175:180'
check "an edge off a 5-degree line is refused" '! routing_bands_ok -180:-112 -112:180'
check "a band running west is refused" '! routing_bands_ok -180:-100 -100:-115 -115:180'
check "no bands at all is refused" '! routing_bands_ok'
check "a band that is not WEST:EAST is refused" '! routing_bands_ok -180:x'
check "W125_N45 starts at -125" '[[ "$(rd5_west_edge /x/segments/W125_N45.rd5)" == -125 ]]'
check "E170_N50, the Aleutians past 180, starts at 170" '[[ "$(rd5_west_edge E170_N50.rd5)" == 170 ]]'
check "W5_S10 starts at -5" '[[ "$(rd5_west_edge W5_S10.rd5)" == -5 ]]'
check "a file that is not a square is refused" '! rd5_west_edge /x/segments/bordernodes.dat'

echo "ok: $pass checks"
