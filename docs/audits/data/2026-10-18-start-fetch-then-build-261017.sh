#!/usr/bin/env bash
# Starts forager-usca-fetch-build again, for example after a reboot. This is the systemd-run line
# that started it on 2026-10-18 at 13:15 PDT, unchanged. See
# docs/audits/2026-10-18-usca-build-switchover-report.md, addendum. Run as an admin on the Pi.
# The download resumes from the partial files (curl -C -). If both files are already complete, it
# goes straight to the MD5 check and the build.
#
# Don't run it while a build-usca.sh is running. The build's own lock would refuse a second
# build, but the download part would still run first.
set -euo pipefail
if systemctl is-active --quiet forager-usca-fetch-build; then
  echo "forager-usca-fetch-build is already running" >&2; exit 1
fi
sudo systemctl reset-failed forager-usca-fetch-build 2>/dev/null || true
sudo systemd-run --unit=forager-usca-fetch-build --description="Forager: fetch the 261017 US + Canada extracts, restarting when slow, then build-usca.sh --local 261017 (owner, 2026-10-18)" \
  -p User=forager-build -p Group=forager-build -p WorkingDirectory=/srv/forager-build -p Environment=HOME=/srv/forager-build \
  -p Nice=10 -p IOSchedulingClass=idle -p CPUWeight=20 -p OOMScoreAdjust=800 \
  -p NoNewPrivileges=yes -p PrivateTmp=yes -p ProtectSystem=strict -p ProtectHome=yes -p ReadWritePaths=/srv/forager-build \
  /srv/forager-build/sources/osm/fetch-then-build-261017.sh
