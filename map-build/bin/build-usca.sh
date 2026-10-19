#!/usr/bin/env bash
# One build of Forager's US + Canada map (forager-usca.pmtiles, z0-z15, with trail attributes) and
# BRouter routing data (.rd5), from Geofabrik's us and canada extracts merged into one. The owner's
# go of 2026-10-18 (Pi session, raspberrypi-composed-bunny): US and Canada, "Canadians are using
# the app too"; the Pi builds and keeps a copy and serves nothing (RECORD -879, -1276). The whole
# US means Alaska, Hawaii and Puerto Rico too (RECORD -1274, -1300). Geofabrik's us extract carries
# Alaska and Hawaii but not Puerto Rico or the US Virgin Islands, which are still to be added as
# regions of their own (pins.env, 2026-10-19). Run as the build user, by hand (`sudo -u forager-build ...`) or by
# forager-build-usca.service.
#
# Usage: build-usca.sh [--fetch-only] [--force] [--local YYMMDD] [--basemap FILE]
#   --fetch-only    download and check the extracts and sources, then stop
#   --force         build even if the extracts are the ones the current output was built from
#   --local YYMMDD  build from us-YYMMDD.osm.pbf and canada-YYMMDD.osm.pbf already in sources/osm,
#                   checked against their .md5 files and their headers, without asking Geofabrik
#                   which extract is current. Over the Pi's Bluetooth link the pair takes about a
#                   day to arrive, by which time Geofabrik has published a newer one, and the
#                   normal path would start downloading again.
#   --basemap FILE  use FILE, a forager-usca.pmtiles an earlier run built, instead of running
#                   Planetiler, and skip the full merge that only Planetiler reads. FILE's metadata
#                   must name the extracts' replication timestamp and sequence, or the run stops.
#                   It is hard-linked into the output, so it must be on the same filesystem as
#                   FB_SRV, and a failed run leaves it where it was. The first US + Canada run
#                   finished its 7h44m basemap and then failed at the routing step (run
#                   usca-20261019T011311Z); the owner's word on 2026-10-19 was to keep that
#                   basemap and move on, not build it again.
#
# A separate script from build-orwa.sh, which stays as it is (CLAUDE.md: new capability is a new
# path). Its own output, status and kept-failure directories, so neither script's `current`,
# status or failed run is the other's. What differs from build-orwa.sh, and why:
#   - The extracts are whole countries under north-america/, not states under us/.
#   - Downloads resume (curl -C -) and retry 20 times: 19 GB over Bluetooth will be interrupted.
#   - Planetiler is limited to Geofabrik's outlines of the two countries (--polygon). The us
#     extract's header box spans -180 to 180 because the Aleutians cross the antimeridian, so the
#     box alone would fill ocean tiles across the whole band.
#   - Planetiler's node map, features and multipolygon geometries are memory-mapped files, named
#     explicitly rather than left to defaults: the input is about 30 times Oregon + Washington's,
#     whose build already peaked at 5.9 of the Pi's 8 GB.
#   - Every .rd5 square is kept. build-orwa.sh dropped the squares outside the two states, which
#     held only ferry routes to Alaska; Alaska is in this build.
#   - Before publishing, sample points in each country and territory the owner named must have a
#     z15 tile with roads in it and a plausibly sized .rd5 square, so a build that silently lost
#     Alaska, Hawaii, Canada or, once it is a region, Puerto Rico is refused.
#   - A free-disk floor before the merge (USCA_MIN_FREE_GB): the build shares the root filesystem
#     with the rest of the Pi.
#
# Both outputs are published together or not at all. On any failure the previous output and the
# `current` link are left untouched, the run's log is kept, its scratch files are removed,
# status-usca.json says which stage failed, and the script exits non-zero.
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
}
# The latest failed run's outputs, kept for inspection, as build-orwa.sh does; its own directory.
FAILED_KEEP="$FB_SRV/failed-usca/latest"
keep_failed_run() { # exit-code
  [[ "$FAILED_KEEP" == "$FB_SRV/failed-usca/latest" && "$FAILED_KEEP" != "$OUT"* ]] || return 1
  if [[ -e "$FAILED_KEEP" ]]; then rm -rf -- "$FAILED_KEEP"; fi
  mkdir -p "$FAILED_KEEP"
  if [[ -d "$run/out" ]]; then mv "$run/out" "$FAILED_KEEP/out"; fi
  local f
  for f in metrics.jsonl versions.txt bounds.txt coverage.txt; do
    if [[ -f "$run/$f" ]]; then cp "$run/$f" "$FAILED_KEEP/"; fi
  done
  printf 'run %s failed at stage %s with exit %s at %s; not published, and never publishable from here\n' \
    "$run_id" "$stage" "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$FAILED_KEEP/FAILED.txt"
  cp "$log_file" "$FAILED_KEEP/"
}
on_exit() {
  local code=$?
  if [[ $code -ne 0 ]]; then
    "${RECORD[@]}" status "$STATUS" --state failed --stage "$stage" --run-id "$run_id" \
      ${osm_ts:+--osm-timestamp "$osm_ts"} --message "exit $code; log $log_file; outputs kept in $FAILED_KEEP" || true
    log "run failed with exit $code; previous outputs untouched; this run's outputs kept in $FAILED_KEEP"
    keep_failed_run "$code" || log "could not keep this run's outputs in $FAILED_KEEP"
    cleanup_scratch
  fi
}
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

