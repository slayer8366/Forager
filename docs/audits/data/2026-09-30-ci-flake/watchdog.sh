#!/usr/bin/env bash
# ci-flake (-296) stall watchdog: runs beside ONE full-suite run of mine. If the test-results
# directory gains no new TEST-*.xml for 300 s, jstack my own test worker (the Gradle Test Executor
# whose cwd is the ci-flake worktree's app dir) into the data dir, once per stall, and keep watching.
# Never kills anything. Exits when the marker file /tmp/cif/suite-done exists.
set -u
label=$1
wt=/home/zynergy-labs/Zynergy/forager-wt/ci-flake
res=$wt/app/build/test-results/testDebugUnitTest
out=/tmp/cif/stalls; mkdir -p "$out"
last=0; since=$(date +%s); dumped=0
while [ ! -f /tmp/cif/suite-done ]; do
  n=$(ls "$res"/TEST-*.xml 2>/dev/null | wc -l)
  now=$(date +%s)
  if [ "$n" != "$last" ]; then last=$n; since=$now; dumped=0; fi
  if [ $((now - since)) -ge 300 ] && [ "$dumped" -eq 0 ]; then
    for p in $(pgrep -f "Gradle Test Executor"); do
      cwd=$(readlink /proc/$p/cwd 2>/dev/null)
      case "$cwd" in "$wt"*)
        f=$out/$label-stall-$(date +%H%M%S)-pid$p.txt
        { echo "xml files: $n; no new file for $((now - since)) s; cwd $cwd"; ps -o pid,etime,time,args -p $p | cut -c1-200;
          /home/zynergy-labs/.local/jdk/temurin-21/bin/jstack -l $p; } > "$f" 2>&1
        echo "$(date +%T) dumped $f" >> "$out/watchdog.log" ;;
      esac
    done
    dumped=1
  fi
  sleep 15
done
