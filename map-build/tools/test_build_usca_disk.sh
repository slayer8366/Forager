#!/usr/bin/env bash
# Tests for how build-usca.sh fits a full US + Canada build on the Pi's two disks, keeping the
# published map until the new one is done (dispatch 2026-10-20-pi-09, parts B, C, D, F and G):
#   B  downloads go to the SD card, linked from sources/osm, and the old-extract cleanup removes
#      the card file as well as the link;
#   C  the merged extract's directory on the card is removed by cleanup_scratch;
#   D  a byte-based need on each disk, from the ratios pinned in pins.env;
#   F  curl_resume restarts a slow or stalled connection, resuming with -C -, up to a cap;
#   G  wait_for_network waits, bounded, for a default route and a resolving host;
#   L  each connection's floor is the one for the link carrying the default route when it starts
#      (dispatch 2026-10-20-pi-11): Wi-Fi's, or the conservative one for anything else or no route.
# The functions are taken from the script itself with sed, as test_build_usca_functions.sh does,
# and run against fake trees for both disks in a temporary directory. Nothing here touches
# /srv/forager-build or /mnt/archive. The whole script, run end to end, is test_build_usca_run.sh.
#
# Run: bash map-build/tools/test_build_usca_disk.sh   (runs every check; exits non-zero on a failure)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
script="$here/bin/build-usca.sh"
source "$here/pins.env"   # the pinned ratios, margins and download limits under test

T="$(mktemp -d)"
trap 'rm -rf -- "$T"' EXIT
FB_SRV="$T/srv"; SRC="$FB_SRV/sources"; run_id=usca-r9; run="$FB_SRV/work/$run_id"
USCA_CARD_MOUNT="$T/card"; USCA_CARD_DIR="$T/card/forager-build"
mkdir -p "$SRC/osm" "$run" "$USCA_CARD_MOUNT"
log() { echo "LOG $*"; }
warnings=()
warn() { echo "WARN $2"; warnings+=("$1"); }

fns=(cleanup_scratch remove_card_run card_ready curl_resume fetch_extract remove_old_extracts remote_size
  planetiler_need with_margin check_disk disk_check gb has_default_route wait_for_network
  default_route_iface download_floor)
expr=""
for f in "${fns[@]}"; do expr+="/^$f() {/,/^}/p; "; done
eval "$(sed -n "$expr /^CARD_OSM=/p; /^card_run=/p" "$script")"
CARD_OSM="${CARD_OSM:-}"; card_run="${card_run:-}"

fails=0; pass=0
# Each check runs in a subshell with set +u, so one missing function or pin fails its own checks
# and the rest still run: the failing-first run reads every failure, not only the first.
check() { if (set +u; eval "$2"); then pass=$((pass + 1)); else echo "FAIL: $1"; fails=$((fails + 1)); fi; }
for f in "${fns[@]}"; do check "build-usca.sh defines $f" 'declare -F "$f" >/dev/null'; done
check "CARD_OSM is the card's sources-osm directory" '[[ "$CARD_OSM" == "$USCA_CARD_DIR/sources-osm" ]]'
check "card_run is this run's directory on the card" '[[ "$card_run" == "$USCA_CARD_DIR/work/$run_id" ]]'

# Stand-ins for the commands the functions call. The card is "mounted" unless card_mounted=0.
card_mounted=1
mountpoint() { [[ "${!#}" == "$USCA_CARD_MOUNT" && $card_mounted -eq 1 ]]; }
nvme_free=0; card_free=0
df() { # as `df -B1 --output=avail DIR` prints it: a header, then the bytes
  local d="${!#}"
  echo "    Avail"
  if [[ "$d" == "$USCA_CARD_MOUNT"* ]]; then echo "$card_free"; else echo "$nvme_free"; fi
}
sleeps=()
sleep() { sleeps+=("$1"); }

# --- D: the need, in bytes, from the ratios measured on the 261017 run ---------------------------
# I is the 261017 inputs' size: us 12,212,909,513 + canada 6,516,972,934 bytes. Planetiler's peak is
# (5.61 + 1.82 + 0.16) x I = 7.59 x 18,729,882,447 = 142,159,807,772.7, rounded up; the margin is 10%
# of that, 14,215,980,777, for 156,375,788,550 in all.
I=18729882447
check "the pinned ratios are the ones Phase 1 derived" \
  '[[ "$USCA_DISK_FEATURES_RATIO $USCA_DISK_ARCHIVE_RATIO $USCA_DISK_MULTIPOLYGON_RATIO $USCA_DISK_MARGIN_PERCENT" == "5.61 1.82 0.16 10" ]]'
