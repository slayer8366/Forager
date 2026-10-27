#!/usr/bin/env bash
# End-to-end tests of build-usca.sh's disk layout (dispatch 2026-10-20-pi-09, parts B, C, D and G):
# the real script, copied unchanged into a temporary tree, run from its own entry point with its
# own arguments. Only the outside world is stood in for: Geofabrik (a `curl` on PATH that serves
# tiny extracts), the two disks' free space (`df`), the card's mount (`mountpoint`), name lookup
# (`getent`), Java (Planetiler and BRouter) and `pmtiles`. osmium and the Python tools are the real
# ones, run on tiny OSM files made here. Both "disks" are directories under one temporary
# directory; nothing touches /srv/forager-build, /mnt/archive or /opt.
#
# Each run stops itself where the check needs it to (BRouter's first step fails on purpose), so
# the publish stage is not reached here; the old-extract cleanup it calls is tested on its own in
# test_build_usca_disk.sh.
#
# Puerto Rico and the USVI (dispatch 2026-10-20-pi-14) are nested regions at Geofabrik,
# us/puerto-rico and us/us-virgin-islands, so the stand-in Geofabrik serves files by their URL path
# and answers 404 for any other, as the real one does for a flat island URL (observed 2026-10-20).
# R1 to R5 run with USCA_REGIONS="us canada", as they did before the islands, so they stay the
# record that us and canada behave as before; R6 to R8 run the four regions.
#
# Run: bash map-build/tools/test_build_usca_run.sh   (runs every check; exits non-zero on a failure)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"
trap '[[ -n "${KEEP_T:-}" ]] || rm -rf -- "$T"' EXIT
ME="$(id -un)"
command -v osmium >/dev/null || { echo "FAIL: osmium is needed"; exit 1; }

# --- The tree: the script under test, its tools, and pins.env with this test's paths -----------
mkdir -p "$T/bef"; echo one > "$T/bef/srtm_12_03.bef"; echo two > "$T/bef/srtm_17_04.bef"
(cd "$T/bef" && sha256sum *.bef > SHA256SUMS)
mkdir -p "$T/tree/map-build/bin" "$T/tree/map-build/tools"
cp "$here/bin/build-usca.sh" "$T/tree/map-build/bin/"
cp "$here/tools/"*.py "$T/tree/map-build/tools/"
cmp -s "$here/bin/build-usca.sh" "$T/tree/map-build/bin/build-usca.sh" || { echo "FAIL: copy differs"; exit 1; }
{
  cat "$here/pins.env"
  echo "# --- test_build_usca_run.sh: this test's paths ---"
  echo "FB_OPT=$T/opt"
  echo "FB_USER=$ME"
  echo "USCA_CARD_MOUNT=$T/card"
  echo "USCA_CARD_DIR=$T/card/forager-build"
  echo "USCA_DOWNLOAD_PAUSE=0"
  echo "USCA_NETWORK_WAIT_SECONDS=0"
  echo "USCA_ROUTE_FILE=$T/route"
  echo 'FB_SRV=$STUB_FB_SRV'
  echo '[[ -z "${STUB_USCA_REGIONS:-}" ]] || USCA_REGIONS="$STUB_USCA_REGIONS"'
  # A two-file elevation store (G8), pinned as the real one is; STUB_BEF_DIR points a run elsewhere.
  echo "USCA_BEF_DIR=\${STUB_BEF_DIR:-$T/bef}"
  echo "USCA_BEF_SUMS_SHA256=$(cd "$T/bef" && sha256sum < SHA256SUMS | cut -d' ' -f1)"
  echo "USCA_BEF_FILES=2"
} > "$T/tree/map-build/pins.env"
source "$here/pins.env"   # for the jar names
mkdir -p "$T/opt/jdk/bin" "$T/opt/basemap" "$T/opt/brouter/profiles2"
echo jar > "$T/opt/basemap/$BASEMAPS_JAR_NAME"; echo jar > "$T/opt/brouter/brouter-$BROUTER_VERSION-all.jar"
CARD_DIR="$T/card/forager-build"
# The routing table curl_resume reads before each connection (pi-11): Wi-Fi carries the default
# route, with the tether up behind it at a higher metric, as on the Pi on 2026-10-20 (observed).
printf 'Iface\tDestination\tGateway\tFlags\tRefCnt\tUse\tMetric\tMask\nwlan0\t00000000\t0100000A\t0003\t0\t0\t600\t00000000\nbnep0\t00000000\t0100000B\t0003\t0\t0\t750\t00000000\n' > "$T/route"

