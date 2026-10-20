# Handoff: the Pi planner, Aspen Meridian, to the next Pi planner session

Written 2026-10-20 at 07:41Z by the Pi planner session that ran 2026-10-18 to 2026-10-20. It was
written at `pi-build` `40873887`, plus this file. The owner, verbatim: "Write a handoff for the next
session".

**Everything below about the Pi, the branches and Cloudflare is a claim about the past.** Check it
against the remote and the Pi before relying on it. CLAUDE.md says why: "A planner's picture of the
repository is a claim about the past, and it decays".

## Who you are

You are **Aspen Meridian, the Pi planner.** Read `docs/process/pi-planner-profile.md` on
`pi-build` first, then CLAUDE.md in full. Its first Known pitfall is absolute: obey it and never
name what it describes.

- **The owner's ruling on the role, verbatim: "Plans only, dispatches to Pi coders".** You plan,
  ask, record and review. Pi coders edit and run code.
- **The owner's relayed word counts as the go** for coders you start ("Your relay counts
  (Recommended)").
- **Claude Code's permission check may still refuse installs and deletes for coders you start.** It
  did so twice, for pi-02 Phase 2 and the first pi-12. Then the owner wants a paste-ready dispatch
  for a session they open and approve: "Write a dispatch for me to paste into a separate coder
  session, and I'll manually approve it". Never route around a refusal.
- **Your memory notes** (`~/.claude/projects/-home-bwann83-Forager/memory/`) carry:
  - the role;
  - the SD card;
  - the network;
  - prep, ask, go.

## Standing rules from the owner (verbatim; each in the record)

- **"Plans only, dispatches to Pi coders".**
- **The SD card** (`/mnt/archive`): "not easily removable so you can feel confident it'll stay in.
  But it's still limited to SD card quality". Use it for inputs read straight through; working
  files stay on the NVMe.
- **Commits go on `pi-build`.** `map-build/` lives there.
- **Install only on the owner's go,** and rebuilds only on their word.

## State of the Pi (read 2026-10-20 07:41Z)

- **Published:** `/srv/forager-build/out-usca/current` points to `20261017T202051Z`, the
  US + Canada map from the 2026-10-17 extracts, with `status-usca.json` ok.
  - The basemap is 33,973,424,728 bytes, z0–15, with six trail keys on `roads`.
  - Routing is 185 BRouter squares.
  - Routes follow trails, including across the −80 band edge (pi-13).
  - It has no Puerto Rico or USVI data.
- **Installed:** `/opt/forager-build/map-build` `COMMIT` is `536f2f3d`. It includes:
  - the hand-stop fix;
  - downloads and the merge on the SD card;
  - the byte-based disk check;
  - the restart loop with a Wi-Fi floor (700 kB/s over 40 s) and a tether floor (50 kB/s over 120 s);
  - the network wait;
  - **four regions**: `us canada us/puerto-rico us/us-virgin-islands`.
- **The next build is therefore a full four-region rebuild,** about 12 h from the 261017 inputs or
  about 18 h with a fresh download. The kept basemap is refused for `--basemap` with four regions.
- **Timers and units:** `forager-build.timer`, `forager-tiles` and `forager-tunnel` are all
  **disabled**. The timer was paused on "Pause the timer this week (Recommended)". Re-enabling it
  waits for the owner. It has `Persistent=true`, so a re-enable after a missed Sunday starts that
  run at once.
- **Disk:** NVMe about 165.4e9 bytes free; card about 100.4e9.
- **Kept:**
  - `kept-usca/usca-20261019T011311Z/`, the basemap hard-linked with the published one;
  - `sources/osm/{us,canada}-261017.osm.pbf`, symlinks to the card.
- **Network:** Wi-Fi is the default route, with the Bluetooth tether behind it. Wi-Fi has dropped
  once without reconnecting by itself, so check `ip route` before long downloads.
- **Nothing is running.** No build, BRouter or `pmtiles serve` process.

## Open work, in the order it matters

1. **Map design, then the Cloudflare upload.** The owner's rulings are in
   `docs/audits/2026-10-20-cloudflare-upload-prep.md`:
   - overwrite `us.pmtiles` in place;
   - "After the design, without waiting for the islands";
   - "No rollback copy";
   - the storage cost was already approved. The record of that was stale, and its correction is in
     the same file.

   **Rowan's reply** (recorded and spot-checked there) shows most of the basemap design already
   exists:
   - **Street v1,** PR #258 on `street-cloudflare`, held for the next release, built on the owner's
     rulings −1284 to −1294, −1300 and −1342 to −1345;
   - **Elder Ford's design review** and its prior art, on `basemap-launch-plan`, with slots reserved
     for `sac_scale`, `trail_visibility` and `informal`.

   The new archive carries those keys. **Open for the owner:**
   - how to preview the design. The earlier ruling: "On the Pi, with the new map";
   - what Sage designs first. The planner's reading: start from Street v1, and fill the trail slots.
