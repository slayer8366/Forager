# Relay 2026-10-19-pi-05: the Pi's record entries, for Rowan Waymark to place

From Aspen Meridian, the Pi planner, to Rowan Waymark. The owner's ruling, 2026-10-19, verbatim:
"Relay to Rowan". The ruling's option read: "I hand Rowan the entries, through you, to place." Rowan
assigns the IDs and decides the wording of the notes. The fields below are drafts in RECORD.md's
format. They cite each other by letter, A to L, until Rowan numbers them.

The Pi wrote no RECORD.md entries on 2026-10-18 or 2026-10-19. The audit record stood in for them:
`docs/audits/2026-10-19-usca-routing-bands-report.md` and its two addenda, on `pi-build`. Every
fact below is read from that record, a commit, or the Pi, as cited. Timestamps are from the clock
when this file was written, unless an entry names an event's own time from a log or commit. The
owner's words came through this session's question tool or chat. The chat ones carry no clock time
of their own.

---

**Kind:** decision
**Ref:** A
**Title:** The Pi may use its SD card, `/mnt/archive`, for build inputs
**The owner, verbatim,** in the Pi planner's window, 2026-10-18 (an evening message, with no clock time recorded): "There's an SD card inserted for backup reserves. You're authorized to use that". And on 2026-10-19: "The SD card is not easily removable so you can feel confident it'll stay in. But it's still limited to SD card quality".
**What was done under it:**
- `us-261017` and `canada-261017` were copied to `/mnt/archive/forager-build/sources-osm/`.
- Their MD5s were checked twice, the second time read from the card directly.
- The NVMe originals were replaced by symlinks at 21:05 PDT on 2026-10-18 (`ls -la sources/osm`), after the owner approved the permission prompt.
**Notes:** relayed by the Pi planner.

---

**Kind:** outcome
**Ref:** B
**Title:** The first US + Canada build finished its basemap and failed at routing: BRouter ran out of heap
**Observed:**
- Run `usca-20261019T011311Z`, started by `forager-usca-fetch-build` at `92856ef1`.
- Merge exit 0 (1107 s). Planetiler exit 0 (7h44m, peak 7.0 GB resident); `forager-usca.pmtiles` is 33,973,424,728 bytes.
- `brouter-cut` exit 1 at 09:20:34Z: `OutOfMemoryError` at 134M of 2.4B nodes, on a 2 GB heap.
- Nothing published, and the previous output untouched, as the script was designed to do.
- Source: the audit record, "What failed".

---

**Kind:** decision
**Ref:** C
**Title:** Cut the routing input down (option A), keep the finished basemap, and commit map-build changes on pi-build
**The owner, verbatim:** "Go with A, and keep the basemap and move on". And, through the question tool, on where to commit: "pi-build (Recommended)".
**What it set:**
- `--basemap FILE` reuses a finished archive (`f4196fdf`).
- The routing input is cut to what `all.brf` routes (`USCA_ROUTING_FILTER`).
- The basemap moved to `kept-usca/`.

---

**Kind:** decision
**Ref:** D
**Title:** A alone did not fit the Pi; routing runs in longitude bands
**Measured first:**
- BRouter holds about 16 bytes per node whatever the input.
- Canada's cut ran out at 33M nodes on a 512 MB heap.
- The highway cut is 477M nodes.
**The owner, verbatim:** "Forecast work is being done on the big machine, which is why we're here on the Pi. A + finer regions for now".
**What it set:**
- Ten bands (`b7302ed8`, `5bc1a752`).
- Every band is cut and counted before BRouter runs (`c894625b`).
- Proof before use: Canada built whole and in two bands gave 106 squares, byte-identical apart from WayLinker's 8-byte run stamp.

---

**Kind:** correction
**Ref:** E
**Supersedes, in part:** -1274 and -1300, where they are read as saying Geofabrik's `us` extract carries Puerto Rico and the USVI.
**Title:** Geofabrik's `us` extract holds no Puerto Rico or US Virgin Islands data
**Observed:**
- `us-261017`'s highway cut has 0 nodes in either island's box.
- `us.poly` ends at −66.88°.
- San Juan's z15 tile holds only land and ocean, with no routing square.
- The claim came from the two being listed as `us`'s subregions on Geofabrik's page (`pins.env`, read 2026-10-18).
- Corrected in the script, the README and `pins.env` (`099a1517`).
**Notes:** Rowan should check how -1274 and -1300 themselves are worded before placing this. The Pi planner read only the script's citation of them.

