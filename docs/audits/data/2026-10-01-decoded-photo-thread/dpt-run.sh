#!/bin/bash
# usage: ag-run.sh LABEL [taskset cpus] -- gradle test args...
LABEL=$1; CPUS=$2; shift 2
cd /home/zynergy-labs/Zynergy/forager-wt/decoded-photo-thread || exit 9
EV=~/Zynergy/forager-wt/decoded-photo-thread-evidence/$LABEL; mkdir -p $EV
AV=$(df -m . | tail -1 | awk '{print $4}')
[ "$AV" -ge 2048 ] || { echo "DISK $AV < 2048"; exit 8; }
if pgrep -f "^[^ ]*java .*(Gradle Test Executor|GradleWrapperMain)" >/dev/null; then echo "OTHER GRADLE RUNNING"; exit 7; fi
rm -rf app/build/test-results/testDebugUnitTest
export LC_ALL=C.UTF-8
export DPT_LOG_DIR=$EV; rm -f $EV/probe.log
if [ "$CPUS" != "none" ]; then PIN="taskset -c $CPUS"; else PIN=""; fi
$PIN ./gradlew --no-daemon --console=plain testDebugUnitTest "$@" > $EV/build.log 2>&1; echo "EXIT $?" >> $EV/build.log
E=$(grep -c "^e: " $EV/build.log)
cp app/build/test-results/testDebugUnitTest/TEST-*.xml $EV/ 2>/dev/null
python3 - $EV $E <<'P'
import glob,sys,xml.etree.ElementTree as ET
ev,e=sys.argv[1],sys.argv[2]
t=f=er=s=0;ts=set();bad=[]
for x in sorted(glob.glob(ev+'/TEST-*.xml')):
    r=ET.parse(x).getroot(); t+=int(r.get('tests'));f+=int(r.get('failures'));er+=int(r.get('errors'));s+=int(r.get('skipped'));ts.add(r.get('timestamp'))
    for tc in r.findall('testcase'):
        for k in ('failure','error'):
            e2=tc.find(k)
            if e2 is not None: bad.append(tc.get('classname').split('.')[-1]+': '+tc.get('name')[:80]+' -> '+(e2.get('message') or '')[:160].replace('\n',' '))
print(f"files={len(glob.glob(ev+'/TEST-*.xml'))} tests={t} fail={f} err={er} skip={s} compile_e_lines={e} ts={sorted(ts)[:1]}..{sorted(ts)[-1:]}")
for b in bad: print('  ',b)
P
tail -2 $EV/build.log | head -1
