#!/usr/bin/env bash
# One build of Forager's US + Canada map (forager-usca.pmtiles, z0-z15, with trail attributes) and
# BRouter routing data (.rd5), from Geofabrik's extracts of the regions in USCA_REGIONS merged into
# one: us, canada, and Puerto Rico and the US Virgin Islands. The owner's go of 2026-10-18 (Pi
# session, raspberrypi-composed-bunny): US and Canada, "Canadians are using the app too"; the Pi
# builds and keeps a copy and serves nothing (RECORD -879, -1276). The whole US means Alaska, Hawaii
# and Puerto Rico too (RECORD -1274, -1300). Geofabrik's us extract carries Alaska and Hawaii but
# not the two islands, so they are regions of their own, each its own extract nested under us/ at
# Geofabrik (pins.env; dispatch 2026-10-20-pi-14, owner: "Write the Puerto Rico dispatch"). Run as
# the build user, by hand (`sudo -u forager-build ...`) or by forager-build-usca.service.
#
# Usage: build-usca.sh [--fetch-only] [--force] [--local YYMMDD] [--basemap FILE]
#   --fetch-only    download and check the extracts and sources, then stop
#   --force         build even if the extracts are the ones the current output was built from
#   --local YYMMDD  build from each region's STEM-YYMMDD.osm.pbf already in sources/osm (us-,
#                   canada-, puerto-rico-, us-virgin-islands-YYMMDD.osm.pbf), checked against their
#                   .md5 files and their headers, without asking Geofabrik which extract is
#                   current. Over the Pi's Bluetooth link the extracts take about a
#                   day to arrive, by which time Geofabrik has published a newer one, and the
#                   normal path would start downloading again.
#   --basemap FILE  use FILE, a forager-usca.pmtiles an earlier run built, instead of running
#                   Planetiler, and skip the full merge that only Planetiler reads. FILE's metadata
#                   must name the extracts' replication timestamp and sequence, and FILE.regions
#                   must name this run's regions, or the run stops; a FILE with no .regions
#                   predates it and is accepted only for a us canada run (basemap_regions_match).
#                   It is hard-linked into the output, so it must be on the same filesystem as
#                   FB_SRV, and a failed run leaves it where it was. The first US + Canada run
#                   finished its 7h44m basemap and then failed at the routing step (run
#                   usca-20261019T011311Z); the owner's word on 2026-10-19 was to keep that
#                   basemap and move on, not build it again.
#
# A separate script from build-orwa.sh, which stays as it is (CLAUDE.md: new capability is a new
# path). Its own output, status and kept-failure directories, so neither script's `current`,
# status or failed run is the other's. What differs from build-orwa.sh, and why:
#   - The extracts are whole countries under north-america/, not states under us/, and the two
#     islands, nested under us/; a region's path builds its URLs, its stem its file names.
#   - Downloads resume (curl -C -) in a loop of their own that starts a new connection whenever one
#     slows below a floor or stalls, up to a cap (pins.env, USCA_DOWNLOAD_*): 19 GB over Bluetooth
#     will be interrupted, and curl's own --retry resends the offset it started from (pi-09). The
#     floor is chosen before each connection from the link carrying the default route: Wi-Fi's
#     restarts a slowed connection to win back its fast start, anything else gets one safe on the
#     Bluetooth tether (pi-11).
#   - The extracts and the merged extract live on the SD card (USCA_CARD_DIR), linked from
#     sources/osm, so the NVMe holds only Planetiler's files while the published map stays in place
#     until the new one is done (owner, 2026-10-20: "always keep the last map").
#   - Planetiler is limited to Geofabrik's outlines of the regions (--polygon). The us
#     extract's header box spans -180 to 180 because the Aleutians cross the antimeridian, so the
#     box alone would fill ocean tiles across the whole band.
#   - Planetiler's node map, features and multipolygon geometries are memory-mapped files, named
#     explicitly rather than left to defaults: the input is about 30 times Oregon + Washington's,
#     whose build already peaked at 5.9 of the Pi's 8 GB.
#   - Every .rd5 square is kept. build-orwa.sh dropped the squares outside the two states, which
#     held only ferry routes to Alaska; Alaska is in this build.
#   - Before publishing, sample points in each country and territory the owner named must have a
#     z15 tile with roads in it and a plausibly sized .rd5 square, so a build that silently lost
#     Alaska, Hawaii, Canada, Puerto Rico or the US Virgin Islands is refused.
#   - A disk check in bytes on both disks, computed from the inputs' sizes and the ratios measured
#     on the 261017 run (pins.env, USCA_DISK_*), at the state stage before anything downloads and
#     again at the merge: the build shares the root filesystem with the rest of the Pi.
#   - Before asking Geofabrik anything, it waits (bounded) for a default route and for the host to
#     resolve: two weekly firings failed at name lookup.
#
# Both outputs are published together or not at all. On any failure, a stop by hand included, the
# previous output and the `current` link are left untouched, the run's log is kept, its scratch
# files are removed, status-usca.json says which stage failed, and the script exits non-zero.
set -euo pipefail
umask 022

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

