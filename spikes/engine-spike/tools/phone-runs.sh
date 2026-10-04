#!/usr/bin/env bash
# phone-runs.sh <engine> <route> <runs> [profile]: each run in a fresh process of the spike app on the S22,
# its EngineSpike line appended to runs.jsonl. The phone in aeroplane mode for these (the owner's step).
set -u
S=R5CT321008R; P=com.zynergylabs.enginespike; E=~/Zynergy/device-evidence/2026-10-04-engine-spike
engine=$1; route=$2; runs=$3; profile=${4:-hiking-mountain}
for run in $(seq 1 "$runs"); do
  adb -s $S shell am force-stop $P
  adb -s $S logcat -c
  adb -s $S shell am start -W -n $P/.SpikeActivity --es engine "$engine" --es route "$route" --es run "$run" --es profile "$profile" >/dev/null
  line=""
  for i in $(seq 1 240); do
    line=$(adb -s $S logcat -d -s EngineSpike:I | grep -o '{.*}' | tail -1)
    [ -n "$line" ] && break
    sleep 0.5
  done
  [ -z "$line" ] && line="{\"engine\":\"$engine\",\"route\":\"$route\",\"run\":\"$run\",\"error\":\"no result within 120 s\"}"
  echo "$line" | tee -a $E/${RUNS_FILE:-runs.jsonl}
done
