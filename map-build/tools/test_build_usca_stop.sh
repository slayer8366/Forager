#!/usr/bin/env bash
# Tests for what build-usca.sh records when it is stopped by hand (`systemctl stop`), and that a
# normal failure is recorded as before (dispatch 2026-10-19-pi-07). The stop path is
# build-usca.sh's own: the block from cleanup_scratch to `trap on_exit EXIT`, log(), and the
# `exec > >(tee ...)` line are taken from the script with sed, as test_build_usca_functions.sh
# takes its functions, and run under systemd-run as the build user in a temporary FB_SRV, with
# systemd's defaults (KillMode=control-group, IgnoreSIGPIPE=yes), as the real builds ran.
#
# Run: bash map-build/tools/test_build_usca_stop.sh   (needs sudo; exits non-zero on a failure)
# It never touches /srv/forager-build or a forager-* unit: its units are named pi07-test-N and its
# FB_SRV is under /tmp/pi07-stop.*, both removed on exit.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
source "$here/pins.env"   # FB_USER only; FB_SRV is replaced below
SUDO=(); [[ $(id -u) -eq 0 ]] || SUDO=(sudo)

units=(pi07-test-1 pi07-test-2 pi07-test-3 pi07-test-4)
T="$(mktemp -d /tmp/pi07-stop.XXXXXX)"
chmod 755 "$T"
cleanup() {
  local u
  for u in "${units[@]}"; do
    "${SUDO[@]}" systemctl stop "$u" 2>/dev/null || true
    "${SUDO[@]}" systemctl reset-failed "$u" 2>/dev/null || true
  done
  [[ "$T" == /tmp/pi07-stop.* ]] && "${SUDO[@]}" rm -rf -- "$T"
}
trap cleanup EXIT

