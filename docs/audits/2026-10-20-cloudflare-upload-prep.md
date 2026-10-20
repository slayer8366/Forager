# 2026-10-20: putting the new map on Cloudflare, what exists and what is ruled (pre-build, nothing done)

Pi planner (Aspen Meridian). The owner, verbatim: "Next step is to upload the maps to the cloudflare
server to replace the maps there." **Nothing has been uploaded or changed.**

## What exists, read 2026-10-20 about 07:30Z

**Account resources, from the Cloudflare connector:**

- One Worker, `forager-pmtiles`, created 2026-08-19.
- It was **last modified 2026-10-20T06:44:59Z.** This session made no Cloudflare change, and no
  coder it started touches Cloudflare. Who changed it, and what, is **not determined**: the
  connector shows only the date.
- One R2 bucket, `forager-maps`. Its contents were not listed this time. On 2026-10-05 it held
  `us.pmtiles` (8,817,909,309 bytes) and `overflow/` (90,511,806 bytes, 14,146 objects), per
  `docs/plans/2026-10-05-pi-serving-cloudflare-steps.md`.

**The live Worker's request handler.** Read: lines 3140–3423 of the 3,423-line bundle. A search
covered the whole file. Lines 1–3139, the bundled library and the offline style, were not read in
full.

- It serves any archive by name: `/<name>/z/x/y.mvt` and `/<name>.json` read R2 key
  `pmtilesPath(name, PMTILES_PATH)`.
- The offline style the app uses names `https://forager-pmtiles.brandonlee1-894.workers.dev/us.json`
  (bundle line 910). So overwriting `us.pmtiles` changes what the app gets, with no app change.
- Overflow: when an archive's `maxZoom` is below 15 (`OVERFLOW_MAX_ZOOM`), the Worker fills zoom 15
  from Protomaps' daily build into `overflow/<name>/…`. The new archive is z0–15, so the overflow
  path is never taken for it. The existing `overflow/us/` objects would sit unused.
- **The Worker has no route for BRouter's `.rd5` squares.** The upload covers the map tiles only.
- The Pi-origin path does nothing while `PI_ORIGIN_URL` is unset, consistent with the owner's
  2026-10-18 change (`docs/audits/2026-10-18-usca-build-switchover-report.md`).

**Credentials, as recorded on 2026-10-05 and 2026-10-18 (not re-checked):**

- The Pi's Cloudflare API token is scoped to Individual Workers Editor on `forager-pmtiles` only.
- The Pi's rclone R2 remote was described as read-only.
- An upload needs an R2 write credential the owner creates.

**Pricing, read from developers.cloudflare.com/r2/pricing on 2026-10-20:**

- Standard storage $0.015/GB-month, after 10 GB-month free.
- Class A operations: 1M a month free.
- Egress free.

So replacing 8.8 GB with 34 GB costs about $0.36 a month (inferred).

## Ruled by the owner, verbatim, through the question tool

- **Cost:** "Yes, ~$0.36/month is fine (Recommended)". This supersedes, for this map, the
  2026-10-05 ruling "Stay below storage limit as I want to explore further avenues before making
  that decision. I have to do budgeting and all that."
- **The swap:** "Overwrite 'us' in place (Recommended)". The option read: "No app change: every
  offline download from then on gets the new map (US + Canada, Alaska, Hawaii, trail details, full
  zoom 15). Takes effect for live users the moment the upload finishes."
- **Timing:** "Wait". This is not decided, so nothing is to be uploaded.
- **Rollback:** "Wait". Not decided.

## Open before any dispatch

- The two "Wait" answers.
- The 06:44:59Z Worker change.
- The R2 write credential.
- The Pi's upload speed, not measured.
- Whether the app's style and the new archive's schema agree. Both are Protomaps 4.15.2 (inferred:
  the old archive's version from the 2026-10-04 handoff, the new one from its metadata). The new
  trail keys are extra fields.