check "Planetiler's need for the 261017 inputs is 7.59 x I, rounded up" '[[ "$(planetiler_need $I 2>&1)" == 142159807773 ]]'
check "the margin adds 10% of the need" '[[ "$(with_margin 142159807773 2>&1)" == 156375788550 ]]'
check "a small need gets the minimum margin, 5,000,000,000 bytes" '[[ "$(with_margin 1000 2>&1)" == 5000001000 ]]'

basemap=""; fetch_only=0
# Refuses: today's NVMe free space, 146,510,110,720 bytes (df -B1 /, 2026-10-20), is
# 156,375,788,550 - 146,510,110,720 = 9,865,677,830 bytes short.
nvme_free=146510110720; card_free=100409962496
out="$(disk_check $I $I 2>&1)" && rc=0 || rc=$?
echo "  disk_check, Sunday on today's NVMe: rc=$rc; $out"
check "D refuses a full run on today's NVMe" '[[ $rc -eq 1 ]]'
check "the refusal is in bytes, with what is free, what is needed and the shortfall" \
  '[[ "$out" == *"146510110720 bytes free"*"156375788550 bytes"* && "$out" == *"short by 9865677830 bytes"* ]]'
check "the refusal shows the arithmetic: the ratios and the input bytes" '[[ "$out" == *5.61*1.82*0.16*"$I input bytes"* ]]'
# Passes: once the leftovers are freed, 146,510,110,720 + 19,860,000,000 = 166,370,110,720 bytes,
# 9,994,322,170 more than the need. The card needs the download (I) and the merged extract (I),
# 2 x 18,729,882,447 = 37,459,764,894. 10% of that is 3,745,976,489, under the 5,000,000,000
# minimum, so the margin is the minimum: 42,459,764,894 of its 100,409,962,496 free.
nvme_free=166370110720
out="$(disk_check $I $I 2>&1)" && rc=0 || rc=$?
echo "  disk_check, Sunday after the cleanup: rc=$rc; $out"
check "D passes the same run once the leftovers are freed" '[[ $rc -eq 0 ]]'
check "the pass names both disks' needs in bytes" '[[ "$out" == *"166370110720 bytes free"*"156375788550 bytes"* && "$out" == *"100409962496 bytes free"*"42459764894 bytes"*"plus 5000000000 margin"* ]]'
# The card refuses on its own: 40,000,000,000 free is 2,459,764,894 bytes short.
card_free=40000000000
out="$(disk_check $I $I 2>&1)" && rc=0 || rc=$?
check "D refuses when the card is short, though the NVMe has room" '[[ $rc -ne 0 && "$out" == *"short by 2459764894 bytes"* ]]'
# Nothing left to download (--local, or the merge's second check): the card needs only the merge.
card_free=23729882447   # I + the 5,000,000,000 minimum margin, exactly
check "with nothing to download, the card needs only the merged extract" 'disk_check $I 0 >/dev/null 2>&1'
card_free=23729882446
check "and one byte less is refused" '[[ "$(disk_check $I 0 2>&1)" == *"short by 1 bytes"* ]]'
card_free=100409962496
card_mounted=0
out="$(disk_check $I 0 2>&1)" && rc=0 || rc=$?
check "D refuses when the card is not mounted" '[[ $rc -ne 0 && "$out" == *"$USCA_CARD_MOUNT is not a mounted filesystem"* ]]'
card_mounted=1
# --basemap: no Planetiler, so no ratios; today's 110 GiB floor, 118,111,600,640 bytes, and no card
# need unless something is downloaded.
basemap="$T/kept.pmtiles"; card_mounted=0
nvme_free=118111600640
check "a --basemap run passes at today's floor, with the card unmounted and nothing to download" 'disk_check $I 0 >/dev/null 2>&1'
nvme_free=118111600639
out="$(disk_check $I 0 2>&1)" && rc=0 || rc=$?
check "a --basemap run one byte under the floor is refused" '[[ $rc -ne 0 && "$out" == *"118111600640 bytes"* ]]'
card_mounted=1; nvme_free=166370110720; card_free=1000
check "a --basemap run that downloads needs the card for the download" '[[ "$(disk_check $I $I 2>&1)" == *"card ("*"1000 bytes free"*"short by"* ]]'
basemap=""
# --fetch-only: downloads only, so no NVMe need.
fetch_only=1; nvme_free=0; card_free=$(( I + 5000000000 ))
check "a fetch-only run needs only the download and its margin on the card" 'disk_check $I $I >/dev/null 2>&1'
fetch_only=0

