#!/usr/bin/env bash
# Verifies network access to the iNaturalist website and public REST API
# (used by the Forager app for species identification/lookup).
set -euo pipefail

check() {
  local url="$1"
  local code
  # Reporting reachability is this script's whole job, so one unreachable host
  # must not abort the remaining checks via `set -e`. The failure is recorded and
  # printed, not swallowed: curl's own diagnostic still goes to stderr (-S) and
  # its exit code is shown in the report line.
  code=$(curl -sS -o /dev/null -w "%{http_code}" "$url") || code="unreachable (curl exit $?)"
  printf "%-55s -> HTTP %s\n" "$url" "$code"
}

check "https://www.inaturalist.org/"
check "https://api.inaturalist.org/v1/taxa?q=morel&per_page=1"
check "https://api.inaturalist.org/v1/observations?per_page=1"
