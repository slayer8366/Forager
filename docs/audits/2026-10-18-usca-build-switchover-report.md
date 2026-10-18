# 2026-10-18: the Pi stops serving, and the weekly build becomes US + Canada

Pi session, 2026-10-18. It continues session `8677907a`, which wrote `build-usca.sh` and stopped
before committing it. This records what was changed on the Pi that day, which exists nowhere in
git except here, and on whose word each change was made. Times are PDT, from the Pi's clock and
journal.

## What changed, in order

| Time | Change | Owner's word |
|---|---|---|
| 12:46 | The owner moved the Pi to the "Virusdownloader" Wi-Fi from the desktop panel (`wf-panel-pi`, NetworkManager audit `connection-activate`, uid 1000). Wi-Fi became the default route (metric 600). Bluetooth tethering to the Tab S8+ stays up at metric 750. | "I had put the Pi on wifi since" (this superseded the morning's "stay on bluetooth") |
| 12:50 | The US + Canada extract download was stopped and restarted. The same command and user, appending to `dl.log`. It resumed from byte 2,468,982,784. | The answer above, to the question of which network to restart on |
| 12:58:26 | `build-usca.sh` and its tests committed and pushed to `pi-build` as `92856ef1` | "Go on 1" |
| 12:58:43 | `forager-tiles.service` and `forager-tunnel.service` stopped and disabled (`systemctl disable --now`) | "no more serving" |
| ~13:09 | `map-build` installed to `/opt/forager-build/map-build` at `92856ef1` by `install-map-build.sh`. The previous copy is kept as `map-build.prev`. | "Switch over the timer to us and Canada" |
| 13:09:53 | `forager-build-usca.service` installed in `/etc/systemd/system/`. The drop-in `/etc/systemd/system/forager-build.timer.d/usca.conf` (`[Timer] Unit=forager-build-usca.service`) is added. `daemon-reload`. | Same |
| 13:12 | The transient unit `forager-build-usca-first.service` is started (`systemd-run`). It waits for the download's loop (pid 7567) to exit. If `dl.log` then holds `us-261017.osm.pbf: OK` and `canada-261017.osm.pbf: OK`, it execs `build-usca.sh --local 261017`. Otherwise it logs the tail of `dl.log` and exits 1 without building. It runs as `forager-build` with the service file's `Nice`, IO, CPU and OOM settings and its main hardening. | "Build when the download finishes" |

The owner also removed the Worker's Pi-origin variable on Cloudflare, so the Worker no longer
asks the Pi first. That was done by the owner, not from the Pi.

## What was checked, and how

- **The stall's cause.** The extract stopped growing at 12:46:32 and curl showed 0 B/s without
  exiting. That was twelve seconds after Wi-Fi took the default route, so curl's connection was
  left on the Bluetooth interface. This is inferred from the timing and the routing table. Nothing
  in curl's output names the cause.
- **The restart.** It resumed rather than restarting: `dl.log` shows "Resuming transfer from
  byte position 2468982784". It ran at about 1.3 MB/s at first, then about 0.34 MB/s on average
  by 13:12.
- **Serving off.** Both units reported `inactive` and `disabled`, and
  `https://origin.zynergy-labs.com/` returned 403. The 403 shows the origin no longer answers with
  tiles. It does not show why.
- **The app's tiles without the Pi.** These were requested through
  `forager-pmtiles.brandonlee1-894.workers.dev` after the owner's Worker change. `us.json` and
  tiles at z7, z9, z10, z11, z12, z14 and z15 all returned 200 with `x-forager-origin: r2`.
  `style/offline.json` returned 200. Two z7 tiles, `us/7/20/44` and `us/7/21/44`, still said
  `pi`, with `cf-cache-status: HIT` and `age` of about 4,456 s. Those are edge-cached copies
  made before the Pi stopped, not live Pi requests.
- **The timer.** `systemctl show forager-build.timer -p Unit` gives
  `forager-build-usca.service`, and `list-timers` shows the next run on Sun 2026-10-25 03:00
  PDT, activating that unit. `systemd-analyze verify` printed nothing for the new service.
- **The waiting unit.** It is `active` and has logged "waiting for the 261017 download (pid 7567)
  to finish".
- **Tests before the commit.** `test_build_usca_functions.sh` passed 15 checks,
  `test_build_functions.sh` passed 14, and `unittest` (mvt_decode, run_record, merge_poly) ran 19
  tests, all passing. The whole script was read again before committing.

## Not verified, and open

- **The build has never run at this size.** Whether a 4 GB heap is enough, how long it takes and
  whether 110 GB of free disk is enough are all unknown until it runs. 150 GB was free at the
  time.
- **The Worker's z0 to z6 and z8 tiles** were not requested. Neither was the offline style's
  content, only its status. The cached `pi` z7 copies were not purged and will age out.
- **On 2026-10-25 the timer's run does not use the 261017 pair.** Without `--local` it asks
  Geofabrik for the newest extracts and downloads about 19 GB again. If the first build is still
  running then, `build-usca.sh`'s `flock` refuses the second, which exits 2.
- **A reboot stops both the download and the waiting unit.** The download resumes with `curl -C
  -`. The waiting unit would have to be started again.
- **The installed copy at `/opt` is `92856ef1`.** This commit changes only comments in
  `forager-build-usca.service` and `map-build/README.md`, so the installed unit's comments are
  out of date. Its directives are unchanged.
- **The owner's answer 3 superseded.** Earlier that day the owner said to switch the timer once
  the first US + Canada build had worked. Their later "Switch over the timer to us and Canada"
  came before that build, and is what was carried out.

## Undo

- **Timer back to Oregon + Washington:** delete
  `/etc/systemd/system/forager-build.timer.d/usca.conf`, then run `systemctl daemon-reload`.
- **Serving back on:** `systemctl enable --now forager-tiles.service forager-tunnel.service`.
- **Cancel the first build:** `systemctl stop forager-build-usca-first`.
- **Earlier map-build:** `/opt/forager-build/map-build.prev`.

## Addendum, 13:15: the download restarts itself when it slows, and the build waits on that

**This supersedes the 13:12 row above.** `forager-build-usca-first.service` was stopped before
it built anything. The download loop it waited on (pid 7567) was stopped too.

**Why.** The owner's suggestion, from experience with long downloads: a restarted download
starts fast and then slows, so restarting it each time it slows cuts the wait ("repeatedly
hitting it when it slowed down dropped my wait time significantly"). The day's own figures
pointed the same way. The 12:50 restart ran at about 1.3 MB/s at first and had fallen to
153 KB/s by 13:14 (measured over 30 s). The waiting unit had to be replaced along with the
download, because it waited on that download's pid. Restarting the download alone would have
left the unit to exit without building.

**What runs now.** The transient unit `forager-usca-fetch-build.service` runs as `forager-build`,
with the same settings as the 13:12 unit. It runs
`/srv/forager-build/sources/osm/fetch-then-build-261017.sh`, committed verbatim as
[`data/2026-10-18-fetch-then-build-261017.sh`](data/2026-10-18-fetch-then-build-261017.sh)
(sha256 `406dabc420d8b12f…` for both copies, checked by `sha256sum` at commit time). For
each extract, it:
- reads the full size from Geofabrik (US 12,212,909,513 bytes, Canada 6,516,972,934);
- runs `curl -C -` with `--speed-limit 700000 --speed-time 40`, so a connection averaging under
  0.7 MB/s for 40 s is dropped;
- pauses 5 s and resumes, until the file reaches its full size, with at most 5,000 connections
  per file;
- logs each connection's length, its gain and its speed to the unit's journal.

When both files are complete, `md5sum -c` must pass on both before it execs `build-usca.sh
--local 261017`. A mismatch exits without building.

**First result, observed.** The first five connections, from 13:15:50 to 13:21:52, lasted 41 to
116 s and ran at 270, 524, 1,056, 787 and 527 KB/s. Together they gained 244 MB in about 362 s,
about 690 KB/s, against 153 KB/s just before. That is five connections in the first six minutes
of one session, not a sustained rate. At that pace both files would finish around 19:00 PDT.
This is a projection, not yet observed.

**Not verified.**
- A connection that never reaches 0.7 MB/s now costs one reconnect every 45 s or so. Whether
  Geofabrik minds that rate is unknown. Nothing from them has been seen.
- The completion branch, size reached → MD5 → exec, has not run yet. The size check uses
  `Content-Length` after redirects. If Geofabrik's redirect serves a different length, the loop
  would end early or never end. The MD5 check catches the first case, not the second.
- A reboot stops this unit. It would have to be started again with the `systemd-run` line from
  this session, which is not committed here.

**Undo.** `systemctl stop forager-usca-fetch-build`. The partial file stays, and any later
`curl -C -` resumes from it.
