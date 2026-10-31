#!/usr/bin/env bash
# Tests for bin/upload-routing.sh (dispatch 2026-10-30-coder-04): the real script, run from its own entry point on a fake
# output directory, with a stand-in `rclone` on PATH that keeps the "remote" as a directory, so copies and read-backs act
# on real bytes. Nothing reaches R2. Checks the order (squares, names, NOTICE, read-backs, then the manifest last), that a
# failed read-back or a manifest that does not match the files stops the run before the manifest, and --dry-run.
#
# Run: bash map-build/tools/test_upload_routing.sh   (runs every check; exits non-zero on a failure)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
script="$here/bin/upload-routing.sh"
T="${KEEP_T:-$(mktemp -d)}"
[[ -n "${KEEP_T:-}" ]] || trap 'rm -rf -- "$T"' EXIT
[[ -x "$script" ]] || { echo "FAIL: $script is missing or not executable"; exit 1; }

# --- The stand-in rclone: REMOTE:path maps to $STUB_REMOTE/path ------------------------------------------------------
mkdir -p "$T/bin"
cat > "$T/bin/rclone" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "rclone $*" >> "$STUB_T/calls.log"
loc() { local p=$1; [[ "$p" == *:* ]] && echo "$STUB_REMOTE/${p#*:}" || echo "$p"; }
cmd=$1; shift
args=(); inc=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --include) inc=$2; shift 2 ;;
    --header-upload|--transfers) shift 2 ;;
    -*) shift ;;
    *) args+=("$1"); shift ;;
  esac
done
case $cmd in
  copy) src=$(loc "${args[0]}"); dst=$(loc "${args[1]}"); mkdir -p "$dst"; for f in "$src"/$inc; do cp "$f" "$dst/"; done ;;
  copyto) dst=$(loc "${args[1]}"); mkdir -p "$(dirname "$dst")"; cp "$(loc "${args[0]}")" "$dst" ;;
  cat) cat "$(loc "${args[0]}")" ;;
  lsf) ls -1 "$(loc "${args[0]}")" ;;
  check)
    [[ "${STUB_FAIL_CHECK:-}" == "${args[0]##*/}" ]] && { echo "ERROR : 1 differences found"; exit 1; }
    diff -rq "$(loc "${args[0]}")" "$(loc "${args[1]}")" >/dev/null && echo "0 differences found" ;;
  *) echo "stub rclone: unexpected $cmd" >&2; exit 3 ;;
esac
EOF
chmod +x "$T/bin/rclone"
export PATH="$T/bin:$PATH" STUB_T="$T" FB_R2_REMOTE="r2:forager-maps"

# --- A fake output, staged by run_record.py as the build does ------------------------------------------------------
fresh() { # a new output directory and an empty remote
  rm -rf "$T/out" "$T/remote" "$T/calls.log"; mkdir -p "$T/out/segments" "$T/out/names1" "$T/remote"; : > "$T/calls.log"
  printf 'square A' > "$T/out/segments/W125_N45.rd5"; printf 'square B' > "$T/out/segments/W120_N45.rd5"
  printf 'names A' > "$T/out/names1/W125_N45.fnm"; printf 'names B' > "$T/out/names1/W120_N45.fnm"
  python3 "$here/tools/run_record.py" routing-manifest "$T/out" --run-id usca-r9 --osm-timestamp 2026-10-31T20:20:51Z \
    --osm-sequence "us:1,canada:2" --notice "$here/notices/routing-NOTICE.txt"
  export STUB_REMOTE="$T/remote"
}
pass=0; nfail=0
check() { if (set +u; eval "$2"); then pass=$((pass + 1)); else echo "FAIL: $1"; nfail=$((nfail + 1)); fi; }
line_of() { grep -n -- "$1" "$T/calls.log" | head -1 | cut -d: -f1; }