# --- Tiny extracts, as Geofabrik's would be: header timestamp, sequence and bounding box ---------
TS=2026-10-17T20:20:51Z
mkdir -p "$T/geofabrik"
printf 'n1 v1 x-122.33 y47.60\nn2 v1 x-122.34 y47.61\nw1 v1 Thighway=path Nn1,n2\n' > "$T/us.opl"
printf 'n11 v1 x-79.38 y43.65\nn12 v1 x-79.39 y43.66\nw11 v1 Thighway=track Nn11,n12\n' > "$T/canada.opl"
osmium extract -b -125,45,-120,50 --set-bounds "$T/us.opl" -o "$T/geofabrik/us-261017.osm.pbf" \
  --output-header=osmosis_replication_timestamp=$TS --output-header=osmosis_replication_sequence_number=2415
osmium extract -b -80,43,-79,44 --set-bounds "$T/canada.opl" -o "$T/geofabrik/canada-261017.osm.pbf" \
  --output-header=osmosis_replication_timestamp=$TS --output-header=osmosis_replication_sequence_number=4945
# The islands' nodes sit on the two coverage points; their boxes are whole degrees around them.
printf 'n21 v1 x-66.1057 y18.4655\nn22 v1 x-66.1057 y18.4655\nw21 v1 Thighway=residential Nn21,n22\n' > "$T/puerto-rico.opl"
printf 'n31 v1 x-64.9307 y18.3419\nn32 v1 x-64.9307 y18.3419\nw31 v1 Thighway=residential Nn31,n32\n' > "$T/us-virgin-islands.opl"
osmium extract -b -67,18,-66,19 --set-bounds "$T/puerto-rico.opl" -o "$T/geofabrik/puerto-rico-261017.osm.pbf" \
  --output-header=osmosis_replication_timestamp=$TS --output-header=osmosis_replication_sequence_number=3282
osmium extract -b -65,18,-64,19 --set-bounds "$T/us-virgin-islands.opl" -o "$T/geofabrik/us-virgin-islands-261017.osm.pbf" \
  --output-header=osmosis_replication_timestamp=$TS --output-header=osmosis_replication_sequence_number=1791
