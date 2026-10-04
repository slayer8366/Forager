#!/usr/bin/env bash
# Installs BRouter v1.7.10 into /opt/forager-build/brouter: the fat jar from the release zip, and
# the routing profiles from BRouter's source at the same tag. Change 5 of dispatch 2026-09-28-495;
# needs the owner's word.
#
# Usage: fetch-brouter.sh <download-dir>     (run as an admin with sudo)
#
# The owner's ruling E (RECORD -601): if the zip's jar lacks the map creator, stop and ask; do not
# compile instead. That case exits 3 with a message saying so, and installs nothing.
# The owner's change 5 ruling (option b): the release zip leaves out all.brf and softaccess.brf,
# which the map creator reads, so every profile file the build or its verification reads comes
# from misc/profiles2 at the tag, each checked against its pinned git blob hash. Nothing is
# installed unless every check passes.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "FAILED: $*"; exit 1; }

dl="${1:?usage: fetch-brouter.sh <download-dir>}"
mkdir -p "$dl"

# --- The jar, from the release zip -----------------------------------------------------------
zip="$dl/$BROUTER_FILE"
if [[ ! -s "$zip" ]]; then
  log "downloading $BROUTER_URL"
  curl -sSfL -A "$HTTP_USER_AGENT" -o "$zip.part" "$BROUTER_URL"
  mv "$zip.part" "$zip"
fi
[[ "$(stat -c %s "$zip")" == "$BROUTER_SIZE" ]] || die "size $(stat -c %s "$zip"), expected $BROUTER_SIZE"
echo "$BROUTER_SHA256  $zip" | sha256sum -c --quiet - || die "SHA-256 mismatch (GitHub's digest)"
log "zip good: size and GitHub's SHA-256 digest match"

stage="$(mktemp -d "$dl/brouter-unpack.XXXXXX")"
mkdir -p "$stage/profiles2"
# Extracts the jar and checks the classes it must hold; exits 3 if the map creator is absent.
python3 - "$zip" "$BROUTER_VERSION" "$stage" $BROUTER_REQUIRED_CLASSES <<'EOF' \
  || { code=$?; [[ $code -eq 3 ]] && { log "STOPPED: ask the owner (ruling E); nothing installed"; exit 3; }; die "jar check failed"; }
import io, os, sys, zipfile
zpath, version, out, required = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
z = zipfile.ZipFile(zpath)
jars = [n for n in z.namelist() if n.endswith(f"brouter-{version}-all.jar")]
if len(jars) != 1:
    print(f"STOP: expected one brouter-{version}-all.jar in the zip, found {jars}", file=sys.stderr); sys.exit(3)
data = z.read(jars[0])
inner = set(zipfile.ZipFile(io.BytesIO(data)).namelist())
missing = [c for c in required if c not in inner]
if missing:
    print(f"STOP: the release jar lacks {missing}. Owner's ruling E: ask the owner; do not compile.", file=sys.stderr)
    sys.exit(3)
with open(os.path.join(out, f"brouter-{version}-all.jar"), "wb") as f:
    f.write(data)
print(f"jar {jars[0]}: all {len(required)} required classes present")
EOF
log "jar extracted from the zip, sha256 $(sha256sum "$stage/brouter-$BROUTER_VERSION-all.jar" | cut -d' ' -f1)"

# --- The profiles, from BRouter's source at the tag ---------------------------------------------
for entry in $BROUTER_PROFILE_BLOBS; do
  name="${entry%%:*}"; want="${entry#*:}"
  curl -sSf -A "$HTTP_USER_AGENT" -o "$stage/profiles2/$name" "$BROUTER_PROFILES_URL/$name"
  got="$(git hash-object "$stage/profiles2/$name")"
  [[ "$got" == "$want" ]] || die "$name: git blob $got, expected $want"
  log "profile good: $name, git blob $got (BRouter source at ${BROUTER_COMMIT:0:8})"
done

# --- Install: only now, with every check passed -------------------------------------------------
dest="$FB_OPT/brouter-$BROUTER_VERSION"
[[ -e "$dest" ]] && die "$dest already exists; not overwriting"
sudo install -d -o root -g root -m 0755 "$dest" "$dest/profiles2" "$FB_OPT/archives"
sudo install -o root -g root -m 0644 "$stage/brouter-$BROUTER_VERSION-all.jar" "$dest/"
sudo install -o root -g root -m 0644 "$stage"/profiles2/* "$dest/profiles2/"
sudo ln -sfn "brouter-$BROUTER_VERSION" "$FB_OPT/brouter"
sudo install -o root -g root -m 0644 "$zip" "$FB_OPT/archives/"
log "installed $dest: the jar and $(ls "$dest/profiles2" | wc -l) profile files; linked as $FB_OPT/brouter"
"$FB_OPT/jdk/bin/java" -cp "$dest/brouter-$BROUTER_VERSION-all.jar" btools.server.BRouter 2>&1 | head -2
