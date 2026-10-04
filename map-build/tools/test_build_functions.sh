#!/usr/bin/env bash
# Tests for build-orwa.sh's drop_extra_rd5 and keep_failed_run. The functions are taken from the
# script itself, not copied, and run against a fake tree in a temporary directory.
#
# Run: bash map-build/tools/test_build_functions.sh      (exits non-zero on the first failure)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
script="$here/bin/build-orwa.sh"

T="$(mktemp -d)"
FB_SRV="$T/srv"; OUT="$FB_SRV/out"; run="$FB_SRV/work/r2"; run_id=r2; stage=validate
log_file="$FB_SRV/logs/r2.log"
RD5_SQUARES="W120_N40 W120_N45 W125_N40 W125_N45"
mkdir -p "$OUT/20261003T202050Z" "$FB_SRV/logs" "$run/out/segments"
ln -s 20261003T202050Z "$OUT/current"
echo "log line" > "$log_file"
log() { echo "LOG $*"; }

# Load the functions and the FAILED_KEEP setting from the script.
eval "$(sed -n '/^cleanup_scratch() {/,/^}/p; /^FAILED_KEEP=/p; /^keep_failed_run() {/,/^}/p; /^drop_extra_rd5() {/,/^}/p' "$script")"
declare -F drop_extra_rd5 keep_failed_run cleanup_scratch >/dev/null || { echo "FAIL: functions not loaded"; exit 1; }

pass=0
check() { if eval "$2"; then pass=$((pass + 1)); else echo "FAIL: $1"; exit 1; fi; }

# --- drop_extra_rd5 ----------------------------------------------------------------------------
for n in W120_N40 W120_N45 W125_N40 W125_N45 W130_N45 W130_N50 W135_N50 W135_N55 W140_N55 W145_N55 W150_N60; do
  head -c 1000 /dev/zero > "$run/out/segments/$n.rd5"
done
head -c 4321 /dev/zero > "$run/out/segments/W150_N60.rd5"
drop_extra_rd5 "$run/out/segments" "$run/dropped-rd5.tsv"
check "the four state squares remain" '[[ "$(cd "$run/out/segments" && ls | tr "\n" " ")" == "W120_N40.rd5 W120_N45.rd5 W125_N40.rd5 W125_N45.rd5 " ]]'
check "seven squares listed" '[[ $(wc -l < "$run/dropped-rd5.tsv") -eq 7 ]]'
check "a dropped square is listed with its size" 'grep -qxP "W150_N60.rd5\t4321" "$run/dropped-rd5.tsv"'
check "no kept square is listed" '! grep -qE "^W1(20|25)_N4[05]" "$run/dropped-rd5.tsv"'
mkdir -p "$T/empty"
drop_extra_rd5 "$T/empty" "$T/empty.tsv"
check "no .rd5 at all: nothing listed, no error" '[[ ! -s "$T/empty.tsv" ]]'

# --- keep_failed_run ---------------------------------------------------------------------------
echo "archive" > "$run/out/forager-orwa.pmtiles"
echo '{"label": "planetiler"}' > "$run/metrics.jsonl"
keep_failed_run 1
K="$FB_SRV/failed/latest"
check "kept outside out/" '[[ "$K" != "$OUT"* && -d "$K" ]]'
check "the run's outputs are kept" '[[ "$(cat "$K/out/forager-orwa.pmtiles")" == "archive" && -f "$K/out/segments/W125_N45.rd5" ]]'
check "metrics, dropped list and log are kept" '[[ -f "$K/metrics.jsonl" && -f "$K/dropped-rd5.tsv" && -f "$K/r2.log" ]]'
check "FAILED.txt names the run, stage and exit" 'grep -q "run r2 failed at stage validate with exit 1" "$K/FAILED.txt"'
check "current is untouched" '[[ "$(readlink "$OUT/current")" == "20261003T202050Z" ]]'
check "out/ holds only what it held" '[[ "$(ls "$OUT" | tr "\n" " ")" == "20261003T202050Z current " ]]'

# A later failure replaces the kept run.
run="$FB_SRV/work/r3"; run_id=r3; stage=planetiler; log_file="$FB_SRV/logs/r3.log"
mkdir -p "$run/out"; echo "second" > "$run/out/forager-orwa.pmtiles"; echo "log 3" > "$log_file"
keep_failed_run 2
check "replaced by the next failure" '[[ "$(cat "$K/out/forager-orwa.pmtiles")" == "second" && ! -e "$K/r2.log" && ! -e "$K/out/segments" ]]'
check "FAILED.txt is the new run's" 'grep -q "run r3 failed at stage planetiler with exit 2" "$K/FAILED.txt"'

# A failure before any output exists keeps the log only.
run="$FB_SRV/work/r4"; run_id=r4; stage=fetch-extracts; log_file="$FB_SRV/logs/r4.log"
mkdir -p "$run"; echo "log 4" > "$log_file"
keep_failed_run 1
check "early failure: no out/, log kept" '[[ ! -e "$K/out" && -f "$K/r4.log" ]]'

echo "all $pass checks passed"