for s in us canada puerto-rico us-virgin-islands; do (cd "$T/geofabrik" && md5sum "$s-261017.osm.pbf" > "$s-261017.osm.pbf.md5"); done
# What the stand-in serves, laid out by URL path under download.geofabrik.de as Geofabrik lays it
# out: each region's dated file, .md5, state.txt and .poly under its path's directory. Each .md5
# names the bare file, as Geofabrik's do (read 2026-10-20).
W="$T/served/north-america"
mkdir -p "$W/us"
for r in us:2415 canada:4945 us/puerto-rico:3282 us/us-virgin-islands:1791; do
  path="${r%%:*}"; st="${path##*/}"; d="$W"; [[ "$path" == */* ]] && d="$W/${path%/*}"
  cp "$T/geofabrik/$st-261017.osm.pbf" "$T/geofabrik/$st-261017.osm.pbf.md5" "$d/"
  mkdir -p "$W/$path-updates"
  printf 'sequenceNumber=%s\ntimestamp=2026-10-17T20\\:20\\:51Z\n' "${r##*:}" > "$W/$path-updates/state.txt"
done
printf 'us\n1\n  -125 45\n  -120 45\n  -120 50\n  -125 50\nEND\nEND\n' > "$W/us.poly"
printf 'canada\n1\n  -80 43\n  -79 43\n  -79 44\n  -80 44\nEND\nEND\n' > "$W/canada.poly"
printf 'puerto-rico\n1\n  -67 18\n  -66 18\n  -66 19\n  -67 19\nEND\nEND\n' > "$W/us/puerto-rico.poly"
printf 'us-virgin-islands\n1\n  -65 18\n  -64 18\n  -64 19\n  -65 19\nEND\nEND\n' > "$W/us/us-virgin-islands.poly"

# --- Stand-ins on PATH ----------------------------------------------------------------------------
mkdir -p "$T/stub"
cat > "$T/stub/df" <<'EOF'
#!/usr/bin/env bash
# df -B1 --output=avail DIR, from STUB_NVME_FREE and STUB_CARD_FREE
d="${!#}"; echo "    Avail"
if [[ "$d" == "$STUB_T/card"* ]]; then echo "${STUB_CARD_FREE:-500000000000}"; else echo "${STUB_NVME_FREE:-500000000000}"; fi
EOF
cat > "$T/stub/mountpoint" <<'EOF'
#!/usr/bin/env bash
[[ "${STUB_CARD_MOUNTED:-1}" == 1 && "${!#}" == "$STUB_T/card" ]]
EOF
cat > "$T/stub/getent" <<'EOF'
#!/usr/bin/env bash
echo "getent $*" >> "$STUB_T/calls.log"
[[ "${STUB_RESOLVES:-1}" == 1 ]] && echo "192.0.2.1 $2"
EOF
cat > "$T/stub/pmtiles" <<'EOF'
#!/usr/bin/env bash
case "$1 $2" in
  "show --metadata") printf '{"planetiler:osm:osmosisreplicationtime": "2026-10-17T20:20:51Z", "planetiler:osm:osmosisreplicationseq": "2415"}\n' ;;
  *) echo "pmtiles stub" ;;
esac
EOF
cat > "$T/stub/curl" <<'EOF'
#!/usr/bin/env bash
# Geofabrik, as far as build-usca.sh asks it: state.txt, a HEAD of -latest (a redirect to the
# dated file), a HEAD of the dated file (its Content-Length), the .md5, the .poly and the extract,
# resumed from the -o file's size with -C -. Each is served from $STUB_T/served by its URL path, and
# a path with nothing there is a 404 (curl -f's exit 22), as at Geofabrik. With STUB_SLOW_FIRST=1
# each extract's first connection sends half and ends as a speed-limit abort does (exit 28).
# calls.log keeps the file names, as before the islands; urls.log has every URL whole.
head=0; out=""; resume=0; url=""; prev=""; sl=""; st=""
for a in "$@"; do
  case "$a" in -I|-sSfI|-sSIL|-sSfIL) head=1 ;; esac
  [[ "$prev" == -o ]] && out="$a"
  [[ "$prev" == -C && "$a" == - ]] && resume=1
  [[ "$prev" == --speed-limit ]] && sl="$a"
  [[ "$prev" == --speed-time ]] && st="$a"
  prev="$a"; url="$a"
done
name="${url##*/}"; rel="${url#https://download.geofabrik.de/}"; F="$STUB_T/served/$rel"
off=0; [[ $resume -eq 1 && -n "$out" && -e "$out" ]] && off=$(stat -c %s "$out")
echo "curl $([[ $head -eq 1 ]] && echo HEAD || echo GET) $name offset=$off" >> "$STUB_T/calls.log"
echo "$([[ $head -eq 1 ]] && echo HEAD || echo GET) $url" >> "$STUB_T/urls.log"
[[ -z "$sl$st" ]] || echo "$name speed-limit=$sl speed-time=$st" >> "$STUB_T/speed.log"
not_found() { echo "curl: (22) The requested URL returned error: 404" >&2; exit 22; }
if [[ $head -eq 1 && "$name" == *-latest.osm.pbf ]]; then
  dated="${rel%-latest.osm.pbf}-261017.osm.pbf"
  [[ -f "$STUB_T/served/$dated" ]] || not_found
  printf 'HTTP/1.1 302 Found\r\nLocation: https://download.geofabrik.de/%s\r\n\r\n' "$dated"; exit 0
fi
[[ -f "$F" ]] || not_found
case "$head:$name" in
  1:*) printf 'HTTP/1.1 200 OK\r\nContent-Length: %s\r\n\r\n' "$(stat -c %s "$F")" ;;
  0:*.osm.pbf) size=$(stat -c %s "$F")
    if [[ "${STUB_SLOW_FIRST:-0}" == 1 && $off -eq 0 ]]; then
      head -c $(( size / 2 )) "$F" >> "$out"; echo "curl: (28) Operation too slow" >&2; exit 28
    fi
    tail -c +$(( off + 1 )) "$F" >> "$out" ;;
  0:*) if [[ -n "$out" ]]; then cp "$F" "$out"; else cat "$F"; fi ;;
