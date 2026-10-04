#!/usr/bin/env bash
# Installs the build toolchain on the Pi: Eclipse Temurin 21 and Apache Maven into /opt/forager-build,
# each checked against its pinned checksum and its pinned signing key, and osmium-tool from Debian.
# Change 1 of dispatch 2026-09-28-495, run on the owner's word (RECORD -601).
#
# Usage: install-toolchain.sh <download-dir>
#   Run as a user with sudo. Archives already in <download-dir> are re-checked, not re-fetched.
#   Nothing is installed unless every check passes. An existing install directory is left alone,
#   and the script stops rather than overwrite it.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

dl="${1:?usage: install-toolchain.sh <download-dir>}"
mkdir -p "$dl"
dl="$(cd "$dl" && pwd)"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "FAILED: $*"; exit 1; }

fetch() { # url dest
  if [[ -s "$2" ]]; then log "present: $2"; return; fi
  log "downloading $1"
  curl -sSfL -A "$HTTP_USER_AGENT" -o "$2.part" "$1"
  mv "$2.part" "$2"
}

# A keyring used only for these checks, kept beside the downloads (never the user's own keyring).
keyring="$dl/.gnupg-verify"
mkdir -p -m 700 "$keyring"

verify_signature() { # file signature-file key-file expected-primary-fingerprint
  gpg --homedir "$keyring" --batch --quiet --import "$here/$3" 2>/dev/null
  local status
  status="$(gpg --homedir "$keyring" --batch --status-fd 1 --verify "$2" "$1" 2>/dev/null || true)"
  grep -q '^\[GNUPG:\] GOODSIG ' <<<"$status" || die "no good signature on $1"
  # VALIDSIG's last field is the primary key's fingerprint.
  local primary
  primary="$(awk '/^\[GNUPG:\] VALIDSIG /{print $NF}' <<<"$status")"
  [[ "$primary" == "$4" ]] || die "signature on $1 is by $primary, expected $4"
  log "signature good: $(basename "$1"), primary key $primary"
}

install_archive() { # file unpacked-dir-name link-name
  if [[ -e "$FB_OPT/$2" ]]; then die "$FB_OPT/$2 already exists; not overwriting"; fi
  sudo install -d -o root -g root -m 0755 "$FB_OPT" "$FB_OPT/archives"
  sudo tar -xzf "$1" -C "$FB_OPT" --no-same-owner --no-same-permissions
  [[ -d "$FB_OPT/$2" ]] || die "$1 did not unpack to $FB_OPT/$2"
  sudo chmod -R u+rwX,go+rX,go-w "$FB_OPT/$2"
  sudo ln -sfn "$2" "$FB_OPT/$3"
  # Keep the verified archive, so a reinstall can be checked again without a download.
  sudo install -o root -g root -m 0644 "$1" "$FB_OPT/archives/"
  log "installed $FB_OPT/$2, linked as $FB_OPT/$3"
}

# --- Temurin -------------------------------------------------------------------------------
fetch "$TEMURIN_URL" "$dl/$TEMURIN_FILE"
fetch "$TEMURIN_URL.sig" "$dl/$TEMURIN_FILE.sig"
echo "$TEMURIN_SHA256  $dl/$TEMURIN_FILE" | sha256sum -c --quiet - || die "Temurin SHA-256 mismatch"
log "sha256 good: $TEMURIN_FILE"
verify_signature "$dl/$TEMURIN_FILE" "$dl/$TEMURIN_FILE.sig" "$TEMURIN_SIGNER_KEY" "$TEMURIN_SIGNER_FPR"

# --- Maven ---------------------------------------------------------------------------------
fetch "$MAVEN_URL" "$dl/$MAVEN_FILE"
fetch "$MAVEN_URL.asc" "$dl/$MAVEN_FILE.asc"
echo "$MAVEN_SHA512  $dl/$MAVEN_FILE" | sha512sum -c --quiet - || die "Maven SHA-512 mismatch"
log "sha512 good: $MAVEN_FILE"
verify_signature "$dl/$MAVEN_FILE" "$dl/$MAVEN_FILE.asc" "$MAVEN_SIGNER_KEY" "$MAVEN_SIGNER_FPR"

# Every check has passed; only now is anything installed.
install_archive "$dl/$TEMURIN_FILE" "$TEMURIN_DIR" jdk
install_archive "$dl/$MAVEN_FILE" "$MAVEN_DIR" maven

# --- osmium-tool ---------------------------------------------------------------------------
sudo apt-get install -y --no-install-recommends "osmium-tool=$OSMIUM_DEB_VERSION"

# --- What is now installed -----------------------------------------------------------------
"$FB_OPT/jdk/bin/java" -version 2>&1 | sed 's/^/  java: /'
JAVA_HOME="$FB_OPT/jdk" "$FB_OPT/maven/bin/mvn" --version 2>&1 | head -1 | sed 's/^/  mvn: /'
osmium --version 2>&1 | head -1 | sed 's/^/  osmium: /'
log "toolchain installed"
