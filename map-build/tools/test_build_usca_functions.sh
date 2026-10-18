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

eval "$(sed -n '/^cleanup_scratch() {/,/^}/p; /^FAILED_KEEP=/p; /^keep_failed_run() {/,/^}/p; /^rd5_square() {/,/^}/p; /^z15_tile() {/,/^}/p; /^curl_resume() {/,/^}/p' "$script")"
declare -F rd5_square z15_tile curl_resume keep_failed_run cleanup_scratch >/dev/null \
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

echo "ok: $pass checks"