esac
EOF
cat > "$T/opt/jdk/bin/java" <<'EOF'
#!/usr/bin/env bash
# Planetiler (-jar) records where the merged extract it reads is, and what is in the card's work
# directory; BRouter's first step records the card's work directory again, then fails, so the run
# ends there and cleanup_scratch runs.
C="$STUB_T/card/forager-build/work"
case " $* " in
  *" -version "*) echo 'openjdk version "stub"' ;;
  *" -jar "*) m="$(readlink -f data/sources/forager-usca.osm.pbf)"
    { echo "merged=$m"; echo "merged_bytes=$(stat -c %s "$m" 2>/dev/null || echo missing)"
      echo "merged_nodes=$(osmium fileinfo -e -g data.count.nodes "$m" 2>/dev/null || echo missing)"
      echo "card_work=$(ls -A "$C" 2>/dev/null | tr '\n' ' ')"; } > "$STUB_T/planetiler.rec"
    for a in "$@"; do if [[ "$a" == --output=* ]]; then echo archive > "${a#--output=}"; fi; done
    for a in "$@"; do if [[ "$a" == --polygon=* ]]; then cp "${a#--polygon=}" "$STUB_T/polygon.rec"; fi; done ;;
  *" btools.mapcreator.OsmFastCutter "*)
    { if [[ -e "$C" ]]; then echo "card_work_exists=yes"; echo "card_work=$(find "$C" -mindepth 1 | tr '\n' ' ')"
      else echo "card_work_exists=no"; fi; } > "$STUB_T/cut.rec"
    # STUB_BROUTER=go goes on to PosUnifier; STUB_DROP_STORE moves the store away here, after
    # the brouter stage's check, as a card dropping out mid-stage would (R10).
    [[ "${STUB_BROUTER:-}" == go ]] || exit 1
    if [[ -n "${STUB_DROP_STORE:-}" ]]; then mv "$STUB_DROP_STORE" "$STUB_DROP_STORE.gone"; fi ;;
  *" btools.mapcreator.PosUnifier "*)
    # As BRouter 1.7.10 does: <SQUARE>.u5d_3 when it read a .bef, <SQUARE>.u5d when it found none.
    # W80_N25 is one of the pinned squares without a .bef.
    dir="${*: -1}"
    if compgen -G "$dir/*.bef" >/dev/null; then : > unodes55/W125_N45.u5d_3; else : > unodes55/W125_N45.u5d; fi
    : > unodes55/W80_N25.u5d ;;
  *" btools.mapcreator.WayLinker "*)
    echo "linked" > "$STUB_T/link.rec"; exit 1 ;;
  *) echo "java stub: unexpected $*" >&2; exit 1 ;;
esac
EOF
chmod 755 "$T/stub/"* "$T/opt/jdk/bin/java"

fails=0; pass=0
check() { if eval "$2"; then pass=$((pass + 1)); else echo "FAIL: $1"; fails=$((fails + 1)); fi; }
status_field() { python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get(sys.argv[2]) or "")' "$1" "$2" 2>/dev/null; }

REGIONS="us canada"   # USCA_REGIONS for the runs, until R6
new_srv() { # name [local]: a fresh FB_SRV with its sources; "local" also puts REGIONS' 261017 files on the card
  # us.poly and canada.poly are put in sources/aux, as before, unless NO_POLYS=1; the islands' are
  # always left for the run to fetch.
  S="$T/srv-$1"; rm -rf -- "$S" "$CARD_DIR" "$T/calls.log" "$T/urls.log" "$T/planetiler.rec" "$T/cut.rec" "$T/speed.log" "$T/polygon.rec"
  mkdir -p "$S/sources/osm" "$S/sources/aux"
  for f in natural_earth_vector.gpkg.zip daylight-landcover.gpkg qrank.csv.gz pgf-encoding.zip \
    water-polygons-split-3857.zip land-polygons-split-3857.zip; do echo x > "$S/sources/aux/$f"; done
  if [[ "${NO_POLYS:-0}" != 1 ]]; then
    printf 'us\n1\n  -125 45\n  -120 45\n  -120 50\n  -125 50\nEND\nEND\n' > "$S/sources/aux/us.poly"
    printf 'canada\n1\n  -80 43\n  -79 43\n  -79 44\n  -80 44\nEND\nEND\n' > "$S/sources/aux/canada.poly"
  fi
  if [[ "${2:-}" == local ]]; then
    mkdir -p "$CARD_DIR/sources-osm"
    for s in $REGIONS; do s="${s##*/}"
      cp "$T/geofabrik/$s-261017.osm.pbf" "$CARD_DIR/sources-osm/"
      ln -s "$CARD_DIR/sources-osm/$s-261017.osm.pbf" "$S/sources/osm/$s-261017.osm.pbf"
      cp "$T/geofabrik/$s-261017.osm.pbf.md5" "$S/sources/osm/"
    done
  fi
  : > "$T/calls.log"; : > "$T/urls.log"
}
build() { # args...: runs the copied script; sets rc and L, the run's log
  rc=0
  env PATH="$T/stub:$PATH" STUB_T="$T" STUB_FB_SRV="$S" STUB_USCA_REGIONS="$REGIONS" bash "$T/tree/map-build/bin/build-usca.sh" "$@" \
    > "$T/out.txt" 2>&1 || rc=$?
  L="$(ls -1 "$S/logs/"usca-*.log 2>/dev/null | tail -1)"; L="${L:-$T/out.txt}"
  run_id="$(basename "$L" .log)"
}
show() { echo "  $1: exit $rc, status $(status_field "$S/status-usca.json" state) at $(status_field "$S/status-usca.json" stage); $(grep -E 'FAILED|bytes free|not a mounted|connection [0-9]+ ended' "$L" | sed 's/^[^]]*] //' | tr '\n' '|')"; }