# --- remote_size: Content-Length from Geofabrik's HEAD -------------------------------------------
curl() { printf 'HTTP/1.1 200 OK\r\nContent-Length: 12212909513\r\nLast-Modified: x\r\n\r\n'; }
check "remote_size reads Content-Length" '[[ "$(remote_size https://example.invalid/us-261017.osm.pbf 2>&1)" == 12212909513 ]]'
curl() { printf 'HTTP/1.1 200 OK\r\n\r\n'; }
check "remote_size fails when there is no Content-Length, and says so" '[[ "$(remote_size https://example.invalid/x 2>&1)" == *"no Content-Length"* ]] && ! remote_size https://example.invalid/x >/dev/null 2>&1'
unset -f curl

# --- Routing tables, as /proc/net/route prints them (columns as the kernel writes them) ----------
# Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT. Gateways are made up.
rt_head='Iface\tDestination\tGateway\tFlags\tRefCnt\tUse\tMetric\tMask\tMTU\tWindow\tIRTT'
rt() { # file iface:metric...: a table with a default route through each iface, and one subnet route
  local f="$1" e; shift
  { printf "$rt_head\n"
    for e in "$@"; do printf '%s\t00000000\t0100000A\t0003\t0\t0\t%s\t00000000\t0\t0\t0\n' "${e%%:*}" "${e##*:}"; done
    printf 'wlan0\t0000000A\t00000000\t0001\t0\t0\t600\t00FFFFFF\t0\t0\t0\n'; } > "$f"
}
rt "$T/rt-tether" bnep0:750
rt "$T/rt-wifi" wlan0:600
rt "$T/rt-both" wlan0:600 bnep0:750          # the shape the Pi's table had on 2026-10-20 (observed)
rt "$T/rt-both-tether-lower" wlan0:600 bnep0:100
rt "$T/rt-usb" usb0:700
rt "$T/rt-wlp" wlp1s0:600
rt "$T/rt-none"                               # a subnet route only

# --- F: curl_resume restarts a slow or stalled connection, resuming each time ---------------------
# On the tether's table, so these checks do not depend on which link this machine is on (L below).
USCA_ROUTE_FILE="$T/rt-tether"
USCA_DOWNLOAD_PAUSE=7; USCA_DOWNLOAD_MAX_RESTARTS=4; HTTP_USER_AGENT=test
mkdir -p "$T/dl"
calls="$T/dl/calls"; : > "$calls"
curl() { # stands in for curl: each connection adds one chunk; the first two "time out" (exit 28)
  local out="" a prev=""; for a in "$@"; do [[ "$prev" == -o ]] && out="$a"; prev="$a"; done
  printf '%s\n' "$*" >> "$calls"
  printf 'chunk%s ' "$(wc -l < "$calls")" >> "$out"
  [[ $(wc -l < "$calls") -ge 3 ]] || return 28
}
want=$(( 3 * 7 ))   # "chunk1 chunk2 chunk3 " is 21 bytes
sleeps=()
# Not in a $(...) subshell, so the sleep stand-in's record reaches this shell.
curl_resume https://example.invalid/x.osm.pbf "$T/dl/x.osm.pbf" $want > "$T/dl/out" 2>&1 && rc=0 || rc=$?
out="$(cat "$T/dl/out")"
echo "  curl_resume, two slow connections then one that finishes: rc=$rc"
check "F: three connections, the first two slowed and restarted" '[[ $rc -eq 0 && $(wc -l < "$calls") -eq 3 ]]'
check "F: the file is whole, in place, and the .part is gone" '[[ "$(cat "$T/dl/x.osm.pbf" 2>/dev/null)" == "chunk1 chunk2 chunk3 " && ! -e "$T/dl/x.osm.pbf.part" ]]'
check "F: every connection resumes with -C -" '[[ $(grep -c -- "-C -" "$calls") -eq 3 ]]'
check "F: every connection carries the pinned floor, 50000 bytes/s over 120 s" \
  '[[ $(grep -c -- "--speed-limit 50000 --speed-time 120" "$calls") -eq 3 ]]'
