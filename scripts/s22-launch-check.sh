#!/usr/bin/env bash
# Pre-merge launch check on the test phone (RECORD -739/-741): installs an APK on the S22, has ART
# verify it, launches it, and fails if the app crashed.
#
# Why this exists. Main at 0e766d94 crashed on every phone at start: ART's verifier rejected
# CompactMapTab's dex code (a java.lang.VerifyError at class load), while the 4,454 Robolectric tests
# and CI stayed green, because they run the JVM bytecode and never the dex. The Android SDK has no
# offline ART verifier, so the only check that sees what a phone sees is a phone. Run this before
# merging any change to UI code (anything under app/src/main/java/.../ui), and paste its output into
# the dispatch report. It is not wired into CI: CI has no phone, and adding an emulator to CI is the
# owner's decision.
#
# Usage: scripts/s22-launch-check.sh [path/to/app-debug.apk]
# Device: the S22 only (R5CT321008R). Install is `install -r` only: the phone's data is kept. This
# script never uninstalls, clears data or deletes files, and never talks to any other device.
#
# What it checks, in order, failing at the first problem:
#   1. the S22 is attached;
#   2. `adb install -r` succeeds;
#   3. `cmd package compile -m verify -f` succeeds and dexopt then reports status=verify, which is
#      ART verifying every class ahead of time (a class it rejects is verified again at run time and
#      throws there, so step 4 is what catches a rejection; this step makes the phone do the work now);
#   4. a cold launch of MainActivity, then LAUNCH_WAIT_SECONDS (default 8) of running, leaves the app's
#      process alive and the crash buffer empty, and logcat holds no VerifyError for the app.
# Shown failing against the 0e766d94 build before it was trusted: see
# docs/audits/2026-10-08-launch-verifyerror-report.md.
set -uo pipefail

cd "$(dirname "$0")/.."

SERIAL=R5CT321008R
PKG=com.zynergylabs.forager.app
ACTIVITY="$PKG/.MainActivity"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
WAIT="${LAUNCH_WAIT_SECONDS:-8}"
ADB=(adb -s "$SERIAL")

fail() { echo "FAIL: $*"; exit 1; }

[ -f "$APK" ] || fail "no APK at $APK (build it first: ./gradlew assembleDebug)"
# Outputs are captured before they are searched: with pipefail, `grep -q` closing a pipe early can
# fail the producer with SIGPIPE and turn a match into a false FAIL.
attached="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
grep -qx "$SERIAL" <<<"$attached" \
  || fail "the S22 ($SERIAL) is not attached; this check runs on no other device"

echo "APK: $APK"
echo "Device: $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r') ($SERIAL)"

install_out="$("${ADB[@]}" install -r "$APK" 2>&1)"
grep -q '^Success' <<<"$install_out" || { printf '%s\n' "$install_out"; fail "install -r did not succeed (an APK older than the installed one is refused: build from the current branch)"; }
echo "Install: Success"
echo "Installed: $("${ADB[@]}" shell dumpsys package "$PKG" | grep -m1 versionName | tr -d '\r' | sed 's/^ *//')"

"${ADB[@]}" logcat -c || fail "could not clear logcat"

compile_out="$("${ADB[@]}" shell cmd package compile -m verify -f "$PKG" 2>&1 | tr -d '\r')"
echo "compile -m verify -f: $compile_out"
[ "$compile_out" = "Success" ] || fail "compile -m verify did not succeed"
dexopt_out="$("${ADB[@]}" shell dumpsys package dexopt | tr -d '\r')"
dexopt_status="$(grep -A3 "^  \[$PKG\]" <<<"$dexopt_out" | grep -o 'status=[a-z-]*' | head -n1)"
echo "dexopt: ${dexopt_status:-<none found>}"
[ "$dexopt_status" = "status=verify" ] || fail "dexopt does not report status=verify after compiling"

"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -W -n "$ACTIVITY" | tr -d '\r' | grep -E '^(Status|LaunchState|Complete)' || true
sleep "$WAIT"

crash="$("${ADB[@]}" logcat -d -b crash | tr -d '\r')"
verify_lines="$("${ADB[@]}" logcat -d | tr -d '\r' | grep -E "VerifyError|Verifier rejected class" | grep -F "$PKG" || true)"
pid="$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r')"

failed=0
if [ -n "$crash" ]; then
  echo "Crash buffer is not empty:"
  printf '%s\n' "$crash" | head -n 20
  failed=1
fi
if [ -n "$verify_lines" ]; then
  echo "Verifier rejections in logcat:"
  printf '%s\n' "$verify_lines" | cut -c1-400 | head -n 5
  failed=1
fi
if [ -z "$pid" ]; then
  echo "The app's process is not running ${WAIT} s after launch."
  failed=1
fi
[ "$failed" -eq 0 ] || fail "the app did not launch cleanly on the S22"
echo "PASS: verified (status=verify), launched, process $pid alive after ${WAIT} s, crash buffer empty"
