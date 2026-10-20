# 2026-10-20 pi-11: a download speed floor for each link (not installed)

From a Pi coder on dispatch `2026-10-20-pi-11` (`prompts/preserved/2026-10-20-pi-11.md`). The
owner's word, relayed verbatim: "Build the Wi-Fi speed floor". Branch `pi-link-floor`, cut from
`origin/pi-build` `0cbfcd2b`, which was checked against the remote before any work. Nothing is
installed. `/opt`, `/etc`, the units, the timer, `/srv/forager-build`'s data and the card were
only read, never written, and nothing was downloaded from Geofabrik. No index row is added to
`docs/audits/README.md`, which is a serialization point; the planner records it. Each claim is
marked **read** (in a file, with its location), **observed** (seen on the machine), **measured**
(a number this session produced) or **inferred**.

## Premises

| Premise | Finding |
|---|---|
| `curl_resume` passes one floor to every connection | **True** (read: `build-usca.sh:253` at `0cbfcd2b`). |
| G reads `/proc/net/route`, not `ip route` | **True** (read: `has_default_route`, `:376-382`). It takes the path as an argument and has no variable. |
| "Which link carries the default route" | **Incomplete.** The Pi's table holds two IPv4 default routes at once: Wi-Fi at metric 600 and the tether at 750 (observed, `/proc/net/route`; `ip route get` picks the Wi-Fi one). So the route that counts is the one with the lowest metric, not the first listed. This is built in and tested. |
| The 2026-10-18 loop ran on Wi-Fi | **True** (observed, NetworkManager's journal). Wi-Fi became the IPv4 and IPv6 default at 12:46:31, and the tether became the IPv4 default again at 18:22:24. The loop ran from 13:15:50 to 18:12:19. |
| "153 KB/s without restarts" | **A 30-second point rate, not an average** (read: `2026-10-18-usca-build-switchover-report.md:85-86`, "fallen to 153 KB/s by 13:14 (measured over 30 s)"). The whole unrestarted Wi-Fi connection averaged 326,651 bytes/s (below). |

## The Wi-Fi numbers: 700000 bytes/s over 40 s, kept

**From the loop's journal** (measured from its 155 "connection N ended" lines):

- 153 connections were ended by the floor (curl exit 28) and 2 completed.
- They moved 14,955 MiB in 17,023 s of connections. The per-line MB are truncated, so this
  slightly undercounts.
- Over the wall clock, with its 5 s pauses, that is 881,575 bytes/s. With this script's 10 s
  pause, the same connections would make 845,225 bytes/s (modelled, not run).
- The median connection ran at 910 KiB/s and the 10th percentile at 563. Connections lasted 17 to
  296 s, with a median of 106 s.
- The floor tripped once every 116 s on average.
- 26 connections averaged under 700,000 bytes/s. Eight of them ended at 41 to 42 s: they were
  under the floor from their start, at 170 to 470 KiB/s.

**From `dl.log`'s second stretch, the 12:50 restart, with no restarts** (measured from its
per-second Current Speed column):

- It started four minutes after Wi-Fi took both default routes, so it ran on Wi-Fi (inferred).
- It ran 1,520 s at 326,651 bytes/s on average. Its current speed was at or over 700,000 in only
  87 of those 1,520 s.
- The 700000/40 floor would first have ended it at 60 s. The 50000/120 floor never would have.

**How often it trips, and whether it aborts a healthy connection.** On the 10-18 Wi-Fi it tripped
once every 116 s. curl ends a connection only after its speed has stayed under 700,000 bytes/s
for 40 s straight. Any such connection is slower than what the restart regime averages with this
script's pause, 845,225 bytes/s. So restarting it is the better bet in expectation (inferred from
averages).

**Not determinable from these logs:** whether a connection that was given up would have
recovered by itself. Per-second data exists only for slow Wi-Fi connections. So no other floor
can be scored against this one, and none is proposed without data (CLAUDE.md, Building).

**The first stretch of `dl.log` (172 KB/s).** It ends with 229 s of no progress. If it ran right
up to the 12:50 restart, that stall began at about 12:46, when Wi-Fi took the default route.
That fits the Pi's record that a network switch stalls curl. **Inferred:** the gap between the two
stretches was not recorded.

## What was built

| Commit | What |
|---|---|
| `4fc42005` | Tests first, committed failing. Its message holds the prediction and the numbers. |
| `b9fcebca` | The change, `pins.env`, the README, and the test for a floor that cannot be chosen. |
| (this commit) | `default_route_iface` reduced to the guard that a revert check showed bites. This record. |

The change:

- `default_route_iface` reads `USCA_ROUTE_FILE` (default `/proc/net/route`) and prints the
  interface of the IPv4 default route with the lowest metric.
- `download_floor` is called by `curl_resume` before every connection. It picks one of two
  floors:
  - an interface matching `USCA_WIFI_IFACE_PATTERN='wl*'` gets `USCA_DOWNLOAD_SPEED_LIMIT_WIFI=700000`
    over `USCA_DOWNLOAD_SPEED_TIME_WIFI=40`;
  - any other interface, no default route, or a table that cannot be read gets 50000 over 120.
- Each connection's line ends with one of:
  - `; link IFACE, Wi-Fi floor N bytes/s over T s`;
  - `; link IFACE, default floor ...`;
  - `; no default route in FILE, default floor ...`;
  - `; cannot read FILE, default floor ...`.
