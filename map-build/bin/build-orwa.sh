#!/usr/bin/env bash
# One build of Forager's Oregon + Washington map (forager-orwa.pmtiles, z0-z15, with trail
# attributes) and BRouter routing data (.rd5), from one merged Geofabrik extract. Dispatch
# 2026-09-28-495. Run as the build user, by hand (`sudo -u forager-build ...`) or by
# forager-build.service.
#
# Usage: build-orwa.sh [--fetch-only] [--force]
#   --fetch-only  download and check the extracts and sources, then stop (change 6)
#   --force       build even if the extract is the one the current output was built from
#
# Both outputs are published together or not at all. On any failure the previous output and the
# `current` link are left untouched, the run's log is kept, its scratch files are removed,
# status.json says which stage failed, and the script exits non-zero.
set -euo pipefail
umask 022

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

fetch_only=0; force=0
for arg in "$@"; do
  case "$arg" in
    --fetch-only) fetch_only=1 ;;
    --force) force=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

[[ "$(id -un)" == "$FB_USER" ]] || { echo "run as $FB_USER" >&2; exit 2; }

JAVA="$FB_OPT/jdk/bin/java"
BASEMAP_JAR="$FB_OPT/basemap/$BASEMAPS_JAR_NAME"
BROUTER_HOME="$FB_OPT/brouter"
SRC="$FB_SRV/sources"
OUT="$FB_SRV/out"
LOGS="$FB_SRV/logs"
STATUS="$FB_SRV/status.json"
RECORD=(python3 "$here/tools/run_record.py")
TIMED=(python3 "$here/tools/peak_rss.py")

run_id="$(date -u +%Y%m%dT%H%M%SZ)"
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
on_exit() {
  local code=$?
  if [[ $code -ne 0 ]]; then
    "${RECORD[@]}" status "$STATUS" --state failed --stage "$stage" --run-id "$run_id" \
      ${osm_ts:+--osm-timestamp "$osm_ts"} --message "exit $code; log $log_file" || true
    log "run failed with exit $code; previous outputs untouched; log kept at $log_file"
    cleanup_scratch
  fi
}
trap on_exit EXIT

curl_get() { # url dest: download to dest.part with the response headers, then move into place
  curl -sSfL --retry 3 --retry-delay 10 -A "$HTTP_USER_AGENT" -D "$2.headers" -o "$2.part" "$1"
  mv "$2.part" "$2"
}

# --- 1. Which extract is current, and is it new? ---------------------------------------------
set_stage state
declare -A seq ts file
for s in $EXTRACT_STATES; do
  state_txt="$(curl -sSf -A "$HTTP_USER_AGENT" "$GEOFABRIK_BASE/$s-updates/state.txt")"
  seq[$s]="$(sed -n 's/^sequenceNumber=//p' <<<"$state_txt")"
  ts[$s]="$(sed -n 's/^timestamp=//p' <<<"$state_txt" | tr -d '\\')"
  # The dated file -latest redirects to, so a mid-run publication cannot swap the file.
  file[$s]="$(curl -sSfI -A "$HTTP_USER_AGENT" "$GEOFABRIK_BASE/$s-latest.osm.pbf" \
    | sed -n 's/^[Ll]ocation: .*\/\([^/]*\.osm\.pbf\)\r\?$/\1/p')"
  [[ -n "${seq[$s]}" && -n "${ts[$s]}" && -n "${file[$s]}" ]] || die "could not read the current $s extract"
  log "$s: ${file[$s]}, sequence ${seq[$s]}, ${ts[$s]}"
done
first="${EXTRACT_STATES%% *}"
for s in $EXTRACT_STATES; do
  [[ "${seq[$s]}" == "${seq[$first]}" && "${ts[$s]}" == "${ts[$first]}" ]] \
    || die "extracts are from different snapshots: $first ${seq[$first]} ${ts[$first]}, $s ${seq[$s]} ${ts[$s]}"
done
osm_ts="${ts[$first]}"; osm_seq="${seq[$first]}"
if [[ $fetch_only -eq 0 && $force -eq 0 && -f "$OUT/current/manifest.json" ]]; then
  last_seq="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["osm"]["replication_sequence"])' \
    "$OUT/current/manifest.json")"
  if [[ "$last_seq" == "$osm_seq" ]]; then
    log "the current output is already built from sequence $osm_seq; nothing to do"
    "${RECORD[@]}" status "$STATUS" --state skipped --stage state --run-id "$run_id" --osm-timestamp "$osm_ts" \
      --message "no new extract"
    trap - EXIT; cleanup_scratch; exit 0
  fi
