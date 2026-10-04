#!/usr/bin/env bash
# Builds the patched Protomaps basemap profile and installs its jar into /opt/forager-build/basemap.
# Change 4 of dispatch 2026-09-28-495; needs the owner's word.
#
# Run as an admin with sudo, from the installed copy (/opt/forager-build/map-build/bin/), so the
# build user can read the patch. The clone, patch, tests and package all run as the build user;
# only the final copy into /opt runs as root.
#
# Stops if the checkout is not the pinned commit, if the patch does not apply, if any upstream or
# Forager test fails, or if the Forager tests did not actually run.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "FAILED: $*"; exit 1; }
as_build() { sudo -u "$FB_USER" -H -- "$@"; }

src="$FB_SRV/src/basemaps"
patch="$here/$BASEMAPS_PATCH"
[[ -r "$patch" ]] || die "cannot read $patch"

as_build mkdir -p "$FB_SRV/src"
if [[ ! -d "$src/.git" ]]; then
  as_build git init -q "$src"
  as_build git -C "$src" remote add origin "$BASEMAPS_REPO"
fi
log "fetching $BASEMAPS_COMMIT"
as_build git -C "$src" fetch -q --depth 1 origin "$BASEMAPS_COMMIT"
# Back to the pinned commit with nothing left over from an earlier build or patch.
as_build git -C "$src" checkout -q --force --detach FETCH_HEAD
as_build git -C "$src" clean -q -f -d -x
head="$(as_build git -C "$src" rev-parse HEAD)"
[[ "$head" == "$BASEMAPS_COMMIT" ]] || die "checkout is $head, expected $BASEMAPS_COMMIT"
grep -q "return \"$BASEMAPS_TILES_VERSION\";" "$src/tiles/src/main/java/com/protomaps/basemap/Basemap.java" \
  || die "Basemap.java at this commit does not report tiles $BASEMAPS_TILES_VERSION"
log "checkout is $head (tiles $BASEMAPS_TILES_VERSION)"

as_build git -C "$src" apply --check "$patch" || die "the patch does not apply"
as_build git -C "$src" apply "$patch"
log "patch applied: $(basename "$patch")"

# `package` runs every test in tiles/ first; any failure stops here.
as_build env JAVA_HOME="$FB_OPT/jdk" "$FB_OPT/maven/bin/mvn" -B -f "$src/tiles/pom.xml" \
  -Dmaven.repo.local="$FB_SRV/.m2" package

# Confirm the Forager tests ran, rather than trusting a green build that might have skipped them.
report="$src/tiles/target/surefire-reports/TEST-com.protomaps.basemap.layers.RoadsTest.xml"
[[ -r "$report" ]] || die "no RoadsTest report at $report"
ran="$(grep -c '<testcase name="foragerTrail\|<testcase name="foragerAccess' "$report" || true)"
failed="$(grep -c '<failure\|<error' "$report" || true)"
# 4 single tests + 5 + 4 parameterized cases = 13 test cases with these names.
[[ "$ran" -eq 13 && "$failed" -eq 0 ]] || die "Forager test cases in the report: $ran (expected 13), failures: $failed"
log "Forager tests ran: $ran cases, 0 failures"

jar="$src/tiles/target/$BASEMAPS_JAR_NAME"
[[ -s "$jar" ]] || die "no jar at $jar"
sudo install -d -o root -g root -m 0755 "$FB_OPT/basemap"
sudo install -o root -g root -m 0644 "$jar" "$FB_OPT/basemap/$BASEMAPS_JAR_NAME"
log "installed $FB_OPT/basemap/$BASEMAPS_JAR_NAME, sha256 $(sha256sum "$FB_OPT/basemap/$BASEMAPS_JAR_NAME" | cut -d' ' -f1)"
"$FB_OPT/jdk/bin/java" -jar "$FB_OPT/basemap/$BASEMAPS_JAR_NAME" --version