- A floor that cannot be chosen stops the download before curl runs.

**Failing first** (observed, the tests of `4fc42005` against `0cbfcd2b`'s script):

- `test_build_usca_disk.sh`: 14 failed, 74 passed. The reasons:
  - the two new functions were undefined;
  - the Wi-Fi pins were absent;
  - curl was given 50000/120 on Wi-Fi tables;
  - the lines named no link.
- Five disk checks passed by design. They check that the conservative pins are kept, and that the
  tether, no-route and after-switch connections still get 50000/120. They guard against
  over-reach, not for the change.
- `test_build_usca_run.sh`: 2 failed (both new checks), 36 passed.
- The missing-pin check failed on the unguarded draft (1 failed, 88 passed). curl had been called
  four times with `--speed-limit "" --speed-time ""`.

## Revert checks

The runner, step by step:

1. Save a copy of the script and its sha256.
2. Apply one edit by exact match. It must occur exactly once.
3. Run `bash -n` on the reverted script. Results are refused if it does not parse.
4. Run the disk and run tests.
5. Restore the script from the saved copy, never from git.
6. Compare the restored script's sha256 with the copy's, and confirm the forward text is present.

Every check below parsed, restored with a matching sha256 (`e9040c49dde0`) and had its forward
text present. All were run on the final tree.

| Behaviour | Edit | Failures read |
|---|---|---|
| Wi-Fi gets the Wi-Fi floor | `[[ "$ifc" == $USCA_WIFI_IFACE_PATTERN ]]` → `false` | disk 7: Wi-Fi table, its line, both-up, `wlp1s0`, the first connection before the switch, each line's own link, and the missing-pin check (with no Wi-Fi branch the missing pin is never read); run 2: the Wi-Fi floor in curl's arguments, the link in the log |
| Chosen per connection | `floor="$(download_floor)"` → `floor="${floor:-$(download_floor)}"` (once per run) | disk 2: "the second, after the switch, had the tether's floor", "each connection's line names its own link" |
| Lowest metric | `(best == "" \|\| $7 + 0 < metric)` → `(best == "")` (first listed) | disk 1: "both up, the tether's metric lower though listed second" |
| Link and floor logged | the line's `; $what $lim bytes/s over $tim s` dropped | disk 9 line checks; run 1: the link in the run's log |
| No route gets the conservative floor | the no-route branch given the Wi-Fi values | disk 2: "no default route: the conservative floor", and its line |
| Unreadable table reported | `awk … "$f"` → `awk … "$f" \|\| true` | disk 1: "a route table that cannot be read" |
| A floor that cannot be chosen stops | the guard's `&& [[ … ]] \` → `; true \` | disk 1: the missing-pin check |

**Two first reverts passed everything** and were acted on. The first draft guarded an unreadable
table twice: `[[ -r "$f" ]] || return 2`, and `|| return 2` after awk. Reverting either one alone
left all 89 and 38 checks passing. With the `-r` line removed, reverting the awk `|| return 2`
still passed: awk is the function's last command, so its own exit status already propagates. Both
guards were removed as redundant. The revert above is of the mechanism that remains.

## Suite (observed, final tree)

| Suite | Count |
|---|---|
| `test_build_usca_disk.sh` | 89 checks: 69 before, plus 2 new function checks, 17 link-floor checks and the missing-pin check |
| `test_build_usca_run.sh` | 38 checks: 36 plus 2 |
| `test_build_usca_functions.sh` | 37 checks, unchanged. It now loads the two functions and reads a tether table. |
| `test_build_usca_stop.sh` | 27 checks |
| `test_build_functions.sh` | 14 checks |
| Python unittests | 22 OK (5 + 6 + 11 `def test_`) |

## Untested, and found

- **IPv6.** Geofabrik's host has AAAA records, and the Pi has an IPv6 default route through Wi-Fi
  (observed). So curl probably connects over IPv6 (inferred), while the floor is chosen from the
  IPv4 table, as the dispatch says.
  - Today both tables name the same link.
  - In NetworkManager's journal since March, the tether was never made the IPv6 default; only
    Wi-Fi and `usb0` were (observed).
  - The harmful case would be the IPv4 default on Wi-Fi with the IPv6 default on the tether. That
    would put the Wi-Fi floor on the tether. It has not been seen and is not handled.
- **The restart cap.** On a Wi-Fi link that stays slow throughout, as in the 12:50 stretch, the
  Wi-Fi floor ends a connection about every 40 to 60 s. At roughly 15 MB per connection, a 12 GB
  extract would pass `USCA_DOWNLOAD_MAX_RESTARTS=500` partway (inferred). The `.part` file is kept
  for the next run. Not changed here.
- **No real download** through the new floor on either link.
- **The run harness.** It gives the script a table through `USCA_ROUTE_FILE`. G's
  `has_default_route` still reads the real `/proc`, as before.
- **That an open connection stays on the link it started on** after the default route moves is
  inferred, not measured.

## Calls made beyond the dispatch

- The route that counts is the lowest-metric default, not the first listed.
- An unreadable table is reported apart from "no default route".
- A floor that cannot be chosen stops the download rather than calling curl.
- The existing F checks and `test_build_usca_functions.sh` were given a tether table, so they no
  longer depend on which link this Pi is on.