fi

# --- 2. Extracts: download, check MD5, check the header timestamp ----------------------------
set_stage fetch-extracts
inputs=()
for s in $EXTRACT_STATES; do
  f="$SRC/osm/${file[$s]}"
  if [[ ! -s "$f" ]]; then
    curl_get "$GEOFABRIK_BASE/${file[$s]}" "$f"
  fi
  curl -sSf -A "$HTTP_USER_AGENT" -o "$f.md5" "$GEOFABRIK_BASE/${file[$s]}.md5"
  (cd "$SRC/osm" && md5sum -c --quiet "$f.md5") || die "MD5 mismatch for ${file[$s]}"
  header_ts="$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$f")"
  [[ "$header_ts" == "$osm_ts" ]] || die "${file[$s]} header says $header_ts, state.txt says $osm_ts"
  log "${file[$s]}: MD5 good, header timestamp $header_ts"
  inputs+=("$f")
done

# --- 3. The profile's other sources -------------------------------------------------------------
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
sources=("$SRC/aux/natural_earth_vector.gpkg.zip" "$SRC/aux/daylight-landcover.gpkg" "$SRC/aux/qrank.csv.gz"
  "$SRC/aux/pgf-encoding.zip" "$SRC/aux/water-polygons-split-3857.zip" "$SRC/aux/land-polygons-split-3857.zip")
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

# --- 4. Merge into one extract, stamped with its own timestamp ---------------------------------
set_stage merge
merged="$run/$EXTRACT_NAME.osm.pbf"
# osmium merge 1.15.0 drops both the replication timestamp and the bounding boxes from the
# header (checked on synthetic files, 2026-10-04), so the timestamp is set here and the bounds
# are computed from the inputs and passed to Planetiler explicitly.
"${TIMED[@]}" merge -- osmium merge "${inputs[@]}" -o "$merged" \
  --output-header="osmosis_replication_timestamp=$osm_ts" \
  --output-header="osmosis_replication_sequence_number=$osm_seq"
