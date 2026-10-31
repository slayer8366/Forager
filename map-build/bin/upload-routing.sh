#!/usr/bin/env bash
# Uploads one published routing set to R2, the way the hand-written upload scripts did (Forager-cartography
# docs/audits/2026-10-23-route-squares-upload.md, 2026-10-26-three-builds-files/upload-261024.sh), and adds NOTICE
# (dispatch 2026-10-30-coder-04; the owner: "A: commit an upload script (Recommended)").
#
# Usage: upload-routing.sh [--dry-run] OUTPUT_DIR
#   OUTPUT_DIR is a build-usca.sh output (e.g. /srv/forager-build/out-usca/current), holding segments/*.rd5,
#   names1/*.fnm, NOTICE and routing-manifest.json, the last two staged by stage_routing_set.
#   FB_R2_REMOTE names the rclone remote and bucket (default r2:forager-maps).
#
# Running it publishes a new routing set to every phone: it needs the owner's go each time, as each upload did before.
#
# Order, each step stopping the run if it fails:
#   1. Every square and names file on disk matches routing-manifest.json by name, size and SHA-256, and NOTICE matches
#      the manifest's notices, so what goes up is what the manifest says. Nothing is uploaded before this passes.
#   2. routing/<run_id>/segments4/ (the squares), routing/<run_id>/names1/, routing/<run_id>/NOTICE.
#   3. Read-backs: rclone check --download of both folders, NOTICE's SHA-256, and the object counts.
#   4. routing/manifest.json, last, as application/json, then read back and compared by SHA-256. Until this step the
#      Worker keeps serving the previous run, which the previous manifest names.
set -euo pipefail

dry=0
[[ "${1:-}" == "--dry-run" ]] && { dry=1; shift; }
out=${1:?usage: upload-routing.sh [--dry-run] OUTPUT_DIR}
remote=${FB_R2_REMOTE:-r2:forager-maps}
log() { printf '%s %s\n' "$(TZ=America/Los_Angeles date '+%F %T %Z')" "$*"; }
die() { log "FAILED: $*"; exit 1; }

manifest="$out/routing-manifest.json"
[[ -f "$manifest" ]] || die "no $manifest: stage it with build-usca.sh's stage_routing_set"
[[ -f "$out/NOTICE" ]] || die "no $out/NOTICE: stage it with build-usca.sh's stage_routing_set"

# 1. The files against the manifest (and the run id and keys the Worker will read).
run_id="$(python3 - "$out" "$manifest" <<'PY'
import hashlib, json, os, re, sys
out, path = sys.argv[1], sys.argv[2]
m = json.load(open(path, encoding="utf-8"))
run_id = m.get("run_id", "")
if not re.match(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$", run_id):
    sys.exit(f"manifest run_id {run_id!r} is not one the Worker accepts")
def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()
problems = []
for key, folder, r2, suffix in (("files", "segments", "segments4", ".rd5"), ("names", "names1", "names1", ".fnm")):
    listed = {e["path"]: e for e in m.get(key, [])}
    on_disk = sorted(n for n in os.listdir(os.path.join(out, folder)) if n.endswith(suffix))
    if key == "files" and not on_disk:
        problems.append(f"no squares in {folder}/")
    for n in on_disk:
        e = listed.pop(f"{run_id}/{r2}/{n}", None)
        p = os.path.join(out, folder, n)
        if e is None:
            problems.append(f"{folder}/{n} is not in the manifest")
        elif e["size"] != os.path.getsize(p) or e["sha256"] != sha(p):
            problems.append(f"{folder}/{n} differs from the manifest")
    problems += [f"{p} is in the manifest but not on disk" for p in listed]
with open(os.path.join(out, "NOTICE"), encoding="utf-8") as f:
    if [l for l in f.read().split("\n") if l] != m.get("notices"):
        problems.append("NOTICE differs from the manifest's notices")
if m.get("notice") != f"{run_id}/NOTICE":
    problems.append(f"the manifest's notice is {m.get('notice')!r}, expected {run_id}/NOTICE")
if problems:
    sys.exit("\n".join(problems))
print(run_id)
PY
)" || die "the output does not match routing-manifest.json (above); nothing was uploaded"

dest="$remote/routing/$run_id"
squares=$(ls "$out/segments" | grep -c '\.rd5$' || true)
names=$(ls "$out/names1" 2>/dev/null | grep -c '\.fnm$' || true)
log "run $run_id: $squares squares, $names names files, NOTICE; to $dest, then $remote/routing/manifest.json"
if [[ $dry -eq 1 ]]; then log "dry run: checked, nothing uploaded"; exit 0; fi

# 2. The set.
log "upload segments4"; rclone copy "$out/segments" "$dest/segments4" --include '*.rd5' --transfers 4 -q
log "upload names1";    rclone copy "$out/names1" "$dest/names1" --include '*.fnm' --transfers 4 -q
log "upload NOTICE";    rclone copyto "$out/NOTICE" "$dest/NOTICE" --header-upload 'Content-Type: text/plain; charset=utf-8' -q

# 3. Read-backs.
log "read-back segments4"; rclone check "$out/segments" "$dest/segments4" --download --one-way --include '*.rd5' \
  || die "segments4 read-back differs; the manifest was not uploaded"
log "read-back names1";    rclone check "$out/names1" "$dest/names1" --download --one-way --include '*.fnm' \
  || die "names1 read-back differs; the manifest was not uploaded"
a=$(sha256sum "$out/NOTICE" | cut -c1-64); b=$(rclone cat "$dest/NOTICE" | sha256sum | cut -c1-64)
[[ "$a" == "$b" ]] || die "NOTICE read-back differs ($a, $b); the manifest was not uploaded"
rs=$(rclone lsf "$dest/segments4" | grep -c '\.rd5$' || true); rn=$(rclone lsf "$dest/names1" | grep -c '\.fnm$' || true)
[[ "$rs" == "$squares" && "$rn" == "$names" ]] || die "counts on R2: $rs squares, $rn names (expected $squares, $names)"
log "read-backs match: $rs squares, $rn names files, NOTICE $a"

# 4. The manifest, last.
log "manifest"; rclone copyto "$manifest" "$remote/routing/manifest.json" --header-upload 'Content-Type: application/json' -q
a=$(sha256sum "$manifest" | cut -c1-64); b=$(rclone cat "$remote/routing/manifest.json" | sha256sum | cut -c1-64)
[[ "$a" == "$b" ]] || die "MANIFEST MISMATCH: local $a, R2 $b"
log "done: routing/manifest.json names $run_id (sha256 $a)"