# --- R1. --local, the full path: the merge goes to the card, and is gone before routing ---------
new_srv r1 local
build --local 261017
show "R1 --local"
S_R1_LOG="$L"
check "R1: the run reached BRouter's first step (it fails there on purpose)" '[[ -f "$T/cut.rec" && "$(status_field "$S/status-usca.json" stage)" == brouter ]]'
check "C: Planetiler read the merged extract from the card's work directory for this run" \
  'grep -qx "merged=$CARD_DIR/work/$run_id/forager-usca.osm.pbf" "$T/planetiler.rec"'
check "C: the merged extract on the card was not empty when Planetiler ran" '! grep -qx "merged_bytes=missing" "$T/planetiler.rec" && ! grep -qx "merged_bytes=0" "$T/planetiler.rec"'
check "C: no merged extract was ever under the NVMe's work directory" \
  '[[ -z "$(grep -l "merged=$S/" "$T/planetiler.rec" 2>/dev/null)" ]]'
check "C: after Planetiler, the merged extract and its directory are gone from the card" \
  'grep -qx "card_work_exists=yes" "$T/cut.rec" && grep -qx "card_work=" "$T/cut.rec"'
check "C: on failure nothing of this run is left on the card" '[[ ! -e "$CARD_DIR/work/$run_id" ]]'
check "C: and the card's extracts are untouched" '[[ -f "$CARD_DIR/sources-osm/us-261017.osm.pbf" && -f "$CARD_DIR/sources-osm/canada-261017.osm.pbf" ]]'
check "D: both disks were checked at the state stage" '[[ $(grep -c "\[state\] NVMe" "$L") -eq 1 && $(grep -c "\[state\] card" "$L") -eq 1 ]]'
check "D: and again at the merge" '[[ $(grep -c "\[merge\] NVMe" "$L") -eq 1 && $(grep -c "\[merge\] card" "$L") -eq 1 ]]'
check "D: the old GiB floor is gone from the log" '! grep -q "GB free under" "$L"'
check "G: a --local run does not wait for the network" '! grep -q "^getent" "$T/calls.log" && ! grep -q "\[network\]" "$L"'

# --- R2. --local --basemap: no merge, so nothing on the card, and the card is not required -------
new_srv r2 local
cp "$T/geofabrik/us-261017.osm.pbf" "$S/kept.pmtiles"
STUB_CARD_MOUNTED=0 build --local 261017 --basemap "$S/kept.pmtiles"
show "R2 --local --basemap, card reported unmounted"
check "C: a --basemap run reaches routing with the card reported unmounted, as before B and C" \
  '[[ -f "$T/cut.rec" && "$(status_field "$S/status-usca.json" stage)" == brouter ]]'
check "C: a --basemap run never makes a work directory on the card" 'grep -qx "card_work_exists=no" "$T/cut.rec"'
check "D: a --basemap run checks the NVMe against its fixed floor, in bytes" 'grep -q "\[state\] NVMe.*118111600640 bytes" "$L"'
check "D: a --basemap run with nothing to download makes no card check" '! grep -q "\] card" "$L"'

# --- R2b. The --basemap run of R2 writes the sidecar for the run's regions (pi-14) ------------------
K="$S/failed-usca/latest/out"
check "R2b: a us canada --basemap run writes the archive's .regions beside it, naming us and canada" \
  '[[ "$(cat "$K/forager-usca.pmtiles.regions" 2>/dev/null)" == "$(printf "timestamp=2026-10-17T20:20:51Z\nsequence=us:2415,canada:4945")" ]]'

# --- R3. The download path, --fetch-only: to the card, linked, one slow connection restarted -----
new_srv r3
STUB_SLOW_FIRST=1 build --fetch-only
show "R3 download, --fetch-only, first connection of each slowed"
check "R3: the fetch-only run finished" '[[ $rc -eq 0 && "$(status_field "$S/status-usca.json" stage)" == fetch-only ]]'
for s in us canada; do
  check "B: $s-261017 is on the card, whole" 'cmp -s "$CARD_DIR/sources-osm/$s-261017.osm.pbf" "$T/geofabrik/$s-261017.osm.pbf"'
  check "B: sources/osm/$s-261017.osm.pbf is a link to the card file" \
    '[[ -L "$S/sources/osm/$s-261017.osm.pbf" && "$(readlink "$S/sources/osm/$s-261017.osm.pbf")" == "$CARD_DIR/sources-osm/$s-261017.osm.pbf" ]]'
  check "B: its .md5 is a regular file in sources/osm" '[[ -f "$S/sources/osm/$s-261017.osm.pbf.md5" && ! -L "$S/sources/osm/$s-261017.osm.pbf.md5" ]]'
  check "F: $s's second connection resumed where the first stopped" \
    'grep -qx "curl GET $s-261017.osm.pbf offset=$(( $(stat -c %s "$T/geofabrik/$s-261017.osm.pbf") / 2 ))" "$T/calls.log"'
