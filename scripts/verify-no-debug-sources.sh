#!/usr/bin/env bash
# Fails if this tree carries code that belongs only to a development build (dispatch 2026-10-29-05,
# RECORD -1805). pre-main and main build the app that ships, and they have no debug-only or
# release-only source set: `ls app/src` on pre-main at d100a4776 is androidTest, main, test,
# testDebug. Development builds are made on throwaway branches that are never merged, and this is
# what notices if one is merged by mistake.
#
# Two checks:
#   1. app/src/debug/ or app/src/release/ exists (as a directory on disk, so an empty folder
#      checked in with a placeholder file counts).
#   2. A tracked file anywhere in the tree has the file name of one that a development build adds
#      outside those two folders. The names are kept as sha256 hashes of the bare file name in
#      scripts/no-debug-sources-names.sha256, so this script and its list name nothing. The list was
#      made from the files the development-build commit adds (both copies of it), minus the two
#      generic names that already exist on pre-main (AndroidManifest.xml, file_paths.xml), which
#      check 1 covers.
#
# The CI message names no file, only a count; run this script locally with --list to see the paths.
# Usage: scripts/verify-no-debug-sources.sh [--list] [repo-root]
set -euo pipefail

list=0
if [ "${1:-}" = "--list" ]; then list=1; shift; fi
root="${1:-$(git rev-parse --show-toplevel)}"
names="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/no-debug-sources-names.sha256"

if [ ! -s "$names" ]; then
  # An empty or missing list would make check 2 pass on anything; refuse instead.
  echo "::error::$names is missing or empty, so the check cannot run."
  exit 1
fi

fail=0
for dir in app/src/debug app/src/release; do
  if [ -e "$root/$dir" ]; then
    echo "::error::$dir/ exists. This branch builds the shipped app and must have no $dir/ folder."
    fail=1
  fi
done

matches=()
while IFS= read -r -d '' path; do
  hash=$(printf '%s' "${path##*/}" | sha256sum | cut -d' ' -f1)
  if grep -qx "$hash" "$names"; then
    matches+=("$path")
  fi
done < <(git -C "$root" ls-files -z)

if [ "${#matches[@]}" -gt 0 ]; then
  echo "::error::${#matches[@]} file(s) in this tree belong only to a development build and must not land on this branch. The PR's own file list shows which; locally, scripts/verify-no-debug-sources.sh --list prints them."
  if [ "$list" = 1 ]; then printf '  %s\n' "${matches[@]}"; fi
  fail=1
fi

tracked=$(git -C "$root" ls-files | wc -l)
listed=$(wc -l < "$names")
echo "Checked $tracked tracked files against $listed names, and the two folders."
if [ "$fail" = 0 ]; then echo "OK: no development-build code in this tree."; fi
exit "$fail"
