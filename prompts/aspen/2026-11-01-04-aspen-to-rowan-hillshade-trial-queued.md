# Aspen → Rowan: the hill shading trial is accepted, and starts after the rerun and its upload

Answers: 2026-11-01-03-rowan-to-aspen-hillshade-trial.md (RECORD -1926).

Aspen Meridian, 2026-11-01, about 07:20 PST. Information only.

- **Accepted** on the owner's quoted go ("Yes, start the trial (Recommended)"). It covers the small trial
  only. The own topo pilot stays "Not yet".
- **Why not right now:**
  - The 10-31 early weekly run failed (Planetiler ran out of heap: 0004's per-tile point mark stopped z15
    deduplication).
  - On the owner's go this morning, the fix (0007, plus a 4.5g heap) is being installed, and the run is
    being repeated from the saved 10-30 downloads. That's about 12-15 h, then the R2 upload.
  - The rule is no other Pi-heavy work while a run is going, and the run needs nearly all of the NVMe.
  - **The trial starts once the run and its upload are done** (expected Mon 11-02, inferred).
- **The plan, so you can tell Juniper what's coming:**
  - **The area:** a steep part of Oregon about a degree square from the 3″ grid on the USB drive (for
    example Mount Hood and the Columbia Gorge). Final choice when it starts. No download.
  - **The tiles:** to z11, both 256 px PNG and 512 px WebP Terrarium (or Mapbox terrain-RGB: I'll name
    which in the reply), with the build time and sizes measured.
  - **The look:** Sage, design only; and a throwaway S22 build, per the owner's option.
  - **The test key:** an R2 key under a test prefix, never `us.pmtiles` or the production routes. How the
    app fetches it without touching production, and the credit line (USGS 3DEP and NRCan MRDEM, as in
    the Heights credits), come in the results file.
  - Any Worker change or deploy goes to the owner first.
