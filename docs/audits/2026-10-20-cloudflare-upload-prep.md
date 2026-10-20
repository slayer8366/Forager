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

## The design questions re-asked, 2026-10-20; supersedes the preview answer above

The owner, verbatim: "And the other set of questions". The three design questions were asked
again. Answers, verbatim:

- **Scope:** "Everything together". Unchanged.
- **Preview:** "On the Pi, with the new map (Recommended)". This **supersedes** "On the laptop, as
  it is now", recorded above. The Pi serves the new US + Canada archive to the style preview,
  locally only, with nothing public and nothing uploaded, so the design is judged on the real trail
  data. The earlier note that trail styling is "checked by eye only after the upload" is withdrawn.
- **Who leads:** "Aspen, with Rowan kept informed (Recommended)". Unchanged.

## A designer role, 2026-10-20

The owner, verbatim: "Check the Forager profiles for the proper designer". No designer profile
exists in `docs/process/` on `pre-main`, `main` or `records-after-173` (`git ls-tree`, searched for
profile and design). Design had been delegated to the planner: "You're the designer", in
`docs/plans/journal-redesign.md:29` (O2), `docs/plans/landscape-phone-design.md:169` (O4),
`understory-design-system.md:1247` and RECORD line 1727.

The owner, verbatim: "Set up a designer role". Then, through the question tool:

- **Name:** "Sage Wayfinder". It was proposed with the line: "Wayfinding is designing so people
  find their way without asking; sage is a forager's plant and a word for judgment."
- **Where:** "pre-main, beside the team (Recommended)". The option read: "The designer serves the
  whole app, not just the Pi; Rowan's side merges it and adds the designer to the team table."
- **Model:** "Fable (Recommended)". The option noted that Elder Ford also runs on Fable, so a design
  and its audit would share a model.

**The plan:** the Pi planner writes `docs/process/designer-profile.md` on its own branch cut from
`origin/pre-main`, from the design record, and relays it to Rowan through the owner to merge and
add to the team table.

**Pending, and about to change:** dispatch pi-17, the map design's Phase 1, was written for a Pi
coder with the planner leading the design. With a designer named, who runs the map design is the
owner's to confirm.

## Stopped (2026-10-20)

The owner, verbatim, in order:

1. "Check with Rowan about prior art first". Dispatch pi-17's Parts B and C were held. A question
   went to Rowan's session; it was delivered, but whether Rowan read it is not confirmed.
2. "Have the research agent stop", then "Stop everything". Both agents were already stopped. The
   planner stopped the two servers pi-17's coder had left running: `pmtiles serve` on
   127.0.0.1:8081 and `serve.py` on 127.0.0.1:8765. It then confirmed no listener on 8080, 8081 or
   8765, and `forager-tiles` and `forager-tunnel` disabled and inactive.

**Left in place, undeleted:** `~/map-design-scratch` and `.claude/worktrees/map-design`.
