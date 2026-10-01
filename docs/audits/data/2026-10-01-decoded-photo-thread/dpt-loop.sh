#!/bin/bash
# usage: dpt-loop.sh PREFIX START END CPUS   (runs PREFIX-START .. PREFIX-END; stops on a DISK / OTHER GRADLE refusal or a STOP file)
P=$1; A=$2; B=$3; C=$4
T="--tests com.zynergylabs.forager.app.ui.log.DecodedPhoto* --tests com.zynergylabs.forager.app.ui.log.JournalPendingDeleteTest --tests com.zynergylabs.forager.app.ui.log.JournalTabTest --tests com.zynergylabs.forager.app.ui.log.scratch.*"
for i in $(seq $A $B); do
  [ -e ~/Zynergy/forager-wt/decoded-photo-thread-evidence/STOP ] && { echo "STOPFILE before $P-$i"; exit 4; }
  echo "### $P-$i $(date -u +%FT%TZ)"
  OUT=$(~/Zynergy/forager-wt/decoded-photo-thread-evidence/dpt-run.sh $P-$i $C $T)
  echo "$OUT"
  if echo "$OUT" | grep -q "^DISK\|^OTHER GRADLE"; then echo "STOPPED $P-$i"; exit 3; fi
done
echo LOOPDONE
