#!/usr/bin/env bash
# Copies map-build/, exactly as committed at the repository's HEAD, to /opt/forager-build/map-build,
# root-owned and readable by the build user, and records the commit in COMMIT. Every manifest
# names that commit. Needs the owner's word: it is the first step of change 4.
#
# Usage: install-map-build.sh      (run as an admin with sudo, from a checkout of the repository)
#
# Refuses uncommitted changes under map-build/, so what runs is always something in git history.
# The previous copy is kept as map-build.prev.
set -euo pipefail

repo="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
here="$repo/map-build"
# shellcheck source=../pins.env
source "$here/pins.env"

[[ -z "$(git -C "$repo" status --porcelain -- map-build)" ]] \
  || { echo "uncommitted changes under map-build/; commit them first" >&2; exit 1; }
commit="$(git -C "$repo" rev-parse HEAD)"

new="$FB_OPT/map-build.new"
[[ -e "$new" ]] && { echo "$new exists from an interrupted install; inspect and remove it first" >&2; exit 1; }
sudo install -d -o root -g root -m 0755 "$new"
git -C "$repo" archive "$commit" map-build | sudo tar -x -C "$new" --strip-components=1 --no-same-owner
echo "$commit" | sudo tee "$new/COMMIT" >/dev/null
sudo chmod -R u+rwX,go+rX,go-w "$new"

if [[ -e "$FB_OPT/map-build" ]]; then
  [[ -e "$FB_OPT/map-build.prev" ]] && sudo mv -T "$FB_OPT/map-build.prev" "$FB_OPT/map-build.prev.$(date -u +%s)"
  sudo mv -T "$FB_OPT/map-build" "$FB_OPT/map-build.prev"
fi
sudo mv -T "$new" "$FB_OPT/map-build"
echo "installed map-build at $commit into $FB_OPT/map-build"