fetch_only=0; force=0; local_date=""; basemap=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --fetch-only) fetch_only=1 ;;
    --force) force=1 ;;
    --local) [[ $# -ge 2 && "$2" =~ ^[0-9]{6}$ ]] || { echo "--local needs a date as YYMMDD" >&2; exit 2; }
      local_date="$2"; shift ;;
    --basemap) [[ $# -ge 2 && -n "$2" ]] || { echo "--basemap needs a .pmtiles file" >&2; exit 2; }
      basemap="$(readlink -f -- "$2")"; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

[[ "$(id -un)" == "$FB_USER" ]] || { echo "run as $FB_USER" >&2; exit 2; }
# Two builds at once would not fit in the Pi's memory, and build-orwa.sh's cleanup deletes every
# extract it did not use. It takes no lock of its own, so look for it.
if pgrep -u "$FB_USER" -f 'bin/build-orwa\.sh' >/dev/null; then echo "build-orwa.sh is running; not starting" >&2; exit 2; fi
exec 9>"$FB_SRV/build-usca.lock"
flock -n 9 || { echo "another build-usca.sh is running" >&2; exit 2; }

JAVA="$FB_OPT/jdk/bin/java"
BASEMAP_JAR="$FB_OPT/basemap/$BASEMAPS_JAR_NAME"
BROUTER_HOME="$FB_OPT/brouter"
SRC="$FB_SRV/sources"
OUT="$FB_SRV/out-usca"
LOGS="$FB_SRV/logs"
STATUS="$FB_SRV/status-usca.json"
RECORD=(python3 "$here/tools/run_record.py")
TIMED=(python3 "$here/tools/peak_rss.py")

run_id="usca-$(date -u +%Y%m%dT%H%M%SZ)"
run="$FB_SRV/work/$run_id"
# On the SD card (B and C): the extracts, linked from $SRC/osm, and this run's merged extract.
CARD_OSM="$USCA_CARD_DIR/sources-osm"
card_run="$USCA_CARD_DIR/work/$run_id"
mkdir -p "$SRC/osm" "$SRC/aux" "$OUT" "$LOGS" "$run"
log_file="$LOGS/$run_id.log"
exec > >(tee -a "$log_file") 2>&1
export FB_METRICS="$run/metrics.jsonl"

stage="start"
osm_ts=""
log() { printf '%s [%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$stage" "$*"; }
die() { log "FAILED: $*"; exit 1; }
set_stage() {
  stage="$1"
  log "stage start"
  "${RECORD[@]}" status "$STATUS" --state running --stage "$stage" --run-id "$run_id" ${osm_ts:+--osm-timestamp "$osm_ts"}
}

cleanup_scratch() {
  # Remove this run's large working files; the log stays in $LOGS.
  if [[ -d "$run" && "$run" == "$FB_SRV/work/"* ]]; then
    rm -rf -- "$run"
  fi
  remove_card_run
}
remove_card_run() { # this run's directory on the card, which holds only the merged extract (C)
  # card_run is unset where only this block is taken from the script (test_build_usca_stop.sh).
  if [[ -n "${card_run:-}" && -d "$card_run" && "$card_run" == "$USCA_CARD_DIR/work/"* ]]; then
    rm -rf -- "$card_run"
  fi
}
# The latest failed run's outputs, kept for inspection, as build-orwa.sh does; its own directory.
FAILED_KEEP="$FB_SRV/failed-usca/latest"
keep_failed_run() { # exit-code
  [[ "$FAILED_KEEP" == "$FB_SRV/failed-usca/latest" && "$FAILED_KEEP" != "$OUT"* ]] || return 1
  if [[ -e "$FAILED_KEEP" ]]; then rm -rf -- "$FAILED_KEEP"; fi
  mkdir -p "$FAILED_KEEP"
  if [[ -d "$run/out" ]]; then mv "$run/out" "$FAILED_KEEP/out"; fi
  local f
  for f in metrics.jsonl versions.txt bounds.txt coverage.txt names-summary.json; do
    if [[ -f "$run/$f" ]]; then cp "$run/$f" "$FAILED_KEEP/"; fi
  done
  printf 'run %s failed at stage %s with exit %s at %s; not published, and never publishable from here\n' \
    "$run_id" "$stage" "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$FAILED_KEEP/FAILED.txt"
  cp "$log_file" "$FAILED_KEEP/"
}
on_exit() {
  local code=$?
  if [[ $code -ne 0 ]]; then
    if [[ -n "$stopped_by" ]]; then
      # A stop signals tee too (KillMode=control-group, or Ctrl-C's process group), so a write
      # to it fails and errexit ends this trap before the run is kept (pi-04; pi-07's
      # pi07-test-0). Write to the log file itself instead.
      exec >>"$log_file" 2>&1
    fi
    "${RECORD[@]}" status "$STATUS" --state failed --stage "$stage" --run-id "$run_id" \
      ${osm_ts:+--osm-timestamp "$osm_ts"} --message "exit $code; log $log_file; outputs kept in $FAILED_KEEP" || true
    log "run failed with exit $code; previous outputs untouched; this run's outputs kept in $FAILED_KEEP"
    keep_failed_run "$code" || log "could not keep this run's outputs in $FAILED_KEEP"
    cleanup_scratch
  fi
}
# A stop by hand (`systemctl stop`, Ctrl-C) ends the run with exit 128+N, so on_exit records it as
# failed. Without these traps bash still runs on_exit, but $? is the last finished command's
# status, usually 0, and on_exit then did nothing: the status stayed "running" (2026-10-19,
# usca-20261019T130011Z; pi-04). The signals are ignored from here on, so on_exit and everything
# it forks cannot be cut short by a second or late one; systemd's SIGKILL after TimeoutStopSec
# still bounds it. The unit then ends "failed" with that exit status, not "Deactivated successfully".
stopped_by=""
on_signal() { # name exit-code
  trap '' TERM INT HUP
  stopped_by="$1"
  exit "$2"
}
trap 'on_signal TERM 143' TERM
trap 'on_signal INT 130' INT
trap 'on_signal HUP 129' HUP
trap on_exit EXIT

rd5_square() { # lon lat: the name of BRouter's 5-degree square holding the point, e.g. W125_N45
  python3 - "$1" "$2" <<'EOF'
import math, sys
lon, lat = float(sys.argv[1]), float(sys.argv[2])
x, y = int(math.floor(lon / 5) * 5), int(math.floor(lat / 5) * 5)
print(("W%d" % -x if x < 0 else "E%d" % x) + "_" + ("S%d" % -y if y < 0 else "N%d" % y))
EOF
}

z15_tile() { # lon lat: the z/x/y of the zoom-15 tile holding the point
  python3 - "$1" "$2" <<'EOF'
import math, sys
lon, lat = float(sys.argv[1]), float(sys.argv[2])
n = 2 ** 15
x = int((lon + 180) / 360 * n)
y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n)
print(15, x, y)
EOF
}

routing_bands_ok() { # band...: each WEST:EAST, contiguous from -180 to 180, on 5-degree lines
  local prev=-180 b w e
  [[ $# -gt 0 ]] || return 1
  for b in "$@"; do
    IFS=: read -r w e <<<"$b"
    [[ "$w" =~ ^-?[0-9]+$ && "$e" =~ ^-?[0-9]+$ ]] || return 1
    (( w == prev && e > w && w % 5 == 0 && e % 5 == 0 )) || return 1
    prev=$e
  done
  (( prev == 180 ))
}

rd5_west_edge() { # file: the west edge of the square a .rd5 covers, e.g. W125_N45.rd5 -> -125
  local n="${1##*/}"
  n="${n%%_*}"
  case "$n" in
    W[0-9]*) echo "-${n#W}" ;;
    E[0-9]*) echo "${n#E}" ;;
    *) return 1 ;;
  esac
}

# The names files (dispatch pi-31, FNAM v1). bin/build-names.sh runs the same two on raw extracts.
names_cut() { # in out [label-suffix]: in's highway ways with a name or a ref, and their nodes
  # Two filters, since tags-filter has no "and" of two keys; the first one's output is removed.
  local hw="$2.highway.osm.pbf" rc=0
  "${TIMED[@]}" "names-filter-highway${3:-}" -- osmium tags-filter "$1" w/highway --overwrite -o "$hw" || rc=$?
  # Measured so the disk check can count it (Sorrel's review S2): the whole highway network, named
  # or not, beside the routing input and its bands.
  (( rc != 0 )) || echo "names highway cut${3:-}: $(stat -c %s "$hw") bytes"
  (( rc != 0 )) || "${TIMED[@]}" "names-filter-named${3:-}" -- osmium tags-filter "$hw" w/name w/ref --overwrite -o "$2" || rc=$?
  rm -f -- "$hw"
  return $rc
}

names_encode() { # named-input names-dir rd5-dir osm-timestamp summary-file: one .fnm per .rd5
  # One export pass, streamed into the encoder, which simplifies each line, splits it at the
  # 1/8-degree cell edges and assigns each piece to its cell and square: no complete_ways cut per
  # square (two such passes at once on the named file reached 7.4 GB and were killed,
  # 2026-10-26). The pipeline's peak is the larger of osmium's and the encoder's; the encoder's
  # own is in its summary line. Each file's date is set to the extract's, as for the .rd5 files.
  mkdir -p -- "$2"
  "${TIMED[@]}" names-encode -- bash -c 'set -euo pipefail
    osmium export -f geojsonseq -x print_record_separator=false --geometry-types=linestring -c "$1" \
      -i "$2" -o - "$3" | python3 "$4" encode "$5" --osm-timestamp "$6" --rd5-dir "$7" --jobs "$8" > "$9"' _ \
    "$here/tools/fnam_export.json" "$USCA_NAMES_EXPORT_INDEX" "$1" "$here/tools/fnam_build.py" \
    "$2" "$4" "$3" "$USCA_NAMES_JOBS" "$5" || return 1
  touch -d "$4" -- "$2/"*.fnm
}

names_nodes_ok() { # count limit: is the names input's node count a whole number no larger than the limit?
  # Prints the reason when it isn't; the caller dies with it (Sorrel's review S1, S5, N9). Checked
  # right after the cut, before BRouter's hours, not by an OOM kill in the export after them.
  [[ "$1" =~ ^[0-9]+$ ]] || { echo "names input node count is not a whole number: '$1'"; return 1; }
  (( $1 <= $2 )) || { echo "names input has $1 nodes, over USCA_NAMES_MAX_INPUT_NODES ($2); see pins.env for the file-backed index"; return 1; }
}

basemap_matches() { # file timestamp sequence: does the archive's metadata name this snapshot?
  # Planetiler records the merged extract's header in the archive's metadata, and the merge stamps
  # that header with the extracts' timestamp and the first region's sequence (section 4), so these
  # two keys say which pair of extracts a basemap was built from.
  local meta
  [[ -s "$1" ]] || { echo "basemap check: $1 is missing or empty"; return 1; }
  meta="$(pmtiles show --metadata "$1")" || { echo "basemap check: cannot read $1's metadata"; return 1; }
  python3 - "$meta" "$2" "$3" <<'EOF'
import json, sys
m = json.loads(sys.argv[1])
got = (m.get("planetiler:osm:osmosisreplicationtime"), m.get("planetiler:osm:osmosisreplicationseq"))
if got != (sys.argv[2], sys.argv[3]):
    print(f"basemap check: built from {got[0]} sequence {got[1]}; the extracts are {sys.argv[2]} sequence {sys.argv[3]}")
    sys.exit(1)
EOF
}

# A region in USCA_REGIONS is its path under USCA_GEOFABRIK_BASE, as Geofabrik serves it: `us`,
# `canada`, or `us/puerto-rico`, nested under us/ (dispatch 2026-10-20-pi-14). The path builds every
# URL. Its stem, the basename (`${s##*/}`: us, canada, puerto-rico), names every local file, glob and
# outline, because Geofabrik's own dated file names and the names inside its .md5 files are the
# stem's (read 2026-10-20). For a bare name the two are the same string, so us and canada are
# fetched and named exactly as before.
region_url_dir() { # path: the URL of the directory Geofabrik serves the region's dated files from
  if [[ "$1" == */* ]]; then echo "$USCA_GEOFABRIK_BASE/${1%/*}"; else echo "$USCA_GEOFABRIK_BASE"; fi
}

regions_ok() { # region...: lower-case paths of [a-z0-9-] segments, no two with one stem
  # Two regions with one stem would share each other's local files and each other's cleanup.
  local r st; declare -A seen=()
  [[ $# -gt 0 ]] || { echo "no regions in USCA_REGIONS"; return 1; }
  for r in "$@"; do
    [[ "$r" =~ ^[a-z0-9-]+(/[a-z0-9-]+)*$ ]] || { echo "region $r is not a path of lower-case [a-z0-9-] segments"; return 1; }
    st="${r##*/}"
    [[ -z "${seen[$st]:-}" ]] || { echo "regions ${seen[$st]} and $r share the stem $st, so they would share local file names"; return 1; }
    seen[$st]="$r"
  done
}

basemap_regions_match() { # file timestamp sequence: does the archive's .regions name this run's regions? (pi-14)
  # Planetiler's metadata carries only the first region's sequence (basemap_matches), so a basemap of
  # us and canada from this snapshot passes that check for a run that adds the islands. Each archive
  # this script builds has FILE.regions beside it (write_basemap_regions), naming the snapshot and
  # every region's sequence, as the manifest does. An archive without one predates it; every such
  # archive was built from USCA_BASEMAP_UNLISTED_REGIONS (pins.env), so it is accepted for a run of
  # exactly those regions, by the metadata check alone as before, and refused for any other.
  local side="$1.regions" got_ts got_seq
  if [[ ! -e "$side" ]]; then
    if [[ "$(echo $USCA_REGIONS)" == "$(echo $USCA_BASEMAP_UNLISTED_REGIONS)" ]]; then
      echo "basemap regions: no $side; an archive without one was built from $USCA_BASEMAP_UNLISTED_REGIONS (USCA_BASEMAP_UNLISTED_REGIONS), this run's regions"
      return 0
    fi
    echo "basemap regions: no $side, so nothing shows the archive holds $USCA_REGIONS; an archive without one holds only $USCA_BASEMAP_UNLISTED_REGIONS"
    return 1
  fi
  got_ts="$(sed -n 's/^timestamp=//p' "$side")"; got_seq="$(sed -n 's/^sequence=//p' "$side")"
  if [[ "$got_ts" != "$2" || "$got_seq" != "$3" ]]; then
    echo "basemap regions: $side says ${got_ts:-no timestamp} ${got_seq:-no sequence}; this run is $2 $3"
    return 1
  fi
  echo "basemap regions: $side names this run's snapshot and regions, $3"
}

write_basemap_regions() { # archive timestamp sequence: the .regions file basemap_regions_match reads
  printf 'timestamp=%s\nsequence=%s\n' "$2" "$3" > "$1.regions.part" && mv -- "$1.regions.part" "$1.regions"
}

default_route_iface() { # the interface of the IPv4 default route the kernel uses: the lowest metric
  # Read from USCA_ROUTE_FILE, /proc/net/route unless a test names another: `ip route` needs
  # netlink, which the unit leaves out (pi-09). Wi-Fi and the tether can both hold a default route
  # at once (metrics 600 and 750, observed 2026-10-20), so the first one listed is not enough.
  # Prints nothing when there is no default route. When the table cannot be read, awk's own exit
  # (non-zero, with its reason on stderr) is this function's, and download_floor says so.
  local f="${USCA_ROUTE_FILE:-/proc/net/route}"
  awk 'NR > 1 && $2 == "00000000" && (best == "" || $7 + 0 < metric) { best = $1; metric = $7 + 0 }
    END { if (best != "") print best }' "$f"
}

download_floor() { # prints "limit time what": the floor for the next connection, and why (pi-11)
  # Wi-Fi, an interface matching USCA_WIFI_IFACE_PATTERN, gets USCA_DOWNLOAD_SPEED_*_WIFI; any other
  # link, no default route, or a table that cannot be read gets the conservative USCA_DOWNLOAD_SPEED_*.
  # The Wi-Fi floor on the tether would end a connection about every 40 s, so every doubt goes to
  # the conservative one.
  local f="${USCA_ROUTE_FILE:-/proc/net/route}" ifc rc=0
  ifc="$(default_route_iface)" || rc=$?
  if (( rc != 0 )); then
    echo "$USCA_DOWNLOAD_SPEED_LIMIT $USCA_DOWNLOAD_SPEED_TIME cannot read $f, default floor"
  elif [[ -z "$ifc" ]]; then
    echo "$USCA_DOWNLOAD_SPEED_LIMIT $USCA_DOWNLOAD_SPEED_TIME no default route in $f, default floor"
  # shellcheck disable=SC2053 # the pattern is meant to match as a glob
  elif [[ "$ifc" == $USCA_WIFI_IFACE_PATTERN ]]; then
    echo "$USCA_DOWNLOAD_SPEED_LIMIT_WIFI $USCA_DOWNLOAD_SPEED_TIME_WIFI link $ifc, Wi-Fi floor"
  else
    echo "$USCA_DOWNLOAD_SPEED_LIMIT $USCA_DOWNLOAD_SPEED_TIME link $ifc, default floor"
  fi
}

curl_resume() { # url dest bytes: resume into dest.part until it holds bytes, then move into place
  # One connection at a time, each resuming with -C -. curl gives a connection up when it averages
  # under a floor's bytes/s for its seconds, which catches a stall too; after a pause the next
  # resumes, up to USCA_DOWNLOAD_MAX_RESTARTS connections. As in the loop that fetched the 261017
  # pair (2026-10-18). The floor is chosen before each connection, not once per run, from the link
  # carrying the default route then (download_floor): the Pi's link has changed mid-download
  # before, and a connection already open stays on the link it started on (inferred; pi-11).
  # Not curl's own --retry: with -C - it resends the offset it started from, so every retry
  # downloads again what the last one already had (measured against a local server, pi-09 Phase 1).
  local url="$1" dest="$2" want="$3" n=0 have now rc t0 floor lim tim what
  if [[ -s "$dest" && ! -e "$dest.part" ]]; then mv -- "$dest" "$dest.part"; fi  # from an earlier run
  have="$(stat -c %s -- "$dest.part" 2>/dev/null || echo 0)"
  while (( have < want )); do
    if (( n >= USCA_DOWNLOAD_MAX_RESTARTS )); then
      echo "${dest##*/}: gave up after $n connections (USCA_DOWNLOAD_MAX_RESTARTS) at $have of $want bytes; $dest.part is kept for the next run"
      return 1
    fi
    (( n == 0 )) || sleep "$USCA_DOWNLOAD_PAUSE"
    floor="$(download_floor)" && read -r lim tim what <<<"$floor" && [[ "$lim" =~ ^[0-9]+$ && "$tim" =~ ^[0-9]+$ ]] \
      || { echo "${dest##*/}: cannot choose a download floor (got \"${floor:-}\"); stopped before connection $(( n + 1 ))"; return 1; }
    t0=$SECONDS; rc=0
    curl -fL -sS -C - --connect-timeout 30 --speed-limit "$lim" --speed-time "$tim" \
      -A "$HTTP_USER_AGENT" -o "$dest.part" "$url" || rc=$?
    n=$((n + 1))
    now="$(stat -c %s -- "$dest.part" 2>/dev/null || echo 0)"
    log "${dest##*/}: connection $n ended (curl exit $rc) after $(( SECONDS - t0 ))s, +$(( now - have )) bytes; $now of $want bytes; $what $lim bytes/s over $tim s" >&2
    have=$now
  done
  if (( have != want )); then
    echo "${dest##*/}: $have bytes, more than the $want Geofabrik gives for it; $dest.part left for inspection"
    return 1
  fi
  mv -- "$dest.part" "$dest"
}

card_ready() { # is the card mounted, with its extracts directory writable? (B and C)
  mountpoint -q -- "$USCA_CARD_MOUNT" || { echo "$USCA_CARD_MOUNT is not a mounted filesystem"; return 1; }
  if ! mkdir -p -- "$CARD_OSM" 2>/dev/null || [[ ! -w "$CARD_OSM" ]]; then echo "cannot write $CARD_OSM"; return 1; fi
}

bef_store_ok() { # dir sums-sha256 files [full]: is BRouter's elevation store there and the one pinned? (G8)
  # Never build without heights silently: PosUnifier reads whatever .bef it finds and gives a
  # node no height where there is none, so a missing or swapped store would still make squares.
  # The store is pinned by its SHA256SUMS (that file's own sha256) and its file count; with
  # "full", every file is hashed against it too (about 1.9 GB read from the card).
  local dir=$1 want=$2 n=$3 sums="$1/SHA256SUMS" have listed held
  if [[ ! -d "$dir" ]]; then
    # For a run card_ready never checked (--local, --basemap), say what to look at first (N5).
    mountpoint -q -- "$USCA_CARD_MOUNT" 2>/dev/null || { echo "no elevation store at $dir ($USCA_CARD_MOUNT is not mounted)"; return 1; }
    echo "no elevation store at $dir"; return 1
  fi
  [[ -s "$sums" ]] || { echo "the elevation store $dir has no SHA256SUMS"; return 1; }
  have="$(sha256sum < "$sums" | cut -d' ' -f1)"
  [[ "$have" == "$want" ]] || { echo "$sums is $have, not the pinned USCA_BEF_SUMS_SHA256 $want"; return 1; }
  have="$(wc -l < "$sums")"
  [[ "$have" -eq "$n" ]] || { echo "$sums lists $have files, not the pinned USCA_BEF_FILES $n"; return 1; }
  # The names, not only the count (Sorrel's review N4): every listed .bef there and non-empty, and
  # no other.
  listed="$(sed -E 's/^[0-9a-f]{64} [ *]//' "$sums" | sort | tr '\n' ' ')"
  held="$(find "$dir" -maxdepth 1 -name '*.bef' -size +0 -printf '%f\n' | sort | tr '\n' ' ')"
  [[ "$held" == "$listed" ]] || { echo "$dir's non-empty .bef files are not the ones SHA256SUMS lists: $(comm -3 <(tr ' ' '\n' <<<"$listed") <(tr ' ' '\n' <<<"$held") | tr -d '\t' | grep . | head -5 | tr '\n' ' ')"; return 1; }
  if [[ "${4:-}" == full ]]; then
    # The failing files by name (N3).
    have="$(cd "$dir" && sha256sum -c --quiet SHA256SUMS 2>/dev/null)" \
      || { echo "a .bef in $dir does not match its SHA256SUMS: $(head -3 <<<"$have" | tr '\n' ' ')"; return 1; }
  fi
}

heights_read_ok() { # unodes-dir allowed-squares: did PosUnifier read heights for every square but the allowed? (G8, S1)
  # BRouter 1.7.10's PosUnifier renames a node tile <SQUARE>.u5d_3 when it read a 3" raster for
  # it and leaves <SQUARE>.u5d when it found no .bef, saying nothing (PosUnifier.java:128-146),
  # and WayLinker marks the square the same either way. So a store gone mid-stage shows only
  # here: an unsuffixed tile whose square isn't one of the pinned blocks without land data.
  local f sq bad=() allowed=" $2 "
  for f in "$1"/*.u5d; do
    [[ -e "$f" ]] || continue
    sq="$(basename "$f" .u5d)"
    [[ "$allowed" == *" $sq "* ]] || bad+=("$sq")
  done
  [[ ${#bad[@]} -eq 0 ]] || { echo "PosUnifier read no heights for ${bad[*]}: is $USCA_BEF_DIR still there?"; return 1; }
}

fetch_extract() { # url name bytes: download onto the card, and link it from $SRC/osm under the same name (B)
  local link="$SRC/osm/$2"
  if [[ -e "$link" && ! -L "$link" ]]; then
    echo "$link is a regular file; extracts now live on the card and are linked from $SRC/osm (move it to $CARD_OSM and link it, or remove it)"
    return 1
  fi
  card_ready || return 1
  curl_resume "$1" "$CARD_OSM/$2" "$3" || return 1
  ln -sfn -- "$CARD_OSM/$2" "$link"
}

remove_old_extracts() { # every extract of this script's regions but this run's inputs: link, card file, .md5, .headers
  # The card's files are walked as well as the links (B): at 18.8 GB a week, links removed alone
  # would fill the card in about five weeks. So a link's card file goes, and so does a card file no
  # link names. A link to anywhere else loses only the link. build-orwa.sh's extracts are left alone.
  local s f i keep base target
  for s in $USCA_REGIONS; do
    s="${s##*/}"  # the stem: local files are named by it (region_url_dir)
    for f in "$SRC/osm/$s-"[0-9]*.osm.pbf "$CARD_OSM/$s-"[0-9]*.osm.pbf; do
      [[ -e "$f" || -L "$f" ]] || continue
      base="${f##*/}"; keep=0
      for i in "${inputs[@]}"; do [[ "$base" == "${i##*/}" ]] && keep=1; done
      [[ $keep -eq 1 ]] && continue
      if [[ -L "$f" ]]; then
        target="$(readlink -- "$f")"
        [[ "$target" == "$CARD_OSM/$base" ]] \
          || warn remove-old-extracts "$f links to $target, not to the card; removed the link only"
      fi
      if rm -f -- "$f" "$f.md5" "$f.headers"; then log "removed old extract $base ($f)"
      else warn remove-old-extracts "could not remove old extract $f"; fi
    done
  done
}

remote_size() { # url: the file's size in bytes, from a HEAD request's Content-Length
  local n
  n="$(curl -sSfIL -A "$HTTP_USER_AGENT" "$1" | tr -d '\r' | awk 'tolower($1) == "content-length:" { n = $2 } END { print n }')" || true
  [[ "$n" =~ ^[0-9]+$ ]] || { echo "no Content-Length for $1"; return 1; }
  echo "$n"
}

planetiler_need() { # input-bytes: Planetiler's peak on the NVMe, from the ratios measured on the 261017 run (D)
  python3 - "$1" "$USCA_DISK_FEATURES_RATIO" "$USCA_DISK_ARCHIVE_RATIO" "$USCA_DISK_MULTIPOLYGON_RATIO" <<'EOF'
from decimal import Decimal, ROUND_CEILING
import sys
print((Decimal(sys.argv[1]) * sum(Decimal(r) for r in sys.argv[2:])).to_integral_value(rounding=ROUND_CEILING))
EOF
}

with_margin() { # bytes: plus USCA_DISK_MARGIN_PERCENT of them, and at least USCA_DISK_MARGIN_MIN_BYTES more
  local m=$(( $1 * USCA_DISK_MARGIN_PERCENT / 100 ))
  (( m >= USCA_DISK_MARGIN_MIN_BYTES )) || m=$USCA_DISK_MARGIN_MIN_BYTES
  echo $(( $1 + m ))
}

gb() { # bytes: as GB (10^9 bytes), for reading only; every check is done in bytes
  awk -v b="$1" 'BEGIN { printf "%.1f GB", b / 1e9 }'
}

check_disk() { # name dir need-bytes why: is there need-bytes free on dir's filesystem?
  local free
  free="$(df -B1 --output=avail -- "$2" 2>/dev/null | tail -1 | tr -dc 0-9)"
  [[ -n "$free" ]] || { echo "$1 ($2): cannot read its free space"; return 1; }
  if (( free < $3 )); then
    echo "$1 ($2): $free bytes free ($(gb "$free")), this run needs $3 bytes ($(gb "$3")): $4; short by $(( $3 - free )) bytes (GB = 10^9 bytes)"
    return 1
  fi
  echo "$1 ($2): $free bytes free ($(gb "$free")), this run needs $3 bytes ($(gb "$3")): $4"
}

disk_check() { # input-bytes download-bytes: room on both disks for this run? (D) Prints each check.
  # The NVMe holds Planetiler's files; the card the downloads and the merged extract. A --basemap
  # run has no Planetiler, so no ratios: it keeps the fixed floor it had, in bytes, since its
  # routing stage's disk need has not been measured. --fetch-only writes only to the card.
  local in="$1" dl="$2" nvme="" card="" why_n="" why_c="" p ok=0
  if [[ $fetch_only -eq 1 ]]; then
    :
  elif [[ -n "$basemap" ]]; then
    nvme="$USCA_BASEMAP_RUN_MIN_FREE_BYTES"; why_n="the fixed floor for a --basemap run (USCA_BASEMAP_RUN_MIN_FREE_BYTES)"
  else
    p="$(planetiler_need "$in")" || { echo "cannot compute Planetiler's need"; return 1; }
    nvme="$(with_margin "$p")"
    why_n="Planetiler, (features $USCA_DISK_FEATURES_RATIO + archive $USCA_DISK_ARCHIVE_RATIO + multipolygon $USCA_DISK_MULTIPOLYGON_RATIO) x $in input bytes = $p, plus $(( nvme - p )) margin"
  fi
  if [[ $fetch_only -eq 0 && -z "$basemap" ]]; then
    card="$(with_margin $(( dl + in )))"; why_c="$dl to download + $in for the merged extract, plus $(( card - dl - in )) margin"
  elif (( dl > 0 )); then
    card="$(with_margin "$dl")"; why_c="$dl to download, plus $(( card - dl )) margin"
  fi
  if [[ -n "$nvme" ]]; then check_disk NVMe "$FB_SRV" "$nvme" "$why_n" || ok=1; fi
  if [[ -n "$card" ]]; then
    if card_ready; then check_disk card "$USCA_CARD_DIR" "$card" "$why_c" || ok=1; else ok=1; fi
  fi
  return $ok
}

run_disk_check() { # input-bytes download-bytes: disk_check, each line logged, or the run stops
  local out l
  if out="$(disk_check "$1" "$2")"; then
    while IFS= read -r l; do [[ -z "$l" ]] || log "$l"; done <<<"$out"
  else
    die "not enough room: $(tr '\n' ' ' <<<"$out")"
  fi
}

has_default_route() { # [route-file ipv6-route-file]: is there a default route in the kernel's tables?
  # Read from /proc: `ip route` needs netlink, which the unit's RestrictAddressFamilies leaves out
  # (measured, pi-09 Phase 1). IPv6's unreachable default on lo does not count.
  awk 'NR > 1 && $2 == "00000000" { f = 1 } END { exit !f }' "${1:-/proc/net/route}" 2>/dev/null && return 0
  awk '$1 == "00000000000000000000000000000000" && $2 == "00" && $10 != "lo" { f = 1 } END { exit !f }' \
    "${2:-/proc/net/ipv6_route}" 2>/dev/null
}

wait_for_network() { # host: wait, at most USCA_NETWORK_WAIT_SECONDS, for a default route and the host to resolve (G)
  local waited=0 missing
  while :; do
    missing=()
    has_default_route || missing+=("no default route")
    getent hosts "$1" >/dev/null 2>&1 || missing+=("$1 does not resolve")
    [[ ${#missing[@]} -gt 0 ]] || return 0
    if (( waited >= USCA_NETWORK_WAIT_SECONDS )); then
      echo "after $waited s: ${missing[*]/%/;}" | sed 's/;$//'
      return 1
    fi
    (( waited > 0 )) || log "waiting up to ${USCA_NETWORK_WAIT_SECONDS}s for the network: ${missing[*]}" >&2
    sleep "$USCA_NETWORK_POLL_SECONDS"
    waited=$(( waited + USCA_NETWORK_POLL_SECONDS ))
  done
}

curl_get() { # url dest: download to dest.part with the response headers, then move into place
  curl -sSfL --retry 3 --retry-delay 10 -A "$HTTP_USER_AGENT" -D "$2.headers" -o "$2.part" "$1"
  mv "$2.part" "$2"
}

# The region list, before anything else: a path that is not one, or two regions with one stem.
# shellcheck disable=SC2086 # the list is meant to split
regions_err="$(regions_ok $USCA_REGIONS)" || die "USCA_REGIONS: $regions_err"

# --- 0. The network, before asking Geofabrik anything (not for --local) ------------------------
if [[ -z "$local_date" ]]; then
  set_stage network
  geofabrik_host="${USCA_GEOFABRIK_BASE#*://}"; geofabrik_host="${geofabrik_host%%/*}"
  net="$(wait_for_network "$geofabrik_host")" || die "the network is not up: $net"
  log "network up: a default route, and $geofabrik_host resolves"
fi

# --- 1. Which extracts, and are they new? ------------------------------------------------------
set_stage state
declare -A seq ts file
if [[ -n "$local_date" ]]; then
  for s in $USCA_REGIONS; do
    file[$s]="${s##*/}-$local_date.osm.pbf"
    f="$SRC/osm/${file[$s]}"
    [[ -s "$f" && -s "$f.md5" ]] || die "--local: $f or its .md5 is missing"
    ts[$s]="$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$f")"
    seq[$s]="$(osmium fileinfo -g header.option.osmosis_replication_sequence_number "$f")"
    [[ -n "${ts[$s]}" && -n "${seq[$s]}" ]] || die "--local: $f has no replication timestamp or sequence in its header"
    log "$s: ${file[$s]} (local), sequence ${seq[$s]}, ${ts[$s]}"
  done
else
  for s in $USCA_REGIONS; do
    state_txt="$(curl -sSf -A "$HTTP_USER_AGENT" "$USCA_GEOFABRIK_BASE/$s-updates/state.txt")"
    seq[$s]="$(sed -n 's/^sequenceNumber=//p' <<<"$state_txt")"
    ts[$s]="$(sed -n 's/^timestamp=//p' <<<"$state_txt" | tr -d '\\')"
    # The dated file -latest redirects to, so a mid-run publication cannot swap the file.
    file[$s]="$(curl -sSfI -A "$HTTP_USER_AGENT" "$USCA_GEOFABRIK_BASE/$s-latest.osm.pbf" \
      | sed -n 's/^[Ll]ocation: .*\/\([^/]*\.osm\.pbf\)\r\?$/\1/p')"
    [[ -n "${seq[$s]}" && -n "${ts[$s]}" && -n "${file[$s]}" ]] || die "could not read the current $s extract"
    log "$s: ${file[$s]}, sequence ${seq[$s]}, ${ts[$s]}"
  done
fi
# Geofabrik numbers each region's sequence separately (us and canada differ), so only the
# timestamps must agree: every extract is then cut from the same planet snapshot.
first="${USCA_REGIONS%% *}"
for s in $USCA_REGIONS; do
  [[ "${ts[$s]}" == "${ts[$first]}" ]] \
    || die "extracts are from different snapshots: $first ${ts[$first]}, $s ${ts[$s]}"
done
osm_ts="${ts[$first]}"
# The manifest's sequence names every region's by its path, e.g.
# us:2416,canada:4946,us/puerto-rico:3282,us/us-virgin-islands:1791 (us and canada as before).
osm_seq="$(for s in $USCA_REGIONS; do printf '%s:%s,' "$s" "${seq[$s]}"; done)"; osm_seq="${osm_seq%,}"
if [[ $fetch_only -eq 0 && $force -eq 0 && -f "$OUT/current/manifest.json" ]]; then
  last_seq="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["osm"]["replication_sequence"])' \
    "$OUT/current/manifest.json")"
  if [[ "$last_seq" == "$osm_seq" ]]; then
    log "the current output is already built from $osm_seq; nothing to do"
    "${RECORD[@]}" status "$STATUS" --state skipped --stage state --run-id "$run_id" --osm-timestamp "$osm_ts" \
      --message "no new extract"
    trap - EXIT; cleanup_scratch; exit 0
  fi
fi
# Checked here, before anything long runs, so a basemap from other extracts stops the run at once.
if [[ -n "$basemap" ]]; then
  basemap_matches "$basemap" "$osm_ts" "${seq[$first]}" || die "--basemap: $basemap was not built from these extracts"
  bm_regions="$(basemap_regions_match "$basemap" "$osm_ts" "$osm_seq")" || die "--basemap: $bm_regions"
  log "--basemap: $bm_regions"
  log "--basemap: $basemap was built from $osm_ts, sequence ${seq[$first]}; Planetiler will not run"
fi
# Room on both disks, from the extracts' sizes: Geofabrik's Content-Length, or the files' own for
# --local. Checked here so an over-size week fails in minutes, not a day of downloading in (D).
declare -A size
in_bytes=0; dl_bytes=0
for s in $USCA_REGIONS; do
  if [[ -n "$local_date" ]]; then
    size[$s]="$(stat -L -c %s -- "$SRC/osm/${file[$s]}")"
  else
    size[$s]="$(remote_size "$(region_url_dir "$s")/${file[$s]}")" || die "could not read the size of ${file[$s]}: ${size[$s]}"
    # What a run before this one left on the card: a partial download, or the whole file.
    got="$(stat -c %s -- "$CARD_OSM/${file[$s]}.part" 2>/dev/null || stat -c %s -- "$CARD_OSM/${file[$s]}" 2>/dev/null || echo 0)"
    (( got <= size[$s] )) || got=0
    dl_bytes=$(( dl_bytes + size[$s] - got ))
  fi
  in_bytes=$(( in_bytes + size[$s] ))
done
log "inputs: $in_bytes bytes, $dl_bytes of them still to download"
run_disk_check "$in_bytes" "$dl_bytes"
# The elevation store (G8), in full before the downloads, so a missing or changed store stops
# the run in a minute rather than after Planetiler's hours.
# A --fetch-only run builds nothing, so it doesn't need the store (Sorrel's review N1).
if [[ $fetch_only -eq 0 ]]; then
  bef="$(bef_store_ok "$USCA_BEF_DIR" "$USCA_BEF_SUMS_SHA256" "$USCA_BEF_FILES" full)" || die "$bef"
  log "elevation store: $USCA_BEF_DIR, $USCA_BEF_FILES .bef files matching SHA256SUMS ($USCA_BEF_SUMS_SHA256)"
fi

# --- 2. Extracts: download (resuming), check MD5, check the header timestamp -------------------
set_stage fetch-extracts
inputs=()
for s in $USCA_REGIONS; do
  f="$SRC/osm/${file[$s]}"
  if [[ -z "$local_date" ]]; then
    curl -sSf -A "$HTTP_USER_AGENT" -o "$f.md5" "$(region_url_dir "$s")/${file[$s]}.md5"
    if [[ ! -s "$f" ]] || ! (cd "$SRC/osm" && md5sum -c --quiet "$f.md5" >/dev/null 2>&1); then
      log "downloading ${file[$s]} (${size[$s]} bytes) to $CARD_OSM"
      dl="$(fetch_extract "$(region_url_dir "$s")/${file[$s]}" "${file[$s]}" "${size[$s]}")" \
        || die "download of ${file[$s]} failed: $dl"
    fi
  fi
  (cd "$SRC/osm" && md5sum -c --quiet "$f.md5") || die "MD5 mismatch for ${file[$s]}"
  header_ts="$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$f")"
  [[ "$header_ts" == "$osm_ts" ]] || die "${file[$s]} header says $header_ts, expected $osm_ts"
  log "${file[$s]}: MD5 good, header timestamp $header_ts"
  inputs+=("$f")
done

# --- 3. The profile's other sources, and the regions' outlines ----------------------------------
set_stage fetch-sources
fetch_static() { # url name: fetched once, kept
  if [[ ! -s "$SRC/aux/$2" ]]; then log "fetching $2"; curl_get "$1" "$SRC/aux/$2"; fi
}
fetch_monthly() { # url name: refreshed when older than COASTLINE_MAX_AGE_DAYS (owner's ruling C)
  if [[ ! -s "$SRC/aux/$2" ]] || [[ -n "$(find "$SRC/aux/$2" -mtime +"$COASTLINE_MAX_AGE_DAYS")" ]]; then
    log "fetching $2"; curl_get "$1" "$SRC/aux/$2"
  fi
}
fetch_static "$SRC_NATURAL_EARTH_URL" natural_earth_vector.gpkg.zip
fetch_static "$SRC_LANDCOVER_URL" daylight-landcover.gpkg
fetch_static "$SRC_QRANK_URL" qrank.csv.gz
fetch_static "$SRC_PGF_URL" pgf-encoding.zip
fetch_monthly "$SRC_WATER_URL" water-polygons-split-3857.zip
fetch_monthly "$SRC_LAND_URL" land-polygons-split-3857.zip
polys=()
for s in $USCA_REGIONS; do
  # Refreshed on the coastlines' schedule: Geofabrik moves an outline rarely, and a stale one
  # only shifts which offshore tiles are drawn.
  fetch_monthly "$USCA_GEOFABRIK_BASE/$s.poly" "${s##*/}.poly"
  polys+=("$SRC/aux/${s##*/}.poly")
done
sources=("$SRC/aux/natural_earth_vector.gpkg.zip" "$SRC/aux/daylight-landcover.gpkg" "$SRC/aux/qrank.csv.gz"
  "$SRC/aux/pgf-encoding.zip" "$SRC/aux/water-polygons-split-3857.zip" "$SRC/aux/land-polygons-split-3857.zip"
  "${polys[@]}")
for f in "${sources[@]}"; do [[ -s "$f" ]] || die "missing source $f"; done
if [[ $fetch_only -eq 1 ]]; then
  log "fetch only: extracts and sources are in $SRC"
  "${RECORD[@]}" status "$STATUS" --state ok --stage fetch-only --run-id "$run_id" --osm-timestamp "$osm_ts" \
    --message "fetch only; nothing built"
  trap - EXIT; cleanup_scratch; exit 0
fi

{
  "$JAVA" -version 2>&1 | head -1
  osmium --version 2>&1 | head -1
  pmtiles version 2>&1 | head -1
  echo "basemap jar sha256 $(sha256sum "$BASEMAP_JAR" | cut -d' ' -f1)"
  echo "brouter jar sha256 $(sha256sum "$BROUTER_HOME/brouter-$BROUTER_VERSION-all.jar" | cut -d' ' -f1)"
} > "$run/versions.txt"

# --- 4. Room to work, then merge into one extract, stamped with its own timestamp ---------------
set_stage merge
# The state stage's check again, from the files now here, with nothing left to download.
in_bytes=0
for i in "${inputs[@]}"; do in_bytes=$(( in_bytes + $(stat -L -c %s -- "$i") )); done
run_disk_check "$in_bytes" 0
# Only Planetiler reads the full merge (routing has its own, smaller one in section 6), so a run
# with --basemap skips it and makes nothing on the card. The merge is written to the card (C),
# where Planetiler reads it, and removed once Planetiler is done, or by cleanup_scratch.
if [[ -z "$basemap" ]]; then
  mkdir -p -- "$card_run"
  merged="$card_run/$USCA_EXTRACT_NAME.osm.pbf"
  # osmium merge 1.15.0 drops the replication timestamp and the bounding boxes from the header
  # (build-orwa.sh, checked on synthetic files 2026-10-04), so the timestamp is set here and the
  # bounds come from the inputs.
  "${TIMED[@]}" merge -- osmium merge "${inputs[@]}" -o "$merged" \
    --output-header="osmosis_replication_timestamp=$osm_ts" \
    --output-header="osmosis_replication_sequence_number=${seq[$first]}"
  [[ "$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$merged")" == "$osm_ts" ]] \
    || die "merged header lacks the replication timestamp"
fi
bounds="$(python3 "$here/tools/union_bounds.py" "${inputs[@]}")" || die "an input has no header bounding box"
echo "$bounds" > "$run/bounds.txt"
poly="$run/$USCA_EXTRACT_NAME.poly"
python3 "$here/tools/merge_poly.py" "$USCA_EXTRACT_NAME" "$poly" "${polys[@]}" || die "could not join the outlines"
log "bounds from the inputs' headers: computed (not logged, as in build-orwa.sh); Planetiler limited to the outlines of: $USCA_REGIONS"

# --- 5. The map --------------------------------------------------------------------------------
mkdir -p "$run/out"
pmtiles_out="$run/out/$USCA_EXTRACT_NAME.pmtiles"
if [[ -n "$basemap" ]]; then
  set_stage basemap
  # A hard link, not a copy: the archive is tens of GB. If this run fails, keep_failed_run moves
  # the link and FILE itself stays where it was.
  ln -- "$basemap" "$pmtiles_out" \
    || die "--basemap: cannot hard-link $basemap into $run/out; it must be on the same filesystem as $FB_SRV"
  # The manifest carries versions.txt, so the output says its basemap was not built by this run.
  echo "basemap: reused from $basemap (--basemap), not built by this run" >> "$run/versions.txt"
  # This run's own .regions, not FILE's: the state stage has just checked FILE against this run.
  write_basemap_regions "$pmtiles_out" "$osm_ts" "$osm_seq" || die "cannot write $pmtiles_out.regions"
  log "basemap: $basemap linked into the output"
else
  set_stage planetiler
  pt="$run/planetiler"
  mkdir -p "$pt/data/sources"
  for f in "${sources[@]}"; do ln -s "$f" "$pt/data/sources/$(basename "$f")"; done
  ln -s "$merged" "$pt/data/sources/$USCA_EXTRACT_NAME.osm.pbf"
  # No --download: every source is already in data/sources, so nothing is fetched out of sight.
  (cd "$pt" && "${TIMED[@]}" planetiler -- "$JAVA" "-Xmx$USCA_PLANETILER_XMX" -jar "$BASEMAP_JAR" \
    --area="$USCA_EXTRACT_NAME" --bounds="$bounds" --polygon="$poly" --maxzoom="$MAXZOOM" \
    --output="$pmtiles_out" --tmpdir="$pt/tmp" --http_user_agent="$HTTP_USER_AGENT" \
    --storage=mmap --nodemap_storage=mmap --nodemap_type="$USCA_NODEMAP_TYPE" --nodemap_madvise=true \
    --multipolygon_geometry_storage=mmap)
  # Which regions and snapshot this archive holds, for a later --basemap (basemap_regions_match).
  write_basemap_regions "$pmtiles_out" "$osm_ts" "$osm_seq" || die "cannot write $pmtiles_out.regions"
  rm -rf -- "$pt/tmp"  # tens of GB; the routing step needs the room
  rm -f -- "$merged"   # nothing after Planetiler reads it
  remove_card_run      # and its directory on the card
fi

# --- 6. The routing data -----------------------------------------------------------------------
# BRouter's cutter ran out of a 2 GB heap 134M nodes into the full 2.4B-node merge (run
# usca-20261019T011311Z, OutOfMemoryError in TinyDenseLongMap), and holds about 16 bytes per node
# of its input whatever the input (measured again on the cut-down Canada input, 33M nodes in 512
# MB). So it is handed only what reaches the .rd5 squares, and that in bands it can hold (the
# owner's choices A and finer regions, 2026-10-19):
#   - each extract cut to the ways all.brf gives a cost to, the nodes they use, route relations and
#     turn restrictions (USCA_ROUTING_FILTER), and the cuts merged;
#   - that cut into longitude bands (USCA_ROUTING_BANDS), each with USCA_ROUTING_BAND_MARGIN
#     degrees either side and every way that touches it whole (osmium extract -s complete_ways);
#   - BRouter's three steps run on each band, keeping only the squares whose west edge is inside
#     the band. On the Canada input built whole and in two bands, all 106 squares matched byte
#     for byte apart from the time WayLinker stamps into each (2026-10-19).
set_stage routing-input
routing_bands_ok $USCA_ROUTING_BANDS || die "USCA_ROUTING_BANDS must run from -180 to 180 on 5-degree lines: $USCA_ROUTING_BANDS"
read -r -a routing_filter <<<"$USCA_ROUTING_FILTER"
routing_parts=()
for i in "${inputs[@]}"; do
  part="$run/routing-$(basename "$i")"
  "${TIMED[@]}" "routing-filter-$(basename "$i" .osm.pbf)" -- osmium tags-filter "$i" "${routing_filter[@]}" -o "$part"
  routing_parts+=("$part")
done
routing_in="$run/$USCA_EXTRACT_NAME-routing.osm.pbf"
"${TIMED[@]}" routing-merge -- osmium merge "${routing_parts[@]}" -o "$routing_in"
rm -f -- "${routing_parts[@]}"
log "routing input: $(osmium fileinfo -e -g data.count.nodes "$routing_in") nodes, $(osmium fileinfo -e -g data.count.ways "$routing_in") ways"

set_stage routing-bands
# Every band is cut and counted before BRouter runs on any of them, so a band list that no longer
# fits stops the run in minutes, naming every band over the limit, rather than hours in at the
# first band that is too big. The first band list was drawn from a highway-and-ferry cut and
# would have put the last band at about 187M nodes of this input (826.8M; run
# usca-20261019T130011Z, stopped before any band ran).
band_files=(); over=()
for band in $USCA_ROUTING_BANDS; do
  IFS=: read -r bw be <<<"$band"
  lo=$(( bw - USCA_ROUTING_BAND_MARGIN < -180 ? -180 : bw - USCA_ROUTING_BAND_MARGIN ))
  hi=$(( be + USCA_ROUTING_BAND_MARGIN > 180 ? 180 : be + USCA_ROUTING_BAND_MARGIN ))
  f="$run/routing-band_${bw}_${be}.osm.pbf"
  "${TIMED[@]}" "routing-band-$band" -- osmium extract -b "$lo,-90,$hi,90" -s complete_ways --overwrite \
    -o "$f" "$routing_in"
  nodes="$(osmium fileinfo -e -g data.count.nodes "$f")"
  log "routing band $band: $nodes nodes (extract $lo to $hi)"
  [[ "$nodes" -le "$USCA_BROUTER_MAX_BAND_NODES" ]] || over+=("$band ($nodes)")
  band_files+=("$f")
done
[[ ${#over[@]} -eq 0 ]] \
  || die "routing bands over USCA_BROUTER_MAX_BAND_NODES ($USCA_BROUTER_MAX_BAND_NODES): ${over[*]}; split them in USCA_ROUTING_BANDS"
# The names files' input (dispatch pi-31): the highway ways with a name or a ref, cut from this
# same routing input, so the names and the .rd5 squares come from one extract by construction.
# USCA_ROUTING_FILTER keeps every highway way whole with its nodes, so this cut is the same as
# one from the raw extracts (shown on the USVI extract, pi-31). Cut here, while $routing_in
# exists, and encoded after BRouter (the names stage), once the square list is known. Two
# filters, since tags-filter has no "and" of two keys.
set_stage names-input
names_in="$run/$USCA_EXTRACT_NAME-names.osm.pbf"
names_cut "$routing_in" "$names_in" || die "could not cut the names input from $routing_in"
names_nodes="$(osmium fileinfo -e -g data.count.nodes "$names_in")"
log "names input: $names_nodes nodes, $(osmium fileinfo -e -g data.count.ways "$names_in") ways, $(stat -c %s "$names_in") bytes"
names_ok="$(names_nodes_ok "$names_nodes" "$USCA_NAMES_MAX_INPUT_NODES")" || die "$names_ok"
rm -f -- "$routing_in"

set_stage brouter
# Again, quickly, before BRouter reads it (the card could have gone since the start).
bef="$(bef_store_ok "$USCA_BEF_DIR" "$USCA_BEF_SUMS_SHA256" "$USCA_BEF_FILES")" || die "$bef"
br="$run/brouter"
profiles="$BROUTER_HOME/profiles2"
bjava=("$JAVA" "-Xmx$USCA_BROUTER_XMX" -cp "$BROUTER_HOME/brouter-$BROUTER_VERSION-all.jar")
mkdir -p "$run/out/segments"
bands=($USCA_ROUTING_BANDS)
for k in "${!bands[@]}"; do
  band="${bands[$k]}"; band_in="${band_files[$k]}"
  IFS=: read -r bw be <<<"$band"
  rm -rf -- "$br"
  mkdir -p "$br"/{nodetiles,waytiles,waytiles55,nodes55,unodes55,segments}
  # The three steps of BRouter's process_pbf_planet.sh at v1.7.10, as in build-orwa.sh: no
  # database pseudo-tags (RECORD -476). Heights come from the pinned 3" .bef store (G8, from
  # 2026-11-01): PosUnifier's last argument, an empty folder until then.
  (cd "$br" && "${TIMED[@]}" "brouter-cut-$band" -- "${bjava[@]}" -Ddeletetmpfiles=true btools.mapcreator.OsmFastCutter \
    "$profiles/lookups.dat" nodetiles waytiles nodes55 waytiles55 bordernids.dat relations.dat restrictions.dat \
    "$profiles/all.brf" "$profiles/trekking.brf" "$profiles/softaccess.brf" "$band_in")
  (cd "$br" && "${TIMED[@]}" "brouter-unify-$band" -- "${bjava[@]}" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
    nodes55 unodes55 bordernids.dat bordernodes.dat "$USCA_BEF_DIR")
  # Every square of this band read its heights, or the run stops here (S1).
  hr="$(heights_read_ok "$br/unodes55" "$USCA_BEF_NONE")" || die "routing band $band: $hr"
  none_here="$(cd "$br/unodes55" && ls -- *.u5d 2>/dev/null | sed 's/\.u5d$//' | tr '\n' ' ')"
  [[ -z "$none_here" ]] || log "routing band $band: no heights, as pinned in USCA_BEF_NONE, for $none_here"
  (cd "$br" && "${TIMED[@]}" "brouter-link-$band" -- "${bjava[@]}" -DskipEncodingCheck=true btools.mapcreator.WayLinker \
    unodes55 waytiles55 bordernodes.dat restrictions.dat "$profiles/lookups.dat" "$profiles/all.brf" segments rd5)
  kept=0
  for f in "$br"/segments/*.rd5; do
    [[ -e "$f" ]] || continue
    x="$(rd5_west_edge "$f")" || die "cannot read the square of $(basename "$f")"
    # A square outside the band's own range holds only the margin's part of it; its own band
    # builds it whole.
    if (( bw <= x && x < be )); then mv -- "$f" "$run/out/segments/"; kept=$((kept + 1)); fi
  done
  log "routing band $band: $kept squares kept"
  rm -f -- "$band_in"
done
rm -rf -- "$br"
# Each .rd5's file date is set to the extract's (WayLinker stamps its own run time inside).
touch -d "$osm_ts" "$run/out/segments/"*.rd5

# --- 6b. Trail and road names per route square (FNAM v1, dispatch pi-31) ----------------------
# One names1/<NAME>.fnm per .rd5 in segments/, even an empty one (names_encode). Nothing here
# touches the .rd5 squares.
set_stage names
names_encode "$names_in" "$run/out/names1" "$run/out/segments" "$osm_ts" "$run/names-summary.json" \
  || die "the names encode failed"
log "names: $(cat "$run/names-summary.json")"
rm -f -- "$names_in"

# --- 7. Validate before publishing -------------------------------------------------------------
set_stage validate
header="$(pmtiles show --header-json "$pmtiles_out")"
metadata="$(pmtiles show --metadata "$pmtiles_out")"
python3 - "$header" "$metadata" "$bounds" "$MAXZOOM" <<'EOF' || die "archive check failed"
import json, sys
header, metadata = json.loads(sys.argv[1]), json.loads(sys.argv[2])
want = [float(v) for v in sys.argv[3].split(",")]
problems = []
if header["minzoom"] != 0 or header["maxzoom"] != int(sys.argv[4]):
    problems.append(f"zooms {header['minzoom']}-{header['maxzoom']}")
b = header["bounds"]
if not (b[0] >= want[0] - 0.01 and b[1] >= want[1] - 0.01 and b[2] <= want[2] + 0.01 and b[3] <= want[3] + 0.01):
    problems.append("archive bounds extend beyond the extracts'")
layers = {l["id"]: set(l.get("fields", {})) for l in metadata.get("vector_layers", [])}
for name in ("boundaries", "buildings", "earth", "landcover", "landuse", "places", "pois", "roads", "water"):
    if name not in layers:
        problems.append(f"layer {name} missing")
trail = {"sac_scale", "trail_visibility", "informal", "tracktype", "surface", "foot"}
missing = trail - layers.get("roads", set())
if missing:
    problems.append(f"roads layer lacks {sorted(missing)}")
if problems:
    print("archive check: " + "; ".join(problems)); sys.exit(1)
print(f"archive check: zooms 0-{header['maxzoom']}, {len(layers)} layers, roads carries all six trail keys")
EOF
# Coverage: each sample point needs a z15 tile with roads in it, and its .rd5 square.
: > "$run/coverage.txt"
for point in $USCA_COVERAGE_POINTS; do
  IFS=: read -r pname plon plat <<<"$point"
  read -r z x y < <(z15_tile "$plon" "$plat")
  tile="$run/coverage-$pname.mvt"
  # pmtiles tile exits 0 with no output for a tile the archive lacks (seen 2026-10-18 on the Oregon +
  # Washington archive at Miami), so the road count below is what catches a missing tile.
  pmtiles tile "$pmtiles_out" "$z" "$x" "$y" > "$tile" 2>/dev/null || die "$pname: could not read z15 tile $z/$x/$y"
  roads="$(python3 "$here/tools/mvt_decode.py" schema "$tile" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("roads", {}).get("features", 0))')"
  [[ "$roads" -gt 0 ]] || die "$pname: z15 tile $z/$x/$y is missing or has no roads"
  sq="$(rd5_square "$plon" "$plat")"
  [[ -f "$run/out/segments/$sq.rd5" ]] || die "$pname: no routing square $sq.rd5"
  size="$(stat -c %s "$run/out/segments/$sq.rd5")"
  [[ "$size" -gt 100000 ]] || die "$pname: $sq.rd5 is implausibly small ($size bytes)"
  printf '%s\t%s/%s/%s\t%s roads\t%s.rd5\t%s bytes\n' "$pname" "$z" "$x" "$y" "$roads" "$sq" "$size" >> "$run/coverage.txt"
  rm -f -- "$tile"
done
for f in "$run/out/segments/"*.rd5; do
  [[ $(stat -c %s "$f") -gt 0 ]] || die "$(basename "$f") is empty"
done
# The names files: one per .rd5 and no other; each one's magic, version, edges (from its name)
# and timestamp; its tables, directory and cells inside the file and covering its records
# exactly. The line names the count, the total bytes and the largest file.
names_check="$(python3 "$here/tools/fnam_build.py" check "$run/out/names1" --rd5-dir "$run/out/segments" \
  --osm-timestamp "$osm_ts")" || die "$names_check"
log "$names_check"
log "validated: archive; $(wc -l < "$run/coverage.txt") sample points covered; $(ls "$run/out/segments" | wc -l) .rd5 squares; $(ls "$run/out/names1" | wc -l) .fnm names files"
cat "$run/coverage.txt"

# --- 8. Manifest, then publish both together ---------------------------------------------------
set_stage publish
cp "$run/bounds.txt" "$run/coverage.txt" "$run/out/"
"${RECORD[@]}" manifest "$run/out/manifest.json" --pins "$here/pins.env" --run-dir "$run" \
  --osm-timestamp "$osm_ts" --osm-sequence "$osm_seq" --inputs "${inputs[@]}" --sources "${sources[@]}" \
  --outputs "$pmtiles_out" "$run/out/segments/"*.rd5 "$run/out/names1/"*.fnm
name="$(date -u -d "$osm_ts" +%Y%m%dT%H%M%SZ)"
[[ -e "$OUT/$name" ]] && name="$name-$run_id"
mv "$run/out" "$OUT/$name"
ln -sfn "$name" "$OUT/.current.new"
mv -T "$OUT/.current.new" "$OUT/current"
log "published $OUT/$name; current -> $name"

# From here the new output is live, and nothing below may record this run as failed, as in
# build-orwa.sh: failures are collected as warnings and the status written once, at the end.
trap - EXIT
set +e
warnings=()
warn() { # step-name message
  log "WARNING (the output is already published): $2"
  [[ " ${warnings[*]} " == *" $1 "* ]] || warnings+=("$1")
}

# Keep the newest USCA_KEEP_GOOD_OUTPUTS outputs; never the one current points at.
current_target="$(readlink "$OUT/current")"
[[ -n "$current_target" ]] || warn remove-old-outputs "cannot read the current link; old outputs left in place"
mapfile -t olds < <(cd "$OUT" && ls -1d [0-9]*T*Z* 2>/dev/null | sort -r | tail -n +"$((USCA_KEEP_GOOD_OUTPUTS + 1))")
for d in "${olds[@]}"; do
  [[ -n "$current_target" && "$d" != "$current_target" && -d "$OUT/$d" ]] || continue
  if rm -rf -- "${OUT:?}/$d"; then log "removed old output $d"
  else warn remove-old-outputs "could not remove old output $d"; fi
done

# Keep only this output's extracts among this script's regions, the card files with their links;
# build-orwa.sh's are left alone.
remove_old_extracts

cleanup_scratch || warn remove-scratch "could not remove the scratch directory $run"

if [[ ${#warnings[@]} -eq 0 ]]; then
  final=(--state ok)
else
  final=(--state ok_with_warnings --warnings "$(IFS=,; echo "${warnings[*]}")")
fi
"${RECORD[@]}" status "$STATUS" "${final[@]}" --stage done --run-id "$run_id" --osm-timestamp "$osm_ts" \
  --output "$OUT/$name" || log "WARNING (the output is already published): could not write $STATUS"
log "done${warnings[*]:+, with warnings: ${warnings[*]}}"
exit 0