check "F: curl's own --retry is not used: it re-sends the starting offset (Phase 1, measured)" '! grep -q -- "--retry" "$calls"'
check "F: a pause of USCA_DOWNLOAD_PAUSE between connections, none before the first" '[[ "${sleeps[*]}" == "7 7" ]]'
check "F: each connection's end is logged with curl's exit and the bytes so far" '[[ "$out" == *"connection 1 ended (curl exit 28)"*"7 of 21 bytes"* ]]'
# The cap: a connection that never adds a byte, four times.
: > "$calls"; rm -f "$T/dl/y.osm.pbf.part"; printf 'part' > "$T/dl/y.osm.pbf.part"
curl() { printf '%s\n' "$*" >> "$calls"; return 28; }
out="$(curl_resume https://example.invalid/y.osm.pbf "$T/dl/y.osm.pbf" 100 2>&1)" && rc=0 || rc=$?
check "F: it gives up after USCA_DOWNLOAD_MAX_RESTARTS connections" '[[ $rc -ne 0 && $(wc -l < "$calls") -eq 4 && "$out" == *"after 4 connections"* ]]'
check "F: giving up keeps the .part for the next run to resume, and puts nothing in place" '[[ -s "$T/dl/y.osm.pbf.part" && ! -e "$T/dl/y.osm.pbf" ]]'
# More bytes than Geofabrik said: refused, not moved into place.
printf '0123456789' > "$T/dl/z.osm.pbf.part"
curl() { return 0; }
check "F: a file larger than its Content-Length is refused" '! curl_resume https://example.invalid/z.osm.pbf "$T/dl/z.osm.pbf" 5 >/dev/null 2>&1 && [[ ! -e "$T/dl/z.osm.pbf" ]]'
unset -f curl

# --- L: the floor for the link carrying the default route, chosen before each connection --------
# Asserted on the arguments curl was actually given, and on each connection's logged line.
check "L: the pinned Wi-Fi floor is 700000 bytes/s over 40 s, for interfaces matching wl*" \
  '(source "$here/pins.env"; [[ "$USCA_DOWNLOAD_SPEED_LIMIT_WIFI $USCA_DOWNLOAD_SPEED_TIME_WIFI $USCA_WIFI_IFACE_PATTERN" == "700000 40 wl*" ]])'
check "L: the conservative floor is still 50000 bytes/s over 120 s" \
  '(source "$here/pins.env"; [[ "$USCA_DOWNLOAD_SPEED_LIMIT $USCA_DOWNLOAD_SPEED_TIME" == "50000 120" ]])'
USCA_DOWNLOAD_SPEED_LIMIT_WIFI=700000; USCA_DOWNLOAD_SPEED_TIME_WIFI=40; USCA_WIFI_IFACE_PATTERN='wl*'
USCA_DOWNLOAD_PAUSE=0
one_conn() { # route-file: one connection that finishes the file; leaves curl's arguments in $calls, the log in $T/dl/out
  : > "$calls"; rm -f "$T/dl/l.osm.pbf" "$T/dl/l.osm.pbf.part"
  USCA_ROUTE_FILE="$1"
  curl() { local out="" a prev=""; for a in "$@"; do [[ "$prev" == -o ]] && out="$a"; prev="$a"; done
    printf '%s\n' "$*" >> "$calls"; printf 'abcd' >> "$out"; }
  curl_resume https://example.invalid/l.osm.pbf "$T/dl/l.osm.pbf" 4 > "$T/dl/out" 2>&1 && rc=0 || rc=$?
  unset -f curl
}
floor_is() { [[ $rc -eq 0 && $(wc -l < "$calls") -eq 1 ]] && grep -q -- "--speed-limit $1 --speed-time $2 " "$calls"; }
one_conn "$T/rt-wifi"
check "L: a Wi-Fi default route: curl gets the Wi-Fi floor" 'floor_is 700000 40'
check "L: and the connection's line names the link and the floor" \
  'grep -q "connection 1 ended .*; link wlan0, Wi-Fi floor 700000 bytes/s over 40 s" "$T/dl/out"'
one_conn "$T/rt-both"
check "L: Wi-Fi and the tether both up, Wi-Fi's metric lower: the Wi-Fi floor" 'floor_is 700000 40 && grep -q "link wlan0, Wi-Fi floor" "$T/dl/out"'
one_conn "$T/rt-tether"
check "L: a tether default route: curl gets the conservative floor" 'floor_is 50000 120'
check "L: and the line names the tether and the default floor" \
  'grep -q "connection 1 ended .*; link bnep0, default floor 50000 bytes/s over 120 s" "$T/dl/out"'