2. **The designer role, Sage Wayfinder,** on Fable. The owner ruled its name, its model, and
   "pre-main, beside the team (Recommended)". The profile isn't written yet.
   - It is to be written from the design record on its own branch cut from `origin/pre-main`, then
     sent through the owner to Rowan, who merges it and adds the team-table row.
   - Rowan's suggested reading for a designer is item 5 of the reply, quoted in the prep record.
   - The owner said "Wait for Rowan's reply first". It has arrived.
3. **Puerto Rico and the USVI on the map.** Installed, not built.
   - Both squares are measured and pass: `W70_N15` is 4.64 MB, and `W65_N15` 286 kB against a
     100 kB floor (pi-15).
   - All four 261017 extracts share one snapshot, so `--local 261017` needs only the two island
     files, 77 MB, placed on the card with links.
   - The owner: "Install only, rebuild later".
4. **The weekly timer:** re-enable only on the owner's word.
5. **For Rowan,** through the owner:
   - the record relay, `prompts/preserved/2026-10-19-pi-05-relay-to-rowan.md`. It has not been
     confirmed placed. Later entries are in the audit addenda;
   - the Pi planner's row in the team table.
6. **Leftovers,** for the owner to rule on, since nothing was deleted:
   - pi-17's `~/map-design-scratch` and its worktree `.claude/worktrees/map-design`;
   - the coder worktrees `pi-stop-fix`, `pi-disk-fit`, `pi-link-floor` and `pi-islands`. Their
     branches are merged into `pi-build`;
   - `/opt/forager-build/map-build.prev*`.
7. **Not determined,** each noted in its record:
   - WayLinker's true memory need;
   - IPv6 for the link floor;
   - the restart cap on slow Wi-Fi;
   - the other eight band edges and the antimeridian for routing;
   - a real Geofabrik download through the new loop, which has never run.

## How to reach Rowan

- `ListAgents` showed "Rowan" as a Remote Control session.
- `SendMessage` to `Rowan` delivered a message, but did not confirm it was read.
- Rowan answered with `send_message` from session `session_01ApYEDz1d7bweKs2GtoPRn6`. Reply to that
  `session_id` with the Claude Code Remote `send_message` tool. `SendMessage` is a different
  transport.
- **The coordination rule from Rowan:** `ui/map/SightingsMap.kt` is frozen during UI split Phase 2.
  App map-code changes go through the owner to Rowan. Style JSON, `map-style/` and the Worker are not
  affected.

## The record

- **Everything is in two documents,** with their index rows in `docs/audits/README.md`:
  - `docs/audits/2026-10-19-usca-routing-bands-report.md`, with its numbered addenda: the build, the
    failures and fixes, the installs, the checks;
  - `docs/audits/2026-10-20-cloudflare-upload-prep.md`: the upload, the design, the designer and
    Rowan.
- **Dispatches** are `prompts/preserved/2026-10-19-pi-01` to `-pi-05`, and `2026-10-20-pi-08` to
  `-pi-17`.
- **`RECORD.md` is not written from the Pi.** The owner: "Relay to Rowan".

## Slips this session made, so you don't repeat them

- **Treated a superseded storage ruling as standing.** It had read only the Pi's records, not the
  forecast side's.
- **Gave a prediction that contradicted its own cited source:** Ramona Falls, at 80%, when the record
  already said 0%.
- **Drew routing bands from a narrower filter than the build used,** which caught a doomed run 33
  minutes in.
- **Held two unconfirmed causes for one gap** (the hand-stop status). Data closed it.
- **Some time estimates ran short.**

Each is recorded where it happened.

## Correction, 2026-10-20, by the next Pi planner session

Appended on the owner's go ("2 go"); the text above is unchanged.

- **The dispatch list under "The record" is incomplete.** It names `2026-10-19-pi-01` to `-pi-05`
  and `2026-10-20-pi-08` to `-pi-17`. `prompts/preserved/2026-10-19-pi-06.md` (the disk-fit study)
  and `prompts/preserved/2026-10-19-pi-07.md` (the hand-stop fix) also exist on `pi-build`. Both
  carry a 2026-10-19 file name and say they were written 2026-10-20 03:52Z. Checked with
  `git ls-tree origin/pi-build prompts/preserved/` at `58011da4`.
