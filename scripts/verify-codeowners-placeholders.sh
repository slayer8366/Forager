#!/usr/bin/env bash
# Fails the build while .github/CODEOWNERS still names a placeholder reviewer.
#
# GitHub does not validate CODEOWNERS entries against real users or teams -- an entry like
# @TODO-motion-owner is simply skipped at review-request time, silently, with no error
# anywhere. So a placeholder handle left in place would not actually require review from
# anyone; it would just look like it does. This script is the forcing function: it fails
# loudly (not a silent warning) until docs/motion-spec.md §6 open question 2 (the named
# motion-owner role) is answered by a human and the placeholder is replaced with a real
# GitHub username or team.
#
# Deliberately not wired into .github/workflows/ci.yml, matching this repo's existing
# scripts/verify-*.sh convention (README.md) of standalone, manually-run checks: ci.yml gates
# every PR in this repo, and this one file's open question shouldn't block unrelated changes
# from merging. Run it by hand, or wire it into a workflow scoped to CODEOWNERS/motion-spec
# changes, once that tradeoff is somebody's explicit call.
set -euo pipefail

CODEOWNERS=".github/CODEOWNERS"

if [ ! -f "$CODEOWNERS" ]; then
  echo "FAILED: $CODEOWNERS not found."
  exit 1
fi

if grep -q '@TODO-' "$CODEOWNERS"; then
  echo "FAILED: $CODEOWNERS still contains a placeholder owner:"
  grep -n '@TODO-' "$CODEOWNERS"
  echo
  echo "Replace the placeholder with a real GitHub username or team (see docs/motion-spec.md" \
       "§6 open question 2) before this can pass."
  exit 1
fi

echo "OK: no placeholder owners in $CODEOWNERS"
