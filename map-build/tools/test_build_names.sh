#!/usr/bin/env bash
# Tests for bin/build-names.sh and build-usca.sh's names_cut and names_encode (dispatch pi-31):
# the real scripts, copied into a temporary tree with pins.env pointed at it, run on tiny extracts
# made here with the real osmium and the real encoder. systemctl and mountpoint are stood in for.
# Nothing touches /srv/forager-build, /mnt/archive or /opt.
#
# The one-off script cuts each raw extract and merges the cuts; the build's stage cuts the merged
# routing input (USCA_ROUTING_FILTER). Both are run here on the same extracts, and their .fnm
# files must be the same bytes.
#
# Run: bash map-build/tools/test_build_names.sh   (exits non-zero on the first failure)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf -- "$T"' EXIT
command -v osmium >/dev/null || { echo "FAIL: osmium is needed"; exit 1; }

pass=0
check() { if eval "$2"; then pass=$((pass + 1)); else echo "FAIL: $1"; exit 1; fi; }

# --- The tree -------------------------------------------------------------------------------------
mkdir -p "$T/tree/map-build/bin" "$T/tree/map-build/tools" "$T/card/forager-build/work" "$T/stub"
cp "$here/bin/build-usca.sh" "$here/bin/build-names.sh" "$T/tree/map-build/bin/"
cp "$here/tools/"*.py "$here/tools/fnam_export.json" "$T/tree/map-build/tools/"
{
  cat "$here/pins.env"
  echo "# --- test_build_names.sh ---"
  echo 'USCA_REGIONS="us canada"'
  echo "USCA_CARD_MOUNT=$T/card"
  echo "USCA_CARD_DIR=$T/card/forager-build"
} > "$T/tree/map-build/pins.env"
cat > "$T/stub/systemctl" <<'EOF'
#!/usr/bin/env bash
[[ "${STUB_BUILD_ACTIVE:-0}" == 1 ]]
EOF
cat > "$T/stub/mountpoint" <<'EOF'
#!/usr/bin/env bash
[[ "${!#}" == "$STUB_T/card" ]]
EOF
chmod +x "$T/stub/"*
export PATH="$T/stub:$PATH" STUB_T="$T"
NAMES="$T/tree/map-build/bin/build-names.sh"

# --- Tiny extracts: us and canada, one snapshot -----------------------------------------------------
# us: a named path across a cell edge and a square edge (W80/W75 at lon -75), a ref-only road, an
# unnamed path, a named railway, a named proposed road, a named closed footway; a route relation
# whose member way has no highway tag. canada: a named track, and the border road again (both
# extracts carry it, as Geofabrik's do), so the merges have something to remove.
TS=2026-10-17T20:20:51Z
cat > "$T/us.opl" <<'EOF'
n1 v1 x-75.10 y42.01
n2 v1 x-74.90 y42.03
n3 v1 x-74.80 y42.20
n4 v1 x-122.30 y47.60
n5 v1 x-122.20 y47.62
n6 v1 x-122.31 y47.61
n7 v1 x-122.32 y47.63
n8 v1 x-122.40 y47.70
n9 v1 x-122.41 y47.71
n10 v1 x-122.50 y47.50
n11 v1 x-122.51 y47.51
n12 v1 x-122.10 y47.40
n13 v1 x-122.09 y47.40
n14 v1 x-122.09 y47.41
n15 v1 x-122.60 y47.30
n16 v1 x-122.61 y47.31
n20 v1 x-79.40 y43.60
n21 v1 x-79.30 y43.61
w1 v1 Thighway=path,name=Long%20%Trail Nn1,n2,n3
w2 v1 Thighway=primary,ref=SR%20%520 Nn4,n5
w3 v1 Thighway=path Nn6,n7
w4 v1 Trailway=rail,name=Main%20%Line Nn8,n9
w5 v1 Thighway=proposed,name=Future%20%Rd Nn10,n11
w6 v1 Thighway=footway,name=Loop Nn12,n13,n14,n12
w7 v1 Tnatural=coastline,name=Shore Nn15,n16
w20 v1 Thighway=secondary,name=Border%20%Rd Nn20,n21
r1 v1 Ttype=route,route=hiking,name=Shore%20%Walk Mw7@
EOF
cat > "$T/canada.opl" <<'EOF'
n20 v1 x-79.40 y43.60
n21 v1 x-79.30 y43.61
n30 v1 x-79.38 y43.65
n31 v1 x-79.39 y43.66
n32 v1 x-79.37 y43.70
w20 v1 Thighway=secondary,name=Border%20%Rd Nn20,n21
w30 v1 Thighway=track,name=Chemin%20%des%20%Érables Nn30,n31,n32
EOF
mkdir -p "$T/src" "$T/rd5"
for s in us canada; do
  osmium cat "$T/$s.opl" -o "$T/src/$s-261017.osm.pbf" --output-header=osmosis_replication_timestamp=$TS