[[ "$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$merged")" == "$osm_ts" ]] \
  || die "merged header lacks the replication timestamp"
bounds="$(python3 "$here/tools/union_bounds.py" "${inputs[@]}")" || die "an input has no header bounding box"
log "bounds from the inputs' headers: computed (not logged, to keep the log free of coordinates)"
echo "$bounds" > "$run/bounds.txt"

# --- 5. The map --------------------------------------------------------------------------------
set_stage planetiler
pt="$run/planetiler"
mkdir -p "$pt/data/sources" "$run/out"
for f in "${sources[@]}"; do ln -s "$f" "$pt/data/sources/$(basename "$f")"; done
ln -s "$merged" "$pt/data/sources/$EXTRACT_NAME.osm.pbf"
pmtiles_out="$run/out/$EXTRACT_NAME.pmtiles"
# No --download: every source is already in data/sources, so nothing is fetched out of sight.
(cd "$pt" && "${TIMED[@]}" planetiler -- "$JAVA" "-Xmx$PLANETILER_XMX" -jar "$BASEMAP_JAR" \
  --area="$EXTRACT_NAME" --bounds="$bounds" --maxzoom="$MAXZOOM" --output="$pmtiles_out" \
  --tmpdir="$pt/tmp" --http_user_agent="$HTTP_USER_AGENT")

# --- 6. The routing data -----------------------------------------------------------------------
set_stage brouter
br="$run/brouter"
mkdir -p "$br"/{nodetiles,waytiles,waytiles55,nodes55,unodes55,segments,no-elevation}
profiles="$BROUTER_HOME/profiles2"
bjava=("$JAVA" "-Xmx$BROUTER_XMX" -cp "$BROUTER_HOME/brouter-$BROUTER_VERSION-all.jar")
# The three steps of BRouter's process_pbf_planet.sh at v1.7.10, without the database pseudo-tags
# (optional, unused by hiking-mountain.brf) and with an empty elevation directory (the SRTM link
# is dead, RECORD -476): the .rd5 files carry no elevation.
(cd "$br" && "${TIMED[@]}" brouter-cut -- "${bjava[@]}" -Ddeletetmpfiles=true btools.mapcreator.OsmFastCutter \
  "$profiles/lookups.dat" nodetiles waytiles nodes55 waytiles55 bordernids.dat relations.dat restrictions.dat \
  "$profiles/all.brf" "$profiles/trekking.brf" "$profiles/softaccess.brf" "$merged")
(cd "$br" && "${TIMED[@]}" brouter-unify -- "${bjava[@]}" -Ddeletetmpfiles=true btools.mapcreator.PosUnifier \
  nodes55 unodes55 bordernids.dat bordernodes.dat no-elevation)
(cd "$br" && "${TIMED[@]}" brouter-link -- "${bjava[@]}" -DskipEncodingCheck=true btools.mapcreator.WayLinker \
  unodes55 waytiles55 bordernodes.dat restrictions.dat "$profiles/lookups.dat" "$profiles/all.brf" segments rd5)
mkdir -p "$run/out/segments"
mv "$br"/segments/*.rd5 "$run/out/segments/"
# .rd5 files carry no date of their own; stamp them with the extract's.
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
# Tile bounds can round outward by a little; anything beyond 0.01 degrees is not this extract.
if not (b[0] >= want[0] - 0.01 and b[1] >= want[1] - 0.01 and b[2] <= want[2] + 0.01 and b[3] <= want[3] + 0.01):
    problems.append("archive bounds extend beyond the extract's")
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
expected_rd5="W125_N40.rd5 W120_N40.rd5 W125_N45.rd5 W120_N45.rd5"
actual_rd5="$(cd "$run/out/segments" && ls -- *.rd5 | sort | tr '\n' ' ')"
[[ "$actual_rd5" == "$(tr ' ' '\n' <<<"$expected_rd5" | sort | tr '\n' ' ')" ]] \
  || die "rd5 files are [$actual_rd5], expected [$expected_rd5]"
for f in "$run/out/segments/"*.rd5; do
  [[ $(stat -c %s "$f") -gt 100000 ]] || die "$(basename "$f") is implausibly small"
done
log "validated: archive and four .rd5 files"

# --- 8. Manifest, then publish both together ---------------------------------------------------
set_stage publish
cp "$run/bounds.txt" "$run/out/bounds.txt"
"${RECORD[@]}" manifest "$run/out/manifest.json" --pins "$here/pins.env" --run-dir "$run" \
  --osm-timestamp "$osm_ts" --osm-sequence "$osm_seq" --inputs "${inputs[@]}" --sources "${sources[@]}" \
  --outputs "$pmtiles_out" "$run/out/segments/"*.rd5
name="$(date -u -d "$osm_ts" +%Y%m%dT%H%M%SZ)"
[[ -e "$OUT/$name" ]] && name="$name-$run_id"
mv "$run/out" "$OUT/$name"
ln -sfn "$name" "$OUT/.current.new"
mv -T "$OUT/.current.new" "$OUT/current"
log "published $OUT/$name; current -> $name"

# Keep the newest KEEP_GOOD_OUTPUTS outputs; never the one current points at.
current_target="$(readlink "$OUT/current")"
mapfile -t olds < <(cd "$OUT" && ls -1d [0-9]*T*Z* 2>/dev/null | sort -r | tail -n +"$((KEEP_GOOD_OUTPUTS + 1))")
for d in "${olds[@]}"; do
  [[ "$d" != "$current_target" && -d "$OUT/$d" ]] && rm -rf -- "${OUT:?}/$d" && log "removed old output $d"
done

# Extracts are dated, so each week adds a pair; keep only the pair this output was built from.
for f in "$SRC/osm/"*.osm.pbf; do
  keep=0
  for i in "${inputs[@]}"; do [[ "$f" == "$i" ]] && keep=1; done
  if [[ $keep -eq 0 ]]; then rm -f -- "$f" "$f.md5" "$f.headers"; log "removed old extract $(basename "$f")"; fi
done

"${RECORD[@]}" status "$STATUS" --state ok --stage done --run-id "$run_id" --osm-timestamp "$osm_ts" \
  --output "$OUT/$name"
trap - EXIT
cleanup_scratch
log "done"
