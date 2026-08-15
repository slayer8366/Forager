#!/usr/bin/env bash
# Verifies network access to the iNaturalist website and public REST API
# (used by the Forager app for species identification/lookup).
set -euo pipefail

check() {
  local url="$1"
  local code
  code=$(curl -sS -o /dev/null -w "%{http_code}" "$url")
  printf "%-55s -> HTTP %s\n" "$url" "$code"
}

check "https://www.inaturalist.org/"
check "https://api.inaturalist.org/v1/taxa?q=morel&per_page=1"
check "https://api.inaturalist.org/v1/observations?per_page=1"
