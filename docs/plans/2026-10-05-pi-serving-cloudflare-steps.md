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

## Progress

- **Step 0, done 2026-10-05,** on the owner's "1 yes", typed in this coder's session.
  - Node v22.23.3 (linux-arm64) is at `/opt/forager-build/node-v22.23.3-linux-arm64`, linked
    as `/opt/forager-build/node`. Its archive and `SHASUMS256.txt.asc` are in
    `/opt/forager-build/archives/`.
  - `SHASUMS256.txt.asc` carries a good signature from `5BE8A3F6C8A5C01D106C0AD820B1A390B168D356`.
    That fingerprint is listed under "Release keys" in the v22.x README, and the key was fetched
    separately from `nodejs/release-keys`. The tarball's checksum is OK.
  - `npm ci` installed the lock's versions (wrangler 4.124.0). `npm run typecheck` is clean, and
    `npm test` passes 16 of 16.
  - Three revert checks each failed exactly the matching test (`d0c4bac4`).
  - `wrangler deploy --dry-run` bundles, listing `ORIGIN_URL` and `ORIGIN_ARCHIVES` among the
    bindings.
  - Wrangler announced anonymous telemetry on that run. Every later command sets
    `WRANGLER_SEND_METRICS=false`.
- **Step 1, done 2026-10-05,** on the owner's "2 yes".
  - `/etc/systemd/system/forager-tiles.service` now runs `pmtiles serve /srv/forager-tiles
    --quiet …`. The previous file is kept as `forager-tiles.service.pre-quiet`.
  - After `daemon-reload` and a restart: the unit is active, NRestarts 0, and `forager-orwa` and
    `us` both answer 200 for a tile and for their JSON.
  - Those four requests wrote **0** `served`/`fetch` lines to the journal. Before the change,
    each request wrote one (the 2026-10-04 20:21Z lines quoted above).
  - The journal's older lines, including this session's test requests, were **not** cleared.
    That is still for the owner to decide.
  - Undo: `sudo cp -p /etc/systemd/system/forager-tiles.service.pre-quiet
    /etc/systemd/system/forager-tiles.service && sudo systemctl daemon-reload && sudo systemctl
    restart forager-tiles`.

## The owner's steps, written out

### A. The API token

1. Sign in at dash.cloudflare.com to the account that holds the Worker `forager-pmtiles`. Its
   account ID ends in `0e14`, and its Workers & Pages list shows `forager-pmtiles`.
2. Click the profile icon at the top right, then **My Profile**, then **API Tokens**, then
   **Create Token**.
3. Under **Custom token**, click **Get started**.
4. **Token name:** `forager-pi-deploy`.
5. **Permissions:** one row: **Account**, then **Workers Scripts**, then **Edit**. Add no other
   rows.
6. **Account Resources:** **Include**, then the account that holds `forager-pmtiles`.
7. **Client IP Address Filtering:** leave it empty.
8. **TTL:** start today, and end in about 30 days.
9. Click **Continue to summary**. Check that it lists only *Workers Scripts: Edit* on that one
   account, then click **Create Token**. Cloudflare shows the token once; copy it.
10. At the Pi, in a terminal you open yourself (not through Claude), run:
    ```
    sudo nano /etc/forager/cloudflare-api-token.env
    ```
    Type these two lines, pasting the token after the first `=`:
    ```
    CLOUDFLARE_API_TOKEN=
    CLOUDFLARE_ACCOUNT_ID=a6a899e01e2194ef8fff048c20130e14
    ```
    Save with Ctrl+O, then Enter, then Ctrl+X. Then run:
    ```
    sudo chown root:root /etc/forager/cloudflare-api-token.env
    sudo chmod 600 /etc/forager/cloudflare-api-token.env
    clear
    ```
11. Tell the coder "token placed". It checks the file's shape and permissions without printing the
    token, then runs one read-only command (`wrangler deployments list`) to confirm the token
    works.

### B. The Worker's two secrets

1. At the Pi, in a terminal you open yourself, run `sudo cat /etc/forager/access-token.env`. It
   shows two lines, `CF_ACCESS_CLIENT_ID=…` and `CF_ACCESS_CLIENT_SECRET=…`.
2. In the dashboard, on the same account: **Workers & Pages**, then `forager-pmtiles`, then
   **Settings**, then **Variables and Secrets**, then **Add**.
3. **Type:** Secret. **Variable name:** `CF_ACCESS_CLIENT_ID`. **Value:** everything after the `=`
   on that line.
4. **Add** again. **Type:** Secret. **Variable name:** `CF_ACCESS_CLIENT_SECRET`. **Value:**
   everything after the `=` on its line.
5. Click **Deploy** (or **Save**). This makes a new version of the code that is live now, with the
   two secrets attached. The live code ignores them, so nothing changes for users.
6. Back at the Pi, run `clear`.
7. Tell the coder "secrets set". It then asks before deploying the new code.
