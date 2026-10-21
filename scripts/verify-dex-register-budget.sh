#!/usr/bin/env bash
# Guards against the launch crash of RECORD -739 (docs/audits/2026-10-08-launch-verifyerror-report.md):
# a method in the built APK whose Dalvik frame needs more than 256 registers.
#
# What happened. Main at 0e766d94 crashed on every phone at start with a java.lang.VerifyError:
# ART rejected AvailabilityCompactMapUiKt because CompactMapTab "[0x10E] register v0 has type
# Reference: MapMode but expected Integer". PR #203 had added two parameters, taking the method
# from 246 registers to 259. Arguments sit in a method's highest registers, so the three Compose
# `$default` ints landed in v256..v258, which most Dalvik instructions cannot name (their register
# fields are 8 bits). D8 has to move such arguments down before using them, and in this method it
# read v0 (holding mapMode) at 0x10E where `$default0` (v256) should have been moved in. The JVM
# bytecode was correct, so Robolectric, the 4,454-test suite and CI were all green, and only ART's
# verifier on a phone saw it.
#
# REPORT ONLY. NOT A GUARD (RECORD -741). It always exits 0 once it has read the whole APK. It was written as
# a guard and shown not to be one: the first fix grouped CompactMapTab's parameters to 249 registers,
# which this passes, and that build was still rejected by ART on the S22 ("[0x1E20] copy-reference
# v12<-v197 type=BooleanConstant"). The 256-register line is not the cause, only where the first
# symptom showed. The guard is scripts/s22-launch-check.sh, run on the phone. This stays as a cheap
# way to see which methods are large, and how large, when deciding what to split. Not wired into CI.
#
# What this reports. There is no offline ART verifier in the Android SDK (build-tools ships dexdump
# and d8, neither of which verifies the way ART does), so this checks the condition that exposed the
# miscompile rather than the miscompile itself: it lists every app method in the APK's dex files
# with more than 256 registers, marked against the baseline beside this script.
# A method over the line is not necessarily miscompiled -- the ones in the baseline launch fine on
# the S22 at the commit that recorded them -- but every one of them is a frame where D8 has to
# shuffle arguments, which is where this crash came from. Whether to shrink one is the owner's
# decision, not this script's. Set REGISTER_REPORT_LIMIT to list methods over a lower count.
#
# A dump that is cut off fails (RECORD -1516). On this project's laptop, with the work directory on a /tmp tmpfs,
# dexdump's output for the largest dex stopped at about 110 MB, mid-record, and dexdump still exited 0, so the
# script reported ~208,100 methods instead of 226,975 and never looked at the rest. Each dump's class records
# are now counted against the dex header's class_defs_size, the number of classes the file defines; dexdump
# writes one "Class #" record per class def, ending each with its source_file_idx line, so a dump cut anywhere
# has fewer of one or both. method_ids_size is not comparable: it counts every method the file refers to,
# including ones defined in other dex files, and the register lines counted below exist only for methods with
# code (abstract and native ones have none).
#
# Usage: scripts/verify-dex-register-budget.sh [path/to/app-debug.apk]
# Needs dexdump from build-tools: $ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION, or else the
# newest build-tools under $ANDROID_SDK_ROOT (or $ANDROID_HOME).
set -euo pipefail

cd "$(dirname "$0")/.."

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
BASELINE="scripts/dex-register-budget-baseline.txt"
LIMIT="${REGISTER_REPORT_LIMIT:-256}"

[ -f "$APK" ] || { echo "FAIL: no APK at $APK (build it first: ./gradlew assembleDebug)"; exit 1; }
[ -f "$BASELINE" ] || { echo "FAIL: no baseline at $BASELINE"; exit 1; }

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK" ] || { echo "FAIL: neither ANDROID_SDK_ROOT nor ANDROID_HOME is set"; exit 1; }
if [ -n "${BUILD_TOOLS_VERSION:-}" ]; then
  DEXDUMP="$SDK/build-tools/$BUILD_TOOLS_VERSION/dexdump"
else
  DEXDUMP="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -n1)dexdump"
fi
[ -x "$DEXDUMP" ] || { echo "FAIL: dexdump not found at $DEXDUMP"; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf -- "${WORK:?}"' EXIT

unzip -q -o "$APK" 'classes*.dex' -d "$WORK"
dex_count="$(find "$WORK" -name 'classes*.dex' | wc -l)"
[ "$dex_count" -gt 0 ] || { echo "FAIL: no classes*.dex inside $APK"; exit 1; }

# One line per app method over the limit: "<registers> <class>.<method>". The Kotlin mangling
# suffix of a function with value-class parameters (CompactMapTab-ztWZzuM) is dropped, since it
# changes with the signature and the baseline should not. Method count is printed too, so a run
# that read nothing (a dexdump format change, say) cannot pass silently.
scanned=0
: > "$WORK/over.txt"
for dex in "$WORK"/classes*.dex; do
  "$DEXDUMP" "$dex" > "$WORK/dump.txt"
  awk -v limit="$LIMIT" -v out="$WORK/over.txt" -v counter="$WORK/count.txt" -v records="$WORK/records.txt" '
    /^Class #/ { classes++ }
    /^  source_file_idx   :/ { ended++ }
    /^  Class descriptor  :/ { cls = $4; gsub(/^'\''L|;'\''$/, "", cls); gsub(/\//, ".", cls) }
    /^      name          :/ { name = $3; gsub(/'\''/, "", name); sub(/-[A-Za-z0-9_]{7}$/, "", name) }
    /^      registers     :/ {
      n++
      if ($3 > limit && cls ~ /^com\.zynergylabs\./) print $3, cls "." name >> out
    }
    END { print n + 0 > counter; print classes + 0, ended + 0 > records }
  ' "$WORK/dump.txt"
  # The dex header (magic "dex\n" at 0, class_defs_size as a little-endian u32 at 0x60, per the dex format).
  [ "$(head -c 4 "$dex" | od -An -c | tr -d ' ')" = 'dex\n' ] || { echo "FAIL: $(basename "$dex") has no dex magic"; exit 1; }
  class_defs="$(od -An -t u4 -j 96 -N 4 "$dex" | tr -d ' ')"
  read -r classes ended < "$WORK/records.txt"
  if [ "$classes" -ne "$class_defs" ] || [ "$ended" -ne "$class_defs" ]; then
    echo "FAIL: $(basename "$dex"): the dump has $classes class records ($ended complete), the dex header says $class_defs (class_defs_size); dexdump's output was cut off or not understood, so the count would be wrong"
    exit 1
  fi
  scanned=$((scanned + $(cat "$WORK/count.txt")))
done
[ "$scanned" -gt 0 ] || { echo "FAIL: read no methods from $dex_count dex files; dexdump output not understood"; exit 1; }

sort -k2 "$WORK/over.txt" -o "$WORK/over.txt"
echo "Scanned $scanned methods in $dex_count dex files; $(wc -l < "$WORK/over.txt") app methods over $LIMIT registers."

while read -r regs method; do
  base="$(awk -v m="$method" '$1 !~ /^#/ && $2 == m { print $1; exit }' "$BASELINE")"
  if [ -z "$base" ]; then
    echo "  NEW   $method: $regs registers (over $LIMIT, not in the baseline)"
  elif [ "$regs" -gt "$base" ]; then
    echo "  GREW  $method: $regs registers (baseline $base)"
  else
    echo "  OK    $method: $regs registers (baseline $base)"
  fi
done < "$WORK/over.txt"

echo "Report only: this is not a guard (see the header). The launch guard is scripts/s22-launch-check.sh."
