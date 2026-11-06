# The Raspberry Pi 5 as Forager's tile origin: Part 2, the Worker

Follows [part 1](2026-10-04-pi-origin-part1-report.md), handback item 4. Run by a Claude Code session
on the Pi, on `pi-origin` from `6c54797c`.

**This report contains no credentials, tokens or key material.**

## The owner's rulings, verbatim

The owner set the Access token's two values as Worker secrets ("Secrets set. Use API tokens"). Asked
what that meant, whether to fall back, and which account, the owner answered: "1a, Pi first with R2
fallback, same Worker same process. Pick it up and go." That means:

- **1a.** The Worker presents the Access service token to reach the Pi. A Cloudflare API token for
  this session was the other reading, and it was rejected.
- **The Pi first, R2 as fallback.** The rejected alternatives were Pi-only, and keeping R2 primary
  with the Pi unused.
- **The same Worker** (`forager-pmtiles`), on the account it is on now. Moving it to the
  zynergy-labs account is not part of this job.

## What changed

`server/pmtiles-worker/src/index.ts`, new function `fetchFromPi`, plus one branch in `fetch`:

- A **z/x/y tile request** goes to `${PI_ORIGIN_URL}/{name}/{z}/{x}/{y}.{ext}` first, carrying
  `CF-Access-Client-Id` and `CF-Access-Client-Secret`.
- **A 200 or 204 from the Pi is served as the answer.** Anything else falls through to the R2 path,
  which is unchanged. That includes a 404, any 3xx (redirects are not followed), a 403, a tunnel
  5xx, a network error, and a timeout after **4 s**. The 4 s is an operating limit chosen for the
  weak Wi-Fi link, not a measured figure.
- **TileJSON (`/us.json`), the z15 overflow and the tile-type check stay on R2.** The Pi's own
  TileJSON names the Pi's public URL, which clients must not be given.
- **Every fallback is logged**, except a 404, which is the expected answer above z14 and is not a
  fault. Logs never include tile coordinates, for privacy, and observability stays off, so these
  logs reach only a live `wrangler tail`.
- **Every response carries `X-Forager-Origin: pi` or `r2`.** Which origin answered can be read from
  outside without logs.
- **Missing configuration falls back to R2 and is logged:** an unset `PI_ORIGIN_URL`, or either
  secret missing.
- **Only plain archive names (`[A-Za-z0-9_-]+`) are forwarded to the Pi.** The tile-path pattern also
  admits `/` and `.`, and the token rides on every request.

`server/pmtiles-worker/wrangler.toml` gains `PI_ORIGIN_URL = "https://origin.zynergy-labs.com"`.
The secrets are not in the file.

## Disclosure

### Confirmed

- One Worker exists, `forager-pmtiles`, last modified 2026-10-05 05:02 UTC, through the Cloudflare
  connector.
- `origin.zynergy-labs.com` returns 403 without the token and 200 with it. This is from part 1, not
  re-run here.

### Not verified

- **The secret names.** The code reads `CF_ACCESS_CLIENT_ID` and `CF_ACCESS_CLIENT_SECRET`. The
  connector does not show secret names, and the owner did not state them. If they differ, every
  request falls back to R2 and logs "Access token secrets not set". Users are not affected, but the
  Pi goes unused. Either rename the secrets or tell the session the names.
- **That the code compiles.** Node is not installed on the Pi, so `npm run typecheck` was not run.
  `npm run build` was not run either, because the owner has not said "build". Workers Builds will
  compile it on its next build.
- **That a Worker on the old account can present a zynergy-labs Access token.** This is part 1's
  inference, still untested.
- **The Pi's 404 above z14, and its `Content-Type`.** Both are inferred from go-pmtiles, not observed
  through the Worker.
- **Which branch Workers Builds deploys from.** The README names a branch that no longer exists on
  the remote. The survey infers `main` (`2026-10-03-own-tiles-survey.md:27`). Pushing `pi-origin`
  is not expected to change production, but that is also unverified.

### Not done

Nothing is deployed. Going live needs this branch merged to whatever branch deploys, and that is the
owner's call. After a deploy, the check is: request one z5 tile through the Worker and read
`X-Forager-Origin: pi`, then stop `forager-tunnel` on the Pi and read `r2` on a tile not already in
the edge cache.

## Correction, after the owner's reply

The owner replied: "CF_ACCESS_CLIENT_ID and CF_ACCESS_CLIENT_SECRET. Node is installed. Check again".
The earlier text above stays as it was, and two of its claims are corrected here.

- **The secret names are confirmed by the owner** and match the code. The first "Not verified"
  item no longer stands.
- **"Node is not installed on the Pi" was wrong.** Node v22.23.3 is at
  `/opt/forager-build/node-v22.23.3-linux-arm64/bin`. It is not on `PATH`, which is the only reason
  `which node` found nothing. With that directory on `PATH`, `npm ci` added 44 packages and
  `tsc --noEmit` **exited 0**. To show that this check can fail, a file holding a deliberate type
  error was added as `src/__control.ts`. `tsc` then exited 2 with `TS2322` on that file, and the file
  was removed afterwards. The tree was clean before and after. `node_modules/` is gitignored.
  `npm run build` (`wrangler deploy --dry-run`) was **still not run**, because the owner has not said
  "build".

## Live, and checked (dispatch 2026-10-04-10)

The owner said "B and yes to the tunnel test". That covered merging `main` into `pi-origin`, opening
the PR and merging it, and stopping and starting the tunnel for check 4.