done
# An extract of another snapshot beside them, which must be passed over.
osmium cat "$T/us.opl" -o "$T/src/us-261010.osm.pbf" --output-header=osmosis_replication_timestamp=2026-10-10T20:20:51Z
for n in W125_N45 W80_N40 W75_N40 W90_N30; do : > "$T/rd5/$n.rd5"; done

# --- The one-off script ---------------------------------------------------------------------------
"$NAMES" "$T/src" "$TS" "$T/rd5" "$T/out" > "$T/run.txt" 2>&1 || { cat "$T/run.txt"; echo "FAIL: build-names.sh failed"; exit 1; }
L="$T/out/build-names.log"
check "one .fnm per .rd5" '[[ "$(cd "$T/out/names1" && ls | tr "\n" " ")" == "W125_N45.fnm W75_N40.fnm W80_N40.fnm W90_N30.fnm " ]]'
check "the check line names the count" 'grep -q "names check: 4 .fnm files, one per .rd5; " "$L"'
check "the log names the two inputs of this snapshot, not the other" \
  'grep -q "inputs $T/src/us-261017.osm.pbf $T/src/canada-261017.osm.pbf;" "$L" && ! grep -q "us-261010" "$L"'
check "the log has disk lines before and after" 'grep -q "disk before: /" "$L" && grep -q "disk after: /" "$L"'
check "metrics name each region's two filters, the merge and the encode" \
  '[[ "$(python3 -c "import json,sys; print(\" \".join(json.loads(l)[\"label\"] for l in open(sys.argv[1])))" "$T/out/metrics.jsonl")" == "names-filter-highway-us-261017 names-filter-named-us-261017 names-filter-highway-canada-261017 names-filter-named-canada-261017 names-merge names-encode" ]]'