---

**Kind:** decision
**Ref:** F
**Title:** Publish US + Canada now; add Puerto Rico and the USVI later as regions of their own
**The owner, verbatim,** through the question tool: "Publish now, add PR later (Recommended)".
**What it set:** San Juan left the coverage points until Puerto Rico is a region (`099a1517`).

---

**Kind:** outcome
**Ref:** G
**Title:** US + Canada published on the Pi
**Observed:**
- Run `usca-20261019T225941Z` at `d5ed9eec`, 22:59:41Z to 03:21:36Z.
- 185 routing squares.
- Archive check passed: zooms 0–15, 9 layers, six trail keys.
- Seven coverage points passed.
- Published `out-usca/20261017T202051Z`; `current` points to it; `status-usca.json` reads `ok`, stage `done`.
- One fix was needed on the way: `run_record.py` could not take the per-region sequence `us:2415,canada:4945` (`d5ed9eec`).
- Source: the audit record, second addendum (`e63f74fe`).

---

**Kind:** decision
**Ref:** H
**Title:** The Pi planner is Aspen Meridian; it plans only and dispatches to Pi coders
**The owner, verbatim:** "You are the Pi planner, your job is to manage the map making and ensure it gets done to the highest quality standard. Write your profile based on your behavior." Through the question tool: "Aspen Meridian", "pi-build (Recommended)", and "Plans only, dispatches to Pi coders".
**What it set:**
- `docs/process/pi-planner-profile.md` on `pi-build` (`ae781657`).
- For Rowan: a row in the team table in `docs/process/planner-profile.md`.

---

**Kind:** intent ×4
**Ref:** I1–I4
**Dispatch-files:** `prompts/preserved/2026-10-19-pi-01.md` to `-pi-04.md` on `pi-build` (`8709d0fa`), for a Pi coder. Each states its own prediction, finish line and stops.

- **I1, pi-01: is the Pi ready for Sunday's weekly build** (timer, 2026-10-25 10:00 UTC)? Read-only.
  - Prediction (planner): under 25 GB of margin as things stand.
- **I2, pi-02: clear the first build's leftovers.** Phase 1 read-only; deleting per item only on the owner's word.
- **I3, pi-03: research Puerto Rico and the USVI as regions.** Read-only; no download or build.
- **I4, pi-04: why `systemctl stop` left `status-usca.json` at "running".** Data first; two explanations already disproved.
  - The owner, verbatim: "go on pi-04". The go covers throwaway repro units only.

---

**Kind:** outcome
**Ref:** J
**Continues:** I2
**Title:** pi-02 Phase 1: the leftovers inventoried; nothing deleted
**Observed (the coder's report, spot-checked by the planner):**
- Freed, counting each inode once:
  - `probe-routing/` and the stopped run's `work/` together: 16.737 GB;
  - the two superseded square sets: 3.118 GB;
  - the kept basemap and `failed-usca/latest`: 0.002 GB.
- The planner re-derived 16.737 and 3.118 GB with its own inode count.
- The planner's prediction of "about 23 GB" for the first pair was wrong: it counted the 7.6 GB hard-linked cut twice.
- The stopped run's "band extract" is a 0-byte file.
- Recommendations, awaiting the owner per item:
  - delete the probe's large files but keep its small evidence files and the 218 Canada test squares;
  - delete the stopped run's scratch and both superseded square sets;
  - keep the kept basemap's directory, because the published manifest names its path.

---

**Kind:** decision
**Ref:** K
**Title:** The Pi's record entries are relayed to Rowan, not written in parallel
**The owner, verbatim:** "Relay to Rowan". The reason offered: `records-after-173` is live (-1427, a commit at 20:26 PDT), and two sessions writing it in parallel can take one ID. That happened at -1421, and Rowan's -1423 correction records it.

---

**Kind:** note
**Ref:** L
**Title:** Open on the Pi
- **Terminals for B and C.** B and C are answered by G. I1, I3 and I4 are still running; I2 waits on the owner.
- **The status-file gap (I4).**
- **Puerto Rico and the USVI (I3).**
- **WayLinker's memory need,** not determined.
- **The weekly build on 2026-10-25 (I1).**
