#!/usr/bin/env bash
# Owner, 2026-10-18: restarting a long download brings back its fast start ("repeatedly hitting
# [pause] when it slowed down dropped my wait time significantly"). Observed here the same day:
# 1.3 MB/s just after a restart, 0.15-0.34 MB/s twenty minutes later. So curl gives up whenever
# it averages under SPEED_LIMIT bytes/s for SPEED_TIME seconds, and resumes (-C -) after a short
# pause. A stall is caught the same way. When both files are complete and their MD5s match, it
# runs the build from them (owner: "Build when the download finishes").
set -u
cd /srv/forager-build/sources/osm
UA="forager-map-build/1 (Forager, an open-source foraging app)"
BASE=https://download.geofabrik.de/north-america
SPEED_LIMIT=700000   # bytes/s
SPEED_TIME=40        # seconds
PAUSE=5              # seconds between restarts, so Geofabrik sees at most about one request a minute
MAX_RESTARTS=5000
for r in us-261017 canada-261017; do
  f="$r.osm.pbf"
  want="$(curl -sSIL -A "$UA" "$BASE/$f" | tr -d '\r' | awk 'tolower($1)=="content-length:"{n=$2} END{print n}')"
  [[ "$want" =~ ^[0-9]+$ ]] || { echo "$f: could not read its size from Geofabrik"; exit 1; }
  n=0
  while :; do
    have="$(stat -c %s "$f" 2>/dev/null || echo 0)"
    [[ "$have" -ge "$want" ]] && break
    t0=$(date +%s)
    curl -fL -sS -C - --connect-timeout 30 --speed-limit "$SPEED_LIMIT" --speed-time "$SPEED_TIME" \
      -A "$UA" -o "$f" "$BASE/$f"
    rc=$?
    now="$(stat -c %s "$f" 2>/dev/null || echo 0)"; dt=$(( $(date +%s) - t0 ))
    n=$((n+1))
    echo "$(date '+%F %T') $f: connection $n ended (curl exit $rc) after ${dt}s, +$(( (now-have)/1024/1024 )) MB at $(( (now-have)/(dt>0?dt:1)/1024 )) KB/s; $(( now/1024/1024 )) of $(( want/1024/1024 )) MB"
    [[ $n -ge $MAX_RESTARTS ]] && { echo "$f: $MAX_RESTARTS restarts; giving up"; exit 1; }
    [[ "$now" -ge "$want" ]] || sleep "$PAUSE"
  done
  echo "$(date '+%F %T') $f: complete, $want bytes, after $n connections"
done
md5sum -c us-261017.osm.pbf.md5 canada-261017.osm.pbf.md5 || { echo "MD5 mismatch; not building"; exit 1; }
echo "$(date '+%F %T') both MD5s match; starting build-usca.sh --local 261017"
exec /opt/forager-build/map-build/bin/build-usca.sh --local 261017