check "the temporary directory is gone" '[[ -z "$(ls -A "$T/card/forager-build/work")" ]]'
check "the files carry the extract's date" '[[ "$(date -u -r "$T/out/names1/W75_N40.fnm" +%Y-%m-%dT%H:%M:%SZ)" == "$TS" ]]'
summary="$T/out/names-summary.json"
field() { python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(eval(sys.argv[2], {}, d))' "$summary" "$1"; }
# Kept: Long Trail, SR 520, Loop, Border Rd (once, after the merge), Chemin des Érables. Not the
# unnamed path, the railway, the proposed road or the coastline.
check "five ways read, the merge having removed the border road's copy" '[[ "$(field ways)" == 5 ]]'
check "the proposed road is dropped by the encoder" '[[ "$(field dropped_highway)" == 1 ]]'
names_of() { python3 - "$1" <<'EOF'
import struct, sys
d = open(sys.argv[1], "rb").read()
toff, doff = struct.unpack_from("<II", d, 20)
pos = toff
def v():
    global pos
    n = s = 0
    while True:
        b = d[pos]; pos += 1; n |= (b & 0x7F) << s; s += 7
        if b < 0x80: return n
out = []
for _ in range(v()):
    n = v(); out.append(d[pos:pos + n].decode()); pos += n
print("|".join(out))
EOF
}
check "W125_N45 holds the ref-only road and the closed footway" '[[ "$(names_of "$T/out/names1/W125_N45.fnm")" == "SR 520|Loop" ]]'
check "the trail across lon -75 is in both W80_N40 and W75_N40" \
  '[[ "$(names_of "$T/out/names1/W80_N40.fnm")" == "Long Trail|Border Rd|Chemin des Érables" && "$(names_of "$T/out/names1/W75_N40.fnm")" == "Long Trail" ]]'
check "a square with nothing in it still has a file" '[[ "$(names_of "$T/out/names1/W90_N30.fnm")" == "" ]]'

# --- The build's stage on the same extracts: same bytes -------------------------------------------
# As build-usca.sh section 6: USCA_ROUTING_FILTER on each extract, the cuts merged, then the
# stage's names_cut and names_encode, taken from the script itself.
(
  set -euo pipefail
  # shellcheck source=/dev/null
  source "$T/tree/map-build/pins.env"
  here="$T/tree/map-build"; TIMED=(python3 "$here/tools/peak_rss.py"); export FB_METRICS="$T/stage-metrics.jsonl"
  eval "$(sed -n '/^names_cut() {/,/^}/p; /^names_encode() {/,/^}/p' "$here/bin/build-usca.sh")"
  read -r -a rf <<<"$USCA_ROUTING_FILTER"
  mkdir -p "$T/stage"
  for s in us canada; do osmium tags-filter "$T/src/$s-261017.osm.pbf" "${rf[@]}" -o "$T/stage/routing-$s.osm.pbf"; done
  osmium merge "$T/stage/routing-us.osm.pbf" "$T/stage/routing-canada.osm.pbf" -o "$T/stage/routing.osm.pbf"
  names_cut "$T/stage/routing.osm.pbf" "$T/stage/names.osm.pbf"
  names_encode "$T/stage/names.osm.pbf" "$T/stage/names1" "$T/rd5" "$TS" "$T/stage/summary.json"
) > "$T/stage.txt" 2>&1 || { cat "$T/stage.txt"; echo "FAIL: the stage's functions failed"; exit 1; }
check "the stage's cut keeps the six named highway ways and not the route relation's member (no highway tag)" \
  '[[ "$(osmium fileinfo -e -g data.count.ways "$T/stage/names.osm.pbf")" == 6 ]]'
check "the highway cut's size is logged, per region and in the stage (Sorrel's review S2)" \
  'grep -qE "names highway cut-us-261017: [0-9]+ bytes" "$L" && grep -qE "^names highway cut: [0-9]+ bytes" "$T/stage.txt"'
check "the stage's highway cut is removed" '[[ ! -e "$T/stage/names.osm.pbf.highway.osm.pbf" ]]'
check "the stage and the one-off script write the same bytes" 'diff -r "$T/stage/names1" "$T/out/names1" >/dev/null'
check "the stage's metrics name its steps without a region" \
  '[[ "$(python3 -c "import json,sys; print(\" \".join(json.loads(l)[\"label\"] for l in open(sys.argv[1])))" "$T/stage-metrics.jsonl")" == "names-filter-highway names-filter-named names-encode" ]]'

# --- Refusals -------------------------------------------------------------------------------------
rc=0; out="$("$NAMES" "$T/src" "$TS" "$T/rd5" "$T/out" 2>&1)" || rc=$?
check "refuses when names1 already exists" '[[ $rc -ne 0 && "$out" == *"names1 already exists"* ]]'
rc=0; out="$(STUB_BUILD_ACTIVE=1 "$NAMES" "$T/src" "$TS" "$T/rd5" "$T/out2" 2>&1)" || rc=$?
check "refuses while the weekly build is active" '[[ $rc -ne 0 && "$out" == *"forager-build-usca.service is active"* && ! -e "$T/out2" ]]'
rc=0; out="$("$NAMES" "$T/src" 2026-10-24T20:20:51Z "$T/rd5" "$T/out3" 2>&1)" || rc=$?
check "refuses a snapshot with no extracts, naming the region" '[[ $rc -ne 0 && "$out" == *"region us: 0 extracts"* ]]'
rc=0; out="$("$NAMES" "$T/src" "$TS" "$T/src" "$T/out4" 2>&1)" || rc=$?
check "refuses a square list with no .rd5" '[[ $rc -ne 0 && "$out" == *"no .rd5 squares"* ]]'
# A failed encode (a square name the encoder refuses) still removes the temporary directory.
: > "$T/rd5/bogus.rd5"
rc=0; "$NAMES" "$T/src" "$TS" "$T/rd5" "$T/out5" > "$T/run5.txt" 2>&1 || rc=$?
check "a failed encode fails the run and leaves no temporary directory" \
  '[[ $rc -ne 0 && -z "$(ls -A "$T/card/forager-build/work")" ]] && grep -q "FAILED: the names encode failed" "$T/run5.txt"'
rm -f "$T/rd5/bogus.rd5"
# The node limit (Sorrel's review S1): an input over it stops after the cut, before the encode.
cp "$T/tree/map-build/pins.env" "$T/pins.env.saved"
sed -i 's/^USCA_NAMES_MAX_INPUT_NODES=.*/USCA_NAMES_MAX_INPUT_NODES=3/' "$T/tree/map-build/pins.env"
rc=0; "$NAMES" "$T/src" "$TS" "$T/rd5" "$T/out6" > "$T/run6.txt" 2>&1 || rc=$?
cp "$T/pins.env.saved" "$T/tree/map-build/pins.env"
check "an input over USCA_NAMES_MAX_INPUT_NODES fails before the encode, naming the count and the limit" \
  '[[ $rc -ne 0 && ! -e "$T/out6/names1" ]] && grep -qE "names input has [0-9]+ nodes, over USCA_NAMES_MAX_INPUT_NODES \(3\)" "$T/run6.txt"'

echo "test_build_names.sh: $pass checks passed"
