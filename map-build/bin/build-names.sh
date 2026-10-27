#!/usr/bin/env bash
# The names files (FNAM v1, one names1/<NAME>.fnm per .rd5 square) from raw extracts, outside the
# weekly build (dispatch 2026-10-26-pi-31): for a set of .rd5 squares already built, whose
# extracts are still on the card. The same two filters, export and encoder as build-usca.sh's
# names stage (its names_cut and names_encode), run on each raw extract and the cuts merged,
# where the stage cuts the build's merged routing input. USCA_ROUTING_FILTER keeps every highway
# way whole with its nodes, so the two give the same ways and nodes (shown on the USVI extract,
# pi-31).
#
# Usage: build-names.sh EXTRACT_DIR OSM_TIMESTAMP SQUARE_LIST_DIR OUT_DIR
#   EXTRACT_DIR      holds one <stem>-*.osm.pbf per region in USCA_REGIONS whose header's
#                    replication timestamp is OSM_TIMESTAMP (2026-10-24T20:20:51Z); read only
#   SQUARE_LIST_DIR  the .rd5 squares (a build's segments/), one .fnm each; read only
#   OUT_DIR          gets names1/, build-names.log, metrics.jsonl and names-summary.json; names1/
#                    must not exist yet
# Temporary files go in a directory of their own under FNAM_TMP_ROOT, the card's work directory
# (USCA_CARD_DIR/work) unless set, removed at the end, failed or not: the NVMe has little room
# above the weekly build's start check. Refuses to start while forager-build-usca.service is active.
set -euo pipefail
umask 022

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

[[ $# -eq 4 ]] || { sed -n '10,20p' "$0" >&2; exit 2; }
src="$1"; osm_ts="$2"; squares="$3"; out="$4"
TIMED=(python3 "$here/tools/peak_rss.py")
tmp_root="${FNAM_TMP_ROOT:-$USCA_CARD_DIR/work}"

die() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) FAILED: $*" >&2; exit 1; }
[[ "$osm_ts" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || die "OSM_TIMESTAMP must look like 2026-10-24T20:20:51Z: $osm_ts"
if systemctl is-active --quiet forager-build-usca.service 2>/dev/null; then die "forager-build-usca.service is active; not starting"; fi
[[ -d "$src" ]] || die "no extract directory $src"
compgen -G "$squares/*.rd5" >/dev/null || die "no .rd5 squares in $squares"
[[ ! -e "$out/names1" ]] || die "$out/names1 already exists"
if [[ "$tmp_root" == "$USCA_CARD_DIR"* ]]; then
  mountpoint -q -- "$USCA_CARD_MOUNT" || die "$USCA_CARD_MOUNT is not mounted; the temporary files go on the card"
fi
[[ -d "$tmp_root" && -w "$tmp_root" ]] || die "cannot write the temporary directory's parent $tmp_root"

# One extract per region, named by its stem as build-usca.sh names them, from this snapshot.
inputs=()
for r in $USCA_REGIONS; do
  stem="${r##*/}"; found=()
  for f in "$src/$stem-"[0-9]*.osm.pbf; do
    [[ -e "$f" ]] || continue
    [[ "$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$f")" == "$osm_ts" ]] && found+=("$f")
  done
  [[ ${#found[@]} -eq 1 ]] || die "region $r: ${#found[@]} extracts in $src from $osm_ts (${found[*]:-none}); need one"
  inputs+=("${found[0]}")
done

mkdir -p -- "$out"
log_file="$out/build-names.log"
exec > >(tee -a "$log_file") 2>&1
export FB_METRICS="$out/metrics.jsonl"
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
free_line() { df -B1 --output=target,avail -- / "$tmp_root" "$out" | tail -n +2 | awk '{ printf "%s %s bytes free; ", $1, $2 }'; }

tmp="$(mktemp -d "$tmp_root/names-XXXXXX")"
trap 'rm -rf -- "$tmp"' EXIT
log "build-names.sh: $osm_ts; $(ls "$squares"/*.rd5 | wc -l) .rd5 squares in $squares; inputs ${inputs[*]}; temporary files in $tmp"
log "disk before: $(free_line)"

# names_cut and names_encode, as build-usca.sh has them (test_build_names.sh compares the two).
eval "$(sed -n '/^names_cut() {/,/^}/p; /^names_encode() {/,/^}/p' "$here/bin/build-usca.sh")"
declare -F names_cut names_encode >/dev/null || die "cannot read names_cut and names_encode from build-usca.sh"

parts=()
for i in "${inputs[@]}"; do
  stem="$(basename "$i" .osm.pbf)"
  part="$tmp/$stem-names.osm.pbf"
  names_cut "$i" "$part" "-$stem" || die "could not cut the names from $i"
  parts+=("$part")
done
named="$tmp/names.osm.pbf"
"${TIMED[@]}" names-merge -- osmium merge "${parts[@]}" -o "$named" || die "could not merge the names cuts"
rm -f -- "${parts[@]}"
log "names input: $(osmium fileinfo -e -g data.count.nodes "$named") nodes, $(osmium fileinfo -e -g data.count.ways "$named") ways, $(stat -c %s "$named") bytes"
log "disk with the names input: $(free_line)"

names_encode "$named" "$out/names1" "$squares" "$osm_ts" "$out/names-summary.json" || die "the names encode failed"
log "names: $(cat "$out/names-summary.json")"
rm -f -- "$named"
check="$(python3 "$here/tools/fnam_build.py" check "$out/names1" --rd5-dir "$squares" --osm-timestamp "$osm_ts")" || die "$check"
log "$check"
log "disk after: $(free_line)"
log "done"
