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
# Exit 0 when there are no NEW failures, 1 when there are, 2 on a usage error, 3 when the results
# could not be read or parsed (see "Parse failures" below). ABSENT never fails
# the check: the record says which are intermittent (group C is MAX_PATH-bound and can pass when
# the random temp suffix is short), and a dropped baseline line is a decision, not a run outcome.
#
# Needs only bash, perl, sort and comm — present on the Windows Git Bash this was written for and
# on Linux CI. On Linux the baseline should come back entirely ABSENT and nothing NEW; that is the
# expected reading there, not an error. The ID format is "classname#method", the method name as
# JUnit records it (backtick names verbatim, with spaces and punctuation).
#
# Parse failures (dispatch 2026-09-28-658, scout items G2/F10). This ran under `set -u` alone, so a
# perl that failed (an unreadable file) or a parse that matched nothing (a JUnit XML whose
# attributes come in another order) left the run side empty, and an empty run side reads as "no
# failures": exit 0, VERDICT clean, on results it never read. Now `pipefail` and `-e` stop it on
# any failed step, and it exits 3, saying why, when: a results file cannot be read (perl -n only
# warns on one and exits 0, so this is checked first); a file holds no <testsuite> element (empty
# or cut short); or the failures the suites' own elements report (failures= plus errors=) do not
# equal the failing <testcase> elements parsed. Each was shown exiting 0 "clean" before this change
# and 3 after it, on a planted input. The `|| true` sites below are deliberate (grep -c and grep
# with no match return 1) and are kept.
set -euo pipefail
# One collation for every sort and every comm below, and it has to be the byte one.
#
# `sort` honours LC_COLLATE; `comm` compares bytes and does not. Under en_US.UTF-8 the collation
# folds case and de-prioritises punctuation, so "...InAppCameraDialogTest#Done dismisses, ..."
# sorts among the lowercase method names while comm expects it before them ('D' is 0x44, 'a' is
# 0x61). Handed sort's order, comm's merge walks off and mis-classifies: measured on this very
# baseline, a run whose single failure WAS a baseline ID came back "NEW: 1" -- a regression that
# is not one -- while the same ID was simultaneously listed under ABSENT, "did not fail", and the
# script exited 1. Two contradictory wrong answers in one report.
#
# A green run cannot surface this: with no failures the run side is empty, so NEW is empty and
# ABSENT is the whole baseline whatever the order, and the verdict is right by accident. It would
# therefore have stayed invisible until the first host that actually had failures -- which is the
# only host whose answer matters. That is why it is fixed here rather than when it next bites.
#
# Exported rather than prefixed onto each call so a sort or comm added later cannot reintroduce
# it. Safe for the data: every ID is ASCII, and the perl below is byte-oriented (no -C flags),
# so its output is unchanged.
export LC_ALL=C
RESULTS_DIR="${1:-app/build/test-results/testDebugUnitTest}"
BASELINE="${2:-docs/windows-host-test-baseline.tsv}"
if [ ! -d "$RESULTS_DIR" ]; then echo "no results directory: $RESULTS_DIR" >&2; exit 2; fi
if [ ! -f "$BASELINE" ]; then echo "no baseline file: $BASELINE" >&2; exit 2; fi
shopt -s nullglob
xml=("$RESULTS_DIR"/TEST-*.xml)
if [ ${#xml[@]} -eq 0 ]; then echo "no TEST-*.xml under $RESULTS_DIR (did the test task run?)" >&2; exit 2; fi

run=$(mktemp); base=$(mktemp); parsed=$(mktemp); trap 'rm -f "$run" "$base" "$parsed"' EXIT

parse_failed() { echo "PARSE FAILED: $1" >&2; echo "VERDICT: none -- the results were not read, so nothing is claimed about them" >&2; exit 3; }

# perl -n only warns on a file it cannot open, and exits 0, so an unreadable file is caught here
# rather than by pipefail.
for f in "${xml[@]}"; do [ -r "$f" ] || parse_failed "cannot read $f"; done

# Every <testcase> that carries a <failure> or <error> child, as classname#name, XML entities decoded.
perl -0ne '
  while (/<testcase name="([^"]*)" classname="([^"]*)"[^>]*>\s*<(?:failure|error)[\s>]/g) {
    my ($n, $c) = ($1, $2);
    for ($n) { s/&quot;/"/g; s/&apos;/'"'"'/g; s/&lt;/</g; s/&gt;/>/g; s/&amp;/&/g; }
    print "$c#$n\n";
  }' "${xml[@]}" > "$parsed" || parse_failed "perl could not read the JUnit XML under $RESULTS_DIR"
sort -u "$parsed" > "$run"

# What the suites themselves report failing, read from each <testsuite> element's own counts. A
# parse that missed a failing <testcase> (a changed attribute order, say) shows up as a shortfall.
# Also counts the <testsuite> elements: one per file, or a file was empty or cut short.
counts=$(perl -0ne '
  while (/<testsuite\b[^>]*>/g) {
    my $t = $&; my ($f) = $t =~ /\bfailures="(\d+)"/; my ($e) = $t =~ /\berrors="(\d+)"/;
    $sum += ($f // 0) + ($e // 0); $suites++;
  }
  END { print(($sum // 0) . " " . ($suites // 0)) }' "${xml[@]}") || parse_failed "perl could not read the <testsuite> counts under $RESULTS_DIR"
reported=${counts% *}; suites=${counts#* }
if [ "$suites" -ne "${#xml[@]}" ]; then
  parse_failed "${#xml[@]} TEST-*.xml file(s) but $suites <testsuite> element(s): one is empty, cut short, or not JUnit XML"
fi
found=$(wc -l < "$parsed" | tr -d ' ')
if [ "$found" -ne "$reported" ]; then
  parse_failed "the suites report $reported failing test(s) but $found were parsed from <testcase> elements"
fi

# Baseline IDs: drop comments and blanks, take the field after the group tab, strip CR.
perl -ne 'next if /^\s*(#|$)/; s/\r?\n$//; my @f = split /\t/, $_, 2; print "$f[1]\n" if defined $f[1];' "$BASELINE" | sort -u > "$base" \
  || parse_failed "perl could not read the baseline $BASELINE"

new=$(comm -23 "$run" "$base"); absent=$(comm -13 "$run" "$base"); overlap=$(comm -12 "$run" "$base" | wc -l | tr -d ' ')
echo "NEW (failing, not in baseline): $(printf '%s' "$new" | grep -c . || true)"; if [ -n "$new" ]; then printf '  %s\n' "$new"; fi
echo "ABSENT (in baseline, did not fail): $(printf '%s' "$absent" | grep -c . || true)"; if [ -n "$absent" ]; then printf '  %s\n' "$absent"; fi
echo "counts: run=$(wc -l < "$run" | tr -d ' ') baseline=$(wc -l < "$base" | tr -d ' ') overlap=$overlap suites=${#xml[@]}"
if [ -n "$new" ]; then echo "VERDICT: NEW failures outside the recorded baseline"; exit 1; fi
echo "VERDICT: no failures outside the recorded baseline"; exit 0
