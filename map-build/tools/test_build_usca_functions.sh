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

eval "$(sed -n '/^cleanup_scratch() {/,/^}/p; /^FAILED_KEEP=/p; /^keep_failed_run() {/,/^}/p; /^rd5_square() {/,/^}/p; /^z15_tile() {/,/^}/p; /^curl_resume() {/,/^}/p; /^default_route_iface() {/,/^}/p; /^download_floor() {/,/^}/p; /^basemap_matches() {/,/^}/p; /^routing_bands_ok() {/,/^}/p; /^rd5_west_edge() {/,/^}/p; /^names_nodes_ok() {/,/^}/p; /^bef_store_ok() {/,/^}/p; /^heights_read_ok() {/,/^}/p' "$script")"
declare -F rd5_square z15_tile curl_resume keep_failed_run cleanup_scratch basemap_matches routing_bands_ok rd5_west_edge names_nodes_ok bef_store_ok heights_read_ok >/dev/null \
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
USCA_DOWNLOAD_SPEED_LIMIT=1; USCA_DOWNLOAD_SPEED_TIME=1; USCA_DOWNLOAD_PAUSE=0; USCA_DOWNLOAD_MAX_RESTARTS=3; HTTP_USER_AGENT=test
# A tether's routing table, so curl_resume's floor (pi-11) does not depend on this machine's link.
USCA_DOWNLOAD_SPEED_LIMIT_WIFI=2; USCA_DOWNLOAD_SPEED_TIME_WIFI=2; USCA_WIFI_IFACE_PATTERN='wl*'
printf 'Iface\tDestination\tGateway\tFlags\tRefCnt\tUse\tMetric\tMask\nbnep0\t00000000\t0100000A\t0003\t0\t0\t750\t00000000\n' > "$T/route"
USCA_ROUTE_FILE="$T/route"
mkdir -p "$T/dl"; printf 'first half' > "$T/dl/x.osm.pbf"
curl() { # stands in for curl: records the partial file it was handed, then "finishes" it
  local out=""; while [[ $# -gt 0 ]]; do [[ "$1" == -o ]] && out="$2"; shift; done
  cat "$out" > "$T/dl/seen"; printf ' second half' >> "$out"
}
# Its third argument is the size Geofabrik gives, "first half second half", 22 bytes (pi-09, F).
curl_resume "https://example.invalid/x.osm.pbf" "$T/dl/x.osm.pbf" 22
check "the partial file was handed to curl to resume" '[[ "$(cat "$T/dl/seen")" == "first half" ]]'
check "the finished file is in place and the .part is gone" '[[ "$(cat "$T/dl/x.osm.pbf")" == "first half second half" && ! -e "$T/dl/x.osm.pbf.part" ]]'
unset -f curl

# --- keep_failed_run ------------------------------------------------------------------------------
echo "archive" > "$run/out/forager-usca.pmtiles"
printf 'seattle\t15/5249/11442\n' > "$run/coverage.txt"
: > "$run/names-summary.json"
keep_failed_run 1
K="$FB_SRV/failed-usca/latest"
check "kept in failed-usca, outside out-usca" '[[ "$K" != "$OUT"* && -d "$K" ]]'
check "the run's outputs, coverage and log are kept" '[[ "$(cat "$K/out/forager-usca.pmtiles")" == archive && -f "$K/coverage.txt" && -f "$K/usca-r2.log" ]]'
check "the names summary is kept (Sorrel's review N2, N8)" '[[ -f "$K/names-summary.json" ]]'
check "FAILED.txt names the run, stage and exit" 'grep -q "run usca-r2 failed at stage validate with exit 1" "$K/FAILED.txt"'
check "current is untouched" '[[ "$(readlink "$OUT/current")" == 20261017T202051Z ]]'
check "build-orwa.sh's kept failure is not touched" '[[ ! -e "$FB_SRV/failed/latest" ]]'

# --- names_nodes_ok: the names input's node limit (Sorrel's review S1, S5, N9) -------------------
check "a count at the limit passes" 'names_nodes_ok 260000000 260000000 >/dev/null'
check "a count over the limit fails, naming the count and the limit" \
  '! names_nodes_ok 260000001 260000000 >/dev/null && [[ "$(names_nodes_ok 260000001 260000000)" == *"260000001 nodes, over USCA_NAMES_MAX_INPUT_NODES (260000000)"* ]]'
# In a subshell, so a count that reaches bash arithmetic can't abort this test (set -u).
not_whole() { local out rc=0; out="$( (names_nodes_ok "$1" 260000000) 2>/dev/null)" || rc=$?; [[ $rc -ne 0 && "$out" == *"not a whole number"* ]]; }
check "an empty count fails as not a whole number" 'not_whole ""'
check "a non-numeric or negative count fails as not a whole number" 'not_whole "12 nodes" && not_whole -5'
# The weekly path's call, between the names cut and BRouter (S5): the same function, so the checks
# above are its checks.
weekly_names_check() {
  sed -n '/^set_stage names-input/,/^set_stage brouter/p' "$script" \
    | grep -qF 'names_ok="$(names_nodes_ok "$names_nodes" "$USCA_NAMES_MAX_INPUT_NODES")" || die "$names_ok"'
}
check "the weekly path checks the names input with it, before BRouter" 'weekly_names_check'

# --- bef_store_ok: BRouter's elevation store, pinned (G8) -----------------------------------------
B="$T/bef"; mkdir -p "$B"; echo one > "$B/srtm_12_03.bef"; echo two > "$B/srtm_17_04.bef"
(cd "$B" && sha256sum *.bef > SHA256SUMS); BS="$(sha256sum < "$B/SHA256SUMS" | cut -d' ' -f1)"
bef_says() { local out rc=0; out="$( (bef_store_ok "$@") 2>&1)" || rc=$?; [[ $rc -ne 0 ]] && echo "$out"; }
check "the pinned store passes, quickly and in full" 'bef_store_ok "$B" "$BS" 2 >/dev/null && bef_store_ok "$B" "$BS" 2 full >/dev/null'
USCA_CARD_MOUNT=/
check "a missing store fails, naming it" '[[ "$(bef_says "$T/nope" "$BS" 2)" == "no elevation store at $T/nope" ]]'
USCA_CARD_MOUNT="$T/not-a-mount"
check "a missing store with the card not mounted says so (N5)" \
  '[[ "$(bef_says "$T/nope" "$BS" 2)" == "no elevation store at $T/nope ($T/not-a-mount is not mounted)" ]]'
check "a store of another SHA256SUMS fails, naming both" '[[ "$(bef_says "$B" 0000 2)" == *"not the pinned USCA_BEF_SUMS_SHA256 0000"* ]]'
check "a count other than the pin fails" '[[ "$(bef_says "$B" "$BS" 3)" == *"lists 2 files, not the pinned USCA_BEF_FILES 3"* ]]'
mv "$B/srtm_17_04.bef" "$T/held.bef"
check "a listed .bef gone fails the quick check, naming it" '[[ "$(bef_says "$B" "$BS" 2)" == *"not the ones SHA256SUMS lists: srtm_17_04.bef"* ]]'
echo other > "$B/srtm_99_99.bef"
check "a listed .bef gone and an unlisted one there, the same count, fails the quick check (N4)" \
  '[[ "$(bef_says "$B" "$BS" 2)" == *"srtm_17_04.bef srtm_99_99.bef"* ]]'
rm -f "$B/srtm_99_99.bef"; mv "$T/held.bef" "$B/srtm_17_04.bef"; echo changed > "$B/srtm_17_04.bef"
check "a changed .bef passes the quick check but fails the full one, naming the file (N3)" \
  'bef_store_ok "$B" "$BS" 2 >/dev/null && [[ "$(bef_says "$B" "$BS" 2 full)" == *"does not match its SHA256SUMS: srtm_17_04.bef: FAILED"* ]]'
rm -f "$B/SHA256SUMS"
check "a store without SHA256SUMS fails" '[[ "$(bef_says "$B" "$BS" 2)" == *"has no SHA256SUMS"* ]]'
# --- heights_read_ok: PosUnifier names a tile <SQUARE>.u5d_3 when it read heights (S1) -----------
# Read on the Pi with BRouter 1.7.10 on the 261024 USVI extract: with the store, W65_N15.u5d_3 and
# W70_N15.u5d_3; with an empty folder, W65_N15.u5d and W70_N15.u5d.
U5="$T/unodes55"; mkdir -p "$U5"; USCA_BEF_DIR=/the/store
check "an empty tile folder passes" 'heights_read_ok "$U5" "W80_N25" >/dev/null'
: > "$U5/W125_N45.u5d_3"; : > "$U5/W80_N25.u5d"
check "tiles with heights and an allowed one without pass" 'heights_read_ok "$U5" "W170_N20 W80_N25" >/dev/null'
: > "$U5/W100_N40.u5d"
check "a tile without heights that isn't allowed fails, naming the square" \
  '! heights_read_ok "$U5" "W170_N20 W80_N25" >/dev/null && [[ "$(heights_read_ok "$U5" "W170_N20 W80_N25")" == "PosUnifier read no heights for W100_N40: "* ]]'
check "a square is matched whole, not by a prefix" '! heights_read_ok "$U5" "W100_N4 W80_N25" >/dev/null'
check "the weekly path runs it after each band's PosUnifier, with the pin, and dies" \
  'grep -qF '"'"'hr="$(heights_read_ok "$br/unodes55" "$USCA_BEF_NONE")" || die'"'"' "$script" && [[ "$(grep -n "btools.mapcreator.PosUnifier" "$script" | cut -d: -f1)" -lt "$(grep -n "heights_read_ok \"\$br" "$script" | cut -d: -f1)" ]]'

check "PosUnifier reads the pinned store, and nothing builds into an empty no-elevation folder" \
  'grep -qF '"'"'bordernids.dat bordernodes.dat "$USCA_BEF_DIR")'"'"' "$script" && ! grep -q "no-elevation" "$script"'
check "the store is checked in full at the state stage and quickly before BRouter" \
  '[[ "$(grep -c "bef_store_ok \"\$USCA_BEF_DIR\" \"\$USCA_BEF_SUMS_SHA256\" \"\$USCA_BEF_FILES\"" "$script")" == 2 ]] && grep -q "USCA_BEF_FILES\" full)\" || die" "$script"'

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

# --- Regions with a path (dispatch 2026-10-20-pi-14) ----------------------------------------------
# Puerto Rico and the USVI are nested under us/ at Geofabrik. These checks run on to the end and
# count their failures, so the failing-first run reads every one, not only the first.
eval "$(sed -n '/^region_url_dir() {/,/^}/p; /^regions_ok() {/,/^}/p; /^basemap_regions_match() {/,/^}/p; /^write_basemap_regions() {/,/^}/p' "$script")"
nfail=0
check_on() { if (set +u; eval "$2"); then pass=$((pass + 1)); else echo "FAIL: $1"; nfail=$((nfail + 1)); fi; }
USCA_GEOFABRIK_BASE=https://download.geofabrik.de/north-america
check_on "build-usca.sh defines region_url_dir, regions_ok, basemap_regions_match and write_basemap_regions" \
  'declare -F region_url_dir regions_ok basemap_regions_match write_basemap_regions >/dev/null'
check_on "a bare region's dated files are served from the base itself, as before" \
  '[[ "$(region_url_dir us)" == https://download.geofabrik.de/north-america && "$(region_url_dir canada)" == https://download.geofabrik.de/north-america ]]'
check_on "a nested region's dated files are served from its directory" \
  '[[ "$(region_url_dir us/puerto-rico)" == https://download.geofabrik.de/north-america/us && "$(region_url_dir us/us-virgin-islands)" == https://download.geofabrik.de/north-america/us ]]'
check_on "the four regions are a valid list" 'regions_ok us canada us/puerto-rico us/us-virgin-islands >/dev/null'
check_on "us canada alone is a valid list" 'regions_ok us canada >/dev/null'
check_on "two regions with one stem are refused, naming the stem" \
  'declare -F regions_ok >/dev/null && ! regions_ok us canada x/us >/dev/null && [[ "$(regions_ok us canada x/us)" == *"us"*"x/us"* ]]'
check_on "a region with an empty segment, a leading or trailing slash, .. or capitals is refused" \
  'declare -F regions_ok >/dev/null && ! regions_ok us/ >/dev/null && ! regions_ok /us >/dev/null && ! regions_ok us//pr >/dev/null && ! regions_ok ../us >/dev/null && ! regions_ok US >/dev/null'
check_on "no regions at all is refused" 'declare -F regions_ok >/dev/null && ! regions_ok >/dev/null'

# The sidecar: written next to an archive, read back by --basemap.
mkdir -p "$T/side"; echo archive > "$T/side/forager-usca.pmtiles"
S4="us:2416,canada:4946,us/puerto-rico:3282,us/us-virgin-islands:1791"; S2="us:2416,canada:4946"; TS4=2026-10-18T20:25:06Z
write_basemap_regions "$T/side/forager-usca.pmtiles" "$TS4" "$S4" 2>/dev/null || true
check_on "write_basemap_regions writes the timestamp and the sequence string, two lines, beside the archive" \
  '[[ "$(cat "$T/side/forager-usca.pmtiles.regions" 2>/dev/null)" == "$(printf "timestamp=%s\nsequence=%s" "$TS4" "$S4")" ]]'
USCA_BASEMAP_UNLISTED_REGIONS="us canada"
USCA_REGIONS="us canada us/puerto-rico us/us-virgin-islands"
check_on "a sidecar naming this run's four regions and snapshot is accepted" \
  'basemap_regions_match "$T/side/forager-usca.pmtiles" "$TS4" "$S4" >/dev/null'
printf 'timestamp=%s\nsequence=%s\n' "$TS4" "$S2" > "$T/side/two.pmtiles.regions"; echo archive > "$T/side/two.pmtiles"
check_on "a four-region run refuses a basemap whose sidecar names us and canada only" \
  'declare -F basemap_regions_match >/dev/null && ! basemap_regions_match "$T/side/two.pmtiles" "$TS4" "$S4" >/dev/null'
check_on "the refusal names both region lists" \
  '[[ "$(basemap_regions_match "$T/side/two.pmtiles" "$TS4" "$S4")" == *"$S2"*"$S4"* ]]'
check_on "a sidecar from another snapshot is refused" \
  'declare -F basemap_regions_match >/dev/null && ! basemap_regions_match "$T/side/forager-usca.pmtiles" 2026-10-25T20:21:00Z "$S4" >/dev/null'
echo archive > "$T/side/old.pmtiles"
check_on "a four-region run refuses a basemap with no sidecar" 'declare -F basemap_regions_match >/dev/null && ! basemap_regions_match "$T/side/old.pmtiles" "$TS4" "$S4" >/dev/null'
check_on "and says the sidecar is missing" '[[ "$(basemap_regions_match "$T/side/old.pmtiles" "$TS4" "$S4")" == *"old.pmtiles.regions"* ]]'
USCA_REGIONS="us canada"
check_on "a us canada run accepts a basemap with no sidecar, as today's kept basemap is accepted" \
  'basemap_regions_match "$T/side/old.pmtiles" 2026-10-17T20:20:51Z "us:2415,canada:4945" >/dev/null'
check_on "a us canada run with a sidecar still checks it" \
  'declare -F basemap_regions_match >/dev/null && ! basemap_regions_match "$T/side/forager-usca.pmtiles" "$TS4" "$S2" >/dev/null'

# The pins: the four regions, us first, and a coverage point in each island, in the last band.
P="$(unset USCA_REGIONS USCA_COVERAGE_POINTS USCA_ROUTING_BANDS; source "$here/pins.env"; printf '%s|%s|%s' "$USCA_REGIONS" "$USCA_COVERAGE_POINTS" "$USCA_ROUTING_BANDS")"
IFS='|' read -r pr pc pb <<<"$P"
check_on "pins.env's USCA_REGIONS is us, canada, Puerto Rico and the USVI, us first" \
  '[[ "$pr" == "us canada us/puerto-rico us/us-virgin-islands" ]]'
check_on "pins.env's USCA_BASEMAP_UNLISTED_REGIONS is us canada" \
  '[[ "$(unset USCA_BASEMAP_UNLISTED_REGIONS; source "$here/pins.env"; echo "${USCA_BASEMAP_UNLISTED_REGIONS:-}")" == "us canada" ]]'
check_on "San Juan is back in the coverage points" '[[ " $pc " == *" san-juan:-66.1057:18.4655 "* ]]'
check_on "Charlotte Amalie is in the coverage points" '[[ " $pc " == *" charlotte-amalie:-64.9307:18.3419 "* ]]'
check_on "the seven earlier points are unchanged" \
  '[[ "$pc" == "seattle:-122.3350:47.6080 miami:-80.1918:25.7617 anchorage:-149.9003:61.2181 honolulu:-157.8583:21.3069 toronto:-79.3832:43.6532 whitehorse:-135.0568:60.7212 st-johns:-52.7126:47.5615 "* ]]'
check_on "Charlotte Amalie is in W65_N15, San Juan in W70_N15" \
  '[[ "$(rd5_square -64.9307 18.3419)" == W65_N15 && "$(rd5_square -66.1057 18.4655)" == W70_N15 ]]'
last="${pb##* }"; lw="${last%%:*}"; le="${last##*:}"
check_on "both squares' west edges are in the last band, $last" \
  'for q in W70_N15 W65_N15; do x="$(rd5_west_edge $q.rd5)"; (( lw <= x && x < le )) || exit 1; done'

# --- stage_routing_set (dispatch 2026-10-30-coder-04): NOTICE and the R2 manifest beside the run's manifest --------
# The function is taken from the script, and RECORD and here are the script's own, so run_record.py is the real one.
eval "$(sed -n '/^RECORD=/p; /^stage_routing_set() {/,/^}/p' "$script")"
if declare -F stage_routing_set >/dev/null; then
  so="$T/stage/out"; mkdir -p "$so/segments" "$so/names1"
  printf 'one' > "$so/segments/W125_N45.rd5"; printf 'n' > "$so/names1/W125_N45.fnm"
  check_on "stage_routing_set stages the set" 'stage_routing_set "$so" usca-20261101T102042Z 2026-10-31T20:20:51Z "us:2429,canada:4959" >/dev/null'
  check_on "its NOTICE is the committed notice file, byte for byte" 'cmp -s "$so/NOTICE" "$here/notices/routing-NOTICE.txt"'
  check_on "its routing-manifest.json names the run, the square under segments4, and the notice" \
    'python3 -c "import json,sys; m=json.load(open(sys.argv[1])); assert m[\"run_id\"]==\"usca-20261101T102042Z\" and m[\"files\"][0][\"path\"]==\"usca-20261101T102042Z/segments4/W125_N45.rd5\" and m[\"notice\"]==\"usca-20261101T102042Z/NOTICE\" and len(m[\"notices\"])==4" "$so/routing-manifest.json"'
  check_on "the publish stage calls it on the run's output before it moves into place" \
    '[[ "$(grep -n "stage_routing_set \"\$run/out\"" "$script" | cut -d: -f1)" -lt "$(grep -n "^mv \"\$run/out\" \"\$OUT/\$name\"" "$script" | cut -d: -f1)" ]]'
else
  echo "FAIL: stage_routing_set is not in build-usca.sh"; nfail=$((nfail + 1))
fi

if [[ $nfail -gt 0 ]]; then echo "FAILED: $nfail checks failed, $pass passed"; exit 1; fi
echo "ok: $pass checks"