done
check "L: every extract connection was given the Wi-Fi floor, 700000 bytes/s over 40 s" \
  '[[ $(grep -c "osm.pbf speed-limit=700000 speed-time=40$" "$T/speed.log") -eq 4 && $(wc -l < "$T/speed.log") -eq 4 ]]'
check "L: each connection's line in the run's log names the link and the floor" \
  '[[ $(grep -c "connection [12] ended .*; link wlan0, Wi-Fi floor 700000 bytes/s over 40 s" "$L") -eq 4 ]]'
check "B: the MD5 check passed through the links" '[[ $(grep -c "MD5 good" "$L") -eq 2 ]]'
check "B: nothing was downloaded onto the NVMe" '[[ -z "$(find "$S" -name "*.osm.pbf*" -type f ! -name "*.md5")" ]]'
check "G: the run waited for the network before the state stage" \
  'grep -q "^getent hosts download.geofabrik.de" "$T/calls.log" && grep "stage start" "$L" | head -1 | grep -q "\[network\]"'
check "D: the state stage read each extract's size from a HEAD of the dated file" \
  '[[ $(grep -c "^curl HEAD us-261017.osm.pbf" "$T/calls.log") -ge 1 && $(grep -c "^curl HEAD canada-261017.osm.pbf" "$T/calls.log") -ge 1 ]]'

# --- R4. An over-size week is refused at the state stage, before any download --------------------
new_srv r4
STUB_NVME_FREE=1000 build
show "R4 download, NVMe with 1000 bytes free"
check "D: the run fails at the state stage" '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state ]]'
check "D: the failure gives the NVMe's free bytes and the shortfall" 'grep -q "FAILED: .*1000 bytes free.*short by" "$L"'
check "D: no extract was downloaded" '! grep -q "^curl GET .*osm.pbf " "$T/calls.log" && [[ ! -e "$CARD_DIR/sources-osm/us-261017.osm.pbf" ]]'
# The card short instead, with the NVMe roomy.
new_srv r4b
STUB_CARD_FREE=1000 build
check "D: a short card also fails the run at the state stage, before any download" \
  '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state ]] && grep -q "FAILED: .*card.*1000 bytes free" "$L" && ! grep -q "^curl GET .*osm.pbf " "$T/calls.log"'
new_srv r4c
STUB_CARD_MOUNTED=0 build
check "B: an unmounted card fails the run at the state stage, naming the mount" \
  '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state ]] && grep -q "FAILED: .*$T/card is not a mounted filesystem" "$L"'

# --- R5. No network: the run fails at its own stage, naming what was missing ---------------------
new_srv r5
STUB_RESOLVES=0 build
show "R5 download, name lookup failing"
check "G: the run fails at the network stage" '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == network ]]'
check "G: the failure names the host that did not resolve" 'grep -q "FAILED: .*download.geofabrik.de does not resolve" "$L"'
check "G: Geofabrik was never asked" '! grep -q "^curl" "$T/calls.log"'

