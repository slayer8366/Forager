#!/usr/bin/env bash
# ci-flake (-296) loop runner. Usage: loop.sh <label> <iterations> <gradle --tests args...>
# Per iteration: wait until the machine is above the floor and no other Gradle worker/wrapper runs;
# run; refuse the result if the log shows a compile error; copy that iteration's JUnit XML aside.
set -u
label=$1; n=$2; shift 2
wt=/home/zynergy-labs/Zynergy/forager-wt/ci-flake
out=/tmp/cif/loops/$label; mkdir -p "$out"
tally=$out/tally.tsv; [ -f "$tally" ] || echo -e "iter\thead\texit\tcompile_error\ttests\tfailures\terrors\tskipped\tfailing" > "$tally"
for i in $(seq 1 "$n"); do
  while :; do
    mem=$(free -m | awk '/Mem/{print $7}'); disk=$(df -m / | awk 'NR==2{print $4}')
    other=$(pgrep -f "GradleWorkerMain|Gradle Test Executor|GradleWrapperMain" | grep -v -x "$$" | wc -l)
    [ "$mem" -ge 2048 ] && [ "$disk" -ge 2048 ] && [ "$other" -eq 0 ] && break
    echo "$(date +%T) waiting mem=$mem disk=$disk other=$other" >> "$out/wait.log"; sleep 30
  done
  [ -f /tmp/cif/YIELD ] && { echo "yield requested before iter $i" >> "$out/wait.log"; exit 3; }
  rm -rf "$wt/app/build/test-results/testDebugUnitTest"
  log=$out/iter$i.log
  (cd "$wt" && ${GRADLE_PREFIX:-} ./gradlew ${GRADLE_FLAGS:-} testDebugUnitTest "$@" > "$log" 2>&1); rc=$?
  ce=0; grep -qE "^e: |Compilation error|compileDebug(Unit)?TestKotlin FAILED|compileDebugKotlin FAILED" "$log" && ce=1
  mkdir -p "$out/xml$i"; cp "$wt"/app/build/test-results/testDebugUnitTest/TEST-*.xml "$out/xml$i/" 2>/dev/null
  counts=$(python3 - "$out/xml$i" <<'PY'
import glob,sys,xml.etree.ElementTree as ET
t=f=e=s=0; fl=[]
for p in glob.glob(sys.argv[1]+'/TEST-*.xml'):
    r=ET.parse(p).getroot(); t+=int(r.get('tests'));f+=int(r.get('failures'));e+=int(r.get('errors'));s+=int(r.get('skipped'))
    for c in r.findall('testcase'):
        if c.find('failure') is not None or c.find('error') is not None: fl.append(r.get('name').rsplit('.',1)[-1]+'>'+c.get('name')[:60])
print(f"{t}\t{f}\t{e}\t{s}\t{'; '.join(fl)}")
PY
)
  echo -e "$i\t$(git -C "$wt" rev-parse --short HEAD)\t$rc\t$ce\t$counts" >> "$tally"
  [ "$ce" -eq 1 ] && { echo "compile error in iter $i, stopping" >> "$out/wait.log"; exit 2; }
  sleep 20   # yield between iterations
done