- **The branch merge:** `bd3ace29`. The only conflict was in `docs/audits/README.md`. Every row is
  kept: 263 at the base, plus 2 from this branch and 7 from `main`, gives 272.
- **The PR:** #169, with CI "Build, test, publish APK" green and GitHub's merge state CLEAN.
- **The merge was the owner's.** This session's attempt to merge was **refused by its permission
  classifier** ("Merge Without Review"), so the owner merged #169 by hand. `main` is at `128f9e82`.

**Which branch deploys, now confirmed by behaviour.** Cloudflare's check on `d4bd00bf` links to the
Worker's *production* builds. Fifteen seconds after `128f9e82` reached `main`, a fresh `/us.json`
response carried `X-Forager-Origin`, which only the new code sets. So a push to `main` deploys.

**Every request below carried a one-off query string.** The edge cache keys on the full URL, so none
of them could be answered from the cache.

| Check | Request | Result |
|---|---|---|
| 1 | z5 tile `5/6/12` | **200, `X-Forager-Origin: pi`**, `application/x-protobuf`, 104,213 bytes, 1.6 s. **Byte-identical** to the same tile read from `pmtiles serve` on `127.0.0.1:8080`, after removing any gzip encoding |
| 2 | `/us.json` | `maxzoom` 15, `minzoom` 0, `tiles` names only the Worker's own hostname, and `origin.zynergy-labs.com` appears nowhere in it |
| 3 | z15 tile `15/6826/12436` (Denver) | **200, `X-Forager-Origin: r2`**, 210,416 bytes, 2.4 s. This is the z15 overflow path |
| 4 | `forager-tunnel` stopped at 05:52:52Z, then z5 `5/6/12` | **200, `X-Forager-Origin: r2`**, 104,213 bytes, **1.07 s**. The origin, requested directly with no token, gave 403 while the tunnel was down |
| 4, after | tunnel started at 05:53:00Z | `active` at once. A fresh z5 tile read **`pi` at 05:53:08Z**. `forager-tiles` stayed `active` throughout |

Check 5 (`wrangler tail`) was **not run**, because this session has no wrangler login. So the
fallback log line itself has **not been seen**. All that is known is that the fallback happened.

### Settled by these checks

- **The cross-account question.** A Worker on the old account presents the zynergy-labs Access token
  successfully (check 1). This was part 1's inference, and it now stands confirmed.
- **The Pi's `Content-Type`** is `application/x-protobuf` (check 1).

### Still not observed

- That the Pi answers 404 above z14. Check 3 reads `r2`, which the code also produces after a
  timeout or any non-200/204 answer, so it does not tell those causes apart. `wrangler tail` would.
- How the 4 s timeout behaves on a slow Pi, as opposed to a down one. Check 4 is the down case: the
  tunnel's 5xx came back quickly, well inside the limit.

## Check 5, the fallback log line (2026-10-05)

The owner asked for the `wrangler tail` check and approved stopping the tunnel again. Access came
from an **account-owned API token** the owner created, scoped to **Specified Workers:
`forager-pmtiles`** with **Metadata Read-only**, and expiring 2026-10-06 23:59 UTC. Cloudflare's docs
list that role as the minimum for `wrangler tail`. The token was kept in a file only `bwann83` can
read and passed to `wrangler` through the environment. It does not appear in any output or record.

**Two false starts, recorded because they look alike and are not.**

- At 07:19Z, creating the tail returned **401**. `wrangler` then ran its own diagnostics, and those
  printed `Invalid access token [code: 9109]` against `/accounts`. That second error is only the
  diagnostics failing, because a Worker-scoped token cannot list accounts. The real error was the
  401.
- Cloudflare's account-token verify endpoint reported the token `active` both before and after.
- At 07:27Z the same request returned **200**, with no change on the Pi. The owner's screenshot of
  the policy came between the two attempts, so the policy most likely was not saved or had not
  propagated at 07:19. This is **inferred, not confirmed**.

**Results.** Every request carried a one-off query string, so none could be answered from the edge
cache. The log stream captured exactly 4 events, one per request sent, and every event had outcome
`ok` and no exceptions.

| Request | Response | Logged by the Worker |
|---|---|---|
| z15 tile `15/6826/12436` | 200, `r2` | nothing |
| `forager-tunnel` stopped at 07:28:05Z, then a z5 tile | 200, `r2`, 1.48 s | `pi-origin: HTTP 530, serving from R2` |
| Tunnel started at 07:28:12Z, first z5 poll | 200, `r2` | `pi-origin: HTTP 530, serving from R2` |
| Second z5 poll, at 07:28:18Z | 200, **`pi`** | nothing |

### What this settles

- **The fallback log line exists and reads as designed.** It carries no coordinates. A tunnel that
  is down reaches the Worker as **HTTP 530**, which is Cloudflare's own status for an origin it
  cannot reach, and the fallback answered well inside the 4 s limit.
- **Above z14 the Pi answers 404.** Check 3 could not show this, because it saw only `r2`. For the
  archive name `us`, the code falls back without a log line on exactly one path: a 404. A timeout,
  a network error and every other status all log. The z15 request logged nothing, so the Pi
  answered 404. That is read from the code's branches plus the empty log, not from the Pi's own
  response.
- **The tunnel needs about 6 to 8 s to rejoin.** The first poll after the restart still met a 530,
  and it fell back cleanly.

### Still not observed

How the 4 s timeout behaves when the Pi is up but slow, as opposed to down.

### Clean-up

`~/.config/forager/tail-token` is still on the Pi, waiting on the owner's word. The token expires on
its own on 2026-10-06 and can be deleted sooner in the dashboard.