# The build user cannot read this checkout, so the script and the tools it calls are copied, and
# the copies checked to be the files under test.
cp "$here/bin/build-usca.sh" "$here/tools/run_record.py" "$here/tools/peak_rss.py" "$T/"
chmod 644 "$T"/*
cmp -s "$here/bin/build-usca.sh" "$T/build-usca.sh" || { echo "FAIL: copy of build-usca.sh differs"; exit 1; }

cat > "$T/harness.sh" <<'EOF'
#!/usr/bin/env bash
# One stand-in run: build-usca.sh's real stop path, a run directory with outputs, then a step.
set -euo pipefail
FB_SRV="$1"; mode="$2"; T="$3"; script="$T/build-usca.sh"
OUT="$FB_SRV/out-usca"; LOGS="$FB_SRV/logs"; STATUS="$FB_SRV/status-usca.json"
RECORD=(python3 "$T/run_record.py"); TIMED=(python3 "$T/peak_rss.py")
run_id=usca-pi07-stop; run="$FB_SRV/work/$run_id"
mkdir -p "$OUT" "$LOGS" "$run/out/segments"
log_file="$LOGS/$run_id.log"
eval "$(sed -n '/^exec > >(tee -a "$log_file") 2>&1$/p' "$script")"
stage=start; osm_ts=""
eval "$(sed -n '/^log() {/p' "$script")"
eval "$(sed -n '/^cleanup_scratch() {/,/^trap on_exit EXIT$/p' "$script")"
stage=brouter
"${RECORD[@]}" status "$STATUS" --state running --stage "$stage" --run-id "$run_id"
echo archive > "$run/out/forager-usca.pmtiles"
echo square > "$run/out/segments/W125_N45.rd5"
if [[ "$mode" == second-signal ]]; then
  # From here the status writer takes 3 s, so the test can send a second TERM while on_exit's
  # first fork is running: a deterministic stand-in for a fork the stop catches.
  RECORD=(bash "$T/slow_record.sh" "$FB_SRV")
fi
log "step starts"
: > "$FB_SRV/ready"
case "$mode" in
  stop|second-signal) sleep 300 ;;
  stop-timed) "${TIMED[@]}" brouter-cut -- sleep 300 ;;  # how every heavy step runs
  fail) exit 3 ;;
esac
EOF
cat > "$T/slow_record.sh" <<'EOF'
FB_SRV="$1"; shift
: > "$FB_SRV/in-record"
sleep 3
exec python3 "$(dirname "$0")/run_record.py" "$@"
EOF
chmod 755 "$T/harness.sh"; chmod 644 "$T/slow_record.sh"

fails=0; pass=0
check() { if eval "$2"; then pass=$((pass + 1)); else echo "FAIL: $1"; fails=$((fails + 1)); fi; }
status_field() { python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get(sys.argv[2]) or "")' "$1" "$2"; }

start_unit() { # unit mode -> sets S, the run's FB_SRV
  S="$T/srv-$1"
  "${SUDO[@]}" install -d -o "$FB_USER" -g "$FB_USER" "$S"
  "${SUDO[@]}" systemd-run --quiet --unit="$1" --uid="$FB_USER" --gid="$FB_USER" "$T/harness.sh" "$S" "$2" "$T"
  local i
  for i in $(seq 100); do [[ -e "$S/ready" ]] && break; sleep 0.1; done
  [[ -e "$S/ready" ]] || { echo "FAIL: $1 never reached its step"; exit 1; }
  sleep 0.3  # the step's own process is running
}

# Reads what a run left. The stage at the stop is brouter, the one the real stop was in.
expect_recorded() { # name exit-code
  local name="$1" code="$2" K="$S/failed-usca/latest"
  # What the run left, printed so a failure can be read for its reason.
  echo "  $name: status $(status_field "$S/status-usca.json" state) at $(status_field "$S/status-usca.json" stage)," \
    "message \"$(status_field "$S/status-usca.json" message | cut -d';' -f1)\"; failed-usca: $(ls "$K" 2>/dev/null | tr '\n' ' ')" \
    "; scratch $([[ -e "$S/work/usca-pi07-stop" ]] && echo left || echo gone)"
  check "$name: (a) the status reads failed at stage brouter" \
    '[[ "$(status_field "$S/status-usca.json" state)" == failed && "$(status_field "$S/status-usca.json" stage)" == brouter ]]'
  check "$name: (a) the status message names exit $code" '[[ "$(status_field "$S/status-usca.json" message)" == "exit $code; "* ]]'
  check "$name: (b) failed-usca/latest/FAILED.txt names the run, stage and exit" \
    'grep -q "^run usca-pi07-stop failed at stage brouter with exit $code at " "$K/FAILED.txt" 2>/dev/null'
  check "$name: (b) the run's outputs and log are kept" \
    '[[ "$(cat "$K/out/forager-usca.pmtiles" 2>/dev/null)" == archive && -f "$K/out/segments/W125_N45.rd5" && -f "$K/usca-pi07-stop.log" ]]'
  check "$name: (c) the run's scratch directory is gone" '[[ ! -e "$S/work/usca-pi07-stop" ]]'
  check "$name: on_exit's line is in the run's log" \
    'grep -q "\[brouter\] run failed with exit $code; previous outputs untouched" "$S/logs/usca-pi07-stop.log"'
}

# --- 1. systemctl stop during a foreground step ------------------------------------------------
start_unit pi07-test-1 stop
"${SUDO[@]}" systemctl stop pi07-test-1
expect_recorded "stop" 143

# --- 2. systemctl stop during a step run through peak_rss.py, as every heavy step is ------------
start_unit pi07-test-2 stop-timed
"${SUDO[@]}" systemctl stop pi07-test-2
expect_recorded "stop, TIMED step" 143

# --- 3. (d) a normal failure, exit 3, is recorded as before -------------------------------------
start_unit pi07-test-3 fail
for i in $(seq 100); do "${SUDO[@]}" systemctl is-active --quiet pi07-test-3 || break; sleep 0.1; done
expect_recorded "(d) exit 3" 3
inv="$(systemctl show -p InvocationID --value pi07-test-3)"
check "(d) exit 3: on_exit's line still reaches the journal through tee, as before" \
  '"${SUDO[@]}" journalctl -q --no-pager _SYSTEMD_INVOCATION_ID="$inv" | grep -q "run failed with exit 3; previous outputs untouched"'

# --- 4. a second TERM while on_exit's status write is running ---------------------------------
start_unit pi07-test-4 second-signal
"${SUDO[@]}" systemctl stop pi07-test-4 & stopper=$!
for i in $(seq 50); do [[ -e "$S/in-record" ]] && break; sleep 0.1; done
[[ -e "$S/in-record" ]] && "${SUDO[@]}" systemctl kill --signal=TERM pi07-test-4 2>/dev/null || true
wait "$stopper" || true
check "second TERM: on_exit had started its status write when the signal was sent" '[[ -e "$S/in-record" ]]'
expect_recorded "second TERM" 143

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails checks failed, $pass passed"; exit 1; fi
echo "ok: $pass checks"