curl_resume() { # url dest: resume into dest.part, then move into place
  if [[ -s "$2" && ! -e "$2.part" ]]; then mv "$2" "$2.part"; fi  # a partial file from an earlier run
  curl -fL --retry "$USCA_DOWNLOAD_RETRIES" --retry-all-errors --retry-delay 30 -C - \
    -A "$HTTP_USER_AGENT" -o "$2.part" "$1"
  mv "$2.part" "$2"
}

curl_get() { # url dest: download to dest.part with the response headers, then move into place
  curl -sSfL --retry 3 --retry-delay 10 -A "$HTTP_USER_AGENT" -D "$2.headers" -o "$2.part" "$1"
  mv "$2.part" "$2"
}

# --- 1. Which extracts, and are they new? ------------------------------------------------------
set_stage state
declare -A seq ts file
if [[ -n "$local_date" ]]; then
  for s in $USCA_REGIONS; do
    file[$s]="$s-$local_date.osm.pbf"
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
# timestamps must agree: both extracts are then cut from the same planet snapshot.
first="${USCA_REGIONS%% *}"
for s in $USCA_REGIONS; do
  [[ "${ts[$s]}" == "${ts[$first]}" ]] \
    || die "extracts are from different snapshots: $first ${ts[$first]}, $s ${ts[$s]}"
done
osm_ts="${ts[$first]}"
# The manifest's sequence names every region's, e.g. us:2415,canada:4945.
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
  log "--basemap: $basemap was built from $osm_ts, sequence ${seq[$first]}; Planetiler will not run"
fi

# --- 2. Extracts: download (resuming), check MD5, check the header timestamp -------------------
set_stage fetch-extracts
inputs=()
for s in $USCA_REGIONS; do
  f="$SRC/osm/${file[$s]}"
  if [[ -z "$local_date" ]]; then
    curl -sSf -A "$HTTP_USER_AGENT" -o "$f.md5" "$USCA_GEOFABRIK_BASE/${file[$s]}.md5"
    if [[ ! -s "$f" ]] || ! (cd "$SRC/osm" && md5sum -c --quiet "$f.md5" >/dev/null 2>&1); then
      log "downloading ${file[$s]}"
      curl_resume "$USCA_GEOFABRIK_BASE/${file[$s]}" "$f"
    fi
  fi
  (cd "$SRC/osm" && md5sum -c --quiet "$f.md5") || die "MD5 mismatch for ${file[$s]}"
  header_ts="$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$f")"
  [[ "$header_ts" == "$osm_ts" ]] || die "${file[$s]} header says $header_ts, expected $osm_ts"
  log "${file[$s]}: MD5 good, header timestamp $header_ts"
  inputs+=("$f")