# --- R6. Four regions, --fetch-only: every URL from the path, every local name from the stem -------
B=https://download.geofabrik.de/north-america
REGIONS="us canada us/puerto-rico us/us-virgin-islands"
NO_POLYS=1 new_srv r6
NO_POLYS=1 build --fetch-only
show "R6 four regions, --fetch-only"
expect_urls() { # the URLs one --fetch-only run asks for, in order, for the regions given
  local r st d
  for r in "$@"; do echo "GET $B/$r-updates/state.txt"; echo "HEAD $B/$r-latest.osm.pbf"; done
  for r in "$@"; do st="${r##*/}"; d="$B"; [[ "$r" == */* ]] && d="$B/${r%/*}"; echo "HEAD $d/$st-261017.osm.pbf"; done
  for r in "$@"; do st="${r##*/}"; d="$B"; [[ "$r" == */* ]] && d="$B/${r%/*}"; echo "GET $d/$st-261017.osm.pbf.md5"; echo "GET $d/$st-261017.osm.pbf"; done
  for r in "$@"; do echo "GET $B/$r.poly"; done
}
check "R6: the four-region fetch-only run finished" '[[ $rc -eq 0 && "$(status_field "$S/status-usca.json" stage)" == fetch-only ]]'
check "R6: the URLs asked for are exactly these, in this order" \
  '[[ "$(cat "$T/urls.log")" == "$(expect_urls us canada us/puerto-rico us/us-virgin-islands)" ]] || { diff <(expect_urls us canada us/puerto-rico us/us-virgin-islands) "$T/urls.log" | head -8; false; }'
for st in puerto-rico us-virgin-islands; do
  check "R6: $st-261017 is on the card under its stem, whole" 'cmp -s "$CARD_DIR/sources-osm/$st-261017.osm.pbf" "$T/geofabrik/$st-261017.osm.pbf"'
  check "R6: sources/osm/$st-261017.osm.pbf is a link to that card file, with its .md5 beside it" \
    '[[ "$(readlink "$S/sources/osm/$st-261017.osm.pbf")" == "$CARD_DIR/sources-osm/$st-261017.osm.pbf" && -f "$S/sources/osm/$st-261017.osm.pbf.md5" ]]'
  check "R6: its outline is sources/aux/$st.poly, Geofabrik's" 'cmp -s "$S/sources/aux/$st.poly" "$W/us/$st.poly"'
done
check "R6: no directory named after a region's path, in sources or on the card" \
  '[[ -d "$CARD_DIR/sources-osm" && -d "$S/sources/aux" && -z "$(find "$S/sources" "$CARD_DIR" -mindepth 1 -type d -name us)" ]]'
check "R6: all four extracts' MD5 and header timestamp passed" '[[ $(grep -c "MD5 good" "$L") -eq 4 ]]'
in4=0; for st in us canada puerto-rico us-virgin-islands; do in4=$(( in4 + $(stat -c %s "$T/geofabrik/$st-261017.osm.pbf") )); done
check "R6: the disk check's input total is all four extracts' bytes, all of them to download" \
  'grep -q "inputs: $in4 bytes, $in4 of them still to download" "$L"'

# --- R6b. us canada, --fetch-only: the same URLs as before the islands -----------------------------
# Passes before the change too, by design: it is the record that us and canada are unchanged.
REGIONS="us canada"
NO_POLYS=1 new_srv r6b
NO_POLYS=1 build --fetch-only
check "R6b: us canada asks for exactly the URLs it asked for before the islands" \
  '[[ $rc -eq 0 && "$(cat "$T/urls.log")" == "$(expect_urls us canada)" ]] || { diff <(expect_urls us canada) "$T/urls.log" | head -8; false; }'
check "R6b: and names its files as before" \
  '[[ -L "$S/sources/osm/us-261017.osm.pbf" && -L "$S/sources/osm/canada-261017.osm.pbf" && -f "$S/sources/aux/us.poly" && -f "$S/sources/aux/canada.poly" && $(ls "$S/sources/osm" | wc -l) -eq 4 ]]'

# --- R7. Four regions, --local: the islands' files by stem, merged, outlined, and in the sidecar -----
REGIONS="us canada us/puerto-rico us/us-virgin-islands"
new_srv r7 local
build --local 261017
show "R7 four regions, --local"
K="$S/failed-usca/latest/out"
check "R7: the four-region --local run reached BRouter's first step" '[[ -f "$T/cut.rec" && "$(status_field "$S/status-usca.json" stage)" == brouter ]]'
check "R7: Planetiler read a merge of all four extracts, 8 nodes" 'grep -qx "merged_nodes=8" "$T/planetiler.rec"'
check "R7: Planetiler's outline joins the four regions' outlines, in order" \
  '[[ "$(grep "^forager-usca-" "$T/polygon.rec" 2>/dev/null | tr "\n" " ")" == "forager-usca-1-1 forager-usca-2-1 forager-usca-3-1 forager-usca-4-1 " ]]'
check "R7: the islands' outlines were fetched from their paths" \
  'grep -qx "GET $B/us/puerto-rico.poly" "$T/urls.log" && grep -qx "GET $B/us/us-virgin-islands.poly" "$T/urls.log"'
check "R7: the disk check's input total is all four extracts' bytes" 'grep -q "\[state\] inputs: $in4 bytes, 0 of them still to download" "$L"'
check "R7: the archive's .regions names the four regions, us first, and the snapshot" \
  '[[ "$(cat "$K/forager-usca.pmtiles.regions" 2>/dev/null)" == "$(printf "timestamp=2026-10-17T20:20:51Z\nsequence=us:2415,canada:4945,us/puerto-rico:3282,us/us-virgin-islands:1791")" ]]'

# --- R8. Four regions, --basemap: an archive that does not name them is refused at once -----------
for v in none two four; do
  new_srv "r8-$v" local
  cp "$T/geofabrik/us-261017.osm.pbf" "$S/kept.pmtiles"
  case $v in
    two) printf 'timestamp=2026-10-17T20:20:51Z\nsequence=us:2415,canada:4945\n' > "$S/kept.pmtiles.regions" ;;
    four) printf 'timestamp=2026-10-17T20:20:51Z\nsequence=us:2415,canada:4945,us/puerto-rico:3282,us/us-virgin-islands:1791\n' > "$S/kept.pmtiles.regions" ;;
  esac
  build --local 261017 --basemap "$S/kept.pmtiles"
  show "R8 four regions, --basemap, sidecar $v"
  case $v in
    none) check "R8: a basemap with no .regions is refused for a four-region run, at the state stage" \
            '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state ]] && grep -q "FAILED: --basemap: .*kept.pmtiles.regions" "$L"'
          check "R8: and before routing or any link into the output" '[[ ! -f "$T/cut.rec" && ! -e "$S/failed-usca/latest/out/forager-usca.pmtiles" ]]' ;;
    two) check "R8: a basemap whose .regions names us and canada only is refused, naming both lists" \
            '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state ]] && grep -q "FAILED: --basemap: .*us:2415,canada:4945; this run .*us/us-virgin-islands:1791" "$L"' ;;
    four) check "R8: a basemap whose .regions names the four regions is accepted, and the run reaches routing" \
            '[[ -f "$T/cut.rec" && "$(status_field "$S/status-usca.json" stage)" == brouter ]]' ;;
  esac
done

# --- R9. The elevation store (G8): missing, or not the pinned one, stops the run before any download --
new_srv r9-missing local
STUB_BEF_DIR="$T/no-such-bef" build --local 261017
show "R9 elevation store missing"
check "R9: a missing elevation store fails the run at the state stage, naming it, before BRouter" \
  '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state && ! -f "$T/cut.rec" ]] && grep -q "FAILED: no elevation store at $T/no-such-bef" "$L"'
mkdir -p "$T/bef-changed"; cp "$T/bef/"* "$T/bef-changed/"; echo tampered > "$T/bef-changed/srtm_17_04.bef"
new_srv r9-changed local
STUB_BEF_DIR="$T/bef-changed" build --local 261017
show "R9 elevation store changed"
check "R9: a .bef that no longer matches SHA256SUMS fails the run at the state stage" \
  '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == state ]] && grep -q "FAILED: a .bef in $T/bef-changed does not match its SHA256SUMS" "$L"'
check "R9: a good store is logged at the start of a run (R1)" 'grep -q "elevation store: $T/bef, 2 .bef files matching SHA256SUMS" "$S_R1_LOG"'

# --- R10. Heights read for every square, or the run stops after PosUnifier (S1) ------------------
mkdir -p "$T/bef-r10"; cp "$T/bef/"* "$T/bef-r10/"
new_srv r10-ok local; rm -f "$T/link.rec"
STUB_BROUTER=go STUB_BEF_DIR="$T/bef-r10" build --local 261017
show "R10 heights read"
check "R10: with the store there, the run passes PosUnifier's check and reaches WayLinker" \
  '[[ -f "$T/link.rec" ]] && ! grep -q "read no heights" "$L"'
check "R10: the pinned squares without heights are logged" 'grep -q "no heights, as pinned in USCA_BEF_NONE, for W80_N25" "$L"'
new_srv r10-drop local; rm -f "$T/link.rec"
STUB_BROUTER=go STUB_BEF_DIR="$T/bef-r10" STUB_DROP_STORE="$T/bef-r10" build --local 261017
show "R10 store gone after the cutter"
check "R10: a store gone mid-stage fails the run at brouter, naming the square, before WayLinker" \
  '[[ $rc -ne 0 && "$(status_field "$S/status-usca.json" stage)" == brouter && ! -f "$T/link.rec" ]] && grep -q "FAILED: routing band .*: PosUnifier read no heights for W125_N45" "$L"'
mv "$T/bef-r10.gone" "$T/bef-r10" 2>/dev/null || true

# --- R11. --fetch-only doesn't need the store (N1) ------------------------------------------------
new_srv r11
STUB_BEF_DIR="$T/no-such-bef" build --fetch-only
show "R11 --fetch-only, no store"
check "R11: a --fetch-only run without the store isn't stopped by it" '! grep -q "no elevation store" "$L"'

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails checks failed, $pass passed"; exit 1; fi
echo "ok: $pass checks"