one_conn "$T/rt-both-tether-lower"
check "L: both up, the tether's metric lower though listed second: the tether's floor" 'floor_is 50000 120 && grep -q "link bnep0, default floor" "$T/dl/out"'
one_conn "$T/rt-usb"
check "L: any other link, here usb0, gets the conservative floor" 'floor_is 50000 120 && grep -q "link usb0, default floor" "$T/dl/out"'
one_conn "$T/rt-wlp"
check "L: a Wi-Fi interface with a predictable name, wlp1s0, matches wl*" 'floor_is 700000 40 && grep -q "link wlp1s0, Wi-Fi floor" "$T/dl/out"'
one_conn "$T/rt-none"
check "L: no default route: the conservative floor" 'floor_is 50000 120'
check "L: and the line says there was no default route, and where it looked" \
  'grep -q "connection 1 ended .*; no default route in $T/rt-none, default floor 50000 bytes/s over 120 s" "$T/dl/out"'
one_conn "$T/rt-missing"
check "L: a route table that cannot be read: the conservative floor, and the line says so" \
  'floor_is 50000 120 && grep -q "; cannot read $T/rt-missing, default floor 50000 bytes/s over 120 s" "$T/dl/out"'
# The link changes between connections: Wi-Fi drops mid-download and the tether takes the default
# route. The stand-in swaps the table after the first connection, which ends as a speed abort does.
: > "$calls"; rm -f "$T/dl/m.osm.pbf" "$T/dl/m.osm.pbf.part"
cp "$T/rt-wifi" "$T/rt-live"; USCA_ROUTE_FILE="$T/rt-live"
curl() { local out="" a prev=""; for a in "$@"; do [[ "$prev" == -o ]] && out="$a"; prev="$a"; done
  printf '%s\n' "$*" >> "$calls"; printf 'ab' >> "$out"
  if [[ $(wc -l < "$calls") -eq 1 ]]; then cp "$T/rt-tether" "$T/rt-live"; return 28; fi; }
curl_resume https://example.invalid/m.osm.pbf "$T/dl/m.osm.pbf" 4 > "$T/dl/out" 2>&1 && rc=0 || rc=$?
unset -f curl
echo "  curl_resume, Wi-Fi then the tether: rc=$rc; $(tr '\n' '|' < "$T/dl/out")"
check "L: the route changes between connections: two connections, the file whole" '[[ $rc -eq 0 && $(wc -l < "$calls") -eq 2 && "$(cat "$T/dl/m.osm.pbf")" == abab ]]'
check "L: the first connection, on Wi-Fi, had the Wi-Fi floor" 'sed -n 1p "$calls" | grep -q -- "--speed-limit 700000 --speed-time 40 "'
check "L: the second, after the switch, had the tether's floor" 'sed -n 2p "$calls" | grep -q -- "--speed-limit 50000 --speed-time 120 "'
check "L: each connection's line names its own link" \
  'grep -q "connection 1 ended (curl exit 28).*link wlan0, Wi-Fi floor" "$T/dl/out" && grep -q "connection 2 ended (curl exit 0).*link bnep0, default floor" "$T/dl/out"'
USCA_ROUTE_FILE="$T/rt-tether"

# --- B: the download goes to the card, and sources/osm links to it -------------------------------
curl() { local a out="" prev=""; for a in "$@"; do [[ "$prev" == -o ]] && out="$a"; prev="$a"; done; printf 'pbf!' >> "$out"; }
printf 'abc  us-261024.osm.pbf\n' > "$SRC/osm/us-261024.osm.pbf.md5"
out="$(fetch_extract https://example.invalid/us-261024.osm.pbf us-261024.osm.pbf 4 2>&1)" && rc=0 || rc=$?
echo "  fetch_extract: rc=$rc; $out"
check "B: the extract is written on the card" '[[ "$(cat "$USCA_CARD_DIR/sources-osm/us-261024.osm.pbf" 2>/dev/null)" == "pbf!" ]]'
check "B: sources/osm has a link of the same name to the card file" \
  '[[ -L "$SRC/osm/us-261024.osm.pbf" && "$(readlink "$SRC/osm/us-261024.osm.pbf")" == "$USCA_CARD_DIR/sources-osm/us-261024.osm.pbf" ]]'
