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

## Correction, 2026-10-20: the storage ruling quoted above was stale

The section "Ruled by the owner" says the cost answer "supersedes, for this map, the 2026-10-05
ruling 'Stay below storage limit…'". The owner corrected that, verbatim: "I gave the go already
when the forecast was put up on the website, your record is stale". So the 2026-10-05 ruling had
already been superseded before this session asked.

- The planner read only the Pi's and `pi-build`'s records, not the forecast side's, and so treated
  a superseded ruling as standing.
- Where that earlier go is recorded is **not checked** here. It is in the forecast work, not in
  this record.
- The planner's reading of it, to be corrected if wrong: the go covers spending past R2's free tier.
- This session's "Yes, ~$0.36/month is fine" stands either way.

**The lesson for the Pi planner:** before calling a ruling standing, check the other lines of work
where the owner may have ruled since. A ruling read from one record is a claim about that record.

## Re-asked after the correction, 2026-10-20: the upload plan ruled; the map design comes first

The owner, verbatim: "Ask those questions again". The three upload questions were re-asked through
the question tool without the stale storage premise. The cost was already approved. Answers,
verbatim:

- **The swap:** "Overwrite 'us' in place (Recommended)".
- **Timing:** "After the design, without waiting for the islands". So the US + Canada map is
  uploaded once the design is settled, and the islands come in a later upload.
- **Rollback:** "No rollback copy". The old `us.pmtiles` is gone once overwritten.

**Before that, on the design itself.** The owner, verbatim: "Or better yet, maybe we can design
the maps first." Answers through the question tool:

- **Scope:** "Everything together".
- **Preview:** "On the laptop, as it is now". The laptop preview reads the live old `us` map, so
  the new trail keys cannot be seen there until the upload. The trail styling is therefore designed
  from established conventions, and checked by eye only after the upload.
- **Who leads:** "Aspen, with Rowan kept informed (Recommended)".

**Note.** The look is set by the style (`map-style/`, and the offline style the Worker and app use),
not by the archive. Overwriting `us.pmtiles` changes the data. Whether the new style ships with or
after the upload is the design dispatch's question.
