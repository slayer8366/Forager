# 2026-10-20 pi-09, Phase 1: premises re-checked, prediction, and the numbers for D and F

From the Pi coder on dispatch `2026-10-20-pi-09` (`prompts/preserved/2026-10-20-pi-09.md`), on
branch `pi-disk-fit`, cut from `origin/pi-build` at `cb3307e2` (verified against the remote on
2026-10-20 before any work). Written before any code. Each claim is marked **read** (in a file,
with location), **observed** (seen on the machine), **measured** (a number this session produced),
or **inferred**.

No premise was found false, so Phase 2 follows. Two premises needed a closer reading, and one
measured fact changes how F is built; all three are below.

## Premises

| Premise in the dispatch | Finding |
|---|---|
| `curl_resume` downloads "into the card, with a symlink at the same name in `sources/osm/`, as today" | **"As today" is the disk, not the script.** The script still downloads straight into `$SRC/osm/` (read: `build-usca.sh:218-223`, `:290`). The card-plus-link layout exists only because the 261017 pair was moved by hand on 2026-10-18 21:05 (observed: `ls -la /srv/forager-build/sources/osm/`, both `.pbf` are links into `/mnt/archive/forager-build/sources-osm/`, the `.md5` files are regular files beside them). B makes the script produce the layout the disk already has. |
| The old-extract cleanup removes only the link | **True.** `rm -f -- "$f" "$f.md5" "$f.headers"` with `$f` the link (read: `build-usca.sh:565-574`); `rm` on a symlink removes the link. |
| `$merged` is under the run directory, removed after Planetiler, and `--basemap` skips the merge | **True** (read: `build-usca.sh:348`, `:392`, `:351`). |
| The fixed floor uses `df -BG` and calls it GB | **True** (read: `build-usca.sh:344-347`). `df -BG` is GiB and rounds up. |
| `ProtectSystem=strict` leaves `/mnt/archive` read-only to the unit | **True** (read: `forager-build-usca.service:35-37`, only `/srv/forager-build` is writable). |
| The ratios 5.55×, 1.81×, about 0.15× | **True as stated, with a rounding caveat (below).** Read from `logs/usca-20261019T011311Z.log`: `features 104GB` (`:9537`), `archive 33GB` (`:9536`), `2.8G used for multipolygon way geometries` (`:208`). |
| The Bluetooth tether runs about 0.2 MB/s | **Consistent.** The first plain-curl stretch in `sources/osm/dl.log` averaged 172 KB/s over 1924 s (measured from its progress meter). The Pi is on the tether now: the default route is the Bluetooth PAN interface, and `wlan0` is down (observed, `ip route`, `ip -br link`). |
| Whether curl's `--retry` resumes with `-C -` is not verified | **Measured now: it does not.** See F. |

### What was not in the dispatch and matters

- **Planetiler's node location cache was 19G** (read: the same log, `:202`, `:207`). It is not in
  the dispatch's ratio list. It is a pass-1/pass-2 file; at the end of `osm_pass2` the feature file
  was 73 GB (fourth addendum), so that phase needs about 19 + 73 + 2.8 = 95 GB, under the archive
  phase's 104 + 34 + 2.8. That it is freed before the sort is **inferred**, not read in Planetiler's
  source. The archive phase sets the peak, so the node cache is left out of D, and this note says so.
- **Planetiler's printed sizes are SI and truncated.** The published archive is 33,973,424,728
  bytes (observed, `stat`), and Planetiler printed it as `33GB`: 1000-based and rounded down, not
  GiB (which would be 31.6). So `features 104GB` means 104 to 105 × 10⁹ bytes (inferred from that
  one comparison).
- **`ip route` cannot run under the weekly unit.** `RestrictAddressFamilies=AF_UNIX AF_INET
  AF_INET6` leaves out `AF_NETLINK`. Measured in a transient unit with that restriction: `ip route
  show default` exited 1, while reading `/proc/net/route` found the default route and `getent
  hosts download.geofabrik.de` resolved. So G reads `/proc/net/route` and `/proc/net/ipv6_route`.
