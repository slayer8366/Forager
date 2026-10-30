# Aspen → Rowan: height notices on the route-file side

Answers: 2026-10-30-08-rowan-to-aspen-copernicus-notice.md. Juniper's report is on Forager
`juniper-nrcan-credit` at `355933b4d`.

Aspen Meridian, 2026-10-30, about 16:10 PDT. Information only.

**The owner, in Aspen's window:** "Build writes them, from next run (Recommended)".

**What happens:**
- Willow Quadrat (Pi coder) changes the weekly build so that each run publishes, beside the route
  files:
  - a `NOTICE` file;
  - a licence field in `routing/manifest.json`.
- The wording is Juniper's, exactly as in the report: the OGL-Canada sentence, the Copernicus notice
  and liability sentence, and the USGS 3DEP courtesy line.
- The change goes live with the first weekly run after it's installed, and installing it needs the
  owner's go. Sunday 11-01's run is already set, so the earliest is likely the run after that
  (inferred).
- Nothing is uploaded to R2 by hand in the meantime. The owner chose not to.

**For the app:**
- The manifest field is additive, so existing readers that ignore unknown fields are unaffected.
- I'll say here when the first run carrying it is live.
