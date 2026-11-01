#!/usr/bin/env bash
# The worker/* release guard (dispatch 2026-11-01-coder-11; the owner: "may as well get it modular"). Cloudflare Workers
# Builds deploys main's server/pmtiles-worker, so a Worker release is a worker/* pull request to main that makes main's
# folder equal pre-main's and changes nothing else. This passes a release only if:
#   1. every file it changes against BASE (main) is inside server/pmtiles-worker, and
#   2. its server/pmtiles-worker equals PRE_MAIN's, file for file and byte for byte.
# Run by .github/workflows/worker-release.yml; tested by test/check-release.test.mjs.
#
# Usage: check-release.sh BASE HEAD PRE_MAIN   (git refs; run from inside the repository)
set -euo pipefail
base=${1:?usage: check-release.sh BASE HEAD PRE_MAIN} head=${2:?} pre=${3:?}
W=server/pmtiles-worker
fail=0
outside=$(git diff --name-only "$(git merge-base "$base" "$head")" "$head" | grep -v "^$W/" || true)
if [[ -n "$outside" ]]; then
  echo "A Worker release changes only $W. These files are outside it:"; echo "$outside" | sed 's/^/  /'; fail=1
fi
differs=$(git diff --name-only "$pre" "$head" -- "$W")
if [[ -n "$differs" ]]; then
  echo "A Worker release's $W equals pre-main's ($pre). These differ:"; echo "$differs" | sed 's/^/  /'; fail=1
fi
[[ $fail -eq 0 ]] && echo "worker release ok: only $W changes, and it equals $pre's"
exit $fail