check "B: the .md5 stays beside the link, a regular file" '[[ -L "$SRC/osm/us-261024.osm.pbf" && -f "$SRC/osm/us-261024.osm.pbf.md5" && ! -L "$SRC/osm/us-261024.osm.pbf.md5" ]]'
check "B: md5sum -c in sources/osm reads the card file through the link" \
  '(cd "$SRC/osm" && md5sum us-261024.osm.pbf | grep -q "^$(printf "pbf!" | md5sum | cut -d" " -f1) ")'
card_mounted=0
out="$(fetch_extract https://example.invalid/canada-261024.osm.pbf canada-261024.osm.pbf 4 2>&1)" && rc=0 || rc=$?
check "B: with the card unmounted, nothing is downloaded and the failure says why" \
  '[[ $rc -ne 0 && "$out" == *"is not a mounted filesystem"* && ! -e "$SRC/osm/canada-261024.osm.pbf" ]]'
card_mounted=1
printf 'old layout' > "$SRC/osm/canada-261024.osm.pbf"
out="$(fetch_extract https://example.invalid/canada-261024.osm.pbf canada-261024.osm.pbf 4 2>&1)" && rc=0 || rc=$?
check "B: a regular file where the link goes is refused, not overwritten" \
  '[[ $rc -ne 0 && "$out" == *"$SRC/osm/canada-261024.osm.pbf"* && "$(cat "$SRC/osm/canada-261024.osm.pbf")" == "old layout" ]]'
rm -f "$SRC/osm/canada-261024.osm.pbf" "$SRC/osm/us-261024.osm.pbf" "$SRC/osm/us-261024.osm.pbf.md5" "$USCA_CARD_DIR/sources-osm/us-261024.osm.pbf"
unset -f curl

# --- B: the old-extract cleanup removes the card file as well as the link -------------------------
# Three of these checks pass on the old cleanup too, by design: the current inputs' files, build-
# orwa.sh's, and (in C below) a card_run outside the card's work directory are all things the
# change must not remove. They guard against the new code over-reaching, not for the change itself.
C="$USCA_CARD_DIR/sources-osm"; mkdir -p "$C"
for x in us-261010 canada-261010 us-261017 canada-261017; do
  echo "$x" > "$C/$x.osm.pbf"; ln -s "$C/$x.osm.pbf" "$SRC/osm/$x.osm.pbf"
  echo "md5 $x" > "$SRC/osm/$x.osm.pbf.md5"
done
echo headers > "$SRC/osm/us-261010.osm.pbf.headers"
echo "orphan" > "$C/us-261003.osm.pbf"                       # on the card, its link already gone
echo "orwa" > "$SRC/osm/oregon-261010.osm.pbf"                 # build-orwa.sh's: not this script's
echo "elsewhere" > "$T/elsewhere.osm.pbf"; ln -s "$T/elsewhere.osm.pbf" "$SRC/osm/canada-261003.osm.pbf"
inputs=("$SRC/osm/us-261017.osm.pbf" "$SRC/osm/canada-261017.osm.pbf")
USCA_REGIONS="us canada"; warnings=()
out="$(remove_old_extracts 2>&1; echo "warnings=${warnings[*]}")"
echo "  remove_old_extracts: $(tr '\n' ' ' <<<"$out")"
check "cleanup: an old link and the card file it names are both removed" \
  '[[ ! -e "$SRC/osm/us-261010.osm.pbf" && ! -L "$SRC/osm/us-261010.osm.pbf" && ! -e "$C/us-261010.osm.pbf" && ! -e "$C/canada-261010.osm.pbf" ]]'
check "cleanup: the old .md5 and .headers beside the link go too" '[[ ! -e "$SRC/osm/us-261010.osm.pbf.md5" && ! -e "$SRC/osm/us-261010.osm.pbf.headers" ]]'
check "cleanup: the card files the current inputs name are kept, and their links and .md5" \
  '[[ "$(cat "$SRC/osm/us-261017.osm.pbf")" == us-261017 && "$(cat "$SRC/osm/canada-261017.osm.pbf")" == canada-261017 && -f "$SRC/osm/us-261017.osm.pbf.md5" ]]'
