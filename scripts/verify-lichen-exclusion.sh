#!/usr/bin/env bash
# Verifies that iNaturalist actually honours `without_taxon_id` — the parameter the
# Fungi filter relies on to keep lichens out of the ranked list.
#
# This needs its own check because the failure mode is silent: iNaturalist answers
# HTTP 200 and ignores a query parameter it doesn't recognise, so a dropped or
# renamed parameter looks exactly like a working filter that happens to change
# nothing. Unit tests can only prove the app *sends* the parameter; only the live
# API can prove it *means* anything. The typo control below is the point of the
# script — if it ever stops differing from the real parameter, the real parameter
# is being ignored too.
set -euo pipefail

API="https://api.inaturalist.org/v1"
# The search that surfaced the problem: three of the top five ranked species were lichens.
REGION="lat=45.326&lng=-122.634&radius=15&month=8&iconic_taxa=Fungi&verifiable=true"
LECANOROMYCETES=54743

# Reporting the comparison is this script's whole job, so an unreachable host must
# not abort the run via `set -e`. The failure is recorded and printed, not swallowed.
total() {
  curl -sS "$1" 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["total_results"])' \
    || echo "unreachable"
}

fail=0
compare() {
  local endpoint="$1" label="$2"
  local base excluded typo
  base=$(total "$API/$endpoint?$REGION&per_page=1")
  excluded=$(total "$API/$endpoint?$REGION&per_page=1&without_taxon_id=$LECANOROMYCETES")
  typo=$(total "$API/$endpoint?$REGION&per_page=1&wthout_taxon_id=$LECANOROMYCETES")

  printf "%-28s baseline=%-8s excluded=%-8s typo-control=%-8s " "$label" "$base" "$excluded" "$typo"
  if [ "$base" = "unreachable" ] || [ "$excluded" = "unreachable" ] || [ "$typo" = "unreachable" ]; then
    echo "SKIPPED (API unreachable)"; fail=1
  elif [ "$excluded" -lt "$base" ] && [ "$typo" = "$base" ]; then
    echo "OK (parameter honoured)"
  else
    echo "FAILED (parameter appears to be ignored)"; fail=1
  fi
}

compare "observations/species_counts" "species_counts"
compare "observations" "observations"

exit "$fail"
