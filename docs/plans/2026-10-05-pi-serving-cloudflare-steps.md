# Serving the Pi's map through the Worker: the Cloudflare steps, the storage options, and what is still open

Written 2026-10-05 by the Pi coder session on `pi-build`, after the Worker change at `65766598`. Each
step below is done only on the owner's go, asked step by step. **Nothing here has been done yet.**

**The owner's words this rests on, each read at the source** (the planner's session,
`session_01Hz8eij3gNycnr4uQVR5pAe`, with `list_events`):
- 00:16:15Z, "1 yes  2 A  3 A + B as a backup": the Pi serves, and R2 storage is the backup.
- 03:20:03Z, "1 yes / 2 yes / 3 claude session with cloudflare access is fine…", then at 03:20:31Z
  "…A coder can do this?". That is: write the Worker change, keep the old `us` map outside
  Oregon and Washington, and have a Claude session do the Cloudflare steps.
- 03:23:19Z, "No to the free storage limit.  Yes to the Pi coder": stay inside R2's free tier,
  and the Pi coder does the Cloudflare steps.
- 03:24:47Z, "Stay below storage limit as I want to explore further avenues before making that
  decision. I have to do budgeting and all that.": the storage backup is on hold, and nothing goes
  into the bucket.

## What exists (read 2026-10-05)

- **The Worker is `forager-pmtiles`,** at `forager-pmtiles.brandonlee1-894.workers.dev`, on account
  `a6a899e01e2194ef8fff048c20130e14` (`server/pmtiles-worker/wrangler.toml`).
  - It is live, and was last modified 2026-10-04T22:42:40Z.
  - Its bundled code, read with the Cloudflare connector, matches the repository's
    `server/pmtiles-worker/` before `65766598`: `src/index.ts` logic, `src/shared.ts`, and
    `src/offline-style.json` (1,774 of 1,774 tokens equal). So whatever changed at 22:42:40Z was
    not the code.
  - **Could not determine** whether a setting, variable or secret changed then: the connector
    shows the name and ID only.
- **`tiles.zynergy-labs.com` does not resolve.** The app uses the `workers.dev` name
  (`app/.../map/OfflineStyle.kt:18`).
- **The app uses the Worker for offline downloads only.** Live browsing uses outside raster
  services (`app/.../ui/map/Basemap.kt`).
- **The Pi's tile server** is reachable only at `origin.zynergy-labs.com`, behind Cloudflare Access
  with one service token, on the **other** account (zynergy-labs.com). The token's two values are
  in `/etc/forager/access-token.env` on the Pi, root-only, as `CF_ACCESS_CLIENT_ID` and
  `CF_ACCESS_CLIENT_SECRET`.
- **The R2 bucket `forager-maps`** was read with the Pi's rclone remote (`rclone size`, `rclone lsl`):

  | What | Objects | Bytes |
  |---|---|---|
  | whole bucket | 14,147 | **8,908,421,115** |
  | `us.pmtiles` | 1 | 8,817,909,309 |
  | `overflow/` (zoom 15 tiles the Worker caches for `us`) | 14,146 | 90,511,806 |

- **The new map** is `forager-orwa.pmtiles`, 938,933,157 bytes, SHA-256
  `2904a30de1c6f0b78c671cc1fdb2b1a447a5459794a4428a8289488dc7895b70`
  (`/srv/forager-build/out/20261003T202050Z/`, and the served copy in `/srv/forager-tiles/`).

## A finding the owner must rule on before the Pi serves the public: the Pi logs every tile

`pmtiles serve` writes one line per request to the journal, with the tile's coordinates, for
example `served 200 /forager-orwa/{z}/{x}/{y}.mvt` (read in `journalctl -u forager-tiles`). Once the
Worker sends users' requests to the Pi, the Pi's journal would record where users looked. That
contradicts the privacy note in `wrangler.toml` ("we keep no tile request logs").
- **The fix found:** `pmtiles serve --quiet` ("Silence logging and progress output", from its
  `--help`), added to `ExecStart` in `forager-tiles.service`, followed by a restart. It also
  silences the server's error lines, so a failing archive would show only as failed requests.
- **The second half:** the journal already holds this session's own test requests, one of them a
  real place. Clearing them means vacuuming the journal (`journalctl --rotate` then
  `--vacuum-time`), which drops every unit's older logs. Left for the owner to decide.

## The Cloudflare API token (made by the owner, never seen in chat)

- **Where:** `/etc/forager/cloudflare-api-token.env`, owned by root, mode `0600`, the same as the
  Access token file.
- **Format,** two lines and nothing else:
  ```
  CLOUDFLARE_API_TOKEN=<the token>
  CLOUDFLARE_ACCOUNT_ID=a6a899e01e2194ef8fff048c20130e14
  ```
- **How to make it, with the narrowest scope:** in the dashboard of the account that holds
  `forager-pmtiles`, go to My Profile → API Tokens → Create Token → Create Custom Token.
  - **Permissions:** Account · **Workers Scripts** · **Edit**. Nothing else while the storage backup
    is on hold. Add Account · **Workers R2 Storage** · **Edit** only when an upload is approved.
  - **Account Resources:** Include · that one account.
  - **TTL:** an end date, for example 30 days.
  - **Client IP Address Filtering:** optional. The Pi's address changes with the home connection.
- **Inferred, not tested:** `wrangler deploy` with `account_id` set needs only Workers Scripts
  Edit. If it refuses, its error names the missing permission. The coder then stops and asks; it
  does not widen the token.