check "cleanup: an old card file with no link is removed" '[[ ! -e "$C/us-261003.osm.pbf" ]]'
check "cleanup: build-orwa.sh's extracts are left alone" '[[ -f "$SRC/osm/oregon-261010.osm.pbf" ]]'
check "cleanup: a link to a file off the card loses the link only, with a warning" \
  '[[ ! -L "$SRC/osm/canada-261003.osm.pbf" && -f "$T/elsewhere.osm.pbf" && "$out" == *"warnings=remove-old-extracts"* ]]'

# --- C: the merged extract's directory on the card ----------------------------------------------
mkdir -p "$card_run" "$run" 2>/dev/null && echo merged > "$card_run/forager-usca.osm.pbf" || echo "  (no card_run to set up)"
cleanup_scratch
check "C: cleanup_scratch removes this run's directory on the card" '[[ -n "$card_run" && ! -e "$card_run" && ! -e "$run" ]]'
check "C: and leaves the card's extracts" '[[ -f "$C/us-261017.osm.pbf" ]]'
mkdir -p "$T/notwork/$run_id"; saved="$card_run"; card_run="$T/notwork/$run_id"
cleanup_scratch
check "C: a card_run outside the card's work directory is never removed" '[[ -d "$T/notwork/$run_id" ]]'
card_run="$saved"
check "C: cleanup_scratch works under set -u with card_run unset, as test_build_usca_stop.sh runs it" \
  '(set -u; unset card_run; cleanup_scratch)'

# --- G: wait for the network, bounded --------------------------------------------------------------
printf 'Iface\tDestination\tGateway\nbnep0\t00000000\t0101A8C0\nbnep0\t0001A8C0\t00000000\n' > "$T/route-default"
printf 'Iface\tDestination\tGateway\nbnep0\t0001A8C0\t00000000\n' > "$T/route-none"
printf '00000000000000000000000000000000 00 00000000000000000000000000000000 00 00000000000000000000000000000000 ffffffff 00000001 00000000 00200200       lo\n' > "$T/route6-lo"
printf '00000000000000000000000000000000 00 00000000000000000000000000000000 00 fe800000000000000000000000000001 00000400 00000001 00000000 00000003    wlan0\n' > "$T/route6-default"
check "G: an IPv4 default route is found" 'has_default_route "$T/route-default" "$T/route6-lo"'
check "G: no default route, only a subnet and IPv6's unreachable default on lo" 'has_default_route "$T/route-none" "$T/route6-lo"; [[ $? -eq 1 ]]'
check "G: an IPv6 default route is found" 'has_default_route "$T/route-none" "$T/route6-default"'
USCA_NETWORK_WAIT_SECONDS=30; USCA_NETWORK_POLL_SECONDS=15
route_up=1; resolves_after=0; polls=0
has_default_route() { [[ $route_up -eq 1 ]]; }
getent() { polls=$((polls + 1)); [[ "$2" == download.geofabrik.de && $polls -gt $resolves_after ]]; }
sleeps=()
check "G: up at once, no wait" 'wait_for_network download.geofabrik.de >/dev/null 2>&1 && [[ ${#sleeps[@]} -eq 0 ]]'
polls=0; resolves_after=2; sleeps=()
wait_for_network download.geofabrik.de >/dev/null 2>&1 && rc=0 || rc=$?
check "G: the host resolves on the third try, after two polls" '[[ $rc -eq 0 && "${sleeps[*]}" == "15 15" ]]'
polls=0; resolves_after=99; route_up=0; sleeps=()
out="$(wait_for_network download.geofabrik.de 2>&1)" && rc=0 || rc=$?
echo "  wait_for_network, nothing up: rc=$rc; $out"
check "G: it gives up after USCA_NETWORK_WAIT_SECONDS, naming both things missing" \
  '[[ $rc -ne 0 && "$out" == *"no default route"* && "$out" == *"download.geofabrik.de does not resolve"* && "$out" == *"30 s"* ]]'
polls=0; route_up=1
out="$(wait_for_network download.geofabrik.de 2>&1)" && rc=0 || rc=$?
check "G: with the route up, the failure names only the name lookup" '[[ $rc -ne 0 && "$out" != *"no default route"* && "$out" == *"does not resolve"* ]]'
check "G: the pinned wait is an hour, polled every 15 s" \
  '(source "$here/pins.env"; [[ "$USCA_NETWORK_WAIT_SECONDS $USCA_NETWORK_POLL_SECONDS" == "3600 15" ]])'

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails checks failed, $pass passed"; exit 1; fi
echo "ok: $pass checks"