# U1: a clean upload.
fresh
check "U1 the upload succeeds" 'bash "$script" "$T/out" > "$T/u1.out" 2>&1'
check "U1 the squares are on the remote under segments4" 'cmp -s "$T/remote/forager-maps/routing/usca-r9/segments4/W125_N45.rd5" "$T/out/segments/W125_N45.rd5"'
check "U1 the names are under names1" 'cmp -s "$T/remote/forager-maps/routing/usca-r9/names1/W120_N45.fnm" "$T/out/names1/W120_N45.fnm"'
check "U1 NOTICE is beside them" 'cmp -s "$T/remote/forager-maps/routing/usca-r9/NOTICE" "$T/out/NOTICE"'
check "U1 the live manifest is the staged one" 'cmp -s "$T/remote/forager-maps/routing/manifest.json" "$T/out/routing-manifest.json"'
check "U1 order: squares, names, NOTICE, then the manifest last" \
  '(( $(line_of "segments4") < $(line_of "names1") && $(line_of "names1") < $(line_of "NOTICE") && $(line_of "NOTICE") < $(line_of "routing/manifest.json") )) && [[ "$(grep -c "copyto.*routing/manifest.json" "$T/calls.log")" == 1 ]] && ! tail -n +$(( $(line_of "copyto.*routing/manifest.json") + 1 )) "$T/calls.log" | grep -q " copy"'
check "U1 the manifest goes with Content-Type application/json" 'grep -q "copyto .*routing-manifest.json r2:forager-maps/routing/manifest.json --header-upload Content-Type: application/json" "$T/calls.log"'

# U2: a failed read-back of the squares stops the run before the manifest.
fresh
check "U2 a failed squares read-back fails the run" '! STUB_FAIL_CHECK=segments bash "$script" "$T/out" > "$T/u2.out" 2>&1'
check "U2 and the manifest was not uploaded" '[[ ! -e "$T/remote/forager-maps/routing/manifest.json" ]] && ! grep -q "routing/manifest.json" "$T/calls.log"'

# U3: no NOTICE: refused before anything is uploaded.
fresh; rm "$T/out/NOTICE"
check "U3 a missing NOTICE is refused" '! bash "$script" "$T/out" > "$T/u3.out" 2>&1'
check "U3 with no rclone copy at all" '! grep -q "copy" "$T/calls.log"'

# U4: a square on disk that the manifest does not list (or a changed one): refused before any upload.
fresh; printf 'changed' > "$T/out/segments/W125_N45.rd5"
check "U4 a square that differs from the manifest is refused" '! bash "$script" "$T/out" > "$T/u4.out" 2>&1 && grep -q "W125_N45.rd5" "$T/u4.out"'
check "U4 with no rclone copy" '! grep -q "copy" "$T/calls.log"'
fresh; printf 'extra' > "$T/out/segments/W115_N45.rd5"
check "U4 a square the manifest does not list is refused" '! bash "$script" "$T/out" > "$T/u4b.out" 2>&1 && ! grep -q "copy" "$T/calls.log"'

# U5: --dry-run checks and prints, and uploads nothing.
fresh
check "U5 --dry-run succeeds" 'bash "$script" --dry-run "$T/out" > "$T/u5.out" 2>&1'
check "U5 and calls no rclone" '[[ ! -s "$T/calls.log" ]]'

# U6: the manifest's notice field names the NOTICE key the script uploads.
fresh; bash "$script" "$T/out" > /dev/null 2>&1
check "U6 the manifest's notice is the uploaded key" '[[ "$(python3 -c "import json;print(json.load(open(\"$T/out/routing-manifest.json\"))[\"notice\"])")" == "usca-r9/NOTICE" && -s "$T/remote/forager-maps/routing/usca-r9/NOTICE" ]]'

if [[ $nfail -gt 0 ]]; then echo "FAILED: $nfail checks failed, $pass passed"; exit 1; fi
echo "ok: $pass checks"
