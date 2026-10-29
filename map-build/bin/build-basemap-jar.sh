#!/usr/bin/env bash
# Builds the patched Protomaps basemap profile and installs its jar into /opt/forager-build/basemap.
# Change 4 of dispatch 2026-09-28-495; needs the owner's word.
#
# Run as an admin with sudo, from the installed copy (/opt/forager-build/map-build/bin/), so the
# build user can read the patch. The clone, patch, tests and package all run as the build user;
# only the final copy into /opt runs as root.
#
# Stops if the checkout is not the pinned commit, if a patch does not apply, if any upstream or
# Forager test fails, or if the Forager tests did not actually run.
#
# The patches (BASEMAPS_PATCHES in pins.env) are applied in order. 0002 (dispatch 2026-10-22-pi-30)
# departs from upstream because OSM data sometimes tags a forest with natural=bare_rock too
# (r14965119 near Seaside, OR, 2026-10-22); Forager prefers the landuse forest/wood kind. Owner's
# ruling, relayed by Rowan and confirmed in the cartography planner session. landuse=wood is not an
# upstream landuse kind, so wood + bare_rock is left as bare_rock.
#
# 0003 (dispatch 2026-10-28, the owner's go of 10-28) adds a barriers layer (fence, wall, hedge, retaining_wall
# lines at zoom 15), keeps addr_street on zoom-15 address points, and sets tunnel on water lines, for the app's
# way to a waypoint: its street drop-off and the gates on its straight legs. 0004 (the owner's choice (b), 10-28) puts a
# mark, one point with no fields, in the barriers layer of every zoom-15 tile, so the app can tell "no fence here"
# from "built before fences".
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../pins.env
source "$here/pins.env"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "FAILED: $*"; exit 1; }
as_build() { sudo -u "$FB_USER" -H -- "$@"; }

src="$FB_SRV/src/basemaps"
patches=()
for p in $BASEMAPS_PATCHES; do
  [[ -r "$here/$p" ]] || die "cannot read $here/$p"
  patches+=("$here/$p")
done
[[ ${#patches[@]} -gt 0 ]] || die "no patches listed in BASEMAPS_PATCHES"

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

for patch in "${patches[@]}"; do
  as_build git -C "$src" apply --check "$patch" || die "the patch does not apply: $(basename "$patch")"
  as_build git -C "$src" apply "$patch"
  log "patch applied: $(basename "$patch")"
done

# `package` runs every test in tiles/ first; any failure stops here.
as_build env JAVA_HOME="$FB_OPT/jdk" "$FB_OPT/maven/bin/mvn" -B -f "$src/tiles/pom.xml" \
  -Dmaven.repo.local="$FB_SRV/.m2" package

# Confirm the Forager tests ran, rather than trusting a green build that might have skipped them.
report="$src/tiles/target/surefire-reports/TEST-com.protomaps.basemap.layers.RoadsTest.xml"
[[ -r "$report" ]] || die "no RoadsTest report at $report"
ran="$(grep -c '<testcase name="foragerTrail\|<testcase name="foragerAccess\|<testcase name="foragerZoom' "$report" || true)"
failed="$(grep -c '<failure\|<error' "$report" || true)"
# Trail attributes: 4 single tests + 5 + 4 parameterized cases = 13. Track and path zooms (dispatch
# 2026-10-21-pi-23): 2 single tests + 2 + 10 + 2 parameterized cases = 16. 29 test cases with these names.
[[ "$ran" -eq 29 && "$failed" -eq 0 ]] || die "Forager test cases in the report: $ran (expected 29), failures: $failed"
log "Forager tests ran: $ran cases, 0 failures"

# Forest over bare rock (patch 0002, dispatch 2026-10-22-pi-30): 1 parameterized test, 4 cases.
lreport="$src/tiles/target/surefire-reports/TEST-com.protomaps.basemap.layers.LanduseTest.xml"
[[ -r "$lreport" ]] || die "no LanduseTest report at $lreport"
lran="$(grep -c '<testcase name="foragerForestOverBareRock' "$lreport" || true)"
lfailed="$(grep -c '<failure\|<error' "$lreport" || true)"
[[ "$lran" -eq 4 && "$lfailed" -eq 0 ]] || die "foragerForestOverBareRock cases in the LanduseTest report: $lran (expected 4), failures: $lfailed"
log "Forager landuse tests ran: $lran cases, 0 failures"

# 0003's and 0004's cases: BarriersTest 8 (5 + the mark's 3), BuildingsTest 3, WaterTest 3, each named forager*.
for pair in BarriersTest:8 BuildingsTest:3 WaterTest:3; do
  cls=${pair%%:*}; want=${pair##*:}
  r="$src/tiles/target/surefire-reports/TEST-com.protomaps.basemap.layers.$cls.xml"
  [[ -r "$r" ]] || die "no $cls report at $r"
  n="$(grep -c '<testcase name="forager' "$r" || true)"
  f="$(grep -c '<failure\|<error' "$r" || true)"
  [[ "$n" -eq "$want" && "$f" -eq 0 ]] || die "forager cases in the $cls report: $n (expected $want), failures: $f"
  log "Forager $cls cases ran: $n, 0 failures"
done

jar="$src/tiles/target/$BASEMAPS_JAR_NAME"
[[ -s "$jar" ]] || die "no jar at $jar"
sudo install -d -o root -g root -m 0755 "$FB_OPT/basemap"
sudo install -o root -g root -m 0644 "$jar" "$FB_OPT/basemap/$BASEMAPS_JAR_NAME"
log "installed $FB_OPT/basemap/$BASEMAPS_JAR_NAME, sha256 $(sha256sum "$FB_OPT/basemap/$BASEMAPS_JAR_NAME" | cut -d' ' -f1)"
"$FB_OPT/jdk/bin/java" -jar "$FB_OPT/basemap/$BASEMAPS_JAR_NAME" --version