done

# --- 3. The profile's other sources, and the two countries' outlines -----------------------------
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
  fetch_monthly "$USCA_GEOFABRIK_BASE/$s.poly" "$s.poly"
  polys+=("$SRC/aux/$s.poly")
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
free_gb="$(df --output=avail -BG "$FB_SRV" | tail -1 | tr -dc 0-9)"
[[ "$free_gb" -ge "$USCA_MIN_FREE_GB" ]] \
  || die "only ${free_gb} GB free under $FB_SRV; this build needs at least $USCA_MIN_FREE_GB GB (USCA_MIN_FREE_GB)"
log "${free_gb} GB free under $FB_SRV (floor $USCA_MIN_FREE_GB GB)"
merged="$run/$USCA_EXTRACT_NAME.osm.pbf"
# Only Planetiler reads the full merge (routing has its own, smaller one in section 6), so a run
# with --basemap skips it.
if [[ -z "$basemap" ]]; then
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
  rm -rf -- "$pt/tmp"  # tens of GB; the routing step needs the room
  rm -f -- "$merged"   # nothing after Planetiler reads it
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
rm -f -- "$routing_in"

set_stage brouter
br="$run/brouter"
profiles="$BROUTER_HOME/profiles2"
bjava=("$JAVA" "-Xmx$USCA_BROUTER_XMX" -cp "$BROUTER_HOME/brouter-$BROUTER_VERSION-all.jar")
mkdir -p "$run/out/segments"
bands=($USCA_ROUTING_BANDS)
for k in "${!bands[@]}"; do
  band="${bands[$k]}"; band_in="${band_files[$k]}"
  IFS=: read -r bw be <<<"$band"
  rm -rf -- "$br"
  mkdir -p "$br"/{nodetiles,waytiles,waytiles55,nodes55,unodes55,segments,no-elevation}
  # The three steps of BRouter's process_pbf_planet.sh at v1.7.10, as in build-orwa.sh: no
  # database pseudo-tags and no elevation data (RECORD -476).
  (cd "$br" && "${TIMED[@]}" "brouter-cut-$band" -- "${bjava[@]}" -Ddeletetmpfiles=true btools.mapcreator.OsmFastCutter \
    "$profiles/lookups.dat" nodetiles waytiles nodes55 waytiles55 bordernids.dat relations.dat restrictions.dat \
    "$profiles/all.brf" "$profiles/trekking.brf" "$profiles/softaccess.brf" "$band_in")
  (cd "$br" && "${TIMED[@]}" "brouter-unify-$band" -- "${bjava[@]}" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
    nodes55 unodes55 bordernids.dat bordernodes.dat no-elevation)
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
log "validated: archive; $(wc -l < "$run/coverage.txt") sample points covered; $(ls "$run/out/segments" | wc -l) .rd5 squares"
cat "$run/coverage.txt"

# --- 8. Manifest, then publish both together ---------------------------------------------------
set_stage publish
cp "$run/bounds.txt" "$run/coverage.txt" "$run/out/"
"${RECORD[@]}" manifest "$run/out/manifest.json" --pins "$here/pins.env" --run-dir "$run" \
  --osm-timestamp "$osm_ts" --osm-sequence "$osm_seq" --inputs "${inputs[@]}" --sources "${sources[@]}" \
  --outputs "$pmtiles_out" "$run/out/segments/"*.rd5
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

# Keep only this output's extracts among this script's regions; build-orwa.sh's are left alone.
for s in $USCA_REGIONS; do
  for f in "$SRC/osm/$s-"[0-9]*.osm.pbf; do
    [[ -e "$f" ]] || continue
    keep=0
    for i in "${inputs[@]}"; do [[ "$f" == "$i" ]] && keep=1; done
    [[ $keep -eq 1 ]] && continue
    if rm -f -- "$f" "$f.md5" "$f.headers"; then log "removed old extract $(basename "$f")"
    else warn remove-old-extracts "could not remove old extract $(basename "$f")"; fi
  done
done

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
