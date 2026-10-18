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
