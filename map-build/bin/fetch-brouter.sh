#!/usr/bin/env bash
# Downloads BRouter's v1.7.10 release zip, checks it, and installs its fat jar and routing profiles
# into /opt/forager-build/brouter. Change 5 of dispatch 2026-09-28-495; needs the owner's word.
#
# Usage: fetch-brouter.sh <download-dir>     (run as an admin with sudo)
#
# The owner's ruling E (RECORD -601): if the zip lacks the map creator, stop and ask; do not
# compile instead. That case exits 3 with a message saying so, and installs nothing.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "FAILED: $*"; exit 1; }

dl="${1:?usage: fetch-brouter.sh <download-dir>}"
mkdir -p "$dl"
zip="$dl/$BROUTER_FILE"
if [[ ! -s "$zip" ]]; then
  log "downloading $BROUTER_URL"
  curl -sSfL -A "$HTTP_USER_AGENT" -o "$zip.part" "$BROUTER_URL"
  mv "$zip.part" "$zip"
fi
[[ "$(stat -c %s "$zip")" == "$BROUTER_SIZE" ]] || die "size $(stat -c %s "$zip"), expected $BROUTER_SIZE"
echo "$BROUTER_SHA256  $zip" | sha256sum -c --quiet - || die "SHA-256 mismatch (GitHub's digest)"
log "zip good: size and GitHub's SHA-256 digest match"

# Locate the jar and the profiles inside the zip, and check the map creator is in the jar.
# Prints "<jar member>\t<profiles2 prefix>" on success; exits 3 if the map creator is absent.
layout="$(python3 - "$zip" "$BROUTER_VERSION" $BROUTER_REQUIRED_CLASSES <<'EOF'
import io, sys, zipfile
zpath, version, required = sys.argv[1], sys.argv[2], sys.argv[3:]
z = zipfile.ZipFile(zpath)
names = z.namelist()
jars = [n for n in names if n.endswith(f"brouter-{version}-all.jar")]
if len(jars) != 1:
    print(f"STOP: expected one brouter-{version}-all.jar in the zip, found {jars}", file=sys.stderr); sys.exit(3)
inner = set(zipfile.ZipFile(io.BytesIO(z.read(jars[0]))).namelist())
missing = [c for c in required if c not in inner]
if missing:
    print(f"STOP: the release jar lacks {missing}. Owner's ruling E: ask the owner; do not compile.", file=sys.stderr)
    sys.exit(3)
lookups = [n for n in names if n.endswith("profiles2/lookups.dat")]
if len(lookups) != 1:
    print(f"expected one profiles2/lookups.dat, found {lookups}", file=sys.stderr); sys.exit(1)
prefix = lookups[0][: -len("lookups.dat")]
for p in ("all.brf", "trekking.brf", "softaccess.brf", "hiking-mountain.brf"):
    if prefix + p not in names:
        print(f"missing {prefix + p}", file=sys.stderr); sys.exit(1)
print(f"{jars[0]}\t{prefix}")
EOF
)" || { code=$?; [[ $code -eq 3 ]] && { log "STOPPED: ask the owner (ruling E); nothing installed"; exit 3; }; die "zip layout check failed"; }
jar_member="${layout%%$'\t'*}"
profiles_prefix="${layout#*$'\t'}"
log "map creator present in $jar_member; profiles at $profiles_prefix"

dest="$FB_OPT/brouter-$BROUTER_VERSION"
[[ -e "$dest" ]] && die "$dest already exists; not overwriting"
stage="$(mktemp -d "$dl/brouter-unpack.XXXXXX")"
python3 - "$zip" "$jar_member" "$profiles_prefix" "$stage" "$BROUTER_VERSION" <<'EOF'
import os, sys, zipfile
zpath, jar, prefix, out, version = sys.argv[1:]
z = zipfile.ZipFile(zpath)
os.makedirs(os.path.join(out, "profiles2"))
with open(os.path.join(out, f"brouter-{version}-all.jar"), "wb") as f:
    f.write(z.read(jar))
for n in z.namelist():
    if n.startswith(prefix) and not n.endswith("/"):
        rel = n[len(prefix):]
        if "/" in rel:
            continue
        with open(os.path.join(out, "profiles2", rel), "wb") as f:
            f.write(z.read(n))
EOF
sudo install -d -o root -g root -m 0755 "$dest" "$dest/profiles2" "$FB_OPT/archives"
sudo install -o root -g root -m 0644 "$stage/brouter-$BROUTER_VERSION-all.jar" "$dest/"
sudo install -o root -g root -m 0644 "$stage"/profiles2/* "$dest/profiles2/"
sudo ln -sfn "brouter-$BROUTER_VERSION" "$FB_OPT/brouter"
sudo install -o root -g root -m 0644 "$zip" "$FB_OPT/archives/"
log "installed $dest (jar and $(ls "$dest/profiles2" | wc -l) profile files), linked as $FB_OPT/brouter"
"$FB_OPT/jdk/bin/java" -cp "$dest/brouter-$BROUTER_VERSION-all.jar" btools.server.BRouter 2>&1 | head -2