- **How the coder uses it:** each command runs in a root shell that sources the file, so the value
  never appears in output.

## The Worker's Pi access key (set by the owner by hand)

In the dashboard of the account that holds `forager-pmtiles`, go to Workers & Pages →
`forager-pmtiles` → Settings → Variables and Secrets → Add. Add two entries, both of type
**Secret**:
- Name `CF_ACCESS_CLIENT_ID`, value: the line of that name in `/etc/forager/access-token.env` on
  the Pi.
- Name `CF_ACCESS_CLIENT_SECRET`, value: the line of that name in the same file.

Reading the file needs `sudo` at the Pi itself. Setting these before the new code is deployed is
harmless, because the live code ignores them. `wrangler deploy` keeps secrets.

## The steps, each asked before it runs

0. **Install Node and the Worker's pinned tools.** There is no Node on the Pi.
   - Wrangler 4.124.0, pinned by `package-lock.json`, needs Node 22 or later.
   - Proposed: Node 22 LTS from nodejs.org, its `SHASUMS256.txt` checked against the release
     signing keys, unpacked under `/opt/forager-build/` next to the JDK. Then `npm ci` in
     `server/pmtiles-worker/`, which installs exactly the lock's versions.
   - Then `npm run typecheck` and `npm test`: the first run of `65766598`'s tests.
   - Undo: remove the Node directory and `node_modules/`.
1. **Privacy:** `--quiet` on the tile server, if the owner approves (see above). It needs one
   restart, about two seconds without tiles. Undo: remove the flag and restart.
2. **The owner creates the API token and places it** (see above).
3. **The owner sets the two Worker secrets** (see above).
4. **Deploy:** `npx wrangler deploy` from `server/pmtiles-worker/` at the approved commit.
   - Before: `npx wrangler deployments list` records the current version, for the undo.
   - Undo: `npx wrangler rollback <that version>`, or redeploy the commit before `65766598`.
5. **Verify with the Pi up:**
   - `GET https://forager-pmtiles.brandonlee1-894.workers.dev/forager-orwa.json` should return
     200, `X-Forager-Source: pi`, with `tiles` naming the `workers.dev` host.
   - The zoom 15 Ramona Falls tile (coordinates in scratch only) should return 200 with
     `X-Forager-Source: pi`, and its decompressed SHA-256 should equal the one read from the
     file on the Pi.
   - `/us.json` and a `us` tile should be unchanged, with no `X-Forager-Source` header.
6. **Verify with the Pi unreachable:**
   - Stop `forager-tunnel` for about two minutes, request a tile not yet cached (another
     coordinate), and expect **503, `Cache-Control: no-store`, `X-Forager-Source: none`**. While the
     backup is on hold, R2 has no copy.
   - Then start the tunnel again and expect 200 from the Pi within about a minute, once the
     "Pi down" memory expires.
   - `wrangler tail`, run during this check, should show the fallback lines, with the archive
     name and reason and no coordinates.
   - Undo: none needed. The tunnel is started again in the same step.

The app change (offline downloads inside Oregon and Washington use `forager-orwa`; elsewhere `us`
stays) is separate work in the Android app. It is prepped, but not built until the owner says so.

## Storage options, for when the owner has budgeted (not asked yet)

R2's free tier is 10 GB-month. **Unverified:** whether Cloudflare counts a GB as 10⁹ or 2³⁰ bytes.
The headroom below is given both ways.

| Option | Bucket after | Headroom at 10⁹ | Headroom at 2³⁰ | What it means |
|---|---|---|---|---|
| **A. No storage backup** (now) | 8,908,421,115 | 1,091,578,885 | 1,828,997,125 | If the Pi is down, `forager-orwa` answers 503 and offline downloads of Oregon and Washington fail until it is back. `us` is unaffected. |
| **B. The full new map** (z0–15, 938,933,157) | 9,847,354,272 | **152,645,728** | 890,063,968 | Under 10 GB on either reading, but at 10⁹ the margin is 153 MB. The `overflow/` cache for `us` grows as people download (90.5 MB so far), so it could cross the line without anyone uploading anything. Safe only with option E. |
| **C. A smaller backup, z0–14** (433,784,124) | 9,342,205,239 | 657,794,761 | 1,395,213,001 | While the Pi is down, zoom 15 is missing. The Worker must also be told not to fill that gap from Protomaps' build into `overflow/` (a code change). |
| **D. A smaller backup, z0–13** (198,581,357) | 9,107,002,472 | 892,997,528 | 1,630,415,768 | As C, missing zooms 14–15 during an outage. |
| **E. Cap `overflow/`** (a Worker change, combined with B, C or D) | as chosen | | | Stop writing new zoom-15 tiles to R2 above a set total, serving them uncached instead. Keeps the bucket under the limit whatever users download. |
| **F. Remove `us.pmtiles`** | 90,511,806 + backup | | | Not viable while offline downloads outside Oregon and Washington use `us`. |

Sizes C and D were measured on the Pi with `pmtiles extract --maxzoom`, into scratch, then
deleted. Every weekly build that is swapped in would need its backup re-uploaded. The old file is
overwritten in place, so the bucket does not grow.

## Not verified

- No test in `65766598` has run.
- No deploy has run, and the Worker's behaviour against the real Access token across accounts is
  untested.
- Whether a Worker on one account can reach an Access-protected hostname on another account with a
  service token. The origin report says it should; nobody has tried it.
