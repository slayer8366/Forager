# Route cut Worker: verify-first answers, and a stop on privacy (dispatch 2026-10-23-01)

**Coder:** Hazel (Opus 5.5). **Base:** origin/pre-main `8b6f3572f`, fetched 2026-10-23. **Status:** stopped
at verify-first item 2, as the dispatch directs ("If it needs an access control, propose it and stop"). Nothing
under `server/` is changed; no route is built.

## 1. Cloudflare limits against the real case: fits, by reasoning (not measured)

**Method: reasoned from the cutter's reads, not measured in a test.** `cutRd5` (`server/pmtiles-worker/src/rd5/cut.ts`)
reads, per square: the header (1 read), the footer (1), each touched non-empty one-degree tile's 4 KB index (1 each),
and one range per row of wanted cells in each touched tile (`cut.ts:69-75`). The count depends only on geometry, so it
was computed exactly by running `wantedCells` (`src/rd5/cells.ts`) over 48 x 48 km boxes (24 km radius, the app's
maximum, `OfflineMapRepository.kt:203` per the route-data report) centred on every 0.01° step in 44.5-45.5°N,
120.5-118°W, one-cell margin, every tile assumed non-empty (an upper bound), plus one read for `routing/manifest.json`.
The script ran locally with Node v24.21.0; it is not committed, since no code is part of this stop; its whole logic is this paragraph.