- **The 2026-10-18 restart loop ran on Wi-Fi, not Bluetooth.** `wlan0` was activated from 12:46:30
  to 18:22 PDT (observed, NetworkManager's journal); the loop ran 13:15 to 18:12. Its 155
  connections averaged 900 KB/s (measured from its journal lines: 14,955 MB in 17,023 s; median
  connection 908 KB/s, 10th percentile 527). Its 700 kB/s floor was a Wi-Fi floor.

## The numbers proposed for D (all pinned in `pins.env`)

Base: the sum of the inputs' bytes, `I`. For 261017, I = 12,212,909,513 + 6,516,972,934 =
**18,729,882,447** bytes (observed, `stat` of the card files; equal to Geofabrik's sizes in the
restart loop's "complete" lines). The merged extract was about 18.7 GB, so `I` is used for it too
(osmium drops the border objects the two share, so the merge is no larger than the sum: inferred).

| Pin | Value | Source |
|---|---|---|
| `USCA_DISK_FEATURES_RATIO` | 5.61 | 105 × 10⁹ / I = 5.606, the top of the 104GB that Planetiler printed (truncated). 104 × 10⁹ / I = 5.553 is the dispatch's 5.55; 5.55 could undercount by up to 1 GB. |
| `USCA_DISK_ARCHIVE_RATIO` | 1.82 | 33,973,424,728 / I = 1.814, rounded up. |
| `USCA_DISK_MULTIPOLYGON_RATIO` | 0.16 | 2.9 × 10⁹ / I = 0.155, the top of the printed 2.8G, rounded up. Kept although that file may be gone by the archive phase: conservative by about 3 GB. |
| `USCA_DISK_MARGIN_PERCENT` | 10 | Of the computed need. The ratios come from one run; 10% of 142 GB is 14 GB, which covers the truncation above several times over. How much the ratios move week to week is not measured. |
| `USCA_DISK_MARGIN_MIN_BYTES` | 5,000,000,000 | So a run whose own need is small (fetch-only, or the card's share of a `--basemap` run) still leaves room for the logs and the rest of the Pi. |
| `USCA_BASEMAP_RUN_MIN_FREE_BYTES` | 118,111,600,640 | Today's 110 GiB floor, kept for `--basemap` runs only, in bytes. A `--basemap` run has no Planetiler, so the ratios do not apply, and its routing stage's disk need has never been measured. Keeping today's floor for it is the only choice that does not change `--basemap` behaviour (a stop in the dispatch). |

The checks: on the NVMe (`FB_SRV`), a Planetiler run needs (5.61 + 1.82 + 0.16) × I = 7.59 × I
plus the margin. On the card, a run needs the bytes still to download (Content-Length minus what
is already there) plus I for the merged extract (not for `--basemap` or `--fetch-only`), plus the
margin. Run at the state stage, from Geofabrik's HEAD Content-Length, or from the files' sizes for
`--local`; and again at the merge, from the files. All figures in bytes; messages say "bytes" and
give GB as 10⁹ bytes.

## The numbers proposed for F (pinned in `pins.env`)

| Pin | Value | Source |
|---|---|---|
| `USCA_DOWNLOAD_SPEED_LIMIT` | 50000 (bytes/s) | A quarter of the tether's 0.2 MB/s. |
| `USCA_DOWNLOAD_SPEED_TIME` | 120 (s) | Replayed against `dl.log`'s two plain-curl stretches (measured, from the per-second progress lines): at 50 kB/s over 120 s the 172 KB/s stretch trips only inside its final 229 s with no progress at all (first trip 88 s into it), and the 319 KB/s stretch never trips. At 700 kB/s over 40 s, 1842 of 1884 windows trip, which is the "abort every 40 s" the dispatch warns of. At 100 kB/s over 40 s the faster stretch trips 37 times. |
| `USCA_DOWNLOAD_PAUSE` | 10 (s) | Between connections. The 2026-10-18 loop used 5. 10 keeps a connection that fails at once (an HTTP error) to at most six a minute against Geofabrik. |
| `USCA_DOWNLOAD_MAX_RESTARTS` | 500 (per file) | The Wi-Fi loop needed 90 and 65 connections at a 700 kB/s floor; at 50 kB/s restarts should come only on stalls. 500 bounds a dead mirror at about 83 minutes of fast failures. Replaces `USCA_DOWNLOAD_RETRIES`, which loses its only reader. |

**curl's own `--retry` with `-C -`, measured against a small local HTTP server** (Range-capable,
dropping or stalling the first two responses after 300,000 bytes; curl 7.88.1): every retry
re-sent the offset curl found when it started, not where the transfer had got to. With no partial
file, all three requests had no Range header; with a 100,000-byte partial, all three asked for
`bytes=100000-`. The file still came out right because curl rewinds the output, so this was
invisible. A plain loop of `curl -C -` resumed properly: `Range: none`, then `bytes=300000-`, then
`bytes=600000-`. So **today's `curl_resume` re-downloads everything since its start after each
interruption** (measured on the small file; that the same happens on a 12 GB Geofabrik download is
inferred). F therefore drops `--retry` and does every resume in its own loop.

**The trade-off this floor makes (for the planner and owner, not decided here).** On Wi-Fi, the
owner's restart trick worked because a fresh connection ran at about 1 MB/s and a slowed one at
0.15 to 0.34 MB/s. A slowed Wi-Fi connection runs at the same speed as the tether's normal one, so
no single floor can restart the first without aborting the second. A 50 kB/s floor is safe on both
links and catches stalls on both, but gives up the Wi-Fi speed-up. A per-link floor (700 kB/s over
40 s when the default route is `wlan0`, 50 kB/s over 120 s otherwise) would keep it, at the cost of
a link check whose wrong answer means a floor that aborts every 40 s on the tether. The Pi is on
the tether now, so only the single floor is built.

## G

Wait up to `USCA_NETWORK_WAIT_SECONDS=3600`, polling every `USCA_NETWORK_POLL_SECONDS=15`, until a
default route is in `/proc/net/route` or `/proc/net/ipv6_route` and `getent hosts
download.geofabrik.de` answers. On timeout, `die` naming what was missing. The unit already has
`After=network-online.target`, and the 2026-10-11 and 10-18 firings still failed to resolve the
host (fourth addendum), so the target alone is not enough. An hour covers a tether or DHCP
reconnect; past that a person is needed. `--local` skips it.

## Prediction, written before building

- **Mechanism:** with B and C, nothing the run writes to the NVMe at the Planetiler archive stage
  is outside Planetiler's own files, so the NVMe low point falls from about 175-179 GB of need
  (pi-06) to about 7.59 × I = 142 GB. The card carries one week's extracts at rest, and during a
  run the new pair and the merged extract on top: 18.7 + 18.7 + 18.7 = 56 GB of its 125.6.
- **Cost (inferred, from pi-06's card speeds):** writing the merge to the card at 58 MB/s is about
  5½ minutes, and Planetiler's two reads of it at 96 MB/s about 6½ minutes more. Planetiler reads
  OSM blocks lazily in worker threads, so its reads may be less sequential than that assumes.
- **Sunday's margin under the new layout (inferred):** the NVMe has 146,510,110,720 bytes free now
  (observed, `df -B1 /`). The check would ask for 7.59 × I = 142.2 × 10⁹ plus 10% = 156.4 × 10⁹,
  so **Sunday's run would be refused at the state stage, about 9.9 GB short, unless the leftovers
  are cleared first.** Clearing them (19.86 GB, fourth addendum) gives about 166.4 × 10⁹ free: the
  check passes with 10.0 × 10⁹ to spare, and the real headroom at the low point is about 24.2 ×
  10⁹. Without the cleanup the run would have about 4.4 × 10⁹ of real headroom, inside the
  uncertainty of the ratios, which is why the check refuses it.
- **Tests:** the new checks fail first because the functions they call do not exist yet
  (`command not found`), or, for the changed `curl_resume` and cleanup, because the old code puts
  the file on the NVMe and leaves the card file behind.
