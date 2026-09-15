#!/usr/bin/env bash
# Compare a unit-test run's failures against the recorded Windows-host baseline, by test ID.
#
#   scripts/compare-test-baseline.sh [RESULTS_DIR] [BASELINE_FILE]
#
# RESULTS_DIR   defaults to app/build/test-results/testDebugUnitTest (Gradle's JUnit XML).
# BASELINE_FILE defaults to docs/windows-host-test-baseline.tsv.
#
# Prints three sections and a one-line verdict:
#   NEW      failing IDs not in the baseline  -> a regression, or a new test that fails on this host
#   ABSENT   baseline IDs that did not fail    -> fixed, renamed, removed, or an intermittent one passing
#   counts   failures in the run, in the baseline, and the overlap
# Exit 0 when there are no NEW failures, 1 when there are, 2 on a usage error. ABSENT never fails
# the check: the record says which are intermittent (group C is MAX_PATH-bound and can pass when
# the random temp suffix is short), and a dropped baseline line is a decision, not a run outcome.
#
# Needs only bash, perl, sort and comm — present on the Windows Git Bash this was written for and
# on Linux CI. On Linux the baseline should come back entirely ABSENT and nothing NEW; that is the
# expected reading there, not an error. The ID format is "classname#method", the method name as
# JUnit records it (backtick names verbatim, with spaces and punctuation).
set -u
RESULTS_DIR="${1:-app/build/test-results/testDebugUnitTest}"
BASELINE="${2:-docs/windows-host-test-baseline.tsv}"
if [ ! -d "$RESULTS_DIR" ]; then echo "no results directory: $RESULTS_DIR" >&2; exit 2; fi
if [ ! -f "$BASELINE" ]; then echo "no baseline file: $BASELINE" >&2; exit 2; fi
shopt -s nullglob
xml=("$RESULTS_DIR"/TEST-*.xml)
if [ ${#xml[@]} -eq 0 ]; then echo "no TEST-*.xml under $RESULTS_DIR (did the test task run?)" >&2; exit 2; fi

run=$(mktemp); base=$(mktemp); trap 'rm -f "$run" "$base"' EXIT

# Every <testcase> that carries a <failure> or <error> child, as classname#name, XML entities decoded.
perl -0ne '
  while (/<testcase name="([^"]*)" classname="([^"]*)"[^>]*>\s*<(?:failure|error)[\s>]/g) {
    my ($n, $c) = ($1, $2);
    for ($n) { s/&quot;/"/g; s/&apos;/'"'"'/g; s/&lt;/</g; s/&gt;/>/g; s/&amp;/&/g; }
    print "$c#$n\n";
  }' "${xml[@]}" | sort -u > "$run"

# Baseline IDs: drop comments and blanks, take the field after the group tab, strip CR.
perl -ne 'next if /^\s*(#|$)/; s/\r?\n$//; my @f = split /\t/, $_, 2; print "$f[1]\n" if defined $f[1];' "$BASELINE" | sort -u > "$base"

new=$(comm -23 "$run" "$base"); absent=$(comm -13 "$run" "$base"); overlap=$(comm -12 "$run" "$base" | wc -l | tr -d ' ')
echo "NEW (failing, not in baseline): $(printf '%s' "$new" | grep -c . || true)"; [ -n "$new" ] && printf '  %s\n' "$new"
echo "ABSENT (in baseline, did not fail): $(printf '%s' "$absent" | grep -c . || true)"; [ -n "$absent" ] && printf '  %s\n' "$absent"
echo "counts: run=$(wc -l < "$run" | tr -d ' ') baseline=$(wc -l < "$base" | tr -d ' ') overlap=$overlap suites=${#xml[@]}"
if [ -n "$new" ]; then echo "VERDICT: NEW failures outside the recorded baseline"; exit 1; fi
echo "VERDICT: no failures outside the recorded baseline"; exit 0