| Squares touched | Worst R2 reads, 8 km | Worst R2 reads, 24 km | Cells, 24 km |
|---|---|---|---|
| 1 | 21 | 39 | 391 |
| 2 (the dispatch's case, near 45°N or 120°W) | 25 | 43 | 391 |
| 4 (a box over 45°N 120°W itself) | 29 | 47 | 391 |

**Limits** (developers.cloudflare.com/workers/platform/limits, read 2026-10-23): R2 calls count as subrequests;
Workers Paid allows 10,000 per request (Free: 50); CPU 30 s default on Paid (Free: 10 ms); 128 MB per isolate on
both. The account is on Workers Paid **as the owner confirmed** (RECORD, "The mapping account is on Workers Paid",
and the $5.00 invoice entry); I did not check the account.

- **Subrequests:** 47 at worst against 10,000. Fits. (Against Free's 50 it would have fitted by 3, which is why the
  plan matters; the row reads are not coalesced today, and could be, one range per tile, if that ever mattered.)
- **Memory:** the cut is held about twice (the copied parts, then the output array, `cut.ts:94-104`). The route-data
  report puts a 24 km cut at 0.5 to 0.85 MB (its Q4, computed). Even at 50 KB a cell, 25 times the Seattle square's
  2.1 KB average, 391 cells is about 20 MB, 40 MB held: under 128 MB. Cell density at a dense city core is
  **not measured**.
- **CPU:** CRC over 200 bytes and at most four 4 KB indexes per square, a copy of the cut, and a sha256 of it for the
  response. Milliseconds for a 1 MB cut; far under 30 s. Not measured.
- **Not done:** reading the whole 55 MB square to check the manifest's sha256 on every cut. The cutter checks the
  header and each tile index against the square's own CRCs (`format.ts:83-90`, `:105-116`); cells carry their own
  checksums, which BRouter checks on read. A whole-file sha256 per request would mean 55 to 220 MB of R2 reads per
  cut; I would not do it per request.

## 2. Privacy: the route cannot stay private as the Worker stands. Stop-and-ask.

The Worker has **no access control of any kind**: the fetch handler (`server/pmtiles-worker/src/index.ts`, `fetch`)
checks only the method and the path; `ALLOWED_ORIGINS = "*"` (`wrangler.toml`), and CORS is not access control in any
case, since a non-browser client ignores it. Any route added is open to anyone who has the hostname, which is in the
APK. A cut-on-request route is also a way to rebuild whole squares: tile the 24 km limit across a square and every
cell comes back. So under "no public route data" (the dispatch, citing -1553; -1553 records it as the planner's
request to Aspen, "private, with no Worker route"), the route needs an access control, and the dispatch says that
decision is the owner's.

The request's area is a second, separate privacy matter, and it is already covered: Workers Logs are off
(`wrangler.toml`, `[observability] enabled = false`), so the rectangle in a URL is not logged by us. Cloudflare's edge
cache holds cut responses keyed by URL, with no user identity in them.

**Options for the owner** (what each asks of the user is nothing in all four; they differ in what they protect):

| | What it is | What it stops | Cost | Before Saturday? |
|---|---|---|---|---|
| **A. Public** | No control; reverse "no public route data". | Nothing. Bounded by the rectangle limit and the platform's own rate limits only. | None. The data is ODbL OpenStreetMap, as the public map tiles already are. | Yes. |
| **B. App key** | A secret in a Worker variable; the app sends it in a header; a missing or wrong key is a plain 403. | Crawlers, casual use, anyone with only the URL. Not someone who unpacks the APK: the key is in it. Changing the key needs an app update. | About 15 lines in the Worker, one header in the app. | Yes. |
| **C. Play Integrity** | The app asks Google for a signed verdict per download; the Worker checks it. | Anything not a genuine Forager install from Play. | A Google Cloud project, key setup, app work, and debug and sideloaded builds fail (the S22's test installs are debug builds). | No. |
| **D. Cloudflare edge** | A WAF rule or Access service token on `/routing/`. | As B (a token in the APK) or rate only (WAF). | Dashboard work, outside this repository and outside this dispatch's rules (no Cloudflare). | Owner's dashboard. |

**My recommendation, for the owner to accept or reject:** B, with A's honesty about it: it keeps the route out of
reach of anyone who only has the URL, costs almost nothing, and can be replaced by C later without changing the
response. It is not secrecy against a determined reader of the APK, and the PR, the app and the record should say so.

## 3. The response shape I propose (for dispatch 2026-10-23-02 and the app's download)

Two steps, so each file is fetched and cached by its own URL, and every file URL carries the run id:

1. `GET /routing/area.json?south=…&west=…&north=…&east=…` (decimal degrees). Answers 200 with:

   ```json
   {
     "run_id": "usca-20261021T111429Z",
     "osm_timestamp": "…",
     "files": [
       { "name": "W125_N45.rd5", "size": 612345, "sha256": "…hex…",
         "url": "/routing/usca-20261021T111429Z/W125_N45.rd5?south=…&west=…&north=…&east=…" }
     ]
   }
   ```

   `name` is BRouter's own file name: the app writes each file under that name into one segments folder per area and
   points BRouter at it. `osm_timestamp` is copied from the manifest. Short cache (it reads the manifest).
2. `GET <url>`: the cut file's bytes, `application/octet-stream`, with the same sha256 in a header. Cached a long
   time, since the run id in the path never names different bytes. Step 1 cuts each square once and puts the result
   in the edge cache under that URL, so step 2 is normally a cache hit.

**Errors, each a plain status and a one-line JSON `{"error": "<code>", "message": "…"}`, never an empty 200:**
`manifest_missing` (503), `run_missing_square` (503, the manifest names no file for a square the area needs but the
area is inside coverage), `checksum` (502, a header or tile-index CRC failed), `outside_coverage` (404, the area
touches no square in the manifest), `too_large` (413, over the limit below), `bad_request` (400, a malformed or
inverted rectangle).

**The limit:** `MAX_ROUTE_AREA_SIDE_KM = 50`, a side of the rectangle, checked on its north-south and its widest
east-west span; the app's largest area is a 48 x 48 km box (24 km radius). Over it: 413, never truncated.

The shape is a proposal: nothing is built until the access question is answered.

## What is unverified

- That Aspen's upload is on R2 (`routing/manifest.json`, `routing/usca-20261021T111429Z/segments4/`). The rules
  allow only read-only checks of the public Worker URL, and the Worker has no route to those keys.
- Memory and CPU for a cut at a dense city core: reasoned, not measured.
- The Workers Paid plan: the owner's word in the record, not checked by me.

## Addendum, 2026-10-23: the owner's answers, and what was built

This supersedes the status line at the top ("stopped … no route is built") and the "My recommendation" paragraph.
The text above stands as the record of the stop.

- **Access, option B.** The owner chose "B: app key (Recommended)". The option text was "Stops casual use of the address;
  not true secrecy; fits Saturday; upgradeable later". The planner fixed the contract as follows:
  - the header is `X-Forager-App-Key`;
  - the Worker's secret is `ROUTING_APP_KEY`;
  - a missing or wrong key gets 403 `{"error":"forbidden"}`, never cached;
  - the app has `BuildConfig.ROUTING_APP_KEY`, from the Gradle property `forager.routingAppKey` or the environment
    variable `FORAGER_ROUTING_APP_KEY`, and it is empty when neither is set.

  The repository is public (GitHub reports `private: false`), so the key is in no committed file. It is a deterrent,
  not secrecy.
- **Rate limits, option D, in the server code.** The owner said, verbatim, "And option D also, we'll try this without
  Google for now". The method is Cloudflare's Workers Rate Limiting binding, which is GA and needs wrangler 4.36.0 or
  later; this package pins 4.124.0. It is declared in `wrangler.toml` and needs no dashboard step: Cloudflare
  documents that these bindings are "not currently visible in the Cloudflare dashboard". Cloudflare's binding page
  and its Workers pricing page state no price and no plan restriction for it, and say nothing about workers.dev.
  The limits proposed for the owner to confirm are in `wrangler.toml`:
  - 30 requests a minute per client address;
  - 600 a minute for the app key.
- **The response shape is as in section 3,** plus these errors:

  | Error | Status | When |
  |---|---|---|
  | `forbidden` | 403 | Missing or wrong key |
  | `rate_limited` | 429 | Over a limit; sent with `Retry-After: 60` |
  | `not_configured` | 503 | The Worker is missing the secret or a limiter, so it never serves open |
  | `manifest_invalid` | 503 | The manifest cannot be read |
  | `unsupported_square` | 502 | A square in a format the cutter refuses |
  | `run_superseded` | 409 | A file URL from a replaced run, not in the edge cache; the body names the current run |

- **Coverage is all or nothing.** If any square the area needs (its margin included) is missing from the manifest,
  the area is refused with `outside_coverage`, and the missing squares are named. A coastal area whose neighbouring
  square was never built therefore gets no route data. The other choice would be to serve the squares that exist and
  name the missing ones, which would be a change to the shape, so it is left as a question.

## Addendum 2, 2026-10-23: the owner's answers on #381

This supersedes the "Coverage is all or nothing" item in the addendum above. That item stays as the record of what
was first built.

- **Rate limits.** The owner chose "Yes, as proposed (Recommended)": 30 requests a minute per address and 600 a minute
  for the key, both counted per Cloudflare location, as `wrangler.toml` declares them.
- **Coverage.** The owner chose "Serve what exists (Recommended)". The option text was "The area gets routes for the
  squares that exist; the missing part is named and logged. Coastal areas still get trails."
  - `area.json` now answers 200 with files for the squares the build has.
  - It always includes `"missing"`, naming the squares the area needs that the build lacks. The list is empty when
    none are missing, and the Worker logs it when it is not.
  - Only an area with none of its squares in the build gets 404 `outside_coverage`.
- **Keys.** The planner sets the Worker secret `ROUTING_APP_KEY` and the GitHub Actions secret
  `FORAGER_ROUTING_APP_KEY`.
